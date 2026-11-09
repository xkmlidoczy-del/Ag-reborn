package cn.academy.port.city;

import java.util.HashMap;
import java.util.Map;

/** Sidewalk furniture must retain two clear lanes and avoid transport and landmarks. */
public final class CityNeighborhoodPropsRegressionTest {
    public static void main(String[] args) {
        long seed=20261103L; Map<Integer,Integer> districts=new HashMap<>();int[] kinds=new int[4];int vertical=0,horizontal=0;
        for(int row=0;row<5;row++) for(int col=0;col<5;col++) {
            int left=-2000+col*800,north=-2000+row*800,d=CityPlan.district(left+400,north+400);
            if(d<1 || d>20) continue;
            for(int bx=0;bx<12;bx++) for(int bz=0;bz<12;bz++) for(boolean v:new boolean[]{true,false})
                for(int along:new int[]{20,32,44}) {
                    int x=left+12+bx*64+(v?10:along),z=north+12+bz*64+(v?along:10);
                    var c=CityNeighborhoodProps.at(seed,x,z);if(c==null)continue;
                    districts.merge(d,1,Integer::sum);kinds[c.kind()]++;if(v)vertical++;else horizontal++;
                    check(CityPlan.sample(seed,x,z).kind()==CityPlan.Kind.SIDEWALK,"amenity outside sidewalk");
                    check(!CityRingTransit.overlapsLot(x,z,1,1) && !CityNorthSouthTransit.overlapsLot(x,z,1,1)
                            && !CityAirportTransit.overlapsLot(x,z,1,1) && CityTransit.overlay(x,z)==null
                            && CitySampleRoute.at(x,z)==null && CityStreetProps.at(seed,x,z)==null
                            && CityDistrictLandmarks.at(seed,x,z)==null,"amenity conflicts with protected route");
                    for(int n=1;n<=2;n++) {
                        int px=x-(v?n:0),pz=z-(v?0:n);
                        check(CityPlan.sample(seed,px,pz).kind()==CityPlan.Kind.SIDEWALK
                                && CityNeighborhoodProps.at(seed,px,pz)==null
                                && CityCommercialStreets.at(seed,px,pz,CityPlan.sample(seed,px,pz))==null,
                                "two sidewalk lanes unavailable at "+x+","+z);
                    }
                    if(c.kind()==0) check("academy:pillar_dark".equals(c.block(4))
                            && "minecraft:lantern[hanging=false,waterlogged=false]".equals(c.block(5)),"supported sidewalk light");
                    if(c.kind()==2) check("minecraft:cauldron".equals(c.block(0)),"empty cauldron has no level property");
                }
        }
        for(int d=1;d<=20;d++)check(districts.getOrDefault(d,0)>0,"no amenities in D"+d);
        for(int i=0;i<kinds.length;i++)check(kinds[i]>100,"amenity kind underrepresented: "+i);
        check(vertical>100 && horizontal>100,"missing sidewalk orientation");
        System.out.println("PASS sidewalk amenities across 20 districts kinds="+java.util.Arrays.toString(kinds)
                +" vertical="+vertical+" horizontal="+horizontal);
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
