package cn.academy.port.terminal.fish;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

public final class FishTile extends BlockEntity {
    FishTile(BlockPos pos, BlockState state) {
        super(FishComputer.TILE.get(), pos, state);
    }
}
