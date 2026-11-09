package cn.academy.port.fleet.client;

/**
 * Shared, stateless presentation clock for the exported reload clips, gun mesh,
 * first-person hands and HUD. This class never modifies ammo or server state.
 * Positions are metres; root angles are degrees. Magazine drop and tilt are
 * dimensionless weights multiplied by each gun's asset profile.
 */
public final class ReloadTimeline {
    public enum Phase { IDLE, REACH, RELEASE, REMOVE, EXCHANGE, INSERT, SEAT, CHARGE, RETURN }

    public record Pose(float progress, Phase phase, float handToMagazine,
                       float handToCharge, float magazineDrop, float magazineTilt,
                       float rootPitchDeg, float rootRollDeg, float rootLower,
                       float charge, boolean magazineVisible) {
        /** Convenience angle for the default -12 degree magazine profile. */
        public float magazineTiltDeg() { return -12.0f * magazineTilt; }
        public boolean active() { return phase != Phase.IDLE; }
    }

    private static final Pose IDLE = new Pose(0, Phase.IDLE, 0, 0, 0, 0, 0, 0, 0, 0, true);
    private ReloadTimeline() {}

    public static boolean isActive(long start, long end, double now) {
        return start >= 0 && end > start && Double.isFinite(now) && now >= start && now < end;
    }

    /**
     * No .999 clamp: stale tags after completion must return exactly to rest.
     * A cancelled reload is represented by start=end=0 by the existing server.
     */
    public static float progress(long start, long end, double now) {
        if (!isActive(start, end, now)) return 0;
        double duration = (double) end - (double) start;
        if (!(duration > 0)) return 0;
        return (float) Math.max(0.0, Math.min(Math.nextDown(1.0f), (now - start) / duration));
    }

    public static Pose sample(float progress, boolean empty) {
        if (!Float.isFinite(progress) || progress <= 0 || progress >= 1) return IDLE;
        float r = progress;
        float cant = smooth(0, .15f, r) * (1 - smooth(.87f, 1, r));
        float drop = smooth(.20f, .36f, r) * (1 - smooth(.50f, .68f, r));
        float tilt = smooth(.22f, .36f, r) * (1 - smooth(.55f, .68f, r));
        float onMag = smooth(.04f, .17f, r)
                * (1 - smooth(empty ? .70f : .72f, empty ? .76f : .84f, r));
        float onCharge = empty ? smooth(.75f, .80f, r) * (1 - smooth(.89f, .95f, r)) : 0;
        float charge = empty ? smooth(.79f, .84f, r) * (1 - smooth(.86f, .91f, r)) : 0;
        Phase phase = r < .17f ? Phase.REACH
                : r < .20f ? Phase.RELEASE
                : r < .38f ? Phase.REMOVE
                : r < .50f ? Phase.EXCHANGE
                : r < .68f ? Phase.INSERT
                : r < .75f ? Phase.SEAT
                : empty && r < .91f ? Phase.CHARGE : Phase.RETURN;
        return new Pose(r, phase, onMag, onCharge, drop, tilt,
                8 * cant, -16 * cant, .035f * cant, charge, !(r >= .38f && r < .49f));
    }

    public static float smooth(float a, float b, float value) {
        if (!Float.isFinite(value)) return 0;
        if (!(b > a)) throw new IllegalArgumentException("smooth interval must increase");
        float t = Math.max(0, Math.min(1, (value - a) / (b - a)));
        return t * t * (3 - 2 * t);
    }
}
