package cn.academy.port.titan;

import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.*;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.*;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/** No biome injection, force-loading or chunk-generation spawns. At most four loaded natural titans. */
@EventBusSubscriber(modid="academy")
public final class CityTitanSpawns {
    private static final Map<ServerLevel,Long> NEXT=new WeakHashMap<>();
    private static final EntityTypeTest<Entity, TitanEntity> TITAN = EntityTypeTest.forClass(TitanEntity.class);
    private static final ThreadLocal<TitanEntity> SPAWNING = new ThreadLocal<>();
    private static final int MAX_NATURAL = 4;
    private CityTitanSpawns(){}
    public static boolean automatic(MobSpawnType type){return type==MobSpawnType.NATURAL||type==MobSpawnType.CHUNK_GENERATION;}
    @SubscribeEvent public static void gate(FinalizeSpawnEvent event){
        if(event.getEntity() instanceof TitanEntity e && automatic(event.getSpawnType()) && SPAWNING.get()!=e)
            event.setSpawnCancelled(true);
    }
    public static boolean collisionFree(ServerLevel world,TitanEntity e,Vec3 pos){
        var box=e.getBoundingBox().move(pos.subtract(e.position()));
        int minX=net.minecraft.core.SectionPos.blockToSectionCoord((int)Math.floor(box.minX)),maxX=net.minecraft.core.SectionPos.blockToSectionCoord((int)Math.floor(box.maxX));
        int minZ=net.minecraft.core.SectionPos.blockToSectionCoord((int)Math.floor(box.minZ)),maxZ=net.minecraft.core.SectionPos.blockToSectionCoord((int)Math.floor(box.maxZ));
        for(int x=minX;x<=maxX;x++)for(int z=minZ;z<=maxZ;z++)if(!world.hasChunk(x,z))return false;
        return world.getWorldBorder().isWithinBounds(box)&&box.maxY<world.getMaxBuildHeight()&&!world.getBlockCollisions(e,box).iterator().hasNext()&&world.getEntities(e,box,l->l instanceof LivingEntity).isEmpty();
    }
    /** A giant spans many lakebed columns: use the highest bed, rather than embedding its sides in a slope. */
    public static Vec3 waterPosition(ServerLevel world,BlockPos at,float width){
        int half=(int)Math.ceil(width*.5),bed=Integer.MIN_VALUE;
        for(int dx=-half;dx<=half;dx++)for(int dz=-half;dz<=half;dz++){
            var column=at.offset(dx,0,dz);if(!world.hasChunkAt(column))return null;
            int surface=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ())-1;
            var top=new BlockPos(column.getX(),surface,column.getZ());if(!world.getFluidState(top).is(FluidTags.WATER))return null;
            int bottom=surface;while(bottom>world.getMinBuildHeight()+1&&world.getFluidState(new BlockPos(column.getX(),bottom-1,column.getZ())).is(FluidTags.WATER))bottom--;
            if(surface-bottom<3)return null;bed=Math.max(bed,bottom);
        }
        return bed==Integer.MIN_VALUE?null:new Vec3(at.getX()+.5,bed+.05,at.getZ()+.5);
    }
    private static boolean enabled(ServerLevel world) {
        return CityTitanZones.dimension(world.dimension()) && TitanConfig.NATURAL_SPAWNS.get()
                && world.getDifficulty()!=Difficulty.PEACEFUL && world.getGameRules().getBoolean(GameRules.RULE_DOMOBSPAWNING);
    }

    private static List<? extends TitanEntity> naturalTitans(ServerLevel world) {
        return world.getEntities(TITAN, titan -> titan.isAlive() && titan.isCityNatural());
    }

    private static double horizontalDistanceSquared(double x, double z, Entity entity) {
        double dx=x-entity.getX(), dz=z-entity.getZ();
        return dx*dx+dz*dz;
    }

    public static TitanEntity trySpawn(ServerLevel world,String species,BlockPos at){
        if (!enabled(world)) return null;
        return trySpawn(world, species, at, naturalTitans(world));
    }

    private static TitanEntity trySpawn(ServerLevel world,String species,BlockPos at,List<? extends TitanEntity> natural) {
        if (!world.hasChunkAt(at) || natural.size()>=MAX_NATURAL
                || natural.stream().anyMatch(t -> horizontalDistanceSquared(at.getX()+.5,at.getZ()+.5,t)<320*320)
                || world.players().stream().anyMatch(p -> !p.isSpectator()
                    && horizontalDistanceSquared(at.getX()+.5,at.getZ()+.5,p)<64*64)) return null;
        var type=TitanBlocks.TYPES.get(species);if(type==null)return null;var e=type.get().create(world);if(e==null)return null;
        if(!CityTitanZones.allowed(world,at,e.getBbWidth()))return null;
        BlockPos top=world.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,at);
        var mobility=e.profile().mobility();Vec3 position;
        if(mobility==TitanProfiles.Mobility.SWIM){
            position=waterPosition(world,at,e.getBbWidth());if(position==null)return null;
        }else{
            if(!world.getFluidState(top.below()).isEmpty()||!world.getBlockState(top.below()).isFaceSturdy(world,top.below(),Direction.UP)||!CityTitanZones.terrainAllowed(world,top.below()))return null;
            int topY=top.getY(),half=(int)Math.ceil(e.getBbWidth()*.5);
            for(int x=-half;x<=half;x++)for(int z=-half;z<=half;z++){
                var column=at.offset(x,0,z);if(!world.hasChunkAt(column))return null;
                int columnTop=world.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,column.getX(),column.getZ());
                int plannedTop=Math.max(0,cn.academy.port.city.CityPlan.sample(CityTitanZones.PLAN_SEED,column.getX(),column.getZ()).height());
                if(columnTop>plannedTop+3)return null;
                topY=Math.max(topY,columnTop);
            }
            position=new Vec3(at.getX()+.5,topY+(mobility==TitanProfiles.Mobility.FLY?12:.02),at.getZ()+.5);
        }
        if(!collisionFree(world,e,position))return null;e.moveTo(position.x,position.y,position.z,world.random.nextFloat()*360,0);
        SPAWNING.set(e);
        try {
            net.neoforged.neoforge.event.EventHooks.finalizeMobSpawn(e,world,world.getCurrentDifficultyAt(e.blockPosition()),MobSpawnType.NATURAL,null);
        } finally {
            SPAWNING.remove();
        }
        if (e.isSpawnCancelled()) return null;
        e.markCityNatural();
        e.setPersistenceRequired();
        if(!world.addFreshEntity(e))return null;
        cn.academy.port.fleet.InteractionLab.LOGGER.info("ACA_TITAN_NATURAL species={} position={} district={}",species,e.position(),cn.academy.port.city.CityPlan.district(at.getX(),at.getZ()));return e;
    }
    @SubscribeEvent public static void tick(LevelTickEvent.Post event){
        if (!(event.getLevel() instanceof ServerLevel world) || world.getGameTime()%200!=0 || !enabled(world)) return;
        long now=world.getGameTime();
        if (now<NEXT.getOrDefault(world,0L) || world.players().stream().noneMatch(
                p -> !p.isSpectator() && CityTitanZones.rural(p.getBlockX(),p.getBlockZ()))) return;
        var titans=naturalTitans(world);
        if(titans.size()>=MAX_NATURAL)return;
        for(var player:world.players()){
            if(player.isSpectator()||!CityTitanZones.rural(player.getBlockX(),player.getBlockZ())||world.random.nextInt(TitanConfig.NATURAL_ONE_IN.get())!=0)continue;
            for(int i=0;i<20;i++){
                double angle=world.random.nextDouble()*Math.PI*2,radius=72+world.random.nextInt(41);var at=BlockPos.containing(player.getX()+Math.cos(angle)*radius,player.getY(),player.getZ()+Math.sin(angle)*radius);
                String id=TitanBlocks.IDS[world.random.nextInt(TitanBlocks.IDS.length)];
                var created=trySpawn(world,id,at,titans);
                if(created!=null){NEXT.put(world,now+1200);return;}
            }
        }
    }
}
