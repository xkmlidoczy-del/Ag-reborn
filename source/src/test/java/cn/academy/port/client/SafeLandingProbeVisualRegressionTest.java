package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;
import java.util.UUID;

/** Receipt, replay, finite decay and exact geometry tests, with no native game or GPU. */
public final class SafeLandingProbeVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = UUID.fromString("a2510010-0000-4000-8000-000000000313");
    private static final Point SOURCE = new Point(0, 64, 0), DESTINATION = new Point(8, 64, 0);
    private static final Box BOUNDS = new Box(7.7, 64, -.3, 8.3, 65.8, .3);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static SafeLandingProbeState.Frame frame(long input, long token, long sequence, int phase, int remaining) {
        return new SafeLandingProbeState.Frame(new SafeLandingProbeState.Identity(19, OWNER, 71, input, token), sequence,
                phase, SOURCE, DESTINATION, BOUNDS, phase == SafeLandingProbeState.PREVIEW || phase == SafeLandingProbeState.SUCCESS, "", remaining);
    }
    private static SafeLandingProbeState.Frame geometry(SafeLandingProbeState.Frame f, Point source, Point destination, Box bounds) {
        return new SafeLandingProbeState.Frame(f.id(), f.sequence(), f.phase(), source, destination, bounds, f.valid(), f.reason(), f.remainingTicks());
    }
    private static boolean contains(Box b, Point p) {
        return p.x() >= b.minX() - 1e-9 && p.x() <= b.maxX() + 1e-9
                && p.y() >= b.minY() - 1e-9 && p.y() <= b.maxY() + 1e-9
                && p.z() >= b.minZ() - 1e-9 && p.z() <= b.maxZ() + 1e-9;
    }
    public static void main(String[] args) {
        var preview = frame(1, 11, 1, SafeLandingProbeState.PREVIEW, 100);
        check(SafeLandingProbeState.valid(preview), "valid bounded server box admitted");
        check(!SafeLandingProbeState.valid(geometry(preview, SOURCE, new Point(13, 64, 0), BOUNDS)), "over-range packet rejected");
        check(!SafeLandingProbeState.valid(geometry(preview, SOURCE, DESTINATION, null)), "no fabricated preview without bounds");
        check(!SafeLandingProbeState.valid(geometry(preview, new Point(Double.NaN, 64, 0), DESTINATION, BOUNDS)), "nonfinite source rejected");
        check(!SafeLandingProbeState.valid(geometry(preview, SOURCE, DESTINATION, new Box(7, 64, -1, 12, 66, 1))), "oversized body rejected");
        var unknown = new SafeLandingProbeState.Frame(preview.id(), 1, SafeLandingProbeState.REFUSED,
                SOURCE, null, null, false, "unloaded_chunk", 0);
        check(SafeLandingProbeState.valid(unknown), "refusal can omit unknown destination and box");
        check(SafeLandingProbeMesh.corners(unknown.bounds()).isEmpty(), "unknown refusal creates no geometry");

        var state = new SafeLandingProbeState();
        check(!state.accept(preview, false), "observer cannot acquire a private preview");
        check(state.accept(preview, true), "local preview accepted");
        check(!state.accept(preview, true), "duplicate packet rejected");
        check(state.accept(frame(1, 11, 2, SafeLandingProbeState.PREVIEW, 96), true), "same frozen heartbeat accepted");
        check(!state.accept(geometry(frame(1, 11, 3, SafeLandingProbeState.PREVIEW, 92), SOURCE,
                new Point(8.1, 64, 0), new Box(7.8, 64, -.3, 8.4, 65.8, .3)), true), "auto retarget rejected");
        check(!state.accept(frame(1, 11, 3, SafeLandingProbeState.PREVIEW, 100), true), "heartbeat may never extend original deadline");
        check(!state.accept(frame(2, 11, 3, SafeLandingProbeState.PREVIEW, 92), true), "confirm input cannot become a refreshed preview");
        check(!state.accept(geometry(frame(2, 11, 3, SafeLandingProbeState.SUCCESS, 0), new Point(.25001, 64, 0), DESTINATION, BOUNDS), true), "excess departure drift refused");
        var success = geometry(frame(2, 11, 3, SafeLandingProbeState.SUCCESS, 0), new Point(.25, 64, 0), DESTINATION, BOUNDS);
        check(state.accept(success, true), "actual precommit source may drift by a quarter meter");
        check(state.preview() == null, "success closes original preview");
        check(!state.accept(success, true), "success duplicate never spawns another pair");
        check(!state.accept(frame(1, 11, 4, SafeLandingProbeState.PREVIEW, 90), true), "late heartbeat cannot revive terminal token");
        check(!state.accept(frame(3, 10, 5, SafeLandingProbeState.SUCCESS, 0), false), "older token rejected even with newer input");
        check(state.accept(frame(3, 12, 1, SafeLandingProbeState.SUCCESS, 0), false), "remote real move requires no local input ownership");

        var cancelled = new SafeLandingProbeState(); cancelled.accept(preview, true); cancelled.cancel(71, 2);
        check(cancelled.preview() == null, "newer cancellation retires original preview");
        check(!cancelled.accept(frame(2, 11, 2, SafeLandingProbeState.SUCCESS, 0), true), "cancelled confirmation cannot produce delayed local success");
        check(cancelled.accept(frame(3, 12, 1, SafeLandingProbeState.PREVIEW, 100), true), "later independent input remains possible");
        cancelled.retire(preview.id()); check(cancelled.preview() != null, "stale visual retirement cannot erase new token");
        cancelled.clear(); check(!cancelled.accept(frame(3, 12, 2, SafeLandingProbeState.PREVIEW, 96), true), "clear retains replay fences");

        var lease = new SafeLandingProbeState(); lease.accept(preview, true);
        for (int tick = 0; tick < 200; tick++) check(lease.tick(true, true) == null, "paused clock remains frozen");
        for (int tick = 1; tick < SafeLandingProbeState.QUIET_TICKS; tick++) check(lease.tick(false, true) == null, "quiet deadline not early");
        check(lease.tick(false, true).equals(preview.id()), "lost receipt returns exact cancellation identity once");
        check(lease.tick(false, true) == null, "quiet expiration idempotent");
        lease.resetSession(); lease.accept(frame(1, 11, 1, SafeLandingProbeState.PREVIEW, 3), true);
        check(lease.tick(false, true) == null && lease.tick(false, true) == null, "short server TTL preserved");
        check(lease.tick(false, true) != null, "server TTL terminal at zero");
        lease.resetSession(); lease.accept(preview, true); check(lease.tick(true, false) != null, "GUI/owner loss cancels even paused");

        var bounded = new SafeLandingProbeState();
        for (int i = 0; i < SafeLandingProbeState.MAX_WATERMARKS; i++) {
            var id = new SafeLandingProbeState.Identity(19, new UUID(1, i + 1), 71, 1, 1);
            check(bounded.accept(new SafeLandingProbeState.Frame(id, 1, SafeLandingProbeState.SUCCESS,
                    SOURCE, DESTINATION, BOUNDS, true, "", 0), false), "finite distinct owner fence admitted");
        }
        var extra = new SafeLandingProbeState.Identity(19, new UUID(2, 1), 71, 1, 1);
        check(!bounded.accept(new SafeLandingProbeState.Frame(extra, 1, SafeLandingProbeState.SUCCESS,
                SOURCE, DESTINATION, BOUNDS, true, "", 0), false), "watermark capacity refuses without evicting old fences");
        check(bounded.watermarkCount() == SafeLandingProbeState.MAX_WATERMARKS, "bounded persistent replay state");

        var lines = SafeLandingProbeMesh.corners(BOUNDS);
        check(lines.size() == 24, "three short corner strokes at each of eight actual box corners");
        for (var line : lines) {
            check(contains(BOUNDS, line.from()) && contains(BOUNDS, line.to()), "corner endpoints match actual server collision box");
            check(line.from().distanceSquared(line.to()) > 0 && line.from().distanceSquared(line.to()) <= .18 * .18 + 1e-10, "short finite line only");
            int axes = (line.from().x() == line.to().x() ? 0 : 1) + (line.from().y() == line.to().y() ? 0 : 1)
                    + (line.from().z() == line.to().z() ? 0 : 1);
            check(axes == 1, "axis-aligned geometry");
        }
        Box departure = SafeLandingProbeMesh.sourceBounds(BOUNDS, success.source(), DESTINATION);
        check(Math.abs(departure.minX() - (-.05)) < 1e-9, "departure translated to actual precommit position");
        for (Box box : List.of(departure, BOUNDS)) {
            var quads = SafeLandingProbeMesh.biped(box); check(quads.size() == 36, "six-piece hologram atlas without oversized headwear");
            for (var q : quads) for (var v : List.of(q.a(), q.b(), q.c(), q.d())) {
                check(contains(box, v.position()), "bounded biped fits actual body box");
                check(v.u() >= 0 && v.u() <= 1 && v.v() >= 0 && v.v() <= 1, "original normalized atlas UVs");
            }
        }
        for (int age = 0; age < 7; age++) {
            check(SafeLandingProbeMesh.frame(age) == age, "restrained seven original frames in order");
            check(SafeLandingProbeMesh.particles(SOURCE, DESTINATION, age).size() == 32, "one pair has at most thirty-two particles");
            check(SafeLandingProbeMesh.alpha(age) > 0, "short decay remains visible until its terminal");
        }
        check(SafeLandingProbeMesh.alpha(7) == 0 && SafeLandingProbeMesh.particles(SOURCE, DESTINATION, 7).isEmpty(), "no permanent gate or residual particles");
        System.out.println("SafeLandingProbeVisualRegressionTest: " + checks + " checks passed");
    }
}
