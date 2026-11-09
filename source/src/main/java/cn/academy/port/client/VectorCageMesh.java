package cn.academy.port.client;

import cn.academy.port.client.VectorCageState.Point;
import java.util.ArrayList;
import java.util.List;

/** Four finite receipt-position pressure planes. White/gray open strokes, no particles or collision mesh. */
public final class VectorCageMesh {
    public static final int MAX_CONTROLLERS = 4, MAX_PARTICLES = 0, MATERIAL_LAYERS = 2;
    public static final int MAX_CONTROLLER_VERTICES = 512, MAX_VERTICES = 2048;
    public static final double CULL_DISTANCE = 72, NEAR_PLANE = .08, MARGIN = .28;
    public enum Phase { PREVIEW, WARMUP, COMPRESS, REST, END }
    public enum Role { FOOTPRINT, EDGE, SPINE, PRESSURE, DAMAGE, ABSORBED }
    public record Stroke(Point from, Point to, double width, int alpha, Role role) {}
    public record Panel(List<Point> corners, int alpha) { public Panel { corners = List.copyOf(corners); } }
    public record Profile(double width, int alpha, int gray, float minV, float maxV) {}
    public record Shape(Phase phase, List<Stroke> strokes, List<Panel> panels, boolean fallback) {
        public Shape { strokes = List.copyOf(strokes); panels = List.copyOf(panels); }
        public int vertexCount() { return strokes.size() * MATERIAL_LAYERS * 4 + panels.size() * 4; }
    }
    public record Bounds(Point min, Point max) {
        public boolean contains(Point p) { return p.x() >= min.x() && p.y() >= min.y() && p.z() >= min.z()
                && p.x() <= max.x() && p.y() <= max.y() && p.z() <= max.z(); }
    }
    private VectorCageMesh() {}
    public static Phase phase(VectorCageState.Frame f) {
        if (f == null || f.phase() == VectorCageState.ENDED) return Phase.END;
        if (f.phase() == VectorCageState.PREVIEW) return Phase.PREVIEW;
        if (f.age() < 6) return Phase.WARMUP;
        return f.pulse() >= 0 ? Phase.COMPRESS : Phase.REST;
    }
    public static Shape shape(VectorCageState.Controller c) { return shape(c, MAX_CONTROLLER_VERTICES); }
    /** Whole four-plane core survives decoration budget exhaustion; no partially drawn cage or arrow. */
    public static Shape shape(VectorCageState.Controller c, int budget) {
        if (c == null || !VectorCageState.valid(c.frame()) || budget < 0) return empty();
        Phase phase = phase(c.frame()); if (phase == Phase.END) return empty();
        var core = new ArrayList<Stroke>(); var decorations = new ArrayList<Stroke>(); var panels = new ArrayList<Panel>();
        var f = c.frame(); Point center = f.center(); double h = f.halfWidth();
        int alpha = switch (phase) { case PREVIEW -> 92; case WARMUP -> 170; case COMPRESS -> 232; case REST -> 58; default -> 0; };
        if (phase == Phase.PREVIEW) {
            // Eight fixed corner segments describe the selected 8 m square without an opaque ground sheet.
            for (int x : new int[]{-1, 1}) for (int z : new int[]{-1, 1}) {
                Point corner = offset(center, x * 4, .055, z * 4);
                core.add(new Stroke(corner, offset(corner, -x * .64, 0, 0), .026, alpha, Role.FOOTPRINT));
                core.add(new Stroke(corner, offset(corner, 0, 0, -z * .64), .026, alpha, Role.FOOTPRINT));
            }
            if (f.low()) {
                // Four short inward-facing open lines distinguish the frozen low mode already at mark time.
                for (int wall = 0; wall < 4; wall++) arrow(decorations, center, wall, 3.72, .055, 78, .023);
            }
        } else {
            // Native F1 capture exposed subpixel contours at ordinary viewing distances. Broaden only
            // warmup/confirmed compression strokes; the field, inactive rests and face fill stay fixed.
            double lowerWidth=phase==Phase.COMPRESS?.055:phase==Phase.WARMUP?.042:.025;
            double upperWidth=phase==Phase.COMPRESS?.050:phase==Phase.WARMUP?.038:.022;
            double uprightWidth=phase==Phase.COMPRESS?.045:phase==Phase.WARMUP?.034:.021;
            for (int wall = 0; wall < 4; wall++) {
                var points = face(center, wall, h);
                if (f.low()) {
                    // Open upper/lower rails, with deliberate corner gaps. Never a closed barrier motif.
                    core.add(new Stroke(lerp(points.get(0), points.get(1), .10), lerp(points.get(0), points.get(1), .90), lowerWidth, alpha, Role.EDGE));
                    core.add(new Stroke(lerp(points.get(3), points.get(2), .10), lerp(points.get(3), points.get(2), .90), upperWidth, alpha * 3 / 4, Role.EDGE));
                } else {
                    core.add(new Stroke(points.get(0), points.get(1), lowerWidth, alpha, Role.EDGE));
                    core.add(new Stroke(points.get(3), points.get(2), upperWidth, alpha * 3 / 4, Role.EDGE));
                    // One vertical edge per face avoids double-bright duplicate corners.
                    core.add(new Stroke(points.get(0), points.get(3), uprightWidth, alpha * 2 / 3, Role.EDGE));
                }
                if (phase == Phase.COMPRESS) {
                    if (!f.low()) {
                        panels.add(new Panel(points, 9));
                        Point middle=lerp(points.get(0),points.get(1),.5);
                        // One narrow finite spine makes each moving plane legible without filling it.
                        decorations.add(new Stroke(offset(middle,0,.20,0),offset(middle,0,2.80,0),.070,alpha,Role.SPINE));
                    }
                    arrow(decorations, center, wall, h, 1.15 + (f.pulse() % 2) * .40, alpha * 7 / 8, .052);
                }
            }
            for (var contact : c.contacts()) {
                if (contact.age() < 0 || contact.age() >= VectorCageState.CONTACT_TICKS) continue;
                int opacity = (VectorCageState.CONTACT_TICKS - contact.age()) * 34;
                double radius = .19 - contact.age() * .022;
                Point p = contact.point();
                // A small contraction at the receipt, never at every target or the next pressure front.
                Point horizontal = contact.wall() < 2 ? new Point(0,0,1) : new Point(1,0,0);
                if (contact.status() == VectorCageState.DAMAGED) {
                    for (int sign : new int[]{-1, 1}) {
                        decorations.add(new Stroke(add(p, scale(horizontal, sign * radius)), add(p, scale(horizontal, sign * radius * .38)), .028, opacity, Role.DAMAGE));
                        decorations.add(new Stroke(offset(p, 0, sign * radius, 0), offset(p, 0, sign * radius * .38, 0), .028, opacity, Role.DAMAGE));
                    }
                } else if (contact.status() == VectorCageState.ABSORBED) {
                    // Two separated gray ticks mean absorbed loss, never an immunity bubble or shield.
                    for (int sign : new int[]{-1, 1}) {
                        Point q = offset(p, 0, sign * radius * .5, 0);
                        decorations.add(new Stroke(add(q, scale(horizontal, -radius * .7)), add(q, scale(horizontal, radius * .7)), .024, opacity * 2 / 3, Role.ABSORBED));
                    }
                }
            }
        }
        int cap = Math.min(MAX_CONTROLLER_VERTICES, budget);
        var all = new ArrayList<>(core); all.addAll(decorations);
        Shape full = new Shape(phase, all, panels, false);
        if (full.vertexCount() <= cap) return full;
        Shape fallback = new Shape(phase, core, List.of(), true);
        return fallback.vertexCount() <= cap ? fallback : new Shape(phase, List.of(), List.of(), true);
    }
    private static Shape empty() { return new Shape(Phase.END, List.of(), List.of(), false); }
    /** Corners follow each actual axis-aligned pressure rectangle, including its finite vertical height. */
    public static List<Point> face(Point c, int wall, double h) {
        return switch (wall) {
            case 0 -> List.of(offset(c,-h,.05,-h),offset(c,-h,.05,h),offset(c,-h,3.05,h),offset(c,-h,3.05,-h));
            case 1 -> List.of(offset(c,h,.05,h),offset(c,h,.05,-h),offset(c,h,3.05,-h),offset(c,h,3.05,h));
            case 2 -> List.of(offset(c,h,.05,-h),offset(c,-h,.05,-h),offset(c,-h,3.05,-h),offset(c,h,3.05,-h));
            case 3 -> List.of(offset(c,-h,.05,h),offset(c,h,.05,h),offset(c,h,3.05,h),offset(c,-h,3.05,h));
            default -> List.of();
        };
    }
    private static void arrow(List<Stroke> out, Point center, int wall, double h, double height, int alpha, double width) {
        Point normal = switch (wall) { case 0 -> new Point(1,0,0); case 1 -> new Point(-1,0,0); case 2 -> new Point(0,0,1); default -> new Point(0,0,-1); };
        Point side = new Point(-normal.z(),0,normal.x());
        Point root = add(offset(center,0,height,0), scale(normal,-h));
        double length = Math.min(.62, h * .62), wing = Math.min(.16, length * .35);
        Point tip = add(root,scale(normal,length));
        out.add(new Stroke(root,tip,width,alpha,Role.PRESSURE));
        out.add(new Stroke(tip,add(add(tip,scale(normal,-wing)),scale(side,wing)),width,alpha,Role.PRESSURE));
        out.add(new Stroke(tip,add(add(tip,scale(normal,-wing)),scale(side,-wing)),width,alpha,Role.PRESSURE));
    }
    public static Profile profile(Stroke s, boolean core) {
        if(s.role==Role.EDGE||s.role==Role.SPINE||s.role==Role.PRESSURE)
            return core?new Profile(s.width,s.alpha,244,13f/32,18f/32)
                    :new Profile(s.width*3.4,s.alpha*3/5,136,0,1);
        return core ? new Profile(s.width,s.alpha,236,11f/32,21f/32)
                : new Profile(s.width*2.2,s.alpha*3/10,156,0,1);
    }
    public static Bounds bounds(VectorCageState.Controller c) {
        return new Bounds(offset(c.frame().center(),-4-MARGIN,.05-MARGIN,-4-MARGIN), offset(c.frame().center(),4+MARGIN,3.05+MARGIN,4+MARGIN));
    }
    public static Point relative(Point world, Point camera) { return new Point(world.x()-camera.x(),world.y()-camera.y(),world.z()-camera.z()); }
    public static Stroke clipNear(Stroke s, Point forward, double halfWidth) {
        double plane=NEAR_PLANE+Math.max(0,halfWidth), a=dot(s.from,forward), b=dot(s.to,forward);
        if (a<=plane && b<=plane) return null;
        Point from=s.from,to=s.to;
        if(a<plane)from=lerp(from,to,(plane-a)/(b-a)); else if(b<plane)to=lerp(from,to,(plane-a)/(b-a));
        return new Stroke(from,to,s.width,s.alpha,s.role);
    }
    public static double nearEyeFade(double distance) { double t=Math.max(0,Math.min(1,(distance-.2)/.65)); return t*t*(3-2*t); }
    public static double dot(Point a,Point b) { return a.x()*b.x()+a.y()*b.y()+a.z()*b.z(); }
    private static Point offset(Point p,double x,double y,double z) { return new Point(p.x()+x,p.y()+y,p.z()+z); }
    private static Point add(Point a,Point b) { return offset(a,b.x(),b.y(),b.z()); }
    private static Point scale(Point p,double n) { return new Point(p.x()*n,p.y()*n,p.z()*n); }
    private static Point lerp(Point a,Point b,double t) { return new Point(a.x()+(b.x()-a.x())*t,a.y()+(b.y()-a.y())*t,a.z()+(b.z()-a.z())*t); }
}
