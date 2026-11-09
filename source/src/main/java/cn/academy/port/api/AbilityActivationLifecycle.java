/* AcademyCraft1.0.7 CPData.sendToSelf activation event boundary. GPLv3; see NOTICE. */
package cn.academy.port.api;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.ActivationTransport;
import cn.academy.port.AcademyNetwork;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.common.NeoForge;
/** Explicit activation observers only; unified snapshots and existing scheduling remain separate. */
public final class AbilityActivationLifecycle {
    private AbilityActivationLifecycle(){}
    public static void post(Player player,AbilityProgress state,boolean active){
        if(active)NeoForge.EVENT_BUS.post(new AbilityActivateEvent(player,state));
        else NeoForge.EVENT_BUS.post(new AbilityDeactivateEvent(player,state));
    }
    public static void bind(ServerPlayer player,AbilityProgress state){
        state.bindActivationEvents(active->{if(player.server.isSameThread()){
            ActivationTransport.dispatch(state,active,value->post(player,state,value),value->AcademyNetwork.activationEvent(player,value));
        }});
    }
}
