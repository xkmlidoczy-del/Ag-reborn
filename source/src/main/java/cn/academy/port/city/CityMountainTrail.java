package cn.academy.port.city;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** A terrain-following pedestrian trail between the D21 observatory and dam. */
final class CityMountainTrail {
    private static final String AIR = "minecraft:air", STONE = "minecraft:stone";
    private static final String PAVING = "minecraft:stone_bricks", SLAB = "minecraft:stone_brick_slab[type=bottom]";
    private final List<Point> centerline;
    private final List<Light> lights;
    private final List<Guard> guards;
    private final Map<Long, Column> columns;

    CityMountainTrail(long seed) {
        var plan = layout();
        int count = plan.size();
        int[] lower = new int[count], upper = new int[count], height = new int[count];
        for (int i = 0; i < count; i++) {
            var point = plan.get(i);
            int target = 2 * CityPlan.sample(seed, point.x(), point.z()).height();
            upper[i] = i == 0 ? target : Math.min(target, upper[i - 1] + 1);
            lower[i] = i == 0 ? target : Math.max(target, lower[i - 1] - 1);
        }
        for (int i = count - 2; i >= 0; i--) {
            upper[i] = Math.min(upper[i], upper[i + 1] + 1);
            lower[i] = Math.max(lower[i], lower[i + 1] - 1);
        }
        // Both envelopes change by at most half a block per step. Their midpoint
        // balances cuts and fills, while the bounds meet both fixed entrances.
        for (int i = 0; i < count; i++) {
            int remaining = count - 1 - i;
            int min = Math.max(186 - i, 16 - remaining);
            int max = Math.min(186 + i, 16 + remaining);
            height[i] = Math.max(min, Math.min(max, (lower[i] + upper[i]) / 2));
        }

        var points = new ArrayList<Point>(count);
        var built = new HashMap<Long, Column>();
        // Give centerline cells priority over shoulders at bends.
        for (int i = 0; i < count; i++) {
            var p = plan.get(i);
            points.add(new Point(p.x(), p.z(), height[i]));
            built.put(key(p.x(), p.z()), column(seed, p.x(), p.z(), height[i], false, null));
        }
        for (var p : points) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int x = p.x() + dx, z = p.z() + dz;
            built.putIfAbsent(key(x, z), column(seed, x, z, p.halfHeight(), false, null));
        }
        var fixtures = new ArrayList<Light>();
        for (int target = 12; target < count - 12; target += 12) {
            boolean placed = false;
            // Avoid corners and keep fixtures outside the walkable three-block path.
            for (int adjustment : new int[]{0, 1, -1, 2, -2, 3, -3}) {
                int i = target + adjustment;
                var p = points.get(i);
                var before = points.get(i - 1);
                var after = points.get(i + 1);
                boolean vertical = before.x() == after.x();
                boolean horizontal = before.z() == after.z();
                if (!vertical && !horizontal) continue;
                for (int side : new int[]{1, -1}) {
                    int x = p.x() + (vertical ? 2 * side : 0);
                    int z = p.z() + (horizontal ? 2 * side : 0);
                    if (built.containsKey(key(x, z)) || CityDistrictLandmarks.at(seed, x, z) != null) continue;
                    var sample = CityPlan.sample(seed, x, z);
                    if (sample.kind() != CityPlan.Kind.TERRAIN) continue;
                    var light = column(seed, x, z, p.halfHeight(), true, null);
                    built.put(key(x, z), light);
                    fixtures.add(new Light(x, light.floorY() + 3, z, i));
                    placed = true;
                    break;
                }
                if (placed) break;
            }
        }
        var barriers = new ArrayList<Guard>();
        for (int i = 1; i < count - 1; i++) {
            var p = points.get(i);
            var before = points.get(i - 1);
            var after = points.get(i + 1);
            boolean vertical = before.x() == after.x();
            boolean horizontal = before.z() == after.z();
            if (!vertical && !horizontal) continue;
            for (int side : new int[]{-1, 1}) {
                int x = p.x() + (vertical ? 2 * side : 0);
                int z = p.z() + (horizontal ? 2 * side : 0);
                if (built.containsKey(key(x, z)) || CityDistrictLandmarks.at(seed, x, z) != null) continue;
                var sample = CityPlan.sample(seed, x, z);
                if (sample.kind() != CityPlan.Kind.TERRAIN || p.halfHeight() / 2.0 - sample.height() < 3) continue;
                String axis = vertical ? "north=true,south=true" : "east=true,west=true";
                var barrier = column(seed, x, z, p.halfHeight(), false, axis);
                built.put(key(x, z), barrier);
                barriers.add(new Guard(x, barrier.floorY() + 1, z, i));
            }
        }
        centerline = List.copyOf(points);
        lights = List.copyOf(fixtures);
        guards = List.copyOf(barriers);
        columns = Map.copyOf(built);
    }

    private static Column column(long seed, int x, int z, int halfHeight, boolean light, String guardAxis) {
        return new Column(halfHeight, CityPlan.sample(seed, x, z).height(), light, guardAxis);
    }

    Column at(int x, int z) {
        if (x < -1766 || x > -1318 || z < -954 || z > -796) return null;
        return columns.get(key(x, z));
    }

    List<Point> centerline() { return centerline; }
    List<Light> lights() { return lights; }
    List<Guard> guards() { return guards; }

    record Point(int x, int z, int halfHeight) {}
    record Light(int x, int y, int z, int pathIndex) {}
    record Guard(int x, int y, int z, int pathIndex) {}

    record Column(int halfHeight, int naturalHeight, boolean light, String guardAxis) {
        private int floorY() { return Math.floorDiv(halfHeight - 1, 2); }
        int top() { return Math.max(naturalHeight + 2, floorY() + 3); }
        String block(int y) {
            if (y < -1 || y > top()) return null;
            if (y < floorY()) return STONE;
            if (y == floorY()) return light || guardAxis != null || (halfHeight & 1) == 0 ? PAVING : SLAB;
            if (light && y <= floorY() + 2) return "academy:pillar_dark";
            if (light && y == floorY() + 3) return "minecraft:lantern[hanging=false,waterlogged=false]";
            if (guardAxis != null && y == floorY() + 1) return "minecraft:spruce_fence[" + guardAxis + ",waterlogged=false]";
            return AIR;
        }
    }

    private record Position(int x, int z) {}

    private static List<Position> layout() {
        var points = new ArrayList<Position>();
        points.add(new Position(-1752, -804));
        segment(points, -1752, -798);
        segment(points, -1764, -798);
        segment(points, -1764, -912);
        segment(points, -1736, -912);
        int x = -1736, z = -912;
        // Switchbacks slow the descent as the route crosses the steep ridge.
        for (int i = 0; i < 13; i++) {
            z = z == -912 ? -952 : -912;
            segment(points, x, z);
            x += 12;
            segment(points, x, z);
        }
        if (z != -912) segment(points, x, -912);
        segment(points, -1320, -912);
        segment(points, -1320, -909);
        return points;
    }

    private static void segment(List<Position> points, int x, int z) {
        var from = points.get(points.size() - 1);
        int dx = Integer.signum(x - from.x()), dz = Integer.signum(z - from.z());
        if (dx != 0 && dz != 0) throw new IllegalArgumentException("trail segments must be cardinal");
        for (int px = from.x(), pz = from.z(); px != x || pz != z;) {
            px += dx;
            pz += dz;
            points.add(new Position(px, pz));
        }
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
}
