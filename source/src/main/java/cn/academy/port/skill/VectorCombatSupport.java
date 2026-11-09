/* AcademyCraft1.0.7 VecManip context seam, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.*;
import cn.academy.port.api.SkillBlockDestroyEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
final class VectorCombatSupport {
 private VectorCombatSupport(){}
 static boolean thread(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
 static boolean finite(Vec3 p){return p!=null&&Double.isFinite(p.x)&&Double.isFinite(p.y)&&Double.isFinite(p.z);}
 static boolean ready(ServerPlayer p){return thread(p)&&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&finite(p.position())&&finite(p.getEyePosition())&&finite(p.getDeltaMovement())&&Float.isFinite(p.getYHeadRot())&&Float.isFinite(p.getYRot())&&Float.isFinite(p.getXRot());}
 static boolean state(cn.academy.port.core.AbilityProgress s,String id,int level){return VectorCombatRules.eligible(s,id,level);}
 static boolean protectedPlayer(ServerPlayer p,Entity e){return e instanceof Player&&!AcademyConfig.ATTACK_PLAYERS.get()||e instanceof ServerPlayer other&&!other.canHarmPlayer(p);}
 static Set<ServerPlayer> audience(ServerPlayer p){var viewers=new LinkedHashSet<ServerPlayer>();for(var v:p.serverLevel().players())if(v.distanceToSqr(p)<=25*25)viewers.add(v);viewers.add(p);return Set.copyOf(viewers);}
 static CompoundTag packet(ServerPlayer p,String kind,long token,long input){var t=new CompoundTag();t.putString("kind",kind);t.putInt("entity",p.getId());t.putLong("token",token);t.putLong("input",input);position(t,p.position());Vec3 dir=ClassicRaytrace.direction(p);t.putDouble("dx",dir.x);t.putDouble("dy",dir.y);t.putDouble("dz",dir.z);t.putFloat("yaw",p.getYHeadRot());t.putFloat("pitch",p.getXRot());return t;}
 static void position(CompoundTag t,Vec3 at){t.putDouble("x",at.x);t.putDouble("y",at.y);t.putDouble("z",at.z);}
 static void send(Set<ServerPlayer> viewers,CompoundTag t){for(var v:viewers)if(!v.hasDisconnected())PacketDistributor.sendToPlayer(v,new AcademyNetwork.ClientData(t));}
 static void sync(ServerPlayer p){AbilityStorage.save(p);AcademyNetwork.sync(p);}
 static boolean loaded(ServerPlayer p,BlockPos pos){return p.serverLevel().hasChunkAt(pos)&&p.serverLevel().getWorldBorder().isWithinBounds(pos);}
 static boolean canBreak(ServerPlayer p,String id,BlockPos pos){var world=p.serverLevel();return loaded(p,pos)&&AcademyConfig.contextTerrain(world,"vecmanip",id)&&world.mayInteract(p,pos)&&!NeoForge.EVENT_BUS.post(new SkillBlockDestroyEvent(p,"vecmanip."+id,pos)).isCanceled()&&!NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,pos,world.getBlockState(pos),p)).isCanceled();}
}
