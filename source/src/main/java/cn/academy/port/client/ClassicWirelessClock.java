/* LambdaLib GameTimer client-pause semantics, MIT; see NOTICE. */
package cn.academy.port.client;

import net.minecraft.Util;
import net.minecraft.client.Minecraft;

/** Client thread only; both matrix rotation and GUI breathing freeze while the game is paused. */
final class ClassicWirelessClock {
    private static long lastReal = -1, elapsed;
    private ClassicWirelessClock() {}
    static long millis() {
        long real = Util.getMillis();
        if (lastReal >= 0 && !Minecraft.getInstance().isPaused()) elapsed += Math.max(0, real - lastReal);
        lastReal = real;
        return elapsed;
    }
}
