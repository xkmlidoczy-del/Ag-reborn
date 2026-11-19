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
        boolean second=caster.profile().second().shape()==TitanProfiles.Shape.BEAM;
        return TitanBoneRig.skillSockets(caster,second,0,"emit").get("emit");
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
        if (cn.academy.port.highpower.PlayerShieldArmor.holding(victim)) return;
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
            case DRAIN -> {status(victim,MobEffects.WITHER,90,0,caster);caster.heal((float)(3 * caster.profile().encounterHealth().doubleValue() / caster.profile().health()));}
            case BLIND -> {status(victim,MobEffects.BLINDNESS,80,0,caster);status(victim,MobEffects.MOVEMENT_SLOWDOWN,60,0,caster);}
            case RADIANCE -> {status(victim,MobEffects.GLOWING,120,0,caster);status(victim,MobEffects.LEVITATION,35,0,caster);}
        }
    }
    static int strike(ServerLevel level,TitanEntity caster,TitanProfiles.Ability ability,
                      List<TitanPattern.Zone> zones,java.util.Set<java.util.UUID> struck,int portions) {
        AABB area=null;
        for(var z:zones)if(z.kind()!=TitanPattern.Kind.TRAIL)area=area==null?z.bounds():area.minmax(z.bounds());
        if(area==null)return 0;
        var victims=level.getEntitiesOfClass(LivingEntity.class,area.inflate(1),v->eligible(caster,v)&&!struck.contains(v.getUUID()));
        if(victims.isEmpty())return 0;
        victims.sort(Comparator.<LivingEntity>comparingInt(e->e==caster.getTarget()?0:1).thenComparingDouble(caster::distanceToSqr));
        double base=ability.damage()*caster.getAttributeValue(cn.academy.port.highpower.CombatAttributes.ABILITY_POWER)/4*TitanConfig.DAMAGE_MULTIPLIER.get()*(TitanEnvironment.empowered(level,caster)?1.15:1)*(caster.enraged()?1.20:1)/portions;
        int hits=0;
        for(var v:victims) {
            for(var z:zones) {
                if(!z.touches(v.getBoundingBox()))continue;
                if(z.kind()==TitanPattern.Kind.RAY && level.clip(new ClipContext(z.a(),v.getBoundingBox().getCenter(),
                        ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,caster)).getType()==HitResult.Type.BLOCK)continue;
                float damage=(float)(base*(ability.effect()==TitanProfiles.Effect.SHOCK && v.isInWaterOrBubble()?1.15:1));
                if(cn.academy.port.highpower.CapBreak.hurt(v, level.damageSources().mobAttack(caster), damage)){
                    secondary(caster,v,ability.effect(),z.a());struck.add(v.getUUID());hits++;
                }
                break;
            }
            if(struck.size()>=MAX_VICTIMS)break;
        }
        return hits;
    }
    /** Immediate resolution for the existing direct ability API (the entity AI advances in game ticks). */
    public static int resolve(ServerLevel level,TitanEntity caster,TitanProfiles.Ability ability,Vec3 origin,Vec3 lockedTarget) {
        boolean second=ability.equals(caster.profile().second());
        var move=TitanPattern.move(caster.species(),second);var struck=new java.util.HashSet<java.util.UUID>();
        int hits=0;
        for(int t=0;t<=TitanPattern.duration(move);t++)if(TitanPattern.active(move,t)) {
            if(TitanPattern.sequence(move))struck.clear();
            var zones=TitanPattern.sample(level,caster,second,caster.position(),lockedTarget,ability.windupTicks()+t,t);
            hits+=strike(level,caster,ability,zones,struck,TitanPattern.sequence(move)?3:1);
        }
        TitanEnvironment.interact(level,caster,ability.shape()==TitanProfiles.Shape.RING?caster.position():lockedTarget,ability.radius());
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
            if(cn.academy.port.highpower.CapBreak.hurt(victim, level.damageSources().mobAttack(caster), meleeDamage(caster))) {
                impulse(victim,caster.position(),false,.7);hits++;
            }
        }
        if(hits>0){TitanVfx.melee(level,caster.species(),center);caster.meleeImpactSound(center);}
        return hits>0;
    }
    private static float meleeDamage(TitanEntity caster) {
        return (float)(caster.getAttributeValue(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE)
                * TitanConfig.DAMAGE_MULTIPLIER.get() * (caster.enraged() ? 1.20 : 1.0));
    }

    /** Apply one hit only after the server's limb cuboid actually intersects. */
    public static boolean limbHit(ServerLevel level,TitanEntity caster,LivingEntity victim) {
        if(!cn.academy.port.highpower.CapBreak.hurt(victim, level.damageSources().mobAttack(caster), meleeDamage(caster)))return false;
        impulse(victim,caster.position(),false,.7);
        TitanVfx.melee(level,caster.species(),victim.position());
        caster.meleeImpactSound(victim.position());
        return true;
    }
}
