/* AcademyCraft1.0.7 TileRFInput. GPLv3; see NOTICE. */
package cn.academy.port.bridge;
import cn.academy.port.solar.ImagFluxGenerator;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class ClassicRFInputBlockEntity extends ClassicEnergyBridgeBlockEntity implements ImagFluxGenerator {
    public ClassicRFInputBlockEntity(BlockPos pos,BlockState state){super(ClassicEnergyBridges.INPUT_TILE.get(),pos,state);}
    @Override public boolean input(){return true;}
    @Override public double getProvidedEnergy(double request){return take(request);}
}
