/* AcademyCraft1.0.7 CategoryChangeEvent server-side adaptation. GPLv3; see NOTICE. */
package cn.academy.port.api;

import cn.academy.port.core.AbilityProgress;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Posted after category/learned/raw-slot mutation, before the source common category handlers. */
public final class CategoryChangeEvent extends PlayerEvent {
    public final AbilityProgress state;
    private final Runnable commonEffects;
    private boolean applied;
    CategoryChangeEvent(ServerPlayer player,AbilityProgress state,Runnable commonEffects){super(player);this.state=state;this.commonEffects=commonEffects;}
    void applyCommonEffects(){if(!applied){applied=true;commonEffects.run();}}
}
