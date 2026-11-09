package cn.academy.port.gametest;

import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.MagneticDomainReserve;
import cn.academy.port.highpower.core.DissipationCurtainShieldRules;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.MagneticDomainShieldRules;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
import java.lang.reflect.InvocationTargetException;
import java.math.BigDecimal;
import java.util.Optional;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Actual native NBT codec and legacy-writer witnesses, separate from contact/controller tests. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDissipationCurtainReserveRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_dissipation_curtain_reserve";
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static ExactCombatState base(String shield, String capacity) {
        return new ExactCombatState(n("80"), n("100"), n(shield), n(capacity), false);
    }
    private static LivingEntity entity(GameTestHelper h) {
        LivingEntity entity = EntityType.COW.create(h.getLevel());
        entity.getPersistentData().put(HighPowerCombat.DATA_KEY, new CompoundTag());
        return entity;
    }
    private static CompoundTag combat(LivingEntity entity) { return entity.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); }
    private static CompoundTag reserve(LivingEntity entity) { return combat(entity).getCompound(MagneticDomainReserve.KEY); }
    private static CompoundTag legacy(String amount, String capacity) {
        CompoundTag tag = new CompoundTag(); tag.putInt("version", 1); tag.putString("amount", amount); tag.putString("capacity", capacity); return tag;
    }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static void checkShares(GameTestHelper h, LivingEntity entity, ExactCombatState base, String lower, String upper, String why) {
        SharedShieldReserveShares shares = saved(entity, base).orElseThrow();
        eq(h, shares.lower(), n(lower), why + " lower"); eq(h, shares.upper(), n(upper), why + " upper");
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void curtain_reserve_migrates_v1_once_and_roundtrips_native_entity_nbt(GameTestHelper h) {
        LivingEntity entity = entity(h); ExactCombatState base = base("0", "100");
        h.assertTrue(saved(entity, base).orElseThrow().equals(SharedShieldReserveShares.ZERO), "missing reserve is valid zero");
        h.assertFalse(combat(entity).contains(MagneticDomainReserve.KEY), "missing zero read creates no stored resources");
        h.assertTrue(ensureMigrated(entity, base), "explicit setup accepts missing zero");
        h.assertFalse(combat(entity).contains(MagneticDomainReserve.KEY), "explicit missing-zero setup awards nothing");
        CompoundTag legacy = legacy("75.125", "100");
        combat(entity).put(MagneticDomainReserve.KEY, legacy.copy());
        checkShares(h, entity, base, "50", "25.125", "logical v1 read");
        checkShares(h, entity, base, "50", "25.125", "repeated logical v1 read");
        h.assertTrue(reserve(entity).equals(legacy), "observer reads preserve exact legacy payload");
        h.assertTrue(ensureMigrated(entity, base), "explicit setup migrates valid legacy payload");
        h.assertTrue(reserve(entity).getInt("version") == 2, "explicit migration persists v2");
        eq(h, n(reserve(entity).getString("amount")), n("75.125"), "persisted aggregate sum remains exact");
        commitShares(entity, base, new SharedShieldReserveShares(BigDecimal.ZERO, n("25.125")));
        LivingEntity loaded = entity(h); loaded.load(entity.saveWithoutId(new CompoundTag()));
        checkShares(h, loaded, base, "0", "25.125", "native save/load retains fixed upper identity");
        var blocked = DissipationCurtainShieldRules.absorb(base, saved(loaded, base).orElseThrow(), n("10"), true);
        eq(h, blocked.absorbed(), BigDecimal.ZERO, "reloaded upper-only reserve cannot mint lower absorption");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void curtain_reserve_legacy_absorption_refraction_and_recovery_preserve_shares(GameTestHelper h) {
        LivingEntity entity = entity(h); ExactCombatState base = base("0", "100");
        commitShares(entity, base, new SharedShieldReserveShares(n("10"), n("40")));
        var hit = MagneticDomainShieldRules.absorb(base, n("50"), n("15"));
        commit(entity, hit.state(), hit.reserve());
        checkShares(h, entity, hit.state(), "0", "35", "legacy absorption");
        var fee = MagneticDomainShieldRules.spend(hit.state(), n("35"), n("5"));
        commit(entity, fee.state(), fee.reserve());
        checkShares(h, entity, fee.state(), "0", "30", "legacy refraction");
        // No-op writes and reload reads may never split the aggregate back into lower.
        commit(entity, fee.state(), n("30"));
        checkShares(h, entity, fee.state(), "0", "30", "aggregate no-op");
        var recovery = MagneticDomainShieldRules.recover(base("99", "100"), n("30"), n("3"));
        commit(entity, recovery.state(), recovery.reserve());
        checkShares(h, entity, recovery.state(), "2", "30", "only unused recovery budget credits lower");
        eq(h, recovery.state().shield(), n("100"), "legacy recovery still fills base first");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void curtain_reserve_capacity_growth_preserves_and_reduction_refuses_each_half(GameTestHelper h) {
        LivingEntity entity = entity(h); ExactCombatState before = base("0", "100"), growth = base("0", "200");
        commitShares(entity, before, new SharedShieldReserveShares(BigDecimal.ZERO, n("50")));
        h.assertTrue(canChange(entity, before, growth), "capacity growth is allowed");
        capacityChanged(entity, before, growth);
        checkShares(h, entity, growth, "0", "50", "growth changes only ceilings");
        eq(h, n(reserve(entity).getString("capacity")), n("200"), "new capacity recorded");
        CompoundTag unchanged = reserve(entity).copy();
        h.assertFalse(canChange(entity, growth, base("0", "80")), "reduction cannot destroy upper above new half");
        h.assertTrue(reserve(entity).equals(unchanged), "refused reduction preserves entire reserve payload");
        h.assertTrue(canChange(entity, growth, before), "exact half boundary reduction allowed");
        capacityChanged(entity, growth, before);
        checkShares(h, entity, before, "0", "50", "allowed reduction preserves each share");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void curtain_reserve_malformed_and_future_payloads_are_inert_and_untouched(GameTestHelper h) {
        ExactCombatState before = base("0", "100"), growth = base("0", "200");
        for (int variant = 0; variant < 7; variant++) {
            LivingEntity entity = entity(h); CompoundTag invalid = legacy("50", "100");
            if (variant == 0) invalid.putInt("version", 999);
            if (variant == 1) invalid.putString("amount", "1E-257");
            if (variant == 2) invalid.putString("capacity", "99");
            if (variant >= 3) {
                invalid.putInt("version", 2); invalid.putString("lower", "0"); invalid.putString("upper", "50");
                if (variant == 3) invalid.putString("amount", "49");
                if (variant == 4) invalid.putString("upper", "51");
                if (variant == 5) invalid.putInt("lower", 0);
                if (variant == 6) invalid.remove("upper");
            }
            combat(entity).put(MagneticDomainReserve.KEY, invalid.copy());
            h.assertTrue(saved(entity, before).isEmpty(), "malformed/future reserve unavailable " + variant);
            h.assertTrue(reserve(entity).equals(invalid), "read preserves payload " + variant);
            h.assertFalse(ensureMigrated(entity, before), "explicit setup refuses invalid payload " + variant);
            h.assertTrue(reserve(entity).equals(invalid), "explicit setup preserves invalid payload " + variant);
            h.assertTrue(canChange(entity, before, growth), "bad optional reserve cannot veto valid base growth " + variant);
            capacityChanged(entity, before, growth);
            h.assertTrue(reserve(entity).equals(invalid), "base growth preserves corrupt payload " + variant);
            h.assertTrue(saved(entity, before).isEmpty() && saved(entity, growth).isEmpty(), "capacity changes cannot revive invalid reserve " + variant);
            boolean refused = false;
            try { commitShares(entity, growth, SharedShieldReserveShares.ZERO); }
            catch (IllegalArgumentException expected) { refused = true; }
            h.assertTrue(refused && reserve(entity).equals(invalid), "writer cannot erase unavailable optional data " + variant);
        }
        h.succeed();
    }

    @SuppressWarnings("unchecked")
    private static Optional<SharedShieldReserveShares> saved(LivingEntity entity, ExactCombatState base) {
        return (Optional<SharedShieldReserveShares>) invoke("savedShares", new Class<?>[]{LivingEntity.class, ExactCombatState.class}, entity, base);
    }
    private static void commit(LivingEntity entity, ExactCombatState base, BigDecimal amount) {
        invoke("commit", new Class<?>[]{LivingEntity.class, ExactCombatState.class, BigDecimal.class}, entity, base, amount);
    }
    private static void commitShares(LivingEntity entity, ExactCombatState base, SharedShieldReserveShares shares) {
        invoke("commitShares", new Class<?>[]{LivingEntity.class, ExactCombatState.class, SharedShieldReserveShares.class}, entity, base, shares);
    }
    private static boolean ensureMigrated(LivingEntity entity, ExactCombatState base) {
        return (boolean) invoke("ensureMigrated", new Class<?>[]{LivingEntity.class, ExactCombatState.class}, entity, base);
    }
    private static boolean canChange(LivingEntity entity, ExactCombatState before, ExactCombatState after) {
        return (boolean) invoke("canChangeCapacity", new Class<?>[]{LivingEntity.class, ExactCombatState.class, ExactCombatState.class}, entity, before, after);
    }
    private static void capacityChanged(LivingEntity entity, ExactCombatState before, ExactCombatState after) {
        invoke("capacityChanged", new Class<?>[]{LivingEntity.class, ExactCombatState.class, ExactCombatState.class}, entity, before, after);
    }
    private static Object invoke(String name, Class<?>[] parameters, Object... arguments) {
        try {
            var method = MagneticDomainReserve.class.getDeclaredMethod(name, parameters); method.setAccessible(true);
            return method.invoke(null, arguments);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof RuntimeException runtime) throw runtime;
            throw new AssertionError("Reserve invocation failed: " + name, error.getCause());
        } catch (ReflectiveOperationException error) { throw new AssertionError("Reserve boundary missing: " + name, error); }
    }
}
