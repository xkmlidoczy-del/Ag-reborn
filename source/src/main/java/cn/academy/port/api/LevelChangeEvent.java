/* AcademyCraft1.0.7 LevelChangeEvent server-side adaptation. GPLv3. */
package cn.academy.port.api;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
/** Primitive level/progress mutation precedes the source CPData NORMAL training reset. */
public final class LevelChangeEvent extends PlayerEvent {
    public final AbilityProgress state;
    private final Runnable commonEffects;private boolean applied;
    public LevelChangeEvent(ServerPlayer player,AbilityProgress state){this(player,state,state::applyLevelChangeEffects);}
    LevelChangeEvent(ServerPlayer player,AbilityProgress state,Runnable effects){super(player);this.state=state;commonEffects=effects;}
    void applyCommonEffects(){if(!applied){applied=true;commonEffects.run();}}
}
