package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGeometry.Segment;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Decal;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * Shape vocabulary for per-skill identities, so that no two skills share a look: chevron trains, crescents, hexagon
 * rings with pins, cracks, converging lines, helices, orthogonal circuit traces, fork trees, lattices, segmented
 * gauges, orbiting satellites and framed panels. All pure, deterministic and finite; every function returns EMPTY on bad input.
 */
public final class FxMotifs {
    private FxMotifs() {}

    public static Point pt(double[] v) {
        if (v == null || v.length < 3) return null;
        Point p = new Point(v[0], v[1], v[2]);
        return p.finite() ? p : null;
    }
    public static boolean ok(Point... points) {
        for (Point p : points) if (p == null || !p.finite()) return false;
        return true;
    }
    public static Point unit(Point p) {
        if (p == null || !p.finite()) return null;
        double l = p.length();
        return l < 1e-9 ? null : p.scale(1 / l);
    }
    /** Two unit vectors perpendicular to {@code axis} and to each other. */
    public static Point[] frame(Point axis) {
        Point f = unit(axis);
        if (f == null) return null;
        Point ref = Math.abs(f.y()) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0);
        Point side = unit(f.cross(ref));
        return side == null ? null : new Point[] {f, side, side.cross(f)};
    }
    private static void line(List<Stroke> out, Point a, Point b, double width, int rgb, double alpha, int texture) {
        if (a == null || b == null || !RailThunderArrayGeometry.validEndpoints(a, b) || b.minus(a).length() < 1e-6 || alpha < 1) return;
        out.add(new Stroke(new Segment(a, b), width, rgb, FxLib.alpha(alpha, 1), texture));
    }
    /** Soft glow plus a thin white core: the standard "emissive line". */
    public static void glowLine(List<Stroke> out, Point a, Point b, double glowWidth, int rgb, double glowAlpha, double coreWidth, double coreAlpha) {
        line(out, a, b, glowWidth, rgb, glowAlpha, RailThunderArrayGrandGeometry.ARC_GLOW);
        if (coreWidth > 0) line(out, a, b, coreWidth, 0xFFFFFF, coreAlpha, RailThunderArrayGrandGeometry.WHITE);
    }

    /** A flat ring sprite in the plane (u, v) around {@code center}. */
    public static FxLib.Parts ring(Point center, Point u, Point v, double radius, int rgb, double alpha) {
        if (!ok(center, u, v) || radius <= 0 || alpha < 1) return FxLib.Parts.EMPTY;
        return new FxLib.Parts(List.of(), List.of(), List.of(new Decal(center, u, v, radius, rgb, FxLib.alpha(alpha, 1), RailThunderArrayGrandGeometry.RING)));
    }

    /** A train of {@code count} chevrons pointing along {@code dir}, evenly spaced, the lead ones brightest. */
    public static FxLib.Parts chevrons(Point from, Point dir, double length, int count, double size, double phase, int rgb, double alpha, VisualTier tier) {
        Point[] f = frame(dir);
        if (tier == null || !tier.enhanced() || !ok(from) || f == null || length <= 0 || count <= 0 || size <= 0) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double u = ((i + phase) % count) / count, fade = Math.sin(Math.PI * Math.min(1, u * 1.05));
            Point tip = from.plus(f[0].scale(u * length));
            for (int s : new int[] {-1, 1}) {
                Point tail = tip.minus(f[0].scale(size * .9)).plus(f[2].scale(s * size));
                glowLine(strokes, tip, tail, .07 * tier.widthScale, rgb, alpha * .55 * tier.glow * fade, .012, alpha * fade);
            }
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /** An arc of {@code spanDeg} in the plane (u, v) centred on direction u, like a parry crescent or a sweep. */
    public static FxLib.Parts arc(Point center, Point u, Point v, double radius, double spanDeg, int steps, double width, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v) || radius <= 0 || steps < 2 || steps > 64 || spanDeg <= 0) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        Point prev = null;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians((i / (double) steps - .5) * spanDeg);
            Point p = center.plus(u.scale(Math.cos(a) * radius)).plus(v.scale(Math.sin(a) * radius));
            double taper = Math.sin(Math.PI * i / steps);
            if (prev != null) glowLine(strokes, prev, p, width * 3 * tier.widthScale * (.3 + .7 * taper), rgb, alpha * .5 * tier.glow, width, alpha * taper);
            prev = p;
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /** {@code count} lines collapsing toward {@code center}: t=0 long and wide, t=1 gone (speed lines shrinking to a point). */
    public static FxLib.Parts converging(Point center, int count, double radius, double t, long seed, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center) || count <= 0 || radius <= 0 || !(t >= 0)) return FxLib.Parts.EMPTY;
        t = Math.min(1, t);
        List<Stroke> strokes = new ArrayList<>();
        FxLib.Rng rng = new FxLib.Rng(seed);
        for (int i = 0; i < count; i++) {
            double az = rng.range(0, Math.PI * 2), el = rng.range(-.5, 1.1), len = radius * rng.range(.6, 1);
            Point d = new Point(Math.cos(az) * Math.cos(el), Math.sin(el), Math.sin(az) * Math.cos(el));
            double outer = len * (1 - t) + .15, inner = outer * .45;
            glowLine(strokes, center.plus(d.scale(outer)), center.plus(d.scale(inner)), .05 * tier.widthScale, rgb, alpha * .5 * tier.glow * (1 - t * .6), .01, alpha * (1 - t * .5));
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /** A hexagon of the given circumradius in the plane (u, v) with a short pin standing at every vertex. */
    public static FxLib.Parts hexagon(Point center, Point u, Point v, Point pinDir, double radius, double rot, double pin, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v, pinDir) || radius <= 0) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        Point[] corners = new Point[6];
        for (int i = 0; i < 6; i++) {
            double a = rot + i * Math.PI / 3;
            corners[i] = center.plus(u.scale(Math.cos(a) * radius)).plus(v.scale(Math.sin(a) * radius));
        }
        for (int i = 0; i < 6; i++) {
            glowLine(strokes, corners[i], corners[(i + 1) % 6], .09 * tier.widthScale, rgb, alpha * .5 * tier.glow, .014, alpha);
            if (pin > 0) glowLine(strokes, corners[i], corners[i].plus(pinDir.scale(pin)), .05 * tier.widthScale, rgb, alpha * .5 * tier.glow, .012, alpha);
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /** Jagged cracks radiating over a plane (u, v): the ground splitting where force was absorbed. */
    public static FxLib.Parts cracks(Point center, Point u, Point v, int count, double length, double growth, long seed, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v) || count <= 0 || length <= 0 || !(growth >= 0)) return FxLib.Parts.EMPTY;
        growth = Math.min(1, growth);
        List<Stroke> strokes = new ArrayList<>();
        FxLib.Rng rng = new FxLib.Rng(seed);
        for (int c = 0; c < count; c++) {
            double a = c * Math.PI * 2 / count + rng.range(-.25, .25), reach = length * growth * rng.range(.7, 1);
            Point prev = center;
            int steps = 7;
            for (int s = 1; s <= steps; s++) {
                double d = reach * s / steps, wob = rng.range(-.22, .22) * (1 - s / (double) steps * .4);
                Point next = center.plus(u.scale(Math.cos(a + wob) * d)).plus(v.scale(Math.sin(a + wob) * d));
                glowLine(strokes, prev, next, .11 * tier.widthScale * (1 - s / (double) steps * .6), rgb, alpha * .55 * tier.glow, .016, alpha * (1 - s / (double) steps * .5));
                prev = next;
            }
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /** A helix of {@code turns} turns around {@code axis} from {@code base}, optionally travelling (phase) and tapering. */
    public static FxLib.Parts helix(Point base, Point axis, double height, double radius, double turns, double phase, boolean taper, int rgb, double alpha, VisualTier tier) {
        Point[] f = frame(axis);
        if (tier == null || !tier.enhanced() || !ok(base) || f == null || height <= 0 || radius <= 0 || turns <= 0) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        Point prev = null;
        int steps = (int) Math.min(64, 12 + turns * 10);
        for (int i = 0; i <= steps; i++) {
            double u = i / (double) steps, a = u * turns * Math.PI * 2 + phase, r = radius * (taper ? 1 - u * .7 : 1);
            Point p = base.plus(f[0].scale(u * height)).plus(f[1].scale(Math.cos(a) * r)).plus(f[2].scale(Math.sin(a) * r));
            if (prev != null) glowLine(strokes, prev, p, .07 * tier.widthScale, rgb, alpha * .5 * tier.glow * Math.sin(Math.PI * Math.min(1, u + .05)), .012, alpha * Math.sin(Math.PI * Math.min(1, u + .05)));
            prev = p;
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /** Satellites orbiting {@code center} in the plane (u, v), each with a short trailing tail along its path. */
    public static FxLib.Parts orbit(Point center, Point u, Point v, double radius, int count, double angle, double size, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v) || radius <= 0 || count <= 0) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        List<Billboard> sprites = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = angle + i * Math.PI * 2 / count;
            Point at = center.plus(u.scale(Math.cos(a) * radius)).plus(v.scale(Math.sin(a) * radius));
            Point tail = center.plus(u.scale(Math.cos(a - .35) * radius)).plus(v.scale(Math.sin(a - .35) * radius));
            glowLine(strokes, tail, at, .06 * tier.widthScale, rgb, alpha * .5 * tier.glow, .012, alpha * .8);
            sprites.add(new Billboard(at, size * tier.widthScale, rgb, FxLib.alpha(220 * tier.glow, alpha / 255.0), RailThunderArrayGrandGeometry.RADIAL, 0));
            sprites.add(new Billboard(at, size * .45 * tier.widthScale, 0xFFFFFF, FxLib.alpha(240 * tier.glow, alpha / 255.0), RailThunderArrayGrandGeometry.RADIAL, 0));
        }
        return new FxLib.Parts(List.copyOf(strokes), List.copyOf(sprites), List.of());
    }

    /** The outline of a rectangle (half sizes hw, hh) in the plane (u, v) with small inward arrowheads on each edge. */
    public static FxLib.Parts panel(Point center, Point u, Point v, double hw, double hh, double inward, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v) || hw <= 0 || hh <= 0) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        Point[] c = {center.plus(u.scale(-hw)).plus(v.scale(-hh)), center.plus(u.scale(hw)).plus(v.scale(-hh)), center.plus(u.scale(hw)).plus(v.scale(hh)), center.plus(u.scale(-hw)).plus(v.scale(hh))};
        for (int i = 0; i < 4; i++) {
            Point a = c[i], b = c[(i + 1) % 4], mid = a.lerp(b, .5);
            glowLine(strokes, a, b, .08 * tier.widthScale, rgb, alpha * .55 * tier.glow, .014, alpha);
            Point in = unit(center.minus(mid));
            if (in != null) {
                Point along = unit(b.minus(a));
                if (along != null) for (int s : new int[] {-1, 1}) glowLine(strokes, mid.plus(in.scale(inward)), mid.minus(in.scale(.05)).plus(along.scale(s * inward * .5)), .05 * tier.widthScale, rgb, alpha * .5 * tier.glow, .01, alpha * .8);
            }
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /** Orthogonal circuit traces from {@code from} to {@code to}: axis-aligned elbows like a printed board, with node dots. */
    public static FxLib.Parts circuit(Point from, Point to, int elbows, long seed, double pulse, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(from, to) || elbows < 1 || elbows > 8 || from.minus(to).length() < 1e-6) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        List<Billboard> sprites = new ArrayList<>();
        FxLib.Rng rng = new FxLib.Rng(seed);
        Point cur = from;
        Point d = to.minus(from);
        double[] rem = {d.x(), d.y(), d.z()};
        int[] order = {0, 1, 2};
        for (int i = 2; i > 0; i--) { int j = (int) (rng.next() * (i + 1)); int t = order[i]; order[i] = order[j]; order[j] = t; }
        int segments = Math.min(8, elbows + 2), axis = 0;
        for (int s = 0; s < segments; s++) {
            int ax = order[axis++ % 3];
            double share = s == segments - 1 ? 1 : rng.range(.3, .7);
            double[] step = {0, 0, 0};
            step[ax] = rem[ax] * share; rem[ax] -= step[ax];
            Point next = cur.plus(new Point(step[0], step[1], step[2]));
            glowLine(strokes, cur, next, .09 * tier.widthScale, rgb, alpha * .5 * tier.glow, .014, alpha);
            sprites.add(new Billboard(next, .16 * tier.widthScale, rgb, FxLib.alpha(210 * tier.glow, .55 + .45 * Math.sin(pulse + s)), RailThunderArrayGrandGeometry.RADIAL, 0));
            cur = next;
        }
        glowLine(strokes, cur, to, .09 * tier.widthScale, rgb, alpha * .5 * tier.glow, .014, alpha);
        return new FxLib.Parts(List.copyOf(strokes), List.copyOf(sprites), List.of());
    }

    /** A forking tree: the trunk from {@code from} to {@code to} with real branches that themselves fork (lightning that actually forks). */
    public static FxLib.Parts forkTree(Point from, Point to, long seed, double age, int depth, double amplitude, int rgb, double alpha, VisualTier tier) {
        Point[] f = from == null || to == null ? null : frame(to.minus(from));
        if (tier == null || !tier.enhanced() || f == null || depth < 0 || depth > 3 || !Double.isFinite(age) || age < 0) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        FxLib.Rng rng = new FxLib.Rng(seed * 17 + (long) Math.floor(age * 2));
        branch(strokes, rng, from, to, f, depth, amplitude, rgb, alpha, tier, 1);
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }
    private static void branch(List<Stroke> out, FxLib.Rng rng, Point from, Point to, Point[] f, int depth, double amp, int rgb, double alpha, VisualTier tier, double scale) {
        double length = to.minus(from).length();
        if (length < .2) return;
        int steps = Math.max(4, Math.min(10, (int) (length * 2)));
        Point prev = from;
        List<Point> joints = new ArrayList<>();
        for (int k = 1; k <= steps; k++) {
            double u = k / (double) steps, a = Math.min(amp, length * .12) * Math.sin(Math.PI * u);
            Point next = k == steps ? to : from.lerp(to, u).plus(f[1].scale(rng.range(-1, 1) * a)).plus(f[2].scale(rng.range(-1, 1) * a));
            glowLine(out, prev, next, .1 * tier.widthScale * scale, rgb, alpha * .5 * tier.glow, .016 * scale, alpha);
            joints.add(next);
            prev = next;
        }
        if (depth <= 0) return;
        int forks = depth >= 2 ? 3 : 2;
        for (int b = 0; b < forks; b++) {
            Point root = joints.get(Math.min(joints.size() - 2, 1 + (int) (rng.next() * (joints.size() - 2))));
            Point dir = from.lerp(to, 1).minus(from).scale(1 / length).plus(f[1].scale(rng.range(-1, 1) * .9)).plus(f[2].scale(rng.range(-1, 1) * .9));
            Point d = unit(dir);
            if (d == null) continue;
            Point tip = root.plus(d.scale(length * rng.range(.28, .5)));
            Point[] sub = frame(tip.minus(root));
            if (sub != null) branch(out, rng, root, tip, sub, depth - 1, amp * .7, rgb, alpha * .85, tier, scale * .7);
        }
    }

    /** A lattice net: {@code cells x cells} grid in the plane (u, v) with bright nodes, like a thrown net of current. */
    public static FxLib.Parts lattice(Point center, Point u, Point v, double half, int cells, double pulse, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v) || half <= 0 || cells < 1 || cells > 10) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        List<Billboard> sprites = new ArrayList<>();
        for (int i = 0; i <= cells; i++) {
            double t = -half + 2 * half * i / cells;
            glowLine(strokes, center.plus(u.scale(t)).plus(v.scale(-half)), center.plus(u.scale(t)).plus(v.scale(half)), .07 * tier.widthScale, rgb, alpha * .45 * tier.glow, .012, alpha * .8);
            glowLine(strokes, center.plus(v.scale(t)).plus(u.scale(-half)), center.plus(v.scale(t)).plus(u.scale(half)), .07 * tier.widthScale, rgb, alpha * .45 * tier.glow, .012, alpha * .8);
            for (int j = 0; j <= cells; j++) {
                double tj = -half + 2 * half * j / cells;
                sprites.add(new Billboard(center.plus(u.scale(t)).plus(v.scale(tj)), .13 * tier.widthScale, rgb, FxLib.alpha(220 * tier.glow, .5 + .5 * Math.sin(pulse + i * .9 + j * 1.3)), RailThunderArrayGrandGeometry.RADIAL, 0));
            }
        }
        return new FxLib.Parts(List.copyOf(strokes), List.copyOf(sprites), List.of());
    }

    /** A gauge ring of {@code segments} arcs in the plane (u, v); the first {@code filled} are lit, the rest dim. */
    public static FxLib.Parts gauge(Point center, Point u, Point v, double radius, int segments, int filled, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v) || radius <= 0 || segments < 2 || segments > 32) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        double gap = .12, span = Math.PI * 2 / segments;
        for (int s = 0; s < segments; s++) {
            boolean lit = s < filled;
            Point prev = null;
            for (int i = 0; i <= 4; i++) {
                double a = s * span + gap / 2 + (span - gap) * i / 4;
                Point p = center.plus(u.scale(Math.cos(a) * radius)).plus(v.scale(Math.sin(a) * radius));
                if (prev != null) glowLine(strokes, prev, p, .1 * tier.widthScale, rgb, (lit ? alpha : alpha * .25) * .55 * tier.glow, lit ? .016 : 0, alpha);
                prev = p;
            }
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /** A ground magic circle: two counter-rotating rings, a hexagram and rune ticks on the plane (u, v) around {@code center}. */
    public static FxLib.Parts sigil(Point center, Point u, Point v, double radius, double rot, int points, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v) || radius <= 0 || points < 3 || points > 12 || alpha < 1) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        List<Decal> decals = new ArrayList<>();
        decals.add(new Decal(center, u, v, radius, rgb, FxLib.alpha(alpha, 1), RailThunderArrayGrandGeometry.RING));
        decals.add(new Decal(center, u, v, radius * .62, rgb, FxLib.alpha(alpha * .8, 1), RailThunderArrayGrandGeometry.RING));
        for (int star = 0; star < 2; star++) {
            for (int i = 0; i < points; i++) {
                double a = (star == 0 ? rot : -rot * .6) + i * Math.PI * 2 / points, b = (star == 0 ? rot : -rot * .6) + ((i + 2) % points) * Math.PI * 2 / points;
                double r = radius * (star == 0 ? .96 : .6);
                Point from = center.plus(u.scale(Math.cos(a) * r)).plus(v.scale(Math.sin(a) * r)), to = center.plus(u.scale(Math.cos(b) * r)).plus(v.scale(Math.sin(b) * r));
                glowLine(strokes, from, to, .09 * tier.widthScale, rgb, alpha * .5 * tier.glow, .014, alpha * .9);
            }
        }
        for (int i = 0; i < points * 3; i++) {
            double a = -rot * .3 + i * Math.PI * 2 / (points * 3), r0 = radius * 1.02, r1 = radius * (i % 3 == 0 ? 1.14 : 1.08);
            glowLine(strokes, center.plus(u.scale(Math.cos(a) * r0)).plus(v.scale(Math.sin(a) * r0)), center.plus(u.scale(Math.cos(a) * r1)).plus(v.scale(Math.sin(a) * r1)), .05 * tier.widthScale, rgb, alpha * .4 * tier.glow, .01, alpha * .8);
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.copyOf(decals));
    }

    /** A vertical column of light: a wide soft shaft with a hot core, a flare at the base and a flare at the top. */
    public static FxLib.Parts pillar(Point base, double height, double radius, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(base) || height <= 0 || radius <= 0 || alpha < 1) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        List<Billboard> sprites = new ArrayList<>();
        Point top = base.plus(new Point(0, height, 0));
        strokes.add(new Stroke(new RailThunderArrayGeometry.Segment(base, top), Math.min(radius, RailThunderArrayGrandGeometry.maxHalfWidth(tier)), rgb, FxLib.alpha(alpha * .55 * tier.glow, 1), RailThunderArrayGrandGeometry.ARC_GLOW));
        strokes.add(new Stroke(new RailThunderArrayGeometry.Segment(base, top), Math.min(radius * .3, RailThunderArrayGrandGeometry.maxHalfWidth(tier)), 0xFFFFFF, FxLib.alpha(alpha, 1), RailThunderArrayGrandGeometry.WHITE));
        sprites.add(new Billboard(base, radius * 3.2, rgb, FxLib.alpha(alpha * .9 * tier.glow, 1), RailThunderArrayGrandGeometry.RADIAL, 0));
        sprites.add(new Billboard(base.plus(new Point(0, .1, 0)), radius * 1.6, 0xFFFFFF, FxLib.alpha(alpha, 1), RailThunderArrayGrandGeometry.STAR, 0));
        sprites.add(new Billboard(top, radius * 2.2, rgb, FxLib.alpha(alpha * .7 * tier.glow, 1), RailThunderArrayGrandGeometry.RADIAL, 0));
        return new FxLib.Parts(List.copyOf(strokes), List.copyOf(sprites), List.of());
    }

    /** A targeting reticle on the plane (u, v): four corner brackets, crosshair ticks and a thin tick ring. */
    public static FxLib.Parts reticle(Point center, Point u, Point v, double radius, double rot, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v) || radius <= 0 || alpha < 1) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        double leg = radius * .38;
        for (int k = 0; k < 4; k++) {
            double a = rot + k * Math.PI / 2 + Math.PI / 4;
            Point corner = center.plus(u.scale(Math.cos(a) * radius * 1.41)).plus(v.scale(Math.sin(a) * radius * 1.41));
            Point toward1 = center.plus(u.scale(Math.cos(a + Math.PI / 4) * radius * 1.41)).plus(v.scale(Math.sin(a + Math.PI / 4) * radius * 1.41));
            Point toward2 = center.plus(u.scale(Math.cos(a - Math.PI / 4) * radius * 1.41)).plus(v.scale(Math.sin(a - Math.PI / 4) * radius * 1.41));
            Point d1 = unit(toward1.minus(corner)), d2 = unit(toward2.minus(corner));
            if (d1 == null || d2 == null) continue;
            glowLine(strokes, corner, corner.plus(d1.scale(leg)), .07 * tier.widthScale, rgb, alpha * .5 * tier.glow, .014, alpha);
            glowLine(strokes, corner, corner.plus(d2.scale(leg)), .07 * tier.widthScale, rgb, alpha * .5 * tier.glow, .014, alpha);
            double ta = rot + k * Math.PI / 2;
            Point inner = center.plus(u.scale(Math.cos(ta) * radius * .45)).plus(v.scale(Math.sin(ta) * radius * .45)), outer = center.plus(u.scale(Math.cos(ta) * radius * .75)).plus(v.scale(Math.sin(ta) * radius * .75));
            glowLine(strokes, inner, outer, .04 * tier.widthScale, rgb, alpha * .35 * tier.glow, .01, alpha * .8);
        }
        List<Decal> decals = List.of(new Decal(center, u, v, radius * .55, rgb, FxLib.alpha(alpha * .7, 1), RailThunderArrayGrandGeometry.RING));
        return new FxLib.Parts(List.copyOf(strokes), List.of(), decals);
    }

    /** A patch of hexagonal cells on the plane (u, v); a bright wave travels outward from the centre cell. */
    public static FxLib.Parts hexGrid(Point center, Point u, Point v, double cell, int rings, double pulse, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center, u, v) || cell <= 0 || rings < 0 || rings > 3 || alpha < 1) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        double sqrt3 = Math.sqrt(3);
        for (int q = -rings; q <= rings; q++) for (int r = Math.max(-rings, -q - rings); r <= Math.min(rings, -q + rings); r++) {
            int dist = (Math.abs(q) + Math.abs(r) + Math.abs(q + r)) / 2;
            double wave = Math.max(0, 1 - Math.abs(((pulse % 1 + 1) % 1) * (rings + 1) - dist));
            double x = cell * 1.5 * q, y = cell * sqrt3 * (r + q / 2.0), a = alpha * (.25 + .75 * wave);
            Point c = center.plus(u.scale(x)).plus(v.scale(y));
            for (int k = 0; k < 6; k++) {
                double a0 = Math.PI / 3 * k, a1 = Math.PI / 3 * (k + 1), s = cell * .94;
                Point p0 = c.plus(u.scale(Math.cos(a0) * s)).plus(v.scale(Math.sin(a0) * s)), p1 = c.plus(u.scale(Math.cos(a1) * s)).plus(v.scale(Math.sin(a1) * s));
                glowLine(strokes, p0, p1, .05 * tier.widthScale, rgb, a * .5 * tier.glow, wave > .3 ? .012 : 0, a);
            }
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }

    /** Short bright data packets sliding along a line from {@code from} to {@code to}. */
    public static FxLib.Parts packets(Point from, Point to, int count, double time, double speed, double length, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(from, to) || count < 1 || count > 24 || length <= 0 || !Double.isFinite(time) || alpha < 1) return FxLib.Parts.EMPTY;
        Point d = unit(to.minus(from));
        double total = to.minus(from).length();
        if (d == null) return FxLib.Parts.EMPTY;
        List<Stroke> strokes = new ArrayList<>();
        List<Billboard> sprites = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double t = ((time * speed + i / (double) count) % 1 + 1) % 1, head = total * t, tail = Math.max(0, head - length);
            Point a = from.plus(d.scale(tail)), b = from.plus(d.scale(head));
            glowLine(strokes, a, b, .1 * tier.widthScale, rgb, alpha * .5 * tier.glow, .016, alpha);
            sprites.add(new Billboard(b, .16 * tier.widthScale, 0xFFFFFF, FxLib.alpha(235 * tier.glow, Math.sin(Math.PI * t)), RailThunderArrayGrandGeometry.RADIAL, 0));
        }
        return new FxLib.Parts(List.copyOf(strokes), List.copyOf(sprites), List.of());
    }

    /** Thin scan rings climbing a vertical column: {@code count} rings at staggered heights moving up and fading near the top. */
    public static FxLib.Parts scanRings(Point base, double height, double radius, int count, double time, double speed, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(base) || height <= 0 || radius <= 0 || count < 1 || count > 16 || !Double.isFinite(time) || alpha < 1) return FxLib.Parts.EMPTY;
        List<Decal> decals = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double t = ((time * speed + i / (double) count) % 1 + 1) % 1;
            decals.add(new Decal(base.plus(new Point(0, height * t, 0)), new Point(1, 0, 0), new Point(0, 0, 1), radius * (1 - .4 * t), rgb, FxLib.alpha(alpha, 1 - t * t), RailThunderArrayGrandGeometry.RING));
        }
        return new FxLib.Parts(List.of(), List.of(), List.copyOf(decals));
    }

    /** A wireframe cube of half size {@code half} turned {@code yaw} radians about the vertical axis. */
    public static FxLib.Parts cube(Point center, double half, double yaw, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center) || half <= 0 || !Double.isFinite(yaw) || alpha < 1) return FxLib.Parts.EMPTY;
        double c = Math.cos(yaw), s = Math.sin(yaw);
        Point[] v = new Point[8];
        for (int i = 0; i < 8; i++) {
            double x = (i & 1) == 0 ? -half : half, y = (i & 2) == 0 ? -half : half, z = (i & 4) == 0 ? -half : half;
            v[i] = center.plus(new Point(x * c - z * s, y, x * s + z * c));
        }
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        List<Stroke> strokes = new ArrayList<>();
        for (int[] e : edges) glowLine(strokes, v[e[0]], v[e[1]], .06 * tier.widthScale, rgb, alpha * .45 * tier.glow, .012, alpha);
        List<Billboard> sprites = new ArrayList<>();
        for (Point corner : v) sprites.add(new Billboard(corner, .09 * tier.widthScale, 0xFFFFFF, FxLib.alpha(alpha * tier.glow, 1), RailThunderArrayGrandGeometry.RADIAL, 0));
        return new FxLib.Parts(List.copyOf(strokes), List.copyOf(sprites), List.of());
    }

    /**
     * Square voxel sprites around {@code center}: {@code t}=0 packed in a body-sized column, {@code t}=1 scattered outward
     * to {@code spread} (departure); pass {@code 1 - t} for an arrival that assembles.
     */
    public static FxLib.Parts voxels(Point center, double height, double spread, int count, double t, long seed, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(center) || height <= 0 || spread < 0 || count < 1 || count > 64 || !Double.isFinite(t) || alpha < 1) return FxLib.Parts.EMPTY;
        double k = Math.max(0, Math.min(1, t));
        FxLib.Rng rng = new FxLib.Rng(seed);
        List<Billboard> sprites = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double a = rng.next() * Math.PI * 2, r0 = rng.next() * .35, y0 = rng.next() * height, out = spread * (.4 + .6 * rng.next());
            double r = r0 + out * k, y = y0 + (rng.next() - .3) * height * .6 * k;
            sprites.add(new Billboard(center.plus(new Point(Math.cos(a) * r, y, Math.sin(a) * r)), (.06 + .07 * rng.next()) * tier.widthScale * (1 - .5 * k), i % 4 == 0 ? 0xFFFFFF : rgb,
                    FxLib.alpha(alpha * tier.glow, 1 - k * k), RailThunderArrayGrandGeometry.WHITE, k * 3 + i));
        }
        return new FxLib.Parts(List.of(), List.copyOf(sprites), List.of());
    }

    /** A wireframe axis-aligned box from {@code min} to {@code max} with bright corner brackets. */
    public static FxLib.Parts box(Point min, Point max, int rgb, double alpha, VisualTier tier) {
        if (tier == null || !tier.enhanced() || !ok(min, max) || alpha < 1) return FxLib.Parts.EMPTY;
        Point lo = new Point(Math.min(min.x(), max.x()), Math.min(min.y(), max.y()), Math.min(min.z(), max.z()));
        Point hi = new Point(Math.max(min.x(), max.x()), Math.max(min.y(), max.y()), Math.max(min.z(), max.z()));
        if (hi.minus(lo).length() > 128) return FxLib.Parts.EMPTY;
        Point[] v = new Point[8];
        for (int i = 0; i < 8; i++) v[i] = new Point((i & 1) == 0 ? lo.x() : hi.x(), (i & 2) == 0 ? lo.y() : hi.y(), (i & 4) == 0 ? lo.z() : hi.z());
        int[][] edges = {{0, 1}, {2, 3}, {4, 5}, {6, 7}, {0, 2}, {1, 3}, {4, 6}, {5, 7}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        List<Stroke> strokes = new ArrayList<>();
        for (int[] e : edges) {
            Point a = v[e[0]], b = v[e[1]];
            glowLine(strokes, a, b, .05 * tier.widthScale, rgb, alpha * .35 * tier.glow, .008, alpha * .55);
            double len = b.minus(a).length(), leg = Math.min(len * .3, 1.2);
            Point d = unit(b.minus(a));
            if (d == null) continue;
            glowLine(strokes, a, a.plus(d.scale(leg)), .09 * tier.widthScale, rgb, alpha * .6 * tier.glow, .016, alpha);
            glowLine(strokes, b, b.minus(d.scale(leg)), .09 * tier.widthScale, rgb, alpha * .6 * tier.glow, .016, alpha);
        }
        return new FxLib.Parts(List.copyOf(strokes), List.of(), List.of());
    }
}
