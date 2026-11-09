package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.api.SkillReflectEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.skill.RadiationMarks;
import cn.academy.port.skill.CoinTosses;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.PacketSendListener;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * Additional server-world tests. Delayed skills are advanced by the actual
 * GameTest ServerLevel clock, never by advancing that clock or looping fake ticks.
 * The runtime_empty template is installed by AcademyRuntimeTests.
 * Native execution verifies server-world behavior; real-client transport and
 * rendering remain separate verification stages.
 */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
public final class AcademyFirstSkillRuntimeTests {
    private static final String TEMPLATE = "runtime_empty";
    private static final String BATCH = "academy_first_skills";
    private static final String ESCROW_KEY = "academy:coin_escrow";
    private static final double EPSILON = .00001;

    private AcademyFirstSkillRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 45)
    public static void electron_commits_on_activation_and_uses_firing_head_aim(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "meltdowner", "electron_bomb", 1, 0);
        var initialTarget = f.target(3.5, 6.5);
        var finalTarget = f.target(6.5, 2.5);
        request(player, "cast", "electron_bomb");
        close(helper, state.cp, 1765, "activation pays 35 CP immediately");
        close(helper, state.overload, 16, "activation pays 16 overload immediately");
        close(helper, state.exp("electron_bomb"), .005, "activation awards experience immediately");
        helper.assertValueEqual(state.cooldowns.get("electron_bomb"), 20, "activation starts cooldown");
        close(helper, initialTarget.getHealth(), 200, "orb does not attack on activation");
        request(player, "cast", "electron_bomb");
        close(helper, state.cp, 1765, "cooldown rejects duplicate cast");
        f.worldTicks(elapsed -> {
            if (elapsed == 10) player.setYHeadRot(-90);
            if (elapsed < 18) {
                close(helper, initialTarget.getHealth(), 200, "initial aim has not fired early");
                close(helper, finalTarget.getHealth(), 200, "new aim has not fired early");
            } else if (elapsed == 18) {
                close(helper, initialTarget.getHealth(), 200, "firing uses current head yaw");
                close(helper, finalTarget.getHealth(), 194, "captured launch damage applies after 18 world ticks");
            } else if (elapsed == 23) {
                close(helper, finalTarget.getHealth(), 194, "accepted orb fires exactly once");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void electron_solid_world_blocks_occlude_delayed_shot(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "meltdowner", "electron_bomb", 1, 0);
        var target = f.target(3.5, 6.5);
        for (int x = 1; x <= 5; x++) for (int y = 1; y <= 4; y++)
            helper.setBlock(new BlockPos(x, y, 4), Blocks.STONE);
        request(player, "cast", "electron_bomb");
        f.worldTicks(elapsed -> {
            if (elapsed == 20) {
                close(helper, target.getHealth(), 200, "solid wall blocks the actual orb ray");
                close(helper, state.exp("electron_bomb"), .005, "occluded activation retains its experience");
                helper.assertBlockPresent(Blocks.STONE, new BlockPos(3, 2, 4));
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void electron_trained_launch_fires_after_three_world_ticks(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "meltdowner", "electron_bomb", 1, .8);
        var target = f.target(3.5, 6.5);
        request(player, "cast", "electron_bomb");
        close(helper, state.cp, 1729, "trained activation pays its pre-increment cost");
        close(helper, state.overload, 13.6, "trained overload cost");
        helper.assertValueEqual(state.cooldowns.get("electron_bomb"), 12, "trained activation cooldown");
        f.worldTicks(elapsed -> {
            if (elapsed < 3) close(helper, target.getHealth(), 200, "trained orb does not fire early");
            if (elapsed == 3) {
                close(helper, target.getHealth(), 189.2, "trained orb fires at three actual world ticks");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void electron_accepted_cast_survives_deactivation_but_logout_cancels(GameTestHelper helper) {
        var f = new Fixture(helper);
        var active = f.fake();
        var loggedOut = f.fake(6.5, 2.5);
        ready(active, "meltdowner", "electron_bomb", 1, 0);
        ready(loggedOut, "meltdowner", "electron_bomb", 1, 0);
        var target = f.target(3.5, 6.5);
        var canceledTarget = f.target(6.5, 6.5);
        request(active, "cast", "electron_bomb");
        request(loggedOut, "cast", "electron_bomb");
        request(active, "toggle", "");
        request(active, "skill_abort", "electron_bomb"); // Ordinary input abort is not a lifecycle abort.
        logout(loggedOut);
        f.worldTicks(elapsed -> {
            if (elapsed == 20) {
                close(helper, target.getHealth(), 194, "accepted shot survives ordinary deactivation/key-up");
                close(helper, canceledTarget.getHealth(), 200, "logout event removes delayed shot");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void teleport_held_context_releases_current_needle_through_wall(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "teleporter", "threatening_teleport", 1, 0);
        var target = f.target(3.5, 6.5);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE, 1));
        helper.setBlock(new BlockPos(3, 2, 4), Blocks.STONE);
        request(player, "skill_start", "threatening_teleport");
        f.worldTicks(elapsed -> {
            if (elapsed < 7) {
                close(helper, target.getHealth(), 200, "held context does not auto-fire");
                close(helper, state.overload, 0, "hold costs nothing before release");
            } else if (elapsed == 7) {
                player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.NEEDLE.get(), 2));
                request(player, "skill_release", "threatening_teleport");
                close(helper, target.getHealth(), 195.5, "current needle deals 1.5x armor-bypassing damage through wall");
                close(helper, state.cp, 1765, "release pays CP");
                close(helper, state.overload, 18, "release pays overload");
                close(helper, state.exp("threatening_teleport"), .003, "entity trace awards hit experience");
                helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "release consumes current held needle");
                helper.assertValueEqual(state.cooldowns.get("threatening_teleport"), 30, "release cooldown");
                request(player, "skill_release", "threatening_teleport");
                close(helper, state.cp, 1765, "release replay cannot double-spend");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void teleport_miss_returns_last_item_as_world_drop(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "teleporter", "threatening_teleport", 1, 0);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE, 1));
        request(player, "skill_start", "threatening_teleport");
        request(player, "skill_release", "threatening_teleport");
        helper.assertTrue(player.getMainHandItem().isEmpty(), "miss consumes the last held item");
        var drops = helper.getLevel().getEntities(EntityType.ITEM, helper.getBounds(),
                item -> item.getItem().is(Items.STONE));
        helper.assertValueEqual(drops.size(), 1, "last item survives as exactly one real world drop");
        helper.assertValueEqual(drops.getFirst().getItem().getCount(), 1, "world drop contains one item");
        close(helper, state.cp, 1765, "miss still pays CP");
        close(helper, state.overload, 18, "miss still pays overload");
        close(helper, state.exp("threatening_teleport"), .0006, "miss experience");
        helper.assertValueEqual(state.cooldowns.get("threatening_teleport"), 30, "miss still has cooldown");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void teleport_empty_item_abort_and_failed_cp_have_no_commit(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "teleporter", "threatening_teleport", 1, 0);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE, 2));
        request(player, "skill_start", "threatening_teleport");
        player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
        postTick(player);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE, 2));
        request(player, "skill_release", "threatening_teleport");
        close(helper, state.cp, 1800, "empty-hand tick cancels rather than pauses context");
        request(player, "skill_start", "threatening_teleport");
        request(player, "skill_abort", "threatening_teleport");
        request(player, "skill_release", "threatening_teleport");
        close(helper, state.overload, 0, "explicit input abort is free");
        state.cp = 34;
        request(player, "skill_start", "threatening_teleport");
        request(player, "skill_release", "threatening_teleport");
        close(helper, state.cp, 34, "insufficient CP does not debit resources");
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "failed CP check retains items");
        close(helper, state.exp("threatening_teleport"), 0, "canceled contexts award no experience");
        helper.assertTrue(state.cooldowns.isEmpty(), "canceled contexts have no cooldown");
        helper.assertTrue(helper.getLevel().getEntities(EntityType.ITEM, helper.getBounds(), item -> true).isEmpty(),
                "canceled contexts spawn no item drops");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void directed_shock_uses_world_hold_time_and_captured_damage(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "vecmanip", "dir_shock", 1, .25);
        var target = f.target(3.5, 4.5);
        request(player, "skill_start", "dir_shock");
        f.worldTicks(elapsed -> {
            if (elapsed == 5) request(player, "skill_start", "dir_shock");
            if (elapsed == 7) {
                state.experience.put("dir_shock", 1.0);
                request(player, "skill_release", "dir_shock");
                close(helper, target.getHealth(), 191, "damage captured at start survives mastery changes");
                close(helper, state.cp, 1700, "release uses current mastery CP cost");
                close(helper, state.overload, 12, "release uses current mastery overload");
                close(helper, state.levelExperience, .0035, "hit experience applies even at mastery cap");
                helper.assertValueEqual(state.cooldowns.get("dir_shock"), 20, "hit cooldown uses current mastery");
                helper.assertTrue(target.getDeltaMovement().y > 0 && target.getDeltaMovement().z > 0,
                        "strong directed impulse and extra knockback reach the real target");
                helper.assertTrue(target.hasImpulse && target.hurtMarked, "velocity tracking flags are set");
                request(player, "skill_release", "dir_shock");
                close(helper, state.cp, 1700, "context is removed before release replay");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 65)
    public static void directed_shock_rejects_exact_six_and_fifty_world_ticks(GameTestHelper helper) {
        var f = new Fixture(helper);
        var early = f.fake();
        var late = f.fake(6.5, 2.5);
        var earlyState = ready(early, "vecmanip", "dir_shock", 1, 0);
        var lateState = ready(late, "vecmanip", "dir_shock", 1, 0);
        request(early, "skill_start", "dir_shock");
        request(late, "skill_start", "dir_shock");
        f.worldTicks(elapsed -> {
            if (elapsed == 6) {
                request(early, "skill_release", "dir_shock");
                close(helper, earlyState.cp, 1800, "strict six-tick boundary rejects");
                close(helper, earlyState.exp("dir_shock"), 0, "early release has no XP");
            }
            if (elapsed == 50) {
                request(late, "skill_release", "dir_shock");
                close(helper, lateState.overload, 0, "strict fifty-tick boundary rejects");
                close(helper, lateState.exp("dir_shock"), 0, "late release has no XP");
                helper.assertTrue(earlyState.cooldowns.isEmpty() && lateState.cooldowns.isEmpty(),
                        "both rejected boundaries have no cooldown");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void directed_shock_world_wall_miss_is_paid_without_cooldown(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "vecmanip", "dir_shock", 1, 0);
        var target = f.target(3.5, 4.5);
        helper.setBlock(new BlockPos(3, 2, 3), Blocks.STONE);
        request(player, "skill_start", "dir_shock");
        f.worldTicks(elapsed -> {
            if (elapsed == 7) {
                request(player, "skill_release", "dir_shock");
                close(helper, target.getHealth(), 200, "wall occludes DirectedShock target");
                close(helper, state.cp, 1750, "occluded miss pays CP");
                close(helper, state.overload, 18, "occluded miss pays overload");
                close(helper, state.exp("dir_shock"), .001, "miss experience is awarded");
                helper.assertTrue(state.cooldowns.isEmpty(), "paid miss has no cooldown");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void held_skills_are_canceled_by_deactivation_and_logout_events(GameTestHelper helper) {
        var f = new Fixture(helper);
        var shock = f.fake();
        var teleport = f.fake(6.5, 2.5);
        var shockState = ready(shock, "vecmanip", "dir_shock", 1, 0);
        var teleportState = ready(teleport, "teleporter", "threatening_teleport", 1, 0);
        teleport.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE, 2));
        request(shock, "skill_start", "dir_shock");
        request(teleport, "skill_start", "threatening_teleport");
        f.worldTicks(elapsed -> {
            if (elapsed == 3) {
                request(shock, "toggle", "");
                request(shock, "toggle", "");
                logout(teleport);
            }
            if (elapsed == 7) {
                request(shock, "skill_release", "dir_shock");
                request(teleport, "skill_release", "threatening_teleport");
                close(helper, shockState.overload, 0, "deactivation removes held shock");
                close(helper, shockState.levelExperience, 0, "canceled shock has no XP");
                close(helper, teleportState.levelExperience, 0, "logout removes held teleport");
                helper.assertValueEqual(teleport.getMainHandItem().getCount(), 2, "logout preserves teleport item");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 50)
    public static void coin_learned_but_unbound_railgun_never_offers_qte_and_refunds(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.packetPlayer();
        var state = ready(player, "electromaster", "railgun", 4, 0);
        helper.assertFalse(state.presets.currentContains("railgun"), "normal empty mapping does not bind learned Railgun");
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.COIN.get(), 3));
        AcademyCraft.COIN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        long token = f.capture.latestCoinToken(player);
        helper.assertFalse(CoinTosses.hasPendingAttempt(player), "unbound learned Railgun never creates QTE delegate");
        helper.assertTrue(player.getPersistentData().contains(ESCROW_KEY), "physical unbound toss still escrows one coin");
        request(player, "coin_attempt", Long.toString(token));
        close(helper, state.overload, 0, "unbound token cannot invoke Railgun");
        f.worldTicks(elapsed -> {
            if (elapsed == 35) {
                helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "physical fall refunds unbound coin exactly once");
                helper.assertFalse(player.getPersistentData().contains(ESCROW_KEY), "unbound toss cleanup removes escrow");
                close(helper, state.exp("railgun"), 0, "unbound toss never earns Railgun mastery");
                helper.assertTrue(state.cooldowns.isEmpty(), "unbound toss starts no cooldown"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 45)
    public static void coin_late_trusted_network_token_fires_railgun_without_iron(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.packetPlayer();
        var state = ready(player, "electromaster", "railgun", 4, 0);
        state.presets.edit(0, 0, "railgun", id -> cn.academy.port.preset.PresetSkills.selectable(state, id));
        var target = f.target(3.5, 6.5);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.COIN.get(), 3));
        helper.assertTrue(AcademyCraft.COIN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND)
                .getResult().consumesAction(), "real coin item-use accepts server toss");
        long token = f.capture.latestCoinToken(player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "toss places exactly one coin in escrow");
        helper.assertTrue(player.getPersistentData().contains(ESCROW_KEY), "escrow is persisted before QTE");
        request(player, "coin_attempt", Long.toString(token - 1));
        request(player, "coin_attempt", "not-a-token");
        helper.assertTrue(CoinTosses.hasPendingAttempt(player), "stale/malformed token cannot judge current toss");
        f.worldTicks(elapsed -> {
            if (elapsed < 24) close(helper, target.getHealth(), 200, "coin does not fire automatically");
            if (elapsed == 24) {
                double beforeCp = state.cp;
                request(player, "coin_attempt", Long.toString(token));
                close(helper, state.cp, beforeCp - 200, "trusted late QTE pays normal Railgun CP");
                close(helper, state.overload, 180, "coin path pays normal overload");
                close(helper, state.exp("railgun"), .005, "coin beam earns experience");
                helper.assertValueEqual(state.cooldowns.get("railgun"), 300, "coin beam starts cooldown");
                helper.assertTrue(target.getHealth() < 200, "coin request reaches actual world beam damage");
                helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "success consumes escrow, no iron item");
                helper.assertFalse(player.getPersistentData().contains(ESCROW_KEY), "success removes saved escrow");
                request(player, "coin_attempt", Long.toString(token));
                close(helper, state.cp, beforeCp - 200, "network replay cannot fire twice");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 50)
    public static void coin_early_network_judgement_refunds_after_real_fall(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.packetPlayer();
        var state = ready(player, "electromaster", "railgun", 4, 0);
        state.presets.edit(0, 0, "railgun", id -> cn.academy.port.preset.PresetSkills.selectable(state, id));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.COIN.get(), 3));
        AcademyCraft.COIN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        long token = f.capture.latestCoinToken(player);
        request(player, "coin_attempt", Long.toString(token));
        helper.assertFalse(CoinTosses.hasPendingAttempt(player), "early judgement is spent once");
        helper.assertTrue(player.getPersistentData().contains(ESCROW_KEY), "early failure keeps falling coin escrow");
        // Repeated subscriber calls in this SAME game tick cannot advance physical coin time.
        postTick(player);
        postTick(player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "same-tick duplicate events cannot finish coin");
        f.worldTicks(elapsed -> {
            if (elapsed == 24) request(player, "coin_attempt", Long.toString(token));
            if (elapsed == 35) {
                helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "completed real-world fall refunds one coin");
                helper.assertFalse(player.getPersistentData().contains(ESCROW_KEY), "refund removes saved escrow");
                close(helper, state.exp("railgun"), 0, "spent early judgement cannot become late success");
                close(helper, state.overload, 0, "failed QTE never invokes beam costs");
                helper.assertTrue(state.cooldowns.isEmpty(), "failed QTE has no ability cooldown");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 40)
    public static void coin_cp_failure_still_consumes_escrow_before_resource_check(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.packetPlayer();
        var state = ready(player, "electromaster", "railgun", 4, 0);
        state.presets.edit(0, 0, "railgun", id -> cn.academy.port.preset.PresetSkills.selectable(state, id));
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.COIN.get(), 3));
        AcademyCraft.COIN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        long token = f.capture.latestCoinToken(player);
        f.worldTicks(elapsed -> {
            if (elapsed == 24) {
                state.cp = 0;
                request(player, "coin_attempt", Long.toString(token));
                helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "accepted QTE consumes coin before CP failure");
                helper.assertFalse(player.getPersistentData().contains(ESCROW_KEY), "CP failure does not refund consumed escrow");
                close(helper, state.cp, 0, "failed ability cost check has no CP debit");
                close(helper, state.overload, 0, "failed ability cost check has no overload");
                close(helper, state.exp("railgun"), 0, "failed beam has no experience");
                helper.assertTrue(state.cooldowns.isEmpty(), "failed beam has no cooldown");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void coin_logout_refunds_and_saved_escrow_recovers_exactly_once(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        ready(player, "electromaster", "railgun", 4, 0);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.COIN.get(), 3));
        AcademyCraft.COIN.get().use(helper.getLevel(), player, InteractionHand.MAIN_HAND);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 2, "physical toss holds one coin");
        logout(player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 3, "logout subscriber refunds outstanding coin");
        helper.assertFalse(player.getPersistentData().contains(ESCROW_KEY), "logout removes saved escrow");
        player.getPersistentData().put(ESCROW_KEY, new ItemStack(AcademyCraft.COIN.get(), 9).save(player.registryAccess()));
        postTick(player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 4, "orphaned restart escrow restores only one legitimate coin");
        postTick(player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 4, "recovery is idempotent");
        player.getPersistentData().put(ESCROW_KEY, new ItemStack(Items.DIAMOND, 9).save(player.registryAccess()));
        postTick(player);
        helper.assertValueEqual(player.getMainHandItem().getCount(), 4, "foreign escrow cannot inject an item");
        helper.assertFalse(player.getPersistentData().contains(ESCROW_KEY), "invalid escrow is removed");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 35)
    public static void railgun_reflection_event_redirects_and_earns_reflected_experience(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "electromaster", "railgun", 4, 0);
        state.presets.edit(0, 0, "railgun", id -> cn.academy.port.preset.PresetSkills.selectable(state, id));
        var reflector = f.target(3.5, 6.5);
        var redirectedTarget = f.target(6.5, 6.5);
        var beyondReflector = f.target(3.5, 10.5);
        reflector.setYRot(-90);
        reflector.setYBodyRot(-90);
        reflector.setYHeadRot(-90);
        var hook = new ReflectionHook(player, reflector);
        f.hook(hook);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.IRON_INGOT, 2));
        request(player, "charge", "railgun");
        f.worldTicks(elapsed -> {
            if (elapsed == 20) {
                helper.assertValueEqual(hook.calls, 1, "registered NeoForge reflection subscriber sees actual beam target");
                close(helper, reflector.getHealth(), 200, "canceled reflection event protects reflector");
                close(helper, redirectedTarget.getHealth(), 186, "reflection traces reflector head aim and deals 14 damage");
                close(helper, beyondReflector.getHealth(), 200, "incoming beam stops at reflector");
                close(helper, state.exp("railgun"), .01, "successful reflected hit awards .01 experience");
                helper.assertValueEqual(player.getMainHandItem().getCount(), 1, "reflection retains normal ammunition cost");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void mutable_skill_attack_hook_changes_actual_world_damage(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        ready(player, "electromaster", "arc_gen", 1, 0);
        var target = f.target(3.5, 6.5);
        var hook = new AttackHook(player, target);
        f.hook(hook);
        request(player, "cast", "arc_gen");
        helper.assertValueEqual(hook.calls, 1, "registered mutable skill event fires once");
        helper.assertValueEqual(hook.skill, "electromaster.arc_gen", "event reports qualified skill identifier");
        close(helper, target.getHealth(), 198, "mutated skill event amount reaches native LivingEntity.hurt");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void generic_passive_tier_gates_and_recovery_work_in_all_categories(GameTestHelper helper) {
        var f = new Fixture(helper);
        for (String category : List.of("electromaster", "meltdowner", "teleporter", "vecmanip")) {
            var player = f.fake();
            var state = AbilityStorage.get(player);
            state.selectCategory(category);
            state.setLevel(5);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.DEVELOPER.get()));
            for (String passive : List.of("brain_course", "brain_course_advanced", "mind_course")) {
                var skill = SkillCatalog.find(category, passive).orElseThrow();
                var prerequisite = SkillCatalog.ALL.stream().filter(other -> other.category().equals(category)
                        && other.controllable() && other.level() == skill.level()).findFirst().orElseThrow();
                state.experience.put(prerequisite.id(), 0.0);
                helper.assertTrue(DevelopmentActions.canLearn(state, DeveloperType.ADVANCED, skill),
                        category + " passive prerequisite fixture is otherwise eligible");
                request(player, "learn", passive);
                helper.assertFalse(state.learned(passive), category + " portable cannot instantly grant higher-tier " + passive);
                helper.assertFalse(DevelopmentController.process(player).isDeveloping(), category + " portable tier gate rejects " + passive);
                // Learned-passive fixture setup. Normal/advanced machines are not ported;
                // no claim is made that an unavailable device actually learned this skill.
                state.learn(passive);
            }
            close(helper, state.maxCp(), 10500, category + " learned generic passives raise max CP");
            close(helper, state.maxOverload(), 600, category + " learned advanced passive raises overload capacity");
            state.cp = 0;
            state.cpDelay = 0;
            postTick(player);
            close(helper, state.cp, ClassicRules.cpRecovery(0, 10500) * 1.2,
                    category + " registered player tick applies mind-course recovery multiplier");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 85)
    public static void electron_radiation_mark_amplifies_native_damage_then_expires(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "meltdowner", "electron_bomb", 1, .8);
        state.learn("rad_intensify"); // Learned-passive fixture, not a portable-tier bypass request.
        var target = f.target(3.5, 6.5);
        request(player, "cast", "electron_bomb");
        final double[] markRate = {0};
        f.worldTicks(elapsed -> {
            var mark = target.getPersistentData();
            if (elapsed == 3) {
                close(helper, target.getHealth(), 189.2, "first ElectronBomb damages before applying radiation mark");
                helper.assertValueEqual(mark.getInt("academy:md_mark_ticks"), 60, "native impact stamps sixty-tick mark");
                markRate[0] = RadiationMarks.rate(state.exp("rad_intensify"));
                close(helper, mark.getDouble("academy:md_mark_rate"), markRate[0], "mark captures CP-derived radiation mastery");
            }
            if (elapsed >= 4 && elapsed <= 63)
                helper.assertValueEqual(mark.getInt("academy:md_mark_ticks"), (int)Math.max(0, 63 - elapsed),
                        "actual living-entity pre-tick events decrement mark once per world tick");
            if (elapsed == 4) {
                target.invulnerableTime = 0;
                double before = target.getHealth();
                helper.assertTrue(target.hurt(target.damageSources().generic(), 5), "marked entity accepts native incoming damage");
                close(helper, target.getHealth(), before - 5 * markRate[0], "native LivingIncomingDamageEvent amplifies damage");
            }
            if (elapsed == 65) {
                helper.assertValueEqual(mark.getInt("academy:md_mark_ticks"), 0, "radiation mark expires in the actual world");
                target.invulnerableTime = 0;
                double before = target.getHealth();
                helper.assertTrue(target.hurt(target.damageSources().generic(), 5), "expired entity accepts native incoming damage");
                close(helper, target.getHealth(), before - 5, "expired mark no longer amplifies damage");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void completed_sleep_event_recovers_resources_with_flags_preserved(GameTestHelper helper) {
        var f = new Fixture(helper);
        var player = f.fake();
        var state = ready(player, "vecmanip", "dir_shock", 1, .4);
        state.cp = 10;
        state.overload = 100;
        state.cpDelay = 8;
        state.overloadDelay = 12;
        state.cooldowns.put("dir_shock", 20);
        NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, true, false));
        close(helper, state.cp, 10, "immediate wake does not award full sleep recovery");
        NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, true));
        close(helper, state.overload, 100, "update-level wake does not award full sleep recovery");
        NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, false));
        close(helper, state.cp, 1800, "completed sleep recovers CP");
        close(helper, state.overload, 0, "completed sleep clears overload");
        helper.assertFalse(state.overloadFine, "recovery waits for native post-tick overload unlock");
        helper.assertTrue(state.activated, "sleep preserves activation");
        helper.assertValueEqual(state.cpDelay, 8, "sleep preserves CP recovery delay");
        helper.assertValueEqual(state.cooldowns.get("dir_shock"), 20, "sleep preserves cooldown");
        close(helper, player.getPersistentData().getCompound("academy:classic_progress").getDouble("cp"),
                1800, "wake subscriber saves recovered progress");
        helper.succeed();
    }

    private static AbilityProgress ready(ServerPlayer player, String category, String skill, int level, double exp) {
        var state = AbilityStorage.get(player);
        state.selectCategory(category);
        state.setLevel(level);
        state.activated = true;
        state.experience.put(skill, exp);
        return state;
    }

    private static Villager target(GameTestHelper helper, double x, double z) {
        var target = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(x, 1, z));
        target.setNoGravity(true); // Real world ticks must not move the fixture out of its tested ray.
        target.setNoAi(true);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(200);
        target.setHealth(200);
        return target;
    }

    private static void position(GameTestHelper helper, ServerPlayer player, double x, double z) {
        var pos = helper.absoluteVec(new Vec3(x, 1, z));
        player.moveTo(pos.x, pos.y, pos.z, 0, 0);
        player.setYHeadRot(0);
        player.setNoGravity(true);
        player.getAbilities().instabuild = false;
        player.setDeltaMovement(Vec3.ZERO);
    }

    private static void request(ServerPlayer player, String action, String value) {
        AcademyGameplay.request(player, new AcademyNetwork.Request(action, value));
    }

    private static void postTick(ServerPlayer player) {
        player.tickCount++;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    }

    private static void logout(ServerPlayer player) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
    }

    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) <= EPSILON,
                label + ": expected " + expected + ", got " + actual);
    }

    /** Cleanup is attached to native pass/failure/timeout, including asynchronous assertion failures. */
    private static final class Fixture {
        final GameTestHelper helper;
        final long startedAt;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<ServerPlayer> loggedIn = new ArrayList<>();
        final List<Villager> targets = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>();
        CaptureListener capture;
        boolean closed;
        long lastTick;

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            startedAt = lastTick = helper.getLevel().getGameTime();
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { close(); }
            });
        }

        FakePlayer fake() { return fake(3.5, 2.5); }

        Villager target(double x, double z) {
            var target = AcademyFirstSkillRuntimeTests.target(helper, x, z);
            targets.add(target);
            return target;
        }

        FakePlayer fake(double x, double z) {
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-FirstTest]"));
            position(helper, player, x, z);
            players.add(player);
            return player;
        }

        /**
         * Mirrors the packaged native mock-player login with an EmbeddedChannel,
         * declaring this fixture's receiving channel before the login subscriber
         * sends its initial state. No client or network negotiation is simulated.
         * It lets PacketDistributor's real nearby-player broadcast reach a recipient,
         * so the request consumes the exact server-emitted token rather than guessing
         * a private counter or exposing/modifying gameplay internals.
         */
        ServerPlayer packetPlayer() {
            var level = helper.getLevel();
            var server = level.getServer();
            var cookie = CommonListenerCookie.createInitial(
                    new GameProfile(UUID.randomUUID(), "[AC-PacketTest]"), false);
            // Preserve the native helper's mock-player identity behavior.
            var player = new ServerPlayer(server, level, cookie.gameProfile(), cookie.clientInformation()) {
                @Override public boolean isSpectator() { return false; }
                @Override public boolean isCreative() { return true; }
            };
            players.add(player);
            var connection = new Connection(PacketFlow.SERVERBOUND);
            var channel = new EmbeddedChannel(connection);
            channels.add(channel);
            NetworkRegistry.onMinecraftRegister(connection, Set.of(AcademyNetwork.ClientData.TYPE.id()));
            // Admission can throw after PlayerList has already added the player.
            // Track both owned objects first so setup failures cannot leak them.
            loggedIn.add(player);
            try {
                server.getPlayerList().placeNewPlayer(connection, player, cookie);
                position(helper, player, 3.5, 2.5);
                capture = new CaptureListener(player, connection);
                return player;
            } catch (RuntimeException failure) {
                try { close(); }
                catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
                throw failure;
            }
        }

        void hook(Object hook) {
            NeoForge.EVENT_BUS.register(hook);
            hooks.add(hook);
        }

        void worldTicks(LongConsumer assertion) {
            helper.onEachTick(() -> {
                long now = helper.getLevel().getGameTime();
                if (closed || helper.testInfo.isDone() || now <= lastTick) return;
                // Native GameTest scheduling normally advances exactly one world tick.
                helper.assertValueEqual(now - lastTick, 1L, "fixture observes consecutive real world ticks");
                lastTick = now;
                for (var player : players) postTick(player);
                assertion.accept(now - startedAt);
            });
        }

        void close() {
            if (closed) return;
            closed = true;
            RuntimeException failure = null;
            for (Object hook : hooks)
                failure = cleanup(() -> NeoForge.EVENT_BUS.unregister(hook), failure);
            for (var player : players) {
                failure = cleanup(() -> {
                    var playerList = helper.getLevel().getServer().getPlayerList();
                    if (loggedIn.contains(player) && playerList.getPlayer(player.getUUID()) == player)
                        playerList.remove(player);
                    else {
                        logout(player);
                        if (loggedIn.contains(player)) player.discard();
                    }
                }, failure);
            }
            // Damage can knock a no-gravity target outside the template; clean by
            // identity rather than relying only on native structure-bound cleanup.
            for (var target : targets) failure = cleanup(target::discard, failure);
            for (var channel : channels) failure = cleanup(() -> channel.finishAndReleaseAll(), failure);
            if (failure != null) throw failure;
        }

        /** One cleanup failure must not prevent releasing the remaining owned fixtures. */
        private static RuntimeException cleanup(Runnable action, RuntimeException failure) {
            try { action.run(); }
            catch (RuntimeException next) {
                if (failure == null) failure = next;
                else failure.addSuppressed(next);
            }
            return failure;
        }
    }

    /** Captures payload objects only: no client, socket, packet codec, or renderer is exercised. */
    private static final class CaptureListener extends ServerGamePacketListenerImpl {
        final List<CompoundTag> payloads = new ArrayList<>();

        CaptureListener(ServerPlayer player, Connection connection) {
            super(player.serverLevel().getServer(), connection, player,
                    CommonListenerCookie.createInitial(player.getGameProfile(), false));
        }

        @Override public void send(Packet<?> packet) {
            if (packet instanceof ClientboundCustomPayloadPacket custom
                    && custom.payload() instanceof AcademyNetwork.ClientData data)
                payloads.add(data.data().copy());
        }

        @Override public void send(Packet<?> packet, PacketSendListener listener) { send(packet); }

        long latestCoinToken(ServerPlayer owner) {
            return payloads.stream().filter(tag -> tag.getString("kind").equals("coin_toss")
                    && tag.getInt("entity") == owner.getId()).reduce((old, latest) -> latest)
                    .orElseThrow(() -> new IllegalStateException("Native nearby-player broadcast did not emit coin token"))
                    .getLong("token");
        }
    }

    public static final class ReflectionHook {
        private final ServerPlayer owner;
        private final Villager target;
        int calls;
        ReflectionHook(ServerPlayer owner, Villager target) { this.owner = owner; this.target = target; }
        @SubscribeEvent public void reflect(SkillReflectEvent event) {
            if (event.attacker == owner && event.target == target) { calls++; event.setCanceled(true); }
        }
    }

    public static final class AttackHook {
        private final ServerPlayer owner;
        private final Villager target;
        int calls;
        String skill;
        AttackHook(ServerPlayer owner, Villager target) { this.owner = owner; this.target = target; }
        @SubscribeEvent public void attack(SkillAttackEvent event) {
            if (event.player == owner && event.target == target) {
                calls++;
                skill = event.skill;
                event.amount = 2;
            }
        }
    }
}
