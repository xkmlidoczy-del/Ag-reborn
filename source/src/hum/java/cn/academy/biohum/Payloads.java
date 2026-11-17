package cn.academy.biohum;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.BlockPos;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import java.util.function.IntConsumer;
import java.util.function.Consumer;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
public final class Payloads {
 public static IntConsumer openClient=id->{};
 public static Consumer<SkillCue> cueClient=cue->{};
 public static Consumer<PhaseState> phaseClient=state->{};
 public record Open(int entity) implements CustomPacketPayload {
  public static final Type<Open> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(BioHum.ID,"open"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Open> CODEC=StreamCodec.of((b,p)->b.writeVarInt(p.entity),b->new Open(b.readVarInt()));
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 public record Action(int entity,String action) implements CustomPacketPayload {
  public static final Type<Action> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(BioHum.ID,"action"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Action> CODEC=StreamCodec.of((b,p)->{b.writeVarInt(p.entity);b.writeUtf(p.action,128);},b->new Action(b.readVarInt(),b.readUtf(128)));
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 public enum Cue { RELEASE, BEAM, IMPACT, MOVE, CANCEL, FIELD, REFLECT, TERRAIN,
                   WINDUP, WALL, PARRY, WHIFF, RECOVER, ILLUSION }
 public record PhaseState(String phase, BlockPos anchor, int radius, long began, boolean ready) implements CustomPacketPayload {
  public static final Type<PhaseState> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(BioHum.ID,"othinus_phase"));
  public static final StreamCodec<RegistryFriendlyByteBuf,PhaseState> CODEC=StreamCodec.of((b,p)->{
   b.writeUtf(p.phase,32);b.writeBlockPos(p.anchor);b.writeVarInt(p.radius);b.writeVarLong(p.began);b.writeBoolean(p.ready);
  },b->new PhaseState(b.readUtf(32),b.readBlockPos(),b.readVarInt(),b.readVarLong(),b.readBoolean()));
  public static PhaseState clear(){return new PhaseState("",BlockPos.ZERO,0,0,true);}
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 public record Cast(UUID actor, String skill, long started) {}
 public record SkillCue(UUID actor, String skill, long started, long time, Cue cue, Vec3 from, Vec3 to,
                        float size, int step, UUID subject) implements CustomPacketPayload {
  public static final Type<SkillCue> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath(BioHum.ID,"skill_cue"));
  public static final StreamCodec<RegistryFriendlyByteBuf,SkillCue> CODEC=StreamCodec.of((b,p)->{
   b.writeUUID(p.actor);b.writeUtf(p.skill,96);b.writeVarLong(p.started);b.writeVarLong(p.time);
   b.writeEnum(p.cue);vector(b,p.from);vector(b,p.to);b.writeFloat(p.size);
   b.writeVarInt(p.step+1);b.writeNullable(p.subject,(buf,id)->buf.writeUUID(id));
  },b->new SkillCue(b.readUUID(),b.readUtf(96),b.readVarLong(),b.readVarLong(),b.readEnum(Cue.class),
    vector(b),vector(b),b.readFloat(),b.readVarInt()-1,b.readNullable(buf->buf.readUUID())));
  public Cast cast(){return new Cast(actor,skill,started);}
  private static void vector(RegistryFriendlyByteBuf b,Vec3 p){b.writeDouble(p.x);b.writeDouble(p.y);b.writeDouble(p.z);}
  private static Vec3 vector(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
  @Override public Type<? extends CustomPacketPayload> type(){return TYPE;}
 }
 public static void cue(HumNpc a,String skill,Cue cue,Vec3 from,Vec3 to,float size){
  cue(a,skill,cue,from,to,size,-1,null);
 }
 static void cue(HumNpc a,String skill,Cue cue,Vec3 from,Vec3 to,float size,int step,UUID subject){
  if(!(a.level() instanceof ServerLevel level))return;
  long started=skill.equals(a.technique())?a.techniqueStarted():level.getGameTime();
  cue(level,a.getUUID(),skill,started,cue,from,to,size,step,subject);
 }
 static void cue(ServerLevel level,UUID actor,String skill,long started,Cue cue,Vec3 from,Vec3 to,float size,
                 int step,UUID subject){
  var payload=new SkillCue(actor,skill,started,level.getGameTime(),cue,from,to,size,step,subject);
  // Observe the actual path, including clients near an impact whose caster is outside entity tracking.
  double view=level.getServer().getPlayerList().getViewDistance()*16.0;
  Vec3 delta=to.subtract(from);
  for(var p:level.players()){
   double t=delta.lengthSqr()==0?0:Math.clamp(p.position().subtract(from).dot(delta)/delta.lengthSqr(),0,1);
   if(p.position().distanceToSqr(from.add(delta.scale(t)))<=view*view)PacketDistributor.sendToPlayer(p,payload);
  }
 }
 static void privateCue(HumNpc actor,ServerPlayer player,String skill,Cue cue,Vec3 at){
  if(player.level()!=actor.level())return;
  PacketDistributor.sendToPlayer(player,new SkillCue(actor.getUUID(),skill,actor.techniqueStarted(),
          actor.level().getGameTime(),cue,at,at,1,-1,player.getUUID()));
 }
 public static void register(RegisterPayloadHandlersEvent e){
  var r=e.registrar("7");
  r.playToClient(Open.TYPE,Open.CODEC,(p,c)->c.enqueueWork(()->openClient.accept(p.entity)));
  r.playToClient(SkillCue.TYPE,SkillCue.CODEC,(p,c)->c.enqueueWork(()->cueClient.accept(p)));
  r.playToClient(PhaseState.TYPE,PhaseState.CODEC,(p,c)->c.enqueueWork(()->phaseClient.accept(p)));
  r.playToServer(Action.TYPE,Action.CODEC,(p,c)->c.enqueueWork(()->{
   if(c.player() instanceof net.minecraft.server.level.ServerPlayer sp&&sp.level().getEntity(p.entity) instanceof HumNpc a)
    a.request(sp,p.action);
  }));
 }
}
