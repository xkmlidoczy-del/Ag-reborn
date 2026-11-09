package cn.academy.port.client;

import cn.academy.port.client.MultiAxisReflectionMesh.Point;
import cn.academy.port.highpower.HighPowerRecoveryHudModel;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

/** NEW442 authenticated toggle receipts. Cosmetic silence never ends server-owned control. */
public final class MultiAxisReflectionState {
    public static final int STARTUP = 0, ACTIVE = 1, REFLECT = 2, CONTACT = 3, ENDED = 4;
    public static final int MAX_CONTEXTS = 4, MAX_WATERMARKS = 1024, QUIET_TICKS = 40;
    public static final int FLASH_TICKS = 5, TRACE_TICKS = 9, CONTACT_TICKS = 7;
    public static final int MAX_TRACES = 4, MAX_TRACE_POINTS = 5, MAX_ARROW_WATERMARKS = 8;
    public static final double MAX_TRACE_STEP_SQUARED = 9, OWNER_DISCONTINUITY_SQUARED = 16;
    public static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Arrow(int entity, UUID uuid) {}
    public record Frame(Identity id, long sequence, int phase, Point point, Point facing,
                        int remainingHits, int liveMask, int facet, int arrowEntity, UUID arrowUuid, Point arrowPoint,
                        String available, String capacity) {
        public Frame(Identity id, long sequence, int phase, Point point, Point facing, int remainingHits,
                     int liveMask, int facet, int arrowEntity, UUID arrowUuid, Point arrowPoint) {
            this(id, sequence, phase, point, facing, remainingHits, liveMask, facet, arrowEntity, arrowUuid, arrowPoint, "0", "0");
        }
        public Arrow arrow() { return new Arrow(arrowEntity, arrowUuid); }
    }
    /** First reflection point is immutable; points contain received or directly observed positions only. */
    public record Trace(Arrow arrow, Point reflectionPoint, int facet, int age, List<Point> points, boolean clientObserved) {
        public Trace { points = List.copyOf(points); }
    }
    public record Controller(Frame frame, boolean local, int quiet, List<Trace> traces,
                             Frame contact, int contactAge, Point observedBody) {
        public Controller { traces = List.copyOf(traces); }
        public Identity id() { return frame.id; }
    }
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Controller> active = new LinkedHashMap<>();
    private final Map<UUID, Fence> fences = new LinkedHashMap<>();
    private final Map<UUID, List<Arrow>> arrowFences = new LinkedHashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < STARTUP || f.phase > ENDED || !MultiAxisReflectionMesh.validPoint(f.point)
                || !MultiAxisReflectionMesh.horizontalUnit(f.facing) || !MultiAxisReflectionMesh.validPoint(f.arrowPoint)
                || f.remainingHits < 0 || f.remainingHits > 4 || f.liveMask < 0 || f.liveMask > 15
                || f.arrowUuid == null || !validAmounts(f.available, f.capacity) || f.phase == ENDED && (f.remainingHits != 0 || f.liveMask != 0)
                || f.remainingHits == 0 && f.liveMask != 0
                || f.phase == ENDED && (!f.available.equals("0") || !f.capacity.equals("0"))) return false;
        boolean event = f.phase == REFLECT || f.phase == CONTACT;
        return event ? f.facet >= 0 && f.facet < 4 && f.arrowEntity >= 0 && !f.arrowUuid.equals(ZERO_UUID)
                && f.point.distanceSquared(f.arrowPoint) <= 25.000001
                : f.facet == -1 && f.arrowEntity == -1 && f.arrowUuid.equals(ZERO_UUID) && f.arrowPoint.equals(f.point);
    }
    private static boolean validAmounts(String available, String capacity) {
        if (available == null || capacity == null || available.length() > 1024 || capacity.length() > 1024) return false;
        try { return HighPowerRecoveryHudModel.decimal(available).compareTo(HighPowerRecoveryHudModel.decimal(capacity)) <= 0; }
        catch (IllegalArgumentException invalid) { return false; }
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        boolean same = old != null && old.frame.id.equals(f.id);
        if (old != null) {
            int order = compare(f.id, old.frame.id);
            if (order < 0 || order == 0 && (!same || old.retired || f.sequence <= old.frame.sequence)
                    || order > 0 && f.id.token <= old.frame.id.token) return false;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) {
            Controller remote = local ? active.values().stream().filter(c -> !c.local).findFirst().orElse(null) : null;
            if (remote == null) return false;
            retire(remote.id());
        }
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED));
        if (f.phase == ENDED) { active.remove(f.id.owner); arrowFences.remove(f.id.owner); return true; }
        if (!same) arrowFences.remove(f.id.owner);
        Controller prior = same ? active.get(f.id.owner) : null;
        boolean continuous = prior != null && prior.observedBody.distanceSquared(f.point) <= OWNER_DISCONTINUITY_SQUARED;
        var traces = new ArrayList<Trace>(continuous ? prior.traces : List.of());
        Frame contact = continuous ? prior.contact : null;
        int contactAge = continuous ? prior.contactAge : CONTACT_TICKS;
        if (f.phase == REFLECT) {
            Trace previous = traces.stream().filter(t -> t.arrow.equals(f.arrow())).findFirst().orElse(null);
            if (previous == null) {
                var seen = new ArrayList<>(arrowFences.getOrDefault(f.id.owner, List.of()));
                if (!seen.contains(f.arrow())) {
                    // Native observation lives for 20 ticks with at most four admissions in that window.
                    // Eight retained arrow identities cover it without extending any visible effect.
                    if (seen.size() == MAX_ARROW_WATERMARKS) seen.removeFirst();
                    seen.add(f.arrow()); arrowFences.put(f.id.owner, List.copyOf(seen));
                    if (traces.size() == MAX_TRACES) traces.removeFirst();
                    traces.add(new Trace(f.arrow(), f.arrowPoint, f.facet, 0, List.of(f.arrowPoint), false));
                }
            } else if (!previous.clientObserved) {
                traces.remove(previous);
                // Once live client observation begins, delayed server positions cannot zigzag the path.
                Trace next = append(previous, f.arrowPoint, false);
                if (next != null) traces.add(next);
            }
        } else if (f.phase == CONTACT) {
            contact = f; contactAge = 0;
            traces.removeIf(t -> t.arrow.equals(f.arrow()));
        }
        active.put(f.id.owner, new Controller(f, local, 0, traces, contact, contactAge, f.point));
        return true;
    }
    private static int compare(Identity a, Identity b) {
        int epoch = Long.compare(a.epoch, b.epoch);
        return epoch != 0 ? epoch : Long.compare(a.input, b.input);
    }
    private static Trace append(Trace trace, Point point, boolean clientObserved) {
        Point last = trace.points.getLast();
        if (last.equals(point)) return clientObserved && !trace.clientObserved
                ? new Trace(trace.arrow, trace.reflectionPoint, trace.facet, trace.age, trace.points, true) : trace;
        if (last.distanceSquared(point) > MAX_TRACE_STEP_SQUARED) return null;
        var points = new ArrayList<>(trace.points);
        if (points.size() == MAX_TRACE_POINTS) points.removeFirst();
        points.add(point);
        return new Trace(trace.arrow, trace.reflectionPoint, trace.facet, trace.age, points, clientObserved || trace.clientObserved);
    }
    /** Called only with a currently loaded, identity-verified arrow's actual world position. */
    public void observe(UUID owner, Arrow arrow, Point position) {
        Controller c = active.get(owner);
        if (c == null || arrow == null || !MultiAxisReflectionMesh.validPoint(position)) return;
        var traces = c.traces.stream().map(t -> t.arrow.equals(arrow) ? append(t, position, true) : t).filter(java.util.Objects::nonNull).toList();
        active.put(owner, new Controller(c.frame, c.local, c.quiet, traces, c.contact, c.contactAge, c.observedBody));
    }
    public void forgetArrow(UUID owner, Arrow arrow) {
        Controller c = active.get(owner); if (c == null) return;
        var traces = c.traces.stream().filter(t -> !t.arrow.equals(arrow)).toList();
        active.put(owner, new Controller(c.frame, c.local, c.quiet, traces, c.contact, c.contactAge, c.observedBody));
    }
    /** Teleports clear transient cues, without sending a control abort or drawing a connecting trail. */
    public void observeBody(UUID owner, Point body) {
        Controller c = active.get(owner); if (c == null || !MultiAxisReflectionMesh.validPoint(body)) return;
        boolean continuous = c.observedBody.distanceSquared(body) <= OWNER_DISCONTINUITY_SQUARED;
        active.put(owner, new Controller(c.frame, c.local, c.quiet, continuous ? c.traces : List.of(),
                continuous ? c.contact : null, c.contactAge, body));
    }
    public List<Identity> tick(boolean paused, Predicate<Controller> validOwner) {
        var losses = new ArrayList<Identity>();
        for (Controller c : List.copyOf(active.values())) {
            if (!validOwner.test(c)) { retire(c.id()); losses.add(c.id()); }
            else if (!paused) {
                var traces = c.traces.stream().filter(t -> t.age + 1 < TRACE_TICKS)
                        .map(t -> new Trace(t.arrow, t.reflectionPoint, t.facet, t.age + 1, t.points, t.clientObserved)).toList();
                int contactAge = Math.min(CONTACT_TICKS, c.contactAge + 1);
                active.put(c.id().owner, new Controller(c.frame, c.local, Math.min(QUIET_TICKS, c.quiet + 1), traces,
                        contactAge < CONTACT_TICKS ? c.contact : null, contactAge, c.observedBody));
            }
        }
        return List.copyOf(losses);
    }
    public Controller current(UUID owner) { return active.get(owner); }
    public List<Controller> controllers() { return List.copyOf(active.values()); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public void retire(Identity id) {
        if (id == null) return;
        Fence old = fences.get(id.owner);
        if (old != null && old.frame.id.equals(id)) { fences.put(id.owner, new Fence(old.frame, true)); active.remove(id.owner); arrowFences.remove(id.owner); }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        for (Controller c : List.copyOf(active.values()))
            if (c.local && (c.id().epoch < epoch || c.id().epoch == epoch && c.id().input <= input)) retire(c.id());
    }
    public void clear() { for (Controller c : List.copyOf(active.values())) retire(c.id()); }
    public void resetSession() { active.clear(); fences.clear(); arrowFences.clear(); cancelledEpoch = cancelledThrough = 0; }
}
