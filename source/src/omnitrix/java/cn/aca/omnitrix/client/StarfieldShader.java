package cn.aca.omnitrix.client;

import cn.aca.omnitrix.Omnitrix;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.io.IOException;
import java.util.function.Function;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderStateShard;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;

public final class StarfieldShader {
    private static ShaderInstance shader;
    private static final int TICKS_PER_SECOND = 20;
    private static final int PHASE_TICKS = 96 * TICKS_PER_SECOND;

    private static final Function<ResourceLocation, RenderType> CUTOUT = Util.memoize(texture -> {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.NO_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true);
        return RenderType.create("aca_omnitrix:starfield_cutout", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, true, false, state);
    });

    private static final Function<ResourceLocation, RenderType> TRANSLUCENT = Util.memoize(texture -> {
        RenderType.CompositeState state = RenderType.CompositeState.builder()
                .setShaderState(new RenderStateShard.ShaderStateShard(() -> shader))
                .setTextureState(new RenderStateShard.TextureStateShard(texture, false, false))
                .setTransparencyState(RenderStateShard.TRANSLUCENT_TRANSPARENCY)
                .setCullState(RenderStateShard.NO_CULL)
                .setLightmapState(RenderStateShard.LIGHTMAP)
                .setOverlayState(RenderStateShard.OVERLAY)
                .createCompositeState(true);
        return RenderType.create("aca_omnitrix:starfield_translucent", DefaultVertexFormat.NEW_ENTITY,
                VertexFormat.Mode.QUADS, 256, true, true, state);
    });

    private StarfieldShader() {}

    static boolean available() { return shader != null; }

    public static RenderType cutout(ResourceLocation texture) {
        return shader != null ? CUTOUT.apply(texture) : RenderType.entityCutoutNoCull(texture);
    }

    public static RenderType translucent(ResourceLocation texture) {
        return shader != null ? TRANSLUCENT.apply(texture) : RenderType.entityTranslucent(texture);
    }

    static void clock(float partial) {
        var level = Minecraft.getInstance().level;
        long tick = level == null ? 0 : Math.floorMod(level.getGameTime(), PHASE_TICKS);
        float seconds = (tick + partial) / TICKS_PER_SECOND;
        if (shader != null) {
            var timeUniform = shader.getUniform("StarfieldSeconds");
            if (timeUniform != null) timeUniform.set(seconds);
            var reducedUniform = shader.getUniform("ReducedMotion");
            if (reducedUniform != null) reducedUniform.set(ClientState.reduced() ? 1.0f : 0.0f);
        }
    }

    public static void register(RegisterShadersEvent event) {
        try {
            event.registerShader(new ShaderInstance(event.getResourceProvider(), Omnitrix.id("starfield"),
                    DefaultVertexFormat.NEW_ENTITY), s -> shader = s);
        } catch (IOException e) {
            throw new RuntimeException("Failed to register starfield shader", e);
        }
    }
}
