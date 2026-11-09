/* Forge1.7.10 BlockFluidClassic compatibility adaptation. Forge-derived portions retain
 * Minecraft Forge Public Licence1.0; new port adapters follow project terms. See NOTICE. */
package cn.academy.port.fusion.flow;

import cn.academy.port.fusion.ClassicFusion;
import cn.academy.port.fusion.flow.ClassicPhaseFlowAlgorithm.Cell;
import cn.academy.port.fusion.flow.ClassicPhaseFlowAlgorithm.Material;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ButtonBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.PressurePlateBlock;
import net.minecraft.world.level.block.SignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WeightedPressurePlateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;

/** Native fluid tick bridge; ClassicPhaseFluid must override tick and call this instead of super. */
public final class ClassicPhaseFlow {
    private ClassicPhaseFlow() {}
    public static void tick(Level level, BlockPos pos) {
        if (level.isClientSide || !level.getBlockState(pos).is(ClassicFusion.PHASE_BLOCK.get())) return;
        ClassicPhaseFlowAlgorithm.tick(new NativeWorld(level), pos.getX(), pos.getY(), pos.getZ());
    }
    private record NativeWorld(Level level) implements ClassicPhaseFlowAlgorithm.World {
        @Override public Cell cell(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            if (level.isOutsideBuildHeight(pos)) {
                return new Cell(Material.OTHER, 0, true, null, Integer.MAX_VALUE);
            }
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) return Cell.air();
            if (state.is(ClassicFusion.PHASE_BLOCK.get())) return Cell.phase(state.getValue(LiquidBlock.LEVEL));
            Block block = state.getBlock();
            // Forge defaultDisplacements, extended only to equivalent modern variants.
            Boolean displacement = block instanceof DoorBlock || block instanceof SignBlock
                    || state.is(Blocks.SUGAR_CANE) ? Boolean.FALSE : null;
            FluidState fluid = state.getFluidState();
            Material material = state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
                    || state.is(Blocks.END_GATEWAY) ? Material.PORTAL
                    : fluid.is(FluidTags.WATER) ? Material.WATER
                    : fluid.is(FluidTags.LAVA) ? Material.LAVA : Material.OTHER;
            // Old vanilla liquids were not BlockFluidBase: their density was MAX, not water density1000.
            int density = fluid.getType() instanceof BaseFlowingFluid
                    ? fluid.getFluidType().getDensity() : Integer.MAX_VALUE;
            return new Cell(material, 0, legacyBlocksMovement(state), displacement, density);
        }
        @SuppressWarnings("deprecation")
        private static boolean legacyBlocksMovement(BlockState state) {
            Block block = state.getBlock();
            // These old wood/rock/cloth materials blocked movement even with a thin/open collision shape.
            return state.blocksMotion() || block instanceof ButtonBlock || block instanceof CarpetBlock
                    || block instanceof FenceGateBlock || block instanceof TrapDoorBlock
                    || block instanceof PressurePlateBlock || block instanceof WeightedPressurePlateBlock;
        }
        @Override public void setAir(int x, int y, int z) {
            level.setBlock(new BlockPos(x, y, z), Blocks.AIR.defaultBlockState(), 3);
        }
        @Override public void setPhase(int x, int y, int z, int metadata, int flags) {
            level.setBlock(new BlockPos(x, y, z), ClassicFusion.PHASE_BLOCK.get().defaultBlockState()
                    .setValue(LiquidBlock.LEVEL, metadata), flags);
        }
        @Override public void schedulePhase(int x, int y, int z, int delay) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            if (state.is(ClassicFusion.PHASE_BLOCK.get())) {
                level.scheduleTick(pos, state.getFluidState().getType(), delay);
            }
        }
        @Override public void notifyPhaseNeighbors(int x, int y, int z) {
            level.updateNeighborsAt(new BlockPos(x, y, z), ClassicFusion.PHASE_BLOCK.get());
        }
        @Override public void dropDisplaced(int x, int y, int z) {
            BlockPos pos = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(pos);
            Block.dropResources(state, level, pos, state.hasBlockEntity() ? level.getBlockEntity(pos) : null);
        }
    }
}
