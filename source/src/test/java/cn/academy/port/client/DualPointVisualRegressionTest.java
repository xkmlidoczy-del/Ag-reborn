package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.DualPointRules;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

/** Actual native-origin/projection helpers and authenticated input/receipt lifecycle, without a game window. */
public final class DualPointVisualRegressionTest {
    private static int checks;
    private static void check(boolean ok, String label) { checks++; if (!ok) throw new AssertionError(label); }
    private static DualPointGeometry.Point p(double x, double y, double z) { return new DualPointGeometry.Point(x, y, z); }
    private static final UUID OWNER = new UUID(1, 2);
    private static DualPointState.Frame f(long input, long token, long sequence, int phase, int slot, int status, int remaining) {
        boolean blank = phase == DualPointState.ENDED || phase == DualPointState.CLEAR_MARK;
        return new DualPointState.Frame(new DualPointState.Identity(4, OWNER, 3, input, token), sequence, phase, slot,
                blank ? p(0, 0, 0) : p(.32, -.28, .35), blank ? p(0, 0, 0) : p(0, 0, 20), status, remaining);
    }
    public static void main(String[] args) throws Exception {
        var input = new DualPointInputState();
        var mark = new HighPowerInput.Input(1, 1, 4, 3); input.tap(mark); input.release(mark);
        check(input.permits(1, 3), "ordinary release preserves pending mark receipt");
        input.accepted(1, 3);
        var shot = new HighPowerInput.Input(1, 2, 4, 3); input.tap(shot);
        check(input.permits(1, 3) && input.permits(2, 3), "new tap preserves authorized mark until receipt");
        input.accepted(2, 3);
        check(!input.permits(1, 3) && input.permits(2, 3), "new accepted identity retires the old input");
        check(input.cancel(mark) == null, "old delegate cannot cancel a newer input");
        check(input.cancelWhenBlocked(false) == null && input.cancelWhenBlocked(true).equals(shot), "GUI or lost focus owns persistent cancellation");
        check(!input.permits(2, 3), "cancel rejects queued receipt");
        for (int n = 1; n <= 100; n++) input.tap(new HighPowerInput.Input(1, n, 4, 4));
        check(input.pendingCount() == 16 && !input.permits(1, 4), "bounded pending inputs");
        input.accepted(100, 4); input.ended(100, 4);
        check(!input.permits(100, 4), "normal end retires accepted permission");
        input.tap(new HighPowerInput.Input(0, 1, 5, 5));
        check(!input.permits(100, 4), "actor and epoch switch drops previous receipts");
        input.reset(); check(input.current() == null && input.pendingCount() == 0, "session reset has no outstanding input");

        var state = new DualPointState();
        check(state.accept(f(1, 5, 1, 0, 1, 0, 100), true), "server mark admitted");
        check(!state.accept(f(1, 5, 1, 0, 1, 0, 100), true), "duplicate cannot renew marker");
        check(state.accept(f(2, 5, 2, 1, 0, 4, 0), true), "primary damage receipt accepted");
        check(state.accept(f(2, 5, 3, 1, 1, 3, 0), true), "staggered second refused receipt accepted without fake hit");
        check(!state.accept(f(2, 5, 4, 1, 1, 4, 0), true), "same input and slot cannot fire twice");
        check(state.accept(f(2, 5, 4, 0, 1, 0, 95), true), "mark heartbeat after emission is allowed");
        check(!state.accept(f(1, 5, 5, 0, 1, 0, 100), true), "late old mark cannot rewind accepted input");
        check(state.accept(f(2, 5, 5, 3, 1, 0, 0), true), "occlusion clears marker");
        check(state.accept(f(2, 5, 6, 2, 0, 0, 0), true) && state.size() == 0, "end retires context");
        check(!state.accept(f(2, 5, 7, 0, 1, 0, 90), true), "retired token cannot resurrect");
        check(!state.accept(f(2, 6, 1, 0, 1, 0, 100), true), "new token requires newer input");
        check(state.accept(f(3, 6, 1, 1, 1, 1, 0), true), "a valid secondary-only shot is allowed when primary admission failed");
        state.cancel(3, 4);
        check(!state.accept(f(4, 7, 1, 0, 1, 0, 100), true), "cancel-before-ack fences a newer pending cast");
        state.clear(); check(state.size() == 0 && state.watermarkCount() > 0, "cleanup keeps replay watermark");
        state.resetSession(); check(state.watermarkCount() == 0, "new connection clears token namespace");
        for (int phase = 0; phase <= 3; phase++) check(!DualPointState.valid(f(1, 1, 1, phase, 2, 0, 0)), "third slot is never allowed");
        check(!DualPointState.valid(f(1, 1, 1, 0, 1, 4, 100)), "prepared endpoint is never damage confirmation");
        check(!DualPointState.valid(f(1, 1, 1, 1, 0, 0, 0)), "fire cannot reuse preparation status");
        check(!DualPointState.valid(f(1, 1, 1, 0, 1, 0, DualPointState.MARK_TICKS + 1)), "mark cannot exceed the configured duration");
        var invalid = f(1, 1, 1, 1, 0, 1, 0);
        check(!DualPointState.valid(new DualPointState.Frame(invalid.id(), 1, 1, 0, p(0, 0, 0), p(0, 0, DualPointGeometry.MAX_RANGE + .001), 1, 0)), "out-of-range receipt rejected");
        check(!DualPointState.valid(new DualPointState.Frame(invalid.id(), 1, 1, 0, p(Double.NaN, 0, 0), p(0, 0, 1), 1, 0)), "nonfinite receipt rejected");
        var lease = new DualPointState.MarkLease(); lease.received(f(1, 1, 1, 0, 1, 0, 100));
        for (int n = 0; n < 5; n++) lease.tick(false, true);
        lease.received(f(1, 1, 2, 0, 1, 0, 100));
        check(lease.remaining() == 95, "heartbeat never refreshes original mark lifetime");
        lease.tick(true, true); check(lease.remaining() == 95, "integrated pause freezes display lease");
        for (int n = 0; n < 12; n++) lease.tick(false, true);
        check(!lease.visible(), "lost heartbeat hides stale marker");
        lease.received(f(2, 1, 3, 0, 1, 0, 100)); check(lease.visible(), "new server mark may replace prior mark");
        lease.tick(true, false); check(!lease.visible(), "GUI/player/world invalidation clears even while paused");
        lease.received(f(3, 1, 4, 0, 1, 0, 1)); lease.tick(false, true); check(!lease.visible(), "last permitted mark tick expires");

        var look = p(0, 0, 1); var up = p(0, 1, 0);
        for (int hand = 0; hand < 2; hand++) {
            var real = DualPointRules.origin(new DualPointRules.Point(0, 0, 0), new DualPointRules.Point(0, 0, 1), hand);
            var origin = p(real.x(), real.y(), real.z()); var end = p(0, 0, 24);
            check(Math.abs(origin.x()) == .32 && Math.signum(origin.x()) == (hand == 0 ? -1 : 1), "two genuine server hand origins remain distinct");
            double area = 0;
            for (var segment : DualPointGeometry.clearCenter(DualPointGeometry.nearClip(origin, end, look, DualPointGeometry.CORE_WIDTH), look, up, DualPointGeometry.CORE_WIDTH)) {
                var ribbon = DualPointGeometry.ribbon(segment, look, DualPointGeometry.CORE_WIDTH);
                area += DualPointGeometry.projectedArea(ribbon, look, up);
                for (var vertex : List.of(ribbon.start().plus(ribbon.side()), ribbon.start().minus(ribbon.side()), ribbon.end().plus(ribbon.side()), ribbon.end().minus(ribbon.side()))) {
                    check(vertex.z() >= DualPointGeometry.NEAR_DEPTH, "whole native-hand ribbon stays beyond near plane");
                    check(Math.max(Math.abs(vertex.x()), Math.abs(vertex.y())) / vertex.z() >= DualPointGeometry.CENTER_CLEAR - 1e-7, "ribbon edge leaves aiming center clear");
                }
            }
            double pixelsPerUnit = 1080 / (2 * Math.tan(Math.toRadians(70) / 2));
            check(area * pixelsPerUnit * pixelsPerUnit > 100, "native 70-degree FOV core has visible projected pixel area");
            var parts = DualPointGeometry.beam(origin, end);
            check(parts.size() == 3 && parts.getFirst().segment().start().equals(origin) && parts.getLast().segment().end().equals(end), "small-ray segments preserve exact accepted endpoints");
        }
        for (var aim : List.of(p(1, 0, 0), p(.6, .8, 0), p(0, 1, 0), p(0, -1, 0))) for (int hand = 0; hand < 2; hand++) {
            var real = DualPointRules.origin(new DualPointRules.Point(0, 0, 0), new DualPointRules.Point(aim.x(), aim.y(), aim.z()), hand);
            var origin = p(real.x(), real.y(), real.z()); var cameraUp = DualPointGeometry.basis(aim).up();
            double area = 0;
            for (var segment : DualPointGeometry.clearCenter(DualPointGeometry.nearClip(origin, aim.scale(24), aim, .009), aim, cameraUp, .009))
                area += DualPointGeometry.projectedArea(DualPointGeometry.ribbon(segment, aim, .009), aim, cameraUp);
            check(area > 1e-5, "both native hand ribbons keep nonzero area through yaw/pitch and vertical singularities");
        }
        check(DualPointGeometry.nearClip(p(0, 0, -2), p(0, 0, -.2), look, .01) == null, "behind-camera path cannot draw");
        check(DualPointGeometry.nearClip(p(0, 0, 0), p(0, 0, DualPointGeometry.MAX_RANGE + 1), look, .01) == null, "geometry cannot manufacture a longer beam");
        check(DualPointGeometry.impact(p(0, 0, 10), look, 0, false).isEmpty(), "miss/refusal/preparation never produces impact sparks");
        check(DualPointGeometry.impact(p(0, 0, 10), look, 0, true).size() == 4, "confirmed damage has exactly four small local sparks");
        preview();
        var budget = new DualPointGeometry.Budget(); for (int n = 0; n < 100; n++) budget.take();
        check(budget.used() == 32 && !budget.take(), "total frame geometry has hard 32-unit ceiling");
        check(contrast(DualPointGeometry.EDGE, 0xDCE8CF) > 4, "saturated dark green edge remains distinct against daylight pale terrain");
        check(contrast(DualPointGeometry.CORE, DualPointGeometry.EDGE) > 4, "green-white core separates from dark green edge");
        String effects = Files.readString(Path.of("src/main/java/cn/academy/port/client/DualPointEffects.java"));
        check(effects.contains("effects/mdray_small/") && !effects.contains("effects/mdray/") && !effects.contains("mdball") && !effects.contains("mdshield"), "only faithful narrow Meltdowner assets are used");
        check(!effects.contains("firstPersonOrigin") && !effects.contains("getEntities") && !effects.contains("setHealth"), "cosmetic code cannot shift native origin, reacquire target or mutate damage");
        check(effects.contains("shot.origin().distanceToSqr(shot.end()) < 1e-12") && !effects.contains("shot.status() == DualPointState.REFUSED ||"), "native-refused damage still shows its real emitted segment without invented impact");
        check(effects.contains("strictMarkPath(marker.owner, camera, vec(endpoint))") && effects.contains("strictMarkPath(marker.owner, nativeOrigin, vec(endpoint))"), "mark requires strict camera and true-hand block visibility");
        check(effects.contains("RenderGuiEvent.Post") && effects.contains("\"PREPARE\"") && effects.contains("previews = List.of(); // A projection is consumed once"), "preparation overlay is explicit and each projected frame is single-use");
        check(effects.contains("marker.state != preview.receipt") && effects.contains("!preview.endpoint.equals(marker.mark.end())")
                && effects.contains("preview.width != graphics.guiWidth()") && effects.contains("mc.options.hideGui"), "overlay retains receipt, expiry, GUI-scale and visibility fences");
        check(effects.contains("new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix()).mul(matrix)"), "overlay uses the actual world frame projection and camera transform");
        String client = Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        for (String required : List.of("hp_dual_point_mark", "hp_dual_point_tap", "hp_dual_point_abort", "dualPointInputs.cancelWhenBlocked", "dualPointInputs.reset();DualPointEffects.clear()"))
            check(client.contains(required), "authenticated client lifecycle seam " + required);
        System.out.println("PASS " + checks + " NEW211 native hand geometry, 70-degree projection, contrast, input and receipt checks");
    }
    private static void preview() {
        var look = p(0, 0, 1); var up = p(0, 1, 0); var oldEndpoint = p(0, 0, 8);
        int retained = 0;
        // Exact pre-repair geometry: ±10 cm corners with 4 cm strokes, passed through beam center clipping.
        for (int sx : new int[]{-1, 1}) for (int sy : new int[]{-1, 1}) {
            var corner = oldEndpoint.plus(p(sx * .10, sy * .10, 0));
            var end = corner.plus(p(-sx * .04, 0, 0));
            retained += DualPointGeometry.clearCenter(DualPointGeometry.nearClip(corner, end, look, .002), look, up, .002).size();
        }
        check(retained == 0, "pre-repair centered 8m world marker is completely removed by beam center clipping");
        for (double fov : new double[]{50, 70, 110}) for (int scale : new int[]{1, 2, 3, 4, 6}) for (double distance : new double[]{1, 8, 20, DualPointGeometry.MAX_RANGE}) {
            int width = 1920 / scale, height = 1080 / scale;
            double near = .05, far = 256, clipZ = (far + near) / (far - near) * distance - 2 * far * near / (far - near);
            var center = DualPointGeometry.projectMark(0, 0, clipZ, distance, distance * distance, width, height, true);
            check(center != null && center.x() == width / 2.0 && center.y() == height / 2.0, "visible authoritative endpoint projects to center across range, FOV and GUI scale");
            var strokes = DualPointGeometry.screenMark(center);
            check(strokes.size() == 8, "four hollow corners survive projection without beam aiming-cone clipping");
            for (var stroke : strokes) {
                double dx = stroke.x1() - Math.round(center.x()), dy = stroke.y1() - Math.round(center.y());
                check(Math.abs(dx) >= 10 || Math.abs(dy) >= 10, "even one-pixel dark outline leaves central crosshair area empty");
                check(stroke.x2() - stroke.x1() <= 5 && stroke.y2() - stroke.y1() <= 5, "stroke footprint stays fixed in GUI pixels");
            }
            double focal = 1 / Math.tan(Math.toRadians(fov) / 2), aspect = width / (double)height;
            var offset = DualPointGeometry.projectMark(.5 * focal / aspect, 0, clipZ, distance, distance * distance, width, height, true);
            if (distance >= 8) check(offset != null && offset.x() > center.x(), "actual FOV controls off-center projection without relocating the endpoint");
        }
        check(DualPointGeometry.projectMark(0, 0, 7.9, 8, 64, 640, 360, false) == null, "obstructed mark is never projected as a through-wall overlay");
        check(DualPointGeometry.projectMark(0, 0, 7.9, 8, Math.pow(DualPointGeometry.MAX_RANGE, 2) + .0001, 640, 360, true) == null, "out-of-range current native hand refuses preview");
        check(DualPointGeometry.projectMark(0, 0, -8, -8, 64, 640, 360, true) == null, "behind-camera marker refused");
        check(DualPointGeometry.projectMark(0, 0, -.11, .1, 64, 640, 360, true) == null, "near-plane marker refused");
        check(DualPointGeometry.projectMark(0, 0, 9, 8, 64, 640, 360, true) == null, "far-plane marker refused");
        check(DualPointGeometry.projectMark(9, 0, 7.9, 8, 64, 640, 360, true) == null, "outside-FOV point is not clamped into view");
        check(DualPointGeometry.projectMark(7.9, 0, 7.9, 8, 64, 640, 360, true) == null, "partially offscreen preparatory marker refused");
        check(DualPointGeometry.projectMark(Double.NaN, 0, 7.9, 8, 64, 640, 360, true) == null, "nonfinite projection refused");
        var budget = new DualPointGeometry.Budget();
        check(budget.reserve(30) && !budget.reserve(3) && budget.used() == 30, "outlined corners and seven-letter preparation label respect existing 32-quad ceiling");
    }
    private static double luminance(int color) {
        double result = 0; int[] shifts = {16, 8, 0}; double[] weights = {.2126, .7152, .0722};
        for (int i = 0; i < 3; i++) { double value = ((color >>> shifts[i]) & 255) / 255.0; result += weights[i] * (value <= .04045 ? value / 12.92 : Math.pow((value + .055) / 1.055, 2.4)); }
        return result;
    }
    private static double contrast(int a, int b) { double x = luminance(a), y = luminance(b); return (Math.max(x, y) + .05) / (Math.min(x, y) + .05); }
}
