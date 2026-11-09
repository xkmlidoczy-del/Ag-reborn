package cn.academy.port.city;

/** Rooftop details must fit inside each footprint and have a solid roof below them. */
public final class CityRooftopRegressionTest {
    public static void main(String[] args) {
        for(int width=24;width<=48;width+=4) for(int depth=20;depth<=48;depth+=4) for(int variant=0;variant<4;variant++) {
            long hash=((long)variant)<<24;int features=0;
            for(int a=0;a<width;a++) for(int b=0;b<depth;b++) for(int y=-1;y<=5;y++) {
                String block=CityRooftop.block(hash,a,b,width,depth,y);if(block==null)continue;
                features++;
                check(a>0 && a<width-1 && b>0 && b<depth-1,"detail overruns parapet");
                if(y>0)check(CityRooftop.block(hash,a,b,width,depth,y-1)!=null,"floating rooftop detail");
            }
            check(features>0,"roof variant has no details");
            if(variant==2)check("academy:neon_cyan".equals(CityRooftop.block(hash,width/2,depth/2,width,depth,5)),"antenna beacon");
        }
        boolean[] seen=new boolean[4];
        for(int z=-1200;z<-400;z+=2) for(int x=-400;x<400;x+=2) {
            var sample=CityPlan.sample(20261103L,x,z);
            if(sample.kind()!=CityPlan.Kind.BUILDING || sample.bw()==0 || sample.a()!=sample.bw()/2
                    || sample.b()!=sample.bd()/2 || CityTemplateSites.at(20261103L,x,z)!=null
                    || CityDistrictLandmarks.at(20261103L,x,z)!=null)continue;
            int v=CityRooftop.variant(sample.hash());
            if(!seen[v]) {
                seen[v]=true;System.out.println("ROOF_EXAMPLE variant="+v+" x="+x+" z="+z+" y="+sample.height());
            }
        }
        for(boolean value:seen)check(value,"missing a D3 procedural roof variant");
        System.out.println("PASS four roof treatments across ordinary facade footprint sizes, with support and parapet clearance");
    }
    private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
}
