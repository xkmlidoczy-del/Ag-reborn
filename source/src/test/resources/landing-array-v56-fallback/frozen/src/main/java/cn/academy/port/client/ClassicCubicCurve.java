/* LambdaLib 1.2.3 CubicCurve adaptation. Copyright Lambda Innovation, MIT. See NOTICE. */
package cn.academy.port.client;

import java.util.Arrays;
import java.util.Comparator;

/** Immutable classic piecewise Hermite curve, including linear endpoint extrapolation. */
public final class ClassicCubicCurve {
    private record Point(double x, double y) {}
    private final Point[] points;

    /** Alternating x,y coordinates. Unequal knot spacing keeps the original averaged slopes. */
    public ClassicCubicCurve(double... coordinates) {
        if ((coordinates.length & 1) != 0) throw new IllegalArgumentException("Expected x,y pairs");
        points = new Point[coordinates.length / 2];
        for (int i = 0; i < points.length; i++) {
            double x = coordinates[i * 2], y = coordinates[i * 2 + 1];
            if (!Double.isFinite(x) || !Double.isFinite(y)) throw new IllegalArgumentException("Non-finite knot");
            points[i] = new Point(x, y);
        }
        Arrays.sort(points, Comparator.comparingDouble(Point::x));
        for (int i = 1; i < points.length; i++)
            if (points[i - 1].x == points[i].x) throw new IllegalArgumentException("Duplicate x knot");
    }

    public double valueAt(double x) {
        if (!Double.isFinite(x)) throw new IllegalArgumentException("Non-finite sample");
        if (points.length == 0) return 0;
        int index = 0;
        while (index < points.length && points[index].x < x) index++;
        if (index == points.length) {
            Point end = points[points.length - 1];
            return end.y + (x - end.x) * (points.length >= 2 ? slope(index - 1, index - 2) : 0);
        }
        if (index == 0) return points[0].y + tangent(0, 1) * (x - points[0].x);
        Point a = points[index - 1], b = points[index];
        double length = b.x - a.x, t = (x - a.x) / length, t2 = t * t, t3 = t2 * t;
        double m0 = tangent(index - 1, length), m1 = tangent(index, length);
        return t3 * (m0 + m1 + 2 * a.y - 2 * b.y)
                + t2 * (-2 * m0 - m1 - 3 * a.y + 3 * b.y) + t * m0 + a.y;
    }

    private double tangent(int index, double length) {
        double result = index == 0 ? (points.length == 1 ? 0 : slope(index, index + 1))
                : index == points.length - 1 ? slope(index, index - 1)
                : .5 * (slope(index + 1, index) + slope(index, index - 1));
        return result * length;
    }

    private double slope(int a, int b) {
        return (points[b].y - points[a].y) / (points[b].x - points[a].x);
    }
}
