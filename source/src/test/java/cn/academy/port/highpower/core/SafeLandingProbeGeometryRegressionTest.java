package cn.academy.port.highpower.core;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.nio.file.Files;
import java.nio.file.Path;

/** Standalone spatial boundaries: exact translated body, footprint and bounded native apron. */
public final class SafeLandingProbeGeometryRegressionTest {
    private static int checks;
    private static void check(boolean result, String message) {
        checks++; if (!result) throw new AssertionError(message);
    }
    private static void rejects(Runnable action, String message) {
        boolean refused = false;
        try { action.run(); } catch (IllegalArgumentException expected) { refused = true; }
        check(refused, message);
    }
    private static boolean near(double a, double b) { return Math.abs(a - b) < 1e-12; }
    public static void main(String[] args) throws Exception {
        Point source = new Point(3.25, 64, -4.5), target = new Point(9.25, 64.5, -4.5);
        Box body = new Box(2.95, 64, -4.8, 3.55, 65.8, -4.2);
        Box moved = SafeLandingProbeGeometry.translatedBody(body, source, target);
        check(near(moved.minX(), 8.95) && near(moved.maxX(), 9.55), "full width translated around destination");
        check(near(moved.minY(), 64.5) && near(moved.maxY(), 66.3), "head clearance follows full current native height");
        check(moved.minZ() == body.minZ() && moved.maxZ() == body.maxZ(), "unchanged axis is preserved");
        Box crouching = new Box(2.95, 64, -4.8, 3.55, 65.5, -4.2);
        check(near(SafeLandingProbeGeometry.translatedBody(crouching, source, target).maxY(), 66),
                "current crouched dimensions are retained");
        Box support = SafeLandingProbeGeometry.support(moved);
        check(support.minX() == moved.minX() && support.maxX() == moved.maxX()
                && support.minZ() == moved.minZ() && support.maxZ() == moved.maxZ(), "whole footprint requires support");
        check(near(support.minY(), 64.48) && support.maxY() == moved.minY(), "support slab ends exactly at feet");
        check(SafeLandingProbeGeometry.withinRange(new Point(0, 0, 0), new Point(12, 0, 0)), "exact twelve-meter boundary accepted");
        check(!SafeLandingProbeGeometry.withinRange(new Point(0, 0, 0), new Point(Math.nextUp(12), 0, 0)),
                "beyond physical range refuses without rounding");
        check(!SafeLandingProbeGeometry.withinRange(new Point(Double.NaN, 0, 0), target), "nonfinite source refuses");
        rejects(() -> SafeLandingProbeGeometry.translatedBody(body, source, new Point(3.25, 77, -4.5)), "vertical displacement consumes range");
        rejects(() -> SafeLandingProbeGeometry.translatedBody(new Box(0, 0, 0, 5, 2, 1), new Point(0, 0, 0), new Point(1, 0, 0)),
                "oversized modded body fails bounded prototype");
        rejects(() -> SafeLandingProbeGeometry.support(new Box(1, 0, 0, 0, 2, 1)), "inverted body refuses");
        var cells = SafeLandingProbeGeometry.collisionCells(new Box(-.3, 64, -.3, .3, 65.8, .3));
        check(cells.minX() == -2 && cells.maxX() == 1 && cells.minZ() == -2 && cells.maxZ() == 1,
                "negative coordinates use native floor, with complete shape apron");
        check(cells.minY() == 62 && cells.maxY() == 66 && cells.count() == 80, "closed native apron is finite");
        check(SafeLandingProbeGeometry.cells(0, 0, 0, 7, 7, 15).count() == 1024, "exact cell budget boundary");
        rejects(() -> SafeLandingProbeGeometry.cells(0, 0, 0, 8, 8, 15), "oversized cell scan refuses");
        rejects(() -> SafeLandingProbeGeometry.cells(Integer.MIN_VALUE, 0, 0, Integer.MAX_VALUE, 1, 1), "overflow-sized cell scan refuses");
        for (int x = -20; x <= 20; x++) for (int z = -20; z <= 20; z++) {
            Point a = new Point(x * .25, 64, z * .25), b = new Point(a.x() + 1, 64.5, a.z() - 1);
            Box current = new Box(a.x() - .3, 64, a.z() - .3, a.x() + .3, 65.8, a.z() + .3);
            Box next = SafeLandingProbeGeometry.translatedBody(current, a, b);
            check(near(next.maxX() - next.minX(), current.maxX() - current.minX())
                    && near(next.maxY() - next.minY(), current.maxY() - current.minY())
                    && near(next.maxZ() - next.minZ(), current.maxZ() - current.minZ()), "translation cannot shrink body clearance");
        }
        // Native block-state behavior is covered by actual runtime cases separately. This
        // source guard protects the destination-only scope and prevents status-clearing escape.
        String nativeSource = Files.readString(Path.of("src/main/java/cn/academy/port/highpower/SafeLandingProbeWorld.java"));
        String guardedHazard = "if (\"destination\".equals(prefix) && destinationHazard(view.getBlockState(pos)))";
        check(nativeSource.contains(guardedHazard) && nativeSource.indexOf(guardedHazard) == nativeSource.lastIndexOf(guardedHazard),
                "hazard reads have one destination-only guarded call site");
        int floorScan = nativeSource.indexOf("for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(envelope.minX");
        int hazardScan = nativeSource.indexOf(guardedHazard);
        int apronScan = nativeSource.indexOf("SafeLandingProbeGeometry.Cells cells;");
        check(floorScan >= 0 && hazardScan > floorScan && hazardScan < apronScan,
                "hazard checks use bounded actual body/support cells, never the neighbor apron");
        check(nativeSource.contains("throw refuse(\"destination_hazard\")"), "native hazard refusal is explicit");
        check(nativeSource.contains("state.getValue(CampfireBlock.LIT)"), "unlit campfires retain their native safe state");
        for (String block : new String[] {"MAGMA_BLOCK", "CAMPFIRE", "SOUL_CAMPFIRE", "FIRE", "SOUL_FIRE", "CACTUS",
                "SWEET_BERRY_BUSH", "WITHER_ROSE", "POWDER_SNOW", "COBWEB", "NETHER_PORTAL", "END_PORTAL", "END_GATEWAY"})
            check(nativeSource.contains("state.is(Blocks." + block + ")"), "explicit native hazard identity: " + block);
        check(!nativeSource.contains("isOnFire(") && !nativeSource.contains("clearFire(") && !nativeSource.contains("setRemainingFireTicks("),
                "a burning grounded source may escape while attached fire persists");
        System.out.println("SafeLandingProbeGeometryRegressionTest passed " + checks + " checks");
    }
}
