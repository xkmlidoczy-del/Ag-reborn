package net.minecraft.world.level.block.entity;
import net.minecraft.core.BlockPos;public class BlockEntity {private final BlockPos pos;public boolean removed;public BlockEntity(BlockPos p){pos=p;}public BlockPos getBlockPos(){return pos;}public Object getBlockState(){return this;}public boolean isRemoved(){return removed;}}
