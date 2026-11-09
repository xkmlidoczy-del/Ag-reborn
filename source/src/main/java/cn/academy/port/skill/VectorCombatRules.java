/* AcademyCraft1.0.7 VecManip combat arithmetic adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
/** Keep Scala Float arithmetic and truncation. Source vals are captured by sessions, not recomputed. */
public final class VectorCombatRules {
 private VectorCombatRules(){}
 public static float lerp(float a,float b,double exp){return a+(float)exp*(b-a);}
 public static boolean eligible(AbilityProgress s,String id,int level){return s!=null&&"vecmanip".equals(s.category)&&s.level>=level&&s.canUse(id)&&Double.isFinite(s.cp)&&Double.isFinite(s.overload)&&Double.isFinite(s.exp(id))&&s.exp(id)>=0&&s.exp(id)<=1;}
 public static float blastCp(double e){return lerp(160,200,e);}public static float blastOverload(double e){return lerp(50,30,e);}public static float blastDamage(double e){return lerp(10,25,e);}public static float blastBreakProbability(double e){return lerp(.5F,.8F,e);}public static float blastDropRate(double e){return lerp(.4F,.9F,e);}public static float blastHardness(double e){float f=(float)e;return f<.25F?2.9F:f<.5F?25:55;}public static int blastCooldown(double e){return(int)lerp(80,50,e);}
 public static float bloodCp(double e){return lerp(280,350,e);}public static float bloodOverload(double e){return lerp(55,40,e);}public static float bloodDamage(double e){return lerp(30,60,e);}public static int bloodCooldown(double e){return(int)lerp(90,40,e);}public static float bloodWalkSpeed(int tick){return lerp(.1F,.007F,Math.max(0,Math.min(1,tick/20F)));}
 public static float stormCp(double e){return lerp(40,25,e);}public static float stormOverload(double e){return lerp(10,7,e);}public static float stormCharge(double e){return lerp(70,30,e);}public static float stormSpeed(double e){return((float)e<.45F?.7F:1.2F)*lerp(2,3,e);}public static int stormCooldown(double e){return(int)lerp(30,10,e);}public static double approach(double from,double to){return from+Math.min(Math.abs(to-from),.16)*Math.signum(to-from);}
 /** Classic scala math.round(Double).toInt, including asymmetric negatives. */
 public static int blastCenter(double coordinate){return(int)Math.round(coordinate);}
 /** Source soft-block probing truncates player position plus [-10,10] random offset toward zero. */
 public static int stormProbe(double coordinate,double offset){return(int)(coordinate+offset);}
}
