package cn.academy.port.gametest;

import cn.academy.port.gametest.HighPowerCirculationReliefRuntimeTests.Completion;

/** Deterministic scheduler controls, not a replacement for the actual native batch run. */
public final class CirculationReliefCompletionRegressionTest {
    private static int checks;
    public static void main(String[] args) {
        for(boolean fail:new boolean[]{false,true}) {
            NativeTickModel old=new NativeTickModel();Throwable problem=new AssertionError("declared callback failure");
            old.finish(fail?problem:null);old.tick(() -> {});
            check(old.done && old.notifications==0,"direct external terminal reproduces missing native listener: "+fail);

            Completion queue=new Completion();NativeTickModel current=new NativeTickModel();
            if(fail)queue.fail(problem);else queue.succeed();
            check(queue.pending() && !current.done,"native callback queues without finishing outside GameTest tick");
            current.tick(() -> queue.flush(() -> current.finish(null),current::finish));
            check(current.done && current.notifications==1 && current.failure==(fail?problem:null),"own tick delivers exact success/failure once");
            current.tick(() -> queue.flush(() -> current.finish(null),current::finish));
            queue.flush(() -> {throw new AssertionError("duplicate success");},failure -> {throw new AssertionError("duplicate failure");});
            check(current.notifications==1,"duplicate pump cannot repeat batch completion");
        }
        Completion queue=new Completion();NativeTickModel current=new NativeTickModel();
        current.tick(() -> queue.flush(() -> current.finish(null),current::finish));
        check(!current.done && current.notifications==0,"empty own-tick pump does not complete early");
        Throwable first=new AssertionError("first"),second=new AssertionError("second");
        queue.succeed();queue.fail(first);queue.succeed();queue.fail(second);
        current.tick(() -> queue.flush(() -> current.finish(null),current::finish));
        check(current.failure==first && current.notifications==1,"callback exception overrides pending success and preserves original failure");
        queue.fail(second);queue.succeed();queue.flush(() -> {throw new AssertionError("late success");},failure -> {throw new AssertionError("late failure");});
        check(current.failure==first && current.notifications==1,"late terminals cannot revise delivered result");
        System.out.println("PASS CirculationReliefCompletionRegressionTest: "+checks+" checks; scheduler model only");
    }
    /** Exact relevant ordering from pinned GameTestInfo.tick:95–117 and GameTestTicker.tick:37–40. */
    private static final class NativeTickModel {
        boolean done;Throwable failure;int notifications;
        void finish(Throwable problem) {done=true;failure=problem;}
        void tick(Runnable ownTick) {if(!done) {ownTick.run();if(done)notifications++;}}
    }
    private static void check(boolean result,String why) {checks++;if(!result)throw new AssertionError(why);}
}
