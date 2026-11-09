package cn.academy.port.city;

/** Street furniture along the commercial portions of D15's existing sidewalk grid. */
final class CityCommercialStreets {
    private CityCommercialStreets() {}

    static Column at(long seed, int x, int z, CityPlan.Sample sample) {
        if (sample.district() != 15 || sample.kind() != CityPlan.Kind.SIDEWALK) return null;
        int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL);
        int lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
        if (lx < 12 || lx >= 788 || lz < 12 || lz >= 788) return null;
        int u = lx - 12, w = lz - 12, ru = u % 64, rw = w % 64;
        int bx = u / 64, bz = w / 64;
        if (CityDistrictZoning.use(15, bx, bz) != CityDistrictZoning.Use.COMMERCIAL) return null;
        boolean lampArmV = (ru == 10 || ru == 61) && rw == 18;
        boolean lampArmH = (rw == 10 || rw == 61) && ru == 18;
        boolean vertical = (ru == 11 || ru == 60) && rw >= 16 && rw <= 55 || lampArmV;
        boolean horizontal = (rw == 11 || rw == 60) && ru >= 16 && ru <= 55 || lampArmH;
        if (!vertical && !horizontal) return null;
        int along = vertical ? rw : ru;
        Kind kind = lampArmV || lampArmH ? Kind.LAMP_ARM : switch (along) {
            case 18 -> Kind.LAMP;
            case 29, 30 -> Kind.BENCH;
            case 42 -> Kind.SIGN;
            case 52 -> Kind.PLANTER;
            default -> null;
        };
        if (kind == null) return null;
        // Leave the existing sample route, train stations and authored landmarks untouched.
        if (reserved(seed, x, z)) return null;
        if (kind == Kind.LAMP || kind == Kind.LAMP_ARM) {
            int otherX = x, otherZ = z;
            if (vertical) otherX += lampArmV ? (ru == 10 ? 1 : -1) : (ru == 11 ? -1 : 1);
            else otherZ += lampArmH ? (rw == 10 ? 1 : -1) : (rw == 11 ? -1 : 1);
            if (CityPlan.sample(seed, otherX, otherZ).kind() != CityPlan.Kind.SIDEWALK
                    || reserved(seed, otherX, otherZ)) return null;
        }
        int side = vertical ? ru : rw;
        int variant = Math.floorMod(bx * 7 + bz * 11 + side, 3);
        String facing = vertical ? (side == 11 ? "west" : "east") : (side == 11 ? "north" : "south");
        return new Column(kind, variant, facing);
    }

    private static boolean reserved(long seed, int x, int z) {
        return (x >= -112 && x <= -15 && z >= 370 && z <= 710)
                || CitySampleRoute.at(x, z) != null || CityStreetProps.at(seed, x, z) != null
                || CityNeighborhoodProps.at(seed, x, z) != null || CityDistrictLandmarks.at(seed, x, z) != null
                || CityTemplateSites.at(seed, x, z) != null || CityTransit.overlay(x, z) != null
                || CityNorthSouthTransit.overlapsLot(x, z, 1, 1) || CityMetroTransit.entranceReserved(x, z, 1, 1) || CityMetroNorthTransit.entranceReserved(x, z, 1, 1) || CityMetroAirportTransit.entranceReserved(x, z, 1, 1);
    }

    private enum Kind { LAMP, LAMP_ARM, BENCH, SIGN, PLANTER }
    record Column(Kind kind, int variant, String facing) {
        int top() { return switch (kind) { case LAMP, LAMP_ARM -> 5; case SIGN -> 4; case BENCH, PLANTER -> 0; }; }
        String block(int y) {
            if (y < 0 || y > top()) return null;
            return switch (kind) {
                case LAMP -> y == 5 ? "academy:steel_slab[type=bottom]" : "academy:pillar_dark";
                case LAMP_ARM -> y == 5 ? "academy:steel_slab[type=bottom]" : y == 4 ? "academy:ceiling_light" : null;
                case BENCH -> "minecraft:oak_stairs[facing=" + facing + "]";
                case SIGN -> y == 4 ? (variant == 0 ? "academy:neon_cyan" : "academy:neon_amber")
                        : y == 3 ? "academy:led_screen" : "academy:pillar_white";
                case PLANTER -> variant == 0 ? "minecraft:potted_dandelion"
                        : variant == 1 ? "minecraft:potted_allium" : "minecraft:potted_azure_bluet";
            };
        }
    }
}
