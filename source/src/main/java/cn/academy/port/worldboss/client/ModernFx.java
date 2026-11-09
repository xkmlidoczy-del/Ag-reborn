package cn.academy.port.worldboss.client;
import cn.academy.port.worldboss.Clips;
import cn.academy.port.worldboss.FxPacket;
import cn.academy.port.worldboss.WorldBossContent;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/**
 * Original FX layer written after studying Lodestone, Photon, Cataclysm and Iron's Spells'n Spellbooks (sources under reference/research/mods):
 * lifetime-driven particles with colour/scale curves and analytic drag, additive glow vs alpha smoke split, camera-facing ribbons for bolts,
 * beams and trails, ground-flat shockwave rings, lava-crack decals and summed distance-attenuated screen shake (see Shake).
 */
public final class ModernFx {
 static ResourceLocation rl(String p){return ResourceLocation.fromNamespaceAndPath("academy","worldboss/"+p);}
 static final ResourceLocation GLOW=rl("textures/fx/glow.png"),SMOKE=rl("textures/fx/smoke.png"),RING=rl("textures/fx/ring.png"),SPARK=rl("textures/fx/spark.png"),CRACK=rl("textures/fx/crack.png"),BOLT=rl("textures/fx/bolt.png"),SHOCK=rl("textures/fx/shock.png"),ROCK=rl("textures/fx/rock.png");
 static final List<Fx> fx=new ArrayList<>();static final Random R=new Random(11);
 static final int MAX=6000;
 /** Clear space immediately around the view; preserves distant FX without a full-screen fire sprite. */
 static float cameraFade(double distance,double radius){return (float)Mth.clamp((distance-.25)/(Math.max(.5,radius)*1.8),0,1);}
 static void clear(){fx.clear();}

 static final class Ctx{PoseStack ps;MultiBufferSource.BufferSource bs;Vec3 cam;Vector3f right=new Vector3f(),up=new Vector3f(),fwd=new Vector3f();float gt;}
 abstract static class Fx{final float t0,life;Fx(float life){t0=Shake.gt();this.life=life;}float age(float gt){return gt-t0;}float u(float gt){return (gt-t0)/life;}boolean dead(float gt){return gt-t0>life;}abstract void draw(Ctx c);}

 // ------------------------------------------------------------------ primitives
 static void quad(Ctx c,RenderType rt,Vec3 ctr,Vec3 ax,Vec3 ay,float r,float g,float b,float a){
  if(a<=.002f)return;VertexConsumer v=c.bs.getBuffer(rt);var p=c.ps.last();float cx=(float)(ctr.x-c.cam.x),cy=(float)(ctr.y-c.cam.y),cz=(float)(ctr.z-c.cam.z);
  float ax_=(float)ax.x,ay_=(float)ax.y,az_=(float)ax.z,bx=(float)ay.x,by=(float)ay.y,bz=(float)ay.z;
  vtx(v,p,cx-ax_-bx,cy-ay_-by,cz-az_-bz,0,1,r,g,b,a);vtx(v,p,cx+ax_-bx,cy+ay_-by,cz+az_-bz,1,1,r,g,b,a);vtx(v,p,cx+ax_+bx,cy+ay_+by,cz+az_+bz,1,0,r,g,b,a);vtx(v,p,cx-ax_+bx,cy-ay_+by,cz-az_+bz,0,0,r,g,b,a);}
 static void vtx(VertexConsumer v,PoseStack.Pose p,float x,float y,float z,float u,float w,float r,float g,float b,float a){v.addVertex(p,x,y,z).setColor(r,g,b,a).setUv(u,w).setOverlay(OverlayTexture.NO_OVERLAY).setLight(LightTexture.FULL_BRIGHT).setNormal(p,0,1,0);}
 static RenderType add(ResourceLocation t){return RenderType.entityTranslucentEmissive(t);}
 static RenderType alpha(ResourceLocation t){return RenderType.entityTranslucentEmissive(t);}
 static void billboard(Ctx c,RenderType rt,Vec3 p,float size,float r,float g,float b,float a,float spin){
  double distance=c.cam.distanceTo(p);
  a*=cameraFade(distance,Math.max(.35f,size*.6f));if(a<=.002f)return;
  size=Math.min(size,(float)distance*.42f);
  float cs=(float)Math.cos(spin)*size,sn=(float)Math.sin(spin)*size;Vec3 ax=new Vec3(c.right.x*cs+c.up.x*sn,c.right.y*cs+c.up.y*sn,c.right.z*cs+c.up.z*sn),ay=new Vec3(-c.right.x*sn+c.up.x*cs,-c.right.y*sn+c.up.y*cs,-c.right.z*sn+c.up.z*cs);quad(c,rt,p,ax,ay,r,g,b,a);}
 static void flat(Ctx c,RenderType rt,Vec3 p,float radius,float rot,float r,float g,float b,float a){float cs=(float)Math.cos(rot)*radius,sn=(float)Math.sin(rot)*radius;quad(c,rt,p,new Vec3(cs,0,sn),new Vec3(-sn,0,cs),r,g,b,a);}
 static void ribbon(Ctx c,RenderType rt,Vec3 a,Vec3 b,float w,float r,float g,float bl,float al){Vec3 d=b.subtract(a);
  double length=d.lengthSqr();if(length<1e-12)return;
  double fraction=Mth.clamp(c.cam.subtract(a).dot(d)/length,0,1);
  double distance=c.cam.distanceTo(a.add(d.scale(fraction)));
  al*=cameraFade(distance,Math.max(.3f,w*.4f));if(al<=.002f)return;
  w=Math.min(w,(float)distance*.7f);Vec3 mid=a.add(b).scale(.5);Vec3 side=d.cross(c.cam.subtract(mid));if(side.lengthSqr()<1e-9)return;side=side.normalize().scale(w/2);quad(c,rt,mid,d.scale(.5),side,r,g,bl,al);}
 /** A continuous core shares one longitudinal UV interval across an entire authored path.
  * SPARK is empty at U=0/1 and outside its narrow central V strip: restarting full UV
  * per sub-meter section turns a hot core into separated dots. Geometry/depth stay native. */
 static void continuousCore(Ctx c,Vec3 a,Vec3 b,float width,float alpha,float u0,float u1){
  Vec3 delta=b.subtract(a);double length=delta.lengthSqr();if(length<1e-12)return;
  double fraction=Mth.clamp(c.cam.subtract(a).dot(delta)/length,0,1);
  double distance=c.cam.distanceTo(a.add(delta.scale(fraction)));
  alpha*=cameraFade(distance,Math.max(.3f,width*.4f));if(alpha<=.002f)return;
  width=Math.min(width,(float)distance*.7f);Vec3 middle=a.add(b).scale(.5);
  Vec3 side=delta.cross(c.cam.subtract(middle));if(side.lengthSqr()<1e-9)return;
  side=side.normalize().scale(width*.5);Vec3 ax=delta.scale(.5);
  Vec3 p0=middle.subtract(ax).subtract(side).subtract(c.cam),p1=middle.add(ax).subtract(side).subtract(c.cam);
  Vec3 p2=middle.add(ax).add(side).subtract(c.cam),p3=middle.subtract(ax).add(side).subtract(c.cam);
  var vertex=c.bs.getBuffer(add(SPARK));var pose=c.ps.last();
  vtx(vertex,pose,(float)p0.x,(float)p0.y,(float)p0.z,u0,.625f,1,.97f,.78f,alpha);
  vtx(vertex,pose,(float)p1.x,(float)p1.y,(float)p1.z,u1,.625f,1,.97f,.78f,alpha);
  vtx(vertex,pose,(float)p2.x,(float)p2.y,(float)p2.z,u1,.375f,1,.97f,.78f,alpha);
  vtx(vertex,pose,(float)p3.x,(float)p3.y,(float)p3.z,u0,.375f,1,.97f,.78f,alpha);
 }
 /** Disc perpendicular to an axis. */
 static void disc(Ctx c,RenderType rt,Vec3 p,Vec3 axis,float radius,float r,float g,float b,float a){Vec3 up=Math.abs(axis.y)>.95?new Vec3(1,0,0):new Vec3(0,1,0);Vec3 u=axis.cross(up).normalize().scale(radius),v=axis.cross(u).normalize().scale(radius);quad(c,rt,p,u,v,r,g,b,a);}

 // ------------------------------------------------------------------ particles
 static final class Puff extends Fx{
  final Vec3 p0,v0;final float drag,grav,s0,s1,spin,heat;final float[] c0,c1,c2;final boolean smoke;
  Puff(Vec3 p,Vec3 v,float life,float drag,float grav,float s0,float s1,float[] c0,float[] c1,float[] c2,float heat,boolean smoke){super(life*20f);p0=p;v0=v;this.drag=drag;this.grav=grav;this.s0=s0;this.s1=s1;this.c0=c0;this.c1=c1;this.c2=c2;this.heat=heat;this.smoke=smoke;spin=R.nextFloat()*6.28f;}
  void draw(Ctx c){float t=age(c.gt)/20f,u=Mth.clamp(u(c.gt),0,1);double k=drag>1e-3?(1-Math.exp(-drag*t))/drag:t;double gk=drag>1e-3?(t/drag-(1-Math.exp(-drag*t))/(drag*drag)):t*t/2;
   Vec3 p=p0.add(v0.scale(k)).add(0,-grav*gk,0);float s=Mth.lerp(u,s0,s1);float[] col=u<.5f?lerp(c0,c1,u*2):lerp(c1,c2,(u-.5f)*2);float a=col[3]*(u<.08f?u/.08f:1)*(float)Math.pow(1-u,1.2);
   if(smoke)billboard(c,alpha(SMOKE),p,s,col[0],col[1],col[2],a,spin+t*.6f);else{billboard(c,add(GLOW),p,s,col[0],col[1],col[2],a,spin);if(heat>0)billboard(c,add(GLOW),p,s*.45f,1,.9f,.7f,a*heat,spin);}}
 }
 static float[] lerp(float[] a,float[] b,float t){return new float[]{Mth.lerp(t,a[0],b[0]),Mth.lerp(t,a[1],b[1]),Mth.lerp(t,a[2],b[2]),Mth.lerp(t,a[3],b[3])};}
 static final float[] WHITE={1,.95f,.75f,1},GOLD={1,.72f,.2f,.95f},ORANGE={1,.4f,.08f,.8f},RED={.7f,.12f,.04f,.55f},ASH={.16f,.14f,.14f,.55f},GREY={.32f,.3f,.3f,.45f},DUSTC={.42f,.35f,.28f,.55f},CLEAR={.1f,.08f,.08f,0};
 static void emit(Fx f){if(fx.size()<MAX)fx.add(f);}
 static Vec3 rnd(double s){return new Vec3((R.nextDouble()-.5)*2*s,(R.nextDouble()-.5)*2*s,(R.nextDouble()-.5)*2*s);}
 static void ember(Vec3 p,Vec3 v,float life,float size){emit(new Puff(p,v,life,.8f,18,size,size*.25f,WHITE,GOLD,RED,.8f,false));}
 static void fire(Vec3 p,Vec3 v,float life,float size){emit(new Puff(p,v,life,1.4f,-6,size*.4f,size,WHITE,ORANGE,ASH,.5f,false));}
 static void smokePuff(Vec3 p,Vec3 v,float life,float size,float[] col){emit(new Puff(p,v,life,1.1f,-2.5f,size*.5f,size,col,GREY,CLEAR,0,true));}

 // ------------------------------------------------------------------ composite effects
 static final class Ring extends Fx{final Vec3 p;final float R_,power;final int variant;final float rot=R.nextFloat()*6.28f;boolean embers;
  Ring(Vec3 p,float r,float power,float life,int variant){super(life);this.p=p;R_=r;this.power=power;this.variant=variant;}
  void draw(Ctx c){float u=Mth.clamp(u(c.gt),0,1);float e=1-(float)Math.pow(1-u,2.4);float rad=R_*e+1;float a=(1-u)*(1-u);float[] col=variant==1?new float[]{1,.3f,.12f}:variant==2?new float[]{.7f,.86f,1}:new float[]{1,.66f,.25f};
   flat(c,add(SHOCK),p,rad*1.04f,rot,col[0],col[1],col[2],a*.9f);flat(c,add(RING),p.add(0,.06,0),rad,rot*.5f,1,.9f,.7f,a);flat(c,alpha(SMOKE),p.add(0,.1,0),rad*.96f,-rot,.35f,.3f,.27f,a*.55f*(u<.8f?1:(1-u)*5));
   if(u<.8f&&R.nextFloat()<.9f)for(int i=0;i<3;i++){double ang=R.nextDouble()*6.283;Vec3 q=p.add(Math.cos(ang)*rad,.4,Math.sin(ang)*rad);if(i==0)smokePuff(q,new Vec3(Math.cos(ang)*5,3+R.nextDouble()*3,Math.sin(ang)*5),.9f,3.2f*power,DUSTC);else ember(q,new Vec3(Math.cos(ang)*6,6+R.nextDouble()*8,Math.sin(ang)*6),.9f+R.nextFloat(),.5f);}}}
 static final class Crack extends Fx{final Vec3 p;final float rad,rot=R.nextFloat()*6.28f;
  Crack(Vec3 p,float rad,float life){super(life);this.p=p;this.rad=rad;}
  void draw(Ctx c){float a=age(c.gt),u=a/life;float grow=1-(float)Math.pow(1-Mth.clamp(a/8f,0,1),2.5);float fade=u>.7f?(1-u)/.3f:1;float cool=Mth.clamp(u*1.6f,0,1);
   flat(c,add(CRACK),p.add(0,.07,0),rad*grow*1.9f,rot,1,Mth.lerp(cool,.9f,.28f),Mth.lerp(cool,.55f,.04f),fade*Mth.lerp(cool,1,.65f));
   if(u<.9f&&R.nextFloat()<.35f){double ang=R.nextDouble()*6.283,rr=R.nextDouble()*rad*grow*1.2;Vec3 q=p.add(Math.cos(ang)*rr,.2,Math.sin(ang)*rr);ember(q,new Vec3(0,3+R.nextDouble()*5,0).add(rnd(1.2)),.8f+R.nextFloat()*.8f,.35f);}}}
 static final class Bolt extends Fx{final Vec3[] pts;final List<Vec3[]> branches=new ArrayList<>();
  Bolt(Vec3 ground,Vec3 top,boolean flash){super(8);int n=18;pts=new Vec3[n+1];Vec3 d=top.subtract(ground);for(int i=0;i<=n;i++){double f=i/(double)n;double amp=Math.min(1,f*3)*7*(1-f*.4);pts[i]=ground.add(d.scale(f)).add(new Vec3((R.nextDouble()-.5)*amp,0,(R.nextDouble()-.5)*amp));}
   for(int b=0;b<3;b++){int s=3+R.nextInt(n-6);Vec3[] br=new Vec3[7];br[0]=pts[s];Vec3 dir=new Vec3(R.nextDouble()-.5,.55,R.nextDouble()-.5).normalize();for(int k=1;k<7;k++)br[k]=br[k-1].add(dir.scale(8+R.nextDouble()*6)).add(rnd(4));branches.add(br);}
   if(flash)EnvClient.pulse(1);
}
  void draw(Ctx c){float u=u(c.gt);float a=u<.1f?1:(.25f+.75f*R.nextFloat())*(1-u);for(int i=0;i<pts.length-1;i++){float w=1.8f*(1-i/(float)pts.length*.4f);ribbon(c,add(BOLT),pts[i],pts[i+1],w*3.2f,.5f,.7f,1,a*.45f);ribbon(c,add(BOLT),pts[i],pts[i+1],w,1,1,1,a);}
   for(Vec3[] br:branches)for(int k=0;k<br.length-1;k++){ribbon(c,add(BOLT),br[k],br[k+1],1.1f*(1-k/7f),.8f,.9f,1,a*.8f);}
   billboard(c,add(GLOW),pts[0].add(0,1,0),9*(1-u),.8f,.9f,1,a*.9f,0);if(u<.2f)billboard(c,add(GLOW),pts[pts.length/2],26,.55f,.7f,1,a*.18f,0);}}
 static final class Meteor extends Fx{final Vec3 a,b;final LinkedList<Vec3> trail=new LinkedList<>();
  Meteor(Vec3 a,Vec3 b,float life){super(life);this.a=a;this.b=b;}
  Vec3 at(float u){Vec3 p=a.lerp(b,u);return p.add(0,Math.sin(Math.PI*Math.min(1,u))*28*(1-u*.2)-0,0);}
  void draw(Ctx c){float u=Mth.clamp(u(c.gt),0,1);Vec3 p=at(u);trail.addFirst(p);while(trail.size()>22)trail.removeLast();
   Vec3 prev=null;int i=0;for(Vec3 q:trail){if(prev!=null){float f=1-i/(float)trail.size();ribbon(c,add(SPARK),prev,q,4.2f*f,1,.55f,.15f,f*.9f);ribbon(c,add(SPARK),prev,q,1.8f*f,1,.95f,.7f,f);}prev=q;i++;}
   billboard(c,add(GLOW),p,6.5f,1,.5f,.12f,.95f,0);billboard(c,add(GLOW),p,3f,1,.95f,.75f,1,0);if(R.nextFloat()<.7f)ember(p,rnd(5),.9f,.6f);if(R.nextFloat()<.35f)smokePuff(p,rnd(2),1.5f,3f,GREY);}}
 static final class Slash extends Fx{final Vec3 p;final float side,rot;final int variant;
  Slash(Vec3 p,float side,int variant){super(11);this.p=p;this.side=side;this.variant=variant;rot=R.nextFloat()*.5f;}
  void draw(Ctx c){float u=Mth.clamp(u(c.gt),0,1);float head=Mth.lerp(u,-1.2f,1.2f);Vec3 prev=null;for(int i=0;i<16;i++){float ang=head-i*.09f;float f=1-i/16f;Vec3 q=p.add(new Vec3(c.right.x,c.right.y,c.right.z).scale(Math.sin(ang)*9*side)).add(new Vec3(c.up.x,c.up.y,c.up.z).scale(Math.cos(ang)*9-6+rot*3));
    if(prev!=null){ribbon(c,add(SPARK),prev,q,(variant==1?5:3.4f)*f,1,.78f-.2f*variant,.28f,f*(1-u*.7f));continuousCore(c,prev,q,(variant==1?5:3.4f)*f*.55f,f*(1-u*.7f),(i-1)/15f,i/15f);}prev=q;}}}
 static final class RoarFx extends Fx{final Vec3 p,dir;
  RoarFx(Vec3 p,Vec3 dir){super(34);this.p=p;this.dir=dir;}
  void draw(Ctx c){float u=Mth.clamp(u(c.gt),0,1);for(int i=0;i<5;i++){float q=u*1.6f-i*.14f;if(q<=0||q>1)continue;Vec3 pos=p.add(dir.scale(q*70));float rad=3+q*30;float a=(1-q)*.9f;disc(c,add(SHOCK),pos,dir,rad,1,.85f,.6f,a);disc(c,add(RING),pos,dir,rad*.9f,.8f,.9f,1,a*.5f);}} }
 static final class Nova extends Fx{final Vec3 p;final float rad;
  Nova(Vec3 p,float rad){super(46);this.p=p;this.rad=rad;}
  void draw(Ctx c){float u=Mth.clamp(u(c.gt),0,1);float e=1-(float)Math.pow(1-u,2.2);float r=rad*e*.8f;float a=(1-u)*(1-u);billboard(c,add(GLOW),p,r*1.1f,1,.55f,.18f,a*.55f,0);billboard(c,add(GLOW),p,r*.45f,1,.95f,.8f,a*.8f,0);
   disc(c,add(SHOCK),p,new Vec3(0,1,0),r*1.25f,1,.7f,.3f,a);disc(c,add(RING),p,new Vec3(1,0,0),r,1,.6f,.2f,a*.7f);disc(c,add(RING),p,new Vec3(0,0,1),r,1,.6f,.2f,a*.7f);}}
 static final class Orb extends Fx{final WorldBossContent.Boss b;final int idx;final int clip;
  Orb(WorldBossContent.Boss b,int idx,float life){super(life);this.b=b;this.idx=idx;clip=b.clip();}
  void draw(Ctx c){if(b.isRemoved()||!b.isAlive()||b.clip()!=clip)return;float pt=c.gt-(float)Math.floor(c.gt);Vec3 p=ClientHooks.anchor(b,idx,pt);float u=Mth.clamp(u(c.gt),0,1);float s=(float)Math.sin(Math.PI*Math.min(1,u*1.05))*(2.2f+u*3.2f);
   billboard(c,add(GLOW),p,s*1.8f,1,.5f,.12f,.55f,0);billboard(c,add(GLOW),p,s*.8f,1,.92f,.65f,.95f,age(c.gt)*.1f);
   if(R.nextFloat()<.8f){Vec3 q=p.add(rnd(9*(1-u)+2));emit(new Puff(q,p.subtract(q).scale(2.2),.45f,0,0,.7f,.1f,WHITE,GOLD,ORANGE,.8f,false));}}}
 static final class Beam extends Fx{final WorldBossContent.Boss b;final int clip;
  Beam(WorldBossContent.Boss b,float life){super(life);this.b=b;clip=b.clip();}
  void draw(Ctx c){if(b.isRemoved()||!b.isAlive()||b.clip()!=clip)return;float pt=c.gt-(float)Math.floor(c.gt);Vec3 m=ClientHooks.anchor(b,ClientHooks.MOUTH,pt);Vec3 dir=b.attackAim().subtract(m).normalize();float u=Mth.clamp(u(c.gt),0,1);float env=Mth.clamp(Math.min(u/.06f,(1-u)/.07f),0,1);if(env<=0)return;
   var mc=Minecraft.getInstance();Vec3 end=m.add(dir.scale(90));HitResult h=mc.level.clip(new ClipContext(m,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,b));if(h.getType()!=HitResult.Type.MISS)end=h.getLocation();
   float w=(2.4f+.6f*(float)Math.sin(c.gt*1.7f))*env;int seg=24;Vec3 prev=m;for(int i=1;i<=seg;i++){Vec3 q=m.lerp(end,i/(float)seg);float wob=1+.12f*(float)Math.sin(c.gt*2+i*.9f);ribbon(c,add(SPARK),prev,q,w*5*wob,1,.4f,.08f,.45f*env);ribbon(c,add(SPARK),prev,q,w*2.2f*wob,1,.78f,.3f,.85f*env);ribbon(c,add(SPARK),prev,q,w*.9f,1,1,.95f,env);continuousCore(c,prev,q,w*.9f,env,(i-1)/(float)seg,i/(float)seg);prev=q;}
   billboard(c,add(GLOW),m,w*5,1,.6f,.2f,.8f*env,0);billboard(c,add(GLOW),end,w*9,1,.55f,.15f,.85f*env,0);billboard(c,add(GLOW),end.add(0,1,0),w*4,1,.95f,.8f,env,0);
   for(int i=0;i<3;i++){float q=((c.gt*.045f+i/3f)%1);Vec3 pos=m.lerp(end,q);disc(c,add(RING),pos,dir,w*(2.2f+q*2),1,.7f,.3f,.6f*env*(1-q));}
   if(R.nextFloat()<.9f){ember(end.add(0,.5,0),new Vec3((R.nextDouble()-.5)*18,10+R.nextDouble()*16,(R.nextDouble()-.5)*18),1f,.6f);smokePuff(end,new Vec3(rnd(3).x,5,rnd(3).z),1.6f,6,DUSTC);}}}


 /** Ground debris: tumbling lava-veined rock cubes thrown by heavy impacts. */
 static final class Rock extends Fx{final Vec3 p0,v0;final float size,gy;final Vec3 axis;final float spin;
  Rock(Vec3 p,Vec3 v,float size,float gy,float life){super(life);p0=p;v0=v;this.size=size;this.gy=gy;axis=new Vec3(R.nextDouble()-.5,R.nextDouble()-.5,R.nextDouble()-.5).normalize();spin=(float)(2+R.nextDouble()*6);}
  void draw(Ctx c){float t=age(c.gt)/20f;Vec3 p=p0.add(v0.scale(t)).add(0,-14*t*t,0);if(p.y<gy){p=new Vec3(p.x,gy,p.z);}float u=Mth.clamp(u(c.gt),0,1);float a=u>.8f?(1-u)*5:1;
   var q=new Quaternionf().rotationAxis(spin*t,(float)axis.x,(float)axis.y,(float)axis.z);Vector3f[] ax={new Vector3f(1,0,0),new Vector3f(0,1,0),new Vector3f(0,0,1)};for(Vector3f v:ax){q.transform(v);v.mul(size);}
   int[][] faces={{0,1,2},{0,2,1},{1,0,2},{1,2,0},{2,0,1},{2,1,0}};for(int i=0;i<6;i++){int k=faces[i][0];float sg=(i%2==0)?1:-1;Vec3 n=new Vec3(ax[k].x,ax[k].y,ax[k].z);Vec3 a1=new Vec3(ax[faces[i][1]].x,ax[faces[i][1]].y,ax[faces[i][1]].z),a2=new Vec3(ax[faces[i][2]].x,ax[faces[i][2]].y,ax[faces[i][2]].z);
    float sh=.55f+.45f*(float)Math.max(0,n.scale(sg).normalize().y*.7+.3);quad(c,alpha(ROCK),p.add(n.scale(sg)),a1,a2,sh,sh,sh,a);}
   if(R.nextFloat()<.25f&&p.y>gy+.2)ember(p,rnd(2),.5f,.35f);}}
 /** Lava geyser: a pulsing glowing column with billowing fire at its crown. */
 static final class Geyser extends Fx{final Vec3 p;final float h;
  Geyser(Vec3 p,float h,float life){super(life);this.p=p;this.h=h;}
  void draw(Ctx c){float u=Mth.clamp(u(c.gt),0,1);float up=(float)Math.sin(Math.PI*Math.min(1,u*1.15));float H=h*up;if(H<.3f)return;Vec3 top=p.add(0,H,0);float w=2.6f*(1-u*.5f);
   ribbon(c,add(SPARK),p,top,w*3.2f,1,.35f,.08f,.5f*up);ribbon(c,add(SPARK),p,top,w*1.6f,1,.7f,.25f,.9f*up);ribbon(c,add(SPARK),p,top,w*.7f,1,.98f,.8f,up);billboard(c,add(GLOW),top,w*5,1,.55f,.15f,.8f*up,0);billboard(c,add(GLOW),p.add(0,1,0),w*4,1,.6f,.2f,.7f*up,0);
   if(R.nextFloat()<.8f)fire(top.add(rnd(1)),new Vec3((R.nextDouble()-.5)*8,6+R.nextDouble()*8,(R.nextDouble()-.5)*8),1f,5f);if(R.nextFloat()<.5f)ember(top,new Vec3((R.nextDouble()-.5)*16,4+R.nextDouble()*14,(R.nextDouble()-.5)*16),1.6f,.5f);}}
 /** Hemispherical blast shell: tilted expanding discs with a hot inner glow. */
 static final class Dome extends Fx{final Vec3 p;final float R_;
  Dome(Vec3 p,float r,float life){super(life);this.p=p;R_=r;}
  void draw(Ctx c){float u=Mth.clamp(u(c.gt),0,1);float e=1-(float)Math.pow(1-u,2.0);float r=R_*e;float a=(1-u)*(1-u);
   for(int i=0;i<6;i++){double th=i*Math.PI/6;Vec3 ax=new Vec3(Math.cos(th),.0,Math.sin(th));Vec3 n=new Vec3(-Math.sin(th),.0,Math.cos(th));disc(c,add(RING),p.add(0,r*.05,0),new Vec3(-Math.sin(th)*.7,.7,Math.cos(th)*.7).normalize(),r,1,.62f,.24f,a*.5f);}
   billboard(c,add(GLOW),p.add(0,r*.25,0),r*1.3f,1,.5f,.15f,a*.5f,0);billboard(c,add(GLOW),p.add(0,r*.15,0),r*.6f,1,.9f,.7f,a*.7f,0);}}
 // ------------------------------------------------------------------ event entry points
 public static void bossEvent(WorldBossContent.Boss b,String type,int clip,int tick){
  if(!b.isAlive())return;
  switch(type){
   case "charge_orb"->emit(new Orb(b,ClientHooks.MOUTH,42));
   case "crest_charge"->emit(new Orb(b,ClientHooks.CREST,52));
   case "beam"->emit(new Beam(b,50));
   case "step_left","step_right","stomp","quake","eruption"->{Vec3 f=ClientHooks.anchor(b,clip,tick,type.equals("step_right")?ClientHooks.RFOOT:ClientHooks.LFOOT);for(int i=0;i<(type.startsWith("step")?6:18);i++){double a=R.nextDouble()*6.28;smokePuff(f.add(Math.cos(a)*3,.5,Math.sin(a)*3),new Vec3(Math.cos(a)*(4+R.nextDouble()*6),2+R.nextDouble()*4,Math.sin(a)*(4+R.nextDouble()*6)),1.3f,type.startsWith("step")?3f:6f,DUSTC);}}
   default->{}
  }}
 /** Per-tick emitters bound to the boss pose. */
 public static void continuous(WorldBossContent.Boss b,int clip,int age){
  if(!b.isAlive())return;
  var mc=Minecraft.getInstance();if(mc.player==null||b.distanceTo(mc.player)>520)return;
  float pt=0;
  if(R.nextFloat()<.7f){Vec3 c=ClientHooks.anchor(b,clip,Math.min(age,Clips.get(clip).frames()-1),ClientHooks.CREST);ember(c.add(rnd(7)).add(0,-5,0),new Vec3((R.nextDouble()-.5)*4,5+R.nextDouble()*7,(R.nextDouble()-.5)*4),2f+R.nextFloat()*1.6f,.45f);}
  if(clip==Clips.SPAWN&&age<150){for(int i=0;i<4;i++){double a=R.nextDouble()*6.28,r=R.nextDouble()*13;Vec3 p=b.position().add(Math.cos(a)*r,-14+R.nextDouble()*14,Math.sin(a)*r);fire(p,new Vec3(0,9+R.nextDouble()*10,0).add(rnd(2)),1.4f+R.nextFloat(),4.5f);}
    if(R.nextFloat()<.7f)smokePuff(b.position().add(rnd(11).multiply(1,.2,1)),new Vec3((R.nextDouble()-.5)*3,12+R.nextDouble()*8,(R.nextDouble()-.5)*3),3.4f,12,ASH);
    if(R.nextFloat()<.5f)ember(b.position().add(rnd(12).multiply(1,.1,1)).add(0,-8,0),new Vec3((R.nextDouble()-.5)*8,14+R.nextDouble()*22,(R.nextDouble()-.5)*8),2.8f,.6f);}
  if(clip==Clips.BREATH&&age>=46&&age<102){Vec3 m=ClientHooks.anchor(b,clip,age,ClientHooks.MOUTH);Vec3 d=b.attackAim().subtract(m).normalize();
   Vec3 end=m.add(d.scale(46));var hit=mc.level.clip(new ClipContext(m,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,b));
   if(hit.getType()!=HitResult.Type.MISS)end=hit.getLocation();double length=m.distanceTo(end);
   for(int i=0;i<16;i++){Vec3 v=d.scale(length*(.8+R.nextDouble()*.6)).add(rnd(5));fire(m.add(rnd(.8)),v,.8f+R.nextFloat()*.7f,3.4f);}
   for(int i=0;i<3;i++)ember(m,d.scale(Math.min(length,15+R.nextDouble()*25)).add(rnd(9)),1.4f,.5f);
   if(length>1&&R.nextFloat()<.6f)smokePuff(m.add(d.scale(Math.min(length,14))),d.scale(Math.min(length*.5,12)).add(rnd(4)),1.9f,6,ASH);}
  if(clip==Clips.CHARGE&&age>=42&&age<86){for(Vec3 f:new Vec3[]{ClientHooks.anchor(b,clip,age,ClientHooks.LFOOT),ClientHooks.anchor(b,clip,age,ClientHooks.RFOOT)}){smokePuff(f.add(rnd(2)),rnd(4).add(0,3,0),1.5f,5,DUSTC);for(int i=0;i<3;i++)ember(f,new Vec3((R.nextDouble()-.5)*9,3+R.nextDouble()*8,(R.nextDouble()-.5)*9).add(b.forward().scale(-8)),.7f,.4f);}
   Shake.add(b.getX(),b.getY(),b.getZ(),.09f,3,160);}
  if(clip==Clips.LANCE&&age>=57&&age<107){Shake.add(b.getX(),b.getY()+10,b.getZ(),.07f,2,150);}
 }
 public static void packet(FxPacket p){
  if(Minecraft.getInstance().level==null)return;
  Vec3 pos=new Vec3(p.x(),p.y(),p.z());
  switch(p.kind()){
   case "shake"->Shake.add(p.x(),p.y(),p.z(),p.a(),p.i(),p.b());
   case "ring"->{emit(new Ring(pos,p.a(),p.b(),p.i(),p.j()));if(p.a()>=14){emit(new Dome(pos,p.a()*.7f,(int)(p.i()*.8f)));int n=(int)Math.min(46,p.a()*1.4);var mc=Minecraft.getInstance();for(int i=0;i<n;i++){double a=R.nextDouble()*6.283,r=R.nextDouble()*p.a()*.5;Vec3 s0=pos.add(Math.cos(a)*r,.6,Math.sin(a)*r);double sp=8+R.nextDouble()*22;emit(new Rock(s0,new Vec3(Math.cos(a)*sp*.5,14+R.nextDouble()*22,Math.sin(a)*sp*.5),.4f+(float)R.nextDouble()*1.3f,(float)pos.y,70));}
     for(int i=0;i<5;i++){double a=i*1.2566+R.nextDouble();smokePuff(pos.add(Math.cos(a)*p.a()*.4,1,Math.sin(a)*p.a()*.4),new Vec3(0,9+R.nextDouble()*7,0),3.6f,p.a()*.45f,ASH);}}}
   case "burst"->{int n=(int)(34*p.a());for(int i=0;i<n;i++){double a=R.nextDouble()*6.28,e=.35+R.nextDouble()*1.1;double sp=(6+R.nextDouble()*20)*p.a();ember(pos.add(rnd(.8)),new Vec3(Math.cos(a)*sp*Math.cos(e),sp*Math.sin(e)*1.4,Math.sin(a)*sp*Math.cos(e)),.9f+R.nextFloat()*1.4f,.5f+R.nextFloat()*.4f);}
     for(int i=0;i<(int)(8*p.a());i++)fire(pos.add(rnd(1.2)),rnd(8).add(0,6,0),.8f+R.nextFloat(),4.5f*p.a());for(int i=0;i<(int)(5*p.a());i++)smokePuff(pos.add(rnd(1.5)),rnd(5).add(0,5,0),2.2f,7*p.a(),ASH);
     emit(new Puff(pos.add(0,.5,0),Vec3.ZERO,.3f,0,0,6*p.a(),12*p.a(),WHITE,GOLD,CLEAR,1,false));}
   case "crack"->{emit(new Crack(pos,p.a(),p.i()));if(p.a()>=9){int g=(int)(p.a()/4);for(int i=0;i<g;i++){double a=R.nextDouble()*6.283,r=p.a()*(.3+R.nextDouble()*.7);emit(new Geyser(pos.add(Math.cos(a)*r,.1,Math.sin(a)*r),8+(float)R.nextDouble()*p.a()*.9f,26+R.nextInt(18)));}}}
   case "bolt"->{emit(new Bolt(pos,new Vec3(p.a(),p.b(),p.c()),p.i()==1));if(p.i()==1)EnvClient.pulse(.9f);else EnvClient.pulse(.35f);}
   case "meteor"->emit(new Meteor(pos,new Vec3(p.a(),p.b(),p.c()),p.i()));
   case "slash"->emit(new Slash(pos,p.a(),p.i()));
   case "roar"->emit(new RoarFx(pos,new Vec3(p.a(),0,p.b()).normalize()));
   case "nova"->emit(new Nova(pos,p.a()));
   case "spike"->{for(int i=0;i<10;i++)ember(pos.add(rnd(.8)),new Vec3((R.nextDouble()-.5)*8,8+R.nextDouble()*14,(R.nextDouble()-.5)*8),1.2f,.5f);smokePuff(pos,new Vec3(0,6,0),2f,6,ASH);}
   case "env"->EnvClient.pulse(.6f);
   default->{}
  }}

 public static void render(RenderLevelStageEvent e){
  if(e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();if(mc.level==null||fx.isEmpty())return;
  Ctx c=new Ctx();c.ps=e.getPoseStack();c.bs=mc.renderBuffers().bufferSource();c.cam=e.getCamera().getPosition();c.gt=Shake.gt();Quaternionf q=e.getCamera().rotation();q.transform(1,0,0,c.right);q.transform(0,1,0,c.up);q.transform(0,0,-1,c.fwd);
  fx.removeIf(f->f.dead(c.gt));for(int i=0;i<fx.size();i++)fx.get(i).draw(c);c.bs.endBatch();}
}
