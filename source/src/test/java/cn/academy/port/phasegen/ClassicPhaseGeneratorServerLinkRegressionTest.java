package cn.academy.port.phasegen;

import java.net.URLClassLoader;

/** Dedicated-server linkage with every real client namespace denied. */
public final class ClassicPhaseGeneratorServerLinkRegressionTest {
    public static void main(String[] args)throws Exception{
        var paths=System.getProperty("java.class.path").split(java.io.File.pathSeparator);var urls=new java.net.URL[paths.length];for(int i=0;i<paths.length;i++)urls[i]=java.nio.file.Path.of(paths[i]).toUri().toURL();
        try(var loader=new URLClassLoader(urls,ClassLoader.getPlatformClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{
                if(name.startsWith("net.minecraft.client.")||name.startsWith("net.neoforged.neoforge.client.")&&!name.equals("net.neoforged.neoforge.client.extensions.IMenuProviderExtension")||name.startsWith("cn.academy.port.client.")||name.startsWith("com.mojang.blaze3d."))throw new ClassNotFoundException("Client class forbidden: "+name);
                return super.loadClass(name,resolve);
            }
        }){
            String[] names={"ClassicPhaseGeneratorRules","ClassicPhaseGeneratorBuffer","ClassicPhaseGeneratorBuffer$ChargeReceiver","ClassicPhaseGenerators","ClassicPhaseGeneratorBlock","ClassicPhaseGeneratorBlockEntity","ClassicPhaseGeneratorBlockEntity$1","ClassicPhaseGeneratorBlockEntity$2","ClassicPhaseGeneratorMenu","ClassicPhaseGeneratorMenu$1","ClassicPhaseGeneratorMenu$2","ClassicPhaseGeneratorMenu$3"};
            for(String name:names){var type=Class.forName("cn.academy.port.phasegen."+name,false,loader);type.getDeclaredFields();type.getDeclaredMethods();type.getDeclaredConstructors();}
            for(String name:new String[]{"cn.academy.port.solar.ImagFluxGenerator","cn.academy.port.wireless.ClassicWirelessProtocol","cn.academy.port.gametest.AcademyPhaseGeneratorRuntimeTests","cn.academy.port.gametest.AcademyPhaseGeneratorRestartRuntimeTests"}){var type=Class.forName(name,false,loader);type.getDeclaredFields();type.getDeclaredMethods();type.getDeclaredConstructors();}
            System.out.println("ClassicPhaseGeneratorServerLinkRegressionTest: "+(names.length+4)+" common/native classes cold-linked with client namespaces denied; engine IMenuProviderExtension exception only");
        }
    }
}
