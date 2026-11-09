package cn.academy.port.highpower.core;

import java.util.Random;

/** Counterexample-first witnesses for V-ADV-01 Vector Construct Manipulation rules. */
public final class ConstructRulesRegressionTest {
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }

    public static void main(String[] args) {
        // Locked below LV4; unlock budgets match the design starts and never exceed the hard caps.
        for (double l : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, -3, 0, 1, 3.999}) {
            check(ConstructRules.maxBlocks(l) == 0 && ConstructRules.maxSpan(l) == 0 && ConstructRules.throwSpeed(l) == 0 && ConstructRules.maxAccel(l) == 0, "locked at " + l);
            check(!ConstructRules.withinBudget(1, 1, l), "nothing grabbable at " + l);
        }
        check(ConstructRules.maxBlocks(4) == 512 && ConstructRules.maxBlocks(5) == 4096 && ConstructRules.maxBlocks(99) == 4096, "block budget 512..4096");
        check(ConstructRules.maxSpan(4) == 16 && ConstructRules.maxSpan(5) == 32 && ConstructRules.maxSpan(1e9) == 32, "span 16..32");
        check(ConstructRules.maxBlocks(Double.POSITIVE_INFINITY) == 0, "infinite level is not a free pass");
        for (double l = 4; l <= 5.0001; l += .01) {
            check(ConstructRules.maxBlocks(l) >= ConstructRules.maxBlocks(Math.max(4, l - .01)), "budget monotone");
            check(ConstructRules.maxSpan(l) <= ConstructRules.ABS_MAX_SPAN && ConstructRules.maxBlocks(l) <= ConstructRules.ABS_MAX_BLOCKS, "caps hold");
        }
        // Counterexamples: zero/negative blocks, over-span, over-count are all refused.
        check(!ConstructRules.withinBudget(0, 3, 5) && !ConstructRules.withinBudget(-1, 3, 5) && !ConstructRules.withinBudget(10, 0, 5), "empty refused");
        check(!ConstructRules.withinBudget(513, 4, 4) && ConstructRules.withinBudget(512, 16, 4) && !ConstructRules.withinBudget(10, 17, 4), "LV4 limits");
        check(!ConstructRules.withinBudget(4097, 4, 5) && !ConstructRules.withinBudget(100, 33, 5), "LV5 limits");
        // Costs are finite, positive and monotone; hostile inputs give NaN (so a caller cannot spend a garbage amount).
        check(Double.isNaN(ConstructRules.startCost(Double.NaN, 5)) && Double.isNaN(ConstructRules.startCost(10, 0)) && Double.isNaN(ConstructRules.startCost(-1, 5)), "bad start cost");
        check(ConstructRules.startCost(10, 64) == 20 && ConstructRules.startCost(10, 128) > ConstructRules.startCost(10, 64), "start cost scales");
        check(Double.isNaN(ConstructRules.upkeep(10, 0, .5)) && Double.isNaN(ConstructRules.upkeep(10, Double.NaN, .5)), "bad upkeep");
        check(ConstructRules.upkeep(10, 8000, 1) > ConstructRules.upkeep(10, 2000, 1) && ConstructRules.upkeep(10, 2000, 1) > ConstructRules.upkeep(10, 2000, 0), "upkeep grows with mass and effort");
        check(ConstructRules.upkeep(10, 2000, 99) == ConstructRules.upkeep(10, 2000, 1), "effort is clamped");
        // Hover control: converges onto the target under gravity without exceeding the acceleration cap, from random starts.
        Random r = new Random(444);
        V3 g = new V3(0, -9.81, 0);
        for (int trial = 0; trial < 400; trial++) {
            V3 target = new V3(r.nextDouble() * 20 - 10, 70 + r.nextDouble() * 10, r.nextDouble() * 20 - 10);
            V3 pos = target.plus(new V3(r.nextGaussian() * 6, r.nextGaussian() * 6, r.nextGaussian() * 6)), vel = new V3(r.nextGaussian() * 5, r.nextGaussian() * 5, r.nextGaussian() * 5);
            double mass = 50 + r.nextDouble() * 20000, dt = 1 / 60.0, cap = 25 + r.nextDouble() * 15;
            for (int step = 0; step < 60 * 8; step++) {
                double[] j = ConstructRules.hoverImpulse(pos, vel, target, g, mass, dt, cap);
                V3 imp = new V3(j[0], j[1], j[2]);
                check(imp.length() / (mass * dt) <= cap + 1e-6, "acceleration capped");
                check(j[3] >= 0 && j[3] <= 1, "effort normalised");
                vel = vel.plus(imp.scale(1 / mass)).plus(g.scale(dt));
                pos = pos.plus(vel.scale(dt));
            }
            check(pos.minus(target).length() < .25 && vel.length() < .5, "settles on target: err " + pos.minus(target).length());
        }
        double[] none = ConstructRules.hoverImpulse(new V3(Double.NaN, 0, 0), V3.ZERO, V3.ZERO, g, 10, .05, 10);
        check(none[0] == 0 && none[1] == 0 && none[2] == 0, "NaN position gives no impulse");
        check(ConstructRules.hoverImpulse(V3.ZERO, V3.ZERO, V3.ZERO, g, -1, .05, 10)[1] == 0, "negative mass refused");
        check(ConstructRules.hoverImpulse(V3.ZERO, V3.ZERO, V3.ZERO, g, 10, 5, 10)[1] == 0, "absurd timestep refused");
        // Upright control: spinning body slows down; tilt is corrected toward zero; hostile inputs are inert.
        V3 w = new V3(3, 2, -4);
        double tilt = .6;
        for (int step = 0; step < 600; step++) {
            V3 axis = new V3(1, 0, 0);
            V3 j = ConstructRules.uprightImpulse(w, axis, tilt, 0, 100, 1 / 60.0);
            w = w.plus(j.scale(1 / 100.0));
            tilt += w.x() / 60.0;
        }
        check(Math.abs(tilt) < .02 && Math.abs(w.y()) < .05, "upright and spin damped: tilt " + tilt);
        check(ConstructRules.uprightImpulse(new V3(Double.NaN, 0, 0), null, 0, 0, 1, .05).length() == 0, "NaN spin inert");
        // Quarter turns and wrap.
        check(ConstructRules.quarterTurns(0) == 0 && ConstructRules.quarterTurns(Math.PI / 2) == 1 && ConstructRules.quarterTurns(Math.PI) == 2
                && ConstructRules.quarterTurns(-Math.PI / 2) == 3 && ConstructRules.quarterTurns(Math.PI * 2 + .1) == 0 && ConstructRules.quarterTurns(Double.NaN) == 0, "quarter turns");
        for (int i = 0; i < 2000; i++) { double a = (r.nextDouble() - .5) * 100; double q = ConstructRules.wrap(a); check(q > -Math.PI - 1e-12 && q <= Math.PI + 1e-12 && Math.abs(Math.sin(q) - Math.sin(a)) < 1e-9, "wrap keeps angle"); }
        // Landing tilt: a tilted structure cannot be written back to the grid.
        check(ConstructRules.landable(0) && ConstructRules.landable(Math.toRadians(19.9)) && !ConstructRules.landable(Math.toRadians(20.1)) && !ConstructRules.landable(Double.NaN), "tilt gate");
        // Throw: the impulse lands the body exactly on the requested velocity; it is bounded and inert on bad input.
        V3 v0 = new V3(1, -2, 3), look = new V3(0, .2, 1);
        V3 jt = ConstructRules.throwImpulse(v0, look, 30, 500);
        V3 v1 = v0.plus(jt.scale(1 / 500.0));
        check(v1.minus(look.unit().scale(30)).length() < 1e-9, "throw sets velocity");
        check(ConstructRules.throwImpulse(v0, V3.ZERO, 30, 500).length() == 0 && ConstructRules.throwImpulse(v0, look, Double.NaN, 500).length() == 0
                && ConstructRules.throwImpulse(v0, look, 30, 0).length() == 0, "bad throw inert");
        check(ConstructRules.throwImpulse(V3.ZERO, look, 1e9, 1).length() <= 40 + 1e-9, "throw speed capped");
        // Impact scale: zero below the minimum speed and for hostile values, bounded above.
        check(ConstructRules.impactScale(2000, 2.9, 30) == 0 && ConstructRules.impactScale(0, 30, 30) == 0 && ConstructRules.impactScale(2000, Double.NaN, 30) == 0, "no impact");
        check(ConstructRules.impactScale(1e12, 1e12, 30) <= 3 + 1e-9 && ConstructRules.impactScale(2000, 30, 30) == 1, "impact bounded");
        check(ConstructRules.impactScale(8000, 20, 30) > ConstructRules.impactScale(2000, 20, 30), "heavier hits harder");
        check(ConstructRules.holdDistance(0) == 5 && ConstructRules.holdDistance(1000) == 32 && Math.abs(ConstructRules.holdDistance(16) - 21.6) < 1e-9, "hold distance");
        for (int span = 0; span <= 64; span++) check(ConstructRules.holdDistance(span) >= span * .5 + 1 || ConstructRules.holdDistance(span) == 32, "the held body never overlaps the caster at span " + span);
        // The structure is gripped in the caster's hand: its nearest side touches the hand, the centre sits off to the hand's side.
        V3 e0 = new V3(0, 0, 0), l0 = new V3(0, 0, 1);
        V3 h0 = ConstructRules.hand(e0, 0, true), hl = ConstructRules.hand(e0, 0, false);
        check(h0.x() < 0 && hl.x() > 0 && Math.abs(h0.x() + hl.x()) < 1e-9 && h0.z() > 0 && h0.y() < 0 && h0.minus(e0).length() < 1, "facing south the right hand is west of, ahead of and below the eye; left mirrors");
        for (double yaw = -Math.PI; yaw <= Math.PI; yaw += .37) for (boolean rh : new boolean[] {true, false}) for (double radius : new double[] {0, 1.5, 6.4, 20}) for (double height : new double[] {0, 3, 24}) {
            V3 t = ConstructRules.holdTarget(e0, yaw, rh, -1.62, radius, height), h = ConstructRules.hand(e0, yaw, rh);
            double rr = Math.max(.5, radius) + ConstructRules.GRIP_GAP;
            check(Math.abs(Math.hypot(t.x() - h.x(), t.z() - h.z()) - rr) < 1e-9, "centre is one circumscribed radius from the hand horizontally");
            check(t.y() - height / 2 >= -1.62 + ConstructRules.HOLD_CLEARANCE - 1e-9, "the base never goes below the clearance above the feet");
            // The crosshair stays clear: the body's near edge is off the view axis, on the hand's side.
            V3 fwd = new V3(-Math.sin(yaw), 0, Math.cos(yaw)), rgt = new V3(-Math.cos(yaw), 0, -Math.sin(yaw));
            double lateral = (t.x() - e0.x()) * rgt.x() + (t.z() - e0.z()) * rgt.z(), ahead = (t.x() - e0.x()) * fwd.x() + (t.z() - e0.z()) * fwd.z();
            check((rh ? lateral : -lateral) > 0 && ahead > 0, "centre is ahead and to the hand's side");
            if (radius >= 1.5) check((rh ? lateral : -lateral) - radius > -radius * .6, "a body of this radius leaves the eye line clear or nearly so");
        }
        V3 tower = ConstructRules.holdTarget(e0, 0, true, -1.62, 6.4, 24);
        check(Math.abs(tower.y() - (-1.62 + .5 + 12)) < 1e-9, "a tall body stands just above the ground with the hand low on it");
        V3 hut = ConstructRules.holdTarget(new V3(0, 10, 0), 0, true, 0, 3.5, 4);
        check(Math.abs(hut.y() - (ConstructRules.hand(new V3(0, 10, 0), 0, true).y() - ConstructRules.GRIP_HEIGHT_FRACTION * 4 + 2)) < 1e-9, "a body in the air is gripped a third of the way up");
        check(ConstructRules.holdTarget(e0, Double.NaN, true, 0, 3, 3) == e0 && ConstructRules.holdTarget(null, 0, true, 0, 3, 3) == null && ConstructRules.holdTarget(e0, 0, true, Double.NaN, 3, 3) == e0
                && ConstructRules.holdTarget(e0, 0, true, 0, -1, 3) == e0, "hostile input does not invent a target");
        // Drawing the structure into the hand: ends exactly at the hand target, starts where it was seized, and never sweeps across the caster.
        V3 pl = new V3(0.5, -60, 21.3), seized = new V3(0.5, -47.5, 26.4), want = ConstructRules.holdTarget(new V3(0.5, -58.4, 21.3), 0, true, -60, 6.364, 24);
        check(ConstructRules.drawIn(seized, want, pl, 0, 0).minus(seized).length() < 1e-9 && ConstructRules.drawIn(seized, want, pl, 0, 1).minus(want).length() < 1e-9 && ConstructRules.drawIn(seized, want, pl, 0, 7).minus(want).length() < 1e-9, "draw-in endpoints");
        for (int k = 0; k <= 100; k++) {
            V3 c = ConstructRules.drawIn(seized, want, pl, 0, k / 100.0);
            // Box 9x9 centred on c: it must not overlap the caster's 0.6-wide footprint while its rear face is still behind the caster's front.
            boolean overlapX = Math.abs(c.x() - pl.x()) < 4.5 + .3, overlapZ = Math.abs(c.z() - pl.z()) < 4.5 + .3;
            check(!(overlapX && overlapZ) , "the drawn-in structure never overlaps the caster at step " + k);
        }
        check(ConstructRules.drawIn(null, want, pl, 0, .5) == want && ConstructRules.drawIn(seized, want, pl, Double.NaN, .5) == want, "hostile draw-in input falls back to the target");
        // Shatter and lock: the shell around a bounding box, and what counts as a slam.
        var shell = ConstructRules.cage(.2, 0, .2, .8, 1.95, .8);
        check(shell.size() == 3 * 4 * 3 - 1 * 2, "a 1x2x1 occupant has a 34-cell shell: " + shell.size());
        for (int[] c : shell) check(!(c[0] == 0 && c[2] == 0 && (c[1] == 0 || c[1] == 1)), "the shell never includes the cells the occupant stands in");
        check(ConstructRules.cage(.2, 0, .2, 1.2, 1, 1.2).size() == 4 * 3 * 4 - 2 * 1 * 2, "a box straddling a cell boundary occupies two cells per axis");
        check(ConstructRules.cage(0, 0, 0, 1, 1, 1).size() == 26 && ConstructRules.cage(-3.5, 64, -3.5, -3.1, 65.9, -3.1).size() == 34, "unit cube and negative coordinates");
        check(ConstructRules.cage(0, 0, 0, 7, 1, 1).isEmpty() && ConstructRules.cage(1, 0, 0, 0, 1, 1).isEmpty() && ConstructRules.cage(Double.NaN, 0, 0, 1, 1, 1).isEmpty(), "oversized, inverted or hostile boxes are not caged");
        check(ConstructRules.cage(0, 0, 0, 6, 6, 6).isEmpty(), "a 6-block occupant would need more than the cell cap");
        check(ConstructRules.slammed(5, 30) && !ConstructRules.slammed(25, 30) && !ConstructRules.slammed(5, 2) && !ConstructRules.slammed(Double.NaN, 30) && !ConstructRules.slammed(0, Double.NaN), "a slam is a sudden loss of speed from a real flight speed, not an arc's slow decay");
        // Dash: within reach nothing moves; from afar the caster stops a stand distance short of the footprint, on the approach line.
        V3 bmin = new V3(-4, -60, 22), bmax = new V3(4, -36, 30);
        check(ConstructRules.dashGap(0, 0, bmin, bmax) == 22 && ConstructRules.dashGap(0, 26, bmin, bmax) == 0 && ConstructRules.dashGap(10, 26, bmin, bmax) == 6, "footprint gap");
        check(ConstructRules.dashGap(Double.NaN, 0, bmin, bmax) == Double.POSITIVE_INFINITY && ConstructRules.dashGap(0, 0, null, bmax) == Double.POSITIVE_INFINITY, "hostile gap input is infinitely far");
        V3 stand = ConstructRules.dashStand(new V3(0, -60, 0), bmin, bmax);
        check(Math.abs(stand.z() - (22 - ConstructRules.DASH_STAND)) < 1e-9 && Math.abs(stand.x()) < 1e-9 && stand.y() == -60, "stand point is one stand distance short, same height");
        V3 diag = ConstructRules.dashStand(new V3(-14, -60, 12), bmin, bmax);
        check(Math.abs(ConstructRules.dashGap(diag.x(), diag.z(), bmin, bmax) - ConstructRules.DASH_STAND) < 1e-9 && diag.x() > -14 && diag.z() > 12, "diagonal approach stops on the line to the nearest corner");
        V3 near = new V3(0, -60, 21);
        check(ConstructRules.dashStand(near, bmin, bmax) == near, "already within the stand distance: stay put");
        check(ConstructRules.DASH_REACH < ConstructRules.RANGE && ConstructRules.DASH_ARRIVE >= ConstructRules.DASH_STAND && ConstructRules.DASH_ARRIVE < ConstructRules.DASH_REACH, "arrive/reach thresholds are ordered");
        check(ConstructRules.ease(0) == 0 && ConstructRules.ease(1) == 1 && ConstructRules.ease(.5) == .5 && ConstructRules.ease(Double.NaN) == 0 && ConstructRules.ease(9) == 1 && ConstructRules.ease(-3) == 0, "ease");
        V3 center = new V3(16.5, -57.8, 8), eye = new V3(16.5, -58.4, .5), view = new V3(0, 0, 1);
        V3 expected = eye.plus(view.scale(64)).minus(center).unit();
        for (V3 hit : new V3[] {null, new V3(30_000_000, -58, 30_000_000), new V3(20, -58.4, 20), new V3(16.5, -58.4, -1), new V3(Double.NaN, 0, 0)})
            check(ConstructRules.throwDirection(center, eye, view, hit, 64).minus(expected).length() < 1e-9, "plot/off-ray/behind/invalid hits cannot turn a forward throw diagonally");
        V3 wall = new V3(16.5, -58.4, 30);
        check(ConstructRules.throwDirection(center, eye, view, wall, 64).minus(wall.minus(center).unit()).length() < 1e-9, "a genuine world hit retains convergence onto the crosshair");
        check(ConstructRules.throwDirection(center, eye, V3.ZERO, wall, 64).equals(V3.ZERO), "bad view cannot launch");
        System.out.println("PASS " + checks + " vector construct manipulation rules: unlock, budgets, costs, hover convergence, upright, landing, throw, impact");
    }
}
