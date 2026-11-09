package cn.academy.port.tutorial;

import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/** Real player persistent-data storage; object keys avoid cross-server/respawn UUID cache aliasing. */
public final class TutorialStorage {
    public static final String KEY="academy:classic_tutorial";
    private static final Map<ServerPlayer,TutorialState> CACHE=new WeakHashMap<>();
    public static TutorialState get(ServerPlayer player) {
        return CACHE.computeIfAbsent(player,p->{
            var tag=p.getPersistentData().getCompound(KEY);
            int misaka=tag.getInt("misakaID");
            if(misaka<1000||misaka>=19000)misaka=1000+p.getRandom().nextInt(18000);
            return decode(tag,misaka,AcademyTutorialConfig.GIVE_CLOUD_TERMINAL.get());
        });
    }
    public static void save(ServerPlayer player) { player.getPersistentData().put(KEY,encode(get(player))); }
    public static void remove(ServerPlayer player) { save(player);CACHE.remove(player); }
    public static void clone(ServerPlayer original,ServerPlayer replacement) {
        replacement.getPersistentData().put(KEY,encode(get(original)));CACHE.remove(original);CACHE.remove(replacement);
    }
    public static void clear() { CACHE.clear(); }
    public static CompoundTag encode(TutorialState state) {
        var tag=new CompoundTag();tag.putInt("schema",1);tag.putLongArray("savedConditions",state.conditionBits());
        var ids=new ListTag();state.activatedIds().stream().sorted().forEach(id->ids.add(StringTag.valueOf(id)));
        tag.put("activatedTuts",ids);tag.putBoolean("tutorialAcquired",state.tutorialAcquired());tag.putInt("misakaID",state.misakaID());tag.putBoolean("AC_Tutorial_Open",state.firstOpened());return tag;
    }
    public static TutorialState decode(CompoundTag tag) {
        int misaka=tag.getInt("misakaID");return decode(tag,misaka>=1000&&misaka<19000?misaka:1000,false);
    }
    public static TutorialState decode(CompoundTag tag,int initialMisaka,boolean giveCloudTerminal) {
        var state=new TutorialState(initialMisaka,giveCloudTerminal);
        var ids=new ArrayList<String>();for(var value:tag.getList("activatedTuts",Tag.TAG_STRING))ids.add(value.getAsString());
        state.restore(tag.getLongArray("savedConditions"),ids,tag.getBoolean("tutorialAcquired"),tag.getBoolean("AC_Tutorial_Open"));return state;
    }
    private TutorialStorage() {}
}
