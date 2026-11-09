package cn.academy.port.story;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Dimension-local persistent ownership; an unloaded guard never becomes a free duplicate spawn. */
final class UrbanStoryGuardData extends SavedData {
    private static final String ID="academy_urban_guards";
    private final Map<String,Entry> entries=new HashMap<>();
    record Entry(UUID uuid,long chunk) {}
    private static String key(long node,UrbanStoryProgress.Objective objective) { return node+":"+objective.name(); }
    static UrbanStoryGuardData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(new SavedData.Factory<>(UrbanStoryGuardData::new,
                (tag,lookup)->load(tag)),ID);
    }
    private static UrbanStoryGuardData load(CompoundTag tag) {
        var data=new UrbanStoryGuardData();
        if (!tag.contains("guards",Tag.TAG_LIST)) return data;
        var list=tag.getList("guards",Tag.TAG_COMPOUND);
        for(int i=0;i<list.size();i++) {
            var e=list.getCompound(i);
            if(e.contains("key",Tag.TAG_STRING) && e.hasUUID("uuid") && e.contains("chunk",Tag.TAG_LONG))
                data.entries.put(e.getString("key"),new Entry(e.getUUID("uuid"),e.getLong("chunk")));
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup) {
        var list=new ListTag();
        entries.forEach((key,value)->{ var e=new CompoundTag();e.putString("key",key);e.putUUID("uuid",value.uuid());e.putLong("chunk",value.chunk());list.add(e); });
        tag.put("guards",list); return tag;
    }
    Entry get(long node,UrbanStoryProgress.Objective objective) { return entries.get(key(node,objective)); }
    boolean put(long node,UrbanStoryProgress.Objective objective,UUID uuid,long chunk) {
        if(entries.putIfAbsent(key(node,objective),new Entry(uuid,chunk))!=null)return false;
        setDirty();return true;
    }
    void update(long node,UrbanStoryProgress.Objective objective,UUID uuid,long chunk) {
        String key=key(node,objective);Entry before=entries.get(key);
        if(before!=null && before.uuid().equals(uuid) && before.chunk()!=chunk) {entries.put(key,new Entry(uuid,chunk));setDirty();}
    }
    void remove(long node,UrbanStoryProgress.Objective objective,UUID uuid) {
        String key=key(node,objective);Entry before=entries.get(key);
        if(before!=null && before.uuid().equals(uuid)) {entries.remove(key);setDirty();}
    }
}
