package cn.academy.port.highpower.core;

import java.util.List;
import java.util.Optional;

/**
 * NEW221's fixed, finite mirror geometry. No Minecraft state or collision callbacks live here.
 * The stored normal points in the entering ray's direction: n = unit(incoming - outgoing).
 * A valid front-face entry therefore has direction dot normal > 0.
 */
public final class FoldedMirrorGeometry {
    public static final double RADIUS_X = .45, RADIUS_Y = .65, THICKNESS = .04;
    public static final double BEAM_RADIUS = .035, MAX_LENGTH = 40;
    public static final int MAX_REFLECTIONS = 2, MAX_SEGMENTS = 3;
    public static final double WORLD_LIMIT = 30_000_000;
    /** Below this incidence even a centered conservative beam footprint cannot fit the mirror. */
    public static final double MIN_INCIDENCE = BEAM_RADIUS / RADIUS_X;
    private static final double UNIT_TOLERANCE = 1e-10;

    public record Vector(double x, double y, double z) {
        public boolean finite() {
            return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z)
                    && Math.abs(x) <= WORLD_LIMIT && Math.abs(y) <= WORLD_LIMIT && Math.abs(z) <= WORLD_LIMIT;
        }
        public Vector add(Vector other) { return new Vector(x + other.x, y + other.y, z + other.z); }
        public Vector subtract(Vector other) { return new Vector(x - other.x, y - other.y, z - other.z); }
        public Vector scale(double factor) { return new Vector(x * factor, y * factor, z * factor); }
        public double dot(Vector other) { return x * other.x + y * other.y + z * other.z; }
        public Vector cross(Vector other) {
            return new Vector(y * other.z - z * other.y, z * other.x - x * other.z, x * other.y - y * other.x);
        }
        public double length() { return Math.hypot(Math.hypot(x, y), z); }
    }

    /** Immutable after release; invalid normals are rejected rather than repaired or invented. */
    public record Plate(int index, Vector center, Vector normal) {
        public Plate {
            if (index < 0 || index >= MAX_REFLECTIONS || !valid(center) || !isUnit(normal))
                throw new IllegalArgumentException("Invalid fixed mirror plate");
            Vector extent = extent(frameFor(normal));
            if (!center.subtract(extent).finite() || !center.add(extent).finite())
                throw new IllegalArgumentException("Fixed mirror volume exceeds world bounds");
        }
    }

    public record Frame(Vector normal, Vector right, Vector up) {}
    /** Axis-aligned bounds enclose every point in the full oriented elliptical slab. */
    public record Bounds(Vector min, Vector max) {}
    public enum ContactKind { REFLECT, BACK_FACE, EDGE, GRAZING }
    /** A rejected physical plane contact has no reflectedDirection. */
    public record Contact(int plateIndex, double distance, Vector point,
                          Vector reflectedDirection, ContactKind kind) {
        public boolean reflects() { return kind == ContactKind.REFLECT; }
    }

    private FoldedMirrorGeometry() {}

    /** A zero, non-finite or out-of-bounds input never receives a made-up direction. */
    public static Optional<Vector> unit(Vector vector) {
        if (!valid(vector)) return Optional.empty();
        double length = vector.length();
        if (!(length > 0) || !Double.isFinite(length)) return Optional.empty();
        Vector result = new Vector(vector.x / length, vector.y / length, vector.z / length);
        return isUnit(result) ? Optional.of(result) : Optional.empty();
    }

    /**
     * Fixes the plane orientation when editing is released. Inputs are propagation directions,
     * not positions. Straight-through/degenerate and grazing choices are rejected.
     */
    public static Optional<Vector> normalFor(Vector incoming, Vector outgoing) {
        Optional<Vector> first = unit(incoming), second = unit(outgoing);
        if (first.isEmpty() || second.isEmpty()) return Optional.empty();
        Optional<Vector> normal = unit(first.get().subtract(second.get()));
        if (normal.isEmpty() || !(first.get().dot(normal.get()) > MIN_INCIDENCE)) return Optional.empty();
        return normal;
    }

    /** The identical basis is used for reflection containment, placement and rendering. */
    public static Frame frame(Plate plate) {
        if (plate == null) throw new IllegalArgumentException("Missing fixed mirror plate");
        return frameFor(plate.normal);
    }

    public static Vector perimeterPoint(Plate plate, double angle) {
        if (!Double.isFinite(angle)) throw new IllegalArgumentException("Non-finite mirror perimeter angle");
        Frame frame = frame(plate);
        return plate.center.add(frame.right.scale(RADIUS_X * Math.cos(angle)))
                .add(frame.up.scale(RADIUS_Y * Math.sin(angle)));
    }

    public static Bounds volumeBounds(Plate plate) {
        Vector extent = extent(frame(plate));
        return new Bounds(plate.center.subtract(extent), plate.center.add(extent));
    }

    /**
     * True center-plane intersection of a unit ray. Distance is measured from the supplied
     * origin, which this function never moves. Exact endpoints and zero-distance contacts are
     * retained so native blockers at distance <= contact.distance always win in the caller.
     *
     * The beam's plane footprint is enclosed by a circle of radius r / abs(direction dot n).
     * By the triangle inequality in elliptical coordinates, rho + projectedRadius / RADIUS_X
     * < 1 guarantees that the entire footprint fits. This conservative test rejects the rim
     * and grazing beams; it never expands the visible ellipse or grants a corner reflection.
     */
    public static Optional<Contact> contact(Plate plate, Vector origin, Vector direction, double maxDistance) {
        if (plate == null || !valid(origin) || !isUnit(direction)
                || !Double.isFinite(maxDistance) || maxDistance < 0 || maxDistance > MAX_LENGTH)
            return Optional.empty();
        double incidence = direction.dot(plate.normal);
        if (incidence == 0) return Optional.empty();
        double distance = plate.center.subtract(origin).dot(plate.normal) / incidence;
        if (!Double.isFinite(distance) || distance < 0 || distance > maxDistance) return Optional.empty();
        // Normalize signed zero only. No positive epsilon is removed from distance or world space.
        if (distance == 0) distance = 0;
        Vector point = origin.add(direction.scale(distance));
        if (!point.finite()) return Optional.empty();
        Frame basis = frame(plate);
        Vector local = point.subtract(plate.center);
        double rho = Math.hypot(local.dot(basis.right) / RADIUS_X, local.dot(basis.up) / RADIUS_Y);
        if (!(rho <= 1)) return Optional.empty();
        ContactKind kind;
        Vector reflected = null;
        if (incidence < 0) kind = ContactKind.BACK_FACE;
        else if (!(incidence > MIN_INCIDENCE)) kind = ContactKind.GRAZING;
        else if (!(rho + BEAM_RADIUS / incidence / RADIUS_X < 1)) kind = ContactKind.EDGE;
        else {
            // Geometric law of reflection, using the stored normal rather than any target aim.
            reflected = unit(direction.subtract(plate.normal.scale(2 * incidence))).orElse(null);
            if (reflected == null) return Optional.empty();
            kind = ContactKind.REFLECT;
        }
        return Optional.of(new Contact(plate.index, distance, point, reflected, kind));
    }

    /**
     * Returns the first physical plate, including back faces, rims and already visited plates.
     * The caller rejects a visited plate instead of skipping it and damaging something behind.
     * Only the just-crossed plate's exactly zero-distance event is excluded. There is no origin
     * offset, epsilon jump, or skipped native obstruction interval after a reflection.
     */
    public static Optional<Contact> nearestContact(List<Plate> plates, Vector origin, Vector direction,
                                                    double maxDistance, int justCrossedPlate) {
        return nearestContact(plates, origin, direction, maxDistance, justCrossedPlate, false);
    }

    /**
     * Continuation from an actual reflection uses its exact point/direction as a departure
     * witness. Floating-point reconstruction of that same plane may otherwise leave a tiny
     * positive residual at distant coordinates. The known departure event alone is excluded:
     * the origin is unchanged, every other plate is tested, and native collision queries must
     * still start at this origin with their full distance. An unrelated witness grants nothing.
     */
    public static Optional<Contact> nearestContact(List<Plate> plates, Vector origin, Vector direction,
                                                    double maxDistance, Contact justCrossed) {
        boolean departing = justCrossed != null && justCrossed.reflects()
                && justCrossed.point.equals(origin) && justCrossed.reflectedDirection.equals(direction);
        return nearestContact(plates, origin, direction, maxDistance,
                departing ? justCrossed.plateIndex : -1, departing);
    }

    private static Optional<Contact> nearestContact(List<Plate> plates, Vector origin, Vector direction,
                                                     double maxDistance, int justCrossedPlate,
                                                     boolean knownDeparture) {
        if (plates == null || plates.size() > MAX_REFLECTIONS || justCrossedPlate < -1
                || justCrossedPlate >= MAX_REFLECTIONS) return Optional.empty();
        int seen = 0;
        Contact nearest = null;
        for (Plate plate : plates) {
            if (plate == null || (seen & (1 << plate.index)) != 0) return Optional.empty();
            seen |= 1 << plate.index;
            if (knownDeparture && plate.index == justCrossedPlate) continue;
            Optional<Contact> candidate = contact(plate, origin, direction, maxDistance);
            if (candidate.isEmpty()) continue;
            Contact hit = candidate.get();
            if (hit.plateIndex == justCrossedPlate && hit.distance == 0) continue;
            if (nearest == null || hit.distance < nearest.distance
                    || hit.distance == nearest.distance && hit.plateIndex < nearest.plateIndex)
                nearest = hit;
        }
        return Optional.ofNullable(nearest);
    }

    private static boolean valid(Vector vector) { return vector != null && vector.finite(); }
    private static boolean isUnit(Vector vector) {
        return valid(vector) && Math.abs(vector.length() - 1) <= UNIT_TOLERANCE;
    }
    private static Frame frameFor(Vector normal) {
        double horizontal = Math.hypot(normal.x, normal.z);
        Vector right = horizontal > 0 ? new Vector(normal.z / horizontal, 0, -normal.x / horizontal)
                : new Vector(1, 0, 0);
        return new Frame(normal, right, normal.cross(right));
    }
    private static Vector extent(Frame frame) {
        return new Vector(extentAxis(frame.right.x, frame.up.x, frame.normal.x),
                extentAxis(frame.right.y, frame.up.y, frame.normal.y),
                extentAxis(frame.right.z, frame.up.z, frame.normal.z));
    }
    private static double extentAxis(double right, double up, double normal) {
        return Math.hypot(RADIUS_X * right, RADIUS_Y * up) + THICKNESS / 2 * Math.abs(normal);
    }
}
