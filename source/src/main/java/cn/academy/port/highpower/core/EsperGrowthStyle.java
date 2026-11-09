package cn.academy.port.highpower.core;

import java.util.HashMap;
import java.util.Map;

/**
 * How each category's practice turns into extra growth (pure; no Minecraft types). Every style only adds a bounded bonus fraction
 * to an experience award the esper just earned by using a skill, so progression stays tied to real use and never replaces it:
 * <ul>
 * <li>electromaster, <b>resonance</b>: sustained casting builds a charge; +10% per half-second step, up to +50%, lost after 5 s idle;</li>
 * <li>meltdowner, <b>overload push</b>: casting while deep in overload teaches faster, up to +60% at the very edge;</li>
 * <li>teleporter, <b>variety</b>: the first use of each different skill per 30 s earns +50%; spamming one skill earns nothing extra;</li>
 * <li>vector manipulator, <b>flow reading</b>: casting while moving fast (sprint, fall, flight, being launched) earns up to +50%.</li>
 * </ul>
 */
public final class EsperGrowthStyle {
    public static final double MAX_BONUS = .6;
    public static final int RESONANCE_STEP_TICKS = 10, RESONANCE_IDLE_TICKS = 100, RESONANCE_MAX_STACKS = 5, VARIETY_TICKS = 600, VARIETY_MEMORY = 256;
    public static final double RESONANCE_STEP = .10, OVERLOAD_MAX = .6, VARIETY_BONUS = .5, FLOW_MAX = .5, FLOW_FULL_SPEED = .5;
    private EsperGrowthStyle() {}

    /** Per-player memory; transient by design (a relog resets the chain, never any earned value). */
    public static final class Tracker {
        long lastAward = Long.MIN_VALUE / 2, lastStep = Long.MIN_VALUE / 2;
        int stacks;
        final Map<String, Long> seen = new HashMap<>();
        public int stacks() { return stacks; }
        /** Resonance stacks as of {@code tick}: a chain idle for over 5 s is already gone even before the next award resets it. */
        public int liveStacks(long tick) { return tick - lastAward > RESONANCE_IDLE_TICKS ? 0 : stacks; }
        /** Distinct skills whose variety window is still open at {@code tick}. */
        public int recentSkills(long tick) {
            int count = 0;
            for (long at : seen.values()) if (tick - at < VARIETY_TICKS) count++;
            return count;
        }
    }

    /**
     * What the HUD shows for the current style: {@code percent} is the bonus the next award would earn right now (EM stacks x10%,
     * MD overload x60%, VM flow x50%, TP a fresh skill's flat 50%) and {@code count} the style's own counter (EM stacks,
     * TP skills used in the last 30 s; 0 for MD and VM). Read-only: never changes the tracker.
     */
    public record Hint(String category, int percent, int count) {}

    public static Hint hint(Tracker tracker, String category, long tick, double overloadFraction, double speed) {
        if (tracker == null || category == null) return null;
        return switch (category) {
            case "electromaster" -> { int stacks = tracker.liveStacks(tick); yield new Hint(category, percent(stacks * RESONANCE_STEP), stacks); }
            case "meltdowner" -> new Hint(category, percent(clamp(overloadFraction) * OVERLOAD_MAX), 0);
            case "teleporter" -> new Hint(category, percent(VARIETY_BONUS), Math.min(VARIETY_MEMORY, tracker.recentSkills(tick)));
            case "vecmanip" -> new Hint(category, percent(clamp(speed / FLOW_FULL_SPEED) * FLOW_MAX), 0);
            default -> null;
        };
    }

    private static int percent(double fraction) { return (int) Math.round(Math.max(0, Math.min(MAX_BONUS, Double.isFinite(fraction) ? fraction : 0)) * 100); }

    /** Fraction of the award to add on top (0 = none), bounded by {@link #MAX_BONUS}. */
    public static double bonus(Tracker tracker, String category, String skill, long tick, double overloadFraction, double speed) {
        if (tracker == null || category == null || skill == null) return 0;
        double bonus = switch (category) {
            case "electromaster" -> resonance(tracker, tick);
            case "meltdowner" -> clamp(overloadFraction) * OVERLOAD_MAX;
            case "teleporter" -> variety(tracker, skill, tick);
            case "vecmanip" -> clamp(speed / FLOW_FULL_SPEED) * FLOW_MAX;
            default -> 0;
        };
        tracker.lastAward = tick;
        return Math.max(0, Math.min(MAX_BONUS, Double.isFinite(bonus) ? bonus : 0));
    }

    private static double resonance(Tracker tracker, long tick) {
        if (tick - tracker.lastAward > RESONANCE_IDLE_TICKS) { tracker.stacks = 0; tracker.lastStep = tick; }
        else if (tick - tracker.lastStep >= RESONANCE_STEP_TICKS) { tracker.stacks = Math.min(RESONANCE_MAX_STACKS, tracker.stacks + 1); tracker.lastStep = tick; }
        return tracker.stacks * RESONANCE_STEP;
    }

    private static double variety(Tracker tracker, String skill, long tick) {
        Long last = tracker.seen.get(skill);
        boolean fresh = last == null || tick - last >= VARIETY_TICKS;
        if (fresh) {
            if (tracker.seen.size() >= VARIETY_MEMORY) tracker.seen.values().removeIf(at -> tick - at >= VARIETY_TICKS);
            if (tracker.seen.size() < VARIETY_MEMORY) tracker.seen.put(skill, tick);
        }
        return fresh ? VARIETY_BONUS : 0;
    }

    private static double clamp(double value) { return Double.isFinite(value) ? Math.max(0, Math.min(1, value)) : 0; }
}
