package cn.academy.port.highpower.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * NEW441's world-axis-aligned, finite rectangular pressure faces. All arithmetic is
 * double precision and relative to the frozen center; there are no Minecraft types,
 * point samples, hidden slab thickness, infinite planes, or numerical hit aprons.
 */
public final class VectorCageGeometry {
    public static final double HALF_WIDTH = 4, MIN_HALF_WIDTH = .25, HEIGHT = 3,
            BOTTOM_OFFSET = .05, RANGE = 56, WORLD_LIMIT = 30_000_000;
    public static final int WALLS = 4, NEGATIVE_X = 0, POSITIVE_X = 1, NEGATIVE_Z = 2, POSITIVE_Z = 3;

    public record Point(double x, double y, double z) {
        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Math.abs(x) <= WORLD_LIMIT && Math.abs(y) <= WORLD_LIMIT && Math.abs(z) <= WORLD_LIMIT;
        }
        public double distanceSquared(Point other) {
            double dx = x - other.x, dy = y - other.y, dz = z - other.z;
            return dx * dx + dy * dy + dz * dz;
        }
    }

    public record Box(double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        public boolean finite() {
            return new Point(minX, minY, minZ).finite() && new Point(maxX, maxY, maxZ).finite()
                    && minX < maxX && minY < maxY && minZ < maxZ;
        }
    }

    /** Center.y is the frozen base, not the midpoint or the owner's eye height. */
    public record Frame(Point center) {
        public Frame {
            if (!valid(center) || !boxAt(center, HALF_WIDTH).finite())
                throw new IllegalArgumentException("Invalid NEW441 frozen field");
        }
    }

    /** Wall tie order is -X,+X,-Z,+Z. Time is continuous within this real tick [0,1]. */
    public record Contact(double time, int wall, Point point, Point previousPoint) {}

    private VectorCageGeometry() {}

    public static Frame frame(Point center) { return new Frame(center); }

    /** Full immutable 8m x 8m x 3m field; useful for conservative terrain clearance. */
    public static Box bounds(Frame frame) { return sweptBounds(frame, HALF_WIDTH, HALF_WIDTH); }

    /** Broad phase only: the outer box includes empty interior space between the four faces. */
    public static Box sweptBounds(Frame frame, double fromHalfWidth, double toHalfWidth) {
        validateSweep(frame, fromHalfWidth, toHalfWidth);
        return boxAt(frame.center, fromHalfWidth);
    }

    /** Eight unique square-ring vertices: bottom ring, then top ring, in -X/-Z first order. */
    public static List<Point> vertices(Frame frame, double halfWidth) {
        validateSweep(frame, halfWidth, halfWidth);
        var result = new ArrayList<Point>(8);
        for (double y : new double[]{BOTTOM_OFFSET, BOTTOM_OFFSET + HEIGHT}) {
            result.add(world(frame, -halfWidth, y, -halfWidth));
            result.add(world(frame, halfWidth, y, -halfWidth));
            result.add(world(frame, halfWidth, y, halfWidth));
            result.add(world(frame, -halfWidth, y, halfWidth));
        }
        return List.copyOf(result);
    }

    /** Four corners of one actual finite face, bottom left, bottom right, top right, top left. */
    public static List<Point> faceVertices(Frame frame, int wall, double halfWidth) {
        validateSweep(frame, halfWidth, halfWidth);
        requireWall(wall);
        return List.of(facePoint(frame, wall, halfWidth, -halfWidth, BOTTOM_OFFSET),
                facePoint(frame, wall, halfWidth, halfWidth, BOTTOM_OFFSET),
                facePoint(frame, wall, halfWidth, halfWidth, BOTTOM_OFFSET + HEIGHT),
                facePoint(frame, wall, halfWidth, -halfWidth, BOTTOM_OFFSET + HEIGHT));
    }

    /** Both reference points bound every face vertex, and therefore every convex face point. */
    public static boolean withinRange(Frame frame, double halfWidth, Point castOrigin, Point owner) {
        if (!valid(castOrigin) || !valid(owner)) return false;
        try {
            for (Point vertex : vertices(frame, halfWidth))
                if (!pointWithinRange(vertex, castOrigin, owner)) return false;
            return true;
        } catch (IllegalArgumentException invalid) { return false; }
    }

    public static boolean contactWithinRange(Contact contact, Point castOrigin, Point owner) {
        return contact != null && Double.isFinite(contact.time) && contact.time >= 0 && contact.time <= 1
                && contact.wall >= 0 && contact.wall < WALLS
                && pointWithinRange(contact.point, castOrigin, owner)
                && pointWithinRange(contact.previousPoint, castOrigin, owner);
    }

    /** Earliest face wins; an exactly simultaneous corner keeps the lower wall index. */
    public static Optional<Contact> earliestContact(Frame frame, double fromHalfWidth,
            double toHalfWidth, Box box) {
        if (!queryValid(frame, fromHalfWidth, toHalfWidth, box)) return Optional.empty();
        Contact earliest = null;
        for (int wall = 0; wall < WALLS; wall++) {
            Contact contact = contact(frame, wall, fromHalfWidth, toHalfWidth, box);
            if (contact != null && (earliest == null || contact.time < earliest.time)) earliest = contact;
        }
        return Optional.ofNullable(earliest);
    }

    /**
     * Exact continuous moving-face/AABB intersection. For a fixed wall, contact is
     * equivalent to three closed intervals: the wall normal lies in the box, its
     * shrinking tangential span overlaps the box, and the fixed height overlaps.
     * Intersect those intervals in half-width space, then choose the largest valid
     * half-width (earliest time). This catches arbitrarily thin boxes between ticks.
     */
    public static Optional<Contact> earliestContact(Frame frame, int wall, double fromHalfWidth,
            double toHalfWidth, Box box) {
        if (wall < 0 || wall >= WALLS || !queryValid(frame, fromHalfWidth, toHalfWidth, box))
            return Optional.empty();
        return Optional.ofNullable(contact(frame, wall, fromHalfWidth, toHalfWidth, box));
    }

    private static Contact contact(Frame frame, int wall, double from, double to, Box box) {
        Point center = frame.center;
        double bottom = center.y + BOTTOM_OFFSET, top = center.y + (BOTTOM_OFFSET + HEIGHT);
        if (box.maxY < bottom || box.minY > top) return null;
        boolean xWall = wall == NEGATIVE_X || wall == POSITIVE_X;
        boolean negative = wall == NEGATIVE_X || wall == NEGATIVE_Z;
        double normalMin = xWall ? box.minX - center.x : box.minZ - center.z;
        double normalMax = xWall ? box.maxX - center.x : box.maxZ - center.z;
        double tangentMin = xWall ? box.minZ - center.z : box.minX - center.x;
        double tangentMax = xWall ? box.maxZ - center.z : box.maxX - center.x;
        double low = negative ? -normalMax : normalMin;
        double high = negative ? -normalMin : normalMax;
        double first = Math.min(from, high);
        double last = Math.max(to, Math.max(low, Math.max(tangentMin, -tangentMax)));
        if (first < last) return null;
        double time = from == to ? 0 : (from - first) / (from - to);
        double tangent = Math.max(Math.max(tangentMin, -first), Math.min(0, Math.min(tangentMax, first)));
        double y = Math.max(bottom, box.minY);
        double sign = negative ? -1 : 1;
        Point point = xWall ? new Point(center.x + sign * first, y, center.z + tangent)
                : new Point(center.x + tangent, y, center.z + sign * first);
        // The same material point follows the shrinking face linearly; its normalized
        // tangential coordinate stays fixed. This is a wall path, never a center ray.
        double previousTangent = tangent / first * from;
        Point previous = xWall ? new Point(center.x + sign * from, y, center.z + previousTangent)
                : new Point(center.x + previousTangent, y, center.z + sign * from);
        if (!valid(point) || !valid(previous) || !contains(box, point)
                || !Double.isFinite(time) || time < 0 || time > 1) return null;
        return new Contact(time, wall, point, previous);
    }

    private static boolean queryValid(Frame frame, double from, double to, Box box) {
        if (box == null || !box.finite()) return false;
        try { validateSweep(frame, from, to); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }
    private static void validateSweep(Frame frame, double from, double to) {
        if (frame == null || !Double.isFinite(from) || !Double.isFinite(to)
                || from > HALF_WIDTH || to < MIN_HALF_WIDTH || to > from)
            throw new IllegalArgumentException("Invalid NEW441 finite contraction");
    }
    private static void requireWall(int wall) {
        if (wall < 0 || wall >= WALLS) throw new IllegalArgumentException("Invalid NEW441 wall");
    }
    private static Box boxAt(Point center, double halfWidth) {
        return new Box(center.x - halfWidth, center.y + BOTTOM_OFFSET, center.z - halfWidth,
                center.x + halfWidth, center.y + (BOTTOM_OFFSET + HEIGHT), center.z + halfWidth);
    }
    private static Point facePoint(Frame frame, int wall, double h, double tangent, double y) {
        return switch (wall) {
            case NEGATIVE_X -> world(frame, -h, y, tangent);
            case POSITIVE_X -> world(frame, h, y, tangent);
            case NEGATIVE_Z -> world(frame, tangent, y, -h);
            case POSITIVE_Z -> world(frame, tangent, y, h);
            default -> throw new IllegalArgumentException("Invalid NEW441 wall");
        };
    }
    private static Point world(Frame frame, double x, double y, double z) {
        return new Point(frame.center.x + x, frame.center.y + y, frame.center.z + z);
    }
    private static boolean pointWithinRange(Point point, Point castOrigin, Point owner) {
        return valid(point) && valid(castOrigin) && valid(owner)
                && point.distanceSquared(castOrigin) <= RANGE * RANGE
                && point.distanceSquared(owner) <= RANGE * RANGE;
    }
    private static boolean contains(Box box, Point p) {
        return p.x >= box.minX && p.x <= box.maxX && p.y >= box.minY && p.y <= box.maxY
                && p.z >= box.minZ && p.z <= box.maxZ;
    }
    private static boolean valid(Point point) { return point != null && point.finite(); }
}
