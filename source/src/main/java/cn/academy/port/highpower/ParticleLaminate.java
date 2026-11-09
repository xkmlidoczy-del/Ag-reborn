package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ParticleLaminateGeometry;
import cn.academy.port.highpower.core.ParticleLaminateGeometry.Panel;
import cn.academy.port.highpower.core.ParticleLaminateGeometry.Segment;
import cn.academy.port.highpower.core.ParticleLaminateRules;
import cn.academy.port.highpower.core.ParticleLaminateRules.Work;
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
import net.minecraft.world.entity.monster.Enemy;
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

/** NEW241: fixed panels, three physical first-body rays and one closed paid budget per pulse. */
@EventBusSubscriber(modid="academy")
public final class ParticleLaminate {
    public static final String ID="particle_laminate";
    private static final boolean DIAGNOSTIC=Boolean.getBoolean("academy.highpower.particleLaminateDiagnostic");
    private static final org.slf4j.Logger DIAGNOSTIC_LOG=org.slf4j.LoggerFactory.getLogger("academy.particle_laminate_diagnostics");
    public static final int MIN_LEVEL=4,MAX_LEVEL=5,ACTIVE=0,EDIT=1,FIRE=2,ENDED=3;
    public static final int PREPARED=0,MISS=1,BLOCKED=2,REFUSED=3,DAMAGED=4,ABSORBED=5,DEGENERATE=6;
    private static final String DATA="academy:particle_laminate",INPUT_AT="inputAt",PREVIEW_AT="previewAt",PULSE_AT="pulseAt",MASTERY_AT="masteryAt";
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Set<ServerPlayer> PROCESSING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static long nextToken;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input) {}
    private record Hold(HighPowerInput.Input input,long started) {}
    private record NativeHit(Vec3 point,Entity target,int status,boolean permitted,boolean contact) {}
    private record FrozenTarget(LivingEntity target,UUID uuid,int id,int segment,Vec3 origin,Vec3 endpoint,Vec3 rayEnd,
                                Vec3 position,AABB bounds,Vec3 ownerPosition,BigDecimal damage,long tick) {}
    public record Receipt(List<SegmentVisual> path,int pulseIndex,int attempts,int successes,boolean paid,
                          boolean lowMode,int uniqueTargets,int prefixes,long input){public Receipt{path=List.copyOf(path);}}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;final int preset;final long revision;
        final Set<ServerPlayer> audience=new LinkedHashSet<>();final Set<UUID> targets=new LinkedHashSet<>();
        HighPowerInput.Input input;Hold hold;List<Panel> panels=List.of();List<Segment> segments=List.of();
        long anchored=-1,armed=-1,lastTick=-1,lastVisual=-1,sequence;int opportunity=-1;
        boolean processing,paid,lowMode;Work work;EmissionPermit emitting;Receipt receipt;
        Context(ServerPlayer p,HighPowerInput.Input input){identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));
            this.input=input;preset=identity.state().presets.current();revision=identity.state().presets.revision();}
    }
    /** No caller may invent a target, replace a slot, split its amount or move its real emitter. */
    public static final class EmissionPermit {
        private final Context context;private final FrozenTarget frozen;private boolean consumed;
        private EmissionPermit(Context c,FrozenTarget target){context=c;frozen=target;}
        ServerPlayer player(){return context.identity.owner();}
        LivingEntity target(){return frozen.target;}
        BigDecimal damage(){return frozen.damage;}
        Vec3 finalOrigin(){return frozen.origin;}
        Vec3 endpoint(){return frozen.endpoint;}
        boolean consume(){if(consumed||!valid())return false;consumed=true;return true;}
        boolean valid(){
            ServerPlayer p=player();Work work=context.work;
            if(context.emitting!=this||!context.processing||!context.paid||work==null||!work.valid()||owned(p)!=context
                    ||!ParticleLaminate.valid(context)||now(p)!=frozen.tick||!p.position().equals(frozen.ownerPosition)
                    ||!harmPermitted(p,frozen.target,work)||!frozen.target.getUUID().equals(frozen.uuid)||frozen.target.getId()!=frozen.id
                    ||!frozen.target.position().equals(frozen.position)||!frozen.target.getBoundingBox().equals(frozen.bounds)
                    ||frozen.segment<0||frozen.segment>=context.segments.size())return false;
            Segment ray=context.segments.get(frozen.segment);
            if(!vec(ray.start()).equals(frozen.origin)||!vec(ray.end()).equals(frozen.rayEnd)||!panelsClear(context,work))return false;
            NativeHit hit=nativeTrace(p,frozen.origin,frozen.rayEnd,true,work);
            return work.valid()&&hit.permitted&&hit.target==frozen.target&&hit.point.equals(frozen.endpoint)
                    &&contextState(context)&&harmState(p,frozen.target)&&now(p)==frozen.tick
                    &&p.position().equals(frozen.ownerPosition)&&frozen.target.position().equals(frozen.position)
                    &&frozen.target.getBoundingBox().equals(frozen.bounds)&&frozen.target.isAlive()&&!frozen.target.isRemoved();
        }
    }
    private ParticleLaminate() {}

    public static boolean start(ServerPlayer p,String wire){
        if(PROCESSING.contains(p))return false;Context c=owned(p);if(c!=null&&c.processing)return false;
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark prior=INPUTS.get(p.getUUID());if(prior!=null&&prior.epoch==input.epoch()&&input.input()<=prior.input)return false;
        if(c!=null&&!valid(c)){finish(c);c=null;}if(c!=null&&c.hold!=null)return false;
        INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        if(!canAct(p)||!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))||!fenceReady(p,INPUT_AT,1)||nextToken==Long.MAX_VALUE)return false;
        if(c!=null&&(c.input.epoch()!=input.epoch()||c.input.slot()!=input.slot()))return false;
        stamp(p,INPUT_AT);
        // Active tap is a free recall, including when the most recent pulse left native cooldown.
        if(c!=null&&c.armed>=0){c.input=input;finish(c);return true;}
        if(!AbilityStorage.get(p).canUse(ID))return false;
        if(c==null){Context old=CONTEXTS.get(p.getUUID());if(old!=null)finish(old);c=new Context(p,input);CONTEXTS.put(p.getUUID(),c);}
        c.input=input;c.hold=new Hold(input,now(p));preview(c);return true;
    }
    public static boolean release(ServerPlayer p,String wire){
        Context c=owned(p);var input=HighPowerInput.parse(wire);
        if(c==null||c.processing||c.hold==null||!HighPowerBracerSession.authenticated(p,input)||!c.hold.input.equals(input))return false;
        if(!valid(c)||ParticleLaminateRules.expired(c.hold.started,now(p),ParticleLaminateRules.HOLD_TICKS)){finish(c);return false;}
        c.processing=true;PROCESSING.add(p);
        try{
            Panel panel=candidate(c,new Work());c.hold=null;
            if(panel==null){send(c,ACTIVE,DEGENERATE,null,List.of());retireEmpty(c);return false;}
            List<Panel> panels=new ArrayList<>(c.panels);panels.add(panel);List<Segment> segments=List.of();
            if(panels.size()==2){var geometry=ParticleLaminateGeometry.segments(panels.getFirst(),panel);if(geometry.isEmpty())return false;segments=geometry.get();}
            if(!valid(c)||!AbilityStorage.get(p).canUse(ID)||!HighPowerResources.trySpend(p,ID,ParticleLaminateRules.placementCost(HighPowerGrowth.values(p).position()),1))return false;
            if(!valid(c)||!panelClear(p,panel,new Work()))return false;
            c.panels=List.copyOf(panels);c.segments=segments;if(c.anchored<0)c.anchored=now(p);
            if(c.panels.size()==2){c.armed=now(p);c.lowMode=p.isShiftKeyDown();}
            if(DIAGNOSTIC)DIAGNOSTIC_LOG.info("NEW241 stage=placed token={} input={} tick={} count={} armed={} low={} panels={}",c.identity.token(),c.input.input(),now(p),c.panels.size(),c.armed,c.lowMode,c.panels);
            send(c,ACTIVE,PREPARED,null,traceVisuals(c,c.panels,new Work()));return true;
        }finally{c.processing=false;PROCESSING.remove(p);retireEmpty(c);}
    }
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;Context c=owned(p);
        boolean owns=c!=null&&c.input.epoch()==input.epoch()&&c.input.slot()==input.slot()&&input.input()>=c.input.input();
        if(!owns&&!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())))return false;
        Watermark prior=INPUTS.get(p.getUUID());boolean retired=prior==null||prior.epoch!=input.epoch()||input.input()>prior.input;
        if(retired)INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));if(owns)finish(c);return owns||retired;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c);}
    public static void remove(ServerPlayer p){if(thread(p)){abort(p);INPUTS.remove(p.getUUID());}}
    public static void clear(){for(Context c:new ArrayList<>(CONTEXTS.values()))finish(c);INPUTS.clear();PROCESSING.clear();}
    public static boolean active(ServerPlayer p){Context c=owned(p);if(c!=null&&!valid(c)){finish(c);return false;}return c!=null;}
    public static boolean holding(ServerPlayer p){Context c=owned(p);return c!=null&&c.hold!=null&&valid(c);}
    public static List<PanelVisual> panels(ServerPlayer p){Context c=owned(p);return c==null?List.of():panelVisuals(c.panels);}
    public static long anchoredAt(ServerPlayer p){Context c=owned(p);return c==null?-1:c.anchored;}
    public static long armedAt(ServerPlayer p){Context c=owned(p);return c==null?-1:c.armed;}
    public static int remainingTicks(ServerPlayer p){Context c=owned(p);return c==null?0:c.anchored<0?c.hold==null?0:Math.max(0,ParticleLaminateRules.HOLD_TICKS-(int)(now(p)-c.hold.started)):ParticleLaminateRules.remaining(c.anchored,c.armed,now(p));}
    public static Receipt receipt(ServerPlayer p){Context c=owned(p);return c==null?null:c.receipt;}
    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.processing)return;
        if(!valid(c)||now(p)<c.lastTick){finish(c);return;}if(now(p)==c.lastTick)return;c.lastTick=now(p);
        if(c.hold!=null&&ParticleLaminateRules.expired(c.hold.started,now(p),ParticleLaminateRules.HOLD_TICKS)){c.hold=null;retireEmpty(c);if(owned(p)!=c)return;}
        if(c.armed>=0){
            int opportunity=ParticleLaminateRules.opportunity(c.armed,now(p));
            if(opportunity>c.opportunity){
                c.opportunity=opportunity; // Skip missed windows permanently; there is no catch-up loop.
                if(fenceReady(p,PULSE_AT,ParticleLaminateRules.PULSE_INTERVAL)){
                    stamp(p,PULSE_AT);c.processing=true;PROCESSING.add(p);
                    try{pulse(c);}finally{c.emitting=null;c.work=null;c.paid=false;c.processing=false;PROCESSING.remove(p);}
                }
            }
        }
        if(owned(p)==c)preview(c);
    }
    private static void pulse(Context c){
        ServerPlayer p=c.identity.owner();Work work=new Work();c.work=work;
        if(!panelsClear(c,work)){finish(c);return;}
        List<NativeHit> hits=new ArrayList<>(3);List<Integer> selected=new ArrayList<>(3);Set<UUID> chosen=new LinkedHashSet<>();
        for(int index=0;index<c.segments.size();index++){
            Segment ray=c.segments.get(index);NativeHit hit=nativeTrace(p,vec(ray.start()),vec(ray.end()),true,work);hits.add(hit);
            if(!work.valid()||!hit.permitted){send(c,FIRE,REFUSED,null,path(c.segments,hits));return;}
            if(hit.target instanceof LivingEntity target&&harmPermitted(p,target,work)&&chosen.add(target.getUUID())
                    &&(c.targets.contains(target.getUUID())||c.targets.size()+newCount(chosen,c.targets)<=ParticleLaminateRules.MAX_UNIQUE))selected.add(index);
        }
        List<SegmentVisual> path=new ArrayList<>(path(c.segments,hits));
        if(!work.valid()||selected.isEmpty()||!valid(c)||!AbilityStorage.get(p).canUse(ID)){
            c.receipt=new Receipt(path,c.opportunity+1,0,0,false,c.lowMode,c.targets.size(),work.prefixes(),c.input.input());send(c,FIRE,work.valid()?MISS:REFUSED,null,path);return;
        }
        var growth=HighPowerGrowth.values(p);List<BigDecimal> shares=ParticleLaminateRules.shares(ParticleLaminateRules.damage(growth.damage(),c.lowMode),selected.size());
        List<FrozenTarget> frozen=new ArrayList<>(selected.size());
        for(int at=0;at<selected.size();at++){
            int index=selected.get(at);NativeHit hit=hits.get(index);LivingEntity target=(LivingEntity)hit.target;Segment ray=c.segments.get(index);
            frozen.add(new FrozenTarget(target,target.getUUID(),target.getId(),index,vec(ray.start()),hit.point,vec(ray.end()),target.position(),target.getBoundingBox(),p.position(),shares.get(at),now(p)));
        }
        // Debit once after all identities and decimal shares are frozen. Native callbacks can only waste slots.
        if(!HighPowerResources.trySpend(p,ID,ParticleLaminateRules.pulseCost(growth.position(),c.lowMode),1))return;
        c.paid=true;for(FrozenTarget target:frozen)c.targets.add(target.uuid);HighPowerNaturalRecovery.offensiveAction(p);
        int successes=0,attempts=0;
        for(FrozenTarget target:frozen){
            attempts++;EmissionPermit permit=new EmissionPermit(c,target);c.emitting=permit;
            BigDecimal hp=health(target.target),shield=HighPowerCombat.shield(target.target);float absorption=target.target.getAbsorptionAmount();boolean accepted;
            try{accepted=HighPowerCombat.attackParticleLaminate(permit);}finally{c.emitting=null;}
            int status=REFUSED;
            if(accepted&&health(target.target).compareTo(hp)<0)status=DAMAGED;
            else if(accepted&&(HighPowerCombat.shield(target.target).compareTo(shield)<0||target.target.getAbsorptionAmount()<absorption))status=ABSORBED;
            if(status==DAMAGED||status==ABSORBED)successes++;
            if(DIAGNOSTIC)DIAGNOSTIC_LOG.info("NEW241 stage=settled token={} pulse={} tick={} segment={} target={} origin={} endpoint={} rawShare={} status={} hpBefore={} hpAfter={} shieldBefore={} shieldAfter={}",c.identity.token(),c.opportunity+1,now(p),target.segment,target.uuid,target.origin,target.endpoint,target.damage,status,hp,health(target.target),shield,HighPowerCombat.shield(target.target));
            path.set(target.segment,new SegmentVisual(target.origin,target.endpoint,status));
            if(owned(p)!=c||!valid(c)||!work.valid())break;
        }
        c.receipt=new Receipt(path,c.opportunity+1,attempts,successes,true,c.lowMode,c.targets.size(),work.prefixes(),c.input.input());
        if(owned(p)==c)send(c,FIRE,successes>0?DAMAGED:REFUSED,null,path);
        if(successes>0&&owned(p)==c&&valid(c)&&fenceReady(p,MASTERY_AT,ParticleLaminateRules.PULSE_INTERVAL)){
            stamp(p,MASTERY_AT);c.identity.state().addExperience(ID,.005);AbilityStorage.save(p);
        }
        if(owned(p)==c&&!valid(c))finish(c);
    }
    private static int newCount(Set<UUID> chosen,Set<UUID> previous){int count=0;for(UUID id:chosen)if(!previous.contains(id))count++;return count;}
    private static Panel candidate(Context c,Work work){
        ServerPlayer p=c.identity.owner();if(c.panels.size()>1)return null;Vec3 eye=p.getEyePosition(),direction=p.getLookAngle().normalize();
        NativeHit hit=nativeTrace(p,eye,endpoint(eye,direction,ParticleLaminateRules.RANGE),false,work);
        if(!hit.permitted||hit.target!=null)return null;
        double distance=eye.distanceTo(hit.point)-(hit.contact?Math.hypot(ParticleLaminateGeometry.HALF_WIDTH,ParticleLaminateGeometry.HALF_HEIGHT)+.06:0);
        if(distance<1.3)return null;
        try{
            Panel panel;
            Vec3 flat=new Vec3(direction.x,0,direction.z);
            if(hit.contact&&direction.y<-.1&&flat.lengthSqr()>1e-6){
                // Aimed down at the ground: stand the plate upright on the contact instead of tilting it into the floor,
                // where its lower half would always fail the clearance check and the release would silently do nothing.
                flat=flat.normalize();
                Vec3 center=hit.point.add(0,ParticleLaminateGeometry.HALF_HEIGHT+.06,0).subtract(flat.scale(ParticleLaminateGeometry.THICKNESS+.06));
                panel=new Panel(c.panels.size(),vector(center),vector(flat));
            } else panel=new Panel(c.panels.size(),vector(endpoint(eye,direction,Math.min(distance,ParticleLaminateRules.RANGE))),vector(direction));
            if(!panelClear(p,panel,work))return null;
            if(!c.panels.isEmpty()&&(ParticleLaminateGeometry.segments(c.panels.getFirst(),panel).isEmpty()||!panelClear(p,c.panels.getFirst(),work)))return null;
            return panel;
        }catch(IllegalArgumentException invalid){return null;}
    }
    private static List<SegmentVisual> traceVisuals(Context c,List<Panel> panels,Work work){
        if(panels.size()!=2)return List.of();var rays=ParticleLaminateGeometry.segments(panels.getFirst(),panels.getLast());if(rays.isEmpty())return List.of();
        List<NativeHit> hits=new ArrayList<>(3);for(Segment ray:rays.get()){NativeHit hit=nativeTrace(c.identity.owner(),vec(ray.start()),vec(ray.end()),true,work);hits.add(hit);if(!hit.permitted)break;}
        return path(rays.get(),hits);
    }
    private static List<SegmentVisual> path(List<Segment> segments,List<NativeHit> hits){
        List<SegmentVisual> path=new ArrayList<>(hits.size());for(int i=0;i<hits.size();i++)path.add(new SegmentVisual(vec(segments.get(i).start()),hits.get(i).point,hits.get(i).status));return List.copyOf(path);
    }
    /** Quarter-block loaded/permitted prefixes precede every shape and entity read; solids win ties. */
    private static NativeHit nativeTrace(ServerPlayer p,Vec3 origin,Vec3 endpoint,boolean includeCaster,Work work){
        ServerLevel world=p.serverLevel();
        if(!finite(origin)||!finite(endpoint)||origin.distanceTo(endpoint)>ParticleLaminateRules.RANGE)return new NativeHit(origin,null,REFUSED,false,false);
        int pieces=Math.max(1,(int)Math.ceil(origin.distanceTo(endpoint)/.25));Vec3 previous=origin;
        Set<Entity> scanned=Collections.newSetFromMap(new IdentityHashMap<>());
        for(int i=1;i<=pieces;i++){
            if(!sameWorld(p,world,work)||!work.prefix())return new NativeHit(previous,null,REFUSED,false,false);
            Vec3 next=origin.lerp(endpoint,(double)i/pieces);AABB query=outward(new AABB(previous,next).inflate(ParticleLaminateRules.RADIUS));
            if(!permittedVolume(p,query,work)||!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);
            Vec3 end=next;Entity target=null;double nearest=origin.distanceToSqr(end);List<Entity> candidates=new ArrayList<>();
            // Bound and charge every returned spatial candidate, including dropped items/XP.
            // Applying hit eligibility in the native query would hide potentially unbounded rejected work.
            world.getEntities(EntityTypeTest.forClass(Entity.class),query,e->true,candidates,ParticleLaminateRules.SCAN_LIMIT+1);
            if(!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);
            if(!work.entities(candidates.size()))return new NativeHit(previous,null,REFUSED,false,false);scanned.addAll(candidates);
            if(scanned.size()>ParticleLaminateRules.SCAN_LIMIT){work.refuse();return new NativeHit(previous,null,REFUSED,false,false);}
            for(Entity body:candidates){
                boolean hittable=(includeCaster||body!=p)&&body.isAlive()&&!body.isRemoved()&&!body.isSpectator()&&body.canBeHitByProjectile();
                if(!sameWorld(p,world,work))return new NativeHit(previous,null,REFUSED,false,false);
                if(!hittable)continue;
                Optional<Vec3> contact=clip(body.getBoundingBox(),origin,next,ParticleLaminateRules.RADIUS);
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
                    var contact=clip(solid,origin,end,ParticleLaminateRules.RADIUS);
                    if(contact.isPresent()&&origin.distanceToSqr(contact.get())<=nearest){nearest=origin.distanceToSqr(contact.get());solidPoint=contact.get();}
                }
            }
            if(solidPoint!=null)return new NativeHit(solidPoint,null,BLOCKED,true,true);
            if(target!=null)return new NativeHit(end,target,REFUSED,true,true);previous=next;
        }
        return new NativeHit(endpoint,null,MISS,true,false);
    }
    private static boolean panelsClear(Context c,Work work){if(c.panels.size()!=2||c.segments.size()!=3)return false;for(Panel panel:c.panels)if(!panelClear(c.identity.owner(),panel,work))return false;return work.valid();}
    private static boolean panelClear(ServerPlayer p,Panel panel,Work work){
        ServerLevel world=p.serverLevel();
        if(p.getEyePosition().distanceTo(vec(panel.center()))>ParticleLaminateRules.RANGE)return false;
        AABB volume=outward(box(ParticleLaminateGeometry.volumeBounds(panel)));
        if(!permittedVolume(p,volume,work)||!sameWorld(p,world,work))return false;
        boolean clear=!world.getBlockCollisions(p,volume).iterator().hasNext();return sameWorld(p,world,work)&&clear;
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
    private static boolean canAct(ServerPlayer p,Work work){
        return thread(p)&&permittedVolume(p,p.getBoundingBox(),work)&&canActState(p);
    }
    /** State checks follow permission/collision callbacks, with no further native world queries. */
    private static boolean canActState(ServerPlayer p){
        var state=AbilityStorage.get(p);
        return p.connection!=null&&!p.hasDisconnected()&&p.containerMenu==p.inventoryMenu&&HighPowerResources.ready(p)
                &&!p.getAbilities().instabuild&&!p.getAbilities().invulnerable&&state.activated&&state.overloadFine&&!state.interfering
                &&state.learned(ID)&&state.learned(DisintegrationSweep.ID)&&state.learned(FoldedMirrorArray.ID)&&state.learned(DualPoint.ID)
                &&state.category.equals("meltdowner")&&state.level>=MIN_LEVEL&&state.level<=MAX_LEVEL&&HighPowerResources.effectiveLevel(p)>=MIN_LEVEL
                &&SkillCatalog.enabled("meltdowner",ID)&&HighPowerCombat.unsupportedReason(p).isEmpty()&&!AbilityConsumption.busy(p)
                &&!p.noPhysics&&!p.isPassenger()&&!p.isSleeping()&&finite(p.position())&&finite(p.getDeltaMovement())&&finite(p.getLookAngle())
                &&p.getLookAngle().lengthSqr()>.5&&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())&&ParticleLaminateRules.clock(now(p));
    }
    private static boolean valid(Context c){
        ServerPlayer p=c.identity.owner();return canAct(p,c.work==null?new Work():c.work)&&contextState(c);
    }
    private static boolean contextState(Context c){
        ServerPlayer p=c.identity.owner();return owned(p)==c&&c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canActState(p)
                &&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision()
                &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))
                &&(c.anchored<0||!ParticleLaminateRules.expired(c.anchored,now(p),ParticleLaminateRules.ANCHOR_TICKS))
                &&(c.armed<0||!ParticleLaminateRules.expired(c.armed,now(p),ParticleLaminateRules.FIELD_TICKS));
    }
    private static boolean harmPermitted(ServerPlayer p,LivingEntity target,Work work){
        return target!=null&&permittedVolume(p,target.getBoundingBox(),work)&&harmState(p,target);
    }
    private static boolean harmState(ServerPlayer p,LivingEntity target){
        ServerLevel world=p.serverLevel();return target!=null&&target!=p&&target instanceof Enemy&&target.level()==world&&target.isAlive()&&!target.isRemoved()&&!target.isSpectator()
                &&world.getEntity(target.getUUID())==target&&world.getEntity(target.getId())==target&&HighPowerCombat.supported(target)
                &&HighPowerCombat.unsupportedReason(target).isEmpty()&&!target.isInvulnerable()&&!target.getPersistentData().getBoolean("academy:protected")
                &&!p.isAlliedTo(target)&&!target.isAlliedTo(p);
    }
    private static void preview(Context c){
        ServerPlayer p=c.identity.owner();if(!ParticleLaminateRules.ready(c.lastVisual,now(p),ParticleLaminateRules.PREVIEW_INTERVAL)||!fenceReady(p,PREVIEW_AT,ParticleLaminateRules.PREVIEW_INTERVAL))return;
        stamp(p,PREVIEW_AT);Work work=new Work();Panel ghost=c.hold!=null?candidate(c,work):null;List<Panel> panels=new ArrayList<>(c.panels);if(ghost!=null)panels.add(ghost);
        send(c,c.hold==null?ACTIVE:EDIT,c.hold!=null&&ghost==null?DEGENERATE:PREPARED,ghost,traceVisuals(c,panels,work));
    }
    private static void retireEmpty(Context c){if(c.panels.isEmpty()&&c.hold==null)finish(c);}
    private static void finish(Context c){if(CONTEXTS.remove(c.identity.owner().getUUID(),c)){c.hold=null;c.emitting=null;send(c,ENDED,PREPARED,null,List.of());}}
    private static List<PanelVisual> panelVisuals(List<Panel> panels){return panels.stream().map(panel->new PanelVisual(vec(panel.center()),vec(panel.normal()))).toList();}
    private static void send(Context c,int phase,int status,Panel ghost,List<SegmentVisual> path){
        ServerPlayer p=c.identity.owner();if(phase!=ENDED&&owned(p)!=c)return;
        if(DIAGNOSTIC&&(phase==FIRE||phase==ENDED))DIAGNOSTIC_LOG.info("NEW241 stage=receipt token={} input={} tick={} phase={} status={} opportunity={} low={} receipt={} path={}",c.identity.token(),c.input.input(),now(p),phase,status,c.opportunity+1,c.lowMode,c.receipt,path);
        c.lastVisual=now(p);Visual visual=new Visual(p.getId(),p.getUUID(),c.input.epoch(),c.input.input(),c.identity.token(),++c.sequence,phase,status,
                phase==ENDED?0:remainingTicks(p),p.position(),phase==ENDED?List.of():panelVisuals(c.panels),ghost==null?Vec3.ZERO:vec(ghost.center()),ghost==null?Vec3.ZERO:vec(ghost.normal()),path,c.lowMode,Math.max(0,c.opportunity+1),c.armed<0?0:(int)Math.max(0,Math.min(100,now(p)-c.armed)));
        if(phase!=ENDED){c.audience.add(p);for(ServerPlayer observer:c.identity.level().players())if(near(observer,c))c.audience.add(observer);}
        for(ServerPlayer observer:c.audience)if(observer.connection!=null&&!observer.hasDisconnected()&&observer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(observer,visual);
    }
    private static boolean near(ServerPlayer observer,Context c){if(observer.distanceToSqr(c.identity.owner())<=96*96)return true;for(Panel panel:c.panels)if(observer.position().distanceToSqr(vec(panel.center()))<=96*96)return true;return false;}
    private static boolean fenceReady(ServerPlayer p,String key,int interval){
        var root=p.getPersistentData();if(root.contains(DATA)&&!root.contains(DATA,Tag.TAG_COMPOUND))return false;
        CompoundTag data=root.getCompound(DATA);return !data.contains(key)||data.contains(key,Tag.TAG_LONG)&&ParticleLaminateRules.ready(data.getLong(key),now(p),interval);
    }
    private static void stamp(ServerPlayer p,String key){CompoundTag data=p.getPersistentData().getCompound(DATA);data.putLong(key,now(p));p.getPersistentData().put(DATA,data);}
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static long now(ServerPlayer p){return p.serverLevel().getServer().overworld().getGameTime();}
    private static BigDecimal health(LivingEntity target){return HighPowerCombat.enabled(target)?new BigDecimal(HighPowerCombat.healthText(target)):BigDecimal.valueOf(target.getHealth());}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=30_000_000&&Math.abs(v.y)<=30_000_000&&Math.abs(v.z)<=30_000_000;}
    private static Vector vector(Vec3 v){return new Vector(v.x,v.y,v.z);}
    private static Vec3 vec(Vector v){return new Vec3(v.x(),v.y(),v.z());}
    private static AABB box(Bounds bounds){return new AABB(vec(bounds.min()),vec(bounds.max()));}
    private static Optional<Vec3> clip(AABB box,Vec3 start,Vec3 end,double radius){return ParticleLaminateGeometry.clip(new Bounds(vector(new Vec3(box.minX,box.minY,box.minZ)),vector(new Vec3(box.maxX,box.maxY,box.maxZ))),vector(start),vector(end),radius).map(ParticleLaminate::vec);}
    private static AABB outward(AABB b){return new AABB(Math.nextDown(b.minX),Math.nextDown(b.minY),Math.nextDown(b.minZ),Math.nextUp(b.maxX),Math.nextUp(b.maxY),Math.nextUp(b.maxZ));}
    private static Vec3 endpoint(Vec3 origin,Vec3 direction,double distance){Vec3 end=origin.add(direction.scale(distance));for(int i=0;i<8&&origin.distanceTo(end)>distance;i++)end=new Vec3(Math.nextAfter(end.x,origin.x),Math.nextAfter(end.y,origin.y),Math.nextAfter(end.z,origin.z));return end;}
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public record PanelVisual(Vec3 center,Vec3 normal){public PanelVisual{if(!finite(center)||!finite(normal))throw new IllegalArgumentException("Invalid NEW241 panel");new Panel(0,vector(center),vector(normal));}}
    public record SegmentVisual(Vec3 start,Vec3 end,int status){public SegmentVisual{if(!finite(start)||!finite(end)||start.distanceTo(end)>64||status<PREPARED||status>DEGENERATE)throw new IllegalArgumentException("Invalid NEW241 ray");}}
    public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int phase,int status,int remainingTicks,
                         Vec3 ownerFeet,List<PanelVisual> panels,Vec3 ghostCenter,Vec3 ghostNormal,List<SegmentVisual> path,boolean lowMode,int pulseIndex,int fieldAge) implements CustomPacketPayload {
        public Visual{
            panels=List.copyOf(panels);path=List.copyOf(path);
            if(owner<0||ownerUuid==null||epoch<=0||input<=0||token<=0||sequence<=0||phase<ACTIVE||phase>ENDED||status<PREPARED||status>DEGENERATE
                    ||remainingTicks<0||remainingTicks>200||!finite(ownerFeet)||!finite(ghostCenter)||!finite(ghostNormal)||panels.size()>2||path.size()>3||pulseIndex<0||pulseIndex>5||fieldAge<0||fieldAge>100
                    ||ghostNormal.lengthSqr()!=0&&Math.abs(ghostNormal.length()-1)>1e-10)throw new IllegalArgumentException("Invalid bounded NEW241 visual");
            if(ghostNormal.lengthSqr()!=0)new Panel(0,vector(ghostCenter),vector(ghostNormal));
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","particle_laminate_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.owner);b.writeUUID(v.ownerUuid);b.writeLong(v.epoch);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.sequence);b.writeByte(v.phase);b.writeByte(v.status);b.writeShort(v.remainingTicks);writeVec(b,v.ownerFeet);b.writeByte(v.panels.size());
            for(PanelVisual panel:v.panels){writeVec(b,panel.center);writeVec(b,panel.normal);}writeVec(b,v.ghostCenter);writeVec(b,v.ghostNormal);b.writeByte(v.path.size());
            for(SegmentVisual segment:v.path){writeVec(b,segment.start);writeVec(b,segment.end);b.writeByte(segment.status);}b.writeBoolean(v.lowMode);b.writeByte(v.pulseIndex);b.writeByte(v.fieldAge);
        },b->{
            int owner=b.readInt();UUID uuid=b.readUUID();long epoch=b.readLong(),input=b.readLong(),token=b.readLong(),sequence=b.readLong();int phase=b.readUnsignedByte(),status=b.readUnsignedByte(),remaining=b.readUnsignedShort();Vec3 feet=readVec(b);
            int count=b.readUnsignedByte();if(count>2)throw new IllegalArgumentException("Unbounded NEW241 panels");List<PanelVisual> panels=new ArrayList<>(count);for(int i=0;i<count;i++)panels.add(new PanelVisual(readVec(b),readVec(b)));Vec3 center=readVec(b),normal=readVec(b);
            count=b.readUnsignedByte();if(count>3)throw new IllegalArgumentException("Unbounded NEW241 rays");List<SegmentVisual> path=new ArrayList<>(count);for(int i=0;i<count;i++)path.add(new SegmentVisual(readVec(b),readVec(b),b.readUnsignedByte()));return new Visual(owner,uuid,epoch,input,token,sequence,phase,status,remaining,feet,panels,center,normal,path,b.readBoolean(),b.readUnsignedByte(),b.readUnsignedByte());
        });
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
