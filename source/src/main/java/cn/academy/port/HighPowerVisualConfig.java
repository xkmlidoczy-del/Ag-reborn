package cn.academy.port;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Isolated client-only high-power materials; no gameplay configuration. */
public final class HighPowerVisualConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue MAGNETIC_DOMAIN_FLOW = BUILDER
            .comment("Optional NEW152 tapered blue-white circulation using the original glow_line texture. Six decorative paths only; boundary, defense and receipts unchanged. OFF keeps the original material. Defaults false.")
            .define("magneticDomainFlow", false);
    public static final ModConfigSpec.BooleanValue RAIL_ARRAY_GRAND = BUILDER
            .comment("Optional NEW151 grand rail body and source-local convergence. Decorative envelope is not a wider damage corridor. Original rendering remains available. Defaults false.")
            .define("railArrayGrand", false);
    public static final ModConfigSpec.BooleanValue LANDING_ARRAY_CUES = BUILDER
            .comment("Optional NEW351 gray-white coordinate-fold publication and accepted-contact cues. Decoration does not change needle paths or guarantee HP damage. Defaults false.")
            .define("landingArrayCues", false);
    public static final ModConfigSpec.BooleanValue ZERO_GAP_ARMOR_STATE = BUILDER
            .comment("Optional NEW252 live energy edges and unprotected corner markers. Decorative layers stay inside actual plates; no extra protection. Defaults false.")
            .define("zeroGapArmorState", false);
    public static final ModConfigSpec.BooleanValue SHEAR_WINDBLADE_VOLUME = BUILDER
            .comment("Optional NEW421 tapered white-gray pressure thickness. Decorative .22/.38m envelope is not damage radius; the .035m contact front is unchanged. OFF preserves .61. Defaults false.")
            .define("shearWindbladeVolume", false);
    public static final ModConfigSpec.BooleanValue MULTICORE_CANOPY_CROWN = BUILDER
            .comment("Optional NEW251 open green-white source crown and receipt-only emission decoration. Four-meter source art is neither protection nor damage coverage. OFF preserves .62. Defaults false.")
            .define("multicoreCanopyCrown", false);
    public static final ModConfigSpec.BooleanValue TRAJECTORY_RETURN_PRESSURE = BUILDER
            .comment("Optional NEW431 wider white-gray reversal pressure and independent native flight receipts. Applies to new casts only; END still clears immediately. OFF preserves .66. Defaults false.")
            .define("trajectoryReturnPressure", false);
    public static final ModConfigSpec.BooleanValue WIND_PRESSURE_LIFT_FLOW = BUILDER
            .comment("Optional NEW443 classic gray wind ribbons. Fixed open flow is not a floor, shield, or safe descent. OFF preserves .72. Defaults false.")
            .define("windPressureLiftFlow", false);
    public static final ModConfigSpec.BooleanValue POINT_MELTDOWN_FOCUS = BUILDER
            .comment("Optional NEW233 face-local heat core and broken focusing arcs. Decorative envelope is not wider mining coverage; success only follows confirmed native removal. OFF preserves .75. Defaults false.")
            .define("pointMeltdownFocus", false);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private HighPowerVisualConfig() {}
}
