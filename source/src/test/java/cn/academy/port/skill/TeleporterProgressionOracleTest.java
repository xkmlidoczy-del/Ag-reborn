package cn.academy.port.skill;
import cn.academy.port.core.SkillTuning;
import java.util.Random;
import java.util.regex.Pattern;
import static cn.academy.port.skill.TeleporterProgressionRules.*;
/** Independently mechanically translated classic scanner with constants read from the pinned Scala file.
 * The scanner is exercised at the original and tuned ranges. Only the range endpoints
 * are taken from the tuning table. This does not execute Scala1.7 or Minecraft. */
public final class TeleporterProgressionOracleTest {
 static float curve(String src,String method,double exp){var m=Pattern.compile("def "+method+"[^\\n]*lerpf\\(([-.0-9]+), ([-.0-9]+), exp\\)").matcher(src);if(!m.find())throw new AssertionError(method);float a=Float.parseFloat(m.group(1)),b=Float.parseFloat(m.group(2));return a+(float)exp*(b-a);}
 static float tunedReach(String skill, double exp) {
  float novice=(float)SkillTuning.number(skill,"reach_novice");
  float mastered=(float)SkillTuning.number(skill,"reach_mastered");
  return novice+(float)exp*(mastered-novice);
 }
 static Destination oracle(Point eye,Point dir,float range,float cpb,double cp,Place place){double dist=Math.min(range,cp/cpb),total=0;int stage=0,counter=0,advances=0;Point mo=eye;while(total<=dist){boolean b=place.clear((int)mo.x(),(int)mo.y(),(int)mo.z());if(stage==0){if(!b)stage=1;}else if(stage==1){if(b)stage=2;}else {if(!b||++counter>4)break;}total+=.8;mo=new Point(mo.x()+dir.x()*.8,mo.y()+dir.y()*.8,mo.z()+dir.z()*.8);advances++;}return new Destination(mo,stage!=1,stage,advances);}
 public static void main(String[] args){TeleporterSourceFixtures.verify();String pt=TeleporterSourceFixtures.skill("PenetrateTeleport"),mt=TeleporterSourceFixtures.skill("MarkTeleport"),fr=TeleporterSourceFixtures.skill("FleshRipping");for(String v:new String[]{"val STEP: Double = 0.8","while(totalStep <= dist)","counter += 1; counter} > 4","new Dest(mo.getPosVec, stage != 1)","ctx.consumeWithForce(overload, (distance * getConsumption(exp)).toFloat)","0.00014f * distance.toFloat"})TeleporterSourceFixtures.contains(pt,v);TeleporterSourceFixtures.contains(pt,"if(!dest.available) {\n      terminate()\n    }\n\n    val x");Random r=new Random(107);int samples=0, changedByReach=0;for(int i=0;i<12000;i++){double e=r.nextInt(101)/100.0,cp=r.nextDouble()*500;Point eye=new Point(-20+r.nextDouble()*40,-5+r.nextDouble()*20,-20+r.nextDouble()*40),dir=direction(-180+r.nextFloat()*360,-80+r.nextFloat()*160);int pattern=r.nextInt(6);Place place=(x,y,z)->switch(pattern){case 0->true;case 1->false;case 2->(x&3)!=0;case 3->(z&3)!=0;case 4->(y&3)!=0;default->(x*x+y*y+z*z)%7>2;};float costPerBlock=curve(pt,"getConsumption",e);
   var original=oracle(eye,dir,curve(pt,"getMaxDistance",e),costPerBlock,cp,place);
   var expected=oracle(eye,dir,tunedReach("penetrate_teleport",e),costPerBlock,cp,place);
   if(!original.equals(expected))changedByReach++;
   var actual=penetrate(eye,dir,e,cp,place);if(!actual.equals(expected))throw new AssertionError("scanner "+actual+" != "+expected);samples++;}
  for(String v:new String[]{"Math.min((ticks + 1) * 2, Math.min(max, cplim))","MINIMUM_VALID_DISTANCE: Double = 3.0","y -= 1.0","y += 1.8","z -= .6","z += .6","x -= .6","x += .6","y = mop.blockY + 1.7","if(!player.worldObj.isAirBlock(hx, hy, hz)) y -= 1.25","else y += mop.entityHit.getEyeHeight","0.00018f * distance"})TeleporterSourceFixtures.contains(mt,v);for(int i=0;i<1000;i++){double exp=r.nextInt(101)/100.0,cp=r.nextDouble()*300;int ticks=r.nextInt(80);double max=tunedReach("mark_teleport",exp),cpb=curve(mt,"getCPB",exp),expected=Math.min((ticks+1)*2,Math.min(max,cp/cpb));TeleporterProgressionRegressionTest.near(markMaxDistance(exp,cp,ticks),expected);}for(int side=0;side<6;side++)for(boolean clear:new boolean[]{true,false}){Point hit=new Point(-.2,5.3,-.7);var at=markBlock(hit,side,5,(x,y,z)->clear);double x=hit.x(),y=hit.y(),z=hit.z();if(side==0)y-=1;if(side==1)y+=1.8;if(side==2){z-=.6;y=6.7;}if(side==3){z+=.6;y=6.7;}if(side==4){x-=.6;y=6.7;}if(side==5){x+=.6;y=6.7;}if(side>1&&!clear)y-=1.25;if(!at.equals(new Point(x,y,z)))throw new AssertionError("block face "+side);}
  for(String v:new String[]{"lerpf(5, 12, exp)","lerpf(6, 14, exp)","lerpf(130, 270, exp)","lerpf(60, 50, exp)","lerpf(90, 40, exp)","private def getDisgustProb: Float = .05f","Potion.confusion.id, 100","ctx.addSkillExp(.005f)","Raytrace.traceLiving(player, range, EntitySelectors.living)","TPSkillHelper.attackIgnoreArmor(ctx, target.target, getDamage)","for(i <- 0 to RandUtils.rangei(4, 6))"})TeleporterSourceFixtures.contains(fr,v);for(int i=0;i<=1000;i++){
   double exp=i/1000.0;
   TeleporterProgressionRegressionTest.near(fleshRange(exp),tunedReach("flesh_ripping",exp));
  }
  System.out.println("PASS pinned classic commit fixtures: "+samples+" independent source-scanner comparisons ("+changedByReach
      +" changed by the tuned endpoints), 1000 charge-cap comparisons, 12 hit-face/head cases and Flesh reach checks");}
}
