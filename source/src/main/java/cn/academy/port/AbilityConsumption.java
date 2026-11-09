/* AcademyCraft1.0.7 common CPData/event-bus transport adaptation. GPLv3; see NOTICE. */
package cn.academy.port;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.api.*;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
/** Server-player binding only; the common ledger remains dependency-free and coldlinkable. */
public final class AbilityConsumption {
    private AbilityConsumption(){}
    static void bind(ServerPlayer player,AbilityProgress state){
        var world=player.serverLevel();state.bindCalculations(request->{
            AbilityCalculationEvent event=switch(request.kind){
                case MAX_CP -> new AbilityCalculationEvent.MaxCP(player,request.value);
                case MAX_OVERLOAD -> new AbilityCalculationEvent.MaxOverload(player,request.value);
                case CP_RECOVERY -> new AbilityCalculationEvent.CPRecoverSpeed(player,request.value);
                case OVERLOAD_RECOVERY -> new AbilityCalculationEvent.OverloadRecoverSpeed(player,request.value);
            };NeoForge.EVENT_BUS.post(event);request.value=event.value;
        });state.bindConsumption(AcademyConfig::consumptionConfig,request->{
            var event=new SkillPerformEvent(player,request.skill,request.overload,request.cp,request.force,request.creative);
            NeoForge.EVENT_BUS.post(event);request.cp=event.cp;request.overload=event.overload;
        },()->NeoForge.EVENT_BUS.post(new AbilityOverloadEvent(player)),
        ()->player.isAlive()&&!player.isRemoved()&&!player.isSpectator()&&player.serverLevel()==world&&world.getServer().isSameThread()&&AbilityStorage.cached(player)==state);state.bindCreativeMode(requested->player.getAbilities().instabuild);AbilityActivationLifecycle.bind(player,state);
    }
    /** Prevent wire/start/damage recursion while preserving direct core CPData-style reentry. */
    public static boolean busy(ServerPlayer player){var state=player==null?null:AbilityStorage.cached(player);return cn.academy.port.canon.CanonOffense.busy(player)||cn.academy.port.highpower.SpatialPincerConsent.committing(player)||cn.academy.port.highpower.TacticalCoordinateFormation.committing(player)||state!=null&&state.consumptionInProgress();}
}
