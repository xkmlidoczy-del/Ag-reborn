/*
 * Adapted from AcademyCraft 1.0.7 WorldGenPhaseLiq.
 * Copyright (c) Lambda Innovation, 2013-2016. GPLv3; see project LICENSE and NOTICE.
 */
package cn.academy.port.fusion.phase;

import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;

/** Native 1.21.1 material/biome adapter for the classic custom lake, not vanilla LakeFeature. */
public final class WorldGenPhaseLiq {
    private final Supplier<? extends Block> phaseBlock;
    public WorldGenPhaseLiq(Supplier<? extends Block> phaseBlock) {
        this.phaseBlock = Objects.requireNonNull(phaseBlock);
    }

    public boolean generate(WorldGenLevel level, PhaseLiquidRandom random, int x, int y, int z) {
        Block fluidBlock = phaseBlock.get();
        return PhaseLakeAlgorithm.generate(new PhaseLakeAlgorithm.World() {
            private BlockPos pos(int px, int py, int pz) { return new BlockPos(px, py, pz); }
            @Override public boolean isAir(int px, int py, int pz) {
                return level.isEmptyBlock(pos(px, py, pz));
            }
            @Override public boolean isLiquid(int px, int py, int pz) {
                return level.getBlockState(pos(px, py, pz)).liquid();
            }
            @Override public boolean isSolid(int px, int py, int pz) {
                return level.getBlockState(pos(px, py, pz)).isSolid();
            }
            @Override public boolean isPhase(int px, int py, int pz) {
                // Original compared block identity, not metadata/state equality.
                return level.getBlockState(pos(px, py, pz)).is(fluidBlock);
            }
            @Override public boolean isDirt(int px, int py, int pz) {
                return level.getBlockState(pos(px, py, pz)).is(Blocks.DIRT);
            }
            @Override public boolean hasSkyLight(int px, int py, int pz) {
                return level.getBrightness(LightLayer.SKY, pos(px, py, pz)) > 0;
            }
            @Override public boolean hasMyceliumTop(int px, int py, int pz) {
                // Modern biomes removed topBlock; vanilla mushroom-fields is the source
                // mushroom-island/mycelium equivalent. Custom surface rules are not guessed.
                int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, px, pz);
                return level.getBiome(pos(px, surfaceY, pz)).is(Biomes.MUSHROOM_FIELDS);
            }
            @Override public boolean isFreezable(int px, int py, int pz) {
                BlockPos position = pos(px, py, pz);
                // Original lake-style isBlockFreezable uses no shoreline-only restriction.
                return level.getBiome(position).value().shouldFreeze(level, position, false);
            }
            @Override public void set(int px, int py, int pz, PhaseLakeAlgorithm.Output output) {
                Block block = switch (output) {
                    case AIR -> Blocks.AIR;
                    case PHASE_LIQUID -> fluidBlock;
                    case GRASS -> Blocks.GRASS_BLOCK;
                    case MYCELIUM -> Blocks.MYCELIUM;
                    case ICE -> Blocks.ICE;
                };
                // Flag 2 and ordinary AIR preserve the old no-neighbor-notification write.
                // LiquidBlock.defaultBlockState is LEVEL=0: naturally collectible sources.
                level.setBlock(pos(px, py, pz), block.defaultBlockState(), 2);
            }
        }, random, x, y, z);
    }
}
