/* AcademyCraft 1.0.7 terminal player-part lifecycle adapter. GPLv3. See NOTICE. */
package cn.academy.port.terminal;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
public final class TerminalEvents {
    @SubscribeEvent public static void death(LivingDeathEvent event){if(event.getEntity() instanceof ServerPlayer p)TerminalFrequencySessions.remove(p);}
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event){if(event.getEntity() instanceof ServerPlayer p){TerminalStorage.save(p);TerminalNetwork.sync(p);}}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)TerminalStorage.remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){if(event.getOriginal() instanceof ServerPlayer old&&event.getEntity() instanceof ServerPlayer p)TerminalStorage.clone(old,p);}
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event){if(event.getEntity() instanceof ServerPlayer p){TerminalFrequencySessions.remove(p);TerminalNetwork.sync(p);}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p){TerminalFrequencySessions.remove(p);TerminalNetwork.sync(p);}}
    @SubscribeEvent public static void stopping(ServerStoppingEvent event){event.getServer().getPlayerList().getPlayers().forEach(TerminalStorage::save);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){TerminalStorage.clear();}
    private TerminalEvents(){}
}
