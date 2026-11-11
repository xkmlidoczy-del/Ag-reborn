package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ConstructRules;
import cn.academy.port.highpower.core.NewSkillBudget;
import cn.academy.port.preset.PresetSkills;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** V-ADV-01 Vector Construct Manipulation (444) without the optional physics backend: registration and a charge-free refusal. */
@GameTestHolder("academy_construct_runtime")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class HighPowerConstructRuntimeTests {
    private static final String TEMPLATE = "construct_empty", BATCH = "construct_runtime";
    private HighPowerConstructRuntimeTests() {}

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int dimension : new int[] {48, 16, 48}) size.add(IntTag.valueOf(dimension));
        for (int axis = 0; axis < 3; axis++) position.add(IntTag.valueOf(0));
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell); air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_construct_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void construct_is_appended_after_the_sixty_design_concepts(GameTestHelper h) {
        var skill = HighPowerSkills.find(HighPowerSkills.VECTOR_CONSTRUCT).orElse(null);
        h.assertTrue(skill != null && skill.category().equals(HighPowerCategories.VECMANIP) && skill.level() == 4 && skill.controllable(), "vecmanip, LV4, controllable");
        h.assertTrue(skill.requirements().size() == 1 && skill.requirements().get(0).id().equals(HighPowerSkills.VECTOR_CAGE), "requires vector cage");
        h.assertTrue(HighPowerSkills.ALL.size() == 61 && HighPowerSkills.ALL.get(60).id().equals(HighPowerSkills.VECTOR_CONSTRUCT)
                && HighPowerSkills.ALL.get(59).id().equals(HighPowerSkills.IMPULSE_SPLIT), "appended last; the sixty keep their indices");
        h.assertTrue(PresetSkills.REGISTERED.contains(HighPowerSkills.VECTOR_CONSTRUCT) && NewSkillRuntime.Kind.of(HighPowerSkills.VECTOR_CONSTRUCT).isPresent(), "bindable with a runtime");
        h.assertTrue(HighPowerSkills.VECTOR_CONSTRUCT_PROPOSAL == 444, "design id");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void only_the_exact_reviewed_physics_bundle_extends_the_damage_contract(GameTestHelper h) {
        Map<String, String> base = Map.of("minecraft", "1.21.1", "neoforge", "21.1.252", "academy", "0.1.0-m41-highpower.0.81.5-city-a3.63");
        Map<String, String> bundle = new HashMap<>(base); bundle.putAll(PhysicsBundle.MODS);
        Set<String> packs = Set.of("vanilla", "mod_data", "mod/neoforge", "mod/academy", "mod/sable");
        h.assertTrue(VanillaCombatContract.runtimeReason(bundle, packs).isEmpty() && VanillaMiningContract.runtimeReason(bundle, packs).isEmpty(), "reviewed bundle admitted");
        Map<String, String> wrong = new HashMap<>(bundle); wrong.put("sable", "2.0.5");
        h.assertTrue(VanillaCombatContract.runtimeReason(wrong, packs).isPresent(), "another Sable version is refused");
        Map<String, String> partial = new HashMap<>(base); partial.put("sable", "2.0.6");
        h.assertTrue(VanillaCombatContract.runtimeReason(partial, packs).isPresent(), "a partial bundle is refused");
        Map<String, String> extra = new HashMap<>(bundle); extra.put("create", "6.0.10");
        h.assertTrue(VanillaCombatContract.runtimeReason(extra, packs).isPresent() && VanillaMiningContract.runtimeReason(extra, packs).isPresent(), "Create is not reviewed yet");
        h.assertTrue(VanillaCombatContract.runtimeReason(base, packs).isPresent(), "the bundle's data pack is refused without the bundle");
        h.assertTrue(VanillaCombatContract.runtimeReason(bundle, Set.of("vanilla", "mod/sable", "file/x")).isPresent(), "an unreviewed pack is still refused");
        h.assertTrue(VanillaCombatContract.runtimeReason(base, Set.of("vanilla", "mod_data")).isEmpty(), "the base set is unchanged");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 120)
    public static void without_sable_the_press_refuses_before_charging(GameTestHelper h) {
        if (ModList.get().isLoaded("sable")) { h.succeed(); return; }
        Fixture f = new Fixture(h, NewSkillRuntime.Kind.CONSTRUCT); ServerPlayer p = f.player(10, 10);
        f.ready(() -> {
            for (int y = 2; y <= 4; y++) h.setBlock(new BlockPos(10, y, 16), Blocks.OAK_PLANKS);
            aim(p, h.absoluteVec(new Vec3(10.5, 3.5, 16.5)));
            double cp = HighPowerResources.cp(p);
            h.assertFalse(f.start(p), "refused without a backend"); f.release(p);
            f.drain(p);
            h.assertTrue(f.commandAcks.stream().anyMatch(v->v.phase()==NewSkillRuntime.END && v.input()==1 && v.epoch()==f.epoch(p)), "refused tap still retires its exact client input");
            h.assertFalse(f.start(p), "a second command is independently refused without a backend"); f.release(p); f.drain(p);
            h.assertTrue(f.commandAcks.stream().anyMatch(v->v.phase()==NewSkillRuntime.END && v.input()==2 && v.epoch()==f.epoch(p)), "the second tap has its own acknowledgement, never a reused receipt");
            h.assertFalse(ConstructRuntime.previewing(p) || ConstructRuntime.heldBody(p) != null, "no state was created");
            near(h, HighPowerResources.cp(p), cp, "nothing was charged");
            h.assertTrue(h.getLevel().getBlockState(h.absolutePos(new BlockPos(10, 3, 16))).is(Blocks.OAK_PLANKS), "the world is untouched");
            h.succeed();
        });
    }

    static void near(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) <= 1e-6 * Math.max(1, Math.abs(expected)), message + ": " + actual + " vs " + expected);
    }

    /** Points the player's view at the centre of {@code target} (absolute coordinates). */
    static void aim(ServerPlayer p, Vec3 target) {
        Vec3 d = target.subtract(p.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z)), pitch = (float) Math.toDegrees(-Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
        p.moveTo(p.getX(), p.getY(), p.getZ(), yaw, pitch); p.setYHeadRot(yaw); p.connection.resetPosition();
    }

    static int count(GameTestHelper h, Block block, BlockPos from, BlockPos to) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(h.absolutePos(from), h.absolutePos(to))) if (h.getLevel().getBlockState(pos).is(block)) n++;
        return n;
    }

    private static final class Fixture {
        final List<NewSkillRuntime.Visual> commandAcks=new ArrayList<>();
        final GameTestHelper h; final NewSkillRuntime.Kind kind; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>(); final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>();
        final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>();
        final Map<ServerPlayer, String> held = new IdentityHashMap<>(); boolean closed;
        Fixture(GameTestHelper h, NewSkillRuntime.Kind kind) {
            this.h = h; this.kind = kind;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player(double x, double z) {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "VC" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 4; dz++) h.setBlock(BlockPos.containing(x + dx, 1, z + dz), Blocks.STONE);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x + .5, 2, z + .5))); place(p);
            h.assertTrue(HighPowerCombat.enroll(p, kind.level), "explicit native connected enrollment");
            var s = AbilityStorage.get(p); s.selectCategory(kind.category); s.setLevel(kind.level);
            s.learn(HighPowerCategories.recoveryRoot(kind.category)); s.learn(kind.id); s.activated = true; s.overloadFine = true;
            h.assertTrue(s.presets.edit(0, 0, kind.id, s::learned), "real learned native key binding");
            HighPowerResources.initializeBudget(p); hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void place(ServerPlayer p) { Vec3 at = anchors.get(p); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0; p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        long epoch(ServerPlayer p) { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(ServerPlayer p) { long input = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, input); return new HighPowerInput.Input(0, input, p.getId(), epoch(p)).wire(); }
        boolean start(ServerPlayer p) { String wire = wire(p); held.put(p, wire); return NewSkillRuntime.start(p, wire); }
        void release(ServerPlayer p) { NewSkillRuntime.release(p, held.get(p)); }
        double cost(ServerPlayer p) { return NewSkillBudget.cp(budget(), HighPowerGrowth.values(p).position()); }
        NewSkillBudget.Skill budget() { return NewSkillBudget.Skill.valueOf(kind.name()); }
        Entity spawn(EntityType<?> type, double ahead) { return spawnAt(type, ahead, 0); }
        /** Spawns {@code ahead} metres in front of the player (looking toward +Z) and {@code side} metres to the right. */
        Entity spawnAt(EntityType<?> type, double ahead, double side) {
            Entity e = type.create(h.getLevel()); Vec3 at = anchors.values().iterator().next();
            e.setPos(at.x - side, at.y, at.z + ahead); h.assertTrue(h.getLevel().addFreshEntity(e), "entity joined the world"); entities.add(e); return e;
        }
        Arrow arrow(ServerPlayer p, double distance) {
            Arrow a = new Arrow(h.getLevel(), p.getX(), p.getEyeY(), p.getZ() + distance, new ItemStack(Items.ARROW), null);
            a.setNoGravity(true); a.setDeltaMovement(0, 0, -.6); h.assertTrue(h.getLevel().addFreshEntity(a), "arrow joined the world"); entities.add(a); return a;
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void drain(ServerPlayer p) {
            EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks(); Object packet;
            while ((packet = channel.readOutbound()) != null) {
                if(packet instanceof net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket custom && custom.payload() instanceof NewSkillRuntime.Visual visual) commandAcks.add(visual);
                if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId())); ReferenceCountUtil.release(packet);
            }
        }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer p : players) { NewSkillRuntime.remove(p); DevelopmentController.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
