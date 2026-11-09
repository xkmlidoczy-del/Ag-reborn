package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;
import java.math.BigDecimal;
/** NEW413 provisional finite utility tuning, independent of combat magnitude. */
public final class BrakingTouchRules {
 public static final double RANGE = SkillTuning.number("braking_touch", "reach"), MAX_SPEED=4;
 public static final int ARM_TICKS = SkillTuning.integer("braking_touch", "duration_ticks"), VISUAL_TICKS=12, FENCE=4, SCAN_LIMIT=64;
 private static final ContinuousHighPowerGrowth.Curve CP=ContinuousHighPowerGrowth.curve("10","5","2",".5",".1");
 private static final ContinuousHighPowerGrowth.Curve CD=ContinuousHighPowerGrowth.curve("10","5","3","2","1");
 private BrakingTouchRules(){}
 public static double cpCost(BigDecimal q){return CP.value(q).doubleValue();}
 public static int cooldownTicks(BigDecimal q){return Math.max(FENCE,ContinuousHighPowerGrowth.ceilCooldownTicks(CD.value(q)));}
 public static float reducedDistance(float d){
  if(!Float.isFinite(d)||d<=0)return d;
  double budget=Math.min(3.0,(double)d*.5);
  float remaining=(float)((double)d-budget);
  // Rounding down can remove more than the budget (including four metres near 2^24).
  // Keep the nearest representable remaining distance that never overstates the reduction.
  if((double)d-(double)remaining>budget)remaining=Math.nextUp(remaining);
  return remaining;
 }
 public static boolean clock(long t){return t>=0&&t<=Long.MAX_VALUE-12000;}
}
