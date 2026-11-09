package cn.academy.port.highpower.core;

/** Counterexamples: HOLD refresh rewinding pull, stale tokens reviving a throw, invisible recovery and yaw wrap. */
public final class ConstructPoseRegressionTest {
    private static int checks;
    private static void check(boolean b,String why) { checks++; if(!b) throw new AssertionError(why); }
    private static double distance(CastPose.Motion a,CastPose.Motion b) {
        return Math.abs(a.arms().rx()-b.arms().rx())+Math.abs(a.arms().rz()-b.arms().rz())+Math.abs(a.hand().z()-b.hand().z());
    }
    public static void main(String[] args) {
        var t=new ConstructPose.Timeline();
        check(t.sample(0,0,true).equals(CastPose.Motion.NONE),"unconfirmed input never animates");
        check(t.accept(1,ConstructPose.PREVIEW,10,0),"preview accepted");
        var lock=t.sample(20,0,true);
        check(lock.strength()==1 && lock.arms().rx()>-1.7,"target lock stays forward, below overhead");
        check(t.accept(1,ConstructPose.HOLD,20,0),"hold accepted");
        check(distance(lock,t.sample(20,0,true))==0,"no discontinuity on grab confirmation");
        var pull=t.sample(25,0,true);
        for(int tick=22;tick<=40;tick+=2) check(t.accept(1,ConstructPose.HOLD,tick,0),"refresh accepted");
        check(t.age(40)==20,"HOLD refresh must not restart the pull animation");
        var hold=t.sample(40,0,true);
        check(distance(pull,hold)>.03,"pull evolves into steady grip");
        check(distance(hold,t.sample(40,50,true))>.2,"view rotation visibly turns the controlling wrist");
        var right=t.sample(40,50,true); var left=t.sample(40,50,false);
        check(Math.abs(right.arms().rz()+left.arms().lz())<1e-9 && Math.abs(right.hand().rotZ()+left.hand().rotZ())<1e-9,"steering mirrors for left hand");
        check(t.accept(1,ConstructPose.THROWN,40,50),"throw accepted");
        check(distance(right,t.sample(40,50,true))<1e-9,"throw begins at current grip");
        check(t.sample(46,50,true).hand().z()<-.35,"confirmed throw thrusts forward");
        check(!t.accept(1,ConstructPose.HOLD,47,50),"late HOLD cannot revive thrown body");
        check(!t.accept(0,ConstructPose.PREVIEW,48,0),"zero token rejected");
        check(t.sample(70,50,true).strength()==0,"throw returns to neutral");
        check(t.accept(2,ConstructPose.HOLD,80,179),"new body can enter hold without preview");
        for(int tick=82;tick<=96;tick+=2)t.accept(2,ConstructPose.HOLD,tick,179);
        check(distance(t.sample(96,179,true),t.sample(96,-179,true))<.1,"yaw wraps without wrist flip");
        check(!t.accept(1,ConstructPose.END,97,0),"old END cannot cancel new body");
        check(t.sample(113,179,true).strength()==0,"lost HOLD times out without END");
        check(t.accept(2,ConstructPose.END,114,179),"explicit cancellation");
        check(t.sample(125,179,true).strength()==0,"cancel restores vanilla pose");
        for(int phase=0;phase<=6;phase++) for(boolean hand:new boolean[]{true,false}) {
            var x=new ConstructPose.Timeline(); x.accept(7,phase,0,0);
            for(double age=0;age<140;age+=.125) {
                var m=x.sample(age,age*37,hand); var h=m.hand(); var a=m.arms();
                check(Double.isFinite(h.x()+h.y()+h.z()+h.rotZ()+a.rx()+a.rz()+a.lean()),"finite transform");
                check(m.strength()>=0 && m.strength()<=1,"blend bounded");
                check(Math.abs(h.z())<=.6 && Math.abs(h.y())<=.6 && Math.abs(a.rz())<3.2,"viewport/limb bounds");
            }
            check(x.sample(Double.NaN,0,hand).equals(CastPose.Motion.NONE),"hostile clock silent");
        }
        // Dash: coil (crouch rises over the wind-up), run (lean forward, arms swept behind), then the squat eases out through the grab.
        var d=new ConstructPose.Timeline(); check(d.accept(9,ConstructPose.DASH,100,0),"dash accepted");
        for(int k=0;k<12;k++) d.accept(9,ConstructPose.DASH,102+2*k,0);
        check(d.stance(100).crouch()==0 && d.stance(100+ConstructPose.CROUCH_TICKS).crouch()>.99, "crouch rises through the wind-up");
        check(d.stance(100+ConstructPose.CROUCH_TICKS+6).lean()>.5 && d.stance(100+ConstructPose.CROUCH_TICKS+6).crouch()<.4, "running lean with a shallower crouch");
        for(double age=0;age<30;age+=.25) { var m=d.sample(100+age,0,true); check(m.arms().rx()>=0 && m.arms().lx()>=0 && m.strength()>=0,"dash arms are never raised in front of the body"); }
        check(d.accept(9,ConstructPose.HOLD,126,0),"hold follows the dash");
        check(d.stance(126).crouch()>.25 && d.stance(126).lean()>.5 && d.stance(140).crouch()<.01 && d.stance(140).lean()<.01, "the grab starts from the dash stance and stands up");
        check(!d.accept(9,ConstructPose.DASH,127,0) && d.phase()==ConstructPose.HOLD, "a late dash cue cannot pull a held body back into the dash");
        check(new ConstructPose.Timeline().stance(5).equals(ConstructPose.Stance.NONE) && d.stance(Double.NaN).equals(ConstructPose.Stance.NONE), "no stance without a cue or with a hostile clock");
        // Reviewed high-power signatures: no generic overhead lifting and distinct per-system machinery.
        String[] ids={"iron_sand_lance","rail_thunder_array","magnetic_domain","thunder_clap","multicore_canopy","plasma_cannon","phase_corridor","tactical_coordinate_formation","vector_cage","zenith_vector_dominion","vector_construct"};
        var used=new java.util.HashSet<CastPose.Archetype>();
        for(String id:ids) { check(CastPose.of(id)!=CastPose.Archetype.LIFT,"no overhead ritual: "+id); check(used.add(CastPose.of(id)),"unique flagship profile: "+id); }
        System.out.println("PASS "+checks+" construct/technology gesture assertions");
    }
}
