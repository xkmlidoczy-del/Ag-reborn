/* AcademyCraft1.0.7 AbilityInterferer/CPData + LambdaLib1.2.3 scheduling. GPLv3; see NOTICE. */
package cn.academy.port.interferer;

/** Source cube, captured predicate and two independent condition-sensitive clocks. No IF contract. */
public final class ClassicInterfererRules {
    public static final double MIN_RANGE=10,MAX_RANGE=100,SYNC_RADIUS=15;
    public static final int SCAN_INTERVAL=10,SYNC_INTERVAL=20,MAX_NAMES=128,MAX_NAME_LENGTH=64;
    private ClassicInterfererRules(){}
    public static double clampRange(double value){return Math.max(MIN_RANGE,Math.min(MAX_RANGE,value));}
    public static double loadRange(double value){return Double.isFinite(value)?clampRange(value):MIN_RANGE;}
    public static boolean validName(String value){return value!=null&&!value.isEmpty()&&value.length()<=MAX_NAME_LENGTH&&value.codePoints().noneMatch(Character::isISOControl);}
    public record Bounds(double minX,double minY,double minZ,double maxX,double maxY,double maxZ){
        public boolean inside(double x,double y,double z){return x>minX&&x<maxX&&y>minY&&y<maxY&&z>minZ&&z<maxZ;}
    }
    public static Bounds bounds(int x,int y,int z,double range){return new Bounds(x+.5-range,y+.5-range,z+.5-range,x+.5+range,y+.5+range,z+.5+range);}
    public record Due(boolean scan,boolean sync){}
    public static final class Clock {
        private int scan=SCAN_INTERVAL,sync=SYNC_INTERVAL;
        public Due tick(boolean enabled){boolean scanDue=enabled&&--scan<=0,syncDue=--sync<=0;if(scanDue)scan=SCAN_INTERVAL;if(syncDue)sync=SYNC_INTERVAL;return new Due(scanDue,syncDue);}
        public int scanRemaining(){return scan;}public int syncRemaining(){return sync;}
    }
}
