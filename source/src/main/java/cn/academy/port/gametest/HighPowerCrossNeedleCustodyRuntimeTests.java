package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.CrossNeedleRegistry;
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

/** NEW321 custody through real PlayerList owners, native publication, pickup and
 * final entity-manager unload. Inventory, floor and enrollment are declared fixtures.
 * Save/load cases use production codecs and actual native lifecycle callbacks; they
 * do not claim an atomic process restart. Existing NEW311 acceptance is unchanged. */
@GameTestHolder("academy_highpower")
@PrefixGameTestTemplate(false)
public final class HighPowerCrossNeedleCustodyRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_cross_needle_custody";
    private HighPowerCrossNeedleCustodyRuntimeTests() { }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void three_actual_offhand_items_publish_independently_before_each_payment(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            List<DisplacedNeedleEntity> published = new ArrayList<>();
            f.listen(event -> {
                var candidate = (DisplacedNeedleEntity)event.getEntity();
                int index = published.size(); published.add(candidate);
                h.assertTrue(candidate.getType() == TeleporterNeedleEntities.CROSS_NEEDLE.get(), "exact NEW321 native entity type");
                h.assertValueEqual(f.needles(), 3 - index, "one real offhand item removed for this emission only");
                h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "only current emission has original escrow");
                h.assertTrue(f.state(index).state() == DisplacedNeedleLedger.State.PENDING
                        && candidate.getUUID().equals(f.state(index).entityId()), "current UUID published before native join");
                h.assertValueEqual(f.payments.get(), index, "no current payment before native publication");
                h.assertFalse(candidate.isAdmitted(), "pending candidate has no native pickup or contact permission");
                h.assertFalse(f.deploy(), "native join cannot recursively reserve a sibling slot");
            });
            for (int index = 0; index < 3; index++) {
                int emission = index;
                h.assertTrue(f.deploy(() -> {
                    var candidate = published.get(emission);
                    h.assertTrue(h.getLevel().getEntity(candidate.getUUID()) == candidate
                            && h.getLevel().getEntity(candidate.getId()) == candidate, "both native world indexes precede payment");
                    h.assertTrue(f.state(emission).state() == DisplacedNeedleLedger.State.PENDING, "payment sees published pending custody");
                    return f.pay();
                }), "independent actual emission admitted: " + index);
                h.assertValueEqual(CrossNeedleRegistry.existing(f.p).size(), index + 1, "each committed item has one live native entity");
            }
            h.assertTrue(published.stream().map(Entity::getUUID).distinct().count() == 3
                    && published.stream().map(Entity::getId).distinct().count() == 3, "three distinct native objects, UUIDs and IDs");
            h.assertTrue(published.stream().allMatch(m -> m.isAdmitted() && m.mediumSnapshot().getCount() == 1
                    && !m.isContactEnabled()), "each entity owns exactly one original and custody alone grants no attack");
            h.assertValueEqual(f.needles(), 1, "four originals minus three native media");
            h.assertFalse(f.deploy(), "fourth slot is refused despite available stock");
            h.assertValueEqual(f.payments.get(), 3, "one payment per admitted original");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 0, "active media never leave duplicated escrow");
            h.assertTrue(CrossNeedleRegistry.availability(f.p) == CrossNeedleRegistry.Availability.OUTSTANDING, "fresh controller sees outstanding custody");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void every_emission_native_cancel_or_throw_preserves_prior_siblings_and_originals(GameTestHelper h) {
        for (int emission = 0; emission < 3; emission++) for (int mode = 0; mode < 2; mode++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(emission); var before = CrossNeedleRegistry.existing(f.p); int selectedMode = mode;
            f.listen(event -> {
                var candidate = (DisplacedNeedleEntity)event.getEntity(); Vec3 position = candidate.position();
                candidate.setDeltaMovement(2, 3, 4); candidate.tick(); candidate.playerTouch(f.p);
                h.assertTrue(candidate.position().equals(position), "staged candidate cannot tick into flight or pick up");
                h.assertFalse(f.deploy(), "cancel/throw callback cannot publish a sibling");
                if (selectedMode == 0) event.setCanceled(true);
                else throw new IllegalStateException("Declared NEW321 native join failure");
            });
            h.assertFalse(f.deploy(), "native join rejects only selected emission");
            h.assertTrue(CrossNeedleRegistry.existing(f.p).equals(before), "already admitted siblings retain exact object order");
            h.assertValueEqual(f.needles(), 3 - emission - mode, "safe cancellation returns original; thrown callback retains unresolved original");
            h.assertValueEqual(f.needles() + f.registry().retainedEscrowCount(f.p.getUUID()), 3 - emission,
                    "returned and retained custody conserve exact remaining originals");
            h.assertValueEqual(f.payments.get(), emission, "cancelled emission never pays");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "neither settled refund nor unresolved callback can replay");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void every_join_stack_mutation_preserves_foreign_inventory_without_snapshot_rollback(GameTestHelper h) {
        for (int emission = 0; emission < 3; emission++) try (Fixture f = new Fixture(h, 4)) {
            f.deployCount(emission); var before = CrossNeedleRegistry.existing(f.p);
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
            h.assertTrue(CrossNeedleRegistry.existing(f.p).equals(before), "earlier sibling custody is unaffected");
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
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "invalid owner is not a refund destination");
            f.clearListeners(); f.p.setGameMode(GameType.SURVIVAL);
            h.assertTrue(CrossNeedleRegistry.retryRefund(f.p), "valid original owner receives its retained needle");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "owner recovery cannot replay refund");
            h.assertValueEqual(f.needles(), 3 - emission, "valid-owner retry conserves all remaining originals");
            h.assertValueEqual(CrossNeedleRegistry.existing(f.p).size(), emission, "previous siblings remain independently recoverable");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void every_join_candidate_identity_owner_and_pose_mutation_fail_closed(GameTestHelper h) {
        for (int emission = 0; emission < 3; emission++) for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(emission); var before = CrossNeedleRegistry.existing(f.p); int selectedMode = mode;
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
            h.assertTrue(CrossNeedleRegistry.existing(f.p).equals(before), "candidate mutation never replaces prior siblings: " + scenario);
            h.assertValueEqual(f.needles() + f.registry().retainedEscrowCount(f.p.getUUID()), 3 - emission,
                    "safe refund or retained original conserves exact remaining stock: " + scenario);
            if (mode != 2) {
                h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "unproven mutated identity cannot issue refund: " + scenario);
                h.assertTrue(CrossNeedleRegistry.availability(f.p) == CrossNeedleRegistry.Availability.OUTSTANDING,
                        "unknown candidate disposition remains outstanding: " + scenario);
                f.clearListeners(); h.assertFalse(f.deploy(), "ambiguous slot blocks replacement into otherwise unused sibling slots: " + scenario);
            }
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_owner_setter_rejects_foreign_owner_and_preserves_valid_original_publication(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            ServerPlayer foreign = f.actors.player(); AtomicInteger joins = new AtomicInteger();
            f.listen(event -> {
                var candidate = (DisplacedNeedleEntity)event.getEntity(); var identity = candidate.identity();
                candidate.setOwner(null); candidate.setOwner(foreign);
                h.assertTrue(candidate.getOwner() == f.p && candidate.identity().equals(identity)
                        && candidate.mediumSnapshot().getCount() == 1 && !candidate.isAdmitted(),
                        "rejected setters preserve the exact original staged owner, identity and medium");
                joins.incrementAndGet();
            });
            f.deployCount(3);
            h.assertValueEqual(joins.get(), 3, "every emission receives actual native ownership-change attempts");
            h.assertTrue(CrossNeedleRegistry.existing(f.p).stream().allMatch(m -> m.getOwner() == f.p
                    && m.identity().owner().equals(f.p.getUUID()) && m.isAdmitted()),
                    "ignored ownership changes do not invalidate otherwise genuine native publication");
            h.assertTrue(CrossNeedleRegistry.existing(foreign).isEmpty(), "foreign owner gains no custody");
            h.assertValueEqual(f.needles(), 0, "three original items admitted without copies or refunds");
            h.assertValueEqual(f.payments.get(), 3, "one payment per unchanged genuine publication");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void each_payment_refusal_is_safe_but_paid_exception_never_refunds_unknown_commit(GameTestHelper h) {
        for (int emission = 0; emission < 3; emission++) for (int mode = 0; mode < 2; mode++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(emission); int selectedMode = mode; var before = CrossNeedleRegistry.existing(f.p);
            h.assertFalse(f.deploy(() -> {
                if (selectedMode == 0) return false;
                f.pay(); throw new IllegalStateException("Declared failure after external resource commit");
            }), "refused or unconfirmed current payment cannot report a committed emission");
            h.assertTrue(CrossNeedleRegistry.existing(f.p).equals(before), "payment failure preserves earlier native siblings");
            h.assertValueEqual(f.payments.get(), emission + mode, "observed payment is never rolled back");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "neither settled return nor paid uncertainty has replayable refund");
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
            h.assertTrue(CrossNeedleRegistry.existing(f.p).equals(List.of(first)), "earlier physical medium remains after future stock disappears");
            h.assertValueEqual(f.payments.get(), 1, "only first admitted emission paid");
            h.assertValueEqual(f.needles() + drops.getFirst().getItem().getCount() + first.mediumSnapshot().getCount(), 3,
                    "all three real originals remain in native custody or actual dropped stack");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void third_slot_refund_pending_blocks_new_emissions_and_preserves_two_siblings(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(2); var siblings = CrossNeedleRegistry.existing(f.p); fillInventory(f.p);
            f.listen(event -> { f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64)); event.setCanceled(true); });
            h.assertFalse(f.deploy(), "third native publication cancelled with no inventory capacity");
            h.assertTrue(CrossNeedleRegistry.availability(f.p) == CrossNeedleRegistry.Availability.REFUND_PENDING,
                    "explicit safe refund differs from unresolved entity custody");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), 1, "only failed third slot retains actual original");
            h.assertTrue(CrossNeedleRegistry.existing(f.p).equals(siblings), "full refund queue never removes two prior siblings");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "full inventory cannot consume retained original");
            f.clearListeners(); h.assertFalse(f.deploy(), "pending refund cannot be bypassed using a new slot");
            f.p.getInventory().setItem(2, ItemStack.EMPTY);
            h.assertTrue(CrossNeedleRegistry.retryRefund(f.p), "one new capacity receives exact failed-slot original");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "retry never creates a fourth item");
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
            CompoundTag saved = f.save();
            if (mode == 1) ownedSlot(saved, f.p.getUUID(), 0).getCompound("ledger").putString("custody", "REFUND_ESCROW");
            f.install(saved); f.p.getInventory().setItem(0, ItemStack.EMPTY);
            h.assertTrue(CrossNeedleRegistry.retryRefund(f.p) == (mode == 0), "only proven unpublished refund survives codec reload");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "neither completed nor ambiguous native transfer can replay");
            h.assertValueEqual(f.needles(), 1 - mode, "save/load never creates an ambiguous refund");
            h.assertValueEqual(f.registry().retainedEscrowCount(f.p.getUUID()), mode, "unknown insertion retains original serialized evidence");
            f.install(f.save());
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "another reload cannot resurrect consumed refund authority");
            h.assertValueEqual(f.payments.get(), 0, "all cancellation and reconciliation paths are unpaid");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void selected_native_pickup_consumes_one_slot_and_keeps_other_two_exact_objects(GameTestHelper h) {
        for (int selected = 0; selected < 3; selected++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(3); var all = CrossNeedleRegistry.existing(f.p); var chosen = all.get(selected);
            for (var medium : all) f.embed(medium);
            fillInventory(f.p); f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 64));
            chosen.playerTouch(f.p);
            h.assertFalse(chosen.isRemoved(), "full native inventory cannot erase original medium");
            h.assertTrue(CrossNeedleRegistry.existing(f.p).equals(all), "failed pickup retains all three exact objects");
            f.p.getInventory().setItem(0, ItemStack.EMPTY); chosen.playerTouch(f.p); chosen.playerTouch(f.p);
            var siblings = all.stream().filter(medium -> medium != chosen).toList();
            h.assertTrue(chosen.isRemoved() && CrossNeedleRegistry.existing(f.p).equals(siblings), "only selected exact native entity is transferred");
            h.assertTrue(siblings.stream().allMatch(medium -> medium.mediumSnapshot().getCount() == 1 && !medium.isRemoved()),
                    "unselected siblings retain their own original single items");
            h.assertValueEqual(f.needles(), 1, "one original returned despite repeated touch");
            h.assertTrue(f.state(selected).state() == DisplacedNeedleLedger.State.SPENT, "only selected slot settles");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "native pickup never issues a second refund");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void genuine_hidden_final_unload_and_native_reload_keep_three_exact_tokens_without_attack(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            f.deployCount(3); var old = CrossNeedleRegistry.existing(f.p);
            for (var medium : old) { h.assertTrue(medium.beginFlight(medium.identity()), "declared committed generation armed before native unload"); f.embed(medium); }
            var tags = old.stream().map(HighPowerCrossNeedleCustodyRuntimeTests::nativeTag).toList();
            var identities = old.stream().map(DisplacedNeedleEntity::identity).toList();
            var manager = manager(h); var chunks = old.stream().map(m -> new ChunkPos(m.blockPosition())).distinct().toList();
            try {
                chunks.forEach(chunk -> manager.updateChunkStatus(chunk, Visibility.HIDDEN));
                for (var medium : old) nativeUnload(manager, medium);
                h.assertTrue(old.stream().allMatch(m -> m.isRemoved() && m.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK),
                        "actual native manager final removal bypasses subclass remove and reaches lifecycle listener");
                h.assertTrue(f.registry().snapshots(f.p.getUUID()).stream().allMatch(s -> s.state() == DisplacedNeedleLedger.State.ACTIVE),
                        "positive unload retains all three outstanding identities");
            } finally { chunks.forEach(chunk -> manager.updateChunkStatus(chunk, Visibility.TICKING)); }
            f.install(f.save());
            h.assertTrue(CrossNeedleRegistry.existing(f.p).isEmpty(), "unloaded originals cannot be invented by lookup");
            h.assertFalse(f.deploy(), "three unloaded slots cannot consume fourth original");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "absence cannot manufacture refunds");
            for (int slot = 0; slot < 3; slot++) {
                var restored = f.decode(tags.get(slot), true);
                h.assertFalse(CrossNeedleRegistry.tryBind(restored), "uninserted decoded object lacks native indexes");
                h.assertTrue(h.getLevel().addFreshEntity(restored) && CrossNeedleRegistry.tryBind(restored), "matching actual native object rebinds exact slot");
                h.assertTrue(restored.identity().equals(identities.get(slot)) && life(restored) == tags.get(slot).getShort("life"),
                        "owner, nonce, UUID, generation and original native lifetime survive");
                h.assertFalse(restored.isContactEnabled() || restored.beginFlight(restored.identity()), "restored generation cannot rearm attack");
                var shadow = f.decode(tags.get(slot), true);
                h.assertFalse(CrossNeedleRegistry.tryBind(shadow), "same persisted UUID on another Java object cannot replace native object");
                shadow.playerTouch(f.p);
            }
            h.assertValueEqual(CrossNeedleRegistry.existing(f.p).size(), 3, "all three original identities independently rebind");
            h.assertValueEqual(f.needles(), 1, "reload and shadow objects create no inventory grants");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void wrong_registry_type_owner_nonce_or_generation_never_binds_a_cross_original(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            f.deployCount(3); var cross = CrossNeedleRegistry.existing(f.p);
            var displaced = DisplacedNeedleRegistry.deploy(f.p, f.position().add(0, 0, 5), () -> true, () -> true).orElseThrow(); f.entities.add(displaced);
            for (var medium : cross) {
                h.assertFalse(DisplacedNeedleRegistry.tryBind(medium), "NEW311 registry rejects exact CROSS_NEEDLE type");
                h.assertFalse(DisplacedNeedleRegistry.recoverExisting(f.p, medium, medium.identity(), f.position().add(1, 0, 0), () -> true, f::pay),
                        "NEW311 recovery cannot spend NEW321 custody");
            }
            h.assertFalse(CrossNeedleRegistry.tryBind(displaced) || CrossNeedleRegistry.authentic(f.p, displaced), "NEW321 refuses exact NEW311 type");
            h.assertFalse(CrossNeedleRegistry.recoverExisting(f.p, displaced, displaced.identity(), f.position(), () -> true, f::pay), "wrong-domain recovery never pays");
            var crossTag = nativeTag(cross.getFirst()); var displacedTag = nativeTag(displaced);
            h.assertTrue(crossTag.contains("AcademyCrossNeedle") && !crossTag.contains("AcademyDisplacedNeedle")
                    && displacedTag.contains("AcademyDisplacedNeedle") && !displacedTag.contains("AcademyCrossNeedle"), "native identity keys remain separated without changing NEW311 schema");
            h.assertTrue(f.decode(crossTag, false).identity() == null && f.decode(displacedTag, true).identity() == null,
                    "opposite exact native type cannot decode another custody domain");
            var original = cross.getFirst(); nativeUnload(manager(h), original);
            for (int mutation = 0; mutation < 3; mutation++) {
                CompoundTag forged = crossTag.copy(); CompoundTag identity = forged.getCompound("AcademyCrossNeedle");
                if (mutation == 0) { UUID owner = UUID.randomUUID(); identity.putUUID("Owner", owner); forged.putUUID("Owner", owner); }
                if (mutation == 1) identity.putLong("Nonce", identity.getLong("Nonce") + 1);
                if (mutation == 2) identity.putLong("Generation", identity.getLong("Generation") + 1);
                var candidate = f.decode(forged, true); h.assertTrue(h.getLevel().addFreshEntity(candidate), "declared stale/forged native decode enters world");
                h.assertFalse(CrossNeedleRegistry.tryBind(candidate), "wrong persisted owner/nonce/generation cannot acquire original slot");
                candidate.playerTouch(f.p); candidate.discard();
            }
            var restored = f.decode(crossTag, true);
            h.assertTrue(h.getLevel().addFreshEntity(restored) && CrossNeedleRegistry.tryBind(restored), "unaltered exact original still rebinds after all refusals");
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
                for (int mutation = 0; mutation < 4; mutation++) {
                    CompoundTag corrupt = original.copy(); CompoundTag owner = ownedEntry(corrupt, f.p.getUUID());
                    if (mutation == 0) ownedSlot(corrupt, f.p.getUUID(), 1).getCompound("ledger").putString("nonce", "01");
                    if (mutation == 1) owner.getList("slots", Tag.TAG_COMPOUND).getCompound(1).putInt("slot", 0);
                    if (mutation == 2) corrupt.getList("entries", Tag.TAG_COMPOUND).add(owner.copy());
                    if (mutation == 3) {
                        ListTag many = new ListTag(); for (int i = 0; i <= CrossNeedleRegistry.MAX_OWNERS; i++) many.add(new CompoundTag()); corrupt.put("entries", many);
                    }
                    f.install(corrupt);
                    h.assertTrue(f.save().equals(corrupt), "malformed persisted bytes retained without repairing empty slots");
                    h.assertTrue(CrossNeedleRegistry.availability(f.p) == CrossNeedleRegistry.Availability.CORRUPT, "corrupt state is explicit and fail closed");
                    h.assertFalse(f.deploy() || CrossNeedleRegistry.retryRefund(f.p), "corruption grants neither publication nor refund");
                    h.assertValueEqual(f.needles(), 1, "corruption never spends remaining original stock");
                    h.assertValueEqual(f.payments.get(), 3, "no payment under malformed custody");
                }
            } finally { h.getLevel().getServer().overworld().getDataStorage().set(CrossNeedleRegistry.ID, live); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void valid_owner_limit_refuses_new_owner_without_reservation_or_payment(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 0)) {
            h.assertFalse(f.deploy(), "empty offhand creates only settled diagnostic owner entry");
            var live = f.registry(); CompoundTag template = ownedEntry(f.save(), f.p.getUUID()).copy();
            CompoundTag full = new CompoundTag(); full.putInt("version", 1); ListTag entries = new ListTag();
            for (int index = 0; index < CrossNeedleRegistry.MAX_OWNERS; index++) {
                UUID owner = UUID.randomUUID(); CompoundTag entry = template.copy(); entry.putString("owner", owner.toString());
                for (Tag value : entry.getList("slots", Tag.TAG_COMPOUND)) ((CompoundTag)value).getCompound("ledger").putString("owner", owner.toString());
                entries.add(entry);
            }
            full.put("entries", entries);
            try {
                f.install(full); f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 3));
                h.assertFalse(f.registry().isBlocked(), "exactly bounded valid owner records remain decodable");
                h.assertValueEqual(f.registry().ownerCount(), CrossNeedleRegistry.MAX_OWNERS, "valid bounded owner count");
                h.assertTrue(CrossNeedleRegistry.availability(f.p) == CrossNeedleRegistry.Availability.REGISTRY_FULL, "new owner receives explicit capacity refusal");
                h.assertFalse(f.deploy(), "capacity cannot be bypassed by native publication");
                h.assertValueEqual(f.needles(), 3, "no inventory reservation when owner capacity is full");
                h.assertValueEqual(f.payments.get(), 0, "no CP callback when owner capacity is full");
                h.assertTrue(f.save().equals(full), "capacity refusal leaves every valid persisted record unchanged");
            } finally { h.getLevel().getServer().overworld().getDataStorage().set(CrossNeedleRegistry.ID, live); }
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void authentic_native_expiry_settles_only_one_slot_while_unknown_removal_stays_outstanding(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 4)) {
            f.deployCount(3); var all = CrossNeedleRegistry.existing(f.p); var expired = all.getFirst(); var identity = expired.identity();
            CompoundTag staleTag = nativeTag(expired); f.expireNaturally(expired);
            h.assertTrue(f.state(0).state() == DisplacedNeedleLedger.State.SPENT && f.state(0).custody() == DisplacedNeedleLedger.Custody.CONSUMED,
                    "real1200-tick native expiry proves consumption of exactly first original");
            h.assertTrue(CrossNeedleRegistry.existing(f.p).equals(all.subList(1, 3)), "native expiry preserves two sibling objects");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "expired original cannot be refunded");
            h.assertTrue(f.deploy(), "verified consumed slot accepts one genuine remaining original"); var replacement = f.last;
            h.assertTrue(replacement.identity().nonce() == identity.nonce() + 1 && replacement != expired, "only consumed slot advances nonce");
            var stale = f.decode(staleTag, true); h.assertTrue(h.getLevel().addFreshEntity(stale), "declared old native save inserted");
            h.assertFalse(CrossNeedleRegistry.tryBind(stale), "expired old nonce cannot replace current native identity"); stale.playerTouch(f.p);
            f.registry().onRemoved(expired, identity, Entity.RemovalReason.DISCARDED);
            h.assertTrue(CrossNeedleRegistry.authentic(f.p, replacement), "old removal replay cannot settle replacement slot");
            var unknown = all.get(1); unknown.setRemoved(Entity.RemovalReason.DISCARDED);
            h.assertTrue(f.state(1).state() == DisplacedNeedleLedger.State.LOST, "generic native discard remains unproven custody");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "ambiguous native destruction never grants refund");
            h.assertValueEqual(f.needles(), 0, "one consumed and three deployed originals create no item grants");
            h.assertValueEqual(f.payments.get(), 4, "four admitted original emissions paid once each");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void each_selected_recovery_native_section_failure_is_paid_once_without_refund_or_sibling_change(GameTestHelper h) {
        for (int selected = 0; selected < 3; selected++) try (Fixture f = new Fixture(h, 3)) {
            f.deployCount(3); var all = CrossNeedleRegistry.existing(f.p); var medium = all.get(selected); var identity = medium.identity();
            h.assertTrue(medium.beginFlight(identity), "declared committed contact generation exists before recovery retirement");
            var positions = all.stream().map(Entity::position).toList(); Vec3 destination = medium.position().add(0, 16, 0); AtomicInteger calls = new AtomicInteger();
            f.listenSection(medium, event -> {
                calls.incrementAndGet();
                h.assertValueEqual(f.payments.get(), 4, "one selected recovery charge precedes actual native section event");
                h.assertFalse(CrossNeedleRegistry.recoverExisting(f.p, medium, identity, positions.get(selectedIndex(all, medium)), () -> true, f::pay),
                        "native section callback cannot reenter paid recovery");
                medium.tick(); medium.playerTouch(f.p);
                throw new IllegalStateException("Declared failure after native recovery relocation");
            });
            h.assertFalse(CrossNeedleRegistry.recoverExisting(f.p, medium, identity, destination, () -> true, f::pay), "native callback throw reports unconfirmed paid move");
            h.assertValueEqual(calls.get(), 1, "one genuine native section event");
            h.assertValueEqual(f.payments.get(), 4, "one paid attempt cannot replay through callback");
            h.assertTrue(medium.position().equals(destination) && medium.identity().equals(identity), "actual native relocation keeps original immutable custody identity");
            h.assertFalse(medium.isContactEnabled() || medium.beginFlight(identity), "recovery never rearms retired generation");
            h.assertFalse(CrossNeedleRegistry.retryRefund(f.p), "paid native uncertainty cannot fabricate inventory escrow");
            for (int slot = 0; slot < 3; slot++) if (slot != selected)
                h.assertTrue(all.get(slot).position().equals(positions.get(slot)) && CrossNeedleRegistry.authentic(f.p, all.get(slot)), "unselected sibling stays exactly in place");
            h.assertValueEqual(f.needles(), 0, "three original media remain physical entities after paid failure");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch="highpower_cross_needle_recovery", timeoutTicks=45)
    public static void native_323_registers_three_retired_cross_slots_and_q_drop_then_recovers_only_selected_slot(GameTestHelper h) {
        var base = new HighPowerRecoveryMarkRuntimeTests.Fixture(h, 5); var entities = new ArrayList<Entity>();
        h.testInfo.addListener(new GameTestListener() {
            public void testStructureLoaded(GameTestInfo info) { }
            public void testPassed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            public void testFailed(GameTestInfo info, GameTestRunner runner) { cleanup(); }
            public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { cleanup(); }
            private void cleanup() { entities.forEach(entity -> { if (!entity.isRemoved()) entity.discard(); }); base.close(); }
        });
        base.needle.discard(); base.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 3));
        List<DisplacedNeedleEntity> cross = new ArrayList<>();
        for (int slot = 0; slot < 3; slot++) {
            double angle = Math.PI / 8 + slot * Math.PI / 2;
            Vec3 source = base.anchor.add(Math.cos(angle) * 4.3, .2, Math.sin(angle) * 4.3);
            var medium = CrossNeedleRegistry.deploy(base.p, source, () -> true, () -> true).orElseThrow(); cross.add(medium); entities.add(medium);
            h.assertTrue(medium.beginFlight(medium.identity()), "declared admitted generation armed before native block retirement");
            medium.setDeltaMovement(0, -1, 0); for (int tick = 0; tick < 12; tick++) medium.tick();
            h.assertFalse(medium.isContactEnabled() || medium.beginFlight(medium.identity()), "native embedding retires cross contact without removing item");
        }
        base.p.getInventory().setItem(base.p.getInventory().selected, new ItemStack(AcademyCraft.NEEDLE.get()));
        h.assertTrue(base.p.drop(false), "actual Q produces independent original323-compatible drop");
        var drops = h.getLevel().getEntitiesOfClass(ItemEntity.class, base.p.getBoundingBox().inflate(2),
                item -> MagneticItemProvenance.ownedBy(item, base.p.getUUID()));
        h.assertValueEqual(drops.size(), 1, "one actual original Q drop"); var drop = drops.getFirst(); entities.add(drop);
        drop.setPos(base.anchor.add(3, .2, -3)); drop.setDeltaMovement(Vec3.ZERO); drop.setNoGravity(true);
        base.aim(cross.getFirst().getBoundingBox().getCenter()); h.assertTrue(base.press(1), "native323 registers first retired cross original");
        base.post(8, () -> { base.aim(cross.get(1).getBoundingBox().getCenter()); h.assertTrue(base.press(2), "native323 registers second distinct cross original"); });
        base.post(16, () -> { base.aim(cross.get(2).getBoundingBox().getCenter()); h.assertTrue(base.press(3), "native323 registers third distinct cross original"); });
        base.post(24, () -> { base.aim(drop.getBoundingBox().getCenter()); h.assertTrue(base.press(4), "native323 retains ordinary Q registration beside three cross slots"); });
        base.post(32, () -> {
            h.assertValueEqual(RecoveryMark.markedCount(base.p), 4, "three cross originals and one native Q hold separate323 handles");
            var chosen = cross.get(1); var identity = chosen.identity(); var all = List.copyOf(entities);
            var positions = all.stream().map(Entity::position).toList(); int age = life(chosen);
            var others = RecoveryMark.handles(base.p).stream().filter(mark -> !mark.needleUuid().equals(chosen.getUUID())).toList();
            double cp = base.cp(); base.aim(chosen.getBoundingBox().getCenter()); h.assertTrue(base.press(5), "explicit aim recalls only selected cross original");
            var receipt = RecoveryMark.receipt(base.p);
            h.assertTrue(receipt != null && receipt.paid() && receipt.moved() && receipt.phase() == RecoveryMark.RECOVERED
                    && receipt.needleUuid().equals(chosen.getUUID()) && chosen.position().equals(base.anchor), "canonical323 receipt proves selected actual native move");
            h.assertTrue(Math.abs(base.cp() - (cp - RecoveryMarkRules.cpCost(HighPowerGrowth.values(base.p).position()))) < 1e-7, "one actual growth-derived323 CP debit");
            h.assertTrue(chosen.identity().equals(identity) && life(chosen) == age && !chosen.isContactEnabled()
                    && !chosen.beginFlight(identity), "selected323 recovery preserves native age and cannot rearm NEW321 attack");
            h.assertTrue(RecoveryMark.handles(base.p).equals(others), "unselected cross and Q handles keep exact tokens and anchors");
            for (int i = 0; i < all.size(); i++) if (all.get(i) != chosen)
                h.assertTrue(all.get(i).position().equals(positions.get(i)), "unselected original never moves");
            h.assertTrue(CrossNeedleRegistry.existing(base.p).equals(cross), "323 transport does not consume or replace a custody slot");
            h.assertValueEqual(count(base.p, AcademyCraft.NEEDLE.get()), 0, "323 never grants inventory for a cross or Q original");
        });
        base.finish(33);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void decorated_or_wrong_medium_never_opens_any_cross_slot_or_pays(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 3)) {
            f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME, Component.literal("Declared decorated cross fixture"));
            h.assertFalse(f.deploy(), "decorated academy needle cannot enter custody");
            h.assertValueEqual(f.needles(), 3, "all decorated originals preserved");
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 3));
            h.assertFalse(f.deploy(), "ordinary arrows cannot substitute for academy needles");
            h.assertTrue(CrossNeedleRegistry.availability(f.p) == CrossNeedleRegistry.Availability.NO_MEDIUM, "medium refusal remains explicit");
            h.assertTrue(CrossNeedleRegistry.existing(f.p).isEmpty(), "wrong stock never creates native medium");
            h.assertValueEqual(f.payments.get(), 0, "wrong or decorated stock cannot pay"); h.succeed();
        }
    }

    private static int selectedIndex(List<DisplacedNeedleEntity> values, DisplacedNeedleEntity medium) { return values.indexOf(medium); }
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
            if (event.getEntity() instanceof DisplacedNeedleEntity medium && medium.isCrossNeedle()
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
        CrossNeedleRegistry registry() { return CrossNeedleRegistry.get(h.getLevel()); }
        DisplacedNeedleLedger.Snapshot state(int slot) { return registry().snapshots(p.getUUID()).get(slot); }
        CompoundTag save() { return registry().save(new CompoundTag(), h.getLevel().registryAccess()); }
        void install(CompoundTag tag) {
            h.getLevel().getServer().overworld().getDataStorage().set(CrossNeedleRegistry.ID,
                    CrossNeedleRegistry.load(h.getLevel().getServer(), tag, h.getLevel().registryAccess()));
        }
        int needles() { return count(p, AcademyCraft.NEEDLE.get()); }
        boolean pay() { payments.incrementAndGet(); return true; }
        boolean deploy() { return deploy(this::pay); }
        boolean deploy(BooleanSupplier payment) {
            var result = CrossNeedleRegistry.deploy(p, position().add(serial * 1.5, 0, 0), () -> true, payment);
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
        DisplacedNeedleEntity decode(CompoundTag tag, boolean cross) {
            var entity = new DisplacedNeedleEntity(cross ? TeleporterNeedleEntities.CROSS_NEEDLE.get() : TeleporterNeedleEntities.DISPLACED_NEEDLE.get(), h.getLevel());
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
