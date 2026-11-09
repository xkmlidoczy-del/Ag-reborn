package cn.academy.port.city;

import java.util.List;

/** A raised, lit pedestrian crossing over an underground station's live track. */
final class CityMetroCrossing {
    private CityMetroCrossing() {}

    static void append(List<CityTransit.Over> out, int x, int z, int alongCenter,
                       int crossCenter, int railY, boolean eastWest, int crossing) {
        int along = eastWest ? x - alongCenter : z - alongCenter;
        int cross = eastWest ? z - crossCenter : x - crossCenter;
        // The caller positions the crossing away from its station entrance.
        int offset = Math.abs(along - crossing), side = Math.abs(cross);
        if(offset > 2 || side > 9)return;
        int floor = side <= 3 ? railY + 5 : railY + 8 - side;
        if(offset <= 1) {
            if(side >= 4) {
                column(out,railY-2,floor-1,"academy:reinforced_concrete");
                String facing = eastWest ? (cross < 0 ? "south" : "north") : (cross < 0 ? "east" : "west");
                out.add(new CityTransit.Over(floor,"minecraft:quartz_stairs[facing="+facing+",half=bottom]"));
            } else out.add(new CityTransit.Over(floor,side==3?"academy:steel_slab[type=bottom]":"academy:brushed_steel"));
            column(out,floor+1,railY+9,"minecraft:air");
            out.add(new CityTransit.Over(railY+10,"academy:reinforced_concrete"));
            if(along==crossing && (cross==-6 || cross==0 || cross==6))
                out.add(new CityTransit.Over(railY+9,"academy:ceiling_light"));
        } else {
            // The station wall is immediately beyond side 9. Open the two lowest
            // steps laterally so passengers can enter from the platform itself.
            if(side>=8) column(out,railY,railY+9,"minecraft:air");
            else {
                out.add(new CityTransit.Over(floor,"academy:brushed_steel"));
                String rail = eastWest ? "minecraft:iron_bars[north=true,south=true]"
                        : "minecraft:iron_bars[east=true,west=true]";
                out.add(new CityTransit.Over(floor+1,rail));
                out.add(new CityTransit.Over(floor+2,rail));
                column(out,floor+3,railY+9,"minecraft:air");
            }
            out.add(new CityTransit.Over(railY+10,"academy:reinforced_concrete"));
        }
    }

    private static void column(List<CityTransit.Over> out,int from,int to,String block) {
        for(int y=from;y<=to;y++)out.add(new CityTransit.Over(y,block));
    }
}
