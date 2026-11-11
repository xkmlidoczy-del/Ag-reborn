package cn.academy.port.city;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.LevelEvent;

/** One immutable pre-upgrade occupancy snapshot. Never loads a chunk or rewrites a legacy building. */
@EventBusSubscriber(modid="academy")
public final class CityInteriorVersions extends SavedData {
    public static final int VERSION=1;
    private boolean captured, failed;
    private final Set<Long> legacy=new HashSet<>();
    private final Map<String,CompoundTag> buildings=new TreeMap<>();
    private Policy cachedPolicy;
    private static final Factory<CityInteriorVersions> FACTORY=new Factory<>(CityInteriorVersions::new,(t,l)->load(t));
    public static CityInteriorVersions load(CompoundTag t){
        var d=new CityInteriorVersions();d.captured=t.getBoolean("Captured");d.failed=t.getBoolean("Failed");
        for(long n:t.getLongArray("LegacyChunks"))d.legacy.add(n);
        if(t.contains("Version")&&t.getInt("Version")!=VERSION){d.captured=true;d.failed=true;}
        for(var raw:t.getList("Buildings",10))if(raw instanceof CompoundTag entry&&!entry.getString("Id").isEmpty())d.buildings.put(entry.getString("Id"),entry.copy());return d;
    }
    @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider lookup){
        t.putInt("Version",VERSION);t.putBoolean("Captured",captured);t.putBoolean("Failed",failed);
        t.putLongArray("LegacyChunks",legacy.stream().mapToLong(Long::longValue).sorted().toArray());var list=new net.minecraft.nbt.ListTag();buildings.values().forEach(e->list.add(e.copy()));t.put("Buildings",list);return t;
    }
    /** Called from the server tick after a generated chunk is FULL, never from a generation worker. */
    public static void record(ServerLevel w,CityInteriors.Group group){
        var d=w.getDataStorage().computeIfAbsent(FACTORY,"academy_city_interiors_v1");if(d.buildings.containsKey(group.id())||!d.policy().allows(group.reservation().x0(),group.reservation().z0(),group.reservation().x1(),group.reservation().z1()))return;
        var entry=new CompoundTag();entry.putString("Id",group.id());entry.putInt("LayoutVersion",VERSION);entry.putIntArray("Bounds",new int[]{group.reservation().x0(),group.reservation().z0(),group.reservation().x1(),group.reservation().z1()});entry.putInt("Parts",group.parts().size());entry.putInt("Floors",group.surfaces().size());
        var surfaces=new net.minecraft.nbt.ListTag();for(var s:group.surfaces()){var floor=new CompoundTag();floor.putInt("Y",s.y());floor.putString("Shape",s.shape());floor.putIntArray("Bounds",new int[]{s.bounds().x0(),s.bounds().z0(),s.bounds().x1(),s.bounds().z1()});floor.putLong("Entrance",s.entrance().asLong());surfaces.add(floor);}entry.put("Surfaces",surfaces);
        var plans=new net.minecraft.nbt.ListTag();for(var p:group.parts()){
            var plan=new CompoundTag();plan.putString("Id",p.id);plan.putString("Theme",p.theme);plan.putString("Entrance",p.entrance.getSerializedName());var ground=p.floors.getFirst();plan.putIntArray("Entry",new int[]{(ground.rect().x0()+ground.rect().x1())/2,ground.y(),p.entrance==net.minecraft.core.Direction.NORTH?ground.rect().z0():ground.rect().z1()});plan.putIntArray("Core",new int[]{p.coreX,p.coreZ,p.stairDepth,p.corridorX});
            var floors=new net.minecraft.nbt.ListTag();for(var f:p.floors)floors.add(new net.minecraft.nbt.IntArrayTag(new int[]{f.y(),f.rect().x0(),f.rect().z0(),f.rect().x1(),f.rect().z1()}));plan.put("Floors",floors);
            var rooms=new net.minecraft.nbt.ListTag();for(var r:p.rooms){var room=new CompoundTag();room.putInt("Floor",r.floor());room.putInt("Number",r.number());room.putInt("Variant",r.variant());room.putString("Use",r.use());room.putIntArray("Bounds",new int[]{r.rect().x0(),r.rect().z0(),r.rect().x1(),r.rect().z1()});rooms.add(room);}plan.put("Rooms",rooms);
            if(p.lift!=null){plan.putLong("LiftController",p.lift.controller().asLong());plan.putIntArray("LiftFloors",p.lift.floors());}plans.add(plan);
        }entry.put("Plans",plans);d.buildings.put(group.id(),entry);d.setDirty();
    }
    public record Policy(Set<Long> legacy,boolean ready){
        public Policy {legacy=Set.copyOf(legacy);}
        public boolean allows(int x0,int z0,int x1,int z1){
            if(!ready)return false;
            for(int x=Math.floorDiv(x0,16);x<=Math.floorDiv(x1,16);x++)for(int z=Math.floorDiv(z0,16);z<=Math.floorDiv(z1,16);z++)
                if(legacy.contains(ChunkPos.asLong(x,z)))return false;
            return true;
        }
        public static Policy fresh(){return new Policy(Set.of(),true);}
        public static Policy unavailable(){return new Policy(Set.of(),false);}
    }
    public Policy policy(){if(cachedPolicy==null)cachedPolicy=new Policy(legacy,captured&&!failed);return cachedPolicy;}
    public static Set<Long> readHeaders(Path region) throws IOException {
        var out=new HashSet<Long>();if(!Files.exists(region))return out;
        try(var files=Files.list(region)){
            for(Path file:files.filter(p->p.getFileName().toString().matches("r\\.-?\\d+\\.-?\\d+\\.mca")).toList()){
                String[] parts=file.getFileName().toString().split("\\.");int rx=Integer.parseInt(parts[1]),rz=Integer.parseInt(parts[2]);
                try(var data=new RandomAccessFile(file.toFile(),"r")){
                    if(data.length()<8192)throw new IOException("Incomplete city region header");
                    for(int i=0;i<1024;i++)if(data.readInt()!=0)out.add(ChunkPos.asLong(rx*32+(i&31),rz*32+(i>>5)));
                }
            }
        }return out;
    }
    @SubscribeEvent public static void attach(LevelEvent.Load event){
        if(!(event.getLevel() instanceof ServerLevel world)||!(world.getChunkSource().getGenerator() instanceof CityGreyboxGenerator generator))return;
        var d=world.getDataStorage().computeIfAbsent(FACTORY,"academy_city_interiors_v1");
        if(!d.captured){
            Path root=world.getServer().getWorldPath(LevelResource.ROOT);
            Path region=root.resolve("dimensions").resolve(world.dimension().location().getNamespace()).resolve(world.dimension().location().getPath()).resolve("region");
            try{d.legacy.addAll(readHeaders(region));d.captured=true;d.failed=false;d.cachedPolicy=null;d.setDirty();world.getDataStorage().save();}
            catch(IOException|RuntimeException error){d.failed=true;d.cachedPolicy=null;d.setDirty();com.mojang.logging.LogUtils.getLogger().error("City interior upgrade disabled: cannot preserve the pre-upgrade chunk snapshot",error);}
        }
        generator.interiorPolicy(d.policy());
        generator.onInteriorGenerated(group->CityInteriors.generated(world,group));
    }
}
