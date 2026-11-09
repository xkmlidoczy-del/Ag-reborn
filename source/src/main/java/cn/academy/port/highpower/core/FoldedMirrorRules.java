package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;

/** NEW221 has one lifetime and one paid, bounded reflected-ray opportunity. */
public final class FoldedMirrorRules {
    public static final int LIFETIME = SkillTuning.integer("folded_mirror_array", "duration_ticks"), HOLD_LIMIT=100, RECALL_TICKS=12, PREVIEW_INTERVAL=4,
            ATTEMPT_INTERVAL=1, EMPTY_INTERVAL=4, MASTERY_INTERVAL=20, SCAN_LIMIT=64;
    public static final double PLACE_RANGE = SkillTuning.number("folded_mirror_array", "placement_reach"), EXIT_RANGE=24, RANGE = SkillTuning.number("folded_mirror_array", "reach"), RADIUS=.035;
    private static final ContinuousHighPowerGrowth.Curve PLACE=ContinuousHighPowerGrowth.curve("2","2","1",".2",".02");
    private static final ContinuousHighPowerGrowth.Curve SHOT=ContinuousHighPowerGrowth.curve("8","8","3",".5",".05");
    private static final ContinuousHighPowerGrowth.Curve COOLDOWN=ContinuousHighPowerGrowth.curve("30","30","15","5","1");
    private FoldedMirrorRules() {}
    public static double placementCost(BigDecimal q){return PLACE.value(q).doubleValue();}
    public static double shotCost(BigDecimal q){return SHOT.value(q).doubleValue();}
    public static BigDecimal theoreticalCooldown(BigDecimal q){return COOLDOWN.value(q);}
    public static int cooldown(BigDecimal q){return ContinuousHighPowerGrowth.ceilCooldownTicks(theoreticalCooldown(q));}
    public static BigDecimal damage(BigDecimal d){return CombatAmounts.requirePositiveAmount(d,"NEW221 D").multiply(new BigDecimal("1.5")).min(CombatAmounts.CAP);}
    public static boolean clock(long tick){return tick>=0&&tick<=Long.MAX_VALUE-LIFETIME-1;}
    public static boolean expired(long start,long now,int duration){return !clock(start)||!clock(now)||now<start||now-start>=duration;}
    public static boolean ready(long before,long now,int interval){return clock(now)&&(before==-1||clock(before)&&now>=before&&now-before>=interval);}
}
