package cn.academy.port.energy;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;

/** Cold common-side linkage rejects all client namespaces; no Minecraft bootstrap or instance creation. */
public final class ClassicEnergyServerLinkRegressionTest {
    private static final class ServerOnlyLoader extends URLClassLoader {
        int clientRequests;
        ServerOnlyLoader(URL[] urls) { super(urls, ClassLoader.getPlatformClassLoader()); }
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.") || (name.startsWith("net.neoforged.neoforge.client.") && !name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension")) || name.startsWith("cn.academy.port.client.")) {
                clientRequests++; throw new ClassNotFoundException("Client namespace denied for server energy-link test: " + name);
            }
            return super.loadClass(name, resolve);
        }
    }
    public static void main(String[] args) throws Exception {
        var urls = new ArrayList<URL>();
        for (String entry : System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator))) urls.add(Path.of(entry).toUri().toURL());
        String[] names = {"ImagEnergyItem", "ClassicEnergy", "ClassicEnergy$Access", "ClassicItemEnergy", "ClassicForgeEnergyStorage",
                "ClassicEnergyItemHelper", "ClassicEnergyItemHelper$1", "ClassicEnergyUnitItem", "ClassicEnergyItems", "ClassicEnergyRecipes", "ClassicEnergyUnitRecipe",
                "ClassicEnergyUnitRecipe$Serializer", "ClassicEnergyUnitRecipe$Serializer$1"};
        int count = 0;
        try (var loader = new ServerOnlyLoader(urls.toArray(URL[]::new))) {
            for (String name : names) {
                var type = Class.forName("cn.academy.port.energy." + name, false, loader);
                type.getDeclaredConstructors(); type.getDeclaredMethods(); type.getDeclaredFields(); count++;
            }
            for (String name : new String[] {"cn.academy.port.AcademyCraft", "cn.academy.port.skill.ChargingEnergy"}) {
                var type = Class.forName(name, false, loader);
                type.getDeclaredConstructors(); type.getDeclaredMethods(); type.getDeclaredFields(); count++;
            }
            if (loader.clientRequests != 0) throw new AssertionError("Energy common linkage requested client classes");
        }
        System.out.println("PASS " + count + " energy/common integration classes cold-linked with client namespaces denied; no Minecraft startup");
    }
}
