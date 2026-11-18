package cn.aca.omnitrix;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

final class BoneSpike extends Block {
    private static final VoxelShape SHAPE = box(3, 0, 3, 13, 11, 13);

    BoneSpike() { super(BlockBehaviour.Properties.of().noCollission().noOcclusion().strength(.2f).noLootTable()); }

    @Override protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        level.removeBlock(pos, false);
    }

    @Override protected void entityInside(BlockState state, Level level, BlockPos pos, Entity entity) {
        if (!(level instanceof ServerLevel server) || !(entity instanceof LivingEntity victim) || server.getGameTime() % 10 != 0) return;
        var owner = SurvivalForms.spikeOwner(server, pos);
        if (owner != null) Abilities.damage(owner, victim, 2, net.minecraft.world.phys.Vec3.ZERO, 0, 0, 0);
    }
}
