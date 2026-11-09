package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.AbilityDamage;
import cn.academy.port.api.SkillAttackEvent;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.HighPowerSkills;
import cn.academy.port.highpower.GroundingReturn;
import cn.academy.port.highpower.PotentialBuffer;
import cn.academy.port.highpower.HighPowerInput;
import cn.academy.port.highpower.ElectromasterPrototype;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.core.CombatAmounts;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
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
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.chat.contents.TranslatableContents;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.Snowball;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.core.BlockPos;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native THROWN controls for the narrowly reviewed projected rail-array exact source, not trajectory or client acceptance. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerRailArrayDamageRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_rail_array_damage";
    private static final BigDecimal MAX_HP = cn.academy.port.highpower.core.HighPowerLevel.LV5.maxHealth();
    private static final BigDecimal SATURATED_HIT = BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.TEN);
    private static final BigDecimal LOW_HIT = SATURATED_HIT;
    private HighPowerRailArrayDamageRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_thrown_source_preserves_caster_and_native_death_credit_without_projectile_entity(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeTarget = f.target(5.5, 2, 8.5), exactTarget = f.enrolled(13.5, 2, 8.5, 1);
        Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
        Vec3 nativeOrigin = segmentOrigin(nativeTarget, -2), exactOrigin = segmentOrigin(exactTarget, -2);
        h.assertTrue(f.thrown(caster, nativeTarget, nativeOrigin, nativeTarget.getHealth()), "ordinary vanilla thrown control is lethal");
        long projectilesBefore = f.projectileCount();
        h.assertTrue(rail(caster, exactTarget,
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
        h.assertTrue(exactHit.skillCalls == 1 && "electromaster.rail_thunder_array".equals(exactHit.skill),
                "the specialized entry point supplies its fixed fully qualified projected rail-array skill to the real skill event; calls="
                        + exactHit.skillCalls + ", skill=" + exactHit.skill);
        var death = exactHit.source.getLocalizedDeathMessage(exactTarget).getContents();
        h.assertTrue(death instanceof TranslatableContents text && text.getKey().equals("death.attack.ac_skill")
                && text.getArgs().length == 3 && text.getArgs()[2] instanceof Component skill
                && skill.getContents() instanceof TranslatableContents skillName
                && skillName.getKey().equals("ac.ability.electromaster.rail_thunder_array.name"),
                "projected rail-array attribution retains the actual native skill death message");
        caster.setPos(exactTarget.position().add(0, 0, 3));
        h.assertTrue(exactHit.source.getSourcePosition().equals(exactOrigin), "moving the owner never rewrites the captured server segment");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_front_and_back_shields_follow_incoming_segment_when_caster_crosses_target(GameTestHelper h) {
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
            h.assertFalse(f.thrown(caster, nativeFront, segmentOrigin(nativeFront, -12), 5), "ordinary thrown impact from the front is blocked despite owner behind");
            caster.setPos(exactFront.position().add(0, 0, 6));
            h.assertFalse(exactFront.isDamageSourceBlocked(exactFront.damageSources().playerAttack(caster)), "exact owner is also on the wrong side for a player-attack source");
            h.assertFalse(rail(caster, exactFront, BigDecimal.valueOf(5), segmentOrigin(exactFront, -12)),
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
            h.assertTrue(f.thrown(caster, nativeBack, segmentOrigin(nativeBack, 12), 5), "ordinary thrown rear impact passes despite owner in front");
            caster.setPos(exactBack.position().add(0, 0, -6));
            h.assertTrue(exactBack.isDamageSourceBlocked(exactBack.damageSources().playerAttack(caster)), "exact owner is in front and would block the old source");
            Vec3 exactBackOrigin = segmentOrigin(exactBack, 12);
            h.assertTrue(rail(caster, exactBack, BigDecimal.valueOf(5), exactBackOrigin),
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
    public static void rail_array_native_iframes_reject_equal_hits_accept_stronger_residual_and_expire_naturally(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeTarget = f.target(5.5, 2, 8.5), exactTarget = f.enrolled(13.5, 2, 8.5, 5);
        Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
        h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 5), "first ordinary thrown hit");
        h.assertTrue(rail(caster, exactTarget, BigDecimal.valueOf(5), segmentOrigin(exactTarget, -2)), "first exact thrown hit");
        h.assertTrue(nativeTarget.invulnerableTime == 20 && exactTarget.invulnerableTime == 20, "both native hurt paths establish twenty ticks");
        h.assertFalse(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 5), "ordinary same-tick equal hit refuses");
        h.assertFalse(rail(caster, exactTarget, BigDecimal.valueOf(5), segmentOrigin(exactTarget, -2)), "exact same-tick equal hit refuses");
        h.runAtTickTime(4, () -> {
            int nativeTimer = nativeTarget.invulnerableTime, exactTimer = exactTarget.invulnerableTime;
            h.assertTrue(nativeTimer == exactTimer && nativeTimer > 10 && nativeTimer < 20, "timers advance naturally within the native differential window");
            h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 9), "ordinary stronger thrown hit accepts only its residual");
            h.assertTrue(rail(caster, exactTarget, BigDecimal.valueOf(9), segmentOrigin(exactTarget, -2)), "exact stronger thrown hit accepts the same raw residual");
            h.assertTrue(nativeHit.damage == 4 && exactHit.damage == 4 && nativeHit.posts == 2 && exactHit.posts == 2,
                    "both real Post events expose exactly four damage; rejected hits add no Post");
            h.assertTrue(nativeTarget.getHealth() == 11, "ordinary accumulated damage is nine");
            exact(h, exactTarget, MAX_HP.subtract(BigDecimal.valueOf(9)), "exact accumulated damage is nine");
            h.assertTrue(nativeTarget.invulnerableTime == nativeTimer && exactTarget.invulnerableTime == exactTimer, "stronger hits do not reset either timer");
            h.assertFalse(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 9), "ordinary immediate repeated stronger amount refuses");
            h.assertFalse(rail(caster, exactTarget, BigDecimal.valueOf(9), segmentOrigin(exactTarget, -2)), "exact immediate repeated stronger amount refuses");
        });
        h.runAtTickTime(12, () -> {
            h.assertTrue(nativeTarget.invulnerableTime <= 10 && exactTarget.invulnerableTime <= 10, "native timers reach the full-hit window without fixture resets");
            h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 9), "ordinary equal amount is accepted after the native window");
            h.assertTrue(rail(caster, exactTarget, BigDecimal.valueOf(9), segmentOrigin(exactTarget, -2)), "exact equal amount is accepted after the native window");
            h.assertTrue(nativeHit.damage == 9 && exactHit.damage == 9 && nativeTarget.getHealth() == 2, "full nine damage is restored in the native control and exact witness");
            exact(h, exactTarget, MAX_HP.subtract(BigDecimal.valueOf(18)), "new native hurt window debits the full raw amount once");
            h.assertTrue(nativeTarget.invulnerableTime == 20 && exactTarget.invulnerableTime == 20, "native code establishes the next ordinary twenty-tick window");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_saturated_witness_still_distinguishes_raw_stronger_and_one_point_residuals(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeTarget = f.target(5.5, 2, 8.5), exactTarget = f.enrolled(13.5, 2, 8.5, 5);
        Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
        BigDecimal first = BigDecimal.valueOf(HighPowerCombat.MAX_NATIVE_WITNESS).multiply(BigDecimal.valueOf(2));
        h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 5), "ordinary control establishes logical five");
        h.assertTrue(rail(caster, exactTarget, first, segmentOrigin(exactTarget, -2)), "huge exact input establishes its own raw history");
        h.assertTrue(exactHit.original == HighPowerCombat.MAX_NATIVE_WITNESS, "huge exact hit uses the bounded native witness");
        h.runAtTickTime(4, () -> {
            int nativeTimer = nativeTarget.invulnerableTime, exactTimer = exactTarget.invulnerableTime;
            h.assertTrue(nativeTimer == exactTimer && exactTimer > 10 && exactTimer < 20, "paired native cooldown timers have really advanced");
            BigDecimal second = first.add(LOW_HIT), third = second.add(BigDecimal.ONE);
            h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 6), "ordinary stronger six passes");
            h.assertTrue(rail(caster, exactTarget, second, segmentOrigin(exactTarget, -2)), "larger raw amount passes despite identical saturated native witness");
            exact(h, exactTarget, MAX_HP.subtract(second), "only the exact ten-million residual is charged");
            h.assertTrue(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 7), "ordinary additional one-point residual passes");
            h.assertTrue(rail(caster, exactTarget, third, segmentOrigin(exactTarget, -2)), "one exact HP over the huge raw amount still passes");
            h.assertTrue(nativeHit.damage == 1 && exactHit.damage == 1, "actual native Post records a one-point residual in each case");
            exact(h, exactTarget, MAX_HP.subtract(third), "one-point residual survives exact arithmetic");
            h.assertFalse(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 7), "ordinary equal amount refuses");
            h.assertFalse(rail(caster, exactTarget, third, segmentOrigin(exactTarget, -2)), "equal huge exact amount refuses");
            h.assertFalse(f.thrown(caster, nativeTarget, segmentOrigin(nativeTarget, -2), 6), "ordinary weaker amount refuses");
            h.assertFalse(rail(caster, exactTarget, second, segmentOrigin(exactTarget, -2)), "weaker exact amount refuses");
            h.assertTrue(nativeHit.posts == 3 && exactHit.posts == 3 && nativeTarget.getHealth() == 13, "only three accepted native transactions committed");
            exact(h, exactTarget, MAX_HP.subtract(third), "equal and weaker refusals neither replay nor heal damage");
            h.assertTrue(nativeTarget.invulnerableTime == nativeTimer && exactTarget.invulnerableTime == exactTimer, "no stronger or refused hit resets either native timer");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_thrown_retains_native_armor_resistance_and_basic_protection(GameTestHelper h) {
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
        h.assertTrue(rail(caster, exactTarget, BigDecimal.valueOf(100), segmentOrigin(exactTarget, -2)), "exact thrown traverses the same native mitigation chain");
        h.assertTrue(nativeHit.armor > 0 && nativeHit.resistance > 0 && nativeHit.enchantments > 0
                && exactHit.armor == nativeHit.armor && exactHit.resistance == nativeHit.resistance && exactHit.enchantments == nativeHit.enchantments,
                "read-only real Post captures matching positive armor, Resistance I and Protection IV reductions");
        h.assertTrue(Math.abs(nativeHit.damage - 56.448F) < .00001F
                && Math.abs((100 - nativeTarget.getHealth()) - 56.448F) < .00001F, "ordinary native control agrees within actual float rounding");
        exact(h, exactTarget, MAX_HP.subtract(new BigDecimal("56.448")), "exact raw 100 retains armor .84, Resistance .8 and Protection .84");
        h.assertTrue(rail(caster, hugeTarget, LOW_HIT, segmentOrigin(hugeTarget, -2)), "large exact thrown input remains admitted without any bypass tag");
        h.assertTrue(hugeHit.original == HighPowerCombat.MAX_NATIVE_WITNESS && hugeHit.armor > 0 && hugeHit.resistance > 0 && hugeHit.enchantments > 0,
                "all native defenses also execute for the bounded large-hit witness");
        exact(h, hugeTarget, MAX_HP.subtract(new BigDecimal("5644800")), "large raw damage retains all reviewed reductions exactly");
        assertThrown(h, hugeHit.source, caster, segmentOrigin(hugeTarget, -2), true);
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_projectile_protection_remains_explicitly_outside_exact_contract(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeTarget = f.target(5.5, 2, 8.5), exactTarget = f.enrolled(13.5, 2, 8.5, 5);
        protection(h, nativeTarget, true); protection(h, exactTarget, true);
        Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
        h.assertTrue(HighPowerCombat.unsupportedReason(exactTarget).orElse("").contains("Only reviewed vanilla Protection I-IV"),
                "Projectile Protection still has an explicit unsupported-enchantment reason");
        h.assertFalse(rail(caster, exactTarget, LOW_HIT, segmentOrigin(exactTarget, -2)),
                "new projectile semantics do not silently broaden the exact enchantment contract");
        h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, exactTarget, LOW_HIT, false,
                caster.position(), () -> true), "alternative electrical net also refuses unsupported Projectile Protection");
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
    public static void rail_array_specialization_does_not_change_existing_player_attack_source(GameTestHelper h) {
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

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_both_modes_preserve_native_absorption_after_reviewed_mitigation(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        for (boolean physical : List.of(true, false)) {
            double x = physical ? 5.5 : 21.5;
            Villager nativeTarget = f.target(x, 2, 16.5), exactTarget = f.enrolled(x + 8, 2, 16.5, 5);
            nativeTarget.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100); nativeTarget.setHealth(100);
            for (Villager target : List.of(nativeTarget, exactTarget)) {
                protection(h, target, false); target.getAttribute(Attributes.ARMOR).setBaseValue(20);
                target.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(8);
                target.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
                target.getAttribute(Attributes.MAX_ABSORPTION).setBaseValue(20); target.setAbsorptionAmount(4);
            }
            Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
            h.assertTrue(f.nativeHit(caster, nativeTarget, segmentOrigin(nativeTarget, -12), 100, physical), "native mode control accepts mitigation plus absorption");
            h.assertTrue(HighPowerCombat.attackRailThunderArray(caster, exactTarget, BigDecimal.valueOf(100), physical,
                    segmentOrigin(exactTarget, -12), () -> true), "guarded mode accepts the same native defenses");
            h.assertTrue(nativeHit.armor == exactHit.armor && nativeHit.resistance == exactHit.resistance
                    && nativeHit.enchantments == exactHit.enchantments && nativeHit.absorption == 4 && exactHit.absorption == 4,
                    "paired read-only native reductions match and consume four absorption exactly once");
            h.assertTrue(nativeTarget.getAbsorptionAmount() == 0 && exactTarget.getAbsorptionAmount() == 0,
                    "both actual native absorption pools are depleted");
            exact(h, exactTarget, MAX_HP.subtract(new BigDecimal("52.448")), "exact residual retains native absorption after exact mitigation");
            h.assertTrue(Math.abs(nativeHit.damage - 52.448F) < .00001F, "native control agrees within its actual float rounding");
            if (!physical) {
                DamageSource source = exactHit.source;
                h.assertTrue(source.is(DamageTypes.PLAYER_ATTACK) && !source.is(DamageTypeTags.IS_PROJECTILE)
                        && source.getEntity() == caster && source.getDirectEntity() == caster && source.sourcePositionRaw() == null
                        && source.typeHolder().equals(nativeHit.source.typeHolder()) && source.getSourcePosition().equals(caster.position()),
                        "alternative net keeps the existing ACA electrical source identity and owner-relative native shield direction");
                h.assertTrue(exactHit.skill.equals("electromaster.rail_thunder_array"), "net and rail share one literal skill identity");
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_net_retains_native_shields_iframes_and_death(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Villager nativeFront = f.target(5.5, 2, 8.5), exactFront = f.enrolled(11.5, 2, 8.5, 5);
        Villager nativeBack = f.target(17.5, 2, 8.5), exactBack = f.enrolled(23.5, 2, 8.5, 5);
        for (Villager target : List.of(nativeFront, exactFront, nativeBack, exactBack)) raiseNorthShield(target);
        Observation nativeHit = f.observe(nativeBack), exactHit = f.observe(exactBack);
        h.runAtTickTime(7, () -> {
            for (Villager target : List.of(nativeFront, exactFront, nativeBack, exactBack)) {
                faceNorth(target); h.assertTrue(target.isBlocking(), "native shield raising delay elapsed");
            }
            caster.setPos(nativeFront.position().add(0, 0, -6));
            h.assertFalse(f.nativeHit(caster, nativeFront, caster.position(), 5, false), "native front electrical control blocks");
            caster.setPos(exactFront.position().add(0, 0, -6));
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, exactFront, BigDecimal.valueOf(5), false,
                    caster.position(), () -> true), "net front native shield blocks");
            exact(h, exactFront, MAX_HP, "blocked net leaves exact health intact");
            caster.setPos(nativeBack.position().add(0, 0, 6));
            h.assertTrue(f.nativeHit(caster, nativeBack, caster.position(), 5, false), "native rear electrical control hits");
            h.assertFalse(f.nativeHit(caster, nativeBack, caster.position(), 5, false), "native equal electrical iframe refuses");
            h.assertTrue(f.nativeHit(caster, nativeBack, caster.position(), 9, false), "native stronger electrical iframe accepts residual");
            caster.setPos(exactBack.position().add(0, 0, 6));
            h.assertTrue(HighPowerCombat.attackRailThunderArray(caster, exactBack, BigDecimal.valueOf(5), false, caster.position(), () -> true), "net rear hit reaches native hurt");
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, exactBack, BigDecimal.valueOf(5), false, caster.position(), () -> true), "net equal iframe refuses");
            h.assertTrue(HighPowerCombat.attackRailThunderArray(caster, exactBack, BigDecimal.valueOf(9), false, caster.position(), () -> true), "net stronger iframe accepts residual");
            h.assertTrue(nativeHit.posts == 2 && exactHit.posts == 2 && nativeHit.damage == 4 && exactHit.damage == 4,
                    "both net pipelines record exactly two posts and a four-point residual");
            exact(h, exactBack, MAX_HP.subtract(BigDecimal.valueOf(9)), "net exact iframe history matches native");
            Villager nativeDead = f.target(5.5, 2, 20.5), exactDead = f.enrolled(13.5, 2, 20.5, 1);
            Observation nativeDeath = f.observe(nativeDead), exactDeath = f.observe(exactDead);
            h.assertTrue(f.nativeHit(caster, nativeDead, caster.position(), 20, false), "native electrical control dies");
            h.assertTrue(HighPowerCombat.attackRailThunderArray(caster, exactDead, new BigDecimal(HighPowerCombat.healthText(exactDead)),
                    false, caster.position(), () -> true), "net lethal raw input completes one native death");
            h.assertTrue(nativeDeath.deaths == 1 && exactDeath.deaths == 1 && exactDead.isDeadOrDying()
                    && exactDead.getKillCredit() == caster && exactDeath.deathSource == exactDeath.source,
                    "net preserves enclosing native death and caster credit without rerunning the live guard afterward");
            exact(h, exactDead, BigDecimal.ZERO, "net lethal ledger remains zero");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_range_uses_captured_emission_and_target_geometry_after_allowed_movement(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        Vec3 committedFeet = caster.position(), origin = caster.getEyePosition();
        caster.setPos(committedFeet.add(0, 0, -.5));
        for (boolean physical : List.of(true, false)) {
            Villager target = f.enrolled(5.5, 2, 8.5, 5);
            double centerDistance = physical ? 96.1 : 95.8;
            target.setPos(origin.x, origin.y - target.getBbHeight() / 2, origin.z + centerDistance);
            Vec3 snapshot = target.position();
            h.assertTrue(caster.distanceToSqr(target) > 96 * 96, "moved caster's feet are beyond 96 while the committed emission remains in range");
            if (physical) h.assertTrue(target.getBoundingBox().minZ - origin.z < 96,
                    "physical ray reaches the near box face at approximately 95.8 m even with center beyond 96");
            h.assertTrue(HighPowerCombat.attackRailThunderArray(caster, target, BigDecimal.valueOf(5), physical, origin,
                    () -> caster.position().distanceToSqr(committedFeet) <= 1 && target.position().equals(snapshot)),
                    "captured range survives the closed controller's allowed half-block backward movement");
            exact(h, target, MAX_HP.subtract(BigDecimal.valueOf(5)), "boundary hit reaches native hurt and debits exactly five HP");
        }
        Villager vertical = f.enrolled(13.5, 2, 8.5, 5);
        vertical.setPos(origin.add(0, 95.8, 0));
        h.assertTrue(caster.distanceToSqr(vertical) > 96 * 96, "upward ray is beyond the later feet-based radius");
        h.assertTrue(rail(caster, vertical, BigDecimal.valueOf(5), origin), "vertical ray intersects the target box within captured 96 m reach");
        exact(h, vertical, MAX_HP.subtract(BigDecimal.valueOf(5)), "vertical native hit is applied once");
        Villager beyond = f.enrolled(21.5, 2, 8.5, 5);
        beyond.setPos(origin.x, origin.y - beyond.getBbHeight() / 2, origin.z + 96.5);
        Observation refused = f.observe(beyond);
        h.assertFalse(rail(caster, beyond, BigDecimal.valueOf(5), origin), "box whose nearest point exceeds captured 96 m still refuses");
        h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, beyond, BigDecimal.valueOf(5), false, origin, () -> true),
                "net center beyond captured 96 m still refuses");
        h.assertTrue(refused.incoming == 0 && refused.posts == 0, "out-of-range geometry refuses before native hurt");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_invalid_context_closed_guard_and_unknown_contract_fail_before_hurt(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster(); Villager target = f.enrolled(13.5, 2, 8.5, 5);
        Observation hit = f.observe(target); Vec3 origin = segmentOrigin(target, -12);
        for (boolean physical : List.of(true, false)) {
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, target, LOW_HIT, physical, origin, null), "missing backend guard refuses");
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, target, LOW_HIT, physical, origin, () -> false), "closed backend transaction refuses");
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, target, LOW_HIT, physical, origin, () -> { throw new IllegalStateException("closed"); }), "failed guard refuses safely");
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, target, LOW_HIT, physical, null, () -> true), "missing server origin refuses");
            for (Vec3 invalid : List.of(new Vec3(Double.NaN, 0, 0), new Vec3(0, Double.POSITIVE_INFINITY, 0),
                    new Vec3(0, 0, Double.NEGATIVE_INFINITY), target.position().add(0, 0, 97))) {
                h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, target, LOW_HIT, physical, invalid, () -> true), "nonfinite or beyond-96 server origin refuses");
            }
            Vec3 previous = caster.position(); caster.setPos(target.position().add(0, 0, 97));
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, target, LOW_HIT, physical, origin, () -> true), "caster excessively far from captured origin fails context sanity");
            caster.setPos(previous);
            var otherLevel = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertTrue(otherLevel != null, "native server has its other dimension");
            Villager elsewhere = new Villager(EntityType.VILLAGER, otherLevel); elsewhere.setPos(target.position());
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, elsewhere, LOW_HIT, physical, origin, () -> true), "different world refuses");
        }
        h.assertFalse(HighPowerCombat.attack(caster, "electromaster.rail_thunder_array", target, LOW_HIT), "generic API cannot forge an unguarded NEW151 electrical source");
        h.assertFalse(HighPowerCombat.attackIronSandLance(caster, target, LOW_HIT, origin), "NEW151 emission-range support never widens iron-lance's close-segment bound");
        target.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("adapterContract", "unreviewed-adapter");
        for (boolean physical : List.of(true, false))
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, target, LOW_HIT, physical, origin, () -> true), "unknown adapter fails closed for both modes");
        h.assertTrue(hit.incoming == 0 && hit.posts == 0, "all invalid contexts refuse before native hurt starts");
        exact(h, target, MAX_HP, "invalid contexts never debit exact health");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void rail_array_both_modes_honor_native_cancellation_and_reentrant_movement_guards(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer caster = f.caster();
        for (boolean physical : List.of(true, false)) {
            double z = physical ? 8.5 : 20.5;
            Villager nativeTarget = f.target(5.5, 2, z), exactTarget = f.enrolled(13.5, 2, z, 5);
            Cancellation cancel = new Cancellation(nativeTarget, exactTarget); f.hook(cancel);
            Observation nativeHit = f.observe(nativeTarget), exactHit = f.observe(exactTarget);
            h.assertFalse(f.nativeHit(caster, nativeTarget, segmentOrigin(nativeTarget, -12), 5, physical), "native cancellation refuses mode control");
            h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, exactTarget, BigDecimal.valueOf(5), physical,
                    segmentOrigin(exactTarget, -12), () -> true), "native cancellation refuses exact mode");
            h.assertTrue(nativeHit.posts == 0 && exactHit.posts == 0 && nativeTarget.getHealth() == 20, "canceled mode has no native Post or health loss");
            exact(h, exactTarget, MAX_HP, "canceled mode preserves exact health");
            cancel.active = false;
            for (int stage = 0; stage < 3; stage++) for (boolean enrolled : List.of(true, false)) {
                Villager guarded = enrolled ? f.enrolled(21.5 + stage * 3, 2, z, 5) : f.target(21.5 + stage * 3, 2, z + 3);
                Vec3 snapshot = guarded.position(), origin = segmentOrigin(guarded, -12);
                java.util.function.BooleanSupplier guard = () -> guarded.position().equals(snapshot);
                boolean[] nestedAccepted = {true};
                StageMutation mutate = new StageMutation(guarded, stage, () -> {
                    guarded.setPos(guarded.position().add(.25, 0, 0));
                    nestedAccepted[0] = HighPowerCombat.attackRailThunderArray(caster, guarded, BigDecimal.ONE, physical, origin, guard);
                });
                f.hook(mutate);
                h.assertFalse(HighPowerCombat.attackRailThunderArray(caster, guarded, BigDecimal.valueOf(5), physical, origin, guard),
                        "movement from a reentrant skill, incoming or Pre callback invalidates the closed attempt");
                h.assertTrue(mutate.called && !nestedAccepted[0], "actual native callback runs once and cannot recursively revive the closed geometry");
                if (enrolled) exact(h, guarded, MAX_HP, "late guard cannot debit enrolled target");
                else h.assertTrue(guarded.getHealth() == 20, "late guard also protects ordinary un-enrolled target");
            }
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void rail_array_grounding_admits_only_the_electrical_net_variant(GameTestHelper h) {
        Defender f = new Defender(h, GroundingReturn.ID);
        f.ready(() -> {
            f.player.setShiftKeyDown(true); f.player.setOnGround(true);
            h.assertTrue(GroundingReturn.start(f.player, f.wire(1)), "real connected defender starts authenticated grounding");
            BigDecimal hp = f.hp(), shield = HighPowerCombat.shield(f.player);
            h.assertTrue(f.hit(true, 5, f.player.position().add(0, .8, 12)), "physical projected rail reaches native hurt");
            exact(h, f.player, hp.subtract(BigDecimal.valueOf(5)), "grounding does not classify a physical rail as electrical");
            equal(h, HighPowerCombat.shield(f.player), shield, "physical rail cannot spend the electrical grounding pool");
            h.assertTrue(f.hit(false, 9, f.caster.position()), "net stronger iframe produces four electrical residual points");
            exact(h, f.player, hp.subtract(BigDecimal.valueOf(5)), "actual grounding absorbs the net residual without restoring earlier physical damage");
            equal(h, HighPowerCombat.shield(f.player), shield, "one precise net absorption refunds only its four spent shield points");
            h.assertTrue(AbilityStorage.get(f.player).exp(GroundingReturn.ID) > 0, "only a real reviewed electrical absorption awards grounding experience");
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void rail_array_potential_buffer_uses_captured_physical_origin_and_native_net_origin(GameTestHelper h) {
        Defender f = new Defender(h, PotentialBuffer.ID);
        f.ready(() -> {
            h.assertTrue(PotentialBuffer.toggle(f.player, f.wire(1)), "real connected defender arms authenticated directional buffer");
            BigDecimal hp = f.hp(), shield = HighPowerCombat.shield(f.player);
            Vec3 frontEmission = f.player.position().add(0, .8, 12);
            f.caster.setPos(f.player.position().add(0, 0, -6));
            h.assertTrue(f.hit(true, 5, frontEmission), "projected front rail accepts despite owner moving behind");
            exact(h, f.player, hp, "buffer protects the real captured front ray");
            equal(h, HighPowerCombat.shield(f.player), shield.subtract(BigDecimal.valueOf(5)), "buffer spends five shield once for front rail");
            f.caster.setPos(f.player.position().add(0, 0, 6));
            h.assertTrue(f.hit(true, 9, f.player.position().add(0, .8, -12)), "rear ray accepts despite owner moving in front");
            exact(h, f.player, hp.subtract(BigDecimal.valueOf(4)), "buffer leaves rear physical iframe residual unshielded");
            equal(h, HighPowerCombat.shield(f.player), shield.subtract(BigDecimal.valueOf(5)), "rear ray spends no shared shield");
            h.assertTrue(f.hit(false, 13, f.caster.position()), "front net accepts its native owner-relative four-point residual");
            exact(h, f.player, hp.subtract(BigDecimal.valueOf(4)), "buffer also admits the reviewed electrical net");
            equal(h, HighPowerCombat.shield(f.player), shield.subtract(BigDecimal.valueOf(9)), "front net spends its own residual once");
            h.succeed();
        });
    }

    private static void equal(GameTestHelper h, BigDecimal actual, BigDecimal expected, String reason) {
        h.assertTrue(actual.compareTo(expected) == 0, reason + "; actual=" + actual + ", expected=" + expected);
    }
    private static final class Defender {
        final GameTestHelper h;
        final ServerPlayer player, caster;
        final EmbeddedChannel channel;
        boolean closed;
        Defender(GameTestHelper h, String skill) {
            this.h = h; h.setBlock(new BlockPos(6, 1, 6), Blocks.STONE);
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "HPR" + UUID.randomUUID().toString().substring(0, 10)), false);
            player = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            var connection = new Connection(PacketFlow.SERVERBOUND); channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, player, cookie);
            player.setGameMode(GameType.SURVIVAL); player.setNoGravity(true); player.getAbilities().instabuild = false; place();
            h.assertTrue(HighPowerCombat.enroll(player, 5), "approved connected defender uses exact capped capacity");
            var state = AbilityStorage.get(player); state.selectCategory("electromaster"); state.setLevel(5);
            for (String id : List.of("forked_arc", "magnetic_bracer", GroundingReturn.ID, PotentialBuffer.ID)) state.learn(id);
            state.activated = true; state.overloadFine = true;
            h.assertTrue(state.presets.edit(0, 0, skill, skill::equals), "real learned and bound defense slot");
            ElectromasterPrototype.initializeBudget(player); epoch();
            caster = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-RailDef]"));
            caster.setNoGravity(true); caster.getAbilities().instabuild = false; caster.setPos(player.position().add(0, 0, 6));
            var attackerState = AbilityStorage.get(caster); attackerState.changeCategoryClassic("electromaster"); attackerState.setLevel(5);
            h.assertTrue(HighPowerCombat.enroll(caster, 5), "reviewed physical/electrical caster");
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) {}
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
        }
        void place() {
            Vec3 at = h.absoluteVec(new Vec3(6.5, 2, 6.5)); player.moveTo(at.x, at.y, at.z, 0, 0);
            player.setYHeadRot(0); player.setOnGround(true); player.setDeltaMovement(Vec3.ZERO); player.connection.resetPosition();
        }
        void ready(Runnable action) {
            h.runAfterDelay(61, () -> { player.doTick(); place(); action.run(); }); // Real native login protection expires normally.
        }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(player, player.connection, player.serverLevel(), AbilityStorage.get(player)); }
        String wire(long input) { return new HighPowerInput.Input(0, input, player.getId(), epoch()).wire(); }
        BigDecimal hp() { return new BigDecimal(HighPowerCombat.healthText(player)); }
        boolean hit(boolean physical, long amount, Vec3 origin) {
            boolean previous = player.server.isPvpAllowed(); player.server.setPvpAllowed(true);
            try { return HighPowerCombat.attackRailThunderArray(caster, player, BigDecimal.valueOf(amount), physical, origin, () -> true); }
            finally { player.server.setPvpAllowed(previous); }
        }
        void close() {
            if (closed) return; closed = true;
            PotentialBuffer.remove(player); GroundingReturn.remove(player);
            AbilityStorage.remove(player); AbilityStorage.remove(caster); caster.discard();
            h.getLevel().getServer().getPlayerList().remove(player); channel.finishAndReleaseAll();
        }
    }

    public static final class Cancellation {
        final LivingEntity first, second; boolean active = true;
        Cancellation(LivingEntity first, LivingEntity second) { this.first = first; this.second = second; }
        @SubscribeEvent(priority = EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent event) {
            if (active && (event.getEntity() == first || event.getEntity() == second)) event.setCanceled(true);
        }
    }
    public static final class StageMutation {
        final LivingEntity target; final int stage; final Runnable action; boolean called;
        StageMutation(LivingEntity target, int stage, Runnable action) { this.target = target; this.stage = stage; this.action = action; }
        private void run() { if (!called) { called = true; action.run(); } }
        @SubscribeEvent(priority = EventPriority.NORMAL) public void skill(SkillAttackEvent event) { if (stage == 0 && event.target == target) run(); }
        @SubscribeEvent(priority = EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent event) { if (stage == 1 && event.getEntity() == target) run(); }
        @SubscribeEvent(priority = EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre event) { if (stage == 2 && event.getEntity() == target) run(); }
    }

    private static boolean rail(ServerPlayer caster, LivingEntity target, BigDecimal raw, Vec3 origin) {
        return HighPowerCombat.attackRailThunderArray(caster, target, raw, true, origin, () -> true);
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
        float original, damage, armor, resistance, enchantments, absorption, blockedDamage, shieldDamage;
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
            absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION);
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
            ServerPlayer caster = new FakePlayer(h.getLevel(), new GameProfile(UUID.randomUUID(), "[HP-RailHit]"));
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
        boolean nativeHit(ServerPlayer caster, LivingEntity target, Vec3 origin, float amount, boolean physical) {
            return physical ? thrown(caster, target, origin, amount)
                    : target.hurt(new AbilityDamage.SkillDamageSource(caster, "electromaster.forked_arc", false), amount);
        }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
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
