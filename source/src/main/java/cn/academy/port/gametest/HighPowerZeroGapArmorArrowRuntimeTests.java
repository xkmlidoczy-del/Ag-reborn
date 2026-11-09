package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.damagesource.DamageContainer;
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static cn.academy.port.gametest.ZeroGapArmorNativeFixture.*;

/** Real native252 flight/body settlement. No fabricated arrow source substitutes for native impact.
 * Resource seeds and accelerated individual Arrow.tick invocations are explicitly identified.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerZeroGapArmorArrowRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_zero_gap_arrows";

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void actual_native_body_damage_stays_unprotected_for_all_six_startup_ticks(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0);
        h.runAfterDelay(61, () -> {
            f.start(); BigDecimal shield = f.shield(), health = f.hp(); f.frontArrow(8, 8).tick();
            eq(h, f.shield(), shield, "paid startup cannot absorb the first real native arrow");
            eq(h, f.hp(), health.subtract(new BigDecimal("16")), "the first startup hit reaches exact HP");
            h.runAfterDelay(5, () -> {
                h.assertFalse(ZeroGapArmor.active(f.p), "fifth native tick is still startup"); f.frontArrow(8, 12).tick();
                eq(h, f.shield(), shield, "the last unprotected tick cannot spend252resources");
                eq(h, f.hp(), health.subtract(new BigDecimal("24")), "native stronger-hit differential remains ordinary during startup");
            });
            h.runAfterDelay(6, () -> {
                h.assertTrue(ZeroGapArmor.active(f.p), "sixth real tick activates the finite plates"); f.frontArrow(8, 16).tick();
                eq(h, f.shield(), shield.subtract(new BigDecimal("8")), "only the newly accepted native differential after activation is protected");
                eq(h, f.hp(), health.subtract(new BigDecimal("24")), "protection starts at the genuine sixth boundary"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void real_native_body_hit_debits_one_contacted_child_at_one_to_one_and_preserves_arrow(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0); var control = new ZeroGapArmorNativeFixture(h, 14);
        ready(h, () -> {
            f.seed(copies("100"), copies("10")); var before = f.armor(); BigDecimal health = f.hp(), nativeHealth = control.hp(); double cp = f.cp();
            var plate = f.front(); Vec3 normal = vec(plate.normal());
            Arrow arrow = f.frontArrow(8, 8), ordinary = control.arrow(control.center().add(normal.scale(8)), normal.scale(-8), null);
            arrow.tick(); ordinary.tick(); BigDecimal damage = nativeHealth.subtract(control.hp());
            h.assertTrue(damage.signum() > 0, "paired real arrow actually damages the ordinary target");
            eq(h, f.shield(), before.base().shield().subtract(damage), "252 pays exactly the genuine post-native damage at1:1");
            eq(h, f.armor().plate(plate.id()).baseCurrent(), before.plate(plate.id()).baseCurrent().subtract(damage), "only the physically contacted child pays");
            for (int i = 0; i < 6; i++) {
                eq(h, f.armor().plate(i).reserveCurrent(), before.plate(i).reserveCurrent(), "sufficient contacted base leaves reserve child " + i);
                if (i != plate.id()) eq(h, f.armor().plate(i).baseCurrent(), before.plate(i).baseCurrent(), "uncontacted base child cannot pay " + i);
            }
            eq(h, f.hp(), health, "authenticated finite contact protects HP"); near(h, f.cp(), cp, "native hit adds no CP charge");
            h.assertTrue(arrow.isRemoved() == ordinary.isRemoved() && arrow.getDeltaMovement().equals(ordinary.getDeltaMovement())
                    && arrow.pickup == ordinary.pickup && arrow.getOwner() == f.shooter && ordinary.getOwner() == control.shooter,
                    "native removal, velocity, pickup and owner attribution match the unshielded control");
            h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "body settlement consumes its crossing exactly once"); h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=95)
    public static void crossing_only_is_read_only_then_later_genuine_body_hurt_settles(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0);
        ready(h, () -> {
            var before = f.p.getPersistentData().copy(); BigDecimal shield = f.shield(), health = f.hp(); double xp = f.mastery();
            var arrow = f.frontArrow(1.6, .5); arrow.tick();
            h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 1 && !arrow.isRemoved(), "first native flight step crosses a plate before the body");
            h.assertTrue(f.p.getPersistentData().equals(before), "observation itself writes no resources, rotation, cooldown or mastery");
            eq(h, f.shield(), shield, "plate crossing alone cannot spend"); eq(h, f.hp(), health, "plate crossing never synthesizes early hurt"); near(h, f.mastery(), xp, "crossing alone earns no XP");
            h.runAfterDelay(4, () -> {
                h.assertTrue(arrow.isRemoved() && f.shield().compareTo(shield) < 0, "later real native body impact settles the still-active numbered plate");
                eq(h, f.hp(), health, "historical contact protects the accepted native body damage");
                h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "historical crossing cannot be reused"); h.succeed();
            });
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=105)
    public static void real_plate_graze_that_misses_body_never_spends_and_expires(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0);
        ready(h, () -> {
            BigDecimal shield = f.shield(), health = f.hp(); double xp = f.mastery();
            var graze = f.graze(); graze.tick();
            h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 1, "real diagonal graze crosses the finite rectangle");
            eq(h, f.shield(), shield, "finite contact is observation only");
            h.runAfterDelay(21, () -> {
                eq(h, f.shield(), shield, "missed native body never consumes a parent or child"); eq(h, f.hp(), health, "graze never causes synthetic body damage");
                near(h, f.mastery(), xp, "expired grazes cannot farm mastery");
                h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "bounded historical receipt expires"); h.succeed();
            });
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void actual_three_plate_centers_protect_but_gap_rear_inside_and_top_edge_remain_vulnerable(GameTestHelper h) {
        var all = fixtures(h, 7);
        ready(h, () -> {
            for (int i = 0; i < all.length; i++) {
                var f = all[i]; BigDecimal shield = f.shield(), health = f.hp(); var plate = f.front(); Vec3 normal = vec(plate.normal());
                if (i < 3) {
                    var selected = f.pose().plates().stream().filter(ZeroGapArmorGeometry.Plate::active).toList();
                    f.radial(selected.get(i), 8, 8, 0, null).tick();
                } else if (i == 3) {
                    double angle = Math.toRadians(ZeroGapArmorGeometry.plateYawDegrees(f.timing().phaseTick(), plate.id()) + 30);
                    Vec3 gap = new Vec3(-Math.sin(angle), 0, Math.cos(angle)); f.arrow(f.center().add(gap.scale(8)), gap.scale(-8), null).tick();
                } else if (i == 4) f.arrow(f.center().add(normal.scale(-8)), normal.scale(8), null).tick();
                else if (i == 5) f.frontArrow(.8, 8).tick();
                else f.arrow(f.center().add(0, .66, 0).add(normal.scale(8)), normal.scale(-8), null).tick();
                if (i < 3) { h.assertTrue(f.shield().compareTo(shield) < 0, "actual selected rectangle pays " + i); eq(h, f.hp(), health, "selected center protects " + i); }
                else { eq(h, f.shield(), shield, "physical vulnerability cannot spend a sibling " + i); h.assertTrue(f.hp().compareTo(health) < 0, "ordinary native body damage survives gap/rear/inside/top " + i); }
                h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "native body decision retires evidence " + i);
            }
            h.succeed();
        }, all);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void nearest_native_wall_body_and_ambiguous_body_tie_cannot_claim_armor(GameTestHelper h) {
        var all = fixtures(h, 3);
        ready(h, () -> {
            for (int i = 0; i < all.length; i++) {
                var f = all[i]; BigDecimal shield = f.shield(), health = f.hp(); Vec3 normal = vec(f.front().normal());
                if (i == 0) f.wall(f.center().add(normal.scale(4)));
                else f.mob(f.p.position().add(normal.scale(i == 1 ? 4 : 0)));
                f.frontArrow(8, 8).tick(); eq(h, f.shield(), shield, "nearest native obstruction/tie retains priority " + i);
                if (i < 2) eq(h, f.hp(), health, "nearer native obstruction prevents defender hurt " + i);
                h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "clipped or ambiguous target creates no usable crossing " + i);
            }
            h.succeed();
        }, all);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void canceled_zero_absorbed_and_native_iframe_damage_retire_without_debit_or_reward(GameTestHelper h) {
        var all = fixtures(h, 5);
        ready(h, () -> {
            for (int i = 0; i < all.length; i++) {
                var f = all[i]; final int mode = i;
                if (i < 3) f.hook(new Object() {
                    @SubscribeEvent public void incoming(LivingIncomingDamageEvent event) { if (event.getEntity() == f.p) { if (mode == 0) event.setCanceled(true); else if (mode == 1) event.setAmount(0); } }
                    @SubscribeEvent public void pre(LivingDamageEvent.Pre event) { if (event.getEntity() == f.p && mode == 2) event.setNewDamage(0); }
                });
                else if (i == 3) { f.p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 7)); f.p.setAbsorptionAmount(32); }
                else h.assertTrue(f.p.hurt(f.p.damageSources().mobAttack(f.shooter), 20), "genuine preceding native hurt establishes iframe");
                BigDecimal shield = f.shield(), health = f.hp(); double xp = f.mastery(); f.frontArrow(8, 8).tick();
                eq(h, f.shield(), shield, "native failed or fully mitigated hurt spends no252resource " + i); eq(h, f.hp(), health, "native cancellation/mitigation remains authoritative " + i);
                near(h, f.mastery(), xp, "zero absorbed damage cannot reward " + i);
                h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "each native decision consumes its crossing " + i);
                if (i == 3) h.assertTrue(f.p.getAbsorptionAmount() == 16, "ordinary absorption alone pays the sixteen-point hit once");
            }
            h.succeed();
        }, all);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void owner_base_weapon_critical_and_competing_lease_changes_revoke_historical_source(GameTestHelper h) {
        var all = fixtures(h, 5);
        ready(h, () -> {
            for (int i = 0; i < all.length; i++) {
                var f = all[i]; BigDecimal shield = f.shield(); var arrow = f.radial(f.front(), 1.6, .5, 0, new ItemStack(Items.BOW)); arrow.tick();
                h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 1, "genuine bow-arrow crossing exists before mutation " + i);
                mutate(f, arrow, i); ZeroGapArmorArrowReceipts.prune(f.p.server);
                h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "source/owner change revokes252evidence " + i);
                eq(h, f.shield(), shield, "mutation alone cannot spend");
            }
            h.succeed();
        }, all);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void incoming_and_pre_source_mutations_revoke_even_already_claimed_native_operand(GameTestHelper h) {
        var all = fixtures(h, 10);
        ready(h, () -> {
            for (int i = 0; i < all.length; i++) {
                var f = all[i]; BigDecimal shield = f.shield(), health = f.hp(); double xp = f.mastery();
                var arrow = f.radial(f.front(), 8, 8, 0, new ItemStack(Items.BOW)); final int mutation = i % 5; final boolean pre = i >= 5; boolean[] changed = {false};
                Runnable change = () -> { if (!changed[0]) { changed[0] = true; mutate(f, arrow, mutation); } };
                f.hook(new Object() {
                    @SubscribeEvent(priority=EventPriority.LOW) public void incoming(LivingIncomingDamageEvent event) { if (!pre && event.getEntity() == f.p && event.getSource().getDirectEntity() == arrow) change.run(); }
                    @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre event) { if (pre && event.getEntity() == f.p && event.getSource().getDirectEntity() == arrow) change.run(); }
                });
                arrow.tick(); h.assertTrue(changed[0], "actual native callback mutates the claimed source " + i);
                eq(h, f.shield(), shield, "final identity recheck refuses stale component debit " + i);
                eq(h, f.hp(), health.subtract(new BigDecimal("16")), "ordinary precomputed native damage still settles once " + i);
                near(h, f.mastery(), xp, "revoked source earns no armor mastery " + i);
                h.assertTrue(arrow.isRemoved(), "native successful-hit disposition remains authoritative " + i);
            }
            h.succeed();
        }, all);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=95)
    public static void current_selected_numbered_plate_is_required_when_historical_contact_reaches_body(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0);
        ready(h, () -> {
            BigDecimal shield = f.shield(), health = f.hp(); var arrow = f.frontArrow(1.6, .5); arrow.tick();
            h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 1, "historical physical crossing exists");
            f.p.setYRot(180); ZeroGapArmorArrowReceipts.prune(f.p.server);
            h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "facing away revokes the now-unselected numbered plate");
            h.runAfterDelay(4, () -> {
                h.assertTrue(arrow.isRemoved() && f.hp().compareTo(health) < 0, "later genuine native body hit remains vulnerable after current-selection change");
                eq(h, f.shield(), shield, "historical contact cannot borrow another currently active plate"); h.succeed();
            });
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=95)
    public static void real_rotation_can_deselect_historical_plate_before_body_without_any_look_change(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0);
        ready(h, () -> {
            var front = f.front(); double angle = ZeroGapArmorGeometry.plateYawDegrees(f.timing().phaseTick(), front.id());
            f.p.setYRot((float)(angle - 89));
            var edge = f.pose().plates().get(front.id());
            h.assertTrue(edge.active(), "fixture places this numbered plate just inside the real facing-selection boundary");
            BigDecimal shield = f.shield(), health = f.hp(); float fixedYaw = f.p.getYRot(); int initialPhase = f.timing().phaseTick();
            var arrow = f.radial(edge, 1.6, .5, 0, null); arrow.tick();
            h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 1, "native flight records an actually selected edge plate");
            h.runAfterDelay(1, () -> {
                h.assertTrue(f.timing().phaseTick() == (initialPhase + 1) % 120 && f.p.getYRot() == fixedYaw, "only the genuine physical tick rotates the ring three degrees");
                h.assertFalse(f.pose().plates().get(edge.id()).active(), "real rotation moves the same numbered plate out of the facing three");
                h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "current selection retires its historical receipt immediately");
            });
            h.runAfterDelay(4, () -> {
                h.assertTrue(arrow.isRemoved() && f.hp().compareTo(health) < 0, "later genuine body hit cannot use a plate deselected by physical rotation");
                eq(h, f.shield(), shield, "rotation never substitutes an active sibling for an old crossing"); h.succeed();
            });
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void immutable_component_ceilings_limit_later_real_body_settlement_after_resource_credit(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0);
        ready(h, () -> {
            f.seed(copies("2"), copies("3")); var plate = f.front(); BigDecimal health = f.hp();
            var arrow = f.radial(plate, 1.6, .5, 0, null); arrow.tick();
            h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 1, "small immutable component ceilings captured by real flight");
            f.seed(copies("10"), copies("20")); var credited = f.armor();
            // Explicit acceleration fixture: the same ordinary arrow now reaches the real body.
            arrow.setDeltaMovement(vec(plate.normal()).scale(-8)); arrow.tick();
            eq(h, f.armor().plate(plate.id()).baseCurrent(), new BigDecimal("8"), "historical base ceiling stays2 after credit");
            eq(h, f.armor().plate(plate.id()).reserveCurrent(), new BigDecimal("17"), "historical reserve ceiling stays3 after credit");
            eq(h, credited.total().subtract(f.armor().total()), new BigDecimal("5"), "only captured2+3 resources can pay");
            eq(h, f.hp(), health.subtract(new BigDecimal("11")), "sixteen native damage minus five captured units reaches HP directly");
            h.assertTrue(f.timing().breakTicks() == 0, "remaining live plate balance does not create a fabricated break"); h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void partial_contact_breaks_whole_armor_for_four_real_ticks_and_residual_never_uses_siblings(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0);
        ready(h, () -> {
            var plate = f.front(); var bases = new ArrayList<>(copies("100")); var reserves = new ArrayList<>(copies("100"));
            bases.set(plate.id(), new BigDecimal("2")); reserves.set(plate.id(), new BigDecimal("3")); f.seed(bases, reserves);
            var before = f.armor(); BigDecimal health = f.hp(); f.radial(plate, 8, 8, 0, null).tick();
            eq(h, before.total().subtract(f.armor().total()), new BigDecimal("5"), "contact spends only its own2base+3reserve");
            eq(h, f.hp(), health.subtract(new BigDecimal("11")), "remaining eleven reaches HP without sibling or lower-layer fallback");
            for (int i = 0; i < 6; i++) if (i != plate.id()) h.assertTrue(f.armor().plate(i).equals(before.plate(i)), "untouched sibling " + i);
            h.assertTrue(f.timing().breakTicks() == 4 && !ZeroGapArmor.active(f.p), "real native exhaustion opens global four-tick gap");
            // A stronger real native hit in the same iframe accepts only its eight-point differential.
            var sibling = f.pose().plates().stream().filter(p -> p.active() && p.id() != plate.id()).findFirst().orElseThrow();
            BigDecimal resources = f.armor().total(), beforeSecond = f.hp(); f.radial(sibling, 8, 12, 0, null).tick();
            eq(h, f.armor().total(), resources, "a funded sibling cannot defend during the global gap");
            eq(h, f.hp(), beforeSecond.subtract(new BigDecimal("8")), "same-hit native differential reaches HP during gap");
            for (int elapsed = 1; elapsed <= 4; elapsed++) {
                final int ticks = elapsed;
                h.runAfterDelay(ticks, () -> {
                    h.assertTrue(f.timing().breakTicks() == 4 - ticks, "genuine server boundary pays gap debt " + ticks);
                    h.assertTrue(ZeroGapArmor.active(f.p) == (ticks == 4), "global protection resumes only after four real boundaries " + ticks);
                    if (ticks == 4) h.succeed();
                });
            }
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void canceled_native_projectile_impact_and_toggle_reload_retire_pending_evidence(GameTestHelper h) {
        var all = fixtures(h, 3);
        ready(h, () -> {
            var arrows = new Arrow[3]; var shields = new BigDecimal[3];
            for (int i = 0; i < 3; i++) { var f = all[i]; shields[i] = f.shield(); arrows[i] = f.frontArrow(1.6, .5); arrows[i].tick(); h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 1, "real pending crossing " + i); }
            all[0].hook(new Object() { @SubscribeEvent public void impact(ProjectileImpactEvent event) { if (event.getProjectile() == arrows[0]) event.setCanceled(true); } });
            h.assertTrue(all[1].toggle(2), "fresh toggle-off retires historical crossing");
            var shares = all[2].armor().toDecimalMap(); var timing = all[2].timing(); all[2].reload();
            h.assertTrue(all[2].armor().toDecimalMap().equals(shares) && all[2].timing().equals(timing), "native reload keeps exact shares and phase while discarding activation");
            h.runAfterDelay(4, () -> {
                for (int i = 0; i < 3; i++) {
                    eq(h, all[i].shield(), shields[i], "canceled, toggled-off or reloaded controller cannot debit " + i);
                    h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(all[i].p) == 0, "old crossing permanently retired " + i);
                }
                h.succeed();
            });
        }, all);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void fabricated_arrow_source_and_nested_native_tick_cannot_claim_any_armor(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0);
        ready(h, () -> {
            BigDecimal shield = f.shield(); var plate = f.front(); var arrow = f.radial(plate, 1.6, .5, 0, null); arrow.tick();
            h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 1, "real earlier crossing exists");
            h.assertTrue(f.p.hurt(f.p.damageSources().arrow(arrow, f.shooter), .1F), "constructed matching source may do ordinary damage");
            eq(h, f.shield(), shield, "constructed source cannot impersonate the native arrow hurt operand");
            arrow.setDeltaMovement(vec(plate.normal()).scale(-8));
            var outer = ZeroGapArmorArrowReceipts.beginTick(arrow);
            try { arrow.tick(); } finally { ZeroGapArmorArrowReceipts.endTick(outer); }
            h.assertTrue(arrow.isRemoved(), "real nested native impact still owns arrow disposition");
            eq(h, f.shield(), shield, "same-arrow nested tick poisons the outer scope and cannot spend");
            near(h, f.mastery(), 0, "poisoned tick scope cannot award mastery");
            h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "both nested scopes leave no reusable crossing"); h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void native_armor_resistance_enchantment_and_absorption_execute_once_before_one_to_one_debit(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0); var control = new ZeroGapArmorNativeFixture(h, 14);
        ready(h, () -> {
            defenses(h, f.p); defenses(h, control.p); f.p.doTick(); control.p.doTick();
            h.assertTrue(f.p.getArmorValue() > 0 && f.p.getArmorValue() == control.p.getArmorValue(), "paired actual native ticks install equal equipment modifiers");
            var actual = new Observation(f.p); var ordinary = new Observation(control.p); f.hook(actual); control.hook(ordinary);
            BigDecimal shield = f.shield(), health = f.hp(), nativeHealth = control.hp(); Vec3 normal = vec(f.front().normal());
            f.frontArrow(8, 8).tick(); control.arrow(control.center().add(normal.scale(8)), normal.scale(-8), null).tick();
            BigDecimal damage = nativeHealth.subtract(control.hp());
            h.assertTrue(actual.posts == 1 && ordinary.posts == 1 && actual.damage > 0 && actual.armor > 0 && actual.effects > 0 && actual.enchantments > 0 && actual.absorption == .5F,
                    "native armor, effects, enchantments and absorption execute once");
            h.assertTrue(actual.damage == ordinary.damage && actual.armor == ordinary.armor && actual.effects == ordinary.effects && actual.enchantments == ordinary.enchantments && actual.absorption == ordinary.absorption,
                    "252 leaves the complete native reduction pipeline identical to control");
            eq(h, f.shield(), shield.subtract(damage), "only actual final native remainder is charged1:1"); eq(h, f.hp(), health, "contact covers that final remainder");
            h.assertTrue(f.p.getAbsorptionAmount() == 0 && control.p.getAbsorptionAmount() == 0, "ordinary absorption pool pays once"); h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void native_temporary_zero_and_real_lethal_veto_preserve_committed_resources(GameTestHelper h) {
        var full = new ZeroGapArmorNativeFixture(h, 0); var lethal = new ZeroGapArmorNativeFixture(h, 14);
        ready(h, () -> {
            lethal.seed(copies("2"), copies("0")); lethal.health("1");
            BigDecimal shield = full.shield(), health = full.hp(); boolean[] zero = {false}, vetoed = {false};
            full.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post event) { if (event.getEntity() == full.p) zero[0] = full.p.getHealth() == 0; } });
            lethal.hook(new Object() { @SubscribeEvent public void death(LivingDeathEvent event) { if (event.getEntity() == lethal.p) { vetoed[0] = true; event.setCanceled(true); } } });
            full.frontArrow(8, 12).tick(); var plate = lethal.front(); lethal.radial(plate, 8, 8, 0, null).tick();
            h.assertTrue(zero[0] && full.p.isAlive(), "native24damage can temporarily hit zero before exact armor settlement");
            eq(h, full.shield(), shield.subtract(new BigDecimal("24")), "one genuine24point operand spends24once"); eq(h, full.hp(), health, "temporary native zero cannot bypass captured armor");
            h.assertTrue(vetoed[0] && lethal.p.isAlive(), "true lethal residual reaches normal native death veto");
            eq(h, lethal.hp(), BigDecimal.ONE, "native veto restores prior exact HP"); eq(h, lethal.armor().plate(plate.id()).current(), BigDecimal.ZERO, "veto does not refund committed contacted child");
            h.assertTrue(lethal.timing().breakTicks() == 4, "committed break debt survives death veto"); h.succeed();
        }, full, lethal);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void unsupported_critical_burning_piercing_gravity_and_melee_remain_native_damage(GameTestHelper h) {
        var all = fixtures(h, 6);
        ready(h, () -> {
            for (int i = 0; i < all.length; i++) {
                var f = all[i]; BigDecimal shield = f.shield(), health = f.hp();
                if (i == 5) h.assertTrue(f.p.hurt(f.p.damageSources().mobAttack(f.shooter), 5), "ordinary native melee reaches body");
                else {
                    var arrow = f.frontArrow(8, 8);
                    switch (i) {
                        case 0 -> arrow.setCritArrow(true);
                        case 1 -> arrow.igniteForSeconds(20);
                        case 2 -> { var saved = arrow.saveWithoutId(new CompoundTag()); saved.putByte("PierceLevel", (byte)1); arrow.load(saved); }
                        case 3 -> arrow.setNoGravity(true);
                        default -> arrow.getPersistentData().putLong("academy:ground_step_arrow_until", f.p.server.overworld().getGameTime() + 20);
                    }
                    arrow.tick();
                }
                eq(h, f.shield(), shield, "closed252allowlist cannot spend on unsupported source " + i);
                h.assertTrue(f.hp().compareTo(health) < 0, "unsupported source preserves ordinary body damage " + i);
                h.assertTrue(ZeroGapArmorArrowReceipts.receiptCount(f.p) == 0, "unsupported source obtains no252claim " + i);
            }
            h.succeed();
        }, all);
    }

    private static void mutate(ZeroGapArmorNativeFixture f, Arrow arrow, int mutation) {
        switch (mutation) {
            case 0 -> arrow.setOwner(f.mob(f.p.position().add(4, 0, 4)));
            case 1 -> arrow.setBaseDamage(3);
            case 2 -> arrow.getWeaponItem().setCount(2);
            case 3 -> arrow.setCritArrow(true);
            default -> arrow.getPersistentData().putLong("academy:ground_step_arrow_until", f.p.server.overworld().getGameTime() + 20);
        }
    }
    private static void defenses(GameTestHelper h, LivingEntity player) {
        var chest = new ItemStack(Items.DIAMOND_CHESTPLATE); chest.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        chest.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        player.setItemSlot(EquipmentSlot.CHEST, chest); player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0)); player.setAbsorptionAmount(.5F);
    }
    public static final class Observation {
        final LivingEntity target; int posts; float damage, armor, effects, enchantments, absorption;
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void post(LivingDamageEvent.Post event) {
            if (event.getEntity() != target) return; posts++; damage = event.getNewDamage(); armor = event.getReduction(DamageContainer.Reduction.ARMOR);
            effects = event.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = event.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = event.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
}
