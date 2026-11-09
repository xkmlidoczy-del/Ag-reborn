/* AcademyCraft1.0.7 BloodRetroContext adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
public final class BloodRetrogradeSession {
 public static final String ID="blood_retro";public static final int LEVEL=4,AUTO_RELEASE_TICKS=30;private final AbilityProgress state;private final float damage;private boolean active=true;private int ticks;
 private BloodRetrogradeSession(AbilityProgress s){state=s;damage=VectorCombatRules.bloodDamage(s.exp(ID));}
 public static BloodRetrogradeSession begin(AbilityProgress s){return VectorCombatRules.eligible(s,ID,LEVEL)?new BloodRetrogradeSession(s):null;}
 public void tick(){if(active&&ticks<Integer.MAX_VALUE)ticks++;}public float walkSpeed(){return VectorCombatRules.bloodWalkSpeed(ticks);}public boolean autoRelease(){return ticks>=AUTO_RELEASE_TICKS;}
 /** No hold-duration minimum: immediate key-up can perform if a living target is in source range. */
 public boolean release(boolean target,boolean creative){if(!active)return false;active=false;if(!target||!state.consumeSkill(ID,VectorCombatRules.bloodCp(state.exp(ID)),VectorCombatRules.bloodOverload(state.exp(ID)),creative))return false;state.setCooldown(ID,VectorCombatRules.bloodCooldown(state.exp(ID)));return true;}
 /** Called after attack, even if protection rejects it. Achievement checks the post-award exp==1. */
 public void award(){state.addExperience(ID,.002F);}public boolean achievement(){return(float)state.exp(ID)==1F;}public void discard(){active=false;}public boolean active(){return active;}public AbilityProgress state(){return state;}public int ticks(){return ticks;}public float damage(){return damage;}
}
