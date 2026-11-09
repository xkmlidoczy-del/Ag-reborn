package cn.academy.port.client;

import cn.academy.port.HighPowerVisualConfig;
import java.security.MessageDigest;
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

/** Optional NEW251 source crown, using the unchanged original ACA alpha texture and native shader. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MulticoreCanopyCrownMaterial {
    static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/mdball/glow.png");
    private static final String ORIGINAL_SHA = "2dd6e193adfbf100cf00671aa40ada6c40acd01156c9c44ee231edf50e9208b5";
    private static volatile boolean ready;
    private static volatile int generation;
    private MulticoreCanopyCrownMaterial() {}
    public static boolean enabled() {
        try {
            return ready && !Boolean.getBoolean("academy.multicoreCanopyCrown.forceFallback")
                    && GameRenderer.getPositionTexColorShader() != null && FoldedMirrorMaterial.available() && HighPowerVisualConfig.SPEC.isLoaded()
                    && HighPowerVisualConfig.MULTICORE_CANOPY_CROWN.get();
        } catch (RuntimeException unavailable) { return false; }
    }
    static int generation() { return generation; }
    @EventBusSubscriber(modid = "academy", value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
    public static final class Reload {
        @SubscribeEvent public static void register(RegisterClientReloadListenersEvent event) {
            event.registerReloadListener((ResourceManagerReloadListener) resources -> {
                ready = false;
                int revision = ++generation;
                boolean valid = false;
                try {
                    ResourceLocation[] textures = {GLOW, ResourceLocation.fromNamespaceAndPath("academy", "textures/port/white.png")};
                    String[] hashes = {ORIGINAL_SHA, "7ac2abfce2be8b46dd9826d597d94edeefc1570b4a992622721b90a1850e3ee9"};
                    valid = true;
                    for (int i = 0; i < textures.length; i++) try (var stream = resources.getResourceOrThrow(textures[i]).open()) {
                        byte[] data = stream.readNBytes(65537);
                        valid &= data.length <= 65536 && hashes[i].equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
                    }
                } catch (Exception unavailable) { valid = false; }
                final boolean accepted = valid;
                Minecraft.getInstance().execute(() -> {
                    if (revision == generation) ready = accepted;
                });
            });
        }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("multicore_canopy")
                .then(Commands.literal("on").executes(c -> set(true)))
                .then(Commands.literal("off").executes(c -> set(false)))
                .then(Commands.literal("status").executes(c -> status()))));
    }
    private static int set(boolean on) {
        if (!HighPowerVisualConfig.SPEC.isLoaded()) return 0;
        HighPowerVisualConfig.MULTICORE_CANOPY_CROWN.set(on);
        HighPowerVisualConfig.SPEC.save();
        return status();
    }
    private static int status() {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable("academy.vfx2.multicore_canopy.status", enabled(), ready, generation), false);
        return 1;
    }
}
