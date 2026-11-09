package cn.academy.port.tutorial;

import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/** Source-compatible notification, fired on both logical sides for each new non-default tutorial. */
public final class TutorialActivatedEvent extends PlayerEvent {
    public final ClassicTutorials.Page tutorial;
    public TutorialActivatedEvent(Player player,ClassicTutorials.Page tutorial) { super(player);this.tutorial=tutorial; }
}
