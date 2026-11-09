/* AcademyCraft1.0.7 OverloadEvent adaptation. GPLv3; see NOTICE. */
package cn.academy.port.api;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
/** Fired at the pre-training overload cap, before overloadFine becomes false. Noncancelable. */
public final class AbilityOverloadEvent extends Event {
    public final ServerPlayer player;
    public AbilityOverloadEvent(ServerPlayer player){this.player=player;}
}
