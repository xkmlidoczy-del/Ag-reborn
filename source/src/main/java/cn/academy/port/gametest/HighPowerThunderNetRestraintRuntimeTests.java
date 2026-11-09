package cn.academy.port.gametest;

import cn.academy.port.AbilityStorage;
import cn.academy.port.core.LegacySingleKeyServerIdentity;
import cn.academy.port.highpower.HighPowerCombat;
import cn.academy.port.highpower.ThunderNetRestraint;
import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeMap;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/** Native NEW-141 attribute composition, payment preflight, ownership and bounded cleanup acceptance. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerThunderNetRestraintRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_thunder_net_restraint";
    private static long token;
    private HighPowerThunderNetRestraintRuntimeTests() {}

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_exact_thirty_percent_preserves_native_effects_attributes_and_inertia(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); Zombie target = f.zombie(); Object controller = f.controller();
        AttributeInstance speed = speed(target); speed.setBaseValue(.42);
        AttributeModifier other = new AttributeModifier(id("other_speed"), .07, AttributeModifier.Operation.ADD_VALUE);
        AttributeModifier base = new AttributeModifier(id("base_speed"), .15, AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
        AttributeModifier total = new AttributeModifier(id("total_speed"), .25, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        speed.addPermanentModifier(other); speed.addTransientModifier(base); speed.addTransientModifier(total);
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SPEED, 400, 1));
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 350, 0));
        var speedEffect = target.getEffect(MobEffects.MOVEMENT_SPEED); var slowEffect = target.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        var beforeModifiers = speed.getModifiers(); double before = speed.getValue();
        target.setNoGravity(false); Vec3 motion = new Vec3(.23, -.31, .17); target.setDeltaMovement(motion); target.fallDistance = 9;
        double gravity = target.getGravity(); boolean impulse = target.hasImpulse, marked = target.hurtMarked;
        f.claim(controller, f.identity(p), target, () -> true);
        near(h, speed.getValue(), before * .7, "one native total multiplier removes exactly thirty percent");
        near(h, speed.getBaseValue(), .42, "base speed untouched");
        h.assertTrue(speed.getModifiers().size() == beforeModifiers.size() + 1, "exactly one new modifier");
        for (var modifier : beforeModifiers) h.assertTrue(speed.getModifier(modifier.id()) == modifier, "every preexisting modifier retains object identity");
        h.assertTrue(target.getEffect(MobEffects.MOVEMENT_SPEED) == speedEffect && speedEffect.getDuration() == 400 && speedEffect.getAmplifier() == 1,
                "existing Speed instance, duration and amplifier untouched");
        h.assertTrue(target.getEffect(MobEffects.MOVEMENT_SLOWDOWN) == slowEffect && slowEffect.getDuration() == 350 && slowEffect.getAmplifier() == 0,
                "existing Slowness instance, duration and amplifier untouched");
        h.assertTrue(target.getDeltaMovement().equals(motion) && target.hasImpulse == impulse && target.hurtMarked == marked && !target.isNoGravity(),
                "no velocity reset, movement sync flag or gravity override");
        near(h, target.getGravity(), gravity, "gravity attribute untouched"); near(h, target.fallDistance, 9, "fall distance untouched");
        // Changes made by another system while restrained must survive removal too; never restore a cached value.
        speed.setBaseValue(.6); ThunderNetRestraint.releaseController(controller);
        near(h, speed.getBaseValue(), .6, "external base-speed edit survives cleanup");
        h.assertTrue(speed.getModifier(other.id()) == other && speed.getModifier(base.id()) == base && speed.getModifier(total.id()) == total,
                "external modifiers retained after cleanup");
        h.assertTrue(speed.getModifier(ThunderNetRestraint.MODIFIER_ID) == null && target.getDeltaMovement().equals(motion), "cleanup only removes owned speed modifier");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_two_casters_share_one_modifier_and_cancel_independently(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer one = f.player(), two = f.player(); Zombie target = f.zombie();
        Object first = f.controller(), second = f.controller(); AtomicBoolean secondValid = new AtomicBoolean(true);
        double initial = speed(target).getValue();
        f.claim(first, f.identity(one), target, () -> true); AttributeModifier owned = speed(target).getModifier(ThunderNetRestraint.MODIFIER_ID);
        f.claim(second, f.identity(two), target, secondValid::get);
        h.assertTrue(speed(target).getModifier(ThunderNetRestraint.MODIFIER_ID) == owned, "second caster shares original exact modifier object");
        near(h, speed(target).getValue(), initial * .7, "multiple casters never compound slowdown");
        ThunderNetRestraint.releaseController(first); near(h, speed(target).getValue(), initial * .7, "first cancel leaves second caster restrained");
        h.assertTrue(ThunderNetRestraint.hold(second, target), "surviving caster can refresh its own lease");
        h.assertFalse(ThunderNetRestraint.hold(first, target), "cancelled controller cannot refresh another caster's lease");
        secondValid.set(false); ThunderNetRestraint.tick(h.getLevel().getServer());
        near(h, speed(target).getValue(), initial, "last invalid target predicate removes the single modifier immediately");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_foreign_same_id_is_never_deleted_or_replaced(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); var identity = f.identity(p); Zombie target = f.zombie(); Object controller = f.controller();
        AttributeModifier foreign = new AttributeModifier(ThunderNetRestraint.MODIFIER_ID, -.30, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        speed(target).addTransientModifier(foreign);
        h.assertFalse(ThunderNetRestraint.available(identity, target), "preexisting same-ID modifier refuses before payment");
        h.assertTrue(ThunderNetRestraint.prepare(controller, identity, target, f.now(), () -> true) == null, "collision cannot prepare a claim");
        ThunderNetRestraint.releaseController(controller);
        h.assertTrue(speed(target).getModifier(ThunderNetRestraint.MODIFIER_ID) == foreign, "unknown same-ID modifier is untouched");
        speed(target).removeModifier(ThunderNetRestraint.MODIFIER_ID);
        f.claim(controller, identity, target, () -> true);
        AttributeModifier owned = speed(target).getModifier(ThunderNetRestraint.MODIFIER_ID);
        speed(target).removeModifier(ThunderNetRestraint.MODIFIER_ID); speed(target).addTransientModifier(foreign);
        h.assertTrue(owned != foreign && owned.equals(foreign), "equal native record values still have different ownership identity");
        h.assertFalse(ThunderNetRestraint.hold(controller, target), "foreign equal-value replacement invalidates ownership");
        ThunderNetRestraint.releaseController(controller); ThunderNetRestraint.tick(h.getLevel().getServer());
        h.assertTrue(speed(target).getModifier(ThunderNetRestraint.MODIFIER_ID) == foreign, "cleanup never removes or restores over foreign replacement");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_failed_and_invalidated_claims_never_call_payment_or_write_movement(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); var identity = f.identity(p); Zombie a = f.zombie(), b = f.zombie(); Object controller = f.controller();
        AtomicInteger payments = new AtomicInteger(); AtomicBoolean allowed = new AtomicBoolean(true);
        var one = ThunderNetRestraint.prepare(controller, identity, a, f.now(), () -> true);
        var two = ThunderNetRestraint.prepare(controller, identity, b, f.now(), allowed::get);
        h.assertTrue(one != null && two != null, "complete eligible batch prepared without movement write");
        allowed.set(false);
        h.assertFalse(ThunderNetRestraint.commitPaid(List.of(one, two), () -> { payments.incrementAndGet(); return true; }), "later invalid target refuses whole batch");
        h.assertTrue(payments.get() == 0 && speed(a).getModifier(ThunderNetRestraint.MODIFIER_ID) == null, "no partial restraint or CP callback on invalid batch");
        var unpaid = ThunderNetRestraint.prepare(controller, identity, a, f.now(), () -> true);
        h.assertFalse(ThunderNetRestraint.commitPaid(List.of(unpaid), () -> { payments.incrementAndGet(); return false; }), "shared CP refusal");
        h.assertTrue(payments.get() == 1 && speed(a).getModifier(ThunderNetRestraint.MODIFIER_ID) == null, "failed payment does not acquire restraint");
        h.assertFalse(ThunderNetRestraint.commitPaid(List.of(unpaid), () -> { payments.incrementAndGet(); return true; }), "consumed failed-payment claim cannot replay");
        h.assertTrue(payments.get() == 1, "replay cannot call payment again");
        var collision = ThunderNetRestraint.prepare(controller, identity, a, f.now(), () -> true);
        AttributeModifier foreign = new AttributeModifier(ThunderNetRestraint.MODIFIER_ID, .05, AttributeModifier.Operation.ADD_VALUE);
        speed(a).addTransientModifier(foreign);
        h.assertFalse(ThunderNetRestraint.commitPaid(List.of(collision), () -> { payments.incrementAndGet(); return true; }), "post-preflight collision detected before debit");
        h.assertTrue(payments.get() == 1 && speed(a).getModifier(ThunderNetRestraint.MODIFIER_ID) == foreign, "foreign state never overwritten");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_six_target_and_sixteen_lease_hard_caps_are_unpaid_refusals(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); var identity = f.identity(p); Object controller = f.controller();
        List<Zombie> mobs = new ArrayList<>(); List<ThunderNetRestraint.Claim> claims = new ArrayList<>(); AtomicInteger payments = new AtomicInteger();
        for (int i = 0; i < 7; i++) { Zombie target = f.zombie(); mobs.add(target); claims.add(ThunderNetRestraint.prepare(controller, identity, target, f.now(), () -> true)); }
        h.assertFalse(ThunderNetRestraint.commitPaid(claims, () -> { payments.incrementAndGet(); return true; }), "seventh target refuses entire batch");
        h.assertTrue(payments.get() == 0, "over-limit batch unpaid");
        h.assertTrue(ThunderNetRestraint.commitPaid(claims.subList(0, 6), () -> { payments.incrementAndGet(); return true; }), "six targets pay only once");
        ThunderNetRestraint.release(controller, mobs.getFirst());
        h.assertTrue(ThunderNetRestraint.prepare(controller, identity, mobs.get(6), f.now(), () -> true) == null, "releasing one cannot recycle bounded admission budget");
        Zombie shared = f.zombie(); double base = speed(shared).getValue();
        for (int i = 0; i < ThunderNetRestraint.MAX_LEASES_PER_TARGET; i++) f.claim(f.controller(), f.identity(p), shared, () -> true);
        near(h, speed(shared).getValue(), base * .7, "sixteen live controllers still apply only one thirty-percent modifier");
        h.assertTrue(ThunderNetRestraint.prepare(f.controller(), f.identity(p), shared, f.now(), () -> true) == null, "seventeenth lease explicitly refused");
        h.assertTrue(payments.get() == 1, "bounded six-target batch had exactly one payment"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_player_passive_and_unreviewed_subclass_fail_closed(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(), other = f.player(); var identity = f.identity(p); Object controller = f.controller();
        Mob cow = f.place(EntityType.COW.create(h.getLevel())); Zombie subclass = f.place(new Zombie(EntityType.ZOMBIE, h.getLevel()) {});
        Vec3 impulse = new Vec3(.4, -.2, .3); other.setDeltaMovement(impulse); double base = speed(other).getValue();
        for (LivingEntity unsupported : List.of(p, other, cow, subclass)) {
            h.assertFalse(ThunderNetRestraint.available(identity, unsupported), "unsupported target has no server-controlled native restraint contract");
            h.assertTrue(ThunderNetRestraint.prepare(controller, identity, unsupported, f.now(), () -> true) == null, "unsupported target refused before payment");
        }
        near(h, speed(other).getValue(), base, "no player movement attribute change");
        h.assertTrue(other.getDeltaMovement().equals(impulse) && other.getActiveEffects().isEmpty(), "no player velocity reset or potion substitute");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_replaced_attribute_instance_never_receives_cached_state(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); Zombie target = f.zombie(); Object controller = f.controller();
        f.claim(controller, f.identity(p), target, () -> true); AttributeInstance original = speed(target);
        AttributeInstance replacement = new AttributeInstance(Attributes.MOVEMENT_SPEED, ignored -> {}); replacement.setBaseValue(.91);
        AttributeModifier foreign = new AttributeModifier(ThunderNetRestraint.MODIFIER_ID, -.08, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL);
        replacement.addTransientModifier(foreign); replaceSpeed(target, replacement);
        target.getAttributes().getAttributesToSync().remove(original); target.getAttributes().getAttributesToUpdate().remove(original);
        h.assertFalse(ThunderNetRestraint.hold(controller, target), "exact AttributeInstance replacement ends the old lease");
        h.assertTrue(speed(target) == replacement && replacement.getModifier(ThunderNetRestraint.MODIFIER_ID) == foreign, "replacement instance and foreign modifier untouched");
        near(h, replacement.getBaseValue(), .91, "never restore original base value into replacement");
        h.assertTrue(original.getModifier(ThunderNetRestraint.MODIFIER_ID) != null, "detached old instance is never mutated through a stale reference");
        h.assertFalse(target.getAttributes().getAttributesToSync().contains(original) || target.getAttributes().getAttributesToUpdate().contains(original),
                "detached cleanup never fires old AttributeMap dirty callback or queues stale native sync");
        h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_target_reload_cannot_inherit_transient_or_stale_entity_lease(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); Zombie target = f.zombie(); Object controller = f.controller();
        f.claim(controller, f.identity(p), target, () -> true);
        CompoundTag saved = new CompoundTag(); target.saveWithoutId(saved);
        h.assertFalse(saved.toString().contains(ThunderNetRestraint.MODIFIER_ID.toString()), "transient restraint never enters native saved attributes");
        int oldId = target.getId(); UUID oldUuid = target.getUUID(); Vec3 at = target.position(); target.discard();
        Zombie replacement = new Zombie(EntityType.ZOMBIE, h.getLevel()); replacement.load(saved); replacement.setId(oldId); replacement.setUUID(oldUuid); replacement.setPos(at);
        h.assertTrue(h.getLevel().addFreshEntity(replacement), "new exact native object reuses saved UUID and entity ID"); f.mobs.add(replacement);
        h.assertTrue(speed(target).getModifier(ThunderNetRestraint.MODIFIER_ID) == null, "entity leave cleans the old live object");
        h.assertTrue(speed(replacement).getModifier(ThunderNetRestraint.MODIFIER_ID) == null, "reloaded entity has no lingering transient modifier");
        h.assertFalse(ThunderNetRestraint.hold(controller, replacement), "matching UUID and ID cannot impersonate captured entity object");
        h.assertFalse(ThunderNetRestraint.hold(controller, target), "removed original cannot be refreshed either"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_dead_logout_category_owner_and_dead_target_cleanup(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer first = f.player(), second = f.player(), third = f.player(); Zombie target = f.zombie();
        Object one = f.controller(), two = f.controller(), three = f.controller(); double base = speed(target).getValue();
        f.claim(one, f.identity(first), target, () -> true); f.claim(two, f.identity(second), target, () -> true); f.claim(three, f.identity(third), target, () -> true);
        first.setHealth(0); ThunderNetRestraint.tick(h.getLevel().getServer());
        h.assertFalse(ThunderNetRestraint.hold(one, target), "dead caster's lease cannot survive"); near(h, speed(target).getValue(), base * .7, "other live owners survive first death");
        ThunderNetRestraint.logout(new PlayerEvent.PlayerLoggedOutEvent(second));
        h.assertFalse(ThunderNetRestraint.hold(two, target), "logout immediately releases exact owner's lease"); near(h, speed(target).getValue(), base * .7, "third owner still holds single restraint");
        AbilityStorage.get(third).selectCategory("meltdowner"); ThunderNetRestraint.tick(h.getLevel().getServer());
        near(h, speed(target).getValue(), base, "last owner's category change cleans without velocity writes");
        ServerPlayer live = f.player(); Object last = f.controller(); f.claim(last, f.identity(live), target, () -> true);
        target.setHealth(0); ThunderNetRestraint.tick(h.getLevel().getServer());
        h.assertTrue(speed(target).getModifier(ThunderNetRestraint.MODIFIER_ID) == null, "dead target cleaned even without damage-event delivery"); h.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 15)
    public static void thunder_restraint_unrefreshed_orphan_expires_within_two_ticks(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); Zombie target = f.zombie(); Object controller = f.controller();
        f.claim(controller, f.identity(p), target, () -> true); double base = speed(target).getBaseValue();
        h.runAfterDelay(3, () -> {
            h.assertTrue(speed(target).getModifier(ThunderNetRestraint.MODIFIER_ID) == null, "automatic server tick retires orphan without backend or owner ticks");
            near(h, speed(target).getValue(), base, "native speed restored by removal only");
            h.assertFalse(ThunderNetRestraint.hold(controller, target), "expired lease cannot revive through unpaid hold"); h.succeed();
        });
    }

    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 75)
    public static void thunder_restraint_continuous_refresh_cannot_exceed_sixty_tick_controller_bound(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); Zombie target = f.zombie(); Object controller = f.controller();
        f.claim(controller, f.identity(p), target, () -> true);
        for (int i = 1; i < 60; i++) h.runAfterDelay(i, () -> h.assertTrue(ThunderNetRestraint.hold(controller, target), "valid owner can refresh inside absolute bounded lifetime"));
        h.runAfterDelay(60, () -> {
            h.assertFalse(ThunderNetRestraint.hold(controller, target), "absolute sixty-tick bound defeats continual refresh");
            h.assertTrue(speed(target).getModifier(ThunderNetRestraint.MODIFIER_ID) == null, "expiry removes final owned modifier"); h.succeed();
        });
    }

    // Separate batch: global lifecycle cleanup must not disrupt independently running acceptance fixtures.
    @GameTest(template = TEMPLATE, batch = "highpower_thunder_net_restraint_stop", timeoutTicks = 15)
    public static void thunder_restraint_server_stop_cleanup_preserves_foreign_replacement(GameTestHelper h) {
        Fixture f = new Fixture(h); ServerPlayer p = f.player(); Zombie a = f.zombie(), b = f.zombie();
        Object one = f.controller(), two = f.controller(); f.claim(one, f.identity(p), a, () -> true); f.claim(two, f.identity(p), b, () -> true);
        AttributeModifier foreign = new AttributeModifier(ThunderNetRestraint.MODIFIER_ID, .02, AttributeModifier.Operation.ADD_VALUE);
        speed(b).removeModifier(ThunderNetRestraint.MODIFIER_ID); speed(b).addTransientModifier(foreign);
        ThunderNetRestraint.clearAll();
        h.assertTrue(speed(a).getModifier(ThunderNetRestraint.MODIFIER_ID) == null, "stop cleanup removes every owned transient modifier");
        h.assertTrue(speed(b).getModifier(ThunderNetRestraint.MODIFIER_ID) == foreign, "stop cleanup does not delete foreign same-ID replacement");
        h.assertFalse(ThunderNetRestraint.hold(one, a), "cleared controller cannot refresh"); h.succeed();
    }

    private static ResourceLocation id(String suffix) { return ResourceLocation.fromNamespaceAndPath("academy", "thunder_net_test_" + suffix); }
    private static AttributeInstance speed(LivingEntity entity) { return entity.getAttribute(Attributes.MOVEMENT_SPEED); }
    private static void near(GameTestHelper h, double actual, double expected, String message) {
        h.assertTrue(Math.abs(actual - expected) < 1e-9, message + ": " + actual + " != " + expected);
    }
    @SuppressWarnings("unchecked")
    private static void replaceSpeed(LivingEntity entity, AttributeInstance replacement) {
        try {
            Field field = AttributeMap.class.getDeclaredField("attributes"); field.setAccessible(true);
            ((Map<Holder<Attribute>, AttributeInstance>) field.get(entity.getAttributes())).put(Attributes.MOVEMENT_SPEED, replacement);
        } catch (ReflectiveOperationException error) { throw new AssertionError("native replacement test fixture unavailable", error); }
    }

    private record Caster(ServerPlayer player, EmbeddedChannel channel) {}
    private static final class Fixture {
        final GameTestHelper h;
        final List<Caster> players = new ArrayList<>();
        final List<Mob> mobs = new ArrayList<>();
        final List<Object> controllers = new ArrayList<>();
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
        ServerPlayer player() {
            var server = h.getLevel().getServer();
            var cookie = CommonListenerCookie.createInitial(new GameProfile(UUID.randomUUID(), "HPNetLease" + players.size()), false);
            ServerPlayer p = new ServerPlayer(server, h.getLevel(), cookie.gameProfile(), cookie.clientInformation());
            Connection connection = new Connection(PacketFlow.SERVERBOUND); EmbeddedChannel channel = new EmbeddedChannel(connection);
            NetworkRegistry.configureMockConnection(connection); server.getPlayerList().placeNewPlayer(connection, p, cookie); players.add(new Caster(p, channel));
            p.setGameMode(GameType.SURVIVAL); p.setPos(h.absoluteVec(new Vec3(2.5 + players.size(), 3, 2.5))); p.setNoGravity(true);
            h.assertTrue(HighPowerCombat.enroll(p, 5), "reviewed native caster");
            var state = AbilityStorage.get(p); state.selectCategory("electromaster"); state.setLevel(5); state.learn("thunder_net"); state.activated = true; state.overloadFine = true;
            return p;
        }
        LegacySingleKeyServerIdentity identity(ServerPlayer p) { return new LegacySingleKeyServerIdentity(p, 0, ++token, AbilityStorage.get(p)); }
        Object controller() { Object result = new Object(); controllers.add(result); return result; }
        Zombie zombie() { return place(new Zombie(EntityType.ZOMBIE, h.getLevel())); }
        <T extends Mob> T place(T target) {
            h.assertTrue(target != null, "native fixture target exists"); target.setPos(h.absoluteVec(new Vec3(2.5 + mobs.size() % 6, 3, 6.5 + mobs.size() / 6)));
            target.setNoAi(true); target.setNoGravity(true); target.setPersistenceRequired();
            h.assertTrue(h.getLevel().addFreshEntity(target), "native target is current world entity"); mobs.add(target); return target;
        }
        long now() { return h.getLevel().getServer().overworld().getGameTime(); }
        void claim(Object controller, LegacySingleKeyServerIdentity identity, LivingEntity target, BooleanSupplier valid) {
            var claim = ThunderNetRestraint.prepare(controller, identity, target, now(), valid);
            h.assertTrue(claim != null, "eligible native target prepares lease");
            h.assertTrue(ThunderNetRestraint.commitPaid(List.of(claim), () -> true), "paid native lease commits");
        }
        void close() {
            if (closed) return; closed = true;
            controllers.forEach(ThunderNetRestraint::releaseController);
            mobs.forEach(Mob::discard);
            for (Caster caster : players) {
                AbilityStorage.remove(caster.player); h.getLevel().getServer().getPlayerList().remove(caster.player); caster.channel.finishAndReleaseAll();
            }
        }
    }
}
