package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ShortInterceptionRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW331: one frozen target, actual existing arrows, or one separately escrowed physical needle. */
@EventBusSubscriber(modid="academy")
public final class ShortInterception {
    public static final String ID="short_interception";
    public static final int MARKED=0, PROJECTED=1, IMPACT=2, REFUSED=3, CANCELLED=4, ENDED=5;
    private static final String DATA="academy:short_interception_fences";
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<ServerPlayer,Context> TRANSACTIONS=new IdentityHashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Map<ServerPlayer,Receipt> RECEIPTS=new IdentityHashMap<>();
    private static final Map<Arrow,EmissionPermit> EMISSIONS=new IdentityHashMap<>();
    private static final Map<UUID,Flight> FLIGHTS=new HashMap<>();
    private static final Map<MinecraftServer,Integer> CLOCKS=new WeakHashMap<>(), POST_CLOCKS=new WeakHashMap<>();
    private static long nextToken;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("academy.short_interception");
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(ServerPlayer owner,long epoch,long input){}
    private record Fences(int request,int cooldown,int cast,List<Integer> quota){
        static final Fences ZERO=new Fences(0,0,0,List.of());
        Fences advance(){return new Fences(Math.max(0,request-1),Math.max(0,cooldown-1),Math.max(0,cast-1),quota.stream().filter(n->n>1).map(n->n-1).toList());}
    }
    public record Receipt(long input,long token,boolean paid,boolean projected,boolean hurt,String reason,
                          int admitted,boolean fallback,UUID target,Vec3 source,Vec3 arrival){}
    private record Source(Vec3 position,Vec3 eye,Vec3 motion,AABB body,float yaw,float pitch,float fall){
        static Source capture(ServerPlayer p){return new Source(p.position(),p.getEyePosition(),p.getDeltaMovement(),p.getBoundingBox(),p.getYRot(),p.getXRot(),p.fallDistance);}
        boolean matches(ServerPlayer p){return position.equals(p.position())&&eye.equals(p.getEyePosition())&&motion.equals(p.getDeltaMovement())&&body.equals(p.getBoundingBox())&&yaw==p.getYRot()&&pitch==p.getXRot()&&fall==p.fallDistance;}
    }
    private static final class Gesture {
        final HighPowerInput.Input input;long tick;int held;
        Gesture(ServerPlayer p,HighPowerInput.Input i){input=i;tick=now(p);}
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;final int preset;final long revision;
        HighPowerInput.Input command;Gesture gesture;LivingEntity target;UUID targetUuid;int targetId,remaining;
        Vec3 targetPoint,source,arrival;long tick,lastVisual,sequence;boolean processing,reentered,closing,terminal,paying,fallback;
        int paid,admitted,cooldown;double cost;BigDecimal damage;List<Segment> segments=new ArrayList<>();
        Context(ServerPlayer p,HighPowerInput.Input input){identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));preset=identity.state().presets.current();revision=identity.state().presets.revision();command=input;tick=now(p);source=arrival=p.getEyePosition();}
    }
    /** Only a confirmed paid relocation in this controller can construct this authority. */
    public static final class EmissionPermit {
        private final Context context;private final Arrow arrow;private final LivingEntity shooter,target;
        private final Vec3 origin;private final BigDecimal raw;private final long expires;private final double budget;
        private EmissionPermit(Context c,Arrow a,LivingEntity shooter,ShortInterceptionWorld.Plan plan){context=c;arrow=a;this.shooter=shooter;target=plan.target().entity();origin=plan.exit();raw=c.damage;expires=now(c.identity.owner())+20;budget=48-plan.usedRange();}
        public ServerPlayer controller(){return context.identity.owner();}
        public Arrow arrow(){return arrow;}
        public LivingEntity shooter(){return shooter;}
        public LivingEntity target(){return target;}
        public BigDecimal rawDamage(){return raw;}
        public Vec3 origin(){return origin;}
        public long expiresAt(){return expires;}
        public double travelBudget(){return budget;}
    }
    public static boolean emissionMatches(EmissionPermit permit){
        return permit!=null&&EMISSIONS.get(permit.arrow)==permit&&!permit.context.closing
                &&identity(permit.context)&&now(permit.controller())<permit.expires&&permit.arrow.level()==permit.controller().level()
                &&permit.arrow.getOwner()==permit.shooter&&ShortInterceptionWorld.hostile(permit.controller(),permit.target);
    }
    public static void onArrowOutcome(EmissionPermit permit,boolean hurt,String reason,Vec3 origin,Vec3 contact){
        if(permit==null)return;Context c=permit.context;
        if(hurt&&identity(c)&&ShortInterceptionWorld.finite(origin)&&ShortInterceptionWorld.finite(contact)){
            Vec3 direction=contact.subtract(origin);if(direction.lengthSqr()>1e-12){direction=direction.normalize();c.segments=List.of(new Segment(origin,contact,direction,direction));record(c,true,reason);emit(c,IMPACT,reason);}
        }
        EMISSIONS.remove(permit.arrow,permit);
    }
    private static final class Flight {
        final Context context;final DisplacedNeedleEntity medium;final DisplacedNeedleEntity.Identity identity;final ShortInterceptionWorld.Plan plan;
        Vec3 last;double traveled;int age;long tick;boolean closed;FallbackPermit claim;
        Flight(Context c,DisplacedNeedleEntity m,ShortInterceptionWorld.Plan p){context=c;medium=m;identity=m.identity();plan=p;last=p.exit();tick=now(c.identity.owner());}
    }
    public static final class FallbackPermit {
        private final Flight flight;private final LivingEntity target;private final Vec3 origin,endpoint;
        private final Source source;private final ShortInterceptionWorld.Target targetState;private boolean consumed;
        private FallbackPermit(Flight f,LivingEntity target,Vec3 origin,Vec3 endpoint){flight=f;this.target=target;this.origin=origin;this.endpoint=endpoint;source=Source.capture(player());targetState=ShortInterceptionWorld.Target.capture(target);}
        public ServerPlayer player(){return flight.context.identity.owner();}public LivingEntity target(){return target;}
        public BigDecimal damage(){return flight.context.damage;}public Vec3 origin(){return origin;}public Vec3 endpoint(){return endpoint;}
        public DisplacedNeedleEntity needle(){return flight.medium;}public boolean consume(){if(consumed)return false;consumed=true;return true;}
        public boolean valid(){return consumed&&flight.claim==this&&validFlight(flight)&&source.matches(player())&&targetState.same(player())
                &&target==flight.plan.target().entity()&&origin.equals(needle().contactSegmentOrigin())&&endpoint.equals(needle().contactPoint())
                &&endpoint.equals(needle().position())&&origin.equals(flight.last)&&flight.plan.usedRange()+flight.traveled+origin.distanceTo(endpoint)<=48
                &&DisplacedProjectionWorld.contactClear(player(),origin,endpoint,target,needle())&&source.matches(player())&&targetState.same(player())&&validFlight(flight);}
    }
    private ShortInterception(){}
    public static boolean start(ServerPlayer p,String wire){return press(p,wire);}
    public static boolean press(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        var old=INPUTS.get(p.getUUID());if(old!=null&&old.owner==p&&old.epoch==input.epoch()&&old.input>=input.input())return false;
        INPUTS.put(p.getUUID(),new Watermark(p,input.epoch(),input.input()));
        Context transaction=TRANSACTIONS.get(p);if(transaction!=null){transaction.reentered=true;return false;}
        if(AbilityConsumption.busy(p)||!mapped(p,input.slot())||!ShortInterceptionWorld.finite(p.getEyePosition()))return false;
        Context c=owned(p);if(c!=null&&(!identity(c)||c.command.epoch()!=input.epoch()||c.command.slot()!=input.slot())){finish(c,ENDED,"context_changed");return false;}
        if(c!=null&&(c.processing||c.gesture!=null))return false;
        if(c==null){if(nextToken==Long.MAX_VALUE||CONTEXTS.size()>=64)return false;c=new Context(p,input);CONTEXTS.put(p.getUUID(),c);}
        c.command=input;c.gesture=new Gesture(p,input);return true;
    }
    public static boolean release(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context tx=TRANSACTIONS.get(p);if(tx!=null){tx.reentered=true;return false;}
        Context c=owned(p);if(c==null||c.gesture==null||!c.gesture.input.equals(input)||c.processing)return false;
        int held=c.gesture.held;c.gesture=null;if(held>=6){clear(p);return true;}
        return action(c);
    }
    private static boolean action(Context c){
        ServerPlayer p=c.identity.owner();c.processing=true;c.reentered=false;TRANSACTIONS.put(p,c);
        try{
            if(!identity(c)||!HighPowerResources.ready(p))return refuse(c,"not_ready");
            Fences fences=readFences(p);if(fences==null)return refuse(c,"storage_invalid");
            if(fences.request>0)return refuse(c,"request_fence");writeFences(p,new Fences(4,fences.cooldown,fences.cast,fences.quota));
            if(c.target==null){
                LivingEntity target=ShortInterceptionWorld.acquire(p);if(target==null)return refuse(c,"no_target");
                if(!identity(c)||c.reentered)return refuse(c,"transaction_changed");
                c.target=target;c.targetUuid=target.getUUID();c.targetId=target.getId();c.targetPoint=target.getBoundingBox().getCenter();c.remaining=ShortInterceptionRules.MARK_TICKS;c.tick=now(p);
                record(c,false,"");emit(c,MARKED,"");return true;
            }
            if(fences.cooldown>0||fences.cast>0||!c.identity.state().canUse(ID))return refuse(c,"cooldown");
            if(fences.quota.size()>=4)return refuse(c,"quota");
            if(!sameTarget(c)||!ShortInterceptionWorld.visibleTarget(p,c.target))return refuse(c,"target_changed");
            var source=Source.capture(p);var target=ShortInterceptionWorld.Target.capture(c.target);c.targetPoint=target.point();
            var selected=ShortInterceptionWorld.select(p);if(selected==null)return refuse(c,"scan_refused");
            var growth=HighPowerGrowth.values(p);c.cost=ShortInterceptionRules.cpCost(growth.position());c.cooldown=ShortInterceptionRules.cooldownTicks(growth.position());c.damage=ShortInterceptionRules.damage(growth.damage());
            c.paid=c.admitted=0;c.segments=new ArrayList<>();c.source=source.eye;c.arrival=source.eye;c.fallback=selected.isEmpty();
            if(c.fallback)return fallback(c,source,target);
            int count=Math.min(selected.size(),4-fences.quota.size());
            for(int i=0;i<count;i++){
                var snapshot=selected.get(i);Arrow arrow=snapshot.arrow();
                if(!unchanged(c,source,target)||!ShortInterceptionArrowBridge.matches(snapshot))return refuse(c,"transaction_changed");
                var plan=ShortInterceptionWorld.plan(p,target,snapshot.position(),snapshot.velocity().length(),i,count,arrow);
                if(plan==null)return refuse(c,"blocked");
                EntityTeleportEvent event=new EntityTeleportEvent(arrow,plan.exit().x,plan.exit().y,plan.exit().z);NeoForge.EVENT_BUS.post(event);
                if(event.isCanceled()||event.getTargetX()!=plan.exit().x||event.getTargetY()!=plan.exit().y||event.getTargetZ()!=plan.exit().z)return refuse(c,"teleport_canceled");
                if(!unchanged(c,source,target)||!ShortInterceptionArrowBridge.matches(snapshot)||!ShortInterceptionWorld.validate(p,plan,arrow)||!unchanged(c,source,target))return refuse(c,"transaction_changed");
                var claim=ShortInterceptionArrowBridge.reserveRelocation(snapshot);if(claim==null)return refuse(c,"projectile_busy");
                try{
                    if(!pay(c,source,target))return refuse(c,"insufficient_cp");
                    if(!ShortInterceptionArrowBridge.markPaid(claim))return refuse(c,"commit_unconfirmed");
                    // Native setter may run tracking callbacks. Paid ambiguity never retries or creates a needle.
                    arrow.setPos(plan.exit());arrow.setDeltaMovement(plan.velocity());
                    if(!unchanged(c,source,target)||!ShortInterceptionArrowBridge.matchesRelocated(snapshot,plan.exit(),plan.velocity(),claim))return refuse(c,"commit_unconfirmed");
                    EmissionPermit permit=new EmissionPermit(c,arrow,snapshot.shooter(),plan);EMISSIONS.put(arrow,permit);
                    if(!ShortInterceptionArrowBridge.arm(permit,claim)){EMISSIONS.remove(arrow,permit);return refuse(c,"commit_unconfirmed");}
                    c.admitted++;c.arrival=plan.exit();c.segments.add(new Segment(snapshot.position(),plan.exit(),snapshot.velocity().normalize(),plan.velocity().normalize()));
                    arrow.hasImpulse=true;arrow.hurtMarked=true;
                    try{p.serverLevel().getChunkSource().broadcastAndSend(arrow,new ClientboundTeleportEntityPacket(arrow));p.serverLevel().getChunkSource().broadcastAndSend(arrow,new ClientboundSetEntityMotionPacket(arrow));}
                    catch(RuntimeException delivery){LOG.warn("NEW331 confirmed native relocation retained despite packet delivery failure",delivery);}
                }finally{ShortInterceptionArrowBridge.release(claim);}
            }
            complete(c);return true;
        }catch(RuntimeException failure){LOG.warn("NEW331 stopped without replay or fallback; paid={}",c.paid,failure);return refuse(c,c.paid>0?"commit_unconfirmed":"validation_failed");}
        finally{c.paying=false;c.processing=false;TRANSACTIONS.remove(p,c);}
    }
    private static boolean fallback(Context c,Source source,ShortInterceptionWorld.Target target){
        ServerPlayer p=c.identity.owner();
        if(InterceptionNeedleRegistry.availability(p)!=InterceptionNeedleRegistry.Availability.READY)return refuse(c,"no_medium");
        var plan=ShortInterceptionWorld.plan(p,target,source.eye,ShortInterceptionRules.NEEDLE_SPEED,0,1,null);
        if(plan==null)return refuse(c,"blocked");
        var medium=InterceptionNeedleRegistry.deploy(p,plan.exit(),()->unchanged(c,source,target)
                &&ShortInterceptionWorld.validate(p,plan,InterceptionNeedleRegistry.pending(p)),()->pay(c,source,target)).orElse(null);
        if(medium==null)return refuse(c,c.paid>0?"commit_unconfirmed":"spawn_rejected");
        medium.setDeltaMovement(plan.velocity());Flight flight=new Flight(c,medium,plan);FLIGHTS.put(p.getUUID(),flight);
        if(!medium.beginFlight(flight.identity)){retireFallback(medium);return refuse(c,"commit_unconfirmed");}
        c.admitted=1;c.arrival=plan.exit();c.segments.add(new Segment(source.eye,plan.exit(),p.getLookAngle().normalize(),plan.velocity().normalize()));complete(c);return true;
    }
    private static boolean pay(Context c,Source source,ShortInterceptionWorld.Target target){
        var p=c.identity.owner();if(!unchanged(c,source,target)||HighPowerResources.hasReservation(p))return false;
        Fences f=readFences(p);if(f==null||f.quota.size()>=4||c.paid==0&&(f.cast>0||f.cooldown>0))return false;
        c.paying=true;
        try{
            if(!HighPowerResources.trySpendShortInterception(p,c.cost,c.cooldown,c.paid==0))return false;
            c.paid++;var quota=new ArrayList<>(f.quota);quota.add(20);writeFences(p,new Fences(f.request,c.paid==1?c.cooldown:f.cooldown,8,List.copyOf(quota)));return true;
        }finally{c.paying=false;}
    }
    static boolean payingProjectile(ServerPlayer p){Context c=TRANSACTIONS.get(p);return c!=null&&c.processing&&c.paying&&!c.reentered&&!c.closing&&c.paid<4;}
    private static boolean unchanged(Context c,Source source,ShortInterceptionWorld.Target target){return TRANSACTIONS.get(c.identity.owner())==c&&c.processing&&!c.reentered&&!c.closing&&identity(c)&&source.matches(c.identity.owner())&&target.same(c.identity.owner());}
    private static boolean sameTarget(Context c){return c.target!=null&&c.targetUuid.equals(c.target.getUUID())&&c.targetId==c.target.getId()&&ShortInterceptionWorld.hostile(c.identity.owner(),c.target);}
    private static boolean identity(Context c){
        var p=c.identity.owner();var s=c.identity.state();return c.identity.owns(p)&&mapped(p,c.command.slot())&&s.presets.current()==c.preset&&s.presets.revision()==c.revision
                &&p.isAlive()&&!p.isRemoved()&&!p.hasDisconnected()&&!p.isCreative()&&!p.isSpectator()&&!p.getAbilities().invulnerable
                &&p.containerMenu==p.inventoryMenu&&s.activated&&s.overloadFine&&!s.interfering&&!s.consumptionInProgress()
                &&s.learned(ID)&&s.learned("cross_substitution")&&SkillCatalog.enabled("teleporter",ID)&&s.level>=3
                &&HighPowerCategories.isTeleporter(s.category)&&HighPowerCombat.enabled(p)&&HighPowerCombat.unsupportedReason(p).isEmpty()
                &&HighPowerResources.effectiveLevel(p)>=3&&ShortInterceptionWorld.finite(p.getEyePosition())&&Float.isFinite(p.getYRot())&&Float.isFinite(p.getXRot());
    }
    private static boolean thread(ServerPlayer p){return p!=null&&p.server!=null&&p.server.isSameThread();}
    private static long now(ServerPlayer p){return Integer.toUnsignedLong(p.server.getTickCount());}
    private static boolean mapped(ServerPlayer p,int slot){return thread(p)&&slot>=0&&slot<4&&ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));}
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    public static boolean canBegin(ServerPlayer p){Context c=owned(p);return thread(p)&&!TRANSACTIONS.containsKey(p)&&(c==null||!c.processing&&c.gesture==null);}
    public static boolean active(ServerPlayer p){return thread(p)&&TRANSACTIONS.containsKey(p);}
    public static boolean marked(ServerPlayer p){Context c=owned(p);return c!=null&&c.target!=null&&c.remaining>0&&!c.closing;}
    public static int remainingTicks(ServerPlayer p){Context c=owned(p);return c==null?0:c.remaining;}
    public static int heldTicks(ServerPlayer p){Context c=owned(p);return c==null||c.gesture==null?0:c.gesture.held;}
    public static Receipt receipt(ServerPlayer p){return RECEIPTS.get(p);}
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context c=owned(p);if(c!=null&&(c.command.epoch()!=input.epoch()||c.command.slot()!=input.slot()||input.input()<c.command.input()))return false;
        INPUTS.put(p.getUUID(),new Watermark(p,input.epoch(),input.input()));clear(p);return true;
    }
    public static void clear(ServerPlayer p){if(!thread(p))return;Context c=owned(p);if(c!=null){c.closing=true;c.reentered=true;finish(c,CANCELLED,"cleared");}ShortInterceptionArrowBridge.invalidate(p);EMISSIONS.entrySet().removeIf(e->e.getValue().controller()==p);Flight f=FLIGHTS.get(p.getUUID());if(f!=null&&f.context.identity.owner()==p)retireFallback(f.medium);}
    public static void cancelActive(ServerPlayer p){clear(p);}
    public static void remove(ServerPlayer p){clear(p);if(p==null)return;INPUTS.computeIfPresent(p.getUUID(),(id,w)->w.owner==p?null:w);RECEIPTS.remove(p);}
    private static boolean refuse(Context c,String reason){
        if(c.paid>0||c.closing||!identity(c)||c.target==null||c.remaining<=0){finish(c,REFUSED,reason);return false;}
        record(c,false,reason);emit(c,REFUSED,reason);return false;
    }
    private static void complete(Context c){c.remaining=0;c.terminal=true;c.gesture=null;CONTEXTS.remove(c.identity.owner().getUUID(),c);record(c,false,"");emit(c,PROJECTED,"");AbilityStorage.save(c.identity.owner());AcademyNetwork.sync(c.identity.owner());}
    private static void finish(Context c,int phase,String reason){c.remaining=0;c.terminal=true;c.gesture=null;CONTEXTS.remove(c.identity.owner().getUUID(),c);record(c,false,reason);emit(c,phase,reason);}
    private static void record(Context c,boolean hurt,String reason){var p=c.identity.owner();if(p.hasDisconnected()||p.isRemoved())return;RECEIPTS.put(p,new Receipt(c.command.input(),c.identity.token(),c.paid>0,c.admitted>0,hurt,reason,c.admitted,c.fallback,c.targetUuid,c.source,c.arrival));}
    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.processing)return;
        if(!identity(c)){finish(c,ENDED,"context_changed");return;}
        long tick=now(p);if(c.tick!=tick){c.tick=tick;if(c.target!=null&&--c.remaining<=0){finish(c,ENDED,"expired");return;}}
        if(c.target!=null&&!ShortInterceptionWorld.visibleTarget(p,c.target)){finish(c,ENDED,"target_changed");return;}
        if(c.gesture!=null&&c.gesture.tick!=tick){c.gesture.tick=tick;if(++c.gesture.held>=6){clear(p);return;}}
        if(c.target!=null&&c.gesture==null&&(tick<c.lastVisual||tick-c.lastVisual>=10)){c.targetPoint=c.target.getBoundingBox().getCenter();emit(c,MARKED,"");}
    }
    static boolean canContactFallback(DisplacedNeedleEntity medium,DisplacedNeedleEntity.Identity id,Entity target){Flight f=FLIGHTS.get(id.owner());return f!=null&&f.medium==medium&&f.identity.equals(id)&&target==f.plan.target().entity()&&validFlight(f);}
    static void contactFallback(DisplacedNeedleEntity medium,DisplacedNeedleEntity.Identity id,EntityHitResult hit){
        Flight f=FLIGHTS.get(id.owner());if(f==null||f.medium!=medium)return;boolean hurt=false;
        try{Vec3 origin=medium.contactSegmentOrigin();if(!canContactFallback(medium,id,hit.getEntity())||origin==null||!origin.equals(f.last)||!(hit.getEntity() instanceof LivingEntity target)||!InterceptionNeedleRegistry.consumeContact(medium,id.generation()))return;
            FallbackPermit permit=new FallbackPermit(f,target,origin,medium.contactPoint());f.claim=permit;hurt=HighPowerCombat.attackShortInterceptionFallback(permit);
        }finally{f.claim=null;if(hurt){Vec3 a=medium.contactSegmentOrigin(),b=medium.contactPoint();if(a!=null&&b!=null&&b.distanceToSqr(a)>1e-12){Vec3 dir=b.subtract(a).normalize();f.context.segments=List.of(new Segment(a,b,dir,dir));record(f.context,true,"");emit(f.context,IMPACT,"");}}retireFallback(medium);}
    }
    static void retireFallback(DisplacedNeedleEntity medium){if(medium==null||medium.identity()==null)return;Flight f=FLIGHTS.get(medium.identity().owner());if(f!=null&&f.medium==medium){f.closed=true;FLIGHTS.remove(medium.identity().owner(),f);}InterceptionNeedleRegistry.retireContact(medium);}
    private static boolean validFlight(Flight f){return !f.closed&&f.age<20&&!f.context.closing&&identity(f.context)&&ShortInterceptionWorld.hostile(f.context.identity.owner(),f.plan.target().entity())
            &&f.medium.identity()!=null&&f.identity.equals(f.medium.identity())&&InterceptionNeedleRegistry.authentic(f.context.identity.owner(),f.medium)
            &&!f.medium.isRemoved()&&!f.medium.inGround&&f.plan.usedRange()+f.traveled<=48;}
    private static Fences readFences(ServerPlayer p){
        var root=p.getPersistentData();if(!root.contains(DATA))return Fences.ZERO;if(!root.contains(DATA,Tag.TAG_COMPOUND))return null;var t=root.getCompound(DATA);
        if(!t.contains("version",Tag.TAG_INT)||t.getInt("version")!=1||!t.contains("request",Tag.TAG_INT)||!t.contains("cooldown",Tag.TAG_INT)||!t.contains("cast",Tag.TAG_INT)||!t.contains("quota",Tag.TAG_INT_ARRAY))return null;
        int r=t.getInt("request"),d=t.getInt("cooldown"),c=t.getInt("cast");int[] q=t.getIntArray("quota");if(r<0||r>4||d<0||d>60||c<0||c>8||q.length>4)return null;
        var list=new ArrayList<Integer>();for(int n:q){if(n<1||n>20)return null;list.add(n);}return new Fences(r,d,c,List.copyOf(list));
    }
    private static void writeFences(ServerPlayer p,Fences f){var t=new CompoundTag();t.putInt("version",1);t.putInt("request",f.request);t.putInt("cooldown",f.cooldown);t.putInt("cast",f.cast);t.putIntArray("quota",f.quota);p.getPersistentData().put(DATA,t);}
    @SubscribeEvent public static void pre(ServerTickEvent.Pre e){
        Integer old=CLOCKS.put(e.getServer(),e.getServer().getTickCount());if(old!=null&&old==e.getServer().getTickCount())return;
        for(var p:e.getServer().getPlayerList().getPlayers())if(p.getPersistentData().contains(DATA)){var f=readFences(p);if(f!=null&&!f.equals(Fences.ZERO))writeFences(p,f.advance());}
    }
    @SubscribeEvent public static void post(ServerTickEvent.Post e){
        Integer old=POST_CLOCKS.put(e.getServer(),e.getServer().getTickCount());if(old!=null&&old==e.getServer().getTickCount())return;
        for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.owner().server==e.getServer())tick(c.identity.owner());
        for(Flight f:new ArrayList<>(FLIGHTS.values()))if(f.context.identity.owner().server==e.getServer()){
            long now=now(f.context.identity.owner());if(f.tick==now)continue;f.tick=now;f.age++;
            Vec3 pos=f.medium.position();if(!validFlight(f)||!ShortInterceptionWorld.finite(pos)){retireFallback(f.medium);continue;}f.traveled+=f.last.distanceTo(pos);f.last=pos;
            if(f.plan.usedRange()+f.traveled>48||!f.medium.isContactEnabled())retireFallback(f.medium);
        }
        ShortInterceptionArrowBridge.prune(e.getServer());EMISSIONS.entrySet().removeIf(x->x.getValue().controller().server==e.getServer()&&(!identity(x.getValue().context)||now(x.getValue().controller())>=x.getValue().expiresAt()||x.getKey().isRemoved()));
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer p){remove(old);if(old.getPersistentData().contains(DATA))p.getPersistentData().put(DATA,old.getPersistentData().get(DATA).copy());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){ShortInterceptionArrowBridge.clear();CONTEXTS.entrySet().removeIf(x->x.getValue().identity.owner().server==e.getServer());TRANSACTIONS.keySet().removeIf(p->p.server==e.getServer());INPUTS.entrySet().removeIf(x->x.getValue().owner.server==e.getServer());RECEIPTS.keySet().removeIf(p->p.server==e.getServer());EMISSIONS.entrySet().removeIf(x->x.getValue().controller().server==e.getServer());FLIGHTS.entrySet().removeIf(x->x.getValue().context.identity.owner().server==e.getServer());CLOCKS.remove(e.getServer());POST_CLOCKS.remove(e.getServer());}
    private static void emit(Context c,int phase,String reason){
        try{if(c.sequence==Long.MAX_VALUE)return;var p=c.identity.owner();var packet=new Visual(p.getId(),p.getUUID(),c.command.input(),c.command.epoch(),c.identity.token(),++c.sequence,phase,c.remaining,c.targetPoint,List.copyOf(c.segments),reason,c.fallback,c.admitted);c.lastVisual=now(p);
            PacketDistributor.sendToPlayer(p,packet);if(phase==PROJECTED||phase==IMPACT)for(var observer:p.serverLevel().players())if(observer!=p&&observer.distanceToSqr(p)<=64*64)PacketDistributor.sendToPlayer(observer,packet);
        }catch(RuntimeException failure){LOG.warn("NEW331 retains authoritative state after visual delivery failure",failure);}
    }
    public record Segment(Vec3 entry,Vec3 exit,Vec3 incoming,Vec3 outgoing){public Segment{if(!ShortInterceptionWorld.finite(entry)||!ShortInterceptionWorld.finite(exit)||!ShortInterceptionWorld.finite(incoming)||!ShortInterceptionWorld.finite(outgoing)||incoming.lengthSqr()<.5||incoming.lengthSqr()>1.5||outgoing.lengthSqr()<.5||outgoing.lengthSqr()>1.5)throw new IllegalArgumentException("Invalid spatial segment");}}
    public record Visual(int entity,UUID owner,long input,long epoch,long token,long sequence,int phase,int remainingTicks,@Nullable Vec3 target,List<Segment> segments,String reason,boolean fallback,int admitted) implements CustomPacketPayload{
        public Visual{segments=List.copyOf(segments);if(entity<0||owner==null||input<=0||epoch<=0||token<=0||sequence<=0||phase<0||phase>5||remainingTicks<0||remainingTicks> ShortInterceptionRules.MARK_TICKS||target!=null&&!ShortInterceptionWorld.finite(target)||segments.size()>4||reason==null||reason.length()>64||admitted<0||admitted>4)throw new IllegalArgumentException("Invalid interception visual");}
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_short_interception"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{b.writeVarInt(v.entity);b.writeUUID(v.owner);b.writeVarLong(v.input);b.writeVarLong(v.epoch);b.writeVarLong(v.token);b.writeVarLong(v.sequence);b.writeByte(v.phase);b.writeVarInt(v.remainingTicks);b.writeBoolean(v.target!=null);if(v.target!=null)vec(b,v.target);b.writeVarInt(v.segments.size());for(var s:v.segments){vec(b,s.entry);vec(b,s.exit);vec(b,s.incoming);vec(b,s.outgoing);}b.writeUtf(v.reason,64);b.writeBoolean(v.fallback);b.writeVarInt(v.admitted);},b->{int entity=b.readVarInt();UUID owner=b.readUUID();long input=b.readVarLong(),epoch=b.readVarLong(),token=b.readVarLong(),sequence=b.readVarLong();int phase=b.readUnsignedByte(),remaining=b.readVarInt();Vec3 target=b.readBoolean()?vec(b):null;int n=b.readVarInt();if(n<0||n>4)throw new IllegalArgumentException("Too many spatial segments");var segments=new ArrayList<Segment>();for(int i=0;i<n;i++)segments.add(new Segment(vec(b),vec(b),vec(b),vec(b)));return new Visual(entity,owner,input,epoch,token,sequence,phase,remaining,target,segments,b.readUtf(64),b.readBoolean(),b.readVarInt());});
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static void vec(RegistryFriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
    private static Vec3 vec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD) public static final class Network{@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}}
}
