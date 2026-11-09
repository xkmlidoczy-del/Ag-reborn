package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.RailThunderArrayRules;
import io.netty.handler.codec.DecoderException;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW151. Server-observed marks, nine fixed allocations or three fixed net pulses; no terrain or motion writes. */
@EventBusSubscriber(modid="academy")
public final class RailThunderArray {
    public static final String ID="rail_thunder_array";
    public static final int PREPARING=0, ARMED=1, TAP=2, HOLD=3, ENDED=4;
    public static final int PREVIEW=0, MISS=1, HIT=2, REFUSED=3, BLOCKED=4;
    public static final int NORMAL=0, REFUSAL=1, EXPIRED=2, INVALIDATED=3, CLEARED=4;
    public static final String RECAST="rail_thunder_array_recast_until", MARK="rail_thunder_array_mark_until";
    private static final String ROOT="academy:electromaster_prototype", TOKEN="rail_thunder_array_token";
    private static final UUID NONE=new UUID(0,0);
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private record Watermark(Object connection,Object state,long epoch,long input){}
    private static final Map<ServerPlayer,Watermark> INPUTS=new IdentityHashMap<>();
    public static Consumer<Visual> clientReceiver=ignored->{};
    public record Segment(int axis,int entity,UUID target,Vec3 from,Vec3 to,int status){}
    private record Ray(Vec3 end,LivingEntity blocker,boolean blocked,boolean capped){}
    private record Admission(int round,long tick){}
    private static final class Lock {
        final LivingEntity target;final UUID uuid;final int id,axis;boolean retired;
        Lock(LivingEntity target,int axis){this.target=target;uuid=target.getUUID();id=target.getId();this.axis=axis;}
    }
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;final int preset;final long revision,started;
        final Vec3 preparationReference;final List<Vec3> axes=new ArrayList<>(3);
        final List<Lock> locks=new ArrayList<>(16);final Map<UUID,Admission> admissions=new HashMap<>();
        final Set<ServerPlayer> audience=new LinkedHashSet<>();
        HighPowerInput.Input input;Vec3 origin,commitReference;long pressed,committed=-1,lastTick=-1,lastSend=-1,sequence;
        int phase=PREPARING,nextSlot,pulses,examined;boolean down,processing,paid,awarded;
        BigDecimal budget;List<BigDecimal> shares=List.of();List<List<BigDecimal>> pulseShares=List.of();
        List<Segment> lastSegments=List.of();
        Context(ServerPlayer p,HighPowerInput.Input input,long token){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),token,AbilityStorage.get(p));this.input=input;
            preset=identity.state().presets.current();revision=identity.state().presets.revision();started=now(p);
            preparationReference=p.position();origin=p.getEyePosition();
        }
    }
    private RailThunderArray(){}

    /** Only a bounded authenticated identity is accepted. No client aim, hit, mode or timing authority. */
    public static boolean press(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input)||!claimInput(p,input))return false;
        Context c=CONTEXTS.get(p.getUUID());
        if(c!=null&&(c.identity.owner()!=p||!valid(c))){finish(c,INVALIDATED);c=null;}
        if(c!=null&&(c.processing||c.phase==TAP||c.phase==HOLD||c.down))return false;
        if(!canAct(p)||!AbilityStorage.get(p).canUse(ID)||!fenceReady(p,RECAST)||!fenceReady(p,MARK)
                ||!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())))return false;
        stamp(p,MARK,now(p)+RailThunderArrayRules.MARK_INTERVAL);
        if(p.isShiftKeyDown()){if(c!=null)finish(c,CLEARED);return true;}
        if(c==null){long token=nextToken(p);if(token<=0)return false;c=new Context(p,input,token);CONTEXTS.put(p.getUUID(),c);}
        if(c.input.slot()!=input.slot()||c.input.epoch()!=input.epoch())return false;
        c.input=input;c.down=true;c.pressed=now(p);c.phase=c.axes.size()==3?ARMED:PREPARING;
        send(c,previews(c),NORMAL);return true;
    }
    public static boolean release(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context c=owned(p);if(c==null||!c.input.equals(input)||!c.down||c.processing)return false;
        if(!valid(c)){finish(c,INVALIDATED);return false;}
        c.down=false;
        if(c.phase==HOLD||RailThunderArrayRules.elapsed(c.pressed,now(p),RailThunderArrayRules.HOLD_THRESHOLD)){
            // A late release does not manufacture a missed pulse or turn a held fourth press into a tap.
            if(c.phase==HOLD||c.phase==ARMED)finish(c,NORMAL);else send(c,previews(c),NORMAL);
            return true;
        }
        if(c.phase==PREPARING){
            Vec3 axis=p.getLookAngle().normalize();
            if(!RailThunderArrayRules.distinct(point(axis),c.axes.stream().map(RailThunderArray::point).toList())){
                feedback(p,"duplicate_direction");send(c,previews(c),REFUSAL);return false;
            }
            c.axes.add(axis);send(c,previews(c),NORMAL);return true;
        }
        if(c.phase!=ARMED)return false;
        c.processing=true;
        try{if(!commit(c,false)){finish(c,REFUSAL);return false;}emitTap(c);return true;}finally{c.processing=false;}
    }
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        fenceInput(p,input);Context c=owned(p);
        if(c==null||input.epoch()!=c.input.epoch()||input.slot()!=c.input.slot()||input.input()<c.input.input())return false;
        finish(c,CLEARED);return true;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c,CLEARED);}
    public static void remove(ServerPlayer p){if(thread(p)){abort(p);INPUTS.remove(p);}}
    public static void clear(){for(Context c:new ArrayList<>(CONTEXTS.values()))finish(c,INVALIDATED);CONTEXTS.clear();INPUTS.clear();}
    public static boolean active(ServerPlayer p){return owned(p)!=null;}
    public static int markCount(ServerPlayer p){Context c=owned(p);return c==null?0:c.axes.size();}
    public static int pulseCount(ServerPlayer p){Context c=owned(p);return c==null?0:c.pulses;}
    public static int lockedCount(ServerPlayer p){Context c=owned(p);return c==null?0:(int)c.locks.stream().filter(l->!l.retired).count();}
    public static int examinedCount(ServerPlayer p){Context c=owned(p);return c==null?0:c.examined;}
    public static List<Segment> serverSegments(ServerPlayer p){Context c=owned(p);return c==null?List.of():c.lastSegments;}
    public static BigDecimal castBudget(ServerPlayer p){Context c=owned(p);return c==null?null:c.budget;}
    public static int phase(ServerPlayer p){Context c=owned(p);return c==null?ENDED:c.phase;}
    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.processing)return;
        if(!valid(c)){finish(c,INVALIDATED);return;}
        long tick=now(p);if(tick<=c.lastTick)return;c.lastTick=tick;c.processing=true;
        try{
            if(c.phase==ARMED&&c.down&&RailThunderArrayRules.elapsed(c.pressed,tick,RailThunderArrayRules.HOLD_THRESHOLD)){
                if(!commit(c,true)){finish(c,REFUSAL);return;}
            }
            if(c.phase==TAP)emitTap(c);
            else if(c.phase==HOLD){
                prune(c);if(c.locks.stream().allMatch(l->l.retired)){finish(c,EXPIRED);return;}
                if(tick-c.committed>=RailThunderArrayRules.HOLD_TICKS){finish(c,EXPIRED);return;}
                emitHold(c);
            }
            if(CONTEXTS.get(p.getUUID())==c&&tick-c.lastSend>=6)send(c,c.phase<TAP?previews(c):List.of(),NORMAL);
        }finally{c.processing=false;}
    }
    private static boolean commit(Context c,boolean hold){
        if(!valid(c)||c.axes.size()!=3)return false;
        ServerPlayer p=c.identity.owner();c.budget=RailThunderArrayRules.budget(HighPowerGrowth.values(p).damage());
        if(c.budget==null)return false;
        c.committed=now(p);c.commitReference=p.position();c.origin=p.getEyePosition();c.phase=hold?HOLD:TAP;
        if(hold){
            select(c);if(c.locks.isEmpty()){feedback(p,"no_targets");return false;}
            List<BigDecimal> budgets=RailThunderArrayRules.split(c.budget,3);List<List<BigDecimal>> portions=new ArrayList<>(3);
            for(BigDecimal budget:budgets)portions.add(RailThunderArrayRules.split(budget,c.locks.size()));c.pulseShares=List.copyOf(portions);
        }else c.shares=RailThunderArrayRules.split(c.budget,9);
        send(c,List.of(),NORMAL);return true;
    }
    private static void emitTap(Context c){
        long age=now(c.identity.owner())-c.committed;
        while(c.nextSlot<9&&RailThunderArrayRules.rayTick(c.nextSlot)<=age){
            int slot=c.nextSlot++;
            // Missing server time consumes the fixed share; it never compresses a round into a burst.
            if(RailThunderArrayRules.rayTick(slot)!=age)continue;
            if(!valid(c)){finish(c,INVALIDATED);return;}
            int axis=slot%3,round=slot/3;Ray ray=ray(c,c.axes.get(axis));LivingEntity target=ray.blocker;
            int status=ray.blocked?BLOCKED:MISS;
            if(target!=null){
                status=REFUSED;Admission prior=c.admissions.get(target.getUUID());
                if(!ray.capped&&eligible(c.identity.owner(),target)&&target.invulnerableTime<=10&&admittedAmount(c.shares.get(slot))
                        &&(prior==null||prior.round!=round&&now(c.identity.owner())-prior.tick>=RailThunderArrayRules.ENTITY_INTERVAL)){
                    c.admissions.put(target.getUUID(),new Admission(round,now(c.identity.owner())));
                    if(!pay(c,1)){finish(c,REFUSAL);return;}
                    status=attack(c,target,c.shares.get(slot),true,axis,ray.end)?HIT:REFUSED;
                }
            }
            if(CONTEXTS.get(c.identity.owner().getUUID())!=c)return;
            send(c,List.of(new Segment(axis,target==null?-1:target.getId(),target==null?NONE:target.getUUID(),c.origin,ray.end,status)),NORMAL);
        }
        if(c.nextSlot>=9)finish(c,NORMAL);
    }
    private static void emitHold(Context c){
        long age=now(c.identity.owner())-c.committed;
        while(c.nextSlot<3&&c.nextSlot*RailThunderArrayRules.PULSE_INTERVAL<=age){
            int pulse=c.nextSlot++;
            if(pulse*RailThunderArrayRules.PULSE_INTERVAL!=age)continue;
            prune(c);List<Integer> targets=new ArrayList<>(16);
            for(int i=0;i<c.locks.size();i++){Lock l=c.locks.get(i);if(!l.retired&&l.target.invulnerableTime<=10&&admittedAmount(c.pulseShares.get(pulse).get(i)))targets.add(i);}
            if(targets.isEmpty())continue;
            if(!pay(c,RailThunderArrayRules.pulseCost(pulse))){finish(c,REFUSAL);return;}
            c.pulses++;List<Segment> segments=new ArrayList<>(16);
            for(int slot:targets){
                if(!valid(c)){finish(c,INVALIDATED);return;}
                Lock lock=c.locks.get(slot);if(!validLock(c,lock)){lock.retired=true;continue;}
                Vec3 to=lock.target.getBoundingBox().getCenter();
                boolean accepted=attack(c,lock.target,c.pulseShares.get(pulse).get(slot),false,lock.axis,to);
                if(CONTEXTS.get(c.identity.owner().getUUID())!=c)return;
                segments.add(new Segment(lock.axis,lock.id,lock.uuid,c.origin,to,accepted?HIT:REFUSED));
            }
            send(c,segments,NORMAL);prune(c);
            if(c.locks.stream().allMatch(l->l.retired)){finish(c,EXPIRED);return;}
        }
    }
    private static boolean pay(Context c,double amount){
        if(c.phase==TAP&&c.paid)return true;
        ServerPlayer p=c.identity.owner();if(!valid(c)||!ElectromasterPrototype.trySpend(p,ID,amount,1)){feedback(p,"cannot_pay");return false;}
        c.paid=true;
        // Crash/reload cannot erase an ongoing paid controller and exploit its internal one-tick timer.
        stamp(p,RECAST,Math.max(now(p)+4,c.committed+(c.phase==HOLD?60:47)+4));
        HighPowerNaturalRecovery.offensiveAction(p);return true;
    }
    private static boolean attack(Context c,LivingEntity target,BigDecimal amount,boolean physical,int axis,Vec3 endpoint){
        ServerPlayer p=c.identity.owner();Vec3 position=p.position(),targetPosition=target.position();AABB box=target.getBoundingBox();
        BigDecimal before=health(target),shield=HighPowerCombat.shield(target);float absorption=target.getAbsorptionAmount();
        boolean accepted=HighPowerCombat.attackRailThunderArray(p,target,amount,physical,c.origin,()->CONTEXTS.get(p.getUUID())==c&&c.paid&&valid(c)
                &&p.position().equals(position)&&target.position().equals(targetPosition)&&target.getBoundingBox().equals(box)
                &&eligible(p,target)&&(physical?rayStillHits(c,axis,target,endpoint):intersects(c,box,axis)&&clearLine(p,c.origin,box.getCenter())));
        boolean productive=accepted&&(health(target).compareTo(before)<0||HighPowerCombat.shield(target).compareTo(shield)<0||target.getAbsorptionAmount()<absorption);
        if(productive&&!c.awarded&&c.identity.owns(p)){
            c.awarded=true;c.identity.state().addExperience(ID,.005);AbilityStorage.save(p);
        }
        if(CONTEXTS.get(p.getUUID())==c&&(!valid(c)||!p.position().equals(position)))finish(c,INVALIDATED);
        return accepted;
    }
    private static boolean admittedAmount(BigDecimal amount){return amount.signum()>0&&amount.compareTo(new BigDecimal("1e20"))<=0;}
    private static boolean rayStillHits(Context c,int axis,LivingEntity target,Vec3 endpoint){Ray checked=ray(c,c.axes.get(axis));return !checked.capped&&checked.blocker==target&&checked.end.equals(endpoint)&&clearLine(c.identity.owner(),c.origin,endpoint);}
    private static BigDecimal health(LivingEntity e){if(HighPowerCombat.enabled(e))try{return new BigDecimal(HighPowerCombat.healthText(e));}catch(NumberFormatException ignored){}return BigDecimal.valueOf(e.getHealth());}
    private static void select(Context c){
        ServerPlayer p=c.identity.owner();List<LivingEntity> scanned=new ArrayList<>(64);
        p.serverLevel().getEntities(EntityTypeTest.forClass(LivingEntity.class),new AABB(c.origin,c.origin).inflate(96),e->e!=p&&e.isAlive()&&!e.isRemoved(),scanned,64);c.examined=scanned.size();
        // A capped query does not establish a complete deterministic candidate set: fail closed, without payment.
        if(scanned.size()>=64)return;
        scanned.removeIf(e->!eligible(p,e)||!clearLine(p,c.origin,e.getBoundingBox().getCenter())||firstAxis(c,e.getBoundingBox())<0);
        scanned.sort(Comparator.<LivingEntity>comparingDouble(e->e.getBoundingBox().getCenter().distanceToSqr(c.origin)).thenComparing(e->e.getUUID().toString()).thenComparingInt(Entity::getId));
        for(LivingEntity e:scanned){if(c.locks.size()==16)break;c.locks.add(new Lock(e,firstAxis(c,e.getBoundingBox())));}
    }
    private static boolean validLock(Context c,Lock l){return !l.retired&&l.target.getId()==l.id&&l.target.getUUID().equals(l.uuid)&&eligible(c.identity.owner(),l.target)
            &&intersects(c,l.target.getBoundingBox(),l.axis)&&clearLine(c.identity.owner(),c.origin,l.target.getBoundingBox().getCenter());}
    private static void prune(Context c){for(Lock l:c.locks)if(!validLock(c,l))l.retired=true;}
    private static int firstAxis(Context c,AABB box){for(int i=0;i<c.axes.size();i++)if(intersects(c,box,i))return i;return -1;}
    private static boolean intersects(Context c,AABB box,int axis){
        if(axis<0||axis>=c.axes.size()||box.getCenter().distanceToSqr(c.origin)>96*96)return false;
        // A target center must project onto the finite forward axis; the radius never wraps behind the caster.
        double projection=box.getCenter().subtract(c.origin).dot(c.axes.get(axis));if(projection<0||projection>96)return false;
        var endpoint=RailThunderArrayRules.endpoint(point(c.origin),point(c.axes.get(axis)));if(endpoint==null)return false;Vec3 end=vector(endpoint);double low=0,high=1;
        for(int i=0;i<48;i++){double a=low+(high-low)/3,b=high-(high-low)/3;if(boxDistance(box,c.origin.lerp(end,a))<boxDistance(box,c.origin.lerp(end,b)))high=b;else low=a;}
        return Math.min(Math.min(boxDistance(box,c.origin),boxDistance(box,end)),boxDistance(box,c.origin.lerp(end,(low+high)/2)))<=4;
    }
    private static double boxDistance(AABB b,Vec3 p){double x=Math.max(Math.max(b.minX-p.x,0),p.x-b.maxX),y=Math.max(Math.max(b.minY-p.y,0),p.y-b.maxY),z=Math.max(Math.max(b.minZ-p.z,0),p.z-b.maxZ);return x*x+y*y+z*z;}
    /** The first physical living blocker stops the ray even if allied, protected or unsupported.
     * Each bounded native query returns at most64 entries; the fixed native hook pipeline revalidates separately. */
    private static Ray ray(Context c,Vec3 axis){
        ServerPlayer p=c.identity.owner();var endpoint=RailThunderArrayRules.endpoint(point(c.origin),point(axis));if(endpoint==null)return new Ray(c.origin,null,true,false);
        Vec3 full=vector(endpoint),end=permittedEnd(p,c.origin,full);
        if(end.equals(c.origin))return new Ray(c.origin,null,true,false);
        var clip=p.serverLevel().clip(new ClipContext(c.origin,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));boolean blocked=!end.equals(full)||clip.getType()!=HitResult.Type.MISS;
        if(clip.getType()!=HitResult.Type.MISS)end=clip.getLocation();
        List<LivingEntity> scanned=new ArrayList<>(64);
        p.serverLevel().getEntities(EntityTypeTest.forClass(LivingEntity.class),new AABB(c.origin,end).inflate(.01),e->e!=p&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator(),scanned,64);c.examined=scanned.size();
        if(scanned.size()>=64)return new Ray(c.origin,null,true,true);
        LivingEntity first=null;double distance=c.origin.distanceToSqr(end);Vec3 hit=end;
        scanned.sort(Comparator.comparingInt(Entity::getId));
        for(LivingEntity e:scanned){Optional<Vec3> intersection=e.getBoundingBox().clip(c.origin,end);if(e.getBoundingBox().contains(c.origin))intersection=Optional.of(c.origin);
            if(intersection.isPresent()){double d=c.origin.distanceToSqr(intersection.get());if(d<distance||d==distance&&!blocked&&first==null){distance=d;first=e;hit=intersection.get();}}}
        return new Ray(hit,first,blocked,false);
    }
    private static boolean eligible(ServerPlayer p,LivingEntity e){
        if(e==null||e==p||!e.isAlive()||e.isRemoved()||e.isSpectator()||e.level()!=p.serverLevel()
                ||p.serverLevel().getEntity(e.getId())!=e||p.serverLevel().getEntity(e.getUUID())!=e||!HighPowerCombat.supported(e)
                ||HighPowerCombat.unsupportedReason(e).isPresent()||e.isInvulnerable()||p.isAlliedTo(e)||e.isAlliedTo(p)
                ||!finite(e.position())||!permittedVolume(p,e.getBoundingBox()))return false;
        if(e instanceof Player)return e.getClass()==ServerPlayer.class&&e instanceof ServerPlayer other&&other.connection!=null&&!other.hasDisconnected()
                &&!other.getAbilities().invulnerable&&AcademyConfig.ATTACK_PLAYERS.get()&&p.server.isPvpAllowed()&&p.canHarmPlayer(other)&&other.canHarmPlayer(p);
        return HighPowerCombat.hostileTarget(e)||e instanceof Mob m&&m.getTarget()==p;
    }
    private static boolean canAct(ServerPlayer p){
        if(!thread(p))return false;var state=AbilityStorage.get(p);Vec3 look=p.getLookAngle();
        return ElectromasterPrototype.ready(p)&&state.activated&&state.overloadFine&&state.learned(ID)&&state.category.equals("electromaster")
                &&ElectromasterPrototype.effectiveLevel(p)>=5&&SkillCatalog.enabled("electromaster",ID)&&HighPowerCombat.unsupportedReason(p).isEmpty()
                &&!AbilityConsumption.busy(p)&&!p.getAbilities().instabuild&&p.containerMenu==p.inventoryMenu
                &&finite(p.position())&&finite(p.getEyePosition())&&finite(look)&&look.lengthSqr()>1e-12&&RailThunderArrayRules.unit(point(look.normalize()))
                &&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())&&RailThunderArrayRules.clock(now(p));
    }
    private static boolean valid(Context c){
        ServerPlayer p=c.identity.owner();if(CONTEXTS.get(p.getUUID())!=c||!c.identity.owns(p)||!HighPowerBracerSession.authenticated(p,c.input)||!canAct(p))return false;
        return c.identity.state().presets.current()==c.preset&&c.identity.state().presets.revision()==c.revision
                &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))&&now(p)>=c.started
                &&p.position().distanceToSqr(c.committed<0?c.preparationReference:c.commitReference)<=1
                &&(c.committed>=0||!RailThunderArrayRules.preparationExpired(c.started,now(p)));
    }
    private static boolean permitted(ServerPlayer p,BlockPos pos){ServerLevel w=p.serverLevel();return !w.isOutsideBuildHeight(pos)&&w.hasChunkAt(pos)&&w.getWorldBorder().isWithinBounds(pos)&&w.mayInteract(p,pos);}
    private static boolean permittedVolume(ServerPlayer p,AABB b){
        if(!finite(new Vec3(b.minX,b.minY,b.minZ))||!finite(new Vec3(b.maxX,b.maxY,b.maxZ))||b.getXsize()>4||b.getYsize()>4||b.getZsize()>4)return false;
        for(int x=(int)Math.floor(b.minX);x<=Math.floor(b.maxX);x++)for(int y=(int)Math.floor(b.minY);y<=Math.floor(b.maxY);y++)for(int z=(int)Math.floor(b.minZ);z<=Math.floor(b.maxZ);z++)if(!permitted(p,new BlockPos(x,y,z)))return false;return true;
    }
    /** Every crossed voxel is checked before native clip. Small conservative cells include diagonal boundaries. */
    private static Vec3 permittedEnd(ServerPlayer p,Vec3 from,Vec3 to){
        if(!finite(from)||!finite(to)||from.distanceToSqr(to)>96*96||!permitted(p,BlockPos.containing(from)))return from;
        int steps=Math.max(1,(int)Math.ceil(from.distanceTo(to)*4));Vec3 previous=from;
        for(int i=1;i<=steps;i++){Vec3 next=from.lerp(to,(double)i/steps);
            for(int x=(int)Math.floor(Math.min(previous.x,next.x));x<=Math.floor(Math.max(previous.x,next.x));x++)
                for(int y=(int)Math.floor(Math.min(previous.y,next.y));y<=Math.floor(Math.max(previous.y,next.y));y++)
                    for(int z=(int)Math.floor(Math.min(previous.z,next.z));z<=Math.floor(Math.max(previous.z,next.z));z++)if(!permitted(p,new BlockPos(x,y,z)))return previous;
            previous=next;
        }return to;
    }
    private static boolean clearLine(ServerPlayer p,Vec3 from,Vec3 to){return finite(from)&&finite(to)&&from.distanceToSqr(to)<=96*96
            &&permittedEnd(p,from,to).equals(to)&&p.serverLevel().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;}
    private static List<Segment> previews(Context c){
        c.origin=c.identity.owner().getEyePosition();List<Segment> result=new ArrayList<>(3);
        for(int i=0;i<c.axes.size();i++){Ray ray=ray(c,c.axes.get(i));result.add(new Segment(i,-1,NONE,c.origin,ray.end,PREVIEW));}return List.copyOf(result);
    }
    private static boolean claimInput(ServerPlayer p,HighPowerInput.Input input){
        Watermark old=INPUTS.get(p);Object state=AbilityStorage.get(p);
        if(old!=null&&old.connection==p.connection&&old.state==state&&old.epoch==input.epoch()&&old.input>=input.input())return false;
        fenceInput(p,input);return true;
    }
    private static void fenceInput(ServerPlayer p,HighPowerInput.Input input){
        Watermark old=INPUTS.get(p);Object state=AbilityStorage.get(p);
        if(old==null||old.connection!=p.connection||old.state!=state||old.epoch!=input.epoch()||old.input<input.input())INPUTS.put(p,new Watermark(p.connection,state,input.epoch(),input.input()));
    }
    private static long nextToken(ServerPlayer p){CompoundTag tag=p.getPersistentData().getCompound(ROOT);if(tag.contains(TOKEN)&&!tag.contains(TOKEN,Tag.TAG_LONG))return -1;
        long old=tag.getLong(TOKEN);if(old<0||old==Long.MAX_VALUE)return -1;tag.putLong(TOKEN,old+1);p.getPersistentData().put(ROOT,tag);return old+1;}
    private static boolean fenceReady(ServerPlayer p,String key){CompoundTag tag=p.getPersistentData().getCompound(ROOT);return !tag.contains(key)||tag.contains(key,Tag.TAG_LONG)&&now(p)>=tag.getLong(key);}
    private static void stamp(ServerPlayer p,String key,long until){CompoundTag tag=p.getPersistentData().getCompound(ROOT);tag.putLong(key,Math.max(tag.getLong(key),until));p.getPersistentData().put(ROOT,tag);}
    private static void finish(Context c,int reason){
        ServerPlayer p=c.identity.owner();if(!CONTEXTS.remove(p.getUUID(),c))return;
        if(c.paid&&thread(p)){
            // An ordinary finish replaces the conservative crash lease with the exact four-tick postcast fence.
            CompoundTag tag=p.getPersistentData().getCompound(ROOT);tag.putLong(RECAST,now(p)+4);tag.putLong("next_"+ID,Math.max(tag.getLong("next_"+ID),p.serverLevel().getGameTime()+4));p.getPersistentData().put(ROOT,tag);
            if(c.identity.owns(p))c.identity.state().setCooldown(ID,4);
        }
        c.phase=ENDED;c.down=false;send(c,List.of(),reason);c.locks.clear();c.admissions.clear();c.audience.clear();
    }
    private static void send(Context c,List<Segment> segments,int reason){
        ServerPlayer owner=c.identity.owner();c.lastSegments=List.copyOf(segments);c.lastSend=now(owner);
        c.audience.removeIf(viewer->viewer!=owner&&(viewer.connection==null||viewer.hasDisconnected()||viewer.isRemoved()||viewer.serverLevel()!=c.identity.level()||viewer.position().distanceToSqr(c.origin)>128*128));
        for(ServerPlayer viewer:c.identity.level().players())if(viewer.connection!=null&&!viewer.hasDisconnected()&&!viewer.isRemoved()&&viewer.position().distanceToSqr(c.origin)<=128*128)c.audience.add(viewer);c.audience.add(owner);
        Visual packet=new Visual(owner.getId(),owner.getUUID(),c.input.input(),c.identity.token(),c.identity.epoch(),++c.sequence,c.phase,(int)Math.clamp(now(owner)-c.started,0,268),c.origin,c.axes,segments,reason);
        for(ServerPlayer viewer:c.audience)if(viewer.connection!=null&&!viewer.hasDisconnected()&&viewer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(viewer,packet);
    }
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.server!=null&&p.server.isSameThread();}
    private static long now(ServerPlayer p){return p.server.overworld().getGameTime();}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static Vec3 vector(RailThunderArrayRules.Point p){return new Vec3(p.x(),p.y(),p.z());}
    private static RailThunderArrayRules.Point point(Vec3 v){return new RailThunderArrayRules.Point(v.x,v.y,v.z);}
    private static void feedback(ServerPlayer p,String key){p.displayClientMessage(Component.translatable("academy.highpower.rail_thunder_array."+key),false);}
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){Vec3 v=new Vec3(b.readDouble(),b.readDouble(),b.readDouble());if(!finite(v))throw new DecoderException("NEW151 finite geometry");return v;}
    private static List<Vec3> readAxes(RegistryFriendlyByteBuf b){int count=b.readVarInt();if(count<0||count>3)throw new DecoderException("NEW151 axes");List<Vec3> result=new ArrayList<>(count);for(int i=0;i<count;i++)result.add(readVec(b));return result;}
    private static List<Segment> readSegments(RegistryFriendlyByteBuf b){int count=b.readVarInt();if(count<0||count>16)throw new DecoderException("NEW151 segments");List<Segment> result=new ArrayList<>(count);for(int i=0;i<count;i++)result.add(new Segment(b.readUnsignedByte(),b.readInt(),b.readUUID(),readVec(b),readVec(b),b.readUnsignedByte()));return result;}
    public record Visual(int entity,UUID owner,long input,long token,long epoch,long sequence,int phase,int age,Vec3 origin,List<Vec3> axes,List<Segment> segments,int reason) implements CustomPacketPayload {
        public Visual{axes=List.copyOf(axes);segments=List.copyOf(segments);if(axes.size()>3||segments.size()>16||phase<0||phase>4||age<0||age>268||!finite(origin))throw new IllegalArgumentException("NEW151 visual bound");
            for(Vec3 axis:axes)if(!RailThunderArrayRules.unit(point(axis)))throw new IllegalArgumentException("NEW151 visual axis");
            for(Segment s:segments)if(s.axis<0||s.axis>=axes.size()||s.status<0||s.status>4||s.target==null||!finite(s.from)||!finite(s.to)||s.from.distanceToSqr(s.to)>96*96+1e-6)throw new IllegalArgumentException("NEW151 visual segment");}
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_rail_thunder_array"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{b.writeInt(v.entity);b.writeUUID(v.owner);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.epoch);b.writeLong(v.sequence);b.writeByte(v.phase);b.writeVarInt(v.age);writeVec(b,v.origin);b.writeVarInt(v.axes.size());for(Vec3 axis:v.axes)writeVec(b,axis);b.writeVarInt(v.segments.size());for(Segment s:v.segments){b.writeByte(s.axis);b.writeInt(s.entity);b.writeUUID(s.target);writeVec(b,s.from);writeVec(b,s.to);b.writeByte(s.status);}b.writeByte(v.reason);},b->new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readUnsignedByte(),b.readVarInt(),readVec(b),readAxes(b),readSegments(b),b.readUnsignedByte()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network{@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}}
    @SubscribeEvent public static void serverTick(ServerTickEvent.Post e){for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.owner().server==e.getServer())tick(c.identity.owner());}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer replacement){
        remove(old);CompoundTag source=old.getPersistentData().getCompound(ROOT),target=replacement.getPersistentData().getCompound(ROOT);
        for(String key:List.of(RECAST,MARK,TOKEN))if(source.contains(key,Tag.TAG_LONG))target.putLong(key,Math.max(target.getLong(key),source.getLong(key)));
        replacement.getPersistentData().put(ROOT,target);
    }}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stopping(ServerStoppingEvent e){for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.owner().server==e.getServer())finish(c,INVALIDATED);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
}
