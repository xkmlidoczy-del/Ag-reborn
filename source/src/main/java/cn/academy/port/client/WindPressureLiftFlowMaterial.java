package cn.academy.port.client;

import cn.academy.port.HighPowerVisualConfig;
import java.security.MessageDigest;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;
import java.util.HexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManagerReloadListener;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;

/** Optional NEW443 transparent flow, using unchanged classic Tornado and glow assets; no movement authority. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class WindPressureLiftFlowMaterial {
    static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/tornado_ring.png");
    private static final String ORIGINAL_SHA = "9f5cc41dd8e4464e441d0a6e0636f0042b8d3bee82a0ab7b2ca094b5414a6429";
    private static volatile boolean ready;
    private static volatile ShaderInstance softShader;
    private static int shaderGeneration;
    static ShaderInstance shader() { return softShader != null ? softShader : GameRenderer.getPositionTexColorShader(); }
    private static volatile int generation;
    private WindPressureLiftFlowMaterial() {}
    public static boolean enabled() {
        try {
            return ready && !Boolean.getBoolean("academy.windPressureLiftFlow.forceFallback")
                    && softShader != null && HighPowerVisualConfig.SPEC.isLoaded()
                    && HighPowerVisualConfig.WIND_PRESSURE_LIFT_FLOW.get();
        } catch (RuntimeException unavailable) { return false; }
    }
    static void failed() { ready=false; }
    static int generation() { return generation; }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event) {
            int revision=++shaderGeneration;softShader=null;
            if(Boolean.getBoolean("academy.windPressureLiftFlow.forceFallback")) return;
            try {
                ShaderInstance next=new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath("academy","wind_lift_flow"),DefaultVertexFormat.POSITION_TEX_COLOR);
                if(GL20.glGetProgrami(next.getId(),GL20.GL_LINK_STATUS)==0){next.close();return;}
                event.registerShader(next,loaded->{if(revision==shaderGeneration)softShader=loaded;});
            } catch(Exception unavailable) {softShader=null;}
        }

        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> {
                ready = false;
                int revision = ++generation;
                boolean valid = false;
                try {
                    ResourceLocation[] textures = {GLOW, ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_line.png")};
                    String[] hashes = {ORIGINAL_SHA, "0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361"};
                    valid = true;
                    for (int i = 0; i < textures.length; i++) try (var stream = resources.getResourceOrThrow(textures[i]).open()) {
                        byte[] data = stream.readNBytes(65537);
                        valid &= data.length <= 65536 && hashes[i].equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
                    }
                } catch (Exception unavailable) { valid = false; }
                final boolean accepted = valid;
                Minecraft.getInstance().execute(() -> {
                    WindPressureLiftFlowRenderer.clear();
                    if (revision == generation) ready = accepted;
                });
            });
        }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("wind_lift")
                .then(Commands.literal("on").executes(c -> set(true)))
                .then(Commands.literal("off").executes(c -> set(false)))
                .then(Commands.literal("status").executes(c -> status()))));
    }
    private static int set(boolean on) {
        if (!HighPowerVisualConfig.SPEC.isLoaded()) return 0;
        HighPowerVisualConfig.WIND_PRESSURE_LIFT_FLOW.set(on);
        HighPowerVisualConfig.SPEC.save();
        return status();
    }
    private static int status() {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable("academy.vfx2.wind_lift.status", enabled(), ready, generation), false);
        return 1;
    }
}
