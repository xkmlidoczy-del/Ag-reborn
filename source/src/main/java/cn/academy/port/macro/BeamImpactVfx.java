package cn.academy.port.macro;

import cn.academy.port.highpower.HighPowerCombat;
import java.math.BigDecimal;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Result-only presentation, including real terrain writes and balances observed across the native hit. */
@EventBusSubscriber(modid="academy")
public final class BeamImpactVfx {
    public static Consumer<Scene> receiver=v->{};
    public static Consumer<Scene> observer=v->{};
    public enum Style { BEAM, BLAST, PRESSURE, CONTACT, PAPER }
    public enum Outcome { HEALTH, SHIELD, BOTH, BLOCKED, CONTROL }
    public record Cell(BlockPos position,int originalState){}
    public record Contact(Vec3 point,Vec3 normal,Outcome outcome){}
    public record Pools(BigDecimal health,BigDecimal shield,float absorption){}
    private record Key(UUID owner,String skill,UUID cast,Vec3 centre){}
    private record Impacts(LivingEntity owner,Vec3 origin,List<Contact> contacts){}
    private static final Map<ServerLevel,Map<Key,List<Cell>>> PENDING=new WeakHashMap<>();
    private static final Map<ServerLevel,Map<Key,Impacts>> CONTACTS=new WeakHashMap<>();
    private BeamImpactVfx(){}

    public static void beam(ServerPlayer p,String skill,Vec3 from,Vec3 to,float radius,List<Cell> removed,List<Vec3> impacts){
        send(p,new Scene(MacroVfx.castId(p,skill),p.getId(),p.getUUID(),p.serverLevel().dimension().location(),skill,
                Style.BEAM,from,to,radius,p.getMainArm()==HumanoidArm.LEFT?-1:1,p.getYHeadRot(),p.serverLevel().getGameTime(),
                50,List.copyOf(removed.stream().limit(256).toList()),List.copyOf(impacts.stream().limit(64).toList())));
    }

    /** Called around the existing hurt invocation. Never feeds amounts back into settlement. */
    public static Pools before(LivingEntity caster,String skill,LivingEntity target){
        if(MacroSkillRules.find(skill)==null||!(caster.level() instanceof ServerLevel w)||!w.getServer().isSameThread()
                ||target.level()!=w||w.players().isEmpty())return null;
        return HighPowerCombat.visualPools(target);
    }

    public static void after(LivingEntity caster,String skill,LivingEntity target,Pools before,boolean accepted,Vec3 origin,Vec3 point){
        if(before==null)return;
        var after=HighPowerCombat.visualPools(target);
        boolean hp=after.health.compareTo(before.health)<0;
        boolean shield=after.shield.compareTo(before.shield)<0||after.absorption<before.absorption;
        if(accepted&&!hp&&!shield)return;
        Outcome outcome=hp?(shield?Outcome.BOTH:Outcome.HEALTH):shield?Outcome.SHIELD:Outcome.BLOCKED;
        contact(caster,skill,origin==null?caster.getEyePosition():origin,point==null?target.getBoundingBox().getCenter():point,outcome);
    }

    public static void contact(LivingEntity caster,String skill,Vec3 origin,Vec3 point,Outcome outcome){
        var profile=MacroSkillRules.find(skill);
        if(profile==null||!(caster.level() instanceof ServerLevel w)||!w.getServer().isSameThread()||!caster.isAlive()
                ||!MacroVfx.finite(origin)||!MacroVfx.finite(point)||origin.distanceToSqr(point)>192*192)return;
        var batches=CONTACTS.computeIfAbsent(w,ignored->new LinkedHashMap<>());
        var key=new Key(caster.getUUID(),profile.skill(),MacroVfx.castId(caster,profile.skill()),null);
        if(batches.size()>=256&&!batches.containsKey(key))return;
        var batch=batches.computeIfAbsent(key,ignored->new Impacts(caster,origin,new ArrayList<>()));
        if(batch.contacts.size()<64)batch.contacts.add(new Contact(point,origin.subtract(point).normalize(),outcome));
    }

    /** Called after every paper placement commits. Cells are in build order. */
    public static void paper(ServerPlayer p,String skill,List<BlockPos> positions){
        if(positions.isEmpty()||positions.size()>32)return;
        var w=p.serverLevel();
        var cells=positions.stream().map(pos->new Cell(pos.immutable(),Block.getId(w.getBlockState(pos)))).toList();
        send(p,new Scene(MacroVfx.castId(p,skill),p.getId(),p.getUUID(),w.dimension().location(),skill,Style.PAPER,
                positions.getFirst().getCenter(),positions.getLast().getCenter(),1,1,p.getYRot(),w.getGameTime(),30,cells,List.of()));
    }

    public static void changed(ServerPlayer p,String skill,UUID cast,Vec3 centre,BlockPos at,BlockState original){
        if(!p.server.isSameThread()||p.hasDisconnected())return;
        var cells=PENDING.computeIfAbsent(p.serverLevel(),w->new LinkedHashMap<>())
                .computeIfAbsent(new Key(p.getUUID(),skill,cast,centre),k->new ArrayList<>());
        if(cells.size()<256)cells.add(new Cell(at.immutable(),Block.getId(original)));
    }

    @SubscribeEvent public static void flush(ServerTickEvent.Post event){
        for(var w:event.getServer().getAllLevels()){
            var groups=PENDING.remove(w);
            if(groups!=null)for(var entry:groups.entrySet()){
                var key=entry.getKey();
                var p=w.getServer().getPlayerList().getPlayer(key.owner);
                var cells=entry.getValue();
                if(p==null||p.serverLevel()!=w||cells.isEmpty())continue;
                boolean blast=key.centre!=null;
                var from=blast?key.centre:p.getEyePosition();
                var to=blast?key.centre:cells.getFirst().position.getCenter();
                send(p,new Scene(key.cast,p.getId(),p.getUUID(),w.dimension().location(),key.skill,
                        blast?Style.BLAST:Style.PRESSURE,from,to,1,1,p.getYRot(),w.getGameTime(),34,List.copyOf(cells),List.of()));
            }
            var batches=CONTACTS.remove(w);
            if(batches!=null)for(var entry:batches.entrySet()){
                var key=entry.getKey();var batch=entry.getValue();var owner=batch.owner;
                if(owner.level()!=w||batch.contacts.isEmpty())continue;
                var scene=new Scene(key.cast,owner.getId(),owner.getUUID(),w.dimension().location(),key.skill,Style.CONTACT,
                        batch.origin,batch.contacts.getFirst().point,1,1,owner.getYRot(),w.getGameTime(),18,List.of(),List.of(),List.copyOf(batch.contacts));
                observer.accept(scene);
                for(var viewer:w.players()){
                    if(viewer.hasDisconnected()||MacroVfx.privateView(key.skill)&&!key.owner.equals(viewer.getUUID()))continue;
                    var eye=viewer.getEyePosition();
                    if(batch.contacts.stream().anyMatch(c->c.point.distanceToSqr(eye)<=96*96))
                        PacketDistributor.sendToPlayer(viewer,scene);
                }
            }
        }
    }

    private static void send(ServerPlayer p,Scene scene){
        if(!p.server.isSameThread()||!p.isAlive()||p.hasDisconnected()||!MacroVfx.finite(scene.from)||!MacroVfx.finite(scene.to)
                ||scene.from.distanceToSqr(scene.to)>192*192)return;
        observer.accept(scene);
        double reach=scene.style==Style.BEAM?72+scene.radius:160;
        for(var viewer:p.serverLevel().players())if(!viewer.hasDisconnected()
                &&MacroVfx.distanceSquared(viewer.getEyePosition(),scene.from,scene.to)<=reach*reach)
            PacketDistributor.sendToPlayer(viewer,scene);
    }

    public record Scene(UUID cast,int entity,UUID owner,ResourceLocation dimension,String skill,Style style,Vec3 from,Vec3 to,
                        float radius,int hand,float yaw,long tick,int life,List<Cell> cells,List<Vec3> impacts,List<Contact> contacts)
            implements CustomPacketPayload{
        public Scene(UUID cast,int entity,UUID owner,ResourceLocation dimension,String skill,Style style,Vec3 from,Vec3 to,
                     float radius,int hand,float yaw,long tick,int life,List<Cell> cells,List<Vec3> impacts){
            this(cast,entity,owner,dimension,skill,style,from,to,radius,hand,yaw,tick,life,cells,impacts,List.of());
        }
        public static final Type<Scene> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","beam_impact_v3"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Scene> CODEC=StreamCodec.of((b,v)->{
            b.writeUUID(v.cast);b.writeVarInt(v.entity);b.writeUUID(v.owner);b.writeResourceLocation(v.dimension);
            b.writeUtf(v.skill,64);b.writeEnum(v.style);b.writeVec3(v.from);b.writeVec3(v.to);b.writeFloat(v.radius);
            b.writeByte(v.hand);b.writeFloat(v.yaw);b.writeLong(v.tick);b.writeVarInt(v.life);
            b.writeVarInt(v.cells.size());
            for(var c:v.cells){b.writeBlockPos(c.position);b.writeVarInt(c.originalState);}
            b.writeVarInt(v.impacts.size());v.impacts.forEach(b::writeVec3);
            b.writeVarInt(v.contacts.size());
            for(var c:v.contacts){b.writeVec3(c.point);b.writeVec3(c.normal);b.writeEnum(c.outcome);}
        },b->{
            UUID cast=b.readUUID();int entity=b.readVarInt();UUID owner=b.readUUID();var dimension=b.readResourceLocation();
            String skill=b.readUtf(64);var style=b.readEnum(Style.class);var from=b.readVec3();var to=b.readVec3();
            float radius=b.readFloat();int hand=b.readByte();float yaw=b.readFloat();long tick=b.readLong();int life=b.readVarInt();
            int n=count(b,256);var cells=new ArrayList<Cell>(n);
            for(int i=0;i<n;i++)cells.add(new Cell(b.readBlockPos(),b.readVarInt()));
            n=count(b,64);var impacts=new ArrayList<Vec3>(n);
            for(int i=0;i<n;i++)impacts.add(b.readVec3());
            n=count(b,64);var contacts=new ArrayList<Contact>(n);
            for(int i=0;i<n;i++)contacts.add(new Contact(b.readVec3(),b.readVec3(),b.readEnum(Outcome.class)));
            return new Scene(cast,entity,owner,dimension,skill,style,from,to,radius,hand,yaw,tick,life,
                    List.copyOf(cells),List.copyOf(impacts),List.copyOf(contacts));
        });
        private static int count(RegistryFriendlyByteBuf b,int max){
            int count=b.readVarInt();
            if(count<0||count>max)throw new IllegalArgumentException("Invalid scene element count");
            return count;
        }
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }

    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network{
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){
            e.registrar("3").playToClient(Scene.TYPE,Scene.CODEC,(v,c)->c.enqueueWork(()->receiver.accept(v)));
        }
    }

    @SubscribeEvent public static void stopped(ServerStoppedEvent e){PENDING.clear();CONTACTS.clear();}
}
