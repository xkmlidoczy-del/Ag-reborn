package cn.academy.port.client;

import cn.academy.port.ClassicHudConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

import java.util.Locale;

/** One optional material decision for an already accepted classic shield quad. */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class LightShieldVfx2 {
    private static int admitted;
    private static ClassicHudConfig.Vfx2Quality frameQuality = ClassicHudConfig.Vfx2Quality.OFF;
    private LightShieldVfx2() {}

    @SubscribeEvent(priority = EventPriority.HIGH) public static void beginFrame(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        admitted = 0;
        frameQuality = quality();
    }

    /** Failure and cap exhaustion return the exact input instance before any geometry is written. */
    public static RenderType materialOrClassic(RenderType original, long phaseMillis) {
        ClassicHudConfig.Vfx2Quality current = quality();
        if (original == null || phaseMillis < 0 || current == ClassicHudConfig.Vfx2Quality.OFF || current != frameQuality || admitted >= cap(current)) return original;
        try {
            if (!LightShieldVfx2Material.bind(current, phaseMillis)) return original;
            RenderType replacement = LightShieldVfx2RenderTypes.SHIELD;
            if (!LightShieldVfx2Material.available()) return original;
            admitted++;
            return replacement;
        } catch (RuntimeException failure) {
            return original;
        }
    }

    private static int cap(ClassicHudConfig.Vfx2Quality quality) {
        return switch (quality) { case OFF -> 0; case LOW -> 8; case MEDIUM -> 16; };
    }
    private static ClassicHudConfig.Vfx2Quality quality() {
        try {
            if (!ClassicHudConfig.SPEC.isLoaded()) return ClassicHudConfig.Vfx2Quality.OFF;
            var value = ClassicHudConfig.LIGHT_SHIELD_VFX2_QUALITY.get();
            return value == null ? ClassicHudConfig.Vfx2Quality.OFF : value;
        } catch (RuntimeException invalidConfig) {
            return ClassicHudConfig.Vfx2Quality.OFF;
        }
    }

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        var shield = Commands.literal("light_shield");
        for (var value : ClassicHudConfig.Vfx2Quality.values())
            shield.then(Commands.literal(value.name().toLowerCase(Locale.ROOT)).executes(context -> set(value)));
        shield.then(Commands.literal("status").executes(context -> status()));
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(shield));
    }
    private static int set(ClassicHudConfig.Vfx2Quality value) {
        if (!ClassicHudConfig.SPEC.isLoaded()) return 0;
        ClassicHudConfig.LIGHT_SHIELD_VFX2_QUALITY.set(value);
        ClassicHudConfig.SPEC.save();
        var mc = Minecraft.getInstance();
        if (mc.player != null) mc.player.displayClientMessage(Component.translatable("academy.vfx2.light_shield.quality", value.name()), false);
        return 1;
    }
    private static int status() {
        var mc = Minecraft.getInstance();
        if (mc.player != null) {
            var value = quality();
            mc.player.displayClientMessage(Component.translatable("academy.vfx2.light_shield.status",
                    value.name(), LightShieldVfx2Material.available(), admitted, cap(value), LightShieldVfx2Material.generation()), false);
        }
        return 1;
    }
}
