package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.RemoteScatterRelayGeometry;
import cn.academy.port.highpower.core.RemoteScatterRelayGeometry.Node;
import cn.academy.port.highpower.core.RemoteScatterRelayGeometry.Vector;
import cn.academy.port.highpower.core.RemoteScatterRelayRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
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
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW253. Fixed finite plates, real native contacts, and one opaque whole-path damage permit. */
@EventBusSubscriber(modid="academy")
public final class RemoteScatterRelay {
    public static final String ID="remote_scatter_relay";
    public static final int MIN_LEVEL=5,MAX_LEVEL=5;
    public static final int ACTIVE=0,EDIT=1,FIRE=2,ENDED=3;
    public static final int PREPARED=0,MISS=1,BLOCKED=2,REFUSED=3,DAMAGED=4,ABSORBED=5,DEGENERATE=6,ENDPOINT=7;
    public static final int TOO_FAR=8,SEGMENT_TOO_FAR=9,TOO_CLOSE=10,FULL=11,BODY_BLOCKED=12,NO_SPACE=13,UNLOADED=14,MEASURING=15,CLEARING=16;
    /** Default only for an unobstructed air selection; all real limits stay 32m. */
    private static final double AIR_SELECTION_DISTANCE=30;
    private static final String DATA="academy:remote_scatter_relay",INPUT_AT="inputAt",ATTEMPT_AT="attemptAt",EMPTY_AT="emptyAt",MASTERY_AT="masteryAt",PREVIEW_AT="previewAt";
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Set<ServerPlayer> PROCESSING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static long nextToken;
    private static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("academy.remote_scatter_relay");
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input) {}
    private record Hold(HighPowerInput.Input input,long started,boolean editing,int index,Vec3 center,int selectionStatus) {}
    private record NativeHit(Vec3 point,Entity target,int status,boolean permitted,boolean contact) {}
    private record Plan(List<SegmentVisual> path,int crossings,LivingEntity target,Vec3 targetPosition,AABB targetBounds,
                        Vec3 hand,Vec3 eye,Vec3 ownerPosition,Vec3 look,List<Node> plates,Vec3 terminal,int status) {
        Plan {path=List.copyOf(path);plates=List.copyOf(plates);}
        Vec3 finalOrigin(){return path.getLast().start;}
        Vec3 endpoint(){return path.getLast().end;}
    }
    public record Receipt(List<SegmentVisual> path,int crossings,int status,UUID victim,long input) {
        public Receipt {path=List.copyOf(path);}
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final int preset;final long revision;
        final Set<ServerPlayer> audience=new LinkedHashSet<>();
        HighPowerInput.Input input;Hold hold;List<Node> plates=List.of();Vec3 terminal;
        long placedAt=-1,lastTick=-1,lastVisual=-1,sequence;
        boolean resolving;EmissionPermit emitting;Receipt receipt;
        Context(ServerPlayer p,HighPowerInput.Input input){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));
            this.input=input;preset=identity.state().presets.current();revision=identity.state().presets.revision();
        }
    }
    /** Only this controller can allocate one complete paid ray. The bridge cannot add a target or leg. */
    public static final class EmissionPermit {
        private final Context context;private final Plan plan;private final BigDecimal damage;
        private final HighPowerInput.Input input;private final long tick;
        private final CompoundTag ledger;private final double cpBefore,cost;private final int cooldown;
        private final BigDecimal q;private boolean consumed,paymentAttempted,paid;
        private EmissionPermit(Context c,Plan plan,BigDecimal damage){
            context=c;this.plan=plan;this.damage=damage;input=c.input;tick=now(player());
            ledger=player().getPersistentData().getCompound(HighPowerResources.DATA_KEY);cpBefore=HighPowerResources.cp(player());
            q=HighPowerGrowth.values(player()).position();cost=RemoteScatterRelayRules.shotCost(q);cooldown=RemoteScatterRelayRules.cooldown(q);
        }
        ServerPlayer player(){return context.identity.owner();}
        LivingEntity target(){return plan.target;}
        BigDecimal damage(){return damage;}
        Vec3 finalOrigin(){return plan.finalOrigin();}
        Vec3 endpoint(){return plan.endpoint();}
        boolean consume(){if(consumed||!valid())return false;consumed=true;return true;}
        boolean commitPayment(){
            if(paymentAttempted)return false;
            if(!consumed||!paymentReady())return false;
            paymentAttempted=true;
            if(!HighPowerResources.trySpend(player(),ID,cost,cooldown))return false;
            paid=true;return true;
        }
        boolean paid(){return paid;}
        /** Local recheck after callback-capable route validation; never scans the world. */
        boolean paymentReady(){
            ServerPlayer p=player();return !paymentAttempted&&!paid&&context.emitting==this&&context.resolving
                    &&owned(p)==context&&context.identity.owns(p)&&localValid(context)&&now(p)==tick&&context.input.equals(input)
                    &&context.plates.equals(plan.plates)&&Objects.equals(context.terminal,plan.terminal)
                    &&p.position().equals(plan.ownerPosition)&&p.getEyePosition().equals(plan.eye)&&p.getLookAngle().equals(plan.look)
                    &&Objects.equals(hand(p),plan.hand)&&targetLocalPermitted(p,plan.target)
                    &&plan.target.position().equals(plan.targetPosition)&&plan.target.getBoundingBox().equals(plan.targetBounds)
                    &&p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)==ledger
                    &&Double.doubleToLongBits(HighPowerResources.cp(p))==Double.doubleToLongBits(cpBefore)
                    &&HighPowerGrowth.values(p).position().compareTo(q)==0&&AbilityStorage.get(p).canUse(ID);
        }
        boolean valid(){
            ServerPlayer p=player();
            if(context.emitting!=this||owned(p)!=context||!context.resolving||!RemoteScatterRelay.valid(context)
                    ||now(p)!=tick||p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)!=ledger
                    ||Double.doubleToLongBits(HighPowerResources.cp(p))!=Double.doubleToLongBits(paid?Math.max(0,cpBefore-cost):cpBefore)
                    ||HighPowerGrowth.values(p).position().compareTo(q)!=0||!context.input.equals(input)||!context.plates.equals(plan.plates)||!Objects.equals(context.terminal,plan.terminal)
                    ||!p.position().equals(plan.ownerPosition)||!p.getEyePosition().equals(plan.eye)
                    ||!p.getLookAngle().equals(plan.look)||!Objects.equals(hand(p),plan.hand)
                    ||!harmPermitted(p,plan.target)||!plan.target.position().equals(plan.targetPosition)
                    ||!plan.target.getBoundingBox().equals(plan.targetBounds))return false;
            Plan current=trace(p,context.plates,context.terminal);
            return current.target==plan.target&&current.crossings==plan.crossings&&current.path.equals(plan.path);
        }
    }
    private RemoteScatterRelay() {}

    /** Crouch is captured once. Subsequent key or crouch changes cannot change this press's meaning. */
    public static boolean start(ServerPlayer p,String wire){
        if(PROCESSING.contains(p))return false;
        Context existing=owned(p);if(existing!=null&&existing.resolving)return false;
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark prior=INPUTS.get(p.getUUID());if(prior!=null&&prior.epoch==input.epoch()&&input.input()<=prior.input)return false;
        if(existing!=null&&!valid(existing)){finish(existing);existing=null;}
        if(existing!=null&&existing.hold!=null)return false;
        INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        if(!canAct(p)||!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))
                ||!fenceReady(p,INPUT_AT,1)||nextToken==Long.MAX_VALUE)return false;
        if(existing!=null&&(existing.input.epoch()!=input.epoch()||existing.input.slot()!=input.slot()))return false;
        stamp(p,INPUT_AT);
        Context c=existing;
        if(c==null){Context foreign=CONTEXTS.get(p.getUUID());if(foreign!=null)finish(foreign);c=new Context(p,input);CONTEXTS.put(p.getUUID(),c);}
        c.input=input;
        int index=-1,status=PREPARED;Vec3 center=null;boolean editing=p.isShiftKeyDown();
        if(editing){Selection selected=select(p,c.plates);index=selected.index;center=selected.center;status=selected.status;}
        c.hold=new Hold(input,now(p),editing,index,center,status);preview(c,true);return true;
    }
    /** Release retires only the press; the paid array retains its original identity and expiry. */
    public static boolean release(ServerPlayer p,String wire){
        Context c=owned(p);HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(c==null||c.resolving||c.hold==null||!HighPowerBracerSession.authenticated(p,input)||!c.hold.input.equals(input))return false;
        Hold hold=c.hold;
        if(!valid(c)||RemoteScatterRelayRules.expired(hold.started,now(p),RemoteScatterRelayRules.HOLD_LIMIT)){finish(c);return false;}
        c.hold=null;c.resolving=true;PROCESSING.add(p);
        try{
            if(now(p)-hold.started>=RemoteScatterRelayRules.RECALL_TICKS){if(hold.editing)finish(c);else send(c,ACTIVE,MEASURING,null,List.of());return true;}
            if(hold.editing)return place(c,hold);
            if(c.terminal==null)return selectTerminal(c);
            return fire(c);
        }finally{PROCESSING.remove(p);c.resolving=false;retireEmpty(c);}
    }
    public static boolean abort(ServerPlayer p,String wire){
        Context c=owned(p);HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input))return false;
        boolean owns=c!=null&&c.input.epoch()==input.epoch()&&c.input.slot()==input.slot()&&input.input()>=c.input.input();
        if(!owns&&!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())))return false;
        Watermark old=INPUTS.get(p.getUUID());boolean retired=old==null||old.epoch!=input.epoch()||input.input()>old.input;
        // A canceled pending physical edge cannot become a delayed start. It buys no input tick.
        if(retired)INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        if(owns)finish(c);return owns||retired;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c);}
    public static void remove(ServerPlayer p){if(thread(p)){abort(p);INPUTS.remove(p.getUUID());}}
    public static void clear(){for(Context c:new ArrayList<>(CONTEXTS.values()))finish(c);INPUTS.clear();PROCESSING.clear();}
    public static boolean active(ServerPlayer p){Context c=owned(p);if(c!=null&&!valid(c)){finish(c);return false;}return c!=null;}
    public static boolean holding(ServerPlayer p){Context c=owned(p);return c!=null&&c.hold!=null&&valid(c);}
    public static List<PlateVisual> plates(ServerPlayer p){Context c=owned(p);return c==null?List.of():plateVisuals(c.plates);}
    public static long placedAt(ServerPlayer p){Context c=owned(p);return c==null?-1:c.placedAt;}
    public static int remainingTicks(ServerPlayer p){Context c=owned(p);return c==null||c.placedAt<0?0:(int)Math.max(0,RemoteScatterRelayRules.LIFETIME-(now(p)-c.placedAt));}
    public static Receipt receipt(ServerPlayer p){Context c=owned(p);return c==null?null:c.receipt;}

    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.resolving)return;
        if(!valid(c)||now(p)<c.lastTick){finish(c);return;}
        if(now(p)==c.lastTick)return;c.lastTick=now(p);
        if(c.hold!=null&&RemoteScatterRelayRules.expired(c.hold.started,now(p),RemoteScatterRelayRules.HOLD_LIMIT)){
            c.hold=null;retireEmpty(c);if(owned(p)!=c)return;
        }
        preview(c,false);
    }
    private record Selection(int index,Vec3 center,int status) {}
    private record Candidate(Node node,int status) {}
    private record Terminal(Vec3 point,int status) {}
    private static Selection selectionFailure(int status){return new Selection(-1,null,status);}
    private static Selection select(ServerPlayer p,List<Node> plates){
        if(plates.size()>=4)return selectionFailure(FULL);
        Vec3 eye=p.getEyePosition(),direction=p.getLookAngle().normalize();
        NativeHit hit=nativeTrace(p,eye,endpoint(eye,direction,32));
        if(!hit.permitted)return selectionFailure(hit.status);
        if(hit.target!=null)return selectionFailure(BODY_BLOCKED);
        // Keep surface selection and its existing full-plate clearance offset unchanged.
        double distance=hit.contact?eye.distanceTo(hit.point)-(Math.hypot(.45,.65)+.06):AIR_SELECTION_DISTANCE;
        if(!(distance>.8))return selectionFailure(TOO_CLOSE);
        // Aimed down at the ground: rest the plate on the contact (raised by its enclosing radius) instead of backing off along
        // a shallow ray, which left it half buried, failing clearance, so every ground placement silently did nothing.
        Vec3 center=hit.contact&&direction.y<-.1?hit.point.add(0,Math.hypot(.45,.65)+.06,0):endpoint(eye,direction,Math.min(32,distance));
        Vec3 previous=plates.isEmpty()?hand(p):vec(plates.getLast().center());
        if(previous.distanceToSqr(center)>1024)return selectionFailure(SEGMENT_TOO_FAR);
        if(previous.distanceToSqr(center)<1e-12)return selectionFailure(TOO_CLOSE);
        int status=visibleCenterStatus(p,center);
        return status==PREPARED?new Selection(plates.size(),center,PREPARED):selectionFailure(status);
    }
    private static Candidate candidate(Context c,Hold hold){
        ServerPlayer p=c.identity.owner();
        if(hold.center==null)return new Candidate(null,hold.selectionStatus);
        if(hold.index!=c.plates.size()||hold.index<0||hold.index>=4)return new Candidate(null,DEGENERATE);
        int status=visibleCenterStatus(p,hold.center);
        if(status!=PREPARED)return new Candidate(null,status);
        Vec3 previous=c.plates.isEmpty()?hand(p):vec(c.plates.getLast().center());
        if(previous.distanceToSqr(hold.center)>1024)return new Candidate(null,SEGMENT_TOO_FAR);
        status=clearStatus(p,previous,hold.center);
        if(status!=PREPARED)return new Candidate(null,status);
        Vec3 normal=p.getEyePosition().subtract(hold.center).normalize();
        try{Node node=new Node(hold.index,vector(hold.center),vector(normal));status=plateStatus(p,node);return new Candidate(status==PREPARED?node:null,status);}
        catch(IllegalArgumentException invalid){return new Candidate(null,DEGENERATE);}
    }
    private static boolean place(Context c,Hold hold){
        if(ScatterLocatorPlate.active(c.identity.owner()))return false;
        ServerPlayer p=c.identity.owner();Vec3 position=p.position(),eye=p.getEyePosition(),look=p.getLookAngle();
        CompoundTag ledger=p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);double cp=HighPowerResources.cp(p);
        BigDecimal q=HighPowerGrowth.values(p).position();Candidate selected=candidate(c,hold);Node node=selected.node;
        if(node==null){send(c,ACTIVE,selected.status,null,List.of());return false;}
        if(!valid(c)||!AbilityStorage.get(p).canUse(ID))return false;
        List<Node> updated=new ArrayList<>(c.plates);updated.add(node);
        // No callbacks occur between the complete validation and closed CP debit/publication.
        double cost=RemoteScatterRelayRules.placementCost(q);
        if(!localValid(c)||!position.equals(p.position())||!eye.equals(p.getEyePosition())||!look.equals(p.getLookAngle())
                ||p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)!=ledger
                ||Double.doubleToLongBits(cp)!=Double.doubleToLongBits(HighPowerResources.cp(p))
                ||q.compareTo(HighPowerGrowth.values(p).position())!=0)return false;
        if(!HighPowerResources.trySpend(p,ID,cost,1))return false;
        c.plates=List.copyOf(updated);c.terminal=null;if(c.placedAt<0)c.placedAt=now(p);
        send(c,ACTIVE,PREPARED,null,List.of());return true;
    }
    private static Terminal candidateTerminal(Context c){
        ServerPlayer p=c.identity.owner();if(c.plates.isEmpty())return new Terminal(null,DEGENERATE);
        Vec3 position=p.position(),look=p.getLookAngle(),eye=p.getEyePosition(),end=endpoint(eye,look.normalize(),32);
        NativeHit visible=nativeTrace(p,eye,end);
        if(!visible.permitted)return new Terminal(null,visible.status);
        if(!localValid(c)||!position.equals(p.position())||!eye.equals(p.getEyePosition())||!look.equals(p.getLookAngle()))return new Terminal(null,REFUSED);
        Vec3 terminal=visible.point,previous=vec(c.plates.getLast().center());
        if(!finite(terminal))return new Terminal(null,DEGENERATE);
        if(previous.distanceToSqr(terminal)>1024)return new Terminal(null,SEGMENT_TOO_FAR);
        if(previous.distanceToSqr(terminal)<1e-12)return new Terminal(null,TOO_CLOSE);
        return new Terminal(terminal,PREPARED);
    }
    private static boolean selectTerminal(Context c){
        Terminal selected=candidateTerminal(c);if(selected.point==null){send(c,ACTIVE,selected.status,null,List.of());return false;}
        c.terminal=selected.point;preview(c,true);return true;
    }
    private static boolean fire(Context c){
        ServerPlayer p=c.identity.owner();
        if(!AbilityStorage.get(p).canUse(ID)||!fenceReady(p,ATTEMPT_AT,1)||!fenceReady(p,EMPTY_AT,4))return false;
        stamp(p,ATTEMPT_AT);
        Plan plan=trace(p,c.plates,c.terminal);
        if(plan.target==null||plan.crossings!=c.plates.size()||!harmPermitted(p,plan.target)){
            stamp(p,EMPTY_AT);send(c,ACTIVE,plan.status,null,plan.path);return false;
        }
        var growth=HighPowerGrowth.values(p);BigDecimal amount=RemoteScatterRelayRules.damage(growth.damage());
        EmissionPermit permit=new EmissionPermit(c,plan,amount);c.emitting=permit;
        BigDecimal hp=health(plan.target),shield=HighPowerCombat.shield(plan.target);float absorption=plan.target.getAbsorptionAmount();boolean accepted;
        try{accepted=HighPowerCombat.attackRemoteScatterRelay(permit);}
        catch(RuntimeException|LinkageError unknown){
            c.receipt=new Receipt(plan.path,plan.crossings,REFUSED,null,c.input.input());
            LOGGER.warn("NEW253 native transaction refused after exception; payment committed={}, no refund or replay",permit.paid,unknown);
            finish(c);return false;
        }finally{c.emitting=null;}
        int status=permit.paid&&accepted?MISS:REFUSED;UUID victim=null;
        if(accepted&&health(plan.target).compareTo(hp)<0){status=DAMAGED;victim=plan.target.getUUID();}
        else if(accepted&&(HighPowerCombat.shield(plan.target).compareTo(shield)<0||plan.target.getAbsorptionAmount()<absorption)){status=ABSORBED;victim=plan.target.getUUID();}
        c.receipt=new Receipt(plan.path,plan.crossings,status,victim,c.input.input());
        if(owned(p)==c)send(c,permit.paid&&status!=REFUSED?FIRE:ACTIVE,status,null,permit.paid&&status!=REFUSED?plan.path:List.of());
        if(owned(p)==c&&!valid(c))finish(c);
        return permit.paid;
    }
    private static Plan trace(ServerPlayer p,List<Node> plates,Vec3 terminal){
        Vec3 hand=hand(p),eye=p.getEyePosition(),owner=p.position(),look=p.getLookAngle();
        List<SegmentVisual> path=new ArrayList<>(5);int crossed=0;
        if(plates.isEmpty()||terminal==null||!finite(hand))return plan(path,0,null,hand,eye,owner,look,plates,terminal,REFUSED);
        int eyePath=clearStatus(p,eye,hand);
        if(eyePath!=PREPARED)return plan(path,0,null,hand,eye,owner,look,plates,terminal,eyePath);
        if(RemoteScatterRelayGeometry.route(vector(hand),plates,vector(terminal)).isEmpty()){
            Vec3 previous=hand;
            for(int i=0;i<=plates.size();i++){Vec3 end=i<plates.size()?vec(plates.get(i).center()):terminal;
                if(!RemoteScatterRelayGeometry.withinRange(vector(previous),vector(end)))return plan(path,0,null,hand,eye,owner,look,plates,terminal,SEGMENT_TOO_FAR);
                previous=end;
            }
            return plan(path,0,null,hand,eye,owner,look,plates,terminal,REFUSED);
        }
        for(Node node:plates){int availability=plateStatus(p,node);if(availability!=PREPARED)return plan(path,0,null,hand,eye,owner,look,plates,terminal,availability);}
        Vec3 start=hand;
        for(int i=0;i<=plates.size();i++){
            Vec3 end=i<plates.size()?vec(plates.get(i).center()):terminal;
            NativeHit hit=nativeTrace(p,start,end,i>0);
            if(start.distanceToSqr(hit.point)==0)return plan(path,crossed,null,hand,eye,owner,look,plates,terminal,!hit.permitted?hit.status:hit.target!=null?BODY_BLOCKED:BLOCKED);
            path.add(new SegmentVisual(start,hit.point));
            if(!hit.permitted)return plan(path,crossed,null,hand,eye,owner,look,plates,terminal,hit.status);
            if(i<plates.size()){
                if(hit.contact)return plan(path,crossed,null,hand,eye,owner,look,plates,terminal,hit.target!=null?BODY_BLOCKED:BLOCKED);
                crossed++;start=end;continue;
            }
            LivingEntity victim=hit.target instanceof LivingEntity living&&harmPermitted(p,living)?living:null;
            return plan(path,crossed,victim,hand,eye,owner,look,plates,terminal,hit.contact?hit.status:ENDPOINT);
        }
        return plan(path,crossed,null,hand,eye,owner,look,plates,terminal,REFUSED);
    }
    private static Plan plan(List<SegmentVisual> path,int crossings,LivingEntity target,Vec3 hand,Vec3 eye,Vec3 owner,Vec3 look,List<Node> plates,Vec3 terminal,int status){
        return new Plan(path,crossings,target,target==null?null:target.position(),target==null?null:target.getBoundingBox(),hand,eye,owner,look,plates,terminal,status);
    }
    /** Queries loaded native shapes only. Each prefix is checked before its shapes are inspected. */
    private static NativeHit nativeTrace(ServerPlayer p,Vec3 origin,Vec3 endpoint){
        return nativeTrace(p,origin,endpoint,false);
    }
    private static NativeHit nativeTrace(ServerPlayer p,Vec3 origin,Vec3 endpoint,boolean includeCaster){
        if(!finite(origin)||!finite(endpoint)||origin.distanceToSqr(endpoint)>RemoteScatterRelayRules.RANGE*RemoteScatterRelayRules.RANGE+1e-9)return new NativeHit(origin,null,REFUSED,false,false);
        int pieces=Math.max(1,(int)Math.ceil(origin.distanceTo(endpoint)/.25));Vec3 previous=origin;
        Set<Entity> scanned=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=1;i<=pieces;i++){
            Vec3 next=origin.lerp(endpoint,(double)i/pieces);AABB box=new AABB(previous,next).inflate(RemoteScatterRelayRules.RADIUS);
            // Expand candidate lookup by one representable coordinate only. Native shape lookup
            // otherwise omits a solid tangent to this closed swept boundary. Clip radius, ray
            // origin and terminal point stay exact; validate the lookup volume before any read.
            AABB query=outward(box);
            int availability=volumeStatus(p,query);
            if(availability!=PREPARED)return new NativeHit(previous,null,availability,false,false);
            // The same checked short prefix bounds entity reads as well as solid reads. A body
            // terminates before permission/chunk checks for distant, unused parts of the budget.
            Vec3 end=next;Entity target=null;double nearest=origin.distanceToSqr(end);
            List<Entity> candidates=new ArrayList<>();
            p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class),query,
                    e->(e!=p||includeCaster)&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()&&e.canBeHitByProjectile(),candidates,RemoteScatterRelayRules.SCAN_LIMIT+1);
            scanned.addAll(candidates);
            if(scanned.size()>RemoteScatterRelayRules.SCAN_LIMIT)return new NativeHit(previous,null,REFUSED,false,false);
            for(Entity entity:candidates){
                Optional<Vec3> hit=clipInclusive(entity.getBoundingBox().inflate(RemoteScatterRelayRules.RADIUS),origin,next);
                if(hit.isPresent()&&(origin.distanceToSqr(hit.get())<nearest||origin.distanceToSqr(hit.get())==nearest&&(target==null||entity.getId()<target.getId()))){
                    end=hit.get();nearest=origin.distanceToSqr(end);target=entity;
                }
            }
            Vec3 solidPoint=null;
            for(var shape:p.serverLevel().getBlockCollisions(p,query))for(AABB solid:shape.toAabbs()){
                AABB bounds=solid.inflate(RemoteScatterRelayRules.RADIUS);Optional<Vec3> hit=clipInclusive(bounds,origin,end);
                if(hit.isPresent()&&origin.distanceToSqr(hit.get())<=nearest){nearest=origin.distanceToSqr(hit.get());solidPoint=hit.get();}
            }
            if(solidPoint!=null)return new NativeHit(solidPoint,null,BLOCKED,true,true);
            if(target!=null)return new NativeHit(end,target,REFUSED,true,true);
            previous=next;
        }
        return new NativeHit(endpoint,null,MISS,true,false);
    }
    private static AABB outward(AABB box){return new AABB(Math.nextDown(box.minX),Math.nextDown(box.minY),Math.nextDown(box.minZ),Math.nextUp(box.maxX),Math.nextUp(box.maxY),Math.nextUp(box.maxZ));}
    /** Vanilla's open terminal clip is insufficient for the required body/solid-at-mirror tie. */
    private static Optional<Vec3> clipInclusive(AABB box,Vec3 start,Vec3 end){
        double near=0,far=1;double[] origins={start.x,start.y,start.z},delta={end.x-start.x,end.y-start.y,end.z-start.z};
        double[] min={box.minX,box.minY,box.minZ},max={box.maxX,box.maxY,box.maxZ};
        for(int i=0;i<3;i++){
            if(delta[i]==0){if(origins[i]<min[i]||origins[i]>max[i])return Optional.empty();continue;}
            double a=(min[i]-origins[i])/delta[i],b=(max[i]-origins[i])/delta[i];
            near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));if(near>far)return Optional.empty();
        }
        return near>=0&&near<=1?Optional.of(start.lerp(end,near)):Optional.empty();
    }
    private static int visibleCenterStatus(ServerPlayer p,Vec3 center){
        if(!finite(center))return DEGENERATE;
        if(p.getEyePosition().distanceToSqr(center)>RemoteScatterRelayRules.PLACE_RANGE*RemoteScatterRelayRules.PLACE_RANGE)return TOO_FAR;
        return clearStatus(p,p.getEyePosition(),center);
    }
    private static int clearStatus(ServerPlayer p,Vec3 a,Vec3 b){
        NativeHit hit=nativeTrace(p,a,b);
        return !hit.permitted?hit.status:hit.contact?(hit.target!=null?BODY_BLOCKED:BLOCKED):PREPARED;
    }
    private static boolean clear(ServerPlayer p,Vec3 a,Vec3 b){return clearStatus(p,a,b)==PREPARED;}
    private static int plateStatus(ServerPlayer p,Node plate){
        var bounds=RemoteScatterRelayGeometry.volumeBounds(plate);AABB box=new AABB(vec(bounds.min()),vec(bounds.max()));
        int availability=volumeStatus(p,box);
        return availability!=PREPARED?availability:p.serverLevel().getBlockCollisions(p,box).iterator().hasNext()?NO_SPACE:PREPARED;
    }
    private static boolean plateClear(ServerPlayer p,Node plate){return plateStatus(p,plate)==PREPARED;}
    private static boolean permittedVolume(ServerPlayer p,AABB box){return volumeStatus(p,box)==PREPARED;}
    /** Return only a reason established by this very check; no second diagnostic world scan. */
    private static int volumeStatus(ServerPlayer p,AABB box){
        ServerLevel world=p.serverLevel();
        if(!finite(new Vec3(box.minX,box.minY,box.minZ))||!finite(new Vec3(box.maxX,box.maxY,box.maxZ))
                ||box.getXsize()>8||box.getYsize()>8||box.getZsize()>8||box.minY<world.getMinBuildHeight()||box.maxY>=world.getMaxBuildHeight()
                ||!world.getWorldBorder().isWithinBounds(box))return REFUSED;
        for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++)for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++)if(!world.hasChunk(x,z))return UNLOADED;
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))if(!world.mayInteract(p,pos))return REFUSED;
        return PREPARED;
    }
    private static boolean canAct(ServerPlayer p){
        if(!thread(p))return false;var state=AbilityStorage.get(p);
        return p.connection!=null&&!p.hasDisconnected()&&p.containerMenu==p.inventoryMenu&&HighPowerResources.ready(p)
                &&!p.isSpectator()&&!p.getAbilities().instabuild&&!p.getAbilities().invulnerable&&state.activated&&state.overloadFine&&!state.interfering
                &&state.learned(ID)&&state.learned(FoldedMirrorArray.ID)&&state.category.equals("meltdowner")&&state.level>=MIN_LEVEL&&state.level<=MAX_LEVEL
                &&HighPowerResources.effectiveLevel(p)>=MIN_LEVEL&&SkillCatalog.enabled("meltdowner",ID)
                &&HighPowerCombat.unsupportedReason(p).isEmpty()&&!AbilityConsumption.busy(p)&&!p.noPhysics&&!p.isPassenger()&&!p.isSleeping()
                &&finite(p.position())&&finite(p.getDeltaMovement())&&finite(p.getLookAngle())&&p.getLookAngle().lengthSqr()>.5
                &&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())&&RemoteScatterRelayRules.clock(now(p))&&permittedVolume(p,p.getBoundingBox());
    }
    /** Callback-free identity/mode check used after native permission callbacks. */
    private static boolean localValid(Context c){
        ServerPlayer p=c.identity.owner();var state=AbilityStorage.get(p);
        return thread(p)&&owned(p)==c&&c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)
                &&p.connection!=null&&!p.hasDisconnected()&&p.containerMenu==p.inventoryMenu
                &&!p.isSpectator()&&!p.getAbilities().instabuild&&!p.getAbilities().invulnerable
                &&p.isAlive()&&!p.isRemoved()&&state.activated&&state.overloadFine&&!state.interfering
                &&state.category.equals("meltdowner")&&state.level==5&&state.learned(ID)&&state.learned(FoldedMirrorArray.ID)
                &&c.preset==state.presets.current()&&c.revision==state.presets.revision()
                &&ID.equals(state.presets.currentSkill(c.input.slot()))&&!p.noPhysics&&!p.isPassenger()&&!p.isSleeping()
                &&!AbilityConsumption.busy(p)&&HighPowerResources.ready(p)&&HighPowerResources.effectiveLevel(p)>=5
                &&SkillCatalog.enabled("meltdowner",ID)&&HighPowerCombat.unsupportedReason(p).isEmpty()
                &&(c.placedAt<0||!RemoteScatterRelayRules.expired(c.placedAt,now(p)));
    }
    private static boolean valid(Context c){
        ServerPlayer p=c.identity.owner();return c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canAct(p)
                &&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision()
                &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))
                &&(c.placedAt<0||!RemoteScatterRelayRules.expired(c.placedAt,now(p),RemoteScatterRelayRules.LIFETIME));
    }
    private static boolean harmPermitted(ServerPlayer p,LivingEntity target){
        return targetLocalPermitted(p,target)&&permittedVolume(p,target.getBoundingBox())&&targetLocalPermitted(p,target);
    }
    /** No permission callbacks or world scans after the final route check. */
    private static boolean targetLocalPermitted(ServerPlayer p,LivingEntity target){
        ServerLevel world=p.serverLevel();
        if(target==null||target==p||target.level()!=world||!target.isAlive()||target.isRemoved()||target.isSpectator()
                ||world.getEntity(target.getUUID())!=target||world.getEntity(target.getId())!=target||!HighPowerCombat.supported(target)
                ||HighPowerCombat.unsupportedReason(target).isPresent()||target.isInvulnerable()||target.getPersistentData().getBoolean("academy:protected")
                ||p.isAlliedTo(target)||target.isAlliedTo(p))return false;
        return !(target instanceof Player)||target.getClass()==ServerPlayer.class&&target instanceof ServerPlayer other
                &&other.connection!=null&&!other.hasDisconnected()&&!other.getAbilities().invulnerable
                &&AcademyConfig.ATTACK_PLAYERS.get()&&p.server.isPvpAllowed()&&p.canHarmPlayer(other)&&other.canHarmPlayer(p);
    }
    public static Vec3 hand(ServerPlayer p){return DualPoint.hand(p,0);}
    private static Vec3 endpoint(Vec3 origin,Vec3 direction,double distance){
        Vec3 end=origin.add(direction.scale(distance));
        for(int i=0;i<8&&origin.distanceToSqr(end)>distance*distance;i++)end=new Vec3(Math.nextAfter(end.x,origin.x),Math.nextAfter(end.y,origin.y),Math.nextAfter(end.z,origin.z));
        return end;
    }
    private static boolean fenceReady(ServerPlayer p,String key,int interval){
        var root=p.getPersistentData();if(root.contains(DATA)&&!root.contains(DATA,Tag.TAG_COMPOUND))return false;
        CompoundTag data=root.getCompound(DATA);return !data.contains(key)||data.contains(key,Tag.TAG_LONG)&&RemoteScatterRelayRules.ready(data.getLong(key),now(p),interval);
    }
    private static void stamp(ServerPlayer p,String key){CompoundTag data=p.getPersistentData().getCompound(DATA);data.putLong(key,now(p));p.getPersistentData().put(DATA,data);}
    private static BigDecimal health(LivingEntity target){return HighPowerCombat.enabled(target)?new BigDecimal(HighPowerCombat.healthText(target)):BigDecimal.valueOf(target.getHealth());}
    private static void preview(Context c,boolean force){
        ServerPlayer p=c.identity.owner();if(!force&&!RemoteScatterRelayRules.ready(c.lastVisual,now(p),4))return;
        boolean editing=c.hold!=null&&c.hold.editing;
        Candidate selected=editing?candidate(c,c.hold):null;Node ghost=selected==null?null:selected.node;
        Terminal pending=c.terminal==null&&c.hold!=null&&!editing?candidateTerminal(c):null;
        Vec3 measured=c.terminal!=null?c.terminal:pending==null?null:pending.point;
        Plan plan=measured==null?null:trace(p,c.plates,measured);
        int status=plan==null?PREPARED:plan.target!=null||plan.status==ENDPOINT?(c.terminal==null?PREPARED:ENDPOINT):plan.status;
        if(editing)status=selected.status;
        else if(pending!=null&&pending.point==null)status=pending.status;
        // These are release actions, not guesses from client elapsed time or crouch state.
        if(c.hold!=null&&now(p)-c.hold.started>=RemoteScatterRelayRules.RECALL_TICKS)status=editing?CLEARING:MEASURING;
        send(c,editing?EDIT:ACTIVE,status,ghost,plan==null?List.of():plan.path);
    }
    private static void retireEmpty(Context c){if(c.plates.isEmpty()&&c.hold==null)finish(c);}
    private static void finish(Context c){if(CONTEXTS.remove(c.identity.owner().getUUID(),c)){c.hold=null;c.emitting=null;send(c,ENDED,PREPARED,null,List.of());}}
    private static List<PlateVisual> plateVisuals(List<Node> plates){return plates.stream().map(plate->new PlateVisual(vec(plate.center()),vec(plate.normal()))).toList();}
    private static void send(Context c,int phase,int status,Node ghost,List<SegmentVisual> path){
        ServerPlayer p=c.identity.owner();c.lastVisual=now(p);
        Vec3 ghostCenter=ghost!=null?vec(ghost.center()):c.hold!=null&&c.hold.editing&&c.hold.center!=null?c.hold.center:c.terminal!=null?c.terminal:Vec3.ZERO;
        Visual visual=new Visual(p.getId(),p.getUUID(),c.input.epoch(),c.input.input(),c.identity.token(),++c.sequence,phase,status,
                phase==ENDED?0:remainingTicks(p),p.position(),phase==ENDED?List.of():plateVisuals(c.plates),ghostCenter,ghost==null?Vec3.ZERO:vec(ghost.normal()),path);
        if(phase!=ENDED){c.audience.add(p);for(ServerPlayer observer:c.identity.level().players()){
            Vec3 eye=observer.getEyePosition();boolean nearby=observer.distanceToSqr(p)<=96*96;
            for(Node node:c.plates)nearby|=eye.distanceToSqr(vec(node.center()))<=96*96;
            for(SegmentVisual leg:path){Vec3 d=leg.end.subtract(leg.start);double t=d.lengthSqr()==0?0:Math.max(0,Math.min(1,eye.subtract(leg.start).dot(d)/d.lengthSqr()));nearby|=eye.distanceToSqr(leg.start.add(d.scale(t)))<=96*96;}
            if(nearby)c.audience.add(observer);
        }}
        for(ServerPlayer observer:c.audience)if(observer.connection!=null&&!observer.hasDisconnected()&&observer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(observer,visual);
    }
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static long now(ServerPlayer p){return p.serverLevel().getServer().overworld().getGameTime();}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=30_000_000&&Math.abs(v.y)<=30_000_000&&Math.abs(v.z)<=30_000_000;}
    private static Vector vector(Vec3 v){return new Vector(v.x,v.y,v.z);}
    private static Vec3 vec(Vector v){return new Vec3(v.x(),v.y(),v.z());}
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public record PlateVisual(Vec3 center,Vec3 normal){
        public PlateVisual{if(!finite(center)||!finite(normal))throw new IllegalArgumentException("Invalid NEW253 plate");new Node(0,vector(center),vector(normal));}
    }
    public record SegmentVisual(Vec3 start,Vec3 end){
        public SegmentVisual{if(!finite(start)||!finite(end)||start.distanceToSqr(end)>1024+1e-9)throw new IllegalArgumentException("Invalid NEW253 segment");}
    }
    public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int phase,int status,int remainingTicks,
                         Vec3 ownerFeet,List<PlateVisual> plates,Vec3 ghostCenter,Vec3 ghostNormal,List<SegmentVisual> path) implements CustomPacketPayload {
        public Visual{
            plates=List.copyOf(plates);path=List.copyOf(path);
            if(owner<0||ownerUuid==null||epoch<=0||input<=0||token<=0||sequence<=0||phase<ACTIVE||phase>ENDED||status<PREPARED||status>CLEARING
                    ||remainingTicks<0||remainingTicks> RemoteScatterRelayRules.LIFETIME||!finite(ownerFeet)||!finite(ghostCenter)||!finite(ghostNormal)
                    ||ghostNormal.lengthSqr()!=0&&Math.abs(ghostNormal.length()-1)>1e-10||plates.size()>4||path.size()>5
                    ||path.stream().mapToDouble(leg->leg.start.distanceTo(leg.end)).sum()>160+1e-8)throw new IllegalArgumentException("Invalid bounded NEW253 visual");
            if(ghostNormal.lengthSqr()!=0)new Node(0,vector(ghostCenter),vector(ghostNormal));
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","remote_scatter_relay_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.owner);b.writeUUID(v.ownerUuid);b.writeLong(v.epoch);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.sequence);
            b.writeByte(v.phase);b.writeByte(v.status);b.writeShort(v.remainingTicks);writeVec(b,v.ownerFeet);b.writeByte(v.plates.size());
            for(PlateVisual plate:v.plates){writeVec(b,plate.center);writeVec(b,plate.normal);}writeVec(b,v.ghostCenter);writeVec(b,v.ghostNormal);b.writeByte(v.path.size());
            for(SegmentVisual leg:v.path){writeVec(b,leg.start);writeVec(b,leg.end);}
        },b->{
            int owner=b.readInt();UUID uuid=b.readUUID();long epoch=b.readLong(),input=b.readLong(),token=b.readLong(),sequence=b.readLong();
            int phase=b.readUnsignedByte(),status=b.readUnsignedByte(),remaining=b.readUnsignedShort();Vec3 feet=readVec(b);
            int count=b.readUnsignedByte();if(count>4)throw new IllegalArgumentException("Unbounded NEW253 plates");List<PlateVisual> plates=new ArrayList<>(count);
            for(int i=0;i<count;i++)plates.add(new PlateVisual(readVec(b),readVec(b)));Vec3 center=readVec(b),normal=readVec(b);
            count=b.readUnsignedByte();if(count>5)throw new IllegalArgumentException("Unbounded NEW253 legs");List<SegmentVisual> path=new ArrayList<>(count);
            for(int i=0;i<count;i++)path.add(new SegmentVisual(readVec(b),readVec(b)));
            return new Visual(owner,uuid,epoch,input,token,sequence,phase,status,remaining,feet,plates,center,normal,path);
        });
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}
    }
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.level().getServer()==e.getServer())tick(c.identity.owner());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p){if(e.getEntity() instanceof ServerPlayer replacement&&p.getPersistentData().contains(DATA))replacement.getPersistentData().put(DATA,p.getPersistentData().get(DATA).copy());remove(p);}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
}
