package cn.academy.port.city;

import cn.academy.port.train.CityMetroAirportLine;
import cn.academy.port.train.CityMetroNorthLine;

/** Airport subway: level separation, runway protection and pedestrian connections. */
public final class CityMetroAirportTransitRegressionTest {
    public static void main(String[] args) {
        long seed=20261103L;
        check(CityMetroAirportLine.STATIONS.size()==4,"four airport metro stations");
        for(int x=-150;x<=1670;x++) {
            var col=CityMetroAirportTransit.overlay(x,CityMetroAirportLine.Z);
            final int sampleX=x;
            boolean bridge=false;
            for(int i=0;i<CityMetroAirportLine.STATIONS.size();i++) {
                int offset=i==3?35:-35;
                if(Math.abs(sampleX-(CityMetroAirportLine.STATIONS.get(i).x()+offset))<=2)bridge=true;
            }
            check("academy:brushed_steel".equals(last(col,-16)) && "minecraft:air".equals(last(col,-15))
                    && (bridge ? ("minecraft:air".equals(last(col,-8)) || starts(col,-8,"minecraft:iron_bars[")) && "academy:reinforced_concrete".equals(last(col,-5))
                               : "academy:reinforced_concrete".equals(last(col,-8))),"airport tunnel x="+x);
        }
        for(int station=0;station<4;station++) {
            int c=CityMetroAirportLine.STATIONS.get(station).x();
            for(boolean direction:new boolean[]{false,true})
                check(CityMetroAirportLine.at(CityMetroAirportLine.dwellStart(station,direction)+2).boarding(),"timetable");
            for(int role=0;role<3;role++)for(int side:new int[]{-3,3})for(int d=-2;d<=2;d++) {
                var p=CityMetroAirportTransit.bay(station,role,side,-15).offset(d,0,0);
                var col=CityMetroAirportTransit.overlay(p.getX(),p.getZ());
                check(starts(col,-15,"academy:platform_gate[") && starts(col,-14,"academy:platform_gate["),"station gates "+p);
            }
            if(!CityMetroAirportTransit.hasSurfaceEntrance(station))continue;
            for(int a=50;a<=65;a++)for(int z=-1640;z<=-1638;z++) {
                int x=c+a,step=a-66;var col=CityMetroAirportTransit.overlay(x,z);
                check(starts(col,step,"minecraft:quartz_stairs[") && "minecraft:air".equals(last(col,step+1))
                        && "minecraft:air".equals(last(col,step+2)),"surface stairs");
                var kind=CityPlan.sample(seed,x,z).kind();
                check(kind!=CityPlan.Kind.ROAD && kind!=CityPlan.Kind.MARKING,"entrance crosses road "+x+","+z);
                check(CityTemplateSites.at(seed,x,z)==null && CityDistrictLandmarks.at(seed,x,z)==null,"entrance conflicts with existing building");
                check(CityMetroAirportTransit.entranceReserved(x,z,1,1),"entrance must reserve surface");
            }
        }
        // The crossing is 16 blocks above the existing north–south track.
        check(CityMetroAirportLine.RAIL_Y-CityMetroNorthLine.RAIL_Y==16,"grade separation");
        for(int role=0;role<3;role++)for(int side:new int[]{-9,9}) {
            var p=CityMetroNorthTransit.bay(0,role,side,-31);
            check(last(CityMetroAirportTransit.overlay(p.getX(),p.getZ()),p.getY())==null,"existing north metro screen preserved");
        }
        for(int role=0;role<3;role++)for(int side:new int[]{-3,3})for(int d=-2;d<=2;d++) {
            var p=CityMetroNorthTransit.bay(0,role,side,-31).offset(0,0,d);
            check(last(CityMetroAirportTransit.overlay(p.getX(),p.getZ()),p.getY())==null,"existing north metro gates preserved");
        }
        for(int z=-1598;z<=-1582;z++) {
            int step=-1614-z;var col=CityMetroAirportTransit.overlay(-93,z);
            check(starts(col,step,"minecraft:quartz_stairs[") && "minecraft:air".equals(last(col,step+1))
                    && "minecraft:air".equals(last(col,step+2)),"north interchange stairs");
        }
        check(last(CityMetroAirportTransit.overlay(-95,-1582),-31)==null
                && "minecraft:air".equals(last(CityMetroNorthTransit.overlay(-95,-1582),-31)),"north platform exit");
        for(int z=-1831;z<=-1640;z++) {
            var col=CityMetroAirportTransit.overlay(1608,z);
            // The last four corridor blocks share the terminal stair and can be above the corridor floor.
            if(z>=-1828)check("academy:plaza_tile".equals(last(col,-16)) && "minecraft:air".equals(last(col,-15)),"airport passage z="+z);
        }
        for(int x=1608;x<=1623;x++)for(int z=-1831;z<=-1829;z++) {
            int step=x-1624;var col=CityMetroAirportTransit.overlay(x,z);
            check(starts(col,step,"minecraft:quartz_stairs[") && "minecraft:air".equals(last(col,step+1))
                    && "minecraft:air".equals(last(col,step+2)),"terminal stair");
        }
        for(int x=1671;x<=1675;x++)for(int z=-1640;z<=-1624;z++) {
            var col=CityMetroAirportTransit.overlay(x,z);String floor=last(col,-16);
            check(("academy:plaza_tile".equals(floor) || "minecraft:lime_concrete".equals(floor))
                    && "minecraft:air".equals(last(col,-15)) && "minecraft:air".equals(last(col,-14)),
                    "terminal end concourse must link the platforms without crossing the track");
        }
        // No new overlay may replace the runway surface or air above it.
        for(int x=1252;x<1948;x++)for(int z=-1688;z< -1628;z++) {
            var col=CityMetroAirportTransit.overlay(x,z);
            if(col!=null)for(var over:col)check(over.y() < -1,"runway surface must remain untouched");
        }
        for(double t=0;t<CityMetroAirportLine.CYCLE;t+=.37) {
            var state=CityMetroAirportLine.at(t);
            check(state.next()>=0 && state.next()<4 && state.speed()>=0 && state.speed()<=CityMetroAirportLine.VMAX+1e-8,"timetable cycle");
        }
        System.out.println("PASS airport underground line: four stations, north interchange, terminal access and protected runway");
    }
    private static String last(CityTransit.Over[] a,int y) {
        if(a==null)return null;for(int i=a.length-1;i>=0;i--)if(a[i].y()==y)return a[i].block();return null;
    }
    private static boolean starts(CityTransit.Over[] a,int y,String v) {String actual=last(a,y);return actual!=null && actual.startsWith(v);}
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
