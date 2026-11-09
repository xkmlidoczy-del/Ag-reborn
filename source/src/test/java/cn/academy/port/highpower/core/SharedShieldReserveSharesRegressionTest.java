package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/** Independent exact witnesses for migration, share identity and every legacy aggregate writer. */
public final class SharedShieldReserveSharesRegressionTest {
    private static int checks;
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static ExactCombatState base(String shield, String capacity) {
        return new ExactCombatState(n("80"), n("100"), n(shield), n(capacity), false);
    }
    private static void check(boolean result, String why) { checks++; if (!result) throw new AssertionError(why); }
    private static void eq(BigDecimal actual, BigDecimal expected, String why) {
        check(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static void shares(SharedShieldReserveShares value, String lower, String upper, String why) {
        eq(value.lower(), n(lower), why + " lower"); eq(value.upper(), n(upper), why + " upper");
    }
    private static void reject(Runnable action, String why) {
        try { action.run(); throw new AssertionError(why); } catch (IllegalArgumentException expected) { checks++; }
    }

    public static void main(String[] args) {
        ExactCombatState base = base("0", "100");
        Map<String,String> legacy = Map.of("version", "1", "capacity", "100", "amount", "75.125");
        SharedShieldReserveShares migrated = SharedShieldReserveShares.fromDecimalMap(base, legacy);
        shares(migrated, "50", "25.125", "v1 allocates lower first exactly once");
        var encoded = migrated.toDecimalMap(base);
        check("2".equals(encoded.get("version")), "all writes use v2");
        eq(n(encoded.get("amount")), n("75.125"), "compatibility amount is exact share sum");
        check(SharedShieldReserveShares.fromDecimalMap(base, encoded).equals(migrated), "v2 round trip");
        check(legacy.get("version").equals("1"), "pure decoder cannot mutate input");
        for (String amount : new String[]{"0", "1E-256", "49.999", "50", "100"}) {
            var value = SharedShieldReserveShares.fromDecimalMap(base,
                    Map.of("version", "1", "capacity", "100", "amount", amount));
            eq(value.total(), n(amount), "v1 migration conserves every decimal");
            check(SharedShieldReserveShares.fromDecimalMap(base, value.toDecimalMap(base)).equals(value), "migrated round trip");
        }

        var reservedUpper = new SharedShieldReserveShares(BigDecimal.ZERO, n("50"));
        shares(reservedUpper.withTotal(base, n("50")), "0", "50", "no-op legacy write never recreates lower");
        shares(reservedUpper.withTotal(base, n("40")), "0", "40", "legacy debit preserves depleted lower");
        shares(reservedUpper.withTotal(base, n("60")), "10", "50", "only a credited recovery delta refills lower");
        var paid = MagneticDomainShieldRules.spend(base, reservedUpper.total(), n("12"));
        shares(reservedUpper.withTotal(paid.state(), paid.reserve()), "0", "38", "152 refraction keeps upper identity");
        var absorbed = MagneticDomainShieldRules.absorb(base, migrated.total(), n("60"));
        shares(migrated.withTotal(absorbed.state(), absorbed.reserve()), "0", "15.125", "152 absorption pays lower then upper");
        var recovered = MagneticDomainShieldRules.recover(base("99", "100"), reservedUpper.total(), n("3"));
        shares(reservedUpper.withTotal(recovered.state(), recovered.reserve()), "2", "50", "152 legacy recovery uses one delta");

        var unequal = new SharedShieldReserveShares(n("49"), n("20"));
        var fullRecovery = unequal.recover(base("99", "100"), n("4"), true, true);
        eq(fullRecovery.state().shield(), n("100"), "base recovers first");
        shares(fullRecovery.shares(), "50", "22", "152 recovery fills lower then upper");
        eq(fullRecovery.state().health(), n("80"), "recovery preserves injury");
        var curtainRecovery = unequal.recover(base("99", "100"), n("4"), true, false);
        shares(curtainRecovery.shares(), "50", "20", "242 cannot recover upper");
        var ordinaryRecovery = unequal.recover(base("99", "100"), n("4"), false, false);
        shares(ordinaryRecovery.shares(), "49", "20", "ordinary recovery cannot credit reserve");
        reject(() -> unequal.recover(base, n("1"), false, true), "upper cannot be independently eligible");
        var dead = new ExactCombatState(BigDecimal.ZERO, n("100"), n("99"), n("100"), true);
        var deadRecovery = unequal.recover(dead, n("100"), true, true);
        shares(deadRecovery.shares(), "49", "20", "dead recovery has no credit");
        eq(deadRecovery.state().shield(), n("99"), "dead base recovery has no credit");

        shares(reservedUpper.validate(base("0", "200")), "0", "50", "growth expands ceilings without redistribution");
        reject(() -> reservedUpper.validate(base("0", "80")), "reduction refuses upper above new half even when sum fits S");
        reject(() -> new SharedShieldReserveShares(n("50"), BigDecimal.ZERO).validate(base("0", "80")), "reduction also refuses lower");
        shares(new SharedShieldReserveShares(n("40"), n("40")).validate(base("0", "80")), "40", "40", "exact reduction boundary allowed");
        var capBase = base("50000000000000000000", "50000000000000000000");
        var capShares = new SharedShieldReserveShares(n("25000000000000000000"), n("25000000000000000000")).validate(capBase);
        eq(capBase.shield().add(capShares.total()), CombatAmounts.CAP, "base plus both shares reaches exact global cap");
        reject(() -> capShares.validate(base("0", "50000000000000000001")), "certified total ceiling cannot exceed cap");
        reject(() -> new SharedShieldReserveShares(n("51"), n("0")).validate(base), "lower half cap checked");
        reject(() -> new SharedShieldReserveShares(n("0"), n("51")).validate(base), "upper half cap checked");

        for (var corruption : Map.of("version", "3", "amount", "76", "capacity", "101", "lower", "51", "upper", "NaN").entrySet()) {
            var invalid = new HashMap<>(encoded); invalid.put(corruption.getKey(), corruption.getValue());
            reject(() -> SharedShieldReserveShares.fromDecimalMap(base, invalid), "malformed/future reserve rejected: " + corruption.getKey());
        }
        for (String field : encoded.keySet()) {
            var invalid = new HashMap<>(encoded); invalid.remove(field);
            reject(() -> SharedShieldReserveShares.fromDecimalMap(base, invalid), "missing v2 field rejected: " + field);
        }

        Random random = new Random(152242);
        for (int i = 0; i < 12000; i++) {
            ExactCombatState before = base(BigDecimal.valueOf(random.nextInt(100001), 3).toPlainString(), "100");
            var beforeShares = new SharedShieldReserveShares(BigDecimal.valueOf(random.nextInt(50001), 3), BigDecimal.valueOf(random.nextInt(50001), 3));
            BigDecimal damage = BigDecimal.valueOf(random.nextInt(300001), 3);
            var hit = MagneticDomainShieldRules.absorb(before, beforeShares.total(), damage);
            var nextShares = beforeShares.withTotal(hit.state(), hit.reserve());
            BigDecimal reservePaid = damage.subtract(damage.min(before.shield())).min(beforeShares.total());
            eq(nextShares.lower(), beforeShares.lower().subtract(reservePaid.min(beforeShares.lower())), "random152 lower debit identity");
            eq(nextShares.upper(), beforeShares.upper().subtract(reservePaid.subtract(reservePaid.min(beforeShares.lower()))), "random152 upper debit identity");
            BigDecimal budget = BigDecimal.valueOf(random.nextInt(300001), 3);
            var recovery = beforeShares.recover(before, budget, true, true);
            eq(recovery.state().shield().add(recovery.shares().total()),
                    before.shield().add(beforeShares.total()).add(budget).min(n("200")), "random shared one-budget conservation");
            check(SharedShieldReserveShares.fromDecimalMap(hit.state(), nextShares.toDecimalMap(hit.state())).equals(nextShares), "random no resplit on reload");
        }
        System.out.println("PASS " + checks + " shared shield reserve migration/identity/recovery checks");
    }
}
