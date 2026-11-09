package cn.academy.port.client.terminal;
import java.nio.file.*;
import java.net.URLClassLoader;
import javax.tools.ToolProvider;
import java.util.ArrayList;
import static cn.academy.port.client.terminal.TerminalClientFixtures.*;
final class OriginalTerminalOracle implements AutoCloseable {
    private final Path temporary;
    private final URLClassLoader loader;
    OriginalTerminalOracle()throws Exception{
        temporary=Files.createTempDirectory("academy-original-terminal-oracle-");Path source=temporary.resolve("src"),classes=temporary.resolve("classes");Files.createDirectories(classes);var files=new ArrayList<String>();
        for(String relative:text("oracle-stubs-index.txt").split("\\R")){if(relative.isBlank())continue;Path file=source.resolve(relative);Files.createDirectories(file.getParent());Files.write(file,bytes("oracle-stubs/"+relative+".txt"));files.add(file.toString());}
        for(var original:java.util.Map.of("TerminalInstallEffect.java","cn/academy/terminal/client/TerminalInstallEffect.java","TerminalInstallerRenderer.java","cn/academy/terminal/client/TerminalInstallerRenderer.java","RenderModelItem.java","cn/lambdalib/template/client/render/item/RenderModelItem.java","ItemModelCustom.java","cn/lambdalib/util/deprecated/ItemModelCustom.java").entrySet()){Path file=source.resolve(original.getValue());Files.createDirectories(file.getParent());Files.write(file,bytes(original.getKey()+".txt"));files.add(file.toString());}
        var args=new ArrayList<>(java.util.List.of("-proc:none","-encoding","UTF-8","-d",classes.toString()));args.addAll(files);check(ToolProvider.getSystemJavaCompiler().run(null,null,null,args.toArray(String[]::new))==0,"Unchanged original renderer/install source compiler");loader=new URLClassLoader(new java.net.URL[]{classes.toUri().toURL()},null);
    }
    java.lang.reflect.Method method(String owner,String name,Class<?>...arguments)throws Exception{return Class.forName(owner,true,loader).getMethod(name,arguments);}
    @Override public void close()throws Exception{loader.close();try(var paths=Files.walk(temporary)){for(Path path:paths.sorted(java.util.Comparator.reverseOrder()).toList())Files.delete(path);}}
}
