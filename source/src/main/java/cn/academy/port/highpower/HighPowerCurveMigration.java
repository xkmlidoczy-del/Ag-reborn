package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.highpower.core.CombatAmounts;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import cn.academy.port.highpower.core.ExactCombatState;
import cn.academy.port.highpower.core.HighPowerLevel;
import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.HashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;

/** One-time v1 checkpoint conversion. Unknown or malformed checkpoints remain rejected. */
public final class HighPowerCurveMigration {
    public static final String BACKUP_KEY = "academy:rebalance_curve_v1_backup";
    private static final ContinuousHighPowerGrowth.Curve OLD_DAMAGE =
            ContinuousHighPowerGrowth.curve("1e7", "1e10", "1e13", "1e16", "5e18");
    private static final MathContext DOWN = new MathContext(40, RoundingMode.DOWN);
    private static final String[] FIELDS = {"version", "health", "maxHealth", "shield", "maxShield", "shieldEnabled"};

    private HighPowerCurveMigration() {}

    public static boolean migrate(LivingEntity entity) {
        if (entity == null || entity.level().isClientSide || entity.getServer() == null
                || !entity.getServer().isSameThread() || !HighPowerCombat.supported(entity)
                || !entity.getPersistentData().contains(HighPowerCombat.DATA_KEY, Tag.TAG_COMPOUND)) return false;
        CompoundTag parent = entity.getPersistentData();
        CompoundTag old = parent.getCompound(HighPowerCombat.DATA_KEY);
        // A previously converted save can never replay the old checkpoint to refill resources.
        if (parent.contains(BACKUP_KEY)) return false;
        boolean continuous = old.contains("growthCurve") || old.contains("growthPosition") || old.contains("earnedLevel");
        if (continuous && (!old.contains("growthCurve", Tag.TAG_INT) || old.getInt("growthCurve") != 1)) return false;
        final ExactCombatState converted;
        final BigDecimal position;
        final int level;
        final BigDecimal pending;
        try {
            if (!old.contains("level", Tag.TAG_INT)) return false;
            level = HighPowerLevel.forLevel(old.getInt("level")).level();
            if (continuous) {
                if (!old.contains("earnedLevel", Tag.TAG_INT) || old.getInt("earnedLevel") != level
                        || !old.contains("growthPosition", Tag.TAG_STRING) || old.getString("growthPosition").length() > 96) return false;
                position = ContinuousHighPowerGrowth.requirePosition(new BigDecimal(old.getString("growthPosition")));
                if (!position.toPlainString().equals(old.getString("growthPosition"))
                        || position.compareTo(BigDecimal.valueOf(Math.min(5, level + 1))) > 0) return false;
            } else position = BigDecimal.valueOf(level);
            var strings = new HashMap<String, String>();
            for (String field : FIELDS) {
                if (!old.contains(field, Tag.TAG_STRING)) return false;
                strings.put(field, old.getString(field));
            }
            var before = ExactCombatState.fromDecimalMap(strings);
            BigDecimal oldDamage = OLD_DAMAGE.value(position);
            if (before.shieldEnabled() || before.maxHealth().compareTo(oldDamage.multiply(BigDecimal.valueOf(20))) != 0
                    || before.maxShield().compareTo(oldDamage.multiply(BigDecimal.TEN)) != 0) return false;
            var after = ContinuousHighPowerGrowth.sample(position);
            converted = new ExactCombatState(rescale(before.health(), before.maxHealth(), after.maxHealth()), after.maxHealth(),
                    rescale(before.shield(), before.maxShield(), after.maxShield()), after.maxShield(), false);
            if (old.contains("pendingLethalHealth")) {
                if (!old.contains("pendingLethalHealth", Tag.TAG_STRING)) return false;
                BigDecimal value = CombatAmounts.parse(old.getString("pendingLethalHealth"), "pending lethal health");
                if (value.signum() <= 0 || value.compareTo(before.maxHealth()) > 0) return false;
                pending = rescale(value, before.maxHealth(), after.maxHealth());
            } else pending = null;
        } catch (IllegalArgumentException invalid) { return false; }

        // The approved curve change rebuilds specialized shield allocations and transient leases.
        // Preserve the complete original checkpoint, including those allocations, for recovery.
        CompoundTag backup = new CompoundTag();
        backup.put("combat", old.copy());
        if (parent.contains(HighPowerNaturalRecovery.DATA_KEY))
            backup.put("naturalRecovery", parent.get(HighPowerNaturalRecovery.DATA_KEY).copy());
        CompoundTag next = new CompoundTag();
        converted.toDecimalMap().forEach(next::putString);
        next.putInt("level", level); next.putInt("earnedLevel", level);
        next.putInt("growthCurve", ContinuousHighPowerGrowth.CURVE_VERSION);
        next.putString("growthPosition", position.toPlainString());
        // Never turn a balance migration into approval of an old native adapter contract.
        if (old.contains("adapterContract", Tag.TAG_STRING)) next.putString("adapterContract", old.getString("adapterContract"));
        if (old.contains("activeGrowth", Tag.TAG_COMPOUND)) next.put("activeGrowth", old.getCompound("activeGrowth").copy());
        next.putBoolean("bracerActive", false);
        next.putLong("lastShieldSpend", entity.level().getGameTime());
        next.putLong("lastShieldTick", Long.MIN_VALUE);
        if (pending != null) next.putString("pendingLethalHealth", pending.toPlainString());
        parent.put(BACKUP_KEY, backup);
        parent.put(HighPowerCombat.DATA_KEY, next);
        parent.remove(HighPowerNaturalRecovery.DATA_KEY);
        HighPowerCombat.curveMigrated(entity, converted);
        if (entity instanceof ServerPlayer player) {
            // CP, cooldowns, learned skills and certified/active coordinates retain their own data.
            var ability = AbilityStorage.get(player);
            if (ability.learned(HighPowerSkills.SCATTER_PLATES)) ScatterPlateStorage.ensure(player);
            if (ability.learned("zero_gap_armor")) ZeroGapArmorStorage.ensureLearned(player);
        }
        return true;
    }

    private static BigDecimal rescale(BigDecimal amount, BigDecimal oldMaximum, BigDecimal newMaximum) {
        if (amount.signum() == 0) return BigDecimal.ZERO;
        if (amount.compareTo(oldMaximum) == 0) return newMaximum;
        // Preserve pool fractions, rounding downward; never refill an injured or depleted pool.
        return amount.multiply(newMaximum).divide(oldMaximum, DOWN).stripTrailingZeros();
    }
}
