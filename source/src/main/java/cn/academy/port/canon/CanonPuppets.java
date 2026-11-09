package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.skill.ClassicRaytrace;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.boss.EnderDragonPart;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Mind Puppet: one non-player creature serves the caster for a short, bounded time.
 * It never attacks the caster or the caster's allies, attacks the nearest hostile creature, and follows the caster when none is left.
 * Server-owned lease; ended by expiry, the caster leaving or deactivating, or the creature dying or unloading.
 */
@EventBusSubscriber(modid="academy")
public final class CanonPuppets {
    public static final String SKILL="mental_puppet";
    private static final int MAX=64,PICK_EVERY=10,ATTACK_EVERY=20;
    private static final double RANGE=number(SKILL, "reach"),FIGHT_RANGE=number(SKILL, "radius");
    private static final Map<ServerPlayer,Puppet> BY_OWNER=new IdentityHashMap<>();
    private static final Map<Mob,Puppet> BY_MOB=new IdentityHashMap<>();
    private static final class Puppet {
        final ServerPlayer owner;final ServerLevel world;final Mob mob;final long expires;long nextPick,nextAttack;LivingEntity victim;
        Puppet(ServerPlayer owner,Mob mob,long expires){this.owner=owner;this.world=owner.serverLevel();this.mob=mob;this.expires=expires;}
    }
    private CanonPuppets() {}

    public static boolean isPuppet(Mob mob){return mob!=null&&BY_MOB.containsKey(mob);}
    public static boolean owns(ServerPlayer p){return p!=null&&BY_OWNER.containsKey(p);}

    /** The non-player creature the caster is looking at, or null. Players are never eligible; everything else with AI is. */
    public static Mob pick(ServerPlayer p){
        if(p==null)return null;
        var hit=ClassicRaytrace.living(p,RANGE,ClipContext.Fluid.NONE);
        if(!(hit instanceof EntityHitResult e))return null;
        Entity entity=e.getEntity();if(entity instanceof EnderDragonPart part)entity=part.parentMob;
        if(!(entity instanceof Mob mob))return null; // a Player is never a Mob, so players are excluded by type
        var world=p.serverLevel();
        if(!mob.isAlive()||mob.isRemoved()||mob.isNoAi()||mob.level()!=world||world.getEntity(mob.getUUID())!=mob
                ||!world.hasChunkAt(mob.blockPosition())||!world.mayInteract(p,mob.blockPosition())||!p.hasLineOfSight(mob)
                ||p.distanceToSqr(mob)>RANGE*RANGE||BY_MOB.containsKey(mob))return null;
        return mob;
    }

    public static boolean begin(ServerPlayer p,Mob mob,long expires){
        if(p==null||mob==null||BY_OWNER.containsKey(p)||BY_MOB.containsKey(mob)||BY_OWNER.size()>=MAX)return false;
        var puppet=new Puppet(p,mob,expires);BY_OWNER.put(p,puppet);BY_MOB.put(mob,puppet);
        mob.setTarget(null);steer(puppet,p.serverLevel().getGameTime());
        return true;
    }

    // ------------------------------------------------------------------ lifetime
    private static boolean valid(Puppet z){
        var p=z.owner;var state=AbilityStorage.get(p);
        return BY_OWNER.get(p)==z&&p.server.getPlayerList().getPlayer(p.getUUID())==p&&p.connection!=null&&!p.hasDisconnected()
                &&p.isAlive()&&!p.isRemoved()&&!p.isSpectator()&&p.serverLevel()==z.world&&z.world.getGameTime()<z.expires
                &&state.activated&&state.overloadFine&&!state.interfering&&state.learned(SKILL)&&SkillCatalog.enabled(state.category,SKILL)
                &&z.mob.isAlive()&&!z.mob.isRemoved()&&z.mob.level()==z.world&&z.world.getEntity(z.mob.getUUID())==z.mob
                &&z.world.hasChunkAt(z.mob.blockPosition());
    }
    private static void end(Puppet z){
        BY_OWNER.remove(z.owner,z);BY_MOB.remove(z.mob,z);
        var mob=z.mob;
        if(mob.isAlive()&&!mob.isRemoved()){
            mob.setTarget(null);mob.getNavigation().stop();
            if(mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET,MemoryStatus.REGISTERED))mob.getBrain().eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        if(z.owner.isAlive()&&!z.owner.hasDisconnected()&&z.owner.connection!=null)
            CanonVfx.emit(z.owner,SKILL,z.owner.position(),mob.position(),CanonVfx.Phase.END,1);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        long now=-1;
        for(Puppet z:java.util.List.copyOf(BY_OWNER.values())){
            if(z.owner.server!=event.getServer())continue;
            if(!valid(z)){end(z);continue;}
            now=z.world.getGameTime();steer(z,now);
        }
    }
    @SubscribeEvent public static void stop(ServerStoppedEvent event){BY_OWNER.clear();BY_MOB.clear();}

    // ------------------------------------------------------------------ behaviour
    private static boolean enemy(Puppet z,LivingEntity e){
        return e!=z.mob&&e!=z.owner&&!(e instanceof Player)&&e instanceof Enemy&&e.isAlive()&&!e.isRemoved()&&!e.isSpectator()
                &&!z.owner.isAlliedTo(e)&&!(e instanceof Mob m&&BY_MOB.containsKey(m))&&e.level()==z.world;
    }
    private static void steer(Puppet z,long now){
        var mob=z.mob;
        // The caster and the caster's allies are never the target, whatever the creature's own goals have just picked.
        var current=mob.getTarget();
        if(current!=null&&(current==z.owner||current instanceof Player||z.owner.isAlliedTo(current)||!current.isAlive()))mob.setTarget(null);
        if(now>=z.nextPick||z.victim==null||!z.victim.isAlive()||z.victim.isRemoved()){
            z.nextPick=now+PICK_EVERY;
            z.victim=z.world.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(FIGHT_RANGE),e->enemy(z,e)&&mob.hasLineOfSight(e))
                    .stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
        }
        LivingEntity victim=z.victim;
        if(victim!=null){
            mob.setTarget(victim);
            if(mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET,MemoryStatus.REGISTERED))
                mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET,victim);
            if(!(mob instanceof Enemy)&&!hasMeleeGoal(mob)&&mob.getAttributes().hasAttribute(Attributes.ATTACK_DAMAGE)){
                // Creatures with a bite but no attack AI: walk to the victim and strike with their own damage.
                mob.getNavigation().moveTo(victim,1.15);
                double reach=mob.getBbWidth()*2.0+victim.getBbWidth();
                if(now>=z.nextAttack&&mob.distanceToSqr(victim)<=reach*reach){z.nextAttack=now+ATTACK_EVERY;mob.doHurtTarget(victim);}
            }
        }else{
            mob.setTarget(null);
            if(mob.distanceToSqr(z.owner)>16)mob.getNavigation().moveTo(z.owner,1.1);
            else if(mob.distanceToSqr(z.owner)<6.25)mob.getNavigation().stop();
        }
    }
    private static boolean hasMeleeGoal(Mob mob){
        return mob.goalSelector.getAvailableGoals().stream().anyMatch(w->w.getGoal() instanceof MeleeAttackGoal);
    }
    static Vec3 center(Mob m){return m.position().add(0,m.getBbHeight()*.5,0);}
}
