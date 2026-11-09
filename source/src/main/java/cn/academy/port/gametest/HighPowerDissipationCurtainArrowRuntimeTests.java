package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ScatterPlateShares;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.*;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
import net.neoforged.neoforge.event.entity.ProjectileImpactEvent;
import net.neoforged.neoforge.event.entity.living.*;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** NEW242 observes real native arrow flight and settles only the later genuine body-hurt operand.
 * Fixtures explicitly enroll LV4 and, where named, install depleted but valid shared ledger balances.
 * No test bypasses native login invulnerability, damage cooldowns, or native arrow impact dispatch. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDissipationCurtainArrowRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_dissipation_curtain_arrows";
    private static final BigDecimal ARROW_COST = new BigDecimal("0.8");
    private HighPowerDissipationCurtainArrowRuntimeTests() {}

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void native_front_body_hit_spends_eighty_percent_and_preserves_arrow_disposition(GameTestHelper h) {
        var f = new Fixture(h, 0); var control = new Fixture(h, 14);
        ready(h, () -> {
            BigDecimal shield = f.shield(), health = f.hp(), nativeHealth = control.hp(); double cp = f.cp();
            Arrow arrow = f.arrow(0, 1, 8, new Vec3(0, 0, -8));
            Arrow ordinary = control.arrow(0, 1, 8, new Vec3(0, 0, -8));
            arrow.tick(); ordinary.tick();
            BigDecimal damage = nativeHealth.subtract(control.hp());
            h.assertTrue(damage.signum() > 0, "paired native arrow really reaches and hurts the unshielded body");
            eq(h, f.shield(), shield.subtract(damage.multiply(ARROW_COST)), "NEW242 debits 0.8 of the actual native damage exactly once");
            eq(h, f.hp(), health, "the authenticated front crossing protects exact HP");
            h.assertTrue(arrow.isRemoved() == ordinary.isRemoved() && arrow.getDeltaMovement().equals(ordinary.getDeltaMovement())
                    && arrow.pickup == ordinary.pickup && arrow.getOwner() == f.shooter && ordinary.getOwner() == control.shooter,
                    "native disposition, velocity, pickup and shooter attribution match the paired control");
            h.assertTrue(f.cp() == cp && DissipationCurtainArrowReceipts.receiptCount(f.p) == 0,
                    "body settlement consumes its receipt and adds no CP charge");
            h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void crossing_alone_never_hurts_and_historical_contact_survives_ordinary_motion(GameTestHelper h) {
        var f = new Fixture(h, 0);
        ready(h, () -> {
            BigDecimal shield = f.shield(), health = f.hp(); double mastery = f.mastery();
            var arrow = f.arrow(0, 1.6, 6.2, new Vec3(0, 0, -1)); arrow.tick();
            h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 1 && !arrow.isRemoved(),
                    "real flight crosses the distant facet before reaching the body");
            eq(h, f.shield(), shield, "crossing alone never debits"); eq(h, f.hp(), health, "crossing alone never synthesizes hurt");
            near(h, f.mastery(), mastery, "crossing alone awards no mastery");
            f.p.setPos(f.p.position().add(.1, 0, 0)); f.p.setYRot(90); f.p.connection.resetPosition();
            h.runAfterDelay(7, () -> {
                h.assertTrue(arrow.isRemoved() && f.shield().compareTo(shield) < 0,
                        "later genuine body hit settles the original same-activation crossing after movement and turning");
                eq(h, f.hp(), health, "historical valid contact protects HP");
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0, "native body contact consumes historical receipt");
                h.succeed();
            });
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=105)
    public static void facet_graze_that_misses_body_never_spends_and_expires_after_twenty_ticks(GameTestHelper h) {
        var f = new Fixture(h, 0);
        ready(h, () -> {
            BigDecimal shield = f.shield(), health = f.hp(); double mastery = f.mastery();
            var arrow = f.arrow(1, 1.6, 6.2, new Vec3(0, 0, -1)); arrow.tick();
            h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 1, "outer central facet graze is a real crossing");
            eq(h, f.shield(), shield, "graze does not spend at the facet");
            h.runAfterDelay(21, () -> {
                eq(h, f.shield(), shield, "a missed body never consumes base shield");
                eq(h, f.hp(), health, "the facet never generates a synthetic hurt");
                near(h, f.mastery(), mastery, "standing and expired grazes award no mastery");
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0, "the fixed twenty-tick receipt has expired");
                h.succeed();
            });
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void observing_a_real_graze_changes_neither_flight_nor_player_callbacks_or_persistent_data(GameTestHelper h) {
        var f = new Fixture(h, 0); var control = new Fixture(h, 14);
        ready(h, () -> {
            var state = AbilityStorage.get(f.p); var saved = f.p.getPersistentData().copy();
            String[] names = {"calculation", "consumptionValidity", "activationChanged", "categoryChanged", "beforeEarnedMutation", "earnedExperience", "skillExpChanged", "skillExpAdded"};
            Map<String, Object> callbacks = new HashMap<>();
            try {
                for (String name : names) { var field = state.getClass().getDeclaredField(name); field.setAccessible(true); callbacks.put(name, field.get(state)); }
                var arrow = f.arrow(1, 1.6, 6.2, new Vec3(0, 0, -1));
                var nativeArrow = control.arrow(1, 1.6, 6.2, new Vec3(0, 0, -1));
                arrow.tick(); nativeArrow.tick();
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 1, "real outer-facet crossing was observed");
                h.assertTrue(arrow.getDeltaMovement().equals(nativeArrow.getDeltaMovement()) && arrow.isRemoved() == nativeArrow.isRemoved()
                        && arrow.position().subtract(f.p.position()).distanceToSqr(nativeArrow.position().subtract(control.p.position())) < 1e-20,
                        "crossing observation does not move, slow, remove or redirect the real arrow");
                for (String name : names) { var field = state.getClass().getDeclaredField(name); field.setAccessible(true); h.assertTrue(field.get(state) == callbacks.get(name), "observer does not rebind " + name); }
                h.assertTrue(f.p.getPersistentData().equals(saved), "crossing observation writes no player persistent state");
            } catch (ReflectiveOperationException invalid) { throw new AssertionError(invalid); }
            h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void three_facets_protect_but_real_four_degree_gaps_rear_side_and_inside_remain_open(GameTestHelper h) {
        Fixture[] fixtures = fixtures(h, 8);
        ready(h, () -> {
            double[] angle = {-40, 0, 40, -20, 20, 180, 90, 0};
            for (int i = 0; i < fixtures.length; i++) {
                var f = fixtures[i]; BigDecimal shield = f.shield(), health = f.hp();
                double radius = i == 7 ? 3 : 8, radians = Math.toRadians(angle[i]);
                Vec3 radial = new Vec3(Math.sin(radians), 0, Math.cos(radians));
                f.arrow(radial.x * radius, 1, radial.z * radius, radial.scale(-radius)).tick();
                if (i < 3) {
                    h.assertTrue(f.shield().compareTo(shield) < 0, "front facet center absorbs " + angle[i]);
                    eq(h, f.hp(), health, "front facet protects its true corridor " + angle[i]);
                } else {
                    eq(h, f.shield(), shield, "gap/rear/side/inside trajectory never spends " + i);
                    h.assertTrue(f.hp().compareTo(health) < 0, "ordinary body damage survives physical vulnerability " + i);
                }
            }
            h.succeed();
        }, fixtures);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void nearer_native_wall_body_and_ambiguous_body_tie_cannot_claim_curtain(GameTestHelper h) {
        Fixture[] fixtures = fixtures(h, 3);
        ready(h, () -> {
            for (int i = 0; i < fixtures.length; i++) {
                var f = fixtures[i]; BigDecimal shield = f.shield();
                if (i == 0) {
                    var wall = BlockPos.containing(f.p.position().add(0, 1, 7));
                    h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState()); f.blocks.add(wall);
                } else f.mob(f.p.position().add(0, 0, i == 1 ? 7 : 0));
                f.arrow(0, 1, 8, new Vec3(0, 0, -8)).tick();
                eq(h, f.shield(), shield, "first wall/body/tied body retains native priority " + i);
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0,
                        "clipped or ambiguous native hit cannot create usable receipt " + i);
            }
            h.succeed();
        }, fixtures);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void canceled_zero_pre_absorption_and_native_iframe_retire_without_spend_or_reward(GameTestHelper h) {
        Fixture[] fixtures = fixtures(h, 5);
        ready(h, () -> {
            for (int i = 0; i < fixtures.length; i++) {
                var f = fixtures[i]; final int mode = i;
                if (i < 3) f.hook(new Object() {
                    @SubscribeEvent public void incoming(LivingIncomingDamageEvent e) {
                        if (e.getEntity() != f.p) return;
                        if (mode == 0) e.setCanceled(true); else if (mode == 1) e.setAmount(0);
                    }
                    @SubscribeEvent public void pre(LivingDamageEvent.Pre e) { if (e.getEntity() == f.p && mode == 2) e.setNewDamage(0); }
                });
                else if (i == 3) { f.p.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 7)); f.p.setAbsorptionAmount(32); }
                else h.assertTrue(f.p.hurt(f.p.damageSources().mobAttack(f.shooter), 20), "genuine prior hit establishes native iframe");
                BigDecimal shield = f.shield(), health = f.hp(); double mastery = f.mastery();
                f.arrow(0, 1, 8, new Vec3(0, 0, -8)).tick();
                eq(h, f.shield(), shield, "failed or fully mitigated native hurt never pays " + i);
                eq(h, f.hp(), health, "native canceled/zero/absorbed/iframe decision protects HP " + i);
                near(h, f.mastery(), mastery, "failed hurt cannot award mastery " + i);
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0, "failed native body hurt consumes receipt " + i);
                if (i == 3) h.assertTrue(f.p.getAbsorptionAmount() == 16, "native absorption alone paid the real sixteen-point hit once");
            }
            h.succeed();
        }, fixtures);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void owner_damage_weapon_critical_or_competing_deflection_mutations_invalidate_crossing(GameTestHelper h) {
        Fixture[] fixtures = fixtures(h, 5);
        ready(h, () -> {
            for (int i = 0; i < fixtures.length; i++) {
                var f = fixtures[i]; BigDecimal shield = f.shield();
                var arrow = f.arrowWithWeapon(0, 1.6, 6.2, new Vec3(0, 0, -1), new ItemStack(Items.BOW)); arrow.tick();
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 1, "ordinary bow source records crossing " + i);
                switch (i) {
                    case 0 -> arrow.setOwner(f.mob(f.p.position().add(4, 0, 4)));
                    case 1 -> arrow.setBaseDamage(3);
                    case 2 -> arrow.getWeaponItem().setCount(2);
                    case 3 -> arrow.setCritArrow(true);
                    default -> arrow.getPersistentData().putLong("academy:ground_step_arrow_until", f.p.server.overworld().getGameTime() + 20);
                }
                DissipationCurtainArrowReceipts.prune(f.p.server);
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0, "source or deflection change retires crossing " + i);
                eq(h, f.shield(), shield, "mutation itself cannot debit anything");
            }
            h.succeed();
        }, fixtures);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void mutable_incoming_and_pre_callbacks_revoke_an_already_claimed_source(GameTestHelper h) {
        Fixture[] fixtures = fixtures(h, 10);
        ready(h, () -> {
            for (int i = 0; i < fixtures.length; i++) {
                var f = fixtures[i]; BigDecimal shield = f.shield(), health = f.hp(); double mastery = f.mastery();
                var arrow = f.arrowWithWeapon(0, 1, 8, new Vec3(0, 0, -8), new ItemStack(Items.BOW));
                final int mutation = i % 5; final boolean before = i >= 5; boolean[] changed = {false};
                Runnable change = () -> {
                    if (changed[0]) return; changed[0] = true;
                    switch (mutation) {
                        case 0 -> arrow.setOwner(f.mob(f.p.position().add(4, 0, 4)));
                        case 1 -> arrow.setBaseDamage(3);
                        case 2 -> arrow.getWeaponItem().setCount(2);
                        case 3 -> arrow.setCritArrow(true);
                        default -> arrow.getPersistentData().putLong("academy:ground_step_arrow_until", f.p.server.overworld().getGameTime() + 20);
                    }
                };
                f.hook(new Object() {
                    @SubscribeEvent(priority=EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) {
                        if (!before && e.getEntity() == f.p && e.getSource().getDirectEntity() == arrow) change.run();
                    }
                    @SubscribeEvent(priority=EventPriority.NORMAL) public void pre(LivingDamageEvent.Pre e) {
                        if (before && e.getEntity() == f.p && e.getSource().getDirectEntity() == arrow) change.run();
                    }
                });
                arrow.tick();
                h.assertTrue(changed[0], "real native callback mutated the claimed source " + i);
                eq(h, f.shield(), shield, "final source recheck prevents a stale curtain debit " + i);
                eq(h, f.hp(), health.subtract(new BigDecimal("16")), "ordinary native body damage remains intact " + i);
                near(h, f.mastery(), mastery, "revoked source produces no reward " + i);
                h.assertTrue(arrow.isRemoved(), "native hit disposition is unchanged " + i);
            }
            h.succeed();
        }, fixtures);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void constructed_arrow_damage_source_and_reentrant_hurt_cannot_claim_twice(GameTestHelper h) {
        var f = new Fixture(h, 0);
        ready(h, () -> {
            BigDecimal shield = f.shield();
            var arrow = f.arrow(0, 1.6, 6.2, new Vec3(0, 0, -1)); arrow.tick();
            h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 1, "genuine historical contact is pending");
            h.assertTrue(f.p.hurt(f.p.damageSources().arrow(arrow, f.shooter), .1F), "constructed matching source follows ordinary native hurt");
            eq(h, f.shield(), shield, "constructed matching source cannot impersonate the native arrow body operand");
            boolean[] reentered = {false};
            f.hook(new Object() {
                @SubscribeEvent(priority=EventPriority.LOWEST) public void incoming(LivingIncomingDamageEvent e) {
                    if (e.getEntity() == f.p && e.getSource().getDirectEntity() == arrow && !reentered[0]) {
                        reentered[0] = true; f.p.hurt(e.getSource(), .05F);
                    }
                }
            });
            for (int tick = 0; tick < 7 && !arrow.isRemoved(); tick++) arrow.tick();
            h.assertTrue(reentered[0] && arrow.isRemoved(), "the genuine body callback executes reentry and preserves native impact");
            h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0,
                    "the native hurt operand consumes its crossing exactly once despite reentry");
            eq(h, f.shield(), shield, "nested native hurt poisons the outer frame and refuses its entire curtain debit");
            near(h, f.mastery(), 0, "a poisoned reentrant hurt cannot award absorption mastery");
            h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void nested_tick_for_the_same_real_arrow_retires_both_outer_defense_scopes(GameTestHelper h) {
        var f = new Fixture(h, 0);
        h.runAfterDelay(61, () -> {
            f.scatter(); f.start();
            h.runAfterDelay(6, () -> {
                BigDecimal shield = f.shield(), health = f.hp();
                var arrow = f.arrow(0, 1.6, 6.2, new Vec3(0, 0, -1)); arrow.tick();
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 1,
                        "genuine first flight step records an unspent curtain crossing");
                // Declared reentry fixture: an already open native-arrow scope surrounds another actual tick.
                // Accelerating this same arrow makes that inner native tick reach the body and NEW212 plane.
                arrow.setDeltaMovement(new Vec3(0, 0, -8));
                var outerCurtain = DissipationCurtainArrowReceipts.beginTick(arrow);
                var outerScatter = ScatterPlateArrowReceipts.beginTick(arrow);
                try { arrow.tick(); }
                finally {
                    ScatterPlateArrowReceipts.endTick(outerScatter);
                    DissipationCurtainArrowReceipts.endTick(outerCurtain);
                }
                h.assertTrue(arrow.isRemoved(), "the nested actual vanilla tick still owns its normal successful-hit disposition");
                eq(h, f.hp(), health.subtract(new BigDecimal("16")), "native body damage still proceeds when nested evidence is refused");
                eq(h, f.shield(), shield, "same-arrow nested tick cannot spend either242 or212 shield");
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0
                        && ScatterPlateArrowReceipts.receiptCount(f.p) == 0,
                        "poisoned outer scopes leave no historical or newly crossed receipt to reuse");
                near(h, f.mastery(), 0, "poisoned curtain scope earns no mastery");
                near(h, AbilityStorage.get(f.p).exp(ScatterPlates.ID), 0, "poisoned scatter scope earns no mastery");
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void projectile_impact_cancel_and_native_teleport_retire_pending_contact(GameTestHelper h) {
        var f = new Fixture(h, 0); var teleport = new Fixture(h, 14);
        ready(h, () -> {
            BigDecimal shield = f.shield(), otherShield = teleport.shield();
            var arrow = f.arrow(0, 1.6, 6.2, new Vec3(0, 0, -1)); arrow.tick();
            teleport.arrow(0, 1.6, 6.2, new Vec3(0, 0, -1)).tick();
            h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 1
                    && DissipationCurtainArrowReceipts.receiptCount(teleport.p) == 1, "both physical crossings are recorded");
            f.hook(new Object() { @SubscribeEvent public void impact(ProjectileImpactEvent e) { if (e.getProjectile() == arrow) e.setCanceled(true); } });
            teleport.p.connection.teleport(teleport.p.getX() + .1, teleport.p.getY(), teleport.p.getZ(), 0, 0);
            h.assertFalse(DissipationCurtain.holding(teleport.p), "even short explicit native teleport retires the held controller");
            h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(teleport.p) == 0, "teleport clears historical contact immediately");
            h.runAfterDelay(7, () -> {
                eq(h, f.shield(), shield, "canceled native impact never debits"); eq(h, teleport.shield(), otherShield, "teleported controller cannot spend");
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0, "impact cancellation permanently consumes pending contact");
                h.succeed();
            });
        }, f, teleport);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=105)
    public static void release_recast_owner_epoch_reload_and_world_change_never_revive_old_receipts(GameTestHelper h) {
        Fixture[] fixtures = {new Fixture(h, 0, true), new Fixture(h, 14), new Fixture(h, 28), new Fixture(h, 42)};
        ready(h, () -> {
            for (var f : fixtures) {
                f.arrow(1, 1.6, 6.2, new Vec3(0, 0, -1)).tick();
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 1, "historical graze exists before lifecycle change");
            }
            var released = fixtures[0]; var replaced = fixtures[1]; var reloaded = fixtures[2];
            h.assertTrue(DissipationCurtain.release(released.p, released.wire(1)), "authenticated release retires the held cast");
            int releasePumpTicks = released.pumpTicks;
            LegacySingleKeyOwnerEpoch.forget(replaced.p); replaced.epoch(); DissipationCurtain.tick(replaced.p);
            AbilityStorage.save(reloaded.p); var saved = reloaded.p.saveWithoutId(new CompoundTag());
            DissipationCurtain.remove(reloaded.p); AbilityStorage.remove(reloaded.p); LegacySingleKeyOwnerEpoch.forget(reloaded.p);
            reloaded.p.load(saved); reloaded.place(); reloaded.epoch();
            var moved = fixtures[3]; var other = moved.p.server.getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertTrue(other != null, "a real destination dimension exists");
            other.getChunk(moved.p.blockPosition());
            moved.p.teleportTo(other, moved.p.getX(), moved.p.getY(), moved.p.getZ(), Set.of(), 0, 0);
            h.assertTrue(moved.p.serverLevel() == other && !DissipationCurtain.holding(moved.p),
                    "real native world transfer retires the held curtain");
            for (var f : fixtures) h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0,
                    "release, owner incarnation replacement, native reload or world transfer invalidates old crossing");
            BigDecimal shield = released.shield();
            h.runAfterDelay(10, () -> {
                int cooldown = AbilityStorage.get(released.p).cooldowns.getOrDefault(DissipationCurtain.ID, 0);
                h.assertTrue(released.pumpTicks - releasePumpTicks == 10,
                        "exactly ten native player ticks run before rearm; observed ticks=" + (released.pumpTicks - releasePumpTicks) + ", cooldown=" + cooldown);
                h.assertTrue(DissipationCurtain.start(released.p, released.wire(2)),
                        "fresh held activation pays again after native LV4 cooldown; observed cooldown=" + cooldown);
                h.runAfterDelay(6, () -> {
                    h.assertTrue(DissipationCurtain.active(released.p) && DissipationCurtainArrowReceipts.receiptCount(released.p) == 0,
                            "fresh activation cannot adopt old same-owner receipts");
                    eq(h, released.shield(), shield, "release and recast never synthesize a shield debit or refill");
                    h.succeed();
                });
            });
        }, fixtures);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void unsupported_arrow_variants_and_melee_remain_ordinary_native_damage(GameTestHelper h) {
        Fixture[] fixtures = fixtures(h, 7);
        ready(h, () -> {
            for (int i = 0; i < fixtures.length; i++) {
                var f = fixtures[i]; BigDecimal shield = f.shield(), health = f.hp();
                if (i == 6) h.assertTrue(f.p.hurt(f.p.damageSources().mobAttack(f.shooter), 5), "ordinary melee reaches body");
                else {
                    var arrow = i == 4 ? f.unknownArrow() : f.arrow(0, 1, 8, new Vec3(0, 0, -8));
                    switch (i) {
                        case 0 -> arrow.setCritArrow(true);
                        case 1 -> arrow.igniteForSeconds(20);
                        case 2 -> { var saved = arrow.saveWithoutId(new CompoundTag()); saved.putByte("PierceLevel", (byte) 1); arrow.load(saved); }
                        case 3 -> arrow.setNoGravity(true);
                        case 5 -> arrow.getPersistentData().putLong("academy:ground_step_arrow_until", f.p.server.overworld().getGameTime() + 20);
                    }
                    arrow.tick();
                }
                eq(h, f.shield(), shield, "closed arrow allowlist or melee cannot spend curtain resources " + i);
                h.assertTrue(f.hp().compareTo(health) < 0, "unsupported attack retains its ordinary native damage " + i);
                h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0, "unsupported attack never obtains a crossing claim " + i);
            }
            h.succeed();
        }, fixtures);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void native_armor_resistance_enchantment_and_absorption_run_once_before_discounted_debit(GameTestHelper h) {
        var f = new Fixture(h, 0); var control = new Fixture(h, 14);
        ready(h, () -> {
            defenses(h, f.p); defenses(h, control.p);
            // Native LivingEntity ticking detects the new equipment and installs its attribute modifiers.
            f.p.doTick(); control.p.doTick();
            h.assertTrue(f.p.getArmorValue() > 0 && f.p.getArmorValue() == control.p.getArmorValue(),
                    "paired native ticks installed the same positive equipment armor before the hit");
            var actual = f.observe(); var ordinary = control.observe();
            BigDecimal shield = f.shield(), health = f.hp(), nativeHealth = control.hp();
            f.arrow(0, 1, 8, new Vec3(0, 0, -8)).tick(); control.arrow(0, 1, 8, new Vec3(0, 0, -8)).tick();
            BigDecimal damage = nativeHealth.subtract(control.hp());
            h.assertTrue(actual.posts == 1 && ordinary.posts == 1 && actual.damage > 0 && actual.armor > 0
                    && actual.effects > 0 && actual.enchantments > 0 && actual.absorption == .5F,
                    "all ordinary native reduction stages execute once: posts=" + actual.posts + "/" + ordinary.posts
                            + " damage=" + actual.damage + " armor=" + actual.armor + " effects=" + actual.effects
                            + " enchantments=" + actual.enchantments + " absorption=" + actual.absorption);
            h.assertTrue(actual.damage == ordinary.damage && actual.armor == ordinary.armor && actual.effects == ordinary.effects
                    && actual.enchantments == ordinary.enchantments && actual.absorption == ordinary.absorption,
                    "native mitigation matches the equivalent unshielded target");
            eq(h, f.shield(), shield.subtract(damage.multiply(ARROW_COST)), "only the actual post-native remainder receives the arrow discount");
            eq(h, f.hp(), health, "curtain covers only that final remainder");
            h.assertTrue(f.p.getAbsorptionAmount() == 0 && control.p.getAbsorptionAmount() == 0, "ordinary absorption is spent once");
            h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=105)
    public static void raised_vanilla_shield_keeps_native_block_and_prevents_any_curtain_debit(GameTestHelper h) {
        var f = new Fixture(h, 0); var control = new Fixture(h, 14);
        ready(h, () -> {
            for (var p : List.of(f.p, control.p)) { p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD)); p.startUsingItem(InteractionHand.OFF_HAND); }
            h.runAfterDelay(7, () -> {
                h.assertTrue(f.p.isBlocking() && control.p.isBlocking(), "real native shield raising delay has elapsed");
                var actual = f.observe(); var ordinary = control.observe(); BigDecimal shield = f.shield(), health = f.hp();
                var arrow = f.arrow(0, 1, 8, new Vec3(0, 0, -8)); var nativeArrow = control.arrow(0, 1, 8, new Vec3(0, 0, -8));
                arrow.tick(); nativeArrow.tick();
                h.assertTrue(actual.blocked && ordinary.blocked && actual.blockedDamage == ordinary.blockedDamage,
                        "native shield facing and durability decisions match");
                eq(h, f.shield(), shield, "native block occurs before any curtain spend"); eq(h, f.hp(), health, "native block protects exact HP");
                h.assertTrue(arrow.isRemoved() == nativeArrow.isRemoved() && arrow.getDeltaMovement().equals(nativeArrow.getDeltaMovement())
                        && DissipationCurtainArrowReceipts.receiptCount(f.p) == 0,
                        "native shield bounce and consumed crossing survive unchanged");
                h.succeed();
            });
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void coexisting_scatter_absorbs_only_once_and_partial_curtain_uses_base_then_lower_never_upper(GameTestHelper h) {
        var full = new Fixture(h, 0); var partial = new Fixture(h, 14);
        h.runAfterDelay(61, () -> {
            partial.pools("2", "2", "20");
            full.scatter(); partial.scatter(); full.start(); partial.start();
            h.runAfterDelay(6, () -> {
                BigDecimal fullShield = full.shield(), fullHealth = full.hp(), partialHealth = partial.hp();
                full.arrow(0, 1, 8, new Vec3(0, 0, -8)).tick(); partial.arrow(0, 1, 8, new Vec3(0, 0, -8)).tick();
                eq(h, full.shield(), fullShield.subtract(new BigDecimal("12.8")), "242 wins over active212 and spends only one 0.8 arrow debit");
                eq(h, full.hp(), fullHealth, "winning curtain protects full body damage");
                eq(h, partial.shield(), BigDecimal.ZERO, "partial curtain drains only its available two base points first");
                eq(h, partial.reserve("lower"), BigDecimal.ZERO, "remaining two-point debit drains only lower reserve");
                eq(h, partial.reserve("upper"), new BigDecimal("20"), "upper reserve remains byte-for-byte spend-ineligible");
                eq(h, partial.hp(), partialHealth.subtract(new BigDecimal("11")), "four available units absorb five; residual eleven goes directly to HP");
                for (var f : List.of(full, partial)) {
                    eq(h, ScatterPlateStorage.read(f.p).orElseThrow().shield(), f.shield(), "ordinary base debit reconciles 212 allocation once");
                    near(h, AbilityStorage.get(f.p).exp(ScatterPlates.ID), 0, "losing212 earns no absorption mastery");
                    h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0 && ScatterPlateArrowReceipts.receiptCount(f.p) == 0,
                            "one native body hit retires both candidate receipts");
                }
                h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void native_equal_hit_iframe_and_stronger_differential_keep_exact_discounted_spending(GameTestHelper h) {
        var f = new Fixture(h, 0);
        ready(h, () -> {
            BigDecimal shield = f.shield(), health = f.hp();
            f.arrow(0, 1, 8, new Vec3(0, 0, -8)).tick();
            eq(h, f.shield(), shield.subtract(new BigDecimal("12.8")), "first native sixteen-point body hit pays once");
            double mastery = f.mastery();
            f.arrow(0, 1, 8, new Vec3(0, 0, -8)).tick();
            eq(h, f.shield(), shield.subtract(new BigDecimal("12.8")), "equal native iframe rejection cannot spend another receipt");
            f.arrow(0, 1, 8, new Vec3(0, 0, -12)).tick();
            eq(h, f.shield(), shield.subtract(new BigDecimal("19.2")), "stronger native twenty-four-point hit adds only the eight-point differential at0.8");
            eq(h, f.hp(), health, "authenticated curtain covers only accepted native damage");
            near(h, f.mastery(), mastery, "real successive hits inside the20tick cadence cannot farm mastery");
            h.assertTrue(DissipationCurtainArrowReceipts.receiptCount(f.p) == 0, "each native body decision permanently consumes its evidence");
            h.succeed();
        }, f);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=90)
    public static void native_temporary_zero_and_lethal_veto_preserve_the_single_committed_debit(GameTestHelper h) {
        var full = new Fixture(h, 0); var lethal = new Fixture(h, 14);
        h.runAfterDelay(61, () -> {
            lethal.pools("2", "0", "10"); lethal.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("health", "1");
            full.start(); lethal.start();
            h.runAfterDelay(6, () -> {
                BigDecimal shield = full.shield(), health = full.hp(); boolean[] temporarilyZero = {false}, vetoed = {false};
                full.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(LivingDamageEvent.Post e) { if (e.getEntity() == full.p) temporarilyZero[0] = full.p.getHealth() == 0; } });
                lethal.hook(new Object() { @SubscribeEvent public void death(LivingDeathEvent e) { if (e.getEntity() == lethal.p) { vetoed[0] = true; e.setCanceled(true); } } });
                full.arrow(0, 1, 8, new Vec3(0, 0, -12)).tick(); lethal.arrow(0, 1, 8, new Vec3(0, 0, -8)).tick();
                h.assertTrue(temporarilyZero[0] && full.p.isAlive(), "native24damage can briefly reach zero before authenticated exact settlement");
                eq(h, full.hp(), health, "temporary native zero cannot bypass the captured curtain");
                eq(h, full.shield(), shield.subtract(new BigDecimal("19.2")), "one24point arrow spends only19.2");
                h.assertTrue(vetoed[0] && lethal.p.isAlive(), "true exact lethal remainder still reaches the native death veto");
                eq(h, lethal.hp(), BigDecimal.ONE, "native veto restores prior exact injury");
                eq(h, lethal.shield(), BigDecimal.ZERO, "death veto never refunds the committed two-point base debit");
                eq(h, lethal.reserve("upper"), new BigDecimal("10"), "death cannot turn upper reserve into protection");
                h.succeed();
            });
        });
    }

    private static void ready(GameTestHelper h, Runnable action, Fixture... active) {
        h.runAfterDelay(61, () -> {
            for (var f : active) f.start();
            h.runAfterDelay(6, () -> { for (var f : active) h.assertTrue(DissipationCurtain.active(f.p), "six real startup ticks completed"); action.run(); });
        });
    }
    private static Fixture[] fixtures(GameTestHelper h, int count) {
        var result = new Fixture[count]; for (int i = 0; i < count; i++) result[i] = new Fixture(h, i * 14); return result;
    }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " / " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-7, why + ": " + actual + " / " + expected); }
    private static void defenses(GameTestHelper h, LivingEntity player) {
        var chest = new ItemStack(Items.DIAMOND_CHESTPLATE); chest.set(DataComponents.UNBREAKABLE, new Unbreakable(false));
        chest.enchant(h.getLevel().registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.PROTECTION), 4);
        player.setItemSlot(EquipmentSlot.CHEST, chest); player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 200, 0));
        player.addEffect(new MobEffectInstance(MobEffects.ABSORPTION, 200, 0)); player.setAbsorptionAmount(.5F);
    }
    public static final class Observation {
        final LivingEntity target; int posts; boolean blocked; float damage, armor, effects, enchantments, absorption, blockedDamage;
        Observation(LivingEntity target) { this.target = target; }
        @SubscribeEvent public void shield(LivingShieldBlockEvent e) { if (e.getEntity() == target) { blocked = e.getBlocked(); blockedDamage = e.getBlockedDamage(); } }
        @SubscribeEvent public void post(LivingDamageEvent.Post e) {
            if (e.getEntity() != target) return; posts++; damage = e.getNewDamage(); armor = e.getReduction(DamageContainer.Reduction.ARMOR);
            effects = e.getReduction(DamageContainer.Reduction.MOB_EFFECTS); enchantments = e.getReduction(DamageContainer.Reduction.ENCHANTMENTS); absorption = e.getReduction(DamageContainer.Reduction.ABSORPTION);
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final ServerPlayer p; final Zombie shooter; final EmbeddedChannel channel; final double offset;
        final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); final List<BlockPos> blocks = new ArrayList<>(); boolean closed; int pumpTicks;
        Fixture(GameTestHelper h, double offset) { this(h, offset, false); }
        Fixture(GameTestHelper h, double offset, boolean pumpBeforeAssertions) {
            this.h = h; this.offset = offset; var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "DC" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); p.getAbilities().instabuild = false; place();
            var state = AbilityStorage.get(p); state.selectCategory("meltdowner"); state.setLevel(4);
            state.learn(DualPoint.ID); state.learn(ScatterPlates.ID); state.learn(DissipationCurtain.ID); state.activated = true; state.overloadFine = true;
            h.assertTrue(HighPowerCombat.enroll(p, 4), "declared connected LV4 native fixture");
            h.assertTrue(state.presets.edit(0, 0, DissipationCurtain.ID, state::learned) && state.presets.edit(0, 1, ScatterPlates.ID, state::learned), "bind learned curtain and scatter independently");
            HighPowerResources.initializeBudget(p); ScatterPlateStorage.ensure(p); epoch(); shooter = mob(p.position().add(5, 0, 5));
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {}
                public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
            if (pumpBeforeAssertions) pumpBeforeAssertions();
            else h.onEachTick(() -> { if (!closed) p.doTick(); });
        }
        /** Same native work once per server tick, before the unordered GameTest callback map. */
        void pumpBeforeAssertions() {
            int started = p.server.getTickCount(), until = started + h.testInfo.getTimeoutTicks() + 5;
            hook(new Object() {
                int last = started;
                @SubscribeEvent(priority=EventPriority.HIGHEST) public void tick(ServerTickEvent.Pre event) {
                    int tick = p.server.getTickCount();
                    if (event.getServer() != p.server || tick <= last || tick > until || closed || p.isRemoved() || p.hasDisconnected()) return;
                    last = tick; p.doTick(); pumpTicks++;
                }
            });
        }
        void place() { Vec3 at = h.absoluteVec(new Vec3(8.5, 18 + offset, 8.5)); p.moveTo(at.x, at.y, at.z, 0, 0); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); }
        Zombie mob(Vec3 at) { Zombie mob = EntityType.HUSK.create(h.getLevel()); mob.setNoAi(true); mob.setNoGravity(true); mob.setSilent(true); mob.getAttribute(Attributes.ARMOR).setBaseValue(0); mob.setPos(at); h.getLevel().addFreshEntity(mob); entities.add(mob); return mob; }
        Arrow arrow(double x, double y, double z, Vec3 velocity) { return arrowWithWeapon(x, y, z, velocity, null); }
        Arrow arrowWithWeapon(double x, double y, double z, Vec3 velocity, ItemStack weapon) {
            var arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), weapon); arrow.setPos(p.position().add(x, y, z)); arrow.setDeltaMovement(velocity);
            h.getLevel().addFreshEntity(arrow); entities.add(arrow); return arrow;
        }
        Arrow unknownArrow() {
            var arrow = new Arrow(h.getLevel(), shooter, new ItemStack(Items.ARROW), null) {}; arrow.setPos(p.position().add(0, 1, 8)); arrow.setDeltaMovement(new Vec3(0, 0, -8));
            h.getLevel().addFreshEntity(arrow); entities.add(arrow); return arrow;
        }
        void start() { h.assertTrue(DissipationCurtain.start(p, wire(1)), "paid authenticated held curtain starts"); h.assertFalse(DissipationCurtain.active(p), "startup has no physical defense"); }
        void scatter() { h.assertTrue(ScatterPlates.toggle(p, new HighPowerInput.Input(1, 1, p.getId(), epoch()).wire()), "real paid212 remains active beside242"); }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        String wire(long input) { return new HighPowerInput.Input(0, input, p.getId(), epoch()).wire(); }
        BigDecimal shield() { return HighPowerCombat.shield(p); }
        BigDecimal hp() { return new BigDecimal(HighPowerCombat.healthText(p)); }
        double cp() { return HighPowerResources.cp(p); }
        double mastery() { return AbilityStorage.get(p).exp(DissipationCurtain.ID); }
        BigDecimal reserve(String share) { return new BigDecimal(p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(MagneticDomainReserve.KEY).getString(share)); }
        /** Explicit finite balance fixture: same existing base and reserve ledger, valid independent212 allocation, no new pool. */
        void pools(String base, String lower, String upper) {
            var combat = p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY); var maximum = new BigDecimal(combat.getString("maxShield"));
            combat.putString("shield", base); var shares = new CompoundTag();
            ScatterPlateShares.initialize(new BigDecimal(base), maximum).toDecimalMap().forEach(shares::putString);
            combat.put(ScatterPlateStorage.KEY, shares); combat.putBoolean(ScatterPlateStorage.INITIALIZED_KEY, true);
            var reserve = new CompoundTag(); reserve.putInt("version", 2); reserve.putString("amount", CombatAmounts.decimalString(new BigDecimal(lower).add(new BigDecimal(upper))));
            reserve.putString("capacity", CombatAmounts.decimalString(maximum)); reserve.putString("lower", lower); reserve.putString("upper", upper); combat.put(MagneticDomainReserve.KEY, reserve);
        }
        Observation observe() { var result = new Observation(p); hook(result); return result; }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void close() {
            if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); blocks.forEach(pos -> h.getLevel().setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState()));
            DissipationCurtain.remove(p); ScatterPlates.remove(p); AbilityStorage.remove(p); LegacySingleKeyOwnerEpoch.forget(p);
            h.getLevel().getServer().getPlayerList().remove(p); entities.forEach(Entity::discard); channel.finishAndReleaseAll();
        }
    }
}
