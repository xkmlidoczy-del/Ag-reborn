package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.BorrowedNeedleEntity;
import cn.academy.port.highpower.BorrowedNeedleEscrow;
import cn.academy.port.highpower.BorrowedNeedleEscrow.State;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** NEW351 phase-two custody proofs using real PlayerList owners, native entity
 * publication/removal/death and player codecs. Reflection reaches the intentionally
 * package-private production capability; no production test hook is introduced. */
@GameTestHolder("academy_highpower_borrowed")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class HighPowerBorrowedNeedleVolleyRuntimeTests {
    private static final String TEMPLATE = "highpower_volley_empty", BOUNDARY = "highpower_volley_boundary";
    private static final String BATCH = "highpower_borrowed_needle_volley";
    private static final TicketType<UUID> TICKET = TicketType.create("academy_borrowed_volley_fixture", UUID::compareTo);
    private HighPowerBorrowedNeedleVolleyRuntimeTests() { }

    @SubscribeEvent public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        installTemplate(level, TEMPLATE, 8, 8, 8);
        installTemplate(level, BOUNDARY, 20, 8, 20);
    }
    private static void installTemplate(ServerLevel level, String name, int x, int y, int z) {
        CompoundTag root = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), pos = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int length : new int[]{x, y, z}) { size.add(IntTag.valueOf(length)); pos.add(IntTag.valueOf(0)); }
        cell.put("pos", pos); cell.putInt("state", 0); blocks.add(cell);
        air.putString("Name", "minecraft:air"); palette.add(air);
        root.put("size", size); root.put("blocks", blocks); root.put("palette", palette); root.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower_borrowed", name))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), root);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=30)
    public static void every_n6_through_n16_reserves_six_once_and_reuses_only_retired_same_slot(GameTestHelper h) {
        for (int n = 6; n <= 16; n++) try (Fixture f = new Fixture(h, 8)) {
            ItemStack hand = f.p.getOffhandItem(); Object volley = f.reserve(n);
            h.assertTrue(volley != null && f.current(volley), "approved round reserves a current opaque capability for N=" + n);
            h.assertTrue(f.p.getOffhandItem() == hand && hand.getCount() == 2, "exact original remainder object survives six-item split");
            h.assertTrue(f.retained() == 6 && f.needles() == 2 && f.payments.get() == 0, "reservation moves exactly six physical needles and pays nothing");
            h.assertValueEqual(f.slots().size(), 6, "exactly six custody slots, independent of N");
            BorrowedNeedleEntity[] previous = new BorrowedNeedleEntity[6];
            long[] generations = new long[6];
            for (int i = 0; i < n; i++) {
                int slot = i % 6;
                if (previous[slot] != null) {
                    h.assertFalse(f.available(volley, i), "a live previous generation exclusively occupies its fixed slot");
                    retireShot(previous[slot]);
                    h.assertTrue(previous[slot].isRemoved() && !previous[slot].isLeaseActive(), "same-slot predecessor is definitively removed before reuse");
                    h.assertTrue(f.retained() == 6 && f.needles() == 2, "per-shot retirement neither returns nor reborrows any physical needle");
                }
                BorrowedNeedleEntity shot = f.publish(volley, i);
                h.assertTrue(shot != null && shot.isLeaseActive(), "one native publication admitted for preallocated index " + i);
                h.assertValueEqual(entityInt(shot, "slotIndex"), slot, "logical index always uses i modulo six");
                h.assertValueEqual(entityInt(shot, "shotIndex"), i, "native entity keeps exact preallocated shot index");
                long generation = entityLong(shot, "leaseGeneration");
                h.assertTrue(generation > generations[slot], "same-slot lease generation strictly increases");
                generations[slot] = generation; previous[slot] = shot;
                h.assertTrue(f.nativeProjectionCount() <= 6, "at most six exact native projections are simultaneously published");
                h.assertTrue(f.retained() == 6 && f.needles() == 2, "all original custody stays reserved for the complete round");
                h.assertFalse(shot.save(new CompoundTag()), "native borrowed projection is never a saved world item");
            }
            h.assertValueEqual(f.payments.get(), n, "exactly N successful publications each pay once");
            h.assertTrue(f.publish(volley, n) == null && f.publish(volley, 16) == null, "round shot-count and absolute sixteen-shot fences refuse extra admission");
            finish(volley);
            h.assertTrue(!f.current(volley) && f.retained() == 0 && f.needles() == 8, "round end returns the six originals exactly once");
            finish(volley);
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "settled round cannot be claimed again");
            h.assertValueEqual(f.needles(), 8, "duplicate finish does not duplicate stock");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void exact_six_plain_offhand_stock_required_before_any_reservation(GameTestHelper h) {
        for (int mode = 0; mode < 7; mode++) try (Fixture f = new Fixture(h, mode == 0 ? 5 : 6)) {
            if (mode == 1) f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME, Component.literal("Declared decorated stock"));
            if (mode == 2) f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 6));
            if (mode == 3) { f.p.setItemInHand(InteractionHand.MAIN_HAND, f.p.getOffhandItem()); f.p.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY); }
            if (mode == 4) f.p.setGameMode(GameType.SPECTATOR);
            CompoundTag before = f.p.getPersistentData().copy(); int beforeNeedles = f.needles();
            h.assertTrue(f.reserve(mode == 5 ? 5 : mode == 6 ? 17 : 6) == null, "invalid stock, owner or N refuses before escrow: " + mode);
            h.assertTrue(f.p.getPersistentData().equals(before), "refused reservation does not rewrite player persistence");
            h.assertValueEqual(f.needles(), beforeNeedles, "refused reservation never touches actual originals");
            h.assertValueEqual(f.payments.get(), 0, "stock refusal cannot debit a shot");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void six_live_slots_duplicate_indices_and_stale_handles_cannot_add_authority(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 12)) {
            Object first = f.reserve(16); BorrowedNeedleEntity[] shots = new BorrowedNeedleEntity[6];
            for (int i = 0; i < 6; i++) { shots[i] = f.publish(first, i); h.assertTrue(shots[i] != null, "first six native slots admitted"); }
            h.assertTrue(f.reserve(6) == null, "existing volley blocks a second reserve even with another six physical offhand needles");
            h.assertTrue(f.publish(first, 0) == null && f.publish(first, -1) == null && f.publish(first, 16) == null,
                    "duplicate, negative and seventeen-th logical indices cannot publish");
            h.assertValueEqual(f.nativeProjectionCount(), 6, "invalid publications never add a seventh native projection");
            h.assertValueEqual(f.payments.get(), 6, "invalid publication never reaches payment");
            h.assertTrue(f.publish(first, 6) == null, "seventh logical shot cannot replace a live same-slot native entity");
            h.assertValueEqual(f.payments.get(), 6, "waiting for definitive same-slot retirement never spends payment");
            retireShot(shots[0]);
            BorrowedNeedleEntity seventh = f.publish(first, 6);
            h.assertTrue(seventh != null && entityInt(seventh, "slotIndex") == 0, "only freed preallocated slot admits index six");
            retireShot(shots[0]); shots[0].tick();
            h.assertTrue(seventh.isLeaseActive(), "stale entity removal cannot revoke newer same-slot generation");
            finish(first); Object second = f.reserve(6);
            h.assertTrue(second != null && f.current(second) && !f.current(first), "new round obtains a distinct capability");
            h.assertTrue(f.publish(first, 7) == null, "stale round capability cannot publish into new custody");
            finish(first);
            h.assertTrue(f.current(second) && f.retained() == 6, "stale finish cannot settle current round originals");
            finish(second);
            h.assertValueEqual(f.needles(), 12, "two reservations and stale operations conserve physical originals");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void known_unpaid_failures_burn_index_without_returning_or_reborrowing_stock(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 6)) {
            Object volley = f.reserve(6); int selected = mode; AtomicInteger guards = new AtomicInteger();
            if (mode == 1) f.listen(event -> event.setCanceled(true));
            BorrowedNeedleEntity shot = f.publish(volley, 0,
                    () -> { guards.incrementAndGet(); return selected != 0; }, () -> selected != 2 && f.pay(), entity -> { });
            h.assertTrue(shot == null, "known guard, join or payment refusal has no admitted shot");
            h.assertValueEqual(f.payments.get(), 0, "known unpaid failure never claims a payment");
            h.assertTrue(f.retained() == 6 && f.needles() == 0 && f.current(volley), "known refusal holds six originals until round end");
            f.clearListeners(); int before = guards.get();
            h.assertTrue(f.publish(volley, 0, () -> { guards.incrementAndGet(); return true; }, f::pay, entity -> { }) == null,
                    "attempted logical shot cannot be replayed after a known refusal");
            h.assertValueEqual(guards.get(), before, "burned index does not execute a callback again");
            h.assertTrue(f.publish(volley, 1) != null, "next preallocated logical shot continues after known refusal");
            finish(volley); h.assertTrue(f.needles() == 6 && f.retained() == 0, "whole round returns exact six originals once");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_join_reentry_observes_unpaid_candidate_and_cannot_reserve_publish_or_claim(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 12)) {
            Object volley = f.reserve(6); AtomicInteger joins = new AtomicInteger();
            f.listen(event -> {
                BorrowedNeedleEntity candidate = (BorrowedNeedleEntity)event.getEntity(); joins.incrementAndGet();
                h.assertTrue(f.retained() == 6 && f.needles() == 6, "six actual originals are already held at native join");
                h.assertTrue(!candidate.isAdmitted() && !candidate.isLeaseActive() && f.payments.get() == 0,
                        "native join precedes admission and callback-free payment");
                h.assertTrue(f.reserve(6) == null && f.publish(volley, 1) == null && !BorrowedNeedleEscrow.claim(f.p),
                        "native publication callback cannot recursively reserve, publish or claim");
            });
            h.assertTrue(f.publish(volley, 0, () -> true, () -> {
                h.assertTrue(f.joined != null && h.getLevel().getEntity(f.joined.getUUID()) == f.joined
                        && h.getLevel().getEntity(f.joined.getId()) == f.joined, "both native indexes contain exact candidate before payment");
                return f.pay();
            }, entity -> { }) != null, "unchanged outer native publication commits once");
            h.assertValueEqual(joins.get(), 1, "only one native join reached");
            h.assertValueEqual(f.payments.get(), 1, "only outer publication paid");
            finish(volley); h.assertValueEqual(f.needles(), 12, "join reentry never duplicates originals"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void unknown_join_and_payment_outcomes_quarantine_all_six_without_compensation(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 6)) {
            Object volley = f.reserve(6); int selected = mode;
            if (mode < 2) f.listen(event -> {
                if (selected == 1) f.p.getInventory().setItem(3, new ItemStack(Items.DIAMOND, 2));
                throw new IllegalStateException("Declared volley native join uncertainty " + selected);
            });
            h.assertTrue(f.publish(volley, 0, () -> true, () -> {
                if (selected == 3) f.pay();
                if (selected >= 2) throw new IllegalStateException("Declared volley payment uncertainty " + selected);
                return f.pay();
            }, entity -> { }) == null, "unknown callback cannot report admitted publication");
            h.assertTrue(f.retained() == 6 && f.needles() == 0, "all six exact originals remain in non-redeemable custody");
            h.assertValueEqual(f.payments.get(), mode == 3 ? 1 : 0, "already observed debit remains evidence, never a compensation refund");
            h.assertTrue(f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive), "unknown outcome grants no damage lease");
            f.clearListeners(); finish(volley); BorrowedNeedleEscrow.retire(f.p);
            h.assertTrue(!BorrowedNeedleEscrow.claim(f.p) && f.reserve(6) == null && f.publish(volley, 1) == null,
                    "unknown outcome blocks claim, replacement round and continuation");
            h.assertTrue(f.retained() == 6 && f.needles() == 0, "unknown finish never mints any inventory return");
            h.assertValueEqual(count(f.p, Items.DIAMOND), mode == 1 ? 2 : 0, "foreign callback inventory effects are preserved");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_join_mutations_reject_before_payment_and_preserve_foreign_inventory(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 8)) {
            Object volley = f.reserve(6); int selected = mode;
            f.listen(event -> {
                BorrowedNeedleEntity candidate = (BorrowedNeedleEntity)event.getEntity();
                if (selected == 0) {
                    f.p.getInventory().setItem(0, f.p.getOffhandItem());
                    f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 7));
                    f.p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
                } else if (selected == 1) candidate.setUUID(UUID.randomUUID());
                else if (selected == 2) candidate.setPos(candidate.position().add(1, 0, 0));
                else candidate.discard();
            });
            h.assertTrue(f.publish(volley, 0) == null && f.payments.get() == 0, "actual mutated native join fails revalidation before shot debit");
            h.assertTrue(f.retained() == 6 && f.needles() == 2, "native mutation preserves exactly eight physical originals across inventory and six-slot custody");
            h.assertTrue(f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive), "changed unpaid native candidate never keeps damage authority");
            if (mode == 0) h.assertTrue(f.p.getOffhandItem().is(Items.DIRT) && f.p.getOffhandItem().getCount() == 7
                    && count(f.p, Items.DIAMOND) == 3 && f.p.getInventory().getItem(0).getCount() == 2, "all foreign native callback inventory changes survive without rollback");
            f.clearListeners(); finish(volley);
            h.assertTrue(!BorrowedNeedleEscrow.claim(f.p) && f.reserve(6) == null && f.retained() == 6, "uncertain native mutation never grants automatic compensation or retry");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_leave_error_revokes_every_slot_before_propagating_and_blocks_all_claims(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 6)) {
            Object volley = f.reserve(6); for (int i = 0; i < 6; i++) h.assertTrue(f.publish(volley, i) != null, "all six native projections admitted");
            BorrowedNeedleEntity selected = f.entities.get(2); AssertionError declared = new AssertionError("Declared six-slot native leave Error");
            f.hook(new Object() { @SubscribeEvent public void leave(EntityLeaveLevelEvent event) { if (event.getEntity() == selected) throw declared; } });
            boolean propagated = false;
            try { finish(volley); } catch (AssertionError actual) { if (actual != declared) throw actual; propagated = true; }
            h.assertTrue(propagated, "exact native callback Error remains observable");
            h.assertTrue(f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive), "partial removal Error revokes all six damage tokens");
            h.assertTrue(f.retained() == 6 && f.needles() == 0 && !BorrowedNeedleEscrow.claim(f.p), "partial unknown retirement never grants originals");
            f.clearListeners(); finish(volley);
            h.assertTrue(f.retained() == 6 && f.needles() == 0 && !BorrowedNeedleEscrow.claim(f.p), "retry cannot turn unknown removal into return authority");
            h.assertValueEqual(f.payments.get(), 6, "finish Error cannot replay payment"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_leave_raw_mutation_is_preserved_verbatim_and_globally_blocks_reuse(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 6)) {
            Object volley = f.reserve(6); BorrowedNeedleEntity shot = f.publish(volley, 0); CompoundTag[] raw = new CompoundTag[1];
            h.assertTrue(shot != null, "actual native projection admitted before raw leave mutation");
            f.hook(new Object() { @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
                if (event.getEntity() != shot) return;
                CompoundTag source = f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY);
                source.putString("declared_unknown_leave", "preserve exact callback bytes"); raw[0] = source.copy();
                h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "busy native leave callback cannot redeem custody");
            } });
            retireShot(shot);
            h.assertTrue(raw[0] != null && f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY).equals(raw[0]),
                    "foreign native leave mutation is never overwritten by retirement");
            f.clearListeners(); finish(volley); BorrowedNeedleEscrow.retire(f.p);
            h.assertTrue(f.retained() == 6 && f.needles() == 0 && !BorrowedNeedleEscrow.claim(f.p) && f.reserve(6) == null,
                    "unknown raw source prevents every future grant and reborrow");
            h.assertTrue(f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY).equals(raw[0]), "unknown raw is still verbatim after refused actions");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void full_bag_end_holds_six_until_one_manual_claim_in_survival_and_creative(GameTestHelper h) {
        for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) try (Fixture f = new Fixture(h, 6)) {
            f.p.setGameMode(mode); fillInventory(f.p); Object volley = f.reserve(6);
            h.assertTrue(volley != null && f.publish(volley, 0) != null, "actual six-stock custody exists even in creative");
            finish(volley);
            h.assertTrue(f.retained() == 6 && f.needles() == 0 && !BorrowedNeedleEscrow.claim(f.p), "full native bag retains all six exact originals");
            h.assertTrue(f.reserve(6) == null, "unsettled pending return globally blocks replacement volley");
            f.p.getInventory().setItem(8, ItemStack.EMPTY);
            finish(volley); BorrowedNeedleEscrow.retire(f.p);
            h.assertTrue(f.p.getInventory().getItem(8).isEmpty() && f.retained() == 6, "later capacity never auto-retries round return");
            h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "explicit manual claim inserts six originals into actual free bag capacity");
            h.assertTrue(f.needles() == 6 && f.retained() == 0 && !BorrowedNeedleEscrow.claim(f.p), "manual six-stock claim is singular");
            h.assertValueEqual(f.droppedNeedles(), 0, "ordinary round return never publishes a world ItemEntity");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void saved_spent_generations_reload_as_returns_and_never_resume_attack(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 6)) {
            fillInventory(f.p); Object volley = f.reserve(16); BorrowedNeedleEntity first = f.publish(volley, 0);
            h.assertTrue(first != null, "first native generation exists"); retireShot(first);
            for (int i = 1; i <= 6; i++) {
                BorrowedNeedleEntity shot = f.publish(volley, i); h.assertTrue(shot != null, "native spent-index fixture admitted " + i);
                retireShot(shot);
            }
            long spentGeneration = f.entities.stream().mapToLong(e -> entityLong(e, "leaseGeneration")).max().orElseThrow();
            CompoundTag saved = f.p.saveWithoutId(new CompoundTag()); int debits = f.payments.get(); f.reload(saved);
            h.assertTrue(f.retained() == 6 && f.needles() == 0 && !f.current(volley), "native player load restores six held originals without restoring old round");
            h.assertTrue(f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive) && f.publish(volley, 7) == null,
                    "loaded spent indices and old projections can never resume attack");
            h.assertTrue(f.reserve(6) == null, "serialized pending return blocks a replacement round until explicit claim");
            f.p.getInventory().setItem(4, ItemStack.EMPTY); h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "loaded exact six originals claim once");
            f.p.setItemInHand(InteractionHand.OFF_HAND, f.p.getInventory().removeItemNoUpdate(firstNeedleSlot(f.p)));
            Object next = f.reserve(6); BorrowedNeedleEntity newShot = f.publish(next, 0);
            h.assertTrue(newShot != null && entityLong(newShot, "leaseGeneration") > spentGeneration, "native reload preserves monotonically spent lease generation fence");
            h.assertValueEqual(f.payments.get(), debits + 1, "load itself never pays or launches a shot"); finish(next); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void strict_v1_single_original_migrates_to_v2_without_sixfold_stock_or_attack(GameTestHelper h) {
        for (State source : new State[]{State.ACTIVE, State.STAGED, State.ESCROWED, State.RETURN_PENDING, State.QUARANTINED, State.DEATH_TRANSFER})
            try (Fixture f = new Fixture(h, 1)) {
                fillInventory(f.p);
                var physical = BorrowedNeedleEscrow.borrow(f.p, f.position(), () -> true, f::pay);
                h.assertTrue(physical.isPresent(), "one real removed original supplies legacy fixture"); f.entities.add(physical.orElseThrow());
                CompoundTag saved = f.p.saveWithoutId(new CompoundTag()), actual = saved.getCompound("NeoForgeData").getCompound(BorrowedNeedleEscrow.STORAGE_KEY);
                CompoundTag legacy = new CompoundTag(); legacy.putInt("version", 1);
                for (String key : new String[]{"owner", "next_nonce", "exhausted", "nonce", "stock", "reason"}) legacy.put(key, actual.get(key).copy());
                legacy.putString("state", source.name()); saved.getCompound("NeoForgeData").put(BorrowedNeedleEscrow.STORAGE_KEY, legacy);
                f.reload(saved);
                h.assertTrue(f.retained() == 1 && f.needles() == 0, "strict legacy record retains exactly one original, never expands it to six");
                h.assertTrue(f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive) && f.reserve(6) == null, "legacy load restores no attack or replacement volley");
                f.p.getInventory().setItem(4, ItemStack.EMPTY);
                boolean safe = source != State.QUARANTINED && source != State.DEATH_TRANSFER;
                h.assertTrue(BorrowedNeedleEscrow.claim(f.p) == safe, "only proven legacy physical stock may return");
                h.assertTrue(f.needles() + f.retained() == 1 && !BorrowedNeedleEscrow.claim(f.p), "migration and repeated claim conserve sole legacy original");
                if (safe) h.assertValueEqual(f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY).getInt("version"), 2,
                        "next actual custody write migrates strict legacy source to v2");
            }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void malformed_v2_records_preserve_exact_raw_without_grant_or_repair(GameTestHelper h) {
        for (int mode = 0; mode < 10; mode++) try (Fixture f = new Fixture(h, 6)) {
            fillInventory(f.p); Object volley = f.reserve(6); h.assertTrue(volley != null, "native six-item reservation supplies malformed codec fixture");
            CompoundTag saved = f.p.saveWithoutId(new CompoundTag()), root = saved.getCompound("NeoForgeData").getCompound(BorrowedNeedleEscrow.STORAGE_KEY);
            if (mode == 0) root.putString("version", "2");
            if (mode == 1) root.putLong("next_nonce", 0);
            if (mode == 2) root.putString("mode", "FUTURE_MODE");
            if (mode == 3) root.putInt("future_authority", 1);
            if (mode == 4) root.getList("slots", 10).remove(5);
            if (mode == 5) root.getList("slots", 10).getCompound(2).putInt("index", 1);
            if (mode == 6) root.getList("slots", 10).getCompound(2).putString("generation", "0");
            if (mode == 7) root.getList("slots", 10).getCompound(2).putInt("unexpected_authority", 1);
            if (mode == 8) root.putInt("spent", 1);
            if (mode == 9) root.getList("slots", 10).getCompound(2).put("stock", new ItemStack(AcademyCraft.NEEDLE.get(), 2).save(f.p.registryAccess()));
            CompoundTag exact = root.copy(); f.reload(saved); f.p.getInventory().setItem(4, ItemStack.EMPTY);
            BorrowedNeedleEscrow.retire(f.p);
            h.assertTrue(!BorrowedNeedleEscrow.claim(f.p) && f.reserve(6) == null && !f.current(volley), "strict malformed record grants neither originals nor attack");
            h.assertTrue(f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY).equals(exact), "unknown v2 bytes remain verbatim after all refused actions");
            h.assertValueEqual(f.needles(), 0, "malformed codec does not invent inventory stock");
            h.assertValueEqual(f.payments.get(), 0, "malformed reserve/load never executes payment");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void last_finite_generation_is_spent_once_and_cannot_wrap_after_native_reload(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 6)) {
            fillInventory(f.p); h.assertTrue(f.reserve(6) != null, "actual removed originals supply finite generation boundary fixture");
            CompoundTag saved = f.p.saveWithoutId(new CompoundTag());
            saved.getCompound("NeoForgeData").getCompound(BorrowedNeedleEscrow.STORAGE_KEY).putLong("next_generation", Long.MAX_VALUE);
            f.reload(saved); f.p.getInventory().setItem(6, ItemStack.EMPTY);
            h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "valid boundary fixture returns its exact six serialized originals once");
            f.p.setItemInHand(InteractionHand.OFF_HAND, f.p.getInventory().removeItemNoUpdate(firstNeedleSlot(f.p)));
            Object volley = f.reserve(6); BorrowedNeedleEntity finalShot = f.publish(volley, 0);
            h.assertTrue(finalShot != null && entityLong(finalShot, "leaseGeneration") == Long.MAX_VALUE, "last finite lease generation is admitted exactly once");
            h.assertTrue(f.publish(volley, 1) == null && f.publish(volley, 0) == null, "exhausted generation can neither wrap nor replay the final index");
            finish(volley);
            h.assertTrue(f.needles() == 6 && f.retained() == 0 && f.reserve(6) == null, "generation exhaustion conserves stock and blocks every future round");
            h.assertValueEqual(f.payments.get(), 1, "only final finite admitted generation pays"); h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_death_keep_inventory_clone_and_captured_six_drops_conserve_stock(GameTestHelper h) {
        Fixture kept = new Fixture(h, 6), dropped = new Fixture(h, 6);
        kept.ready(() -> {
            for (Fixture f : List.of(kept, dropped)) {
                boolean keep = f == kept, oldRule = h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
                try {
                    h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(keep, h.getLevel().getServer());
                    Object volley = f.reserve(6); h.assertTrue(volley != null && f.publish(volley, 0) != null, "actual six-stock round precedes genuine native death");
                    AtomicInteger captured = new AtomicInteger();
                    f.hook(new Object() { @SubscribeEvent(priority=EventPriority.LOWEST) public void drops(LivingDropsEvent event) {
                        if (event.getEntity() != f.p) return;
                        h.assertTrue(f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive), "all damage tokens revoked before native drop capture observer");
                        captured.set(event.getDrops().stream().filter(e -> e.getItem().is(AcademyCraft.NEEDLE.get())).mapToInt(e -> e.getItem().getCount()).sum());
                        f.items.addAll(event.getDrops());
                    } });
                    h.assertTrue(f.p.hurt(f.p.damageSources().generic(), Float.MAX_VALUE), "ordinary native lethal damage commits after real login protection expires");
                    h.assertTrue(!f.p.isAlive() && !f.current(volley), "confirmed native death ends round authority");
                    h.assertValueEqual(captured.get(), keep ? 0 : 6, "native capture receives exactly six rule-permitted originals");
                    h.assertValueEqual(f.retained(), keep ? 6 : 0, "keepInventory and native drop custody remain exclusive");
                    h.assertValueEqual(f.needles() + f.retained() + f.droppedNeedles(), 6, "native death conserves all six physical originals");
                    if (keep) {
                        ServerPlayer old = f.p; old.server.getPlayerList().remove(old); f.p = f.actors.player(old.getGameProfile());
                        f.p.getPersistentData().remove(BorrowedNeedleEscrow.STORAGE_KEY); f.p.restoreFrom(old, false);
                        h.assertTrue(f.p.isAlive() && f.retained() == 6 && BorrowedNeedleEscrow.retainedCount(old) == 0, "native kept-death clone has sole six-stock return authority");
                        h.assertTrue(BorrowedNeedleEscrow.claim(f.p) && !BorrowedNeedleEscrow.claim(f.p), "living native clone claims six kept originals once");
                        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(f.p, old, true));
                        h.assertValueEqual(f.needles(), 6, "duplicate native clone notification cannot duplicate stock");
                    } else { BorrowedNeedleEscrow.deathDrop(f.p); h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "settled native capture cannot refund into dead owner"); }
                    f.items.forEach(Entity::discard); f.clearListeners();
                } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(oldRule, h.getLevel().getServer()); }
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_lethal_join_callback_settles_all_six_before_any_payment(GameTestHelper h) {
        Fixture kept = new Fixture(h, 6), dropped = new Fixture(h, 6); kept.ready(() -> {
            for (Fixture f : List.of(kept, dropped)) {
                boolean keep = f == kept, oldRule = h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
                try {
                    h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(keep, h.getLevel().getServer());
                    Object volley = f.reserve(6); h.assertTrue(volley != null, "actual six-stock round reserved before lethal native join callback");
                    f.hook(new Object() { @SubscribeEvent public void drops(LivingDropsEvent event) { if (event.getEntity() == f.p) f.items.addAll(event.getDrops()); } });
                    f.listen(event -> {
                        h.assertTrue(f.retained() == 6 && f.payments.get() == 0 && !((BorrowedNeedleEntity)event.getEntity()).isAdmitted(),
                                "lethal native join begins with all six originals held and candidate unpaid");
                        h.assertTrue(f.p.hurt(f.p.damageSources().generic(), Float.MAX_VALUE), "actual native callback commits ordinary lethal damage");
                    });
                    h.assertTrue(f.publish(volley, 0) == null, "outer native publication cannot resume after callback death");
                    h.assertTrue(!f.p.isAlive() && f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive), "confirmed callback death permits no new projection lease");
                    h.assertTrue(f.payments.get() == 0 && f.retained() == (keep ? 6 : 0), "outer unwind preserves committed six-slot death outcome before payment");
                    h.assertValueEqual(f.needles() + f.retained() + f.droppedNeedles(), 6, "native lethal join conserves exactly six physical originals");
                    h.assertValueEqual(f.droppedNeedles(), keep ? 0 : 6, "native callback death obeys actual keepInventory rule");
                    f.items.forEach(Entity::discard); f.clearListeners();
                } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(oldRule, h.getLevel().getServer()); }
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_cancelled_six_drop_capture_never_recreates_a_refund(GameTestHelper h) {
        Fixture f = new Fixture(h, 6); f.ready(() -> {
            boolean oldRule = h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            try {
                h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, h.getLevel().getServer());
                Object volley = f.reserve(6); h.assertTrue(volley != null && f.publish(volley, 0) != null, "actual six originals held before native drop veto");
                AtomicInteger captured = new AtomicInteger();
                f.hook(new Object() { @SubscribeEvent(priority=EventPriority.LOWEST) public void drops(LivingDropsEvent event) {
                    if (event.getEntity() != f.p) return;
                    captured.set(event.getDrops().stream().filter(e -> e.getItem().is(AcademyCraft.NEEDLE.get())).mapToInt(e -> e.getItem().getCount()).sum());
                    f.items.addAll(event.getDrops()); event.setCanceled(true);
                } });
                h.assertTrue(f.p.hurt(f.p.damageSources().generic(), Float.MAX_VALUE), "actual lethal hit reaches native six-item drop veto");
                h.assertValueEqual(captured.get(), 6, "all six originals really entered native drop capture before veto");
                h.assertTrue(f.retained() == 0 && f.needles() == 0 && f.droppedNeedles() == 0, "downstream native veto owns consumed capture and publishes no world refund");
                finish(volley); BorrowedNeedleEscrow.deathDrop(f.p); BorrowedNeedleEscrow.retire(f.p);
                h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "cancelled native drop never authorizes recreated inventory stock");
                h.assertValueEqual(f.payments.get(), 1, "drop capture cancellation cannot repay or relaunch"); h.succeed();
            } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(oldRule, h.getLevel().getServer()); }
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void actual_logout_and_native_clone_transfer_six_pending_originals_once(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 6)) {
            fillInventory(f.p); Object volley = f.reserve(6); BorrowedNeedleEntity shot = f.publish(volley, 0);
            h.assertTrue(shot != null, "actual six-stock round has one genuine native projection before logout");
            ServerPlayer old = f.p; old.server.getPlayerList().remove(old);
            h.assertTrue(!shot.isLeaseActive() && !f.current(volley) && !BorrowedNeedleEscrow.claim(old), "real native logout revokes old session and return destination");
            f.p = f.actors.player(old.getGameProfile()); f.p.getPersistentData().remove(BorrowedNeedleEscrow.STORAGE_KEY); f.p.restoreFrom(old, true);
            h.assertTrue(f.p != old && f.p.getUUID().equals(old.getUUID()) && f.retained() == 6, "native restoreFrom carries exact six originals to current replacement owner");
            h.assertTrue(BorrowedNeedleEscrow.retainedCount(old) == 0 && !BorrowedNeedleEscrow.claim(old), "stale logged-out object has no original custody authority");
            f.p.getInventory().setItem(5, ItemStack.EMPTY);
            h.assertTrue(BorrowedNeedleEscrow.claim(f.p) && !BorrowedNeedleEscrow.claim(f.p), "replacement native owner actually inserts all six originals exactly once");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(f.p, old, false));
            h.assertTrue(f.needles() == 6 && f.retained() == 0 && f.payments.get() == 1, "duplicate clone cannot copy settled stock or replay payment"); h.succeed();
        }
    }

    @GameTest(template=BOUNDARY, batch=BATCH, timeoutTicks=65)
    public static void forty_genuine_entity_ticks_retire_only_shot_and_keep_round_stock_until_finish(GameTestHelper h) {
        Fixture f = new Fixture(h, 6); Vec3 base = h.absoluteVec(Vec3.ZERO);
        double boundaryZ = (Math.floor(base.z / 16) + 1) * 16;
        f.p.moveTo(base.x + 2.5, base.y + 1, boundaryZ - .5, 0, 0); f.p.connection.resetPosition();
        f.origin = new Vec3(base.x + 5.5, base.y + 3.5, boundaryZ + .5);
        h.assertTrue(new ChunkPos(f.p.blockPosition()).z + 1 == new ChunkPos(BlockPos.containing(f.position())).z, "native fixture always straddles a real chunk boundary");
        h.assertTrue(h.getBounds().contains(f.p.position()) && h.getBounds().contains(f.position()), "readiness bounding template contains every native fixture coordinate");
        f.nativeReady(() -> {
            Object volley = f.reserve(12); BorrowedNeedleEntity shot = f.publish(volley, 0); AtomicInteger ticks = new AtomicInteger(); boolean[] live39 = {false};
            h.assertTrue(shot != null, "native entity-ticking chunk admits first round projection");
            f.hook(new Object() { @SubscribeEvent public void tick(EntityTickEvent.Post event) {
                if (event.getEntity() != shot) return;
                int n = ticks.incrementAndGet(); if (n == 39) live39[0] = shot.isLeaseActive();
            } });
            h.runAfterDelay(40, () -> {
                h.assertValueEqual(ticks.get(), 40, "forty actual native ServerLevel entity ticks, without manually invoking entity tick");
                h.assertTrue(live39[0] && shot.isRemoved() && !shot.isLeaseActive(), "exact production lease remains through tick39 and retires at tick40");
                h.assertTrue(f.current(volley) && f.retained() == 6 && f.needles() == 0, "per-shot lease expiry keeps all six originals in the still-current round");
                for (int i = 1; i < 6; i++) h.assertTrue(f.publish(volley, i) != null, "next five preallocated indices admit without reborrowing stock");
                h.assertTrue(f.available(volley, 6), "fortieth native tick definitively frees expired slot zero for its next preallocated generation");
                finish(volley); h.assertTrue(f.needles() == 6 && f.retained() == 0, "explicit round finish returns entire stock once after real expiry"); h.succeed();
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=180)
    public static void onehundredsixty_actual_server_ticks_end_unfired_round_without_inventory_autoretry(GameTestHelper h) {
        Fixture f = new Fixture(h, 6); fillInventory(f.p);
        f.nativeReady(() -> {
            Object volley = f.reserve(16); int started = f.p.server.getTickCount();
            h.assertTrue(volley != null && f.current(volley) && f.retained() == 6, "native unopened-shot round reserves all six exact originals");
            h.runAfterDelay(159, () -> {
                h.assertValueEqual(f.p.server.getTickCount() - started, 159, "onehundredfiftynine actual server ticks elapsed without editing clocks");
                h.assertTrue(f.current(volley) && f.retained() == 6 && f.needles() == 0, "native round remains current through deadline tick159");
            });
            h.runAfterDelay(160, () -> {
                h.assertValueEqual(f.p.server.getTickCount() - started, 160, "exactly onehundredsixty actual server ticks elapsed");
                h.assertTrue(!f.current(volley) && f.retained() == 6 && f.needles() == 0, "native deadline ends admission and retains fullbag originals");
                f.p.getInventory().setItem(11, ItemStack.EMPTY);
                h.runAfterDelay(3, () -> {
                    h.assertTrue(f.retained() == 6 && f.p.getInventory().getItem(11).isEmpty(), "opening bag after native deadline never auto-retries return");
                    h.assertTrue(BorrowedNeedleEscrow.claim(f.p) && !BorrowedNeedleEscrow.claim(f.p), "explicit claim alone settles native deadline originals once");
                    h.assertTrue(f.needles() == 6 && f.retained() == 0 && f.payments.get() == 0, "unfired native deadline conserves exact stock with no invented shot debit");
                    h.succeed();
                });
            });
        });
    }

    private static Object call(String name, Object... args) {
        for (Method method : BorrowedNeedleEscrow.class.getDeclaredMethods()) {
            if (!method.getName().equals(name) || method.getParameterCount() != args.length) continue;
            method.setAccessible(true);
            try { return method.invoke(null, args); }
            catch (InvocationTargetException failure) { throw unchecked(failure.getCause()); }
            catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned volley API unavailable: " + name, failure); }
        }
        throw new IllegalStateException("Pinned volley API absent: " + name);
    }
    private static RuntimeException unchecked(Throwable failure) {
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException runtime) return runtime;
        return new IllegalStateException("Native volley reflection failed", failure);
    }
    private static Object tickHandler(Consumer<BorrowedNeedleEntity> tick) {
        try {
            Class<?> type = Class.forName("cn.academy.port.highpower.BorrowedNeedleEscrow$ShotTick");
            return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                if (method.getName().equals("tick")) { tick.accept((BorrowedNeedleEntity)args[0]); return null; }
                if (method.getName().equals("toString")) return "Declared native volley tick witness";
                if (method.getName().equals("hashCode")) return System.identityHashCode(proxy);
                if (method.getName().equals("equals")) return proxy == args[0];
                throw new IllegalStateException("Unexpected ShotTick method: " + method);
            });
        } catch (ClassNotFoundException failure) { throw new IllegalStateException("Pinned ShotTick contract absent", failure); }
    }
    private static Object unwrap(Object value) { return value instanceof Optional<?> result ? result.orElse(null) : value; }
    private static void finish(Object volley) { call("finishVolley", volley); }
    private static void retireShot(BorrowedNeedleEntity shot) { call("retireShot", shot); }
    private static long entityLong(BorrowedNeedleEntity entity, String name) {
        try { return ((Number)entity.getClass().getMethod(name).invoke(entity)).longValue(); }
        catch (InvocationTargetException failure) { throw unchecked(failure.getCause()); }
        catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned entity custody witness absent: " + name, failure); }
    }
    private static int entityInt(BorrowedNeedleEntity entity, String name) { return (int)entityLong(entity, name); }
    private static void fillInventory(ServerPlayer owner) {
        for (int slot = 0; slot < 36; slot++) owner.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
    }
    private static int count(ServerPlayer owner, Item item) {
        int result = 0;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            ItemStack stack = owner.getInventory().getItem(slot); if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }
    private static int firstNeedleSlot(ServerPlayer owner) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) if (owner.getInventory().getItem(slot).is(AcademyCraft.NEEDLE.get())) return slot;
        throw new IllegalStateException("Declared native original not found after claim");
    }
    public static final class JoinProbe {
        final UUID owner; final Consumer<EntityJoinLevelEvent> callback;
        JoinProbe(UUID owner, Consumer<EntityJoinLevelEvent> callback) { this.owner = owner; this.callback = callback; }
        @SubscribeEvent public void join(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof BorrowedNeedleEntity entity && owner.equals(entity.ownerId())) callback.accept(event);
        }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final AcademyWirelessDeviceRuntimeTests.NativeMenuActors actors;
        final List<Object> probes = new ArrayList<>(); final List<BorrowedNeedleEntity> entities = new ArrayList<>(); final List<ItemEntity> items = new ArrayList<>();
        final AtomicInteger payments = new AtomicInteger(); final Set<ChunkPos> held = new LinkedHashSet<>(); final UUID ticket = UUID.randomUUID();
        final int started; ServerPlayer p; BorrowedNeedleEntity joined; Vec3 origin; boolean closed;
        Fixture(GameTestHelper h, int stock) {
            this.h = h; actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h); p = actors.player(); started = p.server.getTickCount();
            p.getInventory().clearContent(); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), stock)); p.setInvulnerable(false);
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) { }
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
            listen(event -> { });
        }
        Vec3 position() { return origin == null ? h.absoluteVec(new Vec3(5.5, 3.5, 5.5)) : origin; }
        int needles() { return count(p, AcademyCraft.NEEDLE.get()); }
        int retained() { return BorrowedNeedleEscrow.retainedCount(p); }
        boolean pay() { payments.incrementAndGet(); return true; }
        Object reserve(int n) { return unwrap(call("reserveVolley", p, UUID.randomUUID(), n)); }
        boolean current(Object volley) { return volley != null && Boolean.TRUE.equals(call("current", volley)); }
        boolean available(Object volley, int slot) { return Boolean.TRUE.equals(call("slotAvailable", volley, slot)); }
        @SuppressWarnings("unchecked") List<?> slots() { return (List<?>)call("allView", p); }
        BorrowedNeedleEntity publish(Object volley, int i) { return publish(volley, i, () -> true, this::pay, entity -> { }); }
        BorrowedNeedleEntity publish(Object volley, int i, BooleanSupplier guard, BooleanSupplier payment, Consumer<BorrowedNeedleEntity> tick) {
            BorrowedNeedleEntity entity = (BorrowedNeedleEntity)unwrap(call("publishShot", volley, i, position(), new Vec3(0, 0, .125), guard, payment, tickHandler(tick)));
            if (entity != null && !entities.contains(entity)) entities.add(entity); return entity;
        }
        int nativeProjectionCount() { return (int)entities.stream().filter(entity -> !entity.isRemoved() && h.getLevel().getEntity(entity.getUUID()) == entity).count(); }
        int droppedNeedles() {
            return h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3), item -> !item.isRemoved() && item.getItem().is(AcademyCraft.NEEDLE.get()))
                    .stream().mapToInt(item -> item.getItem().getCount()).sum();
        }
        void hook(Object probe) { probes.add(probe); NeoForge.EVENT_BUS.register(probe); }
        void listen(Consumer<EntityJoinLevelEvent> callback) {
            hook(new JoinProbe(p.getUUID(), event -> { joined = (BorrowedNeedleEntity)event.getEntity(); if (!entities.contains(joined)) entities.add(joined); callback.accept(event); }));
        }
        void clearListeners() { probes.forEach(NeoForge.EVENT_BUS::unregister); probes.clear(); }
        void reload(CompoundTag saved) {
            clearListeners(); ServerPlayer old = p;
            if (old.server.getPlayerList().getPlayer(old.getUUID()) == old) old.server.getPlayerList().remove(old);
            p = actors.player(old.getGameProfile()); p.load(saved.copy()); listen(event -> { });
            h.assertTrue(p.server.getPlayerList().getPlayer(p.getUUID()) == p, "native codec restored exact current PlayerList owner");
        }
        void ready(Runnable action) { h.runAfterDelay(61, () -> { h.assertTrue(p.server.getTickCount() - started >= 61, "actual native resident ticks expire login protection"); action.run(); }); }
        void nativeReady(Runnable action) {
            for (Vec3 point : List.of(p.position(), position())) {
                ChunkPos chunk = new ChunkPos(BlockPos.containing(point));
                if (held.add(chunk)) { h.getLevel().getChunkSource().addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(chunk.x, chunk.z); }
            }
            h.startSequence().thenWaitUntil(() -> {
                for (Vec3 point : List.of(p.position(), position())) {
                    BlockPos pos = BlockPos.containing(point);
                    h.assertTrue(h.getLevel().areEntitiesLoaded(ChunkPos.asLong(pos)) && h.getLevel().isPositionEntityTicking(pos), "all actual native fixture chunks become entity-ticking");
                }
            }).thenExecute(action);
        }
        @Override public void close() {
            if (closed) return; closed = true; clearListeners();
            for (BorrowedNeedleEntity entity : entities) if (!entity.isRemoved()) entity.discard();
            for (ItemEntity item : items) if (!item.isRemoved()) item.discard(); actors.close();
            for (ChunkPos chunk : held) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket);
        }
    }
}
