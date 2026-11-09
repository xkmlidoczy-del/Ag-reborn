package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.integer;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** One selected real water source moves between two loaded and permitted positions. */
@EventBusSubscriber(modid="academy")
public final class CanonWaterTransfer {
    private static final String ID="hydro_transfer",CATEGORY="hydro_hand";
    private static final int RANGE=integer(ID, "reach"),MAX_SELECTIONS=64;
    private static final int SELECTION_TICKS=integer(ID, "duration_ticks");
    private static final Map<UUID,Selection> SELECTED=new HashMap<>();
    private record Selection(ServerPlayer owner,ServerLevel world,BlockPos source,BlockState state,long expires) {}
    private CanonWaterTransfer() {}
    public static boolean cast(ServerPlayer p,String id) {
        if(!ID.equals(id)||!ready(p))return false;
        var previous=SELECTED.get(p.getUUID());
        if(previous!=null && !valid(previous)){SELECTED.remove(p.getUUID(),previous);previous=null;}
        if(previous==null) {
            var hit=ray(p,ClipContext.Fluid.SOURCE_ONLY);if(hit==null)return false;
            var world=p.serverLevel();BlockPos source=hit.getBlockPos();
            if(SELECTED.size()>=MAX_SELECTIONS || !water(world,source) || !world.mayInteract(p,source)
                    ||p.distanceToSqr(source.getCenter())>RANGE*RANGE || world.getGameTime()>Long.MAX_VALUE-SELECTION_TICKS)return false;
            SELECTED.put(p.getUUID(),new Selection(p,world,source.immutable(),world.getBlockState(source),world.getGameTime()+SELECTION_TICKS));
            p.displayClientMessage(Component.translatableWithFallback("ac.ability.hydro_hand.transfer_selected","已选择水源，请对准目标方块并再次施放。"),true);
            return true;
        }
        var hit=ray(p,ClipContext.Fluid.NONE);if(hit==null)return false;
        BlockPos target=hit.getBlockPos().relative(hit.getDirection());
        if(target.equals(previous.source)||!candidate(previous,target,hit.getBlockPos()))return false;
        var world=previous.world;var playerState=AbilityStorage.get(p);BlockState air=world.getBlockState(target);
        BlockState supportBefore=world.getBlockState(hit.getBlockPos());
        // Fire the ordinary source permission hooks before changing either position.
        if(NeoForge.EVENT_BUS.post(new SkillBlockDestroyEvent(p,CATEGORY+"."+ID,previous.source)).isCanceled()
                ||NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,previous.source,previous.state,p)).isCanceled()
                ||!valid(previous)||!candidate(previous,target,hit.getBlockPos())||!world.getBlockState(target).equals(air)
                ||!world.getBlockState(hit.getBlockPos()).equals(supportBefore))return false;
        if(!HighPowerResources.trySpend(p,ID,HighPowerGrowth.values(p).arcCost(),30))return false;
        if(!valid(previous)||!candidate(previous,target,hit.getBlockPos())||!world.getBlockState(target).equals(air)
                ||!world.getBlockState(hit.getBlockPos()).equals(supportBefore)
                ||AbilityStorage.get(p)!=playerState||!playerState.presets.currentContains(ID))return false;
        BlockSnapshot destination=BlockSnapshot.create(world.dimension(),world,target);
        boolean sourceRemoved=world.setBlock(previous.source,Blocks.AIR.defaultBlockState(),2);
        if(!sourceRemoved||!world.getBlockState(previous.source).isAir()||!world.getBlockState(target).equals(air)) {
            if(world.getBlockState(previous.source).isAir()&&world.getBlockState(target).equals(air))world.setBlock(previous.source,previous.state,2);
            return false;
        }
        if(!world.setBlock(target,Blocks.WATER.defaultBlockState(),2)){
            rollback(previous,target,air);return false;
        }
        boolean denied;
        try {denied=EventHooks.onBlockPlace(p,destination,hit.getDirection());}
        catch(RuntimeException | Error failure){rollback(previous,target,air);throw failure;}
        if(denied||!world.getBlockState(hit.getBlockPos()).equals(supportBefore)||!postValid(previous,target,hit.getBlockPos(),playerState)){rollback(previous,target,air);return false;}
        SELECTED.remove(p.getUUID(),previous);
        CanonVfx.emit(p,ID,previous.source.getCenter(),target.getCenter(),CanonVfx.Phase.START,15);
        world.updateNeighborsAt(previous.source,Blocks.WATER);world.updateNeighborsAt(target,Blocks.WATER);
        world.scheduleTick(target,Fluids.WATER,Fluids.WATER.getTickDelay(world));
        if(AbilityStorage.get(p)==playerState&&CATEGORY.equals(playerState.category)&&playerState.learned(ID)){
            playerState.addExperience(ID,.01);AbilityStorage.save(p);
        }
        return true;
    }
    private static void rollback(Selection selected,BlockPos target,BlockState originalAir) {
        var world=selected.world;
        if(!world.hasChunkAt(selected.source)||!world.hasChunkAt(target))return;
        // Never overwrite unrelated callback edits or block entities. Native cross-chunk hard-crash
        // atomicity is outside ordinary Minecraft saves; ordinary denied operations restore snapshots.
        if(world.getBlockState(selected.source).isAir()&&world.getBlockEntity(selected.source)==null
                &&world.getBlockState(target).equals(Blocks.WATER.defaultBlockState())&&world.getBlockEntity(target)==null) {
            if(world.setBlock(target,originalAir,2)&&world.getBlockState(target).equals(originalAir)
                    &&world.getBlockState(selected.source).isAir())world.setBlock(selected.source,selected.state,2);
        } else if(world.getBlockState(selected.source).equals(selected.state)&&world.getBlockEntity(selected.source)==null
                &&world.getBlockState(target).equals(Blocks.WATER.defaultBlockState())&&world.getBlockEntity(target)==null) {
            // A callback restored the original source after our placement: remove our second source.
            world.setBlock(target,originalAir,2);
        } else if(world.getBlockState(selected.source).isAir()&&world.getBlockEntity(selected.source)==null
                &&world.getFluidState(target).isEmpty())world.setBlock(selected.source,selected.state,2);
    }
    private static boolean postValid(Selection s,BlockPos target,BlockPos support,cn.academy.port.core.AbilityProgress state) {
        var p=s.owner;var world=s.world;
        return SELECTED.get(p.getUUID())==s&&AbilityStorage.get(p)==state&&p.server.isSameThread()&&p.serverLevel()==world
                &&p.isAlive()&&!p.isRemoved()&&!p.hasDisconnected()&&!p.isSpectator()&&p.connection!=null
                &&p.server.getPlayerList().getPlayer(p.getUUID())==p&&CATEGORY.equals(state.category)
                &&state.level>=1&&state.activated&&state.overloadFine&&!state.interfering&&state.learned(ID)&&state.presets.currentContains(ID)
                &&SkillCatalog.enabled(CATEGORY,ID)&&HighPowerCombat.unsupportedReason(p).isEmpty()
                &&world.getGameTime()<s.expires&&world.hasChunkAt(s.source)&&world.hasChunkAt(target)&&world.hasChunkAt(support)
                &&world.getBlockState(s.source).isAir()&&world.getBlockEntity(s.source)==null
                &&world.getBlockState(target).equals(Blocks.WATER.defaultBlockState())&&world.getBlockEntity(target)==null
                &&world.mayInteract(p,s.source)&&world.mayInteract(p,target)&&world.mayInteract(p,support);
    }
    private static boolean candidate(Selection s,BlockPos target,BlockPos support) {
        var p=s.owner;var w=s.world;
        return w.hasChunkAt(target)&&w.hasChunkAt(support)&&w.getBlockState(target).isAir()&&w.getBlockEntity(target)==null
                &&w.mayInteract(p,s.source)&&w.mayInteract(p,target)&&w.mayInteract(p,support)
                &&p.distanceToSqr(s.source.getCenter())<=RANGE*RANGE&&p.distanceToSqr(target.getCenter())<=RANGE*RANGE
                &&w.clip(new ClipContext(p.getEyePosition(),s.source.getCenter(),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    private static boolean ready(ServerPlayer p) {
        if(p==null||p.connection==null||!p.server.isSameThread()||p.hasDisconnected()||p.isRemoved()||!p.isAlive()||p.isSpectator()
                ||p.server.getPlayerList().getPlayer(p.getUUID())!=p||AbilityConsumption.busy(p)
                ||HighPowerCombat.unsupportedReason(p).isPresent()||!HighPowerResources.ready(p))return false;
        var state=AbilityStorage.get(p);return CATEGORY.equals(state.category)&&state.level>=1&&state.canUse(ID)
                &&state.presets.currentContains(ID)&&SkillCatalog.enabled(CATEGORY,ID);
    }
    private static boolean valid(Selection s) {
        return s!=null&&SELECTED.get(s.owner.getUUID())==s&&s.owner.serverLevel()==s.world&&s.owner.isAlive()
                &&!s.owner.isRemoved()&&!s.owner.hasDisconnected()&&s.world.getGameTime()<s.expires
                &&water(s.world,s.source)&&s.world.getBlockState(s.source).equals(s.state);
    }
    private static boolean water(ServerLevel world,BlockPos pos) {
        return world.hasChunkAt(pos)&&world.getBlockEntity(pos)==null&&world.getBlockState(pos).is(Blocks.WATER)
                &&world.getFluidState(pos).isSource();
    }
    private static BlockHitResult ray(ServerPlayer p,ClipContext.Fluid fluids) {
        Vec3 start=p.getEyePosition(),dir=ClassicRaytrace.direction(p),end=start.add(dir.scale(RANGE));
        if(!finite(start)||!finite(dir)||!finite(end))return null;
        int x0=Math.floorDiv((int)Math.floor(Math.min(start.x,end.x)),16),x1=Math.floorDiv((int)Math.floor(Math.max(start.x,end.x)),16);
        int z0=Math.floorDiv((int)Math.floor(Math.min(start.z,end.z)),16),z1=Math.floorDiv((int)Math.floor(Math.max(start.z,end.z)),16);
        for(int x=x0;x<=x1;x++)for(int z=z0;z<=z1;z++)if(!p.serverLevel().hasChunk(x,z))return null;
        var hit=p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.OUTLINE,fluids,p));
        return hit.getType()==HitResult.Type.BLOCK?hit:null;
    }
    private static boolean finite(Vec3 p){return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    private static void remove(ServerPlayer p){SELECTED.remove(p.getUUID());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){SELECTED.values().removeIf(s->s.owner.server==e.getServer());}
}
