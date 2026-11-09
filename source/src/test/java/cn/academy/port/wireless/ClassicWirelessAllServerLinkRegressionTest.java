package cn.academy.port.wireless;

import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

/** All staged common runtime classes, without bootstrap/creation/client entry. */
public final class ClassicWirelessAllServerLinkRegressionTest {
    public static void main(String[] args)throws Exception{
        String[] entries=System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator));var urls=new java.net.URL[entries.length];for(int i=0;i<entries.length;i++)urls[i]=Path.of(entries[i]).toUri().toURL();
        int classes=0;try(var loader=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith("net.minecraft.client.")||name.startsWith("cn.academy.port.client.")||name.startsWith("com.mojang.blaze3d.")||name.startsWith("net.neoforged.neoforge.client.")&&!name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension"))throw new ClassNotFoundException("Real client class forbidden "+name);return super.loadClass(name,resolve);}
        }){
            var mainRoot=Path.of(ClassicWirelessRules.class.getProtectionDomain().getCodeSource().getLocation().toURI());var packageRoot=mainRoot.resolve("cn/academy/port/wireless");try(var files=Files.walk(packageRoot)){for(var file:files.filter(path->path.toString().endsWith(".class")).toList()){String name="cn.academy.port.wireless."+packageRoot.relativize(file).toString().replace(java.io.File.separatorChar,'.').replaceAll("\\.class$","");var type=Class.forName(name,false,loader);type.getDeclaredConstructors();type.getDeclaredMethods();type.getDeclaredFields();classes++;}}
            for(String name:new String[]{"cn.academy.port.solar.ClassicSolarMenu","cn.academy.port.machine.MachineDeveloperSessions","cn.academy.port.fusion.ClassicFusorMenu"}){var type=Class.forName(name,false,loader);type.getDeclaredMethods();type.getDeclaredFields();classes++;}
        }
        if(classes<25)throw new AssertionError("Too few staged common classes "+classes);System.out.println("ClassicWirelessAllServerLinkRegressionTest: "+classes+" common/integration classes cold-linked; real clients forbidden, exact common engine menu extension allowed");
    }
}
