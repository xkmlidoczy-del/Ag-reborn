package cn.academy.port.terminal;
import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
/** All actual common descriptors link with client namespaces unavailable, without initializing a game. */
public final class TerminalServerLinkTest {
    private static final class CommonOnly extends URLClassLoader {
        int clientRequests;
        CommonOnly(URL[] urls){super(urls,ClassLoader.getPlatformClassLoader());}
        @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
            if(name.startsWith("net.minecraft.client.")||name.startsWith("cn.academy.port.client.")||(name.startsWith("net.neoforged.neoforge.client.")&&!name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension"))){clientRequests++;throw new ClassNotFoundException("Client namespace denied: "+name);}
            return super.loadClass(name,resolve);
        }
    }
    public static void main(String[] arguments)throws Exception{
        var urls=new ArrayList<URL>();for(var entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))urls.add(Path.of(entry).toUri().toURL());
        var names=ListNames.NAMES;try(var loader=new CommonOnly(urls.toArray(URL[]::new))){
            for(var name:names){var type=Class.forName(name,false,loader);type.getDeclaredConstructors();type.getDeclaredMethods();type.getDeclaredFields();}
            if(loader.clientRequests!=0)throw new AssertionError("Common terminal requested client namespaces");
        }
        System.out.println("PASS "+names.length+" terminal/common integration classes cold-linked with client namespaces denied; no game startup");
    }
    private static final class ListNames {
        static final String[] NAMES={"cn.academy.port.terminal.TerminalState","cn.academy.port.terminal.TerminalStorage","cn.academy.port.terminal.TerminalInstalledEvent","cn.academy.port.terminal.AppInstalledEvent","cn.academy.port.terminal.TerminalInstallerItem","cn.academy.port.terminal.TerminalAppItem","cn.academy.port.terminal.TerminalModule","cn.academy.port.terminal.TerminalEvents","cn.academy.port.terminal.TerminalNetwork","cn.academy.port.terminal.TerminalNetwork$FrequencyRequest","cn.academy.port.terminal.TerminalFrequencySessions","cn.academy.port.terminal.TerminalFrequencySessions$Session","cn.academy.port.AcademyCraft","cn.academy.port.AcademyNetwork","cn.academy.port.tutorial.TutorialNetwork"};
    }
}
