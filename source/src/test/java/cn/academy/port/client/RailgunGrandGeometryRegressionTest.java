package cn.academy.port.client;

import cn.academy.port.client.vfx2.RailgunGrandGeometry;
import cn.academy.port.client.vfx2.RailgunGrandGeometry.Layer;
import cn.academy.port.client.vfx2.RailgunGrandGeometry.Quad;
import cn.academy.port.client.vfx2.RailgunOverlayGeometry.Vec;
import cn.academy.port.client.vfx2.RailgunOverlayGeometry.Vertex;
import java.util.List;
import java.util.Random;

/** Standalone regression checks for the exact pure helper used by the optional amber shoulders. */
public final class RailgunGrandGeometryRegressionTest {
    private static final Vec ZERO = new Vec(0, 0, 0);
    private static final Vec X = new Vec(1, 0, 0);
    private static final Vec Y = new Vec(0, 1, 0);
    private static final Vec Z = new Vec(0, 0, 1);
    private static int checks;

    public static void main(String[] args) {
        metadataAndWidths();
        physicalTaperAndTexture();
        classicLifetimeAndPause();
        nearPlaneAndActualCameraDirection();
        variedViewsAndStableFallback();
        largeCoordinateTranslation();
        invalidInputs();
        boundedAndImmutable();
        System.out.println("PASS " + checks + " Railgun grand shoulder geometry checks");
    }

    private static void metadataAndWidths() {
        check(Layer.INNER.red() == 250 && Layer.INNER.green() == 201 && Layer.INNER.blue() == 132,
                "inner amber color is explicit");
        check(Layer.OUTER.red() == 236 && Layer.OUTER.green() == 170 && Layer.OUTER.blue() == 93,
                "outer amber color is explicit");
        check(close(Layer.INNER.baseAlpha(), .42) && close(Layer.OUTER.baseAlpha(), .18),
                "base alpha belongs to each shoulder");
        check(close(Layer.INNER.radiusScale(), 2) && close(Layer.OUTER.radiusScale(), 3),
                "shoulders use two and three times the existing .13 outer-core radius");
        for (double age : new double[] {0, 75, 150, 225, 300, 500, 2499}) {
            List<Quad> quads = transverseBeam(age, 1);
            double progress = Math.min(1, age / 300);
            double swell = 1 + .20 * (1 - progress * progress * (3 - 2 * progress));
            for (Quad quad : quads) {
                double expected = .13 * quad.layer().radiusScale() * swell;
                check(close(halfWidth(quad), expected), "brief swell only affects shoulder width");
                check(halfWidth(quad) <= .13 * quad.layer().radiusScale() * 1.20 + 1e-12,
                        "swell never exceeds twenty percent");
            }
        }
        for (Quad quad : transverseBeam(0, 1e300))
            check(close(halfWidth(quad), .6), "absolute shoulder half-width is bounded even for huge scale");
        check(close(halfWidth(findLayer(transverseBeam(300, .5), Layer.OUTER)), .195),
                "caller width scale is retained");
    }

    private static void physicalTaperAndTexture() {
        Vec start = new Vec(0, 2, 10);
        List<Quad> quads = build(start, X, 10, ZERO, Z, 500, 1);
        check(!quads.isEmpty(), "ordinary visible interval has shoulders");
        verifyGeometry(quads, start, X, 10, ZERO, Z);
        for (Quad quad : quads) for (Vertex vertex : vertices(quad)) {
            double distance = vertex.u() * 10;
            double expected = quad.layer().baseAlpha() * smooth(distance / .35) * smooth((10 - distance) / .7);
            check(close(vertex.alpha(), expected), "vertex alpha applies physical taper once");
        }
        for (Layer layer : Layer.values()) {
            check(close(alphaAt(quads, layer, 0), 0), "physical start alpha is zero");
            check(close(alphaAt(quads, layer, .175 / 10), layer.baseAlpha() * .5),
                    "start taper is sampled at its half-distance");
            check(close(alphaAt(quads, layer, .35 / 10), layer.baseAlpha()),
                    "start taper reaches full strength within .35 metres");
            check(close(alphaAt(quads, layer, 9.3 / 10), layer.baseAlpha()),
                    "end taper begins .7 metres before the physical endpoint");
            check(close(alphaAt(quads, layer, 9.65 / 10), layer.baseAlpha() * .5),
                    "end taper is sampled at its half-distance");
            check(close(alphaAt(quads, layer, 1), 0), "physical endpoint alpha is zero");
        }
        // Overlapping tapers on a very short classic visible interval remain within that interval.
        List<Quad> shortBeam = build(start, X, .08, ZERO, Z, 500, 1);
        verifyGeometry(shortBeam, start, X, .08, ZERO, Z);
        check(!shortBeam.isEmpty(), "short finite visible intervals are not extended or replaced");
        check(shortBeam.stream().flatMap(q -> vertices(q).stream()).anyMatch(v -> v.alpha() > 0),
                "very short visible intervals retain a nonzero sampled taper interior");
    }

    private static void classicLifetimeAndPause() {
        check(!transverseBeam(0, 1).isEmpty(), "age zero uses the existing live clock");
        check(RailgunGrandGeometry.LIFETIME_MILLIS == 2500, "classic 2500 ms lifecycle is exact");
        for (double age : new double[] {300, 1499, 1500, 1750, 2000, 2499, 2499.999}) {
            List<Quad> quads = transverseBeam(age, 1);
            double expectedFade = Math.min(1, (2500 - age) / 1000);
            for (Layer layer : Layer.values())
                check(close(alphaAt(quads, layer, .35 / 10), layer.baseAlpha() * expectedFade),
                        "only the original final-second time fade applies");
            check(quads.equals(transverseBeam(age, 1)), "paused clock produces identical immutable geometry");
        }
        check(transverseBeam(2500, 1).isEmpty(), "no geometry survives the exact classic end age");
        check(transverseBeam(2501, 1).isEmpty(), "no tail is created after the classic lifecycle");
    }

    private static void nearPlaneAndActualCameraDirection() {
        double safeDepth = .15 + .39;
        Vec start = new Vec(2, 1, -4);
        List<Quad> forward = build(start, Z, 10, ZERO, Z, 500, 1);
        verifyGeometry(forward, start, Z, 10, ZERO, Z);
        check(!forward.isEmpty(), "near-plane crossing retains its safe original subinterval");
        check(minU(forward) >= (safeDepth + 4) / 10, "clipped u remains tied to original length");
        check(close(maxU(forward), 1), "unclipped physical endpoint is unchanged");
        for (Layer layer : Layer.values())
            check(alphaAt(forward, layer, minU(forward)) < 1e-10,
                    "newly admitted near clip starts transparent");
        double nearFullU = (safeDepth + .3 + 4 + 1e-9) / 10;
        check(close(alphaAt(forward, Layer.INNER, nearFullU), Layer.INNER.baseAlpha()),
                "near fade reaches full strength across .3 metres of camera depth");

        Vec reversedStart = new Vec(2, 1, 6);
        Vec reversedAxis = Z.scale(-1);
        List<Quad> reversed = build(reversedStart, reversedAxis, 10, ZERO, Z, 500, 1);
        verifyGeometry(reversed, reversedStart, reversedAxis, 10, ZERO, Z);
        check(close(minU(reversed), 0) && maxU(reversed) <= (6 - safeDepth) / 10,
                "reverse crossing clips the far original interval without reversal or extension");
        for (Layer layer : Layer.values())
            check(alphaAt(reversed, layer, maxU(reversed)) < 1e-10, "reverse near clip also fades to zero");

        Vec obliqueStart = new Vec(0, 2, -2);
        Vec obliqueAxis = new Vec(1, 0, 1);
        verifyGeometry(build(obliqueStart, obliqueAxis, 12, ZERO, Z, 0, 1),
                obliqueStart, obliqueAxis, 12, ZERO, Z);
        check(build(new Vec(30, 0, -1), X, 10, ZERO, Z, 500, 1).isEmpty(),
                "a distant beam behind actual camera forward is suppressed");
        check(build(new Vec(0, 2, 10), X, 10, ZERO, Z.scale(-1), 500, 1).isEmpty(),
                "camera distance is not substituted for actual forward depth");
        check(build(new Vec(0, 2, .53), X, 10, ZERO, Z, 500, 1).isEmpty(),
                "center in front of near plane but unsafe for full halo is clipped away");

        List<Quad> halfFade = build(new Vec(0, 2, safeDepth + .15 + 1e-9), X, 10, ZERO, Z, 500, 1);
        check(close(alphaAt(halfFade, Layer.INNER, .35 / 10), Layer.INNER.baseAlpha() * .5),
                "parallel-depth center uses the same smooth near safety fade");
    }

    private static void variedViewsAndStableFallback() {
        for (Vec axis : List.of(X, Y, Z, X.scale(-1), Y.scale(-1), Z.scale(-1))) {
            Vec start = axis.scale(2);
            List<Quad> parallel = build(start, axis, 8, ZERO, axis, 500, 1);
            check(!parallel.isEmpty(), "axis-aligned camera fallback always produces finite visible geometry");
            verifyGeometry(parallel, start, axis, 8, ZERO, axis);
            check(parallel.equals(build(start, axis, 8, ZERO, axis, 500, 1)),
                    "parallel fallback is deterministic");
        }
        Vec nearlyParallel = new Vec(1e-12, -1e-12, 1);
        verifyGeometry(build(Z.scale(2), nearlyParallel, 8, ZERO, Z, 500, 1),
                Z.scale(2), nearlyParallel, 8, ZERO, Z);

        Random random = new Random(0x5241494c47554eL);
        for (int i = 0; i < 1200; i++) {
            double yaw = random.nextDouble() * Math.PI * 2;
            double pitch = (random.nextDouble() - .5) * Math.PI;
            Vec forward = new Vec(Math.cos(pitch) * Math.sin(yaw), Math.sin(pitch), Math.cos(pitch) * Math.cos(yaw));
            Vec axis = unit(new Vec(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1));
            Vec camera = new Vec(i % 3, i % 5, i % 7);
            Vec start = camera.add(forward.scale(12)).add(new Vec(random.nextDouble() - .5, random.nextDouble() - .5, random.nextDouble() - .5));
            double length = .01 + random.nextDouble() * 8;
            double age = random.nextDouble() * 2500;
            List<Quad> quads = build(start, axis, length, camera, forward, age, .1 + random.nextDouble() * 20);
            check(!quads.isEmpty(), "varied yaw, pitch and beam axes retain a visible original interval");
            verifyGeometry(quads, start, axis, length, camera, forward);
        }
    }

    private static void largeCoordinateTranslation() {
        Vec start = new Vec(3, .125, 8), axis = new Vec(.2, .35, 1);
        List<Quad> original = build(start, axis, 32, ZERO, Z, 150, .75);
        for (Vec translation : List.of(new Vec(30_000_000, -30_000_000, 30_000_000),
                new Vec(1e12, -1e12, 1e12), new Vec(-1e12, 1e12, -1e12))) {
            List<Quad> translated = build(start.add(translation), axis, 32, translation, Z, 150, .75);
            check(original.equals(translated), "world-camera subtraction preserves exact thin relative geometry before interpolation");
            verifyGeometry(translated, start.add(translation), axis, 32, translation, Z);
        }
        check(!build(start, new Vec(1e308, 1e308, 1e308), 10, ZERO, Z, 500, 1).isEmpty(),
                "finite huge direction normalizes without norm overflow");
        check(!build(start, new Vec(Double.MIN_VALUE, 0, 0), 10, ZERO,
                new Vec(0, 0, Double.MIN_VALUE), 500, 1).isEmpty(),
                "nonzero microscopic direction and forward are safely normalized");
    }

    private static void invalidInputs() {
        Vec start = new Vec(0, 2, 10);
        for (double length : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(build(start, X, length, ZERO, Z, 500, 1).isEmpty(), "invalid visible length fails closed");
        for (double age : new double[] {-1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(build(start, X, 10, ZERO, Z, age, 1).isEmpty(), "invalid caller age fails closed");
        for (double width : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            check(build(start, X, 10, ZERO, Z, 500, width).isEmpty(), "invalid caller width fails closed");
        for (Vec bad : new Vec[] {null, new Vec(Double.NaN, 0, 0), new Vec(0, Double.POSITIVE_INFINITY, 0),
                new Vec(0, 0, Double.NEGATIVE_INFINITY)}) {
            check(build(bad, X, 10, ZERO, Z, 500, 1).isEmpty(), "invalid world origin fails closed");
            check(build(start, X, 10, bad, Z, 500, 1).isEmpty(), "invalid world camera fails closed");
            check(build(start, bad, 10, ZERO, Z, 500, 1).isEmpty(), "invalid beam direction fails closed");
            check(build(start, X, 10, ZERO, bad, 500, 1).isEmpty(), "invalid camera forward fails closed");
        }
        check(build(start, ZERO, 10, ZERO, Z, 500, 1).isEmpty(), "zero beam direction rejected");
        check(build(start, X, 10, ZERO, ZERO, 500, 1).isEmpty(), "zero camera forward rejected");
        check(build(new Vec(Double.MAX_VALUE, 0, 1), X, 10,
                new Vec(-Double.MAX_VALUE, 0, 0), Z, 500, 1).isEmpty(),
                "nonfinite camera-relative subtraction rejected");
    }

    private static void boundedAndImmutable() {
        check(RailgunGrandGeometry.MAX_QUADS == 12 && RailgunGrandGeometry.MAX_VERTICES == 48,
                "allocation ceiling is explicit for the whole two-layer beam");
        List<Quad> maximum = build(new Vec(0, 2, .65), Z, 10, ZERO, Z, 500, 1);
        check(maximum.size() == 12, "taper samples plus near-fade knee meet the exact maximum budget");
        for (double length : new double[] {.001, .1, .35, .7, 1, 32, 512, 1e100})
            verifyGeometry(build(new Vec(0, 2, 10), X, length, ZERO, Z, 500, 1),
                    new Vec(0, 2, 10), X, length, ZERO, Z);
        List<Quad> immutable = transverseBeam(500, 1);
        boolean rejected = false;
        try { immutable.add(immutable.getFirst()); } catch (UnsupportedOperationException expected) { rejected = true; }
        check(rejected, "returned geometry list is immutable");
    }

    private static void verifyGeometry(List<Quad> quads, Vec worldStart, Vec suppliedAxis,
                                       double length, Vec camera, Vec suppliedForward) {
        check(quads.size() <= 12 && quads.size() * 4 <= 48, "whole beam stays within its fixed allocation budget");
        Vec start = worldStart.subtract(camera), axis = unit(suppliedAxis), forward = unit(suppliedForward);
        for (Quad quad : quads) {
            check(quad.layer() != null, "every quad owns an explicit shoulder layer");
            check(close(quad.a().v(), 0) && close(quad.b().v(), 0)
                    && close(quad.c().v(), 1) && close(quad.d().v(), 1), "full transverse texture spans each ribbon once");
            check(close(quad.a().alpha(), quad.d().alpha()) && close(quad.b().alpha(), quad.c().alpha()),
                    "vertex alpha does not duplicate fragment cross-width falloff");
            check(halfWidth(quad) <= .6 + 1e-9, "all shoulder corners obey absolute half-width cap");
            check(quad.a().u() < quad.b().u(), "texture u is ordered along original interval");
            for (Vertex vertex : vertices(quad)) {
                Vec point = vertex.position();
                check(point.finite(), "all output positions remain finite doubles");
                check(point.dot(forward) >= .15, "every actual quad corner stays ahead of near plane");
                check(Double.isFinite(vertex.alpha()) && vertex.alpha() >= 0 && vertex.alpha() <= quad.layer().baseAlpha() + 1e-12,
                        "alpha remains finite and bounded by original layer strength");
                check(vertex.u() >= 0 && vertex.u() <= 1, "u remains inside the original full-length texture interval");
                double projection = point.subtract(start).dot(axis);
                double tolerance = 1e-8 * Math.max(1, length);
                check(projection >= -tolerance && projection <= length + tolerance,
                        "side offsets and clipping never extend the original longitudinal interval");
                check(Math.abs(projection - vertex.u() * length) <= tolerance,
                        "u measures original physical longitudinal distance after clipping");
            }
            Vec center0 = quad.a().position().add(quad.d().position()).scale(.5);
            Vec center1 = quad.b().position().add(quad.c().position()).scale(.5);
            double width = Math.max(halfWidth(quad), maxLayerWidth(quads));
            check(center0.dot(forward) + 1e-8 >= .15 + width && center1.dot(forward) + 1e-8 >= .15 + width,
                    "center clipping conservatively protects the maximum shoulder half-width");
        }
    }

    private static List<Quad> build(Vec start, Vec axis, double length, Vec camera, Vec forward, double age, double width) {
        return RailgunGrandGeometry.buildAlong(start, axis, length, camera, forward, age, width);
    }

    private static List<Quad> transverseBeam(double age, double width) {
        return build(new Vec(0, 2, 10), X, 10, ZERO, Z, age, width);
    }

    private static List<Vertex> vertices(Quad quad) { return List.of(quad.a(), quad.b(), quad.c(), quad.d()); }
    private static double halfWidth(Quad quad) { return quad.a().position().subtract(quad.d().position()).length() * .5; }
    private static double maxLayerWidth(List<Quad> quads) { return quads.stream().mapToDouble(RailgunGrandGeometryRegressionTest::halfWidth).max().orElse(0); }
    private static double minU(List<Quad> quads) { return quads.stream().mapToDouble(q -> q.a().u()).min().orElseThrow(); }
    private static double maxU(List<Quad> quads) { return quads.stream().mapToDouble(q -> q.b().u()).max().orElseThrow(); }
    private static Quad findLayer(List<Quad> quads, Layer layer) { return quads.stream().filter(q -> q.layer() == layer).findFirst().orElseThrow(); }

    private static double alphaAt(List<Quad> quads, Layer layer, double u) {
        for (Quad quad : quads) if (quad.layer() == layer) for (Vertex vertex : vertices(quad))
            if (close(vertex.u(), u)) return vertex.alpha();
        throw new AssertionError("Missing station for " + layer + " at u=" + u);
    }

    private static Vec unit(Vec value) {
        double largest = Math.max(Math.abs(value.x()), Math.max(Math.abs(value.y()), Math.abs(value.z())));
        Vec scaled = new Vec(value.x() / largest, value.y() / largest, value.z() / largest);
        return scaled.scale(1 / scaled.length());
    }

    private static double smooth(double value) { double t = Math.max(0, Math.min(1, value)); return t * t * (3 - 2 * t); }
    private static boolean close(double a, double b) { return Math.abs(a - b) < 1e-9; }
    private static void check(boolean value, String reason) { checks++; if (!value) throw new AssertionError(reason); }
}
