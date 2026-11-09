package cn.academy.misc.tutorial;
public final class OracleSupport {
 public static cpw.mods.fml.relauncher.Side side=cpw.mods.fml.relauncher.Side.SERVER;
 public static final java.util.Map<String,net.minecraft.item.Item> items=new java.util.LinkedHashMap<>();
 public static final java.util.List<String> logs=new java.util.ArrayList<>();
 public static final Bus BUS=new Bus();
 public static final class Bus {public void register(Object listener){}public boolean post(Object event){if(event instanceof TutorialActivatedEvent activation)logs.add("activate:"+activation.tutorial.id);return false;}}
}
