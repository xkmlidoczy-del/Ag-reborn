/* AcademyCraft 1.0.7 WirelessSystem server-END-tick adaptation, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Install once from common registration. No client classes, packet-driven ticks, or chunk tickets. */
public final class ClassicWirelessSystem {
    private static boolean registered;
    private ClassicWirelessSystem() {}
    public static void register() {
        if (!registered) { registered = true; NeoForge.EVENT_BUS.addListener(ClassicWirelessSystem::onServerTick); }
    }
    public static void onServerTick(ServerTickEvent.Post event) {
        for (var level : event.getServer().getAllLevels()) {
            var data = ClassicWirelessSavedData.getNonCreate(level);
            if (data != null) data.graph().tick();
        }
    }
}
