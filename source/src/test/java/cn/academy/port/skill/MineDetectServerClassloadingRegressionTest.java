package cn.academy.port.skill;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;

/** Cold-links staged common classes with client namespaces denied; never initializes Minecraft. */
public final class MineDetectServerClassloadingRegressionTest {
    private static final class ServerOnlyLoader extends URLClassLoader {
        int clientRequests;
        ServerOnlyLoader(URL[] urls) { super(urls, ClassLoader.getPlatformClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.") || name.startsWith("net.neoforged.neoforge.client.")
                    || name.startsWith("cn.academy.port.client.")) {
                clientRequests++;
                throw new ClassNotFoundException("Client namespace denied: " + name);
            }
            return super.loadClass(name, resolve);
        }
    }
    public static void main(String[] args) throws Exception {
        var urls = new ArrayList<URL>();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
            urls.add(Path.of(entry).toUri().toURL());
        String[] types = {"cn.academy.port.skill.MineDetect", "cn.academy.port.skill.MineDetectRules",
                "cn.academy.port.skill.MineDetectRules$Plan", "cn.academy.port.skill.ClassicMineScan",
                "cn.academy.port.skill.ClassicMineScan$Handler", "cn.academy.port.skill.ClassicMineScan$OreAccess",
                "cn.academy.port.skill.ClassicMineScan$Element", "cn.academy.port.skill.ClassicMineOreAdapter", "cn.academy.port.skill.ClassicMineOreAdapter$1", "cn.academy.port.skill.MineOreTagRules"};
        try (var loader = new ServerOnlyLoader(urls.toArray(URL[]::new))) {
            for (String name : types) {
                Class<?> type = Class.forName(name, false, loader);
                type.getDeclaredConstructors(); type.getDeclaredMethods(); type.getDeclaredFields();
            }
            if (loader.clientRequests != 0) throw new AssertionError("Common MineDetect requested client classes");
        }
        System.out.println("PASS 10 MineDetect common classes cold-linked with client namespaces denied (no game startup)");
    }
}
