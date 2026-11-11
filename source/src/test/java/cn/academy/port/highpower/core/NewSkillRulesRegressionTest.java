package cn.academy.port.highpower.core;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;

/**
 * Counterexample-first witnesses for the seven skills added after 0.81.2 (143, 153, 243, 332, 352, 451, 453). Every
 * check states a way the skill could be abused or break (free acceleration, unbounded targets, duplicated energy,
 * continuous invulnerability, damage above the cap) and asserts that the pure rules refuse it.
 */
public final class NewSkillRulesRegressionTest {
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }
    private static boolean near(double a, double b) { return Math.abs(a - b) <= 1e-9 * Math.max(1, Math.max(Math.abs(a), Math.abs(b))); }
    private static void rejects(Runnable r, String message) { try { r.run(); } catch (IllegalArgumentException expected) { checks++; return; } throw new AssertionError(message); }

    public static void main(String[] args) {
        pulseShield();
        railRelay();
        reverseJet();
        incidentBypass();
        phaseCorridor();
        zenithDominion();
        impulseSplit();
        budgets();
        System.out.println("PASS " + checks + " new-skill rules: 143/153/243/332/352/451/453 counterexamples");
    }

    // ---- 143 pulse shield -------------------------------------------------------------------------------------------
    private static void pulseShield() {
        var tagged = new ArrayList<PulseShieldRules.Candidate>();
        var rnd = new Random(143);
        for (int i = 0; i < 40; i++) tagged.add(new PulseShieldRules.Candidate(new UUID(0, i), rnd.nextDouble() * 20 * 20, true));
        var picked = PulseShieldRules.select(tagged);
        check(picked.size() == PulseShieldRules.MAX_TARGETS, "never more than six circuit targets");
        for (var c : picked) check(c.distanceSquared() <= PulseShieldRules.RANGE * PulseShieldRules.RANGE, "out-of-range target selected");
        for (int i = 1; i < picked.size(); i++) check(picked.get(i - 1).distanceSquared() <= picked.get(i).distanceSquared(), "nearest first");
        check(PulseShieldRules.select(List.of(new PulseShieldRules.Candidate(new UUID(1, 1), 4, false))).isEmpty(), "an untagged object is never a target");
        var dup = new PulseShieldRules.Candidate(new UUID(2, 2), 9, true);
        check(PulseShieldRules.select(List.of(dup, dup, dup)).size() == 1, "one UUID counts once");
        check(PulseShieldRules.select(List.of()).isEmpty() && PulseShieldRules.select(null).isEmpty(), "no candidates, no targets");
        check(PulseShieldRules.select(List.of(new PulseShieldRules.Candidate(new UUID(3, 3), Double.NaN, true))).isEmpty(), "NaN distance refused");
        check(PulseShieldRules.interferenceTicks(false) == PulseShieldRules.INTERFERENCE_TICKS && PulseShieldRules.interferenceTicks(true) == 0, "long press only detects, never interferes");
        check(PulseShieldRules.damage(new BigDecimal("1E19")).compareTo(new BigDecimal("2E18")) == 0, "0.2 D");
        check(PulseShieldRules.damage(CombatAmounts.CAP).compareTo(CombatAmounts.CAP) <= 0, "damage never exceeds the 1e20 cap");
        rejects(() -> PulseShieldRules.damage(CombatAmounts.CAP.multiply(BigDecimal.TEN)), "a base D above the cap is refused, as in every other skill");
        rejects(() -> PulseShieldRules.damage(BigDecimal.ZERO), "zero D refused");
    }

    // ---- 153 rail relay ---------------------------------------------------------------------------------------------
    private static void railRelay() {
        var anchors = new ArrayList<V3>();
        V3 last = new V3(0, 64, 0);
        for (int i = 0; i < 6; i++) {
            var next = RailRelayRules.addAnchor(anchors, last.plus(new V3(10, 0, 0)));
            check(next.isPresent(), "a 10 m hop is valid");
            anchors = new ArrayList<>(next.get());
            last = anchors.get(anchors.size() - 1);
            check(anchors.size() <= RailRelayRules.MAX_ANCHORS, "never more than three anchors");
        }
        check(anchors.size() == 3 && anchors.get(0).x() == 40, "the oldest anchor is evicted first");
        check(RailRelayRules.addAnchor(anchors, last.plus(new V3(RailRelayRules.MAX_SEGMENT + 1, 0, 0))).isEmpty(), "a segment above the configured length is refused");
        check(RailRelayRules.addAnchor(anchors, last.plus(new V3(RailRelayRules.MAX_SEGMENT, 0, 0))).isPresent(), "exactly the configured length is allowed");
        check(RailRelayRules.addAnchor(anchors, new V3(Double.NaN, 0, 0)).isEmpty(), "non-finite anchor refused");
        check(RailRelayRules.addAnchor(anchors, null).isEmpty(), "null anchor refused");
        var route = List.of(new V3(0, 64, 0), new V3(20, 64, 0), new V3(20, 64, 20));
        var rnd = new Random(153);
        for (int i = 0; i < 20000; i++) {
            V3 pos = new V3(rnd.nextDouble() * 22 - 1, 64 + rnd.nextGaussian() * .5, rnd.nextDouble() * 22 - 1);
            V3 raw = new V3(rnd.nextGaussian(), rnd.nextGaussian() * .3, rnd.nextGaussian());
            V3 vel = raw.length() < 1e-9 ? V3.ZERO : raw.scale(rnd.nextDouble() * RailRelayRules.MAX_SPEED / raw.length());
            var out = RailRelayRules.step(pos, route, vel, rnd.nextBoolean());
            if (out.isEmpty()) continue;
            check(out.get().length() <= RailRelayRules.MAX_SPEED + 1e-9, "speed cap exceeded");
            check(out.get().minus(vel).length() <= RailRelayRules.MAX_ACCELERATION + 1e-9, "per-tick acceleration exceeded");
        }
        check(RailRelayRules.step(new V3(0, 200, 0), route, V3.ZERO, true).isEmpty(), "off the route the ride ends");
        check(RailRelayRules.step(new V3(5, 64, 0), List.of(new V3(0, 64, 0)), V3.ZERO, true).isEmpty(), "a single anchor is not a route");
        // Riding from rest reaches but never exceeds the cap.
        V3 v = V3.ZERO, p = new V3(0, 64, 0);
        for (int i = 0; i < 200; i++) {
            var next = RailRelayRules.step(p, route, v, true);
            if (next.isEmpty()) break;
            v = next.get(); p = p.plus(v);
        }
        check(v.length() <= RailRelayRules.MAX_SPEED + 1e-9, "terminal speed is capped");
    }

    // ---- 243 reverse jet --------------------------------------------------------------------------------------------
    private static void reverseJet() {
        var rnd = new Random(243);
        for (int i = 0; i < 50000; i++) {
            V3 v = new V3(rnd.nextGaussian() * 2, rnd.nextGaussian() * 2, rnd.nextGaussian() * 2);
            double strength = rnd.nextDouble() * 3 - 1;
            V3 w = ReverseJetRules.brake(v, strength);
            check(w.length() <= v.length() + 1e-12, "braking never speeds the body up");
            check(w.dot(v) >= -1e-12, "braking never reverses the motion (no free reverse acceleration)");
        }
        check(ReverseJetRules.brake(V3.ZERO, 1).length() == 0, "a body at rest cannot be accelerated by the jet");
        check(ReverseJetRules.brake(new V3(1, 0, 0), 1).length() == 0 && ReverseJetRules.brake(new V3(1, 0, 0), 0).length() == 1, "strength endpoints");
        check(ReverseJetRules.brake(new V3(Double.NaN, 0, 0), .5).length() == 0, "non-finite velocity yields rest, never NaN");
        // Falling: the downward speed shrinks and so does the fall distance, never the other way.
        check(near(ReverseJetRules.fallDistance(10f, -1, -.5), 5), "fall distance scales with the remaining downward speed");
        check(ReverseJetRules.fallDistance(10f, -1, -2) == 10f, "an increase in downward speed never inflates or deflates the distance");
        check(ReverseJetRules.fallDistance(10f, 0.3, 0.2) == 10f, "upward motion leaves fall distance alone");
        check(ReverseJetRules.fallDistance(Float.NaN, -1, -.5) == 0f, "non-finite fall distance resets");
        check(ReverseJetRules.hoverVelocity(new V3(.2, -.9, .1), 3).y() >= -.9 && ReverseJetRules.hoverVelocity(new V3(.2, -.9, .1), 3).y() <= 0, "hover only lifts toward zero vertical speed");
        check(ReverseJetRules.hoverVelocity(new V3(.2, .9, .1), 3).y() <= .9, "hover never adds upward speed");
        // Cost grows with duration and the whole use is bounded at 0.8 s braking plus a short hover.
        check(ReverseJetRules.cpCost(5, 1) < ReverseJetRules.cpCost(5, 16), "cost scales with duration");
        check(ReverseJetRules.BRAKE_TICKS == cn.academy.port.core.SkillTuning.number("reverse_jet_brake", "duration_ticks") && ReverseJetRules.HOVER_TICKS == cn.academy.port.core.SkillTuning.number("reverse_jet_brake", "hover_ticks"), "configured brake and hover durations");
        rejects(() -> ReverseJetRules.cpCost(5, -1), "negative duration refused");
    }

    // ---- 332 incident bypass ----------------------------------------------------------------------------------------
    private static void incidentBypass() {
        for (double requested : new double[] {-1, 0, .1, .4, .41, 1, 10, Double.NaN})
            check(IncidentBypassRules.divertedShare(requested) >= 0 && IncidentBypassRules.divertedShare(requested) <= .4 + 1e-12, "diverted share above 40%: " + requested);
        check(near(IncidentBypassRules.divertedShare(1) + IncidentBypassRules.remainingShare(1), 1), "diverted and remaining shares add up to the whole hit");
        check(IncidentBypassRules.ARM_TICKS == 8, "0.4 s arming hold");
        var state = new IncidentBypassRules.Window();
        check(!state.consume(), "an unarmed window diverts nothing");
        state.arm();
        check(state.consume() && !state.consume(), "only the next projectile is diverted");
        check(IncidentBypassRules.handles(IncidentBypassRules.Kind.ARROW) && IncidentBypassRules.handles(IncidentBypassRules.Kind.THROWN), "recognizable projectiles are handled");
        for (var kind : new IncidentBypassRules.Kind[] {IncidentBypassRules.Kind.POISON, IncidentBypassRules.Kind.FIRE, IncidentBypassRules.Kind.AREA_FIELD, IncidentBypassRules.Kind.ATTACHED_STATE})
            check(!IncidentBypassRules.handles(kind), "poison, burning, area fields and attached states are never diverted: " + kind);
        var rnd = new Random(332);
        for (int i = 0; i < 20000; i++) {
            V3 v = new V3(rnd.nextGaussian() * 3, rnd.nextGaussian(), rnd.nextGaussian() * 3);
            V3 toPlayer = v.length() < 1e-6 ? new V3(0, 0, 1) : v.scale(1 / v.length());
            var out = IncidentBypassRules.bypass(v, toPlayer, rnd.nextBoolean());
            if (out.isEmpty()) continue;
            check(out.get().length() <= v.length() + 1e-9, "a bypassed projectile gains no speed");
            check(Math.abs(out.get().dot(toPlayer)) <= v.length() * .5 + 1e-9, "the bypass path no longer points at the player");
        }
        check(IncidentBypassRules.bypass(V3.ZERO, new V3(0, 0, 1), true).isEmpty(), "a stationary object has no path to move");
        check(IncidentBypassRules.bypass(new V3(1, 0, 0), V3.ZERO, true).isEmpty(), "no incoming axis, no bypass");
    }

    // ---- 352 phase corridor -----------------------------------------------------------------------------------------
    private static void phaseCorridor() {
        check(PhaseCorridorRules.MAX_ANCHORS == 6 && PhaseCorridorRules.JUMP_RANGE == 32, "six anchors, 32 m jump");
        check(PhaseCorridorRules.evasion(0) && PhaseCorridorRules.evasion(1) && !PhaseCorridorRules.evasion(2), "0.1 s of trajectory evasion");
        check(PhaseCorridorRules.exposed(2) && PhaseCorridorRules.exposed(4) && !PhaseCorridorRules.exposed(5) && !PhaseCorridorRules.exposed(1), "0.15 s of exposure that can be hit");
        check(!PhaseCorridorRules.evasion(-1) && !PhaseCorridorRules.exposed(-1), "before the jump nothing is granted");
        check(!PhaseCorridorRules.canJump(0) && !PhaseCorridorRules.canJump(4) && PhaseCorridorRules.canJump(PhaseCorridorRules.MIN_INTERVAL), "a new jump waits for the exposure window to end");
        // Chaining at the fastest legal rate never gives continuous cover.
        int jumps = 0, covered = 0, since = Integer.MAX_VALUE;
        for (int t = 0; t < 2000; t++) {
            if (PhaseCorridorRules.canJump(since)) { since = 0; jumps++; } else since++;
            if (PhaseCorridorRules.evasion(since)) covered++;
        }
        check(jumps > 100, "chained jumps are possible");
        check(covered / 2000.0 <= .4 + 1e-9, "evasion covers at most 40% of any chained run, never continuous invulnerability");
        int longest = 0, run = 0; since = Integer.MAX_VALUE;
        for (int t = 0; t < 2000; t++) {
            if (PhaseCorridorRules.canJump(since)) since = 0; else since++;
            if (PhaseCorridorRules.evasion(since)) { run++; longest = Math.max(longest, run); } else run = 0;
        }
        check(longest <= PhaseCorridorRules.EVASION_TICKS, "no evasion run is longer than 0.1 s");
        check(PhaseCorridorRules.validJump(32) && !PhaseCorridorRules.validJump(32.01) && !PhaseCorridorRules.validJump(-1) && !PhaseCorridorRules.validJump(Double.NaN), "jump length bounds");
        check(!PhaseCorridorRules.validJump(0) && !PhaseCorridorRules.validJump(1.99) && PhaseCorridorRules.validJump(2), "a jump that does not move the caster is not a jump (found by the real-world test: staring at the sky paid for a zero-length hop)");
        check(PhaseCorridorRules.validAnchorCount(6) && !PhaseCorridorRules.validAnchorCount(7) && !PhaseCorridorRules.validAnchorCount(-1), "anchor count bounds");
    }

    // ---- 451 zenith dominion ----------------------------------------------------------------------------------------
    private static void zenithDominion() {
        BigDecimal d = new BigDecimal("5E18");
        BigDecimal total = ZenithDominionRules.totalDamage(d);
        check(total.compareTo(new BigDecimal("2E19")) == 0, "total is 4D");
        check(ZenithDominionRules.totalDamage(CombatAmounts.CAP).compareTo(CombatAmounts.CAP) == 0, "total never exceeds 1e20");
        for (int n : new int[]{1,2,3,8,16,17,64,65,130,256}) {
            var shares = ZenithDominionRules.shares(total, n);
            check(shares.size() == n, "one share per target");
            BigDecimal sum = BigDecimal.ZERO;
            for (var s : shares) { check(s.signum() >= 0, "no negative share"); sum = sum.add(s); }
            check(sum.compareTo(total) == 0, "the shares add up to exactly the total, nothing duplicated or lost: " + n);
        }
        rejects(() -> ZenithDominionRules.shares(total, -1), "negative target count refused");
        check(ZenithDominionRules.shares(total, 0).isEmpty(), "no targets, no shares");
        rejects(() -> ZenithDominionRules.shares(total.negate(), 3), "negative total refused");
        var ids = new ArrayList<UUID>();
        for (int i = 0; i < 40; i++) ids.add(new UUID(0, i % 20));
        check(ZenithDominionRules.freeze(ids).size() == 20, "all twenty distinct marks retained");
        check(new java.util.HashSet<>(ZenithDominionRules.freeze(ids)).size() == 20, "duplicate marks collapse to one");
        check(ZenithDominionRules.freeze(List.of()).isEmpty() && ZenithDominionRules.freeze(null).isEmpty(), "no marks, nothing frozen");
        check(ZenithDominionRules.RANGE == 96 && ZenithDominionRules.LIFT_TICKS > 0 && ZenithDominionRules.RELEASE_TICKS > 0, "96 m, lift then release");
        check(ZenithDominionRules.liftVelocity(0) > 0 && ZenithDominionRules.liftVelocity(ZenithDominionRules.LIFT_TICKS) == 0, "a lift that eases to rest");
        for (int t = 0; t <= ZenithDominionRules.LIFT_TICKS; t++) check(ZenithDominionRules.liftVelocity(t) <= ZenithDominionRules.MAX_LIFT_SPEED + 1e-12, "lift speed bound");
    }

    // ---- 453 impulse split ------------------------------------------------------------------------------------------
    private static void impulseSplit() {
        var counterexample=ImpulseSplitRules.damageBudget(1,2.1,false);
        check(counterexample.total()==3&&counterexample.first()==2&&counterexample.second()==1,"2.1 native ceil budget is three, never independent ceil(1.05)+ceil(1.05)=four");
        for(boolean weapon:new boolean[]{false,true})for(double speed:new double[]{0,Math.nextDown(1),1,Math.nextUp(1),.7,8})
            for(double base:new double[]{0,Math.nextDown(2.0),2,Math.nextUp(2.0),2.1,64}){
                var budget=ImpulseSplitRules.damageBudget(speed,base,weapon);
                int nativeAmount=MultiAxisReflectionRules.nativeArrowDamage(speed,base,weapon).intValueExact();
                check(budget.total()==nativeAmount&&(long)budget.first()+budget.second()==nativeAmount,"original native float/cast/ceil operation order and integer sum are preserved");
                check((double)ImpulseSplitRules.capNativeDamage(budget.first(),Float.MAX_VALUE)+ImpulseSplitRules.capNativeDamage(budget.second(),Float.MAX_VALUE)<=nativeAmount,"future gravitational acceleration cannot exceed the birth budget");
            }
        for(int cap:new int[]{0,1,2,3,511,512,16_777_217,Integer.MAX_VALUE}){
            float actual=ImpulseSplitRules.capNativeDamage(cap,Float.MAX_VALUE);
            check(Float.isFinite(actual)&&actual>=0&&(double)actual<=cap,"float representation never rounds an integer share upward");
        }
        var rnd = new Random(453);
        for (int i = 0; i < 40000; i++) {
            V3 m = new V3(rnd.nextGaussian() * 4, rnd.nextGaussian(), rnd.nextGaussian() * 4);
            V3 a = new V3(rnd.nextGaussian(), 0, rnd.nextGaussian()), b = new V3(rnd.nextGaussian(), 0, rnd.nextGaussian());
            var out = ImpulseSplitRules.split(m, a, b);
            if (out.isEmpty()) continue;
            var pair = out.get();
            double before = m.dot(m), after = pair.first().dot(pair.first()) + pair.second().dot(pair.second());
            check(after <= before, "the two actual shares never carry more energy than the one impact, with conservative floating rounding");
            check(pair.first().plus(pair.second()).length()<=m.length(),"random chosen sides never amplify the net momentum magnitude");
            check(pair.first().length() <= m.length() + 1e-9 && pair.second().length() <= m.length() + 1e-9, "each share is no larger than the original");
            check(pair.first().dot(a) >= -1e-9 && pair.second().dot(b) >= -1e-9, "each share points toward its chosen side");
        }
        double twenty=Math.toRadians(20.0001*.5);
        var closeSides=ImpulseSplitRules.split(new V3(0,0,1),new V3(Math.sin(twenty),0,Math.cos(twenty)),new V3(-Math.sin(twenty),0,Math.cos(twenty))).orElseThrow();
        check(closeSides.first().plus(closeSides.second()).length()<=1,"old near-20-degree 1.393x net impulse counterexample is fixed");
        V3 front=new V3(0,0,1);
        for(double angle:new double[]{20,Math.nextUp(20),20.0001,45,89.999999,90,150,159.8,179,179.999999}){
            double half=Math.toRadians(angle*.5);V3 a=new V3(Math.sin(half),0,Math.cos(half)),b=new V3(-Math.sin(half),0,Math.cos(half));
            var narrowed=ImpulseSplitRules.split(front,a,b);
            if(narrowed.isPresent()){
                var v=narrowed.get();check(v.first().plus(v.second()).length()<=1,"20 degree and almost-opposite accepted sides cannot amplify net impulse");
                check(v.first().dot(v.first())+v.second().dot(v.second())<=1,"angular boundaries cannot create kinetic energy");
                if(angle<90)check(v.first().length()<Math.sqrt(.5),"narrow sides are slower than the old amplifying equal-energy result");
            }
        }
        V3 m = new V3(0, 0, 5);
        check(ImpulseSplitRules.split(m, new V3(1, 0, 0), new V3(2, 0, 0)).isEmpty(), "two parallel directions are not two directions");
        check(ImpulseSplitRules.split(m, new V3(1, 0, 0), new V3(1, 0, .001)).isEmpty(), "nearly parallel directions refused");
        check(ImpulseSplitRules.split(m, V3.ZERO, new V3(1, 0, 0)).isEmpty(), "a zero direction is refused");
        check(ImpulseSplitRules.split(V3.ZERO, new V3(1, 0, 0), new V3(-1, 0, 0)).isEmpty(), "no impact, nothing to split");
        check(ImpulseSplitRules.split(new V3(Double.NaN, 0, 5), new V3(1, 0, 0), new V3(-1, 0, 0)).isEmpty(), "non-finite impact refused");
        var opposite = ImpulseSplitRules.split(m, new V3(1, 0, 0), new V3(-1, 0, 0));
        check(opposite.isPresent() && near(opposite.get().first().length(), opposite.get().second().length()), "opposite sides split evenly");
        check(near(opposite.get().first().dot(opposite.get().first()) + opposite.get().second().dot(opposite.get().second()), 25), "an even split conserves energy exactly");
        // The directions must be empty of obstacles: that probe belongs to the caller, the rule only needs two usable axes.
        check(ImpulseSplitRules.validDirections(new V3(0, 0, 1), new V3(1, 0, 0), new V3(-1, 0, 0)), "left and right of the front are valid");
        check(!ImpulseSplitRules.validDirections(new V3(0, 0, 1), new V3(0, 0, -1), new V3(1, 0, 0)), "a direction pointing back at the caster is not a bypass side");
    }

    // ---- shared budgets ---------------------------------------------------------------------------------------------
    private static void budgets() {
        for (var curve : new NewSkillBudget.Skill[] {NewSkillBudget.Skill.PULSE_SHIELD, NewSkillBudget.Skill.RAIL_RELAY, NewSkillBudget.Skill.REVERSE_JET,
                NewSkillBudget.Skill.INCIDENT_BYPASS, NewSkillBudget.Skill.PHASE_CORRIDOR, NewSkillBudget.Skill.ZENITH_DOMINION, NewSkillBudget.Skill.IMPULSE_SPLIT}) {
            double lastCp = Double.POSITIVE_INFINITY; int lastCd = Integer.MAX_VALUE;
            for (int level = 1; level <= 5; level++) {
                double cp = NewSkillBudget.cp(curve, new BigDecimal(level)); int cd = NewSkillBudget.cooldownTicks(curve, new BigDecimal(level));
                check(cp > 0 && Double.isFinite(cp) && cd >= 1, "a budget is positive and finite");
                check(cp <= lastCp + 1e-12 && cd <= lastCd, "a higher level never costs more or waits longer");
                lastCp = cp; lastCd = cd;
            }
        }
    }
}
