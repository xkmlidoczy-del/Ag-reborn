package cn.academy.port.client.city.o5;
import cn.academy.port.city.defense.O5Effect;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.*;

@net.neoforged.fml.common.EventBusSubscriber(modid="academy",value=net.neoforged.api.distmarker.Dist.CLIENT)
public final class CombatEffects {
 static final ResourceLocation WHITE=ResourceLocation.fromNamespaceAndPath("academy","textures/combat/white.png");
 public static final ResourceLocation ENERGY=ResourceLocation.fromNamespaceAndPath("academy","textures/combat/blackflash_impact.png");
 record Burst(O5Effect event,long seed){}
 record Ghost(Vec3 at,float yaw,int move,int kind,int variant,long tick,int delay){}
 record Shard(Vec3 at,Vec3 speed,BlockState state,float size,float spin,long tick){}
 private static Object worldIdentity;
 static final Deque<Burst> BURSTS=new ArrayDeque<>();static final Deque<Ghost> GHOSTS=new ArrayDeque<>();static final Deque<Shard> SHARDS=new ArrayDeque<>();
 public static int renderedBursts,renderedGhosts,renderedShards,renderedEnergy;
 public static void clear(){BURSTS.clear();GHOSTS.clear();SHARDS.clear();}
 public static void accept(O5Effect e){
  var mc=Minecraft.getInstance();if(mc.level==null)return;if(worldIdentity!=mc.level){clear();worldIdentity=mc.level;}
  if(e.kind()==0||e.kind()==2){var actor=mc.level.getEntity(e.owner());float yaw=actor==null?0:actor.getYRot();
   int count=e.from().distanceTo(e.to())>.4?4:2;
   for(int i=0;i<count;i++)GHOSTS.add(new Ghost(e.from().lerp(e.to(),(double)i/count),yaw,e.move(),e.kind(),e.variant(),e.tick(),i));
   while(GHOSTS.size()>28)GHOSTS.removeFirst();
  }
  if(e.kind()!=1&&e.kind()!=3)return;
  BURSTS.add(new Burst(e,((long)e.owner()<<32)^e.tick()^e.move()));while(BURSTS.size()>32)BURSTS.removeFirst();
  Random r=new Random(e.tick()*31+e.owner());int count=e.kind()==1?10:24;
  for(int i=0;i<count;i++){double a=r.nextDouble()*Math.PI*2,rad=.6+r.nextDouble()*2.3;
   BlockPos pos=BlockPos.containing(e.to().x+Math.cos(a)*rad,e.from().y-.2,e.to().z+Math.sin(a)*rad);
   BlockState state=mc.level.getBlockState(pos);for(int k=0;k<3&&state.isAir();k++){pos=pos.below();state=mc.level.getBlockState(pos);}
   if(state.isAir()||state.getCollisionShape(mc.level,pos).isEmpty())continue;
   SHARDS.add(new Shard(new Vec3(pos.getX()+.5,pos.getY()+1.0,pos.getZ()+.5),new Vec3(Math.cos(a)*(e.kind()==3?.11:.025),(e.kind()==3?.22:.13)+r.nextDouble()*.095,Math.sin(a)*(e.kind()==3?.11:.025)),state,(e.kind()==3?.38f:.16f)+r.nextFloat()*(e.kind()==3?.34f:.19f),r.nextFloat()*360,e.tick()));
  }while(SHARDS.size()>192)SHARDS.removeFirst();
 }
 static void ribbon(VertexConsumer out,PoseStack ps,Vec3 a,Vec3 b,double width,Vec3 camera,int red,int green,int blue,int alpha){
  Vec3 side=b.subtract(a).cross(camera.subtract(a.lerp(b,.5))).normalize().scale(width);quad(out,ps,a.subtract(side),a.add(side),b.add(side),b.subtract(side),red,green,blue,alpha);
 }
 static void quad(VertexConsumer out,PoseStack ps,Vec3 a,Vec3 b,Vec3 c,Vec3 d,int r,int g,int bl,int alpha){
  Vec3[] p={a,b,c,d};for(int i=0;i<4;i++)out.addVertex(ps.last(),(float)p[i].x,(float)p[i].y,(float)p[i].z).setColor(r,g,bl,alpha).setUv(i==1||i==2?1:0,i>=2?1:0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(),0,1,0);
 }
 public static void sprite(VertexConsumer out,PoseStack ps,Vec3 center,Vec3 right,Vec3 up,double width,double height,int cell,float alpha){
  Vec3 r=right.scale(width*.5),u=up.scale(height*.5);Vec3[] p={center.subtract(r).subtract(u),center.subtract(r).add(u),center.add(r).add(u),center.add(r).subtract(u)};
  float x=(cell%2)*.5f+.001f,y=(cell/2)*.5f+.001f,w=.498f;float[][] uv={{x,y+w},{x,y},{x+w,y},{x+w,y+w}};
  for(int i=0;i<4;i++)out.addVertex(ps.last(),(float)p[i].x,(float)p[i].y,(float)p[i].z).setColor(255,255,255,(int)(255*Mth.clamp(alpha,0,1))).setUv(uv[i][0],uv[i][1]).setOverlay(OverlayTexture.NO_OVERLAY).setLight(0xF000F0).setNormal(ps.last(),0,1,0);
 }
 @SubscribeEvent public static void render(RenderLevelStageEvent event){
  if(event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;var mc=Minecraft.getInstance();if(mc.level==null)return;if(worldIdentity!=mc.level){clear();worldIdentity=mc.level;}
  double now=mc.level.getGameTime()+event.getPartialTick().getGameTimeDeltaPartialTick(false);Vec3 camera=event.getCamera().getPosition();var ps=event.getPoseStack();var buffers=mc.renderBuffers().bufferSource();
  BURSTS.removeIf(b->now-b.event.tick()>18);GHOSTS.removeIf(g->now-g.tick>11);SHARDS.removeIf(s->now-s.tick>25);
  ps.pushPose();ps.translate(-camera.x,-camera.y,-camera.z);
  var type=RenderType.entityTranslucent(WHITE);var out=buffers.getBuffer(type);
  for(var burst:BURSTS){var e=burst.event;double age=now-e.tick();if(age<0)continue;double life=Math.min(1,age/15);int alpha=(int)(235*(1-life));Vec3 center=e.to();Random r=new Random(burst.seed);
   boolean confirmed=e.kind()==1&&e.variant()>0;float strength=confirmed?(float)Math.max(0,1-age/5):0;
   // Jagged black-core fractures, with a thin crimson sheath at contact only.
   if(confirmed&&age<5)for(int branch=0;branch<9;branch++){
    double theta=r.nextDouble()*Math.PI*2,reach=(.50+r.nextDouble()*.65)*(1+age*.09);
    Vec3 direction=new Vec3(Math.cos(theta),Math.sin(theta)*.8,r.nextDouble()*.6-.3).normalize();Vec3 side=direction.cross(camera.subtract(center)).normalize();Vec3 prev=center;
    for(int segment=1;segment<=6;segment++){
     double t=segment/6.,zig=(r.nextDouble()-.5)*.22*Math.sin(t*Math.PI);Vec3 next=center.add(direction.scale(reach*t)).add(side.scale(zig));double w=(.032*(1-t)+.007)*strength;
     ribbon(out,ps,prev,next,w*2.0,camera,125,0,14,(int)(36*strength));
     ribbon(out,ps,prev,next,w*1.23,camera,231,8,37,(int)(240*strength));
     ribbon(out,ps,prev,next,w*1.03,camera,1,1,3,(int)(250*strength));
     if(segment==3&&branch%2==0){Vec3 fork=next.add(direction.scale(.14)).add(side.scale(.20));ribbon(out,ps,next,fork,.009*strength,camera,186,4,29,(int)(210*strength));ribbon(out,ps,next,fork,.005*strength,camera,1,1,3,(int)(245*strength));}
     prev=next;
    }
   }
   if(confirmed&&age<.85){double size=.04*(1-age/.85);ribbon(out,ps,center.add(-size,0,0),center.add(size,0,0),.007,camera,255,180,187,(int)(210*(1-age/.85)));}
   if(confirmed&&age<7)for(int i=0;i<12;i++){double a=i*Math.PI*2/12+e.move(),rad=.20+age*.11;Vec3 p=center.add(Math.cos(a)*rad,Math.sin(a*1.7)*rad*.6,Math.sin(a)*rad*.65);ribbon(out,ps,p,p.add(Math.cos(a)*.055,.018,Math.sin(a)*.055),.003,camera,186,10,33,(int)(170*(1-age/7)));}
   // Ground pressure ripple is independent of the contact height.
   double radius=.25+age*(e.kind()==3?.43:.17);for(int i=0;i<48;i++){double a=i*Math.PI*2/48,b=(i+1)*Math.PI*2/48;
    Vec3 p=new Vec3(center.x+Math.cos(a)*radius,e.from().y+.045,center.z+Math.sin(a)*radius),q=new Vec3(center.x+Math.cos(b)*radius,e.from().y+.045,center.z+Math.sin(b)*radius);
    ribbon(out,ps,p,q,(e.kind()==3?.035:.008)*(1-life),camera,e.kind()==3?238:91,e.kind()==3?192:3,e.kind()==3?94:18,e.kind()==3?alpha:alpha/3);
   }renderedBursts++;
  }
  buffers.endBatch(type);
  // Crisp authored state masks, drawn separately to keep buffer builders valid.
  var energyType=RenderType.entityTranslucent(ENERGY);var energy=buffers.getBuffer(energyType);
  var cr=event.getCamera().getLeftVector();var cu=event.getCamera().getUpVector();Vec3 right=new Vec3(-cr.x(),-cr.y(),-cr.z()),up=new Vec3(cu.x(),cu.y(),cu.z());
  for(var burst:BURSTS){var e=burst.event;double age=now-e.tick();if(age<0||age>12)continue;Vec3 at=e.to();
   boolean confirmed=e.kind()==1&&e.variant()>0;
   if(confirmed&&age<2.2)sprite(energy,ps,at,right,up,.66+age*.10,.66+age*.10,1,(float)(.96*(1-age/2.2)));
   if(confirmed&&age>1&&age<7)sprite(energy,ps,at.add(0,.02,0),right,up,.34+age*.014,.42+age*.012,2,(float)(.38*Math.sin(Math.PI*(age-1)/6)));
   if(confirmed&&age<8)sprite(energy,ps,new Vec3(at.x,e.from().y+.048,at.z),new Vec3(1,0,0),new Vec3(0,0,1),1.5+age*.20,1.5+age*.20,3,(float)(.25*(1-age/8)));
   renderedEnergy++;
  }buffers.endBatch(energyType);
  for(var ghost:GHOSTS){double age=now-ghost.tick;float fade=(float)Math.max(0,1-age/10)*(.095f-.012f*ghost.delay);if(fade<=0||camera.distanceTo(ghost.at.add(0,1.8,0))<2.7)continue;
   ps.pushPose();ps.translate(ghost.at.x,ghost.at.y,ghost.at.z);ps.mulPose(Axis.YP.rotationDegrees(-ghost.yaw));ps.scale(1.875f,1.875f,-1.875f);
   ArmorGhost.draw(ps,buffers,ghost.move,Math.min(5,age),ghost.kind==2?ghost.variant:-1,age,fade);ps.popPose();renderedGhosts++;
  }
  buffers.endBatch(RenderType.entityTranslucent(ArmorGhost.TEX));
  buffers.endBatch(RenderType.entityTranslucent(CombatHands.TEX));
  for(var shard:SHARDS){double age=now-shard.tick;if(age<0||age>25)continue;double y=shard.at.y+shard.speed.y*age-.009*age*age;if(y<shard.at.y)y=shard.at.y;
   ps.pushPose();ps.translate(shard.at.x+shard.speed.x*age,y,shard.at.z+shard.speed.z*age);ps.mulPose(Axis.YP.rotationDegrees(shard.spin+(float)age*7));ps.mulPose(Axis.XP.rotationDegrees((float)age*5));float scale=shard.size*(float)Math.min(1,(25-age)/5);ps.scale(scale,scale,scale);ps.translate(-.5,0,-.5);
   mc.getBlockRenderer().renderSingleBlock(shard.state,ps,buffers,0xF000F0,OverlayTexture.NO_OVERLAY);ps.popPose();renderedShards++;
  }ps.popPose();
 }
}
