package cn.academy.port.client;

import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Engine-free NEW253 receipt, fixed-node and immutable-lifetime presentation fences. */
public final class RemoteScatterRelayState {
    public static final int ACTIVE = 0, EDIT = 1, FIRE = 2, ENDED = 3;
    public static final int PREPARED = 0, MISS = 1, BLOCKED = 2, REFUSED = 3, DAMAGED = 4, ABSORBED = 5, DEGENERATE = 6, ENDPOINT = 7;
    public static final int TOO_FAR = 8, SEGMENT_TOO_FAR = 9, TOO_CLOSE = 10, FULL = 11, BODY_BLOCKED = 12, NO_SPACE = 13, UNLOADED = 14, MEASURING = 15, CLEARING = 16;
    public static final int MAX_NODES = 4, MAX_SEGMENTS = 5, LIFE_TICKS = cn.academy.port.highpower.core.RemoteScatterRelayRules.LIFETIME, QUIET_TICKS = 12, EDIT_TICKS = 100, FLASH_TICKS = 6;
    public static final double MAX_SEGMENT_LENGTH = 32, MAX_LENGTH = 160;
    public static final int MAX_CONTEXTS = 64, MAX_WATERMARKS = 1024;
    private static final double EPS = 1e-7;
    private static final UUID ZERO_UUID = new UUID(0, 0);
    private static final Vector ZERO = new Vector(0, 0, 0);
    public record Identity(int entity, UUID owner, long epoch, long input, long token) {}
    public record Node(int index, Vector center, Vector normal) {
        public Node {
            if (index < 0 || index >= MAX_NODES) throw new IllegalArgumentException("Relay node index out of range");
            // Only borrow the original physical ellipse, never its two-reflection gameplay rules.
            new FoldedMirrorGeometry.Plate(0, center, normal);
        }
    }
    public record Segment(Vector start, Vector end) {}
    public record Frame(Identity id, long sequence, int phase, int status, int remainingTicks,
                        Vector ownerFeet, List<Node> plates, Vector ghostCenter, Vector ghostNormal, List<Segment> path) {
        public Frame { if (plates != null) plates = List.copyOf(plates); if (path != null) path = List.copyOf(path); }
    }
    public record Status(int count, int seconds, String key, boolean confirmedDamage) {}
    private record Fence(Frame frame, boolean retired, Vector endpoint) {}
    private final Map<UUID, Frame> active = new HashMap<>();
    private final Map<UUID, Fence> fences = new HashMap<>();
    private long cancelledEpoch, cancelledThrough;
    private static boolean point(Vector p) { return p != null && p.finite(); }
    public static boolean direction(Vector p) { return point(p) && Math.abs(p.length() - 1) <= 1e-10; }
    public static boolean hasGhost(Frame f) { return f != null && f.phase == EDIT && direction(f.ghostNormal); }
    public static boolean hasEndpoint(Frame f) {
        return f != null && f.phase != ENDED && f.phase != EDIT && !f.plates.isEmpty()
                && point(f.ghostCenter) && (f.status == ENDPOINT || !ZERO.equals(f.ghostCenter));
    }
    public static boolean eligible(int level, boolean parentLearned, boolean learned) { return level == 5 && parentLearned && learned; }
    public static boolean recallAvailable(Frame frame, int remaining, int quiet) {
        return valid(frame) && frame.phase != ENDED && !frame.plates.isEmpty()
                && remaining > 0 && remaining <= LIFE_TICKS && quiet >= 0 && quiet < QUIET_TICKS;
    }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || f.id.entity < 0 || f.id.owner == null || f.id.owner.equals(ZERO_UUID)
                || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0 || f.sequence <= 0
                || f.phase < ACTIVE || f.phase > ENDED || f.status < PREPARED || f.status > CLEARING
                || f.remainingTicks < 0 || f.remainingTicks > LIFE_TICKS
                || !point(f.ownerFeet) || !point(f.ghostCenter) || !point(f.ghostNormal)
                || f.ghostNormal.length() != 0 && !direction(f.ghostNormal)
                || f.phase != EDIT && f.ghostNormal.length() != 0
                || f.plates == null || f.plates.size() > MAX_NODES || f.path == null || f.path.size() > MAX_SEGMENTS
                || f.path.size() > f.plates.size() + 1
                || f.phase != FIRE && f.phase != ENDED && (f.status == DAMAGED || f.status == ABSORBED)
                || f.phase == FIRE && (f.plates.isEmpty() || f.status != MISS && f.status != BLOCKED && f.status != DAMAGED && f.status != ABSORBED)
                || f.phase == ENDED && (!f.path.isEmpty() || f.remainingTicks != 0)) return false;
        if (hasGhost(f)) try { new Node(0, f.ghostCenter, f.ghostNormal); } catch (IllegalArgumentException invalid) { return false; }
        for (int i = 0; i < f.plates.size(); i++) {
            Node node = f.plates.get(i);
            if (node == null || node.index != i || !point(node.center) || !direction(node.normal)) return false;
        }
        double total = 0;
        for (int i = 0; i < f.path.size(); i++) {
            Segment leg = f.path.get(i);
            if (leg == null || !point(leg.start) || !point(leg.end)) return false;
            double length = leg.end.subtract(leg.start).length();
            if (!(length > 0) || !Double.isFinite(length) || length > MAX_SEGMENT_LENGTH + EPS) return false;
            total += length;
            if (total > MAX_LENGTH + EPS) return false;
            if (i > 0 && (f.path.get(i - 1).end.subtract(leg.start).length() > EPS
                    || leg.start.subtract(f.plates.get(i - 1).center).length() > EPS)) return false;
        }
        return true;
    }
    public boolean accept(Frame f, boolean local) {
        if (!valid(f) || local && (f.id.epoch < cancelledEpoch || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough)) return false;
        Fence fence = fences.get(f.id.owner);
        Vector endpoint = hasEndpoint(f) ? f.ghostCenter : null;
        if (fence == null && fences.size() >= MAX_WATERMARKS) return false;
        if (fence != null) {
            Frame old = fence.frame;
            if (f.id.epoch < old.id.epoch || f.id.token < old.id.token) return false;
            if (f.id.token == old.id.token) {
                if (fence.retired || f.id.entity != old.id.entity || f.id.epoch != old.id.epoch
                        || f.id.input < old.id.input || f.sequence <= old.sequence
                        || !old.plates.isEmpty() && f.remainingTicks > old.remainingTicks) return false;
                if (f.phase != ENDED) {
                    if (f.plates.size() < old.plates.size()) return false;
                    for (int i = 0; i < old.plates.size(); i++) if (!old.plates.get(i).equals(f.plates.get(i))) return false;
                    boolean appended = f.plates.size() == old.plates.size() + 1;
                    if (!appended && endpoint != null && fence.endpoint != null && !endpoint.equals(fence.endpoint)) return false;
                    if (!appended && endpoint == null) endpoint = fence.endpoint;
                }
            } else if (f.id.epoch == old.id.epoch && f.id.input <= old.id.input) return false;
        }
        if (f.phase != ENDED && !active.containsKey(f.id.owner) && active.size() >= MAX_CONTEXTS) return false;
        fences.put(f.id.owner, new Fence(f, f.phase == ENDED, endpoint));
        if (f.phase == ENDED) active.remove(f.id.owner); else active.put(f.id.owner, f);
        return true;
    }
    public void retire(Identity id) {
        if (id == null) return;
        Fence fence = fences.get(id.owner);
        if (fence != null && fence.frame.id.token == id.token && fence.frame.id.epoch == id.epoch) {
            fences.put(id.owner, new Fence(fence.frame, true, fence.endpoint)); active.remove(id.owner);
        }
    }
    public void cancel(long epoch, long input) {
        if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
        if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
        cancelledThrough = Math.max(cancelledThrough, input);
    }
    public void clear() { for (Frame frame : List.copyOf(active.values())) retire(frame.id); }
    public void resetSession() { active.clear(); fences.clear(); cancelledEpoch = cancelledThrough = 0; }
    public Frame current(UUID owner) { return active.get(owner); }
    public int size() { return active.size(); }
    public int watermarkCount() { return fences.size(); }
    public static Status status(Frame f, int remaining) {
        String key = switch (f.status) {
            case ENDPOINT -> "endpoint"; case DEGENERATE -> "degenerate"; case REFUSED -> "refused"; case BLOCKED -> "blocked";
            case DAMAGED -> "damaged"; case ABSORBED -> "absorbed"; case MISS -> "miss";
            case TOO_FAR -> "too_far"; case SEGMENT_TOO_FAR -> "segment_too_far"; case TOO_CLOSE -> "too_close";
            case FULL -> "full"; case BODY_BLOCKED -> "body_blocked"; case NO_SPACE -> "no_space"; case UNLOADED -> "unloaded";
            case MEASURING -> "measuring"; case CLEARING -> "clearing";
            case PREPARED -> f.phase == ENDED ? "ended" : f.phase == EDIT ? "placing" : "prepared";
            default -> "refused";
        };
        return new Status(f.plates.size(), f.plates.isEmpty() ? -1 : (Math.max(0, Math.min(LIFE_TICKS, remaining)) + 19) / 20,
                key, f.phase == FIRE && f.status == DAMAGED);
    }
    /** Empty first-placement failures must remain readable after the server immediately retires the context. */
    public static Status terminalStatus(Frame previous, Frame ended) {
        boolean emptyRefusal = previous != null && previous.plates.isEmpty()
                && (previous.status == BLOCKED || previous.status == REFUSED || previous.status == DEGENERATE
                || previous.status >= TOO_FAR && previous.status <= UNLOADED);
        return status(emptyRefusal ? previous : ended, 0);
    }
    /** Text only: notify after receipt acceptance and local identity gates, never from prediction or a render pass. */
    public static final class ResultNote {
        public static final int TICKS = 25;
        private Identity identity;
        private long sequence;
        private int remaining;
        private boolean ended;
        private Status result;
        public void received(Frame frame) {
            if (!valid(frame)) return;
            if (frame.id.equals(identity)) {
                if (ended || frame.sequence <= sequence) return;
            } else {
                clear(); identity = frame.id; ended = false;
            }
            sequence = frame.sequence;
            if (frame.phase == EDIT || frame.phase == ENDED) {
                ended = frame.phase == ENDED; clear();
            } else if (frame.phase == FIRE) {
                result = RemoteScatterRelayState.status(frame, frame.remainingTicks); remaining = TICKS;
            }
            // A later ACTIVE heartbeat keeps the last actual outcome without renewing its text lifetime.
        }
        /** Hidden/invalid presentation discards the note immediately, so it cannot replay after resuming. */
        public void tick(boolean paused, boolean visible) {
            if (paused || !visible) clear();
            else if (remaining > 0 && --remaining == 0) clear();
        }
        /** A fresh local press fences the previous result identity before its replacement receipt arrives. */
        public void newInput() { clear(); if (identity != null) ended = true; }
        /** Abort, reload, world and session owners clear presentation without opening its last replay fence. */
        public void clear() { result = null; remaining = 0; }
        /** Only key/confirmedDamage are result text; current node count and lifetime still come from the live lease. */
        public Status status() { return result; }
        public int remaining() { return remaining; }
    }
    /** Server refreshes may lower this lifetime; placement, endpoint selection and firing cannot renew it. */
    public static final class Lease {
        private int remaining, quiet, editAge;
        private boolean received, paid, ended;
        public void received(Frame frame) {
            quiet = 0;
            boolean nowPaid = !frame.plates.isEmpty();
            remaining = !received || !paid && nowPaid ? frame.remainingTicks : Math.min(remaining, frame.remainingTicks);
            paid |= nowPaid; received = true; ended = frame.phase == ENDED;
        }
        public boolean tick(boolean paused, boolean valid) {
            if (!valid || ended) return false;
            if (!paused) { if (remaining > 0) remaining--; quiet = Math.min(QUIET_TICKS, quiet + 1); if (!paid) editAge++; }
            // A packet gap hides stale presentation, but cannot revoke a still-paid server token.
            return paid ? remaining > 0 : editAge < EDIT_TICKS;
        }
        public boolean visible() { return received && !ended && quiet < QUIET_TICKS && (paid ? remaining > 0 : editAge < EDIT_TICKS); }
        public int remaining() { return remaining; }
        public int quiet() { return quiet; }
    }
}
