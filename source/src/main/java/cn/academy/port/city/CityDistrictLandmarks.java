package cn.academy.port.city;

/** Exteriors and open public spaces for the individual district samples. */
final class CityDistrictLandmarks {
    private static final String AIR = "minecraft:air", WHITE = "academy:white_panel_wall";
    private static final String GLASS = "academy:curtain_glass_clear", PAVE = "academy:plaza_tile";
    private static final String DARK = "academy:dark_panel_wall", STEEL = "academy:brushed_steel";
    private static final int[] SERVICE_FLOORS = {8, 16, 24};
    // Distribute accessible tall nodes over the maintenance grid. Each centre is
    // an existing wind mast; the original central tower stays in the same place.
    private static final int[][] D22_TOWERS = {
            {-1068, -1860}, {-588, -1860}, {-1068, -1380}, {-588, -1380}, {-828, -1404}
    };
    private static final java.util.Map<Long, int[]> CROWD_ANCHORS = crowdAnchors();

    private CityDistrictLandmarks() {}

    private static java.util.Map<Long, int[]> crowdAnchors() {
        var anchors = new java.util.HashMap<Long, int[]>();
        for (int row = 0; row < 5; row++) for (int col = 0; col < 5; col++) {
            int left = -CityPlan.HALF + col * CityPlan.CELL, north = -CityPlan.HALF + row * CityPlan.CELL;
            int d = CityPlan.district(left + 400, north + 400);
            if (d < 1 || d > 20 || d == 15) continue;
            int x = left + (d == 7 ? 216 : 152) + 24, z = north + (d == 7 ? 280 : 152) + 24;
            int cellX = Math.floorDiv(x, CityCrowdPlan.NEIGHBORHOOD), cellZ = Math.floorDiv(z, CityCrowdPlan.NEIGHBORHOOD);
            anchors.put(((long) cellX << 32) ^ (cellZ & 0xffffffffL), new int[]{x, z});
        }
        return java.util.Map.copyOf(anchors);
    }

    static int[] crowdAnchor(int cellX, int cellZ) {
        return CROWD_ANCHORS.get(((long) cellX << 32) ^ (cellZ & 0xffffffffL));
    }

    static boolean ownsLot(int x, int z) {
        int district = CityPlan.district(x, z);
        if (district < 1 || district > 20 || district == 15) return false;
        int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL);
        int lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
        int ox = district == 7 ? 216 : 152, oz = district == 7 ? 280 : 152;
        boolean primary = lx >= ox && lx < ox + 48 && lz >= oz && lz < oz + 48;
        // D7 adds a second school, a library and an adjacent sports ground.
        return primary || district == 7 && (lx >= 152 && lx < 200 && lz >= 280 && lz < 328
                || lz >= 344 && lz < 392 && (lx >= 88 && lx < 136 || lx >= 152 && lx < 200));
    }

    static Column at(long seed, int x, int z) {
        if (ownsLot(x, z)) {
            int d = CityPlan.district(x, z);
            int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL);
            int lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
            boolean southCampus = d == 7 && lz >= 344;
            boolean sports = southCampus && lx < 136;
            boolean secondSchool = d == 7 && !southCampus && lx < 200;
            int ox = sports ? 88 : d == 7 && !southCampus && !secondSchool ? 216 : 152;
            int a = lx - ox;
            int b = lz - (southCampus ? 344 : d == 7 ? 280 : 152);
            return new Column(southCampus ? sports ? 41 : 40 : secondSchool ? 32 : d, a, b, x, z, 0);
        }
        if (x >= -25 && x <= 25 && z >= -25 && z <= 25 && CityPlan.district(x, z) == 7)
            return new Column(31, 0, 0, x, z, 0);
        if (x >= -1324 && x <= -1317 && z >= -909 && z <= -614)
            return new Column(21, 0, 0, x, z, 0);
        if (x >= -1316 && x <= -1308 && (z >= -825 && z <= -815 || z >= -705 && z <= -695))
            return new Column(38, 0, 0, x, z, 0);
        if (x >= -1754 && x <= -1751 && z >= -818 && z <= -804)
            return new Column(27, 0, 0, x, z, 0);
        if (x >= -1760 && x < -1744 && z >= -834 && z < -818)
            return new Column(25, x + 1760, z + 834, x, z, 0);
        if ((x >= -801 && x <= -799 && z >= -1549 && z <= -1212)
                || (x == -797 && d22ApproachLight(z)))
            return new Column(30, 0, 0, x, z, 0);
        for (int[] centre : D22_TOWERS) {
            int dx = x - centre[0], dz = z - centre[1];
            if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4)
                return new Column(39, dx, dz, x, z,
                        dx == 0 && dz == 0 ? CityPlan.sample(seed, x, z).height() : 0);
        }
        if (d22ScaffoldColumn(x, z)) return new Column(22, 0, 0, x, z, 0);
        // Two terminal wings adjoin the central hall without entering the rail or apron corridors.
        if (z >= -1864 && z <= -1813 && x >= 1544 && x <= 1591)
            return new Column(33, x - 1544, z + 1864, x, z, 0);
        if (z >= -1864 && z <= -1813 && x >= 1652 && x <= 1699)
            return new Column(34, x - 1652, z + 1864, x, z, 0);
        if (x >= 1390 && x <= 1479 && z >= -1780 && z <= -1729)
            return new Column(36, x - 1390, z + 1780, x, z, 0);
        if (x >= 1592 && x < 1652 && z >= -1868 && z < -1808)
            return new Column(23, x - 1592, z + 1868, x, z, 0);
        // Forecourt takes priority where the footpath overlaps the entrance canopy.
        if (x >= 1596 && x <= 1647 && z >= -1808 && z <= -1797)
            return new Column(29, 0, 0, x, z, 0);
        if ((x >= 1212 && x <= 1625 && z >= -1796 && z <= -1784)
                || (x >= 1617 && x <= 1625 && z >= -1808 && z <= -1790)
                || (x >= 1582 && x <= 1594 && z >= -1800 && z <= -1797))
            return new Column(28, 0, 0, x, z, 0);
        if ((x >= 1563 && x <= 1570 && z >= -1812 && z <= -1797)
                || (x >= 1671 && x <= 1678 && z >= -1812 && z <= -1802)
                || (x >= 1648 && x <= 1678 && z >= -1804 && z <= -1802))
            return new Column(35, 0, 0, x, z, 0);
        if (x >= 1480 && x <= 1487 && z >= -1752 && z <= -1746)
            return new Column(37, 0, 0, x, z, 0);
        if (x >= 1760 && x < 1824 && z >= -1900 && z < -1836)
            return new Column(26, x - 1760, z + 1900, x, z, 0);
        if (x >= 1252 && x < 1948 && z >= -1688 && z < -1628)
            return new Column(24, 0, 0, x, z, 0);
        return null;
    }

    private static boolean d22ApproachLight(int z) {
        return z == -1530 || z == -1480 || z == -1430 || z == -1380
                || z == -1330 || z == -1280 || z == -1230;
    }

    private static boolean d22ScaffoldColumn(int x, int z) {
        if (CityPlan.district(x, z) != 22) return false;
        int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL);
        int lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
        if (lx < 12 || lx >= 788 || lz < 12 || lz >= 788) return false;
        int rx = Math.floorMod(x, 24), rz = Math.floorMod(z, 24);
        boolean service = x >= -1187 && x <= -415 && z >= -1982 && z <= -1234
                && (rx == 13 && rz == 12 || rx >= 14 && rx <= 17 && rz >= 10 && rz <= 14);
        boolean bridge = x >= -1188 && x <= -415 && z >= -1982 && z <= -1234
                && (rx >= 10 && rx <= 14 || rz >= 10 && rz <= 14);
        return rx == 12 || rz == 12 || service || bridge;
    }

    record Column(int type, int a, int b, int x, int z, int unused) {
        int top() { return switch (type) { case 2 -> 10; case 6 -> 42; case 17 -> 45; case 20 -> 8; case 21, 38, 41 -> 11; case 22, 30, 32 -> 32; case 23 -> 25; case 24, 37 -> -1; case 25 -> 105; case 26 -> 52; case 27 -> 95; case 28 -> 4; case 29 -> 5; case 31 -> 6; case 33, 34 -> 18; case 35 -> 3; case 36 -> 24; case 39 -> 122; case 40 -> 21; default -> 28; }; }

        String block(int y) {
            if (type == 21) return dam(y);
            if (type == 38) return reservoirLookout(y);
            if (type == 22) return scaffold(y);
            if (type == 39) return d22Tower(y);
            if (type == 23) return terminal(y);
            if (type == 33 || type == 34) return terminalWing(y);
            if (type == 35) return y == -1 ? PAVE : y >= 0 && y <= 3 ? AIR : null;
            if (type == 36) return airportHangar(y);
            if (type == 37) return y == -1 ? z == -1749 ? "minecraft:yellow_concrete" : "minecraft:light_gray_concrete" : null;
            if (type == 25) return observatory(y);
            if (type == 26) return launchSite(y);
            if (type == 27) return observatoryPath(y);
            if (type == 28) return airportApproach(y);
            if (type == 29) return airportForecourt(y);
            if (type == 30) return d22Approach(y);
            if (type == 31) return d7Plaza(y);
            if (type == 24) return runway(y);
            if (y < -1) return null;
            if (y == -1) return surface();
            return switch (type) {
                case 1 -> civic(y);
                case 2 -> testGround(y);
                case 3 -> exhibition(y);
                case 4 -> foodStreet(y);
                case 5, 7 -> school(y);
                case 32 -> secondSchool(y);
                case 40 -> campusLibrary(y);
                case 41 -> campusSports(y);
                case 6 -> entertainment(y);
                case 17 -> industrial(y);
                case 20 -> stadium(y);
                case 8, 9, 10, 11, 12, 13, 14, 16, 18, 19 -> neighborhood(y);
                default -> AIR;
            };
        }

        private String surface() {
            if (type == 40) {
                if (a >= 21 && a <= 26 && b <= 8) return PAVE;
                if (b <= 5 && (a < 18 || a > 29) || b >= 38) return "minecraft:grass_block";
                return PAVE;
            }
            if (type == 41) {
                if (a >= 21 && a <= 26 && b <= 5) return PAVE;
                // Flush in-ground lights supplement high corner lamps at field level.
                if ((a == 14 || a == 33) && (b == 14 || b == 33) || a == 23 && b == 23)
                    return "minecraft:sea_lantern";
                if (a >= 4 && a <= 43 && b >= 4 && b <= 43) {
                    if (a <= 8 || a >= 39 || b <= 8 || b >= 39)
                        return a == 6 || a == 41 || b == 6 || b == 41 ? "minecraft:white_concrete" : "minecraft:red_concrete";
                    return a == 9 || a == 38 || b == 9 || b == 38 || a == 23 || a == 24
                            ? "minecraft:white_concrete" : "minecraft:green_concrete";
                }
                return PAVE;
            }
            if (CityDistrictSignature.footprint(type, a, b)) return PAVE;
            if (type == 32 && a >= 7 && a <= 18 && b >= 33 && b <= 44)
                return a == 7 || a == 18 || b == 33 || b == 44 || b == 39 ? "minecraft:white_concrete" : "minecraft:green_concrete";
            if (type == 7 || type == 5 || type == 32) return b >= 31 && a >= 3 && a <= 44 ? "minecraft:grass_block" : PAVE;
            if (type == 20) {
                double r = Math.pow((a - 23.5) / 19, 2) + Math.pow((b - 23.5) / 14, 2);
                return r < .56 ? "minecraft:grass_block" : r < 1 ? "minecraft:red_concrete" : PAVE;
            }
            if (type == 2 && a > 5 && a < 42 && b > 5 && b < 42) return "academy:asphalt";
            if (type == 8 || type == 14 || type == 18) {
                return (a > 3 && a < 44 && b > 3 && b < 44 && Math.abs(a - 23) > 2 && Math.abs(b - 23) > 2)
                        ? "minecraft:grass_block" : PAVE;
            }
            if (type == 10 || type == 13 || type == 19) {
                int radius = (a - 23) * (a - 23) + (b - 23) * (b - 23);
                if (radius < 25) return "minecraft:water[level=0]";
                if (radius <= 49) return WHITE;
            }
            return PAVE;
        }

        private String civic(int y) {
            boolean building = a >= 6 && a <= 41 && b >= 7 && b <= 37;
            if (!building || y > 24) return AIR;
            if (y == 24 || (y > 0 && y % 6 == 0)) return WHITE;
            boolean wall = a == 6 || a == 41 || b == 7 || b == 37;
            if (!wall) return AIR;
            if (b == 37 && a >= 21 && a <= 26 && y <= 3) return AIR;
            boolean pier = (a == 6 || a == 41) ? Math.floorMod(b - 7, 5) == 0 : Math.floorMod(a - 6, 5) == 0;
            if (pier) return "academy:pillar_white";
            return y % 6 <= 3 ? GLASS : WHITE;
        }

        private String testGround(int y) {
            boolean edge = a == 2 || a == 45 || b == 2 || b == 45;
            if (edge && y < 4) return "minecraft:iron_bars";
            if (b == 36 && a >= 9 && a <= 39 && a % 6 == 0 && y <= 4)
                return y == 3 ? "minecraft:red_concrete" : WHITE;
            if (y == 0 && a >= 8 && a <= 40 && b == 10 && a % 5 < 3) return "minecraft:yellow_concrete";
            return AIR;
        }

        private String exhibition(int y) {
            double dx = a - 23.5, dz = b - 23.5;
            double r = dx * dx + dz * dz;
            if (y == 0 && r <= 18 * 18) return "academy:composite_floor";
            if (y >= 1 && y <= 18) {
                double shell = r + (y - 1) * (y - 1);
                if (shell >= 17 * 17 && shell <= 18.5 * 18.5) return (a == 23 || b == 23 || y % 6 == 0) ? STEEL : GLASS;
            }
            if (r < 2 && y <= 12) return y == 12 ? "academy:neon_cyan" : "academy:pillar_white";
            return AIR;
        }

        private String foodStreet(int y) {
            if (a < 3 || a > 44 || b < 6 || b > 21 || y > 9) return AIR;
            if (y == 9) return DARK;
            if (y == 7 && b == 21) return "academy:led_screen";
            if (a == 3 || a == 44 || b == 6 || b == 21 || a % 10 == 4) {
                if (b == 21 && a % 10 >= 7 && a % 10 <= 9 && y <= 3) return AIR;
                if (y >= 1 && y <= 4 && b == 21 && a % 10 != 4) return GLASS;
                return y % 5 == 0 ? "minecraft:orange_concrete" : WHITE;
            }
            return AIR;
        }

        private String school(int y) {
            if (a < 4 || a > 43 || b < 4 || b > 27 || y > 18) return AIR;
            if (y == 18 || (y > 0 && y % 6 == 0)) return y == 18 ? "academy:blue_panel_wall" : WHITE;
            boolean wall = a == 4 || a == 43 || b == 4 || b == 27;
            if (!wall) return AIR;
            if (b == 27 && a >= 21 && a <= 26 && y <= 3) return AIR;
            boolean pier = (a == 4 || a == 43) ? Math.floorMod(b - 4, 6) == 0 : Math.floorMod(a - 4, 6) == 0;
            if (pier) return WHITE;
            return y % 6 <= 3 ? GLASS : "academy:blue_panel_wall";
        }

        /** A distinct D7 teaching wing, with a sheltered entry and a small open court. */
        private String secondSchool(int y) {
            if (y > 23) return AIR;
            boolean building = a >= 4 && a <= 43 && b >= 4 && b <= 27;
            if (building) {
                if (y == 20) return "academy:blue_panel_wall";
                if (y > 20) {
                    if (b == 4 && a >= 17 && a <= 30 && y <= 22) return "academy:white_panel_wall";
                    return AIR;
                }
                if (y > 0 && y % 5 == 0) return WHITE;
                boolean wall = a == 4 || a == 43 || b == 4 || b == 27;
                if (!wall) {
                    // Four teaching positions face an open central aisle. Lamps and a
                    // colour-coded IF diagram make the room useful without the mentor.
                    if (y == 4 && (a == 11 || a == 18 || a == 29 || a == 36) && (b == 11 || b == 20)) return "academy:ceiling_light";
                    if (y == 0 && b == 24 && (a == 12 || a == 16 || a == 31 || a == 35)) return "minecraft:spruce_slab[type=bottom]";
                    if (y == 0 && b == 23 && (a == 12 || a == 16 || a == 31 || a == 35)) return "minecraft:spruce_stairs[facing=north]";
                    if (y <= 1 && b == 7 && a >= 21 && a <= 26) return y == 0 ? "academy:brushed_steel" : "academy:led_screen";
                    if (y == 2 && b == 7 && (a == 21 || a == 23 || a == 25)) return "academy:neon_cyan";
                    if (y == 0 && b == 22 && (a == 22 || a == 25)) return "minecraft:bookshelf";
                    if (y == 7 && a == 23 && b == 15) return "academy:brushed_steel";
                    if (y == 9 && (a == 20 || a == 27) && b == 15) return "academy:ceiling_light";
                    return AIR;
                }
                if (b == 27 && a >= 21 && a <= 26 && y <= 3) return AIR;
                boolean pier = (a == 4 || a == 43) ? Math.floorMod(b - 4, 5) == 0 : Math.floorMod(a - 4, 6) == 0;
                return pier ? WHITE : y % 5 <= 3 ? GLASS : "academy:blue_panel_wall";
            }
            // Canopy remains above the three-wide route through the entrance.
            if (a >= 19 && a <= 28 && b >= 28 && b <= 32) {
                if (y == 4) return b == 32 ? "academy:blue_panel_wall" : "academy:white_panel_slab[type=bottom]";
                if (y == 3 && b == 31 && (a == 20 || a == 27)) return "academy:ceiling_light";
                if ((a == 19 || a == 28) && b == 32 && y < 4) return "academy:pillar_white";
            }
            int dx = Math.min(Math.abs(a - 34), Math.abs(a - 41));
            int dz = Math.abs(b - 38);
            if (dx == 0 && dz == 0 && y <= 4) return "minecraft:cherry_log";
            if (y >= 4 && y <= 6 && dx <= 2 && dz <= 2 && dx + dz <= 3)
                return "minecraft:cherry_leaves[persistent=true]";
            if (y == 0 && a >= 28 && a <= 30 && b == 42) return "minecraft:oak_stairs[facing=north]";
            return AIR;
        }

        /** Library exterior and open ground-floor entrance; furnishing is deferred. */
        private String campusLibrary(int y) {
            // A high-contrast open-book emblem distinguishes the library from nearby classrooms.
            if (b == 7 && a >= 16 && a <= 31 && y >= 9 && y <= 15) {
                if (a == 24 && y >= 10 && y <= 14) return "academy:brushed_steel";
                if ((a >= 20 && a <= 23 || a >= 25 && a <= 28) && y >= 10 && y <= 13)
                    return "minecraft:white_concrete";
                return "academy:blue_panel_wall";
            }
            boolean building = a >= 4 && a <= 43 && b >= 8 && b <= 33;
            if (building) {
                if (y > 18) return AIR;
                if (y == 18) return "academy:blue_panel_wall";
                if (y > 0 && y % 6 == 0) return WHITE;
                boolean edge = a == 4 || a == 43 || b == 8 || b == 33;
                if (!edge) return AIR;
                if (b == 8 && a >= 21 && a <= 26 && y <= 3) return AIR;
                boolean pier = (a == 4 || a == 43) ? Math.floorMod(b - 8, 5) == 0 : Math.floorMod(a - 4, 6) == 0;
                if (pier) return WHITE;
                return y % 6 <= 3 ? "academy:curtain_glass_blue" : WHITE;
            }
            // A shallow, illuminated entry canopy keeps the public route below clear.
            if (a >= 18 && a <= 29 && b >= 3 && b <= 7) {
                if (y == 5) return b == 3 ? "academy:blue_panel_wall" : "academy:white_panel_slab[type=bottom]";
                if (y == 4 && b == 4 && (a == 20 || a == 27)) return "academy:ceiling_light";
                if ((a == 18 || a == 29) && b == 3 && y < 5) return "academy:pillar_white";
            }
            int dx=Math.min(Math.abs(a-8),Math.abs(a-39)), dz=Math.abs(b-41);
            if(dx==0 && dz==0 && y<=4) return "minecraft:cherry_log";
            if(y>=4 && y<=6 && dx<=2 && dz<=2 && dx+dz<=3) return "minecraft:cherry_leaves[persistent=true]";
            if(y==0 && b==38 && (a>=16 && a<=18 || a>=29 && a<=31)) return "minecraft:oak_stairs[facing=north]";
            return AIR;
        }

        /** Open-air campus field, running lanes and sheltered perimeter lighting. */
        private String campusSports(int y) {
            if ((a == 4 || a == 43) && (b == 4 || b == 43) && y <= 10)
                return y == 10 ? "academy:ceiling_light" : "academy:pillar_white";
            boolean edge = a == 2 || a == 45 || b == 2 || b == 45;
            boolean gate = b == 2 && a >= 21 && a <= 26;
            if (edge && !gate && y <= 2) {
                boolean ew = b == 2 || b == 45, ns = a == 2 || a == 45;
                return "minecraft:iron_bars[east=" + ew + ",west=" + ew + ",north=" + ns + ",south=" + ns + "]";
            }
            if (y == 0 && b == 46 && (a >= 11 && a <= 15 || a >= 32 && a <= 36))
                return "minecraft:oak_stairs[facing=north]";
            return AIR;
        }

        private String entertainment(int y) {
            if (y > 42) return AIR;
            double dx = a - 23.5, dy = y - 21;
            double r = dx * dx + dy * dy;
            if (Math.abs(b - 24) <= 1 && r >= 17 * 17 && r <= 19 * 19)
                return (a + y) % 5 == 0 ? "academy:neon_magenta" : STEEL;
            if (Math.abs(b - 24) <= 1 && (Math.abs(dx) <= 1 || Math.abs(dy) <= 1) && r < 17 * 17) return STEEL;
            if ((a == 17 || a == 30) && b >= 22 && b <= 26 && y <= 4) return "academy:pillar_white";
            return AIR;
        }

        private String industrial(int y) {
            if (a >= 33 && a <= 38 && b >= 8 && b <= 13 && y <= 44)
                return y >= 40 ? "minecraft:red_concrete" : "minecraft:gray_concrete";
            if (a < 4 || a > 43 || b < 18 || b > 41 || y > 13) return AIR;
            if (y == 13) return STEEL;
            if (a == 4 || a == 43 || b == 18 || b == 41)
                return y >= 5 && y <= 8 && a % 6 > 1 ? GLASS : "minecraft:gray_concrete";
            return AIR;
        }

        private String stadium(int y) {
            double r = Math.pow((a - 23.5) / 22, 2) + Math.pow((b - 23.5) / 18, 2);
            if (r >= .88 && r <= 1.05 && y <= 3) return "minecraft:quartz_stairs";
            if ((a == 3 || a == 44) && (b == 8 || b == 39) && y <= 7)
                return y == 7 ? "academy:ceiling_light" : "academy:pillar_white";
            return AIR;
        }

        private String neighborhood(int y) {
            String signature = CityDistrictSignature.block(type, a, b, y);
            if (signature != null) return signature;
            if (type == 11) return foodStreet(y); // market frontage, with no transaction system
            if (type == 8 || type == 14 || type == 18) {
                int dx = Math.min(Math.abs(a - 10), Math.abs(a - 37));
                int dz = Math.min(Math.abs(b - 10), Math.abs(b - 37));
                if (dx == 0 && dz == 0 && y <= 4) return "minecraft:oak_log";
                if (y >= 4 && y <= 6 && dx <= 2 && dz <= 2 && dx + dz <= 3)
                    return "minecraft:oak_leaves[persistent=true]";
                if (y == 0 && (a == 18 || a == 29) && (b == 21 || b == 25)) return "minecraft:oak_stairs";
                return AIR;
            }
            if (type == 10 || type == 13 || type == 19) {
                if (a == 23 && b == 23 && y <= 4) return y == 4 ? "academy:neon_cyan" : WHITE;
                return AIR;
            }
            if (a >= 12 && a <= 35 && b >= 12 && b <= 35) {
                if (y == 6) return "academy:blue_panel_wall";
                if (y < 6 && (a == 12 || a == 35) && (b == 12 || b == 35)) return "academy:pillar_white";
            }
            return AIR;
        }

        private String dam(int y) {
            if (y < -23 || y > 11) return null;
            if (z == -909 || z == -908 || z == -615 || z == -614) {
                if (y <= 7) return "academy:reinforced_concrete";
                if (y == 8 && (z == -908 || z == -615)) return "academy:white_panel_slab[type=bottom]";
                return AIR;
            }
            if (y <= 7) return "academy:reinforced_concrete";
            if (y == 8) return "academy:plaza_tile";
            boolean lookoutEntry = x == -1317 && (z >= -822 && z <= -818 || z >= -702 && z <= -698);
            if (y <= 10 && (x == -1324 || x == -1317) && !lookoutEntry) return "minecraft:iron_bars";
            if (y == 11 && (x == -1324 || x == -1317) && !lookoutEntry && Math.floorMod(z, 20) == 0) return "academy:neon_cyan";
            return AIR;
        }

        /** Water-facing overlooks connected to the existing dam walkway. */
        private String reservoirLookout(int y) {
            int centre = z < -760 ? -820 : -700;
            boolean support = (x == -1315 || x == -1309) && Math.abs(z - centre) == 4;
            if (y >= -4 && y <= 7 && support) return "academy:pillar_dark";
            if (y == 8) return PAVE;
            boolean entry = x == -1316 && Math.abs(z - centre) <= 2;
            boolean edge = x == -1316 || x == -1308 || Math.abs(z - centre) == 5;
            if (y == 9 && edge && !entry) return "minecraft:iron_bars";
            if (y == 9 && x == -1309 && Math.abs(z - centre) == 4) return "academy:pillar_white";
            if (y == 10 && x == -1309 && Math.abs(z - centre) == 4)
                return "minecraft:lantern[hanging=false,waterlogged=false]";
            return y >= 9 && y <= 11 ? AIR : null;
        }

        private String scaffold(int y) {
            // Repeat the verified maintenance assembly around each mast in the scaffold zone.
            // Mast coordinates have remainder 12 on both axes; its ladder is on the east face.
            int rx = Math.floorMod(x, 24), rz = Math.floorMod(z, 24);
            boolean serviceZone = x >= -1187 && x <= -415 && z >= -1982 && z <= -1234;
            if (serviceZone && rx == 13 && rz == 12 && y >= 0 && y <= 26)
                return "minecraft:ladder[facing=east,waterlogged=false]";
            if (serviceZone && rx >= 14 && rx <= 17 && rz >= 10 && rz <= 14) {
                for (int floor : SERVICE_FLOORS) {
                    if (y == floor) return rx == 17 && rz == 10 ? "academy:neon_amber" : STEEL;
                    // The middle of the east edge connects to the maintenance bridge.
                    boolean outer = rz == 10 || rz == 14 || rx == 17 && (rz < 11 || rz > 13);
                    if (outer && (y == floor + 1 || y == floor + 2)) {
                        return "minecraft:iron_bars";
                    }
                }
            }
            boolean bridgeZone = x >= -1188 && x <= -415 && z >= -1982 && z <= -1234;
            if (bridgeZone && !(rx == 12 && rz == 12)) {
                boolean eastWest = rz >= 11 && rz <= 13;
                boolean northSouth = rx >= 11 && rx <= 13;
                boolean eastWestRail = (rz == 10 || rz == 14) && (rx < 8 || rx > 16);
                boolean northSouthRail = (rx == 10 || rx == 14) && (rz < 8 || rz > 16);
                for (int floor : SERVICE_FLOORS) {
                    if (y == floor && (eastWest || northSouth)) return STEEL;
                    if ((y == floor + 1 || y == floor + 2) && (eastWestRail || northSouthRail))
                        return "minecraft:iron_bars";
                }
            }
            if (y < 8 || y > 32 || y % 8 != 0) return null;
            boolean alongX = Math.floorMod(z, 24) == 12, alongZ = Math.floorMod(x, 24) == 12;
            if (alongX && alongZ) return null; // keep the existing turbine mast
            return alongX || alongZ ? STEEL : null;
        }

        /** Accessible 30-level wind-plant tower, leaving its existing turbine mast intact. */
        private String d22Tower(int y) {
            int dx = a, dz = b;
            boolean mast = dx == 0 && dz == 0;
            boolean ladderSupport = dx == -2 && dz == -2;
            boolean ladder = dx == -3 && dz == -2;
            if (dx == 1 && dz == 0 && y >= 0 && y <= 26) return scaffold(y);
            if (ladderSupport && y >= 0 && y <= 121) return STEEL;
            if (ladder && y >= 0 && y <= 121) return "minecraft:ladder[facing=west,waterlogged=false]";
            if ((!mast || y > unused) && y >= 4 && y <= 120 && y % 4 == 0)
                return Math.abs(dx) == 3 && Math.abs(dz) == 3 ? "academy:neon_amber" : STEEL;
            int level = y - Math.floorMod(y, 4);
            if (level >= 4 && level <= 120 && (y == level + 1 || y == level + 2)
                    && (Math.abs(dx) == 4 || Math.abs(dz) == 4)) {
                boolean bridgeOpening = (level == 8 || level == 16 || level == 24)
                        && (Math.abs(dx) == 4 && Math.abs(dz) <= 1 || Math.abs(dz) == 4 && Math.abs(dx) <= 1);
                if (!bridgeOpening) {
                    boolean eastWest = Math.abs(dz) == 4;
                    boolean northSouth = Math.abs(dx) == 4;
                    return "minecraft:iron_bars[east=" + eastWest + ",west=" + eastWest
                            + ",north=" + northSouth + ",south=" + northSouth + "]";
                }
            }
            return scaffold(y);
        }

        private String d22Approach(int y) {
            if (y > 4) return d22ScaffoldColumn(x, z) ? scaffold(y) : null;
            if (y < -1) return null;
            if (y == -1) return x == -800 ? PAVE : "minecraft:cyan_concrete";
            if (x == -797) return y == 4 ? "minecraft:lantern[hanging=false,waterlogged=false]" : "academy:pillar_white";
            return AIR;
        }

        private String terminal(int y) {
            if (y < -1) return null;
            if (y == -1 || y == 12 || y == 24) return "academy:composite_floor";
            if (y > 24) return AIR;
            boolean edge = a == 0 || a == 59 || b == 0 || b == 59;
            if (!edge) {
                if (y == 11 && a >= 12 && a <= 48 && b >= 12 && b <= 48
                        && a % 12 == 0 && b % 12 == 0) return "academy:ceiling_light";
                return AIR;
            }
            if (b == 59 && a >= 25 && a <= 34 && y <= 4) return AIR;
            if ((a == 0 || a == 59) && (b >= 16 && b <= 24 || b >= 35 && b <= 43) && y <= 4) return AIR;
            if (b == 59 && a >= 21 && a <= 38 && y == 6) return "academy:neon_cyan";
            boolean pier = (a == 0 || a == 59) ? b % 8 == 0 : a % 8 == 0;
            if (pier || y % 6 == 0) return WHITE;
            return GLASS;
        }

        /** Exterior shell and through-passages; interiors remain intentionally unfurnished. */
        private String terminalWing(int y) {
            if (y < -1) return null;
            if (y == -1) return "academy:composite_floor";
            if (y == 18) {
                boolean skylight = a >= 8 && a <= 39 && b >= 10 && b <= 41
                        && (Math.floorMod(a, 8) == 0 || Math.floorMod(b, 10) == 0);
                return skylight ? GLASS : WHITE;
            }
            if (y > 18) return AIR;
            boolean edge = a == 0 || a == 47 || b == 0 || b == 51;
            if (!edge) {
                if (y == 17 && a >= 8 && a <= 40 && b >= 8 && b <= 44
                        && a % 12 == 4 && b % 12 == 4) return "academy:ceiling_light";
                return AIR;
            }
            boolean sharedWall = type == 33 ? a == 47 : a == 0;
            if (sharedWall && (b >= 12 && b <= 20 || b >= 31 && b <= 39) && y <= 4) return AIR;
            if (b == 51 && a >= 19 && a <= 26 && y <= 4) return AIR;
            if (b == 51 && a >= 15 && a <= 30 && y == 6) return "academy:neon_cyan";
            boolean pier = (a == 0 || a == 47) ? b % 8 == 0 : a % 8 == 0;
            if (pier || y % 6 == 0) return WHITE;
            return GLASS;
        }

        /** A static maintenance hangar with a wide opening towards the apron. */
        private String airportHangar(int y) {
            if (y < -1) return null;
            if (y == -1) return z == -1749 && x >= 1460 ? "minecraft:yellow_concrete" : "academy:reinforced_concrete";
            int roof = 20 + Math.max(0, 3 - Math.abs(b - 25) / 7);
            if (y > roof) return AIR;
            if (y == roof) return (a % 15 == 0 || b == 0 || b == 51) ? STEEL : "academy:white_panel_wall";
            if (a == 89 && b >= 18 && b <= 38 && y <= 14) return AIR;
            boolean edge = a == 0 || a == 89 || b == 0 || b == 51;
            if (!edge) {
                if (y == roof - 1 && a >= 15 && a <= 75 && a % 15 == 0 && (b == 13 || b == 38))
                    return "academy:ceiling_light";
                return AIR;
            }
            boolean column = (a == 0 || a == 89) ? b % 8 == 0 : a % 10 == 0;
            if (column || y == 0 || y % 7 == 0) return STEEL;
            if ((b == 0 || b == 51) && y >= 8 && y <= 11) return "academy:curtain_glass_blue";
            return WHITE;
        }

        /** Ground access and an unstaffed shelter; no vehicle service is implied. */
        private String airportApproach(int y) {
            if (y < -1 || y > 4) return null;
            boolean shelter = x >= 1582 && x <= 1594 && z >= -1800 && z <= -1797;
            boolean walk = x >= 1617 && z <= -1790 || z <= -1795 || z >= -1785;
            if (y == -1) {
                if (shelter || walk) return PAVE;
                return z == -1790 && Math.floorMod(x, 16) < 8 ? "academy:road_marking" : "academy:asphalt";
            }
            if (!shelter) return AIR;
            if (y == 4) return "academy:steel_slab[type=bottom]";
            if ((x == 1582 || x == 1594) && (z == -1800 || z == -1797)) return "academy:pillar_white";
            if (z == -1800 && y >= 1 && y <= 3) return GLASS;
            if (z == -1799 && x >= 1585 && x <= 1591 && y == 0) return "minecraft:oak_slab[type=bottom]";
            return AIR;
        }

        private String airportForecourt(int y) {
            if (y < -1 || y > 5) return null;
            if (y == -1) return PAVE;
            if ((x == 1601 || x == 1642) && z == -1806)
                return y == 4 ? "minecraft:lantern[hanging=false,waterlogged=false]" : y < 4 ? WHITE : AIR;
            boolean canopy = x >= 1610 && x <= 1633 && z >= -1808 && z <= -1805;
            if (canopy && y == 5) return "academy:steel_slab[type=bottom]";
            if (canopy && y == 4 && z == -1806 && (x == 1614 || x == 1622 || x == 1630))
                return "academy:ceiling_light";
            if ((x == 1610 || x == 1633) && z == -1805 && y <= 4) return WHITE;
            boolean planter = (x >= 1599 && x <= 1605 || x >= 1638 && x <= 1644) && z >= -1803 && z <= -1799;
            if (planter && y == 0) return z == -1803 || z == -1799 || x == 1599 || x == 1605 || x == 1638 || x == 1644
                    ? "academy:reinforced_concrete" : "minecraft:grass_block";
            if (planter && y == 1 && z > -1803 && z < -1799 && Math.floorMod(x, 2) == 0) return "minecraft:oak_leaves[persistent=true]";
            if (y == 0 && z == -1802 && (x >= 1609 && x <= 1613 || x >= 1629 && x <= 1633)) return "minecraft:oak_slab[type=bottom]";
            return AIR;
        }

        private String d7Plaza(int y) {
            if (y < -1 || y > 6) return null;
            int radius = x * x + z * z;
            int dx = Math.min(Math.abs(x - 19), Math.abs(x + 19));
            int dz = Math.min(Math.abs(z - 19), Math.abs(z + 19));
            if (y == -1) {
                if (radius < 36) return "minecraft:water[level=0]";
                if (radius <= 49) return WHITE;
                return dx <= 3 && dz <= 3 ? "minecraft:grass_block" : PAVE;
            }
            if (x == 0 && z == 0 && y <= 4) return y == 4 ? "academy:neon_cyan" : "academy:brushed_steel";
            if (dx == 0 && dz == 0 && y <= 4) return "minecraft:oak_log";
            if (y >= 4 && y <= 6 && dx <= 2 && dz <= 2 && dx + dz <= 3) return "minecraft:oak_leaves[persistent=true]";
            boolean lamp = Math.abs(x) == 18 && z == 0 || Math.abs(z) == 18 && x == 0;
            if (lamp && y <= 4) return y == 4 ? "minecraft:lantern[hanging=false,waterlogged=false]" : "academy:pillar_white";
            if (y == 0 && (Math.abs(x) == 10 && Math.abs(z) == 8 || Math.abs(x) == 8 && Math.abs(z) == 10))
                return "minecraft:oak_stairs";
            return AIR;
        }

        private String runway(int y) {
            if (y != -1) return null;
            if ((z == -1688 || z == -1629) && Math.floorMod(x, 24) == 0) return "academy:neon_cyan";
            if ((x == 1276 || x == 1924) && Math.abs(z + 1658) <= 20 && Math.floorMod(z, 4) < 2) return "minecraft:white_concrete";
            return Math.abs(z + 1658) <= 1 && Math.floorMod(x, 28) < 16 ? "minecraft:white_concrete" : null;
        }

        private String observatory(int y) {
            if (y < 80) return null;
            if (y <= 91) return "minecraft:stone";
            if (y == 92 || y == 96) return "academy:reinforced_concrete";
            if (y >= 93 && y <= 95) {
                boolean edge = a == 0 || a == 15 || b == 0 || b == 15;
                if (!edge || (b == 15 && a >= 6 && a <= 9)) return AIR;
                boolean pier = (a == 0 || a == 15) ? b % 5 == 0 : a % 5 == 0;
                return pier ? WHITE : GLASS;
            }
            double dx = a - 7.5, dz = b - 7.5, dy = y - 96;
            double r = dx * dx + dz * dz + dy * dy;
            if (y >= 97 && y <= 103 && r >= 6 * 6 && r <= 7.5 * 7.5)
                return Math.abs(a - 7.5) < 1 ? "academy:blue_panel_wall" : WHITE;
            return AIR;
        }

        private String launchSite(int y) {
            if (y < -1) return null;
            int dx = a - 32, dz = b - 32, radius = dx * dx + dz * dz;
            if (y == -1) return radius < 16 * 16 ? "minecraft:gray_concrete" : "academy:reinforced_concrete";
            if (y > 50) return AIR;
            if (y <= 42 && radius <= 4 * 4) return y % 12 <= 1 ? "academy:blue_panel_wall" : WHITE;
            if (y >= 43 && y <= 50 && radius <= (51 - y) * (51 - y) / 4)
                return y >= 48 ? "minecraft:red_concrete" : WHITE;
            boolean tower = (a == 9 || a == 14) && (b == 9 || b == 14);
            boolean brace = a >= 9 && a <= 14 && b >= 9 && b <= 14 && y % 8 == 0
                    && (a == 9 || a == 14 || b == 9 || b == 14);
            if (y <= 44 && (tower || brace)) return STEEL;
            return AIR;
        }

        private String observatoryPath(int y) {
            if (y < 80 || y > 95) return null;
            if (y < 92) return "minecraft:stone";
            if (y == 92) return PAVE;
            return AIR;
        }
    }
}
