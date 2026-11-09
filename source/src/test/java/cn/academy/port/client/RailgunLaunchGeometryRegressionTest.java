package cn.academy.port.client;

import cn.academy.port.client.vfx2.RailgunLaunchGeometry;
import cn.academy.port.client.vfx2.RailgunLaunchGeometry.Layer;
import cn.academy.port.client.vfx2.RailgunLaunchGeometry.Quad;
import cn.academy.port.client.vfx2.RailgunOverlayGeometry.Vec;
import cn.academy.port.client.vfx2.RailgunOverlayGeometry.Vertex;
import java.util.List;
import java.util.Random;

/** Standalone checks of the exact pure launch geometry consumed by the optional renderer. */
public final class RailgunLaunchGeometryRegressionTest {
    private static final Vec ZERO = new Vec(0, 0, 0);
    private static final Vec X = new Vec(1, 0, 0);
    private static final Vec Y = new Vec(0, 1, 0);
    private static final Vec Z = new Vec(0, 0, 1);
    private static int checks;

    public static void main(String[] args) {
        metadataAndPhaseSnapshots();
        launchShapeAndClock();
        trailShapeAndClock();
        acceptedLengthAndShortScaling();
        nearPlaneAndProjectedBounds();
        exactJoinsAndPause();
        largeCoordinateTranslation();
        variedViewsAndInputs();
        System.out.println("PASS " + checks + " Railgun launch geometry checks");
    }

    private static void metadataAndPhaseSnapshots() {
        check(Layer.ENVELOPE.profile() == 0 && Layer.TRAIL.profile() == 1,
                "shader profiles distinguish protected launch envelope from narrow trails");
        check(Layer.ENVELOPE.red() == 250 && Layer.ENVELOPE.green() == 201 && Layer.ENVELOPE.blue() == 132,
                "launch envelope retains warm classic amber");
        check(Layer.TRAIL.red() == 236 && Layer.TRAIL.green() == 170 && Layer.TRAIL.blue() == 93,
                "decorative trails retain warm outer amber");
        check(close(Layer.ENVELOPE.baseAlpha(), .42) && close(Layer.TRAIL.baseAlpha(), .24),
                "each profile has its approved maximum alpha");
        check(RailgunLaunchGeometry.LIFETIME_MILLIS == 2500,
                "original classic lifecycle is retained exactly");
        check(RailgunLaunchGeometry.MAX_QUADS == 12 && RailgunLaunchGeometry.MAX_VERTICES == 48,
                "complete decoration keeps the existing twelve-quad forty-eight-vertex ceiling");
        for (double age : new double[] {0, 300, 400, 1100, 1200, 1900, 2500}) {
            List<Quad> geometry = transverse(age, 1, 64);
            int expectedEnvelope = age > 0 && age < 1100 ? 4 : 0;
            int expectedTrail = age >= 300 && age < 1900 ? 8 : 0;
            check(count(geometry, Layer.ENVELOPE) == expectedEnvelope,
                    "launch phase snapshot has exactly its complete envelope or no envelope");
            check(count(geometry, Layer.TRAIL) == expectedTrail,
                    "launch phase snapshot has both complete trails or no trails");
            verify(geometry, new Vec(0, 0, 10), X, 64, ZERO, Z);
        }
        check(transverse(2501, 1, 64).isEmpty(), "classic expiry never leaves a decorative tail");
    }

    private static void launchShapeAndClock() {
        List<Quad> expanded = transverse(300, 1, 64);
        List<Quad> stable = transverse(400, 1, 64);
        check(close(maxRadius(expanded, Layer.ENVELOPE, new Vec(0, 0, 10), X, 64), 1.6),
                "launch envelope reaches the absolute 1.6 m radius by 300 ms");
        check(layer(expanded, Layer.ENVELOPE).equals(layer(stable, Layer.ENVELOPE)),
                "launch geometry and alpha hold steady from 300 through 400 ms");
        for (double age : new double[] {1e-6, 40, 80, 150, 300, 400, 750, 1099}) {
            List<Quad> geometry = transverse(age, 1, 64);
            List<Quad> envelope = layer(geometry, Layer.ENVELOPE);
            check(envelope.size() == 4, "live launch envelope is always a complete four-quad ribbon");
            check(close(firstWidth(envelope), 0) && close(lastWidth(envelope), 0),
                    "launch radius tapers geometrically to zero at physical origin and local end");
            check(close(envelope.getFirst().a().u(), 0) && close(envelope.getLast().b().u() * 64, 12),
                    "launch decoration spans at most the first twelve accepted metres");
            double largest = maxRadius(geometry, Layer.ENVELOPE, new Vec(0, 0, 10), X, 64);
            for (Quad quad : envelope) for (Vertex vertex : vertices(quad)) {
                double distance = vertex.u() * 64;
                double radius = radial(vertex, new Vec(0, 0, 10), X, 64);
                if (close(radius, largest))
                    check(distance >= 1.2 && distance <= 4, "widest sampled launch region remains 1.2 to 4 m ahead");
            }
        }
        check(close(maxRadius(transverse(1e-6, 1, 64), Layer.ENVELOPE, new Vec(0, 0, 10), X, 64), .35),
                "envelope begins at the approved .35 m radius");
        check(close(maxAlpha(transverse(40, 1, 64), Layer.ENVELOPE), .21),
                "launch alpha rises smoothly through half strength at 40 ms");
        check(close(maxAlpha(transverse(80, 1, 64), Layer.ENVELOPE), .42),
                "launch alpha finishes rising at 80 ms");
        check(close(maxAlpha(transverse(750, 1, 64), Layer.ENVELOPE), .21),
                "launch body smoothly fades through half strength between 400 and 1100 ms");
        check(layer(transverse(1100, 1, 64), Layer.ENVELOPE).isEmpty(),
                "there is no residual launch body at 1100 ms");
        check(layer(transverse(1200, 1, 64), Layer.ENVELOPE).isEmpty(),
                "late decorative trails cannot bring the launch body back");
        check(close(maxRadius(transverse(300, 1e300, 64), Layer.ENVELOPE, new Vec(0, 0, 10), X, 64), 1.6),
                "arbitrarily large caller width cannot expand the absolute launch radius");
    }

    private static void trailShapeAndClock() {
        List<Quad> trails = layer(transverse(500, 1, 64), Layer.TRAIL);
        check(trails.size() == 8, "two decorative trails have exactly four quads each");
        double[] spans = {18, 14.4}, offsets = {1.10, .858}, widths = {.10, .085};
        double[] centerProfile = {0, .15625, .5, .84375, 1};
        double[] widthProfile = {0, 1, 1, .5, 0};
        double[] alphaProfile = {0, 1, 1900.0 / 2197, 725.0 / 2197, 0};
        for (int component = 0; component < 2; component++) {
            List<Quad> ribbon = trails.subList(component * 4, component * 4 + 4);
            check(close(firstWidth(ribbon), 0) && close(lastWidth(ribbon), 0),
                    "trail ends taper to zero width without caps");
            check(close(ribbon.getFirst().a().u(), 0) && close(ribbon.getLast().b().u() * 64, spans[component]),
                    "long and short trails end at their approved eighteen and fourteen-point-four metre spans");
            double precedingOffset = -1;
            for (int station = 0; station <= 4; station++) {
                Vertex a = station == 0 ? ribbon.getFirst().a() : ribbon.get(station - 1).b();
                Vertex b = station == 0 ? ribbon.getFirst().d() : ribbon.get(station - 1).c();
                Vec center = midpoint(a, b);
                Vec onAxis = new Vec(spans[component] * station / 4, 0, 10);
                double offset = center.subtract(onAxis).length();
                check(close(offset, offsets[component] * centerProfile[station]),
                        "trail center opens smoothly from the axis to its distinct off-axis end");
                check(offset > precedingOffset,
                        "trail center never returns toward the axis to close an eye or diamond");
                check(close(width(a, b), widths[component] * widthProfile[station]),
                        "trail width rises smoothly and tapers to zero within its distinct bound");
                check(close(a.alpha(), .24 * alphaProfile[station]) && close(a.alpha(), b.alpha()),
                        "longitudinal opacity dissipates before the off-axis zero-width end");
                precedingOffset = offset;
            }
            check(ribbon.getLast().b().alpha() == 0 && ribbon.getLast().c().alpha() == 0,
                    "far trail tip is completely transparent instead of a bright closed outline");
            check(ribbon.get(1).b().alpha() > ribbon.get(2).b().alpha()
                    && ribbon.get(2).b().alpha() > ribbon.getLast().b().alpha(),
                    "trail opacity decreases along its outer half");
        }
        Vec leadingCenter = midpoint(trails.get(1).b(), trails.get(1).c());
        Vec shorterCenter = midpoint(trails.get(5).b(), trails.get(5).c());
        check(leadingCenter.y() * shorterCenter.y() < 0,
                "the two opening trails remain on opposite sides of the accepted axis");
        check(!close(Math.abs(leadingCenter.y()), Math.abs(shorterCenter.y()))
                && !close(leadingCenter.x(), shorterCenter.x()),
                "trail center offsets and lengths intentionally have modest asymmetry");
        check(layer(transverse(120, 1, 64), Layer.TRAIL).isEmpty(), "trails start with zero alpha at 120 ms");
        check(close(maxAlpha(transverse(210, 1, 64), Layer.TRAIL), .12),
                "trail fade-in crosses half strength halfway between 120 and 300 ms");
        check(close(maxAlpha(transverse(300, 1, 64), Layer.TRAIL), .24), "trails reach full alpha at 300 ms");
        check(close(maxAlpha(transverse(1300, 1, 64), Layer.TRAIL), .24), "trail alpha holds through 1300 ms");
        check(close(maxAlpha(transverse(1600, 1, 64), Layer.TRAIL), .108),
                "trail fade-out multiplies the original classic final-second fade");
        check(close(maxAlpha(transverse(1750, 1, 64), Layer.TRAIL), .028125),
                "late trails keep both their own fade and the classic lifetime fade");
        check(transverse(1900, 1, 64).isEmpty(), "all launch decoration is absent at 1900 ms");
    }

    private static void acceptedLengthAndShortScaling() {
        for (double length : new double[] {.001, .1, 1, 3, 5.99, 6, 10, 12, 18, 32, 512, 1e100}) {
            List<Quad> geometry = transverse(500, 1, length);
            check(geometry.size() == 12, "any visible accepted length uses whole components within the same budget");
            verify(geometry, new Vec(0, 0, 10), X, length, ZERO, Z);
            check(close(layer(geometry, Layer.ENVELOPE).getLast().b().u() * length, Math.min(length, 12)),
                    "launch end comes from the actual supplied visible axis length");
            List<Quad> trails = layer(geometry, Layer.TRAIL);
            check(close(trails.get(3).b().u() * length, Math.min(length, 18))
                    && close(trails.getLast().b().u() * length, Math.min(length, 14.4)),
                    "both asymmetric trail ends respect the actual supplied visible axis length");
            double scale = Math.min(1, length / 6);
            check(close(maxRadius(geometry, Layer.ENVELOPE, new Vec(0, 0, 10), X, length), 1.6 * scale),
                    "short visible intervals scale the launch radius by length over six metres");
            check(close(maxRadius(geometry, Layer.TRAIL, new Vec(0, 0, 10), X, length), 1.1 * scale),
                    "short visible intervals scale trail widths and center offsets together");
        }
        // This pure signature receives no callback name, nominal skill range or entity data.
        List<Quad> longAxis = transverse(500, 1, 512), shorterAxis = transverse(500, 1, 32);
        for (int i = 0; i < longAxis.size(); i++) {
            List<Vertex> longerVertices = vertices(longAxis.get(i)), shorterVertices = vertices(shorterAxis.get(i));
            for (int j = 0; j < 4; j++) {
                Vertex longer = longerVertices.get(j), shorter = shorterVertices.get(j);
                check(longer.position().equals(shorter.position()) && longer.alpha() == shorter.alpha()
                        && longer.v() == shorter.v(),
                        "longer accepted ranges retain exactly the same local positions and appearance");
                check(close(longer.u() * 512, shorter.u() * 32),
                        "longitudinal fractions continue to refer to their actual accepted ranges");
            }
        }
        List<Quad> shortResult = transverse(500, 1, 3);
        check(shortResult.stream().flatMap(q -> vertices(q).stream()).allMatch(v -> v.u() <= 1),
                "a short accepted axis is never lengthened to fit decorative geometry");
    }

    private static void nearPlaneAndProjectedBounds() {
        for (Vec start : List.of(new Vec(2, 1, -4), new Vec(2, 1, 6), new Vec(0, 0, .18))) {
            Vec axis = start.z() == 6 ? Z.scale(-1) : Z;
            List<Quad> geometry = build(start, axis, 32, ZERO, Z, 400, 1);
            check(!geometry.isEmpty(), "near-plane crossing retains a safe original subinterval");
            verify(geometry, start, axis, 32, ZERO, Z);
            for (Layer layer : Layer.values()) {
                List<Quad> ribbon = layer(geometry, layer);
                check(ribbon.size() == (layer == Layer.ENVELOPE ? 4 : 8),
                        "near clipping keeps all quads of each admitted component");
                int segments = 4;
                for (int component = 0; component < ribbon.size(); component += segments) {
                    Vertex clipped = start.z() == 6 ? ribbon.get(component + segments - 1).b()
                            : ribbon.get(component).a();
                    double axisDepth = start.add(axis.scale(clipped.u() * 32)).dot(Z);
                    double expectedRadius = start.z() == .18 ? .036 : .03;
                    check(close(axisDepth, .15 + expectedRadius + 1e-9),
                            "first or last clipped station protects the entire radial extent conservatively");
                    check(clipped.alpha() < 1e-12,
                            "the newly clipped first or last station begins at zero near-fade alpha");
                }
            }
        }
        check(build(new Vec(100, 0, -1), X, 32, ZERO, Z, 400, 1).isEmpty(),
                "a distant launch behind actual camera forward fails closed");
        check(build(new Vec(0, 0, 10), X, 32, ZERO, Z.scale(-1), 400, 1).isEmpty(),
                "actual camera forward controls safety rather than camera distance");
        check(build(new Vec(0, 0, .16), X, 32, ZERO, Z, 400, 1).isEmpty(),
                "axis in front of near plane but unsafe for full extent is suppressed");
        check(build(new Vec(0, 0, -16), Z, 32, ZERO, Z, 500, 1).isEmpty(),
                "asymmetric spans never emit a single trail when its partner is wholly clipped");

        for (double depth : new double[] {.20, .30, .60, 1, 2, 4, 8, 10}) {
            Vec start = new Vec(0, 0, depth);
            List<Quad> geometry = build(start, X, 64, ZERO, Z, 500, 1);
            verify(geometry, start, X, 64, ZERO, Z);
            double envelopeProjected = 0, trailProjected = 0;
            for (Quad quad : geometry) for (Vertex vertex : vertices(quad)) {
                // Actual perspective projection of each emitted corner, not a nominal radius.
                Vec p = vertex.position();
                double projected = Math.abs(p.y() / p.z());
                check(Double.isFinite(projected) && projected <= .20 + 1e-9,
                        "actual projected corner remains inside the twenty-percent transverse bound");
                if (quad.layer() == Layer.ENVELOPE) envelopeProjected = Math.max(envelopeProjected, projected);
                else trailProjected = Math.max(trailProjected, projected);
            }
            check(close(envelopeProjected, Math.min(1.6, .20 * depth) / depth),
                    "actual envelope geometry reaches exactly the bounded projection at a clear transverse view");
            check(close(trailProjected, Math.min(1.2, .20 * depth) / depth * (1.1 / 1.2)),
                    "open trail centers remain within the conservative width-plus-offset projected radius cap");
        }
        List<Quad> halfFade = build(new Vec(0, 0, .375000001), X, 64, ZERO, Z, 400, 1);
        check(close(maxAlpha(halfFade, Layer.ENVELOPE), .21),
                "near fade uses conservative full radial extent plus .30 m fade depth");
        List<Quad> remoteOrigin = build(new Vec(-100, 0, 10), X, 512, ZERO, Z, 400, 1);
        for (Quad quad : remoteOrigin) for (Vertex vertex : vertices(quad))
            check(vertex.position().x() <= -82 + 1e-9,
                    "a remote launch never adds a body at the later beam midpoint");
    }

    private static void exactJoinsAndPause() {
        for (double age : new double[] {1, 80, 300, 400, 1100, 1200, 1300, 1750, 1899}) {
            List<Quad> geometry = transverse(age, 1, 64);
            check(geometry.equals(transverse(age, 1, 64)), "a frozen caller clock yields identical geometry");
            for (Layer layer : Layer.values()) {
                List<Quad> ribbons = layer(geometry, layer);
                int segments = 4;
                for (int base = 0; base < ribbons.size(); base += segments)
                    for (int i = 1; i < segments; i++) {
                        Quad previous = ribbons.get(base + i - 1), next = ribbons.get(base + i);
                        check(previous.b().equals(next.a()) && previous.c().equals(next.d()),
                                "shared ribbon joins have identical positions, UV and alpha");
                    }
            }
        }
        List<Quad> immutable = transverse(400, 1, 64);
        boolean rejected = false;
        try { immutable.add(immutable.getFirst()); } catch (UnsupportedOperationException expected) { rejected = true; }
        check(rejected, "returned geometry is immutable");
    }

    private static void largeCoordinateTranslation() {
        Vec start = new Vec(3, .125, 8), axis = new Vec(.2, .35, 1);
        List<Quad> original = build(start, axis, 32, ZERO, Z, 300, .75);
        for (Vec translation : List.of(new Vec(30_000_000, -30_000_000, 30_000_000),
                new Vec(1e12, -1e12, 1e12), new Vec(-1e12, 1e12, -1e12))) {
            List<Quad> translated = build(start.add(translation), axis, 32, translation, Z, 300, .75);
            check(original.equals(translated), "double camera subtraction preserves thin geometry at large world coordinates");
            verify(translated, start.add(translation), axis, 32, translation, Z);
        }
        check(!build(start, new Vec(1e308, 1e308, 1e308), 32, ZERO, Z, 400, 1).isEmpty(),
                "huge finite direction is normalized without norm overflow");
        check(!build(start, new Vec(Double.MIN_VALUE, 0, 0), 32, ZERO,
                new Vec(0, 0, Double.MIN_VALUE), 400, 1).isEmpty(),
                "nonzero microscopic direction and forward normalize safely");
    }

    private static void variedViewsAndInputs() {
        for (Vec axis : List.of(X, Y, Z, X.scale(-1), Y.scale(-1), Z.scale(-1))) {
            Vec start = axis.scale(2);
            List<Quad> geometry = build(start, axis, 32, ZERO, axis, 400, 1);
            check(!geometry.isEmpty(), "parallel camera uses a deterministic finite basis");
            check(geometry.equals(build(start, axis, 32, ZERO, axis, 400, 1)), "parallel fallback is reproducible");
            verify(geometry, start, axis, 32, ZERO, axis);
        }
        Random random = new Random(0x4c41554e4348L);
        for (int i = 0; i < 800; i++) {
            Vec forward = unit(new Vec(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1));
            Vec axis = unit(new Vec(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1,
                    random.nextDouble() * 2 - 1));
            Vec camera = new Vec(i % 3, i % 5, i % 7);
            Vec start = camera.add(forward.scale(4 + random.nextDouble() * 16));
            double length = .001 + random.nextDouble() * 512;
            double age = 1 + random.nextDouble() * 1898;
            verify(build(start, axis, length, camera, forward, age, .1 + random.nextDouble() * 100),
                    start, axis, length, camera, forward);
        }
        Vec start = new Vec(0, 0, 10);
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(build(start, X, bad, ZERO, Z, 400, 1).isEmpty(), "invalid accepted length fails closed");
            check(build(start, X, 32, ZERO, Z, 400, bad).isEmpty(), "invalid caller width fails closed");
        }
        for (double bad : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(build(start, X, 32, ZERO, Z, bad, 1).isEmpty(), "invalid caller age fails closed");
        for (Vec bad : new Vec[] {null, new Vec(Double.NaN, 0, 0), new Vec(0, Double.POSITIVE_INFINITY, 0),
                new Vec(0, 0, Double.NEGATIVE_INFINITY)}) {
            check(build(bad, X, 32, ZERO, Z, 400, 1).isEmpty(), "invalid world origin fails closed");
            check(build(start, bad, 32, ZERO, Z, 400, 1).isEmpty(), "invalid supplied direction fails closed");
            check(build(start, X, 32, bad, Z, 400, 1).isEmpty(), "invalid camera position fails closed");
            check(build(start, X, 32, ZERO, bad, 400, 1).isEmpty(), "invalid camera forward fails closed");
        }
        check(build(start, ZERO, 32, ZERO, Z, 400, 1).isEmpty(), "zero direction fails closed");
        check(build(start, X, 32, ZERO, ZERO, 400, 1).isEmpty(), "zero camera forward fails closed");
        check(build(new Vec(Double.MAX_VALUE, 0, 1), X, 32,
                new Vec(-Double.MAX_VALUE, 0, 0), Z, 400, 1).isEmpty(), "overflowing camera subtraction fails closed");
    }

    private static void verify(List<Quad> geometry, Vec worldStart, Vec suppliedAxis,
                               double length, Vec camera, Vec suppliedForward) {
        check(geometry.size() <= 12 && geometry.size() * 4 <= 48, "complete geometry stays within allocation ceiling");
        check(count(geometry, Layer.ENVELOPE) == 0 || count(geometry, Layer.ENVELOPE) == 4,
                "envelope is omitted or emitted whole");
        check(count(geometry, Layer.TRAIL) == 0 || count(geometry, Layer.TRAIL) == 8,
                "trails are omitted or emitted as two whole ribbons");
        Vec start = worldStart.subtract(camera), axis = unit(suppliedAxis), forward = unit(suppliedForward);
        for (Layer layer : Layer.values()) {
            List<Quad> ribbons = layer(geometry, layer);
            int segments = 4;
            for (int base = 0; base < ribbons.size(); base += segments) {
                List<Quad> ribbon = ribbons.subList(base, base + segments);
                double localSpan = layer == Layer.ENVELOPE ? 12 : base == 0 ? 18 : 14.4;
                double widthBound = base == 0 ? .10 : .085;
                double centerBound = base == 0 ? 1.10 : .858;
                double minimumDepth = ribbon.stream().flatMap(q -> vertices(q).stream())
                        .mapToDouble(v -> start.add(axis.scale(v.u() * length)).dot(forward)).min().orElseThrow();
                for (Quad quad : ribbon) {
                    for (Vertex vertex : vertices(quad)) {
                        check(radial(vertex, start, axis, length) <= .20 * minimumDepth + 1e-8,
                                "the entire component obeys its minimum positive forward-depth radius cap");
                        check(vertex.u() * length <= Math.min(length, localSpan) + 1e-8,
                                "each complete component respects its own asymmetric local span");
                    }
                    if (layer == Layer.TRAIL) {
                        check(width(quad.a(), quad.d()) <= widthBound + 1e-9
                                && width(quad.b(), quad.c()) <= widthBound + 1e-9,
                                "each asymmetric trail respects its own half-width cap after clipping");
                        for (Vertex[] pair : List.of(new Vertex[] {quad.a(), quad.d()},
                                new Vertex[] {quad.b(), quad.c()})) {
                            Vec center = midpoint(pair[0], pair[1]);
                            Vec onAxis = start.add(axis.scale(pair[0].u() * length));
                            check(center.subtract(onAxis).length() <= centerBound + 1e-8,
                                    "each asymmetric trail respects its own center-offset cap after clipping");
                        }
                    }
                }
            }
        }
        for (Quad quad : geometry) {
            check(quad.a().v() == 0 && quad.b().v() == 0 && quad.c().v() == 1 && quad.d().v() == 1,
                    "each quad retains the complete transverse soft profile");
            check(quad.a().u() < quad.b().u(), "stations remain ordered after clipping");
            check(close(quad.a().alpha(), quad.d().alpha()) && close(quad.b().alpha(), quad.c().alpha()),
                    "vertex alpha does not apply the fragment transverse falloff twice");
            if (quad.layer() == Layer.TRAIL) {
                check(width(quad.a(), quad.d()) <= .10 + 1e-9 && width(quad.b(), quad.c()) <= .10 + 1e-9,
                        "each actual trail cross-section obeys its half-width bound");
            }
            for (Vertex vertex : vertices(quad)) {
                Vec point = vertex.position();
                check(point.finite() && Float.isFinite((float) point.x())
                        && Float.isFinite((float) point.y()) && Float.isFinite((float) point.z()),
                        "all actual renderer corners are finite after camera-relative conversion");
                check(point.dot(forward) > .15, "every actual corner is strictly beyond the near plane");
                check(vertex.u() >= 0 && vertex.u() <= 1 && Double.isFinite(vertex.u()),
                        "longitudinal UV remains inside the accepted interval");
                check(Double.isFinite(vertex.alpha()) && vertex.alpha() >= 0 && vertex.alpha() <= quad.layer().baseAlpha() + 1e-12,
                        "alpha remains finite and within its profile bound");
                double distance = vertex.u() * length;
                Vec onAxis = start.add(axis.scale(distance));
                double radial = point.subtract(onAxis).length();
                check(radial <= 1.6 + 1e-8, "every corner obeys the absolute decorative radius");
                check(radial <= .20 * onAxis.dot(forward) + 1e-8,
                        "every actual corner includes width and offset in its forward-depth radius cap");
                double componentLength = quad.layer() == Layer.ENVELOPE ? 12 : 18;
                check(distance >= 0 && distance <= Math.min(length, componentLength) + 1e-8,
                        "decorative stations stay inside their local accepted axis interval");
                check(Math.abs(point.subtract(start).dot(axis) - distance) <= 1e-8 * Math.max(1, componentLength),
                        "radial offsets never extend the accepted longitudinal axis");
            }
        }
    }

    private static List<Quad> build(Vec start, Vec axis, double length, Vec camera, Vec forward, double age, double width) {
        return RailgunLaunchGeometry.buildAlong(start, axis, length, camera, forward, age, width);
    }
    private static List<Quad> transverse(double age, double width, double length) {
        return build(new Vec(0, 0, 10), X, length, ZERO, Z, age, width);
    }
    private static List<Quad> layer(List<Quad> geometry, Layer layer) {
        return geometry.stream().filter(q -> q.layer() == layer).toList();
    }
    private static long count(List<Quad> geometry, Layer layer) { return geometry.stream().filter(q -> q.layer() == layer).count(); }
    private static List<Vertex> vertices(Quad quad) { return List.of(quad.a(), quad.b(), quad.c(), quad.d()); }
    private static Vec midpoint(Vertex a, Vertex b) { return a.position().add(b.position()).scale(.5); }
    private static double width(Vertex a, Vertex b) { return a.position().subtract(b.position()).length() * .5; }
    private static double firstWidth(List<Quad> quads) { return width(quads.getFirst().a(), quads.getFirst().d()); }
    private static double lastWidth(List<Quad> quads) { return width(quads.getLast().b(), quads.getLast().c()); }
    private static double radial(Vertex vertex, Vec start, Vec axis, double length) {
        return vertex.position().subtract(start.add(axis.scale(vertex.u() * length))).length();
    }
    private static double maxRadius(List<Quad> geometry, Layer layer, Vec start, Vec axis, double length) {
        return layer(geometry, layer).stream().flatMap(q -> vertices(q).stream())
                .mapToDouble(v -> radial(v, start, axis, length)).max().orElse(0);
    }
    private static double maxAlpha(List<Quad> geometry, Layer layer) {
        return layer(geometry, layer).stream().flatMap(q -> vertices(q).stream()).mapToDouble(Vertex::alpha).max().orElse(0);
    }
    private static Vec unit(Vec value) {
        double largest = Math.max(Math.abs(value.x()), Math.max(Math.abs(value.y()), Math.abs(value.z())));
        Vec scaled = new Vec(value.x() / largest, value.y() / largest, value.z() / largest);
        return scaled.scale(1 / scaled.length());
    }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-9; }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
