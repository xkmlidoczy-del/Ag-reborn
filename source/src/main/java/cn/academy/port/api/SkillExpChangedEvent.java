/* AcademyCraft1.0.7 SkillExpChangedEvent server-side adaptation. GPLv3. */
package cn.academy.port.api;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
/** Raw skill XP has changed; direct setters leave level progress untouched. */
public final class SkillExpChangedEvent extends PlayerEvent {
    public final String category,skill;
    public final AbilityProgress state;
    public SkillExpChangedEvent(ServerPlayer player,String category,String skill,AbilityProgress state){super(player);this.category=category;this.skill=skill;this.state=state;}
}
