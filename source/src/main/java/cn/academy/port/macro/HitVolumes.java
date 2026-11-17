package cn.academy.port.macro;

import java.util.Optional;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Shared server-and-client authoritative 3D hit-volume mathematics (Sphere, Capsule/Beam, Cone, Slab, and Oriented Bounding Box).
 * Pure geometric math with zero client-only dependencies so dedicated-server linkage and visual meshes share exact boundaries.
 */
public final class HitVolumes {
    public record Basis(Vec3 forward, Vec3 right, Vec3 up) {}
    public record ObbSpec(double halfWidth, double halfHeight, double halfThickness) {
        public double halfDepth() { return halfThickness; }
    }

    private HitVolumes() {}

    /** Squared Euclidean distance from point {@code p} to axis-aligned box {@code box} (0 when inside or on boundary). */
    public static double pointToAabbDistanceSqr(Vec3 p, AABB box) {
        if (p == null || box == null) return Double.POSITIVE_INFINITY;
        double dx = p.x < box.minX ? box.minX - p.x : (p.x > box.maxX ? p.x - box.maxX : 0.0);
        double dy = p.y < box.minY ? box.minY - p.y : (p.y > box.maxY ? p.y - box.maxY : 0.0);
        double dz = p.z < box.minZ ? box.minZ - p.z : (p.z > box.maxZ ? p.z - box.maxZ : 0.0);
        return dx * dx + dy * dy + dz * dz;
    }

    /**
     * Exact squared shortest distance between line segment {@code [a, b]} and axis-aligned {@code box}.
     * Returns 0 if the segment intersects or starts/ends inside {@code box}.
     */
    public static double segmentToAabbDistanceSqr(Vec3 a, Vec3 b, AABB box) {
        if (a == null || b == null || box == null) return Double.POSITIVE_INFINITY;
        Vec3 d = b.subtract(a);
        double lenSqr = d.lengthSqr();
        if (lenSqr < 1.0E-12) return pointToAabbDistanceSqr(a, box);
        if (slabIntersects(a, d, box)) return 0.0;

        // Convex quadratic distance minimization via alternating projection between segment [0,1] and AABB,
        // plus exact check against all 12 box edges for skewed grazing rays.
        double best = Math.min(pointToAabbDistanceSqr(a, box), pointToAabbDistanceSqr(b, box));
        double t = 0.5;
        for (int iter = 0; iter < 6; iter++) {
            Vec3 pt = a.add(d.scale(t));
            Vec3 clamped = new Vec3(
                    Mth.clamp(pt.x, box.minX, box.maxX),
                    Mth.clamp(pt.y, box.minY, box.maxY),
                    Mth.clamp(pt.z, box.minZ, box.maxZ));
            t = Mth.clamp(clamped.subtract(a).dot(d) / lenSqr, 0.0, 1.0);
            best = Math.min(best, pointToAabbDistanceSqr(a.add(d.scale(t)), box));
        }
        best = Math.min(best, segmentAxisEdgesDistSqr(a, d, lenSqr, box));
        return best;
    }

    /** Axial distance [0, |b-a|] of the closest approach along segment {@code [a, b]} to {@code box}. */
    public static double segmentClosestProjection(Vec3 a, Vec3 b, AABB box) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1.0E-6) return 0.0;
        Vec3 dir = d.scale(1.0 / len);
        Vec3 center = box.getCenter();
        double t = Mth.clamp(center.subtract(a).dot(dir), 0.0, len);
        for (int iter = 0; iter < 4; iter++) {
            Vec3 pt = a.add(dir.scale(t));
            Vec3 clamped = new Vec3(
                    Mth.clamp(pt.x, box.minX, box.maxX),
                    Mth.clamp(pt.y, box.minY, box.maxY),
                    Mth.clamp(pt.z, box.minZ, box.maxZ));
            t = Mth.clamp(clamped.subtract(a).dot(dir), 0.0, len);
        }
        return t;
    }

    private static boolean slabIntersects(Vec3 a, Vec3 d, AABB box) {
        double tMin = 0.0, tMax = 1.0;
        double[] orig = {a.x, a.y, a.z};
        double[] dir = {d.x, d.y, d.z};
        double[] min = {box.minX, box.minY, box.minZ};
        double[] max = {box.maxX, box.maxY, box.maxZ};
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dir[i]) < 1.0E-12) {
                if (orig[i] < min[i] || orig[i] > max[i]) return false;
            } else {
                double inv = 1.0 / dir[i];
                double t1 = (min[i] - orig[i]) * inv;
                double t2 = (max[i] - orig[i]) * inv;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return false;
            }
        }
        return true;
    }

    private static double segmentAxisEdgesDistSqr(Vec3 a, Vec3 d, double lenSqr, AABB b) {
        double best = Double.POSITIVE_INFINITY;
        double[] xs = {b.minX, b.maxX}, ys = {b.minY, b.maxY}, zs = {b.minZ, b.maxZ};
        for (double y : ys) for (double z : zs)
            best = Math.min(best, segSegDistSqr(a, d, lenSqr, new Vec3(b.minX, y, z), new Vec3(b.maxX - b.minX, 0, 0)));
        for (double x : xs) for (double z : zs)
            best = Math.min(best, segSegDistSqr(a, d, lenSqr, new Vec3(x, b.minY, z), new Vec3(0, b.maxY - b.minY, 0)));
        for (double x : xs) for (double y : ys)
            best = Math.min(best, segSegDistSqr(a, d, lenSqr, new Vec3(x, y, b.minZ), new Vec3(0, 0, b.maxZ - b.minZ)));
        return best;
    }

    private static double segSegDistSqr(Vec3 p1, Vec3 d1, double a, Vec3 p2, Vec3 d2) {
        double e = d2.lengthSqr();
        Vec3 r = p1.subtract(p2);
        if (e < 1.0E-12) {
            double s = Mth.clamp(-d1.dot(r) / a, 0.0, 1.0);
            return p1.add(d1.scale(s)).distanceToSqr(p2);
        }
        double c = d1.dot(r), f = d2.dot(r), b = d1.dot(d2);
        double denom = a * e - b * b;
        double s = denom > 1.0E-12 ? Mth.clamp((b * f - c * e) / denom, 0.0, 1.0) : 0.0;
        double t = (b * s + f) / e;
        if (t < 0.0) {
            t = 0.0;
            s = Mth.clamp(-c / a, 0.0, 1.0);
        } else if (t > 1.0) {
            t = 1.0;
            s = Mth.clamp((b - c) / a, 0.0, 1.0);
        }
        return p1.add(d1.scale(s)).distanceToSqr(p2.add(d2.scale(t)));
    }

    /**
     * Singularity-free orthonormal basis around {@code axis}. When {@code axis} approaches ±Y zenith/nadir,
     * smoothly anchors horizontal orientation using {@code fallbackYawDeg} so shields/tubes never twist 90-180 degrees.
     */
    public static Basis obbBasis(Vec3 axis, float fallbackYawDeg) {
        Vec3 fwd = axis != null && axis.lengthSqr() > 1.0E-8 ? axis.normalize() : Vec3.directionFromRotation(0, fallbackYawDeg);
        double rad = Math.toRadians(fallbackYawDeg);
        Vec3 yawRight = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
        Vec3 crossUp = fwd.cross(new Vec3(0, 1, 0));
        double horizLen = Math.sqrt(crossUp.lengthSqr());
        Vec3 right;
        if (horizLen > 1.0E-3) {
            right = crossUp.scale(1.0 / horizLen);
            if (horizLen < 0.15) {
                double blend = horizLen / 0.15;
                Vec3 projYawRight = yawRight.subtract(fwd.scale(yawRight.dot(fwd)));
                if (projYawRight.lengthSqr() > 1.0E-6) {
                    projYawRight = projYawRight.normalize();
                    if (right.dot(projYawRight) < 0) projYawRight = projYawRight.scale(-1);
                    right = projYawRight.lerp(right, blend).normalize();
                }
            }
        } else {
            Vec3 proj = yawRight.subtract(fwd.scale(yawRight.dot(fwd)));
            right = proj.lengthSqr() > 1.0E-6 ? proj.normalize() : new Vec3(1, 0, 0);
        }
        Vec3 up = right.cross(fwd).normalize();
        return new Basis(fwd, right, up);
    }

    public static ObbSpec frontalBarrierSpec(String skill, double halfWidth) {
        return switch (skill) {
            case "nitrogen_shield_line" -> new ObbSpec(Math.max(2.2, halfWidth * 1.35), Math.max(1.5, halfWidth * 0.95), 0.48);
            case "nitrogen_bulwark" -> new ObbSpec(Math.max(2.0, halfWidth * 1.25), Math.max(1.6, halfWidth * 1.05), 0.55);
            case "aero_pressure_wall" -> new ObbSpec(Math.max(2.1, halfWidth * 1.30), Math.max(1.5, halfWidth * 0.95), 0.50);
            case "dark_shard_screen" -> new ObbSpec(Math.max(1.8, halfWidth * 1.20), Math.max(1.4, halfWidth * 0.95), 0.42);
            default -> new ObbSpec(Math.max(1.5, halfWidth * 1.15), Math.max(1.3, halfWidth * 0.95), 0.42);
        };
    }

    public static boolean obbContains(Vec3 center, Basis b, ObbSpec spec, Vec3 p) {
        Vec3 rel = p.subtract(center);
        return Math.abs(rel.dot(b.right)) <= spec.halfWidth
                && Math.abs(rel.dot(b.up)) <= spec.halfHeight
                && Math.abs(rel.dot(b.forward)) <= spec.halfThickness;
    }

    public static Optional<Vec3> obbClip(Vec3 center, Basis b, ObbSpec spec, Vec3 from, Vec3 to) {
        Vec3 rel0 = from.subtract(center);
        Vec3 d = to.subtract(from);
        double[] orig = {rel0.dot(b.right), rel0.dot(b.up), rel0.dot(b.forward)};
        double[] dir = {d.dot(b.right), d.dot(b.up), d.dot(b.forward)};
        double[] ext = {spec.halfWidth, spec.halfHeight, spec.halfThickness};
        double tMin = 0.0, tMax = 1.0;
        for (int i = 0; i < 3; i++) {
            if (Math.abs(dir[i]) < 1.0E-12) {
                if (Math.abs(orig[i]) > ext[i]) return Optional.empty();
            } else {
                double inv = 1.0 / dir[i];
                double t1 = (-ext[i] - orig[i]) * inv;
                double t2 = (ext[i] - orig[i]) * inv;
                if (t1 > t2) { double tmp = t1; t1 = t2; t2 = tmp; }
                tMin = Math.max(tMin, t1);
                tMax = Math.min(tMax, t2);
                if (tMin > tMax) return Optional.empty();
            }
        }
        return Optional.of(from.add(d.scale(tMin)));
    }

    public static Vec3[] obbCorners(Vec3 center, Basis b, ObbSpec spec) {
        Vec3[] pts = new Vec3[8];
        for (int i = 0; i < 8; i++) {
            double sx = (i & 1) == 0 ? -spec.halfWidth : spec.halfWidth;
            double sy = (i & 2) == 0 ? -spec.halfHeight : spec.halfHeight;
            double sz = (i & 4) == 0 ? -spec.halfThickness : spec.halfThickness;
            pts[i] = center.add(b.right.scale(sx)).add(b.up.scale(sy)).add(b.forward.scale(sz));
        }
        return pts;
    }

    public static AABB obbEnclosingAabb(Vec3 center, Basis b, ObbSpec spec) {
        double hx = Math.abs(b.right.x) * spec.halfWidth + Math.abs(b.up.x) * spec.halfHeight + Math.abs(b.forward.x) * spec.halfThickness;
        double hy = Math.abs(b.right.y) * spec.halfWidth + Math.abs(b.up.y) * spec.halfHeight + Math.abs(b.forward.y) * spec.halfThickness;
        double hz = Math.abs(b.right.z) * spec.halfWidth + Math.abs(b.up.z) * spec.halfHeight + Math.abs(b.forward.z) * spec.halfThickness;
        return new AABB(center.x - hx, center.y - hy, center.z - hz, center.x + hx, center.y + hy, center.z + hz);
    }
}
