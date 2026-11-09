package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Box;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.List;
import java.util.UUID;

/** Authoritative phases, immutable anchor, retirement/heartbeat fences, and world-border precision. */
public final class ReturnMarkVisualRegressionTest {
    private static int checks;
    private static final UUID OWNER = new UUID(322, 1), OBSERVER_OWNER = new UUID(322, 2);
    private static final Point SOURCE = new Point(0,64,0), DEST = new Point(12,66,0);
    private static final Box BOX = new Box(11.7,66,-.3,12.3,67.8,.3);
    private static void check(boolean value, String message) { checks++; if (!value) throw new AssertionError(message); }
    private static ReturnMarkState.Frame frame(UUID owner, long input, long token, long sequence, int phase, int remaining) {
        return new ReturnMarkState.Frame(new ReturnMarkState.Identity(19,owner,71,input,token),sequence,phase,
                phase==ReturnMarkState.MARKED?DEST:SOURCE,DEST,BOX,phase==ReturnMarkState.SUCCESS,"",remaining);
    }
    private static ReturnMarkState.Frame changed(ReturnMarkState.Frame f, Point source, Point destination, Box bounds, boolean moved) {
        return new ReturnMarkState.Frame(f.id(),f.sequence(),f.phase(),source,destination,bounds,moved,f.reason(),f.remainingTicks());
    }
    public static void main(String[] args) {
        var state=new ReturnMarkState();var mark=frame(OWNER,1,1,1,0,1200);
        check(state.accept(mark,true)&&state.active().equals(mark),"only server-marked anchor appears");
        check(!state.accept(mark,true),"duplicate sequence cannot replay mark");
        check(!state.accept(frame(OWNER,1,2,2,0,1200),true),"same physical input cannot reopen a new token");
        check(!state.accept(frame(OWNER,1,1,2,0,1201),true),"lifetime cannot exceed 1200 ticks");
        for(int i=1;i<=70;i++){
            check(state.tick(false,true)==null,"walking owner with healthy heartbeat keeps anchor");
            if(i%10==0)check(state.accept(frame(OWNER,1,1,1+i/10,0,1200-i),true),"ten-tick authoritative heartbeat admitted");
        }
        check(state.remainingTicks()==1130,"heartbeats do not restart lifetime");
        var movedMark=frame(OWNER,1,1,10,0,1129);
        check(!state.accept(changed(movedMark,DEST,new Point(13,66,0),new Box(12.7,66,-.3,13.3,67.8,.3),false),true),
                "mark heartbeat cannot drag its frozen destination");
        var warning=frame(OWNER,2,1,10,1,4);
        check(state.accept(warning,true)&&state.active().phase()==1,"new authenticated return input advances same token to warning");
        check(!state.accept(frame(OWNER,1,1,11,0,1129),true),"delayed original-input heartbeat cannot rewind warning");
        for(int i=0;i<4;i++)check(state.tick(false,true)==null,"cosmetic countdown never predicts success or loses pending final receipt");
        check(state.warningAge()==4&&state.remainingTicks()==0&&state.active()!=null,"four-tick pulse ends while waiting for real server move");
        var success=frame(OWNER,2,1,11,2,0);
        check(!state.accept(changed(success,SOURCE,DEST,BOX,false),true),"moved=false cannot animate success");
        check(state.accept(success,true)&&state.active()==null,"confirmed success consumes mark");
        check(!state.accept(frame(OWNER,2,1,12,1,4),true)&&!state.accept(success,true),"retired controller cannot reanimate");
        check(state.accept(frame(OWNER,3,2,1,0,1200),true),"new press and token can mark again");
        check(state.accept(frame(OWNER,4,2,2,4,0),true)&&state.active()==null,"refusal consumes anchor with no arrival");
        check(!state.accept(frame(OWNER,4,2,3,2,0),true),"late success after refusal stays retired");
        state.cancel(71,5);check(!state.accept(frame(OWNER,5,3,1,0,1200),true),"local cancellation fences late first acknowledgement");
        check(state.accept(frame(OWNER,6,4,1,0,1200),true),"next uncancelled input can establish mark");
        state.clear();check(!state.accept(frame(OWNER,6,4,2,0,1190),true),"world/preset clearing retires old token");
        state.resetSession();check(state.accept(mark,true),"verified new connection resets replay provenance");
        var quiet=new ReturnMarkState();quiet.accept(mark,true);
        for(int i=0;i<29;i++)check(quiet.tick(false,true)==null,"heartbeat gap has bounded grace");
        check(quiet.tick(false,true)==null&&quiet.active()!=null&&!quiet.markerVisible(),
                "missing heartbeat hides cosmetic at30 ticks without cancelling authenticated mark");
        for(int i=0;i<70;i++)check(quiet.tick(false,true)==null,"long server gap cannot issue destructive cancellation");
        check(quiet.remainingTicks()==1100&&!quiet.markerVisible(),"hidden marker still spends its existing1200tick lifetime");
        check(quiet.accept(frame(OWNER,1,1,2,0,1180),true)&&quiet.markerVisible()&&quiet.remainingTicks()==1100,
                "new matching heartbeat restores marker without extending remaining lifetime");
        check(!quiet.accept(frame(OWNER,1,1,1,0,1200),true),"old heartbeat sequence cannot replay or renew mark");
        for(int i=0;i<1099;i++)check(quiet.tick(false,true)==null,"stale visual ownership lasts only through original lease");
        check(quiet.tick(false,true)!=null&&quiet.active()==null&&!quiet.markerVisible(),"heartbeat gap cannot extend1200tick expiry");
        check(!quiet.accept(frame(OWNER,1,1,3,0,10),true),"server heartbeat after local lease expiry cannot revive retired token");
        var changedSession=new ReturnMarkState();changedSession.accept(mark,true);
        for(int i=0;i<35;i++)changedSession.tick(false,true);
        check(changedSession.tick(false,false)!=null&&changedSession.active()==null,
                "stale hidden mark still clears immediately on world or control invalidity");
        check(!changedSession.accept(frame(OWNER,1,1,2,0,1160),true),"old-session delayed heartbeat stays retired");
        var nextEpoch=new ReturnMarkState.Frame(new ReturnMarkState.Identity(19,OWNER,72,1,1),1,0,DEST,DEST,BOX,false,"",1200);
        check(changedSession.accept(nextEpoch,true)&&!changedSession.accept(frame(OWNER,2,2,3,0,1000),true),
                "new authenticated epoch admits its mark and rejects previous-session replay");
        var paused=new ReturnMarkState();paused.accept(mark,true);
        for(int i=0;i<80;i++)paused.tick(true,true);
        check(paused.remainingTicks()==1200&&paused.tick(true,false)!=null,"pause freezes lifetime but invalid owner still clears");
        var shortLife=new ReturnMarkState();shortLife.accept(frame(OWNER,1,1,1,0,1),true);
        check(shortLife.tick(false,true)!=null,"server's final lifetime tick expires exactly once");
        var observer=new ReturnMarkState();observer.accept(mark,true);
        check(!observer.accept(frame(OBSERVER_OWNER,1,1,1,0,1200),false),"remote persistent anchor stays private");
        check(observer.accept(frame(OBSERVER_OWNER,2,1,2,1,4),false)&&observer.active().equals(mark),
                "observer warning gets a replay fence without replacing local anchor");
        check(observer.accept(frame(OBSERVER_OWNER,2,1,3,4,0),false)&&observer.active().equals(mark),
                "matching observer refusal retires warning without affecting private mark");
        check(!observer.accept(frame(OBSERVER_OWNER,2,1,4,2,0),false),"observer refusal cannot turn into a late arrival");
        check(!observer.accept(frame(new UUID(322,3),1,1,1,3,0),false),"unseen observer terminal cannot allocate cosmetic state");
        check(observer.accept(frame(OBSERVER_OWNER,3,2,1,2,0),false),"bounded confirmed observer success remains admissible");
        check(!ReturnMarkState.valid(changed(mark,new Point(Double.NaN,64,0),DEST,BOX,false)),"NaN receipt rejected");
        double outside = cn.academy.port.highpower.core.ReturnMarkRules.RANGE + 1;
        check(!ReturnMarkState.valid(changed(success,SOURCE,new Point(outside,64,0),new Box(outside-.3,64,-.3,outside+.3,65.8,.3),true)),"overrange arrival rejected");
        check(!ReturnMarkState.valid(changed(warning,SOURCE,DEST,BOX,true)),"warning can never claim movement");
        var full=new ReturnMarkState();
        for(int i=1;i<=ReturnMarkState.MAX_WATERMARKS;i++)check(full.accept(frame(new UUID(322,i),1,1,1,2,0),false),"bounded owner receipt accepted");
        check(!full.accept(frame(new UUID(322,2000),1,1,1,2,0),false)&&full.watermarkCount()==1024,
                "full replay table never evicts stale-owner fences");
        for(double world:new double[]{0,25_000_000,-25_000_000,29_999_000,-29_999_000}){
            Point camera=new Point(world+.0625,64,world+.125),endpoint=new Point(world+.2425,65.8,world+.305);
            Point relative=ReturnMarkState.relative(endpoint,camera);
            check(Math.abs((float)relative.x()-.18)<1e-6&&Math.abs((float)relative.z()-.18)<1e-6,
                    "camera subtraction before float keeps 18cm corners at world border");
            if(Math.abs(world)>1_000_000)check(Math.abs(((float)endpoint.x()-(float)camera.x())-.18)>.1,
                    "mutation witness: absolute float subtraction destroys border corner precision");
        }
        Box departure=PhaseSidestepMesh.sourceBounds(BOX,SOURCE,DEST);
        for(Box b:List.of(departure,BOX)){
            check(PhaseSidestepMesh.corners(b).size()==24,"full-body marker has24 exact corner strokes");
            check(PhaseSidestepMesh.biped(b).size()==36,"success uses six original atlas body pieces");
            for(var q:PhaseSidestepMesh.biped(b))for(var v:List.of(q.a(),q.b(),q.c(),q.d()))
                check(v.position().finite()&&v.u()>=0&&v.u()<=1&&v.v()>=0&&v.v()<=1,"native atlas mesh stays finite and bounded");
        }
        for(int age=0;age<7;age++)check(PhaseSidestepMesh.frame(age)==age&&PhaseSidestepMesh.particles(SOURCE,DEST,age).size()<=32,
                "seven original receipt-only frames and at most32 sparks");
        check(PhaseSidestepMesh.alpha(7)==0&&PhaseSidestepMesh.particles(SOURCE,DEST,7).isEmpty(),"no lingering arrival or portal");
        System.out.println("ReturnMarkVisualRegressionTest: "+checks+" checks passed");
    }
}
