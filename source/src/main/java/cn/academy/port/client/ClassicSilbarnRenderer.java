/* AcademyCraft1.0.7 EntitySilbarn.RenderSibarn, GPLv3; see NOTICE. */
package cn.academy.port.client;
import cn.academy.port.skill.SilbarnEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.Util;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.resources.ResourceLocation;
import org.joml.Quaternionf;
import java.util.*;
public final class ClassicSilbarnRenderer extends EntityRenderer<SilbarnEntity> {
 private final Map<UUID,Long> created=new HashMap<>();private final ClassicMeltdownerStarterTimeline.PauseClock clock=new ClassicMeltdownerStarterTimeline.PauseClock();private Object world,connection;
 public ClassicSilbarnRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
 @Override public void render(SilbarnEntity entity,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){if(entity.isHit())return;var mesh=ClassicSilbarnModels.INSTANCE.mesh();if(mesh==null)return;var mc=net.minecraft.client.Minecraft.getInstance();if(world!=mc.level||connection!=mc.getConnection()){created.clear();clock.clear();world=mc.level;connection=mc.getConnection();}clock.update(Util.getMillis(),!mc.isPaused());long start=created.computeIfAbsent(entity.getUUID(),id->clock.elapsed());if(created.size()>4096)created.keySet().removeIf(id->!id.equals(entity.getUUID()));poses.pushPose();try{poses.scale(.05F,.05F,.05F);double norm=Math.sqrt((double)entity.axisX*entity.axisX+(double)entity.axisY*entity.axisY+(double)entity.axisZ*entity.axisZ);if(norm>0)poses.mulPose(new Quaternionf().fromAxisAngleRad((float)(entity.axisX/norm),(float)(entity.axisY/norm),(float)(entity.axisZ/norm),(float)(.03*(clock.elapsed()-start)*Math.PI/180)));poses.mulPose(Axis.YP.rotationDegrees(-entity.getYRot()));poses.mulPose(Axis.XP.rotationDegrees(90));ClassicPortableMeshEmission.emit(mesh,poses.last(),buffers.getBuffer(RenderType.entityTranslucent(ClassicSilbarnModels.TEXTURE)),light,net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY);}finally{poses.popPose();}}
 @Override public ResourceLocation getTextureLocation(SilbarnEntity e){return ClassicSilbarnModels.TEXTURE;}
}
