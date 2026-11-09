/*
 * Adapted from AcademyCraft 1.0.7 WorldGenPhaseLiq.
 * Copyright (c) Lambda Innovation, 2013-2016. GPLv3; see project LICENSE and NOTICE.
 */
package cn.academy.port.fusion.phase;

/** Minecraft-independent original lake algorithm, shared by native generation and regressions. */
public final class PhaseLakeAlgorithm {
    public static final int WIDTH = 16;
    public static final int HEIGHT = 8;
    public static final int MASK_SIZE = 2048;
    public static final double ELLIPSOID_THRESHOLD = 0.6D;
    public enum Output { AIR, PHASE_LIQUID, GRASS, MYCELIUM, ICE }

    /** Material/biome adapter. Lower boundary acceptance is solid OR the exact phase block. */
    public interface World {
        boolean isAir(int x, int y, int z);
        boolean isLiquid(int x, int y, int z);
        boolean isSolid(int x, int y, int z);
        boolean isPhase(int x, int y, int z);
        boolean isDirt(int x, int y, int z);
        boolean hasSkyLight(int x, int y, int z);
        boolean hasMyceliumTop(int x, int y, int z);
        boolean isFreezable(int x, int y, int z);
        void set(int x, int y, int z, Output output);
    }

    private PhaseLakeAlgorithm() {}
    public static int index(int x, int y, int z) { return (x * 16 + z) * 8 + y; }

    /** Exact loop ranges, 4..7 ellipsoids, draw order and strict .6 mask test from 1.0.7. */
    public static boolean[] createMask(PhaseLiquidRandom random) {
        boolean[] buffer = new boolean[MASK_SIZE];
        for (int i = 0, loops = random.nextInt(4) + 4; i < loops; ++i) {
            double d0 = random.nextDouble() * 6.0D + 3.0D;
            double d1 = random.nextDouble() * 4.0D + 2.0D;
            double d2 = random.nextDouble() * 6.0D + 3.0D;
            double d3 = random.nextDouble() * (14.0D - d0) + 1.0D + d0 / 2.0D;
            double d4 = random.nextDouble() * (4.0D - d1) + 2.0D + d1 / 2.0D;
            double d5 = random.nextDouble() * (14.0D - d2) + 1.0D + d2 / 2.0D;
            for (int j = 1; j < 15; ++j) {
                for (int k = 1; k < 15; ++k) {
                    for (int l = 1; l < 7; ++l) {
                        double d6 = (j - d3) / (d0 / 2.0D);
                        double d7 = (l - d4) / (d1 / 2.0D);
                        double d8 = (k - d5) / (d2 / 2.0D);
                        if (d6 * d6 + d7 * d7 + d8 * d8 < ELLIPSOID_THRESHOLD)
                            buffer[index(j, l, k)] = true;
                    }
                }
            }
        }
        return buffer;
    }

    public static boolean isBoundary(boolean[] buffer, int x, int y, int z) {
        return !buffer[index(x, y, z)] && (
                x < 15 && buffer[index(x + 1, y, z)]
                || x > 0 && buffer[index(x - 1, y, z)]
                || z < 15 && buffer[index(x, y, z + 1)]
                || z > 0 && buffer[index(x, y, z - 1)]
                || y < 7 && buffer[index(x, y + 1, z)]
                || y > 0 && buffer[index(x, y - 1, z)]);
    }

    public static boolean generate(World world, PhaseLiquidRandom random, int x, int y, int z) {
        // Original descent probes the already shifted corner, not the lake center.
        for (x -= 8, z -= 8; y > 5 && world.isAir(x, y, z); --y) {}
        if (y <= 4) return false;
        y -= 4;
        boolean[] buffer = createMask(random);

        // Validate the entire shell before any writes. Do not partially carve a rejected lake.
        for (int i = 0; i < 16; ++i) {
            for (int j2 = 0; j2 < 16; ++j2) {
                for (int j1 = 0; j1 < 8; ++j1) {
                    if (isBoundary(buffer, i, j1, j2)) {
                        if (j1 >= 4 && world.isLiquid(x + i, y + j1, z + j2)) return false;
                        if (j1 < 4 && !world.isSolid(x + i, y + j1, z + j2)
                                && !world.isPhase(x + i, y + j1, z + j2)) return false;
                    }
                }
            }
        }
        for (int i = 0; i < 16; ++i) {
            for (int j2 = 0; j2 < 16; ++j2) {
                for (int j1 = 0; j1 < 8; ++j1) {
                    if (buffer[index(i, j1, j2)])
                        world.set(x + i, y + j1, z + j2,
                                j1 >= 4 ? Output.AIR : Output.PHASE_LIQUID);
                }
            }
        }
        for (int i = 0; i < 16; ++i) {
            for (int j2 = 0; j2 < 16; ++j2) {
                for (int j1 = 4; j1 < 8; ++j1) {
                    if (buffer[index(i, j1, j2)] && world.isDirt(x + i, y + j1 - 1, z + j2)
                            && world.hasSkyLight(x + i, y + j1, z + j2))
                        world.set(x + i, y + j1 - 1, z + j2,
                                world.hasMyceliumTop(x + i, y + j1, z + j2)
                                        ? Output.MYCELIUM : Output.GRASS);
                }
            }
        }
        // This deliberately queries all 256 positions, as the original did. A real phase
        // fluid is not WATER, so it is not given invented freezing behavior.
        for (int i = 0; i < 16; ++i)
            for (int j2 = 0; j2 < 16; ++j2)
                if (world.isFreezable(x + i, y + 4, z + j2))
                    world.set(x + i, y + 4, z + j2, Output.ICE);
        return true;
    }
}
