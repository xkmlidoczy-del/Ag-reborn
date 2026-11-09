package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.DualPointRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW211: authenticated frozen endpoints and two real hand origins; no ray pierces a blocker. */
@EventBusSubscriber(modid="academy")
public final class DualPoint {
    public static final String ID="dual_point";
    private static final String MARK_CLOCK="academy:dual_point_mark_at";
    public static final int MARK=0,FIRE=1,ENDED=2,CLEAR_MARK=3;
    public static final int PREPARED=0,MISS=1,BLOCKED=2,REFUSED=3,DAMAGED=4,ABSORBED=5;
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input) {}
    private record Endpoint(UUID uuid,int entity,Vec3 point) {}
    private record Mark(Endpoint endpoint,long at) {}
    private record Trace(Vec3 end,Entity target,int status,boolean permitted) {}
    public record Receipt(int slot,Vec3 origin,Vec3 end,int status,UUID victim,long input) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity; final int preset; final long revision;
        final Set<ServerPlayer> audience=new LinkedHashSet<>();
        final List<Receipt> receipts=new ArrayList<>(2);
        HighPowerInput.Input input; Mark mark; long lastMark=-1,lastTick=-1,lastVisual=-1,sequence,committed=-1;
        Endpoint[] slots; BigDecimal damage; boolean awarded,resolving; int next; EmissionPermit emitting;
        Context(ServerPlayer p,HighPowerInput.Input input){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));
            this.input=input;preset=identity.state().presets.current();revision=identity.state().presets.revision();
        }
        boolean firing(){return slots!=null&&next<slots.length;}
    }
    /** Unforgeable single-emission authority; the bridge may never choose a skill, source or amount. */
    public static final class EmissionPermit {
        private final Context context; private final int slot; private final Endpoint endpoint;
        private final LivingEntity target; private final Vec3 origin; private boolean consumed;
        private EmissionPermit(Context context,int slot,Endpoint endpoint,LivingEntity target,Vec3 origin){
            this.context=context;this.slot=slot;this.endpoint=endpoint;this.target=target;this.origin=origin;
        }
        ServerPlayer player(){return context.identity.owner();}
        LivingEntity target(){return target;}
        Vec3 origin(){return origin;}
        Vec3 endpoint(){return endpoint.point;}
        BigDecimal damage(){return context.damage;}
        boolean consume(){if(consumed||!valid())return false;consumed=true;return true;}
        boolean valid(){
            if(context.emitting!=this||CONTEXTS.get(player().getUUID())!=context||!DualPoint.valid(context)
                    ||!origin.equals(hand(player(),slot))||!harmPermitted(player(),target)||!matches(endpoint,target)
                    ||!emissionGeometry(player(),origin,endpoint.point))return false;
            Trace trace=trace(player(),origin,endpoint.point);
            return trace.permitted&&trace.target==target;
        }
    }
    private DualPoint() {}
    private static ResourceLocation id(){return ResourceLocation.fromNamespaceAndPath("academy","dual_point_visual");}

    public static boolean mark(ServerPlayer p,String wire){
        Context resolving=owned(p);if(resolving!=null&&resolving.resolving)return false;
        HighPowerInput.Input input=claim(p,wire);if(input==null||!p.isShiftKeyDown()||!canAct(p))return false;
        var data=p.getPersistentData();
        // The tiny cadence tombstone survives abort, rebinding and a fresh owner epoch.
        if(data.contains(MARK_CLOCK)&&(!data.contains(MARK_CLOCK,Tag.TAG_LONG)||!DualPointRules.markReady(data.getLong(MARK_CLOCK),now(p))))return false;
        Context c=context(p,input);if(c==null||c.firing()||!DualPointRules.markReady(c.lastMark,now(p)))return false;
        Endpoint endpoint=aim(p);Vec3 origin=hand(p,1);
        if(endpoint==null||!eligible(p,endpoint,origin)){retireEmpty(c);return false;}
        c.input=input;c.mark=new Mark(endpoint,now(p));c.lastMark=now(p);data.putLong(MARK_CLOCK,now(p));markVisual(c);return true;
    }
    public static boolean tap(ServerPlayer p,String wire){
        Context resolving=owned(p);if(resolving!=null&&resolving.resolving)return false;
        HighPowerInput.Input input=claim(p,wire);if(input==null||p.isShiftKeyDown()||!canAct(p)||!AbilityStorage.get(p).canUse(ID))return false;
        Context c=context(p,input);if(c==null||c.firing())return false;
        expireMark(c);Endpoint primary=aim(p),secondary=c.mark==null?null:c.mark.endpoint;
        // Invalid preparation never buys a speculative future target. Only admitted slots are paid.
        if(primary!=null&&!eligible(p,primary,hand(p,0)))primary=null;
        if(secondary!=null&&!eligible(p,secondary,hand(p,1)))secondary=null;
        int count=(primary==null?0:1)+(secondary==null?0:1);
        if(count==0){retireEmpty(c);return false;}
        var growth=HighPowerGrowth.values(p);BigDecimal damage=DualPointRules.damage(growth.damage());
        if(!HighPowerResources.trySpend(p,ID,DualPointRules.cost(growth.position(),count),DualPointRules.cooldown(growth.arcCooldown()))){retireEmpty(c);return false;}
        c.input=input;c.slots=new Endpoint[]{primary,secondary};c.next=0;c.damage=damage;c.awarded=false;c.receipts.clear();c.committed=now(p);c.lastTick=c.committed-1;
        HighPowerNaturalRecovery.offensiveAction(p);tick(p);return true;
    }
    public static boolean abort(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark old=INPUTS.get(p.getUUID());if(old==null||old.epoch!=input.epoch()||input.input()>old.input)INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        Context c=owned(p);if(c==null||c.input.epoch()!=input.epoch()||c.input.slot()!=input.slot()||input.input()<c.input.input())return false;
        finish(c);return true;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c);}
    public static void remove(ServerPlayer p){if(!thread(p))return;abort(p);INPUTS.remove(p.getUUID());}
    public static void clear(){CONTEXTS.clear();INPUTS.clear();}
    public static boolean active(ServerPlayer p){return owned(p)!=null;}
    public static boolean marked(ServerPlayer p){Context c=owned(p);return c!=null&&c.mark!=null&&!DualPointRules.expired(c.mark.at,now(p));}
    public static Vec3 markedEndpoint(ServerPlayer p){Context c=owned(p);return c==null||c.mark==null?null:c.mark.endpoint.point;}
    public static UUID markedTarget(ServerPlayer p){Context c=owned(p);return c==null||c.mark==null?null:c.mark.endpoint.uuid;}
    public static List<Receipt> receipts(ServerPlayer p){Context c=owned(p);return c==null?List.of():List.copyOf(c.receipts);}

    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null)return;long tick=now(p);
        if(!valid(c)||!DualPointRules.clock(tick)||tick<c.lastTick){finish(c);return;}
        expireMark(c);
        if(tick<=c.lastTick)return;
        c.lastTick=tick;
        if(c.firing()){
            // A missed emission tick loses its prepaid slot; it never catches up or reacquires.
            int slot=c.next;if(tick-c.committed>=slot){
                c.next++;
                if(tick-c.committed==slot&&c.slots[slot]!=null){
                    // The final slot has already advanced next. It remains owned through native and XP callbacks.
                    c.resolving=true;try{emit(c,slot,c.slots[slot]);}finally{c.resolving=false;}
                }
                if(CONTEXTS.get(p.getUUID())!=c||!valid(c)){finish(c);return;}
            }
        }
        if(c.mark!=null&&(c.lastVisual<0||tick-c.lastVisual>=5))markVisual(c);
        if(!c.firing()&&c.mark==null&&c.committed>=0&&tick-c.committed>=2)finish(c);
        else retireEmpty(c);
    }
    private static void emit(Context c,int slot,Endpoint endpoint){
        ServerPlayer p=c.identity.owner();Vec3 origin=hand(p,slot);
        if(!emissionGeometry(p,origin,endpoint.point)){receipt(c,slot,origin,origin,REFUSED,null);return;}
        Trace trace=trace(p,origin,endpoint.point);int status=trace.status;UUID victim=null;
        if(trace.permitted&&trace.target instanceof LivingEntity target){
            status=REFUSED;
            if(matches(endpoint,target)&&harmPermitted(p,target)){
                EmissionPermit permit=new EmissionPermit(c,slot,endpoint,target,origin);c.emitting=permit;
                BigDecimal hp=health(target),shield=HighPowerCombat.shield(target);float absorption=target.getAbsorptionAmount();
                boolean accepted;
                try{accepted=HighPowerCombat.attackDualPoint(permit);}finally{c.emitting=null;}
                if(accepted&&health(target).compareTo(hp)<0){status=DAMAGED;victim=target.getUUID();}
                else if(accepted&&(HighPowerCombat.shield(target).compareTo(shield)<0||target.getAbsorptionAmount()<absorption)){status=ABSORBED;victim=target.getUUID();}
                if(!c.awarded&&victim!=null&&valid(c)){
                    c.awarded=true;c.identity.state().addExperience(ID,.005);AbilityStorage.save(p);
                }
            }
        }
        receipt(c,slot,origin,trace.end,status,victim);
    }
    private static void receipt(Context c,int slot,Vec3 origin,Vec3 end,int status,UUID victim){
        c.receipts.add(new Receipt(slot,origin,end,status,victim,c.input.input()));send(c,FIRE,slot,origin,end,status,0);
    }
    private static HighPowerInput.Input claim(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return null;
        Watermark old=INPUTS.get(p.getUUID());if(old!=null&&old.epoch==input.epoch()&&input.input()<=old.input)return null;
        INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        return ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))?input:null;
    }
    private static Context context(ServerPlayer p,HighPowerInput.Input input){
        Context c=owned(p);if(c!=null&&!valid(c)){finish(c);c=null;}
        if(c==null){if(nextToken==Long.MAX_VALUE||!DualPointRules.clock(now(p)))return null;c=new Context(p,input);CONTEXTS.put(p.getUUID(),c);}
        return c.input.epoch()==input.epoch()&&c.input.slot()==input.slot()?c:null;
    }
    private static Endpoint aim(ServerPlayer p){
        Vec3 eye=p.getEyePosition();DualPointRules.Point bounded=DualPointRules.endpoint(point(eye),point(p.getLookAngle().normalize()));
        if(bounded==null)return null;Vec3 end=new Vec3(bounded.x(),bounded.y(),bounded.z());
        Trace trace=trace(p,eye,end);
        if(!trace.permitted||!(trace.target instanceof LivingEntity target)||!harmPermitted(p,target))return null;
        // The coordinate and entity identity are both immutable; future aim never rewrites this slot.
        Vec3 point=target.getBoundingBox().getCenter();
        return inRange(p,eye,point)?new Endpoint(target.getUUID(),target.getId(),point):null;
    }
    private static boolean eligible(ServerPlayer p,Endpoint endpoint,Vec3 origin){
        if(!emissionGeometry(p,origin,endpoint.point))return false;
        Trace trace=trace(p,origin,endpoint.point);
        return trace.permitted&&trace.target instanceof LivingEntity living&&matches(endpoint,living)&&harmPermitted(p,living);
    }
    private static boolean matches(Endpoint endpoint,LivingEntity target){return endpoint.uuid.equals(target.getUUID())&&endpoint.entity==target.getId();}
    private static boolean emissionGeometry(ServerPlayer p,Vec3 origin,Vec3 endpoint){
        return inRange(p,origin,endpoint)&&clear(p,p.getEyePosition(),origin)&&permittedLine(p,origin,endpoint);
    }
    private static boolean inRange(ServerPlayer p,Vec3 origin,Vec3 endpoint){
        return finite(origin)&&finite(endpoint)&&origin.distanceToSqr(endpoint)<=DualPointRules.RANGE*DualPointRules.RANGE
                &&p.position().distanceToSqr(endpoint)<=DualPointRules.RANGE*DualPointRules.RANGE
                &&p.getEyePosition().distanceToSqr(endpoint)<=DualPointRules.RANGE*DualPointRules.RANGE;
    }
    /** Includes allied, unsupported and nonliving native physical blockers before combat admission. */
    private static Trace trace(ServerPlayer p,Vec3 origin,Vec3 endpoint){
        if(!permittedLine(p,origin,endpoint))return new Trace(origin,null,REFUSED,false);
        AABB swept=new AABB(origin,endpoint).inflate(DualPointRules.RADIUS);
        double nearest=origin.distanceToSqr(endpoint);Vec3 end=endpoint;Entity target=null;boolean solid=false;
        // Narrow quarter-block slices bound solid-shape work even for a diagonal 28 m ray.
        int pieces=Math.max(1,(int)Math.ceil(origin.distanceTo(endpoint)/.25));Vec3 previous=origin;
        for(int i=1;i<=pieces;i++){
            Vec3 next=origin.lerp(endpoint,(double)i/pieces);
            for(var shape:p.serverLevel().getBlockCollisions(p,new AABB(previous,next).inflate(DualPointRules.RADIUS)))for(AABB box:shape.toAabbs()){
                AABB bound=box.inflate(DualPointRules.RADIUS);Optional<Vec3> hit=bound.contains(origin)?Optional.of(origin):bound.clip(origin,endpoint);
                if(hit.isPresent()&&origin.distanceToSqr(hit.get())<=nearest){nearest=origin.distanceToSqr(hit.get());end=hit.get();solid=true;}
            }
            if(solid)break;previous=next;
        }
        List<Entity> candidates=new ArrayList<>();
        p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class),swept,e->e!=p&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()&&e.canBeHitByProjectile(),candidates,DualPointRules.SCAN_LIMIT+1);
        if(candidates.size()>DualPointRules.SCAN_LIMIT)return new Trace(origin,null,REFUSED,false);
        for(Entity e:candidates){
            AABB bounds=e.getBoundingBox().inflate(DualPointRules.RADIUS);Optional<Vec3> hit=bounds.contains(origin)?Optional.of(origin):bounds.clip(origin,endpoint);
            if(hit.isPresent()&&(origin.distanceToSqr(hit.get())<nearest||!solid&&target==null&&origin.distanceToSqr(hit.get())==nearest)){
                nearest=origin.distanceToSqr(hit.get());end=hit.get();target=e;solid=false;
            }
        }
        return new Trace(end,target,solid?BLOCKED:target==null?MISS:REFUSED,true);
    }
    private static boolean canAct(ServerPlayer p){
        if(!thread(p))return false;var state=AbilityStorage.get(p);
        return p.connection!=null&&!p.hasDisconnected()&&HighPowerResources.ready(p)&&!p.getAbilities().instabuild&&!p.getAbilities().invulnerable
                &&state.activated&&state.overloadFine&&!state.interfering&&state.learned(ID)&&state.category.equals("meltdowner")
                &&state.level>=1&&state.level<=5&&HighPowerResources.effectiveLevel(p)>=1&&SkillCatalog.enabled("meltdowner",ID)
                &&HighPowerCombat.unsupportedReason(p).isEmpty()&&!AbilityConsumption.busy(p)&&!p.noPhysics&&!p.isPassenger()&&!p.isSleeping()
                &&finite(p.position())&&finite(p.getDeltaMovement())&&finite(p.getLookAngle())&&p.getLookAngle().lengthSqr()>.5
                &&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())&&permittedVolume(p,p.getBoundingBox());
    }
    private static boolean valid(Context c){
        ServerPlayer p=c.identity.owner();return c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canAct(p)
                &&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision()
                &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()));
    }
    private static boolean harmPermitted(ServerPlayer p,LivingEntity target){
        ServerLevel world=p.serverLevel();
        if(target==p||target.level()!=world||!target.isAlive()||target.isRemoved()||target.isSpectator()||world.getEntity(target.getUUID())!=target
                ||world.getEntity(target.getId())!=target||!HighPowerCombat.supported(target)||HighPowerCombat.unsupportedReason(target).isPresent()
                ||target.isInvulnerable()||target.getPersistentData().getBoolean("academy:protected")||p.isAlliedTo(target)||target.isAlliedTo(p)
                ||!permittedVolume(p,target.getBoundingBox()))return false;
        return !(target instanceof Player)||target.getClass()==ServerPlayer.class&&target instanceof ServerPlayer other
                &&other.connection!=null&&!other.hasDisconnected()&&!other.getAbilities().invulnerable
                &&AcademyConfig.ATTACK_PLAYERS.get()&&p.server.isPvpAllowed()&&p.canHarmPlayer(other)&&other.canHarmPlayer(p);
    }
    private static boolean permittedLine(ServerPlayer p,Vec3 start,Vec3 end){
        if(!finite(start)||!finite(end)||start.distanceToSqr(end)>DualPointRules.RANGE*DualPointRules.RANGE+1e-8)return false;
        int pieces=Math.max(1,(int)Math.ceil(start.distanceTo(end)/.25));
        Vec3 previous=start;
        for(int i=1;i<=pieces;i++){
            Vec3 next=start.lerp(end,(double)i/pieces);
            if(!permittedVolume(p,new AABB(previous,next).inflate(DualPointRules.RADIUS)))return false;previous=next;
        }
        return true;
    }
    private static boolean permittedVolume(ServerPlayer p,AABB box){
        ServerLevel world=p.serverLevel();
        if(!finite(new Vec3(box.minX,box.minY,box.minZ))||!finite(new Vec3(box.maxX,box.maxY,box.maxZ))
                ||box.getXsize()>8||box.getYsize()>8||box.getZsize()>8||box.minY<world.getMinBuildHeight()||box.maxY>=world.getMaxBuildHeight()
                ||!world.getWorldBorder().isWithinBounds(box))return false;
        for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++)for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++)if(!world.hasChunk(x,z))return false;
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))if(!world.mayInteract(p,pos))return false;
        return true;
    }
    private static boolean clear(ServerPlayer p,Vec3 start,Vec3 end){
        if(!permittedLine(p,start,end))return false;
        int pieces=Math.max(1,(int)Math.ceil(start.distanceTo(end)/.25));Vec3 previous=start;
        for(int i=1;i<=pieces;i++){
            Vec3 next=start.lerp(end,(double)i/pieces);
            if(p.serverLevel().getBlockCollisions(p,new AABB(previous,next).inflate(DualPointRules.RADIUS)).iterator().hasNext())return false;
            previous=next;
        }
        return p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    public static Vec3 hand(ServerPlayer p,int slot){DualPointRules.Point result=DualPointRules.origin(point(p.getEyePosition()),point(p.getLookAngle()),slot);return result==null?null:new Vec3(result.x(),result.y(),result.z());}
    private static DualPointRules.Point point(Vec3 v){return new DualPointRules.Point(v.x,v.y,v.z);}
    private static BigDecimal health(LivingEntity target){return HighPowerCombat.enabled(target)?new BigDecimal(HighPowerCombat.healthText(target)):BigDecimal.valueOf(target.getHealth());}
    private static void expireMark(Context c){if(c.mark!=null&&DualPointRules.expired(c.mark.at,now(c.identity.owner()))){c.mark=null;send(c,CLEAR_MARK,1,Vec3.ZERO,Vec3.ZERO,PREPARED,0);}}
    private static void markVisual(Context c){
        if(c.mark==null)return;ServerPlayer p=c.identity.owner();Vec3 origin=hand(p,1),end=c.mark.endpoint.point;c.lastVisual=now(p);
        if(inRange(p,origin,end)&&clear(p,p.getEyePosition(),origin)&&clear(p,origin,end))send(c,MARK,1,origin,end,PREPARED,(int)(DualPointRules.MARK_TICKS-(now(p)-c.mark.at)));
        else send(c,CLEAR_MARK,1,Vec3.ZERO,Vec3.ZERO,PREPARED,0);
    }
    private static void retireEmpty(Context c){if(c.mark==null&&c.slots==null)finish(c);}
    private static void finish(Context c){if(CONTEXTS.remove(c.identity.owner().getUUID(),c)){c.emitting=null;send(c,ENDED,0,Vec3.ZERO,Vec3.ZERO,PREPARED,0);}}
    private static void send(Context c,int phase,int slot,Vec3 origin,Vec3 end,int status,int remaining){
        Visual visual=new Visual(c.identity.owner().getId(),c.identity.owner().getUUID(),c.input.epoch(),c.input.input(),c.identity.token(),++c.sequence,phase,slot,origin,end,status,remaining);
        if(phase!=ENDED){c.audience.add(c.identity.owner());for(ServerPlayer observer:c.identity.level().players())if(observer.distanceToSqr(c.identity.owner())<=96*96)c.audience.add(observer);}
        for(ServerPlayer observer:c.audience)if(observer.connection!=null&&!observer.hasDisconnected()&&observer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(observer,visual);
    }
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static long now(ServerPlayer p){return p.serverLevel().getServer().overworld().getGameTime();}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int phase,int slot,Vec3 origin,Vec3 end,int status,int remainingTicks) implements CustomPacketPayload {
        public Visual{
            if(owner<0||ownerUuid==null||epoch<=0||input<=0||token<=0||sequence<=0||phase<MARK||phase>CLEAR_MARK||slot<0||slot>1||!finite(origin)||!finite(end)
                    ||origin.distanceToSqr(end)>DualPointRules.RANGE*DualPointRules.RANGE+1e-8||status<PREPARED||status>ABSORBED||remainingTicks<0||remainingTicks>DualPointRules.MARK_TICKS)
                throw new IllegalArgumentException("Invalid bounded NEW211 visual receipt");
        }
        public static final Type<Visual> TYPE=new Type<>(id());
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.owner);b.writeUUID(v.ownerUuid);b.writeLong(v.epoch);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.sequence);b.writeByte(v.phase);b.writeByte(v.slot);writeVec(b,v.origin);writeVec(b,v.end);b.writeByte(v.status);b.writeByte(v.remainingTicks);
        },b->new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readUnsignedByte(),b.readUnsignedByte(),readVec(b),readVec(b),b.readUnsignedByte(),b.readUnsignedByte()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.level().getServer()==e.getServer())tick(c.identity.owner());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
}
