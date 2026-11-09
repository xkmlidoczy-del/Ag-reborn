package cn.academy.port.client;

import cn.academy.port.highpower.core.ShearWindbladeGeometry;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** NEW421 bounded, read-only server receipts. No client collision or predicted flight. */
public final class ShearWindbladeState {
    public static final int FLYING = 0, ENDED = 1, TRAVELLING = 0, BLOCKED = 1, EXPIRED = 2;
    public static final int REFUSED = 0, DAMAGED = 1, ABSORBED = 2;
    public static final int MAX_CONTROLLERS = 4, MAX_WATERMARKS = 1024, LIFE_TICKS = 16;
    private static final Point ZERO = new Point(0, 0, 0);
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Front(int index, List<Point> previous, List<Point> current, int status) {
        public Front { previous = List.copyOf(previous); current = List.copyOf(current); }
    }
    public record Impact(int blade, int slot, Point point, int status, int hitEntity) {}
    public record Frame(Identity id, long sequence, int phase, boolean crouch, Point origin,
                        List<Front> fronts, List<Impact> impacts) {
        public Frame { fronts = List.copyOf(fronts); impacts = List.copyOf(impacts); }
    }
    public record Controller(Identity id, boolean local, boolean crouch, Point origin,
                             List<Front> fronts, List<Impact> impacts, int age, int receiptAge) {
        public Controller { fronts = List.copyOf(fronts); impacts = List.copyOf(impacts); }
    }
    private record Fence(Frame frame, boolean closed, int terminalMask, int impactMask) {}
    private final Map<UUID, Fence> fences = new HashMap<>();
    private final Map<UUID, Controller> active = new LinkedHashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0 || f.sequence > 32
                || f.phase != FLYING && f.phase != ENDED || f.origin == null || !f.origin.finite()
                || f.fronts == null || f.impacts == null || f.fronts.size() > 2 || f.impacts.size() > 4) return false;
        if (f.phase == ENDED) return f.origin.equals(ZERO) && f.fronts.isEmpty() && f.impacts.isEmpty();
        int blades = 0, slots = 0;
        for (var front : f.fronts) {
            if (front == null || front.index < 0 || front.index > 1 || (blades & 1 << front.index) != 0
                    || front.status < TRAVELLING || front.status > EXPIRED
                    || !validFront(front.previous, f.origin) || !validFront(front.current, f.origin)) return false;
            blades |= 1 << front.index;
            Point delta = subtract(front.current.getFirst(), front.previous.getFirst());
            Point direction = forward(front.previous);
            if (dot(delta, delta) > 4.000001 || dot(delta, direction) < -1e-7
                    || crossLengthSquared(delta, direction) > 1e-8) return false;
            for (int i = 1; i <= ShearWindbladeGeometry.CHORDS; i++)
                if (subtract(front.current.get(i), front.previous.get(i)).distanceSquared(delta) > 1e-12) return false;
        }
        for (var impact : f.impacts) {
            if (impact == null || impact.blade < 0 || impact.blade > 1 || impact.slot < 0 || impact.slot >= 4
                    || (slots & 1 << impact.slot) != 0 || impact.hitEntity < -1 || impact.status != REFUSED && impact.hitEntity < 0
                    || impact.status < REFUSED || impact.status > ABSORBED || impact.point == null
                    || !impact.point.finite() || impact.point.distanceSquared(f.origin) > 25 * 25) return false;
            slots |= 1 << impact.slot;
        }
        return true;
    }
    /** Receipt vertices must describe the same open eight-chord parabola as authoritative collision. */
    private static boolean validFront(List<Point> points, Point origin) {
        if (points == null || points.size() != ShearWindbladeGeometry.CHORDS + 1) return false;
        for (Point p : points) if (p == null || !p.finite() || p.distanceSquared(origin) > 24.000001 * 24.000001) return false;
        Point center = scale(add(points.getFirst(), points.getLast()), .5);
        Point lateral = scale(subtract(points.getLast(), points.getFirst()), 1 / (2 * ShearWindbladeGeometry.HALF_WIDTH));
        Point forward = forward(points);
        if (Math.abs(dot(lateral, lateral) - 1) > 1e-6 || Math.abs(dot(forward, forward) - 1) > 1e-6
                || Math.abs(dot(lateral, forward)) > 1e-6) return false;
        Point travel = subtract(center, origin);
        if (dot(travel, forward) < -1e-6 || crossLengthSquared(travel, forward) > 1e-8) return false;
        for (int i = 0; i <= ShearWindbladeGeometry.CHORDS; i++) {
            double t = -1 + 2.0 * i / ShearWindbladeGeometry.CHORDS;
            Point expected = add(center, add(scale(lateral, t * ShearWindbladeGeometry.HALF_WIDTH),
                    scale(forward, ShearWindbladeGeometry.BOW * (1 - t * t))));
            if (expected.distanceSquared(points.get(i)) > 1e-12) return false;
        }
        return true;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        int terminalMask = 0, impactMask = 0;
        if (old != null) {
            if (same) {
                if (old.closed || f.sequence <= old.frame.sequence || f.crouch != old.frame.crouch) return false;
                terminalMask = old.terminalMask; impactMask = old.impactMask;
                if (f.phase == FLYING) {
                    if (!f.origin.equals(old.frame.origin)) return false;
                    for (var front : f.fronts) {
                        if ((terminalMask & 1 << front.index) != 0) return false;
                        var before = old.frame.fronts.stream().filter(p -> p.index == front.index).findFirst().orElse(null);
                        if (before != null && (!sameOrientation(before.current, front.previous)
                                || dot(subtract(front.previous.getFirst(), before.current.getFirst()), forward(before.current)) < -1e-6
                                || f.sequence == old.frame.sequence + 1 && !samePoints(before.current, front.previous))) return false;
                    }
                }
            } else if (f.id.epoch < old.frame.id.epoch || f.id.epoch == old.frame.id.epoch
                    && (f.id.input <= old.frame.id.input || f.id.token <= old.frame.id.token)) return false;
        }
        for (var impact : f.impacts) {
            if ((impactMask & 1 << impact.slot) != 0) return false;
            impactMask |= 1 << impact.slot;
        }
        for (var front : f.fronts) if (front.status != TRAVELLING) terminalMask |= 1 << front.index;
        Controller prior = active.get(f.id.owner);
        if (prior != null && !prior.id.equals(f.id)) retire(prior.id);
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED, terminalMask, impactMask));
        if (f.phase == ENDED) { active.remove(f.id.owner); return true; }
        if (prior == null && active.size() >= MAX_CONTROLLERS) {
            Controller victim = active.values().stream().filter(p -> !p.local).findFirst().orElse(null);
            if (victim == null) return true;
            retire(victim.id);
        }
        active.put(f.id.owner, new Controller(f.id, local, f.crouch, f.origin, f.fronts, f.impacts,
                same && prior != null ? prior.age : 0, 0));
        return true;
    }
    private static boolean sameOrientation(List<Point> a, List<Point> b) {
        return forward(a).distanceSquared(forward(b)) < 1e-10
                && subtract(a.getLast(), a.getFirst()).distanceSquared(subtract(b.getLast(), b.getFirst())) < 1e-10;
    }
    private static boolean samePoints(List<Point> a, List<Point> b) {
        for (int i = 0; i < a.size(); i++) if (a.get(i).distanceSquared(b.get(i)) > 1e-10) return false;
        return true;
    }
    public List<Identity> tick(boolean paused, Predicate<Controller> validContext) {
        var ended = new ArrayList<Identity>();
        for (var cast : List.copyOf(active.values())) {
            if (!validContext.test(cast) || !paused && cast.age + 1 >= LIFE_TICKS) { ended.add(cast.id); retire(cast.id); }
            else if (!paused) active.put(cast.id.owner, new Controller(cast.id, cast.local, cast.crouch, cast.origin,
                    cast.fronts, cast.impacts, cast.age + 1, cast.receiptAge + 1));
        }
        return List.copyOf(ended);
    }
    public List<Controller> active() { return List.copyOf(active.values()); }
    public Controller local() { return active.values().stream().filter(Controller::local).findFirst().orElse(null); }
    public boolean owns(Identity id) { var cast = active.get(id.owner); return cast != null && cast.id.equals(id); }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        for (var cast : active()) if (cast.local && cast.id.epoch == epoch && cast.id.input <= input) retire(cast.id);
    }
    public void clear() { for (var cast : active()) retire(cast.id); }
    private void retire(Identity id) {
        Controller cast = active.get(id.owner); if (cast == null || !cast.id.equals(id)) return;
        Fence fence = fences.get(id.owner);
        if (fence != null && fence.frame.id.equals(id)) fences.put(id.owner, new Fence(fence.frame, true, fence.terminalMask, fence.impactMask));
        active.remove(id.owner);
    }
    public void resetSession() { clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public int watermarkCount() { return fences.size(); }
    private static Point forward(List<Point> p) { return scale(subtract(p.get(ShearWindbladeGeometry.CHORDS / 2), scale(add(p.getFirst(), p.getLast()), .5)), 1 / ShearWindbladeGeometry.BOW); }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Point scale(Point p, double n) { return new Point(p.x() * n, p.y() * n, p.z() * n); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static double crossLengthSquared(Point a, Point b) {
        double x = a.y() * b.z() - a.z() * b.y(), y = a.z() * b.x() - a.x() * b.z(), z = a.x() * b.y() - a.y() * b.x();
        return x * x + y * y + z * z;
    }
}
