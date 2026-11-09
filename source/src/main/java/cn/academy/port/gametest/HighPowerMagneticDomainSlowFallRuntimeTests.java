package cn.academy.port.gametest;

import cn.academy.port.gametest.HighPowerMagneticDomainRuntimeTests.Fixture;
import cn.academy.port.highpower.MagneticDomain;
import cn.academy.port.highpower.MagneticDomainSlowFall;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** NEW152 native effect ownership, callback fencing, milk and actual vanilla gravity witnesses. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerMagneticDomainSlowFallRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_magnetic_domain_slowfall";
    private HighPowerMagneticDomainSlowFallRuntimeTests() {}

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_slow_fall_only_descends_without_direct_motion_or_fall_distance_writes(GameTestHelper h) {
        Fixture f = new Fixture(h), other = new Fixture(h);
        f.ready(() -> {
            Object key = lease(f);
            h.assertTrue(f.p instanceof MagneticDomainSlowFall.NativeGuard, "native commit mixin is installed");
            f.p.setOnGround(false); f.p.setDeltaMovement(0, .2, 0);
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertFalse(f.p.hasEffect(MobEffects.SLOW_FALLING), "ascending receives no slow falling");
            falling(f.p); f.p.fallDistance = 9;
            Vec3 motion = f.p.getDeltaMovement();
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            MobEffectInstance effect = f.p.getEffect(MobEffects.SLOW_FALLING);
            h.assertTrue(effect != null && effect.getDuration() == 2 && effect.getAmplifier() == 0, "native two-tick amplifier-zero effect");
            h.assertTrue(f.p.getDeltaMovement().equals(motion) && f.p.fallDistance == 9, "refresh does not write velocity or fall distance");
            h.assertFalse(other.p.hasEffect(MobEffects.SLOW_FALLING), "nearby connected player receives no effect");
            MagneticDomainSlowFall.release(f.p, key);
            h.assertTrue(f.p.getEffect(MobEffects.SLOW_FALLING) == effect && effect.getDuration() == 2, "release only stops refresh");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_ascending_stops_refresh_and_owned_effect_expires_within_two_native_ticks(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Object key = lease(f); falling(f.p);
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertTrue(f.p.hasEffect(MobEffects.SLOW_FALLING), "descending pulse installed");
            for (int tick = 0; tick < 2; tick++) {
                f.p.setOnGround(false); f.p.setDeltaMovement(0, .2, 0);
                MagneticDomainSlowFall.refresh(f.p, key, () -> true);
                f.p.doTick();
            }
            h.assertFalse(f.p.hasEffect(MobEffects.SLOW_FALLING), "ascending never extends the prior two-tick pulse");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_native_expiry_allows_reacquisition_but_preexisting_potion_yields_entire_lease(GameTestHelper h) {
        Fixture f = new Fixture(h), preexisting = new Fixture(h);
        f.ready(() -> {
            Object key = lease(f), foreignKey = lease(preexisting);
            falling(f.p); MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            MobEffectInstance owned = f.p.getEffect(MobEffects.SLOW_FALLING);
            h.assertTrue(owned != null, "owned effect installed");
            f.p.doTick(); f.p.doTick(); f.p.doTick();
            h.assertFalse(f.p.hasEffect(MobEffects.SLOW_FALLING), "actual native tick expires the two-tick effect");
            falling(f.p); MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertTrue(f.p.getEffect(MobEffects.SLOW_FALLING) != null && f.p.getEffect(MobEffects.SLOW_FALLING) != owned, "native expiry does not surrender the lease");

            MobEffectInstance potion = new MobEffectInstance(MobEffects.SLOW_FALLING, 2, 1);
            preexisting.p.addEffect(potion); falling(preexisting.p);
            MagneticDomainSlowFall.refresh(preexisting.p, foreignKey, () -> true);
            h.assertTrue(preexisting.p.getEffect(MobEffects.SLOW_FALLING) == potion && potion.getAmplifier() == 1 && potion.getDuration() == 2, "preexisting potion untouched");
            preexisting.p.doTick(); preexisting.p.doTick(); preexisting.p.doTick();
            falling(preexisting.p); MagneticDomainSlowFall.refresh(preexisting.p, foreignKey, () -> true);
            h.assertFalse(preexisting.p.hasEffect(MobEffects.SLOW_FALLING), "expired preexisting potion does not hand ownership back");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_foreign_added_in_place_merge_and_explicit_remove_surrender_ownership(GameTestHelper h) {
        Fixture f = new Fixture(h), removed = new Fixture(h), equal = new Fixture(h);
        f.ready(() -> {
            Object key = lease(f), removeKey = lease(removed), equalKey = lease(equal);
            falling(f.p); MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            MobEffectInstance original = f.p.getEffect(MobEffects.SLOW_FALLING);
            f.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 200, 0));
            h.assertTrue(f.p.getEffect(MobEffects.SLOW_FALLING) == original && original.getDuration() == 200, "vanilla foreign potion merges in place");
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertTrue(original.getDuration() == 200 && original.isVisible(), "foreign merged potion is not rewritten");
            h.assertFalse(MagneticDomainSlowFall.active(f.p, key), "same object identity does not imply continued ownership");
            f.p.removeEffect(MobEffects.SLOW_FALLING);
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertFalse(f.p.hasEffect(MobEffects.SLOW_FALLING), "foreign removal cannot trigger reacquisition");

            falling(removed.p); MagneticDomainSlowFall.refresh(removed.p, removeKey, () -> true);
            h.assertTrue(removed.p.removeEffect(MobEffects.SLOW_FALLING), "actual native remove succeeds");
            MagneticDomainSlowFall.refresh(removed.p, removeKey, () -> true);
            h.assertFalse(removed.p.hasEffect(MobEffects.SLOW_FALLING), "removal of an owned effect yields the remaining lease");

            falling(equal.p); MagneticDomainSlowFall.refresh(equal.p, equalKey, () -> true);
            MobEffectInstance equalOwned = equal.p.getEffect(MobEffects.SLOW_FALLING);
            equal.p.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 2, 0, false, false, false), equal.p);
            h.assertTrue(equal.p.getEffect(MobEffects.SLOW_FALLING) == equalOwned && equalOwned.getDuration() == 2, "foreign same-source identical potion leaves native object and fields unchanged");
            h.assertFalse(MagneticDomainSlowFall.active(equal.p, equalKey), "Added event relinquishes ownership even when every public effect field and source matches");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_real_milk_cure_is_never_undone_by_refresh(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Object key = lease(f); falling(f.p);
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertTrue(f.p.hasEffect(MobEffects.SLOW_FALLING), "effect before drinking milk");
            Items.MILK_BUCKET.finishUsingItem(new ItemStack(Items.MILK_BUCKET), f.p.serverLevel(), f.p);
            h.assertFalse(f.p.hasEffect(MobEffects.SLOW_FALLING), "real native milk removes the effect");
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertFalse(f.p.hasEffect(MobEffects.SLOW_FALLING), "same lease cannot undo milk");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_added_callback_abort_cannot_commit_after_paid_context_ends(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            AddedAction hook = new AddedAction(f.p, event -> MagneticDomain.abort(f.p)); f.hook(hook);
            double before = f.cp(); h.assertTrue(f.toggle(1), "paid domain activation");
            falling(f.p); MagneticDomain.tick(f.p);
            h.assertTrue(hook.called, "real native Added callback reached");
            h.assertFalse(MagneticDomain.active(f.p), "callback ended the actual paid context");
            h.assertFalse(f.p.hasEffect(MobEffects.SLOW_FALLING), "post-event commit fence prevents orphan effect");
            h.assertTrue(Math.abs(f.cp() - (before - MagneticDomain.CP_COST)) < 1e-6, "callback abort never refunds the paid window");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_nested_foreign_added_keeps_foreign_effect_without_outer_overwrite(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Object key = lease(f); falling(f.p);
            MobEffectInstance foreign = new MobEffectInstance(MobEffects.SLOW_FALLING, 220, 1);
            AddedAction hook = new AddedAction(f.p, event -> f.p.addEffect(foreign)); f.hook(hook);
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertTrue(hook.called && f.p.getEffect(MobEffects.SLOW_FALLING) == foreign, "nested foreign addition survives the outer native null snapshot");
            h.assertTrue(foreign.getDuration() == 220 && foreign.getAmplifier() == 1, "outer domain cannot overwrite nested potion");
            h.assertFalse(MagneticDomainSlowFall.active(f.p, key), "foreign addition surrendered domain ownership");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_nested_reuse_of_exact_pending_effect_fails_closed(GameTestHelper h) {
        Fixture f = new Fixture(h); f.ready(() -> {
            Object key = lease(f); falling(f.p); boolean[] accepted = {true};
            AddedAction hook = new AddedAction(f.p, event -> accepted[0] = f.p.addEffect(event.getEffectInstance(), f.p)); f.hook(hook);
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertTrue(hook.called && !accepted[0], "HEAD guard refuses nested reuse of the same pending instance");
            h.assertFalse(f.p.hasEffect(MobEffects.SLOW_FALLING), "refused reentry also invalidates the outer commit");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=110)
    public static void domain_native_ai_step_uses_slow_falling_gravity_and_native_fall_reset(GameTestHelper h) {
        Fixture f = new Fixture(h), control = new Fixture(h); f.ready(() -> {
            Object key = lease(f);
            int offset = 0;
            for (ServerPlayer player : new ServerPlayer[] {f.p, control.p}) {
                player.setPos(f.p.getX() + offset, h.absoluteVec(new Vec3(0, 18, 0)).y, f.p.getZ());
                player.setNoGravity(false); falling(player); player.fallDistance = 9;
                offset += 4;
            }
            MagneticDomainSlowFall.refresh(f.p, key, () -> true);
            h.assertTrue(f.p.fallDistance == 9 && f.p.getDeltaMovement().y == -1, "effect attachment itself changes neither motion nor fall distance");
            // In 1.21.1 aiStep resets fall distance before calling native travel; travel alone only changes gravity.
            f.p.aiStep(); control.p.aiStep();
            h.assertTrue(f.p.getDeltaMovement().y > control.p.getDeltaMovement().y, "actual native travel applies less downward gravity to slow falling");
            h.assertTrue(f.p.fallDistance < control.p.fallDistance, "actual native slow falling resets accumulated fall distance");
            h.succeed();
        });
    }

    public static final class AddedAction {
        final ServerPlayer player;
        final Consumer<MobEffectEvent.Added> action;
        boolean called;
        AddedAction(ServerPlayer player, Consumer<MobEffectEvent.Added> action) { this.player = player; this.action = action; }
        @SubscribeEvent(priority=EventPriority.LOWEST)
        public void added(MobEffectEvent.Added event) {
            if (event.getEntity() == player && event.getEffectInstance().is(MobEffects.SLOW_FALLING) && !called) {
                called = true; action.accept(event);
            }
        }
    }

    private static void falling(ServerPlayer player) { player.setNoGravity(false); player.setOnGround(false); player.setDeltaMovement(0, -1, 0); }
    private static Object lease(Fixture f) {
        Object key = new Object();
        f.h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo info) {}
            public void testPassed(GameTestInfo info, GameTestRunner runner) { MagneticDomainSlowFall.release(f.p, key); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { MagneticDomainSlowFall.release(f.p, key); }
            public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { MagneticDomainSlowFall.release(f.p, key); }
        });
        return key;
    }
}
