package cn.academy.port.highpower;

import cn.academy.port.AcademyConfig;
import java.util.Set;
import java.util.TreeSet;
import net.neoforged.fml.ModList;

/**
 * Open compatibility for the high-power ledger. The closed vanilla contracts stay available (config
 * {@code highPowerOpenCompat=false}, or JVM property {@code academy.compat.strict}); in open mode the pre-checks that only
 * ask "is every loaded mod, pack, class, item, effect and enchantment on the reviewed list" are skipped. Per-transaction
 * validation in {@link HighPowerCombat} is unchanged: a damage transaction that a foreign hook altered is refused and
 * falls back to ordinary combat.
 */
public final class CompatMode {
    private static final Set<String> KNOWN = Set.of("minecraft", "neoforge", "academy", "sable", "sablecompanion", "veil", "geckolib");
    private static volatile Boolean override;

    private CompatMode() {}

    public static boolean open() {
        Boolean forced = override;
        if (forced != null) return forced;
        if (Boolean.getBoolean("academy.compat.strict")) return false;
        try { return AcademyConfig.HIGH_POWER_OPEN_COMPAT.get(); }
        catch (IllegalStateException configNotLoaded) { return true; }
    }

    /** Test hook: null restores the configured value. */
    public static void override(Boolean value) { override = value; }

    /** Mod ids outside the reviewed base set, for the one-time server log and player notice. */
    public static Set<String> foreignMods() {
        Set<String> out = new TreeSet<>();
        for (var info : ModList.get().getMods()) {
            if ("acamech".equals(info.getModId()) && "0.4.3".equals(info.getVersion().toString())) continue;
            if (!KNOWN.contains(info.getModId())) out.add(info.getModId());
        }
        return out;
    }
}
