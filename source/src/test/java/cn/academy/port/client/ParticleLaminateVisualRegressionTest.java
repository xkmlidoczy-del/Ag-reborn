package cn.academy.port.client;

import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.core.FoldedMirrorGeometry;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import cn.academy.port.highpower.core.ParticleLaminateGeometry;
import cn.academy.port.highpower.core.ParticleLaminateGeometry.Panel;
import cn.academy.port.client.ParticleLaminateState.Frame;
import cn.academy.port.client.ParticleLaminateState.Identity;
import cn.academy.port.client.ParticleLaminateState.Segment;
import static cn.academy.port.client.ParticleLaminateState.*;
import static cn.academy.port.client.FoldedMirrorMesh.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Engine-free NEW241 provenance, actual ray gaps, pulse/fade phases and shared post-clip budgets. */
public final class ParticleLaminateVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(241, 1);
    private static final Vector ZERO = v(0,0,0), LOOK = v(0,0,1), EYE = v(0,64,0);
    private static final Panel A = new Panel(0,v(0,64,4),LOOK), B = new Panel(1,v(0,64,28),LOOK);
    private static final List<Panel> PANELS = List.of(A,B);
    private static Vector v(double x,double y,double z) { return new Vector(x,y,z); }
    private static void check(boolean condition,String message) { checks++; if(!condition)throw new AssertionError(message); }
    private static List<Segment> path(int firstStatus) {
        var nativeSegments=ParticleLaminateGeometry.segments(A,B).orElseThrow();
        List<Segment> result=new ArrayList<>();
        for(int i=0;i<3;i++) {
            var s=nativeSegments.get(i);
            result.add(new Segment(s.start(),i==0 && firstStatus==DAMAGED?s.start().add(s.end().subtract(s.start()).scale(.4)):s.end(),i==0?firstStatus:MISS));
        }
        return result;
    }
    private static Frame frame(long sequence,int phase,int remaining,int pulse,int age,List<Panel> panels,List<Segment> path) {
        return new Frame(new Identity(8,OWNER,2,3,10),sequence,phase,phase==FIRE?MISS:PREPARED,remaining,EYE,panels,
                phase==EDIT?B.center():ZERO,phase==EDIT?B.normal():ZERO,path,false,pulse,age);
    }
    private static Frame active(long seq,int remaining,int pulse,int age) { return frame(seq,ACTIVE,remaining,pulse,age,PANELS,path(MISS)); }
    private static Frame pulse(int index,int age,int remaining) { return frame(index+2,FIRE,remaining,index,age,PANELS,path(DAMAGED)); }
    private static Frame with(Frame f,Identity id,long sequence,List<Panel> panels,List<Segment> path,boolean lowMode) {
        return new Frame(id,sequence,f.phase(),f.status(),f.remainingTicks(),f.ownerFeet(),panels,f.ghostCenter(),f.ghostNormal(),path,lowMode,f.pulseIndex(),f.fieldAge());
    }
    public static void main(String[] args) throws Exception {
        geometry(); receipts(); replayBounds(); leases(); warnings(); meshes(); cullingAndBudget(); input(); deferredRelease(); integration();
        System.out.println("PASS ParticleLaminateVisualRegressionTest: "+checks+" checks");
    }
    private static void geometry() {
        Frame f=active(1,100,0,0);
        check(valid(f),"three real finite crossed rays form a valid active receipt");
        check(f.path().stream().map(Segment::start).distinct().count()==3,"all three rays preserve their different physical origins");
        check(f.path().get(0).start().x()<0 && f.path().get(0).end().x()>0,"upper corner actually crosses to the opposite lower corner");
        check(valid(frame(1,EDIT,195,0,0,List.of(A),path(MISS))),"second-point preview uses the same committed first panel and actual ghost orientation");
        Panel rotated=new Panel(1,B.center(),FoldedMirrorGeometry.unit(v(1,1,1)).orElseThrow());
        var oriented=ParticleLaminateGeometry.segments(A,rotated).orElseThrow();
        var orientedFrame=with(f,f.id(),2,List.of(A,rotated),oriented.stream().map(s -> new Segment(s.start(),s.end(),MISS)).toList(),false);
        check(valid(orientedFrame),"independent frozen panel normals determine each real segment endpoint");
        var wrong=new ArrayList<>(f.path());var first=wrong.getFirst();wrong.set(0,new Segment(first.start().add(v(.01,0,0)),first.end(),MISS));
        check(!valid(with(f,f.id(),2,PANELS,wrong,false)),"fabricated beam origin cannot be accepted");
        wrong=new ArrayList<>(f.path());wrong.set(1,wrong.getFirst());
        check(!valid(with(f,f.id(),2,PANELS,wrong,false)),"duplicate origins cannot replace the actual three segments");
        wrong=new ArrayList<>(f.path());wrong.set(0,new Segment(first.start(),first.end().add(v(0,.01,0)),MISS));
        check(!valid(with(f,f.id(),2,PANELS,wrong,false)),"beam endpoint outside its native line is refused");
        wrong=new ArrayList<>(f.path());wrong.set(0,new Segment(first.start(),first.end().add(first.end().subtract(first.start()).scale(.001)),MISS));
        check(!valid(with(f,f.id(),2,PANELS,wrong,false)),"native result cannot extend beyond the planned closed segment");
        check(!valid(frame(1,ACTIVE,100,0,0,List.of(A,new Panel(1,v(0,64,68),LOOK)),List.of())),"outer diagonal beyond64m is refused even when the center separation is64m");
        check(!valid(frame(1,ACTIVE,100,0,0,PANELS,path(DAMAGED))),"preview cannot label a hit as real");
        var p=pulse(1,6,94);wrong=new ArrayList<>(p.path());wrong.set(0,new Segment(first.start(),first.start(),BLOCKED));
        check(valid(with(p,p.id(),4,PANELS,wrong,false)),"initial containment at an exact zero-distance native blocker remains a valid receipt");
        check(!valid(with(f,new Identity(8,OWNER,0,3,10),2,PANELS,f.path(),false)),"missing epoch is rejected");
        check(!valid(frame(1,ACTIVE,101,0,0,PANELS,List.of())),"armed lease cannot exceed100ticks");
        check(eligible(4,true,true) && !eligible(3,true,true) && !eligible(4,false,true),"LV4 and the actual prerequisite gate the client");
    }
    private static void receipts() {
        ParticleLaminateState state=new ParticleLaminateState();
        Frame edit=frame(1,EDIT,40,0,0,List.of(),List.of());
        check(state.accept(edit,true),"fresh authenticated first hold opens one context");
        Frame anchor=frame(2,ACTIVE,200,0,0,List.of(A),List.of());
        check(state.accept(anchor,true),"first release starts the finite anchor lease");
        Frame second=new Frame(new Identity(8,OWNER,2,4,10),3,EDIT,PREPARED,190,EYE,List.of(A),B.center(),B.normal(),path(MISS),true,0,0);
        check(state.accept(second,true),"second hold may advance input while keeping the same context");
        Frame armed=with(active(4,100,0,0),second.id(),4,PANELS,path(MISS),true);
        check(state.accept(armed,true),"second release locks low mode and both panels");
        check(!state.accept(with(active(5,99,0,1),second.id(),5,PANELS,path(MISS),false),true),"post-release stance cannot alter captured low mode");
        var shifted=List.of(new Panel(0,A.center().add(v(.1,0,0)),A.normal()),B);
        check(!state.accept(with(armed,armed.id(),6,shifted,List.of(),true),true),"a fixed first panel never drifts after release");
        Frame fired=with(pulse(1,6,94),armed.id(),7,PANELS,path(DAMAGED),true);
        check(state.accept(fired,true),"genuine later pulse is accepted");
        check(!state.accept(fired,true),"identical sequence replay is rejected");
        check(!state.accept(with(active(8,95,0,5),armed.id(),8,PANELS,List.of(),true),true),"pulse and field clock cannot regress");
        Frame ended=new Frame(armed.id(),9,ENDED,PREPARED,0,EYE,List.of(),ZERO,ZERO,List.of(),true,1,7);
        check(state.accept(ended,true) && state.size()==0,"terminal receipt clears all panels while retaining pulse provenance");
        check(!state.accept(with(fired,fired.id(),10,PANELS,fired.path(),true),true),"terminal tombstone blocks later same-token replay");
        state.resetSession();check(state.accept(edit,true),"a different connection may reset session replay fences");
        state.clear();check(!state.accept(with(edit,edit.id(),12,List.of(),List.of(),false),true),"same-session clear retains replay tombstones");
        state.resetSession();state.cancel(2,3);check(!state.accept(edit,true),"local cancellation fences queued receipts before rendering");
    }
    private static void replayBounds() {
        ParticleLaminateState state=new ParticleLaminateState();Frame source=active(1,100,0,0);
        for(int i=1;i<=MAX_CONTEXTS;i++)check(state.accept(with(source,new Identity(i,new UUID(241,i),2,3,10),1,PANELS,List.of(),false),false),"bounded distinct owner accepted");
        check(!state.accept(with(source,new Identity(99,new UUID(241,99),2,3,10),1,PANELS,List.of(),false),false),"65th simultaneous context is refused");
        state.resetSession();
        for(int i=1;i<=MAX_WATERMARKS;i++) {
            var f=with(source,new Identity(i,new UUID(241,i),2,3,10),1,PANELS,List.of(),false);
            check(state.accept(f,false),"bounded owner watermark accepted");state.retire(f.id());
        }
        check(state.watermarkCount()==MAX_WATERMARKS && state.size()==0,"retired receipt storage is finite");
        check(!state.accept(with(source,new Identity(2000,new UUID(241,2000),2,3,10),1,PANELS,List.of(),false),false),"full watermark storage refuses new owners instead of forgetting old replays");
    }
    private static void leases() {
        Lease lease=new Lease();Frame setup=frame(1,EDIT,40,0,0,List.of(),List.of());lease.received(setup);
        for(int i=0;i<60;i++)check(lease.tick(true,true),"pausing freezes the finite hold lease");
        for(int i=0;i<QUIET_TICKS-1;i++)check(lease.tick(false,true),"heartbeat grace remains bounded");
        check(!lease.tick(false,true),"silent receipts expire after12active ticks");
        lease=new Lease();lease.received(active(1,100,0,0));
        for(int i=0;i<99;i++) {if(i%5==0)lease.received(active(i+2,100,0,0));check(lease.tick(false,true),"heartbeats cannot renew the field lifetime");}
        check(!lease.tick(false,true),"field ends by100active ticks despite repeated inflated receipts");
        lease=new Lease();lease.received(frame(1,ACTIVE,200,0,0,List.of(A),List.of()));
        for(int i=0;i<180;i++){if(i%5==0)lease.received(frame(i+2,ACTIVE,200-i,0,0,List.of(A),List.of()));check(lease.tick(false,true),"first anchor finite countdown");}
        lease.received(active(190,20,0,0));
        check(lease.remaining()==20 && lease.fieldAge()==0,"late second placement preserves anchor expiry and starts an independent field clock");
        check(recallAvailable(active(1,100,0,0),100,0) && !recallAvailable(frame(1,ACTIVE,200,0,0,List.of(A),List.of()),200,0),"only a live two-panel field opens free recall");
        check(!lease.tick(true,false),"pause never preserves an invalid owner or world");
    }
    private static void warnings() {
        Frame field=active(1,100,0,0);
        check(warning(field,100,0) && warning(field,95,5),"first panel edges warn for six ticks before any damaging pulse");
        check(!warning(field,94,6),"countdown never fabricates a pulse at the boundary");
        Frame fired=pulse(1,6,94);
        check(!warning(fired,90,10) && warning(fired,80,20) && warning(fired,75,25),"next warning follows the actual six-plus-twenty cadence");
        check(warning(active(1,20,0,0),20,0),"short anchor remainder does not shift initial warning timing");
        check(!warning(pulse(5,86,14),8,92),"fifth pulse cannot schedule a phantom sixth warning");
    }
    private static void meshes() {
        for(Panel panel:List.of(A,B,new Panel(0,v(3,64,5),FoldedMirrorGeometry.unit(v(1,2,3)).orElseThrow()))) {
            var basis=ParticleLaminateGeometry.frame(panel);var faces=ParticleLaminateMesh.panel(panel,false,false,0);
            check(faces.stream().filter(f -> f.kind()==FILL).count()==1,"each physical plane has one transparent original-artwork face");
            check(faces.stream().filter(f -> f.kind()==FILL).allMatch(f -> f.alpha()<255 && f.texture()==SHIELD_TEXTURE),"panel source alpha remains clear and uses original mdshield");
            for(var face:faces)for(Vector vertex:face.vertices()) {
                Vector local=vertex.subtract(panel.center());
                check(Math.abs(local.dot(basis.right()))<=.60000001 && Math.abs(local.dot(basis.up()))<=1.00000001 && Math.abs(local.dot(basis.normal()))<=.02000001,
                        "every panel surface/rim/side stays in its own thin oriented slab");
            }
            var ghost=ParticleLaminateMesh.panel(panel,true,false,0);
            check(ghost.size()<faces.size() && ghost.stream().allMatch(f -> f.kind()==GHOST),"ghost orientation stays hollow and visibly dashed");
        }
        var preview=ParticleLaminateMesh.path(active(1,100,0,0),EYE,LOOK,0);
        check(!preview.isEmpty() && preview.stream().allMatch(f -> f.kind()==PLANNED),"harmless dashed preview has no core, hit glint or filled corridor");
        var first=pulse(1,6,94);var solid=ParticleLaminateMesh.path(first,EYE,LOOK,0);
        check(solid.stream().anyMatch(f -> f.kind()==BEAM_CORE) && solid.stream().filter(f -> f.kind()==REFRACTION).count()==2,"one actual damaged segment gets exactly two tiny local hit strokes");
        Vector hit=first.path().getFirst().end();
        for(var face:solid)if(face.kind()==REFRACTION)for(Vector vertex:face.vertices())check(vertex.subtract(hit).length()<.07,"hit glints remain local to the native endpoint");
        var fade=ParticleLaminateMesh.path(first,EYE,LOOK,2);
        check(!fade.isEmpty() && fade.stream().allMatch(f -> f.kind()==BEAM_HALO),"after two ticks only the harmless bounded cosmetic halo remains");
        check(ParticleLaminateMesh.path(first,EYE,LOOK,6).isEmpty(),"all pulse geometry disappears by six ticks");
        for(var face:solid)if(face.kind()==BEAM_CORE || face.kind()==BEAM_EDGE) {
            check(FoldedMirrorMesh.culled(face) && !FoldedMirrorMesh.additive(face),"solid eight-sided beam walls use reviewed depth-tested alpha material");
            for(Vector vertex:face.vertices()) {
                boolean inside=false;
                for(var s:first.path()) {
                    Vector delta=s.end().subtract(s.start()),axis=delta.scale(1/delta.length()),relative=vertex.subtract(s.start());double along=relative.dot(axis);
                    if(along>=-1e-7 && along<=delta.length()+1e-7 && relative.subtract(axis.scale(along)).length()<=.0350001)inside=true;
                }
                check(inside,"solid ray vertices stay inside one real finite segment, preserving genuine gaps");
            }
        }
        check(ParticleLaminateMesh.SPAWNED_PARTICLES==0,"renderer has a hard zero spawned-particle entity budget");
    }
    private static void cullingAndBudget() {
        Panel far=new Panel(1,v(0,64,67.9),LOOK);var actual=ParticleLaminateGeometry.segments(A,far).orElseThrow();
        Frame frame=with(active(1,100,0,0),new Identity(8,OWNER,2,3,10),1,List.of(A,far),actual.stream().map(s -> new Segment(s.start(),s.end(),MISS)).toList(),false);
        Vector nearFarEnd=v(0,64,100);
        check(EYE.subtract(nearFarEnd).length()>64 && ParticleLaminateMesh.visible(frame,nearFarEnd),"a far endpoint remains visible even when its owner is more than64m away");
        check(!ParticleLaminateMesh.visible(frame,v(200,64,100)),"distant unrelated geometry is culled");
        List<FoldedMirrorMesh.Face> faces=new ArrayList<>();
        for(int i=0;i<8;i++){faces.addAll(ParticleLaminateMesh.panel(A,false,true,0));faces.addAll(ParticleLaminateMesh.panel(B,false,true,0));faces.addAll(ParticleLaminateMesh.path(pulse(1,6,94),EYE,LOOK,0));}
        var budget=new FoldedMirrorMesh.Budget();var ordered=FoldedMirrorMesh.ordered(faces,EYE,LOOK,budget);
        check(budget.used()==ParticleLaminateMesh.MAX_MESH_QUADS && ordered.size()==budget.used(),"one post-clip budget caps all contexts and textures at1536quads");
        check(ParticleLaminateMesh.MAX_VISIBLE_CONTEXTS==4,"only four contexts can reach mesh generation");
        int previous=-1;for(var face:ordered){check(FoldedMirrorMesh.layer(face)>=previous,"transparent panels precede halos, cores and local glints");previous=FoldedMirrorMesh.layer(face);}
    }
    private static void input() {
        var state=new ParticleLaminateInputState();var input=new HighPowerInput.Input(0,1,8,2);
        Object world=new Object(),player=new Object(),connection=new Object();
        var context=new ParticleLaminateInputState.DispatchContext(world,player,connection,1,0,2,"241");
        state.begin(input,71,false,context);check(state.release(input)==null,"same-tick key release waits for the native Post start");
        check(state.queuedFor(context) && !state.queuedFor(new ParticleLaminateInputState.DispatchContext(world,player,new Object(),1,0,2,"241")),"queued release retains exact world/player/connection provenance");
        var dispatch=state.flushQueuedStart();check(dispatch.start().equals(input) && dispatch.release().equals(input),"deferred input dispatch sends one start before one release");
        check(state.release(input)==null && state.flushQueuedStart()==null,"repeat release cannot replay a placement");
        state.accepted(1,2);check(state.permits(1,2),"native receipts retain released persistent ownership");
        check(state.cancel(input)==null && state.permits(1,2),"ordinary delegate disposal cannot revoke an already released field");
        check(state.cancelWhenBlocked(true).equals(input) && !state.permits(1,2),"focus/menu/preset invalidation clears field ownership");
    }
    private static ParticleLaminateInputState released(HighPowerInput.Input input, ParticleLaminateInputState.DispatchContext context) {
        var state=new ParticleLaminateInputState();state.begin(input,71,false,context);state.flushQueuedStart();
        check(state.release(input).equals(input),"authentic physical release is available to the Post queue");
        return state;
    }
    private static void deferredRelease() {
        Object world=new Object(),player=new Object(),connection=new Object();
        var context=new ParticleLaminateInputState.DispatchContext(world,player,connection,1,0,2,"241");
        var first=new HighPowerInput.Input(0,1,8,2);var second=new HighPowerInput.Input(0,2,8,2);
        var state=released(first,context);
        List<String> wire=new ArrayList<>();
        check(state.deferRelease(first,context) && wire.isEmpty(),"Pre release only stores identity and context; it emits no stale-stance packet");
        check(!state.deferRelease(first,context),"duplicate physical release cannot fill a second queue slot");
        wire.add("native crouch=true");wire.add("native aim=final");
        var dispatched=state.flushDeferredRelease(context);if(dispatched!=null)wire.add("laminate release "+dispatched.input());
        check(wire.equals(List.of("native crouch=true","native aim=final","laminate release 1")),"release-time crouch and final aim are delivered before the committing ability release");
        check(state.flushDeferredRelease(context)==null && !state.deferRelease(first,context),"flushing and re-deferring the same edge cannot replay a second placement");

        state=released(first,context);state.deferRelease(first,context);state.begin(second,71,false,context);
        check(state.release(second)==null,"new same-Post start retains its once-only queued release");
        wire.clear();wire.add("native input");
        var oldRelease=state.flushDeferredRelease(context);wire.add("release "+oldRelease.input());
        var next=state.flushQueuedStart();wire.add("start "+next.start().input());
        check(state.deferRelease(next.release(),context),"same-Post start/release uses the same deferred-release API");
        wire.add("release "+state.flushDeferredRelease(context).input());
        check(wire.equals(List.of("native input","release 1","start 2","release 2")),"older release flushes before the next start, and its same-Post release follows that start");

        state=released(first,context);state.deferRelease(first,context);state.begin(second,71,false,context);state.flushQueuedStart();state.release(second);
        check(!state.deferRelease(second,context),"one occupied slot cannot be silently overwritten by a later physical edge");
        check(state.flushDeferredRelease(context).equals(first) && state.deferRelease(second,context)
                && state.flushDeferredRelease(context).equals(second),"bounded overflow preserves the older edge and allows the newer edge only after consumption");

        List<ParticleLaminateInputState.DispatchContext> replacements=List.of(
                new ParticleLaminateInputState.DispatchContext(new Object(),player,connection,1,0,2,"241"),
                new ParticleLaminateInputState.DispatchContext(world,new Object(),connection,1,0,2,"241"),
                new ParticleLaminateInputState.DispatchContext(world,player,new Object(),1,0,2,"241"),
                new ParticleLaminateInputState.DispatchContext(world,player,connection,2,0,2,"241"),
                new ParticleLaminateInputState.DispatchContext(world,player,connection,1,1,2,"241"),
                new ParticleLaminateInputState.DispatchContext(world,player,connection,1,0,3,"241"),
                new ParticleLaminateInputState.DispatchContext(world,player,connection,1,0,2,"231"));
        for(var replacement:replacements) {
            state=released(first,context);state.deferRelease(first,context);
            check(state.flushDeferredRelease(replacement)==null && state.flushDeferredRelease(context)==null
                    && !state.deferRelease(first,context),"world/player/connection/session/preset/revision/mapping replacement consumes and rejects stale release");
        }
        state=released(first,context);check(!state.deferRelease(first,null),"release queue requires a captured dispatch context");
        check(!state.deferRelease(second,context),"a fabricated or unreleased input cannot enter the release queue");
        state.deferRelease(first,context);check(state.cancel(first)==null && state.flushDeferredRelease(context).equals(first),"ordinary delegate disposal after release cannot swallow its pending native Post dispatch");
        state=released(first,context);state.deferRelease(first,context);state.cancel();
        check(state.flushDeferredRelease(context)==null,"explicit cancellation clears deferred release");
        state=released(first,context);state.deferRelease(first,context);state.reset();
        check(state.flushDeferredRelease(context)==null,"session reset clears deferred release");
        state.begin(first,71,false,context);state.flushQueuedStart();state.release(first);
        check(state.deferRelease(first,context),"reset also permits a genuinely new session to reuse its input counter");
        state.ended(first.input(),first.epoch());check(state.flushDeferredRelease(context)==null,"terminal native receipt retires an unsent release");
        state=released(first,context);state.deferRelease(first,context);state.begin(new HighPowerInput.Input(0,1,8,3),71,false,context);
        check(state.flushDeferredRelease(context)==null,"new owner epoch cannot inherit the prior release slot");
    }
    private static void integration() throws Exception {
        String effects=Files.readString(Path.of("src/main/java/cn/academy/port/client/ParticleLaminateEffects.java"));
        check(effects.contains("ParticleLaminate.clientReceiver = ParticleLaminateEffects::receive"),"client setup installs the real authenticated receipt handler");
        check(effects.contains("capturedWorld") && effects.contains("capturedConnection") && effects.contains("capturedGeneration") && effects.contains("sameInputSessionEpoch"),"queued receipts fence world/player/connection/generation/session replacements");
        check(effects.contains("next.pulseIndex() > lastPulse"),"replayed same-pulse heartbeats cannot renew a flash");
        check(effects.contains("ParticleLaminateMesh.visible(m.frame,eye)") && !effects.contains("owner.distanceToSqr(camera)"),"production culling tests actual fixed corridor instead of owner location");
        check(effects.contains("FoldedMirrorMaterial.set(face)") && effects.contains("FoldedMirrorRenderTypes.type"),"reviewed scoped221 material is reused without changing global render state");
        check(!effects.contains("addParticle") && !effects.contains("new Particle(") && !effects.contains("hurt("),"effects allocate no native particles and never perform damage");
        check(effects.contains("modelView.popMatrix()") && effects.contains("shaderColor[3]") && effects.contains("buffers.endBatch()"),"all local draw-state changes restore through finally");
        String client=Files.readString(Path.of("src/main/java/cn/academy/port/client/AcademyClient.java"));
        String release=client.substring(client.indexOf("private static void releaseParticleLaminateInput("),client.indexOf("private static void flushParticleLaminateRelease("));
        check(release.contains("deferRelease(identity,particleLaminateDispatchContext") && !release.contains("request("),"production physical release does not send from ClientTick.Pre");
        check(client.contains("flushParticleLaminateRelease();flushParticleLaminateStart();flushParticleLaminateRelease();"),"production Post flush preserves older-release then next-start/release ordering after native movement");
    }
}
