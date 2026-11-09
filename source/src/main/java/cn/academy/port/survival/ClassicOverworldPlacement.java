package cn.academy.port.survival;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/** Exact dimension/config gate, including when Overworld biomes are used in other dimensions. */
public final class ClassicOverworldPlacement extends PlacementFilter {
    private static final ClassicOverworldPlacement INSTANCE = new ClassicOverworldPlacement();
    public static final MapCodec<ClassicOverworldPlacement> CODEC = MapCodec.unit(() -> INSTANCE);
    private ClassicOverworldPlacement() {}
    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos position) {
        return ClassicOreRules.canGenerate(ClassicWorldgenConfig.GENERATE_ORES.get(),
                context.getLevel().getLevel().dimension().equals(Level.OVERWORLD));
    }
    @Override
    public PlacementModifierType<?> type() { return ClassicMaterials.OVERWORLD_PLACEMENT.get(); }
}
