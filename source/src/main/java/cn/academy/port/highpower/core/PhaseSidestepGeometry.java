package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Cells;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;

/** Exact straight swept-body intersection, without sampling, route search or a filled-sector mask. */
public final class PhaseSidestepGeometry {
    private PhaseSidestepGeometry() {}

    /** Minecraft yaw zero faces +Z, so its right is -X. Pitch is deliberately not an input. */
    public static Point destination(Point source, double yaw, boolean left) { return destination(source, yaw, left, PhaseSidestepRules.DISTANCE); }

    /** The same side step at a level-scaled distance; the distance must be one the caller admitted for this exact cast. */
    public static Point destination(Point source, double yaw, boolean left, double distanceMetres) {
        if (source == null || !source.finite() || !Double.isFinite(yaw))
            throw new IllegalArgumentException("Invalid NEW312 source or yaw");
        double angle = yaw % 360;
        if (angle >= 180) angle -= 360;
        if (angle < -180) angle += 360;
        double x, z;
        // Preserve exact cardinal axes, including vertical-look players whose server yaw is valid.
        if (angle == 0) { x = -1; z = 0; }
        else if (angle == 90) { x = 0; z = -1; }
        else if (angle == -90) { x = 0; z = 1; }
        else if (angle == -180) { x = 1; z = 0; }
        else {
            double radians = Math.toRadians(angle);
            x = -Math.cos(radians); z = -Math.sin(radians);
            double length = Math.hypot(x, z);
            x /= length; z /= length;
        }
        if (!Double.isFinite(distanceMetres) || distanceMetres < PhaseSidestepRules.DISTANCE - 1e-9 || distanceMetres > PhaseSidestepRules.MAX_DISTANCE + 1e-9)
            throw new IllegalArgumentException("NEW312 distance outside its level range");
        double distance = left ? -distanceMetres : distanceMetres;
        Point result = new Point(source.x() + x * distance, source.y(), source.z() + z * distance);
        if (!result.finite()) throw new IllegalArgumentException("NEW312 destination outside coordinate bounds");
        return result;
    }

    /** Only a yaw/side selection can create a sweep; callers cannot supply another endpoint. */
    public static Sweep sweep(Box body, Point source, double yaw, boolean left) { return sweep(body, source, yaw, left, PhaseSidestepRules.DISTANCE); }

    public static Sweep sweep(Box body, Point source, double yaw, boolean left, double distanceMetres) {
        if (body == null || !body.body()) throw new IllegalArgumentException("Invalid NEW312 body");
        Point destination = destination(source, yaw, left, distanceMetres);
        Box moved = SafeLandingProbeGeometry.translatedBody(body, source, destination);
        Box bounds = new Box(Math.min(body.minX(), moved.minX()), body.minY(),
                Math.min(body.minZ(), moved.minZ()), Math.max(body.maxX(), moved.maxX()),
                body.maxY(), Math.max(body.maxZ(), moved.maxZ()));
        if (!bounds.finite()) throw new IllegalArgumentException("Invalid NEW312 swept bounds");
        double epsilon = SafeLandingProbeGeometry.EPSILON;
        Cells cells = SafeLandingProbeGeometry.cells(
                (int)Math.floor(bounds.minX() - epsilon) - 1,
                (int)Math.floor(bounds.minY() - epsilon) - 1,
                (int)Math.floor(bounds.minZ() - epsilon) - 1,
                (int)Math.floor(bounds.maxX() + epsilon) + 1,
                (int)Math.floor(bounds.maxY() + epsilon) + 1,
                (int)Math.floor(bounds.maxZ() + epsilon) + 1);
        return new Sweep(body, destination, destination.x() - source.x(), destination.z() - source.z(), bounds, cells);
    }

    /** Immutable broad phase plus an analytic open-overlap interval against each actual native box. */
    public static final class Sweep {
        private final Box body, bounds;
        private final Point destination;
        private final double dx, dz;
        private final Cells cells;
        private Sweep(Box body, Point destination, double dx, double dz, Box bounds, Cells cells) {
            this.body = body; this.destination = destination; this.dx = dx; this.dz = dz;
            this.bounds = bounds; this.cells = cells;
        }
        public Point destination() { return destination; }
        public Box bounds() { return bounds; }
        public Cells cells() { return cells; }

        /** Tangency has no volume and is allowed, matching native AABB intersection semantics. */
        public boolean intersects(Box obstacle) {
            if (obstacle == null || !obstacle.finite()) throw new IllegalArgumentException("Invalid NEW312 collision box");
            if (body.maxY() <= obstacle.minY() || body.minY() >= obstacle.maxY()) return false;
            double enter = 0, exit = 1;
            if (dx == 0) {
                if (body.maxX() <= obstacle.minX() || body.minX() >= obstacle.maxX()) return false;
            } else {
                double first = (obstacle.minX() - body.maxX()) / dx;
                double last = (obstacle.maxX() - body.minX()) / dx;
                enter = Math.max(enter, Math.min(first, last));
                exit = Math.min(exit, Math.max(first, last));
                if (enter >= exit) return false;
            }
            if (dz == 0) {
                if (body.maxZ() <= obstacle.minZ() || body.minZ() >= obstacle.maxZ()) return false;
            } else {
                double first = (obstacle.minZ() - body.maxZ()) / dz;
                double last = (obstacle.maxZ() - body.minZ()) / dz;
                enter = Math.max(enter, Math.min(first, last));
                exit = Math.min(exit, Math.max(first, last));
            }
            return enter < exit;
        }
    }
}
