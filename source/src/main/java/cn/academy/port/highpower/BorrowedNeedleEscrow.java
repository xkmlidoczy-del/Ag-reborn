package cn.academy.port.highpower;

import cn.academy.port.AcademyCraft;
import cn.academy.port.highpower.mixin.NativeBorrowedNeedleMixinPlugin;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * NEW351 physical custody. A volley holds six original needles for reusable session leases.
 * Inventory and escrow are serialized in the same native player root (NeoForgeData), never
 * world SavedData or PlayerPersisted. The projection only borrows an unpersisted lease.
 * Unknown callback outcomes are retained and quarantined; there is no compensation mint.
 */
public final class BorrowedNeedleEscrow {
    public static final String STORAGE_KEY = "AcademyBorrowedNeedleEscrow";
    private static final int VERSION = 2;
    public static final int VOLLEY_SLOTS = 6, MAX_VOLLEY_SHOTS = 16, MAX_VOLLEY_TICKS = 160;
    private static final Set<String> KEYS = Set.of("version", "owner", "next_nonce", "exhausted", "state", "nonce", "stock", "reason");
    private static final Set<String> V2_KEYS = Set.of("version", "owner", "next_nonce", "exhausted", "state", "nonce", "stock", "reason",
            "mode", "slots", "round", "shot_count", "next_shot", "spent", "next_generation", "generation_exhausted", "deadline");
    private static final Set<String> SLOT_KEYS = Set.of("index", "state", "stock", "reason", "shot_index", "generation");
    public enum State { EMPTY, ESCROWED, BORROWED_IDLE, STAGED, ACTIVE, RETURN_PENDING, DEATH_TRANSFER, QUARANTINED }
    public record View(State state, long nonce, int retainedCount, String reason) { }
    public record SlotView(int index, State state, int retainedCount, int shotIndex, long generation, String reason) { }
    /** Opaque controller authority; neither items nor a mutable lease token are exposed. */
    public static final class Volley {
        private final Entry entry;
        private final UUID round;
        private final int shotCount;
        private final long deadline;
        private final int startedTick;
        private final ServerLevel level;
        private final Slot[] slots = new Slot[VOLLEY_SLOTS];
        private int nextShot, spent;
        private boolean live;
        private Volley(Entry entry, UUID round, int shotCount, long deadline) {
            this.entry = entry; this.round = round; this.shotCount = shotCount; this.deadline = deadline;
            this.startedTick = entry.owner.getServer().getTickCount();
            this.level = entry.owner.serverLevel();
            for (int i = 0; i < slots.length; i++) slots[i] = new Slot(i);
        }
        public UUID roundId() { return round; }
        public int shotCount() { return shotCount; }
        public long deadline() { return deadline; }
        public int nextShotIndex() { requireThread(entry.owner); return nextShot; }
        public List<SlotView> slots() {
            requireThread(entry.owner);
            if (entry.volley == this) integrity(entry);
            List<SlotView> result = new ArrayList<>(VOLLEY_SLOTS);
            for (Slot slot : slots) result.add(slotView(slot));
            return List.copyOf(result);
        }
    }
    /** Installed by the package's closed controller; only native admitted entity ticks invoke it. */
    @FunctionalInterface interface ShotTick { void tick(BorrowedNeedleEntity entity); }
    private static final class Slot {
        final int index;
        State state = State.EMPTY;
        ItemStack stock = ItemStack.EMPTY;
        String reason = "";
        int shotIndex = -1;
        long generation;
        @Nullable BorrowedNeedleEntity projection, retiredProjection;
        Slot(int index) { this.index = index; }
    }
    private static final Map<ServerPlayer, Entry> ENTRIES = new IdentityHashMap<>();
    private static boolean registered;

    private static final class Entry {
        final ServerPlayer owner;
        final Object session = new Object();
        State state = State.EMPTY;
        long nextNonce = 1, nonce, nextGeneration = 1;
        boolean generationExhausted;
        @Nullable Volley volley;
        boolean exhausted, busy, replaced, nativeDeath, rawCorrupt, cloningTarget;
        @Nullable Object leaseConnection;
        String reason = "";
        ItemStack stock = ItemStack.EMPTY;
        @Nullable BorrowedNeedleEntity projection;
        @Nullable Tag expectedRaw;
        @Nullable Tag evidence;
        Entry(ServerPlayer owner) { this.owner = owner; }
    }
    private record Pose(UUID uuid, int id, Vec3 position, Vec3 velocity, AABB bounds, float yaw, float pitch) {
        Pose(BorrowedNeedleEntity entity) {
            this(entity.getUUID(), entity.getId(), entity.position(), entity.getDeltaMovement(),
                    entity.getBoundingBox(), entity.getYRot(), entity.getXRot());
        }
        boolean matches(BorrowedNeedleEntity entity) {
            return uuid.equals(entity.getUUID()) && id == entity.getId() && position.equals(entity.position())
                    && velocity.equals(entity.getDeltaMovement()) && bounds.equals(entity.getBoundingBox())
                    && yaw == entity.getYRot() && pitch == entity.getXRot();
        }
    }
    /** Observation only: no snapshot is ever restored over changes made by a callback. */
    private static final class InventorySnapshot {
        final Inventory inventory;
        final ItemStack[] references, values;
        final int selected;
        InventorySnapshot(ServerPlayer owner) {
            inventory = owner.getInventory();
            references = new ItemStack[inventory.getContainerSize()];
            values = new ItemStack[references.length];
            selected = inventory.selected;
            for (int i = 0; i < references.length; i++) {
                references[i] = inventory.getItem(i);
                values[i] = references[i].copy();
            }
        }
        boolean matches(ServerPlayer owner) {
            if (owner.getInventory() != inventory || inventory.getClass() != Inventory.class
                    || inventory.selected != selected || inventory.getContainerSize() != references.length) return false;
            for (int i = 0; i < references.length; i++)
                if (inventory.getItem(i) != references[i] || !ItemStack.matches(inventory.getItem(i), values[i])) return false;
            return true;
        }
    }

    private BorrowedNeedleEscrow() { }

    public static void register() {
        if (registered) return;
        registered = true;
        NeoForge.EVENT_BUS.addListener(BorrowedNeedleEscrow::logout);
        NeoForge.EVENT_BUS.addListener(BorrowedNeedleEscrow::changedDimension);
        NeoForge.EVENT_BUS.addListener(BorrowedNeedleEscrow::cloned);
        NeoForge.EVENT_BUS.addListener(BorrowedNeedleEscrow::stopped);
        NeoForge.EVENT_BUS.addListener(BorrowedNeedleEscrow::serverTick);
    }
    private static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) retire(player);
    }
    private static void changedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) retire(player);
    }
    private static void cloned(PlayerEvent.Clone event) {
        if (event.getOriginal() instanceof ServerPlayer oldOwner && event.getEntity() instanceof ServerPlayer newOwner)
            cloneOwner(oldOwner, newOwner, event.isWasDeath());
    }
    private static void stopped(ServerStoppedEvent event) {
        ENTRIES.entrySet().removeIf(entry -> entry.getKey().getServer() == event.getServer());
    }
    private static void serverTick(ServerTickEvent.Post event) {
        for (Entry entry : List.copyOf(ENTRIES.values()))
            if (entry.owner.getServer() == event.getServer() && entry.volley != null && entry.volley.live)
                current(entry.volley);
    }

    private static boolean withinDeadline(Volley volley) {
        // Native server ticks wrap as ints. A rewind has a huge unsigned delta and fails closed.
        return Integer.toUnsignedLong(volley.entry.owner.getServer().getTickCount() - volley.startedTick) < MAX_VOLLEY_TICKS;
    }

    /** Six physical originals are removed once, without any external callback in the commit. */
    static Optional<Volley> reserveVolley(ServerPlayer owner, UUID roundId, int shotCount) {
        if (!validOwner(owner, null) || roundId == null || shotCount < VOLLEY_SLOTS || shotCount > MAX_VOLLEY_SHOTS
                || !NativeBorrowedNeedleMixinPlugin.isReviewedDeathDropSupported()) return Optional.empty();
        ItemStack hand = owner.getOffhandItem();
        long now = Integer.toUnsignedLong(owner.getServer().getTickCount());
        if (!plainStack(hand) || hand.getCount() < VOLLEY_SLOTS)
            return Optional.empty();
        Entry entry = entry(owner);
        if (!integrity(entry) || entry.state != State.EMPTY || entry.volley != null || entry.busy || entry.replaced
                || entry.nativeDeath || entry.exhausted || entry.generationExhausted) return Optional.empty();
        Volley volley = new Volley(entry, roundId, shotCount, now + MAX_VOLLEY_TICKS);
        // Pre-encode while the real inventory is intact. Plain native ItemStacks have no component callbacks.
        long nonce = entry.nextNonce;
        for (Slot slot : volley.slots) {
            slot.stock = hand.copyWithCount(1);
            slot.state = State.BORROWED_IDLE;
        }
        CompoundTag committed = encodeVolley(entry, volley, State.ACTIVE, nonce, "", nonce == Long.MAX_VALUE ? nonce : nonce + 1,
                nonce == Long.MAX_VALUE);
        for (Slot slot : volley.slots) slot.stock = ItemStack.EMPTY; // Encoding copies are never retained as custody.
        entry.busy = true;
        try {
            entry.volley = volley;
            entry.nonce = nonce;
            entry.nextNonce = nonce == Long.MAX_VALUE ? nonce : nonce + 1;
            entry.exhausted = nonce == Long.MAX_VALUE;
            entry.leaseConnection = owner.connection;
            entry.state = State.ACTIVE;
            entry.reason = "";
            // No constructor, inventory.add/drop, payment, guard, synchronization or event between these operations.
            for (Slot slot : volley.slots) slot.stock = hand.split(1);
            write(entry, committed);
            volley.live = true;
            return Optional.of(volley);
        } catch (RuntimeException failure) {
            quarantineBorrow(entry, "reservation_exception:" + failure.getClass().getSimpleName());
            return Optional.empty();
        } catch (Error failure) {
            quarantineBorrow(entry, "reservation_error:" + failure.getClass().getSimpleName());
            throw failure;
        } finally { entry.busy = false; }
    }

    /** A controller can only spend the next preallocated index, and can never reuse it after a callback. */
    static Optional<BorrowedNeedleEntity> publishShot(Volley volley, int index, Vec3 origin, Vec3 velocity,
            BooleanSupplier guard, BooleanSupplier callbackFreePayment, ShotTick tick) {
        if (volley == null || !finite(origin) || !finite(velocity) || !Double.isFinite(velocity.lengthSqr()) || velocity.lengthSqr() <= 0
                || guard == null || callbackFreePayment == null || tick == null) return Optional.empty();
        Entry entry = volley.entry;
        if (!slotAvailable(volley, index)) return Optional.empty();
        ServerPlayer owner = entry.owner;
        ServerLevel level = owner.serverLevel();
        AABB bounds = TeleporterNeedleEntities.BORROWED_NEEDLE.get().getDimensions().makeBoundingBox(origin);
        if (!loadedDestination(level, bounds)) return Optional.empty();
        Slot slot = volley.slots[index % VOLLEY_SLOTS];
        InventorySnapshot inventory = new InventorySnapshot(owner);
        Object connection = owner.connection;
        ItemStack original = slot.stock;
        long generation = entry.nextGeneration;
        slot.shotIndex = index;
        slot.generation = generation;
        volley.nextShot++;
        volley.spent |= 1 << index;
        if (generation == Long.MAX_VALUE) entry.generationExhausted = true;
        else entry.nextGeneration++;
        entry.busy = true;
        BorrowedNeedleEntity candidate = null;
        boolean admitted = false;
        try {
            persist(entry); // Irrevocably spend both the index and generation before any external callback.
            boolean allowed = guard.getAsBoolean();
            if (entry.nativeDeath || entry.replaced || !volley.live) return Optional.empty();
            if (!volleyBoundaryValid(volley, slot, level, connection, original, inventory, State.BORROWED_IDLE)
                    || !loadedDestination(level, bounds)) {
                quarantineBorrow(entry, "shot_pre_guard_changed");
                return Optional.empty();
            }
            if (!allowed) return Optional.empty();
            candidate = new BorrowedNeedleEntity(TeleporterNeedleEntities.BORROWED_NEEDLE.get(), level);
            if (entry.nativeDeath || entry.replaced || !volley.live) return Optional.empty();
            if (!volleyBoundaryValid(volley, slot, level, connection, original, inventory, State.BORROWED_IDLE)
                    || candidate.isRemoved() || candidate.isAddedToLevel() || candidate.level() != level) {
                quarantineBorrow(entry, "shot_constructor_changed");
                return Optional.empty();
            }
            candidate.setPos(origin);
            candidate.setDeltaMovement(velocity);
            if (!loadedDestination(level, candidate.getBoundingBox())
                    || !candidate.prepareShot(owner, generation, entry.session, volley.round, slot.index, index, tick)) {
                quarantineBorrow(entry, "shot_prepare_changed");
                return Optional.empty();
            }
            slot.projection = candidate;
            slot.state = State.STAGED;
            persist(entry);
            Pose pose = new Pose(candidate);
            boolean accepted = level.addFreshEntity(candidate);
            if (entry.nativeDeath || entry.replaced || !volley.live) return Optional.empty();
            if (!volleyStageValid(volley, slot, level, connection, candidate, pose, original, inventory, accepted)) {
                quarantineBorrow(entry, "shot_native_join_changed");
                return Optional.empty();
            }
            if (!accepted) { idleShot(volley, slot, "join_canceled"); return Optional.empty(); }
            boolean stillAllowed = guard.getAsBoolean();
            if (entry.nativeDeath || entry.replaced || !volley.live) return Optional.empty();
            if (!volleyStageValid(volley, slot, level, connection, candidate, pose, original, inventory, true)) {
                quarantineBorrow(entry, "shot_post_guard_changed");
                return Optional.empty();
            }
            if (!stillAllowed) { idleShot(volley, slot, "guard_refused"); return Optional.empty(); }
            boolean paid = callbackFreePayment.getAsBoolean();
            if (entry.nativeDeath || entry.replaced || !volley.live) return Optional.empty();
            if (!volleyStageValid(volley, slot, level, connection, candidate, pose, original, inventory, true)) {
                quarantineBorrow(entry, "shot_payment_changed");
                return Optional.empty();
            }
            if (!paid) { idleShot(volley, slot, "payment_refused"); return Optional.empty(); }
            slot.state = State.ACTIVE;
            persist(entry);
            if (!candidate.admit(owner, generation, entry.session)) {
                quarantineBorrow(entry, "shot_activation_failed");
                return Optional.empty();
            }
            admitted = true;
            return Optional.of(candidate);
        } catch (RuntimeException failure) {
            quarantineBorrow(entry, "shot_exception:" + failure.getClass().getSimpleName());
            return Optional.empty();
        } catch (Error failure) {
            quarantineBorrow(entry, "shot_error:" + failure.getClass().getSimpleName());
            throw failure;
        } finally {
            try {
                if (!admitted && candidate != null) {
                    candidate.revoke();
                    try { candidate.discardRetired(); }
                    catch (RuntimeException failure) { quarantineBorrow(entry, "shot_cleanup_exception"); }
                    catch (Error failure) { quarantineBorrow(entry, "shot_cleanup_error"); throw failure; }
                }
            } finally { entry.busy = false; }
            if (!admitted && !entry.nativeDeath && !entry.replaced && entry.state == State.RETURN_PENDING) claim(owner);
        }
    }

    private static boolean volleyBoundaryValid(Volley volley, Slot slot, ServerLevel level, Object connection,
            ItemStack original, InventorySnapshot inventory, State state) {
        Entry entry = volley.entry;
        return ENTRIES.get(entry.owner) == entry && entry.volley == volley && volley.live && !entry.replaced
                && !entry.nativeDeath && entry.state == State.ACTIVE && integrity(entry) && validOwner(entry.owner, level)
                && entry.owner.connection == connection && entry.leaseConnection == connection
                && withinDeadline(volley) && slot.state == state && slot.stock == original
                && oneNeedle(original) && inventory.matches(entry.owner) && allSlotStockValid(volley);
    }

    private static boolean volleyStageValid(Volley volley, Slot slot, ServerLevel level, Object connection,
            BorrowedNeedleEntity candidate, Pose pose, ItemStack original, InventorySnapshot inventory, boolean published) {
        return volleyBoundaryValid(volley, slot, level, connection, original, inventory, State.STAGED)
                && slot.projection == candidate && candidate.level() == level && !candidate.isRemoved()
                && pose.matches(candidate) && loadedDestination(level, candidate.getBoundingBox())
                && candidate.hasLease(volley.entry.owner, slot.generation, volley.entry.session) && !candidate.isAdmitted()
                && candidate.matchesShot(volley.round, slot.index, slot.shotIndex)
                && (published ? level.getEntity(pose.uuid()) == candidate && level.getEntity(pose.id()) == candidate
                              : level.getEntity(pose.uuid()) != candidate && level.getEntity(pose.id()) != candidate);
    }

    static boolean current(Volley volley) {
        if (volley == null) return false;
        Entry entry = volley.entry;
        requireThread(entry.owner);
        if (ENTRIES.get(entry.owner) != entry || entry.volley != volley || !volley.live || entry.replaced
                || entry.nativeDeath || entry.state != State.ACTIVE || !integrity(entry)) return false;
        if (!validOwner(entry.owner, volley.level) || entry.owner.connection != entry.leaseConnection
                || !withinDeadline(volley)) {
            pend(entry, "volley_session_expired");
            if (!entry.busy && !entry.nativeDeath && !entry.replaced) claim(entry.owner);
            return false;
        }
        return true;
    }

    static boolean slotAvailable(Volley volley, int index) {
        if (volley == null || index < 0 || index >= volley.shotCount || index != volley.nextShot) return false;
        Entry entry = volley.entry;
        if (!current(volley) || entry.busy || entry.generationExhausted || (volley.spent & 1 << index) != 0) return false;
        Slot slot = volley.slots[index % VOLLEY_SLOTS];
        BorrowedNeedleEntity old = slot.retiredProjection;
        return slot.state == State.BORROWED_IDLE && oneNeedle(slot.stock) && slot.projection == null
                && (old == null || old.isRemoved() && !old.isAdmitted()
                    && old.level() instanceof ServerLevel level && level.getEntity(old.getUUID()) != old && level.getEntity(old.getId()) != old);
    }

    /** Spend a frozen assignment that no longer has a viable target; never disturb its older live slot. */
    static boolean skipShot(Volley volley, int index) {
        if (volley == null || index < 0 || index >= volley.shotCount || index != volley.nextShot
                || !current(volley) || volley.entry.busy || (volley.spent & 1 << index) != 0) return false;
        volley.nextShot++;
        volley.spent |= 1 << index;
        persist(volley.entry);
        return true;
    }

    static void finishVolley(Volley volley) {
        if (volley == null) return;
        Entry entry = volley.entry;
        requireThread(entry.owner);
        if (entry.volley != volley || entry.replaced || !integrity(entry)) return;
        boolean newlyPending = volley.live && entry.state == State.ACTIVE;
        if (newlyPending) pend(entry, "volley_finished");
        else revoke(entry);
        if (newlyPending && !entry.busy && !entry.nativeDeath && entry.state == State.RETURN_PENDING) claim(entry.owner);
    }

    /** Closed controller fail-stop: an uncertain native contact never authorizes a refund. */
    static void quarantineVolley(Volley volley, String reason) {
        if (volley == null) return;
        Entry entry = volley.entry;
        requireThread(entry.owner);
        if (entry.volley != volley || entry.replaced) return;
        String bounded = reason == null ? "controller_uncertain" : reason.substring(0, Math.min(256, reason.length()));
        quarantineBorrow(entry, bounded);
    }

    static void retireShot(BorrowedNeedleEntity entity) {
        if (entity == null || entity.level().isClientSide) return;
        ServerPlayer owner = entity.leaseOwner();
        if (owner == null) { entity.revoke(); entity.discardRetired(); return; }
        requireThread(owner);
        Entry entry = ENTRIES.get(owner);
        Volley volley = entry == null ? null : entry.volley;
        if (volley == null || entity.slotIndex() < 0 || entity.slotIndex() >= VOLLEY_SLOTS) return;
        Slot slot = volley.slots[entity.slotIndex()];
        if (slot.projection != entity) return;
        if (entry.busy && slot.state == State.STAGED) { quarantineBorrow(entry, "staged_shot_removed"); return; }
        if (!current(volley)) { revoke(entry); return; }
        idleShot(volley, slot, "shot_retired");
    }

    static void shotFailed(BorrowedNeedleEntity entity, String reason) {
        ServerPlayer owner = entity.leaseOwner();
        if (owner == null || owner.getServer() == null || !owner.getServer().isSameThread()) return;
        Entry entry = ENTRIES.get(owner);
        if (entry == null || entry.volley == null || entity.slotIndex() < 0 || entity.slotIndex() >= VOLLEY_SLOTS
                || entry.volley.slots[entity.slotIndex()].projection != entity) return;
        quarantineBorrow(entry, reason);
    }

    private static void idleShot(Volley volley, Slot slot, String reason) {
        Entry entry = volley.entry;
        if (!integrity(entry) || entry.replaced || entry.nativeDeath || !volley.live || entry.state != State.ACTIVE) {
            revokeSlot(entry, slot); return;
        }
        slot.state = State.BORROWED_IDLE;
        slot.reason = reason;
        persist(entry); // Retirement is durable before leave observers can run.
        revokeSlot(entry, slot);
    }

    public static List<SlotView> allView(ServerPlayer owner) {
        requireThread(owner);
        Entry entry = entry(owner);
        integrity(entry);
        List<SlotView> result = new ArrayList<>(VOLLEY_SLOTS);
        for (int i = 0; i < VOLLEY_SLOTS; i++) {
            Slot slot = entry.volley == null ? null : entry.volley.slots[i];
            result.add(slot == null ? new SlotView(i, i == 0 ? entry.state : State.EMPTY,
                    i == 0 && !entry.stock.isEmpty() ? entry.stock.getCount() : 0, -1, 0, i == 0 ? entry.reason : "")
                    : slotView(slot));
        }
        return List.copyOf(result);
    }

    private static SlotView slotView(Slot slot) {
        return new SlotView(slot.index, slot.state, slot.stock.isEmpty() ? 0 : slot.stock.getCount(), slot.shotIndex, slot.generation, slot.reason);
    }

    private static boolean allSlotStockValid(Volley volley) {
        Set<ItemStack> identities = java.util.Collections.newSetFromMap(new IdentityHashMap<>());
        for (Slot slot : volley.slots) if (!oneNeedle(slot.stock) || !identities.add(slot.stock)) return false;
        return true;
    }

    /** Guard may call external code. Payment MUST be a narrow callback-free server-thread commit. */
    public static Optional<BorrowedNeedleEntity> borrow(ServerPlayer owner, Vec3 position,
            BooleanSupplier guard, BooleanSupplier callbackFreePayment) {
        if (!validOwner(owner, null) || !finite(position) || guard == null || callbackFreePayment == null
                || !NativeBorrowedNeedleMixinPlugin.isReviewedDeathDropSupported()) return Optional.empty();
        ServerLevel level = owner.serverLevel();
        AABB initialBounds = TeleporterNeedleEntities.BORROWED_NEEDLE.get().getDimensions().makeBoundingBox(position);
        if (!loadedDestination(level, initialBounds)) return Optional.empty();
        Entry entry = entry(owner);
        if (!integrity(entry) || entry.volley != null || entry.state != State.EMPTY || entry.busy || entry.replaced || entry.exhausted
                || entry.nativeDeath) return Optional.empty();
        ItemStack hand = owner.getOffhandItem();
        if (!plainStack(hand)) return Optional.empty();
        ItemStack handBefore = hand.copy();
        InventorySnapshot beforeInventory = new InventorySnapshot(owner);
        Object connection = owner.connection;
        entry.leaseConnection = connection;
        entry.busy = true;
        long nonce = entry.nextNonce;
        if (nonce == Long.MAX_VALUE) entry.exhausted = true;
        else entry.nextNonce++;
        persist(entry); // Burn every attempt's nonce before any callback; never wrap or reuse.
        BorrowedNeedleEntity candidate = null;
        boolean successful = false;
        try {
            boolean allowed = guard.getAsBoolean();
            if (ENTRIES.get(owner) != entry || !validOwner(owner, level) || owner.connection != connection || !integrity(entry)
                    || entry.replaced || entry.nativeDeath || owner.getOffhandItem() != hand
                    || !ItemStack.matches(hand, handBefore) || !beforeInventory.matches(owner) || entry.state != State.EMPTY
                    || !loadedDestination(level, initialBounds)) {
                quarantineBorrow(entry, "pre_guard_changed");
                return Optional.empty();
            }
            if (!allowed) return Optional.empty();
            // No callback or native inventory insertion between removal and escrow persistence.
            entry.stock = hand.split(1);
            entry.nonce = nonce;
            entry.state = State.ESCROWED;
            persist(entry);
            ItemStack remainder = hand.copy();
            InventorySnapshot stagedInventory = new InventorySnapshot(owner);
            if (!oneNeedle(entry.stock)) {
                quarantineBorrow(entry, "invalid_extraction");
                return Optional.empty();
            }
            ItemStack original = entry.stock;
            candidate = new BorrowedNeedleEntity(TeleporterNeedleEntities.BORROWED_NEEDLE.get(), level);
            // Entity's native constructor fires Size and EntityConstructing callbacks.
            // They may retire, clone or kill the owner before there is any candidate lease.
            if (entry.replaced || entry.nativeDeath) return Optional.empty();
            if (ENTRIES.get(owner) != entry || !validOwner(owner, level) || owner.connection != connection
                    || !integrity(entry) || entry.stock != original || !oneNeedle(original)
                    || !stagedInventory.matches(owner)) {
                quarantineBorrow(entry, "constructor_changed");
                return Optional.empty();
            }
            if (entry.state == State.RETURN_PENDING) return Optional.empty();
            if (entry.state != State.ESCROWED || candidate.isRemoved() || candidate.isAddedToLevel()
                    || candidate.level() != level) {
                quarantineBorrow(entry, "constructor_candidate_changed");
                return Optional.empty();
            }
            candidate.setPos(position);
            if (!loadedDestination(level, candidate.getBoundingBox())) {
                quarantineBorrow(entry, "constructor_destination_changed");
                return Optional.empty();
            }
            if (!candidate.prepare(owner, nonce, entry.session)) {
                quarantineBorrow(entry, "prepare_failed");
                return Optional.empty();
            }
            entry.projection = candidate;
            entry.state = State.STAGED;
            persist(entry);
            Pose pose = new Pose(candidate);
            // This is the actual NeoForge EntityJoinLevelEvent publication seam.
            boolean accepted = level.addFreshEntity(candidate);
            if (!stageValid(entry, level, connection, candidate, pose, hand, remainder, original, stagedInventory, accepted)) {
                quarantineBorrow(entry, "native_join_changed");
                return Optional.empty();
            }
            if (!accepted) {
                pend(entry, "join_canceled");
                return Optional.empty();
            }
            boolean stillAllowed = guard.getAsBoolean();
            if (!stageValid(entry, level, connection, candidate, pose, hand, remainder, original, stagedInventory, true)) {
                quarantineBorrow(entry, "post_guard_changed");
                return Optional.empty();
            }
            if (!stillAllowed) {
                pend(entry, "guard_refused");
                return Optional.empty();
            }
            if (!callbackFreePayment.getAsBoolean()) {
                // Even a false result cannot authorize return if the contract was violated.
                if (!stageValid(entry, level, connection, candidate, pose, hand, remainder, original, stagedInventory, true))
                    quarantineBorrow(entry, "payment_changed");
                else pend(entry, "payment_refused");
                return Optional.empty();
            }
            if (!stageValid(entry, level, connection, candidate, pose, hand, remainder, original, stagedInventory, true)) {
                quarantineBorrow(entry, "payment_changed");
                return Optional.empty();
            }
            entry.state = State.ACTIVE;
            persist(entry);
            if (!candidate.admit(owner, nonce, entry.session)) {
                quarantineBorrow(entry, "activation_failed");
                return Optional.empty();
            }
            successful = true;
            return Optional.of(candidate);
        } catch (RuntimeException failure) {
            if (entry.nonce == 0 && !entry.nativeDeath && !entry.replaced) entry.nonce = nonce;
            quarantineBorrow(entry, "borrow_exception:" + failure.getClass().getSimpleName());
            return Optional.empty();
        } catch (Error failure) {
            // Preserve a visible non-redeemable outcome even when a fatal callback error
            // must propagate to the native caller; finally must never turn it into a refund.
            if (entry.nonce == 0 && !entry.nativeDeath && !entry.replaced) entry.nonce = nonce;
            quarantineBorrow(entry, "borrow_error:" + failure.getClass().getSimpleName());
            throw failure;
        } finally {
            entry.busy = false;
            if (!successful) {
                revoke(entry);
                if (candidate != null) {
                    candidate.revoke();
                    try { candidate.discardRetired(); }
                    catch (RuntimeException failure) { quarantineBorrow(entry, "candidate_removal_exception"); }
                }
                if (entry.state == State.RETURN_PENDING && !entry.nativeDeath && !entry.replaced) claim(owner);
            }
        }
    }

    private static boolean stageValid(Entry entry, ServerLevel level, Object connection,
            BorrowedNeedleEntity candidate, Pose pose, ItemStack hand, ItemStack remainder,
            ItemStack original, InventorySnapshot inventory, boolean published) {
        return ENTRIES.get(entry.owner) == entry && !entry.replaced && !entry.nativeDeath && validOwner(entry.owner, level)
                && entry.owner.connection == connection && integrity(entry) && entry.state == State.STAGED
                && entry.projection == candidate && entry.stock == original && oneNeedle(original)
                && entry.owner.getOffhandItem() == hand && ItemStack.matches(hand, remainder)
                && inventory.matches(entry.owner)
                && candidate.level() == level && !candidate.isRemoved() && pose.matches(candidate)
                && loadedDestination(level, candidate.getBoundingBox())
                && candidate.hasLease(entry.owner, entry.nonce, entry.session) && !candidate.isAdmitted()
                && (published ? level.getEntity(pose.uuid()) == candidate && level.getEntity(pose.id()) == candidate
                              : level.getEntity(pose.uuid()) != candidate && level.getEntity(pose.id()) != candidate);
    }

    /** Revocation is first; a full main inventory remains pending until explicit claim. */
    public static void retire(ServerPlayer owner) {
        if (owner == null) return;
        requireThread(owner);
        if (!hasExisting(owner)) return;
        Entry entry = entry(owner);
        if (entry.replaced || !integrity(entry)) return;
        boolean newlyPending = entry.state == State.ACTIVE || entry.state == State.STAGED || entry.state == State.ESCROWED;
        if (newlyPending)
            pend(entry, "retired");
        else revoke(entry);
        if (newlyPending && !entry.busy && !entry.nativeDeath && entry.state == State.RETURN_PENDING) claim(owner);
    }

    /** Only RETURN_PENDING is redeemable; quarantine and a death handoff never auto-retry. */
    public static boolean claim(ServerPlayer owner) {
        if (!validOwner(owner, null)) return false;
        Entry entry = entry(owner);
        if (entry.volley != null) return claimVolley(entry);
        if (!integrity(entry) || entry.busy || entry.replaced || entry.nativeDeath
                || entry.state != State.RETURN_PENDING || !oneNeedle(entry.stock)) return false;
        if (!revoke(entry) || !validOwner(owner, null) || !integrity(entry) || entry.replaced || entry.nativeDeath
                || entry.state != State.RETURN_PENDING || !oneNeedle(entry.stock)) return false;
        var inventory = owner.getInventory();
        ItemStack merge = null;
        ItemStack offhand = inventory.offhand.get(0);
        if (plainStack(offhand) && offhand.getCount() < offhand.getMaxStackSize()) merge = offhand;
        int empty = -1;
        for (int i = 0; i < inventory.items.size(); i++) {
            ItemStack stack = inventory.items.get(i);
            if (merge == null && plainStack(stack) && stack.getCount() < stack.getMaxStackSize()) merge = stack;
            if (empty < 0 && stack.isEmpty()) empty = i;
        }
        if (merge == null && empty < 0) return false;
        ItemStack original = entry.stock;
        // Pre-encode the settled root. The commit below has no event, inventory.add, drop,
        // synchronisation packet or external callback (including creative's deletion shortcut).
        CompoundTag settled = encode(entry, State.EMPTY, 0, ItemStack.EMPTY, "");
        entry.busy = true;
        try {
            if (merge != null) {
                merge.grow(1);
                original.shrink(1);
            } else inventory.items.set(empty, original);
            entry.stock = ItemStack.EMPTY;
            entry.nonce = 0;
            entry.state = State.EMPTY;
            entry.reason = "";
            write(entry, settled);
            inventory.setChanged();
            return true;
        } catch (RuntimeException failure) {
            quarantine(entry, "return_exception:" + failure.getClass().getSimpleName());
            return false;
        } finally { entry.busy = false; }
    }

    private static int retained(Entry entry) {
        if (entry.volley == null) return entry.stock.isEmpty() ? 0 : entry.stock.getCount();
        int count = 0;
        for (Slot slot : entry.volley.slots) if (!slot.stock.isEmpty()) count += slot.stock.getCount();
        return count;
    }

    /** Each settled physical slot is committed once; a full bag stays pending without tick retries. */
    private static boolean claimVolley(Entry entry) {
        Volley volley = entry.volley;
        if (volley == null || !integrity(entry) || entry.busy || entry.replaced || entry.nativeDeath
                || entry.state != State.RETURN_PENDING || volley.live || !revoke(entry)
                || !validOwner(entry.owner, null) || !integrity(entry) || entry.replaced || entry.nativeDeath
                || entry.volley != volley || entry.state != State.RETURN_PENDING) return false;
        boolean returned = false;
        Inventory inventory = entry.owner.getInventory();
        entry.busy = true;
        try {
            for (Slot slot : volley.slots) {
                if (slot.state == State.EMPTY) continue;
                if (slot.state != State.RETURN_PENDING || !oneNeedle(slot.stock)) {
                    quarantine(entry, "return_slot_invalid"); return false;
                }
                ItemStack merge = null;
                ItemStack offhand = inventory.offhand.get(0);
                if (plainStack(offhand) && offhand.getCount() < offhand.getMaxStackSize()) merge = offhand;
                int empty = -1;
                for (int i = 0; i < inventory.items.size(); i++) {
                    ItemStack stack = inventory.items.get(i);
                    if (merge == null && plainStack(stack) && stack.getCount() < stack.getMaxStackSize()) merge = stack;
                    if (empty < 0 && stack.isEmpty()) empty = i;
                }
                if (merge == null && empty < 0) break;
                ItemStack original = slot.stock;
                boolean last = retained(entry) == 1;
                CompoundTag settled;
                if (last) settled = encodeSingle(entry, State.EMPTY, 0, ItemStack.EMPTY, "");
                else {
                    settled = encode(entry, entry.state, entry.nonce, entry.stock, entry.reason);
                    CompoundTag encodedSlot = settled.getList("slots", Tag.TAG_COMPOUND).getCompound(slot.index);
                    encodedSlot.putString("state", State.EMPTY.name());
                    encodedSlot.put("stock", new CompoundTag());
                    encodedSlot.putString("reason", "");
                }
                // Narrow native commit: no add/drop/event or compensation path.
                if (merge != null) { merge.grow(1); original.shrink(1); }
                else inventory.items.set(empty, original);
                slot.stock = ItemStack.EMPTY;
                slot.state = State.EMPTY;
                slot.reason = "";
                if (last) {
                    entry.volley = null;
                    entry.state = State.EMPTY;
                    entry.nonce = 0;
                    entry.reason = "";
                }
                write(entry, settled);
                returned = true;
            }
            if (returned) inventory.setChanged();
            return returned;
        } catch (RuntimeException failure) {
            quarantine(entry, "volley_return_exception:" + failure.getClass().getSimpleName());
            return false;
        } catch (Error failure) {
            quarantine(entry, "volley_return_error:" + failure.getClass().getSimpleName());
            throw failure;
        } finally { entry.busy = false; }
    }

    private static boolean revokeSlot(Entry entry, Slot slot) {
        BorrowedNeedleEntity projection = slot.projection;
        slot.projection = null;
        if (projection == null) return !entry.rawCorrupt;
        slot.retiredProjection = projection;
        projection.revoke();
        boolean wasBusy = entry.busy;
        entry.busy = true;
        try {
            projection.discardRetired();
            if (!projection.isRemoved() && !projection.removalInProgress() || projection.isAdmitted()) {
                quarantine(entry, "shot_removal_unverified"); return false;
            }
            return integrity(entry);
        } catch (RuntimeException failure) {
            quarantine(entry, "shot_removal_exception"); return false;
        } catch (Error failure) {
            quarantine(entry, "shot_removal_error"); throw failure;
        } finally { entry.busy = wasBusy; }
    }

    private static void deathDropVolley(Entry entry) {
        if (entry.replaced || !integrity(entry) || entry.rawCorrupt || entry.state == State.EMPTY
                || entry.state == State.QUARANTINED || entry.state == State.DEATH_TRANSFER) return;
        beginNativeDeath(entry.owner);
        Volley volley = entry.volley;
        if (volley == null || entry.replaced || !integrity(entry) || entry.state != State.RETURN_PENDING) return;
        Collection<ItemEntity> capture = entry.owner.captureDrops();
        if (capture == null) { quarantine(entry, "death_capture_missing"); return; }
        for (Slot slot : volley.slots) {
            if (slot.state == State.EMPTY) continue;
            if (slot.state != State.RETURN_PENDING || !oneNeedle(slot.stock)) {
                quarantine(entry, "death_slot_invalid"); return;
            }
            ItemStack original = slot.stock;
            slot.state = State.DEATH_TRANSFER;
            slot.reason = "native_capture_handoff";
            entry.state = State.DEATH_TRANSFER;
            entry.reason = "native_capture_handoff";
            persist(entry); // Per-original native capture latch precedes every drop callback.
            try {
                ItemEntity dropped = entry.owner.drop(original, true, false);
                long identities = capture.stream().filter(item -> item == dropped).count();
                if (entry.owner.captureDrops() != capture || dropped == null || identities != 1 || entry.replaced
                        || dropped.getItem() != original || !oneNeedle(original) || dropped.level() != entry.owner.level()
                        || dropped.isRemoved() || slot.stock != original || slot.state != State.DEATH_TRANSFER
                        || entry.state != State.DEATH_TRANSFER || entry.volley != volley || !integrity(entry)) {
                    quarantineBorrow(entry, "death_transfer_uncertain");
                    if (!entry.replaced) quarantine(entry, "death_transfer_uncertain");
                    return;
                }
                slot.stock = ItemStack.EMPTY;
                slot.state = State.EMPTY;
                slot.reason = "native_death_settled";
                entry.state = State.RETURN_PENDING;
                entry.reason = "native_death_settled";
                if (retained(entry) == 0) {
                    entry.volley = null;
                    entry.nonce = 0;
                    entry.state = State.EMPTY;
                }
                persist(entry);
                // A later LivingDropsEvent cancellation owns the item; it never grants an escrow refund.
            } catch (RuntimeException failure) {
                quarantine(entry, "death_exception:" + failure.getClass().getSimpleName()); return;
            } catch (Error failure) {
                quarantine(entry, "death_error:" + failure.getClass().getSimpleName()); throw failure;
            }
        }
    }

    private static void cloneVolleyOwner(Entry old, ServerPlayer newOwner, boolean wasDeath) {
        if (old.replaced) return;
        if (old.cloningTarget) { quarantine(old, "clone_target_reentered"); return; }
        Entry next = entry(newOwner);
        boolean nextWasBusy = next.busy;
        ServerPlayer current = old.owner.getServer().getPlayerList().getPlayer(old.owner.getUUID());
        next.busy = true;
        next.cloningTarget = true;
        old.replaced = true;
        try {
            boolean removalVerified = revoke(old);
            boolean oldValid = integrity(old), nextValid = integrity(next);
            if (ENTRIES.get(old.owner) != old || ENTRIES.get(newOwner) != next || next.replaced || nextWasBusy
                    || old.owner.getServer().getPlayerList().getPlayer(old.owner.getUUID()) != current
                    || !nextValid || next.state != State.EMPTY || !next.stock.isEmpty() || next.volley != null) {
                quarantine(next, "clone_conflict"); return;
            }
            Volley source = old.volley;
            if (source == null) return;
            source.live = false;
            Volley moved = new Volley(next, source.round, source.shotCount, source.deadline);
            moved.nextShot = source.nextShot;
            moved.spent = source.spent;
            next.volley = moved;
            next.nextNonce = Math.max(next.nextNonce, old.nextNonce);
            next.exhausted |= old.exhausted;
            next.nextGeneration = Math.max(next.nextGeneration, old.nextGeneration);
            next.generationExhausted |= old.generationExhausted;
            next.nonce = old.nonce;
            boolean keepsInventory = !wasDeath || old.owner.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)
                    || old.owner.isSpectator();
            next.state = oldValid && keepsInventory && removalVerified && old.state != State.QUARANTINED
                    && old.state != State.DEATH_TRANSFER ? State.RETURN_PENDING : State.QUARANTINED;
            next.reason = next.state == State.RETURN_PENDING ? "owner_cloned" : "clone_unresolved";
            for (int i = 0; i < VOLLEY_SLOTS; i++) {
                Slot from = source.slots[i], to = moved.slots[i];
                to.stock = from.stock;
                to.shotIndex = from.shotIndex;
                to.generation = from.generation;
                to.state = from.state == State.EMPTY ? State.EMPTY : next.state;
                to.reason = from.state == State.EMPTY ? from.reason : next.reason;
                from.stock = ItemStack.EMPTY;
                from.state = State.EMPTY;
            }
            next.evidence = old.evidence;
            if (!oldValid) {
                Tag raw = old.owner.getPersistentData().get(STORAGE_KEY);
                next.rawCorrupt = true;
                next.reason = "clone_corrupt_escrow";
                if (raw != null) {
                    newOwner.getPersistentData().put(STORAGE_KEY, raw.copy());
                    next.expectedRaw = raw.copy();
                    next.evidence = raw.copy();
                }
            } else persist(next);
            old.volley = null;
            old.stock = ItemStack.EMPTY;
            old.nonce = 0;
            old.state = State.EMPTY;
            old.reason = "owner_replaced";
            old.rawCorrupt = false;
            if (!oldValid) { old.nextNonce = Long.MAX_VALUE; old.exhausted = true; }
            persist(old);
        } finally { next.cloningTarget = false; next.busy = nextWasBusy; }
    }

    /** Pinned committed-death HEAD hook; no return into the old player's inventory. */
    public static void beginNativeDeath(ServerPlayer owner) {
        requireThread(owner);
        if (!hasExisting(owner)) return;
        Entry entry = entry(owner);
        if (entry.replaced || !integrity(entry)) return;
        entry.nativeDeath = true;
        if (entry.state == State.ACTIVE || entry.state == State.STAGED || entry.state == State.ESCROWED)
            pend(entry, "native_death");
        else revoke(entry);
    }

    /** Only invoked at the reviewed Inventory.dropAll branch while its native capture is active. */
    public static void deathDrop(ServerPlayer owner) {
        requireThread(owner);
        if (!hasExisting(owner)) return;
        Entry entry = entry(owner);
        if (entry.volley != null) { deathDropVolley(entry); return; }
        if (entry.replaced || !integrity(entry) || entry.rawCorrupt || entry.state == State.EMPTY
                || entry.state == State.QUARANTINED || entry.state == State.DEATH_TRANSFER) return;
        beginNativeDeath(owner);
        if (entry.replaced || !integrity(entry) || entry.state != State.RETURN_PENDING) return;
        Collection<ItemEntity> capture = owner.captureDrops();
        if (capture == null || !oneNeedle(entry.stock)) {
            quarantine(entry, "death_capture_missing");
            return;
        }
        ItemStack original = entry.stock;
        entry.state = State.DEATH_TRANSFER;
        entry.reason = "native_capture_handoff";
        persist(entry); // Latch BEFORE the only native drop call; re-entry is never redeemable.
        try {
            ItemEntity dropped = owner.drop(original, true, false);
            long identities = capture.stream().filter(item -> item == dropped).count();
            if (owner.captureDrops() != capture || dropped == null || identities != 1
                    || dropped.getItem() != original || !oneNeedle(original) || dropped.level() != owner.level()
                    || dropped.isRemoved() || entry.stock != original || entry.state != State.DEATH_TRANSFER
                    || !integrity(entry)) {
                quarantine(entry, "death_transfer_uncertain");
                return;
            }
            entry.stock = ItemStack.EMPTY;
            entry.nonce = 0;
            entry.state = State.EMPTY;
            entry.reason = "native_death_settled";
            persist(entry);
            // LivingDropsEvent may later veto or replace this drop. Native policy owns it now;
            // neither cancellation, removal, clone nor reconnect refunds escrow a second time.
        } catch (RuntimeException failure) {
            quarantine(entry, "death_exception:" + failure.getClass().getSimpleName());
        }
    }

    /** Move retained custody once; neither vanilla PlayerPersisted nor an old lease is copied. */
    public static void cloneOwner(ServerPlayer oldOwner, ServerPlayer newOwner, boolean wasDeath) {
        requireThread(oldOwner);
        requireThread(newOwner);
        if (oldOwner == newOwner || oldOwner.getServer() != newOwner.getServer()
                || !oldOwner.getUUID().equals(newOwner.getUUID())) return;
        if (!hasExisting(oldOwner)) return;
        Entry old = entry(oldOwner);
        if (old.volley != null) { cloneVolleyOwner(old, newOwner, wasDeath); return; }
        if (old.replaced) return;
        if (old.cloningTarget) {
            quarantine(old, "clone_target_reentered");
            return;
        }
        Entry next = entry(newOwner);
        boolean nextWasBusy = next.busy;
        ServerPlayer current = oldOwner.getServer().getPlayerList().getPlayer(oldOwner.getUUID());
        next.busy = true;
        next.cloningTarget = true;
        old.replaced = true; // Old authority is gone before any native leave callback.
        try {
            boolean removalVerified = revoke(old);
            boolean oldValid = integrity(old), nextValid = integrity(next);
            if (ENTRIES.get(oldOwner) != old || ENTRIES.get(newOwner) != next || next.replaced || nextWasBusy
                    || oldOwner.getServer().getPlayerList().getPlayer(oldOwner.getUUID()) != current
                    || !nextValid || next.state != State.EMPTY || !next.stock.isEmpty()) {
                quarantine(next, "clone_conflict");
                return;
            }
            if (!oldValid) {
                // Preserve unknown bytes across the real owner replacement verbatim. They
                // never become a redeemable item count, and the old authority is retired once.
                Tag raw = oldOwner.getPersistentData().get(STORAGE_KEY);
                next.rawCorrupt = true;
                next.state = State.QUARANTINED;
                next.reason = "clone_corrupt_escrow";
                next.stock = old.stock;
                if (raw != null) {
                    newOwner.getPersistentData().put(STORAGE_KEY, raw.copy());
                    next.expectedRaw = raw.copy();
                    next.evidence = raw.copy();
                }
                old.stock = ItemStack.EMPTY;
                old.nonce = 0;
                old.nextNonce = Long.MAX_VALUE;
                old.exhausted = true;
                old.state = State.EMPTY;
                old.reason = "owner_replaced";
                old.rawCorrupt = false;
                persist(old);
                return;
            }
            next.nextNonce = Math.max(next.nextNonce, old.nextNonce);
            next.exhausted |= old.exhausted;
            next.nonce = old.nonce;
            next.nextGeneration = Math.max(next.nextGeneration, old.nextGeneration);
            next.generationExhausted |= old.generationExhausted;
            next.stock = old.stock;
            next.evidence = old.evidence;
            next.reason = old.reason;
            boolean keepsInventory = !wasDeath || oldOwner.serverLevel().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)
                    || oldOwner.isSpectator();
            if (old.state == State.EMPTY) {
                next.state = State.EMPTY;
                next.nonce = 0;
                persist(next);
            } else {
                next.state = keepsInventory && removalVerified && old.state != State.QUARANTINED && old.state != State.DEATH_TRANSFER
                        ? State.RETURN_PENDING : State.QUARANTINED;
                next.reason = next.state == State.RETURN_PENDING ? "owner_cloned" : "clone_unresolved";
                persist(next);
            }
            old.stock = ItemStack.EMPTY;
            old.nonce = 0;
            old.state = State.EMPTY;
            old.reason = "owner_replaced";
            old.rawCorrupt = false;
            persist(old);
        } finally {
            next.cloningTarget = false;
            next.busy = nextWasBusy;
        }
    }

    public static View view(ServerPlayer owner) {
        requireThread(owner);
        Entry entry = entry(owner);
        integrity(entry);
        return new View(entry.state, entry.nonce, retained(entry), entry.reason);
    }
    public static int retainedCount(ServerPlayer owner) { return view(owner).retainedCount(); }

    static boolean leaseActive(@Nullable ServerPlayer owner, BorrowedNeedleEntity projection, long nonce, Object session) {
        if (owner == null || owner.getServer() == null || !owner.getServer().isSameThread()) return false;
        Entry entry = ENTRIES.get(owner);
        if (entry != null && entry.volley != null) {
            Volley volley = entry.volley;
            int index = projection.slotIndex();
            if (index < 0 || index >= VOLLEY_SLOTS || entry.busy || session == null || session != entry.session
                    || !current(volley)) return false;
            Slot slot = volley.slots[index];
            return slot.state == State.ACTIVE && slot.projection == projection && slot.generation == nonce
                    && oneNeedle(slot.stock) && projection.matchesShot(volley.round, index, slot.shotIndex)
                    && NativeBorrowedNeedleMixinPlugin.isReviewedDeathDropSupported()
                    && owner.level() == projection.level();
        }
        return entry != null && !entry.replaced && !entry.nativeDeath && entry.state == State.ACTIVE
                && entry.projection == projection && entry.nonce == nonce && entry.session == session
                && session != null && NativeBorrowedNeedleMixinPlugin.isReviewedDeathDropSupported()
                && integrity(entry) && oneNeedle(entry.stock)
                && currentOwner(owner) && !owner.isSpectator() && owner.connection == entry.leaseConnection
                && owner.level() == projection.level();
    }

    static void projectionRemoved(@Nullable ServerPlayer owner, BorrowedNeedleEntity projection) {
        if (owner == null || owner.getServer() == null || !owner.getServer().isSameThread()) return;
        Entry entry = ENTRIES.get(owner);
        if (entry != null && entry.volley != null) { retireShot(projection); return; }
        if (entry == null || entry.projection != projection || entry.replaced) return;
        // A join callback removing its staged candidate is an uncertain publication, not a refund.
        if (entry.busy && entry.state == State.STAGED) {
            quarantine(entry, "staged_projection_removed");
            return;
        }
        if (entry.state == State.ACTIVE) {
            pend(entry, "projection_removed");
            if (!entry.busy && !entry.nativeDeath) claim(owner);
        }
    }

    private static boolean revoke(Entry entry) {
        if (entry.volley != null) {
            boolean verified = true;
            for (Slot slot : entry.volley.slots) verified &= revokeSlot(entry, slot);
            return verified;
        }
        BorrowedNeedleEntity projection = entry.projection;
        entry.projection = null;
        if (projection != null) {
            projection.revoke(); // Always invalidate token before entity removal or stock movement.
            boolean wasBusy = entry.busy;
            entry.busy = true; // EntityLeaveLevelEvent cannot reenter a claim or a new borrow.
            try {
                projection.discardRetired();
                return integrity(entry); // Preserve raw changes made inside removal callbacks.
            }
            catch (RuntimeException failure) {
                if (!entry.replaced && !(entry.nativeDeath && entry.state == State.EMPTY)) {
                    integrity(entry);
                    entry.state = State.QUARANTINED;
                    entry.reason = "projection_removal_exception";
                    persist(entry);
                }
                return false;
            } catch (Error failure) {
                if (!entry.replaced && !(entry.nativeDeath && entry.state == State.EMPTY)) {
                    integrity(entry);
                    entry.state = State.QUARANTINED;
                    entry.reason = "projection_removal_error";
                    persist(entry);
                }
                throw failure;
            } finally { entry.busy = wasBusy; }
        }
        return true;
    }
    private static void pend(Entry entry, String reason) {
        if (!integrity(entry) || entry.replaced || entry.rawCorrupt || entry.state == State.QUARANTINED
                || entry.state == State.DEATH_TRANSFER || entry.state == State.EMPTY) {
            revoke(entry);
            return;
        }
        // Latch and persist the destination BEFORE native discard can run arbitrary observers.
        entry.state = State.RETURN_PENDING;
        entry.reason = reason;
        if (entry.volley != null) {
            entry.volley.live = false;
            for (Slot slot : entry.volley.slots) if (slot.state != State.EMPTY) {
                slot.state = State.RETURN_PENDING;
                slot.reason = reason;
            }
        }
        persist(entry);
        revoke(entry);
    }
    private static void quarantineBorrow(Entry entry, String reason) {
        // A synchronous native death/clone has superseded this attempted publication. Its
        // transfer or keepInventory pending record must not be overwritten by outer cleanup.
        if (!entry.nativeDeath && !entry.replaced) quarantine(entry, reason);
    }
    private static void quarantine(Entry entry, String reason) {
        if (entry.replaced || entry.nativeDeath && entry.state == State.EMPTY) return;
        integrity(entry);
        entry.state = State.QUARANTINED;
        entry.reason = reason;
        if (entry.volley != null) {
            entry.volley.live = false;
            for (Slot slot : entry.volley.slots) if (slot.state != State.EMPTY) {
                slot.state = State.QUARANTINED;
                slot.reason = reason;
            }
        }
        if (!entry.rawCorrupt) persist(entry);
        revoke(entry);
    }

    private static boolean hasExisting(ServerPlayer owner) {
        return ENTRIES.containsKey(owner) || owner.getPersistentData().contains(STORAGE_KEY);
    }
    private static Entry entry(ServerPlayer owner) {
        Entry existing = ENTRIES.get(owner);
        if (existing != null) return existing;
        Entry entry = new Entry(owner);
        ENTRIES.put(owner, entry);
        Tag raw = owner.getPersistentData().get(STORAGE_KEY);
        if (raw == null) return entry;
        entry.expectedRaw = raw.copy();
        try {
            if (!(raw instanceof CompoundTag root) || !root.contains("version", Tag.TAG_INT))
                throw new IllegalArgumentException("shape");
            int version = root.getInt("version");
            if (version != 1 && version != VERSION || !root.getAllKeys().equals(version == 1 ? KEYS : V2_KEYS)
                    || !root.contains("owner", Tag.TAG_STRING) || !root.getString("owner").equals(owner.getUUID().toString())
                    || !root.contains("next_nonce", Tag.TAG_LONG) || !root.contains("nonce", Tag.TAG_LONG)
                    || !strictBoolean(root, "exhausted") || !root.contains("state", Tag.TAG_STRING)
                    || !root.contains("reason", Tag.TAG_STRING) || !root.contains("stock", Tag.TAG_COMPOUND))
                throw new IllegalArgumentException("shape");
            entry.nextNonce = root.getLong("next_nonce");
            entry.nonce = root.getLong("nonce");
            entry.exhausted = root.getBoolean("exhausted");
            entry.state = State.valueOf(root.getString("state"));
            entry.reason = root.getString("reason");
            entry.stock = decodeStock(owner, root.getCompound("stock"));
            if (entry.nextNonce <= 0 || entry.nonce < 0 || entry.reason.length() > 256
                    || entry.exhausted && entry.nextNonce != Long.MAX_VALUE || entry.nonce > entry.nextNonce
                    || !entry.exhausted && entry.nonce >= entry.nextNonce || entry.state == State.BORROWED_IDLE)
                throw new IllegalArgumentException("invariant");
            if (version == VERSION) {
                if (!root.contains("mode", Tag.TAG_STRING) || !root.contains("slots", Tag.TAG_LIST)
                        || !root.contains("round", Tag.TAG_STRING) || !root.contains("shot_count", Tag.TAG_INT)
                        || !root.contains("next_shot", Tag.TAG_INT) || !root.contains("spent", Tag.TAG_INT)
                        || !root.contains("next_generation", Tag.TAG_LONG) || !strictBoolean(root, "generation_exhausted")
                        || !root.contains("deadline", Tag.TAG_LONG)) throw new IllegalArgumentException("v2_shape");
                entry.nextGeneration = root.getLong("next_generation");
                entry.generationExhausted = root.getBoolean("generation_exhausted");
                if (entry.nextGeneration <= 0 || entry.generationExhausted && entry.nextGeneration != Long.MAX_VALUE)
                    throw new IllegalArgumentException("generation");
                if (root.getString("mode").equals("volley")) decodeVolley(entry, root);
                else if (!root.getString("mode").equals("single") || !root.getList("slots", Tag.TAG_COMPOUND).isEmpty()
                        || !((ListTag) root.get("slots")).isEmpty() || !root.getString("round").isEmpty()
                        || root.getInt("shot_count") != 0 || root.getInt("next_shot") != 0
                        || root.getInt("spent") != 0 || root.getLong("deadline") != 0)
                    throw new IllegalArgumentException("single_shape");
            } else entry.nextGeneration = entry.nextNonce; // Strict v1 migration preserves the existing nonce fence.
            if (entry.volley == null) {
                if (entry.state == State.EMPTY && (!entry.stock.isEmpty() || entry.nonce != 0)
                        || entry.state != State.EMPTY && entry.state != State.QUARANTINED
                            && (!oneNeedle(entry.stock) || entry.nonce == 0)) throw new IllegalArgumentException("single_invariant");
                if (entry.state == State.ACTIVE || entry.state == State.STAGED || entry.state == State.ESCROWED) {
                    entry.state = State.RETURN_PENDING;
                    entry.reason = "session_expired";
                } else if (entry.state == State.DEATH_TRANSFER) {
                    entry.state = State.QUARANTINED;
                    entry.reason = "death_transfer_interrupted";
                }
            }
        } catch (RuntimeException invalid) {
            entry.rawCorrupt = true;
            entry.evidence = raw.copy();
            entry.stock = ItemStack.EMPTY;
            entry.volley = null;
            entry.state = State.QUARANTINED;
            entry.reason = "corrupt_escrow";
        }
        return entry; // Inspection alone never rewrites malformed or migrated raw data.
    }

    private static boolean integrity(Entry entry) {
        Tag current = entry.owner.getPersistentData().get(STORAGE_KEY);
        if (entry.expectedRaw == null ? current == null : entry.expectedRaw.equals(current)) return !entry.rawCorrupt;
        entry.evidence = current == null ? null : current.copy();
        entry.rawCorrupt = true;
        entry.state = State.QUARANTINED;
        entry.reason = "persistent_data_changed";
        if (entry.volley != null) {
            entry.volley.live = false;
            for (Slot slot : entry.volley.slots) if (slot.state != State.EMPTY) {
                slot.state = State.QUARANTINED;
                slot.reason = entry.reason;
            }
        }
        revoke(entry);
        return false; // Preserve the actual unexpected raw value, including unknown fields.
    }
    private static boolean strictBoolean(CompoundTag root, String key) {
        return root.contains(key, Tag.TAG_BYTE) && root.getByte(key) >= 0 && root.getByte(key) <= 1;
    }
    private static ItemStack decodeStock(ServerPlayer owner, CompoundTag stock) {
        if (stock.isEmpty()) return ItemStack.EMPTY;
        ItemStack item = ItemStack.parse(owner.registryAccess(), stock).orElseThrow();
        if (!oneNeedle(item) || !stock.equals(item.save(owner.registryAccess()))) throw new IllegalArgumentException("stock");
        return item;
    }
    private static void decodeVolley(Entry entry, CompoundTag root) {
        int count = root.getInt("shot_count"), next = root.getInt("next_shot"), spent = root.getInt("spent");
        String roundText = root.getString("round");
        UUID round = UUID.fromString(roundText);
        long deadline = root.getLong("deadline");
        ListTag slots = (ListTag) root.get("slots");
        if (!round.toString().equals(roundText) || count < VOLLEY_SLOTS || count > MAX_VOLLEY_SHOTS
                || next < 0 || next > count || spent != (1 << next) - 1 || deadline < 0 || deadline > 0xffff_ffffL + MAX_VOLLEY_TICKS
                || slots.getElementType() != Tag.TAG_COMPOUND || slots.size() != VOLLEY_SLOTS
                || !entry.stock.isEmpty() || entry.nonce == 0
                || entry.state != State.ACTIVE && entry.state != State.RETURN_PENDING
                    && entry.state != State.DEATH_TRANSFER && entry.state != State.QUARANTINED)
            throw new IllegalArgumentException("volley_invariant");
        Volley volley = new Volley(entry, round, count, deadline);
        volley.nextShot = next;
        volley.spent = spent;
        Set<Long> generations = new java.util.HashSet<>();
        int retained = 0, transfers = 0;
        for (int i = 0; i < VOLLEY_SLOTS; i++) {
            CompoundTag raw = slots.getCompound(i);
            if (!raw.getAllKeys().equals(SLOT_KEYS) || !raw.contains("index", Tag.TAG_INT) || raw.getInt("index") != i
                    || !raw.contains("state", Tag.TAG_STRING) || !raw.contains("stock", Tag.TAG_COMPOUND)
                    || !raw.contains("reason", Tag.TAG_STRING) || !raw.contains("shot_index", Tag.TAG_INT)
                    || !raw.contains("generation", Tag.TAG_LONG)) throw new IllegalArgumentException("slot_shape");
            Slot slot = volley.slots[i];
            slot.state = State.valueOf(raw.getString("state"));
            slot.stock = decodeStock(entry.owner, raw.getCompound("stock"));
            slot.reason = raw.getString("reason");
            slot.shotIndex = raw.getInt("shot_index");
            slot.generation = raw.getLong("generation");
            if (slot.reason.length() > 256 || slot.shotIndex < -1 || slot.shotIndex >= count || slot.generation < 0
                    || slot.shotIndex == -1 && slot.generation != 0
                    || slot.shotIndex >= 0 && (slot.shotIndex >= next || slot.shotIndex % VOLLEY_SLOTS != i
                        || slot.generation == 0 || !generations.add(slot.generation))
                    || slot.generation > entry.nextGeneration || !entry.generationExhausted && slot.generation >= entry.nextGeneration
                    || slot.state == State.EMPTY && !slot.stock.isEmpty()
                    || slot.state != State.EMPTY && !oneNeedle(slot.stock)
                    || slot.state == State.ESCROWED || slot.state == State.STAGED && slot.shotIndex == -1
                    || slot.state == State.ACTIVE && slot.shotIndex == -1)
                throw new IllegalArgumentException("slot_invariant");
            if (!slot.stock.isEmpty()) retained++;
            if (slot.state == State.DEATH_TRANSFER) transfers++;
            if (entry.state == State.ACTIVE && slot.state != State.BORROWED_IDLE && slot.state != State.STAGED && slot.state != State.ACTIVE
                    || entry.state == State.RETURN_PENDING && slot.state != State.EMPTY && slot.state != State.RETURN_PENDING
                    || entry.state == State.DEATH_TRANSFER && slot.state != State.EMPTY && slot.state != State.RETURN_PENDING && slot.state != State.DEATH_TRANSFER
                    || entry.state == State.QUARANTINED && slot.state != State.EMPTY && slot.state != State.QUARANTINED)
                throw new IllegalArgumentException("slot_global_state");
        }
        if (retained == 0 || entry.state == State.DEATH_TRANSFER && transfers != 1) throw new IllegalArgumentException("retained");
        // No restored session can publish or damage. Safe originals remain manual-claim only.
        if (entry.state == State.ACTIVE) {
            entry.state = State.RETURN_PENDING;
            entry.reason = "session_expired";
            for (Slot slot : volley.slots) { slot.state = State.RETURN_PENDING; slot.reason = entry.reason; }
        } else if (entry.state == State.DEATH_TRANSFER) {
            entry.state = State.QUARANTINED;
            entry.reason = "death_transfer_interrupted";
            for (Slot slot : volley.slots) if (slot.state != State.EMPTY) { slot.state = State.QUARANTINED; slot.reason = entry.reason; }
        }
        entry.volley = volley;
    }
    private static CompoundTag encode(Entry entry, State state, long nonce, ItemStack stock, String reason) {
        return entry.volley == null ? encodeSingle(entry, state, nonce, stock, reason)
                : encodeVolley(entry, entry.volley, state, nonce, reason, entry.nextNonce, entry.exhausted);
    }
    private static CompoundTag encodeSingle(Entry entry, State state, long nonce, ItemStack stock, String reason) {
        CompoundTag root = encodeBase(entry, state, nonce, stock, reason, entry.nextNonce, entry.exhausted);
        root.putString("mode", "single");
        root.put("slots", new ListTag());
        root.putString("round", "");
        root.putInt("shot_count", 0);
        root.putInt("next_shot", 0);
        root.putInt("spent", 0);
        root.putLong("deadline", 0);
        return root;
    }
    private static CompoundTag encodeVolley(Entry entry, Volley volley, State state, long nonce, String reason, long nextNonce, boolean exhausted) {
        CompoundTag root = encodeBase(entry, state, nonce, ItemStack.EMPTY, reason, nextNonce, exhausted);
        root.putString("mode", "volley");
        root.putString("round", volley.round.toString());
        root.putInt("shot_count", volley.shotCount);
        root.putInt("next_shot", volley.nextShot);
        root.putInt("spent", volley.spent);
        root.putLong("deadline", volley.deadline);
        ListTag slots = new ListTag();
        for (Slot slot : volley.slots) {
            CompoundTag tag = new CompoundTag();
            tag.putInt("index", slot.index);
            tag.putString("state", slot.state.name());
            tag.put("stock", slot.stock.isEmpty() ? new CompoundTag() : slot.stock.save(entry.owner.registryAccess()));
            tag.putString("reason", slot.reason);
            tag.putInt("shot_index", slot.shotIndex);
            tag.putLong("generation", slot.generation);
            slots.add(tag);
        }
        root.put("slots", slots);
        return root;
    }
    private static CompoundTag encodeBase(Entry entry, State state, long nonce, ItemStack stock, String reason, long nextNonce, boolean exhausted) {
        CompoundTag root = new CompoundTag();
        root.putInt("version", VERSION);
        root.putString("owner", entry.owner.getUUID().toString());
        root.putLong("next_nonce", nextNonce);
        root.putBoolean("exhausted", exhausted);
        root.putString("state", state.name());
        root.putLong("nonce", nonce);
        root.put("stock", stock.isEmpty() ? new CompoundTag() : stock.save(entry.owner.registryAccess()));
        root.putString("reason", reason);
        root.putLong("next_generation", entry.nextGeneration);
        root.putBoolean("generation_exhausted", entry.generationExhausted);
        return root;
    }
    private static void persist(Entry entry) {
        if (!entry.rawCorrupt) write(entry, encode(entry, entry.state, entry.nonce, entry.stock, entry.reason));
    }
    private static void write(Entry entry, CompoundTag root) {
        entry.owner.getPersistentData().put(STORAGE_KEY, root);
        entry.expectedRaw = root.copy();
    }
    private static boolean oneNeedle(ItemStack stack) { return plainStack(stack) && stack.getCount() == 1; }
    private static boolean plainStack(ItemStack stack) {
        return stack != null && !stack.isEmpty() && stack.getCount() > 0 && stack.getCount() <= stack.getMaxStackSize()
                && stack.is(AcademyCraft.NEEDLE.get()) && stack.isComponentsPatchEmpty();
    }
    private static boolean finite(Vec3 point) {
        return point != null && Double.isFinite(point.x) && Double.isFinite(point.y) && Double.isFinite(point.z);
    }
    private static boolean loadedDestination(ServerLevel level, AABB bounds) {
        if (!Double.isFinite(bounds.minX) || !Double.isFinite(bounds.minY) || !Double.isFinite(bounds.minZ)
                || !Double.isFinite(bounds.maxX) || !Double.isFinite(bounds.maxY) || !Double.isFinite(bounds.maxZ)
                || bounds.minY < level.getMinBuildHeight() || bounds.maxY > level.getMaxBuildHeight()
                || !level.getWorldBorder().isWithinBounds(bounds)) return false;
        int minX = Mth.floor(bounds.minX) >> 4, maxX = Mth.floor(bounds.maxX) >> 4;
        int minZ = Mth.floor(bounds.minZ) >> 4, maxZ = Mth.floor(bounds.maxZ) >> 4;
        if (maxX - minX > 1 || maxZ - minZ > 1) return false;
        for (int x = minX; x <= maxX; x++) for (int z = minZ; z <= maxZ; z++)
            if (level.getChunkSource().getChunkNow(x, z) == null) return false;
        return true;
    }
    private static boolean currentOwner(ServerPlayer owner) {
        return !owner.isRemoved() && !owner.hasDisconnected() && owner.connection != null
                && owner.getServer().getPlayerList().getPlayer(owner.getUUID()) == owner;
    }
    private static boolean validOwner(@Nullable ServerPlayer owner, @Nullable ServerLevel expected) {
        return owner != null && owner.getServer() != null && owner.getServer().isSameThread()
                && currentOwner(owner) && owner.isAlive() && !owner.isSpectator()
                && owner.getInventory().getClass() == Inventory.class
                && (expected == null || owner.serverLevel() == expected);
    }
    private static void requireThread(ServerPlayer owner) {
        if (owner == null || owner.getServer() == null || !owner.getServer().isSameThread())
            throw new IllegalStateException("Borrowed needle custody is server-thread only");
    }
}
