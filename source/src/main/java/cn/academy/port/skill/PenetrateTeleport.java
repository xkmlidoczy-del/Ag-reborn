/* AcademyCraft1.0.7 PTContext adaptation. GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.core.AbilityProgress;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import static cn.academy.port.skill.TeleporterProgressionRules.*;
/** Eye-origin .8-step barrier scan. Release teleports the player, including original unavailable quirk. */
public final class PenetrateTeleport {
 public static final String ID=PENETRATE;private PenetrateTeleport(){}
 public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}public static boolean start(ServerPlayer p,long nonce){if(cn.academy.port.AbilityConsumption.busy(p))return false;return TeleporterProgressionSupport.start(p,ID,nonce)!=null;}
 public static void tick(ServerPlayer p){TeleporterProgressionSupport.tick(p,ID);}
 public static Destination destination(Level world,Vec3 eye,Vec3 direction,double exp,double cp){return penetrate(TeleporterProgressionSupport.point(eye),TeleporterProgressionSupport.point(direction),exp,cp,(x,y,z)->clear(world,x,y,z));}
 private static boolean clear(Level world,int x,int y,int z){var feet=new BlockPos(x,y,z);return world.getBlockState(feet).getCollisionShape(world,feet).isEmpty()&&world.getBlockState(feet.above()).getCollisionShape(world,feet.above()).isEmpty();}
 public static boolean release(ServerPlayer p){return release(p,0);}public static boolean release(ServerPlayer p,long nonce){var h=TeleporterProgressionSupport.terminal(p,ID,nonce);if(h==null)return false;TeleporterProgressionSupport.COMMITTING.add(p.getUUID());try{Vec3 eye=p.getEyePosition(),dir=TeleporterProgressionSupport.direction(p);double range=Math.min(penetrateRange(h.state.exp(ID)),h.state.cp/penetrateConsumption(h.state.exp(ID)))+.8;if(!TeleporterProgressionSupport.loaded(p,eye,eye.add(dir.scale(range)))){TeleporterProgressionSupport.send(p,ID,h,"end",null,false,-1);return false;}var dest=destination(p.serverLevel(),eye,dir,h.state.exp(ID),h.state.cp);Vec3 at=TeleporterProgressionSupport.vector(dest.position());if(!TeleporterProgressionSupport.destinationSafe(p,at)){TeleporterProgressionSupport.send(p,ID,h,"end",null,false,-1);return false;}double distance=p.position().distanceTo(at);
  // Original !available calls terminate() but DOES NOT return. ContextManager only marks disposal.
  if(!consumeWithForce(h.state,ID,(float)(distance*penetrateConsumption(h.exp)),penetrateOverload(h.exp),p.getAbilities().instabuild))return false;h.state.addExperience(ID,penetrateExperience(distance));h.state.setCooldown(ID,penetrateCooldown(h.exp));TeleporterProgressionSupport.teleport(p,at,true);TeleporterProgressionSupport.send(p,ID,h,"end",at,true,-1);TeleporterProgressionSupport.save(p);return true;
 }finally{TeleporterProgressionSupport.COMMITTING.remove(p.getUUID());}}
 public static void abort(ServerPlayer p){abort(p,0);}public static boolean abort(ServerPlayer p,long nonce){return TeleporterProgressionSupport.abort(p,ID,nonce);}public static void remove(ServerPlayer p){TeleporterProgressionSupport.remove(p,ID);}public static boolean active(ServerPlayer p){return TeleporterProgressionSupport.active(p,ID);}public static boolean canUse(AbilityProgress s){return TeleporterProgressionSupport.usable(s,ID);}public static void clear(){TeleporterProgressionSupport.clear(ID);}
}
