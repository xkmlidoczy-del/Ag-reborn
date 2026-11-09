package cn.academy.port.achievements;
import java.net.*;
import java.nio.file.*;
import java.io.File;
/** Fresh common classloader actively rejects every client/render/UI namespace. */
public final class ClassicAchievementColdlinkTest {
    public static void main(String[] args)throws Exception{
        var paths=System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(File.pathSeparator));
        var urls=new URL[paths.length];for(int i=0;i<paths.length;i++)urls[i]=Path.of(paths[i]).toUri().toURL();
        try(var loader=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(name.startsWith("net.minecraft.client.")||name.startsWith("cn.academy.port.client.")||name.startsWith("com.mojang.blaze3d.")||name.startsWith("org.lwjgl."))throw new ClassNotFoundException("client denied: "+name);
                return super.loadClass(name,resolve);
            }
        }){
            for(String name:java.util.List.of("cn.academy.port.achievements.ClassicAchievementCatalog","cn.academy.port.achievements.ClassicAchievements","cn.academy.port.core.AbilityProgress")){
                var type=Class.forName(name,true,loader);type.getDeclaredMethods();type.getDeclaredConstructors();
            }
            var type=Class.forName("cn.academy.port.core.AbilityProgress",true,loader);var state=type.getConstructor().newInstance();
            type.getMethod("selectCategory",String.class).invoke(state,"meltdowner");type.getMethod("setLevel",int.class).invoke(state,1);type.getMethod("learn",String.class).invoke(state,"rad_intensify");
        }
        System.out.println("Achievement common module and progression actively coldlinked with client/render namespaces denied");
    }
}
