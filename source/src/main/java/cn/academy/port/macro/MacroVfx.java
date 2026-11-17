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
    public enum Phase { CHARGE, ACTIVE, HIT, END, RELEASE }
    /** Spatial interpretation for rendering only. The original controller still owns target eligibility. */
    public enum Boundary { NONE, EFFECT, SEARCH, CURTAIN }
    public static Consumer<Event> receiver=v->{};
    public static Consumer<Event> observer=v->{};
    private record Key(UUID owner,String skill){}
    private static final class Cast {
        final UUID id=UUID.randomUUID();
        final Set<UUID> viewers=new HashSet<>();
        long tick, phaseStart;
        long nativeToken;
        Phase phase;
        Event live, result;
        Cast(long tick){this.tick=phaseStart=tick;}
    }
    private static final Map<ServerLevel,Map<Key,Cast>> CASTS=new WeakHashMap<>();
    private static final Map<ServerPlayer,Set<String>> PAYMENTS=new IdentityHashMap<>();
    private MacroVfx(){}
    public static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
    public static double distanceSquared(Vec3 p,Vec3 a,Vec3 b){var d=b.subtract(a);double t=d.lengthSqr()<1e-8?0:Math.clamp(p.subtract(a).dot(d)/d.lengthSqr(),0,1);return p.distanceToSqr(a.add(d.scale(t)));}
    /** Shares a cast reference with later damage/terrain commits, including queued writes. */
    public static UUID castId(LivingEntity caster,String skill){
        var profile=MacroSkillRules.find(skill);
        if(profile==null||!(caster.level() instanceof ServerLevel w)||!w.getServer().isSameThread())return UUID.randomUUID();
        return cast(w,new Key(caster.getUUID(),profile.skill()),w.getGameTime(),0,null).id;
    }
    private static Cast cast(ServerLevel w,Key key,long now,long token,Phase phase){
        var map=CASTS.computeIfAbsent(w,ignored->new LinkedHashMap<>());
        var old=map.get(key);
        if(old==null||stale(old,now)
                ||token>0&&old.nativeToken>0&&token!=old.nativeToken
                ||old.phase==Phase.END&&phase!=null&&phase!=Phase.END&&now>old.tick
                ||phase==Phase.CHARGE&&old.phase!=null&&old.phase!=Phase.CHARGE&&now>old.tick){
            if(map.size()>=2048&&!map.containsKey(key))map.remove(map.keySet().iterator().next());
            old=new Cast(now);
            map.put(key,old);
        }
        if(token>0)old.nativeToken=token;
        return old;
    }
    private static boolean alive(Event event,long now){return event!=null&&now<event.tick()+event.life();}
    private static boolean stale(Cast cast,long now){return now-cast.tick>100&&!alive(cast.live,now)&&!alive(cast.result,now);}
    public static void emit(LivingEntity caster,String skill,Phase phase,Vec3 from,Vec3 to,int life,double actualRadius){
        emit(caster,skill,phase,from,to,life,actualRadius,0);
    }
    public static void emit(LivingEntity caster,String skill,Phase phase,Vec3 from,Vec3 to,int life,double actualRadius,long nativeToken){
        var profile=MacroSkillRules.find(skill);
        if(profile==null||!(caster.level() instanceof ServerLevel world)||!world.getServer().isSameThread()
                ||!caster.isAlive()||!finite(from)||!finite(to)||from.distanceToSqr(to)>MacroSkillRules.MAX_SPAN*MacroSkillRules.MAX_SPAN
                ||from.distanceToSqr(caster.position())>MacroSkillRules.MAX_SPAN*MacroSkillRules.MAX_SPAN
                ||!Double.isFinite(actualRadius)||actualRadius<0||actualRadius>MacroSkillRules.MAX_RADIUS)return;
        if(!SkillCatalog.enabled(profile.category(),profile.skill()))return;
        if(caster instanceof ServerPlayer p){var state=AbilityStorage.get(p);if(phase!=Phase.END&&(!state.category.equals(profile.category())||!state.learned(profile.skill())))return;}
        long now=world.getGameTime();
        var cast=cast(world,new Key(caster.getUUID(),profile.skill()),now,nativeToken,phase);
        if(cast.tick==now&&cast.phase==phase)return;
        if(cast.phase!=phase)cast.phaseStart=now;
        cast.tick=now;
        cast.phase=phase;
        // A confirmed area uses the controller's current extent, including novice mastery.
        // A zero-area composition remains decoration and never acquires gameplay semantics.
        double radius=actualRadius>0?actualRadius:profile.ultimate()?profile.radius():Math.min(8,1+profile.level()*1.2);
        // Residue may outlive the instantaneous hit. This never extends a gameplay/controller lease.
        if(phase==Phase.HIT)life=Math.max(life,profile.ultimate()?70:16);
        // This is a standalone release cue, with no earlier presentation under
        // the release skill's ID. Its visible residue is not a gameplay lease.
        if(phase==Phase.END&&skill.equals("float_release_field"))life=Math.max(life,40);
        var v=new Event(cast.id,caster.getId(),caster.getUUID(),world.dimension().location(),profile.skill(),phase,from,to,profile.shape(),(float)radius,(float)actualRadius,now,Math.clamp(life,1,2400),cast.phaseStart);
        if(phase==Phase.END)cast.live=null;
        else if(phase==Phase.HIT){
            if(cast.live!=null&&cast.live.phase()==Phase.CHARGE)cast.live=null;
            if(cast.live==null||v.actualRadius()>0||cast.live.actualRadius()==0)cast.result=v;
        }else cast.live=v;
        observer.accept(v);
        for(var player:world.players())if(!player.hasDisconnected()&&(inView(player,v)||phase==Phase.END&&cast.viewers.contains(player.getUUID()))){
            PacketDistributor.sendToPlayer(player,v);
            cast.viewers.add(player.getUUID());
        }
    }
    static boolean privateView(String skill){
        return skill.equals("mine_detect")||skill.equals("safe_landing_probe")||skill.equals("circulation_listening")
                ||skill.equals("liquid_dual_sensor")||skill.equals("liquid_sensor")
                ||skill.startsWith("mental_")&&(skill.endsWith("_scan")||skill.endsWith("_sense")||skill.endsWith("_probe"));
    }
    private static boolean inView(ServerPlayer player,Event v){
        return (!privateView(v.skill())||v.owner().equals(player.getUUID()))
                &&distanceSquared(player.getEyePosition(),v.from(),v.to())<=Math.pow(v.visualRadius()+64,2);
    }
    /** A committed cooldown identifies an immediate cast before its first hit or terrain receipt. */
    public static void paid(ServerPlayer p,String skill){
        var profile=MacroSkillRules.find(skill);
        if(profile==null)return;
        var world=p.serverLevel();long now=world.getGameTime();var key=new Key(p.getUUID(),profile.skill());
        var old=CASTS.getOrDefault(world,Map.of()).get(key);
        if(old!=null&&old.tick<now&&old.nativeToken==0&&old.phase!=Phase.CHARGE&&!alive(old.live,now)){
            // Keep any real warm-up/ongoing controller ID. A recently settled instant cast may still be fading.
            CASTS.get(world).put(key,new Cast(now));
        }
        PAYMENTS.computeIfAbsent(p,k->new HashSet<>()).add(skill);
    }
    @SubscribeEvent public static void flush(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
        for(var entry:List.copyOf(PAYMENTS.entrySet())){
            var p=entry.getKey();if(p.server!=e.getServer())continue;PAYMENTS.remove(p);
            if(p.hasDisconnected()||!p.isAlive())continue;
            for(String skill:entry.getValue()){
                var old=CASTS.getOrDefault(p.serverLevel(),Map.of()).get(new Key(p.getUUID(),skill));
                if(old==null||old.phase==null||p.serverLevel().getGameTime()-old.tick>=10&&(old.live==null||old.live.actualRadius()==0))
                    emit(p,skill,Phase.ACTIVE,p.getEyePosition(),p.getEyePosition(),20,0);
            }
        }
        if(e.getServer().getTickCount()%5!=0)return;
        for(var w:e.getServer().getAllLevels()){
            var map=CASTS.get(w);
            if(map==null)continue;
            long now=w.getGameTime();
            map.values().removeIf(c->stale(c,now));
            for(var c:map.values()){
                if(alive(c.live,now)){
                    var owner=w.getEntity(c.live.entity());
                    if(owner==null||!owner.isAlive()||!owner.getUUID().equals(c.live.owner()))retire(w,c,now);
                }
                var v=alive(c.live,now)?c.live:alive(c.result,now)?c.result:null;
                if(v==null)continue;
                // Rejoining players and players who crossed the show boundary need the current age again.
                c.viewers.removeIf(id->{var p=w.getServer().getPlayerList().getPlayer(id);
                    return p==null||p.serverLevel()!=w||p.hasDisconnected()||!inView(p,v);});
                for(var p:w.players())if(!p.hasDisconnected()&&!c.viewers.contains(p.getUUID())&&inView(p,v)){
                    PacketDistributor.sendToPlayer(p,v);
                    c.viewers.add(p.getUUID());
                }
            }
        }
    }
    private static void retire(ServerLevel world,Cast cast,long now){
        var previous=cast.live;
        cast.live=null;
        var end=new Event(cast.id,previous.entity(),previous.owner(),previous.dimension(),previous.skill(),Phase.END,
                previous.from(),previous.to(),previous.shape(),previous.visualRadius(),0,now,1,now);
        observer.accept(end);
        for(var viewer:world.players())if(!viewer.hasDisconnected()&&cast.viewers.contains(viewer.getUUID()))
            PacketDistributor.sendToPlayer(viewer,end);
    }
    /** Observes an already-created server envelope. Decode on client/network threads has no side effects. */
    public static void legacy(CompoundTag tag){
        var server=ServerLifecycleHooks.getCurrentServer();if(server==null||!server.isSameThread()||tag==null||!tag.contains("entity"))return;
        String kind=tag.getString("kind");var profile=kind.equals("arc")?MacroSkillRules.find("arc_gen"):MacroSkillRules.profiles().stream().filter(p->kind.equals(p.skill())||kind.startsWith(p.skill()+"_")).max(Comparator.comparingInt(p->p.skill().length())).orElse(null);
        if(profile==null)return;
        // Its controller publishes the real caster-centred search sphere. Orb/shot
        // packets retain native beams, but must not replace that sphere with a fake one.
        if(Set.of("electron_missile","plasma_cannon","thunder_clap").contains(profile.skill()))return;
        // Position heartbeats do not restart the show; only authored start/release/result messages do.
        if(kind.endsWith("_position")&&!kind.equals("plasma_cannon_position")||kind.endsWith("_particles"))return;
        for(var world:server.getAllLevels())if(world.getEntity(tag.getInt("entity")) instanceof LivingEntity actor){
            Vec3 from=tag.contains("x")?new Vec3(tag.getDouble("x"),tag.getDouble("y"),tag.getDouble("z")):actor.getEyePosition();
            Vec3 to=from;double length=tag.getDouble("length");
            if(length>0&&tag.contains("dx")){var axis=new Vec3(tag.getDouble("dx"),tag.getDouble("dy"),tag.getDouble("dz"));if(finite(axis)&&axis.lengthSqr()>.5)to=from.add(axis.normalize().scale(Math.min(MacroSkillRules.MAX_SPAN,length)));}
            Phase phase=kind.endsWith("_end")?Phase.END:kind.endsWith("_start")?Phase.CHARGE:
                    kind.endsWith("_hit")?Phase.HIT:kind.endsWith("_ray")||kind.endsWith("_go")?Phase.RELEASE:Phase.ACTIVE;
            emit(actor,profile.skill(),phase,from,to,phase==Phase.END?12:phase==Phase.CHARGE?120:32,0,tag.getLong("token"));return;
        }
    }
    public record Event(UUID cast,int entity,UUID owner,ResourceLocation dimension,String skill,Phase phase,Vec3 from,Vec3 to,
                        MacroSkillRules.Shape shape,float visualRadius,float actualRadius,long tick,int life,long phaseStart) implements CustomPacketPayload {
        public Event(UUID cast,int entity,UUID owner,ResourceLocation dimension,String skill,Phase phase,Vec3 from,Vec3 to,
                     MacroSkillRules.Shape shape,float visualRadius,float actualRadius,long tick,int life){
            this(cast,entity,owner,dimension,skill,phase,from,to,shape,visualRadius,actualRadius,tick,life,tick);
        }
        public Boundary boundary(){
            if(actualRadius<=0||phase==Phase.END)return Boundary.NONE;
            if(skill.equals("aero_air_curtain"))return Boundary.CURTAIN;
            if(skill.equals("electron_missile")||phase==Phase.CHARGE&&!cn.academy.port.canon.CanonExpansionCombatA.physicalBarrier(skill))return Boundary.SEARCH;
            return Boundary.EFFECT;
        }
        public static final Type<Event> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","macro_vfx_v2"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Event> CODEC=StreamCodec.of((b,v)->{
            b.writeUUID(v.cast);b.writeVarInt(v.entity);b.writeUUID(v.owner);b.writeResourceLocation(v.dimension);b.writeUtf(v.skill,64);b.writeEnum(v.phase);
            b.writeVec3(v.from);b.writeVec3(v.to);b.writeEnum(v.shape);b.writeFloat(v.visualRadius);b.writeFloat(v.actualRadius);b.writeLong(v.tick);b.writeVarInt(v.life);b.writeLong(v.phaseStart);
        },b->new Event(b.readUUID(),b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readUtf(64),b.readEnum(Phase.class),b.readVec3(),b.readVec3(),b.readEnum(MacroSkillRules.Shape.class),b.readFloat(),b.readFloat(),b.readLong(),b.readVarInt(),b.readLong()));
        @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("2").playToClient(Event.TYPE,Event.CODEC,(v,c)->c.enqueueWork(()->receiver.accept(v)));}
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){CASTS.clear();PAYMENTS.clear();}
}
