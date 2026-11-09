/*
 * AcademyCraft visual timing adapter.
 * Original constants: Copyright (c) Lambda Innovation, 2013-2016.
 * See docs/UPSTREAM-README-1.0.7.md for the original license and notices.
 */
package cn.academy.port.client;

/** Pure timing equations, kept independent of Minecraft and graphics APIs. */
public final class ClassicEffectTimeline {
    public static final long RAILGUN_LIFETIME_MS = 50 * 50L;
    public static final long RAILGUN_BLEND_IN_MS = 150;
    public static final long RAILGUN_BLEND_OUT_MS = 1000;
    public static final long RAILGUN_WIDTH_SHRINK_MS = 800;
    public static final int CHARGE_FRAME_MS = 40;
    public static final int CHARGE_FRAME_COUNT = 40;

    private ClassicEffectTimeline() {}

    public static double beamLengthScale(long ageMillis) {
        return clamp(ageMillis / (double) RAILGUN_BLEND_IN_MS);
    }

    public static double beamAlpha(long ageMillis) {
        return clamp((RAILGUN_LIFETIME_MS - ageMillis) / (double) RAILGUN_BLEND_OUT_MS);
    }

    public static double beamWidthScale(long ageMillis) {
        return clamp((RAILGUN_LIFETIME_MS - ageMillis) / (double) RAILGUN_WIDTH_SHRINK_MS);
    }

    /** Returns -1 once the 1.6-second animation has finished. Never wraps frames. */
    public static int chargeFrame(long ageMillis) {
        if (ageMillis >= (long) CHARGE_FRAME_MS * CHARGE_FRAME_COUNT) return -1;
        return (int) Math.max(0, ageMillis / CHARGE_FRAME_MS);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
