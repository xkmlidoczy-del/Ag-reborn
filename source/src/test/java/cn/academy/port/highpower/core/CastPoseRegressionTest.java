package cn.academy.port.highpower.core;

import cn.academy.port.SkillCatalog;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Random;
import java.util.Set;

/** Witnesses for the cosmetic casting gestures: full skill coverage, bounded finite output, mirroring, timeline shape, identity. */
public final class CastPoseRegressionTest {
    private static int checks;
    private static void check(boolean pass, String message) { checks++; if (!pass) throw new AssertionError(message); }

    public static void main(String[] args) {
        // Coverage: every registered skill is either a gesture or an explicitly non-cast training course, and nothing stale is mapped.
        Set<String> ids = new HashSet<>();
        for (var skill : SkillCatalog.REGISTERED) ids.add(skill.id());
        for (String id : ids) {
            var canon=cn.academy.port.canon.CanonSkills.find(id);
            if(canon.isPresent()){
                var definition=canon.orElseThrow();var visual=cn.academy.port.canon.CanonVfxDesign.find(id);
                check(visual!=null&&visual.category().equals(definition.category()),"canonical network visual identity: "+id);
                for(boolean right:new boolean[]{true,false}){
                    var pose=cn.academy.port.client.canon.CanonPoseMath.sample(definition.category(),id,6,18,right);
                    check(Double.isFinite(pose.rightX()+pose.leftX()+pose.handX()+pose.lean()),"finite canonical gesture: "+id);
                    check(Math.abs(pose.rightX())+Math.abs(pose.leftX())>.1,"real canonical gesture profile: "+id);
                }
            }else check(CastPose.of(id) != null || CastPose.NOT_CASTS.contains(id), "skill without a gesture: " + id);
        }
        for (String id : CastPose.mapped()) check(ids.contains(id), "gesture for an unregistered skill: " + id);
        for (String id : CastPose.NOT_CASTS) check(CastPose.of(id) == null, "course must not gesture: " + id);
        check(CastPose.of(null) == null && CastPose.of("nonsense") == null, "unknown skills have no gesture");
        check(ids.size() >= 100, "catalog size: " + ids.size());
        Set<CastPose.Archetype> used = EnumSet.noneOf(CastPose.Archetype.class);
        for (String id : CastPose.mapped()) used.add(CastPose.of(id));
        check(!used.contains(CastPose.Archetype.LIFT), "no skill uses the retired overhead ritual pose");
        used.addAll(EnumSet.of(CastPose.Archetype.LIFT, CastPose.Archetype.CONSTRUCT_PULL, CastPose.Archetype.CONSTRUCT_STEER,
                CastPose.Archetype.CONSTRUCT_THROW, CastPose.Archetype.CONSTRUCT_LAND));
        check(used.equals(EnumSet.allOf(CastPose.Archetype.class)), "every active archetype has a skill or authoritative construct phase");
        String[] legacy={"STRIKE","AIM","GUARD","SLAM","SWEEP","LIFT","GATHER","FLICK","PLACE"};
        for(int i=0;i<legacy.length;i++) check(CastPose.Archetype.values()[i].name().equals(legacy[i]),"legacy ordinal remains stable: "+i);
        // Envelope: bounded, zero before the press, rises monotonically while held, falls monotonically after release.
        for (var a : CastPose.Archetype.values()) {
            check(CastPose.envelope(a, -1, -1) == 0 && CastPose.envelope(a, 0, -1) == 0 && CastPose.envelope(null, 5, -1) == 0, a + " silent at the start");
            double prev = 0;
            for (double age = 0; age <= 20; age += .25) { double v = CastPose.envelope(a, age, -1); check(v >= prev - 1e-12 && v >= 0 && v <= 1, a + " rises"); prev = v; }
            check(CastPose.envelope(a, 30, -1) == 1, a + " reaches full strength");
            prev = 1;
            for (double since = 0; since <= CastPose.RELAX_TICKS + 2; since += .25) { double v = CastPose.envelope(a, 30, since); check(v <= prev + 1e-12 && v >= 0, a + " relaxes"); prev = v; }
            check(CastPose.envelope(a, 30, CastPose.RELAX_TICKS) == 0, a + " is gone after the relax window");
            check(CastPose.minTicks(a) >= 6 && CastPose.minTicks(a) <= 20, a + " minimum duration is readable but short");
        }
        // Bounds and finiteness over the whole timeline, both handedness, plus hostile inputs.
        Random r = new Random(5);
        for (var a : CastPose.Archetype.values()) for (boolean right : new boolean[] {true, false}) {
            for (int i = 0; i < 4000; i++) {
                double age = r.nextDouble() * 400, since = r.nextInt(3) == 0 ? -1 : r.nextDouble() * 12;
                var h = CastPose.hand(a, age, since, right);
                check(Double.isFinite(h.x() + h.y() + h.z() + h.rotX() + h.rotY() + h.rotZ()), a + " hand finite");
                check(Math.abs(h.x()) <= .6 && Math.abs(h.y()) <= .6 && Math.abs(h.z()) <= .6, a + " hand offsets stay on screen: " + h);
                check(Math.abs(h.rotX()) <= 80 && Math.abs(h.rotY()) <= 80 && Math.abs(h.rotZ()) <= 80, a + " hand rotation bounded: " + h);
                var m = CastPose.arms(a, age, since, right);
                double[] v = {m.rx(), m.ry(), m.rz(), m.lx(), m.ly(), m.lz(), m.lean()};
                for (double x : v) check(Double.isFinite(x) && Math.abs(x) <= 3.2, a + " arm finite and bounded: " + x);
                check(m.lean() >= -.2 && m.lean() <= .4, a + " lean bounded");
            }
            for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -5, 1e300}) {
                var h = CastPose.hand(a, bad, -1, right); var m = CastPose.arms(a, 20, bad, right);
                check(Double.isFinite(h.x() + h.rotX()) && Double.isFinite(m.rx() + m.lx() + m.lean()), a + " hostile time stays finite");
            }
            check(CastPose.hand(a, 0, -1, right).equals(CastPose.Hand.NONE) && CastPose.arms(a, 0, -1, right).equals(CastPose.Arms.NONE), a + " neutral at the start");
        }
        // Mirroring: a left-handed gesture is the right-handed one with the arms swapped and the sideways angles negated.
        for (var a : CastPose.Archetype.values()) for (double age : new double[] {3, 8, 14, 30}) for (double since : new double[] {-1, 0, 3}) {
            var rh = CastPose.arms(a, age, since, true); var lh = CastPose.arms(a, age, since, false);
            check(Math.abs(rh.rx() - lh.lx()) < 1e-9 && Math.abs(rh.lx() - lh.rx()) < 1e-9 && Math.abs(rh.ry() + lh.ly()) < 1e-9 && Math.abs(rh.rz() + lh.lz()) < 1e-9
                    && Math.abs(rh.ly() + lh.ry()) < 1e-9 && Math.abs(rh.lz() + lh.rz()) < 1e-9 && Math.abs(rh.lean() - lh.lean()) < 1e-9, a + " arms mirror at " + age);
            var hr = CastPose.hand(a, age, since, true); var hl = CastPose.hand(a, age, since, false);
            check(Math.abs(hr.x() + hl.x()) < 1e-9 && Math.abs(hr.y() - hl.y()) < 1e-9 && Math.abs(hr.z() - hl.z()) < 1e-9 && Math.abs(hr.rotY() + hl.rotY()) < 1e-9, a + " first-person hand mirrors at " + age);
        }
        // Identity: each archetype differs visibly from every other mid-gesture (so skills do not all look the same).
        for (var a : CastPose.Archetype.values()) for (var b : CastPose.Archetype.values()) {
            if (a == b) continue;
            var x = CastPose.arms(a, 14, -1, true); var y = CastPose.arms(b, 14, -1, true);
            double d = Math.abs(x.rx() - y.rx()) + Math.abs(x.ry() - y.ry()) + Math.abs(x.rz() - y.rz()) + Math.abs(x.lx() - y.lx()) + Math.abs(x.ly() - y.ly()) + Math.abs(x.lz() - y.lz()) + Math.abs(x.lean() - y.lean());
            check(d > .25, a + " and " + b + " must look different: " + d);
        }
        // Dynamics: a wind-up before the pose, a bounded overshoot, a settle to exactly the pose, and held poses that keep breathing.
        for (var a : CastPose.Archetype.values()) {
            double min = 0, max = 0;
            for (double age = 0; age <= 40; age += .125) { double d = CastPose.dyn(a, age); check(Double.isFinite(d), a + " dyn finite"); min = Math.min(min, d); max = Math.max(max, d); }
            check(min >= -.3 && max <= 1.16, a + " wind-up and overshoot are bounded: " + min + " .. " + max);
            check(Math.abs(CastPose.dyn(a, 400) - 1) < 1e-6 && CastPose.dyn(a, 0) == 0, a + " starts at zero and settles on the pose");
            check(Math.abs(CastPose.dyn(a, .25 * 4) ) < 2 && CastPose.dyn(a, 1e-9) <= 0, a + " never jumps forward at the very first instant");
        }
        for (var a : new CastPose.Archetype[] {CastPose.Archetype.STRIKE, CastPose.Archetype.FLICK, CastPose.Archetype.SLAM, CastPose.Archetype.SWEEP, CastPose.Archetype.CONSTRUCT_THROW}) {
            double min = 0, max = 0;
            for (double age = .1; age <= 12; age += .1) { double d = CastPose.dyn(a, age); min = Math.min(min, d); max = Math.max(max, d); }
            check(min < -.1, a + " visibly winds up the other way first: " + min);
            check(max > 1.02, a + " overshoots a little before settling: " + max);
        }
        for (var a : CastPose.Archetype.values()) {
            double lo = 9, hi = -9;
            for (double age = 30; age <= 60; age += .5) { var m = CastPose.arms(a, age, -1, true); lo = Math.min(lo, m.rx()); hi = Math.max(hi, m.rx()); }
            check(hi - lo > .02, a + " held pose is not frozen: " + (hi - lo));
            check(hi - lo < .15, a + " held pose only breathes, it does not wander: " + (hi - lo));
        }
        // Body layer: bounded, finite, mirrored, silent at the start, and distinct between at least most gestures.
        for (var a : CastPose.Archetype.values()) {
            check(CastPose.body(a, 0, -1, true).equals(CastPose.Body.NONE) && CastPose.body(null, 5, -1, true).equals(CastPose.Body.NONE), a + " body is neutral at the start");
            for (int i = 0; i < 3000; i++) {
                double age = r.nextDouble() * 200, since = r.nextInt(3) == 0 ? -1 : r.nextDouble() * 12;
                var b = CastPose.body(a, age, since, true); var l = CastPose.body(a, age, since, false);
                check(Double.isFinite(b.twist() + b.spread() + b.lunge()), a + " body finite");
                check(Math.abs(b.twist()) <= .32 && b.spread() >= 0 && b.spread() <= .25 && Math.abs(b.lunge()) <= .4, a + " body bounded: " + b);
                check(Math.abs(b.twist() + l.twist()) < 1e-9 && Math.abs(b.spread() - l.spread()) < 1e-9 && Math.abs(b.lunge() - l.lunge()) < 1e-9, a + " body mirrors");
            }
            for (double bad : new double[] {Double.NaN, Double.POSITIVE_INFINITY, -3, 1e300}) check(Double.isFinite(CastPose.body(a, bad, -1, true).twist() + CastPose.body(a, 20, bad, true).lunge()), a + " hostile body time");
        }
        check(CastPose.body(CastPose.Archetype.SLAM, 20, -1, true).spread() > CastPose.body(CastPose.Archetype.PLACE, 20, -1, true).spread() + .05, "a slam plants a wider stance than placing");
        check(CastPose.body(CastPose.Archetype.BRAKE, 20, -1, true).lunge() < 0, "braking leans the weight back");
        check(CastPose.body(CastPose.Archetype.STRIKE, 20, -1, true).twist() < 0 && CastPose.body(CastPose.Archetype.STRIKE, 20, 3, true).twist() > .1, "a strike coils back, then drives the shoulder through");
        // Crossfade: mixing is continuous at both ends and keeps every blended value between the two poses.
        for (var a : CastPose.Archetype.values()) for (var b : CastPose.Archetype.values()) {
            var x = CastPose.sample(a, 14, -1, true); var y = CastPose.sample(b, 14, -1, true);
            check(CastPose.mix(x, y, 0).arms().rx() == x.arms().rx() && CastPose.mix(x, y, 1).arms().rx() == y.arms().rx() && CastPose.mix(x, y, 1).body().twist() == y.body().twist(), "mix endpoints");
            double mid = CastPose.mix(x, y, .5).arms().rx();
            check(mid >= Math.min(x.arms().rx(), y.arms().rx()) - 1e-9 && mid <= Math.max(x.arms().rx(), y.arms().rx()) + 1e-9, "mix stays between the poses");
        }
        // Refusal feedback: only the server's "did not start" keys for the caster's own skill cancel a gesture.
        check(CastPose.refusal("message.academy.highpower.pulse_shield.not_ready", "pulse_shield") && CastPose.refusal("message.academy.highpower.vector_construct.terrain", "vector_construct")
                && CastPose.refusal("message.academy.highpower.impulse_split.unsupported", "impulse_split"), "refusals are recognised");
        check(!CastPose.refusal("message.academy.highpower.pulse_shield.not_ready", "impulse_split") && !CastPose.refusal("message.academy.highpower.impulse_split.spent_noop", "impulse_split")
                && !CastPose.refusal("message.academy.highpower.vector_construct.preview", "vector_construct") && !CastPose.refusal("message.academy.highpower.vector_construct.land_blocked", "vector_construct")
                && !CastPose.refusal("chat.type.text", "x") && !CastPose.refusal(null, "x") && !CastPose.refusal("message.academy.highpower.x.not_ready", null)
                && !CastPose.refusal("message.academy.highpower.x.not_ready", "") && !CastPose.refusal("message.academy.highpower.abc.not_ready", "ab"), "other skills, paid-but-ineffective and informational keys are not refusals");
        // The strike winds up, then thrusts: the hand and arm move forward after release.
        var wind = CastPose.arms(CastPose.Archetype.STRIKE, 20, -1, true); var hit = CastPose.arms(CastPose.Archetype.STRIKE, 20, 3, true);
        check(wind.rx() > 0 && hit.rx() < -.5, "strike winds back, then thrusts forward");
        // In the real first-person renderer, a negative X pitch pushed LIFT's wrist
        // below the viewport. Keep a conservative vanilla hand-position proxy in view;
        // the actual hand and offhand are additionally checked by the live QA script.
        var visible=EnumSet.of(CastPose.Archetype.LIFT,CastPose.Archetype.GATHER,CastPose.Archetype.SWEEP,CastPose.Archetype.FLICK);
        for(var a:CastPose.Archetype.values()) if(a.ordinal()>=9) visible.add(a);
        for (var raised : visible) {
            var hand = CastPose.hand(raised, raised == CastPose.Archetype.FLICK || raised == CastPose.Archetype.SWEEP ? 4 : 20, -1, true);
            double pitch = Math.toRadians(hand.rotX());
            double wristY = -.6 * Math.cos(pitch) + .72 * Math.sin(pitch) + hand.y();
            double wristDepth = .72 * Math.cos(pitch) + .6 * Math.sin(pitch) - hand.z();
            check(wristDepth > 0 && wristY / wristDepth > -.6, raised + " wrist stays above the bottom of the first-person viewport");
            var wr=CastPose.wristCamera(hand,true);
            var wl=CastPose.wristCamera(CastPose.hand(raised,raised == CastPose.Archetype.FLICK || raised == CastPose.Archetype.SWEEP ? 4 : 20,-1,false),false);
            check(Math.abs(wr.x()+wl.x())<1e-9 && Math.abs(wr.y()-wl.y())<1e-9 && Math.abs(wr.z()-wl.z())<1e-9,"control anchor mirrors with the real hand transform: "+raised);
            check(-wr.z()>0 && wr.y()/(-wr.z())>-.6,"all-axis wrist proxy stays visible: "+raised);
            check(Math.abs(wr.x()/wr.z())<1,"wrist stays inside horizontal viewport: "+raised);
        }
        check(CastPose.firstPersonOwnedElsewhere("dir_shock") && CastPose.firstPersonOwnedElsewhere("dir_blast") && !CastPose.firstPersonOwnedElsewhere("vector_construct"), "actual native hand-matrix animations retain exclusive ownership");
        check(!CastPose.firstPersonOwnedElsewhere("charging") && CastPose.of("charging") == CastPose.Archetype.GATHER
                && !CastPose.hand(CastPose.of("charging"),20,-1,true).equals(CastPose.Hand.NONE), "charging restores its gathering hand gesture alongside charge arcs");
        check(!CastPose.firstPersonOwnedElsewhere("railgun") && CastPose.of("railgun") == CastPose.Archetype.AIM
                && !CastPose.hand(CastPose.of("railgun"),20,-1,true).equals(CastPose.Hand.NONE), "railgun overlays are not a substitute for its aiming hand gesture");
        System.out.println("PASS " + checks + " cast pose: full skill coverage, bounded gestures, mirroring, timeline, identity");
    }
}
