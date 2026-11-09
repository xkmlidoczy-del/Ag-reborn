package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * NEW252's six real, finite, vertical rectangles. The same immutable capture supplies the
 * server and renderer; a forward cone, enclosing box or projectile direction is never a plate.
 * All angles use Minecraft yaw degrees: zero is +Z and positive ninety is -X.
 */
public final class ZeroGapArmorGeometry {
    public static final int PLATE_COUNT = 6, ACTIVE_PLATE_COUNT = 3;
    public static final double RADIUS = 1.25, WIDTH = .9, HEIGHT = 1.3, CENTER_HEIGHT = 1.1;
    public static final double HALF_WIDTH = WIDTH / 2, HALF_HEIGHT = HEIGHT / 2;
    /** Visual slab extends inward from the physical front plane. */
    public static final double THICKNESS = .04, MAX_DISTANCE_FROM_FEET = 3, MAX_SEGMENT_LENGTH = 128;
    private static final Vector UP = new Vector(0, 1, 0);
    private static final double UNIT_TOLERANCE = 1e-10;

    public record Plate(int id, Vector center, Vector normal, boolean active) {
        public Plate {
            if (id < 0 || id >= PLATE_COUNT || !valid(center) || !horizontalUnit(normal))
                throw new IllegalArgumentException("Invalid NEW252 plate");
            Vector right = rightFor(normal);
            for (int side : new int[] {-1, 1}) for (int vertical : new int[] {-1, 1}) {
                Vector vertex = center.add(right.scale(side * HALF_WIDTH)).add(UP.scale(vertical * HALF_HEIGHT));
                if (!vertex.finite() || !vertex.subtract(normal.scale(THICKNESS)).finite())
                    throw new IllegalArgumentException("NEW252 plate exceeds world bounds");
            }
        }
        public int plateIndex() { return id; }
    }

    public record Frame(Vector normal, Vector right, Vector up) {}

    /** A defensive immutable copy retains numbered plates and their actual world positions. */
    public record Snapshot(Vector feet, double facingYawDegrees, int phaseTick, List<Plate> plates) {
        public Snapshot {
            if (!valid(feet) || !Double.isFinite(facingYawDegrees))
                throw new IllegalArgumentException("Invalid NEW252 pose");
            checkPhase(phaseTick);
            facingYawDegrees = normalizedYaw(facingYawDegrees);
            if (plates == null || plates.size() != PLATE_COUNT)
                throw new IllegalArgumentException("NEW252 capture needs six plates");
            int active = 0;
            for (int index = 0; index < PLATE_COUNT; index++) {
                Plate plate = plates.get(index);
                if (plate == null || plate.id != index)
                    throw new IllegalArgumentException("NEW252 capture needs ordered stable identities");
                for (Vector vertex : vertices(plate)) {
                    if (vertex.subtract(feet).length() > MAX_DISTANCE_FROM_FEET
                            || vertex.subtract(plate.normal.scale(THICKNESS)).subtract(feet).length() > MAX_DISTANCE_FROM_FEET)
                        throw new IllegalArgumentException("NEW252 capture exceeds three meters");
                }
                if (plate.active) active++;
            }
            if (active != ACTIVE_PLATE_COUNT)
                throw new IllegalArgumentException("NEW252 capture needs three facing plates");
            plates = List.copyOf(plates);
        }
        public int activeMask() {
            int mask = 0;
            for (Plate plate : plates) if (plate.active) mask |= 1 << plate.id;
            return mask;
        }
    }

    /** Fraction is the unshifted parameter on the original, closed input segment. */
    public record Contact(int plateId, double fraction, Vector point) {
        public Contact {
            if (plateId < 0 || plateId >= PLATE_COUNT || !Double.isFinite(fraction)
                    || fraction < 0 || fraction > 1 || !valid(point))
                throw new IllegalArgumentException("Invalid NEW252 contact");
        }
        public int plateIndex() { return plateId; }
    }

    private ZeroGapArmorGeometry() {}

    /** Persisted physical phase is an input, never an activation-relative or wall-clock counter. */
    public static Snapshot capture(Vector feet, double facingYawDegrees, ZeroGapArmorTiming timing) {
        if (timing == null) throw new IllegalArgumentException("Missing NEW252 physical phase");
        return capture(feet, facingYawDegrees, timing.phaseTick());
    }

    /** Also supports an explicitly synchronized phase; invalid counters are never silently reset. */
    public static Snapshot capture(Vector feet, double facingYawDegrees, int phaseTick) {
        if (!valid(feet) || !Double.isFinite(facingYawDegrees))
            throw new IllegalArgumentException("Invalid NEW252 pose");
        checkPhase(phaseTick);
        double yaw = normalizedYaw(facingYawDegrees);
        List<Integer> ranked = new ArrayList<>(PLATE_COUNT);
        for (int index = 0; index < PLATE_COUNT; index++) ranked.add(index);
        ranked.sort((first, second) -> compareFacing(first, second, phaseTick, yaw));
        int activeMask = 0;
        for (int index = 0; index < ACTIVE_PLATE_COUNT; index++) activeMask |= 1 << ranked.get(index);
        List<Plate> plates = new ArrayList<>(PLATE_COUNT);
        for (int index = 0; index < PLATE_COUNT; index++) {
            Vector normal = horizontalDirection(plateYawDegrees(phaseTick, index));
            Vector center = feet.add(UP.scale(CENTER_HEIGHT)).add(normal.scale(RADIUS));
            plates.add(new Plate(index, center, normal, (activeMask & (1 << index)) != 0));
        }
        return new Snapshot(feet, yaw, phaseTick, plates);
    }

    public static double plateYawDegrees(int phaseTick, int plateId) {
        checkPhase(phaseTick);
        if (plateId < 0 || plateId >= PLATE_COUNT) throw new IllegalArgumentException("Invalid NEW252 plate ID");
        return (phaseTick * (360.0 / ZeroGapArmorTiming.REVOLUTION_TICKS) + plateId * (360.0 / PLATE_COUNT)) % 360;
    }

    public static Frame frame(Plate plate) {
        if (plate == null) throw new IllegalArgumentException("Missing NEW252 plate");
        return new Frame(plate.normal, rightFor(plate.normal), UP);
    }

    /** Point on the physical rectangle, including its exact boundary. */
    public static Vector point(Plate plate, double right, double up) {
        if (!Double.isFinite(right) || !Double.isFinite(up) || Math.abs(right) > HALF_WIDTH || Math.abs(up) > HALF_HEIGHT)
            throw new IllegalArgumentException("Invalid NEW252 local point");
        Frame basis = frame(plate);
        return plate.center.add(basis.right.scale(right)).add(basis.up.scale(up));
    }

    public static List<Vector> vertices(Plate plate) {
        return List.of(point(plate, -HALF_WIDTH, -HALF_HEIGHT), point(plate, HALF_WIDTH, -HALF_HEIGHT),
                point(plate, HALF_WIDTH, HALF_HEIGHT), point(plate, -HALF_WIDTH, HALF_HEIGHT));
    }

    /** Broad-phase/render bounds only. These bounds are never used as contact coverage. */
    public static Bounds volumeBounds(Plate plate) {
        List<Vector> corners = vertices(plate);
        double minX = Double.POSITIVE_INFINITY, minY = Double.POSITIVE_INFINITY, minZ = Double.POSITIVE_INFINITY;
        double maxX = Double.NEGATIVE_INFINITY, maxY = Double.NEGATIVE_INFINITY, maxZ = Double.NEGATIVE_INFINITY;
        for (Vector corner : corners) for (Vector vertex : List.of(corner, corner.subtract(plate.normal.scale(THICKNESS)))) {
            minX = Math.min(minX, vertex.x()); minY = Math.min(minY, vertex.y()); minZ = Math.min(minZ, vertex.z());
            maxX = Math.max(maxX, vertex.x()); maxY = Math.max(maxY, vertex.y()); maxZ = Math.max(maxZ, vertex.z());
        }
        return new Bounds(new Vector(minX, minY, minZ), new Vector(maxX, maxY, maxZ));
    }

    /**
     * A closed front-plane crossing of one active rectangle. Strictly inside, rear-facing,
     * coplanar, zero-length and non-finite segments miss. There is no positive epsilon, swept
     * radius, corner expansion or shifted origin. Exact start/end contacts remain fractions
     * zero/one so the adapter can let terrain or body contacts at the same fraction win.
     */
    public static Optional<Contact> contact(Plate plate, Vector start, Vector end) {
        if (plate == null || !plate.active || !valid(start) || !valid(end)) return Optional.empty();
        Vector movement = end.subtract(start);
        double length = movement.length();
        if (!(length > 0) || length > MAX_SEGMENT_LENGTH) return Optional.empty();
        double front = start.subtract(plate.center).dot(plate.normal);
        double back = end.subtract(plate.center).dot(plate.normal);
        if (front < 0 || back > 0 || !(front > back)) return Optional.empty();
        double fraction = front == 0 ? 0 : back == 0 ? 1 : front / (front - back);
        Vector hit = fraction == 0 ? start : fraction == 1 ? end : start.add(movement.scale(fraction));
        if (!valid(hit)) return Optional.empty();
        Vector local = hit.subtract(plate.center);
        if (Math.abs(local.dot(rightFor(plate.normal))) > HALF_WIDTH || Math.abs(local.y()) > HALF_HEIGHT)
            return Optional.empty();
        return Optional.of(new Contact(plate.id, fraction, hit));
    }

    public static Optional<Contact> nearestContact(Snapshot snapshot, Vector start, Vector end) {
        return snapshot == null ? Optional.empty() : nearestContact(snapshot.plates, start, end);
    }

    /** Closest admitted contact, with stable lower plate ID for an exact equal-fraction tie. */
    public static Optional<Contact> nearestContact(List<Plate> plates, Vector start, Vector end) {
        if (plates == null || plates.size() > PLATE_COUNT) return Optional.empty();
        int seen = 0;
        Contact nearest = null;
        for (Plate plate : plates) {
            if (plate == null || (seen & (1 << plate.id)) != 0) return Optional.empty();
            seen |= 1 << plate.id;
            Optional<Contact> candidate = contact(plate, start, end);
            if (candidate.isEmpty()) continue;
            Contact hit = candidate.get();
            if (nearest == null || hit.fraction < nearest.fraction
                    || hit.fraction == nearest.fraction && hit.plateId < nearest.plateId) nearest = hit;
        }
        return Optional.ofNullable(nearest);
    }

    private static boolean valid(Vector vector) { return vector != null && vector.finite(); }
    private static boolean horizontalUnit(Vector vector) {
        return valid(vector) && vector.y() == 0 && Math.abs(vector.length() - 1) <= UNIT_TOLERANCE;
    }
    private static Vector rightFor(Vector normal) { return new Vector(normal.z(), 0, -normal.x()); }
    private static void checkPhase(int phaseTick) {
        if (phaseTick < 0 || phaseTick >= ZeroGapArmorTiming.REVOLUTION_TICKS)
            throw new IllegalArgumentException("Invalid persisted NEW252 phase");
    }
    private static double normalizedYaw(double yaw) {
        double normalized = yaw % 360;
        if (normalized < 0) normalized += 360;
        return normalized == 0 || normalized == 360 ? 0 : normalized;
    }
    private static int compareFacing(int first, int second, int phaseTick, double yaw) {
        if (first == second) return 0;
        double a = plateYawDegrees(phaseTick, first), b = plateYawDegrees(phaseTick, second);
        double midpoint = (Math.min(a, b) + Math.max(a, b)) / 2;
        double opposite = midpoint < 180 ? midpoint + 180 : midpoint - 180;
        // Every boundary is an exact integer degree (3-degree phase, 60-degree spacing).
        // Comparing yaw directly avoids subtraction rounding nextUp/nextDown into a false tie.
        if (yaw == midpoint || yaw == opposite) return Integer.compare(first, second);
        boolean lowerAngleCloser = midpoint < 180 ? yaw < midpoint || yaw > opposite
                : yaw > opposite && yaw < midpoint;
        return (a < b) == lowerAngleCloser ? -1 : 1;
    }
    private static Vector horizontalDirection(double yaw) {
        // Preserve exact cardinal planes, avoiding an artificial sin(pi) residual at endpoints.
        if (yaw == 0) return new Vector(0, 0, 1);
        if (yaw == 90) return new Vector(-1, 0, 0);
        if (yaw == 180) return new Vector(0, 0, -1);
        if (yaw == 270) return new Vector(1, 0, 0);
        double radians = Math.toRadians(yaw);
        return new Vector(-Math.sin(radians), 0, Math.cos(radians));
    }
}
