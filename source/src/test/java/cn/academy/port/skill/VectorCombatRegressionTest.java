package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
public final class VectorCombatRegressionTest {
 private static int checks;private static void check(boolean ok,String why){checks++;if(!ok)throw new AssertionError(why);}private static void near(double a,double b,String why){check(Math.abs(a-b)<1E-5,why+" actual="+a+" expected="+b);}
 public static void main(String[] args){
  for(double e:new double[]{0,.15,.249999,.25,.449999,.45,.499999,.5,1}){near(VectorCombatRules.blastCp(e),160+(float)e*40,"blastFloatCP");near(VectorCombatRules.blastOverload(e),50+(float)e*(-20),"blastFloatstrain");near(VectorCombatRules.blastDamage(e),10+(float)e*15,"blastFloatdamage");near(VectorCombatRules.bloodCp(e),280+(float)e*70,"bloodFloatCP");near(VectorCombatRules.bloodDamage(e),30+(float)e*30,"bloodFloatdamage");near(VectorCombatRules.stormCp(e),40+(float)e*(-15),"stormFloatCP");near(VectorCombatRules.stormCharge(e),70+(float)e*(-40),"stormFloatcharge");}
  check(VectorCombatRules.blastHardness(Math.nextDown(.25F))==2.9F&&VectorCombatRules.blastHardness(.25F)==25&&VectorCombatRules.blastHardness(.5F)==55,"captured hardness thresholds");near(VectorCombatRules.stormSpeed(.449999F),.7F*(2+.449999F),"pre.45 speed");near(VectorCombatRules.stormSpeed(.45F),1.2F*(2+.45F),"inclusive.45 speed");
  check(VectorCombatRules.blastCenter(-1.5)==-1&&VectorCombatRules.blastCenter(1.5)==2,"Scala round negative asymmetry");check(VectorCombatRules.stormProbe(-.9,0)==0&&VectorCombatRules.stormProbe(-1.9,0)==-1,"Scala probe truncation");near(VectorCombatRules.approach(0,4),.16,"axis acceleration cap");near(VectorCombatRules.approach(.1,.05),.05,"axis arrival no overshoot");
  for(int t:new int[]{0,6,7,49,50,199,200}){var s=ready("dir_blast",3,0);double cp=s.cp;var ctx=DirectedBlastwaveSession.begin(s);boolean ok=ctx.release(t,false);check(ok==(t>6&&t<50),"strict release "+t);near(s.cp,cp-(ok?160:0),"single finite debit");check(!ctx.release(t,false),"replay release");if(ok){ctx.award(false);near(s.exp("dir_blast"),.0012F,"paid empty missEXP");check(s.cooldowns.get("dir_blast")==80,"captured cooldown");}}
  var s=ready("dir_blast",3,0);var blast=DirectedBlastwaveSession.begin(s);s.experience.put("dir_blast",1D);check(blast.release(7,false),"capture tested");near(blast.damage(),10,"blast captured damage");near(s.overload,30,"blast dynamic strain");near(blast.cp(),160,"blast captured CP");check(blast.cooldown()==80,"blast captured cooldown");blast.award(true);near(s.exp("dir_blast"),1,"experience cap");
  s=ready("dir_blast",3,0);s.cp=159;blast=DirectedBlastwaveSession.begin(s);check(!blast.release(7,false)&&s.cooldowns.isEmpty()&&s.exp("dir_blast")==0,"failedCP no blast mutation");
  s=ready("blood_retro",4,0);var blood=BloodRetrogradeSession.begin(s);s.experience.put("blood_retro",1D);check(blood.release(true,false),"blood immediate release");near(s.overload,40,"blood dynamic strain");near(s.cp,s.maxCp()-350-.875,"blood dynamic CP");near(blood.damage(),30,"blood captured damage");check(s.cooldowns.get("blood_retro")==40,"blood dynamic cooldown");
  s=ready("blood_retro",4,0);blood=BloodRetrogradeSession.begin(s);for(int t=1;t<=30;t++){blood.tick();near(blood.walkSpeed(),t<20?.1F+(t/20F)*(.007F-.1F):.007F,"blood 20tick slow curve");check(blood.autoRelease()==(t>=30),"blood exact auto30");}check(!blood.release(false,false)&&s.exp("blood_retro")==0,"no target free cancel");
  for(double mastery:new double[]{0,1}){s=ready("storm_wing",3,mastery);var storm=StormWingSession.begin(s);double cp=s.cp;int boundary=(int)storm.charge();for(int i=1;i<=boundary;i++){check(storm.tick(false)&&storm.phase()==0,"strictcharge "+i);near(s.cp,cp,"chargefree");}check(storm.tick(false)&&storm.phase()==1&&storm.ticks()==0,"transition strictgreater");near(s.cp,cp,"transition free");check(storm.tick(false),"first active tick");near(s.cp,(float)cp-VectorCombatRules.stormCp(mastery)*StormWingSession.SUSTAIN_CP,"sustained activeCP");near(s.exp("storm_wing"),Math.min(1,mastery+StormWingSession.EXPERIENCE),"source activeEXP");storm.terminate(true);check(s.cooldowns.get("storm_wing")==VectorCombatRules.stormCooldown(s.exp("storm_wing")),"dynamic end cooldown");}
  s=ready("storm_wing",3,0);var storm=StormWingSession.begin(s);for(int i=0;i<71;i++)storm.tick(false);float short_=storm.sustainCp()-.5F;s.cp=short_;check(!storm.tick(false)&&!storm.active(),"failed activeCP ends");near(s.exp("storm_wing"),.00005F,"EXP precedes failedconsume");near(s.cp,short_,"failedconsume unchangedCP");
  for(String id:new String[]{"dir_blast","storm_wing","blood_retro"})for(double invalid:new double[]{Double.NaN,Double.POSITIVE_INFINITY}){s=ready(id,id.equals("blood_retro")?4:3,0);s.cp=invalid;check(!VectorCombatRules.eligible(s,id,s.level),"nonfiniteCP failclosed");s.cp=1000;s.experience.put(id,invalid);check(!VectorCombatRules.eligible(s,id,s.level),"nonfiniteEXP failclosed");}
  flightEndurance();
  System.out.println("PASS "+checks+" vector combat source arithmetic and session checks");
 }
 private static void flightEndurance(){
  for(boolean hover:new boolean[]{false,true}){
   var s=ready("storm_wing",3,0);var flight=StormWingSession.begin(s);flight.hovering(hover);
   int duration=0;boolean cooled=false;double initial=s.cp;
   while(duration<4000&&flight.active()){
    s.tick();if(!flight.tick(false))break;
    if(flight.phase()==StormWingSession.ACTIVE){duration++;cooled|=flight.cooling();check(s.overloadFine,"flight cools before forced overheat");}
   }
   check(duration>=600,"LV3 untrained flight lasts at least 30 seconds: "+duration);
   check(duration<4000&&s.cp<initial,"finite CP still ends flight");
   if(!hover)check(cooled,"extended active flight enters cooling before shutdown");
   System.out.println("STORM_ENDURANCE hover="+hover+" activeTicks="+duration+" seconds="+duration/20.0+" cooling="+cooled);
  }
  var s=ready("storm_wing",3,0);var flight=StormWingSession.begin(s);for(int i=0;i<=70;i++)flight.tick(false);
  s.overload=s.maxOverload()*.9;s.overloadDelay=0;double heat=s.overload;
  flight.tick(false);check(flight.cooling()&&flight.flightSpeed()<flight.speed(),"high heat throttles rather than aborts");s.tick();check(s.overload<heat,"cooling allows native heat recovery");
  s.overload=s.maxOverload()*.65;flight.tick(false);check(flight.cooling(),"hysteresis keeps cooling above60 percent");
  s.overload=s.maxOverload()*.59;flight.tick(false);check(!flight.cooling(),"cooling ends below60 percent");
 }
 private static AbilityProgress ready(String id,int level,double exp){var s=new AbilityProgress();s.selectCategory("vecmanip");s.setLevel(level);s.learn(id);s.experience.put(id,exp);s.activated=true;return s;}
}
