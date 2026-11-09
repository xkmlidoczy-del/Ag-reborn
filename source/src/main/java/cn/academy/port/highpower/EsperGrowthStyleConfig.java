package cn.academy.port.highpower;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owner switch for the per-category growth styles (see {@link cn.academy.port.highpower.core.EsperGrowthStyle}). */
public final class EsperGrowthStyleConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER.comment("Per-category bonus experience: EM resonance, MD overload push, TP variety, VM flow reading. Bonuses are bounded to +60% of an award the esper already earned.").define("enabled", true);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private EsperGrowthStyleConfig() {}
    public static boolean enabled() {
        try { return !SPEC.isLoaded() || ENABLED.get(); } catch (RuntimeException unreadable) { return true; }
    }
}
