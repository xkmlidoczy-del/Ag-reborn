package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;

/** NEW411 cosmetic pressure contours. Radius/fan spread never participate in target selection. */
public final class DeflectionStrikeGeometry {
    public static final int MAX_CONTROLLERS = 1, MAX_PARTICLES = 16, MAIN_LAYERS = 1, AFTERIMAGES = 0;
    public static final int MAX_STROKES = 41, MATERIAL_LAYERS = 2;
    public static final int MAX_VERTICES = MAX_STROKES * MATERIAL_LAYERS * 4 + MAX_PARTICLES * 4;
    public static final double CULL_DISTANCE = 64, NEAR_PLANE = .08;
    public static final double MAX_CORE_WIDTH = .080, MAX_SHOULDER_WIDTH = .160;
    public enum Role { HAND, WAVE, CONTACT }
    public record Stroke(Point from, Point to, double width, int alpha, Role role) {
        public Stroke(Point from, Point to, double width, int alpha) { this(from, to, width, alpha, Role.WAVE); }
    }
    /** Both materials occupy the same contour: a readable source-texture core and its soft gray shoulder. */
    public record Profile(double width, int alpha, int gray, float minV, float maxV) {}
    public record Dust(Point position, double size, int alpha) {}
    public record Shape(List<Stroke> strokes, List<Dust> particles) {
        public Shape { strokes = List.copyOf(strokes); particles = List.copyOf(particles); }
        public int vertexCount() { return strokes.size() * MATERIAL_LAYERS * 4 + particles.size() * 4; }
    }
    private DeflectionStrikeGeometry() {}

    public static Shape shape(DeflectionStrikeState.Controller cast, double age) {
        if (cast == null || !Double.isFinite(age) || age < 0 || age >= DeflectionStrikeState.LIFE_TICKS
                || !cast.origin().finite() || !cast.end().finite() || cast.contacts().size() > 2) return new Shape(List.of(), List.of());
        Point delta = subtract(cast.end(), cast.origin());
        double length = Math.sqrt(dot(delta, delta));
        if (length < 1e-6 || length > 8.000001) return new Shape(List.of(), List.of());
        Point direction = scale(delta, 1 / length);
        Point right = normalized(cross(direction, Math.abs(direction.y()) > .98 ? new Point(0, 0, 1) : new Point(0, 1, 0)));
        Point up = normalized(cross(right, direction));
        int alpha = alpha(age);
        double travel = Math.min(1, age / 5.0), settle = Math.max(0, (age - 5) / 7.0);
        // Only the traveling pressure band gets the broad support; hand and contact geometry stay narrow.
        double dissolve = waveDissolve(age), width = MAX_CORE_WIDTH * (1 - .50 * dissolve);
        int waveAlpha = (int)Math.round(215 * (1 - dissolve));
        Point palm = add(cast.origin(), add(scale(direction, Math.min(.28, length)), add(scale(right, .20), scale(up, -.22))));
        Point center = lerp(cast.origin(), cast.end(), travel);
        var strokes = new ArrayList<Stroke>(MAX_STROKES);
        // An open cuff near the casting hand and a short forward stroke identify the source and force direction.
        if (age < 5) {
            Point wrist = add(palm, scale(direction, -.14));
            strokes.add(new Stroke(add(wrist, scale(right, -.07)), add(palm, scale(right, -.10)), .044, alpha, Role.HAND));
            strokes.add(new Stroke(add(wrist, scale(right, .07)), add(palm, scale(right, .10)), .044, alpha, Role.HAND));
            strokes.add(new Stroke(palm, add(palm, scale(direction, Math.min(.25, length))), .044 * .7, alpha, Role.HAND));
        }
        // The crouch fan is deliberately open. It decorates the same straight confirmed force path.
        int segments = cast.crouch() ? 24 : 32;
        double radius = cast.crouch() ? .24 + .42 * travel - .12 * settle : .16 + .08 * travel - .04 * settle;
        double start = cast.crouch() ? .15 * Math.PI : 0, sweep = cast.crouch() ? .70 * Math.PI : 2 * Math.PI;
        for (int i = 0; i < segments; i++) {
            Point a = radial(center, right, up, radius, start + sweep * i / segments);
            Point b = radial(center, right, up, radius, start + sweep * (i + 1) / segments);
            strokes.add(new Stroke(a, b, width, waveAlpha, Role.WAVE));
        }
        var particles = new ArrayList<Dust>(MAX_PARTICLES);
        for (int contactIndex = 0; contactIndex < cast.contacts().size(); contactIndex++) {
            var contact = cast.contacts().get(contactIndex);
            if (!confirmed(contact.status()) || contact.point() == null || !contact.point().finite()) continue;
            double arrival = Math.sqrt(cast.origin().distanceSquared(contact.point())) / length * 5;
            double contactAge = age - arrival;
            if (contactAge < 0 || contactAge >= 6) continue;
            int contactAlpha = (int)Math.round(185 * (1 - contactAge / 6));
            Point tail = add(contact.point(), scale(direction, .05));
            Point tip = add(tail, scale(direction, .28 + .03 * contactAge));
            strokes.add(new Stroke(tail, tip, .013 * (1 - contactAge / 8), contactAlpha, Role.CONTACT));
            strokes.add(new Stroke(tip, add(add(tip, scale(direction, -.095)), scale(right, .065)), .011, contactAlpha, Role.CONTACT));
            strokes.add(new Stroke(tip, add(add(tip, scale(direction, -.095)), scale(right, -.065)), .011, contactAlpha, Role.CONTACT));
            for (int i = 0; i < 8; i++) {
                double angle = i * Math.PI / 4 + contactIndex * .31;
                double spread = .018 + contactAge * (.015 + (i % 3) * .005);
                Point offset = add(scale(right, Math.cos(angle) * spread), scale(up, Math.sin(angle) * spread - .008 * contactAge));
                Point position = add(contact.point(), add(offset, scale(direction, contactAge * .012)));
                particles.add(new Dust(position, .019 + .005 * contactAge, contactAlpha));
            }
        }
        return new Shape(strokes, particles);
    }
    public static boolean confirmed(int status) { return status == DeflectionStrikeState.DAMAGED || status == DeflectionStrikeState.ABSORBED || status == DeflectionStrikeState.PUSHED; }
    public static int alpha(double age) {
        return !Double.isFinite(age) || age < 0 || age >= DeflectionStrikeState.LIFE_TICKS ? 0
                : (int)Math.round(215 * Math.min(1, (DeflectionStrikeState.LIFE_TICKS - age) / 7));
    }
    public static Profile profile(Stroke stroke, double age, boolean core) {
        if (stroke.role() == Role.HAND) {
            // A hand stroke spends its existing eight vertices on two soft, end-tapered quads.
            // No crisp core near the camera: its former opaque strip could fill a large screen region.
            return new Profile(stroke.width() * (core ? 1 : 2.7), core ? 0 : (int)Math.round(stroke.alpha() * .28),
                    core ? 235 : 208, 0, 1);
        }
        if (stroke.role() == Role.WAVE) {
            double dissolve = waveDissolve(age);
            if (core) return new Profile(stroke.width(), (int)Math.round(stroke.alpha() * (1 - dissolve)),
                    235, 11f / 32, 21f / 32);
            return new Profile(MAX_SHOULDER_WIDTH * (1 - .15 * dissolve),
                    (int)Math.round(stroke.alpha() * (.44 + .12 * dissolve)), 188, 0, 1);
        }
        // Native-confirmed contact arrows retain their prior material and timing.
        double settle = Math.max(0, Math.min(1, (age - 5) / 7));
        if (core) {
            double dissolve = Math.max(0, Math.min(1, (age - 6) / 5));
            dissolve = dissolve * dissolve * (3 - 2 * dissolve);
            // Crop only the strong central part of the original glow_line. RGB and peak alpha stay unchanged.
            return new Profile(stroke.width(), (int)Math.round(stroke.alpha() * (1 - .90 * dissolve)),
                    235, 11f / 32, 21f / 32);
        }
        return new Profile(stroke.width() * (2.7 + 1.4 * settle),
                (int)Math.round(stroke.alpha() * (.44 + .12 * settle)), 188, 0, 1);
    }
    private static double waveDissolve(double age) { return smoothUnit((age - 8) / 4); }
    private static double smoothUnit(double value) {
        double unit = Math.max(0, Math.min(1, value));
        return unit * unit * (3 - 2 * unit);
    }
    public static double handNearEyeFade(double distance) {
        return Double.isFinite(distance) ? smoothUnit((distance - .35) / .85) : 0;
    }
    public static int handAlpha(int peak, double along, double distance) {
        if (!Double.isFinite(along) || along <= 0 || along >= 1) return 0;
        return (int)Math.round(peak * (1 - Math.abs(2 * along - 1)) * handNearEyeFade(distance));
    }
    /** Precision boundary: subtract the camera in double before the render adapter casts anything to float. */
    public static Point relative(Point world, Point camera) { return subtract(world, camera); }
    public static Stroke clipNear(Stroke relative, Point forward) {
        return clipNear(relative, forward, 0);
    }
    /** Reserve half the billboard width so no corner can cross the eye plane after centerline clipping. */
    public static Stroke clipNear(Stroke relative, Point forward, double halfWidth) {
        double plane = NEAR_PLANE + Math.max(0, halfWidth);
        double a = dot(relative.from, forward), b = dot(relative.to, forward);
        if (a <= plane && b <= plane) return null;
        Point from = relative.from, to = relative.to;
        if (a < plane) from = lerp(from, to, (plane - a) / (b - a));
        else if (b < plane) to = lerp(from, to, (plane - a) / (b - a));
        return new Stroke(from, to, relative.width, relative.alpha, relative.role);
    }
    private static Point radial(Point c, Point right, Point up, double radius, double angle) {
        return add(c, add(scale(right, Math.cos(angle) * radius), scale(up, Math.sin(angle) * radius)));
    }
    private static Point normalized(Point p) { return scale(p, 1 / Math.sqrt(dot(p, p))); }
    private static Point add(Point a, Point b) { return new Point(a.x() + b.x(), a.y() + b.y(), a.z() + b.z()); }
    private static Point subtract(Point a, Point b) { return new Point(a.x() - b.x(), a.y() - b.y(), a.z() - b.z()); }
    private static Point scale(Point p, double scale) { return new Point(p.x() * scale, p.y() * scale, p.z() * scale); }
    private static Point lerp(Point a, Point b, double t) { return add(a, scale(subtract(b, a), t)); }
    private static double dot(Point a, Point b) { return a.x() * b.x() + a.y() * b.y() + a.z() * b.z(); }
    private static Point cross(Point a, Point b) { return new Point(a.y() * b.z() - a.z() * b.y(), a.z() * b.x() - a.x() * b.z(), a.x() * b.y() - a.y() * b.x()); }
}
