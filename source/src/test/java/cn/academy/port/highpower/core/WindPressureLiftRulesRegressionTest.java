package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.WindPressureLiftRules.Horizontal;
import cn.academy.port.highpower.core.WindPressureLiftRules.Mode;
import cn.academy.port.highpower.core.WindPressureLiftRules.Velocity;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/** CPU-only additive motion, native-gravity, economics, debt and one-tick authority checks. */
public final class WindPressureLiftRulesRegressionTest {
    private static int checks;
    private static final Horizontal EAST = new Horizontal(1, 0);

    public static void main(String[] args) {
        additiveMotion();
        adverseMotion();
        ordinaryGravity();
        curvesAndDebt();
        tickPermissions();
        System.out.println("PASS " + checks + " NEW443 additive motion, gravity, payment and permission checks");
    }

    private static void additiveMotion() {
        Velocity original = new Velocity(-0.0, 0, .12345678901234567);
        Velocity up = WindPressureLiftRules.assist(original, Mode.UP, null);
        check(up.y() == .1, "grounded up starts with bounded .10 addition");
        same(up.x(), original.x(), "UP retains exact signed-zero X");
        same(up.z(), original.z(), "UP retains exact Z");
        check(WindPressureLiftRules.assist(new Velocity(1, .1, -2), Mode.UP, null).y() == .16,
                "UP approaches maximum .16 without exceeding it");
        for (double oldY : new double[]{.16, Math.nextUp(.16), 1, 100, Double.MAX_VALUE}) {
            Velocity fast = new Velocity(-.2, oldY, .3);
            check(WindPressureLiftRules.assist(fast, Mode.UP, null) == fast,
                    "existing higher upward velocity is never truncated");
        }
        Velocity side = WindPressureLiftRules.assist(new Velocity(0, 0, -0.0), Mode.SIDE, EAST);
        check(side.x() == .03 && side.y() == 0,
                "SIDE adds frozen-heading motion without forcing grounded zero Y downward");
        same(side.z(), -0.0, "zero heading component cannot alter signed-zero native component");
        Velocity descending = WindPressureLiftRules.assist(new Velocity(0, -.08, 0), Mode.SIDE, EAST);
        check(descending.y() <= -.02 && descending.y() > -.02000000000000001,
                "SIDE approaches corrected negative pre-travel target from below");
        for (double higherY : new double[]{-.01, -0.0, 0, .02, .16, 1, Double.MAX_VALUE}) {
            Velocity higher = WindPressureLiftRules.assist(new Velocity(0, higherY, 0), Mode.SIDE, EAST);
            same(higher.y(), higherY, "negative SIDE target never creates a downward overwrite");
        }
        Velocity opposite = new Velocity(-.12, .8, -0.0);
        check(WindPressureLiftRules.assist(opposite, Mode.SIDE, EAST) == opposite,
                "opposite native momentum is retained exactly rather than braked");
        Velocity diagonal = new Velocity(.08, -.2, -.06);
        Velocity opposed = WindPressureLiftRules.assist(diagonal, Mode.SIDE, new Horizontal(-1, 1));
        same(opposed.x(), diagonal.x(), "diagonal opposing native X stays exact");
        same(opposed.z(), diagonal.z(), "diagonal opposing native Z stays exact");
        check(opposed.y() > diagonal.y(), "opposing horizontal motion still permits independent vertical assistance");
        Velocity subnormalOpposite = new Velocity(-Double.MIN_VALUE, .8, -0.0);
        check(WindPressureLiftRules.assist(subnormalOpposite, Mode.SIDE, EAST) == subnormalOpposite,
                "subnormal opposing motion cannot be erased by a large positive addition");
        Velocity perpendicular = new Velocity(.1, .8, -.1);
        Velocity perpendicularResult = WindPressureLiftRules.assist(perpendicular, Mode.SIDE, new Horizontal(1, 1));
        check(perpendicularResult.x() > perpendicular.x() && perpendicularResult.z() > perpendicular.z(),
                "exactly perpendicular motion may receive a bounded along-heading addition");
        same(perpendicularResult.y(), .8, "SIDE does not overwrite an existing jump or knockback ascent");
        for (Velocity fast : List.of(new Velocity(-.4, .4, .01), new Velocity(.4, .5, -0.0),
                new Velocity(.17, .6, .08), new Velocity(Double.MAX_VALUE, .7, Double.MAX_VALUE))) {
            Velocity actual = WindPressureLiftRules.assist(fast, Mode.SIDE, EAST);
            check(actual == fast, "pre-existing high horizontal motion is preserved, never normalized");
        }
        Velocity overResult = new Velocity(.17, .4, -.01);
        check(WindPressureLiftRules.assist(overResult, Mode.SIDE, EAST) == overResult,
                "addition exceeding resultant cap is refused instead of clipping existing velocity");
        Velocity mixed = new Velocity(.17, -.2, .01);
        Velocity mixedResult = WindPressureLiftRules.assist(mixed, Mode.SIDE, EAST);
        same(mixedResult.x(), mixed.x(), "refused horizontal X remains exact");
        same(mixedResult.z(), mixed.z(), "refused horizontal Z remains exact");
        check(mixedResult.y() > mixed.y() && mixedResult.y() - mixed.y() <= .1,
                "horizontal refusal does not create a downward overwrite or oversized vertical rescue");
    }

    private static void adverseMotion() {
        for (double bad : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            check(WindPressureLiftRules.assist(new Velocity(bad, 0, 0), Mode.UP, null) == null,
                    "nonfinite X fails closed");
            check(WindPressureLiftRules.assist(new Velocity(0, bad, 0), Mode.UP, null) == null,
                    "nonfinite Y fails closed");
            check(WindPressureLiftRules.assist(new Velocity(0, 0, bad), Mode.SIDE, EAST) == null,
                    "nonfinite Z fails closed");
            check(WindPressureLiftRules.freezeDirection(bad, 1) == null,
                    "nonfinite heading cannot become a cast direction");
        }
        check(WindPressureLiftRules.freezeDirection(0, -0.0) == null, "zero horizontal look refuses SIDE");
        check(WindPressureLiftRules.assist(null, Mode.UP, null) == null
                && WindPressureLiftRules.assist(new Velocity(0, 0, 0), null, EAST) == null
                && WindPressureLiftRules.assist(new Velocity(0, 0, 0), Mode.SIDE, null) == null,
                "missing native inputs fail closed");
        for (double value : new double[]{Double.MIN_VALUE, Double.MIN_NORMAL, 1, Double.MAX_VALUE}) {
            Horizontal heading = WindPressureLiftRules.freezeDirection(value, value);
            check(heading != null && heading.finite() && heading.x() > 0 && heading.z() > 0,
                    "scaling normalizes both subnormal and huge finite direction inputs");
            check(Math.abs(Math.hypot(heading.x(), heading.z()) - 1) <= Math.ulp(1.0),
                    "heading length is unit within representational precision");
        }
        Random random = new Random(443);
        for (int i = 0; i < 30_000; i++) {
            Velocity before = new Velocity(random.nextDouble() * 2 - 1,
                    random.nextDouble() * 30 - 20, random.nextDouble() * 2 - 1);
            Horizontal heading = WindPressureLiftRules.freezeDirection(random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1);
            Mode mode = (i & 1) == 0 ? Mode.UP : Mode.SIDE;
            Velocity after = WindPressureLiftRules.assist(before, mode, heading);
            check(after != null && after.finite(), "finite input yields finite assistance");
            check(after.y() >= before.y() && after.y() - before.y() <= .1,
                    "no downward overwrite, brake or unbounded fall-speed reset");
            if (before.y() >= (mode == Mode.UP ? .16 : -.02))
                same(after.y(), before.y(), "all pre-existing higher Y remains bit-exact");
            else check(after.y() <= (mode == Mode.UP ? .16 : -.02), "assisted Y obeys mode ceiling");
            if (mode == Mode.UP || Math.hypot(before.x(), before.z()) > .18) {
                same(after.x(), before.x(), "untouched X is exact");
                same(after.z(), before.z(), "untouched Z is exact");
            } else {
                double dx = after.x() - before.x(), dz = after.z() - before.z();
                check(Math.hypot(dx, dz) <= .03 && Math.hypot(after.x(), after.z()) <= .18,
                        "SIDE limits both actual addition and resultant horizontal magnitude");
                check(dx * heading.x() + dz * heading.z() >= 0,
                        "admitted horizontal contribution points along frozen direction");
                if (before.x() * heading.x() + before.z() * heading.z() < 0) {
                    same(after.x(), before.x(), "opposing X is not braked by assistance");
                    same(after.z(), before.z(), "opposing Z is not braked by assistance");
                }
            }
        }
    }

    private static void ordinaryGravity() {
        final double nativeDrag = .98F;
        Velocity up = new Velocity(0, 0, 0), side = new Velocity(0, 0, 0);
        double modeledSideHeight = 0;
        for (int tick = 0; tick < 100; tick++) {
            Velocity u = WindPressureLiftRules.assist(up, Mode.UP, null);
            Velocity s = WindPressureLiftRules.assist(side, Mode.SIDE, EAST);
            // Native travel consumes current motion before applying this gravity step.
            // A negative post-travel velocity alone is not proof of a descending path.
            modeledSideHeight += s.y();
            up = new Velocity(u.x(), (u.y() - .08) * nativeDrag, u.z());
            side = new Velocity(s.x() * .91F, (s.y() - .08) * nativeDrag, s.z() * .91F);
            check(up.y() > 0 && up.y() <= (.16 - .08) * nativeDrag,
                    "native .08 gravity retains bounded upward travel");
            check(side.y() < 0, "negative SIDE target retains negative post-gravity state");
        }
        check(Math.abs(side.y() - (-.02 - .08) * nativeDrag) <= 2 * Math.ulp(side.y()),
                "SIDE has expected native-float-drag post-gravity velocity");
        check(modeledSideHeight > -1.99 && modeledSideHeight < -1.97,
                "native move-before-gravity sequence now descends .02 per assisted tick after initial zero");
        Velocity falling = new Velocity(0, -3.5, 0);
        Velocity aided = WindPressureLiftRules.assist(falling, Mode.SIDE, EAST);
        check(aided.y() < -3.39 && aided.y() - falling.y() <= .1,
                "large descent remains large and cannot be presented as safe landing");
        double nativeFallDistance = 9.25;
        nativeFallDistance += -aided.y();
        check(nativeFallDistance > 12, "native-style fall accumulation is not reset by assistance");
        double afterStop = (side.y() - .08) * nativeDrag;
        check(afterStop < side.y(), "release simply stops assistance; ordinary gravity continues");
    }

    private static void curvesAndDebt() {
        String[] cp = {"8", "4", "2", ".5", ".05"};
        int[] cd = {60, 30, 16, 10, 1};
        for (int i = 0; i < 5; i++) {
            BigDecimal q = BigDecimal.valueOf(i + 1);
            check(WindPressureLiftRules.theoreticalActivationCost(q).compareTo(new BigDecimal(cp[i])) == 0,
                    "exact activation anchor q=" + q);
            check(WindPressureLiftRules.theoreticalSustainCost(q).multiply(BigDecimal.valueOf(8))
                    .compareTo(new BigDecimal(cp[i])) == 0, "upkeep is exactly one eighth");
            check(WindPressureLiftRules.theoreticalCooldown(q).intValueExact() == cd[i],
                    "economic cooldown anchor remains independent of physical fence");
            check(WindPressureLiftRules.cooldownTicks(q) == cd[i],
                    "economic native cooldown follows original curve independently of restart floor");
        }
        double previous = Double.POSITIVE_INFINITY;
        for (int i = 0; i <= 80; i++) {
            BigDecimal q = BigDecimal.ONE.add(BigDecimal.valueOf(i).divide(BigDecimal.valueOf(20)));
            double cost = WindPressureLiftRules.activationCost(q);
            check(Double.isFinite(cost) && cost > 0 && cost <= previous, "continuous CP is positive and monotone");
            previous = cost;
            check(WindPressureLiftRules.theoreticalSustainCost(q).multiply(BigDecimal.valueOf(8))
                    .compareTo(WindPressureLiftRules.theoreticalActivationCost(q)) == 0,
                    "interpolated upkeep is also exactly one eighth");
        }
        check(WindPressureLiftRules.activationCost(new BigDecimal("4.0001")) < .5,
                "growth between levels is not integer-quantized");
        check(WindPressureLiftRules.LIFETIME == cn.academy.port.core.SkillTuning.number("wind_pressure_lift", "duration_ticks") && WindPressureLiftRules.PAYMENT_PERIOD == 20
                && WindPressureLiftRules.MAX_ACCEPTED_MOVE == .9 && WindPressureLiftRules.RESTART_FENCE == 8,
                "immutable native-time, separate restart and movement bounds");
        check(WindPressureLiftRules.paymentShape(0, 0, 0, 0), "genuinely new debt-free shape");
        check(WindPressureLiftRules.paymentShape(20, 8, 60, 1), "largest valid saved countdowns");
        check(WindPressureLiftRules.paymentShape(0, 0, 0, Long.MAX_VALUE),
                "exhausted receipt is valid history; native writer must refuse increment");
        for (int[] bad : new int[][]{{-1, 0, 0}, {21, 0, 0}, {0, -1, 0}, {0, 9, 0},
                {0, 0, -1}, {0, 0, 61}, {Integer.MAX_VALUE, 0, 0}, {0, 0, Integer.MIN_VALUE}})
            check(!WindPressureLiftRules.paymentShape(bad[0], bad[1], bad[2], 1),
                    "malformed persisted countdown cannot clear or mint debt");
        check(!WindPressureLiftRules.paymentShape(0, 0, 0, -1)
                && !WindPressureLiftRules.paymentShape(1, 0, 0, 0)
                && !WindPressureLiftRules.paymentShape(0, 1, 0, 0)
                && !WindPressureLiftRules.paymentShape(0, 0, 1, 0), "receipt and debt shape must agree");
        int remaining = 20;
        for (int tick = 0; tick < 20; tick++) {
            check(remaining == 20 - tick, "debt counts real calls, not a world-clock delta");
            remaining = WindPressureLiftRules.ageRemaining(remaining, 20);
        }
        check(remaining == 0 && WindPressureLiftRules.ageRemaining(0, 20) == 0,
                "twenty real ticks exhaust one tranche without negative credit");
        rejected(() -> WindPressureLiftRules.ageRemaining(21, 20), "oversized debt must fail closed");
        rejected(() -> WindPressureLiftRules.ageRemaining(-1, 20), "negative debt cannot age into free state");
    }

    private static void tickPermissions() {
        check(WindPressureLiftRules.freshPermission(0, 1, 1, 0, -1, 0), "first real-tick permission is usable");
        check(!WindPressureLiftRules.freshPermission(0, 1, 1, 0, 0, 1), "same permission cannot be consumed twice");
        check(!WindPressureLiftRules.freshPermission(0, 1, 2, 0, 0, 1),
                "even a newer sequence cannot grant a second addition in the same server tick");
        check(!WindPressureLiftRules.freshPermission(1, 2, 1, 1, 0, 1), "old sequence cannot replay in newer tick");
        check(WindPressureLiftRules.freshPermission(2, 3, 3, 2, 0, 1),
                "skipped grant does not prevent latest fresh grant");
        for (long now : new long[]{-1, 9, 11, 12, 1000, Long.MAX_VALUE})
            check(!WindPressureLiftRules.freshPermission(10, 11, 10, now, -1, 0),
                    "future, expired or delayed permission never catches up");
        for (long expiry : new long[]{-1, 9, 10, 12, Long.MAX_VALUE})
            check(!WindPressureLiftRules.freshPermission(10, expiry, 10, 10, -1, 0),
                    "permission expiry cannot be widened or inverted");
        check(!WindPressureLiftRules.freshPermission(Long.MAX_VALUE, Long.MIN_VALUE, 1, Long.MAX_VALUE, -1, 0)
                && !WindPressureLiftRules.freshPermission(-1, 0, 1, -1, -1, 0)
                && !WindPressureLiftRules.freshPermission(1, 2, 0, 1, -1, 0)
                && !WindPressureLiftRules.freshPermission(1, 2, 1, 1, -2, 0)
                && !WindPressureLiftRules.freshPermission(1, 2, 1, 1, -1, -1),
                "overflow, negative clocks and malformed watermarks fail closed");
    }

    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static void same(double actual, double expected, String why) {
        check(Double.doubleToRawLongBits(actual) == Double.doubleToRawLongBits(expected), why);
    }
    private static void rejected(Runnable operation, String why) {
        try { operation.run(); throw new AssertionError(why); }
        catch (IllegalArgumentException expected) { checks++; }
    }
}
