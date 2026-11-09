/* AcademyCraft1.0.7 MagManipContext adaptation. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.MagneticRules;
import cn.academy.port.core.LegacySingleKeyProtocol;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BlockItemStateProperties;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.*;

/** Server-authoritative held rotating block, faithful acquisition/follow/release contexts. */
public final class MagManip {
    public static final String ID=MagneticRules.MANIPULATION;
    private static final Map<UUID,Hold> HOLDS=new HashMap<>();private static final LegacySingleKeyProtocol.NonceLedger INPUTS=new LegacySingleKeyProtocol.NonceLedger();private static long nextToken;
    private MagManip() {}
    public static boolean canAcceptInput(ServerPlayer p,long input){return LegacySingleKeyServerIdentity.canClaim(INPUTS,p,input);}
    public static boolean start(ServerPlayer p){return startInternal(p,0);}
    public static boolean start(ServerPlayer p,long input){return input>0&&startInternal(p,input);}
    private static boolean startInternal(ServerPlayer p,long input){if(cn.academy.port.AbilityConsumption.busy(p))return false;
        if(!server(p)||!canAct(p,AbilityStorage.get(p))||input>0&&!LegacySingleKeyServerIdentity.claim(INPUTS,p,input))return false;
        var old=HOLDS.get(p.getUUID());if(old!=null){if(valid(p,old))return false;abort(old.identity.owner());if(HOLDS.containsKey(p.getUUID()))return false;}
        var world=p.serverLevel();ItemStack held=p.getMainHandItem();BlockState state=null;CompoundTag data=null;Vec3 position=null;BlockPos source=null;
        // Original prioritizes any held accepted ItemBlock over aimed terrain.
        if(held.getItem() instanceof BlockItem item){var candidate=held.getOrDefault(DataComponents.BLOCK_STATE,BlockItemStateProperties.EMPTY).apply(item.getBlock().defaultBlockState());if(ClassicMetalTargets.manipulation(candidate)){state=candidate;var component=held.get(DataComponents.BLOCK_ENTITY_DATA);data=component==null?null:component.copyTag();position=p.getEyePosition();}}
        if(state==null){
            // Source MagManip extracts terrain directly, bypassing AbilityPipeline/global and skill-local flags. Modern build/region protections still apply.
            if(!p.mayBuild())return false;
            // LambdaLib's custom IBlockSelector ignores nonmetal blocks, so metal can be selected
            // behind other blocks. Preserve that filtered ray rather than vanilla first-hit clip.
            var hit=traceMetal(p,MagneticRules.PICKUP_RANGE);if(hit==null)return false;
            source=hit.getBlockPos();state=world.getBlockState(source);
            if(!world.hasChunkAt(source)||!world.mayInteract(p,source)||!ClassicMetalTargets.manipulation(state)||state.getDestroySpeed(world,source)<0||NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(world,source,state,p)).isCanceled())return false;
            var te=world.getBlockEntity(source);data=te==null?null:te.saveWithFullMetadata(world.registryAccess());position=Vec3.atCenterOf(source);
        }
        var entity=new MagneticBlockEntity(p,state,data,position,source==null?p.blockPosition():source);
        // Remove BE before set-air to avoid Hopper/Dispenser onRemove spawning and duplicating it.
        if(source!=null){world.removeBlockEntity(source);if(!world.setBlock(source,net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(),3)){restore(world,source,state,data);return false;}}
        if(!world.addFreshEntity(entity)){if(source!=null)restore(world,source,state,data);return false;}
        if(source==null&&!p.getAbilities().instabuild)held.shrink(1);
        var h=new Hold(new LegacySingleKeyServerIdentity(p,input,++nextToken,AbilityStorage.get(p)),entity,MagneticRules.manipulation(AbilityStorage.get(p).exp(ID)),audience(p));HOLDS.put(p.getUUID(),h);updateTarget(p,h);send(p,h,"mag_manip_start");return true;
    }
    public static void tick(ServerPlayer p){if(!server(p))return;var h=HOLDS.get(p.getUUID());if(h==null)return;if(h.identity.owner()!=p)return;if(!valid(p,h)){abort(p);return;}long now=h.level.getGameTime();if(now<=h.lastTick)return;h.lastTick=now;updateTarget(p,h);}
    public static boolean release(ServerPlayer p){
        if(!server(p))return false;var owned=HOLDS.get(p.getUUID());if(owned!=null&&owned.identity.owner()!=p)return false;var h=HOLDS.remove(p.getUUID());if(h==null)return false;
        boolean success=valid(p,h)&&MagneticRules.acceptsRelease(p.distanceToSqr(h.entity))&&h.state.consumeSkill(ID,h.cost.cp(),h.cost.overload(),p.getAbilities().instabuild,()->!h.entity.isRemoved()&&h.entity.level()==h.level);
        Vec3 velocity=h.entity.getDeltaMovement();
        if(success){
            HitResult hit=ClassicRaytrace.living(p,MagneticRules.THROW_RANGE,ClipContext.Fluid.NONE);
            Vec3 end=hit.getType()==HitResult.Type.MISS?p.getEyePosition().add(ClassicRaytrace.direction(p).scale(MagneticRules.THROW_RANGE)):hit instanceof EntityHitResult e?hit.getLocation().add(0,e.getEntity().getEyeHeight()*.6,0):hit.getLocation();
            velocity=end.subtract(h.entity.position()).normalize().scale(h.cost.speed());h.state.setCooldown(ID,h.cost.cooldown());h.state.addExperience(ID,.005);
        }
        if(!h.entity.isRemoved())h.entity.release(velocity);send(p,h,success?"mag_manip_perform":"mag_manip_end");AbilityStorage.save(p);return success;
    }
    public static boolean release(ServerPlayer p,long input){if(!server(p)||input<=0)return false;var h=HOLDS.get(p.getUUID());return h!=null&&h.identity.matches(p,input)&&release(p);}
    public static boolean release(ServerPlayer p,long input,long token,long epoch){if(!server(p))return false;var h=HOLDS.get(p.getUUID());return h!=null&&h.identity.matches(p,input,token,epoch)&&release(p,input);}
    public static boolean abort(ServerPlayer p,long input,long token,long epoch){if(!server(p))return false;var h=HOLDS.get(p.getUUID());return h!=null&&h.identity.matches(p,input,token,epoch)&&abort(p,input);}
    public static boolean abort(ServerPlayer p,long input){if(!server(p)||input<=0)return false;var h=HOLDS.get(p.getUUID());if(h==null||!h.identity.matches(p,input))return false;abort(p);return true;}
    public static void abort(ServerPlayer p){if(!server(p))return;var owned=HOLDS.get(p.getUUID());if(owned!=null&&owned.identity.owner()!=p)return;var h=HOLDS.remove(p.getUUID());if(h!=null){if(!h.entity.isRemoved())h.entity.release(h.entity.getDeltaMovement());send(p,h,"mag_manip_end");}}
    public static boolean active(ServerPlayer p){return p!=null&&HOLDS.containsKey(p.getUUID());}
    public static MagneticBlockEntity block(ServerPlayer p){var h=p==null?null:HOLDS.get(p.getUUID());return h==null?null:h.entity;}
    public static boolean owns(ServerPlayer p,MagneticBlockEntity block){var h=p==null?null:HOLDS.get(p.getUUID());return h!=null&&h.entity==block;}
    public static void remove(ServerPlayer p){if(server(p)){abort(p);INPUTS.forget(p);}}
    public static void clear(){for(var h:HOLDS.values())if(!h.entity.isRemoved())h.entity.release(h.entity.getDeltaMovement());HOLDS.clear();INPUTS.clear();nextToken=0;}
    private static void updateTarget(ServerPlayer p,Hold h){h.entity.moveToTarget(p.getEyePosition().add(0,-.1,0).add(ClassicRaytrace.direction(p).scale(2)));}
    public static BlockHitResult traceMetal(ServerPlayer p,double range){
        Vec3 start=p.getEyePosition(),end=start.add(ClassicRaytrace.direction(p).scale(range));var ctx=new ClipContext(start,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,p);
        return BlockGetter.traverseBlocks(start,end,ctx,(c,pos)->{if(!p.level().hasChunkAt(pos))return null;BlockState state=p.level().getBlockState(pos);if(!ClassicMetalTargets.manipulation(state))return null;return c.getBlockShape(state,p.level(),pos).clip(start,end,pos);},c->null);
    }
    private static void restore(ServerLevel w,BlockPos pos,BlockState state,CompoundTag data){w.setBlock(pos,state,3);var te=w.getBlockEntity(pos);if(te==null&&state.getBlock() instanceof net.minecraft.world.level.block.EntityBlock factory){te=factory.newBlockEntity(pos,state);if(te!=null)w.setBlockEntity(te);}if(te!=null&&data!=null){te.loadWithComponents(data,w.registryAccess());te.setChanged();}}
    private static boolean server(ServerPlayer p){return p!=null&&p.serverLevel().getServer().isSameThread();}
    private static boolean canAct(ServerPlayer p,AbilityProgress s){return p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&s.category.equals("electromaster")&&s.level>=2&&s.canUse(ID)&&finite(p.position())&&finite(ClassicRaytrace.direction(p));}
    private static boolean valid(ServerPlayer p,Hold h){return h.identity.owner()==p&&(h.identity.input()==0||h.identity.owns(p))&&h.level==p.serverLevel()&&h.state==AbilityStorage.get(p)&&canAct(p,h.state)&&!h.entity.isRemoved()&&h.entity.level()==h.level&&h.level.getGameTime()>=h.lastTick;}
    private static boolean finite(Vec3 v){return Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
    private static Set<ServerPlayer> audience(ServerPlayer p){var a=new LinkedHashSet<ServerPlayer>();for(var v:p.serverLevel().players())if(v.distanceToSqr(p)<=32*32)a.add(v);a.add(p);return a;}
    private static void send(ServerPlayer p,Hold h,String kind){var t=new CompoundTag();t.putString("kind",kind);h.identity.write(t);t.putInt("block_entity",h.entity.getId());t.putDouble("cp_hint",h.cost.cp());for(var v:h.audience)if(!v.hasDisconnected())PacketDistributor.sendToPlayer(v,new AcademyNetwork.ClientData(t));}
    private static final class Hold {final LegacySingleKeyServerIdentity identity;final ServerLevel level;final AbilityProgress state;final MagneticBlockEntity entity;final MagneticRules.Manipulation cost;final long token;final Set<ServerPlayer> audience;long lastTick;Hold(LegacySingleKeyServerIdentity identity,MagneticBlockEntity e,MagneticRules.Manipulation c,Set<ServerPlayer>a){this.identity=identity;level=identity.level();state=identity.state();entity=e;cost=c;token=identity.token();audience=a;lastTick=level.getGameTime()-1;}}
}
