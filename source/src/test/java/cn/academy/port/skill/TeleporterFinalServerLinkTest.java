package cn.academy.port.skill;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Actual reflection linkage with client namespaces denied, including common native-fixture signatures.
 * Classes are cold-loaded without static initialization; this is not Minecraft server bootstrap. */
public final class TeleporterFinalServerLinkTest {
    private static final class ServerLoader extends URLClassLoader {
        private int denied;
        private ServerLoader(URL[] urls) {super(urls, ClassLoader.getPlatformClassLoader());}
        @Override protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (name.startsWith("net.minecraft.client.") || name.startsWith("net.neoforged.neoforge.client.") || name.startsWith("cn.academy.port.client.")) {
                denied++;
                throw new ClassNotFoundException("Dedicated-server linkage denies " + name);
            }
            return super.loadClass(name, resolve);
        }
    }

    public static void main(String[] args) throws Exception {
        var urls = new ArrayList<URL>();
        for (String entry : System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))) urls.add(Path.of(entry).toUri().toURL());
        Set<String> examined = new HashSet<>();
        try (var loader = new ServerLoader(urls.toArray(URL[]::new))) {
            for (String name : List.of(
                    "cn.academy.port.skill.TeleporterFinalRules", "cn.academy.port.skill.TeleporterFinalSupport",
                    "cn.academy.port.skill.LocationTeleport", "cn.academy.port.skill.ShiftTeleport", "cn.academy.port.skill.Flashing",
                    "cn.academy.port.clientless.ClassicLateTrig",
                    "cn.academy.port.AcademyGameplay",
                    "cn.academy.port.AcademyCraft", "cn.academy.port.AcademyNetwork", "cn.academy.port.SkillCatalog",
                    "cn.academy.port.SkillAvailability", "cn.academy.port.preset.PresetSkills",
                    "cn.academy.port.core.AbilityProgress", "cn.academy.port.core.SkillPresets",
                    "cn.academy.port.develop.DevelopmentController", "cn.academy.port.develop.DevelopmentActions",
                    "cn.academy.port.develop.DevelopmentProcess", "cn.academy.port.develop.DeveloperType",
                    "cn.academy.port.gametest.AcademyTeleporterFinalRuntimeTests")) {
                var queue = new ArrayDeque<Class<?>>();
                queue.add(Class.forName(name, false, loader));
                while (!queue.isEmpty()) {
                    Class<?> type = queue.remove();
                    if (!examined.add(type.getName())) continue;
                    type.getDeclaredConstructors();
                    type.getDeclaredMethods();
                    type.getDeclaredFields();
                    queue.addAll(List.of(type.getDeclaredClasses()));
                }
            }
            if (loader.denied != 0) throw new AssertionError("Common final Teleporter classes requested client namespaces " + loader.denied + " times");
        }
        System.out.println("PASS " + examined.size() + " final Teleporter, native-fixture, registry, gameplay and learning classes cold-linked with client namespaces denied");
    }
}
