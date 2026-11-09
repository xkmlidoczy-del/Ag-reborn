package cn.academy.port;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Common-safe config holder: does not reference or initialize Minecraft/client renderer classes. */
public final class ClassicHudConfig {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    static {BUILDER.push("gui");}
    public static final ModConfigSpec.DoubleValue CP_X=BUILDER.comment("Classic gui.cpbar X, right aligned, GUI-scaled pixels.").defineInRange("cpbarX",-12d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue CP_Y=BUILDER.comment("Classic gui.cpbar Y, top aligned, GUI-scaled pixels.").defineInRange("cpbarY",12d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue KEY_X=BUILDER.comment("Classic gui.keyhint X, right aligned, GUI-scaled pixels.").defineInRange("keyhintX",0d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue KEY_Y=BUILDER.comment("Classic gui.keyhint Y, vertically centered, GUI-scaled pixels.").defineInRange("keyhintY",30d,-100000d,100000d);
    public static final ModConfigSpec.ConfigValue<String> FONT=BUILDER.comment("Classic system font preference; original CJK fallback sequence is retained. No proprietary font is bundled.").define("font","Microsoft YaHei");
    public static final ModConfigSpec.DoubleValue NOTIFICATION_X=BUILDER.comment("Classic gui.notification X, left aligned.").defineInRange("notificationX",0d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue NOTIFICATION_Y=BUILDER.comment("Classic gui.notification Y, top aligned.").defineInRange("notificationY",15d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue MEDIA_X=BUILDER.comment("Classic gui.media X, right aligned offset.").defineInRange("mediaX",-6d,-100000d,100000d);
    public static final ModConfigSpec.DoubleValue MEDIA_Y=BUILDER.comment("Classic gui.media Y, bottom aligned offset.").defineInRange("mediaY",-6d,-100000d,100000d);
    static {BUILDER.pop();BUILDER.push("generic");}
    public static final ModConfigSpec.BooleanValue HEADS_OR_TAILS=BUILDER.comment("Classic client generic.headsOrTails: show a random result after a coin returns naturally.").define("headsOrTails",false);
    static {BUILDER.pop();}
    public enum Vfx2Quality { OFF, LOW, MEDIUM }
    static { BUILDER.push("vfx2"); }
    public static final ModConfigSpec.EnumValue<Vfx2Quality> VFX2_QUALITY=BUILDER.comment("Client-only Railgun material. OFF is exact classic; LOW is the conservative default with a soft overlay; MEDIUM is experimental and may replace up to four nearby inner-core materials while preserving geometry/timing. No gameplay or server setting.").defineEnum("quality",Vfx2Quality.LOW);
    public static final ModConfigSpec.BooleanValue GRAND_RAILGUN=BUILDER.comment("Optional stronger Railgun additive envelope. Keeps classic ivory core even with MEDIUM; quality OFF always wins. Prototype defaults false. No gameplay change.").define("grandRailgunEnvelope",false);
    public static final ModConfigSpec.BooleanValue RAILGUN_NEAR_EYE_VISIBILITY=BUILDER.comment("Opt-in first-person Railgun usability material: softly reduces only your own shot's glow/core fragments within 1.6 blocks of the eye. Preserves geometry, distant direction, hits and lifetime. Not a classic-fidelity claim. Requires LOW/MEDIUM; OFF always wins. Defaults false.").define("nearEyeRailgunVisibility",false);
    public static final ModConfigSpec.EnumValue<Vfx2Quality> LIGHT_SHIELD_VFX2_QUALITY=BUILDER.comment("Client-only Light Shield RGB detail. OFF preserves exact classic material; LOW highlights source swirl; MEDIUM adds restrained flow. Alpha/cutoff/geometry and defense remain classic. Prototype defaults OFF.").defineEnum("lightShieldQuality",Vfx2Quality.OFF);
    public static final ModConfigSpec.BooleanValue MD_NEAR_EYE_VISIBILITY=BUILDER.comment("Optional own first-person direct Meltdowner near-eye visibility. Original geometry/timing/defenses and OFF retained; reflections/mining/other cameras unchanged. Defaults false.").define("nearEyeMeltdownerVisibility",false);
    public static final ModConfigSpec.BooleanValue PLASMA_VFX2=BUILDER.comment("Optional Plasma blue/pink layered material and accepted-state motion. OFF retains classic rendering. Four enhanced bodies and 512 total enhanced vertices globally; no gameplay/impact inference. Defaults false.").define("plasmaLayeredVisual",false);
    static { BUILDER.pop(); }
    public static final ModConfigSpec SPEC=BUILDER.build();
    private ClassicHudConfig(){}
}
