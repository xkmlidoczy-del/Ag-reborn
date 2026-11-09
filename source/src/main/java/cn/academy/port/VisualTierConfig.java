package cn.academy.port;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only master presentation tier. Kept apart from the frozen per-material switches in
 * {@link HighPowerVisualConfig}: the tier decides how grand the enhanced materials look, while the tier command
 * and the one-time migration below turn those switches on or off together. Never gameplay, server or damage state.
 */
public final class VisualTierConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.EnumValue<VisualTier> TIER = BUILDER
            .comment("Presentation tier for every optional high-power material. OFF = exact classic rendering; LOW = the previous optional look; STANDARD = wider decorative envelopes plus bloom, corona, arcs and field layers; CINEMATIC = widest envelopes plus extra spark/burst layers. Decoration only: damage, range, receipts and server state never read this.")
            .defineEnum("visualTier", VisualTier.CINEMATIC);
    public static final ModConfigSpec.BooleanValue MIGRATED = BUILDER
            .comment("Internal: set after the one-time switch-on of the optional materials for a non-OFF tier.")
            .define("switchesMigrated", false);
    public static final ModConfigSpec.DoubleValue SHAKE = BUILDER
            .comment("Camera shake strength for strong skills (0 disables it, 1 is full). Purely visual; never changes aim, hits or movement.")
            .defineInRange("cameraShake", .6, 0, 1);
    public static final ModConfigSpec SPEC = BUILDER.build();

    /** Effective camera-shake scale in 0..1; zero when the spec is not loaded or unreadable. */
    public static double shake() {
        try { return SPEC.isLoaded() ? Math.max(0, Math.min(1, SHAKE.get())) : 0; } catch (RuntimeException unreadable) { return 0; }
    }

    private VisualTierConfig() {}

    /** Effective tier; OFF whenever the spec is not loaded (tests, early startup) or unreadable. */
    public static VisualTier tier() {
        try { return SPEC.isLoaded() ? TIER.get() : VisualTier.OFF; } catch (RuntimeException unreadable) { return VisualTier.OFF; }
    }
}
