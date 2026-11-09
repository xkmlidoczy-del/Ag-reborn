package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

/** Pure NEW-113 safety policy. Motion/CP values are conservative prototype choices, not workbook values. */
public final class PolarityTetherRules {
    public static final double RANGE = SkillTuning.number("polarity_tether", "reach"), ACCELERATION = .06, MAX_SPEED = SkillTuning.number("polarity_tether", "max_speed"), MAX_GRAVITY = .08, MAX_STEP_CHANGE = .14, BRAKE = .18, ARRIVAL = .8;
    public static final int MAX_TICKS = SkillTuning.integer("polarity_tether", "duration_ticks"), MIN_COMMIT_TICKS = 1, MAX_CONTROLLERS = 1, MAX_PARTICLES = 64;
    private static final double[] COST = {0, 4, 2, 1, .5, .1};
    public enum Phase { PENDING, ANCHORED, PULLING, BRAKING, ENDED }
    public record Vector(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double length() { return Math.sqrt(x*x+y*y+z*z); }
        public Vector scale(double scale) { return new Vector(x*scale,y*scale,z*scale); }
        public Vector plus(Vector v) { return new Vector(x+v.x,y+v.y,z+v.z); }
    }
    private PolarityTetherRules() {}
    /** Position runs from player (0) to anchor (1), so decreasing progress flows inward. */
    public static double inwardDotProgress(double ticks, int index) {
        double progress = index / 4.0 - ticks * .035;
        return progress - Math.floor(progress);
    }
    public static double cpCost(int level) { return level >= 1 && level <= 5 ? COST[level] : Double.NaN; }
    public static boolean canCommit(long started, long tick) { return tick >= started && tick-started >= MIN_COMMIT_TICKS && tick-started < MAX_TICKS; }
    public static boolean expired(long started, long tick) { return tick < started || tick-started >= MAX_TICKS; }
    public static Phase tap(Phase phase, boolean crouching) {
        if (phase == null || phase == Phase.ENDED) return Phase.ENDED;
        if (crouching) return phase == Phase.PENDING || phase == Phase.ANCHORED ? Phase.ENDED : Phase.BRAKING;
        return switch (phase) {
            case PENDING -> Phase.PENDING;
            case ANCHORED -> Phase.PULLING;
            case PULLING, BRAKING -> Phase.BRAKING;
            case ENDED -> Phase.ENDED;
        };
    }
    /** Null means retire without changing true incoming inertia. Never clamps an already excessive velocity. */
    public static Vector pull(Vector velocity, Vector towardAnchor) { return pull(velocity,towardAnchor,0); }
    public static Vector pull(Vector velocity, Vector towardAnchor, double gravity) {
        if (!Double.isFinite(gravity) || gravity < 0 || gravity > MAX_GRAVITY || !admissible(velocity) || towardAnchor == null || !towardAnchor.finite()) return null;
        double distance = towardAnchor.length();
        if (!Double.isFinite(distance) || distance <= ARRIVAL || distance > RANGE) return null;
        Vector next = velocity.plus(towardAnchor.scale(ACCELERATION / distance)).plus(new Vector(0,gravity,0));
        double speed = next.length();
        return speed > MAX_SPEED ? next.scale(MAX_SPEED/speed) : next;
    }
    /** Deliberate emergency braking is distinct from any invalidation, which never calls this method. */
    public static Vector brake(Vector velocity) {
        if (!admissible(velocity)) return null;
        double speed = velocity.length();
        return speed <= BRAKE ? new Vector(0,0,0) : velocity.scale((speed-BRAKE)/speed);
    }
    public static boolean admissible(Vector velocity) {
        return velocity != null && velocity.finite() && velocity.length() <= MAX_SPEED + 1e-10;
    }
}
