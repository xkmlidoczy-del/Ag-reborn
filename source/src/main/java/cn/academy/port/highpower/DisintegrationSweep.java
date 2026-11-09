package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.DisintegrationSweepGeometry;
import cn.academy.port.highpower.core.DisintegrationSweepGeometry.Contact;
import cn.academy.port.highpower.core.DisintegrationSweepGeometry.FrozenPlane;
import cn.academy.port.highpower.core.DisintegrationSweepRules;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
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
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
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

/** NEW231. One authenticated charge, one immutable fan, and bounded native terminal permits. */
@EventBusSubscriber(modid="academy")
public final class DisintegrationSweep {
    public static final String ID="disintegration_sweep";
    public static final int MIN_LEVEL=3,MAX_LEVEL=5,CHARGING=0,SWEEPING=1,ENDED=2;
    public static final int PREPARED=0,MISS=1,BLOCKED=2,REFUSED=3,DAMAGED=4,ABSORBED=5,MINED=6;
    private static final double RANGE=48,RADIUS=.035,HALF=Math.toRadians(25);
    private static final String DATA="academy:disintegration_sweep",INPUT_AT="inputAt",PREVIEW_AT="previewAt",MASTERY_AT="masteryAt";
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Set<ServerPlayer> PROCESSING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static long nextToken;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input) {}
    private record NativeHit(Vec3 point,Entity target,AABB blocker,int status,boolean permitted,boolean contact) {}
    private record Visible(Contact contact,NativeHit hit,Vec3 rayEnd) {}
    private static final class TargetSlot {
        final LivingEntity entity;final UUID uuid;final int id;final double angle,distance;final BigDecimal share;
        boolean attempted;
        TargetSlot(LivingEntity e,double angle,double distance,BigDecimal share){entity=e;uuid=e.getUUID();id=e.getId();this.angle=angle;this.distance=distance;this.share=share;}
    }
    private record Candidate(LivingEntity entity,double angle,double distance) {}
    private record MiningSlot(BeamEtchingMining.Target target,double angle,double distance) {}
    private record Emission(TargetSlot slot,Vec3 origin,Vec3 endpoint,Vec3 rayEnd,Vec3 position,Vec3 eye,Vec3 look,Vec3 targetPosition,AABB targetBounds,double angle,double from,double to,long tick) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;final HighPowerInput.Input input;final int preset;final long revision,started;
        final boolean mining;final Set<ServerPlayer> audience=new LinkedHashSet<>();final BeamEtchingMining.Capacity capacity=BeamEtchingMining.newCapacity();
        FrozenPlane plane,previewPlane;Vec3 releasePosition;List<TargetSlot> targets=List.of();List<MiningSlot> cells=List.of();
        List<ItemStack> inventory;int selected,nextCell,attempts,successes,cooldown;long acquisitionSurfacePrefixes,acquisitionNativePrefixes;double cost;BigDecimal total=BigDecimal.ZERO;
        long released=-1,lastTick=-1,lastVisual=-1,sequence;double angle=-HALF;
        boolean processing,freezing,paid,rewarded;EmissionPermit emitting;MiningPermit digging;Receipt receipt;
        Context(ServerPlayer p,HighPowerInput.Input input){identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));this.input=input;
            preset=identity.state().presets.current();revision=identity.state().presets.revision();started=now(p);mining=p.isShiftKeyDown();previewPlane=DisintegrationSweepGeometry.freeze(vector(hand(p)),vector(p.getLookAngle()),Math.toRadians(p.getYRot()),mining).orElseThrow();}
    }
    /** Opaque single target allocation. The combat bridge cannot invent another target, ray or share. */
    public static final class EmissionPermit {
        private final Context context;private final Emission emission;private boolean consumed;
        private EmissionPermit(Context c,Emission emission){context=c;this.emission=emission;}
        ServerPlayer player(){return context.identity.owner();}
        LivingEntity target(){return emission.slot.entity;}
        BigDecimal damage(){return emission.slot.share;}
        Vec3 finalOrigin(){return emission.origin;}
        Vec3 endpoint(){return emission.endpoint;}
        boolean consume(){if(consumed||!valid())return false;consumed=true;return true;}
        boolean valid(){
            ServerPlayer p=player();LivingEntity target=target();
            if(context.emitting!=this||!context.processing||!context.paid||context.mining||!DisintegrationSweep.valid(context)
                    ||now(p)!=emission.tick||!p.position().equals(emission.position)||!p.getEyePosition().equals(emission.eye)
                    ||!p.getLookAngle().equals(emission.look)||!Objects.equals(hand(p),emission.origin)
                    ||!identity(emission.slot,p)||!harmPermitted(p,target)||!target.position().equals(emission.targetPosition)
                    ||!target.getBoundingBox().equals(emission.targetBounds)||!footprint(context,emission.endpoint))return false;
            var contact=DisintegrationSweepGeometry.contactAt(context.plane,vector(emission.origin),bounds(target.getBoundingBox()),emission.angle);
            if(contact.isEmpty()||contact.get().angle()<emission.from-1e-10||contact.get().angle()>emission.to+1e-10)return false;
            NativeHit hit=nativeTrace(p,emission.origin,emission.rayEnd);
            return hit.permitted&&hit.target==target&&hit.point.distanceToSqr(emission.endpoint)<1e-16;
        }
    }
    /** Fixed cell and real observed hand origin; only this controller can authorize the 48m owner. */
    public static final class MiningPermit {
        private final Context context;private final BeamEtchingMining.Target target;private final Vec3 origin,position,eye,look;private final long tick;private final double cost;
        private boolean consumed;
        private MiningPermit(Context c,BeamEtchingMining.Target target){context=c;this.target=target;ServerPlayer p=player();origin=hand(p);position=p.position();eye=p.getEyePosition();look=p.getLookAngle();tick=now(p);cost=c.cost;}
        ServerPlayer player(){return context.identity.owner();}
        BeamEtchingMining.Target target(){return target;}
        double cost(){return cost;}
        Vec3 origin(){return origin;}
        boolean consume(){if(consumed||context.freezing||!context.processing||!valid())return false;consumed=true;return true;}
        boolean valid(){ServerPlayer p=player();return context.digging==this&&context.mining&&(context.freezing||context.processing)
                &&DisintegrationSweep.valid(context)&&now(p)==tick&&p.position().equals(position)&&p.getEyePosition().equals(eye)
                &&p.getLookAngle().equals(look)&&Objects.equals(hand(p),origin)&&miningVisible(context,target,origin);}
    }
    public record Receipt(List<SegmentVisual> path,int attempts,int successes,boolean paid,boolean mining,long input){public Receipt{path=List.copyOf(path);}}
    private DisintegrationSweep() {}

    /** Native crouch is sampled exactly once, at the authenticated physical press. */
    public static boolean start(ServerPlayer p,String wire){
        if(PROCESSING.contains(p))return false;Context old=owned(p);if(old!=null&&old.processing)return false;
        HighPowerInput.Input input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark prior=INPUTS.get(p.getUUID());if(prior!=null&&prior.epoch==input.epoch()&&input.input()<=prior.input)return false;
        if(old!=null){if(valid(old))return false;finish(old);}
        INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        if(!canAct(p)||!AbilityStorage.get(p).canUse(ID)||!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))
                ||!fenceReady(p,INPUT_AT,1)||nextToken==Long.MAX_VALUE)return false;
        Context foreign=CONTEXTS.get(p.getUUID());if(foreign!=null)finish(foreign);
        stamp(p,INPUT_AT);Context c=new Context(p,input);CONTEXTS.put(p.getUUID(),c);preview(c);return true;
    }
    public static boolean release(ServerPlayer p,String wire){
        Context c=owned(p);var input=HighPowerInput.parse(wire);
        if(c==null||c.processing||c.released>=0||!HighPowerBracerSession.authenticated(p,input)||!c.input.equals(input))return false;
        if(!valid(c)||now(p)-c.started>=100){finish(c);return false;}
        if(now(p)-c.started<8){finish(c);return true;}
        c.processing=true;PROCESSING.add(p);
        try{
            var frame=DisintegrationSweepGeometry.freeze(vector(hand(p)),vector(p.getLookAngle()),Math.toRadians(p.getYRot()),c.mining);
            if(frame.isEmpty()||!clearHand(p)){finish(c);return false;}
            c.plane=frame.get();c.releasePosition=p.position();c.released=now(p);c.lastTick=c.released;
            var growth=HighPowerGrowth.values(p);c.total=DisintegrationSweepRules.damage(growth.damage());
            c.cost=c.mining?DisintegrationSweepRules.miningCost(growth.position()):DisintegrationSweepRules.attackCost(growth.position());
            c.cooldown=c.mining?DisintegrationSweepRules.miningCooldown(growth.position()):DisintegrationSweepRules.cooldown(growth.position());
            c.inventory=inventory(p);c.selected=p.getInventory().selected;c.freezing=true;
            if(c.mining)freezeCells(c);else freezeTargets(c);
            c.freezing=false;
            if(!valid(c)){finish(c);return false;}
            send(c,SWEEPING,PREPARED,-HALF,-HALF,List.of());return true;
        }finally{c.freezing=false;c.processing=false;PROCESSING.remove(p);}
    }
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;Context c=owned(p);
        boolean owns=c!=null&&c.input.epoch()==input.epoch()&&c.input.slot()==input.slot()&&input.input()>=c.input.input();
        if(!owns&&!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())))return false;
        Watermark old=INPUTS.get(p.getUUID());boolean retired=old==null||old.epoch!=input.epoch()||input.input()>old.input;
        if(retired)INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));if(owns)finish(c);return owns||retired;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c);}
    public static void remove(ServerPlayer p){if(thread(p)){abort(p);INPUTS.remove(p.getUUID());}}
    public static void clear(){for(Context c:new ArrayList<>(CONTEXTS.values()))finish(c);INPUTS.clear();PROCESSING.clear();}
    public static boolean active(ServerPlayer p){Context c=owned(p);if(c!=null&&!valid(c)){finish(c);return false;}return c!=null;}
    public static boolean holding(ServerPlayer p){Context c=owned(p);return c!=null&&c.released<0&&valid(c);}
    public static boolean executing(ServerPlayer p){Context c=owned(p);return c!=null&&c.mining&&c.released>=0&&c.processing&&!c.freezing&&valid(c);}
    public static List<Mark> marks(ServerPlayer p){Context c=owned(p);return c==null?List.of():c.cells.stream().map(slot->mark(slot.target)).toList();}
    public static int completed(ServerPlayer p){Context c=owned(p);return c==null?0:c.successes;}
    public static Receipt receipt(ServerPlayer p){Context c=owned(p);return c==null?null:c.receipt;}

    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.processing)return;
        if(!valid(c)||now(p)<c.lastTick){finish(c);return;}if(now(p)==c.lastTick)return;c.lastTick=now(p);
        if(c.released<0){if(now(p)-c.started>=100)finish(c);else preview(c);return;}
        long elapsed=now(p)-c.released;if(elapsed>10){finish(c);return;}if(elapsed<1)return;
        double from=c.angle,to=DisintegrationSweepRules.angleAt((int)elapsed);c.angle=to;c.processing=true;PROCESSING.add(p);
        try{
            List<SegmentVisual> path=new ArrayList<>();if(c.mining)mine(c,from,to,path);else attack(c,from,to,path);
            if(owned(p)!=c)return;
            if(path.isEmpty()){
                Vec3 origin=hand(p);var end=DisintegrationSweepGeometry.rayEndpoint(c.plane,vector(origin),to);
                if(end.isPresent()){
                    NativeHit hit=nativeTrace(p,origin,vec(end.get()));
                    if(hit.permitted&&footprint(c,hit.point))path.add(new SegmentVisual(origin,hit.point,hit.status));
                }
            }
            c.receipt=new Receipt(path,c.attempts,c.successes,c.paid,c.mining,c.input.input());
            send(c,SWEEPING,path.stream().mapToInt(SegmentVisual::status).max().orElse(MISS),from,to,path);
            if(elapsed==10||!valid(c))finish(c);
        }finally{c.emitting=null;c.digging=null;c.processing=false;PROCESSING.remove(p);}
    }
    private static void freezeTargets(Context c){
        ServerPlayer p=c.identity.owner();AABB query=box(DisintegrationSweepGeometry.queryBounds(c.plane));
        // The native entity section query only visits resident entities; a far unloaded fan
        // suffix cannot hide an already visible nearby target or request a chunk load.
        List<Entity> scanned=new ArrayList<>();p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class),query,
                e->e!=p&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()&&e.canBeHitByProjectile(),scanned,65);
        if(scanned.size()>64)return;List<Candidate> candidates=new ArrayList<>();
        for(Entity entity:scanned)if(entity instanceof LivingEntity living&&harmPermitted(p,living)){
            Visible visible=visibleContact(c,living,-HALF,HALF);if(visible!=null)candidates.add(new Candidate(living,visible.contact.angle(),visible.contact.distance()));
        }
        candidates.sort(Comparator.comparingDouble(Candidate::angle).thenComparingDouble(Candidate::distance).thenComparingInt(v->v.entity.getId()).thenComparing(v->v.entity.getUUID()));
        if(candidates.size()>8)candidates=new ArrayList<>(candidates.subList(0,8));
        List<BigDecimal> shares=DisintegrationSweepRules.shares(c.total,candidates.size());List<TargetSlot> slots=new ArrayList<>();
        for(int i=0;i<candidates.size();i++){Candidate value=candidates.get(i);slots.add(new TargetSlot(value.entity,value.angle,value.distance,shares.get(i)));}c.targets=List.copyOf(slots);
    }
    /** Uses analytic body and blocker intervals. Render-ray sampling never selects combat targets. */
    private static Visible visibleContact(Context c,LivingEntity target,double from,double to){
        ServerPlayer p=c.identity.owner();Vec3 origin=hand(p);double cursor=from;int precisionRetries=0;
        for(int i=0;i<=64&&cursor<=to;i++){
            Optional<Contact> contact=DisintegrationSweepGeometry.firstContact(c.plane,vector(origin),bounds(target.getBoundingBox()),cursor,to);
            if(contact.isEmpty())return null;Contact ray=contact.get();
            var limited=DisintegrationSweepGeometry.rayEndpoint(c.plane,vector(origin),ray.angle());
            if(limited.isEmpty()){
                // A native world-coordinate endpoint can be unrepresentable on an
                // exact fan edge even though this body extends into the legal fan.
                // Propose a bounded strict interior angle, then repeat every same
                // analytic, endpoint, nearest-solid/body and footprint check.
                var next=DisintegrationSweepGeometry.nativeInteriorCandidate(c.plane,vector(origin),bounds(target.getBoundingBox()),ray,to,precisionRetries++);
                if(next.isEmpty()||!(next.getAsDouble()>cursor))return null;cursor=next.getAsDouble();continue;
            }
            Vec3 end=vec(limited.get());NativeHit hit=nativeTrace(p,origin,end);
            if(!hit.permitted)return null;
            if(hit.target==target&&footprint(c,hit.point))return new Visible(ray,hit,end);
            if(hit.blocker==null){
                // A one-angle-ULP step can leave the world endpoint unchanged. Propose only
                // a bounded representable interior angle, then perform the same native trace.
                var next=DisintegrationSweepGeometry.nativeInteriorCandidate(c.plane,vector(origin),bounds(target.getBoundingBox()),ray,to,precisionRetries++);
                if(next.isEmpty()||!(next.getAsDouble()>cursor))return null;cursor=next.getAsDouble();continue;
            }
            precisionRetries=0; // A newly verified native blocker retains the existing exact interval transition.
            var blockers=DisintegrationSweepGeometry.contactIntervals(c.plane,vector(origin),bounds(hit.blocker),cursor,to);
            double next=cursor;for(var interval:blockers)if(interval.from()<=ray.angle()+1e-10&&interval.to()>=ray.angle()-1e-10)next=Math.max(next,Math.nextUp(interval.to()));
            if(!(next>cursor))return null;cursor=next;
        }
        return null;
    }
    private static void attack(Context c,double from,double to,List<SegmentVisual> path){
        ServerPlayer p=c.identity.owner();
        for(TargetSlot slot:c.targets){
            if(slot.attempted||!identity(slot,p)||!harmPermitted(p,slot.entity))continue;
            Visible visible=visibleContact(c,slot.entity,from,to);if(visible==null)continue;
            slot.attempted=true;c.attempts++;
            Vec3 origin=hand(p),end=visible.hit.point;LivingEntity target=slot.entity;
            Emission emission=new Emission(slot,origin,end,visible.rayEnd,p.position(),p.getEyePosition(),p.getLookAngle(),target.position(),target.getBoundingBox(),visible.contact.angle(),from,to,now(p));
            if(!c.paid){
                if(!valid(c)||!AbilityStorage.get(p).canUse(ID)||!HighPowerResources.trySpend(p,ID,c.cost,c.cooldown)){finish(c);return;}
                c.paid=true;HighPowerNaturalRecovery.offensiveAction(p);
            }
            EmissionPermit permit=new EmissionPermit(c,emission);c.emitting=permit;
            BigDecimal hp=health(target),shield=HighPowerCombat.shield(target);float absorption=target.getAbsorptionAmount();boolean accepted;
            try{accepted=HighPowerCombat.attackDisintegrationSweep(permit);}finally{c.emitting=null;}
            int status=REFUSED;
            if(accepted&&health(target).compareTo(hp)<0)status=DAMAGED;
            else if(accepted&&(HighPowerCombat.shield(target).compareTo(shield)<0||target.getAbsorptionAmount()<absorption))status=ABSORBED;
            if(status==DAMAGED||status==ABSORBED)c.successes++;
            path.add(new SegmentVisual(origin,end,status));
            if(owned(p)!=c||!valid(c)){finish(c);return;}
        }
    }
    /** Finite initial visible-surface scan. Every admitted cell is fixed before any removal occurs. */
    private static void freezeCells(Context c){
        ServerPlayer p=c.identity.owner();Map<BlockPos,MiningSlot> found=new HashMap<>();Vec3 origin=hand(p);
        for(int i=0;i<=200;i++){
            double angle=i==0?-HALF:i==200?HALF:-HALF+2*HALF*i/200;var limited=DisintegrationSweepGeometry.rayEndpoint(c.plane,vector(origin),angle);if(limited.isEmpty())continue;Vec3 end=vec(limited.get());
            // A grazing point can fail the native thickness proof while a later existing
            // ray sees a valid point on the same initial face. Only admitted cells deduplicate.
            BlockHitResult hit=firstSurface(p,origin,end);if(hit==null||hit.getType()!=HitResult.Type.BLOCK||hit.isInside()||!footprint(c,hit.getLocation())||found.containsKey(hit.getBlockPos()))continue;
            if(!VanillaMiningContract.reviewedBlock(p.serverLevel().getBlockState(hit.getBlockPos())))continue;
            var target=new BeamEtchingMining.Target(hit.getBlockPos(),p.serverLevel().getBlockState(hit.getBlockPos()),hit.getLocation(),hit.getDirection());
            MiningPermit permit=new MiningPermit(c,target);c.digging=permit;
            try{if(BeamEtchingMining.inspectSweep(permit,c.capacity).eligible())found.put(target.pos(),new MiningSlot(target,angle,origin.distanceTo(target.surface())));}finally{c.digging=null;}
            if(found.size()==16)break;
        }
        List<MiningSlot> ordered=new ArrayList<>(found.values());ordered.sort(Comparator.comparingDouble(MiningSlot::angle).thenComparingDouble(MiningSlot::distance).thenComparingLong(v->v.target.pos().asLong()));
        c.cells=List.copyOf(ordered.subList(0,Math.min(16,ordered.size())));
    }
    private static void mine(Context c,double from,double to,List<SegmentVisual> path){
        ServerPlayer p=c.identity.owner();if(!inventoryUnchanged(c)){finish(c);return;}
        while(c.nextCell<c.cells.size()){
            MiningSlot slot=c.cells.get(c.nextCell);if(slot.angle>to+1e-10)return;
            if(!BeamEtching.sharedAttemptReady(p))return;
            if(!valid(c)||!miningVisible(c,slot.target,hand(p))){finish(c);return;}
            MiningPermit permit=new MiningPermit(c,slot.target);c.digging=permit;
            if(!BeamEtching.claimSharedAttempt(p)){c.digging=null;return;}c.nextCell++;c.attempts++;
            BeamEtchingMining.Result result;try{result=BeamEtchingMining.attemptSweep(permit,c.capacity);}finally{c.digging=null;}
            if(!result.removed()){finish(c);return;}
            c.successes++;c.paid=true;path.add(new SegmentVisual(permit.origin,slot.target.surface(),MINED));
            c.identity.state().setCooldown(ID,1);
            boolean inventoryValid=nativeInventoryTransition(c);
            if(!inventoryValid||!valid(c)||!Objects.equals(hand(p),permit.origin)||!result.reason().isEmpty()){finish(c);return;}
        }
    }
    private static boolean miningVisible(Context c,BeamEtchingMining.Target target,Vec3 origin){
        ServerPlayer p=c.identity.owner();if(target==null||!finite(origin)||!footprint(c,target.surface())||!DisintegrationSweepGeometry.permittedOrigin(c.plane,vector(origin))
                ||origin.distanceToSqr(target.surface())>RANGE*RANGE+1e-9||!p.serverLevel().hasChunkAt(target.pos())||p.serverLevel().getBlockState(target.pos())!=target.state())return false;
        // Nudge into the block along this ray, not along the face normal: a normal offset leaves the line of sight and,
        // at the shallow angles of ordinary ground aiming, lands the re-cast hit well outside the surface tolerance.
        Vec3 inward=target.surface().add(target.surface().subtract(origin).normalize().scale(.0001));
        BlockHitResult surface=firstSurface(p,origin,origin.distanceToSqr(inward)<=RANGE*RANGE?inward:target.surface());
        if(surface==null||surface.getType()!=HitResult.Type.BLOCK||surface.isInside()||!surface.getBlockPos().equals(target.pos())||surface.getDirection()!=target.face()
                ||surface.getLocation().distanceToSqr(target.surface())>1e-8)return false;
        NativeHit obstruction=nativeTrace(p,origin,target.surface());
        return obstruction.permitted&&obstruction.target==null&&obstruction.status==BLOCKED&&obstruction.point.distanceToSqr(target.surface())<.01;
    }
    private static BlockHitResult firstSurface(ServerPlayer p,Vec3 start,Vec3 end){
        if(!finite(start)||!finite(end)||start.distanceToSqr(end)>RANGE*RANGE+1e-6)return null;
        int pieces=Math.max(1,(int)Math.ceil(start.distanceTo(end)/.25));Vec3 previous=start;
        for(int i=1;i<=pieces;i++){
            Vec3 next=start.lerp(end,(double)i/pieces);if(!permittedVolume(p,new AABB(previous,next).inflate(.00001)))return null;
            countAcquisitionPrefix(p,false);BlockHitResult hit=p.serverLevel().clip(new ClipContext(previous,next,ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,p));
            if(hit.getType()==HitResult.Type.BLOCK)return hit;previous=next;
        }
        // Vanilla clip excludes a terminal face. Query one representable step across it,
        // then admit only a contact still on the original closed segment and within 48m.
        Vec3 beyond=new Vec3(Math.nextAfter(end.x,end.x+(end.x-start.x)),Math.nextAfter(end.y,end.y+(end.y-start.y)),Math.nextAfter(end.z,end.z+(end.z-start.z)));
        if(!beyond.equals(end)&&permittedVolume(p,new AABB(end,beyond).inflate(.00001))){
            countAcquisitionPrefix(p,false);BlockHitResult hit=p.serverLevel().clip(new ClipContext(start,beyond,ClipContext.Block.OUTLINE,ClipContext.Fluid.ANY,p));
            if(hit.getType()==HitResult.Type.BLOCK&&start.distanceToSqr(hit.getLocation())<=start.distanceToSqr(end)&&start.distanceToSqr(hit.getLocation())<=RANGE*RANGE)return hit;
        }
        return null;
    }
    /** Loaded, permission-checked native prefixes; solids win exact body/contact ties. */
    private static NativeHit nativeTrace(ServerPlayer p,Vec3 origin,Vec3 endpoint){
        if(!finite(origin)||!finite(endpoint)||origin.distanceToSqr(endpoint)>RANGE*RANGE+1e-8)return new NativeHit(origin,null,null,REFUSED,false,false);
        int pieces=Math.max(1,(int)Math.ceil(origin.distanceTo(endpoint)/.25));Vec3 previous=origin;Set<Entity> scanned=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=1;i<=pieces;i++){
            Vec3 next=origin.lerp(endpoint,(double)i/pieces);AABB query=outward(new AABB(previous,next).inflate(RADIUS));
            if(!permittedVolume(p,query))return new NativeHit(previous,null,null,REFUSED,false,false);
            countAcquisitionPrefix(p,true);Vec3 end=next;Entity target=null;AABB blocker=null;double nearest=origin.distanceToSqr(next);List<Entity> entities=new ArrayList<>();
            p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class),query,e->e!=p&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()&&e.canBeHitByProjectile(),entities,65);
            scanned.addAll(entities);if(scanned.size()>64)return new NativeHit(previous,null,null,REFUSED,false,false);
            for(Entity entity:entities){
                Optional<Vec3> hit=clipInclusive(entity.getBoundingBox().inflate(RADIUS),origin,next);
                if(hit.isPresent()&&(origin.distanceToSqr(hit.get())<nearest||origin.distanceToSqr(hit.get())==nearest&&(target==null||entity.getId()<target.getId()))){end=hit.get();nearest=origin.distanceToSqr(end);target=entity;blocker=entity.getBoundingBox();}
            }
            Vec3 solidPoint=null;AABB solidBounds=null;
            for(var shape:p.serverLevel().getBlockCollisions(p,query))for(AABB solid:shape.toAabbs()){
                Optional<Vec3> hit=clipInclusive(solid.inflate(RADIUS),origin,end);
                if(hit.isPresent()&&origin.distanceToSqr(hit.get())<=nearest){nearest=origin.distanceToSqr(hit.get());solidPoint=hit.get();solidBounds=solid;}
            }
            if(solidPoint!=null)return new NativeHit(solidPoint,null,solidBounds,BLOCKED,true,true);
            if(target!=null)return new NativeHit(end,target,blocker,REFUSED,true,true);previous=next;
        }return new NativeHit(endpoint,null,null,MISS,true,false);
    }
    private static Optional<Vec3> clipInclusive(AABB box,Vec3 start,Vec3 end){
        double near=0,far=1;double[] origins={start.x,start.y,start.z},delta={end.x-start.x,end.y-start.y,end.z-start.z};double[] min={box.minX,box.minY,box.minZ},max={box.maxX,box.maxY,box.maxZ};
        for(int i=0;i<3;i++){if(delta[i]==0){if(origins[i]<min[i]||origins[i]>max[i])return Optional.empty();continue;}double a=(min[i]-origins[i])/delta[i],b=(max[i]-origins[i])/delta[i];near=Math.max(near,Math.min(a,b));far=Math.min(far,Math.max(a,b));if(near>far)return Optional.empty();}
        return near>=0&&near<=1?Optional.of(start.lerp(end,near)):Optional.empty();
    }
    private static boolean canAct(ServerPlayer p){
        if(!thread(p))return false;var state=AbilityStorage.get(p);
        return p.connection!=null&&!p.hasDisconnected()&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&p.containerMenu==p.inventoryMenu
                &&HighPowerResources.ready(p)&&!p.getAbilities().instabuild&&!p.getAbilities().invulnerable&&state.activated&&state.overloadFine&&!state.interfering
                &&state.learned(ID)&&state.learned(DualPoint.ID)&&state.learned(FoldedMirrorArray.ID)&&state.category.equals("meltdowner")&&state.level>=3&&state.level<=5
                &&HighPowerResources.effectiveLevel(p)>=3&&SkillCatalog.enabled("meltdowner",ID)&&HighPowerCombat.unsupportedReason(p).isEmpty()
                &&!AbilityConsumption.busy(p)&&!p.noPhysics&&!p.isPassenger()&&!p.isSleeping()&&finite(p.position())&&finite(p.getEyePosition())
                &&finite(p.getDeltaMovement())&&finite(p.getLookAngle())&&p.getLookAngle().lengthSqr()>.5&&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())
                &&DisintegrationSweepRules.clock(now(p))&&permittedVolume(p,p.getBoundingBox());
    }
    private static boolean valid(Context c){
        ServerPlayer p=c.identity.owner();return owned(p)==c&&c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canAct(p)
                &&now(p)>=c.started&&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision()
                &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))
                &&(c.released<0||c.plane!=null&&now(p)>=c.released&&p.position().distanceToSqr(c.releasePosition)<=.25*.25
                &&finite(hand(p))&&DisintegrationSweepGeometry.permittedOrigin(c.plane,vector(hand(p))));
    }
    private static boolean identity(TargetSlot slot,ServerPlayer p){return slot.entity.getId()==slot.id&&slot.entity.getUUID().equals(slot.uuid)
            &&slot.entity.level()==p.serverLevel()&&p.serverLevel().getEntity(slot.id)==slot.entity&&p.serverLevel().getEntity(slot.uuid)==slot.entity;}
    private static boolean harmPermitted(ServerPlayer p,LivingEntity target){
        ServerLevel world=p.serverLevel();if(target==null||target==p||target.level()!=world||!target.isAlive()||target.isRemoved()||target.isSpectator()
                ||world.getEntity(target.getId())!=target||world.getEntity(target.getUUID())!=target||!HighPowerCombat.supported(target)
                ||HighPowerCombat.unsupportedReason(target).isPresent()||target.isInvulnerable()||target.getPersistentData().getBoolean("academy:protected")
                ||p.isAlliedTo(target)||target.isAlliedTo(p)||!permittedVolume(p,target.getBoundingBox()))return false;
        return !(target instanceof Player)||target.getClass()==ServerPlayer.class&&target instanceof ServerPlayer other&&other.connection!=null&&!other.hasDisconnected()
                &&!other.getAbilities().invulnerable&&AcademyConfig.ATTACK_PLAYERS.get()&&p.server.isPvpAllowed()&&p.canHarmPlayer(other)&&other.canHarmPlayer(p);
    }
    private static boolean loadedVolume(ServerPlayer p,AABB box){
        ServerLevel world=p.serverLevel();if(!finite(new Vec3(box.minX,box.minY,box.minZ))||!finite(new Vec3(box.maxX,box.maxY,box.maxZ))
                ||box.minY<world.getMinBuildHeight()||box.maxY>=world.getMaxBuildHeight()||!world.getWorldBorder().isWithinBounds(box))return false;
        for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++)for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++)if(!world.hasChunk(x,z))return false;return true;
    }
    private static boolean permittedVolume(ServerPlayer p,AABB box){
        if(box.getXsize()>8||box.getYsize()>8||box.getZsize()>8||!loadedVolume(p,box))return false;
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(box.minX,box.minY,box.minZ),BlockPos.containing(box.maxX,box.maxY,box.maxZ)))if(!p.serverLevel().mayInteract(p,pos))return false;return true;
    }
    private static boolean clearHand(ServerPlayer p){NativeHit hit=nativeTrace(p,p.getEyePosition(),hand(p));return hit.permitted&&!hit.contact;}
    private static boolean footprint(Context c,Vec3 point){return finite(point)&&c.plane!=null&&DisintegrationSweepGeometry.withinFrozenFootprint(c.plane,vector(point));}
    private static double angleOf(Context c,Vec3 origin,Vec3 point){Vec3 delta=point.subtract(origin);return Math.atan2(delta.dot(vec(c.plane.across())),delta.dot(vec(c.plane.forward())));}
    public static Vec3 hand(ServerPlayer p){return DualPoint.hand(p,0);}
    private static List<ItemStack> inventory(ServerPlayer p){List<ItemStack> items=new ArrayList<>();for(int i=0;i<p.getInventory().getContainerSize();i++)items.add(p.getInventory().getItem(i).copy());return items;}
    private static boolean inventoryUnchanged(Context c){ServerPlayer p=c.identity.owner();if(p.getInventory().selected!=c.selected||p.getInventory().getContainerSize()!=c.inventory.size())return false;
        for(int i=0;i<c.inventory.size();i++)if(!ItemStack.matches(c.inventory.get(i),p.getInventory().getItem(i)))return false;return true;}
    private static boolean nativeInventoryTransition(Context c){ServerPlayer p=c.identity.owner();if(p.getInventory().selected!=c.selected||p.getInventory().getContainerSize()!=c.inventory.size())return false;
        for(int i=0;i<c.inventory.size();i++){ItemStack before=c.inventory.get(i),after=p.getInventory().getItem(i);if(ItemStack.matches(before,after))continue;
            if(i!=c.selected||before.isEmpty()||!before.isDamageableItem())return false;
            if(after.isEmpty()){if(before.getDamageValue()+1<before.getMaxDamage())return false;continue;}
            if(!after.isDamageableItem()||after.getDamageValue()!=before.getDamageValue()+1)return false;ItemStack permitted=before.copy();permitted.setDamageValue(after.getDamageValue());if(!ItemStack.matches(permitted,after))return false;}
        c.inventory=inventory(p);return true;}
    private static void preview(Context c){ServerPlayer p=c.identity.owner();if(!fenceReady(p,PREVIEW_AT,4))return;
        stamp(p,PREVIEW_AT);send(c,CHARGING,PREPARED,-HALF,HALF,List.of());}
    private static void reward(Context c){if(c.rewarded||c.successes<1||!valid(c)||!fenceReady(c.identity.owner(),MASTERY_AT,20))return;
        c.rewarded=true;stamp(c.identity.owner(),MASTERY_AT);c.identity.state().addExperience(ID,.005);AbilityStorage.save(c.identity.owner());}
    private static void finish(Context c){if(CONTEXTS.get(c.identity.owner().getUUID())!=c)return;reward(c);if(!CONTEXTS.remove(c.identity.owner().getUUID(),c))return;
        if(c.mining&&c.successes>0&&c.identity.owns(c.identity.owner()))c.identity.state().setCooldown(ID,c.cooldown);
        c.emitting=null;c.digging=null;send(c,ENDED,PREPARED,c.angle,c.angle,List.of());}
    private static boolean fenceReady(ServerPlayer p,String key,int interval){var root=p.getPersistentData();if(root.contains(DATA)&&!root.contains(DATA,Tag.TAG_COMPOUND))return false;
        CompoundTag data=root.getCompound(DATA);if(!data.contains(key))return true;if(!data.contains(key,Tag.TAG_LONG))return false;long before=data.getLong(key);return before>=0&&now(p)>=before&&now(p)-before>=interval;}
    private static void stamp(ServerPlayer p,String key){CompoundTag data=p.getPersistentData().getCompound(DATA);data.putLong(key,now(p));p.getPersistentData().put(DATA,data);}
    private static BigDecimal health(LivingEntity target){return HighPowerCombat.enabled(target)?new BigDecimal(HighPowerCombat.healthText(target)):BigDecimal.valueOf(target.getHealth());}
    private static void send(Context c,int phase,int status,double from,double to,List<SegmentVisual> path){
        ServerPlayer p=c.identity.owner();c.lastVisual=now(p);FrozenPlane frame=c.plane;
        if(frame==null){
            if(phase==CHARGING&&finite(hand(p))&&finite(p.getLookAngle()))c.previewPlane=DisintegrationSweepGeometry.freeze(vector(hand(p)),vector(p.getLookAngle()),Math.toRadians(p.getYRot()),c.mining).orElse(c.previewPlane);
            frame=c.previewPlane;
        }
        if(frame==null)return;int remaining=phase==ENDED?0:c.released<0?(int)Math.max(0,100-(now(p)-c.started)):(int)Math.max(0,10-(now(p)-c.released));
        Visual visual=new Visual(p.getId(),p.getUUID(),c.input.epoch(),c.input.input(),c.identity.token(),++c.sequence,phase,status,remaining,c.mining,
                finite(p.position())?p.position():vec(frame.origin()),vec(frame.origin()),vec(frame.forward()),vec(frame.across()),from,to,path,phase==ENDED?List.of():c.cells.stream().map(slot->mark(slot.target)).toList());
        if(phase!=ENDED){c.audience.add(p);for(ServerPlayer observer:c.identity.level().players())if(observer.distanceToSqr(p)<=96*96)c.audience.add(observer);}
        for(ServerPlayer observer:c.audience)if(observer.connection!=null&&!observer.hasDisconnected()&&observer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(observer,visual);
    }
    private static Mark mark(BeamEtchingMining.Target target){return new Mark(target.pos(),target.surface());}
    private static void countAcquisitionPrefix(ServerPlayer p,boolean nativeShapes){Context c=owned(p);if(c!=null&&c.freezing){if(nativeShapes)c.acquisitionNativePrefixes++;else c.acquisitionSurfacePrefixes++;}}
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static long now(ServerPlayer p){return p.serverLevel().getServer().overworld().getGameTime();}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=30_000_000&&Math.abs(v.y)<=30_000_000&&Math.abs(v.z)<=30_000_000;}
    private static Vector vector(Vec3 v){return new Vector(v.x,v.y,v.z);}
    private static Vec3 vec(Vector v){return new Vec3(v.x(),v.y(),v.z());}
    private static Bounds bounds(AABB b){return new Bounds(new Vector(b.minX,b.minY,b.minZ),new Vector(b.maxX,b.maxY,b.maxZ));}
    private static AABB box(Bounds b){return new AABB(vec(b.min()),vec(b.max()));}
    private static AABB outward(AABB b){return new AABB(Math.nextDown(b.minX),Math.nextDown(b.minY),Math.nextDown(b.minZ),Math.nextUp(b.maxX),Math.nextUp(b.maxY),Math.nextUp(b.maxZ));}
    private static Vec3 endpoint(Vec3 origin,Vec3 direction,double distance){Vec3 end=origin.add(direction.scale(distance));for(int i=0;i<8&&origin.distanceToSqr(end)>distance*distance;i++)end=new Vec3(Math.nextAfter(end.x,origin.x),Math.nextAfter(end.y,origin.y),Math.nextAfter(end.z,origin.z));return end;}
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public record SegmentVisual(Vec3 start,Vec3 end,int status){public SegmentVisual{if(!finite(start)||!finite(end)||start.distanceToSqr(end)>RANGE*RANGE+1e-7||status<MISS||status>MINED)throw new IllegalArgumentException("Invalid NEW231 segment");}}
    public record Mark(BlockPos pos,Vec3 surface){public Mark{if(pos==null||!finite(surface)||!new AABB(pos).inflate(.0001).contains(surface))throw new IllegalArgumentException("Invalid NEW231 mark");pos=pos.immutable();}}
    public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int phase,int status,int remainingTicks,boolean mining,
                         Vec3 ownerFeet,Vec3 origin,Vec3 forward,Vec3 across,double fromAngle,double toAngle,List<SegmentVisual> path,List<Mark> marks) implements CustomPacketPayload {
        public Visual{path=List.copyOf(path);marks=List.copyOf(marks);
            if(owner<0||ownerUuid==null||epoch<=0||input<=0||token<=0||sequence<=0||phase<CHARGING||phase>ENDED||status<PREPARED||status>MINED
                    ||remainingTicks<0||remainingTicks>100||!finite(ownerFeet)||!finite(origin)||!finite(forward)||!finite(across)
                    ||Math.abs(forward.length()-1)>1e-9||Math.abs(across.length()-1)>1e-9||Math.abs(forward.dot(across))>1e-9
                    ||!Double.isFinite(fromAngle)||!Double.isFinite(toAngle)||fromAngle<-HALF-1e-10||toAngle>HALF+1e-10||fromAngle>toAngle
                    ||path.size()>(mining?2:8)||marks.size()>16||!mining&&!marks.isEmpty()||phase==ENDED&&(!path.isEmpty()||!marks.isEmpty()))throw new IllegalArgumentException("Invalid bounded NEW231 visual");
            FrozenPlane frame=new FrozenPlane(vector(origin),vector(forward),vector(across));
            for(SegmentVisual segment:path)if(segment.start.distanceToSqr(origin)>.25*.25+1e-9||!DisintegrationSweepGeometry.withinFrozenFootprint(frame,vector(segment.end)))throw new IllegalArgumentException("NEW231 segment outside frozen fan");
            for(Mark mark:marks)if(!DisintegrationSweepGeometry.withinFrozenFootprint(frame,vector(mark.surface)))throw new IllegalArgumentException("NEW231 mark outside frozen fan");
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","disintegration_sweep_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.owner);b.writeUUID(v.ownerUuid);b.writeLong(v.epoch);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.sequence);b.writeByte(v.phase);b.writeByte(v.status);b.writeShort(v.remainingTicks);b.writeBoolean(v.mining);
            writeVec(b,v.ownerFeet);writeVec(b,v.origin);writeVec(b,v.forward);writeVec(b,v.across);b.writeDouble(v.fromAngle);b.writeDouble(v.toAngle);b.writeByte(v.path.size());
            for(SegmentVisual segment:v.path){writeVec(b,segment.start);writeVec(b,segment.end);b.writeByte(segment.status);}b.writeByte(v.marks.size());for(Mark mark:v.marks){b.writeBlockPos(mark.pos);writeVec(b,mark.surface);}
        },b->{int owner=b.readInt();UUID uuid=b.readUUID();long epoch=b.readLong(),input=b.readLong(),token=b.readLong(),sequence=b.readLong();int phase=b.readUnsignedByte(),status=b.readUnsignedByte(),remaining=b.readUnsignedShort();boolean mining=b.readBoolean();
            Vec3 feet=readVec(b),origin=readVec(b),forward=readVec(b),across=readVec(b);double from=b.readDouble(),to=b.readDouble();int count=b.readUnsignedByte();if(count>(mining?2:8))throw new IllegalArgumentException("Unbounded NEW231 segments");List<SegmentVisual> path=new ArrayList<>();
            for(int i=0;i<count;i++)path.add(new SegmentVisual(readVec(b),readVec(b),b.readUnsignedByte()));count=b.readUnsignedByte();if(count>16)throw new IllegalArgumentException("Unbounded NEW231 marks");List<Mark> marks=new ArrayList<>();for(int i=0;i<count;i++)marks.add(new Mark(b.readBlockPos(),readVec(b)));
            return new Visual(owner,uuid,epoch,input,token,sequence,phase,status,remaining,mining,feet,origin,forward,across,from,to,path,marks);});
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network{@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.level().getServer()==e.getServer())tick(c.identity.owner());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p){if(e.getEntity() instanceof ServerPlayer replacement&&p.getPersistentData().contains(DATA))replacement.getPersistentData().put(DATA,p.getPersistentData().get(DATA).copy());remove(p);}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
}
