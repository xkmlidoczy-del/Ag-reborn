package cn.academy.biohum.client;
import cn.academy.biohum.BioHum;
import net.minecraft.world.entity.Entity;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.*;
public final class SpriteRenderer<T extends Entity> extends EntityRenderer<T> {
 final ResourceLocation texture;final float size;
 public SpriteRenderer(EntityRendererProvider.Context c,String name,float size){super(c);this.size=size;texture=ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/entity/"+name+".png");}
 @Override public ResourceLocation getTextureLocation(T e){return texture;}
 @Override public void render(T e,float yaw,float partial,PoseStack pose,MultiBufferSource buffers,int light){
  if(e instanceof cn.academy.biohum.AbilityBolt bolt&&cn.academy.biohum.Skills.get(bolt.skill())!=null){
   SkillFx.bolt(bolt,pose,buffers,entityRenderDispatcher.cameraOrientation());
   super.render(e,yaw,partial,pose,buffers,light);
   return;
  }
  pose.pushPose();if(e instanceof cn.academy.biohum.RoseTrap){pose.translate(0,.03,0);pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(90));}else{pose.translate(0,size*.5,0);pose.mulPose(entityRenderDispatcher.cameraOrientation());}var p=pose.last();var v=buffers.getBuffer(RenderType.entityCutoutNoCull(texture));float s=size*.5f;
  v.addVertex(p,-s,-s,0).setColor(-1).setUv(0,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,0,1);
  v.addVertex(p,s,-s,0).setColor(-1).setUv(1,1).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,0,1);
  v.addVertex(p,s,s,0).setColor(-1).setUv(1,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,0,1);
  v.addVertex(p,-s,s,0).setColor(-1).setUv(0,0).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(p,0,0,1);
  pose.popPose();super.render(e,yaw,partial,pose,buffers,light);
 }
}
