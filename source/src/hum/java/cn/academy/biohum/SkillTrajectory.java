package cn.academy.biohum;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Shared segment queries. Damage and visuals consume the same ordered intersections. */
final class SkillTrajectory {
    record Hit(Vec3 position, BlockPos block, LivingEntity entity) {}
    record Result(Vec3 end, int hits, int changed) {}

    // 光之处刑 changes the collision priority of a single ordinary material on this ray.
    private static final class Priority {
        private Block material;

        boolean yields(ServerLevel world, BlockPos pos) {
            var state = world.getBlockState(pos);
            if (state.hasBlockEntity() || state.getDestroySpeed(world, pos) < 0) return false;
            if (material == null) material = state.getBlock();
            return state.is(material);
        }
    }

    static Vec3 loadedEnd(ServerLevel level, Vec3 from, Vec3 end) {
        var boundary = BlockGetter.traverseBlocks(from, end, level, (world, pos) ->
                WorldEffects.inWorld(world, pos) ? null : pos.immutable(), world -> null);
        if (boundary == null) return end;
        return new AABB(boundary).clip(from, end).orElse(from);
    }

    static Hit surface(ServerLevel level, Vec3 from, Vec3 end) {
        return surface(level, from, end, null);
    }

    static boolean priorityCanReach(ServerLevel level, Vec3 from, Vec3 to) {
        return surface(level, from, to, new Priority()).position().distanceToSqr(to) < 1e-6;
    }

    private static Hit surface(ServerLevel level, Vec3 from, Vec3 end, Priority priority) {
        return BlockGetter.traverseBlocks(from, end, level, (world, pos) -> {
            if (!WorldEffects.inWorld(world, pos)) {
                return new Hit(new AABB(pos).clip(from, end).orElse(from), null, null);
            }
            var collision = world.getBlockState(pos).getCollisionShape(world, pos).clip(from, end, pos);
            return collision == null || priority != null && priority.yields(world, pos) ? null
                    : new Hit(collision.getLocation(), pos.immutable(), null);
        }, world -> new Hit(end, null, null));
    }

    static Result beam(HumNpc caster, Skills.Skill skill, Vec3 from, Vec3 aim, UUID issuer,
                       boolean through, WorldEffects.Change terrain, boolean supernatural) {
        return directed(caster,skill,from,aim,skill.range(),skill.power(),issuer,through,terrain,supernatural);
    }

    static Result directed(HumNpc caster, Skills.Skill skill, Vec3 from, Vec3 aim, double range, double power,
                           UUID issuer, boolean through, WorldEffects.Change terrain, boolean supernatural) {
        return beam(caster,caster,skill,from,aim,range,power,issuer,through,terrain,supernatural,new HashSet<>());
    }

    private static Result beam(HumNpc original, HumNpc caster, Skills.Skill skill, Vec3 from, Vec3 aim,
                               double distance, double power, UUID issuer, boolean through,
                               WorldEffects.Change terrain, boolean supernatural, Set<UUID> reflectors) {
        var level = (ServerLevel) caster.level();
        Vec3 dir = aim.subtract(from).normalize();
        if (dir.lengthSqr() == 0) dir = caster.getLookAngle();
        Vec3 end = loadedEnd(level, from, from.add(dir.scale(distance)));
        var hits = new ArrayList<Hit>();
        var priority = skill.action() == Skills.Action.PRECEDENCE ? new Priority() : null;
        BlockGetter.traverseBlocks(from, end, level, (world, pos) -> {
            if (!WorldEffects.inWorld(world, pos)) return true;
            var block = world.getBlockState(pos).getCollisionShape(world, pos).clip(from, end, pos);
            if (block != null && (priority == null || !priority.yields(world, pos))) {
                hits.add(new Hit(block.getLocation(), pos.immutable(), null));
                if (priority != null) return true;
            }
            return null;
        }, world -> false);
        for (var entity : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(.25),
                other -> other != caster && caster.validTarget(other))) {
            entity.getBoundingBox().inflate(.09).clip(from, end)
                    .ifPresent(at -> hits.add(new Hit(at, null, entity)));
        }
        hits.sort(Comparator.comparingDouble(hit -> hit.position.distanceToSqr(from)));
        Vec3 stop = end;
        boolean blocked = false;
        int changed = 0, dealt = 0;
        double remaining = power;
        for (var hit : hits) {
            if (hit.block != null) {
                if (terrain != null && WorldEffects.edit(level, WorldEffects.Source.of(caster, issuer),
                        hit.block, terrain, remaining / 4)) {
                    if(changed==0)Payloads.cue(caster,skill.id(),Payloads.Cue.TERRAIN,hit.position,hit.position,1);
                    changed++;
                    remaining -= Math.max(.25, power / 36);
                    if (remaining > 0) continue;
                }
                stop = hit.position;
                blocked = true;
                break;
            }
            var entity = hit.entity;
            if (entity instanceof HumNpc npc && supernatural && npc.characterId.equals("touma")
                    && npc.guardUntil > level.getGameTime()
                    && new AABB(npc.rightHand(), npc.rightHand()).inflate(.42).clip(from, end).isPresent()) {
                stop = npc.rightHand();
                Payloads.cue(npc, "imagine_breaker", Payloads.Cue.CANCEL, stop, stop, 1);
                break;
            }
            if (entity instanceof HumNpc npc && npc.characterId.equals("accelerator")
                    && CombatSkills.reflects(npc) && skill.action() != Skills.Action.PRESSURE) {
                stop = hit.position;
                Vec3 bouncedEnd=stop;
                if (reflectors.add(npc.getUUID()) && !npc.getUUID().equals(original.getUUID())) {
                    Vec3 reflectedFrom = stop.add(caster.getEyePosition().subtract(stop).normalize().scale(.08));
                    var reflected = beam(original, npc, skill, reflectedFrom, caster.getEyePosition(),
                            distance - from.distanceTo(stop), remaining, null, through, terrain, supernatural, reflectors);
                    bouncedEnd=reflected.end;
                    changed += reflected.changed;
                    dealt += reflected.hits;
                }
                Payloads.cue(npc,"vector_guard",Payloads.Cue.REFLECT,stop,bouncedEnd,1,1,caster.getUUID());
                break;
            }
            DamageSource damage = supernatural ? caster.damageSources().indirectMagic(caster, caster)
                    : caster.damageSources().mobAttack(caster);
            if (entity.hurt(damage, (float) remaining)) {
                dealt++;
                Payloads.cue(caster, skill.id(), Payloads.Cue.IMPACT, hit.position, hit.position, .85f,-1,entity.getUUID());
            }
            if (!through) {
                stop = hit.position;
                break;
            }
            remaining *= .85;
        }
        Payloads.cue(caster, skill.id(), Payloads.Cue.BEAM, from, stop, skill.shape() == Skills.Shape.ARC ? .24f : .42f);
        if (blocked) Payloads.cue(caster, skill.id(), Payloads.Cue.WALL, stop, stop, 1.1f);
        return new Result(stop, dealt, changed);
    }

    private SkillTrajectory() {}
}
