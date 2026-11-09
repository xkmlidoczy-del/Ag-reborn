/* AcademyCraft1.0.7 CPData activation server-event adaptation. GPLv3; see NOTICE. */
package cn.academy.port.api;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
/** Server self-event or explicit client observer message; received event type does not assign the predicted flag. */
public final class AbilityDeactivateEvent extends PlayerEvent {
    public final AbilityProgress state;
    public AbilityDeactivateEvent(ServerPlayer player,AbilityProgress state){this((Player)player,state);}
    public AbilityDeactivateEvent(Player player,AbilityProgress state){super(player);this.state=state;}
}
