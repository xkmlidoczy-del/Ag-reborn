package cn.academy.port.gametest;

import cn.academy.port.highpower.core.FoldedMirrorRules;

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
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native NEW221 defense tests. Every emission starts with real input and a visible fixed mirror. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerFoldedMirrorDamageRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_folded_mirror_damage";
    private static final String SKILL = "meltdowner.folded_mirror_array";
    private HighPowerFoldedMirrorDamageRuntimeTests() {}
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal hp(LivingEntity target) { return HighPowerCombat.enabled(target)
            ? n(HighPowerCombat.healthText(target)) : BigDecimal.valueOf(target.getHealth()); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=30)
    public static void held_shields_use_final_arrival_side_even_when_caster_is_on_opposite_side(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Route> routes = new ArrayList<>();
        List<Villager> controls = new ArrayList<>(); List<Observation> hits = new ArrayList<>(), nativeHits = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double x = 6 + i * 7; Route r = f.route(x, 5, i < 2); routes.add(r);
            var control = f.mob(x + 1.5, 21, false); controls.add(control);
            float yaw = i % 2 == 0 ? 180 : 0;
            raiseShield(r.target, yaw); raiseShield(control, yaw);
            f.hook(new Discount(r.target)); hits.add(f.observe(r.target)); nativeHits.add(f.observe(control));
        }
        h.runAfterDelay(7, () -> {
            for (int i = 0; i < routes.size(); i++) {
                Route r = routes.get(i); var control = controls.get(i); float yaw = i % 2 == 0 ? 180 : 0;
                face(r.target, yaw); face(control, yaw);
                h.assertTrue(r.target.isBlocking() && control.isBlocking(), "native held-shield raising delay completed");
                Vec3 toCaster = r.caster.position().subtract(r.target.position());
                Vec3 toMirror = r.mirror.subtract(r.target.position());
                h.assertTrue(toCaster.z < 0 && toMirror.z > 0, "caster and final source are on opposite shield hemispheres");
                BigDecimal before = hp(r.target);
                Vec3 nativeOrigin = r.mirror.add(control.position().subtract(r.target.position()));
                boolean nativeAccepted = control.hurt(f.source(r.caster, nativeOrigin), 5);
                h.assertTrue(f.fire(r), "real paid reflected release reaches native shield decision");
                Observation hit = hits.get(i), nativeHit = nativeHits.get(i);
                boolean blocks = i % 2 != 0;
                h.assertTrue(nativeAccepted != blocks && hit.blocked == blocks && nativeHit.blocked == blocks,
                        "caster-front/final-rear passes; caster-rear/final-front blocks");
                eq(h, hp(r.target), blocks ? before : before.subtract(n("5")), "native arrival-side decision controls exact and ordinary HP");
                h.assertTrue(hit.blockedDamage == nativeHit.blockedDamage && hit.damage == nativeHit.damage,
                        "paired native PLAYER_ATTACK control has the same block amount and HP damage");
                assertSource(h, hit.source, r, f.receipt(r));
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void final_leg_keeps_native_armor_toughness_resistance_protection_and_absorption(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Route> routes = new ArrayList<>(); List<Villager> controls = new ArrayList<>();
        List<Observation> hits = new ArrayList<>(), nativeHits = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            Route r = f.route(6 + i * 14, 5, i == 0); routes.add(r);
            var control = f.mob(13 + i * 14, 9, false); controls.add(control);
            defenses(h, r.target); defenses(h, control); f.hook(new Discount(r.target));
            hits.add(f.observe(r.target)); nativeHits.add(f.observe(control));
        }
        h.runAfterDelay(2, () -> {
            for (int i = 0; i < routes.size(); i++) {
                Route r = routes.get(i); var control = controls.get(i); BigDecimal before = hp(r.target);
                BigDecimal residual = DamageMitigation.mitigate(n("5"), new DefenseProfile(BigDecimal.valueOf(r.target.getArmorValue()),
                        BigDecimal.valueOf(r.target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)), 1, 4)).subtract(n(".5"));
                h.assertTrue(control.hurt(f.source(r.caster, r.mirror.add(control.position().subtract(r.target.position()))), 5), "native matching ordinary control accepts");
                h.assertTrue(f.fire(r), "genuine mirror reflection emits one native hit");
                Observation hit = hits.get(i), nativeHit = nativeHits.get(i);
                h.assertTrue(hit.posts == 1 && nativeHit.posts == 1 && hit.armor > 0 && hit.effects > 0 && hit.enchantments > 0 && hit.absorption == .5F,
                        "all native reducers execute once on the reflected terminal leg");
                h.assertTrue(hit.armor == nativeHit.armor && hit.effects == nativeHit.effects && hit.enchantments == nativeHit.enchantments
                        && hit.absorption == nativeHit.absorption && Math.abs(hit.damage - nativeHit.damage) < .00001,
                        "native reduction observations agree with the ordinary PLAYER_ATTACK control");
                if (i == 0) eq(h, hp(r.target), before.subtract(residual), "exact ledger retains the post-native residual");
                else h.assertTrue(r.target.getHealth() == control.getHealth(), "ordinary target keeps native float damage");
                h.assertTrue(r.target.getAbsorptionAmount() == 0 && control.getAbsorptionAmount() == 0, "native absorption pool is consumed once");
                assertSource(h, hit.source, r, f.receipt(r));
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void new212_contact_gap_and_rear_are_resolved_from_the_actual_final_leg(GameTestHelper h) {
        Fixture f = new Fixture(h); List<ServerPlayer> casters = new ArrayList<>(), defenders = new ArrayList<>();
        // Keep the reflected leg's lateral angle near the original front-plate and gap witnesses.
        double lateralOffset = (FoldedMirrorRules.PLACE_RANGE - 4) * .375;
        for (int i = 0; i < 3; i++) { casters.add(f.caster(6 + i * 10, 5)); defenders.add(f.player(6 + i * 10 + lateralOffset, 9, true)); }
        f.ready(() -> {
            List<Route> routes = new ArrayList<>();
            for (int i = 0; i < 3; i++) {
                ServerPlayer defender = defenders.get(i); f.arm(defender, "6", i == 0 ? 0 : i == 1 ? 10 : 180);
                routes.add(f.place(casters.get(i), defender)); f.hook(new Discount(defender));
            }
            h.runAfterDelay(2, () -> {
                for (int i = 0; i < routes.size(); i++) {
                    Route r = routes.get(i); ServerPlayer defender = defenders.get(i); BigDecimal before = hp(defender);
                    var prior = ScatterPlateStorage.read(defender).orElseThrow().toDecimalMap();
                    h.assertTrue(f.fire(r), "real reflected beam reaches NEW212 case " + i);
                    var receipt = f.receipt(r); var leg = receipt.path().getLast();
                    var pose = ScatterPlateGeometry.pose(vector(defender.position().add(0, 1, 0)), vector(defender.getLookAngle()));
                    var contact = ScatterPlateGeometry.crossing(pose, vector(leg.start()), vector(leg.end()), 7);
                    if (i == 0) {
                        h.assertTrue(contact.isPresent(), "final ray physically crosses an actual live front ellipse");
                        int index = contact.orElseThrow().plateIndex(); var shares = ScatterPlateStorage.read(defender).orElseThrow();
                        eq(h, HighPowerCombat.shield(defender), n("4"), "only contacted two-point plate share is paid");
                        eq(h, hp(defender), before.subtract(n("3")), "single final hit reaches HP with the unpaid three-point residual");
                        for (int plate = 0; plate < 3; plate++) eq(h, shares.plate(plate).current(), plate == index ? BigDecimal.ZERO : n("2"),
                                "untouched plates cannot lend resources to contacted plate");
                    } else {
                        h.assertTrue(contact.isEmpty(), "final ray really misses through " + (i == 1 ? "the finite front gap" : "the rear"));
                        eq(h, hp(defender), before.subtract(n("5")), "gap/rear leaves native body damage unchanged");
                        eq(h, HighPowerCombat.shield(defender), n("6"), "gap/rear spends no shield");
                        h.assertTrue(ScatterPlateStorage.read(defender).orElseThrow().toDecimalMap().equals(prior), "no synthetic caster-to-target capture changes any share");
                    }
                }
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void skill_callback_changes_to_target_caster_earlier_terrain_mirrors_or_category_spend_the_attempt(GameTestHelper h) { mutableCallbacks(h, 0); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void incoming_callback_changes_to_target_caster_earlier_terrain_mirrors_or_category_spend_the_attempt(GameTestHelper h) { mutableCallbacks(h, 1); }
    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void pre_callback_changes_to_target_caster_earlier_terrain_mirrors_or_category_spend_the_attempt(GameTestHelper h) { mutableCallbacks(h, 2); }

    private static void mutableCallbacks(GameTestHelper h, int stage) {
        Fixture f = new Fixture(h); List<Route> routes = new ArrayList<>(); List<Mutation> gates = new ArrayList<>(); List<Observation> hits = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Route r = f.route(4 + (i % 5) * 5, 5 + (i / 5) * 16, i < 5); routes.add(r);
            f.hook(new Discount(r.target)); Mutation gate = new Mutation(f, r, stage, i % 5); gates.add(gate); f.hook(gate); hits.add(f.observe(r.target));
        }
        h.runAfterDelay(2, () -> {
            for (int i = 0; i < routes.size(); i++) {
                Route r = routes.get(i); BigDecimal before = hp(r.target); double cp = HighPowerResources.cp(r.caster);
                h.assertTrue(f.fire(r), "already paid reflection reaches callback stage " + stage + " mutation " + i % 5);
                h.assertTrue(gates.get(i).called, "requested mutable native callback actually executes");
                eq(h, hp(r.target), before, "whole captured path invalidation prevents exact and ordinary HP damage");
                h.assertTrue(hits.get(i).damage == 0, "no positive native proxy damage escapes the callback veto");
                h.assertTrue(HighPowerResources.cp(r.caster) < cp, "callback invalidation never refunds committed CP");
                h.assertTrue(AbilityStorage.get(r.caster).exp(HighPowerSkills.FOLDED_MIRROR_ARRAY) == 0, "vetoed path grants no productive mastery");
                h.assertTrue(!f.fire(r), "same-tick repeat cannot retry or transfer the spent attempt");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void cancellation_zero_and_positive_amplification_never_escape_the_paid_terminal_budget(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Route> routes = new ArrayList<>(); List<AmountGate> gates = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            Route r = f.route(4 + (i % 6) * 4.5, 5 + (i / 6) * 18, i < 6); routes.add(r);
            // Positive SkillAttack amplification must exceed the original 1.5D allocation, not a prior test discount.
            if (i % 6 != 1) f.hook(new Discount(r.target));
            AmountGate gate = new AmountGate(r.target, i % 6); gates.add(gate); f.hook(gate);
        }
        h.runAfterDelay(2, () -> {
            for (int i = 0; i < routes.size(); i++) {
                Route r = routes.get(i); BigDecimal before = hp(r.target); double cp = HighPowerResources.cp(r.caster);
                h.assertTrue(f.fire(r), "real paid attempt reaches amount callback " + i % 6);
                h.assertTrue(gates.get(i).called, "native amount callback executed");
                eq(h, hp(r.target), before, "cancel, zero or untrusted positive rewrite does not damage exact or ordinary target");
                h.assertTrue(HighPowerResources.cp(r.caster) < cp, "refused terminal amount remains paid");
                h.assertTrue(AbilityStorage.get(r.caster).exp(HighPowerSkills.FOLDED_MIRROR_ARRAY) == 0, "refused terminal amount creates no mastery");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_global_scale",timeoutTicks=20)
    public static void global_amplification_cannot_expand_either_terminal_allocation(GameTestHelper h) { configAmplification(h, false); }
    @GameTest(template=TEMPLATE,batch=BATCH+"_skill_scale",timeoutTicks=20)
    public static void skill_config_amplification_cannot_expand_either_terminal_allocation(GameTestHelper h) { configAmplification(h, true); }

    private static void configAmplification(GameTestHelper h, boolean local) {
        Fixture f = new Fixture(h); List<Route> routes = List.of(f.route(6, 5, true), f.route(20, 5, false));
        h.runAfterDelay(2, () -> {
            double oldGlobal = AcademyConfig.DAMAGE_SCALE.get(); var oldLocal = AcademyConfig.SKILL_NUMERIC_OVERRIDES.get();
            try {
                if (local) AcademyConfig.SKILL_NUMERIC_OVERRIDES.set(List.of(SKILL + ".damage_scale=2"));
                else AcademyConfig.DAMAGE_SCALE.set(2D);
                for (Route r : routes) {
                    BigDecimal before = hp(r.target); double cp = HighPowerResources.cp(r.caster);
                    h.assertTrue(f.fire(r), "actual paid reflection reaches configured amplification");
                    eq(h, hp(r.target), before, "config amplification above original 1.5D refuses either target kind");
                    h.assertTrue(HighPowerResources.cp(r.caster) < cp, "refused amplified allocation remains spent");
                }
            } finally { AcademyConfig.DAMAGE_SCALE.set(oldGlobal); AcademyConfig.SKILL_NUMERIC_OVERRIDES.set(oldLocal); }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void actual_one_point_five_d_budget_is_one_hit_and_forged_skill_strings_cannot_enter(GameTestHelper h) {
        Fixture f = new Fixture(h); Route r = f.route(6, 5, true); Observation hit = f.observe(r.target);
        h.runAfterDelay(2, () -> {
            BigDecimal before = hp(r.target), budget = HighPowerGrowth.values(r.caster).damage().multiply(n("1.5"));
            h.assertTrue(f.fire(r), "undiscounted actual reflection emits");
            eq(h, hp(r.target), before.subtract(budget), "one successful terminal hit is exactly 1.5D total");
            h.assertTrue(hit.posts == 1, "reflection legs are not additional damage transactions");
            assertSource(h, hit.source, r, f.receipt(r));
            h.assertFalse(HighPowerCombat.attack(r.caster, SKILL, r.target, BigDecimal.ONE), "generic MD entry cannot forge the closed folded source");
            AbilityStorage.get(r.caster).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
            h.assertFalse(HighPowerCombat.attack(r.caster, SKILL, r.target, BigDecimal.ONE), "generic EM entry cannot forge the same named source");
            eq(h, hp(r.target), before.subtract(budget), "forged source attempts create no second damage");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_same_rank_iframes_and_absorption_keep_their_ordinary_decisions(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Route> first = new ArrayList<>(), second = new ArrayList<>(); List<Villager> controls = new ArrayList<>();
        List<Observation> hits = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            double x = 6 + i * 7; Route a = f.route(x, 5, i % 2 == 0); first.add(a);
            second.add(f.place(f.caster(x - .8, 5), a.target)); var control = f.mob(x + 1.5, 21, false); controls.add(control);
            if (i >= 2) for (LivingEntity target : List.of(a.target, control)) {
                target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); target.setAbsorptionAmount(10);
            }
            f.hook(new Discount(a.target)); hits.add(f.observe(a.target));
        }
        h.runAfterDelay(2, () -> {
            for (int i = 0; i < first.size(); i++) {
                Route a = first.get(i), b = second.get(i); var control = controls.get(i); BigDecimal before = hp(a.target);
                DamageSource nativeSource = f.source(a.caster, a.mirror.add(control.position().subtract(a.target.position())));
                h.assertTrue(control.hurt(nativeSource, 5), "matching first native hit accepts");
                h.assertFalse(control.hurt(nativeSource, 5), "matching equal native hit is rejected by actual hurt cooldown");
                h.assertTrue(f.fire(a) && f.fire(b), "two independent authentic paid emitters reach the native decision");
                eq(h, hp(a.target), i >= 2 ? before : before.subtract(n("5")), "same-rank iframe allows only the first native residual");
                h.assertTrue(a.target.getAbsorptionAmount() == control.getAbsorptionAmount()
                        && a.target.getAbsorptionAmount() == (i >= 2 ? 5 : 0), "absorption is spent once and never on the iframe refusal");
                h.assertTrue(hits.get(i).posts == 1, "only one native damage completion runs");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void native_death_veto_and_totem_execute_after_real_reflected_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); List<Route> routes = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            Route r = f.route(6 + i * 10, 5, i < 2); routes.add(r); f.hook(new Discount(r.target));
            if (i < 2) r.target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            else r.target.setHealth(1);
            if (i > 0) r.target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
        }
        DeathVeto veto = new DeathVeto(routes.getFirst().target); f.hook(veto);
        h.runAfterDelay(2, () -> {
            for (Route r : routes) h.assertTrue(f.fire(r), "genuine reflected terminal hit reaches native lethal handling");
            h.assertTrue(veto.called && routes.getFirst().target.isAlive(), "native death veto executes and restores the enrolled target");
            eq(h, hp(routes.getFirst().target), BigDecimal.ONE, "death veto restores original exact HP");
            for (int i = 1; i < routes.size(); i++) {
                LivingEntity target = routes.get(i).target;
                h.assertTrue(target.isAlive() && target.getOffhandItem().isEmpty() && hp(target).signum() > 0,
                        "native totem consumes once and rescues both exact and ordinary targets");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void reflected_md_source_does_not_expand_existing_em_defense_intake(GameTestHelper h) {
        Fixture f = new Fixture(h); var caster = f.caster(6, 5); var defender = f.player(7.5, 9, false);
        var state = AbilityStorage.get(defender); state.changeCategoryClassic(HighPowerCategories.ELECTROMASTER); state.setLevel(5);
        state.learn(HighPowerSkills.MAGNETIC_BRACER); state.setActivateState(true); state.overloadFine = true;
        h.assertTrue(HighPowerCombat.enroll(defender, 5), "real EM defensive fixture"); HighPowerResources.initializeBudget(defender);
        for (String skill : List.of(GroundingReturn.ID, PotentialBuffer.ID, MagneticDomain.ID)) state.learn(skill);
        state.presets.edit(0, 0, GroundingReturn.ID, state::learned); state.presets.edit(0, 1, PotentialBuffer.ID, state::learned);
        state.presets.edit(0, 2, MagneticDomain.ID, state::learned); f.hook(new Discount(defender));
        f.ready(() -> {
            h.setBlock(new BlockPos(7, 1, 9), Blocks.STONE); defender.setOnGround(true); defender.setShiftKeyDown(true); face(defender, 0);
            Route r = f.place(caster, defender);
            h.runAfterDelay(2, () -> {
                defender.setOnGround(true); defender.setShiftKeyDown(true); face(defender, 0);
                long epoch = LegacySingleKeyOwnerEpoch.current(defender, defender.connection, defender.serverLevel(), state);
                h.assertTrue(GroundingReturn.start(defender, new HighPowerInput.Input(0, 1, defender.getId(), epoch).wire()), "real grounding starts");
                h.assertTrue(PotentialBuffer.start(defender, new HighPowerInput.Input(1, 2, defender.getId(), epoch).wire()), "real front buffer starts");
                h.assertTrue(MagneticDomain.start(defender, new HighPowerInput.Input(2, 3, defender.getId(), epoch).wire()), "real paid domain starts");
                h.assertTrue(GroundingReturn.active(defender) && PotentialBuffer.active(defender) && MagneticDomain.active(defender), "all existing EM defenses are active before impact");
                BigDecimal before = hp(defender), shield = HighPowerCombat.shield(defender), reserve = MagneticDomainReserve.reserve(defender);
                h.assertTrue(f.fire(r), "native NEW221 source reaches active EM defenses");
                eq(h, hp(defender), before.subtract(n("5")), "unreviewed MD beam remains ordinary native damage for EM defenses");
                eq(h, HighPowerCombat.shield(defender), shield, "reflected MD source cannot spend EM shield");
                eq(h, MagneticDomainReserve.reserve(defender), reserve, "reflected MD source cannot spend domain reserve");
                h.assertTrue(state.exp(GroundingReturn.ID) == 0 && state.exp(PotentialBuffer.ID) == 0 && state.exp(MagneticDomain.ID) == 0,
                        "excluded source awards no EM interception mastery");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=20)
    public static void preexisting_dual_point_and_em_sources_keep_their_native_identity_and_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); var md = f.caster(6, 5); var em = f.caster(22, 5);
        var dualTarget = f.mob(6, 12, true); var emTarget = f.mob(22, 12, true); var a = f.observe(dualTarget); var b = f.observe(emTarget);
        var state = AbilityStorage.get(md); state.presets.edit(0, 0, HighPowerSkills.DUAL_POINT, state::learned);
        AbilityStorage.get(em).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
        f.hook(new Discount(dualTarget, "meltdowner.dual_point")); f.aim(md, dualTarget);
        Vec3 hand = DualPoint.hand(md, 0); long epoch = LegacySingleKeyOwnerEpoch.current(md, md.connection, md.serverLevel(), state);
        h.assertTrue(DualPoint.tap(md, new HighPowerInput.Input(0, 1, md.getId(), epoch).wire()), "preexisting genuine NEW211 ingress still accepts");
        h.assertTrue(HighPowerCombat.attack(em, "electromaster.forked_arc", emTarget, n("5")), "preexisting EM generic ingress still accepts");
        eq(h, hp(dualTarget), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "NEW211 exact damage is unchanged");
        eq(h, hp(emTarget), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("5")), "EM exact damage is unchanged");
        h.assertTrue(a.source.is(DamageTypes.PLAYER_ATTACK) && hand.equals(a.source.getSourcePosition()) && a.source.getDirectEntity() == md,
                "NEW211 retains its real hand origin and native player attribution");
        h.assertTrue(b.source.is(DamageTypes.PLAYER_ATTACK) && b.source.getEntity() == em && b.source.getDirectEntity() == em
                && !b.source.is(DamageTypeTags.IS_PROJECTILE), "existing electrical source is not converted into a reflected projectile");
        h.succeed();
    }

    private static ScatterPlateGeometry.Vector vector(Vec3 v) { return new ScatterPlateGeometry.Vector(v.x, v.y, v.z); }
    private static void face(LivingEntity target, float yaw) { target.setYRot(yaw); target.setYHeadRot(yaw); target.yBodyRot = yaw; target.setXRot(0); }
    private static void raiseShield(LivingEntity target, float yaw) { face(target, yaw); target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); target.startUsingItem(InteractionHand.OFF_HAND); }
    private static void defenses(GameTestHelper h, LivingEntity target) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
        target.getAttribute(Attributes.ARMOR).setBaseValue(5); target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        target.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); target.setAbsorptionAmount(.5F);
    }
    private static void assertSource(GameTestHelper h, DamageSource source, Route r, FoldedMirrorArray.Receipt receipt) {
        Vec3 origin = receipt.path().getLast().start();
        h.assertTrue(source != null && source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                && source.getEntity() == r.caster && source.getDirectEntity() == r.caster
                && origin.equals(source.getSourcePosition()) && origin.equals(source.sourcePositionRaw())
                && origin.distanceToSqr(r.mirror) < 1e-12 && origin.distanceToSqr(FoldedMirrorArray.hand(r.caster)) > 1,
                "native PLAYER_ATTACK attributes the player but locates damage at the real last mirror contact");
        h.assertFalse(source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION),
                "new source adds no native defense bypass or environmental family");
    }
    private record Route(ServerPlayer caster, LivingEntity target, Vec3 mirror) {}

    public static final class Discount {
        final LivingEntity target; final String skill;
        Discount(LivingEntity target) { this(target, SKILL); }
        Discount(LivingEntity target, String skill) { this.target = target; this.skill = skill; }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void skill(SkillAttackEvent e) { if (e.target == target && e.skill.equals(skill)) e.amount = 5; }
    }
    public static final class Mutation {
        final Fixture fixture; final Route route; final int stage, mutation; boolean called;
        Mutation(Fixture fixture, Route route, int stage, int mutation) { this.fixture = fixture; this.route = route; this.stage = stage; this.mutation = mutation; }
        private void change() {
            if (called) return; called = true;
            switch (mutation) {
                case 0 -> route.target.setPos(route.target.position().add(0, 6, 0));
                case 1 -> route.caster.setPos(route.caster.position().add(0, 6, 0));
                case 2 -> {
                    Vec3 midpoint = FoldedMirrorArray.hand(route.caster).lerp(route.mirror, .5);
                    BlockPos at = BlockPos.containing(midpoint); fixture.changedBlocks.add(at);
                    fixture.h.getLevel().setBlockAndUpdate(at, Blocks.STONE.defaultBlockState());
                }
                case 3 -> FoldedMirrorArray.abort(route.caster);
                default -> AbilityStorage.get(route.caster).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
            }
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e) { if (stage == 0 && e.target == route.target && e.skill.equals(SKILL)) change(); }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) { if (stage == 1 && e.getEntity() == route.target) change(); }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e) { if (stage == 2 && e.getEntity() == route.target) change(); }
    }
    public static final class AmountGate {
        final LivingEntity target; final int mode; boolean called;
        AmountGate(LivingEntity target, int mode) { this.target = target; this.mode = mode; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e) {
            if (e.target != target || !e.skill.equals(SKILL) || mode > 1) return;
            called = true; e.amount = mode == 0 ? 0 : e.amount * 2;
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) {
            if (e.getEntity() != target || mode < 2 || mode > 3) return;
            called = true; if (mode == 2) e.setCanceled(true); else e.setAmount(e.getAmount() * 2);
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e) {
            if (e.getEntity() != target || mode < 4) return;
            called = true; e.setNewDamage(mode == 4 ? 0 : e.getNewDamage() * 2);
        }
    }
    public static final class DeathVeto {
        final LivingEntity target; boolean called; DeathVeto(LivingEntity target) { this.target = target; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void death(LivingDeathEvent e) { if (e.getEntity() == target) { called = true; e.setCanceled(true); } }
    }
    public static final class Observation {
        final LivingEntity target; DamageSource source; int posts; boolean blocked; float damage, armor, effects, enchantments, absorption, blockedDamage;
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == target) source = e.getSource(); }
        @SubscribeEvent public void shield(LivingShieldBlockEvent e) { if (e.getEntity() == target) { blocked = e.getBlocked(); blockedDamage = e.getBlockedDamage(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) {
            if (e.getEntity() != target) return; posts++; damage = e.getNewDamage(); armor = e.getReduction(DamageContainer.Reduction.ARMOR);
            effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS);
            absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<EmbeddedChannel> channels = new ArrayList<>(); final List<Object> hooks = new ArrayList<>();
        final Map<ServerPlayer, Vec3> positions = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>();
        final List<BlockPos> changedBlocks = new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h; h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer caster(double x, double z) { return player(x, z, true); }
        ServerPlayer player(double x, double z, boolean enrolled) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "HPMirrorDamage"), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection)); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie); p.setNoGravity(true); p.setGameMode(GameType.SURVIVAL);
            p.setPos(h.absoluteVec(new Vec3(x, 2, z))); p.connection.resetPosition(); positions.put(p, p.position());
            if (enrolled) {
                var state = AbilityStorage.get(p); state.changeCategoryClassic(HighPowerCategories.MELTDOWNER); state.setLevel(2);
                state.learn(HighPowerSkills.DUAL_POINT); state.learn(HighPowerSkills.FOLDED_MIRROR_ARRAY);
                state.setActivateState(true); state.overloadFine = true; state.presets.edit(0, 0, HighPowerSkills.FOLDED_MIRROR_ARRAY, state::learned);
                h.assertTrue(HighPowerCombat.enroll(p, 2), "native connected LV2 MD fixture: " + HighPowerCombat.unsupportedReason(p)); HighPowerResources.initializeBudget(p);
            }
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, 100)); return p;
        }
        Villager mob(double x, double z, boolean enrolled) {
            var mob = new Villager(EntityType.VILLAGER, h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true);
            mob.setPos(h.absoluteVec(new Vec3(x, 2, z))); h.getLevel().addFreshEntity(mob); entities.add(mob);
            if (enrolled) h.assertTrue(HighPowerCombat.enroll(mob, 5), "ordinary native exact-target enrollment"); return mob;
        }
        Route route(double x, double z, boolean enrolled) { return place(caster(x, z), mob(x + 1.5, z + 4, enrolled)); }
        Route place(ServerPlayer caster, LivingEntity target) {
            // The actual editable input pins a center at placement reach, then fixes its normal toward the visible target.
            face(caster, 0); caster.setShiftKeyDown(true); String input = input(caster);
            h.assertTrue(FoldedMirrorArray.start(caster, input), "real crouch-start pins a visible placement center");
            aim(caster, target); caster.setShiftKeyDown(true);
            h.assertTrue(pvp(() -> FoldedMirrorArray.release(caster, input)), "real crouch-release fixes the physical mirror normal");
            var plates = FoldedMirrorArray.plates(caster); h.assertTrue(plates.size() == 1, "one visible committed mirror exists");
            h.assertTrue(plates.getFirst().center().z > target.position().z && caster.position().z < target.position().z,
                    "genuine mirror is beyond target while caster is in front");
            caster.setShiftKeyDown(false); return new Route(caster, target, plates.getFirst().center());
        }
        void aim(ServerPlayer caster, LivingEntity target) {
            Vec3 d = target.getBoundingBox().getCenter().subtract(caster.getEyePosition());
            caster.setYRot((float)Math.toDegrees(Math.atan2(-d.x, d.z)));
            caster.setXRot((float)-Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z))));
        }
        String input(ServerPlayer p) {
            long serial = inputs.merge(p, 1L, Long::sum); var state = AbilityStorage.get(p);
            long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), state);
            return new HighPowerInput.Input(0, serial, p.getId(), epoch).wire();
        }
        boolean fire(Route r) {
            r.caster.setShiftKeyDown(false); String wire = input(r.caster);
            return pvp(() -> FoldedMirrorArray.start(r.caster, wire) && FoldedMirrorArray.release(r.caster, wire));
        }
        FoldedMirrorArray.Receipt receipt(Route r) {
            var receipt = FoldedMirrorArray.receipt(r.caster);
            h.assertTrue(receipt != null && receipt.crossings() == 1 && receipt.path().size() == 2, "actual receipt contains one fixed reflection and two native segments");
            double length = receipt.path().stream().mapToDouble(leg -> leg.start().distanceTo(leg.end())).sum();
            h.assertTrue(length <= 40 && receipt.path().getFirst().end().equals(receipt.path().getLast().start()), "actual native path is continuous and within the cumulative forty-metre budget");
            return receipt;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> {
            for (ServerPlayer p : players) { p.doTick(); p.setPos(positions.get(p)); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
            action.run();
        }); }
        void arm(ServerPlayer p, String shield, float yaw) {
            p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", shield);
            var state = AbilityStorage.get(p); state.learn(HighPowerSkills.SCATTER_PLATES); state.presets.edit(0, 0, HighPowerSkills.SCATTER_PLATES, state::learned);
            h.assertTrue(ScatterPlateStorage.ensure(p).isPresent(), "three actual NEW212 shares exist"); face(p, yaw);
            long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), state);
            h.assertTrue(ScatterPlates.toggle(p, new HighPowerInput.Input(0, 1, p.getId(), epoch).wire()), "real paid NEW212 activation");
        }
        DamageSource source(ServerPlayer caster, Vec3 origin) { return new DamageSource(caster.damageSources().playerAttack(caster).typeHolder(), caster, caster, origin); }
        boolean pvp(java.util.function.BooleanSupplier action) {
            boolean prior = h.getLevel().getServer().isPvpAllowed(); h.getLevel().getServer().setPvpAllowed(true);
            try { return action.getAsBoolean(); } finally { h.getLevel().getServer().setPvpAllowed(prior); }
        }
        Observation observe(LivingEntity target) { var observation = new Observation(target); hook(observation); return observation; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer p : players) {
                FoldedMirrorArray.remove(p); ScatterPlates.remove(p); DualPoint.remove(p); GroundingReturn.remove(p); PotentialBuffer.remove(p); MagneticDomain.remove(p);
                AbilityStorage.remove(p); h.getLevel().getServer().getPlayerList().remove(p);
            }
            entities.forEach(Entity::discard); changedBlocks.forEach(pos -> h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
