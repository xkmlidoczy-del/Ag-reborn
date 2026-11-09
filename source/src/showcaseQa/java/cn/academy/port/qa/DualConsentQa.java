package cn.academy.port.qa;

import cn.academy.port.AbilityStorage;
import cn.academy.port.client.*;
import cn.academy.port.highpower.*;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Two actual client participants. Only fixtures arrange initial players/team/terrain;
 * invitation review and acceptance always enter production chat and native mouse handlers. */
@EventBusSubscriber(modid="academy", value=Dist.CLIENT)
public final class DualConsentQa {
    private static final Path SIGNALS=Path.of(System.getProperty("academy.qa.consentDir","dual-consent-signals"));
    private static final String KIND=System.getProperty("academy.qa.consentKind","");
    private static final String[] PEER_KEYS={"academy:classic_progress",HighPowerCombat.DATA_KEY,HighPowerResources.DATA_KEY};
    private static final Map<String,Tag> PEER_BEFORE=new HashMap<>();
    private static Vec3 peerBefore;
    private static double payerBefore;
    private static long spendClockBefore;
    private static UUID peerId;
    private static int attempts;
    private static volatile RuntimeException domainFailure;
    private static UUID offRecipient;
    private static int offTick=-1;
    private static Object offPulse;
    private static boolean offLeaseReleased,offExpired;
    private DualConsentQa(){}

    static boolean run(Minecraft mc,String action) {
        if(domainFailure!=null)throw domainFailure;
        require(!KIND.isBlank(),"explicit consentKind required");
        if(++attempts>6000)throw new IllegalStateException("QA_DUAL_CONSENT timeout action="+action);
        try {
            if(action.equals("await-peer"))return mc.level.players().size()>=2;
            if(action.startsWith("await:"))return Files.isRegularFile(SIGNALS.resolve(action.substring(6)));
            if(action.startsWith("signal:")){signal(action.substring(7));return true;}
            if(action.equals("prepare")) {
                server(mc,p->{
                    var peer=peer(p);peerId=peer.getUUID();var board=p.getScoreboard();var team=board.getPlayerTeam("aca_consent");if(team==null)team=board.addPlayerTeam("aca_consent");
                    board.addPlayerToTeam(p.getScoreboardName(),team);board.addPlayerToTeam(peer.getScoreboardName(),team);
                    for(var actor:List.of(p,peer)){actor.setGameMode(GameType.SURVIVAL);actor.getAbilities().flying=false;actor.onUpdateAbilities();}
                    for(var pos:BlockPos.betweenClosed(-8,-61,-4,16,-61,18))p.serverLevel().setBlock(pos,Blocks.STONE.defaultBlockState(),3);
                    for(var pos:BlockPos.betweenClosed(-8,-60,-4,16,-54,18))p.serverLevel().setBlock(pos,Blocks.AIR.defaultBlockState(),3);
                    if(KIND.equals("domain"))p.serverLevel().setBlock(new BlockPos(2,-54,2),Blocks.STONE.defaultBlockState(),3);
                    // Fixture only, before marking/saving/inviting and before the movement/resource baseline.
                    peer.teleportTo(-4.5,-60,-2.5);
                });signal("prepared");log("fixture-ready",KIND);return true;
            }
            if(action.equals("stage-peer")) {
                server(mc,p->{var peer=peer(p);peer.teleportTo(KIND.equals("domain")?2.5:-1.5,KIND.equals("domain")?-53:-60,KIND.equals("domain")?2.5:6.5);});
                return true;
            }
            if(action.equals("baseline")) {
                server(mc,p->{var peer=peer(p);peerBefore=peer.position();payerBefore=HighPowerResources.cp(p);spendClockBefore=spendClock(p);PEER_BEFORE.clear();
                    for(String key:PEER_KEYS){var t=peer.getPersistentData().get(key);PEER_BEFORE.put(key,t==null?null:t.copy());}
                    log("baseline","payer="+p.getUUID()+" peer="+peerId+" peerPos="+peerBefore+" cp="+payerBefore);
                });return true;
            }
            if(action.startsWith("click:"))return click(mc,action.substring(6));
            if(action.equals("review")) {
                UUID nonce=receivedNonce();if(nonce==null)return false;
                // ChatScreen's actual Enter handling invokes the registered CLIENT review command.
                // A server sendCommand cannot substitute for this local registered command.
                mc.setScreen(new ChatScreen("/"+(KIND.equals("pincer")?"aca_pincer_review":"aca_tactical_review")+" "+nonce));
                require(mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0),"native ChatScreen Enter accepted");
                log("review-requested","receivedNonce="+nonce);return true;
            }
            if(action.equals("domain-on")||action.equals("domain-off")) {
                // The two-tick native pulse need not survive a network effect snapshot.
                // Its actual owned identity/descending state is checked on CommandEvent below.
                mc.setScreen(new ChatScreen("/aca_domain_team "+(action.equals("domain-on")?"on":"off")));
                require(mc.screen.keyPressed(GLFW.GLFW_KEY_ENTER,0,0),"native ChatScreen sends participant's own consent command");return true;
            }
            if(action.equals("check")) {
                return server(mc,p->{var peer=peer(p);require(peerBefore!=null,"actual pre-accept baseline exists");
                    boolean moved,paid;Vec3 destination;
                    if(KIND.equals("pincer")) {var receipt=SpatialPincerConsent.receipt(p);if(receipt==null)return false;moved=receipt.moved();paid=receipt.paid();destination=receipt.destination();require(peerId.equals(receipt.traveler()),"receipt belongs to actual second participant");log("receipt",receipt);}
                    else if(KIND.equals("tactical")){var receipt=TacticalCoordinateFormation.receipt(p);if(receipt==null)return false;moved=receipt.moved();paid=receipt.paid();destination=receipt.destination();require(peerId.equals(receipt.traveler()),"template receipt belongs to actual second participant");log("receipt",receipt);}
                    else {require(MagneticDomain.active(p)&&!MagneticDomain.active(peer),"only payer owns the actual domain");require(MagneticDomainTeamSlowFall.consented(peer),"receiver's actual command consent retained");if(!peer.hasEffect(MobEffects.SLOW_FALLING))return false;require(!peer.onGround()&&peer.getDeltaMovement().y<0,"actual native descending shared pulse exists after opt-in");destination=null;moved=false;paid=spendClock(p)>spendClockBefore;}
                    if(!KIND.equals("domain")){require(moved&&paid,"server receipt confirms actual paid movement");require(destination!=null&&peer.position().distanceToSqr(destination)<.16,"peer actually reached production destination");require(peer.position().distanceToSqr(peerBefore)>4,"native peer displacement exceeds two metres");}
                    require(paid,"actual finite payer debit");
                    require(spendClock(p)>spendClockBefore,"actual closed successful spend; natural recovery may already hide CP dip");
                    for(String key:PEER_KEYS)require(Objects.equals(PEER_BEFORE.get(key),peer.getPersistentData().get(key)),"recipient resource/ability ledger unchanged: "+key);
                    log("PASS","kind="+KIND+" peerBefore="+peerBefore+" peerAfter="+peer.position()+" cpBefore="+payerBefore+" cpAfter="+HighPowerResources.cp(p)+" closedSpendClockBefore="+spendClockBefore+" closedSpendClockAfter="+spendClock(p));return true;
                });
            }
            if(action.equals("check-off")){server(mc,p->{var peer=peer(p);require(offLeaseReleased&&offExpired&&peer.getUUID().equals(offRecipient),"actual own off command immediately released lease and pulse expired airborne within native deadline");require(!MagneticDomainTeamSlowFall.consented(peer),"off revoked own consent");require(!peer.hasEffect(MobEffects.SLOW_FALLING),"no shared pulse refreshed after own off");log("OFF_PASS","recipient="+peer.getUUID()+" commandTick="+offTick+" ownedPulse="+System.identityHashCode(offPulse));});return true;}
            if(action.equals("finish")){signal("checked");log("DONE",KIND);return true;}
            throw new IllegalArgumentException("unknown dual consent action "+action);
        }catch(ReflectiveOperationException|java.io.IOException e){throw new IllegalStateException(e);}
    }
    @SubscribeEvent public static void command(CommandEvent event) {
        if(!KIND.equals("domain")||domainFailure!=null)return;
        String command=event.getParseResults().getReader().getString().trim();
        if(command.startsWith("/"))command=command.substring(1);
        if(!command.equals("aca_domain_team off"))return;
        try {
            require(event.getParseResults().getContext().getSource().getEntity() instanceof ServerPlayer,"off has actual player command source");
            var p=(ServerPlayer)event.getParseResults().getContext().getSource().getEntity();
            require(p.getUUID().equals(peerId),"off source is actual consenting second participant");
            require(MagneticDomainTeamSlowFall.consented(p)&&validTeamSource(p)&&!p.onGround()&&p.getDeltaMovement().y<0,"off arrives while actual consenting recipient is descending with a valid paid source lease");
            Object pulse=ownedPulse(p);
            require(pulse!=null&&pulse==p.getEffect(MobEffects.SLOW_FALLING)&&p.getEffect(MobEffects.SLOW_FALLING).getDuration()>0,"off begins with exact current source-owned native shared pulse");
            offRecipient=p.getUUID();offTick=p.server.getTickCount();offPulse=pulse;
            log("OFF_BEFORE","recipient="+offRecipient+" tick="+offTick+" pos="+p.position()+" velocity="+p.getDeltaMovement()+" pulseIdentity="+System.identityHashCode(pulse)+" duration="+p.getEffect(MobEffects.SLOW_FALLING).getDuration());
        }catch(RuntimeException failure){domainFailure=failure;}
    }
    @SubscribeEvent public static void beforeTick(ServerTickEvent.Pre event) {
        if(!KIND.equals("domain")||offTick<0||offExpired||domainFailure!=null)return;
        try {
            var p=event.getServer().getPlayerList().getPlayer(offRecipient);
            require(p!=null,"off recipient remains connected");
            int elapsed=event.getServer().getTickCount()-offTick;
            require(!p.onGround()&&p.getDeltaMovement().y<0,"off proof remains genuinely descending through native pulse expiry");
            require(!MagneticDomainTeamSlowFall.consented(p)&&ownedPulse(p)==null&&!hasNativeLease(p),"own off immediately removed consent and native lease before next player tick");
            if(!offLeaseReleased){require(elapsed<=1,"lease removal witnessed within one server boundary");offLeaseReleased=true;log("OFF_RELEASED","tick="+event.getServer().getTickCount()+" elapsed="+elapsed+" effectMayRemain="+p.hasEffect(MobEffects.SLOW_FALLING));}
            if(!p.hasEffect(MobEffects.SLOW_FALLING)){require(elapsed<=MagneticDomainSlowFall.DURATION+1,"owned pulse expires within its native two ticks plus observation boundary");offExpired=true;log("OFF_EXPIRED","elapsed="+elapsed+" pos="+p.position()+" velocity="+p.getDeltaMovement());}
            else require(elapsed<=MagneticDomainSlowFall.DURATION,"off cannot keep refreshing the prior pulse");
        }catch(RuntimeException failure){domainFailure=failure;}
    }
    private static Object ownedPulse(ServerPlayer p) {
        try {var m=MagneticDomainTeamSlowFall.class.getDeclaredMethod("ownedTeamPulse",ServerPlayer.class);m.setAccessible(true);return m.invoke(null,p);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static boolean hasNativeLease(ServerPlayer p) {
        try {var f=MagneticDomainSlowFall.class.getDeclaredField("LEASES");f.setAccessible(true);return ((Map<?,?>)f.get(null)).containsKey(p);}
        catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static boolean validTeamSource(ServerPlayer p) {
        try {
            var f=MagneticDomainTeamSlowFall.class.getDeclaredField("CONSENTS");f.setAccessible(true);Object c=((Map<?,?>)f.get(null)).get(p);if(c==null)return false;
            var source=c.getClass().getDeclaredField("source");source.setAccessible(true);var key=c.getClass().getDeclaredField("key");key.setAccessible(true);
            var m=MagneticDomainTeamSlowFall.class.getDeclaredMethod("validSource",c.getClass(),ServerPlayer.class,Object.class);m.setAccessible(true);
            return Boolean.TRUE.equals(m.invoke(null,c,source.get(c),key.get(c)));
        }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    private static boolean click(Minecraft mc,String target)throws ReflectiveOperationException {
        if(KIND.equals("pincer")) {
            if(!(mc.screen instanceof SpatialPincerScreen screen))return false;
            var l=SpatialPincerUiState.layout(screen.width,screen.height);
            var rect=switch(target){case "recipient"->l.recipient(0);case "invite"->l.button(1);case "accept"->l.button(0);default->throw new IllegalArgumentException(target);};
            require(target.equals("accept")==screen.review(),"correct native picker/review screen");mouse(mc,rect.x()+rect.width()/2.,rect.y()+rect.height()/2.);return true;
        }
        if(!(mc.screen instanceof TacticalCoordinateScreen screen))return false;
        var l=TacticalCoordinateUiState.layout(screen.width,screen.height);
        if(target.equals("recipient")){var p=l.panel();mouse(mc,p.x()+p.width()/2.,p.y()+59.5);return true;}
        var rect=switch(target){case "save"->l.button(0,0);case "choose","invite"->l.button(1,1);case "accept"->l.button(0,1);default->throw new IllegalArgumentException(target);};
        require(target.equals("accept")==screen.review(),"correct native tactical/review screen");mouse(mc,rect.x()+rect.width()/2.,rect.y()+rect.height()/2.);return true;
    }
    private static void mouse(Minecraft mc,double x,double y){require(mc.screen.mouseClicked(x,y,0),"normal native mouse press accepted");mc.screen.mouseReleased(x,y,0);}
    private static UUID receivedNonce()throws ReflectiveOperationException {
        Class<?> owner=KIND.equals("pincer")?SpatialPincerClient.class:TacticalCoordinateClient.class;
        var notices=owner.getDeclaredField("NOTICES");notices.setAccessible(true);
        var values=((Map<?,?>)notices.get(null)).values();if(values.isEmpty())return null;
        Object notice=values.iterator().next();var getter=notice.getClass().getDeclaredMethod("snapshot");getter.setAccessible(true);Object snapshot=getter.invoke(notice);
        return snapshot instanceof SpatialPincerClient.Snapshot p?p.nonce():((TacticalCoordinateClient.Snapshot)snapshot).nonce();
    }
    private static ServerPlayer peer(ServerPlayer p){return p.server.getPlayerList().getPlayers().stream().filter(v->v!=p).findFirst().orElseThrow();}
    private static long spendClock(ServerPlayer p){String id=switch(KIND){case "pincer"->"spatial_pincer_corridor";case "tactical"->"tactical_coordinate_formation";case "domain"->"magnetic_domain";default->throw new IllegalStateException(KIND);};return p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_"+id);}
    private static void signal(String state)throws java.io.IOException{Files.createDirectories(SIGNALS);Files.writeString(SIGNALS.resolve(state),KIND+"\n");}
    private static void server(Minecraft mc,java.util.function.Consumer<ServerPlayer> task){server(mc,p->{task.accept(p);return true;});}
    private static <T>T server(Minecraft mc,java.util.function.Function<ServerPlayer,T> task){require(mc.getSingleplayerServer()!=null,"host-owned server proof only");return mc.getSingleplayerServer().submit(()->task.apply(mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID()))).join();}
    private static void log(String stage,Object value){com.mojang.logging.LogUtils.getLogger().info("QA_DUAL_CONSENT stage={} {}",stage,value);}
    private static void require(boolean value,String message){if(!value)throw new IllegalStateException("QA_DUAL_CONSENT_FAILED "+message);}
}
