package cn.academy.port.tutorial;

import java.util.List;

/** AcademyCraft 1.0.7 ModuleTutorial declarations, in their original page order. */
public final class ClassicTutorials {
    public record Preview(String kind, String target, int metadata) {}
    public record Page(String id, List<String> obtainedItems, List<Preview> previews) {
        public Page { obtainedItems=List.copyOf(obtainedItems); previews=List.copyOf(previews); }
        public boolean defaultInstalled() { return obtainedItems.isEmpty(); }
    }
    private static String ac(String path) { return "academy:"+path; }
    private static Preview recipe(String path) { return new Preview("recipe",ac(path),0); }
    private static Preview block(String path) { return new Preview("block",ac(path),0); }
    private static Page page(String id, List<String> targets, Preview... previews) {
        return new Page(id,targets.stream().map(ClassicTutorials::ac).toList(),List.of(previews));
    }
    private static final List<Page> PAGES=List.of(
        page("welcome",List.of()),
        page("ores",List.of("constraint_metal_ore","imag_silicon_ore","crystal_ore","reso_crystal_ore"),
            block("constraint_metal_ore"),block("imag_silicon_ore"),block("crystal_ore"),block("reso_crystal_ore"),
            new Preview("icon","academy:textures/items/matter_unit/phase_liquid_mat.png",0),
            recipe("constraint_plate"),recipe("imag_silicon_ingot"),recipe("wafer"),recipe("imag_silicon_piece")),
        page("phase_generator",List.of("phase_gen"),recipe("phase_gen")),
        page("solar_generator",List.of("solar_gen"),recipe("solar_gen")),
        page("wind_generator",List.of("windgen_base","windgen_fan","windgen_main","windgen_pillar"),
            recipe("windgen_base"),recipe("windgen_pillar"),recipe("windgen_main"),recipe("windgen_fan")),
        page("metal_former",List.of("metal_former"),recipe("metal_former")),
        page("imag_fusor",List.of("imag_fusor"),recipe("imag_fusor")),
        page("terminal",List.of("terminal_installer","app_skill_tree","app_freq_transmitter","app_media_player"),
            recipe("terminal_installer"),recipe("app_skill_tree"),recipe("app_freq_transmitter"),recipe("app_media_player")),
        page("ability_developer",List.of("portable_developer","developer_normal","developer_advanced"),
            recipe("portable_developer"),recipe("developer_normal"),recipe("developer_advanced")),
        page("ability_basis",List.of()),
        page("energy_bridge",List.of("rf_input","rf_output"),recipe("rf_input"),recipe("rf_output")),
        page("misc",List.of()),
        page("develop_ability",List.of()),
        page("wireless_network",List.of()));
    /** Semantic ledger avoids null AIR aliases for devices that have not been registered yet. */
    public static final List<String> CONDITION_ITEMS=PAGES.stream().flatMap(p->p.obtainedItems().stream()).distinct().toList();
    public static List<Page> pages() { return PAGES; }
    public static Page page(String id) { return PAGES.stream().filter(p->p.id().equals(id)).findFirst().orElseThrow(()->new IllegalArgumentException("Unknown tutorial "+id)); }
    public static boolean knownPage(String id) { return PAGES.stream().anyMatch(p->p.id().equals(id)); }
    private ClassicTutorials() {}
}
