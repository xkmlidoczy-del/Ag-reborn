package cn.academy.port.client;

import static cn.academy.port.client.MotifKit.*;

import cn.academy.port.client.MotifRenderer.Tex;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import java.util.Random;

/**
 * Teleporter motifs: tears in space, doorways, coordinate frames and lattices, ghost copies, tactical grids. The teleports themselves
 * (departure afterimage, path, arrival burst) are drawn by {@link TeleportTrace}; these motifs are what the casting looks like.
 */
final class TpMotifs {
    private TpMotifs() {}

    private static final Point UP = new Point(0, 1, 0);

    /** A narrow rift opens ahead while aiming; on release it snaps shut with a ring of blade-like spikes at the arrival. */
    static void threateningTeleport(B b, M m) {
        Point tgt = m.reach(8);
        Point ground = m.ground();
        Point face = m.flat().scale(-1);
        if (m.held()) {
            double ch = easeOut(m.age() / 8.0);
            rift(b, ground, face, 2.6, 1.3, ch, m.main(), 230);
            b.groundRing(ground, .7 + .3 * ch, m.main(), 170 * ch, m.time() * .05);
            for (int i = 0; i < 4; i++) {
                double a = i * Math.PI / 2 + .4, len = (.3 + .35 * ch);
                b.streak(tgt.plus(new Point(0, .1, 0)), tgt.plus(new Point(Math.cos(a) * len, Math.sin(a) * len + .1, 0)).plus(m.s().scale(Math.cos(a) * len)), .012, m.accent(), 180 * ch);
            }
            return;
        }
        double t = m.since();
        if (t > 30) return;
        double open = easeOut(t / 2.5) * (1 - easeIn((t - 5) / 6.0));
        rift(b, m.feet(), m.flat(), 2.9, 1.9, open, m.main(), 235);
        Random r = new Random(m.seed());
        for (int i = 0; i < 9; i++) {
            double a = i * Math.PI * 2 / 9 + r.nextDouble() * .3, len = (1.2 + r.nextDouble() * 1.4) * easeOut(t / 4.0);
            Point o = m.feet().plus(new Point(0, 1.0, 0));
            Point e = o.plus(m.s().scale(Math.cos(a) * len)).plus(new Point(0, Math.sin(a) * len * .8, 0)).plus(m.f().scale(Math.sin(a * 2) * .4));
            b.streak(o.lerp(e, .15), e, .016, i % 2 == 0 ? m.accent() : m.main(), 230 * m.fade(10));
        }
        shockRing(b, m, m.feet().plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 2.6, 0, 14, m.main());
        flash(b, m, m.feet().plus(new Point(0, 1.0, 0)), .16, m.main());
    }

    /** Two doorway frames and an x-ray line through the wall between them; on exit the frame behind the caster fades. */
    static void penetrateTeleport(B b, M m) {
        Point wall = m.end();
        Point face = m.normal();
        Point dir = norm(wall.minus(m.hand()));
        Point exit = wall.plus(dir.scale(4.0));
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(24);
        if (on <= 0) return;
        Point base = new Point(wall.x(), Math.floor(wall.y()), wall.z());
        frame(b, base, face, .6, 2.0, m.main(), 210 * on);
        frame(b, new Point(exit.x(), base.y(), exit.z()), face, .6, 2.0, m.accent(), 150 * on);
        chevronStream(b, wall.plus(new Point(0, .0, 0)), exit, 5, .22, m.time(), .035, m.main(), 190 * on);
        for (int i = 0; i < 3; i++) {
            double ph = ((m.time() * .04 + i / 3.0) % 1);
            b.ring(wall.lerp(exit, ph), perp(dir)[0], perp(dir)[1], .55, m.main(), 160 * on * Math.sin(Math.PI * ph), i);
        }
        b.line(m.hand(), wall, .006, m.main(), 110 * on);
        if (!m.held() && m.since() < 12) {
            frame(b, m.feet().minus(m.flat().scale(1.4)), m.flat(), .6, 2.0, m.main(), 200 * m.fade(12));
            shockRing(b, m, m.feet().plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 2.0, 0, 12, m.main());
        }
    }

    /** A marker is planted: a coordinate frame stands over a ground ring with a beacon and flag ticks at the corners. */
    static void markTeleport(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(46);
        if (on <= 0) return;
        Point g = m.ground();
        double settle = m.held() ? 1 : easeOut(m.since() / 5.0);
        frame(b, g, m.flat().scale(-1), .55, 2.1 * settle, m.main(), 210 * on);
        b.groundRing(g, .9, m.main(), 200 * on, m.time() * .02);
        b.groundRing(g, 1.3, m.deep(), 130 * on, -m.time() * .015);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            Point c = g.plus(new Point(Math.cos(a) * 1.3, .02, Math.sin(a) * 1.3));
            b.line(c, c.plus(new Point(0, .5, 0)), .012, m.accent(), 190 * on);
            b.mote(c.plus(new Point(0, .5, 0)), .05, m.accent(), 220 * on);
        }
        pillar(b, g, 3.2 * settle, .02, m.main(), 150 * on);
        b.add(FxLib.embers(g, .8, 2.4, m.seed(), m.time(), 8, 0xF3E8FF, m.main(), m.tier()));
        if (m.since() >= 0 && m.since() < 8) shockRing(b, m, g.plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 2.4, 0, 14, m.main());
    }

    /** Two crossed rifts open at the target (an X reads from every side), blade lines cut across them. */
    static void fleshRipping(B b, M m) {
        Point tgt = m.ground();
        double t = m.held() ? m.age() : m.since();
        double open = m.held() ? easeOut(m.age() / 14.0) * .35 : easeOut(t / 3.0) * (1 - easeIn((t - 14) / 10.0));
        if (open <= .02) return;
        Point face1 = new Point(1, 0, 0), face2 = new Point(0, 0, 1);
        Point d1 = norm(new Point(1, 0, 1)), d2 = norm(new Point(1, 0, -1));
        rift(b, tgt, face1, 3.4, 1.7, open, m.main(), 235);
        rift(b, tgt, face2, 3.4, 1.7, open, m.main(), 235);
        rift(b, tgt, d1, 3.0, 1.4, open * .9, m.accent(), 200);
        rift(b, tgt, d2, 3.0, 1.4, open * .9, m.accent(), 200);
        Point mid = tgt.plus(new Point(0, 1.5, 0));
        if (!m.held()) {
            Random r = new Random(m.seed());
            for (int i = 0; i < 3; i++) {
                double a = r.nextDouble() * Math.PI, k = easeOut((t - i * .8) / 3.0);
                if (k <= 0) continue;
                Point dir = new Point(Math.cos(a), (r.nextDouble() - .5) * .8, Math.sin(a));
                b.beam(mid.minus(dir.scale(1.6 * k)), mid.plus(dir.scale(1.6 * k)), .012, m.accent(), 230 * m.fade(14));
            }
            b.orb(mid, .2 * punch(t), m.accent(), 220, m.seed());
            burst(b, mid, 7, 1.8 * easeOut(t / 4.0), m.seed() + 3, 0, m.main(), 210 * m.fade(14));
            b.groundGlow(tgt, 1.3, m.main(), 120 * m.fade(24));
        }
    }

    /** Coordinate axes with a lattice cube float at the hand; a target beacon with a pin ring stands where the jump will end. */
    static void locationTeleport(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(36);
        if (on <= 0) return;
        Point c = m.at(1.1, 0, 0);
        Point[] ax = {new Point(1, 0, 0), new Point(0, 1, 0), new Point(0, 0, 1)};
        double spin = m.time() * .02;
        for (int i = 0; i < 3; i++) arrow(b, c, c.plus(ax[i].scale(.5)), .1, i == 1 ? m.accent() : m.main(), 220 * on);
        double h = .32 + .02 * Math.sin(m.time() * .1);
        for (int sx = -1; sx <= 1; sx += 2) for (int sy = -1; sy <= 1; sy += 2) {
            b.line(c.plus(new Point(sx * h, sy * h, -h)), c.plus(new Point(sx * h, sy * h, h)), .006, m.main(), 120 * on);
            b.line(c.plus(new Point(sx * h, -h, sy * h)), c.plus(new Point(sx * h, h, sy * h)), .006, m.main(), 120 * on);
            b.line(c.plus(new Point(-h, sx * h, sy * h)), c.plus(new Point(h, sx * h, sy * h)), .006, m.main(), 120 * on);
        }
        b.sprite(c, .5, m.main(), 120 * on, Tex.BLOOM, spin, 1);
        Point g = m.ground();
        pillar(b, g, 3.0, .02, m.main(), 170 * on);
        b.groundRing(g, .8, m.main(), 200 * on, m.time() * .03);
        b.groundRing(g, 1.25 + .1 * Math.sin(m.time() * .1), m.deep(), 140 * on, -m.time() * .02);
        b.line(c, g.plus(new Point(0, 1.2, 0)), .005, m.deep(), 90 * on);
        if (m.lv() >= 4 && m.since() >= 0) grand(b, m, m.feet(), 2.4, m.main());
    }

    /** Two rifts, one at the target and one beside it; the targets trade places along a high arc, trails of chevrons mark the swap. */
    static void shiftTp(B b, M m) {
        if (m.tier().layers >= 3) {
            if (m.held()) {
                double open = easeOut(m.age() / 10.0);
                frame(b, m.ground(), m.flat().scale(-1), .55, 1.3, m.main(), 185 * open);
            }
            return; // Confirmed placement renders separately from the server receipt.
        }
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(40);
        if (on <= 0) return;
        Point g = m.ground();
        Point a = g, c = g.plus(m.s().scale(4.2));
        Point face = m.flat().scale(-1);
        double open = m.held() ? easeOut(m.age() / 12.0) : easeOut(m.since() / 4.0);
        rift(b, a, face, 2.6, 1.3, open, m.main(), 220 * on);
        rift(b, c, face, 2.6, 1.3, open, m.accent(), 220 * on);
        b.groundRing(a, 1.1, m.main(), 160 * on, m.time() * .03);
        b.groundRing(c, 1.1, m.accent(), 160 * on, -m.time() * .03);
        double k = m.held() ? (m.time() * .02) % 1 : c01(m.since() / 10.0);
        swapArcs(b, a.plus(new Point(0, 1.2, 0)), c.plus(new Point(0, 1.2, 0)), k, 1.6, m.main(), 230 * on, m.seed());
        chevronStream(b, a.plus(new Point(0, 1.0, 0)), c.plus(new Point(0, 1.0, 0)), 5, .2, m.time(), .03, m.main(), 160 * on);
        if (!m.held()) grand(b, m, g.lerp(c, .5), 3.0, m.main());
    }

    /** Space itself wobbles: shells in three crossed planes swell outward out of phase while the ground lattice undulates. */
    static void spaceFluct(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(40);
        if (on <= 0) return;
        Point c = m.feet().plus(new Point(0, 1.2, 0));
        Point[] ax = {new Point(1, 0, 0), new Point(0, 1, 0), new Point(0, 0, 1)};
        double big = m.held() ? 1 : 1 + 1.4 * easeOut(m.since() / 10.0);
        for (int i = 0; i < 3; i++) {
            for (int k = 0; k < 2; k++) {
                double ph = ((m.time() * .025 + i / 3.0 + k / 2.0) % 1);
                double wob = 1 + .08 * Math.sin(m.time() * .3 + i * 2 + k);
                b.ring(c, ax[i], ax[(i + 1) % 3], (1.0 + 4.0 * ph) * big * wob, m.main(), 190 * on * Math.sin(Math.PI * ph), i * 1.3 + k);
            }
        }
        double breathe = 1 + .12 * Math.sin(m.time() * .2);
        b.blend(m.feet().plus(new Point(0, .05, 0)), new Point(4.5 * breathe, 0, 0), new Point(0, 0, 4.5 * breathe), m.main(), 150 * on, Tex.LATTICE);
        b.sprite(c, 1.4 * breathe, m.deep(), 70 * on, Tex.VORTEX, m.time() * .08, 1);
        b.orb(c, .15, m.accent(), 200 * on, m.seed());
        if (!m.held()) grand(b, m, m.feet(), 4.0, m.main());
    }

    /** A rapid chain of doorways along the line of flight, each flashing in turn with ghost duplicates behind. */
    static void flashing(B b, M m) {
        Point dir = new Point(m.dir().x(), 0, m.dir().z());
        dir = dir.length() < 1e-6 ? m.flat() : norm(dir);
        Point start = m.held() ? m.feet() : m.feet().minus(dir.scale(10.0));
        double on = m.held() ? easeOut(m.age() / 10.0) : 1;
        for (int i = 0; i < 6; i++) {
            Point p = start.plus(dir.scale(2 + i * 2.0));
            double delay = i * 1.1;
            double life = m.held() ? .6 * on : (m.since() >= delay ? Math.exp(-(m.since() - delay) / 9.0) : 0);
            if (life <= .02) continue;
            frame(b, p, dir, .55, 2.0, i % 2 == 0 ? m.main() : m.accent(), 220 * life);
            double flashK = m.held() ? 0 : (m.since() >= delay ? Math.exp(-(m.since() - delay) / 2.0) : 0);
            if (flashK > .05) {
                rift(b, p, dir, 2.2, .9, flashK, m.main(), 220 * flashK);
                b.flare(p.plus(new Point(0, 1.0, 0)), 1.6 * flashK, m.main(), 220 * flashK);
            }
            b.groundRing(p, .6, m.main(), 130 * life, i);
        }
        b.line(start.plus(new Point(0, .06, 0)), start.plus(dir.scale(14)).plus(new Point(0, .06, 0)), .008, m.deep(), 110 * on);
        if (!m.held()) {
            Random r = new Random(m.seed());
            for (int i = 0; i < 12; i++) {
                double d = r.nextDouble() * 10;
                b.streak(start.plus(dir.scale(d + 2)).plus(new Point(0, .5 + r.nextDouble() * 1.5, 0)), start.plus(dir.scale(d + 4)).plus(new Point(0, .5 + r.nextDouble() * 1.5, 0)), .01, m.accent(), 200 * m.fade(14));
            }
            grand(b, m, m.feet(), 3.0, m.main());
        }
    }

    /** A scanner head descends the line of sight to the ground; corner brackets close on the landing patch and light up when it is safe. */
    static void safeLandingProbe(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(36);
        if (on <= 0) return;
        Point g = m.ground();
        Point top = g.plus(new Point(0, 2.8, 0));
        double ph = (m.time() * .035) % 1;
        b.line(top, g, .006, m.main(), 120 * on);
        Point scanY = top.lerp(g, ph);
        b.ring(scanY, new Point(1, 0, 0), new Point(0, 0, 1), .55 - .25 * ph, m.accent(), 200 * on, m.time() * .06);
        b.plate(scanY, new Point(.6, 0, 0), new Point(0, 0, .6), m.main(), 110 * on, Tex.SCAN);
        double r = .75;
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            Point c = g.plus(new Point(Math.cos(a) * r, .04, Math.sin(a) * r));
            Point inward = new Point(-Math.cos(a), 0, -Math.sin(a)).scale(.22), side = new Point(-Math.sin(a), 0, Math.cos(a)).scale(.22);
            b.line(c, c.plus(inward.plus(side)), .012, m.accent(), 220 * on);
            b.line(c, c.plus(inward.minus(side)), .012, m.accent(), 220 * on);
        }
        b.groundGlow(g, .9, m.main(), 100 * on);
        b.groundRing(g, .35 + .05 * Math.sin(m.time() * .2), m.main(), 190 * on, 0);
    }

    /** Two thin ghost slits stand beside the body, one each way; the one on the dodge side stretches into a smear. */
    static void phaseSidestep(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 6.0) : m.fade(22);
        if (on <= 0) return;
        for (int sd = -1; sd <= 1; sd += 2) {
            Point p = m.feet().plus(m.s().scale(1.0 * sd));
            rift(b, p, m.flat(), 2.0, .55, 1.0, m.main(), 190 * on);
            b.groundRing(p, .35, m.main(), 150 * on, sd);
            Point a = m.chest().plus(m.s().scale(.3 * sd)), e = m.chest().plus(m.s().scale(.85 * sd));
            chevronStream(b, a, e, 2, .12, m.time(), .05, m.accent(), 180 * on);
        }
        if (!m.held()) {
            double k = easeOut(m.since() / 4.0);
            for (int i = 0; i < 5; i++) {
                Point p = m.chest().plus(m.s().scale(-.2 * i * k)).plus(new Point(0, (i - 2) * .12, 0));
                b.streak(p, p.plus(m.s().scale(1.4 * k)), .02, m.main(), 160 * m.fade(14));
            }
        }
    }

    /** A hologram double stands to the side: a scan-line figure with a bright sweep moving over it, shedding pixels. */
    static void displacedProjection(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(40);
        if (on <= 0) return;
        double glitch = flicker(m.seed(), m.time()) > .72 ? .35 : 1;
        Point p = m.feet().plus(m.s().scale(1.6)).plus(m.flat().scale(.3));
        Point face = m.flat();
        Point side = norm(face.cross(UP));
        b.blend(p.plus(new Point(0, .95, 0)), side.scale(.55), new Point(0, .98, 0), m.main(), 200 * on * glitch, Tex.LATTICE);
        b.blend(p.plus(new Point(0, .95, 0)), side.scale(.5), new Point(0, .95, 0), m.deep(), 90 * on * glitch, Tex.BLOOM);
        double sweep = (m.time() * .03) % 1;
        b.plate(p.plus(new Point(0, .1 + 1.8 * sweep, 0)), side.scale(.65), new Point(0, .14, 0), m.accent(), 235 * on * glitch, Tex.SCAN);
        b.sprite(p.plus(new Point(0, 1.55, 0)), .22, m.main(), 120 * on * glitch, Tex.BLOOM, 0, 1);
        b.sprite(p.plus(new Point(0, .9, 0)), .55, m.main(), 70 * on * glitch, Tex.BLOOM, 0, 1.0);
        b.groundRing(p, .8, m.main(), 200 * on, m.time() * .03);
        Random r = new Random(m.seed());
        for (int i = 0; i < 10; i++) {
            double ph = ((m.time() * .025 + r.nextDouble()) % 1);
            Point q = p.plus(side.scale((r.nextDouble() - .5) * .8)).plus(new Point(0, .2 + r.nextDouble() * 1.7 - ph * .3, 0)).plus(side.scale(ph * .6 * (r.nextBoolean() ? 1 : -1)));
            b.sprite(q, .05, m.accent(), 235 * on * (1 - ph), Tex.SPARK, r.nextDouble() * 6, 1);
        }
    }

    /** A return anchor: a rotating arrow circles the mark so the eye reads "come back here". */
    static void returnMark(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(46);
        if (on <= 0) return;
        Point g = m.ground();
        double spin = m.time() * .05;
        b.circle(g.plus(new Point(0, .06, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 1.05, 40, spin, Math.PI * 1.6, .03, m.main(), 220 * on);
        Point head = g.plus(new Point(Math.cos(spin + Math.PI * 1.6) * 1.05, .06, Math.sin(spin + Math.PI * 1.6) * 1.05));
        Point tang = new Point(-Math.sin(spin + Math.PI * 1.6), 0, Math.cos(spin + Math.PI * 1.6));
        b.plate(head, tang.scale(.28), perp(tang)[0].scale(.28), m.accent(), 230 * on, Tex.CHEVRON);
        b.groundRing(g, .5, m.main(), 160 * on, -spin);
        pillar(b, g, 1.4, .016, m.main(), 150 * on);
        b.line(g.plus(new Point(0, 1.4, 0)), g.plus(new Point(0, 1.4, 0)).plus(new Point(.45, -.12, 0)), .014, m.accent(), 210 * on);
        b.line(g.plus(new Point(0, 1.1, 0)), g.plus(new Point(0, 1.1, 0)).plus(new Point(.45, .12, 0)), .014, m.accent(), 210 * on);
        if (m.since() >= 0 && m.since() < 8) shockRing(b, m, g.plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 2.0, 0, 12, m.main());
    }

    /** A recovery point: a hexagon plate, a soft pillar and a slow spiral of motes rising along it. */
    static void recoveryMark(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(46);
        if (on <= 0) return;
        Point g = m.ground();
        b.blend(g.plus(new Point(0, .05, 0)), new Point(1.4, 0, 0), new Point(0, 0, 1.4), m.main(), 220 * on, Tex.HEX);
        b.hex(g.plus(new Point(0, .06, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 1.2, m.time() * .01, m.accent(), 210 * on);
        b.groundGlow(g, 1.4, m.main(), 110 * on);
        pillar(b, g, 2.8, .06, m.main(), 150 * on);
        for (int k = 0; k < 2; k++) {
            Point top = g.plus(new Point(0, 2.8, 0));
            helix(b, g.plus(new Point(0, .1, 0)), top, .7, 2.2, m.time() * .05 + k * Math.PI, k == 0 ? m.main() : m.accent(), 200 * on, .03);
        }
        risers(b, g, 1.0, 2.8, 16, m.time(), m.seed(), m.accent(), 235 * on);
        b.line(g.plus(new Point(-.25, .05, 0)), g.plus(new Point(.25, .05, 0)), .02, m.accent(), 220 * on);
        b.line(g.plus(new Point(0, .05, -.25)), g.plus(new Point(0, .05, .25)), .02, m.accent(), 220 * on);
    }

    /** Two points trade places along crossing arcs, drawing an X between them. */
    static void crossSubstitution(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(36);
        if (on <= 0) return;
        Point a = m.feet().plus(new Point(0, 1.1, 0)), c = m.reach(10).plus(new Point(0, .3, 0));
        double k = m.held() ? (m.time() * .025) % 1 : c01(m.since() / 9.0);
        swapArcs(b, a, c, k, 1.8, m.main(), 230 * on, m.seed());
        swapArcs(b, a, c, k, -1.8, m.accent(), 150 * on, m.seed() + 7);
        b.line(a, c, .006, m.main(), 70 * on);
        Point[] pa = perp(c.minus(a));
        b.ring(a, pa[0], pa[1], .5, m.main(), 170 * on, m.time() * .06);
        b.ring(c, pa[0], pa[1], .5, m.accent(), 170 * on, -m.time() * .06);
        if (m.since() >= 8 && m.since() < 20) {
            flash(b, m.later(8), a, .22, m.main());
            flash(b, m.later(8), c, .22, m.accent());
        }
    }

    /** A right-angled route: two doorways joined by an L of chevrons, the corner marked with a node. */
    static void cornerExchange(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(36);
        if (on <= 0) return;
        Point g = m.ground();
        Point corner = g.plus(m.s().scale(3.0));
        Point far = corner.plus(m.flat().scale(3.0));
        frame(b, g, m.s().scale(-1), .55, 2.0, m.main(), 210 * on);
        frame(b, far, m.s(), .55, 2.0, m.accent(), 210 * on);
        chevronStream(b, g.plus(new Point(0, 1.0, 0)), corner.plus(new Point(0, 1.0, 0)), 4, .2, m.time(), .035, m.main(), 190 * on);
        chevronStream(b, corner.plus(new Point(0, 1.0, 0)), far.plus(new Point(0, 1.0, 0)), 4, .2, m.time(), .035, m.accent(), 190 * on);
        b.line(g.plus(new Point(0, 1.0, 0)), corner.plus(new Point(0, 1.0, 0)), .008, m.deep(), 120 * on);
        b.line(corner.plus(new Point(0, 1.0, 0)), far.plus(new Point(0, 1.0, 0)), .008, m.deep(), 120 * on);
        b.orb(corner.plus(new Point(0, 1.0, 0)), .12, m.accent(), 220 * on, m.seed());
        b.groundRing(corner, .5, m.main(), 160 * on, m.time() * .05);
    }

    /** A wide rift-wall tears open in front of the caster: an instant shield of displaced space, ripples travelling over it. */
    static void shortInterception(B b, M m) {
        double t = m.held() ? m.age() : m.since();
        double open = m.held() ? easeOut(m.age() / 5.0) * .8 : easeOut(t / 2.5) * (1 - easeIn((t - 16) / 14.0));
        if (open <= .02) return;
        Point wall = m.feet().plus(m.flat().scale(1.9));
        Point side = norm(m.flat().cross(UP));
        b.plate(wall.plus(new Point(0, 1.1, 0)), side.scale(1.4 * open), new Point(0, 1.1 * open, 0), m.main(), 150 * open, Tex.RIFT);
        b.plate(wall.plus(new Point(0, 1.1, 0)), side.scale(1.2 * open), new Point(0, 1.0 * open, 0), m.accent(), 90 * open, Tex.LATTICE);
        for (int i = 0; i < 3; i++) {
            double ph = ((t * .05 + i / 3.0) % 1);
            b.line(wall.plus(side.scale(-1.5 + 3 * ph)).plus(new Point(0, .05, 0)), wall.plus(side.scale(-1.5 + 3 * ph)).plus(new Point(0, 2.2, 0)), .012, m.accent(), 190 * open * Math.sin(Math.PI * ph));
        }
        b.groundRing(wall, 1.4, m.main(), 150 * open, 0);
        if (!m.held() && t < 6) b.flare(wall.plus(new Point(0, 1.1, 0)), 3.0 * punch(t), m.main(), 220);
    }

    /** Three upright gates in a row along the line of sight, opening one after another with a bead racing through them. */
    static void spatialRelay(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(44);
        if (on <= 0) return;
        Point dir = norm(new Point(m.dir().x(), 0, m.dir().z()).length() < 1e-6 ? m.flat() : new Point(m.dir().x(), 0, m.dir().z()));
        Point[] pp = perp(dir);
        Point base = m.feet();
        Point[] gates = new Point[3];
        for (int i = 0; i < 3; i++) {
            gates[i] = base.plus(dir.scale(3.2 + i * 3.4)).plus(new Point(0, 1.3, 0));
            double open = m.held() ? easeOut((m.age() - i * 3) / 8.0) : easeOut((m.since() + 6 - i * 2) / 5.0);
            if (open <= 0) continue;
            b.ring(gates[i], pp[0], UP, 1.1 * open, m.main(), 220 * on, m.time() * .02 * (i % 2 == 0 ? 1 : -1));
            b.hex(gates[i], pp[0], UP, .6 * open, m.time() * .03, m.accent(), 190 * on);
            b.sprite(gates[i], .9 * open, m.main(), 80 * on, Tex.BLOOM, 0, 1);
        }
        for (int i = 0; i < 2; i++) b.line(gates[i], gates[i + 1], .006, m.deep(), 120 * on);
        double ph = (m.time() * .04) % 1;
        var path = java.util.List.of(base.plus(new Point(0, 1.3, 0)), gates[0], gates[1], gates[2]);
        comet(b, along(path, ph), dir, 1.5, 0xFFFFFF, 220 * on);
        if (!m.held() && m.lv() >= 4) grand(b, m, gates[2].minus(new Point(0, 1.3, 0)), 2.6, m.main());
    }

    /** A recall: motes converge from the mark's direction into the body as a vortex ring contracts at the feet, then a column of light. */
    static void emergencyReturn(B b, M m) {
        double t = m.held() ? m.age() : m.since();
        Point c = m.chest();
        if (m.held()) {
            double ch = easeIn(m.age() / 14.0);
            converge(b, m, c, 3.2, 20, 14, m.main());
            b.groundRing(m.feet(), 3.0 * (1 - ch) + .4, m.main(), 190 * ch, m.time() * .08);
            b.sprite(m.feet().plus(new Point(0, .1, 0)), 1.5 * (1 - .6 * ch), m.deep(), 140 * ch, Tex.VORTEX, m.time() * .2, 1);
            return;
        }
        if (t > 40) return;
        double live = m.fade(36);
        pillar(b, m.feet(), 6.0, .09 * (1 - easeOut(t / 20.0) * .5), m.main(), 230 * live);
        b.flare(c, 3.4 * punch(t), 0xFFFFFF, 230);
        shockRing(b, m, m.feet().plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 3.4, 0, 18, m.main());
        b.add(FxLib.embers(m.feet(), 1.0, 4.0, m.seed(), m.time(), 14, 0xF3E8FF, m.main(), m.tier()));
        grand(b, m, m.feet(), 3.2, m.main());
    }

    /** A tactical board spreads over the ground: a big hexagonal grid, axis rulers, four corner pylons and unit markers sliding on it. */
    static void tacticalCoordinateFormation(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 14.0) : m.fade(50);
        if (on <= 0) return;
        Point g = m.ground();
        double R = 6.0 * on;
        b.plate(g.plus(new Point(0, .05, 0)), new Point(R, 0, 0), new Point(0, 0, R), m.main(), 150 * on, Tex.HEX);
        b.plate(g.plus(new Point(0, .06, 0)), new Point(R, 0, 0), new Point(0, 0, R), m.deep(), 60 * on, Tex.LATTICE);
        b.groundRing(g, R, m.main(), 200 * on, m.time() * .004);
        b.groundRing(g, R * .55, m.deep(), 150 * on, -m.time() * .006);
        b.line(g.minus(new Point(R, -.06, 0)), g.plus(new Point(R, .06, 0)), .014, m.accent(), 180 * on);
        b.line(g.minus(new Point(0, -.06, R)), g.plus(new Point(0, .06, R)), .014, m.accent(), 180 * on);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + Math.PI / 4;
            Point corner = g.plus(new Point(Math.cos(a) * R * .85, 0, Math.sin(a) * R * .85));
            pillar(b, corner, 2.8 * on, .025, m.main(), 190 * on);
            b.hex(corner.plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), .45, i, m.accent(), 200 * on);
        }
        Random r = new Random(m.seed());
        for (int i = 0; i < 5; i++) {
            double a = r.nextDouble() * Math.PI * 2 + m.time() * .01 * (i % 2 == 0 ? 1 : -1), d = R * (.2 + .6 * r.nextDouble());
            Point p = g.plus(new Point(Math.cos(a) * d, .1, Math.sin(a) * d));
            b.orb(p, .12, i == 0 ? m.accent() : m.main(), 220 * on, m.seed() + i);
            b.groundRing(p.minus(new Point(0, .1, 0)), .35, m.main(), 160 * on, i);
        }
        if (m.since() >= 0) grand(b, m, g, 5.0, m.main());
    }

    /** Two lattice walls close in from either side to form a corridor; chevrons run down it. */
    static void spatialPincerCorridor(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(42);
        if (on <= 0) return;
        Point dir = norm(new Point(m.dir().x(), 0, m.dir().z()).length() < 1e-6 ? m.flat() : new Point(m.dir().x(), 0, m.dir().z()));
        Point side = norm(dir.cross(UP));
        double len = Math.min(9, Math.max(5, m.dist()));
        double gap = m.held() ? 3.2 - 1.6 * easeOut(m.age() / 14.0) : 1.6;
        Point mid = m.feet().plus(dir.scale(len * .5 + 1.5));
        for (int sd = -1; sd <= 1; sd += 2) {
            Point c = mid.plus(side.scale(gap * sd)).plus(new Point(0, 1.3, 0));
            b.plate(c, dir.scale(len * .5), new Point(0, 1.3, 0), m.main(), 120 * on, Tex.LATTICE);
            b.plate(c, dir.scale(len * .5), new Point(0, 1.3, 0), m.accent(), 60 * on, Tex.SCAN);
            b.line(c.minus(dir.scale(len * .5)).minus(new Point(0, 1.3, 0)), c.plus(dir.scale(len * .5)).minus(new Point(0, 1.3, 0)), .016, m.main(), 200 * on);
            b.line(c.minus(dir.scale(len * .5)).plus(new Point(0, 1.3, 0)), c.plus(dir.scale(len * .5)).plus(new Point(0, 1.3, 0)), .016, m.main(), 200 * on);
        }
        b.plate(mid.plus(new Point(0, .05, 0)), dir.scale(len * .5), side.scale(gap), m.deep(), 90 * on, Tex.LATTICE);
        chevronStream(b, mid.minus(dir.scale(len * .5)).plus(new Point(0, .1, 0)), mid.plus(dir.scale(len * .5)).plus(new Point(0, .1, 0)), 6, .3, m.time(), .04, m.accent(), 200 * on);
        if (!m.held() && m.since() < 8) b.flare(mid.plus(new Point(0, 1.0, 0)), 3.5 * punch(m.since()), m.main(), 210);
        if (!m.held()) grand(b, m, mid, 3.0, m.main());
    }

    /** A detour around an obstacle: a dashed cylinder marks it and a curved route of chevrons swings past. */
    static void incidentBypass(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(36);
        if (on <= 0) return;
        Point obstacle = m.reach(5.0);
        Point o = new Point(obstacle.x(), m.feet().y(), obstacle.z());
        Point side = norm(m.flat().cross(UP));
        b.circle(o.plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), .7, 24, 0, Math.PI * 2, .01, m.deep(), 170 * on);
        b.circle(o.plus(new Point(0, 1.8, 0)), new Point(1, 0, 0), new Point(0, 0, 1), .7, 24, 0, Math.PI * 2, .01, m.deep(), 170 * on);
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2;
            b.line(o.plus(new Point(Math.cos(a) * .7, .05, Math.sin(a) * .7)), o.plus(new Point(Math.cos(a) * .7, 1.8, Math.sin(a) * .7)), .008, m.deep(), 120 * on);
        }
        Point from = m.feet().plus(new Point(0, .1, 0));
        Point to = o.plus(m.flat().scale(3.0)).plus(new Point(0, .1, 0));
        Point ctrl = o.plus(side.scale(2.8)).plus(new Point(0, .1, 0));
        Point prev = null;
        var pts = new java.util.ArrayList<Point>();
        for (int k = 0; k <= 16; k++) {
            double t = k / 16.0;
            Point q = from.scale((1 - t) * (1 - t)).plus(ctrl.scale(2 * t * (1 - t))).plus(to.scale(t * t));
            pts.add(q);
            if (prev != null) b.line(prev, q, .018, m.main(), 200 * on);
            prev = q;
        }
        for (int i = 0; i < 4; i++) {
            double t = ((m.time() * .03 + i / 4.0) % 1);
            Point p = along(pts, t), q = along(pts, Math.min(1, t + .03));
            Point tan = norm(q.minus(p));
            b.plate(p, tan.scale(.22), perp(tan)[0].scale(.22), m.accent(), 220 * on * Math.sin(Math.PI * t), Tex.CHEVRON);
        }
        b.orb(to, .1, m.accent(), 220 * on, m.seed());
    }

    /** A tunnel of shrinking rings with lattice walls runs far ahead, a pulse of light racing down it to a door at the end. */
    static void phaseCorridor(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 12.0) : m.fade(50);
        if (on <= 0) return;
        Point dir = norm(new Point(m.dir().x(), 0, m.dir().z()).length() < 1e-6 ? m.flat() : new Point(m.dir().x(), 0, m.dir().z()));
        Point[] pp = {norm(dir.cross(UP)), UP};
        double L = 22 * (m.held() ? easeOut(m.age() / 14.0) : 1);
        Point origin = m.feet().plus(new Point(0, 1.2, 0));
        for (int i = 1; i <= 12; i++) {
            double d = i * L / 12.0, r = 1.5 * (1 - .45 * i / 12.0);
            double pulse = Math.exp(-Math.abs(((m.time() * .05) % 1) * 12 - i) * .9);
            b.ring(origin.plus(dir.scale(d)), pp[0], pp[1], r, m.main(), (110 + 130 * pulse) * on, i * .7);
        }
        for (int sd = -1; sd <= 1; sd += 2) {
            Point c = origin.plus(dir.scale(L * .5)).plus(pp[0].scale(1.3 * sd));
            b.plate(c, dir.scale(L * .5), new Point(0, 1.1, 0), m.main(), 70 * on, Tex.LATTICE);
        }
        b.plate(origin.plus(dir.scale(L * .5)).minus(new Point(0, 1.15, 0)), dir.scale(L * .5), pp[0].scale(1.3), m.deep(), 80 * on, Tex.LATTICE);
        comet(b, origin.plus(dir.scale(L * ((m.time() * .05) % 1))), dir, 3.0, 0xFFFFFF, 220 * on);
        Point door = origin.plus(dir.scale(L));
        b.hex(door, pp[0], pp[1], 1.0, m.time() * .02, m.accent(), 230 * on);
        b.flare(door, 2.8, m.main(), 190 * on);
        if (!m.held()) grand(b, m, m.feet(), 3.6, m.main());
    }
}
