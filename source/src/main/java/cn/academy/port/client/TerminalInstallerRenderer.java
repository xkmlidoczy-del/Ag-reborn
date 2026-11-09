/* AcademyCraft1.0.7 TerminalInstallerRenderer → modern BEWLR. GPLv3. */
package cn.academy.port.client;
import cn.academy.port.client.terminal.TerminalInstallerTransform;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
public final class TerminalInstallerRenderer extends BlockEntityWithoutLevelRenderer {
    public TerminalInstallerRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @Override public void renderByItem(ItemStack item,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(context==ItemDisplayContext.GUI)return;var mesh=TerminalInstallerModels.INSTANCE.mesh();if(mesh==null)return;
        pose.pushPose();try{pose.translate(.5,.5,.5);boolean left=context==ItemDisplayContext.FIRST_PERSON_LEFT_HAND||context==ItemDisplayContext.THIRD_PERSON_LEFT_HAND;var source=ClassicPortableRenderer.context(context);pose.mulPose(ClassicPortableContextBridge.matrix(source,left));pose.mulPose(TerminalInstallerTransform.matrix(source,left));ClassicPortableMeshEmission.emit(mesh,pose.last(),buffers.getBuffer(RenderType.entityTranslucent(TerminalInstallerModels.TEXTURE)),light,overlay);}finally{pose.popPose();}
    }
}
