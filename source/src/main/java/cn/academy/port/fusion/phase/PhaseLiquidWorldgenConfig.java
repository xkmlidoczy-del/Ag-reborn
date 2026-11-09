package cn.academy.port.fusion.phase;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Register as COMMON: the original generic.genPhaseLiquid default is true. */
public final class PhaseLiquidWorldgenConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue GENERATE_PHASE_LIQUID = BUILDER
            .comment("Classic generic.genPhaseLiquid: naturally generate phase liquid in the Overworld.",
                    "One 0.3 chance per new chunk; starting Y remains absolute 5..34.",
                    "Existing chunks are not regenerated.")
            .define("genPhaseLiquid", true);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private PhaseLiquidWorldgenConfig() {}
}
