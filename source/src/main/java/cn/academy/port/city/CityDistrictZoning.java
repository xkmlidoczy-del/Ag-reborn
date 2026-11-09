package cn.academy.port.city;

/** Adapted district-level building distribution for the explorable city sample. */
public final class CityDistrictZoning {
    public enum Use {
        CIVIC('C', 1, 28, 84, 10), ACADEMIC('A', 0, 12, 36, 24), SCIENCE('S', 1, 12, 48, 9),
        COMMERCIAL('M', 3, 20, 100, 6), RESIDENTIAL('R', 2, 16, 56, 22), INDUSTRIAL('I', 1, 8, 36, 5),
        LEISURE('L', 3, 12, 72, 24), SPORTS('T', 0, 8, 32, 32), GREEN('P', 2, 8, 20, 72);
        final char key; final int style; final int[] profile;
        Use(char key, int style, int min, int max, int parks) {
            this.key = key; this.style = style; this.profile = new int[]{min, max, parks};
        }
    }

    private static final int[] D15_CENTRE = {40, 200, 4};
    private CityDistrictZoning() {}

    /** Each row is north to south; each character is a west/centre/east group of building lots. */
    private static String layout(int district) {
        return switch (district) {
            case 1 -> "RCRCCCRCR"; // administration and civic offices
            case 2 -> "ISISISIII"; // test labs and industrial perimeter
            case 3 -> "SMSMMMRSR"; // research/exhibition with commercial centre
            case 4 -> "RMRMMMRMR"; // food/commercial streets and housing
            case 5 -> "ASAAAARRA"; // universities, labs, housing
            case 6 -> "RLRLMLRMR"; // entertainment centre
            case 7 -> "RARAAARMR"; // schools, housing and small shops
            case 8 -> "RPRRARRMR";
            case 9 -> "RMRRPRRAR";
            case 10 -> "RARRMRRPR";
            case 11 -> "RMRMMRRPR";
            case 12 -> "RARSRRRMR";
            case 13 -> "RPRRMRRAR";
            case 14 -> "RMRRARRPR";
            case 15 -> "RMMMMMMMR"; // commercial core with residential edges
            case 16 -> "RARRPRRMR";
            case 17 -> "ISIIIIISI"; // industry and research buffer
            case 18 -> "RPRASARRM";
            case 19 -> "RSRRMRRPR";
            case 20 -> "TATATARRT"; // sport fields, engineering schools, housing
            default -> "RRRRMRRRR";
        };
    }

    public static Use use(int district, int bx, int bz) {
        int column = Math.min(2, Math.max(0, bx / 4));
        int row = Math.min(2, Math.max(0, bz / 4));
        char key = layout(district).charAt(row * 3 + column);
        return switch (key) {
            case 'C' -> Use.CIVIC; case 'A' -> Use.ACADEMIC; case 'S' -> Use.SCIENCE;
            case 'M' -> Use.COMMERCIAL; case 'R' -> Use.RESIDENTIAL; case 'I' -> Use.INDUSTRIAL;
            case 'L' -> Use.LEISURE; case 'T' -> Use.SPORTS; case 'P' -> Use.GREEN;
            default -> throw new IllegalStateException("Unknown district layout key " + key);
        };
    }

    static int[] profile(int district, int bx, int bz) {
        Use use = use(district, bx, bz);
        return district == 15 && use == Use.COMMERCIAL ? D15_CENTRE : use.profile;
    }

    static int templateStyle(int district, int bx, int bz) { return use(district, bx, bz).style; }

    /** Use at an ordinary lot coordinate in the district's local street grid. */
    static Use at(int district, int x, int z) {
        int lx = Math.floorMod(x + CityPlan.HALF, CityPlan.CELL);
        int lz = Math.floorMod(z + CityPlan.HALF, CityPlan.CELL);
        int bx = Math.max(0, (lx - CityPlan.ARTERIAL_HALF) / CityPlan.BLOCK);
        int bz = Math.max(0, (lz - CityPlan.ARTERIAL_HALF) / CityPlan.BLOCK);
        return use(district, bx, bz);
    }
}
