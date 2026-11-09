package cn.academy.port.highpower;

import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.ScatterPlateShares;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
import cn.academy.port.highpower.core.ZeroGapArmorShares;
import cn.academy.port.highpower.core.ZeroGapArmorTiming;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * NEW252 persistence boundary shared by the existing exact resource writers.
 * Callers capture fresh parents, prepare a complete proposal, apply it, then
 * notify their observers. NEW252 activation and contacts remain unregistered.
 * Apply has no caller callbacks, reconciliation, serialization or rollback.
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "academy")
public final class ZeroGapArmorStorage {
    public static final String KEY = "zeroGapArmorShares";
    public static final String TIMING_KEY = "zeroGapArmorTiming";
    public static final String INITIALIZED_KEY = "zeroGapArmorSharesInitialized";
    public static final String INVALIDATED_KEY = "zeroGapArmorSharesUnavailable";
    private static final String RESERVE_UNAVAILABLE = "magneticDomainReserveUnavailable";
    private static final Set<String> BASE_FIELDS = Set.of("version", "health", "maxHealth", "shield", "maxShield", "shieldEnabled");
    private static final Set<String> SCATTER_FIELDS = Set.of("version", "shield", "maxShield", "plate0", "plate1", "plate2");
    private static final Set<String> TIMING_FIELDS = Set.of("version", "phaseTick", "breakTicks");
    private static final Set<LivingEntity> APPLYING = Collections.newSetFromMap(new IdentityHashMap<>());

    private ZeroGapArmorStorage() {}

    /** Opaque immutable capture; live tags and detached snapshots are never exposed. */
    public static final class Capture {
        private final LivingEntity owner;
        private final UUID ownerUuid;
        private final int ownerId;
        private final ServerLevel level;
        private final MinecraftServer server;
        private final Thread thread;
        private final CompoundTag source;
        private final CompoundTag snapshot;
        private final ExactCombatState base;
        private final ScatterPlateShares scatter;
        private final SharedShieldReserveShares reserves;
        private final ZeroGapArmorShares armor;
        private final ZeroGapArmorTiming timing;

        private Capture(LivingEntity owner, CompoundTag source, CompoundTag snapshot,
                        ExactCombatState base, ScatterPlateShares scatter, SharedShieldReserveShares reserves,
                        ZeroGapArmorShares armor, ZeroGapArmorTiming timing) {
            this.owner = owner;
            this.ownerUuid = owner.getUUID();
            this.ownerId = owner.getId();
            this.level = (ServerLevel) owner.level();
            this.server = level.getServer();
            this.thread = Thread.currentThread();
            this.source = source;
            this.snapshot = snapshot;
            this.base = base;
            this.scatter = scatter;
            this.reserves = reserves;
            this.armor = armor;
            this.timing = timing;
        }

        public ExactCombatState base() { return base; }
        public ScatterPlateShares scatter() { return scatter; }
        public SharedShieldReserveShares reserves() { return reserves; }
        public Optional<ZeroGapArmorShares> armor() { return Optional.ofNullable(armor); }
        public Optional<ZeroGapArmorTiming> timing() { return Optional.ofNullable(timing); }
    }

    private record Write(String key, Tag value) {}
    private record NestedWrite(String compound, String key, Tag value) {}

    /** Every value here is already validated and serialized, detached from live NBT. */
    public static final class Prepared {
        private final Capture captured;
        private final List<Write> writes;
        private final List<NestedWrite> nestedWrites;
        private final HighPowerCombat.GrowthChange growth;
        private final ZeroGapArmorShares after;
        private final ZeroGapArmorTiming timing;
        private final List<Integer> brokenPlateIds;
        private boolean attempted;

        private Prepared(Capture captured, List<Write> writes, ZeroGapArmorShares after,
                         ZeroGapArmorTiming timing, List<Integer> brokenPlateIds, List<NestedWrite> nestedWrites,
                         HighPowerCombat.GrowthChange growth) {
            this.captured = captured;
            this.writes = List.copyOf(writes);
            this.nestedWrites = List.copyOf(nestedWrites);
            this.growth = growth;
            this.after = after;
            this.timing = timing;
            this.brokenPlateIds = List.copyOf(brokenPlateIds);
        }

        public ZeroGapArmorShares after() { return after; }
        public ZeroGapArmorTiming timing() { return timing; }
        public List<Integer> brokenPlateIds() { return brokenPlateIds; }
    }

    /**
     * Read-only, including failures. An existing NEW212 allocation is required;
     * its missing/corrupt metadata is never repaired by NEW252. The reserve's
     * existing absent/v1 logical migration convention is retained without writes.
     */
    public static Optional<Capture> capture(LivingEntity owner) {
        if (!serverThread(owner) || APPLYING.contains(owner)) return Optional.empty();
        var exact = HighPowerCombat.exactStateSnapshot(owner);
        if (exact.isEmpty()) return Optional.empty();
        CompoundTag source = owner.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        CompoundTag snapshot = source.copy();
        try {
            ExactCombatState base = ExactCombatState.fromDecimalMap(strings(snapshot, BASE_FIELDS, false));
            if (!base.equals(exact.get()) || base.shieldEnabled()) return Optional.empty();
            ScatterPlateShares scatter = readScatter(snapshot, base);
            SharedShieldReserveShares reserves = readReserves(snapshot, base);
            ZeroGapArmorShares armor = null;
            ZeroGapArmorTiming timing = null;
            if (present(snapshot)) {
                if (snapshot.contains(INVALIDATED_KEY) || !trueFlag(snapshot, INITIALIZED_KEY)
                        || !snapshot.contains(KEY, Tag.TAG_COMPOUND) || !snapshot.contains(TIMING_KEY, Tag.TAG_COMPOUND))
                    return Optional.empty();
                armor = ZeroGapArmorShares.fromDecimalMap(base, scatter, reserves,
                        allStrings(snapshot.getCompound(KEY)));
                timing = ZeroGapArmorTiming.fromDecimalMap(strings(snapshot.getCompound(TIMING_KEY), TIMING_FIELDS, true));
            }
            return Optional.of(new Capture(owner, source, snapshot, base, scatter, reserves, armor, timing));
        } catch (IllegalArgumentException | IllegalStateException invalid) { return Optional.empty(); }
    }

    /** A read, retoggle, reload or ordinary transaction never initializes metadata. */
    public static Optional<ZeroGapArmorShares> read(LivingEntity owner) {
        return capture(owner).flatMap(Capture::armor);
    }

    /** Invoked only after real learning; missing initialized metadata is never repaired. */
    public static boolean ensureLearned(net.minecraft.server.level.ServerPlayer owner) {
        var captured = capture(owner);
        if (captured.isEmpty()) return false;
        if (captured.get().armor().isPresent()) return true;
        try { return apply(prepareInitialization(captured.get())); }
        catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
    }
    private static final Map<MinecraftServer, Integer> LAST_NATIVE_TICK = new java.util.WeakHashMap<>();
    /** One genuine server boundary per connected owner; independent of world time and ability category. */
    @net.neoforged.bus.api.SubscribeEvent(priority = net.neoforged.bus.api.EventPriority.HIGHEST)
    public static void nativeTick(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre event) {
        MinecraftServer server = event.getServer();
        if (!server.isSameThread()) return;
        int tick = server.getTickCount();
        if (Objects.equals(LAST_NATIVE_TICK.put(server, tick), tick)) return;
        for (var owner : server.getPlayerList().getPlayers()) {
            if (owner.server != server || !hasMetadata(owner)) continue;
            var captured = capture(owner);
            if (captured.isPresent() && captured.get().armor().isPresent()) {
                try { apply(prepareNativeTick(captured.get())); }
                catch (IllegalArgumentException | IllegalStateException invalid) { guardParentWrite(owner); }
            }
        }
    }

    /** Explicit one-time migration only, preserving each existing parent balance. */
    public static Prepared prepareInitialization(Capture captured) {
        requireFresh(captured);
        if (present(captured.snapshot)) throw new IllegalArgumentException("NEW252 was already initialized or is unavailable");
        ZeroGapArmorShares after = ZeroGapArmorShares.initialize(captured.base, captured.scatter, captured.reserves);
        return stage(captured, after, ZeroGapArmorTiming.INITIAL, List.of(), after.toDecimalMap());
    }

    /**
     * Accepts an exact closed math proposal. Explicit NEW212 contacts must supply
     * their already-changed B_i parents to reconcileParents once; they are never
     * converted into an additional ordinary base spend by this storage layer.
     * Ordinary proposals cannot change capacities. Only the closed combat
     * lifecycle can also supply the matching certified q/Q metadata draft.
     */
    public static Prepared prepare(Capture captured, ZeroGapArmorShares.Transaction transaction) {
        return prepare(captured, transaction, null);
    }

    static Prepared prepare(Capture captured, ZeroGapArmorShares.Transaction transaction,
                            HighPowerCombat.GrowthChange growth) {
        requireFresh(captured);
        Objects.requireNonNull(transaction, "transaction");
        if (captured.armor == null) throw new IllegalArgumentException("NEW252 has not been initialized");
        transaction.before().requireMatches(captured.base, captured.scatter, captured.reserves);
        if (!transaction.before().toDecimalMap().equals(captured.armor.toDecimalMap()))
            throw new IllegalArgumentException("Stale NEW252 child currents");
        ZeroGapArmorTiming timing = transaction.brokenPlateIds().isEmpty()
                ? captured.timing : captured.timing.brokenNow();
        return stage(captured, transaction.after(), timing, transaction.brokenPlateIds(), transaction.after().toDecimalMap(), growth);
    }

    /** Future adapter must invoke exactly once per genuine native server tick, before damage. */
    public static Prepared prepareNativeTick(Capture captured) {
        requireFresh(captured);
        if (captured.armor == null) throw new IllegalArgumentException("NEW252 has not been initialized");
        return stage(captured, captured.armor, captured.timing.advanceOneNativeTick(), List.of(), captured.armor.toDecimalMap());
    }

    /**
     * Two-phase boundary: finish ALL codecs, validation and detached allocation
     * before publishing a Prepared. The encoded argument is private so tests can
     * inject a final-field codec failure without exposing a runtime mutation seam.
     */
    private static Prepared stage(Capture captured, ZeroGapArmorShares after, ZeroGapArmorTiming timing,
                                  List<Integer> brokenPlateIds, Map<String, String> encodedArmor) {
        return stage(captured, after, timing, brokenPlateIds, encodedArmor, null);
    }

    private static Prepared stage(Capture captured, ZeroGapArmorShares after, ZeroGapArmorTiming timing,
                                  List<Integer> brokenPlateIds, Map<String, String> encodedArmor,
                                  HighPowerCombat.GrowthChange growth) {
        requireFresh(captured);
        if (after.base().shieldEnabled()) throw new IllegalArgumentException("NEW252 must not enable generic shield absorption");
        if (growth != null) growth.validate(captured.owner, captured.base, after.base());
        if (growth == null && (after.base().maxHealth().compareTo(captured.base.maxHealth()) != 0
                || after.base().maxShield().compareTo(captured.base.maxShield()) != 0))
            throw new IllegalArgumentException("Capacity changes require an atomic earned-growth metadata draft");
        after.requireMatches(after.base(), after.scatter(), after.reserves());
        CompoundTag base = decimalTag(after.base().toDecimalMap());
        CompoundTag scatter = decimalTag(after.scatter().toDecimalMap());
        Map<String, String> reserveFields = after.reserves().toDecimalMap(after.base());
        CompoundTag reserve = decimalTag(reserveFields);
        reserve.putInt("version", MagneticDomainReserve.VERSION);
        CompoundTag children = decimalTag(encodedArmor);
        CompoundTag clock = decimalTag(timing.toDecimalMap());

        // Validate what will actually be persisted, including the last child.
        ExactCombatState savedBase = ExactCombatState.fromDecimalMap(strings(base, BASE_FIELDS, true));
        ScatterPlateShares savedScatter = ScatterPlateShares.fromDecimalMap(strings(scatter, SCATTER_FIELDS, true));
        savedScatter.requireMatches(savedBase.shield(), savedBase.maxShield());
        SharedShieldReserveShares savedReserve = reservePayload(reserve, savedBase);
        ZeroGapArmorShares savedArmor = ZeroGapArmorShares.fromDecimalMap(savedBase, savedScatter, savedReserve, allStrings(children));
        if (!savedArmor.toDecimalMap().equals(after.toDecimalMap()))
            throw new IllegalArgumentException("Serialized NEW252 draft differs from proposal");
        if (!ZeroGapArmorTiming.fromDecimalMap(strings(clock, TIMING_FIELDS, true)).equals(timing))
            throw new IllegalArgumentException("Serialized NEW252 timing differs from proposal");

        List<Write> writes = new ArrayList<>();
        for (String key : BASE_FIELDS) writes.add(new Write(key, base.get(key)));
        writes.add(new Write(ScatterPlateStorage.KEY, scatter));
        writes.add(new Write(MagneticDomainReserve.KEY, reserve));
        writes.add(new Write(KEY, children));
        writes.add(new Write(TIMING_KEY, clock));
        CompoundTag markers = new CompoundTag();
        markers.putBoolean(ScatterPlateStorage.INITIALIZED_KEY, true);
        markers.putBoolean(INITIALIZED_KEY, true);
        writes.add(new Write(ScatterPlateStorage.INITIALIZED_KEY, markers.get(ScatterPlateStorage.INITIALIZED_KEY)));
        writes.add(new Write(INITIALIZED_KEY, markers.get(INITIALIZED_KEY)));
        List<NestedWrite> nestedWrites = new ArrayList<>();
        if (growth != null) {
            CompoundTag metadata = growth.metadata();
            for (String key : metadata.getAllKeys()) writes.add(new Write(key, metadata.get(key)));
            CompoundTag active = growth.activeMetadata();
            if (active != null) {
                if (captured.source.contains("activeGrowth", Tag.TAG_COMPOUND)) {
                    for (String key : active.getAllKeys())
                        nestedWrites.add(new NestedWrite("activeGrowth", key, active.get(key)));
                } else writes.add(new Write("activeGrowth", active));
            }
        }
        return new Prepared(captured, writes, savedArmor, timing, brokenPlateIds, nestedWrites, growth);
    }

    /**
     * Callback-free server-thread apply. Failure is before the first write.
     * Identity and the COMPLETE captured source are checked, so parent changes,
     * source replacement and unrelated intervening metadata changes all refuse.
     * The live combat compound and unrelated nested tags retain their identities.
     * Once this returns true, callers may notify; this method never writes again.
     */
    public static boolean apply(Prepared prepared) {
        if (prepared == null || !serverThread(prepared.captured.owner) || prepared.attempted) return false;
        Capture captured = prepared.captured;
        if (!APPLYING.add(captured.owner)) return false;
        try {
            prepared.attempted = true;
            if (!freshSource(captured)) return false;
            if (prepared.growth != null) {
                try { prepared.growth.validate(captured.owner, captured.base, prepared.after.base()); }
                catch (IllegalArgumentException | IllegalStateException invalid) { return false; }
            }
            for (Write write : prepared.writes)
                if (!Objects.equals(captured.source.get(write.key()), write.value()))
                    captured.source.put(write.key(), write.value());
            for (NestedWrite write : prepared.nestedWrites)
                captured.source.getCompound(write.compound()).put(write.key(), write.value());
            return true;
        } finally { APPLYING.remove(captured.owner); }
    }

    private static void requireFresh(Capture captured) {
        Objects.requireNonNull(captured, "capture");
        if (!serverThread(captured.owner) || APPLYING.contains(captured.owner) || !freshSource(captured))
            throw new IllegalStateException("Stale, off-thread or reentrant NEW252 capture");
    }

    private static boolean freshSource(Capture captured) {
        return Thread.currentThread() == captured.thread
                && captured.owner.getUUID().equals(captured.ownerUuid) && captured.owner.getId() == captured.ownerId
                && captured.owner.level() == captured.level && captured.owner.getServer() == captured.server
                && captured.owner.getPersistentData().get(HighPowerCombat.DATA_KEY) == captured.source
                && captured.source.equals(captured.snapshot);
    }

    static boolean hasMetadata(LivingEntity owner) {
        return owner != null && present(owner.getPersistentData().getCompound(HighPowerCombat.DATA_KEY));
    }

    /** Called before any legacy parent migration/write; retain every original payload byte. */
    static void guardParentWrite(LivingEntity owner) {
        if (!serverThread(owner) || APPLYING.contains(owner))
            throw new IllegalStateException("Off-thread or reentrant resource write");
        if (hasMetadata(owner) && capture(owner).isEmpty()) latchUnavailable(owner);
    }

    static void latchUnavailable(LivingEntity owner) {
        if (!serverThread(owner) || APPLYING.contains(owner))
            throw new IllegalStateException("Off-thread or reentrant resource invalidation");
        CompoundTag combat = owner.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        if (present(combat) && !combat.contains(INVALIDATED_KEY)) combat.putBoolean(INVALIDATED_KEY, true);
    }

    private static boolean present(CompoundTag combat) {
        return combat.contains(KEY) || combat.contains(TIMING_KEY)
                || combat.contains(INITIALIZED_KEY) || combat.contains(INVALIDATED_KEY);
    }

    private static boolean trueFlag(CompoundTag data, String key) {
        return data.contains(key, Tag.TAG_BYTE) && data.getByte(key) == 1;
    }

    private static ScatterPlateShares readScatter(CompoundTag data, ExactCombatState base) {
        if (data.contains(ScatterPlateStorage.INVALIDATED_KEY)
                || !trueFlag(data, ScatterPlateStorage.INITIALIZED_KEY)
                || !data.contains(ScatterPlateStorage.KEY, Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Unavailable canonical NEW212 parents");
        return ScatterPlateShares.fromDecimalMap(strings(data.getCompound(ScatterPlateStorage.KEY), SCATTER_FIELDS, true))
                .requireMatches(base.shield(), base.maxShield());
    }

    private static SharedShieldReserveShares readReserves(CompoundTag data, ExactCombatState base) {
        if (data.contains(RESERVE_UNAVAILABLE)) throw new IllegalArgumentException("Unavailable reserve parents");
        // Initialization always persists v2, including zero. Missing/downgraded
        // parents afterwards cannot masquerade as a fresh zero/v1 migration.
        if (present(data) && (!data.contains(MagneticDomainReserve.KEY, Tag.TAG_COMPOUND)
                || !data.getCompound(MagneticDomainReserve.KEY).contains("version", Tag.TAG_INT)
                || data.getCompound(MagneticDomainReserve.KEY).getInt("version") != MagneticDomainReserve.VERSION))
            throw new IllegalArgumentException("Initialized NEW252 requires canonical reserve halves");
        if (!data.contains(MagneticDomainReserve.KEY)) return SharedShieldReserveShares.ZERO.validate(base);
        if (!data.contains(MagneticDomainReserve.KEY, Tag.TAG_COMPOUND))
            throw new IllegalArgumentException("Invalid reserve parents");
        return reservePayload(data.getCompound(MagneticDomainReserve.KEY), base);
    }

    private static SharedShieldReserveShares reservePayload(CompoundTag reserve, ExactCombatState base) {
        if (!reserve.contains("version", Tag.TAG_INT)) throw new IllegalArgumentException("Missing reserve version");
        int version = reserve.getInt("version");
        Set<String> fields = switch (version) {
            case 1 -> Set.of("version", "amount", "capacity");
            case MagneticDomainReserve.VERSION -> Set.of("version", "amount", "capacity", "lower", "upper");
            default -> throw new IllegalArgumentException("Unsupported reserve schema");
        };
        if (!reserve.getAllKeys().equals(fields)) throw new IllegalArgumentException("Invalid reserve state fields");
        Map<String, String> values = new HashMap<>();
        for (String field : fields) {
            if (field.equals("version")) values.put(field, Integer.toString(version));
            else {
                if (!reserve.contains(field, Tag.TAG_STRING)) throw new IllegalArgumentException("Missing reserve field " + field);
                values.put(field, reserve.getString(field));
            }
        }
        return SharedShieldReserveShares.fromDecimalMap(base, values);
    }

    private static Map<String, String> strings(CompoundTag data, Set<String> fields, boolean exactFields) {
        if (exactFields && !data.getAllKeys().equals(fields)) throw new IllegalArgumentException("Invalid state fields");
        Map<String, String> values = new HashMap<>();
        for (String field : fields) {
            if (!data.contains(field, Tag.TAG_STRING)) throw new IllegalArgumentException("Missing decimal field " + field);
            values.put(field, data.getString(field));
        }
        return values;
    }

    private static Map<String, String> allStrings(CompoundTag data) {
        return strings(data, data.getAllKeys(), true);
    }

    private static CompoundTag decimalTag(Map<String, String> values) {
        CompoundTag result = new CompoundTag();
        values.forEach((key, value) -> result.putString(Objects.requireNonNull(key), Objects.requireNonNull(value)));
        return result;
    }

    private static boolean serverThread(LivingEntity owner) {
        return owner != null && owner.level() instanceof ServerLevel level && level.getServer().isSameThread();
    }
}
