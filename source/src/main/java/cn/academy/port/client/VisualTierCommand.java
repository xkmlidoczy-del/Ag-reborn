package cn.academy.port.client;

import cn.academy.port.HighPowerVisualConfig;
import cn.academy.port.VisualTier;
import cn.academy.port.VisualTierConfig;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * {@code /aca_vfx2 tier off|low|standard|cinematic|status}: the master switch for every optional high-power material.
 * The per-material switches stay in {@link HighPowerVisualConfig}; the tier writes them together (OFF = all off, otherwise
 * all on), and a one-time migration applies the default tier to configs that predate it.
 */
@EventBusSubscriber(modid = "academy", value = Dist.CLIENT)
public final class VisualTierCommand {
    private static boolean migrationChecked;

    private VisualTierCommand() {}

    private static List<ModConfigSpec.BooleanValue> switches() {
        return List.of(HighPowerVisualConfig.MAGNETIC_DOMAIN_FLOW, HighPowerVisualConfig.RAIL_ARRAY_GRAND, HighPowerVisualConfig.LANDING_ARRAY_CUES,
                HighPowerVisualConfig.ZERO_GAP_ARMOR_STATE, HighPowerVisualConfig.SHEAR_WINDBLADE_VOLUME, HighPowerVisualConfig.MULTICORE_CANOPY_CROWN,
                HighPowerVisualConfig.TRAJECTORY_RETURN_PRESSURE, HighPowerVisualConfig.WIND_PRESSURE_LIFT_FLOW, HighPowerVisualConfig.POINT_MELTDOWN_FOCUS);
    }

    private static void apply(VisualTier tier) {
        boolean on = tier != VisualTier.OFF;
        for (var value : switches()) value.set(on);
        HighPowerVisualConfig.SPEC.save();
    }

    @SubscribeEvent public static void commands(RegisterClientCommandsEvent event) {
        var tier = Commands.literal("tier");
        for (VisualTier value : VisualTier.values())
            tier.then(Commands.literal(value.name().toLowerCase(Locale.ROOT)).executes(context -> set(value)));
        tier.then(Commands.literal("status").executes(context -> message()));
        event.getDispatcher().register(Commands.literal("aca_vfx2").then(tier));
    }

    /** First tick with both specs loaded: a config created before tiers existed gets the default tier's switches once. */
    @SubscribeEvent public static void migrate(ClientTickEvent.Post event) {
        if (migrationChecked || !VisualTierConfig.SPEC.isLoaded() || !HighPowerVisualConfig.SPEC.isLoaded()) return;
        migrationChecked = true;
        try {
            if (VisualTierConfig.MIGRATED.get()) return;
            apply(VisualTierConfig.TIER.get());
            VisualTierConfig.MIGRATED.set(true);
            VisualTierConfig.SPEC.save();
        } catch (RuntimeException unreadable) { /* leave the switches exactly as saved */ }
    }

    private static int set(VisualTier value) {
        if (!VisualTierConfig.SPEC.isLoaded() || !HighPowerVisualConfig.SPEC.isLoaded()) return 0;
        VisualTierConfig.TIER.set(value);
        VisualTierConfig.MIGRATED.set(true);
        VisualTierConfig.SPEC.save();
        apply(value);
        return message();
    }

    private static int message() {
        var player = Minecraft.getInstance().player;
        if (player != null) player.displayClientMessage(Component.literal("High-power visual tier: " + VisualTierConfig.tier()
                + " (decoration only; damage, range and server state are unchanged)"), false);
        return 1;
    }
}
