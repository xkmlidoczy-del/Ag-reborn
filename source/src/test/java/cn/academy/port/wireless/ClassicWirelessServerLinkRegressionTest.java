package cn.academy.port.wireless;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;

/** Cold-link common graph/native adapter classes without initializing or launching Minecraft. */
public final class ClassicWirelessServerLinkRegressionTest {
    public static void main(String[] args) throws Exception {
        var urls=new ArrayList<URL>();for(String entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator)))urls.add(Path.of(entry).toUri().toURL());
        String[] types={"ImagFluxNode","ImagFluxMatrix","ClassicWirelessGraph","ClassicWirelessGraph$Pos","ClassicWirelessGraph$Resolver","ClassicWirelessGraph$NetworkSnapshot","ClassicWirelessGraph$NodeSnapshot","ClassicWirelessGraph$NetworkData","ClassicWirelessGraph$ConnectionData","ClassicWirelessGraph$State","ClassicWirelessGraph$Network","ClassicWirelessGraph$Connection","ClassicWirelessSavedData","ClassicWirelessSavedData$NativeResolver","ClassicWirelessSavedData$1","ClassicWirelessSystem"};
        try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(name.startsWith("net.minecraft.client.")||name.startsWith("net.neoforged.neoforge.client.")||name.startsWith("cn.academy.port.client.")||name.startsWith("com.mojang.blaze3d."))throw new ClassNotFoundException("Client class forbidden: "+name);
                return super.loadClass(name,resolve);
            }
        }){
            for(String name:types){var type=Class.forName("cn.academy.port.wireless."+name,false,loader);type.getDeclaredFields();type.getDeclaredMethods();type.getDeclaredConstructors();}
        }
        System.out.println("ClassicWirelessServerLinkRegressionTest: "+types.length+" common graph/native adapter classes cold-linked with client namespaces denied, no game startup");
    }
}
