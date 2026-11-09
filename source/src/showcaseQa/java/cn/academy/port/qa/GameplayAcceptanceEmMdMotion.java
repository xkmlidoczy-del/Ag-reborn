package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.*;
import com.mojang.logging.LogUtils;
import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/** Read-only real movement/velocity witnesses. Environment prep never calls a movement runtime. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceEmMdMotion {
    private static final Logger LOG=LogUtils.getLogger();
    private static final Set<String> IDS=Set.of("mag_movement","polarity_tether","jet_engine","beam_step","reverse_jet_brake","magnetic_ground_step");
    private static final class Fixture {
        final UUID owner;final String id;Vec3 start,velocity,lastClient;double cp,budget,minCp,minBudget,maxDistance,maxY,minBrake=Double.POSITIVE_INFINITY,powered;
        int impulses;long clockBefore,baselineTick;boolean begun,passed,contextSeen,groundBaseline,nativeJumpSeen;
        Fixture(UUID owner,String id){this.owner=owner;this.id=id;}
    }
    private static Fixture fixture;
    private GameplayAcceptanceEmMdMotion(){}
    public static void run(Minecraft mc,String action){
        var server=mc.getSingleplayerServer();require(server!=null&&mc.player!=null,"integrated player");UUID owner=mc.player.getUUID();
        if(action.startsWith("setup:")){
            String id=action.substring(6);require(IDS.contains(id),"implemented motion "+id);LiveInputQa.run(mc,"setup:"+id);
            server.submit(()->{prepare(server.getPlayerList().getPlayer(owner),id);return true;}).join();LiveInputQa.assertHealthy();
        }else{
            if(action.equals("baseline")||action.equals("baseline-ground"))fixture.lastClient=mc.player.getDeltaMovement();
            server.submit(()->{
                var p=server.getPlayerList().getPlayer(owner);require(fixture!=null&&fixture.owner.equals(owner),"same movement owner");
                if(action.equals("baseline")||action.equals("baseline-ground")){fixture.groundBaseline=action.equals("baseline-ground");baseline(p);}
                else if(action.equals("assert"))verify(p);
                else if(action.equals("verify"))require(fixture.passed,"real motion assertion passed");
                else throw new IllegalArgumentException(action);return true;
            }).join();
        }
    }
    private static void prepare(ServerPlayer p,String id){
        fixture=new Fixture(p.getUUID(),id);var feet=p.blockPosition();var world=p.serverLevel();
        for(var old:world.getEntitiesOfClass(net.minecraft.world.entity.monster.Husk.class,p.getBoundingBox().inflate(64),net.minecraft.world.entity.monster.Husk::isNoAi))old.discard();
        for(int z=-2;z<=35;z++)for(int x=-3;x<=3;x++){
            world.setBlock(feet.offset(x,-1,z),Blocks.STONE.defaultBlockState(),3);
            for(int y=0;y<6;y++)world.setBlock(feet.offset(x,y,z),Blocks.AIR.defaultBlockState(),3);
        }
        if(id.equals("mag_movement")||id.equals("polarity_tether"))world.setBlock(feet.offset(0,2,9),Blocks.IRON_BLOCK.defaultBlockState(),3);
        if(id.equals("reverse_jet_brake"))p.teleportTo(p.getX(),p.getY()+32,p.getZ()); // Initial fall fixture only, before baseline.
        require(cn.academy.port.SkillCatalog.enabled(AbilityStorage.get(p).category,id),"actual enabled production skill, no QA opt-in");
        LOG.info("QA_GAMEPLAY_MOTION_PREPARED skill={} position={} noRuntimeStart=true",id,p.position());
    }
    private static void baseline(ServerPlayer p){
        var f=fixture;require(!f.begun,"one motion baseline");f.start=p.position();f.velocity=p.getDeltaMovement();f.maxY=p.getY();
        if(f.id.equals("beam_step")&&!f.groundBaseline)require(!p.onGround(),"real native Space jump must precede beam-step baseline");
        if(f.id.equals("reverse_jet_brake"))require(!p.onGround()&&f.velocity.y<-.1,"real natural fall before braking");
        f.clockBefore=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+f.id);f.baselineTick=p.serverLevel().getGameTime();
        f.cp=f.minCp=AbilityStorage.get(p).cp;f.budget=f.minBudget=HighPowerResources.availableCp(p);f.begun=true;
        LOG.info("QA_GAMEPLAY_MOTION_BASELINE skill={} position={} velocity={} nativeCp={} budget={}",f.id,f.start,f.velocity,f.cp,f.budget);
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void player(PlayerTickEvent.Post event){if(event.getEntity() instanceof ServerPlayer p)sample(p);}
    @SubscribeEvent public static void post(ServerTickEvent.Post event){var f=fixture;if(f!=null){var p=event.getServer().getPlayerList().getPlayer(f.owner);if(p!=null)sample(p);}}
    private static void sample(ServerPlayer p){
        var f=fixture;if(f==null||!f.begun||f.passed||!p.getUUID().equals(f.owner))return;
        f.minCp=Math.min(f.minCp,AbilityStorage.get(p).cp);f.minBudget=Math.min(f.minBudget,HighPowerResources.availableCp(p));
        f.maxDistance=Math.max(f.maxDistance,p.position().distanceTo(f.start));f.maxY=Math.max(f.maxY,p.getY());
        if(f.id.equals("reverse_jet_brake")&&NewSkillRuntime.active(p)&&!p.onGround()){
            f.contextSeen=true;f.minBrake=Math.min(f.minBrake,Math.abs(p.getDeltaMovement().y));
        }
        if(f.id.equals("magnetic_ground_step"))f.powered=Math.max(f.powered,MagneticGroundStep.poweredDistance(p));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void client(ClientTickEvent.Post event){
        var f=fixture;if(f==null||!f.begun||f.passed||!f.id.equals("beam_step"))return;var mc=Minecraft.getInstance();
        if(mc.player==null||!mc.player.getUUID().equals(f.owner))return;Vec3 velocity=mc.player.getDeltaMovement();
        if(f.lastClient!=null&&velocity.y-f.lastClient.y>.1&&!mc.player.onGround()){
            if(f.groundBaseline&&!f.nativeJumpSeen&&mc.options.keyJump.isDown()){
                f.nativeJumpSeen=true;LOG.info("QA_GAMEPLAY_BEAM_NATIVE_JUMP beforeY={} afterY={} normalSpace=true",f.lastClient.y,velocity.y);
            }else{f.impulses++;LOG.info("QA_GAMEPLAY_BEAM_STEP_REAL_IMPULSE count={} beforeY={} afterY={} actualPosition={}",f.impulses,f.lastClient.y,velocity.y,mc.player.position());}
        }
        f.lastClient=velocity;
    }
    private static void verify(ServerPlayer p){
        var f=fixture;require(f.begun&&!f.passed,"motion baseline required");sample(p);
        long clockAfter=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+f.id);
        boolean closedPayment=HighPowerSkills.contains(f.id)&&clockAfter>f.clockBefore&&clockAfter>f.baselineTick;
        require(f.minCp<f.cp||f.minBudget<f.budget||closedPayment,"real finite movement debit or closed successful spend clock required");
        if(f.id.equals("beam_step")){
            long[] grants=p.getPersistentData().getLongArray("academy:beam_step_grants");
            require((!f.groundBaseline||f.nativeJumpSeen)&&f.impulses==3&&grants.length==3,"three actual airborne boosts; fourth same-window request refused, grants="+Arrays.toString(grants)+" impulses="+f.impulses);
            require(grants[2]-grants[0]<32&&f.maxY>f.start.y+.5,"real same-window assistance increased height");
        }else if(f.id.equals("reverse_jet_brake")){
            require(f.contextSeen&&f.minBrake<Math.abs(f.velocity.y)*.7,"paid actual falling controller measurably reduced vertical speed before landing");
        }else{
            require(f.maxDistance>1,"actual server position changed, not only a pose or velocity assignment");
            if(f.id.equals("magnetic_ground_step"))require(f.powered>1,"actual approved native powered distance, not ordinary sprint alone");
        }
        f.passed=true;
        LOG.info("QA_GAMEPLAY_PASS skill={} witness=actual_authoritative_motion start={} end={} maxDistance={} maxY={} initialVelocity={} minAirBrakeY={} airborneImpulses={} poweredDistance={} minNativeCp={} minBudget={} paymentClockBefore={} paymentClockAfter={} closedFinitePayment={}",f.id,f.start,p.position(),f.maxDistance,f.maxY,f.velocity,f.minBrake,f.impulses,f.powered,f.minCp,f.minBudget,f.clockBefore,clockAfter,closedPayment);
    }
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_GAMEPLAY_MOTION_FAILED "+why);}
}
