/* AcademyCraft1.0.7 Meltdowner/MDContext/RangedRayDamage adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;
import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillReflectEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.common.NeoForge;
import java.util.*;
/** Server-owned charged wide beam. No client tick count/target/cost/position is ever accepted. */
public final class Meltdowner {
    public static final String ID=MeltdownerSession.ID;private static final Map<UUID,Hold> HOLDS=new HashMap<>();private static final Map<UUID,Long> INPUTS=new HashMap<>();private static final Set<UUID> COMMITTING=new HashSet<>();private static long nextToken;
    private Meltdowner(){}
    public static boolean start(ServerPlayer p){if(cn.academy.port.AbilityConsumption.busy(p))return false;return start(p,0);}
    public static boolean start(ServerPlayer p,long input){if(cn.academy.port.AbilityConsumption.busy(p))return false;if(!MeltdownerStarterSupport.serverThread(p)||!mayAct(p)||COMMITTING.contains(p.getUUID())||input<0||input>0&&input<=INPUTS.getOrDefault(p.getUUID(),0L))return false;Hold old=HOLDS.get(p.getUUID());if(old!=null){if(valid(p,old))return false;remove(p);}var session=MeltdownerSession.begin(AbilityStorage.get(p),p.getAbilities().instabuild);if(session==null)return false;var hold=new Hold(p.serverLevel(),session,++nextToken,input,MeltdownerStarterSupport.audience(p));HOLDS.put(p.getUUID(),hold);if(input>0)INPUTS.put(p.getUUID(),input);send(p,hold,"meltdowner_start");if(session.ending())finish(p,hold,false,false);return true;}
    public static void tick(ServerPlayer p){if(!MeltdownerStarterSupport.serverThread(p))return;var h=HOLDS.get(p.getUUID());if(h==null)return;if(!valid(p,h)){remove(p);return;}var state=h.session.state();if(!state.activated||!state.overloadFine||state.interfering){finish(p,h,false,false);return;}long now=p.serverLevel().getGameTime();if(now<=h.lastTick)return;h.lastTick=now;if(h.session.tick(p.getAbilities().instabuild)){finish(p,h,false,false);return;}if(h.released&&h.session.ticks()>=MeltdownerSession.MIN)finish(p,h,true,false);}
    /** A tap is a full cast: releasing before the minimum charge keeps the electron cluster forming and fires it the moment it is ready. */
    public static boolean release(ServerPlayer p){if(!MeltdownerStarterSupport.serverThread(p))return false;var h=HOLDS.get(p.getUUID());if(h==null)return false;if(!valid(p,h)){remove(p);return false;}if(h.session.ticks()<MeltdownerSession.MIN){h.released=true;return true;}return finish(p,h,true,false);}
    public static void abort(ServerPlayer p){if(!MeltdownerStarterSupport.serverThread(p))return;var h=HOLDS.get(p.getUUID());if(h!=null)finish(p,h,false,false);}
    public static void remove(ServerPlayer p){if(!MeltdownerStarterSupport.serverThread(p))return;var h=HOLDS.get(p.getUUID());if(h!=null)finish(p,h,false,true);INPUTS.remove(p.getUUID());}
    public static boolean active(ServerPlayer p){return p!=null&&HOLDS.containsKey(p.getUUID());}public static int heldTicks(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?0:h.session.ticks();}public static void clear(){HOLDS.clear();INPUTS.clear();COMMITTING.clear();nextToken=0;}
    private static boolean mayAct(ServerPlayer p){return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&MeltdownerSession.mayStart(AbilityStorage.get(p))&&MeltdownerStarterSupport.finiteAim(p)&&MeltdownerStarterSupport.finite(p.position());}
    private static boolean valid(ServerPlayer p,Hold h){var s=AbilityStorage.get(p);return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&h.world==p.serverLevel()&&h.session.state()==s&&h.session.active()&&s.category.equals("meltdowner")&&s.level>=3&&s.learned(ID)&&p.serverLevel().getGameTime()>=h.lastTick&&MeltdownerStarterSupport.finiteAim(p)&&MeltdownerStarterSupport.finite(p.position());}
    private static boolean finish(ServerPlayer p,Hold h,boolean fire,boolean discard){if(!HOLDS.remove(p.getUUID(),h))return false;boolean performed=false;try{var shot=fire?h.session.release():null;if(shot!=null&&COMMITTING.add(p.getUUID())){try{perform(p,h,shot);h.session.complete(shot);performed=true;}finally{COMMITTING.remove(p.getUUID());}}else h.session.discard();}finally{send(p,h,"meltdowner_end");if(!discard){AbilityStorage.save(p);AcademyNetwork.sync(p);}}return performed;}
    private static ClassicBeamRay.Vec v(Vec3 p){return new ClassicBeamRay.Vec(p.x,p.y,p.z);}private static BlockPos pos(ClassicBeamRay.Cell c){return new BlockPos(c.x(),c.y(),c.z());}
    private static void perform(ServerPlayer p,Hold h,MeltdownerSession.Shot shot){var world=p.serverLevel();Vec3 direction=MeltdownerBeamSupport.direction(p),eye=p.getEyePosition();double[] length={30};
        var removed=new java.util.ArrayList<cn.academy.port.macro.BeamImpactVfx.Cell>();var impacts=new java.util.ArrayList<Vec3>();
        ClassicBeamRay.World adapter=new ClassicBeamRay.World(){
            private boolean inTerrainShape(ClassicBeamRay.Cell c){
                Vec3 delta=pos(c).getCenter().subtract(eye);double along=delta.dot(direction);
                double limit=length[0]<30?length[0]:ClassicBeamRay.MAX_INCREMENT+.5;
                return along>=0&&along<=limit&&delta.lengthSqr()-along*along<=shot.radius()*shot.radius();
            }
            public List<ClassicBeamRay.Target> targets(ClassicBeamRay.Box b){var result=new ArrayList<ClassicBeamRay.Target>();for(var target:world.getEntities(p,new AABB(b.minX(),b.minY(),b.minZ(),b.maxX(),b.maxY(),b.maxZ()),entity->true))result.add(new ClassicBeamRay.Target(target,v(target.position())));return result;}
            public boolean attack(ClassicBeamRay.Target value,float damage){var target=(Entity)value.identity();if(NeoForge.EVENT_BUS.post(new SkillReflectEvent(p,"meltdowner."+ID,target)).isCanceled()){length[0]=Math.min(length[0],target.distanceTo(p));var hit=ClassicRaytrace.living(target,10,ClipContext.Fluid.NONE);if(hit instanceof EntityHitResult entity)ordinaryAttack(p,entity.getEntity(),MeltdownerSession.reflectedDamage(h.session.mastery()));
                    var tag=MeltdownerBeamSupport.packet(p,"meltdowner_reflection",h.token,h.input);tag.putInt("index",1);Vec3 origin=eye.add(direction.scale(eye.distanceTo(target.getEyePosition())));Vec3 reflected=ClassicRaytrace.direction(target).normalize();MeltdownerBeamSupport.ray(tag,origin,reflected,10);MeltdownerStarterSupport.send(h.audience,tag);cn.academy.port.macro.BeamImpactVfx.beam(p,ID,origin,origin.add(reflected.scale(10)),Math.min(2,shot.radius()),List.of(),List.of());return false;}
                // Unlike Electron/Scatter, the original Meltdowner uses ctx.attack and creates NO radiation mark.
                if(ordinaryAttack(p,target,damage)&&impacts.size()<64)impacts.add(target.getBoundingBox().getCenter());return true;}
            public boolean canBreak(){return AcademyConfig.contextTerrain(p.serverLevel(),"meltdowner",ID);}
            // The legacy integer DDA can wander outside its own beam at negative coordinates.
            // Only terrain is clipped to the unchanged radius/range; target selection and damage stay literal.
            public ClassicBeamRay.Block block(ClassicBeamRay.Cell c){if(!inTerrainShape(c))return new ClassicBeamRay.Block(0,true);var at=pos(c);if(!MeltdownerBeamSupport.loaded(p,at))return new ClassicBeamRay.Block(-1,false);var state=world.getBlockState(at);return new ClassicBeamRay.Block(state.getDestroySpeed(world,at),state.isAir());}
            public boolean denied(ClassicBeamRay.Cell c){return inTerrainShape(c)&&MeltdownerBeamSupport.sourceDenied(p,ID,pos(c));}
            public void destroy(ClassicBeamRay.Cell c,float chance,boolean soundProbe,ClassicBeamRay.Random random){var at=pos(c);var state=world.getBlockState(at);MeltdownerBeamSupport.drop(p,at,chance,true);if(soundProbe&&random.nextDouble()<.1){var snd=state.getSoundType(world,at,p);world.playSound(null,at.getX()+.5F,at.getY()+.5F,at.getZ()+.5F,snd.getBreakSound(),SoundSource.BLOCKS,(snd.getVolume()+1F)/2F,snd.getPitch());}if(world.setBlock(at,Blocks.AIR.defaultBlockState(),3)&&!state.isAir()&&removed.size()<256)removed.add(new cn.academy.port.macro.BeamImpactVfx.Cell(at,net.minecraft.world.level.block.Block.getId(state)));}
        };
        var random=new ClassicBeamRay.Random(){public double nextDouble(){return world.random.nextDouble();}public float nextFloat(){return world.random.nextFloat();}public int nextInt(int n){return world.random.nextInt(n);}};
        ClassicBeamRay.perform(adapter,random,v(p.position()),v(eye),v(direction),shot.radius(),shot.energy(),shot.damage());var tag=MeltdownerBeamSupport.packet(p,"meltdowner_ray",h.token,h.input);tag.putInt("index",0);tag.putInt("charge",shot.charge());MeltdownerBeamSupport.ray(tag,eye,direction,length[0]);MeltdownerStarterSupport.send(h.audience,tag);
        double shown=length[0];
        // Legacy block DDA may walk farther than its old thirty-block billboard. Show the actual
        // successful result envelope without adding damage, targets, energy or a new skill level.
        if(length[0]>=30){for(var cell:removed)shown=Math.max(shown,cell.position().getCenter().subtract(eye).dot(direction)+.7);for(var hit:impacts)shown=Math.max(shown,hit.subtract(eye).dot(direction)+.5);}
        shown=Math.min(64,shown);
        cn.academy.port.macro.BeamImpactVfx.beam(p,ID,eye,eye.add(direction.scale(shown)),shot.radius(),removed,impacts);
    }
    private static boolean ordinaryAttack(ServerPlayer p,Entity target,float damage){return AbilityDamage.attack(p,"meltdowner."+ID,target,damage);}
    private static void send(ServerPlayer p,Hold h,String kind){MeltdownerStarterSupport.send(h.audience,MeltdownerBeamSupport.packet(p,kind,h.token,h.input));}
    private static final class Hold {final ServerLevel world;final MeltdownerSession session;final long token,input;final Set<ServerPlayer> audience;long lastTick;boolean released;Hold(ServerLevel world,MeltdownerSession session,long token,long input,Set<ServerPlayer> audience){this.world=world;this.session=session;this.token=token;this.input=input;this.audience=audience;lastTick=world.getGameTime()-1;}}
}
