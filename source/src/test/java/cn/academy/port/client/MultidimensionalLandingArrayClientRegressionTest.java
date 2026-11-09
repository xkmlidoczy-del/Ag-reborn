package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Pure NEW351 native-input ownership, immutable preview, actual contact provenance and bounded geometry. */
public final class MultidimensionalLandingArrayClientRegressionTest {
    private static final UUID OWNER = new UUID(351,1), NONE = new UUID(0,0);
    private static final Object WORLD = new Object(), PLAYER = new Object(), CONNECTION = new Object();
    private static final Point EYE = new Point(0,65.6,0);
    private static int checks;
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws java.io.IOException {
        inputEdges(); freeHold(); inputGuards(); immutablePlans(); physicalReceipts(); budgetsAndGeometry(); phasePanel(); labelAnnotations(); refusalReasonLocalization();
        System.out.println("MultidimensionalLandingArrayClientRegressionTest: "+checks+" pure checks passed; native render/input QA remains separate");
    }
    private static HighPowerInput.Input input(long n) { return new HighPowerInput.Input(0,n,19,71); }
    private static void refusalReasonLocalization() throws java.io.IOException {
        String prefix="academy.highpower.multidimensional_landing_array.";
        for(String locale:List.of("en_us","zh_cn")) {
            try(var stream=MultidimensionalLandingArrayClientRegressionTest.class.getResourceAsStream("/assets/academy/lang/"+locale+".json")) {
                check(stream!=null,"packaged "+locale+" translations are present");
                var language=com.google.gson.JsonParser.parseString(new String(stream.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
                for(String reason:List.of("short_release_required","no_target","surface_entity_occluded","first_surface_not_up")) {
                    String key=prefix+"reason."+reason;
                    check(language.has(key) && !language.get(key).getAsString().isBlank()
                            && !language.get(key).getAsString().equals(language.get(prefix+"refused").getAsString()),
                            "exact "+reason+" reason has a specific retry message in "+locale);
                }
                for(String key:List.of("free","fixed","title","shots","escrow","stop","status","preview","targets",
                        "confirm","hold","preview_status","plan","anchors","place","freeze","plan_status","clear"))
                    check(language.has(prefix+"panel."+key) && !language.get(prefix+"panel."+key).getAsString().isBlank(),
                            "compact phase panel has dedicated "+locale+" text for "+key);
                String status=String.format(language.get(prefix+"panel.status").getAsString(),"MODE",8,16,16,15,6,4);
                check(status.contains("MODE") && status.contains("8") && status.contains("16/16") && status.contains("15")
                        && status.contains("6") && status.contains("4"),"compact status retains mode, TTL, and every exact ledger counter in "+locale);
            }
        }
    }
    private static MultidimensionalLandingArrayInputState.Gates gates(boolean screen, boolean focus, boolean crouch, long revision) {
        return new MultidimensionalLandingArrayInputState.Gates(new MultidimensionalLandingArrayInputState.DispatchContext(
                WORLD,PLAYER,CONNECTION,1,19,71,0,revision,"array"),0,-100,true,true,true,true,true,true,
                screen,false,focus,true,false,false,crouch);
    }
    private static void tap(MultidimensionalLandingArrayInputState state, long n) {
        var g=gates(true,true,false,1);
        check(state.press(input(n),g),"fresh native press admitted"); state.release(input(n));
        var sent=new ArrayList<String>();
        check(state.flushStartPost(g,x->sent.add("start"),x->sent.add("abort")),"native start waits for Post");
        check(state.flushReleasePost(g,x->sent.add("release"),x->sent.add("abort")) && sent.equals(List.of("start","release")),
                "actual short release follows native start exactly once");
    }
    private static void inputEdges() {
        var state=new MultidimensionalLandingArrayInputState(); var g=gates(true,true,false,1);
        check(state.press(input(1),g),"clear remains reachable with cooldown/source flags false");
        check(!state.press(input(2),g) && state.queuedStart().equals(input(1)),"held key cannot overwrite the pending native edge");
        check(state.flushStartPost(g,x->{},x->{}) && !state.permitsReceipt(1,71,MultidimensionalLandingArrayMesh.MARKED),
                "held input cannot acknowledge a point");
        check(!state.permitsReceipt(1,71,MultidimensionalLandingArrayMesh.PREVIEW),"held input cannot acknowledge frozen targets");
        state.accepted(1,71,400,MultidimensionalLandingArrayMesh.MARKED,false);
        check(state.acceptedRemainingTicks()==0,"unreleased mark receipt cannot manufacture ownership");
        state.release(input(1)); check(state.flushReleasePost(g,x->{},x->{}),"actual key up sends release");
        check(!state.flushReleasePost(g,x->{throw new AssertionError("duplicate release");},x->{}),"release cannot repeat");
        state.accepted(1,71,400,MultidimensionalLandingArrayMesh.MARKED,false);
        for(int i=0;i<20;i++) state.tick(g,false,x->{throw new AssertionError("early expiry");});
        tap(state,2); state.accepted(2,71,399,MultidimensionalLandingArrayMesh.MARKED,false);
        check(state.acceptedRemainingTicks()==380,"later point cannot renew the original400-tick ownership deadline");
        for(int i=0;i<500;i++) state.tick(g,true,x->{throw new AssertionError("paused deadline");});
        check(state.acceptedRemainingTicks()==380,"pause freezes preparation ownership");
        for(int i=0;i<379;i++) state.tick(g,false,x->{});
        check(state.permits(2,71),"lease survives through last actual tick");
        state.tick(g,false,x->{}); check(state.current()==null && !state.permits(2,71),"real preparation expiry retires ownership once");
        var timeout=new MultidimensionalLandingArrayInputState(); check(timeout.press(input(1),g),"pending press created");
        timeout.flushStartPost(g,x->{},x->{}); var aborts=new ArrayList<HighPowerInput.Input>();
        for(int i=0;i<60;i++) timeout.tick(g,false,aborts::add);
        check(timeout.current()==null && aborts.equals(List.of(input(1))),"unacknowledged input has a bounded60-tick cleanup");
        check(!timeout.press(input(1),g),"expired input identity cannot replay");
    }
    private static void freeHold() {
        var state=new MultidimensionalLandingArrayInputState(); var g=gates(true,true,false,1);
        tap(state,1); state.accepted(1,71,400,MultidimensionalLandingArrayMesh.PREVIEW,false);
        check(state.press(input(2),g) && state.flushStartPost(g,x->{},x->{}),"fresh held confirmation reaches server");
        check(state.permitsReceipt(2,71,MultidimensionalLandingArrayMesh.ROUND),"held input may acknowledge server-admitted free round only");
        state.accepted(2,71,160,MultidimensionalLandingArrayMesh.ROUND,true);
        check(state.firing() && state.freeOrigin() && state.holding() && state.pendingCount()==0,
                "early held acknowledgement retains physical key ownership");
        state.release(input(2)); var sent=new ArrayList<String>();
        check(state.queuedRelease().equals(input(2)) && state.flushReleasePost(g,x->sent.add("release"),x->sent.add("abort")),
                "free-origin release survives pending acknowledgement removal");
        check(sent.equals(List.of("release")) && !state.flushReleasePost(g,x->{},x->{}),"free hold cancels with one native release");
        state.ended(2,71); check(state.current()==null,"confirmed free-round end cleans ownership");
        tap(state,3); state.accepted(3,71,120,MultidimensionalLandingArrayMesh.PREVIEW,false);
        tap(state,4); state.accepted(4,71,160,MultidimensionalLandingArrayMesh.ROUND,false);
        check(state.acceptedRemainingTicks()==240,"round ownership covers160 physical ticks and transport cleanup margin");
        check(!state.eligible(g),"ordinary extra starts cannot replace running fixed round");
        var clear=gates(true,true,true,1);
        check(state.press(input(5),clear),"fresh crouch clear remains reachable during fixed round");
        state.release(input(5)); state.flushStartPost(clear,x->{},x->{}); state.flushReleasePost(clear,x->{},x->{});
        state.rejectedGesture(5,71);
        check(state.permits(4,71) && state.firing(),"soft refusal clears only the newer gesture");
        for(int i=0;i<160;i++) state.tick(g,false,x->{throw new AssertionError("physical round ownership ended too soon");});
        check(state.permits(4,71) && state.acceptedRemainingTicks()==80,"receipt ownership survives the complete physical round");
        state.accepted(4,71,0,MultidimensionalLandingArrayMesh.IMPACT,false);
        check(state.acceptedRemainingTicks()==80,"late contact cannot renew round ownership");
        for(int i=0;i<79;i++) state.tick(g,false,x->{});
        check(state.permits(4,71),"ownership lasts until bounded240th tick");
        state.tick(g,false,x->{}); check(state.current()==null,"conservative lease still expires deterministically");
    }
    private static void inputGuards() {
        for(var invalid:List.of(gates(false,true,false,1),gates(true,false,false,1),gates(true,true,false,2))) {
            var state=new MultidimensionalLandingArrayInputState(); tap(state,1); state.accepted(1,71,400,1,false);
            var aborted=new ArrayList<HighPowerInput.Input>(); state.tick(invalid,true,aborted::add);
            check(state.current()==null && aborted.equals(List.of(input(1))),"screen/focus/revision change cancels immediately even while paused");
        }
        var state=new MultidimensionalLandingArrayInputState(); var g=gates(true,true,false,1);
        check(state.press(input(Long.MAX_VALUE),g),"largest legal input identity is representable");
        state.cancelBeforeClear(x->{});
        check(!state.press(input(Long.MAX_VALUE),g) && !state.press(input(Long.MIN_VALUE),g),"overflow/replay cannot restart a retired native identity");
        state.reset(); check(state.press(input(1),g),"new genuine input session resets highwater");
        state.release(input(1));
        var changed=new MultidimensionalLandingArrayInputState.DispatchContext(new Object(),PLAYER,CONNECTION,1,19,71,0,1,"array");
        var differentWorld=new MultidimensionalLandingArrayInputState.Gates(changed,0,-100,true,true,true,true,true,true,true,false,true,true,true,true,false);
        var sent=new ArrayList<String>();
        check(!state.flushStartPost(differentWorld,x->sent.add("start"),x->sent.add("abort")) && sent.equals(List.of("abort")),
                "deferred edge cannot cross a world boundary");
    }
    private static List<Point> points(int count) {
        var points=new ArrayList<Point>(); for(int i=0;i<count;i++) points.add(new Point(3+i*2,64,0)); return points;
    }
    private static List<MultidimensionalLandingArrayMesh.Target> targets(int count) {
        var targets=new ArrayList<MultidimensionalLandingArrayMesh.Target>();
        for(int i=0;i<count;i++) targets.add(new MultidimensionalLandingArrayMesh.Target(new UUID(351,201+i),201+i,new Point(18+i*.25,65.2,3+i*.25),true));
        return targets;
    }
    private static MultidimensionalLandingArrayMesh.Frame frame(long input,long sequence,int phase,List<Point> points,
            List<MultidimensionalLandingArrayMesh.Target> targets,int remaining,int shot,boolean accepted) {
        int total=MultidimensionalLandingArrayMesh.requiredShots(targets.size());
        return new MultidimensionalLandingArrayMesh.Frame(new MultidimensionalLandingArrayMesh.Identity(OWNER,71,input,1),sequence,phase,
                points,remaining,targets,total,shot<0?0:shot+1,shot<0?0:shot+1,phase==7||shot>=0?6:0,0,false,
                shot<0?EYE:MultidimensionalLandingArrayMesh.origin(points.get(shot%6)),shot<0?EYE:targets.get(shot%targets.size()).position(),
                shot<0?NONE:new UUID(351,400+shot),shot<0?-1:shot%6,shot,accepted,phase==5||phase==8,"");
    }
    private static MultidimensionalLandingArrayMesh.Frame copy(MultidimensionalLandingArrayMesh.Frame f,long sequence,int phase,
            List<Point> points,List<MultidimensionalLandingArrayMesh.Target> targets,int remaining,Point source,Point impact,UUID medium,
            int spent,int published,boolean accepted,boolean terminal) {
        return new MultidimensionalLandingArrayMesh.Frame(f.id(),sequence,phase,points,remaining,targets,f.shotsTotal(),spent,published,
                f.retained(),f.pending(),f.freeOrigin(),source,impact,medium,f.slot(),f.shotIndex(),accepted,terminal,f.reason());
    }
    private static MultidimensionalLandingArrayMesh.VisualState prepared(int targets) {
        var state=new MultidimensionalLandingArrayMesh.VisualState();
        for(int i=1;i<=6;i++) check(state.accept(frame(i,i,1,points(i),List.of(),410-i*10,-1,true),true),"six releases create six immutable anchors in order");
        if(targets>0) check(state.accept(frame(7,7,2,points(6),targets(targets),330,-1,true),true),"seventh release freezes server targets");
        return state;
    }
    private static void immutablePlans() {
        var state=new MultidimensionalLandingArrayMesh.VisualState(); var first=frame(1,1,1,points(1),List.of(),400,-1,true);
        check(state.accept(first,true) && state.markersVisible(),"first private authoritative point is visible");
        check(!state.accept(first,true) && !new MultidimensionalLandingArrayMesh.VisualState().accept(first,false),"replayed and other-owner private plans fail closed");
        for(int i=0;i<31;i++) check(state.tick(false,true)==null,"missing heartbeat cannot invent server cancellation");
        check(state.active()!=null && !state.markersVisible() && state.remainingTicks()==369,"silence hides visuals and preserves real400-tick preparation");
        check(state.accept(frame(1,2,1,points(1),List.of(),390,-1,true),true) && state.remainingTicks()==369,"heartbeat restores display without renewing immutable lifetime");
        check(!state.accept(frame(2,3,1,points(3),List.of(),380,-1,true),true),"one native input cannot skip an anchor");
        for(int i=0;i<100;i++) state.tick(true,true);
        check(state.remainingTicks()==369,"pause freezes visual preparation");
        for(int i=0;i<369;i++) state.tick(false,true);
        check(state.active()==null && !state.accept(frame(2,3,1,points(2),List.of(),380,-1,true),true),"expired preparation cannot be revived by a late packet");
        var preview=frame(7,7,2,points(6),targets(16),330,-1,true); state=prepared(16);
        var moved=new ArrayList<>(preview.targets()); var target=moved.getFirst();
        moved.set(0,new MultidimensionalLandingArrayMesh.Target(target.uuid(),target.entity(),new Point(target.position().x()+.001,target.position().y(),target.position().z()),true));
        check(!state.accept(copy(preview,8,2,preview.points(),moved,320,preview.source(),preview.impact(),NONE,0,0,true,false),true),"frozen target cannot track a millimetre of entity movement");
        var unavailable=new ArrayList<>(preview.targets()); unavailable.set(0,new MultidimensionalLandingArrayMesh.Target(target.uuid(),target.entity(),target.position(),false));
        check(state.accept(copy(preview,8,2,preview.points(),unavailable,320,preview.source(),preview.impact(),NONE,0,0,true,false),true),"server can remove target availability without changing its frozen coordinate");
        check(!state.accept(copy(preview,9,2,preview.points(),preview.targets(),310,preview.source(),preview.impact(),NONE,0,0,true,false),true),"unavailable target cannot silently rejoin a frozen round");
        var changedPoints=new ArrayList<>(preview.points()); changedPoints.set(0,new Point(3.001,64,0));
        check(!state.accept(copy(preview,9,2,changedPoints,unavailable,310,preview.source(),preview.impact(),NONE,0,0,true,false),true),"frozen anchor cannot shift after preview");
        check(!state.accept(frame(8,9,1,points(6),List.of(),310,-1,true),true),"marked heartbeat cannot discard frozen targets");
        var mutablePoints=points(6); var mutableTargets=targets(16); var frozen=frame(7,7,2,mutablePoints,mutableTargets,330,-1,true);
        mutablePoints.clear(); mutableTargets.clear();
        check(frozen.points().size()==6 && frozen.targets().size()==16,"caller mutations cannot rewrite a receipt snapshot");
        boolean immutable=false; try { frozen.targets().clear(); } catch(UnsupportedOperationException expected) { immutable=true; }
        check(immutable,"public frozen snapshot is immutable");
        var old=state.active();
        var refusal=new MultidimensionalLandingArrayMesh.Frame(new MultidimensionalLandingArrayMesh.Identity(OWNER,71,8,1),9,6,
                old.points(),0,old.targets(),old.shotsTotal(),0,0,0,0,false,EYE,EYE,NONE,-1,-1,false,false,"cooldown");
        check(state.accept(refusal,true) && state.active()==old,"soft refusal preserves the exact confirmed preview");
        check(!state.accept(refusal,true),"soft-refusal replay cannot re-acknowledge a newer input");
        check(state.accept(copy(old,10,2,old.points(),old.targets(),310,old.source(),old.impact(),NONE,0,0,true,false),true),"older plan input resumes heartbeat after refused newer gesture");
        state.cancel(71,8); check(state.active()==null && !state.accept(preview,true),"explicit native cancellation retires delayed preview");
    }
    private static void physicalReceipts() {
        var state=prepared(16); var start=frame(8,8,7,points(6),targets(16),160,-1,true);
        check(state.accept(start,true) && state.firing(),"eighth fresh release starts one fixed round");
        var unseen=frame(8,9,4,points(6),targets(16),159,0,true);
        check(!state.accept(unseen,true),"unseen contact cannot invent a published needle");
        long sequence=9;
        for(int i=0;i<16;i++) {
            var publication=frame(8,sequence++,3,points(6),targets(16),160-i*4,i,true);
            check(state.accept(publication,true),"each actual publication is bounded by shot index and reused original slot");
            check(!state.accept(publication,true),"publication replay cannot create an extra cue");
            var impact=frame(8,sequence++,4,points(6),targets(16),159-i*4,i,i%2==0);
            Point actual=new Point(impact.impact().x()+.01,impact.impact().y(),impact.impact().z());
            check(state.accept(copy(impact,impact.sequence(),4,impact.points(),impact.targets(),impact.remaining(),impact.source(),actual,
                    impact.medium(),impact.spent(),impact.published(),impact.accepted(),false),true),"actual native contact may differ from the intended endpoint");
            check(!state.accept(copy(impact,sequence,4,impact.points(),impact.targets(),impact.remaining(),impact.source(),actual,
                    impact.medium(),impact.spent(),impact.published(),true,false),true),"duplicate contact cannot upgrade a rejected hurt into success");
        }
        check(state.inFlight()==0,"six retained originals support up to16 unique published shots without inventing bodies");
        check(!state.accept(frame(9,sequence,7,points(6),targets(16),90,-1,true),true),"different input cannot replace the active round");
        var active=state.active();
        var end=copy(active,sequence,5,active.points(),active.targets(),0,active.source(),active.impact(),NONE,16,16,false,true);
        end=new MultidimensionalLandingArrayMesh.Frame(end.id(),end.sequence(),end.phase(),end.points(),0,end.targets(),16,16,16,0,0,false,
                end.source(),end.impact(),NONE,-1,-1,false,true,"");
        check(state.accept(end,true) && state.active()==null,"explicit end is cleanup and never a hit");
        check(!state.accept(frame(8,sequence+1,4,points(6),targets(16),0,15,true),true),"late contact cannot revive a retired round");
        state=prepared(16); check(state.accept(start,true),"new independent state starts a round");
        for(int i=0;i<6;i++) check(state.accept(frame(8,9+i,3,points(6),targets(16),150-i,i,true),true),"at most six physical originals can be in flight");
        check(state.inFlight()==6 && !state.accept(frame(8,15,3,points(6),targets(16),143,6,true),true),"seventh publication cannot reuse a still-active slot");
        for(int i=0;i<160;i++) check(state.tick(false,true)==null,"visual silence cannot cancel a live physical round");
        check(state.active()!=null && !state.markersVisible(),"physical round display ends without prematurely revoking transport ownership");
        for(int i=0;i<79;i++) state.tick(false,true);
        check(state.tick(false,true)!=null && state.active()==null,"conservative visual ownership expires at240 without new server TTL");
        state=prepared(1); check(state.tick(true,false)!=null && state.active()==null,"authentic owner loss cleans visuals even while paused");
    }
    private static void budgetsAndGeometry() {
        for(int count=1;count<=16;count++) {
            int total=0; for(int i=0;i<count;i++) total+=MultidimensionalLandingArrayMesh.targetShares(count,i);
            check(total==Math.max(6,count),"preview allocation accounts for exactly N=max(6,T) original shots");
        }
        check(MultidimensionalLandingArrayMesh.requiredShots(0)==0 && MultidimensionalLandingArrayMesh.requiredShots(17)==0,
                "empty and oversized target previews fail closed");
        check(MultidimensionalLandingArrayMesh.MAX_MARKERS==6 && MultidimensionalLandingArrayMesh.MAX_TARGET_MARKERS==16
                && MultidimensionalLandingArrayMesh.MAX_CUES==6 && MultidimensionalLandingArrayMesh.MAX_DECOR_PRIMITIVES==400,
                "private coordinate and actual receipt primitives have one exact bounded maximum");
        Point at=new Point(30_000_000.01,64.125,30_000_000.02), camera=new Point(30_000_000,64,30_000_000);
        Point relative=MultidimensionalLandingArrayMesh.relative(at,camera);
        check(Math.abs(relative.x()-.01)<1e-8 && Math.abs(relative.z()-.02)<1e-8 && relative.y()==.125,
                "double camera-relative subtraction preserves centimetres near the world border");
        check(MultidimensionalLandingArrayMesh.marker(at).size()==8 && MultidimensionalLandingArrayMesh.contact(at).size()==4,
                "only short local coordinate/contact strokes are generated");
        check(SafeLandingProbeMesh.biped(MultidimensionalLandingArrayMesh.markBounds(at)).size()==36,"coordinate mark retains original classic biped UV geometry");
        check(MultidimensionalLandingArrayMesh.anchorAlpha(false)==115 && MultidimensionalLandingArrayMesh.anchorAlpha(true)==170
                && 124/255.0*MultidimensionalLandingArrayMesh.anchorAlpha(true)/255.0<.35,
                "original alpha-124 anchor material gains phase contrast while remaining below35 percent peak opacity");
        check(MultidimensionalLandingArrayMesh.inside(at,MultidimensionalLandingArrayMesh.markBounds(at)),"inside-camera check suppresses hologram faces");
        check(MultidimensionalLandingArrayMesh.marker(new Point(Double.NaN,0,0)).isEmpty()
                && MultidimensionalLandingArrayMesh.relative(new Point(Double.MAX_VALUE,0,0),camera)==null,"invalid geometry and overflow fail closed");
        for(int i=0;i<7;i++) check(MultidimensionalLandingArrayMesh.frame(i)==i && MultidimensionalLandingArrayMesh.alpha(i)>0,
                "actual publication/contact uses only original seven frames");
        check(MultidimensionalLandingArrayMesh.alpha(-1)==0 && MultidimensionalLandingArrayMesh.alpha(7)==0,"actual cue has no lingering eighth frame");
        check(MultidimensionalLandingArrayMesh.envelopeAlpha(-1)==0 && MultidimensionalLandingArrayMesh.envelopeAlpha(7)==0,
                "soft material envelope never extends authentic seven-tick cue lifetime");
        for(int i=0;i<7;i++) check(MultidimensionalLandingArrayMesh.envelopeAlpha(i)>0
                && MultidimensionalLandingArrayMesh.envelopeAlpha(i)<MultidimensionalLandingArrayMesh.alpha(i),
                "original glow material has bounded subordinate opacity at every original atlas frame");
        check(MultidimensionalLandingArrayMesh.ATLAS_HALF_WIDTH==.009 && MultidimensionalLandingArrayMesh.ENVELOPE_HALF_WIDTH==.024,
                "atlas detail keeps its original width inside one bounded soft edge envelope");
        var glyph=MultidimensionalLandingArrayMesh.contact(at);
        check(Math.abs(glyph.getFirst().from().x()-(at.x()-.16))<1e-9
                && glyph.getFirst().from().y()==at.y()+.02,"material layering keeps authoritative contact center and radius");
        check(MultidimensionalLandingArrayMesh.labelAnchor(at).equals(new Point(at.x(),at.y()+.35,at.z())),
                "target and receipt labels share the same reference plane without moving geometry");
        for(int i=0;i<6;i++) {
            int target=MultidimensionalLandingArrayMesh.labelOffset(false,i,9), receipt=MultidimensionalLandingArrayMesh.labelOffset(true,i,9);
            check(target+9<receipt,"target and confirmed contact use deterministic separated billboard rows");
            if(i>0) check(target+9<MultidimensionalLandingArrayMesh.labelOffset(false,i-1,9)
                    && receipt>MultidimensionalLandingArrayMesh.labelOffset(true,i-1,9)+9,"nearby ordered labels have a readable inter-row gap");
        }
        check(MultidimensionalLandingArrayMesh.nearbyLabel(at,new Point(at.x()+.01,at.y(),at.z()))
                && !MultidimensionalLandingArrayMesh.nearbyLabel(at,new Point(at.x()+1,at.y(),at.z())),
                "small actual-contact offsets keep the receipt in a separate local label lane");
        check(!MultidimensionalLandingArrayMesh.valid(frame(7,7,2,points(6),targets(17),330,-1,true)),"seventeenth target is rejected before rendering");
        var publication=frame(8,9,3,points(6),targets(1),160,0,true);
        check(!MultidimensionalLandingArrayMesh.valid(copy(publication,9,3,publication.points(),publication.targets(),160,
                publication.source(),publication.impact(),publication.medium(),1,1,false,false)),"failed publication cannot earn actual success cosmetics");
        check(!MultidimensionalLandingArrayMesh.valid(copy(publication,9,3,publication.points(),publication.targets(),160,
                new Point(3.001,65.2,0),publication.impact(),publication.medium(),1,1,true,false)),"fixed publication names the exact anchor origin");
        var bad=new MultidimensionalLandingArrayMesh.Frame(publication.id(),Long.MIN_VALUE,3,publication.points(),160,publication.targets(),6,1,1,6,0,
                false,publication.source(),publication.impact(),publication.medium(),0,0,true,false,"");
        check(!MultidimensionalLandingArrayMesh.valid(bad),"sequence overflow cannot admit a replay");
    }
    private static void phasePanel() {
        for(int width:new int[]{320,393,426,640,960}) for(int height:new int[]{240,271,360,540}) {
            var cpOrigin=ClassicHudTimeline.cpOrigin(width,-12,12);
            var cp=new cn.academy.port.highpower.HighPowerRecoveryHudModel.Box((int)Math.floor(cpOrigin.x()),12,194,40);
            var keyOrigin=ClassicHudTimeline.keyOrigin(width,height,0,30);
            var keys=new cn.academy.port.highpower.HighPowerRecoveryHudModel.Box((int)keyOrigin.x()-155,(int)keyOrigin.y(),185,88);
            var notification=new cn.academy.port.highpower.HighPowerRecoveryHudModel.Box(0,15,130,43);
            var chat=new cn.academy.port.highpower.HighPowerRecoveryHudModel.Box(0,height-130,Math.min(width,336),102);
            var reserved=new ArrayList<>(List.of(cp,keys,notification,chat));
            var recovery=cn.academy.port.highpower.HighPowerRecoveryHudModel.layout(width,height,cp,keys,notification,chat);
            if(recovery!=null) reserved.add(recovery);
            var emergency=EmergencyReturnHudLayout.fit(width,height,cp,keys,notification,chat,recovery);
            if(emergency!=null) reserved.add(emergency);
            for(var rows:List.of(List.of(120,130,160,142),List.of(120,175,155,145,142))) {
                var layout=MultidimensionalLandingArrayHudLayout.fit(width,height,rows,372,9,reserved);
                check(layout!=null,"default viewport including 320x240 always retains full exact status");
                check(layout.scale()>=.8f && layout.bounds().within(width,height),"localized phase panel stays readable and fully inside viewport");
                check(reserved.stream().noneMatch(layout.bounds()::intersects),"phase panel respects CP, recovery, key, notification, chat, and emergency instruments");
                check(!layout.bounds().intersects(new cn.academy.port.highpower.HighPowerRecoveryHudModel.Box(width/2-20,height/2-20,40,40))
                        && !layout.bounds().intersects(new cn.academy.port.highpower.HighPowerRecoveryHudModel.Box(Math.max(0,width/2-110),height-64,220,64)),
                        "phase panel preserves crosshair, status icons, and hotbar");
                int rawWidth=layout.compact()?372:rows.stream().mapToInt(Integer::intValue).max().orElseThrow();
                check(rawWidth*(double)layout.scale()<=layout.bounds().width()-(layout.compact()?4:8),
                        "every complete measured row fits; no clipped or abbreviated counters");
            }
        }
        var blocked=List.of(new cn.academy.port.highpower.HighPowerRecoveryHudModel.Box(0,0,320,240));
        check(MultidimensionalLandingArrayHudLayout.fit(320,240,List.of(120,140),300,9,blocked)==null,
                "fully occupied custom HUD fails explicitly instead of drawing over other instruments");
        check(MultidimensionalLandingArrayHudLayout.fit(320,240,List.of(120),Integer.MAX_VALUE,9,blocked)==null,
                "unrepresentably wide localized status does not overflow into a false layout");
    }
    private static void labelAnnotations() {
        check(MultidimensionalLandingArrayMesh.labelAlpha(.6)==0 && MultidimensionalLandingArrayMesh.labelAlpha(.6001)==0
                && MultidimensionalLandingArrayMesh.labelAlpha(Double.NaN)==0,
                "eye-plane annotations hide and tiny alpha never becomes opaque through legacy font normalization");
        check(MultidimensionalLandingArrayMesh.labelAlpha(1)<MultidimensionalLandingArrayMesh.labelAlpha(1.5)
                && MultidimensionalLandingArrayMesh.labelAlpha(2)==230 && MultidimensionalLandingArrayMesh.labelAlpha(96)==230,
                "NEW351 labels fade monotonically through the near two metres without affecting glyph geometry");
        for(int guiHeight:new int[]{240,407,720}) for(double projectionY:new double[]{.7,1.4,2.2})
            for(double depth:new double[]{.7,1,2,4,16,96}) for(int width:new int[]{12,46}) {
                float scale=MultidimensionalLandingArrayMesh.labelScale(depth,depth,width,9,projectionY,guiHeight);
                double screenHeight=scale*9*projectionY*guiHeight/(2*depth);
                check(scale>0 && screenHeight<=MultidimensionalLandingArrayMesh.MAX_LABEL_HEIGHT+.00001,
                        "compact identity labels have a seven-GUI-pixel height cap across distance, FOV, and GUI scale");
            }
        check(MultidimensionalLandingArrayMesh.labelScale(.5,1,12,9,1.4,240)==0
                && MultidimensionalLandingArrayMesh.labelScale(2,Double.NaN,12,9,1.4,240)==0
                && MultidimensionalLandingArrayMesh.labelScale(2,2,12,9,0,240)==0,
                "invalid or eye-adjacent label transforms fail closed");
        var occupied=new ArrayList<MultidimensionalLandingArrayMesh.LabelBounds>();
        var contact=new MultidimensionalLandingArrayMesh.LabelBounds(145,90,175,99);
        check(MultidimensionalLandingArrayMesh.admitLabel(occupied,contact,320,240),"accepted contact claims projected annotation space first");
        for(int i=0;i<6;i++) check(!MultidimensionalLandingArrayMesh.admitLabel(occupied,
                new MultidimensionalLandingArrayMesh.LabelBounds(150+i*.1,91+i*.1,170+i*.1,98+i*.1),320,240),
                "six collinear lower-priority anchor labels cannot cover confirmed contact text");
        check(occupied.size()==1 && MultidimensionalLandingArrayMesh.admitLabel(occupied,
                new MultidimensionalLandingArrayMesh.LabelBounds(180,90,195,99),320,240),
                "projection suppression keeps separated anchor identity without merging or reallocating coordinates");
        check(!MultidimensionalLandingArrayMesh.admitLabel(occupied,new MultidimensionalLandingArrayMesh.LabelBounds(10,20,30,10),320,240)
                && !MultidimensionalLandingArrayMesh.admitLabel(occupied,new MultidimensionalLandingArrayMesh.LabelBounds(Double.NaN,10,20,20),320,240)
                && !MultidimensionalLandingArrayMesh.admitLabel(occupied,new MultidimensionalLandingArrayMesh.LabelBounds(-1,10,20,20),320,240),
                "nonfinite, inverted, and clipped annotation rectangles cannot claim visible text space");
        occupied.clear();
        for(int i=0;i<MultidimensionalLandingArrayMesh.MAX_LABELS;i++) check(MultidimensionalLandingArrayMesh.admitLabel(occupied,
                new MultidimensionalLandingArrayMesh.LabelBounds(i*10,0,i*10+5,5),640,240),"annotation occupancy is bounded by actual six anchors,16targets,six receipts");
        check(!MultidimensionalLandingArrayMesh.admitLabel(occupied,new MultidimensionalLandingArrayMesh.LabelBounds(400,0,405,5),640,240),
                "annotation layout cannot grow beyond the existing private geometry budget");
    }
}
