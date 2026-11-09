package cn.academy.port.solar;

import java.net.URLClassLoader;

public final class ClassicSolarServerLinkRegressionTest {
    public static void main(String[] args)throws Exception {
        var paths=System.getProperty("java.class.path").split(java.io.File.pathSeparator);
        var urls=new java.net.URL[paths.length];for(int i=0;i<paths.length;i++)urls[i]=java.nio.file.Path.of(paths[i]).toUri().toURL();
        try(var loader=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()) {
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException {
                if(name.startsWith("net.minecraft.client.")||name.startsWith("net.neoforged.neoforge.client.") && !name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension")||name.startsWith("cn.academy.port.client.")||name.startsWith("com.mojang.blaze3d."))throw new ClassNotFoundException("Client class forbidden: "+name);
                return super.loadClass(name,resolve);
            }
        }) {
            String[] classes={"ClassicSolarRules","ClassicSolarRules$Status","ClassicSolarBuffer","ClassicSolarBuffer$Charger","ImagFluxGenerator","ClassicSolarGenerators","ClassicSolarBlock","ClassicSolarBlockEntity","ClassicSolarBlockEntity$1","ClassicSolarMenu","ClassicSolarMenu$1"};
            for(String name:classes){var type=Class.forName("cn.academy.port.solar."+name,false,loader);type.getDeclaredFields();type.getDeclaredMethods();type.getDeclaredConstructors();}
            System.out.println("ClassicSolarServerLinkRegressionTest: "+classes.length+" common classes cold-linked with real client namespaces denied (exact common IMenuProviderExtension engine exception)");
        }
    }
}
