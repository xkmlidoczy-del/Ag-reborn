/* AcademyCraft 1.0.7 teleport context/lifecycle adapter. GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.*;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
import static cn.academy.port.skill.TeleporterProgressionRules.*;
final class TeleporterProgressionSupport {
 private TeleporterProgressionSupport(){}
 static final Map<String,Map<UUID,Hold>> HOLDS=new HashMap<>();static final Map<String,Map<UUID,Long>> INPUTS=new HashMap<>();static final Set<UUID> COMMITTING=new HashSet<>();static long nextToken;
 static{for(String id:List.of(PENETRATE,MARK,FLESH)){HOLDS.put(id,new HashMap<>());INPUTS.put(id,new HashMap<>());}}
 static final class Hold{final ServerLevel world;final AbilityProgress state;final UUID uuid;final int entity;final double exp;final long token,nonce;final Set<ServerPlayer> audience;long lastTick;int ticks;HitResult target;Hold(ServerPlayer p,String id,long nonce){world=p.serverLevel();state=AbilityStorage.get(p);uuid=p.getUUID();entity=p.getId();exp=state.exp(id);token=++nextToken;this.nonce=nonce;lastTick=world.getGameTime()-1;var viewers=new LinkedHashSet<ServerPlayer>();for(var v:world.players())if(v.distanceToSqr(p)<=25*25)viewers.add(v);viewers.add(p);audience=Set.copyOf(viewers);}}
 static boolean server(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
 static boolean ready(ServerPlayer p){if(!server(p)||!p.isAlive()||p.isRemoved()||p.isSpectator()||!finite(p.position())||!finite(p.getEyePosition())||!Float.isFinite(p.getXRot())||!Float.isFinite(p.getYRot())||!Float.isFinite(p.getYHeadRot()))return false;var s=AbilityStorage.get(p);return s.level>=0&&s.level<=5&&Double.isFinite(s.cp)&&s.cp>=0&&Double.isFinite(s.overload)&&s.overload>=0&&Double.isFinite(s.extraCp)&&Double.isFinite(s.extraOverload);}
 static boolean usable(AbilityProgress s,String id){return s!=null&&s.category.equals("teleporter")&&s.level<=5&&s.canUse(id)&&SkillCatalog.find(s.category,id).filter(k->k.controllable()&&s.level>=k.level()).isPresent();}
 static boolean valid(ServerPlayer p,Hold h,String id){return ready(p)&&p.serverLevel()==h.world&&p.getId()==h.entity&&p.getUUID().equals(h.uuid)&&AbilityStorage.get(p)==h.state&&usable(h.state,id)&&p.serverLevel().getGameTime()>=h.lastTick;}
 static Hold start(ServerPlayer p,String id,long nonce){if(!ready(p)||nonce<0||COMMITTING.contains(p.getUUID())||nonce>0&&nonce<=INPUTS.get(id).getOrDefault(p.getUUID(),0L))return null;var old=HOLDS.get(id).get(p.getUUID());if(old!=null){if(valid(p,old,id))return null;dispose(p,id);}if(!usable(AbilityStorage.get(p),id))return null;var h=new Hold(p,id,nonce);HOLDS.get(id).put(p.getUUID(),h);if(nonce>0)INPUTS.get(id).put(p.getUUID(),nonce);send(p,id,h,"start",null,false,-1);return h;}
 static Hold terminal(ServerPlayer p,String id,long nonce){if(!server(p)||COMMITTING.contains(p.getUUID()))return null;var h=HOLDS.get(id).get(p.getUUID());if(h==null||nonce>0&&h.nonce!=nonce)return null;if(!valid(p,h,id)){dispose(p,id);return null;}HOLDS.get(id).remove(p.getUUID());return h;}
 static boolean abort(ServerPlayer p,String id,long nonce){var h=terminal(p,id,nonce);if(h==null)return false;send(p,id,h,"end",null,false,-1);return true;}
 static void dispose(ServerPlayer p,String id){if(!server(p))return;var h=HOLDS.get(id).remove(p.getUUID());if(h!=null)send(p,id,h,"end",null,false,-1);}
 static void remove(ServerPlayer p,String id){dispose(p,id);if(server(p))INPUTS.get(id).remove(p.getUUID());}
 static Hold tick(ServerPlayer p,String id){if(!server(p))return null;var h=HOLDS.get(id).get(p.getUUID());if(h==null)return null;if(!valid(p,h,id)){dispose(p,id);return null;}long now=h.world.getGameTime();if(now<=h.lastTick)return null;h.lastTick=now;h.ticks++;return h;}
 static void send(ServerPlayer p,String id,Hold h,String phase,Vec3 at,boolean success,int critical){var t=new CompoundTag();t.putString("kind",id+"_"+phase);t.putInt("entity",h.entity);t.putUUID("entity_uuid",h.uuid);t.putLong("token",h.token);t.putLong("input",h.nonce);t.putDouble("exp",h.exp);t.putInt("ticks",h.ticks);t.putBoolean("success",success);t.putInt("critical_tier",critical);if(at!=null){t.putDouble("x",at.x);t.putDouble("y",at.y);t.putDouble("z",at.z);}if(h.target instanceof EntityHitResult e){t.putInt("target",e.getEntity().getId());t.putUUID("target_uuid",e.getEntity().getUUID());}for(var v:h.audience)if(!v.hasDisconnected()&&v.serverLevel()==h.world)PacketDistributor.sendToPlayer(v,new AcademyNetwork.ClientData(t));}
 static boolean loaded(ServerPlayer p,Vec3 from,Vec3 to){if(!finite(from)||!finite(to)||from.distanceToSqr(to)>70*70)return false;var b=new AABB(from,to).inflate(1);for(int x=((int)Math.floor(b.minX))>>4;x<=((int)Math.floor(b.maxX))>>4;x++)for(int z=((int)Math.floor(b.minZ))>>4;z<=((int)Math.floor(b.maxZ))>>4;z++)if(!p.serverLevel().hasChunk(x,z))return false;return true;}
 static boolean destinationSafe(ServerPlayer p,Vec3 at){var pos=BlockPos.containing(at);return finite(at)&&p.serverLevel().hasChunkAt(pos)&&p.serverLevel().getWorldBorder().isWithinBounds(pos)&&at.y>=p.serverLevel().getMinBuildHeight()&&at.y<p.serverLevel().getMaxBuildHeight();}
 static void teleport(ServerPlayer p,Vec3 at,boolean penetrate){p.stopRiding();p.teleportTo(at.x,at.y,at.z);p.fallDistance=0;var data=p.getPersistentData();int count=data.getInt("ac_tpcount");data.putInt("ac_tpcount",count==Integer.MAX_VALUE?count:count+1);if(penetrate){data.putBoolean("ac_teleporter_ignore_barrier",true);cn.academy.port.achievements.ClassicAchievements.trigger(p,"teleporter.ignore_barrier");}if(data.getInt("ac_tpcount")>=400){data.putBoolean("ac_teleporter_mastery",true);cn.academy.port.achievements.ClassicAchievements.trigger(p,"teleporter.mastery");}}
 static Vec3 direction(Entity p){return vector(TeleporterProgressionRules.direction(p instanceof LivingEntity l?l.getYHeadRot():p.getYRot(),p.getXRot()));}
 static Vec3 vector(Point p){return new Vec3(p.x(),p.y(),p.z());}static Point point(Vec3 p){return new Point(p.x,p.y,p.z);}static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
 static void save(ServerPlayer p){AbilityStorage.save(p);AcademyNetwork.sync(p);}
 static void clear(String id){HOLDS.get(id).clear();INPUTS.get(id).clear();}
 static boolean active(ServerPlayer p,String id){return p!=null&&HOLDS.get(id).containsKey(p.getUUID());}
}
