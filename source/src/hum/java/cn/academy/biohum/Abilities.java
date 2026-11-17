package cn.academy.biohum;

import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.projectile.*;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.damagesource.*;
import net.minecraft.world.effect.*;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.*;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import java.util.*;

public final class Abilities {
 public static final Map<String,String> LABELS=Map.ofEntries(
  Map.entry("imagine_breaker","幻想杀手"),Map.entry("punch","普通拳击"),Map.entry("analysis","术式解析"),Map.entry("spell_intercept","咏唱干扰"),Map.entry("electric_shock","电击"),Map.entry("railgun","超电磁炮"),Map.entry("magnetism","磁力牵引"),Map.entry("vector_impact","矢量冲击"),Map.entry("vector_guard","矢量反射"),Map.entry("mental_stop","心理掌握·停手"),Map.entry("mental_retreat","心理掌握·后撤"),Map.entry("mental_assist","心理掌握·协助"),Map.entry("teleport_self","空间移动"),Map.entry("teleport_carry","携物移动"),Map.entry("gunshot","持械射击"),Map.entry("cover","寻找掩护"),Map.entry("protect_takitsubo","保护泷壶"),Map.entry("aim_track","AIM追踪"),Map.entry("seven_flash","七闪"),Map.entry("yuisen","唯闪"),Map.entry("flame_sword","炎剑"),Map.entry("rune_summon","布置符文·猎杀魔女之王"),Map.entry("tactics","战术提示"),Map.entry("rose_spell","薔薇术式"),Map.entry("leyline_trap","地脉陷阱"));
 public static String label(String id){
  var skill=Skills.get(id);
  if(skill!=null)return skill.label();
  var signature=NpcMove.find(id);
  return signature==null?LABELS.getOrDefault(id,id):signature.label();
 }
 public static void particles(ServerLevel l,Vec3 p,ParticleOptions kind,int count){l.sendParticles(kind,p.x,p.y,p.z,count,.16,.16,.16,.025);}
 public static LivingEntity aimed(HumNpc a,ServerPlayer p){return aimed(a,p,Skills.get(a.skills().getFirst()));}
 public static LivingEntity aimed(HumNpc a,ServerPlayer p,Skills.Skill skill){
  if(skill.target()==Skills.Target.SELF||skill.target()==Skills.Target.POINT)return null;
  var eye=p.getEyePosition();var dir=p.getLookAngle();
  return p.level().getEntitiesOfClass(LivingEntity.class,a.getBoundingBox().inflate(skill.range()),t->t!=a&&t!=p
          &&t.isAlive()&&(skill.target()!=Skills.Target.ENEMY||a.validTarget(t))
          &&t.getBoundingBox().getCenter().subtract(eye).normalize().dot(dir)>.97
          &&(p.hasLineOfSight(t)||a.getTarget()==t&&(skill.action()==Skills.Action.PRECEDENCE||skill.id().equals("lotus_wand"))))
          .stream().min(Comparator.comparingDouble(p::distanceToSqr)).orElse(a.getTarget());
 }
 static boolean target(HumNpc a,LivingEntity t,double range){return t!=null&&t.isAlive()&&a.level()==t.level()&&a.distanceToSqr(t)<=range*range&&a.hasLineOfSight(t);}
 public static boolean cast(HumNpc a,int slot,LivingEntity t){
  return cast(a,Skills.legacy(a.profile(),slot),t,null,null);
 }
 public static boolean cast(HumNpc a,String id,LivingEntity t,Vec3 point,ServerPlayer issuer){
  var skill=Skills.get(id);
  if(!(a.level() instanceof ServerLevel s)||skill==null||!a.skills().contains(id)||!a.isAlive()||a.controlled()
          ||CombatSkills.divineSuppressed(a))return false;
  long now=s.getGameTime();
  var active=Skills.get(a.technique());
  if(a.readyAt(id)>now||!a.casting().isEmpty()||a.closeSwing!=null
          ||active!=null&&a.abortAge()<0&&a.signatureAge()<active.windup())return false;
  if(skill.action()==Skills.Action.CLOSE||id.equals("imagine_breaker"))
   return CloseCombat.begin(a,skill,id.equals("imagine_breaker")&&!CloseCombat.canReach(a,t,skill)?null:t);
  if(skill.action()!=Skills.Action.LEGACY)return CombatSkills.start(a,skill,t,point,issuer);
  var signature=NpcMove.find(id);
  if(signature!=null)return signature.start(a,t);
  boolean ok=false;int cooldown=60;
  switch(id){
   case "analysis" -> {if(target(a,t,24)){var p=a.audience();if(p!=null)a.tell(p,t.getName().getString()+"："+(t instanceof HumNpc n?(n.profile().esper()?"AIM能力者":"魔术或普通行动；需结合发动条件判断"):(t instanceof FlameFamiliar?"符文维持型术式":"暂时无法确定术式结构")));particles(s,t.getEyePosition(),ParticleTypes.ENCHANT,8);ok=true;}cooldown=40;}
   case "spell_intercept" -> {if(target(a,t,skill.range())&&t instanceof HumNpc n){var pending=Skills.get(n.casting());if(pending!=null&&pending.prop().startsWith("rite")){ok=n.interrupt();particles(s,n.getEyePosition(),ParticleTypes.ENCHANT,12);}}cooldown=80;}
   case "electric_shock" -> {if(target(a,t,skill.range())&&a.validTarget(t)){bolt(a,t,"electric",5,1.2,skill);ok=true;}cooldown=45;}
   case "magnetism" -> {for(var item:s.getEntitiesOfClass(ItemEntity.class,a.getBoundingBox().inflate(skill.range()),e->e.getItem().is(net.minecraft.world.item.Items.IRON_INGOT)||e.getItem().is(net.minecraft.world.item.Items.IRON_NUGGET)||e.getItem().is(net.minecraft.world.item.Items.IRON_SWORD)||e.getItem().is(net.minecraft.world.item.Items.GOLD_INGOT))){item.setDeltaMovement(a.position().add(0,.8,0).subtract(item.position()).normalize().scale(.55));ok=true;}cooldown=50;}
   case "vector_impact" -> {if(target(a,t,3)&&a.validTarget(t)){t.hurt(a.damageSources().mobAttack(a),7);var v=t.position().subtract(a.position()).normalize().scale(1.2);t.push(v.x,.3,v.z);particles(s,t.position().add(0,1,0),ParticleTypes.CRIT,15);ok=true;}cooldown=45;}
   case "vector_guard","choker_calculate" -> {a.guardUntil=now+(id.equals("choker_calculate")?skill.duration():80);particles(s,a.getEyePosition(),ParticleTypes.CRIT,8);ok=true;cooldown=skill.cooldown();}
   case "mental_stop","mental_retreat","mental_assist" -> {if(target(a,t,16)&&t instanceof HumNpc n&&n!=a&&!Set.of("mikoto","accelerator","touma","anna","othinus").contains(n.characterId)&&!n.controlled()){n.control(a,100,id.equals("mental_retreat")?1:id.equals("mental_assist")?2:0);particles(s,n.getEyePosition(),ParticleTypes.ENCHANT,12);ok=true;}cooldown=120;}
   case "teleport_self" -> {Vec3 dest=target(a,t,20)?t.position().add(Vec3.directionFromRotation(0,t.getYRot()).scale(-3)):a.position().add(Vec3.directionFromRotation(0,a.getYRot()).scale(8));ok=teleport(a,dest);cooldown=60;}
   case "teleport_carry" -> {Vec3 destination=a.position().add(Vec3.directionFromRotation(0,a.getYRot()).scale(4));if(target(a,t,4)&&t!=a&&!(t instanceof Player)&&!(t instanceof FlameFamiliar)&&t.getBbHeight()<=2.1&&t.getBbWidth()<=.8&&!t.isPassenger()&&!t.isVehicle()){ok=teleport(t,destination);}else if(t==null){var item=s.getEntitiesOfClass(ItemEntity.class,a.getBoundingBox().inflate(4),e->e.isAlive()&&a.distanceToSqr(e)<=16).stream().min(Comparator.comparingDouble(a::distanceToSqr)).orElse(null);if(item!=null)ok=teleport(item,destination);}cooldown=90;}
   case "cover" -> {if(target(a,t,24)){ok=cover(a,t);if(ok)a.coverUntil=now+60;}cooldown=50;}
   case "protect_takitsubo" -> {var weak=s.getEntitiesOfClass(HumNpc.class,a.getBoundingBox().inflate(16),n->n.characterId.equals("takitsubo")&&n.isAlive());if(!weak.isEmpty()){var n=weak.getFirst();a.getNavigation().moveTo(n,1.2);if(a.validTarget(n.getLastHurtByMob()))a.setTarget(n.getLastHurtByMob());ok=true;}cooldown=50;}
   case "aim_track" -> {HumNpc n=t instanceof HumNpc nn&&nn.profile().esper()?nn:s.getEntitiesOfClass(HumNpc.class,a.getBoundingBox().inflate(48),nn->nn!=a&&nn.profile().esper()&&nn.isAlive()).stream().min(Comparator.comparingDouble(a::distanceToSqr)).orElse(null);if(n!=null&&a.crystals>0&&a.getHealth()>4){a.crystals--;a.hurt(a.damageSources().generic(),1);n.addEffect(new MobEffectInstance(MobEffects.GLOWING,100));var p=a.audience();if(p!=null)a.tell(p,"AIM目标："+n.profile().name()+"，距离 "+Math.round(a.distanceTo(n))+" 格。体晶剩余 "+a.crystals);ok=true;}cooldown=120;}
   case "seven_flash" -> {if(target(a,t,12)&&a.validTarget(t)){a.sevenFlashes(t);ok=true;}cooldown=100;}
   case "yuisen" -> {if(target(a,t,6)&&a.validTarget(t))ok=a.windup(id,t,12);cooldown=160;}
   case "flame_sword" -> {a.flameUntil=now+160;a.setItemSlot(EquipmentSlot.MAINHAND,new net.minecraft.world.item.ItemStack(BioHum.FLAME_SWORD.get()));particles(s,a.rightHand(),ParticleTypes.FLAME,20);ok=true;cooldown=180;}
   case "rune_summon" -> {if(!a.getOffhandItem().is(BioHum.RUNE_CARD.get())||a.getOffhandItem().getCount()<3||a.runes.stream().anyMatch(u->s.getEntity(u) instanceof RuneAnchor r&&r.isAlive()))return false;ok=a.windup(id,t,30);cooldown=400;}
   case "tactics" -> {var p=issuer==null?a.audience():issuer;if(p!=null){a.tell(p,t==null?"先确认对方的发动条件，留出撤退路线。":t.getName().getString()+"："+(t instanceof HumNpc n&&n.characterId.equals("accelerator")?"避免直接施加他能够判断的矢量攻击。":t instanceof FlameFamiliar?"维持它的符文仍在；先找到符文。":"观察施放间隙，并利用掩体靠近。"));ok=true;}cooldown=40;}
   case "rose_spell" -> {if(target(a,t,28)&&a.validTarget(t))ok=a.windup(id,t,20);cooldown=100;}
   case "leyline_trap" -> {Vec3 p=t!=null&&target(a,t,20)?t.position().add(0,.05,0):a.position().add(a.getLookAngle().multiply(1,0,1).normalize().scale(5));BlockPos ground=BlockPos.containing(p).below();if(s.getBlockState(ground).isSolid()&&s.getBlockState(ground.above()).isAir()){var trap=BioHum.TRAP.get().create(s);trap.creator=a.getUUID();trap.setPos(p);s.addFreshEntity(trap);ok=true;}cooldown=140;}
  }
  if(ok){
   a.setReady(id,now+cooldown);a.expression(2);a.beginTechnique(id);
   if(skill.target()!=Skills.Target.SELF)a.faceCast(t==null?point:t.getBoundingBox().getCenter());
   if(a.casting().isEmpty()){
    a.resolved(id);
    cue(a,skill,t);
   }
   else a.aimPending(point,issuer);
  }
  return ok;
 }
 public static void finish(HumNpc a,String id,LivingEntity t,Vec3 point,UUID issuer){
  var skill=Skills.get(id);
  if(skill!=null&&skill.action()!=Skills.Action.LEGACY){
   CombatSkills.finish(a,skill,t,point,issuer);
   return;
  }
  var signature=NpcMove.find(id);
  if(signature!=null){
   if(!signature.finish(a,t))a.cancelTechnique();
   else {a.resolved(id);cue(a,skill,t);}
   return;
  }
  switch(id){
   case "rose_spell" -> {if(target(a,t,28)&&a.validTarget(t))bolt(a,t,"magic",8,1);}
   case "yuisen" -> {if(target(a,t,6)&&a.validTarget(t)){a.swing(net.minecraft.world.InteractionHand.MAIN_HAND);flash(a,t,12);}}
   case "rune_summon" -> {if(!summon(a,t)){a.clearSignature();return;}}
  }
  if(skill!=null){a.resolved(id);cue(a,skill,t);}
 }
 private static void cue(HumNpc npc,Skills.Skill skill,LivingEntity target){
  if(skill==null)return;
  Vec3 at=target==null?npc.position():target.getBoundingBox().getCenter();
  Payloads.cue(npc,skill.id(),Payloads.Cue.RELEASE,CastMotion.release(npc,skill,npc.castYaw()),at,1);
 }
 public static AbilityBolt bolt(HumNpc npc,LivingEntity target,String kind,float power,double speed){
  return bolt(npc,target,kind,power,speed,Skills.get(npc.technique()));
 }
 private static AbilityBolt bolt(HumNpc npc,LivingEntity target,String kind,float power,double speed,Skills.Skill skill){
  var bolt=BioHum.BOLT.get().create(npc.level());
  bolt.setOwner(npc);
  bolt.kind=kind;
  bolt.power=power;
  Vec3 hit=target.getBoundingBox().getCenter();
  Vec3 from=npc.position().add(0,npc.getBbHeight()*.65,0);
  if(skill!=null){
   float yaw=CastMotion.yaw(npc.position(),hit,npc.yBodyRot);
   from=CastMotion.release(npc,skill,yaw);
   bolt.skill(skill.id(),skill.range());
   bolt.castStarted(skill.id().equals(npc.technique())?npc.techniqueStarted():npc.level().getGameTime());
  }
  bolt.setPos(from);
  bolt.setDeltaMovement(hit.subtract(from).normalize().scale(speed));
  npc.level().addFreshEntity(bolt);
  return bolt;
 }
 public static void flash(HumNpc a,LivingEntity t,float power){if(!target(a,t,12)||!a.validTarget(t))return;Vec3 from=a.getEyePosition(),to=t.getBoundingBox().getCenter();for(int i=0;i<12;i++)particles((ServerLevel)a.level(),from.lerp(to,i/12.0),ParticleTypes.CRIT,1);t.invulnerableTime=0;t.hurt(a.damageSources().mobAttack(a),power);}
 static boolean safeDestination(Entity e,Vec3 dest){
  if(!(e.level() instanceof ServerLevel s)||!Double.isFinite(dest.x)||!Double.isFinite(dest.y)||!Double.isFinite(dest.z))return false;
  BlockPos p=BlockPos.containing(dest);if(!s.getWorldBorder().isWithinBounds(p)||!s.hasChunkAt(p)||dest.y<s.getMinBuildHeight()+1||dest.y>s.getMaxBuildHeight()-3)return false;
  var box=e.getBoundingBox().move(dest.subtract(e.position()));
  return s.noCollision(e,box)&&s.getBlockState(p.below()).isSolid()&&s.getFluidState(p).isEmpty();
 }
 public static boolean teleport(Entity e,Vec3 dest){
  return teleport(e,dest,true);
 }
 static boolean teleport(Entity e,Vec3 dest,boolean particles){
  if(!(e.level() instanceof ServerLevel s)||!safeDestination(e,dest))return false;
  if(particles)particles(s,e.position().add(0,.8,0),ParticleTypes.PORTAL,20);
  e.teleportTo(dest.x,dest.y,dest.z);e.setDeltaMovement(Vec3.ZERO);
  if(particles)particles(s,dest.add(0,.8,0),ParticleTypes.PORTAL,20);
  return true;
 }
 private static boolean cover(HumNpc a,LivingEntity t){var s=a.level();BlockPos base=a.blockPosition();for(int r=2;r<=6;r++)for(int dx=-r;dx<=r;dx++)for(int dz=-r;dz<=r;dz++){if(Math.abs(dx)!=r&&Math.abs(dz)!=r)continue;var p=base.offset(dx,0,dz);if(!s.getBlockState(p).isAir()||!s.getBlockState(p.above()).isAir()||!s.getBlockState(p.below()).isSolid())continue;var hit=s.clip(new ClipContext(t.getEyePosition(),Vec3.atBottomCenterOf(p).add(0,1.2,0),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,a));if(hit.getType()==HitResult.Type.BLOCK&&a.getNavigation().moveTo(p.getX()+.5,p.getY(),p.getZ()+.5,1.2))return true;}return false;}
 public static boolean summon(HumNpc a,LivingEntity target){
  if(!(a.level() instanceof ServerLevel s))return false;
  List<Vec3> spots=new ArrayList<>();for(int i=0;i<3;i++){double angle=i*Math.PI*2/3;Vec3 p=a.position().add(Math.cos(angle)*2,0,Math.sin(angle)*2);if(!s.hasChunkAt(BlockPos.containing(p))||!s.getBlockState(BlockPos.containing(p)).isAir()||!s.getBlockState(BlockPos.containing(p).below()).isSolid())return false;spots.add(p);}
  if(!a.getOffhandItem().is(BioHum.RUNE_CARD.get())||a.getOffhandItem().getCount()<spots.size())return false;
  a.getOffhandItem().shrink(spots.size());
  a.runes.clear();for(Vec3 p:spots){var r=BioHum.RUNE.get().create(s);r.creator=a.getUUID();r.expires=s.getGameTime()+600;r.setPos(p);s.addFreshEntity(r);a.runes.add(r.getUUID());Payloads.cue(a,"rune_summon",Payloads.Cue.FIELD,p,p,1,a.runes.size()-1,r.getUUID());}
  var f=BioHum.FLAME.get().create(s);f.creator=a.getUUID();f.anchors.addAll(a.runes);
  Vec3 forward=target!=null?target.position().subtract(a.position()).multiply(1,0,1).normalize()
         :Vec3.directionFromRotation(0,a.castYaw());
  Vec3 side=new Vec3(forward.z,0,-forward.x);
  Vec3 initial=a.position().add(forward.scale(2.4));
  for(Vec3 site:List.of(initial,initial.add(side.scale(1.7)),initial.add(side.scale(-1.7)),a.position().add(forward.scale(-2.4)))){
   if(safeDestination(f,site)){initial=site;break;}
  }
  f.setPos(initial);f.setYRot(CastMotion.yaw(initial,initial.add(forward),a.castYaw()));
  f.setTarget(a.validTarget(target)?target:null);s.addFreshEntity(f);a.setTarget(a.validTarget(target)?target:null);
  for(int i=0;i<spots.size();i++)Payloads.cue(a,"rune_summon",Payloads.Cue.BEAM,
      spots.get(i).add(0,.12,0),f.getBoundingBox().getCenter(),.52f,i,f.getUUID());
  return true;
 }
 private static final ThreadLocal<Boolean> REFLECTING=ThreadLocal.withInitial(()->false);
 public static void arrowImpact(net.neoforged.neoforge.event.entity.ProjectileImpactEvent e){
  if(e.isCanceled()||!(e.getProjectile() instanceof AbstractArrow arrow)
   ||!(e.getRayTraceResult() instanceof EntityHitResult hit)||!(hit.getEntity() instanceof HumNpc npc)
   ||!(npc.level() instanceof ServerLevel)||!CombatSkills.reflects(npc)||arrow.getOwner()==npc)return;
  var incoming=arrow.getDeltaMovement();var owner=arrow.getOwner();
  var from=hit.getLocation();
  var reverse=owner!=null&&owner.isAlive()?owner.getBoundingBox().getCenter().subtract(from).normalize():incoming.reverse().normalize();
  if(reverse.lengthSqr()<.001)reverse=incoming.reverse().normalize();
  e.setCanceled(true);
  arrow.setOwner(npc);arrow.setPos(from.add(reverse.scale(.08)));
  arrow.setDeltaMovement(reverse.scale(Math.max(.25,incoming.length())));
  Vec3 next=SkillTrajectory.surface((ServerLevel)npc.level(),from,from.add(arrow.getDeltaMovement())).position();
  Payloads.cue(npc,"vector_guard",Payloads.Cue.REFLECT,from,next,.8f,0,arrow.getUUID());
 }
 public static void incoming(LivingIncomingDamageEvent e){
  if(e.getSource().getEntity() instanceof LivingEntity attacker&&e.getSource().getDirectEntity()==attacker
          &&CombatSkills.divineSuppressed(attacker)){e.setCanceled(true);return;}
  if(OthinusRewrite.protect(e))return;
  if(CharacterArts.screenDamage(e))return;
  if(CombatSkills.protect(e))return;
  if(e.getEntity() instanceof HumNpc defender && defender.characterId.equals("kinuhata")
    && !defender.level().isClientSide && defender.guardUntil>defender.level().getGameTime()
    && e.getSource().getEntity()!=null
    && defender.getLookAngle().dot(e.getSource().getEntity().position().subtract(defender.position()).normalize())>-.15){
   e.setAmount(e.getAmount()*.40f);
   return;
  }
  if(REFLECTING.get()||!(e.getEntity() instanceof HumNpc a)||a.level().isClientSide||!CombatSkills.reflects(a))return;
  var src=e.getSource();var direct=src.getDirectEntity();
  if(src.is(RosterMove.FEINT))return;
  if(direct instanceof AbilityBolt b){if(b.reflectable())e.setCanceled(true);return;}
  if(direct instanceof LivingEntity attacker&&attacker!=a&&!src.is(net.minecraft.tags.DamageTypeTags.WITCH_RESISTANT_TO)){
   e.setCanceled(true);if(attacker instanceof HumNpc other&&other.characterId.equals("accelerator"))return;
   try{REFLECTING.set(true);attacker.hurt(a.damageSources().mobAttack(a),e.getAmount());Payloads.cue(a,"vector_guard",Payloads.Cue.REFLECT,a.getEyePosition(),attacker.getEyePosition(),1);}finally{REFLECTING.set(false);}
  }
 }
 public static void damaged(net.neoforged.neoforge.event.entity.living.LivingDamageEvent.Post event){
  if(!(event.getEntity() instanceof HumNpc npc)||!(npc.level() instanceof ServerLevel)
          ||event.getNewDamage()<=0||npc.suspended()||!npc.isAlive()||!npc.technique().isEmpty())return;
  if(CloseCombat.begin(npc,Skills.get("close_"+npc.characterId+"_flinch"),null)){
   var at=npc.getBoundingBox().getCenter();
   Payloads.cue(npc,npc.technique(),Payloads.Cue.IMPACT,at,at,.45f);
  }
 }
}
