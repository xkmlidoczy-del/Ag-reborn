package oracle;
import cn.academy.energy.block.TilePhaseGen; import cn.academy.energy.api.IFItemManager; import cn.academy.energy.api.item.ImagEnergyItem;
import cn.academy.crafting.ModuleCrafting; import cn.academy.crafting.item.ItemMatterUnit; import cn.lambdalib.s11n.network.NetworkMessage;
import net.minecraft.item.Item; import net.minecraft.item.ItemStack; import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid; import net.minecraftforge.fluids.FluidStack; import net.minecraftforge.common.util.ForgeDirection;
/** Bridge only: all generator arithmetic and update order executes the unchanged original classes. */
public final class OriginalPhaseHarness {
 private TilePhaseGen tile = new TilePhaseGen();
 public OriginalPhaseHarness(double energy, int liquid) { tile.setEnergy(energy); fill(liquid, true); }
 public double energy() { return tile.getEnergy(); } public int liquid() { return tile.getLiquidAmount(); }
 public double bandwidth() { return tile.getBandwidth(); } public double capacity() { return tile.bufferSize; }
 public int fill(int amount, boolean commit) { return tile.fill(ForgeDirection.UNKNOWN, new FluidStack(ModuleCrafting.fluidImagProj, amount), commit); }
 public int drain(int amount, boolean commit) { FluidStack result = tile.drain(ForgeDirection.UNKNOWN, amount, commit); return result == null ? 0 : result.amount; }
 public double addEnergy(double amount, boolean simulate) { return tile.addEnergy(amount, simulate); }
 public double getProvidedEnergy(double amount) { return tile.getProvidedEnergy(amount); }
 public double generate() { double before = energy(); tile.setInventorySlotContents(2, null); tile.updateEntity(); return energy()-before; }
 public double tick(double itemCapacity, double itemBandwidth) { ItemStack stack = new ItemStack(new Battery(itemCapacity, itemBandwidth)); tile.setInventorySlotContents(2, stack); tile.updateEntity(); return IFItemManager.instance.getEnergy(stack); }
 public void remote(boolean value) { tile.getWorldObj().isRemote = value; }
 public void matter(int inputCount, int outputCount, boolean outputPhase) { ItemStack input = ModuleCrafting.matterUnit.create(ModuleCrafting.imagPhase.mat); input.stackSize = inputCount; tile.setInventorySlotContents(0, input); ItemStack output = outputCount < 0 ? null : ModuleCrafting.matterUnit.create(outputPhase ? ModuleCrafting.imagPhase.mat : ItemMatterUnit.NONE); if (output != null) output.stackSize = outputCount; tile.setInventorySlotContents(1, output); }
 public int input() { ItemStack stack = tile.getStackInSlot(0); return stack == null ? 0 : stack.stackSize; }
 public int output() { ItemStack stack = tile.getStackInSlot(1); return stack == null ? 0 : stack.stackSize; }
 public boolean outputEmptyMaterial() { ItemStack stack = tile.getStackInSlot(1); return stack != null && ModuleCrafting.matterUnit.getMaterial(stack) == ItemMatterUnit.NONE; }
 public void reload() { NBTTagCompound tag = new NBTTagCompound(); tile.writeToNBT(tag); tile = new TilePhaseGen(); tile.readFromNBT(tag); }
 public int wrongFluidFill(int amount) { return tile.fill(ForgeDirection.UP, new FluidStack(new Fluid("wrong"), amount), true); }
 public int wrongFluidDrain(int amount) { FluidStack result = tile.drain(ForgeDirection.UP, new FluidStack(new Fluid("wrong"), amount), true); return result == null ? 0 : result.amount; }
 public boolean canFillAllDirections() { for (ForgeDirection from : ForgeDirection.values()) if (!tile.canFill(from, ModuleCrafting.fluidImagProj)) return false; return true; }
 public boolean canDrainAllDirections() { for (ForgeDirection from : ForgeDirection.values()) if (!tile.canDrain(from, ModuleCrafting.fluidImagProj)) return false; return true; }
 public boolean inventoryAcceptsAllSlots() { ItemStack item = new ItemStack(new Item()); for (int index=0; index<3; index++) if (!tile.isItemValidForSlot(index, item)) return false; return true; }
 public void clearNetwork() { NetworkMessage.sent.clear(); }
 public double lastSync(String channel) { for (int i=NetworkMessage.sent.size()-1; i>=0; i--) { NetworkMessage.Sent event=NetworkMessage.sent.get(i); if (event.channel().equals(channel)) return ((Number)event.arguments()[0]).doubleValue(); } return -1; }
 private static final class Battery extends Item implements ImagEnergyItem { private final double capacity, bandwidth; Battery(double capacity, double bandwidth) { this.capacity=capacity; this.bandwidth=bandwidth; stackLimit=1; } public double getMaxEnergy() { return capacity; } public double getBandwidth() { return bandwidth; } }
}
