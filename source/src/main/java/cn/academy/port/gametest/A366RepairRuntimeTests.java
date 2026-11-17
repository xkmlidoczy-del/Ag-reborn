package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.canon.CanonExpansionCombatA;
import cn.academy.port.city.CityElevatorController;
import cn.academy.port.city.CityElevators;
import cn.academy.port.city.CityInteriorContent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.environment.SkillEnvironment;
import cn.academy.port.skill.LightShield;
import cn.academy.port.skill.RailgunDamage;
import cn.academy.port.skill.RailgunVisualContact;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

@GameTestHolder("academy_a366")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class A366RepairRuntimeTests {
    private static final String TEMPLATE = "a366_empty", BATCH = "academy_a366";
    private static final String TRIP = "academy:city_lift_trip";

    private A366RepairRuntimeTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel world)) return;
        var tag = new CompoundTag();
        var size = new ListTag();
        size.add(IntTag.valueOf(48)); size.add(IntTag.valueOf(16)); size.add(IntTag.valueOf(48));
        tag.put("size", size);
        var blocks = new ListTag();
        var first = new CompoundTag();
        var pos = new ListTag();
        pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0)); pos.add(IntTag.valueOf(0));
        first.put("pos", pos); first.putInt("state", 0); blocks.add(first); tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        var palette = new ListTag();
        var air = new CompoundTag(); air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        world.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_a366", TEMPLATE))
                .load(world.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }


    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void railgun_preserves_falloff_and_stops_on_the_wall_face(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var player = f.player(24.5, 5, 8.5, 0, 0);
            var origin = player.getEyePosition();
            var edge = f.zombieWorld(origin.add(2.25, 0, 5));
            var outside = f.zombieWorld(origin.add(2.45, 0, 5));
            var sideOccluded = f.zombieWorld(origin.add(-2, 0, 6));
            h.getLevel().setBlock(BlockPos.containing(origin.add(-2, 0, 3)), Blocks.BEDROCK.defaultBlockState(), 3);
            BlockPos centreWall = BlockPos.containing(origin.add(0, 0, 10));
            for (int x=-3;x<=3;x++) for(int y=-2;y<=2;y++) {
                h.getLevel().setBlock(centreWall.offset(x,y,0), Blocks.BEDROCK.defaultBlockState(), 3);
            }
            var justBehind = f.zombieWorld(new Vec3(origin.x,origin.y,centreWall.getZ()+1.55));
            var farBehind = f.zombieWorld(new Vec3(origin.x,origin.y,centreWall.getZ()+4.55));
            var attacks = f.attackRecorder(player);
            var result = RailgunDamage.perform(player,10,900);
            var contact = RailgunVisualContact.sample(player,h.getLevel(),origin,new Vec3(0,0,1),result.beamLength());
            h.assertTrue(Math.abs(result.beamLength()-(centreWall.getZ()-origin.z))<1e-5,"visible beam ends on bedrock face");
            h.assertTrue(contact!=null&&Math.abs(contact.distance()-result.beamLength())<1e-5,"terminal face supplied to native rendering");
            h.assertTrue(Math.abs(attacks.damage(edge)-9.64)<1e-5,"side attenuation follows a3.64");
            h.assertTrue(attacks.damage(outside)==0,"a body wider than the legacy target point does not extend the radius");
            h.assertTrue(attacks.damage(justBehind)==0&&attacks.damage(farBehind)==0,"blocked targets receive no damage events");
            h.assertTrue(attacks.damage(sideOccluded)==0,"side wall stops its part of the beam");
            h.succeed();
        } finally { f.cleanup(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_terrain", timeoutTicks = 20)
    public static void railgun_goes_through_committed_cuts_but_stops_on_denied_terrain(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var player = f.connectedPlayer(24.5,5,8.5,0,0);
            Vec3 origin = player.getEyePosition();
            var open = BlockPos.containing(origin.add(0,0,4));
            var denied = BlockPos.containing(origin.add(0,0,11));
            var world = h.getLevel();
            h.assertValueEqual(SkillEnvironment.mode(world),SkillEnvironment.Mode.RESTORE,"default server environment mode");
            world.setBlock(open,Blocks.STONE.defaultBlockState(),3);
            world.setBlock(denied,Blocks.STONE.defaultBlockState(),3);
            var noBreak = new Object() {
                @SubscribeEvent public void protect(SkillBlockDestroyEvent event) {
                    if(event.player==player&&event.position.equals(denied))event.setCanceled(true);
                }
            };
            f.registerHook(noBreak);
            var visible = f.zombieWorld(origin.add(0,0,7));
            var behind = f.zombieWorld(origin.add(0,0,12));
            var attacks = f.attackRecorder(player);
            var result = RailgunDamage.perform(player,10,900);
            h.assertTrue(SkillEnvironment.isVacancy(world.getBlockState(open)),"first wall cell was actually removed for restoration");
            h.assertTrue(world.getBlockState(denied).is(Blocks.STONE),"protected wall retained");
            h.assertTrue(Math.abs(result.beamLength()-(denied.getZ()-origin.z))<1e-5,"beam stops where the protection event refused the cut");
            h.assertTrue(attacks.damage(visible)==10&&attacks.damage(behind)==0,"only the opened side of the protected wall deals damage");
            h.succeed();
        } finally { f.cleanup(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_shield", timeoutTicks = 20)
    public static void light_shield_preserves_self_settlement_without_spending_for_a_bystander(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var owner = f.player(10.5,5,10.5,0,0);
            var state = ready(owner,"meltdowner",LightShield.ID,3,1.0);
            var bystander = f.player(12.5,5,10.5,0,0);
            var attacker = f.zombieWorld(owner.position().add(0,0,2));
            h.assertTrue(LightShield.start(owner),"owner shield started normally");
            double cp = state.cp;
            var foreign = new LivingIncomingDamageEvent(bystander,new DamageContainer(bystander.damageSources().mobAttack(attacker),12f));
            NeoForge.EVENT_BUS.post(foreign);
            h.assertTrue(!foreign.isCanceled()&&foreign.getAmount()==12&&state.cp==cp,"bystander untouched and owner does not pay");
            var own = new LivingIncomingDamageEvent(owner,new DamageContainer(owner.damageSources().mobAttack(attacker),12f));
            NeoForge.EVENT_BUS.post(own);
            h.assertTrue(own.isCanceled()&&state.cp<cp,"same original absorption applies to the owner");
            h.succeed();
        } finally { f.cleanup(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_curtain", timeoutTicks = 25)
    public static void air_curtain_intercepts_legacy_arrows_on_both_box_faces(GameTestHelper h) {
        var f = new Fixture(h);
        try {
            var p = f.connectedPlayer(24.5,5,18.5,0,0);
            var world = p.serverLevel();
            Vec3 centre = p.getEyePosition();
            // Offset on the inner face is outside a 2.6-block sphere. The face is still active.
            Vec3 innerStart = centre.add(3.15,1.2,1.8);
            Vec3 outerStart = centre.add(49,0,7);
            world.getChunkAt(BlockPos.containing(outerStart));
            var skill = "aero_air_curtain";
            var state = ready(p,"aero_hand",skill,5,1);
            state.presets.edit(0,0,skill,state::learned);
            h.assertTrue(HighPowerCombat.enroll(p,5),"enrolled");
            HighPowerResources.initializeBudget(p);
            h.assertTrue(CanonExpansionCombatA.cast(p,skill),"curtain accepts selected ability");
            var inner = new Arrow(EntityType.ARROW,world);
            var outer = new Arrow(EntityType.ARROW,world);
            var excluded = new Snowball(EntityType.SNOWBALL,world);
            for (var projectile : List.of(inner,outer,excluded)) {
                projectile.setPos(projectile==outer?outerStart:innerStart);
                projectile.setNoGravity(true);
                projectile.setDeltaMovement(-1.35,0,0);
                world.addFreshEntity(projectile);
                f.entities.add(projectile);
            }
            NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(p));
            h.assertTrue(inner.getDeltaMovement().x>0&&outer.getDeltaMovement().x>0,
                    "both real box faces reflect incoming vanilla arrows");
            h.assertTrue(Math.abs(inner.getX()-centre.x-2.8)<1e-5&&Math.abs(outer.getX()-centre.x-48.2)<1e-5,
                    "reflected positions agree with the 2.6 and 48 half extents plus the contact offset");
            h.assertTrue(excluded.getDeltaMovement().x<0,"unsupported projectile is outside the legacy contract");
            h.succeed();
        } finally { f.cleanup(); }
    }

    @GameTest(template = TEMPLATE, batch = BATCH + "_lift", timeoutTicks = 35)
    public static void orphan_cabin_retains_both_riders_until_a_loaded_exit_is_safe(GameTestHelper h) {
        var f = new Fixture(h);
        var first = f.connectedPlayer(12.5,12,16.5,0,0);
        var second = f.connectedPlayer(12.5,12,16.5,0,0);
        var world = h.getLevel();
        var car = CityInteriorContent.CABIN.get().create(world);
        car.setPos(first.position());
        car.controller(h.absolutePos(new BlockPos(12,11,16)));
        car.phase(CityElevatorController.Phase.FAULT.ordinal());
        world.addFreshEntity(car);
        f.entities.add(car);
        Vec3 landing = h.absoluteVec(new Vec3(19.5,4,16.5));
        Vec3 otherLanding = h.absoluteVec(new Vec3(23.5,4,16.5));
        saveTrip(first,car.controller(),landing);
        saveTrip(second,car.controller(),otherLanding);
        h.assertTrue(first.startRiding(car),"first rider boarded fault fixture");
        CityElevators.login(new PlayerEvent.PlayerLoggedInEvent(second));
        h.assertTrue(second.getVehicle()==car,"reconnect reuses occupied fault cabin");
        first.stopRiding();
        BlockPos ground = BlockPos.containing(landing).below();
        h.runAfterDelay(5,()->{
            h.assertTrue(first.getVehicle()==car&&second.getVehicle()==car&&!car.isRemoved(),"empty shaft cannot cause a dismount or delete another rider's cabin");
            h.assertTrue(first.getPersistentData().contains(TRIP),"unsafe attempt retains recovery data");
            h.assertFalse(CityElevators.validExit(world,landing,first),"air has no safe support");
            world.setBlock(ground,Blocks.STONE.defaultBlockState(),3);
            world.setBlock(ground.above(),Blocks.WATER.defaultBlockState(),3);
            first.stopRiding();
        });
        h.runAfterDelay(10,()->{
            h.assertTrue(first.getVehicle()==car,"a submerged landing is rejected");
            world.setBlock(ground.above(),Blocks.AIR.defaultBlockState(),3);
            first.stopRiding();
        });
        h.runAfterDelay(16,()->{
            h.assertTrue(!first.isPassenger()&&first.position().distanceToSqr(landing)<1e-8
                    && CityElevators.validExit(world,first.position(),first),"first passenger reaches verified supported landing");
            h.assertTrue(second.getVehicle()==car&&!car.isRemoved(),"successful rescue leaves other passenger aboard");
            h.assertTrue(!first.getPersistentData().contains(TRIP)&&second.getPersistentData().contains(TRIP),"each passenger owns their recovery");
            world.setBlock(BlockPos.containing(otherLanding).below(),Blocks.STONE.defaultBlockState(),3);
            second.stopRiding();
        });
        h.runAfterDelay(23,()->{
            h.assertTrue(!second.isPassenger()&&second.position().distanceToSqr(otherLanding)<1e-8
                    && CityElevators.validExit(world,second.position(),second),"remaining rider can leave once their landing is safe");
            h.succeed();
        });
    }

    private static void saveTrip(ServerPlayer player,BlockPos controller,Vec3 safe) {
        var data = new CompoundTag();
        data.putLong("Controller",controller.asLong());
        data.putString("Dimension",player.level().dimension().location().toString());
        data.putDouble("X",safe.x);
        data.putDouble("Y",safe.y);
        data.putDouble("Z",safe.z);
        player.getPersistentData().put(TRIP,data);
    }

    private static AbilityProgress ready(ServerPlayer p, String cat, String skill, int level, double exp) {
        var s = AbilityStorage.get(p);
        s.selectCategory(cat);
        s.setLevel(level);
        s.learn(skill);
        s.experience.put(skill, exp);
        s.extraCp = ClassicRules.EXTRA_CP_CAP[level];
        s.extraOverload = ClassicRules.EXTRA_OVERLOAD_CAP[level];
        s.cp = s.maxCp();
        s.overload = 0;
        s.activated = true;
        s.overloadFine = true;
        return s;
    }

    private static final class Fixture {
        final GameTestHelper h;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>();
        boolean closed;

        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { cleanup(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { cleanup(); }
                public void testAddedForRerun(GameTestInfo o, GameTestInfo n, GameTestRunner r) { cleanup(); }
            });
        }

        ServerPlayer player(double rx, double ry, double rz, float yaw, float pitch) {
            var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-A366]"));
            players.add(p);
            Vec3 at = h.absoluteVec(new Vec3(rx, ry, rz));
            p.moveTo(at.x, at.y, at.z, yaw, pitch);
            p.setYHeadRot(yaw);
            p.yRotO = yaw;
            p.xRotO = pitch;
            p.setNoGravity(true);
            p.setDeltaMovement(Vec3.ZERO);
            p.getAbilities().instabuild = false;
            return p;
        }

        ServerPlayer connectedPlayer(double rx, double ry, double rz, float yaw, float pitch) {
            var level = h.getLevel();
            UUID uuid = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, "A366" + uuid.toString().substring(0, 6)), false);
            var p = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND);
            var channel = new EmbeddedChannel(connection);
            channels.add(channel);
            NetworkRegistry.configureMockConnection(connection);
            level.getServer().getPlayerList().placeNewPlayer(connection, p, cookie);
            players.add(p);
            p.setGameMode(GameType.SURVIVAL);
            Vec3 at = h.absoluteVec(new Vec3(rx, ry, rz));
            p.moveTo(at.x, at.y, at.z, yaw, pitch);
            p.setYHeadRot(yaw);
            p.yRotO = yaw;
            p.xRotO = pitch;
            p.setDeltaMovement(Vec3.ZERO);
            return p;
        }

        Zombie zombieWorld(Vec3 pos) {
            var z = new Zombie(EntityType.ZOMBIE, h.getLevel());
            z.moveTo(pos.x, pos.y, pos.z, 0f, 0f);
            z.setNoAi(true);
            z.setNoGravity(true);
            z.getAttribute(Attributes.MAX_HEALTH).setBaseValue(500);
            z.setHealth(500);
            h.getLevel().addFreshEntity(z);
            entities.add(z);
            return z;
        }

        AttackRecorder attackRecorder(ServerPlayer owner) {
            var rec = new AttackRecorder(owner);
            registerHook(rec);
            return rec;
        }

        void registerHook(Object hook) {
            hooks.add(hook);
            NeoForge.EVENT_BUS.register(hook);
        }

        void cleanup() {
            if (closed) return;
            closed = true;
            for (var hook : hooks) NeoForge.EVENT_BUS.unregister(hook);
            for (var p : players) {
                LightShield.remove(p);
                AbilityStorage.remove(p);
                NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p));
                var list = h.getLevel().getServer().getPlayerList();
                if (!(p instanceof FakePlayer) && list.getPlayer(p.getUUID()) == p) list.remove(p);
                else p.discard();
            }
            for (var e : entities) e.discard();
            for (var ch : channels) ch.finishAndReleaseAll();
        }
    }

    public static final class AttackRecorder {
        final ServerPlayer owner;
        final Map<Entity, Double> amounts = new IdentityHashMap<>();

        AttackRecorder(ServerPlayer owner) { this.owner = owner; }

        @SubscribeEvent
        public void onSkillAttack(SkillAttackEvent event) {
            if (event.player == owner && event.skill.equals("electromaster.railgun")) amounts.merge(event.target, event.amount, Double::sum);
        }

        double damage(Entity e) { return amounts.getOrDefault(e, 0.0); }
    }
}
