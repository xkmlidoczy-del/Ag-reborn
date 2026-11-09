package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Release ownership, frozen plans, actual contact provenance and deliberate consent navigation. */
public final class SpatialPincerClientRegressionTest {
    private static final UUID OWNER = new UUID(341, 1);
    private static final Point A = new Point(2,64,0), B = new Point(5,64,0), C = new Point(8,64,0), SOURCE = new Point(0,65.6,0), IMPACT = new Point(9,65,0);
    private static final Object WORLD = new Object(), PLAYER = new Object(), CONNECTION = new Object();
    private static int checks;
    private static void check(boolean value, String text) { checks++; if (!value) throw new AssertionError(text); }
    public static void main(String[] args) {
        inputs(); visualReceipts(); frozenTargets(); review(); geometry();
        System.out.println("SpatialPincerClientRegressionTest: " + checks + " checks passed; native visual and human consent QA remain separate");
    }
    private static SpatialPincerInputState.Gates gates(boolean screenClosed, boolean ownedPicker, boolean focused, long revision) {
        return new SpatialPincerInputState.Gates(new SpatialPincerInputState.DispatchContext(WORLD, PLAYER, CONNECTION, 1, 19, 71, 0, revision, "corridor"),
                0, -100, true, true, true, true, true, true, screenClosed, ownedPicker, focused, true, false, false, true);
    }
    private static HighPowerInput.Input id(long input) { return new HighPowerInput.Input(0,input,19,71); }
    private static void tap(SpatialPincerInputState state, long input, SpatialPincerInputState.Gates gates) {
        check(state.press(id(input),gates), "new deliberate gesture admitted"); state.release(id(input));
        check(state.flushStartPost(gates,i->{},i->{}) && state.flushReleasePost(gates,i->{},i->{}), "post-movement start precedes release");
    }
    private static void inputs() {
        var state = new SpatialPincerInputState(); var open = gates(true,false,true,1); var sent = new ArrayList<String>();
        check(state.press(id(1),open), "free clear gesture remains reachable during cooldown or bad posture");
        check(!state.press(id(2),open), "holding cannot synthesize repeated casts");
        check(state.flushStartPost(open,i->sent.add("start"),i->sent.add("abort")), "press sends only native intent");
        check(!state.permitsReceipt(1,71,false) && state.permitsReceipt(1,71,true), "unreleased gesture cannot authorize a mark or picker");
        state.accepted(1,71,400,false); check(state.acceptedRemainingTicks()==0, "new-input heartbeat cannot steal release acknowledgement");
        for (int i=0;i<6;i++) state.tick(open,false,x->sent.add("abort"));
        check(sent.equals(List.of("start")), "local six-tick hold does not clear or cast speculatively");
        state.release(id(1)); check(state.flushReleasePost(open,i->sent.add("release"),i->{}), "clear decision waits for actual key release");
        state.accepted(1,71,400,false); check(state.permits(1,71), "accepted plan survives key up");
        for (int i=0;i<80;i++) state.tick(open,true,x->{throw new AssertionError("pause must preserve lease");});
        check(state.acceptedRemainingTicks()==400, "pause freezes input lifetime");
        for (int i=0;i<20;i++) state.tick(open,false,x->{});
        tap(state,2,open); state.accepted(2,71,399,false);
        check(state.acceptedRemainingTicks()==380, "later mark cannot renew first mark's deadline");
        var picker = gates(false,true,true,1);
        state.tick(picker,false,x->{throw new AssertionError("owned picker must preserve plan");});
        check(state.ownsControl(picker) && !state.eligible(picker) && !state.press(id(3),picker), "only own picker preserves ownership without allowing screen key casts");
        state.tick(gates(false,false,true,1),false,x->sent.add("cancel"));
        check(state.current()==null && sent.contains("cancel"), "unrelated screen cancels with original identity");
        tap(state,3,open); state.accepted(3,71,400,false); tap(state,4,open); state.accepted(4,71,0,true);
        check(state.firing() && state.acceptedRemainingTicks()==80, "first publication opens one bounded flight receipt window");
        check(!state.eligible(open) && !state.press(id(5),open), "running volley cannot capture a replacement gesture");
        for (int i=0;i<10;i++) state.tick(open,false,x->{});
        state.accepted(4,71,0,true); check(state.acceptedRemainingTicks()==70, "later projectile or hit cannot extend flight admission");
        for (int i=0;i<58;i++) state.tick(open,false,x->{});
        check(state.firing() && state.acceptedRemainingTicks()==12, "ownership survives tick68: last launch at28 plus40 native flight ticks");
        state.accepted(4,71,0,true); check(state.acceptedRemainingTicks()==12, "late eighth publication cannot renew ownership");
        for (int i=0;i<11;i++) state.tick(open,false,x->{});
        check(state.firing() && state.acceptedRemainingTicks()==1, "ownership remains bounded through tick79");
        state.tick(open,false,x->{}); check(state.current()==null, "ownership retires at tick80 without extending server preparation");
        tap(state,5,open); state.accepted(5,71,400,false);
        state.tick(gates(true,false,false,1),false,x->{}); check(state.current()==null, "focus loss clears owned input");
        tap(state,6,open); state.accepted(6,71,400,false);
        state.tick(gates(true,false,true,2),false,x->{}); check(state.current()==null, "preset revision revokes plan authority");
    }
    private static List<SpatialPincerUiState.Target> targets(int count) {
        var targets = new ArrayList<SpatialPincerUiState.Target>();
        for (int i=0;i<count;i++) targets.add(new SpatialPincerUiState.Target(201+i,new UUID(341,201+i),new Point(12+i*.125,65.2,3+i)));
        return targets;
    }
    private static Point emitter(int needle) {
        Point point=List.of(A,B,C).get(needle%3); return new Point(point.x(),point.y()+1.2,point.z());
    }
    private static SpatialPincerUiState.Frame frame(long input,long token,long sequence,int phase,int prepared,int remaining,int needle,boolean hurt) {
        return frame(input,token,sequence,phase,prepared,remaining,needle,hurt,List.of(),needle>=0?3:0);
    }
    private static SpatialPincerUiState.Frame frame(long input,long token,long sequence,int phase,int prepared,int remaining,int needle,boolean hurt,
                                                    List<SpatialPincerUiState.Target> targets,int needles) {
        return new SpatialPincerUiState.Frame(new SpatialPincerUiState.Controller(19,OWNER,71,input,token), sequence,phase,prepared,remaining,
                prepared>=1?A:null,prepared>=2?B:null,prepared==3?C:null,
                needle>=0?SOURCE:null,needle>=0?emitter(needle):null,
                needle>=0?(targets!=null && !targets.isEmpty()?targets.get(needle%targets.size()).endpoint():IMPACT):null,
                needle,needle>=0?91+needle:-1,needle>=0?new UUID(341,11+needle):null,
                phase==SpatialPincerUiState.MARKED || phase==SpatialPincerUiState.PICKER || phase==SpatialPincerUiState.PREVIEW || phase==SpatialPincerUiState.IMPACT && hurt,
                "",targets,needles);
    }
    private static SpatialPincerUiState.Frame changed(SpatialPincerUiState.Frame f,SpatialPincerUiState.Controller id,
                                                      Point a,Point arrival,Point impact,List<SpatialPincerUiState.Target> targets,int needles) {
        return new SpatialPincerUiState.Frame(id,f.sequence(),f.phase(),f.prepared(),f.remaining(),a,f.pointB(),f.pointC(),f.source(),arrival,impact,
                f.needleSlot(),f.needleEntity(),f.needleUuid(),f.accepted(),f.reason(),targets,needles);
    }
    private static SpatialPincerUiState.VisualState prepared() {
        var state=new SpatialPincerUiState.VisualState();
        for(int i=1;i<=3;i++) check(state.accept(frame(i,1,i,1,i,410-i*10,-1,false),true), "each fresh release freezes exactly one coordinate");
        return state;
    }
    private static void visualReceipts() {
        var state = new SpatialPincerUiState.VisualState(); var first = frame(1,1,1,1,1,400,-1,false);
        check(state.accept(first,true) && state.markerVisible(), "authoritative A creates one plan");
        check(!state.accept(first,true), "replayed sequence rejected");
        for (int i=0;i<31;i++) check(state.tick(false,true)==null, "missing heartbeat never invents cancellation");
        check(!state.markerVisible() && state.active()!=null && state.remainingTicks()==369, "silence hides stale marks while preserving real lease");
        check(state.accept(frame(1,1,2,1,1,390,-1,false),true) && state.remainingTicks()==369, "fresh heartbeat restores view without extending deadline");
        check(!state.accept(frame(1,1,3,1,2,380,-1,false),true), "heartbeat cannot invent B");
        check(!state.accept(frame(2,1,3,1,3,380,-1,false),true), "one release cannot skip B and create C");
        check(state.accept(frame(2,1,3,1,2,380,-1,false),true), "new release captures B");
        check(state.accept(frame(3,1,4,1,3,370,-1,false),true), "third release captures C");
        check(state.accept(frame(4,1,5,6,3,360,-1,false),true), "fourth crouch-release authorizes picker phase only");
        check(state.accept(frame(4,1,6,1,3,350,-1,false),true), "picker heartbeat keeps the same fixed plan");
        check(!state.accept(frame(5,1,7,2,3,0,0,false),true), "old fourth-tap fire receipt cannot bypass target preview");
        var targets=targets(2);
        check(state.accept(frame(5,1,7,7,3,340,-1,false,targets,3),true), "fresh selection freezes two actual targets");
        check(!state.accept(frame(5,1,8,2,3,0,0,false,targets,3),true), "selection input cannot also authorize publication");
        check(!state.accept(frame(6,1,8,3,3,0,0,true,targets,3),true), "unseen impact cannot fabricate a needle");
        check(state.accept(frame(6,1,8,2,3,0,0,false,targets,3),true), "fresh confirmation admits actual first publication");
        check(!state.markerVisible() && state.firing(), "flight state never leaves stale target previews");
        check(!state.accept(frame(6,1,9,2,3,0,0,false,targets,3),true), "same slot cannot publish twice");
        check(state.accept(frame(6,1,9,3,3,0,0,false,targets,3),true), "actual rejected hurt remains a contact with no hit acknowledgement");
        check(!state.accept(frame(6,1,10,3,3,0,0,true,targets,3),true), "duplicate cannot upgrade rejected contact into a hit");
        check(state.accept(frame(6,1,10,2,3,0,1,false,targets,3),true) && state.accept(frame(6,1,11,2,3,0,2,false,targets,3),true), "remaining physical needles use B/T2 then C/T1");
        check(state.accept(frame(6,1,12,3,3,0,2,true,targets,3),true), "matching accepted third contact admitted");
        check(!state.accept(frame(6,1,13,7,3,1,-1,false,targets,3),true), "flight cannot revive preview");
        check(state.accept(frame(6,1,13,4,3,0,-1,false,targets,3),true) && state.active()==null, "end is cleanup only");
        check(!state.accept(frame(6,1,14,3,3,0,1,true,targets,3),true), "terminal prevents delayed impact");
        var observer = new SpatialPincerUiState.VisualState();
        check(!observer.accept(first,false), "private marks are never admitted for another player");
        check(!observer.accept(frame(5,1,7,7,3,340,-1,false,targets,3),false), "target preview is owner-private");
        check(!observer.accept(frame(6,1,8,2,3,0,0,false,targets,3),false), "observer never receives a complete private target list");
        check(observer.accept(frame(6,1,8,2,3,0,0,false),false), "redacted actual remote publication can earn a bounded cue");
        state.resetSession(); check(state.accept(first,true), "new connection resets authenticated history");
        for (int i=0;i<100;i++) state.tick(true,true);
        check(state.remainingTicks()==400, "paused visual lifetime stays fixed");
        for (int i=0;i<399;i++) state.tick(false,true);
        check(state.tick(false,true)!=null && state.active()==null, "exact400 tick preparation expiry retires once");
        check(!state.accept(frame(2,1,2,1,2,400,-1,false),true), "late receipt cannot restart an expired preparation");
    }
    private static void frozenTargets() {
        for(int count=1;count<=8;count++) {
            int needles=Math.max(3,count); var targets=targets(count); var state=prepared();
            var preview=frame(4,1,4,7,3,370,-1,false,targets,needles);
            check(SpatialPincerUiState.requiredNeedles(count)==needles && state.accept(preview,true), "each target count1..8 freezes exact required needle budget");
            int shares=0; for(int i=0;i<count;i++) shares+=SpatialPincerUiState.targetShares(count,i);
            check(shares==needles, "display allocation accounts for every actual needle exactly once");
            check(!state.accept(preview,true), "preview replay cannot refresh expiry");
            for(int i=0;i<20;i++) state.tick(false,true);
            check(state.accept(frame(4,1,5,7,3,369,-1,false,targets,needles),true) && state.remainingTicks()==350, "preview heartbeat cannot renew original lifetime");
            check(!state.accept(frame(5,1,6,7,3,349,-1,false,targets,needles),true), "new input cannot replace the frozen preview");
            check(!state.accept(frame(4,1,6,1,3,349,-1,false),true), "old marked receipt cannot discard frozen targets");
            check(!state.accept(frame(5,1,6,6,3,349,-1,false),true), "frozen attack cannot switch to teammate picker");
            for(int i=0;i<needles;i++) {
                var projected=frame(5,1,6+i,2,3,0,i,false,targets,needles);
                check(state.accept(projected,true), "actual needle slot admitted independently of three coordinate slots");
                check(state.active().arrival().equals(emitter(i)) && state.active().impact().equals(targets.get(i%count).endpoint()), "launch cycles A/B/C and ordered frozen target endpoints exactly");
            }
            check(!state.accept(frame(6,1,20,2,3,0,0,false,targets,needles),true), "another input cannot join a running volley");
            var impact=frame(5,1,20,3,3,0,needles-1,true,targets,needles);
            check(state.accept(changed(impact,impact.id(),A,impact.arrival(),new Point(13.125,65.25,2.75),targets,needles),true), "actual accepted contact may differ from intended endpoint");
        }
        check(SpatialPincerUiState.targetShares(2,0)==2 && SpatialPincerUiState.targetShares(2,1)==1, "T2 primary receives two shares and secondary one");
        check(SpatialPincerUiState.requiredNeedles(0)==0 && SpatialPincerUiState.requiredNeedles(9)==0, "zero and nine target budgets fail closed");
        var targets=targets(8); var preview=frame(4,1,4,7,3,370,-1,false,targets,8); var state=prepared();
        targets.clear(); check(preview.targets().size()==8, "caller cannot mutate the frozen target list after receipt creation");
        boolean immutable=false; try { preview.targets().clear(); } catch(UnsupportedOperationException expected) { immutable=true; }
        check(immutable && state.accept(preview,true), "exposed target snapshot is immutable");
        var moved=new ArrayList<>(preview.targets()); var original=moved.get(0);
        moved.set(0,new SpatialPincerUiState.Target(original.entity(),original.uuid(),new Point(12.001,65.2,3)));
        check(!state.accept(frame(4,1,5,7,3,369,-1,false,moved,8),true), "heartbeat cannot autotrack a target by moving its endpoint");
        var reordered=new ArrayList<>(preview.targets()); java.util.Collections.swap(reordered,0,1);
        check(!state.accept(frame(4,1,5,7,3,369,-1,false,reordered,8),true), "heartbeat cannot silently change primary target or allocation order");
        var projected=frame(5,1,5,2,3,0,7,false,preview.targets(),8);
        check(!SpatialPincerUiState.valid(changed(projected,projected.id(),A,emitter(0),projected.impact(),preview.targets(),8)), "slot7 must launch at exact B emitter");
        check(!SpatialPincerUiState.valid(changed(projected,projected.id(),A,projected.arrival(),preview.targets().get(0).endpoint(),preview.targets(),8)), "slot7 must name exact T8 frozen endpoint");
        check(!SpatialPincerUiState.valid(frame(5,1,5,2,3,0,8,false,preview.targets(),8)), "ninth needle is outside bounded history");
        check(!state.accept(changed(projected,projected.id(),new Point(A.x()+.001,A.y(),A.z()),projected.arrival(),projected.impact(),preview.targets(),8),true), "frozen coordinate cannot shift by even a millimetre");
        for(int count:new int[]{-1,0,1,2,7,9}) check(!SpatialPincerUiState.valid(frame(4,1,5,7,3,369,-1,false,preview.targets(),count)), "mismatched or invalid N fails closed");
        for(List<SpatialPincerUiState.Target> invalid:List.of(List.<SpatialPincerUiState.Target>of(),targets(9),List.of(original,original),
                List.of(new SpatialPincerUiState.Target(4,new UUID(0,0),A)),List.of(new SpatialPincerUiState.Target(4,new UUID(1,1),new Point(Double.NaN,0,0)))))
            check(!SpatialPincerUiState.valid(frame(4,1,5,7,3,369,-1,false,invalid,Math.max(3,invalid.size()))), "empty oversized duplicate or corrupt target data fails closed");
        check(!SpatialPincerUiState.valid(frame(4,1,5,7,3,369,-1,false,null,8)), "missing target list fails closed");
        var partial=new SpatialPincerUiState.VisualState(); check(partial.accept(frame(1,1,1,1,1,400,-1,false),true), "partial preparation exists");
        check(!partial.accept(preview,true), "preview cannot skip second and third fresh marks");
        check(!state.accept(changed(projected,new SpatialPincerUiState.Controller(19,OWNER,70,5,1),A,projected.arrival(),projected.impact(),preview.targets(),8),true), "prior session epoch cannot publish");
        check(!state.accept(frame(3,1,5,7,3,369,-1,false,preview.targets(),8),true), "prior input cannot replace target receipt");
        state.cancel(71,4); check(state.active()==null && !state.accept(frame(4,1,6,7,3,368,-1,false,preview.targets(),8),true), "cancelled preview cannot be revived by late heartbeat");
        var expired=prepared(); check(expired.accept(preview,true), "preview starts inside original lease");
        for(int i=0;i<370;i++) expired.tick(false,true);
        check(expired.active()==null && !expired.accept(projected,true), "expired preview cannot confirm or emit");
        var border=new SpatialPincerUiState.Target(250,new UUID(341,250),new Point(30_000_000.01,65.125,30_000_000.02));
        var precise=frame(4,1,4,7,3,370,-1,false,List.of(border),3);
        check(SpatialPincerUiState.valid(precise) && precise.targets().get(0).endpoint().equals(border.endpoint()), "server endpoint keeps exact double coordinates at world border");
    }
    private static void review() {
        var gate = new SpatialPincerUiState.ReviewGate(); UUID nonce = new UUID(341,501), other = new UUID(341,502);
        Object chat = new Object(), newerChat = new Object(), inventory = new Object();
        check(!gate.allows(null,1), "incoming notice alone grants no popup authority");
        gate.begin(nonce,chat,1);
        check(gate.expects(nonce,1) && gate.allows(chat,1) && gate.allows(null,1), "explicit Review may replace only originating chat or newly empty world");
        check(!gate.allows(newerChat,1) && !gate.allows(inventory,1), "late Review cannot steal newer navigation");
        check(!gate.expects(other,1) && !gate.expects(nonce,2), "review is scoped to exact invitation and session");
        gate.cancel(other); check(gate.active(), "other invitation terminal leaves pending request intact");
        gate.cancel(nonce); check(!gate.active(), "expiry or matching end revokes review authority");
        check(SpatialPincerUiState.invitationDeadline(10,400,Long.MAX_VALUE)==210, "invite may never exceed200 ticks");
        check(SpatialPincerUiState.invitationDeadline(10,35,Long.MAX_VALUE)==45, "plan remainder shortens invite lifetime");
        check(SpatialPincerUiState.invitationDeadline(30,200,45)==45, "review refresh cannot renew acceptance window");
        var keys = new SpatialPincerUiState.Keys(); keys.blockHeld(257);
        check(!keys.press(257,true), "opening with Enter held cannot accept"); keys.release(257);
        check(!keys.press(257,false) && !keys.press(257,true), "typing or moving focus with held Enter cannot accept");
        keys.release(257); check(keys.press(257,true) && !keys.press(257,true), "only fresh focused Enter admits one action");
        check(SpatialPincerUiState.confirmedArrival("success","",true), "positive confirmed transport earns endpoint cue");
        check(!SpatialPincerUiState.confirmedArrival("end","",true) && !SpatialPincerUiState.confirmedArrival("success","transport_unconfirmed",true)
                && !SpatialPincerUiState.confirmedArrival("success","",false), "failure, generic end and nonmovement cannot manufacture arrival");
    }
    private static void geometry() {
        for (int width : new int[]{320,426,640,960}) for (int height : new int[]{180,240,360,540}) {
            var layout=SpatialPincerUiState.layout(width,height); var panel=layout.panel();
            check(panel.x()>=0 && panel.y()>=0 && panel.x()+panel.width()<=width && panel.y()+panel.height()<=height, "native panel fits supported scaled viewport");
            for(int i=0;i<4;i++) check(inside(layout.recipient(i),panel), "each named teammate remains mouse-addressable");
            check(inside(layout.button(0),panel) && inside(layout.button(1),panel), "both explicit consent decisions stay visible");
        }
        check(SpatialPincerMesh.MAX_MARKERS==3 && SpatialPincerMesh.MAX_TARGET_MARKERS==8 && SpatialPincerMesh.MAX_CUES==8
                && SpatialPincerMesh.MAX_DECOR_PRIMITIVES==185, "three coordinates, eight targets and eight cues have a fixed185 primitive maximum");
        check(SpatialPincerMesh.marker(A).size()==8 && SpatialPincerMesh.endpoint(A).size()==4, "planned and confirmed geometry bounded");
        check(SpatialPincerMesh.route(A,C).size()==1 && SpatialPincerMesh.route(A,A).isEmpty(), "single true A-to-C consent route has no fake intermediate arrival");
        Point relative=SpatialPincerMesh.relative(new Point(30_000_000.01,65,30_000_000.02),new Point(30_000_000,64,30_000_000));
        check(Math.abs(relative.x()-.01)<1e-8 && Math.abs(relative.z()-.02)<1e-8 && relative.y()==1, "double subtraction preserves centimetres at world border");
        for(int i=0;i<7;i++) check(SpatialPincerMesh.frame(i)==i && SpatialPincerMesh.alpha(i)>0, "exact original seven confirmation frames");
        check(SpatialPincerMesh.alpha(-1)==0 && SpatialPincerMesh.alpha(7)==0, "confirmed cue does not persist beyond its seven ticks");
    }
    private static boolean inside(SpatialPincerUiState.Rect a, SpatialPincerUiState.Rect b) {
        return a.x()>=b.x() && a.y()>=b.y() && a.x()+a.width()<=b.x()+b.width() && a.y()+a.height()<=b.y()+b.height();
    }
}
