package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

/** NEW451 zenith vector dominion: up to 16 marked enemies lifted together, then one 4D compressed impulse partitioned exactly. */
public final class ZenithDominionRules {
    public static final int MAX_TARGETS = SkillTuning.integer("zenith_vector_dominion", "max_targets"), LIFT_TICKS = SkillTuning.integer("zenith_vector_dominion", "duration_ticks"), RELEASE_TICKS = 4;
    public static final double RANGE = SkillTuning.number("zenith_vector_dominion", "reach"), TOTAL_COEFFICIENT = 4, MAX_LIFT_SPEED = .9;
    private ZenithDominionRules() {}

    public static BigDecimal totalDamage(BigDecimal d) {
        return CombatAmounts.requirePositiveAmount(d, "NEW451 D").multiply(BigDecimal.valueOf(TOTAL_COEFFICIENT)).min(CombatAmounts.CAP);
    }

    /** Exact partition: every share stays with its frozen identity and the last receives the decimal remainder. */
    public static List<BigDecimal> shares(BigDecimal total, int count) {
        total = CombatAmounts.requireAmount(total, "NEW451 allocation");
        if (count < 0 || count > MAX_TARGETS) throw new IllegalArgumentException("NEW451 target count");
        if (count == 0) return List.of();
        BigDecimal each = total.divide(BigDecimal.valueOf(count), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        List<BigDecimal> out = new ArrayList<>(count);
        for (int i = 0; i < count - 1; i++) out.add(each);
        out.add(total.subtract(each.multiply(BigDecimal.valueOf(count - 1))));
        return List.copyOf(out);
    }

    /** Distinct marks in encounter order, at most {@link #MAX_TARGETS}. */
    public static List<UUID> freeze(List<UUID> marked) {
        if (marked == null) return List.of();
        LinkedHashSet<UUID> distinct = new LinkedHashSet<>();
        for (UUID id : marked) if (id != null && distinct.size() < MAX_TARGETS) distinct.add(id);
        return List.copyOf(distinct);
    }

    /** Upward speed during the lift: strongest first, easing to rest at the end of the lift. */
    public static double liftVelocity(int tick) {
        if (tick < 0 || tick >= LIFT_TICKS) return 0;
        return MAX_LIFT_SPEED * (1 - tick / (double) LIFT_TICKS);
    }
}
