/* AcademyCraft1.0.7 RendererMagHook native buffer adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import cn.academy.port.hook.MagHookEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public final class ClassicHookRenderer extends EntityRenderer<MagHookEntity> {
    public ClassicHookRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public void render(MagHookEntity hook,float yaw,float partial,PoseStack poses,MultiBufferSource buffers,int light){
        var models=ClassicHookModels.INSTANCE.models();if(models==null)return;
        poses.pushPose();
        try{
            float sourceYaw=hook.getYRot(),sourcePitch=hook.getXRot();
            if(hook.isHit()){
                var anchor=hook.anchor();var position=anchor.position();
                // Source preRender snaps the rendered origin even between native interpolation ticks.
                poses.translate(position.x()-Mth.lerp(partial,hook.xOld,hook.getX()),position.y()-Mth.lerp(partial,hook.yOld,hook.getY()),position.z()-Mth.lerp(partial,hook.zOld,hook.getZ()));
                sourceYaw=anchor.yaw();sourcePitch=anchor.pitch();
            }
            poses.mulPose(ClassicHookTransform.entity(sourceYaw,sourcePitch));
            ClassicPortableMeshEmission.emit(hook.isHit()?models.open():models.closed(),poses.last(),buffers.getBuffer(RenderType.entityTranslucent(ClassicHookModels.TEXTURE)),light,OverlayTexture.NO_OVERLAY);
        }finally{poses.popPose();}
    }
    @Override public ResourceLocation getTextureLocation(MagHookEntity hook){return ClassicHookModels.TEXTURE;}
}
