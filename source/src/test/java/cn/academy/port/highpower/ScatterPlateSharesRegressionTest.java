package cn.academy.port.highpower;

import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ScatterPlateShares;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Dependency-free conservation and persistence witnesses for NEW212's shares of existing S. */
public final class ScatterPlateSharesRegressionTest {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal CAP = CombatAmounts.CAP;
    private static final BigDecimal TINY = new BigDecimal("1e-256");
    private static int checks;

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void check(boolean value, String why) { checks++; if (!value) throw new AssertionError(why); }
    private static void eq(BigDecimal actual, BigDecimal expected, String why) {
        check(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static void fails(Runnable operation, String why) {
        try { operation.run(); throw new AssertionError("Accepted invalid input: " + why); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void nullFails(Runnable operation, String why) {
        try { operation.run(); throw new AssertionError("Accepted null: " + why); }
        catch (NullPointerException expected) { checks++; }
    }
    private static void same(ScatterPlateShares actual, ScatterPlateShares expected, String why) {
        eq(actual.shield(), expected.shield(), why + " total");
        eq(actual.maxShield(), expected.maxShield(), why + " maximum");
        for (int i = 0; i < 3; i++) {
            eq(actual.plate(i).current(), expected.plate(i).current(), why + " current " + i);
            eq(actual.plate(i).capacity(), expected.plate(i).capacity(), why + " capacity " + i);
        }
    }
    private static void invariant(ScatterPlateShares shares, BigDecimal shield, BigDecimal maximum) {
        BigDecimal currents = ZERO, capacities = ZERO;
        check(shares.plates().size() == 3, "exactly three immutable plate shares");
        for (var plate : shares.plates()) {
            currents = currents.add(plate.current());
            capacities = capacities.add(plate.capacity());
            check(plate.current().signum() >= 0 && plate.current().compareTo(plate.capacity()) <= 0,
                    "each plate stays within its own capacity");
            eq(CombatAmounts.parse(CombatAmounts.decimalString(plate.current()), "current"), plate.current(), "current remains serializable");
            eq(CombatAmounts.parse(CombatAmounts.decimalString(plate.capacity()), "capacity"), plate.capacity(), "capacity remains serializable");
        }
        eq(currents, shield, "plate currents exactly equal EXISTING base shield");
        eq(capacities, maximum, "plate capacities exactly equal EXISTING maximum S");
        eq(shares.shield(), shield, "aggregate current agrees with shares");
        eq(shares.maxShield(), maximum, "aggregate maximum agrees with shares");
        check(shares.requireMatches(shield, maximum) == shares, "matching authority is a no-op");
        check(shares.plate(0).capacity().subtract(shares.plate(2).capacity()).abs().compareTo(TINY) <= 0,
                "deterministic capacity shares differ by at most one admitted quantum");
    }

    public static void main(String[] args) {
        initializationAndRemainders();
        contactAndExhaustion();
        categoryFallbackBreaks();
        spendingRecoveryAndGrowth();
        persistenceAndCorruption();
        repeatedSequences();
        System.out.println("PASS " + checks + " NEW212 exact three-plate shared-base shield checks");
    }

    private static void initializationAndRemainders() {
        BigDecimal[] values = {ZERO, TINY, TINY.multiply(n("2")), TINY.multiply(n("3")), TINY.multiply(n("4")),
                n("1e-255"), n(".000000000000000001"), n(".1"), n("1"), n("1.23456789"),
                n("30"), CAP.subtract(TINY), CAP};
        for (BigDecimal maximum : values) for (BigDecimal current : values) if (current.compareTo(maximum) <= 0) {
            var shares = ScatterPlateShares.initialize(current, maximum);
            invariant(shares, current, maximum);
            BigInteger[] integerShares = current.movePointRight(256).toBigIntegerExact().divideAndRemainder(BigInteger.valueOf(3));
            for (int i = 0; i < 3; i++) {
                BigInteger expected = integerShares[0].add(i < integerShares[1].intValueExact() ? BigInteger.ONE : BigInteger.ZERO);
                eq(shares.plate(i).current(), new BigDecimal(expected, 256), "independent integer-quantum partition oracle");
            }
            same(ScatterPlateShares.fromDecimalMap(shares.toDecimalMap()), shares, "boundary round trip");
        }
        var one = ScatterPlateShares.initialize(TINY, TINY);
        eq(one.plate(0).current(), TINY, "first plate receives one-quantum remainder");
        eq(one.plate(1).current(), ZERO, "one quantum is not rounded into a second shield");
        var two = ScatterPlateShares.initialize(TINY.multiply(n("2")), TINY.multiply(n("3")));
        eq(two.plate(0).current(), TINY, "two-quantum remainder first share");
        eq(two.plate(1).current(), TINY, "two-quantum remainder second share");
        eq(two.plate(2).current(), ZERO, "remainder never overflows last tiny plate");
        var previous = ScatterPlateShares.initialize(ZERO, ZERO);
        for (int units = 1; units <= 300; units++) {
            BigDecimal maximum = TINY.multiply(BigDecimal.valueOf(units));
            var grown = previous.withMaximum(maximum);
            for (int i = 0; i < 3; i++) {
                check(grown.plate(i).capacity().compareTo(previous.plate(i).capacity()) >= 0, "every capacity grows monotonically at quantum boundaries");
                eq(grown.plate(i).current(), previous.plate(i).current(), "capacity growth never refills tiny shares");
            }
            previous = grown.afterBaseRecovery(maximum);
            invariant(previous, maximum, maximum);
        }
    }

    private static void contactAndExhaustion() {
        var before = ScatterPlateShares.restore(n("42"), n("90"), List.of(ZERO, n("30"), n("12")));
        var broken = before.contact(0, n("100"), CAP);
        check(broken.shares() == before, "broken plate contact is a no-op");
        eq(broken.absorbed(), ZERO, "broken plate borrows nothing");
        eq(broken.residual(), n("100"), "broken plate retains full incoming residual");
        var bounded = before.contact(1, n("35.25"), n("7.125"));
        eq(bounded.absorbed(), n("7.125"), "authenticated ceiling bounds absorption");
        eq(bounded.residual(), n("28.125"), "exact fractional residual");
        eq(bounded.shares().plate(1).current(), n("22.875"), "only contacted plate pays");
        eq(bounded.shares().plate(0).current(), ZERO, "broken neighbor stays broken");
        eq(bounded.shares().plate(2).current(), n("12"), "untouched neighbor preserves every decimal");
        invariant(bounded.shares(), n("34.875"), n("90"));
        invariant(before, n("42"), n("90"));
        var exhausted = before.contact(2, n("100"), CAP);
        eq(exhausted.absorbed(), n("12"), "incoming larger than plate pays only contacted balance");
        eq(exhausted.residual(), n("88"), "excess cannot draw other plates or domain reserve");
        eq(exhausted.shares().plate(1).current(), n("30"), "healthy plate cannot cover exhausted neighbor");
        var repeated = exhausted.shares().contact(2, TINY, CAP);
        eq(repeated.absorbed(), ZERO, "repeated hit on exhausted plate absorbs zero");
        eq(repeated.residual(), TINY, "even smallest residual survives exhaustion");
        check(before.contact(1, ZERO, CAP).shares() == before, "zero incoming is unchanged");
        check(before.contact(1, CAP, ZERO).shares() == before, "zero authenticated allowance is unchanged");
        var actualBound = before.contact(1, n(".125"), CAP);
        eq(actualBound.absorbed(), n(".125"), "actual mitigated incoming independently bounds absorption");
        eq(actualBound.residual(), ZERO, "whole actual incoming absorbed exactly");
        var huge = ScatterPlateShares.initialize(CAP, CAP);
        for (int i = 0; i < 3; i++) {
            var tiny = huge.contact(i, TINY, CAP);
            eq(tiny.shares().plate(i).current(), huge.plate(i).current().subtract(TINY), "smallest debit survives 1e20 total");
            invariant(tiny.shares(), CAP.subtract(TINY), CAP);
            var large = huge.contact(i, CAP, CAP);
            eq(large.absorbed(), huge.plate(i).current(), "1e20 contact exhausts only one third");
            eq(large.residual(), CAP.subtract(huge.plate(i).current()), "1e20 residual remains exact");
            eq(large.shares().plate(i).current(), ZERO, "large contact leaves exact broken zero");
        }
    }

    private static void categoryFallbackBreaks() {
        var before=ScatterPlateShares.initialize(n("6"),n("60"));
        var contact=before.contact(2,n("5"),CAP);
        eq(contact.absorbed(),n("2"),"contact owns only its original two-point parent");
        check(contact.shares().brokenByBaseSpend(n("1")).equals(List.of(0)),"additional base fallback reports exhausted sibling, not original already-broken contact");
        var after=contact.shares().afterBaseSpend(n("1"));
        eq(before.shield().subtract(after.shield()),n("5"),"contact plus residual spends exactly one five-point budget");
        eq(after.plate(1).current(),n("1"),"surviving sibling retains exact remainder");
        check(contact.shares().brokenByBaseSpend(contact.shares().shield()).isEmpty(),"zero fallback cannot invalidate an unspent sibling");
        check(contact.shares().brokenByBaseSpend(ZERO).equals(List.of(0,1)),"full fallback invalidates every remaining sibling once");
        fails(()->contact.shares().brokenByBaseSpend(n("5")),"a recovery cannot masquerade as a base-spend invalidation");
        var tiny=ScatterPlateShares.initialize(TINY.multiply(n("6")),TINY.multiply(n("60"))).contact(2,TINY.multiply(n("5")),CAP);
        check(tiny.shares().brokenByBaseSpend(TINY).equals(List.of(0)),"one-quantum survivor remains live");
        try{contact.shares().brokenByBaseSpend(ZERO).clear();throw new AssertionError("Mutable sibling break list");}catch(UnsupportedOperationException expected){checks++;}
    }

    private static void spendingRecoveryAndGrowth() {
        var before = ScatterPlateShares.restore(n("42"), n("90"), List.of(ZERO, n("30"), n("12")));
        check(before.afterBaseSpend(n("42")) == before, "no spend does not repartition damaged shares");
        check(before.afterBaseRecovery(n("42")) == before, "no actual recovery does not heal damaged shares");
        check(before.withMaximum(n("90.000")) == before, "equivalent maximum is unchanged");
        var spend = before.afterBaseSpend(n("7.25"));
        eq(spend.plate(0).current(), ZERO, "shared spend preserves broken first plate");
        eq(spend.plate(1).current(), ZERO, "shared spend drains first available plate");
        eq(spend.plate(2).current(), n("7.25"), "shared spend distributes only remaining actual decrease");
        invariant(spend, n("7.25"), n("90"));
        var recovered = before.afterBaseRecovery(n("42.125"));
        eq(recovered.plate(0).current(), n(".125"), "only explicit actual recovery can restore broken share");
        eq(recovered.plate(1).current(), n("30"), "recovery cannot overfill healthy plate");
        eq(recovered.plate(2).current(), n("12"), "recovery has no second plate bonus budget");
        invariant(recovered, n("42.125"), n("90"));
        var full = before.afterBaseRecovery(n("90"));
        for (int i = 0; i < 3; i++) eq(full.plate(i).current(), n("30"), "actual recovery reaches each exact capacity");
        invariant(full.afterBaseSpend(ZERO), ZERO, n("90"));
        var grown = before.withMaximum(n("90.3"));
        for (int i = 0; i < 3; i++) eq(grown.plate(i).current(), before.plate(i).current(), "q/capacity growth preserves damaged current per plate");
        invariant(grown, n("42"), n("90.3"));
        var huge = before.withMaximum(CAP);
        invariant(huge, n("42"), CAP);
        eq(huge.plate(0).current(), ZERO, "enormous capacity growth cannot revive broken plate");
        fails(() -> before.withMaximum(n("89.999")), "lower capacity rejects occupied individual plate although aggregate S fits");
        fails(() -> before.withMaximum(n("42")), "aggregate fit cannot relocate shield between plates");
        var sparse = ScatterPlateShares.restore(n("6"), n("90"), List.of(ZERO, n("2"), n("4")));
        var lowered = sparse.withMaximum(n("12"));
        invariant(lowered, n("6"), n("12"));
        for (int i = 0; i < 3; i++) eq(lowered.plate(i).current(), sparse.plate(i).current(), "valid lower capacity changes no current");
        invariant(ScatterPlateShares.initialize(ZERO, n("90")).withMaximum(ZERO), ZERO, ZERO);
        fails(() -> before.afterBaseSpend(n("42.001")), "spend rejects increase");
        fails(() -> before.afterBaseRecovery(n("41.999")), "recovery rejects decrease");
        fails(() -> before.afterBaseRecovery(n("90.001")), "actual gain cannot exceed total deficit");
        fails(() -> before.afterBaseSpend(n("-.001")), "spend cannot make negative base shield");
    }

    private static void persistenceAndCorruption() {
        List<BigDecimal> input = new ArrayList<>(List.of(ZERO, n("30"), n("12")));
        var shares = ScatterPlateShares.restore(n("42"), n("90"), input);
        input.set(0, n("30"));
        eq(shares.plate(0).current(), ZERO, "saved caller list cannot mutate share state");
        try { shares.plates().clear(); throw new AssertionError("Mutable plate list"); }
        catch (UnsupportedOperationException expected) { checks++; }
        try { shares.toDecimalMap().clear(); throw new AssertionError("Mutable save map"); }
        catch (UnsupportedOperationException expected) { checks++; }
        for (int reload = 0; reload < 100; reload++) {
            shares = ScatterPlateShares.fromDecimalMap(shares.toDecimalMap()).requireMatches(n("42"), n("90"));
            eq(shares.plate(0).current(), ZERO, "reload/toggle/relearn reconstruction preserves broken plate");
            eq(shares.plate(1).current(), n("30"), "reload cannot repartition healthy plate");
            eq(shares.plate(2).current(), n("12"), "reload cannot refill partial plate");
        }
        var saved = shares.toDecimalMap();
        for (String missing : saved.keySet()) {
            Map<String, String> corrupt = new HashMap<>(saved);
            corrupt.remove(missing);
            fails(() -> ScatterPlateShares.fromDecimalMap(corrupt), "missing persisted field " + missing);
        }
        for (String[] change : new String[][]{{"version", "2"}, {"version", ""}, {"shield", "43"}, {"shield", "-1"},
                {"maxShield", "89"}, {"maxShield", "1e21"}, {"maxShield", "0"}, {"plate0", "1"},
                {"plate1", "30.0001"}, {"plate2", "-1"}, {"plate2", "NaN"}, {"plate2", "Infinity"},
                {"plate2", "1e-257"}, {"plate2", "1e-2147483648"}, {"plate2", "1e2147483647"},
                {"plate2", "0".repeat(513)}, {"plate2", "0." + "0".repeat(255) + "1".repeat(257)}}) {
            Map<String, String> corrupt = new HashMap<>(saved);
            corrupt.put(change[0], change[1]);
            fails(() -> ScatterPlateShares.fromDecimalMap(corrupt), "corrupt persisted field " + change[0]);
        }
        fails(() -> ScatterPlateShares.restore(n("42"), n("90"), List.of(ZERO, n("30"))), "short restore");
        fails(() -> ScatterPlateShares.restore(n("42"), n("90"), List.of(ZERO, n("30"), n("12"), ZERO)), "long restore");
        fails(() -> ScatterPlateShares.restore(n("41"), n("90"), List.of(ZERO, n("30"), n("12"))), "mismatched authoritative restore");
        fails(() -> ScatterPlateShares.restore(n("42"), n("90"), List.of(n("31"), ZERO, n("11"))), "individual overflow even when total fits");
        fails(() -> ScatterPlateShares.restore(n("42"), n("90"), List.of(n("-1"), n("30"), n("13"))), "negative current even when total fits");
        fails(() -> ScatterPlateShares.initialize(n("91"), n("90")), "overfull initialization");
        fails(() -> ScatterPlateShares.initialize(ZERO, CAP.add(TINY)), "over-cap maximum");
        fails(() -> ScatterPlateShares.initialize(n("1e-257"), n("90")), "below supported scale");
        fails(() -> ScatterPlateShares.initialize(ZERO, new BigDecimal(BigInteger.ONE, -257)), "unsupported negative scale");
        fails(() -> ScatterPlateShares.initialize(new BigDecimal("1." + "0".repeat(512)), n("90")), "excessive precision before normalization");
        var stable = shares;
        fails(() -> stable.requireMatches(n("41"), n("90")), "external base current mismatch");
        fails(() -> stable.requireMatches(n("42"), n("91")), "external maximum mismatch");
        fails(() -> stable.plate(-1), "negative accessor index");
        fails(() -> stable.plate(3), "past-last accessor index");
        fails(() -> stable.contact(-1, ZERO, ZERO), "invalid contact index even for no-op");
        fails(() -> stable.contact(3, ZERO, ZERO), "past-last contact index even for no-op");
        fails(() -> stable.contact(0, n("-1"), CAP), "negative incoming on broken plate");
        fails(() -> stable.contact(0, ZERO, CAP.add(TINY)), "over-cap ceiling even for no-op");
        fails(() -> stable.contact(0, CAP.add(TINY), ZERO), "over-cap incoming even for no-op");
        fails(() -> stable.contact(0, ZERO, n("-1")), "negative ceiling even for no-op");
        fails(() -> stable.withMaximum(n("-1")), "negative capacity");
        nullFails(() -> ScatterPlateShares.fromDecimalMap(null), "save map");
        nullFails(() -> ScatterPlateShares.restore(ZERO, ZERO, null), "saved currents");
        nullFails(() -> ScatterPlateShares.initialize(null, ZERO), "base shield");
        nullFails(() -> stable.contact(0, null, ZERO), "contact incoming");
        nullFails(() -> stable.contact(0, ZERO, null), "contact ceiling");
        invariant(stable, n("42"), n("90"));
    }

    private static void repeatedSequences() {
        Random random = new Random(212);
        for (int scenario = 0; scenario < 120; scenario++) {
            BigDecimal maximum = scenario % 3 == 0 ? CAP : scenario % 3 == 1 ? n("1000.000001") : TINY.multiply(n("1000"));
            BigDecimal quantum = scenario % 3 == 2 ? TINY : n(".000001");
            BigDecimal expected = scenario % 2 == 0 ? maximum : ZERO;
            var shares = ScatterPlateShares.initialize(expected, maximum);
            for (int step = 0; step < 100; step++) {
                var before = shares;
                BigDecimal amount = quantum.multiply(BigDecimal.valueOf(random.nextInt(250)));
                switch (random.nextInt(6)) {
                    case 0, 1 -> {
                        int index = random.nextInt(3);
                        BigDecimal incoming = step % 7 == 0 ? CAP : amount;
                        BigDecimal ceiling = step % 11 == 0 ? ZERO : step % 3 == 0 ? CAP : quantum.multiply(BigDecimal.valueOf(random.nextInt(300)));
                        var hit = shares.contact(index, incoming, ceiling);
                        BigDecimal actualDebit = before.plate(index).current().subtract(hit.shares().plate(index).current());
                        eq(actualDebit, hit.absorbed(), "reported contact absorption equals exact plate debit");
                        eq(hit.absorbed().add(hit.residual()), incoming, "incoming partitions exactly into absorbed and residual");
                        check(hit.absorbed().compareTo(ceiling) <= 0 && hit.absorbed().compareTo(incoming) <= 0, "contact obeys both independent ceilings");
                        for (int i = 0; i < 3; i++) if (i != index)
                            eq(hit.shares().plate(i).current(), before.plate(i).current(), "repeated contacts never borrow untouched shares");
                        expected = expected.subtract(hit.absorbed());
                        shares = hit.shares();
                    }
                    case 2 -> {
                        BigDecimal decrease = step % 13 == 0 ? expected : amount.min(expected);
                        expected = expected.subtract(decrease);
                        shares = shares.afterBaseSpend(expected);
                        for (int i = 0; i < 3; i++) check(shares.plate(i).current().compareTo(before.plate(i).current()) <= 0,
                                "ordinary spend only decreases each plate in repeated sequences");
                    }
                    case 3 -> {
                        BigDecimal gain = amount.min(maximum.subtract(expected));
                        expected = expected.add(gain);
                        shares = shares.afterBaseRecovery(expected);
                        eq(shares.shield().subtract(before.shield()), gain, "one actual recovery gain with no bonus budget");
                        for (int i = 0; i < 3; i++) check(shares.plate(i).current().compareTo(before.plate(i).current()) >= 0,
                                "recovery never relocates already owned shield");
                    }
                    case 4 -> {
                        maximum = maximum.add(amount.min(CAP.subtract(maximum)));
                        shares = shares.withMaximum(maximum);
                        for (int i = 0; i < 3; i++) eq(shares.plate(i).current(), before.plate(i).current(), "repeated q-growth never restores plate balance");
                    }
                    case 5 -> {
                        shares = ScatterPlateShares.fromDecimalMap(shares.toDecimalMap());
                        same(shares, before, "repeated persistence reconstruction");
                    }
                    default -> throw new AssertionError("Unreachable operation");
                }
                invariant(shares, expected, maximum);
                same(before, ScatterPlateShares.fromDecimalMap(before.toDecimalMap()), "previous immutable value survives operation");
            }
        }
    }
}
