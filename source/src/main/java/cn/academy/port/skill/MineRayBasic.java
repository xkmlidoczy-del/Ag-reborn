/* AcademyCraft1.0.7 BasicMRContext adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.*;
import java.util.*;
/** Original continuous mining ray: no entity attack, first tick captures hardness, next ticks subtract it. */
public final class MineRayBasic {
    public static final String ID=MineRayBasicSession.ID;private static final Map<UUID,Hold> HOLDS=new HashMap<>();private static final Map<UUID,Long> INPUTS=new HashMap<>();private static long nextToken;
    private MineRayBasic(){}public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}
    public static boolean start(ServerPlayer p,long input){if(cn.academy.port.AbilityConsumption.busy(p))return false;if(!MeltdownerStarterSupport.serverThread(p)||!mayAct(p)||input<0||input>0&&input<=INPUTS.getOrDefault(p.getUUID(),0L))return false;var old=HOLDS.get(p.getUUID());if(old!=null){if(valid(p,old))return false;remove(p);}var session=MineRayBasicSession.begin(AbilityStorage.get(p),p.getAbilities().instabuild);if(session==null)return false;var h=new Hold(p.serverLevel(),session,++nextToken,input,MeltdownerStarterSupport.audience(p));HOLDS.put(p.getUUID(),h);if(input>0)INPUTS.put(p.getUUID(),input);send(p,h,"mine_ray_basic_start");if(session.ending())finish(p,h,false);return true;}
    public static void tick(ServerPlayer p){if(!MeltdownerStarterSupport.serverThread(p))return;var h=HOLDS.get(p.getUUID());if(h==null)return;if(!valid(p,h)){remove(p);return;}var s=h.session.state();if(!s.activated||!s.overloadFine||s.interfering){finish(p,h,false);return;}long now=p.serverLevel().getGameTime();if(now<=h.lastTick)return;h.lastTick=now;boolean end=h.session.tick(p.getAbilities().instabuild,new MineRayBasicSession.World(){
        public MineRayBasicSession.Cell trace(){Vec3 eye=p.getEyePosition();var hit=p.serverLevel().clip(new ClipContext(eye,eye.add(MeltdownerBeamSupport.direction(p).scale(MineRayBasicSession.RANGE)),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));if(hit.getType()!=HitResult.Type.BLOCK)return null;var at=hit.getBlockPos();return new MineRayBasicSession.Cell(at.getX(),at.getY(),at.getZ());}
        private BlockPos pos(MineRayBasicSession.Cell c){return new BlockPos(c.x(),c.y(),c.z());}
        public boolean denied(MineRayBasicSession.Cell c){return MeltdownerBeamSupport.sourceDenied(p,ID,pos(c));}
        public int harvestLevel(MineRayBasicSession.Cell target,MineRayBasicSession.Cell previous){return MeltdownerBeamSupport.harvestLevel(p,pos(target));}
        public float hardness(MineRayBasicSession.Cell c){return p.serverLevel().getBlockState(pos(c)).getDestroySpeed(p.serverLevel(),pos(c));}
        public void breakBlock(MineRayBasicSession.Cell c){MeltdownerBeamSupport.mineBreak(p,pos(c));}
        public void particles(MineRayBasicSession.Cell c){var tag=MeltdownerBeamSupport.packet(p,"mine_ray_basic_particles",h.token,h.input);tag.putInt("index",h.session.ticks());tag.putInt("x",c.x());tag.putInt("y",c.y());tag.putInt("z",c.z());MeltdownerStarterSupport.send(h.audience,tag);}
    });if(end)finish(p,h,false);}
    public static boolean release(ServerPlayer p){if(!MeltdownerStarterSupport.serverThread(p))return false;var h=HOLDS.get(p.getUUID());if(h==null)return false;if(!valid(p,h)){remove(p);return false;}finish(p,h,false);return true;}public static void abort(ServerPlayer p){release(p);}public static void remove(ServerPlayer p){if(!MeltdownerStarterSupport.serverThread(p))return;var h=HOLDS.get(p.getUUID());if(h!=null)finish(p,h,true);INPUTS.remove(p.getUUID());}
    public static boolean active(ServerPlayer p){return p!=null&&HOLDS.containsKey(p.getUUID());}public static int heldTicks(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?0:h.session.ticks();}public static MineRayBasicSession.Cell target(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?MineRayBasicSession.Cell.NONE:h.session.target();}public static void clear(){HOLDS.clear();INPUTS.clear();nextToken=0;}
    private static boolean mayAct(ServerPlayer p){return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&MineRayBasicSession.mayStart(AbilityStorage.get(p))&&MeltdownerStarterSupport.finiteAim(p)&&MeltdownerStarterSupport.finite(p.position());}
    private static boolean valid(ServerPlayer p,Hold h){var s=AbilityStorage.get(p);return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&h.world==p.serverLevel()&&h.session.state()==s&&h.session.active()&&s.category.equals("meltdowner")&&s.level>=3&&s.learned(ID)&&p.serverLevel().getGameTime()>=h.lastTick&&MeltdownerStarterSupport.finiteAim(p)&&MeltdownerStarterSupport.finite(p.position());}
    private static void finish(ServerPlayer p,Hold h,boolean discard){if(!HOLDS.remove(p.getUUID(),h))return;if(discard)h.session.discard();else h.session.finish();send(p,h,"mine_ray_basic_end");if(!discard){AbilityStorage.save(p);AcademyNetwork.sync(p);}}
    private static void send(ServerPlayer p,Hold h,String kind){MeltdownerStarterSupport.send(h.audience,MeltdownerBeamSupport.packet(p,kind,h.token,h.input));}
    private static final class Hold {final ServerLevel world;final MineRayBasicSession session;final long token,input;final Set<ServerPlayer> audience;long lastTick;Hold(ServerLevel world,MineRayBasicSession session,long token,long input,Set<ServerPlayer> audience){this.world=world;this.session=session;this.token=token;this.input=input;this.audience=audience;lastTick=world.getGameTime()-1;}}
}
