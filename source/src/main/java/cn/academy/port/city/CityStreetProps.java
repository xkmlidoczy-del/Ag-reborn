package cn.academy.port.city;

/** Small, deterministic public-space details along the D7–central station–D15 walk. */
final class CityStreetProps {
    private static final String AIR = "minecraft:air";
    private static final String PAVING = "academy:plaza_tile";
    private static final int[][] WIND = {
            {-80, 20, 24, 1}, {-50, 72, 24, 1}, {-72, 158, 45, 0},
            {-58, 286, 45, 0}, {-88, 552, 24, 1}, {-42, 552, 24, 1}
    };
    private static final int[] LIGHT_Z = {22, 86, 150, 214, 278, 342, 454, 518, 568, 612, 650, 690};
    private static final int[][] TREES = {
            {-90, 40}, {-40, 40}, {-76, 46}, {-76, 166}, {-76, 294}, {-54, 350}, {-75, 462}, {-55, 526},
            {-76, 620}, {-54, 660}
    };
    private static final int[][] BENCHES = {
            {-72, 46}, {-58, 110}, {-72, 174}, {-58, 238},
            {-72, 302}, {-58, 350}, {-72, 462}, {-58, 526}, {-72, 574}, {-58, 574},
            {-72, 628}, {-58, 644}, {-72, 660}, {-58, 676}
    };

    private enum Kind { WIND, LIGHT, TREE, BENCH, GARDEN }

    private CityStreetProps() {}

    static Column at(long seed, int x, int z) {
        if (x < -97 || x > -33 || z < 0 || z > 699) return null;
        for (int[] site : WIND) {
            if (Math.abs(x - site[0]) <= site[3] && Math.abs(z - site[1]) <= site[3]) {
                return new Column(Kind.WIND, x, z, site[0], site[1], site[2]);
            }
        }
        for (int lightZ : LIGHT_Z) {
            if (z != lightZ) continue;
            for (int poleX : new int[]{-72, -58}) {
                int armEnd = poleX == -72 ? -70 : -60;
                if (x >= Math.min(poleX, armEnd) && x <= Math.max(poleX, armEnd)
                        && clearGround(seed, poleX, lightZ) && clearGround(seed, armEnd, lightZ)) {
                    return new Column(Kind.LIGHT, x, z, poleX, lightZ, 5);
                }
            }
        }
        for (int[] seat : BENCHES) {
            if (x == seat[0] && (z == seat[1] || z == seat[1] + 1) && clearGround(seed, x, z)) {
                return new Column(Kind.BENCH, x, z, seat[0], seat[1], 0);
            }
        }
        for (int[] tree : TREES) {
            if (Math.abs(x - tree[0]) <= 2 && Math.abs(z - tree[1]) <= 2 && clearTree(seed, tree[0], tree[1])) {
                return new Column(Kind.TREE, x, z, tree[0], tree[1], 6);
            }
        }
        if (z >= 32 && z <= 48 && ((x >= -95 && x <= -85) || (x >= -45 && x <= -35))) {
            return new Column(Kind.GARDEN, x, z, x < -65 ? -90 : -40, 40, 0);
        }
        return null;
    }

    private static boolean clearGround(long seed, int x, int z) {
        if (z >= 548 && z <= 591 && x >= -97 && x <= -33) return true;
        return switch (CityPlan.sample(seed, x, z).kind()) {
            case SIDEWALK, PLAZA, PARK, GROUND -> true;
            default -> false;
        };
    }

    private static boolean clearTree(long seed, int x, int z) {
        for (int dx = -2; dx <= 2; dx++) for (int dz = -2; dz <= 2; dz++) {
            if (!clearGround(seed, x + dx, z + dz)) return false;
        }
        return true;
    }

    record Column(Kind kind, int x, int z, int anchorX, int anchorZ, int height) {
        int top() {
            return kind == Kind.WIND && (x != anchorX || z != anchorZ) ? 0 : height;
        }

        String block(int y) {
            if (y < -1 || y > top()) return null;
            return switch (kind) {
                case WIND -> wind(y);
                case LIGHT -> light(y);
                case TREE -> tree(y);
                case BENCH -> bench(y);
                case GARDEN -> garden(y);
            };
        }

        private String wind(int y) {
            if (y == -1) return "academy:reinforced_concrete";
            if (x != anchorX || z != anchorZ) return "academy:white_panel_slab[type=bottom]";
            if (y == 0) return "academy:brushed_steel";
            if (y == height) return "academy:city_wind_head";
            return "academy:pillar_white";
        }

        private String light(int y) {
            if (y == -1) return PAVING;
            int end = anchorX == -72 ? -70 : -60;
            if (x == anchorX && y < 5) return "academy:pillar_dark";
            if (y == 5) return "academy:steel_slab[type=bottom]";
            if (x == end && y == 4) return "academy:ceiling_light";
            return AIR;
        }

        private String tree(int y) {
            int dx = Math.abs(x - anchorX), dz = Math.abs(z - anchorZ);
            if (y == -1) return anchorX == -90 || anchorX == -40 || (dx <= 1 && dz <= 1)
                    ? "minecraft:grass_block" : PAVING;
            if (dx == 0 && dz == 0 && y <= 3) return "minecraft:oak_log";
            if ((y >= 3 && y <= 5 && dx + dz <= 3) || (y == 6 && dx + dz <= 1)) {
                return "minecraft:oak_leaves[persistent=true]";
            }
            return AIR;
        }

        private String bench(int y) {
            if (y == -1) return PAVING;
            return anchorX == -72 ? "minecraft:oak_stairs[facing=east]" : "minecraft:oak_stairs[facing=west]";
        }

        private String garden(int y) {
            boolean edge = Math.abs(x - anchorX) == 5 || Math.abs(z - anchorZ) == 8;
            if (y == -1) return edge ? "academy:reinforced_concrete" : "minecraft:grass_block";
            if (edge) return "minecraft:oak_leaves[persistent=true]";
            return Math.floorMod(x * 13 + z * 7, 19) == 0 ? "minecraft:azure_bluet" : AIR;
        }
    }
}
