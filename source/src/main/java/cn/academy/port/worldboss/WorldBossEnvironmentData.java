package cn.academy.port.worldboss;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.*;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.ServerLevelData;

/** Native per-dimension undo journal. No static ServerLevel or entity references survive unload. */
final class WorldBossEnvironmentData extends SavedData {
    static final int MAX_CHANGED=60000;
    private static final String ID="academy_world_boss_environment";
    static final class Owner {
        boolean pit;
        long stormCast=Long.MIN_VALUE;
    }
    static final class Cell {
        final BlockState original;
        final CompoundTag blockEntity;
        final LinkedHashMap<UUID,BlockState> layers=new LinkedHashMap<>();
        boolean pending;
        BlockState conflictState;
        CompoundTag conflictBlockEntity;
        Cell(BlockState state,CompoundTag tag){original=state;blockEntity=tag;}
        BlockState visible(){BlockState result=original;for(var state:layers.values())result=state;return result;}
    }
    final Map<UUID,Owner> owners=new LinkedHashMap<>();
    final Map<BlockPos,Cell> cells=new LinkedHashMap<>();
    final Set<UUID> weatherOwners=new LinkedHashSet<>();
    private CompoundTag weather;
    private long dayAdvance,lastDay,lastClock,weatherElapsed;

    private static SavedData.Factory<WorldBossEnvironmentData> factory(ServerLevel level){
        return new SavedData.Factory<>(WorldBossEnvironmentData::new,(tag,lookup)->load(level,tag));
    }
    static WorldBossEnvironmentData get(ServerLevel level){return level.getDataStorage().computeIfAbsent(factory(level),ID);}
    static WorldBossEnvironmentData existing(ServerLevel level){return level.getDataStorage().get(factory(level),ID);}
    Owner owner(UUID id){Owner result=owners.get(id);if(result==null){result=new Owner();owners.put(id,result);setDirty();}return result;}

    boolean change(ServerLevel level,UUID id,BlockPos pos,BlockState state){
        if(level.isOutsideBuildHeight(pos)||!level.hasChunkAt(pos))return false;
        BlockState old=level.getBlockState(pos);
        if(old.is(Blocks.BEDROCK))return false;
        Cell cell=cells.get(pos);boolean firstSnapshot=cell==null;
        // Equal visible layers must still acquire ownership: another boss releasing
        // its identical block must not erase this boss's surviving environment.
        if(cell==null&&old.equals(state))return false;
        if(cell==null){
            if(cells.size()>=MAX_CHANGED)return false;
            BlockEntity tile=level.getBlockEntity(pos);
            cell=new Cell(old,tile==null?null:tile.saveWithFullMetadata(level.registryAccess()).copy());
            cells.put(pos.immutable(),cell);
        }
        cell.layers.remove(id);cell.layers.put(id,state);cell.pending=false;setDirty();
        if(!old.equals(state)&&!write(level,pos,state,null,cell,!firstSnapshot))cell.pending=true;
        if(state.is(Blocks.FIRE))level.getBlockTicks().clearArea(new BoundingBox(pos));
        return true;
    }
    /**
     * Initial excavation already owns the original BE in Cell.original/blockEntity and
     * must not drop a duplicate. Later writes/restores can encounter a container placed
     * by a player while the pit was active: preserve that NEW BE as one native block
     * item before replacing it. Never combine this with Containers.dropContents.
     */
    private static boolean write(ServerLevel level,BlockPos pos,BlockState state,CompoundTag nbt,
                                 Cell journal,boolean preserveCurrentBlockEntity){
        // Validate an original payload before emitting a foreign recovery item or
        // touching the live block. A failed original decoder stays pending intact.
        BlockEntity restored=nbt==null?null:BlockEntity.loadStatic(pos,state,nbt.copy(),level.registryAccess());
        if(nbt!=null&&restored==null)return false;
        BlockEntity current=level.getBlockEntity(pos);
        if(preserveCurrentBlockEntity&&current!=null){
            BlockState currentState=level.getBlockState(pos);
            ItemStack preserved=new ItemStack(currentState.getBlock().asItem());
            if(preserved.isEmpty()||!(preserved.getItem() instanceof BlockItem))
                return blocked(level,pos,current,currentState,journal);
            final CompoundTag snapshot;
            try{
                snapshot=current.saveWithFullMetadata(level.registryAccess()).copy();
                // Native saveToItem separates implicit container/name/lock components
                // from remaining BLOCK_ENTITY_DATA, preserving inventory exactly once.
                current.saveToItem(preserved,level.registryAccess());
            }catch(RuntimeException unsupported){return blocked(level,pos,current,currentState,journal);}
            ItemStack expected=preserved.copy();
            var item=new ItemEntity(level,pos.getX()+.5,pos.getY()+1.05,pos.getZ()+.5,preserved);
            // The restored arena can still contain another boss's authored fire.
            // Protect this recovery entity, not the player's item/components.
            item.setInvulnerable(true);item.setDefaultPickUpDelay();
            if(!level.addFreshEntity(item))return blocked(level,pos,current,currentState,journal);
            // Entity-join listeners may reenter world/container writes. Only commit the
            // exact BE snapshot that the accepted item carries; otherwise discard our
            // new item and retain both the live BE and the durable restoration entry.
            boolean unchanged;
            try{unchanged=level.getBlockEntity(pos)==current&&level.getBlockState(pos).equals(currentState)
                    &&!item.isRemoved()&&ItemStack.isSameItemSameComponents(item.getItem(),expected)
                    &&item.getItem().getCount()==expected.getCount()
                    &&current.saveWithFullMetadata(level.registryAccess()).equals(snapshot);}
            catch(RuntimeException unavailable){unchanged=false;}
            if(!unchanged){item.discard();return blocked(level,pos,level.getBlockEntity(pos),level.getBlockState(pos),journal);}
        }
        level.removeBlockEntity(pos);
        level.setBlock(pos,state,2|16);
        if(restored!=null){level.setBlockEntity(restored);restored.setChanged();level.sendBlockUpdated(pos,state,state,3);}
        journal.conflictState=null;journal.conflictBlockEntity=null;
        return true;
    }
    private static boolean blocked(ServerLevel level,BlockPos pos,BlockEntity current,BlockState state,Cell journal){
        // No corresponding BlockItem (or a rejected item spawn): retain the actual
        // block unchanged. The original restoration AND this foreign payload stay in
        // the per-dimension SavedData journal; periodic recovery can retry later.
        journal.pending=true;journal.conflictState=state;
        try{journal.conflictBlockEntity=current==null?null:current.saveWithFullMetadata(level.registryAccess()).copy();}
        catch(RuntimeException unavailable){/* leave the unmodified live BE as authoritative */}
        return false;
    }
    void release(ServerLevel level,UUID id){
        if(owners.remove(id)==null&&!weatherOwners.contains(id))return;
        for(var it=cells.entrySet().iterator();it.hasNext();){
            var entry=it.next();Cell cell=entry.getValue();if(cell.layers.remove(id)==null)continue;cell.pending=true;
            if(level.hasChunkAt(entry.getKey())){
                if(write(level,entry.getKey(),cell.visible(),cell.layers.isEmpty()?cell.blockEntity:null,cell,true)){
                    if(cell.layers.isEmpty())it.remove();else cell.pending=false;
                }
            }
            // An unloaded position remains durably queued with its remaining layers.
        }
        if(weatherOwners.remove(id)&&weatherOwners.isEmpty())restoreWeather(level);
        setDirty();
    }
    void storm(ServerLevel level,UUID id,long cast){
        Owner owner=owner(id);if(owner.stormCast==cast)return;
        owner.stormCast=cast;
        if(weather==null){
            ServerLevelData info=(ServerLevelData)level.getLevelData();weather=new CompoundTag();
            weather.putLong("day",level.getDayTime());weather.putInt("clear",info.getClearWeatherTime());
            weather.putInt("rain",info.getRainTime());weather.putInt("thunder",info.getThunderTime());
            weather.putBoolean("raining",info.isRaining());weather.putBoolean("thundering",info.isThundering());
            weather.putFloat("rainLevel",level.getRainLevel(1));weather.putFloat("thunderLevel",level.getThunderLevel(1));
            dayAdvance=0;weatherElapsed=0;lastDay=level.getDayTime();lastClock=level.getGameTime();
        }else clock(level);
        weatherOwners.add(id);level.setWeatherParameters(0,24000,true,true);level.setDayTime(18000);
        lastDay=level.getDayTime();lastClock=level.getGameTime();setDirty();
    }
    private void clock(ServerLevel level){
        if(weather==null)return;
        long elapsed=Math.max(0,level.getGameTime()-lastClock),delta=level.getDayTime()-lastDay;
        // Observe actual native time progression (including fractional day speeds)
        // instead of advancing while RULE_DAYLIGHT is false or counting offline time.
        if(delta>=0&&level.getGameRules().getBoolean(GameRules.RULE_DAYLIGHT))dayAdvance+=delta;
        if(level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE))weatherElapsed+=elapsed;
        lastDay=level.getDayTime();lastClock=level.getGameTime();setDirty();
    }
    private void restoreWeather(ServerLevel level){
        if(weather==null)return;clock(level);ServerLevelData info=(ServerLevelData)level.getLevelData();
        level.setDayTime(weather.getLong("day")+dayAdvance);
        info.setClearWeatherTime(remaining(weather.getInt("clear"),weatherElapsed));
        info.setRainTime(remaining(weather.getInt("rain"),weatherElapsed));
        info.setThunderTime(remaining(weather.getInt("thunder"),weatherElapsed));
        info.setRaining(weather.getBoolean("raining"));info.setThundering(weather.getBoolean("thundering"));
        level.setRainLevel(weather.getFloat("rainLevel"));level.setThunderLevel(weather.getFloat("thunderLevel"));
        weather=null;setDirty();
    }
    private static int remaining(int ticks,long elapsed){return (int)Math.max(0,(long)ticks-elapsed);}
    void tick(ServerLevel level){
        clock(level);if(Math.floorMod(level.getGameTime(),20)!=0)return;int restored=0;
        for(var it=cells.entrySet().iterator();it.hasNext();){
            var entry=it.next();if(!level.hasChunkAt(entry.getKey()))continue;
            Cell cell=entry.getValue();
            if((cell.layers.isEmpty()||cell.pending)&&restored<500){
                if(write(level,entry.getKey(),cell.visible(),cell.layers.isEmpty()?cell.blockEntity:null,cell,true)){
                    if(cell.layers.isEmpty())it.remove();else cell.pending=false;
                }
                restored++;setDirty();
            }
            else if(level.getBlockState(entry.getKey()).is(Blocks.FIRE)){
                // Only our exact authored flame cells: preserve native fire model,
                // light and contact hazard without unjournalled runaway spread.
                level.getBlockTicks().clearArea(new BoundingBox(entry.getKey()));
            }
        }
    }
    @Override public CompoundTag save(CompoundTag tag,HolderLookup.Provider lookup){
        tag.putInt("schema",1);var ownerTags=new ListTag();
        owners.forEach((id,owner)->{var row=new CompoundTag();row.putUUID("id",id);row.putBoolean("pit",owner.pit);row.putLong("stormCast",owner.stormCast);ownerTags.add(row);});tag.put("owners",ownerTags);
        var cellTags=new ListTag();cells.forEach((pos,cell)->{
            var row=new CompoundTag();row.putLong("pos",pos.asLong());row.putBoolean("pending",cell.pending);row.put("original",NbtUtils.writeBlockState(cell.original));
            if(cell.blockEntity!=null)row.put("blockEntity",cell.blockEntity.copy());
            if(cell.conflictState!=null)row.put("conflictState",NbtUtils.writeBlockState(cell.conflictState));
            if(cell.conflictBlockEntity!=null)row.put("conflictBlockEntity",cell.conflictBlockEntity.copy());
            var layers=new ListTag();cell.layers.forEach((id,state)->{var layer=new CompoundTag();layer.putUUID("id",id);layer.put("state",NbtUtils.writeBlockState(state));layers.add(layer);});row.put("layers",layers);cellTags.add(row);
        });tag.put("cells",cellTags);
        if(weather!=null){tag.put("weather",weather.copy());tag.putLong("dayAdvance",dayAdvance);tag.putLong("weatherElapsed",weatherElapsed);tag.putLong("lastDay",lastDay);tag.putLong("lastClock",lastClock);}
        var weatherTags=new ListTag();for(UUID id:weatherOwners){var row=new CompoundTag();row.putUUID("id",id);weatherTags.add(row);}tag.put("weatherOwners",weatherTags);return tag;
    }
    private static WorldBossEnvironmentData load(ServerLevel level,CompoundTag tag){
        var data=new WorldBossEnvironmentData();var registry=level.registryAccess().lookupOrThrow(Registries.BLOCK);
        for(var raw:tag.getList("owners",Tag.TAG_COMPOUND)){var row=(CompoundTag)raw;if(!row.hasUUID("id"))continue;var owner=new Owner();owner.pit=row.getBoolean("pit");owner.stormCast=row.getLong("stormCast");data.owners.put(row.getUUID("id"),owner);}
        for(var raw:tag.getList("cells",Tag.TAG_COMPOUND)){
            if(data.cells.size()>=MAX_CHANGED)throw new IllegalStateException("World Boss environment journal exceeds native limit");
            var row=(CompoundTag)raw;var cell=new Cell(NbtUtils.readBlockState(registry,row.getCompound("original")),row.contains("blockEntity",Tag.TAG_COMPOUND)?row.getCompound("blockEntity").copy():null);
            for(var layerRaw:row.getList("layers",Tag.TAG_COMPOUND)){var layer=(CompoundTag)layerRaw;if(layer.hasUUID("id"))cell.layers.put(layer.getUUID("id"),NbtUtils.readBlockState(registry,layer.getCompound("state")));}
            cell.pending=row.getBoolean("pending");
            if(row.contains("conflictState",Tag.TAG_COMPOUND))cell.conflictState=NbtUtils.readBlockState(registry,row.getCompound("conflictState"));
            if(row.contains("conflictBlockEntity",Tag.TAG_COMPOUND))cell.conflictBlockEntity=row.getCompound("conflictBlockEntity").copy();
            data.cells.put(BlockPos.of(row.getLong("pos")),cell);
        }
        if(tag.contains("weather",Tag.TAG_COMPOUND)){data.weather=tag.getCompound("weather").copy();data.dayAdvance=tag.getLong("dayAdvance");data.weatherElapsed=tag.getLong("weatherElapsed");data.lastDay=tag.getLong("lastDay");data.lastClock=tag.getLong("lastClock");}
        for(var raw:tag.getList("weatherOwners",Tag.TAG_COMPOUND)){var row=(CompoundTag)raw;if(row.hasUUID("id"))data.weatherOwners.add(row.getUUID("id"));}return data;
    }
}
