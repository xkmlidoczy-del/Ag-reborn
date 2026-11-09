package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.Random;

import static cn.academy.port.highpower.core.VectorParryRules.*;

/** Independent conservation, direction, cadence and continuous native-economics checks for NEW412. */
public final class VectorParryRulesRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        exactResources();
        frontalSector();
        turnDirections();
        physicalTime();
        economics();
        System.out.println("VectorParryRulesRegressionTest passed " + checks + " checks");
    }

    private static void exactResources() {
        ExactCombatState original = state("100", "1000", "7.125", "100", true);
        Result partial = absorb(original, d("10.375"));
        equal(partial.absorbed(), d("7.125"), "partial base S pays one-for-one");
        equal(partial.state().shield(), BigDecimal.ZERO, "partial absorption exhausts base S");
        equal(partial.state().health(), d("96.75"), "residual HP is applied exactly once");
        equal(original.shield(), d("7.125"), "immutable input is never spent in place");
        equal(original.health(), d("100"), "immutable input HP remains unchanged");
        check(!partial.state().shieldEnabled(), "generic shield pass remains disabled");
        check(partial.state().maxHealth().equals(original.maxHealth())
                && partial.state().maxShield().equals(original.maxShield()), "capacities never refill or expand");

        Result full = absorb(original, d(".125"));
        equal(full.absorbed(), d(".125"), "small fully absorbed hit charges exact damage");
        equal(full.state().shield(), d("7"), "fully absorbed hit debits only existing base S");
        equal(full.state().health(), original.health(), "full absorption does not touch HP");
        Result empty = absorb(state(".125", "100", "0", "100", true), d(".1"));
        equal(empty.absorbed(), BigDecimal.ZERO, "empty base S cannot create absorption");
        equal(empty.state().health(), d(".025"), "empty base S leaves exact residual HP damage");
        Result dead = absorb(state("0", "100", "50", "100", true), d("100"));
        equal(dead.absorbed(), BigDecimal.ZERO, "a dead combatant cannot absorb");
        equal(dead.state().shield(), d("50"), "death cannot spend or refill base S");
        check(dead.state().isDead() && !dead.state().shieldEnabled(), "death stays authoritative");
        Result lethal = absorb(state(".25", "100", ".125", "100", true), d("100"));
        equal(lethal.absorbed(), d(".125"), "overkill never overstates S absorption");
        check(lethal.state().isDead() && lethal.state().shield().signum() == 0, "lethal remainder clamps HP at zero");
        Result zero = absorb(original, BigDecimal.ZERO);
        equal(zero.state().health(), original.health(), "zero hit does not damage HP");
        equal(zero.state().shield(), original.shield(), "zero hit does not spend base S");
        equal(zero.absorbed(), BigDecimal.ZERO, "zero hit cannot authorize a turn");
        check(!zero.state().shieldEnabled(), "zero hit cannot reenable generic absorption");
        Result disabled = absorb(original.withShieldEnabled(false), d("10.375"));
        check(disabled.equals(partial), "active parry uses existing base S independently of generic shield flag");

        String[] amounts = {"0", "1e-256", "3e-256", "1e-30", ".1", "7.125",
                "123456789012345.678901234567890123456789", "99999999999999999999.99999999999999999999", "1e20"};
        for (String shieldText : amounts) {
            for (String damageText : amounts) {
                BigDecimal shield = d(shieldText), incoming = d(damageText);
                ExactCombatState before = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP,
                        shield, CombatAmounts.CAP, false);
                Result result = absorb(before, incoming);
                BigDecimal spent = before.shield().subtract(result.state().shield());
                BigDecimal healthLost = before.health().subtract(result.state().health());
                equal(result.absorbed(), spent, "one base-S debit equals the reported absorption");
                equal(spent.add(healthLost), incoming, "all exact mitigated damage is conserved up to 1e20");
                equal(spent, incoming.min(shield), "base S is consumed before HP");
                check(result.state().maxHealth().equals(before.maxHealth())
                        && result.state().maxShield().equals(before.maxShield()), "no reserve or capacity is minted");
            }
        }
        ExactCombatState cap = new ExactCombatState(CombatAmounts.CAP, CombatAmounts.CAP,
                CombatAmounts.CAP, CombatAmounts.CAP, true);
        Result first = absorb(cap, CombatAmounts.CAP);
        equal(first.state().health(), CombatAmounts.CAP, "1e20 S absorbs a full 1e20 hit");
        Result second = absorb(first.state(), CombatAmounts.CAP);
        check(second.state().isDead() && second.absorbed().signum() == 0, "next 1e20 hit spends HP with no refill");
        rejects(() -> absorb(original, d("-.01")), "negative mitigated damage rejects");
        rejects(() -> absorb(original, d("100000000000000000001")), "damage beyond the ledger cap rejects");
        rejects(() -> absorb(original, d("1e-257")), "unsupported fractional scale rejects");
        rejects(() -> absorb(original, d("1e257")), "unbounded exponent rejects");
        rejects(() -> absorb(null, BigDecimal.ONE), "missing combat state rejects");
        rejects(() -> absorb(original, null), "missing mitigated damage rejects");
    }

    private static void frontalSector() {
        Point origin = p(0, 0, 0), forward = p(0, 0, 1);
        check(front(origin, forward, p(0, 0, 1)), "a valid world origin is not an absent source");
        check(front(p(0, 0, -1), forward, origin), "source at absolute world origin remains valid");
        check(front(origin, forward, p(0, 1000, 1)), "vertical source offset does not narrow horizontal cone");
        check(front(origin, p(0, 1000, 1), p(0, 0, 1)), "pitched look still uses horizontal direction");
        check(!front(origin, forward, p(0, 0, -1)), "rear source rejects");
        check(!front(origin, forward, p(1, 0, 0)), "side source rejects");
        check(!front(origin, forward, origin), "coincident source cannot invent an incoming direction");
        check(!front(origin, forward, p(0, 1, 0)), "vertical-only source cannot invent horizontal direction");
        check(!front(origin, p(0, 1, 0), forward), "vertical-only look rejects");
        check(!front(origin, origin, forward), "zero look rejects");

        double boundary = Math.sqrt(3);
        for (double side : new double[] {-1, 1}) {
            check(front(origin, forward, p(side * boundary, 0, 1)), "represented inclusive 60-degree boundary");
            check(front(origin, forward, p(side * Math.nextDown(boundary), 0, 1)), "one-ULP inward boundary accepts");
            check(!front(origin, forward, p(side * Math.nextUp(boundary), 0, 1)), "one-ULP outward boundary rejects without epsilon");
        }
        Random random = new Random(412);
        for (int i = 0; i < 1000; i++) {
            double yaw = random.nextDouble() * 2 * Math.PI;
            Point look = p(Math.sin(yaw), random.nextDouble() * 2 - 1, Math.cos(yaw));
            Point translated = p(random.nextDouble() * 59_998_000 - 29_999_000,
                    random.nextDouble() * 512 - 256, random.nextDouble() * 59_998_000 - 29_999_000);
            for (double side : new double[] {-1, 1}) {
                double inside = yaw + side * (Math.PI / 3 - 1e-7);
                double outside = yaw + side * (Math.PI / 3 + 1e-7);
                check(front(origin, look, p(Math.sin(inside) * 64, 0, Math.cos(inside) * 64)),
                        "rotated inward angular epsilon accepts");
                check(!front(origin, look, p(Math.sin(outside) * 64, 0, Math.cos(outside) * 64)),
                        "rotated outward angular epsilon rejects");
                check(front(translated, look, p(translated.x() + Math.sin(inside) * 64,
                        translated.y(), translated.z() + Math.cos(inside) * 64)), "translation near world boundary preserves inside");
                check(!front(translated, look, p(translated.x() + Math.sin(outside) * 64,
                        translated.y(), translated.z() + Math.cos(outside) * 64)), "translation near world boundary preserves outside");
            }
        }
        Point edge = p(30_000_000, 320, -30_000_000);
        check(front(edge, p(-1, 0, 1), p(Math.nextDown(edge.x()), 320, Math.nextUp(edge.z()))),
                "nonzero displacement of one world-coordinate ULP is valid");
        check(front(origin, p(0, 0, Double.MIN_VALUE), p(0, 0, Double.MIN_VALUE)), "subnormal nonzero vectors remain valid");
        check(front(origin, p(Double.MAX_VALUE, 0, Double.MAX_VALUE), p(1, 0, 1)), "huge finite look normalizes safely");
        check(front(origin, forward, p(0, 0, Double.MAX_VALUE)), "huge finite source normalizes safely");
        check(!front(p(-Double.MAX_VALUE, 0, 0), p(1, 0, 0), p(Double.MAX_VALUE, 0, 0)),
                "overflowing displacement refuses instead of inventing a direction");
        for (Point invalid : new Point[] {null, p(Double.NaN, 0, 1), p(0, Double.NaN, 1),
                p(0, 0, Double.POSITIVE_INFINITY), p(Double.NEGATIVE_INFINITY, 0, 0)}) {
            check(!front(invalid, forward, forward), "invalid owner refuses");
            check(!front(origin, invalid, forward), "invalid look refuses");
            check(!front(origin, forward, invalid), "invalid source refuses");
        }
    }

    private static void turnDirections() {
        Point[] looks = {p(0, 0, 1), p(1, 0, 0), p(0, 0, -1), p(-1, 0, 0)};
        double[][] right = {{-1, 0}, {0, 1}, {1, 0}, {0, -1}};
        for (int i = 0; i < looks.length; i++) {
            for (boolean crouching : new boolean[] {false, true}) {
                double side = crouching ? -1 : 1;
                Horizontal impulse = desiredImpulse(looks[i], crouching);
                Horizontal ratios = knockbackRatios(looks[i], crouching);
                check(impulse.x() == right[i][0] * side && impulse.z() == right[i][1] * side,
                        "cardinal default-right/crouch-left impulse follows Minecraft coordinates");
                check(-ratios.x() == impulse.x() && -ratios.z() == impulse.z(),
                        "native subtract-ratio convention produces intended impulse");
            }
        }
        for (Point look : new Point[] {p(3, 4, 5), p(Double.MIN_VALUE, 1, Double.MIN_VALUE),
                p(Double.MAX_VALUE, -100, Double.MAX_VALUE)}) {
            Horizontal impulse = desiredImpulse(look, false), ratios = knockbackRatios(look, false);
            check(Double.isFinite(impulse.x()) && Double.isFinite(impulse.z()), "horizontal turn stays finite");
            near(Math.hypot(impulse.x(), impulse.z()), 1, "turn is normalized independently of pitch and scale");
            near(impulse.x(), -ratios.x(), "X native ratio has correct sign");
            near(impulse.z(), -ratios.z(), "Z native ratio has correct sign");
            Horizontal opposite = desiredImpulse(look, true);
            near(opposite.x(), -impulse.x(), "crouch reverses horizontal X");
            near(opposite.z(), -impulse.z(), "crouch reverses horizontal Z");
        }
        for (Point invalid : new Point[] {null, p(0, 0, 0), p(0, 1, 0), p(0, -1, 0),
                p(Double.NaN, 0, 1), p(0, Double.NaN, 1), p(1, 0, Double.POSITIVE_INFINITY)}) {
            check(desiredImpulse(invalid, false) == null && desiredImpulse(invalid, true) == null,
                    "invalid or vertical look cannot invent either lateral impulse");
            check(knockbackRatios(invalid, false) == null && knockbackRatios(invalid, true) == null,
                    "invalid or vertical look cannot invent native ratios");
        }
    }

    private static void physicalTime() {
        check(MAX_HITS_PER_TICK == 4 && RESTART_FENCE == 4 && PAYMENT_PERIOD == 20 && PRECISION_TICKS == 3,
                "provisional physical bounds remain explicit");
        check(precise(0, 0) && precise(0, 1) && precise(0, 2), "initial ages zero through two are precise");
        check(!precise(0, 3) && !precise(0, 20), "age three and later are ordinary hold");
        check(precise(100, 102) && !precise(100, 103), "precision is relative to physical hold start");
        check(!precise(100, 99), "clock rollback never renews precision");
        check(!precise(-1, 0) && !precise(0, -1), "negative clocks reject before subtraction");
        long maximum = Long.MAX_VALUE - PAYMENT_PERIOD;
        check(clock(0) && clock(maximum), "clock leaves room for one future payment");
        check(!clock(-1) && !clock(maximum + 1) && !clock(Long.MAX_VALUE), "overflow scheduling clocks reject");
        check(precise(maximum - 2, maximum) && !precise(maximum - 3, maximum), "precision remains correct near long limit");
        check(!precise(Long.MIN_VALUE, Long.MAX_VALUE) && !precise(Long.MAX_VALUE, Long.MIN_VALUE),
                "time overflow cannot wrap into the precision window");
        check(!precise(0, maximum), "long-running hold cannot wrap into precision");
    }

    private static void economics() {
        String[] activation = {"2", "1", ".4", ".1", ".02"};
        String[] sustain = {".25", ".125", ".05", ".0125", ".0025"};
        int[] theoreticalTicks = {10, 5, 3, 2, 1}, engineTicks = {10, 5, 4, 4, 4};
        for (int i = 0; i < 5; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            equal(theoreticalActivationCost(q), d(activation[i]), "exact activation CP anchor");
            equal(theoreticalSustainCost(q), d(sustain[i]), "exact per-twenty-tick sustain CP anchor");
            equal(theoreticalCooldown(q), BigDecimal.valueOf(theoreticalTicks[i]), "exact theoretical cooldown anchor");
            check(activationCost(q) == Double.parseDouble(activation[i])
                    && sustainCost(q) == Double.parseDouble(sustain[i]), "native CP conversion occurs at the boundary");
            check(cooldownTicks(q) == engineTicks[i], "cooldown cannot bypass the four-tick restart fence");
        }
        BigDecimal previousActivation = d("2"), previousSustain = d(".25"), previousCooldown = d("10");
        int previousTicks = 10;
        for (int i = 101; i <= 500; i++) {
            BigDecimal q = BigDecimal.valueOf(i, 2);
            BigDecimal activationNow = theoreticalActivationCost(q), sustainNow = theoreticalSustainCost(q);
            BigDecimal cooldownNow = theoreticalCooldown(q);
            check(activationNow.signum() > 0 && activationNow.compareTo(previousActivation) < 0,
                    "fractional activation cost continuously decreases");
            check(sustainNow.signum() > 0 && sustainNow.compareTo(previousSustain) < 0,
                    "fractional sustain cost continuously decreases");
            check(cooldownNow.signum() > 0 && cooldownNow.compareTo(previousCooldown) < 0,
                    "theoretical cooldown continuously decreases");
            check(activationNow.subtract(sustainNow.multiply(BigDecimal.valueOf(8))).abs().compareTo(d("1e-38")) <= 0,
                    "independently rounded curves retain the one-eighth sustain ratio at shared output precision");
            int ticks = cooldownTicks(q);
            check(ticks >= RESTART_FENCE && ticks <= previousTicks, "native restart interval is bounded and monotone");
            check(ticks == Math.max(RESTART_FENCE, ContinuousHighPowerGrowth.ceilCooldownTicks(cooldownNow)),
                    "ceil once at native scheduling boundary, then enforce physical fence");
            previousActivation = activationNow;
            previousSustain = sustainNow;
            previousCooldown = cooldownNow;
            previousTicks = ticks;
        }
        check(theoreticalActivationCost(d("3.000000001")).compareTo(d(".4")) < 0,
                "sub-percentage activation growth is not quantized");
        check(theoreticalSustainCost(d("3.000000001")).compareTo(d(".05")) < 0,
                "sub-percentage sustain growth is not quantized");
        equal(theoreticalActivationCost(BigDecimal.ZERO), d("2"), "low growth clamps to LV1");
        equal(theoreticalSustainCost(BigDecimal.TEN), d(".0025"), "high growth clamps to LV5");
        check(cooldownTicks(BigDecimal.ZERO) == 10 && cooldownTicks(BigDecimal.TEN) == 4,
                "clamping never removes the restart fence");
        rejects(() -> activationCost(d("1e257")), "unbounded activation coordinate rejects");
        rejects(() -> sustainCost(d("1e-257")), "unbounded sustain coordinate rejects");
        rejects(() -> cooldownTicks(null), "missing cooldown coordinate rejects");
    }

    private static ExactCombatState state(String hp, String maxHp, String shield, String maxShield, boolean enabled) {
        return new ExactCombatState(d(hp), d(maxHp), d(shield), d(maxShield), enabled);
    }
    private static Point p(double x, double y, double z) { return new Point(x, y, z); }
    private static BigDecimal d(String value) { return new BigDecimal(value); }
    private static void equal(BigDecimal actual, BigDecimal expected, String reason) {
        check(actual.compareTo(expected) == 0, reason + ": " + actual + " != " + expected);
    }
    private static void near(double actual, double expected, String reason) {
        check(Math.abs(actual - expected) <= 1e-15, reason);
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
