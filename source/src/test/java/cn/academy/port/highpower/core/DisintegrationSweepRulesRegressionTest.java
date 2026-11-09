package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.List;

import static cn.academy.port.highpower.core.DisintegrationSweepRules.*;

/** Standalone regression for NEW231's whole-sweep allocation and release-time cost snapshot. */
public final class DisintegrationSweepRulesRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        costs(); sharesAreFrozenExact(); clocks(); malformed();
        System.out.println("Disintegration sweep rules: " + checks + " checks passed");
    }
    private static void costs() {
        String[] cost = {"10", "2", ".1"}, cd = {"40", "10", "1"}, mine = {".2", ".05", ".005"};
        int[] mineCd = {6, 4, 1};
        for (int level = 3; level <= 5; level++) {
            BigDecimal q = BigDecimal.valueOf(level);
            eq(theoreticalAttackCost(q), decimal(cost[level - 3]), "exact attack CP anchor");
            eq(theoreticalCooldown(q), decimal(cd[level - 3]), "exact attack cooldown anchor");
            check(miningCost(q) == Double.parseDouble(mine[level - 3]), "existing mining cost anchor reused");
            check(miningCooldown(q) == mineCd[level - 3], "existing mining cooldown anchor reused");
        }
        BigDecimal previousCost = theoreticalAttackCost(decimal("3")), previousCd = theoreticalCooldown(decimal("3"));
        for (int i = 1; i <= 200; i++) {
            BigDecimal q = decimal("3").add(BigDecimal.valueOf(i, 2));
            BigDecimal cp = theoreticalAttackCost(q), cdValue = theoreticalCooldown(q);
            check(cp.compareTo(previousCost) < 0, "CP decreases continuously inside unlocked domain");
            check(cdValue.compareTo(previousCd) < 0, "theoretical cooldown decreases continuously");
            check(cooldown(q) == ContinuousHighPowerGrowth.ceilCooldownTicks(cdValue), "cooldown rounds once only at engine tick boundary");
            check(miningCost(q) == BeamEtchingRules.cost(q) && miningCooldown(q) == BeamEtchingRules.cooldown(q), "mining curve remains exactly NEW213");
            previousCost = cp; previousCd = cdValue;
        }
        BigDecimal nearFour = decimal("3.999999999999999999999999999999999999");
        check(theoreticalAttackCost(nearFour).compareTo(decimal("2")) > 0, "tiny progress does not quantize CP prematurely");
        Paid paid = freeze(decimal("3.4"), decimal("123.456"));
        eq(paid.wholeDamage(), decimal("246.912"), "one release freezes exact whole 2D");
        BigDecimal before = paid.attackCost();
        freeze(decimal("5"), CombatAmounts.CAP);
        eq(paid.attackCost(), before, "later growth snapshot cannot mutate earlier paid cost");
        eq(paid.position(), decimal("3.4"), "release growth position preserved");
        eq(damage(CombatAmounts.CAP), CombatAmounts.CAP, "whole-sweep damage caps before partition");
        eq(damage(decimal("5e19")), CombatAmounts.CAP, "exact cap boundary");
        eq(damage(decimal("1e-256")), decimal("2e-256"), "small admitted damage stays decimal");
        // Native LV3 learning remains the controller's gate. Effective continuous q can fall
        // after an approved transition/rollback; freezing must use that current ledger position.
        for (BigDecimal q : List.of(BigDecimal.ONE, decimal("2.5"))) {
            BigDecimal currentD = ContinuousHighPowerGrowth.sample(q).damage();
            Paid lower = freeze(q, currentD);
            eq(lower.position(), q, "lower effective growth position is not recertified to LV3");
            eq(lower.attackCost(), BigDecimal.TEN, "lower repeated cost anchors stay on the approved plateau");
            eq(lower.theoreticalCooldown(), decimal("40"), "lower repeated cooldown anchors stay on the approved plateau");
            eq(lower.wholeDamage(), currentD.multiply(BigDecimal.TWO), "rollback damage uses the actual current D without an artificial LV3 jump");
            check(lower.wholeDamage().compareTo(damage(ContinuousHighPowerGrowth.sample(decimal("3")).damage())) < 0,
                    "native unlock never grants the larger LV3 continuous damage checkpoint");
        }
        Paid belowThree = freeze(decimal("2.999999"), ContinuousHighPowerGrowth.sample(decimal("2.999999")).damage());
        Paid atThree = freeze(decimal("3"), ContinuousHighPowerGrowth.sample(decimal("3")).damage());
        Paid aboveThree = freeze(decimal("3.000001"), ContinuousHighPowerGrowth.sample(decimal("3.000001")).damage());
        check(belowThree.wholeDamage().compareTo(atThree.wholeDamage()) < 0 && aboveThree.wholeDamage().compareTo(atThree.wholeDamage()) > 0,
                "damage changes continuously through q3 without recertification");
        check(aboveThree.wholeDamage().subtract(belowThree.wholeDamage()).compareTo(atThree.wholeDamage().multiply(decimal(".0001"))) < 0,
                "near-q3 snapshots do not introduce a damage discontinuity");
        eq(belowThree.attackCost(), atThree.attackCost(), "q3 boundary retains repeated lower CP anchor");
    }
    private static void sharesAreFrozenExact() {
        List<BigDecimal> totals = List.of(BigDecimal.ZERO, BigDecimal.ONE, decimal("246.912"),
                decimal("1e-256"), decimal("7e-256"), decimal("123456789.987654321"), CombatAmounts.CAP);
        for (BigDecimal total : totals) for (int count = 1; count <= MAX_TARGETS; count++) {
            List<BigDecimal> shares = shares(total, count);
            check(shares.size() == count, "exact frozen target count");
            BigDecimal sum = BigDecimal.ZERO;
            for (int i = 0; i < shares.size(); i++) {
                BigDecimal share = shares.get(i);
                check(share.signum() >= 0 && share.compareTo(total) <= 0, "no individual allocation exceeds whole budget");
                check(share.scale() <= CombatAmounts.MAX_ABSOLUTE_SCALE, "bounded decimal remainder scale");
                if (i < count - 1) eq(share, shares.getFirst(), "equal finite decimal quotient before deterministic remainder");
                sum = sum.add(share);
            }
            eq(sum, total, "all allocations exactly conserve min(2D,CAP)");
            try { shares.set(0, BigDecimal.ZERO); throw new AssertionError("mutable allocation"); }
            catch (UnsupportedOperationException expected) { checks++; }
        }
        check(shares(BigDecimal.TEN, 0).isEmpty(), "empty target set never fabricates damage");
        List<BigDecimal> thirds = shares(BigDecimal.ONE, 3);
        check(thirds.get(2).compareTo(thirds.get(0)) > 0, "last deterministic identity retains exact decimal remainder");
        BigDecimal originalSecond = thirds.get(1), originalThird = thirds.get(2);
        // A blocked first identity is discarded. Remaining shares are retained, never recomputed.
        eq(thirds.get(1), originalSecond, "blocked-target share is not reassigned to second identity");
        eq(thirds.get(2), originalThird, "blocked-target share is not reassigned to remainder identity");
    }
    private static void clocks() {
        check(CHARGE_TICKS == 8 && SWEEP_TICKS == 10 && HOLD_TICKS == 100, "approved timing limits");
        check(MAX_TARGETS == 8 && SCAN_LIMIT == 64 && MAX_CELLS == 16 && ATTEMPTS_PER_TICK == 2, "bounded native target and mining work");
        check(!charged(100, 107) && charged(100, 108) && charged(100, 199) && !charged(100, 200), "eight-tick minimum and hundred-tick hold expiry");
        check(!charged(100, 99) && !clock(-1) && !clock(Long.MAX_VALUE), "regressing and overflowing clock refused");
        check(!expired(100, 109, 10) && expired(100, 110, 10), "ten real ticks exactly once");
        check(ready(-1, 100, 1) && !ready(100, 100, 1) && ready(100, 101, 1), "same-tick duplicate cannot refill work");
        check(angleAt(0) == -DisintegrationSweepGeometry.HALF_ANGLE && angleAt(10) == DisintegrationSweepGeometry.HALF_ANGLE, "sweep spans exact fifty degrees");
        for (int i = 0; i < SWEEP_TICKS; i++) check(angleAt(i) < angleAt(i + 1), "each tick advances one continuous adjacent angular interval");
    }
    private static void malformed() {
        invalid(() -> shares(BigDecimal.ONE, 9)); invalid(() -> shares(BigDecimal.ONE, -1));
        invalid(() -> damage(BigDecimal.ZERO)); invalid(() -> damage(decimal("1e21")));
        invalid(() -> freeze(decimal(".999"), BigDecimal.ONE));
        invalid(() -> freeze(decimal("5.1"), BigDecimal.ONE));
        invalid(() -> angleAt(-1)); invalid(() -> angleAt(11));
    }
    private static BigDecimal decimal(String value) { return new BigDecimal(value); }
    private static void eq(BigDecimal actual, BigDecimal expected, String why) { check(actual.compareTo(expected) == 0, why + ": " + actual + " versus " + expected); }
    private static void invalid(Runnable action) { checks++; try { action.run(); } catch (IllegalArgumentException expected) { return; } throw new AssertionError("invalid input accepted"); }
    private static void check(boolean okay, String why) { checks++; if (!okay) throw new AssertionError(why); }
}
