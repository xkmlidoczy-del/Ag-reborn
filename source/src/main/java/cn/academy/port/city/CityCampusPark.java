package cn.academy.port.city;

/** A pedestrian garden between the D7 school and the existing school-link sidewalk. */
final class CityCampusPark {
    private static final int[][] TREES = {{-162, -39}, {-142, -39}, {-162, -28}, {-142, -28}};
    private CityCampusPark() {}

    static Column at(long seed, int x, int z, CityPlan.Sample sample) {
        if (x < -184 || x > -137 || z < -56 || z > -9 || !open(seed, x, z, sample)) return null;
        for (int[] tree : TREES) {
            int dx = Math.abs(x - tree[0]), dz = Math.abs(z - tree[1]);
            if (dx <= 2 && dz <= 2 && clearTree(seed, tree[0], tree[1]))
                return new Column(x, z, tree[0], tree[1], Kind.TREE);
        }
        if (x >= -153 && x <= -151 || z >= -48 && z <= -46 || z >= -20 && z <= -18)
            return new Column(x, z, x, z, Kind.PATH);
        if (x == -148 && (z == -44 || z == -22)) return new Column(x, z, x, z, Kind.LIGHT);
        if ((x == -155 || x == -149) && (z == -42 || z == -26))
            return new Column(x, z, x, z, Kind.BENCH);
        if ((x >= -168 && x <= -165 || x >= -139 && x <= -137) && (z >= -52 && z <= -50 || z >= -16 && z <= -14))
            return new Column(x, z, x, z, Kind.FLOWERS);
        return null;
    }

    private static boolean open(long seed, int x, int z, CityPlan.Sample sample) {
        return sample.district() == 7 && sample.kind() == CityPlan.Kind.PARK
                && !CitySampleRoute.schoolLink(x, z) && CityDistrictLandmarks.at(seed, x, z) == null
                && CityTemplateSites.at(seed, x, z) == null && CityNeighborhoodProps.at(seed, x, z) == null;
    }

    private static boolean clearTree(long seed, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (!open(seed, x + dx, z + dz, CityPlan.sample(seed, x + dx, z + dz))) return false;
        }
        return true;
    }

    private enum Kind { TREE, PATH, LIGHT, BENCH, FLOWERS }
    record Column(int x, int z, int anchorX, int anchorZ, Kind kind) {
        int top() { return switch (kind) { case TREE -> 6; case LIGHT -> 4; case BENCH, FLOWERS -> 0; case PATH -> -1; }; }
        String block(int y) {
            if (kind == Kind.PATH) return y == -1 ? "academy:plaza_tile" : null;
            if (kind == Kind.LIGHT) {
                if (y >= 0 && y < 4) return "academy:pillar_white";
                return y == 4 ? "academy:ceiling_light" : null;
            }
            if (kind == Kind.BENCH && y == 0) return "minecraft:oak_stairs[facing=" + (x == -155 ? "east" : "west") + "]";
            if (kind == Kind.FLOWERS && y == 0 && Math.floorMod(x + z, 2) == 0)
                return Math.floorMod(x + z, 4) == 0 ? "minecraft:azure_bluet" : "minecraft:allium";
            if (kind != Kind.TREE) return null;
            int dx = Math.abs(x - anchorX), dz = Math.abs(z - anchorZ);
            boolean cherry = anchorX == -142;
            if (dx == 0 && dz == 0 && y >= 0 && y <= 4) return cherry ? "minecraft:cherry_log" : "minecraft:oak_log";
            if (y >= 3 && y <= 5 && dx + dz <= 3 || y == 6 && dx + dz <= 1)
                return cherry ? "minecraft:cherry_leaves[persistent=true]" : "minecraft:oak_leaves[persistent=true]";
            return null;
        }
    }
}
