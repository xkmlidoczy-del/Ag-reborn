package cn.academy.port.gametest;

import cn.academy.port.highpower.HighPowerResources;
import cn.academy.port.highpower.WindPressureLift;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native lookup/consumption contracts. Reuses the existing connected native fixture without
 * manufacturing proof records, mutating global proof clocks, or claiming LocalPlayer acceptance. */
@GameTestHolder("academy_wind_pressure_lift")
@PrefixGameTestTemplate(false)
public final class HighPowerWindPressureLiftLatestRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_wind_pressure_lift_latest";
    private HighPowerWindPressureLiftLatestRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_latest_unpaid_and_prepublication_are_absent_and_read_only(GameTestHelper h) {
        F f = new F(h); Object before = f.ledger();
        for (int i = 0; i < 12; i++) h.assertTrue(f.latest(1, 0) == null, "an authenticated input alone creates no proof");
        h.assertTrue(before.equals(f.ledger()), "lookup cannot charge, refund, or mutate resources");
        h.assertTrue(f.toggle(1), "declared integrated-contract fixture pays native startup");
        Object paid = f.ledger();
        h.assertTrue(f.latest(1, 0) == null, "successful startup still waits for accepted-position publication");
        h.assertTrue(paid.equals(f.ledger()), "prepublication lookup cannot publish or pay again");
        var grant = f.publish();
        h.assertTrue(grant != null && f.latest(1, 0) == grant && f.latest(1, grant.token()) == grant,
                "lookup returns the exact immutable server publication after payment");
        f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_latest_rejects_every_captured_identity_mismatch(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid native setup"); var grant = f.publish(); Object paid = f.ledger();
        h.assertTrue(WindPressureLift.latestIntegrated(null, grant.entity(), grant.owner(), grant.input(), grant.epoch(), grant.dimension(), 0) == null, "absent or unrelated server authority cannot look up a proof");
        h.assertTrue(WindPressureLift.latestIntegrated(f.p.server, grant.entity() + 1, grant.owner(), grant.input(), grant.epoch(), grant.dimension(), 0) == null, "different entity cannot bootstrap");
        h.assertTrue(WindPressureLift.latestIntegrated(f.p.server, grant.entity(), UUID.randomUUID(), grant.input(), grant.epoch(), grant.dimension(), 0) == null, "different UUID cannot bootstrap");
        h.assertTrue(WindPressureLift.latestIntegrated(f.p.server, grant.entity(), grant.owner(), grant.input() + 1, grant.epoch(), grant.dimension(), 0) == null, "different input cannot bootstrap");
        h.assertTrue(WindPressureLift.latestIntegrated(f.p.server, grant.entity(), grant.owner(), grant.input(), grant.epoch() + 1, grant.dimension(), 0) == null, "different owner epoch cannot bootstrap");
        h.assertTrue(WindPressureLift.latestIntegrated(f.p.server, grant.entity(), grant.owner(), grant.input(), grant.epoch(), ResourceLocation.fromNamespaceAndPath("academy", "unrelated_lookup_world"), 0) == null, "different world cannot bootstrap");
        h.assertTrue(f.latest(1, grant.token() + 1) == null && f.latest(1, -1) == null, "a wrong bound or malformed token cannot select a controller");
        h.assertTrue(WindPressureLift.latestIntegrated(f.p.server, grant.entity(), null, grant.input(), grant.epoch(), grant.dimension(), 0) == null, "null identity is refused");
        h.assertTrue(WindPressureLift.latestIntegrated(f.p.server, grant.entity(), grant.owner(), 0, grant.epoch(), grant.dimension(), 0) == null, "zero input is refused");
        h.assertTrue(f.latest(1, grant.token()) == grant && paid.equals(f.ledger()), "rejected lookups leave genuine proof and resources unchanged");
        f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_latest_observation_never_consumes_and_consumption_never_refills_same_tick(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid native setup"); var grant = f.publish(); Object paid = f.ledger();
        for (int i = 0; i < 12; i++) h.assertTrue(f.latest(1, 0) == grant && f.latest(1, grant.token()) == grant, "repeated client-frame observations return one unchanged permission");
        h.assertTrue(WindPressureLift.consumeIntegrated(grant, f.p.server), "observation leaves one atomic consumption opportunity");
        for (int i = 0; i < 12; i++) {
            f.publish();
            h.assertTrue(f.latest(1, 0) == null && f.latest(1, grant.token()) == null, "duplicate Post and low-FPS input loops cannot refill a consumed same-tick proof");
            h.assertFalse(WindPressureLift.consumeIntegrated(grant, f.p.server), "old permission cannot replay after lookup consumption");
        }
        h.assertTrue(paid.equals(f.ledger()), "reads and replays never add fees or refunds"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=15)
    public static void wind_lift_latest_preserves_real_pre_post_empty_window_and_replaces_old_sequence(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid native setup"); var first = f.publish();
        PreWindow witness = new PreWindow(f, first); f.hook(witness);
        f.after(1, () -> {
            h.assertTrue(witness.observed, "actual next native ServerTick.Pre was observed after the controller clock update");
            var latest = f.latest(1, first.token());
            h.assertTrue(latest != null && latest.sequence() > first.sequence() && latest.issuedAt() == first.issuedAt() + 1,
                    "actual next Post publishes exactly its current newer sequence");
            h.assertTrue(latest.expiresAt() == latest.issuedAt() + 1, "lookup never widens the original strict TTL");
            h.assertFalse(WindPressureLift.consumeIntegrated(first, f.p.server), "a cached proof from before Pre is never revived");
            h.assertTrue(f.latest(1, first.token()) == latest && WindPressureLift.consumeIntegrated(latest, f.p.server), "only the latest publication can consume");
            h.assertTrue(f.latest(1, 0) == null, "newly consumed latest proof also disappears"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_latest_peek_then_cancel_cannot_resurrect_or_consume(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid native setup"); f.publish(); var captured = f.latest(1, 0); double paid = f.cp();
        WindPressureLift.abort(f.p);
        h.assertTrue(f.latest(1, 0) == null && f.latest(1, captured.token()) == null, "cancellation removes both bootstrap and bound lookup authority");
        h.assertFalse(WindPressureLift.consumeIntegrated(captured, f.p.server), "cancel between observation and consumption fails atomically");
        f.publish(); h.assertTrue(f.latest(1, 0) == null && f.cp() == paid, "read/duplicate publication cannot restart a canceled paid controller or refund it"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=10)
    public static void wind_lift_latest_peek_then_small_native_teleport_revokes_before_next_tick(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid native setup"); f.publish(); var captured = f.latest(1, 0); double paid = f.cp();
        Vec3 destination = f.p.position().add(.1, 0, 0);
        f.p.connection.teleport(destination.x, destination.y, destination.z, f.p.getYRot(), f.p.getXRot());
        h.assertTrue(f.p.position().equals(destination), "ordinary native teleport remains authoritative");
        h.assertTrue(f.latest(1, 0) == null && f.latest(1, captured.token()) == null, "native HEAD invalidation removes lookup authority immediately");
        h.assertFalse(WindPressureLift.consumeIntegrated(captured, f.p.server), "a pre-teleport observation cannot consume after the native discontinuity");
        f.drain(); h.assertTrue(f.latest(1, 0) == null && f.cp() == paid, "native acknowledgement cannot revive proof or refund a fee"); f.succeed();
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=25)
    public static void wind_lift_latest_old_input_or_bound_token_cannot_select_fresh_paid_session(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid native setup"); var old = f.publish();
        h.assertTrue(f.toggle(2), "fresh free-off retires first controller");
        f.after(10, () -> {
            double before = f.cp(); h.assertTrue(f.toggle(3), "actual remaining restart and cooldown ticks have aged");
            h.assertTrue(Math.abs(f.cp() - (before - .5)) < 1e-9, "fresh input pays a full new startup fee");
        });
        f.after(11, () -> {
            var current = f.latest(3, 0);
            h.assertTrue(current != null && current.token() > old.token(), "new native Post exposes only the new paid session");
            h.assertTrue(f.latest(1, 0) == null && f.latest(1, old.token()) == null && f.latest(3, old.token()) == null,
                    "old input and old token cannot follow a later controller");
            h.assertFalse(WindPressureLift.consumeIntegrated(old, f.p.server), "old cached permission remains spent across restart");
            h.assertTrue(f.latest(3, current.token()) == current, "only exact new input plus token resolves"); f.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)
    public static void wind_lift_latest_unpaid_renewal_never_yields_a_new_permission(GameTestHelper h) {
        F f = new F(h); h.assertTrue(f.toggle(1), "paid native setup"); var first = f.publish();
        f.hook(new Object() { @SubscribeEvent(priority=EventPriority.HIGHEST) public void post(ServerTickEvent.Post event) {
            if (event.getServer() == f.p.server) f.p.getPersistentData().getCompound(HighPowerResources.DATA_KEY).putDouble("cp", 0);
        } });
        f.after(19, () -> h.assertTrue(f.latest(1, first.token()) != null, "existing paid window still has a current proof before expiry"));
        f.after(20, () -> {
            h.assertTrue(f.latest(1, 0) == null && f.latest(1, first.token()) == null, "failed real renewal cannot produce latest permission");
            h.assertFalse(WindPressureLift.consumeIntegrated(first, f.p.server), "failed renewal cannot replay initial proof");
            h.assertTrue(f.cp() == 0 && !WindPressureLift.active(f.p), "lookup never subsidizes exhausted CP or resurrects the controller"); f.succeed();
        });
    }

    public static final class PreWindow {
        final F f; final WindPressureLift.Grant prior; final int next; boolean observed;
        PreWindow(F f, WindPressureLift.Grant prior) { this.f = f; this.prior = prior; next = f.p.server.getTickCount() + 1; }
        @SubscribeEvent(priority=EventPriority.LOWEST) public void pre(ServerTickEvent.Pre event) {
            if (observed || event.getServer() != f.p.server || f.p.server.getTickCount() != next) return;
            observed = true;
            try {
                f.h.assertTrue(f.latest(1, 0) == null && f.latest(1, prior.token()) == null, "Pre expires the last proof until accepted-position Post publication");
                f.h.assertFalse(WindPressureLift.consumeIntegrated(prior, f.p.server), "Pre cannot spend last tick's proof");
            } catch (Throwable failure) { f.fail(failure); }
        }
    }

    /** Reflection is confined to reuse of the existing private native fixture; it grants no production authority. */
    private static final class F {
        final GameTestHelper h; final Object fixture; final ServerPlayer p;
        F(GameTestHelper h) {
            this.h = h;
            try {
                Class<?> type = Class.forName("cn.academy.port.gametest.HighPowerWindPressureLiftRuntimeTests$Fixture");
                Constructor<?> constructor = type.getDeclaredConstructor(GameTestHelper.class); constructor.setAccessible(true);
                fixture = constructor.newInstance(h); Field owner = type.getDeclaredField("p"); owner.setAccessible(true); p = (ServerPlayer) owner.get(fixture);
            } catch (InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        Object call(String name, Class<?>[] signature, Object... values) {
            try { Method method = fixture.getClass().getDeclaredMethod(name, signature); method.setAccessible(true); return method.invoke(fixture, values); }
            catch (InvocationTargetException failure) { throw new AssertionError(failure.getCause()); }
            catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
        }
        WindPressureLift.Grant latest(long input, long token) {
            return WindPressureLift.latestIntegrated(p.server, p.getId(), p.getUUID(), input,
                    (long) call("epoch", new Class<?>[0]), p.serverLevel().dimension().location(), token);
        }
        boolean toggle(long input) { return (boolean) call("toggle", new Class<?>[]{long.class}, input); }
        WindPressureLift.Grant publish() { return (WindPressureLift.Grant) call("publish", new Class<?>[0]); }
        double cp() { return (double) call("cp", new Class<?>[0]); }
        Object ledger() { return call("ledger", new Class<?>[0]); }
        void after(int ticks, Runnable action) { call("afterPosts", new Class<?>[]{int.class, Runnable.class}, ticks, action); }
        void hook(Object observer) { call("hook", new Class<?>[]{Object.class}, observer); }
        void drain() { call("drain", new Class<?>[0]); }
        void succeed() { call("succeed", new Class<?>[0]); }
        void fail(Throwable failure) { call("fail", new Class<?>[]{Throwable.class}, failure); }
    }
}
