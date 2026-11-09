package cn.academy.port.highpower.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static cn.academy.port.highpower.core.FoldedMirrorGeometry.*;

/** Standalone Java regression coverage; no Minecraft runtime or Gradle is required. */
public final class FoldedMirrorGeometryRegressionTest {
    private static int checks;

    public static void main(String[] args) {
        fixedReflectionAndRealContact();
        finiteFaceAndBeamFootprint();
        orderedContactsAndExactProgress();
        twoMirrorArclengthAndRevisit();
        orientationsAndPlacementVolume();
        randomizedReflectionConservation();
        malformedInput();
        System.out.println("Folded mirror geometry: " + checks + " checks passed");
    }

    private static void fixedReflectionAndRealContact() {
        Vector incoming = v(0, 0, 1), outgoing = v(1, 0, 0);
        Vector normal = normalFor(incoming, outgoing).orElseThrow();
        near(normal, v(-Math.sqrt(.5), 0, Math.sqrt(.5)), 1e-14, "normal uses incoming minus outgoing");
        Plate plate = new Plate(0, v(0, 0, 5), normal);
        Contact hit = contact(plate, v(.1, .1, 0), incoming, 40).orElseThrow();
        check(hit.reflects(), "off-center front-face reflection");
        near(hit.point(), v(.1, .1, 5.1), 1e-13, "actual plane contact is not teleported to center");
        near(hit.distance(), 5.1, 1e-13, "distance reaches actual tilted plane");
        near(hit.reflectedDirection(), outgoing, 1e-14, "fixed geometric reflection");
        near(hit.point().subtract(plate.center()).dot(normal), 0, 1e-13, "contact lies on plane");

        Vector differentIncoming = unit(v(.1, 0, 1)).orElseThrow();
        Contact different = contact(plate, plate.center().subtract(differentIncoming.scale(3)), differentIncoming, 40).orElseThrow();
        near(different.reflectedDirection(), unit(v(1, 0, .1)).orElseThrow(), 1e-13,
                "new aim obeys frozen plane instead of remembered outgoing aim");
        check(different.reflectedDirection().subtract(outgoing).length() > .09, "mirror is not a homing waypoint");
        check(plate.normal().equals(normal), "shooting never changes stored normal");
        check(normalFor(incoming, incoming).isEmpty(), "straight-through edit has no invented normal");
        check(normalFor(incoming, unit(v(.001, 0, 1)).orElseThrow()).isEmpty(), "grazing edit rejected");
        near(normalFor(incoming, incoming.scale(-1)).orElseThrow(), incoming, 1e-14, "retroreflection is well defined");

        Contact rear = contact(plate, v(0, 0, 10), incoming.scale(-1), 40).orElseThrow();
        check(rear.kind() == ContactKind.BACK_FACE && rear.reflectedDirection() == null,
                "back face remains a rejected physical contact, never a reflection");
        check(contact(plate, v(0, 0, 1), v(0, 1, 0), 40).isEmpty(), "parallel ray has no plane crossing");
    }

    private static void finiteFaceAndBeamFootprint() {
        Plate plate = new Plate(0, v(0, 0, 5), v(0, 0, 1));
        Vector direction = v(0, 0, 1);
        for (Vector local : List.of(v(RADIUS_X, 0, 0), v(-RADIUS_X, 0, 0),
                v(0, RADIUS_Y, 0), v(0, -RADIUS_Y, 0))) {
            Contact edge = contact(plate, local, direction, 40).orElseThrow();
            check(edge.kind() == ContactKind.EDGE && edge.reflectedDirection() == null,
                    "exact visible ellipse edge cannot fit finite beam");
        }
        check(contact(plate, v(RADIUS_X + 1e-9, 0, 0), direction, 40).isEmpty(), "outside horizontal edge");
        check(contact(plate, v(0, RADIUS_Y + 1e-9, 0), direction, 40).isEmpty(), "outside vertical edge");
        check(contact(plate, v(.40, .60, 0), direction, 40).isEmpty(), "rectangle corner has no elliptical mirror");
        check(contact(plate, v(.414, 0, 0), direction, 40).orElseThrow().reflects(), "beam just inside safe rim reflects");
        check(contact(plate, v(.416, 0, 0), direction, 40).orElseThrow().kind() == ContactKind.EDGE,
                "beam center inside visible face still rejects when footprint clips rim");

        Vector oblique = v(Math.sqrt(1 - .2 * .2), 0, .2);
        check(contact(plate, plate.center().subtract(oblique.scale(5)), oblique, 40).orElseThrow().reflects(),
                "centered non-grazing footprint fits");
        Vector offCenter = plate.center().add(v(.30, 0, 0));
        check(contact(plate, offCenter.subtract(oblique.scale(5)), oblique, 40).orElseThrow().kind() == ContactKind.EDGE,
                "oblique footprint expands beyond radius and rejects edge");
        Vector grazing = v(Math.sqrt(1 - .01 * .01), 0, .01);
        check(contact(plate, plate.center().subtract(grazing.scale(5)), grazing, 40).orElseThrow().kind() == ContactKind.GRAZING,
                "grazing incidence cannot fit full beam even at center");

        for (int ix = -50; ix <= 50; ix++) for (int iy = -70; iy <= 70; iy++) {
            Vector start = v(ix * .01, iy * .01, 0);
            var actual = contact(plate, start, direction, 40);
            double rho = Math.hypot(start.x() / RADIUS_X, start.y() / RADIUS_Y);
            check(actual.isPresent() == (rho <= 1), "entire finite ellipse and outside-corner grid");
            if (actual.isPresent()) {
                check(actual.get().reflects() == (rho + BEAM_RADIUS / RADIUS_X < 1),
                        "conservative eroded ellipse is distinct from visible perimeter");
                if (actual.get().reflects()) checkFootprintInside(plate, actual.get(), direction, 12);
            }
        }

        // At every permitted incidence, independently construct the actual projected cylinder rim.
        for (int incidenceStep = 8; incidenceStep <= 100; incidenceStep++) {
            double cosine = incidenceStep / 100.0;
            Vector ray = v(Math.sqrt(1 - cosine * cosine), 0, cosine);
            for (int angle = 0; angle < 360; angle += 15) {
                double a = Math.toRadians(angle), allowance = 1 - BEAM_RADIUS / cosine / RADIUS_X;
                Vector local = v(RADIUS_X * allowance * .999 * Math.cos(a),
                        RADIUS_Y * allowance * .999 * Math.sin(a), 0);
                Contact hit = contact(plate, plate.center().add(local).subtract(ray.scale(3)), ray, 40).orElseThrow();
                check(hit.reflects(), "safe inset works at every supported incidence and azimuth");
                checkFootprintInside(plate, hit, ray, 48);
            }
        }
    }

    private static void orderedContactsAndExactProgress() {
        Plate near = new Plate(0, v(0, 0, 5), v(0, 0, 1));
        Plate far = new Plate(1, v(0, 0, 6), v(0, 0, 1));
        Vector origin = v(0, 0, 0), direction = v(0, 0, 1);
        Contact nearest = nearestContact(List.of(far, near), origin, direction, 40, -1).orElseThrow();
        check(nearest.plateIndex() == 0 && nearest.distance() == 5, "nearest plane wins independent of list ordering");
        check(contact(near, origin, direction, Math.nextDown(5.0)).isEmpty(), "obstruction even one representable step before plane wins");
        check(contact(near, origin, direction, 5).orElseThrow().distance() == 5, "exact endpoint is not biased by epsilon");
        check(nearest.distance() >= 5.0, "native solid or body exactly tied at five metres wins with <=");
        check(nearest.distance() < Math.nextUp(5.0), "strictly later obstruction does not become a fabricated tie");

        Plate samePlane = new Plate(1, near.center(), near.normal());
        check(nearestContact(List.of(samePlane, near), origin, direction, 40, -1).orElseThrow().plateIndex() == 0,
                "plate ties have deterministic index ordering");
        Contact secondAtZero = nearestContact(List.of(near, samePlane), near.center(), direction, 40, 0).orElseThrow();
        check(secondAtZero.plateIndex() == 1 && secondAtZero.distance() == 0,
                "departure bookkeeping never excludes a different plate at zero distance");
        check(nearestContact(List.of(near), origin, direction, 40, 0).isPresent(),
                "index-only departure cannot skip a positive-distance revisited plate");

        Contact prior = contact(near, origin, direction, 40).orElseThrow();
        check(nearestContact(List.of(near), prior.point(), prior.reflectedDirection(), 35, prior).isEmpty(),
                "exact prior contact removes only own departure event");
        Plate hairline = new Plate(1, v(0, 0, 5 - 1e-12), v(0, 0, -1));
        Contact extremelyClose = nearestContact(List.of(near, hairline), prior.point(), prior.reflectedDirection(), 35, prior).orElseThrow();
        check(extremelyClose.plateIndex() == 1 && extremelyClose.distance() > 0 && extremelyClose.distance() < 2e-12,
                "no unchecked epsilon step over a second plane just after reflection");
        check(prior.point().equals(v(0, 0, 5)), "progress bookkeeping never advances ray origin");
        Contact unrelated = nearestContact(List.of(near), v(0, 0, 6), v(0, 0, -1), 35, prior).orElseThrow();
        check(unrelated.kind() == ContactKind.BACK_FACE && unrelated.distance() == 1,
                "witness from another origin cannot exclude a genuine later contact");
        check(nearestContact(List.of(near), prior.point(), direction, 35, prior).isPresent(),
                "witness with a changed direction grants no exclusion");
    }

    private static void twoMirrorArclengthAndRevisit() {
        Vector start = v(0, 0, 0), firstDirection = v(0, 0, 1), secondDirection = v(1, 0, 0);
        Plate first = new Plate(0, v(0, 0, 5), normalFor(firstDirection, secondDirection).orElseThrow());
        Plate second = new Plate(1, v(5, 0, 5), normalFor(secondDirection, firstDirection).orElseThrow());
        List<Plate> mirrors = List.of(second, first);
        Contact firstHit = nearestContact(mirrors, start, firstDirection, MAX_LENGTH, -1).orElseThrow();
        Contact secondHit = nearestContact(mirrors, firstHit.point(), firstHit.reflectedDirection(),
                MAX_LENGTH - firstHit.distance(), firstHit).orElseThrow();
        check(firstHit.plateIndex() == 0 && secondHit.plateIndex() == 1, "two unique mirrors in actual path order");
        near(firstHit.distance(), 5, 1e-13, "first leg arclength");
        near(secondHit.distance(), 5, 1e-13, "second leg arclength");
        double remaining = MAX_LENGTH - firstHit.distance() - secondHit.distance();
        Vector end = secondHit.point().add(secondHit.reflectedDirection().scale(remaining));
        near(end, v(5, 0, 35), 1e-12, "third leg receives only remaining total budget");
        near(firstHit.point().subtract(start).length() + secondHit.point().subtract(firstHit.point()).length()
                + end.subtract(secondHit.point()).length(), 40, 1e-12, "three contiguous segments conserve total arclength");
        check(nearestContact(mirrors, secondHit.point(), secondHit.reflectedDirection(), remaining, secondHit).isEmpty(),
                "departure from second plate does not fabricate a third reflection");
        check(MAX_REFLECTIONS == 2 && MAX_SEGMENTS == 3 && MAX_LENGTH == 40 && BEAM_RADIUS == .035,
                "approved fixed path and radius bounds");

        Plate reversing = new Plate(1, v(3, 0, 5), v(1, 0, 0));
        List<Plate> returningMirrors = List.of(first, reversing);
        Contact out = nearestContact(returningMirrors, start, firstDirection, 40, -1).orElseThrow();
        Contact back = nearestContact(returningMirrors, out.point(), out.reflectedDirection(), 40 - out.distance(), out).orElseThrow();
        Contact revisited = nearestContact(returningMirrors, back.point(), back.reflectedDirection(),
                40 - out.distance() - back.distance(), back).orElseThrow();
        check(revisited.plateIndex() == out.plateIndex() && revisited.reflects(),
                "visited plate remains visible to controller so it stops rather than skipping and damaging behind");
        near(revisited.distance(), 3, 1e-12, "revisit distance is physical, not another full forty metre budget");
    }

    private static void orientationsAndPlacementVolume() {
        Plate straight = new Plate(0, v(2, 3, 4), v(0, 0, 1));
        Bounds bounds = volumeBounds(straight);
        near(bounds.min(), v(1.55, 2.35, 3.98), 1e-14, "axis aligned slab minimum");
        near(bounds.max(), v(2.45, 3.65, 4.02), 1e-14, "axis aligned slab maximum");
        Random random = new Random(221);
        List<Vector> normals = new ArrayList<>(List.of(v(0, 1, 0), v(0, -1, 0), v(1, 0, 0), v(-1, 0, 0), v(0, 0, 1), v(0, 0, -1)));
        for (int i = 0; i < 100; i++) normals.add(randomDirection(random));
        for (Vector normal : normals) {
            Plate plate = new Plate(0, v(4, 8, -3), normal);
            Frame basis = frame(plate);
            near(basis.right().length(), 1, 1e-13, "unit lateral render axis");
            near(basis.up().length(), 1, 1e-13, "unit vertical render axis");
            near(basis.right().dot(normal), 0, 1e-13, "horizontal axis in plane");
            near(basis.up().dot(normal), 0, 1e-13, "vertical axis in plane");
            near(basis.right().dot(basis.up()), 0, 1e-13, "render axes perpendicular");
            near(basis.right().y(), 0, 0, "lateral radius remains horizontal");
            near(basis.right().cross(basis.up()), normal, 1e-13, "consistent plane handedness");
            Bounds box = volumeBounds(plate);
            for (int angle = 0; angle < 360; angle++) {
                Vector point = perimeterPoint(plate, Math.toRadians(angle));
                Vector local = point.subtract(plate.center());
                near(local.dot(normal), 0, 3e-15, "render perimeter lies on collision plane");
                near(Math.pow(local.dot(basis.right()) / RADIUS_X, 2)
                        + Math.pow(local.dot(basis.up()) / RADIUS_Y, 2), 1, 8e-15, "true ellipse, not quad coverage");
                for (int side : new int[]{-1, 1}) {
                    Vector face = point.add(normal.scale(side * THICKNESS / 2));
                    check(insideBounds(face, box, 2e-15), "placement bounds cover every sampled point of full thick ellipse");
                }
            }
            for (int axis = 0; axis < 3; axis++) {
                double angle = Math.atan2(RADIUS_Y * component(basis.up(), axis), RADIUS_X * component(basis.right(), axis));
                Vector support = perimeterPoint(plate, angle).add(normal.scale(Math.copySign(THICKNESS / 2, component(normal, axis))));
                near(component(support, axis), component(box.max(), axis), 3e-15, "analytic bounds attain true slab support");
            }
        }
    }

    private static void randomizedReflectionConservation() {
        Random random = new Random(221_400);
        int residualDepartures = 0;
        for (int i = 0; i < 10_000; i++) {
            Vector incoming = randomDirection(random), outgoing = randomDirection(random);
            var normal = normalFor(incoming, outgoing);
            double expectedIncidence = Math.sqrt(Math.max(0, (1 - incoming.dot(outgoing)) / 2));
            check(normal.isPresent() == (expectedIncidence > MIN_INCIDENCE), "normal rejects precisely degenerate/grazing edits");
            if (normal.isEmpty()) continue;
            Vector center = i % 2 == 0 ? v(0, 0, 0) : v(29_999_000, -29_999_000, 29_999_000);
            Plate plate = new Plate(0, center, normal.get());
            Contact hit = contact(plate, center.subtract(incoming.scale(8)), incoming, 40).orElseThrow();
            check(hit.reflects(), "random front-face center entry reflects across bounded world");
            near(hit.reflectedDirection(), outgoing, 2e-14, "reflection matches independently chosen outgoing direction");
            near(hit.reflectedDirection().length(), 1, 5e-15, "reflection conserves unit speed");
            near(incoming.dot(normal.get()), -hit.reflectedDirection().dot(normal.get()), 2e-14,
                    "angle of incidence equals angle of reflection");
            Vector tangentIn = incoming.subtract(normal.get().scale(incoming.dot(normal.get())));
            Vector tangentOut = hit.reflectedDirection().subtract(normal.get().scale(hit.reflectedDirection().dot(normal.get())));
            near(tangentIn, tangentOut, 2e-14, "reflection conserves tangential component");
            near(unit(hit.reflectedDirection().subtract(normal.get().scale(2 * hit.reflectedDirection().dot(normal.get())))).orElseThrow(),
                    incoming, 2e-14, "reflection is an involution");
            checkFootprintInside(plate, hit, incoming, 8);
            if (nearestContact(List.of(plate), hit.point(), hit.reflectedDirection(), 32, 0).isPresent()) residualDepartures++;
            check(nearestContact(List.of(plate), hit.point(), hit.reflectedDirection(), 32, hit).isEmpty(),
                    "contact witness handles world-coordinate cancellation without moving the ray");
        }
        check(residualDepartures > 0, "test actually exercises nonzero floating-point departure residuals");
    }

    private static void malformedInput() {
        Plate plate = new Plate(0, v(0, 0, 5), v(0, 0, 1));
        Vector origin = v(0, 0, 0), direction = v(0, 0, 1);
        for (Vector invalid : new Vector[]{null, v(0, 0, 0), v(Double.NaN, 0, 1),
                v(0, Double.POSITIVE_INFINITY, 1), v(Double.NEGATIVE_INFINITY, 0, 1), v(WORLD_LIMIT + 1, 0, 1)}) {
            check(unit(invalid).isEmpty(), "invalid or zero direction is not normalized");
            check(normalFor(invalid, direction).isEmpty() && normalFor(direction, invalid).isEmpty(), "invalid edit direction rejected");
            check(contact(plate, origin, invalid, 40).isEmpty(), "invalid shot direction rejected");
            expectInvalid(() -> new Plate(0, origin, invalid), "invalid fixed normal rejected, never normalized silently");
        }
        for (double limit : new double[]{Double.NaN, Double.POSITIVE_INFINITY, -1, Math.nextUp(40.0)})
            check(contact(plate, origin, direction, limit).isEmpty(), "non-finite or excessive segment limit rejected");
        check(contact(plate, origin, v(0, 0, 2), 40).isEmpty(), "nonunit ray cannot distort metre distances");
        check(contact(plate, v(WORLD_LIMIT + 1, 0, 0), direction, 40).isEmpty(), "out-of-world origin rejected");
        check(contact(plate, v(Double.NaN, 0, 0), direction, 40).isEmpty(), "nonfinite origin rejected");
        check(contact(null, origin, direction, 40).isEmpty(), "missing plate rejected");
        expectInvalid(() -> new Plate(0, origin, v(0, 0, 1.00001)), "nonunit normal rejected");
        expectInvalid(() -> new Plate(-1, origin, direction), "negative plate index rejected");
        expectInvalid(() -> new Plate(2, origin, direction), "third plate index rejected");
        expectInvalid(() -> new Plate(0, null, direction), "null center rejected");
        expectInvalid(() -> new Plate(0, v(Double.NaN, 0, 0), direction), "nonfinite center rejected");
        expectInvalid(() -> new Plate(0, v(WORLD_LIMIT, 0, 0), direction), "entire ellipse must fit world bounds");
        expectInvalid(() -> new Plate(0, v(0, 0, WORLD_LIMIT), direction), "entire thickness must fit world bounds");
        new Plate(0, v(WORLD_LIMIT - 1, 0, WORLD_LIMIT - 1), direction);
        check(nearestContact(List.of(plate, plate), origin, direction, 40, -1).isEmpty(), "duplicate plate identity rejected");
        check(nearestContact(List.of(plate, plate, plate), origin, direction, 40, -1).isEmpty(), "over-cap array rejected");
        check(nearestContact(null, origin, direction, 40, -1).isEmpty(), "missing mirror array rejected");
        check(nearestContact(List.of(plate), origin, direction, 40, 2).isEmpty(), "invalid departure identity rejected");
        expectInvalid(() -> perimeterPoint(plate, Double.NaN), "nonfinite render angle rejected");
    }

    /** Independent geometric oracle: intersect the beam cylinder circumference with the plane. */
    private static void checkFootprintInside(Plate plate, Contact contact, Vector ray, int samples) {
        Vector reference = Math.abs(ray.y()) < .9 ? v(0, 1, 0) : v(1, 0, 0);
        Vector cylinderRight = unit(ray.cross(reference)).orElseThrow(), cylinderUp = ray.cross(cylinderRight);
        Frame basis = frame(plate);
        for (int i = 0; i < samples; i++) {
            double angle = 2 * Math.PI * i / samples;
            Vector radial = cylinderRight.scale(BEAM_RADIUS * Math.cos(angle))
                    .add(cylinderUp.scale(BEAM_RADIUS * Math.sin(angle)));
            Vector planeOffset = radial.subtract(ray.scale(radial.dot(plate.normal()) / ray.dot(plate.normal())));
            Vector point = contact.point().add(planeOffset).subtract(plate.center());
            double rho = Math.hypot(point.dot(basis.right()) / RADIUS_X, point.dot(basis.up()) / RADIUS_Y);
            check(rho <= 1 + 2e-8, "every accepted beam-cylinder rim point lies inside true ellipse");
        }
    }

    private static Vector randomDirection(Random random) {
        return unit(v(random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1, random.nextDouble() * 2 - 1)).orElseThrow();
    }
    private static boolean insideBounds(Vector point, Bounds bounds, double tolerance) {
        return point.x() >= bounds.min().x() - tolerance && point.x() <= bounds.max().x() + tolerance
                && point.y() >= bounds.min().y() - tolerance && point.y() <= bounds.max().y() + tolerance
                && point.z() >= bounds.min().z() - tolerance && point.z() <= bounds.max().z() + tolerance;
    }
    private static double component(Vector vector, int index) { return index == 0 ? vector.x() : index == 1 ? vector.y() : vector.z(); }
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static void near(Vector actual, Vector expected, double tolerance, String why) {
        near(actual.subtract(expected).length(), 0, tolerance, why);
    }
    private static void near(double actual, double expected, double tolerance, String why) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= tolerance,
                why + ": expected " + expected + ", got " + actual);
    }
    private static void expectInvalid(Runnable action, String why) {
        checks++;
        try { action.run(); } catch (IllegalArgumentException expected) { return; }
        throw new AssertionError(why);
    }
    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
}
