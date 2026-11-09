/* AcademyCraft1.0.7 RBContext / LambdaLib1.2.3 Motion3D adaptation. GPLv3/MIT; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;
/** Source five-corner AABB and inclusive angular filter; no spherical distance or LOS refinement. */
public final class RayBarrageRules {
 public static final String ID="ray_barrage";public static final int LEVEL=4;public static final double RANGE = SkillTuning.number("ray_barrage", "reach");
 public record Plan(float mastery,float cp,float overload,float plainDamage,float scatteredDamage,int cooldown){}
 private RayBarrageRules(){}
 private static float exp(double e){return(float)ClassicRules.clamp(e,0,1);}private static float lerp(float a,float b,double e){return a+exp(e)*(b-a);}
 public static Plan plan(double e){float f=exp(e);return new Plan(f,lerp(450,380,f),lerp(300,140,f),lerp(25,60,f),lerp(10,18,f),(int)lerp(100,40,f));}
 public static boolean mayStart(AbilityProgress s){return s!=null&&s.category.equals("meltdowner")&&s.level>=LEVEL&&s.canUse(ID);}
 public static ClassicBeamRay.Box bounds(ClassicBeamRay.Vec feet,float yaw,float pitch){double minX=feet.x(),minY=feet.y(),minZ=feet.z(),maxX=minX,maxY=minY,maxZ=minZ;for(float y:new float[]{yaw-27.5F,yaw+27.5F})for(float p:new float[]{pitch-55,pitch+55}){var d=rawDirection(y,p).scale(RANGE).add(feet);minX=Math.min(minX,d.x());minY=Math.min(minY,d.y());minZ=Math.min(minZ,d.z());maxX=Math.max(maxX,d.x());maxY=Math.max(maxY,d.y());maxZ=Math.max(maxZ,d.z());}return new ClassicBeamRay.Box(minX,minY,minZ,maxX,maxY,maxZ);}
 /** fromRotation does not normalize the Float lookup-table vector before move(). */
 public static ClassicBeamRay.Vec rawDirection(float yaw,float pitch){var p=cn.academy.port.clientless.ClassicLateTrig.direction(yaw,pitch);return new ClassicBeamRay.Vec(p[0],p[1],p[2]);}
 private static float wrap(float a){a%=360;if(a>=180)a-=360;if(a< -180)a+=360;return a;}
 public static boolean yawInRange(float start,float end,float angle){if(end<start)return false;if(end-start>=360)return true;float a=wrap(start),b=wrap(end),c=wrap(angle);return a>b?a<=c||c<=b:a<=c&&c<=b;}
 public static boolean angular(double dx,double eyeDy,double dz,float bodyYaw,float pitch){float y=-(float)(Math.atan2(dx,dz)*180/Math.PI),p=-(float)(Math.atan2(eyeDy,Math.sqrt(dx*dx+dz*dz))*180/Math.PI);return yawInRange(bodyYaw-27.5F,bodyYaw+27.5F,y)&&pitch-55<=p&&p<=pitch+55;}
 /** Original terminate is deferred and mToSelf executes even after unsuccessful consume. */
 public static boolean debit(AbilityProgress s,Plan p,boolean creative){return s.consumeSkill(ID,p.cp,p.overload,creative);}
 public static void complete(AbilityProgress s,Plan p){s.setCooldown(ID,p.cooldown);s.addExperience(ID,.005F);}
}
