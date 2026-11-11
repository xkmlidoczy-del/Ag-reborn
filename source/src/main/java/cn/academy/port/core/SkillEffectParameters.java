package cn.academy.port.core;

import cn.academy.port.macro.MacroSkillRules;
import java.util.Set;

/** Effect parameters for descriptions. Only controllers using mastery-scaled areas are listed here. */
public final class SkillEffectParameters {
    private static final Set<String> GROWING_AREAS = Set.of(
            "mental_out_crowd", "mental_crossfire", "mental_crowd_detach", "mental_conflict_ring", "mental_relay_interference",
            "mental_resonance_cascade", "float_field", "float_hover_field", "float_rescue_field", "float_item_field",
            "float_item_shelf_field", "float_collapse_field", "hydro_domain", "hydro_eddy", "hydro_brake_field",
            "paper_array", "liquid_shadow_field", "dark_shard_ring", "nitrogen_shock_ring", "aero_squall");
    private SkillEffectParameters() {}
    public static boolean growsWithMastery(String skill, String parameter) {
        var fields = SkillTuning.fields(skill);
        return fields.containsKey(parameter + "_novice") && fields.containsKey(parameter + "_mastered")
                || parameter.equals("radius") && GROWING_AREAS.contains(skill);
    }
    public static double current(String skill, String parameter, double mastery) {
        double progress = Double.isFinite(mastery) ? Math.clamp(mastery, 0, 1) : 0;
        var fields = SkillTuning.fields(skill);
        var first = fields.get(parameter + "_novice");
        var last = fields.get(parameter + "_mastered");
        if (first != null && last != null) return first.doubleValue() + (last.doubleValue() - first.doubleValue()) * progress;
        double maximum = SkillTuning.number(skill, parameter);
        return parameter.equals("radius") && GROWING_AREAS.contains(skill) ? MacroSkillRules.area(skill, progress, maximum) : maximum;
    }
}
