package cn.academy.port.client;

import static cn.academy.port.client.MotifKit.*;

import cn.academy.port.client.MotifRenderer.Tex;
import cn.academy.port.client.RailThunderArrayGeometry.Point;
import java.util.ArrayList;
import java.util.Random;

/**
 * Vector manipulation motifs: pressure fronts, streamlines, chevron flows, shock cones, ground ripples, spinning discs. Shapes show forces
 * and direction of flow; there are no sparks or arcs here, that is the electromaster's language.
 */
final class VmMotifs {
    private VmMotifs() {}

    private static final Point UP = new Point(0, 1, 0);

    /** Pressure gathers in front of the palm (rings contracting), then a cone of rings, speed lines and a star lands on the target. */
    static void dirShock(B b, M m) {
        Point c = m.at(.7, 0, 0);
        Point dir = m.dir();
        Point[] pp = perp(m.f());
        if (m.held()) {
            double ch = easeIn(m.age() / 10.0);
            for (int i = 0; i < 3; i++) {
                double ph = ((m.time() * .06 + i / 3.0) % 1);
                b.ring(c.plus(m.f().scale(.25 * ph)), pp[0], pp[1], .6 * (1 - ph) + .05, m.main(), 190 * ch * Math.sin(Math.PI * ph), i);
            }
            chevronStream(b, c.plus(m.f().scale(1.2)), c, 2, .1, m.time(), .05, m.main(), 190 * ch);
            return;
        }
        double t = m.since();
        if (t > 26) return;
        Point end = m.reach(14);
        double len = end.minus(c).length();
        for (int i = 0; i < 4; i++) {
            double x = (t - i * 1.1) / 8.0;
            if (x <= 0 || x >= 1) continue;
            double d = len * easeOut(x);
            Point[] dp = perp(dir);
            b.ring(c.plus(dir.scale(d)), dp[0], dp[1], .3 + d * .22, i == 0 ? 0xFFFFFF : m.main(), 220 * (1 - x) * (1 - x), i * 1.7);
        }
        speedLines(b, t * .05, c, dir, len, 12, .7, .09, m.seed(), m.main(), 220 * m.fade(14));
        if (t > 3) {
            M sub = m.later(3);
            spot(b, sub, end, m.normal(), .22, m.main());
            dust(b, sub, end, 1.0, 4, 0xC8D4E4);
        }
        flash(b, m, c, .15, m.main());
    }

    /** A pressure wave rolls out across the ground ahead of the feet: a fast expanding ring, a crack web, dust thrown up behind the front. */
    static void groundShock(B b, M m) {
        Point g = m.feet();
        double t = m.held() ? m.age() * .5 : m.since();
        if (!m.held() && t > 34) return;
        if (m.held()) {
            double ch = easeIn(m.age() / 10.0);
            b.groundRing(g, 1.6 - 1.0 * ch, m.main(), 200 * ch, m.time() * .06);
            b.groundGlow(g, 1.0, m.main(), 100 * ch);
            return;
        }
        double R = 7.0;
        for (int i = 0; i < 3; i++) {
            double x = (t - i * 1.8) / 16.0;
            if (x <= 0 || x >= 1) continue;
            b.groundRing(g, R * easeOut(x), i == 0 ? 0xFFFFFF : m.main(), 220 * Math.pow(1 - x, 1.3), i * 2.2);
        }
        cracks(b, g, m.seed(), 3.6, 8, Math.min(1, t / 6.0), m.main(), 200 * m.fade(32));
        dust(b, m, g.plus(m.flat().scale(2.0)), 3.0, 8, 0xC8D4E4);
        b.groundGlow(g, 2.0, m.main(), 90 * m.fade(20));
    }

    /** Chevron streams wrap the body along the line of travel, speed lines peel off the shoulders and the feet leave skid lines. */
    static void vecAccel(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 6.0) : m.fade(24);
        if (on <= 0) return;
        Point fl = m.flat();
        Point side = norm(fl.cross(UP));
        for (int sd = -1; sd <= 1; sd += 2) {
            Point a = m.feet().plus(new Point(0, 1.2, 0)).minus(fl.scale(1.6)).plus(side.scale(.55 * sd));
            Point e = a.plus(fl.scale(3.6));
            chevronStream(b, a, e, 4, .17, m.time(), .045, m.main(), 200 * on);
        }
        chevronStream(b, m.feet().plus(new Point(0, .08, 0)).minus(fl.scale(1.5)), m.feet().plus(new Point(0, .08, 0)).plus(fl.scale(2.5)), 3, .22, m.time(), .05, m.deep(), 190 * on);
        speedLines(b, m.time(), m.feet().plus(new Point(0, 1.2, 0)).plus(fl.scale(.3)), fl.scale(-1), 4.0, 12, .7, .06, m.seed(), m.main(), 190 * on);
        b.line(m.feet().plus(new Point(0, .05, 0)).plus(side.scale(.3)), m.feet().plus(new Point(0, .05, 0)).plus(side.scale(.3)).minus(fl.scale(3.2)), .03, m.deep(), 140 * on);
        b.line(m.feet().plus(new Point(0, .05, 0)).minus(side.scale(.3)), m.feet().plus(new Point(0, .05, 0)).minus(side.scale(.3)).minus(fl.scale(3.2)), .03, m.deep(), 140 * on);
        if (m.since() >= 0 && m.since() < 6) b.flare(m.chest(), 1.6 * punch(m.since()), m.main(), 200);
    }

    /** Streamlines flowing toward the caster bend around an invisible disc held out in front: the flow is turned, not stopped. */
    static void vecDeviation(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(30);
        if (on <= 0) return;
        Point c = m.at(1.9, 0, 0);
        double A = .85 + .1 * m.lv();
        for (int plane = 0; plane < 2; plane++) {
            Point lat = plane == 0 ? m.s() : m.u();
            for (int k = -2; k <= 2; k++) {
                if (k == 0 && plane == 1) continue;
                double y = k * .32;
                var pts = new ArrayList<Point>();
                for (int i = 0; i <= 18; i++) {
                    double x = -3.0 + 6.0 * i / 18.0;
                    double off = y * (1 + A * Math.exp(-x * x / 1.1)) + (k == 0 ? 0 : 0);
                    pts.add(c.plus(m.f().scale(x)).plus(lat.scale(off)));
                }
                b.poly(pts, .02, m.main(), 190 * on * (1 - Math.abs(k) * .12));
                double t = ((m.time() * .04 + k * .13 + plane * .3) % 1 + 1) % 1;
                Point p = along(pts, t), q = along(pts, Math.min(1, t + .03));
                Point tan = norm(q.minus(p));
                b.plate(p, tan.scale(.12), perp(tan)[0].scale(.12), m.accent(), 220 * on * Math.sin(Math.PI * t), Tex.CHEVRON);
            }
        }
        b.ring(c, m.s(), m.u(), .42, m.main(), 160 * on, m.time() * .03);
        if (m.since() >= 0 && m.since() < 10) shockRing(b, m, c, m.s(), m.u(), 1.6, 0, 10, m.main());
    }

    /** A heavy blast: a pressure disc rides ahead of a cone of rings, debris is thrown along the line, the ground behind it cracks. */
    static void dirBlast(B b, M m) {
        Point c = m.at(.7, 0, 0);
        Point dir = m.dir();
        if (m.held()) {
            double ch = easeIn(m.age() / 14.0);
            Point[] pp = perp(m.f());
            for (int i = 0; i < 4; i++) {
                double ph = ((m.time() * .05 + i / 4.0) % 1);
                b.ring(c.plus(m.f().scale(.6 * ph)), pp[0], pp[1], .9 * (1 - ph) + .06, m.main(), 200 * ch * Math.sin(Math.PI * ph), i);
            }
            b.sprite(c, .3 + .3 * ch, m.deep(), 150 * ch, Tex.VORTEX, m.time() * .2, 1);
            return;
        }
        double t = m.since();
        if (t > 40) return;
        Point end = m.reach(18);
        double len = end.minus(c).length();
        Point[] dp = perp(dir);
        double front = len * easeOut(t / 9.0);
        b.ring(c.plus(dir.scale(front)), dp[0], dp[1], 1.0 + front * .15, 0xFFFFFF, 230 * Math.max(0, 1 - t / 14.0), 0);
        b.ring(c.plus(dir.scale(front)), dp[0], dp[1], .8 + front * .15, m.main(), 200 * Math.max(0, 1 - t / 14.0), 1.2);
        for (int i = 1; i <= 5; i++) {
            double d = front * (1 - i * .16);
            if (d < 0) continue;
            b.ring(c.plus(dir.scale(d)), dp[0], dp[1], .5 + d * .18, m.main(), 160 * Math.max(0, 1 - t / 16.0) * (1 - i * .14), i * .9);
        }
        speedLines(b, t * .05, c, dir, len, 18, 1.0, .09, m.seed(), m.main(), 220 * m.fade(16));
        Random r = new Random(m.seed());
        for (int i = 0; i < 16; i++) {
            double k = easeOut((t - r.nextDouble() * 2) / 12.0);
            if (k <= 0) continue;
            Point p = c.plus(dir.scale(len * (.2 + .8 * r.nextDouble()) * k)).plus(dp[0].scale((r.nextDouble() - .5) * 2.2)).plus(new Point(0, r.nextDouble() * 1.5 * Math.sin(Math.PI * Math.min(1, k)), 0));
            b.sprite(p, .06 + .05 * r.nextDouble(), m.accent(), 220 * m.fade(24), Tex.SHARD, r.nextDouble() * 6 + t * .2, 1);
        }
        if (t > 4) {
            M sub = m.later(4);
            impact(b, sub, end, m.normal(), .35, m.main());
            dust(b, sub, end, 1.8, 6, 0xC8D4E4);
            cracks(b, m.ground(), m.seed() + 1, 2.4, 6, Math.min(1, (t - 4) / 5.0), m.main(), 190 * m.fade(36));
        }
        flash(b, m, c, .25, m.main());
        grand(b, m.later(4), m.ground(), 2.4, m.main());
    }

    /** A vertical vessel pair winds up the target's body; chevrons run backwards down it toward the heart where rings throb. */
    static void bloodRetro(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(40);
        if (on <= 0) return;
        Point g = m.ground();
        Point top = g.plus(new Point(0, 2.0, 0)), heart = g.plus(new Point(0, 1.25, 0));
        for (int k = 0; k < 2; k++) {
            helix(b, g.plus(new Point(0, .05, 0)), top, .34, 2.4, m.time() * -.08 + k * Math.PI, k == 0 ? m.main() : m.accent(), 170 * on, .016);
        }
        for (int i = 0; i < 6; i++) {
            double ph = ((m.time() * .035 + i / 6.0) % 1);
            double y = ph < .5 ? 2.0 * (1 - ph * 2) * 1.0 + 0.0 : 0;   // from the head toward the heart, then from the feet toward the heart
            Point from = ph < .5 ? top : g;
            Point p = from.lerp(heart, ph < .5 ? ph * 2 : (ph - .5) * 2);
            Point tan = norm(heart.minus(from));
            Point side = perp(tan)[0];
            b.plate(p.plus(side.scale(.34)), tan.scale(.12), perp(tan)[1].scale(.12), m.main(), 220 * on, Tex.CHEVRON);
        }
        double beat = Math.pow(Math.max(0, Math.sin(m.time() * .25)), 6);
        b.orb(heart, .15 + .1 * beat, m.accent(), 190 * on, m.seed());
        b.ring(heart, new Point(1, 0, 0), new Point(0, 1, 0), .3 + .5 * beat, m.main(), 180 * on * (1 - beat * .4), 0);
        b.ring(heart, new Point(0, 0, 1), new Point(0, 1, 0), .3 + .5 * beat, m.main(), 180 * on * (1 - beat * .4), 1);
        b.groundRing(g, .8, m.deep(), 150 * on, m.time() * .03);
        b.sprite(g.plus(new Point(0, 1.0, 0)), 1.1, m.deep(), 60 * on, Tex.VORTEX, -m.time() * .05, 1);
        if (m.since() >= 0) grand(b, m, g, 2.6, m.main());
    }

    /** A curved reflecting pane stands in front; incoming and outgoing arrows meet it at mirror angles, ripples leave the contact point. */
    static void vecReflection(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(40);
        if (on <= 0) return;
        Point c = m.at(2.1, 0, .1);
        Point[] pp = {m.s(), m.u()};
        b.plate(c, pp[0].scale(1.0), pp[1].scale(1.15), m.main(), 130 * on, Tex.SCAN);
        b.plate(c.plus(m.f().scale(.03)), pp[0].scale(.9), pp[1].scale(1.0), m.accent(), 60 * on, Tex.LATTICE);
        for (int i = 0; i < 4; i++) {   // rim as four chunky corner ticks
            double sx = (i % 2 == 0 ? 1 : -1), sy = (i < 2 ? 1 : -1);
            Point corner = c.plus(pp[0].scale(sx)).plus(pp[1].scale(sy * 1.15));
            b.line(corner, corner.minus(pp[0].scale(sx * .35)), .02, m.main(), 220 * on);
            b.line(corner, corner.minus(pp[1].scale(sy * .35)), .02, m.main(), 220 * on);
        }
        double ph = ((m.time() * .035) % 1);
        Point inFrom = c.plus(m.f().scale(2.6)).plus(pp[0].scale(-1.8)).plus(pp[1].scale(.9));
        Point outTo = c.plus(m.f().scale(2.6)).plus(pp[0].scale(1.8)).plus(pp[1].scale(.9));
        b.line(inFrom, c, .014, m.deep(), 150 * on);
        b.line(c, outTo, .014, m.main(), 180 * on);
        Point p = ph < .5 ? inFrom.lerp(c, ph * 2) : c.lerp(outTo, (ph - .5) * 2);
        Point dirv = ph < .5 ? norm(c.minus(inFrom)) : norm(outTo.minus(c));
        b.plate(p, dirv.scale(.2), perp(dirv)[0].scale(.2), m.accent(), 230 * on, Tex.CHEVRON);
        double rip = (m.time() * .05) % 1;
        b.ring(c, pp[0], pp[1], .15 + 1.0 * rip, m.main(), 200 * on * (1 - rip), m.time() * .01);
        if (m.since() >= 0 && m.since() < 6) b.flare(c, 1.8 * punch(m.since()), m.main(), 210);
        if (m.lv() >= 4 && m.since() >= 0) grand(b, m, m.feet(), 2.4, m.main());
    }

    /** One crescent sweeps across in front of the palm and lands as a star on the target. */
    static void deflectionStrike(B b, M m) {
        Point c = m.at(.95, 0, 0);
        if (m.held()) {
            double ch = easeOut(m.age() / 8.0);
            b.circle(c, m.s(), m.u(), .6, 18, Math.PI * .25, Math.PI * .5 * ch, .012, m.main(), 160 * ch);
            return;
        }
        double t = m.since();
        if (t > 20) return;
        double sweep = easeOut(t / 3.0);
        double arc = Math.PI * 1.1 * sweep, a0 = -Math.PI * .1;
        double live = m.fade(14);
        b.circle(c, m.s(), m.u(), .85, 24, a0, arc, .1, m.main(), 160 * live);
        b.circle(c, m.s(), m.u(), .85, 24, a0, arc, .045, 0xFFFFFF, 240 * live);
        Point tipPoint = c.plus(m.s().scale(Math.cos(a0 + arc) * .85)).plus(m.u().scale(Math.sin(a0 + arc) * .85));
        b.orb(tipPoint, .1, 0xFFFFFF, 230 * live, m.seed());
        Point end = m.reach(5);
        spot(b, m.later(2), end, m.normal(), .18, m.main());
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3 + .3;
            Point o = end.plus(m.s().scale(Math.cos(a) * .2)).plus(m.u().scale(Math.sin(a) * .2));
            chevronStream(b, o, o.plus(m.s().scale(Math.cos(a) * .7)).plus(m.u().scale(Math.sin(a) * .7)), 1, .07, t * .06, 1.0, m.accent(), 200 * live);
        }
    }

    /** A crescent pane held in front, turning to face the threat; sparks of pressure fly from the contact. */
    static void vectorParry(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 6.0) : m.fade(24);
        if (on <= 0) return;
        Point c = m.at(1.1, 0, 0);
        double tilt = Math.sin(m.time() * .07) * .35;
        b.circle(c, m.s(), m.u(), .72, 22, Math.PI * .5 + tilt - .85, 1.7, .09, m.main(), 150 * on);
        b.circle(c, m.s(), m.u(), .72, 22, Math.PI * .5 + tilt - .85, 1.7, .04, 0xFFFFFF, 235 * on);
        b.circle(c, m.s(), m.u(), .55, 18, Math.PI * .5 + tilt - .6, 1.2, .01, m.main(), 160 * on);
        Point contact = c.plus(m.u().scale(.72 * Math.cos(tilt))).plus(m.s().scale(-.72 * Math.sin(tilt)));
        for (int i = 0; i < 5; i++) {
            double ph = ((m.time() * .09 + i * .2) % 1);
            Point v = m.f().scale(.9).plus(m.s().scale(Math.cos(i * 2.0) * .8)).plus(m.u().scale(Math.sin(i * 2.0) * .5));
            b.streak(contact.plus(v.scale(ph * .5)), contact.plus(v.scale(ph * .5 + .22)), .012, m.accent(), 200 * on * (1 - ph));
        }
        b.ring(c.plus(m.f().scale(.05)), m.s(), m.u(), .4 + .2 * ((m.time() * .06) % 1), m.main(), 130 * on, 0);
        if (m.since() >= 0 && m.since() < 6) b.flare(contact, 1.2 * punch(m.since()), m.main(), 200);
    }

    /** Pressure collapses onto the touched point: rings contract, two clamp bars close in, and every moving mote is slowed to a stop. */
    static void brakingTouch(B b, M m) {
        Point c = m.reach(2.6);
        double t = m.held() ? m.age() : m.since();
        double on = m.held() ? easeOut(m.age() / 6.0) : m.fade(30);
        if (on <= 0) return;
        Point[] pp = perp(m.dir());
        for (int i = 0; i < 3; i++) {
            double ph = ((t * .05 + i / 3.0) % 1);
            b.ring(c, pp[0], pp[1], 1.7 * (1 - ph) + .15, m.main(), 230 * on * Math.sin(Math.PI * ph), i * 1.1);
        }
        double gap = m.held() ? 1.0 - .7 * easeOut(m.age() / 10.0) : .3;
        for (int sd = -1; sd <= 1; sd += 2) {
            Point bc = c.plus(pp[0].scale(gap * sd));
            b.line(bc.plus(pp[1].scale(.8)), bc.minus(pp[1].scale(.8)), .07, m.main(), 235 * on);
            b.line(bc.plus(pp[1].scale(.8)), bc.minus(pp[1].scale(.8)), .025, 0xFFFFFF, 220 * on);
        }
        for (int i = 0; i < 6; i++) {
            double a = i * Math.PI / 3, ph = ((t * .02 + i * .17) % 1);
            Point p = c.plus(pp[0].scale(Math.cos(a) * .9 * (1 - ph * ph))).plus(pp[1].scale(Math.sin(a) * .9 * (1 - ph * ph)));
            b.mote(p, .07, m.accent(), 230 * on * (1 - ph * .7));
        }
        b.orb(c, .16, m.main(), 220 * on, m.seed());
    }

    /** The caster is pinned: a stack of rings narrows into the ground like a spike, chains drop from the shoulders, the ground cracks around it. */
    static void inertialAnchor(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(40);
        if (on <= 0) return;
        double sink = m.held() ? easeOut(m.age() / 8.0) : 1;
        for (int i = 0; i < 5; i++) {
            double y = .9 - i * .26;
            b.ring(m.feet().plus(new Point(0, y * sink, 0)), new Point(1, 0, 0), new Point(0, 0, 1), .8 - i * .15, i < 2 ? m.accent() : m.main(), 200 * on * (1 - i * .1), m.time() * .02 * (i % 2 == 0 ? 1 : -1));
        }
        for (int sd = -1; sd <= 1; sd += 2) {
            Point sh = m.body(.45 * sd, 1.4);
            Point foot = m.feet().plus(m.s().scale(.7 * sd)).plus(new Point(0, .05, 0));
            var pts = new ArrayList<Point>();
            for (int k = 0; k <= 5; k++) pts.add(sh.lerp(foot, k / 5.0).plus(m.f().scale(Math.sin(k * 1.9) * .06)));
            b.poly(pts, .016, m.deep(), 150 * on);
            for (int k = 1; k < 5; k++) b.mote(pts.get(k), .035, m.main(), 190 * on);
        }
        cracks(b, m.feet(), m.seed(), 2.4, 6, Math.min(1, (m.held() ? m.age() : m.since()) / 6.0), m.main(), 180 * on);
        b.groundRing(m.feet(), 1.2, m.main(), 160 * on, m.time() * .01);
        if (m.since() >= 0 && m.since() < 6) {
            shockRing(b, m, m.feet().plus(new Point(0, .05, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 2.8, 0, 12, m.main());
            dust(b, m, m.feet(), 1.4, 5, 0xC8D4E4);
        }
    }

    /** A palm opens a vertical vortex disc and curved arrows spin around it; on release the disc is thrown with a ring of pressure. */
    static void yawPalm(B b, M m) {
        Point c = m.at(.9, 0, 0);
        double ch = m.held() ? easeOut(m.age() / 8.0) : 1;
        double t = m.held() ? 0 : m.since();
        if (t > 22) return;
        double live = m.held() ? 1 : m.fade(20);
        double travel = m.held() ? 0 : easeOut(t / 6.0) * 2.5;
        Point cc = c.plus(m.dir().scale(travel));
        double r = .55 + .15 * ch + travel * .12;
        disc(b, cc, m.s(), m.u(), r, m.time() * .35, Tex.VORTEX, m.main(), 220 * live * ch);
        disc(b, cc, m.s(), m.u(), r * .55, -m.time() * .5, Tex.VORTEX, m.accent(), 190 * live * ch);
        for (int i = 0; i < 3; i++) {
            double a0 = m.time() * .2 + i * Math.PI * 2 / 3;
            b.circle(cc, m.s(), m.u(), r * 1.2, 10, a0, .9, .02, m.main(), 190 * live);
            Point head = cc.plus(m.s().scale(Math.cos(a0 + .9) * r * 1.2)).plus(m.u().scale(Math.sin(a0 + .9) * r * 1.2));
            Point tan = norm(m.s().scale(-Math.sin(a0 + .9)).plus(m.u().scale(Math.cos(a0 + .9))));
            b.plate(head, tan.scale(.12), perp(tan)[0].scale(.12), m.accent(), 230 * live, Tex.CHEVRON);
        }
        if (!m.held()) shockRing(b, m, cc, m.s(), m.u(), 2.0, 0, 12, m.main());
    }

    /** Slow, even ring waves leave the body and rise as soft motes; nothing sharp, nothing fast. */
    static void circulationRelief(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 12.0) : m.fade(50);
        if (on <= 0) return;
        for (int i = 0; i < 4; i++) {
            double ph = ((m.time() * .014 + i / 4.0) % 1);
            b.ring(m.feet().plus(new Point(0, .08 + ph * .6, 0)), new Point(1, 0, 0), new Point(0, 0, 1), 1.2 + 2.6 * ph, m.main(), 170 * on * Math.sin(Math.PI * ph), i * 1.3);
        }
        b.sprite(m.chest(), 1.5, m.main(), 70 * on, Tex.BLOOM, 0, 1);
        risers(b, m.feet(), .9, 2.3, 14, m.time(), m.seed(), m.accent(), 200 * on);
        for (int sd = -1; sd <= 1; sd += 2) {
            var pts = new ArrayList<Point>();
            for (int k = 0; k <= 12; k++) {
                double y = .1 + 1.9 * k / 12.0;
                pts.add(m.feet().plus(new Point(.55 * sd * Math.cos(k * .5 + m.time() * .03), y, .55 * Math.sin(k * .5 + m.time() * .03))));
            }
            b.poly(pts, .009, m.main(), 110 * on);
        }
    }

    /** Sonar: curved wavefronts converge on the head from far away, each leaving a ping on the ground where it passed. */
    static void circulationListening(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(50);
        if (on <= 0) return;
        Point head = m.feet().plus(new Point(0, 1.6, 0));
        Point fl = m.flat();
        Point side = norm(fl.cross(UP));
        for (int i = 0; i < 4; i++) {
            double ph = ((m.time() * .025 + i / 4.0) % 1);
            double r = 4.2 * (1 - ph) + .6;
            double alpha = 220 * on * Math.sin(Math.PI * ph);
            Point wave = m.feet().plus(fl.scale(r)).plus(new Point(0, 1.3, 0));   // a bowl-shaped wavefront standing in front, closing on the listener
            double rad = .9 + r * .4;
            b.circle(wave, side, UP, rad, 28, -Math.PI * .1, Math.PI * 1.2, .06, m.main(), alpha);
            b.circle(wave, side, UP, rad * .97, 28, -Math.PI * .1, Math.PI * 1.2, .025, m.accent(), alpha * .8);
            Point pingAt = m.feet().plus(fl.scale(r)).plus(new Point(0, .05, 0));
            b.groundRing(pingAt, .35 + .3 * ph, m.main(), 200 * on * Math.sin(Math.PI * ph), i);
        }
        b.circle(head.plus(side.scale(.28)), side, UP, .2, 16, -Math.PI / 2, Math.PI, .012, m.accent(), 200 * on);
        b.circle(head.minus(side.scale(.28)), side, UP, .2, 16, Math.PI / 2, Math.PI, .012, m.accent(), 200 * on);
        b.sprite(head, 1.0, m.main(), 110 * on, Tex.BLOOM, 0, 1);
    }

    /** An armillary of force: a ring of arrows turns at chest height inside two tilted gyro rings with small satellites on them. */
    static void autonomousVectorCircle(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 10.0) : m.fade(60);
        if (on <= 0) return;
        Point c = m.chest().plus(new Point(0, .15, 0));
        double R = 1.5;
        b.ring(c, new Point(1, 0, 0), new Point(0, 0, 1), R, m.main(), 180 * on, m.time() * .02);
        int n = 8;
        for (int i = 0; i < n; i++) {
            double a = m.time() * .03 + i * Math.PI * 2 / n;
            Point p = c.plus(new Point(Math.cos(a) * R, 0, Math.sin(a) * R));
            Point tan = new Point(-Math.sin(a), 0, Math.cos(a));
            b.plate(p, tan.scale(.22), UP.scale(.22), i % 2 == 0 ? m.accent() : m.main(), 230 * on, Tex.CHEVRON);
        }
        for (int k = 0; k < 2; k++) {
            double tilt = (k == 0 ? 1 : -1) * Math.PI / 3;
            Point u = new Point(Math.cos(m.time() * .015 * (k == 0 ? 1 : -1)), 0, Math.sin(m.time() * .015 * (k == 0 ? 1 : -1)));
            Point v = UP.scale(Math.sin(tilt)).plus(norm(u.cross(UP)).scale(Math.cos(tilt)));
            b.ring(c, u, norm(v), R * .8, k == 0 ? m.main() : m.deep(), 170 * on, m.time() * .04);
            double a = m.time() * .06 * (k == 0 ? 1 : -1);
            b.orb(c.plus(u.scale(Math.cos(a) * R * .8)).plus(norm(v).scale(Math.sin(a) * R * .8)), .09, m.accent(), 220 * on, m.seed() + k);
        }
        b.orb(c, .14, m.accent(), 180 * on * flicker(m.seed(), m.time()), m.seed());
        b.groundRing(m.feet(), R * 1.1, m.main(), 150 * on, m.time() * .02);
        if (m.since() >= 0) grand(b, m, m.feet(), 3.4, m.main());
    }

    /** One incoming force arrow meets a node and divides into three; the node throbs and sheds a shock ring each time. */
    static void impulseSplit(B b, M m) {
        double on = m.held() ? easeOut(m.age() / 8.0) : m.fade(44);
        if (on <= 0) return;
        Point node = m.at(3.0, 0, 0);
        Point from = m.hand();
        arrow(b, from.plus(m.f().scale(.5)), node.minus(m.f().scale(.15)), .3, m.main(), 220 * on);
        chevronStream(b, from.plus(m.f().scale(.5)), node, 3, .16, m.time(), .04, m.accent(), 200 * on);
        double spread = m.held() ? easeOut(m.age() / 10.0) : 1;
        for (int i = -1; i <= 1; i++) {
            Point out = node.plus(m.f().scale(3.0)).plus(m.s().scale(2.2 * i * spread)).plus(m.u().scale((i == 0 ? .0 : -.3) * spread));
            arrow(b, node.plus(norm(out.minus(node)).scale(.2)), out, .3, i == 0 ? m.accent() : m.main(), 220 * on);
            chevronStream(b, node, out, 2, .13, m.time() + i * 3, .045, m.main(), 190 * on);
        }
        double beat = (m.time() * .06) % 1;
        b.orb(node, .18 + .05 * Math.sin(m.time() * .3), m.accent(), 230 * on, m.seed());
        b.ring(node, m.s(), m.u(), .3 + 1.2 * beat, m.main(), 200 * on * (1 - beat), 0);
        if (!m.held()) {
            flash(b, m, node, .3, m.main());
            grand(b, m, m.ground(), 3.0, m.main());
        }
    }
}
