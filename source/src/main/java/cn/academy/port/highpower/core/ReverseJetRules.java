package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW243 reverse jet brake: only ever removes momentum (never reverses or adds it), then a short vertical hover. */
public final class ReverseJetRules {
    public static final int BRAKE_TICKS = SkillTuning.integer("reverse_jet_brake", "duration_ticks"), HOVER_TICKS = SkillTuning.integer("reverse_jet_brake", "hover_ticks");
    private ReverseJetRules() {}

    /** Scales the velocity toward rest by {@code strength} (clamped to 0..1); the result never points against the original motion. */
    public static V3 brake(V3 v, double strength) {
        if (v == null || !v.finite() || Double.isNaN(strength)) return V3.ZERO;
        return v.scale(1 - Math.max(0, Math.min(1, strength)));
    }

    /** Fall distance shrinks in proportion to the downward speed that was actually removed, and never grows. */
    public static float fallDistance(float distance, double verticalBefore, double verticalAfter) {
        if (!Float.isFinite(distance) || distance < 0) return 0f;
        if (verticalBefore < 0 && verticalAfter < 0 && Math.abs(verticalAfter) < Math.abs(verticalBefore))
            return (float) (distance * (Math.abs(verticalAfter) / Math.abs(verticalBefore)));
        return distance;
    }

    /** Pulls a falling body's vertical speed toward zero; upward speed is only ever reduced. */
    public static V3 hoverVelocity(V3 v, int ticksLeft) {
        if (v == null || !v.finite() || ticksLeft <= 0) return v == null || !v.finite() ? V3.ZERO : v;
        double y = v.y() < 0 ? v.y() * .5 : v.y() * .8;
        return new V3(v.x(), y, v.z());
    }

    /** CP for a braking use of {@code ticks} ticks: the base cost plus a per-tick share, so longer jets always cost more. */
    public static double cpCost(int level, int ticks) {
        if (ticks < 0 || ticks > BRAKE_TICKS + HOVER_TICKS) throw new IllegalArgumentException("NEW243 duration");
        double base = NewSkillBudget.cp(NewSkillBudget.Skill.REVERSE_JET, new BigDecimal(Math.max(1, Math.min(5, level))));
        return base * (1 + ticks / (double) BRAKE_TICKS);
    }
}
