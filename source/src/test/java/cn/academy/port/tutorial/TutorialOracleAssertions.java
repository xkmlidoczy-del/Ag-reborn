package cn.academy.port.tutorial;

import java.util.List;
import java.util.Map;

final class TutorialOracleAssertions {
    static int checks;
    static final Map<String,String> MODERN_IDS = Map.ofEntries(
        Map.entry("oreConstraintMetal","academy:constraint_metal_ore"),
        Map.entry("oreImagSil","academy:imag_silicon_ore"),
        Map.entry("oreImagCrystal","academy:crystal_ore"),
        Map.entry("oreResoCrystal","academy:reso_crystal_ore"),
        Map.entry("phaseGen","academy:phase_gen"),
        Map.entry("solarGen","academy:solar_gen"),
        Map.entry("windgenBase","academy:windgen_base"),
        Map.entry("windgenFan","academy:windgen_fan"),
        Map.entry("windgenMain","academy:windgen_main"),
        Map.entry("windgenPillar","academy:windgen_pillar"),
        Map.entry("metalFormer","academy:metal_former"),
        Map.entry("imagFusor","academy:imag_fusor"),
        Map.entry("terminalInstaller","academy:terminal_installer"),
        Map.entry("app_skill_tree","academy:app_skill_tree"),
        Map.entry("app_freq_transmitter","academy:app_freq_transmitter"),
        Map.entry("app_media_player","academy:app_media_player"),
        Map.entry("developerPortable","academy:portable_developer"),
        Map.entry("developerNormal","academy:developer_normal"),
        Map.entry("developerAdvanced","academy:developer_advanced"),
        Map.entry("rfInput","academy:rf_input"),
        Map.entry("rfOutput","academy:rf_output"),
        Map.entry("constPlate","academy:constraint_plate"),
        Map.entry("ingotImagSil","academy:imag_silicon_ingot"),
        Map.entry("wafer","academy:wafer"),
        Map.entry("silPiece","academy:imag_silicon_piece")
    );
    static void check(boolean condition,String reason) {
        checks++;
        if(!condition)throw new AssertionError(reason);
    }
    static void equal(Object actual,Object expected,String reason) {
        check(java.util.Objects.deepEquals(actual,expected),reason+"; actual="+display(actual)+" expected="+display(expected));
    }
    private static String display(Object value) {
        return value instanceof long[] bits?java.util.Arrays.toString(bits):String.valueOf(value);
    }
    static String modern(String classic) {
        String id=MODERN_IDS.get(classic);
        if(id==null)throw new AssertionError("Unmapped original item identity "+classic);
        return id;
    }
    static String classic(String modern) {
        return MODERN_IDS.entrySet().stream().filter(e->e.getValue().equals(modern)).findFirst().orElseThrow().getKey();
    }
    static String preview(String descriptor) {
        int split=descriptor.indexOf(':');
        String kind=descriptor.substring(0,split),target=descriptor.substring(split+1);
        return kind+":"+(kind.equals("icon")?"academy:textures/"+target+".png":modern(target));
    }
    static void compareState(TutorialState runtime,ClassicTutorialOracle source,String label) throws Exception {
        equal(runtime.conditionBits(),source.bits(),label+" persisted condition bits");
        equal(runtime.activatedIds(),source.activatedIds(),label+" persisted activated set");
        equal(runtime.tutorialAcquired(),source.acquired(),label+" first guide acquired flag");
        equal(runtime.misakaID(),source.misakaID(),label+" persistent Misaka ID");
        equal(runtime.dirty(),source.dirty(),label+" transient dirty flag");
        for(String id:source.ids()) {
            equal(runtime.visible(id),source.visible(id),label+" current condition visibility "+id);
            equal(runtime.activated(id),source.activated(id),label+" stored activation "+id);
        }
    }
    static void tick(TutorialState runtime,ClassicTutorialOracle source,List<String> actual,String label) throws Exception {
        runtime.tick(id->actual.add("activate:"+id),()->actual.add("drop"),()->actual.add("sync"));
        source.tick();
        equal(actual,source.observableLog(),label+" activation/drop/sync ordered trace");
        compareState(runtime,source,label);
    }
    private TutorialOracleAssertions() {}
}
