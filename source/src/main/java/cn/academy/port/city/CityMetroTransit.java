package cn.academy.port.city;

import cn.academy.port.train.CityMetroLine;
import cn.academy.port.train.TrainLine;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;

/** Underground east–west tunnel, five platforms and protected surface entrances. */
public final class CityMetroTransit {
    private CityMetroTransit() {}
    private static boolean intersects(int x,int z,int w,int d,int a,int b,int aw,int ad) {
        return x<a+aw && x+w>a && z<b+ad && z+d>b;
    }
    private static int approachSign(int station) { return station<=2?1:-1; }
    public static int surfaceEntranceX(int station) {
        return CityMetroLine.STATIONS.get(station).x() + approachSign(station) * 67;
    }
    public static boolean entranceReserved(int x,int z,int w,int d) {
        if(z>=598 || z+d<=590 || x>=1900 || x+w<=-1900) return false;
        for(int i=0;i<CityMetroLine.STATIONS.size();i++) {
            int c=CityMetroLine.STATIONS.get(i).x(),sign=approachSign(i);
            int a=sign>0?c+49:c-69;
            if(intersects(x,z,w,d,a,590,21,8)) return true;
        }
        return false;
    }
    public static BlockPos bay(int station,int role,int side,int y) {
        return new BlockPos(CityMetroLine.STATIONS.get(station).x()+(int)Math.round((role-1)*TrainLine.CAR_PITCH),y,CityMetroLine.Z+side);
    }
    public static int stationNear(int x,int z,int radius) {
        return Math.abs(z-CityMetroLine.Z)<=11?CityMetroLine.stationNear(x,radius):-1;
    }
    public static CityTransit.Over[] overlay(int x,int z) {
        int side=z-CityMetroLine.Z;
        if(x< -1900 || x>1900 || Math.abs(side)>11) return null;
        int station=CityMetroLine.stationNear(x,50);
        int along=station<0?0:x-CityMetroLine.STATIONS.get(station).x();
        boolean platform=station>=0 && Math.abs(side)<=10;
        boolean tunnel=Math.abs(side)<=5;
        List<CityTransit.Over> out=new ArrayList<>();
        if(platform || tunnel) {
            String floor=platform && Math.abs(side)>=3?"academy:plaza_tile":"academy:reinforced_concrete";
            if(Math.abs(side)<=1)floor="academy:brushed_steel";
            if(platform && Math.abs(side)==3)floor="minecraft:yellow_concrete";
            out.add(new CityTransit.Over(-16,floor));
            column(out,-15,-9,"minecraft:air");
            out.add(new CityTransit.Over(-8,"academy:reinforced_concrete"));
            if(!platform && Math.abs(side)==5) column(out,-15,-9,"academy:reinforced_concrete");
            if(platform && Math.abs(side)==10) column(out,-15,-9,"academy:white_panel_wall");
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
        for(int i=0;i<CityMetroLine.STATIONS.size();i++) {
            int c=CityMetroLine.STATIONS.get(i).x(),sign=approachSign(i),a=(x-c)*sign;
            if(a>=50 && a<=65 && side>=-8 && side<=-6) {
                int step=a-66;
                out.add(new CityTransit.Over(step-1,"academy:reinforced_concrete"));
                out.add(new CityTransit.Over(step,"minecraft:quartz_stairs[facing="+(sign>0?"east":"west")+",half=bottom]"));
                column(out,step+1,3,"minecraft:air");
            }
            // Guard the open stairwell at street level, leaving the outer end open.
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
            // A lit entrance frame makes the stair visible from the surrounding street.
            if(a==67 && (side==-9 || side==-5)) {
                column(out,0,3,"academy:pillar_white");
                out.add(new CityTransit.Over(4,"academy:brushed_steel"));
            }
            if(a==67 && side>=-8 && side<=-6) {
                out.add(new CityTransit.Over(4,"academy:led_screen"));
                if(side==-7)out.add(new CityTransit.Over(3,"academy:ceiling_light"));
            }
        }
        if(station>=0)CityMetroCrossing.append(out,x,z,CityMetroLine.STATIONS.get(station).x(),CityMetroLine.Z,CityMetroLine.RAIL_Y,true,station<=2?-35:35);
        return out.isEmpty()?null:out.toArray(CityTransit.Over[]::new);
    }
    private static void column(List<CityTransit.Over> out,int from,int to,String block) {
        for(int y=from;y<=to;y++)out.add(new CityTransit.Over(y,block));
    }
}
