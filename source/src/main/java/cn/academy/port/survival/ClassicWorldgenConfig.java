package cn.academy.port.survival;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common configuration is available while world-generation registries are assembled. */
public final class ClassicWorldgenConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue GENERATE_ORES = BUILDER
            .comment("Classic generic.genOres: generate AcademyCraft ores only in the Overworld.",
                     "Starts remain Y 0..59 and replace minecraft:stone only. Affects newly generated chunks.")
            .define("genOres", true);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private ClassicWorldgenConfig() {}
}
