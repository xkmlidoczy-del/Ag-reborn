package cn.academy.port.city;

/** Check terrain-following geometry without loading or modifying a world. */
public final class CityMountainTrailRegressionTest {
    public static void main(String[] args) {
        int worstCut = 0, worstFill = 0;
        for (int iteration = 0; iteration < 32; iteration++) {
            long seed = iteration == 0 ? 20261103L : iteration * 123456789L;
            var trail = new CityMountainTrail(seed);
            var path = trail.centerline();
            check(path.size() == 1140, "expected full switchback path");
            var start = path.getFirst();
            var end = path.getLast();
            check(start.x() == -1752 && start.z() == -804 && start.halfHeight() == 186, "observatory junction");
            check(end.x() == -1320 && end.z() == -909 && end.halfHeight() == 16, "dam junction");
            check(trail.lights().size() >= 85, "mountain route lighting is too sparse");
            int previousLight = 0;
            for (var light : trail.lights()) {
                check(light.pathIndex() - previousLight <= 18, "excessive gap between trail lights");
                previousLight = light.pathIndex();
                var column = trail.at(light.x(), light.z());
                check(column != null && column.light()
                                && "minecraft:lantern[hanging=false,waterlogged=false]".equals(column.block(light.y()))
                                && "academy:pillar_dark".equals(column.block(light.y() - 1)), "supported lantern");
                var near = path.get(light.pathIndex());
                check(Math.abs(near.x() - light.x()) + Math.abs(near.z() - light.z()) == 2,
                        "lighting must stay outside the walking path");
                check(CityDistrictLandmarks.at(seed, light.x(), light.z()) == null, "lighting overlaps a landmark");
            }
            check(path.size() - previousLight <= 24, "excessive gap at trail end");
            for (var guard : trail.guards()) {
                var column = trail.at(guard.x(), guard.z());
                var point = path.get(guard.pathIndex());
                check(column != null && !column.light() && column.guardAxis() != null
                                && column.block(guard.y()).startsWith("minecraft:spruce_fence[")
                                && "minecraft:stone_bricks".equals(column.block(guard.y() - 1)), "guard needs solid support");
                check(Math.abs(point.x() - guard.x()) + Math.abs(point.z() - guard.z()) == 2,
                        "guard must remain outside walking lane");
                check(point.halfHeight() / 2.0 - CityPlan.sample(seed, guard.x(), guard.z()).height() >= 3,
                        "guard should protect a substantial drop");
                check(CityDistrictLandmarks.at(seed, guard.x(), guard.z()) == null, "guard overlaps a landmark");
            }
            for (int i = 0; i < path.size(); i++) {
                var point = path.get(i);
                var natural = CityPlan.sample(seed, point.x(), point.z());
                check(natural.district() == 21 && natural.kind() == CityPlan.Kind.TERRAIN, "trail must stay on D21 land");
                check(CityTransit.overlay(point.x(), point.z()) == null, "trail must not cross railway");
                var column = trail.at(point.x(), point.z());
                check(column != null && !column.light() && column.halfHeight() == point.halfHeight(), "unobstructed centerline floor");
                int floor = Math.floorDiv(point.halfHeight() - 1, 2);
                String block = (point.halfHeight() & 1) == 0 ? "minecraft:stone_bricks" : "minecraft:stone_brick_slab[type=bottom]";
                check(block.equals(column.block(floor)) && "minecraft:air".equals(column.block(floor + 1))
                        && "minecraft:air".equals(column.block(floor + 2)), "walkable floor and headroom");
                if (i > 0) {
                    var before = path.get(i - 1);
                    check(Math.abs(before.x() - point.x()) + Math.abs(before.z() - point.z()) == 1, "cardinal steps");
                    check(Math.abs(before.halfHeight() - point.halfHeight()) <= 1, "slope exceeds half block");
                }
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    int x = point.x() + dx, z = point.z() + dz;
                    var shoulder = CityPlan.sample(seed, x, z);
                    check(shoulder.kind() != CityPlan.Kind.WATER && shoulder.kind() != CityPlan.Kind.SEA, "trail shoulder covers water");
                    check(trail.at(x, z) != null, "three-block-wide trail");
                }
                worstCut = Math.max(worstCut, natural.height() - Math.floorDiv(point.halfHeight(), 2));
                worstFill = Math.max(worstFill, Math.floorDiv(point.halfHeight() + 1, 2) - natural.height());
            }
        }
        System.out.println("PASS D21 mountain trail, 32 plan seeds; max centerline cut=" + worstCut + ", fill=" + worstFill);
    }

    private static void check(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
}
