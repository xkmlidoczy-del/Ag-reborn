/* AcademyCraft 1.0.7 passive/generic float algorithms, GPLv3. See NOTICE. */
package cn.academy.port.core;
/** Source algorithms shared by the standalone ledger and the real ordered calculation bus. */
public final class ClassicPassiveSkills {
    private ClassicPassiveSkills() {}
    public static void calculate(AbilityProgress state,AbilityCalculation.Request request) {
        request.value=calculate(state,request.kind,request.value);
    }
    public static float calculate(AbilityProgress state,AbilityCalculation.Kind kind,float value) {
        return switch(kind) {
            case MAX_CP -> { if(state.learned("brain_course"))value+=1000; if(state.learned("brain_course_advanced"))value+=1500; yield value; }
            case MAX_OVERLOAD -> state.learned("brain_course_advanced")?value+100:value;
            case CP_RECOVERY -> state.learned("mind_course")?value*1.2f:value;
            case OVERLOAD_RECOVERY -> value;
        };
    }
    public static float radiationMastery(double maximum,double initLevel5) {
        float value=(float)maximum/(float)initLevel5;
        return Float.isFinite(value)?Math.max(0,Math.min(1,value)):0;
    }
    public static float radiationRate(double mastery) { return lerp(1.4f,1.8f,(float)mastery); }
    private static float lerp(float a,float b,float exp) {return a+exp*(b-a);}
    private static float tryLerp(float a,float b,float exp){return exp==-1?0:lerp(a,b,exp);}
    public static float criticalProbability(AbilityProgress state,int tier) {
        float folding=state.learned("dim_folding_theorem")?(float)state.exp("dim_folding_theorem"):-1;
        float fluct=state.learned("space_fluct")?(float)state.exp("space_fluct"):-1;
        return switch(tier) {
            case 0 -> tryLerp(.1f,.2f,folding)+tryLerp(.18f,.25f,fluct);
            case 1 -> tryLerp(.10f,.15f,fluct);
            case 2 -> tryLerp(.01f,.03f,fluct);
            default -> throw new IllegalArgumentException("Critical tier must be 0..2");
        };
    }
    public static float criticalRate(int tier) {
        return switch(tier){case 0 -> 1.3f;case 1 -> 1.6f;case 2 -> 2.6f;default -> throw new IllegalArgumentException("Critical tier must be 0..2");};
    }
    public static float criticalDamage(double raw,int tier){return (float)raw*criticalRate(tier);}
    /** CPData overload recovery preserves every original float operation and multiplier order. */
    public static float overloadRecovery(double overload,double base,double configuredSpeed,float multiplier) {
        float maximum=(float)base;
        float raw=(float)configuredSpeed*Math.max(.002f*maximum,.007f*maximum*lerp(1,.5f,(float)overload/maximum/2));
        float recovered=multiplier*raw;
        return Float.isFinite(recovered)&&recovered>=0?recovered:0;
    }
    public static float cpRecovery(double cp,double base,double configuredSpeed,float multiplier) {
        float maximum=(float)base;
        float raw=(float)configuredSpeed*.0003f*maximum*lerp(1,2,(float)cp/maximum);
        float recovered=multiplier*raw;
        return Float.isFinite(recovered)&&recovered>=0?recovered:0;
    }
}
