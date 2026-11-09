package cn.academy.port.city;

import cn.academy.port.train.CityMetroAirportLine;
import cn.academy.port.train.TrainLine;
import java.util.ArrayList;
import net.minecraft.core.BlockPos;

/** Airport subway, separated from the lower north–south line and the airfield surface. */
public final class CityMetroAirportTransit {
    private CityMetroAirportTransit() {}
    private static boolean intersects(int x,int z,int w,int d,int a,int b,int aw,int ad) {
        return x<a+aw && x+w>a && z<b+ad && z+d>b;
    }
    /** End stations use the north interchange and terminal concourse respectively. */
    public static boolean hasSurfaceEntrance(int station) { return station==1 || station==2; }
    public static int entranceX(int station) { return CityMetroAirportLine.STATIONS.get(station).x()+67; }
    public static boolean entranceReserved(int x,int z,int w,int d) {
        for(int i=1;i<=2;i++) {
            int c=CityMetroAirportLine.STATIONS.get(i).x();
            if(intersects(x,z,w,d,c+49,CityMetroAirportLine.Z-10,21,8))return true;
        }
        return false;
    }
    public static BlockPos bay(int station,int role,int side,int y) {
        return new BlockPos(CityMetroAirportLine.STATIONS.get(station).x()+(int)Math.round((role-1)*TrainLine.CAR_PITCH),y,CityMetroAirportLine.Z+side);
    }
    public static CityTransit.Over[] overlay(int x,int z) {
        int side=z-CityMetroAirportLine.Z;
        boolean track=x>=-150 && x<=1670 && Math.abs(side)<=11;
        boolean northTransfer=x>=-96 && x<=-86 && z>=-1624 && z<=-1582;
        boolean airportPassage=x>=1607 && x<=1609 && z>=-1831 && z<=-1640;
        boolean terminalStair=x>=1607 && x<=1625 && z>=-1832 && z<=-1828;
        // Beyond the track terminus, passengers can cross between both platforms
        // without stepping onto a live railway or relying on open platform gates.
        boolean terminalConcourse=x>=1671 && x<=1675 && z>=-1640 && z<=-1624;
        if(!track && !northTransfer && !airportPassage && !terminalStair && !terminalConcourse)return null;
        int station=CityMetroAirportLine.stationNear(x,50);
        int along=station<0?0:x-CityMetroAirportLine.STATIONS.get(station).x();
        boolean platform=track && station>=0 && Math.abs(side)<=10;
        boolean tunnel=track && Math.abs(side)<=5;
        var out=new ArrayList<CityTransit.Over>();
        if(platform || tunnel) {
            String floor=platform && Math.abs(side)>=3?"academy:plaza_tile":"academy:reinforced_concrete";
            if(Math.abs(side)<=1)floor="academy:brushed_steel";
            if(platform && Math.abs(side)==3)floor="minecraft:yellow_concrete";
            out.add(new CityTransit.Over(-16,floor));column(out,-15,-9,"minecraft:air");
            out.add(new CityTransit.Over(-8,"academy:reinforced_concrete"));
            if(!platform && Math.abs(side)==5)column(out,-15,-9,"academy:reinforced_concrete");
            if(platform && Math.abs(side)==10)column(out,-15,-9,"academy:white_panel_wall");
            if(platform && Math.abs(side)<=9 && Math.floorMod(along,8)==0 && Math.floorMod(side,3)==0)
                out.add(new CityTransit.Over(-9,"academy:ceiling_light"));
            if(platform) {
                boolean bay=false;
                for(int role=0;role<3;role++) {
                    int offset=(int)Math.round((role-1)*TrainLine.CAR_PITCH);
                    if(Math.abs(along-offset)<=2)bay=true;
                    if(along==offset && Math.abs(side)==9)out.add(new CityTransit.Over(-15,"academy:led_screen"));
                    if(Math.abs(along-offset)<=2 && Math.abs(side)==3) {
                        String p="[facing="+(side<0?"north":"south")+",hinge="+(Math.floorMod(along,2)==0?"left":"right")+",open=false,powered=false,half=";
                        out.add(new CityTransit.Over(-15,"academy:platform_gate"+p+"lower]"));
                        out.add(new CityTransit.Over(-14,"academy:platform_gate"+p+"upper]"));
                    }
                }
                if(!bay && Math.abs(side)==2)out.add(new CityTransit.Over(-15,"minecraft:iron_bars"));
            }
        }
        if(terminalConcourse) {
            out.add(new CityTransit.Over(-16,x==1673?"minecraft:lime_concrete":"academy:plaza_tile"));
            column(out,-15,-11,"minecraft:air");
            out.add(new CityTransit.Over(-10,"academy:reinforced_concrete"));
            if(x==1673 && (z==-1638 || z==-1632 || z==-1626))out.add(new CityTransit.Over(-11,"academy:ceiling_light"));
        }
        // The north interchange avoids all boarding screens and descends on the
        // east side of the north–south platform, away from its surface stair.
        boolean northUpper=x>=-89 && x<=-87 && z>=-1624 && z<=-1617
                || x>=-94 && x<=-87 && z>=-1619 && z<=-1617
                || x>=-94 && x<=-92 && z>=-1619 && z<=-1598;
        if(northUpper) {
            out.add(new CityTransit.Over(-16,"academy:plaza_tile"));column(out,-15,-11,"minecraft:air");
            out.add(new CityTransit.Over(-10,"academy:reinforced_concrete"));
            if(x==-93 && Math.floorMod(z,8)==0)out.add(new CityTransit.Over(-11,"academy:ceiling_light"));
        }
        if(z==-1625 && x>=-99 && x<=-87)out.add(new CityTransit.Over(-16,"minecraft:lime_concrete"));
        if(z==-1625 && x>=-89 && x<=-87) {
            out.add(new CityTransit.Over(-11,"academy:led_screen"));column(out,-10,-9,"academy:brushed_steel");
        }
        if(z>=-1598 && z<=-1582) {
            int step=-1614-z;
            if(x>=-94 && x<=-92) {
                out.add(new CityTransit.Over(step-1,"academy:reinforced_concrete"));
                out.add(new CityTransit.Over(step,"minecraft:quartz_stairs[facing=north,half=bottom]"));
                column(out,step+1,-11,"minecraft:air");
            }
            if(x==-95 || x==-91)for(int y=step+1;y<=-10;y++) {
                boolean platformOpening=x==-95 && z>=-1584 && y<=-27;
                if(!platformOpening)out.add(new CityTransit.Over(y,"academy:reinforced_concrete"));
            }
            if(x==-91 && (z==-1594 || z==-1588 || z==-1582)) {
                out.add(new CityTransit.Over(step+2,"academy:reinforced_concrete"));
                out.add(new CityTransit.Over(step+3,"minecraft:lantern[hanging=false,waterlogged=false]"));
            }
        }
        // Two intermediate stops have separate surface entrances.
        for(int i=1;i<=2;i++) {
            int a=x-CityMetroAirportLine.STATIONS.get(i).x();
            if(a>=50 && a<=65 && side>=-8 && side<=-6) {
                int step=a-66;
                out.add(new CityTransit.Over(step-1,"academy:reinforced_concrete"));
                out.add(new CityTransit.Over(step,"minecraft:quartz_stairs[facing=east,half=bottom]"));
                column(out,step+1,3,"minecraft:air");
            }
            if(a>=50 && a<65 && (side==-9 || side==-5)) {
                out.add(new CityTransit.Over(0,"minecraft:iron_bars[east=true,west=true]"));
                out.add(new CityTransit.Over(1,"minecraft:iron_bars[east=true,west=true]"));
            }
            if(a==49 && side>=-9 && side<=-5) {
                out.add(new CityTransit.Over(0,"minecraft:iron_bars[north=true,south=true]"));
                out.add(new CityTransit.Over(1,"minecraft:iron_bars[north=true,south=true]"));
            }
            if(a>=66 && a<=67 && side>=-9 && side<=-5) {
                out.add(new CityTransit.Over(-1,"academy:plaza_tile"));column(out,0,2,"minecraft:air");
            }
            if(a==67 && (side==-9 || side==-5)) {
                column(out,0,3,"academy:pillar_white");out.add(new CityTransit.Over(4,"academy:brushed_steel"));
            }
            if(a==67 && side>=-8 && side<=-6) {
                out.add(new CityTransit.Over(4,"academy:led_screen"));
                if(side==-7)out.add(new CityTransit.Over(3,"academy:ceiling_light"));
            }
            if(side==-9 && (a==53 || a==61)) {
                int step=a-66;
                out.add(new CityTransit.Over(step+2,"academy:reinforced_concrete"));
                out.add(new CityTransit.Over(step+3,"minecraft:lantern[hanging=false,waterlogged=false]"));
            }
        }
        // The covered corridor runs beneath the apron; only the terminal stair
        // touches the surface, safely inside the central passenger hall.
        if(airportPassage) {
            out.add(new CityTransit.Over(-16,"academy:plaza_tile"));column(out,-15,-11,"minecraft:air");
            out.add(new CityTransit.Over(-10,"academy:reinforced_concrete"));
            if(x==1608 && Math.floorMod(z,8)==0)out.add(new CityTransit.Over(-11,"academy:ceiling_light"));
        }
        if(x>=1608 && x<=1623 && z>=-1831 && z<=-1829) {
            int step=x-1624;
            out.add(new CityTransit.Over(step-1,"academy:reinforced_concrete"));
            out.add(new CityTransit.Over(step,"minecraft:quartz_stairs[facing=east,half=bottom]"));
            column(out,step+1,3,"minecraft:air");
        }
        if(x>=1608 && x<1623 && (z==-1832 || z==-1828)) {
            out.add(new CityTransit.Over(0,"minecraft:iron_bars[east=true,west=true]"));
            out.add(new CityTransit.Over(1,"minecraft:iron_bars[east=true,west=true]"));
        }
        if(x==1607 && z>=-1832 && z<=-1828) {
            out.add(new CityTransit.Over(0,"minecraft:iron_bars[north=true,south=true]"));
            out.add(new CityTransit.Over(1,"minecraft:iron_bars[north=true,south=true]"));
        }
        if(x>=1624 && x<=1625 && z>=-1832 && z<=-1828) {
            out.add(new CityTransit.Over(-1,"academy:composite_floor"));column(out,0,2,"minecraft:air");
        }
        if(x==1625 && (z==-1832 || z==-1828)) {
            column(out,0,3,"academy:pillar_white");out.add(new CityTransit.Over(4,"academy:brushed_steel"));
        }
        if(x==1625 && z>=-1831 && z<=-1829) {
            out.add(new CityTransit.Over(4,"academy:led_screen"));
            if(z==-1830)out.add(new CityTransit.Over(3,"academy:ceiling_light"));
        }
        if(station>=0)CityMetroCrossing.append(out,x,z,CityMetroAirportLine.STATIONS.get(station).x(),CityMetroAirportLine.Z,CityMetroAirportLine.RAIL_Y,true,station==3?35:-35);
        return out.isEmpty()?null:out.toArray(CityTransit.Over[]::new);
    }
    private static void column(ArrayList<CityTransit.Over> out,int from,int to,String block) {
        for(int y=from;y<=to;y++)out.add(new CityTransit.Over(y,block));
    }
}
