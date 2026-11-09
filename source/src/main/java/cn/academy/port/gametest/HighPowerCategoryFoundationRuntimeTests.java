package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.api.SkillExpAddedEvent;
import cn.academy.port.core.ClassicRules;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import cn.academy.port.highpower.core.HighPowerLevel;
import cn.academy.port.highpower.core.ScatterPlateShares;
import com.mojang.authlib.GameProfile;
import java.math.BigDecimal;
import java.math.MathContext;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.living.LivingHealEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native category and FoodData transitions; initial injury/balance/pose are declared fixtures. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerCategoryFoundationRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_category_foundation";
    private static final net.minecraft.server.level.TicketType<UUID> FIXTURE_TICKET =
            net.minecraft.server.level.TicketType.create("academy_tp_category_fixture", UUID::compareTo);
    private HighPowerCategoryFoundationRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void em_md_tp_paid_relearning_and_save_reload_preserve_q_Q_pools_and_physical_deadlines(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (String original : List.of(HighPowerCategories.ELECTROMASTER, HighPowerCategories.MELTDOWNER)) {
            var p = f.player(true, 4, HighPowerCategories.ELECTROMASTER); var s = AbilityStorage.get(p);
            h.assertTrue(HighPowerCombat.spendShield(p, BigDecimal.ONE), "declared depleted shield fixture");
            if (!original.equals(s.category)) { s.changeCategoryClassic(original); s.learn(HighPowerCategories.recoveryRoot(original)); }
            s.addExperience(HighPowerCategories.recoveryRoot(original), ClassicRules.levelThreshold(4, SkillCatalog.levelSkillCount(s)) * .25);
            var growth = HighPowerGrowth.values(p); double xp = s.levelExperience;
            f.hp(p, new BigDecimal(HighPowerCombat.maxHealthText(p)).subtract(BigDecimal.ONE));
            String hp = HighPowerCombat.healthText(p), max = HighPowerCombat.maxHealthText(p), shield = HighPowerCombat.shieldText(p);
            var budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            budget.putDouble("cp", 37); budget.putLong("next_cast", p.level().getGameTime() + 100);
            budget.putLong("next_dual_point", p.level().getGameTime() + 100);
            s.presets.edit(0, 0, HighPowerCategories.recoveryRoot(original), s::learned);
            s.changeCategoryClassic(HighPowerCategories.TELEPORTER);
            h.assertTrue(s.level == 4 && s.levelExperience == xp && s.experience.isEmpty() && s.presets.currentSkill(0).isEmpty(), "genuine TP switch preserves native progression and clears learned/presets");
            h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().status().equals("unlearned"), "TP extension requires learned313");
            var process = new DevelopmentProcess(); var battery = new PortableBattery(2340);
            var root = SkillCatalog.find(HighPowerCategories.TELEPORTER, HighPowerSkills.SAFE_LANDING_PROBE).orElseThrow();
            process.start(battery, HighPowerProgression.learning(p, s, DevelopmentActions.skill(s, root)));
            for (int tick = 0; tick < 78; tick++) process.tick();
            h.assertTrue(process.state() == DevelopmentProcess.State.DONE && battery.energy == 0 && s.learned(root.id()), "existing ledger learns313 through all finite paid work");
            HighPowerResources.initializeBudget(p); s.setActivateState(true);
            h.assertTrue(HighPowerResources.cp(p) == 37 && s.levelExperience == xp, "TP paid learning and reinitialization do not refill shared CP or award XP");
            equal(h, HighPowerGrowth.values(p).position(), growth.position(), "TP keeps effective q");
            equal(h, HighPowerGrowth.values(p).earnedPosition(), growth.earnedPosition(), "TP keeps earned Q");
            h.assertTrue(hp.equals(HighPowerCombat.healthText(p)) && max.equals(HighPowerCombat.maxHealthText(p)) && shield.equals(HighPowerCombat.shieldText(p)), "TP preserves existing HP, capacity and shield injury");
            h.assertTrue(HighPowerResources.trySpend(p, root.id(), 1.25, 25), "approved313 resource seam debits the existing shared budget");
            long deadline = budget.getLong("next_safe_landing_probe");
            s.changeCategoryClassic(original); s.learn(HighPowerCategories.recoveryRoot(original)); s.setActivateState(true);
            String oldSkill = HighPowerCategories.recoveryRoot(original);
            h.assertFalse(HighPowerResources.trySpend(p, oldSkill, 1, 2), "old EM/MD physical deadline remains after TP return");
            s.changeCategoryClassic(HighPowerCategories.TELEPORTER); s.learn(root.id()); s.setActivateState(true);
            h.assertFalse(HighPowerResources.trySpend(p, root.id(), 1, 2), "classic cooldown clearing cannot clear the313 physical deadline");
            h.assertTrue(HighPowerResources.cp(p) == 35.75 && budget.getLong("next_safe_landing_probe") == deadline, "category/relearning neither refunds nor rewrites313 deadline");
            AbilityStorage.save(p); CompoundTag saved = new CompoundTag(); p.saveWithoutId(saved); p.load(saved);
            AbilityStorage.remove(p); s = AbilityStorage.get(p);
            h.assertTrue(s.learned(root.id()) && HighPowerResources.cp(p) == 35.75
                    && p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_safe_landing_probe") == deadline, "native save/reload retains learned313, depleted shared CP and physical deadline");
            h.assertFalse(HighPowerResources.trySpend(p, root.id(), 1, 2), "native reload cannot clear the313 physical deadline");
            equal(h, HighPowerGrowth.values(p).position(), growth.position(), "reload retains effective q");
            equal(h, HighPowerGrowth.values(p).earnedPosition(), growth.earnedPosition(), "reload retains earned Q");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=75)
    public static void tp_ordinary_native_damage_ignores_retained_em_md_shields_and_keeps_all_twelve_children(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.connectedPlayer(5, HighPowerCategories.MELTDOWNER); var s = AbilityStorage.get(p);
        s.learn(HighPowerSkills.SCATTER_PLATES); s.learn(HighPowerSkills.DISSIPATION_CURTAIN); s.learn(HighPowerSkills.ZERO_GAP_ARMOR);
        var combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); combat.putString("shield", "28");
        var scatter = ScatterPlateShares.restore(new BigDecimal("28"), new BigDecimal(HighPowerCombat.maxShieldText(p)), List.of(new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("8")));
        var scatterTag = new CompoundTag(); scatter.toDecimalMap().forEach(scatterTag::putString);
        combat.put(ScatterPlateStorage.KEY, scatterTag); combat.putBoolean(ScatterPlateStorage.INITIALIZED_KEY, true);
        var reserve = new CompoundTag(); reserve.putInt("version", 2); reserve.putString("amount", "29");
        reserve.putString("capacity", HighPowerCombat.maxShieldText(p)); reserve.putString("lower", "13"); reserve.putString("upper", "16"); combat.put(MagneticDomainReserve.KEY, reserve);
        h.assertTrue(ZeroGapArmorStorage.ensureLearned(p), "declared existing212/242/252 parent-child fixture initializes once");
        var capture = ZeroGapArmorStorage.capture(p).orElseThrow();
        var contact = capture.armor().orElseThrow().contact(0, new BigDecimal("0.5"), new BigDecimal("0.5"), BigDecimal.ZERO);
        h.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepare(capture, contact.transaction())), "fixture has unequal child currents after one finite debit");
        var children = ZeroGapArmorStorage.read(p).orElseThrow().toDecimalMap();
        var parents = ScatterPlateStorage.read(p).orElseThrow().toDecimalMap(); var savedReserve = combat.getCompound(MagneticDomainReserve.KEY).copy();
        HighPowerCombat.shieldActive(p, true); s.changeCategoryClassic(HighPowerCategories.TELEPORTER); s.learn(HighPowerSkills.SAFE_LANDING_PROBE);
        h.assertFalse(HighPowerCombat.shieldActive(p) || HighPowerCombat.spendShield(p, BigDecimal.ONE) || MagneticDomainReserve.available(p), "TP receives no retained EM bracer/domain benefit");
        h.assertTrue(HighPowerCombat.curtainReserve(p).signum() == 0 && HighPowerCombat.zeroGapReserve(p).signum() == 0, "TP exposes no MD reserves");
        HighPowerCombat.tickShield(p);
        h.assertTrue(ZeroGapArmorStorage.read(p).orElseThrow().toDecimalMap().equals(children), "TP shield tick never recovers retained children");
        h.runAtTickTime(61, () -> {
            p.doTick(); p.invulnerableTime = 0;
            String hp = HighPowerCombat.healthText(p); BigDecimal shield = HighPowerCombat.shield(p);
            h.assertTrue(p.hurt(p.damageSources().magic(), .5F), "ordinary native half-point hit accepted in TP"); f.flush(p);
            equal(h, new BigDecimal(HighPowerCombat.healthText(p)), new BigDecimal(hp).subtract(new BigDecimal("0.5")), "TP exact HP takes ordinary native damage");
            equal(h, HighPowerCombat.shield(p), shield, "ordinary TP damage spends no stored base shield");
            h.assertTrue(ZeroGapArmorStorage.read(p).orElseThrow().toDecimalMap().equals(children)
                    && ScatterPlateStorage.read(p).orElseThrow().toDecimalMap().equals(parents)
                    && combat.getCompound(MagneticDomainReserve.KEY).equals(savedReserve), "ordinary TP damage retains every parent and twelve exact children");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void tp_food_only_extension_retains_hunger_gamerule_pause_and_other_heal_units(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.player(true, 5, HighPowerCategories.TELEPORTER);
        BigDecimal half = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO); f.hp(p, half); f.food(p, 17, 0); f.ticks(p, 100);
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "TP food below18 cannot recover");
        var rule = p.level().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION); boolean old = rule.get();
        try { rule.set(false, p.server); f.food(p, 20, 6); f.ticks(p, 20);
            equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "naturalRegeneration guards TP extension");
        } finally { rule.set(old, p.server); }
        HighPowerNaturalRecovery.offensiveAction(p); f.ticks(p, 20);
        h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().status().equals("paused"), "actual TP offense retains shared combat pause");
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "paused TP cannot receive proportional food heal");
        p.heal(4); f.flush(p);
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half.add(BigDecimal.valueOf(4)), "other healing stays four exact HP in TP");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void em_md_vector_paid_relearning_and_save_reload_preserve_q_Q_pools_and_physical_deadlines(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (String original : List.of(HighPowerCategories.ELECTROMASTER, HighPowerCategories.MELTDOWNER, HighPowerCategories.TELEPORTER)) {
            var p = f.player(true, 4, HighPowerCategories.ELECTROMASTER); var s = AbilityStorage.get(p);
            h.assertTrue(HighPowerCombat.spendShield(p, BigDecimal.ONE), "declared depleted shield fixture");
            if (!original.equals(s.category)) { s.changeCategoryClassic(original); s.learn(HighPowerCategories.recoveryRoot(original)); }
            s.addExperience(HighPowerCategories.recoveryRoot(original), ClassicRules.levelThreshold(4, SkillCatalog.levelSkillCount(s)) * .25);
            var growth = HighPowerGrowth.values(p); double xp = s.levelExperience;
            f.hp(p, new BigDecimal(HighPowerCombat.maxHealthText(p)).subtract(BigDecimal.ONE));
            String hp = HighPowerCombat.healthText(p), max = HighPowerCombat.maxHealthText(p), shield = HighPowerCombat.shieldText(p);
            var budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
            budget.putDouble("cp", 37); budget.putLong("next_cast", p.level().getGameTime() + 100);
            budget.putLong("next_dual_point", p.level().getGameTime() + 100);
            budget.putLong("next_" + HighPowerCategories.recoveryRoot(original), p.level().getGameTime() + 100);
            s.presets.edit(0, 0, HighPowerCategories.recoveryRoot(original), s::learned);
            s.changeCategoryClassic(HighPowerCategories.VECMANIP);
            h.assertTrue(s.level == 4 && s.levelExperience == xp && s.experience.isEmpty() && s.presets.currentSkill(0).isEmpty(), "genuine VM switch preserves native progression and clears learned/presets");
            h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().status().equals("unlearned"), "VM extension requires learned411");
            var process = new DevelopmentProcess(); var battery = new PortableBattery(2340);
            var root = SkillCatalog.find(HighPowerCategories.VECMANIP, HighPowerSkills.DEFLECTION_STRIKE).orElseThrow();
            process.start(battery, HighPowerProgression.learning(p, s, DevelopmentActions.skill(s, root)));
            for (int tick = 0; tick < 78; tick++) process.tick();
            h.assertTrue(process.state() == DevelopmentProcess.State.DONE && battery.energy == 0 && s.learned(root.id()), "existing ledger learns411 through all finite paid work");
            HighPowerResources.initializeBudget(p); s.setActivateState(true);
            h.assertTrue(HighPowerResources.cp(p) == 37 && s.levelExperience == xp, "VM paid learning and reinitialization do not refill shared CP or award XP");
            equal(h, HighPowerGrowth.values(p).position(), growth.position(), "VM keeps effective q");
            equal(h, HighPowerGrowth.values(p).earnedPosition(), growth.earnedPosition(), "VM keeps earned Q");
            h.assertTrue(hp.equals(HighPowerCombat.healthText(p)) && max.equals(HighPowerCombat.maxHealthText(p)) && shield.equals(HighPowerCombat.shieldText(p)), "VM preserves existing HP, capacity and shield injury");
            h.assertTrue(HighPowerResources.trySpend(p, root.id(), 1.25, 25), "approved411 resource seam debits the existing shared budget");
            long deadline = budget.getLong("next_deflection_strike");
            s.changeCategoryClassic(original); s.learn(HighPowerCategories.recoveryRoot(original)); s.setActivateState(true);
            String oldSkill = HighPowerCategories.recoveryRoot(original);
            h.assertFalse(HighPowerResources.trySpend(p, oldSkill, 1, 2), "old EM/MD physical deadline remains after VM return");
            s.changeCategoryClassic(HighPowerCategories.VECMANIP); s.learn(root.id()); s.setActivateState(true);
            h.assertFalse(HighPowerResources.trySpend(p, root.id(), 1, 2), "classic cooldown clearing cannot clear the411 physical deadline");
            h.assertTrue(HighPowerResources.cp(p) == 35.75 && budget.getLong("next_deflection_strike") == deadline, "category/relearning neither refunds nor rewrites411 deadline");
            AbilityStorage.save(p); CompoundTag saved = new CompoundTag(); p.saveWithoutId(saved); p.load(saved);
            AbilityStorage.remove(p); s = AbilityStorage.get(p);
            h.assertTrue(s.learned(root.id()) && HighPowerResources.cp(p) == 35.75
                    && p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_deflection_strike") == deadline, "native save/reload retains learned411, depleted shared CP and physical deadline");
            h.assertFalse(HighPowerResources.trySpend(p, root.id(), 1, 2), "native reload cannot clear the411 physical deadline");
            equal(h, HighPowerGrowth.values(p).position(), growth.position(), "reload retains effective q");
            equal(h, HighPowerGrowth.values(p).earnedPosition(), growth.earnedPosition(), "reload retains earned Q");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=75)
    public static void vector_ordinary_native_damage_ignores_retained_em_md_shields_and_keeps_all_twelve_children(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.connectedPlayer(5, HighPowerCategories.MELTDOWNER); var s = AbilityStorage.get(p);
        s.learn(HighPowerSkills.SCATTER_PLATES); s.learn(HighPowerSkills.DISSIPATION_CURTAIN); s.learn(HighPowerSkills.ZERO_GAP_ARMOR);
        var combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); combat.putString("shield", "28");
        var scatter = ScatterPlateShares.restore(new BigDecimal("28"), new BigDecimal(HighPowerCombat.maxShieldText(p)), List.of(new BigDecimal("10"), new BigDecimal("10"), new BigDecimal("8")));
        var scatterTag = new CompoundTag(); scatter.toDecimalMap().forEach(scatterTag::putString);
        combat.put(ScatterPlateStorage.KEY, scatterTag); combat.putBoolean(ScatterPlateStorage.INITIALIZED_KEY, true);
        var reserve = new CompoundTag(); reserve.putInt("version", 2); reserve.putString("amount", "29");
        reserve.putString("capacity", HighPowerCombat.maxShieldText(p)); reserve.putString("lower", "13"); reserve.putString("upper", "16"); combat.put(MagneticDomainReserve.KEY, reserve);
        h.assertTrue(ZeroGapArmorStorage.ensureLearned(p), "declared existing212/242/252 parent-child fixture initializes once");
        var capture = ZeroGapArmorStorage.capture(p).orElseThrow();
        var contact = capture.armor().orElseThrow().contact(0, new BigDecimal("0.5"), new BigDecimal("0.5"), BigDecimal.ZERO);
        h.assertTrue(ZeroGapArmorStorage.apply(ZeroGapArmorStorage.prepare(capture, contact.transaction())), "fixture has unequal child currents after one finite debit");
        var children = ZeroGapArmorStorage.read(p).orElseThrow().toDecimalMap();
        var parents = ScatterPlateStorage.read(p).orElseThrow().toDecimalMap(); var savedReserve = combat.getCompound(MagneticDomainReserve.KEY).copy();
        HighPowerCombat.shieldActive(p, true); s.changeCategoryClassic(HighPowerCategories.VECMANIP); s.learn(HighPowerSkills.DEFLECTION_STRIKE);
        h.assertFalse(HighPowerCombat.shieldActive(p) || HighPowerCombat.spendShield(p, BigDecimal.ONE) || MagneticDomainReserve.available(p), "VM receives no retained EM bracer/domain benefit");
        h.assertTrue(HighPowerCombat.curtainReserve(p).signum() == 0 && HighPowerCombat.zeroGapReserve(p).signum() == 0, "VM exposes no MD reserves");
        HighPowerCombat.tickShield(p);
        h.assertTrue(ZeroGapArmorStorage.read(p).orElseThrow().toDecimalMap().equals(children), "VM shield tick never recovers retained children");
        h.runAtTickTime(61, () -> {
            p.doTick(); p.invulnerableTime = 0;
            String hp = HighPowerCombat.healthText(p); BigDecimal shield = HighPowerCombat.shield(p);
            h.assertTrue(p.hurt(p.damageSources().magic(), .5F), "ordinary native half-point hit accepted in VM"); f.flush(p);
            equal(h, new BigDecimal(HighPowerCombat.healthText(p)), new BigDecimal(hp).subtract(new BigDecimal("0.5")), "VM exact HP takes ordinary native damage");
            equal(h, HighPowerCombat.shield(p), shield, "ordinary VM damage spends no stored base shield");
            h.assertTrue(ZeroGapArmorStorage.read(p).orElseThrow().toDecimalMap().equals(children)
                    && ScatterPlateStorage.read(p).orElseThrow().toDecimalMap().equals(parents)
                    && combat.getCompound(MagneticDomainReserve.KEY).equals(savedReserve), "ordinary VM damage retains every parent and twelve exact children");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void vector_food_only_extension_retains_hunger_gamerule_pause_and_other_heal_units(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.player(true, 5, HighPowerCategories.VECMANIP);
        BigDecimal half = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO); f.hp(p, half); f.food(p, 17, 0); f.ticks(p, 100);
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "VM food below18 cannot recover");
        var rule = p.level().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION); boolean old = rule.get();
        try { rule.set(false, p.server); f.food(p, 20, 6); f.ticks(p, 20);
            equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "naturalRegeneration guards VM extension");
        } finally { rule.set(old, p.server); }
        HighPowerNaturalRecovery.offensiveAction(p); f.ticks(p, 20);
        h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().status().equals("paused"), "actual VM offense retains shared combat pause");
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "paused VM cannot receive proportional food heal");
        p.heal(4); f.flush(p);
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half.add(BigDecimal.valueOf(4)), "other healing stays four exact HP in VM");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void learned_vector_food_matches_native_fractional_control_and_preserves_shadow_timer_on_switch(GameTestHelper h) {
        Fixture f = new Fixture(h); var control = f.player(false, 5, ""); var p = f.player(true, 5, HighPowerCategories.ELECTROMASTER);
        control.setHealth(19.5F); f.hp(p, cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().multiply(new BigDecimal(".975")));
        f.food(control, 20, .3F); f.food(p, 20, .3F); f.ticks(control, 53); f.ticks(p, 53);
        var shadow = p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY).copy();
        String hp = HighPowerCombat.healthText(p); int timer = ((NativeFoodRecoveryAccess)p.getFoodData()).academy$naturalRecoveryTimer();
        AbilityStorage.get(p).changeCategoryClassic(HighPowerCategories.VECMANIP);
        AbilityStorage.get(p).learn(HighPowerSkills.DEFLECTION_STRIKE);
        h.assertTrue(hp.equals(HighPowerCombat.healthText(p)) && shadow.equals(p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY))
                && timer == ((NativeFoodRecoveryAccess)p.getFoodData()).academy$naturalRecoveryTimer(), "switch and root learning do not heal or rewrite float shadow/timer");
        int ticks = 53;
        while (control.getHealth() < 20 && ticks < 200) {
            f.ticks(control, 1); f.ticks(p, 1); ticks++; f.sameFood(control, p);
            h.assertTrue((control.getHealth() == 20) == (new BigDecimal(HighPowerCombat.healthText(p)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) == 0),
                    "VM reaches exact full health on the same native fractional pulse at " + ticks);
        }
        h.assertTrue(ticks == 110, "fractional native reference completes at tick 110 through the category transition");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void vector_root_gate_food_gamerule_and_potion_units_remain_native(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.player(true, 5, HighPowerCategories.VECMANIP); var s = AbilityStorage.get(p);
        BigDecimal half = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO); f.hp(p, half); f.food(p, 20, 6);
        s.unlearn(HighPowerSkills.DEFLECTION_STRIKE);
        h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().status().equals("unlearned"), "unlearned VM root has no proportional food extension");
        f.ticks(p, 10); f.flush(p); equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half.add(BigDecimal.ONE), "unlearned native food remains one exact point");
        s.learn(HighPowerSkills.DEFLECTION_STRIKE); f.hp(p, half); f.food(p, 17, 0); f.ticks(p, 100);
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "food below 18 cannot recover VM HP");
        var rule = p.level().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION); boolean old = rule.get();
        try { rule.set(false, p.server); f.food(p, 20, 6); f.ticks(p, 20);
            equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "vanilla gamerule disables proportional recovery");
        } finally { rule.set(old, p.server); }
        p.heal(4); f.flush(p); MobEffects.REGENERATION.value().applyEffectTick(p, 0); f.flush(p);
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half.add(BigDecimal.valueOf(5)), "item-style heal and regeneration remain 4 plus 1 exact HP");
        h.succeed();
    }

    private static final class PortableBattery implements DevelopmentProcess.Developer {
        double energy; PortableBattery(double energy) { this.energy = energy; }
        public DeveloperType type() { return DeveloperType.PORTABLE; }
        public double energy() { return energy; }
        public double maxEnergy() { return 10000; }
        public boolean tryPullEnergy(double amount) { if (energy < amount) { energy = 0; return false; } energy -= amount; return true; }
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void reentrant_award_category_switch_never_reinterprets_old_award(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.player(true, 1, HighPowerCategories.ELECTROMASTER); var state = AbilityStorage.get(p);
        state.addExperience(HighPowerSkills.FORKED_ARC, ClassicRules.levelThreshold(1, 2) * .25);
        BigDecimal certified = HighPowerCombat.earnedGrowthPosition(p);
        String hp = HighPowerCombat.healthText(p), shield = HighPowerCombat.shieldText(p);
        f.hook(new AwardSwitch(p));
        state.addExperience(HighPowerSkills.FORKED_ARC, .001);
        h.assertTrue(state.category.equals(HighPowerCategories.MELTDOWNER) && !state.learned(HighPowerSkills.FORKED_ARC), "actual native award callback changes category before LOWEST certification");
        equal(h, HighPowerCombat.earnedGrowthPosition(p), certified, "old EM award cannot certify MD denominator after a callback");
        state.learn(HighPowerSkills.DUAL_POINT);
        equal(h, HighPowerGrowth.values(p).position(), certified, "unawarded denominator change remains bounded by earned capacity");
        h.assertTrue(hp.equals(HighPowerCombat.healthText(p)) && shield.equals(HighPowerCombat.shieldText(p)), "reentrant switch never refills HP/shield");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void deferred_growth_captured_inside_native_heal_is_discarded_after_category_change(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.player(true, 1, HighPowerCategories.ELECTROMASTER);
        BigDecimal half = HighPowerLevel.LV1.maxHealth().divide(BigDecimal.TWO); f.hp(p, half); f.food(p, 20, 6);
        f.hook(new HealAwardSwitch(p)); f.ticks(p, 10); f.flush(p);
        h.assertTrue(AbilityStorage.get(p).category.equals(HighPowerCategories.MELTDOWNER), "native heal callback changed the active category after scheduling earned growth");
        equal(h, HighPowerCombat.earnedGrowthPosition(p), BigDecimal.ONE, "deferred old-category award is never committed in the new category");
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "canceled native food pulse and category change never heal");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void em_md_roundtrip_keeps_native_level_progress_injury_cp_deadlines_and_current_denominator(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.player(true, 4, HighPowerCategories.ELECTROMASTER); var s = AbilityStorage.get(p);
        f.hp(p, HighPowerLevel.LV4.maxHealth().subtract(BigDecimal.ONE));
        h.assertTrue(HighPowerCombat.spendShield(p, BigDecimal.ONE), "fixture spends one earned shield point");
        s.addExperience(HighPowerSkills.FORKED_ARC, ClassicRules.levelThreshold(4, SkillCatalog.levelSkillCount(s)) * .5);
        var em = HighPowerGrowth.values(p); double xp = s.levelExperience;
        String health = HighPowerCombat.healthText(p), maximum = HighPowerCombat.maxHealthText(p), shield = HighPowerCombat.shieldText(p);
        var data = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY);
        data.putDouble("cp", 37); data.putLong("next_cast", p.level().getGameTime() + 100);
        p.getPersistentData().put(HighPowerResources.DATA_KEY, data);
        s.presets.edit(0, 0, HighPowerSkills.FORKED_ARC, s::learned);
        s.changeCategoryClassic(HighPowerCategories.MELTDOWNER);
        h.assertTrue(s.level == 4 && s.levelExperience == xp && s.experience.isEmpty() && s.presets.currentSkill(0).isEmpty(),
                "native switch preserves actual level and XP while clearing learned skills and presets");
        h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().status().equals("unlearned"), "MD recovery requires learned NEW211");
        s.learn(HighPowerSkills.DUAL_POINT); s.setActivateState(true);
        var md = HighPowerGrowth.values(p);
        h.assertTrue(SkillCatalog.levelSkillCount(s) == 3 && md.position().compareTo(em.position()) == 0,
                "MD retains its own three-skill native denominator while effective q stays continuous");
        equal(h, md.position(), em.position(), "category switch preserves effective q independently of native fraction");
        h.assertTrue(md.cpMaximum() == em.cpMaximum() && md.cpRecovery() == em.cpRecovery() && md.damage().compareTo(em.damage()) == 0,
                "switching changes neither earned capacity nor current output/recovery");
        h.assertFalse(ElectromasterPrototype.ready(p) || ElectromasterPrototype.trySpend(p, HighPowerSkills.DUAL_POINT, 1, 2),
                "old EM controller seams remain closed to MD");
        h.assertTrue(HighPowerResources.trySpend(p, HighPowerSkills.DUAL_POINT, 1.25, 25), "MD debits the one shared CP ledger");
        double paid = HighPowerResources.cp(p); long deadline = data.getLong("next_dual_point");
        h.assertTrue(paid == 35.75 && deadline == p.level().getGameTime() + 25, "one finite debit and persisted deadline");
        s.changeCategoryClassic(HighPowerCategories.ELECTROMASTER); s.learn(HighPowerSkills.FORKED_ARC);
        ElectromasterPrototype.initializeBudget(p);
        h.assertTrue(s.level == 4 && s.levelExperience == xp && HighPowerResources.cp(p) == paid,
                "return and relearning cannot reset native progression or CP");
        h.assertFalse(ElectromasterPrototype.trySpend(p, HighPowerSkills.FORKED_ARC, 1, 2), "old EM deadline survives the category roundtrip");
        s.changeCategoryClassic(HighPowerCategories.MELTDOWNER); s.learn(HighPowerSkills.DUAL_POINT);
        HighPowerResources.initializeBudget(p);
        h.assertFalse(HighPowerResources.trySpend(p, HighPowerSkills.DUAL_POINT, 1, 2), "MD cooldown cannot be cleared through native category cooldown reset");
        h.assertTrue(data.getLong("next_dual_point") == deadline && HighPowerResources.cp(p) == paid,
                "retry and reinitialization neither refund nor refill");
        h.assertTrue(health.equals(HighPowerCombat.healthText(p)) && maximum.equals(HighPowerCombat.maxHealthText(p))
                && shield.equals(HighPowerCombat.shieldText(p)), "all category and learning steps preserve achieved capacity and exact injury");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void one_cp_tick_survives_same_tick_swaps_unknown_categories_and_em_defenses_stay_closed(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.player(true, 5, HighPowerCategories.ELECTROMASTER); var s = AbilityStorage.get(p);
        h.assertTrue(HighPowerCombat.spendShield(p, BigDecimal.ONE), "initial exact shield expenditure");
        var combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        combat.putLong("lastShieldSpend", p.level().getGameTime() - 100);
        HighPowerCombat.shieldActive(p, true);
        var data = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); data.putDouble("cp", 37);
        h.assertTrue(HighPowerResources.tick(p), "first category owns the shared recovery tick"); double recovered = HighPowerResources.cp(p);
        var shield = HighPowerCombat.shield(p);
        s.changeCategoryClassic(HighPowerCategories.MELTDOWNER); s.learn(HighPowerSkills.DUAL_POINT);
        h.assertFalse(HighPowerResources.tick(p), "same-clock MD tick cannot duplicate EM CP recovery");
        ElectromasterPrototype.tick(new PlayerTickEvent.Post(p));
        h.assertTrue(HighPowerResources.cp(p) == recovered, "existing subscriber shares the exact same tick guard");
        h.assertFalse(HighPowerCombat.shieldActive(p) || HighPowerCombat.spendShield(p, BigDecimal.ONE)
                || MagneticDomainReserve.available(p), "MD cannot access EM bracer or reserve benefits");
        HighPowerCombat.tickShield(p);
        h.assertTrue(HighPowerCombat.shield(p).compareTo(shield) == 0, "MD never regenerates stored EM shield");
        s.changeCategoryClassic(HighPowerCategories.VECMANIP); s.learn(HighPowerSkills.DEFLECTION_STRIKE); s.setActivateState(true);
        h.assertTrue(HighPowerResources.ready(p), "explicit vector admission exposes the same shared CP ledger");
        h.assertFalse(HighPowerResources.tick(p), "same-clock vector transition cannot duplicate CP recovery");
        h.assertTrue(HighPowerResources.cp(p) == recovered, "vector keeps the finite already-recovered CP balance");
        h.assertFalse(HighPowerCombat.shieldActive(p) || HighPowerCombat.spendShield(p, BigDecimal.ONE)
                || MagneticDomainReserve.available(p), "vector receives no automatic shield or EM reserve benefit");
        HighPowerCombat.tickShield(p);
        h.assertTrue(HighPowerCombat.shield(p).compareTo(shield) == 0, "vector never regenerates the retained shield");
        var persisted = data.copy();
        s.changeCategoryClassic("unsupported_test");
        h.assertFalse(HighPowerResources.ready(p) || HighPowerResources.tick(p) || HighPowerNaturalRecovery.snapshot(p).isPresent(),
                "unregistered high-power category has no resources or food extension");
        h.assertTrue(persisted.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "unknown category leaves stored CP and clocks untouched");
        boolean refused = false; try { HighPowerGrowth.values(p); } catch (IllegalStateException expected) { refused = true; }
        h.assertTrue(refused, "unknown category cannot read active high-power curves");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void learned_md_food_matches_native_fractional_control_and_preserves_shadow_timer_on_switch(GameTestHelper h) {
        Fixture f = new Fixture(h); var control = f.player(false, 5, ""); var p = f.player(true, 5, HighPowerCategories.ELECTROMASTER);
        control.setHealth(19.5F); f.hp(p, cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().multiply(new BigDecimal(".975")));
        f.food(control, 20, .3F); f.food(p, 20, .3F); f.ticks(control, 53); f.ticks(p, 53);
        var shadow = p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY).copy();
        String hp = HighPowerCombat.healthText(p); int timer = ((NativeFoodRecoveryAccess)p.getFoodData()).academy$naturalRecoveryTimer();
        AbilityStorage.get(p).changeCategoryClassic(HighPowerCategories.MELTDOWNER);
        AbilityStorage.get(p).learn(HighPowerSkills.DUAL_POINT);
        h.assertTrue(hp.equals(HighPowerCombat.healthText(p)) && shadow.equals(p.getPersistentData().getCompound(HighPowerNaturalRecovery.DATA_KEY))
                && timer == ((NativeFoodRecoveryAccess)p.getFoodData()).academy$naturalRecoveryTimer(), "switch and root learning do not heal or rewrite float shadow/timer");
        int ticks = 53;
        while (control.getHealth() < 20 && ticks < 200) {
            f.ticks(control, 1); f.ticks(p, 1); ticks++; f.sameFood(control, p);
            h.assertTrue((control.getHealth() == 20) == (new BigDecimal(HighPowerCombat.healthText(p)).compareTo(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth()) == 0),
                    "MD reaches exact full health on the same native fractional pulse at " + ticks);
        }
        h.assertTrue(ticks == 110, "fractional native reference completes at tick 110 through the category transition");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void md_root_gate_food_gamerule_and_potion_units_remain_native(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.player(true, 5, HighPowerCategories.MELTDOWNER); var s = AbilityStorage.get(p);
        BigDecimal half = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO); f.hp(p, half); f.food(p, 20, 6);
        s.unlearn(HighPowerSkills.DUAL_POINT);
        h.assertTrue(HighPowerNaturalRecovery.snapshot(p).orElseThrow().status().equals("unlearned"), "unlearned MD root has no proportional food extension");
        f.ticks(p, 10); f.flush(p); equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half.add(BigDecimal.ONE), "unlearned native food remains one exact point");
        s.learn(HighPowerSkills.DUAL_POINT); f.hp(p, half); f.food(p, 17, 0); f.ticks(p, 100);
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "food below 18 cannot recover MD HP");
        var rule = p.level().getGameRules().getRule(GameRules.RULE_NATURAL_REGENERATION); boolean old = rule.get();
        try { rule.set(false, p.server); f.food(p, 20, 6); f.ticks(p, 20);
            equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "vanilla gamerule disables proportional recovery");
        } finally { rule.set(old, p.server); }
        p.heal(4); f.flush(p); MobEffects.REGENERATION.value().applyEffectTick(p, 0); f.flush(p);
        equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half.add(BigDecimal.valueOf(5)), "item-style heal and regeneration remain 4 plus 1 exact HP");
        h.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=50)
    public static void md_offense_pause_survives_category_roundtrip_and_expires_on_real_server_clock(GameTestHelper h) {
        Fixture f = new Fixture(h); var p = f.player(true, 5, HighPowerCategories.MELTDOWNER);
        BigDecimal half = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.TWO); f.hp(p, half); f.food(p, 20, 6);
        HighPowerNaturalRecovery.offensiveAction(p);
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p) == 40, "paid MD offense starts native forty tick rest pause");
        AbilityStorage.get(p).changeCategoryClassic(HighPowerCategories.ELECTROMASTER); AbilityStorage.get(p).learn(HighPowerSkills.FORKED_ARC);
        AbilityStorage.get(p).changeCategoryClassic(HighPowerCategories.MELTDOWNER); AbilityStorage.get(p).learn(HighPowerSkills.DUAL_POINT);
        h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p) == 40, "switch/relearn never erase the shared pause");
        f.ticks(p, 10); equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half, "rest pause blocks proportional food gains");
        h.runAtTickTime(41, () -> {
            h.assertTrue(HighPowerNaturalRecovery.pauseTicksRemaining(p) == 0, "real server ticks expire the unchanged pause");
            f.food(p, 20, 6); f.ticks(p, 10);
            equal(h, new BigDecimal(HighPowerCombat.healthText(p)), half.add(cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().divide(BigDecimal.valueOf(20))), "next genuine food pulse recovers the native fraction");
            h.succeed();
        });
    }

    private static void equal(GameTestHelper h, BigDecimal actual, BigDecimal expected, String message) {
        h.assertTrue(actual.compareTo(expected) == 0, message + ": " + actual + " expected " + expected);
    }
    public static final class AwardSwitch {
        final ServerPlayer player; AwardSwitch(ServerPlayer player) { this.player = player; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void added(SkillExpAddedEvent event) {
            if (event.getEntity() == player && event.category.equals(HighPowerCategories.ELECTROMASTER))
                event.state.changeCategoryClassic(HighPowerCategories.MELTDOWNER);
        }
    }
    public static final class HealAwardSwitch {
        final ServerPlayer player; boolean called; HealAwardSwitch(ServerPlayer player) { this.player = player; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void heal(LivingHealEvent event) {
            if (event.getEntity() != player || called) return; called = true;
            var state = AbilityStorage.get(player); state.addExperience(HighPowerSkills.FORKED_ARC, .1);
            state.changeCategoryClassic(HighPowerCategories.MELTDOWNER); event.setCanceled(true);
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final List<io.netty.channel.embedded.EmbeddedChannel> channels = new ArrayList<>();
        final List<net.minecraft.world.level.ChunkPos> heldChunks = new ArrayList<>(); final UUID ticket = UUID.randomUUID(); boolean closed;
        Fixture(GameTestHelper h) { this.h = h; h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
            public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
        }); }
        ServerPlayer player(boolean enrolled, int level, String category) {
            var p = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-Categories]")); players.add(p); p.setNoGravity(true); p.getAbilities().instabuild = false;
            if (enrolled) {
                var s = AbilityStorage.get(p); s.changeCategoryClassic(category); s.setLevel(level); s.learn(HighPowerCategories.recoveryRoot(category)); s.setActivateState(true);
                h.assertTrue(HighPowerCombat.enroll(p, level), "reviewed native category fixture " + HighPowerCombat.unsupportedReason(p)); HighPowerResources.initializeBudget(p);
            } return p;
        }
        /** Real packet/channel and ticking-chunk fixture for ordinary native damage after login protection. */
        ServerPlayer connectedPlayer(int level, String category) {
            var position = h.absoluteVec(new net.minecraft.world.phys.Vec3(5, 2, 5));
            var chunk = new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(position));
            heldChunks.add(chunk); h.getLevel().getChunkSource().addRegionTicket(FIXTURE_TICKET, chunk, 2, ticket);
            h.getLevel().getChunk(chunk.x, chunk.z);
            var server = h.getLevel().getServer();
            var cookie = net.minecraft.server.network.CommonListenerCookie.createInitial(
                    new GameProfile(UUID.randomUUID(), "HPCat" + UUID.randomUUID().toString().substring(0, 10)), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            var connection = new net.minecraft.network.Connection(net.minecraft.network.protocol.PacketFlow.SERVERBOUND);
            var channel = new io.netty.channel.embedded.EmbeddedChannel(connection); channels.add(channel);
            net.neoforged.neoforge.network.registration.NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie);
            Object packet; while ((packet = channel.readOutbound()) != null)
                if (packet instanceof net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket login)
                    p.connection.handleAcceptTeleportPacket(new net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket(login.getId()));
            p.setGameMode(net.minecraft.world.level.GameType.SURVIVAL); p.setNoGravity(true);
            p.getAbilities().instabuild = false; p.getAbilities().invulnerable = false;
            p.moveTo(position.x, position.y, position.z, 0, 0); p.connection.resetPosition();
            var state = AbilityStorage.get(p); state.changeCategoryClassic(category); state.setLevel(level);
            state.learn(HighPowerCategories.recoveryRoot(category)); state.setActivateState(true);
            h.assertTrue(HighPowerCombat.enroll(p, level), "reviewed connected native category fixture");
            HighPowerResources.initializeBudget(p);
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5));
            return p;
        }
        void hp(ServerPlayer p, BigDecimal amount) { p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", CombatAmounts.decimalString(amount)); p.setHealth(amount.divide(new BigDecimal(HighPowerCombat.maxHealthText(p)), MathContext.DECIMAL128).multiply(BigDecimal.valueOf(20)).floatValue()); }
        void food(ServerPlayer p, int level, float saturation) { var food = p.getFoodData(); food.setFoodLevel(level); food.setSaturation(saturation); food.setExhaustion(0); CompoundTag data = new CompoundTag(); food.addAdditionalSaveData(data); data.putInt("foodTickTimer", 0); food.readAdditionalSaveData(data); }
        void ticks(ServerPlayer p, int count) { for (int i = 0; i < count; i++) p.getFoodData().tick(p); }
        void flush(ServerPlayer p) { HighPowerCombat.tick(new EntityTickEvent.Post(p)); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void sameFood(ServerPlayer a, ServerPlayer b) { var x = a.getFoodData(); var y = b.getFoodData(); h.assertTrue(x.getFoodLevel() == y.getFoodLevel() && x.getSaturationLevel() == y.getSaturationLevel() && x.getExhaustionLevel() == y.getExhaustionLevel(), "same native food/saturation/exhaustion"); }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (var p : players) {
                AbilityStorage.remove(p); var list = h.getLevel().getServer().getPlayerList();
                if (list.getPlayer(p.getUUID()) == p) list.remove(p); else p.discard();
            }
            heldChunks.forEach(chunk -> h.getLevel().getChunkSource().removeRegionTicket(FIXTURE_TICKET, chunk, 2, ticket));
            channels.forEach(io.netty.channel.embedded.EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
