package cn.academy.port.client.vfx2;

import cn.academy.port.ClassicHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Persisted client-only switch. No server command or ability action is submitted. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class MdNearEyeClient {
    private MdNearEyeClient() {}
    public static boolean enabled() {
        return ClassicHudConfig.SPEC.isLoaded() && ClassicHudConfig.MD_NEAR_EYE_VISIBILITY.get();
    }
    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(Commands.literal("md_near_eye")
                .then(Commands.literal("on").executes(context -> set(true)))
                .then(Commands.literal("off").executes(context -> set(false)))
                .then(Commands.literal("status").executes(context -> status()))));
    }
    private static int set(boolean enabled) {
        if (!ClassicHudConfig.SPEC.isLoaded()) return 0;
        ClassicHudConfig.MD_NEAR_EYE_VISIBILITY.set(enabled);
        ClassicHudConfig.SPEC.save();
        return status();
    }
    private static int status() {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.translatable(
                "academy.vfx2.md_near_eye.status", enabled(), MdNearEyeMaterial.available()), false);
        return 1;
    }
}
