package cn.academy.port.tutorial;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;

public final class TutorialServerLinkTest {
    private static final class CommonOnly extends URLClassLoader {
        int clientRequests;
        CommonOnly(URL[] urls){super(urls,ClassLoader.getPlatformClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
            if(name.startsWith("net.minecraft.client.")||name.startsWith("cn.academy.port.client.")||(name.startsWith("net.neoforged.neoforge.client.")&&!name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension"))) {
                clientRequests++;throw new ClassNotFoundException("Client class denied: "+name);
            }
            return super.loadClass(name,resolve);
        }
    }
    public static void main(String[] args)throws Exception {
        var urls=new ArrayList<URL>();for(var entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))urls.add(Path.of(entry).toUri().toURL());
        String[] names={"ClassicTutorials","ClassicTutorials$Page","ClassicTutorials$Preview","TutorialState","TutorialState$EventKind","TutorialStorage","TutorialActivatedEvent","TutorialEvents","TutorialNetwork","TutorialModule","TutorialItem","AcademyTutorialConfig"};
        try(var loader=new CommonOnly(urls.toArray(URL[]::new))) {
            for(var name:names){var type=Class.forName("cn.academy.port.tutorial."+name,false,loader);type.getDeclaredConstructors();type.getDeclaredMethods();type.getDeclaredFields();}
            for(var name:new String[]{"cn.academy.port.AcademyCraft","cn.academy.port.AcademyNetwork"}){var type=Class.forName(name,false,loader);type.getDeclaredConstructors();type.getDeclaredMethods();type.getDeclaredFields();}
            if(loader.clientRequests!=0)throw new AssertionError("Guide common classes requested client namespace");
        }
        System.out.println("PASS 14 tutorial/common integration classes cold-linked with client namespaces denied; no game startup");
    }
}
