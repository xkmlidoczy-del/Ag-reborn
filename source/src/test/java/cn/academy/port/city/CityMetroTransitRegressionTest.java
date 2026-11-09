package cn.academy.port.city;

import cn.academy.port.train.CityMetroLine;
import cn.academy.port.train.TrainLine;
import java.util.Arrays;

/** Underground corridor, stations and walkable, guarded ground entrances. */
public final class CityMetroTransitRegressionTest {
    public static void main(String[] args) {
        long seed=20261103L;int samples=0;
        for(int x=-1900;x<=1900;x++) {
            var out=CityMetroTransit.overlay(x,600);
            check(has(out,-16,"academy:brushed_steel") && has(out,-15,"minecraft:air")
                    && has(out,-9,"minecraft:air") && has(out,-8,"academy:reinforced_concrete"),"tunnel clearance "+x);
            samples++;
        }
        for(int station=0;station<CityMetroLine.STATIONS.size();station++) {
            var stop=CityMetroLine.STATIONS.get(station);
            for(boolean eastbound:new boolean[]{false,true}) {
                var state=CityMetroLine.at(CityMetroLine.dwellStart(station,eastbound)+2);
                check(state.station()==station && state.boarding(),"metro boarding at "+station);
            }
            for(int role=0;role<3;role++) for(int side:new int[]{-3,3}) for(int d=-2;d<=2;d++) {
                var pos=CityMetroTransit.bay(station,role,side,-15).offset(d,0,0);
                var out=CityMetroTransit.overlay(pos.getX(),pos.getZ());
                check(hasPrefix(out,-15,"academy:platform_gate[") && hasPrefix(out,-14,"academy:platform_gate["),"gate "+pos);
            }
            for(int role=0;role<3;role++) for(int side:new int[]{-9,9}) {
                var pos=CityMetroTransit.bay(station,role,side,-15);
                check(has(CityMetroTransit.overlay(pos.getX(),pos.getZ()),-15,"academy:led_screen"),"boarding panel "+pos);
            }
            int sign=station<=2?1:-1;
            for(int a=50;a<=65;a++) for(int z=592;z<=594;z++) {
                int x=stop.x()+sign*a,step=a-66;var out=CityMetroTransit.overlay(x,z);
                check(hasPrefix(out,step,"minecraft:quartz_stairs["),"stairs "+station+" "+a);
                check(has(out,step+1,"minecraft:air") && has(out,step+2,"minecraft:air"),"stair headroom");
                var kind=CityPlan.sample(seed,x,z).kind();
                check(kind!=CityPlan.Kind.ROAD && kind!=CityPlan.Kind.MARKING,"stairs cut through road "+x+","+z);
                check(CityTemplateSites.at(seed,x,z)==null && CityDistrictLandmarks.at(seed,x,z)==null,"entrance hits building "+x+","+z);
            }
            for(int a=50;a<65;a++) for(int z:new int[]{591,595}) {
                var out=CityMetroTransit.overlay(stop.x()+sign*a,z);
                check(hasPrefix(out,0,"minecraft:iron_bars[") && hasPrefix(out,1,"minecraft:iron_bars["),"unguarded shaft side");
            }
            for(int z=591;z<=595;z++)check(hasPrefix(CityMetroTransit.overlay(stop.x()+sign*49,z),0,"minecraft:iron_bars["),"unguarded shaft end");
            var entryFrame=CityMetroTransit.overlay(stop.x()+sign*67,591);
            check(has(entryFrame,0,"academy:pillar_white") && has(entryFrame,4,"academy:brushed_steel"),"entrance frame support");
            var entrySign=CityMetroTransit.overlay(stop.x()+sign*67,593);
            check(has(entrySign,4,"academy:led_screen") && has(entrySign,3,"academy:ceiling_light"),"lit entrance sign");
            for(int a=66;a<=67;a++)for(int z=591;z<=595;z++) {
                var out=CityMetroTransit.overlay(stop.x()+sign*a,z);
                check(has(out,-1,"academy:plaza_tile") && has(out,0,"minecraft:air"),"surface landing");
                var kind=CityPlan.sample(seed,stop.x()+sign*a,z).kind();
                check(kind!=CityPlan.Kind.ROAD && kind!=CityPlan.Kind.MARKING,"landing cuts road");
            }
        }
        for(double t=0;t<CityMetroLine.CYCLE;t+=.37) {
            var state=CityMetroLine.at(t);
            check(state.next()>=0 && state.next()<CityMetroLine.STATIONS.size() && state.speed()>=0
                    && state.speed()<=CityMetroLine.VMAX+1e-8,"metro timetable");
        }
        System.out.println("PASS metro tunnel samples="+samples+", five stations, bidirectional timetable, gates and protected entrances");
    }
    private static boolean has(CityTransit.Over[] a,int y,String v){return a!=null && Arrays.stream(a).anyMatch(o->o.y()==y&&o.block().equals(v));}
    private static boolean hasPrefix(CityTransit.Over[] a,int y,String v){return a!=null && Arrays.stream(a).anyMatch(o->o.y()==y&&o.block().startsWith(v));}
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
