package cn.academy.port.gametest;

import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.MagneticDomainReserve;
import cn.academy.port.highpower.ScatterPlateStorage;
import cn.academy.port.highpower.ZeroGapArmorStorage;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.HighPowerLevel;
import cn.academy.port.highpower.core.ScatterPlateShares;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
import cn.academy.port.highpower.core.ZeroGapArmorShares;
import cn.academy.port.highpower.core.ZeroGapArmorTiming;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Native NBT/thread fixtures for the STANDALONE NEW252 foundation. These tests
 * call its draft API directly; no live defense, input, old-writer or event
 * integration is claimed. Corruption/reentry probes are private reflection in
 * this test class, never commands, packets or production mutation callbacks.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerZeroGapArmorFoundationTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_zero_gap_foundation";
    private HighPowerZeroGapArmorFoundationTests() {}

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void explicit_migration_preserves_depleted_independent_parents(GameTestHelper h) {
        Fixture f = new Fixture(h);
        CompoundTag before = f.tag().copy();
        h.assertTrue(ZeroGapArmorStorage.read(f.player).isEmpty(), "read cannot initialize missing NEW252");
        h.assertTrue(f.tag().equals(before), "read leaves the complete native compound untouched");
        var captured = f.capture();
        var prepared = ZeroGapArmorStorage.prepareInitialization(captured);
        h.assertTrue(f.tag().equals(before), "preparation does not mutate live NBT");
        h.assertTrue(ZeroGapArmorStorage.apply(prepared), "explicit one-time migration applies");
        var after = f.armor();
        sameParents(h, after, captured, "migration");
        eq(h, after.plate(0).baseCurrent(), "0", "depleted B0 stays depleted");
        eq(h, after.plate(1).baseCurrent(), "0", "depleted B0's second child stays depleted");
        eq(h, after.plate(2).baseCurrent(), "12", "B1 divides its current only");
        eq(h, after.plate(4).baseCurrent(), "18", "different B2 current keeps its identity");
        eq(h, after.total(), "96", "all children conserve existing base plus reserve");
        h.assertTrue(f.capture().timing().orElseThrow().equals(ZeroGapArmorTiming.INITIAL), "first migration initializes timing once");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void retoggle_relearning_and_native_save_load_do_not_refill(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize();
        f.commit(f.armor().contact(2, n("17"), n("12"), n("5")).transaction());
        var before = f.armor().toDecimalMap();
        var timing = f.capture().timing().orElseThrow();
        f.tag().putBoolean("bracerActive", true);
        f.tag().putBoolean("bracerActive", false);
        rejected(h, () -> ZeroGapArmorStorage.prepareInitialization(f.capture()), "relearning cannot initialize again");
        h.assertTrue(f.armor().toDecimalMap().equals(before), "retoggle leaves all twelve exact currents alone");
        CompoundTag saved = new CompoundTag();
        f.player.saveWithoutId(saved); f.player.load(saved);
        h.assertTrue(f.armor().toDecimalMap().equals(before), "native save/load retains depleted children");
        h.assertTrue(f.capture().timing().orElseThrow().equals(timing), "native save/load preserves phase and break debt");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void missing_corrupt_future_and_unavailable_metadata_fail_closed(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize();
        CompoundTag healthy = f.tag().copy();
        f.tag().remove(ZeroGapArmorStorage.KEY); unavailableUnchanged(h, f, "missing initialized children");
        f.replace(healthy); f.tag().getCompound(ZeroGapArmorStorage.KEY).putString("reserve5", "NaN");
        unavailableUnchanged(h, f, "malformed last child");
        f.replace(healthy); f.tag().getCompound(ZeroGapArmorStorage.KEY).putString("version", "999");
        unavailableUnchanged(h, f, "future children schema");
        f.replace(healthy); f.tag().remove(ZeroGapArmorStorage.TIMING_KEY);
        unavailableUnchanged(h, f, "missing initialized timing");
        f.replace(healthy); f.tag().getCompound(ZeroGapArmorStorage.TIMING_KEY).putString("breakTicks", "-1");
        unavailableUnchanged(h, f, "corrupt physical debt");
        f.replace(healthy); f.tag().getCompound(ZeroGapArmorStorage.TIMING_KEY).putString("version", "999");
        unavailableUnchanged(h, f, "future timing schema");
        f.replace(healthy); f.tag().putBoolean(ZeroGapArmorStorage.INVALIDATED_KEY, false);
        unavailableUnchanged(h, f, "presence of unavailable flag is final even when false");
        f.replace(healthy); f.tag().putBoolean(ZeroGapArmorStorage.INITIALIZED_KEY, false);
        unavailableUnchanged(h, f, "false initialized marker cannot cause reenrollment");
        f.replace(healthy); f.tag().putString(ZeroGapArmorStorage.INITIALIZED_KEY, "true");
        unavailableUnchanged(h, f, "wrong marker tag type");
        f.replace(healthy); f.tag().getCompound(ScatterPlateStorage.KEY).putString("plate2", "37");
        unavailableUnchanged(h, f, "mismatched authoritative B_i");
        f.replace(healthy); f.tag().getCompound(MagneticDomainReserve.KEY).putInt("version", 999);
        unavailableUnchanged(h, f, "future canonical reserve");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void absent_and_legacy_reserve_migrations_never_award_resources(GameTestHelper h) {
        Fixture f = new Fixture(h); f.tag().remove(MagneticDomainReserve.KEY);
        f.initialize(); eq(h, f.armor().reserves().total(), "0", "absent reserve migrates zero");
        CompoundTag zero = f.tag().copy();
        f.tag().remove(MagneticDomainReserve.KEY);
        unavailableUnchanged(h, f, "even zero reserve missing after NEW252 initialization");
        f.replace(zero);
        CompoundTag downgraded = f.tag().getCompound(MagneticDomainReserve.KEY);
        downgraded.putInt("version", 1); downgraded.remove("lower"); downgraded.remove("upper");
        unavailableUnchanged(h, f, "post-initialization reserve downgrade cannot rerun v1 migration");
        Fixture legacy = new Fixture(h);
        CompoundTag reserve = legacy.tag().getCompound(MagneticDomainReserve.KEY);
        reserve.putInt("version", 1); reserve.remove("lower"); reserve.remove("upper");
        CompoundTag before = reserve.copy();
        var captured = legacy.capture();
        h.assertTrue(reserve.equals(before), "legacy read does not rewrite canonical reserve");
        eq(h, captured.reserves().total(), "36", "legacy logical migration preserves total");
        legacy.initialize();
        eq(h, legacy.armor().reserves().lower(), "36", "v1 follows existing lower-first split exactly once");
        eq(h, legacy.armor().reserves().upper(), "0", "legacy migration creates no upper reserve");
        h.assertTrue(legacy.tag().getCompound(MagneticDomainReserve.KEY).getInt("version") == 2, "explicit apply persists canonical halves");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void invalid_final_child_codec_leaves_entire_live_tag_unchanged(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize();
        var captured = f.capture();
        var after = f.armor().contact(2, n("3"), n("3"), n("0")).shares();
        Map<String, String> bad = new HashMap<>(after.toDecimalMap()); bad.put("reserve5", "invalid-final-child");
        CompoundTag before = f.player.getPersistentData().copy();
        try {
            var method = ZeroGapArmorStorage.class.getDeclaredMethod("stage", ZeroGapArmorStorage.Capture.class,
                    ZeroGapArmorShares.class, ZeroGapArmorTiming.class, List.class, Map.class);
            method.setAccessible(true);
            try {
                method.invoke(null, captured, after, captured.timing().orElseThrow(), List.of(), bad);
                h.fail("invalid final child unexpectedly prepared");
            } catch (InvocationTargetException expected) {
                h.assertTrue(expected.getCause() instanceof IllegalArgumentException, "final child decoder is the rejecting boundary");
            }
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        h.assertTrue(f.player.getPersistentData().equals(before), "base, B_i, reserves, markers and unrelated fields all unchanged");
        h.assertTrue(f.tag().getCompound("activeGrowth") == f.activeGrowth, "failed prepare retains unrelated nested identity");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void apply_preserves_live_identity_and_unrelated_growth_metadata(GameTestHelper h) {
        Fixture f = new Fixture(h); CompoundTag live = f.tag(); f.initialize();
        h.assertTrue(f.tag() == live && f.tag().getCompound("activeGrowth") == f.activeGrowth, "migration preserves compound identities");
        var transaction = f.armor().contact(2, n("17"), n("12"), n("5")).transaction();
        var prepared = ZeroGapArmorStorage.prepare(f.capture(), transaction);
        h.assertTrue(prepared.brokenPlateIds().equals(List.of(2)), "positive total to zero produces exact break ID");
        h.assertTrue(ZeroGapArmorStorage.apply(prepared), "complete staged contact applies");
        h.assertTrue(f.tag() == live && f.tag().getCompound("activeGrowth") == f.activeGrowth, "apply does not swap root or unrelated nested tag");
        h.assertTrue(f.activeGrowth.getString("sentinel").equals("retained") && f.tag().getInt("unrelated") == 77, "unowned fields retain contents");
        var saved = f.capture();
        h.assertTrue(saved.armor().orElseThrow().toDecimalMap().equals(prepared.after().toDecimalMap()), "all parent and child writes visible together on return");
        h.assertTrue(saved.timing().orElseThrow().breakTicks() == 4, "break debt is visible before caller notifications");
        h.assertFalse(ZeroGapArmorStorage.apply(prepared), "prepared commit cannot replay");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void stale_parent_source_and_reentrant_guard_refuse_before_writes(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize();
        var captured = f.capture();
        var transaction = f.armor().contact(2, n("1"), n("1"), n("0")).transaction();
        var prepared = ZeroGapArmorStorage.prepare(captured, transaction);
        f.tag().putString("health", "999"); CompoundTag changed = f.tag().copy();
        h.assertFalse(ZeroGapArmorStorage.apply(prepared), "intervening exact parent change rejects");
        h.assertTrue(f.tag().equals(changed), "stale apply changes no live field");
        rejected(h, () -> ZeroGapArmorStorage.prepare(captured, transaction), "stale capture rejects during prepare too");

        var current = f.capture(); var old = current.armor().orElseThrow();
        var proposal = old.reconcileParents(old.base(), old.scatter(), old.reserves());
        var replaced = ZeroGapArmorStorage.prepare(current, proposal);
        f.replace(f.tag()); CompoundTag replacement = f.tag(); changed = replacement.copy();
        h.assertFalse(ZeroGapArmorStorage.apply(replaced), "equal bytes in a replaced live root do not authenticate old source");
        h.assertTrue(f.tag() == replacement && f.tag().equals(changed), "source refusal retains replacement and contents");

        var guardedCapture = f.capture(); var guardedArmor = guardedCapture.armor().orElseThrow();
        var guarded = ZeroGapArmorStorage.prepare(guardedCapture,
                guardedArmor.reconcileParents(guardedArmor.base(), guardedArmor.scatter(), guardedArmor.reserves()));
        Set<LivingEntity> applying = applyingGuard(); applying.add(f.player); changed = f.tag().copy();
        try {
            h.assertFalse(ZeroGapArmorStorage.apply(guarded), "reentrant apply refused");
            h.assertTrue(ZeroGapArmorStorage.capture(f.player).isEmpty(), "reentrant capture unavailable");
            rejected(h, () -> ZeroGapArmorStorage.prepareNativeTick(guardedCapture), "reentrant preparation refused");
            h.assertTrue(f.tag().equals(changed), "reentrant refusal occurs before every live write");
        } finally { applying.remove(f.player); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void caller_notification_reentry_is_not_overwritten_by_outer_commit(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize();
        var outer = ZeroGapArmorStorage.prepare(f.capture(), f.armor().contact(2, n("2"), n("2"), n("0")).transaction());
        h.assertTrue(ZeroGapArmorStorage.apply(outer), "outer staged write complete");
        // This is the future caller's notification boundary, AFTER callback-free apply.
        var observed = f.capture();
        h.assertTrue(observed.armor().orElseThrow().toDecimalMap().equals(outer.after().toDecimalMap()), "notification observes complete outer state");
        var inner = ZeroGapArmorStorage.prepare(observed,
                observed.armor().orElseThrow().contact(3, n("3"), n("3"), n("0")).transaction());
        h.assertTrue(ZeroGapArmorStorage.apply(inner), "postcommit reentrant transaction can use fresh complete state");
        f.activeGrowth.putString("sentinel", "notification-progress");
        h.assertFalse(ZeroGapArmorStorage.apply(outer), "outer proposal cannot resume or replay after notification");
        h.assertTrue(f.armor().toDecimalMap().equals(inner.after().toDecimalMap()), "no outer rollback overwrites inner resource change");
        h.assertTrue(f.tag().getCompound("activeGrowth") == f.activeGrowth
                && f.activeGrowth.getString("sentinel").equals("notification-progress"), "retained live growth reference remains authoritative");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void native_owner_identity_change_invalidates_unchanged_ledger_capture(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize();
        var captured = f.capture();
        var prepared = ZeroGapArmorStorage.prepareNativeTick(captured);
        CompoundTag live = f.tag(), before = live.copy();
        f.player.setUUID(UUID.randomUUID());
        h.assertFalse(ZeroGapArmorStorage.apply(prepared), "same native entity object with changed UUID cannot use old capture");
        rejected(h, () -> ZeroGapArmorStorage.prepareNativeTick(captured), "changed owner identity rejects prepare");
        h.assertTrue(f.tag() == live && live.equals(before), "identity refusal leaves entire original ledger intact");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void final_contact_hp_residual_preserves_exact_children_and_original_break_ids(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize();
        var before = f.armor();
        var contact = before.contact(2, n("25"), n("12"), n("5"));
        var finalBase = contact.base().applyMitigatedDamage(contact.residual()).state();
        var rebound = contact.shares().reconcileParents(finalBase, contact.scatter(), contact.reserves()).after();
        var complete = new ZeroGapArmorShares.Transaction(before, rebound, contact.brokenPlateIds());
        f.commit(complete);
        eq(h, f.armor().base().health(), "992", "only final residual pays HP");
        eq(h, f.armor().base().shield(), "48", "contacted base paid exactly once");
        eq(h, f.armor().plate(2).current(), "0", "contacted child remains exhausted after HP binding");
        for (int i = 0; i < 6; i++) if (i != 2) {
            eq(h, f.armor().plate(i).baseCurrent(), before.plate(i).baseCurrent(), "HP residual leaves sibling base " + i);
            eq(h, f.armor().plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "HP residual leaves sibling reserve " + i);
        }
        h.assertTrue(complete.brokenPlateIds().equals(List.of(2)) && f.capture().timing().orElseThrow().breakTicks() == 4,
                "original contact break survives HP-only secondary math");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void explicit_212_contact_and_ordinary_base_spend_keep_different_parent_identity(GameTestHelper h) {
        Fixture contact = new Fixture(h); contact.initialize();
        Fixture ordinary = new Fixture(h); ordinary.initialize();
        var before = contact.armor();
        var hit = before.scatter().contact(2, n("6"), n("6"));
        var base = before.base().spendShield(hit.absorbed());
        contact.commit(before.reconcileParents(base, hit.shares(), before.reserves()));
        var original = ordinary.armor();
        ordinary.commit(original.reconcileParents(base, original.scatter().afterBaseSpend(base.shield()), original.reserves()));
        eq(h, contact.armor().plate(2).baseCurrent(), "12", "explicit B2 contact cannot spend B1 child");
        eq(h, contact.armor().plate(4).baseCurrent(), "12", "explicit B2 contact pays its own child once");
        eq(h, ordinary.armor().plate(2).baseCurrent(), "6", "ordinary debit retains canonical B0/B1/B2 order");
        eq(h, ordinary.armor().plate(4).baseCurrent(), "18", "ordinary debit cannot also apply contact delta");
        eq(h, contact.armor().base().shield(), ordinary.armor().base().shield(), "same actual base spend with different valid parent allocation");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void lower_only_242_and_lower_first_152_changes_preserve_reserve_parity(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize(); var before = f.armor();
        var lower = before.reserves().debit(n("8"), false);
        f.commit(before.reconcileParents(before.base(), before.scatter(), lower));
        var afterLower = f.armor();
        for (int i = 1; i < 6; i += 2)
            eq(h, afterLower.plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "NEW242 preserves upper child " + i);
        eq(h, afterLower.plate(0).reserveCurrent(), "0", "lower debit drains even child zero first");
        eq(h, afterLower.plate(2).reserveCurrent(), "2", "lower remainder touches only next even child");
        var aggregate = afterLower.reserves().debit(n("10"), true);
        f.commit(afterLower.reconcileParents(afterLower.base(), afterLower.scatter(), aggregate));
        eq(h, f.armor().reserves().lower(), "0", "NEW152 exhausts remaining lower first");
        eq(h, f.armor().reserves().upper(), "18", "only NEW152 remainder debits upper");
        eq(h, f.armor().plate(1).reserveCurrent(), "4", "upper debit starts at first odd child");
        eq(h, f.armor().plate(3).reserveCurrent(), "7", "other odd child retains exact current");
        eq(h, f.armor().base().shield(), before.base().shield(), "reserve-only callers do not change base shield");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void growth_zero_delta_and_clone_equivalent_drafts_preserve_death_policy(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize();
        f.commit(f.armor().contact(2, n("17"), n("12"), n("5")).transaction());
        var before = f.armor();
        var same = before.reconcileParents(before.base(), before.scatter(), before.reserves());
        f.commit(same);
        h.assertTrue(f.armor().toDecimalMap().equals(before.toDecimalMap()), "zero parent delta cannot refill broken child");
        var grownBase = before.base().withMaximums(before.base().maxHealth().multiply(n("2")), before.base().maxShield().multiply(n("2")));
        var growthDraft = before.reconcileParents(grownBase, before.scatter().withMaximum(grownBase.maxShield()), before.reserves());
        var growth = growthDraft.after();
        for (int i = 0; i < 6; i++) {
            eq(h, growth.plate(i).baseCurrent(), before.plate(i).baseCurrent(), "growth retains base current " + i);
            eq(h, growth.plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "growth retains reserve current " + i);
        }
        CompoundTag beforeGrowth = f.tag().copy();
        rejected(h, () -> ZeroGapArmorStorage.prepare(f.capture(), growthDraft), "native capacity staging awaits the reviewed q/Q metadata transaction");
        h.assertTrue(f.tag().equals(beforeGrowth) && ZeroGapArmorStorage.capture(f.player).isPresent(), "refused capacity change leaves certified native ledger valid");
        var nondeath = before.reconcileParents(before.base().withShieldEnabled(false), before.scatter(), before.reserves()).after();
        h.assertTrue(nondeath.toDecimalMap().equals(before.toDecimalMap()), "nondeath clone-equivalent math copies all currents");
        var deathBase = new ExactCombatState(before.base().maxHealth(), before.base().maxHealth(),
                before.base().maxShield(), before.base().maxShield(), false);
        var deathScatter = before.scatter().afterBaseRecovery(deathBase.shield());
        var death = before.reconcileParents(deathBase, deathScatter, before.reserves());
        f.commit(death);
        for (int i = 0; i < 6; i++) {
            eq(h, f.armor().plate(i).baseCurrent(), f.armor().plate(i).baseCapacity(), "existing .26 full-base death gain fills base child " + i);
            eq(h, f.armor().plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "death copies reserve without refill " + i);
        }
        h.assertTrue(f.capture().timing().orElseThrow().breakTicks() == 4, "clone-equivalent resource recovery cannot erase break debt");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void only_explicit_native_tick_drafts_advance_persisted_phase_and_break_debt(GameTestHelper h) {
        Fixture f = new Fixture(h); f.initialize();
        f.commit(f.armor().contact(2, n("17"), n("12"), n("5")).transaction());
        for (int i = 0; i < 20; i++) f.capture();
        h.assertTrue(f.capture().timing().orElseThrow().breakTicks() == 4, "observer calls never advance physical debt");
        for (int i = 0; i < 3; i++) h.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepareNativeTick(f.capture())), "declared native tick draft applied");
        var three = f.capture().timing().orElseThrow();
        h.assertTrue(three.openGap() && three.breakTicks() == 1 && three.phaseTick() == 3, "gap remains through three genuine logical boundaries");
        CompoundTag saved = new CompoundTag(); f.player.saveWithoutId(saved); f.player.load(saved);
        h.assertTrue(f.capture().timing().orElseThrow().equals(three), "native reload cannot reset or consume remaining debt");
        h.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepareNativeTick(f.capture())), "fourth boundary applies");
        h.assertFalse(f.capture().timing().orElseThrow().openGap(), "gap closes at fourth boundary");
        h.assertTrue(f.capture().timing().orElseThrow().phaseTick() == 4, "phase advances only by declared native boundaries");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=10)
    public static void server_thread_guard_rejects_off_thread_capture_and_apply(GameTestHelper h) {
        Fixture f = new Fixture(h); var prepared = ZeroGapArmorStorage.prepareInitialization(f.capture());
        CompoundTag before = f.tag().copy(); AtomicBoolean readRefused = new AtomicBoolean(), writeRefused = new AtomicBoolean();
        Thread thread = new Thread(() -> {
            readRefused.set(ZeroGapArmorStorage.capture(f.player).isEmpty());
            writeRefused.set(!ZeroGapArmorStorage.apply(prepared));
        }, "new252-foundation-off-thread-probe");
        thread.start();
        try { thread.join(1000); } catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new AssertionError(e); }
        h.assertTrue(!thread.isAlive() && readRefused.get() && writeRefused.get(), "off-thread API access refuses without touching NBT");
        h.assertTrue(f.tag().equals(before), "off-thread rejection leaves full live ledger untouched");
        h.assertTrue(ZeroGapArmorStorage.apply(prepared), "server-thread owner can still apply its untouched preparation");
        h.succeed();
    }

    private static void unavailableUnchanged(GameTestHelper h, Fixture f, String why) {
        CompoundTag before = f.tag().copy();
        h.assertTrue(ZeroGapArmorStorage.capture(f.player).isEmpty() && ZeroGapArmorStorage.read(f.player).isEmpty(), why + " refuses new path");
        h.assertTrue(f.tag().equals(before), why + " remains byte-for-byte intact");
    }

    private static void sameParents(GameTestHelper h, ZeroGapArmorShares after, ZeroGapArmorStorage.Capture before, String why) {
        h.assertTrue(after.base().equals(before.base()) && after.reserves().equals(before.reserves()), why + " preserves exact base and halves");
        h.assertTrue(after.scatter().toDecimalMap().equals(before.scatter().toDecimalMap()), why + " preserves three independent B_i parents");
    }

    private static void rejected(GameTestHelper h, Runnable action, String why) {
        boolean refused = false;
        try { action.run(); } catch (IllegalArgumentException | IllegalStateException expected) { refused = true; }
        h.assertTrue(refused, why);
    }

    @SuppressWarnings("unchecked")
    private static Set<LivingEntity> applyingGuard() {
        try {
            var field = ZeroGapArmorStorage.class.getDeclaredField("APPLYING"); field.setAccessible(true);
            return (Set<LivingEntity>) field.get(null);
        } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void eq(GameTestHelper h, BigDecimal actual, String expected, String why) { eq(h, actual, n(expected), why); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }

    /** Declared native NBT fixture: injured base, depleted B0, unequal B1/B2 and L/U. */
    private static final class Fixture {
        final GameTestHelper helper;
        final ServerPlayer player;
        final CompoundTag activeGrowth = new CompoundTag();

        Fixture(GameTestHelper helper) {
            this.helper = helper;
            player = new FakePlayer(helper.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-252-Draft]"));
            ExactCombatState base = new ExactCombatState(n("1000"), HighPowerLevel.LV2.maxHealth(), n("60"), HighPowerLevel.LV2.maxShield(), false);
            ScatterPlateShares scatter = ScatterPlateShares.restore(base.shield(), base.maxShield(), List.of(n("0"), n("24"), n("36")));
            SharedShieldReserveShares reserves = new SharedShieldReserveShares(n("15"), n("21"));
            CompoundTag combat = new CompoundTag(); base.toDecimalMap().forEach(combat::putString); combat.putInt("level", 2);
            CompoundTag parents = new CompoundTag(); scatter.toDecimalMap().forEach(parents::putString);
            combat.put(ScatterPlateStorage.KEY, parents); combat.putBoolean(ScatterPlateStorage.INITIALIZED_KEY, true);
            CompoundTag reserve = new CompoundTag(); reserves.toDecimalMap(base).forEach(reserve::putString);
            reserve.putInt("version", 2); combat.put(MagneticDomainReserve.KEY, reserve);
            activeGrowth.putString("sentinel", "retained"); combat.put("activeGrowth", activeGrowth); combat.putInt("unrelated", 77);
            player.getPersistentData().put(HighPowerCombat.DATA_KEY, combat);
            helper.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { player.discard(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { player.discard(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) { player.discard(); }
            });
        }

        CompoundTag tag() { return player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); }
        void replace(CompoundTag data) { player.getPersistentData().put(HighPowerCombat.DATA_KEY, data.copy()); }
        ZeroGapArmorStorage.Capture capture() { return ZeroGapArmorStorage.capture(player).orElseThrow(); }
        ZeroGapArmorShares armor() { return ZeroGapArmorStorage.read(player).orElseThrow(); }
        void initialize() { helper.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepareInitialization(capture())), "explicit fixture migration"); }
        void commit(ZeroGapArmorShares.Transaction transaction) {
            helper.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepare(capture(), transaction)), "standalone complete draft applied");
        }
    }
}
