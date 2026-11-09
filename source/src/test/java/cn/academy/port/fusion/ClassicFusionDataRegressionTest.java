package cn.academy.port.fusion;
import com.google.gson.*;
import java.nio.file.*;
import java.util.*;
/** Resource source-geometry comparisons; no game bootstrap or live registry mutation. */
public final class ClassicFusionDataRegressionTest {
    private static int checks;
    private static void yes(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static JsonObject json(Path path)throws Exception{return JsonParser.parseString(Files.readString(path)).getAsJsonObject();}
    public static void main(String[] args)throws Exception {
        Path root=Path.of(System.getProperty("user.dir")),resources=Path.of(System.getProperty("academy.fusion.resources",root.resolve("src/main/resources").toString()));
        var source=json(root.resolve("docs/classic-survival-manifest.json"));Set<Integer> expected=Set.of(17,23,24,25,28,29,30,44,45);
        var aliases=Map.ofEntries(Map.entry("cons_plate","academy:constraint_plate"),Map.entry("glass","minecraft:glass"),Map.entry("crystal0","academy:crystal_low"),Map.entry("crystal1","academy:crystal_normal"),Map.entry("calc_chip","academy:calc_chip"),Map.entry("data_chip","academy:data_chip"),Map.entry("frame","academy:machine_frame"),Map.entry("conv_comp","academy:energy_convert_component"),Map.entry("matter_unit","academy:matter_unit"),Map.entry("reso_crystal","academy:reso_crystal"),Map.entry("mat_core#0","academy:matrix_core_0"),Map.entry("mat_core#1","academy:matrix_core_1"),Map.entry("dustGlowstone","#c:dusts/glowstone"),Map.entry("ender_pearl","minecraft:ender_pearl"),Map.entry("dev_portable","academy:portable_developer"),Map.entry("brain_comp","academy:brain_component"),Map.entry("info_comp","academy:info_component"),Map.entry("bed","minecraft:red_bed"),Map.entry("piston","minecraft:piston"),Map.entry("dustRedstone","#c:dusts/redstone"));
        int recipes=0;
        for(var element:source.getAsJsonArray("recipes")){
            var original=element.getAsJsonObject();int n=original.get("ordinal").getAsInt();if(!expected.contains(n))continue;
            String suffix=String.format("_%02d.json",n);Path path;try(var files=Files.list(resources.resolve("data/academy/recipe/classic"))){path=files.filter(p->p.getFileName().toString().endsWith(suffix)).findFirst().orElseThrow();}
            var recipe=json(path);yes(recipe.get("type").getAsString().equals("minecraft:crafting_shaped"),"native shaped source ordinal"+n);var pattern=recipe.getAsJsonArray("pattern");var rows=original.getAsJsonArray("rows");yes(pattern.size()==rows.size(),"source row count"+n);
            for(int y=0;y<rows.size();y++){var row=rows.get(y).getAsJsonArray();String line=pattern.get(y).getAsString();yes(line.length()==row.size(),"source width"+n);for(int x=0;x<row.size();x++){String alias=row.get(x).getAsString();if(alias.equals("nil"))yes(line.charAt(x)==' ',"nil stays empty"+n);else {var ingredient=recipe.getAsJsonObject("key").getAsJsonObject(String.valueOf(line.charAt(x)));String value=ingredient.has("tag")?"#"+ingredient.get("tag").getAsString():ingredient.get("item").getAsString();yes(value.equals(aliases.get(alias)),"exact ingredient/order"+n);}}}
            yes(recipe.getAsJsonObject("result").get("count").getAsInt()==(n==17?4:1),"source yield"+n);recipes++;
        }
        yes(recipes==9,"all nine source declarations");
        var states=json(resources.resolve("assets/academy/blockstates/imag_fusor.json")).getAsJsonObject("variants");yes(states.size()==8,"four source orientations two states");for(String face:List.of("north","east","south","west"))for(boolean active:List.of(false,true))yes(states.has("facing="+face+",working="+active),"source front orientation covered");
        var work=json(resources.resolve("assets/academy/textures/blocks/ief_working_classic.png.mcmeta"));yes(work.getAsJsonObject("animation").get("frametime").getAsInt()==8,"source400msfrontframe");
        for(String item:List.of("matter_unit","matter_unit_phase_liquid")){var animation=json(resources.resolve("assets/academy/textures/items/"+item+"_classic.png.mcmeta")).getAsJsonObject("animation").getAsJsonArray("frames");int duration=0;for(var entry:animation)duration+=entry.getAsJsonObject().get("time").getAsInt();yes(animation.size()==32&&duration==200,"source10secwrappedmaskperiod");}
        var phaseState=Files.readString(root.resolve("src/main/java/cn/academy/port/client/ClassicPhaseRenderTypes.java"));
        yes(phaseState.contains("setupRenderState(){RenderSystem.disableDepthTest();}"),"Legacy phase depth setup must actually disable native GL depth");
        yes(phaseState.contains("clearRenderState(){RenderSystem.enableDepthTest();}"),"Legacy phase restores native depth state");
        yes(phaseState.contains(".setDepthTestState(LEGACY_NO_DEPTH)"),"Phase layers use explicit state, not modern no-op NO_DEPTH_TEST");
        yes(phaseState.contains(".setWriteMaskState(COLOR_WRITE)"),"Phase layers never write depth");
        yes(Files.readString(root.resolve("src/main/java/cn/academy/port/client/ClassicPhaseRenderer.java")).contains("ClassicWirelessClock.millis()"),"Phase scrolling preserves legacy pause-aware clock");
        yes(Files.readString(root.resolve("src/main/java/cn/academy/port/client/ClassicPhaseRenderer.java")).contains("ClassicPhaseLateEffects.queue(tile)"),"Native visible tiles queue original translucent-pass overlays");
        yes(Files.readString(root.resolve("src/main/java/cn/academy/port/client/ClassicPhaseLateEffects.java")).contains("AFTER_TRANSLUCENT_BLOCKS"),"Legacy pass1 overlays render after black fluid base");
        System.out.println("ClassicFusionDataRegressionTest: "+checks+" assertions passed");
    }
}
