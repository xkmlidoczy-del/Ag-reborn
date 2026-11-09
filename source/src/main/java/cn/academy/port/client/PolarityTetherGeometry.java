package cn.academy.port.client;

/** Pure first-person-only tether geometry. Does not change server endpoints, range or movement. */
public final class PolarityTetherGeometry {
    public static final double WRIST_FORWARD = .45, WRIST_DOWN = .20, WRIST_SIDE = .14;
    public static final double NEAR_DEPTH = .20, MAX_ANGULAR_HALF_WIDTH = .02;
    public record Point(double x, double y, double z) {
        public boolean finite() { return Double.isFinite(x) && Double.isFinite(y) && Double.isFinite(z); }
        public double dot(Point p) { return x*p.x+y*p.y+z*p.z; }
        public Point plus(Point p) { return new Point(x+p.x,y+p.y,z+p.z); }
        public Point scale(double s) { return new Point(x*s,y*s,z*s); }
        public Point cross(Point p) { return new Point(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x); }
        public double length() { return Math.sqrt(dot(this)); }
        public Point lerp(Point p,double t) { return scale(1-t).plus(p.scale(t)); }
    }
    public record Segment(Point start,Point end,double halfWidth) {}
    private PolarityTetherGeometry() {}
    /** Camera-relative wrist, using camera up so a vertical look never collapses the side basis. */
    public static Point wrist(Point forward,Point cameraUp,boolean leftHanded) {
        Point f=unit(forward), u=unit(cameraUp);
        if(f==null||u==null)return null;
        Point right=unit(f.cross(u)); if(right==null)return null;
        Point up=right.cross(f);
        return f.scale(WRIST_FORWARD).plus(up.scale(-WRIST_DOWN)).plus(right.scale(leftHanded?-WRIST_SIDE:WRIST_SIDE));
    }
    /** Clip full ribbon width before projection; very near ribbons also get a bounded angular width. */
    public static Segment clip(Point start,Point end,Point forward,double halfWidth) {
        Point f=unit(forward);
        if(start==null||end==null||!start.finite()||!end.finite()||f==null||!Double.isFinite(halfWidth)
                ||halfWidth<=0||halfWidth>.1||start.length()>32||end.length()>32)return null;
        double a=start.dot(f), b=end.dot(f), depth=NEAR_DEPTH+halfWidth;
        if(a<depth&&b<depth)return null;
        if(a<depth)start=start.lerp(end,(depth-a)/(b-a));
        else if(b<depth)end=start.lerp(end,(depth-a)/(b-a));
        double clippedWidth=Math.min(halfWidth,Math.min(start.dot(f),end.dot(f))*MAX_ANGULAR_HALF_WIDTH);
        return new Segment(start,end,clippedWidth);
    }
    private static Point unit(Point p) {
        if(p==null||!p.finite())return null;
        double length=p.length();return !Double.isFinite(length)||length<1e-9?null:p.scale(1/length);
    }
}
