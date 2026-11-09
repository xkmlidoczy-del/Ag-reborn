/* Unchanged original numeric source loaded apart from the real modern classes. GPLv3; see NOTICE. */
package cn.academy.port.core;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.net.URLClassLoader;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import javax.tools.ToolProvider;

/** The fixture only hosts the old platform; the port under test is its actual compiled production code. */
public final class ClassicNumericLedgersSourceOracleTest {
    static final String PREFIX="classic-oracles/numeric-ledgers/";
    static byte[] resource(String path)throws Exception {
        try(var input=ClassicNumericLedgersSourceOracleTest.class.getResourceAsStream("/"+PREFIX+path)){
            if(input==null)throw new AssertionError("Missing numeric source fixture "+path);return input.readAllBytes();
        }
    }
    public static void main(String[] args)throws Exception {
        var pinned=Map.of(
          "source/cn/academy/ability/api/data/CPData.java.txt","6ef3a87f06fa86e422b183d379c07093366bf60b89057adc94c01cb9088324c3",
          "source/cn/academy/ability/api/data/AbilityData.java.txt","a2be759d022d4992bbbba1cfc6e1fa3874015a8f615445864f65f62c250f126e",
          "source/cn/academy/ability/api/event/CalcEvent.java.txt","45cf7ef4ab13a1d3e29d467aa4b1aa9e3b800a883b96496f88cbffcee7ce1dc3",
          "source/cn/lambdalib/util/generic/MathUtils.java.txt","e80c87ca15cbae89584bd9e1111e22d2f55866bf91b434c8813c20bb98176075");
        String witnesses=new String(resource("source-witnesses.json"),StandardCharsets.UTF_8);
        if(!witnesses.contains("00d19ec0cf538f61c1095c9292f5ee6863db4521")||!witnesses.contains("1.2.3"))throw new AssertionError("Wrong canonical numeric source revision");
        Path temp=Files.createTempDirectory("academy-numeric-original-");
        try {
            var modern=AbilityProgress.class.getProtectionDomain().getCodeSource().getLocation();
            var compilerArgs=new ArrayList<String>(List.of("-proc:none","-classpath",Path.of(modern.toURI()).toString(),"-d",temp.resolve("classes").toString()));
            int files=0;var found=new HashSet<String>();
            for(String line:new String(resource("files.sha256"),StandardCharsets.UTF_8).split("\\R")){
                if(line.isBlank())continue;String[] parts=line.split("  ",2);if(parts.length!=2)throw new AssertionError("Malformed numeric fixture index");
                String relative=parts[1];byte[] bytes=resource(relative);String hash=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
                if(!hash.equals(parts[0])||pinned.containsKey(relative)&&!hash.equals(pinned.get(relative)))throw new AssertionError("Changed numeric source fixture "+relative);
                if(!found.add(relative))throw new AssertionError("Duplicate numeric fixture "+relative);
                Path output=temp.resolve("src").resolve(relative.substring(0,relative.length()-4));
                if(!output.normalize().startsWith(temp.resolve("src")))throw new AssertionError("Unsafe numeric fixture path");
                Files.createDirectories(output.getParent());Files.write(output,bytes);compilerArgs.add(output.toString());files++;
            }
            if(!found.containsAll(pinned.keySet())||files!=40)throw new AssertionError("Incomplete unchanged-original numeric fixture inventory");
            var compiler=ToolProvider.getSystemJavaCompiler();
            if(compiler==null||compiler.run(null,System.out,System.err,compilerArgs.toArray(String[]::new))!=0)throw new AssertionError("Unchanged original numeric source compilation failed");
            // Core category validation uses the actual catalog and its native API signatures.
            // Keep originals isolated while supplying their real modern dependencies, never test stubs.
            var urls=new LinkedHashSet<java.net.URL>();urls.add(temp.resolve("classes").toUri().toURL());urls.add(modern);
            for(String entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator)))if(!entry.isBlank())urls.add(Path.of(entry).toUri().toURL());
            try(var loader=new URLClassLoader(urls.toArray(java.net.URL[]::new),ClassLoader.getPlatformClassLoader())){
                if(!loader.loadClass(AbilityProgress.class.getName()).getProtectionDomain().getCodeSource().getLocation().equals(modern))throw new AssertionError("Wrong actual modern numeric class origin");
                var harness=loader.loadClass("cn.academy.port.core.NumericLedgersSourceOracleHarness");
                try{harness.getMethod("main",String[].class).invoke(null,(Object)args);}catch(InvocationTargetException e){throw new AssertionError("Unchanged original numeric differential failed",e.getCause());}
            }
            System.out.println("PASS 40 hashed fixtures; isolated unchanged-source oracle uses actual modern production class location");
        }finally{try(var paths=Files.walk(temp)){for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);}}
    }
}
