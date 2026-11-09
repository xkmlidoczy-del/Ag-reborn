/* AcademyCraft classic behavior derived from Lambda Innovation 1.0.7. See NOTICE. */
package cn.academy.port.core;

public final class ClassicRules {
    private ClassicRules() {}
    public static final double[] BASE_CP = {1800,1800,2800,4000,5800,8000};
    public static final double[] EXTRA_CP_CAP = {0,900,1000,1500,1700,12000};
    public static final double[] BASE_OVERLOAD = {100,100,150,240,350,500};
    public static final double[] EXTRA_OVERLOAD_CAP = {0,40,70,80,100,500};
    public static double lerp(double from, double to, double exp) { return from+(to-from)*clamp(exp,0,1); }
    public static double clamp(double value,double min,double max) { return Double.isFinite(value)?Math.max(min,Math.min(max,value)):min; }
    public static int level(int level) { return Math.max(0,Math.min(5,level)); }
    public static double cpRecovery(double cp,double base) { return 0.0003*base*(1+cp/base); }
    public static double overloadRecovery(double overload,double base) { return Math.max(0.002*base,0.007*base*(1-0.5*(overload/base/2))); }
    public static int learningStimulations(int level) { return (int)(3+level*level*0.5); }
    public static double levelThreshold(int level,int controllableSkillCount) { return (float)controllableSkillCount*(level==4?1.333f:0.666f); }
    public static int railgunChargeTicks() { return 20; }
    public static int handFrame(long elapsedMillis) { return Math.max(0,Math.min(39,(int)(elapsedMillis/40))); }
    public record SkillCost(double cp,double overload,double damage,double range,int cooldown) {}
    public static SkillCost arc(double exp) { return new SkillCost(lerp(30,70,exp),lerp(18,11,exp),lerp(5,9,exp),cn.academy.port.core.RangedSkillRanges.at("arc_gen",exp,15),(int)lerp(15,5,exp)); }
    public static SkillCost railgun(double exp) { return new SkillCost(lerp(200,450,exp),lerp(180,120,exp),lerp(60,110,exp),cn.academy.port.core.RangedSkillRanges.fixed("railgun"),(int)lerp(300,160,exp)); }
}
