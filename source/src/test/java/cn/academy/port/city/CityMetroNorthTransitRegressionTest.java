package cn.academy.port.city;

import cn.academy.port.train.CityMetroLine;
import cn.academy.port.train.CityMetroNorthLine;
import cn.academy.port.train.TrainLine;

/** Separation, passenger access and timetable for the second metro line. */
public final class CityMetroNorthTransitRegressionTest {
    public static void main(String[] args) {
        long seed=20261103L;
        check(CityMetroNorthLine.STATIONS.size()==5,"five north–south stations");
        for(int z=-1732;z<=1472;z++) {
            var column=CityMetroNorthTransit.overlay(CityMetroNorthLine.X,z);
            final int sampleZ=z;
            boolean bridge=CityMetroNorthLine.STATIONS.stream().anyMatch(stop -> Math.abs(sampleZ-(stop.z()-35))<=2);
            check("academy:brushed_steel".equals(last(column,-32)) && "minecraft:air".equals(last(column,-31))
                    && (bridge ? ("minecraft:air".equals(last(column,-24)) || starts(column,-24,"minecraft:iron_bars[")) && "academy:reinforced_concrete".equals(last(column,-21))
                               : "academy:reinforced_concrete".equals(last(column,-24))),"tunnel and roof at z="+z);
        }
        for(int station=0;station<5;station++) {
            int c=CityMetroNorthLine.STATIONS.get(station).z();
            for(boolean direction:new boolean[]{true,false})
                check(CityMetroNorthLine.at(CityMetroNorthLine.dwellStart(station,direction)+2).boarding(),"station timetable "+station);
            for(int role=0;role<3;role++)for(int side:new int[]{-3,3})for(int d=-2;d<=2;d++) {
                var pos=CityMetroNorthTransit.bay(station,role,side,-31).offset(0,0,d);
                var column=CityMetroNorthTransit.overlay(pos.getX(),pos.getZ());
                check(starts(column,-31,"academy:platform_gate[") && starts(column,-30,"academy:platform_gate["),"station gates "+pos);
            }
            if(station==3)continue;
            for(int a:new int[]{53,61,69,77}) {
                int step=a-82;var lamp=CityMetroNorthTransit.overlay(-109,c+a);
                check("academy:reinforced_concrete".equals(last(lamp,step+2))
                        && starts(lamp,step+3,"minecraft:lantern["),"supported entrance lighting");
            }
            for(int a=50;a<=81;a++)for(int x=-108;x<=-106;x++) {
                int z=c+a,step=a-82;var column=CityMetroNorthTransit.overlay(x,z);
                check(starts(column,step,"minecraft:quartz_stairs[") && "minecraft:air".equals(last(column,step+1))
                        && "minecraft:air".equals(last(column,step+2)),"entrance stairs and clearance");
                var kind=CityPlan.sample(seed,x,z).kind();
                check(kind!=CityPlan.Kind.ROAD && kind!=CityPlan.Kind.MARKING,"shaft must not cut a road at "+x+","+z);
                check(CityTemplateSites.at(seed,x,z)==null && CityDistrictLandmarks.at(seed,x,z)==null,"shaft must avoid existing landmarks and building templates");
                check(CityMetroNorthTransit.entranceReserved(x,z,1,1),"shaft must reserve its ground lot");
            }
        }
        for(int x=-108;x<=-82;x++)for(int z=54;z<=56;z++) {
            var path=CityMetroNorthTransit.overlay(x,z);
            check("academy:plaza_tile".equals(last(path,-1)) && "minecraft:air".equals(last(path,0))
                    && "minecraft:air".equals(last(path,1)),"D7 entrance to promenade path");
            check(CityMetroNorthTransit.entranceReserved(x,z,1,1),"D7 path must reserve its lot");
        }
        // No east–west gate or boarding screen may be replaced by the transfer passage.
        for(int role=0;role<3;role++)for(int side:new int[]{-9,9}) {
            var p=CityMetroTransit.bay(2,role,side,-15);
            check(last(CityMetroNorthTransit.overlay(p.getX(),p.getZ()),p.getY())==null,"east–west boarding screen preserved");
        }
        for(int role=0;role<3;role++)for(int side:new int[]{-3,3})for(int d=-2;d<=2;d++) {
            var p=CityMetroTransit.bay(2,role,side,-15).offset(d,0,0);
            check(last(CityMetroNorthTransit.overlay(p.getX(),p.getZ()),p.getY())==null,"east–west station gate preserved");
        }
        for(int z=608;z<=613;z++) {
            var col=CityMetroNorthTransit.overlay(-112,z);
            check("academy:plaza_tile".equals(last(col,-16)) && "minecraft:air".equals(last(col,-15)),"upper transfer passage");
        }
        for(int z=634;z<=650;z++) {
            int step=618-z;var col=CityMetroNorthTransit.overlay(-107,z);
            check(starts(col,step,"minecraft:quartz_stairs[") && "minecraft:air".equals(last(col,step+1))
                    && "minecraft:air".equals(last(col,step+2)),"transfer stairs z="+z);
        }
        check("minecraft:air".equals(last(CityMetroNorthTransit.overlay(-105,650),-31)),"lower platform exit must be open");
        check("minecraft:purple_concrete".equals(last(CityMetroNorthTransit.overlay(-112,607),-16))
                && "academy:led_screen".equals(last(CityMetroNorthTransit.overlay(-112,607),-11)),"upper transfer wayfinding");
        check("minecraft:purple_concrete".equals(last(CityMetroNorthTransit.overlay(-105,620),-32)),"lower transfer wayfinding");
        for(int z:new int[]{638,644,650}) {
            int step=618-z;var lamp=CityMetroNorthTransit.overlay(-109,z);
            check("academy:reinforced_concrete".equals(last(lamp,step+2))
                    && starts(lamp,step+3,"minecraft:lantern["),"supported transfer lighting");
        }
        check(CityMetroLine.RAIL_Y-CityMetroNorthLine.RAIL_Y==16,"railway levels separated");
        for(double t=0;t<CityMetroNorthLine.CYCLE;t+=.37) {
            var state=CityMetroNorthLine.at(t);
            check(state.next()>=0 && state.next()<5 && state.speed()>=0 && state.speed()<=CityMetroNorthLine.VMAX+1e-8,"line cycle");
        }
        System.out.println("PASS north–south underground line: five stops, four surface shafts, separated interchange, existing gates and timetable");
    }
    private static String last(CityTransit.Over[] a,int y) {
        if(a==null)return null;for(int i=a.length-1;i>=0;i--)if(a[i].y()==y)return a[i].block();return null;
    }
    private static boolean starts(CityTransit.Over[] a,int y,String v) {String actual=last(a,y);return actual!=null && actual.startsWith(v);}
    private static void check(boolean value,String message) {if(!value)throw new AssertionError(message);}
}
