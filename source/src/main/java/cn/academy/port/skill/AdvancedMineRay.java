/* AcademyCraft1.0.7 Expert/Luck MRContext world/transport adaptation, GPLv3; see NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.*;

/** Server-owned native adapters for two original continuous mining variants.
 * UUID maps, input tombstones, game-time guard and lifecycle disposal follow the m13 hold contract. */
final class AdvancedMineRay {
    private static final Map<AdvancedMineRaySession.Tier,Map<UUID,Hold>> HOLDS=new EnumMap<>(AdvancedMineRaySession.Tier.class);
    private static final Map<AdvancedMineRaySession.Tier,Map<UUID,Long>> INPUTS=new EnumMap<>(AdvancedMineRaySession.Tier.class);
    private static long nextToken;
    static {for(var tier:AdvancedMineRaySession.Tier.values()){HOLDS.put(tier,new HashMap<>());INPUTS.put(tier,new HashMap<>());}}
    private AdvancedMineRay(){}
    static boolean start(ServerPlayer p,AdvancedMineRaySession.Tier tier,long input) {if(cn.academy.port.AbilityConsumption.busy(p))return false;
        if(!MeltdownerStarterSupport.serverThread(p)||!mayAct(p,tier)||input<0||input>0&&input<=INPUTS.get(tier).getOrDefault(p.getUUID(),0L))return false;
        var old=HOLDS.get(tier).get(p.getUUID());if(old!=null){if(valid(p,old))return false;remove(p,tier);}
        var session=AdvancedMineRaySession.begin(AbilityStorage.get(p),tier,p.getAbilities().instabuild);if(session==null)return false;
        var hold=new Hold(p.serverLevel(),session,++nextToken,input,MeltdownerStarterSupport.audience(p));HOLDS.get(tier).put(p.getUUID(),hold);
        if(input>0)INPUTS.get(tier).put(p.getUUID(),input);send(p,hold,"_start");if(session.ending())finish(p,hold,false);return true;
    }
    static void tick(ServerPlayer p,AdvancedMineRaySession.Tier tier) {
        if(!MeltdownerStarterSupport.serverThread(p))return;var hold=HOLDS.get(tier).get(p.getUUID());if(hold==null)return;
        if(!valid(p,hold)){remove(p,tier);return;}var state=hold.session.state();
        if(!state.activated||!state.overloadFine||state.interfering){finish(p,hold,false);return;}
        long now=p.serverLevel().getGameTime();if(now<=hold.lastTick)return;hold.lastTick=now;
        boolean end=hold.session.tick(p.getAbilities().instabuild,new AdvancedMineRaySession.World() {
            public AdvancedMineRaySession.Cell trace() {
                Vec3 eye=p.getEyePosition();var hit=p.serverLevel().clip(new ClipContext(eye,eye.add(MeltdownerBeamSupport.direction(p).scale(AdvancedMineRaySession.range(tier))),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p));
                if(hit.getType()!=HitResult.Type.BLOCK)return null;var at=hit.getBlockPos();return new AdvancedMineRaySession.Cell(at.getX(),at.getY(),at.getZ());
            }
            private BlockPos pos(AdvancedMineRaySession.Cell c){return new BlockPos(c.x(),c.y(),c.z());}
            public boolean denied(AdvancedMineRaySession.Cell c){return MeltdownerBeamSupport.sourceDenied(p,tier.id,pos(c));}
            public int harvestLevel(AdvancedMineRaySession.Cell c,AdvancedMineRaySession.Cell previous){return MeltdownerBeamSupport.harvestLevel(p,pos(c));}
            public float hardness(AdvancedMineRaySession.Cell c){return p.serverLevel().getBlockState(pos(c)).getDestroySpeed(p.serverLevel(),pos(c));}
            public void breakBlock(AdvancedMineRaySession.Cell c,int fortune){MeltdownerBeamSupport.advancedMineBreak(p,pos(c),fortune);}
            public void particles(AdvancedMineRaySession.Cell c) {
                var tag=MeltdownerBeamSupport.packet(p,tier.id+"_particles",hold.token,hold.input);tag.putInt("index",hold.session.ticks());
                tag.putInt("x",c.x());tag.putInt("y",c.y());tag.putInt("z",c.z());MeltdownerStarterSupport.send(hold.audience,tag);
            }
        });if(end)finish(p,hold,false);
    }
    static boolean release(ServerPlayer p,AdvancedMineRaySession.Tier tier) {
        if(!MeltdownerStarterSupport.serverThread(p))return false;var hold=HOLDS.get(tier).get(p.getUUID());if(hold==null)return false;
        if(!valid(p,hold)){remove(p,tier);return false;}finish(p,hold,false);return true;
    }
    static void remove(ServerPlayer p,AdvancedMineRaySession.Tier tier) {
        if(!MeltdownerStarterSupport.serverThread(p))return;var hold=HOLDS.get(tier).get(p.getUUID());if(hold!=null)finish(p,hold,true);INPUTS.get(tier).remove(p.getUUID());
    }
    static boolean active(ServerPlayer p,AdvancedMineRaySession.Tier tier){return p!=null&&HOLDS.get(tier).containsKey(p.getUUID());}
    static int heldTicks(ServerPlayer p,AdvancedMineRaySession.Tier tier){var hold=p==null?null:HOLDS.get(tier).get(p.getUUID());return hold==null?0:hold.session.ticks();}
    static AdvancedMineRaySession.Cell target(ServerPlayer p,AdvancedMineRaySession.Tier tier){var hold=p==null?null:HOLDS.get(tier).get(p.getUUID());return hold==null?AdvancedMineRaySession.Cell.NONE:hold.session.target();}
    static void clear(AdvancedMineRaySession.Tier tier){HOLDS.get(tier).clear();INPUTS.get(tier).clear();}
    private static boolean mayAct(ServerPlayer p,AdvancedMineRaySession.Tier tier){return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&AdvancedMineRaySession.mayStart(AbilityStorage.get(p),tier)&&MeltdownerStarterSupport.finiteAim(p)&&MeltdownerStarterSupport.finite(p.position());}
    private static boolean valid(ServerPlayer p,Hold hold) {
        var state=AbilityStorage.get(p);var tier=hold.session.tier();return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&hold.world==p.serverLevel()&&hold.session.state()==state&&hold.session.active()&&state.category.equals("meltdowner")&&state.level>=tier.level&&state.learned(tier.id)&&p.serverLevel().getGameTime()>=hold.lastTick&&MeltdownerStarterSupport.finiteAim(p)&&MeltdownerStarterSupport.finite(p.position());
    }
    private static void finish(ServerPlayer p,Hold hold,boolean discard) {
        if(!HOLDS.get(hold.session.tier()).remove(p.getUUID(),hold))return;
        if(discard)hold.session.discard();else hold.session.finish();send(p,hold,"_end");
        if(!discard){AbilityStorage.save(p);AcademyNetwork.sync(p);}
    }
    private static void send(ServerPlayer p,Hold hold,String suffix){MeltdownerStarterSupport.send(hold.audience,MeltdownerBeamSupport.packet(p,hold.session.tier().id+suffix,hold.token,hold.input));}
    private static final class Hold {
        final ServerLevel world;final AdvancedMineRaySession session;final long token,input;final Set<ServerPlayer> audience;long lastTick;
        Hold(ServerLevel world,AdvancedMineRaySession session,long token,long input,Set<ServerPlayer> audience){this.world=world;this.session=session;this.token=token;this.input=input;this.audience=audience;lastTick=world.getGameTime()-1;}
    }
}
