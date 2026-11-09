package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.ConductiveCircuitRules;
import cn.academy.port.highpower.core.ConductiveCircuitRules.Phase;
import cn.academy.port.highpower.core.ConductiveCircuitRules.Point;
import cn.academy.port.highpower.core.HighPowerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityTeleportEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;

/** NEW-121: two explicit ground placements, bounded pulses and actual sampled crossings. No entities/world edits. */
@EventBusSubscriber(modid="academy")
public final class ConductiveCircuit {
    public static final String ID="conductive_circuit";
    private static final String ATTEMPT="academy:conductive_circuit_attempt";
    private static final BigDecimal FACTOR=new BigDecimal("0.6");
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static long nextToken;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input) {}
    private record Anchor(BlockPos block,Vec3 point) {}
    private record Sample(LivingEntity entity,Vec3 feet,long tick) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final int preset;
        final long revision,started;
        final Anchor a;
        Anchor b;
        List<BlockPos> support=List.of();
        final Set<ServerPlayer> audience=new LinkedHashSet<>();
        Map<UUID,Sample> samples=new HashMap<>();
        Phase phase=Phase.FIRST_ANCHOR;
        long lastTap,latestInput,lastTick=Long.MIN_VALUE,pulseStart,sequence;
        int attempts;
        boolean processing;
        Context(ServerPlayer p,HighPowerInput.Input input,Anchor a){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));
            this.input=input;this.a=a;preset=identity.state().presets.current();revision=identity.state().presets.revision();
            started=lastTap=now(p);latestInput=input.input();
        }
    }
    private ConductiveCircuit() {}
    public static boolean tap(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input)||AbilityConsumption.busy(p))return false;
        Watermark old=INPUTS.get(p.getUUID());
        if(old!=null&&old.epoch==input.epoch()&&input.input()<=old.input)return false;
        INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        if(!canAct(p)||!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())))return false;
        Context c=CONTEXTS.get(p.getUUID());
        if(c!=null){
            if(!valid(c)||c.identity.owner()!=p){finish(c);return false;}
            if(c.processing||c.input.slot()!=input.slot()||now(p)<=c.lastTap)return false;
            c.lastTap=now(p);c.latestInput=input.input();
            if(p.isShiftKeyDown()){finish(c);return true;}
            if(c.phase==Phase.ENERGIZED)return false;
            if(c.b==null){
                Anchor b=lookingGround(p);List<BlockPos> support=buildSupport(p,c.a,b);
                if(support==null)return false;
                c.b=b;c.support=support;c.phase=Phase.RESTING;send(c);return true;
            }
            if(!ElectromasterPrototype.trySpend(p,ID,HighPowerGrowth.values(p).lv2Cost(),1))return false;
            c.phase=Phase.ENERGIZED;c.pulseStart=now(p);c.attempts=0;c.samples.clear();
            // Initial position snapshot only: activation never damages stationary occupants or replays resting motion.
            sampleAndCross(c,false);send(c);return true;
        }
        if(p.isShiftKeyDown()||!AbilityStorage.get(p).canUse(ID)||nextToken==Long.MAX_VALUE)return false;
        Anchor a=lookingGround(p);if(a==null)return false;
        c=new Context(p,input,a);CONTEXTS.put(p.getUUID(),c);send(c);return true;
    }
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Watermark old=INPUTS.get(p.getUUID());
        if(old==null||old.epoch!=input.epoch()||old.input<input.input())INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        Context c=owned(p);
        if(c==null||c.input.epoch()!=input.epoch()||c.input.slot()!=input.slot()||c.input.input()>input.input()||c.latestInput>input.input())return false;
        finish(c);return true;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c);}
    public static void remove(ServerPlayer p){if(!thread(p))return;abort(p);INPUTS.remove(p.getUUID());}
    public static boolean active(ServerPlayer p){return owned(p)!=null;}
    public static Phase phase(ServerPlayer p){Context c=owned(p);return c==null?Phase.ENDED:c.phase;}
    public static int attemptedCount(ServerPlayer p){Context c=owned(p);return c==null?0:c.attempts;}
    public static void clear(){CONTEXTS.clear();INPUTS.clear();/* Tokens never rewind in this JVM. */}
    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.processing)return;
        long tick=now(p);
        if(!valid(c)||ConductiveCircuitRules.expired(c.started,tick)){finish(c);return;}
        if(tick<=c.lastTick)return;c.lastTick=tick;
        if(c.phase==Phase.ENERGIZED){
            if(tick<c.pulseStart||tick-c.pulseStart>=ConductiveCircuitRules.PULSE_TICKS){c.phase=Phase.RESTING;c.samples.clear();send(c);}
            else{c.processing=true;try{sampleAndCross(c,true);}finally{c.processing=false;}}
        }
        if(tick%4==0&&CONTEXTS.get(p.getUUID())==c)send(c);
    }
    private static void sampleAndCross(Context c,boolean attack){
        ServerPlayer p=c.identity.owner();ServerLevel world=c.identity.level();long tick=now(p);
        AABB area=new AABB(c.a.point,c.b.point).inflate(ConductiveCircuitRules.MAX_STEP+.1,.25,ConductiveCircuitRules.MAX_STEP+.1);
        if(!loadedArea(world,area)){finish(c);return;}
        List<LivingEntity> found=new ArrayList<>(ConductiveCircuitRules.SCAN_LIMIT);
        world.getEntities(EntityTypeTest.forClass(LivingEntity.class),area,e->cheapEligible(p,e),found,ConductiveCircuitRules.SCAN_LIMIT);
        found.sort(Comparator.comparingInt(LivingEntity::getId));Map<UUID,Sample> next=new HashMap<>();
        if(attack&&Boolean.getBoolean("academy.debug.lease"))com.mojang.logging.LogUtils.getLogger().info("CIRCUIT_PULSE tick={} a={} b={} found={}",tick,c.a.point,c.b.point,found.stream().map(e->e.getType().toShortString()+"@"+e.position()).toList());
        Point a=point(c.a.point.add(0,-.05,0)),b=point(c.b.point.add(0,-.05,0));
        for(LivingEntity target:found){
            Vec3 feet=target.position();Sample old=c.samples.get(target.getUUID());
            next.put(target.getUUID(),new Sample(target,feet,tick));
            if(!attack||c.attempts>=ConductiveCircuitRules.MAX_ATTEMPTS||old==null||old.entity!=target
                    ||!ConductiveCircuitRules.crosses(a,b,point(old.feet),point(feet),old.tick,tick)
                    ||!canHit(p,target,old.feet,feet)||!targetIntervalReady(target,tick))continue;
            // Reserve before native hooks. Cancellation still consumes this attempt and global target interval.
            target.getPersistentData().putLong(ATTEMPT,tick);c.attempts++;
            int level=ElectromasterPrototype.effectiveLevel(p);
            HighPowerCombat.attack(p,"electromaster."+ID,target,HighPowerGrowth.values(p).damage().multiply(FACTOR));
            if(CONTEXTS.get(p.getUUID())!=c||!valid(c))return;
        }
        c.samples=next;
    }
    private static boolean targetIntervalReady(LivingEntity target,long tick){
        var tag=target.getPersistentData();
        boolean recorded=tag.contains(ATTEMPT);
        if(recorded&&!tag.contains(ATTEMPT,net.minecraft.nbt.Tag.TAG_LONG))return false;
        return ConductiveCircuitRules.intervalReady(recorded,tag.getLong(ATTEMPT),tick);
    }
    private static boolean cheapEligible(ServerPlayer p,LivingEntity e){
        if(e==null||e==p||e.isRemoved()||!e.isAlive()||e.isSpectator()||e.level()!=p.level()
                ||p.serverLevel().getEntity(e.getId())!=e||!HighPowerCombat.supported(e)||!finite(e.position())
                ||p.isAlliedTo(e)||e.isAlliedTo(p)||e.isPassenger())return false;
        if(e instanceof Player player)return !player.getAbilities().invulnerable&&!player.getAbilities().flying
                &&AcademyConfig.ATTACK_PLAYERS.get()&&(!(player instanceof ServerPlayer other)||p.canHarmPlayer(other));
        // An enemy is a reviewed hostile class, or a reviewed mob currently targeting this caster.
        return HighPowerCombat.hostileTarget(e)||e instanceof Mob mob&&mob.getTarget()==p;
    }
    private static boolean canHit(ServerPlayer p,LivingEntity target,Vec3 before,Vec3 after){
        if(!cheapEligible(p,target)||HighPowerCombat.unsupportedReason(target).isPresent()
                ||!permitted(p,BlockPos.containing(after))||!loadedArea(p.serverLevel(),new AABB(before,after)))return false;
        // Foot motion through an intervening collider cannot count as a physical crossing.
        return p.serverLevel().clip(new ClipContext(before.add(0,.1,0),after.add(0,.1,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p)).getType()==HitResult.Type.MISS;
    }
    private static boolean canAct(ServerPlayer p){
        AbilityProgress s=AbilityStorage.get(p);
        return ElectromasterPrototype.ready(p)&&s.activated&&s.overloadFine&&s.learned(ID)&&s.category.equals("electromaster")
                &&ElectromasterPrototype.effectiveLevel(p)>=2&&SkillCatalog.enabled("electromaster",ID)
                &&HighPowerCombat.unsupportedReason(p).isEmpty()&&!AbilityConsumption.busy(p)&&finite(p.position())
                &&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot());
    }
    private static boolean valid(Context c){
        ServerPlayer p=c.identity.owner();
        return thread(p)&&c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canAct(p)
                &&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision()
                &&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))
                &&!ConductiveCircuitRules.expired(c.started,now(p))&&anchorValid(p,c.a)
                &&(c.b==null||anchorValid(p,c.b)&&supportValid(p,c.support));
    }
    private static Anchor lookingGround(ServerPlayer p){
        Vec3 from=p.getEyePosition(),look=p.getLookAngle();if(!finite(from)||!finite(look)||look.lengthSqr()<.5)return null;
        Vec3 desired=from.add(look.normalize().scale(ConductiveCircuitRules.PLACEMENT_RANGE));
        Point bounded=ConductiveCircuitRules.rayEndWithinHeight(point(from),point(desired),p.serverLevel().getMinBuildHeight(),p.serverLevel().getMaxBuildHeight());
        if(bounded==null)return null;
        Vec3 end=new Vec3(bounded.x(),bounded.y(),bounded.z());
        if(!loadedArea(p.serverLevel(),new AABB(from,end)))return null;
        var hit=p.serverLevel().clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
        if(hit.getType()!=HitResult.Type.BLOCK||hit.getDirection()!=Direction.UP)return null;
        Anchor a=new Anchor(hit.getBlockPos().immutable(),hit.getLocation().add(0,.05,0));
        return anchorValid(p,a)?a:null;
    }
    private static boolean anchorValid(ServerPlayer p,Anchor a){
        if(a==null||!finite(a.point)||p.position().distanceToSqr(a.point)>ConductiveCircuitRules.OWNER_RANGE*ConductiveCircuitRules.OWNER_RANGE
                ||Math.abs(a.point.y-(a.block.getY()+1.05))>1e-6)return false;
        return supportValid(p,List.of(a.block));
    }
    private static List<BlockPos> buildSupport(ServerPlayer p,Anchor a,Anchor b){
        if(a==null||b==null||!ConductiveCircuitRules.segmentValid(point(a.point),point(b.point)))return null;
        AABB area=new AABB(a.point,b.point).inflate(ConductiveCircuitRules.CORRIDOR,.1,ConductiveCircuitRules.CORRIDOR);
        if(!loadedArea(p.serverLevel(),area))return null;
        List<BlockPos> result=new ArrayList<>();Point start=point(a.point),end=point(b.point);int y=a.block.getY();
        for(int x=(int)Math.floor(area.minX);x<=(int)Math.floor(area.maxX);x++)
            for(int z=(int)Math.floor(area.minZ);z<=(int)Math.floor(area.maxZ);z++)
                if(ConductiveCircuitRules.intersectsCell(start,end,x,z,ConductiveCircuitRules.CORRIDOR))result.add(new BlockPos(x,y,z));
        return result.size()<=132&&supportValid(p,result)?List.copyOf(result):null;
    }
    private static boolean supportValid(ServerPlayer p,List<BlockPos> support){
        if(support.isEmpty())return false;ServerLevel world=p.serverLevel();
        for(BlockPos pos:support){
            BlockPos above=pos.above();if(!permitted(p,pos)||!permitted(p,above))return false;
            var floor=world.getBlockState(pos);var air=world.getBlockState(above);
            if(!floor.getFluidState().isEmpty()||!Block.isShapeFullBlock(floor.getCollisionShape(world,pos))
                    ||!air.getFluidState().isEmpty()||!air.getCollisionShape(world,above).isEmpty())return false;
        }
        return true;
    }
    private static boolean permitted(ServerPlayer p,BlockPos pos){
        ServerLevel world=p.serverLevel();return !world.isOutsideBuildHeight(pos)&&world.hasChunkAt(pos)
                &&world.getWorldBorder().isWithinBounds(pos)&&world.mayInteract(p,pos);
    }
    private static boolean loadedArea(ServerLevel world,AABB box){
        if(!finite(new Vec3(box.minX,box.minY,box.minZ))||!finite(new Vec3(box.maxX,box.maxY,box.maxZ))
                ||box.getXsize()>37||box.getYsize()>33||box.getZsize()>37||box.minY<world.getMinBuildHeight()||box.maxY>=world.getMaxBuildHeight())return false;
        for(int x=((int)Math.floor(box.minX))>>4;x<=((int)Math.floor(box.maxX))>>4;x++)
            for(int z=((int)Math.floor(box.minZ))>>4;z<=((int)Math.floor(box.maxZ))>>4;z++)if(!world.hasChunk(x,z))return false;
        return true;
    }
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static long now(ServerPlayer p){return p.serverLevel().getServer().overworld().getGameTime();}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static Point point(Vec3 v){return new Point(v.x,v.y,v.z);}
    private static void finish(Context c){CONTEXTS.remove(c.identity.owner().getUUID(),c);c.phase=Phase.ENDED;c.samples.clear();send(c);c.audience.clear();}
    private static void send(Context c){
        ServerPlayer p=c.identity.owner();Vec3 end=c.b==null?c.a.point:c.b.point;
        for(ServerPlayer viewer:c.identity.level().players())if(viewer.position().distanceToSqr(c.a.point)<=48*48||viewer.position().distanceToSqr(end)<=48*48)c.audience.add(viewer);
        c.audience.add(p);int phase=c.phase==Phase.ENDED?2:c.phase==Phase.ENERGIZED?1:0;
        Visual packet=new Visual(p.getId(),p.getUUID(),c.identity.token(),c.identity.epoch(),++c.sequence,phase,c.a.point,end,
                (int)Math.max(0,Math.min(c.phase==Phase.ENERGIZED?ConductiveCircuitRules.PULSE_TICKS-(now(p)-c.pulseStart):ConductiveCircuitRules.LIFETIME,ConductiveCircuitRules.LIFETIME-(now(p)-c.started))));
        for(ServerPlayer viewer:c.audience)if(viewer.connection!=null&&!viewer.hasDisconnected()&&viewer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(viewer,packet);
    }
    public record Visual(int entity,UUID owner,long token,long epoch,long sequence,int phase,Vec3 start,Vec3 end,int remaining)implements CustomPacketPayload {
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_conductive_circuit"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.entity);b.writeUUID(v.owner);b.writeLong(v.token);b.writeLong(v.epoch);b.writeLong(v.sequence);b.writeByte(v.phase);
            b.writeDouble(v.start.x);b.writeDouble(v.start.y);b.writeDouble(v.start.z);b.writeDouble(v.end.x);b.writeDouble(v.end.y);b.writeDouble(v.end.z);b.writeShort(v.remaining);
        },b->new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readUnsignedByte(),
                new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),new Vec3(b.readDouble(),b.readDouble(),b.readDouble()),b.readUnsignedShort()));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){event.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}
    }
    @SubscribeEvent public static void playerTick(PlayerTickEvent.Post event){if(event.getEntity() instanceof ServerPlayer p)tick(p);}
    @SubscribeEvent public static void teleport(EntityTeleportEvent event){
        // Native command/pearl/chorus/enderman teleports never manufacture a movement segment, even if later cancelled.
        if(event.getEntity().level().isClientSide)return;UUID id=event.getEntity().getUUID();
        for(Context c:CONTEXTS.values())c.samples.remove(id);
        if(event.getEntity() instanceof ServerPlayer p)abort(p);
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent event){if(event.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone event){if(event.getOriginal() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent event){if(event.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent event){if(!event.isCanceled()&&event.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void stopped(ServerStoppedEvent event){clear();}
}
