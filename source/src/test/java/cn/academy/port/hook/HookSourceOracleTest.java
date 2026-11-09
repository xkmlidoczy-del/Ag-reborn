package cn.academy.port.hook;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import javax.tools.ToolProvider;

/** Portable launcher for the already completed, byte-identical unchanged-original Hook oracle.
 * Legacy shims compile into a temporary child classloader, never the production/test classpath. */
public final class HookSourceOracleTest {
    public static void main(String[] args)throws Exception{
        Path fixture=Path.of(System.getProperty("academy.hook.fixtureRoot","src/test/resources/classic-hook-source"));
        HookPortableDataTest.verifyFixture(fixture);
        var compiler=ToolProvider.getSystemJavaCompiler();if(compiler==null)throw new AssertionError("Hook oracle requires a JDK21 compiler");
        Path output=Files.createTempDirectory("academy-hook-original-oracle-");
        try{
            List<String> arguments=new ArrayList<>(List.of("-proc:none","-encoding","UTF-8","-classpath",System.getProperty("java.class.path"),"-d",output.toString()));
            for(String folder:List.of("originals","stubs","harness"))try(var files=Files.walk(fixture.resolve(folder))){files.filter(p->p.toString().endsWith(".java")).sorted().forEach(p->arguments.add(p.toString()));}
            var diagnostics=new ByteArrayOutputStream();int status=compiler.run(null,diagnostics,diagnostics,arguments.toArray(String[]::new));
            if(status!=0)throw new AssertionError("Unchanged Hook source compilation failed:\n"+diagnostics.toString(java.nio.charset.StandardCharsets.UTF_8));
            List<URL> urls=new ArrayList<>();urls.add(output.toUri().toURL());for(String entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))urls.add(Path.of(entry).toUri().toURL());
            try(var legacy=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader())){
                Class<?> harness=Class.forName("oracle.SourceOracle",true,legacy);
                try{harness.getMethod("main",String[].class).invoke(null,(Object)new String[0]);}
                catch(InvocationTargetException failure){throw new AssertionError("Unchanged Hook source oracle failed",failure.getCause());}
            }
            System.out.println("PASS portable Hook source launcher: unchanged oracle/shims loaded exclusively from packaged fixtures; no game bootstrap");
        }finally{try(var files=Files.walk(output)){for(Path file:files.sorted(Comparator.reverseOrder()).toList())Files.delete(file);}}
    }
    private HookSourceOracleTest(){}
}
