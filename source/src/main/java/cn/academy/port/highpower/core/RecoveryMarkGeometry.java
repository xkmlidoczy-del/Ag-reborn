package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.math.BigDecimal;

/** Exact NEW323 geometry. An embedded source exception is a single shallow outward exit. */
public final class RecoveryMarkGeometry {
    private RecoveryMarkGeometry() {}

    public static boolean withinRange(Point a, Point b) {
        if (a == null || b == null || !a.finite() || !b.finite()) return false;
        BigDecimal x = exact(a.x()).subtract(exact(b.x())), y = exact(a.y()).subtract(exact(b.y())),
                z = exact(a.z()).subtract(exact(b.z()));
        return x.multiply(x).add(y.multiply(y)).add(z.multiply(z))
                .compareTo(BigDecimal.valueOf(RecoveryMarkRules.RANGE * RecoveryMarkRules.RANGE)) <= 0;
    }

    public static boolean pairwiseRange(Point player, Point source, Point destination) {
        return withinRange(player, source) && withinRange(player, destination) && withinRange(source, destination);
    }

    /** Native WorldBorder's AABB helper subtracts1e-5 from max faces; recovery uses exact containment. */
    public static boolean insideBorder(Box body, double minX, double minZ, double maxX, double maxZ) {
        return body != null && body.finite() && Double.isFinite(minX) && Double.isFinite(minZ)
                && Double.isFinite(maxX) && Double.isFinite(maxZ) && minX < maxX && minZ < maxZ
                && body.minX() >= minX && body.minZ() >= minZ && body.maxX() <= maxX && body.maxZ() <= maxZ;
    }

    public static boolean needleBody(Box body) {
        // The registered needle is 0.25 by 0.25; reject mutated or substituted bodies.
        return body != null && body.finite() && body.maxX() - body.minX() <= .25000001
                && body.maxY() - body.minY() <= .25000001 && body.maxZ() - body.minZ() <= .25000001;
    }

    /**
     * The body center must lie strictly outside exactly one obstacle face. Movement must
     * immediately retreat through that face, never increase overlap on another axis, and
     * completely exit within one body width of travel. Exact binary-input arithmetic
     * retains one-ulp penetrations and refuses equality at the ambiguous center boundary.
     * Callers additionally require one native shape box in one unchanged original block.
     */
    public static boolean shallowOutwardExit(Box body, Point source, Point destination, Box obstacle) {
        if (!needleBody(body) || obstacle == null || !obstacle.finite() || !withinRange(source, destination)) return false;
        double[][] axes = {{body.minX(), body.maxX(), obstacle.minX(), obstacle.maxX(), source.x(), destination.x()},
                {body.minY(), body.maxY(), obstacle.minY(), obstacle.maxY(), source.y(), destination.y()},
                {body.minZ(), body.maxZ(), obstacle.minZ(), obstacle.maxZ(), source.z(), destination.z()}};
        BigDecimal penetration = null, outward = null, width = null, travelSquared = BigDecimal.ZERO;
        int faces = 0;
        for (double[] a : axes) {
            BigDecimal lo = exact(a[0]), hi = exact(a[1]), wallLo = exact(a[2]), wallHi = exact(a[3]);
            if (hi.compareTo(wallLo) <= 0 || lo.compareTo(wallHi) >= 0) return false;
            // Match the already-rounded native translation delta used by ReturnMarkGeometry.
            BigDecimal delta = exact(a[5] - a[4]);
            travelSquared = travelSquared.add(delta.multiply(delta));
            if (delta.signum() > 0 && lo.compareTo(wallLo) < 0 && hi.compareTo(wallHi) < 0
                    || delta.signum() < 0 && lo.compareTo(wallLo) > 0 && hi.compareTo(wallHi) > 0) return false;
            BigDecimal twiceCenter = lo.add(hi);
            int near = twiceCenter.compareTo(wallLo.multiply(BigDecimal.valueOf(2)));
            int far = twiceCenter.compareTo(wallHi.multiply(BigDecimal.valueOf(2)));
            if (near == 0 || far == 0) return false;
            if (near < 0) {
                if (delta.signum() >= 0) return false;
                faces++; penetration = hi.subtract(wallLo); outward = delta.negate(); width = hi.subtract(lo);
            } else if (far > 0) {
                if (delta.signum() <= 0) return false;
                faces++; penetration = wallHi.subtract(lo); outward = delta; width = hi.subtract(lo);
            }
        }
        if (faces != 1 || outward == null || penetration.compareTo(outward) >= 0) return false;
        return penetration.multiply(penetration).multiply(travelSquared)
                .compareTo(width.multiply(width).multiply(outward).multiply(outward)) <= 0;
    }

    private static BigDecimal exact(double value) { return new BigDecimal(value); }
}
