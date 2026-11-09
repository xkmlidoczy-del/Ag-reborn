/*
 * Adapted from AcademyCraft 1.0.7 PhaseLiquidGenerator.
 * Copyright (c) Lambda Innovation, 2013-2016. GPLv3; see project LICENSE and NOTICE.
 */
package cn.academy.port.fusion.phase;

import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/** One source-faithful chance and lake attempt per native decoration chunk. */
public final class PhaseLiquidGenerator extends Feature<NoneFeatureConfiguration> {
    private final BooleanSupplier enabled;
    private final WorldGenPhaseLiq lakes;
    public PhaseLiquidGenerator(Supplier<? extends Block> phaseBlock) {
        this(phaseBlock, () -> PhaseLiquidWorldgenConfig.GENERATE_PHASE_LIQUID.get());
    }
    public PhaseLiquidGenerator(Supplier<? extends Block> phaseBlock, BooleanSupplier enabled) {
        super(NoneFeatureConfiguration.CODEC);
        this.enabled = Objects.requireNonNull(enabled);
        this.lakes = new WorldGenPhaseLiq(phaseBlock);
    }

    @Override public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context) {
        RandomSource nativeRandom = context.random();
        PhaseLiquidRandom random = new PhaseLiquidRandom() {
            @Override public int nextInt(int bound) { return nativeRandom.nextInt(bound); }
            @Override public double nextDouble() { return nativeRandom.nextDouble(); }
        };
        PhaseLiquidRules.Start start = PhaseLiquidRules.drawStart(enabled.getAsBoolean(),
                context.level().getLevel().dimension().equals(Level.OVERWORLD), random,
                context.origin().getX() >> 4, context.origin().getZ() >> 4);
        return start != null && lakes.generate(context.level(), random, start.x(), start.y(), start.z());
    }
}
