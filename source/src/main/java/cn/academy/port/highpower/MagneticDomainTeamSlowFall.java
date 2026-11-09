package cn.academy.port.highpower;

import java.util.*;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Opt-in, session/team scoped native slow falling. Never grants resources or domain ownership. */
@EventBusSubscriber(modid = "academy")
public final class MagneticDomainTeamSlowFall {
    public static final int MAX_RECIPIENTS = 64, MAX_CANDIDATES = 64;
    private static final Map<ServerPlayer, Consent> CONSENTS = new IdentityHashMap<>();
    private static final class Consent {
        final ServerPlayer recipient;
        final Object connection;
        final net.minecraft.server.level.ServerLevel world;
        final PlayerTeam team;
        ServerPlayer source;
        Object key;
        boolean suspended;
        Consent(ServerPlayer p) { recipient=p; connection=p.connection; world=p.serverLevel(); team=p.getTeam(); }
    }
    private MagneticDomainTeamSlowFall() {}

    @SubscribeEvent public static void commands(RegisterCommandsEvent e) {
        e.getDispatcher().register(Commands.literal("aca_domain_team")
            .then(Commands.literal("on").executes(c -> command(c.getSource().getPlayerOrException(), true)))
            .then(Commands.literal("off").executes(c -> command(c.getSource().getPlayerOrException(), false)))
            .then(Commands.literal("status").executes(c -> { feedback(c.getSource().getPlayerOrException()); return 1; })));
    }
    private static int command(ServerPlayer p, boolean enabled) {
        boolean result = consent(p, enabled); feedback(p); return result ? 1 : 0;
    }
    /** The command sender alone can opt in; no target argument or client-supplied identity. */
    public static boolean consent(ServerPlayer p, boolean enabled) {
        if (!thread(p)) return false;
        if (!enabled) { remove(p); return true; }
        Consent old = CONSENTS.get(p);
        if (old != null && validIdentity(old)) return !old.suspended; // on is idempotent; off/on is an explicit new decision.
        remove(p);
        if (!eligible(p) || p.getTeam() == null || CONSENTS.size() >= MAX_RECIPIENTS) return false;
        CONSENTS.put(p, new Consent(p)); return true;
    }
    public static boolean consented(ServerPlayer p) {
        Consent c = CONSENTS.get(p); return c != null && validIdentity(c) && !c.suspended;
    }
    private static void feedback(ServerPlayer p) {
        Consent c = CONSENTS.get(p);
        String state = c == null || !validIdentity(c) ? "off" : c.suspended ? "suspended" : "on";
        String fallback = switch(state) {
            case "on" -> "Teammate slow falling: ON for this session and current team only. Leave/rejoin or team change clears consent. No shared shield.";
            case "suspended" -> "Teammate slow falling: suspended by native effect refusal or another potion. Use off, then on to consent again.";
            default -> "Teammate slow falling: OFF. Requires a current team and survival player. /aca_domain_team on to opt in for this session/team only.";
        };
        p.sendSystemMessage(Component.translatableWithFallback("academy.highpower.domain_team."+state, fallback));
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void tick(ServerTickEvent.Post e) {
        for (Consent c : new ArrayList<>(CONSENTS.values())) if (c.recipient.server == e.getServer()) update(c);
    }
    public static void tickRecipient(ServerPlayer p) { Consent c=CONSENTS.get(p); if(c!=null) update(c); }
    private static void update(Consent c) {
        ServerPlayer p=c.recipient;
        if (!validIdentity(c)) { remove(p); return; }
        if (c.suspended) return;
        if (MagneticDomain.active(p)) { releaseRecipient(p); return; } // own domain owns its native lease.
        if (c.key != null && MagneticDomainSlowFall.yielded(p,c.key)) {
            c.suspended=true; releaseRecipient(p); feedback(p); return;
        }
        if (c.key != null && !validSource(c,c.source,c.key)) releaseRecipient(p);
        if (c.key == null) {
            // Never take over an old two-tick effect, merged potion or hidden-effect chain.
            if (p.hasEffect(MobEffects.SLOW_FALLING)) return;
            List<ServerPlayer> candidates = c.world.getEntitiesOfClass(ServerPlayer.class,
                new AABB(p.position(),p.position()).inflate(MagneticDomain.RADIUS));
            if(candidates.size()>MAX_CANDIDATES) return;
            candidates.sort(Comparator.comparing(ServerPlayer::getUUID));
            for(ServerPlayer source:candidates) {
                Object key=MagneticDomain.teamLeaseKey(source);
                if(key!=null && validSource(c,source,key)) { c.source=source;c.key=key;break; }
            }
        }
        if(c.key!=null) {
            ServerPlayer source=c.source; Object key=c.key;
            MagneticDomainSlowFall.refresh(p,source,key,() -> c.key==key && c.source==source && validSource(c,source,key));
        }
    }
    private static boolean validIdentity(Consent c) {
        ServerPlayer p=c.recipient;
        return CONSENTS.get(p)==c && thread(p) && eligible(p) && p.connection==c.connection
            && p.serverLevel()==c.world && p.getTeam()==c.team && c.team!=null;
    }
    private static boolean validSource(Consent c,ServerPlayer source,Object key) {
        ServerPlayer p=c.recipient;
        if(!validIdentity(c) || c.suspended || source==null || source==p || !eligible(source)
            || source.serverLevel()!=c.world || source.getTeam()!=c.team
            || !MagneticDomain.teamLeaseCurrent(source,key)) return false;
        Vec3 a=MagneticDomain.center(source), b=p.position().add(0,1,0);
        if(!finite(a)||!finite(b)||a.distanceToSqr(b)>MagneticDomain.RADIUS*MagneticDomain.RADIUS) return false;
        // Check all intersected chunks before a native clip; no implicit world loading.
        for(int x=((int)Math.floor(Math.min(a.x,b.x)))>>4;x<=((int)Math.floor(Math.max(a.x,b.x)))>>4;x++)
            for(int z=((int)Math.floor(Math.min(a.z,b.z)))>>4;z<=((int)Math.floor(Math.max(a.z,b.z)))>>4;z++)
                if(!c.world.hasChunk(x,z)) return false;
        return c.world.clip(new ClipContext(a,b,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,source)).getType()==HitResult.Type.MISS;
    }
    private static boolean eligible(ServerPlayer p) {
        return thread(p) && p.connection!=null && p.connection.isAcceptingMessages() && !p.hasDisconnected() && p.isAlive() && !p.isRemoved()
            && p.serverLevel().getEntity(p.getUUID())==p && !p.isSpectator()
            && !p.getAbilities().instabuild && !p.getAbilities().invulnerable && !p.getAbilities().flying
            && !p.isInWaterOrBubble() && !p.isInLava()
            && !p.noPhysics && !p.isPassenger() && !p.isVehicle() && !p.isSleeping() && !p.isFallFlying()
            && p.containerMenu==p.inventoryMenu && finite(p.position()) && finite(p.getDeltaMovement());
    }
    /** Capture only our exact current owned pulse when a separate self-domain context is created. */
    static net.minecraft.world.effect.MobEffectInstance ownedTeamPulse(ServerPlayer p) {
        Consent c = CONSENTS.get(p);
        return c != null && c.key != null ? MagneticDomainSlowFall.ownedEffect(p, c.key) : null;
    }
    public static void releaseRecipient(ServerPlayer p) {
        Consent c=CONSENTS.get(p); if(c==null || c.key==null) return;
        if (MagneticDomainSlowFall.yielded(p,c.key)) c.suspended=true;
        MagneticDomainSlowFall.release(p,c.key);c.key=null;c.source=null;
    }
    static void releaseSource(ServerPlayer source,Object key) {
        for(Consent c:new ArrayList<>(CONSENTS.values())) if(c.source==source && c.key==key) releaseRecipient(c.recipient);
    }
    public static void remove(ServerPlayer p) { releaseRecipient(p); CONSENTS.remove(p); }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e) { if(e.getEntity() instanceof ServerPlayer p)remove(p); }
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e) { if(e.getEntity() instanceof ServerPlayer p)remove(p); }
    @SubscribeEvent public static void clone(PlayerEvent.Clone e) { if(e.getOriginal() instanceof ServerPlayer p)remove(p); if(e.getEntity() instanceof ServerPlayer p)remove(p); }
    @SubscribeEvent public static void stopped(ServerStoppedEvent e) { for(ServerPlayer p:new ArrayList<>(CONSENTS.keySet()))if(p.server==e.getServer())remove(p); }
    private static boolean finite(Vec3 p) { return Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z); }
    private static boolean thread(ServerPlayer p) {return p!=null&&p.server!=null&&p.server.isSameThread();}
}
