package cn.academy.port.client;

import static cn.academy.port.client.MotifKit.*;

import cn.academy.port.client.MotifRenderer.Tex;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import java.util.ArrayList;
import java.util.Random;

/**
 * Meltdowner motifs: turbulent plasma, focused beams, hexagonal shields, jets with shock diamonds, mirror panes and sparkling scatter.
 * Palette is the only thing shared with the other motifs of the category.
 */
final class MdMotifs {
    private MdMotifs() {}

    private static final Point UP = new Point(0, 1, 0);

    /** A ball of electrons gathers, flies to the target as a comet, and bursts as a sphere of rings. */
    static void electronBomb(B b, M m) {
        Point c = m.at(.5, 0, 0);
        if (m.held()) { converge(b, m, c, .75, 10, 12, m.main()); return; }
        double t = m.since();
        Point end = m.reach(26);
        double dist = end.minus(c).length(), tf = Math.max(3, Math.min(9, dist / 4.0));
        Point dir = norm(end.minus(c));
        if (t < tf) {
            double k = t / tf;
            Point head = c.lerp(end, k);
            double r = .12 + .18 * k;
            b.streak(head.minus(dir.scale(Math.min(2.8, dist * k))), head, .09, m.main(), 230);
            b.orb(head, r, m.main(), 255, m.seed());
            for (int i = 0; i < 6; i++) {
                double a = i * 1.05 + t * 1.3, rr = r * (1.1 + .2 * Math.sin(t + i));
                Point[] pp = perp(dir);
                b.mote(head.plus(pp[0].scale(Math.cos(a) * rr)).plus(pp[1].scale(Math.sin(a) * rr)), .03, m.accent(), 220);
            }
            flash(b, m, c, .1, m.main());
            return;
        }
        double tb = t - tf;
        if (tb > 30) return;
        M after = m.later(tf);
        double size = .45 + .12 * m.lv();
        b.orb(end, size * (.4 + .9 * punch(tb)), m.main(), 255 * Math.min(1, punch(tb) + .1), m.seed());
        b.flare(end, size * 5 * punch(tb), m.main(), 220);
        Point[] ax = {new Point(1, 0, 0), new Point(0, 1, 0), new Point(0, 0, 1)};
        for (int i = 0; i < 3; i++) shockRing(b, after, end, ax[i], ax[(i + 1) % 3], size * 4.5, i * .8, 18, i == 0 ? 0xFFFFFF : m.main());
        burst(b, end, 8, size * 3.5 * easeOut(tb / 6.0), m.seed() + 2, tb * .02, m.main(), 220 * after.fade(16));
        b.add(FxLib.sparkShower(end, UP, m.seed() + 4, tb, 18 + 4 * m.lv(), 6, 2.6, 8, 18, 0xFFFFFF, m.main(), m.tier()));
    }

    /** A cylinder of vertical light blades turning around the body, between two scanning rings. */
    static void lightShield(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(36);
        if (on <= 0) return;
        double R = .95;
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + m.time() * .035;
            Point tan = new Point(-Math.sin(a), 0, Math.cos(a));
            Point c = m.feet().plus(new Point(Math.cos(a) * R, 1.0, Math.sin(a) * R));
            b.plate(c, tan.scale(.3), new Point(0, .95, 0), m.main(), 200 * on, Tex.SHARD);
            b.line(c.plus(new Point(0, -.95, 0)), c.plus(new Point(0, .95, 0)), .008, m.accent(), 120 * on);
        }
        for (int k = 0; k < 2; k++) {
            double y = k == 0 ? .08 : 2.0;
            b.ring(m.feet().plus(new Point(0, y, 0)), new Point(1, 0, 0), new Point(0, 0, 1), R * 1.05, m.main(), 190 * on, m.time() * .05 * (k == 0 ? 1 : -1));
        }
        double sweep = (m.time() * .02) % 1;
        b.ring(m.feet().plus(new Point(0, .1 + 1.85 * sweep, 0)), new Point(1, 0, 0), new Point(0, 0, 1), R * 1.02, 0xFFFFFF, 150 * on * Math.sin(Math.PI * sweep), 0);
    }

    /** The big beam: a spinning intake vortex at the muzzle, a thick tapering beam with a white core, a molten crater at the end. */
    static void meltdowner(B b, M m) {
        Point c = m.at(.8, 0, 0);
        if (m.held()) {
            double ch = easeIn(m.age() / 16.0);
            converge(b, m, c, 1.5, 16, 16, m.main());
            disc(b, c, m.s(), m.u(), .35 + .5 * ch, m.time() * .25, Tex.VORTEX, m.main(), 220 * ch);
            disc(b, c, m.s(), m.u(), .2 + .3 * ch, -m.time() * .4, Tex.VORTEX, m.accent(), 200 * ch);
            return;
        }
        Point end = m.reach(40);
        double t = m.since();
        if (t > 44) return;
        disc(b, c, m.s(), m.u(), .6 * Math.exp(-t / 5), t * .3, Tex.VORTEX, m.main(), 220 * Math.exp(-t / 6));
        flash(b, m, c, .38, m.main());
        beamCore(b, m, c, end, .09 + .03 * m.lv(), 14);
        Point g = m.ground();
        if (t > 2) {
            double k = m.fade(40);
            b.groundGlow(g, 2.2 + m.lv() * .3, m.main(), 140 * k);
            cracks(b, g, m.seed() + 6, 2.2, 6, Math.min(1, (t - 2) / 6.0), m.main(), 200 * k);
            impact(b, m, end, m.normal(), .45, m.main());
        }
        grand(b, m, g, 2.4, m.main());
    }

    /** Mining rays: a steady thin ray (basic), a focused ray wound by a double helix (expert), or a ray wrapped in sparkle with a fortune wheel at the hit (luck). */
    static void mineRay(B b, M m, int variant) {
        double on = m.held() ? easeOut(m.age() / 4.0) : m.fade(8);
        if (on <= 0) return;
        Point tip = m.reach(24);
        Point dir = norm(tip.minus(m.hand()));
        double w = variant == 0 ? .022 : variant == 1 ? .04 : .03;
        b.beam(m.hand(), tip, w, m.main(), 235 * on);
        for (int k = 0; k < 3; k++) comet(b, m.hand().lerp(tip, ((m.time() * .06 + k / 3.0) % 1 + 1) % 1), dir, .8, 0xFFFFFF, 190 * on);
        double flick = flicker(m.seed(), m.time());
        b.orb(tip, .1 * flick + .06, m.main(), 230 * on, m.seed());
        Random r = new Random(m.seed() + (long) (m.time() * .7));
        Point[] pp = perp(m.normal());
        Point[] rp = perp(dir);
        for (int i = 0; i < 5; i++) {   // fragments thrown from the hit spot, each a tumbling sliver
            double ph = ((m.time() * .05 + i * .2) % 1 + 1) % 1;
            Point v = pp[0].scale(Math.cos(i * 1.7) * 0.9).plus(pp[1].scale(Math.sin(i * 1.7) * 0.9)).plus(m.normal().scale(.8 + .4 * r.nextDouble()));
            b.sprite(tip.plus(v.scale(ph * .9)).plus(new Point(0, -ph * ph * .5, 0)), .05, m.main(), 220 * on * (1 - ph), Tex.SHARD, i + m.time() * .2, 1);
        }
        b.add(FxLib.embers(tip, .25, .8, m.seed(), m.time(), 4, 0xF0FFF2, m.main(), m.tier()));
        if (variant >= 1) {
            double len = tip.minus(m.hand()).length();
            helix(b, m.hand(), tip, .17, Math.max(3, len * .6), m.time() * .3, m.main(), 220 * on, .022);
            helix(b, m.hand(), tip, .17, Math.max(3, len * .6), m.time() * .3 + Math.PI, 0xFFFFFF, 200 * on, .016);
            for (int k = 0; k < 3; k++) {   // focus rings squeezing the beam near the muzzle
                double ph = ((m.time() * .05 + k / 3.0) % 1);
                b.ring(m.hand().plus(dir.scale(.2 + 1.1 * ph)), rp[0], rp[1], .45 * (1 - ph) + .06, m.main(), 220 * on * Math.sin(Math.PI * ph), k);
            }
        }
        if (variant == 2) {
            for (int i = 0; i < 4; i++) {
                double ph = ((m.time() * .03 + i * .25) % 1);
                Point p = m.hand().lerp(tip, ph);
                b.sprite(p, .35, 0xFFFFFF, 240 * on * Math.sin(Math.PI * ph), Tex.SPARKLE, m.time() * .08 + i, 1);
            }
            Point[] ring = perp(dir);
            b.ring(tip, ring[0], ring[1], .95, m.accent(), 210 * on, m.time() * .08);
            for (int i = 0; i < 5; i++) {
                double a = m.time() * .08 + i * Math.PI * 2 / 5;
                Point d = tip.plus(ring[0].scale(Math.cos(a) * .95)).plus(ring[1].scale(Math.sin(a) * .95));
                b.sprite(d, .2, i % 2 == 0 ? m.main() : 0xFFFFFF, 230 * on, Tex.SPARKLE, a, 1);
            }
            if (m.since() >= 0) grand(b, m, m.ground(), 2.4, m.main());
        }
    }

    /** A fan of beams released one after another, each from its own muzzle point, each landing with its own flash. */
    static void rayBarrage(B b, M m) {
        Point c = m.at(.6, 0, 0);
        if (m.held()) {
            converge(b, m, c, 1.1, 12, 14, m.main());
            for (int i = -3; i <= 3; i++) b.mote(c.plus(m.s().scale(i * .14)).plus(m.f().scale(.1)), .04, m.accent(), 160 * easeIn(m.age() / 14.0));
            return;
        }
        Point tip = m.reach(30);
        for (int i = 0; i < 7; i++) {
            double delay = i * .8;
            if (m.since() < delay) continue;
            M sub = m.later(delay);
            Point origin = m.hand().plus(m.s().scale((i - 3) * .12)).plus(m.f().scale(.5));
            Point target = tip.plus(m.s().scale((i - 3) * 2.6)).plus(m.u().scale(Math.sin(i * 2.1) * 1.8));
            beamCore(b, sub, origin, target, .06, 11);
            spot(b, sub, target, UP, .2, m.main());
        }
        flash(b, m, c, .3, m.main());
        grand(b, m, m.ground(), 3.0, m.main());
    }

    /** A rear thrust plume with a train of shock diamonds, heat rings rolling off it and dust kicked up from the ground. */
    static void jetEngine(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(30);
        if (on <= 0) return;
        Point back = m.flat().scale(-1);
        Point origin = m.feet().plus(new Point(0, 1.0, 0)).plus(back.scale(.35));
        double len = (2.6 + 1.2 * m.lvf() * 2) * on * (.9 + .1 * flicker(m.seed(), m.time()));
        plume(b, origin, back.scale(-1), len, m.main(), 230 * on, m.time());
        for (int i = 0; i < 6; i++) {
            double d = .5 + i * len / 6.5;
            double r = .13 * (1 - i / 7.0);
            b.orb(origin.plus(back.scale(d)), r, i % 2 == 0 ? 0xFFFFFF : m.main(), 210 * on * (1 - i / 7.5), m.seed() + i);
        }
        cone(b, origin, back, len, .22, 4, m.time(), .035, m.deep(), 160 * on);
        double gd = m.feet().y() + .1;
        Point scorch = new Point(origin.x() + back.x() * 1.6, gd, origin.z() + back.z() * 1.6);
        b.groundGlow(scorch, 1.1, m.main(), 110 * on);
        if (m.lv() >= 4) dust(b, m, scorch, 1.5, 4, 0xC8D4E4);
        if (m.since() >= 0 && m.since() < 6) b.flare(origin, 2.2 * punch(m.since()), m.main(), 220);
    }

    /** Orbiting electron missiles leave the shoulder one after another on spiral paths and detonate together. */
    static void electronMissile(B b, M m) {
        Point c = m.chest().plus(new Point(0, .35, 0));
        int n = 6;
        if (m.held()) {
            double ch = easeOut(m.age() / 16.0);
            int shown = Math.min(n, 1 + (int) (m.age() / 3.0));
            b.ring(c, m.s(), m.f(), .8, m.main(), 130 * ch, m.time() * .04);
            for (int i = 0; i < shown; i++) {
                double a = m.time() * .12 + i * Math.PI * 2 / n;
                Point p = c.plus(m.s().scale(Math.cos(a) * .8)).plus(m.f().scale(Math.sin(a) * .8)).plus(m.u().scale(.15 * Math.sin(a * 2)));
                b.orb(p, .09, m.main(), 230 * ch, m.seed() + i);
            }
            return;
        }
        Point tip = m.reach(32);
        double fly = 11;
        for (int i = 0; i < n; i++) {
            double start = i * 1.3, tk = m.since() - start;
            if (tk < 0) continue;
            double a0 = m.time() * .12 + i * Math.PI * 2 / n;
            Point p0 = c.plus(m.s().scale(Math.cos(a0) * .8)).plus(m.f().scale(Math.sin(a0) * .8));
            Point target = tip.plus(m.s().scale(Math.cos(i * 1.9) * 1.3)).plus(m.u().scale(Math.sin(i * 1.9) * 1.0));
            Point ctrl = p0.lerp(target, .5).plus(m.u().scale(2.5 + i * .3)).plus(m.s().scale((i - 2.5) * 1.1));
            double t = c01(tk / fly);
            if (t < 1) {
                var trail = new ArrayList<Point>();
                for (int k = 0; k <= 6; k++) {
                    double tt = Math.max(0, easeInOut(t) - k * .035);
                    trail.add(p0.scale((1 - tt) * (1 - tt)).plus(ctrl.scale(2 * tt * (1 - tt))).plus(target.scale(tt * tt)));
                }
                for (int k = 1; k < trail.size(); k++) b.streak(trail.get(k), trail.get(k - 1), .035 * (1 - k * .13), m.main(), 220 * (1 - k * .12));
                b.orb(trail.get(0), .1, m.main(), 255, m.seed() + i);
            } else {
                M sub = m.later(start + fly);
                spot(b, sub, target, UP, .26, m.main());
                shockRing(b, sub, target, new Point(1, 0, 0), new Point(0, 1, 0), 1.6, 0, 14, m.main());
                shockRing(b, sub, target, new Point(0, 0, 1), new Point(0, 1, 0), 1.6, 0, 14, m.main());
            }
        }
        grand(b, m.later(n * 1.3 + fly - 2), m.ground(), 3.4, m.main());
    }

    /** Two focus points hover where the beams will land; two beams converge on them and a link line pinches between. */
    static void dualPoint(B b, M m) {
        Point tip = m.reach(18);
        Point pa = tip.plus(m.s().scale(.8)), pb = tip.minus(m.s().scale(.8));
        if (m.held()) {
            double ch = easeOut(m.age() / 10.0);
            b.orb(pa, .08 + .05 * ch, m.main(), 200 * ch, m.seed());
            b.orb(pb, .08 + .05 * ch, m.main(), 200 * ch, m.seed() + 1);
            b.line(pa, pb, .008, m.accent(), 140 * ch);
            b.ring(pa, m.f(), m.u(), .25 - .1 * ch, m.main(), 170 * ch, m.time() * .1);
            b.ring(pb, m.f(), m.u(), .25 - .1 * ch, m.main(), 170 * ch, -m.time() * .1);
            return;
        }
        Point c = m.at(.4, 0, 0);
        flash(b, m, c, .15, m.main());
        beamCore(b, m, c, pa, .03, 9);
        beamCore(b, m, c, pb, .03, 9);
        double link = m.fade(14);
        b.beam(pa, pb, .02, m.accent(), 230 * link);
        b.flare(pa.lerp(pb, .5), 1.2 * punch(m.since()), m.main(), 200);
        spot(b, m, pa, UP, .12, m.main());
        spot(b, m, pb, UP, .12, m.main());
    }

    /** Four hex plates orbit the palm like loose scales, then fly out in a fan with streak tails. */
    static void scatterPlates(B b, M m) {
        Point c = m.at(.5, 0, 0);
        if (m.held()) {
            double ch = easeOut(m.age() / 8.0);
            for (int i = 0; i < 4; i++) {
                double a = m.time() * .1 + i * Math.PI / 2;
                Point p = c.plus(m.s().scale(Math.cos(a) * .3)).plus(m.u().scale(Math.sin(a) * .3));
                Point[] pp = perp(p.minus(c).length() < 1e-6 ? m.f() : p.minus(c));
                b.hex(p, pp[0].scale(1), pp[1].scale(1), .09, a, m.main(), 220 * ch);
            }
            return;
        }
        double t = m.since();
        if (t > 22) return;
        Point tip = m.reach(14);
        for (int i = 0; i < 4; i++) {
            double k = easeOut(t / 8.0);
            Point target = tip.plus(m.s().scale((i - 1.5) * 1.3)).plus(m.u().scale(((i % 2) - .5) * 1.2));
            Point p = c.lerp(target, k);
            Point dir = norm(target.minus(c));
            Point[] pp = perp(dir);
            b.streak(p.minus(dir.scale(1.4 * (1 - k * .7))), p, .035, m.main(), 200 * m.fade(20));
            b.hex(p, pp[0], pp[1], .1, t * .4 + i, m.main(), 230 * m.fade(22));
            if (k > .95) spot(b, m, target, UP, .1, m.main());
        }
        flash(b, m, c, .12, m.main());
    }

    /** A hair-thin cutting beam; after it lands a glowing line is engraved across the surface and stays lit while it cools. */
    static void beamEtching(B b, M m) {
        Point tip = m.reach(16);
        Point c = m.at(.4, 0, 0);
        if (m.held()) {
            double ch = easeOut(m.age() / 10.0);
            b.beam(c, tip, .012, m.main(), 150 * ch);
            b.orb(tip, .05 + .03 * flicker(m.seed(), m.time()), m.accent(), 220 * ch, m.seed());
            Point[] pp = perp(m.normal());
            b.circle(tip.plus(m.normal().scale(.03)), pp[0], pp[1], .5, 24, 0, Math.PI * 2, .006, m.main(), 90 * ch);
            return;
        }
        double t = m.since();
        if (t > 60) return;
        Point[] pp = perp(m.normal());
        double prog = easeInOut(t / 10.0), cool = Math.exp(-Math.max(0, t - 10) / 22.0);
        double span = Math.PI * 1.3, r = .75;
        Point prev = null;
        int segs = 28;
        for (int i = 0; i <= segs * prog; i++) {
            double a = -span / 2 + span * i / segs;
            Point q = tip.plus(m.normal().scale(.03)).plus(pp[0].scale(Math.cos(a) * r)).plus(pp[1].scale(Math.sin(a) * r * .8));
            if (prev != null) b.line(prev, q, .035, i > segs * prog - 2 ? 0xFFFFFF : m.main(), 230 * cool + 40);
            prev = q;
        }
        if (prev != null && t < 11) {
            b.beam(c, prev, .01, m.main(), 200 * m.fade(11));
            b.orb(prev, .08, 0xFFFFFF, 240, m.seed());
            b.add(FxLib.sparkShower(prev, m.normal(), m.seed(), t, 8, 3.5, 1.1, 9, 14, 0xFFFFFF, m.main(), m.tier()));
        }
        flash(b, m, c, .1, m.main());
    }

    /** Three mirror panes in the air; the beam bounces through them one after another, each pane flaring as it is hit. */
    static void foldedMirrorArray(B b, M m) {
        Point tip = m.reach(24);
        Point dir = norm(tip.minus(m.hand()));
        Point c = m.at(.4, 0, 0);
        double span = tip.minus(m.hand()).length();
        Point[] mirrors = new Point[3];
        for (int i = 0; i < 3; i++) {
            double d = span * (i + 1) / 4.0;
            mirrors[i] = m.hand().plus(dir.scale(d)).plus(m.s().scale((i % 2 == 0 ? 1 : -1) * 1.5)).plus(m.u().scale(.3 * (i - 1)));
        }
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(40);
        if (on <= 0) return;
        for (int i = 0; i < 3; i++) {
            Point toward = norm((i == 2 ? tip : mirrors[i + 1]).minus(mirrors[i]).plus(norm(mirrors[i].minus(i == 0 ? m.hand() : mirrors[i - 1]))));
            Point nrm = norm(toward);
            Point[] pp = perp(nrm);
            double hit = m.held() ? 0 : Math.exp(-Math.max(0, m.since() - i * 1.4) / 5.0) * (m.since() >= i * 1.4 ? 1 : 0);
            pane(b, mirrors[i], pp[0].scale(.55), pp[1].scale(.8), Tex.SCAN, m.main(), 150 * on + 90 * hit, 190 * on);
            if (hit > .05) b.flare(mirrors[i], 1.0 * hit, m.main(), 220 * hit);
        }
        if (m.held()) {
            b.line(m.hand(), mirrors[0], .008, m.main(), 120 * on);
            return;
        }
        Point prev = c;
        for (int i = 0; i < 4; i++) {
            Point next = i < 3 ? mirrors[i] : tip;
            if (m.since() >= i * 1.4) beamCore(b, m.later(i * 1.4), prev, next, .035, 9);
            prev = next;
        }
        spot(b, m.later(4.2), tip, UP, .2, m.main());
    }

    /** A ground-hugging light rail: the body is thrown along a ribbon of beam, ring afterimages left at each stride, speed lines behind. */
    static void beamStep(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 5.0) : m.fade(22);
        if (on <= 0) return;
        Point fl = m.flat();
        Point from = m.feet().plus(new Point(0, .9, 0)), to = from.plus(fl.scale(5.5 * (m.held() ? easeOut(m.age() / 6.0) : 1)));
        b.beam(from, to, .035, m.main(), 220 * on);
        b.beam(m.feet().plus(new Point(0, .08, 0)), m.feet().plus(fl.scale(5.5)).plus(new Point(0, .08, 0)), .02, m.main(), 160 * on);
        Point[] pp = perp(fl);
        for (int i = 0; i < 5; i++) {
            Point p = from.plus(fl.scale(i * 1.1 + .4));
            b.ring(p, pp[0], pp[1], .7 - i * .07, m.main(), 180 * on * (1 - i * .15), i);
        }
        speedLines(b, m.time(), m.feet().plus(new Point(0, 1.0, 0)).minus(fl.scale(.5)), fl.scale(-1), 3.5, 8, .5, .05, m.seed(), m.main(), 190 * on);
        b.groundGlow(m.feet(), 1.1, m.main(), 120 * on);
        if (m.since() >= 0 && m.since() < 6) b.flare(from, 1.6 * punch(m.since()), m.main(), 220);
    }

    /** Four stacked particle sheets stand in the beam path; a pulse runs through them and each sheet flashes as it passes. */
    static void particleLaminate(B b, M m) {
        Point tip = m.reach(26);
        Point dir = norm(tip.minus(m.hand()));
        Point[] pp = perp(dir);
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(36);
        if (on <= 0) return;
        for (int i = 0; i < 4; i++) {
            Point p = m.hand().plus(dir.scale(2.2 + i * 1.6));
            double size = .6 + i * .22;
            double hit = m.held() ? 0 : (m.since() >= i * 1.3 ? Math.exp(-(m.since() - i * 1.3) / 4.0) : 0);
            pane(b, p, pp[0].scale(size), pp[1].scale(size), Tex.HEX, m.main(), 120 * on + 110 * hit, 180 * on);
            b.ring(p, pp[0], pp[1], size, m.accent(), 140 * on * (.4 + hit), m.time() * .02 * (i % 2 == 0 ? 1 : -1));
            if (hit > .1) b.flare(p, 1.1 * hit, m.main(), 230 * hit);
        }
        if (m.held()) { b.line(m.hand(), m.hand().plus(dir.scale(9)), .008, m.main(), 110 * on); return; }
        flash(b, m, m.at(.4, 0, 0), .2, m.main());
        beamCore(b, m, m.at(.4, 0, 0), tip, .07, 14);
        impact(b, m, tip, m.normal(), .35, m.main());
        grand(b, m, m.ground(), 2.6, m.main());
    }

    /** A wide vertical curtain of light strands ahead, waving like an aurora with motes rising from its hem. */
    static void dissipationCurtain(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(40);
        if (on <= 0) return;
        Point fl = m.flat();
        Point side = norm(fl.cross(UP));
        Point base = m.feet().plus(fl.scale(2.6));
        double W = 3.2, H = 3.2 * on;
        b.blend(base.plus(new Point(0, H / 2, 0)), side.scale(W), new Point(0, H / 2, 0), m.main(), 90 * on, Tex.SCAN);
        b.plate(base.plus(new Point(0, H / 2, 0)), side.scale(W), new Point(0, H / 2, 0), m.main(), 80 * on, Tex.SCAN);
        for (int i = 0; i < 22; i++) {
            double x = -W + 2 * W * i / 21.0, wave = Math.sin(m.time() * .08 + i * .5) * .25 + Math.sin(m.time() * .05 + i * 1.3) * .12;
            Point bot = base.plus(side.scale(x)).plus(fl.scale(wave * .3));
            double h = H * (.7 + .3 * Math.sin(i * 1.9 + m.time() * .04));
            b.streak(bot, bot.plus(new Point(0, h, 0)).plus(fl.scale(wave)), .045, i % 3 == 0 ? m.accent() : m.main(), 220 * on);
        }
        b.line(base.minus(side.scale(W)).plus(new Point(0, .05, 0)), base.plus(side.scale(W)).plus(new Point(0, .05, 0)), .02, m.accent(), 200 * on);
        risers(b, base, W, H, 12, m.time(), m.seed(), m.accent(), 200 * on);
        if (m.lv() >= 4 && m.since() >= 0) grand(b, m, base, 2.2, m.main());
    }

    /** Tight hexagon armour: cells hug the body with no gap, brightness sweeps upward, rings flash at the shoulders on release. */
    static void zeroGapArmor(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(40);
        if (on <= 0) return;
        double R = .58;
        double sweep = (m.time() * .03) % 1;
        for (int row = 0; row < 5; row++) {
            double y = .2 + row * .4;
            int count = 8;
            for (int i = 0; i < count; i++) {
                double a = i * Math.PI * 2 / count + (row % 2) * Math.PI / count + m.time() * .003;
                Point nrm = new Point(Math.cos(a), 0, Math.sin(a));
                Point c = m.feet().plus(new Point(0, y, 0)).plus(nrm.scale(R));
                Point tan = new Point(-Math.sin(a), 0, Math.cos(a));
                double glow = .45 + .55 * Math.exp(-Math.pow((y / 2.0 - sweep) * 5, 2));
                b.blend(c, tan.scale(.3), new Point(0, .27, 0), m.main(), 170 * on * glow, Tex.HEX);
                b.plate(c, tan.scale(.3), new Point(0, .27, 0), m.accent(), 110 * on * glow, Tex.HEX);
            }
        }
        b.ring(m.feet().plus(new Point(0, .1, 0)), new Point(1, 0, 0), new Point(0, 0, 1), R * 1.3, m.main(), 170 * on, m.time() * .03);
        b.ring(m.feet().plus(new Point(0, 2.05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), R * 1.2, m.accent(), 170 * on, -m.time() * .03);
        if (m.since() >= 0) {
            shockRing(b, m, m.chest(), new Point(1, 0, 0), new Point(0, 0, 1), 2.2, 0, 16, m.main());
            grand(b, m, m.feet(), 2.8, m.main());
        }
    }

    /** All the energy at a pin point: a vortex pulls in, one needle beam pierces it, and the surface slumps into a glowing pool with drips. */
    static void pointMeltdown(B b, M m) {
        Point tip = m.reach(20);
        Point c = m.at(.4, 0, 0);
        if (m.held()) {
            double ch = easeIn(m.age() / 14.0);
            b.sprite(tip, .08 + .4 * ch, m.main(), 200 * ch, Tex.VORTEX, m.time() * .3, 1);
            b.orb(tip, .04 + .1 * ch, m.accent(), 230 * ch, m.seed());
            b.line(c, tip, .006, m.main(), 90 * ch);
            return;
        }
        double t = m.since();
        if (t > 50) return;
        beamCore(b, m, c, tip, .04, 10);
        flash(b, m, c, .12, m.main());
        double pool = easeOut(t / 14.0), cool = m.fade(48);
        Point[] pp = perp(m.normal());
        Point s = tip.plus(m.normal().scale(.04));
        b.blend(s, pp[0].scale(.2 + 1.0 * pool), pp[1].scale(.2 + 1.0 * pool), m.deep(), 200 * cool, Tex.BLOOM);
        b.plate(s, pp[0].scale(.15 + .9 * pool), pp[1].scale(.15 + .9 * pool), m.main(), 230 * cool, Tex.BLOOM);
        b.ring(s, pp[0], pp[1], .2 + .9 * pool, m.main(), 200 * cool, t * .05);
        b.sprite(tip, .5 * Math.exp(-t / 10), m.main(), 220, Tex.VORTEX, -t * .4, 1);
        for (int i = 0; i < 5; i++) {
            double a = i * 1.26, fall = ((t * .04 + i * .2) % 1);
            Point top = s.plus(pp[0].scale(Math.cos(a) * .35 * pool)).plus(pp[1].scale(Math.sin(a) * .35 * pool));
            b.streak(top, top.minus(UP.scale(.5 * fall)), .012, m.main(), 190 * cool * (1 - fall));
        }
        b.add(FxLib.embers(s, .5, 1.2, m.seed(), m.time(), 8, 0xF0FFF2, m.main(), m.tier()));
        if (m.lv() >= 4) grand(b, m, m.ground(), 2.0, m.main());
    }

    /** A tall relay beacon planted at the target: stacked rings spin around its mast and scatter beams fan out of its head. */
    static void remoteScatterRelay(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(46);
        if (on <= 0) return;
        Point g = m.ground();
        double H = 3.4 * on;
        pillar(b, g, H, .02, m.main(), 210 * on);
        for (int i = 0; i < 3; i++) {
            Point c = g.plus(new Point(0, H * (.35 + .27 * i), 0));
            b.ring(c, new Point(1, 0, 0), new Point(0, 0, 1), .55 - .1 * i, m.main(), 200 * on, m.time() * (.05 + .02 * i) * (i % 2 == 0 ? 1 : -1));
        }
        b.hex(g.plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 1.0, m.time() * .02, m.main(), 200 * on);
        Point head = g.plus(new Point(0, H + .15, 0));
        b.orb(head, .22 * flicker(m.seed(), m.time()) + .1, m.accent(), 230 * on, m.seed());
        if (!m.held()) {
            Point c = m.at(.4, 0, 0);
            beamCore(b, m, c, head, .04, 9);
            for (int i = 0; i < 6; i++) {
                if (m.since() < 4 + i * .6) continue;
                double a = i * Math.PI / 3 + .4;
                Point target = g.plus(new Point(Math.cos(a) * 6, .2, Math.sin(a) * 6));
                beamCore(b, m.later(4 + i * .6), head, target, .03, 8);
                spot(b, m.later(5 + i * .6), target, UP, .14, m.main());
            }
            grand(b, m.later(3), g, 3.6, m.main());
        }
    }

    /** A hexagonal locator plate lies on the ground; a crosshair is etched on it and a ping ring leaves it every half second. */
    static void scatterLocatorPlate(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(46);
        if (on <= 0) return;
        Point g = m.ground().plus(new Point(0, .04, 0));
        Point x = new Point(1, 0, 0), z = new Point(0, 0, 1);
        b.hex(g, x, z, .8, m.time() * .01, m.main(), 220 * on);
        b.line(g.minus(x.scale(1.1)), g.plus(x.scale(1.1)), .01, m.accent(), 190 * on);
        b.line(g.minus(z.scale(1.1)), g.plus(z.scale(1.1)), .01, m.accent(), 190 * on);
        double ph = (m.time() % 30) / 30.0;
        b.groundRing(m.ground(), 3.0 * easeOut(ph), m.main(), 200 * on * (1 - ph), 0);
        b.groundGlow(m.ground(), 1.0, m.main(), 100 * on);
        pillar(b, g, 1.6 + .4 * m.lvf(), .015, m.main(), 150 * on);
    }

    /** Twin forward jets from the shoulders, shock diamonds, chevrons pointing back along the ground and a skid of scorch. */
    static void reverseJetBrake(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 6.0) : m.fade(28);
        if (on <= 0) return;
        Point fl = m.flat();
        Point side = norm(fl.cross(UP));
        for (int sd = -1; sd <= 1; sd += 2) {
            Point origin = m.feet().plus(new Point(0, 1.45, 0)).plus(side.scale(.4 * sd)).plus(fl.scale(.3));
            double len = 2.2 * on * (.9 + .1 * flicker(m.seed() + sd, m.time()));
            plume(b, origin, fl.scale(-1), len, m.main(), 220 * on, m.time());
            for (int i = 0; i < 4; i++) b.orb(origin.plus(fl.scale(.4 + i * len / 4.5)), .08 * (1 - i / 5.0), 0xFFFFFF, 200 * on * (1 - i / 5.0), m.seed() + i);
        }
        Point g = m.feet().plus(fl.scale(1.0));
        chevronStream(b, g.plus(fl.scale(3.5)), g.minus(fl.scale(.5)), 4, .35, m.time(), .04, m.main(), 190 * on);
        b.line(m.feet().plus(new Point(0, .05, 0)).plus(side.scale(.35)), m.feet().plus(new Point(0, .05, 0)).plus(side.scale(.35)).minus(fl.scale(3)), .03, m.deep(), 150 * on);
        b.line(m.feet().plus(new Point(0, .05, 0)).minus(side.scale(.35)), m.feet().plus(new Point(0, .05, 0)).minus(side.scale(.35)).minus(fl.scale(3)), .03, m.deep(), 150 * on);
        b.groundGlow(m.feet().plus(fl.scale(1.8)), 1.2, m.main(), 120 * on);
        if (m.since() >= 0) grand(b, m, m.feet().plus(fl.scale(2.0)), 2.6, m.main());
    }
}
