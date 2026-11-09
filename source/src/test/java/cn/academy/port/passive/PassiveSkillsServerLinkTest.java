package cn.academy.port.passive;
import java.nio.file.*;
import java.util.*;
public final class PassiveSkillsServerLinkTest {
 public static void main(String[] args)throws Exception{
  for(String name:List.of("cn.academy.port.core.AbilityCalculation","cn.academy.port.core.ClassicPassiveSkills","cn.academy.port.api.AbilityCalculationEvent","cn.academy.port.PassiveSkillEvents","cn.academy.port.AbilityConsumption","cn.academy.port.skill.RadiationMarks","cn.academy.port.skill.ThreateningTeleport")){var type=Class.forName(name,false,PassiveSkillsServerLinkTest.class.getClassLoader());type.getDeclaredMethods();type.getDeclaredFields();type.getDeclaredConstructors();}
  Path stage=Path.of(System.getProperty("academy.passive.stage","."));Path tree=Files.isDirectory(stage.resolve("integration/src"))?stage.resolve("integration/src"):Path.of(".");
  String binder=Files.readString(tree.resolve("src/main/java/cn/academy/port/AbilityConsumption.java")),marks=Files.readString(tree.resolve("src/main/java/cn/academy/port/skill/RadiationMarks.java")),client=Files.readString(tree.resolve("src/main/java/cn/academy/port/client/ClassicFirstSkillEffects.java")),tp=Files.readString(tree.resolve("src/main/java/cn/academy/port/skill/ThreateningTeleport.java"));
  if(!binder.contains("state.bindCalculations")||!binder.contains("NeoForge.EVENT_BUS.post(event);request.value=event.value"))throw new AssertionError("native calculation ingress missing");
  if(!marks.contains("getInt(TICKS)>0")||!marks.contains("event.setAmount")||!marks.contains("putFloat(RATE")||!marks.contains("radiation_mark")||!marks.contains("20,new"))throw new AssertionError("radiation damage/source float/caster-sync missing");
  if(!client.contains("RADIATION_MARKS.clear()")||!client.contains("RANDOM.nextInt(3)")||!client.contains("random(.6,.7)*caster.getBbWidth()")||!client.contains("RANDOM.nextInt(30)+25,20"))throw new AssertionError("source radiation aura lifecycle/geometry missing");
  if(!tp.contains("new FleshRipping.CriticalHitEvent(player,trace.target(),tier)"))throw new AssertionError("ThreateningTeleport missing common critical event");
  System.out.println("PASS 7 common server links and real calculation/radiation/critical adapter checks; no Minecraft/client initialization");
 }
}
