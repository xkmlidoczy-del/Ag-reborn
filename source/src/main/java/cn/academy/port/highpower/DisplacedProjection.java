package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.DisplacedProjectionRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW311: one real, registered needle. Relocation is never itself a damage hit. */
@EventBusSubscriber(modid="academy")
public final class DisplacedProjection {
    public static final String ID="displaced_projection";
    public static final int PROJECTED=1,IMPACT=2,REFUSED=3,CANCELLED=4;
    private static final String DATA="academy:displaced_projection_fences";
    private static final Map<UUID,Cast> BUSY=new HashMap<>();
    private static final Map<UUID,Flight> FLIGHTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Map<ServerPlayer,Receipt> RECEIPTS=new IdentityHashMap<>();
    private static final Map<ServerPlayer,Long> REPLIES=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Integer> CLOCKS=new WeakHashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private static final boolean DIAGNOSTIC=Boolean.getBoolean("academy.highpower.displacedProjectionDiagnostic");
    private static int diagnosticEvents;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("ac.displaced_projection");
    private record Watermark(ServerPlayer owner,long epoch,long input){}
    private record Fences(int request,int cast){static final Fences ZERO=new Fences(0,0);Fences advance(){return new Fences(Math.max(0,request-1),Math.max(0,cast-1));}}
    public record Receipt(long input,long token,boolean paid,boolean projected,boolean hurt,String reason,UUID medium,Vec3 source,Vec3 arrival){}
    private static final class Cast {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset;final long revision;
        final Vec3 caster;final float yaw,pitch;
        DisplacedProjectionWorld.Plan plan;
        DisplacedNeedleEntity medium;
        Vec3 source;boolean paid,closed,projected;
        long sequence;
        Cast(ServerPlayer p,HighPowerInput.Input i){identity=new LegacySingleKeyServerIdentity(p,i.input(),++nextToken,AbilityStorage.get(p));input=i;preset=identity.state().presets.current();revision=identity.state().presets.revision();caster=p.getEyePosition();yaw=p.getYRot();pitch=p.getXRot();source=caster;}
    }
    private static final class Flight {
        final Cast cast;final DisplacedNeedleEntity medium;final DisplacedNeedleEntity.Identity identity;
        final BigDecimal damage;final double range;Vec3 last;double traveled;int age;boolean closed;
        EmissionPermit claim;
        Flight(Cast c,BigDecimal damage){cast=c;medium=c.medium;identity=medium.identity();this.damage=damage;last=c.plan.side();range=DisplacedProjectionRules.RANGE-c.caster.distanceTo(c.plan.side());}
    }
    /** Only this actual native impact can construct a damage authority. */
    public static final class EmissionPermit {
        private final Flight flight;private final LivingEntity target;private final Vec3 origin,endpoint;
        private final Vec3 casterPosition,targetPosition;private final net.minecraft.world.phys.AABB targetBounds;private final float yaw,pitch;private boolean consumed;
        private EmissionPermit(Flight f,LivingEntity target,Vec3 origin,Vec3 endpoint){flight=f;this.target=target;this.origin=origin;this.endpoint=endpoint;casterPosition=player().position();targetPosition=target.position();targetBounds=target.getBoundingBox();yaw=player().getYRot();pitch=player().getXRot();}
        public ServerPlayer player(){return flight.cast.identity.owner();}
        public LivingEntity target(){return target;}
        public BigDecimal damage(){return flight.damage;}
        public Vec3 origin(){return origin;}
        public Vec3 endpoint(){return endpoint;}
        public DisplacedNeedleEntity needle(){return flight.medium;}
        public boolean consume(){if(consumed)return false;consumed=true;return true;}
        public boolean valid(){
            if(!consumed||flight.claim!=this||!validFlight(flight)||target!=flight.cast.plan.target()
                    ||!player().position().equals(casterPosition)||player().getYRot()!=yaw||player().getXRot()!=pitch
                    ||!target.position().equals(targetPosition)||!target.getBoundingBox().equals(targetBounds)
                    ||!origin.equals(needle().contactSegmentOrigin())||!endpoint.equals(needle().contactPoint())||!needle().position().equals(endpoint))return false;
            if(!DisplacedProjectionWorld.hostile(player(),target)
                    ||flight.traveled+origin.distanceTo(endpoint)>flight.range
                    ||player().getEyePosition().distanceTo(origin)+origin.distanceTo(endpoint)>DisplacedProjectionRules.RANGE)return false;
            return DisplacedProjectionWorld.contactClear(player(),origin,endpoint,target,needle())
                    &&validFlight(flight)&&flight.claim==this&&FLIGHTS.get(player().getUUID())==flight&&!flight.closed
                    &&player().position().equals(casterPosition)&&player().getYRot()==yaw&&player().getXRot()==pitch
                    &&target.position().equals(targetPosition)&&target.getBoundingBox().equals(targetBounds)
                    &&origin.equals(needle().contactSegmentOrigin())&&endpoint.equals(needle().contactPoint())&&needle().position().equals(endpoint);
        }
    }
    private DisplacedProjection(){}
    public static boolean start(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input)||AbilityConsumption.busy(p)||BUSY.containsKey(p.getUUID())||!mapped(p,input.slot()))return false;
        var previous=INPUTS.get(p.getUUID());if(previous!=null&&previous.owner==p&&previous.epoch==input.epoch()&&input.input()<=previous.input)return false;
        INPUTS.put(p.getUUID(),new Watermark(p,input.epoch(),input.input()));
        if(nextToken==Long.MAX_VALUE||BUSY.size()>=64||!DisplacedProjectionWorld.finite(p.getEyePosition()))return false;
        long now=Integer.toUnsignedLong(p.server.getTickCount());var replied=REPLIES.put(p,now);if(replied!=null&&now>=replied&&now-replied<4)return false;
        Cast c=new Cast(p,input);BUSY.put(p.getUUID(),c);
        try{
            Fences fences=readFences(p);if(fences==null)return refuse(c,"corrupt_medium");
            if(fences.request>0)return refuse(c,"cast_fence");writeFences(p,new Fences(4,fences.cast));
            if(!eligible(c)||!c.identity.state().canUse(ID))return refuse(c,"not_ready");
            if(fences.cast>0)return refuse(c,"cast_fence");
            if(DisplacedNeedleRegistry.availability(p)==DisplacedNeedleRegistry.Availability.REFUND_PENDING)
                return refuse(c,DisplacedNeedleRegistry.retryRefund(p)?"medium_returned":"refund_pending");
            var assessment=DisplacedProjectionWorld.acquire(p);if(!assessment.valid())return refuse(c,assessment.reason());c.plan=assessment.plan();
            c.medium=DisplacedNeedleRegistry.existing(p).orElse(null);
            if(c.medium==null){
                var state=DisplacedNeedleRegistry.availability(p);
                if(state!=DisplacedNeedleRegistry.Availability.READY){
                    return refuse(c,state==DisplacedNeedleRegistry.Availability.NO_MEDIUM?"no_medium":state==DisplacedNeedleRegistry.Availability.CORRUPT?"corrupt_medium":state==DisplacedNeedleRegistry.Availability.REGISTRY_FULL?"registry_full":"medium_missing");
                }
            }else{
                if(c.medium.impactBusy())return refuse(c,"medium_busy");c.source=c.medium.position();
                if(!DisplacedProjectionWorld.validate(c.plan,c.medium,true).isEmpty())return refuse(c,"blocked");
                // Invalidate the previous damage controller before any event or new resource commit.
                retire(c.medium);
                EntityTeleportEvent event=new EntityTeleportEvent(c.medium,c.plan.side().x,c.plan.side().y,c.plan.side().z);
                NeoForge.EVENT_BUS.post(event);
                if(event.isCanceled()||event.getTargetX()!=c.plan.side().x||event.getTargetY()!=c.plan.side().y||event.getTargetZ()!=c.plan.side().z)return refuse(c,"transaction_changed");
            }
            if(!castValid(c))return refuse(c,"transaction_changed");
            var growth=HighPowerGrowth.values(p);BigDecimal amount=DisplacedProjectionRules.damage(growth.damage());
            double cost=DisplacedProjectionRules.cpCost(growth.position());int cooldown=DisplacedProjectionRules.cooldownTicks(growth.position());
            java.util.function.BooleanSupplier guard=()->castValid(c);
            java.util.function.BooleanSupplier payment=()->{
                if(c.paid||!castValid(c))return false;
                var current=readFences(p);if(current==null||current.cast>0)return false;
                if(!HighPowerResources.trySpend(p,ID,cost,cooldown))return false;
                c.paid=true;writeFences(p,new Fences(current.request,8));return true;
            };
            boolean accepted;
            if(c.medium==null){c.medium=DisplacedNeedleRegistry.deploy(p,c.plan.side(),guard,payment).orElse(null);accepted=c.medium!=null;}
            else accepted=DisplacedNeedleRegistry.reproject(p,c.medium,c.plan.side(),guard,payment);
            if(!accepted)return refuse(c,c.paid?"transaction_changed":"spawn_rejected");
            c.projected=true;
            c.medium.setDeltaMovement(c.plan.velocity());
            Flight flight=new Flight(c,amount);FLIGHTS.put(p.getUUID(),flight);
            if(!c.medium.beginFlight(flight.identity)){finish(flight,CANCELLED,"transaction_changed",false,null);return false;}
            receipt(c,false,"");emit(c,PROJECTED,c.plan.endpoint(),false,"");diagnostic(c,"projected","");
            AbilityStorage.save(p);AcademyNetwork.sync(p);return true;
        }catch(RuntimeException failure){LOG.warn("NEW311 ends without replay or global rollback; paid={}",c.paid,failure);if(c.medium!=null)retire(c.medium);return refuse(c,"transaction_changed");}
        finally{BUSY.remove(p.getUUID(),c);}
    }
    private static boolean castValid(Cast c){return BUSY.get(c.identity.owner().getUUID())==c&&!c.closed&&eligible(c)
            &&c.identity.owner().getEyePosition().equals(c.caster)&&c.identity.owner().getYRot()==c.yaw&&c.identity.owner().getXRot()==c.pitch
            &&c.plan!=null&&DisplacedProjectionWorld.validate(c.plan,c.medium==null?DisplacedNeedleRegistry.pending(c.identity.owner()):c.medium,true).isEmpty();}
    private static boolean eligible(Cast c){
        var p=c.identity.owner();var s=c.identity.state();
        return c.identity.owns(p)&&mapped(p,c.input.slot())&&s.presets.current()==c.preset&&s.presets.revision()==c.revision
                &&p.isAlive()&&!p.isRemoved()&&!p.hasDisconnected()&&!p.isCreative()&&!p.isSpectator()&&!p.getAbilities().invulnerable
                &&p.containerMenu==p.inventoryMenu&&s.activated&&s.overloadFine&&!s.interfering&&!s.consumptionInProgress()
                &&s.learned(ID)&&s.learned(HighPowerSkills.SAFE_LANDING_PROBE)&&SkillCatalog.enabled("teleporter",ID)
                &&HighPowerResources.ready(p)&&HighPowerCombat.unsupportedReason(p).isEmpty();
    }
    private static boolean mapped(ServerPlayer p,int slot){return p!=null&&p.server.isSameThread()&&slot>=0&&slot<4
            &&HighPowerCategories.isTeleporter(AbilityStorage.get(p).category)&&ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));}
    private static boolean validFlight(Flight f){return f!=null&&!f.closed&&f.age<DisplacedProjectionRules.FLIGHT_TICKS&&FLIGHTS.get(f.cast.identity.owner().getUUID())==f
            &&eligible(f.cast)&&f.medium.identity()!=null&&f.medium.identity().equals(f.identity)&&f.medium.getOwner()==f.cast.identity.owner()
            &&f.medium.level()==f.cast.plan.world()&&!f.medium.isRemoved()&&f.medium.isAdmitted()
            &&DisplacedNeedleRegistry.existing(f.cast.identity.owner()).orElse(null)==f.medium;}
    static boolean canContact(DisplacedNeedleEntity medium,DisplacedNeedleEntity.Identity id,Entity target){
        Flight f=FLIGHTS.get(id.owner());return f!=null&&f.medium==medium&&id.equals(f.identity)&&target==f.cast.plan.target()&&validFlight(f);
    }
    static void contact(DisplacedNeedleEntity medium,DisplacedNeedleEntity.Identity id,EntityHitResult hit){
        Flight f=FLIGHTS.get(id.owner());if(f==null||f.medium!=medium)return;
        boolean hurt=false;
        try{
            Vec3 origin=medium.contactSegmentOrigin();
            if(!canContact(medium,id,hit.getEntity())||origin==null||!origin.equals(f.last)
                    ||!(hit.getEntity() instanceof LivingEntity target)
                    ||!DisplacedNeedleRegistry.consumeContact(medium,id.generation()))return;
            EmissionPermit permit=new EmissionPermit(f,target,origin,hit.getLocation());f.claim=permit;
            hurt=HighPowerCombat.attackDisplacedProjection(permit);
        }finally{f.claim=null;finish(f,IMPACT,"",hurt,hit.getLocation());}
    }
    static void retire(DisplacedNeedleEntity medium){if(medium==null||medium.identity()==null)return;Flight f=FLIGHTS.get(medium.identity().owner());if(f!=null&&f.medium==medium)finish(f,CANCELLED,"session_changed",false,null);DisplacedNeedleRegistry.retireContact(medium);}
    private static void finish(Flight f,int phase,String reason,boolean hurt,Vec3 impact){
        if(f.closed)return;f.closed=true;FLIGHTS.remove(f.identity.owner(),f);f.medium.markHarmless(f.medium.identity());DisplacedNeedleRegistry.retireContact(f.medium);
        receipt(f.cast,hurt,reason);try{emit(f.cast,phase,impact==null?f.medium.position():impact,hurt,reason);}catch(RuntimeException ignored){}
        diagnostic(f.cast,hurt?"hurt":phase==IMPACT?"contact":"retired",reason);
    }
    private static boolean refuse(Cast c,String reason){c.closed=true;receipt(c,false,reason);try{emit(c,REFUSED,c.plan==null?c.caster:c.plan.endpoint(),false,reason);}catch(RuntimeException ignored){}diagnostic(c,"refused",reason);return false;}
    private static void receipt(Cast c,boolean hurt,String reason){ServerPlayer p=c.identity.owner();if(p.isRemoved()||p.hasDisconnected()||p.server.getPlayerList().getPlayer(p.getUUID())!=p)return;RECEIPTS.put(p,new Receipt(c.input.input(),c.identity.token(),c.paid,c.projected,hurt,reason,c.medium==null?null:c.medium.getUUID(),c.source,c.plan==null?c.source:c.plan.side()));}
    public static Receipt receipt(ServerPlayer p){return RECEIPTS.get(p);}
    public static boolean active(ServerPlayer p){return p!=null&&(BUSY.containsKey(p.getUUID())||FLIGHTS.containsKey(p.getUUID()));}
    public static void cancelActive(ServerPlayer p){if(p==null||!p.server.isSameThread())return;var c=BUSY.get(p.getUUID());if(c!=null&&c.identity.owner()==p)c.closed=true;var f=FLIGHTS.get(p.getUUID());if(f!=null&&f.cast.identity.owner()==p)finish(f,CANCELLED,"session_changed",false,null);}
    public static boolean release(ServerPlayer p,String wire){return HighPowerBracerSession.authenticated(p,HighPowerInput.parse(wire));}
    public static boolean abort(ServerPlayer p,String wire){var i=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,i))return false;var f=FLIGHTS.get(p.getUUID());var c=BUSY.get(p.getUUID());var owned=f==null?c:f.cast;if(owned==null||owned.identity.owner()!=p||i.epoch()!=owned.input.epoch()||i.slot()!=owned.input.slot()||i.input()<owned.input.input())return false;INPUTS.put(p.getUUID(),new Watermark(p,i.epoch(),i.input()));cancelActive(p);return true;}
    private static Fences readFences(ServerPlayer p){var root=p.getPersistentData();if(!root.contains(DATA))return Fences.ZERO;if(!root.contains(DATA,Tag.TAG_COMPOUND))return null;var t=root.getCompound(DATA);if(!t.contains("version",Tag.TAG_INT)||t.getInt("version")!=1||!t.contains("request",Tag.TAG_INT)||!t.contains("cast",Tag.TAG_INT))return null;int r=t.getInt("request"),c=t.getInt("cast");return r<0||r>4||c<0||c>8?null:new Fences(r,c);}
    private static void writeFences(ServerPlayer p,Fences f){var t=new CompoundTag();t.putInt("version",1);t.putInt("request",f.request);t.putInt("cast",f.cast);p.getPersistentData().put(DATA,t);}
    @SubscribeEvent public static void pre(ServerTickEvent.Pre e){Integer old=CLOCKS.put(e.getServer(),e.getServer().getTickCount());if(old!=null&&old==e.getServer().getTickCount())return;for(var p:e.getServer().getPlayerList().getPlayers())if(p.getPersistentData().contains(DATA)){var f=readFences(p);if(f!=null&&!f.equals(Fences.ZERO))writeFences(p,f.advance());}for(Flight f:new ArrayList<>(FLIGHTS.values()))if(f.cast.identity.owner().server==e.getServer()){f.age++;if(!validFlight(f))finish(f,CANCELLED,"session_changed",false,null);}}
    @SubscribeEvent public static void post(ServerTickEvent.Post e){for(Flight f:new ArrayList<>(FLIGHTS.values()))if(f.cast.identity.owner().server==e.getServer()){
        Vec3 position=f.medium.position();if(!validFlight(f)||!DisplacedProjectionWorld.finite(position)){finish(f,CANCELLED,"session_changed",false,null);continue;}
        f.traveled+=f.last.distanceTo(position);f.last=position;if(f.traveled>=f.range||f.medium.inGround||!f.medium.isContactEnabled())finish(f,CANCELLED,"out_of_range",false,null);
    }}
    private static void remove(ServerPlayer p){cancelActive(p);INPUTS.computeIfPresent(p.getUUID(),(id,w)->w.owner==p?null:w);RECEIPTS.remove(p);REPLIES.remove(p);}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer p){remove(old);if(old.getPersistentData().contains(DATA))p.getPersistentData().put(DATA,old.getPersistentData().get(DATA).copy());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){FLIGHTS.entrySet().removeIf(x->x.getValue().cast.identity.owner().server==e.getServer());BUSY.entrySet().removeIf(x->x.getValue().identity.owner().server==e.getServer());INPUTS.entrySet().removeIf(x->x.getValue().owner.server==e.getServer());RECEIPTS.keySet().removeIf(p->p.server==e.getServer());REPLIES.keySet().removeIf(p->p.server==e.getServer());CLOCKS.remove(e.getServer());}
    private static void emit(Cast c,int phase,Vec3 impact,boolean accepted,String reason){var p=c.identity.owner();Vec3 arrival=c.plan==null?c.source:c.plan.side();var packet=new Visual(p.getId(),p.getUUID(),c.input.input(),c.input.epoch(),c.identity.token(),++c.sequence,phase,c.source,arrival,impact,c.medium==null?-1:c.medium.getId(),c.medium==null?new UUID(0,0):c.medium.getUUID(),accepted,reason);int n=0;for(var observer:p.serverLevel().players())if(!observer.hasDisconnected()&&observer.position().distanceToSqr(arrival)<=64*64){PacketDistributor.sendToPlayer(observer,packet);if(++n>=32)break;}}
    private static void diagnostic(Cast c,String stage,String reason){if(DIAGNOSTIC&&diagnosticEvents++<256)LOG.info("NEW311 stage={} token={} paid={} projected={} source={} arrival={} medium={} reason={}",stage,c.identity.token(),c.paid,c.projected,c.source,c.plan==null?c.source:c.plan.side(),c.medium==null?null:c.medium.getUUID(),reason);}
    public record Visual(int entity,UUID owner,long input,long epoch,long token,long sequence,int phase,Vec3 source,Vec3 arrival,Vec3 impact,int needleEntity,UUID needleUuid,boolean accepted,String reason) implements CustomPacketPayload {
        public Visual {if(entity<0||owner==null||input<=0||epoch<=0||token<=0||sequence<=0||phase<1||phase>4||needleEntity< -1||needleUuid==null||reason==null||reason.length()>64||!reason.matches("[a-z_]*")||!DisplacedProjectionWorld.finite(source)||!DisplacedProjectionWorld.finite(arrival)||!DisplacedProjectionWorld.finite(impact)||source.distanceToSqr(arrival)>40*40||accepted&&phase!=IMPACT||phase==PROJECTED&&(needleEntity<0||needleUuid.equals(new UUID(0,0))))throw new IllegalArgumentException("Invalid NEW311 visual");}
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_displaced_projection"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{b.writeInt(v.entity);b.writeUUID(v.owner);b.writeLong(v.input);b.writeLong(v.epoch);b.writeLong(v.token);b.writeLong(v.sequence);b.writeByte(v.phase);vec(b,v.source);vec(b,v.arrival);vec(b,v.impact);b.writeInt(v.needleEntity);b.writeUUID(v.needleUuid);b.writeBoolean(v.accepted);b.writeUtf(v.reason,64);},b->new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readUnsignedByte(),vec(b),vec(b),vec(b),b.readInt(),b.readUUID(),b.readBoolean(),b.readUtf(64)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static void vec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 vec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}}
}
