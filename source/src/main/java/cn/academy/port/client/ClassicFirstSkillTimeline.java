/* AcademyCraft 1.0.7 AnimPresets/MD/particle timing adaptation. See NOTICE. */
package cn.academy.port.client;

/** Pure, dependency-free values consumed by the client-only renderer and regression tests. */
public final class ClassicFirstSkillTimeline {
    public static final long PREPARE_MS = 300, PUNCH_CURVE_MS = 300;
    // ShockContext terminates on punchTicker > 6, after the seventh tick.
    public static final long PUNCH_CONTEXT_MS = 350, MAX_HOLD_MS = 10_000;
    public static final long SMALL_RAY_MS = 700;
    private static final ClassicCubicCurve PREPARE_X = new ClassicCubicCurve(0, 0, 1, -.02);
    private static final ClassicCubicCurve PREPARE_Y = new ClassicCubicCurve(0, 0, .5, .2, 1, .4);
    private static final ClassicCubicCurve PREPARE_Z = new ClassicCubicCurve(0, 0, 1, -.05);
    private static final ClassicCubicCurve PREPARE_RX = new ClassicCubicCurve(0, 0, 1, -20);
    private static final ClassicCubicCurve PUNCH_X = new ClassicCubicCurve(0, -.04, .5, -.04, 1, 0);
    private static final ClassicCubicCurve PUNCH_Y = new ClassicCubicCurve(0, .8, .5, .75, 1, 0);
    private static final ClassicCubicCurve PUNCH_Z = new ClassicCubicCurve(0, 0, .3, -.4, 1, 0);
    private static final ClassicCubicCurve PUNCH_RX = new ClassicCubicCurve(0, -40, .5, -45, 1, 0);
    private static final ClassicCubicCurve PUNCH_RY = new ClassicCubicCurve(0, 0, .3, 10, 1, 0);

    private ClassicFirstSkillTimeline() {}
    public record HandPose(double x, double y, double z, double rotationX, double rotationY) {}

    public static HandPose prepare(double ageMillis) {
        double time = Math.min(2, Math.max(0, ageMillis) / 150);
        return new HandPose(PREPARE_X.valueAt(time), PREPARE_Y.valueAt(time), PREPARE_Z.valueAt(time),
                PREPARE_RX.valueAt(time), 0);
    }

    public static HandPose punch(double ageMillis) {
        double time = Math.max(0, ageMillis) / PUNCH_CURVE_MS;
        return new HandPose(PUNCH_X.valueAt(time), PUNCH_Y.valueAt(time), PUNCH_Z.valueAt(time),
                PUNCH_RX.valueAt(time), PUNCH_RY.valueAt(time));
    }

    /** Exact original branch order matters particularly for the improved five-tick orb. */
    public static double orbAlpha(int lifeTicks, double ageMillis) {
        double lifeMillis = lifeTicks * 50.0, age = Math.max(0, ageMillis);
        if (age > lifeMillis - 150) return Math.max(0, 1 - (age - (lifeMillis - 150)) / 150);
        if (age > lifeMillis - 400) return .6 + .4 * (age - (lifeMillis - 400)) / 250;
        if (age < 300) return .6 * age / 300;
        return .6;
    }

    public static double orbSize(int lifeTicks, double ageMillis) {
        double lifeMillis = lifeTicks * 50.0, age = Math.max(0, ageMillis);
        if (age > lifeMillis - 100) return Math.max(0, 1.5 * (1 - (age - (lifeMillis - 100)) / 100));
        if (age > lifeMillis - 300) return 1 + .5 * (age - (lifeMillis - 300)) / 200;
        return 1;
    }

    public static double rayLengthScale(double ageMillis) { return clamp(ageMillis / 200); }
    public static double rayAlpha(double ageMillis) { return clamp((700 - ageMillis) / 400); }
    // EntityMdRaySmall overrides getWidth: inherited widthWiggle is intentionally unused.
    public static double rayWidthScale(double ageMillis) { return clamp((700 - ageMillis) / 500); }
    public static double markerBob(long absoluteMillis) { return .05 * Math.sin(absoluteMillis / 400.0); }
    public static double particleAlpha(int ageTicks, int lifeTicks, int fadeTicks, int fadeInTicks) {
        if (ageTicks > lifeTicks) return Math.max(0, 1 - (double) (ageTicks - lifeTicks) / fadeTicks);
        if (ageTicks < fadeInTicks) return (double) Math.max(0, ageTicks) / fadeInTicks;
        return 1;
    }
    private static double clamp(double value) { return Math.max(0, Math.min(1, value)); }
}
