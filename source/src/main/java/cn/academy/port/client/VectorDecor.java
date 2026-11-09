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
 * Per-skill presentation of the vector (white-gray air and pressure) skills. Every skill has its own motif so none looks
 * like another: chevron trains and a compressed disc (411), a guard crescent that bends the incoming line (412), speed lines
 * collapsing to a point (413), a pinned hexagon with ground cracks (422), sweeping wind sheets (423), a descending helix
 * with ground ripples (432), sonar rings with bearing ticks (433), four pressure walls with inward arrows (441), mirror
 * panels with ricochet zigzags (442) and orbiting satellites that dart to intercept (452). {@code power} (0.2..1) is the
 * skill level blended with the caster level: stronger skills get bigger, denser and more layered cues. Pure and deterministic.
 */
public final class VectorDecor {
    public enum Skill { STRIKE, PARRY, BRAKE, ANCHOR, PALM, RELIEF, LISTEN, CAGE, REFLECT, CIRCLE }
    public static final int MAX_QUADS = 700;
    private static final int AIR = 0xDCE8F2, COOL = 0x9FB6CC, CYAN = 0x8FD8FF, WARM = 0xFFE9C0;
    private static final Point X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);

    private VectorDecor() {}

    /** Blend of the skill's own level and the caster's level (0..1 each of 5), floored so a low-level cue is still readable. */
    public static double power(int skillLevel, double casterLevel) {
        double s = Math.max(1, Math.min(5, skillLevel)) / 5.0, c = Math.max(1, Math.min(5, Double.isFinite(casterLevel) ? casterLevel : 3)) / 5.0;
        return Math.max(.2, Math.min(1, s * .6 + c * .4));
    }

    public static FxLib.Parts fx(Skill skill, DecorSnap s, double time, double power, VisualTier tier) {
        if (skill == null || s == null || tier == null || tier.layers < 3 || !Double.isFinite(time) || !Double.isFinite(power)) return FxLib.Parts.EMPTY;
        double p = Math.max(.2, Math.min(1, power));
        FxLib.Builder out = new FxLib.Builder();
        switch (skill) {
            case STRIKE -> strike(out, s, time, p, tier);
            case PARRY -> parry(out, s, time, p, tier);
            case BRAKE -> brake(out, s, time, p, tier);
            case ANCHOR -> anchor(out, s, time, p, tier);
            case PALM -> palm(out, s, time, p, tier);
            case RELIEF -> relief(out, s, time, p, tier);
            case LISTEN -> listen(out, s, time, p, tier);
            case CAGE -> cage(out, s, time, p, tier);
            case REFLECT -> reflect(out, s, time, p, tier);
            case CIRCLE -> circle(out, s, time, p, tier);
        }
        return out.build();
    }

    private static Point up(Point a, double dy) { return a.plus(new Point(0, dy, 0)); }
    private static double fade(DecorSnap s, double life) { return Math.max(.08, 1 - t01(s.age() / life)); }
    /** NaN-safe clamp to 0..1 so hostile ages and counters can never push a size or alpha out of range. */
    private static double t01(double x) { return x != x ? 0 : Math.max(0, Math.min(1, x)); }

    // 411: a punch of compressed air: chevrons run down the line and a pressure disc blooms at every contact.
    private static void strike(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(0)), e = pt(s.point(1));
        if (!FxMotifs.ok(o, e) || e.minus(o).length() < 1e-6) return;
        Point d = FxMotifs.unit(e.minus(o)), length = e.minus(o);
        double f = fade(s, 14), w = 1 + .12 * (tier.widthScale - 1);
        out.add(FxMotifs.chevrons(o, d, length.length(), 3 + (int) Math.round(5 * p), (.75 + .5 * p) * w, s.age() * .18, AIR, 255 * f, tier));
        Point[] fr = FxMotifs.frame(d);
        out.add(FxMotifs.arc(o.plus(d.scale(.9)), fr[2], fr[1], (.7 + .5 * p) * w, 140, 14, .02, WARM, 200 * f, tier));
        for (int i = 2; i < s.points().size(); i++) {
            Point c = pt(s.point(i));
            if (c == null) continue;
            double t = t01(s.age() / 12.0);
            out.add(FxMotifs.ring(c, fr[1], fr[2], (.5 + 2.2 * p * t) * w, AIR, 200 * (1 - t)));
            out.add(FxMotifs.ring(c, fr[1], fr[2], (.3 + 1.2 * p * t) * w, 0xFFFFFF, 170 * (1 - t)));
            out.add(new FxLib.Parts(List.of(), List.of(new Billboard(c, (.4 + .5 * p) * w, AIR, FxLib.alpha(150 * tier.glow, 1 - t), RailThunderArrayGrandGeometry.STAR, s.age() * .2)), List.of()));
            out.add(FxLib.spokes(c, s.token() + i, s.age() / 2.0, 7, 8 + (int) (6 * p), (.9 + 1.0 * p) * w, AIR, 150, tier));
        }
    }

    // 412: a guard crescent in front of the body; a precise parry flares gold and the incoming line bends into a ricochet.
    private static void parry(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point body = pt(s.point(0)), raw = pt(s.point(1)), face = raw == null ? null : FxMotifs.unit(new Point(raw.x(), 0, raw.z()));
        if (!FxMotifs.ok(body) || face == null) return;
        Point[] fr = FxMotifs.frame(face);
        double w = 1 + .12 * (tier.widthScale - 1), precise = s.number(0, 0) > 0 ? 1 : 0, breathe = .75 + .25 * Math.sin(time * .35);
        Point chest = up(body, 1.2);
        int guard = precise > 0 ? WARM : AIR;
        out.add(FxMotifs.panel(chest.plus(face.scale(1.1)), fr[1], fr[2], (.55 + .3 * p) * w, (.8 + .35 * p) * w, 0, guard, 150 * breathe, tier));
        for (int k = -1; k <= 1; k++)
            out.add(FxMotifs.arc(chest.plus(new Point(0, .55 * k, 0)), face, fr[1], (1.1 + .4 * p) * w, 150, 18, .022, k == 0 ? guard : COOL, (k == 0 ? 210 : 150) * breathe, tier));
        if (s.number(2, -1) >= 0) {
            Point impact = pt(s.point(2)), in = FxMotifs.unit(pt(s.point(3)));
            double t = t01(s.number(2, 0) / 10.0), f = 1 - t;
            if (FxMotifs.ok(impact)) {
                out.add(new FxLib.Parts(List.of(), List.of(new Billboard(impact, (.4 + .4 * p) * w, precise > 0 ? WARM : AIR, FxLib.alpha(170 * tier.glow, f), RailThunderArrayGrandGeometry.STAR, time * .1)), List.of()));
                out.add(FxLib.spokes(impact, s.token(), s.number(2, 0) / 2.0, 6, 9, (.8 + .8 * p) * w, precise > 0 ? WARM : AIR, 150, tier));
                List<Stroke> line = new ArrayList<>();
                if (in != null) glowLine(line, impact.plus(in.scale(3.2 * (1 - t * .5))), impact, .1 * w, COOL, 120 * f * tier.glow, .012, 210 * f);
                Point turn = FxMotifs.unit(pt(s.point(4)));
                if (turn != null) { glowLine(line, impact, impact.plus(turn.scale(4.5 * (.3 + t))), .1 * w, precise > 0 ? WARM : CYAN, 130 * f * tier.glow, .014, 220 * f);
                    out.add(FxMotifs.chevrons(impact, turn, 4.5, 3, .35 * w, s.number(3, 0) * .2, precise > 0 ? WARM : CYAN, 200 * f, tier)); }
                out.add(new FxLib.Parts(List.copyOf(line), List.of(), List.of()));
            }
        }
    }

    // 413: speed lines collapsing into the point of contact, a low dust ring and a small flash.
    private static void brake(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point c = pt(s.point(0));
        if (c == null) return;
        double t = t01(s.age() / 12.0), w = 1 + .12 * (tier.widthScale - 1), f = 1 - t;
        out.add(FxMotifs.converging(c, 10 + (int) (14 * p), (2.0 + 1.6 * p) * w, t, s.token(), AIR, 220, tier));
        out.add(FxMotifs.ring(up(c, .05), X, Z, (.4 + 1.9 * p * t) * w, COOL, 160 * f));
        out.add(new FxLib.Parts(List.of(), List.of(new Billboard(c, (.45 + .4 * p) * w, 0xFFFFFF, FxLib.alpha(230 * tier.glow, f), RailThunderArrayGrandGeometry.RADIAL, 0)), List.of()));
        out.add(FxLib.embers(c, .5 * w, 1.2, s.token(), s.age() * 3, 8, AIR, COOL, tier));
    }

    // 422: a pinned hexagon sinks into the ground and cracks spread: the body is anchored, not shielded.
    private static void anchor(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point feet = pt(s.point(0));
        if (feet == null) return;
        double w = 1 + .12 * (tier.widthScale - 1), grounded = s.number(1, 0), hits = s.number(0, 1), breathe = .8 + .2 * Math.sin(time * .2);
        Point g = up(feet, .04);
        out.add(FxMotifs.hexagon(g, X, Z, Y, (1.5 + .7 * p) * w, time * .01, .55 + .3 * p, grounded > 0 ? AIR : COOL, 200 * breathe, tier));
        out.add(FxMotifs.hexagon(g, X, Z, Y, (.9 + .4 * p) * w, -time * .014, 0, CYAN, 150 * breathe, tier));
        out.add(FxMotifs.cracks(g, X, Z, 6 + (int) Math.round(4 * p), (2.4 + 1.8 * p) * w, t01(.35 + .2 * hits), s.token(), AIR, 170, tier));
        if (s.number(2, -1) >= 0 || s.number(3, -1) >= 0) {
            double pressure = s.number(2, -1), landing = s.number(3, -1);
            double age = pressure < 0 ? landing : landing < 0 ? pressure : Math.min(pressure, landing);
            double t = t01(age / 10.0);
            out.add(FxMotifs.ring(g, X, Z, (.6 + 3.6 * p * t) * w, WARM, 190 * (1 - t)));
            out.add(FxLib.sparkShower(g, Y, s.token(), age, 14, 5, 1.2, 8, 12, WARM, COOL, tier));
        }
    }

    // 423: lateral wind sheets sweep the target sideways: three curved ribbons and a chevron wave along the push.
    private static void palm(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(0));
        if (o == null) return;
        double w = 1 + .12 * (tier.widthScale - 1), f = fade(s, 12);
        for (int i = 1; i + 1 < s.points().size(); i += 2) {
            Point end = pt(s.point(i)), delta = pt(s.point(i + 1));
            if (end == null) continue;
            Point lateral = delta == null ? null : FxMotifs.unit(new Point(delta.x(), 0, delta.z()));
            if (lateral == null) { Point fwd = FxMotifs.unit(end.minus(o)); lateral = fwd == null ? X : FxMotifs.unit(fwd.cross(Y)); if (lateral == null) lateral = X; }
            Point center = end.minus(lateral.scale(1.6 * w));
            for (int k = 0; k < 3; k++) {
                Point axisUp = Y;
                out.add(FxMotifs.arc(up(center, -.6 + k * .7), lateral, Z, (1.5 + .5 * p + .2 * k) * w, 110 + 20 * k, 14, .03, k == 1 ? AIR : COOL, 255 * f, tier));
            }
            out.add(FxMotifs.chevrons(center, lateral, 3.4 * w, 4, (.6 + .3 * p) * w, s.age() * .2, AIR, 255 * f, tier));
            out.add(new FxLib.Parts(List.of(), List.of(new Billboard(end, (.6 + .6 * p) * w, AIR, FxLib.alpha(190 * tier.glow, f), RailThunderArrayGrandGeometry.STAR, 0)), List.of()));
        }
        if (s.number(0, 0) > 0) out.add(FxMotifs.ring(up(o, .05), X, Z, (1 + 2.5 * (1 - f)) * w, COOL, 140 * f));
    }

    // 432: a helix of air spirals down the body and into the ground; ripples spread from the sink.
    private static void relief(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point body = pt(s.point(0)), sink = pt(s.point(2));
        if (body == null) return;
        Point ground = sink != null ? sink : up(body, .04);
        double w = 1 + .12 * (tier.widthScale - 1), breathe = .8 + .2 * Math.sin(time * .3);
        out.add(FxMotifs.helix(up(body, 2.0), new Point(0, -1, 0), 2.0, (.75 + .3 * p) * w, 3 + 2 * p, -time * .25, true, AIR, 200 * breathe, tier));
        out.add(FxMotifs.helix(up(body, 1.9), new Point(0, -1, 0), 1.9, (.55 + .2 * p) * w, 2, time * .3 + Math.PI, true, CYAN, 150 * breathe, tier));
        for (int k = 0; k < 3; k++) {
            double t = ((time * .03 + k / 3.0) % 1 + 1) % 1;
            out.add(FxMotifs.ring(up(ground, .04), X, Z, (.4 + 3.2 * p * t) * w, COOL, 170 * (1 - t)));
        }
        if (s.number(2, -1) >= 0) {
            Point c = pt(s.point(3));
            double t = t01(s.number(2, 0) / 9.0);
            if (c != null) { out.add(FxMotifs.ring(c, X, Z, (.5 + 2.2 * p * t) * w, WARM, 190 * (1 - t))); out.add(FxLib.sparkShower(c, Y, s.token(), s.number(2, 0), 14, 5, 1.1, 8, 10, WARM, COOL, tier)); }
        }
    }

    // 433: sonar. Rings expand over the ground and a bright tick on the rim marks the bearing of every sensed disturbance.
    private static void listen(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point me = pt(s.point(0));
        if (me == null) return;
        double w = 1 + .12 * (tier.widthScale - 1);
        for (int k = 0; k < 3; k++) {
            double t = ((time * .025 + k / 3.0) % 1 + 1) % 1;
            out.add(FxMotifs.ring(up(me, .05), X, Z, (1 + 12 * t) * w * (.6 + .4 * p), COOL, 230 * (1 - t * .7) * (.6 + .4 * p)));
        }
        int n = (int) s.number(1, 0);
        for (int i = 0; i < n && 2 + i * 4 + 3 < s.numbers().length; i++) {
            double az = Math.toRadians(s.number(2 + i * 4, 0)), strength = Math.max(.2, t01(s.number(4 + i * 4, 2) / 3.0)), kind = s.number(5 + i * 4, 0);
            Point dir = new Point(-Math.sin(az), 0, Math.cos(az));
            Point from = up(me, .15).plus(dir.scale(3.2 * w)), to = from.plus(dir.scale((.8 + 1.4 * strength) * w));
            List<Stroke> tick = new ArrayList<>();
            glowLine(tick, from, to, .14 * w, kind > 0 ? WARM : CYAN, 170 * tier.glow, .02, 235);
            Point[] fr = FxMotifs.frame(dir);
            glowLine(tick, to, to.minus(dir.scale(.5 * w)).plus(fr[1].scale(.3 * w)), .08 * w, kind > 0 ? WARM : CYAN, 120 * tier.glow, .012, 220);
            glowLine(tick, to, to.minus(dir.scale(.5 * w)).minus(fr[1].scale(.3 * w)), .08 * w, kind > 0 ? WARM : CYAN, 120 * tier.glow, .012, 220);
            out.add(new FxLib.Parts(List.copyOf(tick), List.of(new Billboard(to, .3 * w, 0xFFFFFF, FxLib.alpha(230 * tier.glow, .6 + .4 * Math.sin(time * .5 + i)), RailThunderArrayGrandGeometry.RADIAL, 0)), List.of()));
        }
    }

    // 441: four pressure walls close in around the cage; arrows on every edge point inward and a hit flashes the wall.
    private static void cage(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point c = pt(s.point(0));
        if (c == null) return;
        double half = Math.max(1, Math.min(16, s.number(0, 4))), pulse = s.number(1, 0), w = 1 + .12 * (tier.widthScale - 1), preview = s.number(4, 0);
        double squeeze = 1 - .08 * Math.sin(time * .25 + pulse), alpha = preview > 0 ? 70 : 120;
        Point[] normals = {X, Z, X.scale(-1), Z.scale(-1)};
        for (int i = 0; i < 4; i++) {
            Point n = normals[i], tangent = new Point(-n.z(), 0, n.x());
            Point wall = up(c, 1.4).plus(n.scale(half * squeeze));
            out.add(FxMotifs.panel(wall, tangent, Y, half * .95, 1.5 + .4 * p, (.5 + .3 * p) * w, i == (int) s.number(2, -1) ? WARM : AIR, alpha, tier));
            out.add(FxMotifs.lattice(wall, tangent, Y, Math.min(half * .9, 1.4), 3, time * .1 + i, COOL, preview > 0 ? 40 : 70, tier));
        }
        for (int i = 1; i < s.points().size() && i <= 4; i++) {
            Point hit = pt(s.point(i));
            if (hit == null) continue;
            out.add(new FxLib.Parts(List.of(), List.of(new Billboard(hit, (.8 + .9 * p) * w, WARM, FxLib.alpha(220 * tier.glow, .8), RailThunderArrayGrandGeometry.STAR, time * .15)), List.of()));
            out.add(FxLib.spokes(hit, s.token() + i, (time * .5) % 8, 8, 10, (1.4 + 1.2 * p) * w, WARM, 190, tier));
        }
    }

    // 442: four mirror panels; every live facet is lit and the ricochet traces are drawn as sharp bright zigzags.
    private static void reflect(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point body = pt(s.point(0));
        if (body == null) return;
        int mask = (int) s.number(0, 15);
        double w = 1 + .12 * (tier.widthScale - 1), spin = time * .02;
        Point chest = up(body, 1.1);
        for (int i = 0; i < 4; i++) {
            double a = spin + i * Math.PI / 2;
            Point n = new Point(Math.cos(a), 0, Math.sin(a)), tangent = new Point(-n.z(), 0, n.x());
            boolean live = (mask & (1 << i)) != 0;
            Point at = chest.plus(n.scale((1.7 + .4 * p) * w));
            out.add(FxMotifs.panel(at, tangent, Y, .55 * w, .85 * w, 0, live ? AIR : COOL, live ? 210 : 70, tier));
            if (live) out.add(new FxLib.Parts(List.of(), List.of(new Billboard(at, .5 * w, 0xFFFFFF, FxLib.alpha(200 * tier.glow, .55 + .45 * Math.sin(time * .4 + i * 1.7)), RailThunderArrayGrandGeometry.RADIAL, 0)), List.of()));
        }
        for (List<double[]> trace : s.polylines()) {
            List<Stroke> line = new ArrayList<>();
            List<Billboard> flashes = new ArrayList<>();
            Point prev = null;
            for (double[] v : trace) {
                Point cur = pt(v);
                if (cur == null) continue;
                if (prev != null) glowLine(line, prev, cur, .1 * w, CYAN, 140 * tier.glow, .016, 230);
                flashes.add(new Billboard(cur, .4 * w, 0xFFFFFF, FxLib.alpha(220 * tier.glow, .8), RailThunderArrayGrandGeometry.STAR, 0));
                prev = cur;
            }
            out.add(new FxLib.Parts(List.copyOf(line), List.copyOf(flashes), List.of()));
        }
        Point contact = pt(s.point(3));
        if (contact != null && s.number(1, -1) >= 0) {
            double t = t01(s.number(1, 0) / 8.0);
            out.add(new FxLib.Parts(List.of(), List.of(new Billboard(contact, (.9 + .8 * p) * w, AIR, FxLib.alpha(230 * tier.glow, 1 - t), RailThunderArrayGrandGeometry.STAR, t)), List.of()));
            out.add(FxLib.spokes(contact, s.token(), s.number(1, 0), 8, 12, (1.4 + 1.4 * p) * w, AIR, 200, tier));
        }
    }

    // 452: two counter-rotating rings of satellites; when something hits, the nearest satellite darts out to meet it.
    private static void circle(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point body = pt(s.point(0));
        if (body == null) return;
        double w = 1 + .12 * (tier.widthScale - 1), ready = s.number(0, 1) > 0 ? 1 : .45;
        Point chest = up(body, 1.1);
        out.add(FxMotifs.orbit(chest, X, Z, (1.9 + .5 * p) * w, 6, time * .08, .3 + .14 * p, AIR, 235 * ready, tier));
        out.add(FxMotifs.orbit(chest, X, Z, (1.2 + .3 * p) * w, 3, -time * .12, .22 + .1 * p, CYAN, 220 * ready, tier));
        out.add(FxMotifs.orbit(chest, X, Y, (1.5 + .4 * p) * w, 4, time * .1 + 1, .2 + .08 * p, WARM, 190 * ready, tier));
        out.add(FxMotifs.ring(up(body, .04), X, Z, (2.4 + .6 * p) * w, AIR, 130 * ready));
        out.add(FxMotifs.ring(chest, X, Z, (1.9 + .5 * p) * w, COOL, 140 * ready));
        out.add(FxMotifs.ring(chest, X, Y, (1.5 + .4 * p) * w, COOL, 60 * ready));
        Point contact = pt(s.point(3));
        double ab = s.number(3, -1);
        if (contact != null && ab >= 0) {
            double t = t01(ab / 8.0);
            Point dir = FxMotifs.unit(contact.minus(chest));
            if (dir != null) {
                Point sat = chest.plus(dir.scale(Math.min(contact.minus(chest).length(), (1.9 + .5 * p) * w + (contact.minus(chest).length() - 1.9) * t01(t * 2.2))));
                List<Stroke> dash = new ArrayList<>();
                glowLine(dash, chest.plus(dir.scale(1.9 * w)), sat, .12 * w, CYAN, 140 * tier.glow * (1 - t), .016, 235 * (1 - t));
                out.add(new FxLib.Parts(List.copyOf(dash), List.of(new Billboard(sat, .35 * w, 0xFFFFFF, FxLib.alpha(240 * tier.glow, 1 - t * .5), RailThunderArrayGrandGeometry.RADIAL, 0)), List.of()));
                out.add(new FxLib.Parts(List.of(), List.of(new Billboard(contact, (.8 + .8 * p) * w, AIR, FxLib.alpha(220 * tier.glow, 1 - t), RailThunderArrayGrandGeometry.STAR, t * 2)), List.of()));
                out.add(FxMotifs.ring(contact, X, Z, (.5 + 2 * t) * w, AIR, 190 * (1 - t)));
            }
        }
    }
}
