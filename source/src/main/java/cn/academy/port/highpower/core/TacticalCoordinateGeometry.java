package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Cells;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.math.BigDecimal;

/** Closed NEW353 tactical-coordinate geometry with an independent 64m budget.
 * Every historical landing, return-mark and exchange wrapper retains its original range.
 * Strict swept-box contact uses exact fallback arithmetic at floating-point boundaries.
 */
public final class TacticalCoordinateGeometry {
    public static final double RANGE = TacticalCoordinateRules.RANGE;
    public static final int MAX_CELLS = 131072, MAX_READS = 524288, MAX_SHAPE_BOXES = 262144;

    private TacticalCoordinateGeometry() {}

    /** Vertical displacement consumes the same fixed Euclidean range as horizontal displacement. */
    public static boolean withinRange(Point source, Point destination) {
        return source != null && destination != null && source.finite() && destination.finite()
                && exactDistanceSquared(source, destination).compareTo(BigDecimal.valueOf(RANGE * RANGE)) <= 0;
    }

    private static BigDecimal exactDistanceSquared(Point source, Point destination) {
        BigDecimal x = new BigDecimal(destination.x()).subtract(new BigDecimal(source.x()));
        BigDecimal y = new BigDecimal(destination.y()).subtract(new BigDecimal(source.y()));
        BigDecimal z = new BigDecimal(destination.z()).subtract(new BigDecimal(source.z()));
        return x.multiply(x).add(y.multiply(y)).add(z.multiply(z));
    }

    /** Preserve every face of the stored standing body, without shrinking to a point or center ray. */
    public static Box translatedBody(Box body, Point source, Point destination) {
        if (body == null || !body.body() || !withinRange(source, destination))
            throw new IllegalArgumentException("Invalid NEW353 tactical coordinate body translation");
        double dx = destination.x() - source.x(), dy = destination.y() - source.y(),
                dz = destination.z() - source.z();
        Box result = new Box(body.minX() + dx, body.minY() + dy, body.minZ() + dz,
                body.maxX() + dx, body.maxY() + dy, body.maxZ() + dz);
        if (!result.body()) throw new IllegalArgumentException("Invalid translated NEW353 tactical coordinate body");
        return result;
    }

    /** The supplied destination is retained exactly, including valid differences in floor height. */
    public static Sweep sweep(Box body, Point source, Point destination) {
        Box moved = translatedBody(body, source, destination);
        Box bounds = new Box(Math.min(body.minX(), moved.minX()), Math.min(body.minY(), moved.minY()),
                Math.min(body.minZ(), moved.minZ()), Math.max(body.maxX(), moved.maxX()),
                Math.max(body.maxY(), moved.maxY()), Math.max(body.maxZ(), moved.maxZ()));
        if (!bounds.finite()) throw new IllegalArgumentException("Invalid NEW353 tactical coordinate swept bounds");
        // Closed, conservative native-shape apron only. Collision geometry is never inflated,
        // epsilon-tested, sampled or replaced by this broad-phase rectangular envelope.
        Cells cells = cells((int)Math.floor(Math.nextDown(bounds.minX())) - 1,
                (int)Math.floor(Math.nextDown(bounds.minY())) - 1,
                (int)Math.floor(Math.nextDown(bounds.minZ())) - 1,
                (int)Math.floor(Math.nextUp(bounds.maxX())) + 1,
                (int)Math.floor(Math.nextUp(bounds.maxY())) + 1,
                (int)Math.floor(Math.nextUp(bounds.maxZ())) + 1);
        return new Sweep(body, destination, moved, destination.x() - source.x(),
                destination.y() - source.y(), destination.z() - source.z(), bounds, cells);
    }

    /** Inclusive native-cell work budget; malformed/overflowing scans fail before any world read. */
    public static Cells cells(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        long x = (long)maxX - minX + 1, y = (long)maxY - minY + 1, z = (long)maxZ - minZ + 1;
        if (x <= 0 || y <= 0 || z <= 0 || x > MAX_CELLS || y > MAX_CELLS || z > MAX_CELLS
                || x * y * z > MAX_CELLS)
            throw new IllegalArgumentException("NEW353 tactical coordinate scan exceeds cell budget");
        return new Cells(minX, minY, minZ, maxX, maxY, maxZ, (int)(x * y * z));
    }

    /** Immutable swept AABB, represented by the intersection of three open overlap-time slabs. */
    public static final class Sweep {
        private final Box body, destinationBody, bounds;
        private final Point destination;
        private final double dx, dy, dz;
        private final Cells cells;

        private Sweep(Box body, Point destination, Box destinationBody, double dx, double dy, double dz,
                      Box bounds, Cells cells) {
            this.body = body; this.destination = destination; this.destinationBody = destinationBody;
            this.dx = dx; this.dy = dy; this.dz = dz; this.bounds = bounds; this.cells = cells;
        }

        public Point destination() { return destination; }
        public Box destinationBody() { return destinationBody; }
        public Box bounds() { return bounds; }
        public Cells cells() { return cells; }

        /** Native boxes must overlap in volume; pure face, edge, corner and endpoint tangency pass. */
        public boolean intersects(Box obstacle) {
            if (obstacle == null || !obstacle.finite())
                throw new IllegalArgumentException("Invalid NEW353 tactical coordinate collision box");
            // Outward-rounded arithmetic bounds certify ordinary cases cheaply. They do not
            // change physical geometry: uncertain strict contacts use exact rational comparison.
            double[] interval = {0, 0, 1, 1}; // enter low/high, exit low/high
            if (!clip(interval, body.minX(), body.maxX(), obstacle.minX(), obstacle.maxX(), dx)
                    || !clip(interval, body.minY(), body.maxY(), obstacle.minY(), obstacle.maxY(), dy)
                    || !clip(interval, body.minZ(), body.maxZ(), obstacle.minZ(), obstacle.maxZ(), dz)) return false;
            return interval[1] < interval[2] || exactIntersection(obstacle);
        }

        private static boolean clip(double[] interval, double min, double max,
                                    double obstacleMin, double obstacleMax, double delta) {
            if (delta == 0) return max > obstacleMin && min < obstacleMax;
            double entry = delta > 0 ? obstacleMin - max : obstacleMax - min;
            double exit = delta > 0 ? obstacleMax - min : obstacleMin - max;
            interval[0] = Math.max(interval[0], quotientLow(entry, delta));
            interval[1] = Math.max(interval[1], quotientHigh(entry, delta));
            interval[2] = Math.min(interval[2], quotientLow(exit, delta));
            interval[3] = Math.min(interval[3], quotientHigh(exit, delta));
            return interval[0] < interval[3];
        }

        private static double quotientLow(double roundedDifference, double delta) {
            return Math.nextDown((delta > 0 ? Math.nextDown(roundedDifference)
                    : Math.nextUp(roundedDifference)) / delta);
        }
        private static double quotientHigh(double roundedDifference, double delta) {
            return Math.nextUp((delta > 0 ? Math.nextUp(roundedDifference)
                    : Math.nextDown(roundedDifference)) / delta);
        }

        private boolean exactIntersection(Box obstacle) {
            Fraction enter = new Fraction(BigDecimal.ZERO, BigDecimal.ONE);
            Fraction exit = new Fraction(BigDecimal.ONE, BigDecimal.ONE);
            double[][] axes = {{body.minX(), body.maxX(), obstacle.minX(), obstacle.maxX(), dx},
                    {body.minY(), body.maxY(), obstacle.minY(), obstacle.maxY(), dy},
                    {body.minZ(), body.maxZ(), obstacle.minZ(), obstacle.maxZ(), dz}};
            for (double[] axis : axes) {
                double delta = axis[4];
                if (delta == 0) continue; // Static overlap was already checked by clip.
                Fraction first = delta > 0 ? fraction(axis[2], axis[1], delta) : fraction(axis[3], axis[0], delta);
                Fraction last = delta > 0 ? fraction(axis[3], axis[0], delta) : fraction(axis[2], axis[1], delta);
                if (first.compareTo(enter) > 0) enter = first;
                if (last.compareTo(exit) < 0) exit = last;
                if (enter.compareTo(exit) >= 0) return false;
            }
            return true;
        }

        private static Fraction fraction(double a, double b, double delta) {
            // new BigDecimal(double) retains the complete binary input, including a one-ulp
            // penetration lost by rounded subtraction; no decimal display-string conversion.
            BigDecimal numerator = new BigDecimal(a).subtract(new BigDecimal(b));
            BigDecimal denominator = new BigDecimal(delta);
            if (denominator.signum() < 0) {
                numerator = numerator.negate(); denominator = denominator.negate();
            }
            return new Fraction(numerator, denominator);
        }

        private record Fraction(BigDecimal numerator, BigDecimal denominator) {
            int compareTo(Fraction other) {
                return numerator.multiply(other.denominator).compareTo(other.numerator.multiply(denominator));
            }
        }
    }
}
