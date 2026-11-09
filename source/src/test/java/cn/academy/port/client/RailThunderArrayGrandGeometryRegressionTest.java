package cn.academy.port.client;

import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Quad;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Standalone witnesses for the optional helper used by the renderer; no engine/bootstrap needed. */
public final class RailThunderArrayGrandGeometryRegressionTest {
    private static final Point ZERO = p(0, 0, 0), X = p(1, 0, 0), Y = p(0, 1, 0), Z = p(0, 0, 1);
    private static int checks;

    public static void main(String[] args) {
        constantsAndPrimaryPaths();
        fiveTickClockAndCallerHit();
        boundedPreparation();
        cameraClippingAndUv();
        worldBorderTranslation();
        variedCameraGeometry();
        invalidInputs();
        controllerBudgetsAndImmutability();
        System.out.println("PASS " + checks + " optional NEW151 grand geometry checks; appearance needs native review");
    }

    private static void constantsAndPrimaryPaths() {
        check(RailThunderArrayGeometry.MAX_HALF_WIDTH == .055, "classic shared width guard remains .055");
        check(RailThunderArrayGrandGeometry.CORE_HALF_WIDTH == .08
                && RailThunderArrayGrandGeometry.MID_HALF_WIDTH == .20
                && RailThunderArrayGrandGeometry.SOFT_HALF_WIDTH == .45, "optional widths are explicit");
        check(RailThunderArrayGrandGeometry.MAX_PARTICLES == 0, "grand geometry adds zero particles");
        Point from = p(-4, 2, 10);
        for (Point direction : List.of(X, Y, Z, unit(p(1, 2, 3)))) {
            for (double length : new double[] {.0001, .04, .8, 2, 4, 16, 96}) {
                Point to = from.plus(direction.scale(length));
                for (boolean hold : List.of(false, true)) {
                    List<Stroke> strokes = emission(from, to, 1, hold, false, false);
                    check(strokes.size() == 6, "five primary strokes and one delayed directional stroke");
                    double scale = Math.min(1, length / 4);
                    Stroke soft = texture(strokes, RailThunderArrayGrandGeometry.GLOW);
                    Stroke core = strokes.get(4);
                    check(soft.segment().start().equals(from) && soft.segment().end().equals(to),
                            "soft layer preserves exact accepted endpoints");
                    check(core.segment().start().equals(from) && core.segment().end().equals(to),
                            "core layer preserves exact accepted endpoints");
                    check(close(core.halfWidth(), .08 * scale) && close(soft.halfWidth(), .45 * scale),
                            "short rays scale both primary widths by min(1,length/4)");
                    check(soft.alpha() == 110, "soft shoulder has visible plateau opacity without wider geometry");
                    check(core.rgb() == (hold ? RailThunderArrayGeometry.BLUE_WHITE : RailThunderArrayGeometry.IVORY)
                            && soft.rgb() == (hold ? RailThunderArrayGeometry.BLUE : RailThunderArrayGeometry.ORANGE),
                            "tap is warm ivory/orange and hold is blue-white");
                    Stroke in = strokes.get(1), tile = strokes.get(2), out = strokes.get(3);
                    check(in.segment().start().equals(from) && out.segment().end().equals(to),
                            "original rail textures preserve exact accepted head and tail");
                    check(in.segment().end().equals(tile.segment().start())
                            && tile.segment().end().equals(out.segment().start()), "original three sections meet exactly");
                    for (int part = 0; part < 3; part++) {
                        Stroke rail = strokes.get(part + 1);
                        check(close(rail.halfWidth(), .20 * scale), "mid layer uses bounded optional width");
                        check(rail.texture() == (hold ? RailThunderArrayGrandGeometry.GLOW : RailThunderArrayGrandGeometry.RAIL_IN + part),
                                "hold uses neutral glow sections and tap retains original warm textures");
                        check(rail.rgb() == (hold ? RailThunderArrayGeometry.BLUE : 0xFFFFFF),
                                "hold blue never multiplies baked orange, and tap preserves original texture color");
                    }
                    for (Stroke stroke : strokes) verifyLongitudinal(stroke, from, direction, length);
                    Stroke secondary = strokes.getLast();
                    Point radial = secondary.segment().start().minus(from);
                    radial = radial.minus(direction.scale(radial.dot(direction)));
                    Point transverseSide = unit(direction.cross(Math.abs(direction.y()) > .9 ? X : Y));
                    Point transverseUp = transverseSide.cross(direction);
                    check(close(Math.abs(radial.dot(transverseSide)), .30 * scale)
                                    && close(radial.dot(transverseUp), .22 * scale),
                            "secondary retains two distinct transverse components when one points into camera depth");
                    check(close(radial.length(), Math.hypot(.30, .22) * scale)
                                    && close(secondary.halfWidth(), .035 * scale),
                            "blue-white secondary uses bounded .30/.22m offsets and .035m half-width");
                    check(radial.length() + secondary.halfWidth() < .45 * scale,
                            "whole secondary radial footprint stays below .45m, including its width, on short paths");
                    Point tailRadial = secondary.segment().end().minus(from);
                    tailRadial = tailRadial.minus(direction.scale(tailRadial.dot(direction)));
                    check(close(tailRadial.length(), radial.length() * .45),
                            "secondary tapers inward and never makes a parallel duplicate axis");
                    for (Stroke stroke : emission(from, to, 1, hold, true, false))
                        for (Quad q : project(stroke, ZERO, Z, Y)) for (Point corner : vertices(q)) {
                            double along = corner.minus(from).dot(direction);
                            check(along >= -1e-7 && along <= length + 1e-7,
                                    "all projected primary, secondary and contact corners stay within accepted longitudinal bounds");
                        }
                    List<Stroke> low = emission(from, to, 1, hold, false, true);
                    check(low.size() == 4 && low.stream().noneMatch(s -> close(s.halfWidth(), .45 * scale)),
                            "low LOD omits soft layer and secondary stroke while retaining exact primary path");
                }
            }
        }
    }

    private static void fiveTickClockAndCallerHit() {
        Point from = p(1, 2, 3), to = p(1, 2, 27);
        check(RailThunderArrayGrandGeometry.LIFETIME_TICKS == RailThunderArrayState.FLASH_TICKS,
                "optional emission cannot extend the existing five-tick receipt lifetime");
        List<Stroke> age0 = emission(from, to, 0, false, false, false);
        List<Stroke> age1 = emission(from, to, 1, false, false, false);
        List<Stroke> age3 = emission(from, to, 3, false, false, false);
        List<Stroke> age4 = emission(from, to, 4, false, false, false);
        check(age0.size() == 5 && emission(from, to, .5, false, false, false).size() == 5,
                "secondary waits the full first half-tick");
        check(emission(from, to, .5001, false, false, false).size() == 6 && age1.size() == 6,
                "secondary establishes only after the half-tick delay");
        check(age0.get(4).alpha() < age1.get(4).alpha(), "first tick establishes quickly");
        for (int i = 0; i < 5; i++) {
            check(age1.get(i).alpha() == age3.get(i).alpha(), "primary plateau lasts from one through three");
            check(Math.abs(age4.get(i).alpha() - age3.get(i).alpha() * .5) <= .5,
                    "fourth tick is halfway through the two-tick fade");
        }
        check(emission(from, to, 4.999, false, true, false).size() == 8,
                "all requested components share the same live final interval");
        for (double age : new double[] {5, 5.01, 100}) check(emission(from, to, age, true, true, false).isEmpty(),
                "nothing survives five ticks in hold or hit mode");
        check(age1.equals(emission(from, to, 1, false, false, false)), "paused age creates exactly identical geometry");
        for (int status : new int[] {RailThunderArrayState.PREVIEW, RailThunderArrayState.MISS,
                RailThunderArrayState.HIT, RailThunderArrayState.REFUSED, RailThunderArrayState.BLOCKED}) {
            // The caller supplies this comparison; the geometry owns neither receipts nor hit inference.
            boolean confirmedHit = status == RailThunderArrayState.HIT;
            List<Stroke> result = emission(from, to, 1, false, confirmedHit, false);
            check(result.size() == age1.size() + (confirmedHit ? 2 : 0), "only caller-confirmed HIT adds two ticks");
            check(result.subList(0, age1.size()).equals(age1), "status cannot change or lengthen the primary ray");
            if (confirmedHit) for (Stroke contact : result.subList(age1.size(), result.size())) {
                verifyLongitudinal(contact, from, Z, 24);
                for (Point point : List.of(contact.segment().start(), contact.segment().end()))
                    check(close(point.z(), to.z() - .015) && point.minus(to).length() <= .16,
                            "contact ticks stay modest and just inside the accepted endpoint plane");
            }
        }
        Stroke delayed1 = age1.getLast(), delayed3 = age3.getLast();
        check(delayed1.rgb() == RailThunderArrayGeometry.BLUE_WHITE
                && delayed3.segment().start().z() > delayed1.segment().start().z(),
                "delayed accent travels forward and remains blue-white");
    }

    private static void boundedPreparation() {
        check(RailThunderArrayGrandGeometry.PREPARATION_LENGTH == 2
                && RailThunderArrayGrandGeometry.PREPARATION_RADIUS == .8,
                "source-local optional preparation has explicit two-metre/.8m bounds");
        for (int axis = 0; axis < 3; axis++) for (Point direction : List.of(X, Y, Z, unit(p(1, -2, 3)))) {
            for (double length : new double[] {.0001, .04, .3, .8, 1.9, 2, 96}) {
                Point from = p(2, 3, 4), to = from.plus(direction.scale(length));
                List<Stroke> strokes = RailThunderArrayGrandGeometry.preparation(from, to, axis, 7.5, false);
                check(strokes.size() == 4, "one confirmed ray gets two open two-layer preparation rails");
                check(strokes.equals(RailThunderArrayGrandGeometry.preparation(from, to, axis, 7.5, false)),
                        "preparation is deterministic while paused");
                check(RailThunderArrayGrandGeometry.preparation(from, to, axis, 7.5, true).size() == 2,
                        "low preparation reduces layers");
                double scale = Math.min(1, length / 2), reach = Math.min(2, length);
                for (int rail = 0; rail < 2; rail++) {
                    Stroke body = strokes.get(rail * 2), core = strokes.get(rail * 2 + 1);
                    check(body.texture() == RailThunderArrayGrandGeometry.GLOW
                                    && body.rgb() == RailThunderArrayGeometry.ORANGE && body.alpha() >= 102
                                    && close(body.halfWidth(), .075 * scale),
                            "preparation has a visible warm body instead of a faint white rod");
                    check(core.texture() == RailThunderArrayGrandGeometry.WHITE
                                    && core.rgb() == RailThunderArrayGeometry.IVORY && core.alpha() >= 114
                                    && close(core.halfWidth(), .011 * scale),
                            "preparation retains a narrower ivory core on the same open rail");
                    check(body.segment().equals(core.segment()), "warm body and ivory core share exactly the same local rail");
                }
                for (Stroke stroke : strokes) {
                    verifyLongitudinal(stroke, from, direction, reach);
                    double[] radius = new double[2];
                    int i = 0;
                    for (Point point : List.of(stroke.segment().start(), stroke.segment().end())) {
                        Point relative = point.minus(from);
                        radius[i++] = relative.minus(direction.scale(relative.dot(direction))).length();
                        check(radius[i - 1] + stroke.halfWidth() <= .8, "open preparation fits radius .8 including ribbon");
                    }
                    check(radius[0] > radius[1], "preparation rails point inward while staying open");
                    check(close(radius[0], .68 * scale) && close(radius[1], .10 * scale),
                            "short-path scaling preserves open inward shape without oversizing a short receipt");
                    for (Point look : List.of(Z, X, unit(p(1, 1, 1)))) {
                        Point camera = from.minus(look.scale(8));
                        List<Quad> projected = project(stroke, camera, look, Y);
                        verifyQuads(projected, look, Y);
                        for (Quad q : projected) for (Point corner : vertices(q)) {
                            Point relative = corner.plus(camera).minus(from);
                            double along = relative.dot(direction);
                            check(along >= -1e-7 && along <= reach + 1e-7,
                                    "actual projected preparation corners stay inside the first min(2m,path)");
                            check(relative.minus(direction.scale(along)).length() <= .8 + 1e-7,
                                    "actual projected preparation corners remain inside the .8m radial bound");
                        }
                    }
                }
                check(strokes.get(1).segment().end().minus(strokes.get(3).segment().end()).length() > 0,
                        "preparation does not close across its mouth or form a shield ring");
            }
        }
    }

    private static void cameraClippingAndUv() {
        for (double width : new double[] {.009, .08, .20, .45}) {
            Stroke crossing = stroke(p(-3, 0, 10), p(3, 0, 10), width);
            List<Quad> quads = project(crossing, ZERO, Z, Y);
            check(quads.size() == 2, "an aim-center crossing clips to at most two separated pieces");
            check(quads.getFirst().minV() == 0 && quads.getLast().maxV() == 1,
                    "center clipping preserves source longitudinal texture endpoints");
            check(quads.getFirst().maxV() < quads.getLast().minV(), "center gap also skips corresponding original texture span");
            verifyQuads(quads, Z, Y);
            check(project(stroke(p(0, 0, 1), p(0, 0, 96), width), ZERO, Z, Y).isEmpty(),
                    "direct aim-center beam never covers the aiming point");
            check(project(stroke(p(2, 0, -4), p(2, 0, -.1), width), ZERO, Z, Y).isEmpty(),
                    "behind-camera beam is culled");
            Stroke near = stroke(p(2, 0, -1), p(2, 0, 12), width);
            List<Quad> clipped = project(near, ZERO, Z, Y);
            check(!clipped.isEmpty() && clipped.getFirst().minV() > 0,
                    "near clipping preserves UV fraction without stretching the cap");
            check(clipped.getLast().maxV() == 1, "near clipping preserves the accepted far endpoint");
            verifyQuads(clipped, Z, Y);
            for (Quad q : clipped) check(halfWidth(q) <= width && halfWidth(q) <= minCenterDepth(q, Z) * .024 + 1e-10,
                    "whole optional ribbon obeys world and angular width caps");
            List<Quad> reversed = project(stroke(near.segment().end(), near.segment().start(), width), ZERO, Z, Y);
            check(!reversed.isEmpty() && reversed.getFirst().minV() == 0 && reversed.getLast().maxV() < 1,
                    "reverse near crossing clips the actual tail and corresponding UV");
            verifyQuads(reversed, Z, Y);
        }
        List<Quad> wide = project(stroke(p(-3, 2, 30), p(3, 2, 30), .45), ZERO, Z, Y);
        check(!wide.isEmpty() && close(halfWidth(wide.getFirst()), .45),
                "optional helper admits full .45 width at adequate depth");
        check(RailThunderArrayGeometry.nearClip(p(-3, 2, 30), p(3, 2, 30), Z, .45) == null,
                "classic shared helper still rejects the optional width");
        Point parallelFrom = p(1, 0, 5), parallelTo = p(2, 0, 10);
        List<Quad> parallel = project(stroke(parallelFrom, parallelTo, .2), ZERO, Z, Y);
        check(!parallel.isEmpty(), "view-parallel path gets a finite deterministic side fallback");
        verifyQuads(parallel, Z, Y);
        List<Quad> yawed = project(stroke(p(10, 2, -3), p(10, 2, 3), .2), ZERO, X, Y);
        check(!yawed.isEmpty(), "projection uses actual camera look instead of world Z");
        verifyQuads(yawed, X, Y);
    }

    private static void worldBorderTranslation() {
        Point from = p(-3, 2, 10), to = p(3, 2, 10);
        for (Point translation : List.of(p(29_999_980, 4000, 29_999_980), p(-29_999_980, -4000, -29_999_980))) {
            for (boolean hold : List.of(false, true)) {
                List<Stroke> original = emission(from, to, 1.5, hold, true, false);
                List<Stroke> shifted = emission(from.plus(translation), to.plus(translation), 1.5, hold, true, false);
                check(original.size() == shifted.size(), "world-border translation preserves every stroke");
                for (int i = 0; i < original.size(); i++) {
                    List<Quad> a = project(original.get(i), ZERO, Z, Y);
                    List<Quad> b = project(shifted.get(i), translation, Z, Y);
                    check(a.size() == b.size(), "world-border camera subtraction preserves clipping topology");
                    for (int j = 0; j < a.size(); j++) {
                        check(Math.abs(a.get(j).minV() - b.get(j).minV()) < 1e-6
                                && Math.abs(a.get(j).maxV() - b.get(j).maxV()) < 1e-6,
                                "world-border translation preserves clipped UV fractions");
                        for (int k = 0; k < 4; k++) check(vertices(a.get(j)).get(k).minus(vertices(b.get(j)).get(k)).length() < 1e-6,
                                "double camera subtraction avoids far-coordinate float collapse");
                    }
                    verifyQuads(b, Z, Y);
                }
            }
        }
    }

    private static void variedCameraGeometry() {
        Random random = new Random(15156);
        for (int sample = 0; sample < 800; sample++) {
            Point look = unit(p(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1));
            Point right = unit(look.cross(Math.abs(look.y()) > .9 ? X : Y));
            Point up = right.cross(look);
            Point from = look.scale(random.nextDouble() * 20 - 3)
                    .plus(right.scale(random.nextDouble() * 8 - 4)).plus(up.scale(random.nextDouble() * 8 - 4));
            Point to = from.plus(look.scale(random.nextDouble() * 40 - 20))
                    .plus(right.scale(random.nextDouble() * 20 - 10)).plus(up.scale(random.nextDouble() * 20 - 10));
            double width = .001 + random.nextDouble() * .449;
            List<Quad> quads = project(stroke(from, to, width), ZERO, look, up);
            check(quads.size() <= 2, "arbitrary valid camera cannot exceed two output quads");
            verifyQuads(quads, look, up);
            Point direction = unit(to.minus(from));
            for (Quad q : quads) {
                check(halfWidth(q) <= width + 1e-10, "camera clipping never widens its requested stroke");
                for (Point corner : vertices(q)) {
                    double along = corner.minus(from).dot(direction);
                    check(along >= -1e-7 && along <= to.minus(from).length() + 1e-7,
                            "projected corners never pass the original stroke's longitudinal endpoints");
                }
            }
        }
    }

    private static void invalidInputs() {
        Point good = p(1, 2, 3), end = p(1, 2, 10);
        List<Point> invalid = new ArrayList<>();
        invalid.add(null); invalid.add(p(Double.NaN, 0, 0)); invalid.add(p(0, Double.POSITIVE_INFINITY, 0));
        invalid.add(p(30_000_001, 0, 0)); invalid.add(p(0, 4097, 0));
        for (Point point : invalid) {
            check(emission(point, end, 1, false, true, false).isEmpty()
                    && emission(good, point, 1, false, true, false).isEmpty(), "invalid endpoints produce no emission");
            check(RailThunderArrayGrandGeometry.preparation(point, end, 0, 1, false).isEmpty(),
                    "invalid confirmed preview produces no geometry");
            check(project(stroke(good, end, .2), point, Z, Y).isEmpty(), "invalid camera produces no vertices");
        }
        for (double clock : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -1}) {
            check(emission(good, end, clock, false, true, false).isEmpty(), "invalid age produces no emission");
            check(RailThunderArrayGrandGeometry.preparation(good, end, 0, clock, false).isEmpty(),
                    "invalid preparation clock produces no geometry");
        }
        for (int axis : new int[] {-1, 3, Integer.MAX_VALUE}) {
            check(RailThunderArrayGrandGeometry.emission(good, end, axis, 1, 1, false, true, false).isEmpty(),
                    "unconfirmed fourth or invalid axis produces no emission");
            check(RailThunderArrayGrandGeometry.preparation(good, end, axis, 1, false).isEmpty(),
                    "unconfirmed fourth or invalid axis produces no preparation");
        }
        for (Point to : List.of(good, good.plus(Z.scale(96.01)))) {
            check(emission(good, to, 1, false, true, false).isEmpty(), "zero or oversized path produces no emission");
            check(project(stroke(good, to, .2), ZERO, Z, Y).isEmpty(), "zero or oversized stroke produces no vertices");
        }
        for (double width : new double[] {0, -.1, .450001, Double.NaN, Double.POSITIVE_INFINITY})
            check(project(stroke(good, end, width), ZERO, Z, Y).isEmpty(), "invalid or excessive optional width is rejected");
        for (Point direction : new Point[] {null, ZERO, p(Double.NaN, 1, 0)}) {
            check(project(stroke(good, end, .2), ZERO, direction, Y).isEmpty(), "invalid camera look produces no vertices");
            check(project(stroke(good, end, .2), ZERO, Z, direction).isEmpty(), "invalid camera up produces no vertices");
        }
        check(project(stroke(good, end, .2), ZERO, Z, Z).isEmpty(), "parallel camera look/up is rejected");
        check(project(stroke(p(1, 0, 300), p(2, 0, 301), .2), ZERO, Z, Y).isEmpty(), "distant unsupported camera-relative path fails closed");
        check(project(null, ZERO, Z, Y).isEmpty() && project(new Stroke(null, .2, 0xFFFFFF, 255, 0), ZERO, Z, Y).isEmpty(),
                "null stroke or segment produces no vertices");
        for (Stroke bad : List.of(new Stroke(new Segment(null, end), .2, 0xFFFFFF, 255, 0),
                new Stroke(new Segment(good, end), .2, -1, 255, 0),
                new Stroke(new Segment(good, end), .2, 0xFFFFFF, 0, 0),
                new Stroke(new Segment(good, end), .2, 0xFFFFFF, 256, 0),
                new Stroke(new Segment(good, end), .2, 0xFFFFFF, 255, 5)))
            check(project(bad, ZERO, Z, Y).isEmpty(), "malformed stroke metadata produces no vertices");
    }

    private static void controllerBudgetsAndImmutability() {
        check(RailThunderArrayGrandGeometry.MAX_CONTROLLERS == 4
                && RailThunderArrayGrandGeometry.MAX_STROKES_PER_CONTROLLER == 128
                && RailThunderArrayGrandGeometry.MAX_QUADS_PER_CONTROLLER == 256
                && RailThunderArrayGrandGeometry.MAX_QUADS_GLOBAL == 1024
                && RailThunderArrayGrandGeometry.MAX_VERTICES_GLOBAL == 4096, "full-controller and global budgets are explicit");
        int globalQuads = 0;
        for (int controller = 0; controller < 4; controller++) {
            int strokes = 0, quads = 0;
            for (int receipt = 0; receipt < RailThunderArrayGeometry.MAX_SEGMENTS; receipt++) {
                List<Stroke> result = RailThunderArrayGrandGeometry.emission(p(-10, 0, 10), p(10, 0, 10),
                        receipt % 3, controller * 16L + receipt, 1, true, true, false);
                check(result.size() == 8, "full hold hit receipt includes at most eight preclip strokes");
                strokes += result.size();
                for (Stroke stroke : result) {
                    List<Quad> projected = project(stroke, ZERO, Z, Y);
                    check(projected.size() <= 2, "each stroke has at most two clipped quads");
                    quads += projected.size();
                }
            }
            check(strokes == 128 && quads <= 256, "sixteen full hold target receipts fit one controller without partial dropping");
            globalQuads += quads;
        }
        check(globalQuads > 0 && globalQuads <= 1024 && globalQuads * 4 <= 4096,
                "four simultaneous worst receipt sets remain within global quads and vertices");
        int tapStrokes = 0;
        for (int axis = 0; axis < 3; axis++) tapStrokes += RailThunderArrayGrandGeometry.emission(p(-10, 0, 10), p(10, 0, 10),
                axis, 1, 1, false, true, false).size();
        check(tapStrokes == 24 && tapStrokes * 2 <= 256, "three full tap rays fit without degrading a subset");
        List<Stroke> result = emission(p(-3, 2, 10), p(3, 2, 10), 1, false, true, false);
        boolean immutable = false;
        try { result.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "emission result is immutable");
        List<Quad> quads = project(result.getFirst(), ZERO, Z, Y);
        immutable = false;
        try { quads.clear(); } catch (UnsupportedOperationException expected) { immutable = true; }
        check(immutable, "projected result is immutable");
    }

    private static void verifyLongitudinal(Stroke stroke, Point from, Point direction, double length) {
        check(stroke.halfWidth() > 0 && stroke.halfWidth() <= .45 && stroke.alpha() > 0 && stroke.alpha() <= 255,
                "every output stroke has finite bounded width and alpha");
        for (Point point : List.of(stroke.segment().start(), stroke.segment().end())) {
            double along = point.minus(from).dot(direction);
            check(point.finite() && along >= -1e-7 && along <= length + 1e-7,
                    "every stroke stays within the exact accepted longitudinal path");
        }
    }

    private static void verifyQuads(List<Quad> quads, Point look, Point up) {
        Point forward = unit(look), side = unit(forward.cross(up)), cameraUp = side.cross(forward);
        for (Quad q : quads) {
            check(q.minV() >= 0 && q.maxV() <= 1 && q.minV() <= q.maxV(), "UV interval remains finite within source segment");
            for (int along = 0; along <= 4; along++) for (int across = 0; across <= 4; across++) {
                Point point = q.a().lerp(q.d(), along / 4.0).lerp(q.b().lerp(q.c(), along / 4.0), across / 4.0);
                double depth = point.dot(forward);
                check(point.finite() && depth >= .20 - 1e-8, "whole quad stays beyond near plane");
                check(Math.max(Math.abs(point.dot(side)), Math.abs(point.dot(cameraUp))) >= depth * .022 - 1e-8,
                        "quad interior and edges preserve empty aiming center");
            }
        }
    }

    private static List<Stroke> emission(Point from, Point to, double age, boolean hold, boolean hit, boolean low) {
        return RailThunderArrayGrandGeometry.emission(from, to, 0, 15156, age, hold, hit, low);
    }
    private static List<Quad> project(Stroke stroke, Point camera, Point look, Point up) {
        return RailThunderArrayGrandGeometry.project(stroke, camera, look, up);
    }
    private static Stroke stroke(Point a, Point b, double width) { return new Stroke(new Segment(a, b), width, 0xFFFFFF, 220, 0); }
    private static Stroke texture(List<Stroke> strokes, int texture) { return strokes.stream().filter(s -> s.texture() == texture).findFirst().orElseThrow(); }
    private static List<Point> vertices(Quad q) { return List.of(q.a(), q.b(), q.c(), q.d()); }
    private static double halfWidth(Quad q) { return q.a().minus(q.b()).length() * .5; }
    private static double minCenterDepth(Quad q, Point forward) { return Math.min(q.a().lerp(q.b(), .5).dot(forward), q.c().lerp(q.d(), .5).dot(forward)); }
    private static Point p(double x, double y, double z) { return new Point(x, y, z); }
    private static Point unit(Point point) { return point.scale(1 / point.length()); }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-8; }
    private static void check(boolean ok, String message) { checks++; if (!ok) throw new AssertionError(message); }
}
