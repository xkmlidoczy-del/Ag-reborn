package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static cn.academy.port.highpower.core.InertialAnchorRules.*;

/** Independent exact-payment, stale-proposal, fall and existing-child accounting checks for NEW422. */
public final class InertialAnchorRulesRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        knockbackTransactions();
        nativeStrengthBoundaries();
        staleSnapshots();
        fallTransactions();
        existingChildAccounting();
        frontalSector();
        physicalTimeAndEconomics();
        System.out.println("InertialAnchorRulesRegressionTest passed " + checks + " checks");
    }

    private static void knockbackTransactions() {
        ExactCombatState before = state("13", "100", "7.125", "100", false);
        KnockbackPlan plan = planKnockback(before, .8);
        check(plan != null, "sufficient existing base S admits a native reduction");
        equal(plan.fee(), d("1"), "fee is one percent of maximum base S");
        equal(plan.state().shield(), d("6.125"), "fee is one complete exact debit");
        equal(plan.state().health(), before.health(), "knockback fee never damages HP");
        check(plan.before() == before, "proposal retains original immutable evidence");
        check(plan.originalStrength() == .8 && plan.reducedStrength() == .4,
                "proposal halves this native call's base strength");
        check(plan.state().maxHealth().equals(before.maxHealth())
                && plan.state().maxShield().equals(before.maxShield())
                && plan.state().shieldEnabled() == before.shieldEnabled(), "payment preserves capacities and shield toggle");
        equal(before.shield(), d("7.125"), "planning does not spend the original snapshot in place");

        ExactCombatState exact = state("13", "100", "1.25", "125", true);
        KnockbackPlan exhaustion = planKnockback(exact, 1);
        check(exhaustion != null && exhaustion.state().shield().signum() == 0,
                "a balance exactly equal to the fee is admitted and exhausted");
        equal(exhaustion.fee(), d("1.25"), "decimal percentage stays exact");
        check(exhaustion.state().shieldEnabled(), "knockback does not toggle generic shielding");
        check(planKnockback(exhaustion.state(), 1) == null, "exhausted base S never grants another reduction");

        ExactCombatState shortByQuantum = state("13", "100", "1.249999999999999999999999999999", "125", false);
        Map<String, String> saved = shortByQuantum.toDecimalMap();
        check(planKnockback(shortByQuantum, 1) == null, "fee cannot be paid partially or rounded down");
        check(shortByQuantum.toDecimalMap().equals(saved), "insufficient funds leave the entire state unchanged");
        check(planKnockback(state("13", "100", "0", "125", false), 1) == null,
                "empty base S cannot borrow a reserve or use HP");
        check(planKnockback(state("13", "100", "0", "0", false), 1) == null,
                "zero capacity cannot create a free positive admission");
        check(planKnockback(state("0", "100", "125", "125", true), 1) == null,
                "a dead combatant cannot pay for a reduction");

        BigDecimal quantum = d("1e-256");
        ExactCombatState tiny = new ExactCombatState(BigDecimal.ONE, BigDecimal.ONE, quantum, d("1e-254"), false);
        KnockbackPlan tinyPlan = planKnockback(tiny, 1);
        check(tinyPlan != null, "smallest supported exact positive fee admits");
        equal(tinyPlan.fee(), quantum, "smallest supported fee is not rounded away");
        equal(tinyPlan.state().shield(), BigDecimal.ZERO, "smallest supported fee exactly exhausts S");
        for (String max : new String[] {"1e-256", "9e-256", "1e-255", "9.999e-253"}) {
            ExactCombatState unrepresentable = new ExactCombatState(BigDecimal.ONE, BigDecimal.ONE,
                    d(max), d(max), false);
            check(planKnockback(unrepresentable, 1) == null,
                    "an exact percentage outside the ledger quantum refuses without rounding");
            equal(unrepresentable.shield(), d(max), "unsupported fee cannot consume any S");
        }

        ExactCombatState maximum = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP,
                CombatAmounts.CAP, CombatAmounts.CAP, false);
        ExactCombatState running = maximum;
        for (int i = 0; i < 100; i++) {
            KnockbackPlan next = planKnockback(running, Double.MAX_VALUE);
            check(next != null, "full cap supports exactly one hundred one-percent fees");
            equal(next.fee(), d("1e18"), "cap fee remains exact without binary amount conversion");
            running = next.state();
            equal(running.shield(), CombatAmounts.CAP.subtract(d("1e18").multiply(BigDecimal.valueOf(i + 1))),
                    "successive cap debits conserve every exact unit");
        }
        check(running.shield().signum() == 0 && planKnockback(running, 1) == null,
                "one hundred full fees exhaust the cap without an activation fill");
        equal(running.health(), CombatAmounts.CAP, "fees never spill into HP");
        equal(knockbackFee(BigDecimal.ZERO), BigDecimal.ZERO, "zero maximum has an exact zero mathematical fee");
        rejects(() -> knockbackFee(d("-.01")), "negative maximum rejects");
        rejects(() -> knockbackFee(d("100000000000000000001")), "over-cap maximum rejects");
        rejects(() -> knockbackFee(d("1e-257")), "unsupported maximum scale rejects");
        rejects(() -> knockbackFee(d("1e-256")), "unsupported exact fee scale rejects");
        rejects(() -> knockbackFee(null), "missing maximum rejects");
        rejects(() -> planKnockback(null, 1), "missing combat snapshot rejects");
    }

    private static void nativeStrengthBoundaries() {
        ExactCombatState funded = state("100", "100", "100", "100", false);
        for (double invalid : new double[] {0, -0.0, -1, -Double.MIN_VALUE, Double.NaN,
                Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.MIN_VALUE, 3 * Double.MIN_VALUE}) {
            check(planKnockback(funded, invalid) == null,
                    "zero, invalid and nonrepresentable native half-strengths refuse before payment");
        }
        for (double original : new double[] {2 * Double.MIN_VALUE, 4 * Double.MIN_VALUE,
                Double.MIN_NORMAL, Math.nextUp(Double.MIN_NORMAL), 1, .4, Double.MAX_VALUE}) {
            KnockbackPlan plan = planKnockback(funded, original);
            boolean exactHalf = original * .5 > 0 && original * .5 * 2 == original;
            check((plan != null) == exactHalf, "subnormal boundary admits only an exact positive half");
            if (plan != null) {
                check(Double.isFinite(plan.reducedStrength()) && plan.reducedStrength() > 0,
                        "admitted native strength stays finite and positive");
                check(plan.reducedStrength() * 2 == original, "half-strength round trips exactly");
            }
        }
        Random random = new Random(422);
        for (int i = 0; i < 2_000; i++) {
            double original = Double.longBitsToDouble(random.nextLong() & Long.MAX_VALUE);
            KnockbackPlan plan = planKnockback(funded, original);
            BigDecimal represented = Double.isFinite(original) ? new BigDecimal(original) : null;
            boolean expected = original > 0 && represented != null && original * .5 > 0
                    && new BigDecimal(original * .5).multiply(d("2")).compareTo(represented) == 0;
            check((plan != null) == expected, "binary strength admission matches exact represented-value arithmetic");
            if (plan != null) equal(plan.fee(), BigDecimal.ONE, "strength magnitude never changes the exact fee");
        }
        equal(funded.shield(), d("100"), "all native strength probes leave the immutable input unchanged");
    }

    private static void staleSnapshots() {
        ExactCombatState before = state("90", "100", "70", "100", false);
        KnockbackPlan outer = planKnockback(before, .4);
        ExactCombatState reread = ExactCombatState.fromDecimalMap(before.toDecimalMap());
        check(before != reread && outer.matches(reread, .4), "equal reconstructed ledger snapshots remain valid");
        check(!outer.matches(null, .4), "missing current snapshot refuses commit");
        check(!outer.matches(before, Math.nextUp(.4)), "one-ULP native strength change invalidates the proposal");
        check(!outer.matches(before, Double.NaN) && !outer.matches(before, Double.POSITIVE_INFINITY),
                "nonfinite replacement strength invalidates the proposal");
        for (ExactCombatState changed : new ExactCombatState[] {
                state("89", "100", "70", "100", false), state("90", "101", "70", "100", false),
                state("90", "100", "69", "100", false), state("90", "100", "71", "100", false),
                state("90", "100", "70", "101", false), before.withShieldEnabled(true)}) {
            check(!outer.matches(changed, .4), "every ledger field participates in the snapshot witness");
        }
        KnockbackPlan nested = planKnockback(reread, .8);
        check(!outer.matches(nested.state(), .4), "nested successful spend invalidates an outer stale plan");
        check(!outer.matches(absorbFall(before, BigDecimal.ONE).state(), .4),
                "nested fall spend invalidates an outer stale plan");
        check(!outer.matches(outer.state(), .4), "already committed payment cannot be committed again against its after state");
        KnockbackPlan replanned = planKnockback(nested.state(), .4);
        check(replanned.matches(nested.state(), .4), "a fresh proposal can use the new exact ledger balance");
        equal(replanned.state().shield(), d("68"), "nested and fresh proposals pay two fees without lost updates");
    }

    private static void fallTransactions() {
        ExactCombatState before = state("100", "1000", "7.125", "100", true);
        Result result = absorbFall(before, d("10.375"));
        equal(result.absorbed(), d("7.125"), "fall uses all available base S one-for-one");
        equal(result.state().shield(), BigDecimal.ZERO, "fall exactly exhausts base S");
        equal(result.state().health(), d("96.75"), "fall applies only the exact HP residual once");
        check(!result.state().shieldEnabled(), "result cannot enter a second generic shield pass");
        check(result.state().maxHealth().equals(before.maxHealth())
                && result.state().maxShield().equals(before.maxShield()), "fall never expands or fills capacities");
        equal(before.shield(), d("7.125"), "fall plan never mutates the input balance");
        equal(before.health(), d("100"), "fall plan never mutates the input HP");
        Result disabled = absorbFall(before.withShieldEnabled(false), d("10.375"));
        check(disabled.equals(result), "active fall path uses base S independently of generic shield toggle");
        Result zero = absorbFall(before, BigDecimal.ZERO);
        equal(zero.absorbed(), BigDecimal.ZERO, "amount zero never creates an admission");
        equal(zero.state().health(), before.health(), "amount zero cannot damage HP");
        equal(zero.state().shield(), before.shield(), "amount zero cannot spend or fill S");
        check(!zero.state().shieldEnabled(), "zero amount cannot reenable generic shielding");
        Result dead = absorbFall(state("0", "100", "50", "100", true), d("100"));
        check(dead.state().isDead() && dead.absorbed().signum() == 0, "death remains authoritative");
        equal(dead.state().shield(), d("50"), "dead fall cannot debit or refill S");
        Result lethal = absorbFall(state(".25", "100", ".125", "100", false), d("100"));
        check(lethal.state().isDead(), "lethal fall clamps HP at zero");
        equal(lethal.absorbed(), d(".125"), "overkill cannot overstate paid absorption");

        String[] samples = {"0", "1e-256", "3e-256", "1e-30", ".1", "7.125",
                "123456789012345.678901234567890123456789", "99999999999999999999.99999999999999999999", "1e20"};
        for (String shield : samples) for (String damage : samples) {
            ExactCombatState input = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP,
                    d(shield), CombatAmounts.CAP, false);
            Result paid = absorbFall(input, d(damage));
            BigDecimal baseLoss = input.shield().subtract(paid.state().shield());
            BigDecimal hpLoss = input.health().subtract(paid.state().health());
            equal(paid.absorbed(), baseLoss, "reported fall absorption equals actual existing base debit");
            equal(baseLoss, d(damage).min(d(shield)), "fall consumes existing base S before HP");
            equal(baseLoss.add(hpLoss), d(damage), "fall conserves every exact mitigated unit through 1e20");
        }
        ExactCombatState cap = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP,
                CombatAmounts.CAP, CombatAmounts.CAP, false);
        Result first = absorbFall(cap, CombatAmounts.CAP);
        equal(first.state().health(), CombatAmounts.CAP, "full S cap absorbs a full damage cap");
        Result second = absorbFall(first.state(), CombatAmounts.CAP);
        check(second.state().isDead() && second.absorbed().signum() == 0,
                "next cap-sized fall spends HP without any stance refill");
        rejects(() -> absorbFall(before, d("-.01")), "negative fall damage rejects");
        rejects(() -> absorbFall(before, d("100000000000000000001")), "over-cap fall damage rejects");
        rejects(() -> absorbFall(before, d("1e-257")), "unsupported fall quantum rejects");
        rejects(() -> absorbFall(before, d("1e257")), "unbounded fall exponent rejects");
        rejects(() -> absorbFall(before, null), "missing fall amount rejects");
        rejects(() -> absorbFall(null, BigDecimal.ONE), "missing fall snapshot rejects");
    }

    private static void existingChildAccounting() {
        ExactCombatState base = state("90", "100", "18", "120", false);
        ScatterPlateShares scatter = ScatterPlateShares.restore(base.shield(), base.maxShield(), amounts("2", "7", "9"));
        SharedShieldReserveShares reserves = new SharedShieldReserveShares(d("8"), d("12"));
        ZeroGapArmorShares armor = ZeroGapArmorShares.restore(base, scatter, reserves,
                amounts("0", "2", "3", "4", "4", "5"), amounts("0", "2", "3", "4", "5", "6"));
        Map<String, String> originalScatter = scatter.toDecimalMap(), originalArmor = armor.toDecimalMap();
        Map<String, String> originalReserves = reserves.toDecimalMap(base);

        KnockbackPlan plan = planKnockback(base, 1);
        ScatterPlateShares nextScatter = scatter.afterBaseSpend(plan.state().shield());
        ZeroGapArmorShares after = armor.reconcileParents(plan.state(), nextScatter, reserves).after();
        after.requireMatches(plan.state(), nextScatter, reserves);
        equal(nextScatter.plate(0).current(), d(".8"), "ordinary fee reconciles only its actual base-parent decrease");
        equal(after.plate(0).baseCurrent(), BigDecimal.ZERO, "fee cannot refill an already exhausted child");
        equal(after.plate(1).baseCurrent(), d(".8"), "fee reaches the next existing child in stable order");
        for (int i = 2; i < 6; i++) {
            equal(after.plate(i).baseCurrent(), armor.plate(i).baseCurrent(),
                    "unaffected base parents retain every surviving child balance");
        }
        for (int i = 0; i < 6; i++) {
            equal(after.plate(i).reserveCurrent(), armor.plate(i).reserveCurrent(),
                    "base-only fee never borrows or reshuffles reserve children");
        }
        check(after.reserves().equals(reserves), "lower and upper reserve identities remain unchanged");

        Result fall = absorbFall(plan.state(), d("20"));
        ScatterPlateShares fallenScatter = nextScatter.afterBaseSpend(fall.state().shield());
        ZeroGapArmorShares fallen = after.reconcileParents(fall.state(), fallenScatter, reserves).after();
        fallen.requireMatches(fall.state(), fallenScatter, reserves);
        equal(fall.state().health(), d("86.8"), "fall residual reaches HP even while reserves still exist");
        for (int i = 0; i < 6; i++) {
            equal(fallen.plate(i).baseCurrent(), BigDecimal.ZERO, "exhausted base S exhausts existing base children only");
            equal(fallen.plate(i).reserveCurrent(), armor.plate(i).reserveCurrent(),
                    "fall cannot use reserve or heal/repartition its children");
        }
        check(planKnockback(fall.state(), 1) == null, "reserve balances cannot pay a later missing base fee");
        check(scatter.toDecimalMap().equals(originalScatter) && armor.toDecimalMap().equals(originalArmor)
                && reserves.toDecimalMap(base).equals(originalReserves), "original shared ledgers stay immutable");
        check(fallen.reconcileParents(fall.state(), fallenScatter, reserves).after() == fallen,
                "repeated unchanged parent reconciliation cannot mint children");
    }

    private static void frontalSector() {
        VectorParryRules.Point origin = p(0, 0, 0), look = p(0, 0, 1);
        double edge = Math.sqrt(3);
        for (double side : new double[] {-1, 1}) {
            check(front(origin, look, p(side * edge, 0, 1)), "represented inclusive sixty-degree boundary accepts");
            check(front(origin, look, p(side * Math.nextDown(edge), 0, 1)), "next inward point accepts");
            check(!front(origin, look, p(side * Math.nextUp(edge), 0, 1)), "next outward point rejects without epsilon");
        }
        check(front(origin, look, p(0, 1000, 1)), "vertical source offset does not shrink horizontal front");
        check(front(p(0, 0, -1), look, origin), "source at world origin is a valid real source");
        check(!front(origin, look, origin), "coincident source cannot invent an incoming direction");
        check(!front(origin, look, p(0, 0, -1)), "rear knockback is ineligible");
        check(!front(origin, look, p(1, 0, 0)), "side knockback is ineligible");
        check(front(origin, p(0, 0, Double.MIN_VALUE), p(0, 0, Double.MIN_VALUE)),
                "represented nonzero subnormal directions stay valid");
        for (VectorParryRules.Point invalid : new VectorParryRules.Point[] {null, p(Double.NaN, 0, 1),
                p(0, Double.NaN, 1), p(0, 0, Double.POSITIVE_INFINITY)}) {
            check(!front(invalid, look, look), "invalid owner rejects");
            check(!front(origin, invalid, look), "invalid facing rejects");
            check(!front(origin, look, invalid), "invalid source rejects");
        }
        Random random = new Random(422_120);
        for (int i = 0; i < 300; i++) {
            VectorParryRules.Point owner = p(random.nextDouble(), random.nextDouble(), random.nextDouble());
            VectorParryRules.Point facing = p(random.nextDouble() - .5, 1, random.nextDouble() - .5);
            VectorParryRules.Point source = p(random.nextDouble(), random.nextDouble(), random.nextDouble());
            check(front(owner, facing, source) == VectorParryRules.front(owner, facing, source),
                    "NEW422 retains NEW412's exact represented-vector rule");
        }
    }

    private static void physicalTimeAndEconomics() {
        check(MAX_HITS_PER_TICK == 4 && RESTART_FENCE == 4 && PAYMENT_PERIOD == 20,
                "provisional admissions, physical restart fence and sustain interval stay explicit");
        long latest = Long.MAX_VALUE - PAYMENT_PERIOD;
        check(clock(0) && clock(latest), "clock accepts physical ticks with scheduling headroom");
        check(!clock(-1) && !clock(latest + 1) && !clock(Long.MAX_VALUE), "invalid scheduling clocks reject");
        String[] activation = {"2", "1", ".4", ".1", ".02"};
        String[] sustain = {".25", ".125", ".05", ".0125", ".0025"};
        int[] cooldown = {10, 5, 3, 2, 1}, nativeCooldown = {10, 5, 4, 4, 4};
        for (int i = 0; i < 5; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            equal(theoreticalActivationCost(q), d(activation[i]), "exact provisional activation CP anchor");
            equal(theoreticalSustainCost(q), d(sustain[i]), "exact per-twenty-tick sustain CP anchor");
            equal(theoreticalCooldown(q), BigDecimal.valueOf(cooldown[i]), "exact theoretical cooldown anchor");
            check(activationCost(q) == Double.parseDouble(activation[i])
                    && sustainCost(q) == Double.parseDouble(sustain[i]), "native CP converts only at its boundary");
            check(cooldownTicks(q) == nativeCooldown[i], "physical restart fence remains four ticks");
        }
        BigDecimal previousActivation = d("2"), previousSustain = d(".25"), previousCooldown = d("10");
        int previousTicks = 10;
        for (int i = 101; i <= 500; i++) {
            BigDecimal q = BigDecimal.valueOf(i, 2);
            BigDecimal currentActivation = theoreticalActivationCost(q), currentSustain = theoreticalSustainCost(q);
            BigDecimal currentCooldown = theoreticalCooldown(q);
            check(currentActivation.signum() > 0 && currentActivation.compareTo(previousActivation) < 0,
                    "fractional activation CP decreases continuously");
            check(currentSustain.signum() > 0 && currentSustain.compareTo(previousSustain) < 0,
                    "fractional sustain CP decreases continuously");
            check(currentCooldown.signum() > 0 && currentCooldown.compareTo(previousCooldown) < 0,
                    "fractional theoretical cooldown decreases continuously");
            int ticks = cooldownTicks(q);
            check(ticks >= RESTART_FENCE && ticks <= previousTicks
                    && ticks == Math.max(RESTART_FENCE, ContinuousHighPowerGrowth.ceilCooldownTicks(currentCooldown)),
                    "native cooldown rounds upward once and retains its physical fence");
            previousActivation = currentActivation;
            previousSustain = currentSustain;
            previousCooldown = currentCooldown;
            previousTicks = ticks;
        }
        check(theoreticalActivationCost(d("3.000000001")).compareTo(d(".4")) < 0
                && theoreticalSustainCost(d("3.000000001")).compareTo(d(".05")) < 0,
                "growth below one percentage point is not quantized");
        equal(theoreticalActivationCost(BigDecimal.ZERO), d("2"), "low q clamps to the first anchor");
        equal(theoreticalSustainCost(BigDecimal.TEN), d(".0025"), "high q clamps to the final anchor");
        check(cooldownTicks(BigDecimal.TEN) == 4, "high q cannot bypass physical restart");
        rejects(() -> activationCost(d("1e257")), "unbounded growth exponent rejects");
        rejects(() -> sustainCost(d("1e-257")), "unsupported growth scale rejects");
        rejects(() -> cooldownTicks(null), "missing growth position rejects");
    }

    private static ExactCombatState state(String hp, String maxHp, String shield, String maxShield, boolean enabled) {
        return new ExactCombatState(d(hp), d(maxHp), d(shield), d(maxShield), enabled);
    }
    private static List<BigDecimal> amounts(String... values) {
        return java.util.Arrays.stream(values).map(InertialAnchorRulesRegressionTest::d).toList();
    }
    private static VectorParryRules.Point p(double x, double y, double z) { return new VectorParryRules.Point(x, y, z); }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static void equal(BigDecimal actual, BigDecimal expected, String reason) {
        check(actual.compareTo(expected) == 0, reason + ": " + actual + " != " + expected);
    }
    private static void rejects(Runnable action, String reason) {
        try { action.run(); throw new AssertionError(reason); }
        catch (IllegalArgumentException | NullPointerException expected) { checks++; }
    }
    private static void check(boolean condition, String reason) {
        checks++;
        if (!condition) throw new AssertionError(reason);
    }
}
