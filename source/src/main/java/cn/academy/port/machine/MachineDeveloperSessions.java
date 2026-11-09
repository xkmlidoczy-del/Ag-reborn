package cn.academy.port.machine;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.develop.InductionFactors;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** Sender-bound, nonce-protected machine GUI sessions. Client never chooses a position or machine tier. */
public final class MachineDeveloperSessions {
    private record Session(MachineDeveloperBlockEntity machine,UUID token) {}
    private static final Map<UUID,Session> SESSIONS=new HashMap<>();
    private static final java.util.Set<String> PORTED_SKILLS=cn.academy.port.SkillAvailability.REGISTERED_LEARNABLE;
    private MachineDeveloperSessions() {}
    /** Trusted server-side context/snapshot hook; no client endpoint accepts a player-selected owner. */
    public static java.util.Optional<UUID> activeToken(ServerPlayer player){var session=SESSIONS.get(player.getUUID());return session==null?java.util.Optional.empty():java.util.Optional.of(session.token());}
    private static boolean reachable(ServerPlayer player,MachineDeveloperBlockEntity machine){
        if(player==null||player.isRemoved()||!player.isAlive()||player.isSpectator()||player.level()!=machine.getLevel()||!machine.available())return false;
        // Security adaptation: requests must remain within native reach of at least one source occupied cell.
        for(int part=0;part<8;part++){var pos=machine.getBlockPos().offset(MachineDeveloperBlock.offset(part,machine.facing()));if(player.canInteractWithBlock(pos,0)&&player.serverLevel().mayInteract(player,pos))return true;}
        return false;
    }
    public static boolean open(ServerPlayer player,MachineDeveloperBlockEntity machine){
        if(!player.serverLevel().getServer().isSameThread()||!reachable(player,machine))return false;
        UUID occupant=machine.user();
        if(occupant!=null&&!occupant.equals(player.getUUID()))return false;
        close(player);var session=new Session(machine,UUID.randomUUID());SESSIONS.put(player.getUUID(),session);machine.user(player.getUUID());
        AbilityStorage.save(player);AcademyNetwork.sync(player);send(player,session,"developer_machine");return true;
    }
    private static void send(ServerPlayer player,Session session,String kind){
        var machine=session.machine();var tag=new CompoundTag();tag.putString("kind",kind);tag.putString("session",session.token().toString());tag.putString("type",machine.developerType().name());tag.putDouble("energy",machine.battery().getEnergy());tag.putDouble("max_energy",machine.developerType().energy);tag.putLong("origin",machine.getBlockPos().asLong());
        var wireless=cn.academy.port.wireless.ClassicWirelessSavedData.getNonCreate(player.serverLevel());
        if(wireless!=null){var linked=wireless.graph().nodeForReceiver(cn.academy.port.wireless.ClassicWirelessSavedData.pos(machine.getBlockPos()));if(linked!=null){var pos=cn.academy.port.wireless.ClassicWirelessSavedData.blockPos(linked);if(player.serverLevel().hasChunkAt(pos)&&player.serverLevel().getBlockEntity(pos) instanceof cn.academy.port.wireless.ClassicWirelessNodeBlockEntity node)tag.putString("node_name",node.getNodeName());}}
        PacketDistributor.sendToPlayer(player,new AcademyNetwork.ClientData(tag));
    }
    private static Session authorized(ServerPlayer player,String token){
        var session=SESSIONS.get(player.getUUID());
        if(session==null||!session.token().toString().equals(token)||!player.getUUID().equals(session.machine().user())||!reachable(player,session.machine()))return null;
        return session;
    }
    /** Wireless UI bootstraps only the authenticated active machine session and canonical origin. */
    public static boolean matches(ServerPlayer player,String token,BlockPos origin){var session=authorized(player,token);return session!=null&&session.machine().getBlockPos().equals(origin);}
    /** Called directly from AcademyNetwork's same authenticated ingress, before the portable action dispatcher. */
    public static boolean request(ServerPlayer player,AcademyNetwork.Request request){
        if(player==null||request==null||request.value()==null||request.action()==null||!player.serverLevel().getServer().isSameThread())return false;
        if(request.action().equals("machine_close")){
            var session=SESSIONS.get(player.getUUID());if(session!=null&&session.token().toString().equals(request.value())){close(player);return true;}return false;
        }
        var fields=request.value().split(":",2);var session=authorized(player,fields[0]);if(session==null)return false;
        var developer=session.machine().developer();boolean accepted=switch(request.action()){
            case "machine_level" -> fields.length==1&&DevelopmentController.startLevel(player,developer,InductionFactors.inventory(player));
            case "machine_hp_learn" -> fields.length==2&&cn.academy.port.highpower.HighPowerProgression.requestLearning(player,developer,fields[1]);
            case "machine_learn" -> fields.length==2&&PORTED_SKILLS.contains(fields[1])&&DevelopmentController.startSkill(player,developer,fields[1]);
            case "machine_reset" -> fields.length==1&&DevelopmentController.start(player,developer,DevelopmentActions.reset(AbilityStorage.get(player),InductionFactors.inventory(player)));
            case "machine_abort" -> {if(fields.length!=1)yield false;DevelopmentController.abort(player);yield true;}
            default -> false;
        };
        if(accepted){AbilityStorage.save(player);AcademyNetwork.sync(player);send(player,session,"developer_machine_update");}return accepted;
    }
    /** Source onGuiClosed releases occupancy; it deliberately does not abort DevelopData. */
    public static void close(ServerPlayer player){var session=SESSIONS.remove(player.getUUID());if(session!=null&&player.getUUID().equals(session.machine().user()))session.machine().user(null);}
    public static void release(MachineDeveloperBlockEntity machine){
        for(var iterator=SESSIONS.entrySet().iterator();iterator.hasNext();){var entry=iterator.next();var session=entry.getValue();if(session.machine()!=machine)continue;
            if(machine.getLevel() instanceof net.minecraft.server.level.ServerLevel level){var player=level.getServer().getPlayerList().getPlayer(entry.getKey());if(player!=null)send(player,session,"developer_machine_close");}
            iterator.remove();
        }
        machine.user(null);
    }
    public static void tick(MachineDeveloperBlockEntity machine){
        if(machine.getLevel() instanceof net.minecraft.server.level.ServerLevel level){var occupant=machine.user();if(occupant==null)return;var player=level.getServer().getPlayerList().getPlayer(occupant);var session=SESSIONS.get(occupant);
            if(player==null||session==null||session.machine()!=machine||!reachable(player,machine)){release(machine);return;}
            send(player,session,"developer_machine_update");
        }
    }
    public static void clear(){for(var session:SESSIONS.values())session.machine().user(null);SESSIONS.clear();}
}
