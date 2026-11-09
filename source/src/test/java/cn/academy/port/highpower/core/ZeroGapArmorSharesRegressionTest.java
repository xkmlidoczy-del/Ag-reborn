package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** Dependency-free witnesses for NEW252's twelve exact components and existing parent identities. */
public final class ZeroGapArmorSharesRegressionTest {
    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final BigDecimal CAP = CombatAmounts.CAP;
    private static final BigDecimal Q = new BigDecimal("1e-256");
    private static int checks;

    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal q(long units) { return Q.multiply(BigDecimal.valueOf(units)); }
    private static void check(boolean condition, String why) { checks++; if (!condition) throw new AssertionError(why); }
    private static void eq(BigDecimal actual, BigDecimal expected, String why) {
        check(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static void fails(Runnable action, String why) {
        try { action.run(); throw new AssertionError("Accepted invalid input: " + why); }
        catch (IllegalArgumentException expected) { checks++; }
    }
    private static void nullFails(Runnable action, String why) {
        try { action.run(); throw new AssertionError("Accepted null: " + why); }
        catch (NullPointerException expected) { checks++; }
    }
    private static ExactCombatState base(BigDecimal current, BigDecimal maximum) {
        return new ExactCombatState(n("71.125"), n("100"), current, maximum, false);
    }
    private static ExactCombatState withShield(ExactCombatState state, BigDecimal current) {
        return new ExactCombatState(state.health(), state.maxHealth(), current, state.maxShield(), state.shieldEnabled());
    }
    private static ZeroGapArmorShares initial(String current, String maximum, String lower, String upper) {
        ExactCombatState base = base(n(current), n(maximum));
        return ZeroGapArmorShares.initialize(base, ScatterPlateShares.initialize(base.shield(), base.maxShield()),
                new SharedShieldReserveShares(n(lower), n(upper)));
    }
    private static ZeroGapArmorShares uneven() {
        var base = base(n("28"), n("60"));
        var scatter = ScatterPlateShares.restore(base.shield(), base.maxShield(), List.of(n("10"), n("10"), n("8")));
        var reserves = new SharedShieldReserveShares(n("13"), n("16"));
        return ZeroGapArmorShares.restore(base, scatter, reserves,
                List.of(ZERO, n("10"), n("3"), n("7"), n("8"), ZERO),
                List.of(ZERO, n("10"), n("5"), ZERO, n("8"), n("6")));
    }
    private static List<BigDecimal> currents(ZeroGapArmorShares armor, boolean reserve) {
        List<BigDecimal> result = new ArrayList<>();
        for (var plate : armor.plates()) result.add(reserve ? plate.reserveCurrent() : plate.baseCurrent());
        return result;
    }
    private static void same(ZeroGapArmorShares actual, ZeroGapArmorShares expected, String why) {
        check(actual.base().equals(expected.base()), why + " base snapshot");
        check(actual.reserves().equals(expected.reserves()), why + " reserve snapshot");
        check(actual.toDecimalMap().equals(expected.toDecimalMap()), why + " exact persisted components/parents");
        for (int i = 0; i < 6; i++) check(actual.plate(i).equals(expected.plate(i)), why + " plate " + i);
    }
    private static void invariant(ZeroGapArmorShares armor) {
        BigDecimal total = ZERO, capacity = ZERO, lower = ZERO, upper = ZERO;
        check(armor.plates().size() == 6, "exactly six plates");
        for (int i = 0; i < 6; i++) {
            var plate = armor.plate(i);
            total = total.add(plate.current()); capacity = capacity.add(plate.capacity());
            check(plate.baseCurrent().signum() >= 0 && plate.baseCurrent().compareTo(plate.baseCapacity()) <= 0, "base component bounds");
            check(plate.reserveCurrent().signum() >= 0 && plate.reserveCurrent().compareTo(plate.reserveCapacity()) <= 0, "reserve component bounds");
            eq(CombatAmounts.parse(CombatAmounts.decimalString(plate.baseCurrent()), "base"), plate.baseCurrent(), "strict saved base component");
            eq(CombatAmounts.parse(CombatAmounts.decimalString(plate.reserveCurrent()), "reserve"), plate.reserveCurrent(), "strict saved reserve component");
            if (i % 2 == 0) lower = lower.add(plate.reserveCurrent()); else upper = upper.add(plate.reserveCurrent());
        }
        eq(total, armor.total(), "all currents sum to existing aggregate");
        eq(total, armor.base().shield().add(armor.reserves().total()), "no new current pool");
        eq(capacity, armor.capacity(), "all capacities sum exactly");
        eq(capacity, armor.base().maxShield().multiply(n("2")), "six plates own exactly base + two halves");
        check(total.compareTo(CAP) <= 0 && capacity.compareTo(CAP) <= 0, "global 1e20 boundary");
        eq(lower, armor.reserves().lower(), "lower identity"); eq(upper, armor.reserves().upper(), "upper identity");
        BigDecimal lowerCapacity = ZERO, upperCapacity = ZERO;
        for (int i = 0; i < 3; i++) {
            eq(armor.plate(2 * i).baseCurrent().add(armor.plate(2 * i + 1).baseCurrent()), armor.scatter().plate(i).current(), "NEW212 current identity " + i);
            eq(armor.plate(2 * i).baseCapacity().add(armor.plate(2 * i + 1).baseCapacity()), armor.scatter().plate(i).capacity(), "NEW212 capacity identity " + i);
            lowerCapacity = lowerCapacity.add(armor.plate(2 * i).reserveCapacity());
            upperCapacity = upperCapacity.add(armor.plate(2 * i + 1).reserveCapacity());
        }
        eq(lowerCapacity, SharedShieldReserveShares.halfCapacity(armor.base()), "exact lower mathematical ceiling");
        eq(upperCapacity, SharedShieldReserveShares.halfCapacity(armor.base()), "exact upper mathematical ceiling");
        check(armor.requireMatches(armor.base(), armor.scatter(), armor.reserves()) == armor, "matching snapshot is unchanged");
    }

    public static void main(String[] args) {
        quantumPartitionAndMigration();
        parentIdentityAndOrdinaryChanges();
        contactAccounting();
        categoryResidualSettlement();
        persistenceAndCorruption();
        growthAndGlobalCap();
        randomizedSequences();
        System.out.println("PASS " + checks + " NEW252 six-plate exact partition/parent/contact checks");
    }

    private static void quantumPartitionAndMigration() {
        for (int maximum = 0; maximum <= 30; maximum++) {
            for (int current = 0; current <= maximum; current++) {
                var base = base(q(current), q(maximum));
                var scatter = ScatterPlateShares.initialize(base.shield(), base.maxShield());
                for (int reserveUnits = 0; reserveUnits <= maximum / 2; reserveUnits++) {
                    var reserves = new SharedShieldReserveShares(q(reserveUnits), q(maximum / 2 - reserveUnits));
                    var armor = ZeroGapArmorShares.initialize(base, scatter, reserves);
                    invariant(armor);
                    for (int i = 0; i < 3; i++) for (int child = 0; child < 2; child++) {
                        int index = 2 * i + child;
                        eq(armor.plate(index).baseCurrent(), integerOracle(scatter.plate(i).current(), 2, child), "integer base partition oracle");
                        eq(armor.plate(index).baseCapacity(), integerOracle(scatter.plate(i).capacity(), 2, child), "integer base ceiling oracle");
                    }
                    for (int i = 0; i < 3; i++) {
                        eq(armor.plate(2 * i).reserveCurrent(), integerOracle(reserves.lower(), 3, i), "integer lower partition oracle");
                        eq(armor.plate(2 * i + 1).reserveCurrent(), integerOracle(reserves.upper(), 3, i), "integer upper partition oracle");
                        eq(armor.plate(2 * i).reserveCapacity(), integerOracle(SharedShieldReserveShares.halfCapacity(base), 3, i), "half-quantum mathematical ceiling oracle");
                    }
                    same(ZeroGapArmorShares.fromDecimalMap(base, scatter, reserves, armor.toDecimalMap()), armor, "quantum reload");
                }
            }
        }
        // Every 1..9 quantum parent balance is independently split, never pooled with another parent.
        for (int units = 1; units <= 9; units++) {
            var base = base(q(units * 2L), q(60));
            var scatter = ScatterPlateShares.restore(base.shield(), base.maxShield(), List.of(q(units), ZERO, q(units)));
            var armor = ZeroGapArmorShares.initialize(base, scatter, new SharedShieldReserveShares(q(units), q(9 - units)));
            eq(armor.plate(2).baseCurrent(), ZERO, "depleted middle parent is never refilled by initialization");
            eq(armor.plate(3).baseCurrent(), ZERO, "both middle children stay depleted");
            invariant(armor);
        }
        var half = ZeroGapArmorShares.initialize(base(ZERO, Q), ScatterPlateShares.initialize(ZERO, Q), SharedShieldReserveShares.ZERO);
        eq(half.plate(0).reserveCapacity(), Q.divide(n("2")), "odd maximum retains exact half quantum on first reserve child");
        eq(half.total(), ZERO, "mathematical ceilings never award a fractional current");
        var legacyBase = base(n("12"), n("60"));
        var scatter = ScatterPlateShares.restore(legacyBase.shield(), legacyBase.maxShield(), List.of(ZERO, n("12"), ZERO));
        var legacyReserve = SharedShieldReserveShares.fromDecimalMap(legacyBase,
                Map.of("version", "1", "capacity", "60", "amount", "39.125"));
        var migrated = ZeroGapArmorShares.initialize(legacyBase, scatter, legacyReserve);
        eq(migrated.total(), n("51.125"), "one-time initialization retains existing depleted legacy balances");
        eq(migrated.plate(0).baseCurrent(), ZERO, "legacy depleted NEW212 parent preserved");
        invariant(migrated);
    }

    /** Independent integer arithmetic, including any half-quantum ceiling on the next child. */
    private static BigDecimal integerOracle(BigDecimal total, int count, int index) {
        BigDecimal quantumUnits = total.movePointRight(256);
        BigInteger wholeUnits = quantumUnits.toBigInteger();
        BigInteger[] division = wholeUnits.divideAndRemainder(BigInteger.valueOf(count));
        int remainder = division[1].intValueExact();
        BigDecimal result = new BigDecimal(division[0].add(index < remainder ? BigInteger.ONE : BigInteger.ZERO), 256);
        return index == remainder ? result.add(quantumUnits.subtract(new BigDecimal(wholeUnits)).multiply(Q)) : result;
    }

    private static void parentIdentityAndOrdinaryChanges() {
        var before = uneven();
        var saved = before.toDecimalMap();
        for (int repeat = 0; repeat < 1000; repeat++) {
            var noop = before.reconcileParents(before.base().withShieldEnabled(false), before.scatter(), before.reserves());
            check(noop.before() == before && noop.after() == before, "zero actual deltas preserve exact object");
            check(noop.brokenPlateIds().isEmpty(), "repeated no-op never repeats broken notification");
        }
        var spentScatter = ScatterPlateShares.restore(n("22"), n("60"), List.of(n("10"), n("4"), n("8")));
        var spent = before.reconcileParents(withShield(before.base(), n("22")), spentScatter, before.reserves());
        eq(spent.after().plate(2).baseCurrent(), ZERO, "ordinary B1 spend consumes earlier child first");
        eq(spent.after().plate(3).baseCurrent(), n("4"), "ordinary B1 remaining spend only reaches sibling");
        eq(spent.after().plate(1).baseCurrent(), n("10"), "B0 untouched by B1 change");
        eq(spent.after().plate(4).baseCurrent(), n("8"), "B2 untouched by B1 change");
        check(spent.brokenPlateIds().isEmpty(), "zero base component is not a broken plate while reserve survives");
        var recoveredScatter = ScatterPlateShares.restore(n("28.125"), n("60"), List.of(n("10"), n("10.125"), n("8")));
        var recovered = before.reconcileParents(withShield(before.base(), n("28.125")), recoveredScatter, before.reserves()).after();
        eq(recovered.plate(2).baseCurrent(), n("3.125"), "only B1 actual credit recovers B1 child");
        eq(recovered.plate(0).current(), ZERO, "unrelated broken plate cannot heal");
        var lowerSpent = before.reconcileParents(before.base(), before.scatter(), new SharedShieldReserveShares(n("5"), n("16"))).after();
        eq(lowerSpent.plate(2).reserveCurrent(), ZERO, "lower spend skips empty child0 then drains child2");
        eq(lowerSpent.plate(4).reserveCurrent(), n("5"), "lower spend remainder reaches child4");
        eq(lowerSpent.plate(1).reserveCurrent(), n("10"), "lower spend never touches upper");
        var upperSpent = before.reconcileParents(before.base(), before.scatter(), new SharedShieldReserveShares(n("13"), n("9"))).after();
        eq(upperSpent.plate(1).reserveCurrent(), n("3"), "upper spend stable order begins child1");
        eq(upperSpent.plate(5).reserveCurrent(), n("6"), "upper later child not redistributed");
        var lowerRecovered = before.reconcileParents(before.base(), before.scatter(), new SharedShieldReserveShares(n("13.125"), n("16"))).after();
        eq(lowerRecovered.plate(0).reserveCurrent(), n(".125"), "only actual lower credit revives lower child0");
        eq(lowerRecovered.plate(2).reserveCurrent(), n("5"), "no extra lower recovery budget");
        var upperRecovered = before.reconcileParents(before.base(), before.scatter(), new SharedShieldReserveShares(n("13"), n("18"))).after();
        eq(upperRecovered.plate(3).reserveCurrent(), n("2"), "upper recovery skips full child1 then fills child3");
        eq(upperRecovered.plate(0).current(), ZERO, "upper recovery does not revive lower child");
        // Equal aggregate totals do not erase real parent-identity changes.
        var swappedScatter = ScatterPlateShares.restore(n("28"), n("60"), List.of(n("9"), n("11"), n("8")));
        var swapped = before.reconcileParents(before.base(), swappedScatter, new SharedShieldReserveShares(n("12"), n("17"))).after();
        eq(swapped.plate(1).baseCurrent(), n("9"), "same aggregate base still debits correct parent");
        eq(swapped.plate(2).baseCurrent(), n("4"), "same aggregate base credits only correct parent");
        eq(swapped.plate(2).reserveCurrent(), n("4"), "same aggregate reserve still debits lower");
        eq(swapped.plate(3).reserveCurrent(), n("1"), "same aggregate reserve credits upper");
        var emptied = before.reconcileParents(withShield(before.base(), ZERO), ScatterPlateShares.initialize(ZERO, n("60")), SharedShieldReserveShares.ZERO);
        check(emptied.brokenPlateIds().equals(List.of(1, 2, 3, 4, 5)), "ordinary spends report only positive-to-zero total plates");
        eq(emptied.after().total(), ZERO, "ordinary full spend is exact");
        check(saved.equals(before.toDecimalMap()), "all proposals leave before snapshot unchanged");
        for (var result : List.of(spent.after(), recovered, lowerSpent, upperSpent, lowerRecovered, upperRecovered, swapped, emptied.after())) invariant(result);
    }

    private static void contactAccounting() {
        var before = uneven();
        var partial = before.contact(1, n("8.5"), n("2.125"), n("3.25"));
        eq(partial.baseAbsorbed(), n("2.125"), "independent captured base limit");
        eq(partial.reserveAbsorbed(), n("3.25"), "independent captured reserve limit");
        eq(partial.residual(), n("3.125"), "exact residual after both limited components");
        eq(partial.base().shield(), n("25.875"), "base parent debited only by base absorption");
        eq(partial.scatter().plate(0).current(), n("7.875"), "contact debits existing B0 parent");
        eq(partial.reserves().lower(), n("13"), "odd contact cannot spend lower");
        eq(partial.reserves().upper(), n("12.75"), "odd contact spends only upper");
        eq(partial.base().health(), before.base().health(), "proposal never applies residual to HP");
        check(partial.base().shieldEnabled() == before.base().shieldEnabled(), "proposal preserves base toggle");
        for (int i = 0; i < 6; i++) if (i != 1) check(partial.shares().plate(i).equals(before.plate(i)), "contact never borrows from other plates");
        var onlyBase = before.contact(1, n("15"), CAP, ZERO);
        eq(onlyBase.absorbed(), n("10"), "reserve cannot borrow unused base ceiling");
        eq(onlyBase.residual(), n("5"), "zero reserve ceiling retains residual");
        check(onlyBase.brokenPlateIds().isEmpty(), "base exhausted but reserve survives");
        var onlyReserve = before.contact(1, n("15"), ZERO, CAP);
        eq(onlyReserve.baseAbsorbed(), ZERO, "reserve ceiling cannot authorize base spend");
        eq(onlyReserve.reserveAbsorbed(), n("10"), "independently authorized reserve can pay while base ceiling is zero");
        var baseFirst = before.contact(1, n("3.125"), CAP, CAP);
        eq(baseFirst.baseAbsorbed(), n("3.125"), "actual post-mitigation amount bounds base-first payment");
        eq(baseFirst.reserveAbsorbed(), ZERO, "base-first prevents unnecessary reserve debit");
        var all = before.contact(1, CAP, CAP, CAP);
        eq(all.absorbed(), n("20"), "one plate only at huge incoming");
        eq(all.residual(), CAP.subtract(n("20")), "final residual retains exact capped amount");
        check(all.brokenPlateIds().equals(List.of(1)), "one exhausted contacted plate emits one ID");
        check(all.transaction().before() == before, "transaction retains exact before snapshot");
        var again = all.shares().contact(1, Q, CAP, CAP);
        check(again.shares() == all.shares() && again.brokenPlateIds().isEmpty(), "broken plate does not break again");
        eq(again.residual(), Q, "smallest residual survives broken contact");
        check(before.contact(0, CAP, CAP, CAP).shares() == before, "empty plate cannot borrow");
        check(before.contact(1, ZERO, CAP, CAP).shares() == before, "zero actual incoming cannot spend");
        check(before.contact(1, CAP, ZERO, ZERO).shares() == before, "zero independent ceilings cannot spend");
        var lowerHit = before.contact(2, n("9"), CAP, CAP);
        eq(lowerHit.reserves().lower(), n("8"), "even contact spends only assigned lower component");
        eq(lowerHit.reserves().upper(), n("16"), "even contact never touches upper");
        eq(lowerHit.residual(), n("1"), "no second plate may pay excess");
        check(lowerHit.brokenPlateIds().equals(List.of(2)), "combined component exhaustion breaks total plate");
        var dead = new ExactCombatState(ZERO, n("100"), before.base().shield(), before.base().maxShield(), true);
        var deadArmor = before.reconcileParents(dead, before.scatter(), before.reserves()).after();
        check(deadArmor.contact(1, CAP, CAP, CAP).shares() == deadArmor, "dead contact pays nothing");
        for (var contact : List.of(partial, onlyBase, onlyReserve, baseFirst, all, again, lowerHit)) {
            invariant(contact.shares());
            eq(contact.transaction().before().total().subtract(contact.shares().total()), contact.absorbed(), "exact 1:1 absorption conservation");
        }
    }

    private static void categoryResidualSettlement() {
        var before=initial("6","60","0","0");
        var contact=before.contact(4,n("5"),CAP,CAP);
        var legacy=contact.finishResidual(false);var category=contact.finishResidual(true);
        eq(contact.absorbed(),n("1"),"original authenticated local absorption is unchanged");
        eq(contact.residual(),n("4"),"original contact keeps its own residual and identity");
        eq(legacy.after().base().health(),before.base().health().subtract(n("4")),"disabled category shield retains native residual HP loss");
        eq(legacy.after().base().shield(),n("5"),"disabled category shield cannot spend siblings");
        eq(category.after().base().health(),before.base().health(),"category shield catches residual before HP");
        eq(category.after().base().shield(),n("1"),"exactly five total existing shield points pay one five-point hit");
        check(contact.brokenPlateIds().equals(List.of(4)),"original contact has only its own exhausted child");
        check(category.brokenPlateIds().equals(List.of(0,1,2,3,4)),"final transaction includes every additionally exhausted sibling for persisted break debt");
        check(category.before()==before,"final authorization retains exact original before object");
        eq(before.base().shield().subtract(category.after().base().shield()),n("5"),"one conserved total debit");
        invariant(category.after());
        same(contact.shares(),before.contact(4,n("5"),CAP,CAP).shares(),"finishing never mutates original authenticated proposal");
        try{category.brokenPlateIds().clear();throw new AssertionError("Mutable final break IDs");}catch(UnsupportedOperationException expected){checks++;}
        var reserveBefore=initial("6","60","3","3");var reserveHit=reserveBefore.contact(4,n("5"),CAP,CAP);
        var finalReserve=reserveHit.finishResidual(true).after();
        eq(finalReserve.reserves().total(),reserveHit.reserves().total(),"category fallback never borrows reserve siblings");
        eq(finalReserve.base().health(),reserveBefore.base().health(),"funded residual does not remove HP");
        eq(reserveBefore.total().subtract(finalReserve.total()),n("5"),"base plus authenticated local reserve conserve one hit");
        for(int id=0;id<6;id++)if(id!=4)eq(finalReserve.plate(id).reserveCurrent(),reserveBefore.plate(id).reserveCurrent(),"other reserve component stays exact "+id);
        invariant(finalReserve);
        var quantum=ZeroGapArmorShares.initialize(base(q(6),q(60)),ScatterPlateShares.initialize(q(6),q(60)),SharedShieldReserveShares.ZERO);
        var tiny=quantum.contact(4,q(5),CAP,CAP).finishResidual(true);
        eq(tiny.after().base().shield(),Q,"minimum-quantum residual is not rounded into free absorption");
        check(tiny.brokenPlateIds().equals(List.of(0,1,2,3,4)),"quantum boundary preserves all exact broken IDs");
        var huge=initial("50000000000000000000","50000000000000000000","0","0");
        var lethal=huge.contact(0,CAP,CAP,CAP).finishResidual(true);
        eq(lethal.after().base().shield(),ZERO,"cap-sized hit exhausts finite base exactly");
        eq(lethal.after().base().health(),ZERO,"cap-sized residual stays lethal after shield instead of losing clipped overkill");
        check(lethal.brokenPlateIds().size()==6,"maximum hit records every depleted child");invariant(lethal.after());
        var zero=before.contact(4,ZERO,CAP,CAP).finishResidual(true);same(zero.after(),before,"zero hit has no healing or phantom debit");
    }

    private static void persistenceAndCorruption() {
        var original = uneven();
        var armor = original;
        for (int reload = 0; reload < 100; reload++) {
            armor = ZeroGapArmorShares.fromDecimalMap(armor.base(), armor.scatter(), armor.reserves(), armor.toDecimalMap());
            same(armor, original, "reload/toggle/relearn retain every component");
        }
        var saved = armor.toDecimalMap();
        for (String missing : saved.keySet()) {
            var corrupt = new HashMap<>(saved); corrupt.remove(missing);
            fails(() -> decode(original, corrupt), "missing field " + missing);
            var nullField = new HashMap<>(saved); nullField.put(missing, null);
            fails(() -> decode(original, nullField), "null field " + missing);
        }
        for (String[] mutation : new String[][]{{"version", "0"}, {"version", "2"}, {"shield", "29"}, {"maxShield", "61"},
                {"scatter0", "9"}, {"scatterMax0", "21"}, {"lower", "14"}, {"upper", "15"}, {"base0", "1"},
                {"base1", "11"}, {"base2", "-1"}, {"reserve0", "1"}, {"reserve1", "11"}, {"reserve2", "-1"},
                {"reserve5", "NaN"}, {"reserve5", "Infinity"}, {"reserve5", "1e21"}, {"reserve5", "1e-257"},
                {"reserve5", "1e2147483647"}, {"reserve5", "1e-2147483648"}, {"reserve5", "0".repeat(513)}}) {
            var corrupt = new HashMap<>(saved); corrupt.put(mutation[0], mutation[1]);
            fails(() -> decode(original, corrupt), "malformed/mismatched field " + mutation[0]);
        }
        var extra = new HashMap<>(saved); extra.put("bonus", "1");
        fails(() -> decode(original, extra), "unknown field rejected under strict schema");
        fails(() -> decode(original, Map.of()), "missing armor is not one-time initialization");
        var shiftedScatter = ScatterPlateShares.restore(n("28"), n("60"), List.of(n("9"), n("11"), n("8")));
        fails(() -> ZeroGapArmorShares.fromDecimalMap(original.base(), shiftedScatter, original.reserves(), saved), "same-total NEW212 parent mismatch");
        fails(() -> ZeroGapArmorShares.fromDecimalMap(original.base(), original.scatter(), new SharedShieldReserveShares(n("12"), n("17")), saved), "same-total lower/upper mismatch");
        fails(() -> original.requireMatches(original.base(), shiftedScatter, original.reserves()), "before snapshot compares every NEW212 parent");
        fails(() -> original.requireMatches(original.base().withShieldEnabled(true), original.scatter(), original.reserves()), "before snapshot includes shield toggle");
        var baseCurrents = currents(original, false); var reserveCurrents = currents(original, true);
        var restored = ZeroGapArmorShares.restore(original.base(), original.scatter(), original.reserves(), baseCurrents, reserveCurrents);
        baseCurrents.set(1, ZERO); reserveCurrents.clear(); same(restored, original, "caller lists cannot mutate state");
        try { restored.plates().clear(); throw new AssertionError("Mutable plates"); } catch (UnsupportedOperationException expected) { checks++; }
        try { saved.clear(); throw new AssertionError("Mutable save"); } catch (UnsupportedOperationException expected) { checks++; }
        try { original.contact(1, CAP, CAP, CAP).brokenPlateIds().clear(); throw new AssertionError("Mutable broken IDs"); } catch (UnsupportedOperationException expected) { checks++; }
        fails(() -> ZeroGapArmorShares.restore(original.base(), original.scatter(), original.reserves(), List.of(ZERO), currents(original, true)), "short current list");
        var overfull = currents(original, false); overfull.set(0, n("11")); overfull.set(1, n("-1"));
        fails(() -> ZeroGapArmorShares.restore(original.base(), original.scatter(), original.reserves(), overfull, currents(original, true)), "component invalid despite parent sum");
        var wrongParent = ScatterPlateShares.initialize(n("29"), n("60"));
        fails(() -> ZeroGapArmorShares.initialize(original.base(), wrongParent, original.reserves()), "initialization rejects mismatched base/scatter");
        for (int index : new int[]{-1, 6}) {
            fails(() -> original.plate(index), "invalid accessor");
            fails(() -> original.contact(index, ZERO, ZERO, ZERO), "invalid contact index even on no-op");
        }
        for (BigDecimal invalid : List.of(n("-1"), CAP.add(Q), n("1e-257"))) {
            fails(() -> original.contact(0, invalid, ZERO, ZERO), "invalid incoming even on broken plate");
            fails(() -> original.contact(0, ZERO, invalid, ZERO), "invalid base ceiling on no-op");
            fails(() -> original.contact(0, ZERO, ZERO, invalid), "invalid reserve ceiling on no-op");
        }
        nullFails(() -> ZeroGapArmorShares.fromDecimalMap(original.base(), original.scatter(), original.reserves(), null), "null save");
        nullFails(() -> ZeroGapArmorShares.restore(original.base(), original.scatter(), original.reserves(), null, currents(original, true)), "null currents");
        nullFails(() -> original.contact(0, null, ZERO, ZERO), "null incoming");
        fails(() -> new ZeroGapArmorShares.Transaction(original, original, List.of(1)), "forged broken metadata rejected");
        invariant(original);
    }

    private static ZeroGapArmorShares decode(ZeroGapArmorShares authority, Map<String, String> saved) {
        return ZeroGapArmorShares.fromDecimalMap(authority.base(), authority.scatter(), authority.reserves(), saved);
    }

    private static void growthAndGlobalCap() {
        var before = uneven();
        var grownBase = before.base().withMaximums(n("120"), n("120"));
        var grown = before.reconcileParents(grownBase, before.scatter().withMaximum(n("120")), before.reserves()).after();
        for (int i = 0; i < 6; i++) {
            eq(grown.plate(i).baseCurrent(), before.plate(i).baseCurrent(), "growth preserves each base current");
            eq(grown.plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "growth preserves each reserve current");
        }
        eq(grown.plate(0).current(), ZERO, "growth cannot revive broken plate");
        var quantum = initial("0", "0", "0", "0");
        for (int units = 1; units <= 120; units++) {
            var newBase = quantum.base().withMaximums(quantum.base().maxHealth(), q(units));
            var newScatter = quantum.scatter().withMaximum(q(units));
            var expanded = quantum.reconcileParents(newBase, newScatter, quantum.reserves()).after();
            for (int i = 0; i < 6; i++) {
                check(expanded.plate(i).baseCapacity().compareTo(quantum.plate(i).baseCapacity()) >= 0, "base child capacities monotone at quantum boundaries");
                check(expanded.plate(i).reserveCapacity().compareTo(quantum.plate(i).reserveCapacity()) >= 0, "reserve child capacities monotone including odd halves");
                eq(expanded.plate(i).current(), quantum.plate(i).current(), "quantum growth changes ceilings only");
            }
            var fullBase = withShield(newBase, q(units));
            var fullReserves = new SharedShieldReserveShares(q(units / 2), q(units / 2));
            quantum = expanded.reconcileParents(fullBase, newScatter.afterBaseRecovery(q(units)), fullReserves).after();
            invariant(quantum);
            eq(quantum.total(), q(units + 2L * (units / 2)), "odd-half recovery only credits representable parent balance");
        }
        var cap = initial("50000000000000000000", "50000000000000000000", "25000000000000000000", "25000000000000000000");
        invariant(cap); eq(cap.total(), CAP, "exact aggregate cap supported");
        for (int i = 0; i < 6; i++) {
            var tiny = cap.contact(i, Q, CAP, CAP);
            eq(tiny.shares().total(), CAP.subtract(Q), "smallest quantum debit survives 1e20 aggregate");
            eq(tiny.residual(), ZERO, "small contact exact residual");
            var huge = cap.contact(i, CAP, CAP, CAP);
            eq(huge.absorbed(), cap.plate(i).current(), "global cap hit only spends one plate");
            eq(huge.residual(), CAP.subtract(cap.plate(i).current()), "global cap residual exact");
            check(huge.residual().compareTo(CAP) <= 0, "final residual cap preserved");
            invariant(huge.shares());
        }
        fails(() -> initial("0", "50000000000000000000.0001", "0", "0"), "aggregate capacity above 1e20 rejected even with empty current");
        var sparse = initial("6", "60", "0", "0");
        var shrunk = sparse.reconcileParents(sparse.base().withMaximums(n("100"), n("12")), sparse.scatter().withMaximum(n("12")), sparse.reserves()).after();
        eq(shrunk.total(), n("6"), "valid contraction never clips or redistributes"); invariant(shrunk);
        var unevenBase = base(n("8"), n("60"));
        var unevenScatter = ScatterPlateShares.restore(n("8"), n("60"), List.of(n("8"), ZERO, ZERO));
        var concentrated = ZeroGapArmorShares.restore(unevenBase, unevenScatter, SharedShieldReserveShares.ZERO,
                List.of(n("8"), ZERO, ZERO, ZERO, ZERO, ZERO), List.of(ZERO, ZERO, ZERO, ZERO, ZERO, ZERO));
        fails(() -> concentrated.reconcileParents(unevenBase.withMaximums(n("100"), n("30")), unevenScatter.withMaximum(n("30")), SharedShieldReserveShares.ZERO),
                "parent fits contraction but concentrated child cannot be silently moved");
    }

    private static void randomizedSequences() {
        Random random = new Random(252212152242L);
        for (int scenario = 0; scenario < 24; scenario++) {
            BigDecimal maximum = scenario % 3 == 0 ? q(101) : scenario % 3 == 1 ? n("60") : CAP.divide(n("2"));
            BigDecimal unit = scenario % 3 == 0 ? Q : n(".001");
            BigDecimal half = maximum.divide(n("2")).setScale(256, RoundingMode.DOWN).stripTrailingZeros();
            var base = base(maximum, maximum);
            var armor = ZeroGapArmorShares.initialize(base, ScatterPlateShares.initialize(maximum, maximum), new SharedShieldReserveShares(half, half));
            for (int step = 0; step < 150; step++) {
                var before = armor;
                var savedBefore = before.toDecimalMap();
                BigDecimal amount = unit.multiply(BigDecimal.valueOf(random.nextInt(60)));
                switch (random.nextInt(6)) {
                    case 0, 1 -> {
                        int index = random.nextInt(6);
                        BigDecimal incoming = step % 13 == 0 ? CAP : amount;
                        BigDecimal baseCeiling = step % 7 == 0 ? ZERO : unit.multiply(BigDecimal.valueOf(random.nextInt(80)));
                        BigDecimal reserveCeiling = step % 11 == 0 ? ZERO : unit.multiply(BigDecimal.valueOf(random.nextInt(80)));
                        var hit = before.contact(index, incoming, baseCeiling, reserveCeiling);
                        BigDecimal expectedBase = incoming.min(baseCeiling).min(before.plate(index).baseCurrent());
                        BigDecimal expectedReserve = incoming.subtract(expectedBase).min(reserveCeiling).min(before.plate(index).reserveCurrent());
                        eq(hit.baseAbsorbed(), expectedBase, "random independent base limit");
                        eq(hit.reserveAbsorbed(), expectedReserve, "random independent reserve limit");
                        eq(hit.absorbed().add(hit.residual()), incoming, "random contact incoming conservation");
                        eq(before.total().subtract(hit.shares().total()), hit.absorbed(), "random exact 1:1 debit");
                        for (int i = 0; i < 6; i++) if (i != index) check(before.plate(i).equals(hit.shares().plate(i)), "random untouched plate isolation");
                        armor = hit.shares();
                    }
                    case 2 -> {
                        BigDecimal current = before.base().shield().subtract(amount.min(before.base().shield()));
                        var nextScatter = before.scatter().afterBaseSpend(current);
                        armor = before.reconcileParents(withShield(before.base(), current), nextScatter, before.reserves()).after();
                        for (int i = 0; i < 6; i++) {
                            check(armor.plate(i).baseCurrent().compareTo(before.plate(i).baseCurrent()) <= 0, "random ordinary base spend never credits a child");
                            eq(armor.plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "random base spend retains reserve identity");
                        }
                    }
                    case 3 -> {
                        BigDecimal current = before.base().shield().add(amount.min(maximum.subtract(before.base().shield())));
                        armor = before.reconcileParents(withShield(before.base(), current), before.scatter().afterBaseRecovery(current), before.reserves()).after();
                        eq(armor.total().subtract(before.total()), current.subtract(before.base().shield()), "random one-budget base recovery");
                    }
                    case 4 -> {
                        boolean lower = random.nextBoolean(), recovery = random.nextBoolean();
                        BigDecimal old = lower ? before.reserves().lower() : before.reserves().upper();
                        BigDecimal next = recovery ? old.add(amount.min(half.subtract(old))) : old.subtract(amount.min(old));
                        var reserves = new SharedShieldReserveShares(lower ? next : before.reserves().lower(), lower ? before.reserves().upper() : next);
                        armor = before.reconcileParents(before.base(), before.scatter(), reserves).after();
                        for (int i = 0; i < 6; i++) {
                            eq(armor.plate(i).baseCurrent(), before.plate(i).baseCurrent(), "random reserve change cannot touch base");
                            if ((i % 2 == 0) != lower) eq(armor.plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "random reserve change respects parity parent");
                        }
                    }
                    case 5 -> armor = decode(before, before.toDecimalMap());
                    default -> throw new AssertionError("Unreachable");
                }
                check(before.toDecimalMap().equals(savedBefore), "random operation cannot mutate before proposal");
                invariant(armor);
                same(decode(armor, armor.toDecimalMap()), armor, "random exact reload");
                check(armor.reconcileParents(armor.base(), armor.scatter(), armor.reserves()).after() == armor, "random no-op never heals");
            }
        }
    }
}
