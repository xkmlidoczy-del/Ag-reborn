package cn.academy.port.titan;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Local visual preferences do not change server-side combat. */
public final class TitanVisualConfig {
    private TitanVisualConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue NEAR_CAMERA_FADE;
    public static final ModConfigSpec.DoubleValue SHAKE_SCALE;
    static {
        ModConfigSpec.Builder b=new ModConfigSpec.Builder();b.push("visuals");
        NEAR_CAMERA_FADE=b.comment("Fade the closest titan model when the camera is inside or very close to it.").define("nearCameraFade",true);
        SHAKE_SCALE=b.comment("Camera shake strength; set to zero to disable.").defineInRange("screenShakeScale",1.0,0.0,2.0);
        b.pop();SPEC=b.build();
    }
}
