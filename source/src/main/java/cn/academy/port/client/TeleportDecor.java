package cn.academy.port.client;

import static cn.academy.port.client.FxMotifs.glowLine;
import static cn.academy.port.client.FxMotifs.pt;

import cn.academy.port.VisualTier;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Billboard;
import cn.academy.port.client.RailThunderArrayGrandGeometry.Stroke;
import java.util.ArrayList;
import java.util.List;

/**
 * Per-skill presentation of the teleporter skills in a technological language: space is computed, not conjured. A departure
 * breaks the body into voxels inside a collapsing wireframe cube; an arrival is a coordinate lock (hex grid, reticle, scan
 * column) with voxels assembling; paths are data streams with a dispersion tear. Every skill adds its own signature on top:
 * projection cube (311), afterimage slices (312), downward probe scan (313), crossing swap arcs (321), waypoint beacon (322),
 * tethered recovery anchor (323), portal pair (331), orthogonal exchange route (333), triangular pincer lattice (341), amber
 * extraction column (342), numbered relay chain (343) and a coordinate-axis grid (353). Snap phase 0 = aiming, 1 = success
 * (age counts up for the decoration tail), 2 = refusal or observer warning. Pure and deterministic.
 */
public final class TeleportDecor {
    public enum Skill { PROJECTION, SIDESTEP, PROBE, SUBSTITUTE, RETURN, RECOVERY, INTERCEPT, CORNER, PINCER, EMERGENCY, RELAY, COORDINATE }
    public static final int MAX_QUADS = 1100, TAIL_TICKS = 28;
    private static final int TEAL = 0x7FF5E8, LILAC = 0xC9A8FF, ICE = 0xE6FBFF, WARN = 0xFFB347, RED = 0xFF6A5A;
    private static final Point X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);

    private TeleportDecor() {}

    public static double power(int skillLevel, double casterLevel) { return VectorDecor.power(skillLevel, casterLevel); }

    public static FxLib.Parts fx(Skill skill, DecorSnap s, double time, double power, VisualTier tier) {
        if (skill == null || s == null || tier == null || tier.layers < 3 || !Double.isFinite(time) || !Double.isFinite(power)) return FxLib.Parts.EMPTY;
        double p = Math.max(.2, Math.min(1, power));
        FxLib.Builder out = new FxLib.Builder();
        switch (skill) {
            case PROJECTION -> projection(out, s, time, p, tier);
            case SIDESTEP -> sidestep(out, s, time, p, tier);
            case PROBE -> probe(out, s, time, p, tier);
            case SUBSTITUTE -> substitute(out, s, time, p, tier);
            case RETURN -> waypoint(out, s, time, p, tier, false);
            case RECOVERY -> recovery(out, s, time, p, tier);
            case INTERCEPT -> intercept(out, s, time, p, tier);
            case CORNER -> corner(out, s, time, p, tier);
            case PINCER -> pincer(out, s, time, p, tier);
            case EMERGENCY -> waypoint(out, s, time, p, tier, true);
            case RELAY -> relay(out, s, time, p, tier);
            case COORDINATE -> coordinate(out, s, time, p, tier);
        }
        return out.build();
    }

    private static double sz(VisualTier tier) { return 1 + .12 * (tier.widthScale - 1); }
    private static double t01(double x) { return x != x ? 0 : Math.max(0, Math.min(1, x)); }
    private static Point up(Point a, double dy) { return a.plus(new Point(0, dy, 0)); }
    private static double tail(DecorSnap s) { return t01(s.age() / (double) TAIL_TICKS); }
    private static FxLib.Parts glow(Point at, double radius, int rgb, double alpha, int texture, double roll, VisualTier tier) {
        return new FxLib.Parts(List.of(), List.of(new Billboard(at, radius, rgb, FxLib.alpha(alpha * tier.glow, 1), texture, roll)), List.of());
    }

    // Shared beats -------------------------------------------------------------------------------------------------------

    /** The body breaks into voxels inside a shrinking wireframe cube, with scan rings sweeping up and away. */
    private static void departure(FxLib.Builder out, Point feet, double t, double p, long seed, int rgb, VisualTier tier) {
        if (feet == null) return;
        double w = sz(tier), f = 1 - t;
        out.add(FxMotifs.cube(up(feet, 1), (.7 + .3 * p) * w * (1 - .8 * t), t * 2.5, rgb, 230 * f + 10, tier));
        out.add(FxMotifs.voxels(feet, 2, (2 + 2.5 * p) * w, 18 + (int) (22 * p), t, seed, rgb, 235, tier));
        out.add(FxMotifs.scanRings(feet, 2.4, (.8 + .3 * p) * w, 3, t * 4, 1, ICE, 220 * f + 10, tier));
        out.add(glow(up(feet, 1), (1.0 + .8 * p) * w * f + .1, rgb, 200 * f + 10, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
    }

    /** A coordinate lock: hex grid, closing reticle and a scan column, with the body's voxels assembling. */
    private static void arrival(FxLib.Builder out, Point feet, double t, double p, long seed, int rgb, VisualTier tier) {
        if (feet == null) return;
        double w = sz(tier), f = 1 - t;
        Point g = up(feet, .05);
        out.add(FxMotifs.hexGrid(g, X, Z, (.35 + .2 * p) * w, 2 + (p > .6 ? 1 : 0), t, rgb, 230 * f + 15, tier));
        out.add(FxMotifs.reticle(g, X, Z, (1.2 + .8 * p) * w * (1.6 - .6 * t), t * 1.5, ICE, 230 * f + 15, tier));
        out.add(FxMotifs.pillar(g, 6 + 8 * p, (.15 + .2 * p) * w * f + .02, rgb, 220 * f + 10, tier));
        out.add(FxMotifs.voxels(feet, 2, (2 + 2 * p) * w, 18 + (int) (22 * p), 1 - Math.min(1, t * 2.2), seed + 1, rgb, 235, tier));
        out.add(FxMotifs.ring(g, X, Z, (.6 + (3 + 3 * p) * t) * w, ICE, 220 * f));
        out.add(FxLib.lensStreak(up(feet, 1), (3 + 3 * p) * w, rgb, 170, f, tier));
    }

    /** A dashed data stream from {@code a} to {@code b} with a dispersion tear that fades. */
    private static void path(FxLib.Builder out, Point a, Point b, double time, double t, double p, int rgb, VisualTier tier) {
        if (!FxMotifs.ok(a, b) || a.minus(b).length() < 1e-3 || a.minus(b).length() > 256) return;
        double w = sz(tier), f = 1 - t;
        Point ua = up(a, 1), ub = up(b, 1);
        out.add(FxMotifs.packets(ua, ub, 3 + (int) Math.round(4 * p), time, .08, (.8 + .6 * p) * w, rgb, 230 * f + 20, tier));
        out.add(FxLib.dispersion(ua, ub, .05 * w, 140 * f + 10, tier));
        List<Stroke> line = new ArrayList<>();
        glowLine(line, ua, ub, .05 * w, rgb, 70 * f * tier.glow + 5, .008, 110 * f + 10);
        out.add(new FxLib.Parts(List.copyOf(line), List.of(), List.of()));
    }

    private static void refusal(FxLib.Builder out, Point at, double time, double p, VisualTier tier) {
        if (at == null) return;
        double w = sz(tier);
        Point g = up(at, .05);
        out.add(FxMotifs.reticle(g, X, Z, (1 + .5 * p) * w, time * .05, RED, 220, tier));
        List<Stroke> cross = new ArrayList<>();
        double r = .8 * w;
        glowLine(cross, g.plus(new Point(-r, 0, -r)), g.plus(new Point(r, 0, r)), .1 * w, RED, 120 * tier.glow, .016, 230);
        glowLine(cross, g.plus(new Point(-r, 0, r)), g.plus(new Point(r, 0, -r)), .1 * w, RED, 120 * tier.glow, .016, 230);
        out.add(new FxLib.Parts(List.copyOf(cross), List.of(), List.of()));
    }

    // Skills -------------------------------------------------------------------------------------------------------------

    // 311: displaced projection. Aiming shows a rotating projection cube at the arrival and a lock on the impact; success fires a data line.
    private static void projection(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point src = pt(s.point(0)), arr = pt(s.point(1)), hit = pt(s.point(2));
        double w = sz(tier);
        if (s.phase() == 0) {
            if (arr != null) out.add(FxMotifs.cube(up(arr, .6), (.45 + .2 * p) * w, time * .05, LILAC, 200, tier));
            if (hit != null) out.add(FxMotifs.reticle(hit, X, Z, (.7 + .4 * p) * w, time * .04, TEAL, 210, tier));
            if (src != null && arr != null) out.add(FxMotifs.packets(up(src, 1), up(arr, .6), 4, time, .05, .5 * w, LILAC, 180, tier));
            return;
        }
        double t = tail(s);
        Point target = hit != null ? hit : arr;
        if (src != null && target != null) path(out, src, target, time, t, p, LILAC, tier);
        if (target != null) { arrival(out, target, t, p, s.token(), LILAC, tier); out.add(FxMotifs.cube(up(target, .6), (.5 + .3 * p) * w * (1 + t), t * 3, ICE, 230 * (1 - t) + 10, tier)); }
    }

    // 312: phase sidestep. A row of fading afterimage slices fills the gap and chevrons point the way.
    private static void sidestep(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point a = pt(s.point(0)), b = pt(s.point(1));
        if (!FxMotifs.ok(a, b) || a.minus(b).length() < 1e-3 || a.minus(b).length() > 64) return;
        double w = sz(tier), t = tail(s), f = 1 - t;
        Point d = FxMotifs.unit(b.minus(a));
        Point face = d == null ? X : FxMotifs.unit(d.cross(Y));
        if (face == null) face = X;
        int slices = 3 + (int) Math.round(3 * p);
        for (int i = 0; i < slices; i++) {
            double k = i / (double) slices, fade = (1 - k) * f;
            out.add(FxMotifs.panel(up(a.lerp(b, k), 1), d == null ? X : d, Y, .38 * w, .95 * w, 0, i % 2 == 0 ? TEAL : LILAC, 210 * fade + 5, tier));
        }
        if (d != null) out.add(FxMotifs.chevrons(up(a, .2), d, b.minus(a).length(), 3 + (int) (3 * p), (.4 + .2 * p) * w, time * .2, ICE, 230 * f + 10, tier));
        departure(out, a, t, p, s.token(), TEAL, tier);
        out.add(FxMotifs.ring(up(b, .05), X, Z, (.6 + 2.2 * t) * w, TEAL, 220 * f));
        out.add(FxMotifs.voxels(b, 2, 1.5 * w, 14, 1 - Math.min(1, t * 2.5), s.token() + 3, TEAL, 220, tier));
    }

    // 313: safe landing probe. A probe scans straight down to the landing pad; green-cyan when safe, red with a cross when refused.
    private static void probe(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point a = pt(s.point(0)), b = pt(s.point(1));
        if (b == null) return;
        double w = sz(tier);
        if (s.phase() == 2) { refusal(out, b, time, p, tier); return; }
        boolean safe = s.number(0, 1) > 0;
        int rgb = safe ? TEAL : RED;
        if (s.phase() == 0) {
            Point top = a != null ? up(a, 1.2) : up(b, 6);
            double drop = Math.max(.5, top.y() - b.y());
            List<Stroke> beam = new ArrayList<>();
            glowLine(beam, top, up(b, .05), .1 * w, rgb, 110 * tier.glow, .014, 210);
            out.add(new FxLib.Parts(List.copyOf(beam), List.of(), List.of()));
            for (int k = 0; k < 4; k++) {
                double y = 1 - ((time * .04 + k / 4.0) % 1);
                out.add(FxMotifs.ring(up(b, .05 + drop * y), X, Z, (.5 + .4 * (1 - y)) * w, rgb, 200 * (1 - y * .6)));
            }
            out.add(FxMotifs.hexGrid(up(b, .05), X, Z, (.3 + .1 * p) * w, 2, time * .03, rgb, 200, tier));
            out.add(FxMotifs.reticle(up(b, .06), X, Z, (1 + .4 * p) * w, time * .03, ICE, 190, tier));
            if (!safe) refusal(out, b, time, p, tier);
            return;
        }
        double t = tail(s);
        arrival(out, b, t, p, s.token(), TEAL, tier);
        if (a != null) departure(out, a, t, p, s.token() + 9, TEAL, tier);
    }

    // 321: cross substitution. The two marked points are boxed and two arcs bowed in opposite directions carry packets both ways.
    private static void substitute(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point a = pt(s.point(0)), b = pt(s.point(1));
        double w = sz(tier), t = s.phase() == 1 ? tail(s) : 0, f = 1 - t;
        for (Point m : new Point[] {a, b}) if (m != null) {
            out.add(FxMotifs.cube(up(m, 1), (.55 + .25 * p) * w, (m == a ? 1 : -1) * time * .05, m == a ? TEAL : LILAC, 220 * f + 15, tier));
            out.add(FxMotifs.ring(up(m, .05), X, Z, (.9 + .4 * p) * w, m == a ? TEAL : LILAC, 180 * f + 15));
        }
        if (!FxMotifs.ok(a, b) || a.minus(b).length() < 1e-3 || a.minus(b).length() > 128) return;
        Point mid = up(a.lerp(b, .5), 1), d = FxMotifs.unit(b.minus(a)), side = d == null ? X : FxMotifs.unit(d.cross(Y));
        if (side == null) side = X;
        double bow = Math.min(4, a.minus(b).length() * .3);
        for (int dir = -1; dir <= 1; dir += 2) {
            Point ctrl = mid.plus(side.scale(bow * dir)).plus(new Point(0, .8, 0)), from = dir < 0 ? up(a, 1) : up(b, 1), to = dir < 0 ? up(b, 1) : up(a, 1);
            List<Stroke> curve = new ArrayList<>();
            Point prev = from;
            for (int k = 1; k <= 14; k++) {
                double u = k / 14.0, v = 1 - u;
                Point cur = from.scale(v * v).plus(ctrl.scale(2 * u * v)).plus(to.scale(u * u));
                glowLine(curve, prev, cur, .08 * w, dir < 0 ? TEAL : LILAC, 110 * tier.glow * f + 10, .012, 210 * f + 20);
                if (k % 4 == 0) out.add(FxMotifs.packets(prev, cur, 1, time + k, .12, .35 * w, ICE, 220 * f + 10, tier));
                prev = cur;
            }
            out.add(new FxLib.Parts(List.copyOf(curve), List.of(), List.of()));
        }
        if (s.phase() == 1) { arrival(out, a, t, p, s.token(), TEAL, tier); arrival(out, b, t, p, s.token() + 5, LILAC, tier); }
    }

    // 322 / 342: waypoint beacon (return mark) or amber extraction column (emergency return).
    private static void waypoint(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier, boolean emergency) {
        Point src = pt(s.point(0)), dst = pt(s.point(1));
        double w = sz(tier);
        int rgb = emergency ? WARN : TEAL;
        if (s.phase() == 2) { if (dst != null) out.add(FxMotifs.reticle(up(dst, .05), X, Z, (1.2 + .6 * p) * w, time * .05, WARN, 220, tier)); return; }
        if (s.phase() == 0) {
            if (dst != null) {
                Point g = up(dst, .05);
                out.add(FxMotifs.pillar(g, emergency ? 10 + 8 * p : 5 + 4 * p, (.08 + .08 * p) * w, rgb, 170, tier));
                double yaw = time * .04;
                Point u = new Point(Math.cos(yaw), 0, Math.sin(yaw));
                out.add(FxMotifs.reticle(up(dst, 1.2), u, Y, (.6 + .3 * p) * w, 0, ICE, 200, tier));
                List<Stroke> diamond = new ArrayList<>();
                Point top = up(dst, 2.6 + .2 * Math.sin(time * .1)), r = u.scale(.35 * w), mid = up(dst, 2.2 + .2 * Math.sin(time * .1)), bottom = up(dst, 1.8 + .2 * Math.sin(time * .1));
                glowLine(diamond, top, mid.plus(r), .06 * w, rgb, 120 * tier.glow, .012, 230); glowLine(diamond, mid.plus(r), bottom, .06 * w, rgb, 120 * tier.glow, .012, 230);
                glowLine(diamond, bottom, mid.minus(r), .06 * w, rgb, 120 * tier.glow, .012, 230); glowLine(diamond, mid.minus(r), top, .06 * w, rgb, 120 * tier.glow, .012, 230);
                out.add(new FxLib.Parts(List.copyOf(diamond), List.of(), List.of()));
                int segments = 12, filled = (int) Math.round(segments * t01(s.number(0, 60) / 200.0));
                out.add(FxMotifs.gauge(g, X, Z, (1.1 + .4 * p) * w, segments, Math.max(1, filled), rgb, 200, tier));
            }
            if (emergency && src != null) {
                double pulse = (time * .06) % 1;
                for (int k = 0; k < 2; k++) out.add(FxMotifs.ring(up(src, .05), X, Z, (1 + 3 * ((pulse + k * .5) % 1)) * w, WARN, 210 * (1 - (pulse + k * .5) % 1)));
            }
            return;
        }
        double t = tail(s), f = 1 - t;
        if (src != null) departure(out, src, t, p, s.token(), rgb, tier);
        if (dst != null) {
            arrival(out, dst, t, p, s.token() + 1, rgb, tier);
            if (emergency) {
                out.add(FxMotifs.pillar(up(dst, .05), 24 + 16 * p, (.4 + .4 * p) * w * f + .03, WARN, 230 * f + 10, tier));
                out.add(FxMotifs.scanRings(up(dst, .05), 24 + 16 * p, (1.6 + 1 * p) * w, 6, time, .05, WARN, 220 * f + 10, tier));
                out.add(FxMotifs.hexGrid(up(dst, .05), X, Z, (.8 + .4 * p) * w, 3, t, WARN, 220 * f + 10, tier));
                if (src != null) out.add(FxMotifs.pillar(up(src, .05), 12 + 8 * p, (.25 + .25 * p) * w * f + .02, WARN, 200 * f + 10, tier));
            }
        }
        if (src != null && dst != null) path(out, src, dst, time, t, p, rgb, tier);
    }

    // 323: recovery mark. Every handle is tethered back to the anchor by a data line; the anchor is a beacon with a countdown gauge.
    private static void recovery(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point src = pt(s.point(0)), anchor = pt(s.point(1));
        if (anchor == null) return;
        double w = sz(tier), t = s.phase() == 1 ? tail(s) : 0, f = 1 - t;
        Point g = up(anchor, .05);
        out.add(FxMotifs.hexagon(g, X, Z, Y, (.9 + .4 * p) * w, time * .03, .5, LILAC, 210 * f + 15, tier));
        out.add(FxMotifs.gauge(g, X, Z, (1.4 + .5 * p) * w, 16, Math.max(1, (int) Math.round(16 * t01(s.number(0, 100) / 200.0))), TEAL, 200 * f + 15, tier));
        out.add(FxMotifs.pillar(g, 4 + 4 * p, (.08 + .06 * p) * w, LILAC, 170 * f + 10, tier));
        for (int i = 2; i < s.points().size(); i++) {
            Point h = pt(s.point(i));
            if (h == null || h.minus(anchor).length() > 96) continue;
            out.add(FxMotifs.packets(up(h, 1), up(anchor, 1), 3, time, .06, .5 * w, TEAL, 210 * f + 15, tier));
            out.add(FxMotifs.cube(up(h, 1), .3 * w, time * .06 + i, TEAL, 200 * f + 15, tier));
        }
        if (s.phase() == 1) { arrival(out, anchor, t, p, s.token(), LILAC, tier); if (src != null) departure(out, src, t, p, s.token() + 2, LILAC, tier); }
    }

    // 331: short interception. Each segment is a portal pair (entry and exit rings standing on edge) joined by a data jump.
    private static void intercept(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point target = pt(s.point(0));
        double w = sz(tier), t = s.phase() == 1 ? tail(s) : 0, f = 1 - t;
        if (target != null) out.add(FxMotifs.reticle(target, X, Z, (.8 + .4 * p) * w, time * .05, ICE, 210 * f + 15, tier));
        for (int i = 1; i + 1 < s.points().size(); i += 2) {
            Point in = pt(s.point(i)), outP = pt(s.point(i + 1));
            if (!FxMotifs.ok(in, outP) || in.minus(outP).length() > 96) continue;
            Point d = FxMotifs.unit(outP.minus(in));
            Point[] fr = FxMotifs.frame(d == null ? Z : d);
            for (Point gate : new Point[] {in, outP}) {
                int rgb = gate == in ? LILAC : TEAL;
                out.add(FxMotifs.ring(gate, fr[1], fr[2], (.7 + .3 * p) * w * (1 + .15 * Math.sin(time * .4)), rgb, 220 * f + 20));
                out.add(FxMotifs.ring(gate, fr[1], fr[2], (.45 + .2 * p) * w, ICE, 200 * f + 20));
                out.add(FxMotifs.hexGrid(gate, fr[1], fr[2], .14 * w, 1, time * .05, rgb, 180 * f + 15, tier));
                out.add(glow(gate, (.5 + .3 * p) * w, rgb, 150 * f + 10, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
            }
            out.add(FxMotifs.packets(in, outP, 4, time, .12, .5 * w, ICE, 230 * f + 15, tier));
            out.add(FxLib.dispersion(in, outP, .04 * w, 120 * f + 10, tier));
        }
    }

    // 333: corner exchange. Two marked corners are held in rotating cubes and joined by an orthogonal route with packets.
    private static void corner(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point src = pt(s.point(0)), dst = pt(s.point(1)), a = pt(s.point(2)), b = pt(s.point(3));
        double w = sz(tier), t = s.phase() == 1 ? tail(s) : 0, f = 1 - t;
        int rgb = s.phase() == 2 ? WARN : TEAL;
        for (Point m : new Point[] {a, b}) if (m != null) {
            out.add(FxMotifs.cube(m, (.35 + .15 * p) * w, time * .06, rgb, 220 * f + 15, tier));
            out.add(FxMotifs.cube(m, (.55 + .2 * p) * w, -time * .04, LILAC, 160 * f + 10, tier));
        }
        if (FxMotifs.ok(a, b) && a.minus(b).length() > 1e-3 && a.minus(b).length() < 96) {
            out.add(FxMotifs.circuit(a, b, 2 + (int) Math.round(2 * p), s.token(), (time * .05) % 1, rgb, 220 * f + 15, tier));
            out.add(FxMotifs.packets(a, b, 3, time, .07, .4 * w, ICE, 210 * f + 15, tier));
        }
        if (s.phase() == 1) { if (src != null) departure(out, src, t, p, s.token(), TEAL, tier); if (dst != null) arrival(out, dst, t, p, s.token() + 1, TEAL, tier); }
    }

    // 341: spatial pincer corridor. The three vertices are pinned by cubes and laced by a triangular lattice; firing converges on the impact.
    private static void pincer(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        double w = sz(tier);
        if (s.phase() == 2) {
            double t = tail(s), f = 1 - t;
            for (int i = 0; i < s.points().size(); i++) { Point c = pt(s.point(i)); if (c == null) continue;
                out.add(FxMotifs.reticle(c, X, Z, (.6 + .3 * p) * w * (1.5 - .5 * t), t * 2, ICE, 230 * f + 10, tier));
                out.add(FxLib.spokes(c, s.token() + i, s.age(), TAIL_TICKS, 8, (1 + p) * w, TEAL, 190, tier)); }
            return;
        }
        Point[] v = {pt(s.point(0)), pt(s.point(1)), pt(s.point(2))};
        Point impact = pt(s.point(5));
        boolean firing = s.phase() == 1;
        List<Point> live = new ArrayList<>();
        for (Point c : v) if (c != null) { live.add(c); out.add(FxMotifs.cube(c, (.35 + .15 * p) * w, time * .05, firing ? ICE : TEAL, 220, tier)); }
        for (int i = 0; i < live.size(); i++) for (int j = i + 1; j < live.size(); j++) {
            Point a = live.get(i), b = live.get(j);
            if (a.minus(b).length() > 96) continue;
            List<Stroke> edge = new ArrayList<>();
            glowLine(edge, a, b, .08 * w, TEAL, 110 * tier.glow, .012, 210);
            if (live.size() == 3) { Point opposite = live.get(3 - i - j);
                for (int k = 1; k < 4; k++) { Point along = a.lerp(b, k / 4.0); glowLine(edge, along, along.lerp(opposite, .5), .04 * w, LILAC, 60 * tier.glow, .006, 120); } }
            out.add(new FxLib.Parts(List.copyOf(edge), List.of(), List.of()));
            out.add(FxMotifs.packets(a, b, 2, time, .06, .4 * w, ICE, 210, tier));
        }
        if (firing && impact != null) {
            for (Point c : live) if (c.minus(impact).length() < 96) out.add(FxMotifs.packets(c, impact, 3, time, .14, .6 * w, ICE, 235, tier));
            out.add(FxMotifs.reticle(impact, X, Z, (.9 + .5 * p) * w, time * .08, ICE, 230, tier));
            out.add(glow(impact, (.8 + .6 * p) * w, TEAL, 220, RailThunderArrayGrandGeometry.STAR, time * .1, tier));
        }
        for (int i = 6; i < s.points().size(); i++) { Point tgt = pt(s.point(i)); if (tgt != null) out.add(FxMotifs.reticle(tgt, X, Z, (.5 + .2 * p) * w, time * .06 + i, RED, 200, tier)); }
    }

    // 343: spatial relay. Three numbered beacons (one, two, three pins) chained by data lines; every item leg is a packet stream.
    private static void relay(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        double w = sz(tier), t = s.phase() == 1 ? tail(s) : 0, f = 1 - t;
        Point prev = null;
        for (int i = 0; i < 3; i++) {
            Point m = pt(s.point(i));
            if (m == null) continue;
            Point g = up(m, .05);
            out.add(FxMotifs.hexagon(g, X, Z, Y, (.7 + .3 * p) * w, time * .03 + i, .3 * (i + 1), i == 0 ? TEAL : i == 1 ? LILAC : ICE, 215 * f + 15, tier));
            out.add(FxMotifs.pillar(g, 2.5 + i * 1.2, (.06 + .05 * p) * w, TEAL, 170 * f + 10, tier));
            for (int k = 0; k <= i; k++) out.add(glow(up(m, 2.8 + i * 1.2 + k * .35), .14 * w, ICE, 230 * f + 10, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
            if (prev != null && prev.minus(m).length() < 96) out.add(FxMotifs.packets(up(prev, 1.2), up(m, 1.2), 3, time, .06, .5 * w, TEAL, 215 * f + 15, tier));
            prev = m;
        }
        for (int i = 3; i + 1 < s.points().size(); i += 2) {
            Point a = pt(s.point(i)), b = pt(s.point(i + 1));
            if (!FxMotifs.ok(a, b) || a.minus(b).length() > 96 || a.minus(b).length() < 1e-3) continue;
            out.add(FxMotifs.packets(a, b, 4, time, .1, .4 * w, ICE, 225 * f + 15, tier));
            out.add(FxMotifs.cube(b, .22 * w, time * .1, ICE, 200 * f + 10, tier));
            if (s.phase() == 1) out.add(FxMotifs.voxels(b, .6, 1.2 * w, 10, 1 - Math.min(1, t * 2.5), s.token() + i, TEAL, 220, tier));
        }
    }

    // 353: tactical coordinate formation. The destination becomes a coordinate field: long X/Z axes, a three-ring hex grid,
    // a wide reticle and a scan column, all printed on the ground in a single beat.
    private static void coordinate(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point a = pt(s.point(0)), b = pt(s.point(1));
        if (b == null) return;
        double w = sz(tier), t = tail(s), f = 1 - t;
        Point g = up(b, .05);
        double reach = (6 + 6 * p) * w * (.4 + .6 * Math.min(1, t * 3));
        List<Stroke> axes = new ArrayList<>();
        glowLine(axes, g.plus(X.scale(-reach)), g.plus(X.scale(reach)), .08 * w, TEAL, 120 * tier.glow * f + 10, .014, 230 * f + 15);
        glowLine(axes, g.plus(Z.scale(-reach)), g.plus(Z.scale(reach)), .08 * w, LILAC, 120 * tier.glow * f + 10, .014, 230 * f + 15);
        for (int k = -5; k <= 5; k++) if (k != 0) {
            double at = reach * k / 5.0, tick = .25 * w;
            glowLine(axes, g.plus(X.scale(at)).plus(Z.scale(-tick)), g.plus(X.scale(at)).plus(Z.scale(tick)), .04 * w, TEAL, 70 * f * tier.glow + 5, .008, 170 * f + 10);
            glowLine(axes, g.plus(Z.scale(at)).plus(X.scale(-tick)), g.plus(Z.scale(at)).plus(X.scale(tick)), .04 * w, LILAC, 70 * f * tier.glow + 5, .008, 170 * f + 10);
        }
        out.add(new FxLib.Parts(List.copyOf(axes), List.of(), List.of()));
        out.add(FxMotifs.hexGrid(g, X, Z, (.6 + .3 * p) * w, 3, t * 1.5, TEAL, 220 * f + 15, tier));
        out.add(FxMotifs.reticle(g, X, Z, (3 + 2 * p) * w * (1.4 - .4 * t), t, ICE, 220 * f + 15, tier));
        out.add(FxMotifs.scanRings(g, 16 + 10 * p, (1.4 + .8 * p) * w, 5, time, .05, ICE, 210 * f + 15, tier));
        arrival(out, b, t, p, s.token(), TEAL, tier);
        if (a != null) { departure(out, a, t, p, s.token() + 4, TEAL, tier); path(out, a, b, time, t, p, TEAL, tier); }
    }
}
