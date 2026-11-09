package cn.academy.port.core;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Shared aiming ranges. Other distances in the tuning table can have different origins or semantics. */
public final class RangedSkillRanges {
    public record Reach(double novice, double mastered) {
        public Reach {
            if (!Double.isFinite(novice) || !Double.isFinite(mastered) || novice <= 0 || mastered <= 0) {
                throw new IllegalArgumentException("Aiming reach must be finite and positive");
            }
        }

        public double at(double mastery) {
            double progress = Double.isFinite(mastery) ? Math.max(0, Math.min(1, mastery)) : 0;
            return novice + (mastered - novice) * progress;
        }
    }

    public static final double CANON_TARGET = 24, MENTAL_PRIMARY = 32, MENTAL_REMOTE = 32;
    /** A medium may begin beside the caster; its visual span can exceed the target's eye distance. */
    public static final double MAX_CANON_VISUAL_SPAN = 64;
    public static final double MAX_CANON_AREA_RADIUS = 32;
    public static final Set<String> REMOTE_SCANS = Set.of(
            "mental_life_scan", "mental_hostile_scan", "mental_intent_scan", "mental_target_sense");

    private static final Map<String, Reach> VALUES = load();

    private RangedSkillRanges() {}

    private static Map<String, Reach> load() {
        Map<String, Reach> ranges = new LinkedHashMap<>();
        ranges.put("arc_gen", new Reach(SkillTuning.number("arc_gen", "reach_novice"),
                SkillTuning.number("arc_gen", "reach_mastered")));
        for (String skill : new String[]{
                "railgun", "mental_probe", "mental_disrupt", "mental_out_crowd",
                "mental_life_scan", "mental_hostile_scan", "mental_intent_scan", "mental_target_sense",
                "mental_attention_shift", "mental_focus_break", "mental_route_interrupt", "mental_disengage",
                "mental_interpose", "mental_redirect", "mental_conflict", "mental_suppress_pair",
                "mental_lure_pair", "mental_puppet", "dark_shard", "dark_spear_fan", "dark_matter_barrage",
                "aero_barrage", "dark_pierce", "aero_pressure_lance", "paper_blade", "dark_spread", "aero_cone",
                "aero_ground_blast", "aero_gust", "nitrogen_cone", "hydro_whip", "float_lift", "float_rescue",
                "float_ally_hover", "float_ally_ascent", "float_ally_descent", "float_suspend",
                "float_hostile_descent", "float_item_lift", "float_item_lower", "float_item_release",
                "float_item_pull", "float_item_push", "float_item_shelf"}) {
            double value = SkillTuning.number(skill, "reach");
            ranges.put(skill, new Reach(value, value));
        }
        return Map.copyOf(ranges);
    }

    public static Map<String, Reach> all() {
        return VALUES;
    }

    public static Reach find(String skill) {
        return VALUES.get(skill);
    }

    public static double fixed(String skill) {
        var range = find(skill);
        if (range == null || range.novice() != range.mastered()) {
            throw new IllegalArgumentException("No fixed reach for " + skill);
        }
        return range.novice();
    }

    public static double at(String skill, double mastery, double fallback) {
        var range = find(skill);
        return range == null ? fallback : range.at(mastery);
    }

    public static double withMentalRemote(String skill) {
        var boosted = SkillTuning.fields(skill).get("remote_reach");
        return boosted == null ? MENTAL_REMOTE : boosted.doubleValue();
    }
}
