package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.canon.CanonVfx;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native connected-player packet scope and budget; does not claim client pixels. */
@GameTestHolder("academy_canon_media")
@PrefixGameTestTemplate(false)
public final class CanonVfxNetworkRuntimeTests {
    private CanonVfxNetworkRuntimeTests() {}
    private record Client(ServerPlayer player,EmbeddedChannel channel) {}
    @GameTest(template="empty",batch="canon_vfx_network",timeoutTicks=100)
    public static void long_mental_mark_can_end_after_category_change_clears_learning(GameTestHelper h){
        List<Client> clients=new ArrayList<>();
        try{
            var owner=create(h,new Vec3(4.5,3,2.5),clients);var observer=create(h,new Vec3(4.5,3,8.5),clients);
            var state=AbilityStorage.get(owner.player);state.changeCategoryClassic("mental_out");state.setLevel(5);
            state.learn("mental_disrupt");state.setActivateState(true);drain(owner);drain(observer);
            Vec3 origin=owner.player.getEyePosition(),end=origin.add(0,0,4);
            CanonVfx.emit(owner.player,"mental_disrupt",origin,end,CanonVfx.Phase.ACTIVE,800);
            var marks=drain(observer);h.assertTrue(marks.size()==1&&marks.getFirst().duration()==800,"extended real packet reaches the nearby observer");drain(owner);
            state.changeCategoryClassic("aero_hand");h.assertFalse(state.learned("mental_disrupt"),"real category change has cleared old learning");
            CanonVfx.emit(owner.player,"mental_disrupt",origin,end,CanonVfx.Phase.END,1);
            var ends=drain(observer);h.assertTrue(ends.size()==1&&ends.getFirst().phase()==CanonVfx.Phase.END&&ends.getFirst().skill().equals("mental_disrupt"),"old owned mark retires after the category changes");
            CanonVfx.emit(owner.player,"mental_disrupt",origin,end,CanonVfx.Phase.ACTIVE,800);
            h.assertTrue(drain(observer).isEmpty(),"category mismatch still cannot start or renew control visuals");h.succeed();
        }finally{for(var c:clients){try{c.player.server.getPlayerList().remove(c.player);}finally{c.channel.finishAndReleaseAll();}}}
    }
    @GameTest(template="empty",batch="canon_vfx_network",timeoutTicks=100)
    public static void server_visual_packets_are_category_validated_nearby_and_rate_bounded(GameTestHelper h){
        List<Client> clients=new ArrayList<>();
        try {
            Client owner=create(h,new Vec3(4.5,3,2.5),clients);
            Client near=create(h,new Vec3(4.5,3,8.5),clients);
            Client far=create(h,new Vec3(4.5,3,82.5),clients);
            var state=AbilityStorage.get(owner.player);state.changeCategoryClassic("hydro_hand");state.setLevel(5);
            state.learn("hydro_whip");state.setActivateState(true);drain(owner);drain(near);drain(far);
            Vec3 origin=owner.player.getEyePosition(),end=origin.add(0,0,4);
            CanonVfx.emit(owner.player,"unknown_skill",origin,end,CanonVfx.Phase.START,20);
            CanonVfx.emit(owner.player,"hydro_whip",origin,new Vec3(Double.NaN,0,0),CanonVfx.Phase.START,20);
            CanonVfx.emit(owner.player,"hydro_whip",origin,origin.add(0,0,65),CanonVfx.Phase.START,20);
            CanonVfx.emit(owner.player,"dark_blade",origin,end,CanonVfx.Phase.START,20);
            h.assertTrue(drain(owner).isEmpty()&&drain(near).isEmpty(),"invalid identifiers or coordinates emit nothing");
            for(int i=0;i<12;i++)CanonVfx.emit(owner.player,"hydro_whip",origin,end,CanonVfx.Phase.HIT,20);
            var self=drain(owner);var observed=drain(near);var distant=drain(far);
            h.assertTrue(self.size()==8&&observed.size()==8,"owner and nearby observer receive only the eight-packet budget");
            h.assertTrue(distant.isEmpty(),"observer outside 64 blocks gets no event");
            for(var v:observed)h.assertTrue(v.owner().equals(owner.player.getUUID())&&v.skill().equals("hydro_whip")
                    &&v.phase()==CanonVfx.Phase.HIT&&v.origin().equals(origin)&&v.endpoint().equals(end),"actual event retains immutable server-confirmed data");
            h.succeed();
        } finally {for(var c:clients){try{c.player.server.getPlayerList().remove(c.player);}finally{c.channel.finishAndReleaseAll();}}}
    }
    private static Client create(GameTestHelper h,Vec3 pos,List<Client> clients){
        UUID id=UUID.randomUUID();var cookie=CommonListenerCookie.createInitial(new GameProfile(id,"CV"+id.toString().substring(0,8)),false);
        var player=new ServerPlayer(h.getLevel().getServer(),h.getLevel(),cookie.gameProfile(),cookie.clientInformation());
        var connection=new Connection(PacketFlow.SERVERBOUND);var channel=new EmbeddedChannel(connection);
        NetworkRegistry.configureMockConnection(connection);player.server.getPlayerList().placeNewPlayer(connection,player,cookie);
        player.setGameMode(GameType.SURVIVAL);player.moveTo(h.absoluteVec(pos),0,0);
        var client=new Client(player,channel);clients.add(client);return client;
    }
    private static List<CanonVfx.Visual> drain(Client client){
        client.channel.runPendingTasks();client.channel.flushOutbound();client.channel.runPendingTasks();
        var found=new ArrayList<CanonVfx.Visual>();Object packet;
        while((packet=client.channel.readOutbound())!=null){try{
            if(packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof CanonVfx.Visual v)found.add(v);
        }finally{ReferenceCountUtil.release(packet);}}
        return found;
    }
}
