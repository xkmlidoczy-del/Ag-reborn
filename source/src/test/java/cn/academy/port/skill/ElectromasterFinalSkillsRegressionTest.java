package cn.academy.port.skill;
import static cn.academy.port.core.ClassicFloatLedgerExpectations.*;

import cn.academy.port.core.AbilityProgress;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Independent straight transcription of executable1.0.7 arithmetic, not a game launch. */
public final class ElectromasterFinalSkillsRegressionTest {
    private static int assertions;
    private static void check(boolean value, String label) { assertions++; if (!value) throw new AssertionError(label); }
    private static void same(float actual, float expected, String label) { check(Float.floatToIntBits(actual) == Float.floatToIntBits(expected), label + " actual=" + actual + " expected=" + expected); }
    private static void close(double actual, double expected, String label) { check(Math.abs(actual - expected) < 1E-8, label + " actual=" + actual + " expected=" + expected); }
    private static float lerpf(float a, float b, float t) { return a + t * (b - a); }
    private static AbilityProgress ready(String id, double mastery) {
        var state = new AbilityProgress(); state.selectCategory("electromaster"); state.setLevel(5); state.learn(id); state.experience.put(id, mastery); state.activated = true; return state;
    }
    public static void main(String[] args) {
        for (int e = 0; e <= 10000; e++) {
            float exp = e / 10000F;
            same(ThunderClapSession.overload(exp), lerpf(390, 252, exp), "ThunderClap overload float fidelity");
            same(ThunderClapSession.consumption(exp), lerpf(18, 25, exp), "ThunderClap CP float fidelity");
            same(ThunderClapSession.range(exp), lerpf(32, 64, exp), "LV5 ThunderClap expanded range float fidelity");
            same(BodyIntensifySession.overload(exp), lerpf(200, 120, exp), "BodyIntensify overload float fidelity");
            same(BodyIntensifySession.consumption(exp), lerpf(20, 15, exp), "BodyIntensify CP float fidelity");
            check(BodyIntensifySession.cooldown(exp) == (int) lerpf(900, 600, exp), "BodyIntensify cooldown float truncation");
            for (int ticks : new int[]{0, 39, 40, 41, 59, 60}) {
                same(ThunderClapSession.damage(exp, ticks), lerpf(36, 72, exp) * lerpf(1F, 1.2F, (ticks - 40F) / 60F), "source damage divisor60");
                check(ThunderClapSession.cooldown(exp, ticks) == (int) (ticks * lerpf(10, 6, exp)), "source tick-dependent cooldown");
            }
        }
        for (double exp : new double[]{0, .2, .5, .999, 1}) {
            for (int failure = 1; failure <= 40; failure++) {
                var state = ready(ThunderClapSession.ID, exp);
                var session = ThunderClapSession.begin(state, false);
                float cost=ThunderClapSession.consumption(exp);
                // Half a payment of margin stays clear of either affordability boundary despite float subtraction.
                state.cp=cost*(failure-.5f);
                float sourceBudget=(float)state.cp;int successful=0;
                while(sourceBudget>=cost){sourceBudget-=cost;successful++;}
                check(successful==failure-1,"source float margin budget selects requested failure tick");
                ThunderClapSession.TickResult result = null;
                for (int tick = 1; tick <= failure; tick++){result=session.tick(false);if(tick<failure)check(result==ThunderClapSession.TickResult.CONTINUE,"every pre-failure tick continues");}
                check(result == (failure < 40 ? ThunderClapSession.TickResult.CANCEL : ThunderClapSession.TickResult.FIRE), "CP failure tick40 alone discharges exp="+exp+" failure="+failure+" result="+result+" ticks="+session.ticks());
                check(session.complete() == (failure == 40), "only valid discharge commits");
                check(!session.complete(), "discharge commit replay rejected");
                close(state.exp(ThunderClapSession.ID), failure==40?masteryAfter(exp,.003F,1):exp, "source one EXP contribution");
            }
            var state = ready(ThunderClapSession.ID, exp); var session = ThunderClapSession.begin(state, false);
            double before = state.cp;
            for (int tick = 1; tick <= 60; tick++) check(session.tick(false) == (tick == 60 ? ThunderClapSession.TickResult.FIRE : ThunderClapSession.TickResult.CONTINUE), "auto60 exact boundary");
            close(state.cp, paidCpAfter(before,ThunderClapSession.consumption(exp),40), "only40paid ticks");
            check(session.complete(), "60tick fire accepted");
            check(state.cooldowns.get(ThunderClapSession.ID) == ThunderClapSession.cooldown(exp, 60), "captured cooldown after EXP");
            for (int held : new int[]{0, 10, 39, 40, 41, 59}) {
                state = ready(ThunderClapSession.ID, exp); session = ThunderClapSession.begin(state, true);
                for (int tick = 0; tick < held; tick++) session.tick(true);
                session.end(); check(!session.complete() && state.cooldowns.isEmpty(), "key-up/abort cancels even charged hold");
                close(state.exp(ThunderClapSession.ID), exp, "cancel never awards EXP");
            }
        }
        same(ThunderClapSession.radialDamage(72, 0, 30), 72, "center full damage");
        same(ThunderClapSession.radialDamage(72, 15, 30), 36, "linear radial falloff");
        same(ThunderClapSession.radialDamage(72, 30, 30), 0, "sphere boundary hook has zero raw damage");
        check(ThunderClapSession.within(900, 30) && !ThunderClapSession.within(Math.nextUp(900D), 30) && !ThunderClapSession.within(Double.NaN, 30), "inclusive finite sphere");
        for (int ticks = 0; ticks < 100; ticks++) {
            for (int seed = 0; seed < 128; seed++) {
                double exp = seed / 127D;
                var state = ready(BodyIntensifySession.ID, exp); var session = BodyIntensifySession.begin(state, true);
                for (int tick = 0; tick < ticks; tick++) check(session.tick(true), "body active before100");
                Random actualRandom = new Random(seed), oracleRandom = new Random(seed);
                var result = session.release(actualRandom::nextDouble);
                if (ticks < 10) { check(result == null && !session.complete(), "earlybodycancel"); continue; }
                int ct = Math.min(ticks, 40);
                double p = (ct - 10.0) / 18.0;
                int duration = (int) ((1 + oracleRandom.nextDouble()) * ct * (cn.academy.port.core.SkillTuning.number("body_intensify", "buff_duration_multiplier_novice") + (cn.academy.port.core.SkillTuning.number("body_intensify", "buff_duration_multiplier_mastered") - cn.academy.port.core.SkillTuning.number("body_intensify", "buff_duration_multiplier_novice")) * (double) (float) exp));
                List<BodyIntensifySession.Buff> expected = new ArrayList<>();
                int i = 0;
                while (p > 0) {
                    if (oracleRandom.nextDouble() < p) {
                        i++; var effect = BodyIntensifySession.Effect.values()[i];
                        expected.add(new BodyIntensifySession.Buff(effect, duration, Math.min((int) Math.floor((ct - 10.0) / 18.0), i == 0 ? 3 : 1)));
                    }
                    p -= 1;
                }
                check(result.buffs().equals(expected), "discarded shuffle and preincremented fixed buff order exact");
                check(result.hungerDuration() == (int) (1.25F * ct) && result.hungerAmplifier() == 2, "source HungerIII even at10");
                check(result.chargeTicks() == ct, "duration and probability cap at40");
                check(session.complete() && !session.complete(), "body release one commit");
                close(state.exp(BodyIntensifySession.ID), masteryAfter(exp,.01F,1), "body EXP float literal");
                check(state.cooldowns.get(BodyIntensifySession.ID) == (int) lerpf(900, 600, masteryAfter(exp,.01F,1)), "cooldown uses postaward mastery");
                check(session.release(actualRandom::nextDouble) == null, "body release replay gives no effects");
            }
        }
        var state = ready(BodyIntensifySession.ID, 0); var body = BodyIntensifySession.begin(state, false);
        double floor = state.overload; state.overload = 0;
        check(body.tick(false) && state.overload == floor, "overload floor restored before consumption");
        double before = state.cp;
        for (int i = 1; i < 40; i++) check(body.tick(false), "first40 paid ticks");
        close(state.cp, before - 39 * 20, "CP20 perpaidtick"); before = state.cp;
        for (int i = 40; i < 99; i++) check(body.tick(false), "unpaid ticks41through99");
        close(state.cp, before, "noCPpost40"); check(!body.tick(false) && !body.complete(), "auto100 cancels without buffs EXP or cooldown");
        state = ready(BodyIntensifySession.ID, 0); body = BodyIntensifySession.begin(state, false); state.cp = 199;
        for (int i = 0; i < 9; i++) check(body.tick(false), "poorbodyfirst9"); check(!body.tick(false), "CP failure at10 cancels, never grants hunger");
        state = ready(BodyIntensifySession.ID, 0); body = BodyIntensifySession.begin(state, false);
        for (int i = 0; i < 40; i++) body.tick(false); state.experience.put(BodyIntensifySession.ID, 1D);
        var changed = body.release(() -> .5); check(changed.buffs().getFirst().duration() == (int)(1.5 * 40 * cn.academy.port.core.SkillTuning.number("body_intensify", "buff_duration_multiplier_mastered")), "body duration reads current mastery");
        var thunderState = ready(ThunderClapSession.ID, 0); var thunder = ThunderClapSession.begin(thunderState, true); thunderState.experience.put(ThunderClapSession.ID, 1D);
        same(thunder.mastery(), 0, "thundercapturescontextmastery");
        for (String id : new String[]{BodyIntensifySession.ID, ThunderClapSession.ID}) {
            state = ready(id, 0); state.interfering = true; check(id.equals(BodyIntensifySession.ID) ? BodyIntensifySession.begin(state, false) == null : ThunderClapSession.begin(state, false) == null, "interference rejects start");
            state.interfering = false; state.overload = state.maxOverload() - 1;
            if (id.equals(BodyIntensifySession.ID)) check(!BodyIntensifySession.begin(state, false).active(), "body overload disposes zeroCP start");
            else check(!ThunderClapSession.begin(state, false).active(), "thunder overload disposes zeroCP start");
        }
        System.out.println("PASS " + assertions + " classic final Electromaster differential and lifecycle assertions");
    }
}
