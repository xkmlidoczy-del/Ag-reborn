package cn.academy.port.survival;

import java.util.List;

/** Source: ACWorldGen, CustomWorldGen, BlockGenericOre and LambdaLib RandUtils. */
public final class ClassicOreRules {
    public record Ore(String id, float hardness, int harvestLevel, int veinSize,
                      int attempts, String drop, int minimumDrop, int maximumDrop) {}
    // The source order is intentional. RandUtils.rangei has an EXCLUSIVE upper bound.
    public static final List<Ore> ORES = List.of(
            new Ore("reso_crystal_ore", 3f, 2, 4, 18, "reso_crystal", 1, 1),
            new Ore("constraint_metal_ore", 4f, 1, 4, 24, "constraint_metal_ore", 1, 1),
            new Ore("crystal_ore", 3f, 2, 3, 48, "crystal_low", 1, 2),
            new Ore("imag_silicon_ore", 3.75f, 2, 4, 22, "imag_silicon_ore", 1, 1));
    public static final int MINIMUM_START_Y = 0;
    public static final int MAXIMUM_START_Y = 59;
    private ClassicOreRules() {}
    public static boolean canGenerate(boolean generateOres, boolean overworld) {
        return generateOres && overworld;
    }
}
