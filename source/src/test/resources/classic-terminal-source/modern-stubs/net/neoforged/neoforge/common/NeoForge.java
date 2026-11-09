package net.neoforged.neoforge.common;
import java.util.*;public class NeoForge {public static final Bus EVENT_BUS=new Bus();public static class Bus {public final List<Object> events=new ArrayList<>();public boolean post(Object e){events.add(e);return false;}}}
