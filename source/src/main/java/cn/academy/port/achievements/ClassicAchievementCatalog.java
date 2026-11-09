/* AcademyCraft1.0.7 five page declarations and conditions. GPLv3; see NOTICE. */
package cn.academy.port.achievements;
import java.util.*;
import java.util.function.Predicate;
/** Source page order, IDs, integer positions, immediate parent and exact event matching. */
public final class ClassicAchievementCatalog {
    public enum Kind { CRAFT, PICKUP, MATTER, LEVEL, LEARN, MANUAL }
    public record Entry(String id,String page,int x,int y,String icon,String parent,Kind kind,String key,int level) {
        public String title(){return "achievement.ac_"+id;}
        public String description(){return title()+".desc";}
        public String advancementPath(){return id.contains(".")?id.replace('.','/'):"classic/"+id;}
        public String criterion(){return id.equals("electromaster.mine_detect")?"cast":"award";}
    }
    public static final List<String> PAGES=List.of("default","electromaster","meltdowner","teleporter","vecmanip");
    public static final List<Entry> ALL=List.of(
        new Entry("phase_liquid", "default", 0, 0, "item:phase_liquid", null, Kind.MATTER, "phase_liquid", 0),
        new Entry("matrix1", "default", -2, 0, "item:wireless_matrix", "phase_liquid", Kind.CRAFT, "wireless_matrix", 0),
        new Entry("matrix2", "default", -2, -2, "item:matrix_core_0", "matrix1", Kind.CRAFT, "matrix_core", 0),
        new Entry("node", "default", 0, -2, "item:wireless_node_basic", "phase_liquid", Kind.CRAFT, "wireless_node_basic", 0),
        new Entry("developer1", "default", 2, -2, "item:portable_developer", "node", Kind.CRAFT, "portable_developer", 0),
        new Entry("developer2", "default", 4, -2, "item:developer_normal", "developer1", Kind.CRAFT, "developer_normal", 0),
        new Entry("developer3", "default", 4, 0, "item:developer_advanced", "developer2", Kind.CRAFT, "developer_advanced", 0),
        new Entry("phasegen", "default", 0, 2, "item:phase_gen", "phase_liquid", Kind.CRAFT, "phase_gen", 0),
        new Entry("solargen", "default", 2, 2, "item:solar_gen", "phasegen", Kind.CRAFT, "solar_gen", 0),
        new Entry("windgen", "default", 4, 2, "item:windgen_fan", "solargen", Kind.CRAFT, "windgen_main", 0),
        new Entry("crystal", "default", 2, 0, "item:crystal_low", null, Kind.PICKUP, "crystal_low", 0),
        new Entry("terminal", "default", -2, 2, "item:terminal_installer", null, Kind.CRAFT, "terminal_installer", 0),
        new Entry("electromaster.lv1", "electromaster", 0, 0, "texture:abilities/electromaster/skills/charging.png", null, Kind.LEVEL, "electromaster", 1),
        new Entry("electromaster.lv2", "electromaster", 2, 0, "texture:abilities/electromaster/skills/mag_manip.png", "electromaster.lv1", Kind.LEVEL, "electromaster", 2),
        new Entry("electromaster.lv3", "electromaster", 2, 2, "texture:abilities/electromaster/skills/body_intensify.png", "electromaster.lv2", Kind.LEVEL, "electromaster", 3),
        new Entry("electromaster.lv4", "electromaster", -2, 2, "texture:abilities/electromaster/skills/railgun.png", "electromaster.lv3", Kind.LEVEL, "electromaster", 4),
        new Entry("electromaster.lv5", "electromaster", -2, 0, "texture:abilities/electromaster/skills/thunder_clap.png", "electromaster.lv4", Kind.LEVEL, "electromaster", 5),
        new Entry("electromaster.arc_gen", "electromaster", -3, -1, "texture:abilities/electromaster/skills/arc_gen.png", null, Kind.MANUAL, "arc_gen", 0),
        new Entry("electromaster.attack_creeper", "electromaster", -3, 1, "texture:achievements/em_attack_creeper.png", "electromaster.arc_gen", Kind.MANUAL, "attack_creeper", 0),
        new Entry("electromaster.mag_movement", "electromaster", -3, 3, "texture:abilities/electromaster/skills/mag_movement.png", "electromaster.attack_creeper", Kind.MANUAL, "mag_movement", 0),
        new Entry("electromaster.body_intensify", "electromaster", -1, 3, "texture:abilities/electromaster/skills/body_intensify.png", "electromaster.mag_movement", Kind.MANUAL, "body_intensify", 0),
        new Entry("electromaster.mine_detect", "electromaster", 3, 3, "texture:abilities/electromaster/skills/mine_detect.png", "electromaster.body_intensify", Kind.MANUAL, "mine_detect", 0),
        new Entry("electromaster.thunder_bolt", "electromaster", 3, 1, "texture:abilities/electromaster/skills/thunder_bolt.png", "electromaster.mine_detect", Kind.MANUAL, "thunder_bolt", 0),
        new Entry("electromaster.railgun", "electromaster", 3, -1, "texture:abilities/electromaster/skills/railgun.png", "electromaster.thunder_bolt", Kind.MANUAL, "railgun", 0),
        new Entry("electromaster.thunder_clap", "electromaster", 1, -1, "texture:abilities/electromaster/skills/thunder_clap.png", "electromaster.railgun", Kind.MANUAL, "thunder_clap", 0),
        new Entry("meltdowner.lv1", "meltdowner", 0, 0, "texture:abilities/meltdowner/skills/electron_bomb.png", null, Kind.LEVEL, "meltdowner", 1),
        new Entry("meltdowner.lv2", "meltdowner", 2, 0, "texture:abilities/meltdowner/skills/light_shield.png", "meltdowner.lv1", Kind.LEVEL, "meltdowner", 2),
        new Entry("meltdowner.lv3", "meltdowner", 2, 2, "texture:abilities/meltdowner/skills/meltdowner.png", "meltdowner.lv2", Kind.LEVEL, "meltdowner", 3),
        new Entry("meltdowner.lv4", "meltdowner", -2, 2, "texture:abilities/meltdowner/skills/jet_engine.png", "meltdowner.lv3", Kind.LEVEL, "meltdowner", 4),
        new Entry("meltdowner.lv5", "meltdowner", -2, 0, "texture:abilities/meltdowner/skills/electron_missile.png", "meltdowner.lv4", Kind.LEVEL, "meltdowner", 5),
        new Entry("meltdowner.rad_intensify", "meltdowner", -3, -1, "texture:abilities/meltdowner/skills/rad_intensify.png", null, Kind.LEARN, "rad_intensify", 0),
        new Entry("meltdowner.light_shield", "meltdowner", -3, 1, "texture:abilities/meltdowner/skills/light_shield.png", "meltdowner.rad_intensify", Kind.LEARN, "light_shield", 0),
        new Entry("meltdowner.meltdowner", "meltdowner", -3, 3, "texture:abilities/meltdowner/skills/meltdowner.png", "meltdowner.light_shield", Kind.LEARN, "meltdowner", 0),
        new Entry("meltdowner.mine_ray", "meltdowner", -1, 3, "texture:abilities/meltdowner/skills/mine_ray_luck.png", "meltdowner.meltdowner", Kind.LEARN, "mine_ray_basic", 0),
        new Entry("meltdowner.jet_engine", "meltdowner", 1, 3, "texture:abilities/meltdowner/skills/jet_engine.png", "meltdowner.mine_ray", Kind.MANUAL, "jet_engine", 0),
        new Entry("meltdowner.electron_missile", "meltdowner", 3, 3, "texture:abilities/meltdowner/skills/electron_missile.png", "meltdowner.jet_engine", Kind.LEARN, "electron_missile", 0),
        new Entry("teleporter.lv1", "teleporter", 0, 0, "texture:abilities/teleporter/skills/dim_folding_theorem.png", null, Kind.LEVEL, "teleporter", 1),
        new Entry("teleporter.lv2", "teleporter", 2, 0, "texture:abilities/teleporter/skills/penetrate_teleport.png", "teleporter.lv1", Kind.LEVEL, "teleporter", 2),
        new Entry("teleporter.lv3", "teleporter", 2, 2, "texture:abilities/teleporter/skills/location_teleport.png", "teleporter.lv2", Kind.LEVEL, "teleporter", 3),
        new Entry("teleporter.lv4", "teleporter", -2, 2, "texture:abilities/teleporter/skills/space_fluct.png", "teleporter.lv3", Kind.LEVEL, "teleporter", 4),
        new Entry("teleporter.lv5", "teleporter", -2, 0, "texture:abilities/teleporter/skills/flashing.png", "teleporter.lv4", Kind.LEVEL, "teleporter", 5),
        new Entry("teleporter.threatening_teleport", "teleporter", -3, -1, "texture:abilities/teleporter/skills/threatening_teleport.png", null, Kind.MANUAL, "threatening_teleport", 0),
        new Entry("teleporter.critical_attack", "teleporter", -3, 1, "texture:achievements/tp_critical_attack.png", "teleporter.threatening_teleport", Kind.MANUAL, "critical_attack", 0),
        new Entry("teleporter.ignore_barrier", "teleporter", -3, 3, "texture:abilities/teleporter/skills/penetrate_teleport.png", "teleporter.critical_attack", Kind.MANUAL, "ignore_barrier", 0),
        new Entry("teleporter.flashing", "teleporter", -1, 3, "texture:abilities/teleporter/skills/flashing.png", "teleporter.ignore_barrier", Kind.MANUAL, "flashing", 0),
        new Entry("teleporter.mastery", "teleporter", 1, 3, "texture:achievements/tp_mastery.png", "teleporter.flashing", Kind.MANUAL, "mastery", 0),
        new Entry("vecmanip.lv1", "vecmanip", 0, 0, "texture:abilities/vecmanip/skills/dir_shock.png", null, Kind.LEVEL, "vecmanip", 1),
        new Entry("vecmanip.lv2", "vecmanip", 2, 0, "texture:abilities/vecmanip/skills/vec_accel.png", "vecmanip.lv1", Kind.LEVEL, "vecmanip", 2),
        new Entry("vecmanip.lv3", "vecmanip", 2, 2, "texture:abilities/vecmanip/skills/dir_blast.png", "vecmanip.lv2", Kind.LEVEL, "vecmanip", 3),
        new Entry("vecmanip.lv4", "vecmanip", -2, 2, "texture:abilities/vecmanip/skills/blood_retro.png", "vecmanip.lv3", Kind.LEVEL, "vecmanip", 4),
        new Entry("vecmanip.lv5", "vecmanip", -2, 0, "texture:abilities/vecmanip/skills/plasma_cannon.png", "vecmanip.lv4", Kind.LEVEL, "vecmanip", 5),
        new Entry("vecmanip.ground_shock", "vecmanip", -3, -1, "texture:abilities/vecmanip/skills/ground_shock.png", null, Kind.MANUAL, "ground_shock", 0),
        new Entry("vecmanip.dir_blast", "vecmanip", -3, 1, "texture:abilities/vecmanip/skills/dir_blast.png", "vecmanip.ground_shock", Kind.MANUAL, "dir_blast", 0),
        new Entry("vecmanip.storm_wing", "vecmanip", -3, 3, "texture:abilities/vecmanip/skills/storm_wing.png", "vecmanip.dir_blast", Kind.MANUAL, "storm_wing", 0),
        new Entry("vecmanip.blood_retro", "vecmanip", -1, 3, "texture:abilities/vecmanip/skills/blood_retro.png", "vecmanip.storm_wing", Kind.MANUAL, "blood_retro", 0),
        new Entry("vecmanip.vec_reflection", "vecmanip", 1, 3, "texture:abilities/vecmanip/skills/vec_reflection.png", "vecmanip.blood_retro", Kind.MANUAL, "vec_reflection", 0));
    private static final Map<String,Entry> INDEX;
    static {var map=new LinkedHashMap<String,Entry>();for(var e:ALL)if(map.put(e.id(),e)!=null)throw new IllegalStateException(e.id());INDEX=Map.copyOf(map);for(var e:ALL)if(e.parent()!=null&&!INDEX.containsKey(e.parent()))throw new IllegalStateException(e.parent());}
    private ClassicAchievementCatalog(){}
    public static Entry get(String id){return id==null?null:INDEX.get(id);}
    public static List<Entry> page(String page){return ALL.stream().filter(e->e.page().equals(page)).toList();}
    public static boolean canAward(Entry entry,Predicate<String> earned){return entry!=null&&!earned.test(entry.id())&&(entry.parent()==null||earned.test(entry.parent()));}
    public static List<Entry> matching(Kind kind,String key,int value){return ALL.stream().filter(e->e.kind()==kind&&e.key().equals(key)&&(kind!=Kind.LEVEL||e.level()==value)).toList();}
    /** The source single matrix-core Item accepts every metadata. Modern variants retain that family. */
    public static String craftKey(String registry){String id=registry.startsWith("academy:")?registry.substring(8):"";return Set.of("matrix_core_0","matrix_core_1","matrix_core_2").contains(id)?"matrix_core":id;}
    public static String pageTitle(String page){return page.equals("default")?"AcademyCraft":"ac.achievementpage.cat_"+page;}
    /** Original tooltip/icon visibility is bounded to four unearned ancestors. */
    public static int distance(Entry e,Predicate<String> earned){int n=0;while(e!=null&&!earned.test(e.id())){n++;e=get(e.parent());}return n;}
}
