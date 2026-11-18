package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Omnitrix;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

/** Base sword texture and flowing ImageGen material share one sampler atlas. */
final class AscalonSurfaceShader {
    private static final ResourceLocation ATLAS = Omnitrix.id("textures/vfx/ascalon_sword_atlas.png");
    private static ShaderInstance shader;
    private static final RenderType SOLID = type(false);
    private static final RenderType TRANSLUCENT = type(true);

    private AscalonSurfaceShader() {}

    private static RenderType type(boolean translucent) {
        var state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                .setTextureState(new RenderStateShard.TextureStateShard(ATLAS, true, false))
                .setTransparencyState(translucent ? RenderStateShard.TRANSLUCENT_TRANSPARENCY : RenderStateShard.NO_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true);
        return RenderType.create("aca_omnitrix:ascalon_surface_" + (translucent ? "glass" : "solid"),
                DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 512, true, translucent, state);
    }

    static boolean available() { return shader != null; }
    static RenderType cutout() { return SOLID; }
    static RenderType translucent() { return TRANSLUCENT; }

    static void clock(float partial) {
        var level = Minecraft.getInstance().level;
        float seconds = ((level == null ? 0 : Math.floorMod(level.getGameTime(), 1920)) + partial) / 20f;
        if (shader != null) {
            var time = shader.getUniform("StarfieldSeconds");
            if (time != null) time.set(seconds);
            var reduced = shader.getUniform("ReducedMotion");
            if (reduced != null) reduced.set(ClientState.reduced() ? 1f : 0f);
        }
    }

    static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Omnitrix.id("ascalon_surface"),
                    DefaultVertexFormat.NEW_ENTITY), s -> shader = s);
        } catch (IOException e) {
            throw new RuntimeException("Failed to register Ascalon surface shader", e);
        }
    }
}
