package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.TrajectoryReturnRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.Projectile;
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

/** NEW431: reverse real hostile native arrows once, or one empty-corridor straight air hammer. */
@EventBusSubscriber(modid="academy")
public final class TrajectoryReturn {
    public static final String ID="trajectory_return";
    public static final int REVERSE=0, FLIGHT=1, CONTACT=2, AIR_HAMMER=3, ENDED=4;
    public static final int REFUSED=0, DAMAGED=1, ABSORBED=2, COMMITTED=3;
    private static final String DATA="academy:trajectory_return_fences", CLOCK="next_"+ID;
    private static final Map<UUID,Context> CURRENT=new HashMap<>();
    private static final Set<Context> CASTS=Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<ServerPlayer,Context> TRANSACTIONS=new IdentityHashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Map<ServerPlayer,Receipt> RECEIPTS=new IdentityHashMap<>();
    private static final Map<Arrow,EmissionPermit> EMISSIONS=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Integer> CLOCKS=new WeakHashMap<>(), POST_CLOCKS=new WeakHashMap<>();
    private static long nextToken;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("academy.trajectory_return");
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(ServerPlayer owner,long epoch,long input) {}
    private record Fences(int request,int cooldown,List<Integer> quota) {
        static final Fences ZERO=new Fences(0,0,List.of());
        Fences advance(){return new Fences(Math.max(0,request-1),Math.max(0,cooldown-1),quota.stream().filter(n->n>1).map(n->n-1).toList());}
    }
    private record Source(Vec3 position,Vec3 eye,Vec3 velocity,AABB box,float yaw,float pitch,float fall,boolean crouch) {
        static Source capture(ServerPlayer p){return new Source(p.position(),p.getEyePosition(),p.getDeltaMovement(),p.getBoundingBox(),p.getYRot(),p.getXRot(),p.fallDistance,p.isShiftKeyDown());}
        boolean matches(ServerPlayer p){return position.equals(p.position())&&eye.equals(p.getEyePosition())&&velocity.equals(p.getDeltaMovement())&&box.equals(p.getBoundingBox())&&yaw==p.getYRot()&&pitch==p.getXRot()&&fall==p.fallDistance&&crouch==p.isShiftKeyDown();}
    }
    private record Target(LivingEntity entity,UUID uuid,int id,Vec3 position,AABB box,AABB deathBox,net.minecraft.world.entity.Pose pose,Vec3 point) {
        static Target capture(LivingEntity e,Vec3 point){return new Target(e,e.getUUID(),e.getId(),e.position(),e.getBoundingBox(),e.getDimensions(net.minecraft.world.entity.Pose.DYING).makeBoundingBox(e.position()),e.getPose(),point);}
        boolean same(ServerPlayer p){return hostile(p,entity)&&uuid.equals(entity.getUUID())&&id==entity.getId()&&position.equals(entity.position())&&pose==entity.getPose()&&box.equals(entity.getBoundingBox());}
    }
    private record Body(Entity entity,Vec3 point,double distance) {}
    public record Receipt(long input,long token,boolean paid,int admitted,boolean fallback,boolean hurt,String reason) {}
    public record ImpactReceipt(int slot,int kind,Vec3 origin,Vec3 end,Vec3 velocity,int status,UUID victim) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;final HighPowerInput.Input input;final int preset;final long revision,started;
        final Source source;final Vec3 axis,end;final BigDecimal growth,damage;final double cost;final int cooldown;
        final List<ImpactReceipt> receipts=new ArrayList<>();final Set<ServerPlayer> audience=new LinkedHashSet<>();
        CompoundTag ledger,fences;long balance,clock,sequence;int abilityCooldown,admitted;boolean paid,fallback,hurt,resolving,reentered,closing;
        AirHammerPermit hammer;
        Context(ServerPlayer p,HighPowerInput.Input input){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));this.input=input;
            preset=identity.state().presets.current();revision=identity.state().presets.revision();started=now(p);source=Source.capture(p);axis=p.getLookAngle().normalize();
            var endpoint=TrajectoryReturnRules.endpoint(point(source.eye),point(axis));end=endpoint==null?null:vector(endpoint);
            var values=HighPowerGrowth.values(p);growth=values.position();damage=TrajectoryReturnRules.damage(values.damage());
            cost=TrajectoryReturnRules.cpCost(growth);cooldown=TrajectoryReturnRules.cooldownTicks(growth);captureResources(this);fences=p.getPersistentData().getCompound(DATA).copy();
        }
    }
    /** Only the paid reversal transaction can create this independent NEW431 native permission. */
    public static final class EmissionPermit {
        private final Context context;private final Arrow arrow;private final LivingEntity shooter;private final int slot;
        private final Vec3 origin;private final long expires;
        private EmissionPermit(Context c,Arrow arrow,LivingEntity shooter,int slot){context=c;this.arrow=arrow;this.shooter=shooter;this.slot=slot;origin=arrow.position();expires=now(c.identity.owner())+TrajectoryReturnRules.FLIGHT_TICKS;}
        public ServerPlayer controller(){return context.identity.owner();}public Arrow arrow(){return arrow;}public LivingEntity shooter(){return shooter;}
        public BigDecimal rawDamage(){return context.damage;}public Vec3 origin(){return origin;}public long expiresAt(){return expires;}
        /** Frozen cast eye, distinct from the unchanged native arrow reversal position returned by origin(). */
        public Vec3 castOrigin(){return context.source.eye;}
        public double travelBudget(){return TrajectoryReturnRules.RANGE;}public int slot(){return slot;}public long token(){return context.identity.token();}
    }
    /** Read-only per-native-contact observation. Normal movement between native ticks remains possible. */
    static final class ContactState {
        final EmissionPermit permit;final Source source;final CompoundTag ledger,fences;final long balance,clock,tick;
        final int cooldown;final BigDecimal growth;final Watermark input;
        ContactState(EmissionPermit permit){
            this.permit=permit;var p=permit.controller();source=Source.capture(p);ledger=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            balance=Double.doubleToLongBits(ledger.getDouble("cp"));clock=ledger.getLong(CLOCK);tick=now(p);cooldown=AbilityStorage.get(p).cooldowns.getOrDefault(ID,0);
            growth=HighPowerGrowth.values(p).position();input=INPUTS.get(p.getUUID());fences=p.getPersistentData().getCompound(DATA).copy();
        }
        boolean matches(){
            var p=permit.controller();return emissionMatches(permit)&&now(p)==tick&&source.matches(p)&&INPUTS.get(p.getUUID())==input
                    &&p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)==ledger&&Double.doubleToLongBits(ledger.getDouble("cp"))==balance
                    &&ledger.getLong(CLOCK)==clock&&AbilityStorage.get(p).cooldowns.getOrDefault(ID,0)==cooldown
                    &&growth.compareTo(HighPowerGrowth.values(p).position())==0&&fences.equals(p.getPersistentData().getCompound(DATA));
        }
    }
    /** Closed one-body opportunity; the combat writer supplies no alternate target or amount. */
    public static final class AirHammerPermit {
        private final Context context;private final Target target;private boolean consumed;
        private AirHammerPermit(Context c,Target t){context=c;target=t;}
        public ServerPlayer player(){return context.identity.owner();}public LivingEntity target(){return target.entity;}
        public Vec3 origin(){return context.source.eye;}public Vec3 endpoint(){return target.point;}public BigDecimal damage(){return context.damage;}
        public boolean consume(){if(consumed||!valid())return false;consumed=true;return true;}
        public boolean valid(){
            if(context.hammer!=this||!context.fallback||!context.paid||!unchanged(context)||!target.same(player()))return false;
            // A callback adding a projectile cannot turn a now occupied corridor into an air hammer.
            var arrows=select(context);if(arrows==null||!arrows.isEmpty())return false;
            Target current=firstBody(context);
            return current!=null&&current.entity==target.entity&&current.point.equals(target.point)&&target.same(player())&&unchanged(context);
        }
    }
    private TrajectoryReturn() {}
    public static boolean start(ServerPlayer p,String wire){return tap(p,wire);}
    public static boolean tap(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark prior=INPUTS.get(p.getUUID());if(prior!=null&&prior.owner==p&&prior.epoch==input.epoch()&&prior.input>=input.input())return false;
        INPUTS.put(p.getUUID(),new Watermark(p,input.epoch(),input.input()));
        Context running=TRANSACTIONS.get(p);if(running!=null){running.reentered=true;return false;}
        if(!canAct(p)||!mapped(p,input.slot())||nextToken==Long.MAX_VALUE||CASTS.size()>=256)return feedback(p,"not_ready");
        Fences f=readFences(p);if(f==null||f.request>0||f.cooldown>0||!AbilityStorage.get(p).canUse(ID))return feedback(p,"cooldown");
        // Cheap independent physical request fence precedes every potentially expensive scan, including a free refusal.
        writeFences(p,new Fences(TrajectoryReturnRules.CAST_FENCE,f.cooldown,f.quota));
        Context c=new Context(p,input);CURRENT.put(p.getUUID(),c);CASTS.add(c);TRANSACTIONS.put(p,c);c.resolving=true;
        try{
            if(!unchanged(c)||c.end==null)return refuse(c,"not_ready");
            List<TrajectoryReturnArrowBridge.Snapshot> selected=select(c);
            if(selected==null)return refuse(c,"scan_refused");
            if(!unchanged(c))return refuse(c,"transaction_changed");
            c.fallback=selected.isEmpty();
            if(c.fallback)return airHammer(c);
            int count=Math.min(selected.size(),TrajectoryReturnRules.MAX_ARROWS-f.quota.size());
            if(count<=0)return refuse(c,"quota");
            for(int slot=0;slot<count;slot++){
                var snapshot=selected.get(slot);Arrow arrow=snapshot.arrow();
                if(!unchanged(c)||!TrajectoryReturnArrowBridge.matches(snapshot))return refuse(c,"transaction_changed");
                var claim=TrajectoryReturnArrowBridge.reserveReverse(snapshot);if(claim==null)return refuse(c,"transaction_changed");
                try{
                    if(!pay(c))return refuse(c,"not_ready");
                    if(!unchanged(c)||!TrajectoryReturnArrowBridge.markPaid(claim))return refuse(c,"commit_unconfirmed");
                    // Quota is permanent once this arrow's control opportunity is consumed, even if a callback makes its result uncertain.
                    Fences current=readFences(p);if(current==null||current.quota.size()>=4)return refuse(c,"commit_unconfirmed");
                    var quota=new ArrayList<>(current.quota);quota.add(TrajectoryReturnRules.QUOTA_TICKS);
                    writeFences(p,new Fences(current.request,current.cooldown,List.copyOf(quota)));
                    c.fences=p.getPersistentData().getCompound(DATA).copy();
                    Vec3 reversed=snapshot.velocity().scale(-1);
                    arrow.setDeltaMovement(reversed);
                    if(!unchanged(c)||!TrajectoryReturnArrowBridge.matchesReversed(snapshot,snapshot.position(),reversed,claim))return refuse(c,"commit_unconfirmed");
                    EmissionPermit permit=new EmissionPermit(c,arrow,snapshot.shooter(),slot);EMISSIONS.put(arrow,permit);
                    if(!TrajectoryReturnArrowBridge.arm(permit,claim)){EMISSIONS.remove(arrow,permit);return refuse(c,"commit_unconfirmed");}
                    c.admitted++;arrow.hasImpulse=true;arrow.hurtMarked=true;
                    receipt(c,slot,REVERSE,snapshot.position(),snapshot.position(),reversed,COMMITTED,null);
                    try{p.serverLevel().getChunkSource().broadcastAndSend(arrow,new ClientboundSetEntityMotionPacket(arrow));}
                    catch(RuntimeException failure){LOG.warn("NEW431 keeps committed reversal after packet delivery failure",failure);}
                }finally{TrajectoryReturnArrowBridge.release(claim);}
            }
            record(c,"");sync(p);return true;
        }catch(RuntimeException failure){LOG.warn("NEW431 stopped without retry, refund or fallback; paid={}",c.paid,failure);return refuse(c,c.paid?"commit_unconfirmed":"transaction_changed");}
        finally{c.hammer=null;c.resolving=false;TRANSACTIONS.remove(p,c);}
    }
    private static boolean airHammer(Context c){
        Target target=firstBody(c);if(target==null)return refuse(c,"no_target");
        if(!unchanged(c)||!target.same(c.identity.owner()))return refuse(c,"transaction_changed");
        var finalScan=select(c);if(finalScan==null||!finalScan.isEmpty())return refuse(c,"scan_refused");
        Target current=firstBody(c);if(current==null||current.entity!=target.entity||!current.point.equals(target.point))return refuse(c,"blocked");
        if(!pay(c))return refuse(c,"not_ready");
        c.admitted=1;
        AirHammerPermit permit=new AirHammerPermit(c,target);c.hammer=permit;
        BigDecimal hp=health(target.entity),shield=HighPowerCombat.shield(target.entity);float absorption=target.entity.getAbsorptionAmount();
        boolean accepted,display;
        try{accepted=HighPowerCombat.attackTrajectoryReturnAirHammer(permit);display=accepted||permit.valid();}finally{c.hammer=null;}
        int status=REFUSED;
        if(accepted&&sameEntity(c,target)){
            if(health(target.entity).compareTo(hp)<0)status=DAMAGED;
            else if(HighPowerCombat.shield(target.entity).compareTo(shield)<0||target.entity.getAbsorptionAmount()<absorption)status=ABSORBED;
        }
        c.hurt=status==DAMAGED||status==ABSORBED;
        // A now obstructed or mutated callback path must not be drawn as a real completed segment.
        if(display&&unchanged(c)&&sameEntity(c,target))receipt(c,0,AIR_HAMMER,c.source.eye,target.point,Vec3.ZERO,status,status==REFUSED?null:target.uuid);
        record(c,status==REFUSED?"native_refused":"");sync(c.identity.owner());return true;
    }
    private static boolean sameEntity(Context c,Target t){return t.entity.level()==c.identity.level()&&t.id==t.entity.getId()&&t.uuid.equals(t.entity.getUUID())
            &&c.identity.level().getEntity(t.uuid)==t.entity&&t.position.equals(t.entity.position())
            &&(t.pose==t.entity.getPose()&&t.box.equals(t.entity.getBoundingBox())
                ||!t.entity.isAlive()&&t.entity.getPose()==net.minecraft.world.entity.Pose.DYING&&t.deathBox.equals(t.entity.getBoundingBox()));}
    private static boolean pay(Context c){
        if(c.paid)return unchanged(c);
        if(!unchanged(c))return false;
        Fences f=readFences(c.identity.owner());if(f==null||f.request!=TrajectoryReturnRules.CAST_FENCE||f.cooldown>0)return false;
        if(!HighPowerResources.trySpend(c.identity.owner(),ID,c.cost,c.cooldown))return false;
        c.paid=true;captureResources(c);
        writeFences(c.identity.owner(),new Fences(TrajectoryReturnRules.CAST_FENCE,c.cooldown,f.quota));
        c.fences=c.identity.owner().getPersistentData().getCompound(DATA).copy();
        HighPowerNaturalRecovery.offensiveAction(c.identity.owner());return true;
    }
    private static void captureResources(Context c){var p=c.identity.owner();c.ledger=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);c.balance=Double.doubleToLongBits(c.ledger.getDouble("cp"));c.clock=c.ledger.getLong(CLOCK);c.abilityCooldown=c.identity.state().cooldowns.getOrDefault(ID,0);}
    private static boolean unchanged(Context c){
        var p=c.identity.owner();return TRANSACTIONS.get(p)==c&&c.resolving&&!c.reentered&&session(c)&&now(p)==c.started&&c.source.matches(p)
                &&c.axis.equals(p.getLookAngle().normalize())&&c.growth.compareTo(HighPowerGrowth.values(p).position())==0
                &&p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)==c.ledger&&Double.doubleToLongBits(c.ledger.getDouble("cp"))==c.balance
                &&c.ledger.getLong(CLOCK)==c.clock&&c.identity.state().cooldowns.getOrDefault(ID,0)==c.abilityCooldown
                &&c.fences.equals(p.getPersistentData().getCompound(DATA));
    }
    public static boolean emissionMatches(EmissionPermit permit){
        if(permit==null||EMISSIONS.get(permit.arrow)!=permit||!permit.context.paid||!session(permit.context)||now(permit.controller())>=permit.expires)return false;
        return permit.arrow.level()==permit.controller().level()&&permit.arrow instanceof NativeScatterArrowOwnerAccess a
                &&a.academy$scatterCachedOwner()==permit.shooter&&permit.shooter.getUUID().equals(a.academy$scatterOwnerUuid())&&a.academy$scatterLeftOwner();
    }
    public static void onArrowFlight(EmissionPermit permit,Vec3 origin,Vec3 end,Vec3 velocity){
        if(emissionMatches(permit)&&finite(origin)&&finite(end)&&finite(velocity)&&origin.distanceTo(end)<=TrajectoryReturnRules.MAX_SPEED)
            receipt(permit.context,permit.slot,FLIGHT,origin,end,velocity,COMMITTED,null);
    }
    public static void onArrowOutcome(EmissionPermit permit,boolean confirmed,int status,Vec3 origin,Vec3 point,UUID target){
        if(permit==null)return;Context c=permit.context;
        if(CASTS.contains(c)&&!c.closing&&finite(origin)&&finite(point)&&origin.distanceTo(point)<=TrajectoryReturnRules.MAX_SPEED){
            c.hurt|=confirmed;receipt(c,permit.slot,CONTACT,origin,point,permit.arrow.getDeltaMovement(),confirmed?status:REFUSED,confirmed?target:null);record(c,confirmed?"":"arrow_spent");
        }
        EMISSIONS.remove(permit.arrow,permit);
    }
    /** Null means overflow/unsupported/invalid, and never permits air-hammer fallback. */
    private static List<TrajectoryReturnArrowBridge.Snapshot> select(Context c){
        var p=c.identity.owner();if(c.end==null||!corridor(p,c.source.eye,c.end,TrajectoryReturnRules.SELECTION_RADIUS))return null;
        List<Entity> candidates=new ArrayList<>();AABB box=new AABB(c.source.eye,c.end).inflate(TrajectoryReturnRules.SELECTION_RADIUS);
        p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class),outward(box),e->e!=p,candidates,TrajectoryReturnRules.SCAN_LIMIT+1);
        if(candidates.size()>TrajectoryReturnRules.SCAN_LIMIT)return null;
        var selected=new ArrayList<TrajectoryReturnArrowBridge.Snapshot>();
        for(Entity entity:candidates){
            if(!(entity instanceof Projectile))continue;
            if(!finite(entity.position()))return null;
            if(!TrajectoryReturnRules.corridor(point(c.source.eye),point(c.axis),point(entity.position())))continue;
            if(!(entity instanceof Arrow arrow))return null;
            var snapshot=TrajectoryReturnArrowBridge.snapshot(p,arrow);if(snapshot==null||!hostile(p,snapshot.shooter()))return null;
            var block=p.serverLevel().clip(new ClipContext(c.source.eye,arrow.position(),ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
            if(block.getType()!=HitResult.Type.MISS)return null;
            selected.add(snapshot);
        }
        selected.sort(Comparator.comparingDouble((TrajectoryReturnArrowBridge.Snapshot s)->s.position().distanceToSqr(c.source.eye)).thenComparing(s->s.arrow().getUUID()));
        return List.copyOf(selected.subList(0,Math.min(TrajectoryReturnRules.MAX_ARROWS,selected.size())));
    }
    /** The first actual body and solid shape stop this finite thin ray before hostility is considered. */
    private static Target firstBody(Context c){
        var p=c.identity.owner();Vec3 start=c.source.eye,end=c.end;double radius=TrajectoryReturnRules.AIR_HAMMER_RADIUS;
        if(end==null||!TrajectoryReturnRules.within(point(start),point(end)))return null;
        int pieces=Math.max(1,(int)Math.ceil(start.distanceTo(end)/.25));Vec3 previous=start;double wall=Double.POSITIVE_INFINITY;
        for(int i=1;i<=pieces;i++){
            Vec3 next=start.lerp(end,(double)i/pieces);AABB piece=new AABB(previous,next).inflate(radius);
            if(!DisplacedProjectionWorld.volume(p,piece).isEmpty())return null;
            for(var shape:p.serverLevel().getBlockCollisions(p,piece))for(AABB box:shape.toAabbs()){
                var hit=contact(box.inflate(radius),start,end);if(hit.isPresent())wall=Math.min(wall,start.distanceToSqr(hit.get()));
            }
            var fluid=p.serverLevel().clip(new ClipContext(previous,next,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));
            if(fluid.getType()!=HitResult.Type.MISS)wall=Math.min(wall,start.distanceToSqr(fluid.getLocation()));
            previous=next;
        }
        List<Entity> candidates=new ArrayList<>();p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class),outward(new AABB(start,end).inflate(radius)),e->e!=p,candidates,TrajectoryReturnRules.SCAN_LIMIT+1);
        if(candidates.size()>TrajectoryReturnRules.SCAN_LIMIT)return null;
        List<Body> bodies=new ArrayList<>();
        for(Entity e:candidates){
            if(e.isRemoved()||e.isSpectator()||!(e.isPickable()||e.canBeCollidedWith()||e instanceof LivingEntity||e instanceof Projectile))continue;
            var hit=contact(e.getBoundingBox().inflate(radius),start,end);if(hit.isEmpty())continue;
            double distance=start.distanceToSqr(hit.get());if(distance<wall)bodies.add(new Body(e,hit.get(),distance));
        }
        bodies.sort(Comparator.comparingDouble(Body::distance).thenComparingInt(b->b.entity instanceof LivingEntity l&&hostile(p,l)?1:0).thenComparing(b->b.entity.getUUID()));
        if(bodies.isEmpty()||!(bodies.getFirst().entity instanceof LivingEntity target)||!hostile(p,target))return null;
        return Target.capture(target,bodies.getFirst().point);
    }
    private static boolean corridor(ServerPlayer p,Vec3 start,Vec3 end,double radius){
        if(!finite(start)||!finite(end))return false;
        int pieces=Math.max(1,(int)Math.ceil(start.distanceTo(end)/.25));Vec3 previous=start;
        for(int i=1;i<=pieces;i++){Vec3 next=start.lerp(end,(double)i/pieces);if(!DisplacedProjectionWorld.volume(p,new AABB(previous,next).inflate(radius)).isEmpty())return false;previous=next;}return true;
    }
    private static Optional<Vec3> contact(AABB box,Vec3 start,Vec3 end){if(inside(box,start))return Optional.of(start);var hit=box.clip(start,end).filter(v->inside(box,v));return hit.isEmpty()&&inside(box,end)?Optional.of(end):hit;}
    private static boolean inside(AABB b,Vec3 v){return v.x>=b.minX&&v.x<=b.maxX&&v.y>=b.minY&&v.y<=b.maxY&&v.z>=b.minZ&&v.z<=b.maxZ;}
    private static AABB outward(AABB b){return new AABB(Math.nextDown(b.minX),Math.nextDown(b.minY),Math.nextDown(b.minZ),Math.nextUp(b.maxX),Math.nextUp(b.maxY),Math.nextUp(b.maxZ));}
    static boolean hostile(ServerPlayer p,LivingEntity target){return DeflectionStrike.hostile(p,target)&&VanillaCombatContract.supportedClass(target);}
    private static boolean session(Context c){return !c.closing&&CASTS.contains(c)&&c.identity.owns(c.identity.owner())&&HighPowerBracerSession.authenticated(c.identity.owner(),c.input)
            &&canAct(c.identity.owner())&&mapped(c.identity.owner(),c.input.slot())&&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision();}
    private static boolean canAct(ServerPlayer p){
        if(!thread(p))return false;var s=AbilityStorage.get(p);
        return p.connection!=null&&!p.hasDisconnected()&&p.isAlive()&&!p.isRemoved()&&!p.isCreative()&&!p.isSpectator()&&!p.getAbilities().invulnerable
                &&!p.noPhysics&&!p.isPassenger()&&!p.isVehicle()&&!p.isSleeping()&&p.containerMenu==p.inventoryMenu
                &&s.activated&&s.overloadFine&&!s.interfering&&!s.consumptionInProgress()&&s.learned(ID)&&s.learned("yaw_palm")
                &&HighPowerCategories.VECMANIP.equals(s.category)&&s.level>=3&&s.level<=5&&HighPowerResources.effectiveLevel(p)>=3
                &&SkillCatalog.enabled(s.category,ID)&&HighPowerResources.ready(p)&&HighPowerCombat.unsupportedReason(p).isEmpty()
                &&!AbilityConsumption.busy(p)&&!HighPowerResources.hasReservation(p)&&finite(p.position())&&finite(p.getEyePosition())&&finite(p.getDeltaMovement())
                &&finite(p.getLookAngle())&&p.getLookAngle().lengthSqr()>.5&&Float.isFinite(p.getYRot())&&Float.isFinite(p.getXRot())&&TrajectoryReturnRules.clock(now(p));
    }
    public static boolean canBegin(ServerPlayer p){return thread(p)&&!TRANSACTIONS.containsKey(p);}
    public static boolean active(ServerPlayer p){return thread(p)&&CURRENT.containsKey(p.getUUID());}
    public static Receipt receipt(ServerPlayer p){return RECEIPTS.get(p);}
    public static List<ImpactReceipt> receipts(ServerPlayer p){Context c=CURRENT.get(p.getUUID());return c!=null&&c.identity.owner()==p?List.copyOf(c.receipts):List.of();}
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context c=CURRENT.get(p.getUUID());if(c==null||c.identity.owner()!=p||c.input.epoch()!=input.epoch()||c.input.slot()!=input.slot()||input.input()<c.input.input())return false;
        INPUTS.put(p.getUUID(),new Watermark(p,input.epoch(),input.input()));clear(p);return true;
    }
    public static void abort(ServerPlayer p){clear(p);}public static void cancelActive(ServerPlayer p){clear(p);}
    public static void clear(ServerPlayer p){if(!thread(p))return;Context tx=TRANSACTIONS.get(p);if(tx!=null)tx.reentered=true;for(Context c:new ArrayList<>(CASTS))if(c.identity.owner()==p)finish(c);TrajectoryReturnArrowBridge.invalidate(p);EMISSIONS.entrySet().removeIf(e->e.getValue().controller()==p);}
    public static void remove(ServerPlayer p){clear(p);if(p!=null){INPUTS.computeIfPresent(p.getUUID(),(id,w)->w.owner==p?null:w);RECEIPTS.remove(p);}}
    public static void clear(){for(Context c:new ArrayList<>(CASTS))finish(c);CURRENT.clear();TRANSACTIONS.clear();INPUTS.clear();RECEIPTS.clear();EMISSIONS.clear();TrajectoryReturnArrowBridge.clear();CLOCKS.clear();POST_CLOCKS.clear();}
    public static void tick(ServerPlayer p){for(Context c:new ArrayList<>(CASTS))if(c.identity.owner()==p&&!c.resolving&&(!session(c)||now(p)<c.started||now(p)-c.started>=TrajectoryReturnRules.FLIGHT_TICKS))finish(c);}
    private static boolean refuse(Context c,String reason){record(c,reason);if(!c.paid)finish(c);else sync(c.identity.owner());feedback(c.identity.owner(),reason);return false;}
    private static boolean feedback(ServerPlayer p,String reason){if(HighPowerCombat.unsupportedReason(p).isPresent())reason="unsupported";p.displayClientMessage(Component.translatable("message.academy.highpower.trajectory_return."+reason),true);return false;}
    private static void record(Context c,String reason){if(!c.identity.owner().hasDisconnected())RECEIPTS.put(c.identity.owner(),new Receipt(c.input.input(),c.identity.token(),c.paid,c.admitted,c.fallback,c.hurt,reason));}
    private static void finish(Context c){if(!CASTS.remove(c))return;c.closing=true;c.reentered=true;c.hammer=null;CURRENT.remove(c.identity.owner().getUUID(),c);for(var e:new ArrayList<>(EMISSIONS.entrySet()))if(e.getValue().context==c){TrajectoryReturnArrowBridge.retire(e.getKey());EMISSIONS.remove(e.getKey(),e.getValue());}send(c,ENDED,0,Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,REFUSED);}
    private static void receipt(Context c,int slot,int kind,Vec3 origin,Vec3 end,Vec3 velocity,int status,UUID target){c.receipts.add(new ImpactReceipt(slot,kind,origin,end,velocity,status,target));send(c,kind,slot,origin,end,velocity,status);}
    private static void sync(ServerPlayer p){AbilityStorage.save(p);AcademyNetwork.sync(p);}
    private static void send(Context c,int kind,int slot,Vec3 origin,Vec3 end,Vec3 velocity,int status){
        if(c.sequence>=128)return;var p=c.identity.owner();try{
            var packet=new Visual(p.getId(),p.getUUID(),c.input.epoch(),c.input.input(),c.identity.token(),++c.sequence,kind,slot,origin,end,velocity,status);
            c.audience.add(p);if(kind!=ENDED)for(var observer:c.identity.level().players())if(observer.distanceToSqr(p)<=96*96&&!p.isInvisibleTo(observer))c.audience.add(observer);
            for(var observer:c.audience)if(observer.connection!=null&&!observer.hasDisconnected()&&observer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(observer,packet);
        }catch(RuntimeException failure){LOG.warn("NEW431 retains server state after visual delivery failure",failure);}
    }
    private static Fences readFences(ServerPlayer p){
        var root=p.getPersistentData();if(!root.contains(DATA))return Fences.ZERO;if(!root.contains(DATA,Tag.TAG_COMPOUND))return null;var t=root.getCompound(DATA);
        if(!t.contains("version",Tag.TAG_INT)||t.getInt("version")!=1||!t.contains("request",Tag.TAG_INT)||!t.contains("cooldown",Tag.TAG_INT)||!t.contains("quota",Tag.TAG_INT_ARRAY))return null;
        int request=t.getInt("request"),cooldown=t.getInt("cooldown");int[] q=t.getIntArray("quota");if(request<0||request>4||cooldown<0||cooldown>60||q.length>4)return null;
        List<Integer> quota=new ArrayList<>();for(int n:q){if(n<1||n>20)return null;quota.add(n);}return new Fences(request,cooldown,List.copyOf(quota));
    }
    private static void writeFences(ServerPlayer p,Fences f){var t=new CompoundTag();t.putInt("version",1);t.putInt("request",f.request);t.putInt("cooldown",f.cooldown);t.putIntArray("quota",f.quota);p.getPersistentData().put(DATA,t);}
    @SubscribeEvent public static void pre(ServerTickEvent.Pre e){Integer old=CLOCKS.put(e.getServer(),e.getServer().getTickCount());if(old!=null&&old==e.getServer().getTickCount())return;for(var p:e.getServer().getPlayerList().getPlayers())if(p.getPersistentData().contains(DATA)){var f=readFences(p);if(f!=null&&!f.equals(Fences.ZERO))writeFences(p,f.advance());}}
    @SubscribeEvent public static void post(ServerTickEvent.Post e){Integer old=POST_CLOCKS.put(e.getServer(),e.getServer().getTickCount());if(old!=null&&old==e.getServer().getTickCount())return;for(Context c:new ArrayList<>(CASTS))if(c.identity.owner().server==e.getServer())tick(c.identity.owner());TrajectoryReturnArrowBridge.prune(e.getServer());EMISSIONS.entrySet().removeIf(x->x.getValue().controller().server==e.getServer()&&(!session(x.getValue().context)||now(x.getValue().controller())>=x.getValue().expiresAt()||x.getKey().isRemoved()));}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer p){remove(old);if(old.getPersistentData().contains(DATA))p.getPersistentData().put(DATA,old.getPersistentData().get(DATA).copy());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
    private static boolean mapped(ServerPlayer p,int slot){return thread(p)&&slot>=0&&slot<4&&ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));}
    private static boolean thread(ServerPlayer p){return p!=null&&p.server!=null&&p.server.isSameThread();}
    private static long now(ServerPlayer p){return Integer.toUnsignedLong(p.server.getTickCount());}
    private static BigDecimal health(LivingEntity t){return HighPowerCombat.enabled(t)?new BigDecimal(HighPowerCombat.healthText(t)):BigDecimal.valueOf(t.getHealth());}
    private static boolean finite(Vec3 v){return v!=null&&point(v).finite();}
    private static TrajectoryReturnRules.Point point(Vec3 v){return v==null?null:new TrajectoryReturnRules.Point(v.x,v.y,v.z);}
    private static Vec3 vector(TrajectoryReturnRules.Point p){return new Vec3(p.x(),p.y(),p.z());}
    private static void vec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 vec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int kind,int slot,Vec3 origin,Vec3 end,Vec3 velocity,int status) implements CustomPacketPayload {
        public Visual {
            if(owner<0||ownerUuid==null||epoch<=0||input<=0||token<=0||sequence<=0||sequence>128||kind<REVERSE||kind>ENDED||slot<0||slot>=4
                    ||!TrajectoryReturnRules.within(point(origin),point(end))||!finite(velocity)||velocity.lengthSqr()>64||status<REFUSED||status>COMMITTED
                    ||kind==REVERSE&&(!origin.equals(end)||status!=COMMITTED)||kind==FLIGHT&&(origin.distanceToSqr(end)>64||status!=COMMITTED)
                    ||kind==CONTACT&&(origin.distanceToSqr(end)>64||status==COMMITTED)||kind==AIR_HAMMER&&(!velocity.equals(Vec3.ZERO)||status==COMMITTED)
                    ||kind==ENDED&&(slot!=0||status!=REFUSED||!origin.equals(Vec3.ZERO)||!end.equals(Vec3.ZERO)||!velocity.equals(Vec3.ZERO)))throw new IllegalArgumentException("Invalid bounded NEW431 visual receipt");
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","trajectory_return_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{b.writeInt(v.owner);b.writeUUID(v.ownerUuid);b.writeLong(v.epoch);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.sequence);b.writeByte(v.kind);b.writeByte(v.slot);vec(b,v.origin);vec(b,v.end);vec(b,v.velocity);b.writeByte(v.status);},b->new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readUnsignedByte(),b.readUnsignedByte(),vec(b),vec(b),vec(b),b.readUnsignedByte()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD) public static final class Network {@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}}
}
