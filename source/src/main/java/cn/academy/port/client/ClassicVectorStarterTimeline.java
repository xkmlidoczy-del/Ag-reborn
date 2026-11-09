/* AcademyCraft1.0.7 ParabolaEffect/WaveEffect/WaveEffectUI adaptation, GPLv3;
 * LambdaLib1.2.3 VecUtils/CubicCurve math MIT, Copyright Lambda Innovation. See NOTICE. */
package cn.academy.port.client;

import cn.academy.port.skill.VecAccelSession;
import java.util.*;

/** Pure original mesh, curve, shader-projection and bounded transport math. */
public final class ClassicVectorStarterTimeline {
    public static final String ACCEL_SOUND = "vecmanip.vec_accel", DEVIATION_SOUND = "vecmanip.vec_deviation";
    public static final float ACCEL_VOLUME = .35F, DEVIATION_VOLUME = .5F;
    public static final int WAVE_LIFE = 15;
    private static final float[] SINES = new float[65536];
    static { for (int i = 0; i < SINES.length; i++) SINES[i] = (float) Math.sin(i * Math.PI * 2 / 65536); }
    private static final ClassicCubicCurve ALPHA = new ClassicCubicCurve(0,0,.2,1,.5,1,.8,1,1,0);
    private static final ClassicCubicCurve SIZE = new ClassicCubicCurve(0,.4,.2,.8,2.5,1.5);
    private ClassicVectorStarterTimeline() {}
    private static float sin(float a) { return SINES[(int) (a * 10430.378F) & 65535]; }
    private static float cos(float a) { return SINES[(int) (a * 10430.378F + 16384F) & 65535]; }
    public record Point(double x, double y, double z) {
        public Point add(Point p) { return new Point(x+p.x,y+p.y,z+p.z); }
        public Point scale(double n) { return new Point(x*n,y*n,z*n); }
        public Point normalize() { double d=Math.sqrt(x*x+y*y+z*z);return d<1E-4?new Point(0,0,0):scale(1/d); }
    }
    public static Point direction(float yaw, float pitch) {
        float y = yaw * (float) Math.PI / 180, p = pitch * (float) Math.PI / 180;
        return new Point(-sin(y)*cos(p),-sin(p),cos(y)*cos(p));
    }
    private static Point viewDirection(float yaw, float pitch) {
        float y = -yaw * .017453292F - (float) Math.PI, p = -pitch * .017453292F;
        float f1=cos(y),f2=sin(y),f3=-cos(p),f4=sin(p);
        return new Point(f2*f3,f4,f1*f3);
    }
    /** Exact100 points, .02 step, .98 drag, 1.9 gravity and the source rotateAroundY(90 RADIANS) quirk. */
    public static List<Point> parabola(float yaw, float pitch, int ticks) {
        Point look=viewDirection(yaw,pitch), raw=new Point(look.x,0,look.z);
        Point rotated=new Point(raw.x*cos(90)+raw.z*sin(90),0,raw.z*cos(90)-raw.x*sin(90)).normalize().scale(-.08);
        Point pos=new Point(rotated.x,-.04,rotated.z).add(look.scale(-.12));
        Point speed=direction(yaw,pitch-10).scale(VecAccelSession.speed(ticks));
        var points=new ArrayList<Point>(100);
        for(int i=0;i<100;i++) { points.add(pos);speed=speed.scale(.98);pos=pos.add(speed.scale(.02));speed=new Point(speed.x,speed.y-1.9*.02,speed.z); }
        return List.copyOf(points);
    }
    /** Negative source alpha after idx34 remains in the math; normalized GPU color clamps it to0. */
    public static float parabolaAlpha(int index) { return .7F*(1-index*.03F); }
    private static double clamp(double n,double max) { return Math.max(0,Math.min(max,n)); }
    public static double waveAlpha(int ticks, int ringLife, int offset) {
        return Math.min(clamp(ALPHA.valueAt(ticks/15.0),1),clamp(ALPHA.valueAt((ticks-offset)/(double)ringLife),1))*.7;
    }
    public static double waveSize(int ticks,double ringSize) { return ringSize*SIZE.valueAt(clamp(ticks/20.0,1.62)); }
    public static double waveDepth(int ticks,double offset) { return ticks/40.0+offset; }
    public static float rippleAlpha(float age,float life) { float p=age/life;return p<.2F?p/.2F:p<.5F?1:1-(p-.5F)/.5F; }
    public static float rippleSize(float size,float age) { return size+age*20; }
    /** Literal vm_wave_ui.vert omits clip-space*2 and Y inversion. Preserve the resulting central-half projection. */
    public static double rippleX(float x,float width) { return width/4.0+x/2.0; }
    public static double rippleY(float y,float height) { return height*.75-y/2.0; }
    public static final class Tokens {
        private record Key(String skill,int caster) {}
        private final Map<Key,Long> ended=new LinkedHashMap<>(),started=new LinkedHashMap<>();
        public boolean start(String skill,int caster,long token) {
            Key key=new Key(skill,caster);if(token<=0||token<=ended.getOrDefault(key,0L)||token<=started.getOrDefault(key,0L))return false;
            started.put(key,token); trim(started);return true;
        }
        public boolean end(String skill,int caster,long token) {
            Key key=new Key(skill,caster);if(token<=0||token<=ended.getOrDefault(key,0L))return false;
            ended.put(key,token);trim(ended);return token>=started.getOrDefault(key,0L);
        }
        public boolean live(String skill,int caster,long token) { Key key=new Key(skill,caster);return token>0&&started.getOrDefault(key,0L)==token&&ended.getOrDefault(key,0L)<token; }
        private static void trim(Map<?,?> map) { while(map.size()>512){var iter=map.keySet().iterator();iter.next();iter.remove();} }
        public void clear() { ended.clear();started.clear(); }
    }
}
