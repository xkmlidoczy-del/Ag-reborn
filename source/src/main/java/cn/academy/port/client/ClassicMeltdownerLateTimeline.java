/* AcademyCraft1.0.7 late Meltdowner effects / LambdaLib1.2.3 render math, GPLv3/MIT; see NOTICE. */
package cn.academy.port.client;
import java.util.*;
import cn.academy.port.clientless.ClassicLateTrig;
/** Explicit original variants, never an alias to another skill's effect. No original arm/body override exists. */
public final class ClassicMeltdownerLateTimeline {
 public static final String BARRAGE="ray_barrage",JET="jet_engine",MISSILE="electron_missile",RAY_SOUND="md.ray_small";
 public static final ClassicMeltdownerBeamTimeline.Spec SMALL=new ClassicMeltdownerBeamTimeline.Spec(14,200,400,500,.03,.045,.3,.5,1),PRE_PLAIN=new ClassicMeltdownerBeamTimeline.Spec(30,200,400,500,.045,.052,.4,.5,0),PRE_HIT=new ClassicMeltdownerBeamTimeline.Spec(50,200,400,500,.045,.052,.4,.5,0),BARRAGE_RAY=new ClassicMeltdownerBeamTimeline.Spec(50,100,300,300,.03,.045,.3,.5,0);
 private ClassicMeltdownerLateTimeline(){}
 public record SubRay(float yaw,float pitch){}
 public static List<SubRay> subrays(Random r){float max=50+r.nextFloat()*(60-50);int count=25+r.nextInt(30-25);var result=new ArrayList<SubRay>(count);for(int i=0;i<count;i++)result.add(new SubRay(-max+r.nextFloat()*(2*max),-max/2+r.nextFloat()*max));return List.copyOf(result);}
 public static double width(ClassicMeltdownerBeamTimeline.Spec s,long age,double wiggle){if(s==PRE_HIT||s==PRE_PLAIN)return 1; if(s==SMALL)return age>s.lifeMillis()-500?1-Math.max(0,Math.min(1,(double)(age-(s.lifeMillis()-500))/500)):1;return ClassicMeltdownerBeamTimeline.width(s,age,wiggle);}
 public static float rippleHeight(long mod){return mod*3E-4F;}public static float rippleSize(long mod){return 1.9F+(float)mod/3600*(1.4F-1.9F);}public static float rippleAlpha(long mod){if(mod<1600)return mod/1600F;if(mod>2000)return 1-(mod-2000)/1600F;return 1;}
 public static long ripplePhase(long age,int index){return(age+1200L*index)%3600;}
 public static int missileParticleCount(Random r){return 1+r.nextInt(3-1);} // original rangei(1,3) is half-open
 public static ClassicMeltdownerStarterTimeline.Point missileParticle(ClassicMeltdownerStarterTimeline.Point feet,double r,double theta,double h){return feet.add(new ClassicMeltdownerStarterTimeline.Point(r*Math.sin(theta),1.6+h,r*Math.cos(theta)));}
 public static ClassicMeltdownerStarterTimeline.Point jetParticle(ClassicMeltdownerStarterTimeline.Point feet,boolean local,double x,double y,double z){return feet.add(new ClassicMeltdownerStarterTimeline.Point(x,(local?1.6:0)+y,z));}
 public static double[] direction(float yaw,float pitch){double[] d=ClassicLateTrig.direction(yaw,pitch);double n=Math.sqrt(d[0]*d[0]+d[1]*d[1]+d[2]*d[2]);return new double[]{d[0]/n,d[1]/n,d[2]/n};}
 public static final double[][] DIAMOND_VERTICES={{-1,0,0},{0,-1,0},{1,0,0},{0,1,0},{0,0,1}},DIAMOND_UVS={{0,0},{1,1},{0,0},{1,1},{0,1}};
 public static final int[] DIAMOND_TRIANGLES={0,1,4,1,2,4,2,3,4,3,0,4};
 public static final float DIAMOND_SCALE=1.5F,JET_WALK=.07F,RESTORE_WALK=.1F;
}
