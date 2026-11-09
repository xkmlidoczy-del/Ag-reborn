/* AcademyCraft1.0.7 JEContext adaptation. Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
/** Captured Float mastery, reversed release cost, inclusive client tick16 extrapolation. */
public final class JetEngineSession {
 public static final String ID="jet_engine";public static final int LEVEL=4,TIME=8,LIFETIME=15;public static final double RANGE = SkillTuning.number("jet_engine", "reach"),TARGET_Y=1.65;
 private final AbilityProgress state;private final float mastery,affordability,releaseCp,releaseOverload;private int ticks;private boolean active=true,triggering,releasing;
 private JetEngineSession(AbilityProgress s){state=s;mastery=(float)ClassicRules.clamp(s.exp(ID),0,1);affordability=lerp(170,140,mastery);releaseCp=lerp(60,50,mastery);releaseOverload=affordability;}
 public static boolean mayStart(AbilityProgress s){return s!=null&&s.category.equals("meltdowner")&&s.level>=LEVEL&&s.canUse(ID);}
 public static JetEngineSession begin(AbilityProgress s){return mayStart(s)?new JetEngineSession(s):null;}
 private static float lerp(float a,float b,double e){return a+(float)ClassicRules.clamp(e,0,1)*(b-a);}
 /** Source affordability is checked on every server tick, including after the single release debit. */
 public boolean affordable(boolean creative){return creative||state.cp>=affordability;}
 public boolean release(boolean creative){if(!active||triggering||releasing)return false;releasing=true;try{if(!state.consumeSkill(ID,releaseCp,releaseOverload,creative,()->active)){active=false;return false;}triggering=true;state.addExperience(ID,.004F);state.setCooldown(ID,(int)lerp(60,30,mastery));return true;}finally{releasing=false;}}
 /** Caller applies this displacement before terminating on the 16th tick, just like c_triggerTick. */
 public float step(){if(!active||!triggering)return Float.NaN;boolean ends=ticks>=LIFETIME;++ticks;if(ends)active=false;return ticks/(float)TIME;}
 public float damage(){return lerp(7,20,mastery);}public void discard(){active=false;}public boolean active(){return active;}public boolean triggering(){return triggering;}public int ticks(){return ticks;}public float mastery(){return mastery;}public float affordability(){return affordability;}public float releaseCp(){return releaseCp;}public float releaseOverload(){return releaseOverload;}public AbilityProgress state(){return state;}
}
