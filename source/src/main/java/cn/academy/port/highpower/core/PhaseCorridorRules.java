package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

/** NEW352 phase corridor: chained safe jumps with 0.1 s of trajectory evasion followed by 0.15 s of exposure. */
public final class PhaseCorridorRules {
    public static final int MAX_ANCHORS = 6, EVASION_TICKS = 2, EXPOSED_TICKS = 3, MIN_INTERVAL = EVASION_TICKS + EXPOSED_TICKS + 1;
    public static final double JUMP_RANGE = SkillTuning.number("phase_corridor", "base_distance"), MIN_JUMP = 2;
    private PhaseCorridorRules() {}

    /** True during the 0.1 s that follow a jump; {@code ticksSinceJump} is negative before any jump. */
    public static boolean evasion(int ticksSinceJump) { return ticksSinceJump >= 0 && ticksSinceJump < EVASION_TICKS; }
    /** True for the 0.15 s after the evasion, when the body is solid and can be hit. */
    public static boolean exposed(int ticksSinceJump) { return ticksSinceJump >= EVASION_TICKS && ticksSinceJump < EVASION_TICKS + EXPOSED_TICKS; }
    /** A new jump waits until evasion and exposure have both finished, so cover can never be continuous. */
    public static boolean canJump(int ticksSinceJump) { return ticksSinceJump >= MIN_INTERVAL; }
    /** A jump must actually move the caster: at least {@link #MIN_JUMP} m and at most {@link #JUMP_RANGE} m. */
    public static boolean validJump(double distance) { return Double.isFinite(distance) && distance >= MIN_JUMP && distance <= JUMP_RANGE; }
    /** Same as {@link #validJump(double)} with the level-scaled maximum range. */
    public static boolean validJump(double distance, double range) { return Double.isFinite(distance) && Double.isFinite(range) && distance >= MIN_JUMP && distance <= range; }
    public static boolean validAnchorCount(int count) { return count >= 0 && count <= MAX_ANCHORS; }
}
