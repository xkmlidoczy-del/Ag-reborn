package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AcademyNetwork;
import com.mojang.logging.LogUtils;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

/** Real legacy vector terrain effects. Only setup writes terrain/learning;
 * all destruction, conversion, drops and payment must follow ordinary GLFW. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceVmEnvironment {
    private static final Logger LOG=LogUtils.getLogger();
    private static final class Fixture {
        final UUID owner;final String mode,id;
        final Map<BlockPos,BlockState> blocks=new LinkedHashMap<>();
        final Set<BlockPos> ground=new LinkedHashSet<>(),obstacles=new LinkedHashSet<>(),breakEvents=new LinkedHashSet<>();
        final Set<BlockPos> stormBreaks=new LinkedHashSet<>(),explosionAffected=new LinkedHashSet<>();
        final Set<UUID> dropEntities=new LinkedHashSet<>();int trackedOutputDrops;Item output=Items.DIRT;
        BlockPos direct,guard;double cp,minCp;boolean begun,passed;
        Fixture(UUID owner,String mode){this.owner=owner;this.mode=mode;id=skill(mode);}
    }
    private static Fixture fixture;
    private GameplayAcceptanceVmEnvironment(){}
    public static void run(Minecraft mc,String action){
        var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated player");var owner=mc.player.getUUID();
        if(action.startsWith("setup:")){
            String mode=action.substring(6);require(Set.of("blast-block","ground-wave","ground-soft","storm-fragile","plasma-terrain").contains(mode),"known real terrain scenario");
            LiveInputQa.run(mc,"setup:"+skill(mode));
            server.submit(()->{prepare(server.getPlayerList().getPlayer(owner),mode);return true;}).join();LiveInputQa.assertHealthy();
        }else server.submit(()->{
            var p=server.getPlayerList().getPlayer(owner);var f=fixture;require(f!=null&&f.owner.equals(owner),"same terrain owner");
            switch(action){case "baseline"->baseline(p);case "assert"->verify(p);case "verify"->require(f.passed,"real native terrain result passed");default->throw new IllegalArgumentException(action);}
            return true;
        }).join();
    }
    private static void prepare(ServerPlayer p,String mode){
        fixture=new Fixture(p.getUUID(),mode);var f=fixture;var world=p.serverLevel();var feet=p.blockPosition();
        for(var mob:world.getEntitiesOfClass(net.minecraft.world.entity.Mob.class,p.getBoundingBox().inflate(32)))mob.discard();
        for(var item:world.getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(32)))item.discard();
        for(int x=-7;x<=7;x++)for(int z=-7;z<=16;z++){
            world.setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<6;y++)world.setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        world.setWeatherParameters(6000,0,false,false);
        var learned=AbilityStorage.get(p);learned.setSkillExp(f.id,mode.equals("ground-soft")?1f:0f);AbilityStorage.save(p);AcademyNetwork.sync(p);
        if(mode.equals("blast-block")){
            for(int x=-1;x<=1;x++)for(int y=0;y<=2;y++)for(int z=3;z<=5;z++)place(p,feet.offset(x,y,z),Blocks.DIRT.defaultBlockState());
            f.direct=feet.offset(0,1,3);f.guard=feet.offset(2,1,3);
            world.setBlock(f.guard,Blocks.BEDROCK.defaultBlockState(),3);
        }else if(mode.equals("ground-wave")){
            for(int z=1;z<=8;z++){
                var at=feet.offset(0,-1,z);f.ground.add(at);f.blocks.put(at,world.getBlockState(at));
                for(int y=0;y<=1;y++){var block=feet.offset(0,y,z);f.obstacles.add(block);place(p,block,Blocks.DIRT.defaultBlockState());}
            }
        }else if(mode.equals("ground-soft")){
            // Full ordinary learned mastery legitimately unlocks the source's
            // soft-earth clearing and drop=1. No random source/rule is replaced.
            for(int x=-3;x<=-1;x++)for(int z=-4;z<=-2;z++){var at=feet.offset(x,-1,z);f.ground.add(at);place(p,at,Blocks.DIRT.defaultBlockState());}
        }else if(mode.equals("storm-fragile")){
            // A dense legal glass ring makes normal random probes observable;
            // the owner has clear standing/hovering space and no input movement.
            for(int x=-6;x<=6;x++)for(int z=-6;z<=6;z++)if(Math.abs(x)>=3||Math.abs(z)>=3)
                for(int y=0;y<=5;y++)place(p,feet.offset(x,y,z),Blocks.GLASS.defaultBlockState());
            f.guard=feet.offset(2,1,0);world.setBlock(f.guard,Blocks.STONE.defaultBlockState(),3);
        }else{
            // Native explosions merge equal loot across source cells before
            // spawning it. Use a distinct ordinary soft block so natural dirt
            // below the lane cannot join this material's conservation account.
            f.output=Items.HAY_BLOCK;
            for(int x=-2;x<=2;x++)for(int y=0;y<=4;y++)for(int z=8;z<=12;z++)place(p,feet.offset(x,y,z),Blocks.HAY_BLOCK.defaultBlockState());
            f.guard=feet.offset(3,1,8);world.setBlock(f.guard,Blocks.BEDROCK.defaultBlockState(),3);
        }
        LOG.info("QA_GAMEPLAY_VM_TERRAIN_PREPARED mode={} skill={} actualMastery={} tracked={} direct={} setupOnly=true",mode,f.id,AbilityStorage.get(p).exp(f.id),f.blocks.keySet(),f.direct);
    }
    private static void place(ServerPlayer p,BlockPos at,BlockState state){p.serverLevel().setBlock(at,state,3);fixture.blocks.put(at,state);}
    private static void baseline(ServerPlayer p){
        var f=fixture;require(!f.begun&&AbilityStorage.get(p).canUse(f.id),"one baseline with normal usable skill");
        require(AcademyConfig.contextTerrain(p.serverLevel(),"vecmanip",f.id),"real unchanged server policy permits this terrain skill");
        require(p.onGround(),"native standing support before terrain release");
        if(f.mode.equals("blast-block")){
            var eye=p.getEyePosition();var end=eye.add(cn.academy.port.skill.ClassicRaytrace.direction(p).scale(4));
            var hit=p.serverLevel().clip(new net.minecraft.world.level.ClipContext(eye,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,p));
            var nativeCenter=new BlockPos((int)Math.round(hit.getLocation().x),(int)Math.round(hit.getLocation().y),(int)Math.round(hit.getLocation().z));
            LOG.info("QA_GAMEPLAY_VM_TERRAIN_REAL_DIRECT_AIM hit={} nativeBlastCenter={} required={}",hit.getBlockPos(),nativeCenter,f.direct);
            require(hit.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK&&hit.getBlockPos().equals(f.direct)&&nativeCenter.equals(f.direct),"actual native direct hit and rounded blast center agree on the prepared block");
        }
        for(var entry:f.blocks.entrySet())require(p.serverLevel().getBlockState(entry.getKey()).equals(entry.getValue()),"all prepared blocks still present before input");
        require(output(p)==0,"no preseeded terrain loot");f.cp=f.minCp=AbilityStorage.get(p).cp;f.begun=true;
        LOG.info("QA_GAMEPLAY_VM_TERRAIN_BASELINE mode={} nativeCP={} actualStates={}",f.mode,f.cp,f.blocks);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void tick(PlayerTickEvent.Post e){if(e.getEntity() instanceof ServerPlayer p)sample(p);}
    @SubscribeEvent public static void breaking(BlockEvent.BreakEvent e){
        var f=fixture;if(f!=null&&f.begun&&!f.passed&&e.getPlayer() instanceof ServerPlayer p&&p.getUUID().equals(f.owner)&&f.blocks.containsKey(e.getPos())){
            sample(p);f.breakEvents.add(e.getPos().immutable());
            if(f.mode.equals("storm-fragile")&&cn.academy.port.skill.StormWing.active(p)&&AbilityStorage.get(p).exp(f.id)<.15)
                f.stormBreaks.add(e.getPos().immutable());
        }
    }
    @SubscribeEvent public static void explosion(net.neoforged.neoforge.event.level.ExplosionEvent.Detonate e){
        var f=fixture;if(f==null||!f.begun||f.passed||!f.mode.equals("plasma-terrain"))return;
        if(e.getExplosion().getDirectSourceEntity() instanceof ServerPlayer p&&p.getUUID().equals(f.owner)){
            sample(p);for(var at:e.getAffectedBlocks())if(f.blocks.containsKey(at))f.explosionAffected.add(at.immutable());
            LOG.info("QA_GAMEPLAY_VM_PLASMA_NATIVE_EXPLOSION owner={} center={} radius={} realTrackedAffected={}",f.owner,e.getExplosion().center(),e.getExplosion().radius(),f.explosionAffected);
        }
    }
    @SubscribeEvent public static void drop(net.neoforged.neoforge.event.entity.EntityJoinLevelEvent e){
        var f=fixture;if(f==null||!f.begun||f.passed||e.getLevel().isClientSide()||!(e.getEntity() instanceof ItemEntity item)
                ||!item.getItem().is(f.output)||!f.blocks.containsKey(item.blockPosition())||!f.dropEntities.add(item.getUUID()))return;
        f.trackedOutputDrops+=item.getItem().getCount();
        LOG.info("QA_GAMEPLAY_VM_TERRAIN_NATIVE_DROP mode={} entity={} sourceCell={} item={} count={}",f.mode,item.getUUID(),item.blockPosition(),f.output,item.getItem().getCount());
    }
    private static void sample(ServerPlayer p){var f=fixture;if(f!=null&&f.begun&&!f.passed&&p.getUUID().equals(f.owner))f.minCp=Math.min(f.minCp,AbilityStorage.get(p).cp);}
    private static void verify(ServerPlayer p){
        var f=fixture;require(f.begun&&!f.passed,"one untouched native terrain opportunity");sample(p);
        require(f.minCp<f.cp,"real finite classic CP payment observed during native terrain callbacks");
        var states=new LinkedHashMap<BlockPos,BlockState>();for(var at:f.blocks.keySet())states.put(at,p.serverLevel().getBlockState(at));
        long removed=states.values().stream().filter(BlockState::isAir).count();int drops=output(p);
        require(!f.breakEvents.isEmpty(),"real native owner-attributed block protection callbacks");
        if(f.mode.equals("blast-block")){
            require(p.serverLevel().getBlockState(f.direct).isAir(),"directly aimed central dirt block really destroyed");
            require(p.serverLevel().getBlockState(f.guard).is(Blocks.BEDROCK),"adjacent unbreakable control retained");
            require(removed>0&&drops>0&&drops<=removed,"real native dirt drops bounded by actually destroyed blocks");
        }else if(f.mode.equals("ground-wave")){
            long changed=f.ground.stream().filter(at->p.serverLevel().getBlockState(at).is(Blocks.COBBLESTONE)||p.serverLevel().getBlockState(at).isAir()).count();
            long far=f.obstacles.stream().filter(at->at.getZ()>=p.blockPosition().getZ()+4&&p.serverLevel().getBlockState(at).isAir()).count();
            require(changed>0&&far>=4,"real forward surface conversion and distant above-ground obstacle destruction");
            require(drops==0,"ordinary legacy propagation intentionally destroys without native loot");
        }else if(f.mode.equals("ground-soft")){
            require(f.ground.stream().allMatch(at->p.serverLevel().getBlockState(at).isAir()),"all nine off-axis soft-earth cells cleared by learned mastery");
            require(drops==f.ground.size(),"one actual native dirt drop per cleared soft-earth cell");
        }else if(f.mode.equals("storm-fragile")){
            require(AbilityStorage.get(p).exp(f.id)<.15,"real unmastered storm-wing window, not mastery burst");
            require(f.stormBreaks.stream().anyMatch(at->p.serverLevel().getBlockState(at).isAir()),"low-mastery active wing's real random protection callback followed by glass destruction");
            require(p.serverLevel().getBlockState(f.guard).is(Blocks.STONE)&&drops==0,"hardness control preserved and legacy fragile clearing creates no invented loot");
        }else{
            require(f.explosionAffected.stream().anyMatch(at->p.serverLevel().getBlockState(at).isAir()),"real owner-attributed plasma explosion actually destroyed its affected terrain");
            require(p.serverLevel().getBlockState(f.guard).is(Blocks.BEDROCK),"native explosion retained unbreakable control");
            require(removed>0&&f.trackedOutputDrops>0&&f.trackedOutputDrops<=removed&&drops>0&&drops<=removed,"native plasma explosion produced bounded real hay loot from its actually destroyed fixture blocks");
            require(!cn.academy.port.skill.PlasmaCannon.active(p),"real launched plasma detonated and terminated normally");
        }
        f.passed=true;LOG.info("QA_GAMEPLAY_VM_TERRAIN_PASS mode={} skill={} actualStates={} removed={} nativeOutputItem={} nativeOutputUnits={} joinedNativeOutputUnits={} realOwnerBreakEvents={} nativeCPBefore={} nativeCPMinimum={} noPostBaselineWorldWrites=true",f.mode,f.id,states,removed,f.output,drops,f.trackedOutputDrops,f.breakEvents,f.cp,f.minCp);
    }
    private static int output(ServerPlayer p){var item=fixture.output;int n=p.getInventory().countItem(item);for(var e:p.serverLevel().getEntitiesOfClass(ItemEntity.class,p.getBoundingBox().inflate(32)))if(e.getItem().is(item))n+=e.getItem().getCount();return n;}
    private static String skill(String mode){return switch(mode){case "blast-block"->"dir_blast";case "storm-fragile"->"storm_wing";case "plasma-terrain"->"plasma_cannon";default->"ground_shock";};}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_GAMEPLAY_VM_TERRAIN_FAILED "+why);}
}
