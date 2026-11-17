package cn.academy.biohum;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.ModConfigSpec;

public final class PresentationConfig {
    public enum Quality {
        LOW(12, 0), STANDARD(32, 1), SHOWCASE(64, 2);

        public final int detailDistance;
        public final int extraLayers;

        Quality(int detailDistance, int extraLayers) {
            this.detailDistance = detailDistance;
            this.extraLayers = extraLayers;
        }
    }

    private static final ModConfigSpec SPEC;
    private static final ModConfigSpec.EnumValue<Quality> QUALITY;

    static {
        var builder = new ModConfigSpec.Builder();
        QUALITY = builder.comment("Visual detail. Hit tests, world changes and sound cues are identical.")
                .defineEnum("effectQuality", Quality.STANDARD);
        SPEC = builder.build();
    }

    static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, SPEC);
    }

    public static Quality quality() {
        return QUALITY.get();
    }

    private PresentationConfig() {}
}
