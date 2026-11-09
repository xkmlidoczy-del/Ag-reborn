package cn.academy.port;

import cn.academy.port.skill.CoinTosses;

/** Standalone source/physics checks; deliberately does not launch Minecraft. */
public final class CoinTossRegressionTest {
    private static int assertions;

    private static void eq(double expected, double actual) {
        assertions++;
        if (!Double.isFinite(actual) || Math.abs(expected - actual) > 1.0E-9)
            throw new AssertionError(expected + " != " + actual);
    }

    private static void check(boolean value, String message) {
        assertions++;
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        check(!CoinTosses.Trajectory.readyProgress(Math.nextDown(0.6)), "hint is charging just below .6");
        check(CoinTosses.Trajectory.readyProgress(0.6), "classic hint becomes active at exactly .6");
        check(CoinTosses.Trajectory.readyProgress(Math.nextUp(0.6)), "hint stays active above .6");
        check(!CoinTosses.Trajectory.acceptsProgress(0.7), "fire requires strict >.7");
        check(CoinTosses.Trajectory.acceptsProgress(Math.nextUp(0.7)), "fire accepts the value above .7");
        check(!CoinTosses.Trajectory.readyProgress(Double.NaN), "NaN cannot show a ready hint");
        var coin = new CoinTosses.Trajectory(64, 0);
        eq(0.92, coin.velocity());
        eq(64, coin.height());
        eq(0, coin.maximumHeight());
        eq(0, coin.progress());
        coin.tick();
        eq(0.86, coin.velocity());
        eq(64.86, coin.height());
        eq(64, coin.previousHeight());
        eq(64.86, coin.maximumHeight());
        eq(0.06 / 0.92 * 0.5, coin.progress());
        for (int tick = 2; tick <= 15; tick++) coin.tick();
        eq(70.6, coin.height());
        eq(70.6, coin.maximumHeight());
        eq(0.02, coin.velocity());
        coin.tick();
        eq(-0.04, coin.velocity());
        eq(70.56, coin.height());
        eq(70.6, coin.maximumHeight());
        eq(0.5 + 0.04 / 6.6 * 0.5, coin.progress());

        var early = new CoinTosses.Trajectory(64, 0);
        check(!early.attempt(true), "early judgement must fail");
        check(early.judged(), "early judgement must still be spent");
        for (int tick = 1; tick <= 25; tick++) early.tick();
        check(early.progress() > 0.7, "early test must reach late window");
        check(!early.attempt(true), "a previously failed coin cannot be replayed");
        check(!early.ready(), "a judged coin no longer has a QTE hint");
        check(!early.finished(64), "a failed QTE keeps its physical returning coin");

        var late = new CoinTosses.Trajectory(64, 0);
        int firstReady = -1, firstFire = -1, firstReturn = -1;
        for (int tick = 1; tick <= 30; tick++) {
            late.tick();
            eq(64 + tick * 0.92 - 0.06 * tick * (tick + 1) / 2.0, late.height());
            eq(0.92 - tick * 0.06, late.velocity());
            if (firstReady < 0 && late.ready()) firstReady = tick;
            if (firstFire < 0 && late.progress() > CoinTosses.QTE_THRESHOLD) firstFire = tick;
            if (firstReturn < 0 && late.finished(64)) firstReturn = tick;
            if (tick == 25) {
                check(late.attempt(true), "first valid late QTE must pass");
                check(!late.attempt(true), "successful QTE cannot be repeated");
            }
        }
        eq(22, firstReady);
        eq(25, firstFire);
        eq(30, firstReturn);
        eq(1, late.progress());

        var ineligible = new CoinTosses.Trajectory(64, 0);
        for (int tick = 0; tick < 25; tick++) ineligible.tick();
        check(!ineligible.attempt(false), "disabled/wrong-category/unknown skill cannot pass");
        check(!ineligible.attempt(true), "ineligible judgement cannot be retried after enabling");

        var risingPlayer = new CoinTosses.Trajectory(64, 0.1);
        eq(1.02, risingPlayer.velocity());
        eq(-0.1 / 0.92 * 0.5, risingPlayer.progress());
        risingPlayer.tick();
        eq(0.96, risingPlayer.velocity());
        eq(64.96, risingPlayer.height());

        var longFlight = new CoinTosses.Trajectory(64, 20);
        for (int tick = 0; tick < 120; tick++) longFlight.tick();
        check(!longFlight.finished(-1000), "MAXLIFE is strict >120, not >=120");
        longFlight.tick();
        check(longFlight.finished(-1000), "tick 121 must return regardless of altitude");
        var risingAbovePlayer = new CoinTosses.Trajectory(64, 0);
        risingAbovePlayer.tick();
        check(!risingAbovePlayer.finished(100), "being below player only returns while falling");
        for (int tick = 1; tick < 16; tick++) risingAbovePlayer.tick();
        check(risingAbovePlayer.finished(100), "falling below a climbing player returns");
        check(!risingAbovePlayer.finished(risingAbovePlayer.height()), "return height comparison is strict");

        var underground = new CoinTosses.Trajectory(-64, 0);
        for (int tick = 0; tick < 16; tick++) underground.tick();
        eq(0, underground.maximumHeight());
        eq(0.5 + 57.44 / 64 * 0.5, underground.progress());
        var zeroDenominator = new CoinTosses.Trajectory(0, -1);
        check(Double.isNaN(zeroDenominator.progress()), "classic zero-height denominator is preserved");
        check(!zeroDenominator.attempt(true), "NaN progress must never pass a QTE");
        boolean rejected = false;
        try { new CoinTosses.Trajectory(Double.NaN, 0); }
        catch (IllegalArgumentException expected) { rejected = true; }
        check(rejected, "non-finite physics must be rejected");
        System.out.println("PASS " + assertions + " coin physics/QTE assertions (no game launched)");
    }
}
