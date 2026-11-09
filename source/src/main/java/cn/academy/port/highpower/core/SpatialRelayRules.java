package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Cells;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.math.BigDecimal;
import java.util.UUID;

/** NEW343 economics, exact bounds and closed immutable ordinary-item proof primitives. */
public final class SpatialRelayRules {
    public static final double RANGE = SkillTuning.number("spatial_relay", "reach"), GATHER_RADIUS = 2;
    public static final int MAX_ITEMS = SkillTuning.integer("spatial_relay", "max_targets"), MAX_LEGS = 48, PREPARATION_TICKS = SkillTuning.integer("spatial_relay", "duration_ticks"),
            HOLD_TICKS = 6, REQUEST_FENCE = 4, MAX_CELLS = 32768;
    private static final ContinuousHighPowerGrowth.Curve CP =
            ContinuousHighPowerGrowth.curve("4", "2", "1", ".25", ".025");
    private SpatialRelayRules() {}
    public static double cpCost(BigDecimal q) { return CP.value(q).doubleValue(); }
    public record Proof(UUID owner, UUID entity, UUID nonce, String item, int count) {
        public boolean valid() {
            return owner != null && entity != null && nonce != null && !nonce.equals(new UUID(0, 0))
                    && item != null && item.startsWith("minecraft:") && item.length() > 10 && count > 0 && count <= 99;
        }
    }
    public static boolean exactProof(Proof expected, Proof actual) {
        return expected != null && actual != null && expected.valid() && actual.valid() && expected.equals(actual);
    }
    public static boolean withinRange(Point a, Point b) { return within(a, b, RANGE); }
    public static boolean withinGather(Point a, Point b) { return within(a, b, GATHER_RADIUS); }
    public static boolean positiveLeg(Point a, Point b) {
        return withinRange(a, b) && (a.x() != b.x() || a.y() != b.y() || a.z() != b.z());
    }
    private static boolean within(Point a, Point b, double radius) {
        if (a == null || b == null || !a.finite() || !b.finite()) return false;
        BigDecimal x = exact(a.x()).subtract(exact(b.x())), y = exact(a.y()).subtract(exact(b.y())),
                z = exact(a.z()).subtract(exact(b.z()));
        return x.multiply(x).add(y.multiply(y)).add(z.multiply(z))
                .compareTo(BigDecimal.valueOf(radius * radius)) <= 0;
    }
    public static boolean insideBorder(Box body, double minX, double minZ, double maxX, double maxZ) {
        return RecoveryMarkGeometry.insideBorder(body, minX, minZ, maxX, maxZ);
    }
    public static boolean itemBody(Box body) {
        return body != null && body.finite() && body.maxX() > body.minX() && body.maxY() > body.minY()
                && body.maxZ() > body.minZ() && body.maxX() - body.minX() <= .25000001
                && body.maxY() - body.minY() <= .25000001 && body.maxZ() - body.minZ() <= .25000001;
    }
    public static Box translatedBody(Box body, Point source, Point destination) {
        if (!itemBody(body) || !withinRange(source, destination)) throw new IllegalArgumentException("Invalid relay translation");
        if (!body.equals(bodyAt(source))) throw new IllegalArgumentException("Non-native relay body");
        return bodyAt(destination);
    }
    /** Entity.setPos recomputes each quarter-block face at the destination; subtraction/addition is not equivalent. */
    public static Box bodyAt(Point position) {
        if (position == null || !position.finite()) throw new IllegalArgumentException("Invalid relay position");
        return new Box(position.x() - .125, position.y(), position.z() - .125,
                position.x() + .125, position.y() + .25, position.z() + .125);
    }
    public static Sweep sweep(Box body, Point source, Point destination) {
        Box moved = translatedBody(body, source, destination);
        Box bounds = new Box(Math.min(body.minX(), moved.minX()), Math.min(body.minY(), moved.minY()),
                Math.min(body.minZ(), moved.minZ()), Math.max(body.maxX(), moved.maxX()),
                Math.max(body.maxY(), moved.maxY()), Math.max(body.maxZ(), moved.maxZ()));
        Cells cells = cells((int)Math.floor(Math.nextDown(bounds.minX())) - 1,
                (int)Math.floor(Math.nextDown(bounds.minY())) - 1, (int)Math.floor(Math.nextDown(bounds.minZ())) - 1,
                (int)Math.floor(Math.nextUp(bounds.maxX())) + 1, (int)Math.floor(Math.nextUp(bounds.maxY())) + 1,
                (int)Math.floor(Math.nextUp(bounds.maxZ())) + 1);
        return new Sweep(body, destination, moved, bounds, cells);
    }
    public static Cells cells(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long x = (long)maxX - minX + 1, y = (long)maxY - minY + 1, z = (long)maxZ - minZ + 1;
        if (x <= 0 || y <= 0 || z <= 0 || x > MAX_CELLS || y > MAX_CELLS || z > MAX_CELLS || x * y * z > MAX_CELLS)
            throw new IllegalArgumentException("Relay scan exceeds cell budget");
        return new Cells(minX, minY, minZ, maxX, maxY, maxZ, (int)(x * y * z));
    }
    private static BigDecimal exact(double value) { return new BigDecimal(value); }
    /** Exact face interpolation includes both native endpoints even when their representable extents differ by an ULP. */
    public static final class Sweep {
        private final Box body, destinationBody, bounds;
        private final Point destination;
        private final Cells cells;
        private Sweep(Box body, Point destination, Box destinationBody, Box bounds, Cells cells) {
            this.body = body; this.destination = destination; this.destinationBody = destinationBody;
            this.bounds = bounds; this.cells = cells;
        }
        public Point destination() { return destination; }
        public Box destinationBody() { return destinationBody; }
        public Box bounds() { return bounds; }
        public Cells cells() { return cells; }
        /** Strict volume overlap; tangency remains clear, and thin obstacles cannot disappear between samples. */
        public boolean intersects(Box obstacle) {
            if (obstacle == null || !obstacle.finite()) throw new IllegalArgumentException("Invalid relay obstacle");
            if (bounds.maxX() <= obstacle.minX() || bounds.minX() >= obstacle.maxX()
                    || bounds.maxY() <= obstacle.minY() || bounds.minY() >= obstacle.maxY()
                    || bounds.maxZ() <= obstacle.minZ() || bounds.minZ() >= obstacle.maxZ()) return false;
            double[][] axes = {
                    {body.minX(), body.maxX(), destinationBody.minX(), destinationBody.maxX(), obstacle.minX(), obstacle.maxX()},
                    {body.minY(), body.maxY(), destinationBody.minY(), destinationBody.maxY(), obstacle.minY(), obstacle.maxY()},
                    {body.minZ(), body.maxZ(), destinationBody.minZ(), destinationBody.maxZ(), obstacle.minZ(), obstacle.maxZ()}};
            Fraction[] interval = {new Fraction(BigDecimal.ZERO, BigDecimal.ONE), new Fraction(BigDecimal.ONE, BigDecimal.ONE)};
            for (double[] axis : axes) {
                if (!face(interval, axis[1], axis[3], axis[4], true)
                        || !face(interval, axis[0], axis[2], axis[5], false)) return false;
            }
            return interval[0].compareTo(interval[1]) < 0;
        }
        private static boolean face(Fraction[] interval, double from, double to, double boundary, boolean greater) {
            if (from == to) return greater ? from > boundary : from < boundary;
            // Exact binary values, including exact endpoint subtraction; a rounded native delta would reopen the ULP gap.
            BigDecimal delta = exact(to).subtract(exact(from));
            BigDecimal numerator = exact(boundary).subtract(exact(from));
            boolean increasing = delta.signum() > 0;
            if (!increasing) { numerator = numerator.negate(); delta = delta.negate(); }
            Fraction contact = new Fraction(numerator, delta);
            if (greater == increasing) {
                if (contact.compareTo(interval[0]) > 0) interval[0] = contact;
            } else if (contact.compareTo(interval[1]) < 0) interval[1] = contact;
            return interval[0].compareTo(interval[1]) < 0;
        }
        private record Fraction(BigDecimal numerator, BigDecimal denominator) {
            int compareTo(Fraction other) {
                return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
            }
        }
    }
}
