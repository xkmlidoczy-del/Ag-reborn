/* Unchanged original developer category lifecycle isolated from real modern classes. GPLv3; see NOTICE. */
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
public final class ClassicActivationTransportSourceOracleTest {
    private static final String PREFIX="classic-oracles/developer-lifecycle/";
    private static byte[] resource(String relative)throws Exception {
        try(var in=ClassicActivationTransportSourceOracleTest.class.getResourceAsStream("/"+PREFIX+relative)) {
            if(in==null)throw new AssertionError("Missing source developer lifecycle fixture "+relative);
            return in.readAllBytes();
        }
    }
    public static void main(String[] args)throws Exception {
        var pinned=Map.of(
            "source/cn/academy/ability/api/data/AbilityData.java.txt","a2be759d022d4992bbbba1cfc6e1fa3874015a8f615445864f65f62c250f126e",
            "source/cn/academy/ability/api/data/CPData.java.txt","6ef3a87f06fa86e422b183d379c07093366bf60b89057adc94c01cb9088324c3",
            "source/cn/academy/ability/develop/action/DevelopActionLevel.java.txt","44b6f2b236454ecf04e85f113e1ec876832f5ddcd60d34fba2351317a438f9a5",
            "source/cn/academy/ability/develop/action/DevelopActionReset.java.txt","1097884be881c6851818a834b757c83eadaa0d7b1c025ed85d8e0fb4327a019c",
            "source/cn/academy/ability/api/data/PresetData.java.txt","f375a767a7d19b6d02c3f2de8c7cf3fc472a80b75d646c85df1fe7c7e8d9a6bd",
            "source/cn/academy/ability/api/cooldown/CooldownData.java.txt","2945da5d8d76cb5f230ca5428003aae5bb46138a0787138e3f0f51c7ffa7fc10",
            "source/cn/academy/misc/achievements/DispatcherAch.java.txt","d3d382f3e374ab2d218b2522c81f4017e408a0dcbb4d7bec316b8e35c7ed6cc0",
            "source/cn/academy/misc/achievements/aches/AchEvLevelChange.java.txt","75464468571a9901e632fb00f59daf00d6126c2a7b714a49223b496199048c03");
        String witnesses=new String(resource("source-witnesses.json"),StandardCharsets.UTF_8);
        if(!witnesses.contains("00d19ec0cf538f61c1095c9292f5ee6863db4521")||!witnesses.contains("1.2.3"))
            throw new AssertionError("Wrong canonical developer lifecycle source revision");
        for(String line:new String(resource("witness-files.sha256"),StandardCharsets.UTF_8).split("\\R")) {
            if(line.isBlank())continue;String[] fields=line.split("  ",2);byte[] bytes=resource(fields[1]);
            if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals(fields[0]))
                throw new AssertionError("Changed noncompiled lifecycle witness "+fields[1]);
        }
        var modernLocations=new LinkedHashSet<URL>();
        for(Class<?> type:List.of(SkillCatalog.class,DevelopmentActions.class,AbilityProgress.class,ActivationTransport.class,cn.academy.port.api.AbilityActivationLifecycle.class))
            modernLocations.add(type.getProtectionDomain().getCodeSource().getLocation());
        var runtimeEntries=new LinkedHashSet<Path>();
        for(URL url:modernLocations)runtimeEntries.add(Path.of(url.toURI()));
        for(String entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))
            if(!entry.isBlank())runtimeEntries.add(Path.of(entry));
        String classpath=String.join(java.io.File.pathSeparator,runtimeEntries.stream().map(Path::toString).toList());
        Path temp=Files.createTempDirectory("academy-developer-lifecycle-original-");
        try {
            var compilerArgs=new ArrayList<String>(List.of("-proc:none","-encoding","UTF-8","-classpath",classpath,"-d",temp.resolve("classes").toString()));
            var found=new HashSet<String>();
            for(String line:new String(resource("files.sha256"),StandardCharsets.UTF_8).split("\\R")) {
                if(line.isBlank())continue;String[] fields=line.split("  ",2);
                if(fields.length!=2)throw new AssertionError("Malformed developer lifecycle fixture index");
                String relative=fields[1];
                if(!relative.endsWith(".java.txt")||relative.equals("hosts/net/minecraft/nbt/CompoundTag.java.txt")||relative.equals("hosts/net/minecraft/nbt/Tag.java.txt"))
                    throw new AssertionError("Modern native NBT host forbidden in original developer lifecycle oracle "+relative);
                byte[] bytes=resource(relative);
                String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if(!hash.equals(fields[0])||pinned.containsKey(relative)&&!hash.equals(pinned.get(relative)))
                    throw new AssertionError("Changed developer lifecycle source fixture "+relative);
                if(!found.add(relative))throw new AssertionError("Duplicate developer lifecycle fixture "+relative);
                if(relative.startsWith("harness/"))continue;
                if(relative.equals("hosts/cn/lambdalib/util/datapart/DataPart.java.txt")){
                    try(var in=ClassicActivationTransportSourceOracleTest.class.getResourceAsStream("/classic-oracles/activation-transport/"+relative)){if(in==null)throw new AssertionError("Missing declared transport/side host");bytes=in.readAllBytes();}
                    if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals("77591ba003db1a4524fc7f5d701c6a1d75a6d2b73c2674810f0fdf2dc8a5540a"))throw new AssertionError("Changed declared transport/side host");
                }
                if(relative.equals("hosts/cn/lambdalib/s11n/network/NetworkMessage.java.txt")){
                    try(var in=ClassicActivationTransportSourceOracleTest.class.getResourceAsStream("/classic-oracles/activation-transport/"+relative)){if(in==null)throw new AssertionError("Missing declared transport/side host");bytes=in.readAllBytes();}
                    if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)).equals("337dde68a93bd19f4332820e93ffe3791454531ac768ec57124bd74184e947f0"))throw new AssertionError("Changed declared transport/side host");
                }
                Path output=temp.resolve("src").resolve(relative.substring(0,relative.length()-4));
                if(!output.normalize().startsWith(temp.resolve("src")))throw new AssertionError("Unsafe developer lifecycle fixture path");
                Files.createDirectories(output.getParent());Files.write(output,bytes);compilerArgs.add(output.toString());
            }
            if(found.size()!=77||!found.containsAll(pinned.keySet()))throw new AssertionError("Incomplete unchanged developer lifecycle source bundle");
            byte[] harnessBytes;
            try(var in=ClassicActivationTransportSourceOracleTest.class.getResourceAsStream("/classic-oracles/activation-transport/harness/cn/academy/port/core/ActivationTransportSourceOracleHarness.java.txt")){if(in==null)throw new AssertionError("Missing activation-transport harness");harnessBytes=in.readAllBytes();}
            if(!HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(harnessBytes)).equals("ccbab0b91907fd80671772efadfcdeda16e58ce37372bd4c1e0338968816eeff"))throw new AssertionError("Changed activation-transport harness");
            Path harnessPath=temp.resolve("src/harness/cn/academy/port/core/ActivationTransportSourceOracleHarness.java");Files.createDirectories(harnessPath.getParent());Files.write(harnessPath,harnessBytes);compilerArgs.add(harnessPath.toString());
            var compiler=ToolProvider.getSystemJavaCompiler();
            if(compiler==null||compiler.run(null,System.out,System.err,compilerArgs.toArray(String[]::new))!=0)
                throw new AssertionError("Unchanged original developer lifecycle oracle compilation failed");
            var urls=new LinkedHashSet<URL>();urls.add(temp.resolve("classes").toUri().toURL());urls.addAll(modernLocations);
            for(Path entry:runtimeEntries)urls.add(entry.toUri().toURL());
            try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader())) {
                // Prove the tested modern classes resolve from their current main locations, never input snapshots.
                for(Class<?> type:List.of(SkillCatalog.class,DevelopmentActions.class,AbilityProgress.class,ActivationTransport.class,cn.academy.port.api.AbilityActivationLifecycle.class)) {
                    var isolated=loader.loadClass(type.getName());
                    if(!isolated.getProtectionDomain().getCodeSource().getLocation().equals(type.getProtectionDomain().getCodeSource().getLocation()))
                        throw new AssertionError("Wrong actual modern class location "+type.getName());
                }
                var harness=loader.loadClass("cn.academy.port.core.ActivationTransportSourceOracleHarness");
                try{harness.getMethod("main",String[].class).invoke(null,(Object)args);}
                catch(InvocationTargetException e){throw new AssertionError("Unchanged original developer lifecycle differential failed",e.getCause());}
            }
            System.out.println("PASS 77 hashed original/host fixtures; actual modern SkillCatalog/DevelopmentActions/AbilityProgress locations="+modernLocations);
        } finally {
            try(var paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}
        }
    }
}
