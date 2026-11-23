package cn.academy.port.canon;

public final class BrakedDescentRegressionTest {
    public static void main(String[] args) {
        var descent = new CanonFloatExpansion.BrakedDescent(25.0);
        check(!descent.descending(25.0, 0));
        for (int tick = 1; tick <= 100; tick++) {
            descent.observe(25.0 + (tick % 2 == 0 ? 0.01 : -0.01), tick);
            check(!descent.descending(25.0, tick));
        }
        descent.observe(24.9, 101);
        check(descent.descending(24.9, 101));
        check(!descent.descending(25.0, 102));
        check(descent.descending(24.91, 103));
        for (int tick = 104; tick <= 250; tick++) {
            descent.observe(24.9, tick);
            check(!descent.descending(24.9, tick));
        }
        for (int tick = 251; tick <= 430; tick++) {
            double y = 24.9 - (tick - 250) * .1;
            descent.observe(y, tick);
            check(descent.descending(y, tick));
        }
        System.out.println("PASS actual descent, stationary jitter, ascent, expiry and continuous braking");
    }

    private static void check(boolean condition) {
        if (!condition) throw new AssertionError("Incorrect server observation of braking descent");
    }
}
