package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** NEW311 receipts distinguish committed relocation from a later native damage decision. */
public final class DisplacedProjectionState {
    public static final int PROJECTED = 1, IMPACT = 2, REFUSED = 3, CANCELLED = 4, MAX_WATERMARKS = 1024;
    private static final UUID ZERO = new UUID(0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Frame(Identity id, long sequence, int phase, Point source, Point arrival, Point impact,
                        int needleEntity, UUID needleUuid, boolean accepted, String reason) {}
    private final Map<UUID, Frame> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;

    public static boolean terminal(Frame frame) { return frame != null && frame.phase != PROJECTED; }
    public static boolean valid(Frame frame) {
        if (frame == null || frame.id == null || frame.id.entity < 0 || !uuid(frame.id.owner)
                || frame.id.epoch <= 0 || frame.id.input <= 0 || frame.id.token <= 0 || frame.sequence <= 0
                || frame.phase < PROJECTED || frame.phase > CANCELLED || frame.reason == null || frame.reason.length() > 128
                || !optional(frame.source) || !optional(frame.arrival) || !optional(frame.impact)) return false;
        if (frame.phase == REFUSED || frame.phase == CANCELLED) return !frame.accepted;
        return frame.source != null && frame.arrival != null && frame.impact != null
                && frame.needleEntity >= 0 && uuid(frame.needleUuid) && (frame.phase == IMPACT || !frame.accepted);
    }
    public boolean accept(Frame frame, boolean local) {
        if (!valid(frame) || local && (frame.id.epoch < cancelledEpoch
                || frame.id.epoch == cancelledEpoch && frame.id.input <= cancelledThrough)) return false;
        Frame previous = fences.get(frame.id.owner);
        if (previous == null && fences.size() >= MAX_WATERMARKS) return false;
        if (previous != null && previous.id.equals(frame.id)) {
            if (terminal(previous) || frame.sequence <= previous.sequence || frame.phase == PROJECTED
                    || frame.phase == IMPACT && (frame.needleEntity != previous.needleEntity
                    || !frame.needleUuid.equals(previous.needleUuid))) return false;
        } else {
            // Contact alone must never invent a projectile, relocation or previously unseen cast.
            if (frame.phase == IMPACT || !local && frame.phase == REFUSED) return false;
            if (previous != null && (frame.id.epoch < previous.id.epoch || frame.id.epoch == previous.id.epoch
                    && (frame.id.input <= previous.id.input || frame.id.token <= previous.id.token))) return false;
        }
        fences.put(frame.id.owner, frame); return true;
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public int watermarkCount() { return fences.size(); }
    public void resetSession() { fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    private static boolean uuid(UUID value) { return value != null && !value.equals(ZERO); }
    private static boolean optional(Point point) { return point == null || point.finite(); }
}
