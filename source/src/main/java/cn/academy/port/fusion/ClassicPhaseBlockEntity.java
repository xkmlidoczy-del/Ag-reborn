package cn.academy.port.fusion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
/** Source TileImagPhase: render-only state, no power generation or phantom fluid capability. */
public final class ClassicPhaseBlockEntity extends BlockEntity {
    public ClassicPhaseBlockEntity(BlockPos pos,BlockState state){super(ClassicFusion.PHASE_TILE.get(),pos,state);}
}
