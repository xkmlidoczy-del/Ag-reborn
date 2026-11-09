package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.MagneticDomainShieldRules;
import cn.academy.port.highpower.core.SharedShieldReserveShares;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Optional;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;

/**
 * Server-only shared reserve. NEW152 sees both fixed shares; NEW242 sees only the lower one.
 * Absence is a valid zero migration, malformed/future schemas remain inert and untouched.
 * Category/learning changes suspend access without deleting injury or stored resources.
 */
public final class MagneticDomainReserve {
    public static final String KEY = "magneticDomainReserve";
    private static final String INVALIDATED_KEY = "magneticDomainReserveUnavailable";
    public static final int VERSION = 2;
    private MagneticDomainReserve() {}

    public static boolean available(ServerPlayer player) {
        return eligible(player) && HighPowerCombat.exactState(player).flatMap(base -> saved(player, base)).isPresent();
    }
    public static BigDecimal reserve(ServerPlayer player) {
        if (!eligible(player)) return BigDecimal.ZERO;
        return HighPowerCombat.exactState(player).flatMap(base -> saved(player, base)).orElse(BigDecimal.ZERO);
    }
    public static BigDecimal capacity(ServerPlayer player) {
        if (!available(player)) return BigDecimal.ZERO;
        return HighPowerCombat.exactState(player).map(ExactCombatState::maxShield).orElse(BigDecimal.ZERO);
    }
    public static BigDecimal total(ServerPlayer player) { return HighPowerCombat.shield(player).add(reserve(player)); }

    /** Exactly one percent of certified base S, paid once by the admitted arrow's first positive turn. */
    public static boolean spendRefraction(ServerPlayer player) {
        if (!eligible(player) || !MagneticDomain.active(player)) return false;
        return HighPowerCombat.spendDomainRefraction(player);
    }
    /** The caller's final context/arrow validation runs before any ledger read or mutation. */
    public static boolean debitArrow(ServerPlayer player, BigDecimal amount, java.util.function.BooleanSupplier exactValid) {
        if (player == null || amount == null || exactValid == null || !eligible(player)) return false;
        try { if (!exactValid.getAsBoolean()) return false; }
        catch (RuntimeException invalid) { return false; }
        var base = HighPowerCombat.exactState(player);
        if (base.isEmpty() || amount.compareTo(base.get().maxShield().divide(BigDecimal.valueOf(100))) != 0) return false;
        return spendRefraction(player);
    }

    private static boolean eligible(ServerPlayer player) {
        if (player == null || player.level().isClientSide || !player.serverLevel().getServer().isSameThread()) return false;
        var ability = AbilityStorage.get(player);
        return HighPowerSkills.CATEGORY.equals(ability.category) && ability.level == 5
                && ability.learned(HighPowerSkills.MAGNETIC_DOMAIN)
                && SkillCatalog.enabled(HighPowerSkills.CATEGORY, HighPowerSkills.MAGNETIC_DOMAIN)
                && HighPowerCombat.earnedLevel(player) == 5;
    }

    /** Reads capacity against the independently validated base ledger, including while access is suspended. */
    static Optional<BigDecimal> saved(LivingEntity entity, ExactCombatState base) {
        return savedShares(entity, base).map(SharedShieldReserveShares::total);
    }

    /** Pure logical read: observer/graze queries never rewrite even a valid legacy payload. */
    static Optional<SharedShieldReserveShares> savedShares(LivingEntity entity, ExactCombatState base) {
        CompoundTag combat = entity.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        if (combat.contains(INVALIDATED_KEY)) return Optional.empty();
        if (!combat.contains(KEY)) {
            try { return Optional.of(SharedShieldReserveShares.ZERO.validate(base)); }
            catch (IllegalArgumentException invalid) { return Optional.empty(); }
        }
        if (!combat.contains(KEY, Tag.TAG_COMPOUND)) return Optional.empty();
        CompoundTag reserve = combat.getCompound(KEY);
        if (!reserve.contains("version", Tag.TAG_INT)
                || reserve.getInt("version") != 1 && reserve.getInt("version") != VERSION
                || !reserve.contains("amount", Tag.TAG_STRING) || !reserve.contains("capacity", Tag.TAG_STRING)) return Optional.empty();
        if (reserve.getInt("version") == VERSION
                && (!reserve.contains("lower", Tag.TAG_STRING) || !reserve.contains("upper", Tag.TAG_STRING))) return Optional.empty();
        try {
            var fields = new HashMap<String, String>();
            fields.put("version", Integer.toString(reserve.getInt("version")));
            fields.put("amount", reserve.getString("amount"));
            fields.put("capacity", reserve.getString("capacity"));
            if (reserve.getInt("version") == VERSION) {
                fields.put("lower", reserve.getString("lower"));
                fields.put("upper", reserve.getString("upper"));
            }
            SharedShieldReserveShares shares = SharedShieldReserveShares.fromDecimalMap(base, fields);
            return Optional.of(shares);
        } catch (IllegalArgumentException invalid) { return Optional.empty(); }
    }

    /** Explicit paid-context setup boundary; never call from observers, crossing or active queries. */
    static boolean ensureMigrated(ServerPlayer player) {
        if (player == null || player.level().isClientSide || !player.serverLevel().getServer().isSameThread()) return false;
        return HighPowerCombat.exactState(player).map(base -> ensureMigrated(player, base)).orElse(false);
    }

    /** Explicit native resource lifecycle boundary. Invalid/future payloads remain byte-for-byte intact. */
    static boolean ensureMigrated(LivingEntity entity, ExactCombatState base) {
        if (entity == null || !(entity.level() instanceof ServerLevel level) || !level.getServer().isSameThread()) return false;
        ZeroGapArmorStorage.guardParentWrite(entity);
        var shares = savedShares(entity, base);
        if (shares.isEmpty()) return false;
        CompoundTag combat = entity.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        if (combat.contains(KEY, Tag.TAG_COMPOUND) && combat.getCompound(KEY).getInt("version") == 1)
            writeShares(entity, base, shares.get());
        return true;
    }

    static MagneticDomainShieldRules.Result plan(ServerPlayer player, ExactCombatState base, BigDecimal damage) {
        if (!eligible(player)) return null;
        return saved(player, base).map(amount -> MagneticDomainShieldRules.absorb(base, amount, damage)).orElse(null);
    }
    static MagneticDomainShieldRules.Recovery recover(ServerPlayer player, ExactCombatState base, BigDecimal budget) {
        if (!eligible(player)) return null;
        return saved(player, base).map(amount -> MagneticDomainShieldRules.recover(base, amount, budget)).orElse(null);
    }

    /** The combat adapter decides which learned/category reserve halves are eligible. */
    static SharedShieldReserveShares.Recovery recoverShared(ServerPlayer player, ExactCombatState base,
                                                            BigDecimal budget, boolean lowerEligible, boolean upperEligible) {
        if (player == null || player.level().isClientSide || !player.serverLevel().getServer().isSameThread()) return null;
        return savedShares(player, base).map(shares -> shares.recover(base, budget, lowerEligible, upperEligible)).orElse(null);
    }

    /** Legacy NEW152 aggregate callers apply a delta; they must never split the remaining total anew. */
    static void commit(LivingEntity entity, ExactCombatState base, BigDecimal amount) {
        ZeroGapArmorStorage.guardParentWrite(entity);
        ExactCombatState before = ZeroGapArmorStorage.read(entity).map(shares -> shares.base()).orElse(base);
        SharedShieldReserveShares previous = savedShares(entity, before)
                .orElseThrow(() -> new IllegalArgumentException("Unavailable shared shield reserve"));
        writeShares(entity, base, previous.withTotal(base, amount));
    }

    /** Commits every shared parent and optional child before notifying observers. */
    static void commitShares(LivingEntity entity, ExactCombatState base, SharedShieldReserveShares shares) {
        ZeroGapArmorStorage.guardParentWrite(entity);
        ExactCombatState before = ZeroGapArmorStorage.read(entity).map(armor -> armor.base()).orElse(base);
        if (savedShares(entity, before).isEmpty()) throw new IllegalArgumentException("Unavailable shared shield reserve");
        writeShares(entity, base, shares);
    }

    private static void writeShares(LivingEntity entity, ExactCombatState base, SharedShieldReserveShares shares) {
        ZeroGapArmorStorage.guardParentWrite(entity);
        if (ZeroGapArmorStorage.read(entity).isPresent()) {
            HighPowerCombat.commitResources(entity, base, null, shares);
            return;
        }
        entity.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).put(KEY, serialize(base, shares));
    }

    static CompoundTag serialize(ExactCombatState base, SharedShieldReserveShares shares) {
        var fields = shares.toDecimalMap(base);
        CompoundTag reserve = new CompoundTag();
        reserve.putInt("version", VERSION);
        reserve.putString("amount", fields.get("amount"));
        reserve.putString("capacity", fields.get("capacity"));
        reserve.putString("lower", fields.get("lower"));
        reserve.putString("upper", fields.get("upper"));
        return reserve;
    }

    static boolean canChangeCapacity(LivingEntity entity, ExactCombatState before, ExactCombatState after) {
        if (!entity.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).contains(KEY)) return true;
        var shares = savedShares(entity, before);
        // A corrupt optional tranche never vetoes otherwise valid base growth or operator reset.
        if (shares.isEmpty()) return true;
        try { shares.get().validate(after); return true; }
        catch (IllegalArgumentException invalid) { return false; }
    }
    static void capacityChanged(LivingEntity entity, ExactCombatState before, ExactCombatState after) {
        ZeroGapArmorStorage.guardParentWrite(entity);
        if (ZeroGapArmorStorage.read(entity).isPresent()) {
            HighPowerCombat.commitResources(entity, after, null, null);
            return;
        }
        if (!entity.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).contains(KEY)) return;
        var shares = savedShares(entity, before);
        if (shares.isPresent()) writeShares(entity, after, shares.get());
        else {
            // Retain the original bytes and keep them inert even if a later base cap happens to match them.
            entity.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).putBoolean(INVALIDATED_KEY, true);
        }
    }
}
