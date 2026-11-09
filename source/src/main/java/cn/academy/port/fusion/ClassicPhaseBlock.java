/* AcademyCraft1.0.7 BlockImagPhase adaptation. GPLv3. See NOTICE. */
package cn.academy.port.fusion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
public final class ClassicPhaseBlock extends LiquidBlock implements EntityBlock {
    public ClassicPhaseBlock(Properties properties){super(ClassicFusion.PHASE_SOURCE.get(),properties);}
    @Override protected FluidState getFluidState(BlockState state){int level=state.getValue(LEVEL);return level==0?ClassicFusion.PHASE_SOURCE.get().getSource(false):ClassicFusion.PHASE_SOURCE.get().getFlowing(Math.max(1,3-level),false);}
    @Override protected boolean isRandomlyTicking(BlockState state){return true;}
    @Override protected void randomTick(BlockState state,net.minecraft.server.level.ServerLevel level,BlockPos pos,net.minecraft.util.RandomSource random){cn.academy.port.fusion.flow.ClassicPhaseFlow.tick(level,pos);}
    // Original fluid only registers matter/energy units. Vanilla buckets must not delete an unsupported fluid.
    @Override public ItemStack pickupBlock(Player player,LevelAccessor level,BlockPos pos,BlockState state){return ItemStack.EMPTY;}
    @Override public BlockEntity newBlockEntity(BlockPos pos,BlockState state){return new ClassicPhaseBlockEntity(pos,state);}
}
