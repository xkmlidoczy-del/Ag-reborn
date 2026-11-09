package cn.academy.port.tutorial;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class AcademyTutorialConfig {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue GIVE_CLOUD_TERMINAL=BUILDER.comment(
        "Classic generic.giveCloudTerminal: drops the AcademyCraft tutorial guide on first spawn after ten ticks.",
        "The original setting name says terminal, but the granted item is the tutorial, not a terminal installer.")
        .define("giveCloudTerminal",true);
    public static final ModConfigSpec SPEC=BUILDER.build();
    private AcademyTutorialConfig() {}
}
