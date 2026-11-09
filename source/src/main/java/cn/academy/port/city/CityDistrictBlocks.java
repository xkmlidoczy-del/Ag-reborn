package cn.academy.port.city;

/** Low-rise, use-specific exterior blocks replace a subset of ordinary tower lots. */
final class CityDistrictBlocks {
    private static final String AIR = "minecraft:air", PAVE = "academy:plaza_tile";
    private static final String WHITE = "academy:white_panel_wall", BLUE = "academy:blue_panel_wall";
    private static final String DARK = "academy:dark_panel_wall", STEEL = "academy:brushed_steel";
    private static final String GLASS = "academy:curtain_glass_clear", BLUE_GLASS = "academy:curtain_glass_blue";
    private CityDistrictBlocks() {}

    static Column at(long seed, int x, int z) {
        return at(seed, x, z, CityTemplateSites.at(seed, x, z));
    }
    static Column at(long seed, int x, int z, CityTemplateSites.Site site) {
        if (site == null || site.id().equals("grand_complex")) return null;
        int district = CityPlan.district(x, z);
        long h = seed ^ ((long) site.originX() * 0x9e3779b97f4a7c15L)
                ^ ((long) site.originZ() * 0xc2b2ae3d27d4eb4fL) ^ 0x4f1bbcdc6765a219L;
        h ^= h >>> 30; h *= 0xbf58476d1ce4e5b9L; h ^= h >>> 27; h *= 0x94d049bb133111ebL; h ^= h >>> 31;
        // Keep the commercial centre predominantly high-rise.
        if (Long.remainderUnsigned(h, district == 15 ? 6 : 4) != 0) return null;
        return new Column(x - site.originX(), z - site.originZ(), CityDistrictZoning.at(district, x, z),
                (int) Long.remainderUnsigned(h >>> 8, 3));
    }

    record Column(int a, int b, CityDistrictZoning.Use use, int variant) {
        int top() { return switch (use) {
            case INDUSTRIAL -> 23; case SCIENCE -> 17; case CIVIC -> 17; case ACADEMIC -> 16;
            case LEISURE -> 15; case RESIDENTIAL -> 13; case COMMERCIAL -> 10; case SPORTS -> 11; case GREEN -> 9;
        }; }

        String block(int y) {
            if (y < -1) return null;
            if (y == -1) return ground();
            if (y > top()) return AIR;
            String forecourt = forecourt(y);
            if (forecourt != null) return forecourt;
            String result = switch (use) {
                case CIVIC -> civic(y); case ACADEMIC -> academic(y); case SCIENCE -> science(y);
                case COMMERCIAL -> shops(y, false); case RESIDENTIAL -> houses(y);
                case INDUSTRIAL -> industrial(y); case LEISURE -> shops(y, true);
                case SPORTS -> sports(y); case GREEN -> park(y);
            };
            return result == null ? AIR : result;
        }

        private String ground() {
            if (use == CityDistrictZoning.Use.GREEN)
                return Math.abs(a - 23) <= 1 || Math.abs(b - 23) <= 1 ? PAVE : "minecraft:grass_block";
            if ((use == CityDistrictZoning.Use.ACADEMIC || use == CityDistrictZoning.Use.RESIDENTIAL)
                    && between(b, 1, 6) && (between(a, 2, 19) || between(a, 28, 45))) return "minecraft:grass_block";
            if (use == CityDistrictZoning.Use.SPORTS && between(a, 4, 43) && between(b, 28, 43))
                return a == 4 || a == 43 || b == 28 || b == 43 || a == 23
                        ? "minecraft:white_concrete" : "minecraft:green_concrete";
            if ((use == CityDistrictZoning.Use.ACADEMIC || use == CityDistrictZoning.Use.RESIDENTIAL)
                    && between(a, 4, 43) && between(b, 34, 43)) return "minecraft:grass_block";
            if (use == CityDistrictZoning.Use.INDUSTRIAL && between(a, 4, 43) && between(b, 34, 43))
                return b == 34 && Math.floorMod(a, 4) < 2 ? "minecraft:yellow_concrete" : "academy:reinforced_concrete";
            return PAVE;
        }

        private String forecourt(int y) {
            if (b < 1 || b > 6 || use == CityDistrictZoning.Use.GREEN) return null;
            boolean living = use == CityDistrictZoning.Use.ACADEMIC || use == CityDistrictZoning.Use.RESIDENTIAL;
            if (living) {
                int dx = Math.min(Math.abs(a - 7), Math.abs(a - 40)), dz = Math.abs(b - 3);
                if (dx == 0 && dz == 0 && y <= 3) return "minecraft:oak_log";
                if (y >= 3 && y <= 5 && dx <= 2 && dz <= 2 && dx + dz <= 3 || y == 6 && dx + dz <= 1)
                    return "minecraft:oak_leaves[persistent=true]";
            }
            if ((a == 3 || a == 44) && b == 3 && y <= 3)
                return y == 3 ? "minecraft:lantern[hanging=false,waterlogged=false]" : "academy:pillar_white";
            if ((a == 15 || a == 32) && b == 4 && y == 0 && use != CityDistrictZoning.Use.INDUSTRIAL)
                return "minecraft:oak_stairs[facing=south]";
            if ((a == 11 || a == 36) && b == 3 && y == 0
                    && (use == CityDistrictZoning.Use.COMMERCIAL || use == CityDistrictZoning.Use.LEISURE))
                return "minecraft:potted_allium";
            return null;
        }

        private String civic(int y) {
            if (between(a, 5, 42) && between(b, 7, 9) && y == 8) return WHITE;
            if (b == 7 && (a == 6 || a == 12 || a == 18 || a == 29 || a == 35 || a == 41) && y <= 7)
                return "academy:pillar_white";
            return shell(y, 4, 9, 43, 30, 16, WHITE, BLUE_GLASS, BLUE, 23, 2);
        }
        private String academic(int y) {
            if (b == 7 && between(a, 18, 29) && y == 5) return BLUE;
            if ((a == 8 || a == 39) && b == 38 && y <= 3)
                return y == 3 ? "minecraft:lantern[hanging=false,waterlogged=false]" : "academy:pillar_white";
            return shell(y, 4, 8, 43, 29, 15, WHITE, BLUE_GLASS, BLUE, 23, 2);
        }
        private String science(int y) {
            if (between(a, 21, 26) && between(b, 16, 20) && between(y, 8, 11))
                return y == 8 || y == 11 ? STEEL : "academy:curtain_glass_teal";
            String left = shell(y, 4, 8, 20, 28, 15, WHITE, "academy:curtain_glass_teal", STEEL, 12, 2);
            return left != null ? left : shell(y, 27, 8, 43, 28, 15, WHITE, "academy:curtain_glass_teal", STEEL, 35, 2);
        }
        private String houses(int y) {
            for (int i = 0; i < 3; i++) {
                int start = 3 + i * 15;
                String wall = switch ((variant + i) % 3) {
                    case 0 -> "minecraft:white_concrete"; case 1 -> "minecraft:light_gray_concrete"; default -> "minecraft:terracotta";
                };
                int height = (variant + i) % 3 == 0 ? 13 : 11;
                String house = shell(y, start, 8, start + 11, 27, height, wall, GLASS, BLUE, start + 5, 1);
                if (house != null) return house;
            }
            return null;
        }
        private String shops(int y, boolean leisure) {
            if (between(b, 5, 7) && between(a, 3, 44) && y == 5)
                return leisure ? "academy:neon_magenta" : "academy:white_panel_slab[type=bottom]";
            if (b == 8 && between(a, 4, 43) && y == 7)
                return leisure ? "academy:neon_magenta" : "academy:led_screen";
            for (int i = 0; i < 3; i++) {
                int start = 3 + i * 15;
                String wall = switch ((i + variant) % 3) {
                    case 0 -> "minecraft:orange_concrete"; case 1 -> "minecraft:cyan_concrete"; default -> "minecraft:light_gray_concrete";
                };
                String shop = shell(y, start, 8, start + 11, 26, leisure ? 14 : 9,
                        leisure ? DARK : wall, "academy:curtain_glass_clear", leisure ? "academy:neon_magenta" : WHITE, start + 5, 1);
                if (shop != null) return shop;
            }
            return null;
        }
        private String industrial(int y) {
            if (between(a, 34, 37) && between(b, 13, 16) && y <= 22)
                return y >= 20 ? "minecraft:red_concrete" : "minecraft:gray_concrete";
            if (b == 35 && (a == 9 || a == 38) && y <= 2) return y == 2 ? "academy:neon_amber" : STEEL;
            return shell(y, 4, 8, 43, 29, 10, "minecraft:gray_concrete", "academy:curtain_glass_smoke", STEEL, 23, 3);
        }
        private String sports(int y) {
            if ((a == 6 || a == 41) && (b == 32 || b == 39) && y <= 3) return WHITE;
            if ((a == 6 || a == 41) && between(b, 32, 39) && y == 3) return WHITE;
            return shell(y, 4, 6, 43, 24, 10, WHITE, BLUE_GLASS, "minecraft:lime_concrete", 23, 2);
        }
        private String park(int y) {
            boolean post = (a == 12 || a == 35) && (b == 12 || b == 35);
            if (post && y <= 5) return WHITE;
            if (between(a, 12, 35) && between(b, 12, 35) && y == 6) return "academy:white_panel_slab[type=bottom]";
            if ((a == 16 || a == 31) && (b == 16 || b == 31) && y == 5) return "academy:ceiling_light";
            int dx = Math.min(Math.abs(a - 6), Math.abs(a - 41));
            int dz = Math.min(Math.abs(b - 7), Math.abs(b - 41));
            if (dx == 0 && dz == 0 && y <= 4) return "minecraft:oak_log";
            if (y >= 3 && y <= 5 && dx <= 2 && dz <= 2 && dx + dz <= 3 || y == 6 && dx + dz <= 1)
                return "minecraft:oak_leaves[persistent=true]";
            return null;
        }
        /** A hollow exterior with a ground-level north entrance. */
        private String shell(int y, int x0, int z0, int x1, int z1, int height,
                             String wall, String glass, String accent, int door, int halfDoor) {
            if (!between(a, x0, x1) || !between(b, z0, z1) || y > height) return null;
            if (y == height) return "academy:reinforced_concrete";
            boolean edge = a == x0 || a == x1 || b == z0 || b == z1;
            if (!edge) return AIR;
            if (b == z0 && Math.abs(a - door) <= halfDoor && y <= 2) return AIR;
            if (y > 0 && y % 5 == 0) return accent;
            int along = a == x0 || a == x1 ? b - z0 : a - x0;
            if (y >= 2 && y <= 4 && Math.floorMod(along, 6) >= 2 && Math.floorMod(along, 6) <= 4) return glass;
            return wall;
        }
    }
    private static boolean between(int value, int low, int high) { return value >= low && value <= high; }
}
