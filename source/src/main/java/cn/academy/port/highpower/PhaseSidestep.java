package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.PhaseSidestepRules;
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
import net.minecraft.world.entity.RelativeMovement;
import net.minecraft.world.phys.AABB;
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

/** NEW312: manual grounded sidestep. The design's0.15s is timing guidance, never
 * prediction, immunity, rewind or damage cancellation. No mastery is awarded for an unproven dodge. */
@EventBusSubscriber(modid="academy")
public final class PhaseSidestep {
    public static final String ID="phase_sidestep";
    public static final int SUCCESS=1, REFUSED=3;
    private static final String DATA="academy:phase_sidestep_fences";
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Map<ServerPlayer,Receipt> RECEIPTS=new IdentityHashMap<>();
    private static final Map<ServerPlayer,Long> REPLIES=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Integer> CLOCKS=new WeakHashMap<>();
    private static long nextToken;
    private static final boolean DIAGNOSTIC=Boolean.getBoolean("academy.highpower.phaseSidestepDiagnostic");
    private static int diagnosticEvents;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("academy.phase_sidestep");
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(ServerPlayer owner,long epoch,long input){}
    private record Fences(int request,int cooldown){
        static final Fences ZERO=new Fences(0,0);
        Fences advance(){return new Fences(Math.max(0,request-1),Math.max(0,cooldown-1));}
    }
    public record Receipt(long input,long token,boolean paid,boolean moved,boolean left,String reason,Vec3 source,Vec3 destination){}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final HighPowerInput.Input input;
        final Vec3 source;
        final Vec3 initialMotion;
        final AABB initialBody;
        final net.minecraft.world.entity.Pose initialPose;
        final float initialYaw,initialPitch,initialFall;
        final boolean initialGrounded;
        final boolean left;
        final int preset;
        final long revision;
        SafeLandingProbeWorld.PhaseCandidate candidate;
        boolean closing,retired,paid,moved;
        Context(ServerPlayer p,HighPowerInput.Input i){
            identity=new LegacySingleKeyServerIdentity(p,i.input(),++nextToken,AbilityStorage.get(p));
            input=i;source=p.position();left=p.isShiftKeyDown();
            initialMotion=p.getDeltaMovement();initialBody=p.getBoundingBox();initialPose=p.getPose();
            initialYaw=p.getYRot();initialPitch=p.getXRot();initialFall=p.fallDistance;initialGrounded=p.onGround();
            preset=identity.state().presets.current();revision=identity.state().presets.revision();
        }
    }
    private PhaseSidestep(){}

    public static boolean start(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input)||AbilityConsumption.busy(p)||active(p)
                ||!finite(p.position())||!finite(p.getEyePosition())||!Float.isFinite(p.getYRot())||!Float.isFinite(p.getXRot()))return false;
        Watermark previous=INPUTS.get(p.getUUID());
        if(previous!=null&&previous.owner==p&&previous.epoch==input.epoch()&&input.input()<=previous.input)return false;
        if(!mapped(p,input.slot()))return false;
        INPUTS.put(p.getUUID(),new Watermark(p,input.epoch(),input.input()));
        if(nextToken==Long.MAX_VALUE||CONTEXTS.size()>=64)return false;
        long now=Integer.toUnsignedLong(p.server.getTickCount());Long replied=REPLIES.get(p);
        if(replied!=null&&now>=replied&&now-replied<4)return false;REPLIES.put(p,now);
        Fences fences=readFences(p);if(fences!=null&&fences.request>0)return false;
        Context c=new Context(p,input);CONTEXTS.put(p.getUUID(),c);
        try{
            if(fences==null)return refuse(c,"storage_invalid");
            writeFences(p,new Fences(PhaseSidestepRules.REQUEST_INTERVAL_TICKS,fences.cooldown));
            int shared=SafeLandingProbe.sharedMovementRemaining(p);
            if(shared<0)return refuse(c,"storage_invalid");
            if(shared>0||fences.cooldown>0||!c.identity.state().canUse(ID))return refuse(c,"cooldown");
            String reason=eligibility(c);if(!reason.isEmpty())return refuse(c,reason);
            if(!initialSourceUnchanged(c))return refuse(c,"event_changed_state");
            var assessment=SafeLandingProbeWorld.phase(p,c.left);
            if(!assessment.valid())return refuse(c,assessment.reason());
            c.candidate=assessment.candidate();
            if(!initialSourceUnchanged(c))return refuse(c,"event_changed_state");
            Vec3 before=p.position(),motion=p.getDeltaMovement(),destination=c.candidate.destination();
            float fall=p.fallDistance,yaw=p.getYRot(),pitch=p.getXRot();boolean grounded=p.onGround();var pose=p.getPose();
            EntityTeleportEvent event=new EntityTeleportEvent(p,destination.x,destination.y,destination.z);
            NeoForge.EVENT_BUS.post(event);
            if(event.isCanceled())return refuse(c,"event_cancelled");
            if(Double.compare(event.getTargetX(),destination.x)!=0||Double.compare(event.getTargetY(),destination.y)!=0
                    ||Double.compare(event.getTargetZ(),destination.z)!=0)return refuse(c,"event_redirected");
            if(!unchanged(c,before,motion,fall,yaw,pitch,grounded,pose))return refuse(c,"event_changed_state");
            reason=eligibility(c);if(!reason.isEmpty())return refuse(c,reason);
            var check=SafeLandingProbeWorld.revalidatePhase(p,c.candidate);
            if(!check.valid())return refuse(c,check.reason());
            if(!unchanged(c,before,motion,fall,yaw,pitch,grounded,pose))return refuse(c,"event_changed_state");
            fences=readFences(p);shared=SafeLandingProbe.sharedMovementRemaining(p);
            if(fences==null||shared<0)return refuse(c,"storage_invalid");
            if(shared>0||fences.cooldown>0)return refuse(c,"cooldown");
            var growth=HighPowerGrowth.values(p);int cooldown=PhaseSidestepRules.cooldownTicks(growth.position());
            // Closed shared-resource write has no external callback between these fences and native transport.
            if(!HighPowerResources.trySpend(p,ID,PhaseSidestepRules.cpCost(growth.position()),cooldown))return refuse(c,"insufficient_cp");
            c.paid=true;
            SafeLandingProbe.commitSharedMovement(p);
            writeFences(p,new Fences(fences.request,cooldown));
            // Absolute XYZ; native client resets velocity. Never synthesize a velocity/fall/ground repair.
            p.connection.teleport(destination.x,destination.y,destination.z,yaw,pitch,RelativeMovement.ROTATION);
            c.moved=p.serverLevel()==c.identity.level()&&p.position().equals(destination);
            if(!c.moved||!p.getDeltaMovement().equals(motion)||p.fallDistance!=fall||p.onGround()!=grounded)
                return refuse(c,"commit_unconfirmed");
            complete(c,destination);return true;
        }catch(RuntimeException failure){
            LOG.warn("NEW312 confirmation ended without retry/refund after native failure; paid={}",c.paid,failure);
            if(c.candidate!=null)c.moved|=p.serverLevel()==c.identity.level()&&p.position().equals(c.candidate.destination());
            return refuse(c,c.paid?"transport_unconfirmed":"validation_failed");
        }finally{CONTEXTS.remove(p.getUUID(),c);}
    }
    private static boolean unchanged(Context c,Vec3 position,Vec3 motion,float fall,float yaw,float pitch,boolean grounded,net.minecraft.world.entity.Pose pose){
        var p=c.identity.owner();return identity(c)&&!c.closing&&p.position().equals(position)&&p.getDeltaMovement().equals(motion)
                &&p.fallDistance==fall&&p.getYRot()==yaw&&p.getXRot()==pitch&&p.onGround()==grounded&&p.getPose()==pose&&p.isShiftKeyDown()==c.left
                &&p.getBoundingBox().equals(c.initialBody);
    }
    private static boolean initialSourceUnchanged(Context c){return unchanged(c,c.source,c.initialMotion,c.initialFall,c.initialYaw,c.initialPitch,c.initialGrounded,c.initialPose);}
    private static void complete(Context c,Vec3 destination){
        var p=c.identity.owner();CONTEXTS.remove(p.getUUID(),c);
        if(retainReceipt(c))RECEIPTS.put(p,new Receipt(c.input.input(),c.identity.token(),true,true,c.left,"",c.source,destination));
        try{if(retainReceipt(c))emit(c,SUCCESS,"");}catch(RuntimeException failure){LOG.warn("NEW312 moved successfully but visual delivery failed",failure);}
        // Movement is not proof that an incoming attack was avoided; deliberately no mastery award.
        try{if(retainReceipt(c)){AbilityStorage.save(p);AcademyNetwork.sync(p);}}catch(RuntimeException failure){LOG.warn("NEW312 moved successfully; final sync failed without rollback",failure);}
        diagnostic(c,"success","");
    }
    private static boolean refuse(Context c,String reason){
        var p=c.identity.owner();CONTEXTS.remove(p.getUUID(),c);c.closing=true;
        Vec3 destination=c.candidate==null?c.source:c.candidate.destination();
        if(retainReceipt(c))RECEIPTS.put(p,new Receipt(c.input.input(),c.identity.token(),c.paid,c.moved,c.left,reason,c.source,destination));
        try{if(retainReceipt(c))emit(c,REFUSED,reason);}catch(RuntimeException failure){LOG.warn("NEW312 refusal visual could not be delivered",failure);}
        diagnostic(c,"refused",reason);return false;
    }
    /** Native key release does not repeat, reverse or undo an immediate move. */
    public static boolean release(ServerPlayer p,String wire){return HighPowerBracerSession.authenticated(p,HighPowerInput.parse(wire));}
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        var c=owned(p);if(c==null||input.epoch()!=c.input.epoch()||input.slot()!=c.input.slot()||input.input()<c.input.input())return false;
        INPUTS.put(p.getUUID(),new Watermark(p,input.epoch(),input.input()));c.closing=true;return true;
    }
    public static boolean active(ServerPlayer p){return owned(p)!=null;}
    /** Administrative/control cancellation preserves the input watermark and all resource fences. */
    public static void cancelActive(ServerPlayer p){var c=owned(p);if(c!=null)c.closing=true;}
    public static Receipt receipt(ServerPlayer p){return p==null?null:RECEIPTS.get(p);}
    private static Context owned(ServerPlayer p){if(!thread(p))return null;var c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean mapped(ServerPlayer p,int slot){return thread(p)&&HighPowerCategories.isTeleporter(AbilityStorage.get(p).category)
            &&slot>=0&&slot<4&&ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));}
    private static boolean identity(Context c){var p=c.identity.owner();return CONTEXTS.get(p.getUUID())==c&&c.identity.owns(p)&&mapped(p,c.input.slot())
            &&c.identity.state().presets.current()==c.preset&&c.identity.state().presets.revision()==c.revision;}
    private static String eligibility(Context c){
        var p=c.identity.owner();var s=c.identity.state();
        if(!identity(c)||!p.isAlive()||p.isRemoved()||p.hasDisconnected()||p.isSpectator()||p.getAbilities().instabuild||p.getAbilities().invulnerable
                ||p.containerMenu!=p.inventoryMenu||!s.activated||!s.overloadFine||s.interfering||s.consumptionInProgress()
                ||!s.learned(ID)||s.level<1||s.level>5||!SkillCatalog.enabled(HighPowerCategories.TELEPORTER,ID)
                ||!HighPowerResources.ready(p)||HighPowerCombat.unsupportedReason(p).isPresent())return "context_changed";
        if(!Float.isFinite(p.fallDistance)||p.fallDistance!=0)return "source_airborne";
        if(p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)||p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING))return "movement_conflict";
        if(CornerExchange.active(p)||CrossSubstitution.isTransporting(p)||ReturnMark.active(p)||SafeLandingProbe.active(p)||(BeamStep.active(p)||WindPressureLift.active(p))||MagneticGroundStep.active(p)||PolarityTether.active(p)
                ||cn.academy.port.skill.MagMovement.active(p)||cn.academy.port.skill.VecAccel.active(p)
                ||cn.academy.port.skill.JetEngine.active(p)||cn.academy.port.skill.StormWing.active(p)
                ||cn.academy.port.skill.MarkTeleport.active(p)||cn.academy.port.skill.PenetrateTeleport.active(p)
                ||cn.academy.port.skill.LocationTeleport.active(p)||cn.academy.port.skill.Flashing.active(p))return "movement_conflict";
        return SafeLandingProbeWorld.sourceRefusal(p);
    }
    private static Fences readFences(ServerPlayer p){
        var root=p.getPersistentData();if(!root.contains(DATA))return Fences.ZERO;
        if(!root.contains(DATA,Tag.TAG_COMPOUND))return null;var tag=root.getCompound(DATA);
        if(!tag.contains("version",Tag.TAG_INT)||tag.getInt("version")!=1||!tag.contains("request",Tag.TAG_INT)||!tag.contains("cooldown",Tag.TAG_INT))return null;
        int request=tag.getInt("request"),cooldown=tag.getInt("cooldown");
        return request<0||request>4||cooldown<0||cooldown>40?null:new Fences(request,cooldown);
    }
    private static void writeFences(ServerPlayer p,Fences f){
        var tag=p.getPersistentData().contains(DATA,Tag.TAG_COMPOUND)?p.getPersistentData().getCompound(DATA).copy():new CompoundTag();
        tag.putInt("version",1);tag.putInt("request",f.request);tag.putInt("cooldown",f.cooldown);p.getPersistentData().put(DATA,tag);
    }
    private static boolean thread(ServerPlayer p){return p!=null&&p.server.isSameThread();}
    private static boolean retainReceipt(Context c){var p=c.identity.owner();return !c.retired&&!p.isRemoved()&&!p.hasDisconnected()
            &&p.server.getPlayerList().getPlayer(p.getUUID())==p;}
    public static void remove(ServerPlayer p){
        if(!thread(p))return;var c=owned(p);if(c!=null){c.closing=true;c.retired=true;}
        INPUTS.computeIfPresent(p.getUUID(),(id,w)->w.owner==p?null:w);RECEIPTS.remove(p);REPLIES.remove(p);
    }
    @SubscribeEvent public static void physical(ServerTickEvent.Pre e){
        Integer previous=CLOCKS.put(e.getServer(),e.getServer().getTickCount());if(previous!=null&&previous==e.getServer().getTickCount())return;
        for(var p:e.getServer().getPlayerList().getPlayers())if(p.getPersistentData().contains(DATA)){var f=readFences(p);if(f!=null&&!f.equals(Fences.ZERO))writeFences(p,f.advance());}
    }
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p){
        // A live player changing worlds may retain the terminal refusal, unlike a retired incarnation.
        cancelActive(p);INPUTS.computeIfPresent(p.getUUID(),(id,w)->w.owner==p?null:w);RECEIPTS.remove(p);REPLIES.remove(p);
    }}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer p){remove(old);if(old.getPersistentData().contains(DATA))p.getPersistentData().put(DATA,old.getPersistentData().get(DATA).copy());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){CONTEXTS.entrySet().removeIf(x->{var c=x.getValue();if(c.identity.owner().server!=e.getServer())return false;c.closing=true;c.retired=true;return true;});INPUTS.entrySet().removeIf(x->x.getValue().owner.server==e.getServer());RECEIPTS.keySet().removeIf(p->p.server==e.getServer());REPLIES.keySet().removeIf(p->p.server==e.getServer());CLOCKS.remove(e.getServer());}
    private static void emit(Context c,int phase,String reason){
        var p=c.identity.owner();Vec3 destination=c.candidate==null?c.source:c.candidate.destination();
        AABB bounds=c.candidate==null?null:c.candidate.relativeBody().move(destination);
        var packet=new Visual(p.getId(),p.getUUID(),c.input.input(),c.input.epoch(),c.identity.token(),1,phase,c.source,destination,bounds,phase==SUCCESS,reason,c.left);
        if(p.connection!=null&&!p.hasDisconnected()&&p.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(p,packet);
        if(phase==SUCCESS){int count=0;for(var observer:c.identity.level().players())if(observer!=p&&observer.connection!=null&&!observer.hasDisconnected()
                &&Math.min(observer.position().distanceToSqr(c.source),observer.position().distanceToSqr(destination))<=32*32){PacketDistributor.sendToPlayer(observer,packet);if(++count>=32)break;}}
    }
    private static void diagnostic(Context c,String stage,String reason){if(DIAGNOSTIC&&diagnosticEvents++<256)LOG.info("NEW312 stage={} token={} input={} paid={} moved={} left={} source={} destination={} reason={}",stage,c.identity.token(),c.input.input(),c.paid,c.moved,c.left,c.source,c.candidate==null?c.source:c.candidate.destination(),reason);}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<=30000032&&Math.abs(p.z)<=30000032&&Math.abs(p.y)<=4096;}
    public record Visual(int entity,UUID owner,long input,long epoch,long token,long sequence,int phase,Vec3 source,Vec3 destination,AABB bounds,boolean moved,String reason,boolean left) implements CustomPacketPayload {
        public Visual {
            if(entity<0||owner==null||input<=0||epoch<=0||token<=0||sequence<=0||(phase!=SUCCESS&&phase!=REFUSED)||!finite(source)||!finite(destination)
                    ||source.distanceToSqr(destination)>(cn.academy.port.highpower.core.PhaseSidestepRules.MAX_DISTANCE+1e-6)*(cn.academy.port.highpower.core.PhaseSidestepRules.MAX_DISTANCE+1e-6)||reason==null||reason.length()>64||!reason.matches("[a-z_]*")
                    ||moved!=(phase==SUCCESS)||moved&&(bounds==null||!reason.isEmpty()||source.y!=destination.y||source.distanceToSqr(destination)<2.999999*2.999999)
                    ||bounds!=null&&(!finite(new Vec3(bounds.minX,bounds.minY,bounds.minZ))||!finite(new Vec3(bounds.maxX,bounds.maxY,bounds.maxZ))
                    ||bounds.getXsize()<=0||bounds.getYsize()<=0||bounds.getZsize()<=0||bounds.getXsize()>8||bounds.getYsize()>4||bounds.getZsize()>8))throw new IllegalArgumentException("Invalid NEW312 visual");
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_phase_sidestep"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.entity);b.writeUUID(v.owner);b.writeLong(v.input);b.writeLong(v.epoch);b.writeLong(v.token);b.writeLong(v.sequence);b.writeByte(v.phase);
            writeVec(b,v.source);writeVec(b,v.destination);b.writeBoolean(v.bounds!=null);if(v.bounds!=null){writeVec(b,new Vec3(v.bounds.minX,v.bounds.minY,v.bounds.minZ));writeVec(b,new Vec3(v.bounds.maxX,v.bounds.maxY,v.bounds.maxZ));}
            b.writeBoolean(v.moved);b.writeUtf(v.reason,64);b.writeBoolean(v.left);
        },b->{int entity=b.readInt();UUID owner=b.readUUID();long input=b.readLong(),epoch=b.readLong(),token=b.readLong(),sequence=b.readLong();int phase=b.readUnsignedByte();Vec3 source=readVec(b),destination=readVec(b);AABB bounds=b.readBoolean()?new AABB(readVec(b),readVec(b)):null;return new Visual(entity,owner,input,epoch,token,sequence,phase,source,destination,bounds,b.readBoolean(),b.readUtf(64),b.readBoolean());});
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}}
}
