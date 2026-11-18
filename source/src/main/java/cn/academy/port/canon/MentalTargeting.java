package cn.academy.port.canon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.LivingTargetType;

final class MentalTargeting {
    private MentalTargeting() {}

    static boolean reachable(ServerPlayer caster, LivingEntity entity, double reach) {
        if (entity == null || entity instanceof Player || !entity.isAlive() || entity.isRemoved() || entity.isSpectator()
                || entity.level() != caster.level() || caster.isAlliedTo(entity) || entity.isAlliedTo(caster)
                || caster.distanceToSqr(entity) > reach * reach) return false;
        var level = caster.serverLevel();
        return level.getEntity(entity.getId()) == entity && level.hasChunkAt(entity.blockPosition())
                && level.mayInteract(caster, entity.blockPosition()) && caster.hasLineOfSight(entity);
    }

    static boolean hostile(ServerPlayer caster, LivingEntity entity) {
        if (CanonTargetPolicy.hostile(entity) || caster.getLastHurtByMob() == entity) return true;
        var victim = entity instanceof Mob mob ? target(mob) : entity.getLastHurtMob();
        return victim == caster || victim != null && caster.isAlliedTo(victim) && victim.isAlliedTo(caster);
    }

    private static boolean brainTargets(Mob mob) {
        return mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED);
    }

    static LivingEntity target(Mob mob) {
        return brainTargets(mob) ? mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) : mob.getTarget();
    }

    static LivingEntity observedTarget(LivingEntity entity) {
        return entity instanceof Mob mob ? target(mob) : entity.getLastHurtMob();
    }

    static boolean controllable(Mob mob) {
        return mob.isAlive() && !mob.isRemoved() && !mob.isNoAi() && !mob.isInvulnerable();
    }

    static boolean assign(Mob mob, LivingEntity target) {
        if (!controllable(mob)) return false;
        if (target(mob) == target) return true;
        if (brainTargets(mob)) {
            var change = CommonHooks.onLivingChangeTarget(mob, target, LivingTargetType.BEHAVIOR_TARGET);
            if (change.isCanceled() || change.getNewAboutToBeSetTarget() != target) return false;
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        } else {
            mob.setTarget(target);
        }
        return target(mob) == target;
    }

    static boolean stopRoute(Mob mob) {
        if (!controllable(mob)) return false;
        var brain = mob.getBrain();
        if (brain.checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED))
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        mob.getNavigation().stop();
        return mob.getNavigation().isDone();
    }

    static boolean moving(Mob mob) {
        return !mob.getNavigation().isDone()
                || mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.VALUE_PRESENT);
    }

    static void restore(Mob mob, LivingEntity owned, LivingEntity previous) {
        if (target(mob) == owned)
            assign(mob, previous != null && previous.isAlive() && !previous.isRemoved()
                    && previous.level() == mob.level() ? previous : null);
    }

    static boolean resisted(ServerPlayer caster) {
        if (!caster.hasDisconnected())
            caster.displayClientMessage(Component.translatable("academy.mental.resisted"), true);
        return false;
    }
}
