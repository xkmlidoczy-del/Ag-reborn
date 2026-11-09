/* RenderDeveloperPortable source OBJ, modern native item context adapter. GPLv3; see NOTICE. */
package cn.academy.port.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Source-alpha blended, double-sided mesh. Native item renderer retains light, bob/spin and outer hand pose. */
public final class ClassicPortableRenderer extends BlockEntityWithoutLevelRenderer {
    public ClassicPortableRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    static ClassicPortableTransform.Context context(ItemDisplayContext context){
        return switch(context){
            case FIRST_PERSON_LEFT_HAND,FIRST_PERSON_RIGHT_HAND->ClassicPortableTransform.Context.FIRST_PERSON;
            case THIRD_PERSON_LEFT_HAND,THIRD_PERSON_RIGHT_HAND->ClassicPortableTransform.Context.THIRD_PERSON;
            case GROUND->ClassicPortableTransform.Context.GROUND;
            default->ClassicPortableTransform.Context.STANDARD;
        };
    }
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack poses,MultiBufferSource buffers,int light,int overlay){
        if(context==ItemDisplayContext.GUI)return;
        var mesh=ClassicPortableModels.INSTANCE.mesh();if(mesh==null)return;
        poses.pushPose();
        try{
            // ItemRenderer subtracts .5 after context selection. Source custom meshes were centered on the callback origin.
            poses.translate(.5,.5,.5);
            boolean left=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;
            // Legacy vanilla and Forge wrapped the callback before LambdaLib's source-local renderer.
            poses.mulPose(ClassicPortableContextBridge.matrix(context(context),left));
            poses.mulPose(ClassicPortableTransform.matrix(context(context),left));
            ClassicPortableMeshEmission.emit(mesh,poses.last(),buffers.getBuffer(RenderType.entityTranslucent(ClassicPortableModels.TEXTURE)),light,overlay);
        }finally{poses.popPose();}
    }
}
