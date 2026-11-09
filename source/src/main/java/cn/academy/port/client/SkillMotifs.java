package cn.academy.port.client;

import cn.academy.port.client.MotifKit.B;
import cn.academy.port.client.MotifKit.M;
import java.util.Map;
import java.util.Set;

/**
 * Per-skill visual identity. Every skill with an entry here is drawn by its own choreography ({@link EmMotifs}, {@link MdMotifs},
 * {@link TpMotifs}, {@link VmMotifs}) and carries no shared category signature; the category only picks the colours. Skills that already
 * have a full effect of their own (listed in {@link #OWN}) get nothing added and no signature either.
 */
final class SkillMotifs {
    private SkillMotifs() {}

    interface Motif { void draw(B b, M m); }

    private static final Map<String, Motif> MOTIFS = Map.ofEntries(
            // electromaster
            Map.entry("arc_gen", EmMotifs::arcGen), Map.entry("forked_arc", EmMotifs::forkedArc), Map.entry("charging", EmMotifs::charging),
            Map.entry("magnetic_bracer", EmMotifs::magneticBracer), Map.entry("mag_movement", EmMotifs::magMovement), Map.entry("mag_manip", EmMotifs::magManip),
            Map.entry("mine_detect", EmMotifs::mineDetect), Map.entry("body_intensify", EmMotifs::bodyIntensify), Map.entry("thunder_bolt", EmMotifs::thunderBolt),
            Map.entry("charge_decoy", EmMotifs::chargeDecoy), Map.entry("polarity_tether", EmMotifs::polarityTether), Map.entry("conductive_circuit", EmMotifs::conductiveCircuit),
            Map.entry("grounding_return", EmMotifs::groundingReturn), Map.entry("magnetic_ground_step", EmMotifs::magneticGroundStep),
            Map.entry("magnetic_item_pull", EmMotifs::magneticItemPull), Map.entry("iron_sand_lance", EmMotifs::ironSandLance), Map.entry("thunder_net", EmMotifs::thunderNet),
            Map.entry("potential_buffer", EmMotifs::potentialBuffer), Map.entry("pulse_shield", EmMotifs::pulseShield), Map.entry("magnetic_domain", EmMotifs::magneticDomain),
            Map.entry("rail_relay", EmMotifs::railRelay),
            // meltdowner
            Map.entry("electron_bomb", MdMotifs::electronBomb), Map.entry("light_shield", MdMotifs::lightShield), Map.entry("meltdowner", MdMotifs::meltdowner),
            Map.entry("mine_ray_basic", (b, m) -> MdMotifs.mineRay(b, m, 0)), Map.entry("mine_ray_expert", (b, m) -> MdMotifs.mineRay(b, m, 1)),
            Map.entry("mine_ray_luck", (b, m) -> MdMotifs.mineRay(b, m, 2)), Map.entry("ray_barrage", MdMotifs::rayBarrage), Map.entry("jet_engine", MdMotifs::jetEngine),
            Map.entry("electron_missile", MdMotifs::electronMissile), Map.entry("dual_point", MdMotifs::dualPoint), Map.entry("scatter_plates", MdMotifs::scatterPlates),
            Map.entry("beam_etching", MdMotifs::beamEtching), Map.entry("folded_mirror_array", MdMotifs::foldedMirrorArray), Map.entry("beam_step", MdMotifs::beamStep),
            Map.entry("particle_laminate", MdMotifs::particleLaminate), Map.entry("directional_dissipation_curtain", MdMotifs::dissipationCurtain),
            Map.entry("zero_gap_armor", MdMotifs::zeroGapArmor), Map.entry("point_meltdown", MdMotifs::pointMeltdown),
            Map.entry("remote_scatter_relay", MdMotifs::remoteScatterRelay), Map.entry("scatter_locator_plate", MdMotifs::scatterLocatorPlate),
            Map.entry("reverse_jet_brake", MdMotifs::reverseJetBrake),
            // teleporter
            Map.entry("threatening_teleport", TpMotifs::threateningTeleport), Map.entry("penetrate_teleport", TpMotifs::penetrateTeleport),
            Map.entry("mark_teleport", TpMotifs::markTeleport), Map.entry("flesh_ripping", TpMotifs::fleshRipping), Map.entry("location_teleport", TpMotifs::locationTeleport),
            Map.entry("shift_tp", TpMotifs::shiftTp), Map.entry("space_fluct", TpMotifs::spaceFluct), Map.entry("flashing", TpMotifs::flashing),
            Map.entry("safe_landing_probe", TpMotifs::safeLandingProbe), Map.entry("phase_sidestep", TpMotifs::phaseSidestep),
            Map.entry("displaced_projection", TpMotifs::displacedProjection), Map.entry("return_mark", TpMotifs::returnMark), Map.entry("recovery_mark", TpMotifs::recoveryMark),
            Map.entry("cross_substitution", TpMotifs::crossSubstitution), Map.entry("corner_exchange", TpMotifs::cornerExchange),
            Map.entry("short_interception", TpMotifs::shortInterception), Map.entry("spatial_relay", TpMotifs::spatialRelay), Map.entry("emergency_return", TpMotifs::emergencyReturn),
            Map.entry("tactical_coordinate_formation", TpMotifs::tacticalCoordinateFormation), Map.entry("spatial_pincer_corridor", TpMotifs::spatialPincerCorridor),
            Map.entry("incident_bypass", TpMotifs::incidentBypass), Map.entry("phase_corridor", TpMotifs::phaseCorridor),
            // vector manipulation
            Map.entry("dir_shock", VmMotifs::dirShock), Map.entry("ground_shock", VmMotifs::groundShock), Map.entry("vec_accel", VmMotifs::vecAccel),
            Map.entry("vec_deviation", VmMotifs::vecDeviation), Map.entry("dir_blast", VmMotifs::dirBlast), Map.entry("blood_retro", VmMotifs::bloodRetro),
            Map.entry("vec_reflection", VmMotifs::vecReflection), Map.entry("deflection_strike", VmMotifs::deflectionStrike), Map.entry("vector_parry", VmMotifs::vectorParry),
            Map.entry("braking_touch", VmMotifs::brakingTouch), Map.entry("inertial_anchor", VmMotifs::inertialAnchor), Map.entry("yaw_palm", VmMotifs::yawPalm),
            Map.entry("circulation_relief", VmMotifs::circulationRelief), Map.entry("circulation_listening", VmMotifs::circulationListening),
            Map.entry("autonomous_vector_circle", VmMotifs::autonomousVectorCircle), Map.entry("impulse_split", VmMotifs::impulseSplit));

    /** Skills whose own renderer already draws the whole effect (rail, clap, cannon, cage, canopy...). Nothing is added to them. */
    private static final Set<String> OWN = Set.of("railgun", "thunder_clap", "rail_thunder_array", "scatter_bomb", "thermal_shell", "disintegration_sweep",
            "multicore_canopy", "shear_windblade", "storm_wing", "trajectory_return", "vector_cage", "multi_axis_reflection", "wind_pressure_lift",
            "zenith_vector_dominion", "plasma_cannon", "vector_construct", "multidimensional_landing_array");

    static boolean has(String skill) { return skill != null && MOTIFS.containsKey(skill); }
    static boolean own(String skill) { return skill != null && OWN.contains(skill); }
    /** True when the shared category signature and the generic level cues must not be drawn for this skill. */
    static boolean replaces(String skill) { return has(skill) || own(skill); }

    /** How far from the eye the aim point is searched, in blocks. */
    static double range(String skill) {
        if(skill==null)return 28;
        var range=cn.academy.port.core.RangedSkillRanges.find(skill);if(range!=null)return range.at(AcademyClient.state.exp(skill));
        return switch(skill){
            case "mine_ray_basic" -> cn.academy.port.skill.MineRayBasicSession.RANGE;
            case "mine_ray_expert","mine_ray_luck" -> cn.academy.port.core.SkillTuning.number(skill, "reach");
            case "dir_shock" -> cn.academy.port.skill.DirectedShock.RANGE;
            case "dir_blast" -> cn.academy.port.skill.DirectedBlastwave.RANGE;
            case "blood_retro" -> cn.academy.port.skill.BloodRetrograde.RANGE;
            case "electron_bomb" -> cn.academy.port.skill.ElectronBomb.RANGE;
            case "ray_barrage" -> cn.academy.port.skill.RayBarrageRules.RANGE;
            case "thunder_bolt" -> cn.academy.port.skill.ThunderBoltRules.RANGE;
            default -> 28;
        };
    }

    static void draw(B b, M m) {
        Motif motif = MOTIFS.get(m.skill());
        if (motif != null) motif.draw(b, m);
    }
}
