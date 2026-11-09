package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** NEW143 pulse shield: 0.8 s local interference on up to six circuit-tagged targets within 12 m; long press only detects. */
public final class PulseShieldRules {
    public static final double RANGE = SkillTuning.number("pulse_shield", "radius"), DAMAGE_COEFFICIENT = .2;
    public static final int MAX_TARGETS = SkillTuning.integer("pulse_shield", "max_targets"), INTERFERENCE_TICKS = 16;
    public record Candidate(UUID id, double distanceSquared, boolean tagged) {}
    private PulseShieldRules() {}

    /** Tagged, in-range, distinct targets, nearest first (UUID breaks ties), at most {@link #MAX_TARGETS}. */
    public static List<Candidate> select(List<Candidate> candidates) {
        if (candidates == null) return List.of();
        Map<UUID, Candidate> nearest = new HashMap<>();
        for (Candidate c : candidates) {
            if (c == null || c.id() == null || !c.tagged() || !Double.isFinite(c.distanceSquared()) || c.distanceSquared() < 0 || c.distanceSquared() > RANGE * RANGE) continue;
            nearest.merge(c.id(), c, (a, b) -> a.distanceSquared() <= b.distanceSquared() ? a : b);
        }
        List<Candidate> out = new ArrayList<>(nearest.values());
        out.sort(Comparator.comparingDouble(Candidate::distanceSquared).thenComparing(Candidate::id));
        return List.copyOf(out.subList(0, Math.min(MAX_TARGETS, out.size())));
    }

    public static int interferenceTicks(boolean detectOnly) { return detectOnly ? 0 : INTERFERENCE_TICKS; }

    public static BigDecimal damage(BigDecimal d) {
        return CombatAmounts.requirePositiveAmount(d, "NEW143 D").multiply(BigDecimal.valueOf(DAMAGE_COEFFICIENT)).min(CombatAmounts.CAP);
    }
}
