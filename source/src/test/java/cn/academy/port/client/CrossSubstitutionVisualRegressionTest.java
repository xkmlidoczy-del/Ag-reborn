package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.UUID;

/** Frozen A/B leases, strict native-needle provenance, replay fences and camera-relative precision. */
public final class CrossSubstitutionVisualRegressionTest {
    private static final UUID OWNER = new UUID(321,1), OTHER = new UUID(321,2);
    private static final Point A = new Point(2,64,0), B = new Point(5,64,0), SOURCE = new Point(0,65.62,0), IMPACT = new Point(8,65,0);
    private static int checks;
    private static void check(boolean value, String text) { checks++; if (!value) throw new AssertionError(text); }
    private static CrossSubstitutionState.Frame frame(long input, long token, long sequence, int phase, int prepared, int remaining, int needle) {
        return new CrossSubstitutionState.Frame(new CrossSubstitutionState.Identity(19,OWNER,71,input,token), sequence, phase,
                prepared, remaining, prepared>0?A:null, prepared==2?B:null,
                needle>=0?SOURCE:null, needle>=0?(needle==1?B:A):null, needle>=0?IMPACT:null,
                needle, needle>=0?91+needle:-1, needle>=0?new UUID(321,11+needle):null,
                phase==CrossSubstitutionState.MARKED || phase==CrossSubstitutionState.IMPACT, "");
    }
    private static CrossSubstitutionState.Frame edit(CrossSubstitutionState.Frame f, Point a, Point b, int entity, UUID uuid, boolean accepted) {
        return new CrossSubstitutionState.Frame(f.id(),f.sequence(),f.phase(),f.prepared(),f.remaining(),a,b,f.source(),f.arrival(),f.impact(),
                f.needleSlot(),entity,uuid,accepted,f.reason());
    }
    private static CrossSubstitutionState.Frame swap(long input, long sequence, int phase, int prepared, int remaining, Point source, Point arrival) {
        return new CrossSubstitutionState.Frame(new CrossSubstitutionState.Identity(19,OWNER,71,input,1), sequence, phase,
                prepared, remaining, prepared>0?A:null, prepared==2?B:null, source, arrival, arrival,
                -1,-1,new UUID(0,0),phase==CrossSubstitutionState.SWAPPED, "");
    }
    public static void main(String[] args) {
        var state = new CrossSubstitutionState(); var first = frame(1,1,1,1,1,400,-1);
        check(state.accept(first,true) && state.markerVisible(), "verified A creates one preparation");
        check(!state.accept(first,true), "duplicate cannot replay preparation");
        for(int i=0;i<31;i++) check(state.tick(false,true)==null,"silence alone never destructively aborts preparation");
        check(!state.markerVisible() && state.active()!=null && state.remainingTicks()==369,"stale heartbeat hides cosmetics while original clock continues");
        check(state.accept(frame(1,1,2,1,1,390,-1),true) && state.remainingTicks()==369 && state.markerVisible(),"heartbeat restores visibility without replenishing lease");
        check(!state.accept(frame(1,1,3,1,1,400,-1),true),"authoritative lease increase refused");
        var second = frame(2,1,3,1,2,380,-1);
        check(!state.accept(edit(second,new Point(3,64,0),B,-1,null,true),true),"B cannot move frozen A");
        check(!state.accept(frame(1,1,3,1,2,380,-1),true),"heartbeat cannot add B without fresh input");
        check(state.accept(second,true) && state.remainingTicks()==369,"fresh B retains stable token and original A lifetime");
        var firstShot = frame(3,1,4,2,2,0,0);
        check(!state.accept(frame(3,1,4,3,2,0,0),true),"impact cannot create an unpaid unseen needle");
        check(!state.accept(edit(firstShot,A,B,91,new UUID(0,0),false),true),"zero needle UUID invalid");
        check(state.accept(firstShot,true) && !state.markerVisible() && state.firing(),"actual first publication consumes visible preparation");
        check(!state.accept(frame(3,1,5,2,2,0,0),true),"same slot cannot publish a second needle");
        check(!state.accept(edit(frame(3,1,5,2,2,0,1),A,B,91,new UUID(321,11),false),true),"slots cannot share physical identity");
        check(state.accept(frame(3,1,5,2,2,0,1),true),"B needle has separate native identity");
        check(!state.accept(edit(frame(3,1,6,3,2,0,0),A,B,91,new UUID(321,99),true),true),"native id recycling cannot admit wrong UUID contact");
        check(state.accept(edit(frame(3,1,6,3,2,0,0),A,B,91,new UUID(321,11),false),true),"native iframe-rejected contact is terminal for its own needle");
        check(!state.accept(frame(3,1,7,3,2,0,0),true),"later receipt cannot convert rejected contact into confirmed hit");
        check(state.accept(frame(3,1,7,2,2,0,2),true),"third actual A needle admitted despite earlier contact");
        check(state.accept(frame(3,1,8,3,2,0,1),true),"accepted native hit matches known B needle");
        check(!state.accept(frame(3,1,9,1,2,1,-1),true),"firing cannot return to preparation");
        check(state.accept(frame(3,1,9,4,2,0,-1),true) && state.active()==null,"ENDED retires after actual flights");
        check(!state.accept(frame(3,1,10,3,2,0,2),true),"terminal receipt fences delayed contact");
        check(!state.accept(frame(4,1,10,1,1,400,-1),true),"new input cannot resurrect old controller token");
        check(state.accept(frame(4,2,1,1,1,400,-1),true),"fresh higher token creates new preparation");
        state.cancel(71,4);
        check(!state.accept(frame(4,2,2,1,1,390,-1),true) && state.active()==null,"explicit cancel fences delayed mark");
        state.resetSession(); check(state.accept(first,true),"verified new connection resets replay provenance");
        for(int i=0;i<399;i++) check(state.tick(false,true)==null,"400-tick lease remains until exact expiry");
        check(state.tick(false,true)!=null && state.active()==null,"lease expiry retires once");
        check(!state.accept(frame(1,1,2,1,1,1,-1),true),"late heartbeat cannot revive locally expired preparation");
        var observer = new CrossSubstitutionState();
        check(!observer.accept(first,false),"remote owners cannot create private preparation labels");
        check(observer.accept(firstShot,false),"remote authoritative publication can create endpoint cue");
        check(observer.accept(frame(3,1,5,3,2,0,0),false),"remote native contact requires prior certified publication");
        check(CrossSubstitutionState.MAX_NEEDLES==3,"fixed maximum of three native histories");
        check(!CrossSubstitutionState.valid(frame(1,1,1,1,1,CrossSubstitutionState.PREPARATION_TICKS + 1,-1)),"preparation cannot exceed the configured duration");
        check(!CrossSubstitutionState.valid(frame(3,1,4,2,2,0,3)),"fourth needle cannot allocate history");
        var close = CrossSubstitutionState.relative(new Point(30_000_000.01,64,30_000_000.02), new Point(30_000_000,64,30_000_000));
        check(Math.abs(close.x()-.01)<1e-8 && Math.abs(close.z()-.02)<1e-8,"double camera subtraction preserves millimetre-scale far-world corners");
        check(CrossSubstitutionState.labelScale(.1,.1,50)==0 && CrossSubstitutionState.labelScale(-1,1,50)==0,"near and behind-camera labels hidden");
        check(CrossSubstitutionState.labelScale(.4,.4,50)*50<=.4*.22+.000001,"near text physical width capped by depth");
        check(CrossSubstitutionState.labelScale(30,30,50)<=.022,"far label scale remains compact");
        var full = new CrossSubstitutionState();
        for(int i=0;i<CrossSubstitutionState.MAX_WATERMARKS;i++) {
            var id = new CrossSubstitutionState.Identity(19,new UUID(321,i+1),71,1,1);
            var f = new CrossSubstitutionState.Frame(id,1,1,1,400,A,null,null,null,null,-1,-1,null,true,"");
            check(full.accept(f,true),"bounded owner watermark admitted");
        }
        var overflow = new CrossSubstitutionState.Frame(new CrossSubstitutionState.Identity(19,OTHER,72,2,2),2,1,1,400,A,null,null,null,null,-1,-1,null,true,"");
        check(full.watermarkCount()==CrossSubstitutionState.MAX_WATERMARKS,"watermark storage remains bounded");
        overflow = new CrossSubstitutionState.Frame(new CrossSubstitutionState.Identity(19,new UUID(9999,9999),71,1,1),1,1,1,400,A,null,null,null,null,-1,-1,null,true,"");
        check(!full.accept(overflow,true),"capacity refuses new owner rather than discarding replay fences");
        selfSwap();
        System.out.println("CrossSubstitutionVisualRegressionTest: " + checks + " checks passed");
    }
    private static void selfSwap() {
        var state = new CrossSubstitutionState();
        check(state.accept(frame(1,1,1,1,1,400,-1),true),"swap preparation starts with authenticated A");
        for(int i=0;i<20;i++)state.tick(false,true);
        var missingB = swap(2,2,6,1,390,A,A);
        check(state.accept(missingB,true) && state.remainingTicks()==380 && state.markerVisible() && !state.firing(),
                "missing-B refusal preserves A and clamps its original lease without starting a flight");
        check(!state.accept(missingB,true),"swap refusal cannot replay");
        check(!state.accept(swap(3,3,6,2,380,A,B),true),"refusal cannot fabricate a second mark");
        check(!state.accept(swap(3,3,6,1,400,A,A),true),"refusal cannot renew original authoritative lease");
        check(state.accept(frame(3,1,3,1,2,370,-1),true),"standing fresh tap can place B after swap refusal");
        var ready = state.active();
        check(CrossSubstitutionState.nearbyMark(ready,new Point(2.75,64.125,0))==1,"inclusive horizontal and vertical guidance boundary selects A");
        check(CrossSubstitutionState.nearbyMark(ready,new Point(2.750001,64,0))==0,"guidance refuses beyond horizontal proximity");
        check(CrossSubstitutionState.nearbyMark(ready,new Point(2,64.125001,0))==0,"guidance refuses beyond vertical proximity");
        check(CrossSubstitutionState.nearbyMark(ready,B)==2,"guidance supports B-to-A direction");
        check(CrossSubstitutionState.nearbyMark(ready,new Point(Double.NaN,64,0))==0,"nonfinite guidance cannot imply proximity");
        check(CrossSubstitutionState.nearbyMark(edit(ready,A,new Point(2.5,64,0),-1,null,true),new Point(2.25,64,0))==0,
                "ambiguous proximity never chooses a destination");
        var refused = swap(4,4,6,2,350,new Point(2.1,64,0),B);
        check(state.accept(refused,true) && state.markerVisible() && state.remainingTicks()==350,"ordinary swap refusal keeps both marks");
        check(!state.accept(edit(swap(5,5,6,2,340,A,B),new Point(2.01,64,0),B,-1,new UUID(0,0),false),true),
                "swap refusal cannot shift frozen A");
        check(!CrossSubstitutionState.valid(edit(refused,A,B,-1,new UUID(0,0),true)),"swap refusal cannot claim acceptance");
        check(!CrossSubstitutionState.valid(edit(refused,A,B,91,new UUID(321,11),false)),"swap refusal cannot carry a physical needle identity");
        var success = swap(5,5,7,2,0,new Point(2.1,64,0),B);
        check(!state.accept(swap(4,5,7,2,0,A,B),true),"refused nonce cannot be reused for a later successful swap");
        check(!CrossSubstitutionState.valid(edit(success,A,B,-1,new UUID(0,0),false)),"unconfirmed teleport cannot produce a success cue");
        check(!CrossSubstitutionState.valid(edit(success,A,B,91,new UUID(321,11),true)),"swap success is distinct from needle publication");
        check(!CrossSubstitutionState.valid(swap(5,5,7,2,1,A,B)),"success cannot preserve a preparation lease");
        check(!CrossSubstitutionState.valid(swap(5,5,7,1,0,A,B)),"success requires diagnostic identity of both marks");
        check(!CrossSubstitutionState.valid(swap(5,5,7,2,0,new Point(40,64,0),B)),"out-of-range receipt cannot draw a swap endpoint cue");
        check(!CrossSubstitutionState.valid(swap(5,5,7,2,0,null,B)),"swap cue requires actual confirmed source");
        check(state.accept(success,true) && state.active()==null && state.remainingTicks()==0,"confirmed swap retires marks with actual endpoint receipt");
        check(!state.accept(success,true),"confirmed swap cannot replay");
        check(!state.accept(frame(6,1,6,1,2,340,-1),true),"late heartbeat cannot resurrect consumed marks");
        var observer = new CrossSubstitutionState();
        check(!observer.accept(refused,false),"private preflight refusal never leaks remote preparation");
        check(observer.accept(success,false),"observer can admit confirmed actual endpoints without a needle entity");
        check(!observer.accept(swap(5,6,7,2,0,A,B),false),"remote terminal fence suppresses repeated endpoint cues");
        var empty = new CrossSubstitutionState();
        var noMarks = swap(1,1,6,0,0,A,A);
        check(empty.accept(noMarks,true) && empty.active()==null && CrossSubstitutionState.terminal(noMarks),"unprepared swap refusal releases pending input");
        check(!CrossSubstitutionState.valid(swap(1,1,6,0,400,A,A)),"unprepared refusal cannot invent a lease");
        var expired = new CrossSubstitutionState(); expired.accept(swap(1,1,6,2,1,A,B),true);
        check(expired.tick(false,true)!=null && expired.active()==null,"preserved refusal preparation expires at its original deadline");
        var firing = new CrossSubstitutionState(); firing.accept(frame(3,1,1,2,2,0,0),true);
        check(!firing.accept(swap(4,2,6,2,1,A,B),true),"swap refusal cannot resurrect marks during a salvo");
        check(!firing.accept(swap(4,2,7,2,0,A,B),true),"swap success cannot cross an active native salvo");
        var cancelled = new CrossSubstitutionState(); cancelled.accept(frame(1,1,1,1,2,400,-1),true);cancelled.cancel(71,2);
        check(!cancelled.accept(swap(2,2,7,2,0,A,B),true),"cancelled input fences delayed swap success");
        var uncertain = new CrossSubstitutionState(); uncertain.accept(frame(1,1,1,1,2,400,-1),true);
        check(uncertain.accept(frame(2,1,2,5,2,0,-1),true) && uncertain.active()==null,"unconfirmed paid swap failure ends without an arrival receipt");
        check(!uncertain.accept(swap(2,3,7,2,0,A,B),true),"unconfirmed terminal outcome cannot later become fake success");
    }
}
