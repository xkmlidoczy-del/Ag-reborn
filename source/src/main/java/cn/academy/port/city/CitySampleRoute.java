package cn.academy.port.city;

/** Ground and platform columns for the D7 to D15 walking sample. Each column is independent. */
final class CitySampleRoute {
    static final int START_X = -65, START_Z = -69;
    private static final String AIR = "minecraft:air";
    private static final String PAVING = "academy:plaza_tile";
    private static final String WHITE = "academy:white_panel_wall";
    private static final String GLASS = "academy:curtain_glass_clear";
    private static final String CYAN = "minecraft:cyan_concrete";
    private static final String AMBER = "minecraft:yellow_concrete";

    private enum Part { CAMPUS, GATE, NORTH, SOUTH, PLAZA, SCHOOL_LINK }

    private CitySampleRoute() {}

    static Column at(int x, int z) {
        if (x < -290 || x > -33 || z < -92 || z > 699) return null;
        if (schoolLink(x, z)) return new Column(Part.SCHOOL_LINK, x, z);
        if (between(x, -86, -44) && z <= -51) return new Column(Part.CAMPUS, x, z);
        if (between(x, -74, -56) && between(z, -25, -23)) return new Column(Part.GATE, x, z);
        if (between(x, -97, -33) && between(z, 548, 591)) return new Column(Part.PLAZA, x, z);

        boolean lane = between(x, -68, -62);
        boolean stairHead = between(x, -77, -73);
        // The east-facing stairs must be approached from their lower west end.
        boolean landing = between(x, -77, -76);
        if ((lane && between(z, -50, 385)) || (between(x, -77, -62) && between(z, 382, 386))
                || (stairHead && between(z, 383, 391)) || (landing && between(z, 392, 394))) {
            return new Column(Part.NORTH, x, z);
        }
        if ((lane && between(z, 420, 547)) || (lane && between(z, 592, 699))
                || (between(x, -77, -62) && between(z, 420, 423))
                || (stairHead && between(z, 409, 423)) || (landing && between(z, 406, 408))) {
            return new Column(Part.SOUTH, x, z);
        }
        return null;
    }

    /** Sidewalk connection from the D7 school entrance to the existing central-station route. */
    static boolean schoolLink(int x, int z) {
        return between(x, -162, -160) && between(z, -92, -58)
                || between(x, -226, -224) && between(z, -92, -48)
                || between(x, -290, -122) && between(z, -60, -58)
                || between(x, -290, -288) && between(z, -58, -51)
                || between(x, -123, -121) && between(z, -59, -7)
                || between(x, -123, -69) && between(z, -8, -6);
    }

    record Column(Part part, int x, int z) {
        int top() {
            return switch (part) {
                case CAMPUS, GATE -> 7;
                case PLAZA -> 6;
                default -> 4;
            };
        }

        String block(int y) {
            if (y < -1) return null;
            return switch (part) {
                case CAMPUS -> campus(y);
                case GATE -> gate(y);
                case NORTH -> lane(y, CYAN);
                case SOUTH -> lane(y, AMBER);
                case PLAZA -> plaza(y);
                // Leave overhead canopies intact while clearing player headroom.
                case SCHOOL_LINK -> y == -1 ? schoolLinkFloor() : y <= 3 ? AIR : null;
            };
        }

        private String schoolLinkFloor() {
            // The school-to-school path crosses the local north–south street here.
            if ((between(x, -196, -189) || between(x, -260, -253)) && between(z, -60, -58))
                return Math.floorMod(x, 2) == 0 ? "minecraft:white_concrete" : "academy:asphalt";
            return PAVING;
        }

        private String lane(int y, String color) {
            if (y == -1) return x == -68 || x == -62 || (z == 382 || z == 423) ? color : PAVING;
            if ((x == -68 || x == -62) && Math.floorMod(z + 16, 48) == 0 && z > 0 && z < 360) {
                if (y < 3) return WHITE;
                if (y == 3) return "academy:neon_cyan";
            }
            return AIR;
        }

        private String campus(int y) {
            boolean facade = x == -86 || x == -44 || z == -88 || z == -51;
            boolean foyer = between(x, -68, -62);
            if (y == -1) return foyer ? "academy:composite_floor" : PAVING;
            if (y > 7) return AIR;
            if (y == 7) return facade ? "academy:blue_panel_wall" : AIR;
            if (y == 6) return WHITE;
            if (y == 5 && foyer && Math.floorMod(z, 7) == 0) return "academy:ceiling_light";
            if (z == -51 && between(x, -67, -63) && y <= 3) return AIR;
            if (facade) {
                int pane = x == -86 || x == -44 ? Math.floorMod(z + 88, 7) : Math.floorMod(x + 86, 7);
                if (between(y, 1, 3) && between(pane, 2, 5)) return GLASS;
                return y == 4 ? "academy:blue_panel_wall" : WHITE;
            }
            if ((x == -72 || x == -58) && !between(z, -72, -66) && between(y, 0, 4)) return WHITE;
            if ((x == -79 || x == -51) && z > -85 && z < -55 && Math.floorMod(z, 5) <= 1 && y == 0) {
                return "minecraft:oak_slab[type=top]";
            }
            return AIR;
        }

        private String gate(int y) {
            if (y == -1) return between(x, -68, -62) ? CYAN : PAVING;
            if (y > 7) return AIR;
            if (y == 6 || y == 7) return y == 6 ? "academy:neon_cyan" : WHITE;
            if (x <= -72 || x >= -58) return x <= -73 || x >= -57 ? WHITE : AIR;
            return AIR;
        }

        private String plaza(int y) {
            boolean kiosk = (between(x, -93, -78) || between(x, -52, -37)) && between(z, 564, 584);
            if (y == -1) {
                if (between(x, -68, -62)) return "academy:composite_floor";
                if ((z == 548 || z == 591) && Math.floorMod(x, 4) < 2) return "academy:brushed_steel";
                return Math.floorMod(x + z, 12) == 0 ? "academy:white_panel_wall" : PAVING;
            }
            if (y == 0 && (x == -73 || x == -57) && Math.floorMod(z - 552, 11) == 0) return "academy:neon_amber";
            boolean lamp = (x == -74 || x == -56) && (z == 556 || z == 586);
            if (lamp && y <= 5) return y == 5 ? "academy:ceiling_light" : "academy:pillar_white";
            boolean planter = (between(x, -77, -75) || between(x, -55, -53))
                    && (between(z, 552, 555) || between(z, 586, 589));
            if (planter && y == 0) return "academy:reinforced_concrete";
            if (planter && y == 1) return (x + z) % 2 == 0
                    ? "minecraft:flowering_azalea_leaves[persistent=true]" : "minecraft:oak_leaves[persistent=true]";
            if (!kiosk || y > 6) return AIR;
            if (y == 6) return "academy:dark_panel_wall";
            if (y == 5) return (z == 564 || z == 584 || x == -93 || x == -78 || x == -52 || x == -37)
                    ? "academy:led_screen" : AIR;
            if (y == 4 && z == 565 && (x == -91 || x == -80 || x == -50 || x == -39)) return "academy:ceiling_light";
            if (z == 564 && (between(x, -87, -84) || between(x, -46, -43)) && y < 4) return AIR;
            if (z == 564 || z == 584 || x == -93 || x == -78 || x == -52 || x == -37) return y == 0 ? WHITE : GLASS;
            return AIR;
        }
    }

    private static boolean between(int value, int min, int max) {
        return value >= min && value <= max;
    }
}
