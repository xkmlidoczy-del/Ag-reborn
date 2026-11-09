package cn.academy.port.canon;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Server-confirmed, bounded presentation only; never supplies gameplay targets or damage. */
@EventBusSubscriber(modid="academy")
public final class CanonVfx {
    public enum Phase { START, ACTIVE, HIT, END }
    public static Consumer<Visual> clientReceiver = visual -> {};
    /** GameTest-only audit tap on accepted server emissions; never affects gameplay. */
    public static Consumer<Visual> serverObserver = visual -> {};
    private static final Map<ServerPlayer,Quota> PLAYERS=new WeakHashMap<>();
    private static final Map<MinecraftServer,Quota> SERVERS=new IdentityHashMap<>();
    private record Quota(long tick,int count) {}
    private CanonVfx() {}

    /** Emit only after a successful action. Endpoints are server-observed world coordinates. */
    public static void emit(ServerPlayer player,String skill,Vec3 origin,Vec3 endpoint,Phase phase,int durationTicks) {
        emit(player,skill,origin,endpoint,phase,durationTicks,0);
    }
    /**
     * Area skills also report the radius their controller actually checks, centred on {@code origin}, so the client can
     * draw the real reach instead of a motif-sized guess. Zero means no area.
     */
    public static void emit(ServerPlayer player,String skill,Vec3 origin,Vec3 endpoint,Phase phase,int durationTicks,double radius) {
        if(!Double.isFinite(radius) || radius<0 || radius>cn.academy.port.core.RangedSkillRanges.MAX_CANON_AREA_RADIUS) return;
        if(player==null || skill==null || skill.length()>64 || phase==null || durationTicks<1 || durationTicks>maximumDuration(skill)
                || !finite(origin) || !finite(endpoint) || !player.server.isSameThread() || player.connection==null
                || player.hasDisconnected() || player.isRemoved() || !player.isAlive()
                || player.server.getPlayerList().getPlayer(player.getUUID())!=player
                || origin.distanceToSqr(player.position())>64*64 || endpoint.distanceToSqr(origin)>cn.academy.port.core.RangedSkillRanges.MAX_CANON_VISUAL_SPAN*cn.academy.port.core.RangedSkillRanges.MAX_CANON_VISUAL_SPAN) return;
        var state=AbilityStorage.get(player); var design=CanonVfxDesign.find(skill);
        // END only retires this owner's existing mark/pose. Category changes clear learned skills before leases unwind.
        if(design==null || SkillCatalog.find(design.category(),skill).isEmpty()
                || phase!=Phase.END&&(!design.category().equals(state.category)||!state.learned(skill)||!SkillCatalog.enabled(state.category,skill))) return;
        long tick=player.serverLevel().getGameTime();
        Quota pq=PLAYERS.get(player),sq=SERVERS.get(player.server);
        int pc=pq==null||pq.tick()!=tick?0:pq.count(),sc=sq==null||sq.tick()!=tick?0:sq.count();
        if(pc>=8||sc>=128)return;
        PLAYERS.put(player,new Quota(tick,pc+1));SERVERS.put(player.server,new Quota(tick,sc+1));
        Vec3 axis=player.getLookAngle().normalize();if(!finite(axis)||axis.lengthSqr()<.99)return;
        var visual=new Visual(player.getId(),player.getUUID(),player.serverLevel().dimension().location(),skill,origin,endpoint,phase,durationTicks,(float)radius,axis);
        serverObserver.accept(visual);
        PacketDistributor.sendToPlayersNear(player.serverLevel(),null,origin.x,origin.y,origin.z,64,visual);
    }
    /** Long mental control marks follow their real leases; other effects keep the original bound. */
    public static int maximumDuration(String skill){var d=CanonVfxDesign.find(skill);return d!=null&&d.category().equals("mental_out")?2400:200;}
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    /** Non-player native controllers use the identical canon packet, identity and renderer. */
    public static void emitEntity(net.minecraft.world.entity.LivingEntity caster,cn.academy.port.core.AbilityProgress state,
                                  String skill,Vec3 origin,Vec3 endpoint,Phase phase,int ticks) {
        if(!(caster.level() instanceof net.minecraft.server.level.ServerLevel world)||!caster.isAlive()
                ||!world.getServer().isSameThread()||!state.learned(skill)||!SkillCatalog.enabled(state.category,skill)
                ||!finite(origin)||!finite(endpoint)) return;
        var design=CanonVfxDesign.find(skill);
        if(design==null||!design.category().equals(state.category)) return;
        var axis=endpoint.subtract(origin);if(axis.lengthSqr()<1e-6)axis=caster.getLookAngle();
        var visual=new Visual(caster.getId(),caster.getUUID(),world.dimension().location(),skill,origin,endpoint,phase,Math.max(1,Math.min(200,ticks)),0,axis.normalize());
        serverObserver.accept(visual);
        PacketDistributor.sendToPlayersNear(world,null,origin.x,origin.y,origin.z,64,visual);
    }
    public record Visual(int entity,UUID owner,ResourceLocation dimension,String skill,Vec3 origin,Vec3 endpoint,Phase phase,int duration,float radius,Vec3 axis) implements CustomPacketPayload {
        public Visual(int entity,UUID owner,ResourceLocation dimension,String skill,Vec3 origin,Vec3 endpoint,Phase phase,int duration,float radius){
            this(entity,owner,dimension,skill,origin,endpoint,phase,duration,radius,endpoint.distanceToSqr(origin)>1e-8?endpoint.subtract(origin).normalize():new Vec3(0,0,1));
        }
        public Visual(int entity,UUID owner,ResourceLocation dimension,String skill,Vec3 origin,Vec3 endpoint,Phase phase,int duration){
            this(entity,owner,dimension,skill,origin,endpoint,phase,duration,0f);
        }
        public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","canon_visual"));
        public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{
            b.writeVarInt(v.entity);b.writeUUID(v.owner);b.writeResourceLocation(v.dimension);b.writeUtf(v.skill,64);
            vec(b,v.origin);vec(b,v.endpoint);b.writeEnum(v.phase);b.writeVarInt(v.duration);b.writeFloat(v.radius);vec(b,v.axis);
        },b->new Visual(b.readVarInt(),b.readUUID(),b.readResourceLocation(),b.readUtf(64),vec(b),vec(b),b.readEnum(Phase.class),b.readVarInt(),b.readFloat(),vec(b)));
        public Type<? extends CustomPacketPayload> type(){return TYPE;}
    }
    private static void vec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}
    private static Vec3 vec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
    @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)
    public static final class Network {
        @SubscribeEvent public static void register(RegisterPayloadHandlersEvent event){
            event.registrar("3").playToClient(Visual.TYPE,Visual.CODEC,(packet,context)->context.enqueueWork(()->clientReceiver.accept(packet)));
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent e){SERVERS.remove(e.getServer()); PLAYERS.keySet().removeIf(p->p.server==e.getServer());}
}
