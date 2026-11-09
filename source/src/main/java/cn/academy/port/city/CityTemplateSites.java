package cn.academy.port.city;

/** Chunk-independent placement rules for authored building templates and the D15 complex. */
public final class CityTemplateSites {
    static final int COMPLEX_X = -78, COMPLEX_Z = 700, COMPLEX_WIDTH = 157, COMPLEX_DEPTH = 167;
    private static final String[] STYLES = {"academic", "lab", "residential", "commercial"};

    record Site(String id, int originX, int originZ, int reservedWidth, int reservedDepth) {}

    private CityTemplateSites() {}

    /** Legacy reservation flag: the lot may host its authored template or a selected low-rise replacement. */
    public static boolean usesTemplate(long seed, int x, int z) { return at(seed, x, z) != null; }

    static Site at(long seed, int x, int z) {
        if (x >= COMPLEX_X && x < COMPLEX_X + COMPLEX_WIDTH && z >= COMPLEX_Z && z < COMPLEX_Z + COMPLEX_DEPTH) {
            return new Site("grand_complex", COMPLEX_X, COMPLEX_Z, COMPLEX_WIDTH, COMPLEX_DEPTH);
        }
        int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL), lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
        if (lx < CityPlan.ARTERIAL_HALF || lx >= CityPlan.CELL - CityPlan.ARTERIAL_HALF
                || lz < CityPlan.ARTERIAL_HALF || lz >= CityPlan.CELL - CityPlan.ARTERIAL_HALF) return null;
        int ru = (lx - CityPlan.ARTERIAL_HALF) % CityPlan.BLOCK, rw = (lz - CityPlan.ARTERIAL_HALF) % CityPlan.BLOCK;
        if (ru < 12 || ru >= 60 || rw < 12 || rw >= 60) return null;
        int ox = x - ru + 12, oz = z - rw + 12;
        if (CityAirportTransit.overlapsLot(ox, oz, 48, 48) || CityNorthSouthTransit.overlapsLot(ox, oz, 48, 48)
                || CityRingTransit.overlapsLot(ox, oz, 48, 48) || CityMetroTransit.entranceReserved(ox, oz, 48, 48) || CityMetroNorthTransit.entranceReserved(ox, oz, 48, 48) || CityMetroAirportTransit.entranceReserved(ox, oz, 48, 48)) return null;
        if (CityDistrictLandmarks.ownsLot(ox + 24, oz + 24)) return null;
        if (overlaps(ox, oz, 48, 48, -112, -100, 97, 810)
                || overlaps(ox, oz, 48, 48, -110, -110, 220, 220)
                || overlaps(ox, oz, 48, 48, -1900, 370, 3801, 61)
                || overlaps(ox, oz, 48, 48, COMPLEX_X, COMPLEX_Z, COMPLEX_WIDTH, COMPLEX_DEPTH)) return null;
        var sample = CityPlan.sample(seed, ox + 24, oz + 24);
        if (sample.kind() != CityPlan.Kind.BUILDING || sample.bw() == 0 || sample.district() > 20) return null;
        long hash = sample.hash();
        if (Long.remainderUnsigned(hash >>> 12, 3) == 0) return null; // retain some existing procedural facades
        int bx = (lx - CityPlan.ARTERIAL_HALF) / CityPlan.BLOCK;
        int bz = (lz - CityPlan.ARTERIAL_HALF) / CityPlan.BLOCK;
        int style = CityDistrictZoning.templateStyle(sample.district(), bx, bz);
        int variant = (int) Long.remainderUnsigned(hash >>> 22, 10) + 1;
        if (sample.district() == 7 || sample.district() == 20) variant = new int[]{1, 2, 7, 9}[(variant - 1) % 4];
        return new Site(STYLES[style] + "_" + (variant < 10 ? "0" : "") + variant, ox, oz, 48, 48);
    }

    private static boolean overlaps(int ax, int az, int aw, int ad, int bx, int bz, int bw, int bd) {
        return ax < bx + bw && ax + aw > bx && az < bz + bd && az + ad > bz;
    }
}
