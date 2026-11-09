package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.util.Optional;

/** NEW332 incident bypass: the next recognizable projectile is moved beside the caster; at most 40% of a hit is diverted. */
public final class IncidentBypassRules {
    public static final int ARM_TICKS = 8, WINDOW_TICKS = SkillTuning.integer("incident_bypass", "duration_ticks");
    public static final double MAX_SHARE = .4;
    public enum Kind { ARROW, THROWN, POISON, FIRE, AREA_FIELD, ATTACHED_STATE }
    private IncidentBypassRules() {}

    public static boolean handles(Kind kind) { return kind == Kind.ARROW || kind == Kind.THROWN; }

    public static double divertedShare(double requested) {
        return Double.isNaN(requested) ? 0 : Math.max(0, Math.min(MAX_SHARE, requested));
    }
    public static double remainingShare(double requested) { return 1 - divertedShare(requested); }

    /** One-shot window: armed by the hold, spent by the first diverted projectile. */
    public static final class Window {
        private boolean armed;
        public void arm() { armed = true; }
        public boolean consume() { boolean was = armed; armed = false; return was; }
        public boolean armed() { return armed; }
    }

    /**
     * The velocity of a bypassed projectile: same speed, turned 60 degrees toward one side of the player so its path
     * passes beside them. Empty for a stationary projectile or an undefined incoming axis.
     */
    public static Optional<V3> bypass(V3 velocity, V3 towardPlayer, boolean left) {
        if (velocity == null || towardPlayer == null || !velocity.finite() || !towardPlayer.finite()) return Optional.empty();
        double speed = velocity.length();
        V3 axis = towardPlayer.unit();
        if (speed < 1e-9 || axis.length() < .5) return Optional.empty();
        V3 side = axis.cross(new V3(0, 1, 0)).unit();
        if (side.length() < .5) side = axis.cross(new V3(1, 0, 0)).unit();
        if (!left) side = side.scale(-1);
        return Optional.of(side.scale(Math.sqrt(3) / 2 * speed).plus(axis.scale(.5 * speed)));
    }
}
