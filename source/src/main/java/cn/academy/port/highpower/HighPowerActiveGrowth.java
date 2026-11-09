package cn.academy.port.highpower;

import cn.academy.port.AbilityStorage;
import cn.academy.port.SkillCatalog;
import cn.academy.port.highpower.core.ContinuousHighPowerGrowth;
import java.math.BigDecimal;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;

/**
 * Persisted active coordinate, independent of the shared earned-capacity high-water.
 * Category boundaries rebase the native witness without converting retained XP into
 * another denominator. Only the closed native earned-XP receipt can advance it.
 */
final class HighPowerActiveGrowth {
    private static final String KEY = "activeGrowth";
    private static final int VERSION = 1;
    private HighPowerActiveGrowth() {}

    static void beforeMutation(ServerPlayer player) {
        if (player != null && player.server.isSameThread() && HighPowerCombat.enabled(player)) {
            try { state(player); } catch (IllegalArgumentException | IllegalStateException invalid) { /* Disable only high-power output, never native mutations. */ }
        }
    }
    static BigDecimal position(ServerPlayer player) { return amount(state(player)); }
    static long revision(ServerPlayer player) { return state(player).getLong("revision"); }
    static boolean initial(ServerPlayer player) { return state(player).getBoolean("initial"); }
    static void finishInitial(ServerPlayer player) { state(player).putBoolean("initial", false); }

    static void enrolled(ServerPlayer player, int level) {
        var ability = AbilityStorage.get(player);
        BigDecimal active = HighPowerCategories.active(ability) ? BigDecimal.valueOf(Math.min(level, ability.level)) : BigDecimal.valueOf(level);
        CompoundTag data = create(player, active, true);
        player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).put(KEY, data);
    }
    static void reset(ServerPlayer player, BigDecimal position) {
        player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).put(KEY, create(player, position, false));
    }
    static void commit(ServerPlayer player, BigDecimal position) {
        CompoundTag data = state(player);
        BigDecimal admitted = ContinuousHighPowerGrowth.requirePosition(position);
        if (admitted.compareTo(HighPowerCombat.earnedGrowthPosition(player)) > 0) throw new IllegalStateException("Active growth exceeds earned capacity");
        data.putString("position", text(admitted)); data.putBoolean("initial", false);
    }
    /** Clone retains the old witnessed context until AbilityStorage's normal clone handler restores it. */
    static void commitClonedPending(ServerPlayer player, BigDecimal position) {
        var data = player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY).getCompound(KEY);
        data.putString("position", text(position)); data.putBoolean("initial", false);
    }
    private static CompoundTag create(ServerPlayer player, BigDecimal position, boolean initial) {
        var ability = AbilityStorage.get(player); CompoundTag data = new CompoundTag();
        data.putInt("version", VERSION); data.putString("position", text(position));
        data.putString("category", ability.category); data.putInt("nativeLevel", ability.level);
        data.putDouble("nativeXp", ability.levelExperience); data.putLong("revision", 0); data.putBoolean("initial", initial);
        return data;
    }
    private static CompoundTag state(ServerPlayer player) {
        var combat = player.getPersistentData().getCompound(HighPowerCombat.DATA_KEY);
        var ability = AbilityStorage.get(player); BigDecimal earned = HighPowerCombat.earnedGrowthPosition(player);
        if (earned.compareTo(BigDecimal.ONE) < 0) throw new IllegalStateException("Missing earned growth contract");
        if (!combat.contains(KEY)) {
            // .16 migration is a snapshot, never an award or a balance mutation. Before-mutation
            // plumbing captures the old category before the first native switch/relevel.
            BigDecimal position = HighPowerCategories.active(ability)
                    ? ContinuousHighPowerGrowth.position(ability.level, ability.levelProgress(SkillCatalog.levelSkillCount(ability))).min(earned)
                    : BigDecimal.valueOf(Math.max(1, Math.min(5, ability.level))).min(earned);
            CompoundTag migrated = create(player, position, false); combat.put(KEY, migrated);
        }
        if (!combat.contains(KEY, Tag.TAG_COMPOUND)) throw new IllegalStateException("Invalid active growth metadata");
        CompoundTag data = combat.getCompound(KEY);
        if (!data.contains("version", Tag.TAG_INT) || data.getInt("version") != VERSION
                || !data.contains("position", Tag.TAG_STRING) || data.getString("position").length() > 96
                || !data.contains("category", Tag.TAG_STRING) || data.getString("category").length() > 64
                || !data.contains("nativeLevel", Tag.TAG_INT) || data.getInt("nativeLevel") < 0 || data.getInt("nativeLevel") > 5
                || !data.contains("nativeXp", Tag.TAG_DOUBLE) || !Double.isFinite(data.getDouble("nativeXp")) || data.getDouble("nativeXp") < 0
                || !data.contains("revision", Tag.TAG_LONG) || data.getLong("revision") < 0
                || !data.contains("initial", Tag.TAG_BYTE)) throw new IllegalStateException("Invalid active growth metadata");
        BigDecimal position = amount(data);
        if (position.compareTo(earned) > 0 || !text(position).equals(data.getString("position"))) throw new IllegalStateException("Invalid active growth coordinate");
        if (ability.level < 0 || ability.level > 5 || !Double.isFinite(ability.levelExperience) || ability.levelExperience < 0)
            throw new IllegalStateException("Invalid native growth witness");
        boolean boundary = !data.getString("category").equals(ability.category) || data.getInt("nativeLevel") != ability.level;
        if (boundary) {
            // An observed raw rollback can only reduce output; promotion/switching never restores it.
            if (ability.level < data.getInt("nativeLevel")) position = position.min(BigDecimal.valueOf(Math.max(1, ability.level)));
            long revision = data.getLong("revision");
            if (revision == Long.MAX_VALUE) throw new IllegalStateException("Exhausted active growth revision");
            data.putLong("revision", revision + 1); data.putString("position", text(position));
            data.putString("category", ability.category); data.putInt("nativeLevel", ability.level); data.putBoolean("initial", false);
        }
        // Observe a new raw XP value without awarding it. Actual before/after receipts own all credit.
        data.putDouble("nativeXp", ability.levelExperience);
        return data;
    }
    private static BigDecimal amount(CompoundTag data) {
        try { return ContinuousHighPowerGrowth.requirePosition(new BigDecimal(data.getString("position"))); }
        catch (IllegalArgumentException invalid) { throw new IllegalStateException("Invalid active growth coordinate", invalid); }
    }
    private static String text(BigDecimal value) { return value.stripTrailingZeros().toPlainString(); }
}
