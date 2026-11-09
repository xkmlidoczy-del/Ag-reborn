package cn.academy.port.city;

import cn.academy.port.train.CityMetroNorthLine;
import cn.academy.port.train.TrainLine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** A second underground line, separated vertically from the east–west metro. */
public final class CityMetroNorthTransit {
    private CityMetroNorthTransit() {}
    private static boolean intersects(int x,int z,int w,int d,int a,int b,int aw,int ad) {
        return x<a+aw && x+w>a && z<b+ad && z+d>b;
    }
    /** Central station uses an underground transfer rather than another surface shaft. */
    public static boolean hasSurfaceEntrance(int station) { return station != 3; }
    public static int entranceZ(int station) { return CityMetroNorthLine.STATIONS.get(station).z()+83; }
    public static boolean entranceReserved(int x,int z,int w,int d) {
        // D7 campus entrance joins the existing west promenade.
        if(intersects(x,z,w,d,-108,51,27,6))return true;
        for(int i=0;i<CityMetroNorthLine.STATIONS.size();i++) {
            if(!hasSurfaceEntrance(i)) continue;
            int c=CityMetroNorthLine.STATIONS.get(i).z();
            if(intersects(x,z,w,d,CityMetroNorthLine.X-10,c+49,7,35))return true;
        }
        return false;
    }
    public static BlockPos bay(int station,int role,int side,int y) {
        return new BlockPos(CityMetroNorthLine.X+side,y,
                CityMetroNorthLine.STATIONS.get(station).z()+(int)Math.round((role-1)*TrainLine.CAR_PITCH));
    }
    public static CityTransit.Over[] overlay(int x,int z) {
        int side=x-CityMetroNorthLine.X;
        boolean campusLink=x>=-108 && x<=-82 && z>=54 && z<=56
                || x>=-108 && x<=-106 && z>=51 && z<=55;
        if(!campusLink && (Math.abs(side)>14 || z < -1732 || z > 1472))return null;
        int station=CityMetroNorthLine.stationNear(z,50);
        int along=station<0?0:z-CityMetroNorthLine.STATIONS.get(station).z();
        boolean platform=station>=0 && Math.abs(side)<=10;
        boolean tunnel=Math.abs(side)<=5;
        var out=new ArrayList<CityTransit.Over>();
        if(campusLink) {
            out.add(new CityTransit.Over(-1,"academy:plaza_tile"));
            column(out,0,3,"minecraft:air");
        }
        if(platform || tunnel) {
            String floor=platform && Math.abs(side)>=3?"academy:plaza_tile":"academy:reinforced_concrete";
            if(Math.abs(side)<=1)floor="academy:brushed_steel";
            if(platform && Math.abs(side)==3)floor="minecraft:yellow_concrete";
            out.add(new CityTransit.Over(-32,floor));
            column(out,-31,-25,"minecraft:air");
            out.add(new CityTransit.Over(-24,"academy:reinforced_concrete"));
            if(!platform && Math.abs(side)==5)column(out,-31,-25,"academy:reinforced_concrete");
            if(platform && Math.abs(side)==10)column(out,-31,-25,"academy:white_panel_wall");
            if(platform && Math.abs(side)<=9 && Math.floorMod(along,8)==0 && Math.floorMod(side,3)==0)
                out.add(new CityTransit.Over(-25,"academy:ceiling_light"));
            if(platform) {
                boolean bay=false;
                for(int role=0;role<3;role++) {
                    int offset=(int)Math.round((role-1)*TrainLine.CAR_PITCH);
                    if(Math.abs(along-offset)<=2)bay=true;
                    if(along==offset && Math.abs(side)==9)out.add(new CityTransit.Over(-31,"academy:led_screen"));
                    if(Math.abs(along-offset)<=2 && Math.abs(side)==3) {
                        String p="[facing="+(side<0?"west":"east")+",hinge="+(Math.floorMod(along,2)==0?"left":"right")+",open=false,powered=false,half=";
                        out.add(new CityTransit.Over(-31,"academy:platform_gate"+p+"lower]"));
                        out.add(new CityTransit.Over(-30,"academy:platform_gate"+p+"upper]"));
                    }
                }
                if(!bay && Math.abs(side)==2)out.add(new CityTransit.Over(-31,"minecraft:iron_bars"));
            }
        }
        // Underground passage begins on the east–west platform at (-112, 608),
        // bypassing its boarding screens at x=-106, -100 and -94.
        boolean upperPassage = x>=-113 && x<=-111 && z>=608 && z<=615
                || x>=-113 && x<=-106 && z>=613 && z<=615
                || x>=-108 && x<=-106 && z>=613 && z<=634;
        if(upperPassage) {
            out.add(new CityTransit.Over(-16,"academy:plaza_tile"));
            column(out,-15,-11,"minecraft:air");
            out.add(new CityTransit.Over(-10,"academy:reinforced_concrete"));
            if(Math.floorMod(z,8)==0 && x==-107)out.add(new CityTransit.Over(-11,"academy:ceiling_light"));
        }
        // A coloured floor strip and overhead screens identify the transfer on both levels.
        if(z==607 && x>=-114 && x<=-101)out.add(new CityTransit.Over(-16,"minecraft:purple_concrete"));
        if(z==607 && x>=-113 && x<=-111) {
            out.add(new CityTransit.Over(-11,"academy:led_screen"));
            column(out,-10,-9,"academy:brushed_steel");
        }
        if(x==-105 && z>=600 && z<=649)out.add(new CityTransit.Over(-32,"minecraft:purple_concrete"));
        // Descend south from the passage to the lower platform. The side opening
        // at the last three steps lets passengers turn onto that platform.
        if(z>=634 && z<=650) {
            int step=618-z;
            if(side>=-8 && side<=-6) {
                out.add(new CityTransit.Over(step-1,"academy:reinforced_concrete"));
                out.add(new CityTransit.Over(step,"minecraft:quartz_stairs[facing=north,half=bottom]"));
                column(out,step+1,-11,"minecraft:air");
            }
            if(side==-9 || side==-5) for(int y=step+1;y<=-10;y++) {
                boolean platformOpening=side==-5 && z>=648 && y<=-27;
                if(!platformOpening)out.add(new CityTransit.Over(y,"academy:reinforced_concrete"));
            }
        }
        if(side==-9 && (z==638 || z==644 || z==650)) {
            int step=618-z;
            out.add(new CityTransit.Over(step+2,"academy:reinforced_concrete"));
            out.add(new CityTransit.Over(step+3,"minecraft:lantern[hanging=false,waterlogged=false]"));
        }
        for(int i=0;i<CityMetroNorthLine.STATIONS.size();i++) {
            if(!hasSurfaceEntrance(i))continue;
            int a=z-CityMetroNorthLine.STATIONS.get(i).z();
            if(a>=50 && a<=81 && side>=-8 && side<=-6) {
                int step=a-82;
                out.add(new CityTransit.Over(step-1,"academy:reinforced_concrete"));
                out.add(new CityTransit.Over(step,"minecraft:quartz_stairs[facing=south,half=bottom]"));
                column(out,step+1,3,"minecraft:air");
            }
            if(side==-9 && (a==53 || a==61 || a==69 || a==77)) {
                int step=a-82;
                out.add(new CityTransit.Over(step+2,"academy:reinforced_concrete"));
                out.add(new CityTransit.Over(step+3,"minecraft:lantern[hanging=false,waterlogged=false]"));
            }
            if(a>=50 && a<81 && (side==-9 || side==-5)) {
                out.add(new CityTransit.Over(0,"minecraft:iron_bars[north=true,south=true]"));
                out.add(new CityTransit.Over(1,"minecraft:iron_bars[north=true,south=true]"));
            }
            if(a==49 && side>=-9 && side<=-5) {
                out.add(new CityTransit.Over(0,"minecraft:iron_bars[east=true,west=true]"));
                out.add(new CityTransit.Over(1,"minecraft:iron_bars[east=true,west=true]"));
            }
            if(a>=82 && a<=83 && side>=-9 && side<=-5) {
                out.add(new CityTransit.Over(-1,"academy:plaza_tile"));column(out,0,2,"minecraft:air");
            }
            if(a==83 && (side==-9 || side==-5)) {
                column(out,0,3,"academy:pillar_white");out.add(new CityTransit.Over(4,"academy:brushed_steel"));
            }
            if(a==83 && side>=-8 && side<=-6) {
                out.add(new CityTransit.Over(4,"academy:led_screen"));
                if(side==-7)out.add(new CityTransit.Over(3,"academy:ceiling_light"));
            }
        }
        if(station>=0)CityMetroCrossing.append(out,x,z,CityMetroNorthLine.STATIONS.get(station).z(),CityMetroNorthLine.X,CityMetroNorthLine.RAIL_Y,false,-35);
        return out.isEmpty()?null:out.toArray(CityTransit.Over[]::new);
    }
    private static void column(List<CityTransit.Over> out,int from,int to,String block) {
        for(int y=from;y<=to;y++)out.add(new CityTransit.Over(y,block));
    }
}
