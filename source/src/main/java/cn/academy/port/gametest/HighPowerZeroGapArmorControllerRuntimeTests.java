package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import static cn.academy.port.gametest.ZeroGapArmorNativeFixture.*;

/** NEW252 native input/payment/lifecycle and finite-contact contract. Never advances a fake physical clock. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerZeroGapArmorControllerRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_zero_gap_controller";

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=55)
    public static void native_toggle_release_six_tick_startup_and_twenty_tick_upkeep(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0);
        double cp = f.cp(), xp = f.mastery(); var before = f.armor().toDecimalMap(); BigDecimal health = f.hp();
        h.assertTrue(f.toggle(1), "native toggle ingress starts252");
        near(h, f.cp(), cp - .05, "LV5 activation costs exactly0.05CP");
        h.assertTrue(ZeroGapArmor.holding(f.p) && !ZeroGapArmor.active(f.p) && ZeroGapArmor.blocksRecovery(f.p), "paid startup exists without physical protection");
        h.assertTrue(ZeroGapArmor.release(f.p, f.wire(1)) && ZeroGapArmor.holding(f.p), "key release is harmless for a toggle");
        h.assertFalse(f.toggle(1), "duplicate press cannot toggle off or pay again");
        near(h, f.cp(), cp - .05, "replay and key-up cannot debit");
        var witness = new PaymentWitness(f); f.hook(witness);
        h.runAfterDelay(5, () -> h.assertFalse(ZeroGapArmor.active(f.p), "the fifth native startup tick remains unprotected"));
        h.runAfterDelay(6, () -> h.assertTrue(ZeroGapArmor.active(f.p), "the sixth native startup tick becomes protective"));
        h.runAfterDelay(42, () -> {
            h.assertTrue(witness.charges == 2 && witness.exact && witness.spacing, "native upkeep charges exactly1CP at two20tick boundaries");
            h.assertTrue(f.armor().toDecimalMap().equals(before), "startup and idle upkeep do not refill, redistribute or spend any shield child");
            eq(h, f.hp(), health, "activation never heals"); near(h, f.mastery(), xp, "idle toggle earns no mastery");
            double paid = f.cp(); h.assertTrue(f.toggle(2), "second fresh press switches off");
            near(h, f.cp(), paid, "fresh toggle-off is free");
            h.assertFalse(ZeroGapArmor.holding(f.p) || ZeroGapArmor.blocksRecovery(f.p), "toggle-off retires controller and recovery block");
            h.assertFalse(f.toggle(3), "same-tick recast cannot bypass the paid stop cooldown");
            int stoppedAt = f.p.server.getTickCount(), phase = f.timing().phaseTick();
            h.runAfterDelay(1, () -> {
                h.assertTrue(f.p.server.getTickCount() == stoppedAt + 1 && f.timing().phaseTick() == (phase + 1) % 120,
                        "exactly one genuine physical boundary elapsed after paid toggle-off");
                h.assertTrue(AbilityStorage.get(f.p).cooldowns.getOrDefault(ZeroGapArmor.ID, 0) == 0,
                        "the genuine native player stage clears its one-tick ability cooldown before ingress");
                h.assertTrue(f.toggle(4), "fresh input can pay again after actual LV5 cooldown"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void malformed_owner_epoch_slot_and_replay_inputs_never_spend(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0); double cp = f.cp(); long epoch = f.epoch();
        for (String bad : List.of("", "0:01:" + f.p.getId() + ":" + epoch, "0:1:" + (f.p.getId() + 1) + ":" + epoch,
                "0:1:" + f.p.getId() + ":" + (epoch + 1), f.wire(1, 1), "0:0:" + f.p.getId() + ":" + epoch)) {
            h.assertFalse(f.request("hp_zero_gap_armor_toggle", bad), "bad input rejected before payment: " + bad);
        }
        near(h, f.cp(), cp, "all malformed or unowned attempts leave CP unchanged");
        h.assertTrue(f.toggle(2), "fresh correct input succeeds after refused ingress");
        h.assertFalse(ZeroGapArmor.abort(f.p, f.wire(1)), "stale abort cannot close newer activation");
        h.assertTrue(ZeroGapArmor.holding(f.p), "new activation survives stale abort");
        h.assertTrue(ZeroGapArmor.abort(f.p, f.wire(2)), "matching explicit abort closes startup");
        h.assertFalse(f.toggle(2), "aborted input cannot be replayed");
        h.assertTrue(f.p.getPersistentData().contains(ZeroGapArmor.PAYMENT_KEY), "durable payment/cooldown evidence survives abort");
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void exclusive_scatter_and_curtain_conflicts_refuse_before_payment_in_both_directions(GameTestHelper h) {
        var all = fixtures(h, 4);
        h.assertTrue(ScatterPlates.toggle(all[0].p, all[0].wire(1, 1)), "paid212 starts first");
        h.assertTrue(DissipationCurtain.start(all[1].p, all[1].wire(2, 1)), "paid242 startup starts first");
        for (int i = 0; i < 2; i++) {
            double paid = all[i].cp(); h.assertFalse(all[i].toggle(1), "252 refuses conflicting paid defense " + i);
            near(h, all[i].cp(), paid, "conflict costs no252CP " + i);
        }
        all[2].start(); all[3].start();
        double scatterCp = all[2].cp(), curtainCp = all[3].cp();
        h.assertFalse(ScatterPlates.toggle(all[2].p, all[2].wire(1, 1)), "paid252 startup excludes212 activation");
        h.assertFalse(DissipationCurtain.start(all[3].p, all[3].wire(2, 1)), "paid252 startup excludes242 activation");
        near(h, all[2].cp(), scatterCp, "212 conflict cannot charge"); near(h, all[3].cp(), curtainCp, "242 conflict cannot charge");
        h.assertTrue(ZeroGapArmor.holding(all[2].p) && ZeroGapArmor.holding(all[3].p), "rejected competitors do not cancel252"); h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void menu_category_preset_death_logout_and_owner_epoch_retire_startup(GameTestHelper h) {
        var all = fixtures(h, 6);
        for (int mode = 0; mode < all.length; mode++) {
            var f = all[mode]; f.start(); var shares = f.armor().toDecimalMap(); var timing = f.timing();
            switch (mode) {
                case 0 -> f.p.containerMenu = ChestMenu.threeRows(37, f.p.getInventory());
                case 1 -> AbilityStorage.get(f.p).category = HighPowerCategories.ELECTROMASTER;
                case 2 -> AbilityStorage.get(f.p).presets.edit(0, 0, "", ignored -> true);
                case 3 -> f.p.setHealth(0);
                case 4 -> ZeroGapArmor.logout(new PlayerEvent.PlayerLoggedOutEvent(f.p));
                default -> { LegacySingleKeyOwnerEpoch.forget(f.p); f.epoch(); }
            }
            ZeroGapArmor.tick(f.p);
            h.assertFalse(ZeroGapArmor.holding(f.p) || ZeroGapArmor.active(f.p), "lifecycle change closes252 " + mode);
            h.assertTrue(ZeroGapArmor.paidUntil(f.p) == 0, "paid controller handle retired " + mode);
            h.assertTrue(f.armor().toDecimalMap().equals(shares) && f.timing().equals(timing), "lifecycle cleanup cannot reset children, rotation or debt " + mode);
            h.assertTrue(f.p.getPersistentData().contains(ZeroGapArmor.PAYMENT_KEY), "cooldown evidence persists " + mode);
            if (mode == 0) f.p.containerMenu = f.p.inventoryMenu;
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=145)
    public static void physical_rotation_advances_once_per_real_tick_even_while_released_and_in_other_category(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0); int initial = f.timing().phaseTick();
        f.start();
        for (int i = 0; i < 20; i++) { ZeroGapArmor.tick(f.p); ZeroGapArmor.release(f.p, f.wire(1)); f.pose(); }
        h.assertTrue(f.timing().phaseTick() == initial, "input, reads and repeated controller ticks cannot advance physical phase");
        h.runAfterDelay(7, () -> {
            h.assertTrue(f.timing().phaseTick() == (initial + 7) % 120, "seven real ticks advance seven phase units");
            h.assertTrue(f.toggle(2), "toggle off after startup");
            AbilityStorage.get(f.p).category = HighPowerCategories.ELECTROMASTER;
        });
        h.runAfterDelay(39, () -> {
            h.assertTrue(f.timing().phaseTick() == (initial + 39) % 120, "inactive other-category owner still rotates physically");
            var before = f.timing(); var shares = f.armor().toDecimalMap(); f.reload();
            h.assertTrue(f.timing().equals(before) && f.armor().toDecimalMap().equals(shares), "native save/load retains exact phase and child identities");
            h.assertFalse(ZeroGapArmor.holding(f.p), "relog cannot restore a paid activation");
        });
        h.runAfterDelay(120, () -> { h.assertTrue(f.timing().phaseTick() == initial, "one revolution takes exactly120 genuine server ticks"); h.succeed(); });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void global_break_debt_survives_release_category_reload_and_only_four_real_ticks_pay_it(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0); f.start();
        h.runAfterDelay(6, () -> {
            var bases = new ArrayList<>(copies("20")); bases.set(f.front().id(), BigDecimal.ZERO);
            f.seed(bases, copies("0"));
            h.assertTrue(f.timing().breakTicks() == 4 && !ZeroGapArmor.active(f.p) && ZeroGapArmor.blocksOtherDefense(f.p), "one actual funded child exhaustion opens the whole armor");
            int phase = f.timing().phaseTick();
            for (int i = 0; i < 30; i++) { ZeroGapArmor.tick(f.p); ZeroGapArmor.release(f.p, f.wire(1)); }
            h.assertTrue(f.timing().breakTicks() == 4 && f.timing().phaseTick() == phase, "read/release/controller spam pays no physical debt");
            h.assertTrue(f.toggle(2), "fresh toggle off is allowed during debt");
            AbilityStorage.get(f.p).category = HighPowerCategories.ELECTROMASTER;
            var saved = f.timing(); f.reload(); h.assertTrue(f.timing().equals(saved), "category switch and actual native save/load cannot clear debt");
            for (int elapsed = 1; elapsed <= 4; elapsed++) {
                final int ticks = elapsed;
                h.runAfterDelay(ticks, () -> {
                    h.assertTrue(f.timing().breakTicks() == 4 - ticks && f.timing().phaseTick() == (phase + ticks) % 120, "only genuine native boundary repays break tick " + ticks);
                    h.assertTrue(ZeroGapArmor.blocksOtherDefense(f.p) == (ticks < 4), "same-source MD exclusion remains for all four tick intervals " + ticks);
                    if (ticks == 4) h.succeed();
                });
            }
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void finite_current_phase_rectangles_keep_gaps_rear_inside_and_height_open_without_autotracking(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0); var attacker = new ZeroGapArmorNativeFixture(h, 14); f.start();
        h.runAfterDelay(6, () -> {
            var pose = f.pose(); var saved = f.p.getPersistentData().copy(); Vec3 body = f.center(); int admitted = 0;
            for (var plate : pose.plates()) {
                Vec3 normal = vec(plate.normal());
                var capture = ZeroGapArmor.captureBeam(f.p, attacker.p, body.add(normal.scale(8)), body, f.p);
                h.assertTrue((capture != null) == plate.active(), "only the three actually facing numbered plates admit center crossings " + plate.id());
                if (capture != null) { admitted++; h.assertTrue(capture.plateIndex() == plate.id() && ZeroGapArmor.matches(f.p, capture), "current finite plate identity retained"); }
            }
            h.assertTrue(admitted == 3, "exactly three of six finite rectangles protect");
            var plate = f.front(); Vec3 normal = vec(plate.normal()), side = vec(ZeroGapArmorGeometry.frame(plate).right());
            h.assertTrue(ZeroGapArmor.captureBeam(f.p, attacker.p, body.add(normal.scale(.8)), body, f.p) == null, "inside start crosses no outer plate");
            Vec3 above = body.add(0, .66, 0);
            h.assertTrue(ZeroGapArmor.captureBeam(f.p, attacker.p, above.add(normal.scale(8)), above, f.p) == null, "finite top edge does not become a tall cone");
            Vec3 off = body.add(side.scale(.451));
            h.assertTrue(ZeroGapArmor.captureBeam(f.p, attacker.p, off.add(normal.scale(1.6)), off, f.p) == null, "true side gap remains beyond the0.9m width");
            double between = ZeroGapArmorGeometry.plateYawDegrees(pose.phaseTick(), plate.id()) + 30;
            Vec3 gap = new Vec3(-Math.sin(Math.toRadians(between)), 0, Math.cos(Math.toRadians(between)));
            h.assertTrue(ZeroGapArmor.captureBeam(f.p, attacker.p, body.add(gap.scale(8)), body, f.p) == null, "the real thirty-degree interplate gap reaches the body");
            h.assertTrue(f.pose().equals(pose) && f.p.getPersistentData().equals(saved), "incoming direction never retargets plates or mutates persistent state");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=25)
    public static void finite_capture_rechecks_world_los_current_facing_and_immutable_component_ceilings(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0); var attacker = new ZeroGapArmorNativeFixture(h, 14);
        f.seed(copies("2"), copies("3")); f.start();
        h.runAfterDelay(6, () -> {
            var plate = f.front(); Vec3 body = f.center(), origin = body.add(vec(plate.normal()).scale(8));
            var capture = ZeroGapArmor.captureBeam(f.p, attacker.p, origin, body, f.p);
            h.assertTrue(capture != null, "real finite beam crossing admits current plate");
            eq(h, capture.baseCeiling(), new BigDecimal("2"), "capture freezes exact base component"); eq(h, capture.reserveCeiling(), new BigDecimal("3"), "capture freezes exact assigned reserve component");
            f.seed(copies("10"), copies("20"));
            eq(h, capture.baseCeiling(), new BigDecimal("2"), "later credit cannot enlarge captured base"); eq(h, capture.reserveCeiling(), new BigDecimal("3"), "later credit cannot enlarge captured reserve");
            f.p.setYRot(f.p.getYRot() + 180); h.assertFalse(ZeroGapArmor.matches(f.p, capture), "a now-rear numbered plate cannot settle historical evidence");
            f.p.setYRot(0); f.wall(body.add(vec(plate.normal()).scale(4)));
            h.assertTrue(ZeroGapArmor.captureBeam(f.p, attacker.p, origin, body, f.p) == null, "nearest solid wall prevents a new finite capture");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void failed_upkeep_closes_without_overdraw_mastery_or_child_repartition(GameTestHelper h) {
        var f = new ZeroGapArmorNativeFixture(h, 0); f.start(); var shares = f.armor().toDecimalMap(); boolean[] refused = {false};
        f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void pre(ServerTickEvent.Pre event) {
            if (event.getServer() != f.p.server || !ZeroGapArmor.renewalPending(f.p)) return;
            f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 0);
            ZeroGapArmor.tick(f.p); refused[0] = !ZeroGapArmor.holding(f.p); near(h, f.cp(), 0, "failed exact upkeep cannot overdraw");
        }});
        h.runAfterDelay(22, () -> {
            h.assertTrue(refused[0] && !ZeroGapArmor.holding(f.p), "unpaid boundary closes the paid toggle");
            near(h, f.mastery(), 0, "failed upkeep earns no mastery");
            h.assertTrue(f.armor().toDecimalMap().equals(shares), "failed upkeep changes no parent or child allocation"); h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=15)
    public static void missing_or_corrupt_children_fail_closed_without_activation_repair_or_payment(GameTestHelper h) {
        var all = fixtures(h, 3);
        for (int i = 0; i < all.length; i++) {
            var f = all[i]; var combat = f.p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
            if (i == 0) combat.remove(ZeroGapArmorStorage.KEY);
            else if (i == 1) combat.getCompound(ZeroGapArmorStorage.KEY).putString("base0", "-1");
            else combat.getCompound(ZeroGapArmorStorage.TIMING_KEY).putString("phaseTick", "120");
            var saved = combat.copy(); double cp = f.cp();
            h.assertFalse(f.toggle(1), "invalid persisted child/timing refuses activation " + i);
            near(h, f.cp(), cp, "refused invalid storage costs no CP " + i);
            h.assertTrue(combat.equals(saved), "activation never repairs, resets or fills malformed persistent metadata " + i);
        }
        h.succeed();
    }

    public static final class PaymentWitness {
        final ZeroGapArmorNativeFixture f; long until, lastCharge; double cp; int charges; boolean exact = true, spacing = true;
        PaymentWitness(ZeroGapArmorNativeFixture f) { this.f = f; until = ZeroGapArmor.paidUntil(f.p); lastCharge = f.p.server.getTickCount(); }
        @SubscribeEvent(priority=EventPriority.HIGHEST) public void before(ServerTickEvent.Pre event) {
            if (event.getServer() == f.p.server) { until = ZeroGapArmor.paidUntil(f.p); cp = f.cp(); }
        }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void after(ServerTickEvent.Pre event) {
            if (event.getServer() != f.p.server) return; long next = ZeroGapArmor.paidUntil(f.p);
            if (next > until) {
                charges++; exact &= Math.abs(f.cp() - (cp - 1)) < 1e-8;
                spacing &= next - until == 20 && f.p.server.getTickCount() - lastCharge == 20; lastCharge = f.p.server.getTickCount();
                double paid = f.cp(); ZeroGapArmor.tick(f.p); exact &= f.cp() == paid;
            }
        }
    }
}
