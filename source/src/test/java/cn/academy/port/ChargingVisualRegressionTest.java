package cn.academy.port;

import cn.academy.port.client.ClassicChargingTimeline;

/** Pure source-state checks; no Minecraft classes, launcher, Gradle, or display are required. */
public final class ChargingVisualRegressionTest {
    private static int assertions;

    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }

    private static void eq(double expected, double actual) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < 1.0E-7, expected + " != " + actual);
    }

    public static void main(String[] args) {
        eq(15, ClassicChargingTimeline.RANGE);
        eq(20, ClassicChargingTimeline.BEAM_TEMPLATES);
        eq(20, ClassicChargingTimeline.BEAM_LENGTH);
        eq(5, ClassicChargingTimeline.BEAM_PASSES);
        eq(.1, ClassicChargingTimeline.BEAM_WIDTH);
        eq(1.2, ClassicChargingTimeline.BEAM_MAX_OFFSET);
        eq(.3, ClassicChargingTimeline.BEAM_BRANCH);
        eq(.7, ClassicChargingTimeline.BEAM_WIDTH_SHRINK);
        eq(.8, ClassicChargingTimeline.BEAM_TEX_WIGGLE);
        check(!ClassicChargingTimeline.beamShown(true, .199999), "Shown beam hides below showWiggle .2");
        check(ClassicChargingTimeline.beamShown(true, .2), "Shown beam strict showWiggle boundary");
        check(ClassicChargingTimeline.beamShown(false, .799999), "Hidden beam shows below hideWiggle .8");
        check(!ClassicChargingTimeline.beamShown(false, .8), "Hidden beam strict hideWiggle boundary");
        eq(10, ClassicChargingTimeline.SURROUND_TEMPLATES);
        eq(3, ClassicChargingTimeline.SURROUND_PASSES);
        eq(4, ClassicChargingTimeline.THIN_COUNT);
        eq(6, ClassicChargingTimeline.NORMAL_COUNT);
        eq(.8, ClassicChargingTimeline.SURROUND_OFFSET);
        eq(.7, ClassicChargingTimeline.SURROUND_BRANCH);
        eq(.9, ClassicChargingTimeline.SURROUND_WIDTH_SHRINK);
        eq(.3, ClassicChargingTimeline.SURROUND_SCALE);
        eq(1.3, ClassicChargingTimeline.ITEM_SIZE_MULTIPLIER);
        eq(.3, ClassicChargingTimeline.LOOP_VOLUME);
        eq(.6, ClassicChargingTimeline.SUBARC_FRAME_RATE);
        eq(.7, ClassicChargingTimeline.SUBARC_SWITCH_RATE);
        eq(30, ClassicChargingTimeline.SUBARC_LIFE);
        check(!ClassicChargingTimeline.subArcShown(false, .21), "SubArc initially hidden; strict .3*.7 show boundary");
        check(ClassicChargingTimeline.subArcShown(false, .209999), "Hidden subArc shows below .21");
        check(ClassicChargingTimeline.subArcShown(true, .28), "Shown subArc strict .4*.7 hide boundary");
        check(!ClassicChargingTimeline.subArcShown(true, .279999), "Shown subArc hides below .28");
        eq(30, ClassicChargingTimeline.subArcAge(29, .899999));
        eq(29, ClassicChargingTimeline.subArcAge(29, .9));
        check(ClassicChargingTimeline.surroundShown(true), "Supported aimed block shows both surround modes");
        check(!ClassicChargingTimeline.surroundShown(false), "Item support alone never shows item surround");

        var tokens = new ClassicChargingTimeline.Tokens();
        check(!tokens.acceptStart(7, 0), "Zero token rejected");
        check(!tokens.acceptStart(7, -1), "Negative token rejected");
        check(tokens.acceptStart(7, 10), "Fresh start accepted");
        check(!tokens.acceptStart(7, 10), "Duplicate start never adds a second sound loop");
        check(!tokens.acceptStart(7, 9), "Older start cannot replace healthy charging");
        check(!ClassicChargingTimeline.matchingEnd(10, 9), "Old end cannot close active context");
        check(!ClassicChargingTimeline.matchingEnd(10, 11), "Other end cannot close active context");
        check(ClassicChargingTimeline.matchingEnd(10, 10), "Matching release closes context");
        tokens.rememberEnd(7, 10);
        check(!tokens.acceptStart(7, 10), "Released start replay rejected");
        check(tokens.acceptStart(7, 11), "Reactivation after release accepted");
        tokens.rememberEnd(7, 10);
        check(!tokens.acceptStart(7, 10), "Stale end cannot roll token history backward");
        tokens.rememberEnd(8, 12);
        check(!tokens.acceptStart(8, 12), "End before start suppresses reordered replay");
        check(tokens.acceptStart(8, 13), "Newer context for second caster accepted");
        tokens.clear();
        check(tokens.acceptStart(7, 1), "Session reset admits tokens from restarted server");
        check(!ClassicChargingTimeline.matchingEnd(0, 0), "Invalid matching tokens rejected");
        System.out.println("PASS " + assertions + " charging-visual assertions (no game launch)");
    }
}
