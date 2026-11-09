package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.DisplacedNeedleEntity;
import cn.academy.port.highpower.DisplacedNeedleRegistry;
import cn.academy.port.highpower.TeleporterNeedleEntities;
import cn.academy.port.highpower.core.DisplacedNeedleLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Native NEW323 custody seam, independent of the skill's input/mark/economics controller.
 * Real entities, inventory, projectile ticks, section events and native lifetime are used.
 * The explicitly synthetic unload/reload test checks the production codec, not a restart.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDisplacedNeedleRecoveryRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_displaced_needle_recovery";
    private HighPowerDisplacedNeedleRecoveryRuntimeTests() { }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void recovery_moves_same_uuid_once_without_inventory_or_attack_generation(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            var medium = f.medium; var identity = medium.identity(); int nativeId = medium.getId();
            var before = f.state(); Vec3 destination = f.destination();
            h.assertTrue(medium.beginFlight(identity), "original committed flight is armed before recall");
            h.assertTrue(f.recover(destination, () -> true), "existing medium moved");
            h.assertTrue(medium.position().equals(destination) && medium.getId() == nativeId
                    && medium.getUUID().equals(identity.entityId()) && medium.identity().equals(identity),
                    "native ID, UUID, nonce and generation all survive recovery");
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).orElse(null) == medium && medium.getOwner() == f.p,
                    "exact native object and original owner remain bound");
            h.assertTrue(f.state().equals(before) && medium.mediumSnapshot().getCount() == 1,
                    "same active one-item custody with no generation or nonce refill");
            h.assertValueEqual(f.payments.get(), 1, "one callback-free payment");
            h.assertValueEqual(f.needles(), 1, "recall did not insert the deployed original into inventory");
            h.assertFalse(medium.isContactEnabled(), "recovery is harmless");
            h.assertFalse(medium.beginFlight(identity), "old generation cannot be rearmed");
            h.assertFalse(f.recover(destination, () -> true), "no-op replay is free");
            h.assertValueEqual(f.payments.get(), 1, "no replay charge");
            f.settle(); medium.playerTouch(f.p); medium.playerTouch(f.p);
            h.assertTrue(medium.isRemoved() && f.state().state() == DisplacedNeedleLedger.State.SPENT,
                    "ordinary native pickup settles the original once");
            h.assertValueEqual(f.needles(), 2, "two original needles total after native pickup");
            h.assertFalse(f.recover(f.source(), () -> true), "picked-up UUID cannot be recalled");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void refused_recovery_retires_attack_without_restoring_external_mutation(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            var medium = f.medium; var identity = medium.identity(); Vec3 source = medium.position();
            h.assertTrue(medium.beginFlight(identity), "original native generation armed");
            h.assertFalse(f.recover(f.destination(), () -> false), "guard cancellation refuses");
            h.assertFalse(medium.isContactEnabled(), "guard refusal irrevocably retires contact");
            h.assertFalse(f.recover(f.destination(), () -> { throw new IllegalStateException("Declared recovery guard failure"); }),
                    "thrown guard refuses without a fabricated refund");
            h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, medium, identity, f.destination(), () -> true, () -> false),
                    "payment refusal is free");
            h.assertTrue(medium.position().equals(source) && f.state().state() == DisplacedNeedleLedger.State.ACTIVE,
                    "unmutated refusal retains the same entity and custody");
            h.assertFalse(f.recover(f.destination(), () -> { medium.setDeltaMovement(0.3, 0, 0); return true; }),
                    "post-guard motion mutation is refused");
            h.assertTrue(medium.getDeltaMovement().equals(new Vec3(0.3, 0, 0)), "external mutation is not rolled back");
            h.assertValueEqual(f.payments.get(), 0, "every failed guard precedes payment");
            h.assertValueEqual(f.needles(), 1, "refusal did not grant or remove an item");
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).orElse(null) == medium,
                    "refused recovery does not declare physical custody lost");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void recovery_guard_blocks_native_tick_pickup_reprojection_and_nested_recovery(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.settle(); var medium = f.medium; var identity = medium.identity();
            Vec3 source = medium.position(); int beforeLife = life(medium);
            h.assertTrue(f.recover(f.destination(), () -> {
                medium.tick(); medium.playerTouch(f.p);
                h.assertTrue(medium.position().equals(source) && life(medium) == beforeLife,
                        "native tick and pickup cannot run during the recovery guard");
                h.assertFalse(f.recover(f.source().add(0, 1, 0), () -> true), "same-entity nested recovery blocked");
                h.assertFalse(DisplacedNeedleRegistry.reproject(f.p, medium, f.source(), () -> true, f::pay),
                        "NEW311 cannot issue a generation during recovery");
                h.assertFalse(medium.advanceGeneration(identity, identity.generation() + 1), "generation mutation blocked");
                h.assertFalse(medium.beginFlight(identity), "contact rearming blocked");
                h.assertValueEqual(f.needles(), 1, "native inventory is untouched inside callback");
                return true;
            }), "outer recovery commits once after native reentry attempts");
            h.assertValueEqual(f.payments.get(), 1, "only outer payment committed");
            h.assertTrue(medium.identity().equals(identity) && life(medium) == beforeLife,
                    "identity and exact existing age are preserved");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void full_inventory_refusal_and_owner_only_native_pickup_preserve_original(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            for (int slot = 0; slot < 36; slot++) f.p.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
            ItemStack secondOriginal = f.p.getOffhandItem();
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
            Vec3 source = f.medium.position();
            h.assertFalse(f.recover(f.destination(), () -> hasRoom(f.p, f.medium)),
                    "controller capacity guard refuses a full inventory before payment");
            h.assertTrue(f.medium.position().equals(source), "full inventory leaves physical medium in place");
            h.assertValueEqual(f.payments.get(), 0, "full capacity guard does not spend");
            f.p.getInventory().setItem(2, ItemStack.EMPTY);
            h.assertTrue(f.recover(f.destination(), () -> hasRoom(f.p, f.medium)), "capacity allows physical recall");
            h.assertTrue(f.p.getInventory().getItem(2).isEmpty(), "recall itself never inserts an item");
            f.settle();
            ServerPlayer outsider = f.actors.player(); outsider.getInventory().clearContent();
            f.medium.playerTouch(outsider);
            h.assertFalse(f.medium.isRemoved(), "another owner cannot pick up recalled needle");
            f.p.getInventory().setItem(2, new ItemStack(Items.DIRT, 64));
            f.medium.playerTouch(f.p);
            h.assertFalse(f.medium.isRemoved(), "native full inventory pickup keeps original medium");
            f.p.getInventory().setItem(2, secondOriginal);
            f.medium.playerTouch(f.p); f.medium.playerTouch(f.p);
            h.assertTrue(f.medium.isRemoved(), "native pickup transfers exactly once when capacity returns");
            h.assertValueEqual(f.needles(), 2, "retained second original plus one recovered original");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void recovery_rejects_wrong_owner_stale_generation_nonce_and_native_impostor(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            var medium = f.medium; var identity = medium.identity();
            var wrongNonce = new DisplacedNeedleEntity.Identity(identity.owner(), identity.nonce() + 1,
                    identity.entityId(), identity.generation());
            h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, medium, wrongNonce, f.destination(), () -> true, f::pay),
                    "unissued custody nonce is refused");
            ServerPlayer outsider = f.actors.player();
            h.assertFalse(DisplacedNeedleRegistry.recoverExisting(outsider, medium, identity, f.destination(), () -> true, f::pay),
                    "foreign owner cannot recall the registered medium");
            CompoundTag saved = new CompoundTag(); h.assertTrue(medium.save(saved), "native original saved for declared impostor fixture");
            var impostor = new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            impostor.load(saved); f.entities.add(impostor);
            h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, impostor, identity, f.destination(), () -> true, f::pay),
                    "unindexed same-UUID object cannot replace the original");
            h.assertTrue(DisplacedNeedleRegistry.reproject(f.p, medium, f.source().add(0, 0.5, 0), () -> true, () -> true),
                    "authentic registry reprojection advances the original generation");
            h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, medium, identity, f.destination(), () -> true, f::pay),
                    "old registration generation cannot act on a new generation");
            h.assertTrue(f.recover(f.destination(), () -> true), "current exact identity remains recoverable");
            h.assertValueEqual(f.payments.get(), 1, "only current identity spends");
            h.assertValueEqual(f.needles(), 1, "identity failures never grant inventory");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void death_removal_and_unload_inside_guard_cannot_commit_recovery(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h)) {
            int action = mode; var medium = f.medium;
            h.assertFalse(f.recover(f.destination(), () -> {
                if (action == 0) {
                    f.p.setInvulnerable(false);
                    f.p.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
                } else medium.remove(action == 1 ? Entity.RemovalReason.UNLOADED_TO_CHUNK : Entity.RemovalReason.DISCARDED);
                return true;
            }), "changed owner or native removal invalidates final recovery validation");
            h.assertValueEqual(f.payments.get(), 0, "death/removal refusal is free");
            h.assertFalse(medium.isContactEnabled(), "removal never revives damage");
            h.assertTrue(f.state().state() == (action == 2 ? DisplacedNeedleLedger.State.LOST : DisplacedNeedleLedger.State.ACTIVE),
                    "death/unload preserves outstanding custody; generic discard stays unresolved");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "missing identity does not mint a refund");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void synthetic_native_reload_rebinds_original_uuid_without_restoring_recovery_or_damage(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            var first = f.medium; var identity = first.identity(); f.settle();
            int originalLife = life(first); CompoundTag entityTag = new CompoundTag(); first.save(entityTag);
            first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK);
            h.assertFalse(f.recover(f.destination(), () -> true), "unloaded UUID is not recalled or reconstructed by the seam");
            CompoundTag registryTag = f.registry().save(new CompoundTag(), h.getLevel().registryAccess());
            var registry = DisplacedNeedleRegistry.load(h.getLevel().getServer(), registryTag, h.getLevel().registryAccess());
            h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID, registry);
            var restored = new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            restored.load(entityTag); f.entities.add(restored);
            h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, restored, identity, f.destination(), () -> true, f::pay),
                    "decoded object without native indexing has no authority");
            h.assertTrue(h.getLevel().addFreshEntity(restored) && DisplacedNeedleRegistry.tryBind(restored),
                    "declared native reload rebinds original registered UUID");
            f.medium = restored;
            h.assertTrue(f.recover(f.destination(), () -> true), "current loaded original UUID can be recalled");
            h.assertTrue(restored.getUUID().equals(first.getUUID()) && restored.identity().equals(identity)
                    && life(restored) == originalLife && !restored.isContactEnabled(),
                    "native reload and recall preserve identity and remaining life without damage permission");
            h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, first, identity, f.source(), () -> true, f::pay),
                    "old removed Java object cannot race its native reload");
            h.assertValueEqual(f.payments.get(), 1, "only rebound native entity spends");
            h.assertValueEqual(f.needles(), 1, "reload/recovery does not insert or clone the item");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void repeated_recovery_preserves_native_age_through_real_fall_embedding_and_expiry(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.settle();
            while (life(f.medium) < 1000) f.medium.tick();
            var identity = f.medium.identity();
            for (int attempt = 0; attempt < 4; attempt++) {
                int beforeLife = life(f.medium);
                Vec3 destination = attempt % 2 == 0 ? f.destination() : f.source();
                h.assertTrue(f.recover(destination, () -> true), "same existing entity recalls on repeated attempts");
                h.assertValueEqual(life(f.medium), beforeLife, "recall never resets native age immediately");
                f.medium.tick();
                h.assertValueEqual(life(f.medium), beforeLife, "first native post-recall tick does not enter age-resetting startFalling");
                f.settle();
                h.assertTrue(life(f.medium) >= beforeLife && f.medium.identity().equals(identity),
                        "real falling and re-embedding retain accumulated native age and same generation");
            }
            while (life(f.medium) < 1199) f.medium.tick();
            h.assertFalse(f.medium.isRemoved(), "original remains just before native1200-tick expiry");
            f.medium.tick();
            h.assertTrue(f.medium.isRemoved() && f.state().state() == DisplacedNeedleLedger.State.SPENT
                    && f.state().custody() == DisplacedNeedleLedger.Custody.CONSUMED,
                    "repeated recalls did not grant a fresh lifetime or defeat authentic native despawn settlement");
            h.assertFalse(f.recover(f.destination(), () -> true), "expired original cannot be recalled");
            h.assertValueEqual(f.needles(), 1, "expiry grants no cloned needle or inventory refund");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_section_callbacks_cannot_reenter_or_turn_paid_failure_into_refund(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h)) {
            int action = mode; var medium = f.medium; var identity = medium.identity();
            Vec3 destination = f.source().add(0, 16, 0); AtomicInteger observed = new AtomicInteger();
            f.listenSection(event -> {
                observed.incrementAndGet();
                h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, medium, identity, f.source(), () -> true, f::pay),
                        "native entity-section callback cannot recursively recall the moving entity");
                h.assertFalse(DisplacedNeedleRegistry.reproject(f.p, medium, f.source(), () -> true, f::pay),
                        "native entity-section callback cannot create a damaging generation");
                medium.tick(); medium.playerTouch(f.p);
                if (action == 2) medium.discard();
                if (action > 0) throw new IllegalStateException("Declared native recovery section callback failure");
            });
            h.assertTrue(f.recover(destination, () -> true) == (action == 0), "normal section event succeeds and thrown event reports failure");
            h.assertValueEqual(observed.get(), 1, "real native section crossing occurred exactly once");
            h.assertValueEqual(f.payments.get(), 1, "post-payment native failure cannot replay or refund a charge");
            h.assertValueEqual(f.needles(), 1, "post-payment callbacks cannot grant inventory");
            h.assertTrue(medium.identity().equals(identity) && !medium.isContactEnabled()
                    && f.state().state() == (action == 2 ? DisplacedNeedleLedger.State.LOST : DisplacedNeedleLedger.State.ACTIVE),
                    "callback failure preserves original custody; explicit removal remains unresolved without replacement");
            h.assertTrue(medium.position().equals(destination), "native position was committed before the section callback failed");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "paid native uncertainty cannot fabricate inventory escrow");
            h.assertFalse(medium.impactBusy(), "finally always releases recovery barrier after native exception");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void payment_exception_before_native_move_keeps_one_original_without_refund(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            Vec3 source = f.medium.position(); var identity = f.medium.identity();
            h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, f.medium, identity, f.destination(), () -> true, () -> {
                f.pay();
                throw new IllegalStateException("Declared failure after payment and before native position change");
            }), "partial external payment failure is unconfirmed, never a free retry or inventory refund");
            h.assertTrue(f.medium.position().equals(source) && f.medium.identity().equals(identity)
                    && DisplacedNeedleRegistry.existing(f.p).orElse(null) == f.medium,
                    "pre-move failure retains the exact original object, location and custody");
            h.assertValueEqual(f.payments.get(), 1, "already committed payment remains observed");
            h.assertValueEqual(f.needles(), 1, "no item minting on paid failure");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "paid uncertainty has no physical escrow to refund");
            h.assertFalse(f.medium.impactBusy(), "payment exception still releases short-lived barrier");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch="highpower_displaced_needle_visibility", timeoutTicks=30)
    public static void native_hidden_tracking_flap_resumes_same_entity_without_damage_or_age_reset(GameTestHelper h) {
        Fixture f = new Fixture(h);
        try {
            f.settle(); var medium = f.medium; var identity = medium.identity();
            while (life(medium) < 1000) medium.tick();
            int age = life(medium); Vec3 position = medium.position();
            h.assertTrue(medium.beginFlight(identity), "original generation armed before tracking cessation");
            var manager = manager(h); var chunk = new ChunkPos(medium.blockPosition());
            AtomicInteger leaveCallbacks = new AtomicInteger();
            Object leaveProbe = new Object() {
                @SubscribeEvent public void leave(net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent event) {
                    if (event.getEntity() != medium) return;
                    leaveCallbacks.incrementAndGet();
                    h.assertFalse(medium.isRemoved(), "native tracking stop precedes any removal reason");
                    h.assertTrue(medium.getRemovalReason() == null, "native hidden transition has no removal reason");
                    h.assertTrue(h.getLevel().getEntity(medium.getUUID()) == medium,
                            "native leave callback still sees old visible indexes before stopTracking completes");
                    h.assertTrue(DisplacedNeedleRegistry.existing(f.p).isEmpty(),
                            "same-callback old indexes cannot prematurely resume tracking suspension");
                    medium.tick(); medium.playerTouch(f.p);
                    h.assertTrue(medium.position().equals(position) && life(medium) == age,
                            "suspension blocks native tick/pickup during leave-event reentry");
                }
            };
            NeoForge.EVENT_BUS.register(leaveProbe);
            try {
                manager.updateChunkStatus(chunk, Visibility.HIDDEN);
                h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.ACTIVE && !medium.isRemoved(),
                        "tracking cessation preserves outstanding custody without declaring loss");
                h.assertTrue(DisplacedNeedleRegistry.existing(f.p).isEmpty(), "hidden entity is unavailable");
            } finally {
                manager.updateChunkStatus(chunk, Visibility.TICKING);
                NeoForge.EVENT_BUS.unregister(leaveProbe);
            }
            h.assertValueEqual(leaveCallbacks.get(), 1, "actual native onTrackingEnd/leave callback executed");
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).isEmpty(), "same physical tick remains fenced after visibility returns");
            h.runAfterDelay(1, () -> {
                try (f) {
                    h.assertTrue(DisplacedNeedleRegistry.existing(f.p).orElse(null) == medium,
                            "later physical tick revalidates both indexes and resumes exact same object");
                    h.assertTrue(medium.identity().equals(identity) && !medium.isContactEnabled(), "resumption grants no generation or damage");
                    h.assertFalse(medium.beginFlight(identity), "suspended generation cannot be rearmed");
                    h.assertTrue(life(medium) >= age, "native age never reset across tracking cessation/resumption");
                    f.settle();
                    while (life(medium) < 1199) medium.tick();
                    medium.tick();
                    h.assertTrue(medium.isRemoved() && f.state().custody() == DisplacedNeedleLedger.Custody.CONSUMED,
                            "native lifetime expiry after tracking resumption still settles the original once");
                    h.assertValueEqual(f.needles(), 1, "tracking/expiry never grants replacement inventory");
                    h.succeed();
                }
            });
        } catch (RuntimeException | Error failure) { f.close(); throw failure; }
    }

    @GameTest(template=TEMPLATE, batch="highpower_displaced_needle_visibility", timeoutTicks=30)
    public static void native_hidden_final_unload_clears_live_pointer_and_rebinds_saved_original_uuid(GameTestHelper h) {
        try (Fixture f = new Fixture(h)) {
            f.settle(); var first = f.medium; var identity = first.identity(); int age = life(first);
            var manager = manager(h); var chunk = new ChunkPos(first.blockPosition());
            CompoundTag entityTag = new CompoundTag();
            try {
                manager.updateChunkStatus(chunk, Visibility.HIDDEN);
                h.assertTrue(first.save(entityTag), "native save still includes the temporarily hidden original item");
                nativeUnload(manager, first);
                h.assertTrue(first.isRemoved() && first.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                        "pinned manager unload uses final setRemoved, bypassing subclass remove");
                h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.ACTIVE,
                        "positive final unload retains ACTIVE outstanding custody");
            } finally { manager.updateChunkStatus(chunk, Visibility.TICKING); }
            var restored = new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            restored.load(entityTag); f.entities.add(restored);
            h.assertTrue(h.getLevel().addFreshEntity(restored) && DisplacedNeedleRegistry.tryBind(restored),
                    "same UUID rebind proves final unload cleared old live pointer without replacing the registry");
            f.medium = restored;
            h.assertTrue(restored.identity().equals(identity) && life(restored) == age && !restored.isContactEnabled(),
                    "same saved nonce/generation/item/life restored without attack authority");
            var shadow = new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            shadow.load(entityTag); f.entities.add(shadow);
            h.assertFalse(DisplacedNeedleRegistry.tryBind(shadow), "same UUID shadow cannot replace native loaded original");
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).orElse(null) == restored, "real loaded original remains the unique bound medium");
            h.assertTrue(f.recover(f.destination(), () -> true), "loaded original can be recalled after genuine hidden final unload");
            f.settle(); restored.playerTouch(f.p); restored.playerTouch(f.p);
            h.assertValueEqual(f.needles(), 2, "one native pickup returns the one original item after unload/reload");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch="highpower_displaced_needle_visibility", timeoutTicks=30)
    public static void native_hidden_discard_and_kill_remain_lost_and_cannot_rebind_old_nbt(GameTestHelper h) {
        for (var reason : List.of(Entity.RemovalReason.DISCARDED, Entity.RemovalReason.KILLED)) try (Fixture f = new Fixture(h)) {
            var medium = f.medium; CompoundTag old = new CompoundTag(); medium.save(old);
            var manager = manager(h); var chunk = new ChunkPos(medium.blockPosition());
            try {
                manager.updateChunkStatus(chunk, Visibility.HIDDEN);
                medium.setRemoved(reason); // actual final native method, not subclass remove
                h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.LOST,
                        "positive hidden destruction retains existing unknown-custody failure policy");
            } finally { manager.updateChunkStatus(chunk, Visibility.TICKING); }
            var stale = new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            stale.load(old); f.entities.add(stale);
            h.assertTrue(h.getLevel().addFreshEntity(stale), "declared stale-NBT fixture inserted after original removal");
            h.assertFalse(DisplacedNeedleRegistry.tryBind(stale), "LOST record is never repaired by old NBT or same UUID");
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).isEmpty(), "missing/destroyed custody stays unavailable");
            stale.playerTouch(f.p);
            h.assertValueEqual(f.needles(), 1, "unknown removal cannot grant pickup, refund or replacement");
        }
        h.succeed();
    }

    /** Test-only access to the actual pinned native lifecycle, with no production accessor. */
    @SuppressWarnings("unchecked")
    private static PersistentEntitySectionManager<Entity> manager(GameTestHelper h) {
        try {
            var field = net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager");
            field.setAccessible(true);
            return (PersistentEntitySectionManager<Entity>)field.get(h.getLevel());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native entity manager unavailable", failure); }
    }
    private static void nativeUnload(PersistentEntitySectionManager<Entity> manager, DisplacedNeedleEntity medium) {
        try {
            var method = PersistentEntitySectionManager.class.getDeclaredMethod("unloadEntity", EntityAccess.class);
            method.setAccessible(true); method.invoke(manager, medium);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native unload seam failed", failure); }
    }

    private static int life(DisplacedNeedleEntity medium) {
        CompoundTag tag = new CompoundTag(); medium.addAdditionalSaveData(tag); return tag.getShort("life");
    }
    private static boolean hasRoom(ServerPlayer player, DisplacedNeedleEntity medium) {
        return player.getInventory().getSlotWithRemainingSpace(medium.mediumSnapshot()) >= 0
                || player.getInventory().getFreeSlot() >= 0;
    }
    public static final class SectionProbe {
        private final DisplacedNeedleEntity medium;
        private final Consumer<EntityEvent.EnteringSection> callback;
        SectionProbe(DisplacedNeedleEntity medium, Consumer<EntityEvent.EnteringSection> callback) {
            this.medium = medium; this.callback = callback;
        }
        @SubscribeEvent public void enter(EntityEvent.EnteringSection event) {
            if (event.getEntity() == medium) callback.accept(event);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final AcademyWirelessDeviceRuntimeTests.NativeMenuActors actors;
        final ServerPlayer p;
        final AtomicInteger payments = new AtomicInteger();
        final List<DisplacedNeedleEntity> entities = new ArrayList<>();
        final List<SectionProbe> probes = new ArrayList<>();
        DisplacedNeedleEntity medium;
        Fixture(GameTestHelper h) {
            this.h = h; actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h); p = actors.player();
            p.getInventory().clearContent(); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 2));
            medium = DisplacedNeedleRegistry.deploy(p, source(), () -> true, () -> true).orElseThrow(); entities.add(medium);
            for (int x = 5; x <= 9; x++) h.getLevel().setBlockAndUpdate(BlockPos.containing(h.absoluteVec(new Vec3(x, 2, 5))), Blocks.STONE.defaultBlockState());
        }
        Vec3 source() { return h.absoluteVec(new Vec3(5.5, 3.5, 5.5)); }
        Vec3 destination() { return h.absoluteVec(new Vec3(8.5, 3.5, 5.5)); }
        DisplacedNeedleRegistry registry() { return DisplacedNeedleRegistry.get(h.getLevel()); }
        DisplacedNeedleLedger.Snapshot state() { return registry().snapshot(p.getUUID()).orElseThrow(); }
        boolean pay() { payments.incrementAndGet(); return true; }
        boolean recover(Vec3 destination, BooleanSupplier guard) {
            return DisplacedNeedleRegistry.recoverExisting(p, medium, medium.identity(), destination, guard, this::pay);
        }
        int needles() {
            int result = 0;
            for (int slot = 0; slot < p.getInventory().getContainerSize(); slot++) {
                ItemStack stack = p.getInventory().getItem(slot); if (stack.is(AcademyCraft.NEEDLE.get())) result += stack.getCount();
            }
            return result;
        }
        void settle() {
            for (int tick = 0; tick < 24 && !medium.isRemoved(); tick++) medium.tick();
            h.assertTrue(medium.inGround && medium.shakeTime == 0, "real native gravity/impact settles a pickup-ready arrow");
        }
        void listenSection(Consumer<EntityEvent.EnteringSection> callback) {
            SectionProbe probe = new SectionProbe(medium, callback); probes.add(probe); NeoForge.EVENT_BUS.register(probe);
        }
        @Override public void close() {
            probes.forEach(NeoForge.EVENT_BUS::unregister);
            for (var entity : entities) if (!entity.isRemoved()) entity.discard();
            actors.close();
        }
    }
}
