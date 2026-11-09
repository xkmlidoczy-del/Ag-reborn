package cn.academy.port.highpower.core;

/** Minimal immutable double vector for the pure rules; no engine types, so every rule is testable headlessly. */
public record V3(double x, double y, double z) {
    public static final V3 ZERO = new V3(0, 0, 0);
    public V3 plus(V3 o) { return new V3(x + o.x, y + o.y, z + o.z); }
    public V3 minus(V3 o) { return new V3(x - o.x, y - o.y, z - o.z); }
    public V3 scale(double f) { return new V3(x * f, y * f, z * f); }
    public double dot(V3 o) { return x * o.x + y * o.y + z * o.z; }
    public V3 cross(V3 o) { return new V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x); }
    public double length() { return Math.sqrt(dot(this)); }
    public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    /** Unit vector, or {@link #ZERO} when the vector is zero or not finite. */
    public V3 unit() {
        if (!finite()) return ZERO;
        double l = length();
        return l < 1e-12 ? ZERO : scale(1 / l);
    }
}
