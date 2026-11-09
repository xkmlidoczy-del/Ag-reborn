/* AcademyCraft 1.0.7 TerminalData persistent-player adapter. GPLv3. See NOTICE. */
package cn.academy.port.terminal;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/** New modern save schema; does not reinterpret old dynamically ordered app bit indices. */
public final class TerminalStorage {
    public static final String KEY="academy:classic_terminal";
    private static final Map<ServerPlayer,TerminalState> CACHE=new WeakHashMap<>();
    public static TerminalState get(ServerPlayer player){return CACHE.computeIfAbsent(player,p->decode(p.getPersistentData().getCompound(KEY)));}
    public static void save(ServerPlayer player){player.getPersistentData().put(KEY,encode(get(player)));}
    public static void remove(ServerPlayer player){save(player);CACHE.remove(player);TerminalFrequencySessions.remove(player);}
    public static void clone(ServerPlayer original,ServerPlayer replacement){
        replacement.getPersistentData().put(KEY,encode(get(original)));CACHE.remove(original);CACHE.remove(replacement);TerminalFrequencySessions.remove(original);TerminalFrequencySessions.remove(replacement);
    }
    public static void clear(){CACHE.clear();TerminalFrequencySessions.clear();}
    public static CompoundTag encode(TerminalState state){
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putBoolean("isInstalled",state.terminalInstalled());
        var apps=new ListTag();state.savedAppIds().stream().sorted().forEach(id->apps.add(StringTag.valueOf(id)));tag.put("installedApps",apps);return tag;
    }
    public static TerminalState decode(CompoundTag tag){
        var ids=new ArrayList<String>();for(var value:tag.getList("installedApps",Tag.TAG_STRING))ids.add(value.getAsString());
        var state=new TerminalState();state.restore(tag.getBoolean("isInstalled"),ids);return state;
    }
    private TerminalStorage(){}
}
