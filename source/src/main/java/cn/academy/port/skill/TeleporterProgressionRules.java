/* AcademyCraft 1.0.7 PTContext/MTContext/FRContext adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
/** Pure source arithmetic and geometry. No client-supplied endpoint enters gameplay. */
public final class TeleporterProgressionRules {
 public static final String PENETRATE="penetrate_teleport",MARK="mark_teleport",FLESH="flesh_ripping";
 private TeleporterProgressionRules(){}
 public record Point(double x,double y,double z){public Point add(Point p){return new Point(x+p.x,y+p.y,z+p.z);}public Point scale(double n){return new Point(x*n,y*n,z*n);}public double distance(Point p){return Math.sqrt((x-p.x)*(x-p.x)+(y-p.y)*(y-p.y)+(z-p.z)*(z-p.z));}}
 public static Point direction(float yaw,float pitch){double[] d=cn.academy.port.clientless.ClassicLateTrig.direction(yaw,pitch);double length=Math.sqrt(d[0]*d[0]+d[1]*d[1]+d[2]*d[2]);return new Point(d[0]/length,d[1]/length,d[2]/length);}
 public record Destination(Point position,boolean available,int stage,int advances){}
 @FunctionalInterface public interface Place {boolean clear(int x,int y,int z);}
 public static float lerp(float a,float b,double e){return a+(float)ClassicRules.clamp(e,0,1)*(b-a);}
 public static float penetrateConsumption(double e){return lerp(14,9,e);}public static float penetrateRange(double e){return lerp((float) SkillTuning.number("penetrate_teleport", "reach_novice"),(float) SkillTuning.number("penetrate_teleport", "reach_mastered"),e);}public static float penetrateOverload(double e){return lerp(80,50,e);}public static int penetrateCooldown(double e){return(int)lerp(50,30,e);}public static float penetrateExperience(double distance){return .00014F*(float)distance;}
 /** Legacy starts at eyes, truncates all coordinates toward zero, and advances one .8 step past cap.
  * Stage2 breaks on fifth clear probe or first obstructed probe, without backing up. Stage0 is available. */
 public static Destination penetrate(Point eye,Point direction,double exp,double cp,Place place){double max=Math.min(penetrateRange(exp),cp/penetrateConsumption(exp));double travelled=0;Point cursor=eye;int stage=0,counter=0,advances=0;while(travelled<=max){boolean clear=place.clear((int)cursor.x,(int)cursor.y,(int)cursor.z);if(stage==0){if(!clear)stage=1;}else if(stage==1){if(clear)stage=2;}else if(!clear||++counter>4)break;travelled+=.8;cursor=cursor.add(direction.scale(.8));advances++;}return new Destination(cursor,stage!=1,stage,advances);}
 public static float markConsumption(double e){return lerp(12,4,e);}public static float markRange(double e){return lerp((float) SkillTuning.number("mark_teleport", "reach_novice"),(float) SkillTuning.number("mark_teleport", "reach_mastered"),e);}public static double markMaxDistance(double e,double cp,int ticks){return Math.min(((double)ticks+1)*2,Math.min(markRange(e),cp/markConsumption(e)));}public static float markOverload(double e){return lerp(40,20,e);}public static int markCooldown(double e){return(int)lerp(30,0,e);}public static float markExperience(double distance){return .00018F*(float)distance;}
 /** Legacy hit-face indexes DOWN,UP,NORTH,SOUTH,WEST,EAST; side-head probes use truncation. */
 public static Point markBlock(Point hit,int side,int blockY,Place air){double x=hit.x,y=hit.y,z=hit.z;switch(side){case 0->y-=1;case 1->y+=1.8;case 2->{z-=.6;y=blockY+1.7;}case 3->{z+=.6;y=blockY+1.7;}case 4->{x-=.6;y=blockY+1.7;}case 5->{x+=.6;y=blockY+1.7;}default->throw new IllegalArgumentException("Block side must be 0..5");}if(side>1&&!air.clear((int)x,(int)(y+1),(int)z))y-=1.25;return new Point(x,y,z);}
 public static float fleshDamage(double e){return lerp(5,12,e);}public static float fleshRange(double e){return lerp((float) SkillTuning.number("flesh_ripping", "reach_novice"),(float) SkillTuning.number("flesh_ripping", "reach_mastered"),e);}public static float fleshConsumption(double e){return lerp(130,270,e);}public static float fleshOverload(double e){return lerp(60,50,e);}public static int fleshCooldown(double e){return(int)lerp(90,40,e);}public static final float FLESH_EXP=.005F,NAUSEA_PROBABILITY=.05F;public static final int NAUSEA_TICKS=100;
 /** performWithForce floors CP while training the entire requested cost, even if overshoot exceeds CP. */
 public static boolean consumeWithForce(AbilityProgress s,double cp,double overload,boolean creative){return consumeWithForce(s,"",cp,overload,creative);}
 public static boolean consumeWithForce(AbilityProgress s,String skill,double cp,double overload,boolean creative){return s!=null&&s.consumeWithForceSkill(skill,cp,overload,creative);}
}
