package cn.academy.port.city;

/** Four small gardens inside the otherwise open D7 central plaza. */
final class CityD7CentralGardens {
    private static final int[][] CENTRES = {{-38, -35}, {-38, 68}, {54, -55}, {54, 55}};
    private CityD7CentralGardens() {}

    static Column at(long seed, int x, int z, CityPlan.Sample sample) {
        if (sample.district() != 7 || sample.kind() != CityPlan.Kind.PLAZA) return null;
        for (int[] center : CENTRES) {
            if (Math.abs(x - center[0]) > 9 || Math.abs(z - center[1]) > 9) continue;
            if (CitySampleRoute.at(x, z) != null || CityStreetProps.at(seed, x, z) != null
                    || CityDistrictLandmarks.at(seed, x, z) != null || CityTemplateSites.at(seed, x, z) != null
                    || CityNorthSouthTransit.overlapsLot(x, z, 1, 1)) return null;
            return new Column(x - center[0], z - center[1], center[0] > 0);
        }
        return null;
    }

    record Column(int dx, int dz, boolean cherry) {
        int top() { return 6; }
        String block(int y) {
            if (y < -1 || y > 6) return null;
            int ax = Math.abs(dx), az = Math.abs(dz);
            boolean walk = ax <= 1 || az <= 1;
            boolean border = ax == 9 || az == 9;
            boolean lamp = ax == 3 && az == 3;
            boolean bench = ax == 3 && az == 6;
            if (y == -1) return walk || border || lamp || bench ? "academy:plaza_tile" : "minecraft:grass_block";
            if (walk) return null;
            if (lamp && y <= 4) return y == 4 ? "minecraft:lantern[hanging=false,waterlogged=false]" : "academy:pillar_white";
            if (bench && y == 0) return "minecraft:oak_stairs[facing=" + (dx < 0 ? "east" : "west") + "]";
            int tx = Math.abs(ax - 6), tz = Math.abs(az - 6);
            if (tx == 0 && tz == 0 && y <= 4) return cherry ? "minecraft:cherry_log" : "minecraft:oak_log";
            if (y >= 3 && y <= 5 && tx <= 2 && tz <= 2 && tx + tz <= 3 || y == 6 && tx + tz <= 1)
                return cherry ? "minecraft:cherry_leaves[persistent=true]" : "minecraft:oak_leaves[persistent=true]";
            if (y == 0 && !border && Math.floorMod(dx * 17 + dz * 11, 19) == 0)
                return cherry ? "minecraft:allium" : "minecraft:azure_bluet";
            return null;
        }
    }
}
