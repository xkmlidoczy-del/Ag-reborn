package cn.academy.port.tutorial;

import cn.academy.port.AcademyNetwork;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Uses the existing common payload, keeping guide server code free of client classes. */
public final class TutorialNetwork {
    private static void send(ServerPlayer player,String kind,String activated) {
        var tag=TutorialStorage.encode(TutorialStorage.get(player));tag.putString("kind",kind);
        if(activated!=null)tag.putString("tutorial",activated);
        PacketDistributor.sendToPlayer(player,new AcademyNetwork.ClientData(tag));
    }
    public static void sync(ServerPlayer player) { send(player,"tutorial_state",null); }
    public static void activated(ServerPlayer player,String id) { send(player,"tutorial_activate",id); }
    public static void open(ServerPlayer player) {
        if(player.isRemoved()||!player.isAlive()||player.isSpectator())return;
        send(player,"tutorial_open",null);TutorialStorage.get(player).markOpened();TutorialStorage.save(player);
    }
    public static boolean request(ServerPlayer player,AcademyNetwork.Request request) {
        if(player.isRemoved()||!player.isAlive()||player.isSpectator()||!player.serverLevel().getServer().isSameThread()||!request.action().equals("tutorial_open")||!request.value().isEmpty())return false;
        // The guide opens directly from its actual held item. A terminal application is a separate future ingress.
        if(!player.getMainHandItem().is(TutorialModule.ITEM.get())&&!player.getOffhandItem().is(TutorialModule.ITEM.get()))return false;
        open(player);return true;
    }
    private TutorialNetwork() {}
}
