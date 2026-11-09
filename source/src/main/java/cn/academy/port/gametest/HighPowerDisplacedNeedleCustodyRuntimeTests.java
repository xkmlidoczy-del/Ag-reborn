package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.DisplacedNeedleEntity;
import cn.academy.port.highpower.DisplacedNeedleRegistry;
import cn.academy.port.highpower.TeleporterNeedleEntities;
import cn.academy.port.highpower.core.DisplacedNeedleLedger;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Native addFreshEntity/EntityJoinLevelEvent, Inventory.add, playerTouch, death and respawn.
 * Players use real PlayerList admission with an explicitly mocked network connection.
 * NBT reconstruction fixtures exercise production codecs and removal seams; they do not
 * claim a process restart or a real chunk unload. No fixture invokes an ability controller.
 */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerDisplacedNeedleCustodyRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_displaced_needle_custody";
    private HighPowerDisplacedNeedleCustodyRuntimeTests() { }

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
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "refund ticket cannot replay");
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
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "full inventory cannot consume escrow");
            f.p.getInventory().setItem(2, ItemStack.EMPTY);
            h.assertTrue(DisplacedNeedleRegistry.retryRefund(f.p), "new capacity receives retained original");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "repeat retry cannot duplicate");
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
            h.assertTrue(DisplacedNeedleRegistry.availability(f.p) == DisplacedNeedleRegistry.Availability.REFUND_PENDING,
                    "safe escrow refund differs from an unresolved outstanding entity");
            CompoundTag saved = f.registry().save(new CompoundTag(), h.getLevel().registryAccess());
            CompoundTag originalEscrow = ownedEntry(saved, f.p.getUUID()).getCompound("escrow").copy();
            var restored = DisplacedNeedleRegistry.load(h.getLevel().getServer(), saved, h.getLevel().registryAccess());
            h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID, restored);
            h.assertTrue(ownedEntry(restored.save(new CompoundTag(), h.getLevel().registryAccess()), f.p.getUUID())
                    .getCompound("escrow").equals(originalEscrow), "production codec retains exactly original serialized one-item escrow");
            h.assertTrue(DisplacedNeedleRegistry.availability(f.p) == DisplacedNeedleRegistry.Availability.REFUND_PENDING,
                    "decoded safe cancellation remains explicitly refundable");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "restored full inventory cannot consume retained escrow");
            h.assertValueEqual(f.needles(), 0, "no refund while capacity is unavailable");
            h.assertValueEqual(restored.retainedEscrowCount(f.p.getUUID()), 1, "full retry retains original item after reload");
            f.p.getInventory().setItem(0, ItemStack.EMPTY);
            h.assertTrue(DisplacedNeedleRegistry.retryRefund(f.p), "new native inventory capacity receives original persisted escrow");
            h.assertTrue(ItemStack.matches(f.p.getInventory().getItem(0), new ItemStack(AcademyCraft.NEEDLE.get())),
                    "native insertion returns one original plain academy needle");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "same decoded refund ticket cannot be replayed");
            h.assertValueEqual(f.needles(), 1, "exactly one original returned across all retries");
            h.assertValueEqual(restored.retainedEscrowCount(f.p.getUUID()), 0, "successful native insertion clears escrow");
            h.assertTrue(f.state().nonce() == nonce && f.state().state() == DisplacedNeedleLedger.State.SPENT
                    && f.state().custody() == DisplacedNeedleLedger.Custody.RETURNED,
                    "refund consumes original ticket without creating a deployment nonce");
            CompoundTag settled = restored.save(new CompoundTag(), h.getLevel().registryAccess());
            h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID,
                    DisplacedNeedleRegistry.load(h.getLevel().getServer(), settled, h.getLevel().registryAccess()));
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "settled saved record cannot replay return after another reload");
            h.assertTrue(f.state().nonce() == nonce, "reloaded settled return preserves nonce");
            h.assertValueEqual(f.needles(), 1, "second reload creates no item");
            h.assertValueEqual(f.payments.get(), 0, "all refund retries are free and never enter payment seam");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_join_exception_and_payment_refusal_both_conserve_item(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            f.listen(event -> { throw new IllegalStateException("Declared GameTest native join failure"); });
            h.assertFalse(f.deploy(), "native join exception rejects deployment");
            h.assertValueEqual(f.needles(), 2, "throwing callback returns only retained original");
            f.clearListeners();
            h.assertTrue(DisplacedNeedleRegistry.deploy(f.p, f.position(), () -> true, () -> false).isEmpty(),
                    "native admission followed by resource refusal remains uncommitted");
            h.assertValueEqual(f.needles(), 2, "resource refusal conserves physical stock");
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).isEmpty(), "no accepted candidate survives cleanup");
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
            h.assertTrue(DisplacedNeedleRegistry.availability(f.p) == DisplacedNeedleRegistry.Availability.OUTSTANDING,
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
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).isEmpty(), "relocated candidate is not activated");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_pickup_full_inventory_then_once_and_same_entity_reprojection(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertTrue(f.deploy(), "one physical medium admitted");
            var medium = f.last;
            UUID uuid = medium.getUUID(); int id = medium.getId();
            h.assertValueEqual(f.needles(), 1, "one actual offhand item spent");
            h.assertFalse(f.deploy(), "outstanding owner cannot consume second offhand item");
            h.assertTrue(DisplacedNeedleRegistry.reproject(f.p, medium, f.position().add(1, 0, 0),
                    () -> true, () -> { f.payments.incrementAndGet(); return true; }), "existing entity reprojects");
            h.assertTrue(medium.getUUID().equals(uuid) && medium.getId() == id && medium.identity().generation() == 2,
                    "reprojection retains native UUID/id and advances ledger generation");
            h.assertFalse(medium.isContactEnabled(), "foundation reprojection never grants contact/damage");
            h.assertValueEqual(f.needles(), 1, "reprojection consumes no second item");
            f.embed(medium);
            // Keep the second original in a known offhand before filling all main slots.
            fillInventory(f.p);
            ItemStack second = f.p.getOffhandItem(); f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
            medium.playerTouch(f.p);
            h.assertFalse(medium.isRemoved(), "native full inventory pickup leaves same entity");
            h.assertValueEqual(medium.mediumSnapshot().getCount(), 1, "native medium still owns original stack");
            h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.ACTIVE, "full pickup retains active custody");
            f.p.getInventory().setItem(0, second);
            medium.playerTouch(f.p); medium.playerTouch(f.p);
            h.assertTrue(medium.isRemoved(), "native pickup removes exact transferred entity");
            h.assertValueEqual(f.needles(), 2, "two original needles, no pickup replay");
            h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.SPENT, "pickup settles ledger once");
            h.assertValueEqual(f.payments.get(), 2, "first deployment plus one same-entity reprojection only");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_death_respawn_keeps_global_identity_and_recovers_same_physical_item(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.deploy(), "one medium deployed before death");
            var medium = f.last; var before = f.state();
            f.p.setInvulnerable(false);
            f.p.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            h.assertFalse(f.p.isAlive(), "actual native fatal damage completed");
            h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.ACTIVE, "death preserves recoverable physical entity custody");
            ServerPlayer replacement = h.getLevel().getServer().getPlayerList().respawn(f.p, false, Entity.RemovalReason.KILLED);
            f.replacement = replacement;
            h.assertTrue(replacement != f.p && replacement.getUUID().equals(f.p.getUUID()), "native PlayerList respawn clones owner identity");
            h.assertValueEqual(count(replacement, AcademyCraft.NEEDLE.get()), 0, "clone does not mint a medium");
            h.assertTrue(f.registry().snapshot(replacement.getUUID()).orElseThrow().nonce() == before.nonce(), "clone shares one server-wide nonce record");
            h.assertTrue(DisplacedNeedleRegistry.existing(replacement).orElse(null) == medium, "respawn resolves the same exact physical entity");
            f.embed(medium); medium.playerTouch(replacement); medium.playerTouch(replacement);
            h.assertValueEqual(count(replacement, AcademyCraft.NEEDLE.get()), 1, "respawn recovers original exactly once");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_spawn_callback_death_retains_escrow_until_native_respawn(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            f.listen(event -> {
                f.p.setInvulnerable(false);
                f.p.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            });
            h.assertFalse(f.deploy(), "owner dying in join callback prevents payment/activation");
            h.assertFalse(f.p.isAlive(), "native callback death occurred");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "dead owner retains original refund escrow");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "dead player's inventory is not a refund destination");
            f.clearListeners();
            f.replacement = h.getLevel().getServer().getPlayerList().respawn(f.p, false, Entity.RemovalReason.KILLED);
            h.assertTrue(DisplacedNeedleRegistry.retryRefund(f.replacement), "native respawn receives retained original");
            h.assertValueEqual(count(f.replacement, AcademyCraft.NEEDLE.get()), 1, "one return after death and clone");
            h.assertValueEqual(f.payments.get(), 0, "death before commit spends nothing");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_nbt_reconstruction_requires_original_registry_and_loaded_identity(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertTrue(f.deploy(), "medium exists before reconstruction fixture");
            var first = f.last;
            CompoundTag entityTag = new CompoundTag(); h.assertTrue(first.save(entityTag), "native entity NBT saved");
            first.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // explicit synthetic unload disposition
            CompoundTag registryTag = f.registry().save(new CompoundTag(), h.getLevel().registryAccess());
            var restoredRegistry = DisplacedNeedleRegistry.load(h.getLevel().getServer(), registryTag, h.getLevel().registryAccess());
            h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID, restoredRegistry);
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).isEmpty(), "missing/unloaded entity gives no lookup result");
            h.assertFalse(f.deploy(), "missing/unloaded outstanding UUID cannot consume a second item");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "absence cannot manufacture a refund");
            var restored = new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            restored.load(entityTag); f.extra.add(restored);
            h.assertFalse(restored.isAdmitted(), "native decode never restores pickup authority automatically");
            h.assertFalse(restored.isContactEnabled(), "native decode never restores attack authority");
            h.assertFalse(DisplacedNeedleRegistry.tryBind(restored), "not-yet-inserted reconstruction lacks world proof");
            h.assertTrue(h.getLevel().addFreshEntity(restored), "reconstructed native entity inserted");
            h.assertTrue(DisplacedNeedleRegistry.tryBind(restored), "same owner/nonce/UUID/world rebind succeeds");
            h.assertFalse(restored.isContactEnabled(), "registry rebind still grants no attack");
            var impostor = new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            impostor.load(entityTag); f.extra.add(impostor);
            h.assertFalse(DisplacedNeedleRegistry.tryBind(impostor), "same serialized UUID on a second object cannot replace native identity");
            impostor.playerTouch(f.p);
            h.assertValueEqual(f.needles(), 1, "unbound synthetic duplicate cannot grant pickup");
            f.embed(restored); restored.playerTouch(f.p);
            h.assertValueEqual(f.needles(), 2, "only actual reloaded original is recoverable");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void corrupt_saved_metadata_is_preserved_and_does_not_reset_owner_custody(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertTrue(f.deploy(), "active identity provides real serialized record");
            CompoundTag original = f.registry().save(new CompoundTag(), h.getLevel().registryAccess());
            CompoundTag corrupt = original.copy();
            CompoundTag owned = ownedEntry(corrupt, f.p.getUUID());
            owned.getCompound("ledger").putString("nonce", "01");
            var blockedOwner = DisplacedNeedleRegistry.load(h.getLevel().getServer(), corrupt, h.getLevel().registryAccess());
            h.assertTrue(blockedOwner.save(new CompoundTag(), h.getLevel().registryAccess()).equals(corrupt), "invalid original owner bytes retained, never overwritten with empty ledger");
            h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID, blockedOwner);
            h.assertTrue(DisplacedNeedleRegistry.availability(f.p) == DisplacedNeedleRegistry.Availability.CORRUPT, "corrupt differs from absent medium");
            h.assertFalse(f.deploy(), "corrupt metadata refuses a fresh reservation");
            h.assertValueEqual(f.needles(), 1, "remaining inventory unchanged by corrupt path");
            CompoundTag duplicate = original.copy();
            duplicate.getList("entries", Tag.TAG_COMPOUND).add(ownedEntry(duplicate, f.p.getUUID()).copy());
            var duplicateRegistry = DisplacedNeedleRegistry.load(h.getLevel().getServer(), duplicate, h.getLevel().registryAccess());
            h.assertTrue(duplicateRegistry.isBlocked() && duplicateRegistry.save(new CompoundTag(), h.getLevel().registryAccess()).equals(duplicate), "duplicate owners preserve the entire original and block globally");
            CompoundTag overflow = new CompoundTag(); overflow.putInt("version", 1); ListTag many = new ListTag();
            for (int i = 0; i <= DisplacedNeedleRegistry.MAX_OWNERS; i++) many.add(new CompoundTag());
            overflow.put("entries", many);
            var bounded = DisplacedNeedleRegistry.load(h.getLevel().getServer(), overflow, h.getLevel().registryAccess());
            h.assertTrue(bounded.isBlocked() && bounded.ownerCount() == 0, "oversized records never allocate unbounded owner ledgers");
            h.assertTrue(bounded.save(new CompoundTag(), h.getLevel().registryAccess()).equals(overflow), "oversized metadata retained verbatim");
            h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID,
                    DisplacedNeedleRegistry.load(h.getLevel().getServer(), original, h.getLevel().registryAccess()));
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void synthetic_midrefund_save_ambiguity_keeps_original_and_never_replays_inventory(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p);
            f.listen(event -> {
                f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
                event.setCanceled(true);
            });
            h.assertFalse(f.deploy(), "native cancellation establishes real retained refund escrow");
            CompoundTag saved = f.registry().save(new CompoundTag(), h.getLevel().registryAccess());
            // Explicit synthetic save-window fixture: native Inventory.add has no event
            // inside its transfer. Production decode must reject this persisted ambiguity.
            ownedEntry(saved, f.p.getUUID()).getCompound("ledger").putString("custody", "REFUND_ESCROW");
            var interrupted = DisplacedNeedleRegistry.load(h.getLevel().getServer(), saved, h.getLevel().registryAccess());
            h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID, interrupted);
            f.p.getInventory().setItem(0, ItemStack.EMPTY);
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "interrupted native insertion has no replayable return ticket");
            h.assertTrue(interrupted.snapshot(f.p.getUUID()).orElseThrow().state() == DisplacedNeedleLedger.State.LOST,
                    "persisted in-flight refund restores outstanding ambiguity");
            h.assertValueEqual(interrupted.retainedEscrowCount(f.p.getUUID()), 1, "original serialized escrow retained for reconciliation");
            h.assertValueEqual(f.needles(), 0, "even available inventory capacity cannot recreate an ambiguous return");
            h.assertFalse(f.deploy(), "ambiguous outstanding owner cannot begin a replacement");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_dimension_change_keeps_one_server_wide_outstanding_medium(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertTrue(f.deploy(), "one genuine medium in original dimension");
            var medium = f.last;
            var nether = h.getLevel().getServer().getLevel(net.minecraft.world.level.Level.NETHER);
            h.assertTrue(nether != null, "native server has independent Nether level");
            f.p.teleportTo(nether, 0.5, 80, 0.5, 0, 0);
            h.assertTrue(DisplacedNeedleRegistry.get(nether) == f.registry(), "different dimensions share overworld SavedData object");
            h.assertTrue(DisplacedNeedleRegistry.deploy(f.p, new Vec3(0.5, 81, 0.5), () -> true,
                    () -> { f.payments.incrementAndGet(); return true; }).isEmpty(), "cross-dimension reentry cannot reserve second item");
            h.assertValueEqual(f.needles(), 1, "remaining original offhand stock is untouched");
            h.assertValueEqual(f.payments.get(), 1, "only original deployment payment");
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).orElse(null) == medium, "lookup resolves actual original-dimension entity without replacing it");
            f.p.teleportTo(h.getLevel(), f.position().x, f.position().y, f.position().z, 0, 0);
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void decorated_or_wrong_offhand_never_enters_custody_or_spends(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME, Component.literal("Declared decorated fixture"));
            h.assertFalse(f.deploy(), "named academy needle is not a plain custody medium");
            h.assertValueEqual(f.p.getOffhandItem().getCount(), 3, "decorated stack unchanged");
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 3));
            h.assertFalse(f.deploy(), "ordinary arrows cannot substitute for academy needle");
            h.assertTrue(DisplacedNeedleRegistry.availability(f.p) == DisplacedNeedleRegistry.Availability.NO_MEDIUM, "refusal distinguishes missing plain medium");
            h.assertValueEqual(f.payments.get(), 0, "wrong media never call payment seam");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_arrow_lifetime_consumes_medium_without_refund_and_requires_fresh_stock(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertTrue(f.deploy(), "one actual needle deployed before native lifetime expiry");
            var expired = f.last;
            var oldIdentity = expired.identity();
            f.expireNaturally(expired);
            h.assertTrue(f.state().state() == DisplacedNeedleLedger.State.SPENT
                    && f.state().custody() == DisplacedNeedleLedger.Custody.CONSUMED,
                    "pinned native1200-tick discard settles consumed custody");
            h.assertTrue(h.getLevel().getEntity(expired.getUUID()) == null, "expired native entity is no longer in world index");
            h.assertValueEqual(f.needles(), 1, "expiry creates no inventory refund");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 0, "active expiry cannot recreate escrow");
            h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "consumed medium has no return ticket");
            expired.discard(); expired.tick(); expired.playerTouch(f.p);
            f.registry().onRemoved(expired, oldIdentity, Entity.RemovalReason.DISCARDED);
            f.registry().onRemoved(expired, oldIdentity, Entity.RemovalReason.DISCARDED);
            h.assertValueEqual(f.needles(), 1, "repeated removal/touch callbacks cannot grant consumed item");
            h.assertTrue(f.deploy(), "verified native consumption permits a new deployment");
            var fresh = f.last;
            h.assertTrue(fresh != expired && !fresh.getUUID().equals(expired.getUUID())
                    && fresh.identity().nonce() == oldIdentity.nonce() + 1,
                    "fresh original offhand stock creates a distinct next nonce");
            h.assertValueEqual(f.needles(), 0, "second deployment physically removes the second original needle");
            h.assertValueEqual(f.payments.get(), 2, "each admitted generation spends exactly once");
            h.assertFalse(f.deploy(), "new outstanding medium prevents another deployment");
            f.registry().onRemoved(expired, oldIdentity, Entity.RemovalReason.DISCARDED);
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).orElse(null) == fresh,
                    "old removal replay cannot settle the new nonce");
            f.embed(fresh); fresh.playerTouch(f.p); fresh.playerTouch(f.p);
            h.assertValueEqual(f.needles(), 1, "only the second original remains recoverable after first was consumed");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_expiry_leave_event_reentry_never_duplicates_custody_or_grants_item(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            h.assertTrue(f.deploy(), "one original deployed before native removal callback");
            var expired = f.last;
            var oldIdentity = expired.identity();
            AtomicInteger leaves = new AtomicInteger(), admittedInLeave = new AtomicInteger();
            f.listenLeave(expired, event -> {
                leaves.incrementAndGet();
                h.assertTrue(event.getEntity() == expired && expired.isRemoved(), "real leave event observes exact removed object");
                expired.playerTouch(f.p);
                h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "leave callback cannot refund consumed physical custody");
                for (int attempt = 0; attempt < 3; attempt++) if (f.deploy()) admittedInLeave.incrementAndGet();
            });
            f.expireNaturally(expired);
            h.assertValueEqual(leaves.get(), 1, "one genuine EntityLeaveLevelEvent for native expiry");
            h.assertTrue(admittedInLeave.get() <= 1, "removal callback may never admit multiple outstanding media");
            h.assertValueEqual(f.needles(), 2 - admittedInLeave.get(), "callback can spend only genuine remaining stock");
            h.assertValueEqual(f.payments.get(), 1 + admittedInLeave.get(), "callbacks cannot charge without exact admitted medium");
            if (admittedInLeave.get() == 0) h.assertTrue(f.deploy(), "after settled removal one fresh-stock deployment succeeds");
            var fresh = f.last;
            h.assertTrue(fresh != expired && fresh.identity().nonce() == oldIdentity.nonce() + 1,
                    "exactly one new nonce exists after removal reentry");
            f.registry().onRemoved(expired, oldIdentity, Entity.RemovalReason.DISCARDED);
            expired.discard(); expired.playerTouch(f.p);
            h.assertValueEqual(f.needles(), 1, "one expired plus one outstanding leaves one original inventory needle");
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).orElse(null) == fresh, "replayed old callback cannot replace new entity custody");
            h.assertFalse(f.deploy(), "fresh outstanding identity still blocks all further callback attempts");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void stale_preexpiry_nbt_cannot_rebind_after_consumption_or_new_nonce_registry_reload(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertTrue(f.deploy(), "first genuine original deployed");
            var expired = f.last;
            CompoundTag oldEntity = new CompoundTag(); h.assertTrue(expired.save(oldEntity), "native pre-expiry entity NBT captured");
            long oldNonce = expired.identity().nonce();
            f.expireNaturally(expired);
            CompoundTag consumedSave = f.registry().save(new CompoundTag(), h.getLevel().registryAccess());
            var consumedRegistry = DisplacedNeedleRegistry.load(h.getLevel().getServer(), consumedSave, h.getLevel().registryAccess());
            h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID, consumedRegistry);
            // Explicit adversarial NBT reconstruction, not a claim of an atomic disk restart:
            // a stale pre-expiry entity file must not revive against newer durable custody.
            var stale = new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            stale.load(oldEntity); f.extra.add(stale);
            h.assertTrue(h.getLevel().addFreshEntity(stale), "native world accepts the declared stale-file reconstruction fixture");
            h.assertFalse(DisplacedNeedleRegistry.tryBind(stale), "post-consumption saved registry rejects stale old entity");
            stale.tick(); stale.playerTouch(f.p);
            h.assertFalse(stale.isAdmitted(), "old persisted medium stays inert after consumed-state reload");
            h.assertValueEqual(f.needles(), 1, "stale entity grants no consumed needle");
            h.assertTrue(f.deploy(), "second original still permits fresh nonce after consumed registry reload");
            var fresh = f.last;
            h.assertTrue(fresh.identity().nonce() == oldNonce + 1, "new reservation advances durable nonce");
            CompoundTag freshEntity = new CompoundTag(); h.assertTrue(fresh.save(freshEntity), "current entity NBT captured");
            fresh.remove(Entity.RemovalReason.UNLOADED_TO_CHUNK); // explicit codec/rebind fixture
            CompoundTag currentSave = f.registry().save(new CompoundTag(), h.getLevel().registryAccess());
            var currentRegistry = DisplacedNeedleRegistry.load(h.getLevel().getServer(), currentSave, h.getLevel().registryAccess());
            h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID, currentRegistry);
            h.assertFalse(DisplacedNeedleRegistry.tryBind(stale), "old nonce cannot bind against newly restored outstanding nonce");
            stale.tick(); stale.playerTouch(f.p);
            h.assertValueEqual(f.needles(), 0, "two real deployments consumed inventory and stale reconstruction cannot refund either");
            var current = new DisplacedNeedleEntity(TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            current.load(freshEntity); f.extra.add(current);
            h.assertTrue(h.getLevel().addFreshEntity(current), "current native entity reconstruction inserted");
            h.assertTrue(DisplacedNeedleRegistry.tryBind(current), "only matching new nonce/world/UUID rebinds");
            f.registry().onRemoved(stale, stale.identity(), Entity.RemovalReason.DISCARDED);
            h.assertTrue(DisplacedNeedleRegistry.existing(f.p).orElse(null) == current, "stale removal cannot settle restored new nonce");
            f.embed(current); current.playerTouch(f.p); stale.playerTouch(f.p);
            h.assertValueEqual(f.needles(), 1, "only still-existing second original is recovered");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void manual_discard_and_native_unload_do_not_impersonate_lifetime_consumption(GameTestHelper h) {
        for (var reason : new Entity.RemovalReason[]{Entity.RemovalReason.DISCARDED, Entity.RemovalReason.UNLOADED_TO_CHUNK}) {
            try (Fixture f = new Fixture(h, 2)) {
                h.assertTrue(f.deploy(), "genuine original deployed for non-expiry removal fixture");
                var medium = f.last; var identity = medium.identity();
                medium.remove(reason);
                h.assertTrue(medium.isRemoved(), "native removal seam ran");
                h.assertTrue(f.state().state() == (reason == Entity.RemovalReason.DISCARDED
                        ? DisplacedNeedleLedger.State.LOST : DisplacedNeedleLedger.State.ACTIVE),
                        "manual discard stays ambiguous; unload retains outstanding active identity");
                f.registry().onRemoved(medium, identity, Entity.RemovalReason.DISCARDED);
                h.assertFalse(f.deploy(), "generic discard or unload cannot authorize replacement stock");
                h.assertFalse(DisplacedNeedleRegistry.retryRefund(f.p), "generic absence cannot create a refund");
                h.assertValueEqual(f.needles(), 1, "remaining stock unchanged by unresolved removal");
                h.assertValueEqual(f.payments.get(), 1, "no payment after unresolved removal");
                CompoundTag saved = f.registry().save(new CompoundTag(), h.getLevel().registryAccess());
                h.getLevel().getServer().overworld().getDataStorage().set(DisplacedNeedleRegistry.ID,
                        DisplacedNeedleRegistry.load(h.getLevel().getServer(), saved, h.getLevel().registryAccess()));
                h.assertFalse(f.deploy(), "restored unresolved removal remains outstanding");
                h.assertValueEqual(f.needles(), 1, "registry reload never mints or spends a replacement");
            }
        }
        h.succeed();
    }

    private static CompoundTag ownedEntry(CompoundTag root, UUID owner) {
        for (Tag value : root.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag)value;
            if (owner.toString().equals(entry.getString("owner"))) return entry;
        }
        throw new IllegalStateException("Fixture owner record absent");
    }
    private static void fillInventory(ServerPlayer p) {
        for (int i = 0; i < 36; i++) p.getInventory().setItem(i, new ItemStack(Items.DIRT, 64));
    }
    private static int count(ServerPlayer p, Item item) {
        int n = 0;
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            var stack = p.getInventory().getItem(i); if (stack.is(item)) n += stack.getCount();
        }
        return n;
    }
    public static final class JoinProbe {
        private final UUID owner; private final Consumer<EntityJoinLevelEvent> callback;
        JoinProbe(UUID owner, Consumer<EntityJoinLevelEvent> callback) { this.owner = owner; this.callback = callback; }
        @SubscribeEvent public void join(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof DisplacedNeedleEntity medium && medium.identity() != null
                    && medium.identity().owner().equals(owner)) callback.accept(event);
        }
    }
    public static final class LeaveProbe {
        private final DisplacedNeedleEntity exact; private final Consumer<EntityLeaveLevelEvent> callback;
        LeaveProbe(DisplacedNeedleEntity exact, Consumer<EntityLeaveLevelEvent> callback) { this.exact = exact; this.callback = callback; }
        @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
            if (event.getEntity() == exact) callback.accept(event);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h;
        final AcademyWirelessDeviceRuntimeTests.NativeMenuActors actors;
        final ServerPlayer p;
        final AtomicInteger payments = new AtomicInteger();
        final List<JoinProbe> probes = new ArrayList<>();
        final List<LeaveProbe> leaveProbes = new ArrayList<>();
        final List<DisplacedNeedleEntity> extra = new ArrayList<>();
        DisplacedNeedleEntity last;
        ServerPlayer replacement;
        int joins;
        Fixture(GameTestHelper h, int needles) {
            this.h = h; actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h); p = actors.player();
            p.getInventory().clearContent(); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), needles));
        }
        Vec3 position() { return h.absoluteVec(new Vec3(5.5, 3.5, 5.5)); }
        DisplacedNeedleRegistry registry() { return DisplacedNeedleRegistry.get(h.getLevel()); }
        DisplacedNeedleLedger.Snapshot state() { return registry().snapshot(p.getUUID()).orElseThrow(); }
        int needles() { return count(p, AcademyCraft.NEEDLE.get()); }
        boolean deploy() {
            var result = DisplacedNeedleRegistry.deploy(p, position(), () -> true,
                    () -> { payments.incrementAndGet(); return true; });
            if (result.isPresent()) { last = result.get(); extra.add(last); }
            return result.isPresent();
        }
        void listen(Consumer<EntityJoinLevelEvent> callback) {
            JoinProbe probe = new JoinProbe(p.getUUID(), event -> { joins++; extra.add((DisplacedNeedleEntity)event.getEntity()); callback.accept(event); });
            probes.add(probe); NeoForge.EVENT_BUS.register(probe);
        }
        void listenLeave(DisplacedNeedleEntity exact, Consumer<EntityLeaveLevelEvent> callback) {
            LeaveProbe probe = new LeaveProbe(exact, callback); leaveProbes.add(probe); NeoForge.EVENT_BUS.register(probe);
        }
        void clearListeners() {
            for (var probe : probes) NeoForge.EVENT_BUS.unregister(probe); probes.clear();
            for (var probe : leaveProbes) NeoForge.EVENT_BUS.unregister(probe); leaveProbes.clear();
        }
        void expireNaturally(DisplacedNeedleEntity medium) {
            embed(medium);
            CompoundTag initial = new CompoundTag(); medium.addAdditionalSaveData(initial);
            int life = initial.getShort("life");
            h.assertTrue(life > 0 && life < 1200, "actual embedded native life begins below despawn threshold");
            for (int tick = life; tick < 1199; tick++) medium.tick();
            h.assertFalse(medium.isRemoved(), "native medium still exists at1199 embedded-life ticks");
            CompoundTag last = new CompoundTag(); medium.addAdditionalSaveData(last);
            h.assertValueEqual(last.getShort("life"), (short)1199, "full native lifetime advanced without synthetic shortening");
            medium.tick();
            h.assertTrue(medium.isRemoved() && medium.getRemovalReason() == Entity.RemovalReason.DISCARDED,
                    "native tick1200 executes vanilla discard");
        }
        void embed(DisplacedNeedleEntity medium) {
            BlockPos block = BlockPos.containing(medium.position()).below();
            h.getLevel().setBlockAndUpdate(block, Blocks.STONE.defaultBlockState());
            medium.setDeltaMovement(0, -1, 0);
            for (int tick = 0; tick < 12; tick++) medium.tick();
            CompoundTag embedded = new CompoundTag(); medium.addAdditionalSaveData(embedded);
            h.assertTrue(embedded.getBoolean("inGround"), "actual native arrow tick embedded medium in real block");
        }
        @Override public void close() {
            clearListeners();
            for (var medium : extra) if (!medium.isRemoved()) medium.discard();
            if (replacement != null && h.getLevel().getServer().getPlayerList().getPlayer(replacement.getUUID()) == replacement)
                h.getLevel().getServer().getPlayerList().remove(replacement);
            actors.close();
        }
    }
}
