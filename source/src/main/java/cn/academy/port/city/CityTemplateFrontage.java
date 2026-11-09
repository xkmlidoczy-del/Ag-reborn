package cn.academy.port.city;

/** Small use-specific forecourts in the unused north setback of authored building lots. */
final class CityTemplateFrontage {
    private CityTemplateFrontage() {}

    static Column at(long seed, int x, int z, CityPlan.Sample sample, CityTemplateLibrary.Column template) {
        return plan(seed, x, z, sample, template != null && template.northSetback());
    }

    /** Pure placement rules, separated so all planned lots can be audited without loading a world. */
    static Column plan(long seed, int x, int z, CityPlan.Sample sample, boolean northSetback) {
        if (!northSetback || sample.district() < 1 || sample.district() > 20) return null;
        int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL), lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
        int u = lx - CityPlan.ARTERIAL_HALF, w = lz - CityPlan.ARTERIAL_HALF;
        if (u < 0 || w < 0) return null;
        int ru = u % CityPlan.BLOCK, rw = w % CityPlan.BLOCK;
        if (ru < 12 || ru >= 60 || rw < 12 || rw >= 60) return null;
        int a = ru - 12, b = rw - 12, ox = x - a, oz = z - b;
        long h = seed ^ ((long) ox * 0x9e3779b97f4a7c15L) ^ ((long) oz * 0xc2b2ae3d27d4eb4fL);
        h ^= h >>> 30; h *= 0xbf58476d1ce4e5b9L; h ^= h >>> 27; h *= 0x94d049bb133111ebL; h ^= h >>> 31;
        return new Column(a, b, CityDistrictZoning.at(sample.district(), x, z), (int) Long.remainderUnsigned(h, 3));
    }

    record Column(int a, int b, CityDistrictZoning.Use use, int variant) {
        int top() { return 6; }
        String block(int y) {
            if (y < -1 || y > 6) return null;
            boolean entry = a >= 21 && a <= 26;
            boolean green = switch (use) {
                case ACADEMIC, RESIDENTIAL, SPORTS, GREEN -> true;
                default -> false;
            };
            boolean retail = use == CityDistrictZoning.Use.COMMERCIAL || use == CityDistrictZoning.Use.LEISURE;
            boolean technical = use == CityDistrictZoning.Use.SCIENCE || use == CityDistrictZoning.Use.INDUSTRIAL;
            if (y == -1) {
                if (entry || b == 0) return "academy:plaza_tile";
                if (technical) return b == 1 && Math.floorMod(a, 4) < 2 ? "minecraft:yellow_concrete" : "academy:reinforced_concrete";
                return green ? "minecraft:grass_block" : "academy:plaza_tile";
            }
            // Six clear columns keep the entrance aligned with the street sidewalk.
            if (entry || b == 0) return null;
            if (green && variant == 0) {
                int dx = Math.min(Math.abs(a - 8), Math.abs(a - 39)), dz = Math.abs(b - 3);
                if (dx == 0 && dz == 0 && y <= 4)
                    return use == CityDistrictZoning.Use.ACADEMIC ? "minecraft:cherry_log" : "minecraft:oak_log";
                if (y >= 3 && y <= 5 && dx <= 2 && dz <= 2 && dx + dz <= 3 || y == 6 && dx + dz <= 1)
                    return use == CityDistrictZoning.Use.ACADEMIC
                            ? "minecraft:cherry_leaves[persistent=true]" : "minecraft:oak_leaves[persistent=true]";
            }
            if (b == 3 && a == (variant == 1 ? 29 : 18) && variant != 2 && y <= 3)
                return y == 3 ? "minecraft:lantern[hanging=false,waterlogged=false]"
                        : retail || technical ? "academy:pillar_dark" : "academy:pillar_white";
            if (b == 3 && (a == 8 || a == 39)) {
                if (retail && a == (variant == 1 ? 39 : 8)) {
                    if (y == 0) return "academy:pillar_dark";
                    if (y <= 2) return "academy:led_screen";
                    if (y == 3) return "academy:steel_slab[type=bottom]";
                }
                if (technical) {
                    if (y == 0) return "academy:reinforced_concrete";
                    if (y == 1) return "academy:brushed_steel";
                    if (y == 2) return "academy:neon_amber";
                }
                if (!retail && !technical && (!green || variant != 0) && y == 0)
                    return variant == 1 ? "minecraft:potted_allium" : "minecraft:potted_dandelion";
            }
            if (b == 4 && (a == 15 || a == 32) && !technical && y == 0)
                return "minecraft:oak_stairs[facing=south]";
            return null;
        }
    }
}
