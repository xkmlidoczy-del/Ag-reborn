/* AcademyCraft1.0.7 BlastwaveContext adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
public final class DirectedBlastwaveSession {
 public static final String ID="dir_blast";public static final int LEVEL=3,MIN_TICKS=6,MAX_ACCEPTED_TICKS=50,MAX_TOLERANT_TICKS=200,PUNCH_ANIM_TICKS=6;
 private final AbilityProgress state;private final float cp,damage,breakProbability,hardness,dropRate;private final int cooldown;private boolean active=true;
 private DirectedBlastwaveSession(AbilityProgress s){state=s;double e=s.exp(ID);cp=VectorCombatRules.blastCp(e);damage=VectorCombatRules.blastDamage(e);breakProbability=VectorCombatRules.blastBreakProbability(e);hardness=VectorCombatRules.blastHardness(e);dropRate=VectorCombatRules.blastDropRate(e);cooldown=VectorCombatRules.blastCooldown(e);}
 public static DirectedBlastwaveSession begin(AbilityProgress s){return VectorCombatRules.eligible(s,ID,LEVEL)?new DirectedBlastwaveSession(s):null;}
 public static boolean accepts(long tick){return tick>MIN_TICKS&&tick<MAX_ACCEPTED_TICKS;}public static boolean holds(long tick){return tick>=0&&tick<MAX_TOLERANT_TICKS;}
 public boolean release(long tick,boolean creative){if(!active)return false;active=false;if(!accepts(tick)||!state.consumeSkill(ID,cp,VectorCombatRules.blastOverload(state.exp(ID)),creative))return false;state.setCooldown(ID,cooldown);return true;}
 public void award(boolean entities){state.addExperience(ID,entities?.0025F:.0012F);}public void discard(){active=false;}public boolean active(){return active;}public AbilityProgress state(){return state;}public float cp(){return cp;}public float damage(){return damage;}public float breakProbability(){return breakProbability;}public float hardness(){return hardness;}public float dropRate(){return dropRate;}public int cooldown(){return cooldown;}
}
