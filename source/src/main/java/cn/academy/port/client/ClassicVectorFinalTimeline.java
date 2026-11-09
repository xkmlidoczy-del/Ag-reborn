/* AcademyCraft1.0.7 WaveEffect/TornadoEffect/PlasmaBodyEffect literal math. GPLv3; see NOTICE. */
package cn.academy.port.client;
import java.util.*;
public final class ClassicVectorFinalTimeline {
 public static final String REFLECTION_SOUND="vecmanip.vec_reflection",PLASMA_SOUND="vecmanip.plasma_cannon",READY_SOUND="vecmanip.plasma_cannon_t";public static final int DIVIDE=40,SECTIONS=20;public static final double HEIGHT=12,SIZE=8,DENSITY=1,DRIFT_SCALE=.3,BODY_SIZE=22;
 private static final float[] SIN=new float[65536];static{for(int i=0;i<SIN.length;i++)SIN[i]=(float)Math.sin(i*Math.PI*2/65536);}private static float sin(float v){return SIN[(int)(v*10430.378F)&65535];}private static float cos(float v){return SIN[(int)(v*10430.378F+16384F)&65535];}
 private ClassicVectorFinalTimeline(){}
 public record Trig(float amp,float speed,float phase){public float phaseAt(float t){return speed*t-phase;}}
 public record Ball(float size,float x,float y,float z,Trig h,Trig v){}
 public record Ring(double y,double width,double phase,double scale){}
 private static float between(Random r,float a,float b){return a+r.nextFloat()*(b-a);}private static double between(Random r,double a,double b){return a+r.nextDouble()*(b-a);}
 private static Trig trig(Random r,float size){return new Trig(between(r,1.4F,2F)*size,between(r,.5F,.7F),between(r,0,(float)Math.PI*2));}
 public static List<Ball> balls(Random r){var balls=new ArrayList<Ball>();for(int i=0;i<4;i++)balls.add(new Ball(between(r,1,1.5F),between(r,-1.5F,1.5F),between(r,-1.5F,1.5F),between(r,-1.5F,1.5F),trig(r,1),trig(r,1)));int n=4+r.nextInt(2);for(int i=0;i<n;i++)balls.add(new Ball(between(r,.1F,.3F),between(r,-3F,3F),between(r,-3F,3F),between(r,-3F,3F),trig(r,2.5F),trig(r,2.5F)));return List.copyOf(balls);}
 public static double[] ball(Ball b,float dt){return new double[]{b.x()+b.h().amp()*sin(b.h().phaseAt(dt)),b.y()+b.v().amp()*sin(b.v().phaseAt(dt)),b.z()+b.h().amp()*cos(b.h().phaseAt(dt)),b.size()};}
 public static List<Ring> rings(Random r){var rings=new ArrayList<Ring>();double accum=0,step=HEIGHT/DIVIDE;while(accum<HEIGHT){accum+=step*(1+r.nextGaussian()*.2);if(r.nextDouble()<DENSITY){rings.add(new Ring(accum,step*between(r,1.8,2.2),r.nextDouble()*360,between(r,.9,1.2)));if(r.nextDouble()<.35)rings.add(new Ring(accum,step*between(r,1.8,2.2),r.nextDouble()*360,between(r,1.2,1.7)));}}return List.copyOf(rings);}
 public static double tornadoAlpha(int age,boolean dead,int deadTicks){return (dead?1-deadTicks/20F:age<20?age/20F:1)*.5*.7;}
 public static double[] ring(Ring r,double time){double ny=r.y()/HEIGHT,t=time*.1,amp=.3+Math.pow(ny*2,1.4);double x=ClassicVectorFinalNoise.noise(ny,t)*amp*SIZE*DRIFT_SCALE,z=ClassicVectorFinalNoise.noise(ny,t,1)*amp*SIZE*DRIFT_SCALE;double radius=((.5+.3*ClassicVectorFinalNoise.noise(ny,.2*time))+.5*Math.pow(1.5*ny,2)+ClassicVectorFinalNoise.noise(ny))*SIZE*r.scale();double rot=.1*(1+.5*ny)*time+r.phase();return new double[]{x,z,radius,rot};}
 public static float bodyAlpha(float alpha,float dt,boolean dead){float delta=(dead?0:1)-alpha;return alpha+Math.min(Math.abs(delta),dt*(dead?1:.3F))*Math.signum(delta);}
 /** Independent CPU transcription of original shader used for deterministic source checks. */
 public static double density(double[] p,List<double[]> balls,double alpha){double ret=0;for(var b:balls){double dx=p[0]-b[0],dy=p[1]-b[1],dz=p[2]-b[2],d=Math.max(.1,Math.sqrt(dx*dx+dy*dy+dz*dz));ret+=alpha*b[3]/(d*d);}return Math.max(0,Math.min(2,ret));}
 public static double[] rayMarch(double[] cam,List<double[]> balls,double alpha){double len=Math.sqrt(cam[0]*cam[0]+cam[1]*cam[1]+cam[2]*cam[2]);double[] dir={cam[0]/len,cam[1]/len,cam[2]/len},p={cam[0]-dir[0]*3,cam[1]-dir[1]*3,cam[2]-dir[2]*3},out={0,0,0,0};for(int i=0;i<20&&out[3]<1;i++){double d=density(p,balls,alpha),a=.075*d,t=1-d/2;double[] c={.43+(.98-.43)*t,.74+(.51-.74)*t,1+(.92-1)*t};for(int j=0;j<3;j++){out[j]=out[j]+(c[j]-out[j])*a/(out[3]+a);p[j]+=dir[j]*.15;}out[3]+=a;}if(out[3]<.2)out[3]=2*out[3]-.2;out[3]=Math.max(0,Math.min(1,out[3]))*(.5+alpha*.5);return out;}
}
