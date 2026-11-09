package cn.academy.port.client;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.logging.LogUtils;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import java.io.IOException;

/** Exact no-alpha-cutoff material for source HUD alpha-test disable and ripple's alpha>0. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicSkillAlphaShader {
    private static ShaderInstance alpha;
    private ClassicSkillAlphaShader() {}
    @SubscribeEvent public static void register(RegisterShadersEvent event) {
        alpha=null;
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(),
                    ResourceLocation.fromNamespaceAndPath("academy","classic_skill_alpha"),
                    DefaultVertexFormat.POSITION_TEX_COLOR), shader -> alpha=shader);
        } catch(IOException exception) {
            LogUtils.getLogger().error("Classic skill no-cutoff shader failed; fallback cannot preserve low-alpha source fades",exception);
        }
    }
    public static ShaderInstance get() { return alpha!=null?alpha:GameRenderer.getPositionTexColorShader(); }
}
