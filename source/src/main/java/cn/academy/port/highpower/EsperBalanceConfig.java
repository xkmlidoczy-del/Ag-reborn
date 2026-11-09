package cn.academy.port.highpower;

import cn.academy.port.highpower.core.ClassicDamageScale;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-owner switches for lifting classic skill damage onto the high-power level unit (see {@link ClassicDamageScale}). */
public final class EsperBalanceConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec.BooleanValue SCALE_CLASSIC = BUILDER.comment("Lift classic skill damage (railgun, thunder clap, arc, meltdowner beam...) of enrolled espers onto the level damage unit D.").define("scaleClassicDamage", true);
    public static final ModConfigSpec.DoubleValue DIVISOR = BUILDER.comment("Classic damage is multiplied by D / divisor. 50 makes a full-mastery railgun about 2.2D.").defineInRange("classicDamageDivisor", ClassicDamageScale.DEFAULT_DIVISOR, 1.0, 1.0E6);
    public static final ModConfigSpec SPEC = BUILDER.build();
    private EsperBalanceConfig() {}
    public static boolean scaleClassic() { try { return !SPEC.isLoaded() || SCALE_CLASSIC.get(); } catch (RuntimeException unreadable) { return true; } }
    public static double divisor() { try { return SPEC.isLoaded() ? DIVISOR.get() : ClassicDamageScale.DEFAULT_DIVISOR; } catch (RuntimeException unreadable) { return ClassicDamageScale.DEFAULT_DIVISOR; } }
}
