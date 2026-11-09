package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** NEW423 open palm → real confirmed ray → short applied-momentum glyph. No particles or prediction. */
public final class YawPalmMesh {
    public static final int MAX_CONTROLLERS = 4, MAX_PARTICLES = 0, MAX_STROKES = 64, MATERIAL_LAYERS = 2;
    public static final int MAX_VERTICES = 2048, MAX_CONTROLLER_VERTICES = MAX_STROKES * MATERIAL_LAYERS * 4;
    public static final double CULL_DISTANCE = 64, NEAR_PLANE = .08, DECORATIVE_MARGIN = .85, MAX_ARROW_LENGTH = .52;
    public enum Role { PALM, PRESSURE, PATH, MOMENTUM, DAMAGE, ABSORBED }
    public record Stroke(Point from, Point to, Point anchor, double width, int alpha, Role role) {}
    public record Profile(double width, int alpha, int gray, float minV, float maxV) {}
    public record Bounds(Point min, Point max) {
        public boolean contains(Point p) { return p.x() >= min.x() && p.y() >= min.y() && p.z() >= min.z()
                && p.x() <= max.x() && p.y() <= max.y() && p.z() <= max.z(); }
    }
    public record Shape(List<Stroke> strokes) {
        public Shape { strokes = List.copyOf(strokes); }
        public int vertexCount() { return strokes.size() * MATERIAL_LAYERS * 4; }
    }
    private YawPalmMesh() {}
    public static Shape shape(YawPalmState.Controller c, double age) {
        if (c == null || !Double.isFinite(age) || age < 0 || age >= YawPalmState.LIFE_TICKS
                || c.contacts().isEmpty() || c.contacts().size() > 3) return new Shape(List.of());
        Point difference = subtract(c.end(), c.origin()); double length = length(difference);
        if (length < 1e-6 || length > 6.000001) return new Shape(List.of());
        Point direction = scale(difference, 1 / length);
        Point right = normalized(cross(direction, Math.abs(direction.y()) > .98 ? new Point(0,0,1) : new Point(0,1,0)));
        Point up = normalized(cross(right, direction));
        var strokes = new ArrayList<Stroke>(MAX_STROKES);
        int fade = alpha(age);
        double palmDistance = Math.min(.65, length * .5);
        Point palm = add(c.origin(), add(scale(direction, palmDistance), add(scale(right, c.crouch() ? -.18 : .18), scale(up, -.17))));
        if (age < 5) {
            int palmAlpha = (int)Math.round(235 * Math.min(1, (5 - age) / 2));
            // Five separated finger pressure strokes and an open palm/cuff, never a closed energy orb.
            for (int finger = 0; finger < 5; finger++) {
                double x = (finger - 2) * .060, height = finger == 0 || finger == 4 ? .16 : finger == 2 ? .27 : .23;
                Point root = add(palm, add(scale(right, x), scale(up, .025)));
                Point joint = add(root, add(scale(right, (finger - 2) * .020), scale(up, height * .58)));
                Point tip = add(root, add(scale(right, (finger - 2) * .032), scale(up, height)));
                add(strokes, root, joint, c.origin(), .052, palmAlpha, Role.PALM);
                add(strokes, joint, tip, c.origin(), .040, palmAlpha, Role.PALM);
            }
            Point left = add(palm, scale(right, -.14)), r = add(palm, scale(right, .14));
            Point wristLeft = add(palm, add(scale(right, -.07), scale(up, -.17)));
            Point wristRight = add(palm, add(scale(right, .07), scale(up, -.17)));
            add(strokes, left, wristLeft, c.origin(), .063, palmAlpha, Role.PALM);
            add(strokes, r, wristRight, c.origin(), .063, palmAlpha, Role.PALM);
            add(strokes, wristLeft, wristRight, c.origin(), .055, palmAlpha, Role.PALM);
            add(strokes, add(wristLeft, scale(up, -.06)), add(wristLeft, scale(right, -.06)), c.origin(), .040, palmAlpha, Role.PALM);
            add(strokes, add(wristRight, scale(up, -.06)), add(wristRight, scale(right, .06)), c.origin(), .040, palmAlpha, Role.PALM);
            // Two short open pressure crescents in the palm plane, not a sector or target area.
            for (int layer = 0; layer < 2; layer++) {
                Point center = add(palm, scale(direction, .07 + layer * .06));
                double radius = .25 + layer * .085;
                for (int i = 0; i < 4; i++) {
                    double a = -.62 + i * .31, b = a + .31;
                    Point p = add(center, add(scale(right, Math.sin(a) * radius), scale(up, Math.cos(a) * radius)));
                    Point q = add(center, add(scale(right, Math.sin(b) * radius), scale(up, Math.cos(b) * radius)));
                    add(strokes, p, q, c.origin(), .060 - layer * .012, palmAlpha, Role.PRESSURE);
                }
            }
        }
        // Every point lies on the authoritative ray. No fan, spread or extrapolated future endpoint.
        Point pathStart = add(c.origin(), scale(direction, Math.min(.44, length * .25)));
        Point pathEnd = add(c.end(), scale(direction, -Math.min(.07, length * .1)));
        add(strokes, pathStart, pathEnd, c.origin(), .016, (int)(fade * .68), Role.PATH);
        if (age >= 1) for (YawPalmState.Contact contact : c.contacts()) {
            if (contact.status() == YawPalmState.REFUSED) continue;
            Point anchor = add(contact.end(), scale(direction, -.09));
            if (contact.status() == YawPalmState.DAMAGED) {
                // Four compact separated marks communicate native damage only.
                for (int i = 0; i < 4; i++) {
                    double angle = Math.PI * (.25 + i * .5);
                    Point axis = add(scale(right, Math.cos(angle)), scale(up, Math.sin(angle)));
                    add(strokes, add(anchor, scale(axis, .035)), add(anchor, scale(axis, .105)), anchor, .023, fade, Role.DAMAGE);
                }
            } else if (contact.status() == YawPalmState.ABSORBED) {
                // Two quiet shield bars are distinct from the damage marks and momentum arrow.
                for (int i = -1; i <= 1; i += 2) {
                    Point center = add(anchor, scale(up, i * .060));
                    add(strokes, add(center, scale(right, -.105)), add(center, scale(right, .105)), anchor, .030, (int)(fade * .65), Role.ABSORBED);
                }
            }
            double deltaLength = length(contact.velocityDelta());
            if (deltaLength <= 1e-6) continue;
            Point side = scale(contact.velocityDelta(), 1 / deltaLength);
            double arrowLength = Math.min(MAX_ARROW_LENGTH, .22 + deltaLength * .25);
            Point tip = add(anchor, scale(side, arrowLength));
            Point wing = normalized(cross(side, direction));
            if (length(wing) < .5) wing = up;
            add(strokes, anchor, tip, anchor, .043, fade, Role.MOMENTUM);
            add(strokes, tip, add(add(tip, scale(side, -.105)), scale(wing, .073)), anchor, .039, fade, Role.MOMENTUM);
            add(strokes, tip, add(add(tip, scale(side, -.105)), scale(wing, -.073)), anchor, .039, fade, Role.MOMENTUM);
            // Short open shoulder arc around the same confirmed contact; not a movement path.
            for (int i = 0; i < 3; i++) {
                double a = -.65 + i * .43, b = a + .43;
                Point p = add(anchor, add(scale(side, .10 + .09 * Math.cos(a)), scale(wing, .15 * Math.sin(a))));
                Point q = add(anchor, add(scale(side, .10 + .09 * Math.cos(b)), scale(wing, .15 * Math.sin(b))));
                add(strokes, p, q, anchor, .023, (int)(fade * .65), Role.MOMENTUM);
            }
        }
        return new Shape(strokes);
    }
    private static void add(List<Stroke> strokes, Point a, Point b, Point anchor, double width, int alpha, Role role) {
        if (strokes.size() < MAX_STROKES && a.finite() && b.finite() && a.distanceSquared(b) > 1e-12)
            strokes.add(new Stroke(a, b, anchor, width, alpha, role));
    }
    public static int alpha(double age) { return !Double.isFinite(age) || age < 0 || age >= 12 ? 0 : (int)Math.round(228 * Math.min(1, (12 - age) / 6)); }
    public static Profile profile(Stroke stroke, boolean core) {
        if (core) return new Profile(stroke.width, stroke.alpha, 242, 11f / 32, 21f / 32);
        return new Profile(stroke.width * 2.35, (int)(stroke.alpha * .47), 176, 0, 1);
    }
    public static Bounds bounds(YawPalmState.Controller c) {
        Point min = c.origin(), max = c.origin();
        for (var contact : c.contacts()) {
            Point p = contact.end(); min = new Point(Math.min(min.x(),p.x()),Math.min(min.y(),p.y()),Math.min(min.z(),p.z()));
            max = new Point(Math.max(max.x(),p.x()),Math.max(max.y(),p.y()),Math.max(max.z(),p.z()));
        }
        Point margin = new Point(DECORATIVE_MARGIN,DECORATIVE_MARGIN,DECORATIVE_MARGIN);
        return new Bounds(subtract(min,margin),add(max,margin));
    }
    public static Point relative(Point world, Point camera) { return subtract(world, camera); }
    /** Width-aware clipping guarantees the entire billboard stays beyond the eye plane. */
    public static Stroke clipNear(Stroke relative, Point forward, double halfWidth) {
        double plane = NEAR_PLANE + Math.max(0,halfWidth), a = dot(relative.from,forward), b = dot(relative.to,forward);
        if (a <= plane && b <= plane) return null;
        Point from = relative.from, to = relative.to;
        if (a < plane) from = lerp(from,to,(plane-a)/(b-a));
        else if (b < plane) to = lerp(from,to,(plane-a)/(b-a));
        return new Stroke(from,to,relative.anchor,relative.width,relative.alpha,relative.role);
    }
    public static double nearEyeFade(double distance) { double t = Math.max(0,Math.min(1,(distance-.23)/.55)); return t*t*(3-2*t); }
    private static double length(Point p) { return Math.sqrt(dot(p,p)); }
    private static Point normalized(Point p) { double l=length(p); return l < 1e-9 ? YawPalmState.ZERO : scale(p,1/l); }
    private static Point add(Point a,Point b) { return new Point(a.x()+b.x(),a.y()+b.y(),a.z()+b.z()); }
    private static Point subtract(Point a,Point b) { return new Point(a.x()-b.x(),a.y()-b.y(),a.z()-b.z()); }
    private static Point scale(Point p,double s) { return new Point(p.x()*s,p.y()*s,p.z()*s); }
    private static Point lerp(Point a,Point b,double t) { return add(a,scale(subtract(b,a),t)); }
    private static double dot(Point a,Point b) { return a.x()*b.x()+a.y()*b.y()+a.z()*b.z(); }
    private static Point cross(Point a,Point b) { return new Point(a.y()*b.z()-a.z()*b.y(),a.z()*b.x()-a.x()*b.z(),a.x()*b.y()-a.y()*b.x()); }
}
