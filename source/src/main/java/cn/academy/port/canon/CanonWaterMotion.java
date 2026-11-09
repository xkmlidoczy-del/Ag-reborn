package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityConsumption;
import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/** Bounded motion in existing water, always acting on a real, eligible server entity. */
public final class CanonWaterMotion {
    public static final Set<String> IMPLEMENTED=Set.of("hydro_current","hydro_lift","hydro_brake","hydro_repel","hydro_rescue");
    private static final String CATEGORY="hydro_hand";
    private CanonWaterMotion() {}
    public static boolean cast(ServerPlayer p,String id) {
        if(p==null||id==null||!IMPLEMENTED.contains(id)||!p.server.isSameThread()||p.connection==null||p.hasDisconnected()
                ||p.server.getPlayerList().getPlayer(p.getUUID())!=p||!p.isAlive()||p.isRemoved()||p.isSpectator()
                ||AbilityConsumption.busy(p)||HighPowerCombat.unsupportedReason(p).isPresent()||!HighPowerResources.ready(p))return false;
        var state=AbilityStorage.get(p);int level=id.equals("hydro_current")||id.equals("hydro_repel")?3:4;
        if(!CATEGORY.equals(state.category)||state.level<level||!state.canUse(id)||!state.presets.currentContains(id)
                ||!SkillCatalog.enabled(CATEGORY,id))return false;
        LivingEntity target=p;
        if(id.equals("hydro_repel")||id.equals("hydro_rescue")) {
            var hit=ClassicRaytrace.living(p,number(id, "reach"),ClipContext.Fluid.NONE);
            if(!(hit instanceof EntityHitResult entityHit)||!(entityHit.getEntity() instanceof LivingEntity living))return false;
            target=living;
        }
        if(!eligible(p,target,id))return false;
        Vec3 before=target.getDeltaMovement(),desired=velocity(p,target,id);if(desired==null||!permitted(p,target,desired))return false;
        if(!HighPowerResources.trySpend(p,id,HighPowerGrowth.values(p).arcCost(),30))return false;
        if(AbilityStorage.get(p)!=state||!state.activated||!state.overloadFine||state.interfering
                ||!state.presets.currentContains(id)||!eligible(p,target,id)||!target.getDeltaMovement().equals(before)
                ||!permitted(p,target,desired))return false;
        target.setDeltaMovement(desired);target.hasImpulse=true;target.hurtMarked=true;
        CanonVfx.emit(p,id,p.position(),target==p?p.position().add(desired):target.position(),CanonVfx.Phase.START,12);
        state.addExperience(id,.01);AbilityStorage.save(p);return true;
    }
    private static Vec3 velocity(ServerPlayer p,LivingEntity target,String id) {
        Vec3 current=target.getDeltaMovement();if(!finite(current))return null;
        return switch(id) {
            case "hydro_current" -> {Vec3 look=ClassicRaytrace.direction(p);Vec3 horizontal=new Vec3(look.x,0,look.z);
                yield horizontal.lengthSqr()<1e-8?null:new Vec3(horizontal.normalize().x*number(id, "horizontal_speed"),Math.max(-.1,Math.min(.1,current.y)),horizontal.normalize().z*number(id, "horizontal_speed"));}
            case "hydro_lift" -> new Vec3(current.x*.5,number(id, "vertical_speed"),current.z*.5);
            case "hydro_brake" -> new Vec3(current.x*number(id, "horizontal_retention"),Math.max(-.08,Math.min(.08,current.y)),current.z*number(id, "horizontal_retention"));
            case "hydro_repel" -> {Vec3 v=target.position().subtract(p.position());Vec3 h=new Vec3(v.x,0,v.z);
                yield h.lengthSqr()<1e-8?null:new Vec3(h.normalize().x*number(id, "horizontal_speed"),Math.max(0,current.y),h.normalize().z*number(id, "horizontal_speed"));}
            case "hydro_rescue" -> new Vec3(current.x*.5,Math.max(number(id, "min_vertical_speed"),current.y),current.z*.5);
            default -> null;
        };
    }
    private static boolean eligible(ServerPlayer p,LivingEntity target,String id) {
        if(target==null||target.level()!=p.level()||target.isRemoved()||!target.isAlive()||target.isPassenger()||target.isVehicle()
                ||target.isNoGravity()||!target.isInWaterOrBubble()||!finite(target.position())||!finite(target.getDeltaMovement())
                ||target!=p&&p.distanceToSqr(target)>Math.pow(number(id, "reach"),2)||!p.serverLevel().hasChunkAt(target.blockPosition())||!p.serverLevel().mayInteract(p,target.blockPosition()))return false;
        if(target==p)return !id.equals("hydro_repel")&&!id.equals("hydro_rescue");
        if(!p.hasLineOfSight(target))return false;
        if(id.equals("hydro_rescue"))return target instanceof ServerPlayer other&&other.isShiftKeyDown()
                &&!other.isSpectator()&&!other.hasDisconnected()&&!other.getAbilities().flying
                &&HighPowerCombat.unsupportedReason(other).isEmpty()&&other.server.getPlayerList().getPlayer(other.getUUID())==other;
        if(!id.equals("hydro_repel")||!(target instanceof Mob)||!CanonTargetPolicy.hostile(target)
                ||target.isAlliedTo(p)||p.isAlliedTo(target)||HighPowerCombat.unsupportedReason(target).isPresent())return false;
        var type=target.getType();
        return CanonTargetPolicy.reviewed(target)||net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE.getKey(type).getNamespace().equals("minecraft")
                &&type!=net.minecraft.world.entity.EntityType.WITHER&&type!=net.minecraft.world.entity.EntityType.ENDER_DRAGON
                &&type!=net.minecraft.world.entity.EntityType.WARDEN&&type!=net.minecraft.world.entity.EntityType.ELDER_GUARDIAN;
    }
    private static boolean permitted(ServerPlayer owner,LivingEntity target,Vec3 velocity) {
        if(!finite(velocity)||velocity.lengthSqr()>1)return false;ServerLevel w=owner.serverLevel();Vec3 end=target.position().add(velocity);
        if(!finite(end)||!w.hasChunkAt(BlockPos.containing(end))||!w.mayInteract(owner,BlockPos.containing(end))
                ||!w.getWorldBorder().isWithinBounds(target.getBoundingBox().move(velocity)))return false;
        return w.noCollision(target,target.getBoundingBox().move(velocity));
    }
    private static boolean finite(Vec3 v){return v!=null&&Double.isFinite(v.x)&&Double.isFinite(v.y)&&Double.isFinite(v.z);}
}
