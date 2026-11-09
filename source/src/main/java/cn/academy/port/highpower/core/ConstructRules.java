package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

/**
 * Pure rules of V-ADV-01 Vector Construct Manipulation (design sheet 15): budgets, costs, hover control, landing alignment,
 * throw impulse and impact scaling. Physics speeds and impulses are kept separate from the exact combat ledger: the impact
 * scale only multiplies the caster's ledger damage, which the combat code still caps at 10^20.
 */
public final class ConstructRules {
    /** Design test starts: one structure per player, eight per server, 4096 blocks and a 32-block span at most. */
    public static final int ABS_MAX_BLOCKS = 4096, ABS_MAX_SPAN = 32, PER_PLAYER = 1, SERVER_MAX = 8;
    public static final int PREVIEW_TICKS = SkillTuning.integer("vector_construct", "duration_ticks"), THROW_HIT_TICKS = 60, LAND_RETRY_TICKS = 10, DASH_CROUCH_TICKS = 6, DASH_MAX_TICKS = 40, GRAB_EASE_TICKS = 18, SHATTER_MIN_FLIGHT_TICKS = 4;
    /** Horizontal distance up to which a locked structure is grabbed on the spot; farther away the caster dashes to it first. */
    public static final double DASH_REACH = 6, DASH_STAND = 2.2, DASH_ARRIVE = 2.4, DASH_SPEED = 1.3;
    public static final double RANGE = SkillTuning.number("vector_construct", "reach"), MAX_TILT_DEGREES = 20, MIN_IMPACT_SPEED = 3, MASS_REFERENCE = 2000;
    static final double KP = 9, KD = 6, KR = 5;

    private ConstructRules() {}

    /** Skill unlocks at LV4; the block budget grows from 512 at LV4 to 4096 at LV5 (geometric), never above the hard cap. */
    public static int maxBlocks(double level) {
        if (!Double.isFinite(level) || level < 4) return 0;
        double f = Math.min(1, level - 4);
        return (int) Math.min(ABS_MAX_BLOCKS, Math.round(512 * Math.pow(8, f)));
    }

    /** Longest bounding-box side: 16 at LV4, 32 at LV5. */
    public static int maxSpan(double level) {
        if (!Double.isFinite(level) || level < 4) return 0;
        return (int) Math.min(ABS_MAX_SPAN, Math.round(16 + 16 * Math.min(1, level - 4)));
    }

    /** Throw speed in m/s: 18 at LV4, 30 at LV5. */
    public static double throwSpeed(double level) {
        if (!Double.isFinite(level) || level < 4) return 0;
        return 18 + 12 * Math.min(1, level - 4);
    }

    /** Peak control acceleration in m/s^2 (higher level holds heavier structures steadier). */
    public static double maxAccel(double level) {
        if (!Double.isFinite(level) || level < 4) return 0;
        return 25 + 15 * Math.min(1, level - 4);
    }

    /** Hold distance in front of the eyes, so the structure never sits inside the caster. */
    public static double holdDistance(int span) { return Math.max(5, Math.min(32, 4 + Math.max(0, span) * 1.1)); }

    /** Where the main hand is, relative to the eye: a little to the hand's side, ahead and below, from yaw alone (it does not swing with pitch). */
    public static final double HAND_SIDE = .42, HAND_AHEAD = .5, HAND_DOWN = .55, HOLD_ANGLE_DEGREES = 55, HOLD_CLEARANCE = .5, GRIP_HEIGHT_FRACTION = .35, GRIP_GAP = 1.0;

    public static V3 hand(V3 eye, double yawRadians, boolean rightHand) {
        if (eye == null || !eye.finite() || !Double.isFinite(yawRadians)) return eye;
        V3 forward = new V3(-Math.sin(yawRadians), 0, Math.cos(yawRadians)), right = new V3(-Math.cos(yawRadians), 0, -Math.sin(yawRadians));
        return eye.plus(forward.scale(HAND_AHEAD)).plus(right.scale(HAND_SIDE * (rightHand ? 1 : -1))).plus(new V3(0, -HAND_DOWN, 0));
    }

    /**
     * Where the held structure's centre is steered so that it is in the caster's hand rather than in front of the eyes: the hand
     * grips the structure's nearest side, so the centre sits one circumscribed radius from the hand, out toward the hand's side and
     * ahead ({@link #HOLD_ANGLE_DEGREES} off the view), which keeps the crosshair clear. Vertically the hand holds the structure
     * {@link #GRIP_HEIGHT_FRACTION} of its height above its base, and the base never goes below {@link #HOLD_CLEARANCE} above the
     * caster's feet (a base in the terrain drags along it and topples the body).
     */
    public static V3 holdTarget(V3 eye, double yawRadians, boolean rightHand, double feetY, double radius, double height) {
        V3 hand = hand(eye, yawRadians, rightHand);
        if (hand == null || !Double.isFinite(yawRadians) || !Double.isFinite(feetY) || !Double.isFinite(radius) || !Double.isFinite(height) || radius < 0 || height < 0) return eye;
        double side = rightHand ? 1 : -1, a = Math.toRadians(HOLD_ANGLE_DEGREES);
        V3 forward = new V3(-Math.sin(yawRadians), 0, Math.cos(yawRadians)), right = new V3(-Math.cos(yawRadians), 0, -Math.sin(yawRadians));
        V3 out = forward.scale(Math.cos(a)).plus(right.scale(side * Math.sin(a)));
        double r = Math.max(.5, radius) + GRIP_GAP, base = Math.max(feetY + HOLD_CLEARANCE, hand.y() - GRIP_HEIGHT_FRACTION * height);
        return new V3(hand.x() + out.x() * r, base + height / 2, hand.z() + out.z() * r);
    }

    /** Horizontal distance from a point to the footprint of the box {@code min..max} (0 inside it). */
    public static double dashGap(double px, double pz, V3 min, V3 max) {
        if (min == null || max == null || !min.finite() || !max.finite() || !Double.isFinite(px) || !Double.isFinite(pz)) return Double.POSITIVE_INFINITY;
        double dx = Math.max(Math.max(min.x() - px, 0), px - max.x()), dz = Math.max(Math.max(min.z() - pz, 0), pz - max.z());
        return Math.hypot(dx, dz);
    }

    /**
     * Where the caster ends the dash: {@link #DASH_STAND} blocks short of the structure's nearest footprint point, on the line from
     * the caster, at the caster's own height. A caster already inside or within the stand distance stays where they are.
     */
    public static V3 dashStand(V3 player, V3 min, V3 max) {
        if (player == null || !player.finite() || min == null || max == null || !min.finite() || !max.finite()) return player;
        double nx = Math.max(min.x(), Math.min(max.x(), player.x())), nz = Math.max(min.z(), Math.min(max.z(), player.z()));
        double dx = player.x() - nx, dz = player.z() - nz, d = Math.hypot(dx, dz);
        if (d <= DASH_STAND) return player;
        return new V3(nx + dx / d * DASH_STAND, player.y(), nz + dz / d * DASH_STAND);
    }

    /**
     * Where the structure is while it is drawn from where it was seized into the hand, {@code u} being 0..1 progress. Seized
     * structures start right in front of the caster, so a straight path would sweep through them: in the caster's frame the
     * sideways move runs first (0..0.6), the move back toward the caster later (0.4..1) and the vertical one throughout.
     */
    public static V3 drawIn(V3 from, V3 to, V3 player, double yawRadians, double u) {
        if (from == null || to == null || player == null || !from.finite() || !to.finite() || !player.finite() || !Double.isFinite(yawRadians) || !Double.isFinite(u)) return to;
        V3 forward = new V3(-Math.sin(yawRadians), 0, Math.cos(yawRadians)), right = new V3(-Math.cos(yawRadians), 0, -Math.sin(yawRadians));
        V3 a = from.minus(player), b = to.minus(player);
        double pl = ease(u / .6), pf = ease((u - .4) / .6), pv = ease(u / .8);
        double l = a.dot(right) + (b.dot(right) - a.dot(right)) * pl, f = a.dot(forward) + (b.dot(forward) - a.dot(forward)) * pf;
        double y = from.y() + (to.y() - from.y()) * pv;
        return new V3(player.x() + right.x() * l + forward.x() * f, y, player.z() + right.z() * l + forward.z() * f);
    }

    /** Ticks the broken-off pieces take to fly to the target, how long the locking blocks stay, and the most blocks one lock may place. */
    public static final int ENTOMB_GATHER_TICKS = 10, ENTOMB_HOLD_TICKS = 100, ENTOMB_MAX_CELLS = 160, ENTOMB_MAX_ACTIVE_BLOCKS = 2048;
    public static final double ENTOMB_MAX_SIDE = 6;

    /**
     * The one-block-thick shell of cells around a bounding box: every cell of the box grown by one cell on each side, minus the
     * cells the box itself occupies. Empty for a non-finite, inverted or larger-than-{@link #ENTOMB_MAX_SIDE} box. Each entry is
     * {x, y, z} in block coordinates.
     */
    public static java.util.List<int[]> cage(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double[] v = {minX, minY, minZ, maxX, maxY, maxZ};
        for (double d : v) if (!Double.isFinite(d)) return java.util.List.of();
        if (maxX < minX || maxY < minY || maxZ < minZ || maxX - minX > ENTOMB_MAX_SIDE || maxY - minY > ENTOMB_MAX_SIDE || maxZ - minZ > ENTOMB_MAX_SIDE) return java.util.List.of();
        final double eps = 1e-6;
        int x0 = (int) Math.floor(minX), y0 = (int) Math.floor(minY), z0 = (int) Math.floor(minZ);
        int x1 = (int) Math.floor(maxX - eps), y1 = (int) Math.floor(maxY - eps), z1 = (int) Math.floor(maxZ - eps);
        if (x1 < x0) x1 = x0; if (y1 < y0) y1 = y0; if (z1 < z0) z1 = z0;
        java.util.List<int[]> out = new java.util.ArrayList<>();
        for (int x = x0 - 1; x <= x1 + 1; x++) for (int y = y0 - 1; y <= y1 + 1; y++) for (int z = z0 - 1; z <= z1 + 1; z++) {
            boolean inside = x >= x0 && x <= x1 && y >= y0 && y <= y1 && z >= z0 && z <= z1;
            if (!inside) out.add(new int[] {x, y, z});
        }
        return out.size() > ENTOMB_MAX_CELLS ? java.util.List.of() : out;
    }

    /**
     * A thrown body has slammed into something when one tick takes more than 40% of the speed it had the tick before. Gravity or
     * drag never does that, so an arc up to its apex does not count.
     */
    public static boolean slammed(double speed, double previous) {
        return Double.isFinite(speed) && Double.isFinite(previous) && previous >= MIN_IMPACT_SPEED && speed < previous * .6;
    }

    /** Smoothstep of a 0..1 progress, 0 for anything non-finite. */
    public static double ease(double t) {
        if (!Double.isFinite(t)) return 0;
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    public static boolean withinBudget(int blocks, int span, double level) {
        return blocks > 0 && span > 0 && blocks <= maxBlocks(level) && span <= maxSpan(level);
    }

    /** CP to grab: the skill base cost scaled by how much is being lifted. */
    public static double startCost(double base, int blocks) {
        if (!Double.isFinite(base) || base < 0 || blocks <= 0) return Double.NaN;
        return base * (1 + blocks / 64.0);
    }

    /** CP per tick while holding: grows with mass and with how hard the controller is pushing (normalised acceleration). */
    public static double upkeep(double base, double mass, double effort) {
        if (!Double.isFinite(base) || !Double.isFinite(mass) || !Double.isFinite(effort) || base < 0 || mass <= 0) return Double.NaN;
        double m = Math.sqrt(mass / MASS_REFERENCE), e = Math.max(0, Math.min(1, effort));
        return base * .02 * m * (1 + e);
    }

    /**
     * Linear impulse for one physics step: a critically damped spring toward {@code target}, gravity cancelled, acceleration
     * clamped to {@code maxAccel}. Returns {x, y, z, effort} where effort is the clamped acceleration over the cap.
     */
    public static double[] hoverImpulse(V3 pos, V3 vel, V3 target, V3 gravity, double mass, double dt, double maxAccel) {
        if (pos == null || vel == null || target == null || gravity == null || !pos.finite() || !vel.finite() || !target.finite() || !gravity.finite()
                || !Double.isFinite(mass) || mass <= 0 || !Double.isFinite(dt) || dt <= 0 || dt > 1 || !Double.isFinite(maxAccel) || maxAccel <= 0) return new double[] {0, 0, 0, 0};
        V3 acc = target.minus(pos).scale(KP).minus(vel.scale(KD)).minus(gravity);
        double a = acc.length(), effort = Math.min(1, a / maxAccel);
        if (a > maxAccel) acc = acc.scale(maxAccel / a);
        V3 j = acc.scale(mass * dt);
        return new double[] {j.x(), j.y(), j.z(), effort};
    }

    /**
     * Angular impulse for one physics step that damps spin and turns the body upright (tilt axis {@code tiltAxis}, angle
     * {@code tilt}) and toward the target yaw error. Inertia is approximated by a scalar.
     */
    public static V3 uprightImpulse(V3 angularVelocity, V3 tiltAxis, double tilt, double yawError, double inertia, double dt) {
        if (angularVelocity == null || !angularVelocity.finite() || !Double.isFinite(tilt) || !Double.isFinite(yawError) || !Double.isFinite(inertia)
                || inertia <= 0 || !Double.isFinite(dt) || dt <= 0 || dt > 1) return V3.ZERO;
        V3 correction = V3.ZERO;
        if (tiltAxis != null && tiltAxis.finite() && tiltAxis.length() > 1e-9) correction = tiltAxis.unit().scale(-KR * tilt);
        correction = correction.plus(new V3(0, -KR * wrap(yawError), 0));
        V3 alpha = correction.minus(angularVelocity.scale(KD));
        double cap = 12;
        if (alpha.length() > cap) alpha = alpha.scale(cap / alpha.length());
        return alpha.scale(inertia * dt);
    }

    /** Wrap an angle to (-pi, pi]. */
    public static double wrap(double a) {
        if (!Double.isFinite(a)) return 0;
        double r = a % (Math.PI * 2);
        if (r > Math.PI) r -= Math.PI * 2;
        if (r <= -Math.PI) r += Math.PI * 2;
        return r;
    }

    /** Nearest number of counter-clockwise quarter turns (0..3) for a yaw in radians. */
    public static int quarterTurns(double yaw) {
        if (!Double.isFinite(yaw)) return 0;
        return Math.floorMod((int) Math.round(yaw / (Math.PI / 2)), 4);
    }

    public static boolean landable(double tiltRadians) {
        return Double.isFinite(tiltRadians) && Math.abs(tiltRadians) <= Math.toRadians(MAX_TILT_DEGREES);
    }

    /** Never interpret an out-of-ray storage-plot point as a world target. */
    public static V3 throwDirection(V3 center, V3 eye, V3 look, V3 hit, double range) {
        if (center == null || eye == null || look == null || !center.finite() || !eye.finite() || !look.finite()
                || look.length() < 1e-9 || !Double.isFinite(range) || range <= 0 || range > 128) return V3.ZERO;
        V3 forward = look.unit(), aim = eye.plus(forward.scale(range));
        if (hit != null && hit.finite()) {
            V3 delta = hit.minus(eye);
            double along = delta.dot(forward);
            if (along >= 0 && along <= range + .001 && delta.minus(forward.scale(along)).length() <= .01) aim = hit;
        }
        V3 direction = aim.minus(center);
        return direction.length() < 1e-9 ? forward : direction.unit();
    }

    /** Impulse that sets the body's velocity to {@code speed} along {@code look} (bounded so a bad look vector cannot fling it). */
    public static V3 throwImpulse(V3 vel, V3 look, double speed, double mass) {
        if (vel == null || look == null || !vel.finite() || !look.finite() || look.length() < 1e-9 || !Double.isFinite(speed) || speed <= 0
                || !Double.isFinite(mass) || mass <= 0) return V3.ZERO;
        V3 dv = look.unit().scale(Math.min(speed, 40)).minus(vel);
        if (dv.length() > 80) dv = dv.scale(80 / dv.length());
        return dv.scale(mass);
    }

    /** Multiplier on the caster's ledger damage for a thrown-structure hit; 0 below the minimum impact speed. */
    public static double impactScale(double mass, double speed, double throwSpeed) {
        if (!Double.isFinite(mass) || !Double.isFinite(speed) || !Double.isFinite(throwSpeed) || mass <= 0 || throwSpeed <= 0 || speed < MIN_IMPACT_SPEED) return 0;
        double v = Math.min(1.5, speed / throwSpeed), m = Math.max(.1, Math.min(2, Math.sqrt(mass / MASS_REFERENCE)));
        return v * m;
    }
}
