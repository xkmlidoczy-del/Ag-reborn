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
import net.minecraft.world.entity.monster.Zombie;
import java.math.RoundingMode;
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

/** NEW251 native damage is exercised only through distinct authenticated deploy and fire inputs.
 * Learned bits, target HP and armor are explicit fixtures; learning has a separate paid suite. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerMulticoreCanopyDamageRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_multicore_canopy_damage";
    private static final String ID = "multicore_canopy", SKILL = "meltdowner." + ID;
    private HighPowerMulticoreCanopyDamageRuntimeTests() {}
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal hp(LivingEntity target) { return HighPowerCombat.enabled(target)
            ? n(HighPowerCombat.healthText(target)) : new BigDecimal(Float.toString(target.getHealth())); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }
    private static void near(GameTestHelper h, double actual, double expected, String why) {
        h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " != " + expected);
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_01",timeoutTicks=60)
    public static void six_native_origins_share_exactly_one_whole_four_d_budget(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(16, 4);
        List<Zombie> targets = new ArrayList<>(); List<Observation> hits = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            Zombie target = f.target(caster, -2.5 + i, 0, 22, true, true); targets.add(target); hits.add(f.observe(target));
        }
        BigDecimal budget = HighPowerGrowth.values(caster).damage().multiply(n("4")).min(CombatAmounts.CAP);
        BigDecimal share = budget.divide(n("6"), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        f.deploy(List.of(caster), 6, () -> {
            List<Vec3> cores = MulticoreCanopy.cores(caster).stream().map(MulticoreCanopy.CoreVisual::center).toList();
            h.assertTrue(new HashSet<>(cores).size() == 6, "all six real fixed emitter positions are distinct");
            double cp = HighPowerResources.cp(caster); BigDecimal credits = f.credits.get(caster);
            String fire = f.input(caster, 0);
            h.assertTrue(MulticoreCanopy.start(caster, fire) && MulticoreCanopy.release(caster, fire), "distinct tap freezes one six-core salvo");
            h.runAfterDelay(16, () -> {
                BigDecimal spent = BigDecimal.ZERO; Set<Vec3> sources = new HashSet<>();
                for (int i = 0; i < 6; i++) {
                    BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(targets.get(i))); spent = spent.add(loss);
                    h.assertTrue(loss.compareTo(share) >= 0 && loss.subtract(share).compareTo(BigDecimal.ONE.scaleByPowerOfTen(-255)) < 0,
                            "each frozen target owns one sixth, with only the final exact decimal remainder");
                    h.assertTrue(hits.get(i).skills == 1 && hits.get(i).posts == 1, "each selected identity has exactly one native completion");
                    source(h, hits.get(i).source, caster, hits.get(i).source.getSourcePosition()); sources.add(hits.get(i).source.getSourcePosition());
                }
                eq(h, spent, budget, "six native health debits conserve the single whole-fire four-D amount below CAP");
                h.assertTrue(sources.equals(new HashSet<>(cores)), "each of the six native sources is its own actual fixed core");
                var receipt = MulticoreCanopy.receipt(caster);
                h.assertTrue(receipt != null && receipt.paid() && receipt.slots() == 6 && receipt.attempts() == 6 && receipt.successes() == 6,
                        "the committed paid receipt closes exactly six simultaneous slots");
                near(h, f.debit(caster, cp, credits), .2, "the entire six-origin salvo pays one LV5 fire cost");
                h.assertFalse(HighPowerCombat.attack(caster, SKILL, targets.getFirst(), BigDecimal.ONE), "generic skill entry cannot mint a certified core permit");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_02",timeoutTicks=60)
    public static void one_target_reused_by_six_cores_loses_only_one_native_iframe_share(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(16, 4);
        Zombie target = f.target(caster, 0, 0, 18, true, false); Observation hit = f.observe(target);
        BigDecimal budget = HighPowerGrowth.values(caster).damage().multiply(n("4")).min(CombatAmounts.CAP);
        BigDecimal share = budget.divide(n("6"), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        f.deploy(List.of(caster), 6, () -> {
            double cp = HighPowerResources.cp(caster); BigDecimal credits = f.credits.get(caster);
            String fire = f.input(caster, 0);
            h.assertTrue(MulticoreCanopy.start(caster, fire) && MulticoreCanopy.release(caster, fire), "one frozen hostile is reused round-robin by six cores");
            h.runAfterDelay(16, () -> {
                var receipt = MulticoreCanopy.receipt(caster);
                h.assertTrue(receipt != null && receipt.slots() == 6 && receipt.frozenTargets() == 1 && receipt.attempts() == 6,
                        "fewer targets never shrink the six-core denominator or skip native attempts");
                h.assertTrue(hit.skills == 6 && hit.posts == 1, "equal simultaneous shares retain vanilla hurt cooldown");
                eq(h, cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target)), share, "discarded iframe shares are never reassigned to the first hit");
                near(h, f.debit(caster, cp, credits), .2, "iframe misses still belong to the single paid command");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_03",timeoutTicks=140)
    public static void held_command_freezes_all_seventeen_unique_targets_once_and_spaces_native_hits_five_ticks(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(16, 4);
        List<Zombie> targets = new ArrayList<>(); List<Observation> hits = new ArrayList<>();
        for (int row = 0; row < 4; row++) for (int column = 0; column < 4; column++) {
            Zombie target = f.target(caster, -2.25 + column * 1.5, -1.65 + row * 1.1, 22, true, true);
            targets.add(target); hits.add(f.observe(target));
        }
        Zombie extra = f.target(caster, 0, 2.9, 22, true, true); targets.add(extra); hits.add(f.observe(extra));
        BigDecimal budget = HighPowerGrowth.values(caster).damage().multiply(n("4")).min(CombatAmounts.CAP);
        BigDecimal share = budget.divide(n("17"),CombatAmounts.MAX_ABSOLUTE_SCALE,RoundingMode.DOWN);
        BigDecimal remainder=budget.subtract(share.multiply(n("16")));
        f.deploy(List.of(caster), 6, () -> {
            List<Vec3> cores = MulticoreCanopy.cores(caster).stream().map(MulticoreCanopy.CoreVisual::center).toList();
            double cp = HighPowerResources.cp(caster); BigDecimal credits = f.credits.get(caster);
            h.assertTrue(MulticoreCanopy.start(caster, f.input(caster, 0)), "a held second press freezes all seventeen identities before payment");
            h.runAfterDelay(97, () -> {
                BigDecimal spent = BigDecimal.ZERO; List<Long> ticks = new ArrayList<>(); Set<Vec3> used = new HashSet<>(); int damaged = 0;
                for (int i = 0; i < targets.size(); i++) {
                    BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(targets.get(i))); spent = spent.add(loss);
                    h.assertTrue(hits.get(i).skills <= 1 && hits.get(i).posts <= 1, "a frozen hostile is attempted once in the whole held command");
                    if (loss.signum() > 0) { damaged++; h.assertTrue(loss.compareTo(share)==0||loss.compareTo(remainder)==0,"one-seventeenth share or final decimal remainder"); ticks.add(hits.get(i).postTick); used.add(hits.get(i).source.getSourcePosition()); }
                }
                Collections.sort(ticks);
                h.assertTrue(damaged == 17 && ticks.size() == 17, "the seventeenth initial hostile retains its own slot");
                for (int i = 1; i < ticks.size(); i++) h.assertTrue(ticks.get(i) - ticks.get(i - 1) == 5, "held slots execute at physical five-tick spacing without catch-up");
                eq(h, spent, budget, "seventeen independent native hits close one whole four-D ledger");
                h.assertTrue(used.equals(new HashSet<>(cores)), "held slots use the same six frozen core identities");
                List<Observation> ordered = hits.stream().filter(hit -> hit.posts == 1).sorted(Comparator.comparingLong(hit -> hit.postTick)).toList();
                for (int i = 0; i < ordered.size(); i++) h.assertTrue(ordered.get(i).source.getSourcePosition().equals(cores.get(i % 6)),
                        "each held native source follows the frozen core-ID cycle at slot " + i);
                var receipt = MulticoreCanopy.receipt(caster);
                h.assertTrue(receipt != null && receipt.held() && receipt.paid() && receipt.slots() == 17 && receipt.emittedSlots() == 17,
                        "the finite held command has exhausted all seventeen slots");
                near(h, f.debit(caster, cp, credits), .2, "seventeen native hits have one fire debit"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_04",timeoutTicks=60)
    public static void late_canceled_and_moved_slots_never_redistribute_or_adopt_replacement_bodies(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(16, 4);
        List<Zombie> targets = new ArrayList<>(); Map<Zombie, Observation> observed = new IdentityHashMap<>();
        for (int i = 0; i < 6; i++) {
            Zombie target = f.target(caster, -2.5 + i, 0, 22, true, true); targets.add(target); observed.put(target, f.observe(target));
        }
        BigDecimal budget = HighPowerGrowth.values(caster).damage().multiply(n("4")).min(CombatAmounts.CAP);
        BigDecimal share = budget.divide(n("6"), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        List<Zombie> replacement = new ArrayList<>(); Set<LivingEntity> surviving = new HashSet<>(targets); boolean[] called = {false};
        f.hook(new Object() {
            @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e) {
                if (e.player != caster || !e.skill.equals(SKILL) || called[0]) return;
                called[0] = true; surviving.remove(e.target); e.amount = 0;
                Zombie moved = targets.stream().filter(t -> t != e.target).findFirst().orElseThrow(); surviving.remove(moved);
                Vec3 original = moved.position(); f.moveTargetAway(moved);
                Zombie body = f.mob(original, true); body.setBaby(true); replacement.add(body);
            }
        });
        f.deploy(List.of(caster), 6, () -> {
            double cp = HighPowerResources.cp(caster); BigDecimal credits = f.credits.get(caster); String fire = f.input(caster, 0);
            h.assertTrue(MulticoreCanopy.start(caster, fire) && MulticoreCanopy.release(caster, fire), "six immutable target slots freeze before native callbacks");
            h.runAfterDelay(16, () -> {
                h.assertTrue(called[0] && replacement.size() == 1, "first real allocated attack zeroes itself and moves a later frozen identity");
                for (Zombie target : targets) {
                    BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(target));
                    if (surviving.contains(target)) h.assertTrue(loss.compareTo(share) >= 0 && loss.subtract(share).compareTo(BigDecimal.ONE.scaleByPowerOfTen(-255)) < 0,
                            "a surviving frozen slot retains its original sixth");
                    else eq(h, loss, BigDecimal.ZERO, "late invalid target's own allocation is discarded; events=" + observed.get(target).events);
                }
                eq(h, hp(replacement.getFirst()), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "replacement body at the same position cannot inherit the old identity");
                near(h, f.debit(caster, cp, credits), .2, "late misses keep the one already-paid debit"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_05",timeoutTicks=35)
    public static void armor_toughness_resistance_protection_and_absorption_match_native_player_attack(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Zombie> controls = new ArrayList<>();
        List<Observation> nativeHits = new ArrayList<>(); List<BigDecimal> before = new ArrayList<>();
        for (Case c : cases) {
            Zombie control = f.control(c); controls.add(control); defenses(h, c.target); defenses(h, control);
            f.hook(new Discount(c.target)); nativeHits.add(f.observe(control)); before.add(hp(c.target));
        }
        f.fire(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Zombie control = controls.get(i);
                h.assertTrue(control.hurt(f.nativeSource(c.caster, translatedOrigin(f, c, control)), 5), "matching native reducer control accepts");
            }
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Observation hit = c.hit, nativeHit = nativeHits.get(i);
                h.assertTrue(hit.posts == 1 && nativeHit.posts == 1 && hit.armor > 0 && hit.effects > 0 && hit.enchantments > 0 && hit.absorption == .5F,
                        "one native pipeline executes every ordinary partial reducer");
                h.assertTrue(hit.armor == nativeHit.armor && hit.effects == nativeHit.effects && hit.enchantments == nativeHit.enchantments
                        && hit.absorption == nativeHit.absorption && Math.abs(hit.damage - nativeHit.damage) < .00001,
                        "enrolled and ordinary canopy observations match the native PLAYER_ATTACK counterpart");
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

    @GameTest(template=TEMPLATE,batch=BATCH+"_06",timeoutTicks=60)
    public static void held_shield_front_and_rear_match_the_actual_core_side_on_both_target_kinds(GameTestHelper h) { shield(h, false); }

    private static void shield(GameTestHelper h, boolean rear) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Zombie> controls = new ArrayList<>(); List<Observation> nativeHits = new ArrayList<>();
        List<BigDecimal> before = new ArrayList<>(); float yaw = rear ? 0 : 180;
        for (Case c : cases) {
            Zombie control = f.control(c); controls.add(control); raiseShield(c.target, yaw); raiseShield(control, yaw);
            f.hook(new Discount(c.target)); nativeHits.add(f.observe(control)); before.add(hp(c.target));
        }
        f.fire(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Zombie control = controls.get(i); face(c.target, yaw); face(control, yaw);
                h.assertTrue(c.target.isBlocking() && control.isBlocking(), "native shield raising delay has actually elapsed during the firing warning");
                h.assertTrue(control.hurt(f.nativeSource(c.caster, translatedOrigin(f, c, control)), 5) == rear, "native control makes the expected directional decision");
            }
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Observation nativeHit = nativeHits.get(i);
                eq(h, hp(c.target), rear ? before.get(i).subtract(n("5")) : before.get(i), "actual core arrival side governs enrolled and ordinary shielding");
                h.assertTrue(c.hit.blocked == !rear && c.hit.blocked == nativeHit.blocked
                        && c.hit.blockedDamage == nativeHit.blockedDamage && c.hit.damage == nativeHit.damage,
                        "native block decision, blocked amount and residual match the counterpart");
                source(h, c.hit.source, c.caster, f.origins.get(c.caster));
            }
            if (rear) h.succeed(); else { f.close(); shield(h, true); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_07",timeoutTicks=60)
    public static void same_rank_native_iframes_do_not_consume_a_second_absorption_or_health_payment(GameTestHelper h) { iframes(h, false); }

    private static void iframes(GameTestHelper h, boolean absorb) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Zombie> controls = new ArrayList<>(); List<BigDecimal> afterFirst = new ArrayList<>();
        for (Case c : cases) {
            Zombie control = f.control(c); controls.add(control); f.hook(new Discount(c.target));
            for (LivingEntity target : List.of(c.target, control)) target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            if (absorb) for (LivingEntity target : List.of(c.target, control)) {
                target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); target.setAbsorptionAmount(10);
            }
        }
        f.fire(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Zombie control = controls.get(i);
                h.assertTrue(c.target.hurt(f.nativeSource(c.caster, f.origins.get(c.caster)), 5), "first ordinary native hit accepts on the actual target");
                h.assertTrue(control.hurt(f.nativeSource(c.caster, translatedOrigin(f, c, control)), 5), "first paired native control accepts");
                afterFirst.add(hp(c.target));
            }
            h.runAfterDelay(5, () -> {
                for (int i = 0; i < cases.size(); i++) {
                    Case c = cases.get(i); Zombie control = controls.get(i);
                    h.assertFalse(control.hurt(f.nativeSource(c.caster, translatedOrigin(f, c, control)), 5), "equal native control is still in the real hurt cooldown");
                }
            });
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); eq(h, hp(c.target), afterFirst.get(i), "the authentic second NEW251 hit cannot bypass native iframe");
                h.assertTrue(c.hit.skills == 1 && c.hit.posts == 1, "one paid canopy attempt produces no second native completion");
                h.assertTrue(c.target.getAbsorptionAmount() == controls.get(i).getAbsorptionAmount()
                        && c.target.getAbsorptionAmount() == (absorb ? 5 : 0), "refused equal hit never spends absorption twice");
            }
            if (absorb) h.succeed(); else { f.close(); iframes(h, true); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_08",timeoutTicks=35)
    public static void lethal_native_completion_matches_ordinary_player_attack_on_both_target_kinds(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Zombie> controls = new ArrayList<>(); List<Observation> nativeHits = new ArrayList<>();
        for (Case c : cases) {
            Zombie control = f.control(c); controls.add(control); nativeHits.add(f.observe(control)); control.setHealth(1);
            if (HighPowerCombat.enabled(c.target)) c.target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            else c.target.setHealth(1);
            f.hook(new Discount(c.target));
        }
        f.fire(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Zombie control = controls.get(i);
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

    @GameTest(template=TEMPLATE,batch=BATCH+"_09",timeoutTicks=35)
    public static void native_death_veto_restores_the_exact_prelethal_state(GameTestHelper h) {
        Fixture f = new Fixture(h); Case c = f.caseAt(8, true); f.hook(new Discount(c.target));
        c.target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
        DeathVeto veto = new DeathVeto(c.target); f.hook(veto);
        f.fire(List.of(c.caster), () -> {}, () -> {
            h.assertTrue(veto.called && c.target.isAlive(), "real native death decision calls the cancellation hook");
            eq(h, hp(c.target), BigDecimal.ONE, "native death veto restores the enrolled prelethal ledger"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_10",timeoutTicks=35)
    public static void native_totems_rescue_enrolled_and_ordinary_targets_after_one_real_hit(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Zombie> controls = new ArrayList<>();
        for (Case c : cases) {
            Zombie control = f.control(c); controls.add(control); control.setHealth(1);
            if (HighPowerCombat.enabled(c.target)) c.target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            else c.target.setHealth(1);
            for (LivingEntity target : List.of(c.target, control)) target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            f.hook(new Discount(c.target));
        }
        f.fire(casters(cases), () -> {
            for (int i = 0; i < cases.size(); i++) controls.get(i).hurt(f.nativeSource(cases.get(i).caster, translatedOrigin(f, cases.get(i), controls.get(i))), 5);
        }, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Zombie control = controls.get(i);
                h.assertTrue(c.hit.posts == 1 && c.target.isAlive() && control.isAlive() && c.target.getOffhandItem().isEmpty()
                        && control.getOffhandItem().isEmpty() && hp(c.target).signum() > 0, "native totem is consumed once and rescues both target kinds");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_11",timeoutTicks=100)
    public static void native_zero_and_cancellation_discard_each_paid_target_attempt(GameTestHelper h) { amounts(h, 0, false); }
    @GameTest(template=TEMPLATE,batch=BATCH+"_12",timeoutTicks=100)
    public static void unknown_positive_skill_incoming_and_pre_amplification_is_refused_on_both_target_kinds(GameTestHelper h) { amounts(h, 0, true); }

    private static void amounts(GameTestHelper h, int stage, boolean amplify) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<AmountGate> gates = new ArrayList<>(); List<BigDecimal> before = new ArrayList<>();
        for (Case c : cases) {
            if (!(amplify && stage == 0)) f.hook(new Discount(c.target));
            AmountGate gate = new AmountGate(c.target, stage * 2 + (amplify ? 1 : 0)); gates.add(gate); f.hook(gate); before.add(hp(c.target));
        }
        f.fire(casters(cases), () -> {}, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); h.assertTrue(gates.get(i).called && c.hit.skills == 1, "requested real amount callback runs once at stage " + stage);
                eq(h, hp(c.target), before.get(i), "cancellation, zero or unknown positive amplification cannot damage either target kind");
                h.assertTrue(c.hit.damage == 0, "no positive native proxy residual escapes refusal");
                h.assertTrue(AbilityStorage.get(c.caster).exp(ID) == 0, "refused allocation awards no mastery");
            }
            if (stage == 2) h.succeed(); else { f.close(); amounts(h, stage + 1, amplify); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_13",timeoutTicks=35)
    public static void global_config_amplification_cannot_expand_a_frozen_target_allocation(GameTestHelper h) { config(h, false); }
    @GameTest(template=TEMPLATE,batch=BATCH+"_14",timeoutTicks=35)
    public static void skill_config_amplification_cannot_expand_a_frozen_target_allocation(GameTestHelper h) { config(h, true); }

    private static void config(GameTestHelper h, boolean local) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<BigDecimal> before = cases.stream().map(c -> hp(c.target)).toList();
        f.fire(casters(cases), () -> {
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

    @GameTest(template=TEMPLATE,batch=BATCH+"_15",timeoutTicks=260)
    public static void skill_callbacks_cannot_move_retarget_reteam_or_change_a_paid_segment(GameTestHelper h) { mutations(h, 0, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH+"_16",timeoutTicks=260)
    public static void incoming_callbacks_cannot_move_retarget_reteam_or_change_a_paid_segment(GameTestHelper h) { mutations(h, 1, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH+"_17",timeoutTicks=260)
    public static void pre_callbacks_cannot_move_retarget_reteam_or_change_a_paid_segment(GameTestHelper h) { mutations(h, 2, 0); }

    private static void mutations(GameTestHelper h, int stage, int mode) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<Mutation> gates = new ArrayList<>(); List<BigDecimal> before = new ArrayList<>();
        for (Case c : cases) { f.hook(new Discount(c.target)); Mutation gate = new Mutation(f, c, stage, mode); gates.add(gate); f.hook(gate); before.add(hp(c.target)); }
        f.fire(casters(cases), () -> {}, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); Mutation gate = gates.get(i);
                h.assertTrue(gate.called && c.hit.skills == 1, "real callback executes one captured target attempt, stage=" + stage + " mode=" + mode);
                eq(h, hp(c.target), before.get(i), "callback invalidation cannot debit either native target kind, stage=" + stage + " mode=" + mode + " events=" + c.hit.events);
                h.assertTrue(c.hit.damage == 0, "late callback invalidation prevents positive proxy damage");
                if (gate.replacement != null) eq(h, hp(gate.replacement), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "replacement identity cannot inherit a consumed native permit");
                if (mode == 9) h.assertFalse(gate.reentered, "native callback cannot create a second command while a permit is resolving");
                if (mode != 4) h.assertTrue(AbilityStorage.get(c.caster).exp(ID) == 0, "invalidated geometry grants no productive mastery");
            }
            if (mode == 9) h.succeed(); else { f.close(); mutations(h, stage, mode + 1); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_18",timeoutTicks=30)
    public static void unreviewed_enchantments_fail_closed_without_creating_a_native_attack(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair();
        for (Case c : cases) {
            ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
            helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.THORNS), 1);
            c.target.setItemSlot(EquipmentSlot.HEAD, helmet);
        }
        f.deploy(casters(cases), 1, () -> {
            List<BigDecimal> before = cases.stream().map(c -> hp(c.target)).toList();
            for (Case c : cases) { String fire = f.input(c.caster, 0); if (MulticoreCanopy.start(c.caster, fire)) MulticoreCanopy.release(c.caster, fire); }
            h.runAfterDelay(16, () -> {
                for (int i = 0; i < cases.size(); i++) {
                    Case c = cases.get(i); eq(h, hp(c.target), before.get(i), "unreviewed native equipment preserves either health ledger");
                    h.assertTrue(c.hit.posts == 0 && c.hit.skills == 0 && AbilityStorage.get(c.caster).exp(ID) == 0,
                            "closed enchantment contract creates no native damage or mastery");
                }
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_19",timeoutTicks=110)
    public static void first_solid_passive_allied_hostile_and_other_player_stop_the_physical_core_ray(GameTestHelper h) { blocker(h, 0); }

    private static void blocker(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(16, 4);
        Zombie target = f.target(caster, 0, 0, 9, true, false); Observation hit = f.observe(target);
        List<LivingEntity> bodies = new ArrayList<>(); List<BigDecimal> health = new ArrayList<>();
        f.deploy(List.of(caster), 1, () -> {
            Vec3 middle = f.origins.get(caster).lerp(target.getBoundingBox().getCenter(), .5);
            if (mode == 0) f.change(BlockPos.containing(middle), Blocks.STONE);
            else {
                LivingEntity body;
                if (mode == 1) {
                    Villager villager = new Villager(EntityType.VILLAGER, h.getLevel()); villager.setNoAi(true); villager.setNoGravity(true);
                    villager.setPos(middle.add(0, -villager.getBbHeight() / 2, 0)); h.getLevel().addFreshEntity(villager); f.entities.add(villager);
                    h.assertTrue(HighPowerCombat.enroll(villager, 5), "passive blocker has a real protected exact HP ledger"); body = villager;
                } else if (mode == 2) {
                    Zombie ally = f.mob(middle, true); ally.setPos(middle.add(0, -ally.getBbHeight() / 2, 0)); f.ally(caster, ally); body = ally;
                } else {
                    ServerPlayer player = f.caster(25, 25); player.setPos(middle.add(0, -player.getBbHeight() / 2, 0)); player.connection.resetPosition(); body = player;
                }
                bodies.add(body); health.add(hp(body));
            }
            double cp = HighPowerResources.cp(caster); BigDecimal credits = f.credits.get(caster);
            String fire = f.input(caster, 0); if (MulticoreCanopy.start(caster, fire)) MulticoreCanopy.release(caster, fire);
            h.runAfterDelay(16, () -> {
                eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "first physical obstruction prevents harming the hostile behind it, kind=" + mode);
                h.assertTrue(hit.posts == 0 && AbilityStorage.get(caster).exp(ID) == 0, "blocked physical path cannot grant native damage or mastery");
                for (int i = 0; i < bodies.size(); i++) eq(h, hp(bodies.get(i)), health.get(i), "non-hostile first body blocks without being harmed");
                near(h, f.debit(caster, cp, credits), 0, "no valid native opportunity means no fire CP payment");
                if (mode == 3) h.succeed(); else { f.close(); blocker(h, mode + 1); }
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_20",timeoutTicks=60)
    public static void one_retired_core_discards_its_original_sixth_without_core_or_target_substitution(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(16, 4);
        Map<Integer, Zombie> targets = new HashMap<>();
        for (int i = 0; i < 6; i++) { Zombie target = f.target(caster, -2.5 + i, 0, 22, true, true); targets.put(target.getId(), target); }
        BigDecimal budget = HighPowerGrowth.values(caster).damage().multiply(n("4")).min(CombatAmounts.CAP);
        BigDecimal share = budget.divide(n("6"), CombatAmounts.MAX_ABSOLUTE_SCALE, RoundingMode.DOWN);
        f.deploy(List.of(caster), 6, () -> {
            List<MulticoreCanopy.CoreVisual> cores = MulticoreCanopy.cores(caster);
            double cp = HighPowerResources.cp(caster); BigDecimal credits = f.credits.get(caster); String fire = f.input(caster, 0);
            h.assertTrue(MulticoreCanopy.start(caster, fire), "six exact core and target identities freeze before the world changes");
            List<MulticoreCanopy.TargetVisual> frozen = MulticoreCanopy.targets(caster);
            h.assertTrue(frozen.size() == 6 && MulticoreCanopy.release(caster, fire), "tap retains the full frozen six-slot denominator");
            f.change(BlockPos.containing(cores.get(1).center()), Blocks.STONE);
            h.runAfterDelay(16, () -> {
                for (int i = 0; i < 6; i++) {
                    BigDecimal loss = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(hp(targets.get(frozen.get(i).id())));
                    BigDecimal expected = i == 1 ? BigDecimal.ZERO : i == 5 ? budget.subtract(share.multiply(n("5"))) : share;
                    eq(h, loss, expected, "late core retirement preserves the original allocation for slot " + i);
                }
                var receipt = MulticoreCanopy.receipt(caster);
                h.assertTrue(receipt != null && receipt.slots() == 6 && receipt.emittedSlots() == 6 && receipt.attempts() == 5
                        && receipt.path().stream().noneMatch(ray -> ray.coreId() == cores.get(1).id()),
                        "unavailable core cannot be replaced by another emitter or shrink the denominator");
                near(h, f.debit(caster, cp, credits), .2, "five surviving native opportunities still make one fire payment"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_21_dimension",timeoutTicks=85)
    public static void native_callbacks_cannot_continue_a_core_emission_after_owner_dimension_changes(GameTestHelper h) { dimension(h, 0); }

    private static void dimension(GameTestHelper h, int stage) {
        Fixture f = new Fixture(h); Case c = f.caseAt(16, true); f.hook(new Discount(c.target));
        var other = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
        h.assertTrue(other != null, "real alternate native server level exists");
        other.getChunk(c.caster.blockPosition()); boolean[] called = {false};
        f.hook(new Object() {
            void change() {
                if (called[0]) return; called[0] = true;
                c.caster.teleportTo(other, c.caster.getX(), c.caster.getY(), c.caster.getZ(), Set.of(), 0, 0);
            }
            @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e) { if (stage == 0 && e.target == c.target && e.skill.equals(SKILL)) change(); }
            @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) { if (stage == 1 && e.getEntity() == c.target) change(); }
            @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e) { if (stage == 2 && e.getEntity() == c.target) change(); }
        });
        f.fire(List.of(c.caster), () -> {}, () -> {
            h.assertTrue(called[0] && c.caster.serverLevel() == other, "the real native callback changed owner world at stage " + stage);
            eq(h, hp(c.target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "old-world target cannot be damaged through the detached core permit");
            h.assertTrue(c.hit.damage == 0 && !MulticoreCanopy.active(c.caster) && AbilityStorage.get(c.caster).exp(ID) == 0,
                    "dimension change cancels finite controller state and prevents native residual damage or mastery");
            if (stage == 2) h.succeed(); else { f.close(); dimension(h, stage + 1); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_22_late_absorption",timeoutTicks=250)
    public static void absorption_reduction_callbacks_cannot_spend_stale_health_or_absorption_after_native_pre(GameTestHelper h) { lateAbsorption(h, 0); }

    private static void lateAbsorption(GameTestHelper h, int mode) {
        Fixture f = new Fixture(h); List<Case> cases = f.pair(); List<BigDecimal> before = new ArrayList<>();
        List<Vec3> ownerPositions = cases.stream().map(c -> c.caster.position()).toList();
        List<Vec3> targetPositions = cases.stream().map(c -> c.target.position()).toList();
        List<LateAbsorptionMutation> gates = new ArrayList<>();
        for (Case c : cases) {
            c.target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0)); c.target.setAbsorptionAmount(2);
            before.add(hp(c.target)); f.hook(new Discount(c.target));
            var gate = new LateAbsorptionMutation(f, c, mode); gates.add(gate); f.hook(gate);
        }
        f.fire(casters(cases), () -> {}, () -> {
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); LateAbsorptionMutation gate = gates.get(i);
                h.assertTrue(gate.calls == 1 && gate.afterPre && gate.poolAtCallback == 2 && gate.unchangedReduction == 2,
                        "a real positive ABSORPTION reducer mutated context after native Pre while returning its unchanged reduction, mode=" + mode);
                eq(h, hp(c.target), before.get(i), "late context invalidation cannot leave a stale native HP debit, mode=" + mode);
                h.assertTrue(c.target.getAbsorptionAmount() == 2, "late context invalidation cannot consume the original absorption pool, mode=" + mode);
                h.assertTrue(c.hit.damage == 0 && AbilityStorage.get(c.caster).exp(ID) == 0,
                        "the canceled late attempt yields neither native residual damage nor mastery, mode=" + mode);
            }
            // Restore only deliberate geometric fixtures. Native iframe state and every saved fence remain untouched.
            for (int i = 0; i < cases.size(); i++) {
                Case c = cases.get(i); c.target.setPos(targetPositions.get(i)); c.caster.setPos(ownerPositions.get(i)); c.caster.connection.resetPosition();
            }
            h.runAfterDelay(21, () -> {
                List<BigDecimal> afterOrdinary = new ArrayList<>();
                for (int i = 0; i < cases.size(); i++) {
                    Case c = cases.get(i);
                    h.assertTrue(c.target.hurt(f.nativeSource(c.caster, f.origins.get(c.caster)), 5),
                            "a later real native PLAYER_ATTACK accepts after natural cooldown expiry, mode=" + mode);
                    eq(h, hp(c.target), before.get(i).subtract(n("3")),
                            "the fresh ordinary hit spends exactly HP3 after the preserved absorption2, mode=" + mode);
                    h.assertTrue(c.target.getAbsorptionAmount() == 0 && gates.get(i).calls == 1,
                            "ordinary combat has no stranded absorption reservation and the mutation remains one-use");
                    afterOrdinary.add(hp(c.target));
                }
                f.fire(casters(cases), () -> {}, () -> {
                    for (int i = 0; i < cases.size(); i++) {
                        Case c = cases.get(i);
                        eq(h, hp(c.target), afterOrdinary.get(i).subtract(n("5")),
                                "a fresh authorized deployment and native cast succeeds after the canceled frame, mode=" + mode);
                        h.assertTrue(c.target.getAbsorptionAmount() == 0 && gates.get(i).calls == 1 && AbilityStorage.get(c.caster).exp(ID) > 0,
                                "fresh authorized combat has no stranded native frame, absorption debit or missing productive mastery");
                    }
                    if (mode == 2) h.succeed(); else { f.close(); lateAbsorption(h, mode + 1); }
                });
            });
        });
    }

    public static final class LateAbsorptionMutation {
        final Fixture fixture; final Case c; final int mode; boolean sawPre, afterPre; int calls; float poolAtCallback, unchangedReduction;
        LateAbsorptionMutation(Fixture fixture, Case c, int mode) { this.fixture = fixture; this.c = c; this.mode = mode; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) {
            if (e.getEntity() != c.target || calls != 0) return;
            e.addReductionModifier(DamageContainer.Reduction.ABSORPTION, (container, reduction) -> {
                calls++; afterPre = sawPre; poolAtCallback = c.target.getAbsorptionAmount(); unchangedReduction = reduction;
                if (mode == 0) fixture.moveTargetAway(c.target);
                else if (mode == 1) c.caster.setPos(c.caster.position().add(.1, 0, 0));
                else MulticoreCanopy.abort(c.caster);
                return reduction;
            });
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void pre(LivingDamageEvent.Pre e) { if (e.getEntity() == c.target) sawPre = true; }
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_23_input_fence",timeoutTicks=35)
    public static void early_second_press_is_refused_until_five_real_ticks_have_elapsed(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(16, 4);
        Zombie target = f.target(caster, 0, 0, 9, true, false); Observation hit = f.observe(target);
        String deploy = f.input(caster, 0);
        h.assertTrue(MulticoreCanopy.start(caster, deploy) && MulticoreCanopy.release(caster, deploy), "first real input deploys and arms one core");
        h.assertTrue(MulticoreCanopy.cores(caster).size() == 1, "initial deployment owns one paid fixed core");
        h.runAfterDelay(1, () -> {
            double cp = HighPowerResources.cp(caster); BigDecimal credits = f.credits.get(caster);
            h.assertFalse(MulticoreCanopy.start(caster, f.input(caster, 0)), "fresh second serial still fails the five-physical-tick input fence");
            h.assertTrue(MulticoreCanopy.active(caster) && MulticoreCanopy.cores(caster).size() == 1 && hit.skills == 0,
                    "early refusal preserves the armed deployment without a native attempt");
            near(h, f.debit(caster, cp, credits), 0, "early second-press refusal spends no extra CP");
            h.runAfterDelay(5, () -> {
                String fire = f.input(caster, 0);
                h.assertTrue(MulticoreCanopy.start(caster, fire) && MulticoreCanopy.release(caster, fire), "a new serial after six real ticks can freeze and commit the salvo");
                h.runAfterDelay(16, () -> {
                    h.assertTrue(hit.skills == 1 && hit.posts == 1, "the late valid press reaches exactly one native attack"); h.succeed();
                });
            });
        });
    }

    private static String damageType(DamageSource source) {
        return source.typeHolder().unwrapKey().map(key -> key.location().toString()).orElse("unkeyed");
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
                || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION), "NEW251 adds no native bypass or unrelated damage family");
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
        final Fixture fixture; final Case c; final int stage, mode; boolean called, reentered; Zombie replacement;
        Mutation(Fixture fixture, Case c, int stage, int mode) { this.fixture = fixture; this.c = c; this.stage = stage; this.mode = mode; }
        private void change() {
            if (called) return; called = true;
            switch (mode) {
                case 0 -> fixture.moveTargetAway(c.target);
                case 1 -> c.caster.setPos(c.caster.position().add(.1, 0, 0));
                case 2 -> fixture.change(BlockPos.containing(fixture.origins.get(c.caster).lerp(c.target.getBoundingBox().getCenter(), .5)), Blocks.STONE);
                case 3 -> fixture.ally(c.caster, c.target);
                case 4 -> AbilityStorage.get(c.caster).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
                case 5 -> { Vec3 old = c.target.position(); fixture.moveTargetAway(c.target); replacement = fixture.mob(old, true); }
                case 6 -> c.target.setUUID(UUID.randomUUID());
                case 7 -> AbilityStorage.get(c.caster).presets.edit(0, 0, "", ignored -> true);
                case 8 -> fixture.change(BlockPos.containing(fixture.origins.get(c.caster)), Blocks.STONE);
                default -> {
                    reentered = MulticoreCanopy.start(c.caster, fixture.input(c.caster, 0));
                    c.target.setUUID(UUID.randomUUID());
                }
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
        long postTick; final List<String> events = new ArrayList<>();
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void skill(SkillAttackEvent e) { if (e.target == target && e.skill.equals(SKILL)) skills++; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) {
            if (e.getEntity() != target) return; source = e.getSource();
            if (events.size() < 32) events.add("incoming:" + damageType(source) + ":" + e.getAmount() + "@" + target.position());
        }
        @SubscribeEvent public void shield(LivingShieldBlockEvent e) { if (e.getEntity() == target) { blocked = e.getBlocked(); blockedDamage = e.getBlockedDamage(); } }
        @SubscribeEvent public void death(LivingDeathEvent e) { if (e.getEntity() == target) deaths++; }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) {
            if (e.getEntity() != target) return; posts++; postTick = target.getServer().getTickCount(); damage = e.getNewDamage();
            if (events.size() < 32) events.add("post:" + damageType(e.getSource()) + ":" + damage + "@" + target.position());
            armor = e.getReduction(DamageContainer.Reduction.ARMOR);
            effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS);
            absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
    public static final class PlayerPump {
        final Fixture fixture; final ServerPlayer player; int last;
        PlayerPump(Fixture fixture, ServerPlayer player) { this.fixture = fixture; this.player = player; last = player.server.getTickCount(); }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void tick(ServerTickEvent.Post e) {
            if (e.getServer() != player.server || player.server.getTickCount() <= last || fixture.closed || player.isRemoved()) return;
            last = player.server.getTickCount(); double before = HighPowerResources.cp(player); player.doTick();
            fixture.credits.merge(player, BigDecimal.valueOf(HighPowerResources.cp(player)).subtract(BigDecimal.valueOf(before)), BigDecimal::add);
        }
    }

    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final List<BlockPos> changedBlocks = new ArrayList<>(); final List<PlayerTeam> teams = new ArrayList<>();
        final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>(); final Map<ServerPlayer, Vec3> origins = new IdentityHashMap<>();
        final Map<ServerPlayer, BigDecimal> credits = new IdentityHashMap<>();
        final Map<Entity, UUID> originalUuids = new IdentityHashMap<>();
        boolean closed, changedConfig; double oldGlobal; List<? extends String> oldLocal;
        Fixture(GameTestHelper h) {
            this.h = h;
            // The real template's opaque perimeter isolates simultaneous tests from 96m acquisition.
            for (int y = 1; y <= 9; y++) for (int i = 0; i < 32; i++) {
                h.setBlock(new BlockPos(0, y, i), Blocks.STONE); h.setBlock(new BlockPos(31, y, i), Blocks.STONE);
                h.setBlock(new BlockPos(i, y, 0), Blocks.STONE); h.setBlock(new BlockPos(i, y, 31), Blocks.STONE);
            }
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer caster(double x, double z) {
            var server = h.getLevel().getServer(); UUID uuid = UUID.randomUUID();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(uuid, "HPC" + uuid.toString().substring(0, 10)), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection)); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie); p.setNoGravity(true); p.setGameMode(GameType.SURVIVAL);
            p.setPos(h.absoluteVec(new Vec3(x, 3, z))); p.connection.resetPosition(); face(p, 0);
            var state = AbilityStorage.get(p); state.changeCategoryClassic(HighPowerCategories.MELTDOWNER); state.setLevel(5);
            for (String id : List.of(HighPowerSkills.DUAL_POINT, HighPowerSkills.FOLDED_MIRROR_ARRAY,
                    HighPowerSkills.DISINTEGRATION_SWEEP, HighPowerSkills.PARTICLE_LAMINATE, ID)) state.learn(id);
            state.setActivateState(true); state.overloadFine = true; state.presets.edit(0, 0, ID, state::learned);
            h.assertTrue(HighPowerCombat.enroll(p, 5), "connected native LV5 MD fixture: " + HighPowerCombat.unsupportedReason(p));
            HighPowerResources.initializeBudget(p); credits.put(p, BigDecimal.ZERO); hook(new PlayerPump(this, p)); return p;
        }
        Zombie mob(Vec3 feet, boolean enrolled) {
            Zombie target = EntityType.HUSK.create(h.getLevel()); target.setNoAi(true); target.setNoGravity(true); target.setSilent(true);
            target.getAttribute(Attributes.ARMOR).setBaseValue(0); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
            target.setPos(feet); h.getLevel().addFreshEntity(target); entities.add(target); originalUuids.put(target, target.getUUID());
            if (enrolled) h.assertTrue(HighPowerCombat.enroll(target, 5), "reviewed native hostile target"); return target;
        }
        Zombie target(ServerPlayer caster, double dx, double dy, double dz, boolean enrolled, boolean baby) {
            Zombie target = mob(caster.getEyePosition().add(dx, dy, dz), enrolled); target.setBaby(baby);
            target.setPos(target.position().add(0, -target.getBbHeight() / 2, 0)); return target;
        }
        Case caseAt(double x, boolean enrolled) {
            ServerPlayer caster = caster(x, 4); Zombie target = target(caster, 0, 0, 9, enrolled, false);
            return new Case(caster, target, observe(target));
        }
        List<Case> pair() { return List.of(caseAt(8, true), caseAt(24, false)); }
        Zombie control(Case c) { return mob(c.caster.position().add(3, 0, -1), false); }
        String input(ServerPlayer p, int slot) {
            long serial = inputs.merge(p, 1L, Long::sum); var state = AbilityStorage.get(p);
            long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), state);
            return new HighPowerInput.Input(slot, serial, p.getId(), epoch).wire();
        }
        void deploy(List<ServerPlayer> casters, int count, Runnable armed) {
            Map<ServerPlayer, String> wires = new IdentityHashMap<>();
            for (ServerPlayer caster : casters) {
                String wire = input(caster, 0); wires.put(caster, wire);
                h.assertTrue(MulticoreCanopy.start(caster, wire), "first authenticated held input starts fixed-core deployment");
            }
            Runnable release = () -> {
                for (ServerPlayer caster : casters) {
                    h.assertTrue(MulticoreCanopy.cores(caster).size() == count, "real five-tick placement created exactly " + count + " cores");
                    h.assertTrue(MulticoreCanopy.release(caster, wires.get(caster)), "first input release arms the existing cores");
                    origins.put(caster, MulticoreCanopy.cores(caster).getFirst().center());
                }
                h.runAfterDelay(6, armed);
            };
            if (count == 1) release.run(); else h.runAfterDelay((count - 1) * 5 + 1, release);
        }
        void fire(List<ServerPlayer> casters, Runnable beforeNative, Runnable finished) {
            deploy(casters, 1, () -> {
                for (ServerPlayer caster : casters) {
                    String wire = input(caster, 0);
                    h.assertTrue(MulticoreCanopy.start(caster, wire), "distinct second input freezes native hostile roster");
                    h.assertTrue(MulticoreCanopy.release(caster, wire), "short release queues one warned simultaneous salvo");
                }
                h.runAfterDelay(10, beforeNative);
                h.runAfterDelay(16, finished);
            });
        }
        void moveTargetAway(LivingEntity target) {
            Vec3 displacement = new Vec3(0, 0, 4);
            var destination = target.getBoundingBox().move(displacement);
            // The eight-block native template has a barrier roof. Keep deliberate invalidation
            // inside its air volume, and keep the abandoned original ray/contact unchanged.
            for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(destination.minX, destination.minY, destination.minZ),
                    BlockPos.containing(Math.nextDown(destination.maxX), Math.nextDown(destination.maxY), Math.nextDown(destination.maxZ)))) {
                h.assertTrue(h.getLevel().hasChunkAt(pos) && h.getLevel().getBlockState(pos).isAir(),
                        "the deliberate target-movement destination is loaded air: " + pos);
            }
            h.assertTrue(h.getLevel().noCollision(target, destination), "the deliberate target-movement destination has no block or body collision");
            target.setPos(target.position().add(displacement));
        }
        void ally(ServerPlayer caster, LivingEntity target) {
            var board = h.getLevel().getScoreboard(); PlayerTeam team = board.addPlayerTeam("mc" + UUID.randomUUID().toString().substring(0, 10));
            team.setAllowFriendlyFire(false); board.addPlayerToTeam(caster.getScoreboardName(), team); board.addPlayerToTeam(target.getScoreboardName(), team); teams.add(team);
        }
        void change(BlockPos pos, net.minecraft.world.level.block.Block block) {
            changedBlocks.add(pos); h.getLevel().setBlockAndUpdate(pos, block.defaultBlockState());
        }
        DamageSource nativeSource(ServerPlayer caster, Vec3 origin) {
            return new DamageSource(caster.damageSources().playerAttack(caster).typeHolder(), caster, caster, origin);
        }
        Observation observe(LivingEntity target) { var result = new Observation(target); hook(result); return result; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        double debit(ServerPlayer player, double initial, BigDecimal initialCredits) {
            return BigDecimal.valueOf(initial).add(credits.get(player).subtract(initialCredits)).subtract(BigDecimal.valueOf(HighPowerResources.cp(player))).doubleValue();
        }
        void restoreConfig() {
            if (changedConfig) { AcademyConfig.DAMAGE_SCALE.set(oldGlobal); AcademyConfig.SKILL_NUMERIC_OVERRIDES.set(oldLocal); changedConfig = false; }
        }
        void close() {
            if (closed) return; closed = true; restoreConfig(); hooks.forEach(NeoForge.EVENT_BUS::unregister);
            teams.forEach(h.getLevel().getScoreboard()::removePlayerTeam);
            for (ServerPlayer p : players) { MulticoreCanopy.remove(p); AbilityStorage.remove(p); h.getLevel().getServer().getPlayerList().remove(p); }
            entities.forEach(entity -> { if (originalUuids.containsKey(entity)) entity.setUUID(originalUuids.get(entity)); entity.discard(); }); changedBlocks.forEach(pos -> h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
