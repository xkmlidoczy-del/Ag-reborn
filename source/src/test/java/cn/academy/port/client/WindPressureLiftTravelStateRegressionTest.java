package cn.academy.port.client;

/** Native witness lifetime negatives; no engine or synthetic movement claims. */
public final class WindPressureLiftTravelStateRegressionTest {
    private static int checks;
    private static final Object CLIENT=new Object(), WORLD=new Object(), PLAYER=new Object(), CONNECTION=new Object(), THREAD=new Object();
    private static final WindPressureLiftTravelState.Identity ID=new WindPressureLiftTravelState.Identity(CLIENT,WORLD,PLAYER,CONNECTION,1);
    private static void check(boolean value,String name){checks++;if(!value)throw new AssertionError(name);}
    public static void main(String[] args) {
        var state=new WindPressureLiftTravelState();
        check(state.beginStep(null,ID,THREAD)==null,"event without native run cannot open step");
        check(state.beginTravel(null,ID,THREAD,true)==null,"manual aiStep without native tick cannot open travel");
        var run=state.beginRun(ID,THREAD);check(run!=null,"native run opens opaque capability");
        check(state.plan(run,2,true),"actual native two-step plan recorded");
        var first=state.beginStep(run,ID,THREAD);check(first!=null&&first.index()==0,"first native step");
        check(state.beginTravel(first,ID,THREAD,false)==null,"fake event/manual call cannot steal native step");
        var a=state.beginTravel(first,ID,THREAD,true);check(a!=null&&state.owns(a,ID,THREAD),"actual native travel retires then owns one proposal");
        state.endTravel(a);check(!state.owns(a,ID,THREAD),"return closes travel capability");
        check(state.beginTravel(first,ID,THREAD,true)==null,"same native step cannot receive a second impulse");
        state.endStep(first);
        var second=state.beginStep(run,ID,THREAD);var b=state.beginTravel(second,ID,THREAD,true);
        check(b!=null&&second.index()==1&&b.serial()==a.serial()+1,"genuine second native step has distinct serial");
        state.endTravel(b);state.endStep(second);
        check(state.beginStep(run,ID,THREAD)==null,"extra third tick cannot exceed actual plan");
        state.endRun(run);check(!state.owns(b,ID,THREAD),"run finally closes all authority");
        check(!state.plan(run,1,true),"closed plan cannot reopen previous run");
        for(int n=3;n<=12;n++) {
            run=state.beginRun(ID,THREAD);check(state.plan(run,n,true)&&run.catchup(),"catch-up identified before first step "+n);
            check(state.beginStep(run,ID,THREAD)==null,"whole catch-up batch refuses first impulse "+n);state.endRun(run);
        }
        run=state.beginRun(ID,THREAD);state.plan(run,0,true);
        check(state.beginStep(run,ID,THREAD)==null,"zero native ticks grants nothing");state.endRun(run);
        run=state.beginRun(ID,THREAD);state.plan(run,2,false);
        check(run.plannedCount()==0&&state.beginStep(run,ID,THREAD)==null,"render-only invocation grants nothing");state.endRun(run);
        run=state.beginRun(ID,THREAD);check(!state.plan(run,-1,true),"invalid native timer refuses");state.endRun(run);
        run=state.beginRun(ID,THREAD);state.plan(run,1,true);
        check(!state.plan(run,2,true),"second timer observation cannot extend same run");state.endRun(run);
        run=state.beginRun(ID,THREAD);state.plan(run,1,true);
        check(state.beginStep(run,ID,new Object())==null,"different thread cannot use run");
        first=state.beginStep(run,ID,THREAD);a=state.beginTravel(first,ID,THREAD,true);
        check(a!=null,"foreign-thread attempt did not steal genuine step");
        var reloaded=new WindPressureLiftTravelState.Identity(CLIENT,WORLD,PLAYER,CONNECTION,2);
        check(!state.owns(a,reloaded,THREAD),"resource reload generation cannot commit");
        check(!state.owns(a,new WindPressureLiftTravelState.Identity(CLIENT,WORLD,new Object(),CONNECTION,1),THREAD),"replacement player cannot commit");
        check(!state.owns(a,new WindPressureLiftTravelState.Identity(CLIENT,new Object(),PLAYER,CONNECTION,1),THREAD),"replacement world cannot commit");
        check(!state.owns(a,new WindPressureLiftTravelState.Identity(CLIENT,WORLD,PLAYER,new Object(),1),THREAD),"replacement connection cannot commit");
        state.invalidate();check(!state.owns(a,ID,THREAD),"callback cancellation wins before final commit");state.endTravel(a);state.endStep(first);state.endRun(run);
        run=state.beginRun(ID,THREAD);state.plan(run,1,true);first=state.beginStep(run,ID,THREAD);a=state.beginTravel(first,ID,THREAD,true);
        check(state.beginTravel(first,ID,THREAD,true)==null&&!state.owns(a,ID,THREAD),"recursive certified travel invalidates outer proposal");state.endRun(run);
        run=state.beginRun(ID,THREAD);state.plan(run,1,true);first=state.beginStep(run,ID,THREAD);a=state.beginTravel(first,ID,THREAD,true);
        check(state.beginRun(ID,THREAD)==null&&!state.owns(a,ID,THREAD),"recursive run cannot replace outer capability");state.endRun(run);
        run=state.beginRun(ID,THREAD);state.plan(run,1,true);first=state.beginStep(run,ID,THREAD);a=state.beginTravel(first,ID,THREAD,true);
        state.endStep(first);check(!state.owns(a,ID,THREAD),"incomplete travel cleanup invalidates run");state.endRun(run);
        var prior=run;run=state.beginRun(ID,THREAD);state.plan(run,1,true);state.endRun(prior);
        first=state.beginStep(run,ID,THREAD);a=state.beginTravel(first,ID,THREAD,true);
        check(a!=null,"old finally cannot close new run");
        try { throw new IllegalStateException("native fixture exception"); }
        catch(IllegalStateException expected) { check(state.owns(a,ID,THREAD),"native exception does not manufacture a new token"); }
        finally {state.endTravel(a);state.endStep(first);state.endRun(run);}
        check(!state.owns(a,ID,THREAD),"finally closes exceptional native step");
        var other=new WindPressureLiftTravelState();
        check(other.beginTravel(first,ID,THREAD,true)==null,"capability from another state instance is not authority");
        check(!WindPressureLiftTravelState.sameIdentity(ID,new WindPressureLiftTravelState.Identity(CLIENT,WORLD,PLAYER,null,1)),"missing native connection fails closed");
        stackChecks();
        System.out.println("PASS WindPressureLiftTravelStateRegressionTest: "+checks+" checks; native hooks not exercised");
    }
    private static void stackChecks() {
        var a=new WindPressureLiftTravelState.Frame(PLAYER,"travel","(LVec3;)V");
        var b=new WindPressureLiftTravelState.Frame(WORLD,"aiStep","()V");
        var c=new WindPressureLiftTravelState.Frame(CLIENT,"nativeTickWrapper","()V");
        var expected=java.util.List.of(a,b,c);
        check(WindPressureLiftTravelState.stackPrefix(expected,expected),"exact declaring class and descriptor chain");
        check(WindPressureLiftTravelState.stackPrefix(java.util.List.of(a,b,c,new WindPressureLiftTravelState.Frame(THREAD,"run","()V")),expected),"outer application loop is outside bounded witness prefix");
        check(!WindPressureLiftTravelState.stackPrefix(java.util.List.of(a,c),expected),"missing native parent frame refused");
        check(!WindPressureLiftTravelState.stackPrefix(java.util.List.of(a,b,b,c),expected),"recursive native frame refused");
        check(!WindPressureLiftTravelState.stackPrefix(java.util.List.of(a,new WindPressureLiftTravelState.Frame(THREAD,"callback","()V"),b,c),expected),"event callback inserted in native chain refused");
        check(!WindPressureLiftTravelState.stackPrefix(java.util.List.of(new WindPressureLiftTravelState.Frame(new Object(),"travel","(LVec3;)V"),b,c),expected),"same class-name claim cannot replace declaring Class identity");
        check(!WindPressureLiftTravelState.stackPrefix(java.util.List.of(new WindPressureLiftTravelState.Frame(PLAYER,"travel","()V"),b,c),expected),"overloaded descriptor cannot impersonate native travel");
        check(!WindPressureLiftTravelState.stackPrefix(java.util.List.of(a,b,new WindPressureLiftTravelState.Frame(CLIENT,"fakeTickEvent","()V")),expected),"public event cannot impersonate final verified wrapper");
        check(!WindPressureLiftTravelState.stackPrefix(java.util.Collections.nCopies(49,a),expected),"unbounded/deep stack refused");
        check(!WindPressureLiftTravelState.stackPrefix(expected,java.util.List.of()),"missing expected final witness cannot match");
        check(!WindPressureLiftTravelState.stackPrefix(null,expected),"missing actual stack refused");
    }
}
