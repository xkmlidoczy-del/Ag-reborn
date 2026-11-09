package cn.academy.port.former;
import java.net.URLClassLoader;
/** Dedicated-server cold linkage excludes all client implementation namespaces. */
public final class ClassicMetalFormerServerLinkTest {
    public static void main(String[] args)throws Exception{
        var paths=System.getProperty("java.class.path").split(java.io.File.pathSeparator);var urls=new java.net.URL[paths.length];for(int i=0;i<paths.length;i++)urls[i]=java.nio.file.Path.of(paths[i]).toUri().toURL();
        try(var loader=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(name.startsWith("net.minecraft.client.")||name.startsWith("net.neoforged.neoforge.client.")&&!name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension")||name.startsWith("cn.academy.port.client.")||name.startsWith("com.mojang.blaze3d."))throw new ClassNotFoundException("Client class forbidden: "+name);
                return super.loadClass(name,resolve);
            }
        }){
            String[] names={"ClassicMetalFormer","ClassicMetalFormerBlock","ClassicMetalFormerBlockEntity","ClassicMetalFormerBlockEntity$1","ClassicMetalFormerBlockEntity$2","ClassicMetalFormerMenu","ClassicMetalFormerMenu$1","ClassicMetalFormerMenu$2","ClassicMetalFormerMenu$3","ClassicMetalFormerRecipes","ClassicMetalFormerRecipes$Recipe","ClassicMetalFormerRules","ClassicMetalFormerRules$Rule","ClassicMetalFormerWork","ClassicMetalFormerWork$Mode","ClassicMetalFormerWork$Access"};
            for(String name:names){var type=Class.forName("cn.academy.port.former."+name,false,loader);type.getDeclaredFields();type.getDeclaredMethods();type.getDeclaredConstructors();}
            var wireless=Class.forName("cn.academy.port.wireless.ClassicWirelessProtocol",false,loader);wireless.getDeclaredFields();wireless.getDeclaredMethods();wireless.getDeclaredConstructors();
            System.out.println("ClassicMetalFormerServerLinkTest: "+names.length+" common classes + wireless protocol cold-linked with real client namespaces denied (common engine IMenuProviderExtension exception)");
        }
    }
}
