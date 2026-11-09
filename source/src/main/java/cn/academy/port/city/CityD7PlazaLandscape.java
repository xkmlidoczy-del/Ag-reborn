package cn.academy.port.city;

/** Connected lawns and shaded paths around D7's central plaza. */
final class CityD7PlazaLandscape {
    private static final int[][] TREES = {
            {36, -34}, {36, -74}, {73, -34}, {75, -75}, {85, -50}, {85, -85},
            {36, 34}, {36, 74}, {73, 34}, {75, 75}, {85, 50}, {85, 85}
    };
    private static final int[][] WEST_TREES = {
            {-91, 9}, {-78, 9}, {-91, 29}, {-78, 29},
            {-91, 62}, {-78, 62}, {-91, 75}, {-78, 75}
    };
    private CityD7PlazaLandscape() {}

    static Column at(long seed, int x, int z, CityPlan.Sample sample) {
        if (sample.district() == 7 && sample.kind() == CityPlan.Kind.PLAZA
                && x >= -95 && x <= -77 && z >= 2 && z <= 82) {
            // Leave the existing campus route, windmill and established street garden intact.
            if (CitySampleRoute.at(x, z) != null || CityStreetProps.at(seed, x, z) != null
                    || CityDistrictLandmarks.at(seed, x, z) != null
                    || CityD7CentralGardens.at(seed, x, z, sample) != null
                    || CityNorthSouthTransit.overlapsLot(x, z, 1, 1)) return null;
            return new Column(x, z);
        }
        int az = Math.abs(z);
        if (sample.district() != 7 || sample.kind() != CityPlan.Kind.PLAZA
                || x < 30 || x > 90 || az < 27 || az > 90) return null;
        // Garden paths and details retain priority, including their otherwise empty columns.
        if (CityD7CentralGardens.at(seed, x, z, sample) != null) return null;
        return new Column(x, z);
    }
    record Column(int x, int z) {
        int top() { return 6; }
        String block(int y) {
            if (y < -1 || y > 6) return null;
            if (x < 0) return west(y);
            int az = Math.abs(z);
            boolean path = x == 30 || x == 90 || az == 27 || az == 90
                    || x >= 53 && x <= 55 || az >= 54 && az <= 56;
            boolean lamp = (x == 50 || x == 58) && (az == 32 || az == 75);
            boolean bench = (x == 50 || x == 58) && (az == 40 || az == 80);
            if (y == -1) return path || lamp || bench ? "academy:plaza_tile" : "minecraft:grass_block";
            if (path) return null;
            if (lamp && y <= 4) return y == 4 ? "minecraft:lantern[hanging=false,waterlogged=false]" : "academy:pillar_white";
            if (bench && y == 0) return "minecraft:oak_stairs[facing=" + (x == 50 ? "east" : "west") + "]";
            for (int[] tree : TREES) {
                int dx = Math.abs(x - tree[0]), dz = Math.abs(z - tree[1]);
                if (dx > 2 || dz > 2) continue;
                int style = Math.floorMod(tree[0] + tree[1], 3);
                String log = style == 0 ? "minecraft:birch_log" : style == 1 ? "minecraft:cherry_log" : "minecraft:oak_log";
                String leaves = style == 0 ? "minecraft:birch_leaves[persistent=true]"
                        : style == 1 ? "minecraft:cherry_leaves[persistent=true]" : "minecraft:oak_leaves[persistent=true]";
                if (dx == 0 && dz == 0 && y <= 4) return log;
                if (y >= 3 && y <= 5 && dx + dz <= 3 || y == 6 && dx + dz <= 1) return leaves;
                return null;
            }
            if (y == 0 && Math.floorMod(x * 17 + z * 11, 43) == 0)
                return Math.floorMod(x + z, 2) == 0 ? "minecraft:azure_bluet" : "minecraft:dandelion";
            return null;
        }

        private String west(int y) {
            boolean path = x >= -83 && x <= -82 || z >= 26 && z <= 28 || z >= 54 && z <= 56;
            boolean lamp = x == -84 && (z == 12 || z == 30 || z == 45 || z == 60 || z == 78);
            boolean bench = x == -85 && (z == 17 || z == 68);
            if (y == -1) return path || lamp || bench ? "academy:plaza_tile" : "minecraft:grass_block";
            if (path) return null;
            if (lamp && y <= 4) return y == 4 ? "minecraft:lantern[hanging=false,waterlogged=false]" : "academy:pillar_white";
            if (bench && y == 0) return "minecraft:oak_stairs[facing=west]";
            for (int[] tree : WEST_TREES) {
                int dx = Math.abs(x - tree[0]), dz = Math.abs(z - tree[1]);
                if (dx > 2 || dz > 2) continue;
                String wood = tree[0] == -91 ? "birch" : "cherry";
                if (dx == 0 && dz == 0 && y <= 4) return "minecraft:" + wood + "_log";
                if (y >= 3 && y <= 5 && dx + dz <= 3 || y == 6 && dx + dz <= 1)
                    return "minecraft:" + wood + "_leaves[persistent=true]";
                return null;
            }
            if (y == 0 && Math.floorMod(x * 13 + z * 7, 59) == 0)
                return Math.floorMod(x + z, 2) == 0 ? "minecraft:azure_bluet" : "minecraft:allium";
            return null;
        }
    }
}
