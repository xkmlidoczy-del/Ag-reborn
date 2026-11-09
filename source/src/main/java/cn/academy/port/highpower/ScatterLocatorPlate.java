package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Plate;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ScatterLocatorRules;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW223 owns preparation only. All targeting, collision, permission and paid damage stay in NEW221. */
@EventBusSubscriber(modid="academy")
public final class ScatterLocatorPlate {
    public static final String ID="scatter_locator_plate";
    public static final int MIN_LEVEL=2,MAX_LEVEL=5;
    public static final int ACTIVE=0,EDIT=1,PREVIEW=2,ENDED=3;
    public static final int PREPARED=0,MISS=1,BLOCKED=2,REFUSED=3,DEGENERATE=6;
    private static final String DATA="academy:scatter_locator_plate",INPUT_AT="inputAt",PREVIEW_AT="previewAt";
    private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
    private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
    private static final Set<ServerPlayer> PROCESSING=Collections.newSetFromMap(new IdentityHashMap<>());
    private static long nextToken;
    public static Consumer<Visual> clientReceiver=ignored->{};
    private record Watermark(long epoch,long input) {}
    private record Hold(HighPowerInput.Input input,long started,boolean editing,Vec3 center) {}
    private static final class Context {
        final LegacySingleKeyServerIdentity identity;
        final int preset;final long revision;final BigDecimal position,earnedPosition;
        final Set<ServerPlayer> audience=new LinkedHashSet<>();
        HighPowerInput.Input input;Hold hold;Plate plate;HighPowerResources.ControlHold fee;
        long placedAt=-1,lastTick=-1,lastVisual=-1,sequence;
        boolean resolving,preview;
        Context(ServerPlayer p,HighPowerInput.Input input){
            identity=new LegacySingleKeyServerIdentity(p,input.input(),++nextToken,AbilityStorage.get(p));
            this.input=input;preset=identity.state().presets.current();revision=identity.state().presets.revision();
            var growth=HighPowerGrowth.values(p);position=growth.position();earnedPosition=growth.earnedPosition();
        }
    }
    private ScatterLocatorPlate() {}

    public static boolean start(ServerPlayer p,String wire){
        if(PROCESSING.contains(p)||FoldedMirrorArray.processing(p))return false;
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context existing=owned(p);if(existing!=null&&existing.resolving)return false;
        Watermark prior=INPUTS.get(p.getUUID());
        if(prior!=null&&prior.epoch==input.epoch()&&input.input()<=prior.input)return false;
        if(existing!=null&&!valid(existing)){finish(existing);existing=null;}
        if(existing!=null&&existing.hold!=null)return false;
        INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        if(!canAct(p)||!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot()))||!inputReady(p)||nextToken==Long.MAX_VALUE)return false;
        if(existing!=null&&(existing.input.epoch()!=input.epoch()||existing.input.slot()!=input.slot()))return false;
        stampInput(p);
        Context c=existing;
        if(c==null){Context foreign=CONTEXTS.get(p.getUUID());if(foreign!=null)finish(foreign);c=new Context(p,input);CONTEXTS.put(p.getUUID(),c);}
        c.input=input;boolean editing=p.isShiftKeyDown();Vec3 center=null;
        if(editing){center=FoldedMirrorArray.locatorCenter(p,c.plate);if(center==null){retireEmpty(c);return false;}}
        c.hold=new Hold(input,now(p),editing,center);preview(c,true);return true;
    }
    public static boolean release(ServerPlayer p,String wire){
        Context c=owned(p);HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(c==null||c.resolving||FoldedMirrorArray.processing(p)||c.hold==null||!HighPowerBracerSession.authenticated(p,input)||!c.hold.input.equals(input))return false;
        if(!valid(c)){finish(c);return false;}
        Hold hold=c.hold;c.hold=null;
        if(ScatterLocatorRules.expired(hold.started,now(p),ScatterLocatorRules.HOLD_LIMIT)){retireEmpty(c);return false;}
        c.resolving=true;PROCESSING.add(p);
        try{
            if(hold.editing)return place(c,hold);
            if(now(p)-hold.started>=ScatterLocatorRules.RECALL_TICKS){finish(c);return true;}
            c.preview=true;preview(c,true);return c.plate!=null;
        }finally{PROCESSING.remove(p);c.resolving=false;retireEmpty(c);}
    }
    public static boolean abort(ServerPlayer p,String wire){
        HighPowerInput.Input input=HighPowerInput.parse(wire);
        if(!HighPowerBracerSession.authenticated(p,input))return false;
        Context c=owned(p);
        boolean owns=c!=null&&c.input.epoch()==input.epoch()&&c.input.slot()==input.slot()&&input.input()>=c.input.input();
        if(!owns&&!ID.equals(AbilityStorage.get(p).presets.currentSkill(input.slot())))return false;
        Watermark old=INPUTS.get(p.getUUID());boolean retired=old==null||old.epoch!=input.epoch()||input.input()>old.input;
        if(retired)INPUTS.put(p.getUUID(),new Watermark(input.epoch(),input.input()));
        if(owns)finish(c);return owns||retired;
    }
    public static void abort(ServerPlayer p){Context c=owned(p);if(c!=null)finish(c);}
    public static void remove(ServerPlayer p){if(thread(p)){abort(p);INPUTS.remove(p.getUUID());}}
    public static void clear(){for(Context c:new ArrayList<>(CONTEXTS.values()))finish(c);INPUTS.clear();PROCESSING.clear();}
    public static boolean active(ServerPlayer p){Context c=owned(p);if(c!=null&&!valid(c)){finish(c);return false;}return c!=null;}
    public static boolean holding(ServerPlayer p){return active(p)&&owned(p).hold!=null;}
    public static FoldedMirrorArray.PlateVisual plate(ServerPlayer p){Context c=owned(p);return c==null||c.plate==null?null:visualPlate(c.plate);}
    public static List<FoldedMirrorArray.PlateVisual> plates(ServerPlayer p){var plate=plate(p);return plate==null?List.of():List.of(plate);}
    public static long placedAt(ServerPlayer p){Context c=owned(p);return c==null?-1:c.placedAt;}
    public static int remainingTicks(ServerPlayer p){Context c=owned(p);return c==null||c.placedAt<0?0:(int)Math.max(0,ScatterLocatorRules.LIFETIME-(now(p)-c.placedAt));}
    public static double heldCp(ServerPlayer p){Context c=owned(p);return c==null||c.fee==null?0:c.fee.heldAmount();}
    public static void tick(ServerPlayer p){
        Context c=owned(p);if(c==null||c.resolving)return;
        if(!valid(c)||now(p)<c.lastTick){finish(c);return;}
        if(now(p)==c.lastTick)return;c.lastTick=now(p);
        if(c.hold!=null&&ScatterLocatorRules.expired(c.hold.started,now(p),ScatterLocatorRules.HOLD_LIMIT)){
            c.hold=null;retireEmpty(c);if(owned(p)!=c)return;
        }
        preview(c,false);
    }
    private static boolean place(Context c,Hold hold){
        ServerPlayer p=c.identity.owner();
        if(!valid(c)||FoldedMirrorArray.processing(p)||!FoldedMirrorArray.plates(p).isEmpty()||!RemoteScatterRelay.plates(p).isEmpty())return false;
        Plate candidate=FoldedMirrorArray.locatorCandidate(p,hold.center);
        if(candidate==null){send(c,ACTIVE,DEGENERATE,null,List.of());return false;}
        if(c.plate!=null){
            if(!c.plate.center().equals(candidate.center()))return false;
            c.plate=candidate;send(c,ACTIVE,PREPARED,null,List.of());return true;
        }
        if(!AbilityStorage.get(p).canUse(ID)||!valid(c))return false;
        HighPowerResources.ControlHold fee=HighPowerResources.holdControlFee(p,c,c.identity.token(),ScatterLocatorRules.controlFee(c.position));
        if(fee==null)return false;
        // No external callback runs between this ownership-bound hold and committing the plane.
        c.fee=fee;c.plate=candidate;c.placedAt=now(p);
        send(c,ACTIVE,PREPARED,null,List.of());return true;
    }

    /** Opaque one-use transfer; a native caller cannot mutate the plate or choose another target. */
    static final class Borrow {
        private final Context context;private final Plate plate;private boolean paid,retired;
        private Borrow(Context context){this.context=context;plate=context.plate;}
        Plate plate(){return plate;}
        boolean pay(double shotCost,int cooldown){
            ServerPlayer p=context.identity.owner();
            if(paid||retired||owned(p)!=context||context.resolving||context.hold!=null||context.plate!=plate
                    ||!FoldedMirrorArray.plates(p).isEmpty()||!RemoteScatterRelay.plates(p).isEmpty())return false;
            if(!valid(context)){finish(context);return false;}
            if(!context.fee.claim(context,context.identity.token()))return false;
            paid=HighPowerResources.trySpendControlledMirror(p,context.fee,context,context.identity.token(),shotCost,cooldown);
            if(!paid&&!context.fee.valid(context,context.identity.token()))finish(context);
            return paid;
        }
        void retire(){if(paid&&!retired){retired=true;finish(context);}}
    }
    static Borrow borrow(ServerPlayer p){
        Context c=owned(p);if(c==null)return null;
        if(!valid(c)){finish(c);return null;}
        return c.plate!=null&&c.hold==null&&!c.resolving&&c.fee.state()==HighPowerResources.ControlHold.State.HELD
                &&FoldedMirrorArray.plates(p).isEmpty()&&RemoteScatterRelay.plates(p).isEmpty()?new Borrow(c):null;
    }
    private static boolean canAct(ServerPlayer p){
        if(!thread(p))return false;var state=AbilityStorage.get(p);
        return p.connection!=null&&!p.hasDisconnected()&&p.containerMenu==p.inventoryMenu&&HighPowerResources.ready(p)
                &&!p.getAbilities().instabuild&&!p.getAbilities().invulnerable&&state.activated&&state.overloadFine&&!state.interfering
                &&state.learned(ID)&&state.learned(FoldedMirrorArray.ID)&&state.category.equals("meltdowner")&&state.level>=MIN_LEVEL&&state.level<=MAX_LEVEL
                &&HighPowerResources.effectiveLevel(p)>=MIN_LEVEL&&SkillCatalog.enabled("meltdowner",ID)&&SkillCatalog.enabled("meltdowner",FoldedMirrorArray.ID)
                &&HighPowerCombat.unsupportedReason(p).isEmpty()&&!AbilityConsumption.busy(p)&&!p.noPhysics&&!p.isPassenger()&&!p.isSleeping()
                &&finite(p.position())&&finite(p.getDeltaMovement())&&finite(p.getLookAngle())&&p.getLookAngle().lengthSqr()>.5
                &&Float.isFinite(p.getXRot())&&Float.isFinite(p.getYRot())&&ScatterLocatorRules.clock(now(p))&&FoldedMirrorArray.locatorOwnerVolume(p);
    }
    private static boolean valid(Context c){
        ServerPlayer p=c.identity.owner();
        if(!c.identity.owns(p)||!HighPowerBracerSession.authenticated(p,c.input)||!canAct(p)
                ||c.preset!=c.identity.state().presets.current()||c.revision!=c.identity.state().presets.revision()
                ||!ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))
                ||c.placedAt>=0&&ScatterLocatorRules.expired(c.placedAt,now(p),ScatterLocatorRules.LIFETIME))return false;
        var growth=HighPowerGrowth.values(p);
        return c.position.compareTo(growth.position())==0&&c.earnedPosition.compareTo(growth.earnedPosition())==0
                &&(c.plate==null&&c.fee==null||c.plate!=null&&c.fee!=null&&c.fee.valid(c,c.identity.token()));
    }
    private static void preview(Context c,boolean force){
        ServerPlayer p=c.identity.owner();if(!force&&!ScatterLocatorRules.ready(c.lastVisual,now(p),ScatterLocatorRules.PREVIEW_INTERVAL))return;
        boolean editing=c.hold!=null&&c.hold.editing;
        if(!fenceReady(p,PREVIEW_AT,ScatterLocatorRules.PREVIEW_INTERVAL)){
            if(force)send(c,editing?EDIT:c.preview?PREVIEW:ACTIVE,PREPARED,null,List.of());return;
        }
        stamp(p,PREVIEW_AT);
        Plate ghost=editing?FoldedMirrorArray.locatorCandidate(p,c.hold.center):null;
        Plate shown=editing?ghost:c.preview?c.plate:null;
        List<FoldedMirrorArray.SegmentVisual> path=shown==null?List.of():FoldedMirrorArray.locatorPreview(p,shown);
        send(c,editing?EDIT:c.preview?PREVIEW:ACTIVE,editing&&ghost==null?DEGENERATE:PREPARED,ghost,path);
    }
    private static void retireEmpty(Context c){if(c.plate==null&&c.hold==null)finish(c);}
    private static void finish(Context c){
        if(!CONTEXTS.remove(c.identity.owner().getUUID(),c))return;
        if(c.fee!=null)c.fee.close();c.hold=null;c.plate=null;
        send(c,ENDED,PREPARED,null,List.of());
    }
    private static void send(Context c,int phase,int status,Plate ghost,List<FoldedMirrorArray.SegmentVisual> path){
        ServerPlayer p=c.identity.owner();c.lastVisual=now(p);
        Vec3 ghostCenter=ghost!=null?vec(ghost.center()):c.hold!=null&&c.hold.editing?c.hold.center:Vec3.ZERO;
        Visual visual=new Visual(p.getId(),p.getUUID(),c.input.epoch(),c.input.input(),c.identity.token(),++c.sequence,phase,status,
                phase==ENDED?0:remainingTicks(p),p.position(),phase==ENDED||c.plate==null?List.of():List.of(visualPlate(c.plate)),
                ghostCenter,ghost==null?Vec3.ZERO:vec(ghost.normal()),path,phase==ENDED||c.fee==null?0:c.fee.heldAmount());
        if(phase!=ENDED){c.audience.add(p);for(ServerPlayer observer:c.identity.level().players())if(observer.distanceToSqr(p)<=96*96)c.audience.add(observer);}
        for(ServerPlayer observer:c.audience)if(observer.connection!=null&&!observer.hasDisconnected()&&observer.serverLevel()==c.identity.level())PacketDistributor.sendToPlayer(observer,visual);
    }
    private static boolean inputReady(ServerPlayer p){return fenceReady(p,INPUT_AT,1);}
    private static boolean fenceReady(ServerPlayer p,String key,int interval){
        var root=p.getPersistentData();if(root.contains(DATA)&&!root.contains(DATA,Tag.TAG_COMPOUND))return false;
        CompoundTag data=root.getCompound(DATA);return !data.contains(key)||data.contains(key,Tag.TAG_LONG)&&ScatterLocatorRules.ready(data.getLong(key),now(p),interval);
    }
    private static void stampInput(ServerPlayer p){stamp(p,INPUT_AT);}
    private static void stamp(ServerPlayer p,String key){CompoundTag data=p.getPersistentData().getCompound(DATA);data.putLong(key,now(p));p.getPersistentData().put(DATA,data);}
    private static Context owned(ServerPlayer p){if(!thread(p))return null;Context c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
    private static boolean thread(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static long now(ServerPlayer p){return p.serverLevel().getServer().overworld().getGameTime();}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=30_000_000&&Math.abs(v.y)<=30_000_000&&Math.abs(v.z)<=30_000_000;}
    private static Vec3 vec(Vector v){return new Vec3(v.x(),v.y(),v.z());}
    private static FoldedMirrorArray.PlateVisual visualPlate(Plate plate){return new FoldedMirrorArray.PlateVisual(vec(plate.center()),vec(plate.normal()));}
    private static void writeVec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 readVec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    public record Visual(int owner,UUID ownerUuid,long epoch,long input,long token,long sequence,int phase,int status,int remainingTicks,
                         Vec3 ownerFeet,List<FoldedMirrorArray.PlateVisual> plates,Vec3 ghostCenter,Vec3 ghostNormal,
                         List<FoldedMirrorArray.SegmentVisual> path,double heldCp) implements CustomPacketPayload {
        public Visual{
            plates=List.copyOf(plates);path=List.copyOf(path);
            if(owner<0||ownerUuid==null||epoch<=0||input<=0||token<=0||sequence<=0||phase<ACTIVE||phase>ENDED
                    ||status<PREPARED||status>DEGENERATE||status==4||status==5||remainingTicks<0||remainingTicks>ScatterLocatorRules.LIFETIME
                    ||!finite(ownerFeet)||!finite(ghostCenter)||!finite(ghostNormal)||ghostNormal.lengthSqr()!=0&&Math.abs(ghostNormal.length()-1)>1e-10
                    ||plates.size()>ScatterLocatorRules.MAX_PLATES||path.size()>ScatterLocatorRules.MAX_SEGMENTS
                    ||path.stream().mapToDouble(leg->leg.start().distanceTo(leg.end())).sum()>ScatterLocatorRules.TOTAL_RANGE+1e-8
                    ||!Double.isFinite(heldCp)||heldCp<0||heldCp>2)throw new IllegalArgumentException("Invalid bounded NEW223 visual");
            if(ghostNormal.lengthSqr()!=0)new FoldedMirrorArray.PlateVisual(ghostCenter,ghostNormal);
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","scatter_locator_plate_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeInt(v.owner);b.writeUUID(v.ownerUuid);b.writeLong(v.epoch);b.writeLong(v.input);b.writeLong(v.token);b.writeLong(v.sequence);
            b.writeByte(v.phase);b.writeByte(v.status);b.writeShort(v.remainingTicks);writeVec(b,v.ownerFeet);b.writeByte(v.plates.size());
            for(var plate:v.plates){writeVec(b,plate.center());writeVec(b,plate.normal());}writeVec(b,v.ghostCenter);writeVec(b,v.ghostNormal);b.writeByte(v.path.size());
            for(var leg:v.path){writeVec(b,leg.start());writeVec(b,leg.end());}b.writeDouble(v.heldCp);
        },b->{
            int owner=b.readInt();UUID uuid=b.readUUID();long epoch=b.readLong(),input=b.readLong(),token=b.readLong(),sequence=b.readLong();
            int phase=b.readUnsignedByte(),status=b.readUnsignedByte(),remaining=b.readUnsignedShort();Vec3 feet=readVec(b);
            int count=b.readUnsignedByte();if(count>ScatterLocatorRules.MAX_PLATES)throw new IllegalArgumentException("Unbounded NEW223 plates");
            List<FoldedMirrorArray.PlateVisual> plates=new ArrayList<>(count);for(int i=0;i<count;i++)plates.add(new FoldedMirrorArray.PlateVisual(readVec(b),readVec(b)));
            Vec3 center=readVec(b),normal=readVec(b);count=b.readUnsignedByte();if(count>ScatterLocatorRules.MAX_SEGMENTS)throw new IllegalArgumentException("Unbounded NEW223 preview");
            List<FoldedMirrorArray.SegmentVisual> path=new ArrayList<>(count);for(int i=0;i<count;i++)path.add(new FoldedMirrorArray.SegmentVisual(readVec(b),readVec(b)));
            return new Visual(owner,uuid,epoch,input,token,sequence,phase,status,remaining,feet,plates,center,normal,path,b.readDouble());
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
