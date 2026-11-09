package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyConfig;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW231 native defenses, reached only through an authenticated eight-tick charge and release. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDisintegrationSweepDamageRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_disintegration_sweep_damage";
    private static final String ID = "disintegration_sweep", SKILL = "meltdowner." + ID;
    private HighPowerDisintegrationSweepDamageRuntimeTests() {}
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal hp(LivingEntity target) { return HighPowerCombat.enabled(target)
            ? n(HighPowerCombat.healthText(target)) : new BigDecimal(Float.toString(target.getHealth())); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void eight_frozen_native_targets_share_one_two_d_budget_and_a_ninth_gets_nothing(GameTestHelper h) { budget(h, 3); }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void level_five_native_sweep_conserves_whole_eight_target_two_d_budget(GameTestHelper h) { budget(h, 5); }

    private static void budget(GameTestHelper h, int level) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(16, 4, level);
        List<Villager> targets = new ArrayList<>(); List<Observation> hits = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            Villager target = f.angular(caster, -22 + i * 5.5, 18, true); targets.add(target); hits.add(f.observe(target));
        }
        BigDecimal budget = HighPowerGrowth.values(caster).damage().multiply(n("2")).min(CombatAmounts.CAP);
        BigDecimal share = budget.divide(n("8"));
        f.sweep(List.of(caster), () -> {}, () -> {
            int damaged = 0; BigDecimal spent = BigDecimal.ZERO;
            for (int i = 0; i < targets.size(); i++) {
                BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(targets.get(i))); spent = spent.add(loss);
                if (loss.signum() > 0) { damaged++; eq(h, loss, share, "selected identity owns exactly one eighth"); }
                h.assertTrue(hits.get(i).skills <= 1 && hits.get(i).posts <= 1, "an identity gets at most one native attempt across ten ticks");
                if (hits.get(i).source != null) source(h, hits.get(i).source, caster, f.origins.get(caster));
            }
            h.assertTrue(damaged == 8, "only the first eight initially visible frozen identities receive damage");
            eq(h, spent, budget, "native multi-target damage closes the single whole-sweep ledger");
            h.assertFalse(DisintegrationSweep.active(caster), "the finite ten-tick sweep has ended");
            h.assertFalse(HighPowerCombat.attack(caster, SKILL, targets.getFirst(), BigDecimal.ONE), "generic MD entry cannot forge NEW231");
            AbilityStorage.get(caster).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
            h.assertFalse(HighPowerCombat.attack(caster, SKILL, targets.getFirst(), BigDecimal.ONE), "generic EM entry cannot forge NEW231 either");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void canceled_and_missed_frozen_shares_are_discarded_without_reassignment(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(16, 4, 3);
        List<Villager> targets = new ArrayList<>(); List<Observation> hits = new ArrayList<>();
        for (int angle : new int[]{-18, -6, 6, 18}) { var target = f.angular(caster, angle, 18, true); targets.add(target); hits.add(f.observe(target)); }
        AmountGate cancel = new AmountGate(targets.getFirst(), 2); f.hook(cancel);
        BigDecimal share = HighPowerGrowth.values(caster).damage().multiply(n("2")).divide(n("4"));
        List<Villager> newcomer = new ArrayList<>();
        f.sweep(List.of(caster), () -> {
            Vec3 vacated = targets.get(1).position(); targets.get(1).setPos(vacated.add(0, 4, 0));
            newcomer.add(f.mob(vacated, true));
        }, () -> {
            h.assertTrue(cancel.called && hits.getFirst().skills == 1, "the canceled identity spent one real native attempt");
            eq(h, hp(targets.getFirst()), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "native cancellation discards its own allocation");
            eq(h, hp(targets.get(1)), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "a frozen target moving out of the plane is not tracked");
            eq(h, hp(newcomer.getFirst()), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "new body at the abandoned contact cannot inherit an allocation");
            for (int i = 2; i < 4; i++) {
                eq(h, hp(targets.get(i)), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(share), "remaining targets retain their original quarter share");
                h.assertTrue(hits.get(i).posts == 1, "each surviving identity receives one native completion");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void armor_toughness_resistance_protection_and_absorption_match_native_player_attack(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Villager> controls = new ArrayList<>();
        List<Observation> nativeHits = new ArrayList<>(); List<BigDecimal> before = new ArrayList<>();
        for (Case c : cases) {
            Villager control = f.control(c); controls.add(control); defenses(h, c.target); defenses(h, control);
            f.hook(new Discount(c.target)); nativeHits.add(f.observe(control)); before.add(hp(c.target));
        }
        f.sweep(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Villager control = controls.get(i);
                h.assertTrue(control.hurt(f.nativeSource(c.caster, translatedOrigin(f, c, control)), 5), "matching native reducer control accepts");
            }
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Observation hit = c.hit, nativeHit = nativeHits.get(i);
                h.assertTrue(hit.posts == 1 && nativeHit.posts == 1 && hit.armor > 0 && hit.effects > 0 && hit.enchantments > 0 && hit.absorption == .5F,
                        "one native pipeline executes every ordinary partial reducer");
                h.assertTrue(hit.armor == nativeHit.armor && hit.effects == nativeHit.effects && hit.enchantments == nativeHit.enchantments
                        && hit.absorption == nativeHit.absorption && Math.abs(hit.damage - nativeHit.damage) < .00001,
                        "enrolled and ordinary sweep observations match the native PLAYER_ATTACK counterpart");
                BigDecimal residual = DamageMitigation.mitigate(n("5"), new DefenseProfile(BigDecimal.valueOf(c.target.getArmorValue()),
                        BigDecimal.valueOf(c.target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)), 1, 4)).subtract(n(".5"));
                if (i == 0) eq(h, hp(c.target), before.get(i).subtract(residual), "native residual debits exact HP");
                else h.assertTrue(c.target.getHealth() == controls.get(i).getHealth(), "ordinary HP remains vanilla float damage");
                h.assertTrue(c.target.getAbsorptionAmount() == 0 && controls.get(i).getAbsorptionAmount() == 0, "absorption is spent once");
                source(h, hit.source, c.caster, f.origins.get(c.caster));
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=60)
    public static void held_shield_front_and_rear_match_the_actual_hand_side_on_both_target_kinds(GameTestHelper h) { shield(h, false); }

    private static void shield(GameTestHelper h, boolean rear) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Villager> controls = new ArrayList<>(); List<Observation> nativeHits = new ArrayList<>();
        List<BigDecimal> before = new ArrayList<>(); float yaw = rear ? 0 : 180;
        for (Case c : cases) {
            Villager control = f.control(c); controls.add(control); raiseShield(c.target, yaw); raiseShield(control, yaw);
            f.hook(new Discount(c.target)); nativeHits.add(f.observe(control)); before.add(hp(c.target));
        }
        f.sweep(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Villager control = controls.get(i); face(c.target, yaw); face(control, yaw);
                h.assertTrue(c.target.isBlocking() && control.isBlocking(), "native shield raising delay has actually elapsed during charge");
                h.assertTrue(control.hurt(f.nativeSource(c.caster, translatedOrigin(f, c, control)), 5) == rear, "native control makes the expected directional decision");
            }
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Observation nativeHit = nativeHits.get(i);
                eq(h, hp(c.target), rear ? before.get(i).subtract(n("5")) : before.get(i), "actual hand arrival side governs enrolled and ordinary shielding");
                h.assertTrue(c.hit.blocked == !rear && c.hit.blocked == nativeHit.blocked
                        && c.hit.blockedDamage == nativeHit.blockedDamage && c.hit.damage == nativeHit.damage,
                        "native block decision, blocked amount and residual match the counterpart");
                source(h, c.hit.source, c.caster, f.origins.get(c.caster));
            }
            if (rear) h.succeed(); else { f.close(); shield(h, true); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=60)
    public static void same_rank_native_iframes_do_not_consume_a_second_absorption_or_health_payment(GameTestHelper h) { iframes(h, false); }

    private static void iframes(GameTestHelper h, boolean absorb) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Villager> controls = new ArrayList<>(); List<BigDecimal> afterFirst = new ArrayList<>();
        for (Case c : cases) {
            Villager control = f.control(c); controls.add(control); f.hook(new Discount(c.target));
            for (LivingEntity target : List.of(c.target, control)) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            if (absorb) for (LivingEntity target : List.of(c.target, control)) {
                target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); target.setAbsorptionAmount(10);
            }
        }
        f.sweep(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Villager control = controls.get(i);
                h.assertTrue(c.target.hurt(f.nativeSource(c.caster, f.origins.get(c.caster)), 5), "first ordinary native hit accepts on the actual target");
                h.assertTrue(control.hurt(f.nativeSource(c.caster, translatedOrigin(f, c, control)), 5), "first paired native control accepts");
                afterFirst.add(hp(c.target));
            }
            h.runAfterDelay(5, () -> {
                for (int i = 0; i < cases.size(); i++) {
                    Case c = cases.get(i); Villager control = controls.get(i);
                    h.assertFalse(control.hurt(f.nativeSource(c.caster, translatedOrigin(f, c, control)), 5), "equal native control is still in the real hurt cooldown");
                }
            });
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); eq(h, hp(c.target), afterFirst.get(i), "the authentic second NEW231 hit cannot bypass native iframe");
                h.assertTrue(c.hit.skills == 1 && c.hit.posts == 1, "one paid sweep attempt produces no second native completion");
                h.assertTrue(c.target.getAbsorptionAmount() == controls.get(i).getAbsorptionAmount()
                        && c.target.getAbsorptionAmount() == (absorb ? 5 : 0), "refused equal hit never spends absorption twice");
            }
            if (absorb) h.succeed(); else { f.close(); iframes(h, true); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void lethal_native_completion_matches_ordinary_player_attack_on_both_target_kinds(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Villager> controls = new ArrayList<>(); List<Observation> nativeHits = new ArrayList<>();
        for (Case c : cases) {
            Villager control = f.control(c); controls.add(control); nativeHits.add(f.observe(control)); control.setHealth(1);
            if (HighPowerCombat.enabled(c.target)) c.target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            else c.target.setHealth(1);
            f.hook(new Discount(c.target));
        }
        f.sweep(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Villager control = controls.get(i);
                h.assertTrue(control.hurt(f.nativeSource(c.caster, translatedOrigin(f, c, control)), 5), "paired lethal native PLAYER_ATTACK is accepted");
            }
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Observation nativeHit = nativeHits.get(i);
                h.assertTrue(!c.target.isAlive() && !controls.get(i).isAlive(), "enrolled and ordinary lethal results remain native deaths");
                eq(h, hp(c.target), BigDecimal.ZERO, "the native lethal completion closes exact or ordinary HP at zero");
                h.assertTrue(c.hit.skills == 1 && c.hit.posts == 1 && c.hit.deaths == 1 && nativeHit.posts == 1 && nativeHit.deaths == 1,
                        "one authenticated allocated hit produces one native damage completion and death decision");
                source(h, c.hit.source, c.caster, f.origins.get(c.caster));
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void native_death_veto_restores_the_exact_prelethal_state(GameTestHelper h) {
        Fixture f = new Fixture(h); Case c = f.caseAt(8, true); f.hook(new Discount(c.target));
        c.target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
        DeathVeto veto = new DeathVeto(c.target); f.hook(veto);
        f.sweep(List.of(c.caster), () -> {}, () -> {
            h.assertTrue(veto.called && c.target.isAlive(), "real native death decision calls the cancellation hook");
            eq(h, hp(c.target), BigDecimal.ONE, "native death veto restores the enrolled prelethal ledger"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void native_totems_rescue_enrolled_and_ordinary_targets_after_one_real_hit(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Villager> controls = new ArrayList<>();
        for (Case c : cases) {
            Villager control = f.control(c); controls.add(control); control.setHealth(1);
            if (HighPowerCombat.enabled(c.target)) c.target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            else c.target.setHealth(1);
            for (LivingEntity target : List.of(c.target, control)) target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            f.hook(new Discount(c.target));
        }
        f.sweep(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) controls.get(i).hurt(f.nativeSource(cases.get(i).caster, translatedOrigin(f, cases.get(i), controls.get(i))), 5);
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Villager control = controls.get(i);
                h.assertTrue(c.hit.posts == 1 && c.target.isAlive() && control.isAlive() && c.target.getOffhandItem().isEmpty()
                        && control.getOffhandItem().isEmpty() && hp(c.target).signum() > 0, "native totem is consumed once and rescues both target kinds");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void native_zero_and_cancellation_discard_each_paid_target_attempt(GameTestHelper h) { amounts(h, 0, false); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=90)
    public static void unknown_positive_skill_incoming_and_pre_amplification_is_refused_on_both_target_kinds(GameTestHelper h) { amounts(h, 0, true); }

    private static void amounts(GameTestHelper h, int stage, boolean amplify) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<AmountGate> gates = new ArrayList<>(); List<BigDecimal> before = new ArrayList<>();
        for (Case c : cases) {
            if (!(amplify && stage == 0)) f.hook(new Discount(c.target));
            AmountGate gate = new AmountGate(c.target, stage * 2 + (amplify ? 1 : 0)); gates.add(gate); f.hook(gate); before.add(hp(c.target));
        }
        f.sweep(casters(cases), () -> {}, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); h.assertTrue(gates.get(i).called && c.hit.skills == 1, "requested real amount callback runs once at stage " + stage);
                eq(h, hp(c.target), before.get(i), "cancellation, zero or unknown positive amplification cannot damage either target kind");
                h.assertTrue(c.hit.damage == 0, "no positive native proxy residual escapes refusal");
                h.assertTrue(AbilityStorage.get(c.caster).exp(ID) == 0, "refused allocation awards no mastery");
            }
            if (stage == 2) h.succeed(); else { f.close(); amounts(h, stage + 1, amplify); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_global_scale",timeoutTicks=35)
    public static void global_config_amplification_cannot_expand_a_frozen_target_allocation(GameTestHelper h) { config(h, false); }
    @GameTest(template=TEMPLATE,batch=BATCH+"_skill_scale",timeoutTicks=35)
    public static void skill_config_amplification_cannot_expand_a_frozen_target_allocation(GameTestHelper h) { config(h, true); }

    private static void config(GameTestHelper h, boolean local) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<BigDecimal> before = cases.stream().map(c -> hp(c.target)).toList();
        f.sweep(casters(cases), () -> {
            f.changedConfig = true; f.oldGlobal = AcademyConfig.DAMAGE_SCALE.get(); f.oldLocal = AcademyConfig.SKILL_NUMERIC_OVERRIDES.get();
            if (local) AcademyConfig.SKILL_NUMERIC_OVERRIDES.set(List.of(SKILL + ".damage_scale=2")); else AcademyConfig.DAMAGE_SCALE.set(2D);
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); eq(h, hp(c.target), before.get(i), "configured amplification cannot inflate the original paid share");
                h.assertTrue(c.hit.skills == 1 && c.hit.posts == 0 && AbilityStorage.get(c.caster).exp(ID) == 0, "config refusal spends only its single attempt without mastery");
            }
            f.restoreConfig(); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=150)
    public static void skill_callbacks_cannot_move_retarget_reteam_or_change_a_paid_segment(GameTestHelper h) { mutations(h, 0, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=150)
    public static void incoming_callbacks_cannot_move_retarget_reteam_or_change_a_paid_segment(GameTestHelper h) { mutations(h, 1, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=150)
    public static void pre_callbacks_cannot_move_retarget_reteam_or_change_a_paid_segment(GameTestHelper h) { mutations(h, 2, 0); }

    private static void mutations(GameTestHelper h, int stage, int mode) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Mutation> gates = new ArrayList<>(); List<BigDecimal> before = new ArrayList<>();
        for (Case c : cases) { f.hook(new Discount(c.target)); Mutation gate = new Mutation(f, c, stage, mode); gates.add(gate); f.hook(gate); before.add(hp(c.target)); }
        f.sweep(casters(cases), () -> {}, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Mutation gate = gates.get(i);
                h.assertTrue(gate.called && c.hit.skills == 1, "real callback executes one captured target attempt, stage=" + stage + " mode=" + mode);
                eq(h, hp(c.target), before.get(i), "callback invalidation cannot debit either native target kind");
                h.assertTrue(c.hit.damage == 0, "late callback invalidation prevents positive proxy damage");
                if (gate.replacement != null) eq(h, hp(gate.replacement), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "replacement identity cannot inherit a consumed native permit");
                if (mode != 4) h.assertTrue(AbilityStorage.get(c.caster).exp(ID) == 0, "invalidated geometry grants no productive mastery");
            }
            if (mode == 5) h.succeed(); else { f.close(); mutations(h, stage, mode + 1); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void actual_continuous_beam_contact_spends_only_new212_contacted_share_and_front_gap_stays_open(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Case> cases = new ArrayList<>(); List<PathWitness> paths = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            ServerPlayer caster = f.caster(6 + 10 * i, 4, 3); Vec3 hand = DualPoint.hand(caster, 0);
            ServerPlayer defender = f.player(hand.x, hand.z + 7, 3, true);
            Case c = new Case(caster, defender, f.observe(defender)); cases.add(c); f.hook(new Discount(defender));
            PathWitness path = new PathWitness(caster); paths.add(path); f.hook(path);
        }
        f.ready(() -> {
            List<BigDecimal> before = new ArrayList<>(); List<net.minecraft.world.phys.AABB> bodies = new ArrayList<>();
            List<ScatterPlateGeometry.Pose> poses = new ArrayList<>();
            for (int i = 0; i < cases.size(); i++) {
                ServerPlayer defender = (ServerPlayer)cases.get(i).target; f.armPlates(defender, i == 0 ? 190 : i == 1 ? 180 : 0); before.add(hp(defender));
                bodies.add(defender.getBoundingBox()); poses.add(ScatterPlateGeometry.pose(vector(defender.position().add(0, 1, 0)), vector(defender.getLookAngle())));
            }
            f.sweep(casters(cases), () -> {}, () -> {
                for (int i = 0; i < cases.size(); i++) {
                    Case c = cases.get(i); ServerPlayer defender = (ServerPlayer)c.target;
                    var body = bodies.get(i);
                    var leg = paths.get(i).paths.stream().filter(s -> body.inflate(.036).contains(s.end())
                            && (s.status() == DisintegrationSweep.DAMAGED || s.status() == DisintegrationSweep.ABSORBED)).findFirst().orElseThrow();
                    var contact = ScatterPlateGeometry.crossing(poses.get(i), vector(leg.start()), vector(leg.end()), 7);
                    if (i == 0) {
                        h.assertTrue(contact.isPresent(), "actual moving-beam terminal segment crosses a real front ellipse");
                        int plate = contact.orElseThrow().plateIndex(); var shares = ScatterPlateStorage.read(defender).orElseThrow();
                        eq(h, HighPowerCombat.shield(defender), n("4"), "only the physically contacted two-point share pays");
                        eq(h, hp(defender), before.get(i).subtract(n("3")), "only the unpaid native residual reaches HP");
                        for (int index = 0; index < 3; index++) eq(h, shares.plate(index).current(), index == plate ? BigDecimal.ZERO : n("2"), "uncontacted shares cannot lend shield");
                    } else {
                        h.assertTrue(contact.isEmpty(), "actual native segment misses through " + (i == 1 ? "the finite front gap" : "the rear"));
                        eq(h, HighPowerCombat.shield(defender), n("6"), "gap/rear spends no NEW212 resource");
                        eq(h, hp(defender), before.get(i).subtract(n("5")), "gap/rear preserves the native body residual");
                    }
                    source(h, c.hit.source, c.caster, f.origins.get(c.caster));
                }
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void swept_player_attack_identity_is_not_new222_melee_or_existing_em_intake(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer md = f.caster(8, 4, 3), em = f.caster(24, 4, 3);
        ServerPlayer shell = f.player(DualPoint.hand(md, 0).x, DualPoint.hand(md, 0).z + 7, 3, true);
        ServerPlayer electrical = f.player(DualPoint.hand(em, 0).x, DualPoint.hand(em, 0).z + 7, 5, false);
        var shellState = AbilityStorage.get(shell); shellState.learn(HighPowerSkills.SCATTER_PLATES); shellState.learn(HighPowerSkills.THERMAL_SHELL);
        shellState.presets.edit(0, 0, HighPowerSkills.THERMAL_SHELL, shellState::learned);
        var emState = AbilityStorage.get(electrical); emState.changeCategoryClassic(HighPowerCategories.ELECTROMASTER); emState.setLevel(5);
        emState.learn(HighPowerSkills.MAGNETIC_BRACER); emState.setActivateState(true); emState.overloadFine = true;
        h.assertTrue(HighPowerCombat.enroll(electrical, 5), "real enrolled EM defensive target"); HighPowerResources.initializeBudget(electrical);
        for (String skill : List.of(GroundingReturn.ID, PotentialBuffer.ID, MagneticDomain.ID)) emState.learn(skill);
        emState.presets.edit(0, 0, GroundingReturn.ID, emState::learned); emState.presets.edit(0, 1, PotentialBuffer.ID, emState::learned);
        emState.presets.edit(0, 2, MagneticDomain.ID, emState::learned); f.hook(new Discount(shell)); f.hook(new Discount(electrical));
        DefenseWitness defensiveState = new DefenseWitness(shell, electrical); f.hook(defensiveState);
        f.ready(() -> {
            h.assertTrue(ThermalShell.toggle(shell, f.input(shell, 0)), "real paid NEW222 shell is active");
            BlockPos ground = BlockPos.containing(electrical.position()).below(); f.change(ground, Blocks.STONE);
            Vec3 supportedPosition = electrical.position(); electrical.setNoGravity(false); electrical.setShiftKeyDown(true); face(electrical, 180);
            // A standing native player continuously collides downward with its support. A floating
            // no-gravity mock loses onGround during travel and correctly cannot keep NEW122 active.
            electrical.setDeltaMovement(0, -.08, 0); electrical.doTick(); electrical.connection.resetPosition();
            h.assertTrue(electrical.onGround() && electrical.position().distanceToSqr(supportedPosition) < 1e-12,
                    "ordinary gravity settles the crouching native defender on its real stone support");
            h.assertTrue(GroundingReturn.start(electrical, f.input(electrical, 0)), "real grounding is active");
            h.assertTrue(PotentialBuffer.start(electrical, f.input(electrical, 1)), "real front buffer is active");
            h.assertTrue(MagneticDomain.start(electrical, f.input(electrical, 2)), "real paid domain is active");
            h.assertTrue(ThermalShell.active(shell) && GroundingReturn.active(electrical) && PotentialBuffer.active(electrical) && MagneticDomain.active(electrical),
                    "all native defensive controllers are genuinely active before charging");
            BigDecimal shellHp = hp(shell), emHp = hp(electrical), shellS = HighPowerCombat.shield(shell), emS = HighPowerCombat.shield(electrical), reserve = MagneticDomainReserve.reserve(electrical);
            f.sweep(List.of(md, em), () -> {}, () -> {
                h.assertTrue(defensiveState.shellObserved && defensiveState.electricalObserved, "both actual NEW231 native incoming transactions were observed");
                h.assertTrue(defensiveState.shellLive, "real NEW222 shell remains active at native NEW231 intake");
                h.assertTrue(defensiveState.groundingLive, "physically supported NEW122 grounding remains active at native NEW231 intake");
                h.assertTrue(defensiveState.bufferLive, "real NEW142 front buffer remains active at native NEW231 intake");
                h.assertTrue(defensiveState.domainLive, "real paid NEW152 domain remains active at native NEW231 intake");
                eq(h, hp(shell), shellHp.subtract(n("5")), "NEW231 remains outside the closed NEW222 direct-melee intake");
                eq(h, HighPowerCombat.shield(shell), shellS, "NEW222 spends no shield for the sweep source");
                h.assertTrue(ThermalShell.pulseCount(shell) == 0 && shellState.exp(HighPowerSkills.THERMAL_SHELL) == 0, "excluded sweep grants no shell pulse or mastery");
                eq(h, hp(electrical), emHp.subtract(n("5")), "unreviewed MD source keeps ordinary native damage under EM defenses");
                eq(h, HighPowerCombat.shield(electrical), emS, "sweep consumes no EM shield"); eq(h, MagneticDomainReserve.reserve(electrical), reserve, "sweep consumes no EM reserve");
                h.assertTrue(emState.exp(GroundingReturn.ID) == 0 && emState.exp(PotentialBuffer.ID) == 0 && emState.exp(MagneticDomain.ID) == 0, "excluded source creates no EM interception mastery");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void preexisting_new211_and_new221_keep_their_native_damage_and_distinct_origins(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer dual = f.caster(7, 4, 3), mirror = f.caster(23, 4, 3);
        Villager dualTarget = f.mob(h.absoluteVec(new Vec3(7, 2, 12)), true), mirrorTarget = f.mob(h.absoluteVec(new Vec3(24.5, 2, 8)), true);
        Observation dualHit = f.observe(dualTarget), mirrorHit = f.observe(mirrorTarget);
        var d = AbilityStorage.get(dual); d.presets.edit(0, 0, HighPowerSkills.DUAL_POINT, d::learned);
        var m = AbilityStorage.get(mirror); m.presets.edit(0, 0, HighPowerSkills.FOLDED_MIRROR_ARRAY, m::learned);
        f.hook(new Discount(dualTarget, "meltdowner.dual_point")); f.hook(new Discount(mirrorTarget, "meltdowner.folded_mirror_array"));
        face(mirror, 0); mirror.setShiftKeyDown(true); String placement = f.input(mirror, 0);
        h.assertTrue(FoldedMirrorArray.start(mirror, placement), "existing crouch placement starts"); f.aim(mirror, mirrorTarget);
        h.assertTrue(FoldedMirrorArray.release(mirror, placement), "existing fixed mirror commits"); mirror.setShiftKeyDown(false);
        h.runAfterDelay(2, () -> {
            f.aim(dual, dualTarget); Vec3 hand = DualPoint.hand(dual, 0);
            h.assertTrue(DualPoint.tap(dual, f.input(dual, 0)), "genuine preexisting NEW211 ingress still accepts");
            String fire = f.input(mirror, 0); h.assertTrue(FoldedMirrorArray.start(mirror, fire) && FoldedMirrorArray.release(mirror, fire), "genuine preexisting NEW221 ingress still accepts");
            eq(h, hp(dualTarget), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "NEW211 residual is unchanged");
            eq(h, hp(mirrorTarget), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "NEW221 residual is unchanged");
            source(h, dualHit.source, dual, hand);
            Vec3 reflected = FoldedMirrorArray.receipt(mirror).path().getLast().start(); source(h, mirrorHit.source, mirror, reflected);
            h.assertTrue(reflected.distanceToSqr(DualPoint.hand(mirror, 0)) > 1, "NEW221 retains its reflected origin rather than NEW231's hand origin");
            h.succeed();
        });
    }

    private static List<ServerPlayer> casters(List<Case> cases) { return cases.stream().map(Case::caster).toList(); }
    private static Vec3 translatedOrigin(Fixture f, Case c, LivingEntity control) { return f.origins.get(c.caster).add(control.position().subtract(c.target.position())); }
    private static ScatterPlateGeometry.Vector vector(Vec3 v) { return new ScatterPlateGeometry.Vector(v.x, v.y, v.z); }
    private static void face(LivingEntity target, float yaw) { target.setYRot(yaw); target.setYHeadRot(yaw); target.yBodyRot = yaw; target.setXRot(0); }
    private static void raiseShield(LivingEntity target, float yaw) { face(target, yaw); target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); target.startUsingItem(InteractionHand.OFF_HAND); }
    private static void defenses(GameTestHelper h, LivingEntity target) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
        target.getAttribute(Attributes.ARMOR).setBaseValue(5); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0)); target.setAbsorptionAmount(.5F);
    }
    private static void source(GameTestHelper h, DamageSource source, ServerPlayer caster, Vec3 origin) {
        h.assertTrue(source != null && source.is(DamageTypes.PLAYER_ATTACK) && source.getEntity() == caster && source.getDirectEntity() == caster
                && origin.equals(source.getSourcePosition()) && origin.equals(source.sourcePositionRaw()), "native player attribution retains the actual certified emission origin");
        h.assertFalse(source.is(DamageTypeTags.IS_PROJECTILE) || source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE) || source.is(DamageTypeTags.BYPASSES_SHIELD)
                || source.is(DamageTypeTags.BYPASSES_COOLDOWN) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION), "NEW231 adds no native bypass or unrelated damage family");
    }
    private record Case(ServerPlayer caster, LivingEntity target, Observation hit) {}

    public static final class Discount {
        final LivingEntity target; final String skill;
        Discount(LivingEntity target) { this(target, SKILL); }
        Discount(LivingEntity target, String skill) { this.target = target; this.skill = skill; }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void skill(SkillAttackEvent e) { if (e.target == target && e.skill.equals(skill)) e.amount = 5; }
    }
    public static final class AmountGate {
        final LivingEntity target; final int mode; boolean called;
        AmountGate(LivingEntity target, int mode) { this.target = target; this.mode = mode; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e) {
            if (e.target != target || !e.skill.equals(SKILL) || mode > 1) return; called = true; e.amount = mode == 0 ? 0 : e.amount * 2;
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) {
            if (e.getEntity() != target || mode < 2 || mode > 3) return; called = true; if (mode == 2) e.setCanceled(true); else e.setAmount(e.getAmount() * 2);
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e) {
            if (e.getEntity() != target || mode < 4) return; called = true; e.setNewDamage(mode == 4 ? 0 : e.getNewDamage() * 2);
        }
    }
    public static final class Mutation {
        final Fixture fixture; final Case c; final int stage, mode; boolean called; Villager replacement;
        Mutation(Fixture fixture, Case c, int stage, int mode) { this.fixture = fixture; this.c = c; this.stage = stage; this.mode = mode; }
        private void change() {
            if (called) return; called = true;
            switch (mode) {
                case 0 -> c.target.setPos(c.target.position().add(0, 4, 0));
                case 1 -> c.caster.setPos(c.caster.position().add(.1, 0, 0));
                case 2 -> fixture.change(BlockPos.containing(DualPoint.hand(c.caster, 0).lerp(c.target.getBoundingBox().getCenter(), .5)), Blocks.STONE);
                case 3 -> fixture.ally(c.caster, c.target);
                case 4 -> AbilityStorage.get(c.caster).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
                default -> { Vec3 old = c.target.position(); c.target.setPos(old.add(0, 4, 0)); replacement = fixture.mob(old, true); }
            }
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e) { if (stage == 0 && e.target == c.target && e.skill.equals(SKILL)) change(); }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) { if (stage == 1 && e.getEntity() == c.target) change(); }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e) { if (stage == 2 && e.getEntity() == c.target) change(); }
    }
    public static final class DeathVeto {
        final LivingEntity target; boolean called; DeathVeto(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void death(LivingDeathEvent e) { if (e.getEntity() == target) { called = true; e.setCanceled(true); } }
    }
    public static final class Observation {
        final LivingEntity target; DamageSource source; int skills, posts, deaths; boolean blocked; float damage, armor, effects, enchantments, absorption, blockedDamage;
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void skill(SkillAttackEvent e) { if (e.target == target && e.skill.equals(SKILL)) skills++; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == target) source = e.getSource(); }
        @SubscribeEvent public void shield(LivingShieldBlockEvent e) { if (e.getEntity() == target) { blocked = e.getBlocked(); blockedDamage = e.getBlockedDamage(); } }
        @SubscribeEvent public void death(LivingDeathEvent e) { if (e.getEntity() == target) deaths++; }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) {
            if (e.getEntity() != target) return; posts++; damage = e.getNewDamage(); armor = e.getReduction(DamageContainer.Reduction.ARMOR);
            effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS);
            absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
    public static final class PathWitness {
        final ServerPlayer caster; final List<DisintegrationSweep.SegmentVisual> paths = new ArrayList<>();
        PathWitness(ServerPlayer caster) { this.caster = caster; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void tick(ServerTickEvent.Post e) {
            if (e.getServer() != caster.server) return; var receipt = DisintegrationSweep.receipt(caster);
            if (receipt != null) paths.addAll(receipt.path());
        }
    }
    public static final class PaymentWitness {
        final ServerPlayer caster; final double cost; final int cooldown;
        double beforeTick; long capturedTick = -1, beforeClock, firstTick = -1, paidClock; int calls; boolean paid = true;
        PaymentWitness(ServerPlayer caster) {
            this.caster = caster; var growth = HighPowerGrowth.values(caster);
            cost = DisintegrationSweepRules.attackCost(growth.position()); cooldown = DisintegrationSweepRules.cooldown(growth.position());
        }
        // The native player pump is HIGHEST; this observes legitimate recovery before the NORMAL
        // sweep controller runs. Each emission must still have the one exact paid debit behind it.
        @SubscribeEvent(priority=EventPriority.HIGH) public void before(ServerTickEvent.Post e) {
            if (e.getServer() != caster.server) return; capturedTick = caster.serverLevel().getGameTime();
            beforeTick = HighPowerResources.cp(caster); beforeClock = caster.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID);
        }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void skill(SkillAttackEvent e) {
            if (e.player != caster || !e.skill.equals(SKILL)) return;
            long now = caster.serverLevel().getGameTime(), clock = caster.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_" + ID);
            if (calls++ == 0) {
                firstTick = now; paidClock = clock; paid &= clock == now + cooldown && clock != beforeClock;
            }
            double expected = beforeTick - (now == firstTick ? cost : 0);
            paid &= capturedTick == now && clock == paidClock
                    && Double.doubleToLongBits(HighPowerResources.cp(caster)) == Double.doubleToLongBits(expected);
        }
    }
    public static final class DefenseWitness {
        final ServerPlayer shell, electrical; boolean shellObserved, electricalObserved;
        boolean shellLive = true, groundingLive = true, bufferLive = true, domainLive = true;
        DefenseWitness(ServerPlayer shell, ServerPlayer electrical) { this.shell = shell; this.electrical = electrical; }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void incoming(LivingIncomingDamageEvent e) {
            if (e.getEntity() == shell) { shellObserved = true; shellLive &= ThermalShell.active(shell); }
            if (e.getEntity() == electrical) {
                electricalObserved = true; groundingLive &= GroundingReturn.active(electrical);
                bufferLive &= PotentialBuffer.active(electrical); domainLive &= MagneticDomain.active(electrical);
            }
        }
    }

    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final List<BlockPos> changedBlocks = new ArrayList<>(); final List<PlayerTeam> teams = new ArrayList<>();
        final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>(); final Map<ServerPlayer, Vec3> positions = new IdentityHashMap<>(), origins = new IdentityHashMap<>();
        boolean closed, changedConfig, pvp; double oldGlobal; List<? extends String> oldLocal;
        Fixture(GameTestHelper h) {
            this.h = h;
            // Every entity and blocker stays inside the actual 32x32 template. Its opaque perimeter also
            // prevents a 48m sweep from acquiring bodies belonging to a concurrently running GameTest.
            for (int y = 1; y <= 6; y++) for (int i = 0; i < 32; i++) {
                h.setBlock(new BlockPos(0, y, i), Blocks.STONE); h.setBlock(new BlockPos(31, y, i), Blocks.STONE);
                h.setBlock(new BlockPos(i, y, 0), Blocks.STONE); h.setBlock(new BlockPos(i, y, 31), Blocks.STONE);
            }
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer caster(double x, double z, int level) { return player(h.absoluteVec(new Vec3(x, 2, z)).x, h.absoluteVec(new Vec3(x, 2, z)).z, level, true); }
        ServerPlayer player(double worldX, double worldZ, int level, boolean enrolled) {
            var server = h.getLevel().getServer(); UUID uuid = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, "HPSw" + uuid.toString().substring(0, 10)), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection)); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie); p.setNoGravity(true); p.setGameMode(GameType.SURVIVAL);
            p.setPos(worldX, h.absoluteVec(new Vec3(0, 2, 0)).y, worldZ); p.connection.resetPosition(); positions.put(p, p.position()); face(p, 0);
            if (enrolled) {
                var state = AbilityStorage.get(p); state.changeCategoryClassic(HighPowerCategories.MELTDOWNER); state.setLevel(level);
                state.learn(HighPowerSkills.DUAL_POINT); state.learn(HighPowerSkills.FOLDED_MIRROR_ARRAY); state.learn(ID);
                state.setActivateState(true); state.overloadFine = true; state.presets.edit(0, 0, ID, state::learned);
                h.assertTrue(HighPowerCombat.enroll(p, level), "real native connected MD caster: " + HighPowerCombat.unsupportedReason(p)); HighPowerResources.initializeBudget(p);
            }
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, 200)); return p;
        }
        Villager mob(Vec3 position, boolean enrolled) {
            var target = new Villager(EntityType.VILLAGER, h.getLevel()); target.setNoAi(true); target.setNoGravity(true); target.setPos(position);
            h.getLevel().addFreshEntity(target); entities.add(target);
            if (enrolled) h.assertTrue(HighPowerCombat.enroll(target, 5), "supported exact native target"); return target;
        }
        Villager angular(ServerPlayer caster, double degrees, double forward, boolean enrolled) {
            Vec3 hand = DualPoint.hand(caster, 0); return mob(new Vec3(hand.x + Math.tan(Math.toRadians(degrees)) * forward, caster.getY(), hand.z + forward), enrolled);
        }
        Case caseAt(double x, boolean enrolled) {
            ServerPlayer caster = caster(x, 4, 3); Villager target = angular(caster, 0, 7, enrolled); return new Case(caster, target, observe(target));
        }
        List<Case> pair() { return List.of(caseAt(8, true), caseAt(24, false)); }
        Villager control(Case c) { return mob(c.caster.position().add(3, 0, -1), false); }
        String input(ServerPlayer p, int slot) {
            long serial = inputs.merge(p, 1L, Long::sum); var state = AbilityStorage.get(p);
            long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), state);
            return new HighPowerInput.Input(slot, serial, p.getId(), epoch).wire();
        }
        void sweep(List<ServerPlayer> casters, Runnable released, Runnable finished) {
            Map<ServerPlayer, String> wires = new IdentityHashMap<>(); List<PaymentWitness> payments = new ArrayList<>();
            for (ServerPlayer caster : casters) {
                caster.setShiftKeyDown(false); String input = input(caster, 0); wires.put(caster, input);
                PaymentWitness payment = new PaymentWitness(caster); payments.add(payment); hook(payment);
                h.assertTrue(DisintegrationSweep.start(caster, input), "authenticated actual combat charge starts");
            }
            h.runAfterDelay(8, () -> {
                for (ServerPlayer caster : casters) {
                    origins.put(caster, DualPoint.hand(caster, 0));
                    h.assertTrue(DisintegrationSweep.release(caster, wires.get(caster)), "release after eight native ticks commits the paid sweep");
                }
                released.run(); h.runAfterDelay(11, () -> {
                    for (PaymentWitness payment : payments) {
                        h.assertTrue(payment.calls > 0, "the authenticated sweep reached a real skill/native target decision");
                        h.assertTrue(payment.paid, "one exact native CP debit and one fixed cooldown authorize every emission, including after passive recovery");
                    }
                    finished.run();
                });
            });
        }
        void ready(Runnable action) {
            if (!pvp) { acquirePvp(h); pvp = true; }
            h.runAfterDelay(61, () -> {
                for (ServerPlayer p : players) { p.doTick(); p.setPos(positions.get(p)); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
                action.run();
            });
        }
        void armPlates(ServerPlayer p, float yaw) {
            p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", "6");
            var state = AbilityStorage.get(p); state.learn(HighPowerSkills.SCATTER_PLATES); state.presets.edit(0, 0, HighPowerSkills.SCATTER_PLATES, state::learned);
            h.assertTrue(ScatterPlateStorage.ensure(p).isPresent(), "native three-way shield shares exist"); face(p, yaw);
            h.assertTrue(ScatterPlates.toggle(p, input(p, 0)), "real paid NEW212 activation");
        }
        void aim(ServerPlayer caster, LivingEntity target) {
            Vec3 d = target.getBoundingBox().getCenter().subtract(caster.getEyePosition());
            caster.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            caster.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        }
        void ally(ServerPlayer caster, LivingEntity target) {
            var board = h.getLevel().getScoreboard(); PlayerTeam team = board.addPlayerTeam("ds" + UUID.randomUUID().toString().substring(0, 10));
            team.setAllowFriendlyFire(false); board.addPlayerToTeam(caster.getScoreboardName(), team); board.addPlayerToTeam(target.getScoreboardName(), team); teams.add(team);
        }
        void change(BlockPos pos, net.minecraft.world.level.block.Block block) { changedBlocks.add(pos); h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState()); }
        DamageSource nativeSource(ServerPlayer caster, Vec3 origin) { return new DamageSource(caster.damageSources().playerAttack(caster).typeHolder(), caster, caster, origin); }
        Observation observe(LivingEntity target) { var result = new Observation(target); hook(result); return result; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void restoreConfig() { if (changedConfig) { AcademyConfig.DAMAGE_SCALE.set(oldGlobal); AcademyConfig.SKILL_NUMERIC_OVERRIDES.set(oldLocal); changedConfig = false; } }
        void close() {
            if (closed) return; closed = true; restoreConfig(); hooks.forEach(NeoForge.EVENT_BUS::unregister); teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);
            for (ServerPlayer p : players) {
                DisintegrationSweep.remove(p); DualPoint.remove(p); FoldedMirrorArray.remove(p); ScatterPlates.remove(p); ThermalShell.remove(p);
                GroundingReturn.remove(p); PotentialBuffer.remove(p); MagneticDomain.remove(p); AbilityStorage.remove(p); h.getLevel().getServer().getPlayerList().remove(p);
            }
            entities.forEach(Entity::discard); changedBlocks.forEach(pos -> h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            channels.forEach(EmbeddedChannel::finishAndReleaseAll); if (pvp) releasePvp(h);
        }
    }
    private static int pvpFixtures; private static boolean originalPvp;
    private static void acquirePvp(GameTestHelper h) { if (pvpFixtures++ == 0) { originalPvp = h.getLevel().getServer().isPvpAllowed(); h.getLevel().getServer().setPvpAllowed(true); } }
    private static void releasePvp(GameTestHelper h) { if (--pvpFixtures == 0) h.getLevel().getServer().setPvpAllowed(originalPvp); }
}
