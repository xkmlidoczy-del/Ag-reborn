package cn.academy.port.client;

import cn.academy.port.highpower.core.ShearWindbladeGeometry;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
import cn.academy.port.highpower.core.ShearWindbladeRules;
import java.util.List;

/** Standalone CPU checks of the exact staged fixture factory; no game launch or rendering required. */
public final class ShearWindbladeMaterialPreviewRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= 1e-9, message + ": " + actual + " != " + expected);
    }
    private static void configuration() {
        for (String bad : List.of("NaN", "Infinity", "-Infinity", "", "eight", "0", "7.99", "9", "16.01", "1e100"))
            check(ShearWindbladeMaterialPreview.parseSettings("front", "horizontal", bad) == null, "invalid distance fails closed: " + bad);
        check(ShearWindbladeMaterialPreview.parseSettings("front", "horizontal", null) == null, "missing distance fails closed");
        check(ShearWindbladeMaterialPreview.parseSettings("oblique", "horizontal", "8") == null, "unsupported angle fails closed");
        check(ShearWindbladeMaterialPreview.parseSettings("front", "diagonal", "8") == null, "unsupported plane fails closed");
        check(ShearWindbladeMaterialPreview.parseSettings(null, "horizontal", "8") == null, "missing angle fails closed");
        check(ShearWindbladeMaterialPreview.parseSettings("front", null, "8") == null, "missing plane fails closed");
    }
    private static void fixture(String angle, String mode, double distance, Point camera, Point look) {
        var settings = ShearWindbladeMaterialPreview.parseSettings(angle, mode, Double.toString(distance));
        var controller = ShearWindbladeMaterialPreview.fixture(camera, look, .73, settings);
        check(controller != null, "finite fixture accepted by production State: " + angle + "/" + mode);
        check(!controller.local() && controller.age() == 0 && controller.receiptAge() == 0,
                "isolated controller has no local owner or advancing receipt phase");
        check(controller.impacts().isEmpty() && controller.fronts().size() == 2, "exactly two fronts and no impact certificates");
        check(controller.crouch() == mode.equals("vertical"), "plane matches authoritative crouch convention");
        var cameraBasis = ShearWindbladeGeometry.frame(camera, look, .73);
        Point travel = angle.equals("side") ? cameraBasis.horizontal() : cameraBasis.forward();
        var basis = ShearWindbladeGeometry.frame(controller.origin(), travel, .73);
        for (int blade = 0; blade < 2; blade++) {
            var front = controller.fronts().get(blade);
            check(front.index() == blade && front.status() == ShearWindbladeState.TRAVELLING, "stable production blade identity/status");
            double previous = (4 - blade * ShearWindbladeRules.SECOND_DELAY) * ShearWindbladeRules.STEP;
            check(front.previous().equals(ShearWindbladeGeometry.front(basis, mode.equals("vertical") ? 1 : 0, previous)),
                    "previous receipt is the production parabola with the actual plane and delay");
            check(front.current().equals(ShearWindbladeGeometry.front(basis, mode.equals("vertical") ? 1 : 0, previous + ShearWindbladeRules.STEP)),
                    "current receipt uses one authoritative two-meter step");
        }
        var first = controller.fronts().get(0).previous();
        var second = controller.fronts().get(1).previous();
        double spacing = ShearWindbladeRules.STEP * ShearWindbladeRules.SECOND_DELAY;
        for (int knot = 0; knot <= ShearWindbladeGeometry.CHORDS; knot++)
            near(Math.sqrt(first.get(knot).distanceSquared(second.get(knot))), spacing, "every corresponding knot has real two-tick spacing");
        Point a = first.get(ShearWindbladeGeometry.CHORDS / 2), b = second.get(ShearWindbladeGeometry.CHORDS / 2);
        Point midpoint = new Point((a.x() + b.x()) / 2, (a.y() + b.y()) / 2, (a.z() + b.z()) / 2);
        Point expectedCenter = new Point(camera.x() + cameraBasis.forward().x() * distance,
                camera.y() + cameraBasis.forward().y() * distance, camera.z() + cameraBasis.forward().z() * distance);
        near(midpoint.distanceSquared(expectedCenter), 0, "pair apex midpoint uses the labeled camera distance");
        var shape = ShearWindbladeRenderGeometry.shape(controller, ShearWindbladeMaterialPreview.PARTIAL);
        check(shape.strokes().size() == 16, "same production renderer receives two complete eight-chord fronts");
        for (var stroke : shape.strokes()) {
            var points = controller.fronts().get(stroke.blade()).previous();
            check(stroke.from().equals(points.get(stroke.chord())) && stroke.to().equals(points.get(stroke.chord() + 1))
                    && stroke.alpha() == 238, "fixed age zero/partial half renders previous points at exact production alpha");
        }
        check(controller.equals(ShearWindbladeMaterialPreview.fixture(camera, look, .73, settings)),
                "repeated factory calls have no clock/random advancement");
    }
    public static void main(String[] args) {
        configuration();
        for (String angle : List.of("front", "side")) for (String mode : List.of("horizontal", "vertical"))
            for (double distance : List.of(8.0, 16.0))
                for (Point look : List.of(new Point(0, 0, 1), new Point(.2, -.1, 1), new Point(0, 1, 0)))
                    fixture(angle, mode, distance, new Point(.125, 64.25, -.5), look);
        var settings = ShearWindbladeMaterialPreview.parseSettings("front", "horizontal", "8");
        check(ShearWindbladeMaterialPreview.fixture(new Point(Double.NaN, 0, 0), new Point(0, 0, 1), 0, settings) == null,
                "nonfinite camera fails closed");
        check(ShearWindbladeMaterialPreview.fixture(new Point(0, 0, 0), new Point(0, 0, 0), 0, settings) == null,
                "degenerate camera direction fails closed");
        check(ShearWindbladeMaterialPreview.fixture(new Point(0, 0, 0), new Point(0, 0, 1), Double.NaN, settings) == null,
                "nonfinite yaw fails closed");
        check(ShearWindbladeMaterialPreview.fixture(new Point(29_999_999, 0, 0), new Point(1, 0, 0), 0, settings) == null,
                "fixture outside the supported world boundary fails closed");
        check(ShearWindbladeMaterialPreview.fixture(new Point(0, 0, 0), new Point(0, 0, 1), 0,
                new ShearWindbladeMaterialPreview.Settings("front", "horizontal", Double.NaN)) == null, "unparsed invalid settings fail closed");
        check(!ShearWindbladeMaterialPreview.allowed(), "default launch configuration cannot display a staged fixture");
        System.out.println("ShearWindbladeMaterialPreviewRegressionTest: " + checks + " checks passed (CPU fixture only; native visuals untested)");
    }
}
