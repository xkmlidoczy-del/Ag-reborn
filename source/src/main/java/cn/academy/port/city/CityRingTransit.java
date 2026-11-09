package cn.academy.port.city;

import cn.academy.port.train.CityRingLine;
import cn.academy.port.train.TrainLine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Deterministic guideway, stations, access stairs and one D15 pedestrian interchange. */
public final class CityRingTransit {
    private CityRingTransit() {}
    private static boolean intersects(int x, int z, int w, int d, int a, int b, int aw, int ad) {
        return x < a + aw && x + w > a && z < b + ad && z + d > b;
    }
    public static boolean overlapsLot(int x, int z, int w, int d) {
        if (x >= 917 || z >= 917 || x + w <= -916 || z + d <= -916) return false;
        if (x >= -800 && z >= -800 && x + w <= 800 && z + d <= 800) return false;
        if (intersects(x,z,w,d,-916,-916,1833,33) || intersects(x,z,w,d,-916,884,1833,33)
                || intersects(x,z,w,d,-916,-916,33,1833) || intersects(x,z,w,d,884,-916,33,1833)
                || intersects(x,z,w,d,16,897,85,5)) return true;
        for (int a : new int[]{-916,800}) for (int b : new int[]{-916,800})
            if (intersects(x,z,w,d,a,b,117,117)) return true;
        for (var station : CityRingLine.STATIONS) {
            if (station.horizontal()) {
                if (intersects(x,z,w,d,station.x()-50,station.z()-11,166,23)) return true;
            } else if (intersects(x,z,w,d,station.x()-11,station.z()-50,23,166)) return true;
        }
        return false;
    }
    private static double trackDistance(int x, int z) {
        if (Math.abs(x) <= 800) return Math.abs(Math.abs(z) - 900);
        if (Math.abs(z) <= 800) return Math.abs(Math.abs(x) - 900);
        return Math.abs(Math.hypot(Math.abs(x) - 800, Math.abs(z) - 800) - 100);
    }
    public static int stationNear(int x, int z, int radius) {
        for (int i=0; i<CityRingLine.STATIONS.size(); i++) {
            var s=CityRingLine.STATIONS.get(i);
            if ((s.horizontal() ? Math.abs(x-s.x()) : Math.abs(z-s.z())) <= radius
                    && (s.horizontal() ? Math.abs(z-s.z()) : Math.abs(x-s.x())) <= 11) return i;
        }
        return -1;
    }
    public static BlockPos bay(int station, int role, int side, int y) {
        var s=CityRingLine.STATIONS.get(station); var p=CityRingLine.point(s.distance());
        int shift=(int)Math.round((role-1)*TrainLine.CAR_PITCH);
        return s.horizontal() ? new BlockPos(s.x()+(int)Math.round(p.dx()*shift),y,s.z()+side)
                : new BlockPos(s.x()+side,y,s.z()+(int)Math.round(p.dz()*shift));
    }
    public static CityTransit.Over[] overlay(long seed, int x, int z) {
        if (!overlapsLot(x,z,1,1)) return null;
        List<CityTransit.Over> out=new ArrayList<>();
        double dist=trackDistance(x,z);
        int station=stationNear(x,z,50);
        var stop=station<0 ? null : CityRingLine.STATIONS.get(station);
        int along=stop==null ? 0 : stop.horizontal() ? x-stop.x() : z-stop.z();
        int cross=stop==null ? 0 : stop.horizontal() ? z-stop.z() : x-stop.x();
        boolean platform=stop!=null && Math.abs(cross)<=10;
        if (dist<=4.25 || platform) {
            out.add(new CityTransit.Over(63,"academy:reinforced_concrete"));
            String floor=platform && Math.abs(cross)>=3 ? "academy:plaza_tile" : "academy:reinforced_concrete";
            if (dist<=1.25) floor="academy:brushed_steel";
            if (platform && Math.abs(cross)==3) floor="minecraft:yellow_concrete";
            out.add(new CityTransit.Over(64,floor));
            if (!platform && dist>3.25) out.add(new CityTransit.Over(65,"minecraft:iron_bars"));
        }
        if (platform) {
            boolean stairOpening=along==50 && Math.abs(cross)>=6 && Math.abs(cross)<=8;
            if ((Math.abs(cross)==10 || Math.abs(along)==50) && !stairOpening)
                out.add(new CityTransit.Over(65,"minecraft:iron_bars"));
            out.add(new CityTransit.Over(74,"academy:white_panel_slab[type=bottom]"));
            if (Math.abs(cross)==10 && Math.floorMod(along,20)==0) column(out,65,73,"academy:pillar_white");
            if (Math.abs(cross)<=9 && Math.floorMod(cross,3)==0 && Math.floorMod(along,5)==0)
                out.add(new CityTransit.Over(73,"academy:ceiling_light"));
            boolean nearBay=false;
            for (int role=0;role<3;role++) {
                var center=bay(station,role,0,65);
                int delta=stop.horizontal()?x-center.getX():z-center.getZ();
                if (Math.abs(delta)<=2) nearBay=true;
                if (delta==0 && Math.abs(cross)==9) out.add(new CityTransit.Over(65,"academy:led_screen"));
                if (Math.abs(delta)<=2 && Math.abs(cross)==3) {
                    String facing=stop.horizontal() ? (cross<0?"north":"south") : (cross<0?"west":"east");
                    String p="[facing="+facing+",hinge="+(Math.floorMod(along,2)==0?"left":"right")+",open=false,powered=false,half=";
                    out.add(new CityTransit.Over(65,"academy:platform_gate"+p+"lower]"));
                    out.add(new CityTransit.Over(66,"academy:platform_gate"+p+"upper]"));
                }
            }
            if (!nearBay && Math.abs(cross)==2) out.add(new CityTransit.Over(65,"minecraft:iron_bars"));
        }
        for (var s : CityRingLine.STATIONS) {
            int a=s.horizontal()?x-s.x():z-s.z();
            int c=s.horizontal()?z-s.z():x-s.x();
            if (a>=51 && a<=115 && Math.abs(c)>=6 && Math.abs(c)<=8) {
                int step=115-a;
                if (step>0) out.add(new CityTransit.Over(step-1,"academy:brushed_steel"));
                out.add(new CityTransit.Over(step,"minecraft:quartz_stairs[facing="+(s.horizontal()?"west":"north")+",half=bottom]"));
                if (Math.abs(c)==8 && step>=6 && step%8==0) column(out,0,step-2,"academy:pillar_white");
            }
            if (a>=51 && a<=112 && Math.abs(c)==9)
                out.add(new CityTransit.Over(116-a,"minecraft:iron_bars"));
            if (a>=116 && a<=120 && Math.abs(c)<=11) {
                out.add(new CityTransit.Over(-1,"academy:plaza_tile")); column(out,0,3,"minecraft:air");
            }
        }
        if (x>=16 && x<=100 && z>=897 && z<=901) {
            var kind=CityPlan.sample(seed,x,z).kind();
            String floor=kind==CityPlan.Kind.ROAD || kind==CityPlan.Kind.MARKING
                    ? (Math.floorMod(z,2)==0?"minecraft:white_concrete":"academy:asphalt") : "academy:plaza_tile";
            out.add(new CityTransit.Over(-1,floor)); column(out,0,3,"minecraft:air");
        }
        boolean crossingEW=Math.abs(z-400)<=35;
        boolean crossingNS=Math.abs(x-150)<=35 && Math.abs(Math.abs(z)-900)<=18;
        if (dist<.75 && !platform && !crossingEW && !crossingNS
                && Math.floorMod(x+z,24)==0 && CityDistrictLandmarks.at(seed,x,z)==null) {
            var kind=CityPlan.sample(seed,x,z).kind();
            if (kind!=CityPlan.Kind.ROAD && kind!=CityPlan.Kind.MARKING)
                column(out,0,62,"academy:pillar_dark");
        }
        return out.isEmpty()?null:out.toArray(CityTransit.Over[]::new);
    }
    private static void column(List<CityTransit.Over> out,int from,int to,String block) {
        for (int y=from;y<=to;y++) out.add(new CityTransit.Over(y,block));
    }
}
