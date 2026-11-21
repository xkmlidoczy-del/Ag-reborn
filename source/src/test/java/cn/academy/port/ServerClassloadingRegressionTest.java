package cn.academy.port;
import java.net.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
/** Static/cold linkage check, never constructs or starts Minecraft or accepts an agreement. */
public final class ServerClassloadingRegressionTest {
    private static boolean clientPackage(String name) {
        return name.startsWith("cn.academy.port.") && name.contains(".client.");
    }
    private static String outer(String name) {
        int split = name.indexOf('$');
        return split < 0 ? name : name.substring(0, split);
    }

    private static boolean outsideServerInventory(Path file, String name, Set<String> commonMixins) throws IOException {
        var node = new org.objectweb.asm.tree.ClassNode();
        new org.objectweb.asm.ClassReader(Files.readAllBytes(file)).accept(node,
                org.objectweb.asm.ClassReader.SKIP_CODE | org.objectweb.asm.ClassReader.SKIP_DEBUG | org.objectweb.asm.ClassReader.SKIP_FRAMES);
        if (!commonMixins.contains(name) && node.invisibleAnnotations != null
                && node.invisibleAnnotations.stream().anyMatch(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;"))) return true;
        if (node.visibleAnnotations == null) return false;
        for (var annotation : node.visibleAnnotations) {
            if (!annotation.desc.equals("Lnet/neoforged/fml/common/EventBusSubscriber;") || annotation.values == null) continue;
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (annotation.values.get(i).equals("value") && annotation.values.get(i + 1) instanceof List<?> dist
                        && dist.size() == 1 && dist.get(0) instanceof String[] side && side[1].equals("CLIENT")) return true;
            }
        }
        return false;
    }

    private static Set<String> serverMixins(Path resources) throws IOException {
        var classes = new HashSet<String>();
        try (var files = Files.list(resources)) {
            for (var file : files.filter(p -> p.toString().endsWith(".mixins.json")).toList()) {
                var config = com.google.gson.JsonParser.parseString(Files.readString(file)).getAsJsonObject();
                for (var side : List.of("mixins", "server")) {
                    if (config.has(side)) for (var entry : config.getAsJsonArray(side)) {
                        classes.add(config.get("package").getAsString() + "." + entry.getAsString());
                    }
                }
            }
        }
        return classes;
    }

    private static final class ServerOnlyLoader extends URLClassLoader {
        int clientRequests;
        private final Set<String> sided;
        ServerOnlyLoader(URL[] urls, Set<String> sided){super(urls,ClassLoader.getPlatformClassLoader());this.sided=sided;}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(name.startsWith("net.minecraft.client.")||(name.startsWith("net.neoforged.neoforge.client.") && !name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension"))||clientPackage(name)||sided.contains(outer(name))){clientRequests++;throw new ClassNotFoundException("Client namespace denied for server-link test: "+name);}
            return super.loadClass(name,resolve);
        }
    }
    public static void main(String[] args)throws Exception {
        var entries=System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator));var urls=new ArrayList<URL>();for(String entry:entries)urls.add(Path.of(entry).toUri().toURL());
        int classes=0;var bossCommon=new HashSet<String>();
        Path root=Path.of(System.getProperty("academy.qa.mainClasses","build/classes/java/main")).resolve("cn/academy/port");
        Path resources=Path.of(System.getProperty("academy.qa.mainResources","build/resources/main"));
        var commonMixins=serverMixins(resources);
        var sided=new HashSet<String>();
        var inventory=new ArrayList<String>();
        try(var files=Files.walk(root)){
            for(var file:files.filter(p->p.toString().endsWith(".class")).toList()){
                String relative=root.relativize(file).toString().replace(File.separatorChar,'.');
                if(relative.startsWith("gametest.")||relative.startsWith("qa."))continue;
                String name="cn.academy.port."+relative.substring(0,relative.length()-6);
                if (clientPackage(name)) continue;
                if (!name.contains("$") && outsideServerInventory(file,name,commonMixins)) sided.add(name);
                inventory.add(name);
            }
        }
        try(var loader=new ServerOnlyLoader(urls.toArray(URL[]::new),sided)){
            for(String name:inventory){
                if (sided.contains(outer(name))) continue;
                try {
                    Class<?> type=Class.forName(name,false,loader);
                    type.getDeclaredConstructors();type.getDeclaredMethods();type.getDeclaredFields();classes++;
                } catch (LinkageError failed) {
                    throw new AssertionError("Server linkage failed for "+name, failed);
                }
                if(name.startsWith("cn.academy.port.worldboss."))bossCommon.add(name);
            }
            if(loader.clientRequests!=0)throw new AssertionError("Server cold-link requested "+loader.clientRequests+" client classes");
        }
        if(!bossCommon.containsAll(Set.of("cn.academy.port.worldboss.WorldBossContent","cn.academy.port.worldboss.WorldBossContent$Boss","cn.academy.port.worldboss.WorldBossCombat","cn.academy.port.worldboss.WorldBossEnvironment","cn.academy.port.worldboss.WorldBossEnvironmentData","cn.academy.port.worldboss.WorldBossPresentation","cn.academy.port.worldboss.Clips","cn.academy.port.worldboss.FxPacket")))throw new AssertionError("World Boss common inventory is cold-linked, never skipped: "+bossCommon);
        if(classes<40)throw new AssertionError("Unexpectedly small common class inventory: "+classes);
        System.out.println("PASS "+classes+" common classes cold-linked with client namespaces denied; no Minecraft startup");
    }
}
