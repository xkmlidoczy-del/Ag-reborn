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

/** Optional NEW233 local heat focus, using unchanged classic MD textures; no mining authority. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class PointMeltdownFocusMaterial {
    static final ResourceLocation[] TEXTURES = {ResourceLocation.fromNamespaceAndPath("academy", "textures/port/white.png"), ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdball/glow.png"), ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdray_small/blend_in.png"), ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdray_small/tile.png"), ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdray_small/blend_out.png"), ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdball/0.png"), ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_line.png")};
    private static final String[] HASHES = {"7ac2abfce2be8b46dd9826d597d94edeefc1570b4a992622721b90a1850e3ee9", "2dd6e193adfbf100cf00671aa40ada6c40acd01156c9c44ee231edf50e9208b5", "ac97e7a452ecdc5a41f6b2379c98225e443693337f3dd90e2ba54707f1b38104", "591ab5e780dc406a1d32e4dbf938fc584a56fe8898dcc9c596cf5e07b912e88f", "0c71ed58ed0c122517e09a3db39addf931934ed22b58f880c781f0c31d916620", "01130bef3ab1d82cfaf41892cae654f8d77d40ed0c00d3642d4fffd31d09546b", "0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361"};
    private static volatile boolean ready;
    private static volatile ShaderInstance softShader;
    private static int shaderGeneration;
    static ShaderInstance shader() { return softShader != null ? softShader : GameRenderer.getPositionTexColorShader(); }
    private static volatile int generation;
    private PointMeltdownFocusMaterial() {}
    public static boolean enabled() {
        try {
            return ready && !Boolean.getBoolean("academy.pointMeltdownFocus.forceFallback")
                    && softShader != null && HighPowerVisualConfig.SPEC.isLoaded()
                    && HighPowerVisualConfig.POINT_MELTDOWN_FOCUS.get();
        } catch (RuntimeException unavailable) { return false; }
    }
    static void failed() { ready=false; }
    static int generation() { return generation; }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void shaders(RegisterShadersEvent event) {
            int revision=++shaderGeneration;softShader=null;
            if(Boolean.getBoolean("academy.pointMeltdownFocus.forceFallback")) return;
            try {
                ShaderInstance next=new ShaderInstance(event.getResourceProvider(),
                        ResourceLocation.fromNamespaceAndPath("academy","point_meltdown_focus"),DefaultVertexFormat.POSITION_TEX_COLOR);
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
                    ResourceLocation[] textures = TEXTURES;
                    String[] hashes = HASHES;
                    valid = true;
                    for (int i = 0; i < textures.length; i++) try (var stream = resources.getResourceOrThrow(textures[i]).open()) {
                        byte[] data = stream.readNBytes(65537);
                        valid &= data.length <= 65536 && hashes[i].equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
                    }
                } catch (Exception unavailable) { valid = false; }
                final boolean accepted = valid;
                Minecraft.getInstance().execute(() -> {
                    PointMeltdownFocusRenderer.clear();
                    if (revision == generation) ready = accepted;
                });
            });
        }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("point_meltdown")
                .then(Commands.literal("on").executes(c -> set(true)))
                .then(Commands.literal("off").executes(c -> set(false)))
                .then(Commands.literal("status").executes(c -> status()))));
    }
    private static int set(boolean on) {
        if (!HighPowerVisualConfig.SPEC.isLoaded()) return 0;
        HighPowerVisualConfig.POINT_MELTDOWN_FOCUS.set(on);
        HighPowerVisualConfig.SPEC.save();
        return status();
    }
    private static int status() {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable("academy.vfx2.point_meltdown.status", enabled(), ready, generation), false);
        return 1;
    }
}
