/* AcademyCraft1.0.7 TileRFOutput. GPLv3; see NOTICE. */
package cn.academy.port.bridge;
import cn.academy.port.machine.ImagFluxReceiver;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
public final class ClassicRFOutputBlockEntity extends ClassicEnergyBridgeBlockEntity implements ImagFluxReceiver {
    public ClassicRFOutputBlockEntity(BlockPos pos,BlockState state){super(ClassicEnergyBridges.OUTPUT_TILE.get(),pos,state);}
    @Override public boolean input(){return false;}
    @Override public double getRequiredEnergy(){return available()?ClassicEnergyBridgeBuffer.CAPACITY-buffer.energy():0;}
    @Override public double injectEnergy(double amount){if(!available())return amount;double left=buffer.add(amount);if(left!=amount)setChanged();return left;}
    @Override public double pullEnergy(double amount){return take(amount);}
}
