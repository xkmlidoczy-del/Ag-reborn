package net.minecraftforge.common;
import java.util.*; public class MinecraftForge {public static final Bus EVENT_BUS=new Bus(); public static class Bus {public final List<Object> events=new ArrayList<>();public java.util.function.Predicate<Object> canceled=e->false;public boolean post(Object e){events.add(e);return canceled.test(e);}}}
