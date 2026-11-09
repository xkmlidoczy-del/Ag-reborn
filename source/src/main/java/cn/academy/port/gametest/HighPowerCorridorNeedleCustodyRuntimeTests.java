package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.CorridorNeedleRegistry;
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

/** NEW341 custody through real PlayerList owners, native publication, pickup and
 * final entity-manager unload. Inventory, floor and enrollment are declared fixtures.
 * Save/load cases use production codecs and actual native lifecycle callbacks; they
 * do not claim an atomic process restart. Existing NEW311 acceptance is unchanged. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerCorridorNeedleCustodyRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_corridor_needle_custody";
    private HighPowerCorridorNeedleCustodyRuntimeTests() { }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void eight_actual_offhand_items_publish_independently_before_each_payment(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 9)) {
            List<DisplacedNeedleEntity> published = new ArrayList<>();
            f.listen(event -> {
                var candidate = (DisplacedNeedleEntity)event.getEntity();
                int index = published.size(); published.add(candidate);
                h.assertTrue(candidate.getType() == TeleporterNeedleEntities.CORRIDOR_NEEDLE.get(), "exact NEW341 native entity type");
                h.assertValueEqual(f.needles(), 8 - index, "one real offhand item removed for this emission only");
                h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "only current emission has original escrow");
                h.assertTrue(f.state(index).state() == DisplacedNeedleLedger.State.PENDING
                        && candidate.getUUID().equals(f.state(index).entityId()), "current UUID published before native join");
                h.assertValueEqual(f.payments.get(), index, "no current payment before native publication");
                h.assertFalse(candidate.isAdmitted(), "pending candidate has no native pickup or contact permission");
                h.assertFalse(f.deploy(), "native join cannot recursively reserve a sibling slot");
            });
            for (int index = 0; index < 8; index++) {
                int emission = index;
                h.assertTrue(f.deploy(() -> {
                    var candidate = published.get(emission);
                    h.assertTrue(h.getLevel().getEntity(candidate.getUUID()) == candidate
                            && h.getLevel().getEntity(candidate.getId()) == candidate, "both native world indexes precede payment");
                    h.assertTrue(f.state(emission).state() == DisplacedNeedleLedger.State.PENDING, "payment sees published pending custody");
                    return f.pay();
                }), "independent actual emission admitted: " + index);
                h.assertValueEqual(CorridorNeedleRegistry.existing(f.p).size(), index + 1, "each committed item has one live native entity");
            }
            h.assertTrue(published.stream().map(Entity::getUUID).distinct().count() == 8
                    && published.stream().map(Entity::getId).distinct().count() == 8, "eight distinct native objects, UUIDs and IDs");
            h.assertTrue(published.stream().allMatch(m -> m.isAdmitted() && m.mediumSnapshot().getCount() == 1
                    && !m.isContactEnabled()), "each entity owns exactly one original and custody alone grants no attack");
            h.assertValueEqual(f.needles(), 1, "nine originals minus eight native media");
            h.assertFalse(f.deploy(), "ninth slot is refused despite available stock");
            h.assertValueEqual(f.payments.get(), 8, "one payment per admitted original");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 0, "active media never leave duplicated escrow");
            h.assertTrue(CorridorNeedleRegistry.availability(f.p) == CorridorNeedleRegistry.Availability.OUTSTANDING, "fresh controller sees outstanding custody");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void every_emission_native_cancel_or_throw_preserves_prior_siblings_and_originals(GameTestHelper h) {
        for (int emission = 0; emission < 3; emission++) for (int mode = 0; mode < 2; mode++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(emission); var before = CorridorNeedleRegistry.existing(f.p); int selectedMode = mode;
            f.listen(event -> {
                var candidate = (DisplacedNeedleEntity)event.getEntity(); Vec3 position = candidate.position();
                candidate.setDeltaMovement(2, 3, 4); candidate.tick(); candidate.playerTouch(f.p);
                h.assertTrue(candidate.position().equals(position), "staged candidate cannot tick into flight or pick up");
                h.assertFalse(f.deploy(), "cancel/throw callback cannot publish a sibling");
                if (selectedMode == 0) event.setCanceled(true);
                else throw new IllegalStateException("Declared NEW341 native join failure");
            });
            h.assertFalse(f.deploy(), "native join rejects only selected emission");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).equals(before), "already admitted siblings retain exact object order");
            h.assertValueEqual(f.needles(), 3 - emission - mode, "safe cancellation returns original; thrown callback retains unresolved original");
            h.assertValueEqual(f.needles() + f.registry().retainedEscrowCount(f.p.getUUID()), 3 - emission,
                    "returned and retained custody conserve exact remaining originals");
            h.assertValueEqual(f.payments.get(), emission, "cancelled emission never pays");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "neither settled refund nor unresolved callback can replay");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void every_join_stack_mutation_preserves_foreign_inventory_without_snapshot_rollback(GameTestHelper h) {
        for (int emission = 0; emission < 3; emission++) try (Fixture f = new Fixture(h, 4)) {
            f.deployCount(emission); var before = CorridorNeedleRegistry.existing(f.p);
            f.listen(event -> {
                f.p.getInventory().setItem(0, f.p.getOffhandItem());
                f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 7));
                f.p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
            });
            h.assertFalse(f.deploy(), "changed actual offhand refuses current emission");
            h.assertTrue(f.p.getOffhandItem().is(Items.DIRT) && f.p.getOffhandItem().getCount() == 7,
                    "callback's replacement offhand is preserved");
            h.assertValueEqual(count(f.p, Items.DIAMOND), 3, "unrelated native inventory changes remain");
            h.assertValueEqual(f.needles(), 4 - emission, "relocated remainder plus one original refund conserve stock");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).equals(before), "earlier sibling custody is unaffected");
            h.assertValueEqual(f.payments.get(), emission, "stack revalidation precedes current CP callback");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void every_join_owner_invalidation_retains_one_original_until_owner_is_valid(GameTestHelper h) {
        for (int emission = 0; emission < 3; emission++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(emission);
            f.listen(event -> f.p.setGameMode(GameType.SPECTATOR));
            h.assertFalse(f.deploy(), "native owner mode change blocks current commit");
            h.assertValueEqual(f.payments.get(), emission, "invalid native owner never pays for current emission");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "one actual original waits for safe refund");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "invalid owner is not a refund destination");
            f.clearListeners(); f.p.setGameMode(GameType.SURVIVAL);
            h.assertTrue(CorridorNeedleRegistry.retryRefund(f.p), "valid original owner receives its retained needle");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "owner recovery cannot replay refund");
            h.assertValueEqual(f.needles(), 3 - emission, "valid-owner retry conserves all remaining originals");
            h.assertValueEqual(CorridorNeedleRegistry.existing(f.p).size(), emission, "previous siblings remain independently recoverable");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void every_join_candidate_identity_owner_and_pose_mutation_fail_closed(GameTestHelper h) {
        for (int emission = 0; emission < 3; emission++) for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(emission); var before = CorridorNeedleRegistry.existing(f.p); int selectedMode = mode;
            ServerPlayer foreign = mode == 1 ? f.actors.player() : null;
            String scenario = "emission=" + emission + ", mutation=" + List.of("UUID", "native owner NBT", "pose").get(mode);
            f.listen(event -> {
                var candidate = (DisplacedNeedleEntity)event.getEntity();
                if (selectedMode == 0) candidate.setUUID(UUID.randomUUID());
                if (selectedMode == 1) {
                    candidate.setOwner(null);
                    h.assertTrue(candidate.getOwner() == f.p, "null native ownership setter is a no-op: " + scenario);
                    // A null owner setter does not change vanilla Projectile ownership.
                    // Invoke the real native codec with another admitted owner to exercise
                    // a genuine callback mutation, without manufacturing a ledger proof.
                    CompoundTag changed = new CompoundTag(); candidate.addAdditionalSaveData(changed);
                    changed.putUUID("Owner", foreign.getUUID()); candidate.readAdditionalSaveData(changed);
                    h.assertTrue(candidate.getOwner() == foreign, "native codec actually changed candidate owner: " + scenario);
                    h.assertTrue(candidate.identity() == null && candidate.mediumSnapshot().isEmpty()
                            && !candidate.isAdmitted() && !candidate.isContactEnabled(),
                            "staged NBT reload also invalidates identity and medium, and grants no admission: " + scenario);
                }
                if (selectedMode == 2) candidate.setPos(candidate.position().add(4, 0, 0));
            });
            h.assertFalse(f.deploy(), "foreign candidate mutation prevents current emission: " + scenario);
            h.assertValueEqual(f.payments.get(), emission, "identity/owner/pose proof precedes payment: " + scenario);
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).equals(before), "candidate mutation never replaces prior siblings: " + scenario);
            h.assertValueEqual(f.needles() + f.registry().retainedEscrowCount(f.p.getUUID()), 3 - emission,
                    "safe refund or retained original conserves exact remaining stock: " + scenario);
            if (mode != 2) {
                h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "unproven mutated identity cannot issue refund: " + scenario);
                h.assertTrue(CorridorNeedleRegistry.availability(f.p) == CorridorNeedleRegistry.Availability.OUTSTANDING,
                        "unknown candidate disposition remains outstanding: " + scenario);
                f.clearListeners(); h.assertFalse(f.deploy(), "ambiguous slot blocks replacement into otherwise unused sibling slots: " + scenario);
            }
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_owner_setter_rejects_foreign_owner_and_preserves_valid_original_publication(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 8)) {
            ServerPlayer foreign = f.actors.player(); AtomicInteger joins = new AtomicInteger();
            f.listen(event -> {
                var candidate = (DisplacedNeedleEntity)event.getEntity(); var identity = candidate.identity();
                candidate.setOwner(null); candidate.setOwner(foreign);
                h.assertTrue(candidate.getOwner() == f.p && candidate.identity().equals(identity)
                        && candidate.mediumSnapshot().getCount() == 1 && !candidate.isAdmitted(),
                        "rejected setters preserve the exact original staged owner, identity and medium");
                joins.incrementAndGet();
            });
            f.deployCount(8);
            h.assertValueEqual(joins.get(), 8, "every emission receives actual native ownership-change attempts");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).stream().allMatch(m -> m.getOwner() == f.p
                    && m.identity().owner().equals(f.p.getUUID()) && m.isAdmitted()),
                    "ignored ownership changes do not invalidate otherwise genuine native publication");
            h.assertTrue(CorridorNeedleRegistry.existing(foreign).isEmpty(), "foreign owner gains no custody");
            h.assertValueEqual(f.needles(), 0, "eight original items admitted without copies or refunds");
            h.assertValueEqual(f.payments.get(), 8, "one payment per unchanged genuine publication");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void each_payment_refusal_is_safe_but_paid_exception_never_refunds_unknown_commit(GameTestHelper h) {
        for (int emission = 0; emission < 3; emission++) for (int mode = 0; mode < 2; mode++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(emission); int selectedMode = mode; var before = CorridorNeedleRegistry.existing(f.p);
            h.assertFalse(f.deploy(() -> {
                if (selectedMode == 0) return false;
                f.pay(); throw new IllegalStateException("Declared failure after external resource commit");
            }), "refused or unconfirmed current payment cannot report a committed emission");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).equals(before), "payment failure preserves earlier native siblings");
            h.assertValueEqual(f.payments.get(), emission + mode, "observed payment is never rolled back");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "neither settled return nor paid uncertainty has replayable refund");
            if (mode == 0) h.assertValueEqual(f.needles(), 3 - emission, "explicit unpaid refusal returns one known original");
            else {
                h.assertValueEqual(f.needles(), 2 - emission, "paid unknown does not return a potentially committed original");
                h.assertFalse(f.deploy(), "paid unknown blocks future publication rather than using an unused slot");
            }
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void future_inventory_is_not_reserved_and_real_control_q_keeps_remaining_originals(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            h.assertTrue(f.deploy(), "first emission consumes one actual offhand needle");
            var first = f.last;
            f.p.getInventory().setItem(f.p.getInventory().selected, f.p.getOffhandItem());
            f.p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
            h.assertTrue(f.p.drop(true), "real control-Q drops the two unreserved remaining originals");
            var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, f.p.getBoundingBox().inflate(2),
                    item -> item.getItem().is(AcademyCraft.NEEDLE.get()) && MagneticItemProvenance.ownedBy(item, f.p.getUUID()));
            h.assertValueEqual(drops.size(), 1, "one native stack drop"); f.items.add(drops.getFirst());
            h.assertValueEqual(drops.getFirst().getItem().getCount(), 2, "native Q retains both unreserved original items");
            h.assertFalse(f.deploy(), "later emission must recheck its actual current offhand");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).equals(List.of(first)), "earlier physical medium remains after future stock disappears");
            h.assertValueEqual(f.payments.get(), 1, "only first admitted emission paid");
            h.assertValueEqual(f.needles() + drops.getFirst().getItem().getCount() + first.mediumSnapshot().getCount(), 3,
                    "all three real originals remain in native custody or actual dropped stack");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void third_slot_refund_pending_blocks_new_emissions_and_preserves_two_siblings(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(2); var siblings = CorridorNeedleRegistry.existing(f.p); fillInventory(f.p);
            f.listen(event -> { f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64)); event.setCanceled(true); });
            h.assertFalse(f.deploy(), "third native publication cancelled with no inventory capacity");
            h.assertTrue(CorridorNeedleRegistry.availability(f.p) == CorridorNeedleRegistry.Availability.REFUND_PENDING,
                    "explicit safe refund differs from unresolved entity custody");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "only failed third slot retains actual original");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).equals(siblings), "full refund queue never removes two prior siblings");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "full inventory cannot consume retained original");
            f.clearListeners(); h.assertFalse(f.deploy(), "pending refund cannot be bypassed using a new slot");
            f.p.getInventory().setItem(2, ItemStack.EMPTY);
            h.assertTrue(CorridorNeedleRegistry.retryRefund(f.p), "one new capacity receives exact failed-slot original");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "retry never creates a fourth item");
            h.assertValueEqual(f.needles(), 1, "one returned inventory needle alongside two native originals");
            h.assertValueEqual(f.payments.get(), 2, "refund attempts never invoke third payment");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void persisted_safe_refund_returns_once_but_midtransfer_ambiguity_cannot_replay(GameTestHelper h) {
        for (int mode = 0; mode < 2; mode++) try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p);
            f.listen(event -> { f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64)); event.setCanceled(true); });
            h.assertFalse(f.deploy(), "native cancellation retains a real original");
            CompoundTag saved = legacyOwner(f.save(), f.p.getUUID());
            if (mode == 1) ownedSlot(saved, f.p.getUUID(), 0).getCompound("ledger").putString("custody", "REFUND_ESCROW");
            f.install(saved);
            assertEmptyMigrationSlots(h, f);
            if (mode == 0) h.assertTrue(ownedSlot(f.save(), f.p.getUUID(), 0).equals(ownedSlot(saved, f.p.getUUID(), 0)),
                    "legacy safe refund preserves its exact original ledger, dimension and escrow");
            else h.assertTrue(f.state(0).state() == DisplacedNeedleLedger.State.LOST,
                    "legacy interrupted transfer stays fail closed under normal ledger restoration");
            f.p.getInventory().setItem(0, ItemStack.EMPTY);
            h.assertTrue(CorridorNeedleRegistry.retryRefund(f.p) == (mode == 0), "only proven unpublished refund survives codec reload");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "neither completed nor ambiguous native transfer can replay");
            h.assertValueEqual(f.needles(), 1 - mode, "save/load never creates an ambiguous refund");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), mode, "unknown insertion retains original serialized evidence");
            f.install(f.save());
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "another reload cannot resurrect consumed refund authority");
            h.assertValueEqual(f.payments.get(), 0, "all cancellation and reconciliation paths are unpaid");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void selected_native_pickup_consumes_one_slot_and_keeps_other_two_exact_objects(GameTestHelper h) {
        for (int selected = 0; selected < 3; selected++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(3); var all = CorridorNeedleRegistry.existing(f.p); var chosen = all.get(selected);
            for (var medium : all) f.embed(medium);
            fillInventory(f.p); f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
            chosen.playerTouch(f.p);
            h.assertFalse(chosen.isRemoved(), "full native inventory cannot erase original medium");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).equals(all), "failed pickup retains all three exact objects");
            f.p.getInventory().setItem(0, ItemStack.EMPTY); chosen.playerTouch(f.p); chosen.playerTouch(f.p);
            var siblings = all.stream().filter(medium -> medium != chosen).toList();
            h.assertTrue(chosen.isRemoved() && CorridorNeedleRegistry.existing(f.p).equals(siblings), "only selected exact native entity is transferred");
            h.assertTrue(siblings.stream().allMatch(medium -> medium.mediumSnapshot().getCount() == 1 && !medium.isRemoved()),
                    "unselected siblings retain their own original single items");
            h.assertValueEqual(f.needles(), 1, "one original returned despite repeated touch");
            h.assertTrue(f.state(selected).state() == DisplacedNeedleLedger.State.SPENT, "only selected slot settles");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "native pickup never issues a second refund");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void legacy_three_slot_migration_and_native_reload_keep_exact_tokens_without_attack(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            f.deployCount(3);
            var returned = CorridorNeedleRegistry.existing(f.p).getFirst();
            f.embed(returned); returned.playerTouch(f.p);
            h.assertTrue(returned.isRemoved() && f.deploy(), "actual pickup and later deployment supply a noninitial legacy nonce");
            h.assertTrue(f.state(0).nonce() == 2, "legacy first slot has a real reused nonce before migration");
            var old = CorridorNeedleRegistry.existing(f.p);
            for (var medium : old) { h.assertTrue(medium.beginFlight(medium.identity()), "declared committed generation armed before native unload"); f.embed(medium); }
            var tags = old.stream().map(HighPowerCorridorNeedleCustodyRuntimeTests::nativeTag).toList();
            var identities = old.stream().map(DisplacedNeedleEntity::identity).toList();
            var manager = manager(h); var chunks = old.stream().map(m -> new ChunkPos(m.blockPosition())).distinct().toList();
            try {
                chunks.forEach(chunk -> manager.updateChunkStatus(chunk, Visibility.HIDDEN));
                for (var medium : old) nativeUnload(manager, medium);
                h.assertTrue(old.stream().allMatch(m -> m.isRemoved() && m.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK),
                        "actual native manager final removal bypasses subclass remove and reaches lifecycle listener");
                h.assertTrue(f.registry().snapshots(f.p.getUUID()).subList(0, 3).stream().allMatch(s -> s.state() == DisplacedNeedleLedger.State.ACTIVE),
                        "positive unload retains all three outstanding identities");
            } finally { chunks.forEach(chunk -> manager.updateChunkStatus(chunk, Visibility.TICKING)); }
            CompoundTag legacy = legacyOwner(f.save(), f.p.getUUID());
            f.install(legacy);
            h.assertTrue(f.registry().isDirty(), "legacy migration is persisted as the explicit new schema");
            assertEmptyMigrationSlots(h, f);
            CompoundTag migrated = f.save();
            for (int slot = 0; slot < 3; slot++)
                h.assertTrue(ownedSlot(migrated, f.p.getUUID(), slot).equals(ownedSlot(legacy, f.p.getUUID(), slot)),
                        "migration preserves each original ledger, UUID, nonce, generation, dimension and escrow exactly");
            f.install(migrated);
            h.assertTrue(f.save().equals(migrated), "v2 round trip never remigrates or appends more slots");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).isEmpty(), "unloaded originals cannot be invented by lookup");
            h.assertFalse(f.deploy(), "three unloaded slots cannot consume fourth original");
            h.assertTrue(CorridorNeedleRegistry.deploy(f.p, 7, f.position(), () -> true, f::pay).isEmpty(),
                    "newly appended exact slot cannot bypass outstanding legacy native custody");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "absence cannot manufacture refunds");
            for (int slot = 0; slot < 3; slot++) {
                var restored = f.decode(tags.get(slot), true);
                h.assertFalse(CorridorNeedleRegistry.tryBind(restored), "uninserted decoded object lacks native indexes");
                h.assertTrue(h.getLevel().addFreshEntity(restored) && CorridorNeedleRegistry.tryBind(restored), "matching actual native object rebinds exact slot");
                h.assertTrue(restored.identity().equals(identities.get(slot)) && life(restored) == tags.get(slot).getShort("life"),
                        "owner, nonce, UUID, generation and original native lifetime survive");
                h.assertFalse(restored.isContactEnabled() || restored.beginFlight(restored.identity()), "restored generation cannot rearm attack");
                var shadow = f.decode(tags.get(slot), true);
                h.assertFalse(CorridorNeedleRegistry.tryBind(shadow), "same persisted UUID on another Java object cannot replace native object");
                shadow.playerTouch(f.p);
            }
            h.assertValueEqual(CorridorNeedleRegistry.existing(f.p).size(), 3, "all three original identities independently rebind");
            h.assertValueEqual(f.needles(), 1, "reload and shadow objects create no inventory grants");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void wrong_registry_type_owner_nonce_or_generation_never_binds_a_corridor_original(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            f.deployCount(3); var corridor = CorridorNeedleRegistry.existing(f.p);
            var displaced = DisplacedNeedleRegistry.deploy(f.p, f.position().add(0, 0, 5), () -> true, () -> true).orElseThrow(); f.entities.add(displaced);
            for (var medium : corridor) {
                h.assertFalse(DisplacedNeedleRegistry.tryBind(medium), "NEW311 registry rejects exact CORRIDOR_NEEDLE type");
                h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, medium, medium.identity(), f.position().add(1, 0, 0), () -> true, f::pay),
                        "NEW311 recovery cannot spend NEW341 custody");
            }
            h.assertFalse(CorridorNeedleRegistry.tryBind(displaced) || CorridorNeedleRegistry.authentic(f.p, displaced), "NEW341 refuses exact NEW311 type");
            h.assertFalse(CorridorNeedleRegistry.recoverExisting(f.p, displaced, displaced.identity(), f.position(), () -> true, f::pay), "wrong-domain recovery never pays");
            var corridorTag = nativeTag(corridor.getFirst()); var displacedTag = nativeTag(displaced);
            h.assertTrue(corridorTag.contains("AcademyCorridorNeedle") && !corridorTag.contains("AcademyDisplacedNeedle")
                    && displacedTag.contains("AcademyDisplacedNeedle") && !displacedTag.contains("AcademyCorridorNeedle"), "native identity keys remain separated without changing NEW311 schema");
            h.assertTrue(f.decode(corridorTag, false).identity() == null && f.decode(displacedTag, true).identity() == null,
                    "opposite exact native type cannot decode another custody domain");
            var original = corridor.getFirst(); nativeUnload(manager(h), original);
            for (int mutation = 0; mutation < 3; mutation++) {
                CompoundTag forged = corridorTag.copy(); CompoundTag identity = forged.getCompound("AcademyCorridorNeedle");
                if (mutation == 0) { UUID owner = UUID.randomUUID(); identity.putUUID("Owner", owner); forged.putUUID("Owner", owner); }
                if (mutation == 1) identity.putLong("Nonce", identity.getLong("Nonce") + 1);
                if (mutation == 2) identity.putLong("Generation", identity.getLong("Generation") + 1);
                var candidate = f.decode(forged, true); h.assertTrue(h.getLevel().addFreshEntity(candidate), "declared stale/forged native decode enters world");
                h.assertFalse(CorridorNeedleRegistry.tryBind(candidate), "wrong persisted owner/nonce/generation cannot acquire original slot");
                candidate.playerTouch(f.p); candidate.discard();
            }
            var restored = f.decode(corridorTag, true);
            h.assertTrue(h.getLevel().addFreshEntity(restored) && CorridorNeedleRegistry.tryBind(restored), "unaltered exact original still rebinds after all refusals");
            h.assertValueEqual(f.payments.get(), 3, "wrong-domain proof never invokes payment");
            h.assertValueEqual(f.needles(), 0, "four physical originals remain in four independent entity records");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void corrupt_duplicate_and_oversized_saved_records_preserve_bytes_and_block_deployment(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            f.deployCount(3); CompoundTag original = f.save(); var live = f.registry();
            try {
                for (int mutation = 0; mutation < 14; mutation++) {
                    CompoundTag corrupt = original.copy(); CompoundTag owner = ownedEntry(corrupt, f.p.getUUID());
                    if (mutation == 0) ownedSlot(corrupt, f.p.getUUID(), 1).getCompound("ledger").putString("nonce", "01");
                    if (mutation == 1) owner.getList("slots", Tag.TAG_COMPOUND).getCompound(1).putInt("slot", 0);
                    if (mutation == 2) corrupt.getList("entries", Tag.TAG_COMPOUND).add(owner.copy());
                    if (mutation == 3) {
                        ListTag many = new ListTag(); for (int i = 0; i <= CorridorNeedleRegistry.MAX_OWNERS; i++) many.add(new CompoundTag()); corrupt.put("entries", many);
                    }
                    if (mutation == 4) ownedSlot(corrupt, f.p.getUUID(), 1).getCompound("ledger").putString("generation", "2");
                    if (mutation == 5) corrupt.putString("domain", "cross_substitution");
                    if (mutation == 6) owner.getList("slots", Tag.TAG_COMPOUND).remove(7);
                    if (mutation == 7) owner.getList("slots", Tag.TAG_COMPOUND).add(ownedSlot(corrupt, f.p.getUUID(), 7).copy());
                    if (mutation == 8) ownedSlot(corrupt, f.p.getUUID(), 7).putInt("slot", 8);
                    if (mutation == 9) corrupt.putInt("version", 3);
                    if (mutation == 10) corrupt.putInt("version", -1);
                    if (mutation == 11) corrupt.putString("version", "2");
                    if (mutation == 12) corrupt.putInt("unknown", 1);
                    if (mutation == 13) ownedSlot(corrupt, f.p.getUUID(), 7).putInt("ledger", 0);
                    f.install(corrupt);
                    h.assertTrue(f.save().equals(corrupt), "malformed persisted bytes retained without repairing empty slots");
                    h.assertTrue(CorridorNeedleRegistry.availability(f.p) == CorridorNeedleRegistry.Availability.CORRUPT, "corrupt state is explicit and fail closed");
                    h.assertFalse(f.deploy() || CorridorNeedleRegistry.retryRefund(f.p), "corruption grants neither publication nor refund");
                    h.assertTrue(CorridorNeedleRegistry.deploy(f.p, 7, f.position(), () -> true, f::pay).isEmpty(),
                            "corrupt owner cannot publish into an explicitly requested added slot");
                    h.assertValueEqual(f.needles(), 1, "corruption never spends remaining original stock");
                    h.assertValueEqual(f.payments.get(), 3, "no payment under malformed custody");
                }
            } finally { h.getLevel().getServer().overworld().getDataStorage().set(CorridorNeedleRegistry.ID, live); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void legacy_blocked_ledgers_preserve_original_evidence_and_never_open_added_slots(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            fillInventory(f.p);
            f.listen(event -> { f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64)); event.setCanceled(true); });
            h.assertFalse(f.deploy(), "real cancelled original supplies legacy escrow evidence");
            f.clearListeners();
            CompoundTag original = legacyOwner(f.save(), f.p.getUUID());
            var live = f.registry();
            try {
                for (int mutation = 0; mutation < 3; mutation++) {
                    CompoundTag legacy = original.copy();
                    CompoundTag blocked = ownedSlot(legacy, f.p.getUUID(), 1).getCompound("ledger");
                    if (mutation == 0) blocked.putString("nonce", "01");
                    if (mutation == 1) {
                        blocked.putString("state", "BLOCKED"); blocked.putString("custody", "UNKNOWN");
                        blocked.putString("nonce", Long.toString(Long.MAX_VALUE));
                    }
                    if (mutation == 2) ownedSlot(legacy, f.p.getUUID(), 2).putString("unknown", "retained evidence");
                    f.install(legacy);
                    assertEmptyMigrationSlots(h, f);
                    CompoundTag migrated = f.save();
                    for (int slot = 0; slot < 3; slot++)
                        h.assertTrue(ownedSlot(migrated, f.p.getUUID(), slot).equals(ownedSlot(legacy, f.p.getUUID(), slot)),
                                "blocked migration preserves all three original raw slot compounds, including retained escrow");
                    h.assertTrue(CorridorNeedleRegistry.availability(f.p) == CorridorNeedleRegistry.Availability.CORRUPT,
                            "legacy blocked ledger stays owner-wide corrupt after expansion");
                    f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 1));
                    h.assertTrue(CorridorNeedleRegistry.deploy(f.p, 7, f.position(), () -> true, f::pay).isEmpty(),
                            "new empty slot cannot bypass a preserved legacy blocked record");
                    f.p.getInventory().setItem(0, ItemStack.EMPTY);
                    h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "blocked owner cannot spend even preserved legacy refund escrow");
                    h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "one actual original remains in retained escrow");
                    h.assertValueEqual(f.payments.get(), 0, "migration and corrupt owner never invoke payment");
                    f.install(migrated);
                    h.assertTrue(f.save().equals(migrated), "blocked v2 record round trips verbatim without healing");
                }
            } finally { h.getLevel().getServer().overworld().getDataStorage().set(CorridorNeedleRegistry.ID, live); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void legacy_migration_rejects_missing_duplicate_extra_or_unknown_structures(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.deploy(), "legacy original supplies a concrete nonce and native UUID");
            CompoundTag original = legacyOwner(f.save(), f.p.getUUID());
            var live = f.registry();
            try {
                for (int mutation = 0; mutation < 9; mutation++) {
                    CompoundTag malformed = original.copy();
                    CompoundTag owner = ownedEntry(malformed, f.p.getUUID());
                    ListTag slots = owner.getList("slots", Tag.TAG_COMPOUND);
                    if (mutation == 0) slots.remove(2);
                    if (mutation == 1) slots.add(slots.getCompound(2).copy());
                    if (mutation == 2) slots.getCompound(2).putInt("slot", 1);
                    if (mutation == 3) slots.getCompound(2).putInt("slot", 3);
                    if (mutation == 4) owner.putInt("unknown", 1);
                    if (mutation == 5) malformed.putInt("version", 0);
                    if (mutation == 6) malformed.putString("domain", "short_interception");
                    if (mutation == 7) malformed.getList("entries", Tag.TAG_COMPOUND).add(owner.copy());
                    if (mutation == 8) malformed.put("entries", new CompoundTag());
                    f.install(malformed);
                    h.assertTrue(f.registry().isBlocked() && f.save().equals(malformed),
                            "unknown or malformed legacy structure is retained verbatim, never guessed or expanded");
                    h.assertTrue(CorridorNeedleRegistry.existing(f.p).isEmpty(), "malformed legacy record grants no native ownership");
                    h.assertTrue(CorridorNeedleRegistry.deploy(f.p, 7, f.position(), () -> true, f::pay).isEmpty()
                            && !CorridorNeedleRegistry.retryRefund(f.p), "malformed legacy record grants neither appended publication nor refund");
                    h.assertValueEqual(f.payments.get(), 1, "only the actual pre-migration deployment paid");
                }
            } finally { h.getLevel().getServer().overworld().getDataStorage().set(CorridorNeedleRegistry.ID, live); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void legacy_and_v2_empty_or_spent_ledgers_with_escrow_remain_verbatim_blocked(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            h.assertFalse(f.deploy(() -> false), "real unpaid cancellation produces a settled generation-zero original");
            h.assertTrue(f.state(0).state() == DisplacedNeedleLedger.State.SPENT
                    && f.state(0).custody() == DisplacedNeedleLedger.Custody.RETURNED,
                    "native cancellation supplied the spent-returned fixture");
            CompoundTag original = f.save(); var live = f.registry(); int stock = f.needles();
            try {
                for (int version : new int[]{1, 2}) for (int state = 0; state < 3; state++) {
                    CompoundTag corrupt = version == 1 ? legacyOwner(original, f.p.getUUID()) : original.copy();
                    CompoundTag slot = ownedSlot(corrupt, f.p.getUUID(), state == 0 ? 1 : 0);
                    if (state == 2) {
                        slot.getCompound("ledger").putString("custody", "NONE");
                        slot.getCompound("ledger").putString("removedItems", "0");
                    }
                    // Declared corrupt serialized fixture, never an actual item grant.
                    slot.put("escrow", new ItemStack(AcademyCraft.NEEDLE.get(), 1).save(h.getLevel().registryAccess()));
                    f.install(corrupt);
                    h.assertTrue(f.registry().isBlocked() && f.save().equals(corrupt),
                            "EMPTY, SPENT/RETURNED and SPENT/NONE cannot own escrow; retain the entire source verbatim");
                    h.assertValueEqual(f.save().getInt("version"), version, "impossible legacy escrow never migrates or rewrites its version");
                    h.assertTrue(CorridorNeedleRegistry.availability(f.p) == CorridorNeedleRegistry.Availability.CORRUPT
                            && CorridorNeedleRegistry.existing(f.p).isEmpty(), "contradictory escrow grants no availability or native ownership");
                    h.assertTrue(CorridorNeedleRegistry.deploy(f.p, 7, f.position(), () -> true, f::pay).isEmpty()
                            && !f.deploy() && !CorridorNeedleRegistry.retryRefund(f.p),
                            "contradictory escrow grants neither exact/free-slot publication nor refund");
                    h.assertTrue(f.save().equals(corrupt), "all refused actions leave original corrupt evidence unchanged");
                    h.assertValueEqual(f.needles(), stock, "blocking never consumes or creates a physical original");
                    h.assertValueEqual(f.payments.get(), 0, "blocking never invokes payment");
                }
            } finally { h.getLevel().getServer().overworld().getDataStorage().set(CorridorNeedleRegistry.ID, live); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void legacy_and_v2_valid_interrupted_escrow_states_preserve_originals_without_authority(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            CompoundTag[] pending = new CompoundTag[1];
            fillInventory(f.p);
            f.listen(event -> {
                pending[0] = f.save();
                f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
                event.setCanceled(true);
            });
            h.assertFalse(f.deploy(), "real native cancellation retains exactly one original in a full-inventory refund queue");
            f.clearListeners();
            h.assertTrue(pending[0] != null && f.state(0).state() == DisplacedNeedleLedger.State.RETURNING,
                    "native publication supplies actual pending and refund escrow snapshots");
            CompoundTag original = f.save(); var live = f.registry();
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 64));
            try {
                for (int version : new int[]{1, 2}) for (int state = 0; state < 5; state++) {
                    CompoundTag source = state == 1 || state == 4 ? pending[0] : original;
                    CompoundTag saved = version == 1 ? legacyOwner(source, f.p.getUUID()) : source.copy();
                    CompoundTag slot = ownedSlot(saved, f.p.getUUID(), 0);
                    CompoundTag ledger = slot.getCompound("ledger");
                    // Declared interrupted-state fixtures retain the original native escrow and nonce.
                    if (state == 0) { ledger.putString("state", "RESERVED"); ledger.putString("custody", "ESCROW"); }
                    if (state == 3) ledger.putString("custody", "REFUND_ESCROW");
                    if (state == 4) { ledger.putString("state", "LOST"); ledger.putString("custody", "OUTSTANDING"); }
                    f.install(saved);
                    h.assertFalse(f.registry().isBlocked(), "valid interrupted registry escrow stays readable");
                    h.assertTrue(CorridorNeedleRegistry.availability(f.p) != CorridorNeedleRegistry.Availability.CORRUPT,
                            "valid interrupted owner escrow is not mislabeled corrupt");
                    CompoundTag restored = ownedSlot(f.save(), f.p.getUUID(), 0);
                    h.assertTrue(restored.getCompound("escrow").equals(slot.getCompound("escrow"))
                            && restored.getString("dimension").equals(slot.getString("dimension"))
                            && restored.getCompound("ledger").getString("nonce").equals(ledger.getString("nonce"))
                            && restored.getCompound("ledger").getString("generation").equals(ledger.getString("generation"))
                            && restored.getCompound("ledger").getString("entity").equals(ledger.getString("entity")),
                            "restoration retains exact escrow, dimension, nonce, generation and native UUID evidence");
                    var expected = state == 0 ? DisplacedNeedleLedger.State.RESERVED
                            : state == 1 ? DisplacedNeedleLedger.State.PENDING
                            : state == 2 ? DisplacedNeedleLedger.State.RETURNING : DisplacedNeedleLedger.State.LOST;
                    h.assertTrue(f.state(0).state() == expected, "only interrupted insertion loses refund authority on restoration");
                    h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "exactly one actual original remains retained");
                    h.assertTrue(CorridorNeedleRegistry.existing(f.p).isEmpty()
                            && CorridorNeedleRegistry.deploy(f.p, 7, f.position(), () -> true, f::pay).isEmpty(),
                            "valid interrupted escrow creates neither native ownership, attack nor new publication");
                    h.assertTrue(f.p.getInventory().getFreeSlot() < 0 && f.p.getInventory().getSlotWithRemainingSpace(
                            new ItemStack(AcademyCraft.NEEDLE.get(), 1)) < 0,
                            "declared full inventory includes offhand merge capacity before the refund attempt");
                    h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "full inventory or interrupted authority never grants a speculative refund");
                    h.assertValueEqual(f.needles(), 64, "original pending escrow remains separate from unchanged full offhand stock");
                    h.assertValueEqual(f.payments.get(), 0, "restoration and refused publication never pay");
                }
            } finally { h.getLevel().getServer().overworld().getDataStorage().set(CorridorNeedleRegistry.ID, live); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void safe_refund_uses_native_offhand_merge_capacity_exactly_once_after_reload(GameTestHelper h) {
        for (int version : new int[]{1, 2}) try (Fixture f = new Fixture(h, 2)) {
            fillInventory(f.p);
            f.listen(event -> {
                f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
                event.setCanceled(true);
            });
            h.assertFalse(f.deploy(), "real unpaid cancellation retains one original when all native inventory capacity is full");
            f.clearListeners();
            h.assertTrue(f.state(0).state() == DisplacedNeedleLedger.State.RETURNING
                    && f.state(0).custody() == DisplacedNeedleLedger.Custody.RETURN_PENDING,
                    "only proven safe refund escrow enters the positive capacity control");
            CompoundTag saved = f.save();
            f.install(version == 1 ? legacyOwner(saved, f.p.getUUID()) : saved);
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 1));
            h.assertTrue(f.p.getInventory().getFreeSlot() < 0 && f.p.getInventory().getSlotWithRemainingSpace(
                    new ItemStack(AcademyCraft.NEEDLE.get(), 1)) == 40, "native offhand slot is the sole remaining merge capacity");
            h.assertTrue(CorridorNeedleRegistry.retryRefund(f.p), "safe original transfers into native offhand merge capacity");
            h.assertValueEqual(f.p.getOffhandItem().getCount(), 2, "one original merges with the one existing offhand needle");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 0, "completed native merge consumes the sole retained original");
            h.assertTrue(f.state(0).state() == DisplacedNeedleLedger.State.SPENT
                    && f.state(0).custody() == DisplacedNeedleLedger.Custody.RETURNED, "successful native refund settles custody");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "same-runtime retry cannot repeat the native return");
            f.install(f.save());
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "reload cannot restore spent refund authority");
            h.assertValueEqual(f.needles(), 2, "all retries preserve exactly the two native inventory originals");
            h.assertValueEqual(f.payments.get(), 0, "safe refund and reload never invoke payment");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void valid_owner_limit_refuses_new_owner_without_reservation_or_payment(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 0)) {
            h.assertFalse(f.deploy(), "empty offhand creates only settled diagnostic owner entry");
            var live = f.registry(); CompoundTag template = ownedEntry(f.save(), f.p.getUUID()).copy();
            CompoundTag full = new CompoundTag(); full.putInt("version", 2); full.putString("domain", CorridorNeedleRegistry.DOMAIN); ListTag entries = new ListTag();
            for (int index = 0; index < CorridorNeedleRegistry.MAX_OWNERS; index++) {
                UUID owner = UUID.randomUUID(); CompoundTag entry = template.copy(); entry.putString("owner", owner.toString());
                for (Tag value : entry.getList("slots", Tag.TAG_COMPOUND)) ((CompoundTag)value).getCompound("ledger").putString("owner", owner.toString());
                entries.add(entry);
            }
            full.put("entries", entries);
            try {
                f.install(full); f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 3));
                h.assertFalse(f.registry().isBlocked(), "exactly bounded valid owner records remain decodable");
                h.assertValueEqual(f.registry().ownerCount(), CorridorNeedleRegistry.MAX_OWNERS, "valid bounded owner count");
                h.assertTrue(CorridorNeedleRegistry.availability(f.p) == CorridorNeedleRegistry.Availability.REGISTRY_FULL, "new owner receives explicit capacity refusal");
                h.assertFalse(f.deploy(), "capacity cannot be bypassed by native publication");
                h.assertValueEqual(f.needles(), 3, "no inventory reservation when owner capacity is full");
                h.assertValueEqual(f.payments.get(), 0, "no CP callback when owner capacity is full");
                h.assertTrue(f.save().equals(full), "capacity refusal leaves every valid persisted record unchanged");
            } finally { h.getLevel().getServer().overworld().getDataStorage().set(CorridorNeedleRegistry.ID, live); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void authentic_native_expiry_settles_only_one_slot_while_unknown_removal_stays_outstanding(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            f.deployCount(3); var all = CorridorNeedleRegistry.existing(f.p); var expired = all.getFirst(); var identity = expired.identity();
            CompoundTag staleTag = nativeTag(expired); f.expireNaturally(expired);
            h.assertTrue(f.state(0).state() == DisplacedNeedleLedger.State.SPENT && f.state(0).custody() == DisplacedNeedleLedger.Custody.CONSUMED,
                    "real1200-tick native expiry proves consumption of exactly first original");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).equals(all.subList(1, 3)), "native expiry preserves two sibling objects");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "expired original cannot be refunded");
            h.assertTrue(f.deploy(), "verified consumed slot accepts one genuine remaining original"); var replacement = f.last;
            h.assertTrue(replacement.identity().nonce() == identity.nonce() + 1 && replacement != expired, "only consumed slot advances nonce");
            var stale = f.decode(staleTag, true); h.assertTrue(h.getLevel().addFreshEntity(stale), "declared old native save inserted");
            h.assertFalse(CorridorNeedleRegistry.tryBind(stale), "expired old nonce cannot replace current native identity"); stale.playerTouch(f.p);
            f.registry().onRemoved(expired, identity, Entity.RemovalReason.DISCARDED);
            h.assertTrue(CorridorNeedleRegistry.authentic(f.p, replacement), "old removal replay cannot settle replacement slot");
            var unknown = all.get(1); unknown.setRemoved(Entity.RemovalReason.DISCARDED);
            h.assertTrue(f.state(1).state() == DisplacedNeedleLedger.State.LOST, "generic native discard remains unproven custody");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "ambiguous native destruction never grants refund");
            h.assertValueEqual(f.needles(), 0, "one consumed and three deployed originals create no item grants");
            h.assertValueEqual(f.payments.get(), 4, "four admitted original emissions paid once each");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void each_selected_recovery_native_section_failure_is_paid_once_without_refund_or_sibling_change(GameTestHelper h) {
        for (int selected = 0; selected < 3; selected++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(3); var all = CorridorNeedleRegistry.existing(f.p); var medium = all.get(selected); var identity = medium.identity();
            h.assertTrue(medium.beginFlight(identity), "declared committed contact generation exists before recovery retirement");
            var positions = all.stream().map(Entity::position).toList(); Vec3 destination = medium.position().add(0, 16, 0); AtomicInteger calls = new AtomicInteger();
            f.listenSection(medium, event -> {
                calls.incrementAndGet();
                h.assertValueEqual(f.payments.get(), 4, "one selected recovery charge precedes actual native section event");
                h.assertFalse(CorridorNeedleRegistry.recoverExisting(f.p, medium, identity, positions.get(selectedIndex(all, medium)), () -> true, f::pay),
                        "native section callback cannot reenter paid recovery");
                medium.tick(); medium.playerTouch(f.p);
                throw new IllegalStateException("Declared failure after native recovery relocation");
            });
            h.assertFalse(CorridorNeedleRegistry.recoverExisting(f.p, medium, identity, destination, () -> true, f::pay), "native callback throw reports unconfirmed paid move");
            h.assertValueEqual(calls.get(), 1, "one genuine native section event");
            h.assertValueEqual(f.payments.get(), 4, "one paid attempt cannot replay through callback");
            h.assertTrue(medium.position().equals(destination) && medium.identity().equals(identity), "actual native relocation keeps original immutable custody identity");
            h.assertFalse(medium.isContactEnabled() || medium.beginFlight(identity), "recovery never rearms retired generation");
            h.assertFalse(CorridorNeedleRegistry.retryRefund(f.p), "paid native uncertainty cannot fabricate inventory escrow");
            for (int slot = 0; slot < 3; slot++) if (slot != selected)
                h.assertTrue(all.get(slot).position().equals(positions.get(slot)) && CorridorNeedleRegistry.authentic(f.p, all.get(slot)), "unselected sibling stays exactly in place");
            h.assertValueEqual(f.needles(), 0, "three original media remain physical entities after paid failure");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch="highpower_corridor_needle_recovery", timeoutTicks=45)
    public static void native_323_registers_three_retired_corridor_slots_and_q_drop_then_recovers_only_selected_slot(GameTestHelper h) {
        var base = new HighPowerRecoveryMarkRuntimeTests.Fixture(h, 5); var entities = new ArrayList<Entity>();
        h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo info) { }
            public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { cleanup(); }
            private void cleanup() { entities.forEach(entity -> { if (!entity.isRemoved()) entity.discard(); }); base.close(); }
        });
        base.needle.discard(); base.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 3));
        List<DisplacedNeedleEntity> corridor = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            double angle = Math.PI / 8 + slot * Math.PI / 2;
            Vec3 source = base.anchor.add(Math.cos(angle) * 4.3, .2, Math.sin(angle) * 4.3);
            var medium = CorridorNeedleRegistry.deploy(base.p, source, () -> true, () -> true).orElseThrow(); corridor.add(medium); entities.add(medium);
            h.assertTrue(medium.beginFlight(medium.identity()), "declared admitted generation armed before native block retirement");
            medium.setDeltaMovement(0, -1, 0); for (int tick = 0; tick < 12; tick++) medium.tick();
            h.assertFalse(medium.isContactEnabled() || medium.beginFlight(medium.identity()), "native embedding retires corridor contact without removing item");
        }
        base.p.getInventory().setItem(base.p.getInventory().selected, new ItemStack(AcademyCraft.NEEDLE.get()));
        h.assertTrue(base.p.drop(false), "actual Q produces independent original323-compatible drop");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, base.p.getBoundingBox().inflate(2),
                item -> MagneticItemProvenance.ownedBy(item, base.p.getUUID()));
        h.assertValueEqual(drops.size(), 1, "one actual original Q drop"); var drop = drops.getFirst(); entities.add(drop);
        drop.setPos(base.anchor.add(3, .2, -3)); drop.setDeltaMovement(Vec3.ZERO); drop.setNoGravity(true);
        base.aim(corridor.getFirst().getBoundingBox().getCenter()); h.assertTrue(base.press(1), "native323 registers first retired corridor original");
        base.post(8, () -> { base.aim(corridor.get(1).getBoundingBox().getCenter()); h.assertTrue(base.press(2), "native323 registers second distinct corridor original"); });
        base.post(16, () -> { base.aim(corridor.get(2).getBoundingBox().getCenter()); h.assertTrue(base.press(3), "native323 registers third distinct corridor original"); });
        base.post(24, () -> { base.aim(drop.getBoundingBox().getCenter()); h.assertTrue(base.press(4), "native323 retains ordinary Q registration beside three corridor slots"); });
        base.post(32, () -> {
            h.assertValueEqual(RecoveryMark.markedCount(base.p), 4, "three corridor originals and one native Q hold separate323 handles");
            var chosen = corridor.get(1); var identity = chosen.identity(); var all = List.copyOf(entities);
            var positions = all.stream().map(Entity::position).toList(); int age = life(chosen);
            var others = RecoveryMark.handles(base.p).stream().filter(mark -> !mark.needleUuid().equals(chosen.getUUID())).toList();
            double cp = base.cp(); base.aim(chosen.getBoundingBox().getCenter()); h.assertTrue(base.press(5), "explicit aim recalls only selected corridor original");
            var receipt = RecoveryMark.receipt(base.p);
            h.assertTrue(receipt != null && receipt.paid() && receipt.moved() && receipt.phase() == RecoveryMark.RECOVERED
                    && receipt.needleUuid().equals(chosen.getUUID()) && chosen.position().equals(base.anchor), "canonical323 receipt proves selected actual native move");
            h.assertTrue(Math.abs(base.cp() - (cp - RecoveryMarkRules.cpCost(HighPowerGrowth.values(base.p).position()))) < 1e-7, "one actual growth-derived323 CP debit");
            h.assertTrue(chosen.identity().equals(identity) && life(chosen) == age && !chosen.isContactEnabled()
                    && !chosen.beginFlight(identity), "selected323 recovery preserves native age and cannot rearm NEW341 attack");
            h.assertTrue(RecoveryMark.handles(base.p).equals(others), "unselected corridor and Q handles keep exact tokens and anchors");
            for (int i = 0; i < all.size(); i++) if (all.get(i) != chosen)
                h.assertTrue(all.get(i).position().equals(positions.get(i)), "unselected original never moves");
            h.assertTrue(CorridorNeedleRegistry.existing(base.p).equals(corridor), "323 transport does not consume or replace a custody slot");
            h.assertValueEqual(count(base.p, AcademyCraft.NEEDLE.get()), 0, "323 never grants inventory for a corridor or Q original");
        });
        base.finish(33);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void decorated_or_wrong_medium_never_opens_any_corridor_slot_or_pays(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME, Component.literal("Declared decorated corridor fixture"));
            h.assertFalse(f.deploy(), "decorated academy needle cannot enter custody");
            h.assertValueEqual(f.needles(), 3, "all decorated originals preserved");
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 3));
            h.assertFalse(f.deploy(), "ordinary arrows cannot substitute for academy needles");
            h.assertTrue(CorridorNeedleRegistry.availability(f.p) == CorridorNeedleRegistry.Availability.NO_MEDIUM, "medium refusal remains explicit");
            h.assertTrue(CorridorNeedleRegistry.existing(f.p).isEmpty(), "wrong stock never creates native medium");
            h.assertValueEqual(f.payments.get(), 0, "wrong or decorated stock cannot pay"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void frozen_slots_publish_exact_indices_with_holes_without_redirecting_or_reentering(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 9)) {
            for (int slot : new int[]{-1, 8, Integer.MAX_VALUE})
                h.assertTrue(CorridorNeedleRegistry.deploy(f.p, slot, f.position(), () -> true, f::pay).isEmpty(),
                        "out-of-range slot is refused before reservation");
            h.assertValueEqual(f.needles(), 9, "invalid input cannot reserve any original");
            h.assertValueEqual(f.payments.get(), 0, "invalid input cannot invoke payment");
            for (int slot : new int[]{7, 1, 5, 0, 6, 2, 4, 3}) {
                int current = slot;
                f.listen(event -> {
                    h.assertTrue(f.state(current).state() == DisplacedNeedleLedger.State.PENDING,
                            "frozen slot is pending before native callback");
                    for (int nested = 0; nested < 8; nested++)
                        h.assertTrue(CorridorNeedleRegistry.deploy(f.p, nested, f.position(), () -> true, f::pay).isEmpty(),
                                "native callback cannot recursively consume any slot");
                });
                var medium = CorridorNeedleRegistry.deploy(f.p, slot, f.position().add(slot * 1.5, 0, 0), () -> true, f::pay).orElseThrow();
                f.entities.add(medium); f.clearListeners();
                h.assertTrue(f.state(slot).entityId().equals(medium.getUUID()), "exact frozen slot retains admitted UUID");
                h.assertTrue(CorridorNeedleRegistry.deploy(f.p, slot, f.position(), () -> true, f::pay).isEmpty(),
                        "an admitted frozen slot cannot replay into the next free slot");
            }
            h.assertValueEqual(f.needles(), 1, "exactly eight originals published");
            h.assertValueEqual(f.payments.get(), 8, "each frozen slot pays exactly once");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void early_native_pickup_cannot_redirect_later_frozen_slot_into_lower_spent_slot(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            var first = CorridorNeedleRegistry.deploy(f.p, 0, f.position(), () -> true, f::pay).orElseThrow();
            f.entities.add(first); f.embed(first); var identity = first.identity();
            first.playerTouch(f.p);
            h.assertTrue(first.isRemoved() && f.state(0).state() == DisplacedNeedleLedger.State.SPENT,
                    "actual early pickup settles slot zero before the next volley emission");
            var spent = f.state(0);
            for (int slot : new int[]{1, 7}) {
                var next = CorridorNeedleRegistry.deploy(f.p, slot, f.position().add(slot * 1.5, 0, 0), () -> true, f::pay).orElseThrow();
                f.entities.add(next);
                h.assertTrue(f.state(slot).entityId().equals(next.getUUID()) && f.state(slot).nonce() == 1,
                        "later emission reserves its exact frozen index despite a lower free index");
                h.assertTrue(f.state(0).equals(spent) && !next.getUUID().equals(identity.entityId()),
                        "settled earlier index and original UUID are never reassigned by a later emission");
            }
            for (int slot = 2; slot < 7; slot++)
                h.assertTrue(f.state(slot).state() == DisplacedNeedleLedger.State.EMPTY && f.state(slot).nonce() == 0,
                        "unpublished holes retain pristine ledgers");
            h.assertValueEqual(f.needles(), 2, "one returned original and one unspent original remain in inventory");
            h.assertValueEqual(f.payments.get(), 3, "only three successful exact-index publications pay");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void exact_empty_slot_cannot_bypass_unloaded_lost_or_refund_pending_sibling(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 2)) {
            if (mode == 2) {
                fillInventory(f.p);
                f.listen(event -> { f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64)); event.setCanceled(true); });
                h.assertFalse(f.deploy(), "known native refusal retains one real refund original");
                f.clearListeners();
            } else {
                h.assertTrue(f.deploy(), "first native original admitted");
                if (mode == 0) nativeUnload(manager(h), f.last);
                else f.last.discard();
            }
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 1));
            CompoundTag before = f.save(); int payments = f.payments.get();
            for (int slot : new int[]{1, 7})
                h.assertTrue(CorridorNeedleRegistry.deploy(f.p, slot, f.position(), () -> true, f::pay).isEmpty(),
                        "an exact empty index cannot bypass unresolved owner-wide custody");
            h.assertTrue(f.save().equals(before), "refusal leaves all exact slot nonces and saved evidence unchanged");
            h.assertValueEqual(f.payments.get(), payments, "unresolved sibling prevents payment");
            h.assertValueEqual(f.p.getOffhandItem().getCount(), 1, "unresolved sibling prevents actual item reservation");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void all_four_native_and_saved_domains_reject_foreign_and_mixed_proofs(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            h.assertTrue(f.deploy(), "NEW341 original admitted"); var corridor = f.last;
            var displaced = DisplacedNeedleRegistry.deploy(f.p, f.position().add(2, 0, 0), () -> true, () -> true).orElseThrow();
            var cross = CrossNeedleRegistry.deploy(f.p, f.position().add(4, 0, 0), () -> true, () -> true).orElseThrow();
            var interception = InterceptionNeedleRegistry.deploy(f.p, f.position().add(6, 0, 0), () -> true, () -> true).orElseThrow();
            f.entities.addAll(List.of(displaced, cross, interception));
            h.assertFalse(DisplacedNeedleRegistry.tryBind(corridor) || CrossNeedleRegistry.tryBind(corridor)
                    || InterceptionNeedleRegistry.tryBind(corridor), "all three old registries reject NEW341 type");
            var oldMedia = List.of(displaced, cross, interception);
            var oldKeys = List.of("AcademyDisplacedNeedle", "AcademyCrossNeedle", "AcademyInterceptionNeedle");
            for (int i = 0; i < oldMedia.size(); i++) {
                var old = oldMedia.get(i);
                h.assertFalse(CorridorNeedleRegistry.tryBind(old) || CorridorNeedleRegistry.authentic(f.p, old),
                        "NEW341 cannot borrow another domain's live ledger");
                h.assertFalse(CorridorNeedleRegistry.recoverExisting(f.p, old, old.identity(), f.position(), () -> true, f::pay),
                        "foreign recovery never reaches payment");
                h.assertTrue(f.decode(nativeTag(old), true).identity() == null, "foreign native identity cannot decode as NEW341");
                var wrongType = new DisplacedNeedleEntity(i == 0 ? TeleporterNeedleEntities.DISPLACED_NEEDLE.get()
                        : i == 1 ? TeleporterNeedleEntities.CROSS_NEEDLE.get() : TeleporterNeedleEntities.INTERCEPTION_NEEDLE.get(), h.getLevel());
                wrongType.load(nativeTag(corridor)); f.entities.add(wrongType);
                h.assertTrue(wrongType.identity() == null && wrongType.mediumSnapshot().isEmpty(),
                        "NEW341 tag cannot decode into any old native type");
                CompoundTag mixed = nativeTag(corridor);
                mixed.put(oldKeys.get(i), nativeTag(old).getCompound(oldKeys.get(i)).copy());
                h.assertTrue(f.decode(mixed, true).identity() == null, "mixed NEW341 plus foreign identity fails closed");
            }
            var lookup = h.getLevel().registryAccess(); var server = h.getLevel().getServer();
            for (CompoundTag old : List.of(DisplacedNeedleRegistry.get(h.getLevel()).save(new CompoundTag(), lookup),
                    CrossNeedleRegistry.get(h.getLevel()).save(new CompoundTag(), lookup),
                    InterceptionNeedleRegistry.get(h.getLevel()).save(new CompoundTag(), lookup))) {
                var rejected = CorridorNeedleRegistry.load(server, old, lookup);
                h.assertTrue(rejected.isBlocked() && rejected.save(new CompoundTag(), lookup).equals(old),
                        "foreign registry codec is preserved verbatim without migration or nonce borrowing");
            }
            var saved = f.save();
            h.assertTrue(CrossNeedleRegistry.load(server, saved, lookup).isBlocked()
                    && InterceptionNeedleRegistry.load(server, saved, lookup).isBlocked()
                    && DisplacedNeedleRegistry.load(server, saved, lookup).isBlocked(),
                    "NEW341 domain-tagged registry cannot decode as any old registry");
            for (String domain : new String[]{"", "cross_substitution", "short_interception"}) {
                var forged = saved.copy(); forged.putString("domain", domain);
                var rejected = CorridorNeedleRegistry.load(server, forged, lookup);
                h.assertTrue(rejected.isBlocked() && rejected.save(new CompoundTag(), lookup).equals(forged),
                        "wrong explicit domain cannot silently adopt valid looking NEW341 ledgers");
            }
            h.assertValueEqual(f.payments.get(), 1, "only original NEW341 deployment paid");
            h.assertValueEqual(f.needles(), 0, "four actual originals remain in four native domains");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void actual_owner_death_retires_all_eight_originals_without_refund_or_rearm(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 8)) {
            f.deployCount(8); var all = CorridorNeedleRegistry.existing(f.p);
            h.assertTrue(all.getFirst().beginFlight(all.getFirst().identity()), "first original had native contact enabled");
            f.p.hurt(h.getLevel().damageSources().genericKill(), Float.MAX_VALUE);
            h.assertFalse(f.p.isAlive(), "real native lethal damage reaches owner death");
            for (var medium : all) {
                h.assertTrue(!medium.isRemoved() && medium.mediumSnapshot().getCount() == 1,
                        "death retains each exact physical original");
                h.assertFalse(medium.isContactEnabled() || medium.beginFlight(medium.identity())
                        || medium.advanceGeneration(medium.identity(), 2), "death retires both used and never-armed originals");
            }
            h.assertTrue(f.registry().snapshots(f.p.getUUID()).stream().allMatch(state -> state.state() == DisplacedNeedleLedger.State.ACTIVE),
                    "owner death keeps all eight outstanding custody identities");
            h.assertFalse(f.deploy() || CorridorNeedleRegistry.retryRefund(f.p), "dead owner cannot replace or refund originals");
            h.assertValueEqual(f.payments.get(), 8, "death creates no resource commits");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void recovery_reembedding_preserves_original_expiry_and_never_opens_a_generation(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.deploy(), "one original"); var medium = f.last; var identity = medium.identity();
            h.assertTrue(medium.beginFlight(identity), "one armed generation"); f.embed(medium); int age = life(medium);
            h.assertTrue(age > 0, "actual embedded native clock has begun");
            Vec3 destination = medium.position().add(2, 2, 0);
            h.getLevel().setBlockAndUpdate(BlockPos.containing(destination).below(2), Blocks.STONE.defaultBlockState());
            h.assertTrue(CorridorNeedleRegistry.recoverExisting(f.p, medium, identity, destination, () -> true, f::pay),
                    "closed323 route relocates exact original");
            h.assertTrue(medium.identity().equals(identity) && life(medium) == age && medium.position().equals(destination),
                    "recovery preserves native lifetime and custody");
            h.assertFalse(medium.isContactEnabled() || medium.beginFlight(identity) || medium.advanceGeneration(identity, 2),
                    "323 never renews NEW341 attack");
            for (int tick = 0; tick < 40 && !medium.inGround; tick++) medium.tick();
            h.assertTrue(medium.inGround && life(medium) >= age, "natural fall and reembedding do not rewind original life");
            for (int tick = 0; tick < 1200 && life(medium) < 1199 && !medium.isRemoved(); tick++) medium.tick();
            h.assertTrue(!medium.isRemoved() && life(medium) == 1199, "native ticks reach unchanged final lifetime");
            medium.tick();
            h.assertTrue(medium.isRemoved() && f.state(0).custody() == DisplacedNeedleLedger.Custody.CONSUMED,
                    "recovered original still reaches native expiry");
            h.assertValueEqual(f.payments.get(), 2, "one deployment and one real recovery charge");
            h.succeed();
        }
    }

    private static int selectedIndex(List<DisplacedNeedleEntity> values, DisplacedNeedleEntity medium) { return values.indexOf(medium); }
    /** Declared legacy fixture: one real owner's first three saved slots, never a native restart claim. */
    private static CompoundTag legacyOwner(CompoundTag current, UUID owner) {
        CompoundTag legacy = current.copy(); legacy.putInt("version", 1);
        CompoundTag savedOwner = ownedEntry(current, owner).copy();
        ListTag slots = new ListTag();
        for (int slot = 0; slot < 3; slot++) slots.add(ownedSlot(current, owner, slot).copy());
        savedOwner.put("slots", slots);
        ListTag owners = new ListTag(); owners.add(savedOwner); legacy.put("entries", owners);
        return legacy;
    }
    private static void assertEmptyMigrationSlots(GameTestHelper h, Fixture f) {
        CompoundTag saved = f.save();
        h.assertValueEqual(saved.getInt("version"), 2, "known legacy registry uses explicit v2 schema");
        h.assertValueEqual(ownedEntry(saved, f.p.getUUID()).getList("slots", Tag.TAG_COMPOUND).size(), 8,
                "v2 records exactly eight physical slots");
        h.assertValueEqual(f.registry().snapshots(f.p.getUUID()).size(), 8, "migration appends exactly five new ledgers");
        for (int slot = 3; slot < 8; slot++) {
            var snapshot = f.state(slot);
            h.assertTrue(snapshot.owner().equals(f.p.getUUID()) && snapshot.state() == DisplacedNeedleLedger.State.EMPTY
                    && snapshot.custody() == DisplacedNeedleLedger.Custody.NONE && snapshot.nonce() == 0
                    && snapshot.generation() == 0 && snapshot.removedItems() == 0 && snapshot.entityId() == null,
                    "appended ledger has no item, UUID, nonce, generation or attack authority");
            CompoundTag entry = ownedSlot(saved, f.p.getUUID(), slot);
            h.assertTrue(entry.getString("dimension").isEmpty() && entry.getCompound("escrow").isEmpty(),
                    "appended slot creates no dimension binding or escrow item");
        }
    }
    private static CompoundTag ownedEntry(CompoundTag root, UUID owner) {
        for (Tag value : root.getList("entries", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag)value; if (owner.toString().equals(entry.getString("owner"))) return entry;
        }
        throw new IllegalStateException("Fixture owner record absent");
    }
    private static CompoundTag ownedSlot(CompoundTag root, UUID owner, int slot) {
        for (Tag value : ownedEntry(root, owner).getList("slots", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag)value; if (entry.getInt("slot") == slot) return entry;
        }
        throw new IllegalStateException("Fixture slot record absent");
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
            if (event.getEntity() instanceof DisplacedNeedleEntity medium && medium.isCorridorNeedle()
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
        DisplacedNeedleEntity last; int serial;
        Fixture(GameTestHelper h, int stock) {
            this.h = h; actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h); p = actors.player();
            p.getInventory().clearContent(); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), stock));
        }
        Vec3 position() { return h.absoluteVec(new Vec3(5.5, 3.5, 5.5)); }
        CorridorNeedleRegistry registry() { return CorridorNeedleRegistry.get(h.getLevel()); }
        DisplacedNeedleLedger.Snapshot state(int slot) { return registry().snapshots(p.getUUID()).get(slot); }
        CompoundTag save() { return registry().save(new CompoundTag(), h.getLevel().registryAccess()); }
        void install(CompoundTag tag) {
            h.getLevel().getServer().overworld().getDataStorage().set(CorridorNeedleRegistry.ID,
                    CorridorNeedleRegistry.load(h.getLevel().getServer(), tag, h.getLevel().registryAccess()));
        }
        int needles() { return count(p, AcademyCraft.NEEDLE.get()); }
        boolean pay() { payments.incrementAndGet(); return true; }
        boolean deploy() { return deploy(this::pay); }
        boolean deploy(BooleanSupplier payment) {
            var result = CorridorNeedleRegistry.deploy(p, position().add(serial * 1.5, 0, 0), () -> true, payment);
            if (result.isPresent()) { last = result.get(); entities.add(last); serial++; }
            return result.isPresent();
        }
        void deployCount(int count) { for (int slot = 0; slot < count; slot++) h.assertTrue(deploy(), "real original emission admitted: " + slot); }
        void listen(Consumer<EntityJoinLevelEvent> callback) {
            var probe = new JoinProbe(p.getUUID(), event -> { entities.add((DisplacedNeedleEntity)event.getEntity()); callback.accept(event); });
            probes.add(probe); NeoForge.EVENT_BUS.register(probe);
        }
        void listenSection(DisplacedNeedleEntity medium, Consumer<EntityEvent.EnteringSection> callback) {
            var probe = new SectionProbe(medium, callback); probes.add(probe); NeoForge.EVENT_BUS.register(probe);
        }
        void clearListeners() { probes.forEach(NeoForge.EVENT_BUS::unregister); probes.clear(); }
        DisplacedNeedleEntity decode(CompoundTag tag, boolean corridor) {
            var entity = new DisplacedNeedleEntity(corridor ? TeleporterNeedleEntities.CORRIDOR_NEEDLE.get() : TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
            entity.load(tag); entities.add(entity); return entity;
        }
        void embed(DisplacedNeedleEntity medium) {
            h.getLevel().setBlockAndUpdate(BlockPos.containing(medium.position()).below(), Blocks.STONE.defaultBlockState());
            medium.setDeltaMovement(0, -1, 0); for (int tick = 0; tick < 12; tick++) medium.tick();
            h.assertTrue(medium.inGround && medium.shakeTime == 0, "actual native gravity/impact embeds a pickup-ready medium");
        }
        void expireNaturally(DisplacedNeedleEntity medium) {
            embed(medium); int age = life(medium); h.assertTrue(age > 0 && age < 1200, "native lifetime begins below expiry");
            while (life(medium) < 1199 && !medium.isRemoved()) medium.tick();
            h.assertTrue(!medium.isRemoved() && life(medium) == 1199, "full original native lifetime reaches1199 without synthetic shortening");
            medium.tick(); h.assertTrue(medium.isRemoved() && medium.getRemovalReason() == Entity.RemovalReason.DISCARDED, "genuine1200th native despawn tick");
        }
        @Override public void close() {
            clearListeners(); for (var medium : entities) if (!medium.isRemoved()) medium.discard();
            items.forEach(item -> { if (!item.isRemoved()) item.discard(); }); actors.close();
        }
    }
}
