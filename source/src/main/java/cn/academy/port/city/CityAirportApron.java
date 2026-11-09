package cn.academy.port.city;

/** Ground-only aircraft apron and taxiway markings; no aircraft service is implied. */
final class CityAirportApron {
    private static final int[] STANDS = {1512, 1560, 1608, 1656, 1704};
    private static final int[] FLOODLIGHTS = {1496, 1544, 1592, 1640, 1688, 1736};
    private CityAirportApron() {}

    static Column at(int x, int z) {
        boolean apron = x >= 1488 && x <= 1744 && z >= -1772 && z <= -1689;
        // Connect to the runway across its northern edge without replacing the rest of its lights.
        boolean runwayJunction = x >= 1625 && x <= 1639 && z == -1688;
        return apron || runwayJunction ? new Column(x, z) : null;
    }

    record Column(int x, int z) {
        private boolean floodlight() {
            if (z < -1772 || z > -1770) return false;
            for (int light : FLOODLIGHTS) if (x == light) return true;
            return false;
        }
        int top() { return floodlight() ? 9 : -1; }
        String block(int y) {
            if (floodlight()) {
                if (y == 9) return "academy:steel_slab[type=bottom]";
                if (z == -1772 && y >= 0 && y <= 8) return "academy:pillar_dark";
                if (z > -1772 && y == 8) return "academy:ceiling_light";
            }
            if (y != -1) return null;
            // Connect the western maintenance hangar to the first stand lead-in.
            if (z == -1749 && x >= 1488 && x <= 1512) return "minecraft:yellow_concrete";
            for (int center : STANDS)
                if (x == center && z >= -1758 && z <= -1714) return "minecraft:yellow_concrete";
            boolean mainTaxi = z >= -1722 && z <= -1706;
            boolean runwayConnector = x >= 1625 && x <= 1639 && z >= -1714;
            if (mainTaxi || runwayConnector) {
                if (z == -1714 || x == 1632 && z >= -1714) return "minecraft:yellow_concrete";
                return "academy:asphalt";
            }
            if ((z == -1723 || z == -1705) && Math.floorMod(x, 16) == 0)
                return "academy:neon_cyan";
            for (int center : STANDS) {
                int dx = Math.abs(x - center);
                if (dx == 16 && z >= -1764 && z <= -1730 || z == -1764 && dx <= 16)
                    return (dx == 16 && (z == -1764 || z == -1748 || z == -1732))
                            ? "academy:neon_amber" : "minecraft:white_concrete";
                if (z == -1758 && dx <= 5)
                    return "minecraft:yellow_concrete";
            }
            return "minecraft:light_gray_concrete";
        }
    }
}
