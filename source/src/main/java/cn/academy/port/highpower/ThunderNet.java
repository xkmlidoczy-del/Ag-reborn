package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ThunderNetRules;
import io.netty.handler.codec.DecoderException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW141. Fixed server locks, caster-origin native attacks, no damage on decorative network edges. */
@EventBusSubscriber(modid="academy")
public final class ThunderNet {
    public static final String ID="thunder_net";
    private static final String RECAST="thunder_net_recast_until";
    public static final int PENDING=0,HOLDING=1,TAP=2,ENDED=3;
    public static final int LOCKED=0,HIT=1,REFUSED=2;
    public static final int NORMAL=0,REFUSAL=1,EXPIRED=2,INVALIDATED=3;
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input){}
    public record Node(int entity,UUID uuid,Vec3 point,int status){}
    private static final class Lock {
        final LivingEntity target;final UUID uuid;final int id;
        Vec3 point;int status=LOCKED;
        Lock(LivingEntity target){this.target=target;uuid=target.getUUID();id=target.getId();point=target.getBoundingBox().getCenter();}
        Node node(){return new Node(id,uuid,point,status);}
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;final HighPowerInput.Input input;
        final int preset;final long revision,started;final Vec3 anchor;
        final List<Lock> locks=new ArrayList<>();final Set<ServerPlayer> audience=new LinkedHashSet<>();
        long lastTick=-1,lastPulse=-1,sequence;int phase=PENDING,pulses,paidCooldown;
        boolean processing,awarded,paid;
        Vec3 origin;
        Context(ServerPlayer p,HighPowerInput.Input input,List<LivingEntity> targets){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));this.input=input;
            preset=identity.state().presets.current();revision=identity.state().presets.revision();started=now(p);
            anchor=targets.getFirst().getBoundingBox().getCenter();origin=p.position();targets.forEach(t->locks.add(new Lock(t)));
        }
    }
    private ThunderNet(){}
    /** Clients submit only one authenticated key identity; all selection and duration are server-owned. */
    public static boolean begin(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark old=INPUTS.get(p.getUUID());if(old!=null&&old.epoch==input.epoch()&&input.input()<=old.input)return false;
        INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        Context previous=CONTEXTS.get(p.getUUID());
        if(previous!=null){if(!valid(previous))finish(previous,INVALIDATED,false);else return false;}
        if(!canAct(p)||!AbilityStorage.get(p).canUse(ID)||!recastReady(p)||nextToken==Long.MAX_VALUE
                ||!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())))return false;
        List<LivingEntity> selected=select(p);
        if(selected.isEmpty()){feedback(p,"no_targets");return false;}
        Context c=new Context(p,input,selected);CONTEXTS.put(p.getUUID(),c);send(c,NORMAL,false);return true;
    }
    public static boolean release(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context c=owned(p);if(c==null||!c.input.equals(input)||c.processing)return false;
        if(!valid(c)){finish(c,INVALIDATED,false);return false;}
        if(ThunderNetRules.held(c.started,now(p))){
            // A delayed release can never become a tap, or synthesize a missed hold pulse.
            finish(c,NORMAL,false);return true;
        }
        c.processing=true;
        try{prune(c);if(c.locks.isEmpty()){finish(c,REFUSAL,false);return false;}
            c.phase=TAP;boolean result=pulse(c,false);if(CONTEXTS.get(p.getUUID())==c)finish(c,result?NORMAL:REFUSAL,true);return result;
        }finally{c.processing=false;}
    }
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark old=INPUTS.get(p.getUUID());if(old==null||old.epoch!=input.epoch()||input.input()>old.input)INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        Context c=owned(p);if(c==null||input.epoch()!=c.input.epoch()||input.slot()!=c.input.slot()||input.input()<c.input.input())return false;
        finish(c,NORMAL,false);return true;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c,NORMAL,false);}
    public static void remove(ServerPlayer p){if(!thread(p))return;abort(p);INPUTS.remove(p.getUUID());}
    public static void clear(){for(Context c:new ArrayList<>(CONTEXTS.values()))finish(c,INVALIDATED,false);CONTEXTS.clear();INPUTS.clear();}
    public static boolean active(ServerPlayer p){return owned(p)!=null;}
    public static int lockedCount(ServerPlayer p){Context c=owned(p);return c==null?0:c.locks.size();}
    public static int pulseCount(ServerPlayer p){Context c=owned(p);return c==null?0:c.pulses;}
    public static List<Node> serverNodes(ServerPlayer p){Context c=owned(p);return c==null?List.of():c.locks.stream().map(Lock::node).toList();}
    public static Vec3 anchor(ServerPlayer p){Context c=owned(p);return c==null?null:c.anchor;}
    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.processing)return;long tick=now(p);
        if(!valid(c)){finish(c,INVALIDATED,false);return;}
        if(ThunderNetRules.expired(c.started,tick)){finish(c,EXPIRED,false);return;}
        // Invalidation wins even a duplicate native tick. Never re-add a retired lock.
        prune(c);if(c.locks.isEmpty()){finish(c,EXPIRED,false);return;}
        if(tick<=c.lastTick)return;c.lastTick=tick;
        for(Lock lock:c.locks)lock.status=LOCKED;
        if(ThunderNetRules.held(c.started,tick)){
            if(c.phase==PENDING){
                if(c.locks.stream().anyMatch(l->l.target instanceof Player)){for(Lock l:c.locks)l.status=REFUSED;feedback(p,"players_tap_only");finish(c,REFUSAL,true);return;}
                c.phase=HOLDING;
            }
            // Renew only existing claims, after all lock conditions have just been checked.
            if(c.paid)for(Lock lock:new ArrayList<>(c.locks))if(!ThunderNetRestraint.hold(c,lock.target)){ThunderNetRestraint.release(c,lock.target);c.locks.remove(lock);}
            if(c.locks.isEmpty()){finish(c,EXPIRED,false);return;}
            if(ThunderNetRules.pulseReady(c.lastPulse,tick)){
                c.processing=true;boolean emitted;try{emitted=pulse(c,true);}finally{c.processing=false;}
                if(emitted)return;
                if(CONTEXTS.get(p.getUUID())!=c)return;
            }
        }
        if(tick%2==0&&CONTEXTS.get(p.getUUID())==c)send(c,NORMAL,false);
    }
    private static boolean pulse(Context c,boolean hold){
        ServerPlayer p=c.identity.owner();long tick=now(p);prune(c);
        List<Lock> targets=c.locks.stream().filter(l->l.target.invulnerableTime<=10).toList();
        if(targets.isEmpty())return false;
        if(hold&&targets.stream().anyMatch(l->l.target instanceof Player)){finish(c,REFUSAL,true);return false;}
        var growth=HighPowerGrowth.values(p);BigDecimal damage=ThunderNetRules.damage(growth.damage(),hold);if(damage==null)return false;
        int cooldown=ThunderNetRules.cooldownTicks(growth.position());
        double cost=hold?ThunderNetRules.pulseCost(growth.position()):ThunderNetRules.tapCost(growth.position());
        List<ThunderNetRestraint.Claim> claims=new ArrayList<>();
        if(hold){
            for(Lock lock:targets){var claim=ThunderNetRestraint.prepare(c,c.identity,lock.target,tick,()->valid(c)&&!ThunderNetRules.expired(c.started,now(p))&&c.locks.contains(lock)&&validLock(c,lock));
                if(claim==null){ThunderNetRestraint.release(c,lock.target);c.locks.remove(lock);}else claims.add(claim);}
            targets=targets.stream().filter(c.locks::contains).toList();if(targets.isEmpty())return false;
        }
        // Reserve the attempt before payment/native callbacks. A refused native hit cannot be replayed this tick.
        c.lastPulse=tick;
        boolean paid=hold?ThunderNetRestraint.commitPaid(claims,()->valid(c)&&ElectromasterPrototype.trySpend(p,ID,cost,1))
                :valid(c)&&ElectromasterPrototype.trySpend(p,ID,cost,cooldown);
        if(!paid){feedback(p,"cannot_pay");finish(c,REFUSAL,false);return false;}
        c.paid=true;c.paidCooldown=cooldown;c.pulses++;
        // Separate persistent re-entry fence survives internal one-tick continuation debits and saves.
        stampRecast(p,cooldown);
        HighPowerNaturalRecovery.offensiveAction(p);
        for(Lock lock:targets){
            if(CONTEXTS.get(p.getUUID())!=c||!valid(c)){finish(c,INVALIDATED,false);return true;}
            if(!validLock(c,lock)){ThunderNetRestraint.release(c,lock.target);c.locks.remove(lock);continue;}
            c.origin=p.position();lock.point=lock.target.getBoundingBox().getCenter();
            BigDecimal before=health(lock.target),shield=HighPowerCombat.shield(lock.target);float absorption=lock.target.getAbsorptionAmount();
            Vec3 hitOrigin=c.origin,hitPoint=lock.point;
            boolean accepted=HighPowerCombat.attackThunderNet(p,lock.target,damage,()->CONTEXTS.get(p.getUUID())==c&&valid(c)
                    &&p.position().equals(hitOrigin)&&lock.target.getBoundingBox().getCenter().equals(hitPoint)&&validLock(c,lock));
            lock.status=accepted?HIT:REFUSED;
            boolean productive=accepted&&(health(lock.target).compareTo(before)<0||HighPowerCombat.shield(lock.target).compareTo(shield)<0||lock.target.getAbsorptionAmount()<absorption);
            // A callback can abort, kill, swap state or change a remaining lock. Nothing is rolled back.
            if(CONTEXTS.get(p.getUUID())!=c||!valid(c)||!p.position().equals(c.origin)){finish(c,INVALIDATED,false);return true;}
            if(productive&&!c.awarded){c.awarded=true;c.identity.state().addExperience(ID,.005);AbilityStorage.save(p);}
            if(CONTEXTS.get(p.getUUID())!=c||!valid(c)){finish(c,INVALIDATED,false);return true;}
            if(hold)prune(c);
        }
        if(hold){send(c,NORMAL,false);if(c.locks.isEmpty())finish(c,EXPIRED,false);}
        return true;
    }
    private static BigDecimal health(LivingEntity e){if(HighPowerCombat.enabled(e))try{return new BigDecimal(HighPowerCombat.healthText(e));}catch(NumberFormatException ignored){}return BigDecimal.valueOf(e.getHealth());}
    private static List<LivingEntity> select(ServerPlayer p){
        List<LivingEntity> scanned=new ArrayList<>(ThunderNetRules.SCAN_LIMIT);
        // Live-index query does not load chunks; every admitted target and actual line is checked below.
        p.serverLevel().getEntities(EntityTypeTest.forClass(LivingEntity.class),p.getBoundingBox().inflate(ThunderNetRules.RANGE),e->cheapEligible(p,e),scanned,ThunderNetRules.SCAN_LIMIT);
        scanned.removeIf(e->!eligible(p,e)||!conductive(p,e)||!clearLine(p,p.position(),e.getBoundingBox().getCenter())
                ||!ThunderNetRules.inside(point(e.getBoundingBox().getCenter()),point(p.position()),ThunderNetRules.RANGE));
        scanned.sort(Comparator.<LivingEntity>comparingDouble(e->e.getBoundingBox().getCenter().distanceToSqr(p.getEyePosition())).thenComparingInt(LivingEntity::getId));
        LivingEntity primary=scanned.stream().filter(e->ThunderNetRules.aimed(point(p.getEyePosition()),point(p.getLookAngle()),point(e.getBoundingBox().getCenter()))).findFirst().orElse(null);
        if(primary==null)return List.of();Vec3 anchor=primary.getBoundingBox().getCenter();
        List<LivingEntity> result=new ArrayList<>();result.add(primary);
        scanned.sort(Comparator.<LivingEntity>comparingDouble(e->e.getBoundingBox().getCenter().distanceToSqr(anchor)).thenComparingInt(LivingEntity::getId));
        for(LivingEntity e:scanned)if(e!=primary&&result.size()<ThunderNetRules.MAX_TARGETS&&ThunderNetRules.inside(point(e.getBoundingBox().getCenter()),point(anchor),ThunderNetRules.ANCHOR_RADIUS))result.add(e);
        return result;
    }
    private static boolean cheapEligible(ServerPlayer p,LivingEntity e){
        return e!=null&&e!=p&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()&&e.level()==p.serverLevel()
                &&HighPowerCombat.supported(e)&&(e instanceof Player||HighPowerCombat.hostileTarget(e)||e instanceof Mob m&&m.getTarget()==p);
    }
    private static boolean eligible(ServerPlayer p,LivingEntity e){
        if(!cheapEligible(p,e)||p.serverLevel().getEntity(e.getId())!=e||p.serverLevel().getEntity(e.getUUID())!=e
                ||HighPowerCombat.unsupportedReason(e).isPresent()||e.isInvulnerable()||p.isAlliedTo(e)||e.isAlliedTo(p)
                ||!permittedVolume(p,e.getBoundingBox())||!finite(e.position()))return false;
        if(e instanceof Player)return e.getClass()==ServerPlayer.class&&e instanceof ServerPlayer other&&other.connection!=null&&!other.hasDisconnected()
                &&!other.getAbilities().invulnerable&&AcademyConfig.ATTACK_PLAYERS.get()&&p.server.isPvpAllowed()&&p.canHarmPlayer(other)&&other.canHarmPlayer(p);
        return true;
    }
    private static boolean conductive(ServerPlayer p,LivingEntity e){return e.isInWaterOrRain()||ConductiveMarkReceipts.has(p,e);}
    private static boolean validLock(Context c,Lock lock){ServerPlayer p=c.identity.owner();LivingEntity e=lock.target;return e.getId()==lock.id&&e.getUUID().equals(lock.uuid)
            &&eligible(p,e)&&conductive(p,e)&&ThunderNetRules.inside(point(e.getBoundingBox().getCenter()),point(c.anchor),ThunderNetRules.ANCHOR_RADIUS)
            &&ThunderNetRules.inside(point(e.getBoundingBox().getCenter()),point(p.position()),ThunderNetRules.RANGE)
            &&clearLine(p,p.position(),e.getBoundingBox().getCenter());}
    private static void prune(Context c){for(var it=c.locks.iterator();it.hasNext();){Lock lock=it.next();if(!validLock(c,lock)){ThunderNetRestraint.release(c,lock.target);it.remove();}else lock.point=lock.target.getBoundingBox().getCenter();}}
    private static boolean canAct(ServerPlayer p){
        if(!thread(p))return false;var s=AbilityStorage.get(p);
        return ElectromasterPrototype.ready(p)&&s.activated&&s.overloadFine&&s.learned(ID)&&s.category.equals("electromaster")
                &&ElectromasterPrototype.effectiveLevel(p)>=4&&SkillCatalog.enabled("electromaster",ID)
                &&HighPowerCombat.unsupportedReason(p).isEmpty()&&!AbilityConsumption.busy(p)&&!p.getAbilities().instabuild
                &&finite(p.position())&&finite(p.getLookAngle())&&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())&&ThunderNetRules.clock(now(p));
    }
    private static boolean valid(Context c){ServerPlayer p=c.identity.owner();return c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canAct(p)
            &&c.identity.state().presets.current()==c.preset&&c.identity.state().presets.revision()==c.revision
            &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))&&now(p)>=c.started;}
    private static boolean permitted(ServerPlayer p,BlockPos pos){ServerLevel w=p.serverLevel();return !w.isOutsideBuildHeight(pos)&&w.hasChunkAt(pos)&&w.getWorldBorder().isWithinBounds(pos)&&w.mayInteract(p,pos);}
    private static boolean permittedVolume(ServerPlayer p,AABB box){
        if(!finite(new Vec3(box.minX,box.minY,box.minZ))||!finite(new Vec3(box.maxX,box.maxY,box.maxZ))||box.getXsize()>4||box.getYsize()>4||box.getZsize()>4)return false;
        for(int x=(int)Math.floor(box.minX);x<=Math.floor(box.maxX);x++)for(int y=(int)Math.floor(box.minY);y<=Math.floor(box.maxY);y++)for(int z=(int)Math.floor(box.minZ);z<=Math.floor(box.maxZ);z++)if(!permitted(p,new BlockPos(x,y,z)))return false;
        return true;
    }
    private static boolean clearLine(ServerPlayer p,Vec3 from,Vec3 to){
        if(!finite(from)||!finite(to)||from.distanceToSqr(to)>ThunderNetRules.RANGE*ThunderNetRules.RANGE)return false;
        // Conservative chunk rectangle includes even arbitrarily short diagonal corner crossings.
        // No sampling approximation can accidentally request an unloaded chunk during the native clip.
        for(int x=((int)Math.floor(Math.min(from.x,to.x)))>>4;x<=((int)Math.floor(Math.max(from.x,to.x)))>>4;x++)
            for(int z=((int)Math.floor(Math.min(from.z,to.z)))>>4;z<=((int)Math.floor(Math.max(from.z,to.z)))>>4;z++)if(!p.serverLevel().hasChunk(x,z))return false;
        // Bounded permission samples supplement endpoint volumes; collider clip remains authoritative.
        int count=Math.max(1,(int)Math.ceil(from.distanceTo(to)*2));for(int i=0;i<=count;i++)if(!permitted(p,BlockPos.containing(from.lerp(to,(double)i/count))))return false;
        return p.serverLevel().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.server!=null&&p.server.isSameThread();}
    private static long now(ServerPlayer p){return p.server.overworld().getGameTime();}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static ThunderNetRules.Point point(Vec3 v){return new ThunderNetRules.Point(v.x,v.y,v.z);}
    private static void feedback(ServerPlayer p,String key){p.displayClientMessage(Component.translatable("academy.highpower.thunder_net."+key),false);}
    private static boolean recastReady(ServerPlayer p){var tag=p.getPersistentData().getCompound("academy:electromaster_prototype");return !tag.contains(RECAST)||tag.contains(RECAST,net.minecraft.nbt.Tag.TAG_LONG)&&now(p)>=tag.getLong(RECAST);}
    private static void stampRecast(ServerPlayer p,int cooldown){var tag=p.getPersistentData().getCompound("academy:electromaster_prototype");tag.putLong(RECAST,Math.max(tag.getLong(RECAST),now(p)+cooldown));p.getPersistentData().put("academy:electromaster_prototype",tag);}
    private static void finish(Context c,int reason,boolean preserve){
        if(!CONTEXTS.remove(c.identity.owner().getUUID(),c))return;
        ThunderNetRestraint.releaseController(c);
        if(c.paid&&c.phase==HOLDING&&thread(c.identity.owner())){
            // This is the captured player object's own persistent fence, even if its ability-state/epoch was retired.
            // Never write a replacement player's budget or state. Lifecycle handlers run before normal state-save hooks.
            stampRecast(c.identity.owner(),c.paidCooldown);
        }
        if(c.paid&&c.phase==HOLDING&&c.identity.owns(c.identity.owner())){
            ServerPlayer p=c.identity.owner();long tick=p.serverLevel().getGameTime();var tag=p.getPersistentData().getCompound("academy:electromaster_prototype");
            tag.putLong("next_"+ID,Math.max(tag.getLong("next_"+ID),tick+c.paidCooldown));p.getPersistentData().put("academy:electromaster_prototype",tag);
            c.identity.state().setCooldown(ID,c.paidCooldown);stampRecast(p,c.paidCooldown);
        }
        c.phase=ENDED;if(!preserve)c.locks.clear();else c.locks.removeIf(l->l.status==LOCKED);send(c,reason,preserve);c.locks.clear();c.audience.clear();
    }
    private static void send(Context c,int reason,boolean preserve){
        ServerPlayer owner=c.identity.owner();if(!preserve)c.origin=owner.position();
        for(ServerPlayer viewer:c.identity.level().players())if(viewer.position().distanceToSqr(c.origin)<=96*96||viewer.position().distanceToSqr(c.anchor)<=48*48)c.audience.add(viewer);c.audience.add(owner);
        List<Node> nodes=c.locks.stream().map(Lock::node).toList();int links=0,bit=0;
        for(int i=0;i<nodes.size();i++)for(int j=i+1;j<nodes.size();j++,bit++)if(clearLine(owner,nodes.get(i).point,nodes.get(j).point))links|=1<<bit;
        Visual packet=new Visual(owner.getId(),owner.getUUID(),c.input.input(),c.identity.token(),c.identity.epoch(),++c.sequence,c.phase,(int)Math.clamp(now(owner)-c.started,0,68),c.origin,c.anchor,nodes,links,reason);
        for(ServerPlayer viewer:c.audience)if(viewer.connection!=null&&!viewer.hasDisconnected()&&viewer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(viewer,packet);
    }
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    private static List<Node> readNodes(RegistryFriendlyByteBuf b){int count=b.readVarInt();if(count<0||count>ThunderNetRules.MAX_TARGETS)throw new DecoderException("NEW141 node bound");List<Node> nodes=new ArrayList<>(count);for(int i=0;i<count;i++)nodes.add(new Node(b.readInt(),b.readUUID(),readVec(b),b.readUnsignedByte()));return nodes;}
    public record Visual(int entity,UUID owner,long input,long token,long epoch,long sequence,int phase,int age,Vec3 origin,Vec3 anchor,List<Node> nodes,int links,int reason) implements CustomPacketPayload {
        public Visual{nodes=List.copyOf(nodes);if(nodes.size()>ThunderNetRules.MAX_TARGETS)throw new IllegalArgumentException("NEW141 node bound");}
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_thunder_net"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{b.writeInt(v.entity);b.writeUUID(v.owner);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.epoch);b.writeLong(v.sequence);b.writeByte(v.phase);b.writeByte(v.age);writeVec(b,v.origin);writeVec(b,v.anchor);b.writeVarInt(v.nodes.size());for(Node n:v.nodes){b.writeInt(n.entity);b.writeUUID(n.uuid);writeVec(b,n.point);b.writeByte(n.status);}b.writeShort(v.links);b.writeByte(v.reason);},b->new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readUnsignedByte(),b.readUnsignedByte(),readVec(b),readVec(b),readNodes(b),b.readUnsignedShort(),b.readUnsignedByte()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network{@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.owner().server==e.getServer())tick(c.identity.owner());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e){
        if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer replacement){
            remove(old);var source=old.getPersistentData().getCompound("academy:electromaster_prototype");
            if(source.contains(RECAST,net.minecraft.nbt.Tag.TAG_LONG)&&source.getLong(RECAST)>now(replacement)){
                var target=replacement.getPersistentData().getCompound("academy:electromaster_prototype");
                target.putLong(RECAST,Math.max(source.getLong(RECAST),target.getLong(RECAST)));
                replacement.getPersistentData().put("academy:electromaster_prototype",target);
            }
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.owner().server==e.getServer())finish(c,INVALIDATED,false);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
}
