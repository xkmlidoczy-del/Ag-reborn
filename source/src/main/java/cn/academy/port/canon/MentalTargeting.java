package cn.academy.port.canon;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.neoforged.neoforge.entity.PartEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.common.CommonHooks;
import net.neoforged.neoforge.event.entity.living.LivingChangeTargetEvent.LivingTargetType;

final class MentalTargeting {
    private MentalTargeting() {}

    static boolean reachable(ServerPlayer caster, LivingEntity entity, double reach) {
        if (entity == null || entity instanceof Player || !entity.isAlive() || entity.isRemoved() || entity.isSpectator()
                || entity.level() != caster.level() || caster.isAlliedTo(entity) || entity.isAlliedTo(caster)) return false;
        var level = caster.serverLevel();
        if (level.getEntity(entity.getId()) != entity || !level.hasChunkAt(entity.blockPosition())
                || !level.mayInteract(caster, entity.blockPosition())) return false;
        Vec3 eye = caster.getEyePosition();
        if (entity.getBoundingBox().distanceToSqr(eye) <= reach * reach && visible(caster, entity)) return true;
        if (entity.isMultipartEntity() && entity.getParts() != null) {
            for (var part : entity.getParts()) {
                if (part != null && part.getBoundingBox().distanceToSqr(eye) <= reach * reach
                        && clear(caster, eye, closest(part.getBoundingBox(), eye))) return true;
            }
        }
        return false;
    }

    static boolean visible(LivingEntity observer, LivingEntity target) {
        if (observer.hasLineOfSight(target)) return true;
        Vec3 eye = observer.getEyePosition();
        return clear(observer, eye, closest(target.getBoundingBox(), eye));
    }

    private static Vec3 closest(AABB box, Vec3 p) {
        return new Vec3(Math.clamp(p.x,box.minX,box.maxX),Math.clamp(p.y,box.minY,box.maxY),Math.clamp(p.z,box.minZ,box.maxZ));
    }

    private static boolean clear(Entity observer, Vec3 from, Vec3 to) {
        return observer.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,observer))
                .getType() == HitResult.Type.MISS;
    }
    static boolean hostile(ServerPlayer caster, LivingEntity entity) {
        if (CanonTargetPolicy.hostile(entity) || entity.getType().getCategory() == MobCategory.MONSTER
                || entity.getType().is(net.neoforged.neoforge.common.Tags.EntityTypes.BOSSES)
                || caster.getLastHurtByMob() == entity) return true;
        var victim = entity instanceof Mob mob ? target(mob) : entity.getLastHurtMob();
        return victim == caster || victim != null && caster.isAlliedTo(victim) && victim.isAlliedTo(caster);
    }

    static LivingEntity hitBody(HitResult hit) {
        if (!(hit instanceof EntityHitResult entityHit)) return null;
        Entity entity = entityHit.getEntity();
        if (entity instanceof PartEntity<?> part) entity = part.getParent();
        return entity instanceof LivingEntity body ? body : null;
    }

    static LivingEntity aimed(ServerPlayer caster, double reach) {
        var start = caster.getEyePosition();
        var end = start.add(cn.academy.port.skill.ClassicRaytrace.direction(caster).scale(reach));
        return cn.academy.port.api.CombatTargeting.ray(caster.serverLevel(), caster, start, end, .3,
                entity -> !entity.isSpectator() && !cn.academy.port.api.ControlRelations.allied(caster, entity))
                .stream().findFirst().orElse(null);
    }

    private static boolean brainTargets(Mob mob) {
        return mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED);
    }

    static LivingEntity target(Mob mob) {
        return brainTargets(mob) ? mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(mob.getTarget()) : mob.getTarget();
    }

    static LivingEntity observedTarget(LivingEntity entity) {
        return entity instanceof Mob mob ? target(mob) : entity.getLastHurtMob();
    }

    static boolean controllable(Mob mob) {
        return mob.isAlive() && !mob.isRemoved();
    }

    static boolean assign(Mob mob, LivingEntity target) {
        if (!controllable(mob) || mob.isNoAi()) return false;
        boolean hasBrain = brainTargets(mob);
        var previous = hasBrain ? mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) : null;
        if (hasBrain && previous != target) {
            var change = CommonHooks.onLivingChangeTarget(mob, target, LivingTargetType.BEHAVIOR_TARGET);
            if (change.isCanceled() || change.getNewAboutToBeSetTarget() != target) return false;
            mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, target);
        }
        if (mob.getTarget() != target) mob.setTarget(target);
        if (mob.getTarget() == target && target(mob) == target) return true;
        if (hasBrain && previous != target) mob.getBrain().setMemory(MemoryModuleType.ATTACK_TARGET, previous);
        return false;
    }

    static boolean stopRoute(Mob mob) {
        if (!controllable(mob) || mob.isNoAi()) return false;
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

    static void approach(Mob mob, LivingEntity target, boolean nativeTarget, long now, double speed) {
        if (mob.isWithinMeleeAttackRange(target)) return;
        if (nativeTarget && !mob.getNavigation().isDone()) return;
        if (nativeTarget && now % 10L == 0L && mob.getNavigation().moveTo(target, speed)) return;
        Vec3 direction = target.position().subtract(mob.position()).multiply(1, 0, 1).normalize();
        mob.getLookControl().setLookAt(target, 40, 30);
        mob.move(net.minecraft.world.entity.MoverType.SELF, direction.scale(0.19 * speed));
    }

    static boolean strike(Mob body, LivingEntity victim) {
        if (!body.isWithinMeleeAttackRange(victim) || !visible(body, victim)) return false;
        var attack = body.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.ATTACK_DAMAGE);
        float amount = attack == null ? 2f : (float) Math.max(1, attack.getValue());
        body.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
        cn.academy.port.api.NativeCombatDamage.hurtProjected(victim, body.damageSources().mobAttack(body), amount);
        return true;
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
