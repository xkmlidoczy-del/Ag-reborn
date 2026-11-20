package cn.aca.infinity;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class InfinityConfig {
    private static final int DEFAULT_SNAP_REPEAT_TICKS = 10;
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.IntValue HUKOU_TAG_TICKS;
    public static final ModConfigSpec.IntValue HUKOU_SHAME_TICKS;
    public static final ModConfigSpec.IntValue HUKOU_GAWK_TICKS;
    public static final ModConfigSpec.IntValue HUKOU_PARENT_SLOW_TICKS;
    public static final ModConfigSpec.IntValue TIME_STOP_TICKS;
    public static final ModConfigSpec.DoubleValue SNAP_RADIUS;
    public static final ModConfigSpec.IntValue SNAP_COOLDOWN_TICKS;
    public static final ModConfigSpec.IntValue SOUL_SAVE_COOLDOWN_TICKS;
    public static final ModConfigSpec.BooleanValue ENABLE_COSMIC_VAULT_GENERATION;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();
        b.push("soul_hukou");
        HUKOU_TAG_TICKS = b.comment("Duration (in ticks) of the floating <Prefix>大勇 / <Prefix>翠花 Hukou nameplate above inspected units (default 900 = 45s)")
                .defineInRange("hukouTagTicks", 900, 100, 72000);
        HUKOU_SHAME_TICKS = b.comment("Duration (in ticks) of the 'Social Death / 当场社死' hard-control & vulnerability debuff on normal mobs (default 160 = 8s)")
                .defineInRange("hukouShameTicks", 160, 20, 6000);
        HUKOU_GAWK_TICKS = b.comment("Duration (in ticks) that nearby bystanders stop and turn their heads to stare at the inspected target (default 70 = 3.5s)")
                .defineInRange("hukouGawkTicks", 70, 10, 1200);
        HUKOU_PARENT_SLOW_TICKS = b.comment("Duration (in ticks) of the secondary 'Call Parents / 叫家长' speed & attack-speed reduction (default 300 = 15s)")
                .defineInRange("hukouParentSlowTicks", 300, 40, 12000);
        b.pop();

        b.push("cosmic_skills");
        TIME_STOP_TICKS = b.comment("Duration (in ticks) of the Time Stone local stasis field (default 200 = 10s)")
                .defineInRange("timeStopTicks", 200, 40, 2400);
        SNAP_RADIUS = b.comment("Radius (in blocks) of the Six-Stone Infinity Snap wave (default 48.0)")
                .defineInRange("snapRadius", 48.0, 12.0, 128.0);
        SNAP_COOLDOWN_TICKS = b.comment("Ticks between Six-Stone Snaps (default 10 = 0.5s). Replaces snapCooldownTicks.")
                .defineInRange("snapRepeatTicks", DEFAULT_SNAP_REPEAT_TICKS, 1, 12000);
        SOUL_SAVE_COOLDOWN_TICKS = b.comment("Cooldown (in ticks) of the Soul Stone fatal-damage protection passive (default 1200 = 60s)")
                .defineInRange("soulSaveCooldownTicks", 1200, 100, 24000);
        b.pop();

        b.push("worldgen");
        ENABLE_COSMIC_VAULT_GENERATION = b.comment("Whether rare Cosmic Vault meteorite ruins generate naturally in wilderness biomes (default true)")
                .define("enableCosmicVaultGeneration", true);
        b.pop();

        SPEC = b.build();
    }

    private InfinityConfig() {}

    public static int hukouTagTicks() {
        return getOrDefault(HUKOU_TAG_TICKS, 900);
    }

    public static int hukouShameTicks() {
        return getOrDefault(HUKOU_SHAME_TICKS, 160);
    }

    public static int hukouGawkTicks() {
        return getOrDefault(HUKOU_GAWK_TICKS, 70);
    }

    public static int hukouParentSlowTicks() {
        return getOrDefault(HUKOU_PARENT_SLOW_TICKS, 300);
    }

    public static int timeStopTicks() {
        return getOrDefault(TIME_STOP_TICKS, 200);
    }

    public static double snapRadius() {
        try {
            return SNAP_RADIUS.get();
        } catch (IllegalStateException ex) {
            return 48.0;
        }
    }

    public static int snapCooldownTicks() {
        return getOrDefault(SNAP_COOLDOWN_TICKS, DEFAULT_SNAP_REPEAT_TICKS);
    }

    public static int soulSaveCooldownTicks() {
        return getOrDefault(SOUL_SAVE_COOLDOWN_TICKS, 1200);
    }

    public static boolean enableCosmicVaultGeneration() {
        try {
            return ENABLE_COSMIC_VAULT_GENERATION.get();
        } catch (IllegalStateException ex) {
            return true;
        }
    }

    private static int getOrDefault(ModConfigSpec.IntValue val, int fallback) {
        try {
            return val.get();
        } catch (IllegalStateException ex) {
            return fallback;
        }
    }
}
