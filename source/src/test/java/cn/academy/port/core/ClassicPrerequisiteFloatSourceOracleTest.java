/* Unchanged original learning conditions isolated from real modern classes. GPLv3; see NOTICE. */
package cn.academy.port.core;

import cn.academy.port.SkillCatalog;
import cn.academy.port.develop.DevelopmentActions;
import java.lang.reflect.InvocationTargetException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import javax.tools.ToolProvider;

/** All legacy platform hosts live in resources and the isolated temporary oracle, never a source set. */
public final class ClassicPrerequisiteFloatSourceOracleTest {
    private static final String PREFIX="classic-oracles/progression-fidelity/";
    private static byte[] resource(String relative)throws Exception {
        try(var in=ClassicPrerequisiteFloatSourceOracleTest.class.getResourceAsStream("/"+PREFIX+relative)) {
            if(in==null)throw new AssertionError("Missing source prerequisite fixture "+relative);
            return in.readAllBytes();
        }
    }
    public static void main(String[] args)throws Exception {
        var pinned=Map.of(
            "source/cn/academy/ability/develop/condition/DevConditionDep.java.txt","0dace28d10b1d397137aa554f33af9b662c84459306aefcc91ba6f60fab7046f",
            "source/cn/academy/ability/api/data/AbilityData.java.txt","a2be759d022d4992bbbba1cfc6e1fa3874015a8f615445864f65f62c250f126e",
            "source/cn/academy/ability/api/data/CPData.java.txt","6ef3a87f06fa86e422b183d379c07093366bf60b89057adc94c01cb9088324c3",
            "source/cn/lambdalib/util/generic/MathUtils.java.txt","e80c87ca15cbae89584bd9e1111e22d2f55866bf91b434c8813c20bb98176075");
        String witnesses=new String(resource("source-witnesses.json"),StandardCharsets.UTF_8);
        if(!witnesses.contains("00d19ec0cf538f61c1095c9292f5ee6863db4521")||!witnesses.contains("1.2.3"))
            throw new AssertionError("Wrong canonical prerequisite source revision");
        var modernLocations=new LinkedHashSet<URL>();
        for(Class<?> type:List.of(SkillCatalog.class,DevelopmentActions.class,AbilityProgress.class))
            modernLocations.add(type.getProtectionDomain().getCodeSource().getLocation());
        var runtimeEntries=new LinkedHashSet<Path>();
        for(URL url:modernLocations)runtimeEntries.add(Path.of(url.toURI()));
        for(String entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
            if(!entry.isBlank())runtimeEntries.add(Path.of(entry));
        String classpath=String.join(java.io.File.pathSeparator,runtimeEntries.stream().map(Path::toString).toList());
        Path temp=Files.createTempDirectory("academy-prerequisite-original-");
        try {
            var compilerArgs=new ArrayList<String>(List.of("-proc:none","-encoding","UTF-8","-classpath",classpath,"-d",temp.resolve("classes").toString()));
            var found=new HashSet<String>();
            for(String line:new String(resource("files.sha256"),StandardCharsets.UTF_8).split("\\R")) {
                if(line.isBlank())continue;String[] fields=line.split("  ",2);
                if(fields.length!=2)throw new AssertionError("Malformed prerequisite fixture index");
                String relative=fields[1];
                if(!relative.endsWith(".java.txt")||relative.equals("hosts/net/minecraft/nbt/CompoundTag.java.txt")||relative.equals("hosts/net/minecraft/nbt/Tag.java.txt"))
                    throw new AssertionError("Modern native NBT host forbidden in original prerequisite oracle "+relative);
                byte[] bytes=resource(relative);
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if(!hash.equals(fields[0])||pinned.containsKey(relative)&&!hash.equals(pinned.get(relative)))
                    throw new AssertionError("Changed prerequisite source fixture "+relative);
                if(!found.add(relative))throw new AssertionError("Duplicate prerequisite fixture "+relative);
                Path output=temp.resolve("src").resolve(relative.substring(0,relative.length()-4));
                if(!output.normalize().startsWith(temp.resolve("src")))throw new AssertionError("Unsafe prerequisite fixture path");
                Files.createDirectories(output.getParent());Files.write(output,bytes);compilerArgs.add(output.toString());
            }
            if(found.size()!=44||!found.containsAll(pinned.keySet()))throw new AssertionError("Incomplete unchanged prerequisite source bundle");
            var compiler=ToolProvider.getSystemJavaCompiler();
            if(compiler==null||compiler.run(null,System.out,System.err,compilerArgs.toArray(String[]::new))!=0)
                throw new AssertionError("Unchanged original prerequisite oracle compilation failed");
            var urls=new LinkedHashSet<URL>();urls.add(temp.resolve("classes").toUri().toURL());urls.addAll(modernLocations);
            for(Path entry:runtimeEntries)urls.add(entry.toUri().toURL());
            try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader())) {
                // Prove the tested modern classes resolve from their current main locations, never input snapshots.
                for(Class<?> type:List.of(SkillCatalog.class,DevelopmentActions.class,AbilityProgress.class)) {
                    var isolated=loader.loadClass(type.getName());
                    if(!isolated.getProtectionDomain().getCodeSource().getLocation().equals(type.getProtectionDomain().getCodeSource().getLocation()))
                        throw new AssertionError("Wrong actual modern class location "+type.getName());
                }
                var harness=loader.loadClass("cn.academy.port.progression.PrerequisiteFloatSourceOracleTest");
                try{harness.getMethod("main",String[].class).invoke(null,(Object)args);}
                catch(InvocationTargetException e){throw new AssertionError("Unchanged original prerequisite differential failed",e.getCause());}
            }
            System.out.println("PASS 44 hashed original/host fixtures; actual modern SkillCatalog/DevelopmentActions/AbilityProgress locations="+modernLocations);
        } finally {
            try(var paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
}
