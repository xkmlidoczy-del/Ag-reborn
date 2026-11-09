package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Random;

import static cn.academy.port.highpower.core.MultiAxisReflectionRules.*;

/** CPU-only exact ledger, native rounding, persistence, and physical quota regression. */
public final class MultiAxisReflectionRulesRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        exactDebits();
        nativeRounding();
        rollingQuota();
        continuousCosts();
        System.out.println("MultiAxisReflectionRulesRegressionTest passed " + checks + " checks");
    }

    private static void exactDebits() {
        ExactCombatState base = state("7.125");
        SharedShieldReserveShares shares = new SharedShieldReserveShares(d("4.25"), d("49.5"));
        equal(capacity(base), d("150"), "nominal capacity is exactly base plus one half S");
        equal(available(base, shares), d("11.375"), "upper reserve cannot fund a reflection");
        Result paid = reflect(base, shares, d("8.125"));
        check(paid.accepted(), "a complete exact debit accepts");
        equal(paid.spent(), d("8.125"), "the arrow multiplier is not reused");
        equal(paid.state().shield(), BigDecimal.ZERO, "base pays first");
        equal(paid.shares().lower(), d("3.25"), "only the lower remainder pays");
        equal(paid.shares().upper(), shares.upper(), "upper identity remains untouched");
        equal(paid.state().health(), base.health(), "reflection does not damage or heal HP");
        check(!paid.state().shieldEnabled(), "successful special shield spend disables generic pass");
        equal(base.shield(), d("7.125"), "immutable base never mutates during planning");

        Result exact = reflect(base, shares, d("11.375"));
        check(exact.accepted(), "exact eligible balance accepts inclusively");
        equal(available(exact.state(), exact.shares()), BigDecimal.ZERO, "exact full debit leaves zero eligible S");
        for (BigDecimal invalid : new BigDecimal[] {BigDecimal.ZERO, d("11.3750000000000000000000001"),
                d("12"), d("-.1"), d("1e21"), d("1e-257"), null}) {
            unchanged(reflect(base, shares, invalid), base, shares, "zero, invalid or insufficient amount refuses atomically");
        }
        ExactCombatState dead = new ExactCombatState(BigDecimal.ZERO, d("100"), d("7.125"), d("100"), true);
        unchanged(reflect(dead, shares, BigDecimal.ONE), dead, shares, "dead owner refuses without spend");
        SharedShieldReserveShares corrupt = new SharedShieldReserveShares(d("50.0001"), BigDecimal.ZERO);
        unchanged(reflect(base, corrupt, BigDecimal.ONE), base, corrupt, "corrupt over-cap share refuses");
        check(!reflect(null, shares, BigDecimal.ONE).accepted() && !reflect(base, null, BigDecimal.ONE).accepted(),
                "absent ledger state refuses");
        unchanged(reflect(state("0"), new SharedShieldReserveShares(BigDecimal.ZERO, d("50")), BigDecimal.ONE),
                state("0"), new SharedShieldReserveShares(BigDecimal.ZERO, d("50")), "upper-only reserves refuse");

        String[] values = {"0", "1e-256", "3e-256", ".000000000000000000000001", ".125", "5.125", "49.5"};
        for (String baseValue : values) for (String lowerValue : values) for (String hitValue : values) {
            ExactCombatState before = state(baseValue);
            SharedShieldReserveShares reserve = new SharedShieldReserveShares(d(lowerValue), d("50"));
            BigDecimal incoming = d(hitValue), eligible = before.shield().add(reserve.lower());
            Result result = reflect(before, reserve, incoming);
            boolean expected = incoming.signum() > 0 && incoming.compareTo(eligible) <= 0;
            check(result.accepted() == expected, "admission depends on full exact eligible balance");
            if (expected) {
                BigDecimal baseSpent = before.shield().subtract(result.state().shield());
                BigDecimal lowerSpent = reserve.lower().subtract(result.shares().lower());
                equal(baseSpent.add(lowerSpent), incoming, "decimal conservation at the 1e-256 boundary");
                equal(result.spent(), incoming, "reported debit matches complete incoming amount");
                equal(baseSpent, incoming.min(before.shield()), "base S precedes the lower share");
                equal(result.shares().upper(), reserve.upper(), "upper share identity survives all debits");
                equal(result.state().health(), before.health(), "HP remains exactly unchanged");
            } else unchanged(result, before, reserve, "refused hit leaves the complete ledger unchanged");
        }
    }

    private static void nativeRounding() {
        equal(nativeArrowDamage(Math.nextUp(1.0), 1, false), BigDecimal.ONE,
                "native float speed rounds next-double-up before multiplication");
        check(Math.ceil(Math.nextUp(1.0)) == 2, "counterexample distinguishes a double-only amount");
        equal(nativeArrowDamage(1, Math.nextUp(1.0), true), BigDecimal.ONE,
                "plain weapon damage casts baseDamage to float");
        equal(nativeArrowDamage(1, Math.nextUp(1.0), false), d("2"),
                "weaponless arrow retains the exact double baseDamage");
        equal(nativeArrowDamage(8, 64, true), d("512"), "eligible native arrow maximum remains 512");
        equal(nativeArrowDamage(0, 64, true), BigDecimal.ZERO, "motionless arrow cannot mint damage");
        equal(nativeArrowDamage(8, 0, false), BigDecimal.ZERO, "zero base damage cannot reflect");
        equal(nativeArrowDamage(Double.MIN_VALUE, 1, false), BigDecimal.ZERO, "float speed underflow follows native semantics");
        equal(nativeArrowDamage(Double.MAX_VALUE, 64, true), BigDecimal.valueOf(Integer.MAX_VALUE),
                "native product is clamped before ceil");
        for (double invalid : new double[] {-1, Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            rejects(() -> nativeArrowDamage(invalid, 1, false), "invalid speed refuses");
            rejects(() -> nativeArrowDamage(1, invalid, true), "invalid base damage refuses");
        }
        Random random = new Random(442);
        for (int i = 0; i < 2000; i++) for (boolean weapon : new boolean[] {false, true}) {
            double speed = random.nextDouble() * 8, damage = random.nextDouble() * 64;
            // Independent spelling of the pinned source's float, clamp, and Mth.ceil steps.
            float f = (float) speed;
            double d0 = weapon ? (double) (float) damage : damage;
            double clamped = Math.min(Math.max((double) f * d0, 0.0), 2.147483647E9);
            int truncated = (int) clamped;
            int expected = clamped > truncated ? truncated + 1 : truncated;
            equal(nativeArrowDamage(speed, damage, weapon), BigDecimal.valueOf(expected), "pinned native integer oracle");
        }
    }

    private static void rollingQuota() {
        check(FACET_COUNT == 4 && MAX_REFLECTIONS == 4 && QUOTA_TICKS == 20, "four native events per rolling twenty ticks");
        Quota quota = Quota.empty();
        for (int facet = 0; facet < 4; facet++) {
            check(quota.canConsume(facet), "each fresh facet may accept once");
            quota = quota.consume(facet);
            check(!quota.canConsume(facet), "same facet cannot repeat during the same physical tick");
            Quota restored = Quota.restore(quota.remainingTicks(), quota.usedFacetMask());
            check(restored.equals(quota) && !restored.canConsume(facet), "persisted relog retains the facet and rolling fences");
        }
        check(Arrays.equals(quota.remainingTicks(), new int[] {20, 20, 20, 20}), "each event occupies its own full clock");
        for (int tick = 1; tick < 20; tick++) {
            quota = quota.nativeTick();
            check(!quota.canConsume(0), "the rolling window is exhausted before its twentieth true tick");
        }
        quota = quota.nativeTick();
        check(quota.canConsume(0), "capacity returns after exactly twenty true ticks");
        Quota staggered = Quota.EMPTY.consume(0).nativeTick().consume(0).nativeTick().consume(0).nativeTick().consume(0);
        check(Arrays.equals(staggered.remainingTicks(), new int[] {20, 19, 18, 17}), "rolling clocks preserve individual ages");
        for (int i = 0; i < 17; i++) staggered = staggered.nativeTick();
        check(Arrays.equals(staggered.remainingTicks(), new int[] {3, 2, 1, 0}), "only the oldest event expires first");
        check(staggered.consume(3).firstRemaining() == 20, "an expired slot permits exactly one replacement");
        int[] snapshot = staggered.remainingTicks(); snapshot[0] = 0;
        check(staggered.firstRemaining() == 3, "persistence snapshots cannot mutate quota state");
        for (int[] invalid : new int[][] {{0}, {21, 0, 0, 0}, {0, 20, 0, 0}, {20, 0, -1, -1}, {20, 19, 0, 1}})
            rejects(() -> Quota.restore(invalid, 0), "malformed clocks refuse rather than resetting capacity");
        rejects(() -> Quota.restore(null, 0), "missing clocks refuse");
        rejects(() -> Quota.restore(new int[4], 16), "unknown persisted facet bits refuse");
        rejects(() -> Quota.restore(new int[4], -1), "negative persisted facet mask refuses");
        check(!Quota.EMPTY.canConsume(-1) && !Quota.EMPTY.canConsume(4), "unknown facets cannot consume");
        Quota spent = Quota.EMPTY.consume(0);
        rejects(() -> spent.consume(0), "same-facet second consume fails closed");
    }

    private static void continuousCosts() {
        for (int i = 100; i <= 500; i++) {
            BigDecimal q = BigDecimal.valueOf(i, 2);
            equal(theoreticalActivationCost(q), VectorParryRules.theoreticalActivationCost(q), "activation reuses the continuous curve");
            equal(theoreticalSustainCost(q), VectorParryRules.theoreticalSustainCost(q), "sustain reuses the continuous curve");
            equal(theoreticalCooldown(q), VectorParryRules.theoreticalCooldown(q), "cooldown reuses the continuous curve");
            check(cooldownTicks(q) == VectorParryRules.cooldownTicks(q), "native fence follows vector parry exactly");
        }
        check(theoreticalActivationCost(d("3.00000001")).compareTo(theoreticalActivationCost(d("3"))) < 0,
                "fractional growth is not quantized");
        check(PAYMENT_PERIOD == 20 && RESTART_FENCE == 4 && cooldownTicks(d("5")) == 4, "physical payment and restart fences remain");
        check(clock(0) && !clock(-1) && !clock(Long.MAX_VALUE), "scheduling overflow fails closed");
    }

    private static ExactCombatState state(String shield) { return new ExactCombatState(d("100"), d("100"), d(shield), d("100"), true); }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static void unchanged(Result result, ExactCombatState base, SharedShieldReserveShares shares, String why) {
        check(!result.accepted() && result.spent().signum() == 0 && result.state().equals(base) && result.shares().equals(shares), why);
    }
    private static void equal(BigDecimal actual, BigDecimal expected, String why) { check(actual.compareTo(expected) == 0, why); }
    private static void rejects(Runnable action, String why) {
        try { action.run(); throw new AssertionError(why); }
        catch (IllegalArgumentException | IllegalStateException | NullPointerException expected) { checks++; }
    }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
}
