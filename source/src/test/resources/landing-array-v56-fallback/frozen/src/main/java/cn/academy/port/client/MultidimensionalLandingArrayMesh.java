package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** NEW351 private frozen receipt geometry and bounded visual history; no target tracking or impact prediction. */
public final class MultidimensionalLandingArrayMesh {
    public static final int MARKED = 1, PREVIEW = 2, PROJECTED = 3, IMPACT = 4, ENDED = 5, REFUSED = 6,
            ROUND = 7, FREE_ORIGIN = ROUND, RECOVERY = 8;
    public static final int PLAN_TICKS = 400, ROUND_TICKS = 160, HEARTBEAT_TIMEOUT_TICKS = 30;
    public static final int MAX_MARKERS = 6, MAX_TARGET_MARKERS = 16, MAX_SHOTS = 16, MAX_CUES = 6,
            MAX_IN_FLIGHT = 6, POINT_STROKES = 8, TARGET_STROKES = 4, BIPED_QUADS = 36, SUCCESS_TICKS = 7;
    // Includes every owned decorative stroke/quad; native item/entity model geometry is rendered by its original renderer.
    public static final int MAX_DECOR_PRIMITIVES = MAX_MARKERS * (POINT_STROKES + BIPED_QUADS)
            + MAX_TARGET_MARKERS * TARGET_STROKES + MAX_CUES * TARGET_STROKES * 3;
    public static final double CULL_DISTANCE = 96, ORIGIN_HEIGHT = 1.2, NEAR_PLANE = .08;
    public static final double ATLAS_HALF_WIDTH = .009, ENVELOPE_HALF_WIDTH = .024;
    public static final int MAX_LABELS = MAX_MARKERS + MAX_TARGET_MARKERS + MAX_CUES, MAX_LABEL_HEIGHT = 7;
    private static final UUID ZERO = new UUID(0, 0);
    public record Identity(UUID owner, long epoch, long input, long token) {}
    public record Target(UUID uuid, int entity, Point position, boolean available) {}
    public record Frame(Identity id, long sequence, int phase, List<Point> points, int remaining,
                        List<Target> targets, int shotsTotal, int spent, int published, int retained, int pending,
                        boolean freeOrigin, Point source, Point impact, UUID medium, int slot, int shotIndex,
                        boolean accepted, boolean terminal, String reason) {
        public Frame {
            if (points != null) points = Collections.unmodifiableList(new ArrayList<>(points));
            if (targets != null) targets = Collections.unmodifiableList(new ArrayList<>(targets));
        }
    }
    public record Line(Point from, Point to) {}
    private MultidimensionalLandingArrayMesh() {}
    public static boolean uuid(UUID id) { return id != null && !ZERO.equals(id); }
    public static boolean point(Point p) {
        return p != null && p.finite() && Math.abs(p.x()) <= 30_000_001 && Math.abs(p.z()) <= 30_000_001
                && Math.abs(p.y()) <= 1_000_000;
    }
    public static int requiredShots(int targets) { return targets >= 1 && targets <= MAX_TARGET_MARKERS ? Math.max(MAX_MARKERS, targets) : 0; }
    public static int targetShares(int targets, int target) {
        int shots = requiredShots(targets);
        return shots == 0 || target < 0 || target >= targets ? 0 : 1 + (shots - 1 - target) / targets;
    }
    public static boolean preparation(int phase) { return phase == MARKED || phase == PREVIEW; }
    public static boolean round(int phase) { return phase == ROUND || phase == PROJECTED || phase == IMPACT; }
    public static boolean terminal(Frame frame) { return frame != null && frame.terminal; }
    public static boolean sameController(Identity a, Identity b) {
        return a != null && b != null && Objects.equals(a.owner, b.owner) && a.epoch == b.epoch && a.token == b.token;
    }
    public static boolean valid(Frame f) {
        if (f == null || f.id == null || !uuid(f.id.owner) || f.id.epoch <= 0 || f.id.input <= 0 || f.id.token <= 0
                || f.sequence <= 0 || f.phase < MARKED || f.phase > RECOVERY || f.reason == null || f.reason.length() > 128
                || f.points == null || f.points.size() > MAX_MARKERS || f.points.stream().anyMatch(p -> !point(p))
                || f.targets == null || f.targets.size() > MAX_TARGET_MARKERS
                || f.remaining < 0 || f.remaining > PLAN_TICKS || f.shotsTotal < 0 || f.shotsTotal > MAX_SHOTS
                || f.spent < 0 || f.spent > f.shotsTotal || f.published < 0 || f.published > f.spent
                || f.retained < 0 || f.retained > MAX_MARKERS || f.pending < 0 || f.pending > f.retained
                || !point(f.source) || !point(f.impact) || f.medium == null) return false;
        if ((f.phase == ENDED || f.phase == RECOVERY) && !f.terminal
                || f.terminal && f.phase != ENDED && f.phase != REFUSED && f.phase != RECOVERY) return false;
        var identities = new HashSet<UUID>(); var entities = new HashSet<Integer>();
        for (var t : f.targets) if (t == null || !uuid(t.uuid) || t.entity < 0 || !point(t.position)
                || !identities.add(t.uuid) || !entities.add(t.entity)) return false;
        if (!f.targets.isEmpty() && f.shotsTotal != requiredShots(f.targets.size())) return false;
        if (f.phase == MARKED) return !f.points.isEmpty() && f.remaining > 0 && f.targets.isEmpty()
                && f.shotsTotal == 0 && f.spent == 0 && f.published == 0 && f.accepted && noMedium(f) && !f.freeOrigin;
        if (f.phase == PREVIEW) return f.points.size() == MAX_MARKERS && !f.targets.isEmpty() && f.remaining > 0
                && f.spent == 0 && f.published == 0 && f.accepted && noMedium(f) && !f.freeOrigin;
        if (round(f.phase)) {
            if (f.points.size() != MAX_MARKERS || f.targets.isEmpty() || f.remaining > ROUND_TICKS) return false;
            if (f.phase == ROUND) return f.accepted && noMedium(f);
            return uuid(f.medium) && f.shotIndex >= 0 && f.shotIndex < f.shotsTotal && f.slot == f.shotIndex % MAX_MARKERS
                    && f.spent > f.shotIndex && f.published > 0 && (f.phase == IMPACT || f.accepted)
                    && (f.phase != PROJECTED || f.freeOrigin || f.source.equals(origin(f.points.get(f.slot))));
        }
        return f.remaining == 0 && noMedium(f) && (f.phase != REFUSED || !f.accepted);
    }
    private static boolean noMedium(Frame f) { return ZERO.equals(f.medium) && f.slot == -1 && f.shotIndex == -1; }
    private static boolean sameTargets(List<Target> before, List<Target> next) {
        if (before.size() != next.size()) return false;
        for (int i = 0; i < before.size(); i++) {
            var a = before.get(i); var b = next.get(i);
            if (!a.uuid.equals(b.uuid) || a.entity != b.entity || !a.position.equals(b.position)
                    || !a.available && b.available) return false;
        }
        return true;
    }

    /** One owner-private controller. A new connection resets history; visual silence never clears gameplay ownership. */
    public static final class VisualState {
        private Frame active, latest;
        private final Frame[] shots = new Frame[MAX_SHOTS];
        private int remaining, quiet, roundOwnership;
        private long previewInput, roundInput, sequence, cancelledEpoch, cancelledThrough;
        private boolean retired, firing;
        public boolean accept(Frame f, boolean local) {
            if (!local || !valid(f) || f.id.epoch < cancelledEpoch
                    || f.id.epoch == cancelledEpoch && f.id.input <= cancelledThrough) return false;
            boolean same = latest != null && sameController(latest.id, f.id);
            // A rejected gesture has no authority to rewrite a previously confirmed plan or round.
            if (f.phase == REFUSED && !f.terminal) {
                if (same && (retired || f.sequence <= sequence)) return false;
                if (same) sequence = f.sequence;
                return latest == null || f.id.owner.equals(latest.id.owner) && (f.id.epoch > latest.id.epoch
                        || f.id.epoch == latest.id.epoch && f.id.token >= latest.id.token);
            }
            if (latest != null) {
                if (!latest.id.owner.equals(f.id.owner) || f.id.epoch < latest.id.epoch
                        || f.id.epoch == latest.id.epoch && f.id.token < latest.id.token) return false;
                if (same) {
                    if (retired || f.sequence <= sequence || f.id.input < latest.id.input
                            || f.points.size() < latest.points.size() || !f.points.subList(0, latest.points.size()).equals(latest.points)
                            || f.spent < latest.spent || f.published < latest.published) return false;
                    if (preparation(f.phase) && (firing || f.remaining > latest.remaining)) return false;
                    if (f.points.size() > latest.points.size() && (f.phase != MARKED || latest.phase != MARKED
                            || f.id.input <= latest.id.input || f.points.size() != latest.points.size() + 1)) return false;
                    if (!latest.targets.isEmpty() && (!sameTargets(latest.targets, f.targets) || f.shotsTotal != latest.shotsTotal)) return false;
                    if (f.phase == MARKED && latest.phase != MARKED) return false;
                    if (firing && round(f.phase) && (f.id.input != roundInput || f.freeOrigin != latest.freeOrigin
                            || f.remaining > latest.remaining)) return false;
                } else if (f.id.epoch == latest.id.epoch && f.id.input <= latest.id.input) return false;
            }
            if (!same && !terminal(f) && f.phase != MARKED) return false;
            if (!same && f.phase == MARKED && f.points.size() != 1) return false;
            if (f.phase == PREVIEW && (!same || latest.points.size() != MAX_MARKERS || firing
                    || latest.targets.isEmpty() && f.id.input <= latest.id.input
                    || !latest.targets.isEmpty() && f.id.input != previewInput)) return false;
            if (round(f.phase) && !firing && (f.phase != ROUND || !same || latest.phase != PREVIEW
                    || f.id.input <= previewInput)) return false;
            if (f.phase == PROJECTED) {
                if (shots[f.shotIndex] != null) return false;
                for (var shot : shots) if (shot != null && (shot.medium.equals(f.medium)
                        || shot.phase == PROJECTED && shot.slot == f.slot)) return false;
                shots[f.shotIndex] = f;
            } else if (f.phase == IMPACT) {
                var before = shots[f.shotIndex];
                if (before == null || before.phase != PROJECTED || before.slot != f.slot || !before.medium.equals(f.medium)
                        || !before.source.equals(f.source)) return false;
                shots[f.shotIndex] = f;
            }
            if (!same) {
                java.util.Arrays.fill(shots, null); previewInput = roundInput = 0; firing = false; roundOwnership = 0;
            }
            int lease = same && active != null && preparation(f.phase) ? Math.min(remaining, f.remaining) : f.remaining;
            if (f.phase == PREVIEW) previewInput = f.id.input;
            if (f.phase == ROUND && !firing) {
                roundInput = f.id.input; roundOwnership = MultidimensionalLandingArrayInputState.ROUND_OWNERSHIP_TICKS; firing = true;
            }
            latest = f; sequence = f.sequence; retired = terminal(f); active = retired ? null : f; remaining = lease; quiet = 0;
            if (retired) firing = false;
            return true;
        }
        public Frame active() { return active; }
        public int remainingTicks() { return remaining; }
        public int visibleAge() { return quiet; }
        public boolean markersVisible() { return active != null && remaining > 0 && quiet < HEARTBEAT_TIMEOUT_TICKS; }
        public boolean targetsFrozen() { return active != null && active.phase == PREVIEW && remaining > 0; }
        public boolean firing() { return active != null && firing; }
        public int inFlight() {
            int n = 0; for (var shot : shots) if (shot != null && shot.phase == PROJECTED) n++; return n;
        }
        public Identity tick(boolean paused, boolean validOwner) {
            if (active == null) return null;
            var id = active.id;
            if (!validOwner) { retire(id); return id; }
            if (!paused) {
                quiet = Math.min(HEARTBEAT_TIMEOUT_TICKS, quiet + 1); if (remaining > 0) remaining--;
                if (roundOwnership > 0) roundOwnership--;
            }
            // Preparation and conservative ownership deadlines are genuine expiry, unlike a missing visual heartbeat.
            if (preparation(active.phase) && remaining == 0 || firing && roundOwnership == 0) { retire(id); return id; }
            return null;
        }
        public void retire(Identity id) {
            if (id != null && latest != null && sameController(latest.id, id)) {
                retired = true; active = null; remaining = quiet = roundOwnership = 0; firing = false;
                java.util.Arrays.fill(shots, null);
            }
        }
        public void cancel(long epoch, long input) {
            if (epoch <= 0 || input <= 0 || epoch < cancelledEpoch) return;
            if (epoch > cancelledEpoch) { cancelledEpoch = epoch; cancelledThrough = 0; }
            cancelledThrough = Math.max(cancelledThrough, input);
            if (active != null && active.id.epoch == epoch && active.id.input <= input) retire(active.id);
        }
        public void clear() { if (active != null) retire(active.id); }
        public void resetSession() {
            active = latest = null; java.util.Arrays.fill(shots, null); remaining = quiet = roundOwnership = 0;
            previewInput = roundInput = sequence = cancelledEpoch = cancelledThrough = 0; retired = firing = false;
        }
    }
    public static List<Line> marker(Point feet) {
        if (!point(feet)) return List.of();
        var result = new ArrayList<Line>(POINT_STROKES);
        for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
            var corner = new Point(feet.x() + x * .34, feet.y() + .03, feet.z() + z * .34);
            result.add(new Line(corner, new Point(feet.x() + x * .17, feet.y() + .03, corner.z())));
            result.add(new Line(corner, new Point(corner.x(), feet.y() + .03, feet.z() + z * .17)));
        }
        return List.copyOf(result);
    }
    public static List<Line> contact(Point at) {
        if (!point(at)) return List.of(); double r = .16;
        var a = new Point(at.x()-r, at.y()+.02, at.z()-r); var b = new Point(at.x()+r, at.y()+.02, at.z()-r);
        var c = new Point(at.x()+r, at.y()+.02, at.z()+r); var d = new Point(at.x()-r, at.y()+.02, at.z()+r);
        return List.of(new Line(a,b),new Line(b,c),new Line(c,d),new Line(d,a));
    }
    public static Box markBounds(Point feet) {
        return !point(feet) ? null : new Box(feet.x()-.3, feet.y(), feet.z()-.3, feet.x()+.3, feet.y()+1.8, feet.z()+.3);
    }
    public static Point origin(Point feet) { return !point(feet) ? null : new Point(feet.x(), feet.y()+ORIGIN_HEIGHT, feet.z()); }
    /** Labels share a reference plane but occupy separate billboard rows; authoritative glyphs never move. */
    public static Point labelAnchor(Point at) { return !point(at) ? null : new Point(at.x(), at.y()+.35, at.z()); }
    public static int labelOffset(boolean receipt, int nearbyOrdinal, int fontHeight) {
        int row = Math.max(0, Math.min(MAX_TARGET_MARKERS-1, nearbyOrdinal));
        return receipt ? 2 + row * (fontHeight+3) : -(row+1) * (fontHeight+3);
    }
    public static boolean nearbyLabel(Point a, Point b) {
        if (!point(a) || !point(b)) return false;
        double x=a.x()-b.x(), y=a.y()-b.y(), z=a.z()-b.z();
        return x*x+y*y+z*z <= .75*.75;
    }
    /** Only NEW351 annotations fade near the eye; coordinates and native models retain their exact geometry. */
    public static int labelAlpha(double depth) {
        if (!Double.isFinite(depth) || depth <= .6) return 0;
        int alpha=(int)(230*Math.min(1,(depth-.6)/1.4));
        return alpha < 8 ? 0 : alpha; // Font's legacy low-alpha normalization must never turn a fade into opaque text.
    }
    public static float labelScale(double depth, double distance, int pixels, int fontHeight, double projectionY, int guiHeight) {
        if (labelAlpha(depth)==0 || !Double.isFinite(distance) || distance < 0 || pixels <= 0 || fontHeight <= 0
                || !Double.isFinite(projectionY) || projectionY <= 0 || guiHeight <= 0) return 0;
        double screenBound = 2*depth*MAX_LABEL_HEIGHT/(projectionY*guiHeight*fontHeight);
        return (float)Math.min(Math.min(.022,.014+distance*.0005),screenBound);
    }
    public record LabelBounds(double left, double top, double right, double bottom) {}
    /** Call in receipt/target/anchor priority order. Suppression affects text only and never reallocates targets. */
    public static boolean admitLabel(List<LabelBounds> occupied, LabelBounds next, int width, int height) {
        if (occupied == null || occupied.size() >= MAX_LABELS || next == null || width <= 0 || height <= 0
                || !Double.isFinite(next.left) || !Double.isFinite(next.top) || !Double.isFinite(next.right) || !Double.isFinite(next.bottom)
                || next.left < 0 || next.top < 0 || next.right > width || next.bottom > height
                || next.left >= next.right || next.top >= next.bottom) return false;
        for (var before : occupied) if (next.left < before.right+2 && next.right+2 > before.left
                && next.top < before.bottom+2 && next.bottom+2 > before.top) return false;
        occupied.add(next); return true;
    }
    public static Point relative(Point point, Point camera) {
        if (point == null || camera == null || !point.finite() || !camera.finite()) return null;
        // Keep subtraction in double before any renderer converts the short vector to float.
        return new Point(point.x()-camera.x(), point.y()-camera.y(), point.z()-camera.z());
    }
    public static boolean inside(Point camera, Box bounds) {
        return camera != null && bounds != null && camera.x() >= bounds.minX() && camera.x() <= bounds.maxX()
                && camera.y() >= bounds.minY() && camera.y() <= bounds.maxY() && camera.z() >= bounds.minZ() && camera.z() <= bounds.maxZ();
    }
    public static int frame(int age) { return Math.max(0, Math.min(SUCCESS_TICKS-1, age)); }
    public static int anchorAlpha(boolean frozen) { return frozen ? 170 : 115; }
    public static int alpha(int age) { return age < 0 || age >= SUCCESS_TICKS ? 0 : 210*(SUCCESS_TICKS-age)/SUCCESS_TICKS; }
    public static int envelopeAlpha(int age) { return alpha(age)*3/4; }
}
