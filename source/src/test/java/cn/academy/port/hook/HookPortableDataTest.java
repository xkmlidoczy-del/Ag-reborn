package cn.academy.port.hook;

import com.google.gson.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

/** Pins all unchanged witnesses, finite shims, source assets and licenses, then checks real port data.
 * All inputs come from normal source-archive paths. No private reference/staging tree is consulted. */
public final class HookPortableDataTest {
    static int assertions;
    static void check(boolean condition,String message){assertions++;if(!condition)throw new AssertionError(message);}
    static String hash(Path file)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
    public static void verifyFixture(Path folder)throws Exception{
        Path root=folder.toAbsolutePath().normalize();JsonObject manifest=JsonParser.parseString(Files.readString(root.resolve("manifest.json"))).getAsJsonObject();
        check(manifest.get("academycraft_expected_commit").getAsString().equals("00d19ec0cf538f61c1095c9292f5ee6863db4521"),"expected supplied AcademyCraft1.0.7 identity pinned");check(manifest.get("lambdalib_release").getAsString().equals("1.2.3"),"LambdaLib1.2.3 identity pinned");
        var expected=new HashSet<String>();var counts=new HashMap<String,Integer>();
        for(JsonElement value:manifest.getAsJsonArray("files")){
            JsonObject entry=value.getAsJsonObject();String name=entry.get("path").getAsString();Path path=root.resolve(name).normalize();check(path.startsWith(root),"fixture stays inside archive directory");check(expected.add(name),"no duplicate fixture entry");check(Files.isRegularFile(path),"packaged witness exists: "+name);check(hash(path).equals(entry.get("sha256").getAsString()),"pinned fixture bytes: "+name);counts.merge(entry.get("category").getAsString(),1,Integer::sum);
        }
        try(var files=Files.walk(root)){for(Path file:files.filter(Files::isRegularFile).toList()){String name=root.relativize(file).toString().replace(java.io.File.separatorChar,'/');check(name.equals("manifest.json")||expected.contains(name),"no unpinned fixture file: "+name);}}
        check(counts.getOrDefault("originals",0)==15,"fifteen unchanged originals packaged");check(counts.getOrDefault("stubs",0)==42,"forty-two necessary old API/GL shims packaged");check(counts.getOrDefault("harness",0)==1,"completed oracle harness packaged unchanged");check(counts.getOrDefault("assets",0)==5,"two models, two textures and original recipe script packaged");check(counts.getOrDefault("licenses",0)==3,"GPL3, additional notices and LambdaLib MIT packaged");
        check(Files.readString(root.resolve("licenses/AcademyCraft-GPL3.txt")).contains("GNU GENERAL PUBLIC LICENSE"),"full GPL3 present");check(Files.readString(root.resolve("licenses/LambdaLib-MIT.txt")).contains("Permission is hereby granted, free of charge"),"full MIT present");check(Files.readString(root.resolve("licenses/AcademyCraft-additional-notices.txt")).contains("Prohibits any person"),"upstream additional notices retained");
    }
    public static void main(String[] args)throws Exception{
        Path fixture=Path.of(System.getProperty("academy.hook.fixtureRoot","src/test/resources/classic-hook-source"));Path project=Path.of(System.getProperty("academy.hook.projectRoot","."));verifyFixture(fixture);
        var matcher=Pattern.compile("(?m)^\\s*(shaped|shapeless|smelting)\\s*\\(([^)]+)\\)").matcher(Files.readString(fixture.resolve("assets/academy/recipes/default.recipe")));int ordinal=0;String hook=null;while(matcher.find()){ordinal++;if(ordinal==41)hook=matcher.group(1)+":"+matcher.group(2);}check(ordinal==49&&"shaped:mag_hook*3".equals(hook),"source ordinal41 among49 declarations");
        var recipe=JsonParser.parseString(Files.readString(project.resolve("src/main/resources/data/academy/recipe/classic/maghook_41.json"))).getAsJsonObject();check(recipe.get("type").getAsString().equals("minecraft:crafting_shaped"),"native shaped recipe");check(recipe.getAsJsonArray("pattern").toString().equals("[\" P \",\"PPP\",\" P \"]"),"source five-plate cross retained");check(recipe.getAsJsonObject("key").getAsJsonObject("P").get("tag").getAsString().equals("c:plates/iron"),"source iron plate tag translated");check(recipe.getAsJsonObject("result").get("id").getAsString().equals("academy:maghook")&&recipe.getAsJsonObject("result").get("count").getAsInt()==3,"source three-hook output retained");
        var plates=JsonParser.parseString(Files.readString(project.resolve("src/main/resources/data/c/tags/item/plates/iron.json"))).getAsJsonObject();boolean obtainable=false;for(JsonElement value:plates.getAsJsonArray("values"))if(value.isJsonPrimitive()&&value.getAsString().equals("academy:reinforced_iron_plate"))obtainable=true;check(obtainable,"real obtainable port plate belongs to tag");
        for(String path:List.of("models/maghook.obj","models/maghook_open.obj","textures/models/maghook.png","textures/items/maghook.png"))check(hash(fixture.resolve("assets/academy/"+path)).equals(hash(project.resolve("src/main/resources/assets/academy/"+path))),"actual production asset matches pinned source: "+path);
        var icon=JsonParser.parseString(Files.readString(project.resolve("src/main/resources/assets/academy/models/item/maghook.json"))).getAsJsonObject();check(icon.get("parent").getAsString().equals("minecraft:item/generated")&&icon.getAsJsonObject("textures").get("layer0").getAsString().equals("academy:items/maghook"),"source GUI icon route retained");
        check(Files.readString(project.resolve("src/main/java/cn/academy/port/skill/ClassicMetalTargets.java")).contains("\"academy:maghook\""),"real entity included in configurable default metal targets");
        System.out.println("PASS "+assertions+" portable Hook archive/data assertions: pinned originals/shims/assets/licenses, recipe41, obtainable plate membership, actual source visuals and metal-target registration");
    }
    private HookPortableDataTest(){}
}
