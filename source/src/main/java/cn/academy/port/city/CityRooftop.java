package cn.academy.port.city;

/** Four compact rooftop treatments, confined to an ordinary facade footprint. */
final class CityRooftop {
    private CityRooftop() {}
    static int variant(long hash) { return (int)((hash >>> 24) & 3); }
    /** Relative y=0 is above the roof slab; -1 is the slab itself. */
    static String block(long hash,int a,int b,int width,int depth,int y) {
        int x=a-width/2,z=b-depth/2,ax=Math.abs(x),az=Math.abs(z);
        return switch (variant(hash)) {
            case 0 -> {
                if (ax<=2 && az<=1 && y>=0 && y<=2)
                    yield y==2 ? "academy:dark_panel_wall" : "academy:brushed_steel";
                yield null;
            }
            case 1 -> {
                if (ax==4 && az<=3 && y==0) yield "minecraft:grass_block";
                if (ax==4 && az<=3 && y==1) yield "minecraft:flowering_azalea_leaves[persistent=true]";
                yield null;
            }
            case 2 -> {
                if (x==0 && z==0 && y>=0 && y<=5)
                    yield y==5 ? "academy:neon_cyan" : "academy:pillar_white";
                yield null;
            }
            default -> {
                if (y==-1 && ((ax==3 && az<=3) || (az==0 && ax<=3))) yield "minecraft:white_concrete";
                if (y==0 && ax==5 && az==5) yield "academy:neon_amber";
                yield null;
            }
        };
    }
}
