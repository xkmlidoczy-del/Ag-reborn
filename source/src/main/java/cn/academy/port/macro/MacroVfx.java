package cn.academy.port.macro;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Accepted server actions only. There is deliberately no client-to-server macro message. */
@EventBusSubscriber(modid="academy")
public final class MacroVfx {
    public enum Phase { CHARGE, ACTIVE, HIT, END }
    public static Consumer<Event> receiver=v->{};
    public static Consumer<Event> observer=v->{};
    private record Key(UUID owner,String skill){}
    private record Stamp(UUID cast,long tick,Phase phase){}
    private static final Map<ServerLevel,Map<Key,Stamp>> CASTS=new WeakHashMap<>();
    private static final Map<ServerPlayer,Set<String>> PAYMENTS=new IdentityHashMap<>();
    private MacroVfx(){}
    public static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    public static double distanceSquared(Vec3 p,Vec3 a,Vec3 b){var d=b.subtract(a);double t=d.lengthSqr()<1e-8?0:Math.clamp(p.subtract(a).dot(d)/d.lengthSqr(),0,1);return p.distanceToSqr(a.add(d.scale(t)));}
    public static void emit(LivingEntity caster,String skill,Phase phase,Vec3 from,Vec3 to,int life,double actualRadius){
        var profile=MacroSkillRules.find(skill);
        if(profile==null||!(caster.level() instanceof ServerLevel world)||!world.getServer().isSameThread()
                ||!caster.isAlive()||!finite(from)||!finite(to)||from.distanceToSqr(to)>MacroSkillRules.MAX_SPAN*MacroSkillRules.MAX_SPAN
                ||from.distanceToSqr(caster.position())>MacroSkillRules.MAX_SPAN*MacroSkillRules.MAX_SPAN
                ||!Double.isFinite(actualRadius)||actualRadius<0||actualRadius>MacroSkillRules.MAX_RADIUS)return;
        if(!SkillCatalog.enabled(profile.category(),profile.skill()))return;
        if(caster instanceof ServerPlayer p){var state=AbilityStorage.get(p);if(phase!=Phase.END&&(!state.category.equals(profile.category())||!state.learned(profile.skill())))return;}
        long now=world.getGameTime();var map=CASTS.computeIfAbsent(world,w->new HashMap<>());var key=new Key(caster.getUUID(),profile.skill());var old=map.get(key);
        // One composition per phase per tick; individual native hit renderers retain their target detail.
        if(old!=null&&old.tick==now&&old.phase==phase)return;
        var cast=old==null||phase==Phase.CHARGE&&old.phase!=Phase.CHARGE||now-old.tick>2400?UUID.randomUUID():old.cast;
        map.put(key,new Stamp(cast,now,phase));
        if(map.size()>1024)map.entrySet().removeIf(e->now-e.getValue().tick>2400);
        // A confirmed area uses the controller's current extent, including novice mastery.
        // A zero-area composition remains decoration and never acquires gameplay semantics.
        double radius=actualRadius>0?actualRadius:profile.ultimate()?profile.radius():Math.min(8,1+profile.level()*1.2);
        // Residue may outlive the instantaneous hit. This never extends a gameplay/controller lease.
        if(phase==Phase.HIT)life=Math.max(life,profile.ultimate()?70:16);
        // This is a standalone release cue, with no earlier presentation under
        // the release skill's ID. Its visible residue is not a gameplay lease.
        if(phase==Phase.END&&skill.equals("float_release_field"))life=Math.max(life,40);
        var v=new Event(cast,caster.getId(),caster.getUUID(),world.dimension().location(),profile.skill(),phase,from,to,profile.shape(),(float)radius,(float)actualRadius,now,Math.clamp(life,1,2400));
        observer.accept(v);
        double audience=radius+64;
        for(var player:world.players())if(!player.hasDisconnected()&&distanceSquared(player.getEyePosition(),from,to)<=audience*audience)PacketDistributor.sendToPlayer(player,v);
    }
    /** Paid upkeep is a local, non-damaging flourish, never a fabricated hit or an unconfirmed field. */
    public static void paid(ServerPlayer p,String skill){
        if(MacroSkillRules.find(skill)!=null)PAYMENTS.computeIfAbsent(p,k->new HashSet<>()).add(skill);
    }
    @SubscribeEvent public static void flush(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
        for(var entry:List.copyOf(PAYMENTS.entrySet())){
            var p=entry.getKey();if(p.server!=e.getServer())continue;PAYMENTS.remove(p);
            if(p.hasDisconnected()||!p.isAlive())continue;
            for(String skill:entry.getValue()){
                var old=CASTS.getOrDefault(p.serverLevel(),Map.of()).get(new Key(p.getUUID(),skill));
                if(old==null||p.serverLevel().getGameTime()-old.tick>=10)emit(p,skill,Phase.ACTIVE,p.getEyePosition(),p.getEyePosition(),20,0);
            }
        }
    }
    /** Observes an already-created server envelope. Decode on client/network threads has no side effects. */
    public static void legacy(CompoundTag tag){
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null||!server.isSameThread()||tag==null||!tag.contains("entity"))return;
        String kind=tag.getString("kind");var profile=kind.equals("arc")?MacroSkillRules.find("arc_gen"):MacroSkillRules.profiles().stream().filter(p->kind.equals(p.skill())||kind.startsWith(p.skill()+"_")).max(Comparator.comparingInt(p->p.skill().length())).orElse(null);
        if(profile==null)return;
        // Its controller publishes the real caster-centred search sphere. Orb/shot
        // packets retain native beams, but must not replace that sphere with a fake one.
        if(profile.skill().equals("electron_missile"))return;
        // Position heartbeats do not restart the show; only authored start/release/result messages do.
        if(kind.endsWith("_position")&&!kind.equals("plasma_cannon_position")||kind.endsWith("_particles"))return;
        for(var world:server.getAllLevels())if(world.getEntity(tag.getInt("entity")) instanceof LivingEntity actor){
            Vec3 from=tag.contains("x")?new Vec3(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z")):actor.getEyePosition();
            Vec3 to=from;double length=tag.getDouble("length");
            if(length>0&&tag.contains("dx")){var axis=new Vec3(tag.getDouble("dx"),tag.getDouble("dy"),tag.getDouble("dz"));if(finite(axis)&&axis.lengthSqr()>.5)to=from.add(axis.normalize().scale(Math.min(MacroSkillRules.MAX_SPAN,length)));}
            Phase phase=kind.endsWith("_end")?Phase.END:kind.endsWith("_start")?Phase.CHARGE:kind.endsWith("_hit")?Phase.HIT:Phase.ACTIVE;
            emit(actor,profile.skill(),phase,from,to,phase==Phase.END?12:phase==Phase.CHARGE?120:32,0);return;
        }
    }
    public record Event(UUID cast,int entity,UUID owner,ResourceLocation dimension,String skill,Phase phase,Vec3 from,Vec3 to,
                        MacroSkillRules.Shape shape,float visualRadius,float actualRadius,long tick,int life) implements CustomPacketPayload {
        public static final Type<Event> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","macro_vfx_v1"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Event> CODEC=StreamCodec.of((b,v)->{
            b.writeUUID(v.cast);b.writeVarInt(v.entity);b.writeUUID(v.owner);b.writeResourceLocation(v.dimension);b.writeUtf(v.skill,64);b.writeEnum(v.phase);
            b.writeVec3(v.from);b.writeVec3(v.to);b.writeEnum(v.shape);b.writeFloat(v.visualRadius);b.writeFloat(v.actualRadius);b.writeLong(v.tick);b.writeVarInt(v.life);
        },b->new Event(b.readUUID(),b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readUtf(64),b.readEnum(Phase.class),b.readVec3(),b.readVec3(),b.readEnum(MacroSkillRules.Shape.class),b.readFloat(),b.readFloat(),b.readLong(),b.readVarInt()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Event.TYPE,Event.CODEC,(v,c)->c.enqueueWork(()->receiver.accept(v)));}
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){CASTS.clear();PAYMENTS.clear();}
}
