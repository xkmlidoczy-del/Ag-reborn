package cn.academy.port.skill;
import static cn.academy.port.core.ClassicFloatLedgerExpectations.*;

import cn.academy.port.core.AbilityProgress;
import cn.academy.port.testing.CheckpointSourceFixtures;

/** Golden executable formulas independently transcribed from classic SBContext and LSContext. */
public final class MeltdownerStarterRegressionTest {
    private static int checks;
    private static void check(boolean value, String label) { checks++; if (!value) throw new AssertionError(label); }
    private static void equal(double actual, double expected, String label) {
        check(Double.isFinite(actual) && Math.abs(actual - expected) <= 1E-8, label + ": expected " + expected + ", got " + actual);
    }
    private static AbilityProgress ready(String id, double exp) {
        var state = new AbilityProgress(); state.selectCategory("meltdowner"); state.setLevel(2);
        state.learn(id); state.experience.put(id, exp); state.activated = true; return state;
    }
    public static void main(String[] args) throws Exception {
        // Differential source formulas across mastery and every held tick, including strict/inclusive boundaries.
        for (int sample = 0; sample <= 1000; sample++) {
            double exp = sample / 1000D; float e = (float) exp;
            equal(ScatterBombSession.overload(exp), 80 + (60 - 80) * e, "Scatter overload float lerp");
            equal(ScatterBombSession.consumption(exp), 3 + (6 - 3) * e, "Scatter CP float lerp");
            equal(ScatterBombSession.damage(exp), 5 + (9 - 5) * e, "Scatter raw float damage");
            var state = ready(ScatterBombSession.ID, exp); var scatter = ScatterBombSession.begin(state, true);
            var reference = new ReferenceScatter(e);
            for (int tick = 1; tick <= 200; tick++) {
                var result = scatter.tick(true); reference.tick();
                check(result.spawnBall() == reference.spawn && result.ending() == reference.ending
                        && result.selfHurt() == reference.selfHurt, "Scatter source tick transitions " + tick);
                check(scatter.ticks() == reference.ticks && scatter.balls() == reference.balls, "Scatter source ball schedule");
            }
            check(scatter.complete() && !scatter.complete(), "Scatter terminal commit only once");
            equal(state.exp(ScatterBombSession.ID), masteryAfter(exp,.001F*7,1), "Scatter ball EXP float multiplication");
            check(state.cooldowns.isEmpty(), "Scatter source deliberately has no cooldown");
            equal(LightShieldSession.overload(exp), 110 + (60 - 110) * e, "Shield startup overload");
            equal(LightShieldSession.consumption(exp), 9 + (4 - 9) * e, "Shield tick CP");
            equal(LightShieldSession.actionCp(exp), 50 + (30 - 50) * e, "Shield touch CP / incoming overload");
            equal(LightShieldSession.actionOverload(exp), 5 + (3 - 5) * e, "Shield touch overload / incoming CP");
            equal(LightShieldSession.touchDamage(exp), 2 + (6 - 2) * e, "Shield touch damage");
            equal(LightShieldSession.absorbDamage(exp), 15 + (50 - 15) * e, "Shield absorb amount");
            state = ready(LightShieldSession.ID, exp); var shield = LightShieldSession.begin(state, true);
            var golden = new ReferenceShield(e);
            for (int tick = 1; tick <= Math.ceil(golden.max) + 1; tick++) {
                boolean body = shield.beginTick(true), expected = golden.tick();
                check(body == expected && shield.ticks() == golden.ticks && shield.ending() == golden.ending,
                        "Shield source strict timeout still runs terminating body");
            }
            equal(state.exp(LightShieldSession.ID), masteryAfter(exp,1E-6F,golden.ticks), "Shield every terminal body trains EXP");
            int expectedCooldown = (int) ((2 * golden.ticks) + (golden.ticks - 2 * golden.ticks) * e);
            check(shield.complete() && !shield.complete(), "Shield terminal commit only once");
            check(state.cooldowns.get(LightShieldSession.ID) == expectedCooldown, "Shield captured mastery cooldown");
            for (int duration = 0; duration <= 200; duration++)
                check(LightShieldSession.cooldown(exp, duration) == (int) (2 * duration + (duration - 2 * duration) * e), "Shield exact truncated cooldown");
        }
        var state = ready(ScatterBombSession.ID, 0); state.cp = 19 * 3;
        var scatter = ScatterBombSession.begin(state, false);
        for (int tick = 1; tick <= 20; tick++) {
            state.overload = 0; var result = scatter.tick(false); equal(state.overload, 80, "Scatter overload floor restored");
            check(result.spawnBall() == (tick == 20), "failed CP tick creates its ball first");
        }
        check(scatter.ending() && scatter.balls() == 1 && scatter.ticks() == 20, "failed CP20 has one shot");
        equal(state.cp, 0, "failed CP does not go negative"); scatter.complete(); equal(state.exp(ScatterBombSession.ID), .001F, "failed final CP ball still awards EXP");
        state = ready(ScatterBombSession.ID, 1); scatter = ScatterBombSession.begin(state, false);
        double initialCp = state.cp;
        for (int tick = 1; tick <= 200; tick++) {
            var result = scatter.tick(false); if (tick >= 80) equal(state.cp, initialCp - 80 * 6, "Scatter no CP charge after80");
            check(result.selfHurt() == (tick == 200), "forced self-hurt only at200");
        }
        scatter.complete(); check(state.cooldowns.isEmpty(), "forced termination still no cooldown");
        state = ready(LightShieldSession.ID, 0); state.setLevel(5); var shield = LightShieldSession.begin(state, false);
        double cp = state.cp; equal(state.overload, 110, "Shield initial strain110");
        check(shield.touch(false, true), "touch performs finite transaction"); equal(state.cp, cp - 50, "touch pays50 CP");
        equal(state.overload, 115, "touch pays5 strain"); shield.completeTouch();
        cp = state.cp; float amount = shield.absorb(20, true, false);
        equal(amount, 5, "incoming subtracts15"); equal(state.cp, cp - 5, "incoming swapped args pay5CP");
        equal(state.overload, 165, "incoming swapped args pay50strain"); equal(state.exp(LightShieldSession.ID), 2 * (double) .001F, "touch and incoming both train");
        int last = shield.lastAbsorb(); cp = state.cp; double strain = state.overload, exp = state.exp(LightShieldSession.ID);
        for (int tick = 0; tick <= 18; tick++) {
            if (tick != 0) shield.beginTick(true);
            equal(shield.absorb(20, true, false), 20, "inclusive absorb action interval0..18");
        }
        equal(state.cp, cp, "suppressed incoming pays no CP"); equal(state.overload, strain, "suppressed incoming pays no strain");
        equal(state.exp(LightShieldSession.ID), masteryAfter(exp,1E-6F,18), "suppressed incoming awards no action EXP");
        shield.beginTick(true); equal(shield.absorb(20, true, false), 5, "tick19 permits second incoming");
        check(shield.lastAbsorb() == last + 19, "last absorb records eligible attempt tick");
        state = ready(LightShieldSession.ID, 0); shield = LightShieldSession.begin(state, false); state.cp = 4;
        equal(shield.absorb(20, true, false), 20, "failed incoming CP does not absorb"); check(shield.lastAbsorb() == 0, "failed incoming still installs18-tick gate");
        equal(state.cp, 4, "failed incoming does not partially spend"); equal(state.exp(LightShieldSession.ID), .001F, "failed incoming still awards EXP");
        shield.absorb(20, true, false); equal(state.exp(LightShieldSession.ID), .001F, "retry within interval no extra award");
        state = ready(LightShieldSession.ID, 0); shield = LightShieldSession.begin(state, false); cp = state.cp;
        equal(shield.absorb(20, false, false), 20, "null environmental source not absorbed"); check(shield.lastAbsorb() == -1, "null source does not set interval");
        equal(state.cp, cp, "environmental source no cost"); equal(state.exp(LightShieldSession.ID), .001F, "environmental source still trains");
        shield.absorb(0, true, false); equal(state.exp(LightShieldSession.ID), .001F, "zero damage returns before EXP");
        state = ready(LightShieldSession.ID, 0); shield = LightShieldSession.begin(state, false); state.cp = 8;
        check(shield.beginTick(false) && shield.ending(), "failed tick still executes remainder");
        equal(state.exp(LightShieldSession.ID), 1E-6F, "failed tick still trains tick EXP");
        check(!shield.touch(false, true), "failed touch no action"); equal(state.exp(LightShieldSession.ID), 1E-6F, "failed touch no EXP");
        shield.complete(); check(state.cooldowns.get(LightShieldSession.ID) == 2, "failed first tick cooldown2");
        state = ready(LightShieldSession.ID, 1); shield = LightShieldSession.begin(state, true);
        for (int tick = 0; tick < cn.academy.port.core.SkillTuning.number("light_shield", "duration_ticks_mastered"); tick++) shield.beginTick(true);
        state.cp = 100; check(shield.beginTick(false) && shield.ending(), "master tick181 marks termination before remaining work");
        check(shield.touch(false, true), "terminating tick still touches"); equal(state.cp, 66, "terminating tick pays4+30CP");
        shield.completeTouch(); shield.complete(); check(state.cooldowns.get(LightShieldSession.ID) == cn.academy.port.core.SkillTuning.number("light_shield", "duration_ticks_mastered") + 1, "master strict timeout sets cooldown");
        for (String id : java.util.List.of(ScatterBombSession.ID, LightShieldSession.ID)) {
            state = ready(id, 0); state.category = "electromaster"; check(!mayStart(state, id), "wrong category blocked");
            state.category = "meltdowner"; state.level = 1; check(!mayStart(state, id), "level1 blocked"); state.level = 2;
            state.activated = false; check(!mayStart(state, id), "deactivated blocked"); state.activated = true;
            state.interfering = true; check(!mayStart(state, id), "interference blocked"); state.interfering = false;
            state.overloadFine = false; check(!mayStart(state, id), "overload lock blocked"); state.overloadFine = true;
            state.setCooldown(id, 1); check(!mayStart(state, id), "cooldown blocked"); state.tickCooldowns();
            state.experience.remove(id); check(!mayStart(state, id), "unlearned blocked");
            state = ready(id, 0); state.overload = state.maxOverload() - 1;
            if (id.equals(ScatterBombSession.ID)) { scatter = ScatterBombSession.begin(state, false); check(scatter.ending(), "Scatter startup overload disposes"); scatter.discard(); check(!scatter.complete(), "Scatter discard cannot award"); }
            else { shield = LightShieldSession.begin(state, false); check(shield.ending(), "Shield startup overload disposes"); shield.discard(); check(!shield.complete(), "Shield discard cannot cooldown"); }
        }
        for (float bodyYaw : new float[]{-720, -360, -180, -90, 0, 60, 90, 180, 270, 360, 720}) {
            for (int angle = -180; angle <= 180; angle++) {
                double dx = Math.sin(Math.toRadians(angle)), dz = Math.cos(Math.toRadians(angle));
                check(LightShieldSession.reachable(dx, dz, bodyYaw) == (Math.abs(-(Math.atan2(dx, dz) * 180 / Math.PI) - bodyYaw) % 360 < 60), "literal asymmetric yaw modulo");
            }
        }
        check(!LightShieldSession.reachable(1, 1, 270), "literal wrap quirk can reject a geometrically near target");
        float[] trigonometry = new float[65536];
        for (int i = 0; i < trigonometry.length; i++) trigonometry[i] = (float) Math.sin(i * Math.PI * 2 / 65536);
        float[] samples = {0, Math.nextUp(0F), .125F, .4999F, .5F, .5001F, .875F, Math.nextDown(1F)};
        for (int head = -720; head <= 720; head += 30) for (int vertical = -90; vertical <= 90; vertical += 15)
            for (float horizontalRoll : samples) for (float verticalRoll : samples) {
                var direction = ScatterBombAim.direction(head, vertical, horizontalRoll, verticalRoll);
                float yaw = (float) (head + 2 * (horizontalRoll - .5F) * 5D);
                float pitch = (float) (vertical + (verticalRoll - .5F) * 5D);
                float yawRadians = yaw / 180F * (float) Math.PI, pitchRadians = pitch / 180F * (float) Math.PI;
                float yawSin = trigonometry[(int) (yawRadians * 10430.378F) & 65535];
                float yawCos = trigonometry[(int) (yawRadians * 10430.378F + 16384F) & 65535];
                float pitchSin = trigonometry[(int) (pitchRadians * 10430.378F) & 65535];
                float pitchCos = trigonometry[(int) (pitchRadians * 10430.378F + 16384F) & 65535];
                double dx = -yawSin * pitchCos, dy = -pitchSin, dz = yawCos * pitchCos;
                double length = Math.sqrt(dx * dx + dy * dy + dz * dz);
                equal(direction.x(), dx / length, "Scatter source spread X"); equal(direction.y(), dy / length, "Scatter source spread Y"); equal(direction.z(), dz / length, "Scatter source spread Z");
                equal(direction.x() * direction.x() + direction.y() * direction.y() + direction.z() * direction.z(), 1, "Scatter spread normalized before15block move");
                check(yaw >= head - 5 && yaw <= head + 5 && pitch >= vertical - 2.5 && pitch <= vertical + 2.5, "source five-degree constructor bounds");
            }
        for (float invalid : new float[]{-1F, 1F, Float.NaN, Float.POSITIVE_INFINITY}) {
            boolean rejected = false; try { ScatterBombAim.direction(0, 0, invalid, .5F); } catch (IllegalArgumentException expected) { rejected = true; }
            check(rejected, "invalid RNG sample rejected");
        }
        String motion = CheckpointSourceFixtures.pinnedText("lambdalib-1.2.3/Motion3D.java.txt");
        check(motion.contains("+ 2 * (RNG.nextFloat() - 0.5F) * offset") && motion.contains("entity.rotationPitch + (RNG.nextFloat() - 0.5F) * offset"), "spread golden audit follows actual Motion3D float/double expression");
        String sb = CheckpointSourceFixtures.pinnedText("academycraft-1.0.7/ScatterBomb.scala.txt"),
                ls = CheckpointSourceFixtures.pinnedText("academycraft-1.0.7/LightShield.scala.txt");
        check(sb.contains("ticks == 200") && sb.contains("hurtResistantTime = -1") && !sb.contains("ctx.setCooldown"), "golden Scatter audit matches pinned source quirks");
        check(ls.contains("ctx.consume(getAbsorbConsumption, getAbsorbOverload)") && ls.contains("if (entity != null) if (isEntityReachable(entity)) perform = true\n    else perform = true"), "golden Shield audit matches swapped costs and dangling else");
        check(ls.contains("ContextManager.instance.find(classOf[LSContext])") && !ls.contains("context.get().player == player"), "source global first context retained");
        System.out.println("PASS " + checks + " Meltdowner starter source-differential formula, timing, resource, EXP and quirk checks");
    }
    private static boolean mayStart(AbilityProgress state, String id) { return id.equals(ScatterBombSession.ID) ? ScatterBombSession.mayStart(state) : LightShieldSession.mayStart(state); }
    private static final class ReferenceScatter {
        final float exp; int ticks, balls; boolean spawn, ending, selfHurt;
        ReferenceScatter(float exp) { this.exp = exp; }
        void tick() { ticks += 1; spawn = ticks <= 80 && ticks >= 20 && ticks % 10 == 0; if (spawn) balls++; selfHurt = ticks == 200; if (selfHurt) ending = true; }
    }
    private static final class ReferenceShield {
        final float max; int ticks; boolean ending;
        ReferenceShield(float exp) { max = (float) ((cn.academy.port.core.SkillTuning.number("light_shield", "duration_ticks_novice") + (cn.academy.port.core.SkillTuning.number("light_shield", "duration_ticks_mastered") - cn.academy.port.core.SkillTuning.number("light_shield", "duration_ticks_novice")) * exp)); }
        boolean tick() { if (ending) return false; ticks += 1; if (ticks > max) ending = true; return true; }
    }
}
