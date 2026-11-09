package cn.academy.port.client;

import static cn.academy.port.client.MotifKit.*;

import cn.academy.port.client.MotifRenderer.Tex;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Electromaster motifs. Only the palette is shared; every skill here has its own shape, material and timing:
 * crackling arcs, coils, field lines, iron-sand swarms, hexagon shields, circuit traces, scan sweeps, nerve pulses.
 */
final class EmMotifs {
    private EmMotifs() {}

    private static final Point UP = new Point(0, 1, 0);

    /** Short bright arc; a ball of crackle in the palm while held, a fan of three arcs on release. */
    static void arcGen(B b, M m) {
        Point c = m.at(.38, 0, 0);
        if (m.held()) {
            double ch = easeIn(m.age() / 8.0);
            b.orb(c, .05 + .1 * ch * flicker(m.seed(), m.time()), m.main(), 230 * ch, m.seed());
            for (int i = 0; i < 3; i++) {
                Random r = new Random(m.seed() + i * 17L + (long) (m.time() * 1.5));
                Point o = c.plus(rndUnit(r).scale(.22 + .22 * ch));
                var path = jag(c, o, m.seed() + i, m.time(), 3, .05);
                for (int k = 1; k < path.size(); k++) b.lightning(path.get(k - 1), path.get(k), .012, m.main(), 220 * ch, m.seed() + k + i);
            }
            return;
        }
        Point tip = m.reach(11);
        flash(b, m, c, .16, m.main());
        bolt(b, m, c, tip, .028, 3, 5);
        Point side = m.s().scale(.9);
        bolt(b, m, c, tip.plus(side), .016, 1, 4, m.seed() + 5);
        bolt(b, m, c, tip.minus(side), .016, 1, 4, m.seed() + 6);
        spot(b, m, tip, UP, .16, m.main());
    }

    /** A tree: one trunk that splits at 55% into three branches, each landing with its own flash. */
    static void forkedArc(B b, M m) {
        Point c = m.at(.4, 0, 0);
        Point tip = m.reach(10);
        if (m.held()) {
            double ch = easeIn(m.age() / 10.0);
            for (int i = -1; i <= 1; i++) {
                Point prong = c.plus(m.f().scale(.22)).plus(m.s().scale(.11 * i));
                b.line(c, prong, .008, m.main(), 190 * ch);
                b.mote(prong, .04 + .03 * flicker(m.seed() + i, m.time()), m.accent(), 230 * ch);
            }
            return;
        }
        Point node = c.lerp(tip, .55);
        flash(b, m, c, .13, m.main());
        bolt(b, m, c, node, .03, 0, 5);
        for (int i = -1; i <= 1; i++) {
            Point end = tip.plus(m.s().scale(1.7 * i)).plus(m.u().scale(i == 0 ? .2 : -.1));
            bolt(b, m, node, end, .02, 0, 4 + (i == 0 ? 1 : 0), m.seed() + 10 + i);
            spot(b, m, end, UP, .1, m.main());
        }
        b.orb(node, .12 * Math.min(1, punch(m.since())), m.accent(), 220, m.seed());
    }

    /** A battery filling: a circuit plate floats over the palm, two helices climb into it, cells light one by one. */
    static void charging(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(16);
        if (on <= 0) return;
        Point c = m.at(.5, 0, .1);
        b.plate(c, m.s().scale(.34), m.f().scale(.23), m.main(), 200 * on, Tex.CIRCUIT);
        b.sprite(c, .55, m.main(), 70 * on, Tex.BLOOM, 0, 1);
        helix(b, m.hand(), c, .13, 3, m.time() * .25, m.main(), 200 * on, .014);
        helix(b, m.hand(), c, .13, 3, m.time() * .25 + Math.PI, 0xFFFFFF, 180 * on, .01);
        double fill = m.held() ? (m.age() / 70.0) % 1 : 1;
        for (int i = 0; i < 5; i++) {
            double lit = c01(fill * 5 - i);
            b.mote(c.plus(m.s().scale(-.24 + i * .12)).plus(m.f().scale(.2)), .05, lit > 0 ? m.accent() : m.deep(), (lit > 0 ? 235 : 90) * on);
        }
        b.orb(c.plus(m.u().scale(.14)), .11 * flicker(m.seed(), m.time()), m.main(), 220 * on, m.seed());
        if (m.lv() >= 3) for (int i = 0; i < 2; i++) {
            Point corner = c.plus(m.s().scale(i == 0 ? -.2 : .2)).plus(m.f().scale(.14));
            var path = jag(m.hand(), corner, m.seed() + i, m.time(), 4, .05);
            for (int k = 1; k < path.size(); k++) b.lightning(path.get(k - 1), path.get(k), .008, m.main(), 160 * on, m.seed() + k);
        }
        if (m.since() >= 0 && m.since() < 6) b.flare(c, .7 * punch(m.since()), m.main(), 220);
    }

    /** Dipole field lines between hand and target, beads flowing toward the player, pole rings collapsing onto the target. */
    static void magMovement(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(20);
        if (on <= 0) return;
        Point tip = m.reach(16);
        fieldLoops(b, m.hand(), tip, 4 + m.lv(), 1.0 + .15 * m.lv(), m.main(), 165 * on, m.time(), -.03);
        Point[] pp = perp(tip.minus(m.hand()));
        double ph = (m.time() * .04) % 1;
        for (int i = 0; i < 3; i++) {
            double k = (ph + i / 3.0) % 1;
            b.ring(tip, pp[0], pp[1], .15 + 1.0 * (1 - k), m.main(), 200 * on * Math.sin(Math.PI * k), i * 1.7);
        }
        b.orb(tip, .1, m.accent(), 200 * on, m.seed());
        chevronStream(b, tip, m.hand(), 4, .09, m.time(), .035, m.main(), 190 * on);
        if (m.since() >= 0 && m.since() < 6) b.flare(m.hand(), 1.2 * punch(m.since()), m.main(), 200);
    }

    /** A three-finger magnetic claw that closes around the held point; iron filings orbit it. */
    static void magManip(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(22);
        if (on <= 0) return;
        Point hold = m.at(Math.min(m.dist(), 4.5), 0, 0);
        double close = m.held() ? easeOut(m.age() / 14.0) : 1;
        double R = .95 - .5 * close;
        for (int k = 0; k < 3; k++) {
            double a = k * Math.PI * 2 / 3 + m.time() * .02;
            Point radial = m.s().scale(Math.cos(a)).plus(m.u().scale(Math.sin(a)));
            Point prev = null;
            for (int i = 0; i <= 6; i++) {
                double t = i / 6.0;
                Point q = hold.plus(m.f().scale(-.8 + 1.1 * t)).plus(radial.scale(R * (1 - .6 * t * t) + .1 * (1 - t)));
                if (prev != null) b.line(prev, q, .018, m.main(), 220 * on);
                prev = q;
            }
            b.mote(prev, .06, m.accent(), 230 * on);
        }
        grains(b, hold.minus(m.f().scale(.3)), hold.plus(m.f().scale(.3)), 16, .55 - .25 * close, .6, m.seed(), m.time(), m.deep(), 230 * on);
        fieldLoops(b, m.hand(), hold, 3, .55, m.main(), 130 * on, m.time(), .03);
        b.orb(hold, .08 + .04 * close, m.main(), 160 * on, m.seed());
    }

    /** A survey: a radar needle sweeps the ground with a fading wedge behind it, echo blips light as it passes and stay. */
    static void mineDetect(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 6.0) : m.fade(30);
        if (on <= 0) return;
        Point c = m.feet().plus(new Point(0, .06, 0));
        double R = 5 + 1.2 * m.lv();
        double sweepAngle = m.time() * .13;
        for (int i = 0; i < 5; i++) {
            double a = sweepAngle - i * .16;
            Point dir = new Point(Math.cos(a), 0, Math.sin(a)), side = new Point(-Math.sin(a), 0, Math.cos(a));
            b.plate(c.plus(dir.scale(R * .5)), dir.scale(R * .5), side.scale(R * .055), m.main(), 210 * on * (1 - i * .2), Tex.SCAN);
        }
        b.groundRing(c, R, m.main(), 170 * on, m.time() * .01);
        b.groundRing(c, R * .5, m.deep(), 120 * on, -m.time() * .01);
        Random r = new Random(m.seed());
        for (int i = 0; i < 8 + m.lv() * 2; i++) {
            double a = r.nextDouble() * Math.PI * 2, d = R * (.2 + .8 * r.nextDouble());
            double diff = ((sweepAngle - a) % (Math.PI * 2) + Math.PI * 2) % (Math.PI * 2);
            double glow = Math.exp(-diff * 1.1);
            Point p = c.plus(new Point(Math.cos(a) * d, .05, Math.sin(a) * d));
            b.mote(p, .13 + .1 * glow, m.accent(), (50 + 190 * glow) * on);
            if (glow > .55) pillar(b, p, .6 + 1.2 * glow, .012, m.main(), 190 * glow * on);
        }
    }

    /** Nerve signals: pulses run up two lines along the legs, spine and arms; a circuit plate lights under the feet. */
    static void bodyIntensify(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(30);
        if (on <= 0) return;
        b.plate(m.feet().plus(new Point(0, .05, 0)), new Point(1.2, 0, 0), new Point(0, 0, 1.2), m.main(), 190 * on, Tex.CIRCUIT);
        b.sprite(m.chest(), 1.3, m.main(), 55 * on, Tex.BLOOM, 0, 1);
        b.groundRing(m.feet(), .85 + .08 * Math.sin(m.time() * .15), m.main(), 140 * on, m.time() * .03);
        for (int sd = -1; sd <= 1; sd += 2) {
            var pts = new ArrayList<Point>();
            pts.add(m.body(.22 * sd, .05)); pts.add(m.body(.24 * sd, .5)); pts.add(m.body(.2 * sd, .95)); pts.add(m.body(.3 * sd, 1.45));
            pts.add(m.body(.55 * sd, 1.2)); pts.add(m.body(.6 * sd, .85));
            b.poly(pts, .018, m.main(), 150 * on);
            b.streak(pts.get(0), pts.get(3), .06, m.main(), 70 * on);
            for (int k = 0; k < 2; k++) {
                double t = ((m.time() * .035 + k * .5 + (sd > 0 ? .25 : 0)) % 1 + 1) % 1;
                Point head = along(pts, t), tail = along(pts, Math.max(0, t - .14));
                b.streak(tail, head, .045, m.main(), 235 * on);
                b.mote(head, .12, m.accent(), 240 * on);
            }
        }
        for (int i = 0; i < 2 + m.lv() / 2; i++) {
            Random r = new Random(m.seed() + i * 13L + (long) (m.time() * 1.5));
            Point a = m.body(.45 * (r.nextBoolean() ? 1 : -1), 1.3 + .3 * r.nextDouble()), e = a.plus(rndUnit(r).scale(.35));
            var path = jag(a, e, m.seed() + i, m.time(), 3, .05);
            for (int k = 1; k < path.size(); k++) b.lightning(path.get(k - 1), path.get(k), .008, m.main(), 190 * on, m.seed() + k);
        }
        if (m.held() && m.age() < 8) b.flare(m.chest(), 1.4 * punch(m.age()), m.main(), 180);
    }

    /** A bolt from the sky: a plasma knot gathers high above the target, then the strike with forks, ground arcs and scorch. */
    static void thunderBolt(B b, M m) {
        Point tgt = m.ground();
        Point sky = tgt.plus(new Point(.6, 20, -.4));
        if (m.held()) {
            double ch = easeIn(m.age() / 16.0);
            b.orb(sky, .2 + .8 * ch, m.main(), 190 * ch * flicker(m.seed(), m.time()), m.seed());
            for (int i = 0; i < 4; i++) {
                var path = jag(sky, sky.plus(rndUnit(new Random(m.seed() + i + (long) (m.time() * 1.5))).scale(1.5 + 3 * ch)), m.seed() + i, m.time(), 3, .15);
                for (int k = 1; k < path.size(); k++) b.lightning(path.get(k - 1), path.get(k), .03, m.main(), 170 * ch, m.seed() + k);
            }
            b.groundRing(tgt, 1.6 - .8 * ch, m.main(), 150 * ch, m.time() * .05);
            return;
        }
        double t = m.since();
        if (t > 40) return;
        bolt(b, m, sky, tgt, .07, 6, 7);
        bolt(b, m, sky.plus(new Point(1.2, 0, 0)), tgt.plus(new Point(.25, 0, .2)), .035, 3, 5, m.seed() + 31);
        if (t < 6) b.flare(tgt.plus(new Point(0, 1, 0)), 5 * punch(t), 0xFFFFFF, 230);
        impact(b, m, tgt, UP, .4, m.main());
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + (m.seed() % 7) * .3, len = 2.2 + (i % 2);
            Point e = tgt.plus(new Point(Math.cos(a) * len, .1, Math.sin(a) * len));
            bolt(b, m, tgt.plus(new Point(0, .2, 0)), e, .02, 0, 4, m.seed() + 60 + i);
        }
        cracks(b, tgt, m.seed() + 2, 2.4, 7, Math.min(1, t / 5.0), m.main(), 210 * m.fade(34));
        grand(b, m, tgt, 2.2, m.main());
    }

    /** A decoy: a bobbing charged orb on a tripod of light pillars; arcs lick down to the ground and motes spiral up into it. */
    static void chargeDecoy(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(40);
        if (on <= 0) return;
        Point g = m.ground();
        Point apex = g.plus(new Point(0, 1.5 + .07 * Math.sin(m.time() * .12), 0));
        for (int i = 0; i < 3; i++) {
            double a = i * Math.PI * 2 / 3 + .5;
            Point foot = g.plus(new Point(Math.cos(a) * .75, .02, Math.sin(a) * .75));
            b.line(foot, apex, .012, m.main(), 150 * on);
            var path = jag(apex, foot, m.seed() + i, m.time(), 4, .09);
            if (flicker(m.seed() + i * 5L, m.time()) > .8)
                for (int k = 1; k < path.size(); k++) b.lightning(path.get(k - 1), path.get(k), .012, m.main(), 200 * on, m.seed() + k);
        }
        b.orb(apex, .2 * flicker(m.seed(), m.time()) + .1, m.main(), 230 * on, m.seed());
        b.groundRing(g, .9, m.main(), 180 * on, m.time() * .03);
        b.groundGlow(g, 1.0, m.main(), 90 * on);
        for (int i = 0; i < 6; i++) {
            double ph = ((m.time() * .03 + i / 6.0) % 1), a = i * 2.4 + ph * 5;
            b.mote(g.plus(new Point(Math.cos(a) * .7 * (1 - ph), ph * 1.5, Math.sin(a) * .7 * (1 - ph))), .05, m.accent(), 220 * on * Math.sin(Math.PI * ph));
        }
    }

    /** A twisted pair of strands between hand and target, opposite pulses running on each, an end ring on each side counter-rotating. */
    static void polarityTether(B b, M m) {
        double grow = m.held() ? easeOut(m.age() / 8.0) : 1, on = m.held() ? 1 : m.fade(30);
        if (on <= 0) return;
        Point tip = m.hand().lerp(m.reach(14), grow);
        double len = tip.minus(m.hand()).length();
        if (len < .3) return;
        double turns = Math.max(1, len * .45);
        helix(b, m.hand(), tip, .16, turns, m.time() * .22, m.main(), 200 * on, .014);
        helix(b, m.hand(), tip, .16, turns, m.time() * .22 + Math.PI, 0xFFFFFF, 190 * on, .01);
        Point dir = norm(tip.minus(m.hand()));
        for (int k = 0; k < 2; k++) {
            double t = ((m.time() * .03 * (k == 0 ? 1 : -1) + k * .5) % 1 + 1) % 1;
            comet(b, m.hand().lerp(tip, t), dir.scale(k == 0 ? 1 : -1), 1.0, k == 0 ? m.main() : m.accent(), 220 * on);
        }
        Point[] pp = perp(dir);
        b.ring(tip, pp[0], pp[1], .38, m.main(), 190 * on, m.time() * .08);
        b.ring(tip, pp[0], pp[1], .22, m.accent(), 170 * on, -m.time() * .08);
        b.ring(m.hand(), pp[0], pp[1], .22, m.main(), 170 * on, -m.time() * .08);
        b.orb(tip, .09, m.main(), 200 * on, m.seed());
    }

    /** A printed circuit on the ground: traces from a central node, data pulses running out along them. */
    static void conductiveCircuit(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(44);
        if (on <= 0) return;
        Point g = m.ground().plus(new Point(0, .05, 0));
        double R = 2.4;
        b.plate(g, new Point(R, 0, 0), new Point(0, 0, R), m.main(), 175 * on, Tex.CIRCUIT);
        b.groundRing(m.ground(), R * .98, m.main(), 120 * on, m.time() * .01);
        Random r = new Random(m.seed());
        for (int i = 0; i < 6; i++) {
            var pts = new ArrayList<Point>();
            double a = i * Math.PI / 3 + r.nextDouble() * .3;
            Point cur = g;
            pts.add(cur);
            for (int k = 0; k < 3; k++) {
                double leg = R * (.2 + .15 * r.nextDouble());
                a += (k % 2 == 0 ? 1 : -1) * Math.PI / 4;
                cur = cur.plus(new Point(Math.cos(a) * leg, 0, Math.sin(a) * leg));
                pts.add(cur);
            }
            b.poly(pts, .012, m.main(), 140 * on);
            double t = ((m.time() * .03 + i * .17) % 1 + 1) % 1;
            Point head = along(pts, t), tail = along(pts, Math.max(0, t - .18));
            b.streak(tail, head, .03, m.accent(), 230 * on);
            b.mote(head, .08, m.accent(), 230 * on);
            b.mote(cur, .06, m.main(), 190 * on);
        }
        b.hex(g.plus(new Point(0, .02, 0)), new Point(1, 0, 0), new Point(0, 0, 1), .4, m.time() * .03, m.main(), 230 * on);
        pillar(b, g, 1.4 + .4 * m.lvf(), .018, m.main(), 160 * on);
    }

    /** The current dives into the ground and the ring contracts back to the feet: a return stroke, not an outward blast. */
    static void groundingReturn(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(30);
        if (on <= 0) return;
        for (int i = 0; i < 3; i++) {
            double k = ((m.time() * .035 + i / 3.0) % 1 + 1) % 1;
            b.groundRing(m.feet(), 3.0 * (1 - k) + .3, k < .5 ? m.deep() : m.main(), 210 * on * Math.sin(Math.PI * k), i * 1.1);
        }
        for (int i = 0; i < 4; i++) {
            double a = i * Math.PI / 2 + .6;
            Point out = m.feet().plus(new Point(Math.cos(a) * 2.8, .12, Math.sin(a) * 2.8));
            double k = ((m.time() * .035 + i * .13) % 1 + 1) % 1;
            if (k > .75) bolt(b, m, out, m.feet().plus(new Point(0, .15, 0)), .016, 0, 3, m.seed() + i);
            var path = jag(out, m.feet().plus(new Point(0, .1, 0)), m.seed() + i, m.time(), 6, .15);
            b.poly(path, .008, m.deep(), 90 * on);
        }
        b.groundGlow(m.feet(), 1.1, m.main(), 120 * on);
        for (int sd = -1; sd <= 1; sd += 2) b.line(m.body(.2 * sd, 1.0), m.body(.2 * sd, .05), .01, m.main(), 130 * on);
    }

    /** Magnetic steps: ring plates under the feet, low field arcs reaching ahead, fading footprints behind. */
    static void magneticGroundStep(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 6.0) : m.fade(26);
        if (on <= 0) return;
        Point ahead = m.feet().plus(m.flat().scale(2.8));
        for (int k = 0; k < 4; k++) {
            double fade = 1 - k * .25;
            Point at = m.feet().minus(m.flat().scale(k * 0.9));
            b.groundRing(at, .9 - k * .09, m.main(), 210 * on * fade, m.time() * .05 + k);
            if (k > 0) b.streak(at.plus(new Point(0, .08, 0)), at.plus(m.flat().scale(.9)).plus(new Point(0, .08, 0)), .09, m.main(), 170 * on * fade);
        }
        b.groundGlow(m.feet(), .9, m.main(), 110 * on);
        fieldLoops(b, m.feet().plus(new Point(0, .1, 0)), ahead.plus(new Point(0, .1, 0)), 5, .9, m.main(), 170 * on, m.time(), .035);
        b.groundRing(ahead, .75, m.accent(), 220 * on, -m.time() * .06);
        b.orb(ahead.plus(new Point(0, .15, 0)), .14, m.accent(), 220 * on, m.seed());
        b.groundRing(ahead, .75 + .1 * Math.sin(m.time() * .2), m.main(), 140 * on, m.time() * .04);
    }

    /** A horseshoe magnet at the hand; field lines and a stream of chevrons draw things in. */
    static void magneticItemPull(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(22);
        if (on <= 0) return;
        Point c = m.at(.55, 0, 0);
        b.circle(c, m.s(), m.f(), .26, 20, Math.PI * .2, Math.PI * 1.6, .04, m.main(), 235 * on);
        b.circle(c, m.s(), m.f(), .2, 20, Math.PI * .2, Math.PI * 1.6, .016, m.accent(), 230 * on);
        b.sprite(c, .5, m.main(), 80 * on, Tex.BLOOM, 0, 1);
        Point reach = m.at(4.5, 0, -.3);
        for (int i = 0; i < 4 + m.lv(); i++) {
            double a = i * Math.PI * 2 / (4 + m.lv()) + m.time() * .01;
            Point start = reach.plus(m.s().scale(Math.cos(a) * 1.3)).plus(m.u().scale(Math.sin(a) * 1.0));
            Point ctrl = start.lerp(c, .5).plus(m.u().scale(.4 * Math.sin(a * 2)));
            Point prev = null;
            for (int k = 0; k <= 10; k++) {
                double t = k / 10.0;
                Point q = start.scale((1 - t) * (1 - t)).plus(ctrl.scale(2 * t * (1 - t))).plus(c.scale(t * t));
                if (prev != null) b.line(prev, q, .014, m.main(), 170 * on * (.3 + .7 * t));
                prev = q;
            }
            double ph = ((m.time() * .03 + i * .21) % 1);
            Point bead = start.scale((1 - ph) * (1 - ph)).plus(ctrl.scale(2 * ph * (1 - ph))).plus(c.scale(ph * ph));
            b.mote(bead, .09, m.accent(), 230 * on * Math.sin(Math.PI * ph));
        }
        chevronStream(b, reach, c, 4, .17, m.time(), .04, m.main(), 210 * on);
    }

    /** Iron sand drawn into a spear; the grains stay loose, then the lance thrusts and the grains scatter on impact. */
    static void ironSandLance(B b, M m) {
        Point dir = m.dir();
        if (m.held()) {
            double ch = easeOut(m.age() / 14.0);
            Point tip = m.hand().plus(dir.scale(.4 + 2.0 * ch));
            grains(b, m.hand(), tip, (int) (24 + 40 * ch), .35 * (1 - .5 * ch), .7, m.seed(), m.time(), m.deep(), 230);
            b.line(m.hand(), tip, .02, m.main(), 120 * ch);
            b.orb(tip, .06 + .05 * ch, m.accent(), 200 * ch, m.seed());
            return;
        }
        double t = m.since();
        if (t > 34) return;
        Point end = m.reach(18);
        double travel = easeOut(t / 5.0);
        Point head = m.hand().lerp(end, travel), tail = m.hand().lerp(end, Math.max(0, travel - 3.2 / Math.max(1, end.minus(m.hand()).length())));
        double live = m.fade(34);
        grains(b, tail, head, 70, .28, .6, m.seed(), m.time(), m.deep(), 220 * live);
        b.streak(tail, head, .05, m.main(), 230 * live);
        b.beam(tail.lerp(head, .4), head, .03, m.accent(), 200 * live);
        if (travel < 1) b.orb(head, .14, 0xFFFFFF, 230, m.seed());
        else {
            impact(b, m, end, m.normal(), .3, m.main());
            Random r = new Random(m.seed());
            for (int i = 0; i < 22; i++) {
                Point v = rndUnit(r);
                double sp = (1.2 + 2.5 * r.nextDouble()) * easeOut((t - 5) / 14.0);
                b.sprite(end.plus(v.scale(sp)).plus(new Point(0, -.002 * (t - 5) * (t - 5), 0)), .035, m.deep(), 210 * live, Tex.SPARK, r.nextDouble() * 6, 1);
            }
        }
    }

    /** A net of lightning strands: rings and meridians form a dome over the target, nodes bead the crossings, then it cinches shut. */
    static void thunderNet(B b, M m) {
        Point g = m.ground();
        double R = 3.6 + .5 * m.lv();
        if (m.held()) {
            double ch = easeOut(m.age() / 12.0);
            b.groundRing(g, R * ch, m.main(), 190, m.time() * .02);
            dome(b, g, m.flat(), R * ch, 3, 8, Math.PI * 2, m.main(), 70 * ch);
            return;
        }
        double t = m.since();
        if (t > 40) return;
        double open = easeOut(t / 6.0), cinch = easeIn((t - 8) / 12.0);
        double rad = R * open * (1 - .6 * cinch), live = m.fade(40);
        dome(b, g, m.flat(), rad, 4, 12, Math.PI * 2, m.main(), 190 * live);
        Random rnd = new Random(m.seed() + (long) (m.time() * 1.5));
        for (int i = 0; i < 7; i++) {
            double a = rnd.nextDouble() * Math.PI * 2, el = rnd.nextDouble() * 1.2;
            Point p = g.plus(new Point(Math.cos(a) * Math.cos(el) * rad, Math.sin(el) * rad, Math.sin(a) * Math.cos(el) * rad));
            var path = jag(p, g.plus(new Point(Math.cos(a + .5) * Math.cos(el) * rad, Math.sin(el + .3) * rad, Math.sin(a + .5) * Math.cos(el) * rad)), m.seed() + i, m.time(), 3, .12);
            for (int k = 1; k < path.size(); k++) b.lightning(path.get(k - 1), path.get(k), .012, m.accent(), 200 * live, m.seed() + k + i);
            b.mote(p, .08, m.accent(), 220 * live);
        }
        b.groundGlow(g, rad, m.main(), 110 * live);
        if (t > 8 && t < 12) b.flare(g.plus(new Point(0, .4, 0)), 3.0 * punch(t - 8), 0xFFFFFF, 220);
        grand(b, m, g, 2.6, m.main());
    }

    /** A stack of capacitor plates around the body; arcs hop between plates, a gauge ring fills. */
    static void potentialBuffer(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(36);
        if (on <= 0) return;
        double[] ys = {.35, 1.0, 1.65};
        for (int i = 0; i < 3; i++) {
            Point c = m.feet().plus(new Point(0, ys[i], 0));
            b.ring(c, new Point(1, 0, 0), new Point(0, 0, 1), .82, m.main(), 210 * on, m.time() * (.04 + .02 * i) * (i % 2 == 0 ? 1 : -1));
            if (i > 0) {
                Random r = new Random(m.seed() + i + (long) (m.time() * 1.5));
                double a = r.nextDouble() * Math.PI * 2;
                Point p = m.feet().plus(new Point(Math.cos(a) * .8, ys[i - 1], Math.sin(a) * .8)), q = m.feet().plus(new Point(Math.cos(a + .4) * .8, ys[i], Math.sin(a + .4) * .8));
                var path = jag(p, q, m.seed() + i, m.time(), 3, .06);
                for (int k = 1; k < path.size(); k++) b.lightning(path.get(k - 1), path.get(k), .01, m.accent(), 220 * on, m.seed() + k);
            }
        }
        double fill = m.held() ? (m.age() / 80.0) % 1 : 1;
        b.circle(m.feet().plus(new Point(0, 2.15, 0)), new Point(1, 0, 0), new Point(0, 0, 1), .5, 36, -Math.PI / 2, Math.PI * 2 * fill, .02, m.accent(), 230 * on);
        b.circle(m.feet().plus(new Point(0, 2.15, 0)), new Point(1, 0, 0), new Point(0, 0, 1), .5, 36, -Math.PI / 2, Math.PI * 2, .006, m.deep(), 110 * on);
        if (m.lv() >= 4 && m.since() >= 0) grand(b, m, m.feet(), 2.0, m.main());
    }

    /** Hexagon shield patches on a front hemisphere; a ripple of brightness sweeps over them. */
    static void pulseShield(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(40);
        if (on <= 0) return;
        double R = 1.5 + .6 * easeOut(m.age() / 10.0);
        Point c = m.chest();
        var pts = shell(c, m.f(), R, 16, 1.15);
        double sweep = (m.time() * .04) % 1;
        for (int i = 0; i < pts.size(); i++) {
            Point p = pts.get(i);
            Point nrm = norm(p.minus(c));
            Point[] pp = perp(nrm);
            double phase = ((double) i / pts.size() - sweep + 1) % 1;
            double glow = .5 + .5 * Math.exp(-phase * 5);
            b.hex(p, pp[0], pp[1], .5, i * .5, m.main(), 190 * on * glow);
        }
        Point[] pp = perp(m.f());
        b.ring(c.plus(m.f().scale(R * .99)), pp[0], pp[1], R * 0.55, m.accent(), 150 * on * (1 - sweep), m.time() * .02);
        if (m.since() >= 0 && m.since() < 12) shockRing(b, m, c.plus(m.f().scale(R * .5)), pp[0], pp[1], R, 0, 12, m.accent());
        if (m.lv() >= 4 && m.since() >= 0) grand(b, m, m.feet(), 2.2, m.main());
    }

    /** A field dome: dipole field lines loop between the poles above and below the caster's domain, iron-sand motes stream along them. */
    static void magneticDomain(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 14.0) : m.fade(46);
        if (on <= 0) return;
        double R = 6.5;
        Point c = m.feet().plus(new Point(0, 2.0, 0));
        Point top = c.plus(new Point(0, 4.2, 0)), bot = c.minus(new Point(0, 2.0, 0));
        int n = 12;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + m.time() * .004;
            Point out = new Point(Math.cos(a) * R * on, 0, Math.sin(a) * R * on);
            Point mid = c.plus(out);
            Point prev = null;
            Point ctrlT = c.plus(out.scale(1.0)).plus(new Point(0, 4.5, 0)), ctrlB = c.plus(out.scale(1.0)).minus(new Point(0, 3.0, 0));
            for (int k = 0; k <= 18; k++) {
                double t = k / 18.0;
                Point q = t < .5
                        ? top.scale((1 - 2 * t) * (1 - 2 * t)).plus(ctrlT.scale(2 * (2 * t) * (1 - 2 * t))).plus(mid.scale(4 * t * t))
                        : mid.scale((2 - 2 * t) * (2 - 2 * t)).plus(ctrlB.scale(2 * (2 * t - 1) * (2 - 2 * t))).plus(bot.scale((2 * t - 1) * (2 * t - 1)));
                if (prev != null) b.line(prev, q, .012, m.main(), 150 * on);
                prev = q;
            }
            double ph = ((m.time() * .012 + i * .09) % 1 + 1) % 1;
            Point bead = ph < .5
                    ? top.scale((1 - 2 * ph) * (1 - 2 * ph)).plus(ctrlT.scale(2 * (2 * ph) * (1 - 2 * ph))).plus(mid.scale(4 * ph * ph))
                    : mid.scale((2 - 2 * ph) * (2 - 2 * ph)).plus(ctrlB.scale(2 * (2 * ph - 1) * (2 - 2 * ph))).plus(bot.scale((2 * ph - 1) * (2 * ph - 1)));
            b.mote(bead, .07, m.accent(), 220 * on);
        }
        b.groundRing(m.feet(), R * on, m.main(), 180 * on, m.time() * .005);
        b.groundRing(m.feet(), R * .55 * on, m.deep(), 140 * on, -m.time() * .008);
        b.orb(top, .22, m.accent(), 220 * on * flicker(m.seed(), m.time()), m.seed());
        b.orb(bot, .22, m.main(), 200 * on * flicker(m.seed() + 1, m.time()), m.seed() + 1);
        if (m.since() >= 0) grand(b, m, m.feet(), 4.5, m.main());
    }

    /** Twin rails with sleepers laid down from the hand to the target; a pulse races along and lights a relay node at the end. */
    static void railRelay(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(40);
        if (on <= 0) return;
        Point tip = m.reach(22);
        Point dir = norm(tip.minus(m.hand()));
        double len = tip.minus(m.hand()).length();
        double lay = m.held() ? easeOut(m.age() / 10.0) : 1;
        Point head = m.hand().lerp(tip, lay);
        Point side = norm(dir.cross(UP)).scale(.22);
        b.line(m.hand().plus(side), head.plus(side), .018, m.main(), 220 * on);
        b.line(m.hand().minus(side), head.minus(side), .018, m.main(), 220 * on);
        int ties = (int) (len * lay / .9);
        for (int i = 1; i <= ties; i++) {
            Point p = m.hand().lerp(tip, i * .9 / len);
            b.line(p.plus(side.scale(1.3)), p.minus(side.scale(1.3)), .012, m.deep(), 150 * on);
        }
        if (!m.held()) {
            double t = easeIn(m.since() / 7.0);
            if (t < 1) comet(b, m.hand().lerp(tip, t), dir, 2.5, 0xFFFFFF, 240 * on);
            else {
                Point[] pp = perp(dir);
                b.hex(tip, pp[0], pp[1], .6, m.time() * .02, m.main(), 230 * on);
                pillar(b, tip, 3.0, .03, m.main(), 200 * on);
                b.flare(tip.plus(new Point(0, .6, 0)), 2.0, m.main(), 200 * on);
            }
            if (m.lv() >= 5) grand(b, m, m.ground(), 3.0, m.main());
        }
    }

    /** Three counter-rotating coil rings around the forearm with a bead racing each, and short arcs hopping between the outer rings. */
    static void magneticBracer(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(40);
        if (on <= 0) return;
        for (int i = 0; i < 3; i++) {
            Point c = m.hand().minus(m.f().scale(.08 + .17 * i));
            double r = .22 + .025 * i, dir = i % 2 == 0 ? 1 : -1;
            b.ring(c, m.s(), m.u(), r, m.main(), 215 * on, m.time() * (.12 + .05 * i) * dir);
            double a = m.time() * (.22 + .08 * i) * dir;
            b.mote(c.plus(m.s().scale(Math.cos(a) * r * .8)).plus(m.u().scale(Math.sin(a) * r * .8)), .07, m.accent(), 240 * on);
        }
        if (flicker(m.seed(), m.time()) > .66) {
            Random rnd = new Random(m.seed() + (long) (m.time() * 1.5));
            double a1 = rnd.nextDouble() * Math.PI * 2, a2 = a1 + .8;
            Point p = m.hand().plus(m.s().scale(Math.cos(a1) * .12)).plus(m.u().scale(Math.sin(a1) * .12));
            Point q = m.hand().minus(m.f().scale(.42)).plus(m.s().scale(Math.cos(a2) * .14)).plus(m.u().scale(Math.sin(a2) * .14));
            var path = jag(p, q, m.seed(), m.time(), 4, .04);
            for (int k = 1; k < path.size(); k++) b.lightning(path.get(k - 1), path.get(k), .007, m.main(), 220 * on, m.seed() + k);
        }
        b.sprite(m.hand(), .7, m.main(), 90 * on, Tex.BLOOM, 0, 1);
        if (m.since() >= 0 && m.since() < 6) b.flare(m.hand(), .9 * punch(m.since()), m.main(), 200);
    }
}
