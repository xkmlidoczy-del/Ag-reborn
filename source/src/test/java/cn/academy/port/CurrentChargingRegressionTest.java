package cn.academy.port;
import static cn.academy.port.core.ClassicFloatLedgerExpectations.*;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.CurrentChargingSession;

/** Pure source-order regression. No Minecraft classes, bootstrap, game, or network are exercised. */
public final class CurrentChargingRegressionTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static void close(double value, double expected, String label) {
        check(Math.abs(value - expected) <= .000001, label + ": expected " + expected + ", got " + value);
    }
    private static AbilityProgress ready(double mastery) {
        var state = new AbilityProgress();
        state.selectCategory("electromaster"); state.setLevel(1); state.learn(CurrentChargingSession.ID);
        state.experience.put(CurrentChargingSession.ID, mastery); state.activated = true;
        return state;
    }
    private static final class Target implements CurrentChargingSession.Target {
        boolean present = true, supported = true, reject;
        boolean ignoreBandwidth;
        double received;
        int calls;
        @Override public boolean present() { return present; }
        @Override public boolean supported() { return supported; }
        @Override public double charge(double amount, boolean bypass) {
            calls++; ignoreBandwidth = bypass;
            if (reject) return amount;
            received += amount; return 0;
        }
    }
    public static void main(String[] args) {
        close(CurrentChargingSession.speed(0), 15, "novice speed IF/tick");
        close(CurrentChargingSession.speed(1), 35, "master speed IF/tick");
        close(CurrentChargingSession.consumption(0), 3, "novice CP/tick");
        close(CurrentChargingSession.consumption(1), 7, "master CP/tick");
        close(CurrentChargingSession.startOverload(0), 65, "novice one-time overload");
        close(CurrentChargingSession.startOverload(1), 48, "master one-time overload");
        close(CurrentChargingSession.speed(.5), 25, "interpolated speed");
        close(CurrentChargingSession.consumption(.5), 5, "interpolated CP");
        close(CurrentChargingSession.startOverload(.5), 56.5, "interpolated overload");
        close(CurrentChargingSession.speed(Double.NaN), 15, "nonfinite mastery defaults novice");
        close(CurrentChargingSession.speed(Double.POSITIVE_INFINITY), 15, "infinite mastery rejected");
        close(CurrentChargingSession.speed(-1), 15, "low mastery bounded");
        close(CurrentChargingSession.speed(2), 35, "high mastery bounded");
        close(CurrentChargingSession.RANGE, 15, "fixed block trace distance");

        var state = ready(0);
        var session = CurrentChargingSession.begin(state, true, false);
        check(session != null && session.active() && session.itemMode(), "valid item context begins");
        close(state.cp, 1800, "start spends no CP"); close(state.overload, 65, "start overload debit");
        close(session.overloadFloor(), 65, "post-start overload floor captured");
        close(state.extraOverload, 65 * .0058f, "start overload trains max overload");
        close(state.extraCp, 0, "zero CP start has no max CP training");
        close(state.exp("charging"), 0, "start awards no experience");
        check(state.cooldowns.isEmpty(), "charging has no source cooldown");
        var target = new Target();
        check(session.tick(target, false) == CurrentChargingSession.TickResult.CONTINUE, "item first tick continues");
        close(state.cp, 1797, "item tick pays CP"); close(target.received, 15, "item tick transfers IF");
        check(!target.ignoreBandwidth, "source item path honors bandwidth");
        close(state.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "supported item EXP");
        close(state.levelExperience, CurrentChargingSession.SUPPORTED_EXPERIENCE, "level EXP");
        close(state.extraCp, 3 * .0025f, "tick max CP training");
        close(state.overload, 65, "tick adds no overload");
        check(state.cpDelay == 15 && state.overloadDelay == 32, "zero overload ticks still reset recovery delays");
        state.overload = 30;
        session.tick(target, false);
        close(state.overload, 65, "tick restores start floor");
        state.overload = 90;
        session.tick(target, false);
        close(state.overload, 90, "floor never lowers later overload");
        state.experience.put("charging", 1.0);
        double before = target.received;
        session.tick(target, false);
        close(target.received - before, 15, "current mastery does not alter captured transfer speed");
        close(state.cp, 1788, "current mastery does not alter captured CP cost");
        close(state.exp("charging"), 1, "mastery capped");
        session.end();
        check(session.tick(target, false) == CurrentChargingSession.TickResult.ALREADY_ENDED, "ended context does not charge");
        close(target.received, 60, "ended tick has no transfer");

        state = ready(0); session = CurrentChargingSession.begin(state, true, false); target = new Target();
        target.supported = false;
        session.tick(target, false);
        close(state.cp, 1797, "unsupported nonempty item still pays CP");
        close(state.exp("charging"), CurrentChargingSession.UNSUPPORTED_EXPERIENCE, "unsupported item gets smaller EXP");
        check(target.calls == 0, "unsupported item never invokes transfer");
        target.present = false;
        check(session.tick(target, false) == CurrentChargingSession.TickResult.ITEM_REMOVED, "empty item terminates captured item mode");
        close(state.cp, 1797, "empty item pays no extra CP");
        close(state.exp("charging"), CurrentChargingSession.UNSUPPORTED_EXPERIENCE, "empty item gets no EXP");
        target.present = true;
        check(session.tick(target, false) == CurrentChargingSession.TickResult.ALREADY_ENDED, "item return cannot revive ended context");

        for (boolean itemMode : new boolean[]{false, true}) {
            state = ready(0); session = CurrentChargingSession.begin(state, itemMode, false); target = new Target();
            target.reject = true; // Models full and extract-only supported energy targets.
            session.tick(target, false);
            close(target.received, 0, "supported but full target accepts zero");
            close(state.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "support, not actual energy, determines EXP");
            check(target.ignoreBandwidth != itemMode, "source block/item bandwidth request differs");
            state.cp = 2;
            double exp = state.exp("charging");
            int calls = target.calls;
            check(session.tick(target, false) == CurrentChargingSession.TickResult.RESOURCE_EXHAUSTED, "resource exhaustion ends context");
            close(state.cp, 2, "failed CP check leaves balance");
            close(state.exp("charging") - exp, itemMode ? 0 : CurrentChargingSession.SUPPORTED_EXPERIENCE,
                    "block final exhausted tick earns EXP; item does not");
            check(target.calls == calls + (itemMode ? 0 : 1), "block final exhausted tick invokes transfer; item does not");
        }
        state = ready(0); session = CurrentChargingSession.begin(state, false, false); target = new Target();
        state.cp = 0;
        session.tick(target, false);
        close(target.received, 15, "source exhausted block tick performs final free charge");
        close(state.exp("charging"), CurrentChargingSession.SUPPORTED_EXPERIENCE, "exhausted block charges before EXP before CP");
        state = ready(0); session = CurrentChargingSession.begin(state, false, false); target = new Target();
        target.present = false; target.supported = false; state.cp = 0;
        session.tick(target, false);
        close(state.exp("charging"), CurrentChargingSession.UNSUPPORTED_EXPERIENCE, "missed block exhausted tick still earns smaller EXP");
        check(target.calls == 0, "block miss has no transfer");

        state = ready(0); state.cp = 0; session = CurrentChargingSession.begin(state, true, false);
        check(session != null && session.active(), "zero CP still starts source zero-CP context");
        target = new Target(); session.tick(target, false);
        close(target.received, 0, "zero CP item does not transfer");
        state = ready(0); state.overload = 40; session = CurrentChargingSession.begin(state, true, false);
        check(session != null && !session.active() && !state.overloadFine, "start that overloads is paid but immediately disposed");
        close(state.overload, 100, "start strain capped at max before max-overload training");
        state = ready(0); state.cp = 0; session = CurrentChargingSession.begin(state, true, true);
        target = new Target(); session.tick(target, true);
        close(state.cp, 0, "creative has no CP debit"); close(state.overload, 0, "creative has no overload debit");
        close(target.received, 15, "creative still produces finite target transfer");
        close(state.extraCp, 3 * .0025f, "creative source still trains CP");
        close(state.extraOverload, 65 * .0058f, "creative source still trains max overload");

        for (int invalid = 0; invalid < 7; invalid++) {
            state = ready(0);
            switch (invalid) {
                case 0 -> state.category = "meltdowner";
                case 1 -> state.level = 0;
                case 2 -> state.activated = false;
                case 3 -> state.interfering = true;
                case 4 -> state.overloadFine = false;
                case 5 -> state.experience.clear();
                case 6 -> state.cooldowns.put("charging", 1);
            }
            check(CurrentChargingSession.begin(state, true, false) == null, "start gating " + invalid);
        }
        state = ready(0); session = CurrentChargingSession.begin(state, true, false); target = new Target();
        state.activated = false;
        check(session.tick(target, false) == CurrentChargingSession.TickResult.INVALID_STATE, "deactivation terminates active context");
        close(target.received, 0, "invalid state has no transfer");

        for (int step = 0; step <= 1000; step++) {
            double mastery = step / 1000.0;
            state = ready(mastery); session = CurrentChargingSession.begin(state, true, false); target = new Target();
            double initial = state.cp;
            session.tick(target, false);
            close(state.cp, paidCpAfter(initial,3f+(float)mastery*(7f-3f),1), "sweep captured source float CP " + step);
            close(target.received, CurrentChargingSession.speed(mastery), "sweep captured speed " + step);
            close(session.overloadFloor(), CurrentChargingSession.startOverload(mastery), "sweep captured overload " + step);
            check(state.cooldowns.isEmpty(), "sweep no cooldown " + step);
        }
        System.out.println("PASS " + assertions + " deterministic Current Charging assertions");
    }
}
