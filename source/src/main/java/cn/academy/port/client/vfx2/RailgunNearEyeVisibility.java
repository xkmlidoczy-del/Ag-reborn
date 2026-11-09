package cn.academy.port.client.vfx2;

/** Pure policy and numerical oracle for the opt-in first-person visibility material. */
public final class RailgunNearEyeVisibility {
    public static final double FADE_START = .12;
    public static final double GLOW_FADE_END = 1.60;
    public static final double CORE_FADE_END = .80;

    /** Object identity prevents entity-ID reuse, respawn and reconnect from inheriting a shot. */
    public record Owner(Object player, Object level, Object connection, long session) {}

    private RailgunNearEyeVisibility() {}

    public static boolean eligible(Owner owner, Object player, Object level, Object connection,
                                   long session, Object cameraEntity, boolean firstPerson,
                                   boolean alive, boolean spectator) {
        return owner != null && player != null && level != null && connection != null
                && owner.player == player && owner.level == level && owner.connection == connection
                && owner.session == session && cameraEntity == player && firstPerson && alive && !spectator;
    }

    /**
     * Positive view depth, never world Z or distance to the beam axis. No proximity gate can
     * flap as the camera moves: the cubic and its first derivative are continuous at both ends.
     * Unsafe fragments contribute zero; the core recovers sooner to preserve its direction.
     * Fragments at/after 1.6 blocks are unchanged. No beam or primitive is discarded here.
     */
    public static double visibility(double viewDepth, boolean core) {
        if (!Double.isFinite(viewDepth)) return 1;
        double end = core ? CORE_FADE_END : GLOW_FADE_END;
        double progress = Math.max(0, Math.min(1, (viewDepth - FADE_START) / (end - FADE_START)));
        return progress * progress * (3 - 2 * progress);
    }
}
