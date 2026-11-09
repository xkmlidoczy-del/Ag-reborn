/* AcademyCraft1.0.7 VecReflectionContext adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
/** Literal dynamic costs and pre-award reflection ratio, with source negative damage remainder. */
public final class VecReflectionSession {
 public static final String ID="vec_reflection";public static final int LEVEL=4;
 private final AbilityProgress state;private final double floor;private boolean active=true,ending;private int ticks;
 private VecReflectionSession(AbilityProgress s,boolean creative){state=s;s.consumeSkill(ID,0,initialOverload(s.exp(ID)),creative);floor=s.overload;}
 private static float lerp(float a,float b,double e){return a+(float)e*(b-a);}
 public static float initialOverload(double e){return lerp(350,250,e);}public static float entityCp(double e){return lerp(300,160,e);}public static float damageCp(double e){return lerp(20,15,e);}public static float normalCp(double e){return lerp(15,11,e);}public static float ratio(double e){return lerp(.6F,1.2F,e);}
 public static boolean mayStart(AbilityProgress s){return s!=null&&s.category.equals("vecmanip")&&s.level>=LEVEL&&s.canUse(ID)&&Double.isFinite(s.cp)&&Double.isFinite(s.overload)&&Double.isFinite(s.exp(ID));}
 public static VecReflectionSession begin(AbilityProgress s,boolean creative){return mayStart(s)?new VecReflectionSession(s,creative):null;}
 public boolean beginTick(){if(!active||ending)return false;ticks++;if(state.overload<floor)state.overload=floor;return true;}
 public boolean affect(float difficulty,boolean creative){if(!active||!Float.isFinite(difficulty)||difficulty<0)return false;if(!state.consumeSkill(ID,difficulty*entityCp(state.exp(ID)),0,creative,()->active))return false;state.addExperience(ID,difficulty*.0008F);return true;}
 public void endTick(boolean creative){if(active&&!state.consumeSkill(ID,normalCp(state.exp(ID)),0,creative,()->active))ending=true;}
 public record Damage(boolean performed,float remaining,float reflected){}
 /** Source passby branch never performs, even for complete or more-than-complete absorption. */
 public Damage damage(float incoming,boolean passby,boolean creative){if(!active||state.consumptionInProgress()||!Float.isFinite(incoming)||incoming<0)return new Damage(false,incoming,0);float reflected=ratio(state.exp(ID))*incoming;if(passby)return new Damage(false,incoming-reflected,reflected);float cp=damageCp(state.exp(ID))*incoming;if(!state.consumeWithForceSkill(ID,cp,0,creative,()->active))return new Damage(false,incoming,0);state.addExperience(ID,incoming*.0004F);return new Damage(true,incoming-reflected,reflected);}
 public void discard(){active=false;ending=true;}public AbilityProgress state(){return state;}public double overloadFloor(){return floor;}public int ticks(){return ticks;}public boolean active(){return active;}public boolean ending(){return ending;}
}
