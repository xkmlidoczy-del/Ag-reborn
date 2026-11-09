package cn.academy.port.highpower.core;

import static cn.academy.port.highpower.core.EsperGrowthStyle.*;

/** Pure checks of the four category growth styles: each rewards a different way of playing and nothing exceeds the bound. */
public final class EsperGrowthStyleRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) { checks++; if (!condition) throw new AssertionError(message); }
    private static void near(double actual, double expected, String message) { check(Math.abs(actual - expected) < 1e-9, message + ": " + actual + " vs " + expected); }

    public static void main(String[] args) {
        // Electromaster: first cast earns nothing extra, a sustained chain climbs to +50%, idle time loses it.
        var em = new Tracker();
        near(bonus(em, "electromaster", "arc", 0, 0, 0), 0, "EM first cast");
        double previous = 0;
        for (int step = 1; step <= 8; step++) {
            double b = bonus(em, "electromaster", "arc", step * 10L, 0, 0);
            check(b >= previous && b <= RESONANCE_MAX_STACKS * RESONANCE_STEP + 1e-9, "EM resonance only climbs, bounded: " + b);
            previous = b;
        }
        near(previous, .5, "EM resonance saturates at +50%");
        near(bonus(em, "electromaster", "arc", 80 + 5, 0, 0), .5, "same-half-second spam does not climb further");
        near(bonus(em, "electromaster", "arc", 80 + RESONANCE_IDLE_TICKS + 50, 0, 0), 0, "EM chain is lost after 5 s idle");
        var perTick = new Tracker(); double last = 0;
        for (long tick = 0; tick < 5; tick++) last = bonus(perTick, "electromaster", "storm", tick, 0, 0);
        near(last, 0, "a per-tick award stream cannot instantly max resonance");

        // Meltdowner: scales with overload, nothing at rest.
        var md = new Tracker();
        near(bonus(md, "meltdowner", "beam", 0, 0, 0), 0, "MD calm");
        near(bonus(md, "meltdowner", "beam", 1, .5, 0), .3, "MD half overload");
        near(bonus(md, "meltdowner", "beam", 2, 1, 0), .6, "MD edge of overload");
        near(bonus(md, "meltdowner", "beam", 3, 7, 0), .6, "MD over-range is clamped");
        near(bonus(md, "meltdowner", "beam", 4, Double.NaN, 0), 0, "MD non-finite is nothing");

        // Teleporter: only fresh skills.
        var tp = new Tracker();
        near(bonus(tp, "teleporter", "flash", 0, 0, 0), .5, "TP first use of a skill");
        near(bonus(tp, "teleporter", "flash", 5, 0, 0), 0, "TP spamming one skill earns no extra");
        near(bonus(tp, "teleporter", "probe", 6, 0, 0), .5, "TP a different skill is fresh");
        near(bonus(tp, "teleporter", "flash", VARIETY_TICKS + 1, 0, 0), .5, "TP variety refreshes after 30 s");
        for (int i = 0; i < 1000; i++) bonus(tp, "teleporter", "s" + i, 10_000L + i, 0, 0);
        check(tp.seen.size() <= VARIETY_MEMORY, "TP memory is bounded");

        // Vector manipulator: speed, not time or overload.
        var vm = new Tracker();
        near(bonus(vm, "vecmanip", "palm", 0, 1, 0), 0, "VM standing still, overload ignored");
        near(bonus(vm, "vecmanip", "palm", 1, 0, .25), .25, "VM half flow");
        near(bonus(vm, "vecmanip", "palm", 2, 0, 3), .5, "VM clamps at +50%");
        near(bonus(vm, "vecmanip", "palm", 3, 0, -1), 0, "VM negative speed is nothing");

        // Identity: the same situation pays different categories differently, and unknown categories pay nothing.
        var a = new Tracker(); var b = new Tracker(); var c = new Tracker(); var d = new Tracker();
        double e = bonus(a, "electromaster", "x", 0, .8, .4), m = bonus(b, "meltdowner", "x", 0, .8, .4), t = bonus(c, "teleporter", "x", 0, .8, .4), v = bonus(d, "vecmanip", "x", 0, .8, .4);
        check(e != m && m != t && t != v && e != t && e != v && m != v, "same moment, four different rewards: " + e + " " + m + " " + t + " " + v);
        near(bonus(new Tracker(), "classic", "x", 0, 1, 1), 0, "unknown category");
        near(bonus(null, "meltdowner", "x", 0, 1, 1), 0, "no tracker");
        for (double overload : new double[] {0, .3, 1, 2, Double.POSITIVE_INFINITY, -1})
            for (double speed : new double[] {0, .2, 9, Double.NaN})
                for (String category : new String[] {"electromaster", "meltdowner", "teleporter", "vecmanip"}) {
                    double bound = bonus(new Tracker(), category, "y", 0, overload, speed);
                    check(bound >= 0 && bound <= MAX_BONUS, "bounded " + category + " " + overload + " " + speed + " -> " + bound);
                }
        // HUD hint: read-only, mirrors what the next award would pay.
        var hem = new Tracker(); near(hint(hem, "electromaster", 0, 0, 0).percent(), 0, "EM hint idle");
        for (int step = 0; step <= 3; step++) bonus(hem, "electromaster", "arc", step * 10L, 0, 0);
        check(hint(hem, "electromaster", 30, 0, 0).equals(new Hint("electromaster", 30, 3)), "EM hint shows live stacks and their bonus: " + hint(hem, "electromaster", 30, 0, 0));
        check(hint(hem, "electromaster", 30, 0, 0).equals(hint(hem, "electromaster", 30, 0, 0)) && hem.stacks() == 3, "reading the hint does not change the tracker");
        check(hint(hem, "electromaster", 30 + RESONANCE_IDLE_TICKS + 1, 0, 0).count() == 0, "EM hint drops to zero once the chain is idle, before the next award");
        check(hint(new Tracker(), "meltdowner", 0, .5, 9).equals(new Hint("meltdowner", 30, 0)), "MD hint is overload share x60%");
        check(hint(new Tracker(), "vecmanip", 0, 1, .25).equals(new Hint("vecmanip", 25, 0)), "VM hint is flow share x50%");
        var htp = new Tracker(); bonus(htp, "teleporter", "flash", 0, 0, 0); bonus(htp, "teleporter", "probe", 5, 0, 0);
        check(hint(htp, "teleporter", 10, 0, 0).equals(new Hint("teleporter", 50, 2)), "TP hint counts skills in the window");
        check(hint(htp, "teleporter", VARIETY_TICKS + 3, 0, 0).count() == 1, "TP window closes per skill: " + hint(htp, "teleporter", VARIETY_TICKS + 3, 0, 0));
        check(hint(null, "meltdowner", 0, 1, 1) == null && hint(new Tracker(), "classic", 0, 1, 1) == null && hint(new Tracker(), null, 0, 1, 1) == null, "no hint without tracker or known category");
        for (double overload : new double[] {-1, 0, 2, Double.NaN}) for (double speed : new double[] {-1, 0, 9, Double.NaN})
            for (String category : new String[] {"electromaster", "meltdowner", "teleporter", "vecmanip"}) {
                var h = hint(new Tracker(), category, 0, overload, speed);
                check(h.percent() >= 0 && h.percent() <= 60 && h.count() >= 0, "hint bounded " + category + " " + overload + " " + speed);
            }
        System.out.println("PASS " + checks + " category growth style checks");
    }
}
