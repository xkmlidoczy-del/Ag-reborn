package cn.acamech;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.PacketDistributor;

import static cn.acamech.RangedWeapon.*;

final class RangedCombat {
    static final float YAW_LIMIT = 44f, PITCH_UP = -37f, PITCH_DOWN = 57f;
    private static final double SPLASH = 2.35;

    private RangedCombat() {}

    static boolean canAim(MechEntity mech, Player pilot) {
        return Math.abs(Mth.wrapDegrees(pilot.getYRot() - mech.getYRot())) <= YAW_LIMIT
                && pilot.getXRot() >= PITCH_UP && pilot.getXRot() <= PITCH_DOWN;
    }

    static void fire(MechEntity mech, Player pilot) {
        var level = (ServerLevel) mech.level();
        var weapon = mech.chassis().ranged;
        Vec3 eye = pilot.getEyePosition();
        Vec3 look = pilot.getLookAngle();
        Vec3 aim = trace(level, mech, eye, eye.add(look.scale(weapon.range)), .06).end;
        List<Trace> paths = new ArrayList<>(weapon.emitterCount());
        for (int i = 0; i < weapon.emitterCount(); i++) {
            Vec3 from = mech.rangedMuzzle(i);
            Vec3 base = mech.position().add(0, from.y - mech.getY(), 0);
            Vec3 obstruction = block(level, mech, base, from);
            if (obstruction != null) {
                paths.add(new Trace(base, obstruction, null, true));
                continue;
            }
            Vec3 direction = aim.subtract(from).normalize();
            if (direction.dot(look) < .10) direction = look;
            if (weapon == PULSE) {
                var bolt = new PulseBolt(MechMod.PULSE_BOLT.get(), level);
                bolt.launch(mech, pilot, from, direction);
                if (level.addFreshEntity(bolt)) {
                    sound(level, from, SoundEvents.BREEZE_SHOOT, 1.25f, .61f);
                    sound(level, from, SoundEvents.IRON_GOLEM_ATTACK, .88f, .61f);
                    return;
                }
                mech.setRangedResult(MISS);
                return;
            }
            paths.add(trace(level, mech, from, from.add(direction.scale(weapon.range)), weapon == NEEDLE ? .10 : .17));
        }
        resolveBeams(level, mech, pilot, weapon, paths);
    }

    private static void resolveBeams(ServerLevel level, MechEntity mech, Player pilot, RangedWeapon weapon, List<Trace> paths) {
        var counts = new LinkedHashMap<LivingEntity, Integer>();
        for (Trace trace : paths) {
            if (trace.target != null) counts.merge(trace.target, 1, Integer::sum);
        }
        var damaged = new HashSet<LivingEntity>();
        for (var entry : counts.entrySet()) {
            var target = entry.getKey();
            if (target.hurt(level.damageSources().mobProjectile(mech, pilot), weapon.damage * entry.getValue())) {
                damaged.add(target);
                Vec3 away = target.position().subtract(mech.position()).normalize();
                target.knockback(weapon == NEEDLE ? .25 : .45, -away.x, -away.z);
            }
        }
        var rays = new ArrayList<MechMod.RangedRay>(paths.size());
        boolean stopped = false;
        for (Trace trace : paths) {
            int contact = trace.target != null && damaged.contains(trace.target) ? 1
                    : trace.target != null || trace.blocked ? BLOCKED : MISS;
            stopped |= contact == BLOCKED;
            rays.add(new MechMod.RangedRay(trace.start, trace.end, contact));
            if (contact == 1) {
                sound(level, trace.end, weapon == NEEDLE ? SoundEvents.TRIDENT_HIT : SoundEvents.AMETHYST_BLOCK_BREAK, .85f, 1.22f);
                sound(level, trace.end, SoundEvents.HEAVY_CORE_HIT, weapon == NEEDLE ? .47f : .76f, .92f);
            } else if (contact == BLOCKED) {
                sound(level, trace.end, SoundEvents.COPPER_BULB_TURN_OFF, .66f, 1.1f);
            }
        }
        mech.setRangedResult(damaged.isEmpty() ? stopped ? BLOCKED : MISS : damaged.size());
        Vec3 start = paths.getFirst().start;
        sound(level, start, weapon == NEEDLE ? SoundEvents.SHULKER_SHOOT : SoundEvents.CONDUIT_ACTIVATE,
                1.18f, weapon == NEEDLE ? 1.74f : 1.45f);
        broadcast(level, start, new MechMod.RangedEffect(weapon, rays));
    }

    static void pulseImpact(ServerLevel level, PulseBolt bolt, Entity shooter, MechEntity mech, Trace contact, Vec3 velocity) {
        Vec3 point = contact.end;
        var source = level.damageSources().mobProjectile(bolt, shooter instanceof LivingEntity living ? living : null);
        int count = 0;
        if (contact.target != null && contact.target.hurt(source, PULSE.damage)) {
            kick(contact.target, velocity, .79);
            count++;
        }
        // Ray starts just before the surface so damage around cover obeys the blast's line of sight.
        Vec3 origin = point.subtract(velocity.normalize().scale(.08));
        for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(point, point).inflate(SPLASH),
                e -> e instanceof Enemy && e.isAlive() && e != contact.target)) {
            Vec3 center = target.getBoundingBox().getCenter();
            if (center.distanceToSqr(point) > SPLASH * SPLASH || block(level, bolt, origin, center) != null) continue;
            if (target.hurt(source, 7)) {
                kick(target, center.subtract(point), .54);
                count++;
            }
        }
        int result = count > 0 ? count : BLOCKED;
        if (mech != null && mech.isAlive()) mech.setRangedResult(result);
        sound(level, point, SoundEvents.FIREWORK_ROCKET_BLAST, 1.21f, .67f);
        sound(level, point, result > 0 ? SoundEvents.MACE_SMASH_GROUND : SoundEvents.IRON_GOLEM_DAMAGE, 1.04f, .67f);
        broadcast(level, point, new MechMod.RangedEffect(PULSE, List.of(new MechMod.RangedRay(
                point.subtract(velocity.normalize()), point, result))));
    }

    private static void kick(LivingEntity target, Vec3 delta, double strength) {
        target.knockback(strength, -delta.x, -delta.z);
    }

    static Trace trace(ServerLevel level, Entity source, Vec3 from, Vec3 to, double radius) {
        Vec3 wall = block(level, source, from, to);
        Vec3 end = wall != null ? wall : to;
        LivingEntity nearest = null;
        Vec3 contact = end;
        double best = from.distanceToSqr(end);
        for (var target : level.getEntitiesOfClass(LivingEntity.class, new AABB(from, end).inflate(radius + .001),
                e -> e instanceof Enemy && e.isAlive() && !source.isPassengerOfSameVehicle(e))) {
            var bounds = target.getBoundingBox().inflate(radius);
            Vec3 point = bounds.contains(from) ? from : bounds.clip(from, end).orElse(null);
            if (point == null || from.distanceToSqr(point) >= best) continue;
            nearest = target;
            contact = point;
            best = from.distanceToSqr(point);
        }
        if (nearest != null) return new Trace(from, contact, nearest, false);
        return new Trace(from, end, null, wall != null);
    }

    private static Vec3 block(ServerLevel level, Entity source, Vec3 from, Vec3 to) {
        // Stop at the edge of loaded terrain rather than loading chunks or hitting through them.
        Vec3 end = to;
        for (int n = 1, steps = 1 + (int) Math.ceil(from.distanceTo(to) / 5); n <= steps; n++) {
            if (!level.hasChunkAt(BlockPos.containing(from.lerp(to, n / (double) steps)))) {
                end = from.lerp(to, (n - 1) / (double) steps);
                break;
            }
        }
        var result = level.clip(new ClipContext(from, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, source));
        if (result.getType() == HitResult.Type.BLOCK) return result.getLocation();
        return end.equals(to) ? null : end;
    }

    private static void sound(ServerLevel level, Vec3 point, SoundEvent sound, float gain, float pitch) {
        level.playSound(null, point.x, point.y, point.z, sound, SoundSource.PLAYERS, gain, pitch);
    }

    private static void broadcast(ServerLevel level, Vec3 point, MechMod.RangedEffect effect) {
        PacketDistributor.sendToPlayersNear(level, null, point.x, point.y, point.z, 88, effect);
    }

    record Trace(Vec3 start, Vec3 end, LivingEntity target, boolean blocked) {
        boolean contact() { return target != null || blocked; }
    }
}
