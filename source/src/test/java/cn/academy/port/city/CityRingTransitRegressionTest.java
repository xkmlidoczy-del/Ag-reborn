package cn.academy.port.city;

import cn.academy.port.train.CityRingLine;
import cn.academy.port.train.TrainLine;
import java.util.Arrays;

/** Integration of the analytic ring, platforms, gates and land reservations. */
public final class CityRingTransitRegressionTest {
    public static void main(String[] args) {
        long seed=20261103L; int samples=0;
        for(double distance=0;distance<CityRingLine.LENGTH;distance+=1.7) {
            var p=CityRingLine.point(distance); int x=(int)Math.round(p.x()),z=(int)Math.round(p.z());
            var overlays=CityRingTransit.overlay(seed,x,z);
            check(overlays!=null && has(overlays,64,"academy:brushed_steel"),"missing track at "+x+","+z);
            check(Math.abs(Math.hypot(p.dx(),p.dz())-1)<1e-9,"unit tangent");
            var plan=CityPlan.sample(seed,x,z);
            check(plan.kind()!=CityPlan.Kind.BUILDING || plan.height()<CityRingLine.RAIL_Y-2,
                    "unreserved tall building intersects ring at "+x+","+z);
            var landmark=CityDistrictLandmarks.at(seed,x,z);
            if(landmark!=null) {
                String block=landmark.block(CityRingLine.RAIL_Y);
                check(block==null || block.equals("minecraft:air"),"landmark intersects ring at "+x+","+z);
            }
            samples++;
        }
        for(int i=0;i<CityRingLine.STATIONS.size();i++) {
            var stop=CityRingLine.STATIONS.get(i); var p=CityRingLine.point(stop.distance());
            check(Math.hypot(p.x()-stop.x(),p.z()-stop.z())<1e-8,"station geometry "+i);
            var arrival=CityRingLine.at(CityRingLine.dwellStart(i)+2);
            check(arrival.station()==i && arrival.boarding() && arrival.direction()==1,"clockwise boarding phase "+i);
            var reverse=CityRingLine.at(CityRingLine.dwellStart(i,false)+2);
            check(reverse.station()==i && reverse.boarding() && reverse.direction()==-1
                    && reverse.next()==Math.floorMod(i-1,CityRingLine.STATIONS.size()),"counterclockwise boarding phase "+i);
            check(CityRingLine.at(CityRingLine.dwellStart(i)+CityRingLine.DWELL-1).phase()==TrainLine.Phase.CLOSING,"closing phase "+i);
            for(int role=0;role<3;role++) for(int side:new int[]{-3,3}) for(int d=-2;d<=2;d++) {
                var bay=CityRingTransit.bay(i,role,side,65);
                int x=bay.getX()+(stop.horizontal()?d:0),z=bay.getZ()+(stop.horizontal()?0:d);
                var over=CityRingTransit.overlay(seed,x,z);
                check(hasPrefix(over,65,"academy:platform_gate[") && hasPrefix(over,66,"academy:platform_gate["),
                        "gate missing at "+x+","+z);
            }
            for(int role=0;role<3;role++) for(int side:new int[]{-9,9}) {
                var bay=CityRingTransit.bay(i,role,side,65);
                check(has(CityRingTransit.overlay(seed,bay.getX(),bay.getZ()),65,"academy:led_screen"),"boarding screen "+i);
            }
            for(int a=51;a<=115;a++) for(int cross=6;cross<=8;cross++) {
                int x=stop.horizontal()?stop.x()+a:stop.x()+cross;
                int z=stop.horizontal()?stop.z()+cross:stop.z()+a;
                check(hasPrefix(CityRingTransit.overlay(seed,x,z),115-a,"minecraft:quartz_stairs["),"access staircase "+i+" step "+a);
            }
        }
        for(int x=16;x<=100;x++) check(has(CityRingTransit.overlay(seed,x,899),-1,"academy:plaza_tile")
                || CityPlan.sample(seed,x,899).kind()==CityPlan.Kind.ROAD || CityPlan.sample(seed,x,899).kind()==CityPlan.Kind.MARKING,
                "D15 connection floor "+x);
        for(double t=0;t<CityRingLine.CYCLE;t+=.31) {
            var state=CityRingLine.at(t);
            check(state.next()>=0 && state.next()<CityRingLine.STATIONS.size() && state.speed()>=0
                    && state.speed()<=CityRingLine.VMAX+1e-8,"clock validity");
            for(int a=0;a<CityRingLine.SERVICES;a++) {
                var first=CityRingLine.at(t,a);
                check(first.direction()==state.direction() && first.speed()>=0 && first.speed()<=CityRingLine.VMAX+1e-8,
                        "fleet must share a safe direction and speed");
                for(int b=a+1;b<CityRingLine.SERVICES;b++) {
                    var second=CityRingLine.at(t,b);
                    double separation=Math.abs(first.distance()-second.distance());
                    separation=Math.min(separation,CityRingLine.LENGTH-separation);
                    check(separation>1000,"trains must stay at least 1000 blocks apart at t="+t);
                    check(first.station()<0 || first.station()!=second.station(),"two trains at one platform");
                }
            }
        }
        for(int service=0;service<CityRingLine.SERVICES;service++) for(int station=0;station<CityRingLine.STATIONS.size();station++)
            for(boolean clockwise:new boolean[]{true,false}) {
                int primary=Math.floorMod(station-service*CityRingLine.SERVICE_STATION_GAP,CityRingLine.STATIONS.size());
                double dwell=CityRingLine.dwellStart(primary,clockwise);
                var beforeStop=CityRingLine.at(dwell-.00001,service);
                var stopped=CityRingLine.at(dwell+.00001,service);
                check(stopped.station()==station && stopped.direction()==(clockwise?1:-1),"service must stop at its assigned station");
                check(Math.hypot(beforeStop.point().x()-stopped.point().x(),beforeStop.point().z()-stopped.point().z())<.01,
                        "fleet position continuous across arrival and reversal");
                check(CityRingLine.arrivalIn(station,dwell+2)==0,"arrival information must include every service");
            }
        var start=CityRingLine.point(0);var end=CityRingLine.point(CityRingLine.LENGTH);
        check(Math.hypot(start.x()-end.x(),start.z()-end.z())<1e-8,"ring closes");
        var before=CityRingLine.at(CityRingLine.dwellStart(0,false)-.001).point();
        var after=CityRingLine.at(CityRingLine.dwellStart(0,false)+.001).point();
        check(Math.hypot(before.x()-after.x(),before.z()-after.z())<.01,"reversal changes position");
        System.out.println("PASS ring geometry samples="+samples+", 12 stations, both directions, gates, stairs and D15 walking connection");
    }
    private static boolean has(CityTransit.Over[] arr,int y,String block) {
        return arr!=null && Arrays.stream(arr).anyMatch(o->o.y()==y && o.block().equals(block));
    }
    private static boolean hasPrefix(CityTransit.Over[] arr,int y,String prefix) {
        return arr!=null && Arrays.stream(arr).anyMatch(o->o.y()==y && o.block().startsWith(prefix));
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
