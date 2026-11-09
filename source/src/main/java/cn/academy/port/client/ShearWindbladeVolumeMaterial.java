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

/** Optional NEW421 volume, using the unchanged original ACA alpha texture and native shader. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class ShearWindbladeVolumeMaterial {
    static final ResourceLocation GLOW = ResourceLocation.fromNamespaceAndPath("academy", "textures/effects/glow_line.png");
    private static final String ORIGINAL_SHA = "0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361";
    private static volatile boolean ready;
    private static volatile int generation;
    private ShearWindbladeVolumeMaterial() {}
    public static boolean enabled() {
        try {
            return ready && !Boolean.getBoolean("academy.shearWindbladeVolume.forceFallback")
                    && GameRenderer.getPositionTexColorShader() != null && HighPowerVisualConfig.SPEC.isLoaded()
                    && HighPowerVisualConfig.SHEAR_WINDBLADE_VOLUME.get();
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
                try (var stream = resources.getResourceOrThrow(GLOW).open()) {
                    byte[] data = stream.readNBytes(65537);
                    valid = data.length <= 65536 && ORIGINAL_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)));
                } catch (Exception unavailable) { valid = false; }
                final boolean accepted = valid;
                Minecraft.getInstance().execute(() -> {
                    ShearWindbladeVolumePass.clear();
                    if (revision == generation) ready = accepted;
                });
            });
        }
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("shear_windblade")
                .then(Commands.literal("on").executes(c -> set(true)))
                .then(Commands.literal("off").executes(c -> set(false)))
                .then(Commands.literal("status").executes(c -> status()))));
    }
    private static int set(boolean on) {
        if (!HighPowerVisualConfig.SPEC.isLoaded()) return 0;
        HighPowerVisualConfig.SHEAR_WINDBLADE_VOLUME.set(on);
        HighPowerVisualConfig.SPEC.save();
        return status();
    }
    private static int status() {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable("academy.vfx2.shear_windblade.status", enabled(), ready, generation), false);
        return 1;
    }
}
