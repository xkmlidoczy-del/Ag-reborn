package cn.academy.port.client.vfx2;

/** Optional presentation policy; grants no damage, movement or packet authority. */
public final class MdNearEyeVisibility {
    public static final double FADE_START = .12, CORE_FADE_END = .80, GLOW_FADE_END = 1.60;
    public record Owner(Object player, Object level, Object connection, long session) {}
    private MdNearEyeVisibility() {}

    public static boolean eligible(Owner owner, boolean direct, Object player, Object level,
            Object connection, long session, Object cameraEntity, Object renderCameraEntity,
            boolean firstPerson, boolean detached, boolean alive, boolean spectator) {
        return direct && owner != null && player != null && level != null && connection != null
                && owner.player == player && owner.level == level && owner.connection == connection
                && owner.session == session && cameraEntity == player && renderCameraEntity == player
                && firstPerson && !detached && alive && !spectator;
    }

    /** Positive view-space depth, with a continuous first derivative and exact distant recovery. */
    public static double visibility(double viewDepth, boolean core) {
        if (!Double.isFinite(viewDepth)) return 1;
        double end = core ? CORE_FADE_END : GLOW_FADE_END;
        double t = Math.max(0, Math.min(1, (viewDepth - FADE_START) / (end - FADE_START)));
        return t * t * (3 - 2 * t);
    }
}
