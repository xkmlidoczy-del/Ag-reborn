package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.ElectromasterPrototype;
import cn.academy.port.highpower.HighPowerCategories;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.HighPowerResources.ControlHold;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.core.FoldedMirrorRules;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native ledger foundation tests. Direct hold/payment calls are declared fixtures, not skill activation proof. */
@GameTestHolder("academy_control_hold_runtime")
@net.neoforged.fml.common.EventBusSubscriber(modid="academy")
@PrefixGameTestTemplate(false)
public final class HighPowerControlHoldRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_control_hold";
    private HighPowerControlHoldRuntimeTests() {}

    @net.neoforged.bus.api.SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST)
    public static void installFocusedTemplate(net.neoforged.neoforge.event.level.LevelEvent.Load event) {
        if (!net.neoforged.neoforge.gametest.GameTestHooks.isGametestEnabled()
                || !(event.getLevel() instanceof net.minecraft.server.level.ServerLevel level)) return;
        var original = level.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_highpower", TEMPLATE));
        level.getStructureManager().getOrCreate(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy_control_hold_runtime", TEMPLATE))
                .load(level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.BLOCK), original.save(new CompoundTag()));
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void full_cp_hold_and_release_never_create_regeneration_room_or_refund(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER);
        double maximum = HighPowerGrowth.values(p).cpMaximum(); Object owner = new Object();
        CompoundTag before = data(p).copy(); ControlHold hold = f.hold(p, owner, 1, 7);
        h.assertTrue(before.equals(data(p)), "acquiring a hold has no persistent write");
        same(h, HighPowerResources.cp(p), maximum, "held fee remains inside total CP");
        same(h, HighPowerResources.availableCp(p), maximum - 7, "only spendable CP excludes hold");
        h.assertTrue(HighPowerResources.tick(p), "ordinary native regeneration tick still runs");
        same(h, HighPowerResources.cp(p), maximum, "full total CP has no artificial regeneration room");
        h.assertTrue(hold.valid(owner, 1), "ordinary regeneration preserves exact hold expectation");
        CompoundTag afterTick = data(p).copy(); hold.close(); hold.close();
        h.assertTrue(afterTick.equals(data(p)), "repeated release never writes a refund, clock, or field");
        same(h, HighPowerResources.availableCp(p), maximum, "release only removes the spend restriction");
        h.assertTrue(hold.state() == ControlHold.State.TERMINAL, "release is terminal");
        ServerPlayer deficit = f.player(HighPowerCategories.MELTDOWNER); f.cp(deficit, 20);
        Object deficitOwner = new Object(); ControlHold deficitHold = f.hold(deficit, deficitOwner, 2, 7);
        double recovered = 20 + HighPowerGrowth.values(deficit).cpRecovery() / 20;
        h.assertTrue(HighPowerResources.tick(deficit) && deficitHold.valid(deficitOwner, 2), "ordinary recovery below maximum retains the claim");
        same(h, HighPowerResources.cp(deficit), recovered, "hold does not alter ordinary recovery amount");
        same(h, HighPowerResources.availableCp(deficit), recovered - 7, "recovered funds become available except for the same frozen fee");
        deficitHold.close(); same(h, HighPowerResources.cp(deficit), recovered, "release cannot add a second recovery/refund budget");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void unrelated_spending_can_use_exact_unheld_funds_without_consuming_fee(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER);
        f.cp(p, 20); Object owner = new Object(); ControlHold hold = f.hold(p, owner, 1, 7);
        h.assertTrue(HighPowerResources.trySpend(p, HighPowerSkills.DUAL_POINT, 13, 1), "ordinary shared writer spends exact unheld funds");
        same(h, HighPowerResources.cp(p), 7, "one ordinary debit changes total exactly once");
        same(h, HighPowerResources.availableCp(p), 0, "held fee is unavailable to other skills");
        h.assertTrue(hold.valid(owner, 1), "legitimate unrelated payment advances the pinned ledger expectation");
        CompoundTag after = data(p).copy();
        h.assertFalse(HighPowerResources.trySpend(p, HighPowerSkills.THERMAL_SHELL, .01, 1), "another central writer cannot borrow held CP");
        h.assertTrue(after.equals(data(p)), "refused spending has no ledger or clock write");
        hold.close(); same(h, HighPowerResources.cp(p), 7, "release never restores spent CP");
        same(h, HighPowerResources.availableCp(p), 7, "released remainder becomes available");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void combined_exact_balance_settles_once_and_rejects_owner_generation_replays(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER);
        Object owner = new Object(); double fee = .1, shot = .2; f.cp(p, fee + shot);
        ControlHold hold = f.hold(p, owner, 9, fee); long now = p.serverLevel().getGameTime();
        h.assertFalse(claim(hold, new Object(), 9) || claim(hold, owner, 8), "foreign owner and stale generation cannot claim");
        h.assertTrue(claim(hold, owner, 9), "exact owner/session claims once");
        h.assertFalse(claim(hold, owner, 9), "CLAIMED cannot be claimed twice");
        h.assertFalse(settle(p, hold, new Object(), 9, shot, 4) || settle(p, hold, owner, 8, shot, 4), "foreign owner and generation cannot settle an existing claim");
        h.assertTrue(hold.state() == ControlHold.State.CLAIMED, "foreign settlement cannot release the legitimate claim");
        h.assertTrue(settle(p, hold, owner, 9, shot, 4), "combined arithmetic accepts the actual stored binary-sum balance");
        same(h, HighPowerResources.cp(p), 4e-17, "raw binary-sum balance retains its real canonical-decimal excess after one combined debit");
        same(h, HighPowerResources.heldAmount(p), 0, "claim is retired before successful settlement returns");
        h.assertTrue(hold.state() == ControlHold.State.TERMINAL, "settled capability is terminal");
        h.assertTrue(data(p).getLong("next_" + HighPowerSkills.FOLDED_MIRROR_ARRAY) == now + 4
                && AbilityStorage.get(p).cooldowns.get(HighPowerSkills.FOLDED_MIRROR_ARRAY) == 4, "ordinary NEW221 clocks are committed once");
        CompoundTag paid = data(p).copy();
        h.assertFalse(settle(p, hold, owner, 9, shot, 4) || claim(hold, owner, 9), "repeat settle and claim fail");
        hold.close(); h.assertTrue(paid.equals(data(p)), "repeat settlement and close cannot debit or refund");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void refused_combined_payment_keeps_fee_held_without_starting_a_clock(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER);
        Object owner = new Object(); f.cp(p, 11); ControlHold hold = f.hold(p, owner, 1, 4);
        CompoundTag before = data(p).copy(); h.assertTrue(claim(hold, owner, 1), "claim fixture");
        h.assertFalse(settle(p, hold, owner, 1, 8, 20), "shot cannot use less than fee plus shot funds");
        h.assertTrue(hold.state() == ControlHold.State.HELD && hold.valid(owner, 1), "free refusal restores live HELD state");
        h.assertTrue(before.equals(data(p)), "free refusal changes no CP or persistent cooldown");
        same(h, HighPowerResources.availableCp(p), 7, "refused payment retains original restriction");
        h.assertTrue(AbilityStorage.get(p).cooldowns.getOrDefault(HighPowerSkills.FOLDED_MIRROR_ARRAY, 0) == 0, "free refusal starts no UI cooldown");
        hold.close(); same(h, HighPowerResources.cp(p), 11, "release after refusal has no positive writer");
        ControlHold newer = f.hold(p, owner, 2, 4); hold.close();
        h.assertTrue(newer.valid(owner, 2) && !claim(hold, owner, 1), "old release/replay cannot touch a newer session's hold");
        same(h, HighPowerResources.heldAmount(p), 4, "new session retains its own reserved amount"); newer.close();
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void harvest_and_control_holds_share_one_budget_and_cannot_settle_together(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER);
        Object owner = new Object(); f.cp(p, 20); ControlHold hold = f.hold(p, owner, 1, 4);
        try (var harvest = HighPowerResources.reserve(p, HighPowerSkills.BEAM_ETCHING, 3)) {
            h.assertTrue(harvest != null && harvest.valid(), "ordinary harvest reserves only unheld funds");
            same(h, HighPowerResources.availableCp(p), 13, "both transient claims subtract from availability");
            h.assertTrue(claim(hold, owner, 1), "control claim before collision");
            h.assertFalse(settle(p, hold, owner, 1, 8, 20), "combined control settlement refuses an active harvest");
            h.assertTrue(hold.state() == ControlHold.State.HELD && harvest.valid(), "refusal preserves both original claims");
            same(h, HighPowerResources.cp(p), 20, "colliding claims never debit");
            // Foundation fixture for the already approved native removal payment; world-harvest tests own removal proof.
            invoke(harvest, "settleRemoved", new Class<?>[0]);
            same(h, HighPowerResources.cp(p), 17, "existing harvest settles only its own exact cost");
            h.assertTrue(hold.valid(owner, 1), "legitimate harvest debit advances held fee expectation");
            same(h, HighPowerResources.availableCp(p), 13, "settled harvest is not subtracted twice");
        }
        h.assertTrue(claim(hold, owner, 1) && settle(p, hold, owner, 1, 8, 20), "later no-harvest scope settles the combined control fee");
        same(h, HighPowerResources.cp(p), 5, "harvest, fee and shot total exactly fifteen");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void corrupt_or_replaced_cp_ledger_fails_closed_without_snapshot_restore(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (int scenario = 0; scenario < 4; scenario++) {
            ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER); Object owner = new Object(); f.cp(p, 20);
            ControlHold hold = f.hold(p, owner, 1, 4);
            if (scenario == 0) data(p).putDouble("cp", 19);
            else if (scenario == 1) p.getPersistentData().put(HighPowerResources.DATA_KEY, data(p).copy());
            else if (scenario == 2) data(p).putInt("cp", 20);
            else data(p).putDouble("cp", Double.NaN);
            CompoundTag corrupt = data(p); long corruptBits = Double.doubleToLongBits(corrupt.getDouble("cp"));
            CompoundTag otherFields = corrupt.copy(); otherFields.remove("cp");
            h.assertFalse(hold.valid(owner, 1) || claim(hold, owner, 1), "changed raw ledger identity/type/value invalidates capability " + scenario);
            h.assertFalse(HighPowerResources.trySpend(p, HighPowerSkills.DUAL_POINT, 1, 1), "central spending fails closed " + scenario);
            h.assertTrue(HighPowerResources.reserve(p, HighPowerSkills.BEAM_ETCHING, 1) == null, "new harvest cannot adopt corrupt control ledger " + scenario);
            h.assertFalse(HighPowerResources.tick(p), "regeneration cannot legitimize corrupt ledger during hold " + scenario);
            hold.close(); hold.close();
            CompoundTag remainingFields = data(p).copy(); remainingFields.remove("cp");
            h.assertTrue(corrupt == data(p) && corruptBits == Double.doubleToLongBits(data(p).getDouble("cp"))
                    && remainingFields.equals(otherFields), "release never restores a stale CP snapshot " + scenario);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void earned_capacity_change_invalidates_frozen_hold_without_refill(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER);
        Object owner = new Object(); f.cp(p, 20); ControlHold hold = f.hold(p, owner, 1, 4);
        double maximum = HighPowerGrowth.values(p).cpMaximum();
        AbilityStorage.get(p).addExperience(HighPowerSkills.DUAL_POINT, .005);
        h.assertTrue(HighPowerGrowth.values(p).cpMaximum() > maximum, "genuine native XP changes certified capacity");
        h.assertFalse(hold.valid(owner, 1) || claim(hold, owner, 1), "capacity change cannot reuse frozen fee claim");
        same(h, HighPowerResources.cp(p), 20, "capacity growth itself gives no CP");
        hold.close(); same(h, HighPowerResources.cp(p), 20, "capacity-change cleanup never refunds");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void em_arc_and_defense_respect_hold_without_changing_original_fees(GameTestHelper h) {
        Fixture f = new Fixture(h);
        ServerPlayer blocked = f.player(HighPowerSkills.CATEGORY); f.cp(blocked, 20);
        ControlHold blockedHold = f.hold(blocked, new Object(), 1, 18);
        h.assertFalse(ElectromasterPrototype.cast(blocked), "original direct arc cannot borrow held funds");
        same(h, HighPowerResources.cp(blocked), 20, "blocked original arc has no debit"); blockedHold.close();
        ServerPlayer exact = f.player(HighPowerSkills.CATEGORY); f.cp(exact, 20); Object owner = new Object();
        // The shared empty structure is underground. A ray through its ceiling earns native XP,
        // correctly changing Q and invalidating a frozen fee. This ledger-tracking case needs a
        // verified miss so that it tests the debit separately from earned-capacity retirement.
        var underground = arcClip(exact);
        System.out.println("Control-hold arc fixture original ray: " + underground.getType() + " at " + underground.getLocation());
        exact.moveTo(exact.getX(), exact.serverLevel().getMaxBuildHeight() - 30, exact.getZ(), 0, -90);
        h.assertTrue(arcClip(exact).getType() == net.minecraft.world.phys.HitResult.Type.MISS, "declared open-sky arc fixture has no XP-bearing block contact");
        var earnedBefore = HighPowerGrowth.values(exact).earnedPosition(); double xpBefore = AbilityStorage.get(exact).levelExperience;
        ControlHold arcHold = f.hold(exact, owner, 1, 15); double arc = HighPowerGrowth.values(exact).arcCost();
        h.assertTrue(arc == 5 && ElectromasterPrototype.cast(exact), "original arc can spend exact unheld five CP");
        same(h, HighPowerResources.cp(exact), 15, "original arc fee is unchanged");
        h.assertTrue(HighPowerGrowth.values(exact).earnedPosition().compareTo(earnedBefore) == 0
                && AbilityStorage.get(exact).levelExperience == xpBefore, "verified miss leaves earned capacity and XP unchanged");
        h.assertTrue(arcHold.valid(owner, 1), "arc writer participates in hold ledger tracking"); arcHold.close();
        ServerPlayer defense = f.player(HighPowerSkills.CATEGORY); Object defenseOwner = new Object();
        double maximum = HighPowerGrowth.values(defense).cpMaximum(), upkeep = HighPowerGrowth.values(defense).arcCost() / 20;
        ControlHold defenseHold = f.hold(defense, defenseOwner, 1, maximum - upkeep);
        f.defending(defense); ElectromasterPrototype.tick(new PlayerTickEvent.Post(defense));
        same(h, HighPowerResources.cp(defense), maximum - upkeep, "original defense spends exact unheld upkeep after unchanged full-total recovery");
        h.assertTrue(defenseHold.valid(defenseOwner, 1), "defense writer participates in hold ledger tracking"); defenseHold.close();
        ServerPlayer blockedDefense = f.player(HighPowerSkills.CATEGORY);
        ControlHold blockedDefenseHold = f.hold(blockedDefense, new Object(), 1, maximum - upkeep / 2);
        f.defending(blockedDefense); ElectromasterPrototype.tick(new PlayerTickEvent.Post(blockedDefense));
        same(h, HighPowerResources.cp(blockedDefense), maximum, "defense cannot borrow held upkeep"); blockedDefenseHold.close();
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void original_underground_arc_earns_capacity_and_invalidates_frozen_hold(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerSkills.CATEGORY); f.cp(p, 20);
        var contact = arcClip(p);
        h.assertTrue(contact.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK,
                "preserved original underground fixture contacts a real block: " + contact);
        Object owner = new Object(); ControlHold hold = f.hold(p, owner, 1, 15);
        var before = HighPowerGrowth.values(p).earnedPosition(); double xpBefore = AbilityStorage.get(p).levelExperience;
        h.assertTrue(ElectromasterPrototype.cast(p), "preserved original arc pays for the native block contact");
        same(h, HighPowerResources.cp(p), 15, "block-contact arc retains its original five CP debit");
        var after = HighPowerGrowth.values(p).earnedPosition(); double xpAfter = AbilityStorage.get(p).levelExperience;
        System.out.println("Control-hold original arc capacity: Q " + before + " -> " + after + "; XP " + xpBefore + " -> " + xpAfter);
        h.assertTrue(after.compareTo(before) > 0 && xpAfter > xpBefore, "real block-contact XP raises capacity before hold validation");
        h.assertFalse(hold.valid(owner, 1) || claim(hold, owner, 1), "earned capacity change intentionally retires use of frozen fee");
        CompoundTag afterArc = data(p).copy(); hold.close();
        h.assertTrue(afterArc.equals(data(p)), "growth-invalidated hold cleanup never restores old CP");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void q4_fractional_unheld_spend_preserves_exact_frozen_fee(GameTestHelper h) {
        fractionalBoundary(h, 4, 1.2, .2, 1);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void q5_fractional_unheld_spend_preserves_exact_frozen_fee(GameTestHelper h) {
        fractionalBoundary(h, 5, .22, .02, .2);
    }

    private static void fractionalBoundary(GameTestHelper h, int level, double total, double fee, double spend) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER, level); f.cp(p, total);
        Object owner = new Object(); ControlHold hold = f.hold(p, owner, 1, fee);
        var before = HighPowerGrowth.values(p).earnedPosition(); double xp = AbilityStorage.get(p).levelExperience;
        h.assertTrue(HighPowerResources.trySpend(p, HighPowerSkills.DUAL_POINT, spend, 1), "exact unheld decimal funds admit ordinary payment at q" + level);
        h.assertTrue(before.compareTo(HighPowerGrowth.values(p).earnedPosition()) == 0 && xp == AbilityStorage.get(p).levelExperience,
                "resource-only payment cannot change q/Q or XP at q" + level);
        same(h, HighPowerResources.cp(p), fee, "ordinary payment leaves exactly the frozen fee at q" + level);
        h.assertTrue(hold.valid(owner, 1), "exact unheld spend cannot invalidate its untouched frozen fee at q" + level);
        same(h, HighPowerResources.availableCp(p), 0, "exact spend leaves no free CP"); hold.close();
        same(h, HighPowerResources.cp(p), fee, "release adds no compensating CP");
        ServerPlayer below = f.player(HighPowerCategories.MELTDOWNER, level); double insufficient = Math.nextDown(total); f.cp(below, insufficient);
        Object belowOwner = new Object(); ControlHold belowHold = f.hold(below, belowOwner, 1, fee);
        h.assertFalse(HighPowerResources.trySpend(below, HighPowerSkills.DUAL_POINT, spend, 1), "one actual double below exact funds is insufficient");
        same(h, HighPowerResources.cp(below), insufficient, "boundary refusal changes no CP");
        h.assertTrue(belowHold.valid(belowOwner, 1), "boundary refusal retains the fee"); belowHold.close();
        ServerPlayer legacy = f.player(HighPowerCategories.MELTDOWNER, level); f.cp(legacy, total);
        h.assertTrue(HighPowerResources.trySpend(legacy, HighPowerSkills.DUAL_POINT, spend, 1), "no-hold legacy debit remains admitted");
        same(h, HighPowerResources.cp(legacy), total - spend, "no-hold path retains its original binary subtraction exactly");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void fractional_combined_payment_uses_same_exact_decimal_boundary(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER, 5); f.cp(p, .3);
        Object owner = new Object(); ControlHold hold = f.hold(p, owner, 1, .1);
        h.assertTrue(claim(hold, owner, 1) && settle(p, hold, owner, 1, .2, 1), "decimal .3 exactly covers held .1 plus shot .2");
        same(h, HighPowerResources.cp(p), 0, "exact decimal combined fee leaves zero");
        ServerPlayer below = f.player(HighPowerCategories.MELTDOWNER, 5); double insufficient = Math.nextDown(.3); f.cp(below, insufficient);
        Object belowOwner = new Object(); ControlHold belowHold = f.hold(below, belowOwner, 1, .1);
        h.assertTrue(claim(belowHold, belowOwner, 1), "below-boundary claim fixture");
        h.assertFalse(settle(below, belowHold, belowOwner, 1, .2, 1), "combined fee refuses the true adjacent insufficient double");
        same(h, HighPowerResources.cp(below), insufficient, "combined refusal cannot round up or debit");
        h.assertTrue(belowHold.state() == ControlHold.State.HELD, "refused boundary claim returns to HELD");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void rounded_available_view_never_admits_an_exact_decimal_shortfall(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER, 5); f.cp(p, 1.2);
        Object owner = new Object(); ControlHold hold = f.hold(p, owner, 1, Math.nextUp(.2));
        same(h, HighPowerResources.availableCp(p), 1, "public double view necessarily rounds .99999999999999996 to one");
        h.assertFalse(HighPowerResources.trySpend(p, HighPowerSkills.DUAL_POINT, 1, 1), "exact admission cannot use rounded view as authorization");
        same(h, HighPowerResources.cp(p), 1.2, "true fractional shortfall remains free");
        h.assertTrue(hold.valid(owner, 1), "refused exact shortfall preserves capability"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void fractional_harvest_and_unrelated_spend_preserve_both_reserved_floors(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER, 5); f.cp(p, .42);
        Object owner = new Object(); ControlHold hold = f.hold(p, owner, 1, .02);
        try (var harvest = HighPowerResources.reserve(p, HighPowerSkills.BEAM_ETCHING, .2)) {
            h.assertTrue(harvest != null, "fractional harvest claims only spendable CP");
            same(h, HighPowerResources.availableCp(p), .2, "canonical availability subtracts both claims before rounding once");
            h.assertTrue(HighPowerResources.trySpend(p, HighPowerSkills.DUAL_POINT, .2, 1), "ordinary debit uses exact unreserved remainder");
            same(h, HighPowerResources.cp(p), .22, "ordinary debit retains both fractional claims");
            h.assertTrue(hold.valid(owner, 1) && harvest.valid(), "both claim identities survive legitimate debit");
            invoke(harvest, "settleRemoved", new Class<?>[0]);
            same(h, HighPowerResources.cp(p), .02, "harvest commits exactly its share with control hold active");
            h.assertTrue(hold.valid(owner, 1), "harvest cannot undercut frozen control fee by binary rounding");
        }
        hold.close(); same(h, HighPowerResources.cp(p), .02, "both releases have no compensating credit");
        ServerPlayer legacy = f.player(HighPowerCategories.MELTDOWNER, 5); f.cp(legacy, .22);
        try (var harvest = HighPowerResources.reserve(legacy, HighPowerSkills.BEAM_ETCHING, .2)) {
            h.assertTrue(harvest != null, "legacy harvest-only fixture"); invoke(harvest, "settleRemoved", new Class<?>[0]);
            same(h, HighPowerResources.cp(legacy), .22 - .2, "no-control-hold harvest preserves original binary subtraction");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void fractional_em_arc_and_defense_keep_the_exact_held_remainder(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer arc = f.player(HighPowerSkills.CATEGORY, 5); f.cp(arc, .12);
        arc.moveTo(arc.getX(), arc.serverLevel().getMaxBuildHeight() - 30, arc.getZ(), 0, -90);
        h.assertTrue(arcClip(arc).getType() == net.minecraft.world.phys.HitResult.Type.MISS, "fractional arc fixture is a verified no-XP miss");
        Object arcOwner = new Object(); ControlHold arcHold = f.hold(arc, arcOwner, 1, .02);
        h.assertTrue(ElectromasterPrototype.cast(arc), "native original q5 arc spends exact available .1");
        same(h, HighPowerResources.cp(arc), .02, "EM arc subtraction retains exact held .02");
        h.assertTrue(arcHold.valid(arcOwner, 1), "EM arc leaves unchanged-capacity hold valid");
        ServerPlayer defense = f.player(HighPowerSkills.CATEGORY, 5); f.cp(defense, .025);
        Object defenseOwner = new Object(); ControlHold defenseHold = f.hold(defense, defenseOwner, 1, .02);
        double upkeep = HighPowerGrowth.values(defense).arcCost() / 20;
        h.assertTrue((boolean) invoke(null, "trySpendPrototypeDefense", new Class<?>[]{ServerPlayer.class, double.class}, defense, upkeep), "declared defense payment seam uses original exact upkeep without a regeneration tick");
        same(h, HighPowerResources.cp(defense), .02, "EM defense subtraction retains exact held .02");
        h.assertTrue(defenseHold.valid(defenseOwner, 1), "EM defense advances expected CP without changing fee");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void unrepresentable_dual_claim_remainder_refuses_before_any_debit(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER);
        // Arithmetic-derived fixture: these are actual native curve samples at q=2.04;
        // this resource-only test does not claim that the fixture earned that coordinate.
        var q = new java.math.BigDecimal("2.04");
        double fee = FoldedMirrorRules.placementCost(q);
        double harvestCost = cn.academy.port.highpower.core.BeamEtchingRules.cost(q);
        double ordinaryCost = cn.academy.port.highpower.core.ThermalShellRules.activationCost(q);
        same(h, fee, 1.9965029945305472, "native q2.04 control fee fixture");
        same(h, harvestCost, .48440773816023697, "native q2.04 harvest cost fixture");
        double total = 4.4774137272213315; f.cp(p, total); Object owner = new Object(); ControlHold hold = f.hold(p, owner, 1, fee);
        try (var harvest = HighPowerResources.reserve(p, HighPowerSkills.BEAM_ETCHING, harvestCost)) {
            h.assertTrue(harvest != null, "both native sampled fees can be reserved in the existing balance");
            var exactRemaining = java.math.BigDecimal.valueOf(total).subtract(java.math.BigDecimal.valueOf(ordinaryCost));
            var protectedFloor = java.math.BigDecimal.valueOf(fee).add(java.math.BigDecimal.valueOf(harvestCost));
            h.assertTrue(exactRemaining.compareTo(protectedFloor) > 0
                    && java.math.BigDecimal.valueOf(exactRemaining.doubleValue()).compareTo(protectedFloor) < 0,
                    "fixture proves an affordable exact remainder whose double representation undercuts the two live claims");
            CompoundTag before = data(p).copy();
            h.assertFalse(HighPowerResources.trySpend(p, HighPowerSkills.THERMAL_SHELL, ordinaryCost, 1), "unrepresentable protected remainder is refused before debit");
            h.assertTrue(before.equals(data(p)) && harvest.valid() && hold.valid(owner, 1), "refusal preserves CP, clocks, and both reservations without rounding credit");
        }
        hold.close(); same(h, HighPowerResources.cp(p), total, "releasing both refused claims cannot change total CP");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void decimal_debit_storage_never_rounds_the_remaining_cp_upward(GameTestHelper h) {
        Fixture f = new Fixture(h); double total = 1.2, cost = Math.nextUp(.1);
        var exact = java.math.BigDecimal.valueOf(total).subtract(java.math.BigDecimal.valueOf(cost));
        h.assertTrue(java.math.BigDecimal.valueOf(exact.doubleValue()).compareTo(exact) > 0, "fixture nearest double rounds above the exact decimal remainder");
        double expected = Math.nextDown(exact.doubleValue());
        ServerPlayer ordinary = f.player(HighPowerCategories.MELTDOWNER); f.cp(ordinary, total);
        Object owner = new Object(); ControlHold hold = f.hold(ordinary, owner, 1, .2);
        h.assertTrue(HighPowerResources.trySpend(ordinary, HighPowerSkills.DUAL_POINT, cost, 1), "representable conservative remainder still funds the hold");
        same(h, HighPowerResources.cp(ordinary), expected, "held ordinary debit stores no upward decimal rounding surplus");
        h.assertTrue(hold.valid(owner, 1), "conservatively rounded remainder preserves hold");
        ServerPlayer combined = f.player(HighPowerCategories.MELTDOWNER); f.cp(combined, total);
        Object combinedOwner = new Object(); ControlHold combinedHold = f.hold(combined, combinedOwner, 1, .05);
        h.assertTrue(claim(combinedHold, combinedOwner, 1) && settle(combined, combinedHold, combinedOwner, 1, .05000000000000002, 1), "combined fee fixture has the same exact decimal total debit");
        same(h, HighPowerResources.cp(combined), expected, "combined settlement also stores no upward rounding surplus");
        ServerPlayer mined = f.player(HighPowerCategories.MELTDOWNER); f.cp(mined, total);
        Object minedOwner = new Object(); ControlHold minedHold = f.hold(mined, minedOwner, 1, .2);
        try (var harvest = HighPowerResources.reserve(mined, HighPowerSkills.BEAM_ETCHING, cost)) {
            h.assertTrue(harvest != null, "fractional harvest fixture"); invoke(harvest, "settleRemoved", new Class<?>[0]);
            same(h, HighPowerResources.cp(mined), expected, "held harvest settlement stores no upward rounding surplus");
            h.assertTrue(minedHold.valid(minedOwner, 1), "conservative harvest payment preserves frozen fee");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void no_hold_original_payments_and_regeneration_keep_exact_behavior(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(HighPowerCategories.MELTDOWNER); f.cp(p, 37);
        var growth = HighPowerGrowth.values(p); double placement = FoldedMirrorRules.placementCost(growth.position());
        h.assertTrue(HighPowerResources.trySpend(p, HighPowerSkills.FOLDED_MIRROR_ARRAY, placement, 1), "original NEW221 placement remains paid");
        same(h, HighPowerResources.cp(p), 37 - placement, "original NEW221 placement fee unchanged");
        h.assertTrue(HighPowerResources.tick(p), "ordinary shared recovery remains active");
        same(h, HighPowerResources.cp(p), 37 - placement + growth.cpRecovery() / 20, "original recovery remains total-based");
        h.assertFalse(HighPowerResources.tick(p), "same-tick recovery remains single use");
        ServerPlayer em = f.player(HighPowerSkills.CATEGORY); f.cp(em, 37);
        h.assertTrue(ElectromasterPrototype.trySpend(em, HighPowerSkills.GROUNDING_RETURN, 0, 60), "old intentional zero CP cost remains admitted");
        same(h, HighPowerResources.cp(em), 37, "zero CP fee starts only its existing clock");
        ServerPlayer arc = f.player(HighPowerSkills.CATEGORY); f.cp(arc, 37);
        h.assertTrue(ElectromasterPrototype.cast(arc), "ordinary unheld original arc remains admitted");
        same(h, HighPowerResources.cp(arc), 32, "ordinary unheld original arc subtracts exact original fee");
        h.assertFalse(ElectromasterPrototype.cast(arc), "original persistent arc cooldown still rejects replay");
        h.succeed();
    }

    private static CompoundTag data(ServerPlayer p) { return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); }
    private static net.minecraft.world.phys.BlockHitResult arcClip(ServerPlayer p) {
        return p.serverLevel().clip(new net.minecraft.world.level.ClipContext(p.getEyePosition(), p.getEyePosition().add(p.getLookAngle().scale(24)),
                net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.NONE, p));
    }
    private static void same(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Double.doubleToLongBits(actual) == Double.doubleToLongBits(expected), message + ": " + actual + " != " + expected);
    }
    private static boolean claim(ControlHold hold, Object owner, long generation) {
        return (boolean) invoke(hold, "claim", new Class<?>[]{Object.class, long.class}, owner, generation);
    }
    private static boolean settle(ServerPlayer p, ControlHold hold, Object owner, long generation, double shot, int cooldown) {
        return (boolean) invoke(null, "trySpendControlledMirror", new Class<?>[]{ServerPlayer.class, ControlHold.class, Object.class, long.class, double.class, int.class}, p, hold, owner, generation, shot, cooldown);
    }
    private static Object invoke(Object receiver, String name, Class<?>[] types, Object... arguments) {
        try {
            var method = (receiver == null ? HighPowerResources.class : receiver.getClass()).getDeclaredMethod(name, types);
            method.setAccessible(true); return method.invoke(receiver, arguments);
        } catch (InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }
    private static final class Fixture {
        final GameTestHelper h;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<ControlHold> holds = new ArrayList<>();
        boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        ServerPlayer player(String category) { return player(category, 2); }
        ServerPlayer player(String category, int level) {
            var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-Control-CP]")); players.add(p);
            p.setNoGravity(true); p.getAbilities().instabuild = false;
            Vec3 at = h.absoluteVec(new Vec3(3, 5, 3)); p.moveTo(at.x, at.y, at.z, 0, -80); p.setYHeadRot(0);
            var ability = AbilityStorage.get(p); ability.changeCategoryClassic(category); ability.setLevel(level);
            for (var skill : HighPowerSkills.ALL) if (skill.category().equals(category) && skill.level() <= level) ability.learn(skill.id());
            ability.setActivateState(true); ability.overloadFine = true;
            h.assertTrue(HighPowerCombat.enroll(p, level), "declared native enrollment: " + HighPowerCombat.unsupportedReason(p));
            HighPowerResources.initializeBudget(p); return p;
        }
        void cp(ServerPlayer p, double amount) { data(p).putDouble("cp", amount); }
        ControlHold hold(ServerPlayer p, Object owner, long generation, double amount) {
            ControlHold hold = (ControlHold) invoke(null, "holdControlFee", new Class<?>[]{ServerPlayer.class, Object.class, long.class, double.class}, p, owner, generation, amount);
            h.assertTrue(hold != null, "declared control hold fixture admitted"); holds.add(hold); return hold;
        }
        void defending(ServerPlayer p) {
            p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ElectromasterPrototype.CONTROL.get()));
            p.setShiftKeyDown(true); p.startUsingItem(InteractionHand.MAIN_HAND);
        }
        void close() {
            if (closed) return; closed = true; holds.forEach(ControlHold::close);
            for (ServerPlayer p : players) { HighPowerGrowth.invalidate(p); AbilityStorage.remove(p); p.discard(); }
        }
    }
}
