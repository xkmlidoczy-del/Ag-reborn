package net.minecraftforge.fluids;
import net.minecraft.nbt.NBTTagCompound; import cn.academy.crafting.ModuleCrafting;
/** Minimal old Forge tank semantics for nonnegative amounts; not the port implementation. */
public class FluidTank {
 private final int capacity; private FluidStack fluid;
 public FluidTank(int capacity) { this.capacity = capacity; }
 public int getCapacity() { return capacity; }
 public int getFluidAmount() { return fluid == null ? 0 : fluid.amount; }
 public int fill(FluidStack resource, boolean doFill) { if (resource == null || resource.amount <= 0 || fluid != null && fluid.getFluid() != resource.getFluid()) return 0; int accepted = Math.min(resource.amount, capacity-getFluidAmount()); if (doFill && accepted > 0) { if (fluid == null) fluid = new FluidStack(resource.getFluid(), accepted); else fluid.amount += accepted; } return accepted; }
 public FluidStack drain(int maximum, boolean doDrain) { if (fluid == null || maximum <= 0) return null; int removed = Math.min(maximum, fluid.amount); FluidStack result = new FluidStack(fluid.getFluid(), removed); if (doDrain) { fluid.amount -= removed; if (fluid.amount <= 0) fluid = null; } return result; }
 public void setFluid(FluidStack value) { fluid = value; }
 public FluidTankInfo getInfo() { return new FluidTankInfo(fluid, capacity); }
 public void writeToNBT(NBTTagCompound tag) { if (fluid != null) { tag.setInteger("Amount", fluid.amount); tag.setInteger("Fluid", 1); } }
 public void readFromNBT(NBTTagCompound tag) { fluid = tag.hasKey("Fluid") ? new FluidStack(ModuleCrafting.fluidImagProj, tag.getInteger("Amount")) : null; }
}
