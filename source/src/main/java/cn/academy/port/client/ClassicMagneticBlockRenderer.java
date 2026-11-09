/* AcademyCraft1.0.7 RenderEntityBlock rotation adapted to modern baked block rendering. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.skill.ElectromasterEntities;
import cn.academy.port.skill.MagneticBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Original spinning block, rendered as its actual baked state; not a particle billboard. */
public final class ClassicMagneticBlockRenderer extends EntityRenderer<MagneticBlockEntity> {
    public ClassicMagneticBlockRenderer(EntityRendererProvider.Context context){super(context);shadowRadius=0;}
    @Override public void render(MagneticBlockEntity e,float yaw,float partial,PoseStack p,MultiBufferSource b,int light){
        if(e.carriedState().isAir())return;p.pushPose();
        try {p.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partial,e.previousSpinYaw,e.spinYaw)));p.mulPose(Axis.XP.rotationDegrees(Mth.lerp(partial,e.previousSpinPitch,e.spinPitch)));p.translate(-.5,-.5,-.5);Minecraft.getInstance().getBlockRenderer().renderSingleBlock(e.carriedState(),p,b,light,OverlayTexture.NO_OVERLAY);}finally{p.popPose();}
        super.render(e,yaw,partial,p,b,light);
    }
    @Override public ResourceLocation getTextureLocation(MagneticBlockEntity e){return TextureAtlas.LOCATION_BLOCKS;}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
    public static final class Registration {
        @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(ElectromasterEntities.MAGNETIC_BLOCK.get(),ClassicMagneticBlockRenderer::new);}
    }
}
