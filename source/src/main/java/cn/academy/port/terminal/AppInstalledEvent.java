/* AcademyCraft 1.0.7 terminal event adapter. GPLv3. See NOTICE. */
package cn.academy.port.terminal;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
public final class AppInstalledEvent extends PlayerEvent {
    public final String app;
    public AppInstalledEvent(Player player,String app){super(player);this.app=app;}
}
