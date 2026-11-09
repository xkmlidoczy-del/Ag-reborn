package cn.academy.port.fusion.phase;

/** Source-exact per-chunk gate and start coordinates; heights are absolute, not relative to -64. */
public final class PhaseLiquidRules {
    public static final double CHANCE = 0.3D;
    public record Start(int x, int y, int z) {}
    private PhaseLiquidRules() {}

    /** Returns null without any coordinate draws when the dimension/config/chance rejects. */
    public static Start drawStart(boolean enabled, boolean overworld, PhaseLiquidRandom random,
                                  int chunkX, int chunkZ) {
        if (!enabled || !overworld) return null;
        if (!(random.nextDouble() < CHANCE)) return null;
        // Java's original argument evaluation order is x, y, z. Do not use InSquarePlacement,
        // HeightRangePlacement or integer RarityFilter: they change these draws/probability.
        return new Start(chunkX * 16 + random.nextInt(16), 5 + random.nextInt(30),
                chunkZ * 16 + random.nextInt(16));
    }
}
