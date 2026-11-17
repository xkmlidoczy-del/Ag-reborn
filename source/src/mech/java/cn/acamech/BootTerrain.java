package cn.acamech;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;

/** Samples the collision shapes beneath the rotated tread, including partial blocks. */
public final class BootTerrain {
    private static final double EDGE = .003;
    private final Level level;
    private final CollisionContext context;
    private final Chassis.Boot boot;
    private final Map<BlockPos, List<AABB>> cached = new HashMap<>();

    public BootTerrain(MechEntity mech) {
        level = mech.level();
        context = CollisionContext.of(mech);
        boot = mech.chassis().boot;
    }

    record Contact(double height, double coverage) {}

    Contact contact(Vec3 at, float yaw, double below, double above) {
        var footprint = new Footprint(at, yaw, boot);
        var shapes = new ArrayList<AABB>();
        double top = highest(footprint, below, above + .12, shapes);
        if (!Double.isFinite(top) || top > above) return null;

        // Count heel, middle and toe contact across the width. Prefer a broad tread to one corner.
        int supported = 0;
        for (int x = -1; x <= 1; x++) {
            for (int z = -1; z <= 1; z++) {
                double side = x * boot.halfWidth() * .77;
                double forward = z * (z < 0 ? boot.heel() : boot.toe()) * .79;
                double wx = at.x + footprint.fx * forward + footprint.fz * side;
                double wz = at.z + footprint.fz * forward - footprint.fx * side;
                for (AABB box : shapes) {
                    if (Math.abs(box.maxY - top) < EDGE && wx >= box.minX && wx <= box.maxX
                            && wz >= box.minZ && wz <= box.maxZ) {
                        supported++;
                        break;
                    }
                }
            }
        }
        return new Contact(top, supported / 9.0);
    }

    double height(Vec3 at, float yaw, double below, double above) {
        return highest(new Footprint(at, yaw, boot), below, above, null);
    }

    /** A swing is also checked at the interpolated render position, between server ticks. */
    public double clearSwing(Vec3 at, float yaw, float pitch) {
        double tipDip = Math.sin(Math.abs(pitch)) * (pitch > 0 ? boot.toe() : boot.heel());
        double floor = height(at, yaw, at.y - tipDip - .06, at.y + .78);
        return Math.max(at.y, floor + tipDip + .006);
    }

    private double highest(Footprint footprint, double below, double above, List<AABB> overlaps) {
        double height = Double.NEGATIVE_INFINITY;
        int minY = Mth.floor(below) - 1;
        int maxY = Mth.floor(above);
        for (int x = Mth.floor(footprint.cx - footprint.extentX); x <= Mth.floor(footprint.cx + footprint.extentX); x++) {
            for (int z = Mth.floor(footprint.cz - footprint.extentZ); z <= Mth.floor(footprint.cz + footprint.extentZ); z++) {
                for (int y = minY; y <= maxY; y++) {
                    var pos = new BlockPos(x, y, z);
                    for (AABB box : cached.computeIfAbsent(pos, this::collision)) {
                        if (box.maxY <= below || box.minY >= above || !footprint.overlaps(box)) continue;
                        height = Math.max(height, box.maxY);
                        if (overlaps != null) overlaps.add(box);
                    }
                }
            }
        }
        return height;
    }

    private List<AABB> collision(BlockPos pos) {
        if (!level.hasChunkAt(pos) || level.isOutsideBuildHeight(pos)) return List.of();
        var state = level.getBlockState(pos);
        if (state.isAir()) return List.of();
        return state.getCollisionShape(level, pos, context).toAabbs().stream().map(box -> box.move(pos)).toList();
    }

    private static final class Footprint {
        final double cx, cz, fx, fz, extentX, extentZ, halfWidth, halfLength;

        Footprint(Vec3 at, float yaw, Chassis.Boot boot) {
            double angle = yaw * Mth.DEG_TO_RAD;
            fx = -Math.sin(angle);
            fz = Math.cos(angle);
            double center = (boot.toe() - boot.heel()) * .5;
            cx = at.x + fx * center;
            cz = at.z + fz * center;
            halfLength = (boot.toe() + boot.heel()) * .5 - EDGE;
            halfWidth = boot.halfWidth() - EDGE;
            extentX = Math.abs(fx) * halfLength + Math.abs(fz) * halfWidth;
            extentZ = Math.abs(fz) * halfLength + Math.abs(fx) * halfWidth;
        }

        boolean overlaps(AABB box) {
            double hx = (box.maxX - box.minX) * .5;
            double hz = (box.maxZ - box.minZ) * .5;
            double dx = box.minX + hx - cx;
            double dz = box.minZ + hz - cz;
            return Math.abs(dx) < extentX + hx && Math.abs(dz) < extentZ + hz
                    && Math.abs(dx * fx + dz * fz) < halfLength + hx * Math.abs(fx) + hz * Math.abs(fz)
                    && Math.abs(dx * fz - dz * fx) < halfWidth + hx * Math.abs(fz) + hz * Math.abs(fx);
        }
    }
}
