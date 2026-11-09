/* AcademyCraft 1.0.7 ThunderBolt numeric/state adaptation. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ClassicRules;

/** Dependency-free classic rules. No world, client, random generator, or game bootstrap. */
public final class ThunderBoltRules {
    public static final String ID = "thunder_bolt";
    public static final int LEVEL = 4;
    public static final double RANGE = SkillTuning.number("thunder_bolt", "reach"), AOE_RANGE = SkillTuning.number("thunder_bolt", "radius");
    public static final double HIT_EXPERIENCE = .005, MISS_EXPERIENCE = .003;
    public static final int DIRECT_SLOW_TICKS = 40, AOE_SLOW_TICKS = 20, SLOW_AMPLIFIER = 3;

    private ThunderBoltRules() {}

    /** getSkillExp is Float in the classic context, before any experience is awarded. */
    public static float capturedExperience(double experience) {
        return (float) ClassicRules.clamp(experience, 0, 1);
    }

    public static ClassicRules.SkillCost cost(double experience) {
        float exp = capturedExperience(experience);
        // CP uses double lerp then an Int cast; the other source formulas use lerpf.
        return new ClassicRules.SkillCost((int) (280 + (double) exp * 140),
                50F + exp * (27F - 50F), 10F + exp * (25F - 10F), RANGE,
                (int) (120F + exp * (50F - 120F)));
    }

    public static float aoeDamage(double experience) {
        return 6F + capturedExperience(experience) * (15F - 6F);
    }

    /** The source compares its Float mastery against a Double .2 literal, strictly. */
    public static boolean rollsSlowdown(double experience) {
        return capturedExperience(experience) > .2;
    }

    public static boolean slowdownSucceeds(double experience, double randomSample) {
        return rollsSlowdown(experience) && Double.isFinite(randomSample)
                && randomSample >= 0 && randomSample < .8;
    }

    /** Sphere membership tests entity feet, with an inclusive radius, after the AABB query. */
    public static boolean withinAoe(double feetDistanceSquared) {
        return Double.isFinite(feetDistanceSquared) && feetDistanceSquared >= 0
                && feetDistanceSquared <= AOE_RANGE * AOE_RANGE;
    }

    public static boolean canUse(AbilityProgress state) {
        return state != null && "electromaster".equals(state.category) && state.level >= LEVEL
                && state.canUse(ID);
    }

    /** The context is immediate-on-keydown; no hold duration or key-up transaction exists. */
    public static Plan prepare(AbilityProgress state, boolean creative) {
        if (!canUse(state)) return null;
        float experience = capturedExperience(state.exp(ID));
        var cost = cost(experience);
        if (!state.consumeSkill(ID,cost.cp(), cost.overload(), creative)) return null;
        return new Plan(experience, cost, aoeDamage(experience));
    }

    /** Hit classification counts selected entities even if their damage event was rejected. */
    public static void complete(AbilityProgress state, Plan plan, boolean hasPrimary, int aoeCount) {
        complete(state,plan,hasPrimary,aoeCount,()->{});
    }
    /** Source addSkillExp -> achievement -> cooldown ordering, without common client linkage. */
    public static void complete(AbilityProgress state,Plan plan,boolean hasPrimary,int aoeCount,Runnable success) {
        if (state == null || plan == null) return;
        state.addExperience(ID, hasPrimary || aoeCount > 0 ? HIT_EXPERIENCE : MISS_EXPERIENCE);
        success.run();
        state.setCooldown(ID, plan.cost.cooldown());
    }

    /**
     * Null-ray source quirk: Motion3D(player) uses normalized velocity, not head aim.
     * A zero/invalid velocity produced NaN in 1.7.10; keep that miss finite at the eye.
     */
    public static Offset missOffset(double vx, double vy, double vz) {
        double length = Math.hypot(Math.hypot(vx, vy), vz);
        if (!(length > 0) || !Double.isFinite(length)) return new Offset(0, 0, 0);
        return new Offset(vx / length * RANGE, vy / length * RANGE, vz / length * RANGE);
    }

    public record Offset(double x, double y, double z) {}
    public record Plan(float experience, ClassicRules.SkillCost cost, float aoeDamage) {}
}
