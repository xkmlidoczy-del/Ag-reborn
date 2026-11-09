/* AcademyCraft1.0.7 RenderItemAchievement GUI-only dynamic texture display. GPLv3. */
package cn.academy.port.client;
import cn.academy.port.display.ClassicAchievementIconItem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.world.item.*;
public final class ClassicAchievementIconRenderer extends BlockEntityWithoutLevelRenderer {
    public ClassicAchievementIconRenderer(){super(Minecraft.getInstance().getBlockEntityRenderDispatcher(),Minecraft.getInstance().getEntityModels());}
    @Override public void renderByItem(ItemStack stack,ItemDisplayContext context,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(context!=ItemDisplayContext.GUI)return;
        // Native GUI scales this unit-square to16px. Source alpha blending and inventory-only path remain.
        var vertices=buffers.getBuffer(RenderType.entityTranslucent(ClassicAchievementIconItem.texture(stack)));
        var p=pose.last();int full=net.minecraft.client.renderer.LightTexture.FULL_BRIGHT;
        vertex(p,vertices,0,0,0,1,full,overlay);vertex(p,vertices,1,0,1,1,full,overlay);vertex(p,vertices,1,1,1,0,full,overlay);vertex(p,vertices,0,1,0,0,full,overlay);
    }
    private static void vertex(PoseStack.Pose p,com.mojang.blaze3d.vertex.VertexConsumer v,float x,float y,float u,float w,int light,int overlay){v.addVertex(p,x,y,0).setColor(255,255,255,255).setUv(u,w).setOverlay(overlay).setLight(light).setNormal(p,0,0,1);}
}
