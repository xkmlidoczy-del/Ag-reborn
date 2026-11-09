package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.api.SkillPerformEvent;
import cn.academy.port.bridge.ClassicEnergyBridges;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.former.ClassicMetalFormer;
import cn.academy.port.fusion.ClassicFusion;
import cn.academy.port.highpower.HighPowerCharging;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.machine.ImagFluxReceiver;
import cn.academy.port.machine.MachineDeveloperBlock;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.skill.CurrentCharging;
import cn.academy.port.wireless.ClassicWirelessDevices;
import cn.academy.port.wireless.ClassicWirelessNodeBlock;
import cn.academy.port.wireless.ClassicWirelessNodeBlockEntity;
import com.mojang.authlib.GameProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.energy.IEnergyStorage;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Enrolled-only utility extension through the unchanged classic server entry points.
 * Explicitly learned FakePlayers are manually ticked; delayed tests advance the real
 * ServerLevel clock, never its day time or a fabricated timestamp. These fixtures do
 * not establish survival acquisition, sockets, client effects, or foreign FE support.
 * Test providers and this class are excluded from the distributable mod jar.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid = "academy", bus = EventBusSubscriber.Bus.MOD)
public final class HighPowerChargingRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_charging";
    private static final BlockPos TARGET = new BlockPos(5, 2, 8);
    private static final Map<BlockKey, ForeignEnergy> FOREIGN_BLOCKS = new HashMap<>();
    private static final Map<ItemStack, ForeignEnergy> FOREIGN_ITEMS = new IdentityHashMap<>();
    private record BlockKey(Level level, BlockPos pos) {}
    private HighPowerChargingRuntimeTests() {}

    @SubscribeEvent
    public static void registerFixtureCapabilities(RegisterCapabilitiesEvent event) {
        if (!GameTestHooks.isGametestEnabled()) return;
        event.registerBlock(Capabilities.EnergyStorage.BLOCK, (level, pos, state, entity, side) -> {
            var storage = FOREIGN_BLOCKS.get(new BlockKey(level, pos));
            if (storage != null) storage.queries++;
            return side == Direction.UP ? storage : null;
        }, Blocks.LAPIS_BLOCK);
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> {
            var storage = FOREIGN_ITEMS.get(stack);
            if (storage != null) storage.queries++;
            return storage;
        }, Items.LAPIS_LAZULI);
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void novice_first_positive_transfer_pays_once_and_real_next_tick_keeps_captured_mastery(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 0); var s = AbilityStorage.get(p);
        var stack = portable(p); var before = Ledger.of(s);
        var strains = new ArrayList<Float>(); var hook = f.hook(p, event -> strains.add(event.overload));
        h.assertTrue(CurrentCharging.start(p), "learned enrolled portable hold starts");
        h.assertTrue(CurrentCharging.active(p), "unchanged active entry reports enhanced hold");
        before.unchanged(h, s, "start is a free preflight");
        CurrentCharging.tick(p);
        near(h, energy(stack), 15, "first positive native IF transfer");
        near(h, s.cp, 1797, "novice 15 IF costs classic 3 CP");
        near(h, s.overload, 65, "first positive transfer pays novice start strain");
        near(h, strains.getFirst(), 65, "first native cost callback receives classic novice start strain");
        near(h, s.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "one positive-transfer XP award");
        var paid = Ledger.of(s); CurrentCharging.tick(p);
        paid.unchanged(h, s, "same-world-tick replay has no resource or XP effects");
        near(h, energy(stack), 15, "same-world-tick replay has no second IF transfer");
        double trainedOverload = s.extraOverload;
        s.experience.put("charging", .5);
        long firstTick = h.getLevel().getGameTime();
        h.runAfterDelay(1, () -> {
            h.assertTrue(h.getLevel().getGameTime() > firstTick, "real world time advanced");
            CurrentCharging.tick(p);
            near(h, energy(stack), 30, "live mastery cannot replace the captured novice speed");
            near(h, s.cp, 1794, "captured novice rate remains 3 CP");
            near(h, s.overload, 65, "later transfer does not repay start strain");
            near(h, s.extraOverload, trainedOverload, "later transfers do not retrain start overload");
            h.assertValueEqual(hook.calls, 2, "two genuine positive transfers post exactly two cost events");
            near(h, strains.getLast(), 0, "later native cost callback receives zero start strain");
            near(h, s.exp("charging"), .5 + CurrentChargingSession.SUPPORTED_EXPERIENCE, "second positive transfer awards XP once");
            h.assertTrue(CurrentCharging.release(p), "unchanged release entry terminates enhanced hold");
            h.assertFalse(CurrentCharging.active(p), "released hold is gone");
            h.assertFalse(CurrentCharging.release(p), "duplicate release is inert");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void one_if_headroom_costs_point_two_cp_and_filling_autostops(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 0); var s = AbilityStorage.get(p);
        var stack = portable(p); ClassicEnergyItemHelper.setEnergy(stack, DeveloperType.PORTABLE.energy - 1);
        h.assertTrue(CurrentCharging.start(p), "one IF headroom is a valid target"); CurrentCharging.tick(p);
        near(h, energy(stack), DeveloperType.PORTABLE.energy, "only real one-IF headroom is accepted");
        near(h, s.cp, 1799.8, "accepted delta / captured speed gives 0.2 CP");
        near(h, s.overload, 65, "positive partial fill still pays first start strain");
        near(h, s.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "partial positive fill awards once");
        h.assertFalse(CurrentCharging.active(p), "finite target completion ends hold immediately");
        var paid = Ledger.of(s); CurrentCharging.tick(p); CurrentCharging.release(p); CurrentCharging.start(p);
        paid.unchanged(h, s, "full-target replays never train or spend"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void registered_energy_unit_mastery_bandwidth_limits_thirty_five_to_twenty_if(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 1); var s = AbilityStorage.get(p);
        var stack = new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get()); hand(p, stack);
        var hook = f.hook(p, event -> {});
        h.assertTrue(CurrentCharging.start(p), "registered native energy unit starts"); CurrentCharging.tick(p);
        near(h, energy(stack), 20, "native twenty-IF item bandwidth limits master thirty-five-IF request");
        near(h, s.cp, 1796, "twenty accepted IF costs four CP, not seven");
        near(h, s.overload, 48, "captured master start strain is forty-eight");
        near(h, s.levelExperience, CurrentChargingSession.SUPPORTED_EXPERIENCE, "mastered skill still has just one practice progress award");
        h.assertValueEqual(hook.calls, 1, "one combined cost event for the positive transfer"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void full_invalid_creative_and_multicount_items_fail_before_payment(GameTestHelper h) {
        var f = new Fixture(h);
        for (String kind : List.of("full", "unsupported", "creative", "portable_count", "unit_count")) {
            var p = f.player(true, 0); var s = AbilityStorage.get(p); ItemStack stack;
            switch (kind) {
                case "unsupported" -> stack = new ItemStack(Items.STONE);
                case "unit_count" -> stack = new ItemStack(ClassicEnergyItems.ENERGY_UNIT.get(), 2);
                default -> stack = new ItemStack(AcademyCraft.DEVELOPER.get());
            }
            if (kind.equals("full")) ClassicEnergyItemHelper.setEnergy(stack, DeveloperType.PORTABLE.energy);
            if (kind.equals("portable_count")) stack.setCount(2);
            if (kind.equals("creative")) p.getAbilities().instabuild = true;
            hand(p, stack); var before = Ledger.of(s); var hook = f.hook(p, event -> {});
            h.assertFalse(CurrentCharging.start(p), kind + " rejected before hold starts"); CurrentCharging.tick(p);
            h.assertFalse(CurrentCharging.active(p), kind + " leaves no active hold");
            before.unchanged(h, s, kind + " leaves CP, strain, training and XP unchanged");
            h.assertValueEqual(hook.calls, 0, kind + " does not post a cost event");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void full_native_mainhand_never_falls_back_to_aimed_machine(GameTestHelper h) {
        var f = new Fixture(h); var node = f.node(ClassicWirelessDevices.BASIC.get());
        var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = portable(p);
        ClassicEnergyItemHelper.setEnergy(stack, DeveloperType.PORTABLE.energy); var before = Ledger.of(s);
        h.assertFalse(CurrentCharging.start(p), "known full native item refuses instead of choosing block mode");
        CurrentCharging.tick(p); before.unchanged(h, s, "full mainhand preflight is free");
        near(h, node.getEnergy(), 0, "aimed native machine receives no fallback energy"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void unsupported_stone_chooses_block_mode_and_equipping_item_does_not_change_mode(GameTestHelper h) {
        var f = new Fixture(h); var node = f.node(ClassicWirelessDevices.BASIC.get());
        var p = f.player(true, 0); var s = AbilityStorage.get(p); hand(p, new ItemStack(Items.STONE));
        h.assertTrue(CurrentCharging.start(p), "unsupported held stone permits a known aimed block");
        var stack = portable(p); CurrentCharging.tick(p);
        near(h, node.getEnergy(), 15, "captured block mode still charges aimed native node");
        near(h, energy(stack), 0, "newly equipped supported item cannot steal block mode");
        near(h, s.cp, 1797, "block success has paid CP before its native write");
        near(h, s.overload, 65, "block first positive transfer pays one start strain"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void captured_item_mode_uses_live_native_stack_and_never_falls_back_after_removal(GameTestHelper h) {
        var f = new Fixture(h); var node = f.node(ClassicWirelessDevices.BASIC.get());
        var p = f.player(true, 0); var s = AbilityStorage.get(p); var first = portable(p);
        h.assertTrue(CurrentCharging.start(p), "native mainhand captures item mode"); var replacement = portable(p);
        CurrentCharging.tick(p);
        near(h, energy(first), 0, "old hand stack is untouched"); near(h, energy(replacement), 15, "current native hand stack receives transfer");
        near(h, node.getEnergy(), 0, "item mode has priority over aimed native block");
        var paid = Ledger.of(s); hand(p, ItemStack.EMPTY);
        h.runAfterDelay(1, () -> {
            CurrentCharging.tick(p); h.assertFalse(CurrentCharging.active(p), "missing native hand terminates item mode");
            paid.unchanged(h, s, "missing hand has no second payment or XP");
            near(h, node.getEnergy(), 0, "lost item never switches to block charging"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void insufficient_cp_and_would_overload_refuse_before_native_energy_and_training(GameTestHelper h) {
        var f = new Fixture(h);
        for (boolean insufficientCp : List.of(true, false)) {
            var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = portable(p);
            if (insufficientCp) s.cp = 2; else s.overload = 40;
            var before = Ledger.of(s); CurrentCharging.start(p); CurrentCharging.tick(p);
            h.assertFalse(CurrentCharging.active(p), "unpayable transaction ends hold");
            before.unchanged(h, s, insufficientCp ? "insufficient CP is atomic" : "would-overload is atomic");
            near(h, energy(stack), 0, "unpayable transfer cannot grant free IF");
        }
        var node = f.node(ClassicWirelessDevices.BASIC.get()); var p = f.player(true, 0); var s = AbilityStorage.get(p);
        s.cp = 0; var before = Ledger.of(s); CurrentCharging.start(p); CurrentCharging.tick(p);
        near(h, node.getEnergy(), 0, "enrolled empty-CP block mode has no classic final free tick");
        before.unchanged(h, s, "empty-CP block transfer has no XP or training"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void foreign_item_and_block_fe_receivers_are_never_queried_or_simulated(GameTestHelper h) {
        var f = new Fixture(h); var block = f.foreignBlock();
        var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = new ItemStack(Items.LAPIS_LAZULI);
        var item = f.foreignItem(stack); hand(p, stack); var before = Ledger.of(s);
        h.assertFalse(CurrentCharging.start(p), "foreign FE item plus foreign aimed FE block fail closed"); CurrentCharging.tick(p);
        before.unchanged(h, s, "unknown FE cannot trigger any debit or training");
        h.assertValueEqual(item.queries + block.queries, 0, "enhanced path never invokes foreign capability providers");
        h.assertValueEqual(item.receives + block.receives, 0, "no real or simulated foreign receive callbacks");
        h.assertValueEqual(item.energy + block.energy, 0, "foreign stores remain untouched"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void real_nodes_all_tiers_and_native_receivers_receive_one_fractional_transfer(GameTestHelper h) {
        var f = new Fixture(h);
        for (var block : List.of(ClassicWirelessDevices.BASIC.get(), ClassicWirelessDevices.STANDARD.get(), ClassicWirelessDevices.ADVANCED.get())) {
            var node = f.node(block); node.setEnergy(.375); var p = f.player(true, 0);
            h.assertTrue(CurrentCharging.start(p), "actual native node tier starts"); CurrentCharging.tick(p);
            near(h, node.getEnergy(), 15.375, "native node preserves fractional IF in one write");
            near(h, AbilityStorage.get(p).cp, 1797, "actual node transaction pays three CP");
            CurrentCharging.release(p); h.setBlock(TARGET, Blocks.AIR);
        }
        for (var block : List.of(ClassicFusion.FUSOR_BLOCK.get(), ClassicMetalFormer.BLOCK.get())) {
            h.setBlock(TARGET, block); var receiver = (ImagFluxReceiver) h.getBlockEntity(TARGET);
            receiver.injectEnergy(.375); var p = f.player(true, 0);
            h.assertTrue(CurrentCharging.start(p), "known final native receiver starts"); CurrentCharging.tick(p);
            near(h, receiver.getEnergy(), 15.375, "known native receiver preserves fractional IF exactly once");
            near(h, AbilityStorage.get(p).cp, 1797, "known receiver shares paid classic CP transaction");
            CurrentCharging.release(p); h.setBlock(TARGET, Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void rf_output_receiver_with_hidden_transfer_state_is_not_in_closed_native_whitelist(GameTestHelper h) {
        var f = new Fixture(h); h.setBlock(TARGET, ClassicEnergyBridges.OUTPUT.get());
        var receiver = (ImagFluxReceiver) h.getBlockEntity(TARGET); var p = f.player(true, 0);
        var s = AbilityStorage.get(p); var before = Ledger.of(s);
        h.assertFalse(CurrentCharging.start(p), "native receiver interface alone cannot opt an unreviewed machine into transactions");
        CurrentCharging.tick(p); near(h, receiver.getEnergy(), 0, "unreviewed RF output native buffer stays untouched");
        before.unchanged(h, s, "unsupported native receiver never trains or spends"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void normal_advanced_root_and_all_slave_cells_charge_only_canonical_native_battery(GameTestHelper h) {
        var f = new Fixture(h);
        for (var block : List.of(MachineDevelopers.NORMAL.get(), MachineDevelopers.ADVANCED.get())) {
            var root = f.machine(block); double expected = 0;
            for (int part = 0; part < 8; part++) {
                var pos = root.getBlockPos().offset(MachineDeveloperBlock.offset(part, Direction.NORTH));
                var cell = (MachineDeveloperBlockEntity) h.getLevel().getBlockEntity(pos);
                var p = f.player(true, 0); f.aimAtSide(p, pos);
                h.assertTrue(CurrentCharging.start(p), "complete " + block.type + " part " + part + " is aimed native receiver");
                CurrentCharging.tick(p); expected += 15;
                near(h, root.battery().getEnergy(), expected, "each body part commits exactly once into live root");
                near(h, AbilityStorage.get(p).cp, 1797, "body part payment is one classic transaction");
                if (part != 0) near(h, cell.battery().persistedEnergy(), 0, "slave local battery never duplicates root energy");
                CurrentCharging.release(p);
            }
            var bridge = h.getLevel().getCapability(Capabilities.EnergyStorage.BLOCK, root.getBlockPos(), Direction.UP);
            int quota = (int) (block.type.bandwidth * 4);
            h.assertTrue(bridge != null && bridge.receiveEnergy(quota, true) == quota,
                    "native charging leaves the simultaneous machine FE quota untouched");
            h.setBlock(TARGET, Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void incomplete_removed_occluded_and_out_of_range_blocks_never_pay(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 0); var s = AbilityStorage.get(p); var before = Ledger.of(s);
        h.setBlock(TARGET, MachineDevelopers.NORMAL.get().defaultBlockState().setValue(MachineDeveloperBlock.PART, 0));
        h.assertFalse(CurrentCharging.start(p), "incomplete developer origin has no known usable target");
        h.setBlock(TARGET, Blocks.AIR); var node = f.node(ClassicWirelessDevices.BASIC.get());
        h.setBlock(new BlockPos(5, 2, 6), Blocks.STONE);
        h.assertFalse(CurrentCharging.start(p), "solid non-receiver occludes native target");
        h.setBlock(new BlockPos(5, 2, 6), Blocks.AIR);
        h.assertTrue(CurrentCharging.start(p), "unoccluded native target begins"); h.setBlock(TARGET, Blocks.AIR); CurrentCharging.tick(p);
        h.assertFalse(CurrentCharging.active(p), "removed selected block ends hold"); near(h, node.getEnergy(), 0, "removed native receiver receives no write");
        h.setBlock(new BlockPos(5, 2, 20), ClassicWirelessDevices.BASIC.get());
        h.assertFalse(CurrentCharging.start(p), "native block beyond fifteen meters is not selected");
        before.unchanged(h, s, "all invalid native block selections are free"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void unloaded_aim_does_not_load_chunks_or_pay(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 0); var s = AbilityStorage.get(p); var before = Ledger.of(s);
        var absent = new BlockPos(29_000_000, h.getLevel().getMinBuildHeight() + 2, 29_000_000);
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(absent.getX() >> 4, absent.getZ() >> 4) == null, "remote aim fixture starts unloaded");
        int loaded = h.getLevel().getChunkSource().getLoadedChunksCount();
        p.moveTo(absent.getX() + .5, absent.getY(), absent.getZ() + .5, 0, 0); p.setYHeadRot(0);
        h.assertFalse(CurrentCharging.start(p), "unloaded aim refuses before native ray or target access"); CurrentCharging.tick(p);
        h.assertValueEqual(h.getLevel().getChunkSource().getLoadedChunksCount(), loaded, "enhanced preflight never force-loads ray chunks");
        h.assertTrue(h.getLevel().getChunkSource().getChunkNow(absent.getX() >> 4, absent.getZ() >> 4) == null, "same remote chunk remains absent");
        before.unchanged(h, s, "unloaded target has no debit or XP"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void reentrant_tick_and_start_are_ignored_while_outer_transfer_commits_only_once(GameTestHelper h) {
        var f = new Fixture(h);
        for (String action : List.of("tick", "start", "both")) {
            var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = portable(p);
            var hook = f.hook(p, event -> {
                if (!action.equals("start")) CurrentCharging.tick(p);
                if (!action.equals("tick")) h.assertFalse(CurrentCharging.start(p), "nested start cannot replace owning hold");
            });
            h.assertTrue(CurrentCharging.start(p), action + " reentry fixture begins"); CurrentCharging.tick(p);
            h.assertValueEqual(hook.calls, 1, action + " reentry never recursively posts cost events");
            near(h, energy(stack), 15, action + " reentry has one outer transfer");
            near(h, s.cp, 1797, action + " reentry has one CP debit"); near(h, s.overload, 65, action + " reentry has one strain debit");
            near(h, s.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, action + " reentry has one XP award");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void callback_release_and_combined_reentry_abort_before_all_ledgers_change(GameTestHelper h) {
        var f = new Fixture(h);
        for (boolean combined : List.of(false, true)) {
            var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = portable(p); var before = Ledger.of(s);
            var hook = f.hook(p, event -> {
                if (combined) { CurrentCharging.tick(p); CurrentCharging.start(p); }
                CurrentCharging.release(p);
                if (combined) { CurrentCharging.start(p); CurrentCharging.tick(p); }
            });
            h.assertTrue(CurrentCharging.start(p), "release callback fixture begins"); CurrentCharging.tick(p);
            h.assertValueEqual(hook.calls, 1, "callback cancellation has one event and no recursion");
            before.unchanged(h, s, "hold cancellation aborts payment, training and XP");
            near(h, energy(stack), 0, "canceled native transaction transfers no IF");
            h.assertFalse(CurrentCharging.active(p), "canceled hold cannot be resurrected within callback");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void callback_hand_replacement_count_and_native_energy_mutations_abort_transaction(GameTestHelper h) {
        var f = new Fixture(h);
        for (String action : List.of("replace", "count", "energy")) {
            var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = portable(p);
            var replacement = new ItemStack(AcademyCraft.DEVELOPER.get()); var before = Ledger.of(s);
            var hook = f.hook(p, event -> {
                switch (action) {
                    case "replace" -> hand(p, replacement);
                    case "count" -> stack.setCount(2);
                    default -> ClassicEnergyItemHelper.setEnergy(stack, 7);
                }
            });
            h.assertTrue(CurrentCharging.start(p), action + " mutation fixture begins"); CurrentCharging.tick(p);
            h.assertValueEqual(hook.calls, 1, "mutation is observed through real NeoForge cost event");
            before.unchanged(h, s, action + " invalidates native plan before ledger payment");
            near(h, energy(stack), action.equals("energy") ? 7 : 0, "only the explicit hook's native energy mutation remains");
            near(h, energy(replacement), 0, "replacement stack never receives a stale transaction");
            h.assertFalse(CurrentCharging.active(p), action + " invalidated hold terminates");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void callback_block_replacement_and_energy_mutation_abort_transaction(GameTestHelper h) {
        var f = new Fixture(h);
        for (boolean replace : List.of(false, true)) {
            var node = f.node(ClassicWirelessDevices.BASIC.get()); var p = f.player(true, 0);
            var s = AbilityStorage.get(p); var before = Ledger.of(s);
            f.hook(p, event -> {
                if (replace) { h.setBlock(TARGET, Blocks.AIR); h.setBlock(TARGET, ClassicWirelessDevices.BASIC.get()); }
                else node.setEnergy(7);
            });
            h.assertTrue(CurrentCharging.start(p), "block mutation fixture starts"); CurrentCharging.tick(p);
            before.unchanged(h, s, "changed native block invalidates all planned ledger mutations");
            near(h, node.getEnergy(), replace ? 0 : 7, "old native target has only the explicit hook's change");
            var current = (ClassicWirelessNodeBlockEntity) h.getBlockEntity(TARGET);
            near(h, current.getEnergy(), replace ? 0 : 7, "replacement or modified receiver receives no additional IF");
            h.assertFalse(CurrentCharging.active(p), "block mutation terminates stale hold"); h.setBlock(TARGET, Blocks.AIR);
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void callback_creative_change_and_cost_rewrites_fail_closed_without_training(GameTestHelper h) {
        var f = new Fixture(h);
        for (String action : List.of("creative", "free_cp", "strain", "nan")) {
            var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = portable(p); var before = Ledger.of(s);
            f.hook(p, event -> {
                switch (action) {
                    case "creative" -> p.getAbilities().instabuild = true;
                    case "free_cp" -> event.cp = 0;
                    case "strain" -> event.overload = 0;
                    default -> event.cp = Float.NaN;
                }
            });
            h.assertTrue(CurrentCharging.start(p), action + " callback fixture begins"); CurrentCharging.tick(p);
            before.unchanged(h, s, action + " cannot create free charging or creative training");
            near(h, energy(stack), 0, action + " cannot reach native write");
            h.assertFalse(CurrentCharging.active(p), action + " aborts hold");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void nonempty_wrong_type_quota_entries_fail_closed_even_with_current_last_tick(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = portable(p);
        var quota = new CompoundTag(); var malformed = new ListTag(); malformed.add(StringTag.valueOf("not-a-compound"));
        quota.put("entries", malformed); quota.putLong("lastTick", p.getServer().overworld().getGameTime());
        p.getPersistentData().put(HighPowerCharging.DATA_KEY, quota); var before = Ledger.of(s); var hook = f.hook(p, event -> {});
        CurrentCharging.start(p); CurrentCharging.tick(p);
        before.unchanged(h, s, "malformed saved quota cannot be reset into a fresh allowance");
        near(h, energy(stack), 0, "malformed same-tick quota grants no IF");
        h.assertValueEqual(hook.calls, 0, "invalid durable guard is rejected before cost callbacks");
        h.assertTrue(p.getPersistentData().getCompound(HighPowerCharging.DATA_KEY).equals(quota), "failure preserves malformed evidence instead of silently repairing it");
        h.assertFalse(CurrentCharging.active(p), "invalid saved quota aborts hold"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void representable_tiny_if_headroom_cannot_transfer_when_float_cp_debit_would_be_zero(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = portable(p);
        double initial = Math.nextDown(DeveloperType.PORTABLE.energy); ClassicEnergyItemHelper.setEnergy(stack, initial);
        h.assertTrue(initial < DeveloperType.PORTABLE.energy, "one native-double ULP is real IF headroom"); var before = Ledger.of(s);
        CurrentCharging.start(p); CurrentCharging.tick(p);
        before.unchanged(h, s, "rounded-to-zero float CP debit cannot train or pay strain");
        h.assertTrue(energy(stack) == initial, "tiny representable IF cannot be minted for a rounded-to-zero CP price");
        h.assertFalse(CurrentCharging.active(p), "unrepresentable payment ends hold"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void enrollment_during_live_classic_creative_hold_aborts_before_next_transfer(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(false, 0); var s = AbilityStorage.get(p); var stack = portable(p);
        p.getAbilities().instabuild = true;
        h.assertTrue(CurrentCharging.start(p), "classic creative hold exists before enrollment"); CurrentCharging.tick(p);
        near(h, energy(stack), 15, "non-enrolled creative fixture demonstrates original source transfer");
        var paid = Ledger.of(s); h.assertTrue(HighPowerCombat.enroll(p, 1), "enrollment changes the live applicability gate");
        h.runAfterDelay(1, () -> {
            CurrentCharging.tick(p); h.assertFalse(CurrentCharging.active(p), "enrollment retires pre-existing classic hold");
            paid.unchanged(h, s, "retired classic creative hold cannot keep training under enrollment");
            near(h, energy(stack), 15, "enrollment blocks the next formerly creative free transfer");
            h.assertFalse(CurrentCharging.start(p), "enrolled creative state cannot restart through classic entry"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void invalid_existing_enrollment_cannot_fall_back_to_classic_charging(GameTestHelper h) {
        var f = new Fixture(h);
        for (boolean creative : List.of(false, true)) {
            var p = f.player(true, 0); var s = AbilityStorage.get(p); var stack = portable(p);
            p.getPersistentData().put(HighPowerCombat.DATA_KEY, new CompoundTag()); p.getAbilities().instabuild = creative;
            var before = Ledger.of(s);
            h.assertFalse(CurrentCharging.start(p), "presence of invalid enrollment remains fail-closed, creative=" + creative);
            CurrentCharging.tick(p); before.unchanged(h, s, "invalid enrollment never enters legacy spending or training");
            near(h, energy(stack), 0, "invalid enrollment cannot obtain legacy IF");
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 10)
    public static void non_enrolled_classic_full_and_unsupported_items_keep_original_order_and_training(GameTestHelper h) {
        var f = new Fixture(h); var full = f.player(false, 0); var fullState = AbilityStorage.get(full); var stack = portable(full);
        ClassicEnergyItemHelper.setEnergy(stack, DeveloperType.PORTABLE.energy);
        h.assertTrue(CurrentCharging.start(full), "non-enrolled full item retains source begin");
        near(h, fullState.overload, 65, "classic begin still pays overload immediately"); CurrentCharging.tick(full);
        near(h, fullState.cp, 1797, "classic full item still pays its tick CP");
        near(h, fullState.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "classic full supported item still trains");
        var unsupported = f.player(false, 0); var unsupportedState = AbilityStorage.get(unsupported); hand(unsupported, new ItemStack(Items.STONE));
        h.assertTrue(CurrentCharging.start(unsupported), "non-enrolled unsupported item retains source behavior"); CurrentCharging.tick(unsupported);
        near(h, unsupportedState.cp, 1797, "classic unsupported item still spends CP");
        near(h, unsupportedState.exp("charging"), CurrentChargingSession.UNSUPPORTED_EXPERIENCE, "classic unsupported item retains smaller XP");
        h.succeed();
    }

    /** Global clear is deliberately in its own batch, away from other tests' live holds. */
    @GameTest(template = TEMPLATE, batch = "highpower_charging_durable_guards", timeoutTicks = 15)
    public static void same_tick_budget_survives_release_clear_logout_and_actual_player_nbt_reload(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(true, 1); var s = AbilityStorage.get(p); var stack = portable(p);
        h.assertTrue(CurrentCharging.start(p), "durable guard fixture begins"); CurrentCharging.tick(p);
        near(h, energy(stack), 35, "one master transfer before lifecycle probes"); var paid = Ledger.of(s);
        h.assertTrue(CurrentCharging.release(p), "release clears transient hold"); CurrentCharging.start(p); CurrentCharging.tick(p);
        paid.unchanged(h, s, "release/repress cannot buy a second same-tick transfer"); near(h, energy(stack), 35, "same tick remains single transfer");
        CurrentCharging.clear(); CurrentCharging.start(p); CurrentCharging.tick(p);
        paid.unchanged(h, s, "global transient clear cannot reset durable resource guards"); near(h, energy(stack), 35, "clear does not reopen same-tick IF allowance");
        NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p)); s = AbilityStorage.get(p);
        CurrentCharging.start(p); CurrentCharging.tick(p); paid.unchanged(h, s, "logout/cache reload preserves same-tick payment guard");
        near(h, energy(stack), 35, "logout cannot mint second native IF transfer");
        AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag());
        CurrentCharging.remove(p); AbilityStorage.remove(p);
        var reloaded = f.rawPlayer(); reloaded.load(saved); var restored = AbilityStorage.get(reloaded);
        h.assertTrue(HighPowerCombat.enabled(reloaded), "actual player NBT restores enrollment");
        h.assertTrue(reloaded.getMainHandItem().is(AcademyCraft.DEVELOPER.get()), "actual player inventory NBT restores native target");
        near(h, energy(reloaded.getMainHandItem()), 35, "saved native item retains paid energy");
        CurrentCharging.start(reloaded); CurrentCharging.tick(reloaded);
        paid.unchanged(h, restored, "actual NBT reload preserves same-tick CP/XP guard");
        near(h, energy(reloaded.getMainHandItem()), 35, "actual NBT reload cannot replay native write");
        h.runAfterDelay(1, () -> {
            if (!CurrentCharging.active(reloaded)) CurrentCharging.start(reloaded);
            CurrentCharging.tick(reloaded);
            near(h, energy(reloaded.getMainHandItem()), 70, "genuine later world tick restores one ordinary transfer opportunity");
            near(h, restored.cp, 1786, "only two genuine master transfers were paid"); h.succeed();
        });
    }

    private static ItemStack portable(ServerPlayer player) { var stack = new ItemStack(AcademyCraft.DEVELOPER.get()); hand(player, stack); return stack; }
    private static void hand(ServerPlayer player, ItemStack stack) { player.setItemSlot(EquipmentSlot.MAINHAND, stack); }
    private static double energy(ItemStack stack) { return ClassicEnergyItemHelper.getEnergy(stack); }
    private static void near(GameTestHelper h, double actual, double expected, String label) {
        double tolerance = Math.max(1e-8, Math.ulp((float) expected) * 1.1);
        h.assertTrue(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                label + ": expected " + expected + ", got " + actual);
    }
    private record Ledger(double cp, double overload, double extraCp, double extraOverload,
                          double xp, double levelExperience, int cpDelay, int overloadDelay) {
        static Ledger of(AbilityProgress s) { return new Ledger(s.cp, s.overload, s.extraCp, s.extraOverload,
                s.exp("charging"), s.levelExperience, s.cpDelay, s.overloadDelay); }
        void unchanged(GameTestHelper h, AbilityProgress s, String label) {
            h.assertTrue(equals(of(s)), label + ": before=" + this + ", after=" + of(s));
        }
    }
    public static final class PerformHook {
        final ServerPlayer player; final Consumer<SkillPerformEvent> action; int calls; boolean inside;
        PerformHook(ServerPlayer player, Consumer<SkillPerformEvent> action) { this.player = player; this.action = action; }
        @SubscribeEvent public void perform(SkillPerformEvent event) {
            if (event.player != player || !event.skill.equals(CurrentCharging.ID)) return;
            calls++;
            // A broken reentry fence is an assertion failure, never unbounded fixture recursion.
            if (!inside) { inside = true; try { action.accept(event); } finally { inside = false; } }
        }
    }
    private static final class ForeignEnergy implements IEnergyStorage {
        int queries, receives, energy;
        public int receiveEnergy(int request, boolean simulate) { receives++; int accepted = Math.min(request, 1000 - energy); if (!simulate) energy += accepted; return accepted; }
        public int extractEnergy(int request, boolean simulate) { return 0; }
        public int getEnergyStored() { return energy; }
        public int getMaxEnergyStored() { return 1000; }
        public boolean canExtract() { return false; }
        public boolean canReceive() { return true; }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>();
        final List<PerformHook> hooks = new ArrayList<>(); final List<BlockKey> blocks = new ArrayList<>();
        final List<ItemStack> items = new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
                public void testAddedForRerun(GameTestInfo original, GameTestInfo rerun, GameTestRunner runner) { cleanup(); }
            });
        }
        ServerPlayer rawPlayer() {
            var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-Charging]"));
            Vec3 pos = h.absoluteVec(new Vec3(5.5, 1, 3.5)); p.moveTo(pos.x, pos.y, pos.z, 0, 0); p.setYHeadRot(0);
            p.setNoGravity(true); p.setDeltaMovement(Vec3.ZERO); p.getAbilities().instabuild = false; players.add(p); return p;
        }
        ServerPlayer player(boolean enrolled, double mastery) {
            var p = rawPlayer(); var s = AbilityStorage.get(p); s.selectCategory("electromaster"); s.setLevel(1);
            s.activated = true; s.experience.put("charging", mastery);
            if (enrolled) h.assertTrue(HighPowerCombat.enroll(p, 1), "explicit native high-power fixture enrollment");
            return p;
        }
        PerformHook hook(ServerPlayer player, Consumer<SkillPerformEvent> action) {
            var hook = new PerformHook(player, action); NeoForge.EVENT_BUS.register(hook); hooks.add(hook); return hook;
        }
        ClassicWirelessNodeBlockEntity node(ClassicWirelessNodeBlock block) {
            h.setBlock(TARGET, block); var node = (ClassicWirelessNodeBlockEntity) h.getBlockEntity(TARGET);
            h.assertTrue(node.available(), "actual loaded native node fixture"); return node;
        }
        MachineDeveloperBlockEntity machine(MachineDeveloperBlock block) {
            var origin = h.absolutePos(TARGET);
            for (int part = 0; part < 8; part++) h.getLevel().setBlock(origin.offset(MachineDeveloperBlock.offset(part, Direction.NORTH)),
                    block.defaultBlockState().setValue(MachineDeveloperBlock.FACING, Direction.NORTH).setValue(MachineDeveloperBlock.PART, part), Block.UPDATE_CLIENTS);
            var root = (MachineDeveloperBlockEntity) h.getLevel().getBlockEntity(origin);
            h.assertTrue(root != null && root.isOrigin() && root.available(), "complete actual native developer multiblock fixture"); return root;
        }
        void aimAtSide(ServerPlayer p, BlockPos target) {
            p.moveTo(target.getX() - 3, target.getY() + .5 - p.getEyeHeight(), target.getZ() + .5, -90, 0); p.setYHeadRot(-90);
        }
        ForeignEnergy foreignBlock() {
            h.setBlock(TARGET, Blocks.LAPIS_BLOCK); var key = new BlockKey(h.getLevel(), h.absolutePos(TARGET));
            var storage = new ForeignEnergy(); FOREIGN_BLOCKS.put(key, storage); blocks.add(key); h.getLevel().invalidateCapabilities(key.pos()); return storage;
        }
        ForeignEnergy foreignItem(ItemStack stack) { var storage = new ForeignEnergy(); FOREIGN_ITEMS.put(stack, storage); items.add(stack); return storage; }
        void cleanup() {
            if (closed) return; closed = true;
            for (var hook : hooks) NeoForge.EVENT_BUS.unregister(hook);
            RuntimeException failure = null;
            for (var p : players) try {
                CurrentCharging.remove(p); NeoForge.EVENT_BUS.post(new PlayerEvent.PlayerLoggedOutEvent(p)); p.discard();
            } catch (RuntimeException next) { if (failure == null) failure = next; else failure.addSuppressed(next); }
            for (var key : blocks) { FOREIGN_BLOCKS.remove(key); h.getLevel().invalidateCapabilities(key.pos()); }
            for (var stack : items) FOREIGN_ITEMS.remove(stack);
            if (failure != null) throw failure;
        }
    }
}
