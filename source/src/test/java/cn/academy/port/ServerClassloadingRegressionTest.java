package cn.academy.port;
import java.net.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
/** Static/cold linkage check, never constructs or starts Minecraft or accepts an agreement. */
public final class ServerClassloadingRegressionTest {
    private static final class ServerOnlyLoader extends URLClassLoader {
        int clientRequests;
        ServerOnlyLoader(URL[] urls){super(urls,ClassLoader.getPlatformClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(name.startsWith("net.minecraft.client.")||(name.startsWith("net.neoforged.neoforge.client.") && !name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension"))||(name.startsWith("cn.academy.port.client.")||name.startsWith("cn.academy.port.worldboss.client."))){clientRequests++;throw new ClassNotFoundException("Client namespace denied for server-link test: "+name);}
            return super.loadClass(name,resolve);
        }
    }
    public static void main(String[] args)throws Exception {
        var entries=System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator));var urls=new ArrayList<URL>();for(String entry:entries)urls.add(Path.of(entry).toUri().toURL());
        int classes=0;var bossCommon=new HashSet<String>();
        try(var loader=new ServerOnlyLoader(urls.toArray(URL[]::new))){
            Path root=Path.of("build/classes/java/main/cn/academy/port");
            try(var files=Files.walk(root)){for(Path file:files.filter(p->p.toString().endsWith(".class")).toList()){
                String relative=root.relativize(file).toString().replace(File.separatorChar,'.');
                if(relative.startsWith("client.")||relative.startsWith("worldboss.client.")||relative.startsWith("gametest.")||relative.startsWith("qa."))continue;
                String name="cn.academy.port."+relative.substring(0,relative.length()-6);
                Class<?> type=Class.forName(name,false,loader);
                type.getDeclaredConstructors();type.getDeclaredMethods();type.getDeclaredFields();classes++;
                if(name.startsWith("cn.academy.port.worldboss."))bossCommon.add(name);
            }}
            if(loader.clientRequests!=0)throw new AssertionError("Server cold-link requested "+loader.clientRequests+" client classes");
        }
        if(!bossCommon.containsAll(Set.of("cn.academy.port.worldboss.WorldBossContent","cn.academy.port.worldboss.WorldBossContent$Boss","cn.academy.port.worldboss.WorldBossCombat","cn.academy.port.worldboss.WorldBossEnvironment","cn.academy.port.worldboss.WorldBossEnvironmentData","cn.academy.port.worldboss.WorldBossPresentation","cn.academy.port.worldboss.Clips","cn.academy.port.worldboss.FxPacket")))throw new AssertionError("World Boss common inventory is cold-linked, never skipped: "+bossCommon);
        if(classes<40)throw new AssertionError("Unexpectedly small common class inventory: "+classes);
        System.out.println("PASS "+classes+" common classes cold-linked with client namespaces denied; no Minecraft startup");
    }
}
