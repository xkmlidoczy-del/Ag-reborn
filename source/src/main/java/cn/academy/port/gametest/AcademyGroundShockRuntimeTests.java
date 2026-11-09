package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.GroundShockWave;
import cn.academy.port.skill.GroundShock;
import cn.academy.port.skill.ClassicRaytrace;
import cn.academy.port.skill.Plotter;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongConsumer;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Native server-world/request tests for the 1.0.7 Groundshock adaptation.
 * Real ServerLevel game time advances held input; FakePlayer receives exactly one
 * player-post-tick event per observed world tick. Its network handler discards
 * payloads, so these tests make no socket/codec, real-client, rendering or audio claim.
 * Fixtures and event listeners are cleaned up on pass, failure, timeout and rerun.
 */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy")
public final class AcademyGroundShockRuntimeTests {
    private static final String ID = GroundShockWave.ID;
    private static final String TEMPLATE = "runtime_empty", WIDE_TEMPLATE = "ground_shock_runtime_empty";
    private static final String BATCH = "academy_ground_shock";
    private static final double EPSILON = .00001;
    private AcademyGroundShockRuntimeTests() {}

    /** Mastery clears have their own footprint: never mutate a neighboring 8-wide test. */
    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        var tag = new CompoundTag(); var size = new ListTag();
        // Forty-wide also contains a full 25-iteration +X wave if a regression
        // incorrectly substitutes live mastery for the captured novice parameters.
        size.add(IntTag.valueOf(40)); size.add(IntTag.valueOf(6)); size.add(IntTag.valueOf(56)); tag.put("size", size);
        var blocks = new ListTag(); var originAir = new CompoundTag(); var origin = new ListTag();
        origin.add(IntTag.valueOf(0)); origin.add(IntTag.valueOf(0)); origin.add(IntTag.valueOf(0));
        originAir.put("pos", origin); originAir.putInt("state", 0); blocks.add(originAir); tag.put("blocks", blocks);
        tag.put("entities", new ListTag()); var palette = new ListTag(); var air = new CompoundTag();
        air.putString("Name", "minecraft:air"); palette.add(air); tag.put("palette", palette);
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy", WIDE_TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_four_world_tick_release_cancels_without_mutation(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        f.groundLine(3, 3, 12, Blocks.STONE); var target = f.target(3.5, 4.5);
        request(player, "skill_start");
        helper.assertTrue(GroundShock.active(player), "request starts the server-owned hold");
        assertFree(helper, state, 1800, 0, "initial key down");
        f.worldTicks(elapsed -> {
            if (elapsed == 4) {
                request(player, "skill_release"); request(player, "skill_release");
                helper.assertFalse(GroundShock.active(player), "four actual world ticks terminate the context");
                assertFree(helper, state, 1800, 0, "early release and replay");
                close(helper, target.getHealth(), 200, "early release attacks nobody");
                helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 0, 3)); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_five_ticks_commit_once_and_deduplicate_overlapping_target(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        f.groundLine(3, 3, 12, Blocks.STONE); var target = f.target(3.5, 4.5);
        var hook = new AttackHook(player, target, false); f.hook(hook); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 3) { request(player, "skill_start"); assertFree(helper, state, 1800, 0, "duplicate key down"); }
            if (elapsed == 5) {
                request(player, "skill_release");
                helper.assertFalse(GroundShock.active(player), "accepted release removes hold before doing work");
                close(helper, state.cp, 1720, "novice release pays exactly 80 CP");
                close(helper, state.overload, 15, "novice release pays 15 strain");
                close(helper, state.extraCp, .2, "release trains maximum CP");
                close(helper, state.extraOverload, .087, "release trains maximum overload");
                close(helper, target.getHealth(), 196, "real native entity receives captured damage4");
                helper.assertValueEqual(hook.calls, 1, "target overlapping successive ground cells is attacked once");
                helper.assertValueEqual(hook.skill, "vecmanip.ground_shock", "mutable attack hook receives exact skill source");
                close(helper, state.exp(ID), GroundShockWave.ENTITY_EXPERIENCE + GroundShockWave.PERFORM_EXPERIENCE,
                        "one target contributes .002 plus final .001");
                close(helper, state.levelExperience, GroundShockWave.ENTITY_EXPERIENCE + GroundShockWave.PERFORM_EXPERIENCE,
                        "entity and performance EXP also train level progress");
                helper.assertValueEqual(state.cooldowns.get(ID), 80, "captured novice cooldown starts at80");
                helper.assertValueEqual(state.cpDelay, 15, "successful consume installs CP recovery delay");
                helper.assertValueEqual(state.overloadDelay, 32, "successful consume installs strain recovery delay");
                assertLaunch(helper, target, .48, .72);
                var saved = player.getPersistentData().getCompound("academy:classic_progress");
                close(helper, saved.getDouble("cp"), 1720, "request persists committed CP");
                request(player, "skill_release"); request(player, "skill_start"); request(player, "skill_release");
                close(helper, state.cp, 1720, "release replay and cooldown-gated restart cannot repay");
                close(helper, state.overload, 15, "replay cannot add strain");
                helper.assertValueEqual(hook.calls, 1, "release replay cannot redo entity attack");
                close(helper, state.exp(ID), .003, "release replay cannot award extra EXP");
                helper.assertFalse(GroundShock.active(player), "cooldown refuses a new hold"); helper.succeed();
            }
        });
    }

    @GameTest(template = WIDE_TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_release_head_aim_and_live_mastery_preserve_captured_novice_parameters(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(5.5, 8.5); var state = ready(player, 0);
        for (int x = 6; x <= 15; x++) helper.setBlock(new BlockPos(x, 0, 8), Blocks.STONE);
        var oldAim = f.target(5.5, 14.5); var currentAim = f.target(8.5, 8.5);
        var soft = new BlockPos(0, 0, 3); var hard = new BlockPos(0, 0, 4);
        helper.setBlock(soft, Blocks.DIRT); helper.setBlock(hard, Blocks.STONE);
        request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 3) { player.setYHeadRot(-90); state.experience.put(ID, 1.0); }
            if (elapsed == 5) {
                request(player, "skill_release");
                close(helper, oldAim.getHealth(), 200, "old start aim receives no attack");
                close(helper, currentAim.getHealth(), 196, "current release head aim gets captured damage4");
                close(helper, state.cp, 1720, "live mastery1 cannot change captured CP80");
                close(helper, state.overload, 15, "live mastery1 cannot change captured strain15");
                helper.assertValueEqual(state.cooldowns.get(ID), 80, "live mastery1 cannot change captured cooldown80");
                assertLaunch(helper, currentAim, .48, .72);
                helper.assertBlockPresent(Blocks.AIR, soft); helper.assertBlockPresent(Blocks.STONE, hard);
                close(helper, state.exp(ID), 1, "live mastery cap remains1");
                close(helper, state.levelExperience, .003, "EXP remains credited even when skill is already capped"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_mature_low_cp_and_airborne_releases_abort_without_mutation(GameTestHelper helper) {
        var f = new Fixture(helper); var poor = f.player(); var flying = f.player(6.5, 2.5);
        var poorState = ready(poor, 0); poorState.cp = 79; var flyingState = ready(flying, 0);
        flying.setOnGround(false); f.groundLine(3, 3, 12, Blocks.STONE); f.groundLine(6, 3, 12, Blocks.STONE);
        var target = f.target(3.5, 4.5); var other = f.target(6.5, 4.5);
        request(poor, "skill_start"); request(flying, "skill_start");
        helper.assertTrue(GroundShock.active(poor) && GroundShock.active(flying), "CP and ground are release-time gates");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(poor, "skill_release"); request(flying, "skill_release");
                helper.assertFalse(GroundShock.active(poor) || GroundShock.active(flying), "both mature failures terminate");
                assertFree(helper, poorState, 79, 0, "failed CP release"); assertFree(helper, flyingState, 1800, 0, "airborne release");
                close(helper, target.getHealth(), 200, "CP failure cannot attack"); close(helper, other.getHealth(), 200, "airborne failure cannot attack");
                helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 0, 3));
                helper.assertBlockPresent(Blocks.STONE, new BlockPos(6, 0, 3)); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_airborne_preparation_can_land_before_accepted_release(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        player.setOnGround(false); f.groundLine(3, 3, 12, Blocks.STONE); var target = f.target(3.5, 4.5);
        request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed < 5) helper.assertTrue(GroundShock.active(player), "airborne preparation stays active");
            if (elapsed == 5) {
                player.setOnGround(true); request(player, "skill_release");
                close(helper, state.cp, 1720, "grounded release commits after airborne preparation");
                close(helper, target.getHealth(), 196, "landed caster produces normal wave"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_terrain_conversion_immunities_and_three_above_column(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        var stone = new BlockPos(3, 0, 3); var grass = new BlockPos(3, 0, 4);
        var farmland = new BlockPos(3, 0, 5); var liquid = new BlockPos(3, 0, 6); var unbreakable = new BlockPos(3, 0, 7);
        helper.setBlock(stone, Blocks.STONE); helper.setBlock(grass, Blocks.GRASS_BLOCK);
        helper.setBlock(farmland, Blocks.FARMLAND); helper.setBlock(liquid, Blocks.WATER); helper.setBlock(unbreakable, Blocks.BEDROCK);
        // Immune blocks are also tested in guaranteed (not random) above-column breaks.
        helper.setBlock(new BlockPos(3, 1, 8), Blocks.FARMLAND);
        helper.setBlock(new BlockPos(3, 1, 9), Blocks.WATER);
        helper.setBlock(new BlockPos(3, 1, 10), Blocks.BEDROCK);
        for (int y = 1; y <= 3; y++) helper.setBlock(new BlockPos(3, y, 3), Blocks.STONE);
        helper.setBlock(new BlockPos(3, 4, 3), Blocks.STONE);
        request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(player, "skill_release");
                assertBlockOneOf(helper, stone, Blocks.COBBLESTONE, Blocks.AIR, "stone converts before optional ground destruction");
                assertBlockOneOf(helper, grass, Blocks.DIRT, Blocks.AIR, "grass converts before optional ground destruction");
                helper.assertBlockPresent(Blocks.FARMLAND, farmland); helper.assertBlockPresent(Blocks.WATER, liquid);
                helper.assertBlockPresent(Blocks.BEDROCK, unbreakable);
                helper.assertBlockPresent(Blocks.FARMLAND, new BlockPos(3, 1, 8));
                helper.assertBlockPresent(Blocks.WATER, new BlockPos(3, 1, 9));
                helper.assertBlockPresent(Blocks.BEDROCK, new BlockPos(3, 1, 10));
                for (int y = 1; y <= 3; y++) helper.assertBlockPresent(Blocks.AIR, new BlockPos(3, y, 3));
                helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 4, 3));
                close(helper, state.exp(ID), .001, "terrain-only wave awards final EXP exactly once"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_cancelled_break_events_protect_conversion_and_above_destruction(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); ready(player, 0); var cells = new HashSet<BlockPos>();
        var stone = new BlockPos(3, 0, 3); var grass = new BlockPos(3, 0, 4);
        helper.setBlock(stone, Blocks.STONE); helper.setBlock(grass, Blocks.GRASS_BLOCK);
        cells.add(helper.absolutePos(stone)); cells.add(helper.absolutePos(grass));
        for (int y = 1; y <= 3; y++) {
            var pos = new BlockPos(3, y, 3); helper.setBlock(pos, Blocks.STONE); cells.add(helper.absolutePos(pos));
        }
        var hook = new BreakHook(player, cells); f.hook(hook); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(player, "skill_release");
                helper.assertBlockPresent(Blocks.STONE, stone); helper.assertBlockPresent(Blocks.GRASS_BLOCK, grass);
                for (int y = 1; y <= 3; y++) helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, y, 3));
                helper.assertTrue(hook.observed.containsAll(cells), "exact protected cells receive native BreakEvent checks");
                close(helper, AbilityStorage.get(player).cp, 1720, "protection does not refund accepted performance"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_zeroed_attack_still_launches_and_credits_unique_entity_experience(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        f.groundLine(3, 3, 12, Blocks.STONE); var target = f.target(3.5, 4.5);
        var hook = new AttackHook(player, target, true); f.hook(hook); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(player, "skill_release");
                helper.assertValueEqual(hook.calls, 1, "zero-damage target remains deduplicated");
                close(helper, target.getHealth(), 200, "mutable SkillAttackEvent amount0 rejects health damage");
                close(helper, state.exp(ID), .003, "source affected-target EXP survives rejected damage");
                assertLaunch(helper, target, .48, .72); close(helper, state.cp, 1720, "rejected damage still pays performance cost");
                helper.succeed();
            }
        });
    }

    @GameTest(template = WIDE_TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_entity_experience_reaches_exact_mastery_before_soft_volume_clear(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(10.5, 8.5); var state = ready(player, .998);
        fillMasteryVolume(helper); setBoundaryMarkers(helper); var target = f.target(10.5, 10.5);
        var hook = new AttackHook(player, target, false); f.hook(hook); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(player, "skill_release");
                close(helper, state.exp(ID), 1, ".002 entity EXP reaches exact live mastery cap before clear");
                close(helper, state.levelExperience, .003, "capped mastery still credits both EXP stages");
                helper.assertValueEqual(hook.calls, 1, "mastery target contributes one source entity EXP increment");
                for (int x = 5; x < 15; x++) for (int y = 0; y < 2; y++) for (int z = 3; z < 13; z++)
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, y, z));
                assertBoundaryMarkers(helper); close(helper, target.getHealth(), 194.004, "captured near-master damage");
                close(helper, state.cp, 1650.14F, "captured near-master original float ledger cost");
                helper.assertValueEqual(state.cooldowns.get(ID), 40, "near-master captured iteration/cooldown truncation"); helper.succeed();
            }
        });
    }

    @GameTest(template = WIDE_TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_final_performance_experience_does_not_retroactively_trigger_mastery_clear(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(10.5, 8.5); var state = ready(player, .9979);
        f.groundLine(10, 9, 33, Blocks.STONE); var target = f.target(10.5, 10.5);
        var soft = new BlockPos(5, 0, 3); helper.setBlock(soft, Blocks.DIRT); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(player, "skill_release");
                close(helper, state.exp(ID), 1, "final .001 EXP reaches cap after pre-clear check");
                helper.assertBlockPresent(Blocks.DIRT, soft);
                close(helper, state.levelExperience, .003, "target and final EXP are both recorded");
                helper.assertTrue(target.getHealth() < 200, "entity stage really ran before final EXP"); helper.succeed();
            }
        });
    }

    @GameTest(template = WIDE_TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_mastered_start_reaches_exact_twenty_five_iteration_boundary(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(10.5, 8.5); var state = ready(player, 1);
        f.groundLine(10, 9, 34, Blocks.STONE); var target = f.target(10.5, 34.5);
        helper.setBlock(new BlockPos(10, 1, 33), Blocks.STONE);
        helper.setBlock(new BlockPos(10, 1, 34), Blocks.STONE);
        request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(player, "skill_release");
                close(helper, state.cp, 1650, "mastered start captures CP150");
                close(helper, state.overload, 10, "mastered start captures strain10");
                close(helper, target.getHealth(), 194, "last source cell reaches target with mastered damage6");
                assertBlockOneOf(helper, new BlockPos(10, 0, 33), Blocks.COBBLESTONE, Blocks.AIR, "25th center processes its ground");
                // Source lateral offsets also truncate absolute coordinates. At negative X/Z,
                // the -rx/+0.448 side sample of center25 can legitimately reach this next ground
                // row. This is not a26th Plotter center; the above-row34 marker below proves that.
                assertBlockOneOf(helper, new BlockPos(10, 0, 34), Blocks.STONE, Blocks.COBBLESTONE,
                        "outside center ground can receive a source-truncated lateral sample");
                helper.assertBlockPresent(Blocks.AIR, new BlockPos(10, 1, 33));
                helper.assertBlockPresent(Blocks.STONE, new BlockPos(10, 1, 34));
                helper.assertValueEqual(state.cooldowns.get(ID), 40, "mastered captured cooldown40");
                assertLaunch(helper, target, .78, 1.17);
                close(helper, state.levelExperience, .003, "last-cell target credits entity and final EXP once"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_lifecycle_events_and_input_aborts_remove_free_contexts(GameTestHelper helper) {
        var f = new Fixture(helper); var players = new ArrayList<ServerPlayer>(); var states = new ArrayList<AbilityProgress>();
        var actions = List.of("skill_abort", "abort_active", "toggle", "logout", "dimension", "state_replaced");
        for (var action : actions) {
            var player = f.player(); players.add(player); states.add(ready(player, 0)); request(player, "skill_start");
            helper.assertTrue(GroundShock.active(player), action + " fixture hold starts");
        }
        f.worldTicks(elapsed -> {
            if (elapsed == 1) for (int i = 0; i < actions.size(); i++) {
                var player = players.get(i);
                switch (actions.get(i)) {
                    case "logout" -> logout(player);
                    case "dimension" -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
                    case "state_replaced" -> AbilityStorage.remove(player);
                    default -> request(player, actions.get(i));
                }
            }
            if (elapsed == 2) for (int i = 0; i < actions.size(); i++)
                helper.assertFalse(GroundShock.active(players.get(i)), actions.get(i) + " removes transient input context");
            if (elapsed == 5) {
                for (int i = 0; i < actions.size(); i++) {
                    request(players.get(i), "skill_release"); assertFree(helper, states.get(i), 1800, 0, actions.get(i) + " cannot replay");
                    assertFree(helper, AbilityStorage.get(players.get(i)), 1800, 0, actions.get(i) + " replacement state cannot replay");
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_death_and_clone_never_persist_or_revive_held_context(GameTestHelper helper) {
        var f = new Fixture(helper); var dead = f.player(); var deadState = ready(dead, 0);
        var original = f.player(); var oldState = ready(original, 0); var replacement = f.player();
        request(dead, "skill_start"); request(original, "skill_start");
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(dead, dead.damageSources().generic()));
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, false));
        helper.assertFalse(GroundShock.active(dead), "death subscriber immediately removes held context");
        helper.assertFalse(deadState.activated, "death deactivates ability state");
        helper.assertFalse(GroundShock.active(original) || GroundShock.active(replacement), "clone cannot transfer a transient hold");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(dead, "skill_release"); request(original, "skill_release"); request(replacement, "skill_release");
                assertFree(helper, deadState, 1800, 0, "death context cannot replay"); assertFree(helper, oldState, 1800, 0, "old clone cannot replay");
                var copied = AbilityStorage.get(replacement);
                helper.assertTrue(copied.learned(ID), "nondeath clone keeps learned Ground Shock progression");
                assertFree(helper, copied, 1800, 0, "cloned progression excludes held-input context"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_category_interference_overload_and_unlearning_cancel_on_native_tick(GameTestHelper helper) {
        var f = new Fixture(helper); var players = new ArrayList<ServerPlayer>(); var states = new ArrayList<AbilityProgress>();
        var gates = List.of("category", "interference", "overload", "unlearned", "deactivated", "cooldown");
        for (var gate : gates) {
            var player = f.player(); players.add(player); states.add(ready(player, 0)); request(player, "skill_start");
        }
        f.worldTicks(elapsed -> {
            if (elapsed == 1) for (int i = 0; i < gates.size(); i++) {
                var state = states.get(i);
                switch (gates.get(i)) {
                    case "category" -> state.selectCategory("electromaster");
                    case "interference" -> state.interfering = true;
                    case "overload" -> { state.overload = 1; state.overloadFine = false; }
                    case "unlearned" -> state.experience.remove(ID);
                    case "deactivated" -> state.activated = false;
                    case "cooldown" -> state.cooldowns.put(ID, 20);
                    default -> throw new AssertionError("Unexpected gate");
                }
            }
            if (elapsed == 2) for (int i = 0; i < gates.size(); i++)
                helper.assertFalse(GroundShock.active(players.get(i)), gates.get(i) + " cancels on one actual world tick");
            if (elapsed == 5) {
                for (int i = 0; i < gates.size(); i++) {
                    request(players.get(i), "skill_release"); var state = states.get(i);
                    close(helper, state.cp, 1800, gates.get(i) + " cancellation has no CP debit");
                    close(helper, state.extraCp, 0, gates.get(i) + " cancellation has no resource training");
                    close(helper, state.levelExperience, 0, gates.get(i) + " cancellation has no level EXP");
                    close(helper, state.exp(ID), 0, gates.get(i) + " cancellation has no skill EXP");
                }
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void ground_shock_start_gates_reject_unusable_progression_without_cost(GameTestHelper helper) {
        var f = new Fixture(helper);
        for (var gate : List.of("category", "level", "unlearned", "deactivated", "interference", "overload", "cooldown")) {
            var player = f.player(); var state = ready(player, 0);
            switch (gate) {
                case "category" -> state.category = "electromaster";
                case "level" -> state.level = 0;
                case "unlearned" -> state.experience.remove(ID);
                case "deactivated" -> state.activated = false;
                case "interference" -> state.interfering = true;
                case "overload" -> state.overloadFine = false;
                case "cooldown" -> state.cooldowns.put(ID, 80);
                default -> throw new AssertionError("Unexpected gate");
            }
            request(player, "skill_start"); helper.assertFalse(GroundShock.active(player), gate + " cannot start");
            close(helper, state.cp, 1800, gate + " is free"); close(helper, state.overload, 0, gate + " adds no strain");
            close(helper, state.levelExperience, 0, gate + " adds no EXP");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 225)
    public static void ground_shock_hold_survives_two_hundred_world_ticks_without_auto_fire(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        f.groundLine(3, 3, 12, Blocks.STONE); var target = f.target(3.5, 4.5); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            helper.assertTrue(GroundShock.active(player), "source hold stays active through world tick" + elapsed);
            assertFree(helper, state, 1800, 0, "long hold tick" + elapsed);
            close(helper, target.getHealth(), 200, "long hold does not auto-attack");
            if (elapsed == 200) {
                request(player, "skill_release"); close(helper, state.cp, 1720, "200-tick release is accepted without maximum duration");
                close(helper, target.getHealth(), 196, "long hold produces one normal wave");
                helper.assertFalse(GroundShock.active(player), "long release terminates context"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_creative_zero_cp_release_still_performs_trains_and_cools_down(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0); state.cp = 0;
        player.getAbilities().instabuild = true; f.groundLine(3, 3, 12, Blocks.STONE); var target = f.target(3.5, 4.5);
        request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(player, "skill_release"); close(helper, state.cp, 0, "creative bypasses zero CP failure without debiting");
                close(helper, state.overload, 0, "creative adds no strain"); close(helper, state.extraCp, .2, "creative keeps source CP training");
                close(helper, state.extraOverload, .087, "creative keeps source strain training");
                close(helper, state.exp(ID), .003, "creative keeps entity and performance EXP");
                helper.assertValueEqual(state.cooldowns.get(ID), 80, "creative keeps captured cooldown");
                close(helper, target.getHealth(), 196, "creative wave attacks real target"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void ground_shock_air_only_wave_pays_final_experience_without_querying_targets(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0); var target = f.target(3.5, 4.5);
        var hook = new AttackHook(player, target, false); f.hook(hook); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) {
                request(player, "skill_release"); close(helper, state.cp, 1720, "accepted air-only wave still pays CP");
                close(helper, state.overload, 15, "accepted air-only wave still pays strain"); close(helper, state.exp(ID), .001, "no ground means final EXP only");
                close(helper, target.getHealth(), 200, "living target above air is not an affected ground target");
                helper.assertValueEqual(hook.calls, 0, "air cells never query/attack living targets");
                helper.assertValueEqual(state.cooldowns.get(ID), 80, "air-only accepted wave keeps cooldown"); helper.succeed();
            }
        });
    }

    private static AbilityProgress ready(ServerPlayer player, double mastery) {
        var state = AbilityStorage.get(player); state.selectCategory("vecmanip"); state.setLevel(1); state.activated = true;
        state.experience.put(ID, mastery); state.cpDelay = 15; state.overloadDelay = 32; return state;
    }
    private static void request(ServerPlayer player, String action) { AcademyGameplay.request(player, new AcademyNetwork.Request(action, ID)); }
    private static void logout(ServerPlayer player) { NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player)); }
    private static void postTick(ServerPlayer player) { player.tickCount++; NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)); }
    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) <= EPSILON, label + ": expected " + expected + ", got " + actual);
    }
    private static void assertFree(GameTestHelper helper, AbilityProgress state, double cp, double mastery, String label) {
        close(helper, state.cp, cp, label + " leaves CP"); close(helper, state.overload, 0, label + " leaves strain");
        close(helper, state.extraCp, 0, label + " leaves CP training"); close(helper, state.extraOverload, 0, label + " leaves strain training");
        close(helper, state.exp(ID), mastery, label + " leaves skill EXP"); close(helper, state.levelExperience, 0, label + " leaves level EXP");
        helper.assertTrue(state.cooldowns.isEmpty(), label + " starts no cooldown");
    }
    private static void assertLaunch(GameTestHelper helper, Villager target, double low, double high) {
        double speed = target.getDeltaMovement().y;
        helper.assertTrue(speed >= low - EPSILON && speed <= high + EPSILON, "captured random vertical speed lies in source mastery interval");
        helper.assertTrue(target.hasImpulse && target.hurtMarked, "native motion tracking flags set for launch");
    }
    private static void assertBlockOneOf(GameTestHelper helper, BlockPos relative, Block first, Block second, String label) {
        var block = helper.getLevel().getBlockState(helper.absolutePos(relative));
        helper.assertTrue(block.is(first) || block.is(second), label + ": got " + block);
    }
    private static void fillMasteryVolume(GameTestHelper helper) {
        for (int x = 5; x < 15; x++) for (int y = 0; y < 2; y++) for (int z = 3; z < 13; z++)
            helper.setBlock(new BlockPos(x, y, z), Blocks.DIRT);
    }
    private static List<BlockPos> boundaryMarkers() {
        return List.of(new BlockPos(4, 0, 5), new BlockPos(15, 0, 5), new BlockPos(5, 0, 2),
                new BlockPos(5, 0, 13), new BlockPos(5, 2, 3));
    }
    private static void setBoundaryMarkers(GameTestHelper helper) { for (var pos : boundaryMarkers()) helper.setBlock(pos, Blocks.DIRT); }
    private static void assertBoundaryMarkers(GameTestHelper helper) { for (var pos : boundaryMarkers()) helper.assertBlockPresent(Blocks.DIRT, pos); }

    private static final class Fixture {
        final GameTestHelper helper; final long started; long previous; boolean closed;
        final List<ServerPlayer> players = new ArrayList<>(); final List<Villager> targets = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>();
        Fixture(GameTestHelper helper) {
            this.helper = helper; started = previous = helper.getLevel().getGameTime();
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { cleanup(); }
            });
        }
        FakePlayer player() { return player(3.5, 2.5); }
        FakePlayer player(double x, double z) {
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-GroundTest]"));
            var anchor = helper.absolutePos(new BlockPos((int) x, 1, (int) z));
            // Groundshock uses .toInt, not floor. At a negative world origin +.5 belongs to the
            // NEXT integer under truncation, so choose the signed half-cell around the intended
            // source anchor. This is fixture alignment, not a change to classic runtime geometry.
            double px = signedHalf(anchor.getX()), pz = signedHalf(anchor.getZ());
            player.moveTo(px, anchor.getY(), pz, 0, 0);
            player.setYHeadRot(0); player.setOnGround(true); player.setNoGravity(true); player.setDeltaMovement(Vec3.ZERO);
            player.getAbilities().instabuild = false; players.add(player);
            helper.assertValueEqual((int) player.getX(), anchor.getX(), "fixture aligns source-truncated absolute X");
            helper.assertValueEqual((int) player.getY(), anchor.getY(), "fixture aligns source-truncated absolute Y");
            helper.assertValueEqual((int) player.getZ(), anchor.getZ(), "fixture aligns source-truncated absolute Z");
            Vec3 direction = ClassicRaytrace.direction(player).normalize();
            close(helper, direction.x, 0, "fixture head yaw0 points along world+Z");
            close(helper, direction.y, 0, "fixture pitch0 keeps ground offsets horizontal");
            close(helper, direction.z, 1, "fixture head yaw0 has positive Z direction");
            int[] first = new Plotter((int) player.getX(), (int) player.getY() - 1, (int) player.getZ(),
                    direction.x, 0, direction.z).next();
            var expected = helper.absolutePos(new BlockPos((int) x, 0, (int) z + 1));
            helper.assertTrue(new BlockPos(first[0], first[1], first[2]).equals(expected),
                    "fixture first classic Plotter step matches arranged ground cell, including actual world origin/rotation");
            return player;
        }
        private static double signedHalf(int anchor) { return anchor + (anchor < 0 ? -.5 : .5); }
        Villager target(double x, double z) {
            var target = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(x, 1, z));
            target.setNoAi(true); target.setNoGravity(true); target.setDeltaMovement(Vec3.ZERO);
            target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200); target.setHealth(200); targets.add(target); return target;
        }
        void groundLine(int x, int firstZ, int lastZ, Block block) {
            for (int z = firstZ; z <= lastZ; z++) helper.setBlock(new BlockPos(x, 0, z), block);
        }
        void hook(Object hook) { NeoForge.EVENT_BUS.register(hook); hooks.add(hook); }
        void worldTicks(LongConsumer assertions) {
            helper.onEachTick(() -> {
                long now = helper.getLevel().getGameTime(); if (closed || helper.testInfo.isDone() || now <= previous) return;
                helper.assertValueEqual(now - previous, 1L, "fixture observes consecutive actual ServerLevel ticks"); previous = now;
                for (var player : players) postTick(player); assertions.accept(now - started);
            });
        }
        void cleanup() {
            if (closed) return; closed = true; RuntimeException failure = null;
            for (var hook : hooks) failure = attempt(() -> NeoForge.EVENT_BUS.unregister(hook), failure);
            for (var player : players) failure = attempt(() -> { GroundShock.abort(player); logout(player); }, failure);
            for (var target : targets) failure = attempt(target::discard, failure);
            if (failure != null) throw failure;
        }
        private static RuntimeException attempt(Runnable action, RuntimeException failure) {
            try { action.run(); } catch (RuntimeException next) { if (failure == null) failure = next; else failure.addSuppressed(next); }
            return failure;
        }
    }
    public static final class AttackHook {
        final ServerPlayer owner; final Villager target; final boolean zero; int calls; String skill;
        AttackHook(ServerPlayer owner, Villager target, boolean zero) { this.owner = owner; this.target = target; this.zero = zero; }
        @SubscribeEvent public void attack(SkillAttackEvent event) {
            if (event.player == owner && event.target == target) { calls++; skill = event.skill; if (zero) event.amount = 0; }
        }
    }
    public static final class BreakHook {
        final ServerPlayer owner; final Set<BlockPos> cells; final Set<BlockPos> observed = new HashSet<>();
        BreakHook(ServerPlayer owner, Set<BlockPos> cells) { this.owner = owner; this.cells = Set.copyOf(cells); }
        @SubscribeEvent public void breaking(BlockEvent.BreakEvent event) {
            if (event.getPlayer() == owner && cells.contains(event.getPos())) { observed.add(event.getPos().immutable()); event.setCanceled(true); }
        }
    }
}
