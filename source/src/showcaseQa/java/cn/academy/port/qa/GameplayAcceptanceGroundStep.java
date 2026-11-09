package cn.academy.port.qa;

import cn.academy.port.AcademyConfig;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.client.MagneticGroundStepClientMotion;
import cn.academy.port.highpower.MagneticGroundStep;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Read-only measurements of real local input/native movement. No config opt-in or motion receiver injection. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
final class GameplayAcceptanceGroundStep {
    private static Vec3 origin,previous;
    private static int additions,stopAdditions,samples;
    private static double distance,maxStep,maxY;
    private static boolean measuring;
    private static volatile String failure;
    private GameplayAcceptanceGroundStep(){}
    static void assertHealthy(){if(failure!=null)throw new IllegalStateException("QA_GROUND_FAILED "+failure);}
    static void run(Minecraft mc,String action){
        assertHealthy();
        if(action.equals("prepare")){
            // A normal player option: never change the world skill gate or call a skill controller.
            mc.options.autoJump().set(false);
            var server=mc.getSingleplayerServer();require(server!=null,"integrated server required");var owner=mc.player.getUUID();
            server.execute(()->{try{
                var p=server.getPlayerList().getPlayer(owner);
                require(!p.isCreative()&&!p.isSpectator()&&!p.getAbilities().mayfly,"ordinary survival actor required");
                require(AcademyConfig.MAGNETIC_GROUND_STEP_ADMISSION.get()>=1,"real loaded server config completed versioned release migration");
                require(SkillCatalog.enabled("electromaster",MagneticGroundStep.ID),"production default/migrated world enables skill without QA opt-in");
                require(AbilityStorage.get(p).learned(MagneticGroundStep.ID),"real learned skill required");
                LogUtils.getLogger().info("QA_GROUND_ADMISSION_PASS version={} enabled=true survival=true optInMutation=false",AcademyConfig.MAGNETIC_GROUND_STEP_ADMISSION.get());
            }catch(RuntimeException e){failure=e.toString();}});
        }else if(action.equals("begin")){
            require(MagneticGroundStepClientMotion.canStartLocal(),"normal pre-payment input/ground contract must be ready: "+MagneticGroundStepClientMotion.startBlocker());
            origin=previous=mc.player.position();additions=stopAdditions=samples=0;distance=maxStep=maxY=0;measuring=true;
        }else if(action.equals("travel")){
            require(measuring&&samples>=10,"sustained native movement sample required");
            require(additions>=8,"at least eight distinct real server-authorized local additions required; a pose/one impulse cannot pass");
            require(distance>=3,"native local actor actually traversed at least three metres");
            require(maxStep<=.400001&&maxY<=.025001,"native flat-ground displacement stays within production step/height bounds");
            stopAdditions=additions;
            var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();Vec3 start=origin;
            server.execute(()->{try{var p=server.getPlayerList().getPlayer(owner);
                require(p.position().subtract(start).horizontalDistance()>=2.5,"real server accepted native movement packets");
                LogUtils.getLogger().info("QA_GROUND_TRAVEL_PASS additions={} localDistance={} serverDistance={} maxStep={} maxY={} samples={} actualNativeMovement=true",additions,distance,p.position().subtract(start).horizontalDistance(),maxStep,maxY,samples);
            }catch(RuntimeException e){failure=e.toString();}});
        }else if(action.equals("stopping")){
            require(measuring,"movement baseline required");stopAdditions=additions;
        }else if(action.equals("stopped")){
            require(measuring,"movement baseline required");
            require(session()==null,"release/GUI cancellation retires actual local assistance");
            require(additions<=stopAdditions+1,"no banked addition after the next normal input pass");
            measuring=false;
            var server=mc.getSingleplayerServer();var owner=mc.player.getUUID();
            server.execute(()->{try{require(!MagneticGroundStep.active(server.getPlayerList().getPlayer(owner)),"actual server authorization ended after real release/GUI cancellation");
                LogUtils.getLogger().info("QA_GROUND_STOP_PASS additions={} baseline={} nativeVelocityPreservedByProduction=true",additions,stopAdditions);
            }catch(RuntimeException e){failure=e.toString();}});
        }else throw new IllegalArgumentException("unknown ground acceptance action "+action);
    }
    @SubscribeEvent public static void tick(ClientTickEvent.Post e){
        var mc=Minecraft.getInstance();if(!measuring||mc.player==null)return;
        try{Vec3 now=mc.player.position(),step=now.subtract(previous);previous=now;samples++;
            distance+=step.horizontalDistance();maxStep=Math.max(maxStep,step.horizontalDistance());maxY=Math.max(maxY,Math.abs(step.y));
            Object s=session();if(s!=null)additions=Math.max(additions,(Integer)field(s,"applied"));
        }catch(RuntimeException ex){failure=ex.toString();}
    }
    private static Object session(){return field(MagneticGroundStepClientMotion.class,"session");}
    private static Object field(Object target,String name){try{var type=target instanceof Class<?> c?c:target.getClass();var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(target instanceof Class<?>?null:target);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException(message);}
}
