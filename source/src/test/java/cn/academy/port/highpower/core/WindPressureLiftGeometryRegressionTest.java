package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.WindPressureLiftGeometry.Box;
import cn.academy.port.highpower.core.WindPressureLiftGeometry.Frame;
import cn.academy.port.highpower.core.WindPressureLiftGeometry.Point;
import java.math.BigDecimal;
import java.util.List;
import java.util.Random;

/** Exact fixed-cylinder, full standing body, world-boundary and conservative sweep checks. */
public final class WindPressureLiftGeometryRegressionTest {
    private static int checks;
    private static final Frame ORIGIN = new Frame(new Point(0, 0, 0));
    private static final Box STANDING = new Box(-.3, 0, -.3, .3, 1.8, .3);

    public static void main(String[] args) {
        standingBodyAndCeilings();
        exactRadialBoundary();
        translatedWorldBounds();
        sweptBodies();
        malformedInputs();
        randomizedOracle();
        System.out.println("PASS " + checks + " NEW443 fixed full-body cylinder and sweep geometry checks");
    }

    private static void standingBodyAndCeilings() {
        check(WindPressureLiftGeometry.contains(ORIGIN, STANDING), "standing body begins inside grounded fixed column");
        check(WindPressureLiftGeometry.DIAMETER == 6 && WindPressureLiftGeometry.RADIUS == 3
                && WindPressureLiftGeometry.HEIGHT == 6, "explicit diameter-six and height-six field");
        Box top = new Box(-.3, 4.2, -.3, .3, 6, .3);
        check(WindPressureLiftGeometry.contains(ORIGIN, top), "standing head may exactly touch closed column ceiling");
        check(!WindPressureLiftGeometry.contains(ORIGIN,
                new Box(-.3, 4.2, -.3, .3, Math.nextUp(6.0), .3)), "one ULP above ceiling refuses full body");
        check(!WindPressureLiftGeometry.contains(ORIGIN,
                new Box(-.3, Math.nextDown(0.0), -.3, .3, 1.8, .3)), "one ULP below base refuses full body");
        check(!WindPressureLiftGeometry.contains(ORIGIN,
                new Box(2.5, 0, -.3, 3.1, 1.8, .3)), "feet center in cylinder does not excuse shoulder outside");
        check(!WindPressureLiftGeometry.contains(ORIGIN,
                new Box(2, 0, 2, 2.6, 1.8, 2.6)), "square broad-phase corners are not cylindrical space");
        check(!WindPressureLiftGeometry.contains(ORIGIN,
                new Box(-.3, 5, -.3, .3, 6.8, .3)), "feet below six meters does not authorize head above field");
        Box bounds = WindPressureLiftGeometry.bounds(ORIGIN);
        check(bounds.equals(new Box(-3, 0, -3, 3, 6, 3)), "frozen base defines exact enclosing bounds");
        check(!WindPressureLiftGeometry.contains(ORIGIN, bounds), "enclosing box is broad phase only");
    }

    private static void exactRadialBoundary() {
        // Squaring the subnormal X component underflows in ordinary double arithmetic.
        // An exact comparison must still reject this actual corner beyond radius three.
        Box tinyOutside = new Box(-Double.MIN_VALUE, 0, 2.9, 0, 1.8, 3);
        check(!WindPressureLiftGeometry.contains(ORIGIN, tinyOutside),
                "exact represented corner beyond circle has no rounding apron");
        Box tinyInside = new Box(-Double.MIN_VALUE, 0, 2.9, 0, 1.8, Math.nextDown(3.0));
        check(WindPressureLiftGeometry.contains(ORIGIN, tinyInside), "one ULP radial clearance admits full tiny box");
        for (double z : new double[]{.1, .3, .5, 1, 1.8, 2.4}) {
            double edge = Math.sqrt(9 - z * z);
            for (double x : new double[]{Math.nextDown(edge), edge, Math.nextUp(edge)}) {
                Box box = new Box(x - .01, 0, 0, x, 1.8, z);
                check(WindPressureLiftGeometry.contains(ORIGIN, box) == oracle(ORIGIN, box),
                        "adjacent representable radial corners agree with independent exact oracle");
            }
        }
    }

    private static void translatedWorldBounds() {
        for (Point base : List.of(new Point(8192.125, 64.25, -8192.5),
                new Point(1_000_000.125, -64, -1_000_000.5),
                new Point(29_999_990.125, 120, -29_999_990.5),
                new Point(-29_999_990.125, 120, 29_999_990.5))) {
            Frame frame = new Frame(base);
            Box body = shifted(STANDING, base.x(), base.y(), base.z());
            check(WindPressureLiftGeometry.contains(frame, body), "large-coordinate standing body remains in frozen column");
            Box bounds = WindPressureLiftGeometry.bounds(frame);
            check(bounds.maxX() - bounds.minX() == 6 && bounds.maxY() - bounds.minY() == 6
                    && bounds.maxZ() - bounds.minZ() == 6, "full extent remains six meters at large coordinates");
            check(frame.base().equals(base), "anchor retains complete double precision without camera-relative conversion");
            check(!WindPressureLiftGeometry.contains(frame, shifted(body, 4, 0, 0)),
                    "field stays fixed instead of recentering on moving body");
            check(WindPressureLiftGeometry.contains(frame, shifted(body, 0, .16, 0)),
                    "sub-block ascent survives large-coordinate precision");
        }
        Frame touch = new Frame(new Point(29_999_997, 29_999_994, -29_999_997));
        check(WindPressureLiftGeometry.bounds(touch).finite(), "complete field may touch hard world bounds");
        rejected(() -> new Frame(new Point(Math.nextUp(29_999_997.0), 0, 0)),
                "full column crossing positive world border refuses");
        rejected(() -> new Frame(new Point(Math.nextDown(-29_999_997.0), 0, 0)),
                "full column crossing negative world border refuses");
        rejected(() -> new Frame(new Point(0, Math.nextUp(29_999_994.0), 0)),
                "ceiling crossing representable world domain refuses");
    }

    private static void sweptBodies() {
        Box sweep = WindPressureLiftGeometry.sweptBox(STANDING, .03, .16, -.02);
        check(sweep.equals(new Box(-.3, 0, -.32, .32999999999999996, 1.96, .3)),
                "sweep unions entire initial and final bodies with all native components");
        check(WindPressureLiftGeometry.containsSweep(ORIGIN, STANDING, .03, .16, -.02),
                "normal combined native step remains inside whole cylinder");
        Box ceiling = new Box(-.3, 4.19, -.3, .3, 5.99, .3);
        check(WindPressureLiftGeometry.contains(ORIGIN, ceiling)
                && !WindPressureLiftGeometry.containsSweep(ORIGIN, ceiling, 0, .16, 0),
                "near ceiling refuses proposed whole-body step without moving or braking body");
        Box edge = new Box(2.38, 0, -.3, 2.98, 1.8, .3);
        check(WindPressureLiftGeometry.contains(ORIGIN, edge)
                && !WindPressureLiftGeometry.containsSweep(ORIGIN, edge, .03, .02, 0),
                "perimeter stops authorization before assisted body leaves cylinder");
        check(!WindPressureLiftGeometry.containsSweep(ORIGIN, STANDING, 0, -.01, 0),
                "even a short downward sweep below frozen base refuses assistance");
        Box raised = shifted(STANDING, 0, 2, 0);
        check(WindPressureLiftGeometry.containsSweep(ORIGIN, raised, 0, -.5, 0),
                "descending body can be checked inside field without resetting fall state");
        for (int step = 0; step <= 100; step++) {
            double t = step / 100.0;
            Box intermediate = shifted(STANDING, .03 * t, .16 * t, -.02 * t);
            check(encloses(sweep, intermediate), "conservative sweep encloses every intermediate standing box");
        }
        check(WindPressureLiftGeometry.sweptBox(STANDING, 0, 0, 0).equals(STANDING),
                "zero displacement preserves whole body bounds");
        check(WindPressureLiftGeometry.sweptBox(STANDING, Double.MAX_VALUE, 0, 0) == null,
                "oversized movement cannot manufacture unbounded collision query");
    }

    private static void malformedInputs() {
        for (Box bad : List.of(new Box(0, 0, 0, 0, 1, 1), new Box(0, 0, 0, 1, 0, 1),
                new Box(0, 0, 0, 1, 1, 0), new Box(1, 0, 0, 0, 1, 1),
                new Box(Double.NaN, 0, 0, 1, 1, 1), new Box(0, 0, 0, Double.POSITIVE_INFINITY, 1, 1),
                new Box(-30_000_001, 0, 0, -29_999_999, 1, 1))) {
            check(!WindPressureLiftGeometry.contains(ORIGIN, bad), "malformed native body fails closed");
            check(WindPressureLiftGeometry.sweptBox(bad, 0, 0, 0) == null, "malformed body cannot create sweep");
        }
        check(!WindPressureLiftGeometry.contains(null, STANDING)
                && !WindPressureLiftGeometry.contains(ORIGIN, null), "missing body or fixed frame refuses");
        for (double bad : new double[]{Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY}) {
            check(!WindPressureLiftGeometry.containsSweep(ORIGIN, STANDING, bad, 0, 0)
                    && !WindPressureLiftGeometry.containsSweep(ORIGIN, STANDING, 0, bad, 0)
                    && !WindPressureLiftGeometry.containsSweep(ORIGIN, STANDING, 0, 0, bad),
                    "all nonfinite motion components refuse full sweep");
            rejected(() -> new Frame(new Point(bad, 0, 0)), "invalid frozen anchor rejected");
        }
        rejected(() -> new Frame(null), "null frozen anchor rejected");
    }

    private static void randomizedOracle() {
        Random random = new Random(443443);
        for (int i = 0; i < 6000; i++) {
            double baseX = i % 2 == 0 ? 0 : 29_999_990.125;
            Frame frame = new Frame(new Point(baseX, 64.25, -baseX));
            double x = random.nextDouble() * 7 - 3.5, z = random.nextDouble() * 7 - 3.5;
            double y = random.nextDouble() * 6 - .5;
            Box body = shifted(new Box(x, y, z, x + .6, y + 1.8, z + .6),
                    frame.base().x(), frame.base().y(), frame.base().z());
            check(WindPressureLiftGeometry.contains(frame, body) == oracle(frame, body),
                    "full-body containment agrees with independent decimal world-coordinate oracle");
        }
    }

    private static boolean oracle(Frame frame, Box box) {
        if (box.minY() < frame.base().y() || box.maxY() > frame.base().y() + 6) return false;
        for (double x : new double[]{box.minX(), box.maxX()})
            for (double z : new double[]{box.minZ(), box.maxZ()}) {
                BigDecimal dx = new BigDecimal(x).subtract(new BigDecimal(frame.base().x()));
                BigDecimal dz = new BigDecimal(z).subtract(new BigDecimal(frame.base().z()));
                if (dx.pow(2).add(dz.pow(2)).compareTo(BigDecimal.valueOf(9)) > 0) return false;
            }
        return true;
    }
    private static Box shifted(Box box, double x, double y, double z) {
        return new Box(box.minX() + x, box.minY() + y, box.minZ() + z,
                box.maxX() + x, box.maxY() + y, box.maxZ() + z);
    }
    private static boolean encloses(Box outer, Box inner) {
        return outer.minX() <= inner.minX() && outer.minY() <= inner.minY() && outer.minZ() <= inner.minZ()
                && outer.maxX() >= inner.maxX() && outer.maxY() >= inner.maxY() && outer.maxZ() >= inner.maxZ();
    }
    private static void check(boolean condition, String why) {
        checks++;
        if (!condition) throw new AssertionError(why);
    }
    private static void rejected(Runnable operation, String why) {
        try { operation.run(); throw new AssertionError(why); }
        catch (IllegalArgumentException expected) { checks++; }
    }
}
