package cn.academy.port.city;

/** Deterministic lighting, seating and small amenities on ordinary district sidewalks. */
final class CityNeighborhoodProps {
    private CityNeighborhoodProps() {}

    static Column at(long seed, int x, int z) {
        int d = CityPlan.district(x, z);
        if (d == 0 || d >= 21 || (z >= 370 && z <= 430)
                || (x >= -112 && x <= -15 && z >= -100 && z <= 710)) return null;
        if (CitySampleRoute.schoolLink(x, z)) return null;
        if (CityNorthSouthTransit.overlapsLot(x, z, 1, 1) || CityRingTransit.overlapsLot(x, z, 1, 1)
                || CityAirportTransit.overlapsLot(x, z, 1, 1) || CityMetroTransit.entranceReserved(x, z, 1, 1) || CityMetroNorthTransit.entranceReserved(x, z, 1, 1) || CityMetroAirportTransit.entranceReserved(x, z, 1, 1)) return null;
        int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL), lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
        if (lx < 12 || lx >= 788 || lz < 12 || lz >= 788) return null;
        int u = lx - 12, w = lz - 12, ru = u % 64, rw = w % 64;
        boolean vertical = ru == 10 && (rw == 20 || rw == 32 || rw == 44);
        boolean horizontal = rw == 10 && (ru == 20 || ru == 32 || ru == 44);
        if (!vertical && !horizontal) return null;
        int position = vertical ? rw : ru;
        int parity = Math.floorMod(u / 64 + w / 64 + d, 2);
        if (position == 32 && parity != 0) return null;
        if (position != 32 && Math.floorMod(u / 64 * 3 + w / 64 + d + position, 3) == 0) return null;
        if (CityTemplateSites.at(seed, x, z) != null) return null;
        if (CityPlan.sample(seed, x, z).kind() != CityPlan.Kind.SIDEWALK) return null;
        if (CityDistrictLandmarks.at(seed, x, z) != null || CitySampleRoute.at(x, z) != null) return null;
        int kind = position == 32 ? 0 : Math.floorMod(u / 64 * 5 + w / 64 * 7 + d + position, 3) + 1;
        return new Column(kind, vertical);
    }

    record Column(int kind, boolean vertical) {
        int top() { return kind == 0 ? 5 : kind == 3 ? 2 : 0; }
        String block(int y) {
            if (y < 0 || y > top()) return null;
            return switch (kind) {
                case 0 -> y == 5 ? "minecraft:lantern[hanging=false,waterlogged=false]" : "academy:pillar_dark";
                case 1 -> "minecraft:oak_stairs[facing=" + (vertical ? "west" : "north") + "]";
                case 2 -> "minecraft:cauldron";
                case 3 -> y == 2 ? "academy:led_screen" : "academy:pillar_white";
                default -> throw new IllegalStateException("unknown sidewalk amenity");
            };
        }
    }
}
