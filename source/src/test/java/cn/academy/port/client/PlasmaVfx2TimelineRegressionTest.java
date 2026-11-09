package cn.academy.port.client;

import cn.academy.port.client.vfx2.PlasmaVfx2Timeline;
import cn.academy.port.client.vfx2.PlasmaVfx2Timeline.*;

/** Optional visual timing and allocation contracts; no claim of native pixel acceptance. */
public final class PlasmaVfx2TimelineRegressionTest {
    private static int checks;
    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
    private static void near(double actual, double expected, String message) {
        check(Math.abs(actual - expected) < 1E-6, message + ": " + actual + " != " + expected);
    }

    private static void acceptedStages() {
        State state = new State(45.5);
        check(state.snapshot().stage() == Stage.CHARGE, "accepted start charges");
        state.advance(2_275, false);
        near(state.snapshot().charge01(), 1, "captured fractional charge threshold");
        check(state.snapshot().stage() == Stage.CHARGE, "age cannot fabricate ready cue or launch");
        check(state.acceptedReady(), "accepted ready cue starts once");
        check(!state.acceptedReady(), "duplicate ready cannot reset pulse");
        state.advance(250, false);
        near(state.snapshot().readyPulse(), .5, "one-second ready pulse");
        check(state.snapshot().stage() == Stage.READY_CUE, "free hold remains ready cue");
        check(state.acceptedGo(), "accepted GO starts launch");
        check(!state.acceptedReady(), "late ready cannot return launch to charge");
        state.advance(150, false);
        near(state.snapshot().launchEnvelope(), 1, "release peak at three ticks");
        near(state.snapshot().launch01(), .5, "release midpoint");
        check(!state.acceptedGo(), "duplicate GO cannot restart release");
        state.advance(149, false);
        check(state.snapshot().stage() == Stage.LAUNCH, "release remains live before six ticks");
        state.advance(1, false);
        check(state.snapshot().stage() == Stage.TRAVEL, "release ends at six ticks");
        near(state.snapshot().launchEnvelope(), 0, "no release pulse after six ticks");
        check(state.acceptedEnd(), "accepted END closes presentation");
        check(!state.acceptedEnd() && !state.acceptedGo() && !state.acceptedReady(), "terminal fence");
        check(state.snapshot().ended() && state.snapshot().stage() == Stage.END, "neutral terminal state");
        near(state.snapshot().readyPulse() + state.snapshot().launchEnvelope(), 0, "end emits no launch/impact cue");

        State earlyCue = new State(45.5);
        earlyCue.advance(2_250, false);
        earlyCue.acceptedReady();
        check(earlyCue.snapshot().charge01() < 1 && earlyCue.snapshot().stage() == Stage.READY_CUE,
                "fractional ready sound cue is not legal-release proof");
        State cancel = new State(60);
        cancel.advance(100, false);
        cancel.acceptedEnd();
        check(cancel.snapshot().stage() == Stage.END && cancel.snapshot().launchEnvelope() == 0,
                "early cancellation never creates launch");
    }

    private static State atFps(int fps, long duration) {
        State state = new State(60);
        state.acceptedReady();
        long previous = 0;
        for (long frame = 1; previous < duration; frame++) {
            long current = Math.min(duration, frame * 1_000 / fps);
            state.advance(current - previous, false);
            previous = current;
        }
        return state;
    }

    private static void clocksAndReload() {
        Snapshot reference = atFps(20, 121_750).snapshot();
        for (int fps : new int[] {30, 60, 144, 240}) {
            Snapshot sample = atFps(fps, 121_750).snapshot();
            check(sample.visualMillis() == reference.visualMillis(), "same elapsed age at FPS " + fps);
            near(sample.phase01(), reference.phase01(), "same accumulated phase at FPS " + fps);
            near(sample.readyPulse(), reference.readyPulse(), "same ready pulse at FPS " + fps);
        }
        State paused = atFps(60, 1_750);
        Snapshot before = paused.snapshot();
        paused.advance(90_000, true);
        check(paused.snapshot().equals(before), "pause freezes every visual field");
        paused.advance(1_000, false);
        check(paused.snapshot().visualMillis() == 2_750, "resume excludes paused interval");
        paused.acceptedGo();
        paused.advance(150, false);
        Snapshot launchBefore = paused.snapshot();
        paused.advance(20_000, true);
        check(paused.snapshot().equals(launchBefore), "pause freezes GO envelope");

        State loop = new State(60);
        loop.acceptedReady();
        loop.advance(PlasmaVfx2Timeline.PHASE_LOOP_MILLIS - 1, false);
        double left = loop.snapshot().phase01();
        loop.advance(1, false);
        near(loop.snapshot().phase01(), 0, "exact bounded loop wrap");
        near(loop.snapshot().readyPulse(), 1, "ready pulse has exact loop continuity");
        for (int cycles : new int[] {1, 7, 31, 120}) {
            check(Math.abs(Math.sin(2 * Math.PI * cycles * left)) < .0063,
                    "integral-cycle motion approaches continuous wrap " + cycles);
        }
        loop.advance(Long.MAX_VALUE, false);
        check(loop.snapshot().visualMillis() == Long.MAX_VALUE, "age saturates instead of overflowing");
        check(loop.snapshot().phase01() >= 0 && loop.snapshot().phase01() < 1, "phase remains bounded for long sessions");
        loop.advance(Long.MAX_VALUE, false);
        check(loop.snapshot().visualMillis() == Long.MAX_VALUE, "repeated huge time remains finite");
        loop.clear();
        check(loop.snapshot().stage() == Stage.INACTIVE && loop.snapshot().visualMillis() == 0,
                "resource/session reload clears all state");
        check(!loop.acceptedGo() && !loop.acceptedReady() && !loop.acceptedEnd(), "cleared instance cannot revive");
        State reversed = new State(60);
        reversed.advance(-1, false);
        check(reversed.snapshot().stage() == Stage.INACTIVE, "reversed clock fails closed");
        for (double invalid : new double[] {Double.NaN, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, 0, 29.9, 60.1}) {
            State state = new State(invalid);
            check(state.snapshot().stage() == Stage.INACTIVE && !state.acceptedGo(), "invalid charge fails closed");
        }
    }

    private static void actualPositionHistory() {
        History history = new History();
        check(history.offer(0, 0, 0, 0), "initial accepted position anchors history");
        check(!history.offer(1, 0, 0, 24), "minimum time protects packet bursts");
        check(!history.offer(.09, 0, 0, 25), "minimum distance protects repeated positions");
        check(history.offer(.1, 0, 0, 25), "both exact sample minima accepted");
        history.clear();
        for (int i = 0; i < 50; i++) check(history.offer(i * 6, 1, 2, i * 300L), "actual six-tick server position " + i);
        check(history.size() == 24 && history.points().get(0).visualMillis() == 26 * 300L,
                "24 samples retain newest actual positions only");
        check(history.points().get(23).position().x() == 294, "no interpolation or extrapolation");
        var immutable = history.points();
        try { immutable.clear(); throw new AssertionError("mutable history snapshot"); }
        catch (UnsupportedOperationException expected) { checks++; }
        history.expire(49 * 300L + 350);
        check(history.size() == 24, "exact stale boundary retained");
        history.expire(49 * 300L + 351);
        check(history.size() == 0, "stale trajectory clears");
        history.offer(0, 0, 0, 0);
        history.offer(8, 0, 0, 300);
        check(history.size() == 2, "bounded sync displacement accepted");
        history.offer(16.01, 0, 0, 600);
        check(history.size() == 1, "teleport discontinuity starts a new anchor");
        history.offer(17, 0, 0, 951);
        check(history.size() == 1, "stale arrival never bridges old trail");
        history.offer(18, 0, 0, 900);
        check(history.size() == 1 && history.points().get(0).visualMillis() == 900, "clock reversal resets history");
        for (double invalid : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY}) {
            check(!history.offer(invalid, 0, 0, 1_000) && history.size() == 0, "nonfinite coordinate clears history");
            history.offer(0, 0, 0, 900);
        }
        check(!history.offer((Vec) null, 1_000) && history.size() == 0, "null coordinate fails closed");
        history.offer(0, 0, 0, 0);
        check(!history.offer(1, 0, 0, -1) && history.size() == 0, "negative sample clock fails closed");
        history.offer(0, 0, 0, 0);
        history.clear();
        check(history.size() == 0, "END/reload clear removes all samples");
    }

    private static void boundedAdmission() {
        Budget budget = new Budget();
        check(!budget.reserve(-1) && !budget.reserve(Integer.MAX_VALUE), "invalid reservations refused");
        check(budget.bodiesUsed() == 0 && budget.verticesUsed() == 0, "failed reservations have no side effects");
        for (int i = 0; i < 4; i++) check(budget.reserve(128), "whole body and trail admitted " + i);
        check(budget.bodiesUsed() == 4 && budget.verticesUsed() == 512, "exact global budget");
        check(!budget.reserve(0) && !budget.reserve(1), "fifth material body refused even without geometry");
        budget.beginFrame();
        check(budget.reserve(500), "large first reservation");
        check(!budget.reserve(13) && budget.verticesUsed() == 500 && budget.bodiesUsed() == 1,
                "over-budget instance cannot consume partial allocation");
        check(budget.reserve(12) && budget.remainingVertices() == 0, "exact remaining vertices accepted");
        check(budget.reserve(0) && budget.reserve(0) && !budget.reserve(0), "zero-vertex material still counts body cap");
        budget.beginFrame();
        check(budget.bodiesUsed() == 0 && budget.remainingVertices() == 512, "each new frame restores budget");
    }

    public static void main(String[] args) {
        acceptedStages();
        clocksAndReload();
        actualPositionHistory();
        boundedAdmission();
        System.out.println("PASS " + checks + " optional Plasma timing, history and admission checks");
    }
}
