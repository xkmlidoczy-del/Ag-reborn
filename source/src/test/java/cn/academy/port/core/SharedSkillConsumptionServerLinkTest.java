package cn.academy.port.core;
import java.net.*;
import java.nio.file.*;
import java.io.*;
import java.util.*;
public final class SharedSkillConsumptionServerLinkTest {
    public static void main(String[] arguments)throws Exception{
        var urls=new ArrayList<URL>();for(String part:System.getProperty("java.class.path").split(File.pathSeparator))urls.add(Path.of(part).toUri().toURL());
        try(var loader=new URLClassLoader(urls.toArray(URL[]::new),ClassLoader.getPlatformClassLoader()){
            @Override protected Class<?> loadClass(String name,boolean resolve)throws ClassNotFoundException{if(name.startsWith("net.minecraft.client.")||name.startsWith("cn.academy.port.client.")||name.startsWith("com.mojang.blaze3d.")||name.startsWith("org.lwjgl."))throw new ClassNotFoundException("client-denied "+name);return super.loadClass(name,resolve);}
        }){
            String[] classes={"core.SkillConsumption","core.SkillConsumption$Config","core.SkillConsumption$Request","core.AbilityProgress","core.CurrentChargingSession","skill.AdvancedMineRaySession","skill.BodyIntensifySession","skill.ThunderClapSession","skill.LightShieldSession","skill.MineRayBasicSession","skill.MeltdownerSession","skill.ScatterBombSession","skill.ElectronMissileSession","skill.JetEngineSession","skill.VecAccelSession","skill.VecDeviationSession","skill.TeleporterProgressionRules","skill.VecReflectionSession","skill.PlasmaCannonSession","skill.DirectedBlastwaveSession","skill.StormWingSession","skill.BloodRetrogradeSession","api.SkillPerformEvent","api.AbilityOverloadEvent","AbilityConsumption"};
            for(String name:classes){var type=Class.forName("cn.academy.port."+name,false,loader);type.getDeclaredFields();type.getDeclaredMethods();type.getDeclaredConstructors();}
            var type=Class.forName("cn.academy.port.core.AbilityProgress",true,loader);Object state=type.getConstructor().newInstance();type.getMethod("consumeSkill",String.class,double.class,double.class,boolean.class).invoke(state,"",1D,1D,false);
            System.out.println("PASS "+classes.length+" common classes coldlink with client/Blaze3D/LWJGL denied and isolated core execution");
        }
    }
}
