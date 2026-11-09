package cn.academy.port.city;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Stable crowd-slot/service identities retain resources when ambient actors leave the loaded neighborhood. */
final class CityNpcMemory extends SavedData {
    private final Map<String,CompoundTag> residents=new HashMap<>();
    private static final Factory<CityNpcMemory> FACTORY=new Factory<>(CityNpcMemory::new,(tag,lookup) -> {
        var memory=new CityNpcMemory();for(String key:tag.getAllKeys())memory.residents.put(key,tag.getCompound(key).copy());return memory;
    });
    static CityNpcMemory get(ServerLevel level){return level.getDataStorage().computeIfAbsent(FACTORY,"academy_city_people");}
    CompoundTag read(String key){var tag=residents.get(key);return tag==null?new CompoundTag():tag.copy();}
    void write(String key,CompoundTag tag){if(!key.isEmpty()){residents.put(key,tag.copy());setDirty();}}
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){residents.forEach(tag::put);return tag;}
}
