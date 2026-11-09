package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.ClassicSkillConfiguration;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native shared-resource witnesses; automatic damage/transport acceptance belongs to controller tests. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerEmergencyReturnBudgetRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_emergency_return_budget", ID = "emergency_return";
    private HighPowerEmergencyReturnBudgetRuntimeTests() {}

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=75)
    public static void tp342_recovery_waits_sixty_native_ticks_and_credits_one_existing_budget(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player();
        BigDecimal maximum = n(HighPowerCombat.maxShieldText(p)), budget = maximum.divide(n("100"));
        h.assertTrue(HighPowerCombat.spendShield(p, maximum.divide(BigDecimal.TWO)), "declared real EM spend starts existing quiet delay");
        f.teleporter(p, true); BigDecimal before = HighPowerCombat.shield(p); long spent = p.level().getGameTime();
        HighPowerCombat.tickShield(p); equal(h, HighPowerCombat.shield(p), before, "same spend tick is quiet");
        h.runAfterDelay(59, () -> {
            HighPowerCombat.tickShield(p); equal(h, HighPowerCombat.shield(p), before, "fifty-nine genuine ticks remain too early");
            h.runAfterDelay(1, () -> {
                h.assertTrue(p.level().getGameTime() - spent == 60, "exactly sixty genuine native ticks elapsed");
                cn.academy.port.AcademyGameplay.tick(new net.neoforged.neoforge.event.tick.PlayerTickEvent.Post(p));
                equal(h, HighPowerCombat.shield(p), before.add(budget), "actual player-post handler routes one existing 1%S budget");
                HighPowerCombat.tickShield(p); equal(h, HighPowerCombat.shield(p), before.add(budget), "same-tick duplicate cannot credit twice");
                var saved = tag(p).copy(); var ability = AbilityStorage.get(p); ability.unlearn(ID); ability.learn(ID);
                HighPowerResources.initializeBudget(p); HighPowerCombat.tickShield(p);
                h.assertTrue(tag(p).equals(saved), "same-tick relearning and resource reinitialization never refill or reset delay");
                ability.changeCategoryClassic(HighPowerCategories.TELEPORTER); ability.unlearn(ID);
                h.runAfterDelay(1, () -> {
                    HighPowerCombat.tickShield(p); equal(h, HighPowerCombat.shield(p), before.add(budget), "unlearned TP suspends the next native recovery");
                    ability.learn(ID); HighPowerCombat.tickShield(p);
                    equal(h, HighPowerCombat.shield(p), before.add(budget.multiply(BigDecimal.TWO)), "relearn resumes only the current single budget");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void tp342_base_recovery_conserves_asymmetric252_children_and_never_fills_reserves(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); f.asymmetric(p); f.teleporter(p, true); f.quiet(p);
        var before = ZeroGapArmorStorage.read(p).orElseThrow(); var reserve = tag(p).getCompound(MagneticDomainReserve.KEY).copy();
        BigDecimal budget = before.base().maxShield().divide(n("100"));
        HighPowerCombat.tickShield(p); var after = ZeroGapArmorStorage.read(p).orElseThrow();
        equal(h, after.base().shield().subtract(before.base().shield()), budget, "base receives the sole existing recovery budget");
        equal(h, after.total().subtract(before.total()), budget, "twelve child delta is exactly the base delta");
        equal(h, after.base().health(), before.base().health(), "base recovery leaves HP unchanged");
        h.assertTrue(after.reserves().equals(before.reserves()) && tag(p).getCompound(MagneticDomainReserve.KEY).equals(reserve),
                "both existing reserve parents remain byte-for-byte unchanged");
        for (int i = 0; i < 6; i++) equal(h, after.plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "unchanged reserve child " + i);
        for (int i = 0; i < 3; i++) equal(h, after.plate(2 * i).baseCurrent().add(after.plate(2 * i + 1).baseCurrent()),
                ScatterPlateStorage.read(p).orElseThrow().plate(i).current(), "actual existing base parent " + i);
        HighPowerCombat.tickShield(p);
        h.assertTrue(ZeroGapArmorStorage.read(p).orElseThrow().toDecimalMap().equals(after.toDecimalMap()), "duplicate native call cannot credit children twice");

        var full = f.player(); BigDecimal maximum = n(HighPowerCombat.maxShieldText(full));
        h.assertTrue(HighPowerCombat.spendShield(full, budget.divide(BigDecimal.TWO)), "near-full actual base fixture");
        f.reserve(full, new SharedShieldReserveShares(n("15"), n("21"))); f.teleporter(full, true); f.quiet(full);
        var retained = tag(full).getCompound(MagneticDomainReserve.KEY).copy(); HighPowerCombat.tickShield(full);
        equal(h, HighPowerCombat.shield(full), maximum, "only actual missing base room is credited");
        h.assertTrue(tag(full).getCompound(MagneticDomainReserve.KEY).equals(retained), "unused recovery cannot spill into retained reserve halves");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void category_relearn_disabled_or_corrupt342_never_refills_or_leaks_old_defenses(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); f.asymmetric(p); f.teleporter(p, true); f.quiet(p);
        HighPowerCombat.shieldActive(p, true); var saved = tag(p).copy();
        h.assertFalse(HighPowerCombat.shieldActive(p) || HighPowerCombat.spendShield(p, BigDecimal.ONE)
                || MagneticDomainReserve.available(p), "342 adds no EM bracer/domain or generic shield spending");
        h.assertTrue(HighPowerCombat.curtainReserve(p).signum() == 0 && HighPowerCombat.zeroGapReserve(p).signum() == 0,
                "342 exposes no old MD reserves");
        h.assertTrue(tag(p).equals(saved), "old defense queries are read-only and refuse");
        var ability = AbilityStorage.get(p); ability.unlearn(ID); HighPowerCombat.tickShield(p);
        h.assertTrue(tag(p).equals(saved), "unlearned TP receives no shield recovery");
        ability.learn(ID);
        try {
            SkillCatalog.initializeConfiguration(Map.of(ClassicSkillConfiguration.key(HighPowerCategories.TELEPORTER, ID),
                    new ClassicSkillConfiguration.Flags(false, false)));
            HighPowerCombat.tickShield(p); h.assertTrue(tag(p).equals(saved), "disabled342 receives no recovery");
        } finally { SkillCatalog.resetConfiguration(); }
        ability.category = "vector"; HighPowerCombat.tickShield(p);
        h.assertTrue(tag(p).equals(saved), "retained learned342 outside TP receives no recovery");
        ability.category = HighPowerCategories.TELEPORTER; ability.unlearn(ID); ability.learn(ID);
        h.assertTrue(tag(p).equals(saved), "category return/relearn cannot recreate or refill parent/child balances");
        tag(p).putString("shield", "NaN"); var corrupt = tag(p).copy(); HighPowerCombat.tickShield(p);
        h.assertTrue(tag(p).equals(corrupt) && !HighPowerCombat.enabled(p), "corrupt exact base remains inert and unrepaired");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void unauthenticated_or_null_payment_has_no_resource_or_timing_effect(GameTestHelper h) {
        var f = new Fixture(h); var p = f.player(); f.teleporter(p, true); var saved = tag(p).copy();
        try {
            Class<?> budget = Class.forName("cn.academy.port.highpower.EmergencyReturnBudget");
            Class<?> payment = Class.forName("cn.academy.port.highpower.EmergencyReturnBudget$Payment");
            h.assertFalse(Modifier.isPublic(budget.getModifiers()), "no public generic TP shield spender");
            for (var constructor : payment.getDeclaredConstructors())
                h.assertTrue(Modifier.isPrivate(constructor.getModifiers()), "payment capability has no accessible constructor");
            h.assertTrue(invoke(budget, "quote", new Class<?>[]{ServerPlayer.class, Object.class}, p, new Object()) == null,
                    "arbitrary controller tokens cannot mint a payment");
            h.assertTrue(invoke(budget, "pay", new Class<?>[]{ServerPlayer.class, payment}, p, null).toString().equals("NOT_PAID"),
                    "null payment fails before resource access");
            h.assertTrue(invoke(HighPowerCombat.class, "spendEmergencyReturn", new Class<?>[]{ServerPlayer.class, payment}, p, null).toString().equals("NOT_PAID"),
                    "even direct combat seam rejects absent capability");
            h.assertTrue(tag(p).equals(saved), "all rejected capability requests retain exact resource and quiet-delay bytes");
        } catch (ClassNotFoundException unavailable) { throw new AssertionError(unavailable); }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void inactive_interfering_overloaded_dead_uncertified_or_unapproved_tp_cannot_recover(GameTestHelper h) {
        var f = new Fixture(h);
        for (String reason : List.of("inactive", "interference", "overload", "dead", "earned_level", "contract")) {
            var p = f.player(reason.equals("earned_level") ? 3 : 4);
            h.assertTrue(HighPowerCombat.spendShield(p, BigDecimal.ONE), "declared depleted owner for " + reason);
            f.teleporter(p, true); f.quiet(p); var ability = AbilityStorage.get(p);
            switch (reason) {
                case "inactive" -> ability.setActivateState(false);
                case "interference" -> ability.interfering = true;
                case "overload" -> ability.overloadFine = false;
                case "dead" -> p.setHealth(0);
                case "earned_level" -> ability.level = 4;
                case "contract" -> tag(p).putString("adapterContract", "unapproved-future-contract");
                default -> throw new AssertionError(reason);
            }
            var saved = tag(p).copy(); HighPowerCombat.tickShield(p);
            h.assertTrue(tag(p).equals(saved), "otherwise-ineligible342 changes no resource or timing byte: " + reason);
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void malformed_or_missing_initialized_partitions_refuse342_without_repair_or_legacy_fallback(GameTestHelper h) {
        var f = new Fixture(h);
        for (String reason : List.of("children", "children_missing", "scatter", "scatter_missing", "reserve", "reserve_missing", "reserve_latched", "spend_clock", "recovery_clock")) {
            var p = f.player(); f.asymmetric(p); f.teleporter(p, true); f.quiet(p);
            switch (reason) {
                case "children" -> tag(p).getCompound(ZeroGapArmorStorage.KEY).putString("base5", "NaN");
                case "children_missing" -> tag(p).remove(ZeroGapArmorStorage.KEY);
                case "scatter", "scatter_missing", "reserve", "reserve_latched" -> {
                    // Also exercise standalone old parents without optional252 metadata.
                    tag(p).remove(ZeroGapArmorStorage.KEY); tag(p).remove(ZeroGapArmorStorage.TIMING_KEY);
                    tag(p).remove(ZeroGapArmorStorage.INITIALIZED_KEY); tag(p).remove(ZeroGapArmorStorage.INVALIDATED_KEY);
                    if (reason.equals("scatter")) tag(p).getCompound(ScatterPlateStorage.KEY).putString("plate2", "NaN");
                    if (reason.equals("scatter_missing")) tag(p).remove(ScatterPlateStorage.KEY);
                    if (reason.equals("reserve")) tag(p).getCompound(MagneticDomainReserve.KEY).putString("lower", "NaN");
                    if (reason.equals("reserve_latched")) tag(p).putBoolean("magneticDomainReserveUnavailable", true);
                }
                case "reserve_missing" -> tag(p).remove(MagneticDomainReserve.KEY);
                case "spend_clock" -> tag(p).putString("lastShieldSpend", "0");
                case "recovery_clock" -> tag(p).putString("lastShieldTick", "0");
                default -> throw new AssertionError(reason);
            }
            var saved = tag(p).copy();
            h.assertFalse((Boolean) invoke(HighPowerCombat.class, "emergencyReturnBenefitsActive", new Class<?>[]{net.minecraft.world.entity.LivingEntity.class}, p),
                    "enhanced342 refuses existing invalid metadata: " + reason);
            HighPowerCombat.tickShield(p);
            h.assertTrue(tag(p).equals(saved), "342 cannot enter ordinary fallback, invalidate, migrate or replace payload: " + reason);
        }
        h.succeed();
    }

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static CompoundTag tag(ServerPlayer p) { return p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); }
    private static void equal(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static Object invoke(Class<?> owner, String method, Class<?>[] parameters, Object... arguments) {
        try { var target = owner.getDeclaredMethod(method, parameters); target.setAccessible(true); return target.invoke(null, arguments); }
        catch (InvocationTargetException failed) { throw new AssertionError("Resource boundary failed: " + method, failed.getCause()); }
        catch (ReflectiveOperationException unavailable) { throw new AssertionError("Missing resource boundary: " + method, unavailable); }
    }

    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player() { return player(4); }
        ServerPlayer player(int level) {
            var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-342-Budget]")); players.add(p);
            p.tickCount = 1; // The explicit player-post integration probe does not invoke periodic network sync.
            var ability = AbilityStorage.get(p); ability.changeCategoryClassic(HighPowerCategories.ELECTROMASTER); ability.setLevel(level);
            ability.learn(HighPowerSkills.FORKED_ARC); ability.setActivateState(true); ability.overloadFine = true;
            h.assertTrue(HighPowerCombat.enroll(p, level), "declared native existing resource owner"); HighPowerResources.initializeBudget(p);
            return p;
        }
        void teleporter(ServerPlayer p, boolean learned) {
            var ability = AbilityStorage.get(p); ability.changeCategoryClassic(HighPowerCategories.TELEPORTER);
            ability.learn(HighPowerSkills.SAFE_LANDING_PROBE); if (learned) ability.learn(ID);
            ability.setActivateState(true); ability.overloadFine = true;
            HighPowerGrowth.values(p);
        }
        /** Mature-delay setup only; the first test separately witnesses sixty genuine ticks. */
        void quiet(ServerPlayer p) { tag(p).putLong("lastShieldSpend", p.level().getGameTime() - 60); }
        void reserve(ServerPlayer p, SharedShieldReserveShares shares) {
            var state = new ExactCombatState(n(HighPowerCombat.healthText(p)), n(HighPowerCombat.maxHealthText(p)),
                    HighPowerCombat.shield(p), n(HighPowerCombat.maxShieldText(p)), false);
            var encoded = new CompoundTag();
            shares.toDecimalMap(state).forEach(encoded::putString); encoded.putInt("version", MagneticDomainReserve.VERSION);
            tag(p).put(MagneticDomainReserve.KEY, encoded);
        }
        void asymmetric(ServerPlayer p) {
            var base = new ExactCombatState(n("1000"), n(HighPowerCombat.maxHealthText(p)), n("60"), n(HighPowerCombat.maxShieldText(p)), false);
            var scatter = ScatterPlateShares.restore(base.shield(), base.maxShield(), List.of(n("20"), n("20"), n("20")));
            var reserves = new SharedShieldReserveShares(n("15"), n("21"));
            base.toDecimalMap().forEach(tag(p)::putString); var encoded = new CompoundTag(); scatter.toDecimalMap().forEach(encoded::putString);
            tag(p).put(ScatterPlateStorage.KEY, encoded); tag(p).putBoolean(ScatterPlateStorage.INITIALIZED_KEY, true); reserve(p, reserves);
            h.assertTrue(ZeroGapArmorStorage.ensureLearned(p), "declared existing252 storage initialized once");
            var armor = ZeroGapArmorShares.restore(base, scatter, reserves,
                    List.of(n("3"), n("17"), n("5"), n("15"), n("7"), n("13")),
                    List.of(n("1"), n("2"), n("5"), n("7"), n("9"), n("12")));
            var children = new CompoundTag(); armor.toDecimalMap().forEach(children::putString); tag(p).put(ZeroGapArmorStorage.KEY, children);
            h.assertTrue(ZeroGapArmorStorage.read(p).isPresent(), "declared unequal children match all authoritative parents");
        }
        void close() {
            if (closed) return; closed = true; SkillCatalog.resetConfiguration();
            for (var p : players) { HighPowerGrowth.invalidate(p); AbilityStorage.remove(p); p.discard(); }
        }
    }
}
