package cn.academy.port.highpower.core;

import cn.academy.port.core.SkillTuning;

/** NEW-121 pure finite geometry. All tuning beyond 32m/0.6D/10 ticks/six targets is provisional. */
public final class ConductiveCircuitRules {
    public static final double PLACEMENT_RANGE = SkillTuning.number("conductive_circuit", "placement_reach"), MAX_LENGTH = SkillTuning.number("conductive_circuit", "reach"), MIN_LENGTH=.5, OWNER_RANGE=32;
    public static final double MAX_STEP=2, FOOT_TOLERANCE=.15, SIDE_EPSILON=1e-6, CORRIDOR=.05;
    public static final int LIFETIME=200, PULSE_TICKS = SkillTuning.integer("conductive_circuit", "duration_ticks"), TARGET_INTERVAL=10, MAX_ATTEMPTS=6, SCAN_LIMIT=64;
    private static final double[] COST={0,0,5,2,.5,.1};
    public enum Phase { FIRST_ANCHOR, RESTING, ENERGIZED, ENDED }
    public record Point(double x,double y,double z) {
        public boolean finite(){return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z);}
    }
    private ConductiveCircuitRules() {}
    public static double cpCost(int level){return level>=2&&level<=5?COST[level]:Double.NaN;}
    public static boolean expired(long start,long now){return now<start||now-start>=LIFETIME;}
    public static boolean intervalReady(boolean recorded,long last,long now){return now>=0&&(!recorded||last>=0&&now>=last&&now-last>=TARGET_INTERVAL);}
    public static boolean segmentValid(Point a,Point b){
        if(a==null||b==null||!a.finite()||!b.finite()||Math.abs(a.y-b.y)>1e-6)return false;
        double dx=b.x-a.x,dz=b.z-a.z,l2=dx*dx+dz*dz;
        return l2>=MIN_LENGTH*MIN_LENGTH&&l2<=MAX_LENGTH*MAX_LENGTH;
    }
    /** Stop at the world's vertical boundary before asking whether the traversed ray region is loaded. */
    public static Point rayEndWithinHeight(Point start,Point desired,double minY,double maxY){
        if(start==null||desired==null||!start.finite()||!desired.finite()||!Double.isFinite(minY)||!Double.isFinite(maxY)
                ||minY>=maxY||start.y<minY||start.y>=maxY)return null;
        double fraction=1,dy=desired.y-start.y;
        if(desired.y<minY)fraction=(minY-start.y)/dy;
        else if(desired.y>=maxY)fraction=(Math.nextDown(maxY)-start.y)/dy;
        if(!Double.isFinite(fraction)||fraction<0||fraction>1)return null;
        Point end=new Point(start.x+(desired.x-start.x)*fraction,
                Math.max(minY,Math.min(Math.nextDown(maxY),start.y+dy*fraction)),start.z+(desired.z-start.z)*fraction);
        return end.finite()?end:null;
    }
    /** A real center-foot trajectory crosses the finite segment, not a standing/current bounding box. */
    public static boolean crosses(Point a,Point b,Point previous,Point current,long previousTick,long now){
        if(!segmentValid(a,b)||previous==null||current==null||!previous.finite()||!current.finite()
                ||now<=previousTick||now-previousTick!=1||Math.abs(previous.y-a.y)>FOOT_TOLERANCE
                ||Math.abs(current.y-a.y)>FOOT_TOLERANCE)return false;
        double mx=current.x-previous.x,my=current.y-previous.y,mz=current.z-previous.z;
        if(mx*mx+my*my+mz*mz>MAX_STEP*MAX_STEP)return false;
        double dx=b.x-a.x,dz=b.z-a.z,len=Math.sqrt(dx*dx+dz*dz);
        double oldSide=(dx*(previous.z-a.z)-dz*(previous.x-a.x))/len;
        double side=(dx*(current.z-a.z)-dz*(current.x-a.x))/len;
        // First arrival at the line counts once. Starting on it, standing or sliding along it never does.
        if(Math.abs(oldSide)<=SIDE_EPSILON||oldSide>0&&side>SIDE_EPSILON||oldSide<0&&side<-SIDE_EPSILON)return false;
        double fraction=oldSide/(oldSide-side);
        if(!Double.isFinite(fraction)||fraction<0||fraction>1)return false;
        double x=previous.x+mx*fraction,z=previous.z+mz*fraction;
        double projection=((x-a.x)*dx+(z-a.z)*dz)/(len*len);
        return projection>=0&&projection<=1;
    }
    /** Liang-Barsky against an XZ cell; used for a complete supercover, never sparse terrain samples. */
    public static boolean intersectsCell(Point a,Point b,int x,int z,double margin){
        if(a==null||b==null||!a.finite()||!b.finite()||!Double.isFinite(margin)||margin<0)return false;
        double[] range={0,1}; double dx=b.x-a.x,dz=b.z-a.z;
        return clip(-dx,a.x-(x-margin),range)&&clip(dx,x+1+margin-a.x,range)
                &&clip(-dz,a.z-(z-margin),range)&&clip(dz,z+1+margin-a.z,range);
    }
    private static boolean clip(double p,double q,double[] range){
        if(p==0)return q>=0;
        double t=q/p;
        if(p<0){if(t>range[1])return false;range[0]=Math.max(range[0],t);}
        else{if(t<range[0])return false;range[1]=Math.min(range[1],t);}
        return true;
    }
}
