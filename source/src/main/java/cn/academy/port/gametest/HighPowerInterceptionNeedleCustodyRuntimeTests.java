package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.CrossNeedleRegistry;
import cn.academy.port.highpower.InterceptionNeedleRegistry;
import cn.academy.port.highpower.DisplacedNeedleEntity;
import cn.academy.port.highpower.DisplacedNeedleRegistry;
import cn.academy.port.highpower.HighPowerGrowth;
import cn.academy.port.highpower.MagneticItemProvenance;
import cn.academy.port.highpower.RecoveryMark;
import cn.academy.port.highpower.TeleporterNeedleEntities;
import cn.academy.port.highpower.core.DisplacedNeedleLedger;
import cn.academy.port.highpower.core.RecoveryMarkRules;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** Native NEW331 fallback custody. Uses actual offhand split, publication, pickup,
 * native impact/despawn and final entity-manager unload. Inventory, poses and floor
 * are declared fixtures; save reconstruction is not an atomic process restart. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerInterceptionNeedleCustodyRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_interception_needle_custody";
    private HighPowerInterceptionNeedleCustodyRuntimeTests() { }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_cancel_escrows_before_join_and_blocks_reentrant_deployment(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            f.listen(event -> {
                var medium = (DisplacedNeedleEntity)event.getEntity();
                h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.PENDING, "pending ledger precedes native join callback");
                h.assertValueEqual(f.p.getOffhandItem().getCount(), 3, "exactly one actual item already removed");
                h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "original item retained in escrow");
                h.assertFalse(medium.isAdmitted(), "staged candidate remains unadmitted");
                Vec3 position = medium.position(); medium.setDeltaMovement(2, 3, 4); medium.tick(); medium.playerTouch(f.p);
                h.assertTrue(medium.position().equals(position), "callback cannot tick staged candidate into flight");
                h.assertFalse(f.deploy(), "same-owner callback cannot reserve a second candidate");
                h.assertValueEqual(f.payments.get(), 0, "no reentrant or premature payment");
                event.setCanceled(true);
            });
            h.assertFalse(f.deploy(), "native join cancellation denies deployment");
            h.assertValueEqual(f.joins, 1, "one real join callback only");
            h.assertValueEqual(f.needles(), 4, "all four original needles remain after exact refund");
            h.assertValueEqual(f.payments.get(), 0, "cancelled spawn never charges");
            h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.SPENT, "refund settled once");
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "refund ticket cannot replay");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_join_inventory_mutation_is_preserved_without_snapshot_rollback(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            f.listen(event -> {
                f.p.getInventory().setItem(0, f.p.getOffhandItem());
                f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 7));
                f.p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
            });
            h.assertFalse(f.deploy(), "offhand object/count changed during native join");
            h.assertTrue(f.p.getOffhandItem().is(Items.DIRT) && f.p.getOffhandItem().getCount() == 7,
                    "callback's new offhand survives failed deployment");
            h.assertValueEqual(count(f.p, Items.DIAMOND), 3, "unrelated callback inventory change survives");
            h.assertValueEqual(f.needles(), 4, "three relocated originals plus exactly one refund");
            h.assertValueEqual(f.payments.get(), 0, "offhand revalidation precedes payment");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_cancel_full_inventory_retains_one_original_until_explicit_retry(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p);
            f.listen(event -> {
                f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
                event.setCanceled(true);
            });
            h.assertFalse(f.deploy(), "cancelled native spawn");
            h.assertValueEqual(f.needles(), 0, "full inventory has no copied refund");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "actual removed item remains retained");
            h.assertTrue(f.state().custody() == DisplacedNeedleLedger.Custody.RETURN_PENDING, "safe refund remains pending");
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "full inventory cannot consume escrow");
            f.p.getInventory().setItem(2, ItemStack.EMPTY);
            h.assertTrue(InterceptionNeedleRegistry.retryRefund(f.p), "new capacity receives retained original");
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "repeat retry cannot duplicate");
            h.assertValueEqual(f.needles(), 1, "exactly one original returns");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 0, "escrow empty only after real insertion");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void safe_native_cancel_refund_survives_saved_data_reload_and_returns_original_once(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p);
            f.listen(event -> {
                f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
                event.setCanceled(true);
            });
            h.assertFalse(f.deploy(), "genuine native cancellation leaves full-inventory original escrow");
            long nonce = f.state().nonce();
            h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.RETURNING
                    && f.state().custody() == DisplacedNeedleLedger.Custody.RETURN_PENDING
                    && f.state().generation() == 0 && f.state().entityId() == null,
                    "safe persisted refund has no active generation or unresolved candidate identity");
            h.assertTrue(InterceptionNeedleRegistry.availability(f.p) == InterceptionNeedleRegistry.Availability.REFUND_PENDING,
                    "safe escrow refund differs from an unresolved outstanding entity");
            CompoundTag saved = f.registry().save(new CompoundTag(), h.getLevel().registryAccess());
            CompoundTag originalEscrow = ownedEntry(saved, f.p.getUUID()).getCompound("escrow").copy();
            var restored = InterceptionNeedleRegistry.load(h.getLevel().getServer(), saved, h.getLevel().registryAccess());
            h.getLevel().getServer().overworld().getDataStorage().set(InterceptionNeedleRegistry.ID, restored);
            h.assertTrue(ownedEntry(restored.save(new CompoundTag(), h.getLevel().registryAccess()), f.p.getUUID())
                    .getCompound("escrow").equals(originalEscrow), "production codec retains exactly original serialized one-item escrow");
            h.assertTrue(InterceptionNeedleRegistry.availability(f.p) == InterceptionNeedleRegistry.Availability.REFUND_PENDING,
                    "decoded safe cancellation remains explicitly refundable");
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "restored full inventory cannot consume retained escrow");
            h.assertValueEqual(f.needles(), 0, "no refund while capacity is unavailable");
            h.assertValueEqual(restored.retainedEscrowCount(f.p.getUUID()), 1, "full retry retains original item after reload");
            f.p.getInventory().setItem(0, ItemStack.EMPTY);
            h.assertTrue(InterceptionNeedleRegistry.retryRefund(f.p), "new native inventory capacity receives original persisted escrow");
            h.assertTrue(ItemStack.matches(f.p.getInventory().getItem(0), new ItemStack(AcademyCraft.NEEDLE.get())),
                    "native insertion returns one original plain academy needle");
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "same decoded refund ticket cannot be replayed");
            h.assertValueEqual(f.needles(), 1, "exactly one original returned across all retries");
            h.assertValueEqual(restored.retainedEscrowCount(f.p.getUUID()), 0, "successful native insertion clears escrow");
            h.assertTrue(f.state().nonce() == nonce && f.state().state() == DisplacedNeedleLedger.State.SPENT
                    && f.state().custody() == DisplacedNeedleLedger.Custody.RETURNED,
                    "refund consumes original ticket without creating a deployment nonce");
            CompoundTag settled = restored.save(new CompoundTag(), h.getLevel().registryAccess());
            h.getLevel().getServer().overworld().getDataStorage().set(InterceptionNeedleRegistry.ID,
                    InterceptionNeedleRegistry.load(h.getLevel().getServer(), settled, h.getLevel().registryAccess()));
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "settled saved record cannot replay return after another reload");
            h.assertTrue(f.state().nonce() == nonce, "reloaded settled return preserves nonce");
            h.assertValueEqual(f.needles(), 1, "second reload creates no item");
            h.assertValueEqual(f.payments.get(), 0, "all refund retries are free and never enter payment seam");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_identity_mutation_never_activates_or_refunds_foreign_entity(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            f.listen(event -> event.getEntity().setUUID(UUID.randomUUID()));
            h.assertFalse(f.deploy(), "native UUID mutation fails captured identity proof");
            h.assertValueEqual(f.payments.get(), 0, "mutated UUID never charged");
            // UUID mutation invalidates the staged release proof. The exact original remains
            // retained and blocked; the adapter must not infer a refundable disposition.
            h.assertValueEqual(f.needles() + f.registry().retainedEscrowCount(f.p.getUUID()), 2,
                    "inventory and retained escrow conserve originals without minting");
            h.assertTrue(InterceptionNeedleRegistry.availability(f.p) == InterceptionNeedleRegistry.Availability.OUTSTANDING,
                    "unproven mutated candidate stays outstanding");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_join_relocation_cannot_silently_commit_another_position(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            f.listen(event -> event.getEntity().setPos(f.position().add(4, 0, 0)));
            h.assertFalse(f.deploy(), "native callback relocation invalidates captured stage pose");
            h.assertValueEqual(f.payments.get(), 0, "repositioned candidate never charges");
            h.assertValueEqual(f.needles(), 2, "discarded original candidate returns exactly its own removed item");
            h.assertTrue(InterceptionNeedleRegistry.existing(f.p).isEmpty(), "relocated candidate is not activated");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void one_actual_original_publishes_before_exactly_one_payment_and_blocks_second(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            f.listen(event -> {
                var candidate = (DisplacedNeedleEntity)event.getEntity();
                h.assertTrue(candidate.isInterceptionNeedle() && candidate.getType() == TeleporterNeedleEntities.INTERCEPTION_NEEDLE.get(), "exact third custody type");
                h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.PENDING
                        && candidate.getUUID().equals(f.state().entityId()), "durable pending identity precedes join");
                h.assertValueEqual(f.needles(), 2, "exactly one offhand original already escrowed");
                h.assertValueEqual(f.payments.get(), 0, "native publication precedes CP");
                h.assertFalse(candidate.isAdmitted() || f.deploy(), "publication is inert and same-owner reentry refuses");
            });
            h.assertTrue(f.deploy(() -> {
                var candidate = f.entities.getFirst();
                h.assertTrue(h.getLevel().getEntity(candidate.getUUID()) == candidate
                        && h.getLevel().getEntity(candidate.getId()) == candidate, "both actual native indexes precede payment");
                h.assertFalse(f.deploy(), "payment cannot reenter reservation");
                return f.pay();
            }), "one fallback admitted");
            var first = f.last; var identity = first.identity();
            h.assertTrue(InterceptionNeedleRegistry.existing(f.p).orElse(null) == first
                    && InterceptionNeedleRegistry.authentic(f.p, first), "one exact active native original");
            h.assertFalse(f.deploy(), "active fallback never permits replacement or another needle");
            h.assertValueEqual(f.needles(), 2, "unused originals remain in hand");
            h.assertValueEqual(f.payments.get(), 1, "one accepted native publication pays exactly once");
            h.assertFalse(first.advanceGeneration(identity, 2), "fallback has no reprojection generation");
            h.assertTrue(first.identity().equals(identity) && !first.isContactEnabled(), "custody alone is not an attack");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void closed_guard_refusal_before_or_after_publication_is_free_and_returns_only_original(GameTestHelper h) {
        for (int failAt : new int[]{1, 2}) try (Fixture f = new Fixture(h, 2)) {
            AtomicInteger guards = new AtomicInteger();
            var result = InterceptionNeedleRegistry.deploy(f.p, f.position(), () -> guards.incrementAndGet() < failAt, f::pay);
            h.assertTrue(result.isEmpty(), "changed zero-arrow/target guard aborts fallback");
            h.assertValueEqual(f.needles(), 2, "known unpaid refusal conserves original stock");
            h.assertValueEqual(f.payments.get(), 0, "guard refusal precedes every CP commit");
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "completed return cannot replay");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void throwing_join_and_unknown_paid_commit_retain_unique_blocked_custody(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 2)) {
            if (mode == 0) f.listen(event -> { throw new IllegalStateException("Declared native join uncertainty"); });
            int selected = mode;
            h.assertFalse(f.deploy(() -> {
                if (selected == 1) { f.pay(); throw new IllegalStateException("Declared post-charge uncertainty"); }
                if (selected == 2) { f.pay(); f.entities.getFirst().setPos(f.position().add(1, 0, 0)); return true; }
                return f.pay();
            }), "uncertain callback cannot report success");
            h.assertValueEqual(f.needles(), 1, "unknown publication never refunds an original");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "one actual retained original closes custody");
            h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.LOST, "unknown completion retains outstanding tombstone");
            h.assertFalse(f.deploy() || InterceptionNeedleRegistry.retryRefund(f.p), "neither replacement nor refund can replay");
            h.assertValueEqual(f.payments.get(), mode == 0 ? 0 : 1, "observed paid result is never rolled back");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void explicit_payment_refusal_is_safe_but_persisted_transfer_ambiguity_never_replays(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertFalse(f.deploy(() -> false), "explicit unpaid callback refusal");
            h.assertValueEqual(f.needles(), 2, "verified discarded unpaid original returned once");
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "settled refusal cannot replay");
        }
        try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p);
            f.listen(event -> { f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64)); event.setCanceled(true); });
            h.assertFalse(f.deploy(), "known native cancellation retains original until inventory has capacity");
            CompoundTag saved = f.save(); ownedEntry(saved, f.p.getUUID()).getCompound("ledger").putString("custody", "REFUND_ESCROW");
            f.install(saved); f.p.getInventory().setItem(0, ItemStack.EMPTY);
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "interrupted in-flight insertion has no repeatable ticket");
            h.assertValueEqual(f.needles(), 0, "ambiguous persisted refund grants no item");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "retained original serialized evidence remains");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_pickup_full_inventory_and_foreign_owner_cannot_duplicate_original(GameTestHelper h) {
        for (boolean creative : new boolean[]{false, true}) try (Fixture f = new Fixture(h, 1);
                var foreign = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h)) {
            h.assertTrue(f.deploy(), "one real native fallback"); var medium = f.last; f.embed(medium);
            medium.playerTouch(foreign.player()); h.assertFalse(medium.isRemoved(), "foreign pickup cannot acquire custody");
            if (creative) f.p.setGameMode(GameType.CREATIVE);
            fillInventory(f.p); f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64)); medium.playerTouch(f.p);
            h.assertFalse(medium.isRemoved(), "full inventory retains original even in creative");
            f.p.getInventory().setItem(0, ItemStack.EMPTY); medium.playerTouch(f.p); medium.playerTouch(f.p);
            h.assertTrue(medium.isRemoved() && f.state().state() == DisplacedNeedleLedger.State.SPENT, "one actual native pickup settles custody");
            h.assertValueEqual(f.needles(), 1, "repeated owner touch inserts exactly one original");
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "pickup creates no independent refund");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void native_flight_retires_after_twenty_ticks_and_first_block_contact_keeps_same_item(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            var medium = InterceptionNeedleRegistry.deploy(f.p, f.position().add(0, 32, 0), () -> true, f::pay).orElseThrow();
            f.entities.add(medium); var identity = medium.identity();
            h.assertTrue(medium.beginFlight(identity), "committed generation arms once"); medium.setDeltaMovement(.01, 0, 0);
            for (int tick = 0; tick < 19; tick++) medium.tick();
            h.assertTrue(medium.isContactEnabled(), "first nineteen real flight segments remain eligible"); medium.tick();
            h.assertFalse(medium.isContactEnabled() || medium.beginFlight(identity) || medium.advanceGeneration(identity, 2), "twentieth segment irreversibly retires fallback attack");
            h.assertTrue(!medium.isRemoved() && medium.identity().equals(identity)
                    && medium.mediumSnapshot().getCount() == 1, "flight expiry preserves original native entity and item");
        }
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.deploy(), "second declared native fixture"); var medium = f.last;
            h.assertTrue(medium.beginFlight(medium.identity()), "one contact generation armed"); f.embed(medium);
            h.assertFalse(medium.isContactEnabled() || medium.beginFlight(medium.identity()), "first actual block hit retires contact permanently");
            h.assertTrue(InterceptionNeedleRegistry.authentic(f.p, medium), "embedded original keeps ordinary recovery and pickup custody");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void genuine_hidden_final_unload_and_native_reload_keep_exact_identity_and_lifetime_without_attack(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertTrue(f.deploy(), "native fallback admitted"); var old = f.last;
            h.assertTrue(old.beginFlight(old.identity()), "declared native generation before unload"); f.embed(old);
            var tag = nativeTag(old); var identity = old.identity(); var manager = manager(h); var chunk = new ChunkPos(old.blockPosition());
            try {
                manager.updateChunkStatus(chunk, Visibility.HIDDEN); nativeUnload(manager, old);
                h.assertTrue(old.isRemoved() && old.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK,
                        "actual final entity-manager unload delivers native custody removal");
                h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.ACTIVE, "unload preserves outstanding identity");
            } finally { manager.updateChunkStatus(chunk, Visibility.TICKING); }
            f.install(f.save());
            h.assertTrue(InterceptionNeedleRegistry.existing(f.p).isEmpty(), "missing native original is never recreated");
            h.assertFalse(f.deploy() || InterceptionNeedleRegistry.retryRefund(f.p), "unload grants neither replacement nor refund");
            var restored = f.decode(tag, 331);
            h.assertFalse(InterceptionNeedleRegistry.tryBind(restored), "decode alone lacks actual world indexes");
            h.assertTrue(h.getLevel().addFreshEntity(restored) && InterceptionNeedleRegistry.tryBind(restored), "actual exact native reload rebinds");
            h.assertTrue(restored.identity().equals(identity) && life(restored) == tag.getShort("life"), "original identity and native age survive");
            h.assertFalse(restored.isContactEnabled() || restored.beginFlight(identity) || restored.advanceGeneration(identity, 2), "reload cannot create a new attack");
            var shadow = f.decode(tag, 331); h.assertFalse(InterceptionNeedleRegistry.tryBind(shadow), "same saved UUID on another object is not custody");
            shadow.playerTouch(f.p); h.assertValueEqual(f.needles(), 1, "reload and shadow never mint inventory"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void three_exact_types_and_registry_domains_refuse_foreign_or_mixed_proofs(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            h.assertTrue(f.deploy(), "NEW331 original admitted"); var interception = f.last;
            var displaced = DisplacedNeedleRegistry.deploy(f.p, f.position().add(2, 0, 0), () -> true, () -> true).orElseThrow();
            var cross = CrossNeedleRegistry.deploy(f.p, f.position().add(4, 0, 0), () -> true, () -> true).orElseThrow();
            f.entities.add(displaced); f.entities.add(cross);
            h.assertFalse(DisplacedNeedleRegistry.tryBind(interception) || CrossNeedleRegistry.tryBind(interception), "old registries reject third native type");
            h.assertFalse(InterceptionNeedleRegistry.tryBind(displaced) || InterceptionNeedleRegistry.tryBind(cross), "new registry rejects both older native types");
            for (var old : List.of(displaced, cross)) h.assertFalse(InterceptionNeedleRegistry.recoverExisting(f.p, old, old.identity(), f.position().add(0, 1, 0), () -> true, f::pay), "wrong-domain recovery cannot pay");
            for (int type : new int[]{311, 321}) h.assertTrue(f.decode(nativeTag(interception), type).identity() == null, "fallback tag cannot decode into an old native type");
            for (var old : List.of(displaced, cross)) h.assertTrue(f.decode(nativeTag(old), 331).identity() == null, "older tag cannot decode into fallback type");
            var mixed = nativeTag(interception); mixed.put("AcademyCrossNeedle", nativeTag(cross).getCompound("AcademyCrossNeedle"));
            h.assertTrue(f.decode(mixed, 331).identity() == null, "mixed-domain persisted proof fails closed");
            h.assertTrue(InterceptionNeedleRegistry.load(h.getLevel().getServer(), DisplacedNeedleRegistry.get(h.getLevel()).save(new CompoundTag(), h.getLevel().registryAccess()), h.getLevel().registryAccess()).isBlocked(), "old single-ledger schema cannot migrate by accidental domain collision");
            h.assertValueEqual(f.payments.get(), 1, "wrong-domain operations never pay"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void stale_or_forged_native_owner_nonce_generation_cannot_replace_original(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.deploy(), "one original"); var original = f.last; var tag = nativeTag(original); nativeUnload(manager(h), original);
            for (int mutation = 0; mutation < 3; mutation++) {
                var forged = tag.copy(); var identity = forged.getCompound("AcademyInterceptionNeedle");
                if (mutation == 0) { UUID owner = UUID.randomUUID(); identity.putUUID("Owner", owner); forged.putUUID("Owner", owner); }
                if (mutation == 1) identity.putLong("Nonce", identity.getLong("Nonce") + 1);
                if (mutation == 2) identity.putLong("Generation", 2);
                var candidate = f.decode(forged, 331); h.assertTrue(h.getLevel().addFreshEntity(candidate), "declared forged decode enters native world");
                h.assertFalse(InterceptionNeedleRegistry.tryBind(candidate), "wrong identity cannot replace exact persisted original"); candidate.playerTouch(f.p); candidate.discard();
            }
            var restored = f.decode(tag, 331); h.assertTrue(h.getLevel().addFreshEntity(restored) && InterceptionNeedleRegistry.tryBind(restored), "unaltered original still rebinds after all refusals");
            h.assertValueEqual(f.needles(), 0, "forged proofs create no item grants"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void malformed_and_foreign_saved_domains_preserve_bytes_without_new_reservation(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertTrue(f.deploy(), "original active ledger"); var original = f.save(); var live = f.registry();
            try {
                for (int mutation = 0; mutation < 5; mutation++) {
                    var corrupt = original.copy(); var owner = ownedEntry(corrupt, f.p.getUUID());
                    if (mutation == 0) owner.getCompound("ledger").putString("nonce", "01");
                    if (mutation == 1) corrupt.putString("domain", DisplacedNeedleRegistry.ID);
                    if (mutation == 2) corrupt.getList("entries", Tag.TAG_COMPOUND).add(owner.copy());
                    if (mutation == 3) owner.getCompound("ledger").putString("generation", "2");
                    if (mutation == 4) { ListTag many = new ListTag(); for (int i = 0; i <= InterceptionNeedleRegistry.MAX_OWNERS; i++) many.add(new CompoundTag()); corrupt.put("entries", many); }
                    f.install(corrupt); h.assertTrue(f.save().equals(corrupt), "invalid persisted evidence remains byte-for-byte structural data");
                    h.assertTrue(InterceptionNeedleRegistry.availability(f.p) == InterceptionNeedleRegistry.Availability.CORRUPT, "malformed domain is explicit");
                    h.assertFalse(f.deploy() || InterceptionNeedleRegistry.retryRefund(f.p), "corrupt custody cannot grant a new item or refund");
                    h.assertValueEqual(f.needles(), 1, "remaining inventory stays unchanged");
                }
            } finally { h.getLevel().getServer().overworld().getDataStorage().set(InterceptionNeedleRegistry.ID, live); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void positive_native_expiry_settles_only_original_and_unknown_discard_blocks_replacement(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertTrue(f.deploy(), "one original admitted"); var old = f.last; var identity = old.identity(); var stale = nativeTag(old); f.expireNaturally(old);
            h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.SPENT && f.state().custody() == DisplacedNeedleLedger.Custody.CONSUMED, "actual1200-tick native expiry proves consumption");
            h.assertFalse(InterceptionNeedleRegistry.retryRefund(f.p), "consumed original never grants refund");
            h.assertTrue(f.deploy(), "positively consumed custody allows next actual offhand original"); var replacement = f.last;
            h.assertTrue(replacement.identity().nonce() == identity.nonce() + 1 && replacement != old, "only new actual item advances separate fallback nonce");
            var shadow = f.decode(stale, 331); h.assertTrue(h.getLevel().addFreshEntity(shadow), "declared stale native object");
            h.assertFalse(InterceptionNeedleRegistry.tryBind(shadow), "expired old nonce cannot bind current custody");
            f.registry().onRemoved(old, identity, Entity.RemovalReason.DISCARDED);
            h.assertTrue(InterceptionNeedleRegistry.authentic(f.p, replacement), "old callback replay cannot settle new original");
            replacement.setRemoved(Entity.RemovalReason.DISCARDED);
            h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.LOST, "generic discard is not evidence of native consumption");
            h.assertFalse(f.deploy() || InterceptionNeedleRegistry.retryRefund(f.p), "unknown removal remains outstanding"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void same_entity_recovery_preserves_native_life_then_reembedding_resumes_original_expiry(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.deploy(), "one original"); var medium = f.last; var identity = medium.identity();
            h.assertTrue(medium.beginFlight(identity), "one armed generation"); f.embed(medium); int age = life(medium);
            h.assertTrue(age > 0, "actual embedded native clock has begun");
            Vec3 destination = medium.position().add(2, 2, 0);
            h.getLevel().setBlockAndUpdate(BlockPos.containing(destination).below(2), Blocks.STONE.defaultBlockState());
            h.assertTrue(InterceptionNeedleRegistry.recoverExisting(f.p, medium, identity, destination, () -> true, f::pay), "323 route relocates exact original");
            h.assertTrue(medium.identity().equals(identity) && life(medium) == age && medium.position().equals(destination), "recovery preserves native lifetime and custody");
            h.assertFalse(medium.isContactEnabled() || medium.beginFlight(identity) || medium.advanceGeneration(identity, 2), "323 never renews fallback attack");
            for (int tick = 0; tick < 40 && !medium.inGround; tick++) medium.tick();
            h.assertTrue(medium.inGround && life(medium) >= age, "natural fall and reembedding do not rewind original life");
            for (int tick = 0; tick < 1200 && life(medium) < 1199 && !medium.isRemoved(); tick++) medium.tick();
            h.assertTrue(!medium.isRemoved() && life(medium) == 1199, "bounded native ticks reach unchanged final lifetime"); medium.tick();
            h.assertTrue(medium.isRemoved() && f.state().custody() == DisplacedNeedleLedger.Custody.CONSUMED, "recovered original still reaches native expiry");
            h.assertValueEqual(f.payments.get(), 2, "one deployment and one real recovery charge"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void paid_recovery_section_exception_cannot_reenter_refund_or_rearm(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.deploy(), "one original"); var medium = f.last; var identity = medium.identity();
            h.assertTrue(medium.beginFlight(identity), "declared committed generation");
            Vec3 source = medium.position(), destination = source.add(0, 16, 0); AtomicInteger calls = new AtomicInteger();
            f.listenSection(medium, event -> {
                calls.incrementAndGet(); h.assertValueEqual(f.payments.get(), 2, "resource commit precedes actual section callback");
                h.assertFalse(InterceptionNeedleRegistry.recoverExisting(f.p, medium, identity, source, () -> true, f::pay), "native section cannot reenter paid move");
                medium.tick(); medium.playerTouch(f.p); throw new IllegalStateException("Declared post-relocation failure");
            });
            h.assertFalse(InterceptionNeedleRegistry.recoverExisting(f.p, medium, identity, destination, () -> true, f::pay), "paid native callback uncertainty is not fabricated success");
            h.assertValueEqual(calls.get(), 1, "one actual native section callback"); h.assertValueEqual(f.payments.get(), 2, "callback does not repeat CP");
            h.assertTrue(medium.position().equals(destination) && medium.identity().equals(identity), "actual moved original retains same custody");
            h.assertFalse(medium.isContactEnabled() || medium.beginFlight(identity) || InterceptionNeedleRegistry.retryRefund(f.p), "unknown move cannot renew attack or refund"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void decorated_or_wrong_offhand_medium_never_spends_or_deploys(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME, Component.literal("Declared decorated fixture"));
            h.assertFalse(f.deploy(), "decorated academy needle refused"); h.assertValueEqual(f.needles(), 2, "decorated originals preserved");
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2)); h.assertFalse(f.deploy(), "ordinary arrows never become fallback needles");
            h.assertTrue(InterceptionNeedleRegistry.availability(f.p) == InterceptionNeedleRegistry.Availability.NO_MEDIUM, "explicit fallback no-medium refusal");
            h.assertValueEqual(f.payments.get(), 0, "invalid medium never pays"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch="highpower_interception_recovery", timeoutTicks=80)
    public static void native_323_mixed_domains_share_eight_handle_cap_and_recover_only_same_fallback(GameTestHelper h) {
        var base = new HighPowerRecoveryMarkRuntimeTests.Fixture(h, 5);
        List<Entity> all = new ArrayList<>(); all.add(base.needle);
        h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo info) { }
            public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { cleanup(); }
            private void cleanup() { all.forEach(entity -> { if (!entity.isRemoved()) entity.discard(); }); base.close(); }
        });
        base.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 4));
        for (int slot = 0; slot < 3; slot++) {
            var cross = CrossNeedleRegistry.deploy(base.p, base.source.add(0, 1 + slot, 0), () -> true, () -> true).orElseThrow(); all.add(cross);
        }
        var fallback = InterceptionNeedleRegistry.deploy(base.p, base.source.add(0, 4, 0), () -> true, () -> true).orElseThrow(); all.add(fallback);
        h.assertTrue(fallback.beginFlight(fallback.identity()), "declared original fallback generation before native retirement");
        for (int index = 0; index < all.size(); index++) {
            double angle = index * Math.PI / 4;
            var needle = (DisplacedNeedleEntity)all.get(index);
            needle.setPos(base.anchor.add(Math.cos(angle) * 3.8, .2, Math.sin(angle) * 3.8));
            needle.setDeltaMovement(0, -1, 0); for (int tick = 0; tick < 12; tick++) needle.tick();
            h.assertTrue(needle.inGround && !needle.isContactEnabled(), "each actual native original is retired and embedded");
        }
        base.p.getInventory().setItem(base.p.getInventory().selected, new ItemStack(AcademyCraft.NEEDLE.get(), 4));
        for (int index = 5; index < 9; index++) {
            h.assertTrue(base.p.drop(false), "one genuine Q removes an independent actual original");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, base.p.getBoundingBox().inflate(2),
                    item -> MagneticItemProvenance.ownedBy(item, base.p.getUUID()));
            h.assertValueEqual(drops.size(), 1, "exactly one new native Q original near owner"); var drop = drops.getFirst();
            double angle = index == 8 ? Math.PI / 8 : index * Math.PI / 4;
            double radius = index == 8 ? 5.3 : 3.8;
            drop.setPos(base.anchor.add(Math.cos(angle) * radius, .2, Math.sin(angle) * radius));
            drop.setDeltaMovement(Vec3.ZERO); drop.setNoGravity(true); all.add(drop);
        }
        var crossBefore = CrossNeedleRegistry.existing(base.p);
        base.aim(all.getFirst().getBoundingBox().getCenter()); h.assertTrue(base.press(1), "old311 original registration remains accepted");
        for (int index = 1; index < 8; index++) {
            int selected = index;
            base.post(index * 8, () -> {
                base.aim(all.get(selected).getBoundingBox().getCenter()); h.assertTrue(base.press(selected + 1), "fresh mixed-domain original registers");
                h.assertValueEqual(RecoveryMark.markedCount(base.p), selected + 1, "all supported domains share one bounded handle set");
            });
        }
        base.post(64, () -> {
            var handles = RecoveryMark.handles(base.p); var positions = all.stream().map(Entity::position).toList();
            h.assertValueEqual(handles.size(), RecoveryMark.MAX_HANDLES, "combined311321331 and Q originals reach exactly eight");
            base.aim(all.get(8).getBoundingBox().getCenter()); double cp = base.cp();
            h.assertFalse(base.press(9), "ninth genuine original refuses at common cap");
            h.assertTrue(RecoveryMark.handles(base.p).equals(handles) && base.cp() == cp, "cap refusal keeps every existing handle and resource");
            var identity = fallback.identity(); int age = life(fallback); base.aim(fallback.getBoundingBox().getCenter());
            h.assertTrue(base.press(10), "explicit aim recovers only selected NEW331 original at capacity");
            var receipt = RecoveryMark.receipt(base.p);
            h.assertTrue(receipt != null && receipt.paid() && receipt.moved() && receipt.phase() == RecoveryMark.RECOVERED
                    && receipt.needleUuid().equals(fallback.getUUID()) && fallback.position().equals(base.anchor), "native323 receipt proves same-entity fallback relocation");
            h.assertTrue(Math.abs(base.cp() - (cp - RecoveryMarkRules.cpCost(HighPowerGrowth.values(base.p).position()))) < 1e-7, "one actual growth-derived recovery charge");
            h.assertTrue(fallback.identity().equals(identity) && life(fallback) == age
                    && !fallback.isContactEnabled() && !fallback.beginFlight(identity) && !fallback.advanceGeneration(identity, 2), "323 preserves native age and never renews fallback attack");
            h.assertTrue(RecoveryMark.handles(base.p).equals(handles.stream().filter(mark -> !mark.needleUuid().equals(fallback.getUUID())).toList()), "seven other handles retain exact identities and anchors");
            for (int index = 0; index < all.size(); index++) if (all.get(index) != fallback)
                h.assertTrue(all.get(index).position().equals(positions.get(index)), "unselected original remains untouched");
            h.assertTrue(InterceptionNeedleRegistry.existing(base.p).orElse(null) == fallback
                    && DisplacedNeedleRegistry.existing(base.p).orElse(null) == base.needle
                    && CrossNeedleRegistry.existing(base.p).equals(crossBefore), "each registry retains only its own exact native objects");
            h.assertValueEqual(count(base.p, AcademyCraft.NEEDLE.get()), 0, "marking and recovery never mint an inventory needle");
        });
        base.finish(65);
    }

    private static CompoundTag ownedEntry(CompoundTag root, UUID owner) {
        for (Tag value : root.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag)value; if (owner.toString().equals(entry.getString("owner"))) return entry;
        }
        throw new IllegalStateException("Fixture owner record absent");
    }
    private static void fillInventory(ServerPlayer owner) { for (int slot = 0; slot < 36; slot++) owner.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64)); }
    private static int count(ServerPlayer owner, Item item) {
        int result = 0;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            var stack = owner.getInventory().getItem(slot); if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }
    private static CompoundTag nativeTag(DisplacedNeedleEntity medium) {
        CompoundTag tag = new CompoundTag(); if (!medium.save(tag)) throw new IllegalStateException("Native needle save refused"); return tag;
    }
    private static int life(DisplacedNeedleEntity medium) { CompoundTag tag = new CompoundTag(); medium.addAdditionalSaveData(tag); return tag.getShort("life"); }
    @SuppressWarnings("unchecked")
    private static PersistentEntitySectionManager<Entity> manager(GameTestHelper h) {
        try {
            var field = net.minecraft.server.level.ServerLevel.class.getDeclaredField("entityManager"); field.setAccessible(true);
            return (PersistentEntitySectionManager<Entity>)field.get(h.getLevel());
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native entity manager unavailable", failure); }
    }
    private static void nativeUnload(PersistentEntitySectionManager<Entity> manager, DisplacedNeedleEntity medium) {
        try {
            var method = PersistentEntitySectionManager.class.getDeclaredMethod("unloadEntity", EntityAccess.class);
            method.setAccessible(true); method.invoke(manager, medium);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native final unload failed", failure); }
    }
    public static final class JoinProbe {
        private final UUID owner; private final Consumer<EntityJoinLevelEvent> callback;
        JoinProbe(UUID owner, Consumer<EntityJoinLevelEvent> callback) { this.owner = owner; this.callback = callback; }
        @SubscribeEvent public void join(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof DisplacedNeedleEntity medium && medium.isInterceptionNeedle()
                    && medium.identity() != null && medium.identity().owner().equals(owner)) callback.accept(event);
        }
    }
    public static final class SectionProbe {
        private final DisplacedNeedleEntity medium; private final Consumer<EntityEvent.EnteringSection> callback;
        SectionProbe(DisplacedNeedleEntity medium, Consumer<EntityEvent.EnteringSection> callback) { this.medium = medium; this.callback = callback; }
        @SubscribeEvent public void enter(EntityEvent.EnteringSection event) { if (event.getEntity() == medium) callback.accept(event); }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final AcademyWirelessDeviceRuntimeTests.NativeMenuActors actors; final ServerPlayer p;
        final AtomicInteger payments = new AtomicInteger(); final List<Object> probes = new ArrayList<>();
        final List<DisplacedNeedleEntity> entities = new ArrayList<>(); final List<ItemEntity> items = new ArrayList<>();
        DisplacedNeedleEntity last; int serial, joins;
        Fixture(GameTestHelper h, int stock) {
            this.h = h; actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h); p = actors.player();
            p.getInventory().clearContent(); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), stock));
            listen(event -> { });
            joins = 0;
        }
        Vec3 position() { return h.absoluteVec(new Vec3(5.5, 3.5, 5.5)); }
        InterceptionNeedleRegistry registry() { return InterceptionNeedleRegistry.get(h.getLevel()); }
        DisplacedNeedleLedger.Snapshot state() { return registry().snapshot(p.getUUID()).orElseThrow(); }
        CompoundTag save() { return registry().save(new CompoundTag(), h.getLevel().registryAccess()); }
        void install(CompoundTag tag) {
            h.getLevel().getServer().overworld().getDataStorage().set(InterceptionNeedleRegistry.ID,
                    InterceptionNeedleRegistry.load(h.getLevel().getServer(), tag, h.getLevel().registryAccess()));
        }
        int needles() { return count(p, AcademyCraft.NEEDLE.get()); }
        boolean pay() { payments.incrementAndGet(); return true; }
        boolean deploy() { return deploy(this::pay); }
        boolean deploy(BooleanSupplier payment) {
            var result = InterceptionNeedleRegistry.deploy(p, position().add(serial * 1.5, 0, 0), () -> true, payment);
            if (result.isPresent()) { last = result.get(); if (!entities.contains(last)) entities.add(last); serial++; }
            return result.isPresent();
        }
        void listen(Consumer<EntityJoinLevelEvent> callback) {
            var probe = new JoinProbe(p.getUUID(), event -> { if (!entities.contains(event.getEntity())) { joins++; entities.add((DisplacedNeedleEntity)event.getEntity()); } callback.accept(event); });
            probes.add(probe); NeoForge.EVENT_BUS.register(probe);
        }
        void listenSection(DisplacedNeedleEntity medium, Consumer<EntityEvent.EnteringSection> callback) {
            var probe = new SectionProbe(medium, callback); probes.add(probe); NeoForge.EVENT_BUS.register(probe);
        }
        void clearListeners() { probes.forEach(NeoForge.EVENT_BUS::unregister); probes.clear(); }
        DisplacedNeedleEntity decode(CompoundTag tag, int type) {
            var entity = new DisplacedNeedleEntity(type == 331 ? TeleporterNeedleEntities.INTERCEPTION_NEEDLE.get() : type == 321 ? TeleporterNeedleEntities.CROSS_NEEDLE.get() : TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            entity.load(tag); entities.add(entity); return entity;
        }
        void embed(DisplacedNeedleEntity medium) {
            h.getLevel().setBlockAndUpdate(BlockPos.containing(medium.position()).below(), Blocks.STONE.defaultBlockState());
            medium.setDeltaMovement(0, -1, 0); for (int tick = 0; tick < 12; tick++) medium.tick();
            h.assertTrue(medium.inGround && medium.shakeTime == 0, "actual native gravity/impact embeds a pickup-ready medium");
        }
        void expireNaturally(DisplacedNeedleEntity medium) {
            embed(medium); int age = life(medium); h.assertTrue(age > 0 && age < 1200, "native lifetime begins below expiry");
            for (int tick = 0; tick < 1200 && life(medium) < 1199 && !medium.isRemoved(); tick++) medium.tick();
            h.assertTrue(!medium.isRemoved() && life(medium) == 1199, "full original native lifetime reaches1199 without synthetic shortening");
            medium.tick(); h.assertTrue(medium.isRemoved() && medium.getRemovalReason() == Entity.RemovalReason.DISCARDED, "genuine1200th native despawn tick");
        }
        @Override public void close() {
            clearListeners(); for (var medium : entities) if (!medium.isRemoved()) medium.discard();
            items.forEach(item -> { if (!item.isRemoved()) item.discard(); }); actors.close();
        }
    }
}
