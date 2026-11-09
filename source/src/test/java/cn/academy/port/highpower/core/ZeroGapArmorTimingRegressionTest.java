package cn.academy.port.highpower.core;

import java.util.HashMap;

/** Save/load and phase-boundary witnesses for the physical break debt, independent of world time. */
public final class ZeroGapArmorTimingRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        for (int phase = 0; phase < 120; phase++) {
            var state = new ZeroGapArmorTiming(phase, 0).brokenNow();
            for (int elapsed = 0; elapsed <= 4; elapsed++) {
                var reloaded = ZeroGapArmorTiming.fromDecimalMap(state.toDecimalMap());
                check(reloaded.equals(state), "reload preserves exact phase and unpaid gap ticks");
                check(reloaded.openGap() == (elapsed < 4), "four genuine boundaries required, including phase wrap");
                check(reloaded.phaseTick() == (phase + elapsed) % 120, "phase never resets on persistence");
                if (elapsed < 4) state = reloaded.advanceOneNativeTick();
            }
            var repeated = state.brokenNow().brokenNow();
            check(repeated.breakTicks() == 4, "simultaneous breaks overlap instead of multiplying debt");
            check(repeated.phaseTick() == state.phaseTick(), "breaks never select a fresh orbit position");
        }
        for (String invalid : new String[]{"", "-1", "04", "5", "1.0", "NaN", "2147483647"}) {
            var fields = new HashMap<>(ZeroGapArmorTiming.INITIAL.toDecimalMap());
            fields.put("breakTicks", invalid);
            refuses(() -> ZeroGapArmorTiming.fromDecimalMap(fields));
        }
        for (String field : new String[]{"version", "phaseTick", "breakTicks"}) {
            var fields = new HashMap<>(ZeroGapArmorTiming.INITIAL.toDecimalMap()); fields.remove(field);
            refuses(() -> ZeroGapArmorTiming.fromDecimalMap(fields));
        }
        refuses(() -> new ZeroGapArmorTiming(120, 0));
        refuses(() -> new ZeroGapArmorTiming(-1, 0));
        var foreignClock = new HashMap<>(ZeroGapArmorTiming.INITIAL.toDecimalMap());
        foreignClock.put("worldTime", "0");
        refuses(() -> ZeroGapArmorTiming.fromDecimalMap(foreignClock));
        System.out.println("PASS " + checks + " NEW252 persisted physical phase/break checks");
    }
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static void refuses(Runnable action) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError("Malformed physical timing must not become a fresh window");
    }
}
