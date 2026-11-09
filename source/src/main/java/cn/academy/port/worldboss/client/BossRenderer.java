package cn.academy.port.worldboss.client;
import cn.academy.port.worldboss.Clips;
import cn.academy.port.worldboss.FxPacket;
import cn.academy.port.worldboss.WorldBossContent;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import java.io.*;
import org.joml.Matrix4f;
/** Draws the baked V9 clips (one matrix per bone per 20 fps frame) with clip-to-clip blending and ordinary entity yaw. */
public class BossRenderer extends EntityRenderer<WorldBossContent.Boss> {
 static final ResourceLocation TEX=ResourceLocation.fromNamespaceAndPath("academy","worldboss/boss.png");
 static final ResourceLocation EM=ResourceLocation.fromNamespaceAndPath("academy","worldboss/emissive.png");
 static Mesh[] meshes;static Matrix4f[][][] clips;
 record Mesh(int bone,boolean fx,float[] v){}
 public BossRenderer(EntityRendererProvider.Context c){super(c);shadowRadius=7;load();var tm=net.minecraft.client.Minecraft.getInstance().getTextureManager();tm.register(TEX,new BossTexture());tm.register(EM,new BossTexture("emissive.png"));}
 static void load(){if(meshes!=null)return;try(var in=new DataInputStream(new BufferedInputStream(BossRenderer.class.getResourceAsStream("/assets/academy/worldboss/model.bin"),1<<16))){
  int bones=in.readInt(),n=in.readInt();meshes=new Mesh[n];for(int i=0;i<n;i++){int bone=in.readInt();boolean fx=in.readInt()!=0;float[] v=new float[24*8];for(int j=0;j<v.length;j++)v[j]=in.readFloat();meshes[i]=new Mesh(bone,fx,v);}
  clips=new Matrix4f[in.readInt()][][];for(int a=0;a<clips.length;a++){clips[a]=new Matrix4f[in.readInt()][bones];for(int f=0;f<clips[a].length;f++)for(int b=0;b<bones;b++){float[] v=new float[12];for(int k=0;k<12;k++)v[k]=in.readFloat();clips[a][f][b]=new Matrix4f(v[0],v[3],v[6],0,v[1],v[4],v[7],0,v[2],v[5],v[8],0,v[9],v[10],v[11],1);}}
 }catch(Exception e){throw new RuntimeException(e);}}
 public ResourceLocation getTextureLocation(WorldBossContent.Boss e){return TEX;}
 static Matrix4f[] pose(int clip,float frame){Matrix4f[][] c=clips[Math.max(0,Math.min(clip,clips.length-1))];frame=Mth.clamp(frame,0,c.length-1);int f=Math.max(0,Math.min((int)frame,c.length-1)),n=Math.min(f+1,c.length-1);float t=frame-f;Matrix4f[] m=new Matrix4f[c[f].length];for(int b=0;b<m.length;b++)m[b]=new Matrix4f(c[f][b]).lerp(c[n][b],t);return m;}
 public boolean shouldRender(WorldBossContent.Boss e,net.minecraft.client.renderer.culling.Frustum f,double x,double y,double z){return f.isVisible(e.getBoundingBox().inflate(80));}
 public void render(WorldBossContent.Boss e,float yaw,float partial,PoseStack ps,MultiBufferSource buffers,int light){
  int clip=e.clip();float frame=e.frame(partial);Matrix4f[] mats=pose(clip,frame);
  int overlay=OverlayTexture.pack(OverlayTexture.u(0),OverlayTexture.v(e.hurtTime>0||e.deathTime>0));
  if(e.blendFrom>=0){float age=(float)(e.level().getGameTime()-e.blendStart)+partial;if(age<9){float w=1-Mth.clamp(age/9f,0,1);w=w*w*(3-2*w);Matrix4f[] old=pose(e.blendFrom,e.blendFromFrame+age);for(int b=0;b<mats.length;b++)mats[b]=new Matrix4f(mats[b]).lerp(old[b],w);}else e.blendFrom=-1;}
  light=LightTexture.pack(Math.max(LightTexture.block(light),13),LightTexture.sky(light));
  ps.pushPose();ps.translate(0,ClientHooks.rise(clip,frame),0);ps.mulPose(Axis.YP.rotationDegrees(180-yaw));
  for(int pass=0;pass<2;pass++){VertexConsumer out=buffers.getBuffer(pass==0?RenderType.entityCutoutNoCull(TEX):RenderType.eyes(EM));int lt=pass==0?light:LightTexture.FULL_BRIGHT;
   for(Mesh mesh:meshes){if(mesh.fx)continue;Matrix4f m=mats[mesh.bone];if(Math.abs(m.determinant3x3())<0.00000001)continue;ps.pushPose();ps.mulPose(m);var pose=ps.last();float[] v=mesh.v;
    for(int i=0;i<v.length;i+=8)out.addVertex(pose,v[i],v[i+1],v[i+2]).setColor(255,255,255,255).setUv(v[i+3],v[i+4]).setOverlay(overlay).setLight(lt).setNormal(pose,v[i+5],v[i+6],v[i+7]);
    ps.popPose();}}
  ps.popPose();
  super.render(e,yaw,partial,ps,buffers,light);
 }
}
