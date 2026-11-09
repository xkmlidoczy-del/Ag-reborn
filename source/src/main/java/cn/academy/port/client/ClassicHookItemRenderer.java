/* AcademyCraft1.0.7 HookRender native BEWLR adaptation. GPLv3; see NOTICE. */
package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;

public final class ClassicHookItemRenderer extends BlockEntityWithoutLevelRenderer {
    private final ClassicHookTransform.ItemState state=new ClassicHookTransform.ItemState();
    public ClassicHookItemRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext display,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(display==ItemDisplayContext.GUI)return;var models=ClassicHookModels.INSTANCE.models();if(models==null)return;
        var context=ClassicPortableRenderer.context(display);
        boolean left=display==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||display==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
        poses.pushPose();try{
            poses.translate(.5,.5,.5);poses.mulPose(ClassicPortableContextBridge.matrix(context,left));poses.mulPose(state.render(context,left));
            ClassicPortableMeshEmission.emit(models.closed(),poses.last(),buffers.getBuffer(RenderType.entityTranslucent(ClassicHookModels.TEXTURE)),light,overlay);
        }finally{poses.popPose();}
    }
}
