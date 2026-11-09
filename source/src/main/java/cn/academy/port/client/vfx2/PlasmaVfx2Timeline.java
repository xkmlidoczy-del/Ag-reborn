package cn.academy.port.client.vfx2;

import java.util.ArrayDeque;
import java.util.List;

/** Pure optional presentation state. The caller owns accepted packet/token and world fences. */
public final class PlasmaVfx2Timeline {
    public static final long PHASE_LOOP_MILLIS = 120_000;
    public static final long LAUNCH_MILLIS = 300; // Six nominal client ticks; no gameplay deadline.
    public static final int MAX_HISTORY = 24;
    public static final long MIN_SAMPLE_MILLIS = 25;
    public static final long STALE_HISTORY_MILLIS = 350;
    public static final double MIN_SAMPLE_DISTANCE = .10;
    public static final double MAX_SAMPLE_JUMP = 8;
    public static final int MAX_BODIES = 4;
    public static final int MAX_VERTICES = 512;

    private PlasmaVfx2Timeline() {}

    public enum Stage { INACTIVE, CHARGE, READY_CUE, LAUNCH, TRAVEL, END }

    /** Use phase01 with integral cycle counts so the 120-second wrap is continuous. */
    public record Snapshot(Stage stage, float charge01, float readyPulse,
                           float launch01, float launchEnvelope, double phase01,
                           long visualMillis, boolean ended) {
        public double phaseSeconds() { return phase01 * (PHASE_LOOP_MILLIS / 1000.0); }
    }

    /** Construct only for an accepted START, and forward only matching accepted events. */
    public static final class State {
        private final double chargeMillis;
        private boolean active, readyCue, go, ended;
        private long visualMillis, phaseMillis, readyPhaseMillis, launchMillis;

        public State(double chargeTicks) {
            active = Double.isFinite(chargeTicks) && chargeTicks >= 30 && chargeTicks <= 60;
            chargeMillis = active ? chargeTicks * 50 : 1;
        }

        /** Delta is from the caller's visual clock, never server gameplay or predicted movement. */
        public Snapshot advance(long deltaMillis, boolean paused) {
            if (!active || ended || paused || deltaMillis == 0) return snapshot();
            if (deltaMillis < 0) {
                clear(); // A reversed session clock cannot retain a live optional effect.
                return snapshot();
            }
            visualMillis = deltaMillis > Long.MAX_VALUE - visualMillis
                    ? Long.MAX_VALUE : visualMillis + deltaMillis;
            phaseMillis = (phaseMillis + deltaMillis % PHASE_LOOP_MILLIS) % PHASE_LOOP_MILLIS;
            if (go) launchMillis = Math.min(LAUNCH_MILLIS,
                    launchMillis + Math.min(deltaMillis, LAUNCH_MILLIS));
            return snapshot();
        }

        /** READY is the original sound cue, which can precede legal release by one server tick. */
        public boolean acceptedReady() {
            if (!active || ended || go || readyCue) return false;
            readyCue = true;
            readyPhaseMillis = phaseMillis;
            return true;
        }

        /** Only accepted GO may enter launch; local key release is deliberately absent. */
        public boolean acceptedGo() {
            if (!active || ended || go) return false;
            go = true;
            launchMillis = 0;
            return true;
        }

        /** END has no impact meaning: cancellation and invalidation use this same event. */
        public boolean acceptedEnd() {
            if (!active || ended) return false;
            ended = true;
            return true;
        }

        public Snapshot snapshot() {
            if (!active) return new Snapshot(Stage.INACTIVE, 0, 0, 0, 0, 0, 0, false);
            float charge = (float) Math.min(1, visualMillis / chargeMillis);
            double phase = phaseMillis / (double) PHASE_LOOP_MILLIS;
            if (ended) return new Snapshot(Stage.END, charge, 0, 0, 0, phase, visualMillis, true);
            if (go) {
                float progress = launchMillis / (float) LAUNCH_MILLIS;
                float envelope = launchMillis >= LAUNCH_MILLIS ? 0
                        : (float) Math.pow(Math.sin(Math.PI * progress), 2);
                return new Snapshot(launchMillis < LAUNCH_MILLIS ? Stage.LAUNCH : Stage.TRAVEL,
                        charge, 0, progress, envelope, phase, visualMillis, false);
            }
            long readyAge = (phaseMillis - readyPhaseMillis + PHASE_LOOP_MILLIS) % PHASE_LOOP_MILLIS;
            // Exactly 120 one-second cycles per phase loop; no seam when the bounded phase wraps.
            float pulse = readyCue ? (float) (.5 + .5 * Math.cos(2 * Math.PI * readyAge / 1000.0)) : 0;
            return new Snapshot(readyCue ? Stage.READY_CUE : Stage.CHARGE,
                    charge, pulse, 0, 0, phase, visualMillis, false);
        }

        /** World, connection, token replacement and resource reload discard all optional state. */
        public void clear() {
            active = readyCue = go = ended = false;
            visualMillis = phaseMillis = readyPhaseMillis = launchMillis = 0;
        }
    }

    public record Vec(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
    }
    public record Point(Vec position, long visualMillis) {}

    /** Only actual accepted server-position samples belong here, never extrapolated positions. */
    public static final class History {
        private final ArrayDeque<Point> points = new ArrayDeque<>();

        public boolean offer(double x, double y, double z, long visualMillis) {
            return offer(new Vec(x, y, z), visualMillis);
        }

        public boolean offer(Vec position, long visualMillis) {
            if (position == null || !position.finite() || visualMillis < 0) {
                clear();
                return false;
            }
            Point previous = points.peekLast();
            if (previous != null) {
                long gap = visualMillis - previous.visualMillis();
                double x = position.x() - previous.position().x();
                double y = position.y() - previous.position().y();
                double z = position.z() - previous.position().z();
                double distanceSquared = x * x + y * y + z * z;
                if (gap < 0 || gap > STALE_HISTORY_MILLIS || !Double.isFinite(distanceSquared)
                        || distanceSquared > MAX_SAMPLE_JUMP * MAX_SAMPLE_JUMP) {
                    clear(); // New anchor only: never draw a bridge across a discontinuity.
                } else if (gap < MIN_SAMPLE_MILLIS
                        || distanceSquared < MIN_SAMPLE_DISTANCE * MIN_SAMPLE_DISTANCE) {
                    return false;
                }
            }
            if (points.size() == MAX_HISTORY) points.removeFirst();
            points.addLast(new Point(position, visualMillis));
            return true;
        }

        /** Same pause-aware visual clock as offer; a pause alone cannot expire a trail. */
        public void expire(long visualMillis) {
            Point last = points.peekLast();
            if (last != null && (visualMillis < last.visualMillis()
                    || visualMillis - last.visualMillis() > STALE_HISTORY_MILLIS)) clear();
        }

        public List<Point> points() { return List.copyOf(points); }
        public int size() { return points.size(); }
        public void clear() { points.clear(); }
    }

    /** One shared frame budget. Reserve before submitting a body or any of its auxiliary vertices. */
    public static final class Budget {
        private int bodies, vertices;

        public void beginFrame() { bodies = vertices = 0; }

        /** Zero vertices still reserves one enhanced body; failure never consumes partial budget. */
        public boolean reserve(int requestedVertices) {
            if (requestedVertices < 0 || bodies >= MAX_BODIES
                    || requestedVertices > MAX_VERTICES - vertices) return false;
            bodies++;
            vertices += requestedVertices;
            return true;
        }

        public int bodiesUsed() { return bodies; }
        public int verticesUsed() { return vertices; }
        public int remainingVertices() { return MAX_VERTICES - vertices; }
    }
}
