package cn.academy.port.client;

import cn.academy.port.VisualTier;
import cn.academy.port.client.MotifRenderer.Plate;
import cn.academy.port.client.MotifRenderer.Prims;
import cn.academy.port.client.MotifRenderer.Ribbon;
import cn.academy.port.client.MotifRenderer.Sprite;
import cn.academy.port.client.MotifRenderer.Tex;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Drawing kit for the per-skill motifs ({@link SkillMotifs}): a collector, the shapes the motifs are built from, the animation curves, the
 * choreography beats (anticipation, release flash, follow-through) and the grand layer LV4/LV5 skills add on release. Every beat draws
 * with the motif materials ({@link MotifRenderer.Tex}). Pure functions of their arguments; drawing only.
 */
final class MotifKit {
    private MotifKit() {}

    /** Everything a motif needs about one cast. {@code since} is -1 while the key is held; {@code end} is the aim point, {@code ground} the ground under it. */
    record M(String skill, String category, int lv, Point feet, Point hand, Point f, Point s, Point u, Point end, Point ground, Point normal, double age, double since,
             double time, long seed, VisualTier tier) {
        boolean held() { return since < 0; }
        double fade(double life) { return since < 0 ? 1 : Math.max(0, 1 - since / life); }
        double charge(double ticks) { return Math.min(1, age / ticks); }
        /** A point relative to the hand: forward along the view, to the side, and up. */
        Point at(double fwd, double side, double up) { return hand.plus(f.scale(fwd)).plus(s.scale(side)).plus(u.scale(up)); }
        Point body(double side, double up) { return feet.plus(s.scale(side)).plus(new Point(0, up, 0)); }
        Point chest() { return feet.plus(new Point(0, 1.1, 0)); }
        int main() { return Palette.main(category); }
        int accent() { return Palette.accent(category); }
        int deep() { return Palette.deep(category); }
        double dist() { return Math.max(.5, end.minus(hand).length()); }
        Point dir() { Point d = end.minus(hand); double l = d.length(); return l < 1e-6 ? f : d.scale(1 / l); }
        /** The aim point, pulled in to at most {@code len} blocks from the hand. */
        Point reach(double len) { return hand.plus(dir().scale(Math.min(dist(), len))); }
        /** Level 1..5 as a 0..1 intensity. */
        double lvf() { return (Math.max(1, Math.min(5, lv)) - 1) / 4.0; }
        /** The same cast seen {@code delay} ticks later (for staggered parts); stays "held" until the delay has passed. */
        M later(double delay) { return new M(skill, category, lv, feet, hand, f, s, u, end, ground, normal, age, since < 0 ? -1 : since - delay, time, seed, tier); }
        /** Horizontal unit vector along the view. */
        Point flat() { Point d = new Point(f.x(), 0, f.z()); return d.length() < 1e-6 ? new Point(0, 0, 1) : norm(d); }
    }

    /** The category only chooses the colours; shapes and motion belong to the skill. */
    static final class Palette {
        static int main(String c) { return switch (c) { case "electromaster" -> 0x66D4FF; case "meltdowner" -> 0x70FF90; case "teleporter" -> 0xA06CFF; default -> 0xCFE0FF; }; }
        static int accent(String c) { return switch (c) { case "electromaster" -> 0xF2FCFF; case "meltdowner" -> 0xF0FFF2; case "teleporter" -> 0xEBDDFF; default -> 0xFFFFFF; }; }
        static int deep(String c) { return switch (c) { case "electromaster" -> 0x2A6CFF; case "meltdowner" -> 0x1FA85A; case "teleporter" -> 0x5A2BD0; default -> 0x7FA8FF; }; }
    }

    /** Collector of ribbons, sprites and plates, plus the older shared-kit parts for the effects that already exist there. */
    static final class B {
        final Prims p = new Prims();
        final FxLib.Builder legacy = new FxLib.Builder();
        final VisualTier tier;
        final double w;
        B(VisualTier tier) { this.tier = tier; this.w = tier.widthScale; }

        static int a(double v) { return Math.max(1, Math.min(255, (int) Math.round(v))); }
        private static boolean bad(Point... pts) { for (Point q : pts) if (q == null || !q.finite()) return true; return false; }

        void line(Point a, Point b, double width, int rgb, double alpha) {
            if (bad(a, b) || alpha < 1 || b.minus(a).length() < 1e-6) return;
            p.ribbons.add(new Ribbon(a, b, width * w, rgb, a(alpha), Tex.LINE, 0, 1));
        }
        /** An energy ribbon: tail at {@code a}, hot head at {@code b}. */
        void streak(Point a, Point b, double width, int rgb, double alpha) {
            if (bad(a, b) || alpha < 1 || b.minus(a).length() < 1e-6) return;
            p.ribbons.add(new Ribbon(a, b, width * w, rgb, a(alpha), Tex.STREAK, 0, 1));
        }
        /** Beam: wide soft glow, flowing energy body, hard white core. */
        void beam(Point a, Point b, double width, int rgb, double alpha) {
            if (bad(a, b) || alpha < 1 || b.minus(a).length() < 1e-6) return;
            p.ribbons.add(new Ribbon(a, b, width * 6 * w, rgb, a(alpha * .35), Tex.LINE, 0, 1));
            p.ribbons.add(new Ribbon(a, b, width * 2.6 * w, rgb, a(alpha * .85), Tex.STREAK, 0, 1));
            p.ribbons.add(new Ribbon(a, b, width * .9 * w, 0xFFFFFF, a(alpha), Tex.LINE, 0, 1));
        }
        /** One segment of a lightning bolt, textured with a ragged filament. */
        void lightning(Point a, Point b, double width, int rgb, double alpha, long seed) {
            if (bad(a, b) || alpha < 1) return;
            double len = b.minus(a).length();
            if (len < 1e-6) return;
            double u0 = Math.floorMod(seed, 50) / 100.0;
            p.ribbons.add(new Ribbon(a, b, width * 4.5 * w, rgb, a(alpha * .5), Tex.LINE, 0, 1));
            p.ribbons.add(new Ribbon(a, b, width * 3 * w, rgb, a(alpha), Tex.LIGHTNING, u0, u0 + Math.min(.5, len * .25)));
        }
        void poly(List<Point> pts, double width, int rgb, double alpha) { for (int i = 1; i < pts.size(); i++) line(pts.get(i - 1), pts.get(i), width, rgb, alpha); }
        /** Turbulent plasma ball with a white-hot core. */
        void orb(Point c, double r, int rgb, double alpha, long seed) {
            if (bad(c) || r <= 0 || alpha < 1) return;
            double roll = Math.floorMod(seed, 628) / 100.0;
            p.sprites.add(new Sprite(c, r * 2.0 * w, rgb, a(alpha * .4 * tier.glow), Tex.BLOOM, 0, 1));
            p.sprites.add(new Sprite(c, r * w, rgb, a(alpha), Tex.PLASMA, roll, 1));
            p.sprites.add(new Sprite(c, r * .5 * w, 0xFFFFFF, a(alpha), Tex.BLOOM, 0, 1));
        }
        void orb(Point c, double r, int rgb, double alpha) { orb(c, r, rgb, alpha, 0); }
        void mote(Point c, double r, int rgb, double alpha) {
            if (bad(c) || r <= 0 || alpha < 1) return;
            p.sprites.add(new Sprite(c, r * w, rgb, a(alpha * tier.glow), Tex.BLOOM, 0, 1));
        }
        void smoke(Point c, double r, int rgb, double alpha) {
            if (bad(c) || r <= 0 || alpha < 1) return;
            p.sprites.add(new Sprite(c, r, rgb, a(alpha), Tex.SMOKE, Math.floorMod((long) (c.x() * 7 + c.z() * 3), 628) / 100.0, 1));
        }
        /** Anamorphic lens flare, {@code half} blocks to each side. */
        void flare(Point c, double half, int rgb, double alpha) {
            if (bad(c) || half <= 0 || alpha < 1) return;
            p.sprites.add(new Sprite(c, half / 4 * w, rgb, a(alpha * tier.glow), Tex.FLARE, 0, 4));
            p.sprites.add(new Sprite(c, half / 12 * w, 0xFFFFFF, a(alpha), Tex.BLOOM, 0, 1));
        }
        /** A free-standing textured plate spanning c +- u +- v. */
        void plate(Point c, Point u, Point v, int rgb, double alpha, Tex tex) {
            if (bad(c, u, v) || alpha < 1) return;
            p.plates.add(new Plate(c, u, v, rgb, a(alpha), tex));
        }
        /** A plate drawn with alpha blending (glass, tears, scorch): keeps its colour on a bright background where additive would wash out. */
        void blend(Point c, Point u, Point v, int rgb, double alpha, Tex tex) {
            if (bad(c, u, v) || alpha < 1) return;
            p.plates.add(new Plate(c, u, v, rgb, a(alpha), tex, true));
        }
        /** A camera-facing textured sprite. */
        void sprite(Point c, double r, int rgb, double alpha, Tex tex, double roll, double stretch) {
            if (bad(c) || r <= 0 || alpha < 1) return;
            p.sprites.add(new Sprite(c, r, rgb, a(alpha), tex, roll, stretch));
        }
        /** Circle (or arc) as a chain of ribbons, in the plane spanned by unit vectors u and v. */
        void circle(Point c, Point u, Point v, double r, int segs, double a0, double arc, double width, int rgb, double alpha) {
            Point prev = null;
            for (int i = 0; i <= segs; i++) {
                double t = a0 + arc * i / segs;
                Point q = c.plus(u.scale(Math.cos(t) * r)).plus(v.scale(Math.sin(t) * r));
                if (prev != null) line(prev, q, width, rgb, alpha);
                prev = q;
            }
        }
        /** Ragged ring plate, radius {@code R}, in the plane of unit vectors u and v. */
        void ring(Point c, Point u, Point v, double R, int rgb, double alpha, double rot) {
            if (R <= 0) return;
            double h = R / .78, co = Math.cos(rot), si = Math.sin(rot);
            Point uu = u.scale(co).plus(v.scale(si)).scale(h), vv = v.scale(co).minus(u.scale(si)).scale(h);
            plate(c, uu, vv, rgb, alpha, Tex.RING);
        }
        void groundRing(Point c, double R, int rgb, double alpha, double rot) { ring(c.plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), R, rgb, alpha, rot); }
        void groundGlow(Point c, double r, int rgb, double alpha) {
            plate(c.plus(new Point(0, .03, 0)), new Point(r, 0, 0), new Point(0, 0, r), rgb, alpha * tier.glow, Tex.BLOOM);
        }
        void polygon(Point c, Point u, Point v, double r, int n, double rot, double width, int rgb, double alpha) { circle(c, u, v, r, n, rot, Math.PI * 2, width, rgb, alpha); }
        /** A hexagonal plate: the hex-cell material with a bright rim. */
        void hex(Point c, Point u, Point v, double r, double rot, int rgb, double alpha) {
            plate(c, u.scale(r), v.scale(r), rgb, alpha * .85, Tex.HEX);
            polygon(c, u, v, r * .92, 6, rot, .012, rgb, alpha);
        }
        void add(FxLib.Parts parts) { legacy.add(parts); }
        boolean isEmpty() { return p.isEmpty() && legacy.build().strokes().isEmpty() && legacy.build().sprites().isEmpty() && legacy.build().decals().isEmpty(); }
    }

    // ---- geometry helpers --------------------------------------------------------------------------------------------------

    static Point norm(Point p) { double l = p.length(); return l < 1e-9 ? new Point(0, 1, 0) : p.scale(1 / l); }

    /** Two unit vectors perpendicular to {@code axis}. */
    static Point[] perp(Point axis) {
        Point f = norm(axis), ref = Math.abs(f.y()) > .9 ? new Point(1, 0, 0) : new Point(0, 1, 0);
        Point u = norm(f.cross(ref)), v = f.cross(u);
        return new Point[] {u, norm(v)};
    }

    /** Jagged lightning polyline with a deterministic flicker. */
    static List<Point> jag(Point a, Point b, long seed, double time, int steps, double amp) {
        Random r = new Random(seed * 31 + (long) Math.floor(time * 1.5));
        Point[] pp = perp(b.minus(a));
        var out = new ArrayList<Point>();
        out.add(a);
        for (int k = 1; k < steps; k++) {
            double t = k / (double) steps, e = Math.sin(Math.PI * t);
            out.add(a.lerp(b, t).plus(pp[0].scale((r.nextDouble() - .5) * 2 * amp * e)).plus(pp[1].scale((r.nextDouble() - .5) * 2 * amp * e)));
        }
        out.add(b);
        return out;
    }

    /** Looping field lines between two points, bulging round the axis; beads run along them. */
    static void fieldLoops(B b, Point from, Point to, int n, double bulge, int rgb, double alpha, double time, double speed) {
        Point axis = to.minus(from);
        if (axis.length() < .3) return;
        Point[] pp = perp(axis);
        for (int i = 0; i < n; i++) {
            double th = i * Math.PI * 2 / n + time * .02;
            Point side = pp[0].scale(Math.cos(th)).plus(pp[1].scale(Math.sin(th)));
            Point ctrl = from.lerp(to, .5).plus(side.scale(bulge));
            Point prev = null;
            for (int k = 0; k <= 14; k++) {
                double t = k / 14.0;
                Point q = from.scale((1 - t) * (1 - t)).plus(ctrl.scale(2 * t * (1 - t))).plus(to.scale(t * t));
                if (prev != null) b.line(prev, q, .009, rgb, alpha);
                prev = q;
            }
            double t = ((time * speed + i / (double) n) % 1 + 1) % 1;
            Point bead = from.scale((1 - t) * (1 - t)).plus(ctrl.scale(2 * t * (1 - t))).plus(to.scale(t * t));
            b.mote(bead, .06, 0xFFFFFF, alpha);
        }
    }

    static void helix(B b, Point from, Point to, double r, double turns, double phase, int rgb, double alpha, double width) {
        Point axis = to.minus(from);
        if (axis.length() < .05) return;
        Point[] pp = perp(axis);
        Point prev = null;
        for (int i = 0; i <= 28; i++) {
            double t = i / 28.0, ang = phase + t * turns * Math.PI * 2;
            Point q = from.lerp(to, t).plus(pp[0].scale(Math.cos(ang) * r)).plus(pp[1].scale(Math.sin(ang) * r));
            if (prev != null) b.line(prev, q, width, rgb, alpha);
            prev = q;
        }
    }

    /** Exhaust plume trailing behind {@code origin} (against {@code dir}): a flowing ribbon with bloom beads along it. */
    static void plume(B b, Point origin, Point dir, double len, int rgb, double alpha, double time) {
        Point tail = origin.minus(dir.scale(len));
        b.streak(tail, origin, .12 + len * .03, rgb, alpha * .9);
        for (int i = 0; i < 7; i++) {
            double t = i / 6.0, wob = Math.sin(time * .9 + i * 1.7) * .04;
            b.mote(origin.minus(dir.scale(len * t)).plus(new Point(wob, wob * .5, -wob)), .24 - .16 * t, i < 2 ? 0xFFFFFF : rgb, alpha * (1 - t * .75));
        }
    }

    /** Rings sliding along {@code dir} out of {@code origin}, widening like a cone. */
    static void cone(B b, Point origin, Point dir, double len, double half, int n, double time, double speed, int rgb, double alpha) {
        Point[] pp = perp(dir);
        for (int i = 0; i < n; i++) {
            double t = ((time * speed + i / (double) n) % 1 + 1) % 1, d = len * t, r = Math.max(.05, d * Math.tan(half));
            b.ring(origin.plus(dir.scale(d)), pp[0], pp[1], r, rgb, alpha * (1 - t * .7) * Math.min(1, t * 6), i * 1.3);
        }
    }

    /** A crack network on the ground (material plate) plus a few crisp jagged lines, growing with {@code prog}. */
    static void cracks(B b, Point c, long seed, double radius, int branches, double prog, int rgb, double alpha) {
        double rot = Math.floorMod(seed, 628) / 100.0, co = Math.cos(rot), si = Math.sin(rot), r = radius * prog * 1.05;
        if (r < .05) return;
        b.blend(c.plus(new Point(0, .035, 0)), new Point(co * r, 0, si * r), new Point(-si * r, 0, co * r), 0x0A0E18, alpha * .75, Tex.CRACK);   // scorched fissure
        b.plate(c.plus(new Point(0, .04, 0)), new Point(co * r, 0, si * r), new Point(-si * r, 0, co * r), rgb, alpha * .9, Tex.CRACK);       // glowing core
        Random rnd = new Random(seed);
        for (int i = 0; i < branches; i++) {
            double a = i * Math.PI * 2 / branches + (rnd.nextDouble() - .5) * .5, len = radius * (.55 + rnd.nextDouble() * .45) * prog;
            Point prev = c.plus(new Point(0, .05, 0));
            double ang = a;
            for (int k = 1; k <= 5; k++) {
                ang += (rnd.nextDouble() - .5) * .55;
                Point q = prev.plus(new Point(Math.cos(ang), 0, Math.sin(ang)).scale(len / 5));
                b.line(prev, q, .014 * (1.2 - k * .15), 0xFFFFFF, alpha * (1 - k * .13));
                prev = q;
            }
        }
    }

    /** A textured disc spinning by {@code rot} in the plane of unit vectors u and v. */
    static void disc(B b, Point c, Point u, Point v, double r, double rot, Tex tex, int rgb, double alpha) {
        double co = Math.cos(rot), si = Math.sin(rot);
        b.plate(c, u.scale(co * r).plus(v.scale(si * r)), v.scale(co * r).minus(u.scale(si * r)), rgb, alpha, tex);
    }

    /** Doorway-shaped coordinate frame standing on the ground at {@code c}, facing {@code face}. */
    static void frame(B b, Point c, Point face, double halfWidth, double height, int rgb, double alpha) {
        Point side = norm(face.cross(new Point(0, 1, 0))).scale(halfWidth);
        Point a = c.minus(side), d = c.plus(side), up = new Point(0, height, 0);
        b.line(a, a.plus(up), .016, rgb, alpha); b.line(d, d.plus(up), .016, rgb, alpha);
        b.line(a.plus(up), d.plus(up), .016, rgb, alpha); b.line(a.plus(new Point(0, .04, 0)), d.plus(new Point(0, .04, 0)), .016, rgb, alpha);
        b.blend(c.plus(up.scale(.5)), side, up.scale(.5), rgb, alpha * .5, Tex.LATTICE);
    }

    /** A line with a chevron head at {@code to}. */
    static void arrow(B b, Point from, Point to, double head, int rgb, double alpha) {
        Point d = to.minus(from);
        double l = d.length();
        if (l < 1e-6) return;
        Point dir = d.scale(1 / l);
        Point[] pp = perp(dir);
        b.line(from, to.minus(dir.scale(head * .5)), .012, rgb, alpha);
        b.plate(to.minus(dir.scale(head * .5)), dir.scale(head * .6), pp[0].scale(head * .6), rgb, alpha, Tex.CHEVRON);
    }

    /** Wireframe dome of latitude rings and meridians; {@code arc} limits it to a sector around {@code face}. */
    static void dome(B b, Point c, Point face, double radius, int rings, int meridians, double arc, int rgb, double alpha) {
        Point fwd = new Point(face.x(), 0, face.z());
        fwd = fwd.length() < 1e-6 ? new Point(0, 0, 1) : norm(fwd);
        Point side = norm(fwd.cross(new Point(0, 1, 0)));
        for (int k = 1; k <= rings; k++) {
            double el = k * (Math.PI / 2) / (rings + .4), r = radius * Math.cos(el), y = radius * Math.sin(el);
            b.circle(c.plus(new Point(0, y, 0)), fwd, side, r, 28, -arc / 2, arc, .012, rgb, alpha * (1 - k * .08));
        }
        for (int mm = 0; mm <= meridians; mm++) {
            double az = -arc / 2 + arc * mm / Math.max(1, meridians);
            Point prev = null;
            for (int k = 0; k <= 8; k++) {
                double el = k * (Math.PI / 2) / 8;
                Point q = c.plus(fwd.scale(Math.cos(el) * radius * Math.cos(az))).plus(side.scale(Math.cos(el) * radius * Math.sin(az))).plus(new Point(0, Math.sin(el) * radius, 0));
                if (prev != null) b.line(prev, q, .01, rgb, alpha * .75);
                prev = q;
            }
        }
    }

    /** A star of {@code n} bidirectional light rays through {@code c}, each with its own angle and length (camera-facing). */
    static void burst(B b, Point c, int n, double half, long seed, double spin, int rgb, double alpha) {
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            double ang = i * Math.PI / n + r.nextDouble() * .3 + spin, len = half * (.55 + .45 * r.nextDouble());
            b.sprite(c, len / 5, rgb, alpha * (.6 + .4 * r.nextDouble()), Tex.FLARE, ang, 5);
        }
    }

    /** Speed lines streaming out of {@code origin} along {@code dir}, staggered in phase, fading in and out. */
    static void speedLines(B b, double time, Point origin, Point dir, double len, int n, double spread, double speed, long seed, int rgb, double alpha) {
        Point[] pp = perp(dir);
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2, rad = spread * Math.sqrt(r.nextDouble()), off = r.nextDouble();
            double ph = ((time * speed + off) % 1 + 1) % 1, env = Math.sin(Math.PI * ph);
            Point lateral = pp[0].scale(Math.cos(ang) * rad).plus(pp[1].scale(Math.sin(ang) * rad));
            Point head = origin.plus(dir.scale(len * ph)).plus(lateral.scale(.35 + .65 * ph));
            b.streak(head.minus(dir.scale(len * .22)), head, .022 + .014 * r.nextDouble(), rgb, alpha * env);
        }
    }

    /** Chevrons sliding from {@code from} to {@code to}, pointing along the way. */
    static void chevronStream(B b, Point from, Point to, int n, double size, double time, double speed, int rgb, double alpha) {
        Point d = to.minus(from);
        double l = d.length();
        if (l < .2) return;
        Point dir = d.scale(1 / l);
        Point[] pp = perp(dir);
        for (int i = 0; i < n; i++) {
            double t = ((time * speed + i / (double) n) % 1 + 1) % 1;
            b.plate(from.plus(d.scale(t)), dir.scale(size * 1.35), pp[0].scale(size * 1.35), rgb, alpha * Math.sin(Math.PI * t), Tex.CHEVRON);
        }
    }

    /** A flat pane c +- u +- v with a bright rim; the pane material is the given texture. */
    static void pane(B b, Point c, Point u, Point v, Tex tex, int rgb, double alpha, double rim) {
        b.blend(c, u, v, rgb, alpha * .8, tex);
        b.plate(c, u, v, rgb, alpha * .35, tex);
        if (rim <= 0) return;
        Point p1 = c.minus(u).minus(v), p2 = c.plus(u).minus(v), p3 = c.plus(u).plus(v), p4 = c.minus(u).plus(v);
        b.line(p1, p2, .012, rgb, rim); b.line(p2, p3, .012, rgb, rim); b.line(p3, p4, .012, rgb, rim); b.line(p4, p1, .012, rgb, rim);
    }

    /** A vertical light pillar standing on {@code base}. */
    static void pillar(B b, Point base, double height, double width, int rgb, double alpha) {
        b.beam(base, base.plus(new Point(0, height, 0)), width, rgb, alpha);
    }

    /** Motes drifting up from a disc of radius {@code R} around {@code c}, looping, with a brighter head. */
    static void risers(B b, Point c, double R, double height, int n, double time, long seed, int rgb, double alpha) {
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = R * Math.sqrt(r.nextDouble()), cycle = 40 + r.nextDouble() * 40;
            double ph = ((time / cycle + r.nextDouble()) % 1 + 1) % 1;
            Point q = c.plus(new Point(Math.cos(a) * d + Math.sin(time * .07 + i) * .08, ph * height, Math.sin(a) * d));
            b.mote(q, .04 + .04 * r.nextDouble(), i % 3 == 0 ? 0xFFFFFF : rgb, alpha * Math.pow(Math.sin(Math.PI * ph), .8));
        }
    }

    /** A vertical rift (tear in space) standing on {@code base} facing {@code face}, {@code h} tall and {@code w} wide; {@code open} 0..1. */
    static void rift(B b, Point base, Point face, double h, double w, double open, int rgb, double alpha) {
        if (open <= .02) return;
        Point side = norm(face.cross(new Point(0, 1, 0))).scale(w * .8 * open);
        Point up = new Point(0, h * .5 * Math.min(1, open * 1.3), 0);
        b.blend(base.plus(up), side, up, rgb, alpha * .9, Tex.RIFT);
        b.plate(base.plus(up), side.scale(1.25), up, rgb, alpha * .5, Tex.RIFT);
    }

    /** Shapes pairs of orbs that swap: two orbs exchanging places along arcs. */
    static void swapArcs(B b, Point p, Point q, double t, double lift, int rgb, double alpha, long seed) {
        Point mid = p.lerp(q, .5).plus(new Point(0, lift, 0));
        double e = easeInOut(t);
        Point a = p.scale((1 - e) * (1 - e)).plus(mid.scale(2 * e * (1 - e))).plus(q.scale(e * e));
        Point c = q.scale((1 - e) * (1 - e)).plus(mid.scale(2 * e * (1 - e))).plus(p.scale(e * e));
        b.orb(a, .16, rgb, alpha, seed); b.orb(c, .16, rgb, alpha, seed + 1);
        for (int k = 1; k <= 5; k++) {
            double tt = Math.max(0, e - k * .05);
            Point ta = p.scale((1 - tt) * (1 - tt)).plus(mid.scale(2 * tt * (1 - tt))).plus(q.scale(tt * tt));
            Point tc = q.scale((1 - tt) * (1 - tt)).plus(mid.scale(2 * tt * (1 - tt))).plus(p.scale(tt * tt));
            b.mote(ta, .12 - k * .018, rgb, alpha * (1 - k * .17)); b.mote(tc, .12 - k * .018, rgb, alpha * (1 - k * .17));
        }
    }

    /** Point at fraction {@code t} (0..1) of the total length of a polyline. */
    static Point along(List<Point> pts, double t) {
        double total = 0;
        for (int i = 1; i < pts.size(); i++) total += pts.get(i).minus(pts.get(i - 1)).length();
        double want = c01(t) * total, run = 0;
        for (int i = 1; i < pts.size(); i++) {
            double seg = pts.get(i).minus(pts.get(i - 1)).length();
            if (run + seg >= want && seg > 1e-9) return pts.get(i - 1).lerp(pts.get(i), (want - run) / seg);
            run += seg;
        }
        return pts.get(pts.size() - 1);
    }

    static Point rndUnit(Random r) {
        double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, q = Math.sqrt(1 - z * z);
        return new Point(q * Math.cos(a), z, q * Math.sin(a));
    }

    /** A small bright landing: plasma flash, a star of rays, and a shock ring on the surface whose normal is {@code normal}. */
    static void spot(B b, M m, Point at, Point normal, double size, int rgb) {
        double t = m.since();
        if (t < 0 || t > 18) return;
        double k = Math.max(0, 1 - t / 14.0), e = punch(t);
        b.orb(at, size * (.4 + .6 * Math.min(1, e)), rgb, 235 * Math.min(1, e + .2) * k, m.seed() + 3);
        burst(b, at, 5 + m.lv(), size * 3.4 * easeOut(t / 5.0), m.seed() + 5, t * .02, rgb, 210 * k);
        Point[] pp = perp(normal);
        shockRing(b, m, at.plus(normal.scale(.04)), pp[0], pp[1], size * 2.4, 0, 12, rgb);
    }

    /** {@link #spot} plus a physical spark shower, for the skills that hit hard. */
    static void impact(B b, M m, Point at, Point normal, double size, int rgb) {
        spot(b, m, at, normal, size, rgb);
        if (m.since() >= 0) b.add(FxLib.sparkShower(at, normal, m.seed() + 11, m.since(), 6 + 3 * m.lv(), 5, 1.15, 9, 16, 0xFFFFFF, rgb, m.tier()));
    }

    /** A camera-facing disc of grains (iron sand, dust motes) scattered along a segment with a taper profile. */
    static void grains(B b, Point from, Point to, int n, double radius, double taper, long seed, double time, int rgb, double alpha) {
        Point axis = to.minus(from);
        if (axis.length() < .05) return;
        Point[] pp = perp(axis);
        Random r = new Random(seed);
        for (int i = 0; i < n; i++) {
            double t = r.nextDouble(), a = r.nextDouble() * Math.PI * 2 + time * .05 * (r.nextBoolean() ? 1 : -1);
            double rad = radius * Math.pow(1 - t, taper) * (.3 + .7 * r.nextDouble());
            Point q = from.lerp(to, t).plus(pp[0].scale(Math.cos(a) * rad)).plus(pp[1].scale(Math.sin(a) * rad));
            b.sprite(q, .03 + .03 * r.nextDouble(), r.nextInt(4) == 0 ? 0xFFFFFF : rgb, alpha * (.5 + .5 * r.nextDouble()), Tex.SPARK, r.nextDouble() * 6.28, 1);
        }
    }

    /** Points on a hemisphere (Fibonacci spiral) of radius R around c, within {@code cone} radians of {@code axis}. */
    static List<Point> shell(Point c, Point axis, double R, int n, double cone) {
        var out = new ArrayList<Point>();
        Point[] pp = perp(axis);
        Point ax = norm(axis);
        for (int i = 0; i < n; i++) {
            double z = 1 - (1 - Math.cos(cone)) * (i + .5) / n, rr = Math.sqrt(1 - z * z), a = i * 2.399963;
            out.add(c.plus(ax.scale(z * R)).plus(pp[0].scale(Math.cos(a) * rr * R)).plus(pp[1].scale(Math.sin(a) * rr * R)));
        }
        return out;
    }

    // ---- animation curves ----------------------------------------------------------------------------------------------------

    static double c01(double x) { return Math.max(0, Math.min(1, x)); }
    /** Anticipation: slow start, accelerating. */
    static double easeIn(double x) { x = c01(x); return x * x * x; }
    /** Expansion: fast start, decelerating. */
    static double easeOut(double x) { x = c01(x); return 1 - Math.pow(1 - x, 3); }
    static double easeInOut(double x) { x = c01(x); return x * x * (3 - 2 * x); }
    /** Release punch: 0 while held, rises in about a tick, overshoots, then decays; about 1 at its peak. */
    static double punch(double since) { return since < 0 ? 0 : Math.min(1.25, (1 - Math.exp(-since * 1.7)) * Math.exp(-since / 5.0) * 1.55); }
    static double decay(double since, double tau) { return since < 0 ? 1 : Math.exp(-since / tau); }
    /** Irregular flicker (not a sine): hash noise stepped every other tick. */
    static double flicker(long seed, double time) {
        long k = (long) Math.floor(time * 1.5);
        long h = (seed * 0x9E3779B97F4A7C15L) ^ (k * 0xBF58476D1CE4E5B9L);
        h ^= h >>> 29; h *= 0x94D049BB133111EBL; h ^= h >>> 32;
        return .6 + .4 * ((h & 1023) / 1023.0);
    }

    // ---- choreography beats --------------------------------------------------------------------------------------------------

    /** Anticipation: motes spiral inward from {@code R} into {@code c}, brighter and tighter as the charge builds. */
    static void converge(B b, M m, Point c, double R, int n, double chargeTicks, int rgb) {
        double ch = m.held() ? easeIn(m.age() / chargeTicks) : 1, out = m.held() ? 1 : Math.max(0, 1 - m.since() / 3.0);
        if (out <= 0) return;
        for (int i = 0; i < n; i++) {
            double ph = ((m.time() * .045 + i * .6180339) % 1 + 1) % 1, a = i * 2.399 + m.time() * .2;
            double r = R * (1 - ph) * (1.25 - .75 * ch) + .03, env = Math.sin(Math.PI * ph) * ch * out;
            Point q = c.plus(m.s().scale(Math.cos(a) * r)).plus(m.u().scale(Math.sin(a) * r)).plus(m.f().scale(-.12 * ph));
            Point tail = c.plus(m.s().scale(Math.cos(a - .5) * r * 1.2)).plus(m.u().scale(Math.sin(a - .5) * r * 1.2));
            b.streak(tail, q, .016, rgb, 200 * env);
            b.mote(q, .02 + .035 * ch, i % 3 == 0 ? 0xFFFFFF : rgb, 230 * env);
        }
        b.orb(c, .05 + .16 * ch, rgb, 200 * ch * out, m.seed());
    }

    /** The release flash: a plasma orb that overshoots and a lens flare, both gone within a few ticks. */
    static void flash(B b, M m, Point c, double size, int rgb) {
        double e = punch(m.since());
        if (e <= .02) return;
        b.orb(c, size * (.35 + .9 * e), rgb, 255 * Math.min(1, e), m.seed());
        b.flare(c, size * 3.4 * e, rgb, 220 * Math.min(1, e));
    }

    /** Lightning with side forks, re-rolled every other tick, and a slower-fading afterimage of the same path. */
    static void bolt(B b, M m, Point a, Point e, double width, int forks, double life) { bolt(b, m, a, e, width, forks, life, m.seed()); }

    static void bolt(B b, M m, Point a, Point e, double width, int forks, double life, long seed) {
        double since = m.since();
        if (since < 0 || since > life * 2.2) return;
        double live = Math.max(0, 1 - since / life), ghost = Math.max(0, 1 - since / (life * 2.2));
        double amp = Math.min(.5, a.minus(e).length() * .07);
        var path = jag(a, e, seed, m.time(), 9, amp);
        for (int i = 1; i < path.size(); i++) b.lightning(path.get(i - 1), path.get(i), width, m.main(), 255 * live, seed + i);
        var still = jag(a, e, seed + 7, 0, 9, amp);
        for (int i = 1; i < still.size(); i++) b.line(still.get(i - 1), still.get(i), width * .8, m.main(), 80 * ghost * (1 - live * .6));
        for (int k = 0; k < forks; k++) {
            int at = 2 + k * 2 + (int) (seed % 2);
            if (at >= path.size() - 1) break;
            Point o = path.get(at);
            Point[] pp = perp(e.minus(a));
            Point tip = o.plus(pp[k % 2].scale((k % 2 == 0 ? 1 : -1) * (.5 + .25 * k))).plus(e.minus(o).scale(.25));
            var fk = jag(o, tip, seed + 40 + k, m.time(), 4, .12);
            for (int i = 1; i < fk.size(); i++) b.lightning(fk.get(i - 1), fk.get(i), width * .6, m.main(), 220 * live, seed + 90 + i);
        }
    }

    /**
     * A beam that grows from {@code a} to its head in a few ticks, with a white core, a flowing energy body, a travelling bright pulse
     * with a fading tail, and (from LV2) colour fringes. Gone with an exponential tail.
     */
    static void beamCore(B b, M m, Point a, Point e, double width, double life) {
        double since = m.since();
        if (since < 0) return;
        double grow = easeOut(since / 3.0), live = decay(since, life / 2.5);
        if (live < .02) return;
        Point head = a.lerp(e, grow);
        b.beam(a, head, width, m.main(), 255 * live);
        if (grow < 1) b.orb(head, width * 3.2, m.accent(), 240 * live, m.seed());   // the growing tip is a hot point, never a flat cut
        if (m.lv() >= 2) b.add(FxLib.dispersion(a, head, width * .8, 90 * live, m.tier()));
        double pulse = easeOut(since / 6.0);
        if (pulse < 1) comet(b, a.lerp(e, pulse), norm(e.minus(a)), .9 + .3 * m.lv(), 0xFFFFFF, 230 * (1 - pulse * .5));
        if (grow >= 1) b.orb(e, .12 + .04 * m.lv(), m.main(), 200 * live, m.seed());
    }

    /** A bright head with a flowing tail behind it. */
    static void comet(B b, Point head, Point dir, double len, int rgb, double alpha) {
        b.streak(head.minus(dir.scale(len)), head, .07, rgb, alpha);
        b.mote(head, .09, 0xFFFFFF, alpha);
    }

    /**
     * A shock ring that expands like a real one: fast, decelerating, uneven (the ring material is ragged), with a bright leading edge and a
     * dimmer wake. {@code delay} ticks after release it starts.
     */
    static void shockRing(B b, M m, Point c, Point u, Point v, double R, double delay, double life, int rgb) {
        double x = (m.since() - delay) / life;
        if (m.since() < 0 || x <= 0 || x >= 1) return;
        double r = R * easeOut(x), alpha = Math.pow(1 - x, 1.4);
        double rot = Math.floorMod(m.seed() + (long) (delay * 7), 628) / 100.0;
        b.ring(c, u, v, r, rgb, 230 * alpha, rot);
        b.ring(c, u, v, r * .96, 0xFFFFFF, 200 * alpha, rot + .7);
    }

    /** Soft dust or smoke: large dim sprites that rise and spread. Physical skills only. */
    static void dust(B b, M m, Point c, double R, int n, int rgb) {
        if (m.since() < 0) return;
        double t = m.since(), k = Math.max(0, 1 - t / 30.0);
        if (k <= 0) return;
        Random r = new Random(m.seed() + 5);
        for (int i = 0; i < n; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = R * Math.sqrt(r.nextDouble()) * easeOut(t / 14.0 + .1);
            Point q = c.plus(new Point(Math.cos(a) * d, .2 + t * .035 * (.5 + r.nextDouble()), Math.sin(a) * d));
            b.smoke(q, .35 + .3 * r.nextDouble() + t * .012, rgb, 70 * k);
        }
    }

    // ---- grand layer ---------------------------------------------------------------------------------------------------------

    /**
     * What LV4 and LV5 skills add around the point where they land. Each category has its own kind of impact on the surroundings, so a
     * grand skill of one category never looks like another's: electric (arc-crawled cracks, light column), molten (glowing crater, embers,
     * vortex), spatial (lattice ripple, tears, orthogonal shells) and force (dust, pressure funnel, debris, speed lines).
     * Anchored on the ground at {@code at}.
     * @param size characteristic radius of the skill's impact in blocks
     */
    static void grand(B b, M m, Point at, double size, int rgb) {
        if (m.lv() < 4 || m.since() < 0) return;
        switch (m.category()) {
            case "electromaster" -> grandElectric(b, m, at, size, rgb);
            case "meltdowner" -> grandMolten(b, m, at, size, rgb);
            case "teleporter" -> grandSpatial(b, m, at, size, rgb);
            default -> grandForce(b, m, at, size, rgb);
        }
    }

    private static final Point GX = new Point(1, 0, 0), GZ = new Point(0, 0, 1), GY = new Point(0, 1, 0);

    private static void grandElectric(B b, M m, Point at, double size, int rgb) {
        double t = m.since(), k = Math.max(0, 1 - t / 42.0), quick = Math.max(0, 1 - t / 14.0);
        if (k <= 0) return;
        boolean five = m.lv() >= 5;
        b.groundGlow(at, size * (1.3 + (five ? .6 : 0)), rgb, 110 * k);
        for (int i = 0; i < (five ? 3 : 2); i++) shockRing(b, m, at.plus(new Point(0, .05, 0)), GX, GZ, size * (2.2 + .6 * i), i * 3.5, 26 + i * 4, i == 0 ? 0xFFFFFF : rgb);
        cracks(b, at, m.seed() + 17, size * (1.5 + (five ? .7 : 0)), five ? 12 : 8, Math.min(1, t / 7.0), rgb, 220 * k);
        b.beam(at, at.plus(GY.scale(2.5 + size * 1.6)), .03, rgb, 230 * quick);
        b.add(FxLib.sparkShower(at.plus(new Point(0, .1, 0)), GY, m.seed() + 3, t, five ? 40 : 24, 6.5, 1.3, 9, 24, 0xFFFFFF, rgb, m.tier()));
        b.flare(at.plus(new Point(0, .6, 0)), 2.0 + size, rgb, 230 * quick);
        for (int i = 0; i < (five ? 5 : 3); i++) {   // arcs crawl out along the ground and re-roll
            double a = i * Math.PI * 2 / (five ? 5 : 3) + (m.seed() % 9) * .2, len = size * (1.4 + (i % 2) * .6);
            bolt(b, m.later(Math.min(t, 1)), at.plus(new Point(0, .15, 0)), at.plus(new Point(Math.cos(a) * len, .15, Math.sin(a) * len)), .018, 0, 8, m.seed() + 70 + i);
        }
        if (five) {
            for (int j = 1; j <= 4; j++) {
                double el = j * .33, grow = size * (1.3 + t * .05), rr = grow * Math.cos(el), y = grow * Math.sin(el);
                b.circle(at.plus(new Point(0, y, 0)), GX, GZ, rr, 36, 0, Math.PI * 2, .012, 0xFFFFFF, 140 * Math.max(0, 1 - t / 20.0));
            }
            b.add(FxLib.spokes(at.plus(new Point(0, 1.2, 0)), m.seed() + 9, t, 18, 16, 2.0 + size, rgb, 200, m.tier()));
        }
    }

    private static void grandMolten(B b, M m, Point at, double size, int rgb) {
        double t = m.since(), k = Math.max(0, 1 - t / 60.0), quick = Math.max(0, 1 - t / 18.0);
        if (k <= 0) return;
        boolean five = m.lv() >= 5;
        b.groundGlow(at, size * (1.8 + (five ? .7 : 0)), rgb, 170 * k);
        b.groundGlow(at, size * .9, 0xFFFFFF, 140 * quick);
        for (int i = 0; i < 2; i++) {   // scorch rings that spread slowly and stay lit, with no hard white leading edge
            double x = (t - i * 6) / 44.0;
            if (x > 0 && x < 1) b.ring(at.plus(new Point(0, .05, 0)), GX, GZ, size * (1.6 + i) * easeOut(x), rgb, 190 * (1 - x), i * 2.3);
        }
        cracks(b, at, m.seed() + 29, size * (1.2 + (five ? .6 : 0)), 5, Math.min(1, t / 12.0), rgb, 190 * k);
        b.sprite(at.plus(new Point(0, .15, 0)), size * 1.1, rgb, 190 * k, Tex.VORTEX, m.time() * .06, 1);   // the melt turns slowly in the crater
        b.add(FxLib.embers(at, size * 1.2, 4 + size, m.seed() + 31, m.time(), five ? 44 : 26, 0xF0FFF2, rgb, m.tier()));
        pillar(b, at, 2.0 + size, .05, rgb, 150 * quick);
        if (five) {
            for (int i = 0; i < 3; i++) b.ring(at.plus(new Point(0, 1.5 + i * 1.1, 0)), GX, GZ, size * (.8 + i * .35) * (1 + t * .01), rgb, 140 * k, m.time() * .03 * (i % 2 == 0 ? 1 : -1));
            b.flare(at.plus(new Point(0, 1.0, 0)), 2.6 + size, rgb, 220 * quick);
        }
    }

    private static void grandSpatial(B b, M m, Point at, double size, int rgb) {
        double t = m.since(), k = Math.max(0, 1 - t / 46.0), quick = Math.max(0, 1 - t / 16.0);
        if (k <= 0) return;
        boolean five = m.lv() >= 5;
        double R = size * (1.8 + (five ? .8 : 0)) * (.5 + .5 * easeOut(t / 10.0));
        b.blend(at.plus(new Point(0, .05, 0)), GX.scale(R), GZ.scale(R), rgb, 170 * k, Tex.LATTICE);
        b.groundRing(at, R, 0xFFFFFF, 180 * k, m.time() * .01);
        Point c = at.plus(new Point(0, 1.2, 0));
        Point[][] planes = {{GX, GZ}, {GX, GY}, {GZ, GY}};
        for (int i = 0; i < 3; i++) {   // shells in three orthogonal planes, out of phase
            double x = (t - i * 3.0) / 30.0;
            if (x > 0 && x < 1) b.ring(c, planes[i][0], planes[i][1], size * 2.4 * easeOut(x), rgb, 210 * (1 - x), i * 1.7);
        }
        double open = easeOut(t / 4.0) * (1 - easeIn((t - 14) / 12.0));
        rift(b, at, GX, 3.2 + size * .4, 1.2 + size * .2, open, rgb, 230);
        rift(b, at, GZ, 3.2 + size * .4, 1.2 + size * .2, open, rgb, 230);
        b.sprite(c, size * 1.3, rgb, 50 * k, Tex.SPARKLE, m.time() * .02, 1);
        b.flare(c, 1.6 + size * .6, rgb, 150 * quick);
        if (five) {
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + Math.PI / 4;
                frame(b, at.plus(new Point(Math.cos(a) * R * .85, 0, Math.sin(a) * R * .85)), new Point(-Math.cos(a), 0, -Math.sin(a)), .6, 2.3, rgb, 190 * k);
            }
            b.hex(at.plus(new Point(0, .08, 0)), GX, GZ, R * .7, m.time() * .01, rgb, 160 * k);
        }
    }

    private static void grandForce(B b, M m, Point at, double size, int rgb) {
        double t = m.since(), k = Math.max(0, 1 - t / 40.0), quick = Math.max(0, 1 - t / 12.0);
        if (k <= 0) return;
        boolean five = m.lv() >= 5;
        for (int i = 0; i < (five ? 3 : 2); i++) shockRing(b, m, at.plus(new Point(0, .06, 0)), GX, GZ, size * (2.6 + .8 * i), i * 2.0, 18 + i * 3, i == 0 ? 0xFFFFFF : rgb);
        cracks(b, at, m.seed() + 41, size * (1.8 + (five ? .8 : 0)), five ? 10 : 7, Math.min(1, t / 5.0), 0xFFFFFF, 210 * k);
        dust(b, m, at, size * 1.6, five ? 12 : 7, 0xC8D4E4);
        Random r = new Random(m.seed() + 43);
        for (int i = 0; i < (five ? 22 : 14); i++) {   // debris flung out along the ground, then falling
            double a = r.nextDouble() * Math.PI * 2, sp = (.8 + r.nextDouble() * 1.6) * size, x = easeOut(t / 16.0 - r.nextDouble() * .1);
            if (x <= 0) continue;
            double arc = Math.sin(Math.PI * Math.min(1, x)) * (.6 + r.nextDouble() * 1.4);
            b.sprite(at.plus(new Point(Math.cos(a) * sp * x, .1 + arc, Math.sin(a) * sp * x)), .06 + .06 * r.nextDouble(), 0xFFFFFF, 220 * k, Tex.SHARD, r.nextDouble() * 6 + t * .15, 1);
        }
        for (int i = 0; i < 14; i++) {   // speed lines race outward over the ground
            double a = i * Math.PI * 2 / 14 + r.nextDouble() * .2, x = easeOut(t / 10.0 - i * .01);
            if (x <= 0 || x >= 1) continue;
            Point dir = new Point(Math.cos(a), 0, Math.sin(a));
            b.streak(at.plus(dir.scale(size * 2.2 * x)).plus(new Point(0, .2, 0)), at.plus(dir.scale(size * 2.2 * x + 1.2 + size * .3)).plus(new Point(0, .2, 0)), .015, rgb, 200 * (1 - x));
        }
        b.groundGlow(at, size * 1.4, rgb, 90 * quick);
        if (five) {   // a pressure funnel: rings stack upward and widen like a tornado of force
            for (int i = 0; i < 6; i++) {
                double y = .4 + i * .7, rr = (.9 + i * .38) * size * .45, ph = ((t * .04 + i * .1) % 1);
                b.ring(at.plus(new Point(0, y, 0)), GX, GZ, rr, rgb, 170 * k, t * .1 * (i % 2 == 0 ? 1 : -1) + ph);
            }
            b.flare(at.plus(new Point(0, .5, 0)), 3.0 + size, rgb, 210 * quick);
        }
    }
}
