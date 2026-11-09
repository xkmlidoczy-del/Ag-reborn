package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** NEW153 rail relay: three magnetic anchors, 32 m segments, a capped and acceleration-limited ride along the route. */
public final class RailRelayRules {
    public static final int MAX_ANCHORS = 3;
    public static final double MAX_SEGMENT = SkillTuning.number("rail_relay", "reach"), MAX_SPEED = SkillTuning.number("rail_relay", "max_speed"), MAX_ACCELERATION = .18, MAX_OFF_ROUTE = 3, END_RADIUS = .75;
    private RailRelayRules() {}

    /** The next anchor list, or empty when the hop is unusable. The oldest anchor leaves when a fourth would arrive. */
    public static Optional<List<V3>> addAnchor(List<V3> anchors, V3 next) { return addAnchor(anchors, next, MAX_SEGMENT); }

    /** Same with a level-scaled longest hop (see MobilityScale); the speed limits of the ride itself are unchanged. */
    public static Optional<List<V3>> addAnchor(List<V3> anchors, V3 next, double maxSegment) {
        if (next == null || !next.finite() || anchors == null || !Double.isFinite(maxSegment) || maxSegment <= 0) return Optional.empty();
        if (!anchors.isEmpty() && anchors.get(anchors.size() - 1).minus(next).length() > maxSegment + 1e-9) return Optional.empty();
        List<V3> out = new ArrayList<>(anchors);
        out.add(next);
        while (out.size() > MAX_ANCHORS) out.remove(0);
        return Optional.of(List.copyOf(out));
    }

    /**
     * One tick of the ride: the new velocity toward the route direction at the speed cap, changing by at most
     * {@link #MAX_ACCELERATION}. Empty when the rider left the route, reached its end or the route is unusable.
     */
    public static Optional<V3> step(V3 position, List<V3> route, V3 velocity, boolean forward) {
        if (position == null || velocity == null || route == null || route.size() < 2 || !position.finite() || !velocity.finite()) return Optional.empty();
        double best = Double.POSITIVE_INFINITY; int bestIndex = -1; double bestT = 0;
        for (int i = 0; i + 1 < route.size(); i++) {
            V3 a = route.get(i), b = route.get(i + 1), ab = b.minus(a);
            double len2 = ab.dot(ab);
            if (len2 < 1e-12) continue;
            double t = Math.max(0, Math.min(1, position.minus(a).dot(ab) / len2));
            double d = position.minus(a.plus(ab.scale(t))).length();
            if (d < best) { best = d; bestIndex = i; bestT = t; }
        }
        if (bestIndex < 0 || best > MAX_OFF_ROUTE) return Optional.empty();
        V3 end = forward ? route.get(route.size() - 1) : route.get(0);
        if (position.minus(end).length() <= END_RADIUS) return Optional.empty();
        V3 dir = route.get(bestIndex + 1).minus(route.get(bestIndex)).unit();
        if (!forward) dir = dir.scale(-1);
        V3 delta = dir.scale(MAX_SPEED).minus(velocity);
        double dl = delta.length();
        if (dl > MAX_ACCELERATION) delta = delta.scale(MAX_ACCELERATION / dl);
        V3 out = velocity.plus(delta);
        double ol = out.length();
        return Optional.of(ol > MAX_SPEED && velocity.length() <= MAX_SPEED ? out.scale(MAX_SPEED / ol) : out);
    }
}
