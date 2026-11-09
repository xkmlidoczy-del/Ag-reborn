package cn.academy.port.client.canon;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Client-only presentation switches for canon skill effects (academy-canon-fx-client.toml). Camera items only ever act on
 * the local caster's own view; nothing here reaches the server or other players.
 */
public final class CanonFxConfig {
    private static final ModConfigSpec.Builder BUILDER=new ModConfigSpec.Builder();
    private static final ModConfigSpec.BooleanValue SHADERS=BUILDER.comment("Use the skill effect shaders (refraction, dissolve, fluid flow, dispersion, hologram).",
            "Off, or with a shader pack active, effects fall back to plain textures.").define("shaders",true);
    private static final ModConfigSpec.DoubleValue SHAKE=BUILDER.comment("Camera shake strength for your own casts. 0 turns it off.").defineInRange("cameraShake",1.0,0.0,2.0);
    private static final ModConfigSpec.BooleanValue HIT_FREEZE=BUILDER.comment("Brief impact freeze of skill effects when your cast hits.").define("hitFreeze",true);
    private static final ModConfigSpec.BooleanValue FOV_KICK=BUILDER.comment("Field-of-view kick on your own movement skills.").define("fovKick",true);
    private static final ModConfigSpec.BooleanValue SCREEN=BUILDER.comment("Screen overlays for your own high-level casts (tint, vignette, edge water/frost/ink).").define("screenEffects",true);
    public static final ModConfigSpec SPEC=BUILDER.build();
    private CanonFxConfig() {}

    private static <T> T get(ModConfigSpec.ConfigValue<T> value,T fallback){
        try{return SPEC.isLoaded()?value.get():fallback;}catch(IllegalStateException notLoaded){return fallback;}
    }
    static boolean shaders(){return get(SHADERS,true);}
    static double shake(){return get(SHAKE,1.0);}
    static boolean hitFreeze(){return get(HIT_FREEZE,true);}
    static boolean fovKick(){return get(FOV_KICK,true);}
    static boolean screen(){return get(SCREEN,true);}
}
