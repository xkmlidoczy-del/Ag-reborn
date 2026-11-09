package cn.academy.port.client;

import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Receipt authentication, immutable leases, truthful queue progress, bounded decor and border precision. */
public final class SpatialRelayVisualRegressionTest {
    private static final UUID OWNER=new UUID(343,1);
    private static final Point A=new Point(0,65,0), B=new Point(8,65,0), C=new Point(8,65,8), DROP=new Point(1,65,0);
    private static int checks;
    private static void check(boolean value,String message){checks++;if(!value)throw new AssertionError(message);}
    private static SpatialRelayState.Frame frame(long input,long sequence,int phase,int life,int points,int eligible,int queued,int completed,
                                                  List<SpatialRelayState.Leg> planned,SpatialRelayState.Leg confirmed,String reason){
        return new SpatialRelayState.Frame(new SpatialRelayState.Identity(19,OWNER,71,input,1),sequence,phase,life,
                points>=1?A:null,points>=2?B:null,points>=3?C:null,eligible,queued,completed,planned,confirmed,reason);
    }
    private static SpatialRelayState.Frame mark(long input,long sequence,int life,int points){
        return frame(input,sequence,SpatialRelayState.MARKED,life,points,1,0,0,List.of(),null,"");
    }
    public static void main(String[] args){
        var state=new SpatialRelayState();var first=mark(1,1,400,1);
        check(state.accept(first,true)&&state.markerVisible(),"first authenticated mark admitted");
        check(!state.accept(first,true),"duplicate receipt rejected");
        for(int i=0;i<45;i++)state.tick(true,true);
        check(state.remainingTicks()==400&&state.markerVisible(),"pause freezes both clocks");
        for(int i=0;i<31;i++)check(state.tick(false,true)==null,"stale visuals cannot cancel server queue");
        check(!state.markerVisible()&&state.active()!=null&&state.remainingTicks()==369,"stale plan hides without destructive cancellation");
        check(state.accept(mark(1,2,380,1),true)&&state.remainingTicks()==369,"heartbeat cannot renew local immutable clock");
        check(!state.accept(mark(1,3,381,1),true),"server lifetime increase rejected");
        check(!state.accept(mark(1,3,375,2),true),"mark addition requires new genuine native gesture");
        check(state.accept(mark(2,3,375,2),true)&&state.remainingTicks()==369,"B addition retains original lifetime");
        check(!state.accept(mark(3,4,374,2),true),"heartbeat cannot acknowledge unrelated next gesture");
        check(state.accept(mark(3,4,370,3),true),"third release captures C");
        var gather=new SpatialRelayState.Leg(42,DROP,A);var ab=new SpatialRelayState.Leg(42,A,B);var bc=new SpatialRelayState.Leg(42,B,C);
        var mutable=new ArrayList<>(List.of(gather,ab,bc));
        var queued=frame(4,5,SpatialRelayState.QUEUED,365,3,1,1,0,mutable,null,"");mutable.clear();
        check(queued.planned().size()==3,"frozen receipt has immutable route snapshot");
        check(state.accept(queued,true)&&state.queued()&&state.active().confirmed()==null,"queued preview never invents arrival");
        check(!SpatialRelayState.valid(frame(4,6,SpatialRelayState.QUEUED,364,3,1,1,0,List.of(ab),gather,"")),"preview cannot carry confirmed movement");
        check(state.accept(frame(4,6,SpatialRelayState.MOVED,364,3,1,1,1,List.of(ab,bc),gather,""),true),"only actual positive movement confirms first leg");
        check(!state.accept(frame(4,7,SpatialRelayState.MOVED,363,3,1,1,1,List.of(ab,bc),gather,""),true),"replayed move with newer sequence cannot duplicate arrival");
        check(state.accept(frame(4,7,SpatialRelayState.QUEUED,363,3,1,1,1,List.of(ab,bc),null,""),true),"next heartbeat can retain queue without repeating moved cue");
        var busy=frame(5,8,SpatialRelayState.REFUSED,362,3,1,1,1,List.of(ab,bc),null,"queue_running");
        check(state.acceptQueueRefusal(busy)&&state.active().id().input()==4&&state.queued(),"busy new gesture never replaces queue nonce");
        check(!state.acceptQueueRefusal(busy),"transient refusal still advances replay sequence fence");
        check(state.accept(frame(4,9,SpatialRelayState.MOVED,361,3,1,1,2,List.of(bc),ab,""),true),"original queue continues after newer gesture refusal");
        check(state.accept(frame(4,10,SpatialRelayState.MOVED,360,3,1,0,3,List.of(),bc,""),true),"final actual move admitted without predicting queue success");
        check(state.accept(frame(4,11,SpatialRelayState.ENDED,0,0,0,0,3,List.of(),null,"completed"),true)&&state.active()==null,"completion retires all marks and paths");
        var finalMove=frame(4,10,SpatialRelayState.MOVED,360,3,1,0,3,List.of(),bc,"");
        var complete=frame(4,11,SpatialRelayState.ENDED,0,0,0,0,3,List.of(),null,"completed");
        check(SpatialRelayState.preservesConfirmedCue(complete,finalMove),"clean final end retains only existing confirmed cue age");
        check(!SpatialRelayState.preservesConfirmedCue(frame(4,11,SpatialRelayState.ENDED,0,0,0,0,3,List.of(),null,"cleared"),finalMove),"explicit clear removes final cue immediately");
        check(!SpatialRelayState.preservesConfirmedCue(frame(4,11,SpatialRelayState.REFUSED,0,0,0,0,3,List.of(),null,"completed"),finalMove),"refusal cannot pretend to be clean completion");
        check(!SpatialRelayState.preservesConfirmedCue(frame(5,11,SpatialRelayState.ENDED,0,0,0,0,3,List.of(),null,"completed"),finalMove),"different gesture cannot retain prior cue");
        check(!SpatialRelayState.preservesConfirmedCue(frame(4,11,SpatialRelayState.ENDED,0,0,0,0,4,List.of(),null,"completed"),finalMove),"different completed count cannot invent final arrival");
        check(!state.accept(mark(4,12,300,3),true),"terminal queue cannot revive marks");
        check(!state.accept(frame(4,12,SpatialRelayState.MOVED,359,3,1,0,4,List.of(),bc,""),true),"terminal queue cannot restart final cue");
        state.resetSession();check(state.accept(first,true),"new verified session resets fences");state.cancel(71,1);
        check(!state.accept(mark(1,2,399,1),true),"cancel fences delayed receipt");
        state.resetSession();state.accept(first,true);state.clear();check(!state.accept(mark(1,2,399,1),true),"context clear retains replay fence");
        var observer=new SpatialRelayState();check(!observer.accept(first,false)&&!observer.accept(queued,false),"private marks and planned routes remain owner-only");
        check(observer.accept(frame(4,6,SpatialRelayState.MOVED,364,3,1,1,1,List.of(ab,bc),gather,""),false),"observer sees actual confirmed native item move");
        check(SpatialRelayState.valid(frame(4,6,SpatialRelayState.MOVED,364,3,1,1,1,List.of(),new SpatialRelayState.Leg(42,A,new Point(A.x()+1e-9,A.y(),A.z())),"")),"tiny positive native move remains truthful");
        check(!SpatialRelayState.valid(frame(1,1,SpatialRelayState.MARKED,SpatialRelayState.MARK_LIFE_TICKS + 1,1,0,0,0,List.of(),null,"")),"overlong mark lease rejected");
        check(!SpatialRelayState.valid(frame(4,6,SpatialRelayState.MOVED,364,3,1,1,1,List.of(),new SpatialRelayState.Leg(42,A,A),"")),"zero-displacement leg has no fake arrival");
        check(!SpatialRelayState.valid(frame(1,1,SpatialRelayState.MARKED,400,1,17,0,0,List.of(),null,"")),"seventeenth eligible handle refused");
        var expiring=new SpatialRelayState();expiring.accept(mark(1,1,1,1),true);check(expiring.tick(false,true)!=null&&!expiring.markerVisible(),"exact mark expiry retires local ownership");
        var interrupted=new SpatialRelayState();interrupted.accept(first,true);check(interrupted.tick(true,false)!=null,"pause does not suppress explicit ownership loss");
        geometry();
        System.out.println("SpatialRelayVisualRegressionTest: "+checks+" checks passed; no native-rendered acceptance claim");
    }
    private static void geometry(){
        check(SpatialRelayMesh.MAX_PLANNED_STROKES+SpatialRelayMesh.CONFIRMED_STROKES+SpatialRelayMesh.CONFIRMED_QUADS<=64,"all global decorative primitives fit fixed64 budget");
        check(SpatialRelayMesh.MAX_MARKERS==3,"exactly three anchor markers maximum");
        var routes=new ArrayList<SpatialRelayState.Leg>();
        for(int i=0;i<16;i++){routes.add(new SpatialRelayState.Leg(i,new Point(i*.1,65,1),A));routes.add(new SpatialRelayState.Leg(i,A,B));routes.add(new SpatialRelayState.Leg(i,B,C));}
        var dashed=SpatialRelayMesh.planned(routes);check(dashed.size()<=48&&dashed.size()>=18,"shared segments deduplicate without hiding any gathered route");
        for(var line:dashed)check(line.from().finite()&&line.to().finite()&&line.from().distanceSquared(line.to())>0,"planned path is finite and dashed");
        check(SpatialRelayMesh.endpoint(A).size()==4&&SpatialRelayMesh.marker(A).size()==4,"small fixed-topology mark and endpoint cues");
        for(int age=0;age<7;age++)check(SpatialRelayMesh.frame(age)==age&&SpatialRelayMesh.alpha(age)>0,"seven original frames only");
        check(SpatialRelayMesh.alpha(7)==0&&SpatialRelayMesh.alpha(-1)==0,"confirmed decor ends at seven ticks");
        var relative=SpatialRelayMesh.relative(new Point(30_000_000.01,65,30_000_000.02),new Point(30_000_000,64,30_000_000));
        check(Math.abs(relative.x()-.01)<1e-8&&Math.abs(relative.z()-.02)<1e-8&&relative.y()==1,"camera subtraction preserves border centimetres in double precision");
    }
}
