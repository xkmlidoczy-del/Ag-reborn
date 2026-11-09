package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;
import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.level.BlockEvent;

/** Uses existing water to perform actual protected-world fire and farmland interactions. */
public final class CanonWaterExtra {
    public static final Set<String> IMPLEMENTED=Set.of("hydro_irrigate","hydro_extinguish");
    private static final String CATEGORY="hydro_hand";
    private CanonWaterExtra() {}
    public static boolean cast(ServerPlayer p,String id) {
        if(p==null||!IMPLEMENTED.contains(id)||!p.server.isSameThread()||p.connection==null||p.hasDisconnected()
                ||p.server.getPlayerList().getPlayer(p.getUUID())!=p||p.isRemoved()||!p.isAlive()||p.isSpectator()
                ||AbilityConsumption.busy(p)||HighPowerCombat.unsupportedReason(p).isPresent()||!HighPowerResources.ready(p))return false;
        var state=AbilityStorage.get(p);if(!CATEGORY.equals(state.category)||state.level<2||!state.canUse(id)
                ||!state.presets.currentContains(id)||!SkillCatalog.enabled(CATEGORY,id))return false;
        var world=p.serverLevel();BlockPos water=source(p,id);if(water==null)return false;
        BlockHitResult hit=ray(p,id);if(hit==null)return false;
        BlockPos target=hit.getBlockPos();BlockState old=world.getBlockState(target);
        if(id.equals("hydro_extinguish")&&!old.is(Blocks.FIRE)) {
            target=target.relative(hit.getDirection());if(!world.hasChunkAt(target))return false;old=world.getBlockState(target);
        }
        if(!allowedTarget(p,water,target,id,old))return false;
        if(NeoForge.EVENT_BUS.post(new SkillBlockDestroyEvent(p,CATEGORY+"."+id,target)).isCanceled()
                ||NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,target,old,p)).isCanceled()
                ||!allowedTarget(p,water,target,id,old))return false;
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost(),30))return false;
        if(AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine||state.interfering
                ||!state.presets.currentContains(id)||!allowedTarget(p,water,target,id,old))return false;
        BlockState desired=id.equals("hydro_irrigate")?old.setValue(FarmBlock.MOISTURE,7):Blocks.AIR.defaultBlockState();
        BlockSnapshot snapshot=BlockSnapshot.create(world.dimension(),world,target);
        if(!world.setBlock(target,desired,2)||!world.getBlockState(target).equals(desired))return false;
        boolean denied;
        try {denied=EventHooks.onBlockPlace(p,snapshot,hit.getDirection());}
        catch(RuntimeException | Error failure){restore(world,target,old,desired);throw failure;}
        if(denied||!world.getBlockState(target).equals(desired)||!waterValid(p,water,id)||!world.mayInteract(p,target)
                ||AbilityStorage.get(p)!=state||!state.activated||!state.presets.currentContains(id)) {
            restore(world,target,old,desired);return false;
        }
        world.updateNeighborsAt(target,old.getBlock());
        CanonVfx.emit(p,id,water.getCenter(),target.getCenter(),CanonVfx.Phase.HIT,15);
        state.addExperience(id,.01);AbilityStorage.save(p);return true;
    }
    private static void restore(ServerLevel world,BlockPos pos,BlockState old,BlockState expected) {
        if(world.hasChunkAt(pos)&&world.getBlockEntity(pos)==null&&world.getBlockState(pos).equals(expected))world.setBlock(pos,old,2);
    }
    private static boolean allowedTarget(ServerPlayer p,BlockPos source,BlockPos target,String id,BlockState old) {
        var world=p.serverLevel();
        double reach=number(id, "reach");
        return waterValid(p,source,id)&&world.hasChunkAt(target)&&world.getBlockEntity(target)==null&&world.mayInteract(p,target)
                &&p.distanceToSqr(target.getCenter())<=reach*reach&&world.getBlockState(target).equals(old)
                &&(id.equals("hydro_irrigate")?old.is(Blocks.FARMLAND)&&old.getValue(FarmBlock.MOISTURE)<7:old.is(Blocks.FIRE));
    }
    private static BlockPos source(ServerPlayer p,String id) {
        int range=integer(id, "source_reach");
        BlockPos base=p.blockPosition();BlockPos best=null;double distance=(double)range*range;
        for(BlockPos pos:BlockPos.betweenClosed(base.offset(-range,-range,-range),base.offset(range,range,range))) {
            double d=p.distanceToSqr(pos.getCenter());if(d<=distance&&waterValid(p,pos,id)){distance=d;best=pos.immutable();}
        }
        return best;
    }
    private static boolean waterValid(ServerPlayer p, BlockPos pos,String id) {
        double range=number(id, "source_reach");
        var world=p.serverLevel();if(!world.hasChunkAt(pos)||world.getBlockEntity(pos)!=null||!world.mayInteract(p,pos)
                ||!world.getBlockState(pos).is(Blocks.WATER)||!world.getFluidState(pos).isSource()
                ||p.distanceToSqr(pos.getCenter())>range*range)return false;
        return world.clip(new ClipContext(p.getEyePosition(),pos.getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    private static BlockHitResult ray(ServerPlayer p,String id) {
        Vec3 start=p.getEyePosition(),direction=ClassicRaytrace.direction(p),end=start.add(direction.scale(number(id, "reach")));
        if(!finite(start)||!finite(direction)||!finite(end))return null;
        int x0=Math.floorDiv((int)Math.floor(Math.min(start.x,end.x)),16),x1=Math.floorDiv((int)Math.floor(Math.max(start.x,end.x)),16);
        int z0=Math.floorDiv((int)Math.floor(Math.min(start.z,end.z)),16),z1=Math.floorDiv((int)Math.floor(Math.max(start.z,end.z)),16);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!p.serverLevel().hasChunk(x,z))return null;
        var hit=p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,p));
        return hit.getType()==HitResult.Type.BLOCK?hit:null;
    }
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
