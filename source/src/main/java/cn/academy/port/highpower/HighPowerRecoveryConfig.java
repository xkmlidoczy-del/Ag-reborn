package cn.academy.port.highpower;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owned creative extension; never changes classic BodyIntensify. */
public final class HighPowerRecoveryConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue ENABLED = BUILDER.comment(
            "Steady breathing: ability-assisted rest. Only reviewed FoodData natural healing scales to exact maximum HP; items and effects remain 1:1.")
            .define("enabled", true);
    public static final ModConfigSpec.IntValue COMBAT_PAUSE_TICKS = BUILDER.comment(
            "Additional rest delay after damage or offensive action. Set 0 for strict vanilla natural-regeneration timing. Hunger, saturation, exhaustion and naturalRegeneration remain vanilla.")
            .defineInRange("combatPauseTicks", 40, 0, 12000);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private HighPowerRecoveryConfig() {}
}
