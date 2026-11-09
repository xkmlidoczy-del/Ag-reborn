package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** NEW313 receipt admission, immutable preview geometry and bounded non-evicting replay fences. */
public final class SafeLandingProbeState {
    public static final int PREVIEW = 0, SUCCESS = 1, ENDED = 2, REFUSED = 3;
    public static final int MAX_WATERMARKS = 1024, QUIET_TICKS = 12;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point source, Point destination,
                        Box bounds, boolean valid, String reason, int remainingTicks) {}
    private record Fence(Frame frame, boolean retired) {}
    private final Map<UUID, Fence> fences = new HashMap<>();
    private Frame preview;
    private int quiet, remaining;
    private long cancelledEpoch, cancelledThrough;

    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < PREVIEW || f.phase > REFUSED || f.remainingTicks < 0 || f.remainingTicks > cn.academy.port.highpower.core.SafeLandingProbeRules.PREVIEW_TICKS
                || f.reason == null || f.reason.length() > 128
                || f.source != null && !f.source.finite() || f.destination != null && !f.destination.finite()
                || f.bounds != null && !f.bounds.body()) return false;
        if (f.phase == PREVIEW || f.phase == SUCCESS) {
            if (!f.valid || f.source == null || f.destination == null || f.bounds == null
                    || (f.phase == PREVIEW ? !SafeLandingProbeGeometry.withinRange(f.source, f.destination)
                        : f.source.distanceSquared(f.destination) > Math.pow(cn.academy.port.highpower.core.SafeLandingProbeRules.RANGE + .251, 2))
                    || Math.abs(f.bounds.minY() - f.destination.y()) > 1e-5
                    || f.destination.x() < f.bounds.minX() || f.destination.x() > f.bounds.maxX()
                    || f.destination.z() < f.bounds.minZ() || f.destination.z() > f.bounds.maxZ()) return false;
            return f.phase == PREVIEW ? f.remainingTicks > 0 : f.remainingTicks == 0;
        }
        return f.remainingTicks == 0 && (f.phase != REFUSED || !f.valid);
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || f.phase == PREVIEW && !local || local && (f.id.epoch < cancelledEpoch
                || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence old = fences.get(f.id.owner);
        if (old == null && fences.size() >= MAX_WATERMARKS) return false;
        if (old != null) {
            var before = old.frame;
            if (f.id.epoch < before.id.epoch || f.id.epoch == before.id.epoch && f.id.token < before.id.token) return false;
            if (f.id.epoch == before.id.epoch && f.id.token == before.id.token) {
                if (old.retired || f.id.entity != before.id.entity || f.id.input < before.id.input
                        || f.sequence <= before.sequence) return false;
                if (before.phase == PREVIEW && f.phase == PREVIEW && (f.id.input != before.id.input
                        || f.remainingTicks > before.remainingTicks || !Objects.equals(f.source, before.source)
                        || !Objects.equals(f.destination, before.destination) || !Objects.equals(f.bounds, before.bounds))) return false;
                if (before.phase == PREVIEW && f.phase == SUCCESS && (f.source.distanceSquared(before.source) > .25 * .25
                        || !Objects.equals(f.destination, before.destination) || !Objects.equals(f.bounds, before.bounds))) return false;
            } else if (f.id.epoch == before.id.epoch && f.id.input <= before.id.input) return false;
        }
        fences.put(f.id.owner, new Fence(f, f.phase != PREVIEW));
        if (f.phase == PREVIEW) {
            boolean same = preview != null && preview.id.equals(f.id);
            preview = f; quiet = 0; remaining = same ? Math.min(remaining, f.remainingTicks) : f.remainingTicks;
        } else if (preview != null && sameController(preview.id, f.id)) { preview = null; quiet = remaining = 0; }
        return true;
    }
    public Frame preview() { return preview; }
    public int remainingTicks() { return remaining; }
    public int watermarkCount() { return fences.size(); }
    /** Returns the exact lost preview once. The caller must abort before discarding its input. */
    public Identity tick(boolean paused, boolean validOwner) {
        if (preview == null) return null;
        if (!paused) { quiet++; remaining = Math.max(0, remaining - 1); }
        if (validOwner && quiet < QUIET_TICKS && remaining > 0) return null;
        var id = preview.id; retire(id); return id;
    }
    public void retire(Identity id) {
        if (id == null) return;
        Fence old = fences.get(id.owner);
        if (old != null && sameController(old.frame.id, id)) fences.put(id.owner, new Fence(old.frame, true));
        if (preview != null && sameController(preview.id, id)) { preview = null; quiet = remaining = 0; }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
        if (preview != null && preview.id.epoch == epoch && preview.id.input <= input) retire(preview.id);
    }
    public void clear() { if (preview != null) retire(preview.id); }
    public void resetSession() { fences.clear(); preview = null; quiet = remaining = 0; cancelledEpoch = cancelledThrough = 0; }
    private static boolean sameController(Identity a, Identity b) {
        return a.entity == b.entity && a.owner.equals(b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
}
