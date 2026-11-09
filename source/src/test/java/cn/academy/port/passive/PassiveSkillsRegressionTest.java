package cn.academy.port.passive;
import cn.academy.port.*;
import cn.academy.port.core.*;
import cn.academy.port.skill.*;
import cn.academy.port.preset.PresetSkills;
import net.minecraft.nbt.*;
import java.io.*;
import java.util.*;
public final class PassiveSkillsRegressionTest {
 static int checks;
 static void yes(boolean value,String label){checks++;if(!value)throw new AssertionError(label);}
 static void bits(double actual,float expected,String label){yes(Double.doubleToRawLongBits(actual)==Double.doubleToRawLongBits((double)expected),label+" actual="+actual+" expected="+expected);}
 static AbilityProgress state(String category,int level){var s=new AbilityProgress();s.selectCategory(category);s.setLevel(level);return s;}
 public static void main(String[] args)throws Exception {
  for(String category:List.of("electromaster","meltdowner","teleporter","vecmanip")){
   var s=state(category,5);s.extraCp=40;s.extraOverload=3;s.cp=1;s.overload=99;s.cpDelay=9;s.overloadDelay=13;s.overloadFine=false;
   s.learn("brain_course");yes(s.cp==9040&&s.overload==0&&s.maxCp()==9040&&s.extraCp==40,"brain course native recalculation/refill and training retention");
   s.learn("brain_course_advanced");yes(s.maxCp()==10540&&s.maxOverload()==603,"advanced course adds1500CP/100O cumulatively");
   s.learn("mind_course");s.cp=0;s.cpDelay=0;s.tick();bits(s.cp,1.2f*(1f*.0003f*10500f*(1f+0f*(2f-1f))),"learned mind course exact float recovery");
   yes(s.overloadFine==false&&s.overloadDelay==12,"learning preserves source lock and recovery delays");
   double remaining=s.cp;s.learn("mind_course");yes(s.cp==remaining,"idempotent course learning does not refill twice");
   var data=AbilityStorage.encode(s);var bytes=new ByteArrayOutputStream();NbtIo.writeCompressed(data,bytes);var loaded=AbilityStorage.decode(NbtIo.readCompressed(new ByteArrayInputStream(bytes.toByteArray()),NbtAccounter.unlimitedHeap()));
   yes(loaded.maxCp()==s.maxCp()&&loaded.maxOverload()==s.maxOverload()&&loaded.learned("mind_course")&&loaded.cp==s.cp,"cold compressed NBT retains courses/effects");
   for(String id:SkillAvailability.PASSIVES){yes(!PresetSkills.selectable(loaded,id),"course/passive hidden from preset actions");yes(!loaded.presets.edit(0,0,id,v->PresetSkills.selectable(loaded,v)),"preset edit rejects passive");}
   s.selectCategory("teleporter");yes(!s.learned("brain_course")&&s.maxCp()==1800&&s.maxOverload()==100,"category reset loses learned generic instances");
  }
  var s=state("meltdowner",1);s.learn("rad_intensify");s.extraCp=900;bits(s.exp("rad_intensify"),2700f/8000f,"customized radiation mastery includes trained CP");bits(RadiationMarks.rate(s.exp("rad_intensify")),1.4f+(2700f/8000f)*(1.8f-1.4f),"radiation exact float rate");
  s.setLevel(5);s.learn("brain_course");s.learn("brain_course_advanced");s.extraCp=12000;bits(s.exp("rad_intensify"),1f,"radiation mastery clamp with both generic courses");s=state("teleporter",1);s.learn("dim_folding_theorem");s.cp=10;s.overload=50;s.cpDelay=4;s.overloadDelay=8;s.overloadFine=false;
  ThreateningTeleport.recordCritical(s,0);yes(s.learned("space_fluct")&&s.level==1&&s.cp==s.maxCp()&&s.overload==0,"source critical auto-learns level4 passive and refills");bits(s.exp("dim_folding_theorem"),.005f,"folding source award");bits(s.exp("space_fluct"),.0001f,"space source award");bits(s.levelExperience,.005f+.0001f,"source float level accumulation");
  s.cp=12;s.overload=14;float folding=.005f,space=.0001f,progress=.005f+.0001f;
  for(int i=0;i<400;i++){int tier=i%3;ThreateningTeleport.recordCritical(s,tier);float amt=(tier+1)*.005f;folding+=Math.min(1f-folding,amt);space+=Math.min(1f-space,.0001f);progress+=amt;progress+=.0001f;}
  bits(s.exp("dim_folding_theorem"),folding,"source saturation uses remaining mastery");bits(s.exp("space_fluct"),space,"source cumulative space float mastery");bits(s.levelExperience,progress,"source full progress awards after saturation");yes(s.cp==12&&s.overload==14&&s.cpDelay==4&&s.overloadDelay==8&&!s.overloadFine,"already learned critical never refills or unlocks");
  s=state("teleporter",5);s.learn("space_fluct");s.experience.put("space_fluct",1d);double p=ThreateningTeleport.criticalProbability(s,0);var ready=s;yes(ThreateningTeleport.criticalTier(ready,()->p)==-1,"strict source float probability boundary");yes(ThreateningTeleport.criticalTier(ready,()->Math.nextDown(p))==0,"one double bit below source probability succeeds");
  var custom=state("electromaster",5);custom.learn("brain_course");custom.learn("brain_course_advanced");custom.bindCalculations(e->{ClassicPassiveSkills.calculate(custom,e);if(e.kind==AbilityCalculation.Kind.MAX_CP)e.value*=2;if(e.kind==AbilityCalculation.Kind.MAX_OVERLOAD)e.value+=31;});custom.learn("mind_course");custom.cp=20001;custom.overload=611;
  var saved=AbilityStorage.encode(custom);var cold=AbilityStorage.decode(saved);yes(cold.baseCp()==21000&&cold.baseOverload()==631&&cold.cp==20001&&cold.overload==611,"source raw calculated maxima retain extension effects and full current values across cold NBT");
  var maxEvents=new int[1];cold.bindCalculations(e->{if(e.kind==AbilityCalculation.Kind.MAX_CP)maxEvents[0]++;});yes(cold.baseCp()==21000&&maxEvents[0]==0,"native rebinding preserves restored source cached maximum without reposting");
  var legacy=saved.copy();legacy.remove("raw_max_cp");legacy.remove("raw_max_overload");legacy.putInt("schema",2);yes(AbilityStorage.decode(legacy).maxCp()==10500,"pre-existing schema2 data migrates without invented extension capacity");
  for(double corrupt:new double[]{Double.NaN,Double.POSITIVE_INFINITY,-1,Double.MAX_VALUE}){var bad=saved.copy();bad.putDouble("raw_max_cp",corrupt);var bounded=AbilityStorage.decode(bad);yes(Double.isFinite(bounded.maxCp())&&bounded.maxCp()==10500,"malformed calculated maximum rejected");}
  var recovery=state("electromaster",5);recovery.learn("mind_course");recovery.cp=3777.314f;recovery.cpDelay=0;float expected=(float)recovery.cp+ClassicPassiveSkills.cpRecovery(recovery.cp,recovery.baseCp(),1,1.2f);recovery.tick();bits(recovery.cp,expected,"source learned MindCourse tick performs float cumulative CP addition");
  System.out.println("PASS "+checks+" passive/generic ledger, compressed NBT, float progression and hidden-preset checks");
 }
}
