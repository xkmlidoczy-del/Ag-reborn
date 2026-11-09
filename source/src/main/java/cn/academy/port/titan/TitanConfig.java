package cn.academy.port.titan;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Server-controlled limits for world interaction, combat and visual density. */
public final class TitanConfig {
    private TitanConfig() {}
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue NATURAL_SPAWNS;
    public static final ModConfigSpec.IntValue NATURAL_ONE_IN;
    public static final ModConfigSpec.BooleanValue TERRAIN_CHANGES;
    public static final ModConfigSpec.BooleanValue BLOCK_DESTRUCTION;
    public static final ModConfigSpec.IntValue TERRAIN_BLOCK_LIMIT;
    public static final ModConfigSpec.DoubleValue PARTICLE_DENSITY;
    public static final ModConfigSpec.DoubleValue DAMAGE_MULTIPLIER;
    static {
        ModConfigSpec.Builder b=new ModConfigSpec.Builder();
        b.push("gameplay");
        NATURAL_SPAWNS=b.comment("Allow ACA city-only rural natural spawns. No biome modifiers are installed. Spawn eggs and commands remain explicit.")
            .define("naturalSpawns",true);
        NATURAL_ONE_IN=b.comment("One in this many ten-second city-player spawn checks runs; quiet zones, collision and population checks still apply.")
            .defineInRange("naturalSpawnOneIn",12,1,10000);
        TERRAIN_CHANGES=b.comment("Allow bounded terrain effects and destruction; mobGriefing and entity block-destruction events also apply.")
            .define("terrainChanges",true);
        BLOCK_DESTRUCTION=b.comment("Allow titans to break eligible blocks during abilities. Requires terrainChanges and mobGriefing.")
            .define("blockDestruction",true);
        TERRAIN_BLOCK_LIMIT=b.comment("Maximum number of world blocks changed by one cast.")
            .defineInRange("maxChangedBlocksPerCast",24,0,96);
        DAMAGE_MULTIPLIER=b.comment("Multiplier for titan ability and timed melee damage.")
            .defineInRange("damageMultiplier",1.0,0.1,4.0);
        b.pop().push("effects");
        PARTICLE_DENSITY=b.comment("Server-side particle density; large effects can be expensive in multiplayer.")
            .defineInRange("particleDensity",1.0,0.1,2.0);
        b.pop();SPEC=b.build();
    }
}
