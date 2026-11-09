package cn.academy.port.city;

import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;

/**
 * Per-coordinate facade grammar for ordinary buildings: 4-block floors, piers every 4th bay, windows recessed one block with a
 * sill, spandrel rows in the district colour, a storefront ground floor with a street door, a flat roof with a parapet and
 * occasional lit windows behind the glass. Pure function of the footprint sample and height, so chunks stay independent.
 */
final class CityFacade {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private CityFacade() {}

    private enum Group { CAMPUS, COMMERCIAL, INDUSTRIAL, RESIDENTIAL }

    private static Group group(CityDistrictZoning.Use use) {
        return switch (use) {
            case CIVIC, ACADEMIC, SPORTS -> Group.CAMPUS;
            case SCIENCE, COMMERCIAL, LEISURE -> Group.COMMERCIAL;
            case INDUSTRIAL -> Group.INDUSTRIAL;
            case RESIDENTIAL, GREEN -> Group.RESIDENTIAL;
        };
    }

    private static BlockState academy(String id) { return CityBlocks.block(id).defaultBlockState(); }

    private static BlockState slab(String id, SlabType type) {
        return CityBlocks.block(id).defaultBlockState().setValue(SlabBlock.TYPE, type);
    }

    private static BlockState pier(Group g) {
        return academy(g == Group.COMMERCIAL ? "pillar_dark" : g == Group.INDUSTRIAL ? "brushed_steel" : "pillar_white");
    }

    private static BlockState glass(Group g, long hash) {
        return switch (g) {
            case COMMERCIAL -> academy(hash % 3 == 0 ? "curtain_glass_teal" : "curtain_glass_smoke");
            case RESIDENTIAL -> academy("curtain_glass_clear");
            default -> academy("curtain_glass_blue");
        };
    }

    private static BlockState wallBlock(Group g, BlockState districtColour, boolean spandrel) {
        if (spandrel) return districtColour;
        return switch (g) {
            case COMMERCIAL -> academy("dark_panel_wall");
            case INDUSTRIAL -> academy("brushed_steel");
            case RESIDENTIAL -> academy("aluminum_panel");
            default -> academy("white_panel_wall");
        };
    }

    private static long hash(long lot, int a, int b, int f) {
        long x = lot ^ (a * 0x9E3779B97F4A7C15L) ^ (b * 0xC2B2AE3D27D4EB4FL) ^ (f * 0x165667B19E3779F9L);
        x = (x ^ (x >>> 33)) * 0xFF51AFD7ED558CCDL;
        return (x ^ (x >>> 33)) & Long.MAX_VALUE;
    }

    /** h = blocks above the ground surface; the sample must be a facade building (bw &gt; 0). */
    static BlockState blockAt(CityPlan.Sample s, int h, BlockState districtColour, CityDistrictZoning.Use use) {
        int bw = s.bw(), bd = s.bd(), a = s.a(), b = s.b(), top = s.height();
        Group g = group(use);
        if (h > top + 5) return AIR;
        boolean edge = a == 0 || a == bw - 1 || b == 0 || b == bd - 1;
        if (h >= top - 1) {
            if (h == top && edge) return academy("dark_panel_wall");                       // parapet
            String detail = CityRooftop.block(s.hash(), a, b, bw, bd, h - top);
            if (detail != null) return CityGreyboxGenerator.parse(detail);
            return h == top - 1 ? academy("reinforced_concrete") : AIR;
        }
        int f = h / 4, k = h % 4;
        int d = Math.min(Math.min(a, bw - 1 - a), Math.min(b, bd - 1 - b));
        if (d >= 2) return k == 0 && f > 0 ? wallBlock(g, districtColour, false) : AIR;     // hollow interior with floor slabs
        // which wall are we on: the nearest one decides the bay index
        boolean zWall = Math.min(b, bd - 1 - b) <= Math.min(a, bw - 1 - a);
        int idx = zWall ? a : b, len = zWall ? bw - 1 : bd - 1;
        boolean corner = (a == 0 || a == bw - 1) && (b == 0 || b == bd - 1);
        boolean pier = corner || idx % 4 == 0 || idx == len;
        if (d == 1) {                                                                        // inset plane: glass behind recessed windows
            if (k == 0) return f > 0 ? wallBlock(g, districtColour, false) : AIR;
            boolean window = !pier && g != Group.INDUSTRIAL && (f > 0 ? k <= 2 : k <= 3);
            if (window) {
                long windowHash = hash(s.hash(), a, b, f);
                if (k == 2 && windowHash % 5 == 0) return academy("lit_window");
                return glass(g, windowHash);
            }
            return AIR;
        }
        // perimeter plane
        if (f == 0) {                                                                        // storefront
            if (b == 0 && Math.abs(a - bw / 2) <= 1 && k >= 1) return AIR;                   // entrance
            if (k == 0) return academy("brushed_steel");
            return pier || g == Group.INDUSTRIAL ? pier(g) : glass(g, s.hash());
        }
        if (pier) return pier(g);
        if (g == Group.INDUSTRIAL) return k == 0 ? districtColour : wallBlock(g, districtColour, false);
        return switch (k) {
            case 0 -> wallBlock(g, districtColour, true);                                    // spandrel in the district colour
            case 1 -> slab(g == Group.COMMERCIAL ? "steel_slab" : "white_panel_slab", SlabType.BOTTOM);   // sill
            case 2 -> AIR;                                                                    // recessed window opening
            default -> wallBlock(g, districtColour, false);
        };
    }
}
