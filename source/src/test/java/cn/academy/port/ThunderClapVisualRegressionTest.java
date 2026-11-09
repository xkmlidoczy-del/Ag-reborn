package cn.academy.port;

import cn.academy.port.client.ClassicThunderClapTimeline;

/** Pure source-state checks; no Minecraft classes, Gradle, game launch, display, or save are needed. */
public final class ThunderClapVisualRegressionTest {
    private static int assertions;
    private static void check(boolean condition, String message) {
        assertions++;
        if (!condition) throw new AssertionError(message);
    }
    private static void eq(double expected, double actual) {
        check(Double.isFinite(actual) && Math.abs(expected - actual) < 1e-6, expected + " != " + actual);
    }

    public static void main(String[] args) {
        eq(40, ClassicThunderClapTimeline.RANGE);
        eq(60, ClassicThunderClapTimeline.CHARGE_TICKS);
        eq(10, ClassicThunderClapTimeline.END_LINGER_TICKS);
        eq(100, ClassicThunderClapTimeline.SURROUND_LIFE_TICKS);
        eq(.1, ClassicThunderClapTimeline.NORMAL_WALK_SPEED);
        eq(.001, ClassicThunderClapTimeline.MIN_WALK_SPEED);
        eq(5, ClassicThunderClapTimeline.BOLD_COUNT);
        eq(10, ClassicThunderClapTimeline.BOLD_TEMPLATES);
        eq(3, ClassicThunderClapTimeline.BOLD_PASSES);
        eq(3.5, ClassicThunderClapTimeline.BOLD_LENGTH_FROM);
        eq(4.5, ClassicThunderClapTimeline.BOLD_LENGTH_TO_EXCLUSIVE);
        eq(.35, ClassicThunderClapTimeline.BOLD_WIDTH);
        eq(1.2, ClassicThunderClapTimeline.BOLD_OFFSET);
        eq(.45, ClassicThunderClapTimeline.BOLD_BRANCH);
        eq(.9, ClassicThunderClapTimeline.BOLD_WIDTH_SHRINK);
        eq(.3, ClassicThunderClapTimeline.SURROUND_SCALE);
        eq(1.3, ClassicThunderClapTimeline.ENTITY_SIZE_MULTIPLIER);
        eq(30, ClassicThunderClapTimeline.SUBARC_LIFE);
        eq(.6, ClassicThunderClapTimeline.SUBARC_FRAME_RATE);
        eq(.7, ClassicThunderClapTimeline.SUBARC_SWITCH_RATE);
        check(ClassicThunderClapTimeline.replaceSubArc(.299999), "SubArc replacement below .5*.6");
        check(!ClassicThunderClapTimeline.replaceSubArc(.3), "SubArc replacement strict boundary");
        check(ClassicThunderClapTimeline.subArcShown(false, .209999), "Initially hidden arc shows below .3*.7");
        check(!ClassicThunderClapTimeline.subArcShown(false, .21), "Hidden arc strict switch boundary");
        check(!ClassicThunderClapTimeline.subArcShown(true, .279999), "Shown arc hides below .4*.7");
        check(ClassicThunderClapTimeline.subArcShown(true, .28), "Shown arc strict switch boundary");
        eq(30, ClassicThunderClapTimeline.subArcAge(29, .899999));
        eq(29, ClassicThunderClapTimeline.subArcAge(29, .9));

        var hold = new ClassicThunderClapTimeline.Hold();
        eq(.1, hold.speed());
        check(hold.active() && hold.surroundAlive() && hold.markVisible(true), "Accepted local context starts mark and surround");
        check(!hold.markVisible(false), "Observer never sees another player's target mark");
        double previousSpeed = hold.speed();
        for (int tick = 1; tick <= 60; tick++) {
            hold.tick();
            eq(tick, hold.ticks());
            eq(Math.max(.001, .1 - .099 / 60 * tick), hold.speed());
            check(hold.speed() <= previousSpeed, "Charge slows monotonically");
            check(hold.active() && hold.markVisible(true), "Client cannot discharge itself");
            previousSpeed = hold.speed();
        }
        eq(.001, hold.speed());
        hold.tick();
        check(hold.active(), "Missing/delayed authoritative end does not become an unauthorized client discharge");
        eq(.001, hold.speed());
        hold.end();
        eq(.1, hold.speed());
        check(!hold.active() && !hold.markVisible(true), "Termination restores speed and immediately removes mark");
        check(hold.surroundAlive(), "Termination retains surround");
        for (int tick = 1; tick < 10; tick++) {
            hold.tick();
            hold.end();
            check(hold.surroundAlive(), "Duplicate termination never resets linger age");
            eq(.1, hold.speed());
        }
        hold.tick();
        check(!hold.surroundAlive(), "Surround expires at exactly ten termination ticks");

        var earlyAbort = new ClassicThunderClapTimeline.Hold();
        for (int tick = 0; tick < 5; tick++) earlyAbort.tick();
        earlyAbort.end();
        eq(.1, earlyAbort.speed());
        check(!earlyAbort.markVisible(true), "Early key-up cancels mark without discharge");
        for (int tick = 0; tick < 10; tick++) earlyAbort.tick();
        check(!earlyAbort.surroundAlive(), "Early cancellation still has ten-tick surround cleanup");
        var naturalLife = new ClassicThunderClapTimeline.Hold();
        for (int tick = 0; tick < 99; tick++) naturalLife.tick();
        check(naturalLife.surroundAlive(), "EntitySurroundArc lives through age99");
        naturalLife.tick();
        check(!naturalLife.surroundAlive(), "Source surround default lifetime is100ticks");
        check(naturalLife.active() && naturalLife.markVisible(true), "Surround entity expiry never changes authoritative hold");
        eq(.001, naturalLife.speed());
        naturalLife.end();
        check(!naturalLife.surroundAlive(), "Termination cannot resurrect naturally expired surround");
        eq(.1, naturalLife.speed());
        eq(.1, ClassicThunderClapTimeline.walkSpeed(-1));
        eq(.001, ClassicThunderClapTimeline.walkSpeed(Integer.MAX_VALUE));

        eq(3_600, ClassicThunderClapTimeline.RIPPLE_CYCLE_MS);
        eq(1_200, ClassicThunderClapTimeline.RIPPLE_OFFSET_MS);
        eq(3, ClassicThunderClapTimeline.RIPPLE_LAYERS);
        eq(.8, ClassicThunderClapTimeline.RIPPLE_RED);
        eq(.8, ClassicThunderClapTimeline.RIPPLE_GREEN);
        eq(.8, ClassicThunderClapTimeline.RIPPLE_BLUE);
        eq(.7, ClassicThunderClapTimeline.RIPPLE_ALPHA);
        eq(0, ClassicThunderClapTimeline.ripplePhase(0, 0));
        eq(1_200, ClassicThunderClapTimeline.ripplePhase(0, 1));
        eq(2_400, ClassicThunderClapTimeline.ripplePhase(0, 2));
        eq(0, ClassicThunderClapTimeline.ripplePhase(3_600, 0));
        eq(0, ClassicThunderClapTimeline.ripplePhase(2_400, 1));
        eq(0, ClassicThunderClapTimeline.ripplePhase(1_200, 2));
        eq(3_599, ClassicThunderClapTimeline.ripplePhase(-1, 0));
        eq(0, ClassicThunderClapTimeline.rippleHeight(0));
        eq(.36, ClassicThunderClapTimeline.rippleHeight(1_200));
        eq(.72, ClassicThunderClapTimeline.rippleHeight(2_400));
        eq(1.9, ClassicThunderClapTimeline.rippleSize(0));
        eq(1.65, ClassicThunderClapTimeline.rippleSize(1_800));
        eq(1.4, ClassicThunderClapTimeline.rippleSize(3_600));
        eq(0, ClassicThunderClapTimeline.rippleFade(0));
        eq(.5, ClassicThunderClapTimeline.rippleFade(800));
        eq(1, ClassicThunderClapTimeline.rippleFade(1_600));
        eq(1, ClassicThunderClapTimeline.rippleFade(2_000));
        eq(.5, ClassicThunderClapTimeline.rippleFade(2_800));
        eq(0, ClassicThunderClapTimeline.rippleFade(3_600));
        eq(204, ClassicThunderClapTimeline.colorByte(ClassicThunderClapTimeline.RIPPLE_RED));
        eq(179, ClassicThunderClapTimeline.colorByte(ClassicThunderClapTimeline.RIPPLE_ALPHA));
        eq(0, ClassicThunderClapTimeline.colorByte(-1));
        eq(255, ClassicThunderClapTimeline.colorByte(2));
        for (int millis = 0; millis < 3_600; millis++) for (int layer = 0; layer < 3; layer++) {
            long phase = ClassicThunderClapTimeline.ripplePhase(millis, layer);
            check(phase >= 0 && phase < 3_600, "Ripple phase bounded for every layer");
            float fade = ClassicThunderClapTimeline.rippleFade(phase);
            check(fade >= 0 && fade <= 1, "Ripple fade bounded for every layer");
            float size = ClassicThunderClapTimeline.rippleSize(phase);
            check(size > 1.4F && size <= 1.9F, "Ripple size preserves source cycle");
        }
        boolean invalidLayer = false;
        try { ClassicThunderClapTimeline.ripplePhase(0, 3); } catch (IllegalArgumentException expected) { invalidLayer = true; }
        check(invalidLayer, "Invalid layer rejected");
        check(ClassicThunderClapTimeline.ripplePhase(Long.MAX_VALUE, 2) < 3_600, "Long-running phase does not overflow");

        var tokens = new ClassicThunderClapTimeline.Tokens();
        check(!tokens.acceptStart(7, 0) && !tokens.acceptStart(7, -1), "Nonpositive token rejected");
        check(tokens.acceptStart(7, 10), "Fresh accepted start");
        check(!tokens.acceptStart(7, 10) && !tokens.acceptStart(7, 9), "Duplicate/stale start rejected");
        check(ClassicThunderClapTimeline.matchingEnd(10, 10), "Only matching accepted token ends context");
        check(!ClassicThunderClapTimeline.matchingEnd(10, 9) && !ClassicThunderClapTimeline.matchingEnd(10, 11), "Unrelated end preserves active hold");
        check(!ClassicThunderClapTimeline.matchingEnd(0, 0), "Zero matching token is invalid");
        tokens.rememberEnd(7, 10);
        check(!tokens.acceptStart(7, 10), "Cancellation suppresses start replay");
        check(tokens.acceptStart(7, 11), "Later fresh hold accepted");
        tokens.rememberEnd(7, 10);
        check(!tokens.acceptStart(7, 10), "Stale end cannot move token history backward");
        tokens.rememberEnd(8, 12);
        check(!tokens.acceptStart(8, 12), "End arriving before start suppresses delayed start");
        check(tokens.acceptStart(8, 13), "Observer's next token accepted");
        tokens.clear();
        check(tokens.acceptStart(7, 1), "New server session can restart tokens");

        var input = new ClassicThunderClapTimeline.InputGate();
        check(input.acceptStart(0, input.capture()), "Internal non-input-driven context accepts nonce0");
        check(!input.acceptPacket(1) && !input.acceptPacket(-1), "Undriven mode accepts only nonce0, not positive old/negative inputs");
        check(input.end(0), "Internal context may complete");
        check(input.acceptStart(0, input.capture()), "Internal completion preserves non-input-driven mode");
        input.abort();
        check(input.acceptStart(0, input.capture()), "Cleanup without any physical press does not invent input-driven mode");
        long firstNonce = input.press();
        long firstPress = input.capture();
        check(firstNonce > 0, "Physical press gets positive client nonce");
        check(input.acceptStart(firstNonce, firstPress), "Held physical-input acknowledgement accepted");
        check(!input.acceptPacket(0) && !input.acceptPacket(-1), "Physical input rejects internal nonce0 and malformed missing/negative nonce");
        input.abort();
        check(!input.acceptStart(firstNonce, firstPress) && !input.acceptStart(firstNonce, input.capture()), "Abort retires nonce for queued and later starts");
        check(!input.acceptPacket(0), "Retired physical input cannot fall back to internal nonce0");
        long secondNonce = input.press();
        long secondPress = input.capture();
        check(secondNonce > firstNonce, "Rapid repress uses fresh monotonic nonce");
        check(!input.acceptStart(firstNonce, firstPress), "Fresh physical press rejects queued previous start");
        check(!input.acceptStart(firstNonce, secondPress), "Old start arriving only after new press is still rejected by echo nonce");
        check(!input.acceptPacket(firstNonce) && !input.end(firstNonce), "Old end nonce cannot retire a newer physical press");
        check(input.acceptStart(secondNonce, secondPress), "Fresh press remains ready for its matching acknowledgement after old end");
        check(!input.acceptStart(secondNonce, firstPress), "Generation also rejects callbacks queued before new physical press");
        check(ClassicThunderClapTimeline.matchingLocalEnd(20, secondNonce, 20, secondNonce), "Local end requires matching token and input nonce");
        check(!ClassicThunderClapTimeline.matchingLocalEnd(20, secondNonce, 20, firstNonce), "Even matching token cannot authorize an old input's end");
        check(!ClassicThunderClapTimeline.matchingLocalEnd(20, secondNonce, 19, secondNonce), "Even matching input cannot authorize an old server token's end");
        check(!ClassicThunderClapTimeline.matchingLocalEnd(20, -1, 20, -1), "Invalid/missing input cannot match local end");
        check(ClassicThunderClapTimeline.matchingLocalEnd(20, 0, 20, 0), "Internal nonce0 end is supported behind undriven gate");
        check(input.end(secondNonce), "Current physical-input end retires expected nonce");
        check(!input.acceptPacket(secondNonce) && !input.acceptPacket(0), "Ended input cannot restart or fall back to nonce0");
        long thirdNonce = input.press();
        check(thirdNonce > secondNonce, "Press after authoritative end stays monotonic");
        input.clear();
        check(input.acceptStart(0, input.capture()), "Session reset admits internal non-input-driven contexts");
        check(!input.acceptPacket(thirdNonce), "Session clear does not accept positive previous-session input");
        long fourthNonce = input.press();
        check(fourthNonce > thirdNonce, "Session clear never resets client nonce counter");
        check(!input.acceptStart(thirdNonce, input.capture()), "Repress after clear rejects old input even with fresh callback generation");
        check(input.acceptStart(fourthNonce, input.capture()), "Fresh session press admits matching new nonce");
        for (int press = 0; press < 1_024; press++) {
            input.abort();
            long retired = fourthNonce;
            fourthNonce = input.press();
            check(fourthNonce > retired, "Every rapid reactivation uses increasing positive nonce");
            check(!input.acceptStart(retired, input.capture()) && !input.end(retired), "Every old acknowledgement/end is inert after reactivation");
            check(input.acceptStart(fourthNonce, input.capture()), "Every current acknowledgement remains valid");
        }

        var clock = new ClassicThunderClapTimeline.PauseClock();
        eq(0, clock.update(1_000, true));
        eq(500, clock.update(1_500, true));
        eq(500, clock.update(2_000, false));
        eq(500, clock.update(8_000, false));
        eq(500, clock.update(9_000, true));
        eq(600, clock.update(9_100, true));
        eq(600, clock.update(9_000, true));
        clock.clear();
        eq(0, clock.elapsed());
        eq(0, clock.update(500, true));
        System.out.println("PASS " + assertions + " thunder-clap visual assertions (no game launch)");
    }
}
