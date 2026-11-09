package cn.academy.port.titan;

import java.util.Comparator;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Damage is server authoritative and applied once at the authored impact tick. */
public final class TitanCombat {
    private TitanCombat() {}
    private static final int MAX_VICTIMS=64;
    public static Vec3 beamStart(TitanEntity caster) {
        // Root-relative approximations of each model's head/jaw pivot, in blocks.
        // The animated client bone is not available to the authoritative server.
        double[] mouth=switch(caster.species()) {
            case "storm_drake" -> new double[]{16,24.3};
            case "tidal_sail" -> new double[]{16,8.5};
            case "crystal_mantis" -> new double[]{9,22.5};
            case "clockwork_rex" -> new double[]{11,20.2};
            default -> new double[]{caster.getBbWidth()*.5,caster.getBbHeight()*.5};
        };
        double yaw=Math.toRadians(caster.yBodyRot);
        return caster.position().add(-Math.sin(yaw)*mouth[0],mouth[1],Math.cos(yaw)*mouth[0]);
    }
    public static Vec3 clippedBeamEnd(ServerLevel level,TitanEntity caster,Vec3 from,Vec3 desired,double maxRange) {
        Vec3 diff=desired.subtract(from);
        Vec3 end=diff.lengthSqr()>maxRange*maxRange?from.add(diff.normalize().scale(maxRange)):desired;
        BlockHitResult hit=level.clip(new ClipContext(from,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,caster));
        return hit.getType()==HitResult.Type.BLOCK?hit.getLocation():end;
    }
    public static boolean eligible(TitanEntity caster,LivingEntity target) {
        if(target==caster || target instanceof TitanEntity || !target.isAlive() || caster.isAlliedTo(target))return false;
        return !(target instanceof Player player && (player.isCreative() || player.isSpectator()));
    }
    private static double segmentDistanceSqr(Vec3 p,Vec3 a,Vec3 b) {
        Vec3 ab=b.subtract(a);double l=ab.lengthSqr();
        if(l<1.0e-8)return p.distanceToSqr(a);
        double t=Math.max(0,Math.min(1,p.subtract(a).dot(ab)/l));
        return p.distanceToSqr(a.add(ab.scale(t)));
    }
    private static void status(LivingEntity victim,Holder<MobEffect> effect,int ticks,int amplifier,TitanEntity source) {
        victim.addEffect(new MobEffectInstance(effect,ticks,amplifier),source);
    }
    private static void impulse(LivingEntity victim,Vec3 center,boolean pull,double strength) {
        double dx=victim.getX()-center.x,dz=victim.getZ()-center.z;
        double length=Math.sqrt(dx*dx+dz*dz);if(length<.001){dx=.01;length=.01;}
        double sign=pull?-1:1;victim.push(sign*dx/length*strength,.16,sign*dz/length*strength);victim.hurtMarked=true;
    }
    private static void secondary(TitanEntity caster,LivingEntity victim,TitanProfiles.Effect effect,Vec3 center) {
        switch(effect) {
            case FIRE -> {victim.igniteForSeconds(5);impulse(victim,center,false,.8);}
            case FROST -> {status(victim,MobEffects.MOVEMENT_SLOWDOWN,120,1,caster);victim.setTicksFrozen(Math.max(victim.getTicksFrozen(),100));}
            case SHOCK -> {status(victim,MobEffects.MOVEMENT_SLOWDOWN,60,1,caster);impulse(victim,center,false,.65);}
            case ROOT -> {status(victim,MobEffects.MOVEMENT_SLOWDOWN,120,2,caster);status(victim,MobEffects.WEAKNESS,100,0,caster);}
            case SPORE -> {status(victim,MobEffects.POISON,120,0,caster);status(victim,MobEffects.MOVEMENT_SLOWDOWN,80,0,caster);}
            case PRESSURE -> {impulse(victim,center,true,1.0);status(victim,MobEffects.MOVEMENT_SLOWDOWN,80,0,caster);}
            case GRAVITY -> {impulse(victim,center,true,1.2);status(victim,MobEffects.MOVEMENT_SLOWDOWN,70,1,caster);}
            case VENOM -> {status(victim,MobEffects.POISON,140,1,caster);}
            case SLASH -> {status(victim,MobEffects.WEAKNESS,80,0,caster);impulse(victim,center,false,.7);}
            case DRAIN -> {status(victim,MobEffects.WITHER,90,0,caster);caster.heal(3);}
            case BLIND -> {status(victim,MobEffects.BLINDNESS,80,0,caster);status(victim,MobEffects.MOVEMENT_SLOWDOWN,60,0,caster);}
            case RADIANCE -> {status(victim,MobEffects.GLOWING,120,0,caster);status(victim,MobEffects.LEVITATION,35,0,caster);}
        }
    }
    public static int resolve(ServerLevel level,TitanEntity caster,TitanProfiles.Ability ability,Vec3 origin,Vec3 lockedTarget) {
        Vec3 center=ability.shape()==TitanProfiles.Shape.RING?origin:lockedTarget;
        AABB query=ability.shape()==TitanProfiles.Shape.BEAM
            ?new AABB(origin,lockedTarget).inflate(ability.radius()+3,ability.radius()+8,ability.radius()+3)
            :new AABB(center.x-ability.radius(),center.y-16,center.z-ability.radius(),center.x+ability.radius(),center.y+32,center.z+ability.radius());
        List<LivingEntity> victims=level.getEntitiesOfClass(LivingEntity.class,query,e->eligible(caster,e));
        victims.sort(Comparator.<LivingEntity>comparingInt(e->e==caster.getTarget()?0:1).thenComparingDouble(e->e.distanceToSqr(center)));
        boolean empowered=TitanEnvironment.empowered(level,caster);
        int hits=0;
        for(LivingEntity victim:victims) {
            if(hits>=MAX_VICTIMS)break;
            double extra=victim.getBbWidth()*.5;
            if(ability.shape()==TitanProfiles.Shape.BEAM) {
                if(segmentDistanceSqr(victim.getBoundingBox().getCenter(),origin,lockedTarget)>Math.pow(ability.radius()+extra,2))continue;
            } else {
                double dx=victim.getX()-center.x,dz=victim.getZ()-center.z;
                if(dx*dx+dz*dz>Math.pow(ability.radius()+extra,2))continue;
            }
            float damage=(float)(ability.damage()*TitanConfig.DAMAGE_MULTIPLIER.get()*(empowered?1.15:1.0)*(caster.enraged()?1.20:1.0));
            if(ability.effect()==TitanProfiles.Effect.SHOCK && victim.isInWaterOrBubble())damage*=1.15f;
            if(victim.hurt(level.damageSources().mobAttack(caster),damage)) {secondary(caster,victim,ability.effect(),center);hits++;}
        }
        TitanEnvironment.interact(level,caster,center,ability.radius());
        TitanVfx.impact(level,caster.species(),ability,origin,lockedTarget);
        return hits;
    }
    public static boolean melee(ServerLevel level,TitanEntity caster,LivingEntity target,double reach) {
        if(target==null||!eligible(caster,target)||caster.distanceToSqr(target)>reach*reach)return false;
        Vec3 center=target.position();double sweep=Math.max(5.0,reach*.35);
        AABB area=new AABB(center,center).inflate(sweep,8,sweep);
        List<LivingEntity> victims=level.getEntitiesOfClass(LivingEntity.class,area,e->eligible(caster,e));
        victims.sort(Comparator.<LivingEntity>comparingInt(e->e==target?0:1).thenComparingDouble(e->e.distanceToSqr(center)));
        int hits=0;
        for(LivingEntity victim:victims) {
            if(hits>=MAX_VICTIMS)break;
            double dx=victim.getX()-center.x,dz=victim.getZ()-center.z;
            double extra=victim.getBbWidth()*.5;
            if(dx*dx+dz*dz>(sweep+extra)*(sweep+extra))continue;
            if(caster.distanceToSqr(victim)>(reach+sweep)*(reach+sweep))continue;
            if(victim.hurt(level.damageSources().mobAttack(caster),(float)(caster.profile().attack()*TitanConfig.DAMAGE_MULTIPLIER.get()*(caster.enraged()?1.20:1.0)))) {
                impulse(victim,caster.position(),false,.7);hits++;
            }
        }
        if(hits>0){TitanVfx.melee(level,caster.species(),center);caster.meleeImpactSound(center);}
        return hits>0;
    }
    /** Apply one hit only after the server's limb cuboid actually intersects. */
    public static boolean limbHit(ServerLevel level,TitanEntity caster,LivingEntity victim) {
        float damage=(float)(caster.profile().attack()*TitanConfig.DAMAGE_MULTIPLIER.get()*(caster.enraged()?1.20:1.0));
        if(!victim.hurt(level.damageSources().mobAttack(caster),damage))return false;
        impulse(victim,caster.position(),false,.7);
        TitanVfx.melee(level,caster.species(),victim.position());
        caster.meleeImpactSound(victim.position());
        return true;
    }
}
