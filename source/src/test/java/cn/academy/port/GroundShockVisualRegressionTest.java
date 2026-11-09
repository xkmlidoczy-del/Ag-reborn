package cn.academy.port;

import cn.academy.port.client.ClassicGroundShockTimeline;

/** Source timing and lifecycle checks run without Minecraft, Gradle, a display, or a game. */
public final class GroundShockVisualRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void eq(double expected, double actual) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < 1E-6, expected + " != " + actual);
    }
    public static void main(String[] args) {
        eq(0, ClassicGroundShockTimeline.liftPitch(0));
        eq(-.05, ClassicGroundShockTimeline.liftPitch(1));
        eq(-.1, ClassicGroundShockTimeline.liftPitch(2));
        eq(-.15, ClassicGroundShockTimeline.liftPitch(3));
        eq(-.2, ClassicGroundShockTimeline.liftPitch(4));
        eq(-.2, ClassicGroundShockTimeline.liftPitch(5));
        eq(-.2, ClassicGroundShockTimeline.liftPitch(20));
        eq(-.16, ClassicGroundShockTimeline.liftPitch(21));
        eq(-.04, ClassicGroundShockTimeline.liftPitch(24));
        eq(0, ClassicGroundShockTimeline.liftPitch(25));
        eq(0, ClassicGroundShockTimeline.liftPitch(26));
        eq(0, ClassicGroundShockTimeline.liftPitch(Integer.MAX_VALUE));
        double lift = 0;
        for (int tick = 1; tick <= 25; tick++) lift += ClassicGroundShockTimeline.liftPitch(tick);
        eq(-4.1, lift);
        eq(0, ClassicGroundShockTimeline.slashPitch(0));
        for (int tick = 1; tick <= 4; tick++) eq(3.4, ClassicGroundShockTimeline.slashPitch(tick));
        eq(0, ClassicGroundShockTimeline.slashPitch(5));
        eq(13.6, ClassicGroundShockTimeline.slashPitch(1) * 4);
        eq(2, ClassicGroundShockTimeline.SOUND_VOLUME);
        eq(4, ClassicGroundShockTimeline.MIN_DIGGING);
        eq(8, ClassicGroundShockTimeline.MAX_DIGGING_EXCLUSIVE);
        eq(1, ClassicGroundShockTimeline.SMOKE_SIZE);
        eq(.5, ClassicGroundShockTimeline.SMOKE_CHANCE);
        eq(.5, ClassicGroundShockTimeline.SMOKE_LIFE_MIN);
        eq(.7, ClassicGroundShockTimeline.SMOKE_LIFE_MAX_EXCLUSIVE);
        for (double modifier : new double[]{.5, .6, .699999}) {
            eq(0, ClassicGroundShockTimeline.smokeAlpha(0, modifier));
            eq(.5, ClassicGroundShockTimeline.smokeAlpha(150 * modifier, modifier));
            eq(1, ClassicGroundShockTimeline.smokeAlpha(300 * modifier, modifier));
            eq(1, ClassicGroundShockTimeline.smokeAlpha(1_500 * modifier, modifier));
            eq(.5, ClassicGroundShockTimeline.smokeAlpha(1_750 * modifier, modifier));
            eq(0, ClassicGroundShockTimeline.smokeAlpha(2_000 * modifier, modifier));
            eq(0, ClassicGroundShockTimeline.smokeAlpha(3_000, modifier));
        }
        check(ClassicGroundShockTimeline.smokeAlive(3_999), "Invisible smoke still has the classic four-second entity lifetime");
        check(!ClassicGroundShockTimeline.smokeAlive(4_000), "Smoke dies at deltaTime >= 4");
        eq(0, ClassicGroundShockTimeline.smokeAlpha(Double.NaN, .6));
        eq(0, ClassicGroundShockTimeline.smokeAlpha(10, 0));
        for (int frame = 0; frame < 4; frame++) {
            eq((frame % 2) * .5, ClassicGroundShockTimeline.smokeU(frame));
            eq((frame / 2) * .5, ClassicGroundShockTimeline.smokeV(frame));
        }
        check(ClassicGroundShockTimeline.validBlockArrayLength(0), "Successful empty block set retains sound and slash");
        check(ClassicGroundShockTimeline.validBlockArrayLength(375), "All 125 source cells fit the packet bound");
        check(!ClassicGroundShockTimeline.validBlockArrayLength(378), "126 cells rejected");
        check(!ClassicGroundShockTimeline.validBlockArrayLength(374), "Incomplete xyz tuple rejected");
        var uplift = new ClassicGroundShockTimeline.Uplift(0);
        eq(-.05, uplift.tick());
        eq(-.1, uplift.tick());
        uplift.bind(10);
        eq(10, uplift.token());
        eq(2, uplift.ticks());
        eq(-.15, uplift.tick());
        uplift.bind(11);
        eq(10, uplift.token());
        for (int i = 0; i < 1_000_000; i++) uplift.tick();
        eq(0, uplift.tick());
        eq(26, uplift.ticks());
        eq(10, uplift.token());
        var tokens = new ClassicGroundShockTimeline.Tokens();
        check(!tokens.acceptStart(1, 0), "Zero token rejected");
        check(tokens.acceptStart(1, 1), "First start accepted");
        check(!tokens.acceptStart(1, 1), "Duplicate start cannot restart uplift");
        check(tokens.acceptPerform(1, 1), "Matching hold performs");
        check(!tokens.acceptPerform(1, 1), "Duplicate perform cannot replay slash, sound or particles");
        tokens.rememberAbort(1, 1);
        check(!tokens.acceptStart(1, 1), "Abort after perform never reopens hold");
        check(tokens.acceptStart(1, 2), "Second use accepted");
        tokens.rememberAbort(1, 2);
        check(!tokens.acceptPerform(1, 2), "Aborted token cannot perform");
        check(!tokens.acceptStart(1, 2), "Aborted token cannot restart");
        tokens.rememberAbort(2, 10);
        check(!tokens.acceptStart(2, 10), "Abort received before start suppresses reorder");
        check(!tokens.acceptPerform(2, 9), "Older observer perform rejected");
        check(tokens.acceptPerform(3, 20), "Observer may receive perform without a hold packet");
        check(!tokens.acceptStart(3, 20), "Late start after observer perform rejected");
        check(tokens.acceptStart(1, 3), "Newer use after abort accepted");
        tokens.rememberAbort(1, 2);
        check(tokens.acceptPerform(1, 3), "Old abort cannot cancel newer hold");
        check(!ClassicGroundShockTimeline.matchingToken(3, 2), "Stale end cannot close current hold");
        check(ClassicGroundShockTimeline.matchingToken(3, 3), "Matching end closes current hold");
        tokens.clear();
        check(tokens.acceptStart(1, 1), "Session replacement admits restarted server tokens");
        var clock = new ClassicGroundShockTimeline.PauseClock();
        eq(0, clock.update(1_000, true));
        eq(50, clock.update(1_050, true));
        eq(50, clock.update(1_100, false));
        eq(50, clock.update(10_000, false));
        eq(50, clock.update(11_000, true));
        eq(100, clock.update(11_050, true));
        eq(100, clock.update(11_000, true));
        clock.clear();
        eq(0, clock.update(12_000, true));
        System.out.println("PASS " + assertions + " Ground Shock visual assertions (no game launch)");
    }
}
