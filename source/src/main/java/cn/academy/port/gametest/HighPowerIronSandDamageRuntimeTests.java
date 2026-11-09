package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.core.CombatAmounts;
import com.mojang.authlib.GameProfile;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native THROWN controls for the narrowly reviewed iron-sand exact source, not trajectory or client acceptance. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerIronSandDamageRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_iron_sand_damage";
    private static final BigDecimal MAX_HP = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth();
    private static final BigDecimal SATURATED_HIT = BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.TEN);
    private static final BigDecimal LOW_HIT = SATURATED_HIT;
    private HighPowerIronSandDamageRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_thrown_source_preserves_caster_and_native_death_credit_without_projectile_entity(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeTarget = f.target(5.5, 2, 8.5), exactTarget = f.enrolled(13.5, 2, 8.5, 1);
        Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
        Vec3 nativeOrigin = segmentOrigin(nativeTarget, -2), exactOrigin = segmentOrigin(exactTarget, -2);
        h.assertTrue(f.thrown(caster, nativeTarget, nativeOrigin, nativeTarget.getHealth()), "ordinary vanilla thrown control is lethal");
        long projectilesBefore = f.projectileCount();
        h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exactTarget,
                new BigDecimal(HighPowerCombat.healthText(exactTarget)), exactOrigin), "exact raw lethality reaches the enclosing native death path");
        h.assertTrue(f.projectileCount() == projectilesBefore, "the exact bridge does not spawn a synthetic projectile entity");
        assertThrown(h, nativeHit.source, caster, nativeOrigin, false);
        assertThrown(h, exactHit.source, caster, exactOrigin, true);
        h.assertTrue(exactHit.source.typeHolder().equals(nativeHit.source.typeHolder()), "the exact source retains the canonical vanilla THROWN holder");
        h.assertTrue(nativeHit.incoming == 1 && exactHit.incoming == 1 && nativeHit.posts == 1 && exactHit.posts == 1,
                "each lethal control traverses one real incoming and Post pipeline");
        h.assertTrue(nativeHit.deaths == 1 && exactHit.deaths == 1
                && nativeHit.deathSource == nativeHit.source && exactHit.deathSource == exactHit.source,
                "native death receives the very same source once, without a synthetic second hit");
        h.assertTrue(nativeTarget.isDeadOrDying() && exactTarget.isDeadOrDying(), "both controls really died");
        h.assertTrue(nativeTarget.getKillCredit() == caster && exactTarget.getKillCredit() == caster
                && nativeTarget.getLastHurtByMob() == caster && exactTarget.getLastHurtByMob() == caster,
                "native player kill credit and attacker attribution survive absent exact direct entity");
        exact(h, exactTarget, BigDecimal.ZERO, "lethal exact transaction commits zero health");
        h.assertTrue(exactHit.skillCalls == 1 && (HighPowerSkills.CATEGORY + "." + HighPowerSkills.IRON_SAND_LANCE).equals(exactHit.skill),
                "the specialized entry point supplies its fixed fully qualified iron-sand skill to the real skill event; calls="
                        + exactHit.skillCalls + ", skill=" + exactHit.skill);
        var death = exactHit.source.getLocalizedDeathMessage(exactTarget).getContents();
        h.assertTrue(death instanceof TranslatableContents text && text.getKey().equals("death.attack.ac_skill")
                && text.getArgs().length == 3 && text.getArgs()[2] instanceof Component skill
                && skill.getContents() instanceof TranslatableContents skillName
                && skillName.getKey().equals("ac.ability.electromaster.iron_sand_lance.name"),
                "iron-sand attribution retains the actual native skill death message");
        caster.setPos(exactTarget.position().add(0, 0, 3));
        h.assertTrue(exactHit.source.getSourcePosition().equals(exactOrigin), "moving the owner never rewrites the captured server segment");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_front_and_back_shields_follow_incoming_segment_when_caster_crosses_target(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeFront = f.target(5.5, 2, 8.5), exactFront = f.enrolled(11.5, 2, 8.5, 5);
        Villager nativeBack = f.target(17.5, 2, 8.5), exactBack = f.enrolled(23.5, 2, 8.5, 5);
        for (Villager target : List.of(nativeFront, exactFront, nativeBack, exactBack)) raiseNorthShield(target);
        Observation nativeFrontHit = f.observe(nativeFront), exactFrontHit = f.observe(exactFront);
        Observation nativeBackHit = f.observe(nativeBack), exactBackHit = f.observe(exactBack);
        h.runAtTickTime(7, () -> {
            for (Villager target : List.of(nativeFront, exactFront, nativeBack, exactBack)) {
                faceNorth(target);
                h.assertTrue(target.isBlocking(), "real held shield completed its native raising delay");
            }
            caster.setPos(nativeFront.position().add(0, 0, 6));
            h.assertFalse(nativeFront.isDamageSourceBlocked(nativeFront.damageSources().playerAttack(caster)), "native control owner is behind the shield");
            h.assertFalse(f.thrown(caster, nativeFront, segmentOrigin(nativeFront, -2), 5), "ordinary thrown impact from the front is blocked despite owner behind");
            caster.setPos(exactFront.position().add(0, 0, 6));
            h.assertFalse(exactFront.isDamageSourceBlocked(exactFront.damageSources().playerAttack(caster)), "exact owner is also on the wrong side for a player-attack source");
            h.assertFalse(HighPowerCombat.attackIronSandLance(caster, exactFront, BigDecimal.valueOf(5), segmentOrigin(exactFront, -2)),
                    "exact thrown impact follows its front segment, not the moved caster");
            h.assertTrue(nativeFront.getHealth() == nativeFront.getMaxHealth(), "native frontal block preserves health");
            exact(h, exactFront, MAX_HP, "exact frontal block preserves all ledger HP");
            h.assertTrue(nativeFrontHit.shields == 1 && exactFrontHit.shields == 1
                    && nativeFrontHit.originalBlock && exactFrontHit.originalBlock && nativeFrontHit.blocked && exactFrontHit.blocked
                    && nativeFrontHit.blockedDamage == 5 && exactFrontHit.blockedDamage == 5
                    && nativeFrontHit.shieldDamage == exactFrontHit.shieldDamage && exactFrontHit.shieldDamage > 0,
                    "both real shield events decide and account for the same full block");

            caster.setPos(nativeBack.position().add(0, 0, -6));
            h.assertTrue(nativeBack.isDamageSourceBlocked(nativeBack.damageSources().playerAttack(caster)), "native control owner has moved in front of the shield");
            h.assertTrue(f.thrown(caster, nativeBack, segmentOrigin(nativeBack, 2), 5), "ordinary thrown rear impact passes despite owner in front");
            caster.setPos(exactBack.position().add(0, 0, -6));
            h.assertTrue(exactBack.isDamageSourceBlocked(exactBack.damageSources().playerAttack(caster)), "exact owner is in front and would block the old source");
            Vec3 exactBackOrigin = segmentOrigin(exactBack, 2);
            h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exactBack, BigDecimal.valueOf(5), exactBackOrigin),
                    "exact rear impact follows the incoming segment rather than caster position");
            h.assertTrue(nativeBack.getHealth() == nativeBack.getMaxHealth() - 5, "ordinary rear impact removes five native HP");
            exact(h, exactBack, MAX_HP.subtract(BigDecimal.valueOf(5)), "exact rear impact removes five exact HP");
            h.assertTrue(nativeBackHit.shields == 1 && exactBackHit.shields == 1
                    && !nativeBackHit.originalBlock && !exactBackHit.originalBlock && !nativeBackHit.blocked && !exactBackHit.blocked
                    && nativeBackHit.shieldDamage == 0 && exactBackHit.shieldDamage == 0,
                    "neither real shield event applies a rear block or shield durability loss");
            assertThrown(h, exactBackHit.source, caster, exactBackOrigin, true);
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 25)
    public static void iron_sand_native_iframes_reject_equal_hits_accept_stronger_residual_and_expire_naturally(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeTarget = f.target(5.5, 2, 8.5), exactTarget = f.enrolled(13.5, 2, 8.5, 5);
        Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
        h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 5), "first ordinary thrown hit");
        h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exactTarget, BigDecimal.valueOf(5), segmentOrigin(exactTarget, -2)), "first exact thrown hit");
        h.assertTrue(nativeTarget.invulnerableTime == 20 && exactTarget.invulnerableTime == 20, "both native hurt paths establish twenty ticks");
        h.assertFalse(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 5), "ordinary same-tick equal hit refuses");
        h.assertFalse(HighPowerCombat.attackIronSandLance(caster, exactTarget, BigDecimal.valueOf(5), segmentOrigin(exactTarget, -2)), "exact same-tick equal hit refuses");
        h.runAtTickTime(4, () -> {
            int nativeTimer = nativeTarget.invulnerableTime, exactTimer = exactTarget.invulnerableTime;
            h.assertTrue(nativeTimer == exactTimer && nativeTimer > 10 && nativeTimer < 20, "timers advance naturally within the native differential window");
            h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 9), "ordinary stronger thrown hit accepts only its residual");
            h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exactTarget, BigDecimal.valueOf(9), segmentOrigin(exactTarget, -2)), "exact stronger thrown hit accepts the same raw residual");
            h.assertTrue(nativeHit.damage == 4 && exactHit.damage == 4 && nativeHit.posts == 2 && exactHit.posts == 2,
                    "both real Post events expose exactly four damage; rejected hits add no Post");
            h.assertTrue(nativeTarget.getHealth() == 11, "ordinary accumulated damage is nine");
            exact(h, exactTarget, MAX_HP.subtract(BigDecimal.valueOf(9)), "exact accumulated damage is nine");
            h.assertTrue(nativeTarget.invulnerableTime == nativeTimer && exactTarget.invulnerableTime == exactTimer, "stronger hits do not reset either timer");
            h.assertFalse(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 9), "ordinary immediate repeated stronger amount refuses");
            h.assertFalse(HighPowerCombat.attackIronSandLance(caster, exactTarget, BigDecimal.valueOf(9), segmentOrigin(exactTarget, -2)), "exact immediate repeated stronger amount refuses");
        });
        h.runAtTickTime(12, () -> {
            h.assertTrue(nativeTarget.invulnerableTime <= 10 && exactTarget.invulnerableTime <= 10, "native timers reach the full-hit window without fixture resets");
            h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 9), "ordinary equal amount is accepted after the native window");
            h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exactTarget, BigDecimal.valueOf(9), segmentOrigin(exactTarget, -2)), "exact equal amount is accepted after the native window");
            h.assertTrue(nativeHit.damage == 9 && exactHit.damage == 9 && nativeTarget.getHealth() == 2, "full nine damage is restored in the native control and exact witness");
            exact(h, exactTarget, MAX_HP.subtract(BigDecimal.valueOf(18)), "new native hurt window debits the full raw amount once");
            h.assertTrue(nativeTarget.invulnerableTime == 20 && exactTarget.invulnerableTime == 20, "native code establishes the next ordinary twenty-tick window");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_saturated_witness_still_distinguishes_raw_stronger_and_one_point_residuals(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeTarget = f.target(5.5, 2, 8.5), exactTarget = f.enrolled(13.5, 2, 8.5, 5);
        Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
        BigDecimal first = BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.valueOf(2));
        h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 5), "ordinary control establishes logical five");
        h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exactTarget, first, segmentOrigin(exactTarget, -2)), "huge exact input establishes its own raw history");
        h.assertTrue(exactHit.original == HighPowerCombat.MAX_NATIVE_WITNESS, "huge exact hit uses the bounded native witness");
        h.runAtTickTime(4, () -> {
            int nativeTimer = nativeTarget.invulnerableTime, exactTimer = exactTarget.invulnerableTime;
            h.assertTrue(nativeTimer == exactTimer && exactTimer > 10 && exactTimer < 20, "paired native cooldown timers have really advanced");
            BigDecimal second = first.add(LOW_HIT), third = second.add(BigDecimal.ONE);
            h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 6), "ordinary stronger six passes");
            h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exactTarget, second, segmentOrigin(exactTarget, -2)), "larger raw amount passes despite identical saturated native witness");
            exact(h, exactTarget, MAX_HP.subtract(second), "only the exact ten-million residual is charged");
            h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 7), "ordinary additional one-point residual passes");
            h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exactTarget, third, segmentOrigin(exactTarget, -2)), "one exact HP over the huge raw amount still passes");
            h.assertTrue(nativeHit.damage == 1 && exactHit.damage == 1, "actual native Post records a one-point residual in each case");
            exact(h, exactTarget, MAX_HP.subtract(third), "one-point residual survives exact arithmetic");
            h.assertFalse(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 7), "ordinary equal amount refuses");
            h.assertFalse(HighPowerCombat.attackIronSandLance(caster, exactTarget, third, segmentOrigin(exactTarget, -2)), "equal huge exact amount refuses");
            h.assertFalse(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 6), "ordinary weaker amount refuses");
            h.assertFalse(HighPowerCombat.attackIronSandLance(caster, exactTarget, second, segmentOrigin(exactTarget, -2)), "weaker exact amount refuses");
            h.assertTrue(nativeHit.posts == 3 && exactHit.posts == 3 && nativeTarget.getHealth() == 13, "only three accepted native transactions committed");
            exact(h, exactTarget, MAX_HP.subtract(third), "equal and weaker refusals neither replay nor heal damage");
            h.assertTrue(nativeTarget.invulnerableTime == nativeTimer && exactTarget.invulnerableTime == exactTimer, "no stronger or refused hit resets either native timer");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_thrown_retains_native_armor_resistance_and_basic_protection(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeTarget = f.target(5.5, 2, 8.5), exactTarget = f.enrolled(13.5, 2, 8.5, 5), hugeTarget = f.enrolled(21.5, 2, 8.5, 5);
        nativeTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); nativeTarget.setHealth(100);
        for (Villager target : List.of(nativeTarget, exactTarget, hugeTarget)) {
            protection(h, target, false);
            target.getAttribute(Attributes.ARMOR).setBaseValue(20);
            target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
            target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
            h.assertTrue(target.getArmorValue() == 20 && target.getAttributeValue(Attributes.ARMOR_TOUGHNESS) == 8,
                    "paired fixtures expose the same reviewed twenty armor and eight toughness");
            h.assertTrue(HighPowerCombat.unsupportedReason(target).isEmpty(), "ordinary Protection IV remains inside the reviewed contract: " + HighPowerCombat.unsupportedReason(target));
        }
        Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget), hugeHit = f.observe(hugeTarget);
        h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 100), "ordinary thrown traverses all three native mitigations");
        h.assertTrue(HighPowerCombat.attackIronSandLance(caster, exactTarget, BigDecimal.valueOf(100), segmentOrigin(exactTarget, -2)), "exact thrown traverses the same native mitigation chain");
        h.assertTrue(nativeHit.armor > 0 && nativeHit.resistance > 0 && nativeHit.enchantments > 0
                && exactHit.armor == nativeHit.armor && exactHit.resistance == nativeHit.resistance && exactHit.enchantments == nativeHit.enchantments,
                "read-only real Post captures matching positive armor, Resistance I and Protection IV reductions");
        h.assertTrue(Math.abs(nativeHit.damage - 56.448F) < .00001F
                && Math.abs((100 - nativeTarget.getHealth()) - 56.448F) < .00001F, "ordinary native control agrees within actual float rounding");
        exact(h, exactTarget, MAX_HP.subtract(new BigDecimal("56.448")), "exact raw 100 retains armor .84, Resistance .8 and Protection .84");
        h.assertTrue(HighPowerCombat.attackIronSandLance(caster, hugeTarget, LOW_HIT, segmentOrigin(hugeTarget, -2)), "large exact thrown input remains admitted without any bypass tag");
        h.assertTrue(hugeHit.original == HighPowerCombat.MAX_NATIVE_WITNESS && hugeHit.armor > 0 && hugeHit.resistance > 0 && hugeHit.enchantments > 0,
                "all native defenses also execute for the bounded large-hit witness");
        exact(h, hugeTarget, MAX_HP.subtract(new BigDecimal("5644800")), "large raw damage retains all reviewed reductions exactly");
        assertThrown(h, hugeHit.source, caster, segmentOrigin(hugeTarget, -2), true);
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_projectile_protection_remains_explicitly_outside_exact_contract(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeTarget = f.target(5.5, 2, 8.5), exactTarget = f.enrolled(13.5, 2, 8.5, 5);
        protection(h, nativeTarget, true); protection(h, exactTarget, true);
        Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
        h.assertTrue(HighPowerCombat.unsupportedReason(exactTarget).orElse("").contains("Only reviewed vanilla Protection I-IV"),
                "Projectile Protection still has an explicit unsupported-enchantment reason");
        h.assertFalse(HighPowerCombat.attackIronSandLance(caster, exactTarget, LOW_HIT, segmentOrigin(exactTarget, -2)),
                "new projectile semantics do not silently broaden the exact enchantment contract");
        h.assertTrue(exactHit.incoming == 0 && exactHit.posts == 0, "unsupported exact attack refuses before native damage begins");
        exact(h, exactTarget, MAX_HP, "unsupported Projectile Protection cannot debit exact HP");
        h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 10), "ordinary thrown combat remains available with Projectile Protection");
        h.assertTrue(nativeHit.enchantments > 0 && nativeHit.damage < 10, "actual vanilla projectile-tagged enchantment protection executes");
        h.assertTrue(f.thrown(caster, exactTarget, segmentOrigin(exactTarget, -2), 10), "an enrolled unsupported target also retains ordinary native thrown combat");
        h.assertTrue(exactHit.incoming == 1 && exactHit.posts == 1 && exactHit.enchantments == nativeHit.enchantments && exactHit.damage == nativeHit.damage,
                "ordinary projectile protection is neither disabled nor duplicated on the enrolled target");
        exact(h, exactTarget, MAX_HP.subtract(new BigDecimal(Float.toString(exactHit.damage))), "ordinary admitted damage keeps its actual native float value");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void iron_sand_specialization_does_not_change_existing_player_attack_source(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(); Villager target = f.enrolled(5.5, 2, 8.5, 5);
        Observation hit = f.observe(target);
        h.assertTrue(HighPowerCombat.attack(caster, HighPowerSkills.CATEGORY + "." + HighPowerSkills.FORKED_ARC, target, BigDecimal.valueOf(5)), "existing exact attack API remains usable");
        DamageSource nativeControl = target.damageSources().playerAttack(caster);
        h.assertTrue(hit.source.is(DamageTypes.PLAYER_ATTACK) && !hit.source.is(DamageTypeTags.IS_PROJECTILE)
                && hit.source.typeHolder().equals(nativeControl.typeHolder())
                && hit.source.getEntity() == caster && hit.source.getDirectEntity() == caster
                && hit.source.sourcePositionRaw() == null && hit.source.getSourcePosition().equals(caster.position()),
                "the preexisting exact API keeps its canonical player-attack identity and owner-relative position");
        exact(h, target, MAX_HP.subtract(BigDecimal.valueOf(5)), "existing source still debits one exact hit");
        h.succeed();
    }

    private static Vec3 segmentOrigin(LivingEntity target, double zOffset) { return target.position().add(0, .8, zOffset); }
    private static void faceNorth(LivingEntity target) { target.setYRot(180); target.setYHeadRot(180); target.yBodyRot = 180; }
    private static void raiseNorthShield(LivingEntity target) {
        faceNorth(target); target.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        target.startUsingItem(InteractionHand.OFF_HAND);
    }
    private static void protection(GameTestHelper h, LivingEntity target, boolean projectile) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(projectile ? Enchantments.PROJECTILE_PROTECTION : Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); target.setItemSlot(EquipmentSlot.HEAD, helmet);
    }
    private static void exact(GameTestHelper h, LivingEntity target, BigDecimal expected, String message) {
        h.assertTrue(new BigDecimal(HighPowerCombat.healthText(target)).compareTo(expected) == 0,
                message + "; expected=" + expected.toPlainString() + ", actual=" + HighPowerCombat.healthText(target));
    }
    private static void assertThrown(GameTestHelper h, DamageSource source, ServerPlayer caster, Vec3 origin, boolean exactSource) {
        h.assertTrue(source != null && source.is(DamageTypes.THROWN) && source.is(DamageTypeTags.IS_PROJECTILE), "actual event source is vanilla THROWN and projectile-tagged");
        h.assertTrue(!source.is(DamageTypeTags.BYPASSES_ARMOR) && !source.is(DamageTypeTags.BYPASSES_SHIELD)
                && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) && !source.is(DamageTypeTags.BYPASSES_COOLDOWN)
                && !source.is(DamageTypeTags.BYPASSES_EFFECTS) && !source.is(DamageTypeTags.BYPASSES_RESISTANCE)
                && !source.is(DamageTypeTags.BYPASSES_ENCHANTMENTS) && !source.is(DamageTypeTags.IS_PLAYER_ATTACK),
                "the actual event source contains no armor, shield, iframe, effect, resistance or enchantment bypass");
        h.assertTrue(source.getEntity() == caster && !source.isDirect(), "caster remains causing entity rather than direct impact entity");
        h.assertTrue(source.getSourcePosition().equals(origin), "actual source position is the incoming server segment");
        h.assertTrue(exactSource ? source.getDirectEntity() == null && source.sourcePositionRaw().equals(origin)
                        : source.getDirectEntity() instanceof Snowball snowball && snowball.getOwner() == caster && source.sourcePositionRaw() == null,
                exactSource ? "exact source carries position without a fake direct entity" : "ordinary control retains its real vanilla thrown projectile owner");
    }

    /** Read-only listeners observe native events; no setters, replacement reductions or contract weakening. */
    public static final class Observation {
        final LivingEntity target;
        DamageSource source, deathSource;
        String skill;
        int incoming, posts, shields, deaths, skillCalls;
        boolean originalBlock, blocked;
        float original, damage, armor, resistance, enchantments, blockedDamage, shieldDamage;
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) {
            if (event.getEntity() == target) { incoming++; source = event.getSource(); }
        }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) {
            if (event.getEntity() != target) return;
            posts++; original = event.getOriginalDamage(); damage = event.getNewDamage();
            armor = event.getReduction(DamageContainer.Reduction.ARMOR);
            resistance = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS);
            enchantments = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS);
        }
        @SubscribeEvent public void shield(LivingShieldBlockEvent event) {
            if (event.getEntity() != target) return;
            shields++; originalBlock = event.getOriginalBlock(); blocked = event.getBlocked();
            blockedDamage = event.getBlockedDamage(); shieldDamage = event.shieldDamage();
        }
        @SubscribeEvent public void death(LivingDeathEvent event) {
            if (event.getEntity() == target) { deaths++; deathSource = event.getSource(); }
        }
        @SubscribeEvent public void skill(SkillAttackEvent event) {
            if (event.target == target) { skillCalls++; skill = event.skill; }
        }
    }

    private static final class Fixture {
        final GameTestHelper h;
        final List<Entity> entities = new ArrayList<>();
        final List<ServerPlayer> players = new ArrayList<>();
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
        ServerPlayer caster() {
            ServerPlayer caster = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-IronHit]"));
            players.add(caster); caster.setPos(h.absoluteVec(new Vec3(5.5, 2, 4.5)));
            caster.setNoGravity(true); caster.getAbilities().instabuild = false;
            var ability = AbilityStorage.get(caster); ability.changeCategoryClassic("electromaster"); ability.setLevel(5);
            h.assertTrue(HighPowerCombat.enroll(caster, 5), "real supported test caster enrollment: " + HighPowerCombat.unsupportedReason(caster));
            return caster;
        }
        Villager target(double x, double y, double z) {
            Villager target = new Villager(EntityType.VILLAGER, h.getLevel());
            target.setNoAi(true); target.setNoGravity(true); target.setPos(h.absoluteVec(new Vec3(x, y, z)));
            h.getLevel().addFreshEntity(target); entities.add(target); return target;
        }
        Villager enrolled(double x, double y, double z, int level) {
            Villager target = target(x, y, z);
            h.assertTrue(HighPowerCombat.enroll(target, level), "real supported exact target enrollment: " + HighPowerCombat.unsupportedReason(target));
            return target;
        }
        boolean thrown(ServerPlayer caster, LivingEntity target, Vec3 origin, float amount) {
            Snowball projectile = new Snowball(EntityType.SNOWBALL, h.getLevel());
            projectile.setOwner(caster); projectile.setNoGravity(true); projectile.setPos(origin);
            h.getLevel().addFreshEntity(projectile); entities.add(projectile);
            try { return target.hurt(target.damageSources().thrown(projectile, caster), amount); }
            finally { projectile.discard(); } // Native source control cannot later collide or add an unrelated hit.
        }
        long projectileCount() {
            long count = 0;
            for (Entity entity : h.getLevel().getAllEntities()) if (entity instanceof Projectile && !entity.isRemoved()) count++;
            return count;
        }
        Observation observe(LivingEntity target) {
            Observation observer = new Observation(target); hooks.add(observer); NeoForge.EVENT_BUS.register(observer); return observer;
        }
        void close() {
            if (closed) return; closed = true;
            hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer player : players) { AbilityStorage.remove(player); player.discard(); }
            entities.forEach(Entity::discard);
        }
    }
}
