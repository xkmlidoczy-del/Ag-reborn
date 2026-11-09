package cn.academy.port.highpower;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.core.SkillTuning;
import cn.academy.port.highpower.core.BrakingTouchRules;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.phys.*;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** NEW413: one real owned-object stop OR one certified landing calculation. No damage or item creation. */
@EventBusSubscriber(modid="academy")
public final class BrakingTouch {
 public static final String ID="braking_touch", PROOF="academy:braking_snowball_origin", SPENT="academy:braking_touch_spent";
 public static final int ARMED=0,BRAKED=1,LANDED=2,ENDED=3;
 private static final Map<UUID,Context> CONTEXTS=new HashMap<>();
 private static final Map<UUID,Watermark> INPUTS=new HashMap<>();
 private static long nextToken;
 private static final boolean DIAGNOSTIC=Boolean.getBoolean("academy.highpower.brakingTouchDiagnostic");
 private static final org.slf4j.Logger LOG=org.slf4j.LoggerFactory.getLogger("academy.braking_touch_qa");
 private static int diagnosticCount;
 public static Consumer<Visual> clientReceiver=ignored->{};
 private record Watermark(long epoch,long input){}
 private static final class Context {
  final LegacySingleKeyServerIdentity identity;final HighPowerInput.Input input;final long started;final boolean landing;
  final int preset;final long revision;final double cost;final int cooldown;
  final Set<ServerPlayer> audience=new LinkedHashSet<>();
  long sequence,expires;boolean resolving,done;
  Context(ServerPlayer p,HighPowerInput.Input in){identity=new LegacySingleKeyServerIdentity(p,in.input(),++nextToken,AbilityStorage.get(p));input=in;started=now(p);landing=p.isShiftKeyDown();preset=identity.state().presets.current();revision=identity.state().presets.revision();var q=HighPowerGrowth.values(p).position();cost=BrakingTouchRules.cpCost(q);cooldown=BrakingTouchRules.cooldownTicks(q);expires=started+BrakingTouchRules.ARM_TICKS;}
 }
 private record Target(Entity entity,UUID uuid,int id,Vec3 position,AABB box,Vec3 velocity,CompoundTag proof,net.minecraft.world.item.ItemStack stack){
  static Target of(Entity e){return new Target(e,e.getUUID(),e.getId(),e.position(),e.getBoundingBox(),e.getDeltaMovement(),e.getPersistentData().copy(),e instanceof ItemEntity i?i.getItem().copy():((Snowball)e).getItem().copy());}
  boolean same(ServerPlayer p){return p.serverLevel()==entity.level()&&!entity.isRemoved()&&p.serverLevel().getEntity(uuid)==entity&&p.serverLevel().getEntity(id)==entity&&uuid.equals(entity.getUUID())&&position.equals(entity.position())&&box.equals(entity.getBoundingBox())&&velocity.equals(entity.getDeltaMovement())&&proof.equals(entity.getPersistentData())&&net.minecraft.world.item.ItemStack.matches(stack,entity instanceof ItemEntity i?i.getItem():((Snowball)entity).getItem());}
 }
 private BrakingTouch(){}
 /** Read-only, opt-in owner gate for bounded native-contact QA observations. */
 static boolean diagnosticLandingArmed(ServerPlayer p){var c=owned(p);return DIAGNOSTIC&&c!=null&&c.landing&&!c.done;}
 public static boolean start(ServerPlayer p,String wire){
  if(InertialAnchor.active(p) || (CirculationRelief.active(p) || AutonomousVectorCircle.active(p)) || (MultiAxisReflection.active(p) || WindPressureLift.active(p)))return feedback(p,"not_ready");
  Context old=owned(p);if(old!=null&&old.resolving)return false;
  var in=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,in))return false;
  var last=INPUTS.get(p.getUUID());if(last!=null&&last.epoch==in.epoch()&&last.input>=in.input())return false;
  INPUTS.put(p.getUUID(),new Watermark(in.epoch(),in.input()));
  if(!canAct(p)||!AbilityStorage.get(p).canUse(ID)||!ID.equals(AbilityStorage.get(p).presets.currentSkill(in.slot()))||nextToken==Long.MAX_VALUE)return feedback(p,"not_ready");
  if(old!=null)finish(old,true);
  Context c=new Context(p,in);CONTEXTS.put(p.getUUID(),c);
  if(c.landing){if(!BrakingTouchLandingProof.available(p)){finish(c,true);return feedback(p,"unsupported_landing");}send(c,ARMED,p.position(),Vec3.ZERO,Vec3.ZERO);return true;}
  c.resolving=true;
  try{
   Entity target=first(p);if(target==null||!eligible(p,target)){finish(c,true);return feedback(p,"no_target");}
   Target snapshot=Target.of(target);Vec3 origin=p.getEyePosition(),look=p.getLookAngle();
   if(!valid(c)||!snapshot.same(p)||first(p)!=target||!origin.equals(p.getEyePosition())||!look.equals(p.getLookAngle())){finish(c,true);return false;}
   if(!HighPowerResources.trySpend(p,ID,c.cost,c.cooldown)){finish(c,true);return feedback(p,"not_ready");}
   // Payment is a final opportunity; an unsupported mutation after it cannot mint a refund or repeat authority.
   c.done=true;c.expires=now(p)+BrakingTouchRules.VISUAL_TICKS;
   if(!valid(c)||!snapshot.same(p)||first(p)!=target||!eligible(p,target)){finish(c,true);return feedback(p,"spent_noop");}
   target.getPersistentData().putBoolean(SPENT,true);
   target.setDeltaMovement(Vec3.ZERO);target.hasImpulse=true;target.hurtMarked=true;
   send(c,BRAKED,snapshot.position,snapshot.velocity,Vec3.ZERO);return true;
  }finally{c.resolving=false;}
 }
 /** Called only by the separately fingerprinted native accepted-contact scope, after fall-event callbacks. */
 public static float reduceLanding(ServerPlayer p,float distance,float multiplier){
  Context c=owned(p);var proof=BrakingTouchLandingProof.current(p);
  if(c==null||!c.landing||c.done||c.resolving||!valid(c)||proof==null||!proof.valid())return distance;
  c.resolving=true;
  try{
   float reduced=BrakingTouchRules.reducedDistance(distance);
   double safe=p.getAttributeValue(Attributes.SAFE_FALL_DISTANCE),factor=p.getAttributeValue(Attributes.FALL_DAMAGE_MULTIPLIER);
   if(!p.serverLevel().getGameRules().getBoolean(net.minecraft.world.level.GameRules.RULE_FALL_DAMAGE)||p.getType().is(net.minecraft.tags.EntityTypeTags.FALL_DAMAGE_IMMUNE)||!Float.isFinite(distance)||distance<0||!Float.isFinite(multiplier)||multiplier<=0||!Double.isFinite(safe)||!Double.isFinite(factor)||factor<=0||damage(distance,safe,multiplier,factor)<=0||damage(reduced,safe,multiplier,factor)>=damage(distance,safe,multiplier,factor)||!proof.claim()){finish(c,true);return distance;}
   Vec3 velocity=p.getDeltaMovement(),point=proof.point();
   if(!valid(c)||!proof.valid()||!HighPowerResources.trySpend(p,ID,c.cost,c.cooldown)){finish(c,true);return distance;}
   c.done=true;c.expires=now(p)+BrakingTouchRules.VISUAL_TICKS;
   if(!valid(c)||!proof.valid()||!velocity.equals(p.getDeltaMovement())){finish(c,true);feedback(p,"spent_noop");return distance;}
   if(DIAGNOSTIC&&diagnosticCount<96){diagnosticCount++;LOG.info("NEW413_CONTACT tick={} entity={} distance={} reduced={} multiplier={}",now(p),p.getId(),distance,reduced,multiplier);}
   send(c,LANDED,point,proof.incomingVelocity(),velocity);return reduced;
  }finally{c.resolving=false;}
 }
 private static double damage(float d,double safe,float multiplier,double factor){float excess=d-(float)safe;float scaled=excess*multiplier;return net.minecraft.util.Mth.ceil((double)scaled*factor);}
 public static void landingFinished(ServerPlayer p){var c=owned(p);if(c!=null&&c.landing&&!c.done)finish(c,true);}
 public static boolean release(ServerPlayer p,String wire){return stop(p,wire,false);}
 public static boolean abort(ServerPlayer p,String wire){return stop(p,wire,true);}
 private static boolean stop(ServerPlayer p,String wire,boolean force){var in=HighPowerInput.parse(wire);if(!HighPowerBracerSession.authenticated(p,in))return false;var prior=INPUTS.get(p.getUUID());if(prior==null||prior.epoch!=in.epoch()||prior.input<in.input())INPUTS.put(p.getUUID(),new Watermark(in.epoch(),in.input()));var c=owned(p);if(c==null||c.input.epoch()!=in.epoch()||c.input.slot()!=in.slot()||in.input()<c.input.input())return false;if(force||c.landing&&!c.done)finish(c,true);return true;}
 public static void abort(ServerPlayer p){var c=owned(p);if(c!=null)finish(c,true);}
 public static void remove(ServerPlayer p){if(thread(p)){abort(p);INPUTS.remove(p.getUUID());}}
 public static boolean active(ServerPlayer p){return owned(p)!=null;}
 public static void tick(ServerPlayer p){var c=owned(p);if(c==null||c.resolving)return;if(!valid(c)||now(p)>=c.expires)finish(c,!c.done);}
 public static void clear(){CONTEXTS.clear();INPUTS.clear();diagnosticCount=0;}
 private static boolean valid(Context c){ServerPlayer p=c.identity.owner();return CONTEXTS.get(p.getUUID())==c&&c.identity.owns(p)&&HighPowerBracerSession.authenticated(p,c.input)&&canAct(p)&&c.preset==c.identity.state().presets.current()&&c.revision==c.identity.state().presets.revision()&&ID.equals(c.identity.state().presets.currentSkill(c.input.slot()))&&now(p)>=c.started&&now(p)<c.expires;}
 private static boolean canAct(ServerPlayer p){if(!thread(p)||!HighPowerResources.ready(p)||!HighPowerCategories.VECMANIP.equals(AbilityStorage.get(p).category)||!AbilityStorage.get(p).learned(ID)||HighPowerCombat.unsupportedReason(p).isPresent()||AbilityConsumption.busy(p)||p.isPassenger()||p.isVehicle()||p.isSleeping()||p.noPhysics||p.isFallFlying()||p.getAbilities().flying||p.isInWaterOrBubble()||p.isInLava()||p.onClimbable()||p.containerMenu!=p.inventoryMenu||!finite(p.position())||!finite(p.getDeltaMovement())||!BrakingTouchRules.clock(now(p)))return false;return DisplacedProjectionWorld.volume(p,p.getBoundingBox()).isEmpty();}
 private static Entity first(ServerPlayer p){
  double range=BrakingTouchRules.RANGE;
  Vec3 start=p.getEyePosition(),end=start.add(p.getLookAngle().scale(range));AABB query=new AABB(start,end).inflate(.000001);
  if(!DisplacedProjectionWorld.volume(p,query).isEmpty())return null;
  var solid=p.serverLevel().clip(new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.ANY,p));double wall=solid.getType()==HitResult.Type.MISS?Double.POSITIVE_INFINITY:start.distanceToSqr(solid.getLocation());
  List<Entity> all=new ArrayList<>();p.serverLevel().getEntities(EntityTypeTest.forClass(Entity.class),query,e->e!=p&&!e.isRemoved()&&!e.isSpectator(),all,BrakingTouchRules.SCAN_LIMIT+1);if(all.size()>BrakingTouchRules.SCAN_LIMIT)return null;
  record Hit(Entity e,double d){}List<Hit> hits=new ArrayList<>();
  for(Entity e:all){AABB b=e.getBoundingBox();Optional<Vec3> at=b.contains(start)?Optional.of(start):b.clip(start,end);if(at.isEmpty()&&inside(b,end))at=Optional.of(end);if(at.isPresent()&&inside(b,at.get())){double d=start.distanceToSqr(at.get());if(d<=range*range&&d<wall)hits.add(new Hit(e,d));}}
  hits.sort(Comparator.comparingDouble(Hit::d).thenComparingInt(h->eligible(p,h.e)?1:0).thenComparing(h->h.e.getUUID()));return hits.isEmpty()?null:hits.getFirst().e;
 }
 private static boolean inside(AABB b,Vec3 v){return v.x>=b.minX&&v.x<=b.maxX&&v.y>=b.minY&&v.y<=b.maxY&&v.z>=b.minZ&&v.z<=b.maxZ;}
 private static boolean eligible(ServerPlayer p,Entity e){
  if(e==null||e.level()!=p.serverLevel()||e.isRemoved()||e.isPassenger()||e.isVehicle()||e.noPhysics||e.isNoGravity()||e.isInWaterOrBubble()||e.isInLava()||e.getPersistentData().contains(SPENT)||e.getPersistentData().getBoolean("academy:protected")||e.getPersistentData().getBoolean("academy:immovable")||!finite(e.getDeltaMovement())||e.getDeltaMovement().lengthSqr()<=1e-12||e.getDeltaMovement().lengthSqr()>16||!DisplacedProjectionWorld.volume(p,e.getBoundingBox()).isEmpty())return false;
  if(e.getClass()==ItemEntity.class&&e.getType()==EntityType.ITEM){var proof=SpatialRelayProvenance.proof((ItemEntity)e);return proof!=null&&proof.owner().equals(p.getUUID())&&(((ItemEntity)e).getTarget()==null||p.getUUID().equals(((ItemEntity)e).getTarget()))&&!SpatialRelay.active(p)&&!MagneticItemPull.active(p)&&!RecoveryMark.active(p);}
  if(e.getClass()!=Snowball.class||e.getType()!=EntityType.SNOWBALL||!(e instanceof NativeScatterArrowOwnerAccess a)||a.academy$scatterCachedOwner()!=p||!p.getUUID().equals(a.academy$scatterOwnerUuid()))return false;
  Snowball snow=(Snowball)e;var stack=snow.getItem();var tag=e.getPersistentData().getCompound(PROOF);
  return stack.is(Items.SNOWBALL)&&stack.getCount()==1&&stack.isComponentsPatchEmpty()&&tag.getAllKeys().size()==3&&tag.hasUUID("owner")&&tag.hasUUID("entity")&&tag.hasUUID("nonce")&&tag.getUUID("owner").equals(p.getUUID())&&tag.getUUID("entity").equals(e.getUUID())&&!tag.getUUID("nonce").equals(new UUID(0,0));
 }
 @SubscribeEvent(priority=EventPriority.LOWEST,receiveCanceled=true) public static void joined(EntityJoinLevelEvent event){
  Entity e=event.getEntity();if(e.level().isClientSide||e.getClass()!=Snowball.class||e.getType()!=EntityType.SNOWBALL||event.loadedFromDisk())return;
  e.getPersistentData().remove(PROOF);if(event.isCanceled()||!(e instanceof NativeScatterArrowOwnerAccess a)||!(a.academy$scatterCachedOwner() instanceof ServerPlayer p)||!p.getUUID().equals(a.academy$scatterOwnerUuid())||!thread(p)||p.serverLevel()!=e.level())return;
  CompoundTag t=new CompoundTag();t.putUUID("owner",p.getUUID());t.putUUID("entity",e.getUUID());t.putUUID("nonce",UUID.randomUUID());e.getPersistentData().put(PROOF,t);
 }
 public static void ownerChanged(Entity e){if(e!=null)e.getPersistentData().remove(PROOF);}
 private static void finish(Context c,boolean notify){if(CONTEXTS.remove(c.identity.owner().getUUID(),c)&&notify)send(c,ENDED,c.identity.owner().position(),Vec3.ZERO,Vec3.ZERO);}
 private static void send(Context c,int phase,Vec3 at,Vec3 before,Vec3 after){ServerPlayer p=c.identity.owner();long until=phase==ENDED?now(p):c.expires;Visual v=new Visual(p.getId(),p.getUUID(),c.identity.token(),++c.sequence,c.input.input(),c.input.epoch(),p.serverLevel().dimension().location(),phase,at,before,after,until);if(DIAGNOSTIC&&diagnosticCount<96){diagnosticCount++;LOG.info("NEW413_VISUAL tick={} entity={} phase={} token={} point={} before={} after={} expires={}",now(p),p.getId(),phase,c.identity.token(),at,before,after,until);}
  c.audience.add(p);if(phase==BRAKED||phase==LANDED)for(var o:p.serverLevel().players())if(o.distanceToSqr(p)<=64*64&&!p.isInvisibleTo(o))c.audience.add(o);for(var o:c.audience)if(o.connection!=null&&!o.hasDisconnected()&&o.serverLevel()==p.serverLevel())PacketDistributor.sendToPlayer(o,v);}
 private static boolean feedback(ServerPlayer p,String key){if(HighPowerCombat.unsupportedReason(p).isPresent())key="unsupported";Object[] args="no_target".equals(key)?new Object[]{SkillTuning.format(ID,"{{reach}}")}:new Object[0];p.displayClientMessage(Component.translatable("message.academy.highpower.braking_touch."+key,args),true);return false;}
 private static Context owned(ServerPlayer p){if(!thread(p))return null;var c=CONTEXTS.get(p.getUUID());return c!=null&&c.identity.owner()==p?c:null;}
 private static boolean thread(ServerPlayer p){return p!=null&&p.server.isSameThread();}private static long now(ServerPlayer p){return p.serverLevel().getGameTime();}
 private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z)&&Math.abs(v.x)<=3e7&&Math.abs(v.y)<=3e7&&Math.abs(v.z)<=3e7;}
 private static void vec(RegistryFriendlyByteBuf b,Vec3 v){b.writeDouble(v.x);b.writeDouble(v.y);b.writeDouble(v.z);}private static Vec3 vec(RegistryFriendlyByteBuf b){return new Vec3(b.readDouble(),b.readDouble(),b.readDouble());}
 public record Visual(int entityId,UUID owner,long token,long sequence,long input,long epoch,ResourceLocation dimension,int phase,Vec3 point,Vec3 before,Vec3 after,long expiresAt)implements CustomPacketPayload{
  public Visual{if(entityId<0||owner==null||token<=0||sequence<=0||sequence>4||input<=0||epoch<=0||dimension==null||phase<0||phase>3||!finite(point)||!finite(before)||!finite(after)||!BrakingTouchRules.clock(expiresAt))throw new IllegalArgumentException("Invalid braking receipt");}
  public static final Type<Visual> TYPE=new Type<>(ResourceLocation.fromNamespaceAndPath("academy","braking_touch_visual"));
  public static final StreamCodec<RegistryFriendlyByteBuf,Visual> CODEC=StreamCodec.of((b,v)->{b.writeInt(v.entityId);b.writeUUID(v.owner);b.writeLong(v.token);b.writeLong(v.sequence);b.writeLong(v.input);b.writeLong(v.epoch);b.writeResourceLocation(v.dimension);b.writeByte(v.phase);vec(b,v.point);vec(b,v.before);vec(b,v.after);b.writeLong(v.expiresAt);},b->new Visual(b.readInt(),b.readUUID(),b.readLong(),b.readLong(),b.readLong(),b.readLong(),b.readResourceLocation(),b.readUnsignedByte(),vec(b),vec(b),vec(b),b.readLong()));public Type<?extends CustomPacketPayload> type(){return TYPE;}
 }
 @EventBusSubscriber(modid="academy",bus=EventBusSubscriber.Bus.MOD)public static final class Network{@SubscribeEvent public static void register(RegisterPayloadHandlersEvent e){e.registrar("1").playToClient(Visual.TYPE,Visual.CODEC,(p,c)->clientReceiver.accept(p));}}
 @SubscribeEvent public static void tick(ServerTickEvent.Post e){for(var c:new ArrayList<>(CONTEXTS.values()))if(c.identity.level().getServer()==e.getServer())tick(c.identity.owner());}
 @SubscribeEvent public static void logout(PlayerEvent.PlayerLoggedOutEvent e){if(e.getEntity()instanceof ServerPlayer p)remove(p);}
 @SubscribeEvent public static void clone(PlayerEvent.Clone e){if(e.getOriginal()instanceof ServerPlayer p)remove(p);}
 @SubscribeEvent public static void dimension(PlayerEvent.PlayerChangedDimensionEvent e){if(e.getEntity()instanceof ServerPlayer p)remove(p);}
 @SubscribeEvent public static void death(LivingDeathEvent e){if(!e.isCanceled()&&e.getEntity()instanceof ServerPlayer p)remove(p);}
 @SubscribeEvent public static void stopped(ServerStoppedEvent e){clear();}
}
