package cn.academy.port.energy;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;

/** Cold common-side linkage, without starting Minecraft or initializing registries. */
public final class ClassicNativeBlockChargingServerLinkTest {
    private static final class ServerOnlyLoader extends URLClassLoader {
        int clientRequests;
        ServerOnlyLoader(URL[] urls) { super(urls, ClassLoader.getPlatformClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.") || name.startsWith("cn.academy.port.client.")
                    || (name.startsWith("net.neoforged.neoforge.client.")
                    && !name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension"))) {
                clientRequests++; throw new ClassNotFoundException("Client class denied: " + name);
            }
            return super.loadClass(name, resolve);
        }
    }
    public static void main(String[] args) throws Exception {
        var urls = new ArrayList<URL>();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))
            urls.add(Path.of(entry).toUri().toURL());
        String[] classes = {"cn.academy.port.energy.ClassicIFNodeManager", "cn.academy.port.energy.ClassicIFReceiverManager",
                "cn.academy.port.energy.ClassicEnergyBlockHelper", "cn.academy.port.skill.ChargingEnergy",
                "cn.academy.port.skill.ChargingEnergy$BlockAccess", "cn.academy.port.skill.ChargingEnergy$BlockTarget",
                "cn.academy.port.skill.ChargingEnergy$Target", "cn.academy.port.wireless.ImagFluxNode",
                "cn.academy.port.machine.ImagFluxReceiver"};
        try (var loader = new ServerOnlyLoader(urls.toArray(URL[]::new))) {
            for (String name : classes) {
                var type = Class.forName(name, false, loader);
                type.getDeclaredConstructors(); type.getDeclaredMethods(); type.getDeclaredFields();
            }
            if (loader.clientRequests != 0) throw new AssertionError("Block charging requested client classes");
        }
        System.out.println("PASS " + classes.length + " native block charging classes cold-linked with actual client namespaces denied");
    }
}
