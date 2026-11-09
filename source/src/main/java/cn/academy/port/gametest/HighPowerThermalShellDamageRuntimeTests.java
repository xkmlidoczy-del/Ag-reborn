package cn.academy.port.gametest;

import cn.academy.port.AbilityDamage;
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
import net.minecraft.world.entity.monster.Husk;
import net.minecraft.world.entity.projectile.Arrow;
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
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Real NEW222 input, native hurt and deferred opaque permits; no synthetic absorption receipt. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerThermalShellDamageRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_thermal_shell_damage";
    private HighPowerThermalShellDamageRuntimeTests() {}
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static BigDecimal hp(LivingEntity entity) { return n(HighPowerCombat.healthText(entity)); }
    private static BigDecimal decimal(float value) { return new BigDecimal(Float.toString(value)); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) {
        h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected);
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void shell_debit_is_only_the_native_post_armor_resistance_protection_absorption_remainder(GameTestHelper h) {
        Fixture f = new Fixture(h); var owner = f.player(6, 6, true); var control = f.player(18, 6, false);
        f.ready(() -> {
            var attacker = f.mob(6, 8, false); var nativeAttacker = f.mob(18, 8, false);
            defenses(h, owner); defenses(h, control); f.arm(owner, null, false);
            var hit = f.observe(owner); var nativeHit = f.observe(control);
            BigDecimal shield = HighPowerCombat.shield(owner), health = hp(owner);
            h.assertTrue(f.hurt(control, control.damageSources().mobAttack(nativeAttacker), 20), "paired native melee control accepted");
            h.assertTrue(f.hurt(owner, owner.damageSources().mobAttack(attacker), 20), "actual shell melee accepted");
            h.assertTrue(hit.posts == 1 && nativeHit.posts == 1 && hit.damage > 0 && hit.armor > 0
                    && hit.effects > 0 && hit.enchantments > 0 && hit.absorption == .5F, "native reducers execute once before shell");
            h.assertTrue(hit.damage == nativeHit.damage && hit.armor == nativeHit.armor && hit.effects == nativeHit.effects
                    && hit.enchantments == nativeHit.enchantments && hit.absorption == nativeHit.absorption, "native reductions match the unshielded control");
            eq(h, HighPowerCombat.shield(owner), shield.subtract(decimal(hit.damage)), "shell uses actual post-native remainder without raw-fraction scaling");
            eq(h, hp(owner), health, "only base S protects remaining exact HP");
            h.assertTrue(owner.getAbsorptionAmount() == 0 && control.getAbsorptionAmount() == 0, "native absorption is spent once");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void canonical_melee_types_preserve_iframes_and_reconcile_scatter_shares_once(GameTestHelper h) {
        Fixture f = new Fixture(h); var owners = new ServerPlayer[3];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(6 + i * 12, 6, true);
        var playerAttacker = f.player(30, 8, false);
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                var owner = owners[i]; LivingEntity attacker = i == 2 ? playerAttacker : f.mob(6 + i * 12, 8, false);
                f.arm(owner, "60", true); BigDecimal health = hp(owner);
                DamageSource source = i == 0 ? owner.damageSources().mobAttack(attacker) : i == 1
                        ? new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.MOB_ATTACK_NO_AGGRO), attacker)
                        : owner.damageSources().playerAttack(playerAttacker);
                h.assertTrue(f.hurt(owner, source, 5), "canonical direct melee type " + i + " accepts");
                h.assertFalse(f.hurt(owner, source, 5), "equal same-frame native hurt cooldown remains authoritative");
                h.assertTrue(f.hurt(owner, source, 8), "stronger native hit applies only its differential");
                eq(h, HighPowerCombat.shield(owner), n("52"), "five then three debit only once each across coexisting defenses");
                eq(h, hp(owner), health, "both native accepted residuals stay within shared S");
                var shares = ScatterPlateStorage.read(owner).orElseThrow();
                BigDecimal sum = BigDecimal.ZERO;
                for (int plate = 0; plate < 3; plate++) sum = sum.add(shares.plate(plate).current());
                eq(h, sum, n("52"), "ordinary base write reconciles all three existing allocations exactly once");
                h.assertTrue(AbilityStorage.get(owner).exp(HighPowerSkills.SCATTER_PLATES) == 0,
                        "ordinary melee never acquires NEW212's arrow or beam capture");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unknown_subclasses_skills_projectiles_environment_spoofs_range_and_los_keep_ordinary_hp_path(GameTestHelper h) {
        Fixture f = new Fixture(h); var owners = new ServerPlayer[10];
        // Keep every native owner/attacker inside this 32x32 template. Wider rows overlap
        // adjacent concurrent GameTests and can acquire unrelated damage and hurt cooldowns.
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(5 + (i % 5) * 5, 6 + (i / 5) * 14, true);
        var skillAttacker = f.player(20, 8, false);
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                int x = 5 + (i % 5) * 5, z = 6 + (i / 5) * 14;
                var owner = owners[i]; var attacker = f.mob(x, z + (i == 7 ? ThermalShellRules.RANGE + .01 : 2), false);
                f.arm(owner, null, false); BigDecimal shield = HighPowerCombat.shield(owner), health = hp(owner);
                DamageSource ordinary = owner.damageSources().mobAttack(attacker), source;
                switch (i) {
                    case 0 -> source = new DamageSource(ordinary.typeHolder(), attacker) {};
                    case 1 -> source = owner.damageSources().onFire();
                    case 2 -> source = owner.damageSources().magic();
                    case 3 -> source = new AbilityDamage.SkillDamageSource(skillAttacker, "meltdowner.thermal_shell", false);
                    case 4 -> { var arrow = new Arrow(EntityType.ARROW, h.getLevel()); arrow.setOwner(attacker); arrow.setPos(attacker.position());
                        f.entities.add(arrow); h.getLevel().addFreshEntity(arrow); source = owner.damageSources().arrow(arrow, attacker); }
                    case 5 -> source = new DamageSource(ordinary.typeHolder(), attacker, attacker, owner.position());
                    case 6 -> source = new DamageSource(ordinary.typeHolder(), null, attacker);
                    case 7 -> source = ordinary;
                    case 8 -> { h.setBlock(new BlockPos(x, 3, z + 1), Blocks.STONE); h.setBlock(new BlockPos(x, 2, z + 1), Blocks.STONE); source = ordinary; }
                    default -> source = new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.EXPLOSION), attacker);
                }
                var observation = f.observe(owner);
                h.assertTrue(f.hurt(owner, source, 5), "unsupported shell intake retains native ordinary hit " + i);
                eq(h, HighPowerCombat.shield(owner), shield, "closed shell source classification refuses case " + i);
                eq(h, hp(owner), health.subtract(decimal(observation.damage)), "native HP adaptation remains active for refused shell case " + i);
                h.assertTrue(AbilityStorage.get(owner).exp(HighPowerSkills.THERMAL_SHELL) == 0, "no productive shell XP for refused source");
            }
            h.assertFalse(HighPowerCombat.attack(owners[0], "meltdowner.thermal_shell", owners[1], BigDecimal.ONE), "generic MD callers cannot forge a pulse");
            AbilityStorage.get(owners[0]).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
            h.assertFalse(HighPowerCombat.attack(owners[0], "meltdowner.thermal_shell", owners[1], BigDecimal.ONE), "generic EM callers cannot forge a named thermal pulse");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_cancellation_zero_pre_and_closed_context_do_not_spend_shared_shield(GameTestHelper h) {
        Fixture f = new Fixture(h); var owners = new ServerPlayer[3];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(6 + i * 12, 6, true);
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                var owner = owners[i]; var attacker = f.mob(6 + i * 12, 8, false); f.arm(owner, null, false);
                BigDecimal health = hp(owner), shield = HighPowerCombat.shield(owner);
                var gate = new IntakeGate(owner, i); f.hook(gate); f.hurt(owner, owner.damageSources().mobAttack(attacker), 5);
                h.assertTrue(gate.called, "actual native stage reached " + i);
                eq(h, HighPowerCombat.shield(owner), shield, "cancelled/zero/invalid capture never debits shield");
                eq(h, hp(owner), i == 1 ? health.subtract(n("5")) : health, "native accepted outcome remains authoritative");
                h.assertTrue(AbilityStorage.get(owner).exp(HighPowerSkills.THERMAL_SHELL) == 0, "no shell XP for uncommitted damage");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void melee_geometry_is_captured_before_mutable_native_callbacks(GameTestHelper h) {
        Fixture f = new Fixture(h); var owner = f.player(6, 6, true);
        f.ready(() -> {
            var attacker = f.mob(6, 8, false); f.arm(owner, null, false);
            var mover = new IncomingMove(owner, attacker); f.hook(mover);
            BigDecimal shield = HighPowerCombat.shield(owner), health = hp(owner);
            h.assertTrue(f.hurt(owner, owner.damageSources().mobAttack(attacker), 5), "qualified melee enters real native pipeline");
            h.assertTrue(mover.called && attacker.distanceToSqr(owner) > 9, "mutable observer moves attacker after authenticated capture");
            eq(h, HighPowerCombat.shield(owner), shield.subtract(n("5")), "later motion cannot reinterpret the accepted contact origin");
            eq(h, hp(owner), health, "snapshot preserves the accepted hit's shield transaction");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void real_wither_effect_tick_remains_native_dot_and_never_becomes_melee_intake(GameTestHelper h) {
        Fixture f = new Fixture(h); var owner = f.player(6, 6, true);
        f.ready(() -> {
            f.arm(owner, null, false); var hit = f.observe(owner);
            BigDecimal health = hp(owner), shield = HighPowerCombat.shield(owner);
            owner.addEffect(new MobEffectInstance(MobEffects.WITHER, 3, 5));
            // One actual native player tick drives MobEffectInstance and its vanilla WITHER source.
            owner.doTick();
            h.assertTrue(hit.posts == 1 && hit.source != null && hit.source.is(DamageTypes.WITHER) && hit.damage > 0,
                    "real status-effect ticking reaches ordinary native WITHER damage");
            eq(h, hp(owner), health.subtract(decimal(hit.damage)), "DoT keeps native HP damage");
            eq(h, HighPowerCombat.shield(owner), shield, "DoT cannot spend shell shield");
            h.assertTrue(ThermalShell.pulseCount(owner) == 0 && AbilityStorage.get(owner).exp(HighPowerSkills.THERMAL_SHELL) == 0,
                    "DoT creates no pulse or mastery receipt");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void native_held_shield_blocks_before_shell_intake_and_creates_no_absorption_receipt(GameTestHelper h) {
        Fixture f = new Fixture(h); var owner = f.player(6, 6, true); var control = f.player(18, 6, false);
        f.ready(() -> {
            var attacker = f.mob(6, 8, false); var nativeAttacker = f.mob(18, 8, false);
            for (var p : List.of(owner, control)) {
                face(p, 0); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); p.startUsingItem(InteractionHand.OFF_HAND);
                f.hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, 8));
            }
            h.runAfterDelay(7, () -> {
                face(owner, 0); face(control, 0); h.assertTrue(owner.isBlocking() && control.isBlocking(), "real held shield delay completed");
                f.arm(owner, null, false); var hit = f.observe(owner); var nativeHit = f.observe(control);
                BigDecimal health = hp(owner), shield = HighPowerCombat.shield(owner);
                h.assertFalse(f.hurt(control, control.damageSources().mobAttack(nativeAttacker), 5), "native control blocks direct frontal melee");
                h.assertFalse(f.hurt(owner, owner.damageSources().mobAttack(attacker), 5), "native held shield blocks before shell absorption");
                h.assertTrue(hit.blocked && nativeHit.blocked && hit.blockedDamage == nativeHit.blockedDamage, "held shields use matching native block decisions");
                eq(h, hp(owner), health, "native block leaves exact HP unchanged"); eq(h, HighPowerCombat.shield(owner), shield, "native block cannot debit shell S");
                h.assertTrue(ThermalShell.pulseCount(owner) == 0 && AbilityStorage.get(owner).exp(HighPowerSkills.THERMAL_SHELL) == 0,
                        "no final damage means no pulse or mastery receipt"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_temporary_zero_hp_and_death_veto_preserve_one_shell_debit(GameTestHelper h) {
        Fixture f = new Fixture(h); var protectedOwner = f.player(6, 6, true); var lethalOwner = f.player(18, 6, true);
        var totemOwner = f.player(30, 6, true);
        f.ready(() -> {
            var attacker = f.mob(6, 8, false); var other = f.mob(18, 8, false);
            f.arm(protectedOwner, "100", false); BigDecimal health = hp(protectedOwner);
            h.assertTrue(f.hurt(protectedOwner, protectedOwner.damageSources().mobAttack(attacker), 40), "native normalized HP reaches temporary zero before Post");
            eq(h, hp(protectedOwner), health, "captured outer frame permits exact protection despite native temporary HP0");
            eq(h, HighPowerCombat.shield(protectedOwner), n("60"), "forty native residual consumes exactly forty base shield");
            f.arm(lethalOwner, "2", false); lethalOwner.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            var veto = new DeathVeto(lethalOwner); f.hook(veto);
            h.assertTrue(f.hurt(lethalOwner, lethalOwner.damageSources().mobAttack(other), 5), "partial shell permits true exact lethal remainder");
            h.assertTrue(veto.called, "native death hook still executes and owns its veto");
            eq(h, hp(lethalOwner), BigDecimal.ONE, "native death veto restores the prior exact injury");
            eq(h, HighPowerCombat.shield(lethalOwner), BigDecimal.ZERO, "death veto never refunds already consumed shared S");
            var totemAttacker = f.mob(30, 8, false); f.arm(totemOwner, "2", false);
            totemOwner.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            totemOwner.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
            h.assertTrue(f.hurt(totemOwner, totemOwner.damageSources().mobAttack(totemAttacker), 5), "same partial shell also keeps native totem flow");
            h.assertTrue(totemOwner.isAlive() && totemOwner.getOffhandItem().isEmpty() && hp(totemOwner).signum() > 0,
                    "native totem consumes once and restores living exact HP");
            eq(h, HighPowerCombat.shield(totemOwner), BigDecimal.ZERO, "native rescue never refills spent shell S");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void deferred_closed_pulse_keeps_native_player_attack_origin_and_exact_native_mitigation(GameTestHelper h) {
        Fixture f = new Fixture(h); var owner = f.player(6, 6, true);
        f.ready(() -> {
            var target = f.mob(6, 8, true); var control = f.mob(18, 8, false); defenses(h, target); defenses(h, control);
            f.arm(owner, null, false); var hit = f.observe(target); var nativeHit = f.observe(control);
            BigDecimal health = hp(target); Vec3 origin = owner.position().add(0, 1, 0);
            BigDecimal budget = ThermalShellRules.pulseBudget(HighPowerGrowth.values(owner).damage(), n("5"));
            h.assertTrue(budget.compareTo(n("5")) == 0, "fixture makes committed absorption the outgoing raw ceiling");
            BigDecimal residual = DamageMitigation.mitigate(budget, new DefenseProfile(BigDecimal.valueOf(target.getArmorValue()),
                    BigDecimal.valueOf(target.getAttributeValue(Attributes.ARMOR_TOUGHNESS)), 1, 4)).subtract(n(".5"));
            h.assertTrue(control.hurt(f.nativePulse(owner, origin.add(12, 0, 0)), 5), "paired bounded native pulse control accepts");
            h.assertTrue(f.hurt(owner, owner.damageSources().mobAttack(target), 5), "genuine absorption creates the outgoing receipt");
            h.assertTrue(hit.posts == 0, "pulse cannot recursively emit while owner's outer hurt frame is live");
            h.runAfterDelay(3, () -> {
                h.assertTrue(hit.posts == 1 && hit.source != null, "one deferred permit reaches native hurt");
                eq(h, hp(target), health.subtract(residual), "outgoing exact residual retains every native defense");
                h.assertTrue(hit.armor == nativeHit.armor && hit.effects == nativeHit.effects && hit.enchantments == nativeHit.enchantments
                        && hit.absorption == nativeHit.absorption && Math.abs(hit.damage - nativeHit.damage) < .00001,
                        "bounded native control matches pulse reductions");
                assertPulseSource(h, hit.source, owner, origin);
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void pulse_front_native_shield_and_existing_hurt_cooldown_keep_their_ordinary_decisions(GameTestHelper h) {
        Fixture f = new Fixture(h); var frontOwner = f.player(6, 6, true); var cooldownOwner = f.player(18, 6, true);
        f.ready(() -> {
            var front = f.mob(6, 8, true); var cooling = f.mob(18, 8, true);
            face(front, 180); front.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); front.startUsingItem(InteractionHand.OFF_HAND);
            var frontHit = f.observe(front); var coolingHit = f.observe(cooling);
            h.runAfterDelay(7, () -> {
                face(front, 180); h.assertTrue(front.isBlocking(), "real native shield raise delay completed");
                f.arm(frontOwner, null, false); f.arm(cooldownOwner, null, false);
                h.assertTrue(cooling.hurt(cooling.damageSources().generic(), 6), "real prior native hit establishes stronger cooldown history");
                BigDecimal cooledHealth = hp(cooling), frontHealth = hp(front);
                h.assertTrue(f.hurt(frontOwner, frontOwner.damageSources().mobAttack(front), 5), "front absorption commits");
                h.assertTrue(f.hurt(cooldownOwner, cooldownOwner.damageSources().mobAttack(cooling), 5), "cooldown absorption commits");
                h.runAfterDelay(3, () -> {
                    h.assertTrue(frontHit.blocked && frontHit.blockedDamage == 5, "pulse PLAYER_ATTACK respects shield facing from actual shell origin");
                    eq(h, hp(front), frontHealth, "native shield leaves exact pulse target HP unchanged");
                    eq(h, hp(cooling), cooledHealth, "five-point pulse cannot bypass stronger native iframe");
                    h.assertTrue(coolingHit.posts == 1, "only real setup hit reached damage Post on cooling target");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void pulse_rechecks_closed_controller_at_native_incoming_and_pre_for_enrolled_and_ordinary_targets(GameTestHelper h) {
        Fixture f = new Fixture(h); var owners = new ServerPlayer[4];
        // Seven-metre lanes exceed the three-metre pulse radius and stay in this template.
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(6 + i * 7, 6, true);
        f.ready(() -> {
            List<Husk> targets = new ArrayList<>(); List<PulseVeto> gates = new ArrayList<>();
            for (int i = 0; i < owners.length; i++) {
                var owner = owners[i]; var target = f.mob(6 + i * 7, 8, i % 2 == 0); targets.add(target);
                var gate = new PulseVeto(owner, target, i / 2); gates.add(gate); f.hook(gate); f.arm(owner, null, false);
                h.assertTrue(f.hurt(owner, owner.damageSources().mobAttack(target), 5), "actual committed receipt for late guard case " + i);
            }
            h.runAfterDelay(3, () -> {
                for (int i = 0; i < owners.length; i++) {
                    var target = targets.get(i); h.assertTrue(gates.get(i).called, "native late callback runs for case " + i);
                    if (i % 2 == 0) eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "late closed-controller guard vetoes exact pulse damage");
                    else h.assertTrue(target.getHealth() == target.getMaxHealth(), "late closed-controller guard also vetoes ordinary un-enrolled HP");
                }
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void pulse_hook_cancellation_amplification_and_discount_cannot_exceed_or_redistribute_absorption_budget(GameTestHelper h) {
        Fixture f = new Fixture(h); var owners = new ServerPlayer[3];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(6 + i * 12, 6, true);
        f.ready(() -> {
            List<Husk> targets = new ArrayList<>(); List<PulseScale> scales = new ArrayList<>();
            for (int i = 0; i < owners.length; i++) {
                var owner = owners[i]; var target = f.mob(6 + i * 12, 8, true); targets.add(target);
                var scale = new PulseScale(target, i == 0 ? 0 : i == 1 ? 2 : .5); scales.add(scale); f.hook(scale); f.arm(owner, null, false);
                h.assertTrue(f.hurt(owner, owner.damageSources().mobAttack(target), 5), "real absorption freezes five-point total budget");
            }
            h.runAfterDelay(3, () -> {
                for (int i = 0; i < owners.length; i++) {
                    h.assertTrue(scales.get(i).called, "actual outgoing SkillAttackEvent observed");
                    eq(h, hp(targets.get(i)), i == 2 ? cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth().subtract(n("2.5")) : cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(),
                            "zero cancels, amplification refuses, and decrease retains only its reduced allocation");
                }
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH+"_global_scale",timeoutTicks=100)
    public static void pulse_global_config_amplification_refuses_the_attempt_without_spending_extra_damage(GameTestHelper h) { configAmplification(h, false); }

    @GameTest(template=TEMPLATE,batch=BATCH+"_local_scale",timeoutTicks=100)
    public static void pulse_skill_config_amplification_refuses_the_attempt_without_spending_extra_damage(GameTestHelper h) { configAmplification(h, true); }

    private static void configAmplification(GameTestHelper h, boolean local) {
        Fixture f = new Fixture(h); var owner = f.player(6, 6, true);
        f.ready(() -> {
            var target = f.mob(6, 8, true); var modifier = new ConfigScale(target, local); f.hook(modifier); f.arm(owner, null, false);
            h.assertTrue(f.hurt(owner, owner.damageSources().mobAttack(target), 5), "real shell absorption queues the bounded emission");
            h.runAfterDelay(3, () -> {
                h.assertTrue(modifier.called && modifier.restored, "actual outgoing config multiplier applied then restored in the same server tick");
                eq(h, hp(target), cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth(), "config amplification beyond the fixed absorption budget refuses this pulse");
                h.succeed();
            });
        });
    }

    private static void assertPulseSource(GameTestHelper h, DamageSource source, ServerPlayer owner, Vec3 origin) {
        h.assertTrue(source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                && source.getEntity() == owner && source.getDirectEntity() == owner
                && origin.equals(source.getSourcePosition()) && origin.equals(source.sourcePositionRaw()),
                "closed thermal pulse carries its actual captured shell origin and native player attribution");
        h.assertFalse(source.is(DamageTypeTags.IS_FIRE) || source.is(DamageTypeTags.IS_EXPLOSION)
                || source.is(DamageTypeTags.BYPASSES_ARMOR) || source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS)
                || source.is(DamageTypeTags.BYPASSES_EFFECTS) || source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY), "pulse adds no native defense bypass or environmental family");
    }
    private static void face(LivingEntity p, float yaw) { p.setYRot(yaw); p.setYHeadRot(yaw); p.yBodyRot = yaw; p.setXRot(0); }
    private static void defenses(GameTestHelper h, LivingEntity p) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); p.setItemSlot(EquipmentSlot.HEAD, helmet);
        p.getAttribute(Attributes.ARMOR).setBaseValue(5); p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); p.setAbsorptionAmount(.5F);
    }
    public static final class IntakeGate {
        final ServerPlayer owner; final int stage; boolean called;
        IntakeGate(ServerPlayer owner, int stage) { this.owner = owner; this.stage = stage; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) {
            if (e.getEntity() != owner || stage == 2) return; called = true;
            if (stage == 0) e.setCanceled(true); else ThermalShell.abort(owner);
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e) {
            if (e.getEntity() == owner && stage == 2) { called = true; e.setNewDamage(0); }
        }
    }
    public static final class IncomingMove {
        final ServerPlayer owner; final LivingEntity attacker; boolean called;
        IncomingMove(ServerPlayer owner, LivingEntity attacker) { this.owner = owner; this.attacker = attacker; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) {
            if (e.getEntity() == owner) { called = true; attacker.setPos(attacker.position().add(0, 0, 10)); }
        }
    }
    public static final class DeathVeto {
        final ServerPlayer owner; boolean called;
        DeathVeto(ServerPlayer owner) { this.owner = owner; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void death(LivingDeathEvent e) {
            if (e.getEntity() == owner) { called = true; e.setCanceled(true); }
        }
    }
    public static final class PulseVeto {
        final ServerPlayer owner; final LivingEntity target; final int stage; boolean called;
        PulseVeto(ServerPlayer owner, LivingEntity target, int stage) { this.owner = owner; this.target = target; this.stage = stage; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) {
            if (e.getEntity() == target && e.getSource().getEntity() == owner && stage == 0) { called = true; ThermalShell.abort(owner); }
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e) {
            if (e.getEntity() == target && e.getSource().getEntity() == owner && stage == 1) { called = true; ThermalShell.abort(owner); }
        }
    }
    public static final class PulseScale {
        final LivingEntity target; final double scale; boolean called;
        PulseScale(LivingEntity target, double scale) { this.target = target; this.scale = scale; }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e) {
            if (e.target == target && e.skill.equals("meltdowner.thermal_shell")) { called = true; e.amount *= scale; }
        }
    }
    /** These config tests have separate batches; all temporary config changes are restored in the same tick. */
    public static final class ConfigScale implements AutoCloseable {
        final LivingEntity target; final boolean local; final double oldGlobal; final List<? extends String> oldLocal;
        boolean called, restored;
        ConfigScale(LivingEntity target, boolean local) {
            this.target = target; this.local = local; oldGlobal = AcademyConfig.DAMAGE_SCALE.get(); oldLocal = List.copyOf(AcademyConfig.SKILL_NUMERIC_OVERRIDES.get());
        }
        @SubscribeEvent(priority=EventPriority.NORMAL) public void skill(SkillAttackEvent e) {
            if (e.target != target || !e.skill.equals("meltdowner.thermal_shell")) return; called = true;
            if (local) AcademyConfig.SKILL_NUMERIC_OVERRIDES.set(List.of("meltdowner.thermal_shell.damage_scale=2"));
            else AcademyConfig.DAMAGE_SCALE.set(2D);
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void tick(ServerTickEvent.Post e) { if (called && !restored) close(); }
        @Override public void close() {
            AcademyConfig.DAMAGE_SCALE.set(oldGlobal); AcademyConfig.SKILL_NUMERIC_OVERRIDES.set(oldLocal); restored = true;
        }
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
        final Map<ServerPlayer, Vec3> positions = new IdentityHashMap<>(); boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h; h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player(double x, double z, boolean enrolled) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "HPThermalDamage"), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection)); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie); p.setNoGravity(true); p.setGameMode(GameType.SURVIVAL);
            p.setPos(h.absoluteVec(new Vec3(x, 2, z))); p.connection.resetPosition(); positions.put(p, p.position());
            if (enrolled) {
                var s = AbilityStorage.get(p); s.changeCategoryClassic(HighPowerCategories.MELTDOWNER); s.setLevel(2);
                s.learn(HighPowerSkills.DUAL_POINT); s.learn(HighPowerSkills.SCATTER_PLATES); s.learn(HighPowerSkills.THERMAL_SHELL);
                s.setActivateState(true); s.overloadFine = true;
                s.presets.edit(0, 0, HighPowerSkills.THERMAL_SHELL, s::learned);
                h.assertTrue(HighPowerCombat.enroll(p, 2), "connected native shell owner " + HighPowerCombat.unsupportedReason(p)); HighPowerResources.initializeBudget(p);
            }
            return p;
        }
        Husk mob(double x, double z, boolean enrolled) {
            var mob = new Husk(EntityType.HUSK, h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true);
            mob.setPos(h.absoluteVec(new Vec3(x, 2, z))); mob.getAttribute(Attributes.ARMOR).setBaseValue(0);
            h.getLevel().addFreshEntity(mob); entities.add(mob); if (enrolled) h.assertTrue(HighPowerCombat.enroll(mob, 5), "native exact hostile fixture"); return mob;
        }
        void ready(Runnable action) {
            for (var p : players) hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, 61));
            h.runAfterDelay(61, () -> {
                for (var p : players) { p.doTick(); p.setPos(positions.get(p)); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
                action.run();
            });
        }
        void arm(ServerPlayer p, String base, boolean plates) {
            if (base != null) p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", base);
            var s = AbilityStorage.get(p);
            if (plates) { s.learn(HighPowerSkills.SCATTER_PLATES); s.presets.edit(0, 1, HighPowerSkills.SCATTER_PLATES, s::learned); ScatterPlateStorage.ensure(p); }
            long epoch = LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), s);
            h.assertTrue(ThermalShell.toggle(p, new HighPowerInput.Input(0, 1, p.getId(), epoch).wire()), "real paid NEW222 activation");
            if (plates) h.assertTrue(ScatterPlates.toggle(p, new HighPowerInput.Input(1, 2, p.getId(), epoch).wire()), "independent real paid NEW212 activation");
        }
        boolean hurt(ServerPlayer target, DamageSource source, float amount) {
            boolean old = h.getLevel().getServer().isPvpAllowed(); h.getLevel().getServer().setPvpAllowed(true);
            try { return target.hurt(source, amount); } finally { h.getLevel().getServer().setPvpAllowed(old); }
        }
        DamageSource nativePulse(ServerPlayer p, Vec3 origin) { return new DamageSource(p.damageSources().playerAttack(p).typeHolder(), p, p, origin); }
        Observation observe(LivingEntity entity) { var result = new Observation(entity); hook(result); return result; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void close() {
            if (closed) return; closed = true;
            for (Object hook : hooks) { NeoForge.EVENT_BUS.unregister(hook); if (hook instanceof ConfigScale scale) scale.close(); }
            for (var p : players) { ThermalShell.remove(p); ScatterPlates.remove(p); AbilityStorage.remove(p); h.getLevel().getServer().getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
