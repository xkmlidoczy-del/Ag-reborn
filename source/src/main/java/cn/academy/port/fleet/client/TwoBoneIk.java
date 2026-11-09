package cn.academy.port.fleet.client;

/** Pure Java two-segment arm solver. The requested wrist is never detached. */
public final class TwoBoneIk {
    public record Point(float x, float y, float z) {
        public Point add(Point v) { return new Point(x + v.x, y + v.y, z + v.z); }
        public Point sub(Point v) { return new Point(x - v.x, y - v.y, z - v.z); }
        public Point scale(float k) { return new Point(x * k, y * k, z * k); }
        public float dot(Point v) { return x * v.x + y * v.y + z * v.z; }
        public float length() { return (float) Math.sqrt(dot(this)); }
        public boolean finite() { return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z); }
    }
    public record Arm(Point shoulder, Point elbow, Point wrist, boolean shoulderAdjusted) {}
    private TwoBoneIk() {}

    /**
     * pole is a direction toward the preferred elbow bend (normally down/out).
     * If the eye-space shoulder cannot reach, move that off-screen endpoint
     * along the ray. Segment lengths and the contact point remain unchanged.
     */
    public static Arm solve(Point shoulder, Point wrist, Point pole, float upper, float forearm) {
        if (shoulder == null || wrist == null || pole == null
                || !shoulder.finite() || !wrist.finite() || !pole.finite()
                || !Float.isFinite(upper) || !Float.isFinite(forearm)
                || upper < .001f || forearm < .001f || upper > 10 || forearm > 10) {
            throw new IllegalArgumentException("finite points and bone lengths in [.001,10] metres required");
        }
        Point origin = shoulder;
        Point delta = wrist.sub(origin);
        float distance = delta.length();
        if (!Float.isFinite(distance)) throw new IllegalArgumentException("arm span exceeds numeric range");
        Point direction = distance > 1e-6f ? delta.scale(1 / distance) : new Point(0, 0, -1);
        float minimum = Math.abs(upper - forearm) + 1e-5f;
        float maximum = (upper + forearm) * .9999f;
        boolean moved = false;
        if (distance > maximum) {
            origin = wrist.sub(direction.scale(maximum));
            distance = maximum;
            moved = true;
        } else if (distance < minimum) {
            origin = wrist.sub(direction.scale(minimum));
            distance = minimum;
            moved = true;
        }
        Point bend = pole.sub(direction.scale(pole.dot(direction)));
        if (bend.length() < 1e-5f) {
            Point fallback = Math.abs(direction.y) < .9f ? new Point(0, -1, 0) : new Point(1, 0, 0);
            bend = fallback.sub(direction.scale(fallback.dot(direction)));
        }
        bend = bend.scale(1 / bend.length());
        float along = (upper * upper - forearm * forearm + distance * distance) / (2 * distance);
        float height = (float) Math.sqrt(Math.max(0, upper * upper - along * along));
        Point elbow = origin.add(direction.scale(along)).add(bend.scale(height));
        return new Arm(origin, elbow, wrist, moved);
    }
}
