/* AcademyCraft 1.0.7 FreqTransmitterUI/Syncs sender-bound adapter. GPLv3. See NOTICE. */
package cn.academy.port.terminal;

import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import cn.academy.port.machine.MachineDeveloperBlockEntity;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.solar.ClassicSolarGenerators;
import cn.academy.port.wireless.ClassicWirelessMatrixBlockEntity;
import cn.academy.port.wireless.ClassicWirelessNodeBlockEntity;
import cn.academy.port.wireless.ClassicWirelessSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;

/** Clicked endpoints need the source four-block unobstructed world ray; selected source survives movement. */
public final class TerminalFrequencySessions {
    private static final Set<String> KINDS=Set.of("query_matrix","authorize_matrix","authorize_node","link_matrix","link_node");
    private static final long LIFETIME_NANOS=20_000_000_000L;
    private record Session(ServerLevel level,BlockPos selected,BlockEntity identity,String password,boolean matrix,long expiry){}
    private static final Map<ServerPlayer,Session> SESSIONS=new WeakHashMap<>();
    public static void remove(ServerPlayer player){SESSIONS.remove(player);}
    public static void clear(){SESSIONS.clear();}
    private static BlockEntity entity(ServerLevel level,BlockPos pos){return pos!=null&&level.hasChunkAt(pos)?level.getBlockEntity(pos):null;}
    /** Canonical multiblock origin, but never load a chunk for a client supplied position. */
    public static BlockPos origin(ServerLevel level,BlockPos clicked){
        var tile=entity(level,clicked);
        if(tile instanceof ClassicWirelessMatrixBlockEntity matrix){var root=matrix.origin();return root==null?null:root.getBlockPos();}
        if(tile instanceof MachineDeveloperBlockEntity machine){var root=machine.origin();return root==null?null:root.getBlockPos();}
        return tile==null?null:clicked;
    }
    public static boolean clicked(ServerPlayer player,BlockPos target){
        if(!TerminalNetwork.valid(player)||target==null||!player.serverLevel().hasChunkAt(target)||!player.serverLevel().mayInteract(player,target))return false;
        var from=player.getEyePosition();var to=from.add(player.getLookAngle().scale(4));
        if(!Double.isFinite(from.x)||!Double.isFinite(from.y)||!Double.isFinite(from.z)||!Double.isFinite(to.x)||!Double.isFinite(to.y)||!Double.isFinite(to.z))return false;
        // ServerLevel.clip reads block states: preflight every possible crossed chunk so that
        // a diagonal click at a chunk corner cannot cause the ray to load an absent neighbor.
        var start=BlockPos.containing(from);var end=BlockPos.containing(to);
        for(int cx=Math.min(start.getX()>>4,end.getX()>>4);cx<=Math.max(start.getX()>>4,end.getX()>>4);cx++)
            for(int cz=Math.min(start.getZ()>>4,end.getZ()>>4);cz<=Math.max(start.getZ()>>4,end.getZ()>>4);cz++)
                if(!player.serverLevel().hasChunkAt(new BlockPos(cx<<4,start.getY(),cz<<4)))return false;
        var hit=player.serverLevel().clip(new ClipContext(from,to,ClipContext.Block.OUTLINE,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK||!player.serverLevel().mayInteract(player,hit.getBlockPos()))return false;
        var canonical=origin(player.serverLevel(),hit.getBlockPos());var requested=origin(player.serverLevel(),target);
        return canonical!=null&&canonical.equals(requested)&&player.serverLevel().mayInteract(player,canonical);
    }
    private static boolean clean(String value){return value!=null&&value.length()<=128&&value.codePoints().noneMatch(Character::isISOControl);}
    private static Session session(ServerPlayer player,BlockPos selected,boolean matrix){
        var session=SESSIONS.get(player);if(session==null)return null;
        if(session.level!=player.serverLevel()||session.matrix!=matrix||System.nanoTime()-session.expiry>=0||!session.selected.equals(selected)||entity(session.level,session.selected)!=session.identity||session.identity.isRemoved()){remove(player);return null;}
        return session;
    }
    public static boolean request(ServerPlayer player,TerminalNetwork.FrequencyRequest request){
        if(!TerminalNetwork.valid(player)||request==null||request.kind()==null||!KINDS.contains(request.kind())||request.target()==null||request.selected()==null||!clean(request.password()))return false;
        var state=TerminalStorage.get(player);if(!state.terminalInstalled()||!state.isInstalled("freq_transmitter"))return false;
        var level=player.serverLevel();var selected=origin(level,request.selected());var target=origin(level,request.target());
        boolean accepted=false;String name="",status="e4";
        if(target!=null&&clicked(player,request.target())){
            var tile=entity(level,target);var graph=ClassicWirelessSavedData.get(level).graph();var own=ClassicWirelessSavedData.pos(target);
            switch(request.kind()){
                case "query_matrix" -> {
                    if(tile instanceof ClassicWirelessMatrixBlockEntity matrix&&matrix.available()){
                        var network=graph.networkAt(own);if(network!=null){accepted=true;name=network.ssid();selected=target;}else status="e0";
                    }
                }
                case "authorize_matrix" -> {
                    if(tile instanceof ClassicWirelessMatrixBlockEntity matrix&&matrix.available()&&target.equals(selected)){
                        var network=graph.networkAt(own);status="e1";
                        if(network!=null&&request.password().equals(graph.networkPassword(own))){accepted=true;name=network.ssid();SESSIONS.put(player,new Session(level,target,tile,request.password(),true,System.nanoTime()+LIFETIME_NANOS));}
                    }
                }
                case "authorize_node" -> {
                    if(tile instanceof ClassicWirelessNodeBlockEntity node&&node.available()&&target.equals(selected)){
                        status="e1";if(request.password().equals(node.getPassword())){accepted=true;name=node.getNodeName();SESSIONS.put(player,new Session(level,target,tile,request.password(),false,System.nanoTime()+LIFETIME_NANOS));}
                    }
                }
                case "link_matrix" -> {
                    status="e2";var session=selected==null?null:session(player,selected,true);
                    if(session!=null&&tile instanceof ClassicWirelessNodeBlockEntity node&&node.available()&&session.identity instanceof ClassicWirelessMatrixBlockEntity matrix&&matrix.available())accepted=graph.linkNode(ClassicWirelessSavedData.pos(selected),own,session.password);
                }
                case "link_node" -> {
                    status="e3";var session=selected==null?null:session(player,selected,false);
                    if(session!=null&&session.identity instanceof ClassicWirelessNodeBlockEntity node&&node.available()&&session.password.equals(node.getPassword())&&level.mayInteract(player,target)){
                        var generator=level.getCapability(ClassicSolarGenerators.IMAG_FLUX,target,tile.getBlockState(),tile,null);
                        var receiver=level.getCapability(MachineDevelopers.IMAG_FLUX,target,tile.getBlockState(),tile,null);
                        if(generator!=null)accepted=graph.linkGenerator(ClassicWirelessSavedData.pos(selected),own,session.password,true);
                        else if(receiver!=null)accepted=graph.linkReceiver(ClassicWirelessSavedData.pos(selected),own,session.password,true);
                    }
                }
                default -> {}
            }
        }
        if(accepted)status="e6";
        // Failed authorization ends the capability, while a source query never transmits its password.
        if(!accepted&&request.kind().startsWith("authorize_"))remove(player);
        TerminalNetwork.frequencyReply(player,request,selected==null?request.selected():selected,accepted,name,status);return accepted;
    }
    private TerminalFrequencySessions(){}
}
