package cn.academy.port.highpower;

import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.ScatterPlateShares;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** Persistent allocations of the existing base shield, never another resource pool. */
public final class ScatterPlateStorage {
    public static final String KEY = "scatterPlateShares";
    public static final String INITIALIZED_KEY = "scatterPlateSharesInitialized";
    public static final String INVALIDATED_KEY = "scatterPlateSharesUnavailable";
    private static final String[] FIELDS = {"version", "shield", "maxShield", "plate0", "plate1", "plate2"};

    private ScatterPlateStorage() {}

    /** Read-only even when learning/category access is suspended. */
    public static Optional<ScatterPlateShares> read(ServerPlayer player) {
        if (!serverThread(player)) return Optional.empty();
        return HighPowerCombat.exactStateSnapshot(player).flatMap(base -> saved(player, base.shield(), base.maxShield()));
    }

    /** First learning/migration only. Relearning and reactivation retain the exact independent currents. */
    public static Optional<ScatterPlateShares> ensure(ServerPlayer player) {
        if (!serverThread(player)) return Optional.empty();
        var optional = HighPowerCombat.exactState(player);
        if (optional.isEmpty()) return Optional.empty();
        ExactCombatState base = optional.get();
        ZeroGapArmorStorage.guardParentWrite(player);
        CompoundTag combat = combat(player);
        if (present(combat)) return saved(player, base.shield(), base.maxShield());
        ScatterPlateShares shares = ScatterPlateShares.initialize(base.shield(), base.maxShield());
        store(player, shares);
        return Optional.of(shares);
    }

    private static boolean present(CompoundTag combat) {
        return combat.contains(KEY) || combat.contains(INITIALIZED_KEY) || combat.contains(INVALIDATED_KEY);
    }

    private static Optional<ScatterPlateShares> saved(LivingEntity entity, BigDecimal current, BigDecimal maximum) {
        CompoundTag combat = combat(entity);
        if (combat.contains(INVALIDATED_KEY) || !combat.contains(INITIALIZED_KEY, Tag.TAG_BYTE)
                || !combat.getBoolean(INITIALIZED_KEY) || !combat.contains(KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag data = combat.getCompound(KEY);
        Map<String, String> values = new HashMap<>();
        for (String key : FIELDS) {
            if (!data.contains(key, Tag.TAG_STRING)) return Optional.empty();
            values.put(key, data.getString(key));
        }
        try { return Optional.of(ScatterPlateShares.fromDecimalMap(values).requireMatches(current, maximum)); }
        catch (IllegalArgumentException | IllegalStateException invalid) { return Optional.empty(); }
    }

    /** A healthy allocation may veto a capacity reduction that cannot contain one of its currents. */
    static boolean canChangeCapacity(LivingEntity entity, ExactCombatState before, ExactCombatState after) {
        var shares = saved(entity, before.shield(), before.maxShield());
        if (shares.isEmpty()) return true; // Optional corrupt metadata cannot block ordinary progression.
        try { shares.get().withMaximum(after.maxShield()); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }

    /**
     * Runs before EVERY ordinary base write, even while NEW212 is unlearned or in another category.
     * Read the old authoritative amounts directly: growth metadata may already carry the next cap.
     * Only the actual base delta is distributed; a zero delta never repairs an exhausted plate.
     */
    record BaseChange(ScatterPlateShares before, ScatterPlateShares after) {}
    static BaseChange baseChanged(LivingEntity entity, ExactCombatState after) {
        ZeroGapArmorStorage.guardParentWrite(entity);
        if (ZeroGapArmorStorage.read(entity).isPresent()) {
            BaseChange change = planBaseChange(entity, after);
            HighPowerCombat.commitResources(entity, after, null, null);
            return change;
        }
        BaseChange change;
        try { change = planBaseChange(entity, after); }
        catch (IllegalArgumentException | IllegalStateException invalid) {
            // Preserve the legacy parent-only write and keep malformed allocations inert.
            combat(entity).putBoolean(INVALIDATED_KEY, true);
            return null;
        }
        if (change != null) store(entity, change.after());
        return change;
    }

    /** Detached parent calculation; the shared commit owns serialization and invalidation. */
    static BaseChange planBaseChange(LivingEntity entity, ExactCombatState after) {
        CompoundTag combat = combat(entity);
        if (!present(combat) || combat.contains(INVALIDATED_KEY)) return null;
        if (!combat.contains("shield", Tag.TAG_STRING) || !combat.contains("maxShield", Tag.TAG_STRING))
            throw new IllegalArgumentException("Missing prior base amounts");
        BigDecimal current = CombatAmounts.parse(combat.getString("shield"), "prior base shield");
        BigDecimal maximum = CombatAmounts.parse(combat.getString("maxShield"), "prior maximum base shield");
        ScatterPlateShares before = saved(entity, current, maximum).orElseThrow(
                () -> new IllegalArgumentException("Invalid plate allocation"));
        ScatterPlateShares afterShares = before.withMaximum(after.maxShield());
        afterShares = after.shield().compareTo(current) < 0 ? afterShares.afterBaseSpend(after.shield())
                : afterShares.afterBaseRecovery(after.shield());
        return new BaseChange(before, afterShares.requireMatches(after.shield(), after.maxShield()));
    }

    record Contact(ExactCombatState state, ScatterPlateShares shares, BigDecimal absorbed, boolean broken) {}

    /** Called only with a closed controller capture after native mitigation and final absorption. */
    static Contact plan(ServerPlayer player, ExactCombatState base, int index, BigDecimal damage, BigDecimal ceiling) {
        var saved = saved(player, base.shield(), base.maxShield());
        if (saved.isEmpty() || base.isDead()) return null;
        try {
            var contact = saved.get().contact(index, damage, ceiling);
            ExactCombatState after = new ExactCombatState(base.health(), base.maxHealth(), contact.shares().shield(),
                    base.maxShield(), false).applyMitigatedDamage(contact.residual()).state();
            return new Contact(after, contact.shares(), contact.absorbed(),
                    contact.absorbed().signum() > 0 && contact.shares().plate(index).current().signum() == 0);
        } catch (IllegalArgumentException | IllegalStateException invalid) { return null; }
    }

    /** Atomic contact override: HighPowerCombat writes this and its exact base before any callback. */
    static void commit(LivingEntity entity, ExactCombatState base, ScatterPlateShares shares) {
        ZeroGapArmorStorage.guardParentWrite(entity);
        shares = shares.requireMatches(base.shield(), base.maxShield());
        if (ZeroGapArmorStorage.read(entity).isPresent()) {
            HighPowerCombat.commitResources(entity, base, shares, null);
            return;
        }
        store(entity, shares);
    }

    private static void store(LivingEntity entity, ScatterPlateShares shares) {
        ZeroGapArmorStorage.guardParentWrite(entity);
        CompoundTag data = serialize(shares);
        CompoundTag combat = combat(entity);
        combat.put(KEY, data);
        combat.putBoolean(INITIALIZED_KEY, true);
    }

    static CompoundTag serialize(ScatterPlateShares shares) {
        CompoundTag data = new CompoundTag();
        shares.toDecimalMap().forEach(data::putString);
        return data;
    }

    private static CompoundTag combat(LivingEntity entity) {
        return entity.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
    }
    private static boolean serverThread(ServerPlayer player) {
        return player != null && !player.level().isClientSide && player.getServer() != null && player.getServer().isSameThread();
    }
}
