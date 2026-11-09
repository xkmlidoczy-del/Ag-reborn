/* AcademyCraft1.0.7 SkillExpAddedEvent server-side adaptation. GPLv3. */
package cn.academy.port.api;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
/** Carries the original float award request, including a request beyond mastery one. */
public final class SkillExpAddedEvent extends PlayerEvent {
    public final String category,skill;
    public final AbilityProgress state;
    public final float amount;
    public SkillExpAddedEvent(ServerPlayer player,String category,String skill,AbilityProgress state ,float amount){super(player);this.category=category;this.skill=skill;this.state=state;this.amount=amount;}
}
