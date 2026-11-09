/* AcademyCraft1.0.7 fluid-container registrations, indivisible1000mB exchange. GPLv3. */
package cn.academy.port.fusion;
import cn.academy.port.energy.ClassicEnergy;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import cn.academy.port.energy.ClassicEnergyItems;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
/** Does not mint partial fluid from partial IF, or multiple charged payloads from stacked units. */
public final class ClassicUnitFluidHandler implements IFluidHandlerItem {
    private ItemStack container;private final boolean energy;
    public ClassicUnitFluidHandler(ItemStack stack,boolean energy){container=stack;this.energy=energy;}
    private boolean singleton(){return container.getCount()==1;}
    private boolean full(){return energy?ClassicEnergyItemHelper.getEnergy(container)==ClassicEnergy.ENERGY_UNIT_CAPACITY:ClassicMatterUnitItem.filled(container);}
    private boolean empty(){return energy?ClassicEnergyItemHelper.getEnergy(container)==0:ClassicMatterUnitItem.empty(container);}
    private FluidStack fluid(){return new FluidStack(ClassicFusion.PHASE_SOURCE.get(),1000);}
    @Override public ItemStack getContainer(){return container;}
    @Override public int getTanks(){return 1;}
    @Override public FluidStack getFluidInTank(int tank){return tank==0&&singleton()&&full()?fluid():FluidStack.EMPTY;}
    @Override public int getTankCapacity(int tank){return tank==0?1000:0;}
    @Override public boolean isFluidValid(int tank,FluidStack resource){return tank==0&&!resource.isEmpty()&&resource.getFluid().isSame(ClassicFusion.PHASE_SOURCE.get());}
    @Override public int fill(FluidStack resource,FluidAction action){
        if(!singleton()||!empty()||resource.getAmount()<1000||!isFluidValid(0,resource))return 0;
        if(action.execute()){if(energy)ClassicEnergyItemHelper.setEnergy(container,10000);else container=new ItemStack(ClassicFusion.PHASE_MATTER_UNIT.get());}return 1000;
    }
    @Override public FluidStack drain(FluidStack resource,FluidAction action){return isFluidValid(0,resource)?drain(resource.getAmount(),action):FluidStack.EMPTY;}
    @Override public FluidStack drain(int amount,FluidAction action){
        if(!singleton()||!full()||amount<1000)return FluidStack.EMPTY;
        var result=fluid();if(action.execute()){if(energy)ClassicEnergyItemHelper.setEnergy(container,0);else container=new ItemStack(ClassicFusion.MATTER_UNIT.get());}return result;
    }
}
