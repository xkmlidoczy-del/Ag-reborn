package dev.acafauna.core;
import net.neoforged.neoforge.common.ModConfigSpec;
public final class FaunaConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue NATURAL, PLAYER_PREDATION;
    public static final ModConfigSpec.IntValue LOCAL_CAP;
    static {
        var b=new ModConfigSpec.Builder();
        NATURAL=b.comment("Natural generation in tagged overworld biomes. No chunk loading.").define("naturalSpawns",true);
        PLAYER_PREDATION=b.comment("Only lion, jaguar, Nile crocodile and Komodo dragon can hunt players.").define("fourSpeciesPlayerPredation",true);
        LOCAL_CAP=b.defineInRange("maximumFaunaWithin32Blocks",24,1,128);
        SPEC=b.build();
    }
    private FaunaConfig(){}
}
