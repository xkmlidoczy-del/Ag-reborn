/* Classic MarkRender/RenderMarker/EntityBloodSplash/TPParticleFactory, GPLv3. See NOTICE. */
package cn.academy.port.client;
import java.util.List;
/** Render-only classic contracts, also executable without Minecraft or a GPU. */
public final class ClassicTeleporterTimeline {
 private ClassicTeleporterTimeline(){}
 public static int markFrame(int ticks){return(int)((ticks/2.5)%7);}public static int bloodFrame(int ticks){return Math.min(9,Math.max(0,ticks));}public static boolean bloodAlive(int ticks){return ticks<10;}public static int bloodCount(int exclusiveRandom4to6){return exclusiveRandom4to6+1;}public static double markerBob(long millis){return .05*Math.sin(millis/400.0);}public static double particleAlpha(int age){return age<5?age/5.0:age<=20?1:Math.max(0,1-(age-20)/20.0);}public static boolean particleAlive(int age){return age<=40;}
 public record Box(float x,float y,float z,float width,float height,float depth,float inflate,int u,int v,boolean mirror){}
 /** ModelBiped(0): static, 64x32 UVs, includes inflated headwear. Rotation points already applied. */
 public static final List<Box> BIPED=List.of(new Box(-4,-8,-4,8,8,8,0,0,0,false),new Box(-4,0,-2,8,12,4,0,16,16,false),new Box(-8,0,-2,4,12,4,0,40,16,false),new Box(4,0,-2,4,12,4,0,40,16,true),new Box(-3.9F,12,-2,4,12,4,0,0,16,false),new Box(-.1F,12,-2,4,12,4,0,0,16,true),new Box(-4,-8,-4,8,8,8,.5F,32,0,false));
 /** Legacy ModelBox six textured quads with face UV topology. Dimensions remain model pixels. */
 public record Vertex(double x,double y,double z,float u,float v){}public record Quad(Vertex a,Vertex b,Vertex c,Vertex d){}
 public static List<Quad> mesh(Box b){double x=b.x-b.inflate,y=b.y-b.inflate,z=b.z-b.inflate,X=b.x+b.width+b.inflate,Y=b.y+b.height+b.inflate,Z=b.z+b.depth+b.inflate;if(b.mirror){double q=x;x=X;X=q;}double[][] p={{x,y,z},{X,y,z},{X,Y,z},{x,Y,z},{x,y,Z},{X,y,Z},{X,Y,Z},{x,Y,Z}};int u=b.u,v=b.v,w=(int)b.width,h=(int)b.height,d=(int)b.depth;return List.of(face(p,new int[]{5,1,2,6},u+d+w,v+d,u+d+w+d,v+d+h,b.mirror),face(p,new int[]{0,4,7,3},u,v+d,u+d,v+d+h,b.mirror),face(p,new int[]{5,4,0,1},u+d,v,u+d+w,v+d,b.mirror),face(p,new int[]{2,3,7,6},u+d+w,v+d,u+d+w+w,v,b.mirror),face(p,new int[]{1,0,3,2},u+d,v+d,u+d+w,v+d+h,b.mirror),face(p,new int[]{4,5,6,7},u+d+w+d,v+d,u+d+w+d+w,v+d+h,b.mirror));}
 private static Quad face(double[][] p,int[] a,float u,float v,float U,float V,boolean mirror){Vertex[] out={vertex(p[a[0]],U,v),vertex(p[a[1]],u,v),vertex(p[a[2]],u,V),vertex(p[a[3]],U,V)};return mirror?new Quad(out[3],out[2],out[1],out[0]):new Quad(out[0],out[1],out[2],out[3]);}private static Vertex vertex(double[] p,float u,float v){return new Vertex(p[0],p[1],p[2],u/64,v/32);}
}
