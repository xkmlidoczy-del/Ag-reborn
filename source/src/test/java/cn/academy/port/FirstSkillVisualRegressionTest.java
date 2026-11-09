package cn.academy.port;

import cn.academy.port.client.ClassicCubicCurve;
import cn.academy.port.client.ClassicFirstSkillTimeline;

/** Standalone headless checks. Does not load Minecraft, accept its EULA, or create a window. */
public final class FirstSkillVisualRegressionTest {
    private static int assertions;
    private static void eq(double expected, double actual) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1.0E-9)
            throw new AssertionError(expected + " != " + actual);
    }
    private static void rejects(Runnable action) {
        assertions++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Malformed curve accepted");
    }
    private static void pose(double age, double x, double y, double z, double rx, double ry) {
        var pose = ClassicFirstSkillTimeline.punch(age);
        eq(x, pose.x()); eq(y, pose.y()); eq(z, pose.z());
        eq(rx, pose.rotationX()); eq(ry, pose.rotationY());
    }
    public static void main(String[] args) {
        eq(0, new ClassicCubicCurve().valueAt(42));
        var single = new ClassicCubicCurve(3, 7);
        eq(7, single.valueAt(-100)); eq(7, single.valueAt(3)); eq(7, single.valueAt(100));
        var linear = new ClassicCubicCurve(1, -.02, 0, 0); // Unsorted construction is supported.
        eq(.02, linear.valueAt(-1)); eq(-.01, linear.valueAt(.5)); eq(-.04, linear.valueAt(2));
        rejects(() -> new ClassicCubicCurve(0, 1, 0, 2));
        rejects(() -> new ClassicCubicCurve(0, Double.NaN));
        rejects(() -> new ClassicCubicCurve(0));
        rejects(() -> linear.valueAt(Double.POSITIVE_INFINITY));

        // Upstream interpolates unweighted mean slopes at unevenly spaced knots.
        double[][] curves = {
                {0, 0, .3, -.4, 1, 0}, {0, .8, .5, .75, 1, 0},
                {0, -.04, .5, -.04, 1, 0}, {0, -40, .5, -45, 1, 0},
                {0, 0, .3, 10, 1, 0}, {-2, 5, -.1, 1, .2, 8, 4, -5}
        };
        for (double[] coordinates : curves) {
            var curve = new ClassicCubicCurve(coordinates);
            for (int sample = -100; sample <= 300; sample++) {
                double x = sample / 100.0;
                eq(referenceHermite(coordinates, x), curve.valueAt(x));
            }
        }
        eq(0, ClassicFirstSkillTimeline.prepare(0).y());
        eq(.2, ClassicFirstSkillTimeline.prepare(75).y());
        eq(.4, ClassicFirstSkillTimeline.prepare(150).y());
        var held = ClassicFirstSkillTimeline.prepare(300);
        eq(-.04, held.x()); eq(.8, held.y()); eq(-.1, held.z()); eq(-40, held.rotationX());
        eq(.8, ClassicFirstSkillTimeline.prepare(2450).y()); // Last accepted release is 49 ticks.
        eq(.8, ClassicFirstSkillTimeline.prepare(9950).y()); // Tolerated hold remains at t=2.
        eq(350, ClassicFirstSkillTimeline.PUNCH_CONTEXT_MS); // Original punchTicker > 6 cleanup.
        pose(0, -.04, .8, 0, -40, 0);
        // Golden values preserve overshoot; a linear interpolation substitute would fail.
        pose(75, -.0425, .81875, -.3664021164021165, -45.625, 9.16005291005291);
        pose(90, -.04288, .8204, -.4, -46.6, 10);
        pose(150, -.04, .75, -.38289601554907676, -45, 9.57240038872692);
        pose(225, -.0225, .41875, -.19752186588921278, -25.625, 4.938046647230321);
        pose(300, 0, 0, 0, 0, 0);
        pose(350, .01333333333333334, -.25, .09523809523809529, 15, -2.3809523809523823);

        eq(0, ClassicFirstSkillTimeline.orbAlpha(20, 0));
        eq(.3, ClassicFirstSkillTimeline.orbAlpha(20, 150));
        eq(.6, ClassicFirstSkillTimeline.orbAlpha(20, 300));
        eq(.6, ClassicFirstSkillTimeline.orbAlpha(20, 600));
        eq(.8, ClassicFirstSkillTimeline.orbAlpha(20, 725));
        eq(1, ClassicFirstSkillTimeline.orbAlpha(20, 850));
        eq(.5, ClassicFirstSkillTimeline.orbAlpha(20, 925));
        eq(0, ClassicFirstSkillTimeline.orbAlpha(20, 1000));
        eq(1, ClassicFirstSkillTimeline.orbSize(20, 700));
        eq(1.25, ClassicFirstSkillTimeline.orbSize(20, 800));
        eq(1.5, ClassicFirstSkillTimeline.orbSize(20, 900));
        eq(.75, ClassicFirstSkillTimeline.orbSize(20, 950));
        eq(0, ClassicFirstSkillTimeline.orbSize(20, 1000));
        // Short orb starts in the burst branch, intentionally skipping ordinary fade-in.
        eq(.84, ClassicFirstSkillTimeline.orbAlpha(5, 0));
        eq(1, ClassicFirstSkillTimeline.orbAlpha(5, 100));
        eq(2.0 / 3, ClassicFirstSkillTimeline.orbAlpha(5, 150));
        eq(1.0 / 3, ClassicFirstSkillTimeline.orbAlpha(5, 200));
        eq(0, ClassicFirstSkillTimeline.orbAlpha(5, 250));
        eq(1.125, ClassicFirstSkillTimeline.orbSize(5, 0));
        eq(1.5, ClassicFirstSkillTimeline.orbSize(5, 150));
        eq(.75, ClassicFirstSkillTimeline.orbSize(5, 200));
        eq(0, ClassicFirstSkillTimeline.orbSize(5, 250));
        eq(0, ClassicFirstSkillTimeline.rayLengthScale(0));
        eq(.5, ClassicFirstSkillTimeline.rayLengthScale(100));
        eq(1, ClassicFirstSkillTimeline.rayLengthScale(200));
        eq(1, ClassicFirstSkillTimeline.rayAlpha(300));
        eq(.5, ClassicFirstSkillTimeline.rayAlpha(500));
        eq(0, ClassicFirstSkillTimeline.rayAlpha(700));
        eq(1, ClassicFirstSkillTimeline.rayWidthScale(200));
        eq(.5, ClassicFirstSkillTimeline.rayWidthScale(450));
        eq(0, ClassicFirstSkillTimeline.rayWidthScale(700));
        eq(0, ClassicFirstSkillTimeline.markerBob(0));
        eq(.05 * Math.sin(628 / 400.0), ClassicFirstSkillTimeline.markerBob(628));
        eq(0, ClassicFirstSkillTimeline.particleAlpha(0, 20, 20, 5));
        eq(.8, ClassicFirstSkillTimeline.particleAlpha(4, 20, 20, 5));
        eq(1, ClassicFirstSkillTimeline.particleAlpha(20, 20, 20, 5));
        eq(.5, ClassicFirstSkillTimeline.particleAlpha(30, 20, 20, 5));
        eq(0, ClassicFirstSkillTimeline.particleAlpha(40, 20, 20, 5));
        eq(0, ClassicFirstSkillTimeline.particleAlpha(41, 20, 20, 5));
        eq(.5, ClassicFirstSkillTimeline.particleAlpha(1, 10, 20, 2));
        eq(1, ClassicFirstSkillTimeline.particleAlpha(2, 10, 20, 2));
        System.out.println("PASS " + assertions + " first-skill visual assertions (no game launch)");
    }

    /** Independent standard Hermite basis evaluation of the source's averaged secant tangents. */
    private static double referenceHermite(double[] pairs, double x) {
        int count = pairs.length / 2;
        if (x <= pairs[0]) return pairs[1] + (x - pairs[0]) * secant(pairs, 0, 1);
        if (x >= pairs[pairs.length - 2]) return pairs[pairs.length - 1]
                + (x - pairs[pairs.length - 2]) * secant(pairs, count - 2, count - 1);
        int right = 1;
        while (pairs[right * 2] < x) right++;
        int left = right - 1;
        double length = pairs[right * 2] - pairs[left * 2], t = (x - pairs[left * 2]) / length;
        double leftSlope = left == 0 ? secant(pairs, 0, 1)
                : (secant(pairs, left - 1, left) + secant(pairs, left, right)) / 2;
        double rightSlope = right == count - 1 ? secant(pairs, left, right)
                : (secant(pairs, left, right) + secant(pairs, right, right + 1)) / 2;
        double t2 = t * t, t3 = t2 * t;
        return (2 * t3 - 3 * t2 + 1) * pairs[left * 2 + 1]
                + (t3 - 2 * t2 + t) * length * leftSlope
                + (-2 * t3 + 3 * t2) * pairs[right * 2 + 1]
                + (t3 - t2) * length * rightSlope;
    }
    private static double secant(double[] pairs, int left, int right) {
        return (pairs[right * 2 + 1] - pairs[left * 2 + 1]) / (pairs[right * 2] - pairs[left * 2]);
    }
}
