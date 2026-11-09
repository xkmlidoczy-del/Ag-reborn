package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillExpAddedEvent;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.highpower.ElectromasterPrototype;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.HighPowerProgression;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import com.mojang.authlib.GameProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native growth/resource integration; enrollment, learned bits and initial CP are declared fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerGrowthResourcesRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_growth_resources";
    // Actual private prototype ledger. Tests write ONLY its initial CP balance, never clocks or growth proof.
    private static final String CP_KEY = "academy:electromaster_prototype";
    private static final ContinuousHighPowerGrowth.Curve ARC_COOLDOWN = ContinuousHighPowerGrowth.curve("10", "5", "3", "2", "1");
    private static final ContinuousHighPowerGrowth.Curve GROUND_COOLDOWN = ContinuousHighPowerGrowth.curve("60", "60", "20", "5", "1");
    private HighPowerGrowthResourcesRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void positive_native_xp_changes_fractional_values_without_refilling_or_extra_xp(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(2); var state = AbilityStorage.get(p);
        f.cp(p, 37); Balances balances = balances(p); var before = HighPowerGrowth.values(p);
        int denominator = SkillCatalog.levelSkillCount(state);
        h.assertTrue(denominator == 2, "six pack additions leave the actual classic LV2 denominator at two");
        double classicCp = state.cp, classicOverload = state.overload;
        int[] events = {0};
        Consumer<SkillExpAddedEvent> observer = event -> {
            if (event.getEntity() == p) {
                events[0]++;
                h.assertTrue(event.state == state && event.amount == .0005F, "one native event retains the original small XP amount");
            }
        };
        f.observe(observer);
        float expected = 0;
        for (int i = 0; i < 2; i++) {
            state.addExperience(HighPowerSkills.FORKED_ARC, .0005);
            expected += .0005F;
            var after = HighPowerGrowth.values(p);
            h.assertTrue(after.position().compareTo(before.position()) > 0 && after.position().compareTo(new BigDecimal("2.01")) < 0,
                    "sub-percent native progression is retained without percentage or integer quantization");
            h.assertTrue(after.damage().compareTo(before.damage()) > 0 && after.cpMaximum() > before.cpMaximum()
                    && after.cpRecovery() > before.cpRecovery() && after.arcCost() < before.arcCost()
                    && after.tetherCost() < before.tetherCost() && after.lv2Cost() < before.lv2Cost(),
                    "each valid tiny award changes all current continuous amount curves");
            exact(h, after.damage(), ContinuousHighPowerGrowth.sample(after.position()).damage(), "damage remains the exact decimal sample");
            exact(h, after.earnedPosition(), after.position(), "actual native XP certifies this fractional coordinate");
            sameBalances(h, p, balances, "growth before any recovery tick");
            h.assertTrue(state.levelExperience == (double) expected && state.exp(HighPowerSkills.FORKED_ARC) == (double) expected,
                    "growth consumes exactly the existing float XP award without adding an extra one");
            h.assertTrue(SkillCatalog.levelSkillCount(state) == denominator && state.level == 2,
                    "numerical growth neither changes the classic denominator nor unlocks an integer level");
            before = after;
        }
        h.assertTrue(events[0] == 2, "two awards produce exactly two native Added events");
        h.assertTrue(state.cp == classicCp && state.overload == classicOverload, "high-power growth does not touch the classic resource ledger");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void successful_arc_uses_pre_award_exact_damage_fractional_cost_and_ceiled_cooldown(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(2); var state = AbilityStorage.get(p);
        f.earn(p, .5); f.cp(p, 37); p.setShiftKeyDown(true);
        Villager target = f.target(); var growth = HighPowerGrowth.values(p);
        BigDecimal hp = new BigDecimal(HighPowerCombat.healthText(target));
        double cp = ElectromasterPrototype.prototypeCp(p), classicCp = state.cp;
        double levelXp = state.levelExperience, skillXp = state.exp(HighPowerSkills.FORKED_ARC);
        long now = p.serverLevel().getGameTime();
        BigDecimal theoretical = ARC_COOLDOWN.value(growth.position());
        h.assertTrue(theoretical.stripTrailingZeros().scale() > 0 && growth.arcCost() > 0
                && growth.arcCost() != Math.floor(growth.arcCost()), "fixture reaches a genuinely fractional cost and theoretical cooldown");
        h.assertTrue(ElectromasterPrototype.castLearned(p), "learned native arc hits the actual ray-traced target");
        exact(h, new BigDecimal(HighPowerCombat.healthText(target)), hp.subtract(growth.damage()),
                "this cast applies its exact pre-award decimal D, never a rounded integer or newly earned D");
        sameDouble(h, ElectromasterPrototype.prototypeCp(p), cp - growth.arcCost(), "actual cast debits precisely its fractional positive CP cost");
        h.assertTrue(p.getPersistentData().getCompound(CP_KEY).getLong("next_cast") == now + growth.arcCooldown(),
                "native deadline uses the captured cooldown once");
        h.assertTrue(growth.arcCooldown() == ContinuousHighPowerGrowth.ceilCooldownTicks(theoretical)
                && state.cooldowns.getOrDefault(HighPowerSkills.FORKED_ARC, 0) == growth.arcCooldown(),
                "theoretical cooldown is ceiled only at the native scheduler and HUD boundary");
        h.assertTrue(state.levelExperience == (double) ((float) levelXp + .005F)
                && state.exp(HighPowerSkills.FORKED_ARC) == (double) ((float) skillXp + .005F),
                "one successful target hit awards only the unchanged .005 native XP");
        h.assertTrue(HighPowerGrowth.values(p).damage().compareTo(growth.damage()) > 0,
                "successful cast XP updates the next cast's smooth damage immediately");
        double paid = ElectromasterPrototype.prototypeCp(p), awarded = state.levelExperience;
        h.assertFalse(ElectromasterPrototype.cast(p), "same-clock replay remains rejected by the real native deadline");
        sameDouble(h, ElectromasterPrototype.prototypeCp(p), paid, "rejected cast has no second cost");
        h.assertTrue(state.levelExperience == awarded && state.cp == classicCp, "rejected cast adds no XP and neither cast charges classic CP");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void shared_skill_costs_stay_fractional_and_grounding_keeps_zero_cp_payment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(2); var state = AbilityStorage.get(p);
        f.earn(p, .5); f.cp(p, 37); var growth = HighPowerGrowth.values(p);
        double expected = 37, xp = state.levelExperience;
        String[] skills = {HighPowerSkills.POLARITY_TETHER, HighPowerSkills.CHARGE_DECOY, HighPowerSkills.CONDUCTIVE_CIRCUIT};
        double[] costs = {growth.tetherCost(), growth.lv2Cost(), growth.lv2Cost()};
        for (int i = 0; i < skills.length; i++) {
            h.assertTrue(costs[i] > 0 && costs[i] != Math.floor(costs[i]), "shared positive cost remains fractional for " + skills[i]);
            h.assertTrue(ElectromasterPrototype.trySpend(p, skills[i], costs[i], 1), "existing shared payment seam accepts " + skills[i]);
            expected -= costs[i];
            sameDouble(h, ElectromasterPrototype.prototypeCp(p), expected, "shared CP keeps the exact existing double subtraction");
            h.assertFalse(ElectromasterPrototype.trySpend(p, skills[i], costs[i], 1), "same-tick shared payment is not replayable");
        }
        h.assertTrue(ElectromasterPrototype.trySpend(p, HighPowerSkills.GROUNDING_RETURN, 0, growth.groundingCooldown()),
                "grounding retains its intentional zero CP entry cost");
        sameDouble(h, ElectromasterPrototype.prototypeCp(p), expected, "grounding cooldown cannot invent a CP charge or refund");
        h.assertTrue(growth.groundingCooldown() == ContinuousHighPowerGrowth.ceilCooldownTicks(GROUND_COOLDOWN.value(growth.position()))
                && state.cooldowns.getOrDefault(HighPowerSkills.GROUNDING_RETURN, 0) == growth.groundingCooldown(),
                "grounding uses its separately ceiled smooth scheduler duration");
        h.assertTrue(state.levelExperience == xp, "resource payment alone invents no successful-action XP");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void earned_growth_changes_only_the_following_real_regeneration_tick(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(2);
        f.cp(p, 37); Balances before = balances(p); f.earn(p, .5);
        sameBalances(h, p, before, "genuine XP increases capacity without an instant refill");
        var growth = HighPowerGrowth.values(p); long now = p.serverLevel().getGameTime();
        h.assertTrue(now > 0, "native GameTest clock has advanced beyond the missing-tag sentinel");
        ElectromasterPrototype.tick(new PlayerTickEvent.Post(p));
        sameDouble(h, ElectromasterPrototype.prototypeCp(p), 37 + growth.cpRecovery() / 20,
                "one native tick adds precisely the current fractional recovery rate divided by twenty");
        double recovered = ElectromasterPrototype.prototypeCp(p);
        ElectromasterPrototype.tick(new PlayerTickEvent.Post(p));
        sameDouble(h, ElectromasterPrototype.prototypeCp(p), recovered, "same native tick cannot regenerate twice");
        exact(h, new BigDecimal(HighPowerCombat.healthText(p)), before.hp(), "CP tick cannot refill exact health");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void rollback_preserves_earned_cp_capacity_and_balance_but_lowers_current_output(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(2); var state = AbilityStorage.get(p);
        f.earn(p, .75); f.cp(p, 550); var grown = HighPowerGrowth.values(p); Balances before = balances(p);
        String maxHp = HighPowerCombat.maxHealthText(p);
        state.setLevel(1); var rolledBack = HighPowerGrowth.values(p);
        exact(h, rolledBack.position(), BigDecimal.ONE, "current output follows the actual rollback");
        exact(h, rolledBack.earnedPosition(), grown.earnedPosition(), "earned high-water mark is retained");
        sameDouble(h, rolledBack.cpMaximum(), grown.cpMaximum(), "rollback retains earned CP capacity");
        sameBalances(h, p, before, "rollback retains all absolute balances, including CP above the LV1 anchor");
        h.assertTrue(rolledBack.damage().compareTo(grown.damage()) < 0 && rolledBack.cpRecovery() < grown.cpRecovery()
                && rolledBack.arcCost() > grown.arcCost() && rolledBack.tetherCost() > grown.tetherCost(),
                "lower current coordinate lowers output and recovery while restoring weaker-level costs");
        h.assertTrue(rolledBack.arcCost() == 10 && rolledBack.arcCooldown() == 10 && rolledBack.cpRecovery() == 10,
                "rollback uses the LV1 current-cost/rate anchor, not the earned high-water output");
        h.assertTrue(HighPowerCombat.maxHealthText(p).equals(maxHp) && ElectromasterPrototype.prototypeCp(p) > 200,
                "retained capacity never silently clamps HP or CP to lower-level anchors");
        ElectromasterPrototype.initializeBudget(p);
        sameBalances(h, p, before, "repeated initialization cannot refill or clamp an existing earned budget");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void category_switch_blocks_casts_and_regeneration_without_losing_stored_resources(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(2); var state = AbilityStorage.get(p);
        f.earn(p, .5); f.cp(p, 550); Balances before = balances(p); var earned = HighPowerGrowth.values(p);
        state.changeCategoryClassic("unsupported_test");
        h.assertFalse(ElectromasterPrototype.ready(p) || ElectromasterPrototype.cast(p) || ElectromasterPrototype.castLearned(p)
                || ElectromasterPrototype.trySpend(p, HighPowerSkills.FORKED_ARC, 1, 1), "another category cannot invoke high-power attacks or payments");
        ElectromasterPrototype.tick(new PlayerTickEvent.Post(p));
        sameDouble(h, p.getPersistentData().getCompound(CP_KEY).getDouble("cp"), 550, "disabled category receives no prototype regeneration or clamp");
        exact(h, new BigDecimal(HighPowerCombat.healthText(p)), before.hp(), "category switch preserves exact HP");
        exact(h, HighPowerCombat.shield(p), before.shield(), "category switch preserves exact shield");
        sameDouble(h, ElectromasterPrototype.prototypeCp(p), 0, "ineligible category exposes no spendable prototype budget");
        state.changeCategoryClassic(HighPowerSkills.CATEGORY); state.setLevel(1);
        state.learn(HighPowerSkills.FORKED_ARC); // Declared relearned-bit fixture; it is not a survival acquisition claim.
        ElectromasterPrototype.initializeBudget(p);
        sameBalances(h, p, before, "returning to lower-level electromaster restores visibility without refilling");
        sameDouble(h, HighPowerGrowth.values(p).cpMaximum(), earned.cpMaximum(), "category round trip retains earned CP high-water capacity");
        exact(h, HighPowerGrowth.values(p).position(), BigDecimal.ONE, "returned category still obeys actual low-level output");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void cached_snapshot_identity_is_stable_until_real_growth_coordinate_changes(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(2); var state = AbilityStorage.get(p);
        var before = HighPowerGrowth.values(p);
        for (int i = 0; i < 20; i++) {
            ElectromasterPrototype.prototypeCp(p);
            h.assertTrue(HighPowerGrowth.values(p) == before, "unchanged coordinate reuses the same evaluated curve snapshot");
        }
        state.setSkillExp(HighPowerSkills.FORKED_ARC, .125F);
        state.addExperience(HighPowerSkills.FORKED_ARC, 0);
        h.assertTrue(HighPowerGrowth.values(p) == before, "mastery-only and zero-XP events do not reevaluate unchanged numerical progress");
        state.addExperience(HighPowerSkills.FORKED_ARC, .00001);
        var after = HighPowerGrowth.values(p);
        h.assertTrue(after != before && after.position().compareTo(before.position()) > 0,
                "genuine positive native progression refreshes the cached values immediately");
        h.assertTrue(HighPowerGrowth.values(p) == after, "subsequent reads reuse the refreshed snapshot");
        f.earn(p, 1); var full = HighPowerGrowth.values(p);
        exact(h, full.position(), BigDecimal.valueOf(3), "fixture reaches the capped LV2 progress coordinate");
        double levelXp = state.levelExperience;
        state.addExperience(HighPowerSkills.FORKED_ARC, .005);
        h.assertTrue(state.levelExperience == (double) ((float) levelXp + .005F), "beyond-threshold positive XP still follows classic rules");
        h.assertTrue(HighPowerGrowth.values(p) == full,
                "positive XP at unchanged capped current and earned coordinates reuses the existing snapshot");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void paid_level_wrapper_keeps_lv2_full_and_lv3_zero_numerically_identical(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(2); var state = AbilityStorage.get(p);
        f.earn(p, 1); f.cp(p, 37); var full = HighPowerGrowth.values(p); Balances before = balances(p);
        h.assertTrue(state.level == 2 && state.levelProgress(SkillCatalog.levelSkillCount(state)) == 1,
                "ordinary positive native XP reaches the unchanged LV2 threshold");
        exact(h, full.position(), BigDecimal.valueOf(3), "full LV2 already has the continuous LV3 numerical anchor");
        // Isolated finite NORMAL developer fixture: actual production action, paid process and wrapper,
        // but no claim that a machine structure or its initial energy was acquired in survival.
        FiniteDeveloper developer = new FiniteDeveloper();
        var action = HighPowerProgression.leveling(p, DevelopmentActions.level(state, DevelopmentActions.CategoryItems.NONE, ignored -> 0));
        var process = new DevelopmentProcess(); process.start(developer, action);
        int ticks = action.stimulations() * developer.type().ticksPerStimulation();
        for (int i = 0; i < ticks - 1; i++) process.tick();
        h.assertTrue(state.level == 2 && process.isDeveloping(), "paid developer cannot unlock one tick early");
        sameBalances(h, p, before, "stimulation work itself grants no high-power refill");
        process.tick(); var zero = HighPowerGrowth.values(p);
        h.assertTrue(process.state() == DevelopmentProcess.State.DONE && state.level == 3 && state.levelExperience == 0
                && HighPowerCombat.earnedLevel(p) == 3, "actual wrapper certifies only successful finite developer completion");
        h.assertTrue(full.equals(zero), "LV2 p=1 and LV3 p=0 have identical D, capacities, recovery, all costs and scheduled cooldowns");
        sameBalances(h, p, before, "integer unlock does not jump or refill any absolute high-power resource");
        sameDouble(h, developer.energy(), 20000 - developer.type().actualConsumption(15), "all fifteen NORMAL stimulations paid finite IF");
        h.succeed();
    }

    private record Balances(BigDecimal hp, BigDecimal shield, double cp) {}
    private static Balances balances(ServerPlayer p) {
        return new Balances(new BigDecimal(HighPowerCombat.healthText(p)), HighPowerCombat.shield(p), ElectromasterPrototype.prototypeCp(p));
    }
    private static void sameBalances(GameTestHelper h, ServerPlayer p, Balances expected, String message) {
        exact(h, new BigDecimal(HighPowerCombat.healthText(p)), expected.hp(), message + ": HP");
        exact(h, HighPowerCombat.shield(p), expected.shield(), message + ": shield");
        sameDouble(h, ElectromasterPrototype.prototypeCp(p), expected.cp(), message + ": CP");
    }
    private static void exact(GameTestHelper h, BigDecimal actual, BigDecimal expected, String message) {
        h.assertTrue(actual.compareTo(expected) == 0, message + "; actual=" + actual + " expected=" + expected);
    }
    private static void sameDouble(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Double.compare(actual, expected) == 0, message + "; actual=" + actual + " expected=" + expected);
    }
    private static final class FiniteDeveloper implements DevelopmentProcess.Developer {
        private double energy = 20000;
        public DeveloperType type() { return DeveloperType.NORMAL; }
        public boolean tryPullEnergy(double amount) { if (amount > energy) return false; energy -= amount; return true; }
        public double energy() { return energy; }
        public double maxEnergy() { return 50000; }
    }
    private static final class Fixture {
        final GameTestHelper h;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>();
        boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        ServerPlayer player(int level) {
            var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-Growth-CP]"));
            players.add(p); p.setNoGravity(true); p.getAbilities().instabuild = false;
            var at = h.absoluteVec(new Vec3(5.5, 2, 4.5)); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0);
            var state = AbilityStorage.get(p); state.changeCategoryClassic(HighPowerSkills.CATEGORY); state.setLevel(level);
            for (var skill : HighPowerSkills.ALL) if (skill.category().equals(state.category) && skill.level() <= level) state.learn(skill.id());
            state.setActivateState(true); state.overloadFine = true;
            h.assertTrue(HighPowerCombat.enroll(p, level), "declared supported native enrollment: " + HighPowerCombat.unsupportedReason(p));
            ElectromasterPrototype.initializeBudget(p); return p;
        }
        void cp(ServerPlayer p, double amount) { p.getPersistentData().getCompound(CP_KEY).putDouble("cp", amount); }
        void earn(ServerPlayer p, double fraction) {
            var state = AbilityStorage.get(p);
            state.addExperience(HighPowerSkills.FORKED_ARC, ClassicRules.levelThreshold(state.level, SkillCatalog.levelSkillCount(state)) * fraction);
        }
        Villager target() {
            var target = new Villager(EntityType.VILLAGER, h.getLevel());
            target.setPos(h.absoluteVec(new Vec3(5.5, 2, 8.5))); target.setNoAi(true); target.setNoGravity(true);
            h.getLevel().addFreshEntity(target); entities.add(target);
            h.assertTrue(HighPowerCombat.enroll(target, 5), "declared high-capacity native target"); return target;
        }
        void observe(Consumer<SkillExpAddedEvent> observer) { hooks.add(observer); NeoForge.EVENT_BUS.addListener(SkillExpAddedEvent.class, observer); }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (var p : players) { HighPowerGrowth.invalidate(p); AbilityStorage.remove(p); p.discard(); }
            entities.forEach(Entity::discard);
        }
    }
}
