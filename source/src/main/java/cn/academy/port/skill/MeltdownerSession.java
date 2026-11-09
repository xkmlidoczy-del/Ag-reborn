/* AcademyCraft1.0.7 MDContext adaptation, Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
/** Server-owned charge; key abort never fires. All values capture the original Float mastery. */
public final class MeltdownerSession {
    public static final String ID="meltdowner";
    public static final int LEVEL=3, MIN=20, MAX=40, TOLERANCE=100;
    public record Shot(int charge,float radius,float energy,float damage,int cooldown,float experience) {}
    private final AbilityProgress state; private final float mastery, consumption; private final double floor;
    private int ticks; private boolean active=true,ending;
    private MeltdownerSession(AbilityProgress state,boolean creative) {this.state=state;mastery=(float)ClassicRules.clamp(state.exp(ID),0,1);consumption=consumption(mastery);state.consumeSkill(ID,0,overload(mastery),creative);floor=state.overload;ending=!state.overloadFine;}
    public static boolean mayStart(AbilityProgress state) {return state!=null&&state.category.equals("meltdowner")&&state.level>=LEVEL&&state.canUse(ID);}
    public static MeltdownerSession begin(AbilityProgress state,boolean creative) {return mayStart(state)?new MeltdownerSession(state,creative):null;}
    private static float lerp(float a,float b,double exp) {return a+(b-a)*(float)ClassicRules.clamp(exp,0,1);}
    public static float overload(double exp){return lerp(200,170,exp);}
    public static float consumption(double exp){return lerp(10,15,exp);}
    public static float rate(int charge){return .8F+(1.2F-.8F)*((charge-20F)/20F);}
    public static Shot shot(double exp,int ticks){int ct=Math.min(ticks,MAX);float rate=rate(ct);return new Shot(ct,lerp((float) SkillTuning.number("meltdowner", "radius_novice"),(float) SkillTuning.number("meltdowner", "radius_mastered"),exp),rate*lerp(300,700,exp),rate*lerp(18,50,exp),(int)(rate*20*lerp(15,7,exp)),rate*.002F);}
    public static float reflectedDamage(double exp){return .5F*lerp(20,50,exp);}
    public boolean tick(boolean creative) {if(!active||ending)return ending;if(state.overload<floor)state.overload=floor;++ticks;if(!state.consumeSkill(ID,consumption,0,creative)||ticks>TOLERANCE||!state.overloadFine)ending=true;return ending;}
    public Shot release(){if(!active)return null;active=false;boolean fire=!ending&&ticks>=MIN;ending=true;return fire?shot(mastery,ticks):null;}
    /** Source attacks precede awards/cooldown. Commit only after the world adapter finishes. */
    public void complete(Shot shot){state.addExperience(ID,shot.experience());state.setCooldown(ID,shot.cooldown());}
    public void discard(){active=false;ending=true;}
    public AbilityProgress state(){return state;} public boolean active(){return active;} public boolean ending(){return ending;} public int ticks(){return ticks;} public float mastery(){return mastery;} public double overloadFloor(){return floor;}
}
