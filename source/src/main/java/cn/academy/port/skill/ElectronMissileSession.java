/* AcademyCraft1.0.7 EMContext adaptation. Copyright Lambda Innovation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
/** Tick0 spawn, tick8 first strike, spawn before strike, inclusive captured lifetime, literal cooldown700. */
public final class ElectronMissileSession {
 public static final String ID="electron_missile";public static final int LEVEL=5,MAX_HOLD=5,BALL_LIFE=2333333;public static final float ATTACK_EXP=.001F;
 public record Tick(int index,boolean body,boolean spawn,boolean seek,boolean update,boolean ending){}
 private final AbilityProgress state;private final float mastery,consumption,attackCp,attackOverload,range,damage;private final int limit;private final double floor;private int ticks,balls;private boolean active=true,ending;
 private ElectronMissileSession(AbilityProgress s,boolean creative){state=s;mastery=(float)ClassicRules.clamp(s.exp(ID),0,1);consumption=lerp(12,5,mastery);attackCp=lerp(60,25,mastery);attackOverload=lerp(9,4,mastery);range=lerp((float) SkillTuning.number("electron_missile", "reach_novice"),(float) SkillTuning.number("electron_missile", "reach_mastered"),mastery);damage=lerp(10,18,mastery);limit=(int)lerp(80,200,mastery);s.consumeSkill(ID,0,200,creative);floor=s.overload;ending=!s.overloadFine;}
 public static boolean mayStart(AbilityProgress s){return s!=null&&s.category.equals("meltdowner")&&s.level>=LEVEL&&s.canUse(ID);}
 public static ElectronMissileSession begin(AbilityProgress s,boolean creative){return mayStart(s)?new ElectronMissileSession(s,creative):null;}
 private static float lerp(float a,float b,double e){return a+(float)ClassicRules.clamp(e,0,1)*(b-a);}
 public Tick tick(boolean creative){if(!active||ending)return new Tick(ticks,false,false,false,false,true);if(state.overload<floor)state.overload=floor;int index=ticks;if(!state.consumeSkill(ID,consumption,0,creative,()->active)){ending=true;return new Tick(index,false,false,false,false,true);}boolean body=ticks<=limit,spawn=body&&ticks%10==0&&balls<MAX_HOLD,seek=body&&ticks!=0&&ticks%8==0;if(spawn)balls++;if(!body)ending=true;ticks++;return new Tick(index,body,spawn,seek,true,ending);}
 /** Called after existence of a nearest living target is established; debit precedes ball removal/attack. */
 public boolean debitAttack(boolean creative){if(!active||balls==0||!state.consumeSkill(ID,attackCp,attackOverload,creative,()->active))return false;balls--;return true;}
 public void completeAttack(){if(active)state.addExperience(ID,ATTACK_EXP);}
 public boolean complete(){if(!active)return false;active=false;ending=true;state.setCooldown(ID,Math.max(700,Math.min(400,(int)mastery)));balls=0;return true;}
 public void discard(){active=false;ending=true;balls=0;}public boolean active(){return active;}public boolean ending(){return ending;}public int ticks(){return ticks;}public int balls(){return balls;}public int limit(){return limit;}public float mastery(){return mastery;}public float range(){return range;}public float damage(){return damage;}public double floor(){return floor;}public AbilityProgress state(){return state;}
}
