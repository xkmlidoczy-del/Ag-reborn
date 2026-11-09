package cn.academy.port.client;

import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

/**
 * Decoration-only memory of the local Magnetic Bracer key hold. It never gates gameplay: the server alone decides whether
 * the bracer is active. A hold is forgotten on world change and after a long silence so a lost release cannot leave it lit.
 */
final class BracerDecorState {
    private static final long MAX_HOLD_MILLIS = 120_000;
    private static long input, since;
    private static Level world;

    private BracerDecorState() {}

    static void pressed(long value) { input = value; since = System.currentTimeMillis(); world = Minecraft.getInstance().level; }
    static void released() { input = 0; world = null; }

    static boolean heldLocal() {
        Minecraft mc = Minecraft.getInstance();
        if (input <= 0 || mc.level == null || mc.level != world || System.currentTimeMillis() - since > MAX_HOLD_MILLIS) return false;
        return true;
    }
}
