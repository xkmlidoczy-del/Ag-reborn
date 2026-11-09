package cn.academy.port.tutorial;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

public final class TutorialEvents {
    private static void obtained(net.minecraft.world.entity.player.Player player,ItemStack stack,TutorialState.EventKind kind) {
        if(player instanceof ServerPlayer server&&!stack.isEmpty()&&TutorialStorage.get(server).recordObtained(BuiltInRegistries.ITEM.getKey(stack.getItem()).toString(),kind))TutorialStorage.save(server);
    }
    @SubscribeEvent public static void crafted(PlayerEvent.ItemCraftedEvent event) { obtained(event.getEntity(),event.getCrafting(),TutorialState.EventKind.CRAFT); }
    @SubscribeEvent public static void smelted(PlayerEvent.ItemSmeltedEvent event) { obtained(event.getEntity(),event.getSmelting(),TutorialState.EventKind.SMELT); }
    @SubscribeEvent public static void pickup(ItemEntityPickupEvent.Post event) { obtained(event.getPlayer(),event.getOriginalStack(),TutorialState.EventKind.PICKUP); }
    @SubscribeEvent public static void tick(PlayerTickEvent.Post event) {
        if(!(event.getEntity() instanceof ServerPlayer player))return;
        var state=TutorialStorage.get(player);boolean acquired=state.tutorialAcquired();
        state.tick(id->{NeoForge.EVENT_BUS.post(new TutorialActivatedEvent(player,ClassicTutorials.page(id)));TutorialNetwork.activated(player,id);},
            ()->player.serverLevel().addFreshEntity(new ItemEntity(player.serverLevel(),player.getX(),player.getY()+1,player.getZ(),new ItemStack(TutorialModule.ITEM.get()))),
            ()->{TutorialStorage.save(player);TutorialNetwork.sync(player);});
        if(acquired!=state.tutorialAcquired())TutorialStorage.save(player);
    }
    @SubscribeEvent public static void login(PlayerEvent.PlayerLoggedInEvent event) { if(event.getEntity() instanceof ServerPlayer player){TutorialStorage.save(player);TutorialNetwork.sync(player);} }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event) { if(event.getEntity() instanceof ServerPlayer player)TutorialStorage.remove(player); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone event) { if(event.getOriginal() instanceof ServerPlayer old&&event.getEntity() instanceof ServerPlayer player)TutorialStorage.clone(old,player); }
    @SubscribeEvent public static void respawn(PlayerEvent.PlayerRespawnEvent event) { if(event.getEntity() instanceof ServerPlayer player)TutorialNetwork.sync(player); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event) { if(event.getEntity() instanceof ServerPlayer player)TutorialNetwork.sync(player); }
    @SubscribeEvent public static void stopping(ServerStoppingEvent event) { event.getServer().getPlayerList().getPlayers().forEach(TutorialStorage::save); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent event) { TutorialStorage.clear(); }
    private TutorialEvents() {}
}
