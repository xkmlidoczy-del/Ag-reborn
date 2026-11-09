package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.FoldedMirrorGeometry.Bounds;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static cn.academy.port.highpower.core.ZeroGapArmorGeometry.*;

/** Pure shared-render/server geometry; no world, resource ledger or incoming-aim adapter. */
public final class ZeroGapArmorGeometryRegressionTest {
    private static int checks;
    private static final Vector FEET = v(0, 0, 0);

    public static void main(String[] args) {
        dimensionsAndBounds();
        phaseAndFacing();
        facesGapsAndBoundaries();
        exactContactsAndOrdering();
        translationAndRotation();
        invalidInputs();
        System.out.println("ZeroGapArmorGeometryRegressionTest: " + checks + " checks passed");
    }

    private static void dimensionsAndBounds() {
        check(PLATE_COUNT == 6 && ACTIVE_PLATE_COUNT == 3, "six identities and at most three colliders");
        check(RADIUS == 1.25 && WIDTH == .9 && HEIGHT == 1.3 && CENTER_HEIGHT == 1.1, "approved finite tuning");
        for (int phase = 0; phase < ZeroGapArmorTiming.REVOLUTION_TICKS; phase++) {
            Snapshot state = capture(FEET, 17, phase);
            check(state.plates().size() == 6, "immutable capture keeps dim plates too");
            for (Plate plate : state.plates()) {
                near(plate.center().y(), CENTER_HEIGHT, 0, "feet-relative center");
                near(Math.hypot(plate.center().x(), plate.center().z()), RADIUS, 1e-15, "radius 1.25 ring");
                Frame basis = frame(plate);
                near(basis.normal().length(), 1, 1e-15, "unit outward normal");
                near(basis.right().length(), 1, 1e-15, "unit right");
                check(basis.normal().y() == 0 && basis.right().y() == 0 && basis.up().equals(v(0, 1, 0)), "vertical plates do not inherit pitch");
                near(basis.normal().dot(basis.right()), 0, 1e-15, "orthogonal flat frame");
                List<Vector> corners = vertices(plate);
                near(corners.get(0).subtract(corners.get(1)).length(), WIDTH, 1e-15, "actual finite width");
                near(corners.get(0).subtract(corners.get(3)).length(), HEIGHT, 1e-15, "actual finite height");
                Bounds bounds = volumeBounds(plate);
                for (Vector corner : corners) {
                    check(corner.subtract(FEET).length() < 3, "every front corner inside book's 3m bound");
                    check(corner.subtract(plate.normal().scale(THICKNESS)).subtract(FEET).length() < 3, "every back corner inside 3m");
                    check(inBounds(bounds, corner, 0), "front vertices enclosed exactly for broad phase");
                    check(inBounds(bounds, corner.subtract(plate.normal().scale(THICKNESS)), 0), "inward slab enclosed exactly for rendering");
                }
            }
        }
        Snapshot state = capture(FEET, 0, 0);
        try { state.plates().clear(); throw new AssertionError("mutable snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
        List<Plate> copied = new ArrayList<>(state.plates());
        Snapshot copy = new Snapshot(FEET, 0, 0, copied);
        copied.clear();
        check(copy.equals(state), "snapshot defensively copies the list");
    }

    private static void phaseAndFacing() {
        for (int phase = 0; phase < 120; phase++) for (int yaw = 0; yaw < 360; yaw++) {
            Snapshot state = capture(FEET, yaw, phase);
            check(Integer.bitCount(state.activeMask()) == 3, "exactly three active over every physical phase and yaw degree");
            for (Plate active : state.plates()) if (active.active()) for (Plate dim : state.plates()) if (!dim.active()) {
                double a = distance(plateYawDegrees(phase, active.id()), yaw);
                double b = distance(plateYawDegrees(phase, dim.id()), yaw);
                check(a < b || a == b && active.id() < dim.id(), "three closest yaw normals with stable ID ties");
            }
        }
        check(capture(FEET, 0, 0).activeMask() == ((1 << 0) | (1 << 1) | (1 << 5)), "world-facing zero yaw");
        check(capture(FEET, 30, 0).activeMask() == ((1 << 0) | (1 << 1) | (1 << 2)), "exact cutoff tie uses lower ID");
        check(capture(FEET, Math.nextDown(30.0), 0).activeMask() == ((1 << 0) | (1 << 1) | (1 << 5)), "just before tie retains actual closest plate");
        check(capture(FEET, Math.nextUp(30.0), 0).activeMask() == ((1 << 0) | (1 << 1) | (1 << 2)), "just after tie selects actual closest plate");
        for (int phase = 0; phase < 120; phase++) for (int plate = 0; plate < 6; plate++) {
            double cutoff = (plateYawDegrees(phase, plate) + 30) % 360;
            for (double yaw : new double[] {Math.nextDown(cutoff), cutoff, Math.nextUp(cutoff)}) {
                Snapshot state = capture(FEET, yaw, phase);
                for (Plate active : state.plates()) if (active.active()) for (Plate dim : state.plates()) if (!dim.active()) {
                    BigDecimal a = exactDistance(plateYawDegrees(phase, active.id()), state.facingYawDegrees());
                    BigDecimal b = exactDistance(plateYawDegrees(phase, dim.id()), state.facingYawDegrees());
                    check(a.compareTo(b) < 0 || a.compareTo(b) == 0 && active.id() < dim.id(), "all cutoffs distinguish adjacent doubles against an independent decimal oracle");
                }
            }
        }
        for (int yaw = -720; yaw <= 720; yaw++) {
            check(capture(FEET, yaw, 37).equals(capture(FEET, yaw + 720, 37)), "normalized yaw wrap keeps geometry and active IDs");
        }
        Snapshot atZero = capture(FEET, 43, ZeroGapArmorTiming.INITIAL);
        ZeroGapArmorTiming timing = ZeroGapArmorTiming.INITIAL;
        for (int step = 1; step <= 360; step++) {
            timing = timing.advanceOneNativeTick();
            ZeroGapArmorTiming restored = ZeroGapArmorTiming.fromDecimalMap(timing.toDecimalMap());
            check(capture(FEET, 43, timing).equals(capture(FEET, 43, restored)), "reload preserves world phase and IDs");
            check(capture(FEET, 43, timing).phaseTick() == step % 120, "exact physical 120 tick revolution");
            if (step % 120 == 0) check(capture(FEET, 43, timing).equals(atZero), "whole revolution wraps exactly");
        }
        timing = new ZeroGapArmorTiming(73, 0);
        check(capture(FEET, 11, timing).equals(capture(FEET, 11, timing.brokenNow())), "break debt cannot reset phase or move plate identities");
        Snapshot yawA = capture(FEET, 0, 37), yawB = capture(FEET, 180, 37);
        for (int i = 0; i < 6; i++) check(yawA.plates().get(i).center().equals(yawB.plates().get(i).center()), "facing changes activation only, not world phase");
    }

    private static void facesGapsAndBoundaries() {
        Snapshot state = capture(FEET, 0, 0);
        for (Plate plate : state.plates()) {
            Vector outside = plate.center().add(plate.normal().scale(2));
            Vector inside = plate.center().subtract(plate.normal().scale(.5));
            var hit = contact(plate, outside, inside);
            check(hit.isPresent() == plate.active(), "only an actual active face collides");
            if (hit.isPresent()) {
                check(hit.get().plateId() == plate.id(), "actual numbered contact");
                near(hit.get().fraction(), .8, 2e-15, "exact face segment parameter");
                near(hit.get().point().subtract(plate.center()).length(), 0, 1e-15, "actual face point");
            }
            check(contact(plate, inside, outside).isEmpty(), "rear-to-front departure is not defense");
            check(contact(plate, plate.center().subtract(plate.normal().scale(.01)), inside).isEmpty(), "already inside cannot catch");
        }
        Vector core = v(0, CENTER_HEIGHT, 0);
        for (double degrees : new double[] {25, 30, 35, 325, 330, 335}) {
            Vector direction = yaw(degrees);
            check(nearestContact(state, core.add(direction.scale(3)), core).isEmpty(), "real finite gap between front plates");
        }
        check(nearestContact(state, v(0, CENTER_HEIGHT, -3), core).isEmpty(), "inactive rear face remains a real gap");
        check(nearestContact(state, core, v(0, CENTER_HEIGHT, 3)).isEmpty(), "inside-origin ray departs through front");
        check(nearestContact(state, v(.8, CENTER_HEIGHT, 3), v(.8, CENTER_HEIGHT, 0)).isEmpty(), "enclosing rectangle or cone would falsely cover this gap");
        Plate front = state.plates().getFirst();
        for (double x : new double[] {-HALF_WIDTH, 0, HALF_WIDTH}) for (double y : new double[] {-HALF_HEIGHT, 0, HALF_HEIGHT}) {
            Vector boundary = point(front, x, y);
            check(contact(front, boundary.add(v(0, 0, 2)), boundary.subtract(v(0, 0, .5))).isPresent(), "closed finite edges and corners admitted");
        }
        for (double x : new double[] {Math.nextDown(-HALF_WIDTH), Math.nextUp(HALF_WIDTH), -.6, .6})
            check(contact(front, v(x, CENTER_HEIGHT, 3), v(x, CENTER_HEIGHT, 0)).isEmpty(), "outside lateral edge has no positive epsilon expansion");
        for (double y : new double[] {Math.nextDown(CENTER_HEIGHT - HALF_HEIGHT), Math.nextUp(CENTER_HEIGHT + HALF_HEIGHT), 0, 2})
            check(contact(front, v(0, y, 3), v(0, y, 0)).isEmpty(), "outside finite top/bottom misses");
        check(contact(front, v(.451, CENTER_HEIGHT + .651, 3), v(.451, CENTER_HEIGHT + .651, 0)).isEmpty(), "outside actual corner misses");
        check(contact(front, v(-3, CENTER_HEIGHT, RADIUS), v(3, CENTER_HEIGHT, RADIUS)).isEmpty(), "coplanar side traversal misses");
        check(contact(front, v(0, CENTER_HEIGHT, RADIUS + 1e-12), v(0, CENTER_HEIGHT, RADIUS - 1e-12)).isPresent(), "tiny genuine crossing is not erased by an epsilon");
    }

    private static void exactContactsAndOrdering() {
        Plate front = capture(FEET, 0, 0).plates().getFirst();
        Vector center = front.center(), outside = center.add(v(0, 0, 2)), inside = center.subtract(v(0, 0, 1));
        Contact terminal = contact(front, outside, center).orElseThrow();
        check(terminal.fraction() == 1 && terminal.point().equals(center), "exact terminal contact retained for terrain/body tie");
        Contact initial = contact(front, center, inside).orElseThrow();
        check(initial.fraction() == 0 && initial.point().equals(center), "exact initial inward contact retained without origin shift");
        check(contact(front, center, center).isEmpty(), "zero movement gives no fabricated contact");
        check(contact(front, center, outside).isEmpty(), "starting on plane and departing is not an entry");
        check(contact(front, outside, center.add(v(0, 0, Math.ulp(RADIUS)))).isEmpty(), "segment ends before plate with no epsilon jump");
        Plate low = new Plate(1, center, v(0, 0, 1), true), high = new Plate(4, center, v(0, 0, 1), true);
        check(nearestContact(List.of(high, low), outside, inside).orElseThrow().plateId() == 1, "equal distance lower ID wins independent of list order");
        check(nearestContact(List.of(low, high), outside, inside).orElseThrow().plateId() == 1, "reordered tie is deterministic");
        Plate near = new Plate(4, center.add(v(0, 0, 1)), v(0, 0, 1), true);
        Contact first = nearestContact(List.of(low, near), outside, inside).orElseThrow();
        check(first.plateId() == 4 && first.fraction() == 1.0 / 3, "nearest physical face precedes lower identity");
        near(first.point().z(), center.z() + 1, 0, "first exact world point");
        check(nearestContact(List.of(low, low), outside, inside).isEmpty(), "duplicate IDs reject entire ambiguous candidate list");
        check(nearestContact(Arrays.asList(low, null), outside, inside).isEmpty(), "null candidate cannot hide malformed capture");
    }

    private static void translationAndRotation() {
        for (Vector feet : new Vector[] {v(13, 27, -41), v(29_999_990, -29_999_990, 29_999_990), v(-29_999_990, 29_999_990, -29_999_990)}) {
            Snapshot shifted = capture(feet, 12, 17), origin = capture(FEET, 12, 17);
            for (int i = 0; i < 6; i++) {
                Plate a = origin.plates().get(i), b = shifted.plates().get(i);
                near(b.center().subtract(feet).subtract(a.center()).length(), 0, 5e-9, "translation preserves centers to world-coordinate precision");
                check(a.active() == b.active() && a.normal().equals(b.normal()), "translation preserves IDs and orientation");
                for (Vector corner : vertices(b)) check(inBounds(volumeBounds(b), corner, 0), "far-coordinate broad phase encloses actual corners exactly");
                Vector start = b.center().add(b.normal().scale(2)), end = b.center().subtract(b.normal().scale(.5));
                var hit = contact(b, start, end);
                check(hit.isPresent() == b.active(), "saturated valid world position still uses real face");
                if (hit.isPresent()) near(hit.get().fraction(), .8, 2e-9, "far-coordinate segment parameter remains accurate");
            }
        }
        for (int phase = 0; phase < 120; phase++) {
            Snapshot before = capture(FEET, 11.5, phase);
            Snapshot rotated = capture(FEET, 101.5, (phase + 30) % 120);
            for (int i = 0; i < 6; i++) {
                Plate a = before.plates().get(i), b = rotated.plates().get(i);
                check(a.active() == b.active(), "quarter-turn phase and yaw preserve active IDs");
                near(rotate90(a.center()).subtract(b.center()).length(), 0, 2e-15, "quarter-turn actual positions");
                near(rotate90(a.normal()).subtract(b.normal()).length(), 0, 2e-15, "quarter-turn outward normals");
                if (a.active()) {
                    Vector start = a.center().add(a.normal().scale(2)), end = a.center().subtract(a.normal().scale(.5));
                    Contact first = contact(a, start, end).orElseThrow();
                    Contact second = contact(b, rotate90(start), rotate90(end)).orElseThrow();
                    near(first.fraction(), second.fraction(), 2e-15, "contact fraction covariant under rotation");
                    near(rotate90(first.point()).subtract(second.point()).length(), 0, 2e-15, "contact point covariant under rotation");
                }
            }
        }
    }

    private static void invalidInputs() {
        Plate front = capture(FEET, 0, 0).plates().getFirst();
        Vector end = v(0, CENTER_HEIGHT, 0);
        for (Vector bad : new Vector[] {v(Double.NaN, 0, 0), v(0, Double.POSITIVE_INFINITY, 0), v(0, 0, Double.NEGATIVE_INFINITY), v(Double.MAX_VALUE, 0, 0), v(30_000_001, 0, 0), null}) {
            check(contact(front, bad, end).isEmpty() && contact(front, end, bad).isEmpty(), "invalid/saturated segment endpoints fail closed");
            reject(() -> capture(bad, 0, 0), "invalid feet refused");
        }
        for (Vector boundary : new Vector[] {v(30_000_000, 0, 0), v(-30_000_000, 0, 0), v(0, 30_000_000, 0), v(0, 0, 30_000_000)})
            reject(() -> capture(boundary, 0, 0), "partial geometry outside world bounds is refused");
        for (double yaw : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY})
            reject(() -> capture(FEET, yaw, 0), "nonfinite yaw does not invent a fallback");
        reject(() -> capture(FEET, 0, -1), "negative phase rejected");
        reject(() -> capture(FEET, 0, 120), "unsynchronized revolution counter rejected");
        reject(() -> capture(FEET, 0, (ZeroGapArmorTiming) null), "missing persisted phase rejected");
        reject(() -> new Plate(0, v(0, 1, 0), v(0, 1, 0), true), "vertical normal has no zero-vector fallback");
        reject(() -> new Plate(0, v(0, 1, 0), v(0, 0, 0), true), "zero normal is rejected");
        reject(() -> new Plate(0, v(0, 1, 0), v(0, 0, 2), true), "nonunit normal is rejected");
        reject(() -> new Plate(6, v(0, 1, 0), v(0, 0, 1), true), "out-of-range identity is rejected");
        reject(() -> point(front, Math.nextUp(HALF_WIDTH), 0), "cannot render expanded corners");
        reject(() -> point(front, 0, Double.NaN), "nonfinite local coordinate rejected");
        check(contact(front, v(0, CENTER_HEIGHT, 128), v(0, CENTER_HEIGHT, 0)).isPresent(), "closed supported segment maximum");
        check(contact(front, v(0, CENTER_HEIGHT, Math.nextUp(128.0)), v(0, CENTER_HEIGHT, 0)).isEmpty(), "unbounded segment refused");
        check(nearestContact((Snapshot) null, end, end).isEmpty(), "null capture fails closed");
        check(nearestContact(List.of(), end, end).isEmpty(), "no active candidate is a miss");
    }

    private static boolean inBounds(Bounds bounds, Vector point, double tolerance) {
        return point.x() >= bounds.min().x() - tolerance && point.x() <= bounds.max().x() + tolerance
                && point.y() >= bounds.min().y() - tolerance && point.y() <= bounds.max().y() + tolerance
                && point.z() >= bounds.min().z() - tolerance && point.z() <= bounds.max().z() + tolerance;
    }
    private static double distance(double first, double second) { double d = Math.abs(first - second); return Math.min(d, 360 - d); }
    private static BigDecimal exactDistance(double first, double second) {
        BigDecimal d = new BigDecimal(first).subtract(new BigDecimal(second)).abs();
        return d.min(BigDecimal.valueOf(360).subtract(d));
    }
    private static Vector yaw(double degrees) { double radians = Math.toRadians(degrees); return v(-Math.sin(radians), 0, Math.cos(radians)); }
    private static Vector rotate90(Vector point) { return v(-point.z(), point.y(), point.x()); }
    private static Vector v(double x, double y, double z) { return new Vector(x, y, z); }
    private static void near(double actual, double expected, double tolerance, String why) { check(Math.abs(actual - expected) <= tolerance, why + ": " + actual + " versus " + expected); }
    private static void reject(Runnable action, String why) { try { action.run(); throw new AssertionError(why); } catch (IllegalArgumentException expected) { checks++; } }
    private static void check(boolean condition, String why) { checks++; if (!condition) throw new AssertionError(why); }
}
