package cn.academy.port.preset;

import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import java.util.List;
import java.util.Set;

/** Deliberately bounded to implemented active gameplay, rather than all classic metadata. */
public final class PresetSkills {
    public static final Set<String> IMPLEMENTED = Set.of("arc_gen", "charging", "railgun", "electron_bomb", "threatening_teleport", "dir_shock", "ground_shock", "dir_blast", "storm_wing", "blood_retro", "vec_accel", "vec_deviation", "vec_reflection", "plasma_cannon", "mag_movement", "mag_manip", "thunder_bolt", "mine_detect", "body_intensify", "thunder_clap", "scatter_bomb", "light_shield", "meltdowner", "mine_ray_basic", "mine_ray_expert", "mine_ray_luck", "ray_barrage", "jet_engine", "electron_missile", "penetrate_teleport", "mark_teleport", "flesh_ripping", "location_teleport", "shift_tp", "flashing");
    public static final Set<String> REGISTERED = java.util.stream.Stream.concat(IMPLEMENTED.stream(), cn.academy.port.highpower.HighPowerSkills.IDS.stream()).collect(java.util.stream.Collectors.toUnmodifiableSet());
    private PresetSkills() {}
    public static boolean selectable(AbilityProgress state, String id) {
        return mappedUsable(state,id)&&SkillCatalog.enabled(state.category,id);
    }
    /**
     * Pack-only migration seam: disabling unaccepted NEW132 must not freeze unrelated slot edits.
     * The requested mapping is always validated normally. Only an unchanged, already-mapped NEW132
     * may survive its disabled flag; learned/level/category checks and every other slot rule remain.
     */
    public static boolean edit(AbilityProgress state,int preset,int slot,String requested) {
        if(state==null||!cn.academy.port.core.SkillPresets.validPreset(preset)
                ||!cn.academy.port.core.SkillPresets.validSlot(slot)||requested==null
                ||!requested.isEmpty()&&!selectable(state,requested))return false;
        String groundStep=cn.academy.port.highpower.HighPowerSkills.MAGNETIC_GROUND_STEP;
        boolean retainDisabledGroundStep=!requested.equals(groundStep)&&state.presets.contains(preset,groundStep)
                &&mappedUsable(state,groundStep)&&!SkillCatalog.enabled(state.category,groundStep);
        return state.presets.edit(preset,slot,requested,id->selectable(state,id)
                ||retainDisabledGroundStep&&id.equals(groundStep));
    }
    /** Original persisted raw mappings survive unlearning/level changes; cast ingress still validates eligibility. */
    public static boolean registeredMapping(AbilityProgress state,String id){return state!=null&&id!=null&&state.hasCategory()&&(REGISTERED.contains(id)||cn.academy.port.canon.CanonSkills.findKnown(state.category,id).isPresent())&&SkillCatalog.find(state.category,id).filter(SkillCatalog.Skill::controllable).isPresent();}
    /** Original ClientRuntime restores old mappings without rechecking the live canControl getter. */
    public static boolean mappedUsable(AbilityProgress state, String id) {
        return state != null && id != null && state.hasCategory() && REGISTERED.contains(id) && state.learned(id)
                && SkillCatalog.find(state.category, id).filter(skill -> skill.controllable() && state.level >= skill.level()).isPresent();
    }
    public static List<SkillCatalog.Skill> available(AbilityProgress state, int preset) {
        return SkillCatalog.REGISTERED.stream().filter(skill -> selectable(state, skill.id())
                && skill.category().equals(state.category) && !state.presets.contains(preset, skill.id())).toList();
    }
}
