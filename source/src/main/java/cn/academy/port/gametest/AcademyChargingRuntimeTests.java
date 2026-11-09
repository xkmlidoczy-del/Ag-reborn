package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.skill.ChargingEnergy;
import cn.academy.port.skill.CurrentCharging;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.LongConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.EnergyStorage;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Native held-skill tests, advanced by the real ServerLevel clock. FakePlayer is
 * not socket transport. Test-only FE providers exercise native capability lookup,
 * not an unported AcademyCraft machine. The existing runtime_empty template is used.
 * This class and its fixture providers are excluded from the distributable jar.
 */
@GameTestHolder("academy")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
public final class AcademyChargingRuntimeTests {
    private static final String TEMPLATE = "runtime_empty", BATCH = "academy_charging";
    private static final Map<BlockTarget, IEnergyStorage> BLOCKS = new HashMap<>();
    private static final Map<ItemStack, IEnergyStorage> ITEMS = new IdentityHashMap<>();
    private AcademyChargingRuntimeTests() {}
    private record BlockTarget(Level level, BlockPos pos) {}

    /** Only native GameTest mode installs these bounded fixture capabilities. */
    @SubscribeEvent
    public static void registerFixtureCapabilities(RegisterCapabilitiesEvent event) {
        if (!GameTestHooks.isGametestEnabled()) return;
        event.registerBlock(Capabilities.EnergyStorage.BLOCK,
                (level, pos, state, entity, side) -> side == Direction.UP ? BLOCKS.get(new BlockTarget(level, pos)) : null,
                Blocks.REDSTONE_BLOCK);
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> ITEMS.get(stack), Items.REDSTONE);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void charging_mainhand_developer_captures_mastery_and_switches_current_stack(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        var first = new ItemStack(AcademyCraft.DEVELOPER.get()); var second = new ItemStack(AcademyCraft.DEVELOPER.get());
        player.setItemSlot(EquipmentSlot.MAINHAND, first);
        request(player, "skill_start"); request(player, "skill_start");
        close(helper, state.overload, 65, "repeated start does not repay overload");
        close(helper, state.cp, 1800, "start consumes zero CP");
        f.worldTicks(elapsed -> {
            if (elapsed == 1) {
                close(helper, energy(first), 15, "real mainhand native storage receives 15 IF");
                close(helper, state.cp, 1797, "item first tick pays 3 CP");
                postTick(player); // Duplicate world-tick event cannot transfer again.
                close(helper, energy(first), 15, "same-world-tick replay cannot double charge");
                player.setItemSlot(EquipmentSlot.MAINHAND, second);
                state.experience.put("charging", 1.0);
            } else if (elapsed == 2) {
                close(helper, energy(first), 15, "original stack is no longer charged");
                close(helper, energy(second), 15, "new current mainhand is charged at captured novice speed");
                close(helper, state.cp, 1794, "current mastery cannot alter captured CP consumption");
                request(player, "skill_release"); request(player, "skill_release");
                helper.assertFalse(CurrentCharging.active(player), "release removes held context");
            } else if (elapsed == 3) {
                close(helper, energy(second), 15, "release prevents subsequent charging");
                helper.assertTrue(state.cooldowns.isEmpty(), "source charging has no cooldown"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void charging_unsupported_nonempty_item_trains_but_empty_hand_terminates(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(Items.STONE)); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 1) {
                close(helper, state.cp, 1797, "unsupported item still pays CP");
                close(helper, state.exp("charging"), CurrentChargingSession.UNSUPPORTED_EXPERIENCE, "unsupported item trains smaller EXP");
                player.setItemSlot(EquipmentSlot.MAINHAND, ItemStack.EMPTY);
            } else if (elapsed == 2) {
                helper.assertFalse(CurrentCharging.active(player), "empty current mainhand terminates captured item mode");
                close(helper, state.cp, 1797, "empty item does not consume CP");
                close(helper, state.exp("charging"), CurrentChargingSession.UNSUPPORTED_EXPERIENCE, "empty item gets no extra EXP");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void charging_full_supported_item_earns_support_experience(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        var full = new ItemStack(AcademyCraft.DEVELOPER.get());
        new DeveloperItemEnergy(full, DeveloperType.PORTABLE).energy(DeveloperType.PORTABLE.energy);
        player.setItemSlot(EquipmentSlot.MAINHAND, full); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 1) {
                close(helper, energy(full), 10000, "full item cannot exceed finite IF capacity");
                close(helper, state.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "full item gets supported EXP");
                close(helper, state.cp, 1797, "zero effective transfer still pays CP"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void charging_item_cp_exhaustion_stops_before_transfer_and_experience(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0); state.cp = 2;
        var stack = new ItemStack(AcademyCraft.DEVELOPER.get()); player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 1) {
                helper.assertFalse(CurrentCharging.active(player), "item CP failure terminates");
                close(helper, energy(stack), 0, "exhausted item transfers no IF");
                close(helper, state.exp("charging"), 0, "exhausted item earns no EXP");
                close(helper, state.cp, 2, "failed item consumption leaves CP unchanged"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void charging_block_capability_keeps_mode_after_equipping_item_and_restores_floor(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        var storage = f.block(new BlockPos(3, 2, 6), 1000, 1000);
        request(player, "skill_start");
        var stack = new ItemStack(AcademyCraft.DEVELOPER.get()); player.setItemSlot(EquipmentSlot.MAINHAND, stack);
        state.overload = 0;
        f.worldTicks(elapsed -> {
            if (elapsed == 1) {
                helper.assertValueEqual(storage.getEnergyStored(), 60, "real block capability receives source 15IF as 60FE");
                close(helper, energy(stack), 0, "captured block mode does not charge newly equipped item");
                close(helper, state.cp, 1797, "block successful tick pays CP after charge");
                close(helper, state.overload, 65, "floor restores post-start strain after recovery");
                close(helper, state.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "supported block EXP");
                helper.assertTrue(ChargingEnergy.blockSupported(helper.getLevel(), helper.absolutePos(new BlockPos(3, 2, 6))), "UP capability classified supported");
                helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void charging_block_cp_exhaustion_preserves_final_source_ordered_transfer(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0); state.cp = 0;
        var storage = f.block(new BlockPos(3, 2, 6), 1000, 1000); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 1) {
                helper.assertValueEqual(storage.getEnergyStored(), 60, "block transfers its final tick before failed CP check");
                close(helper, state.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "block awards EXP before failed CP check");
                close(helper, state.cp, 0, "failed block CP check leaves balance");
                helper.assertFalse(CurrentCharging.active(player), "exhausted block context terminates");
            } else if (elapsed == 2) {
                helper.assertValueEqual(storage.getEnergyStored(), 60, "terminated block does not charge on later tick"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void charging_fe_item_adapter_preserves_integer_bandwidth_and_supported_exp(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        var stack = new ItemStack(Items.REDSTONE); var storage = new EnergyStorage(17, 11, 0);
        f.item(stack, storage); player.setItemSlot(EquipmentSlot.MAINHAND, stack); request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 1) {
                helper.assertValueEqual(storage.getEnergyStored(), 11, "item capability receiver enforces its FE bandwidth");
            } else if (elapsed == 2) {
                helper.assertValueEqual(storage.getEnergyStored(), 17, "second tick fills only remaining capacity");
            } else if (elapsed == 3) {
                helper.assertValueEqual(storage.getEnergyStored(), 17, "full finite FE item stays full");
                close(helper, state.exp("charging"), 3 * CurrentChargingSession.SUPPORTED_EXPERIENCE, "full FE item remains supported for EXP");
                close(helper, state.cp, 1791, "three item ticks pay normal captured CP"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void charging_extract_only_block_is_supported_and_entities_occlude_machine_ray(GameTestHelper helper) {
        var f = new Fixture(helper); var player = f.player(); var state = ready(player, 0);
        var storage = f.block(new BlockPos(3, 2, 6), 1000, 0);
        request(player, "skill_start");
        f.worldTicks(elapsed -> {
            if (elapsed == 1) {
                helper.assertValueEqual(storage.getEnergyStored(), 0, "extract-only block cannot receive energy");
                close(helper, state.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "extract-only capability earns support EXP");
                f.target();
            } else if (elapsed == 2) {
                helper.assertValueEqual(storage.getEnergyStored(), 0, "occluding entity prevents any block transfer");
                close(helper, state.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE + CurrentChargingSession.UNSUPPORTED_EXPERIENCE,
                        "source traceLiving entity hit is an unsupported block-mode target"); helper.succeed();
            }
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void charging_lifecycle_subscribers_cancel_without_refund_or_revival(GameTestHelper helper) {
        var f = new Fixture(helper);
        for (String action : List.of("skill_abort", "abort_active", "toggle", "logout", "dimension", "state_replaced")) {
            var player = f.player(); var state = ready(player, 0);
            player.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.DEVELOPER.get()));
            request(player, "skill_start"); helper.assertTrue(CurrentCharging.active(player), action + " fixture starts");
            switch (action) {
                case "logout" -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player));
                case "dimension" -> NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerChangedDimensionEvent(player, Level.OVERWORLD, Level.NETHER));
                case "state_replaced" -> { AbilityStorage.remove(player); postTick(player); }
                default -> request(player, action);
            }
            helper.assertFalse(CurrentCharging.active(player), action + " cancels held context");
            close(helper, state.overload, 65, action + " does not refund already-paid start overload");
            request(player, "skill_release");
            close(helper, state.exp("charging"), 0, action + " release replay never charges");
        }
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void charging_death_and_clone_subscribers_remove_transient_contexts(GameTestHelper helper) {
        var f = new Fixture(helper); var dead = f.player(); var deadState = ready(dead, 0);
        dead.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.DEVELOPER.get())); request(dead, "skill_start");
        NeoForge.EVENT_BUS.post(new LivingDeathEvent(dead, dead.damageSources().generic()));
        helper.assertFalse(CurrentCharging.active(dead), "death subscriber ends charging context");
        helper.assertFalse(deadState.activated, "death deactivates recovered ability state");
        close(helper, energy(dead.getMainHandItem()), 0, "death before first tick transfers no IF");
        var original = f.player(); ready(original, 0);
        original.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.DEVELOPER.get())); request(original, "skill_start");
        var replacement = f.player();
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(replacement, original, false));
        helper.assertFalse(CurrentCharging.active(original), "clone subscriber removes original's context");
        helper.assertFalse(CurrentCharging.active(replacement), "clone never persists held-input context");
        close(helper, AbilityStorage.get(replacement).overload, 65, "nondeath clone preserves paid strain while canceling held input");
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void charging_overload_lock_interference_and_category_changes_end_context(GameTestHelper helper) {
        var f = new Fixture(helper); var overload = f.player(); var state = ready(overload, 0); state.overload = 40;
        overload.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.DEVELOPER.get())); request(overload, "skill_start");
        helper.assertFalse(CurrentCharging.active(overload), "start reaching overload cap has no surviving context");
        close(helper, state.overload, 100, "overload start still commits its paid strain");
        var interference = f.player(); var interferedState = ready(interference, 0);
        interference.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.DEVELOPER.get())); request(interference, "skill_start");
        interferedState.interfering = true;
        var category = f.player(); var categoryState = ready(category, 0);
        category.setItemSlot(EquipmentSlot.MAINHAND, new ItemStack(AcademyCraft.DEVELOPER.get())); request(category, "skill_start");
        categoryState.selectCategory("vecmanip");
        f.worldTicks(elapsed -> {
            if (elapsed == 1) {
                helper.assertFalse(CurrentCharging.active(interference), "server interference gate terminates context");
                helper.assertFalse(CurrentCharging.active(category), "category switch terminates stale context");
                close(helper, energy(interference.getMainHandItem()), 0, "interference prevents item transfer");
                close(helper, energy(category.getMainHandItem()), 0, "category change prevents item transfer"); helper.succeed();
            }
        });
    }

    private static AbilityProgress ready(ServerPlayer player, double mastery) {
        var state = AbilityStorage.get(player); state.selectCategory("electromaster"); state.setLevel(1);
        state.activated = true; state.experience.put("charging", mastery); return state;
    }
    private static double energy(ItemStack stack) { return new DeveloperItemEnergy(stack, DeveloperType.PORTABLE).energy(); }
    private static void request(ServerPlayer player, String action) {
        AcademyGameplay.request(player, new AcademyNetwork.Request(action, "charging"));
    }
    private static void postTick(ServerPlayer player) { player.tickCount++; NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player)); }
    private static void close(GameTestHelper helper, double actual, double expected, String label) {
        helper.assertTrue(Math.abs(actual - expected) <= .00001, label + ": expected " + expected + ", got " + actual);
    }
    private static final class Fixture {
        final GameTestHelper helper; final long started; long previous;
        final List<ServerPlayer> players = new ArrayList<>(); final List<Villager> targets = new ArrayList<>();
        final List<BlockTarget> blocks = new ArrayList<>(); final List<ItemStack> items = new ArrayList<>();
        boolean closed;
        Fixture(GameTestHelper helper) {
            this.helper = helper; started = previous = helper.getLevel().getGameTime();
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { cleanup(); }
            });
        }
        ServerPlayer player() {
            var player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[AC-ChargeTest]"));
            var pos = helper.absoluteVec(new Vec3(3.5, 1, 2.5)); player.moveTo(pos.x, pos.y, pos.z, 0, 0);
            player.setYHeadRot(0); player.setNoGravity(true); player.getAbilities().instabuild = false;
            player.setDeltaMovement(Vec3.ZERO); players.add(player); return player;
        }
        EnergyStorage block(BlockPos relative, int capacity, int bandwidth) {
            helper.setBlock(relative, Blocks.REDSTONE_BLOCK);
            var key = new BlockTarget(helper.getLevel(), helper.absolutePos(relative));
            var storage = new EnergyStorage(capacity, bandwidth, 0); BLOCKS.put(key, storage); blocks.add(key);
            helper.getLevel().invalidateCapabilities(key.pos); return storage;
        }
        void item(ItemStack stack, IEnergyStorage storage) { ITEMS.put(stack, storage); items.add(stack); }
        void target() {
            var target = helper.spawnWithNoFreeWill(EntityType.VILLAGER, new Vec3(3.5, 1, 4.5));
            target.setNoGravity(true); target.setNoAi(true); targets.add(target);
        }
        void worldTicks(LongConsumer assertions) {
            helper.onEachTick(() -> {
                long now = helper.getLevel().getGameTime(); if (closed || helper.testInfo.isDone() || now <= previous) return;
                helper.assertValueEqual(now - previous, 1L, "consecutive actual world ticks"); previous = now;
                for (var player : players) postTick(player); assertions.accept(now - started);
            });
        }
        void cleanup() {
            if (closed) return; closed = true;
            RuntimeException failure = null;
            for (var player : players) {
                try { CurrentCharging.abort(player); NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(player)); }
                catch (RuntimeException next) { if (failure == null) failure = next; else failure.addSuppressed(next); }
            }
            for (var target : targets) target.discard();
            for (var key : blocks) { BLOCKS.remove(key); helper.getLevel().invalidateCapabilities(key.pos); }
            for (var stack : items) ITEMS.remove(stack);
            if (failure != null) throw failure;
        }
    }
}
