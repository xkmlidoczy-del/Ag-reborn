package cn.academy.port.client;

import java.util.List;

/**
 * Engine-free snapshot of one live effect controller for the decoration layer. The meaning of {@code points},
 * {@code numbers} and {@code polylines} is documented by each skill's decor class; all are plain doubles so the
 * frozen effect classes can fill them without referencing the decoration kit.
 */
public record DecorSnap(boolean local, int age, int phase, long token, List<double[]> points, double[] numbers, List<List<double[]>> polylines) {
    /** Placeholder for an absent point; every consumer treats non-finite coordinates as missing. */
    public static final double[] NONE = {Double.NaN, Double.NaN, Double.NaN};
    public DecorSnap {
        points = List.copyOf(points);
        polylines = List.copyOf(polylines);
        numbers = numbers == null ? new double[0] : numbers.clone();
    }
    public static DecorSnap of(boolean local, int age, int phase, long token, List<double[]> points, double... numbers) {
        return new DecorSnap(local, age, phase, token, points, numbers, List.of());
    }
    public double number(int index, double fallback) { return index >= 0 && index < numbers.length ? numbers[index] : fallback; }
    public double[] point(int index) { return index >= 0 && index < points.size() ? points.get(index) : null; }
}
