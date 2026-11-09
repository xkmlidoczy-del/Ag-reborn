package cn.academy.port.city;

/** District-specific public-space samples in existing open park lots, generated one column at a time. */
final class CityNeighborhoodSpaces {
    private static final String PAVE = "academy:plaza_tile";
    private CityNeighborhoodSpaces() {}

    static Column at(long seed, int x, int z, CityPlan.Sample sample) {
        int d = sample.district();
        if (d < 1 || d > 20 || d == 15 || sample.kind() != CityPlan.Kind.PARK) return null;
        int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL), lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
        if (lx < 12 || lx >= 788 || lz < 12 || lz >= 788) return null;
        int u = lx - 12, w = lz - 12, ru = u % 64, rw = w % 64;
        if (ru < 12 || ru >= 60 || rw < 12 || rw >= 60) return null;
        int ox = x - ru + 12, oz = z - rw + 12, cx = ox + 24, cz = oz + 24;
        if (overlaps(ox, oz, -1900, 370, 3801, 61)
                || overlaps(ox, oz, -162, -100, 130, 810)
                || overlaps(ox, oz, -184, -56, 48, 48)
                || d == 7 && overlaps(ox, oz, -110, -110, 220, 220)
                || CityAirportTransit.overlapsLot(ox, oz, 48, 48)
                || CityNorthSouthTransit.overlapsLot(ox, oz, 48, 48)
                || CityRingTransit.overlapsLot(ox, oz, 48, 48)
                || CityMetroTransit.entranceReserved(ox, oz, 48, 48) || CityMetroNorthTransit.entranceReserved(ox, oz, 48, 48) || CityMetroAirportTransit.entranceReserved(ox, oz, 48, 48)
                || CityDistrictLandmarks.at(seed, cx, cz) != null
                || CityTemplateSites.at(seed, cx, cz) != null) return null;
        var center = CityPlan.sample(seed, cx, cz);
        if (center.district() != d || center.kind() != CityPlan.Kind.PARK) return null;
        long hash = mix(seed ^ ((long) ox * 0x9e3779b97f4a7c15L) ^ ((long) oz * 0xc2b2ae3d27d4eb4fL));
        if (Long.remainderUnsigned(hash, 3) == 0) return null;
        var use = CityDistrictZoning.use(d, u / 64, w / 64);
        return new Column(ru - 12, rw - 12, use, (hash & 4) != 0);
    }

    private static boolean overlaps(int x, int z, int a, int b, int width, int depth) {
        return x < a + width && x + 48 > a && z < b + depth && z + 48 > b;
    }
    private static long mix(long x) {
        x ^= x >>> 30; x *= 0xbf58476d1ce4e5b9L;
        x ^= x >>> 27; x *= 0x94d049bb133111ebL;
        return x ^ x >>> 31;
    }

    record Column(int a, int b, CityDistrictZoning.Use use, boolean variant) {
        int top() { return 7; }
        String block(int y) {
            if (y < -1 || y > 7) return null;
            boolean walk = a >= 22 && a <= 24 || b >= 22 && b <= 24;
            boolean plaza = use == CityDistrictZoning.Use.CIVIC || use == CityDistrictZoning.Use.COMMERCIAL
                    || use == CityDistrictZoning.Use.LEISURE;
            boolean industrial = use == CityDistrictZoning.Use.INDUSTRIAL || use == CityDistrictZoning.Use.SCIENCE;
            boolean court = use == CityDistrictZoning.Use.SPORTS && a >= 5 && a <= 19 && b >= 5 && b <= 20;
            int fountain = (a - 34) * (a - 34) + (b - 34) * (b - 34);
            if (y == -1) {
                if (walk) return PAVE;
                if (court) return a == 5 || a == 19 || b == 5 || b == 20 || b == 12
                        ? "minecraft:white_concrete" : "minecraft:green_concrete";
                if (plaza && fountain < 9) return "minecraft:water[level=0]";
                if (plaza && fountain <= 16) return "academy:white_panel_wall";
                if (industrial && a >= 6 && a <= 41 && b >= 6 && b <= 41)
                    return (a == 7 || a == 40) && Math.floorMod(b, 6) < 3 ? "minecraft:yellow_concrete" : "academy:reinforced_concrete";
                if (plaza && a >= 5 && a <= 42 && b >= 5 && b <= 42) return "academy:composite_floor";
                if (variant && !industrial && !plaza && a >= 7 && a <= 40 && b >= 7 && b <= 40
                        && (a == 7 || a == 40 || b == 7 || b == 40)) return PAVE;
                return null;
            }
            // Keep both 3-block-wide crossings entirely clear to their sidewalk edges.
            if (walk) return null;
            if (plaza && a == 34 && b == 34 && y <= 3)
                return y == 3 ? "academy:neon_cyan" : "academy:brushed_steel";
            boolean light = !court && (a == 19 || a == 28) && (b == 19 || b == 28);
            if (light && y <= 4) return y == 4 ? "minecraft:lantern[hanging=false,waterlogged=false]" : "academy:pillar_white";
            boolean seat = (a == 20 || a == 26) && (b == 11 || b == 12 || b == 35 || b == 36);
            if (seat && y == 0) return "minecraft:oak_stairs[facing=" + (a == 20 ? "east" : "west") + "]";
            int low = variant ? 10 : 12, high = variant ? 37 : 35;
            int dx = Math.min(Math.abs(a - low), Math.abs(a - high));
            int dz = Math.min(Math.abs(b - low), Math.abs(b - high));
            boolean fountainCorner = plaza && a >= 30 && b >= 30;
            if (industrial && dx <= 1 && dz <= 1) {
                if (y == 0) return "academy:brushed_steel";
                if (dx == 0 && dz == 0 && y >= 1 && y <= 3)
                    return y == 3 ? "academy:neon_amber" : variant ? "academy:brushed_steel" : "academy:blue_panel_wall";
                return null;
            }
            if (!industrial && !fountainCorner && !court && dx <= 2 && dz <= 2) {
                String log = use == CityDistrictZoning.Use.ACADEMIC || use == CityDistrictZoning.Use.LEISURE
                        ? "minecraft:cherry_log" : variant ? "minecraft:birch_log" : "minecraft:oak_log";
                String leaves = use == CityDistrictZoning.Use.ACADEMIC || use == CityDistrictZoning.Use.LEISURE
                        ? "minecraft:cherry_leaves[persistent=true]" : variant
                        ? "minecraft:birch_leaves[persistent=true]" : "minecraft:oak_leaves[persistent=true]";
                if (dx == 0 && dz == 0 && y <= 4) return log;
                if (y >= 4 && y <= 6 && dx + dz <= 3 || y == 7 && dx + dz <= 1) return leaves;
            }
            if (!plaza && !industrial && !court && y == 0 && Math.floorMod(a * 17 + b * 11, 97) == 0)
                return variant ? "minecraft:azure_bluet" : "minecraft:dandelion";
            return null;
        }
    }
}
