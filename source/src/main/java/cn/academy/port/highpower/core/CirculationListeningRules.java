package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

import cn.academy.port.highpower.core.VectorParryRules.Point;

/** NEW433: bounded, coarse disturbance cues; these rules never deal damage or award XP. */
public final class CirculationListeningRules {
    public static final double RANGE = SkillTuning.number("circulation_listening", "reach");
    public static final int MAX_CUES = SkillTuning.integer("circulation_listening", "max_targets"), QUERY_LIMIT = 64, SAMPLE_INTERVAL_TICKS = 2;
    public static final int PAYMENT_PERIOD = VectorParryRules.PAYMENT_PERIOD;
    public static final int RESTART_FENCE = VectorParryRules.RESTART_FENCE;
    public static final double ARROW_MIN_SPEED = .5, ARROW_MAX_SPEED = 4;
    public static final double RUNNER_MIN_SPEED = .12, RUNNER_MAX_SPEED = 1.5;
    /** A defensive vertical step fence, not a claim to establish native movement provenance. */
    public static final double MAX_RUNNER_VERTICAL_SPEED = 4;
    private static final double ELEVATION_SLOPE = Math.tan(Math.toRadians(15));
    private static final UUID ZERO_ID = new UUID(0, 0);

    private CirculationListeningRules() {}

    public enum Kind { ARROW, RUNNER }

    /** Server-only observation: UUID and exact world object must never be sent as cue data. */
    public record Sample(UUID identity, Object world, long tick, Kind kind,
                         Point position, Point velocity, boolean sprinting) {}
    public record Track(Sample previous, Sample current) {}

    /** Only bounded presentation scalars: world azimuth, relative elevation, strength and kind. */
    public record Cue(int azimuth, int elevation, int strength, int kind) {
        public Cue {
            if (azimuth < 0 || azimuth >= 16 || elevation < -1 || elevation > 1
                    || strength < 1 || strength > 3 || kind < 0 || kind > 1) {
                throw new IllegalArgumentException("Invalid circulation listening cue");
            }
        }
    }

    public record Selection(List<Cue> cues, boolean overloaded) {
        public Selection {
            cues = List.copyOf(cues);
            if (cues.size() > MAX_CUES || overloaded && !cues.isEmpty()) {
                throw new IllegalArgumentException("Invalid circulation listening selection");
            }
        }
    }

    private record LocatedCue(Cue cue, double distance) {}

    /**
     * Inspect at most 64 candidates, including ineligible ones. A 65th candidate, detected
     * only with hasNext(), suppresses the entire batch, so a query-order prefix cannot leak.
     * The native collector must impose the same bound before materializing candidates and
     * reject duplicated source identities. It also owns source type, LOS and chunk checks.
     */
    public static Selection collect(Point listener, Iterable<Track> tracks) {
        if (listener == null || !listener.finite() || tracks == null) {
            return new Selection(List.of(), false);
        }
        Iterator<Track> iterator = tracks.iterator();
        List<LocatedCue> eligible = new ArrayList<>(QUERY_LIMIT);
        int inspected = 0;
        while (inspected < QUERY_LIMIT && iterator.hasNext()) {
            Track track = iterator.next();
            inspected++;
            if (track == null) continue;
            Cue cue = cue(listener, track.previous(), track.current());
            if (cue != null) {
                eligible.add(new LocatedCue(cue, distance(listener, track.current().position())));
            }
        }
        if (iterator.hasNext()) return new Selection(List.of(), true);
        eligible.sort(Comparator.comparingDouble(LocatedCue::distance)
                .thenComparingInt(c -> c.cue().azimuth())
                .thenComparingInt(c -> c.cue().elevation())
                .thenComparingInt(c -> c.cue().strength())
                .thenComparingInt(c -> c.cue().kind()));
        List<Cue> selected = new ArrayList<>(MAX_CUES);
        for (int i = 0; i < Math.min(MAX_CUES, eligible.size()); i++) {
            selected.add(eligible.get(i).cue());
        }
        return new Selection(selected, false);
    }

    /** No first-frame cue: both valid observations and an exact two-tick pair are required. */
    public static Cue cue(Point listener, Sample previous, Sample current) {
        if (!continuous(previous, current) || !withinRange(listener, current.position())) return null;
        double dx = current.position().x() - listener.x();
        double dy = current.position().y() - listener.y();
        double dz = current.position().z() - listener.z();
        double horizontal = Math.hypot(dx, dz);
        double distance = Math.hypot(horizontal, dy);
        if (distance == 0) return null;
        // World-fixed centers: 0=+Z, 4=+X, 8=-Z, 12=-X. Exact vertical uses canonical 0.
        double angle = horizontal == 0 ? 0 : Math.atan2(dx, dz);
        int azimuth = Math.floorMod((int) Math.floor(angle * 8 / Math.PI + .5), 16);
        // The represented +/-15-degree boundaries are level, without an outward epsilon.
        double boundary = horizontal * ELEVATION_SLOPE;
        int elevation = dy > boundary ? 1 : dy < -boundary ? -1 : 0;
        double speed = measuredSpeed(previous, current);
        double minimum = current.kind() == Kind.ARROW ? ARROW_MIN_SPEED : RUNNER_MIN_SPEED;
        double maximum = current.kind() == Kind.ARROW ? ARROW_MAX_SPEED : RUNNER_MAX_SPEED;
        double normalizedSpeed = Math.max(0, Math.min(1, (speed - minimum) / (maximum - minimum)));
        double score = ((1 - distance / RANGE) + normalizedSpeed) * .5;
        int strength = Math.max(1, Math.min(3, 1 + (int) Math.floor(score * 3)));
        return new Cue(azimuth, elevation, strength, current.kind().ordinal());
    }

    /**
     * Defensive continuity only. Even arrow displacement consistent with old native velocity
     * cannot prove absence of a teleport; the native observer must independently witness
     * actual movement and invalidate set-position/teleport, remove/re-add and world changes.
     */
    public static boolean continuous(Sample previous, Sample current) {
        if (!valid(previous) || !valid(current) || !previous.identity().equals(current.identity())
                || previous.world() != current.world() || previous.kind() != current.kind()
                || current.tick() - previous.tick() != SAMPLE_INTERVAL_TICKS) return false;
        if (current.kind() == Kind.RUNNER && (!previous.sprinting() || !current.sprinting())) return false;
        double dx = current.position().x() - previous.position().x();
        double dy = current.position().y() - previous.position().y();
        double dz = current.position().z() - previous.position().z();
        if (!Double.isFinite(dx) || !Double.isFinite(dy) || !Double.isFinite(dz)) return false;
        boolean arrow = current.kind() == Kind.ARROW;
        double minimum = arrow ? ARROW_MIN_SPEED : RUNNER_MIN_SPEED;
        double maximum = arrow ? ARROW_MAX_SPEED : RUNNER_MAX_SPEED;
        if (!boundedMagnitude(dx / SAMPLE_INTERVAL_TICKS, arrow ? dy / SAMPLE_INTERVAL_TICKS : 0,
                dz / SAMPLE_INTERVAL_TICKS, minimum, maximum)) return false;
        // Accepted player movement updates position/knownMovement, not deltaMovement.
        // A runner's zero or stale delta therefore cannot establish or contradict movement;
        // require actual bounded displacement here and native physical-write proof outside.
        if (!arrow) return Math.abs(dy) <= MAX_RUNNER_VERTICAL_SPEED * SAMPLE_INTERVAL_TICKS;
        Point velocity = previous.velocity();
        double ex = velocity.x() * SAMPLE_INTERVAL_TICKS;
        double ey = velocity.y() * SAMPLE_INTERVAL_TICKS;
        double ez = velocity.z() * SAMPLE_INTERVAL_TICKS;
        double residual = Math.hypot(Math.hypot(dx - ex, dz - ez), dy - ey);
        // Arrow drag/gravity allowance cannot relax the actual speed/step fences above.
        double tolerance = .2 + .04 * Math.hypot(Math.hypot(ex, ez), ey);
        return residual <= tolerance;
    }

    /** Inclusive configured reach sphere, with exact represented-value comparisons at the boundary. */
    public static boolean withinRange(Point listener, Point source) {
        if (listener == null || source == null || !listener.finite() || !source.finite()) return false;
        return boundedMagnitude(source.x() - listener.x(), source.y() - listener.y(),
                source.z() - listener.z(), 0, RANGE);
    }

    private static boolean valid(Sample sample) {
        if (sample == null || sample.identity() == null || ZERO_ID.equals(sample.identity())
                || sample.world() == null || sample.kind() == null || !clock(sample.tick())
                || sample.position() == null || !sample.position().finite()
                || sample.velocity() == null || !sample.velocity().finite()) return false;
        Point v = sample.velocity();
        if (sample.kind() == Kind.ARROW) return boundedMagnitude(v.x(), v.y(), v.z(), ARROW_MIN_SPEED, ARROW_MAX_SPEED);
        return Math.abs(v.y()) <= MAX_RUNNER_VERTICAL_SPEED
                && boundedMagnitude(v.x(), 0, v.z(), 0, RUNNER_MAX_SPEED);
    }

    private static double measuredSpeed(Sample previous, Sample current) {
        double x = (current.position().x() - previous.position().x()) / SAMPLE_INTERVAL_TICKS;
        double z = (current.position().z() - previous.position().z()) / SAMPLE_INTERVAL_TICKS;
        double y = current.kind() == Kind.ARROW
                ? (current.position().y() - previous.position().y()) / SAMPLE_INTERVAL_TICKS : 0;
        return Math.hypot(Math.hypot(x, z), y);
    }

    private static double distance(Point a, Point b) {
        return Math.hypot(Math.hypot(a.x() - b.x(), a.z() - b.z()), a.y() - b.y());
    }

    private static boolean boundedMagnitude(double x, double y, double z, double minimum, double maximum) {
        if (!Double.isFinite(x) || !Double.isFinite(y) || !Double.isFinite(z)
                || Math.abs(x) > maximum || Math.abs(y) > maximum || Math.abs(z) > maximum) return false;
        double squared = x * x + y * y + z * z;
        double lower = minimum * minimum, upper = maximum * maximum;
        double uncertainty = 16 * Math.ulp(Math.max(squared, upper));
        if (squared < lower - uncertainty || squared > upper + uncertainty) return false;
        if (squared > lower + uncertainty && squared < upper - uncertainty) return true;
        BigDecimal bx = new BigDecimal(x), by = new BigDecimal(y), bz = new BigDecimal(z);
        BigDecimal exact = bx.multiply(bx).add(by.multiply(by)).add(bz.multiply(bz));
        BigDecimal min = new BigDecimal(minimum), max = new BigDecimal(maximum);
        return exact.compareTo(min.multiply(min)) >= 0 && exact.compareTo(max.multiply(max)) <= 0;
    }

    public static boolean clock(long tick) { return VectorParryRules.clock(tick); }
    public static BigDecimal theoreticalActivationCost(BigDecimal q) { return VectorParryRules.theoreticalActivationCost(q); }
    public static double activationCost(BigDecimal q) { return VectorParryRules.activationCost(q); }
    public static BigDecimal theoreticalSustainCost(BigDecimal q) { return VectorParryRules.theoreticalSustainCost(q); }
    public static double sustainCost(BigDecimal q) { return VectorParryRules.sustainCost(q); }
    public static BigDecimal theoreticalCooldown(BigDecimal q) { return VectorParryRules.theoreticalCooldown(q); }
    public static int cooldownTicks(BigDecimal q) { return VectorParryRules.cooldownTicks(q); }
}
