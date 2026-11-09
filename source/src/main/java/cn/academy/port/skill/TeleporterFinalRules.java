/* AcademyCraft classic1.0.7 LocationTeleport/STContext/Flashing adaptation. GPLv3; see NOTICE.
 * LambdaLib1.2.3 line/rotation semantics are MIT; see docs/LAMBDALIB-LICENSE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.core.ClassicRules;
/** Pure original float arithmetic and strict geometry, shared by common gameplay and previews. */
public final class TeleporterFinalRules {
 public static final String LOCATION="location_teleport",SHIFT="shift_tp",FLASH="flashing";
 private TeleporterFinalRules(){}
 public record Point(double x,double y,double z){public Point add(Point b){return new Point(x+b.x,y+b.y,z+b.z);}public Point scale(double n){return new Point(x*n,y*n,z*n);}}
 public static float lerp(float a,float b,double e){return a+(float)ClassicRules.clamp(e,0,1)*(b-a);}
 public static boolean crossDimension(double exp){return (float)exp>.8F;}
 public static float locationOverload(){return 240;}
 public static float locationConsumption(double exp,double distance,boolean cross){float d=(float)distance;return lerp(200,150,exp)*(cross?2:1)*Math.max(8F,(float)Math.sqrt(Math.min(800F,d)));}
 /** Classic measures distance AFTER all entities move, generally earning .015 even for a long jump. */
 public static float locationExperience(double postMoveDistance){return postMoveDistance>=200?.03F:.015F;}
 public static int locationCooldown(double updatedExp){return(int)lerp(30,20,updatedExp);}
 public static double locationRadius() { return SkillTuning.number("location_teleport", "radius"); }
 public static boolean locationCompanion(double width,double height,double distanceSquared){float w=(float)width,h=(float)height;return w*w*h<80F&&distanceSquared<=locationRadius()*locationRadius();}
 public static float shiftDamage(double e){return lerp(15,35,e);}public static float shiftRange(double e){return lerp((float) SkillTuning.number("shift_tp", "reach_novice"),(float) SkillTuning.number("shift_tp", "reach_mastered"),e);}public static float shiftConsumption(double e){return lerp(260,320,e);}public static float shiftOverload(double e){return lerp(40,30,e);}public static int shiftCooldown(double e){return(int)lerp(100,60,e);}public static float shiftExperience(int targets){return(1+targets)*.002F;}
 public static int[] shiftBlock(int x,int y,int z,int side){switch(side){case 0->y--;case 1->y++;case 2->z--;case 3->z++;case 4->x--;case 5->x++;default->throw new IllegalArgumentException("side must be0..5");}return new int[]{x,y,z};}
 public static int[] shiftFallback(Point eye,Point direction,double exp){Point end=eye.add(direction.scale(shiftRange(exp)));return new int[]{(int)end.x,(int)end.y,(int)end.z};}
 public static float flashConsumption(double e){return lerp(13,6,e);}public static float flashStartOverload(double e){return lerp(250,180,e);}public static float flashStartConsumption(double e){return lerp(80,60,e);}public static int flashMaxTime(double e){return(int)lerp((float) SkillTuning.number("flashing", "duration_ticks_novice"),(float) SkillTuning.number("flashing", "duration_ticks_mastered"),e);}public static int flashCooldown(double e){return(int)lerp(900,400,e);}public static float flashRange(double e){return lerp((float) SkillTuning.number("flashing", "reach_novice"),(float) SkillTuning.number("flashing", "reach_mastered"),e);}public static final float FLASH_EXP=.002F;
 public static boolean flashExpired(int ticks,double exp){return ticks>flashMaxTime(exp);}
 /** Source A,D,W,S IDs 1..4; rotateAroundZ(pitch), then rotateAroundY(-90-yaw).
  * Vec3 rotations use the Minecraft1.7.10 lookup-table float sin/cos; do not replace with modern trig. */
 public static Point flashDirection(int key,float yaw,float pitch){Point d=switch(key){case 1->new Point(0,0,-1);case 2->new Point(0,0,1);case 3->new Point(1,0,0);case 4->new Point(-1,0,0);default->throw new IllegalArgumentException("Flashing key must be1..4");};float angle=pitch*((float)Math.PI)/180F,c=cn.academy.port.clientless.ClassicLateTrig.cos(angle),s=cn.academy.port.clientless.ClassicLateTrig.sin(angle);double x=d.x*c+d.y*s,y=d.y*c-d.x*s,z=d.z;angle=(-90-yaw)*((float)Math.PI)/180F;c=cn.academy.port.clientless.ClassicLateTrig.cos(angle);s=cn.academy.port.clientless.ClassicLateTrig.sin(angle);return new Point(x*c+z*s,y,z*c-x*s);}
 @FunctionalInterface public interface Air {boolean clear(int x,int y,int z);}
 public static Point flashBlock(Point hit,int side,int blockY,Air air){double x=hit.x,y=hit.y,z=hit.z;switch(side){case 0->y-=1;case 1->y+=1.8;case 2->{z-=.6;y=blockY+1.7;}case 3->{z+=.6;y=blockY+1.7;}case 4->{x-=.6;y=blockY+1.7;}case 5->{x+=.6;y=blockY+1.7;}default->throw new IllegalArgumentException("side must be0..5");}if(side>1&&!air.clear((int)x,(int)(y+1),(int)z))y-=1.25;return new Point(x,y,z);}
 /** The legacy algorithm excludes tangent, endpoint-on-plane, and box-edge intersections. */
 public static boolean lineBox(Point min,Point max,Point a,Point b){if(a.x<min.x&&b.x<min.x||a.x>max.x&&b.x>max.x||a.y<min.y&&b.y<min.y||a.y>max.y&&b.y>max.y||a.z<min.z&&b.z<min.z||a.z>max.z&&b.z>max.z)return false;if(a.x>min.x&&a.x<max.x&&a.y>min.y&&a.y<max.y&&a.z>min.z&&a.z<max.z)return true;for(int axis=0;axis<3;axis++)for(double plane:new double[]{coord(min,axis),coord(max,axis)}){double da=coord(a,axis)-plane,db=coord(b,axis)-plane;if(da*db>=0||da==db)continue;double t=-da/(db-da);Point hit=new Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.z+(b.z-a.z)*t);int j=(axis+1)%3,k=(axis+2)%3;if(coord(hit,j)>coord(min,j)&&coord(hit,j)<coord(max,j)&&coord(hit,k)>coord(min,k)&&coord(hit,k)<coord(max,k))return true;}return false;}
 private static double coord(Point p,int i){return i==0?p.x:i==1?p.y:p.z;}
}
