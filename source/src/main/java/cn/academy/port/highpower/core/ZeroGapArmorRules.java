package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW252's CP/cooldown rules only; shield capacities and debits belong to the exact ledger. */
public final class ZeroGapArmorRules {
    public static final int STARTUP_TICKS = SkillTuning.integer("zero_gap_armor", "startup_ticks"), SUSTAIN_INTERVAL_TICKS = 20;
    public static final int BREAK_TICKS = ZeroGapArmorTiming.BREAK_TICKS;
    public static final BigDecimal SUSTAIN_CP = BigDecimal.ONE;
    private static final ContinuousHighPowerGrowth.Curve ACTIVATION = ContinuousHighPowerGrowth.curve("8", "4", "2", ".5", ".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN = ContinuousHighPowerGrowth.curve("60", "30", "16", "10", "1");

    private ZeroGapArmorRules() {}

    public static BigDecimal theoreticalActivationCost(BigDecimal q) { return ACTIVATION.value(q); }
    public static double activationCost(BigDecimal q) { return theoreticalActivationCost(q).doubleValue(); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return COOLDOWN.value(q); }
    /** Rounding happens once at the engine scheduling boundary, never inside the growth curve. */
    public static int cooldownTicks(BigDecimal q) { return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q)); }
    public static BigDecimal theoreticalSustainCost(BigDecimal q) {
        ContinuousHighPowerGrowth.clampPosition(q);
        return SUSTAIN_CP;
    }
    /** One CP per twenty genuine physical ticks, independent of q and shield capacity. */
    public static double sustainCost(BigDecimal q) { return theoreticalSustainCost(q).doubleValue(); }
}
