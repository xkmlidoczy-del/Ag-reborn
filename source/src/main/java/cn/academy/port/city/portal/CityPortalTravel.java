package cn.academy.port.city.portal;

import cn.academy.port.city.CityBlocks;
import java.util.*;
import net.minecraft.core.*;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.*;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** One public arrival pool, separate persistent return addresses for every player. */
@EventBusSubscriber(modid="academy")
public final class CityPortalTravel {
    public static final ResourceKey<Level> CITY=ResourceKey.create(Registries.DIMENSION,ResourceLocation.fromNamespaceAndPath("academy","story_city_map"));
    public static final String RETURN="academy_amethyst_portal_return";
    private CityPortalTravel(){}
    public static DimensionTransition destination(ServerLevel from,ServerPlayer player,BlockPos source){
        if(from.dimension().equals(CITY))return home(player);
        var city=from.getServer().getLevel(CITY);if(city==null){player.displayClientMessage(Component.translatable("academy.city.portal.unavailable"),true);return null;}
        BlockPos station=station(city);if(station==null){player.displayClientMessage(Component.translatable("academy.city.portal.no_landing"),true);return null;}
        return prepareArrival(from,player,source,city,station);
    }
    /** Trusted server preparation, also exercised against real generator columns by the native fixture. */
    public static DimensionTransition prepareArrival(ServerLevel from,ServerPlayer player,BlockPos source,ServerLevel city,BlockPos station){
        Vec3 arrival=safeNear(city,station);Vec3 returnSpot=safeNear(from,source);
        if(arrival==null||returnSpot==null){player.displayClientMessage(Component.translatable("academy.city.portal.no_landing"),true);return null;}
        var record=new CompoundTag();record.putInt("Schema",1);record.putString("Dimension",from.dimension().location().toString());record.putLong("Anchor",source.asLong());
        record.putDouble("X",returnSpot.x);record.putDouble("Y",returnSpot.y);record.putDouble("Z",returnSpot.z);record.putFloat("Yaw",player.getYRot());
        return transition(city,arrival,0,entity->{if(entity instanceof ServerPlayer p){p.getPersistentData().put(RETURN,record.copy());p.sendSystemMessage(Component.translatable("academy.city.portal.arrived"));}});
    }
    public static DimensionTransition home(ServerPlayer player){
        var record=player.getPersistentData().getCompound(RETURN);ServerLevel target=null;Vec3 spot=null;float yaw=record.getFloat("Yaw");
        var id=ResourceLocation.tryParse(record.getString("Dimension"));
        if(record.getInt("Schema")==1&&id!=null){
            var key=ResourceKey.create(Registries.DIMENSION,id);target=player.server.getLevel(key);
            if(target!=null&&!key.equals(CITY)){
                var anchor=BlockPos.of(record.getLong("Anchor"));target.getChunkAt(anchor);
                var saved=new Vec3(record.getDouble("X"),record.getDouble("Y"),record.getDouble("Z"));
                if(Double.isFinite(saved.x)&&Double.isFinite(saved.y)&&Double.isFinite(saved.z)&&saved.distanceToSqr(Vec3.atCenterOf(anchor))<32*32&&standable(target,saved))spot=saved;
                if(spot==null)spot=safeNear(target,anchor);
            }else target=null;
        }
        if(target==null||spot==null){
            target=player.server.overworld();var spawn=target.getSharedSpawnPos();target.getChunkAt(spawn);
            BlockPos top=target.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,spawn);spot=safeNear(target,top.below());
            if(spot==null&&standable(target,Vec3.atBottomCenterOf(top)))spot=Vec3.atBottomCenterOf(top);
        }
        if(spot==null){player.displayClientMessage(Component.translatable("academy.city.portal.no_landing"),true);return null;}
        return transition(target,spot,yaw,entity->{if(entity instanceof ServerPlayer p)p.sendSystemMessage(Component.translatable("academy.city.portal.returned"));});
    }
    private static DimensionTransition transition(ServerLevel world,Vec3 at,float yaw,DimensionTransition.PostDimensionTransition callback){
        return new DimensionTransition(world,at,Vec3.ZERO,yaw,0,DimensionTransition.PLAY_PORTAL_SOUND.then(DimensionTransition.PLACE_PORTAL_TICKET).then(e->{e.setPortalCooldown(80);callback.onTransition(e);}));
    }
    /** Candidate feet are outside the 2x2 water: arrivals cannot immediately bounce back. */
    public static Vec3 safeNear(ServerLevel world,BlockPos pool){
        int[][] offsets={{-1,-1},{2,-1},{-1,2},{2,2},{0,-2},{1,-2},{-2,0},{3,1},{0,3},{1,3},{-3,-1},{4,2}};
        for(int dy:new int[]{1,2,0,3,-1})for(var off:offsets){var p=pool.offset(off[0],dy,off[1]);world.getChunkAt(p);Vec3 at=Vec3.atBottomCenterOf(p);if(standable(world,at))return at;}
        return null;
    }
    public static boolean standable(ServerLevel world,Vec3 at){
        var p=BlockPos.containing(at);if(!world.getWorldBorder().isWithinBounds(p)||p.getY()<=world.getMinBuildHeight()||p.getY()+2>=world.getMaxBuildHeight())return false;
        var below=world.getBlockState(p.below());if(!below.isFaceSturdy(world,p.below(),Direction.UP)||below.is(Blocks.MAGMA_BLOCK)||below.is(Blocks.CAMPFIRE)||below.is(Blocks.SOUL_CAMPFIRE)||below.is(Blocks.CACTUS))return false;
        if(!world.getFluidState(p).isEmpty()||!world.getFluidState(p.above()).isEmpty())return false;
        var box=new AABB(at.x-.31,at.y+.01,at.z-.31,at.x+.31,at.y+1.81,at.z+.31);
        return !world.getBlockCollisions(null,box).iterator().hasNext()&&!world.getBlockState(p).is(Blocks.FIRE)&&!world.getBlockState(p).is(Blocks.SOUL_FIRE);
    }
    public static BlockPos station(ServerLevel city){
        var data=StationData.get(city);
        if(data.pool!=null){city.getChunkAt(data.pool);if(AmethystPortalRitual.active(city,data.pool)&&safeNear(city,data.pool)!=null)return data.pool;}
        // These are open campus foyer tiles. Only the tiny pool and its soil rim change;
        // no city generator, buildings, containers, or occupied headroom are rewritten.
        for(var p:List.of(new BlockPos(-76,-1,-78),new BlockPos(-69,-1,-78),new BlockPos(-76,-1,-68),new BlockPos(-69,-1,-62),new BlockPos(-82,-1,-70))){
            city.getChunkAt(p);boolean clear=true;
            for(int x=-1;x<=2&&clear;x++)for(int z=-1;z<=2;z++){
                var at=p.offset(x,0,z);var floor=city.getBlockState(at);
                if(!(floor.is(CityBlocks.block("plaza_tile"))||floor.is(CityBlocks.block("composite_floor")))||city.getBlockEntity(at)!=null||!city.isEmptyBlock(at.above())||!city.isEmptyBlock(at.above(2))){clear=false;break;}
            }
            if(!clear)continue;
            var before=new LinkedHashMap<BlockPos,net.minecraft.world.level.block.state.BlockState>();
            for(var off:AmethystPortalRitual.FIBERS){var plant=p.offset(off);before.put(plant.below(),city.getBlockState(plant.below()));before.put(plant,city.getBlockState(plant));city.setBlock(plant.below(),Blocks.GRASS_BLOCK.defaultBlockState(),2);city.setBlock(plant,AmethystPortalContent.FIBER.get().defaultBlockState(),2);}
            for(var off:AmethystPortalRitual.WATER){var at=p.offset(off);before.put(at,city.getBlockState(at));}
            AmethystPortalRitual.open(city,p);
            if(AmethystPortalRitual.active(city,p)&&safeNear(city,p)!=null){data.pool=p;data.setDirty();return p;}
            before.forEach((at,state)->city.setBlock(at,state,3));
        }return null;
    }
    public static CompoundTag returnRecord(ServerPlayer p){return p.getPersistentData().getCompound(RETURN).copy();}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){
        if(event.getOriginal().getPersistentData().contains(RETURN))event.getEntity().getPersistentData().put(RETURN,event.getOriginal().getPersistentData().getCompound(RETURN).copy());
    }
    private static final class StationData extends SavedData {
        BlockPos pool;
        static final Factory<StationData> FACTORY=new Factory<>(StationData::new,(t,lookup)->{var d=new StationData();if(t.contains("Pool"))d.pool=BlockPos.of(t.getLong("Pool"));return d;});
        static StationData get(ServerLevel w){return w.getDataStorage().computeIfAbsent(FACTORY,"academy_amethyst_city_arrival");}
        @Override public CompoundTag save(CompoundTag t,HolderLookup.Provider lookup){if(pool!=null)t.putLong("Pool",pool.asLong());return t;}
    }
}
