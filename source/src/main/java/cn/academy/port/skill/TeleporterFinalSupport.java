/* AcademyCraft classic1.0.7 context/lifecycle adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.*;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;
/** Separate final-skill lifecycle maps keep progression support frozen and merge additions narrow. */
final class TeleporterFinalSupport {
 static final Map<String,Map<UUID,Hold>> HOLDS=new HashMap<>();static final Map<String,Map<UUID,Long>> INPUTS=new HashMap<>();static final Set<UUID> COMMITTING=new HashSet<>();static long nextToken;
 static{for(String id:List.of(TeleporterFinalRules.LOCATION,TeleporterFinalRules.SHIFT,TeleporterFinalRules.FLASH)){HOLDS.put(id,new HashMap<>());INPUTS.put(id,new HashMap<>());}}private TeleporterFinalSupport(){}
 static final class Hold{final ServerPlayer owner;final ServerLevel world;final AbilityProgress state;final UUID uuid;final int entity;final double exp;final long token,nonce;final Set<ServerPlayer> audience;long lastTick;int ticks;double overloadKeep;long lastPerform;Hold(ServerPlayer p,String id,long nonce){owner=p;world=p.serverLevel();state=AbilityStorage.get(p);uuid=p.getUUID();entity=p.getId();exp=state.exp(id);token=++nextToken;this.nonce=nonce;lastTick=world.getGameTime()-1;var viewers=new LinkedHashSet<ServerPlayer>();for(var v:world.players())if(v.distanceToSqr(p)<=25*25)viewers.add(v);viewers.add(p);audience=Set.copyOf(viewers);}}
 static boolean server(ServerPlayer p){return TeleporterProgressionSupport.server(p);}static boolean ready(ServerPlayer p){return TeleporterProgressionSupport.ready(p);}static boolean usable(AbilityProgress s,String id){return TeleporterProgressionSupport.usable(s,id);}
 static boolean valid(ServerPlayer p,Hold h,String id){return ready(p)&&p.serverLevel()==h.world&&p.getId()==h.entity&&p.getUUID().equals(h.uuid)&&AbilityStorage.get(p)==h.state&&usable(h.state,id)&&p.serverLevel().getGameTime()>=h.lastTick;}
 static Hold current(ServerPlayer p,String id){return p==null?null:HOLDS.get(id).get(p.getUUID());}
 static Hold start(ServerPlayer p,String id,long nonce){if(!ready(p)||nonce<0||COMMITTING.contains(p.getUUID())||nonce>0&&nonce<=INPUTS.get(id).getOrDefault(p.getUUID(),0L))return null;var old=current(p,id);if(old!=null){if(valid(p,old,id))return null;dispose(p,id);}if(!usable(AbilityStorage.get(p),id))return null;var h=new Hold(p,id,nonce);HOLDS.get(id).put(p.getUUID(),h);if(nonce>0)INPUTS.get(id).put(p.getUUID(),nonce);return h;}
 static Hold terminal(ServerPlayer p,String id,long nonce){if(!server(p)||COMMITTING.contains(p.getUUID()))return null;var h=current(p,id);if(h==null||nonce>0&&h.nonce!=nonce)return null;if(!valid(p,h,id)){dispose(p,id);return null;}HOLDS.get(id).remove(p.getUUID());return h;}
 static Hold tick(ServerPlayer p,String id){if(!server(p))return null;var h=current(p,id);if(h==null)return null;if(!valid(p,h,id)){dispose(p,id);return null;}long now=h.world.getGameTime();if(now<=h.lastTick)return null;h.lastTick=now;return h;}
 static void send(ServerPlayer p,String id,Hold h,String phase,Vec3 at,boolean success){var t=tag(id,h,phase);t.putBoolean("success",success);if(id.equals(TeleporterFinalRules.FLASH)&&phase.equals("perform"))t.putLong("hop",h.lastPerform);if(at!=null){t.putDouble("x",at.x);t.putDouble("y",at.y);t.putDouble("z",at.z);}for(var v:h.audience)if(!v.hasDisconnected()&&v.serverLevel()==h.world)PacketDistributor.sendToPlayer(v,new AcademyNetwork.ClientData(t));}
 static CompoundTag tag(String id,Hold h,String phase){var t=new CompoundTag();t.putString("kind",id+"_"+phase);t.putInt("entity",h.entity);t.putUUID("entity_uuid",h.uuid);t.putLong("token",h.token);t.putLong("input",h.nonce);t.putDouble("exp",h.exp);t.putInt("ticks",h.ticks);return t;}
 static void dispose(ServerPlayer p,String id){if(!server(p))return;var h=HOLDS.get(id).remove(p.getUUID());if(h!=null){if(id.equals(TeleporterFinalRules.FLASH)){h.state.setCooldown(id,TeleporterFinalRules.flashCooldown(h.exp));save(p);}send(p,id,h,"end",null,false);}}
 static boolean abort(ServerPlayer p,String id,long nonce){var h=current(p,id);if(!server(p)||h==null||nonce>0&&nonce!=h.nonce)return false;dispose(p,id);return true;}
 static void remove(ServerPlayer p,String id){dispose(p,id);if(server(p))INPUTS.get(id).remove(p.getUUID());}static void clear(String id){HOLDS.get(id).clear();INPUTS.get(id).clear();}static boolean active(ServerPlayer p,String id){return current(p,id)!=null;}
 static void save(ServerPlayer p){TeleporterProgressionSupport.save(p);}static boolean loaded(ServerPlayer p,Vec3 a,Vec3 b){return TeleporterProgressionSupport.loaded(p,a,b);}static boolean destinationSafe(ServerPlayer p,Vec3 v){return TeleporterProgressionSupport.destinationSafe(p,v);}static Vec3 direction(Entity p){return TeleporterProgressionSupport.direction(p);}static Vec3 vec(TeleporterFinalRules.Point p){return new Vec3(p.x(),p.y(),p.z());}static TeleporterFinalRules.Point point(Vec3 p){return new TeleporterFinalRules.Point(p.x,p.y,p.z);}
 static void count(ServerPlayer p,boolean barrier){var d=p.getPersistentData();int n=d.getInt("ac_tpcount");d.putInt("ac_tpcount",n==Integer.MAX_VALUE?n:n+1);if(barrier){d.putBoolean("ac_teleporter_ignore_barrier",true);cn.academy.port.achievements.ClassicAchievements.trigger(p,"teleporter.ignore_barrier");}if(d.getInt("ac_tpcount")>=400){d.putBoolean("ac_teleporter_mastery",true);cn.academy.port.achievements.ClassicAchievements.trigger(p,"teleporter.mastery");}}
 static long nonce(String s){if(s==null||s.length()<1||s.length()>19||s.charAt(0)=='0')return 0;try{long n=Long.parseLong(s);return n>0&&s.equals(Long.toString(n))?n:0;}catch(NumberFormatException ignored){return 0;}}
}
