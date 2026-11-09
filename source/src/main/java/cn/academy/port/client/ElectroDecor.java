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
 * Per-skill presentation of the electromaster (blue-white lightning and magnetism) skills. Each skill has its own shape and the
 * big ones stage tech-style set pieces (hex grids, targeting reticles, scan rings, data packets, energy columns): twin charged helices
 * locked by a reticle (113), orthogonal circuit traces with travelling pulses (121), a sky-to-ground discharge column over a pulsing
 * hex grid with radial ground arcs (122), flickering
 * hologram slices (123), an iron-sand swarm spear (131), magnetic footprints and dust rails (132), curved pull lines with beads (133),
 * a lightning net over a hex-grid floor with packets between nodes (141), a forked arc with a lock-on impact (111),
 * a wrist-projected hex field plate (112) and a segmented shield gauge with discharge spokes (142). {@code power} (0.2..1)
 * blends the skill level and the caster level. Pure and deterministic.
 */
public final class ElectroDecor {
    public enum Skill { TETHER, CIRCUIT, GROUND, DECOY, LANCE, STEP, PULL, NET, BUFFER, ARC, BRACER }
    public static final int MAX_QUADS = 900;
    private static final int ELEC = 0x62BBFF, ICE = 0xDDF3FF, VIOLET = 0xA98BFF, GOLD = 0xFFE08A, SAND = 0xC8B684;
    private static final Point X = new Point(1, 0, 0), Y = new Point(0, 1, 0), Z = new Point(0, 0, 1);

    private ElectroDecor() {}

    public static double power(int skillLevel, double casterLevel) { return VectorDecor.power(skillLevel, casterLevel); }

    public static FxLib.Parts fx(Skill skill, DecorSnap s, double time, double power, VisualTier tier) {
        if (skill == null || s == null || tier == null || tier.layers < 3 || !Double.isFinite(time) || !Double.isFinite(power)) return FxLib.Parts.EMPTY;
        double p = Math.max(.2, Math.min(1, power));
        FxLib.Builder out = new FxLib.Builder();
        switch (skill) {
            case TETHER -> tether(out, s, time, p, tier);
            case CIRCUIT -> circuit(out, s, time, p, tier);
            case GROUND -> ground(out, s, time, p, tier);
            case DECOY -> decoy(out, s, time, p, tier);
            case LANCE -> lance(out, s, time, p, tier);
            case STEP -> step(out, s, time, p, tier);
            case PULL -> pull(out, s, time, p, tier);
            case NET -> net(out, s, time, p, tier);
            case BUFFER -> buffer(out, s, time, p, tier);
            case ARC -> arc(out, s, time, p, tier);
            case BRACER -> bracer(out, s, time, p, tier);
        }
        return out.build();
    }

    private static double sz(VisualTier tier) { return 1 + .12 * (tier.widthScale - 1); }
    private static double t01(double x) { return x != x ? 0 : Math.max(0, Math.min(1, x)); }
    private static double wrap(double x) { return ((x % 1) + 1) % 1; }
    private static Point up(Point a, double dy) { return a.plus(new Point(0, dy, 0)); }
    private static FxLib.Parts glow(Point at, double radius, int rgb, double alpha, int texture, double roll, VisualTier tier) {
        return new FxLib.Parts(List.of(), List.of(new Billboard(at, radius, rgb, FxLib.alpha(alpha * tier.glow, 1), texture, roll)), List.of());
    }

    // 113: a charged tether. Two helices of opposite polarity wind round the line, beads of charge run along it,
    // and a targeting reticle over a small hex plate shows where the field is locked.
    private static void tether(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(0)), a = pt(s.point(1)), n = FxMotifs.unit(pt(s.point(2)));
        if (!FxMotifs.ok(o, a) || o.minus(a).length() < 1e-3) return;
        double w = sz(tier), len = Math.min(a.minus(o).length(), 96);
        Point d = FxMotifs.unit(a.minus(o)), end = o.plus(d.scale(len));
        out.add(FxMotifs.helix(o, d, len, (.3 + .25 * p) * w, Math.max(2, len / 1.4), time * .35, false, ELEC, 220, tier));
        out.add(FxMotifs.helix(o, d, len, (.3 + .25 * p) * w, Math.max(2, len / 1.4), time * .35 + Math.PI, false, VIOLET, 220, tier));
        List<Stroke> core = new ArrayList<>();
        glowLine(core, o, end, .12 * w, ICE, 90 * tier.glow, .016, 200);
        out.add(new FxLib.Parts(List.copyOf(core), List.of(), List.of()));
        int beads = 5 + (int) (6 * p);
        List<Billboard> sprites = new ArrayList<>();
        for (int i = 0; i < beads; i++) {
            double t = wrap(time * .045 + i / (double) beads);
            sprites.add(new Billboard(o.plus(d.scale(len * t)), (.22 + .14 * p) * w, i % 2 == 0 ? ELEC : VIOLET, FxLib.alpha(235 * tier.glow, Math.sin(Math.PI * t)), RailThunderArrayGrandGeometry.RADIAL, 0));
        }
        out.add(new FxLib.Parts(List.of(), List.copyOf(sprites), List.of()));
        if (n != null) {
            Point[] fr = FxMotifs.frame(n);
            Point root = end.plus(n.scale(.04));
            out.add(FxMotifs.reticle(root, fr[1], fr[2], (.9 + .8 * p) * w, time * .02, ELEC, 225, tier));
            out.add(FxMotifs.hexGrid(root, fr[1], fr[2], .3 * w, 1, time * .04, ICE, 170, tier));
            out.add(FxLib.spokes(root, s.token(), (time * .5) % 10, 10, 8 + (int) (6 * p), (1.2 + 1.6 * p) * w, ICE, 170, tier));
            out.add(FxMotifs.forkTree(root, root.plus(n.scale(1.2 + p)).plus(fr[1].scale(.6)), s.token(), time * .5, 2, .35, ICE, 220, tier));
        }
        out.add(FxMotifs.ring(o, X, Z, (.5 + .3 * Math.sin(time * .3)) * w, ELEC, 160));
    }

    // 121: orthogonal circuit traces. A pulse runs from end to end; energised circuits light up white and arc.
    private static void circuit(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point a = pt(s.point(0)), b = pt(s.point(1));
        if (!FxMotifs.ok(a, b) || a.minus(b).length() < 1e-3) return;
        double w = sz(tier);
        boolean live = s.number(0, 0) == 1;
        double pulse = wrap(time * (live ? .06 : .03));
        out.add(FxMotifs.circuit(a, b, 3 + (int) Math.round(3 * p), s.token(), pulse, live ? ICE : ELEC, live ? 240 : 150, tier));
        for (Point end : new Point[] {a, b}) {
            out.add(FxMotifs.hexagon(up(end, .05), X, Z, Y, (.6 + .5 * p) * w, time * .02, 0, ELEC, live ? 230 : 140, tier));
            out.add(FxMotifs.ring(up(end, .04), X, Z, (1.0 + .8 * p) * w, ELEC, live ? 200 : 110));
            out.add(glow(up(end, .3), (.5 + .5 * p) * w, live ? ICE : ELEC, live ? 220 : 120, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
        }
        if (live) {
            out.add(FxLib.lightning(a, b, s.token(), time * .5, 14, .5 + .6 * p, .05 * w, 0xFFFFFF, ELEC, 1, tier));
            out.add(FxLib.sparkShower(a, Y, s.token(), (time * 1.5) % 12, 14, 5, 1.2, 8, 12, ICE, ELEC, tier));
            out.add(FxLib.sparkShower(b, Y, s.token() + 1, (time * 1.5) % 12, 14, 5, 1.2, 8, 12, ICE, ELEC, tier));
        }
    }

    // 122: grounding return. A column of light drops from the sky, a magic circle spins on the ground and arcs run out across it.
    private static void ground(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point g = pt(s.point(0));
        if (g == null) return;
        double w = sz(tier), burst = s.number(2, 0), phase = s.number(0, 0);
        Point base = up(g, .05);
        out.add(FxLib.groundGlow(base, (1.6 + 1.2 * p) * w, ELEC, 90, (.8 + .5 * Math.sin(time * .2)) * w, 120, tier));
        out.add(FxMotifs.hexGrid(base, X, Z, (.45 + .2 * p) * w, 2, time * .03, ELEC, phase >= 1 ? 220 : 140, tier));
        if (burst > 0) {
            double t = t01(1 - burst / 12.0), f = 1 - t;
            out.add(FxMotifs.pillar(base, 26 + 26 * p, (.3 + .4 * p) * w * (.6 + .4 * f), ELEC, 190 * (.45 + .55 * f), tier));
            out.add(FxMotifs.scanRings(base, 26 + 26 * p, (1.2 + .8 * p) * w, 5, time, .04, ICE, 200 * f + 40, tier));
            out.add(FxMotifs.hexGrid(base, X, Z, (.7 + .5 * p) * w * (.5 + .5 * t), 3, t, ICE, 220 * f + 30, tier));
            for (int k = 0; k < 3; k++) out.add(FxMotifs.ring(base, X, Z, (1 + (5 + 7 * p) * t) * (1 - k * .2) * w, k == 0 ? ICE : ELEC, 220 * f * (1 - k * .25)));
            int arms = 5 + (int) Math.round(4 * p);
            for (int i = 0; i < arms; i++) {
                double ang = i * Math.PI * 2 / arms + .3, len = (3 + 5 * p) * (.4 + .6 * t) * w;
                out.add(FxMotifs.forkTree(base, base.plus(new Point(Math.cos(ang) * len, .05, Math.sin(ang) * len)), s.token() + i, time * .5, 2, .5, ICE, 230 * (.4 + .6 * f), tier));
            }
            out.add(FxLib.sparkShower(base, Y, s.token(), (1 - f) * 12, 22, 7, 1.5, 9, 14, ICE, ELEC, tier));
            out.add(FxLib.lensStreak(up(base, .4), (4 + 4 * p) * w, ICE, 120, f, tier));
        }
    }

    // 123: charge decoy. Hologram slices of the caster trail to the decoy, flickering, and a scanned column marks where it stands.
    private static void decoy(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(0)), d = pt(s.point(1));
        if (!FxMotifs.ok(o, d)) return;
        double w = sz(tier), preview = s.number(0, 0) == 0 ? .55 : 1;
        Point axis = FxMotifs.unit(d.minus(o));
        int slices = 3 + (int) Math.round(4 * p);
        Point side = axis == null ? X : FxMotifs.unit(axis.cross(Y));
        if (side == null) side = X;
        for (int i = 0; i < slices; i++) {
            double t = (i + 1.0) / (slices + 1.0);
            double flicker = ((long) Math.floor(time * .5 + i * 3.1) % 3 == 0) ? .35 : 1;
            out.add(FxMotifs.panel(o.plus(d.minus(o).scale(t)).plus(new Point(0, 1, 0)), side, Y, .42 * w, 1.0 * w, 0, i % 2 == 0 ? VIOLET : ELEC, 170 * flicker * preview, tier));
        }
        out.add(FxMotifs.reticle(up(d, .05), X, Z, (1.0 + .6 * p) * w, time * .05, VIOLET, 210 * preview, tier));
        for (int k = 0; k < 4; k++) {
            double y = wrap(time * .03 + k / 4.0) * 2.0;
            out.add(FxMotifs.ring(up(d, y), X, Z, (.55 + .12 * Math.sin(y * 3)) * w, ICE, 190 * preview * (1 - y / 2.4)));
        }
        out.add(FxLib.embers(d, .8 * w, 2.6, s.token(), time, 10 + (int) (8 * p), ICE, VIOLET, tier));
        out.add(FxMotifs.helix(d, Y, 2.2, .5 * w, 2.5, time * .2, false, VIOLET, 150 * preview, tier));
    }

    // 131: iron sand lance. Each spear is a swarm of sand grains held together by a thin blue field line; an orbit of grains circles the caster.
    private static void lance(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(0));
        if (o == null) return;
        double w = sz(tier);
        boolean orbit = s.number(0, 0) == 1;
        double charge = t01(s.number(1, 0) / 20.0);
        if (orbit) {
            out.add(FxMotifs.orbit(up(o, 1.2), X, Z, (1.4 + .6 * p) * w, 14, time * .1, .12 + .06 * p, SAND, 220, tier));
            out.add(FxMotifs.orbit(up(o, 1.2), X, Y, (1.1 + .4 * p) * w, 10, -time * .13, .1 + .05 * p, SAND, 200, tier));
            out.add(FxMotifs.gauge(up(o, .05), X, Z, (1.8 + 1.0 * p) * w, 16, (int) Math.round(16 * charge), GOLD, 190, tier));
        }
        FxLib.Rng rng = new FxLib.Rng(s.token() * 31 + (long) Math.floor(time * .5));
        for (int i = 3, k = 0; i < s.numbers().length; i++, k++) {
            Point tail = pt(s.point(1 + 2 * k)), head = pt(s.point(2 + 2 * k));
            if (!FxMotifs.ok(tail, head) || head.minus(tail).length() < 1e-3) continue;
            Point axis = FxMotifs.unit(head.minus(tail));
            Point[] fr = FxMotifs.frame(axis);
            List<Stroke> shaft = new ArrayList<>();
            glowLine(shaft, tail, head, .1 * w, ELEC, 90 * tier.glow, .014, 170);
            List<Billboard> grains = new ArrayList<>();
            double len = head.minus(tail).length();
            int count = 26 + (int) (30 * p);
            for (int g = 0; g < count; g++) {
                double t = (g + rng.next()) / count, taper = Math.sin(Math.PI * Math.min(1, t * 1.1)), ang = rng.next() * Math.PI * 2, r = (.1 + .35 * taper * (1 - .5 * t)) * w * (.5 + .5 * rng.next());
                Point at = tail.plus(axis.scale(len * t)).plus(fr[1].scale(Math.cos(ang) * r)).plus(fr[2].scale(Math.sin(ang) * r));
                grains.add(new Billboard(at, (.05 + .05 * rng.next()) * w, g % 5 == 0 ? GOLD : SAND, FxLib.alpha(230 * tier.glow, .5 + .5 * taper), RailThunderArrayGrandGeometry.RADIAL, 0));
            }
            out.add(new FxLib.Parts(List.copyOf(shaft), List.copyOf(grains), List.of()));
            out.add(glow(head, (.45 + .3 * p) * w, ICE, 230, RailThunderArrayGrandGeometry.STAR, time * .1, tier));
            double status = s.number(i, 0);
            if (status >= 2 && status != 3) {
                out.add(FxLib.sparkShower(head, Y, s.token() + k, (time * 1.5) % 10, 16, 6, 1.3, 8, 12, GOLD, SAND, tier));
                out.add(FxMotifs.ring(up(head, .04), X, Z, (.8 + 1.4 * p) * w, SAND, 170));
            }
        }
    }

    // 132: magnetic ground step. Footprint rings and a dust rail mark the slide; the deflection kicks a white arc.
    private static void step(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point a = pt(s.point(0)), b = pt(s.point(1));
        if (!FxMotifs.ok(a, b) || a.minus(b).length() < 1e-3) return;
        double w = sz(tier);
        Point d = FxMotifs.unit(b.minus(a)), len = b.minus(a);
        for (Point end : new Point[] {a, b}) {
            out.add(FxMotifs.ring(up(end, .04), X, Z, (.8 + .6 * p) * w, ELEC, 200));
            out.add(FxMotifs.hexagon(up(end, .05), X, Z, Y, (.5 + .3 * p) * w, time * .03, 0, ICE, 190, tier));
            out.add(FxLib.embers(end, .7 * w, 1.4, s.token() + (end == a ? 0 : 1), time, 8 + (int) (8 * p), SAND, ELEC, tier));
        }
        out.add(FxMotifs.chevrons(up(a, .1), d, Math.min(len.length(), 40), 4 + (int) Math.round(4 * p), (.3 + .2 * p) * w, time * .15, ICE, 215, tier));
        List<Stroke> rail = new ArrayList<>();
        glowLine(rail, up(a, .06), up(b, .06), .16 * w, ELEC, 100 * tier.glow, .02, 190);
        out.add(new FxLib.Parts(List.copyOf(rail), List.of(), List.of()));
        out.add(FxLib.dispersion(up(a, .2), up(b, .2), .05 * w, 130, tier));
        Point defl = pt(s.point(2));
        if (s.number(2, 0) == 1 && defl != null && FxMotifs.unit(defl) != null) {
            Point dd = FxMotifs.unit(defl);
            out.add(FxMotifs.forkTree(b, b.plus(dd.scale(3 + 3 * p)), s.token(), time * .5, 2, .6, ICE, 235, tier));
            out.add(glow(b, (.9 + .8 * p) * w, ICE, 220, RailThunderArrayGrandGeometry.STAR, time * .1, tier));
        }
    }

    // 133: magnetic item pull. Every item is reeled to the hand along a curved line with beads running in; the hand holds a small lock-on reticle.
    private static void pull(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point hand = pt(s.point(0));
        if (hand == null) return;
        double w = sz(tier);
        Point[] fr = FxMotifs.frame(Y);
        out.add(FxMotifs.reticle(hand, fr[1], fr[2], (.4 + .2 * p) * w, time * .08, ELEC, 225, tier));
        out.add(glow(hand, (.5 + .3 * p) * w, ICE, 230, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
        for (int i = 1; i < s.points().size() && i <= 12; i++) {
            Point item = pt(s.point(i));
            if (item == null) continue;
            Point mid = item.lerp(hand, .5).plus(new Point(0, .6 + .5 * Math.sin(i), 0));
            List<Stroke> line = new ArrayList<>();
            List<Billboard> beads = new ArrayList<>();
            Point prev = item;
            for (int k = 1; k <= 12; k++) {
                double t = k / 12.0, u = 1 - t;
                Point cur = item.scale(u * u).plus(mid.scale(2 * u * t)).plus(hand.scale(t * t));
                glowLine(line, prev, cur, .16 * w, ELEC, 210 * tier.glow, .022, Math.min(255, 205 + 50 * t));
                prev = cur;
            }
            for (int k = 0; k < 4; k++) {
                double t = wrap(time * .05 + k / 4.0 + i * .13), u = 1 - t;
                beads.add(new Billboard(item.scale(u * u).plus(mid.scale(2 * u * t)).plus(hand.scale(t * t)), .13 * w, ICE, FxLib.alpha(235 * tier.glow, Math.sin(Math.PI * t)), RailThunderArrayGrandGeometry.RADIAL, 0));
            }
            out.add(new FxLib.Parts(List.copyOf(line), List.copyOf(beads), List.of()));
            out.add(FxMotifs.ring(up(item, .04), X, Z, (.4 + .1 * Math.sin(time * .3 + i)) * w, ELEC, 160));
        }
    }

    // 141: thunder net. Nodes are tied to each other and to the caster by live lightning, a hex-grid floor and an energy column root the net, and data packets run between nodes.
    private static void net(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(0)), anchor = pt(s.point(1));
        if (o == null) return;
        double w = sz(tier), age = t01(s.number(1, 0) / 20.0);
        Point root = anchor != null ? anchor : o;
        out.add(FxMotifs.hexGrid(up(root, .05), X, Z, (.9 + .6 * p) * w * (.4 + .6 * age), 3, time * .02, ELEC, 210, tier));
        out.add(FxMotifs.reticle(up(root, .06), X, Z, (2.5 + 2 * p) * w, time * .01, ICE, 200, tier));
        out.add(FxMotifs.scanRings(up(root, .05), 14 + 12 * p, (1.0 + .6 * p) * w, 4, time, .05, ICE, 190, tier));
        out.add(FxMotifs.pillar(up(root, .05), 14 + 12 * p, (.35 + .4 * p) * w, ELEC, 150 + 60 * age, tier));
        Point prev = o;
        for (int i = 2; i < s.points().size() && i < 14; i++) {
            Point node = pt(s.point(i));
            if (node == null) continue;
            Point at = up(node, 1.0);
            out.add(FxMotifs.forkTree(up(o, 1.2), at, s.token() + i, time * .5, 2, .5 + .3 * p, ICE, 215, tier));
            if (prev != o) { out.add(FxLib.lightning(prev, at, s.token() * 3 + i, time * .5, 10, .4 + .4 * p, .04 * w, 0xFFFFFF, ELEC, 1, tier));
                out.add(FxMotifs.packets(prev, at, 3, time, .05, .8 * w, ICE, 220, tier)); }
            out.add(FxMotifs.hexagon(up(node, .05), X, Z, Y, (.7 + .4 * p) * w, time * .03 + i, 0, ELEC, 210, tier));
            out.add(glow(at, (.7 + .6 * p) * w, ICE, 230, RailThunderArrayGrandGeometry.STAR, time * .1 + i, tier));
            out.add(FxLib.spokes(at, s.token() + i, (time * .5 + i) % 8, 8, 6, (1.0 + 1.0 * p) * w, ICE, 170, tier));
            prev = at;
        }
    }

    // 142: potential buffer. A segmented gauge shield stands in front of the body; every hit discharges as spokes and forking arcs.
    private static void buffer(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point body = pt(s.point(0)), raw = pt(s.point(1)), impact = pt(s.point(2));
        if (body == null || raw == null) return;
        Point face = FxMotifs.unit(new Point(raw.x(), 0, raw.z()));
        if (face == null) return;
        Point[] fr = FxMotifs.frame(face);
        double w = sz(tier), breathe = .75 + .25 * Math.sin(time * .3);
        Point chest = up(body, 1.15).plus(face.scale(1.3));
        int filled = 12 - (int) Math.min(6, Math.max(0, s.number(0, 0) * 2));
        out.add(FxMotifs.gauge(chest, fr[1], fr[2], (1.5 + .7 * p) * w, 12, Math.max(4, filled), ELEC, 220 * breathe, tier));
        out.add(FxMotifs.gauge(chest, fr[1], fr[2], (1.0 + .4 * p) * w, 8, 8, ICE, 190 * breathe, tier));
        out.add(FxMotifs.ring(up(body, .05), X, Z, (1.8 + .8 * p) * w, ELEC, 150));
        out.add(glow(chest, (.8 + .4 * p) * w, ELEC, 90 * breathe, RailThunderArrayGrandGeometry.RADIAL, 0, tier));
        if (impact != null && impact.minus(body).length() < 12 && impact.length() > 1e-6) {
            double flash = .55 + .45 * Math.sin(time * .9);
            out.add(glow(impact, (.9 + .9 * p) * w, ICE, 230 * flash, RailThunderArrayGrandGeometry.STAR, time * .2, tier));
            out.add(FxLib.spokes(impact, s.token(), (time * .7) % 9, 9, 12, (1.6 + 1.8 * p) * w, ICE, 200, tier));
            for (int i = 0; i < 4; i++) {
                double ang = i * Math.PI / 2 + time * .05;
                out.add(FxMotifs.forkTree(impact, impact.plus(fr[1].scale(Math.cos(ang) * (1.5 + 1.5 * p))).plus(fr[2].scale(Math.sin(ang) * (1.5 + 1.5 * p))), s.token() + i, time * .5, 2, .4, ICE, 230, tier));
            }
        }
    }

    // 111: forked arc. Data packets race down the bolt, the struck point gets a closing lock-on reticle and a hex flash,
    // and a small charge gauge marks the emitter.
    private static void arc(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point o = pt(s.point(0)), e = pt(s.point(1));
        if (!FxMotifs.ok(o, e) || e.minus(o).length() < 1e-3) return;
        double w = sz(tier), t = t01(s.age() / 10.0), f = 1 - t;
        Point[] fr = FxMotifs.frame(e.minus(o));
        out.add(FxMotifs.packets(o, e, 3 + (int) Math.round(3 * p), time, .18, (.6 + .4 * p) * w, ICE, 230 * f + 25, tier));
        out.add(FxLib.dispersion(o, e, .04 * w, 120 * f + 10, tier));
        out.add(FxMotifs.reticle(e, fr[1], fr[2], (.45 + .35 * p) * w * (1.5 - .6 * t), t * 1.6, ICE, 230 * f + 25, tier));
        out.add(FxMotifs.hexGrid(e, fr[1], fr[2], (.16 + .06 * p) * w, 1, t, ELEC, 220 * f + 20, tier));
        out.add(FxLib.spokes(e, s.token(), s.age(), 10, 6 + (int) (4 * p), (.7 + .7 * p) * w, ICE, 180, tier));
        out.add(glow(e, (.35 + .3 * p) * w, ICE, 220 * f + 20, RailThunderArrayGrandGeometry.STAR, t, tier));
        out.add(FxMotifs.gauge(o, fr[1], fr[2], (.3 + .1 * p) * w, 6, 6 - (int) Math.round(5 * t), ELEC, 200 * f + 30, tier));
    }

    // 112: magnetic bracer. A wrist emitter with two charge gauges projects a hex-cell field plate ahead of the arm; field loops
    // curl back to the wrist and a deflected projectile gets a lock-on flash where it was turned aside.
    private static void bracer(FxLib.Builder out, DecorSnap s, double time, double p, VisualTier tier) {
        Point wrist = pt(s.point(0)), look = FxMotifs.unit(pt(s.point(1)));
        if (wrist == null || look == null) return;
        Point[] fr = FxMotifs.frame(look);
        double w = sz(tier), dim = Math.max(.2, Math.min(1, s.number(1, 1))), breathe = (.8 + .2 * Math.sin(time * .4)) * dim;
        Point plate = wrist.plus(look.scale(.9 + .2 * p));
        out.add(FxMotifs.hexGrid(plate, fr[1], fr[2], (.2 + .08 * p) * w, 2, time * .03, ELEC, 200 * breathe, tier));
        out.add(FxMotifs.reticle(plate, fr[1], fr[2], (.55 + .25 * p) * w, time * .02, ICE, 170 * breathe, tier));
        out.add(FxMotifs.gauge(wrist, fr[1], fr[2], .26 * w, 8, 1 + (int) (wrap(time * .05) * 8), ELEC, 220 * dim, tier));
        out.add(FxMotifs.gauge(wrist.plus(look.scale(.18)), fr[1], fr[2], .18 * w, 6, 6, ICE, 200 * dim, tier));
        Point mid = wrist.plus(look.scale(.45));
        out.add(FxMotifs.arc(mid, look, fr[1], .45 * w, 290, 22, .012, ELEC, 170 * breathe, tier));
        out.add(FxMotifs.arc(mid, look, fr[2], .45 * w, 290, 22, .012, VIOLET, 150 * breathe, tier));
        out.add(FxMotifs.packets(wrist, plate, 3, time, .07, .25 * w, ICE, 210 * dim, tier));
        double age = s.number(0, -1);
        Point hit = pt(s.point(2));
        if (age >= 0 && hit != null) {
            double t = t01(age / 10.0), f = 1 - t;
            Point[] hf = FxMotifs.frame(hit.minus(wrist)) == null ? fr : FxMotifs.frame(hit.minus(wrist));
            out.add(FxMotifs.reticle(hit, hf[1], hf[2], (.5 + .3 * p) * w * (1.4 - .5 * t), t * 2, ICE, 235 * f + 20, tier));
            out.add(FxLib.spokes(hit, s.token(), age, 10, 8, (.8 + .8 * p) * w, ICE, 190, tier));
            out.add(FxMotifs.hexGrid(plate, fr[1], fr[2], (.2 + .08 * p) * w, 2, t, ICE, 240 * f, tier));
        }
    }
}
