package cn.academy.port.canon;

import static cn.academy.port.core.SkillTuning.number;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import java.util.IdentityHashMap;
import java.util.Map;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
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
        final ServerPlayer owner;final ServerLevel world;final Mob mob;final long expires;long nextPick,nextAttack; final LivingEntity previous; LivingEntity victim;
        Puppet(ServerPlayer owner,Mob mob,long expires){this.owner=owner;this.world=owner.serverLevel();this.mob=mob;this.expires=expires;this.previous=MentalTargeting.target(mob);}
    }
    private CanonPuppets() {}

    public static boolean isPuppet(Mob mob){return mob!=null&&BY_MOB.containsKey(mob);}
    public static boolean owns(ServerPlayer p){return p!=null&&BY_OWNER.containsKey(p);}

    /** The non-player creature the caster is looking at, or null. Players are never eligible; everything else with AI is. */
    public static Mob pick(ServerPlayer p){
        if(p==null)return null;
        if (!(MentalTargeting.aimed(p,RANGE) instanceof Mob mob)) return null;
        if (!MentalTargeting.reachable(p,mob,RANGE) || !CanonTargetPolicy.directTarget(p,mob)
                || !MentalTargeting.controllable(mob) || BY_MOB.containsKey(mob)) return null;
        return mob;
    }

    public static boolean begin(ServerPlayer p,Mob mob,long expires){
        if(p==null||mob==null||BY_OWNER.containsKey(p)||BY_MOB.containsKey(mob)||BY_OWNER.size()>=MAX)return false;
        var puppet=new Puppet(p,mob,expires);
        if (!cn.academy.port.api.ControlRelations.bind(p, mob, expires, SKILL)) return false;
        MentalTargeting.assign(mob,null);
        puppet.nextAttack = puppet.world.getGameTime() + ATTACK_EVERY;
        BY_OWNER.put(p,puppet);BY_MOB.put(mob,puppet);
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
        cn.academy.port.api.ControlRelations.release(z.mob, SKILL);
        BY_OWNER.remove(z.owner,z);BY_MOB.remove(z.mob,z);
        var mob=z.mob;
        if(mob.isAlive()&&!mob.isRemoved()){
            MentalTargeting.restore(mob,z.victim,z.previous);
            mob.getNavigation().stop();
        }
        if(z.owner.isAlive()&&!z.owner.hasDisconnected()&&z.owner.connection!=null)
            CanonVfx.emit(z.owner,SKILL,z.owner.position(),mob.position(),CanonVfx.Phase.END,1);
    }

    @SubscribeEvent public static void tick(ServerTickEvent.Post event){
        long now=-1;
        for(Puppet z:java.util.List.copyOf(BY_OWNER.values())){
            if(z.owner.server!=event.getServer())continue;
            if(!valid(z)){end(z);continue;}
            now=z.world.getGameTime();
            steer(z,now);
        }
    }
    @SubscribeEvent public static void yieldToDriver(net.neoforged.neoforge.event.tick.EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide()) return;
        var puppet = BY_MOB.get(mob);
        if (puppet != null && cn.academy.port.api.CombatControl.needsDriver(mob) && valid(puppet))
            event.setCanceled(true);
    }

    @SubscribeEvent public static void stop(ServerStoppedEvent event){BY_OWNER.clear();BY_MOB.clear();}

    @SubscribeEvent public static void retarget(LivingChangeTargetEvent event) {
        Puppet puppet = BY_MOB.get(event.getEntity());
        if (puppet != null && event.getNewAboutToBeSetTarget() != puppet.victim)
            event.setNewAboutToBeSetTarget(puppet.victim);
    }

    @SubscribeEvent public static void protectAllies(LivingIncomingDamageEvent event) {
        Puppet puppet = BY_MOB.get(event.getSource().getEntity());
        if (puppet != null && !enemy(puppet,event.getEntity())) event.setCanceled(true);
    }

    @SubscribeEvent public static void observeStrike(LivingDamageEvent.Post event) {
        Puppet puppet = BY_MOB.get(event.getSource().getEntity());
        if (puppet != null && event.getEntity() == puppet.victim && event.getNewDamage() > 0)
            puppet.nextAttack = puppet.world.getGameTime() + ATTACK_EVERY;
    }

    // ------------------------------------------------------------------ behaviour
    private static boolean enemy(Puppet z,LivingEntity e){
        return e != z.mob && e.isAlive() && !e.isRemoved() && e.level() == z.world
                && MentalTargeting.hostile(z.owner, e)
                && !e.isSpectator() && !z.owner.isAlliedTo(e) && !e.isAlliedTo(z.owner)
                && (!(e instanceof ServerPlayer player) || !player.isCreative()
                        && z.owner.server.isPvpAllowed() && z.owner.canHarmPlayer(player))
                && !cn.academy.port.api.ControlRelations.allied(z.owner, e)
                && !(e instanceof Mob m && BY_MOB.containsKey(m));
    }
    private static void steer(Puppet z,long now){
        var mob=z.mob;
        if(now>=z.nextPick || z.victim != null && !enemy(z,z.victim)){
            z.nextPick=now+PICK_EVERY;
            z.victim=z.world.getEntitiesOfClass(LivingEntity.class,mob.getBoundingBox().inflate(FIGHT_RANGE),e->enemy(z,e)&&MentalTargeting.visible(mob,e))
                    .stream().min(java.util.Comparator.comparingDouble(mob::distanceToSqr)).orElse(null);
        }
        LivingEntity victim=z.victim;
        if(victim!=null){
            boolean directed = MentalTargeting.assign(mob, victim);
            MentalTargeting.approach(mob, victim, directed && !cn.academy.port.api.CombatControl.needsDriver(mob), now, 1.15);
            if (now >= z.nextAttack && MentalTargeting.strike(mob, victim))
                z.nextAttack = now + ATTACK_EVERY;
        }else{
            boolean idle = MentalTargeting.assign(mob, null);
            if (mob.distanceToSqr(z.owner) > 16)
                MentalTargeting.approach(mob, z.owner, idle && !cn.academy.port.api.CombatControl.needsDriver(mob), now, 1.1);
            else if (mob.distanceToSqr(z.owner) < 6.25) mob.getNavigation().stop();
        }
    }
    static Vec3 center(Mob m){return m.position().add(0,m.getBbHeight()*.5,0);}
}
