package cn.academy.port.terminal;
import java.util.*;import net.minecraft.server.level.ServerPlayer;public class TerminalFrequencySessions {public static final Set<ServerPlayer> sessions=Collections.newSetFromMap(new IdentityHashMap<>());public static void remove(ServerPlayer p){sessions.remove(p);}public static void clear(){sessions.clear();}}
