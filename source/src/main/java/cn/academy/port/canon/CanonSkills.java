package cn.academy.port.canon;

import cn.academy.port.SkillCatalog;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Released canon catalog. Only skills with concrete controllers belong here. */
public final class CanonSkills {
    public static final boolean EXPERIMENTAL = Boolean.parseBoolean(System.getProperty("academy.canon.experimental", "true"));
    public static final String MENTAL = "mental_out", AERO = "aero_hand", FLOAT = "float_dial", NITROGEN = "nitrogen_armor", DARK = "dark_matter", HYDRO = "hydro_hand", PAPER = "paper_psychokinesis", LIQUID = "liquid_shadow";
    private static final List<SkillCatalog.Skill> BASE = List.of(
            skill(MENTAL, "mental_probe", 1, ""),
            skill(MENTAL, "mental_disrupt", 3, "mental_probe"),
            skill(MENTAL, "mental_out_crowd", 5, "mental_disrupt"),
            skill(AERO, "aero_jet", 1, ""),
            skill(AERO, "aero_launch", 3, "aero_jet"),
            skill(AERO, "aero_barrage", 5, "aero_launch"),
            skill(NITROGEN, "nitrogen_armor", 1, ""),
            skill(NITROGEN, "nitrogen_punch", 3, "nitrogen_armor"),
            skill(NITROGEN, "nitrogen_dome", 5, "nitrogen_punch"),
            skill(DARK, "dark_blade", 1, ""),
            skill(DARK, "dark_wings", 3, "dark_blade"),
            skill(DARK, "dark_construct", 5, "dark_wings"),
            skill(HYDRO, "hydro_whip", 1, ""),
            skill(HYDRO, "hydro_shield", 3, "hydro_whip"),
            skill(HYDRO, "hydro_domain", 5, "hydro_shield"),
            skill(PAPER, "paper_blade", 1, ""),
            skill(PAPER, "paper_armor", 3, "paper_blade"),
            skill(PAPER, "paper_wing", 3, "paper_blade"),
            skill(PAPER, "paper_array", 5, "paper_armor"),
            skill(LIQUID, "liquid_scout", 1, ""),
            skill(LIQUID, "liquid_strike", 3, "liquid_scout"),
            skill(LIQUID, "liquid_shadow_field", 5, "liquid_strike"),
            skill(FLOAT, "float_lift", 1, ""),
            skill(FLOAT, "float_rescue", 3, "float_lift"),
            skill(FLOAT, "float_field", 5, "float_rescue"));
    private static final List<SkillCatalog.Skill> KNOWN = catalog();
    private static List<SkillCatalog.Skill> catalog() {
        var result = new java.util.ArrayList<SkillCatalog.Skill>(BASE);
        result.addAll(CanonExpansionA.skills()); result.addAll(CanonExpansionB.skills());
        result.addAll(CanonWaterExpansion.skills());result.addAll(CanonFloatExpansion.skills());
        result.addAll(CanonLiquidCombatExpansion.skills());
        result.addAll(CanonOffenseSkills.skills());
        var ids = new java.util.HashSet<String>();
        for (var skill : result) if (!ids.add(skill.id())) throw new IllegalStateException("Duplicate canon skill " + skill.id());
        return List.copyOf(result);
    }
    public static final List<SkillCatalog.Skill> ALL = EXPERIMENTAL ? KNOWN : List.of();
    public static final List<String> CATEGORIES = EXPERIMENTAL ? List.of(MENTAL, AERO, FLOAT, NITROGEN, DARK, HYDRO, PAPER, LIQUID) : List.of();
    public static final Set<String> IDS = ALL.stream().map(SkillCatalog.Skill::id)
            .collect(java.util.stream.Collectors.toUnmodifiableSet());
    private CanonSkills() {}
    private static SkillCatalog.Skill skill(String category, String id, int level, String parent) {
        return new SkillCatalog.Skill(category, id, level, true,
                parent.isEmpty() ? List.of() : List.of(new SkillCatalog.Requirement(parent, 0)), 0);
    }
    /** Known identities remain readable even if the launch flag is subsequently absent. */
    public static Optional<SkillCatalog.Skill> findKnown(String category, String id) {
        return KNOWN.stream().filter(s -> s.category().equals(category) && s.id().equals(id)).findFirst();
    }
    public static Optional<SkillCatalog.Skill> find(String id) {
        return ALL.stream().filter(s -> s.id().equals(id)).findFirst();
    }
    public static boolean knownCategory(String category) {
        return MENTAL.equals(category) || AERO.equals(category) || FLOAT.equals(category) || NITROGEN.equals(category) || DARK.equals(category) || HYDRO.equals(category) || PAPER.equals(category) || LIQUID.equals(category);
    }
    public static boolean activeCategory(String category) { return EXPERIMENTAL && knownCategory(category); }
    public static boolean knownSkill(String id) { return KNOWN.stream().anyMatch(s -> s.id().equals(id)); }
    public static String root(String category) {
        return MENTAL.equals(category) ? "mental_probe" : AERO.equals(category) ? "aero_jet"
                : FLOAT.equals(category) ? "float_lift" : NITROGEN.equals(category) ? "nitrogen_armor" : DARK.equals(category) ? "dark_blade"
                : HYDRO.equals(category) ? "hydro_whip" : PAPER.equals(category) ? "paper_blade" : LIQUID.equals(category) ? "liquid_scout" : null;
    }
}
