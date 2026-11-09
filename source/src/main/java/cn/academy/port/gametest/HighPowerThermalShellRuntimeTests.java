package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.api.SkillExpAddedEvent;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ThermalShellRules;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native NEW222 controller cost, deferred bounded emission, durable cadence and lifecycle witnesses. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerThermalShellRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_thermal_shell_controller";
    private static BigDecimal n(String value) { return new BigDecimal(value); }
    private static void eq(GameTestHelper h, BigDecimal actual, BigDecimal expected, String why) { h.assertTrue(actual.compareTo(expected) == 0, why + ": " + actual + " != " + expected); }
    private static void near(GameTestHelper h, double actual, double expected, String why) { h.assertTrue(Math.abs(actual - expected) < 1e-5, why + ": " + actual + " != " + expected); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 110)
    public static void thermal_paid_toggle_twenty_tick_upkeep_and_same_tick_pulse_have_independent_costs(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            double cp = f.cp(); h.assertTrue(f.toggle(1), "authenticated LV2 toggle"); near(h, f.cp(), cp - 2, "activation exact approved two CP");
            double once = f.cp(); ThermalShell.tick(f.p); ThermalShell.tick(f.p); near(h, f.cp(), once, "activation period already paid; no duplicate upkeep");
            int[] renewals = {0};
            // Embedded connections need actual player ticks to retire the native one-tick UI cooldown.
            f.hook(new NativePlayerPump(f.p, 22));
            f.hook(new Object() { @SubscribeEvent(priority = EventPriority.HIGHEST) public void before(ServerTickEvent.Pre e) {
                if (e.getServer() != f.p.server || !ThermalShell.renewalPending(f.p)) return;
                renewals[0]++; double prior = f.cp(), upkeep = ThermalShellRules.sustainCost(HighPowerGrowth.values(f.p).position());
                ThermalShell.tick(f.p); near(h, f.cp(), prior - upkeep, "exact closed twenty-tick upkeep debit");
                h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ThermalShell.ID, 0) == 1, "pulse genuinely shares upkeep's still-active native one-tick guard");
                f.place(); Zombie target = f.mob(0, 2); double before = f.cp(); BigDecimal shield = f.shield();
                h.assertTrue(f.hit(5), "fresh positive absorption in upkeep tick"); h.assertTrue(target.getHealth() == 20, "no synchronous damage inside outer native hurt");
                double cost = ThermalShellRules.pulseCost(HighPowerGrowth.values(f.p).position()); ThermalShell.drain(f.p);
                near(h, f.cp(), before - cost, "same tick one-tick upkeep guard cannot reject pulse debit");
                eq(h, f.shield(), shield.subtract(n("5")), "one committed shield debit"); near(h, target.getHealth(), 15, "one conserved five damage pulse");
                double paid = f.cp(); ThermalShell.drain(f.p); near(h, f.cp(), paid, "pending batch consumed exactly once");
            } });
            h.runAfterDelay(22, () -> { h.assertTrue(renewals[0] == 1 && ThermalShell.active(f.p), "one genuine renewal and pulse leave paid controller active"); h.succeed(); });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_no_cp_does_not_rollback_positive_absorption_or_emit_unpaid_damage(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie target = f.mob(0, 2); h.assertTrue(f.toggle(1), "activate with genuine budget"); f.cp(0);
            BigDecimal shield = f.shield(), hp = f.hp(); h.assertTrue(f.hit(5), "absorption committed before optional pulse payment");
            ThermalShell.drain(f.p); eq(h, f.shield(), shield.subtract(n("5")), "CP failure does not refund shield"); eq(h, f.hp(), hp, "existing S still protects HP");
            near(h, f.cp(), 0, "no negative or invented CP"); near(h, target.getHealth(), 20, "unpaid pending cannot hurt");
            h.assertTrue(ThermalShell.pulseCount(f.p) == 0 && f.xp() > 0, "genuine absorption earns bounded mastery; unpaid pulse earns no extra use"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_four_frozen_targets_share_one_total_and_fifth_target_never_receives_damage(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            List<Zombie> targets = List.of(f.mob(0, 1), f.mob(1, 1), f.mob(-1, 1), f.mob(0, 2), f.mob(0, 2.8));
            h.assertTrue(f.toggle(1) && f.hit(8), "one paid shell and positive absorption");
            for (Zombie target : targets) near(h, target.getHealth(), 20, "outer hurt never recursively attacks a selected mob");
            double cp = f.cp(), cost = ThermalShellRules.pulseCost(HighPowerGrowth.values(f.p).position()); ThermalShell.drain(f.p);
            for (int i = 0; i < 4; i++) near(h, targets.get(i).getHealth(), 18, "four exact two damage shares");
            near(h, targets.get(4).getHealth(), 20, "fifth otherwise eligible target remains untouched"); near(h, f.cp(), cp - cost, "one batch payment, not four");
            h.assertTrue(ThermalShell.pulseCount(f.p) == 1, "one bounded emission batch"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_rejected_frozen_targets_are_free_and_never_replaced_by_new_arrivals(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie old = f.mob(0, 2); h.assertTrue(f.toggle(1) && f.hit(5), "freeze original target before callbacks");
            old.setPos(f.p.position().add(0, 0, 8)); Zombie newcomer = f.mob(0, 2); double cp = f.cp(), xp = f.xp();
            ThermalShell.drain(f.p); near(h, f.cp(), cp, "all frozen opportunities rejected before payment"); near(h, f.xp(), xp, "empty pulse awards no XP");
            near(h, old.getHealth(), 20, "out-of-range frozen target rejected"); near(h, newcomer.getHealth(), 20, "no target reacquisition"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_native_knockback_motion_keeps_real_origin_without_resetting_velocity(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie target = f.mob(1, 1.5); h.assertTrue(f.toggle(1) && f.hit(5), "native close hit");
            Vec3 velocity = f.p.getDeltaMovement(); h.assertTrue(velocity.lengthSqr() > 0, "native melee produced actual owner knockback");
            f.p.setPos(f.p.position().add(velocity.scale(.5))); f.p.connection.resetPosition();
            ThermalShell.drain(f.p); near(h, target.getHealth(), 15, "bounded native movement does not suppress earned aftershock");
            h.assertTrue(f.p.getDeltaMovement().equals(velocity), "controller never erases native movement"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_callback_cancellation_spends_one_valid_attempt_without_redistribution(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie first = f.mob(0, 1), second = f.mob(0, 2); f.hook(new IncomingAction(first, () -> {}, true));
            h.assertTrue(f.toggle(1) && f.hit(6), "two frozen targets"); double cp = f.cp(), cost = ThermalShellRules.pulseCost(HighPowerGrowth.values(f.p).position());
            ThermalShell.drain(f.p); near(h, first.getHealth(), 20, "native incoming cancellation honored"); near(h, second.getHealth(), 17, "rejected share never redistributed");
            near(h, f.cp(), cp - cost, "validated paid attempt remains spent despite later cancellation"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_pulse_callback_abort_rejects_current_and_remaining_native_emissions(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie first = f.mob(0, 1), second = f.mob(0, 2); IncomingAction action = new IncomingAction(first, () -> ThermalShell.abort(f.p), false); f.hook(action);
            h.assertTrue(f.toggle(1) && f.hit(6), "positive absorption queued"); BigDecimal shield = f.shield(); double cp = f.cp(), cost = ThermalShellRules.pulseCost(HighPowerGrowth.values(f.p).position());
            ThermalShell.drain(f.p); h.assertTrue(action.called && !ThermalShell.active(f.p), "native callback ended exact owned controller");
            near(h, first.getHealth(), 20, "late guard rejects current stale permit"); near(h, second.getHealth(), 20, "remaining target never attacked");
            near(h, f.cp(), cp - cost, "no unsafe refund after paid callback"); eq(h, f.shield(), shield, "pulse completion cannot roll back prior absorption"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_pulse_callback_owner_motion_invalidates_whole_paid_batch_without_new_origin(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie first = f.mob(0, 1), second = f.mob(0, 2);
            IncomingAction moved = new IncomingAction(first, () -> f.p.setPos(f.p.position().add(.25, 0, 0)), false); f.hook(moved);
            h.assertTrue(f.toggle(1) && f.hit(6), "two frozen targets"); double cp = f.cp(), cost = ThermalShellRules.pulseCost(HighPowerGrowth.values(f.p).position());
            ThermalShell.drain(f.p); h.assertTrue(moved.called, "first native incoming callback moved caster a short distance");
            near(h, first.getHealth(), 20, "exact emission position guards current permit"); near(h, second.getHealth(), 20, "remaining slots cannot reacquire changed emission position");
            near(h, f.cp(), cp - cost, "validated batch remains paid after callback motion"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_mastery_callback_lifecycle_change_discards_already_frozen_pending(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie target = f.mob(0, 2); ExperienceAction change = new ExperienceAction(f.p, () -> AbilityStorage.get(f.p).presets.switchTo(1)); f.hook(change);
            h.assertTrue(f.toggle(1), "active"); BigDecimal shield = f.shield(); double cp = f.cp(); h.assertTrue(f.hit(5), "absorption is productive before mutable mastery callback");
            h.assertTrue(change.called, "actual XP observer changed preset"); ThermalShell.drain(f.p);
            eq(h, f.shield(), shield.subtract(n("5")), "committed protection retained"); near(h, f.cp(), cp, "stale pending does not spend");
            near(h, target.getHealth(), 20, "stale pending never emits after preset callback"); h.assertFalse(ThermalShell.active(f.p), "old controller cannot own new preset"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 110)
    public static void thermal_fifteen_tick_fence_survives_retoggle_and_native_nbt_owner_epoch_reload(GameTestHelper h) {
        Fixture f = new Fixture(h, 5); f.ready(() -> {
            Zombie target = f.mob(0, 2); h.assertTrue(f.toggle(1) && f.hit(5), "first productive hit"); ThermalShell.drain(f.p);
            CompoundTag receipt = f.p.getPersistentData().getCompound(ThermalShell.PULSE_KEY).copy(); double xp = f.xp();
            h.assertTrue(receipt.getLong("until") - receipt.getLong("observed") == 15, "physical interval is independent fixed fifteen ticks");
            h.assertTrue(f.toggle(2), "toggle ends immediate"); h.assertFalse(f.toggle(3), "exit cooldown has a persistent same tick fence");
            f.next(() -> {
                h.assertTrue(f.toggle(4), "LV5 exit cooldown ends after real tick"); double cp = f.cp(); f.hit(5); ThermalShell.drain(f.p);
                near(h, f.cp(), cp, "fresh controller cannot reset owner pulse quota"); near(h, f.xp(), xp, "fresh controller cannot farm mastery");
                ThermalShell.remove(f.p); AbilityStorage.save(f.p); CompoundTag saved = new CompoundTag(); f.p.saveWithoutId(saved);
                AbilityStorage.remove(f.p); LegacySingleKeyOwnerEpoch.forget(f.p); f.p.load(saved); f.place(); f.epoch();
                h.assertTrue(f.p.getPersistentData().getCompound(ThermalShell.PULSE_KEY).equals(receipt), "native serialization preserves exact physical receipt");
                f.next(() -> {
                    h.assertTrue(f.toggle(1), "new owner epoch after genuine exit tick"); double reloaded = f.cp(); f.hit(5); ThermalShell.drain(f.p);
                    near(h, f.cp(), reloaded, "reloaded epoch cannot reset physical cadence"); near(h, f.xp(), xp, "serialized mastery fence survives owner replacement");
                    h.runAfterDelay(14, () -> {
                        f.p.doTick(); f.place(); target.setPos(f.p.position().add(0, 0, 2)); target.setDeltaMovement(Vec3.ZERO); target.invulnerableTime = 0;
                        double fresh = f.cp(); f.hit(5); double cost = ThermalShellRules.pulseCost(HighPowerGrowth.values(f.p).position()); ThermalShell.drain(f.p);
                        near(h, f.cp(), fresh - cost, "later legitimate absorption pays again after physical owner and target fences"); h.succeed();
                    });
                });
            });
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_owner_category_epoch_dimension_and_logout_discard_pending_before_payment(GameTestHelper h) {
        Fixture[] fixtures = {new Fixture(h, 2), new Fixture(h, 2), new Fixture(h, 2), new Fixture(h, 2)};
        fixtures[0].ready(() -> {
            for (int i = 0; i < fixtures.length; i++) {
                Fixture f = fixtures[i]; f.p.doTick(); f.place(); Zombie target = f.mob(0, 2); h.assertTrue(f.toggle(1) && f.hit(4), "owned pending before lifecycle " + i); double cp = f.cp();
                if (i == 0) AbilityStorage.get(f.p).category = "electromaster";
                else if (i == 1) LegacySingleKeyOwnerEpoch.forget(f.p);
                else if (i == 2) ThermalShell.dimension(new PlayerEvent.PlayerChangedDimensionEvent(f.p, f.p.level().dimension(), net.minecraft.world.level.Level.NETHER));
                else ThermalShell.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));
                ThermalShell.drain(f.p); near(h, f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).getDouble("cp"), cp, "invalidated pending spends no CP");
                near(h, target.getHealth(), 20, "invalidated pending emits no damage"); h.assertFalse(ThermalShell.active(f.p), "lifecycle ownership is gone"); target.discard();
            }
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_short_native_connection_teleport_cancels_pending_even_inside_drift_bound(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie target = f.mob(0, 2); h.assertTrue(f.toggle(1) && f.hit(5), "owned pending"); double cp = f.cp();
            f.p.connection.teleport(f.p.getX() + .25, f.p.getY(), f.p.getZ(), f.p.getYRot(), f.p.getXRot());
            ThermalShell.drain(f.p); h.assertFalse(ThermalShell.active(f.p), "actual native teleport seam terminates old controller");
            near(h, f.cp(), cp, "teleported pending does not pay"); near(h, target.getHealth(), 20, "short teleport cannot reuse drift permission"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_pulse_respects_an_existing_shared_cp_reservation(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie target = f.mob(0, 2); h.assertTrue(f.toggle(1), "active shell"); f.cp(1);
            try (var reservation = HighPowerResources.reserve(f.p, DualPoint.ID, 1)) {
                h.assertTrue(reservation != null && reservation.valid(), "genuine other-skill reservation uses same CP ledger");
                h.assertTrue(f.hit(5), "absorption remains productive while CP reserved"); ThermalShell.drain(f.p);
                near(h, f.cp(), 1, "pulse cannot spend reserved CP"); near(h, target.getHealth(), 20, "reserved-budget failure cannot emit");
                h.assertTrue(reservation.valid(), "failed pulse does not replace original ledger");
            }
            near(h, HighPowerResources.availableCp(f.p), 1, "closing reservation reveals unchanged actual CP"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_wall_created_after_capture_rejects_frozen_target_without_cp(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie target = f.mob(0, 2.5); h.assertTrue(f.toggle(1) && f.hit(5), "original visible target frozen");
            h.setBlock(new BlockPos(6, 2, 7), Blocks.STONE); h.setBlock(new BlockPos(6, 3, 7), Blocks.STONE); double cp = f.cp(); ThermalShell.drain(f.p);
            near(h, f.cp(), cp, "new obstruction rejects whole opportunity before payment"); near(h, target.getHealth(), 20, "no through-wall aftershock"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 90)
    public static void thermal_mastery_callback_nested_damage_never_restores_outer_hp_or_reenters_pulse(GameTestHelper h) {
        Fixture f = new Fixture(h, 2); f.ready(() -> {
            Zombie target = f.mob(0, 2); h.assertTrue(f.toggle(1), "owned shell");
            ExperienceAction nested = new ExperienceAction(f.p, () -> { f.p.invulnerableTime = 0; f.p.hurt(f.p.damageSources().generic(), 3); }); f.hook(nested);
            BigDecimal hp = f.hp(), shield = f.shield(); h.assertTrue(f.hit(5), "outer committed absorption");
            h.assertTrue(nested.called, "actual mastery observer performed nested native hurt");
            eq(h, f.hp(), hp.subtract(n("3")), "outer completion does not overwrite nested ordinary injury"); eq(h, f.shield(), shield.subtract(n("5")), "one real outer shield spend");
            ThermalShell.drain(f.p); h.assertTrue(ThermalShell.pulseCount(f.p) <= 1, "nested damage cannot enqueue recursive batches");
            h.assertTrue(target.getHealth() >= 15, "total aftershock never amplified by nested callback"); h.succeed();
        });
    }

    public static final class IncomingAction {
        final LivingEntity target; final Runnable action; final boolean cancel; boolean called;
        IncomingAction(LivingEntity target, Runnable action, boolean cancel) { this.target = target; this.action = action; this.cancel = cancel; }
        @SubscribeEvent(priority = EventPriority.NORMAL) public void incoming(LivingIncomingDamageEvent e) { if (e.getEntity() == target && !called) { called = true; action.run(); if (cancel) e.setCanceled(true); } }
    }
    public static final class ExperienceAction {
        final ServerPlayer target; final Runnable action; boolean called;
        ExperienceAction(ServerPlayer target, Runnable action) { this.target = target; this.action = action; }
        @SubscribeEvent public void experience(SkillExpAddedEvent e) { if (e.getEntity() == target && e.skill.equals(ThermalShell.ID) && !called) { called = true; action.run(); } }
    }
    public static final class NativePlayerPump {
        final ServerPlayer p; final int until; int last;
        NativePlayerPump(ServerPlayer p, int duration) { this.p = p; last = p.server.getTickCount(); until = last + duration; }
        @SubscribeEvent(priority = EventPriority.HIGHEST) public void tick(ServerTickEvent.Post e) {
            if (e.getServer() != p.server) return; int tick = p.server.getTickCount();
            if (tick <= last || tick > until || p.isRemoved() || p.hasDisconnected()) return;
            last = tick; p.doTick();
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final ServerPlayer p; final Cow attacker; final EmbeddedChannel channel;
        final List<Entity> entities = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h, int level) {
            this.h = h; for (int x = 1; x <= 13; x++) for (int z = 1; z <= 15; z++) h.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "HT" + UUID.randomUUID().toString().substring(0, 10)), false);
            p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); var connection = new Connection(PacketFlow.SERVERBOUND);
            channel = new EmbeddedChannel(connection); NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie);
            p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true); p.getAbilities().instabuild = false; place();
            var state = AbilityStorage.get(p); state.selectCategory("meltdowner"); state.setLevel(level);
            for (String id : List.of(DualPoint.ID, ScatterPlates.ID, ThermalShell.ID)) state.learn(id);
            state.activated = true; state.overloadFine = true;
            h.assertTrue(HighPowerCombat.enroll(p, level), "approved connected shell owner");
            h.assertTrue(state.presets.edit(0, 0, ThermalShell.ID, id -> id.equals(ThermalShell.ID)), "genuine learned shell slot");
            HighPowerResources.initializeBudget(p); epoch();
            attacker = EntityType.COW.create(h.getLevel()); attacker.setNoAi(true); attacker.setNoGravity(true); attacker.setPos(p.position().add(0, 0, -2)); h.getLevel().addFreshEntity(attacker); entities.add(attacker);
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
                public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
            });
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { p.doTick(); place(); action.run(); }); }
        void next(Runnable action) { h.runAfterDelay(1, () -> { p.doTick(); place(); action.run(); }); }
        void place() { Vec3 at = h.absoluteVec(new Vec3(6.5, 2, 6.5)); p.moveTo(at.x, at.y, at.z, 0, 0); p.setYHeadRot(0); p.setOnGround(true); p.setDeltaMovement(Vec3.ZERO); p.connection.resetPosition(); if (attacker != null) attacker.setPos(p.position().add(0, 0, -2)); }
        Zombie mob(double x, double z) { Zombie target = EntityType.HUSK.create(h.getLevel()); target.setNoAi(true); target.setNoGravity(true); target.setSilent(true); target.setOnGround(false); target.getAttribute(Attributes.ARMOR).setBaseValue(0); target.getAttribute(Attributes.KNOCKBACK_RESISTANCE).setBaseValue(0); target.setPos(p.position().add(x, 0, z)); h.getLevel().addFreshEntity(target); entities.add(target); return target; }
        long epoch() { return LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p)); }
        boolean toggle(long input) { return ThermalShell.toggle(p, new HighPowerInput.Input(0, input, p.getId(), epoch()).wire()); }
        boolean hit(float amount) { p.invulnerableTime = 0; return p.hurt(p.damageSources().mobAttack(attacker), amount); }
        double cp() { return HighPowerResources.cp(p); } void cp(double amount) { p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", amount); }
        double xp() { return AbilityStorage.get(p).exp(ThermalShell.ID); } BigDecimal hp() { return n(HighPowerCombat.healthText(p)); } BigDecimal shield() { return HighPowerCombat.shield(p); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister); ThermalShell.remove(p); AbilityStorage.remove(p); h.getLevel().getServer().getPlayerList().remove(p); entities.forEach(Entity::discard); channel.finishAndReleaseAll(); }
    }
}
