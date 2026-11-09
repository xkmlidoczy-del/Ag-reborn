package cn.academy.port.client;

/** Executes the same single-use fence and bounded cosmetic state used by the live receiver. */
public final class BeamStepClientRegressionTest {
    private static int checks;
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static BeamStepReceiptState pending(double y) {
        var state = new BeamStepReceiptState();
        check(state.begin(7,3,100,y),"fresh input accepted"); return state;
    }
    private static boolean consume(BeamStepReceiptState s, long token, long tick, double y, boolean eligible) {
        return s.consume(7,3,token,tick,y,200,202,201,eligible);
    }
    public static void main(String[] args) {
        motion(); receipts(); plates(); feedback();
        System.out.println("PASS " + checks + " NEW232 pure receipt, native-gravity, one-shot and cosmetic-bound checks; GPU appearance not inferred");
    }
    private static void motion() {
        check(BeamStepReceiptState.assistedY(0)==.36,"stationary Y receives capped .36");
        check(Math.abs(BeamStepReceiptState.assistedY(-.4)-.32)<1e-12,"descending cast adds no more than .72");
        check(Math.abs(BeamStepReceiptState.assistedY(-2)+1.28)<1e-12,"fastfall remains descending instead of being reset");
        check(Double.isNaN(BeamStepReceiptState.assistedY(.5)),"unrelated faster ascent refuses instead of braking");
        check(Double.isNaN(BeamStepReceiptState.assistedY(Double.NaN)),"nonfinite motion rejected");
        check(Double.isNaN(BeamStepReceiptState.assistedY(-Double.MAX_VALUE)),"unrepresentable addition refuses instead of claiming success");
        for(double y:new double[]{-2,-1,-.9,-.4,0,.2,Math.nextDown(.36)}) {
            double result=BeamStepReceiptState.assistedY(y);
            check(result>y&&result<=.36&&result-y<=.72,"representable arithmetic respects strict addition and result caps");
        }
        for(double y:new double[]{-.001,-.2,-1,0,.15,.35}) {
            double next=((Math.abs(y)<.003?0:y)-.08)*(double).98F;
            double twice=((Math.abs(next)<.003?0:next)-.08)*(double).98F;
            check(BeamStepReceiptState.nativeY(y,next,1),"ordinary one-tick gravity accepted");
            check(BeamStepReceiptState.nativeY(y,twice,2),"ordinary two-tick gravity accepted");
            check(!BeamStepReceiptState.nativeY(y,next+.01,2),"new vertical impulse rejected");
        }
        check(!BeamStepReceiptState.nativeY(0,-.1,0),"changed Y before native tick rejected");
        check(!BeamStepReceiptState.nativeY(0,Double.POSITIVE_INFINITY,2),"nonfinite newer context rejected");
    }
    private static void receipts() {
        var s=pending(-.2);
        check(!s.consume(8,3,1,100,-.2,200,202,201,true),"different input does not consume pending input");
        check(!s.consume(7,2,1,100,-.2,200,202,201,true),"stale session rejected");
        check(s.pending(),"unrelated receipt cannot retire pending input");
        check(consume(s,1,100,-.2,true),"authenticated fresh input consumed");
        check(!consume(s,1,100,-.2,true),"duplicate receipt never adds twice");
        check(!s.begin(7,3,101,-.2),"same key identity cannot be rearmed");
        check(s.begin(8,3,101,-.2),"next fresh input arms");
        check(!s.consume(8,3,1,101,-.2,200,202,201,true),"spent token cannot cross new input");
        s=pending(-.2); check(!consume(s,1,103,-.2,true),"local third tick expires");
        check(!s.pending(),"expired paid receipt is retired");
        check(s.recordedMatches(7,3,103),"expired motion retains bounded owned-input explanation");
        check(!s.recordedMatches(8,3,103)&&!s.recordedMatches(7,4,103),"retained explanation refuses unrelated input or session");
        check(!s.recordedMatches(7,3,120),"retired input feedback expires by twentieth tick");
        s.forgetFeedback(); check(!s.recordedMatches(7,3,103),"world/category/preset/death clear retires explanatory identity");
        s=pending(-.2); check(!consume(s,1,100,-.1,true),"new vertical movement blocks addition");
        check(!consume(s,2,100,-.2,true),"changed context cannot recover old permission");
        s=pending(-.2); check(!consume(s,1,100,-.2,false),"preset/category/eligibility denial is paid no-op");
        check(!consume(s,2,100,-.2,true),"restored eligibility cannot revive rejected grant");
        s=pending(-.2); s.cancel(6,3); check(s.pending(),"older cancellation preserves newest tap");
        s.cancel(7,3); check(!consume(s,1,100,-.2,true),"matching cancellation fences delayed receipt");
        s=pending(-.2); s.cancel(); check(!s.begin(7,3,101,-.2),"same-connection world clear retains input floor");
        check(s.begin(1,4,101,-.2),"new owner epoch may restart input numbering");
        s.resetConnection(); check(s.begin(1,1,0,0),"only new connection clears replay floors");
        check(!BeamStepReceiptState.fresh(200,203,201,2),"extended server TTL rejected");
        check(!BeamStepReceiptState.fresh(200,202,203,2),"expired server TTL rejected");
        check(!BeamStepReceiptState.fresh(Long.MAX_VALUE,1,1,2),"timestamp overflow rejected");
    }
    private static void plates() {
        var state=new BeamStepPlateState(); var p=new BeamStepPlateState.Point(1,2,3);
        check(state.accept(1,2,p,100,120,100),"fresh cosmetic trace accepted");
        check(!state.accept(1,2,p,100,120,101),"replayed visual cannot refresh lifetime");
        check(state.accept(2,2,p,101,121,101)&&state.accept(3,2,p,102,122,102)
                &&state.accept(4,2,p,103,123,103),"later physical taps create bounded trail");
        check(state.plates().size()==3&&state.plates().getFirst().token()==2,"fourth plate evicts oldest only");
        for(int i=0;i<19;i++)state.tick(103,false);
        check(state.plates().size()==3,"local lease remains before twentieth tick");
        state.tick(103,false); check(state.plates().isEmpty(),"all traces disappear by local twentieth tick");
        check(!state.accept(4,2,p,103,123,104),"expired trace cannot reappear from replay");
        check(state.accept(5,2,p,200,220,219),"late but fresh trace accepted with only remaining age");
        state.tick(219,false); check(state.plates().isEmpty(),"late receipt cannot acquire new full lease");
        check(state.accept(6,2,p,200,220,200),"next token accepted");
        state.tick(220,true); check(state.plates().isEmpty(),"absolute server expiry also applies while paused");
        check(!state.accept(7,2,p,200,221,200),"unbounded cosmetic TTL rejected");
        check(!state.accept(7,2,new BeamStepPlateState.Point(Double.NaN,0,0),200,220,200),"nonfinite geometry rejected");
        check(state.accept(7,2,p,200,220,200),"valid receipt after malformed packet accepted");
        state.clear(); check(!state.accept(7,2,p,200,220,201),"clear keeps replay fence");
        state.terminal(8); check(!state.accept(8,2,p,200,220,201),"terminal-before-start cannot revive");
        check(BeamStepPlateState.alpha(20)==0&&BeamStepPlateState.alpha(Float.NaN)==0,"expired and invalid opacity is zero");
        check(BeamStepPlateState.alpha(19)<BeamStepPlateState.alpha(15),"last eight ticks visibly fade");
        var corners=BeamStepPlateState.corners(p);
        check(corners.size()==8&&corners.stream().allMatch(c->c.y()==p.y()),"thin chamfered plane has no slab thickness");
        check(corners.stream().allMatch(c->Math.abs(c.x()-p.x())<=.44000001&&Math.abs(c.z()-p.z())<=.27000001),"plate footprint remains restrained");
    }
    private static BeamStepFeedbackState.Visual visual(long token,long issued) {
        return new BeamStepFeedbackState.Visual(token,3,issued,issued+20,new BeamStepPlateState.Point(1,2,3));
    }
    private static void feedback() {
        var s=new BeamStepFeedbackState();
        var first=s.visual(visual(1,100),100);
        check(first.visual()==null&&first.notice()==BeamStepFeedbackState.Notice.NONE,"paid visual alone cannot look successful");
        var success=s.outcome(1,3,100,102,101,BeamStepFeedbackState.Outcome.APPLIED);
        check(success.visual()!=null&&success.visual().issued()==100&&success.visual().expires()==120,"visual-first joins real application with immutable lifetime");
        check(s.visual(visual(1,100),102).visual()==null,"duplicate visual cannot render twice");
        check(s.outcome(1,3,100,102,102,BeamStepFeedbackState.Outcome.PAID_EXPIRED).notice()==BeamStepFeedbackState.Notice.NONE,"terminal-after-success cannot invent paid failure");
        check(s.outcome(2,3,108,110,108,BeamStepFeedbackState.Outcome.APPLIED).visual()==null,"grant-first waits for geometry");
        check(s.visual(visual(2,108),109).visual()!=null,"grant-first later visual is not missed");
        check(s.outcome(3,3,116,118,119,BeamStepFeedbackState.Outcome.PAID_EXPIRED).notice()==BeamStepFeedbackState.Notice.PAID_EXPIRED,"owned late paid grant reports explicit expiry");
        check(s.visual(visual(3,116),119).visual()==null,"expired paid grant never displays a plate");
        check(s.outcome(3,3,116,118,119,BeamStepFeedbackState.Outcome.PAID_NOOP).notice()==BeamStepFeedbackState.Notice.NONE,"failure feedback once per token");
        s=new BeamStepFeedbackState(); s.visual(visual(1,100),100);
        check(s.outcome(1,3,100,102,101,BeamStepFeedbackState.Outcome.PAID_NOOP).notice()==BeamStepFeedbackState.Notice.PAID_NOOP,"visual-first rejected motion reports paid no-op");
        check(s.outcome(1,3,100,102,101,BeamStepFeedbackState.Outcome.APPLIED).visual()==null,"failed outcome is final and cannot revive on replay");
        check(s.outcome(2,3,108,111,108,BeamStepFeedbackState.Outcome.APPLIED).visual()==null&&s.size()==1,"malformed grant TTL creates no admission");
        check(s.outcome(2,3,108,110,108,BeamStepFeedbackState.Outcome.APPLIED).notice()==BeamStepFeedbackState.Notice.NONE,"valid outcome still accepted after malformed packet");
        check(s.visual(new BeamStepFeedbackState.Visual(2,4,108,128,visual(2,108).at()),109).visual()==null,"cross-epoch visual cannot join outcome");
        check(s.visual(visual(2,109),109).visual()==null,"changed original issue time cannot join outcome");
        check(s.visual(visual(2,108),109).visual()!=null,"correct immutable visual still joins after mismatch");
        s=new BeamStepFeedbackState();
        for(int i=1;i<=4;i++)s.visual(visual(i,100),100);
        check(s.size()==3,"pending visual and admitted outcome storage bounded to three");
        check(s.outcome(1,3,100,102,101,BeamStepFeedbackState.Outcome.APPLIED).visual()==null,"evicted token cannot revive");
        for(int i=0;i<20;i++)s.tick(100,false);
        check(s.size()==0,"quiet local lease removes all rendezvous entries within twenty ticks");
        check(s.visual(visual(4,100),101).visual()==null&&s.size()==0,"quiet-expired replay cannot reacquire storage");
        s=new BeamStepFeedbackState(); s.outcome(5,3,100,102,100,BeamStepFeedbackState.Outcome.APPLIED);
        check(s.visual(visual(5,100),120).visual()==null&&s.size()==0,"absolute original expiry wins over late geometry");
        s.visual(visual(6,200),200); s.clear();
        check(s.outcome(6,3,200,202,201,BeamStepFeedbackState.Outcome.APPLIED).visual()==null&&s.size()==0,"lifecycle clear keeps terminal replay fence");
        s=new BeamStepFeedbackState(); s.outcome(7,3,100,102,100,BeamStepFeedbackState.Outcome.APPLIED);
        for(int i=0;i<19;i++)s.tick(100,false);
        var late=s.visual(visual(7,100),100);
        check(late.visual()!=null&&late.visualAge()==19,"fresh delayed visual carries already-spent local lease");
        var latePlates=new BeamStepPlateState(); var lateVisual=late.visual();
        check(latePlates.acceptApplied(lateVisual.token(),lateVisual.epoch(),lateVisual.at(),lateVisual.issued(),lateVisual.expires(),100,late.visualAge()),"late admitted plate uses existing local age");
        latePlates.tick(100,false); check(latePlates.plates().isEmpty(),"rendezvous delay cannot extend cosmetic lifetime to forty ticks");
        s.tick(100,false); check(s.size()==0,"joining visual does not restart local twenty-tick lease");
        s=new BeamStepFeedbackState();
        s.outcome(9,3,100,102,100,BeamStepFeedbackState.Outcome.APPLIED);
        s.outcome(10,3,108,110,108,BeamStepFeedbackState.Outcome.APPLIED);
        var plates=new BeamStepPlateState();
        var newer=s.visual(visual(10,108),109).visual(); var older=s.visual(visual(9,100),109).visual();
        check(newer!=null&&older!=null,"out-of-order genuine outcomes both join their original visual");
        check(plates.acceptApplied(newer.token(),newer.epoch(),newer.at(),newer.issued(),newer.expires(),109)
                &&plates.acceptApplied(older.token(),older.epoch(),older.at(),older.issued(),older.expires(),109),"out-of-order admitted genuine plates are not suppressed by newer token");
    }

}
