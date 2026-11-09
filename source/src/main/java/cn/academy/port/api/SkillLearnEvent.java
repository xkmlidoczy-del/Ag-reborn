/* AcademyCraft1.0.7 SkillLearnEvent server-side adaptation. GPLv3. */
package cn.academy.port.api;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
/** Posted after the learned bit changes, before CPData's NORMAL refill/calculation. */
public final class SkillLearnEvent extends PlayerEvent {
    public final String category,skill;
    public final AbilityProgress state;
    private final Runnable commonEffects;private boolean applied;
    public SkillLearnEvent(ServerPlayer player,String category,String skill){super(player);this.category=category;this.skill=skill;state=cn.academy.port.AbilityStorage.get(player);commonEffects=state::applySkillLearnEffects;}
    SkillLearnEvent(ServerPlayer player,String category,String skill,AbilityProgress state,Runnable effects){super(player);this.category=category;this.skill=skill;this.state=state;commonEffects=effects;}
    void applyCommonEffects(){if(!applied){applied=true;commonEffects.run();}}
}
