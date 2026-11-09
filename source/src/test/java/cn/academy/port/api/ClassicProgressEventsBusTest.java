/* Actual official standalone NeoForge bus and proposed production progress adapters. GPLv3. */
package cn.academy.port.api;
import cn.academy.port.core.*;
import net.neoforged.bus.api.*;
import java.util.*;
public final class ClassicProgressEventsBusTest {
 static int checks;static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}
 static void bits(double value,float expected,String why){check(Double.doubleToRawLongBits(value)==Double.doubleToRawLongBits((double)expected),why);}
 static void checkFlags(AbilityProgress s){check(s.activated&&!s.overloadFine&&s.interfering&&s.cpDelay==13&&s.overloadDelay==21,"source learn/level preserve active/overload-lock/interference/delays");}
 public static void main(String[] args){
  for(var type:List.of(SkillLearnEvent.class,LevelChangeEvent.class,SkillExpChangedEvent.class,SkillExpAddedEvent.class))check(!ICancellableEvent.class.isAssignableFrom(type),"source progress events are noncancellable");
  var bus=BusBuilder.builder().build();var s=new AbilityProgress();s.selectCategory("teleporter");s.setLevel(1);var phases=new ArrayList<String>();int[] calls={0,0,0,0};
  s.bindProgressEvents((id,effects)->bus.post(new SkillLearnEvent(null,s.category,id,s,effects)),(level,effects)->bus.post(new LevelChangeEvent(null,s,effects)),award->bus.post(new SkillExpChangedEvent(null,award.category(),award.skill(),s)),award->bus.post(new SkillExpAddedEvent(null,award.category(),award.skill(),s,award.amount())));
  s.bindCalculations(r->{if(r.kind==AbilityCalculation.Kind.MAX_CP||r.kind==AbilityCalculation.Kind.MAX_OVERLOAD){phases.add("calc:"+r.kind);r.value=(float)r.value+17.25f;}});
  bus.addListener(EventPriority.HIGHEST,SkillLearnEvent.class,e->{calls[0]++;phases.add("learn-high");check(e.state==s&&e.category.equals("teleporter")&&e.skill.equals("space_fluct")&&s.learned("space_fluct"),"native learn exposes just-mutated learned bit and live state");check(s.baseCp()==9999&&s.cp==713.125f&&s.overload==17.75f&&s.extraCp==100.125f&&s.levelExperience==7.7f&&s.exp("space_fluct")==((double).73f),"native learn HIGHEST precedes CP/common and award mutation");checkFlags(s);});
  bus.addListener(EventPriority.NORMAL,SkillLearnEvent.class,AbilityProgressLifecycle::learned);
  bus.addListener(EventPriority.LOWEST,SkillLearnEvent.class,e->{phases.add("learn-low");check(s.baseCp()==1817.25f&&s.cp==1917.375f&&s.overload==0&&s.extraCp==100.125f&&s.extraOverload==33.25f&&s.levelExperience==7.7f&&s.exp("space_fluct")==((double).73f),"native learn LOWEST sees refill before award");checkFlags(s);});
  bus.addListener(EventPriority.HIGHEST,SkillExpChangedEvent.class,e->{calls[1]++;phases.add("changed-high");check(e.state==s&&e.skill.equals("space_fluct")&&s.exp("space_fluct")==1,"Changed exposes clamped mastery");});
  bus.addListener(EventPriority.LOWEST,SkillExpChangedEvent.class,e->phases.add("changed-low"));
  bus.addListener(EventPriority.HIGHEST,SkillExpAddedEvent.class,e->{calls[2]++;phases.add("added-high:"+e.amount);check(e.state==s&&e.amount==.35f&&s.exp("space_fluct")==1,"Added retains full request beyond capped mastery");});
  bus.addListener(EventPriority.LOWEST,SkillExpAddedEvent.class,e->phases.add("added-low"));
  s.unlearnedExperience.put("space_fluct",(double).73f);s.restoreCalculatedMaxima(9999,444);s.extraCp=100.125f;s.extraOverload=33.25f;s.cp=713.125f;s.overload=17.75f;s.levelExperience=7.7f;s.activated=true;s.overloadFine=false;s.interfering=true;s.cpDelay=13;s.overloadDelay=21;
  s.addExperienceRaw("space_fluct",.35f);check(phases.equals(List.of("learn-high","calc:MAX_CP","calc:MAX_OVERLOAD","learn-low","changed-high","changed-low","added-high:0.35","added-low")),"actual native priority bus preserves original learn/common/XP order");bits(s.levelExperience,7.7f+.35f,"full request progression");check(!s.learned("dim_folding_theorem"),"generic XP bypasses missing tree parent");
  phases.clear();double cp=s.cp;s.cp=17;s.overload=9;s.addPassiveExperienceRaw("space_fluct",.35f);check(calls[0]==1&&calls[1]==2&&calls[2]==2&&s.cp==17&&s.overload==9,"duplicate auto-learn is silent and capped awards still post both XP events");
  bus.addListener(EventPriority.HIGHEST,LevelChangeEvent.class,e->{calls[3]++;phases.add("level-high");check(s.level==5&&s.levelExperience==0&&s.extraCp==100.125f&&s.extraOverload==33.25f&&s.cp==17&&s.overload==9&&s.baseCp()==1817.25f,"level HIGHEST primitive mutation precedes growth/common reset");checkFlags(s);});
  bus.addListener(EventPriority.NORMAL,LevelChangeEvent.class,AbilityProgressLifecycle::level);
  bus.addListener(EventPriority.LOWEST,LevelChangeEvent.class,e->{phases.add("level-low");check(s.extraCp==0&&s.extraOverload==0&&s.cp==8017.25f&&s.overload==0&&s.baseCp()==8017.25f&&s.levelExperience==0,"level LOWEST after NORMAL reset/refill");checkFlags(s);});
  phases.clear();s.setLevel(5);check(phases.equals(List.of("level-high","calc:MAX_CP","calc:MAX_OVERLOAD","level-low")),"actual native level priority order");
  s.extraCp=7.5;s.extraOverload=1.25;s.cp=42;s.overload=19;s.levelExperience=.73;phases.clear();s.setLevel(5);check(phases.isEmpty()&&calls[3]==1&&s.extraCp==7.5&&s.cp==42&&s.overload==19&&s.levelExperience==.73,"same-level call preserves every ledger and emits no event");s.learn("space_fluct");check(phases.isEmpty()&&calls[0]==1&&s.cp==42,"duplicate learn preserves CP");
  int before=calls[1]+calls[2];for(double invalid:new double[]{-1,Double.NaN,Double.POSITIVE_INFINITY,Double.MAX_VALUE}){s.addExperienceRaw("space_fluct",invalid);s.addExperience("space_fluct",invalid);}check(before==calls[1]+calls[2]&&s.cp==42,"existing modern nonfinite/negative award guards stay fail closed");
  boolean rejected=false;try{s.addExperienceRaw("arc_gen",.1);}catch(IllegalStateException e){rejected=true;}check(rejected&&!s.learned("arc_gen")&&before==calls[1]+calls[2],"wrong-category awards reject without events");
  // Apply twice to a single event: once-only effects must not refill twice.
  int[] effects={0};var event=new SkillLearnEvent(null,"teleporter","space_fluct",s,()->effects[0]++);AbilityProgressLifecycle.learned(event);AbilityProgressLifecycle.learned(event);check(effects[0]==1,"native dispatcher applies an event's source common effects once");
  var identity=new AbilityProgress();identity.selectCategory("teleporter");identity.setLevel(1);identity.learn("space_fluct");var awards=new ArrayList<AbilityProgress.ExperienceAward>();
  identity.bindProgressEvents((id,common)->common.run(),(level,common)->common.run(),award->{awards.add(award);identity.changeCategoryClassic("electromaster");},awards::add);identity.addExperienceRaw("space_fluct",.2f);
  check(awards.size()==2&&awards.get(0)==awards.get(1)&&awards.get(1).category().equals("teleporter")&&awards.get(1).skill().equals("space_fluct")&&awards.get(1).amount()==.2f&&identity.category.equals("electromaster"),"XP event identity and amount stay fixed while listeners mutate live category");
  System.out.println("PASS "+checks+" actual NeoForge progress-event priority, payload, once-only, duplicate/no-op and guard checks; no game bootstrap");
 }
}
