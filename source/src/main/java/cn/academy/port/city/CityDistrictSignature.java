package cn.academy.port.city;

/** Distinct, compact public landmarks for ten otherwise similar district sample plazas. */
final class CityDistrictSignature {
    private CityDistrictSignature() {}
    static boolean has(int d) {
        return switch (d) { case 8, 9, 10, 11, 12, 13, 14, 16, 18, 19 -> true; default -> false; };
    }
    static boolean footprint(int d, int a, int b) {
        return has(d) && a >= 30 && a <= 42 && b >= (d == 11 ? 29 : 5) && b <= (d == 11 ? 41 : 17);
    }
    static String block(int d, int a, int b, int y) {
        if (!footprint(d,a,b) || y < 0 || y > 19) return null;
        int x = a - 36, z = b - (d == 11 ? 35 : 11);
        boolean corner = Math.abs(x) == 5 && Math.abs(z) == 5;
        switch (d) {
            case 8 -> { // Cherry arbor.
                if (corner && y <= 5) return "minecraft:cherry_log";
                if (y == 5 && (Math.abs(x) == 5 || Math.abs(z) == 5)) return "minecraft:cherry_planks";
                if (y >= 6 && y <= 7 && Math.abs(x)+Math.abs(z) <= 8) return "minecraft:cherry_leaves[persistent=true]";
                if (y == 4 && Math.abs(x) == 4 && Math.abs(z) == 5) return "minecraft:lantern[hanging=true,waterlogged=false]";
            }
            case 9 -> { // Open reading room.
                if (z == -5 && Math.abs(x) <= 4 && y >= 1 && y <= 4) return "minecraft:bookshelf";
                if (corner && y <= 6) return "academy:pillar_white";
                if (y == 6 && Math.abs(x) <= 5 && Math.abs(z) <= 5) return "academy:blue_panel_wall";
                if (y == 0 && z == 1 && Math.abs(x) <= 2) return "minecraft:oak_stairs[facing=south]";
            }
            case 10 -> { // Ring sculpture.
                if (x == 0 && z == 0 && y <= 4) return "academy:brushed_steel";
                if (z == 0 && y >= 4 && y <= 14) {
                    int dy = y - 9, r = x*x + dy*dy;
                    if (r >= 17 && r <= 27) return y == 14 ? "academy:neon_cyan" : "academy:brushed_steel";
                }
            }
            case 11 -> { // Market sign.
                if (x == 0 && z == -3 && y <= 12) return "academy:pillar_dark";
                if (z == -3 && Math.abs(x) <= 3 && y >= 8 && y <= 12) return "academy:led_screen";
                if (y == 5 && Math.abs(x) <= 5 && Math.abs(z) <= 3) return "minecraft:orange_concrete";
                if (y == 4 && Math.abs(x) == 4 && z == 2) return "academy:ceiling_light";
            }
            case 12 -> { // Layered science sculpture.
                if (x == 0 && z == 0 && y <= 13) return "academy:brushed_steel";
                if ((y == 4 || y == 8 || y == 12) && Math.abs(x) <= 3 && Math.abs(z) <= 3
                        && (Math.abs(x) == 3 || Math.abs(z) == 3)) return "academy:neon_cyan";
                if ((y == 4 || y == 8 || y == 12) && (x == 0 || z == 0) && Math.abs(x) <= 3 && Math.abs(z) <= 3) return "academy:brushed_steel";
                if (y >= 5 && y <= 11 && Math.abs(x) == 3 && Math.abs(z) == 3) return "academy:curtain_glass_clear";
            }
            case 13 -> { // White gateway.
                if (Math.abs(x) == 4 && z == 0 && y <= 10) return "academy:white_panel_wall";
                if (z == 0 && y == 10 && Math.abs(x) <= 4) return "academy:white_panel_wall";
                if (z == 0 && y == 12 && Math.abs(x) <= 2) return "academy:neon_cyan";
                if (z == 0 && y == 11 && Math.abs(x) == 2) return "academy:white_panel_wall";
                if (x == 0 && z == 0 && y == 0) return "minecraft:sea_lantern";
            }
            case 14 -> { // Stones and bamboo.
                if ((x == -3 && z == -2 || x == 2 && z == 3 || x == 4 && z == -4) && y == 0) return "minecraft:stone";
                if ((x == -4 && z == 3 || x == -3 && z == 3 || x == -4 && z == 4) && y <= 6) return "minecraft:bamboo_block";
                if (x == 3 && z == 2 && y <= 2) return "academy:pillar_white";
                if (x == 3 && z == 2 && y == 3) return "minecraft:lantern[hanging=false,waterlogged=false]";
            }
            case 16 -> { // Open community stage.
                if (corner && y <= 7) return "academy:pillar_white";
                if (y == 7 && Math.abs(x) <= 5 && Math.abs(z) <= 5) return "academy:blue_panel_wall";
                if (y == 6 && Math.abs(x) == 3 && Math.abs(z) == 3) return "academy:ceiling_light";
                if (z == -5 && Math.abs(x) <= 3 && y <= 2) return "academy:composite_floor";
            }
            case 18 -> { // Technical exhibition pylons.
                if (Math.abs(x) == 4 && z == 0 && y <= 12) return "academy:pillar_white";
                if (z == 0 && Math.abs(x) <= 4 && y == 12) return "academy:brushed_steel";
                if (x == 0 && z == 0 && y <= 1) return "academy:brushed_steel";
                if (x == 0 && z == 0 && y >= 2 && y <= 9) return "academy:neon_cyan";
                if (z == 0 && Math.abs(x) == 2 && y >= 3 && y <= 8) return "academy:curtain_glass_clear";
            }
            case 19 -> { // Illuminated stepped spire.
                int r = y < 3 ? 3 : y < 7 ? 2 : y < 11 ? 1 : 0;
                if (y <= 15 && Math.abs(x) <= r && Math.abs(z) <= r)
                    return y == 15 || y == 7 && Math.abs(x) == 1 && Math.abs(z) == 1
                            ? "academy:neon_cyan" : "academy:white_panel_wall";
            }
            default -> { }
        }
        return "minecraft:air";
    }
}
