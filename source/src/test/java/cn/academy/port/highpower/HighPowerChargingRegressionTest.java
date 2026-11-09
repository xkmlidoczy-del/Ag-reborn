package cn.academy.port.highpower;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.core.CurrentChargingSession;
import cn.academy.port.core.SkillConsumption;
import java.util.concurrent.atomic.AtomicInteger;

/** Closed deterministic planning/payment tests; runtime identity/storage is covered by GameTests. */
public final class HighPowerChargingRegressionTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static void near(double actual, double expected, String label) { check(Math.abs(actual - expected) < 1e-5, label + ": " + actual + " != " + expected); }
    private static AbilityProgress state() {
        var s = new AbilityProgress(); s.selectCategory("electromaster"); s.setLevel(1); s.learn("charging"); s.activated = true;
        return s;
    }
    public static void main(String[] args) {
        near(HighPowerChargingRules.accepted(0, 10000, 50, 15), 15, "novice output");
        near(HighPowerChargingRules.accepted(0, 10000, 20, 35), 20, "native item bandwidth");
        near(HighPowerChargingRules.accepted(9999, 10000, 50, 15), 1, "one IF headroom");
        near(HighPowerChargingRules.price(1, 15, 3), .2, "only accepted fraction billed");
        near(HighPowerChargingRules.price(20, 35, 7), 4, "bandwidth fraction billed");
        check(HighPowerChargingRules.accepted(10000, 10000, 50, 15) == 0, "full output zero");
        check(HighPowerChargingRules.accepted(0, 10000, 0, 15) == 0, "zero bandwidth output zero");
        for (double bad : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1}) {
            check(HighPowerChargingRules.accepted(bad, 10000, 50, 15) == 0, "bad store");
            check(HighPowerChargingRules.accepted(0, bad, 50, 15) == 0, "bad capacity");
            check(HighPowerChargingRules.accepted(0, 10000, bad, 15) == 0, "bad bandwidth");
            check(HighPowerChargingRules.accepted(0, 10000, 50, bad) == 0, "bad request");
        }
        for (int i = 0; i <= 10000; i++) {
            double stored = i * 19.99999999, request = .1 + (i % 350) / 10.0;
            double accepted = HighPowerChargingRules.accepted(stored, 200000, Double.MAX_VALUE, request);
            check(accepted >= 0 && accepted <= request && stored + accepted <= 200000, "bounded representable delta " + i);
            check(stored + accepted - stored == accepted, "actual arithmetic delta " + i);
        }
        var s = state(); AtomicInteger writes = new AtomicInteger(), awards = new AtomicInteger();
        double beforeCp = s.cp;
        check(s.consumeKnownEnergy("charging", .2, 65, () -> true, writes::incrementAndGet, () -> {
            check(s.consumptionInProgress(), "observer awards stay inside transaction guard");
            s.addExperience("charging", CurrentChargingSession.SUPPORTED_EXPERIENCE); awards.incrementAndGet();
        }), "one accepted native transaction");
        near(s.cp, (float) beforeCp - .2f, "float-compatible proportional debit");
        near(s.overload, 65, "first successful transfer pays classic start strain");
        near(s.extraCp, .2f * .0025f, "only paid portion trains CP capacity");
        check(writes.get() == 1 && awards.get() == 1 && !s.consumptionInProgress(), "once-only write/award, guard unwinds");
        for (int mode = 0; mode < 8; mode++) {
            var candidate = state(); final int selected = mode;
            candidate.bindConsumption(() -> SkillConsumption.Config.DEFAULT, event -> {
                if (selected == 0) event.cp = 0;
                if (selected == 1) event.cp *= 2;
                if (selected == 2) event.overload += 1;
                if (selected == 3) event.cp = Float.NaN;
            }, () -> { throw new AssertionError("no overload callback may occur"); }, () -> true);
            candidate.bindCreativeMode(requested -> selected == 4);
            if (mode == 5) candidate.cp = 0;
            if (mode == 6) candidate.overload = 50;
            double cp = candidate.cp, overload = candidate.overload, growth = candidate.extraCp;
            int delayed = candidate.cpDelay, oldWrites = writes.get(), oldAwards = awards.get();
            check(!candidate.consumeKnownEnergy("charging", 3, 65, () -> selected != 7,
                    writes::incrementAndGet, awards::incrementAndGet), "rejected contract " + mode);
            check(candidate.cp == cp && candidate.overload == overload && candidate.extraCp == growth && candidate.cpDelay == delayed
                    && writes.get() == oldWrites && awards.get() == oldAwards, "rejection leaves ledgers/write/award unchanged " + mode);
        }
        var reentrant = state(); AtomicInteger hooks = new AtomicInteger();
        reentrant.bindConsumption(() -> SkillConsumption.Config.DEFAULT, event -> {
            hooks.incrementAndGet();
            check(!reentrant.consumeKnownEnergy("charging", 3, 0, () -> true,
                    () -> { throw new AssertionError("recursive write"); }, () -> {}), "recursive transaction refused");
        }, SkillConsumption.NO_OVERLOAD_EVENT, () -> true);
        check(reentrant.consumeKnownEnergy("charging", 3, 65, () -> true, () -> {}, () -> {}), "outer transaction valid");
        check(hooks.get() == 1, "recursive rejection happens before hooks");
        var tiny = state(); double cp = tiny.cp;
        check(!tiny.consumeKnownEnergy("charging", Double.MIN_NORMAL, 0, () -> true, () -> { throw new AssertionError("free energy"); }, () -> {}), "unrepresentable debit cannot produce free IF");
        check(tiny.cp == cp, "tiny rejection leaves CP unchanged");
        System.out.println("PASS " + assertions + " high-power native charging assertions");
    }
}
