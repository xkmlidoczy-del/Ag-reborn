/* AcademyCraft MineDetect and LambdaLib WorldUtils adaptation. See NOTICE. */
package cn.academy.port.skill;

import cn.academy.port.core.SkillTuning;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Dependency-free exact scan traversal and source append-only handler state. */
public final class ClassicMineScan {
    public static final int SCAN_LIMIT = 1000;
    public static final double RANGE_CAP = SkillTuning.number("mine_detect", "reach_mastered");
    private ClassicMineScan() {}
    public interface OreAccess {
        boolean accepts(int x, int y, int z);
        int harvestLevel(int x, int y, int z);
    }
    public record Element(int x, int y, int z, int level) {}
    public static double range(double requested) {
        if (!Double.isFinite(requested) || requested <= 0) throw new IllegalArgumentException("Invalid mine range");
        return Math.min(requested, RANGE_CAP);
    }
    public static double safeDistanceSquared(double range) { return range * .2 * range * .2; }
    public static int colorLevel(boolean advanced, int harvest) { return advanced ? Math.min(3, harvest + 1) : 0; }
    public static List<Element> scan(double cx, double cy, double cz, double requested,
                                      boolean advanced, OreAccess access) {
        double r = range(requested);
        if (!finite(cx, cy, cz) || access == null) throw new IllegalArgumentException("Invalid mine scan");
        var found = new ArrayList<Element>();
        int x0 = (int) Math.floor(cx-r), x1 = (int) Math.ceil(cx+r);
        int y0 = (int) Math.floor(cy-r), y1 = (int) Math.ceil(cy+r);
        int z0 = (int) Math.floor(cz-r), z1 = (int) Math.ceil(cz+r);
        candidates:
        for (int x=x0; x<=x1; x++) for (int y=y0; y<=y1; y++) for (int z=z0; z<=z1; z++) {
            // WorldUtils calls its block selector BEFORE the inclusive integer-origin sphere.
            if (access.accepts(x,y,z) && distanceSquared(cx,cy,cz,x,y,z) <= r*r) {
                found.add(new Element(x,y,z,0));
                if (found.size() >= SCAN_LIMIT) break candidates;
            }
        }
        // WorldUtils returns all selected BlockPos objects before MineDetect's foreach
        // captures harvest metadata0; novice mode never queries harvest at all.
        if(advanced)for(int i=0;i<found.size();i++) {
            var e=found.get(i);found.set(i,new Element(e.x,e.y,e.z,
                    colorLevel(true,access.harvestLevel(e.x,e.y,e.z))));
        }
        return found;
    }
    public static double distanceSquared(double ax,double ay,double az,double bx,double by,double bz) {
        double x=ax-bx,y=ay-by,z=az-bz;return x*x+y*y+z*z;
    }
    private static boolean finite(double x,double y,double z) {
        return Double.isFinite(x)&&Double.isFinite(y)&&Double.isFinite(z)
                &&Math.abs(x)<30_000_100&&Math.abs(y)<30_000_100&&Math.abs(z)<30_000_100;
    }
    public static final class Handler {
        public final double range, safeDistanceSquared;
        public final boolean advanced;
        private final List<Element> aliveSims = new ArrayList<>();
        private final List<Element> readOnly = Collections.unmodifiableList(aliveSims);
        private double x,y,z,lastX,lastY,lastZ;
        private int age, refreshes;
        private boolean first = true, dead;
        public Handler(double x,double y,double z,double range,boolean advanced) {
            if(!finite(x,y,z))throw new IllegalArgumentException("Invalid mine handler position");
            this.x=x;this.y=y;this.z=z;this.range=ClassicMineScan.range(range);
            this.safeDistanceSquared=ClassicMineScan.safeDistanceSquared(this.range);this.advanced=advanced;
        }
        public boolean tick(double targetX,double targetY,double targetZ,OreAccess access) {
            if(dead)return false;
            if(!finite(targetX,targetY,targetZ)){dead=true;return false;}
            ++age;
            // EntityAdvanced.onFirstUpdate precedes the HandlerEntity's follow-target update.
            if(first){first=false;update(access);}
            x=targetX;y=targetY;z=targetZ;
            if(distanceSquared(x,y,z,lastX,lastY,lastZ)>safeDistanceSquared)update(access);
            if(age>MineDetectRules.TIME)dead=true;
            return !dead;
        }
        private void update(OreAccess access) {
            aliveSims.addAll(scan(x,y,z,range,advanced,access));
            lastX=x;lastY=y;lastZ=z;++refreshes;
        }
        public List<Element> aliveSims(){return readOnly;}
        public int age(){return age;}
        public int refreshes(){return refreshes;}
        public double x(){return x;}public double y(){return y;}public double z(){return z;}
    }
}
