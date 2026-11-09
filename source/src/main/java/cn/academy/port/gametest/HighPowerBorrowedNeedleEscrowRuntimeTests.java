package cn.academy.port.gametest;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.BorrowedNeedleEntity;
import cn.academy.port.highpower.BorrowedNeedleEscrow;
import cn.academy.port.highpower.BorrowedNeedleEscrow.State;
import cn.academy.port.highpower.TeleporterNeedleEntities;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.List;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
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
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.GameTestHooks;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** NEW351 PHASE1 custody only: real PlayerList owners, native publication, native
 * player codecs and native death/drop callbacks. Inventory and positions are declared
 * fixtures. Same-process codec restoration does not claim an atomic process restart.
 * No skill, learning, attack generation, test command or public cast is introduced. */
@GameTestHolder("academy_highpower_borrowed")
@PrefixGameTestTemplate(false)
@EventBusSubscriber(modid="academy")
public final class HighPowerBorrowedNeedleEscrowRuntimeTests {
    private static final String TEMPLATE = "highpower_empty", BATCH = "highpower_borrowed_needle_escrow";
    private static final String EXPIRY_TEMPLATE = "highpower_borrowed_boundary";
    private static final org.slf4j.Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<UUID> TICKET = TicketType.create("academy_borrowed_needle_fixture", UUID::compareTo);
    private HighPowerBorrowedNeedleEscrowRuntimeTests() { }

    @SubscribeEvent
    public static void installTemplate(LevelEvent.Load event) {
        if (!GameTestHooks.isGametestEnabled() || !(event.getLevel() instanceof ServerLevel level)) return;
        // Native GameTestInfo waits for entity-ticking only in the structure's
        // bounding chunks. The declared projection at local (5.5, 3.5, 5.5)
        // must be inside that pre-execution gate. The former 4-cube failed in
        // full r4 at structure (4626865, -60, 13171612): owner chunk z=823225,
        // projection chunk z=823226, loaded but not entity-ticking.
        installTemplate(level, TEMPLATE, 8, 8, 8);
        // One wider fixture reproduces that same chunk-boundary relationship
        // on every random suite origin while covering both native chunks.
        installTemplate(level, EXPIRY_TEMPLATE, 20, 8, 20);
    }

    private static void installTemplate(ServerLevel level, String name, int xSize, int ySize, int zSize) {
        CompoundTag tag = new CompoundTag(), cell = new CompoundTag(), air = new CompoundTag();
        ListTag size = new ListTag(), position = new ListTag(), blocks = new ListTag(), palette = new ListTag();
        for (int length : new int[]{xSize, ySize, zSize}) { size.add(IntTag.valueOf(length)); position.add(IntTag.valueOf(0)); }
        cell.put("pos", position); cell.putInt("state", 0); blocks.add(cell);
        air.putString("Name", "minecraft:air"); palette.add(air);
        tag.put("size", size); tag.put("blocks", blocks); tag.put("palette", palette); tag.put("entities", new ListTag());
        level.getStructureManager().getOrCreate(ResourceLocation.fromNamespaceAndPath("academy_highpower_borrowed", name))
                .load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void actual_singleton_and_stack_split_publish_before_one_payment(GameTestHelper h) {
        for (int stock : new int[]{1, 3}) try (Fixture f = new Fixture(h, stock)) {
            ItemStack original = f.p.getOffhandItem();
            f.listen(event -> {
                var candidate = (BorrowedNeedleEntity)event.getEntity();
                h.assertTrue(candidate.getType() == TeleporterNeedleEntities.BORROWED_NEEDLE.get(), "dedicated non-item native projection type");
                h.assertTrue(f.state() == State.STAGED, "actual escrow is staged before native entity join");
                h.assertValueEqual(f.needles(), stock - 1, "one actual offhand original removed before publication");
                h.assertValueEqual(f.retained(), 1, "projection contains no second physical item");
                h.assertValueEqual(f.payments.get(), 0, "native publication precedes payment");
                h.assertFalse(candidate.isLeaseActive() || candidate.isAdmitted(), "unpaid candidate carries no live lease");
                h.assertFalse(f.borrow() || BorrowedNeedleEscrow.claim(f.p), "native callback cannot reenter publication or return");
            });
            h.assertTrue(f.borrow(() -> {
                var candidate = f.joined;
                h.assertTrue(h.getLevel().getEntity(candidate.getUUID()) == candidate
                        && h.getLevel().getEntity(candidate.getId()) == candidate, "both native indexes contain exact candidate before payment");
                h.assertTrue(f.state() == State.STAGED, "payment sees staged original custody");
                return f.pay();
            }), "single exact original admitted");
            h.assertTrue(f.last.isLeaseActive() && f.last.isAdmitted() && f.last.ownerId().equals(f.p.getUUID()), "committed native object belongs to exact owner");
            h.assertValueEqual(f.last.nonce(), BorrowedNeedleEscrow.view(f.p).nonce(), "native lease uses escrow nonce");
            h.assertTrue(stock == 1 ? f.p.getOffhandItem().isEmpty() : f.p.getOffhandItem() == original && original.getCount() == 2,
                    "singleton empties offhand and stack split preserves exact remaining object");
            h.assertValueEqual(f.needles() + f.retained(), stock, "inventory plus sole escrow conserves originals");
            h.assertFalse(f.borrow(), "one slot refuses another native lease");
            h.assertValueEqual(f.payments.get(), 1, "only one committed callback payment");
            h.assertFalse(f.last.save(new CompoundTag()), "borrowed projection is never a persistent world item");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_cancel_and_throw_preserve_exact_original_and_never_pay(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 1)) {
            int selected = mode;
            f.listen(event -> {
                h.assertFalse(f.borrow(), "join callback cannot reserve recursively");
                if (selected != 1) event.setCanceled(true);
                if (selected != 0) throw new IllegalStateException("Declared native borrowed-needle join failure");
            });
            h.assertFalse(f.borrow(), "native cancellation or callback throw refuses admission");
            h.assertValueEqual(f.payments.get(), 0, "failed native publication never pays");
            h.assertValueEqual(f.needles() + f.retained(), 1, "returned or retained exact original remains singular");
            h.assertFalse(f.joined.isLeaseActive() || f.joined.isAdmitted(), "failed candidate cannot retain damage permission");
            if (mode == 0) h.assertTrue(f.state() == State.EMPTY && f.needles() == 1, "known unchanged cancellation safely returns original");
            else h.assertTrue(f.state() == State.QUARANTINED && f.retained() == 1, "throw keeps uncertain original quarantined");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "settled cancellation and unknown throw cannot replay a grant");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_join_mutations_never_rollback_foreign_inventory_or_pay(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 3)) {
            int selected = mode;
            f.listen(event -> {
                var candidate = (BorrowedNeedleEntity)event.getEntity();
                if (selected == 0) {
                    f.p.getInventory().setItem(0, f.p.getOffhandItem());
                    f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 7));
                    f.p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
                } else if (selected == 1) candidate.setUUID(UUID.randomUUID());
                else if (selected == 2) candidate.setPos(candidate.position().add(4, 0, 0));
                else candidate.discard();
            });
            h.assertFalse(f.borrow(), "changed native callback refuses commit: " + mode);
            h.assertValueEqual(f.payments.get(), 0, "revalidation precedes any payment");
            h.assertValueEqual(f.needles() + f.retained(), 3, "no snapshot rollback creates or loses original inventory");
            h.assertFalse(f.joined.isLeaseActive() || f.joined.isAdmitted(), "changed candidate never retains a lease");
            if (mode == 0) {
                h.assertTrue(f.p.getOffhandItem().is(Items.DIRT) && f.p.getOffhandItem().getCount() == 7, "callback replacement offhand remains untouched");
                h.assertValueEqual(count(f.p, Items.DIAMOND), 3, "unrelated callback inventory mutation preserved");
                h.assertValueEqual(f.p.getInventory().getItem(0).getCount(), 2, "relocated original remainder is not restored from a snapshot");
            }
            h.assertTrue(f.state() == State.QUARANTINED, "unexpected native mutation retains explicit quarantine");
            f.clearListeners();
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p) || f.borrow(), "unknown outcome cannot automatically retry or replace the slot");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void refused_payment_and_exceptions_before_or_after_debit_have_distinct_custody(GameTestHelper h) {
        for (int mode = 0; mode < 3; mode++) try (Fixture f = new Fixture(h, 1)) {
            int selected = mode;
            h.assertFalse(f.borrow(() -> {
                if (selected == 0) return false;
                if (selected == 2) f.pay();
                throw new IllegalStateException("Declared payment uncertainty " + selected);
            }), "unconfirmed payment never reports admission");
            h.assertValueEqual(f.payments.get(), mode == 2 ? 1 : 0, "external debit evidence is never fabricated or undone");
            h.assertValueEqual(f.needles() + f.retained(), 1, "one exact original remains accounted for");
            h.assertTrue(mode == 0 ? f.state() == State.EMPTY && f.needles() == 1 : f.state() == State.QUARANTINED && f.retained() == 1,
                    "known unpaid refusal returns; either throw keeps unknown outcome quarantined");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "unknown payment cannot be replayed as an item refund");
            h.assertTrue(f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive), "no failed publication restores damage lease");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void creative_and_survival_full_bags_retain_until_one_actual_claim(GameTestHelper h) {
        for (GameType mode : new GameType[]{GameType.SURVIVAL, GameType.CREATIVE}) try (Fixture f = new Fixture(h, 1)) {
            f.p.setGameMode(mode); fillInventory(f.p);
            h.assertTrue(f.borrow(), "one physical original borrowed even in creative");
            var projection = f.last;
            BorrowedNeedleEscrow.retire(f.p);
            h.assertTrue(!projection.isLeaseActive() && projection.isRemoved(), "retirement revokes and removes projection before returning");
            h.assertTrue(f.state() == State.RETURN_PENDING && f.retained() == 1, "full native inventory preserves actual escrow in " + mode);
            h.assertValueEqual(f.needles(), 0, "creative inventory rejection does not falsely prove insertion");
            h.assertTrue(f.p.getInventory().getFreeSlot() < 0, "all native main inventory slots are full");
            for (int attempt = 0; attempt < 3; attempt++) {
                h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "full bag cannot return or discard borrowed item");
                h.assertValueEqual(f.retained(), 1, "repeated failed claim retains exact original");
            }
            f.p.getInventory().setItem(7, ItemStack.EMPTY);
            h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "one actual newly free slot receives original");
            h.assertTrue(f.p.getInventory().getItem(7).is(AcademyCraft.NEEDLE.get())
                    && f.p.getInventory().getItem(7).getCount() == 1, "observed real insertion proves return");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "successful claim cannot replay");
            h.assertTrue(f.state() == State.EMPTY && f.retained() == 0 && f.needles() == 1, "exact original leaves escrow only once");
            h.assertValueEqual(f.payments.get(), 1, "retirement and claims never pay again");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void repeated_retire_after_fullbag_opens_capacity_never_retries_pending_return(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p); h.assertTrue(f.borrow(), "actual singleton borrowed from full native inventory");
            BorrowedNeedleEscrow.retire(f.p);
            h.assertTrue(f.state() == State.RETURN_PENDING && f.retained() == 1 && f.needles() == 0, "first retirement cannot insert into full inventory");
            f.p.getInventory().setItem(9, ItemStack.EMPTY);
            for (int attempt = 0; attempt < 3; attempt++) {
                BorrowedNeedleEscrow.retire(f.p);
                h.assertTrue(f.state() == State.RETURN_PENDING && f.retained() == 1 && f.needles() == 0,
                        "repeated lifecycle retirement does not retry an already pending manual claim");
                h.assertTrue(f.p.getInventory().getItem(9).isEmpty(), "newly free capacity remains unused until explicit claim");
            }
            h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "explicit claim alone inserts original after capacity becomes available");
            assertCanonicalEmpty(h, f, "explicit pending claim settles exact original once");
            h.assertValueEqual(f.needles(), 1, "one actual original after repeated retirement and one claim");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "explicit completion cannot replay");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void real_offhand_merge_is_claimed_once_without_world_drop(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            fillInventory(f.p);
            h.assertTrue(f.borrow(), "one original retained beside actual offhand remainder");
            BorrowedNeedleEscrow.retire(f.p);
            h.assertTrue(f.state() == State.EMPTY && f.retained() == 0, "retirement completes actual native offhand merge");
            h.assertValueEqual(f.p.getOffhandItem().getCount(), 2, "one original merged into exact one-item remainder");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "completed merge cannot replay");
            h.assertValueEqual(f.droppedNeedles(), 0, "ordinary return never publishes an ItemEntity");
            h.assertValueEqual(f.payments.get(), 1, "one original borrow was paid");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_constructor_size_and_constructing_reentry_have_no_lease_or_nested_payment(GameTestHelper h) {
        for (boolean size : new boolean[]{true, false}) try (Fixture f = new Fixture(h, 1)) {
            AtomicInteger calls = new AtomicInteger();
            f.construct(size, candidate -> {
                calls.incrementAndGet();
                h.assertTrue(f.state() == State.ESCROWED && f.retained() == 1 && f.needles() == 0,
                        "real native constructor callback sees actual original escrow before staging");
                h.assertTrue(candidate.ownerId() == null && candidate.nonce() == 0 && !candidate.isAdmitted() && !candidate.isLeaseActive(),
                        "native superclass constructor has no prepared owner, nonce or live lease");
                h.assertFalse(f.borrow() || BorrowedNeedleEscrow.claim(f.p), "constructor cannot recursively publish or claim original");
                h.assertValueEqual(f.payments.get(), 0, "native construction always precedes payment");
            });
            h.assertTrue(f.borrow(), "unchanged native construction admits exactly one lease");
            h.assertValueEqual(calls.get(), 1, "selected actual native constructor seam ran exactly once");
            h.assertTrue(f.state() == State.ACTIVE && f.retained() == 1 && f.needles() == 0, "reentrant refusal preserves actual original");
            h.assertValueEqual(f.payments.get(), 1, "only outer native admission pays");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_constructor_inventory_mutation_is_rejected_before_any_world_publication(GameTestHelper h) {
        for (boolean size : new boolean[]{true, false}) try (Fixture f = new Fixture(h, 3)) {
            AtomicInteger calls = new AtomicInteger(), joins = new AtomicInteger();
            f.listen(event -> joins.incrementAndGet());
            f.construct(size, candidate -> {
                calls.incrementAndGet();
                h.assertTrue(f.state() == State.ESCROWED && f.retained() == 1, "actual escrow precedes constructor mutation");
                f.p.getInventory().setItem(0, f.p.getOffhandItem());
                f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.DIRT, 7));
                f.p.getInventory().setItem(1, new ItemStack(Items.DIAMOND, 3));
            });
            h.assertFalse(f.borrow(), "constructor-mutated owner cannot admit a projection");
            h.assertValueEqual(calls.get(), 1, "real selected constructor callback was reached");
            h.assertValueEqual(joins.get(), 0, "constructor mutation is checked before native world publication");
            h.assertValueEqual(f.payments.get(), 0, "constructor mutation is checked before payment");
            h.assertTrue(f.state() == State.QUARANTINED && f.retained() == 1, "uncertain constructor mutation quarantines actual original");
            h.assertTrue(f.p.getOffhandItem().is(Items.DIRT) && f.p.getOffhandItem().getCount() == 7, "foreign constructor offhand is never rolled back");
            h.assertValueEqual(count(f.p, Items.DIAMOND), 3, "unrelated constructor inventory mutation is preserved");
            h.assertValueEqual(f.needles() + f.retained(), 3, "relocated remainder and actual escrow conserve all originals");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_constructor_retirement_cannot_be_overwritten_by_later_staging(GameTestHelper h) {
        for (boolean size : new boolean[]{true, false}) try (Fixture f = new Fixture(h, 1)) {
            AtomicInteger calls = new AtomicInteger(), joins = new AtomicInteger();
            f.listen(event -> joins.incrementAndGet());
            f.construct(size, candidate -> {
                calls.incrementAndGet();
                BorrowedNeedleEscrow.retire(f.p);
                h.assertTrue(f.state() == State.RETURN_PENDING && f.retained() == 1, "explicit constructor retirement marks original pending while transaction remains busy");
                h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "constructor cannot redeem busy pending return");
            });
            h.assertFalse(f.borrow(), "explicit retirement inside construction aborts outer admission");
            h.assertValueEqual(calls.get(), 1, "actual selected constructor event reached");
            h.assertValueEqual(joins.get(), 0, "retired constructor candidate never enters native world");
            h.assertValueEqual(f.payments.get(), 0, "retired constructor never pays");
            assertCanonicalEmpty(h, f, "constructor retirement safely returns original after leaving busy phase");
            h.assertValueEqual(f.needles(), 1, "constructor retirement preserves exact original once");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_constructor_throw_before_and_after_mutation_retains_unknown_original(GameTestHelper h) {
        for (boolean size : new boolean[]{true, false}) for (boolean mutate : new boolean[]{false, true}) try (Fixture f = new Fixture(h, 1)) {
            AtomicInteger calls = new AtomicInteger(), joins = new AtomicInteger();
            f.listen(event -> joins.incrementAndGet());
            f.construct(size, candidate -> {
                calls.incrementAndGet();
                if (mutate) f.p.getInventory().setItem(2, new ItemStack(Items.DIAMOND, 3));
                throw new IllegalStateException("Declared native constructor uncertainty afterMutation=" + mutate);
            });
            h.assertFalse(f.borrow(), "throwing actual native constructor cannot admit");
            h.assertValueEqual(calls.get(), 1, "actual native constructor callback threw once");
            h.assertTrue(joins.get() == 0 && f.payments.get() == 0, "constructor throw reaches neither world publication nor payment");
            h.assertTrue(f.state() == State.QUARANTINED && f.retained() == 1 && f.needles() == 0, "one unknown original remains quarantined");
            h.assertValueEqual(count(f.p, Items.DIAMOND), mutate ? 3 : 0, "prethrow callback effects are never snapshot-rolled-back");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p) || f.borrow(), "unknown native constructor cannot automatically refund or retry");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_removal_callback_reentrant_retirement_keeps_canonical_settled_empty(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.borrow(), "one original admitted before native leave callback"); var projection = f.last;
            AtomicInteger leaves = new AtomicInteger();
            f.hook(new Object() { @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
                if (event.getEntity() != projection) return;
                leaves.incrementAndGet();
                h.assertFalse(projection.isLeaseActive(), "native removal callback sees revoked damage token");
                BorrowedNeedleEscrow.retire(f.p);
            } });
            BorrowedNeedleEscrow.retire(f.p);
            h.assertValueEqual(leaves.get(), 1, "actual native EntityLeaveLevelEvent observed once");
            assertCanonicalEmpty(h, f, "outer retirement cannot overwrite callback's completed return");
            h.assertValueEqual(f.needles(), 1, "reentrant native retirement returns original exactly once");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "reentrant completed return cannot be claimed again");
            h.assertValueEqual(f.payments.get(), 1, "native leave reentry never pays");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_quarantine_removal_callback_cannot_claim_unknown_escrow(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.borrow(), "actual original admitted before unknown native persistence mutation"); var projection = f.last;
            AtomicInteger leaves = new AtomicInteger();
            f.hook(new Object() { @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
                if (event.getEntity() != projection) return;
                leaves.incrementAndGet();
                h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "quarantine is latched before native removal callback can claim");
                BorrowedNeedleEscrow.retire(f.p);
            } });
            CompoundTag raw = f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY);
            raw.putString("unexpected_callback", "declared unknown source"); CompoundTag exact = raw.copy();
            h.assertTrue(f.state() == State.QUARANTINED, "actual unexpected raw mutation quarantines live custody");
            h.assertValueEqual(leaves.get(), 1, "quarantine removed actual native projection once");
            h.assertTrue(f.retained() == 1 && f.needles() == 0, "native leave callback cannot mint uncertain original");
            h.assertTrue(f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY).equals(exact), "unknown source is preserved through reentrant removal");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p) || f.borrow(), "unknown removal never grants retry or publication");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_leave_callback_raw_mutation_remains_verbatim_quarantined_without_grant(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.borrow(), "one original active before callback mutates raw custody"); var projection = f.last;
            AtomicInteger leaves = new AtomicInteger(); CompoundTag[] observed = new CompoundTag[1];
            f.hook(new Object() { @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
                if (event.getEntity() != projection) return;
                leaves.incrementAndGet();
                CompoundTag raw = f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY);
                raw.putString("unknown_native_leave", "declared callback mutation"); observed[0] = raw.copy();
            } });
            BorrowedNeedleEscrow.retire(f.p);
            h.assertValueEqual(leaves.get(), 1, "actual native leave callback changed persistent source once");
            h.assertTrue(observed[0] != null && f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY).equals(observed[0]),
                    "outer retirement cannot overwrite foreign raw mutation made during actual removal");
            h.assertTrue(f.state() == State.QUARANTINED && f.retained() == 1 && f.needles() == 0,
                    "post-removal unknown source retains original without granting inventory");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p) || f.borrow(), "unknown leave callback source never silently repairs or retries");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_spectator_transition_immediately_invalidates_live_borrowed_lease(GameTestHelper h) {
        Fixture f = new Fixture(h, 1);
        f.nativeReady(() -> {
            boolean admitted = f.borrow();
            h.assertTrue(admitted, "survival owner has an exact active lease; " + f.diagnostics()); var projection = f.last;
            f.p.setGameMode(GameType.SPECTATOR);
            h.assertFalse(projection.isLeaseActive(), "actual spectator mode switch immediately invalidates live token");
            projection.tick();
            h.assertTrue(projection.isRemoved() && f.state() == State.RETURN_PENDING && f.retained() == 1,
                    "native projection tick retires spectator token and preserves actual original");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "spectator cannot claim original");
            f.p.setGameMode(GameType.SURVIVAL);
            h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "same valid owner explicitly recovers retired original");
            assertCanonicalEmpty(h, f, "mode restoration grants no resurrected damage lease");
            h.assertValueEqual(f.needles(), 1, "one original across actual game mode changes");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void different_real_native_connection_cannot_inherit_existing_session_lease(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.borrow(), "exact original has a lease for initial native connection"); var projection = f.last;
            ServerPlayer other = f.actors.player(); var originalConnection = f.p.connection;
            try {
                // Declared connection replacement uses a second genuinely admitted
                // native listener. It is not a null/disconnected mock or token edit.
                f.p.connection = other.connection;
                h.assertFalse(projection.isLeaseActive(), "different native listener cannot inherit original connection's damage lease");
                projection.tick();
                h.assertFalse(projection.isLeaseActive(), "observed connection replacement permanently retires original token");
            } finally { f.p.connection = originalConnection; }
            h.assertFalse(projection.isLeaseActive(), "restoring pointer cannot resurrect a retired session lease");
            h.assertValueEqual(f.needles() + f.retained(), 1, "connection replacement never creates or loses physical original");
            h.assertValueEqual(f.payments.get(), 1, "connection replacement never pays again");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_constructor_death_preserves_committed_custody_without_later_publication(GameTestHelper h) {
        List<Fixture> fixtures = List.of(new Fixture(h, 1), new Fixture(h, 1), new Fixture(h, 1), new Fixture(h, 1));
        fixtures.getFirst().ready(() -> {
            for (int index = 0; index < fixtures.size(); index++) {
                Fixture f = fixtures.get(index); boolean keep = index % 2 == 0, size = index < 2;
                boolean oldRule = h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
                try {
                    h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(keep, h.getLevel().getServer());
                    AtomicInteger constructors = new AtomicInteger(), joins = new AtomicInteger();
                    f.listen(event -> joins.incrementAndGet());
                    f.hook(new Object() { @SubscribeEvent public void drops(LivingDropsEvent event) {
                        if (event.getEntity() == f.p) f.items.addAll(event.getDrops());
                    } });
                    f.construct(size, candidate -> {
                        constructors.incrementAndGet();
                        h.assertTrue(f.state() == State.ESCROWED && f.retained() == 1, "actual native constructor sees pre-staging original escrow");
                        h.assertTrue(f.p.hurt(f.p.damageSources().generic(), Float.MAX_VALUE), "constructor callback triggers genuine ordinary native lethal damage");
                        h.assertTrue(f.state() == (keep ? State.RETURN_PENDING : State.EMPTY), "native death settles custody inside actual constructor callback");
                    });
                    h.assertFalse(f.borrow(), "constructor death aborts native projection admission");
                    h.assertValueEqual(constructors.get(), 1, "selected actual constructor callback reached");
                    h.assertFalse(f.p.isAlive(), "constructor owner really died through native damage");
                    h.assertValueEqual(joins.get(), 0, "dead constructor owner cannot publish candidate after settlement");
                    h.assertValueEqual(f.payments.get(), 0, "constructor death cannot reach payment");
                    h.assertTrue(f.state() == (keep ? State.RETURN_PENDING : State.EMPTY), "outer constructor continuation cannot overwrite committed death custody");
                    h.assertValueEqual(f.retained(), keep ? 1 : 0, "kept escrow and native death transfer remain exclusive");
                    // Every fixture has the same declared player position. Count this
                    // owner's witnessed capture, then remove it before the next death.
                    int legit = f.items.stream().filter(e -> !e.isRemoved() && h.getLevel().getEntity(e.getUUID()) == e
                            && e.getItem().is(AcademyCraft.NEEDLE.get())).mapToInt(e -> e.getItem().getCount()).sum();
                    h.assertValueEqual(f.needles() + f.retained() + legit, 1, "actual native constructor death conserves this owner's exact original");
                    f.items.forEach(Entity::discard); f.clearListeners();
                } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(oldRule, h.getLevel().getServer()); }
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void invalid_stock_and_owner_are_refused_without_payment_or_reservation(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 2)) {
            f.p.getOffhandItem().set(DataComponents.CUSTOM_NAME, Component.literal("Declared decorated original"));
            h.assertFalse(f.borrow(), "decorated academy needle cannot enter plain escrow");
            h.assertValueEqual(f.needles(), 2, "decorated originals remain untouched");
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.ARROW, 2));
            h.assertFalse(f.borrow(), "vanilla arrow cannot substitute for original needle");
            f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), 2));
            f.p.setGameMode(GameType.SPECTATOR);
            h.assertFalse(f.borrow(), "spectator cannot reserve escrow");
            f.p.setGameMode(GameType.SURVIVAL);
            h.assertTrue(BorrowedNeedleEscrow.borrow(f.p, f.position(), () -> false, f::pay).isEmpty(), "failed admission guard cannot reserve escrow");
            h.assertValueEqual(f.needles(), 2, "all refused attempts leave actual stock");
            h.assertValueEqual(f.retained(), 0, "refused preflight never creates escrow");
            h.assertValueEqual(f.payments.get(), 0, "refused preflight never pays");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void unloaded_nonfinite_and_outside_world_destinations_refuse_without_reservation(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            Vec3 source = f.position();
            Vec3 unloaded = source.add(32_000, 0, 32_000);
            ChunkPos remote = new ChunkPos(BlockPos.containing(unloaded));
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(remote.x, remote.z) == null, "declared distant transaction chunk is genuinely unloaded");
            Vec3[] refused = { unloaded, new Vec3(Double.NaN, source.y, source.z), new Vec3(source.x, Double.POSITIVE_INFINITY, source.z),
                    new Vec3(h.getLevel().getWorldBorder().getMaxX() + 2, source.y, source.z),
                    new Vec3(source.x, h.getLevel().getMinBuildHeight() - 1, source.z),
                    new Vec3(source.x, h.getLevel().getMaxBuildHeight() + 1, source.z) };
            CompoundTag original = f.p.getPersistentData().copy(); AtomicInteger guards = new AtomicInteger(), joins = new AtomicInteger();
            f.listen(event -> joins.incrementAndGet());
            for (Vec3 point : refused) {
                h.assertTrue(BorrowedNeedleEscrow.borrow(f.p, point, () -> { guards.incrementAndGet(); return true; }, f::pay).isEmpty(),
                        "invalid or unloaded destination refuses before actual reservation: " + point);
                h.assertTrue(f.p.getPersistentData().equals(original), "rejected destination cannot burn nonce or rewrite player custody");
                h.assertTrue(f.needles() == 1 && f.retained() == 0, "rejected destination preserves sole actual offhand original");
            }
            h.assertValueEqual(guards.get(), 0, "destination preflight invokes no external guard");
            h.assertTrue(joins.get() == 0 && f.payments.get() == 0, "destination preflight reaches neither native world publication nor payment");
            h.assertTrue(h.getLevel().getChunkSource().getChunkNow(remote.x, remote.z) == null, "refusal never loads the distant native chunk");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_clone_removal_exception_transfers_only_quarantined_original(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.borrow(), "active actual original precedes native owner clone");
            ServerPlayer old = f.p; var projection = f.last; AtomicInteger leaves = new AtomicInteger();
            f.hook(new Object() { @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
                if (event.getEntity() != projection) return;
                leaves.incrementAndGet();
                throw new IllegalStateException("Declared native leave uncertainty during clone handoff");
            } });
            // A same-UUID native replacement is admitted before restoreFrom. Do not
            // logout the old object first: that would retire the projection before
            // the clone's own native removal callback is exercised.
            f.p = f.actors.player(old.getGameProfile());
            f.p.getPersistentData().remove(BorrowedNeedleEscrow.STORAGE_KEY);
            f.p.restoreFrom(old, true);
            h.assertValueEqual(leaves.get(), 1, "clone itself observed one genuine failing native projection leave callback");
            h.assertTrue(f.state() == State.QUARANTINED && f.retained() == 1 && f.needles() == 0,
                    "uncertain native clone removal cannot grant new owner redeemable custody");
            h.assertValueEqual(BorrowedNeedleEscrow.retainedCount(old), 0, "replaced old object cannot retain a second authoritative original");
            h.assertFalse(BorrowedNeedleEscrow.claim(old) || BorrowedNeedleEscrow.claim(f.p) || f.borrow(),
                    "neither old nor new owner may claim or republish uncertain clone original");
            h.assertFalse(projection.isLeaseActive(), "clone removal failure still revokes old damage token");
            h.assertValueEqual(f.payments.get(), 1, "native clone never pays again");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void stale_nonce_removed_object_and_duplicate_uuid_cannot_replace_current_lease(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.borrow(), "first exact original admitted");
            var stale = f.last; long nonce = stale.nonce();
            BorrowedNeedleEscrow.retire(f.p);
            h.assertValueEqual(f.needles(), 1, "first lease returns exact original");
            f.p.setItemInHand(InteractionHand.OFF_HAND, f.p.getInventory().removeItemNoUpdate(firstNeedleSlot(f.p)));
            h.assertTrue(f.borrow(), "returned original may open a distinct new lease");
            var live = f.last;
            h.assertTrue(live.nonce() > nonce && live != stale, "new lease advances nonce and native object");
            stale.tick(); stale.discard();
            var duplicate = new BorrowedNeedleEntity(TeleporterNeedleEntities.BORROWED_NEEDLE.get(), h.getLevel());
            duplicate.setUUID(live.getUUID()); duplicate.setPos(live.position()); f.entities.add(duplicate);
            h.assertFalse(h.getLevel().addFreshEntity(duplicate), "native duplicate UUID cannot enter beside current projection");
            duplicate.tick();
            h.assertTrue(live.isLeaseActive() && h.getLevel().getEntity(live.getUUID()) == live,
                    "stale removal and foreign duplicate cannot revoke or replace live token");
            h.assertTrue(f.state() == State.ACTIVE && f.retained() == 1 && f.needles() == 0, "current sole original remains in escrow");
            h.assertValueEqual(f.payments.get(), 2, "only two genuine admissions pay");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_player_save_load_stable_states_never_restores_damage_authority(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 1)) {
            if (mode == 1 || mode == 2) { fillInventory(f.p); h.assertTrue(f.borrow(), "actual original establishes saved custody"); }
            if (mode == 2) BorrowedNeedleEscrow.retire(f.p);
            if (mode == 3) {
                f.listen(event -> { throw new IllegalStateException("Declared native uncertainty for saved quarantine"); });
                h.assertFalse(f.borrow(), "native uncertainty establishes real quarantined original"); f.clearListeners();
            }
            State source = mode == 0 ? State.EMPTY : mode == 1 ? State.ACTIVE : mode == 2 ? State.RETURN_PENDING : State.QUARANTINED;
            h.assertTrue(f.state() == source, "declared stable source state exists before native save");
            CompoundTag saved = f.p.saveWithoutId(new CompoundTag());
            int originals = f.needles() + f.retained();
            f.reload(saved);
            h.assertTrue(f.state() == (source == State.ACTIVE ? State.RETURN_PENDING : source), "native player codec restores custody without restoring live projection");
            h.assertValueEqual(f.needles() + f.retained(), originals, "native Inventory and NeoForgeData codecs conserve original");
            h.assertTrue(f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive), "old live object cannot regain damage permission after load");
            if (source == State.ACTIVE || source == State.RETURN_PENDING) {
                f.p.getInventory().setItem(4, ItemStack.EMPTY);
                h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "restored safe original returns by actual insertion");
                h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "restored return is idempotent");
                h.assertTrue(f.needles() == 1 && f.retained() == 0, "restored original is singular after claim");
            } else h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "empty and unknown saved states create no new grant");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void interrupted_native_escrow_records_only_restore_return_or_quarantine(GameTestHelper h) {
        for (State state : new State[]{State.ESCROWED, State.STAGED, State.DEATH_TRANSFER}) try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p); h.assertTrue(f.borrow(), "one actual removed original supplies interrupted-state serialization");
            CompoundTag saved = f.p.saveWithoutId(new CompoundTag());
            // Declared interrupted-record fixture changes only the phase on a genuine
            // serialized escrow. It creates neither a physical item nor native lease.
            saved.getCompound("NeoForgeData").getCompound(BorrowedNeedleEscrow.STORAGE_KEY).putString("state", state.name());
            f.reload(saved);
            h.assertTrue(f.state() == (state == State.DEATH_TRANSFER ? State.QUARANTINED : State.RETURN_PENDING), "only unknown death transfer stays quarantined");
            h.assertValueEqual(f.retained(), 1, "interrupted phase preserves exact serialized original");
            h.assertTrue(f.entities.stream().noneMatch(BorrowedNeedleEntity::isLeaseActive), "no serialized phase restores a live damage token");
            f.p.getInventory().setItem(4, ItemStack.EMPTY);
            h.assertTrue(BorrowedNeedleEscrow.claim(f.p) == (state != State.DEATH_TRANSFER), "only proven non-world original may be claimed");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "neither completed nor unknown transfer is replayable");
            h.assertValueEqual(f.needles() + f.retained(), 1, "interrupted custody keeps exactly one original");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void malformed_player_record_is_preserved_and_cannot_grant_or_autoretry(GameTestHelper h) {
        for (int mode = 0; mode < 4; mode++) try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p); h.assertTrue(f.borrow(), "actual original supplies malformed codec fixture");
            CompoundTag saved = f.p.saveWithoutId(new CompoundTag());
            CompoundTag record = saved.getCompound("NeoForgeData").getCompound(BorrowedNeedleEscrow.STORAGE_KEY);
            if (mode == 0) record.putString("state", "FUTURE_UNRECOGNIZED");
            if (mode == 1) record.putLong("next_nonce", 0);
            if (mode == 2) record.putInt("future_authority", 1);
            if (mode == 3) record.putString("version", "1");
            CompoundTag exact = record.copy(); f.reload(saved);
            h.assertTrue(f.state() == State.QUARANTINED, "malformed player record is explicitly blocked");
            f.p.getInventory().setItem(2, ItemStack.EMPTY);
            BorrowedNeedleEscrow.retire(f.p);
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p) || f.borrow(), "malformed source cannot mint a return or new lease");
            h.assertTrue(f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY).equals(exact), "unknown serialized source is retained verbatim");
            h.assertValueEqual(f.needles(), 0, "invalid record never creates an inventory item");
            h.assertValueEqual(f.payments.get(), 1, "malformed rereads and refused actions never pay again");
        }
        h.succeed();
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_final_entity_unload_retires_lease_and_retains_fullbag_original(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p); h.assertTrue(f.borrow(), "one original active before native final unload"); var live = f.last;
            nativeUnload(h, live);
            h.assertTrue(live.isRemoved() && live.getRemovalReason() == Entity.RemovalReason.UNLOADED_TO_CHUNK, "actual native entity manager final unload path");
            h.assertFalse(live.isLeaseActive(), "native unload immediately revokes borrowed lease");
            h.assertTrue(f.state() == State.RETURN_PENDING && f.retained() == 1, "unloaded projection leaves original escrow returnable");
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "full native inventory retains original after unload");
            f.p.getInventory().setItem(2, ItemStack.EMPTY);
            h.assertTrue(BorrowedNeedleEscrow.claim(f.p) && !BorrowedNeedleEscrow.claim(f.p), "native unload original can return exactly once");
            h.assertValueEqual(f.needles() + f.retained(), 1, "unload never creates world item or duplicate");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void real_logout_reload_and_native_clone_transfer_owner_without_duplicate_claim(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            fillInventory(f.p); h.assertTrue(f.borrow(), "one real original active before owner handoff");
            ServerPlayer old = f.p; var oldProjection = f.last;
            h.getLevel().getServer().getPlayerList().remove(old);
            h.assertFalse(oldProjection.isLeaseActive(), "actual PlayerList logout revokes old native projection");
            h.assertFalse(BorrowedNeedleEscrow.claim(old), "logged-out object is never a valid item destination");
            f.p = f.actors.player(old.getGameProfile());
            // NativeMenuActors places a reconnecting player, which loads disk data.
            // A real respawn constructs a fresh object before restoreFrom; remove only
            // this fixture's preloaded escrow root to reproduce that native precondition.
            f.p.getPersistentData().remove(BorrowedNeedleEscrow.STORAGE_KEY);
            f.p.restoreFrom(old, true);
            h.assertTrue(f.p.getUUID().equals(old.getUUID()) && f.p != old, "native replacement owns same identity with distinct object");
            h.assertTrue(f.state() == State.RETURN_PENDING && f.retained() == 1, "actual native restoreFrom clone event carries one original");
            h.assertValueEqual(BorrowedNeedleEscrow.retainedCount(old), 0, "old object loses escrow authority after transfer");
            h.assertFalse(BorrowedNeedleEscrow.claim(old), "stale owner cannot claim clone original");
            f.p.getInventory().setItem(5, ItemStack.EMPTY);
            h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "current living owner receives original once");
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(f.p, old, false));
            h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "duplicate native clone notification cannot copy consumed escrow");
            h.assertValueEqual(f.needles(), 1, "one authoritative returned original across handoff");
            h.assertValueEqual(f.payments.get(), 1, "lifecycle never invokes another payment");
        }
        h.succeed();
    }

    @GameTest(template=EXPIRY_TEMPLATE, batch=BATCH, timeoutTicks=60)
    public static void genuine_native_lease_expiry_leaves_fullbag_claim_pending_without_autoretry(GameTestHelper h) {
        Fixture f = new Fixture(h, 1); fillInventory(f.p);
        Vec3 origin = h.absoluteVec(Vec3.ZERO);
        double boundaryZ = (Math.floor(origin.z / 16) + 1) * 16;
        f.p.moveTo(origin.x + 2.5, origin.y + 1, boundaryZ - .5, 0, 0);
        f.p.connection.resetPosition();
        f.sourceOverride = new Vec3(origin.x + 5.5, origin.y + 3.5, boundaryZ + .5);
        h.assertTrue(new ChunkPos(f.p.blockPosition()).z + 1 == new ChunkPos(BlockPos.containing(f.position())).z,
                "declared owner and projection straddle a genuine native chunk boundary on every suite origin");
        h.assertTrue(h.getBounds().contains(f.p.position()) && h.getBounds().contains(f.position()),
                "native GameTest pre-execution readiness bounds include both sides of the reproduced r4 boundary");
        f.nativeReady(() -> {
            boolean admitted = f.borrow();
            h.assertTrue(admitted, "one original active in genuinely entity-ticking chunk; " + f.diagnostics()); var projection = f.last;
            AtomicInteger actualTicks = new AtomicInteger(); boolean[] activeAt39 = {false}; int[] actualAge39 = {-1};
            f.hook(new Object() { @SubscribeEvent public void tick(EntityTickEvent.Post event) {
                if (event.getEntity() != projection) return;
                int count = actualTicks.incrementAndGet();
                if (count == BorrowedNeedleEntity.MAX_LEASE_TICKS - 1) {
                    activeAt39[0] = projection.isLeaseActive(); actualAge39[0] = nativeLeaseTicks(projection);
                }
            } });
            h.assertValueEqual(nativeLeaseTicks(projection), 0, "actual native lease counter starts at zero");
            h.runAfterDelay(BorrowedNeedleEntity.MAX_LEASE_TICKS, () -> {
                String evidence = "nativePostTicks=" + actualTicks.get() + ",leaseTicks=" + nativeLeaseTicks(projection)
                        + ",entityTickCount=" + projection.tickCount + "," + f.diagnostics();
                LOGGER.info("Borrowed needle actual expiry evidence: {}", evidence);
                h.assertValueEqual(actualTicks.get(), BorrowedNeedleEntity.MAX_LEASE_TICKS, "forty genuine ServerLevel entity ticks were observed; " + evidence);
                h.assertTrue(activeAt39[0] && actualAge39[0] == BorrowedNeedleEntity.MAX_LEASE_TICKS - 1,
                        "same exact lease remains active through real native tick39");
                h.assertValueEqual(nativeLeaseTicks(projection), BorrowedNeedleEntity.MAX_LEASE_TICKS, "unchanged production lease counter reaches exactly40");
                h.assertFalse(projection.isLeaseActive(), "fortieth actual native entity tick expires projection");
                h.assertTrue(projection.isRemoved() && f.state() == State.RETURN_PENDING && f.retained() == 1,
                        "native expiry revokes projection and leaves fullbag original pending");
                f.p.getInventory().setItem(6, ItemStack.EMPTY);
                h.runAfterDelay(3, () -> {
                    h.assertTrue(f.retained() == 1 && f.needles() == 0, "opening inventory capacity does not secretly retry pending claim");
                    h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "explicit actual claim returns expired lease original");
                    h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "native expiry claim is idempotent");
                    h.assertTrue(f.needles() == 1 && f.retained() == 0, "one exact original after expiry");
                    h.assertValueEqual(f.payments.get(), 1, "expiry never pays or republishes");
                    h.succeed();
                });
            });
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_lethal_join_callback_settles_death_custody_before_payment(GameTestHelper h) {
        Fixture kept = new Fixture(h, 1), dropped = new Fixture(h, 1);
        kept.ready(() -> {
            for (Fixture f : List.of(kept, dropped)) {
                boolean keep = f == kept; boolean oldRule = h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
                try {
                    h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(keep, h.getLevel().getServer());
                    f.hook(new Object() { @SubscribeEvent public void drops(LivingDropsEvent event) {
                        if (event.getEntity() == f.p) f.items.addAll(event.getDrops());
                    } });
                    f.listen(event -> {
                        h.assertTrue(f.state() == State.STAGED && f.payments.get() == 0, "native death starts while sole original is staged and unpaid");
                        h.assertTrue(f.p.hurt(f.p.damageSources().generic(), Float.MAX_VALUE), "genuine resident owner receives native lethal callback damage");
                    });
                    h.assertFalse(f.borrow(), "native callback death cannot admit a new damage lease");
                    h.assertFalse(f.p.isAlive() || f.joined.isLeaseActive(), "actual callback death revokes candidate");
                    h.assertValueEqual(f.payments.get(), 0, "native death settlement precedes any payment");
                    h.assertTrue(f.state() == (keep ? State.RETURN_PENDING : State.EMPTY), "borrow unwind preserves already-settled native death state");
                    h.assertValueEqual(f.needles() + f.retained() + f.droppedNeedles(), 1, "callback death conserves sole original through native rule");
                    h.assertValueEqual(f.droppedNeedles(), keep ? 0 : 1, "native callback death drop follows actual keepInventory rule");
                } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(oldRule, h.getLevel().getServer()); }
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void ordinary_native_death_logout_and_clone_never_create_borrowed_state(GameTestHelper h) {
        Fixture f = new Fixture(h, 0);
        // Deliberately never call borrow, view, retainedCount or any escrow API.
        h.assertFalse(f.p.getPersistentData().contains(BorrowedNeedleEscrow.STORAGE_KEY), "ordinary newly admitted player has no borrowed custody root");
        f.ready(() -> {
            h.assertTrue(f.p.hurt(f.p.damageSources().generic(), Float.MAX_VALUE), "ordinary player reaches genuine native lethal damage");
            h.assertFalse(f.p.isAlive(), "ordinary native death was committed");
            h.assertFalse(f.p.getPersistentData().contains(BorrowedNeedleEscrow.STORAGE_KEY), "native death hooks do not add state for never-borrowed player");
            ServerPlayer old = f.p;
            old.server.getPlayerList().remove(old);
            h.assertFalse(old.getPersistentData().contains(BorrowedNeedleEscrow.STORAGE_KEY), "ordinary native logout does not create borrowed state");
            f.p = f.actors.player(old.getGameProfile());
            f.p.restoreFrom(old, false);
            h.assertTrue(f.p.isAlive(), "ordinary native respawn clone is living");
            h.assertFalse(old.getPersistentData().contains(BorrowedNeedleEscrow.STORAGE_KEY)
                    || f.p.getPersistentData().contains(BorrowedNeedleEscrow.STORAGE_KEY), "ordinary clone leaves both players without escrow records");
            f.p.server.getPlayerList().remove(f.p);
            h.assertFalse(f.p.getPersistentData().contains(BorrowedNeedleEscrow.STORAGE_KEY), "ordinary replacement logout remains inert");
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=20)
    public static void native_leave_error_latches_quarantine_before_propagating_exact_error(GameTestHelper h) {
        try (Fixture f = new Fixture(h, 1)) {
            h.assertTrue(f.borrow(), "actual original active before native leave Error"); var projection = f.last;
            AssertionError declared = new AssertionError("Declared native borrowed custody leave Error"); AtomicInteger leaves = new AtomicInteger();
            f.hook(new Object() { @SubscribeEvent public void leave(EntityLeaveLevelEvent event) {
                if (event.getEntity() == projection) { leaves.incrementAndGet(); throw declared; }
            } });
            boolean propagated = false;
            try { BorrowedNeedleEscrow.retire(f.p); }
            catch (AssertionError actual) { if (actual != declared) throw actual; propagated = true; }
            h.assertTrue(propagated, "same actual native callback Error propagates rather than being swallowed");
            h.assertValueEqual(leaves.get(), 1, "actual native leave callback threw once");
            h.assertTrue(f.state() == State.QUARANTINED && f.retained() == 1 && f.needles() == 0,
                    "Error is quarantined before propagation and never returns uncertain original");
            h.assertFalse(projection.isLeaseActive() || BorrowedNeedleEscrow.claim(f.p) || f.borrow(), "propagated native Error grants no lease or retry");
            h.assertValueEqual(f.payments.get(), 1, "native Error cannot replay original payment");
            h.succeed();
        }
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_death_keep_inventory_and_real_capture_drop_conserve_original(GameTestHelper h) {
        Fixture kept = new Fixture(h, 1), dropped = new Fixture(h, 1);
        kept.ready(() -> {
            for (Fixture f : List.of(kept, dropped)) {
                boolean keep = f == kept; boolean oldRule = h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
                try {
                    h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(keep, h.getLevel().getServer());
                    h.assertTrue(f.borrow(), "living admitted owner borrows before native lethal hit"); var projection = f.last;
                    AtomicInteger drops = new AtomicInteger();
                    f.hook(new Object() { @SubscribeEvent(priority=EventPriority.LOWEST) public void drops(LivingDropsEvent event) {
                        if (event.getEntity() != f.p) return;
                        h.assertFalse(projection.isLeaseActive(), "damage lease revoked before native captured drops observers");
                        drops.set(event.getDrops().stream().filter(e -> e.getItem().is(AcademyCraft.NEEDLE.get())).mapToInt(e -> e.getItem().getCount()).sum());
                        f.items.addAll(event.getDrops());
                    } });
                    h.assertTrue(f.p.hurt(f.p.damageSources().generic(), Float.MAX_VALUE), "real ordinary lethal native damage admitted after login protection expires");
                    h.assertFalse(f.p.isAlive() || projection.isLeaseActive(), "confirmed native death has no live borrowed lease");
                    h.assertValueEqual(drops.get(), keep ? 0 : 1, "native capture collection receives exactly rule-allowed borrowed original");
                    h.assertValueEqual(f.retained(), keep ? 1 : 0, "keepInventory retains escrow; normal death transfers it once");
                    h.assertValueEqual(f.needles() + f.retained() + f.droppedNeedles(), 1, "inventory plus escrow plus legitimate native death drop conserves one original");
                    h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "dead owner is not a return destination");
                    if (keep) {
                        ServerPlayer dead = f.p;
                        dead.server.getPlayerList().remove(dead);
                        f.p = f.actors.player(dead.getGameProfile());
                        // Match the fresh native respawn object's pre-clone data condition.
                        f.p.getPersistentData().remove(BorrowedNeedleEscrow.STORAGE_KEY);
                        f.p.restoreFrom(dead, false);
                        h.assertTrue(f.p.isAlive() && f.state() == State.RETURN_PENDING, "actual confirmed death clone retains one pending original with keepInventory");
                        h.assertValueEqual(BorrowedNeedleEscrow.retainedCount(dead), 0, "confirmed dead original loses return authority");
                        h.assertTrue(BorrowedNeedleEscrow.claim(f.p), "living death clone actually receives kept original");
                        h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "kept death clone cannot repeat claim");
                    } else BorrowedNeedleEscrow.deathDrop(f.p);
                    h.assertValueEqual(f.needles() + f.retained() + f.droppedNeedles(), 1, "clone or repeated death callback cannot recreate original");
                } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(oldRule, h.getLevel().getServer()); }
            }
            h.succeed();
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_cancelled_drop_consumes_capture_once_and_never_refunds(GameTestHelper h) {
        Fixture f = new Fixture(h, 1);
        f.ready(() -> {
            boolean oldRule = h.getLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY);
            try {
                h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(false, h.getLevel().getServer());
                h.assertTrue(f.borrow(), "actual original borrowed before death-drop veto"); AtomicInteger captured = new AtomicInteger();
                f.hook(new Object() { @SubscribeEvent(priority=EventPriority.LOWEST) public void drops(LivingDropsEvent event) {
                    if (event.getEntity() != f.p) return;
                    captured.set(event.getDrops().stream().filter(e -> e.getItem().is(AcademyCraft.NEEDLE.get())).mapToInt(e -> e.getItem().getCount()).sum());
                    f.items.addAll(event.getDrops()); event.setCanceled(true);
                } });
                h.assertTrue(f.p.hurt(f.p.damageSources().generic(), Float.MAX_VALUE), "actual native lethal hit reaches captured-drop veto");
                h.assertValueEqual(captured.get(), 1, "original was really transferred into native death capture");
                h.assertTrue(f.retained() == 0 && f.needles() == 0 && f.droppedNeedles() == 0, "native cancellation owns its captured original and publishes no world item");
                BorrowedNeedleEscrow.deathDrop(f.p); BorrowedNeedleEscrow.retire(f.p);
                h.assertFalse(BorrowedNeedleEscrow.claim(f.p), "cancelled downstream drop cannot authorize a fabricated refund");
                h.assertValueEqual(f.payments.get(), 1, "drop veto creates no second payment");
                h.succeed();
            } finally { h.getLevel().getGameRules().getRule(GameRules.RULE_KEEPINVENTORY).set(oldRule, h.getLevel().getServer()); }
        });
    }

    @GameTest(template=TEMPLATE, batch=BATCH, timeoutTicks=100)
    public static void native_death_veto_and_totem_never_enter_death_item_transfer(GameTestHelper h) {
        Fixture vetoed = new Fixture(h, 1), rescued = new Fixture(h, 1);
        vetoed.ready(() -> {
            for (Fixture f : List.of(vetoed, rescued)) {
                h.assertTrue(f.borrow(), "actual original borrowed by living protected owner");
                AtomicInteger deaths = new AtomicInteger(), drops = new AtomicInteger();
                f.hook(new Object() {
                    @SubscribeEvent(priority=EventPriority.HIGHEST) public void death(LivingDeathEvent event) {
                        if (event.getEntity() == f.p) { deaths.incrementAndGet(); if (f == vetoed) event.setCanceled(true); }
                    }
                    @SubscribeEvent public void drops(LivingDropsEvent event) { if (event.getEntity() == f.p) drops.incrementAndGet(); }
                });
                if (f == rescued) f.p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.TOTEM_OF_UNDYING));
                h.assertTrue(f.p.hurt(f.p.damageSources().generic(), Float.MAX_VALUE), "ordinary native lethal damage enters configured rescue");
                h.assertValueEqual(drops.get(), 0, "vetoed death and totem never reach native dropEquipment transfer");
                h.assertValueEqual(f.retained(), 1, "exact original remains in escrow through native rescue");
                h.assertValueEqual(f.droppedNeedles(), 0, "rescue creates no world item");
                if (f == rescued) h.assertTrue(f.p.isAlive() && f.p.getOffhandItem().isEmpty() && deaths.get() == 0, "native totem consumed exactly once before death event");
                else h.assertValueEqual(deaths.get(), 1, "actual native death event reaches cancellation");
                h.assertValueEqual(f.payments.get(), 1, "native rescue never pays a second time");
            }
            h.succeed();
        });
    }

    private static void assertCanonicalEmpty(GameTestHelper h, Fixture f, String message) {
        var view = BorrowedNeedleEscrow.view(f.p);
        CompoundTag root = f.p.getPersistentData().getCompound(BorrowedNeedleEscrow.STORAGE_KEY);
        h.assertTrue(view.state() == State.EMPTY && view.nonce() == 0 && view.retainedCount() == 0
                && root.getString("state").equals("EMPTY") && root.getLong("nonce") == 0
                && root.getCompound("stock").isEmpty(), message);
    }
    private static void fillInventory(ServerPlayer owner) {
        for (int slot = 0; slot < 36; slot++) owner.getInventory().setItem(slot, new ItemStack(Items.DIRT, 64));
    }
    private static int count(ServerPlayer owner, Item item) {
        int result = 0;
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++) {
            var stack = owner.getInventory().getItem(slot); if (stack.is(item)) result += stack.getCount();
        }
        return result;
    }
    private static int firstNeedleSlot(ServerPlayer owner) {
        for (int slot = 0; slot < owner.getInventory().getContainerSize(); slot++)
            if (owner.getInventory().getItem(slot).is(AcademyCraft.NEEDLE.get())) return slot;
        throw new IllegalStateException("Actual returned original absent");
    }
    @SuppressWarnings("unchecked")
    private static void nativeUnload(GameTestHelper h, BorrowedNeedleEntity medium) {
        try {
            var field = ServerLevel.class.getDeclaredField("entityManager"); field.setAccessible(true);
            var manager = (PersistentEntitySectionManager<Entity>)field.get(h.getLevel());
            var method = PersistentEntitySectionManager.class.getDeclaredMethod("unloadEntity", EntityAccess.class);
            method.setAccessible(true); method.invoke(manager, medium);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native final unload unavailable", failure); }
    }
    private static int nativeLeaseTicks(BorrowedNeedleEntity projection) {
        try {
            var field = BorrowedNeedleEntity.class.getDeclaredField("leaseTicks"); field.setAccessible(true);
            return field.getInt(projection);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native lease clock witness unavailable", failure); }
    }
    private static String readiness(ServerLevel level, Vec3 point) {
        BlockPos pos = BlockPos.containing(point);
        return "position=" + point + ",chunk=" + new ChunkPos(pos) + ",entitiesLoaded=" + level.areEntitiesLoaded(ChunkPos.asLong(pos))
                + ",entityTicking=" + level.isPositionEntityTicking(pos);
    }
    private static int spawnProtection(ServerPlayer owner) {
        try {
            var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime"); field.setAccessible(true);
            return field.getInt(owner);
        } catch (ReflectiveOperationException failure) { throw new IllegalStateException("Pinned native login protection witness unavailable", failure); }
    }
    public static final class JoinProbe {
        private final UUID owner; private final Consumer<EntityJoinLevelEvent> callback;
        JoinProbe(UUID owner, Consumer<EntityJoinLevelEvent> callback) { this.owner = owner; this.callback = callback; }
        @SubscribeEvent public void join(EntityJoinLevelEvent event) {
            if (event.getEntity() instanceof BorrowedNeedleEntity medium && owner.equals(medium.ownerId())) callback.accept(event);
        }
    }
    public static final class ConstructorProbe {
        private final boolean size; private final BooleanSupplier active; private final Consumer<BorrowedNeedleEntity> callback;
        ConstructorProbe(boolean size, BooleanSupplier active, Consumer<BorrowedNeedleEntity> callback) {
            this.size = size; this.active = active; this.callback = callback;
        }
        private void inspect(Entity entity) {
            if (active.getAsBoolean() && entity instanceof BorrowedNeedleEntity candidate
                    && candidate.getType() == TeleporterNeedleEntities.BORROWED_NEEDLE.get()) callback.accept(candidate);
        }
        @SubscribeEvent public void size(EntityEvent.Size event) { if (size) inspect(event.getEntity()); }
        @SubscribeEvent public void constructing(EntityEvent.EntityConstructing event) { if (!size) inspect(event.getEntity()); }
    }
    private static final class Fixture implements AutoCloseable {
        final GameTestHelper h; final AcademyWirelessDeviceRuntimeTests.NativeMenuActors actors;
        final AtomicInteger payments = new AtomicInteger(); final List<Object> probes = new ArrayList<>();
        final List<BorrowedNeedleEntity> entities = new ArrayList<>(); final List<ItemEntity> items = new ArrayList<>();
        final UUID ticket = UUID.randomUUID(); final Set<ChunkPos> held = new LinkedHashSet<>();
        ServerPlayer p; BorrowedNeedleEntity last, joined; Vec3 sourceOverride; final int started; int borrowDepth; boolean closed;
        Fixture(GameTestHelper h, int stock) {
            this.h = h; actors = new AcademyWirelessDeviceRuntimeTests.NativeMenuActors(h); p = actors.player();
            p.getInventory().clearContent(); p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(AcademyCraft.NEEDLE.get(), stock));
            p.setInvulnerable(false); started = p.server.getTickCount();
            h.testInfo.addListener(new GameTestListener() {
                public void testStructureLoaded(GameTestInfo info) { }
                public void testPassed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testFailed(GameTestInfo info, GameTestRunner runner) { close(); }
                public void testAddedForRerun(GameTestInfo info, GameTestInfo next, GameTestRunner runner) { close(); }
            });
            listen(event -> { });
        }
        Vec3 position() { return sourceOverride == null ? h.absoluteVec(new Vec3(5.5, 3.5, 5.5)) : sourceOverride; }
        State state() { return BorrowedNeedleEscrow.view(p).state(); }
        int retained() { return BorrowedNeedleEscrow.retainedCount(p); }
        int needles() { return count(p, AcademyCraft.NEEDLE.get()); }
        int droppedNeedles() {
            return h.getLevel().getEntitiesOfClass(ItemEntity.class, p.getBoundingBox().inflate(3), e -> !e.isRemoved() && e.getItem().is(AcademyCraft.NEEDLE.get()))
                    .stream().mapToInt(e -> e.getItem().getCount()).sum();
        }
        boolean pay() { payments.incrementAndGet(); return true; }
        boolean borrow() { return borrow(this::pay); }
        boolean borrow(BooleanSupplier payment) {
            borrowDepth++;
            try {
                var result = BorrowedNeedleEscrow.borrow(p, position(), () -> true, payment);
                if (result.isPresent()) { last = result.get(); if (!entities.contains(last)) entities.add(last); }
                return result.isPresent();
            } finally { borrowDepth--; }
        }
        void construct(boolean size, Consumer<BorrowedNeedleEntity> callback) {
            hook(new ConstructorProbe(size, () -> borrowDepth > 0, candidate -> {
                if (!entities.contains(candidate)) entities.add(candidate); callback.accept(candidate);
            }));
        }
        void hook(Object probe) { probes.add(probe); NeoForge.EVENT_BUS.register(probe); }
        void listen(Consumer<EntityJoinLevelEvent> callback) {
            hook(new JoinProbe(p.getUUID(), event -> {
                joined = (BorrowedNeedleEntity)event.getEntity(); if (!entities.contains(joined)) entities.add(joined); callback.accept(event);
            }));
        }
        void clearListeners() { probes.forEach(NeoForge.EVENT_BUS::unregister); probes.clear(); }
        void reload(CompoundTag nativeSave) {
            clearListeners(); ServerPlayer old = p;
            if (old.server.getPlayerList().getPlayer(old.getUUID()) == old) old.server.getPlayerList().remove(old);
            p = actors.player(old.getGameProfile()); p.load(nativeSave.copy());
            h.assertTrue(p.server.getPlayerList().getPlayer(p.getUUID()) == p, "restored exact UUID belongs to current native PlayerList object");
            listen(event -> { });
        }
        String diagnostics() {
            var view = BorrowedNeedleEscrow.view(p);
            return "state=" + view.state() + ",reason=" + view.reason() + ",retained=" + view.retainedCount()
                    + ",inventory=" + needles() + ",ownerCurrent=" + (p.server.getPlayerList().getPlayer(p.getUUID()) == p)
                    + ",ownerAlive=" + p.isAlive() + ",ownerSpectator=" + p.isSpectator() + ",source=" + readiness(h.getLevel(), position());
        }
        void nativeReady(Runnable action) {
            var chunks = h.getLevel().getChunkSource();
            for (Vec3 point : List.of(p.position(), position())) {
                ChunkPos chunk = new ChunkPos(BlockPos.containing(point));
                if (held.add(chunk)) { chunks.addRegionTicket(TICKET, chunk, 2, ticket); h.getLevel().getChunk(chunk.x, chunk.z); }
            }
            LOGGER.info("Borrowed needle native fixture preparation: owner={}, source={}", readiness(h.getLevel(), p.position()), readiness(h.getLevel(), position()));
            h.startSequence().thenWaitUntil(() -> {
                for (Vec3 point : List.of(p.position(), position())) {
                    BlockPos pos = BlockPos.containing(point);
                    h.assertTrue(h.getLevel().areEntitiesLoaded(ChunkPos.asLong(pos)) && h.getLevel().isPositionEntityTicking(pos),
                            "exact native transaction chunk must become entity-ready; " + readiness(h.getLevel(), point));
                }
            }).thenExecute(() -> {
                LOGGER.info("Borrowed needle native fixture ready: {}", diagnostics());
                action.run();
            });
        }
        void ready(Runnable action) {
            h.runAfterDelay(61, () -> {
                h.assertTrue(p.server.getTickCount() - started >= 61 && spawnProtection(p) <= 0,
                        "sixty-one genuine resident server ticks expire native login protection without modifying it");
                action.run();
            });
        }
        @Override public void close() {
            if (closed) return; closed = true; clearListeners();
            for (var entity : entities) if (!entity.isRemoved()) entity.discard();
            for (var item : items) if (!item.isRemoved()) item.discard();
            actors.close();
            for (ChunkPos chunk : held) h.getLevel().getChunkSource().removeRegionTicket(TICKET, chunk, 2, ticket);
        }
    }
}
