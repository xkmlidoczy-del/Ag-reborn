package cn.academy.port.client;

import cn.academy.port.client.MagneticDomainFlowGeometry.FlowRibbon;
import cn.academy.port.client.MagneticDomainFlowGeometry.FlowSegment;
import cn.academy.port.client.MagneticDomainFlowGeometry.Layer;
import cn.academy.port.client.MagneticDomainFlowGeometry.Profile;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import javax.imageio.ImageIO;

/** Executes the production material helper; independent coordinates, clipping and emitted-width witnesses. */
public final class MagneticDomainMaterialFlowRegressionTest {
    private static final Point ZERO = new Point(0, 0, 0), LOOK = new Point(0, 0, -1), UP = new Point(0, 1, 0);
    private static int checks;
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
    private static void close(double expected, double actual, double tolerance, String reason) {
        check(Double.isFinite(expected) && Double.isFinite(actual) && Math.abs(expected - actual) <= tolerance,
                reason + ": expected " + expected + ", actual " + actual);
    }
    private static void close(Point expected, Point actual, double tolerance, String reason) {
        check(expected != null && actual != null && expected.finite() && actual.finite()
                && expected.minus(actual).length() <= tolerance, reason);
    }

    public static void main(String[] args) throws Exception {
        originalPaths(); profiles(); clock(); clipping(); finiteCameras(); translation(); generatedBudget(); texture(); invalid(); integration();
        System.out.println("PASS " + checks + " NEW152 original-path, tapered material, moving highlight, clipped-coordinate, pause and four-controller budget checks");
    }

    private static void originalPaths() {
        for (Point center : List.of(ZERO, new Point(19, 64, -28), new Point(1_000_000, 96, -1_000_000))) {
            for (int frame : new int[]{0, 1, 8, 117, 1199, 1200, -1, Integer.MAX_VALUE}) {
                var original = MagneticDomainGeometry.circulation(center, frame);
                var material = MagneticDomainFlowGeometry.segments(center, frame);
                check(original.size() == 24 && material.size() == original.size(), "six original four-segment paths retain their topology");
                for (int i = 0; i < original.size(); i++) {
                    FlowSegment flow = material.get(i);
                    close(original.get(i).start(), flow.segment().start(), 0, "existing integer-tick start endpoint is exact");
                    close(original.get(i).end(), flow.segment().end(), 0, "existing integer-tick end endpoint is exact");
                    check(flow.path() == i / 4, "each set of four belongs to one original path");
                    close((i % 4) / 4.0, flow.startT(), 0, "original start coordinate");
                    close((i % 4 + 1) / 4.0, flow.endT(), 0, "original end coordinate");
                }
            }
        }
        var fractional = MagneticDomainFlowGeometry.segments(ZERO, 12.5);
        var atTick = MagneticDomainFlowGeometry.segments(ZERO, 12);
        var nextTick = MagneticDomainFlowGeometry.segments(ZERO, 13);
        check(!fractional.equals(atTick) && !fractional.equals(nextTick), "render interpolation actually advances the original path motion");
        for (int i = 0; i < fractional.size(); i++) {
            double radius = i < 12 ? 3.2 : 3.8;
            for (Point point : List.of(fractional.get(i).segment().start(), fractional.get(i).segment().end())) {
                close(radius, Math.hypot(point.x(), point.z()), 1e-12, "interpolation retains the original circle rather than cutting a chord");
            }
        }
        var beforeWrap = MagneticDomainFlowGeometry.segments(ZERO, 1199.999999);
        var afterWrap = MagneticDomainFlowGeometry.segments(ZERO, .000001);
        for (int i = 0; i < beforeWrap.size(); i++) {
            close(beforeWrap.get(i).segment().start(), afterWrap.get(i).segment().start(), 3e-7, "circulation movement is continuous at the bounded clock wrap");
        }
    }

    private static void profiles() {
        double[] expectedTaper = {0, Math.sqrt(.5), 1, Math.sqrt(.5), 0};
        for (Layer layer : Layer.values()) for (int path = 0; path < 6; path++) {
            double maximum = layer == Layer.CORE ? .008 : .035;
            for (double frame : new double[]{0, 15.25, 119.999, 120, 1199.999, 1200}) {
                for (int node = 0; node <= 4; node++) {
                    Profile profile = MagneticDomainFlowGeometry.profile(path, node / 4.0, frame, layer);
                    close(maximum * expectedTaper[node], profile.halfWidth(), 1e-15, "a single whole-path taper spans all four quads");
                    check(profile.alpha() >= 0 && profile.alpha() <= 255, "material alpha is finite and bounded");
                    if (node == 0 || node == 4) check(profile.alpha() == 0 && profile.halfWidth() == 0, "only true path ends taper to zero");
                    else check(profile.alpha() > 70, "the stable interior remains visible between highlights");
                }
                for (int segment = 0; segment < 4; segment++) {
                    double a = segment / 4.0, b = (segment + 1) / 4.0;
                    Profile left = MagneticDomainFlowGeometry.profile(path, a, frame, layer);
                    Profile right = MagneticDomainFlowGeometry.profile(path, b, frame, layer);
                    for (int sample = 0; sample <= 16; sample++) {
                        double f = sample / 16.0;
                        Profile middle = MagneticDomainFlowGeometry.profile(path, a + (b - a) * f, frame, layer);
                        close(left.halfWidth() + (right.halfWidth() - left.halfWidth()) * f, middle.halfWidth(), 1e-15,
                                "profile matches the existing quad's linear width everywhere along the segment");
                        close(left.alpha() + (right.alpha() - left.alpha()) * f, middle.alpha(), .500001,
                                "profile matches endpoint alpha interpolation without new geometry");
                        check(middle.halfWidth() >= 0 && middle.halfWidth() <= maximum, "cosmetic half-width never exceeds the approved bound");
                    }
                }
            }
            for (int node = 1; node < 4; node++) {
                double t = node / 4.0;
                Profile fromLeft = MagneticDomainFlowGeometry.profile(path, t - 1e-10, 31.5, layer);
                Profile shared = MagneticDomainFlowGeometry.profile(path, t, 31.5, layer);
                Profile fromRight = MagneticDomainFlowGeometry.profile(path, t + 1e-10, 31.5, layer);
                close(fromLeft.halfWidth(), shared.halfWidth(), 1e-10, "taper is continuous entering a shared path node");
                close(fromRight.halfWidth(), shared.halfWidth(), 1e-10, "taper is continuous leaving a shared path node");
                check(fromLeft.alpha() == shared.alpha() && fromRight.alpha() == shared.alpha(), "highlight does not restart at segment boundaries");
            }
        }
        int earlyLeft = MagneticDomainFlowGeometry.profile(0, .25, 30, Layer.CORE).alpha();
        int earlyRight = MagneticDomainFlowGeometry.profile(0, .75, 30, Layer.CORE).alpha();
        int lateLeft = MagneticDomainFlowGeometry.profile(0, .25, 90, Layer.CORE).alpha();
        int lateRight = MagneticDomainFlowGeometry.profile(0, .75, 90, Layer.CORE).alpha();
        check(earlyLeft > earlyRight + 40 && lateRight > lateLeft + 40, "the broad highlight travels along the same path");
        for (int frame = 0; frame < 1200; frame++) for (double t : new double[]{.25, .5, .75}) {
            Profile now = MagneticDomainFlowGeometry.profile(0, t, frame, Layer.CORE);
            Profile next = MagneticDomainFlowGeometry.profile(0, t, frame + 1, Layer.CORE);
            check(Math.abs(next.alpha() - now.alpha()) <= 3, "highlight brightness varies slowly without frame-rate flicker");
        }
        for (Layer layer : Layer.values()) for (int path = 0; path < 6; path++) for (double t : new double[]{0, .125, .25, .5, .75, .9, 1}) {
            Profile start = MagneticDomainFlowGeometry.profile(path, t, 0, layer);
            check(start.equals(MagneticDomainFlowGeometry.profile(path, t, 120, layer)), "six-second highlight repeats without profile drift");
            check(start.equals(MagneticDomainFlowGeometry.profile(path, t, 1200, layer)), "material is continuous at the existing clock wrap");
        }
    }

    private static void clock() {
        var lease = new MagneticDomainState.Lease();
        lease.received(MagneticDomainState.ACTIVE);
        check(lease.tick(false, true), "existing lease advances normally");
        close(1.25, MagneticDomainFlowGeometry.time(lease.frame(), .25, false), 0, "active animation uses game partial ticks");
        double paused = MagneticDomainFlowGeometry.time(lease.frame(), .1, true);
        var pausedShape = MagneticDomainFlowGeometry.segments(ZERO, paused);
        for (double partial : new double[]{0, .1, .5, .999, 1, Double.NaN}) {
            check(lease.tick(true, true), "paused valid lease remains alive");
            close(paused, MagneticDomainFlowGeometry.time(lease.frame(), partial, true), 0, "pause ignores changing render partial ticks");
            check(pausedShape.equals(MagneticDomainFlowGeometry.segments(ZERO, MagneticDomainFlowGeometry.time(lease.frame(), partial, true))),
                    "pause freezes actual generated circulation");
        }
        check(lease.frame() == 1 && lease.quiet() == 1, "paused sampling does not advance animation or heartbeat age");
        check(!lease.tick(true, false), "owner invalidation still retires the existing lease while paused");
        close(0, MagneticDomainFlowGeometry.time(1199, 1, false), 0, "fractional clock wraps without accumulating history");
        close(5, MagneticDomainFlowGeometry.time(5, -2, false), 0, "negative partial ticks clamp to the existing tick");
        close(6, MagneticDomainFlowGeometry.time(5, 2, false), 0, "oversized partial ticks cannot jump multiple simulation ticks");
        check(Double.isNaN(MagneticDomainFlowGeometry.time(5, Double.NaN, false)), "invalid running clock refuses geometry");
    }

    private static void clipping() {
        for (Layer layer : Layer.values()) {
            FlowSegment nearCut = new FlowSegment(new Segment(new Point(-1, .5, .1), new Point(1, .5, -2)), 0, .25, .5);
            var near = MagneticDomainFlowGeometry.ribbons(nearCut, ZERO, LOOK, UP, 23.5, layer);
            check(near.size() == 1, "near crossing produces one visible material quad");
            check(near.getFirst().startT() > .25 && near.getFirst().startProfile().alpha() > 0,
                    "near cut retains an interior coordinate and does not invent a faded endpoint");
            verifyCoordinates(nearCut, near, ZERO, 23.5, layer);

            FlowSegment aimCut = new FlowSegment(new Segment(new Point(-1, 0, -4), new Point(1, 0, -4)), 1, .25, .5);
            var aim = MagneticDomainFlowGeometry.ribbons(aimCut, ZERO, LOOK, UP, 23.5, layer);
            check(aim.size() == 2, "aim exclusion splits one original quad into exactly two");
            check(aim.getFirst().endT() < aim.getLast().startT(), "aim gap is removed from original path coordinates");
            check(aim.getFirst().endProfile().alpha() > 0 && aim.getLast().startProfile().alpha() > 0,
                    "both aim-cut ends retain their uninterrupted highlight and taper");
            verifyCoordinates(aimCut, aim, ZERO, 23.5, layer);

            FlowSegment combined = new FlowSegment(new Segment(new Point(-.3, 0, .05), new Point(.3, 0, -4)), 2, .5, .75);
            var both = MagneticDomainFlowGeometry.ribbons(combined, ZERO, LOOK, UP, 44.75, layer);
            check(both.size() == 2, "near clipping and aim splitting compose within the two-quad bound");
            verifyCoordinates(combined, both, ZERO, 44.75, layer);
        }
        FlowSegment behind = new FlowSegment(new Segment(new Point(-1, 0, 3), new Point(1, 0, 3)), 0, .25, .5);
        check(MagneticDomainFlowGeometry.ribbons(behind, ZERO, LOOK, UP, 0, Layer.CORE).isEmpty(), "behind-eye material emits nothing");
    }

    private static void verifyCoordinates(FlowSegment original, List<FlowRibbon> ribbons, Point camera, double frame, Layer layer) {
        Point a = original.segment().start().minus(camera), delta = original.segment().end().minus(original.segment().start());
        for (FlowRibbon ribbon : ribbons) {
            Point[] endpoints = {ribbon.start(), ribbon.end()};
            Profile[] profiles = {ribbon.startProfile(), ribbon.endProfile()};
            double[] coordinates = {ribbon.startT(), ribbon.endT()};
            for (int i = 0; i < 2; i++) {
                double fraction = endpoints[i].minus(a).dot(delta) / delta.dot(delta);
                double expected = original.startT() + (original.endT() - original.startT()) * fraction;
                close(expected, coordinates[i], 1e-12, "clipped coordinate is recovered from the original world segment");
                close(a.plus(delta.scale(fraction)), endpoints[i], 1e-12, "material clipping never relocates the underlying path");
                check(profiles[i].equals(MagneticDomainFlowGeometry.profile(original.path(), expected, frame, layer)),
                        "clipped material profile uses original path time, not fragment-local time");
            }
            verifyRibbon(ribbon, LOOK, UP, layer);
        }
    }

    private static void finiteCameras() {
        FlowSegment collinear = new FlowSegment(new Segment(new Point(1, .5, -4), new Point(2, 1, -8)), 0, .25, .5);
        for (Point up : List.of(UP, LOOK, ZERO)) for (Layer layer : Layer.values()) {
            var ribbons = MagneticDomainFlowGeometry.ribbons(collinear, ZERO, LOOK, up, 0, layer);
            check(ribbons.size() == 1, "a camera-collinear path outside the aim gap has a stable fallback ribbon");
            verifyRibbon(ribbons.getFirst(), LOOK, up, layer);
            close(0, ribbons.getFirst().startSide().dot(collinear.segment().end().minus(collinear.segment().start())), 1e-12,
                    "fallback ribbon side remains perpendicular to the unchanged path");
        }
        for (Point look : List.of(LOOK, new Point(1, 0, 0), UP, new Point(.6, .8, 0))) {
            var basis = RailThunderArrayGeometry.basis(look);
            Point camera = new Point(0, .62, 0);
            for (double frame : new double[]{0, .25, 50, 333.75, 1199.9}) for (FlowSegment flow : MagneticDomainFlowGeometry.segments(ZERO, frame)) {
                for (Layer layer : Layer.values()) {
                    var ribbons = MagneticDomainFlowGeometry.ribbons(flow, camera, look, basis.up(), frame, layer);
                    check(ribbons.size() <= 2, "all view orientations retain a finite two-piece clipping bound");
                    for (FlowRibbon ribbon : ribbons) verifyRibbon(ribbon, look, basis.up(), layer);
                }
            }
        }
    }

    private static void verifyRibbon(FlowRibbon ribbon, Point look, Point cameraUp, Layer layer) {
        var basis = RailThunderArrayGeometry.basis(look);
        Point right = basis.forward().cross(cameraUp);
        right = right.length() < 1e-8 ? basis.side() : right.scale(1 / right.length());
        Point up = right.cross(basis.forward());
        double maximum = layer == Layer.CORE ? .008 : .035;
        check(ribbon.start().finite() && ribbon.end().finite() && ribbon.startSide().finite() && ribbon.endSide().finite(), "all emitted geometry is finite");
        check(ribbon.startT() >= 0 && ribbon.endT() <= 1 && ribbon.startT() < ribbon.endT(), "clipped path interval stays ordered and bounded");
        check(ribbon.startSide().length() <= maximum + 1e-12 && ribbon.endSide().length() <= maximum + 1e-12, "actual emitted half-widths stay within both approved maxima");
        check(ribbon.startSide().length() <= ribbon.start().dot(basis.forward()) * .012 + 1e-12
                && ribbon.endSide().length() <= ribbon.end().dot(basis.forward()) * .012 + 1e-12, "existing near-eye angular width cap remains effective");
        for (Point point : List.of(ribbon.start().plus(ribbon.startSide()), ribbon.start().minus(ribbon.startSide()),
                ribbon.end().minus(ribbon.endSide()), ribbon.end().plus(ribbon.endSide()))) {
            double depth = point.dot(basis.forward());
            check(point.finite() && depth >= .20 - 1e-10, "every expanded material corner stays ahead of the near plane");
            double x = point.dot(right) / depth, y = point.dot(up) / depth;
            check(Math.abs(x) >= .022 - 1e-9 || Math.abs(y) >= .022 - 1e-9, "both material layers leave the aiming region clear");
        }
    }

    private static void translation() {
        Point camera = new Point(0, .62, 4), shift = new Point(25_000_000, 100, -25_000_000);
        double frame = 42.375;
        var original = MagneticDomainFlowGeometry.segments(ZERO, frame);
        var translated = MagneticDomainFlowGeometry.segments(shift, frame);
        for (int i = 0; i < original.size(); i++) for (Layer layer : Layer.values()) {
            var near = MagneticDomainFlowGeometry.ribbons(original.get(i), camera, LOOK, UP, frame, layer);
            var far = MagneticDomainFlowGeometry.ribbons(translated.get(i), camera.plus(shift), LOOK, UP, frame, layer);
            check(near.size() == far.size(), "large world translation preserves clipped topology");
            for (int j = 0; j < near.size(); j++) {
                close(near.get(j).start(), far.get(j).start(), 2e-8, "double camera subtraction retains sub-block positions near world limits");
                close(near.get(j).end(), far.get(j).end(), 2e-8, "translated endpoints preserve double-relative precision");
                close(near.get(j).startSide(), far.get(j).startSide(), 2e-8, "large translation preserves fine material width");
                close(near.get(j).endSide(), far.get(j).endSide(), 2e-8, "large translation preserves tapered material width");
            }
        }
    }

    private static void generatedBudget() {
        check(MagneticDomainFlowGeometry.PRECLIP_QUADS == 48 && MagneticDomainFlowGeometry.MAX_PARTICLES == 0,
                "24 existing segments spend exactly two material layers and no particles");
        check(MagneticDomainFlowGeometry.MAX_QUADS_PER_CONTROLLER == 128 && MagneticDomainFlowGeometry.MAX_VISIBLE_CONTROLLERS == 4
                && MagneticDomainFlowGeometry.MAX_VERTICES == 2048, "controller and global bounds retain their approved values");
        Point camera = new Point(0, .62, 20);
        for (double frame : new double[]{0, 15.5, 65, 1199.75}) {
            int totalQuads = 0;
            for (int controller = 0; controller < 4; controller++) {
                Point center = new Point(controller - 1.5, 0, 0);
                var budget = new MagneticDomainGeometry.Budget();
                int emitted = 0;
                for (FlowSegment flow : MagneticDomainFlowGeometry.segments(center, frame)) for (Layer layer : Layer.values()) {
                    for (FlowRibbon ribbon : MagneticDomainFlowGeometry.ribbons(flow, camera, LOOK, UP, frame, layer)) {
                        verifyRibbon(ribbon, LOOK, UP, layer);
                        if (budget.take()) emitted++;
                    }
                }
                List<Segment> other = new ArrayList<>();
                other.addAll(MagneticDomainGeometry.boundary(center, LOOK, (int) frame));
                other.addAll(MagneticDomainGeometry.frontBoundary(center, LOOK, (int) frame));
                other.addAll(MagneticDomainGeometry.impact(center, LOOK, 0));
                other.addAll(MagneticDomainGeometry.refraction(center, LOOK, 0));
                other.addAll(MagneticDomainGeometry.slowFall(center, (int) frame));
                for (Segment line : other) {
                    var near = RailThunderArrayGeometry.nearClip(line.start().minus(camera), line.end().minus(camera), LOOK, MagneticDomainGeometry.WIDTH);
                    for (Segment part : RailThunderArrayGeometry.clearCenter(near, LOOK, UP, MagneticDomainGeometry.WIDTH)) {
                        if (RailThunderArrayGeometry.ribbon(part, LOOK, MagneticDomainGeometry.WIDTH) != null && budget.take()) emitted++;
                    }
                }
                check(emitted > 48 && emitted == budget.used() && emitted <= 128, "actual circulation and existing domain geometry share one post-clip budget");
                totalQuads += emitted;
            }
            check(totalQuads > 0 && totalQuads <= 512 && totalQuads * 4 <= 2048, "four fully populated controllers retain the global emitted-vertex ceiling");
        }
        // Deliberately overload the submission stream with real production-generated quads.
        // This verifies the debit at emission, not merely that the nominal shape fits the budget.
        var demand = new ArrayList<FlowRibbon>();
        for (int frame : new int[]{0, 20, 40, 60}) for (FlowSegment flow : MagneticDomainFlowGeometry.segments(ZERO, frame)) {
            for (Layer layer : Layer.values()) demand.addAll(MagneticDomainFlowGeometry.ribbons(flow, camera, LOOK, UP, frame, layer));
        }
        check(demand.size() > 128, "overload witness has more actual generated quads than one controller can emit");
        int vertices = 0;
        for (int controller = 0; controller < 4; controller++) {
            var budget = new MagneticDomainGeometry.Budget();
            for (FlowRibbon ribbon : demand) if (budget.take()) {
                check(ribbon.start().finite() && ribbon.end().finite(), "overload fixture debits finite generated geometry");
                vertices += 4;
            }
            check(budget.used() == 128, "real over-budget submissions stop at exactly 128 quads per controller");
        }
        check(vertices == 2048, "four overloaded production streams saturate but never exceed 2,048 emitted vertices");
    }

    private static void texture() throws Exception {
        Path texture = Path.of("src/main/resources/assets/academy/textures/effects/glow_line.png");
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(texture)));
        check(hash.equals("0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361"),
                "material uses the exact original glow_line bytes, without an asset replacement");
        BufferedImage image = ImageIO.read(texture.toFile());
        check(image != null && image.getWidth() == 32 && image.getHeight() == 32, "material reuses the original glow_line asset");
        for (int x = 0; x < image.getWidth(); x++) {
            check((image.getRGB(x, 0) >>> 24) == 0 && (image.getRGB(x, 31) >>> 24) == 0, "original full texture has transparent soft outer edges");
            check((image.getRGB(x, 15) >>> 24) == 255, "original center supports the readable blue-white core");
        }
        for (int x = 0; x < 32; x++) {
            for (int y = 14; y < 18; y++) check((image.getRGB(x, y) >>> 24) >= 200,
                    "cropped core uses the proven luminous original rows rather than transparent padding");
            check((image.getRGB(x, 8) >>> 24) < 80 && (image.getRGB(x, 23) >>> 24) < 80,
                    "shoulder crop still reaches soft low-alpha material edges");
        }
        for (Layer layer : Layer.values()) for (double t : new double[]{.25, .5, .75}) {
            int peak = layer == Layer.CORE ? MagneticDomainGeometry.CORE_ALPHA : MagneticDomainGeometry.FLOW_ALPHA;
            Profile p = MagneticDomainFlowGeometry.profile(0, t, 0, layer);
            check(p.alpha() >= Math.round(peak * .68) && p.alpha() <= peak,
                    "interior alpha retains bounded lobe without a second width-taper multiplication");
        }
        Profile shoulder = MagneticDomainFlowGeometry.profile(0, .5, 0, Layer.SHOULDER);
        Profile core = MagneticDomainFlowGeometry.profile(0, .5, 0, Layer.CORE);
        check(shoulder.minV() == 8f / 32 && shoulder.maxV() == 24f / 32, "soft envelope retains original soft shoulders at bounded spatial width");
        check(core.minV() == 14f / 32 && core.maxV() == 18f / 32, "core crops the original strong center without a new texture");
    }

    private static void invalid() {
        Point nan = new Point(Double.NaN, 0, 0);
        for (Point center : new Point[]{null, nan, new Point(30_000_001, 0, 0)}) {
            check(MagneticDomainFlowGeometry.segments(center, 0).isEmpty(), "invalid world centers refuse circulation geometry");
        }
        for (double time : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            check(MagneticDomainFlowGeometry.segments(ZERO, time).isEmpty(), "nonfinite animation time cannot enter path trigonometry");
            check(MagneticDomainFlowGeometry.profile(0, .5, time, Layer.CORE) == null, "nonfinite material time refuses a profile");
        }
        for (double t : new double[]{-.01, 1.01, Double.NaN, Double.POSITIVE_INFINITY}) {
            check(MagneticDomainFlowGeometry.profile(0, t, 0, Layer.CORE) == null, "invalid original path coordinates are rejected");
        }
        check(MagneticDomainFlowGeometry.profile(-1, .5, 0, Layer.CORE) == null
                && MagneticDomainFlowGeometry.profile(6, .5, 0, Layer.CORE) == null
                && MagneticDomainFlowGeometry.profile(0, .5, 0, null) == null, "invalid path or material selection is rejected");
        FlowSegment valid = new FlowSegment(new Segment(new Point(-1, .5, -4), new Point(1, .5, -4)), 0, .25, .5);
        for (FlowSegment flow : new FlowSegment[]{null, new FlowSegment(null, 0, .25, .5),
                new FlowSegment(new Segment(ZERO, ZERO), 0, .25, .5),
                new FlowSegment(new Segment(nan, ZERO), 0, .25, .5),
                new FlowSegment(valid.segment(), 6, .25, .5), new FlowSegment(valid.segment(), 0, .5, .25),
                new FlowSegment(valid.segment(), 0, 0, 1), new FlowSegment(valid.segment(), 0, Double.NaN, .5)}) {
            check(MagneticDomainFlowGeometry.ribbons(flow, ZERO, LOOK, UP, 0, Layer.CORE).isEmpty(), "malformed or degenerate segment produces no quad");
        }
        check(MagneticDomainFlowGeometry.ribbons(valid, null, LOOK, UP, 0, Layer.CORE).isEmpty()
                && MagneticDomainFlowGeometry.ribbons(valid, nan, LOOK, UP, 0, Layer.CORE).isEmpty()
                && MagneticDomainFlowGeometry.ribbons(valid, ZERO, ZERO, UP, 0, Layer.CORE).isEmpty()
                && MagneticDomainFlowGeometry.ribbons(valid, ZERO, nan, UP, 0, Layer.CORE).isEmpty()
                && MagneticDomainFlowGeometry.ribbons(valid, ZERO, LOOK, null, 0, Layer.CORE).isEmpty()
                && MagneticDomainFlowGeometry.ribbons(valid, ZERO, LOOK, nan, 0, Layer.CORE).isEmpty()
                && MagneticDomainFlowGeometry.ribbons(valid, ZERO, LOOK, UP, Double.NaN, Layer.CORE).isEmpty()
                && MagneticDomainFlowGeometry.ribbons(valid, ZERO, LOOK, UP, 0, null).isEmpty(), "invalid camera, time and material inputs fail closed");
    }

    private static String source(String path) throws Exception {
        return Files.readString(Path.of("src/main/java/cn/academy/port/" + path)).replaceAll("\\s+", "");
    }

    /** Small wiring witnesses complement the executed geometry; lifecycle/server tests remain independent. */
    private static void integration() throws Exception {
        String effects = source("client/MagneticDomainEffects.java");
        int flowMethod = effects.indexOf("privatestaticvoidflow(");
        int vertexMethod = effects.indexOf("privatestaticvoidvertex(", flowMethod);
        check(flowMethod >= 0 && vertexMethod > flowMethod, "actual renderer has a distinct material-flow adapter");
        String flow = effects.substring(flowMethod, vertexMethod);
        int visibility = flow.indexOf("if(!visible(owner,from,to)||!visible(owner,center,from)||!visible(owner,center,to))return;");
        int clipping = flow.indexOf("MagneticDomainFlowGeometry.ribbons(");
        int debit = flow.indexOf("if(!take(budget))continue;", clipping);
        int emission = flow.indexOf("vertex(out,matrix,", debit);
        check(visibility >= 0 && clipping > visibility && debit > clipping && emission > debit,
                "all three original LOS checks precede clipping, and the shared debit precedes actual flow emission");
        check(flow.contains("path.segment().start()") && flow.contains("path.segment().end()")
                && flow.contains("ribbon.startSide()") && flow.contains("ribbon.endSide()")
                && flow.contains("ribbon.startT()") && flow.contains("ribbon.endT()")
                && flow.contains("pa.alpha()") && flow.contains("pb.alpha()"),
                "renderer consumes original path endpoints, asymmetric taper sides, UV coordinates and endpoint alpha");
        check(effects.contains("MAX_VISIBLE_CONTEXTS=4;") && effects.contains(".limit(MAX_VISIBLE_CONTEXTS)")
                && effects.contains("MAX_FRAME_QUADS=MAX_VISIBLE_CONTEXTS*MagneticDomainGeometry.MAX_QUADS;")
                && effects.contains("frameQuads=0;")
                && effects.contains("if(frameQuads>=MAX_FRAME_QUADS||!budget.take())returnfalse;")
                && effects.contains("frameQuads++;returntrue;"),
                "actual renderer enforces four contexts and one shared 512-quad frame ceiling");
        int oldLine = effects.indexOf("privatestaticvoidline(");
        int takeMethod = effects.indexOf("privatestaticbooleantake(", oldLine);
        check(oldLine >= 0 && takeMethod > oldLine
                && effects.substring(oldLine, takeMethod).contains("ribbon==null||!take(budget)"),
                "original domain boundaries and receipts debit the same global ceiling as material flow");
        String white = "out=buffers.getBuffer(ClassicRenderTypes.world(WHITE));";
        int markerLoop = effects.indexOf("for(Markermarker:visible){");
        int reacquireAtMarker = effects.indexOf(white, markerLoop);
        int receipts = effects.indexOf("MagneticDomainGeometry.impact(", markerLoop);
        int glow = effects.indexOf("buffers.getBuffer(ClassicRenderTypes.world(MagneticDomainVisualMaterial.GLOW))", markerLoop);
        int reacquireAfterGlow = effects.indexOf(white, glow);
        int boundary = effects.indexOf("MagneticDomainGeometry.frontBoundary(", glow);
        check(markerLoop >= 0 && reacquireAtMarker > markerLoop && receipts > reacquireAtMarker
                && glow > receipts && reacquireAfterGlow > glow && boundary > reacquireAfterGlow,
                "WHITE is reacquired for every controller and after GLOW before subsequent boundaries");
        check(effects.contains("MagneticDomainFlowGeometry.time(marker.lease.frame(),partial,mc.isPaused())"),
                "the actual new render branch uses pause-aware tick interpolation");
        int enabled = effects.indexOf("if(MagneticDomainVisualMaterial.enabled()){");
        int fallback = effects.indexOf("}else{", enabled);
        check(enabled >= 0 && fallback > enabled && boundary > fallback,
                "material failure or OFF has an explicit original-flow fallback");
        String off = effects.substring(fallback, boundary);
        check(off.contains("MagneticDomainGeometry.circulation(point(center),marker.lease.frame())")
                && off.contains("MagneticDomainGeometry.FLOW_WIDTH") && off.contains("MagneticDomainGeometry.FLOW_CORE_WIDTH")
                && off.contains("MagneticDomainGeometry.FLOW_ALPHA") && off.contains("MagneticDomainGeometry.CORE_ALPHA"),
                "OFF retains original circulation clock, widths and material alpha");
        check(!effects.contains("addParticle(") && !effects.contains("setDeltaMovement(") && !effects.contains("setHealth("),
                "cosmetic adapter emits no particles or gameplay mutations");

        String material = source("client/MagneticDomainVisualMaterial.java");
        check(material.contains("textures/effects/glow_line.png")
                && material.contains("0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361")
                && material.contains("readNBytes(65537)") && material.contains("data.length<=65536")
                && material.contains("MessageDigest.getInstance(\"SHA-256\")"),
                "loaded material accepts only the exact original asset under the bounded resource-read cap");
        int reset = material.indexOf("ready=false;generation++;");
        int load = material.indexOf("resources.getResourceOrThrow(GLOW).open()");
        check(material.contains("ResourceManagerReloadListener") && reset >= 0 && load > reset
                && material.contains("catch(Exceptionunavailable){ready=false;}")
                && material.contains("returnready&&HighPowerVisualConfig.SPEC.isLoaded()&&HighPowerVisualConfig.MAGNETIC_DOMAIN_FLOW.get();"),
                "reload clears readiness before loading and any failure immediately falls back to original rendering");

        String config = source("HighPowerVisualConfig.java");
        String registration = source("AcademyCraft.java");
        String classicHud = source("ClassicHudConfig.java");
        check(config.contains(".define(\"magneticDomainFlow\",false)")
                && registration.contains("registerConfig(ModConfig.Type.CLIENT,HighPowerVisualConfig.SPEC,\"academy-highpower-visual-client.toml\")")
                && !material.contains("ClassicHudConfig") && !classicHud.contains("magneticDomainFlow") && !classicHud.contains("MAGNETIC_DOMAIN_FLOW"),
                "optional default-OFF setting has its own client spec, without extending the pinned classic HUD config");
    }
}
