package cn.academy.biohum;

import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.*;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.item.*;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.*;
import net.minecraft.network.chat.Component;
import net.minecraft.world.*;
import net.minecraft.world.phys.Vec3;
import java.util.*;

public class HumNpc extends PathfinderMob {
 private static final EntityDataAccessor<Integer> MODE=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Integer> EXPRESSION=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<String> CAST=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.STRING);
 private static final EntityDataAccessor<String> SIGNATURE=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.STRING);
 private static final EntityDataAccessor<Long> SIGNATURE_START=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.LONG);
 private static final EntityDataAccessor<String> PHASE=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.STRING);
 private static final EntityDataAccessor<String> WINGS=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.STRING);
 private static final EntityDataAccessor<Float> CAST_YAW=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.FLOAT);
 private static final EntityDataAccessor<Float> CAST_PITCH=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.FLOAT);
 private static final EntityDataAccessor<Long> WING_FOLD=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.LONG);
 private static final EntityDataAccessor<Integer> CHAMBER=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.INT);
 private static final EntityDataAccessor<Long> CHAMBER_SIGNAL=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.LONG);
 private static final EntityDataAccessor<Long> CAST_ABORT=SynchedEntityData.defineId(HumNpc.class,EntityDataSerializers.LONG);
 public final String characterId;
 public UUID follower,controlledBy,interlocutor;
 public long controlUntil,guardUntil,flameUntil,coverUntil;
 public int controlInstruction;
 public int crystals=3,shots;
 private final Map<String,Long> cooldowns=new HashMap<>();
 private net.minecraft.core.BlockPos chamberSeat;
 private Vec3 pendingPoint;
 private UUID pendingIssuer;
 private float pendingHealth;
 private String pending="";private UUID pendingTarget;private long pendingAt;private int wirePulses;
 long wireStarted;
 CloseCombat.Swing closeSwing;
 long parryUntil;
 long riposteUntil;
 UUID unreachableTarget;
 long unreachableUntil;
 net.minecraft.core.BlockPos retreatTo;
 private UUID wireTarget;
 public final List<UUID> runes=new ArrayList<>();
 public HumNpc(EntityType<? extends PathfinderMob> t,Level l,String id){
  super(t,l);characterId=id;setPersistenceRequired();
  var p=profile();getAttribute(Attributes.MAX_HEALTH).setBaseValue(p.health());setHealth(p.health());
  getAttribute(Attributes.ATTACK_DAMAGE).setBaseValue(p.attack());
  switch(id){
   case "hamazura" -> {
    setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(BioHum.PISTOL.get()));
    setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(BioHum.CARTRIDGE.get(),6));
   }
   case "kanzaki" -> setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.IRON_SWORD));
   case "mikoto" -> setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.IRON_NUGGET,16));
   case "mugino" -> setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.AMETHYST_SHARD,6));
   case "aureolus" -> setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(Items.GOLD_NUGGET,12));
   case "stiyl" -> setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(BioHum.RUNE_CARD.get(),12));
   case "etzali" -> setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.OBSIDIAN));
   case "agnese" -> setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(Items.BLAZE_ROD));
   case "fiamma" -> setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(BioHum.RELIC.get()));
   case "lidvia" -> setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(BioHum.RELIC.get()));
   default -> {}
  }
  if(suspended()){setNoGravity(true);refreshDimensions();}
 }
 public Profiles.Profile profile(){return Profiles.get(characterId);}
 public String phaseId(){String id=entityData.get(PHASE);return id.isEmpty()?profile().phase():id;}
 public Skills.Phase phase(){return Skills.phase(characterId,phaseId());}
 public List<String> skills(){return phase().skills();}
 public float phaseScale(){return phase().scale();}
 public static final String SUSPENDED_PHASE="ot22_original_body";
 public boolean suspended(){return "aleister".equals(characterId)&&SUSPENDED_PHASE.equals(phaseId());}
 public boolean chamberInstalled(){return entityData.get(CHAMBER)!=0;}
 public int chamberSignal(){
  return (int)(entityData.get(CHAMBER_SIGNAL)&3);
 }
 public boolean chamberSignalActive(){return (entityData.get(CHAMBER_SIGNAL)>>>2)>level().getGameTime();}
 void chamberSignal(int state,int duration){entityData.set(CHAMBER_SIGNAL,((level().getGameTime()+duration)<<2)|(state&3));}
 void mountChamber(net.minecraft.core.BlockPos pos){
  chamberSeat=pos.immutable();entityData.set(CHAMBER,1);setNoGravity(true);
  Vec3 at=Vec3.atBottomCenterOf(pos).add(0,1,0);
  moveTo(at.x,at.y,at.z,0,0);setYBodyRot(0);setYHeadRot(0);
 }
 public boolean wingsFolded(){return entityData.get(WING_FOLD)>level().getGameTime();}
 void foldWingsUntil(long time){entityData.set(WING_FOLD,time);}
 public boolean selectPhase(String id){
  if(Skills.phase(characterId,id)==null)return false;
  applyPhase(id);
  if(characterId.equals("othinus")){
   if(id.equals("nt09_magic_god")&&getMainHandItem().isEmpty()
           &&!getPersistentData().getBoolean("BioHumBrokenGungnir")){
    setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(BioHum.GUNGNIR.get()));
   }else if(!id.equals("nt09_magic_god")&&getMainHandItem().is(BioHum.GUNGNIR.get())){
    setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
   }
  }
  if(characterId.equals("aleister")){
   setNoGravity(suspended());
   if(!suspended()){
    if(chamberSeat!=null){
     var exit=Vec3.atBottomCenterOf(chamberSeat.offset(0,1,4));
     if(Abilities.safeDestination(this,exit))moveTo(exit.x,exit.y,exit.z,0,0);
    }
    chamberSeat=null;entityData.set(CHAMBER,0);
    if(getMainHandItem().isEmpty()||getMainHandItem().is(Items.BLAZE_ROD))
     setItemSlot(EquipmentSlot.MAINHAND,new ItemStack(BioHum.BLASTING_ROD.get()));
   }else if(getMainHandItem().is(Items.BLAZE_ROD)||getMainHandItem().is(BioHum.BLASTING_ROD.get()))
    setItemSlot(EquipmentSlot.MAINHAND,ItemStack.EMPTY);
  }
  return true;
 }
 private void applyPhase(String id){
  interrupt();clearSignature();entityData.set(WINGS,"");entityData.set(WING_FOLD,0L);
  entityData.set(PHASE,id.equals(profile().phase())?"":id);refreshDimensions();
 }
 @Override public EntityDimensions getDefaultDimensions(Pose pose){
  var base=super.getDefaultDimensions(pose);
  if(suspended())return EntityDimensions.scalable(.78f,3.6f).withEyeHeight(1.37f);
  return characterId==null||entityData.get(PHASE).isEmpty()?base:base.scale(phaseScale()/profile().scale());
 }
 @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key){
  super.onSyncedDataUpdated(key);
  if(PHASE.equals(key)&&characterId!=null)refreshDimensions();
 }
 @Override public boolean shouldRenderAtSqrDistance(double distance){return phaseScale()<.3f?distance<48*48:super.shouldRenderAtSqrDistance(distance);}
 public static AttributeSupplier.Builder attributes(){return Mob.createMobAttributes().add(Attributes.MAX_HEALTH,24).add(Attributes.MOVEMENT_SPEED,.27).add(Attributes.ATTACK_DAMAGE,3).add(Attributes.FOLLOW_RANGE,32).add(Attributes.KNOCKBACK_RESISTANCE,.1);}
 @Override protected void defineSynchedData(SynchedEntityData.Builder b){
  super.defineSynchedData(b);
  b.define(MODE,0);b.define(EXPRESSION,0);b.define(CAST,"");
  b.define(SIGNATURE,"");b.define(SIGNATURE_START,0L);b.define(PHASE,"");b.define(WINGS,"");
  b.define(CAST_YAW,0f);b.define(CAST_PITCH,0f);b.define(CAST_ABORT,-1L);b.define(WING_FOLD,0L);
  b.define(CHAMBER,0);b.define(CHAMBER_SIGNAL,0L);
 }
 public int mode(){return entityData.get(MODE);}public int expression(){return entityData.get(EXPRESSION);}public String casting(){return entityData.get(CAST);}
 public NpcMove signature(){return NpcMove.find(technique());}
 public String technique(){return entityData.get(SIGNATURE);}
 public long techniqueStarted(){return entityData.get(SIGNATURE_START);}
 public long signatureAge(){return level().getGameTime()-techniqueStarted();}
 void beginSignature(NpcMove move){beginTechnique(move.id());}
 void beginTechnique(String id){
  entityData.set(CAST_YAW,yBodyRot);
  entityData.set(CAST_PITCH,0f);
  entityData.set(CAST_ABORT,-1L);
  entityData.set(SIGNATURE_START,level().getGameTime());
  entityData.set(SIGNATURE,id);
  var skill=Skills.get(id);
  if(skill!=null&&skill.windup()>0){
   var at=position().add(0,getBbHeight()*.72,0);
   Payloads.cue(this,id,Payloads.Cue.WINDUP,at,at,1);
  }
 }
 void faceCast(Vec3 point){
  if(point==null)return;
  float yaw=CastMotion.yaw(position(),point,castYaw());
  var delta=point.subtract(position().add(0,getBbHeight()*.74,0));
  float pitch=(float)Math.toDegrees(Math.atan2(-delta.y,Math.max(.25,delta.horizontalDistance())));
  var skill=Skills.get(technique());
  float pitchLimit=skill!=null&&CloseCombat.isAttack(skill)?30:73;
  entityData.set(CAST_PITCH,net.minecraft.util.Mth.clamp(pitch,-pitchLimit,pitchLimit));
  entityData.set(CAST_YAW,yaw);
 }
 public float castYaw(){return entityData.get(CAST_YAW);}
 public float castPitch(){return entityData.get(CAST_PITCH);}
 public long abortAge(){long at=entityData.get(CAST_ABORT);return at<0?-1:level().getGameTime()-at;}
 public long abortFrame(){return entityData.get(CAST_ABORT)-techniqueStarted();}
 public void cancelTechnique(){
  if(!technique().isEmpty()&&entityData.get(CAST_ABORT)<0)entityData.set(CAST_ABORT,level().getGameTime());
 }
 void clearSignature(){entityData.set(SIGNATURE,"");closeSwing=null;parryUntil=0;}
 public String wings(){return entityData.get(WINGS);}
 void spreadWings(String id,int duration){
  entityData.set(WINGS,id);getPersistentData().putLong("BioHumWings",level().getGameTime()+duration);
 }
 public long readyAt(String id){return cooldowns.getOrDefault(id,0L);}
 public long readyAt(int slot){return readyAt(Skills.legacy(profile(),slot));}
 public void setReady(String id,long time){if(time>0)cooldowns.put(id,time);else cooldowns.remove(id);}
 public void setReady(int slot,long time){setReady(Skills.legacy(profile(),slot),time);}
 void resolved(String id){getPersistentData().putString("BioHumResolved",id);getPersistentData().putLong("BioHumResolvedAt",level().getGameTime());}
 void resolvedAutonomousDuel(String id){getPersistentData().putString("BioHumAutoDuelSelected",id);}
 public void expression(int n){entityData.set(EXPRESSION,Math.max(0,Math.min(2,n)));}
 @Override public Component getName(){return getCustomName()!=null?getCustomName():Component.literal(profile().name());}
 @Override protected void registerGoals(){
  goalSelector.addGoal(0,new FloatGoal(this){@Override public boolean canUse(){return !suspended()&&super.canUse();}});
  goalSelector.addGoal(1,new CombatGoal());
  goalSelector.addGoal(2,new FollowGoal());
  goalSelector.addGoal(5,new WaterAvoidingRandomStrollGoal(this,.7){@Override public boolean canUse(){return !suspended()&&mode()==0&&!controlled()&&super.canUse();}});
  goalSelector.addGoal(6,new LookAtPlayerGoal(this,Player.class,7));goalSelector.addGoal(7,new RandomLookAroundGoal(this));
  targetSelector.addGoal(1,new HurtByTargetGoal(this));
 }
 public Player owner(){return follower==null?null:level().getPlayerByUUID(follower);}
 public ServerPlayer audience(){var p=owner();if(p instanceof ServerPlayer sp)return sp;return interlocutor!=null&&level().getPlayerByUUID(interlocutor) instanceof ServerPlayer sp?sp:null;}
 public boolean ally(Entity e){
  if(e==this||e.getUUID().equals(follower))return true;
  if(e instanceof HumNpc a){if(follower!=null&&follower.equals(a.follower))return true;if(characterId.equals("hamazura")&&a.characterId.equals("takitsubo"))return true;if(characterId.equals("takitsubo")&&a.characterId.equals("hamazura"))return true;}
  if(e instanceof FlameFamiliar f&&getUUID().equals(f.creator))return true;
  return super.isAlliedTo(e);
 }
 @Override public boolean isAlliedTo(Entity e){return ally(e);}
 public boolean validTarget(LivingEntity t){return t!=null&&t.level()==level()&&t.isAlive()&&!ally(t)&&!OthinusRewrite.hidden(t)&&!(t instanceof Player p&&(p.isCreative()||p.isSpectator()));}
 public void setMode(int mode,Player p){entityData.set(MODE,mode);if(p!=null)follower=p.getUUID();getNavigation().stop();setTarget(null);}
 @Override public void setTarget(LivingEntity target){
  if(target!=null&&target.getUUID().equals(unreachableTarget)&&unreachableUntil>level().getGameTime()
          &&!hasLineOfSight(target))return;
  super.setTarget(target);
 }
 @Override protected InteractionResult mobInteract(Player p,InteractionHand hand){
  if(hand!=InteractionHand.MAIN_HAND)return InteractionResult.PASS;
  if(!level().isClientSide&&p instanceof ServerPlayer sp){
   var held=p.getItemInHand(hand);
   if(characterId.equals("takitsubo")&&held.is(BioHum.CRYSTAL.get())){if(crystals==Integer.MAX_VALUE)return InteractionResult.CONSUME;crystals++;if(!p.isCreative())held.shrink(1);tell(sp,"已补充体晶。剩余："+crystals);}
   else if(acceptMedium(sp,held))return InteractionResult.CONSUME;
   else net.neoforged.neoforge.network.PacketDistributor.sendToPlayer(sp,new Payloads.Open(getId()));
  }
  return InteractionResult.sidedSuccess(level().isClientSide);
 }
 private boolean acceptMedium(ServerPlayer player,ItemStack given){
  Item medium=switch(characterId){
   case "mikoto" -> Items.IRON_NUGGET;
   case "mugino" -> Items.AMETHYST_SHARD;
   case "aureolus" -> Items.GOLD_NUGGET;
   case "stiyl" -> BioHum.RUNE_CARD.get();
   case "fiamma","lidvia" -> BioHum.RELIC.get();
   default -> null;
  };
  if(medium==null||!given.is(medium)||follower!=null&&!follower.equals(player.getUUID()))return false;
  var held=getOffhandItem();
  int count=held.is(medium)?held.getCount():0;
  int moved=Math.min(given.getCount(),given.getMaxStackSize()-count);
  if(moved>0){
   setItemSlot(EquipmentSlot.OFFHAND,new ItemStack(medium,count+moved));
   if(!player.isCreative())given.shrink(moved);
   tell(player,"已补充施放媒介。");
  }
  return true;
 }
 public boolean request(ServerPlayer p,String action){
  if(!isAlive()||p.level()!=level()||distanceToSqr(p)>36)return false;
  if(action.equals("talk")&&OthinusRewrite.interact(this,p))return true;
  interlocutor=p.getUUID();
  if(!action.equals("talk")&&follower!=null&&!follower.equals(p.getUUID())){tell(p,"已有同行者，请由原同行者解除跟随。");return false;}
  switch(action){
   case "talk" -> {expression(1);
    if(suspended())tell(p,chamberInstalled()
      ?"这里没有供人出入的门。需要离开的话，使用坐标传送。 §8[游戏对白]"
      :"维持系统在运转。你想问哪一项数据？ §8[游戏对白]");
    else if(characterId.equals("othinus")&&phaseId().equals("nt09_magic_god"))
     tell(p,"你想回到哪里？先让这片世界看清你的答案。");
    else if(characterId.equals("othinus")&&phaseId().equals("nt10_fading"))
     tell(p,"那把枪已经碎了。要解除魔神之力，还得去找那只眼睛。");
    else tell(p,profile().dialogue().get(Math.floorMod(shots++,profile().dialogue().size()))+" §8[原创互动对白 · "+phaseId()+"]");
    return true;}
   case "follow" -> {if(suspended()){tell(p,"维生舱固定，无法同行。");return false;}setMode(1,p);expression(1);tell(p,"开始同行。");return true;}
   case "stay" -> {setMode(2,p);tell(p,"留在这里。");return true;}
   case "defend" -> {if(suspended()){tell(p,"固定舱位只启动就地防护。");return false;}setMode(3,p);tell(p,"进入防卫同行模式。");return true;}
   case "release" -> {setMode(0,null);follower=null;expression(0);tell(p,"已解除同行。");return true;}
   case "character" -> {
    var signature=NpcMove.of(this);
    if(signature==null)return false;
    signature.interact(this,p);
    return true;
   }
   default -> {
    String id=Skills.actionId(profile(),action);
    var move=Skills.get(id);
    if(move==null||!skills().contains(id))return false;
    boolean ok=Abilities.cast(this,id,Abilities.aimed(this,p,move),CombatSkills.point(p,move.range()),p);
    tell(p,ok?move.label()+" 已发动。":"条件不满足、没有有效目标，或仍在冷却。");
    return ok;
   }
  }
 }
 public void tell(ServerPlayer p,String s){p.sendSystemMessage(Component.literal("§b"+profile().name()+"§r："+s));}
 public boolean windup(String id,LivingEntity target,int ticks){
  if(!pending.isEmpty())return false;
  pending=id;pendingTarget=target==null?null:target.getUUID();pendingAt=level().getGameTime()+ticks;
  pendingPoint=null;pendingIssuer=null;pendingHealth=getHealth();entityData.set(CAST,id);return true;
 }
 void aimPending(Vec3 point,ServerPlayer player){pendingPoint=point;pendingIssuer=player==null?null:player.getUUID();}
 boolean windDisrupted(){return getHealth()<pendingHealth||hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN);}
 private void clearPending(){
  pending="";pendingTarget=null;pendingPoint=null;pendingIssuer=null;entityData.set(CAST,"");
 }
 public boolean interrupt(){
  if(pending.isEmpty()&&closeSwing==null)return false;
  var move=Skills.get(pending);
  Vec3 source=move==null?rightHand():CastMotion.world(this,move,CastMotion.aimed(this,move,signatureAge()),castYaw());
  Payloads.cue(this,technique(),Payloads.Cue.CANCEL,source,source,1);
  closeSwing=null;wirePulses=0;wireTarget=null;
  cancelTechnique();clearPending();return true;
 }
 public boolean controlled(){return controlledBy!=null&&controlUntil>level().getGameTime();}
 public void control(HumNpc caster,int ticks,int instruction){
  controlledBy=caster.getUUID();controlUntil=level().getGameTime()+ticks;controlInstruction=instruction;interrupt();getNavigation().stop();setTarget(null);
  if(instruction==1){Vec3 away=position().subtract(caster.position()).normalize().scale(7);getNavigation().moveTo(getX()+away.x,getY(),getZ()+away.z,1.1);}
  if(instruction==2&&caster.validTarget(caster.getTarget()))setTarget(caster.getTarget());
 }
 public Vec3 rightHand(){
  var move=Skills.get(technique());
  if(move!=null&&!suspended()&&CastMotion.style(move).anchor()==CastMotion.Anchor.RIGHT)
   return CastMotion.world(this,move,CastMotion.aimed(this,move,signatureAge()),castYaw());
  var f=Vec3.directionFromRotation(0,getYRot());var r=new Vec3(-f.z,0,f.x);
  return position().add(0,getBbHeight()*.60,0).add(f.scale(.42*phaseScale())).add(r.scale(.25*phaseScale()));
 }
 @Override public void tick(){
  if(suspended()){
   setNoGravity(true);setDeltaMovement(Vec3.ZERO);getNavigation().stop();
   if(!level().isClientSide&&chamberSeat!=null){
    var point=Vec3.atBottomCenterOf(chamberSeat).add(0,1,0);
    if(position().distanceToSqr(point)>1e-6)setPos(point);
   }
  }
  super.tick();if(!(level() instanceof ServerLevel level))return;long now=level.getGameTime();
  if(!wings().isEmpty()&&getPersistentData().getLong("BioHumWings")<=now)entityData.set(WINGS,"");
  if(characterId.equals("lidvia")&&now%10==0)CombatSkills.checkCross(this,level);
  if(getTarget()!=null&&!validTarget(getTarget()))setTarget(null);
  if(controlledBy!=null&&now>=controlUntil){controlledBy=null;setTarget(null);getNavigation().stop();}
  if(controlled()&&controlInstruction==0){getNavigation().stop();setTarget(null);}
  if(controlled()&&controlInstruction==2&&level() instanceof ServerLevel sl&&sl.getEntity(controlledBy) instanceof HumNpc caster&&caster.validTarget(caster.getTarget()))setTarget(caster.getTarget());
  if(characterId.equals("touma")&&guardUntil>now){
   var hand=rightHand();
   for(var bolt:level.getEntitiesOfClass(AbilityBolt.class,getBoundingBox().inflate(1),
       b->b.supernatural()&&b.getOwner()!=this)){
    if(bolt.position().distanceToSqr(hand)<.55*.55){
     bolt.discard();Payloads.cue(this,"imagine_breaker",Payloads.Cue.CANCEL,hand,hand,.8f);
    }
   }
  }
  var move=Skills.get(technique());
  if(move!=null){
   if((abortAge()>=8||abortAge()<0&&signatureAge()>=move.length())&&pending.isEmpty())clearSignature();
   else if(pendingTarget!=null&&level.getEntity(pendingTarget) instanceof LivingEntity target&&target.isAlive()){
    getLookControl().setLookAt(target,45,45);
    faceCast(target.getBoundingBox().getCenter());
   }
  }
  if(!pending.isEmpty()){
   if(pending.equals("wind_plasma")&&windDisrupted())interrupt();
   else if(now>=pendingAt){
    String id=pending;var t=pendingTarget==null?null:level.getEntity(pendingTarget);
    var point=pendingPoint;var issuer=pendingIssuer;clearPending();
    Abilities.finish(this,id,t instanceof LivingEntity l?l:null,point,issuer);
   }
  }
  if(wirePulses>0&&now%2==0){
   var subject=wireTarget==null?null:level.getEntity(wireTarget);
   if(subject instanceof LivingEntity living&&validTarget(living))CombatSkills.wire(this,living,8-wirePulses);
   if(--wirePulses==0)wireTarget=null;
  }
  CloseCombat.tick(this,level);
  if(characterId.equals("aleister")&&phaseId().equals("nt18_outside"))CharacterArts.tickAleister(this,level);
  if(characterId.equals("othinus")&&phaseId().equals("nt09_magic_god"))OthinusRewrite.autonomousDuel(this,level);
  if(characterId.equals("hamazura")&&tickCount%10==0){for(var t:level().getEntitiesOfClass(HumNpc.class,getBoundingBox().inflate(10),n->n.characterId.equals("takitsubo"))){var a=t.getLastHurtByMob();if(a!=null&&a.isAlive()&&t.getLastHurtByMobTimestamp()+100>t.tickCount&&validTarget(a))setTarget(a);}}
  if(mode()==3&&owner()!=null&&owner().getLastHurtByMob()!=null&&owner().getLastHurtByMobTimestamp()+100>owner().tickCount&&validTarget(owner().getLastHurtByMob()))setTarget(owner().getLastHurtByMob());
  var support=NpcMove.of(this);
  if(support!=null)support.defend(this,level);
  if(tickCount%100==0&&getTarget()==null)expression(0);
 }
 boolean hasFragileFamiliar(ServerLevel level){
  int live=0;
  for(var id:runes)if(level.getEntity(id) instanceof RuneAnchor r&&r.isAlive())live++;
  if(live!=3)return false;
  return !level.getEntitiesOfClass(FlameFamiliar.class,getBoundingBox().inflate(48),f->f.isAlive()
      &&getUUID().equals(f.creator)&&f.anchorsValid()).isEmpty();
 }
 public void sevenFlashes(LivingEntity t){wireTarget=t.getUUID();wirePulses=7;wireStarted=techniqueStarted();}
 @Override public boolean isInWall(){return !suspended()&&super.isInWall();}
 @Override public boolean isPushable(){return !suspended()&&super.isPushable();}
 @Override public boolean doHurtTarget(Entity e){if(suspended())return false;boolean hit=super.doHurtTarget(e);if(hit&&flameUntil>level().getGameTime())e.igniteForSeconds(4);return hit;}
 @Override public void addAdditionalSaveData(CompoundTag t){
  super.addAdditionalSaveData(t);t.putString("CharacterId",characterId);t.putString("PhaseId",phaseId());t.putInt("BioMode",mode());t.putInt("BioExpression",expression());if(follower!=null)t.putUUID("Follower",follower);t.putInt("Crystals",crystals);t.putInt("TalkIndex",shots);
  var byId=new CompoundTag();
  cooldowns.forEach((id,time)->{if(time>level().getGameTime())byId.putLong(id,time);});
  t.put("SkillReadyAt",byId);t.putLong("GuardUntil",guardUntil);t.putLong("FlameUntil",flameUntil);
  if(!wings().isEmpty())t.putString("ActiveWings",wings());
  if(wingsFolded())t.putLong("WingFoldUntil",entityData.get(WING_FOLD));
  if(chamberSeat!=null&&suspended())t.putLong("ChamberSeat",chamberSeat.asLong());
  if(controlledBy!=null){t.putUUID("ControlledBy",controlledBy);t.putLong("ControlUntil",controlUntil);t.putInt("ControlInstruction",controlInstruction);}
  t.putInt("RuneCount",runes.size());for(int i=0;i<runes.size();i++)t.putUUID("Rune"+i,runes.get(i));
 }
 @Override public void readAdditionalSaveData(CompoundTag t){
  super.readAdditionalSaveData(t);if(t.contains("CharacterId")&&!t.getString("CharacterId").equals(characterId))throw new IllegalArgumentException("Character identity mismatch");
  selectPhase(t.contains("PhaseId")?t.getString("PhaseId"):profile().phase());
  if(suspended()&&t.contains("ChamberSeat")){
   chamberSeat=net.minecraft.core.BlockPos.of(t.getLong("ChamberSeat"));entityData.set(CHAMBER,1);setNoGravity(true);
  }
  entityData.set(WING_FOLD,t.getLong("WingFoldUntil"));
  String wings=t.getString("ActiveWings");if(skills().contains(wings)&&getPersistentData().getLong("BioHumWings")>level().getGameTime())entityData.set(WINGS,wings);
  entityData.set(MODE,Math.max(0,Math.min(3,t.getInt("BioMode"))));expression(t.getInt("BioExpression"));follower=t.hasUUID("Follower")?t.getUUID("Follower"):null;crystals=t.contains("Crystals")?Math.max(0,t.getInt("Crystals")):3;shots=t.getInt("TalkIndex");
  cooldowns.clear();
  if(t.contains("SkillReadyAt")){
   var byId=t.getCompound("SkillReadyAt");
   for(String id:byId.getAllKeys())if(Skills.get(id)!=null&&Skills.get(id).actor().equals(characterId))setReady(id,byId.getLong(id));
  }else{
   var legacy=t.getLongArray("AbilityReadyAt");
   for(int i=0;i<Math.min(profile().abilities().size(),legacy.length);i++)setReady(i,legacy[i]);
  }
  guardUntil=t.getLong("GuardUntil");flameUntil=t.getLong("FlameUntil");controlledBy=t.hasUUID("ControlledBy")?t.getUUID("ControlledBy"):null;controlUntil=t.getLong("ControlUntil");controlInstruction=Math.max(0,Math.min(2,t.getInt("ControlInstruction")));runes.clear();for(int i=0;i<t.getInt("RuneCount");i++)if(t.hasUUID("Rune"+i))runes.add(t.getUUID("Rune"+i));
 }
 private final class FollowGoal extends Goal {
  private int repath;
  FollowGoal(){setFlags(EnumSet.of(Flag.MOVE));}
  @Override public boolean canUse(){return !suspended()&&(mode()==1||mode()==3)&&owner()!=null&&!controlled()
   &&!CombatSkills.divineSuppressed(HumNpc.this)&&technique().isEmpty()
   &&getTarget()==null&&distanceToSqr(owner())>4;}
  @Override public boolean canContinueToUse(){return canUse();}
  @Override public boolean requiresUpdateEveryTick(){return true;}
  @Override public void start(){repath=0;}
  @Override public void tick(){if(--repath<=0&&owner()!=null){repath=10;getNavigation().moveTo(owner(),1.1);}}
  @Override public void stop(){getNavigation().stop();}
 }
 private final class CombatGoal extends Goal {
  private int delay,repath;
  private final CombatTactics.Movement movement=new CombatTactics.Movement();
  CombatGoal(){setFlags(EnumSet.of(Flag.MOVE,Flag.LOOK));}
  @Override public boolean canUse(){
   var target=getTarget();
   return !CombatSkills.divineSuppressed(HumNpc.this)&&validTarget(target)
      &&(!target.getUUID().equals(unreachableTarget)||unreachableUntil<=level().getGameTime()||hasLineOfSight(target))
      &&(!controlled()||controlInstruction==2)
      &&(!suspended()||chamberInstalled()&&CharacterArts.threatenedBy(HumNpc.this,target));
  }
  @Override public boolean canContinueToUse(){return canUse();}
  @Override public boolean requiresUpdateEveryTick(){return true;}
  @Override public void start(){delay=0;repath=0;movement.reset();}
  @Override public void stop(){
   getNavigation().stop();
   if(retreatTo!=null){
    var path=getNavigation().createPath(retreatTo,0);
    if(path!=null&&path.canReach())getNavigation().moveTo(path,1);
    retreatTo=null;
   }
  }
  @Override public void tick(){
   var t=getTarget();
   if(!validTarget(t))return;
   getLookControl().setLookAt(t,35,30);
   var active=Skills.get(technique());
   if(active!=null&&active.id().startsWith("close_")&&active.id().endsWith("_flinch")
           &&signatureAge()>=4&&t instanceof HumNpc other&&CloseCombat.incoming(HumNpc.this,other))
    clearSignature();
   if(!technique().isEmpty()||!casting().isEmpty())return;
   if(t instanceof HumNpc other&&CloseCombat.incoming(HumNpc.this,other)
       &&CloseCombat.canReach(other,HumNpc.this,other.closeSwing.skill)
       &&CloseCombat.guard(HumNpc.this,t))return;
   if(--delay<=0){
    if(CombatTactics.choose(HumNpc.this,t)){delay=8;repath=0;return;}
    if(!suspended()&&CloseCombat.react(HumNpc.this,t)){delay=5;repath=0;return;}
    delay=4+(getId()&3);
   }
   movement.progress(HumNpc.this,t);
   if(--repath<=0){
    repath=11+(getId()&3);
    movement.move(HumNpc.this,t);
   }
  }
 }
}
