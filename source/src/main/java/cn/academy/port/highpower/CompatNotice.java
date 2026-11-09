package cn.academy.port.highpower;

import com.mojang.logging.LogUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/** One log line at server start and one chat line per login, only when other mods are installed. */
public final class CompatNotice {
    private static final org.slf4j.Logger LOG = LogUtils.getLogger();

    private CompatNotice() {}

    @SubscribeEvent public static void started(ServerStartedEvent event) {
        var foreign = CompatMode.foreignMods();
        if (foreign.isEmpty()) return;
        if (CompatMode.open()) LOG.info("Academy high-power open compatibility is ON with {} other mod(s): {}. The exact ledger stays active; a damage transaction altered by a foreign hook is refused and falls back to ordinary combat.", foreign.size(), foreign);
        else LOG.warn("Academy high-power strict contract is ON (highPowerOpenCompat=false) and these other mods are installed: {}. High-power combat and mining are disabled by design.", foreign);
    }

    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || CompatMode.foreignMods().isEmpty()) return;
        player.displayClientMessage(Component.translatable(CompatMode.open() ? "message.academy.highpower.compat.open" : "message.academy.highpower.compat.strict"), false);
    }
}
