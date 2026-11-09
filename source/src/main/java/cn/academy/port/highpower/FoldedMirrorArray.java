package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.FoldedMirrorRules;
import cn.academy.port.highpower.core.ScatterLocatorRules;
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

/** NEW221. Fixed finite plates, real native contacts, and one opaque whole-path damage permit. */
@EventBusSubscriber(modid="academy")
public final class FoldedMirrorArray {
    public static final String ID="folded_mirror_array";
    public static final int MIN_LEVEL=2,MAX_LEVEL=5;
    public static final int ACTIVE=0,EDIT=1,FIRE=2,ENDED=3;
    public static final int PREPARED=0,MISS=1,BLOCKED=2,REFUSED=3,DAMAGED=4,ABSORBED=5,DEGENERATE=6;
    private static final String DATA="academy:folded_mirror_array",INPUT_AT="inputAt",ATTEMPT_AT="attemptAt",EMPTY_AT="emptyAt",MASTERY_AT="masteryAt",PREVIEW_AT="previewAt";
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Map<ServerPlayer,Receipt> BORROWED_RECEIPTS=new IdentityHashMap<>();
    private static final Set<ServerPlayer> PROCESSING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static long nextToken;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input) {}
    private record Hold(HighPowerInput.Input input,long started,boolean editing,int index,Vec3 center) {}
    private record NativeHit(Vec3 point,Entity target,int status,boolean permitted,boolean contact) {}
    private record Plan(List<SegmentVisual> path,int crossings,LivingEntity target,Vec3 targetPosition,AABB targetBounds,
                        Vec3 hand,Vec3 eye,Vec3 ownerPosition,Vec3 look,List<Plate> plates,int status) {
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
        HighPowerInput.Input input;Hold hold;List<Plate> plates=List.of();
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
        private final HighPowerInput.Input input;private final long tick;private boolean consumed;
        private EmissionPermit(Context c,Plan plan,BigDecimal damage){context=c;this.plan=plan;this.damage=damage;input=c.input;tick=now(player());}
        ServerPlayer player(){return context.identity.owner();}
        LivingEntity target(){return plan.target;}
        BigDecimal damage(){return damage;}
        Vec3 finalOrigin(){return plan.finalOrigin();}
        Vec3 endpoint(){return plan.endpoint();}
        boolean consume(){if(consumed||!valid())return false;consumed=true;return true;}
        boolean valid(){
            ServerPlayer p=player();
            if(context.emitting!=this||owned(p)!=context||!context.resolving||!FoldedMirrorArray.valid(context)
                    ||now(p)!=tick||!context.input.equals(input)||!context.plates.equals(plan.plates)
                    ||!p.position().equals(plan.ownerPosition)||!p.getEyePosition().equals(plan.eye)
                    ||!p.getLookAngle().equals(plan.look)||!Objects.equals(hand(p),plan.hand)
                    ||!harmPermitted(p,plan.target)||!plan.target.position().equals(plan.targetPosition)
                    ||!plan.target.getBoundingBox().equals(plan.targetBounds))return false;
            Plan current=trace(p,context.plates);
            return current.target==plan.target&&current.crossings==plan.crossings&&current.path.equals(plan.path);
        }
    }
    private FoldedMirrorArray() {}

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
        int index=-1;Vec3 center=null;boolean editing=p.isShiftKeyDown();
        if(editing){
            Selection selected=select(p,c.plates);
            if(selected==null){retireEmpty(c);return false;}index=selected.index;center=selected.center;
        }
        c.hold=new Hold(input,now(p),editing,index,center);preview(c,true);return true;
    }
    /** Release retires only the press; the paid array retains its original identity and expiry. */
    public static boolean release(ServerPlayer p,String wire){
        Context c=owned(p);HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(c==null||c.resolving||c.hold==null||!HighPowerBracerSession.authenticated(p,input)||!c.hold.input.equals(input))return false;
        Hold hold=c.hold;
        if(!valid(c)||FoldedMirrorRules.expired(hold.started,now(p),FoldedMirrorRules.HOLD_LIMIT)){finish(c);return false;}
        c.hold=null;c.resolving=true;PROCESSING.add(p);
        try{
            if(hold.editing)return place(c,hold);
            if(now(p)-hold.started>=FoldedMirrorRules.RECALL_TICKS){finish(c);return true;}
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
    public static void remove(ServerPlayer p){if(thread(p)){abort(p);INPUTS.remove(p.getUUID());BORROWED_RECEIPTS.remove(p);}}
    public static void clear(){for(Context c:new ArrayList<>(CONTEXTS.values()))finish(c);INPUTS.clear();PROCESSING.clear();BORROWED_RECEIPTS.clear();}
    public static boolean active(ServerPlayer p){Context c=owned(p);if(c!=null&&!valid(c)){finish(c);return false;}return c!=null;}
    public static boolean holding(ServerPlayer p){Context c=owned(p);return c!=null&&c.hold!=null&&valid(c);}
    public static List<PlateVisual> plates(ServerPlayer p){Context c=owned(p);return c==null?List.of():plateVisuals(c.plates);}
    public static long placedAt(ServerPlayer p){Context c=owned(p);return c==null?-1:c.placedAt;}
    public static int remainingTicks(ServerPlayer p){Context c=owned(p);return c==null||c.placedAt<0?0:(int)Math.max(0,FoldedMirrorRules.LIFETIME-(now(p)-c.placedAt));}
    public static Receipt receipt(ServerPlayer p){Context c=owned(p);return c!=null&&c.receipt!=null?c.receipt:BORROWED_RECEIPTS.get(p);}
    /** NEW223 cannot replace its source while a native NEW221 opportunity is unwinding. */
    static boolean processing(ServerPlayer p){Context c=owned(p);return PROCESSING.contains(p)||c!=null&&c.resolving;}

    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.resolving)return;
        if(!valid(c)||now(p)<c.lastTick){finish(c);return;}
        if(now(p)==c.lastTick)return;c.lastTick=now(p);
        if(c.hold!=null&&FoldedMirrorRules.expired(c.hold.started,now(p),FoldedMirrorRules.HOLD_LIMIT)){
            c.hold=null;retireEmpty(c);if(owned(p)!=c)return;
        }
        preview(c,false);
    }
    private record Selection(int index,Vec3 center) {}
    private static Selection select(ServerPlayer p,List<Plate> plates){
        return select(p,plates,FoldedMirrorRules.PLACE_RANGE,2,FoldedMirrorRules.PLACE_RANGE);
    }
    private static Selection select(ServerPlayer p,List<Plate> plates,double range,int maximum,double airRange){
        Vec3 eye=p.getEyePosition(),direction=p.getLookAngle().normalize();
        var contact=FoldedMirrorGeometry.nearestContact(plates,vector(eye),vector(direction),range,-1);
        Vec3 end=contact.isPresent()?vec(contact.get().point()):endpoint(eye,direction,range);
        NativeHit hit=nativeTrace(p,eye,end);
        if(!hit.permitted)return null;
        if(contact.isPresent()&&!hit.contact){
            Plate selected=plates.get(contact.get().plateIndex());Vec3 center=vec(selected.center());
            return visibleCenter(p,center,range)?new Selection(selected.index(),center):null;
        }
        if(plates.size()>=maximum||hit.target!=null)return null;
        // Before the normal exists, a sphere encloses every possible complete slab orientation.
        // Air placement may leave room for native uncrouching/movement before release. A real
        // surface keeps its actual clearance-backed position, including the outer range band.
        double distance=hit.contact?eye.distanceTo(hit.point)-(Math.hypot(.45,.65)+.06):Math.min(eye.distanceTo(hit.point),airRange);
        if(!(distance>.8))return null;
        // Aimed down at the ground: rest the plate on the contact (raised by its enclosing radius) instead of backing off along
        // a shallow ray, which left it half buried, failing clearance, so every ground placement silently did nothing.
        Vec3 center=hit.contact&&direction.y<-.1?hit.point.add(0,Math.hypot(.45,.65)+.06,0):endpoint(eye,direction,Math.min(range,distance));
        return visibleCenter(p,center,range)?new Selection(plates.size(),center):null;
    }
    private static Plate candidate(Context c,Hold hold){
        ServerPlayer p=c.identity.owner();if(hold.index<0||hold.index>c.plates.size()||hold.index>1||!visibleCenter(p,hold.center))return null;
        Vec3 source=hold.index==0?hand(p):vec(c.plates.getFirst().center());
        Vec3 eye=p.getEyePosition(),end=endpoint(eye,p.getLookAngle().normalize(),FoldedMirrorRules.EXIT_RANGE);
        NativeHit exit=nativeTrace(p,eye,end);if(!exit.permitted)return null;
        var normal=FoldedMirrorGeometry.normalFor(vector(hold.center.subtract(source)),vector(exit.point.subtract(hold.center)));
        if(normal.isEmpty())return null;
        try{Plate plate=new Plate(hold.index,vector(hold.center),normal.get());return plateClear(p,plate)?plate:null;}
        catch(IllegalArgumentException invalid){return null;}
    }
    private static boolean place(Context c,Hold hold){
        ServerPlayer p=c.identity.owner();if(ScatterLocatorPlate.active(p))return false;
        Plate plate=candidate(c,hold);
        if(plate==null){send(c,ACTIVE,DEGENERATE,null,List.of());return false;}
        if(!valid(c)||!AbilityStorage.get(p).canUse(ID))return false;
        List<Plate> updated=new ArrayList<>(c.plates);if(hold.index==updated.size())updated.add(plate);else updated.set(hold.index,plate);
        // A closed debit has no external activation/cost callback between geometry validation and commit.
        double cost=FoldedMirrorRules.placementCost(HighPowerGrowth.values(p).position());
        if(!HighPowerResources.trySpend(p,ID,cost,1))return false;
        if(!valid(c)||!plateClear(p,plate))return false;
        c.plates=List.copyOf(updated);if(c.placedAt<0)c.placedAt=now(p);
        send(c,ACTIVE,PREPARED,null,List.of());return true;
    }
    private static boolean fire(Context c){
        ServerPlayer p=c.identity.owner();
        if(!AbilityStorage.get(p).canUse(ID)||!fenceReady(p,ATTEMPT_AT,FoldedMirrorRules.ATTEMPT_INTERVAL)
                ||!fenceReady(p,EMPTY_AT,FoldedMirrorRules.EMPTY_INTERVAL))return false;
        stamp(p,ATTEMPT_AT);
        // A locator can supply exactly one immutable plate only to an otherwise empty NEW221.
        // Ordinary paid arrays retain their original placement, shot and expiry paths.
        ScatterLocatorPlate.Borrow borrowed=c.plates.isEmpty()?ScatterLocatorPlate.borrow(p):null;
        List<Plate> shotPlates=borrowed==null?c.plates:List.of(borrowed.plate());
        if(shotPlates.isEmpty()){stamp(p,EMPTY_AT);return false;}
        Plan plan=trace(p,shotPlates);
        if(Boolean.getBoolean("academy.debug.lease"))com.mojang.logging.LogUtils.getLogger().info("MIRROR_FIRE status={} crossings={} target={} plates={} path={}",plan.status,plan.crossings,plan.target,shotPlates,plan.path);
        if(plan.target==null||plan.crossings<1||!harmPermitted(p,plan.target)){stamp(p,EMPTY_AT);send(c,ACTIVE,plan.status,null,List.of());return false;}
        var growth=HighPowerGrowth.values(p);BigDecimal amount=FoldedMirrorRules.damage(growth.damage());
        boolean paid=valid(c)&&(borrowed==null
                ?HighPowerResources.trySpend(p,ID,FoldedMirrorRules.shotCost(growth.position()),FoldedMirrorRules.cooldown(growth.position()))
                :borrowed.pay(FoldedMirrorRules.shotCost(growth.position()),FoldedMirrorRules.cooldown(growth.position())));
        if(!paid){stamp(p,EMPTY_AT);return false;}
        try{
            if(borrowed!=null){
                // Transfer precedes source retirement. EmissionPermit depends only on this exact
                // NEW221-owned snapshot, never on a consumed NEW223 context or resource hold.
                c.plates=shotPlates;borrowed.retire();
            }
            HighPowerNaturalRecovery.offensiveAction(p);
            EmissionPermit permit=new EmissionPermit(c,plan,amount);c.emitting=permit;
            BigDecimal hp=health(plan.target),shield=HighPowerCombat.shield(plan.target);float absorption=plan.target.getAbsorptionAmount();boolean accepted;
            try{accepted=HighPowerCombat.attackFoldedMirrorArray(permit);}finally{c.emitting=null;}
            int status=REFUSED;UUID victim=null;
            if(accepted&&health(plan.target).compareTo(hp)<0){status=DAMAGED;victim=plan.target.getUUID();}
            else if(accepted&&(HighPowerCombat.shield(plan.target).compareTo(shield)<0||plan.target.getAbsorptionAmount()<absorption)){status=ABSORBED;victim=plan.target.getUUID();}
            c.receipt=new Receipt(plan.path,plan.crossings,status,victim,c.input.input());
            if(borrowed!=null)BORROWED_RECEIPTS.put(p,c.receipt);
            if(owned(p)==c)send(c,FIRE,status,null,plan.path);
            if(victim!=null&&owned(p)==c&&valid(c)&&fenceReady(p,MASTERY_AT,FoldedMirrorRules.MASTERY_INTERVAL)){
                stamp(p,MASTERY_AT);c.identity.state().addExperience(ID,.005);AbilityStorage.save(p);
            }
            if(owned(p)==c&&!valid(c))finish(c);
            return true;
        }finally{
            // Paid cancellation, shield absorption, or an exception never returns the borrowed
            // plate or its fee. It must also never turn into a reusable ordinary NEW221 array.
            if(borrowed!=null){c.emitting=null;c.plates=List.of();borrowed.retire();}
        }
    }
    private static Plan trace(ServerPlayer p,List<Plate> plates){
        Vec3 hand=hand(p),eye=p.getEyePosition(),owner=p.position(),look=p.getLookAngle();
        List<SegmentVisual> path=new ArrayList<>(3);int crossings=0,status=MISS;LivingEntity victim=null;
        if(plates.isEmpty()||!finite(hand)||!clear(p,eye,hand))return plan(path,crossings,null,hand,eye,owner,look,plates,REFUSED);
        for(Plate plate:plates)if(!plateClear(p,plate))return plan(path,crossings,null,hand,eye,owner,look,plates,REFUSED);
        Vec3 origin=hand,direction=vec(FoldedMirrorGeometry.unit(plates.getFirst().center().subtract(vector(hand))).orElse(new Vector(0,0,0)));
        if(direction.lengthSqr()<.5)return plan(path,crossings,null,hand,eye,owner,look,plates,REFUSED);
        double remaining=FoldedMirrorRules.RANGE;int visited=0;FoldedMirrorGeometry.Contact justCrossed=null;
        for(int leg=0;leg<3&&remaining>0;leg++){
            var contact=FoldedMirrorGeometry.nearestContact(plates,vector(origin),vector(direction),remaining,justCrossed);
            Vec3 end=contact.isPresent()?vec(contact.get().point()):endpoint(origin,direction,remaining);
            NativeHit hit=nativeTrace(p,origin,end,leg>0);path.add(new SegmentVisual(origin,hit.point));
            if(!hit.permitted){status=REFUSED;break;}
            // Native solids and every hittable body at or before the plane terminate the ray.
            if(hit.contact){status=hit.status;if(crossings>0&&hit.target instanceof LivingEntity living&&harmPermitted(p,living))victim=living;break;}
            if(contact.isEmpty()){status=MISS;break;}
            var mirror=contact.get();
            if(!mirror.reflects()||mirror.distance()<=0||(visited&(1<<mirror.plateIndex()))!=0||crossings>=2){status=BLOCKED;break;}
            remaining-=origin.distanceTo(hit.point);if(remaining<0){status=REFUSED;break;}
            visited|=1<<mirror.plateIndex();crossings++;justCrossed=mirror;
            origin=hit.point;direction=vec(mirror.reflectedDirection());
        }
        return plan(path,crossings,victim,hand,eye,owner,look,plates,status);
    }
    private static Plan plan(List<SegmentVisual> path,int crossings,LivingEntity target,Vec3 hand,Vec3 eye,Vec3 owner,Vec3 look,List<Plate> plates,int status){
        return new Plan(path,crossings,target,target==null?null:target.position(),target==null?null:target.getBoundingBox(),hand,eye,owner,look,plates,status);
    }
    /** Queries loaded native shapes only. Each prefix is checked before its shapes are inspected. */
    private static NativeHit nativeTrace(ServerPlayer p,Vec3 origin,Vec3 endpoint){
        return nativeTrace(p,origin,endpoint,false);
    }
    private static NativeHit nativeTrace(ServerPlayer p,Vec3 origin,Vec3 endpoint,boolean includeCaster){
        if(!finite(origin)||!finite(endpoint)||origin.distanceToSqr(endpoint)>FoldedMirrorRules.RANGE*FoldedMirrorRules.RANGE+1e-9)return new NativeHit(origin,null,REFUSED,false,false);
        int pieces=Math.max(1,(int)Math.ceil(origin.distanceTo(endpoint)/.25));Vec3 previous=origin;
        Set<Entity> scanned=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=1;i<=pieces;i++){
            Vec3 next=origin.lerp(endpoint,(double)i/pieces);AABB box=new AABB(previous,next).inflate(FoldedMirrorRules.RADIUS);
            // Expand candidate lookup by one representable coordinate only. Native shape lookup
            // otherwise omits a solid tangent to this closed swept boundary. Clip radius, ray
            // origin and terminal point stay exact; validate the lookup volume before any read.
            AABB query=outward(box);
            if(!permittedVolume(p,query))return new NativeHit(previous,null,REFUSED,false,false);
            // The same checked short prefix bounds entity reads as well as solid reads. A body
            // terminates before permission/chunk checks for distant, unused parts of the budget.
            Vec3 end=next;Entity target=null;double nearest=origin.distanceToSqr(end);
            List<Entity> candidates=new ArrayList<>();
            p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class),query,
                    e->(e!=p||includeCaster)&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()&&e.canBeHitByProjectile(),candidates,FoldedMirrorRules.SCAN_LIMIT+1);
            scanned.addAll(candidates);
            if(scanned.size()>FoldedMirrorRules.SCAN_LIMIT)return new NativeHit(previous,null,REFUSED,false,false);
            for(Entity entity:candidates){
                Optional<Vec3> hit=clipInclusive(entity.getBoundingBox().inflate(FoldedMirrorRules.RADIUS),origin,next);
                if(hit.isPresent()&&(origin.distanceToSqr(hit.get())<nearest||origin.distanceToSqr(hit.get())==nearest&&(target==null||entity.getId()<target.getId()))){
                    end=hit.get();nearest=origin.distanceToSqr(end);target=entity;
                }
            }
            Vec3 solidPoint=null;
            for(var shape:p.serverLevel().getBlockCollisions(p,query))for(AABB solid:shape.toAabbs()){
                AABB bounds=solid.inflate(FoldedMirrorRules.RADIUS);Optional<Vec3> hit=clipInclusive(bounds,origin,end);
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
    private static boolean visibleCenter(ServerPlayer p,Vec3 center){return visibleCenter(p,center,FoldedMirrorRules.PLACE_RANGE);}
    private static boolean visibleCenter(ServerPlayer p,Vec3 center,double range){
        return finite(center)&&p.getEyePosition().distanceToSqr(center)<=range*range&&clear(p,p.getEyePosition(),center);
    }
    /** NEW223 reuses these checked native queries and the same pure fixed-plane construction. */
    static Vec3 locatorCenter(ServerPlayer p,Plate existing){
        Selection selected=select(p,existing==null?List.of():List.of(existing),ScatterLocatorRules.PLACE_RANGE,1,ScatterLocatorRules.AIR_PLACE_RANGE);
        if(selected!=null)return selected.center;
        // NEW223 edit selection only. Quotient rounding can put an exact center endpoint one
        // ULP beyond24. Never widen the ray, alter contact(), or skip native obstruction checks.
        Vec3 eye=p.getEyePosition(),direction=p.getLookAngle().normalize();
        if(!ScatterLocatorRules.exactCenterAtEditLimit(existing,vector(eye),vector(direction)))return null;
        Vec3 center=vec(existing.center());
        return visibleCenter(p,center,ScatterLocatorRules.PLACE_RANGE)?center:null;
    }
    static Plate locatorCandidate(ServerPlayer p,Vec3 center){
        if(!visibleCenter(p,center,ScatterLocatorRules.PLACE_RANGE))return null;
        Vec3 eye=p.getEyePosition(),end=endpoint(eye,p.getLookAngle().normalize(),FoldedMirrorRules.EXIT_RANGE);
        NativeHit exit=nativeTrace(p,eye,end);if(!exit.permitted)return null;
        var normal=FoldedMirrorGeometry.normalFor(vector(center.subtract(hand(p))),vector(exit.point.subtract(center)));
        if(normal.isEmpty())return null;
        try{Plate plate=new Plate(0,vector(center),normal.get());return plateClear(p,plate)?plate:null;}
        catch(IllegalArgumentException invalid){return null;}
    }
    static boolean locatorClear(ServerPlayer p,Plate plate){return plate!=null&&plateClear(p,plate);}
    static boolean locatorOwnerVolume(ServerPlayer p){return permittedVolume(p,p.getBoundingBox());}
    static List<SegmentVisual> locatorPreview(ServerPlayer p,Plate plate){return trace(p,List.of(plate)).path;}

    private static boolean clear(ServerPlayer p,Vec3 a,Vec3 b){NativeHit hit=nativeTrace(p,a,b);return hit.permitted&&!hit.contact;}
    private static boolean plateClear(ServerPlayer p,Plate plate){
        var bounds=FoldedMirrorGeometry.volumeBounds(plate);AABB box=new AABB(vec(bounds.min()),vec(bounds.max()));
        return permittedVolume(p,box)&&!p.serverLevel().getBlockCollisions(p,box).iterator().hasNext();
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
    private static boolean canAct(ServerPlayer p){
        if(!thread(p))return false;var state=AbilityStorage.get(p);
        return p.connection!=null&&!p.hasDisconnected()&&p.containerMenu==p.inventoryMenu&&HighPowerResources.ready(p)
                &&!p.getAbilities().instabuild&&!p.getAbilities().invulnerable&&state.activated&&state.overloadFine&&!state.interfering
                &&state.learned(ID)&&state.learned(DualPoint.ID)&&state.category.equals("meltdowner")&&state.level>=MIN_LEVEL&&state.level<=MAX_LEVEL
                &&HighPowerResources.effectiveLevel(p)>=MIN_LEVEL&&SkillCatalog.enabled("meltdowner",ID)
                &&HighPowerCombat.unsupportedReason(p).isEmpty()&&!AbilityConsumption.busy(p)&&!p.noPhysics&&!p.isPassenger()&&!p.isSleeping()
                &&finite(p.position())&&finite(p.getDeltaMovement())&&finite(p.getLookAngle())&&p.getLookAngle().lengthSqr()>.5
                &&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())&&FoldedMirrorRules.clock(now(p))&&permittedVolume(p,p.getBoundingBox());
    }
    private static boolean valid(Context c){
        ServerPlayer p=c.identity.owner();return c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canAct(p)
                &&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision()
                &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))
                &&(c.placedAt<0||!FoldedMirrorRules.expired(c.placedAt,now(p),FoldedMirrorRules.LIFETIME));
    }
    private static boolean harmPermitted(ServerPlayer p,LivingEntity target){
        ServerLevel world=p.serverLevel();
        if(target==null||target==p||target.level()!=world||!target.isAlive()||target.isRemoved()||target.isSpectator()
                ||world.getEntity(target.getUUID())!=target||world.getEntity(target.getId())!=target||!HighPowerCombat.supported(target)
                ||HighPowerCombat.unsupportedReason(target).isPresent()||target.isInvulnerable()||target.getPersistentData().getBoolean("academy:protected")
                ||p.isAlliedTo(target)||target.isAlliedTo(p)||!permittedVolume(p,target.getBoundingBox()))return false;
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
        CompoundTag data=root.getCompound(DATA);return !data.contains(key)||data.contains(key,Tag.TAG_LONG)&&FoldedMirrorRules.ready(data.getLong(key),now(p),interval);
    }
    private static void stamp(ServerPlayer p,String key){CompoundTag data=p.getPersistentData().getCompound(DATA);data.putLong(key,now(p));p.getPersistentData().put(DATA,data);}
    private static BigDecimal health(LivingEntity target){return HighPowerCombat.enabled(target)?new BigDecimal(HighPowerCombat.healthText(target)):BigDecimal.valueOf(target.getHealth());}
    private static void preview(Context c,boolean force){
        ServerPlayer p=c.identity.owner();if(!force&&!FoldedMirrorRules.ready(c.lastVisual,now(p),FoldedMirrorRules.PREVIEW_INTERVAL))return;
        if(c.hold!=null&&c.hold.editing&&!fenceReady(p,PREVIEW_AT,FoldedMirrorRules.PREVIEW_INTERVAL)){
            if(force)send(c,EDIT,PREPARED,null,List.of());return;
        }
        if(c.hold!=null&&c.hold.editing)stamp(p,PREVIEW_AT);
        Plate ghost=c.hold!=null&&c.hold.editing?candidate(c,c.hold):null;
        List<SegmentVisual> planned=List.of();
        if(ghost!=null){List<Plate> proposal=new ArrayList<>(c.plates);if(ghost.index()==proposal.size())proposal.add(ghost);else proposal.set(ghost.index(),ghost);planned=trace(p,List.copyOf(proposal)).path;}
        send(c,c.hold!=null&&c.hold.editing?EDIT:ACTIVE,c.hold!=null&&c.hold.editing&&ghost==null?DEGENERATE:PREPARED,ghost,planned);
    }
    private static void retireEmpty(Context c){if(c.plates.isEmpty()&&c.hold==null)finish(c);}
    private static void finish(Context c){if(CONTEXTS.remove(c.identity.owner().getUUID(),c)){c.hold=null;c.emitting=null;send(c,ENDED,PREPARED,null,List.of());}}
    private static List<PlateVisual> plateVisuals(List<Plate> plates){return plates.stream().map(plate->new PlateVisual(vec(plate.center()),vec(plate.normal()))).toList();}
    private static void send(Context c,int phase,int status,Plate ghost,List<SegmentVisual> path){
        ServerPlayer p=c.identity.owner();c.lastVisual=now(p);
        Vec3 ghostCenter=ghost!=null?vec(ghost.center()):c.hold!=null&&c.hold.editing?c.hold.center:Vec3.ZERO;
        Visual visual=new Visual(p.getId(),p.getUUID(),c.input.epoch(),c.input.input(),c.identity.token(),++c.sequence,phase,status,
                phase==ENDED?0:remainingTicks(p),p.position(),phase==ENDED?List.of():plateVisuals(c.plates),ghostCenter,ghost==null?Vec3.ZERO:vec(ghost.normal()),path);
        if(phase!=ENDED){c.audience.add(p);for(ServerPlayer observer:c.identity.level().players())if(observer.distanceToSqr(p)<=96*96)c.audience.add(observer);}
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
        public PlateVisual{if(!finite(center)||!finite(normal))throw new IllegalArgumentException("Invalid NEW221 plate");new Plate(0,vector(center),vector(normal));}
    }
    public record SegmentVisual(Vec3 start,Vec3 end){
        public SegmentVisual{if(!finite(start)||!finite(end)||start.distanceToSqr(end)>1600+1e-8)throw new IllegalArgumentException("Invalid NEW221 segment");}
    }
    public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int phase,int status,int remainingTicks,
                         Vec3 ownerFeet,List<PlateVisual> plates,Vec3 ghostCenter,Vec3 ghostNormal,List<SegmentVisual> path) implements CustomPacketPayload {
        public Visual{
            plates=List.copyOf(plates);path=List.copyOf(path);
            if(owner<0||ownerUuid==null||epoch<=0||input<=0||token<=0||sequence<=0||phase<ACTIVE||phase>ENDED||status<PREPARED||status>DEGENERATE
                    ||remainingTicks<0||remainingTicks> FoldedMirrorRules.LIFETIME||!finite(ownerFeet)||!finite(ghostCenter)||!finite(ghostNormal)
                    ||ghostNormal.lengthSqr()!=0&&Math.abs(ghostNormal.length()-1)>1e-10||plates.size()>2||path.size()>3
                    ||path.stream().mapToDouble(leg->leg.start.distanceTo(leg.end)).sum()>40+1e-8)throw new IllegalArgumentException("Invalid bounded NEW221 visual");
            if(ghostNormal.lengthSqr()!=0)new Plate(0,vector(ghostCenter),vector(ghostNormal));
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","folded_mirror_array_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.owner);b.writeUUID(v.ownerUuid);b.writeLong(v.epoch);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.sequence);
            b.writeByte(v.phase);b.writeByte(v.status);b.writeShort(v.remainingTicks);writeVec(b,v.ownerFeet);b.writeByte(v.plates.size());
            for(PlateVisual plate:v.plates){writeVec(b,plate.center);writeVec(b,plate.normal);}writeVec(b,v.ghostCenter);writeVec(b,v.ghostNormal);b.writeByte(v.path.size());
            for(SegmentVisual leg:v.path){writeVec(b,leg.start);writeVec(b,leg.end);}
        },b->{
            int owner=b.readInt();UUID uuid=b.readUUID();long epoch=b.readLong(),input=b.readLong(),token=b.readLong(),sequence=b.readLong();
            int phase=b.readUnsignedByte(),status=b.readUnsignedByte(),remaining=b.readUnsignedShort();Vec3 feet=readVec(b);
            int count=b.readUnsignedByte();if(count>2)throw new IllegalArgumentException("Unbounded NEW221 plates");List<PlateVisual> plates=new ArrayList<>(count);
            for(int i=0;i<count;i++)plates.add(new PlateVisual(readVec(b),readVec(b)));Vec3 center=readVec(b),normal=readVec(b);
            count=b.readUnsignedByte();if(count>3)throw new IllegalArgumentException("Unbounded NEW221 legs");List<SegmentVisual> path=new ArrayList<>(count);
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
