package cn.academy.port.city;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Deterministic woodland across D21, kept clear of the lake, trails and landmarks. */
final class CityMountainNature {
    private static final int CELL = 16, MARGIN = 5;
    private final Map<Long, Column> columns;
    private final List<Tree> trees;

    CityMountainNature(long seed, CityMountainTrail trail) {
        var built = new HashMap<Long, Column>();
        var anchors = new ArrayList<Tree>();
        var cells = new HashMap<Long, Tree>();
        for (int cz = Math.floorDiv(-1200, CELL); cz < Math.floorDiv(-400, CELL); cz++)
            for (int cx = Math.floorDiv(-2000, CELL); cx < Math.floorDiv(-1200, CELL); cx++) {
                long h = mix(seed ^ ((long) cx * 0x9e3779b97f4a7c15L) ^ ((long) cz * 0xc2b2ae3d27d4eb4fL));
                if (Long.remainderUnsigned(h, 100) >= 57) continue;
                int x = cx * CELL + (int) Long.remainderUnsigned(h >>> 9, CELL);
                int z = cz * CELL + (int) Long.remainderUnsigned(h >>> 20, CELL);
                boolean separated = true;
                for (int ox = -1; ox <= 1 && separated; ox++) for (int oz = -1; oz <= 1; oz++) {
                    var other = cells.get(key(cx + ox, cz + oz));
                    if (other != null && Math.max(Math.abs(x - other.x()), Math.abs(z - other.z())) <= 6) {
                        separated = false; break;
                    }
                }
                if (!separated) continue;
                var ground = CityPlan.sample(seed, x, z);
                if (ground.kind() != CityPlan.Kind.TERRAIN || ground.district() != 21 || ground.height() < 5) continue;
                int low = ground.height(), high = low;
                boolean clear = true;
                for (int dx = -MARGIN; dx <= MARGIN && clear; dx++) for (int dz = -MARGIN; dz <= MARGIN; dz++) {
                    int px = x + dx, pz = z + dz;
                    var part = CityPlan.sample(seed, px, pz);
                    if (part.kind() != CityPlan.Kind.TERRAIN || part.district() != 21
                            || trail.at(px, pz) != null || CityDistrictLandmarks.at(seed, px, pz) != null) {
                        clear = false; break;
                    }
                    if (Math.abs(dx) <= 3 && Math.abs(dz) <= 3) {
                        low = Math.min(low, part.height()); high = Math.max(high, part.height());
                    }
                }
                if (!clear || high - low > 5) continue;
                boolean spruce = Long.remainderUnsigned(h >>> 30, 4) != 0;
                int height = (spruce ? 7 : 6) + (int) Long.remainderUnsigned(h >>> 36, 3);
                Tree tree = new Tree(x, z, ground.height(), height, spruce);
                anchors.add(tree);
                cells.put(key(cx, cz), tree);
                for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
                    int px = x + dx, pz = z + dz;
                    int natural = CityPlan.sample(seed, px, pz).height();
                    built.putIfAbsent(key(px, pz), new Column(tree, dx, dz, natural));
                }
            }
        columns = Map.copyOf(built);
        trees = List.copyOf(anchors);
    }

    Column at(int x, int z) {
        if (x < -2000 || x >= -1200 || z < -1200 || z >= -400) return null;
        return columns.get(key(x, z));
    }
    List<Tree> trees() { return trees; }
    record Tree(int x, int z, int base, int height, boolean spruce) {}
    record Column(Tree tree, int dx, int dz, int naturalHeight) {
        int top() { return tree.base() + tree.height(); }
        String block(int y) {
            if (y < naturalHeight || y < tree.base() || y > top()) return null;
            int level = y - tree.base();
            if (dx == 0 && dz == 0 && level < tree.height())
                return tree.spruce() ? "minecraft:spruce_log" : "minecraft:birch_log";
            int radius;
            if (tree.spruce()) {
                if (level < 2) return null;
                radius = level >= tree.height() - 1 ? 1 : level >= tree.height() - 3 ? 2 : 3;
                if (level == tree.height()) radius = 0;
            } else {
                if (level < tree.height() - 3) return null;
                radius = level == tree.height() ? 1 : 2;
            }
            if (Math.abs(dx) > radius || Math.abs(dz) > radius
                    || radius > 0 && Math.abs(dx) == radius && Math.abs(dz) == radius) return null;
            return tree.spruce() ? "minecraft:spruce_leaves[persistent=true]" : "minecraft:birch_leaves[persistent=true]";
        }
    }

    private static long key(int x, int z) { return ((long) x << 32) ^ (z & 0xffffffffL); }
    private static long mix(long x) {
        x ^= x >>> 30; x *= 0xbf58476d1ce4e5b9L;
        x ^= x >>> 27; x *= 0x94d049bb133111ebL;
        return x ^ x >>> 31;
    }
}
