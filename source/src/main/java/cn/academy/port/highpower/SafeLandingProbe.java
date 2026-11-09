package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.SafeLandingProbeRules;
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

/** NEW313: one frozen landing, explicit second press, and a standard authoritative teleport. */
@EventBusSubscriber(modid="academy")
public final class SafeLandingProbe {
    public static final String ID="safe_landing_probe";
    public static final int PREVIEW=0, SUCCESS=1, ENDED=2, REFUSED=3;
    private static final String DATA="academy:safe_landing_probe_fences";
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Map<ServerPlayer,Receipt> RECEIPTS=new IdentityHashMap<>();
    private static final Map<ServerPlayer,Long> REPLIES=new IdentityHashMap<>();
    private static final Map<MinecraftServer,Integer> CLOCKS=new WeakHashMap<>();
    // NEW342 can commit before this class's normal Pre subscriber. Only that owner
    // skips the already-issued movement decrement; no other clock is reordered.
    private static final Map<ServerPlayer,Integer> PRE_MOVEMENT_ISSUED=new IdentityHashMap<>();
    private static final int MAX_CONTEXTS=64;
    private static long nextToken;
    private static final boolean DIAGNOSTIC=Boolean.getBoolean("academy.highpower.safeLandingDiagnostic");
    private static int diagnosticEvents;
    private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("academy.safe_landing_probe");
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(ServerPlayer owner,long epoch,long input) {}
    private record Fences(int preview,int movement,int cooldown,int mastery) {
        static final Fences ZERO=new Fences(0,0,0,0);
        Fences advance(){return new Fences(Math.max(0,preview-1),Math.max(0,movement-1),Math.max(0,cooldown-1),Math.max(0,mastery-1));}
    }
    public record Receipt(long input,long token,boolean paid,boolean moved,String reason,Vec3 source,Vec3 destination) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final long started;
        final int preset;
        final long revision;
        final Vec3 source;
        HighPowerInput.Input command;
        SafeLandingProbeWorld.Candidate candidate;
        long sequence,lastTick=-1,lastVisual=-1;
        boolean processing,closing,paid,moved;
        Context(ServerPlayer p,HighPowerInput.Input input){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));
            command=input;started=now(p);source=p.position();preset=identity.state().presets.current();revision=identity.state().presets.revision();
        }
    }
    private SafeLandingProbe() {}

    public static boolean press(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input)||AbilityConsumption.busy(p))return false;
        // Invalid native state cannot become a coordinate-bearing refusal payload.
        if(!finite(p.position())||!finite(p.getEyePosition())
                ||!Float.isFinite(p.getYRot())||!Float.isFinite(p.getXRot()))return false;
        Context current=CONTEXTS.get(p.getUUID());if(current!=null&&current.processing)return false;
        Watermark prior=INPUTS.get(p.getUUID());
        if(prior!=null&&prior.owner==p&&prior.epoch==input.epoch()&&input.input()<=prior.input)return false;
        if(!mapped(p,input.slot()))return false;
        INPUTS.put(p.getUUID(),new Watermark(p,input.epoch(),input.input()));
        if(current!=null){
            if(!current.identity.owns(p)||current.command.epoch()!=input.epoch()||current.command.slot()!=input.slot()){
                finish(current,ENDED,"context_changed");return false;
            }
            current.command=input;return confirm(current);
        }
        if(nextToken==Long.MAX_VALUE||CONTEXTS.size()>=MAX_CONTEXTS)return false;
        Long replied=REPLIES.get(p);if(replied!=null&&now(p)>=replied&&now(p)-replied<4)return false;REPLIES.put(p,now(p));
        Context c=new Context(p,input);Fences fences=readFences(p);
        if(fences==null)return refuse(c,"storage_invalid");
        if(fences.preview>0)return false;
        writeFences(p,new Fences(SafeLandingProbeRules.PREVIEW_INTERVAL_TICKS,fences.movement,fences.cooldown,fences.mastery));
        if(fences.movement>0||fences.cooldown>0||!AbilityStorage.get(p).canUse(ID))return refuse(c,"cooldown");
        String reason=eligibility(c);if(!reason.isEmpty())return refuse(c,reason);
        CONTEXTS.put(p.getUUID(),c);c.processing=true;
        try{
            var assessment=SafeLandingProbeWorld.preview(p);
            if(!assessment.valid())return refuse(c,assessment.reason());
            if(!identity(c)||c.closing)return refuse(c,"context_changed");
            c.candidate=assessment.candidate();emit(c,PREVIEW,true,"",c.source);return true;
        }catch(RuntimeException failure){LOG.warn("NEW313 preview refused after native validation failure",failure);return refuse(c,"validation_failed");}
        finally{c.processing=false;if(c.closing&&CONTEXTS.get(p.getUUID())==c)finish(c,ENDED,"context_changed");}
    }
    /** Key-up does not confirm or cancel the frozen preview. */
    public static boolean release(ServerPlayer p,String wire){var i=HighPowerInput.parse(wire);Context c=owned(p);return c!=null&&i!=null&&i.epoch()==c.command.epoch()&&i.input()<=c.command.input();}

    private static boolean confirm(Context c){
        ServerPlayer p=c.identity.owner();c.processing=true;
        try{
            String reason=eligibility(c);if(!reason.isEmpty())return refuse(c,reason);
            if(!SafeLandingProbeRules.fresh(c.started,now(p)))return refuse(c,"expired");
            if(c.candidate==null||p.position().distanceToSqr(c.source)>.25*.25)return refuse(c,"source_moved");
            var check=SafeLandingProbeWorld.revalidate(p,c.candidate);if(!check.valid())return refuse(c,check.reason());
            Vec3 before=p.position(),motion=p.getDeltaMovement();float fall=p.fallDistance,yaw=p.getYRot(),pitch=p.getXRot();boolean grounded=p.onGround();
            var pose=p.getPose();Vec3 destination=c.candidate.destination();
            EntityTeleportEvent event=new EntityTeleportEvent(p,destination.x,destination.y,destination.z);
            NeoForge.EVENT_BUS.post(event);
            if(event.isCanceled())return refuse(c,"event_cancelled");
            if(Double.compare(event.getTargetX(),destination.x)!=0||Double.compare(event.getTargetY(),destination.y)!=0||Double.compare(event.getTargetZ(),destination.z)!=0)
                return refuse(c,"event_redirected");
            if(!identity(c)||c.closing||!p.position().equals(before)||!p.getDeltaMovement().equals(motion)||p.fallDistance!=fall
                    ||p.onGround()!=grounded||p.getPose()!=pose||p.getYRot()!=yaw||p.getXRot()!=pitch)return refuse(c,"event_changed_state");
            reason=eligibility(c);if(!reason.isEmpty())return refuse(c,reason);
            check=SafeLandingProbeWorld.revalidate(p,c.candidate);if(!check.valid())return refuse(c,check.reason());
            // Native shape/permission reads are followed by a final owner/source fence too.
            if(!identity(c)||c.closing||!p.position().equals(before)||!p.getDeltaMovement().equals(motion)||p.fallDistance!=fall
                    ||p.onGround()!=grounded||p.getPose()!=pose||p.getYRot()!=yaw||p.getXRot()!=pitch)return refuse(c,"event_changed_state");
            Fences fences=readFences(p);if(fences==null)return refuse(c,"storage_invalid");
            if(fences.movement>0||fences.cooldown>0)return refuse(c,"cooldown");
            var growth=HighPowerGrowth.values(p);int cooldown=SafeLandingProbeRules.cooldownTicks(growth.position());
            // No external callbacks occur between the final checks, shared CP debit and native call.
            if(!HighPowerResources.trySpend(p,ID,SafeLandingProbeRules.cpCost(growth.position()),cooldown))return refuse(c,"insufficient_cp");
            c.paid=true;
            writeFences(p,new Fences(fences.preview,SafeLandingProbeRules.MOVEMENT_FENCE_TICKS,cooldown,fences.mastery));
            // Arguments are ABSOLUTE. Native code computes only the rotation-relative packet fields.
            // Absolute XYZ guarantees the validated destination even if the client has drifted.
            // Vanilla clears client XYZ velocity; server velocity/fall/ground are left untouched.
            p.connection.teleport(destination.x,destination.y,destination.z,yaw,pitch,RelativeMovement.ROTATION);
            c.moved=p.serverLevel()==c.identity.level()&&p.position().equals(destination);
            if(!c.moved||!p.getDeltaMovement().equals(motion)||p.fallDistance!=fall||p.onGround()!=grounded)
                return refuse(c,"commit_unconfirmed");
            completeSuccess(c,before,destination);return true;
        }catch(RuntimeException failure){
            LOG.warn("NEW313 confirmation ended without retry/refund after native failure; paid={}",c.paid,failure);
            if(c.candidate!=null)c.moved|=p.serverLevel()==c.identity.level()&&p.position().equals(c.candidate.destination());
            return refuse(c,c.paid?"transport_unconfirmed":"validation_failed");
        }finally{c.processing=false;if(CONTEXTS.get(p.getUUID())==c)finish(c,ENDED,"context_changed");}
    }
    /** Once native movement is confirmed, later presentation/XP callbacks cannot undo history.
     * No bookkeeping failure retries the move, charge or possibly-applied XP award. */
    private static void completeSuccess(Context c,Vec3 before,Vec3 destination){
        ServerPlayer p=c.identity.owner();CONTEXTS.remove(p.getUUID(),c);
        RECEIPTS.put(p,new Receipt(c.command.input(),c.identity.token(),true,true,"",before,destination));
        try{emit(c,SUCCESS,true,"",before);}catch(RuntimeException failure){LOG.warn("NEW313 moved successfully but visual delivery failed",failure);}
        try{
            Fences after=readFences(p);
            if(after!=null&&after.mastery==0&&before.distanceTo(destination)>=SafeLandingProbeRules.MIN_MASTERY_DISTANCE&&c.identity.owns(p)){
                writeFences(p,new Fences(after.preview,after.movement,after.cooldown,SafeLandingProbeRules.MASTERY_INTERVAL_TICKS));
                c.identity.state().addExperience(ID,.002);
            }
        }catch(RuntimeException failure){LOG.warn("NEW313 moved successfully; mastery callback failed and is not retried",failure);}
        try{AbilityStorage.save(p);AcademyNetwork.sync(p);}catch(RuntimeException failure){LOG.warn("NEW313 moved successfully; final sync failed without rollback",failure);}
        diagnostic(c,"success",before,destination,"");
    }
    public static boolean abort(ServerPlayer p,String wire){
        var input=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context c=owned(p);if(c==null||input.epoch()!=c.command.epoch()||input.slot()!=c.command.slot()||input.input()<c.command.input())return false;
        INPUTS.put(p.getUUID(),new Watermark(p,input.epoch(),input.input()));c.command=input;c.closing=true;
        if(!c.processing)finish(c,ENDED,"context_changed");return true;
    }
    public static void remove(ServerPlayer p){if(!thread(p))return;Context c=CONTEXTS.get(p.getUUID());if(c!=null&&c.identity.owner()==p){c.closing=true;if(!c.processing)finish(c,ENDED,"context_changed");}INPUTS.computeIfPresent(p.getUUID(),(id,w)->w.owner==p?null:w);RECEIPTS.remove(p);REPLIES.remove(p);PRE_MOVEMENT_ISSUED.remove(p);}
    public static boolean active(ServerPlayer p){return owned(p)!=null;}
    public static Receipt receipt(ServerPlayer p){return p==null?null:RECEIPTS.get(p);}
    private static boolean refuse(Context c,String reason){finish(c,REFUSED,reason);return false;}
    private static void finish(Context c,int phase,String reason){
        ServerPlayer p=c.identity.owner();CONTEXTS.remove(p.getUUID(),c);c.closing=true;
        Vec3 to=c.candidate==null?c.source:c.candidate.destination();
        RECEIPTS.put(p,new Receipt(c.command.input(),c.identity.token(),c.paid,c.moved,reason,c.source,to));
        emit(c,phase,false,reason,c.source);diagnostic(c,"end",c.source,to,reason);
    }
    private static boolean mapped(ServerPlayer p,int slot){return thread(p)&&HighPowerCategories.isTeleporter(AbilityStorage.get(p).category)
            &&slot>=0&&slot<4&&ID.equals(AbilityStorage.get(p).presets.currentSkill(slot));}
    private static boolean identity(Context c){ServerPlayer p=c.identity.owner();return c.identity.owns(p)&&mapped(p,c.command.slot())
            &&c.identity.state().presets.current()==c.preset&&c.identity.state().presets.revision()==c.revision;}
    private static String eligibility(Context c){
        ServerPlayer p=c.identity.owner();var s=c.identity.state();
        if(!identity(c)||!p.isAlive()||p.isRemoved()||p.hasDisconnected()||p.isSpectator()||p.getAbilities().instabuild||p.getAbilities().invulnerable
                ||p.containerMenu!=p.inventoryMenu||!s.activated||!s.overloadFine||s.interfering||s.consumptionInProgress()
                ||!s.learned(ID)||s.level<1||s.level>5||!SkillCatalog.enabled(HighPowerCategories.TELEPORTER,ID)
                ||!HighPowerResources.ready(p)||HighPowerCombat.unsupportedReason(p).isPresent())return "context_changed";
        if(!Float.isFinite(p.fallDistance)||p.fallDistance!=0)return "source_airborne";
        if(p.hasEffect(net.minecraft.world.effect.MobEffects.LEVITATION)||p.hasEffect(net.minecraft.world.effect.MobEffects.SLOW_FALLING))return "movement_conflict";
        if(SpatialPincerConsent.committing(p)||CornerExchange.active(p)||CrossSubstitution.isTransporting(p)||ReturnMark.active(p)||PhaseSidestep.active(p)||(BeamStep.active(p)||WindPressureLift.active(p))||MagneticGroundStep.active(p)||PolarityTether.active(p)
                ||cn.academy.port.skill.MagMovement.active(p)||cn.academy.port.skill.VecAccel.active(p)
                ||cn.academy.port.skill.JetEngine.active(p)||cn.academy.port.skill.StormWing.active(p)
                ||cn.academy.port.skill.MarkTeleport.active(p)||cn.academy.port.skill.PenetrateTeleport.active(p)
                ||cn.academy.port.skill.LocationTeleport.active(p)||cn.academy.port.skill.Flashing.active(p))return "movement_conflict";
        return SafeLandingProbeWorld.sourceRefusal(p);
    }
    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.processing)return;long tick=now(p);if(c.lastTick==tick)return;c.lastTick=tick;
        if(c.closing){finish(c,ENDED,"context_changed");return;}
        String reason=eligibility(c);if(!reason.isEmpty()){finish(c,ENDED,reason);return;}
        if(!SafeLandingProbeRules.fresh(c.started,tick)){finish(c,ENDED,"expired");return;}
        if(p.position().distanceToSqr(c.source)>.25*.25){finish(c,ENDED,"source_moved");return;}
        if(c.lastVisual>=0&&tick-c.lastVisual<4)return;
        c.processing=true;
        try{var a=SafeLandingProbeWorld.revalidate(p,c.candidate);if(!a.valid())finish(c,REFUSED,a.reason());else if(!c.closing&&identity(c))emit(c,PREVIEW,true,"",c.source);}
        catch(RuntimeException failure){finish(c,REFUSED,"validation_failed");}
        finally{c.processing=false;if(c.closing&&CONTEXTS.get(p.getUUID())==c)finish(c,ENDED,"context_changed");}
    }
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.server.isSameThread();}
    private static long now(ServerPlayer p){return Integer.toUnsignedLong(p.server.getTickCount());}
    private static Fences readFences(ServerPlayer p){
        var root=p.getPersistentData();if(!root.contains(DATA))return Fences.ZERO;
        if(!root.contains(DATA,Tag.TAG_COMPOUND))return null;CompoundTag tag=root.getCompound(DATA);
        if(!tag.contains("version",Tag.TAG_INT)||tag.getInt("version")!=1)return null;
        String[] keys={"preview","movement","cooldown","mastery"};int[] v=new int[4];
        for(int i=0;i<4;i++){if(!tag.contains(keys[i],Tag.TAG_INT))return null;v[i]=tag.getInt(keys[i]);if(v[i]<0||v[i]>12000)return null;}
        if(v[0]>4||v[1]>8||v[2]>40||v[3]>20)return null;return new Fences(v[0],v[1],v[2],v[3]);
    }
    /** The historical313 movement field is now the one shared312/313 physical fence.
     * Keeping its existing storage and sole ticker avoids migration resets or double advancement. */
    static int sharedMovementRemaining(ServerPlayer p){
        if(!thread(p))return -1;Fences f=readFences(p);return f==null?-1:f.movement;
    }
    /** Closed callback-free312 commit; all unrelated313 countdowns are preserved. */
    static void commitSharedMovement(ServerPlayer p){
        if(!thread(p))throw new IllegalStateException("Server thread required");
        Fences f=readFences(p);if(f==null||f.movement!=0)throw new IllegalStateException("Shared TP fence changed");
        writeFences(p,new Fences(f.preview,SafeLandingProbeRules.MOVEMENT_FENCE_TICKS,f.cooldown,f.mastery));
    }
    /** NEW342-only commit before this tick's ordinary movement countdown has advanced. */
    static void commitSharedMovementFromPre(ServerPlayer p){
        if(!thread(p)||!EmergencyReturn.isTransporting(p))throw new IllegalStateException("No authenticated emergency transport");
        commitSharedMovement(p);
        Integer advanced=CLOCKS.get(p.server);
        if(advanced==null||advanced!=p.server.getTickCount())PRE_MOVEMENT_ISSUED.put(p,p.server.getTickCount());
    }
    private static void writeFences(ServerPlayer p,Fences f){
        CompoundTag t=p.getPersistentData().contains(DATA,Tag.TAG_COMPOUND)?p.getPersistentData().getCompound(DATA).copy():new CompoundTag();
        t.putInt("version",1);t.putInt("preview",f.preview);t.putInt("movement",f.movement);t.putInt("cooldown",f.cooldown);t.putInt("mastery",f.mastery);p.getPersistentData().put(DATA,t);
    }
    @SubscribeEvent public static void physical(ServerTickEvent.Pre e){
        Integer previous=CLOCKS.put(e.getServer(),e.getServer().getTickCount());if(previous!=null&&previous==e.getServer().getTickCount())return;
        for(ServerPlayer p:e.getServer().getPlayerList().getPlayers())if(p.getPersistentData().contains(DATA)){
            Fences f=readFences(p);Integer issued=PRE_MOVEMENT_ISSUED.remove(p);
            if(f!=null&&!f.equals(Fences.ZERO)){
                Fences next=f.advance();
                if(issued!=null&&issued==e.getServer().getTickCount())next=new Fences(next.preview,f.movement,next.cooldown,next.mastery);
                writeFences(p,next);
            }
        }
        PRE_MOVEMENT_ISSUED.keySet().removeIf(p->p.server==e.getServer());
    }
    @SubscribeEvent public static void controllers(ServerTickEvent.Post e){for(Context c:new ArrayList<>(CONTEXTS.values()))if(c.identity.owner().server==e.getServer())tick(c.identity.owner());}
    @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void death(LivingDeathEvent e){if(e.getEntity() instanceof ServerPlayer p)remove(p);}
    @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal() instanceof ServerPlayer old&&e.getEntity() instanceof ServerPlayer p){remove(old);if(old.getPersistentData().contains(DATA))p.getPersistentData().put(DATA,old.getPersistentData().get(DATA).copy());}}
    @SubscribeEvent public static void stopped(ServerStoppedEvent e){CONTEXTS.entrySet().removeIf(x->x.getValue().identity.owner().server==e.getServer());INPUTS.entrySet().removeIf(x->x.getValue().owner.server==e.getServer());RECEIPTS.keySet().removeIf(p->p.server==e.getServer());REPLIES.keySet().removeIf(p->p.server==e.getServer());CLOCKS.remove(e.getServer());PRE_MOVEMENT_ISSUED.keySet().removeIf(p->p.server==e.getServer());}

    private static void emit(Context c,int phase,boolean valid,String reason,Vec3 source){
        if(c.sequence==Long.MAX_VALUE)return;ServerPlayer p=c.identity.owner();Vec3 destination=c.candidate==null?c.source:c.candidate.destination();
        AABB bounds=c.candidate==null?null:c.candidate.relativeBody().move(destination);
        int remaining=phase==PREVIEW?(int)Math.max(0,SafeLandingProbeRules.PREVIEW_TICKS-(now(p)-c.started)):0;
        var v=new Visual(p.getId(),p.getUUID(),c.command.input(),c.command.epoch(),c.identity.token(),++c.sequence,phase,source,destination,bounds,valid,reason,remaining);
        c.lastVisual=now(p);
        if(p.connection!=null&&!p.hasDisconnected()&&p.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(p,v);
        if(phase==SUCCESS){int sent=0;for(ServerPlayer observer:c.identity.level().players())if(observer!=p&&observer.connection!=null&&!observer.hasDisconnected()
                &&Math.min(observer.position().distanceToSqr(source),observer.position().distanceToSqr(destination))<=32*32){PacketDistributor.sendToPlayer(observer,v);if(++sent>=32)break;}}
    }
    private static void diagnostic(Context c,String stage,Vec3 from,Vec3 to,String reason){if(DIAGNOSTIC&&diagnosticEvents++<256)LOG.info("NEW313 stage={} token={} input={} paid={} moved={} source={} destination={} reason={}",stage,c.identity.token(),c.command.input(),c.paid,c.moved,from,to,reason);}
    private static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z)&&Math.abs(p.x)<=30000032&&Math.abs(p.z)<=30000032&&Math.abs(p.y)<=4096;}
    public record Visual(int entity,UUID owner,long input,long epoch,long token,long sequence,int phase,Vec3 source,Vec3 destination,AABB bounds,boolean valid,String reason,int remainingTicks) implements CustomPacketPayload {
        public Visual {
            if(entity<0||owner==null||input<=0||epoch<=0||token<=0||sequence<=0||phase<PREVIEW||phase>REFUSED||!finite(source)||!finite(destination)
                    ||source.distanceToSqr(destination)>12.251*12.251||reason==null||reason.length()>64||!reason.matches("[a-z_]*")
                    ||remainingTicks<0||remainingTicks> SafeLandingProbeRules.PREVIEW_TICKS||phase!=PREVIEW&&remainingTicks!=0||valid!=(phase==PREVIEW||phase==SUCCESS)
                    ||valid&&bounds==null||bounds!=null&&(!finite(new Vec3(bounds.minX,bounds.minY,bounds.minZ))||!finite(new Vec3(bounds.maxX,bounds.maxY,bounds.maxZ))
                    ||bounds.getXsize()<=0||bounds.getYsize()<=0||bounds.getZsize()<=0||bounds.getXsize()>4||bounds.getYsize()>4||bounds.getZsize()>4))throw new IllegalArgumentException("Invalid NEW313 visual");
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","hp_safe_landing_probe"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.entity);b.writeUUID(v.owner);b.writeLong(v.input);b.writeLong(v.epoch);b.writeLong(v.token);b.writeLong(v.sequence);b.writeByte(v.phase);
            writeVec(b,v.source);writeVec(b,v.destination);b.writeBoolean(v.bounds!=null);if(v.bounds!=null){writeVec(b,new Vec3(v.bounds.minX,v.bounds.minY,v.bounds.minZ));writeVec(b,new Vec3(v.bounds.maxX,v.bounds.maxY,v.bounds.maxZ));}
            b.writeBoolean(v.valid);b.writeUtf(v.reason,64);b.writeByte(v.remainingTicks);
        },b->{int entity=b.readInt();UUID owner=b.readUUID();long input=b.readLong(),epoch=b.readLong(),token=b.readLong(),sequence=b.readLong();int phase=b.readUnsignedByte();Vec3 source=readVec(b),destination=readVec(b);AABB bounds=b.readBoolean()?new AABB(readVec(b),readVec(b)):null;return new Visual(entity,owner,input,epoch,token,sequence,phase,source,destination,bounds,b.readBoolean(),b.readUtf(64),b.readUnsignedByte());});
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->clientReceiver.accept(packet));}}
}
