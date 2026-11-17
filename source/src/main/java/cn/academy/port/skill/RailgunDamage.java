/* Adapted from Lambda Innovation AcademyCraft 1.0.7 RangedRayDamage/Plotter. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AcademyNetwork;
import cn.academy.port.api.SkillReflectEvent;
import cn.academy.port.core.RangedSkillRanges;
import cn.academy.port.highpower.environment.SkillEnvironment;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.Shapes;
import net.neoforged.neoforge.common.NeoForge;

public final class RailgunDamage {
    public static final double HIT_RADIUS = 2.4;
    private static final String SKILL = "electromaster.railgun";
    private static final double EPSILON = 1e-6;

    public record Result(double beamLength, boolean reflectedHit) {}

    public static Result perform(ServerPlayer player, float damage, float energy) {
        var world = player.serverLevel();
        var direction = ClassicRaytrace.direction(player).normalize();
        if (direction.lengthSqr() < EPSILON) return new Result(0, false);
        var origin = player.getEyePosition();
        double reach = RangedSkillRanges.fixed("railgun");
        var end = origin.add(direction.scale(reach));
        var candidates = new ArrayList<>(world.getEntities(player, new AABB(origin, end).inflate(HIT_RADIUS),
                target -> target instanceof LivingEntity && target.isAlive()));
        candidates.removeIf(target -> !withinShot(target.position().subtract(origin), direction, reach));
        // Resolve the front of the beam first, so a reflector can stop later damage and cutting.
        candidates.sort(Comparator.comparingDouble(target -> target.position().subtract(origin).dot(direction)));

        var terrain = SkillEnvironment.begin(player, SKILL, 4);
        var cut = world.mayInteract(player, player.blockPosition()) && terrain.allowsBlockChanges()
                ? new TerrainCut(player, origin, direction, energy, terrain) : null;
        for (var target : candidates) {
            if (player.serverLevel() != world) return new Result(reach, false);
            if (!target.isAlive() || target.level() != world) continue;
            Vec3 targetPoint = target.position();
            Vec3 offset = targetPoint.subtract(origin);
            if (!withinShot(offset, direction, reach)) continue;
            double along = offset.dot(direction);
            double clear = advance(player, world, origin, direction, along, cut);
            if (clear + EPSILON < along) return new Result(clear, false);
            if (!target.isAlive() || target.level() != world || !target.position().equals(targetPoint)) continue;

            Vec3 crossSection = offset.subtract(direction.scale(along));
            // Each part of a wide beam follows the same axis. A surviving side wall can shield a target
            // even when the centerline is open.
            if (crossSection.lengthSqr() > EPSILON && firstObstruction(player, world, origin.add(crossSection), targetPoint) != null)
                continue;
            if (player.serverLevel() != world) return new Result(clear, false);
            if (NeoForge.EVENT_BUS.post(new SkillReflectEvent(player, SKILL, target)).isCanceled()) {
                var hit = ClassicRaytrace.living(target, 15, ClipContext.Fluid.NONE);
                if (hit instanceof EntityHitResult entity) AbilityDamage.attack(player, SKILL, entity.getEntity(), 14);
                var reflectedOrigin = target.getEyePosition();
                var reflectedDirection = ClassicRaytrace.direction(target).normalize();
                double reflectedLength = advance(player, world, reflectedOrigin, reflectedDirection, 15, null);
                AcademyNetwork.effect(player, "railgun", reflectedOrigin, reflectedDirection, reflectedLength);
                return new Result(along, hit instanceof EntityHitResult);
            }
            double lateral = offset.cross(direction).length();
            AbilityDamage.attack(player, SKILL, target, damage * (1 - .8 * Math.min(50, lateral) / 50));
        }
        if (player.serverLevel() != world) return new Result(reach, false);
        return new Result(advance(player, world, origin, direction, reach, cut), false);
    }

    private static boolean withinShot(Vec3 offset, Vec3 direction, double reach) {
        double along = offset.dot(direction);
        return along >= 0 && along <= reach && offset.cross(direction).length() < HIT_RADIUS;
    }

    /** Use the first surviving collider as the end plane. Canceled or budget-exhausted cuts are still solid. */
    private static double advance(ServerPlayer player, ServerLevel world, Vec3 origin, Vec3 direction,
                                  double distance, TerrainCut cut) {
        if (distance <= EPSILON) return distance;
        Vec3 end = origin.add(direction.scale(distance));
        BlockHitResult hit = firstObstruction(player, world, origin, end);
        while (hit != null && cut != null && world.hasChunkAt(hit.getBlockPos())) {
            BlockPos blocked = hit.getBlockPos();
            double cellDepth = blocked.getCenter().subtract(origin).dot(direction);
            cut.to(Math.min(distance, cellDepth));
            var next = firstObstruction(player, world, origin, end);
            if (next != null && next.getBlockPos().equals(blocked)) break;
            hit = next;
        }
        if (hit == null && cut != null) {
            cut.to(distance);
            hit = firstObstruction(player, world, origin, end);
        }
        return hit == null ? distance : Math.clamp(hit.getLocation().subtract(origin).dot(direction), 0, distance);
    }

    private static BlockHitResult firstObstruction(ServerPlayer player, ServerLevel world, Vec3 from, Vec3 to) {
        var clip = new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player);
        return BlockGetter.traverseBlocks(from, to, clip, (context, pos) -> {
            if (!world.hasChunkAt(pos)) return Shapes.block().clip(from, to, pos);
            var state = world.getBlockState(pos);
            return SkillEnvironment.isVacancy(state) ? null : context.getBlockShape(state, world, pos).clip(from, to, pos);
        }, noHit -> null);
    }

    /** Advance the original cut rays through the same energy budget once, as each portion of the beam is reached. */
    private static final class TerrainCut {
        private final ServerPlayer player;
        private final ServerLevel world;
        private final Vec3 origin, direction;
        private final SkillEnvironment.Cast terrain;
        private final List<CutRay> rays = new ArrayList<>();

        TerrainCut(ServerPlayer player, Vec3 origin, Vec3 direction, float energy, SkillEnvironment.Cast terrain) {
            this.player = player;
            this.world = player.serverLevel();
            this.origin = origin;
            this.direction = direction;
            this.terrain = terrain;
            Vec3 up = Math.abs(direction.y) > .95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
            Vec3 right = direction.cross(up).normalize();
            Vec3 vertical = direction.cross(right).normalize();
            var starts = new LinkedHashSet<BlockPos>();
            for (double x = -2; x <= 2; x += .9) {
                for (double y = -2; y <= 2; y += .9) {
                    double radius = 2 * (.9 + world.random.nextDouble() * .2);
                    if (x * x + y * y <= radius * radius)
                        starts.add(BlockPos.containing(origin.add(right.scale(x)).add(vertical.scale(y))));
                }
            }
            if (starts.isEmpty()) return;
            float perRay = energy / starts.size();
            for (BlockPos pos : starts) rays.add(new CutRay(pos, direction, perRay * (.95f + world.random.nextFloat() * .1f)));
        }

        void to(double distance) {
            for (var ray : rays) {
                while (ray.energy > 0 && ray.next.getCenter().subtract(origin).dot(direction) <= distance + EPSILON) {
                    BlockPos pos = ray.next;
                    if (!world.hasChunkAt(pos) || !world.mayInteract(player, pos)) {
                        ray.energy = 0;
                        break;
                    }
                    var state = world.getBlockState(pos);
                    if (!state.isAir() && !SkillEnvironment.isVacancy(state)) {
                        float hardness = state.getDestroySpeed(world, pos);
                        if (hardness < 0 || hardness > ray.energy || state.hasBlockEntity()
                                || !terrain.breakBlock(pos, () -> world.random.nextFloat() < .05)) {
                            ray.energy = 0;
                            break;
                        }
                        ray.energy -= hardness;
                    }
                    ray.step();
                }
            }
        }
    }

    private static final class CutRay {
        private final Plotter plotter;
        private BlockPos next;
        private float energy;

        CutRay(BlockPos start, Vec3 direction, float energy) {
            this.energy = energy;
            plotter = new Plotter(start.getX(), start.getY(), start.getZ(), direction.x, direction.y, direction.z);
            step();
        }

        void step() {
            int[] xyz = plotter.next();
            next = new BlockPos(xyz[0], xyz[1], xyz[2]);
        }
    }
}
