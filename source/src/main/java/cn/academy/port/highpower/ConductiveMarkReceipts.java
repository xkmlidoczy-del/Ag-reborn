package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ThunderNetRules;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Server-memory proof for NEW141 only. Legacy/admin NBT never proves ownership or accepted Arc damage. */
@EventBusSubscriber(modid="academy")
public final class ConductiveMarkReceipts {
    public static final String SOURCE="electromaster.forked_arc";
    public static final int MAX_PER_CASTER=64;
    private static final Map<ServerPlayer,LinkedHashMap<UUID,Receipt>> RECEIPTS=new IdentityHashMap<>();
    private static long nextIssue;
    public static final class Capture {
        private final LegacySingleKeyServerIdentity identity;
        private final LivingEntity target;
        private final UUID uuid;
        private final int entityId,preset;
        private final long revision,issued;
        private boolean consumed;
        private Capture(ServerPlayer p,LivingEntity target){
            identity=new LegacySingleKeyServerIdentity(p,0,++nextIssue,AbilityStorage.get(p));
            this.target=target;uuid=target.getUUID();entityId=target.getId();issued=now(p);
            preset=identity.state().presets.current();revision=identity.state().presets.revision();
        }
    }
    private record Receipt(LegacySingleKeyServerIdentity identity,LivingEntity target,UUID uuid,int entityId,long issued,String source){}
    private ConductiveMarkReceipts(){}
    /** Must be captured immediately before the primary Arc's native attack, never from branch/admin marks. */
    public static Capture capture(ServerPlayer p,LivingEntity target){
        return live(p)&&targetLive(p,target)&&nextIssue<Long.MAX_VALUE?new Capture(p,target):null;
    }
    /** The trusted Arc callsite invokes this only on an accepted return, after every native callback. */
    public static boolean acceptedArc(Capture c){
        if(c==null||c.consumed)return false;c.consumed=true;
        ServerPlayer p=c.identity.owner();
        if(!live(p)||!c.identity.owns(p)||now(p)!=c.issued||c.identity.state().presets.current()!=c.preset
                ||c.identity.state().presets.revision()!=c.revision||!targetLive(p,c.target)
                ||c.target.getId()!=c.entityId||!c.target.getUUID().equals(c.uuid))return false;
        var entries=RECEIPTS.computeIfAbsent(p,key->new LinkedHashMap<>());prune(entries);
        entries.remove(c.uuid);
        while(entries.size()>=MAX_PER_CASTER)entries.remove(entries.keySet().iterator().next());
        entries.put(c.uuid,new Receipt(c.identity,c.target,c.uuid,c.entityId,c.issued,SOURCE));return true;
    }
    public static boolean has(ServerPlayer p,LivingEntity target){
        if(!live(p)||!targetLive(p,target))return false;
        var entries=RECEIPTS.get(p);if(entries==null)return false;
        Receipt r=entries.get(target.getUUID());
        if(r==null)return false;
        if(!valid(r)){entries.remove(target.getUUID());if(entries.isEmpty())RECEIPTS.remove(p);return false;}
        return r.target==target&&r.entityId==target.getId()&&r.uuid.equals(target.getUUID());
    }
    private static boolean valid(Receipt r){
        ServerPlayer p=r.identity.owner();return SOURCE.equals(r.source)&&live(p)&&r.identity.owns(p)
                &&ThunderNetRules.receiptLive(r.issued,now(p))&&targetLive(p,r.target)
                &&r.target.getId()==r.entityId&&r.uuid.equals(r.target.getUUID());
    }
    private static boolean live(ServerPlayer p){return p!=null&&p.server.isSameThread()&&p.connection!=null&&!p.hasDisconnected()
            &&ElectromasterPrototype.ready(p)&&AbilityStorage.get(p).category.equals("electromaster")
            &&AbilityStorage.get(p).activated&&AbilityStorage.get(p).overloadFine
            &&AbilityStorage.get(p).learned(HighPowerSkills.FORKED_ARC)&&ThunderNetRules.clock(now(p))
            &&p.serverLevel().getEntity(p.getId())==p&&HighPowerCombat.unsupportedReason(p).isEmpty();}
    private static boolean targetLive(ServerPlayer p,LivingEntity e){return e!=null&&e!=p&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()
            &&e.level()==p.serverLevel()&&p.serverLevel().getEntity(e.getId())==e&&p.serverLevel().getEntity(e.getUUID())==e;}
    private static long now(ServerPlayer p){return p.server.overworld().getGameTime();}
    private static void prune(Map<UUID,Receipt> entries){entries.values().removeIf(r->!valid(r));}
    public static void remove(ServerPlayer p){RECEIPTS.remove(p);}
    public static void clear(){RECEIPTS.clear();}
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){
        RECEIPTS.entrySet().removeIf(entry->{if(entry.getKey().server!=e.getServer())return false;prune(entry.getValue());return entry.getValue().isEmpty();});
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(!e.isCanceled()){if(e.getEntity() instanceof ServerPlayer p)remove(p);RECEIPTS.values().forEach(entries->entries.values().removeIf(r->r.target==e.getEntity()));}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
}
