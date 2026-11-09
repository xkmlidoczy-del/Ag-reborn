package cn.academy.port.qa;

import cn.academy.port.*;
import cn.academy.port.api.SkillPerformEvent;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.skill.*;
import java.lang.reflect.Field;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Real native player ticks and real R/W keys. Initial finite ledger is a declared fixture;
 * after prepare no resource, heat, skill context, movement or permission is written here. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class GameplayAcceptanceStormCycle {
    private static Fixture fixture;
    private static final class Fixture {
        final ServerPlayer owner; final AbilityProgress state; final String kind;
        final double initialCp,initialHeat; final boolean initialMayfly;
        StormWingSession session; boolean started,everCooling,resumed,exhausted;
        int hoverSamples,cruiseSamples,coolingSamples;
        double hoverCp,hoverHeat,cruiseCp,cruiseHeat,exhaustedCp;
        double beforeCp; boolean consumePending;
        Fixture(ServerPlayer p,String kind){owner=p;state=AbilityStorage.get(p);this.kind=kind;initialCp=state.cp;initialHeat=state.overload;initialMayfly=p.getAbilities().mayfly;}
    }
    private GameplayAcceptanceStormCycle(){}
    static boolean run(Minecraft mc,String action){
        if(action.equals("turn")){mc.player.setYRot(mc.player.getYRot()+180);mc.player.setXRot(0);return true;}
        return mc.getSingleplayerServer().submit(()->{
            var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
            if(action.startsWith("prepare:")){
                require(!StormWing.active(p)&&!p.getAbilities().instabuild,"finite survival before actual R start");
                String kind=action.substring(8);require(kind.equals("costs")||kind.equals("cycle"),"declared scenario");
                var s=AbilityStorage.get(p);require(s.category.equals("vecmanip")&&s.learned(StormWing.ID),"learned actual category");
                s.setSkillExp(StormWing.ID,1);s.cp=kind.equals("cycle")?2200:1000;
                s.overload=kind.equals("cycle")?s.maxOverload()*.84:0;
                s.cpDelay=AcademyConfig.CP_RECOVER_COOLDOWN.get();s.overloadDelay=AcademyConfig.OVERLOAD_RECOVER_COOLDOWN.get();s.overloadFine=true;
                AbilityStorage.save(p);AcademyNetwork.sync(p);fixture=new Fixture(p,kind);
                com.mojang.logging.LogUtils.getLogger().info("QA_GAMEPLAY_STORM_FIXTURE kind={} initialCp={} initialHeat={} maxHeat={} mastery=1 initialRecoveryDelayIsConfiguredMaximum=true noWritesAfterBaseline=true",kind,s.cp,s.overload,s.maxOverload());return true;
            }
            var f=fixture;require(f!=null&&f.owner==p&&f.state==AbilityStorage.get(p),"same owner/native ledger");
            if(action.equals("await-active"))return StormWing.active(p)&&StormWing.phase(p)==StormWingSession.ACTIVE;
            if(action.equals("check-costs")){
                require(f.started&&f.hoverSamples>=10&&f.cruiseSamples>=10,"real successful native hover and cruise samples");
                near(f.hoverCp*2,f.cruiseCp,1e-5,"actual observed cruise CP twice hover");
                near(f.hoverHeat*2,f.cruiseHeat,1e-5,"actual observed cruise heat twice hover");
                require(f.state.cp<f.initialCp,"finite ledger truly debited");
                com.mojang.logging.LogUtils.getLogger().info("QA_GAMEPLAY_STORM_COSTS_PASS skill=storm_wing hoverSamples={} cruiseSamples={} actualHoverCp={} actualCruiseCp={} actualHoverHeat={} actualCruiseHeat={} initialCp={} remainingCp={} realSkillPerformAndLedger=true",f.hoverSamples,f.cruiseSamples,f.hoverCp,f.cruiseCp,f.hoverHeat,f.cruiseHeat,f.initialCp,f.state.cp);return true;
            }
            if(action.equals("check-cycle")){
                require(f.started&&f.everCooling&&f.resumed&&f.coolingSamples>=10,"actual 85-percent sputter and 60-percent recovery");
                require(f.exhausted&&!StormWing.active(p),"flight naturally ended after finite CP exhaustion without stop key");
                require(f.exhaustedCp>=0&&f.exhaustedCp<f.session.sustainCp(),"actual terminal CP insufficient for next native sustain debit");
                require(p.getAbilities().mayfly==f.initialMayfly,"owned mayfly permission restored");
                com.mojang.logging.LogUtils.getLogger().info("QA_GAMEPLAY_STORM_CYCLE_PASS skill=storm_wing coolingSamples={} real85PercentEntry=true real60PercentRecovery=true nativeCoolingSpeedRatio=0.6 initialCp={} terminalCp={} naturalCpExhaustion=true noRefillOrStopAfterBaseline=true",f.coolingSamples,f.initialCp,f.exhaustedCp);return true;
            }
            throw new IllegalArgumentException(action);
        }).join();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void paid(SkillPerformEvent e){
        var f=fixture;if(f==null||e.player!=f.owner||!e.skill.equals(StormWing.ID))return;
        var session=session(e.player);require(session!=null&&session.phase()==StormWingSession.ACTIVE&&StormWing.input(e.player)>0,"real authenticated active flight");
        f.started=true;f.session=session;f.beforeCp=f.state.cp;f.consumePending=true;
        require(!e.creative&&!e.force&&e.cp>0&&Float.isFinite(e.cp)&&Float.isFinite(e.overload),"finite ordinary native sustain request");
        boolean hover=(boolean)field(session,"hovering");
        if(session.cooling()){
            if(!f.everCooling){require(session.heat()>=StormWingSession.SPUTTER,"actual entry at85 percent");com.mojang.logging.LogUtils.getLogger().info("QA_GAMEPLAY_STORM_COOLING_ENTRY heat={} cp={} speed={}",session.heat(),f.state.cp,session.flightSpeed());}
            f.everCooling=true;f.coolingSamples++;require(e.overload==0,"native cooling adds zero heat");
            near(session.flightSpeed(),session.speed()*StormWingSession.SPUTTER_SPEED,1e-5,"real session speed is60 percent");
        }else{
            if(f.everCooling&&!f.resumed){require(session.heat()<=StormWingSession.COOLED,"actual native recovery before full speed");f.resumed=true;near(session.flightSpeed(),session.speed(),1e-5,"actual speed restored");com.mojang.logging.LogUtils.getLogger().info("QA_GAMEPLAY_STORM_COOLING_RECOVERED heat={} cp={} speed={}",session.heat(),f.state.cp,session.flightSpeed());}
            if(hover){f.hoverSamples++;f.hoverCp=e.cp;f.hoverHeat=e.overload;}
            else{f.cruiseSamples++;f.cruiseCp=e.cp;f.cruiseHeat=e.overload;}
        }
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ServerTickEvent.Post event){
        var f=fixture;if(f==null||f.owner.isRemoved()||!f.started||f.exhausted)return;
        if(f.consumePending&&StormWing.active(f.owner)){require(f.state.cp<f.beforeCp,"successful native sustain actually decreased CP");f.consumePending=false;}
        if(f.kind.equals("cycle")&&!StormWing.active(f.owner)){
            require(f.consumePending&&f.state.cp<f.session.sustainCp(),"context ended on actual unaffordable native sustain request");
            require(f.state.overloadFine,"natural CP end rather than overload failure");
            require(f.owner.onGround()||f.owner.hasEffect(MobEffects.SLOW_FALLING),"native exhaustion grants safe descent");
            require(f.state.cooldowns.getOrDefault(StormWing.ID,0)>0,"native exhaustion closes cooldown");
            f.exhausted=true;f.exhaustedCp=f.state.cp;f.consumePending=false;
        }
    }
    private static StormWingSession session(ServerPlayer p){Object holds=staticField(StormWing.class,"HOLDS");Object h=((Map<?,?>)holds).get(p.getUUID());return h==null?null:(StormWingSession)field(h,"session");}
    private static Object staticField(Class<?> c,String name){try{Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(null);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static Object field(Object o,String name){try{Field f=o.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(o);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    private static void near(double a,double b,double tolerance,String why){require(Math.abs(a-b)<=tolerance,why+": "+a+" / "+b);}
    private static void require(boolean v,String why){if(!v)throw new IllegalStateException("QA_GAMEPLAY_STORM_FAILED "+why);}
}
