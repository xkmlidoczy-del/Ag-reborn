package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.canon.CanonVfx;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.CompatMode;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.environment.SkillEnvironment;
import cn.academy.port.skill.ElectronBomb;
import cn.academy.port.skill.RayBarrage;
import cn.academy.port.skill.ThunderClap;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
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
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Specialized end-to-end runtime verification for the a3.87-rc2 skill upgrade:
 * P0 (Creative & /academy dev casting, HighPower auto-enrollment, weapon/offhand unlock),
 * P1 (Boss & ArmorStand combat admission, passive Villager/Cow protection, DryFire honesty, LV5 ultimates & PointMeltdown entity damage),
 * P2 (Two-stage endpoint blasts & craters, ElectronBomb wall-splash, RayBarrage non-Silbarn refraction, ThunderClap >=20-tick key-release strike).
 */
@GameTestHolder("academy_a387_upgrade")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class A387SkillUpgradeRuntimeTests {
    private static final Vec3 ORIGIN = new Vec3(10.5, 2.0, 4.5);

    private A387SkillUpgradeRuntimeTests() {}

    @SubscribeEvent
    public static void registerArenaTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag();
        var size = new ListTag();
        for (int value : new int[]{24, 16, 28}) size.add(IntTag.valueOf(value));
        var pos = new ListTag();
        for (int i = 0; i < 3; i++) pos.add(IntTag.valueOf(0));
        var air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        var cell = new CompoundTag();
        cell.put("pos", pos);
        cell.putInt("state", 0);
        var blocks = new ListTag();
        blocks.add(cell);
        var palette = new ListTag();
        palette.add(air);
        tag.put("size", size);
        tag.put("blocks", blocks);
        tag.put("palette", palette);
        tag.put("entities", new ListTag());
        level.getStructureManager()
                .getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_a387_upgrade", "arena"))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    private static void buildFloor(GameTestHelper h, net.minecraft.world.level.block.Block floorBlock) {
        h.getLevel().setDayTime(18000);
        for (int x = 0; x < 24; x++) {
            for (int z = 0; z < 28; z++) {
                h.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
                h.setBlock(new BlockPos(x, 1, z), floorBlock);
            }
        }
    }

    private static ServerPlayer join(GameTestHelper h, String prefix, GameType gameType) {
        var level = h.getLevel();
        UUID uuid = UUID.randomUUID();
        var cookie = CommonListenerCookie.createInitial(
                new GameProfile(uuid, prefix + uuid.toString().substring(0, 6)), false);
        var player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        var connection = new Connection(PacketFlow.SERVERBOUND);
        var channel = new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        player.setGameMode(gameType);
        if (gameType == GameType.CREATIVE) {
            player.getAbilities().instabuild = true;
            player.getAbilities().invulnerable = true;
            player.getAbilities().mayfly = true;
        }
        h.testInfo.addListener(new GameTestListener() {
            @Override public void testStructureLoaded(GameTestInfo test) {}
            @Override public void testPassed(GameTestInfo test, GameTestRunner runner) { close(); }
            @Override public void testFailed(GameTestInfo test, GameTestRunner runner) { close(); }
            @Override public void testAddedForRerun(GameTestInfo test, GameTestInfo next, GameTestRunner runner) { close(); }

            private void close() {
                var players = level.getServer().getPlayerList();
                try {
                    if (players.getPlayer(uuid) == player) players.remove(player);
                } finally {
                    channel.finishAndReleaseAll();
                }
            }
        });
        return player;
    }

    private static void aimAt(ServerPlayer p, Vec3 worldTarget) {
        Vec3 d = worldTarget.subtract(p.getEyePosition());
        float yaw = (float) (Math.toDegrees(Math.atan2(d.z, d.x)) - 90.0);
        float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z)));
        p.setYRot(yaw);
        p.setYHeadRot(yaw);
        p.setXRot(pitch);
        p.yRotO = yaw;
        p.xRotO = pitch;
    }

    private static String wire(ServerPlayer p, int slot, long seq) {
        long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p));
        return new HighPowerInput.Input(slot, seq, p.getId(), epoch).wire();
    }

    /** 1. P0 Gate: Creative mode + /academy dev + weapon in mainhand + HighPower & Canon casting. */
    @GameTest(template = "arena", batch = "a387_p0", timeoutTicks = 80)
    public static void p0_creative_and_dev_unlock(GameTestHelper h) {
        h.assertTrue(CompatMode.openGameplay(), "Open gameplay enabled by the production mod");
        buildFloor(h, Blocks.STONE);
        var player = join(h, "P0", GameType.CREATIVE);
        player.moveTo(h.absoluteVec(ORIGIN), 0, 0);
        player.setOnGround(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.NETHERITE_SWORD));
        player.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.IRON_INGOT, 8));

        var z1 = h.spawn(EntityType.ZOMBIE, ORIGIN.add(0, 0, 4.0));
        var z2 = h.spawn(EntityType.ZOMBIE, ORIGIN.add(2.0, 0, 4.0));
        var z3 = h.spawn(EntityType.ZOMBIE, ORIGIN.add(-2.0, 0, 2.5));
        z1.setNoAi(true);
        z2.setNoAi(true);
        z3.setNoAi(true);

        var cmd = player.createCommandSourceStack().withPermission(4);
        player.server.getCommands().performPrefixedCommand(cmd, "academy dev electromaster");

        h.assertTrue(HighPowerCombat.enabled(player) && HighPowerCombat.earnedLevel(player) == 5,
                "/academy dev enrolls ledger at rank 5");
        var state = AbilityStorage.get(player);
        h.assertTrue(state.learned(HighPowerSkills.FORKED_ARC), "/academy dev learns HighPower skills");
        h.assertTrue(AcademyGameplay.ammo(player), "Railgun accepts offhand iron ingot while holding mainhand weapon");

        state.presets.edit(0, 0, HighPowerSkills.FORKED_ARC, state::learned);
        aimAt(player, z1.getBoundingBox().getCenter());
        float z1Before = z1.getHealth();
        boolean arcOk = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
        h.assertTrue(arcOk, "Creative player casts Forked Arc while holding weapon");

        h.runAfterDelay(5, () -> {
            h.assertTrue(z1.getHealth() < z1Before, "Forked Arc deals real damage in Creative mode");

            player.server.getCommands().performPrefixedCommand(cmd, "academy dev meltdowner");
            var mdState = AbilityStorage.get(player);
            mdState.presets.edit(0, 0, "point_meltdown", mdState::learned);
            aimAt(player, z2.getBoundingBox().getCenter());
            float z2Before = z2.getHealth();
            String w1 = wire(player, 0, 1L);
            boolean pmStart = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("hp_point_meltdown_start", w1));
            boolean pmRel = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("hp_point_meltdown_release", w1));
            h.assertTrue(pmStart && pmRel && z2.getHealth() < z2Before,
                    "Point Meltdown deals direct entity damage in Creative mode");

            player.server.getCommands().performPrefixedCommand(cmd, "academy dev dark_matter");
            var dmState = AbilityStorage.get(player);
            dmState.presets.edit(0, 0, "dark_blade", dmState::learned);
            aimAt(player, z3.getBoundingBox().getCenter());
            float z3Before = z3.getHealth();
            boolean dbOk = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
            h.assertTrue(dbOk && z3.getHealth() < z3Before, "Dark Blade casts and deals damage in Creative mode");

            z1.discard();
            z2.discard();
            z3.discard();
            player.server.getPlayerList().remove(player);
            h.succeed();
        });
    }

    /** 2. P1 Gate: Boss & ArmorStand combat admission + passive Villager/Cow protection. */
    @GameTest(template = "arena", batch = "a387_p1_targets", timeoutTicks = 80)
    public static void p1_crossmod_boss_and_friendly_safety(GameTestHelper h) {
        h.assertTrue(CompatMode.openGameplay(), "Open gameplay enabled by the production mod");
        buildFloor(h, Blocks.STONE);
        h.setBlock(new BlockPos(8, 2, 5), Blocks.WATER);

        var player = join(h, "P1T", GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(ORIGIN), 0, 0);
        player.setOnGround(true);
        var cmd = player.createCommandSourceStack().withPermission(4);
        player.server.getCommands().performPrefixedCommand(cmd, "academy dev hydro_hand");

        Warden warden = h.spawn(EntityType.WARDEN, ORIGIN.add(0, 0, 3.5));
        warden.setNoAi(true);
        ArmorStand stand = h.spawn(EntityType.ARMOR_STAND, ORIGIN.add(4, 0, 3.5));
        stand.setNoGravity(true);
        Villager villager = h.spawn(EntityType.VILLAGER, ORIGIN.add(-1.2, 0, 3.2));
        villager.setNoAi(true);
        Cow cow = h.spawn(EntityType.COW, ORIGIN.add(1.2, 0, 3.0));
        cow.setNoAi(true);

        float wardenBefore = warden.getHealth();
        float villagerBefore = villager.getHealth();
        float cowBefore = cow.getHealth();

        var state = AbilityStorage.get(player);
        state.presets.edit(0, 0, "hydro_fan", state::learned);
        state.presets.edit(0, 1, "hydro_whip", state::learned);

        aimAt(player, warden.getBoundingBox().getCenter());
        boolean fanOk = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
        h.assertTrue(fanOk, "hydro_fan accepts Warden boss in cone");

        h.runAfterDelay(6, () -> {
            h.assertTrue(warden.getHealth() < wardenBefore, "hydro_fan deals real damage to Boss entity (Warden)");
            h.assertTrue(villager.getHealth() == villagerBefore && cow.getHealth() == cowBefore,
                    "AoE skill leaves passive Villager and Cow completely unharmed");

            aimAt(player, stand.getBoundingBox().getCenter());
            boolean whipOk = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "1"));
            h.assertTrue(whipOk && stand.lastHit == h.getLevel().getGameTime(),
                    "Direct skill lands the armor stand's native first hit");
            h.assertTrue(stand.hurt(player.damageSources().playerAttack(player), 1) && stand.isRemoved(),
                    "The next native hit breaks the armor stand");

            warden.discard();
            stand.discard();
            villager.discard();
            cow.discard();
            player.server.getPlayerList().remove(player);
            h.succeed();
        });
    }

    /** 3. P1 Gate: DryFire honesty (Phase.START, 0 paper spent) & 4 LV5 ultimates real damage. */
    @GameTest(template = "arena", batch = "a387_p1_ultimates", timeoutTicks = 120)
    public static void p1_dryfire_honesty_and_lv5_ultimates(GameTestHelper h) {
        h.assertTrue(CompatMode.openGameplay(), "Open gameplay enabled by the production mod");
        buildFloor(h, Blocks.STONE);
        h.setBlock(new BlockPos(8, 2, 5), Blocks.WATER);

        var player = join(h, "P1U", GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(ORIGIN), 0, -75);
        player.setYHeadRot(0);
        player.setOnGround(true);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.PAPER, 16));

        var visuals = new ArrayList<CanonVfx.Visual>();
        var prevObserver = CanonVfx.serverObserver;
        CanonVfx.serverObserver = v -> {
            if (v.owner().equals(player.getUUID())) visuals.add(v);
        };

        var cmd = player.createCommandSourceStack().withPermission(4);
        player.server.getCommands().performPrefixedCommand(cmd, "academy dev paper_psychokinesis");
        var state = AbilityStorage.get(player);
        state.presets.edit(0, 0, "paper_blade", state::learned);

        try {
            boolean dryOk = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
            h.assertTrue(dryOk, "Dry-fire into empty sky succeeds cleanly");
            h.assertTrue(player.getMainHandItem().getCount() == 16, "Dry-fire consumes zero paper sheets");
            h.assertTrue(visuals.stream().anyMatch(v -> v.skill().equals("paper_blade") && v.phase() == CanonVfx.Phase.START)
                    && visuals.stream().noneMatch(v -> v.skill().equals("paper_blade") && v.phase() == CanonVfx.Phase.HIT),
                    "Dry-fire emits START micro-gleam, never fake HIT VFX");
        } finally {
            CanonVfx.serverObserver = prevObserver;
        }

        // Test solo mental_out_crowd + nitrogen_dome + float_field + hydro_domain real damage
        Zombie zMental = h.spawn(EntityType.ZOMBIE, ORIGIN.add(0, 0, 3.0));
        zMental.setNoAi(true);
        float zmBefore = zMental.getHealth();
        player.server.getCommands().performPrefixedCommand(cmd, "academy dev mental_out");
        AbilityStorage.get(player).presets.edit(0, 0, "mental_out_crowd", AbilityStorage.get(player)::learned);
        aimAt(player, zMental.getBoundingBox().getCenter());
        boolean crowdOk = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
        h.assertTrue(crowdOk && zMental.getHealth() < zmBefore, "Solo mental_out_crowd deals psychic crush damage");

        Zombie zNitrogen = h.spawn(EntityType.ZOMBIE, ORIGIN.add(2.0, 0, 3.0));
        zNitrogen.setNoAi(true);
        float znBefore = zNitrogen.getHealth();
        player.server.getCommands().performPrefixedCommand(cmd, "academy dev nitrogen_armor");
        var naState = AbilityStorage.get(player);
        naState.presets.edit(0, 0, "nitrogen_armor", naState::learned);
        naState.presets.edit(0, 1, "nitrogen_dome", naState::learned);
        AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
        boolean domeOk = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "1"));
        h.assertTrue(domeOk && zNitrogen.getHealth() < znBefore, "nitrogen_dome cryo-nova deals activation damage");

        // Submerge a zombie in water for hydro_domain and another for float_field
        for (int x = 13; x <= 16; x++) for (int z = 6; z <= 9; z++) h.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
        Zombie zHydro = h.spawn(EntityType.ZOMBIE, new Vec3(14.5, 2.0, 7.5));
        zHydro.setNoAi(true);
        zHydro.baseTick();
        float zhBefore = zHydro.getHealth();

        player.server.getCommands().performPrefixedCommand(cmd, "academy dev hydro_hand");
        AbilityStorage.get(player).presets.edit(0, 0, "hydro_domain", AbilityStorage.get(player)::learned);
        boolean hydroOk = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
        h.assertTrue(hydroOk, "hydro_domain starts");

        Zombie zFloat = h.spawn(EntityType.ZOMBIE, ORIGIN.add(-2.0, 0, 3.0));
        zFloat.setNoAi(true);
        float zfBefore = zFloat.getHealth();
        player.server.getCommands().performPrefixedCommand(cmd, "academy dev float_dial");
        AbilityStorage.get(player).presets.edit(0, 0, "float_field", AbilityStorage.get(player)::learned);
        boolean floatOk = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
        h.assertTrue(floatOk, "float_field starts");

        for (int k = 1; k <= 32; k++) {
            h.runAfterDelay(k, () -> {
                zHydro.baseTick();
                if (!player.isRemoved()) {
                    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(player));
                    NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
                }
            });
        }

        h.runAfterDelay(36, () -> {
            h.assertTrue(zHydro.getHealth() < zhBefore, "hydro_domain deals crushing water damage over time");
            h.assertTrue(zFloat.getHealth() < zfBefore, "float_field deals gravity shear damage over time");
            zMental.discard();
            zNitrogen.discard();
            zHydro.discard();
            zFloat.discard();
            player.server.getPlayerList().remove(player);
            h.succeed();
        });
    }

    /** 4. P2 Gate: Endpoint ground blast + crater, ElectronBomb splash, RayBarrage refraction, ThunderClap >=20t release. */
    @GameTest(template = "arena", batch = "a387_p2_blasts", timeoutTicks = 120)
    public static void p2_twostage_endpoint_blasts_and_splash(GameTestHelper h) {
        h.assertTrue(CompatMode.openGameplay(), "Open gameplay enabled by the production mod");
        buildFloor(h, Blocks.DIRT);

        var player = join(h, "P2B", GameType.SURVIVAL);
        player.moveTo(h.absoluteVec(ORIGIN), 0, 0);
        player.setOnGround(true);
        var cmd = player.createCommandSourceStack().withPermission(4);

        // 4A: CanonEndpointBlast dark_rain aimed at dirt floor next to zombie -> splash damage + restorable vacancy crater
        Zombie zBlast = h.spawn(EntityType.ZOMBIE, new Vec3(10.5, 2.0, 9.8));
        zBlast.setNoAi(true);
        float zbBefore = zBlast.getHealth();
        player.server.getCommands().performPrefixedCommand(cmd, "academy dev dark_matter");
        AbilityStorage.get(player).presets.edit(0, 0, "dark_rain", AbilityStorage.get(player)::learned);
        // Aim at ground 2m to the left of zBlast so direct target lock misses and CanonEndpointBlast detonates on terrain
        aimAt(player, h.absoluteVec(new Vec3(8.8, 1.9, 9.8)));
        boolean rainBlast = AcademyGameplay.requestFromClient(player, new AcademyNetwork.Request("slot_press", "0"));
        h.assertTrue(rainBlast, "dark_rain ground detonation accepted by CanonEndpointBlast");
        h.assertTrue(zBlast.getHealth() < zbBefore, "dark_rain endpoint blast deals splash damage to nearby zombie");

        // 4B: RayBarrage non-Silbarn scatter hits primary AND wedge zombie
        Zombie zRayMain = h.spawn(EntityType.ZOMBIE, new Vec3(10.5, 2.0, 12.5));
        Zombie zRaySide = h.spawn(EntityType.ZOMBIE, new Vec3(12.2, 2.0, 12.5));
        zRayMain.setNoAi(true);
        zRaySide.setNoAi(true);
        float zrmBefore = zRayMain.getHealth();
        float zrsBefore = zRaySide.getHealth();
        player.server.getCommands().performPrefixedCommand(cmd, "academy dev meltdowner");
        aimAt(player, zRayMain.getBoundingBox().getCenter());
        boolean rbOk = RayBarrage.start(player);
        h.assertTrue(rbOk && zRayMain.getHealth() < zrmBefore && zRaySide.getHealth() < zrsBefore,
                "RayBarrage without Silbarn damages both primary target and forward-wedge secondary target");

        // 4C: ElectronBomb block-impact splash damages adjacent zombie
        BlockPos wallPos = new BlockPos(6, 2, 8);
        h.setBlock(wallPos, Blocks.STONE);
        h.setBlock(wallPos.above(), Blocks.STONE);
        Zombie zBombSide = h.spawn(EntityType.ZOMBIE, new Vec3(7.2, 2.0, 8.5));
        zBombSide.setNoAi(true);
        float zbsBefore = zBombSide.getHealth();
        AbilityStorage.get(player).cooldowns.clear();
        aimAt(player, h.absoluteVec(new Vec3(6.5, 2.5, 8.2)));
        boolean ebOk = ElectronBomb.perform(player);
        h.assertTrue(ebOk, "ElectronBomb starts");

        for (int k = 1; k <= 8; k++) {
            h.runAfterDelay(k, () -> ElectronBomb.tick(player));
        }

        // 4D: ThunderClap >=20-tick early key release strikes target
        Zombie zThunder = h.spawn(EntityType.ZOMBIE, new Vec3(15.5, 2.0, 10.5));
        zThunder.setNoAi(true);
        float ztBefore = zThunder.getHealth();

        h.runAfterDelay(12, () -> {
            // Check crater vacancy from dark_rain after queued SkillEnvironment ticks
            boolean hasVacancy = false;
            for (int x = 6; x <= 12; x++) {
                for (int z = 7; z <= 12; z++) {
                    if (SkillEnvironment.isVacancy(h.getBlockState(new BlockPos(x, 1, z)))) {
                        hasVacancy = true;
                        break;
                    }
                }
            }
            h.assertTrue(hasVacancy, "Endpoint blast creates managed restorable crater on dirt floor");
            h.assertTrue(zBombSide.getHealth() < zbsBefore, "ElectronBomb wall impact deals splash damage");

            player.server.getCommands().performPrefixedCommand(cmd, "academy dev electromaster");
            aimAt(player, h.absoluteVec(new Vec3(15.5, 1.9, 10.5)));
            h.assertTrue(ThunderClap.start(player), "ThunderClap starts charging");
        });

        for (int k = 13; k <= 37; k++) {
            h.runAfterDelay(k, () -> {
                aimAt(player, h.absoluteVec(new Vec3(15.5, 1.9, 10.5)));
                ThunderClap.tick(player);
            });
        }

        h.runAfterDelay(38, () -> {
            int held = ThunderClap.heldTicks(player);
            h.assertTrue(held >= 20 && held < 40, "Held ticks in early-release window [20, 40): " + held);
            h.assertTrue(ThunderClap.release(player), "ThunderClap releases early");
        });

        h.runAfterDelay(44, () -> {
            h.assertTrue(zThunder.getHealth() < ztBefore,
                    "ThunderClap >=20-tick key release calls down proportional lightning damage");
            zBlast.discard();
            zRayMain.discard();
            zRaySide.discard();
            zBombSide.discard();
            zThunder.discard();
            player.server.getPlayerList().remove(player);
            h.succeed();
        });
    }
}
