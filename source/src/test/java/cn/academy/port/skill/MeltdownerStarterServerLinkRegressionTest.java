package cn.academy.port.skill;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;

/** Cold dedicated-side linkage only. No Minecraft, world, client or audio process is started. */
public final class MeltdownerStarterServerLinkRegressionTest {
    private static final class ServerOnlyLoader extends URLClassLoader {
        int clientRequests;
        ServerOnlyLoader(URL[] urls) { super(urls, ClassLoader.getPlatformClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.") || name.startsWith("net.neoforged.neoforge.client.") || name.startsWith("cn.academy.port.client.")) {
                clientRequests++; throw new ClassNotFoundException("Denied dedicated-server client class: " + name);
            }
            return super.loadClass(name, resolve);
        }
    }
    public static void main(String[] args) throws Exception {
        var urls = new ArrayList<URL>();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) urls.add(Path.of(entry).toUri().toURL());
        String[] types = {"ScatterBomb", "ScatterBomb$Hold", "ScatterBomb$Ball", "ScatterBombSession", "ScatterBombSession$Tick", "LightShield", "LightShield$Hold", "LightShieldSession", "MeltdownerStarterSupport", "ScatterBombAim", "ScatterBombAim$Direction", "RadiationMarks"};
        try (var loader = new ServerOnlyLoader(urls.toArray(URL[]::new))) {
            for (String name : types) { Class<?> type = Class.forName("cn.academy.port.skill." + name, false, loader); type.getDeclaredConstructors(); type.getDeclaredMethods(); type.getDeclaredFields(); }
            if (loader.clientRequests != 0) throw new AssertionError("Meltdowner common classes requested client namespaces");
        }
        System.out.println("PASS 12 Meltdowner starter common classes cold-linked with client namespaces denied");
    }
}
