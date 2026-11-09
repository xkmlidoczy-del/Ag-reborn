package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.MulticoreCanopyGeometry;
import cn.academy.port.highpower.core.MulticoreCanopyGeometry.Core;
import cn.academy.port.highpower.core.MulticoreCanopyRules;
import cn.academy.port.highpower.core.MulticoreCanopyRules.Work;
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

/** NEW251: six real frozen origins, immutable identity slots and one paid decimal 4D plan. */
@EventBusSubscriber(modid="academy")
public final class MulticoreCanopy {
    public static final String ID="multicore_canopy";
    private static final boolean DIAGNOSTIC=Boolean.getBoolean("academy.highpower.multicoreCanopyDiagnostic");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG=org.slf4j.LoggerFactory.getLogger("academy.multicore_canopy_diagnostics");
    private static int diagnosticEvents;
    public static final int MIN_LEVEL=5,MAX_LEVEL=5,DEPLOY=0,ARMED=1,WARNING=2,HELD=3,FIRE=4,ENDED=5;
    public static final int PREPARED=0,MISS=1,BLOCKED=2,REFUSED=3,DAMAGED=4,ABSORBED=5,LOST=6;
    private static final String DATA="academy:multicore_canopy",FIRE_FENCE="fireRemaining",INPUT_FENCE="inputRemaining",PREVIEW_FENCE="previewRemaining";
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Map<ServerPlayer,Receipt> RECEIPTS=new WeakHashMap<>();
    private static final Set<ServerPlayer> PROCESSING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static long nextToken,physicalTick;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input) {}
    private record NativeHit(Vec3 point,Entity target,int status,boolean permitted,boolean contact) {}
    private static final class CoreState {final Core core;boolean available=true;CoreState(Core core){this.core=core;}}
    private static final class TargetState {
        final LivingEntity entity;final UUID uuid;final int id;Vec3 lastVisible;long invisibleAt=-1;boolean retired;
        TargetState(LivingEntity e,Vec3 point){entity=e;uuid=e.getUUID();id=e.getId();lastVisible=point;}
    }
    private record Slot(int index,int coreId,TargetState target,BigDecimal damage) {}
    private record FrozenTarget(Slot slot,Vec3 origin,Vec3 endpoint,Vec3 rayEnd,Vec3 position,AABB bounds,
                                Vec3 ownerPosition,Vec3 ownerEye,Vec3 ownerLook,long tick) {}
    public record Receipt(List<SegmentVisual> path,int attempts,int successes,boolean paid,boolean held,int frozenTargets,
                          int slots,int emittedSlots,BigDecimal damage,BigDecimal cost){public Receipt{path=List.copyOf(path);}}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;final int preset;final long revision,anchored;final List<Core> candidates;
        final List<CoreState> cores=new ArrayList<>(6);final Set<ServerPlayer> audience=new LinkedHashSet<>();
        HighPowerInput.Input input;boolean deployHeld=true,firstReleased,fireHeld,modeChosen,held,paid,processing,rewarded;
        int nextCandidate,slotIndex,attempts,successes;long lastDeploy=-1,fireStarted=-1,lastEmission=-1,lastTick=-1,lastVisual=-1,sequence;
        List<TargetState> roster=List.of();List<Integer> coreRoster=List.of();List<Slot> slots=List.of();
        BigDecimal damage=BigDecimal.ZERO,cost=BigDecimal.ZERO;int cooldown;Work work;EmissionPermit emitting;
        Context(ServerPlayer p,HighPowerInput.Input input){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));this.input=input;
            preset=identity.state().presets.current();revision=identity.state().presets.revision();anchored=physicalTick;
            candidates=MulticoreCanopyGeometry.cores(vector(p.getEyePosition()),vector(p.getLookAngle().normalize()));
        }
    }
    /** Private construction and one-use consumption close every emission over its actual native origin. */
    public static final class EmissionPermit {
        private final Context context;private final FrozenTarget frozen;private boolean consumed;
        private EmissionPermit(Context c,FrozenTarget frozen){context=c;this.frozen=frozen;}
        ServerPlayer player(){return context.identity.owner();}LivingEntity target(){return frozen.slot.target.entity;}
        BigDecimal damage(){return frozen.slot.damage;}Vec3 finalOrigin(){return frozen.origin;}Vec3 endpoint(){return frozen.endpoint;}
        boolean consume(){if(consumed||!valid())return false;consumed=true;return true;}
        boolean valid(){return context.emitting==this&&context.processing&&context.paid&&frozenValid(context,frozen);}
    }
    /** Also used directly before first payment, after all preparation callbacks have returned. */
    private static boolean frozenValid(Context c,FrozenTarget frozen){
        ServerPlayer p=c.identity.owner();TargetState target=frozen.slot.target;Work work=c.work;
        if(!c.processing||work==null||!work.valid()||owned(p)!=c||!valid(c)
                ||physicalTick!=frozen.tick||!ownerPose(p,frozen)||!targetState(p,target)||target.invisibleAt>=0
                ||!target.entity.position().equals(frozen.position)||!target.entity.getBoundingBox().equals(frozen.bounds))return false;
        CoreState core=core(c,frozen.slot.coreId);
        if(core==null||!core.available||!vec(core.core.center()).equals(frozen.origin)||!coreClear(c,core,work)
                ||!harmPermitted(p,target.entity,work)||!range(p,frozen.origin,frozen.endpoint))return false;
        NativeHit visible=nativeTrace(p,p.getEyePosition(),target.entity.getBoundingBox().getCenter(),false,MulticoreCanopyRules.BEAM_RADIUS,work);
        if(!visible.permitted||visible.target!=target.entity||!contextState(c)||!ownerPose(p,frozen))return false;
        NativeHit hit=nativeTrace(p,frozen.origin,frozen.rayEnd,true,MulticoreCanopyRules.BEAM_RADIUS,work);
        return work.valid()&&hit.permitted&&hit.target==target.entity&&hit.point.equals(frozen.endpoint)&&contextState(c)
                &&ownerPose(p,frozen)&&physicalTick==frozen.tick&&targetState(p,target)
                &&target.entity.position().equals(frozen.position)&&target.entity.getBoundingBox().equals(frozen.bounds)
                &&range(p,frozen.origin,frozen.endpoint);
    }
    private MulticoreCanopy() {}

    public static boolean start(ServerPlayer p,String wire){
        if(PROCESSING.contains(p))return false;var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context c=owned(p);if(c!=null&&c.processing)return false;
        Watermark prior=INPUTS.get(p.getUUID());if(prior!=null&&prior.epoch==input.epoch()&&input.input()<=prior.input)return false;
        if(c!=null&&!valid(c)){finish(c);c=null;}
        if(c!=null&&(c.deployHeld||c.fireHeld||c.input.epoch()!=input.epoch()||c.input.slot()!=input.slot()||c.fireStarted>=0&&!p.isShiftKeyDown()))return false;
        if(!canAct(p)||!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))||!fenceReady(p,INPUT_FENCE)||nextToken==Long.MAX_VALUE)return false;
        INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));stamp(p,INPUT_FENCE,MulticoreCanopyRules.PREVIEW_INTERVAL);
        if(c!=null&&p.isShiftKeyDown()){c.input=input;finish(c);return true;}
        if(!AbilityStorage.get(p).canUse(ID))return false;
        if(c==null){
            Context old=CONTEXTS.get(p.getUUID());if(old!=null)finish(old);c=new Context(p,input);CONTEXTS.put(p.getUUID(),c);RECEIPTS.remove(p);
            guardedDeploy(c);if(owned(p)==c)preview(c);return owned(p)==c;
        }
        if(!c.firstReleased)return false;
        c.input=input;c.fireHeld=true;c.fireStarted=physicalTick;c.processing=true;PROCESSING.add(p);c.work=new Work();
        try{
            maintainCores(c,c.work);if(!valid(c)){finish(c);return false;}
            c.coreRoster=c.cores.stream().filter(core->core.available).map(core->core.core.id()).toList();
            var growth=HighPowerGrowth.values(p);c.damage=MulticoreCanopyRules.damage(growth.damage());c.cost=MulticoreCanopyRules.theoreticalFireCost(growth.position());c.cooldown=MulticoreCanopyRules.cooldownTicks(growth.position());
            c.roster=acquire(c,c.work);
            if(!c.work.valid()||!valid(c)){finish(c);return false;}
            if(c.roster.isEmpty()||c.coreRoster.isEmpty()){record(c,List.of());send(c,FIRE,MISS,List.of());finish(c);return true;}
            send(c,WARNING,PREPARED,List.of());return true;
        }finally{c.work=null;c.processing=false;PROCESSING.remove(p);}
    }
    public static boolean release(ServerPlayer p,String wire){
        Context c=owned(p);var input=HighPowerInput.parse(wire);
        if(c==null||c.processing||!HighPowerBracerSession.authenticated(p,input)||!c.input.equals(input)||!valid(c))return false;
        if(c.deployHeld){c.deployHeld=false;c.firstReleased=true;if(c.cores.isEmpty())finish(c);else send(c,ARMED,PREPARED,List.of());return true;}
        if(c.fireStarted<0||!c.fireHeld)return false;c.fireHeld=false;
        if(c.modeChosen&&c.held||physicalTick-c.fireStarted>=MulticoreCanopyRules.WARNING_TICKS){finish(c);return true;}
        chooseMode(c,false);send(c,WARNING,PREPARED,List.of());return true;
    }
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;Context c=owned(p);
        boolean owns=c!=null&&c.input.epoch()==input.epoch()&&c.input.slot()==input.slot()&&input.input()>=c.input.input();
        if(!owns&&!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())))return false;
        Watermark prior=INPUTS.get(p.getUUID());boolean retired=prior==null||prior.epoch!=input.epoch()||input.input()>prior.input;
        if(retired)INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));if(owns)finish(c);return owns||retired;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c);}
    public static void remove(ServerPlayer p){if(thread(p)){abort(p);INPUTS.remove(p.getUUID());RECEIPTS.remove(p);}}
    public static void clear(){for(Context c:new ArrayList<>(CONTEXTS.values()))finish(c);INPUTS.clear();PROCESSING.clear();RECEIPTS.clear();}
    public static boolean active(ServerPlayer p){Context c=owned(p);if(c!=null&&!valid(c)){finish(c);return false;}return c!=null;}
    public static boolean holding(ServerPlayer p){Context c=owned(p);return c!=null&&(c.deployHeld||c.fireHeld)&&valid(c);}
    public static List<CoreVisual> cores(ServerPlayer p){Context c=owned(p);return c==null?List.of():coreVisuals(c);}
    public static List<TargetVisual> targets(ServerPlayer p){Context c=owned(p);return c==null?List.of():targetVisuals(c);}
    public static long anchoredAt(ServerPlayer p){Context c=owned(p);return c==null?-1:c.anchored;}
    public static int remainingTicks(ServerPlayer p){Context c=owned(p);return c==null?0:MulticoreCanopyRules.remaining(c.anchored,physicalTick);}
    public static Receipt receipt(ServerPlayer p){return RECEIPTS.get(p);}
    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.processing)return;
        if(!valid(c)||physicalTick<c.lastTick){finish(c);return;}if(physicalTick==c.lastTick)return;c.lastTick=physicalTick;
        c.processing=true;PROCESSING.add(p);c.work=new Work();
        try{
            maintainCores(c,c.work);if(!valid(c)){finish(c);return;}
            if(c.deployHeld&&c.nextCandidate<6&&MulticoreCanopyRules.ready(c.lastDeploy,physicalTick,5))deploy(c,c.work);
            if(!c.work.valid()){finish(c);return;}
            if(c.fireStarted>=0){
                maintainTargets(c,c.work);if(!valid(c)){finish(c);return;}
                if(physicalTick-c.fireStarted>=MulticoreCanopyRules.WARNING_TICKS){
                    if(!c.modeChosen)chooseMode(c,true);
                    if(c.held&&!c.fireHeld){finish(c);return;}
                    if(c.held){if(MulticoreCanopyRules.ready(c.lastEmission,physicalTick,5))emitOpportunity(c,true);}
                    else emitOpportunity(c,false);
                }
            }
            if(owned(p)==c)preview(c);
        }finally{c.emitting=null;c.work=null;c.processing=false;PROCESSING.remove(p);}
    }
    private static void guardedDeploy(Context c){
        ServerPlayer p=c.identity.owner();c.processing=true;PROCESSING.add(p);c.work=new Work();
        try{deploy(c,c.work);if(!c.work.valid())finish(c);}finally{c.work=null;c.processing=false;PROCESSING.remove(p);}
    }
    private static void deploy(Context c,Work work){
        c.lastDeploy=physicalTick;CoreState candidate=new CoreState(c.candidates.get(c.nextCandidate++));ServerPlayer p=c.identity.owner();
        if(!coreClear(c,candidate,work)||!valid(c)||!AbilityStorage.get(p).canUse(ID))return;
        Vec3 position=p.position(),eye=p.getEyePosition(),look=p.getLookAngle();
        double cost=MulticoreCanopyRules.deploymentCost(HighPowerGrowth.values(p).position());
        if(!contextState(c)||!position.equals(p.position())||!eye.equals(p.getEyePosition())||!look.equals(p.getLookAngle()))return;
        if(!HighPowerResources.trySpend(p,ID,cost,1))return;
        // Spending itself has no native callbacks. The accepted identity exists immediately;
        // any later environmental change may only retire it, never silently buy another core.
        c.cores.add(candidate);if(!contextState(c))candidate.available=false;
        diagnostic("NEW251 stage=deployed token={} tick={} core={} origin={} accepted={} placementCost={}",c.identity.token(),physicalTick,candidate.core.id(),vec(candidate.core.center()),candidate.available,cost);
        send(c,DEPLOY,PREPARED,List.of());
    }
    private static void chooseMode(Context c,boolean held){
        c.modeChosen=true;c.held=held;int count=held?c.roster.size():c.cores.size();List<BigDecimal> shares=MulticoreCanopyRules.shares(c.damage,count);List<Slot> slots=new ArrayList<>(count);
        for(int i=0;i<count;i++)slots.add(new Slot(i,held?c.coreRoster.get(i%c.coreRoster.size()):c.cores.get(i).core.id(),c.roster.get(i%c.roster.size()),shares.get(i)));
        c.slots=List.copyOf(slots);
    }
    private static List<TargetState> acquire(Context c,Work work){
        ServerPlayer p=c.identity.owner();ServerLevel world=c.identity.level();Vec3 eye=p.getEyePosition(),look=p.getLookAngle().normalize(),position=p.position();
        List<Entity> raw=new ArrayList<>();world.getEntities(EntityTypeTest.forClass(Entity.class),new AABB(eye,eye).inflate(96),e->true,raw,65);
        if(!work.entities(raw.size())||raw.size()>64||!sameWorld(p,world,work)||!contextState(c)||!position.equals(p.position())||!eye.equals(p.getEyePosition())||!look.equals(p.getLookAngle().normalize())){work.refuse();return List.of();}
        List<LivingEntity> candidates=new ArrayList<>();
        for(Entity entity:raw)if(entity instanceof LivingEntity target&&harmPermitted(p,target,work)
                &&MulticoreCanopyGeometry.inCone(vector(eye),vector(look),vector(target.getBoundingBox().getCenter())))candidates.add(target);
        candidates.sort(Comparator.comparingDouble((LivingEntity target)->eye.distanceToSqr(target.getBoundingBox().getCenter())).thenComparingInt(Entity::getId));
        List<TargetState> result=new ArrayList<>();Set<UUID> ids=new HashSet<>();
        for(LivingEntity target:candidates){
            if(!sameWorld(p,world,work)||!contextState(c)||!position.equals(p.position())||!eye.equals(p.getEyePosition())||!look.equals(p.getLookAngle().normalize())){work.refuse();return List.of();}
            Vec3 center=target.getBoundingBox().getCenter();NativeHit hit=nativeTrace(p,eye,center,false,MulticoreCanopyRules.BEAM_RADIUS,work);
            if(hit.permitted&&hit.target==target&&harmState(p,target)&&ids.add(target.getUUID()))result.add(new TargetState(target,center));
            if(!work.valid())return List.of();if(result.size()==16)break;
        }
        return List.copyOf(result);
    }
    private static void maintainCores(Context c,Work work){for(CoreState core:c.cores){if(core.available)coreClear(c,core,work);if(!work.valid()||!contextState(c))return;}}
    private static void maintainTargets(Context c,Work work){
        ServerPlayer p=c.identity.owner();
        for(TargetState target:c.roster){
            if(target.retired)continue;if(!targetState(p,target)){target.retired=true;continue;}
            Vec3 center=target.entity.getBoundingBox().getCenter();
            NativeHit hit=nativeTrace(p,p.getEyePosition(),center,false,MulticoreCanopyRules.BEAM_RADIUS,work);
            if(!work.valid()||!contextState(c))return;
            if(hit.permitted&&hit.target==target.entity&&targetState(p,target)){
                if(target.invisibleAt>=0&&physicalTick-target.invisibleAt>=10){target.retired=true;continue;}
                target.invisibleAt=-1;target.lastVisible=center;
            }else{if(target.invisibleAt<0)target.invisibleAt=physicalTick;if(physicalTick-target.invisibleAt>=10)target.retired=true;}
        }
    }
    private static void emitOpportunity(Context c,boolean held){
        ServerPlayer p=c.identity.owner();c.lastEmission=physicalTick;
        Vec3 ownerPosition=p.position(),ownerEye=p.getEyePosition(),ownerLook=p.getLookAngle();
        int end=held?Math.min(c.slots.size(),c.slotIndex+1):c.slots.size();List<FrozenTarget> prepared=new ArrayList<>();
        for(int i=c.slotIndex;i<end;i++){
            FrozenTarget target=prepare(c,c.slots.get(i),c.work);if(target!=null)prepared.add(target);
            if(!c.work.valid()||!contextState(c)||!pose(p,ownerPosition,ownerEye,ownerLook)){finish(c);return;}
        }
        // Slot assignment advances even on a missing core, target, LOS or native obstacle.
        c.slotIndex=end;
        if(!prepared.isEmpty()&&!c.paid){
            if(!fenceReady(p,FIRE_FENCE)){record(c,List.of());finish(c);return;}
            boolean opportunity=false;
            for(FrozenTarget target:prepared){if(frozenValid(c,target)){opportunity=true;break;}if(!c.work.valid()||!contextState(c))break;}
            if(!c.work.valid()||!contextState(c)||!pose(p,ownerPosition,ownerEye,ownerLook)){record(c,List.of());finish(c);return;}
            if(!opportunity)prepared.clear();
            else{
                if(!AbilityStorage.get(p).canUse(ID)||!HighPowerResources.trySpend(p,ID,c.cost.doubleValue(),c.cooldown)){record(c,List.of());finish(c);return;}
                c.paid=true;stamp(p,FIRE_FENCE,20);HighPowerNaturalRecovery.offensiveAction(p);
                diagnostic("NEW251 stage=paid token={} tick={} held={} cores={} targets={} shares={} budget={} cost={}",c.identity.token(),physicalTick,c.held,c.cores.size(),c.roster.size(),c.slots.size(),c.damage,c.cost);
            }
        }
        List<SegmentVisual> path=new ArrayList<>();
        for(FrozenTarget target:prepared){
            if(!c.paid||!contextState(c)||!c.work.valid())break;
            c.attempts++;EmissionPermit permit=new EmissionPermit(c,target);c.emitting=permit;
            BigDecimal hp=health(target.slot.target.entity),shield=HighPowerCombat.shield(target.slot.target.entity);float absorption=target.slot.target.entity.getAbsorptionAmount();boolean accepted;
            try{accepted=HighPowerCombat.attackMulticoreCanopy(permit);}finally{c.emitting=null;}
            int status=REFUSED;
            if(accepted&&health(target.slot.target.entity).compareTo(hp)<0)status=DAMAGED;
            else if(accepted&&(HighPowerCombat.shield(target.slot.target.entity).compareTo(shield)<0||target.slot.target.entity.getAbsorptionAmount()<absorption))status=ABSORBED;
            if(status==DAMAGED||status==ABSORBED)c.successes++;
            diagnostic("NEW251 stage=settled token={} tick={} core={} slot={} origin={} endpoint={} share={} status={} hpBefore={} hpAfter={} shieldBefore={} shieldAfter={}",c.identity.token(),physicalTick,target.slot.coreId,target.slot.index,target.origin,target.endpoint,target.slot.damage,status,hp,health(target.slot.target.entity),shield,HighPowerCombat.shield(target.slot.target.entity));
            path.add(new SegmentVisual(target.slot.coreId,target.slot.index,target.origin,target.endpoint,status));
        }
        record(c,path);if(owned(p)==c)send(c,FIRE,c.successes>0?DAMAGED:prepared.isEmpty()?MISS:REFUSED,path);
        if(c.successes>0&&!c.rewarded&&owned(p)==c&&valid(c)){c.rewarded=true;c.identity.state().addExperience(ID,.005);AbilityStorage.save(p);}
        if(c.slotIndex>=c.slots.size()||!held||!c.work.valid()||!contextState(c))finish(c);
    }
    private static FrozenTarget prepare(Context c,Slot slot,Work work){
        ServerPlayer p=c.identity.owner();TargetState target=slot.target;CoreState core=core(c,slot.coreId);
        if(core==null||!core.available||!targetState(p,target)||target.invisibleAt>=0||!coreClear(c,core,work)||!harmPermitted(p,target.entity,work))return null;
        Vec3 origin=vec(core.core.center()),center=target.entity.getBoundingBox().getCenter(),position=target.entity.position(),ownerPosition=p.position(),eye=p.getEyePosition(),look=p.getLookAngle();AABB bounds=target.entity.getBoundingBox();
        NativeHit visible=nativeTrace(p,eye,center,false,MulticoreCanopyRules.BEAM_RADIUS,work);
        if(!visible.permitted||visible.target!=target.entity)return null;
        NativeHit hit=nativeTrace(p,origin,center,true,MulticoreCanopyRules.BEAM_RADIUS,work);
        if(!hit.permitted||hit.target!=target.entity||!range(p,origin,hit.point)||!work.valid()||!contextState(c)||!targetState(p,target)
                ||!position.equals(target.entity.position())||!bounds.equals(target.entity.getBoundingBox())||!ownerPosition.equals(p.position())||!eye.equals(p.getEyePosition())||!look.equals(p.getLookAngle()))return null;
        return new FrozenTarget(slot,origin,hit.point,center,position,bounds,ownerPosition,eye,look,physicalTick);
    }
    private static void record(Context c,List<SegmentVisual> path){RECEIPTS.put(c.identity.owner(),new Receipt(path,c.attempts,c.successes,c.paid,c.held,c.roster.size(),c.slots.size(),c.slotIndex,c.damage,c.cost));}
    private static CoreState core(Context c,int id){for(CoreState core:c.cores)if(core.core.id()==id)return core;return null;}
    private static boolean pose(ServerPlayer p,Vec3 position,Vec3 eye,Vec3 look){return p.position().equals(position)&&p.getEyePosition().equals(eye)&&p.getLookAngle().equals(look);}
    private static boolean ownerPose(ServerPlayer p,FrozenTarget frozen){return pose(p,frozen.ownerPosition,frozen.ownerEye,frozen.ownerLook);}
    private static boolean targetState(ServerPlayer p,TargetState target){
        if(target.retired)return false;LivingEntity entity=target.entity;ServerLevel world=p.serverLevel();
        // Terminal identity/team invalidation is latched even inside a paid native callback guard.
        // Positional drift and LOS are deliberately excluded; LOS keeps its ten-tick grace.
        if(!entity.getUUID().equals(target.uuid)||entity.getId()!=target.id||entity.level()!=world||!entity.isAlive()||entity.isRemoved()
                ||world.getEntity(target.uuid)!=entity||world.getEntity(target.id)!=entity||!world.hasChunkAt(entity.blockPosition())
                ||p.isAlliedTo(entity)||entity.isAlliedTo(p)){target.retired=true;return false;}
        return harmState(p,entity);
    }
    private static boolean range(ServerPlayer p,Vec3 origin,Vec3 point){return MulticoreCanopyGeometry.pathWithinRange(vector(p.getEyePosition()),vector(origin),vector(point));}
    /** Quarter-block loaded/permitted prefixes precede every shape and entity read; solids win ties. */
    private static NativeHit nativeTrace(ServerPlayer p,Vec3 origin,Vec3 endpoint,boolean includeCaster,double radius,Work work){
        ServerLevel world=p.serverLevel();
        if(!finite(origin)||!finite(endpoint)||origin.distanceTo(endpoint)>MulticoreCanopyRules.RANGE)return new NativeHit(origin,null,REFUSED,false,false);
        int pieces=Math.max(1,(int)Math.ceil(origin.distanceTo(endpoint)/.25));Vec3 previous=origin;
        Set<Entity> scanned=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=1;i<=pieces;i++){
            if(!sameWorld(p,world,work)||!work.prefix())return new NativeHit(previous,null,REFUSED,false,false);
            Vec3 next=origin.lerp(endpoint,(double)i/pieces);AABB query=outward(new AABB(previous,next).inflate(radius));
            if(!permittedVolume(p,query,work)||!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);
            Vec3 end=next;Entity target=null;double nearest=origin.distanceToSqr(end);List<Entity> candidates=new ArrayList<>();
            // Bound and charge every returned spatial candidate, including dropped items/XP.
            // Applying hit eligibility in the native query would hide potentially unbounded rejected work.
            world.getEntities(EntityTypeTest.forClass(Entity.class),query,e->true,candidates,MulticoreCanopyRules.SCAN_LIMIT+1);
            if(!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);
            if(!work.entities(candidates.size()))return new NativeHit(previous,null,REFUSED,false,false);scanned.addAll(candidates);
            if(scanned.size()>MulticoreCanopyRules.SCAN_LIMIT){work.refuse();return new NativeHit(previous,null,REFUSED,false,false);}
            for(Entity body:candidates){
                boolean hittable=(includeCaster||body!=p)&&body.isAlive()&&!body.isRemoved()&&!body.isSpectator()&&body.canBeHitByProjectile();
                if(!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);
                if(!hittable)continue;
                Optional<Vec3> contact=clip(body.getBoundingBox(),origin,next,radius);
                if(contact.isPresent()){
                    double distance=origin.distanceToSqr(contact.get());
                    if(distance<nearest||distance==nearest&&(target==null||body.getId()<target.getId())){end=contact.get();nearest=distance;target=body;}
                }
            }
            Vec3 solidPoint=null;if(!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);
            var shapes=world.getBlockCollisions(p,query).iterator();
            while(true){
                if(!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);
                boolean more=shapes.hasNext();if(!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);if(!more)break;
                var shape=shapes.next();if(!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);
                for(AABB solid:shape.toAabbs()){
                    if(!work.shape())return new NativeHit(previous,null,REFUSED,false,false);
                    var contact=clip(solid,origin,end,radius);
                    if(contact.isPresent()&&origin.distanceToSqr(contact.get())<=nearest){nearest=origin.distanceToSqr(contact.get());solidPoint=contact.get();}
                }
            }
            if(solidPoint!=null)return new NativeHit(solidPoint,null,BLOCKED,true,true);
            if(target!=null)return new NativeHit(end,target,REFUSED,true,true);previous=next;
        }
        return new NativeHit(endpoint,null,MISS,true,false);
    }
    private static boolean coreClear(Context c,CoreState core,Work work){
        ServerPlayer p=c.identity.owner();Vec3 center=vec(core.core.center()),position=p.position(),eye=p.getEyePosition(),look=p.getLookAngle();
        if(!contextState(c)||!core.available)return false;
        if(eye.distanceTo(center)>MulticoreCanopyRules.MAX_CORE_DISTANCE){core.available=false;return false;}
        NativeHit body=nativeTrace(p,center,center,true,MulticoreCanopyRules.CORE_RADIUS,work);
        if(!work.valid()||!contextState(c))return false;
        // Unknown/unloaded/denied work cannot be called a geometric obstruction.
        if(!body.permitted){work.refuse();return false;}
        if(body.contact){core.available=false;return false;}
        if(!pose(p,position,eye,look)){work.refuse();return false;}
        NativeHit stem=nativeTrace(p,eye,center,false,MulticoreCanopyRules.CORE_RADIUS,work);
        if(!work.valid()||!contextState(c))return false;
        if(!stem.permitted){work.refuse();return false;}
        if(stem.contact){core.available=false;return false;}
        if(!pose(p,position,eye,look)){work.refuse();return false;}
        return true;
    }
    private static boolean permittedVolume(ServerPlayer p,AABB volume,Work work){
        ServerLevel world=p.serverLevel();
        if(!sameWorld(p,world,work)||!work.valid()||!finite(new Vec3(volume.minX,volume.minY,volume.minZ))||!finite(new Vec3(volume.maxX,volume.maxY,volume.maxZ))
                ||volume.getXsize()>8||volume.getYsize()>8||volume.getZsize()>8||volume.minY<world.getMinBuildHeight()||volume.maxY>=world.getMaxBuildHeight()
                ||!world.getWorldBorder().isWithinBounds(volume))return false;
        for(int x=((int)Math.floor(volume.minX))>>4;x<=((int)Math.floor(volume.maxX))>>4;x++)for(int z=((int)Math.floor(volume.minZ))>>4;z<=((int)Math.floor(volume.maxZ))>>4;z++)if(!world.hasChunk(x,z))return false;
        for(BlockPos pos:BlockPos.betweenClosed(BlockPos.containing(volume.minX,volume.minY,volume.minZ),BlockPos.containing(volume.maxX,volume.maxY,volume.maxZ)))if(!sameWorld(p,world,work)||!work.cell()||!world.mayInteract(p,pos)||!sameWorld(p,world,work))return false;
        return sameWorld(p,world,work);
    }
    private static boolean sameWorld(ServerPlayer p,ServerLevel world,Work work){if(p.level()==world&&p.serverLevel()==world&&!p.isRemoved())return true;work.refuse();return false;}
    private static boolean canAct(ServerPlayer p){return canAct(p,new Work());}
    private static boolean canAct(ServerPlayer p,Work work){return thread(p)&&permittedVolume(p,p.getBoundingBox(),work)&&canActState(p);}
    private static boolean canActState(ServerPlayer p){
        var state=AbilityStorage.get(p);
        return p.connection!=null&&!p.hasDisconnected()&&p.containerMenu==p.inventoryMenu&&HighPowerResources.ready(p)
                &&!p.getAbilities().instabuild&&!p.getAbilities().invulnerable&&state.activated&&state.overloadFine&&!state.interfering
                &&state.learned(ID)&&state.learned(ParticleLaminate.ID)&&state.category.equals("meltdowner")&&state.level==5&&HighPowerResources.effectiveLevel(p)==5
                &&SkillCatalog.enabled("meltdowner",ID)&&HighPowerCombat.unsupportedReason(p).isEmpty()&&!AbilityConsumption.busy(p)
                &&!p.noPhysics&&!p.isPassenger()&&!p.isSleeping()&&finite(p.position())&&finite(p.getDeltaMovement())&&finite(p.getLookAngle())
                &&p.getLookAngle().lengthSqr()>.5&&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())&&MulticoreCanopyRules.clock(physicalTick);
    }
    private static boolean valid(Context c){ServerPlayer p=c.identity.owner();return canAct(p,c.work==null?new Work():c.work)&&contextState(c);}
    private static boolean contextState(Context c){
        ServerPlayer p=c.identity.owner();return owned(p)==c&&c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canActState(p)
                &&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision()
                &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))&&!MulticoreCanopyRules.expired(c.anchored,physicalTick,240);
    }
    private static boolean harmPermitted(ServerPlayer p,LivingEntity target,Work work){return target!=null&&permittedVolume(p,target.getBoundingBox(),work)&&harmState(p,target);}
    private static boolean harmState(ServerPlayer p,LivingEntity target){
        ServerLevel world=p.serverLevel();return target!=null&&target!=p&&target.level()==world&&target.isAlive()&&!target.isRemoved()&&!target.isSpectator()
                &&world.getEntity(target.getUUID())==target&&world.getEntity(target.getId())==target&&world.hasChunkAt(target.blockPosition())
                &&HighPowerCombat.supportedMulticoreCanopyTarget(target)&&HighPowerCombat.unsupportedReason(target).isEmpty()
                &&!target.isInvulnerable()&&!target.getPersistentData().getBoolean("academy:protected")&&!p.isAlliedTo(target)&&!target.isAlliedTo(p);
    }
    private static void preview(Context c){
        ServerPlayer p=c.identity.owner();if(!MulticoreCanopyRules.ready(c.lastVisual,physicalTick,5)||!fenceReady(p,PREVIEW_FENCE))return;
        stamp(p,PREVIEW_FENCE,5);send(c,c.deployHeld?DEPLOY:c.fireStarted<0?ARMED:c.modeChosen&&c.held?HELD:WARNING,PREPARED,List.of());
    }
    private static void finish(Context c){
        if(CONTEXTS.remove(c.identity.owner().getUUID(),c)){
            c.deployHeld=false;c.fireHeld=false;c.emitting=null;
            diagnostic("NEW251 stage=ended token={} tick={} paid={} held={} slots={} spentSlots={} attempts={} successes={}",c.identity.token(),physicalTick,c.paid,c.held,c.slots.size(),c.slotIndex,c.attempts,c.successes);
            if(c.fireStarted>=0)record(c,RECEIPTS.containsKey(c.identity.owner())?RECEIPTS.get(c.identity.owner()).path():List.of());
            send(c,ENDED,PREPARED,List.of());
        }
    }
    private static List<CoreVisual> coreVisuals(Context c){return c.cores.stream().map(core->new CoreVisual(core.core.id(),vec(core.core.center()),core.available)).toList();}
    private static List<TargetVisual> targetVisuals(Context c){return c.roster.stream().filter(target->!target.retired).map(target->new TargetVisual(target.id,target.lastVisible,target.invisibleAt<0?PREPARED:LOST)).toList();}
    private static void send(Context c,int phase,int status,List<SegmentVisual> path){
        ServerPlayer p=c.identity.owner();if(phase!=ENDED&&owned(p)!=c)return;c.lastVisual=physicalTick;
        Visual visual=new Visual(p.getId(),p.getUUID(),c.input.epoch(),c.input.input(),c.identity.token(),++c.sequence,phase,status,
                phase==ENDED?0:remainingTicks(p),p.position(),phase==ENDED?List.of():coreVisuals(c),phase==ENDED?List.of():targetVisuals(c),path,c.held,c.slotIndex,
                c.fireStarted<0?0:(int)Math.max(0,12-(physicalTick-c.fireStarted)));
        if(phase!=ENDED){c.audience.add(p);for(ServerPlayer observer:c.identity.level().players())if(observer.distanceToSqr(p)<=96*96)c.audience.add(observer);}
        for(ServerPlayer observer:c.audience)if(observer.connection!=null&&!observer.hasDisconnected()&&observer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(observer,visual);
    }
    /** Remaining real ticks persist through recasts/logout/restart; offline time never advances a fence. */
    private static boolean fenceReady(ServerPlayer p,String key){
        var root=p.getPersistentData();if(root.contains(DATA)&&!root.contains(DATA,Tag.TAG_COMPOUND))return false;
        CompoundTag data=root.getCompound(DATA);return !data.contains(key)||data.contains(key,Tag.TAG_INT)&&data.getInt(key)==0;
    }
    private static void stamp(ServerPlayer p,String key,int ticks){CompoundTag data=p.getPersistentData().getCompound(DATA);data.putInt(key,ticks);p.getPersistentData().put(DATA,data);}
    private static void advanceFences(ServerPlayer p){
        if(!p.getPersistentData().contains(DATA,Tag.TAG_COMPOUND))return;CompoundTag data=p.getPersistentData().getCompound(DATA);
        for(String key:List.of(FIRE_FENCE,INPUT_FENCE,PREVIEW_FENCE))if(data.contains(key,Tag.TAG_INT)){int value=data.getInt(key);if(value>0&&value<=240)data.putInt(key,value-1);}
    }
    private static void diagnostic(String format,Object... values){if(DIAGNOSTIC&&diagnosticEvents<256){diagnosticEvents++;DIAGNOSTIC_LOG.info(format,values);}}
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static BigDecimal health(LivingEntity target){return HighPowerCombat.enabled(target)?new BigDecimal(HighPowerCombat.healthText(target)):BigDecimal.valueOf(target.getHealth());}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=30_000_000&&Math.abs(v.y)<=30_000_000&&Math.abs(v.z)<=30_000_000;}
    private static Vector vector(Vec3 v){return new Vector(v.x,v.y,v.z);}private static Vec3 vec(Vector v){return new Vec3(v.x(),v.y(),v.z());}
    private static Optional<Vec3> clip(AABB box,Vec3 start,Vec3 end,double radius){return MulticoreCanopyGeometry.clip(new Bounds(vector(new Vec3(box.minX,box.minY,box.minZ)),vector(new Vec3(box.maxX,box.maxY,box.maxZ))),vector(start),vector(end),radius).map(MulticoreCanopy::vec);}
    private static AABB outward(AABB b){return new AABB(Math.nextDown(b.minX),Math.nextDown(b.minY),Math.nextDown(b.minZ),Math.nextUp(b.maxX),Math.nextUp(b.maxY),Math.nextUp(b.maxZ));}
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public record CoreVisual(int id,Vec3 center,boolean available){public CoreVisual{if(id<0||id>5||!finite(center))throw new IllegalArgumentException("Invalid NEW251 core");}}
    public record TargetVisual(int id,Vec3 point,int status){public TargetVisual{if(id<0||!finite(point)||status<PREPARED||status>LOST)throw new IllegalArgumentException("Invalid NEW251 target");}}
    public record SegmentVisual(int coreId,int slot,Vec3 start,Vec3 end,int status){public SegmentVisual{if(coreId<0||coreId>5||slot<0||slot>15||!finite(start)||!finite(end)||start.distanceTo(end)>96||status<PREPARED||status>LOST)throw new IllegalArgumentException("Invalid NEW251 ray");}}
    public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int phase,int status,int remainingTicks,
                         Vec3 ownerFeet,List<CoreVisual> cores,List<TargetVisual> targets,List<SegmentVisual> path,boolean held,int shotIndex,int warningTicks) implements CustomPacketPayload {
        public Visual{
            cores=List.copyOf(cores);targets=List.copyOf(targets);path=List.copyOf(path);
            if(owner<0||ownerUuid==null||epoch<=0||input<=0||token<=0||sequence<=0||phase<DEPLOY||phase>ENDED||status<PREPARED||status>LOST
                    ||remainingTicks<0||remainingTicks> MulticoreCanopyRules.LIFETIME||!finite(ownerFeet)||cores.size()>6||targets.size()>16||path.size()>6||shotIndex<0||shotIndex>16||warningTicks<0||warningTicks>12
                    ||cores.stream().map(CoreVisual::id).distinct().count()!=cores.size()||targets.stream().map(TargetVisual::id).distinct().count()!=targets.size())throw new IllegalArgumentException("Invalid bounded NEW251 visual");
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","multicore_canopy_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.owner);b.writeUUID(v.ownerUuid);b.writeLong(v.epoch);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.sequence);b.writeByte(v.phase);b.writeByte(v.status);b.writeShort(v.remainingTicks);writeVec(b,v.ownerFeet);
            b.writeByte(v.cores.size());for(CoreVisual core:v.cores){b.writeByte(core.id);writeVec(b,core.center);b.writeBoolean(core.available);}
            b.writeByte(v.targets.size());for(TargetVisual target:v.targets){b.writeInt(target.id);writeVec(b,target.point);b.writeByte(target.status);}
            b.writeByte(v.path.size());for(SegmentVisual ray:v.path){b.writeByte(ray.coreId);b.writeByte(ray.slot);writeVec(b,ray.start);writeVec(b,ray.end);b.writeByte(ray.status);}
            b.writeBoolean(v.held);b.writeByte(v.shotIndex);b.writeByte(v.warningTicks);
        },b->{
            int owner=b.readInt();UUID uuid=b.readUUID();long epoch=b.readLong(),input=b.readLong(),token=b.readLong(),sequence=b.readLong();int phase=b.readUnsignedByte(),status=b.readUnsignedByte(),remaining=b.readUnsignedShort();Vec3 feet=readVec(b);
            int count=b.readUnsignedByte();if(count>6)throw new IllegalArgumentException("Unbounded NEW251 cores");List<CoreVisual> cores=new ArrayList<>(count);for(int i=0;i<count;i++)cores.add(new CoreVisual(b.readUnsignedByte(),readVec(b),b.readBoolean()));
            count=b.readUnsignedByte();if(count>16)throw new IllegalArgumentException("Unbounded NEW251 targets");List<TargetVisual> targets=new ArrayList<>(count);for(int i=0;i<count;i++)targets.add(new TargetVisual(b.readInt(),readVec(b),b.readUnsignedByte()));
            count=b.readUnsignedByte();if(count>6)throw new IllegalArgumentException("Unbounded NEW251 rays");List<SegmentVisual> path=new ArrayList<>(count);for(int i=0;i<count;i++)path.add(new SegmentVisual(b.readUnsignedByte(),b.readUnsignedByte(),readVec(b),readVec(b),b.readUnsignedByte()));
            return new Visual(owner,uuid,epoch,input,token,sequence,phase,status,remaining,feet,cores,targets,path,b.readBoolean(),b.readUnsignedByte(),b.readUnsignedByte());
        });
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network{@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){
        if(physicalTick<Long.MAX_VALUE)physicalTick++;
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers())advanceFences(p);
        for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.level().getServer()==e.getServer())tick(c.identity.owner());
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer p){if(e.getEntity() instanceof ServerPlayer replacement&&p.getPersistentData().contains(DATA))replacement.getPersistentData().put(DATA,p.getPersistentData().get(DATA).copy());remove(p);}}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
}
