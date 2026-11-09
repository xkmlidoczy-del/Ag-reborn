package cn.academy.port.gametest;

import cn.academy.port.AbilityDamage;
import cn.academy.port.AbilityStorage;
import cn.academy.port.AcademyCraft;
import cn.academy.port.AcademyGameplay;
import cn.academy.port.develop.DevelopmentController;
import cn.academy.port.develop.DevelopmentProcess;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.util.ReferenceCountUtil;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
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
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW412 uses real connected input, native hurt, native mitigation and the installed native turn seam. */
@GameTestHolder("academy_highpower_vector")
@PrefixGameTestTemplate(false)
public final class HighPowerVectorParryRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_vector_parry", ID = "vector_parry";
    private HighPowerVectorParryRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void front_sector_protects_inside_sixty_degrees_and_refuses_rear_and_outside(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = new ServerPlayer[4];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(5 + i * 7, 8, 1, true);
        f.ready(() -> {
            double[] angles = {0, 59.99, 60.01, 180};
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; double angle = Math.toRadians(angles[i]);
                Zombie attacker = f.zombie(p.position().add(Math.sin(angle) * 2, 0, Math.cos(angle) * 2));
                f.arm(p, "20"); BigDecimal health = hp(p);
                h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "real native contact " + angles[i]);
                eq(h, HighPowerCombat.shield(p), n(i < 2 ? "15" : "20"), "horizontal sector owns only approved frontal contacts");
                eq(h, hp(p), i < 2 ? health : health.subtract(n("5")), "rear and outside contacts keep ordinary exact HP damage");
                h.assertTrue(VectorParry.remainingHits(p) == (i < 2 ? 3 : 4), "only positive approved absorption consumes quota");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_armor_resistance_protection_and_absorption_run_once_before_partial_base_shield(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true), control = f.player(22, 8, 0, false);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2)), other = f.zombie(control.position().add(0, 0, 2));
            defenses(h, p); defenses(h, control); f.arm(p, "2");
            Observation hit = f.observe(p), nativeHit = f.observe(control); BigDecimal health = hp(p);
            h.assertTrue(f.hit(control, control.damageSources().mobAttack(other), 20), "paired native mitigation control accepted");
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 20), "real parry native hit accepted");
            h.assertTrue(hit.posts == 1 && hit.damage > 2 && hit.armor > 0 && hit.effects > 0 && hit.enchantments > 0
                    && hit.absorption == .5F, "every native reducer executes before the remaining shield budget");
            h.assertTrue(hit.damage == nativeHit.damage && hit.armor == nativeHit.armor && hit.effects == nativeHit.effects
                    && hit.enchantments == nativeHit.enchantments && hit.absorption == nativeHit.absorption, "parry never rescales native reductions");
            eq(h, HighPowerCombat.shield(p), BigDecimal.ZERO, "two existing base S are consumed exactly once");
            eq(h, hp(p), health.subtract(decimal(hit.damage).subtract(n("2"))), "only the post-mitigation remainder beyond S reaches HP");
            h.assertTrue(p.getAbsorptionAmount() == 0 && control.getAbsorptionAmount() == 0, "native absorption remains spent once");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void unknown_subclass_environment_skill_and_spoofed_sources_keep_ordinary_hp_damage(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = new ServerPlayer[7];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(6 + (i % 3) * 9, 6 + (i / 3) * 9, 1, true);
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; Zombie attacker = f.zombie(p.position().add(0, 0, 2)); f.arm(p, "20");
                DamageSource ordinary = p.damageSources().mobAttack(attacker);
                DamageSource source = switch (i) {
                    case 0 -> new DamageSource(ordinary.typeHolder(), attacker) {};
                    case 1 -> p.damageSources().magic();
                    case 2 -> p.damageSources().onFire();
                    case 3 -> new AbilityDamage.SkillDamageSource(p, "vecmanip.vector_parry", false);
                    case 4 -> new DamageSource(ordinary.typeHolder(), attacker, attacker, p.position());
                    case 5 -> new DamageSource(ordinary.typeHolder(), null, attacker);
                    default -> new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.SONIC_BOOM), attacker);
                };
                Observation hit = f.observe(p); BigDecimal health = hp(p); f.visuals(p);
                h.assertTrue(f.hit(p, source, 5), "unsupported intake still uses native hurt " + i);
                eq(h, HighPowerCombat.shield(p), n("20"), "closed provenance refuses source " + i);
                eq(h, hp(p), health.subtract(decimal(hit.damage)), "refused source retains exact ordinary HP path");
                h.assertTrue(VectorParry.remainingHits(p) == 4 && f.contactVisuals(p) == 0, "refused source grants no quota debit or contact FX");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void cancellation_zero_damage_and_native_absorption_do_not_debit_or_emit_contact(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = new ServerPlayer[3];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(6 + i * 9, 8, 1, true);
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; Zombie attacker = f.zombie(p.position().add(0, 0, 2)); f.arm(p, "20");
                if (i == 2) { p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 2)); p.setAbsorptionAmount(10); }
                else f.hook(new DamageGate(p, i));
                BigDecimal health = hp(p); f.visuals(p); f.hit(p, p.damageSources().mobAttack(attacker), 5);
                eq(h, HighPowerCombat.shield(p), n("20"), "no positive residual means no S debit");
                eq(h, hp(p), health, "native cancellation and absorption own their zero-damage result");
                h.assertTrue(VectorParry.remainingHits(p) == 4 && f.contactVisuals(p) == 0, "zero or canceled damage yields no contact receipt");
                h.assertTrue(AbilityStorage.get(p).exp(ID) == 0, "unproductive intake grants no mastery");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void full_native_held_shield_precedes_parry_and_preserves_quota_and_precision(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2));
            p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); p.startUsingItem(InteractionHand.OFF_HAND);
            h.runAfterDelay(7, () -> {
                f.place(p); h.assertTrue(p.isBlocking(), "real native held-shield delay elapsed"); f.arm(p, "20");
                Observation hit = f.observe(p); BigDecimal health = hp(p); f.visuals(p);
                h.assertFalse(f.hit(p, p.damageSources().mobAttack(attacker), 5), "native shield blocks the frontal hit");
                h.assertTrue(hit.blocked && hit.blockedDamage == 5, "real LivingShieldBlockEvent owns the full block");
                eq(h, hp(p), health, "native block preserves HP"); eq(h, HighPowerCombat.shield(p), n("20"), "native block preserves S");
                h.assertTrue(VectorParry.remainingHits(p) == 4 && VectorParry.precisionAvailable(p) && f.contactVisuals(p) == 0,
                        "native block consumes neither hit quota nor precise turn and emits no contact"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void native_iframes_allow_only_the_damage_difference_and_never_invent_a_second_impulse(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2)); f.arm(p, "20"); Observation hit = f.observe(p);
            DamageSource source = p.damageSources().mobAttack(attacker); BigDecimal health = hp(p);
            h.assertTrue(f.hit(p, source, 5), "initial native hurt accepts"); Vec3 firstMotion = p.getDeltaMovement();
            h.assertFalse(f.hit(p, source, 5), "equal hit is refused by native hurt cooldown");
            h.assertTrue(f.hit(p, source, 8), "stronger hit applies only its native differential");
            eq(h, HighPowerCombat.shield(p), n("12"), "five plus native difference three consumes eight S");
            eq(h, hp(p), health, "existing S protects both accepted residuals");
            vector(h, p.getDeltaMovement(), firstMotion, "iframe differential cannot manufacture another native impulse");
            h.assertTrue(hit.posts == 2 && hit.knockbacks == 1 && VectorParry.remainingHits(p) == 2, "native impulse cadence and positive-hit quota stay independent");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void four_positive_hits_per_tick_then_native_hp_and_quota_survives_nbt_reload(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 5, true);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2)); f.arm(p, "100"); BigDecimal health = hp(p);
            for (int i = 0; i < 5; i++) {
                p.invulnerableTime = 0;
                h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "fresh native hurt " + i);
            }
            eq(h, HighPowerCombat.shield(p), n("80"), "only four positive hits may spend S in one real server tick");
            eq(h, hp(p), health.subtract(n("5")), "fifth accepted native hit keeps its ordinary HP path");
            h.assertTrue(VectorParry.remainingHits(p) == 0, "positive-hit quota is exhausted");
            CompoundTag quota = p.getPersistentData().getCompound(VectorParry.QUOTA_KEY).copy();
            h.assertFalse(quota.isEmpty(), "physical quota is persisted before any reload");
            double cp = HighPowerResources.cp(p); f.reload(p);
            h.assertTrue(quota.equals(p.getPersistentData().getCompound(VectorParry.QUOTA_KEY)), "native NBT reload retains the physical quota fence");
            h.assertFalse(f.start(p), "new input epoch cannot erase same-tick stop and payment fences");
            near(h, HighPowerResources.cp(p), cp, "refused reload restart never pays twice");
            h.runAfterDelay(1, () -> {
                h.assertTrue(VectorParry.remainingHits(p) == 4, "only the next actual server tick replenishes hit quota");
                eq(h, HighPowerCombat.shield(p), n("80"), "physical quota renewal never refills stored S"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void activation_and_twenty_tick_sustain_use_the_five_approved_cost_anchors(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = new ServerPlayer[5];
        double[] activation = {2, 1, .4, .1, .02}, sustain = {.25, .125, .05, .0125, .0025};
        int[] cooldown = {10, 5, 4, 4, 4}; int[] renewals = new int[5];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(4 + i * 5, 8, i + 1, true);
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; double cp = HighPowerResources.cp(p); long now = p.level().getGameTime();
                h.assertTrue(f.start(p), "authenticated parry at level " + (i + 1));
                near(h, HighPowerResources.cp(p), cp - activation[i], "approved activation curve");
                h.assertTrue(p.getPersistentData().getCompound(VectorParry.PAYMENT_KEY).getLong("stopUntil") == now + 4,
                        "activation persists the independent four-tick physical restart fence");
                double paid = HighPowerResources.cp(p); VectorParry.tick(p); VectorParry.tick(p);
                near(h, HighPowerResources.cp(p), paid, "same-tick controller calls cannot duplicate payment");
                final int index = i;
                f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerTickEvent.Pre e) {
                    if (e.getServer() != p.server || !VectorParry.renewalPending(p)) return;
                    double prior = HighPowerResources.cp(p); long until = VectorParry.paidUntil(p); renewals[index]++;
                    VectorParry.tick(p); near(h, HighPowerResources.cp(p), prior - sustain[index], "exact twenty-tick sustain curve");
                    h.assertTrue(VectorParry.paidUntil(p) == until + 20, "renewal advances the same paid period by twenty ticks");
                    double once = HighPowerResources.cp(p); VectorParry.tick(p); near(h, HighPowerResources.cp(p), once, "renewal is idempotent");
                } });
            }
            h.runAfterDelay(22, () -> {
                for (int i = 0; i < owners.length; i++) {
                    ServerPlayer p = owners[i];
                    h.assertTrue(renewals[i] == 1 && VectorParry.active(p), "one paid renewal leaves the held controller active");
                    h.assertTrue(f.release(p), "matching held input releases level " + (i + 1));
                    h.assertTrue(AbilityStorage.get(p).cooldowns.getOrDefault(ID, 0) == cooldown[i], "release uses approved cooldown anchors with four-tick minimum");
                    h.assertTrue(p.getPersistentData().getCompound(VectorParry.PAYMENT_KEY).getLong("stopUntil") == p.level().getGameTime() + cooldown[i],
                            "release records the same cooldown in the persistent physical fence");
                }
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void release_stops_protection_without_refill_or_touching_other_skill_cooldowns(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 5, true);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2));
            p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putLong("next_deflection_strike", p.level().getGameTime() + 123);
            f.arm(p, "20"); double cp = HighPowerResources.cp(p); BigDecimal health = hp(p);
            long other = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_deflection_strike");
            h.assertTrue(f.release(p), "authenticated release terminates the held controller");
            h.assertFalse(VectorParry.active(p), "release immediately ends protection");
            h.assertFalse(f.start(p), "release cannot erase four-tick minimum or mint another precise window");
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "native damage still works after release");
            eq(h, hp(p), health.subtract(n("5")), "released defense cannot spend base S");
            eq(h, HighPowerCombat.shield(p), n("20"), "release does not alter stored base S");
            near(h, HighPowerResources.cp(p), cp, "release and refused restart do not refund or charge CP");
            h.assertTrue(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getLong("next_deflection_strike") == other, "parry never rewrites another skill's cooldown");
            CompoundTag ledger = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).copy();
            AbilityStorage.get(p).changeCategoryClassic(HighPowerCategories.ELECTROMASTER);
            h.assertTrue(ledger.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)), "category change preserves the one existing ledger");
            eq(h, HighPowerCombat.shield(p), n("20"), "category change cannot refill the common shield"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch="highpower_vector_parry_category_interaction",timeoutTicks=100)
    public static void full_remaining_base_shield_keeps_superarmor_and_does_not_spend_a_precise_turn(GameTestHelper h) {
        forceCategoryTraits(h); Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true), control = f.player(22, 8, 1, true);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2)); f.arm(p, "20"); f.visuals(p);
            control.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", "20");
            Zombie other = f.zombie(control.position().add(0, 0, 2));
            h.assertTrue(f.hit(control, control.damageSources().mobAttack(other), 5), "same native hit without casting supplies real category baseline");
            eq(h, HighPowerCombat.shield(control), n("15"), "no-cast category shield spends five S");
            BigDecimal health = hp(p); int quota = VectorParry.remainingHits(p);
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "real native melee settles behind full base shield");
            eq(h, HighPowerCombat.shield(p), n("16.25"), "actual casting saves25% S compared with native no-cast category baseline");
            eq(h, hp(p), health, "remaining shield prevents real HP loss");
            vector(h, p.getDeltaMovement(), Vec3.ZERO, "positive remaining S preserves superarmor instead of inventing a side impulse");
            h.assertTrue(VectorParry.remainingHits(p) == quota - 1 && VectorParry.precisionAvailable(p),
                    "positive absorption spends hit quota while canceled impulse does not spend precision");
            h.assertTrue(f.visuals(p).stream().noneMatch(v -> v.phase() == VectorParry.TURNED), "canceled native motion emits no TURNED receipt"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch="highpower_vector_parry_category_interaction",timeoutTicks=100)
    public static void category_breaking_hit_uses_real_native_precise_right_turn(GameTestHelper h) { categoryBreaking(h, false); }
    @GameTest(template=TEMPLATE,batch="highpower_vector_parry_category_interaction",timeoutTicks=100)
    public static void category_breaking_hit_uses_real_native_precise_crouching_left_turn(GameTestHelper h) { categoryBreaking(h, true); }
    private static void categoryBreaking(GameTestHelper h, boolean crouch) {
        forceCategoryTraits(h); Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true);
        ServerPlayer control = f.player(22, 8, 0, false);
        f.ready(() -> {
            p.setShiftKeyDown(crouch); Zombie attacker = f.zombie(p.position().add(0, 0, 2));
            Zombie nativeAttacker = f.zombie(control.position().add(0, 0, 2));
            h.assertTrue(f.hit(control, control.damageSources().mobAttack(nativeAttacker), 5), "independent native impulse control");
            Vec3 expected = control.getDeltaMovement(); f.arm(p, "1"); f.visuals(p); BigDecimal health = hp(p);
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "actual native breaking hit during true precise window");
            eq(h, HighPowerCombat.shield(p), BigDecimal.ZERO, "one existing S is completely spent before native impulse");
            eq(h, hp(p), health.subtract(n("2.75")), "twenty-five-percent reduced native remainder spends one S before2.75HP");
            Vec3 actual = p.getDeltaMovement(); near(h, actual.horizontalDistance(), expected.horizontalDistance(), "side turn conserves actual native horizontal impulse");
            near(h, actual.y, expected.y, "vertical native impulse is unchanged"); near(h, actual.z, 0, "frontal impulse is redirected sideways");
            h.assertTrue(crouch ? actual.x > 0 : actual.x < 0, "actual side follows the starting crouch state");
            h.assertTrue(!VectorParry.precisionAvailable(p) && f.visuals(p).stream().filter(v -> v.phase() == VectorParry.TURNED).count() == 1,
                    "one real native turn consumes one precise token and emits exactly one actual receipt"); h.succeed();
        });
    }
    private static void forceCategoryTraits(GameTestHelper h) {
        EsperLegacyBypass.force(true);
        h.testInfo.addListener(new GameTestListener() {
            boolean closed; private void close() { if (!closed) { closed = true; EsperLegacyBypass.force(false); } }
            public void testStructureLoaded(GameTestInfo info) {}
            public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
            public void testAddedForRerun(GameTestInfo oldInfo, GameTestInfo newInfo, GameTestRunner runner) { close(); }
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void precise_native_turn_rotates_only_existing_horizontal_impulse_and_crouch_selects_other_side(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = {f.player(6, 8, 1, true), f.player(16, 8, 1, true)};
        ServerPlayer control = f.player(26, 8, 0, false);
        f.ready(() -> {
            h.assertTrue(VectorParryNativeBridge.available(owners[0]), "reviewed NEW412 native seam is actually installed");
            Zombie nativeAttacker = f.zombie(control.position().add(0, 0, 2)); Observation nativeHit = f.observe(control);
            h.assertTrue(f.hit(control, control.damageSources().mobAttack(nativeAttacker), 5), "native impulse control accepted");
            Vec3 expected = control.getDeltaMovement();
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; p.setShiftKeyDown(i == 1); Zombie attacker = f.zombie(p.position().add(0, 0, 2));
                f.arm(p, "20"); Observation hit = f.observe(p); f.visuals(p);
                h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "real precision-window native contact");
                Vec3 actual = p.getDeltaMovement();
                near(h, actual.horizontalDistance(), expected.horizontalDistance(), "turn retains the native horizontal impulse magnitude");
                near(h, actual.y, expected.y, "turn preserves native vertical behavior");
                near(h, actual.z, 0, "frontal native knockback rotates sideways");
                h.assertTrue(i == 0 ? actual.x < 0 : actual.x > 0, "crouch selects the opposite signed side");
                h.assertTrue(hit.knockbacks == 1 && nativeHit.knockbacks == 1 && !VectorParry.precisionAvailable(p), "one genuine impulse spends the once-per-hold precise token");
                h.assertTrue(f.visuals(p).stream().filter(v -> v.phase() == VectorParry.TURNED).count() == 1, "exactly one TURNED receipt follows real movement");
                p.invulnerableTime = 0; p.setDeltaMovement(Vec3.ZERO);
                h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "second fresh native contact still absorbs");
                h.assertTrue(p.getDeltaMovement().z < 0 && Math.abs(p.getDeltaMovement().x) < 1e-6, "second hit keeps ordinary native direction");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=105)
    public static void precision_includes_age_two_but_age_three_is_absorption_only(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer early = f.player(8, 8, 1, true), late = f.player(22, 8, 1, true);
        f.ready(() -> {
            Zombie first = f.zombie(early.position().add(0, 0, 2)), second = f.zombie(late.position().add(0, 0, 2));
            f.arm(early, "20"); f.arm(late, "20"); long started = h.getLevel().getGameTime();
            h.runAfterDelay(2, () -> {
                f.place(early); h.assertTrue(h.getLevel().getGameTime() - started == 2, "contact really occurs at age two");
                h.assertTrue(f.hit(early, early.damageSources().mobAttack(first), 5), "age-two native hit accepts");
                h.assertTrue(early.getDeltaMovement().x < 0 && Math.abs(early.getDeltaMovement().z) < 1e-6, "age two uses the final precision tick");
                eq(h, HighPowerCombat.shield(early), n("15"), "age-two positive residual spends base S once");
            });
            h.runAfterDelay(3, () -> {
                f.place(late); h.assertTrue(h.getLevel().getGameTime() - started == 3, "contact really occurs at age three");
                h.assertTrue(f.hit(late, late.damageSources().mobAttack(second), 5), "age-three native hit accepts");
                h.assertTrue(late.getDeltaMovement().z < 0 && Math.abs(late.getDeltaMovement().x) < 1e-6, "age three preserves ordinary native direction");
                eq(h, HighPowerCombat.shield(late), n("15"), "expired precise window still permits held absorption"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void canceled_resisted_and_listener_modified_native_knockback_never_emit_a_turn(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = new ServerPlayer[3];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(6 + i * 9, 8, 1, true);
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; Zombie attacker = f.zombie(p.position().add(0, 0, 2)); f.arm(p, "20"); f.visuals(p);
                if (i == 1) p.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(1);
                else f.hook(new KnockbackGate(p, i));
                h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "native damage remains independent of knockback outcome");
                eq(h, HighPowerCombat.shield(p), n("15"), "positive native damage still consumes S");
                if (i < 2) vector(h, p.getDeltaMovement(), Vec3.ZERO, "canceled or fully resisted native motion stays zero");
                else h.assertTrue(p.getDeltaMovement().z > 0 && Math.abs(p.getDeltaMovement().x) < 1e-6, "listener's native direction remains authoritative");
                h.assertTrue(f.visuals(p).stream().noneMatch(v -> v.phase() == VectorParry.TURNED), "no genuine approved impulse means no turn FX");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void hurt_reentry_poisoning_and_unrelated_knockback_cannot_borrow_precise_turn(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2)); f.arm(p, "20"); f.visuals(p);
            p.knockback(.4, 0, 1);
            h.assertTrue(p.getDeltaMovement().z < 0 && Math.abs(p.getDeltaMovement().x) < 1e-6 && VectorParry.precisionAvailable(p),
                    "unrelated native knockback has no hurt receipt and cannot use precision");
            p.setDeltaMovement(Vec3.ZERO); boolean[] reentered = {false};
            f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent e) {
                if (e.getEntity() != p || reentered[0]) return; reentered[0] = true;
                h.assertFalse(f.hit(p, p.damageSources().mobAttack(attacker), 5), "same-strength reentrant hurt follows native iframe rejection");
            } });
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "outer native hit still completes");
            h.assertTrue(reentered[0], "real native knockback listener ran the nested hurt");
            eq(h, HighPowerCombat.shield(p), n("15"), "outer damage is spent once before knockback reentry");
            h.assertTrue(p.getDeltaMovement().z < 0 && Math.abs(p.getDeltaMovement().x) < 1e-6, "nested hurt retires pending native turn authority");
            h.assertTrue(f.visuals(p).stream().noneMatch(v -> v.phase() == VectorParry.TURNED), "reentrant receipt cannot emit a turn"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void other_reviewed_melee_sources_absorb_without_borrowing_exact_zombie_precision(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = new ServerPlayer[5];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(4 + i * 5, 8, 1, true);
        ServerPlayer playerAttacker = f.player(14, 10, 0, false);
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; LivingEntity attacker;
                if (i == 0) {
                    Husk husk = new Husk(EntityType.HUSK, h.getLevel()); husk.setNoAi(true); husk.setNoGravity(true);
                    husk.setPos(p.position().add(0, 0, 2)); h.getLevel().addFreshEntity(husk); f.entities.add(husk); attacker = husk;
                } else if (i == 2) attacker = playerAttacker;
                else attacker = f.zombie(p.position().add(0, 0, 2));
                if (i == 3) attacker.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(1);
                if (i == 4) {
                    ItemStack weapon = new ItemStack(Items.IRON_SWORD);
                    weapon.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SHARPNESS), 1);
                    attacker.setItemSlot(EquipmentSlot.MAINHAND, weapon);
                }
                DamageSource source = i == 1
                        ? new DamageSource(h.getLevel().registryAccess().lookupOrThrow(Registries.DAMAGE_TYPE).getOrThrow(DamageTypes.MOB_ATTACK_NO_AGGRO), attacker)
                        : i == 2 ? p.damageSources().playerAttack(playerAttacker) : p.damageSources().mobAttack(attacker);
                f.arm(p, "20"); f.visuals(p); BigDecimal health = hp(p);
                h.assertTrue(f.hit(p, source, 5), "reviewed absorption-only native melee " + i);
                eq(h, HighPowerCombat.shield(p), n("15"), "reviewed melee consumes ordinary base S"); eq(h, hp(p), health, "reviewed positive residual stays protected");
                h.assertTrue(p.getDeltaMovement().z < 0 && Math.abs(p.getDeltaMovement().x) < 1e-6, "unreviewed precise source keeps its native direction");
                h.assertTrue(VectorParry.precisionAvailable(p) && f.visuals(p).stream().noneMatch(v -> v.phase() == VectorParry.TURNED),
                        "absorption-only source never spends or emits a precise turn");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void actual_plain_arrow_collision_preserves_native_damage_and_only_rotates_hurt_impulse(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true), control = f.player(22, 8, 0, false);
        f.ready(() -> {
            Zombie shooter = f.zombie(p.position().add(0, 0, 6)), nativeShooter = f.zombie(control.position().add(0, 0, 6));
            Arrow arrow = f.arrow(p, shooter, null), nativeArrow = f.arrow(control, nativeShooter, null);
            arrow.setDeltaMovement(0, 0, -3); nativeArrow.setDeltaMovement(0, 0, -3);
            f.arm(p, "20"); Observation hit = f.observe(p), nativeHit = f.observe(control); BigDecimal health = hp(p);
            nativeArrow.tick(); arrow.tick();
            h.assertTrue(arrow.isRemoved() && nativeArrow.isRemoved() && hit.posts == 1 && nativeHit.posts == 1,
                    "both real Arrow ticks collide, apply native damage and retire normally");
            h.assertTrue(hit.damage == nativeHit.damage && hit.damage > 0, "native arrow damage remains unchanged");
            eq(h, HighPowerCombat.shield(p), n("20").subtract(decimal(hit.damage)), "only actual post-native arrow remainder spends S");
            eq(h, hp(p), health, "plain arrow residual fits existing shield");
            Vec3 actual = p.getDeltaMovement(), expected = control.getDeltaMovement();
            near(h, actual.horizontalDistance(), expected.horizontalDistance(), "arrow turn adds no later projectile impulse");
            near(h, actual.y, expected.y, "plain arrow vertical motion stays native");
            h.assertTrue(actual.x < 0 && Math.abs(actual.z) < 1e-6 && expected.z < 0, "only the genuine hurt impulse changes its horizontal direction");
            h.assertTrue(hit.knockbacks == 1 && nativeHit.knockbacks == 1, "plain Arrow.doKnockback introduces no extra adapted call"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void critical_tipped_enchanted_and_non_native_gravity_arrows_fail_closed(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer[] owners = new ServerPlayer[4];
        for (int i = 0; i < owners.length; i++) owners[i] = f.player(5 + i * 7, 8, 1, true);
        f.ready(() -> {
            for (int i = 0; i < owners.length; i++) {
                ServerPlayer p = owners[i]; Zombie shooter = f.zombie(p.position().add(0, 0, 6)); ItemStack weapon = null;
                if (i == 2) {
                    weapon = new ItemStack(Items.BOW);
                    weapon.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PUNCH), 1);
                }
                Arrow arrow = f.arrow(p, shooter, weapon);
                if (i == 0) arrow.setCritArrow(true);
                if (i == 1) arrow.addEffect(new MobEffectInstance(MobEffects.POISON, 40));
                if (i == 3) arrow.setNoGravity(true);
                f.arm(p, "20"); f.visuals(p); BigDecimal health = hp(p);
                h.assertTrue(f.hit(p, p.damageSources().arrow(arrow, shooter), 5), "unreviewed arrow retains ordinary native hurt " + i);
                eq(h, HighPowerCombat.shield(p), n("20"), "unreviewed arrow cannot borrow the kinetic whitelist");
                eq(h, hp(p), health.subtract(n("5")), "refused arrow damage keeps native HP path");
                h.assertTrue(VectorParry.remainingHits(p) == 4 && f.contactVisuals(p) == 0, "refused arrow spends no hit quota and grants no FX");
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void nested_incoming_hurt_rejects_both_parry_receipts_and_matches_native_hp_control(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true), control = f.player(22, 8, 1, false);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2)), other = f.zombie(control.position().add(0, 0, 2));
            f.arm(p, "20"); BigDecimal health = hp(p), controlHealth = hp(control); boolean[] entered = new boolean[2];
            f.hook(new Object() { @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) {
                int index = e.getEntity() == p ? 0 : e.getEntity() == control ? 1 : -1;
                if (index < 0 || entered[index]) return; entered[index] = true;
                ServerPlayer target = index == 0 ? p : control; f.hit(target, target.damageSources().magic(), 1);
            } });
            f.visuals(p); f.hit(control, control.damageSources().mobAttack(other), 5); f.hit(p, p.damageSources().mobAttack(attacker), 5);
            h.assertTrue(entered[0] && entered[1], "both actual native incoming callbacks nested hurt");
            eq(h, HighPowerCombat.shield(p), n("20"), "reentrant damage cannot commit an outer or inner parry receipt");
            eq(h, health.subtract(hp(p)), controlHealth.subtract(hp(control)), "rejected parry follows the same exact native HP result as control");
            h.assertTrue(VectorParry.remainingHits(p) == 4 && f.contactVisuals(p) == 0, "poisoned frame yields no quota debit or contact FX"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void learning_never_refills_and_only_inactive_learned_parry_gets_existing_sixty_tick_recovery(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, false), held = f.player(22, 8, 1, true);
        f.ready(() -> {
            long now = h.getLevel().getGameTime(); CompoundTag data = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            data.putString("shield", "1"); data.putLong("lastShieldSpend", now - 60); data.putLong("lastShieldTick", now - 1);
            HighPowerCombat.tickShield(p); eq(h, HighPowerCombat.shield(p), BigDecimal.ONE, "unlearned Vector root receives no parry shield recovery");
            double cp = HighPowerResources.cp(p); AbilityStorage.get(p).learn(ID);
            eq(h, HighPowerCombat.shield(p), BigDecimal.ONE, "learning only unlocks eligibility and never refills S");
            near(h, HighPowerResources.cp(p), cp, "learning cannot refill the existing CP ledger");
            data.putLong("lastShieldSpend", now - 59); data.putLong("lastShieldTick", now - 1);
            HighPowerCombat.tickShield(p); eq(h, HighPowerCombat.shield(p), BigDecimal.ONE, "existing recovery still waits all sixty inactive ticks");
            data.putLong("lastShieldSpend", now - 60); data.putLong("lastShieldTick", now - 1);
            BigDecimal onePercent = n(HighPowerCombat.maxShieldText(p)).divide(n("100"));
            HighPowerCombat.tickShield(p); eq(h, HighPowerCombat.shield(p), BigDecimal.ONE.add(onePercent), "one existing recovery pass restores exactly one percent base capacity");
            HighPowerCombat.tickShield(p); eq(h, HighPowerCombat.shield(p), BigDecimal.ONE.add(onePercent), "global lastShieldTick prevents a second recovery pass");
            f.arm(held, "1"); CompoundTag active = held.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            active.putLong("lastShieldSpend", now - 60); active.putLong("lastShieldTick", now - 1);
            HighPowerCombat.tickShield(held); eq(h, HighPowerCombat.shield(held), BigDecimal.ONE, "held parry blocks the shared recovery budget"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=140)
    public static void player_post_tick_recovers_once_after_sixty_real_ticks_and_rejoin_preserves_the_wait(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true);
        f.ready(() -> {
            BigDecimal maximum = n(HighPowerCombat.maxShieldText(p)), budget = maximum.divide(n("100"));
            f.arm(p, maximum.divide(BigDecimal.TWO).toPlainString());
            Zombie attacker = f.zombie(p.position().add(0, 0, 2)); BigDecimal health = hp(p);
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "real native parry absorption starts the existing shield-spend clock");
            long spent = h.getLevel().getGameTime(); BigDecimal before = HighPowerCombat.shield(p);
            h.assertTrue(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getLong("lastShieldSpend") == spent,
                    "native committed absorption stamped the actual world tick");
            h.assertTrue(f.release(p), "real held-input release leaves learned parry inactive");
            AbilityStorage.get(p).setActivateState(false);
            CompoundTag reserve = savedReserve(p);
            f.postTick(p); eq(h, HighPowerCombat.shield(p), before, "actual player-post handler cannot recover on the spend tick");
            f.reload(p); f.postTick(p);
            eq(h, HighPowerCombat.shield(p), before, "native save/load and a fresh owner epoch cannot skip the quiet period");
            eq(h, hp(p), health, "rejoin and inactive player ticks do not mint or remove HP");
            h.assertTrue(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getLong("lastShieldSpend") == spent,
                    "rejoin retains the original spend timestamp");
            h.runAfterDelay(59, () -> {
                h.assertTrue(h.getLevel().getGameTime() - spent == 59, "fifty-nine real native world ticks elapsed");
                f.postTick(p); eq(h, HighPowerCombat.shield(p), before, "actual gameplay handler preserves all sixty quiet ticks");
            });
            h.runAfterDelay(60, () -> {
                h.assertTrue(h.getLevel().getGameTime() - spent == 60, "the next check is exactly the sixtieth real world tick");
                f.postTick(p); eq(h, HighPowerCombat.shield(p), before.add(budget), "actual PlayerTick.Post routes one existing one-percent base-S budget");
                f.postTick(p); eq(h, HighPowerCombat.shield(p), before.add(budget), "duplicate gameplay callbacks in one tick never double the shared budget");
                eq(h, hp(p), health, "shield recovery never changes exact HP");
                h.assertTrue(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY).equals(reserve),
                        "inactive Vector recovery leaves both saved reserve shares byte-for-byte unchanged");
                eq(h, MagneticDomainReserve.reserve(p), BigDecimal.ZERO, "Vector recovery never grants access to old reserve defenses"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void player_post_tick_blocks_held_recovery_then_credits_only_the_next_inactive_tick(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true);
        f.ready(() -> {
            BigDecimal before = n(HighPowerCombat.maxShieldText(p)).divide(BigDecimal.TWO);
            BigDecimal budget = n(HighPowerCombat.maxShieldText(p)).divide(n("100")); f.arm(p, before.toPlainString());
            long now = h.getLevel().getGameTime(); CompoundTag data = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            data.putLong("lastShieldSpend", now - 60); data.putLong("lastShieldTick", now - 1);
            h.assertFalse(data.contains(MagneticDomainReserve.KEY), "fixture begins without a saved reserve");
            f.postTick(p); f.postTick(p);
            h.assertTrue(VectorParry.active(p), "actual player-post callbacks preserve the authenticated held controller");
            eq(h, HighPowerCombat.shield(p), before, "held parry blocks recovery even when the stored quiet delay has elapsed");
            h.assertTrue(f.release(p), "matching input releases the held defense");
            f.postTick(p); eq(h, HighPowerCombat.shield(p), before, "same-tick release cannot claim a second global recovery evaluation");
            h.runAfterDelay(1, () -> {
                f.postTick(p); eq(h, HighPowerCombat.shield(p), before.add(budget), "next inactive gameplay tick receives exactly one base budget");
                f.postTick(p); eq(h, HighPowerCombat.shield(p), before.add(budget), "repeated post callback cannot add another percent");
                h.assertFalse(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).contains(MagneticDomainReserve.KEY),
                        "held or inactive Vector ticks never create a reserve ledger"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void player_post_tick_grants_no_parry_recovery_to_root_only_or_switched_categories(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer rootOnly = f.player(8, 8, 1, false), switched = f.player(22, 8, 1, true);
        f.ready(() -> {
            long now = h.getLevel().getGameTime();
            for (ServerPlayer p : List.of(rootOnly, switched)) {
                CompoundTag data = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
                data.putString("shield", "1"); data.putLong("lastShieldSpend", now - 60); data.putLong("lastShieldTick", now - 1);
            }
            f.postTick(rootOnly); eq(h, HighPowerCombat.shield(rootOnly), BigDecimal.ONE, "genuine NEW411-only owner gets no recovery through gameplay ticks");
            var state = AbilityStorage.get(switched); state.changeCategoryClassic(HighPowerCategories.TELEPORTER);
            h.assertFalse(state.learned(ID), "ordinary native category change clears learned parry");
            f.postTick(switched); eq(h, HighPowerCombat.shield(switched), BigDecimal.ONE, "unlearned new category cannot retain old parry recovery");
            state.changeCategoryClassic(HighPowerCategories.VECMANIP); state.learn(HighPowerSkills.DEFLECTION_STRIKE);
            f.postTick(switched); eq(h, HighPowerCombat.shield(switched), BigDecimal.ONE, "returning to Vector with only the root cannot restore parry benefits");
            h.runAfterDelay(1, () -> {
                f.postTick(rootOnly); f.postTick(switched);
                eq(h, HighPowerCombat.shield(rootOnly), BigDecimal.ONE, "root-only recovery remains suspended on a later real tick");
                eq(h, HighPowerCombat.shield(switched), BigDecimal.ONE, "category swap cannot exploit a later tick to refill S");
                for (ServerPlayer p : List.of(rootOnly, switched)) h.assertFalse(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).contains(MagneticDomainReserve.KEY),
                        "ineligible category ticks never create a reserve"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void player_post_tick_cannot_regenerate_a_genuinely_dead_parry_owner(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true);
        f.ready(() -> {
            CompoundTag data = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            data.putString("health", "1"); data.putString("shield", "1");
            h.assertTrue(f.hit(p, p.damageSources().magic(), 5), "real native lethal damage completes through the ordinary exact HP path");
            h.assertFalse(p.isAlive(), "fixture is genuinely dead before any gameplay tick"); eq(h, hp(p), BigDecimal.ZERO, "native death leaves zero exact HP");
            data = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            data.putLong("lastShieldSpend", h.getLevel().getGameTime() - 60); data.putLong("lastShieldTick", h.getLevel().getGameTime() - 1);
            BigDecimal shield = HighPowerCombat.shield(p); CompoundTag before = data.copy();
            f.postTick(p); f.postTick(p);
            eq(h, hp(p), BigDecimal.ZERO, "dead player callbacks cannot restore exact HP");
            eq(h, HighPowerCombat.shield(p), shield, "dead player callbacks cannot mint base S after the quiet delay");
            h.assertTrue(before.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)), "dead player tick leaves the exact resource ledger unchanged"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void finite_native_developer_completion_preserves_existing_hp_shield_reserve_cp_and_growth(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, false);
        f.ready(() -> {
            var state = AbilityStorage.get(p);
            h.assertTrue(f.hit(p, p.damageSources().magic(), 1), "existing owner has genuine native exact injury");
            h.assertFalse(HighPowerCombat.spendShield(p, n("7")), "Vector owner cannot use the old electromaster shield-spend entrance");
            p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", HighPowerCombat.shield(p).subtract(n("7")).toPlainString());
            CompoundTag budget = p.getPersistentData().getCompound(HighPowerResources.DATA_KEY); budget.putDouble("cp", 37);
            budget.putLong("next_deflection_strike", p.level().getGameTime() + 123);
            CompoundTag reserve = new CompoundTag(); reserve.putInt("version", MagneticDomainReserve.VERSION);
            reserve.putString("amount", "9"); reserve.putString("capacity", HighPowerCombat.maxShieldText(p)); reserve.putString("lower", "4"); reserve.putString("upper", "5");
            p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).put(MagneticDomainReserve.KEY, reserve);
            CompoundTag combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).copy(), resources = budget.copy();
            BigDecimal q = HighPowerGrowth.values(p).position(), earned = HighPowerCombat.earnedGrowthPosition(p); double xp = state.levelExperience;
            ItemStack developer = new ItemStack(AcademyCraft.DEVELOPER.get()); p.setItemInHand(InteractionHand.MAIN_HAND, developer);
            DeveloperItemEnergy battery = new DeveloperItemEnergy(developer, DeveloperType.PORTABLE); battery.energy(2340);
            h.assertTrue(DevelopmentController.startSkill(p, ID), "learned Deflection Strike enables real finite NEW412 development");
            for (int i = 0; i < 77; i++) DevelopmentController.tick(p, ignored -> {});
            h.assertTrue(!state.learned(ID) && battery.energy() == 30, "all but final native paid development tick grants nothing");
            h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)) && resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),
                    "unfinished development leaves both existing ledgers unchanged");
            DevelopmentController.tick(p, ignored -> {});
            h.assertTrue(state.learned(ID) && battery.energy() == 0 && DevelopmentController.process(p).state() == DevelopmentProcess.State.DONE,
                    "exactly seventy-eight native paid ticks complete NEW412 for 2340 IF");
            h.assertTrue(combat.equals(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY)) && resources.equals(p.getPersistentData().getCompound(HighPowerResources.DATA_KEY)),
                    "completion preserves exact HP, base S, inaccessible reserve and shared high-power CP byte-for-byte");
            eq(h, HighPowerGrowth.values(p).position(), q, "learning never raises active q");
            eq(h, HighPowerCombat.earnedGrowthPosition(p), earned, "learning never certifies earned Q");
            h.assertTrue(state.exp(ID) == 0 && state.levelExperience == xp && MagneticDomainReserve.reserve(p).signum() == 0,
                    "learning grants no XP or access to old reserve defenses"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void positive_partial_absorption_can_turn_native_impulse_even_when_base_shield_is_exhausted(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2)); f.arm(p, "2"); BigDecimal health = hp(p); f.visuals(p);
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "partially shielded native hit accepts");
            eq(h, HighPowerCombat.shield(p), BigDecimal.ZERO, "only two existing S are spent");
            eq(h, hp(p), health.subtract(n("3")), "remaining three damage reaches exact HP once");
            h.assertTrue(p.getDeltaMovement().x < 0 && Math.abs(p.getDeltaMovement().z) < 1e-6,
                    "committed positive absorption retains its genuine native turn after spending the last S");
            h.assertTrue(f.visuals(p).stream().filter(v -> v.phase() == VectorParry.TURNED).count() == 1, "native movement evidence grants one turn receipt"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=100)
    public static void recursive_knockback_invalidates_outer_turn_and_preserves_native_control_momentum(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(8, 8, 1, true), control = f.player(22, 8, 0, false);
        f.ready(() -> {
            Zombie attacker = f.zombie(p.position().add(0, 0, 2)), other = f.zombie(control.position().add(0, 0, 2)); f.arm(p, "20"); f.visuals(p);
            Vec3[] nested = {null, null}; boolean[] entered = {false, false};
            f.hook(new Object() { @SubscribeEvent public void knockback(LivingKnockBackEvent e) {
                int index = e.getEntity() == p ? 0 : e.getEntity() == control ? 1 : -1;
                if (index < 0 || entered[index]) return; entered[index] = true;
                ServerPlayer target = index == 0 ? p : control; target.knockback(.2, 0, 1); nested[index] = target.getDeltaMovement();
            } });
            h.assertTrue(f.hit(control, control.damageSources().mobAttack(other), 5), "paired native recursive-knockback control accepts");
            h.assertTrue(f.hit(p, p.damageSources().mobAttack(attacker), 5), "outer authenticated native hurt accepts");
            h.assertTrue(nested[0] != null && nested[0].z < 0 && Math.abs(nested[0].x) < 1e-6, "recursive knockback retains its own ordinary native direction");
            vector(h, p.getDeltaMovement(), control.getDeltaMovement(), "both nested and outer force remain exactly native after reentry invalidates turn");
            eq(h, HighPowerCombat.shield(p), n("15"), "knockback reentry never refunds already settled damage absorption");
            h.assertTrue(f.visuals(p).stream().noneMatch(v -> v.phase() == VectorParry.TURNED),
                    "nested knockback retires the outer precise receipt without turn FX"); h.succeed();
        });
    }

    private static BigDecimal n(String text) { return new BigDecimal(text); }
    private static BigDecimal hp(LivingEntity e) { return n(HighPowerCombat.healthText(e)); }
    private static BigDecimal decimal(float value) { return new BigDecimal(Float.toString(value)); }
    /** Declared old-category reserve fixture; NEW412 must preserve it without making it accessible. */
    private static CompoundTag savedReserve(ServerPlayer p) {
        CompoundTag reserve = new CompoundTag(); reserve.putInt("version", MagneticDomainReserve.VERSION);
        reserve.putString("amount", "9"); reserve.putString("capacity", HighPowerCombat.maxShieldText(p)); reserve.putString("lower", "4"); reserve.putString("upper", "5");
        p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).put(MagneticDomainReserve.KEY, reserve); return reserve.copy();
    }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-5, why + ": " + actual + " != " + expected); }
    private static void vector(GameTestHelper h, Vec3 actual, Vec3 expected, String why) { h.assertTrue(actual.distanceToSqr(expected) < 1e-10, why + ": " + actual + " != " + expected); }
    private static void defenses(GameTestHelper h, LivingEntity p) {
        ItemStack helmet = new ItemStack(Items.DIAMOND_HELMET);
        helmet.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        helmet.set(DataComponents.UNBREAKABLE, new Unbreakable(true)); p.setItemSlot(EquipmentSlot.HEAD, helmet);
        p.getAttribute(Attributes.ARMOR).setBaseValue(5); p.getAttribute(Attributes.ARMOR_TOUGHNESS).setBaseValue(2);
        p.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100, 0));
        p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 100, 0)); p.setAbsorptionAmount(.5F);
    }
    public static final class DamageGate {
        final ServerPlayer owner; final int mode;
        DamageGate(ServerPlayer owner, int mode) { this.owner = owner; this.mode = mode; }
        @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == owner && mode == 0) e.setCanceled(true); }
        @SubscribeEvent public void pre(LivingDamageEvent.Pre e) { if (e.getEntity() == owner && mode == 1) e.setNewDamage(0); }
    }
    public static final class KnockbackGate {
        final ServerPlayer owner; final int mode;
        KnockbackGate(ServerPlayer owner, int mode) { this.owner = owner; this.mode = mode; }
        @SubscribeEvent public void knockback(LivingKnockBackEvent e) {
            if (e.getEntity() != owner) return;
            if (mode == 0) e.setCanceled(true); else e.setRatioZ(-e.getRatioZ());
        }
    }
    public static final class Observation {
        final LivingEntity owner; int posts, knockbacks; float damage, armor, effects, enchantments, absorption, blockedDamage; boolean blocked;
        Observation(LivingEntity owner) { this.owner = owner; }
        @SubscribeEvent public void shield(LivingShieldBlockEvent e) { if (e.getEntity() == owner) { blocked = e.getBlocked(); blockedDamage = e.getBlockedDamage(); } }
        @SubscribeEvent public void knockback(LivingKnockBackEvent e) { if (e.getEntity() == owner) knockbacks++; }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) {
            if (e.getEntity() != owner) return; posts++; damage = e.getNewDamage();
            armor = e.getReduction(DamageContainer.Reduction.ARMOR); effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS);
            enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<Entity> entities = new ArrayList<>();
        final List<Object> hooks = new ArrayList<>(); final Map<ServerPlayer, EmbeddedChannel> channels = new IdentityHashMap<>();
        final Map<ServerPlayer, Vec3> anchors = new IdentityHashMap<>(); final Map<ServerPlayer, Long> inputs = new IdentityHashMap<>();
        final Map<ServerPlayer, String> heldInputs = new IdentityHashMap<>(); boolean closed;
        Fixture(GameTestHelper h) {
            this.h = h;
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        ServerPlayer player(double x, double z, int level, boolean learned) {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "VP" + UUID.randomUUID().toString().substring(0, 10)), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection); channels.put(p, channel);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); drain(p);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); anchors.put(p, h.absoluteVec(new Vec3(x, 2, z))); place(p);
            if (level > 0) {
                h.assertTrue(HighPowerCombat.enroll(p, level), "real connected Vector owner enrollment");
                var s = AbilityStorage.get(p); s.selectCategory(HighPowerCategories.VECMANIP); s.setLevel(level);
                s.learn(HighPowerSkills.DEFLECTION_STRIKE); if (learned) s.learn(ID); s.activated = true; s.overloadFine = true;
                h.assertTrue(s.presets.edit(0, 0, learned ? ID : HighPowerSkills.DEFLECTION_STRIKE, s::learned), "genuine learned native key binding");
                HighPowerResources.initializeBudget(p);
            }
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, h.testInfo.getTimeoutTicks() + 5)); return p;
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { for (ServerPlayer p : players) { p.doTick(); place(p); } action.run(); }); }
        void postTick(ServerPlayer p) { AcademyGameplay.tick(new PlayerTickEvent.Post(p)); }
        void place(ServerPlayer p) {
            Vec3 anchor = anchors.get(p); p.moveTo(anchor.x, anchor.y, anchor.z, 0, 0); p.setYHeadRot(0); p.yBodyRot = 0;
            p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition();
        }
        Zombie zombie(Vec3 position) {
            Zombie mob = new Zombie(EntityType.ZOMBIE, h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true); mob.setPos(position);
            mob.getAttribute(Attributes.ARMOR).setBaseValue(0); mob.getAttribute(Attributes.ATTACK_KNOCKBACK).setBaseValue(0);
            h.assertTrue(h.getLevel().addFreshEntity(mob), "real exact-class native attacker registered"); entities.add(mob); return mob;
        }
        Arrow arrow(ServerPlayer target, LivingEntity shooter, ItemStack weapon) {
            Arrow arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), weapon);
            arrow.setPos(target.position().add(0, 1, 2)); arrow.setDeltaMovement(0, 0, -.5);
            h.assertTrue(h.getLevel().addFreshEntity(arrow), "real native arrow registered with cached shooter"); entities.add(arrow); return arrow;
        }
        String wire(ServerPlayer p) {
            long next = inputs.getOrDefault(p, 0L) + 1; inputs.put(p, next);
            return new HighPowerInput.Input(0, next, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire();
        }
        boolean start(ServerPlayer p) { String wire = wire(p); boolean result = VectorParry.start(p, wire); if (result) heldInputs.put(p, wire); return result; }
        boolean release(ServerPlayer p) { return VectorParry.release(p, heldInputs.get(p)); }
        void arm(ServerPlayer p, String shield) { p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", shield); h.assertTrue(start(p), "authenticated paid held parry"); }
        boolean hit(ServerPlayer p, DamageSource source, float amount) {
            boolean prior = p.server.isPvpAllowed(); p.server.setPvpAllowed(true);
            try { return p.hurt(source, amount); } finally { p.server.setPvpAllowed(prior); }
        }
        void reload(ServerPlayer p) {
            AbilityStorage.save(p); CompoundTag saved = p.saveWithoutId(new CompoundTag());
            VectorParry.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.load(saved); place(p); inputs.put(p, 0L);
        }
        Observation observe(LivingEntity e) { Observation result = new Observation(e); hook(result); return result; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        List<Object> drain(ServerPlayer p) {
            EmbeddedChannel channel = channels.get(p); channel.runPendingTasks(); channel.flushOutbound(); channel.runPendingTasks();
            List<Object> packets = new ArrayList<>(); Object packet;
            while ((packet = channel.readOutbound()) != null) {
                if (packet instanceof ClientboundPlayerPositionPacket position) p.connection.handleAcceptTeleportPacket(new ServerboundAcceptTeleportationPacket(position.getId()));
                packets.add(packet); ReferenceCountUtil.release(packet);
            }
            return packets;
        }
        List<VectorParry.Visual> visuals(ServerPlayer p) {
            List<VectorParry.Visual> result = new ArrayList<>();
            for (Object packet : drain(p)) if (packet instanceof ClientboundCustomPayloadPacket custom && custom.payload() instanceof VectorParry.Visual visual && visual.owner().equals(p.getUUID())) result.add(visual);
            return result;
        }
        long contactVisuals(ServerPlayer p) { return visuals(p).stream().filter(v -> v.phase() == VectorParry.ABSORBED || v.phase() == VectorParry.TURNED).count(); }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer p : players) { VectorParry.remove(p); DevelopmentController.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p); p.server.getPlayerList().remove(p); }
            entities.forEach(Entity::discard); channels.values().forEach(EmbeddedChannel::finishAndReleaseAll);
        }
    }
}
