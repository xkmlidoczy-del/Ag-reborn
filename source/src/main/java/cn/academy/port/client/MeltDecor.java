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
 * Per-skill presentation of the meltdowner skills as particle-beam hardware: accelerator ring stacks charge the emitters,
 * beams are a hot core inside a green sheath with packets racing along and a dispersion fringe, and every surface the beam
 * touches is a machined hex panel. Signatures: twin accelerator emitter (211), tri-plate scatter array (212), surface etch
 * glyphs with a scanning cutter (213), hex mirror chain with bounce flares (221), hex-cell heat dome (222), targeting plate
 * layout (223), disintegration fan (231), hover-thruster step pads (232), stacked laminate field (241), dissipation curtain
 * wall (242) and relay satellites (253). Pure and deterministic.
 */
public final class MeltDecor {
    public enum Skill { DUAL, PLATES, ETCH, MIRROR, SHELL, LOCATOR, SWEEP, STEP, LAMINATE, CURTAIN, RELAY }
    public static final int MAX_QUADS = 1100;
    private static final int GREEN = 0x7CFF9E, LIME = 0xC8FFB0, MINT = 0x5CFFC8, HOT = 0xFFF3C4, GOLD = 0xFFD36A, ICE = 0xE9FFF0;
    private static final Point X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);

    private MeltDecor() {}

    public static double power(int skillLevel, double casterLevel) { return VectorDecor.power(skillLevel, casterLevel); }

    public static FxLib.Parts fx(Skill skill, DecorSnap s, double time, double power, VisualTier tier) {
        if (skill == null || s == null || tier == null || tier.layers < 3 || !Double.isFinite(time) || !Double.isFinite(power)) return FxLib.Parts.EMPTY;
        double p = Math.max(.2, Math.min(1, power));
        FxLib.Builder out = new FxLib.Builder();
        switch (skill) {
            case DUAL -> dual(out, s, time, p, tier);
            case PLATES -> plates(out, s, time, p, tier);
            case ETCH -> etch(out, s, time, p, tier);
            case MIRROR -> mirror(out, s, time, p, tier, false);
            case SHELL -> shell(out, s, time, p, tier);
            case LOCATOR -> mirror(out, s, time, p, tier, true);
            case SWEEP -> sweep(out, s, time, p, tier);
            case STEP -> step(out, s, time, p, tier);
            case LAMINATE -> laminate(out, s, time, p, tier);
            case CURTAIN -> curtain(out, s, time, p, tier);
            case RELAY -> relay(out, s, time, p, tier);
        }
        return out.build();
    }

    private static double sz(VisualTier tier) { return 1 + .12 * (tier.widthScale - 1); }
    private static double t01(double x) { return x != x ? 0 : Math.max(0, Math.min(1, x)); }
    private static Point up(Point a, double dy) { return a.plus(new Point(0, dy, 0)); }
    private static boolean near(Point a, Point b, double max) { return FxMotifs.ok(a, b) && a.minus(b).length() <= max; }
    private static FxLib.Parts glow(Point at, double radius, int rgb, double alpha, int texture, double roll, VisualTier tier) {
        return new FxLib.Parts(List.of(), List.of(new Billboard(at, radius, rgb, FxLib.alpha(alpha * tier.glow, 1), texture, roll)), List.of());
    }

    /** A beam: hot core inside a green sheath, packets racing along and a dispersion fringe. */
    private static void beam(FxLib.Builder out, Point a, Point b, double time, double p, double alpha, VisualTier tier) {
        if (!near(a, b, 256) || a.minus(b).length() < 1e-3) return;
        double w = sz(tier);
        List<Stroke> line = new ArrayList<>();
        glowLine(line, a, b, (.22 + .14 * p) * w, GREEN, alpha * .55 * tier.glow, .05 * w, alpha);
        glowLine(line, a, b, (.08 + .05 * p) * w, HOT, alpha * .7 * tier.glow, 0, 0);
        out.add(new FxLib.Parts(List.copyOf(line), List.of(), List.of()));
        out.add(FxMotifs.packets(a, b, 3 + (int) Math.round(4 * p), time, .16, (.8 + .6 * p) * w, ICE, alpha, tier));
        out.add(FxLib.dispersion(a, b, .06 * w, alpha * .6, tier));
    }

    /** A stack of accelerator rings round an emitter, spinning up along the firing axis. */
    private static void accelerator(FxLib.Builder out, Point at, Point axis, double time, double p, double alpha, VisualTier tier) {
        Point[] fr = FxMotifs.frame(axis);
        if (fr == null || at == null) return;
        double w = sz(tier);
        int rings = 2 + (int) Math.round(2 * p);
        for (int k = 0; k < rings; k++) {
            Point c = at.plus(fr[0].scale(-.25 * k));
            int filled = 1 + (int) (((time * .3 + k * 2) % 10) / 10 * 10);
            out.add(FxMotifs.gauge(c, fr[1], fr[2], (.35 + .12 * k) * w * (.8 + .3 * p), 10, filled, k % 2 == 0 ? GREEN : MINT, alpha, tier));
        }
        out.add(glow(at, (.5 + .4 * p) * w, LIME, alpha, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
    }

    /** Impact on a surface: closing reticle, hex flash, sparks. */
    private static void impact(FxLib.Builder out, Point at, Point normal, double time, double p, long seed, double alpha, VisualTier tier) {
        if (at == null) return;
        Point[] fr = FxMotifs.frame(normal == null ? Y : normal);
        if (fr == null) fr = FxMotifs.frame(Y);
        double w = sz(tier);
        out.add(FxMotifs.reticle(at, fr[1], fr[2], (.6 + .4 * p) * w, time * .05, ICE, alpha, tier));
        out.add(FxMotifs.hexGrid(at, fr[1], fr[2], (.18 + .08 * p) * w, 1, time * .06, GREEN, alpha, tier));
        out.add(FxLib.sparkShower(at, fr[0], seed, (time * 1.3) % 12, 14 + (int) (10 * p), 6, 1.3, 8, 12, HOT, GREEN, tier));
        out.add(glow(at, (.6 + .5 * p) * w, HOT, alpha, RailThunderArrayGrandGeometry.STAR, time * .1, tier));
    }

    // 211: dual point. A twin accelerator stack at the emitter, a heavy beam and a machined impact.
    private static void dual(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(0)), e = pt(s.point(1));
        if (!near(o, e, 256) || o.minus(e).length() < 1e-3) return;
        Point axis = FxMotifs.unit(e.minus(o));
        accelerator(out, o, axis, time, p, 220, tier);
        beam(out, o, e, time, p, 230, tier);
        impact(out, e, axis.scale(-1), time, p, s.token(), 230, tier);
    }

    // 212: scatter plates. Three hex plates stand round the axis; live plates glow, the struck plate fires a lock-on.
    private static void plates(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point c = pt(s.point(0)), axis = FxMotifs.unit(pt(s.point(1)));
        if (c == null) return;
        Point[] fr = FxMotifs.frame(axis == null ? Y : axis);
        double w = sz(tier);
        int mask = (int) s.number(0, 7), struck = (int) s.number(1, -1);
        for (int i = 0; i < 3; i++) {
            double a = time * .02 + i * Math.PI * 2 / 3;
            Point at = c.plus(fr[1].scale(Math.cos(a) * (1.6 + .6 * p) * w)).plus(fr[2].scale(Math.sin(a) * (1.6 + .6 * p) * w));
            boolean live = (mask & (1 << i)) != 0;
            int rgb = i == struck ? HOT : live ? GREEN : MINT;
            out.add(FxMotifs.hexagon(at, fr[1], fr[2], fr[0], (.45 + .2 * p) * w, a, 0, rgb, live ? 230 : 80, tier));
            out.add(FxMotifs.hexGrid(at, fr[1], fr[2], (.13 + .05 * p) * w, 1, time * .04 + i, rgb, live ? 190 : 60, tier));
            if (live) out.add(FxMotifs.packets(c, at, 2, time + i * 7, .08, .3 * w, ICE, 200, tier));
        }
        out.add(FxMotifs.gauge(c, fr[1], fr[2], (.5 + .2 * p) * w, 3, Integer.bitCount(mask & 7), GREEN, 220, tier));
        Point hit = pt(s.point(2));
        if (hit != null && hit.minus(c).length() < 32 && hit.minus(c).length() > 1e-3) { beam(out, c, hit, time, p * .7, 200, tier); impact(out, hit, fr[0], time, p, s.token(), 220, tier); }
        for (int i = 3; i < s.points().size(); i++) { Point h = pt(s.point(i)); if (near(h, c, 48)) out.add(FxMotifs.reticle(h, X, Z, (.5 + .3 * p) * w, time * .05, HOT, 200, tier)); }
    }

    // 213: beam etching. Each etched mark is a burning hex glyph; a cutter beam scans from the emitter mark to mark.
    private static void etch(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(0));
        if (o == null) return;
        double w = sz(tier);
        int n = s.points().size() - 1, confirmed = (int) s.number(0, n);
        for (int i = 1; i < s.points().size(); i++) {
            Point m = pt(s.point(i));
            if (!near(m, o, 64)) continue;
            boolean done = i <= confirmed;
            out.add(FxMotifs.hexagon(m, X, Z, Y, (.32 + .12 * p) * w, time * .03 + i, 0, done ? HOT : GREEN, done ? 230 : 150, tier));
            out.add(FxMotifs.reticle(m, X, Z, (.4 + .15 * p) * w, -time * .04, done ? GOLD : MINT, done ? 210 : 120, tier));
            out.add(glow(m, (.3 + .2 * p) * w, done ? HOT : GREEN, done ? 200 : 110, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
        }
        if (n > 0) {
            int target = 1 + (int) ((time * .08) % n);
            Point m = pt(s.point(target));
            if (near(m, o, 64)) { beam(out, up(o, 1.4), m, time, p * .6, 210, tier); out.add(FxLib.sparkShower(m, Y, s.token() + target, (time * 1.5) % 10, 12, 5, 1.2, 8, 10, HOT, GOLD, tier)); }
        }
        accelerator(out, up(o, 1.4), Y, time, p * .6, 180, tier);
    }

    // 221 / 223: folded mirror (live beam) and scatter locator (preview). Plates are hex mirrors set by their normals;
    // the path is a beam with bounce flares (mirror) or a dashed targeting line with brackets (locator).
    private static void mirror(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier, boolean preview) {
        Point feet = pt(s.point(0));
        double w = sz(tier);
        for (int i = 3; i + 1 < s.points().size(); i += 2) {
            Point c = pt(s.point(i)), n = FxMotifs.unit(pt(s.point(i + 1)));
            if (c == null || n == null || (feet != null && c.minus(feet).length() > 96)) continue;
            Point[] fr = FxMotifs.frame(n);
            if (preview) {
                out.add(FxMotifs.reticle(c, fr[1], fr[2], (.6 + .3 * p) * w, time * .03 + i, MINT, 210, tier));
                out.add(FxMotifs.hexagon(c, fr[1], fr[2], fr[0], (.5 + .2 * p) * w, 0, 0, GREEN, 140, tier));
            } else {
                out.add(FxMotifs.hexagon(c, fr[1], fr[2], fr[0], (.7 + .3 * p) * w, time * .01, 0, LIME, 230, tier));
                out.add(FxMotifs.hexGrid(c, fr[1], fr[2], (.16 + .06 * p) * w, 2, time * .03 + i, GREEN, 180, tier));
                out.add(glow(c, (.4 + .3 * p) * w, GREEN, 120, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
            }
        }
        Point ghost = pt(s.point(1)), gn = FxMotifs.unit(pt(s.point(2)));
        if (ghost != null && gn != null) { Point[] fr = FxMotifs.frame(gn); out.add(FxMotifs.hexagon(ghost, fr[1], fr[2], fr[0], (.6 + .2 * p) * w, time * .04, 0, MINT, 110 + 60 * Math.sin(time * .3), tier)); }
        for (List<double[]> line : s.polylines()) {
            Point prev = null;
            int k = 0;
            for (double[] v : line) {
                Point cur = pt(v);
                if (cur == null) continue;
                if (prev != null && prev.minus(cur).length() < 128) {
                    if (preview) out.add(FxMotifs.packets(prev, cur, 3, time + k * 5, .06, .4 * w, MINT, 200, tier));
                    else { beam(out, prev, cur, time + k * 3, p, 210, tier); out.add(glow(cur, (.4 + .3 * p) * w, HOT, 170, RailThunderArrayGrandGeometry.STAR, time * .1 + k, tier)); }
                }
                prev = cur; k++;
            }
            if (!preview && prev != null) impact(out, prev, null, time, p, s.token(), 220, tier);
        }
    }

    // 222: thermal shell. A dome of hex cells around the body, heat rings rising off it and a flare where a hit lands.
    private static void shell(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point body = pt(s.point(0));
        if (body == null) return;
        double w = sz(tier), breathe = .8 + .2 * Math.sin(time * .25), r = (1.6 + .5 * p) * w;
        Point chest = up(body, 1.0);
        for (int k = 0; k < 6; k++) {
            double a = time * .01 + k * Math.PI / 3;
            Point n = new Point(Math.cos(a), .25, Math.sin(a));
            Point u = FxMotifs.unit(n);
            if (u == null) continue;
            Point[] fr = FxMotifs.frame(u);
            out.add(FxMotifs.hexGrid(chest.plus(u.scale(r)), fr[1], fr[2], (.2 + .06 * p) * w, 1, time * .03 + k, GREEN, 170 * breathe, tier));
        }
        out.add(FxMotifs.hexGrid(up(chest, r * .9), X, Z, (.2 + .06 * p) * w, 1, time * .03, LIME, 170 * breathe, tier));
        out.add(FxMotifs.scanRings(body, 2.6, r * .9, 3, time, .03, LIME, 160, tier));
        out.add(FxMotifs.ring(up(body, .05), X, Z, r * 1.05, GREEN, 170));
        Point hit = pt(s.point(2));
        if (near(hit, body, 8) && hit.length() > 1e-6) impact(out, hit, FxMotifs.unit(hit.minus(chest)), time, p, s.token(), 220 * (.6 + .4 * Math.sin(time * .8)), tier);
    }

    // 231: disintegration sweep. A fan of accelerating arcs sweeps between the two edge angles; the swept path is a beam and every mark burns.
    private static void sweep(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(1)), fwd = FxMotifs.unit(pt(s.point(2))), across = FxMotifs.unit(pt(s.point(3)));
        if (o == null || fwd == null || across == null) return;
        double w = sz(tier), from = s.number(2, -.4), to = s.number(3, .4), mining = s.number(4, 0);
        int rgb = mining > 0 ? GOLD : GREEN;
        double reach = 14;
        for (List<double[]> line : s.polylines()) for (double[] v : line) { Point c = pt(v); if (c != null) reach = Math.max(reach, Math.min(64, c.minus(o).length())); }
        double mid = (from + to) / 2, span = Math.toDegrees(Math.max(.05, Math.abs(to - from)));
        Point u = fwd.scale(Math.cos(mid)).plus(across.scale(Math.sin(mid))), v = across.scale(Math.cos(mid)).minus(fwd.scale(Math.sin(mid)));
        // The sector lies in one plane, so from eye level it collapsed to a single hairline. Stack faded layers and
        // raise upright fins on the leading arc, which keeps a vertical silhouette from any camera height.
        double lift = .38 + .22 * p;
        for (int layer = -1; layer <= 1; layer++) {
            Point centre = layer == 0 ? o : o.plus(new Point(0, layer * lift, 0));
            double fade = layer == 0 ? 1 : (layer < 0 ? .5 : .74);
            for (int k = 1; k <= 4; k++) out.add(FxMotifs.arc(centre, u, v, reach * k / 4.0, Math.max(10, span), 16, .02, k == 4 ? HOT : rgb, (230 - 25 * k) * fade, tier));
        }
        for (int fin = 0; fin <= 8; fin++) {
            double edgeAngle = from + (to - from) * fin / 8.0;
            Point dir = fwd.scale(Math.cos(edgeAngle)).plus(across.scale(Math.sin(edgeAngle)));
            Point base = o.plus(dir.scale(reach));
            List<Stroke> rib = new ArrayList<>();
            glowLine(rib, base.plus(new Point(0, -lift, 0)), base.plus(new Point(0, lift * 1.6, 0)), .06 * w, fin % 2 == 0 ? HOT : rgb, 165 * tier.glow, .012, 215);
            out.add(new FxLib.Parts(List.copyOf(rib), List.of(), List.of()));
        }
        for (double edge : new double[] {from, to}) {
            Point dir = fwd.scale(Math.cos(edge)).plus(across.scale(Math.sin(edge)));
            List<Stroke> l = new ArrayList<>();
            glowLine(l, o, o.plus(dir.scale(reach)), .1 * w, rgb, 110 * tier.glow, .016, 200);
            out.add(new FxLib.Parts(List.copyOf(l), List.of(), List.of()));
        }
        Point head = o.plus(fwd.scale(Math.cos(to) * reach)).plus(across.scale(Math.sin(to) * reach));
        beam(out, o, head, time, p, 230, tier);
        accelerator(out, o, fwd, time, p, 220, tier);
        for (int i = 4; i < s.points().size(); i++) { Point m = pt(s.point(i)); if (near(m, o, 96)) out.add(FxMotifs.reticle(m, X, Z, (.4 + .2 * p) * w, time * .05 + i, mining > 0 ? GOLD : HOT, 210, tier)); }
    }

    // 232: beam step. Each foothold is a hovering hex pad with thruster glow underneath and packets streaming down.
    private static void step(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point c = pt(s.point(0));
        if (c == null) return;
        double w = sz(tier), f = 1 - t01(s.age() / 60.0);
        out.add(FxMotifs.hexagon(c, X, Z, Y, (.6 + .2 * p) * w, time * .03, 0, LIME, 230 * f + 20, tier));
        out.add(FxMotifs.hexGrid(c, X, Z, (.15 + .05 * p) * w, 1, time * .05, GREEN, 200 * f + 20, tier));
        out.add(glow(up(c, -.15), (.6 + .3 * p) * w, GREEN, 200 * f + 20, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
        out.add(FxMotifs.packets(c, up(c, -2.2), 3, time, .12, .35 * w, ICE, 200 * f + 20, tier));
        out.add(FxMotifs.ring(c, X, Z, (.8 + .3 * Math.sin(time * .3)) * w, MINT, 180 * f + 20));
    }

    // 241: particle laminate. Each panel is three stacked hex layers; a pulse runs through the stack and the field path burns as a beam.
    private static void laminate(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        double w = sz(tier);
        int pulse = (int) s.number(3, 0);
        Point feet = pt(s.point(0));
        int idx = 0;
        for (int i = 3; i + 1 < s.points().size(); i += 2, idx++) {
            Point c = pt(s.point(i)), n = FxMotifs.unit(pt(s.point(i + 1)));
            if (c == null || n == null || (feet != null && c.minus(feet).length() > 96)) continue;
            Point[] fr = FxMotifs.frame(n);
            for (int layer = -1; layer <= 1; layer++) {
                boolean lit = Math.floorMod(pulse + idx, 3) == layer + 1 || ((int) (time * .2) + layer) % 3 == 0;
                out.add(FxMotifs.hexGrid(c.plus(n.scale(.22 * layer * w)), fr[1], fr[2], (.18 + .06 * p) * w, 1 + (p > .6 ? 1 : 0), time * .03 + layer, lit ? LIME : GREEN, lit ? 220 : 120, tier));
            }
            out.add(FxMotifs.hexagon(c, fr[1], fr[2], fr[0], (.75 + .3 * p) * w, 0, .25, MINT, 200, tier));
        }
        mirror(out, new DecorSnap(s.local(), s.age(), s.phase(), s.token(), List.of(), s.numbers(), s.polylines()), time, p, tier, false);
    }

    // 242: dissipation curtain. A standing wall of hex cells with scanlines dropping through it; contacts ripple outward.
    private static void curtain(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point c = pt(s.point(0)), axis = FxMotifs.unit(pt(s.point(1)));
        if (c == null) return;
        Point face = axis == null ? Z : FxMotifs.unit(new Point(axis.x(), 0, axis.z()));
        if (face == null) face = Z;
        Point side = FxMotifs.unit(face.cross(Y));
        if (side == null) side = X;
        double w = sz(tier);
        Point wall = up(c, 1.4);
        out.add(FxMotifs.hexGrid(wall, side, Y, (.32 + .12 * p) * w, 3, time * .02, GREEN, 190, tier));
        out.add(FxMotifs.panel(wall, side, Y, (2.4 + .8 * p) * w, (1.6 + .4 * p) * w, .3, LIME, 200, tier));
        List<Stroke> scans = new ArrayList<>();
        for (int k = 0; k < 3; k++) {
            double y = 1 - ((time * .03 + k / 3.0) % 1), hy = (1.6 + .4 * p) * w * (2 * y - 1), hw = (2.4 + .8 * p) * w;
            glowLine(scans, wall.plus(side.scale(-hw)).plus(Y.scale(hy)), wall.plus(side.scale(hw)).plus(Y.scale(hy)), .06 * w, ICE, 120 * tier.glow, .012, 210);
        }
        out.add(new FxLib.Parts(List.copyOf(scans), List.of(), List.of()));
        Point hit = pt(s.point(2));
        if (near(hit, c, 12) && hit.length() > 1e-6) {
            double ripple = (time * .08) % 1;
            out.add(FxMotifs.ring(hit, side, Y, (.3 + 2.2 * ripple) * w, HOT, 230 * (1 - ripple)));
            out.add(FxMotifs.ring(hit, side, Y, (.2 + 1.2 * ripple) * w, LIME, 220 * (1 - ripple)));
            out.add(glow(hit, (.6 + .4 * p) * w, HOT, 220, RailThunderArrayGrandGeometry.STAR, time * .1, tier));
        }
    }

    // 253: remote scatter relay. Every relay node is a satellite cube with a ring aligned to its normal; the relayed beam bounces through them.
    private static void relay(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point feet = pt(s.point(0));
        double w = sz(tier);
        for (int i = 3; i + 1 < s.points().size(); i += 2) {
            Point c = pt(s.point(i)), n = FxMotifs.unit(pt(s.point(i + 1)));
            if (c == null || (feet != null && c.minus(feet).length() > 128)) continue;
            Point[] fr = FxMotifs.frame(n == null ? Y : n);
            out.add(FxMotifs.cube(c, (.3 + .1 * p) * w, time * .05 + i, LIME, 230, tier));
            out.add(FxMotifs.ring(c, fr[1], fr[2], (.8 + .3 * p) * w, GREEN, 210));
            out.add(FxMotifs.orbit(c, fr[1], fr[2], (.8 + .3 * p) * w, 3, time * .08 + i, .12, MINT, 200, tier));
            if (feet != null) out.add(FxMotifs.packets(up(feet, 1.4), c, 2, time + i * 3, .05, .35 * w, MINT, 150, tier));
        }
        mirror(out, new DecorSnap(s.local(), s.age(), s.phase(), s.token(), List.of(), s.numbers(), s.polylines()), time, p, tier, false);
    }
}
