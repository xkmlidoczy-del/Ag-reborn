package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.develop.InductionFactors;
import com.mojang.authlib.GameProfile;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Server-world integration tests. FakePlayer's network handler discards packets:
 * requests below exercise the authoritative request handler, not wire transport.
 * FakePlayer does not tick itself, so NeoForge player-post-tick events are posted
 * explicitly to test the registered gameplay subscriber at exact tick boundaries.
 */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class AcademyRuntimeTests {
    private static final String TEMPLATE = "runtime_empty";
    private static final double EPSILON = 0.00001;

    private AcademyRuntimeTests() {}

    /** In-memory template keeps test support out of the gameplay resource pack. */
    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        var tag = new CompoundTag();
        var size = new ListTag();
        size.add(IntTag.valueOf(8));
        size.add(IntTag.valueOf(6));
        size.add(IntTag.valueOf(56));
        tag.put("size", size);
        var blocks = new ListTag();
        var originAir = new CompoundTag();
        var origin = new ListTag();
        origin.add(IntTag.valueOf(0));
        origin.add(IntTag.valueOf(0));
        origin.add(IntTag.valueOf(0));
        originAir.put("pos", origin);
        originAir.putInt("state", 0);
        blocks.add(originAir);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        var palette = new ListTag();
        var air = new CompoundTag();
        air.putString("Name", "minecraft:air");
        palette.add(air);
        tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void arc_entity_hit_charges_cost_and_experience(GameTestHelper helper) {
        var player = player(helper);
        try {
            var state = ready(player, "arc_gen", 1, 0);
            var target = target(helper, 3.5, 6.5);
            double originalCp = state.cp;
            request(player, "cast", "arc_gen");
            close(helper, target.getHealth(), 195, "ArcGen entity damage");
            close(helper, state.cp, originalCp - 30, "ArcGen CP cost");
            close(helper, state.overload, 18, "ArcGen overload cost");
            close(helper, state.extraCp, .075, "ArcGen CP training gain");
            close(helper, state.extraOverload, .1044, "ArcGen overload training gain");
            close(helper, state.exp("arc_gen"), .0048, "ArcGen entity-hit experience");
            close(helper, state.levelExperience, .0048, "ArcGen level experience");
            helper.assertValueEqual(state.cpDelay, 15, "CP recovery delay");
            helper.assertValueEqual(state.overloadDelay, 32, "overload recovery delay");
            helper.assertValueEqual(state.cooldowns.get("arc_gen"), 14, "post-experience ArcGen cooldown");
            var saved = player.getPersistentData().getCompound("academy:classic_progress");
            close(helper, saved.getDouble("cp"), state.cp, "request persists authoritative CP");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void arc_block_occludes_entity_and_awards_block_experience(GameTestHelper helper) {
        var player = player(helper);
        try {
            var state = ready(player, "arc_gen", 1, 0);
            var target = target(helper, 3.5, 6.5);
            helper.setBlock(new BlockPos(3, 2, 4), Blocks.STONE);
            request(player, "cast", "arc_gen");
            close(helper, target.getHealth(), 200, "solid block occludes ArcGen damage");
            close(helper, state.exp("arc_gen"), .0018, "block-hit experience");
            close(helper, state.overload, 18, "occluded cast still pays overload");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void arc_miss_pays_cost_without_experience(GameTestHelper helper) {
        var player = player(helper);
        try {
            var state = ready(player, "arc_gen", 1, 1);
            double originalCp = state.cp;
            request(player, "cast", "arc_gen");
            close(helper, state.cp, originalCp - 70, "trained ArcGen miss CP cost");
            close(helper, state.overload, 11, "trained ArcGen overload cost");
            close(helper, state.levelExperience, 0, "miss has no experience gain");
            helper.assertValueEqual(state.cooldowns.get("arc_gen"), 5, "trained ArcGen cooldown");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void arc_server_gates_reject_invalid_casts(GameTestHelper helper) {
        var player = player(helper);
        try {
            var state = ready(player, "arc_gen", 1, 0);
            double originalCp = state.cp;
            state.activated = false;
            helper.assertFalse(AcademyGameplay.arc(player), "inactive cast rejected");
            state.activated = true;
            state.interfering = true;
            helper.assertFalse(AcademyGameplay.arc(player), "interfered cast rejected");
            state.interfering = false;
            state.overloadFine = false;
            helper.assertFalse(AcademyGameplay.arc(player), "overloaded cast rejected");
            state.overloadFine = true;
            state.cooldowns.put("arc_gen", 1);
            helper.assertFalse(AcademyGameplay.arc(player), "cooldown cast rejected");
            state.cooldowns.clear();
            state.experience.clear();
            helper.assertFalse(AcademyGameplay.arc(player), "unlearned cast rejected");
            close(helper, state.cp, originalCp, "rejected casts preserve CP");
            close(helper, state.overload, 0, "rejected casts preserve overload");
            state.experience.put("arc_gen", 0.0);
            state.cp = 29;
            helper.assertFalse(AcademyGameplay.arc(player), "insufficient CP rejected");
            close(helper, state.cp, 29, "failed cost check preserves CP");
            helper.assertTrue(state.cooldowns.isEmpty(), "failed cast has no cooldown");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void arc_cooldown_and_resource_delays_follow_player_ticks(GameTestHelper helper) {
        var player = player(helper);
        try {
            var state = ready(player, "arc_gen", 1, 0);
            request(player, "cast", "arc_gen");
            double paidCp = state.cp;
            helper.assertValueEqual(state.cooldowns.get("arc_gen"), 15, "miss cooldown");
            for (int i = 0; i < 14; i++) tick(player);
            helper.assertFalse(AcademyGameplay.arc(player), "cannot recast a tick early");
            close(helper, state.cp, paidCp, "CP does not recover during delay");
            tick(player);
            helper.assertTrue(state.cooldowns.isEmpty(), "cooldown clears on fifteenth event");
            close(helper, state.cp, paidCp, "CP delay reaches zero before recovery");
            tick(player);
            helper.assertTrue(state.cp > paidCp, "CP recovery resumes after delay");
            helper.assertTrue(AcademyGameplay.arc(player), "can cast after cooldown expires");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime", timeoutTicks = 240)
    public static void developer_requests_require_item_and_server_prerequisites(GameTestHelper helper) {
        var player = player(helper);
        cleanupOnCompletion(helper, player);
        var state = AbilityStorage.get(player);
        request(player, "develop_level", "");
        helper.assertFalse(state.hasCategory(), "category acquisition requires a main-hand portable developer");
        player.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(AcademyCraft.DEVELOPER.get()));
        request(player, "develop_level", "");
        helper.assertFalse(DevelopmentController.process(player).isDeveloping(), "offhand developer cannot start source portable development");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.DEVELOPER.get()));
        var energy = new DeveloperItemEnergy(player.getMainHandItem(), DeveloperType.PORTABLE);
        close(helper, energy.energy(), 0, "unseeded portable developer is empty");
        energy.energy(10000); // Finite test-fixture battery, not a gameplay refill.
        request(player, "select", "invalid_category");
        request(player, "select", "electromaster");
        helper.assertFalse(state.hasCategory(), "untrusted instant category selectors are ignored");
        request(player, "learn", "arc_gen");
        helper.assertFalse(DevelopmentController.process(player).isDeveloping(), "ArcGen rejects before category/level acquisition");
        var factor = InductionFactors.stack("electromaster");
        factor.setCount(2);
        player.getInventory().setItem(1, factor);
        request(player, "develop_level", "");
        helper.assertTrue(DevelopmentController.process(player).isDeveloping(), "valid portable category request starts timed development");
        final long startedAt = helper.getLevel().getGameTime();
        final long[] lastTick = {startedAt};
        helper.onEachTick(() -> {
            long now = helper.getLevel().getGameTime();
            if (helper.testInfo.isDone() || now <= lastTick[0]) return;
            helper.assertValueEqual(now - lastTick[0], 1L, "development advances with consecutive real world ticks");
            lastTick[0] = now;
            tick(player);
            long elapsed = now - startedAt;
            if (elapsed < 130) helper.assertFalse(state.hasCategory(), "five stimulations cannot complete early");
            if (elapsed == 130) {
                helper.assertValueEqual(state.category, "electromaster", "real induction factor selects acquired category");
                helper.assertValueEqual(state.level, 1, "category acquisition promotes to first level");
                helper.assertTrue(player.getInventory().getItem(1).isEmpty(), "source category acquisition consumes entire factor slot");
                close(helper, energy.energy(), 6100, "130 category ticks consume 3900 IF");
                request(player, "learn", "railgun");
                helper.assertFalse(DevelopmentController.process(player).isDeveloping(), "Railgun level prerequisites reject");
                request(player, "learn", "thunder_bolt");
                helper.assertFalse(DevelopmentController.process(player).isDeveloping(), "unsupported skill request rejects");
                request(player, "learn", "arc_gen");
                helper.assertTrue(DevelopmentController.process(player).isDeveloping(), "eligible first skill starts finite stimulation");
                helper.assertFalse(state.learned("arc_gen"), "learning does not grant the skill immediately");
            }
            if (elapsed > 130 && elapsed < 208) helper.assertFalse(state.learned("arc_gen"), "three skill stimulations cannot complete early");
            if (elapsed == 208) {
                helper.assertTrue(state.learned("arc_gen"), "ArcGen commits after 78 actual world ticks");
                close(helper, energy.energy(), 3760, "three skill stimulations consume another 2340 IF");
                helper.assertTrue(player.getPersistentData().getCompound("academy:classic_progress").getCompound("skills").contains("arc_gen"),
                        "completed development persists learned skill");
                player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
                state.levelExperience = 100;
                request(player, "develop_level", "");
                helper.assertFalse(DevelopmentController.process(player).isDeveloping(), "offhand alone cannot upgrade");
                helper.assertValueEqual(state.level, 1, "missing main-hand developer preserves level");
                request(player, "toggle", "");
                helper.assertTrue(state.activated, "server activation toggle remains authoritative");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void ability_progress_survives_player_nbt_and_cache_eviction(GameTestHelper helper) throws IOException {
        var player = player(helper);
        FakePlayer restored = null;
        try {
            var state = ready(player, "arc_gen", 4, .25);
            state.experience.put("railgun", .6);
            state.cp = 3210.5;
            state.overload = 111.25;
            state.extraCp = 42;
            state.extraOverload = 9;
            state.levelExperience = 1.25;
            state.cpDelay = 11;
            state.overloadDelay = 22;
            state.cooldowns.put("arc_gen", 7);
            state.cooldowns.put("railgun", 150);
            AbilityStorage.save(player);
            var before = AbilityStorage.encodeSaved(state);
            var entityTag = player.saveWithoutId(new CompoundTag());
            var bytes = new ByteArrayOutputStream();
            NbtIo.writeCompressed(entityTag, bytes);
            var loaded = NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()), NbtAccounter.unlimitedHeap());
            logout(player);
            restored = new FakePlayer(helper.getLevel(), player.getGameProfile());
            restored.load(loaded);
            var after = AbilityStorage.get(restored);
            helper.assertTrue(state != after, "cache eviction creates a distinct state object");
            helper.assertValueEqual(AbilityStorage.encodeSaved(after), before, "player NBT roundtrip preserves every genuinely persisted source progress field");
            helper.assertTrue(after.cooldowns.isEmpty()&&after.cooldownMaxTicks.isEmpty(),"original non-NBT CooldownData is fresh after native player load");
            helper.succeed();
        } finally {
            if (restored != null) logout(restored);
            else logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void ability_progress_copies_on_respawn_event(GameTestHelper helper) {
        var original = player(helper);
        var replacement = new FakePlayer(helper.getLevel(), original.getGameProfile());
        try {
            var state = ready(original, "arc_gen", 3, .7);
            state.cp = 1100;
            state.overload = 99;
            state.cooldowns.put("arc_gen", 4);
            var expectedState = AbilityStorage.decode(AbilityStorage.encode(state));
            expectedState.recoverAll();
            expectedState.activated = false;
            expectedState.cooldowns.clear();expectedState.cooldownMaxTicks.clear();
            var expected = AbilityStorage.encode(expectedState);
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, true));
            helper.assertValueEqual(AbilityStorage.encode(AbilityStorage.get(replacement)), expected,
                    "source death clone preserves progression, clears transient cooldown, recovers resources, and deactivates");
            helper.succeed();
        } finally {
            logout(replacement);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void railgun_ingot_fires_exactly_after_twenty_ticks(GameTestHelper helper) {
        successfulCharge(helper, Items.IRON_INGOT);
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void railgun_iron_block_fires_exactly_after_twenty_ticks(GameTestHelper helper) {
        successfulCharge(helper, Items.IRON_BLOCK);
    }

    private static void successfulCharge(GameTestHelper helper, Item ammunition) {
        var player = player(helper);
        try {
            var state = ready(player, "railgun", 4, 0);
            var target = target(helper, 3.5, 6.5);
            var beyondBeam = target(helper, 6.5, 6.5);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(ammunition, 3));
            helper.setBlock(new BlockPos(3, 2, 8), Blocks.STONE);
            helper.setBlock(new BlockPos(3, 2, 10), Blocks.BEDROCK);
            helper.setBlock(new BlockPos(3, 2, 12), Blocks.STONE);
            request(player, "charge", "railgun");
            for (int i = 0; i < 10; i++) tick(player);
            request(player, "charge", "railgun");
            for (int i = 10; i < 19; i++) tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "no ammo consumed before twentieth tick");
            close(helper, state.cp, 5800, "no CP cost before release");
            close(helper, target.getHealth(), 200, "no early beam damage");
            tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "release consumes one ammunition item");
            close(helper, state.cp, 5600, "release charges Railgun CP cost");
            close(helper, state.overload, 180, "release charges Railgun overload");
            close(helper, state.exp("railgun"), .005, "successful Railgun experience");
            helper.assertValueEqual(state.cooldowns.get("railgun"), 300, "release starts cooldown");
            double lateral = target.position().subtract(player.getEyePosition()).cross(player.getLookAngle()).length();
            close(helper, target.getHealth(), 200 - 60 * (1 - .8 * lateral / 50), "beam damage and lateral attenuation");
            close(helper, beyondBeam.getHealth(), 200, "entity outside beam radius unaffected");
            helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, 2, 8));
            helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(3, 2, 10));
            helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 12));
            request(player, "charge", "railgun");
            for (int i = 0; i < 20; i++) tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "cooldown rejects another charge");
            for (int i = 20; i < 300; i++) tick(player);
            helper.assertTrue(state.canUse("railgun"), "Railgun becomes usable when cooldown and overload recover");
            request(player, "charge", "railgun");
            for (int i = 0; i < 20; i++) tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "Railgun can fire again after cooldown expires");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void railgun_failed_cp_check_preserves_legacy_ammo_order(GameTestHelper helper) {
        var player = player(helper);
        try {
            var state = ready(player, "railgun", 4, 0);
            state.cp = 0;
            state.cpDelay = 15;
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT, 3));
            request(player, "charge", "railgun");
            for (int i = 0; i < 19; i++) tick(player);
            var expected = AbilityStorage.decode(AbilityStorage.encode(state));
            expected.tick();
            tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "ammunition is consumed before insufficient-CP check");
            close(helper, state.cp, expected.cp, "failed CP check has no resource debit");
            close(helper, state.overload, 0, "failed CP check has no overload");
            close(helper, state.exp("railgun"), 0, "failed CP check has no experience");
            helper.assertTrue(state.cooldowns.isEmpty(), "failed CP check has no cooldown");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void railgun_abort_and_invalid_ammunition_cancel_charge(GameTestHelper helper) {
        var player = player(helper);
        try {
            var state = ready(player, "railgun", 4, 0);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT, 3));
            request(player, "charge", "railgun");
            for (int i = 0; i < 10; i++) tick(player);
            request(player, "abort", "railgun");
            for (int i = 0; i < 20; i++) tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "abort preserves ammunition");
            request(player, "charge", "railgun");
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLD_INGOT, 3));
            tick(player);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT, 3));
            for (int i = 0; i < 20; i++) tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "item switch cancels instead of pausing charge");
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.GOLD_INGOT, 3));
            request(player, "charge", "railgun");
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT, 3));
            for (int i = 0; i < 20; i++) tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "unsupported ammunition cannot start charge");
            close(helper, state.cp, 5800, "canceled charges cost no CP");
            close(helper, state.overload, 0, "canceled charges cost no overload");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void railgun_deactivation_and_logout_cancel_charge(GameTestHelper helper) {
        var player = player(helper);
        try {
            var state = ready(player, "railgun", 4, 0);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT, 3));
            request(player, "charge", "railgun");
            request(player, "toggle", "");
            tick(player);
            request(player, "toggle", "");
            for (int i = 0; i < 20; i++) tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "deactivation cancels charge");
            request(player, "charge", "railgun");
            logout(player);
            for (int i = 0; i < 20; i++) tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "logout clears charge");
            close(helper, state.exp("railgun"), 0, "canceled charge has no experience");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    @GameTest(template = TEMPLATE, batch = "academy_runtime")
    public static void creative_casts_preserve_cp_overload_and_ammunition(GameTestHelper helper) {
        var player = player(helper);
        try {
            var state = ready(player, "arc_gen", 4, 0);
            state.experience.put("railgun", 0.0);
            state.cp = 0;
            state.cpDelay = 15;
            player.getAbilities().instabuild = true;
            helper.assertTrue(AcademyGameplay.arc(player), "creative bypasses ArcGen CP insufficiency");
            close(helper, state.cp, 0, "creative ArcGen keeps CP");
            close(helper, state.overload, 0, "creative ArcGen keeps overload");
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT, 3));
            request(player, "charge", "railgun");
            for (int i = 0; i < 19; i++) tick(player);
            var expected = AbilityStorage.decode(AbilityStorage.encode(state));
            expected.tick();
            tick(player);
            helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "creative Railgun retains ammunition");
            close(helper, state.cp, expected.cp, "creative Railgun preserves recovered CP");
            close(helper, state.overload, 0, "creative Railgun keeps overload");
            close(helper, state.exp("railgun"), .005, "creative Railgun earns experience");
            helper.assertValueEqual(state.cooldowns.get("railgun"), 300, "creative Railgun still has cooldown");
            helper.succeed();
        } finally {
            logout(player);
        }
    }

    private static void cleanupOnCompletion(GameTestHelper helper, FakePlayer player) {
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) {}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { logout(player); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { logout(player); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo original,
                    net.minecraft.gametest.framework.GameTestInfo rerun, net.minecraft.gametest.framework.GameTestRunner runner) { logout(player); }
        });
    }

    private static FakePlayer player(GameTestHelper helper) {
        var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-GameTest]"));
        var position = helper.absoluteVec(new Vec3(3.5, 1, 2.5));
        player.moveTo(position.x, position.y, position.z, 0, 0);
        // LivingEntity starts with a random head yaw; moveTo resets body yaw only.
        // Classic abilities deliberately aim from the head, so pin both fixture axes.
        player.setYHeadRot(0);
        player.getAbilities().instabuild = false;
        return player;
    }

    private static AbilityProgress ready(FakePlayer player, String skill, int level, double experience) {
        var state = AbilityStorage.get(player);
        state.selectCategory("electromaster");
        state.setLevel(level);
        state.activated = true;
        state.experience.put(skill, experience);
        return state;
    }

    private static Villager target(GameTestHelper helper, double x, double z) {
        var target = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(x, 1, z));
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        helper.testInfo.addListener(new net.minecraft.gametest.framework.GameTestListener() {
            public void testStructureLoaded(net.minecraft.gametest.framework.GameTestInfo info) {}
            public void testPassed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { target.discard(); }
            public void testFailed(net.minecraft.gametest.framework.GameTestInfo info, net.minecraft.gametest.framework.GameTestRunner runner) { target.discard(); }
            public void testAddedForRerun(net.minecraft.gametest.framework.GameTestInfo original,
                    net.minecraft.gametest.framework.GameTestInfo rerun, net.minecraft.gametest.framework.GameTestRunner runner) { target.discard(); }
        });
        return target;
    }

    private static void request(FakePlayer player, String action, String value) {
        AcademyGameplay.request(player, new AcademyNetwork.Request(action, value));
    }

    private static void tick(FakePlayer player) {
        player.tickCount++;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    }

    private static void logout(FakePlayer player) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
    }

    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) <= EPSILON,
                label + ": expected " + expected + ", got " + actual);
    }
}
