/* Copyright (c) Lambda Innovation,2013-2016. AcademyCraft STContext adaptation.
 * GPLv3; see NOTICE. LambdaLib strict line-box is MIT. */
package cn.academy.port.skill;
import cn.academy.port.*;
import cn.academy.port.api.SkillBlockDestroyEvent;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.core.*;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.port.skill.TeleporterFinalRules.*;
/** Server determines placement and line targets. Client sends no coordinates, item, damage or target. */
public final class ShiftTeleport {
 public static final String ID=SHIFT;private ShiftTeleport(){}
 public record Trace(BlockPos position,Direction face,Vec3 hit){}
 public static Trace trace(Entity p,double exp){Vec3 eye=p.getEyePosition(),dir=TeleporterFinalSupport.direction(p),end=eye.add(dir.scale(shiftRange(exp)));var h=ClassicRaytrace.perform(p,eye,end,ClipContext.Fluid.NONE,e->false);if(h instanceof BlockHitResult b&&h.getType()==HitResult.Type.BLOCK){int[] a=shiftBlock(b.getBlockPos().getX(),b.getBlockPos().getY(),b.getBlockPos().getZ(),b.getDirection().get3DDataValue());return new Trace(new BlockPos(a[0],a[1],a[2]),b.getDirection(),h.getLocation());}int[] a=shiftFallback(TeleporterFinalSupport.point(eye),TeleporterFinalSupport.point(dir),exp);return new Trace(new BlockPos(a[0],a[1],a[2]),Direction.DOWN,end);}
 public static List<Entity> targets(Entity p,double exp){BlockPos dest=trace(p,exp).position;Vec3 a=p.position(),b=Vec3.atCenterOf(dest);return p.level().getEntities(p,new AABB(a,b),e->{if(!(e instanceof LivingEntity||e instanceof EnderDragonPart))return false;float hw=e.getBbWidth()/2;return lineBox(new Point(e.getX()-hw,e.getY(),e.getZ()-hw),new Point(e.getX()+hw,e.getY()+e.getBbHeight(),e.getZ()+hw),TeleporterFinalSupport.point(a),TeleporterFinalSupport.point(b));});}
 public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}public static boolean start(ServerPlayer p,long nonce){if(cn.academy.port.AbilityConsumption.busy(p))return false;if(!TeleporterFinalSupport.ready(p)||!(p.getMainHandItem().getItem() instanceof BlockItem))return false;var h=TeleporterFinalSupport.start(p,ID,nonce);if(h==null)return false;TeleporterFinalSupport.send(p,ID,h,"start",null,true);return true;}
 public static void tick(ServerPlayer p){var h=TeleporterFinalSupport.tick(p,ID);if(h!=null)h.ticks++;}
 public static boolean release(ServerPlayer p){return release(p,0);}public static boolean release(ServerPlayer p,long nonce){var h=TeleporterFinalSupport.terminal(p,ID,nonce);if(h==null)return false;TeleporterFinalSupport.COMMITTING.add(p.getUUID());try{var stack=p.getMainHandItem();boolean itemValid=!stack.isEmpty()&&stack.getItem() instanceof BlockItem;Trace trace=trace(p,h.exp);Vec3 at=Vec3.atCenterOf(trace.position);
  // Original c_end(attacked) fires as soon as a block item exists, even if placement/CP later fails.
  TeleporterFinalSupport.send(p,ID,h,"end",at,itemValid);if(!itemValid||!TeleporterFinalSupport.loaded(p,p.getEyePosition(),at)||!TeleporterFinalSupport.destinationSafe(p,at))return false;
  BlockItem item=(BlockItem)stack.getItem();var world=p.serverLevel();var pos=trace.position;var hit=new BlockHitResult(trace.hit,trace.face,pos,false);var placement=new ExactPlacement(p,stack,hit,pos);var previousState=world.getBlockState(pos);int previousCount=stack.getCount();var state=item.getBlock().getStateForPlacement(placement);if(state==null||!world.getBlockState(pos).canBeReplaced(placement)||!state.canSurvive(world,pos)||!AcademyConfig.contextTerrain(world,"teleporter",ID)||!world.mayInteract(p,pos)||!p.mayUseItemAt(pos,trace.face,stack)||NeoForge.EVENT_BUS.post(new SkillBlockDestroyEvent(p,"teleporter."+ID,pos)).isCanceled()||!h.state.consumeSkill(ID,shiftConsumption(h.exp),shiftOverload(h.exp),p.getAbilities().instabuild,()->p.getMainHandItem()==stack&&!stack.isEmpty()&&stack.getCount()==previousCount&&world.getBlockState(pos).equals(previousState)&&AcademyConfig.contextTerrain(world,"teleporter",ID)&&world.mayInteract(p,pos)&&p.mayUseItemAt(pos,trace.face,stack)))return false;
  int before=stack.getCount();item.place(placement);if(!p.getAbilities().instabuild&&stack.getCount()>=before)stack.shrink(1);
  // Source re-traces AFTER attempted placement before selecting line targets.
  List<Entity> targets=targets(p,h.exp);for(var target:targets){double damage=shiftDamage(h.exp);int tier=ThreateningTeleport.criticalTier(h.state,()->world.random.nextFloat());if(tier>=0){damage=cn.academy.port.core.ClassicPassiveSkills.criticalDamage(damage,tier);cn.academy.port.achievements.ClassicAchievements.trigger(p,"teleporter.critical_attack");ThreateningTeleport.recordCritical(h.state,tier);p.getPersistentData().putBoolean("ac_teleporter_critical_attack",true);p.sendSystemMessage(Component.translatable("ac.ability.teleporter.crithit",Float.valueOf((float)ThreateningTeleport.criticalRate(tier))));NeoForge.EVENT_BUS.post(new FleshRipping.CriticalHitEvent(p,target,tier));var t=TeleporterFinalSupport.tag(ID,h,"critical");t.putInt("target",target.getId());t.putUUID("target_uuid",target.getUUID());t.putInt("critical_tier",tier);for(var v:h.audience)if(!v.hasDisconnected()&&v.serverLevel()==world)PacketDistributor.sendToPlayer(v,new AcademyNetwork.ClientData(t));}AbilityDamage.attack(p,"teleporter."+ID,target,damage,false);}
  // Original only emits this sound and learns after the placement guard and consume pass.
  TeleporterFinalSupport.send(p,ID,h,"placed",at,true);h.state.addExperience(ID,shiftExperience(targets.size()));h.state.setCooldown(ID,shiftCooldown(h.exp));TeleporterFinalSupport.save(p);return true;
 }finally{TeleporterFinalSupport.COMMITTING.remove(p.getUUID());}}
 /** Prevent modern context from moving the already server-adjusted legacy block position twice. */
 private static final class ExactPlacement extends BlockPlaceContext {final BlockPos exact;ExactPlacement(ServerPlayer p,ItemStack s,BlockHitResult h,BlockPos exact){super(p,InteractionHand.MAIN_HAND,s,h);this.exact=exact;}@Override public BlockPos getClickedPos(){return exact==null?super.getClickedPos():exact;}@Override public boolean canPlace(){return getLevel().getBlockState(getClickedPos()).canBeReplaced(this);}@Override public boolean replacingClickedOnBlock(){return true;}}
 public static void abort(ServerPlayer p){abort(p,0);}public static boolean abort(ServerPlayer p,long nonce){return TeleporterFinalSupport.abort(p,ID,nonce);}public static void remove(ServerPlayer p){TeleporterFinalSupport.remove(p,ID);}public static boolean active(ServerPlayer p){return TeleporterFinalSupport.active(p,ID);}public static boolean canUse(AbilityProgress s){return TeleporterFinalSupport.usable(s,ID);}public static void clear(){TeleporterFinalSupport.clear(ID);}
}
