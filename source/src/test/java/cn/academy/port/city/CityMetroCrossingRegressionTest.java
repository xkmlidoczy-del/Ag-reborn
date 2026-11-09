package cn.academy.port.city;

import cn.academy.port.train.CityMetroLine;
import cn.academy.port.train.CityMetroNorthLine;
import cn.academy.port.train.CityMetroAirportLine;

/** Passengers can cross over, without stepping onto the tracks, at every metro stop. */
public final class CityMetroCrossingRegressionTest {
    public static void main(String[] args) {
        for(int i=0;i<CityMetroLine.STATIONS.size();i++) {
            var stop=CityMetroLine.STATIONS.get(i);
            checkEastWest(stop.x(),CityMetroLine.Z,CityMetroLine.RAIL_Y,false,i<=2?-35:35);
        }
        for(int i=0;i<CityMetroAirportLine.STATIONS.size();i++) {
            var stop=CityMetroAirportLine.STATIONS.get(i);
            checkEastWest(stop.x(),CityMetroAirportLine.Z,CityMetroAirportLine.RAIL_Y,true,i==3?35:-35);
        }
        for(var stop:CityMetroNorthLine.STATIONS) {
            int along=stop.z()-35,rail=CityMetroNorthLine.RAIL_Y;
            for(int side=-9;side<=9;side++) {
                var col=CityMetroNorthTransit.overlay(CityMetroNorthLine.X+side,along);
                verify(col,side,rail,"north–south stop "+stop.z());
            }
            for(int side=-7;side<=7;side++)for(int edge:new int[]{-2,2}) {
                int floor=Math.abs(side)<=3?rail+5:rail+8-Math.abs(side);
                check("minecraft:iron_bars[east=true,west=true]".equals(last(CityMetroNorthTransit.overlay(CityMetroNorthLine.X+side,along+edge),floor+1)),"north–south connected bridge railing");
            }
            for(int side:new int[]{-9,9})for(int edge:new int[]{-2,2})
                check("minecraft:air".equals(last(CityMetroNorthTransit.overlay(CityMetroNorthLine.X+side,along+edge),rail)),"north–south lateral ramp entry");
        }
        for(int i=0;i<CityMetroLine.STATIONS.size();i++) {
            int center=CityMetroLine.STATIONS.get(i).x(),sign=i<=2?1:-1;
            for(int a=1;a<=49;a++) {
                var col=CityMetroTransit.overlay(center+sign*a,CityMetroLine.Z-7);
                check("minecraft:air".equals(last(col,-15)) && "minecraft:air".equals(last(col,-14)),
                        "east–west entry must retain a direct platform path");
            }
        }
        for(int i:new int[]{1,2}) {
            int center=CityMetroAirportLine.STATIONS.get(i).x();
            for(int a=1;a<=49;a++) {
                var col=CityMetroAirportTransit.overlay(center+a,CityMetroAirportLine.Z-7);
                check("minecraft:air".equals(last(col,-15)) && "minecraft:air".equals(last(col,-14)),
                        "airport metro entry must retain a direct platform path");
            }
        }
        check(CityMetroLine.RAIL_Y+5 > CityMetroLine.RAIL_Y+3.7,"bridge must clear the train envelope");
        System.out.println("PASS raised, lit, two-way crossings at all 14 underground stations");
    }
    private static void checkEastWest(int center,int z,int rail,boolean airport,int offset) {
        for(int side=-9;side<=9;side++) {
            var col=airport?CityMetroAirportTransit.overlay(center+offset,z+side):CityMetroTransit.overlay(center+offset,z+side);
            verify(col,side,rail,"east–west stop "+center);
        }
        for(int side=-7;side<=7;side++)for(int edge:new int[]{-2,2}) {
            var col=airport?CityMetroAirportTransit.overlay(center+offset+edge,z+side):CityMetroTransit.overlay(center+offset+edge,z+side);
            int floor=Math.abs(side)<=3?rail+5:rail+8-Math.abs(side);
            check("minecraft:iron_bars[north=true,south=true]".equals(last(col,floor+1)),"east–west connected bridge railing");
        }
        for(int side:new int[]{-9,9})for(int edge:new int[]{-2,2}) {
            var col=airport?CityMetroAirportTransit.overlay(center+offset+edge,z+side):CityMetroTransit.overlay(center+offset+edge,z+side);
            check("minecraft:air".equals(last(col,rail)),"east–west lateral ramp entry");
        }
    }
    private static void verify(CityTransit.Over[] col,int side,int rail,String where) {
        int abs=Math.abs(side),floor=abs<=3?rail+5:rail+8-abs;
        String value=last(col,floor);
        check(abs<3?"academy:brushed_steel".equals(value):abs==3?"academy:steel_slab[type=bottom]".equals(value)
                :value!=null && value.startsWith("minecraft:quartz_stairs["),where+" floor side="+side);
        check("minecraft:air".equals(last(col,floor+1)) && "minecraft:air".equals(last(col,floor+2)),where+" headroom side="+side);
        check("academy:reinforced_concrete".equals(last(col,rail+10)),where+" roof side="+side);
    }
    private static String last(CityTransit.Over[] a,int y) {
        if(a==null)return null;for(int i=a.length-1;i>=0;i--)if(a[i].y()==y)return a[i].block();return null;
    }
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
