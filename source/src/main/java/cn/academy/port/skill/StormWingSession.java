/* AcademyCraft1.0.7 StormWingContext adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
public final class StormWingSession {
 public static final String ID="storm_wing";public static final int LEVEL=3,CHARGE=0,ACTIVE=1;public static final float EXPERIENCE=.00005F;
 /** Sustained-flight rebalance over the classic per-tick costs (which stay the classic reference in VectorCombatRules):
  * classic StormWing overheated a LV3 flyer in about 1-2 s. Active flight now pays SUSTAIN_CP of the classic CP and SUSTAIN_HEAT of the
  * classic overload per tick, hovering without a direction key pays HOVER of that, and above SPUTTER of the overload limit the wings
  * sputter at SPUTTER_SPEED instead of cutting out. */
 public static final float SUSTAIN_CP=.12F,SUSTAIN_HEAT=.04F,HOVER=.5F,SPUTTER=.85F,COOLED=.6F,SPUTTER_SPEED=.6F;
 private final AbilityProgress state;private final float cp,overload,speed,charge;private boolean live=true,hovering,cooling;private int phase,ticks;
 private StormWingSession(AbilityProgress s){state=s;double e=s.exp(ID);cp=VectorCombatRules.stormCp(e);overload=VectorCombatRules.stormOverload(e);speed=VectorCombatRules.stormSpeed(e);charge=VectorCombatRules.stormCharge(e);}
 public static StormWingSession begin(AbilityProgress s){return VectorCombatRules.eligible(s,ID,LEVEL)?new StormWingSession(s):null;}
 /** Source order consumes while already active, then changes charge state; transition tick is free. */
 public boolean tick(boolean creative){if(!live)return false;if(phase==ACTIVE){updateCooling();state.addExperience(ID,EXPERIENCE);
  // A zero-heat cooling tick must not postpone normal heat recovery. Preserve the earlier delay only when the
  // consumption hook really added no heat; another mod's heat mutation remains authoritative.
  double beforeHeat=state.overload;int beforeDelay=state.overloadDelay;
  if(!state.consumeSkill(ID,sustainCp(),sustainHeat(),creative)){live=false;return false;}
  if(cooling&&state.overload==beforeHeat)state.overloadDelay=Math.min(state.overloadDelay,beforeDelay);}if(ticks<Integer.MAX_VALUE)ticks++;if(phase==CHARGE&&ticks>charge){phase=ACTIVE;ticks=0;}return true;}
 public void terminate(boolean gameplay){if(gameplay)state.setCooldown(ID,VectorCombatRules.stormCooldown(state.exp(ID)));live=false;}
 /** Per-tick active costs after the sustain rebalance. */
 public float sustainCp(){return cp*SUSTAIN_CP*(hovering?HOVER:1);}public float sustainHeat(){return cooling?0:overload*SUSTAIN_HEAT*(hovering?HOVER:1);}
 /** Hovering (no direction key held) halves the sustain cost. */
 public void hovering(boolean value){hovering=value;}
 /** Overload as a fraction of the limit: 0 cool, 1 overheated. */
 public float heat(){double max=state.maxOverload();return max>0?(float)Math.min(1,Math.max(0,state.overload/max)):0;}
 private void updateCooling(){if(cooling){if(heat()<=COOLED)cooling=false;}else if(heat()>=SPUTTER)cooling=true;}
 public boolean cooling(){return cooling;}
 /** Flight speed with the overheat warning: the wings sputter rather than cut out near the limit. */
 public float flightSpeed(){return cooling||heat()>=SPUTTER?speed*SPUTTER_SPEED:speed;}
 public boolean active(){return live;}public int phase(){return phase;}public int ticks(){return ticks;}public AbilityProgress state(){return state;}public float cp(){return cp;}public float overload(){return overload;}public float speed(){return speed;}public float charge(){return charge;}public boolean softBreak(){return(float)state.exp(ID)<.15F;}public boolean masteryBurst(){return(float)state.exp(ID)==1F;}
}
