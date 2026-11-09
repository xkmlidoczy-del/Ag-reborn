package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyOwnerEpoch;
import cn.academy.port.highpower.*;
import cn.academy.port.highpower.core.DissipationCurtainGeometry;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.math.BigDecimal;
import java.util.*;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.*;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Paid startup/upkeep and owner lifecycle; actual incoming damage is covered by separate native tests. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDissipationCurtainControllerRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_dissipation_curtain_controller";
    private static void eq(GameTestHelper h, BigDecimal a, BigDecimal b, String why) { h.assertTrue(a.compareTo(b) == 0, why + ": " + a + " != " + b); }
    private static void near(GameTestHelper h, double a, double b, String why) { h.assertTrue(Math.abs(a - b) < 1e-8, why + ": " + a + " != " + b); }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 50)
    public static void startup_is_six_ticks_then_real_twenty_tick_upkeep_without_idle_mastery_or_refill(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(6);
        double cp = HighPowerResources.cp(p), xp = AbilityStorage.get(p).exp(DissipationCurtain.ID);
        BigDecimal base = HighPowerCombat.shield(p); String health = HighPowerCombat.healthText(p);
        h.assertTrue(DissipationCurtain.start(p, f.wire(p, 1)), "one authenticated paid hold begins");
        near(h, HighPowerResources.cp(p), cp - .5, "LV4 activation has exact approved cost");
        h.assertTrue(DissipationCurtain.holding(p) && !DissipationCurtain.active(p), "startup is held but provides no protection");
        h.assertTrue(DissipationCurtain.blocksRecovery(p), "paid startup suppresses shared shield recovery");
        double paid = HighPowerResources.cp(p); h.assertFalse(DissipationCurtain.start(p, f.wire(p, 1)), "duplicate start is fenced");
        near(h, HighPowerResources.cp(p), paid, "duplicate packet cannot spend twice");
        PaymentWitness witness = new PaymentWitness(p); f.hook(witness);
        h.runAfterDelay(5, () -> h.assertFalse(DissipationCurtain.active(p), "fifth startup tick remains nonprotective"));
        h.runAfterDelay(6, () -> { DissipationCurtain.tick(p); h.assertTrue(DissipationCurtain.active(p), "sixth tick activates held facets"); });
        h.runAfterDelay(42, () -> {
            h.assertTrue(witness.charges == 2 && witness.exact && witness.spacing, "exactly one CP each20ticks, repeated tick never duplicates payment");
            near(h, AbilityStorage.get(p).exp(DissipationCurtain.ID), xp, "idle holding never awards mastery");
            eq(h, HighPowerCombat.shield(p), base, "activation/upkeep never grant shield");
            h.assertTrue(HighPowerCombat.healthText(p).equals(health), "activation/upkeep never heal");
            h.assertTrue(DissipationCurtain.release(p, f.wire(p, 1)), "matching release closes held curtain");
            h.assertFalse(DissipationCurtain.holding(p) || DissipationCurtain.blocksRecovery(p), "release clears controller and recovery block"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void release_abort_and_recast_keep_input_and_paid_cooldown_fences(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(6); BigDecimal base = HighPowerCombat.shield(p);
        h.assertTrue(DissipationCurtain.start(p, f.wire(p, 1)), "initial hold");
        h.assertTrue(DissipationCurtain.release(p, f.wire(p, 1)), "early release aborts startup");
        h.assertFalse(DissipationCurtain.start(p, f.wire(p, 2)), "paid cooldown prevents immediate recast");
        h.assertFalse(DissipationCurtain.start(p, f.wire(p, 1)), "old start cannot bypass watermark");
        h.runAfterDelay(11, () -> {
            h.assertTrue(DissipationCurtain.start(p, f.wire(p, 3)), "fresh input may start after cooldown");
            h.assertFalse(DissipationCurtain.release(p, f.wire(p, 1)), "stale release cannot stop newer activation");
            h.assertTrue(DissipationCurtain.holding(p), "new activation survives old packet");
            h.assertTrue(DissipationCurtain.abort(p, f.wire(p, 3)), "current explicit abort closes hold");
            eq(h, HighPowerCombat.shield(p), base, "recast never initializes or refills a shield reserve"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void menu_category_preset_death_and_logout_cancel_paid_startup(GameTestHelper h) {
        Fixture f = new Fixture(h);
        for (int mode = 0; mode < 5; mode++) {
            ServerPlayer p = f.player(6 + mode * 12); h.assertTrue(DissipationCurtain.start(p, f.wire(p, 1)), "paid fixture " + mode);
            switch (mode) {
                case 0 -> p.containerMenu = ChestMenu.threeRows(37, p.getInventory());
                case 1 -> AbilityStorage.get(p).category = HighPowerCategories.ELECTROMASTER;
                case 2 -> AbilityStorage.get(p).presets.edit(0, 0, "", ignored -> true);
                case 3 -> p.setHealth(0);
                default -> DissipationCurtain.logout(new PlayerEvent.PlayerLoggedOutEvent(p));
            }
            DissipationCurtain.tick(p);
            h.assertFalse(DissipationCurtain.holding(p) || DissipationCurtain.active(p), "lifecycle invalidation closes controller " + mode);
            h.assertTrue(DissipationCurtain.paidUntil(p) == 0, "lifecycle removes paid handle " + mode);
            h.assertTrue(p.getPersistentData().contains(DissipationCurtain.PAYMENT_KEY), "durable payment/cooldown evidence survives " + mode);
            if (mode == 0) p.containerMenu = p.inventoryMenu;
        }
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void finite_crossing_guard_rejects_solid_los_and_captures_never_survive_release(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(6), attacker = f.player(20);
        h.assertTrue(DissipationCurtain.start(p, f.wire(p, 1)), "held curtain paid");
        h.runAfterDelay(6, () -> {
            DissipationCurtain.tick(p); Vec3 eye = p.getEyePosition(), front = eye.add(0, 0, 8);
            var capture = DissipationCurtain.captureBeam(p, attacker, front, eye, p);
            h.assertTrue(capture != null && capture.plateIndex() == 1 && DissipationCurtain.matches(p, capture), "actual finite front crossing has one authenticated facet");
            h.assertTrue(DissipationCurtain.captureBeam(p, attacker, eye.add(0, 0, 4), eye, p) == null, "inside start bypasses finite front plane");
            h.assertTrue(DissipationCurtain.captureBeam(p, attacker, eye.add(0, 0, -8), eye, p) == null, "rear start bypasses");
            BlockPos wall = BlockPos.containing(eye.add(0, 0, 3)); h.getLevel().setBlockAndUpdate(wall, Blocks.STONE.defaultBlockState());
            h.assertTrue(DissipationCurtain.captureBeam(p, attacker, front, eye, p) == null, "solid defender-to-facet LOS refuses a new crossing");
            h.getLevel().setBlockAndUpdate(wall, Blocks.AIR.defaultBlockState());
            h.assertTrue(DissipationCurtain.release(p, f.wire(p, 1)), "release ends activation");
            h.assertFalse(DissipationCurtain.matches(p, capture), "old opaque evidence cannot survive release"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 30)
    public static void failed_renewal_closes_without_spending_or_creating_xp(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(6); h.assertTrue(DissipationCurtain.start(p, f.wire(p, 1)), "paid hold");
        double xp = AbilityStorage.get(p).exp(DissipationCurtain.ID); boolean[] refused = {false};
        f.hook(new Object() { @SubscribeEvent(priority = EventPriority.HIGHEST) public void pre(ServerTickEvent.Pre e) {
            if (e.getServer() != p.server || !DissipationCurtain.renewalPending(p)) return;
            p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 0);
            DissipationCurtain.tick(p); refused[0] = !DissipationCurtain.holding(p);
            near(h, HighPowerResources.cp(p), 0, "failed renewal cannot overdraw or mint CP");
        } });
        h.runAfterDelay(22, () -> {
            h.assertTrue(refused[0] && !DissipationCurtain.holding(p), "unpaid renewal closes at exact paid boundary");
            near(h, AbilityStorage.get(p).exp(DissipationCurtain.ID), xp, "failed upkeep awards no XP"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void contact_and_validation_are_read_only_and_callback_reentry_fails_closed(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(6), attacker = f.player(20);
        h.assertTrue(DissipationCurtain.start(p, f.wire(p, 1)), "held curtain paid");
        h.runAfterDelay(6, () -> {
            DissipationCurtain.tick(p); Vec3 eye = p.getEyePosition(), front = eye.add(0, 0, 8);
            String reentrantWire = f.wire(p, 2);
            var state = AbilityStorage.get(p); var saved = p.getPersistentData().copy(); Map<String, Object> callbacks = new HashMap<>();
            String[] names = {"calculation", "consumptionValidity", "activationChanged", "categoryChanged", "beforeEarnedMutation", "earnedExperience", "skillExpChanged", "skillExpAdded"};
            try {
                for (String name : names) { var field = state.getClass().getDeclaredField(name); field.setAccessible(true); callbacks.put(name, field.get(state)); }
                var capture = DissipationCurtain.captureBeam(p, attacker, front, eye, p);
                h.assertTrue(capture != null && DissipationCurtain.matches(p, capture), "read-only native contact evidence");
                var contextField = capture.getClass().getDeclaredField("context"); contextField.setAccessible(true); Object context = contextField.get(capture);
                var validating = context.getClass().getDeclaredField("validating"); validating.setAccessible(true);
                validating.setBoolean(context, true);
                try {
                    h.assertTrue(DissipationCurtain.captureBeam(p, attacker, front, eye, p) == null, "guard callback cannot recursively capture another contact");
                    h.assertFalse(DissipationCurtain.matches(p, capture), "guard callback cannot settle older contact before validation completes");
                    h.assertFalse(DissipationCurtain.start(p, reentrantWire), "guard callback cannot replace its processing activation");
                } finally { validating.setBoolean(context, false); }
                var planning = context.getClass().getDeclaredField("planning"); planning.setAccessible(true); planning.setBoolean(context, true);
                try { h.assertFalse(DissipationCurtain.matches(p, capture), "exact plan reentry has no second claim"); }
                finally { planning.setBoolean(context, false); }
                for (String name : names) { var field = state.getClass().getDeclaredField(name); field.setAccessible(true); h.assertTrue(field.get(state) == callbacks.get(name), "observer does not rebind " + name); }
                h.assertTrue(p.getPersistentData().equals(saved), "capture, matches and refused reentry never write persistent resources");
            } catch (ReflectiveOperationException invalid) { throw new AssertionError(invalid); }
            h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 20)
    public static void same_tick_exhaustion_then_credit_never_revives_a_broken_activation(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(6), attacker = f.player(20);
        h.assertTrue(DissipationCurtain.start(p, f.wire(p, 1)), "paid curtain");
        h.runAfterDelay(6, () -> {
            DissipationCurtain.tick(p); Vec3 eye = p.getEyePosition();
            var capture = DissipationCurtain.captureBeam(p, attacker, eye.add(0, 0, 8), eye, p);
            h.assertTrue(capture != null, "initial certified contact"); BigDecimal base = HighPowerCombat.shield(p);
            var ability = AbilityStorage.get(p); ability.category = HighPowerCategories.ELECTROMASTER;
            try { h.assertTrue(HighPowerCombat.spendShield(p, base), "real shared-base expenditure empties eligible curtain budget"); }
            finally { ability.category = HighPowerCategories.MELTDOWNER; }
            // Declared adversarial credit fixture: emulate a separate same-tick recovery callback.
            p.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putString("shield", base.toPlainString());
            h.assertFalse(DissipationCurtain.matches(p, capture), "empty generation stays invalid even after same-tick credit");
            h.assertFalse(DissipationCurtain.active(p), "credit never restarts depleted held curtain");
            DissipationCurtain.tick(p); h.assertTrue(DissipationCurtain.paidUntil(p) == 0, "next controller tick closes depleted activation"); h.succeed();
        });
    }

    public static final class PaymentWitness {
        final ServerPlayer p; long until, lastCharge; double cp; int charges; boolean exact = true, spacing = true;
        PaymentWitness(ServerPlayer p) { this.p = p; until = DissipationCurtain.paidUntil(p); lastCharge = p.server.getTickCount(); }
        @SubscribeEvent(priority = EventPriority.HIGHEST) public void before(ServerTickEvent.Pre e) {
            if (e.getServer() != p.server) return; until = DissipationCurtain.paidUntil(p); cp = HighPowerResources.cp(p);
        }
        @SubscribeEvent(priority = EventPriority.LOWEST) public void after(ServerTickEvent.Pre e) {
            if (e.getServer() != p.server) return; long next = DissipationCurtain.paidUntil(p);
            if (next > until) { charges++; exact &= Math.abs(HighPowerResources.cp(p) - (cp - 1)) < 1e-8;
                spacing &= next - until == 20 && p.server.getTickCount() - lastCharge == 20; lastCharge = p.server.getTickCount();
                double paid = HighPowerResources.cp(p); DissipationCurtain.tick(p); exact &= HighPowerResources.cp(p) == paid; }
        }
    }
    private static final class Fixture {
        final GameTestHelper h; final List<ServerPlayer> players = new ArrayList<>(); final List<EmbeddedChannel> channels = new ArrayList<>(); final List<Object> hooks = new ArrayList<>(); boolean closed;
        Fixture(GameTestHelper h) { this.h = h; h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo i) {} public void testPassed(GameTestInfo i, GameTestRunner r) { close(); }
            public void testFailed(GameTestInfo i, GameTestRunner r) { close(); } public void testAddedForRerun(GameTestInfo i, GameTestInfo n, GameTestRunner r) { close(); }
        }); }
        ServerPlayer player(double x) {
            var server = h.getLevel().getServer(); var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "HPCurtainCtl"), false);
            var p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation()); players.add(p);
            var connection = new Connection(PacketFlow.SERVERBOUND); channels.add(new EmbeddedChannel(connection)); NetworkRegistry.configureMockConnection(connection);
            server.getPlayerList().placeNewPlayer(connection, p, cookie); p.setGameMode(GameType.SURVIVAL); p.setNoGravity(true);
            p.setPos(h.absoluteVec(new Vec3(x, 2, 6))); p.connection.resetPosition(); p.setYRot(0); p.setXRot(0);
            var s = AbilityStorage.get(p); s.changeCategoryClassic(HighPowerCategories.MELTDOWNER); s.setLevel(4);
            for (String id : List.of(DualPoint.ID, ScatterPlates.ID, DissipationCurtain.ID)) s.learn(id);
            s.setActivateState(true); s.overloadFine = true; s.presets.edit(0, 0, DissipationCurtain.ID, s::learned);
            h.assertTrue(HighPowerCombat.enroll(p, 4), "native LV4 fixture enrolled"); HighPowerResources.initializeBudget(p);
            hook(new HighPowerMagneticDomainRuntimeTests.NativePlayerPump(p, 70)); return p;
        }
        String wire(ServerPlayer p, long input) { return new HighPowerInput.Input(0, input, p.getId(), LegacySingleKeyOwnerEpoch.current(p, p.connection, p.serverLevel(), AbilityStorage.get(p))).wire(); }
        void hook(Object hook) { hooks.add(hook); NeoForge.EVENT_BUS.register(hook); }
        void close() { if (closed) return; closed = true; hooks.forEach(NeoForge.EVENT_BUS::unregister);
            for (ServerPlayer p : players) { DissipationCurtain.remove(p); AbilityStorage.remove(p); h.getLevel().getServer().getPlayerList().remove(p); }
            channels.forEach(EmbeddedChannel::finishAndReleaseAll); }
    }
}
