package cn.academy.port.highpower;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/** Pack-only additions. The canonical 50-entry classic catalog and XP denominator stay unchanged. */
public final class HighPowerSkills {
    /** Historical electromaster alias retained for its existing controllers. */
    public static final String CATEGORY = "electromaster";
    public static final String DEFLECTION_STRIKE = "deflection_strike";
    public static final int DEFLECTION_STRIKE_PROPOSAL = 411;
    public static final String SAFE_LANDING_PROBE = "safe_landing_probe";
    public static final int SAFE_LANDING_PROBE_PROPOSAL = 313;
    public static final String DISPLACED_PROJECTION = "displaced_projection";
    public static final int DISPLACED_PROJECTION_PROPOSAL = 311;
    public static final String PHASE_SIDESTEP = "phase_sidestep";
    public static final int PHASE_SIDESTEP_PROPOSAL = 312;
    public static final String RETURN_MARK = "return_mark";
    public static final int RETURN_MARK_PROPOSAL = 322;
    public static final String CROSS_SUBSTITUTION = "cross_substitution";
    public static final int CROSS_SUBSTITUTION_PROPOSAL = 321;
    public static final String RECOVERY_MARK = "recovery_mark";
    public static final int RECOVERY_MARK_PROPOSAL = 323;
    public static final String CORNER_EXCHANGE = "corner_exchange";
    public static final int CORNER_EXCHANGE_PROPOSAL = 333;
    public static final String SHORT_INTERCEPTION = "short_interception";
    public static final int SHORT_INTERCEPTION_PROPOSAL = 331;
    public static final String SPATIAL_PINCER_CORRIDOR = "spatial_pincer_corridor";
    public static final int SPATIAL_PINCER_CORRIDOR_PROPOSAL = 341;
    public static final String MULTIDIMENSIONAL_LANDING_ARRAY = "multidimensional_landing_array";
    public static final int MULTIDIMENSIONAL_LANDING_ARRAY_PROPOSAL = 351;
    public static final String SPATIAL_RELAY = "spatial_relay";
    public static final int SPATIAL_RELAY_PROPOSAL = 343;
    public static final String TACTICAL_COORDINATE_FORMATION = "tactical_coordinate_formation";
    public static final int TACTICAL_COORDINATE_FORMATION_PROPOSAL = 353;
    public static final String EMERGENCY_RETURN = "emergency_return";
    public static final int EMERGENCY_RETURN_PROPOSAL = 342;
    public static final String DUAL_POINT = "dual_point";
    public static final int DUAL_POINT_PROPOSAL = 211;
    public static final String SCATTER_PLATES = "scatter_plates";
    public static final int SCATTER_PLATES_PROPOSAL = 212;
    public static final String BEAM_ETCHING = "beam_etching";
    public static final int BEAM_ETCHING_PROPOSAL = 213;
    public static final String THERMAL_SHELL = "thermal_shell";
    public static final int THERMAL_SHELL_PROPOSAL = 222;
    public static final String FOLDED_MIRROR_ARRAY = "folded_mirror_array";
    public static final int FOLDED_MIRROR_ARRAY_PROPOSAL = 221;
    public static final String DISINTEGRATION_SWEEP = "disintegration_sweep";
    public static final int DISINTEGRATION_SWEEP_PROPOSAL = 231;
    public static final String PARTICLE_LAMINATE = "particle_laminate";
    public static final int PARTICLE_LAMINATE_PROPOSAL = 241;
    public static final String DISSIPATION_CURTAIN = "directional_dissipation_curtain";
    public static final int DISSIPATION_CURTAIN_PROPOSAL = 242;
    public static final String MULTICORE_CANOPY = "multicore_canopy";
    public static final int MULTICORE_CANOPY_PROPOSAL = 251;
    public static final String ZERO_GAP_ARMOR = "zero_gap_armor";
    public static final int ZERO_GAP_ARMOR_PROPOSAL = 252;
    public static final String BEAM_STEP = "beam_step";
    public static final int BEAM_STEP_PROPOSAL = 232;
    public static final String FORKED_ARC = "forked_arc", MAGNETIC_BRACER = "magnetic_bracer", CHARGE_DECOY = "charge_decoy", POLARITY_TETHER = "polarity_tether", CONDUCTIVE_CIRCUIT = "conductive_circuit", GROUNDING_RETURN = "grounding_return", MAGNETIC_GROUND_STEP = "magnetic_ground_step", MAGNETIC_ITEM_PULL = "magnetic_item_pull", IRON_SAND_LANCE = "iron_sand_lance", THUNDER_NET = "thunder_net", POTENTIAL_BUFFER = "potential_buffer", RAIL_THUNDER_ARRAY = "rail_thunder_array", MAGNETIC_DOMAIN = "magnetic_domain";
    /** Authoritative design sheet proposal identifiers, independent of classic ordinals. */
    public static final int FORKED_ARC_PROPOSAL = 111, MAGNETIC_BRACER_PROPOSAL = 112, POLARITY_TETHER_PROPOSAL = 113, CONDUCTIVE_CIRCUIT_PROPOSAL = 121, GROUNDING_RETURN_PROPOSAL = 122, MAGNETIC_GROUND_STEP_PROPOSAL = 132, MAGNETIC_ITEM_PULL_PROPOSAL = 133, IRON_SAND_LANCE_PROPOSAL = 131, THUNDER_NET_PROPOSAL = 141, POTENTIAL_BUFFER_PROPOSAL = 142, RAIL_THUNDER_ARRAY_PROPOSAL = 151, MAGNETIC_DOMAIN_PROPOSAL = 152;
    public static final Set<String> IDS = Set.of(FORKED_ARC, MAGNETIC_BRACER, CHARGE_DECOY, POLARITY_TETHER, CONDUCTIVE_CIRCUIT, GROUNDING_RETURN, MAGNETIC_GROUND_STEP, MAGNETIC_ITEM_PULL, IRON_SAND_LANCE, THUNDER_NET, POTENTIAL_BUFFER, RAIL_THUNDER_ARRAY, MAGNETIC_DOMAIN, DUAL_POINT, SCATTER_PLATES, BEAM_ETCHING, THERMAL_SHELL, FOLDED_MIRROR_ARRAY, DISINTEGRATION_SWEEP, BEAM_STEP, PARTICLE_LAMINATE, DISSIPATION_CURTAIN, MULTICORE_CANOPY, ZERO_GAP_ARMOR, SAFE_LANDING_PROBE, PHASE_SIDESTEP, DISPLACED_PROJECTION, RETURN_MARK, RECOVERY_MARK, CROSS_SUBSTITUTION, CORNER_EXCHANGE, SHORT_INTERCEPTION, SPATIAL_RELAY, EMERGENCY_RETURN, TACTICAL_COORDINATE_FORMATION, SPATIAL_PINCER_CORRIDOR, MULTIDIMENSIONAL_LANDING_ARRAY, DEFLECTION_STRIKE);
    public static final List<SkillCatalog.Skill> ALL = List.of(
            new SkillCatalog.Skill(CATEGORY, FORKED_ARC, 1, true, List.of(), 0),
            new SkillCatalog.Skill(CATEGORY, MAGNETIC_BRACER, 1, true,
                    List.of(new SkillCatalog.Requirement(FORKED_ARC, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, CHARGE_DECOY, 2, true,
                    List.of(new SkillCatalog.Requirement(MAGNETIC_BRACER, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, POLARITY_TETHER, 1, true,
                    List.of(new SkillCatalog.Requirement(FORKED_ARC, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, CONDUCTIVE_CIRCUIT, 2, true,
                    List.of(new SkillCatalog.Requirement(FORKED_ARC, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, GROUNDING_RETURN, 2, true,
                    List.of(new SkillCatalog.Requirement(MAGNETIC_BRACER, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, MAGNETIC_GROUND_STEP, 3, true,
                    List.of(new SkillCatalog.Requirement(POLARITY_TETHER, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, MAGNETIC_ITEM_PULL, 3, true,
                    List.of(new SkillCatalog.Requirement(POLARITY_TETHER, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, IRON_SAND_LANCE, 3, true,
                    List.of(new SkillCatalog.Requirement(POLARITY_TETHER, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, THUNDER_NET, 4, true,
                    List.of(new SkillCatalog.Requirement(CONDUCTIVE_CIRCUIT, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, POTENTIAL_BUFFER, 4, true,
                    List.of(new SkillCatalog.Requirement(GROUNDING_RETURN, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, RAIL_THUNDER_ARRAY, 5, true,
                    List.of(new SkillCatalog.Requirement(THUNDER_NET, 0)), 0),
            new SkillCatalog.Skill(CATEGORY, MAGNETIC_DOMAIN, 5, true,
                    List.of(new SkillCatalog.Requirement(POTENTIAL_BUFFER, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, DUAL_POINT, 1, true, List.of(), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, SCATTER_PLATES, 1, true,
                    List.of(new SkillCatalog.Requirement(DUAL_POINT, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, BEAM_ETCHING, 1, true,
                    List.of(new SkillCatalog.Requirement(DUAL_POINT, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, THERMAL_SHELL, 2, true,
                    List.of(new SkillCatalog.Requirement(SCATTER_PLATES, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, FOLDED_MIRROR_ARRAY, 2, true,
                    List.of(new SkillCatalog.Requirement(DUAL_POINT, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, DISINTEGRATION_SWEEP, 3, true,
                    List.of(new SkillCatalog.Requirement(FOLDED_MIRROR_ARRAY, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, BEAM_STEP, 3, true,
                    List.of(new SkillCatalog.Requirement(THERMAL_SHELL, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, PARTICLE_LAMINATE, 4, true,
                    List.of(new SkillCatalog.Requirement(DISINTEGRATION_SWEEP, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, DISSIPATION_CURTAIN, 4, true,
                    List.of(new SkillCatalog.Requirement(SCATTER_PLATES, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, MULTICORE_CANOPY, 5, true,
                    List.of(new SkillCatalog.Requirement(PARTICLE_LAMINATE, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.MELTDOWNER, ZERO_GAP_ARMOR, 5, true,
                    List.of(new SkillCatalog.Requirement(DISSIPATION_CURTAIN, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, SAFE_LANDING_PROBE, 1, true, List.of(), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, PHASE_SIDESTEP, 1, true,
                    List.of(new SkillCatalog.Requirement(SAFE_LANDING_PROBE, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, DISPLACED_PROJECTION, 1, true,
                    List.of(new SkillCatalog.Requirement(SAFE_LANDING_PROBE, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, RETURN_MARK, 2, true,
                    List.of(new SkillCatalog.Requirement(SAFE_LANDING_PROBE, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, RECOVERY_MARK, 2, true,
                    List.of(new SkillCatalog.Requirement(DISPLACED_PROJECTION, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, CROSS_SUBSTITUTION, 2, true,
                    List.of(new SkillCatalog.Requirement(DISPLACED_PROJECTION, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, CORNER_EXCHANGE, 3, true,
                    List.of(new SkillCatalog.Requirement(RETURN_MARK, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, SHORT_INTERCEPTION, 3, true,
                    List.of(new SkillCatalog.Requirement(CROSS_SUBSTITUTION, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, SPATIAL_RELAY, 4, true,
                    List.of(new SkillCatalog.Requirement(RECOVERY_MARK, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, EMERGENCY_RETURN, 4, true,
                    List.of(new SkillCatalog.Requirement(RETURN_MARK, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, TACTICAL_COORDINATE_FORMATION, 5, true,
                    List.of(new SkillCatalog.Requirement(SPATIAL_RELAY, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, SPATIAL_PINCER_CORRIDOR, 4, true,
                    List.of(new SkillCatalog.Requirement(CROSS_SUBSTITUTION, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.TELEPORTER, MULTIDIMENSIONAL_LANDING_ARRAY, 5, true,
                    List.of(new SkillCatalog.Requirement(SPATIAL_PINCER_CORRIDOR, 0)), 0),
            new SkillCatalog.Skill(HighPowerCategories.VECMANIP, DEFLECTION_STRIKE, 1, true, List.of(), 0));
    private HighPowerSkills() {}
    public static Optional<SkillCatalog.Skill> find(String id) {
        return id == null ? Optional.empty() : ALL.stream().filter(skill -> skill.id().equals(id)).findFirst();
    }
    public static boolean contains(String id) { return find(id).isPresent(); }
    public static boolean contains(String category, String id) {
        return find(id).filter(skill -> skill.category().equals(category)).isPresent();
    }
    public static boolean eligible(AbilityProgress state, String id) {
        return HighPowerCategories.active(state) && find(id).filter(skill -> skill.category().equals(state.category)
                && state.level >= skill.level() && state.canUse(id) && SkillCatalog.enabled(skill.category(), id)).isPresent();
    }
    /** Electromaster compatibility wrapper; shared resources use HighPowerResources.effectiveLevel. */
    public static int effectiveLevel(AbilityProgress state, int ledgerLevel) {
        return state != null && state.category.equals(CATEGORY) && state.level >= 1 && state.level <= 5
                && ledgerLevel >= 1 && ledgerLevel <= 5 ? Math.min(state.level, ledgerLevel) : 0;
    }
}
