package cn.academy.port.skill;
import java.io.File;import java.net.*;import java.nio.file.Path;import java.util.*;
/** Common coldlinks, deliberately denies all client namespaces. This is not native startup. */
public final class AdvancedMineRayServerLinkRegressionTest {
    private static final class ServerLoader extends URLClassLoader {int denied;ServerLoader(URL[] urls){super(urls,ClassLoader.getPlatformClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(name.startsWith("net.minecraft.client.")||name.startsWith("net.neoforged.neoforge.client.")||name.startsWith("cn.academy.port.client.")){denied++;throw new ClassNotFoundException(name);}return super.loadClass(name,resolve);
        }
    }
    public static void main(String[] args)throws Exception {
        var urls=new ArrayList<URL>();for(var item:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))urls.add(Path.of(item).toUri().toURL());int count=0;
        try(var loader=new ServerLoader(urls.toArray(URL[]::new))) {
            for(String name:List.of("cn.academy.port.skill.AdvancedMineRaySession","cn.academy.port.skill.AdvancedMineRay","cn.academy.port.skill.MineRayExpert","cn.academy.port.skill.MineRayLuck","cn.academy.port.skill.MeltdownerBeamSupport","cn.academy.port.SkillAvailability","cn.academy.port.preset.PresetSkills")) {
                var queue=new ArrayDeque<Class<?>>();queue.add(Class.forName(name,false,loader));while(!queue.isEmpty()){var type=queue.remove();type.getDeclaredConstructors();type.getDeclaredFields();type.getDeclaredMethods();queue.addAll(List.of(type.getDeclaredClasses()));count++;}
            }
            if(loader.denied!=0)throw new AssertionError("Common classes requested client linkage");
        }
        System.out.println("PASS "+count+" advanced mining common classes coldlinked with client namespaces denied");
    }
}
