package net.minecraft.inventory;
import net.minecraft.entity.player.EntityPlayer; import net.minecraft.item.ItemStack;
public interface IInventory {
 int getSizeInventory(); ItemStack getStackInSlot(int slot); ItemStack decrStackSize(int slot, int count);
 ItemStack getStackInSlotOnClosing(int slot); void setInventorySlotContents(int slot, ItemStack stack);
 String getInventoryName(); boolean hasCustomInventoryName(); int getInventoryStackLimit();
 boolean isUseableByPlayer(EntityPlayer player); void openInventory(); void closeInventory(); boolean isItemValidForSlot(int slot, ItemStack stack);
}
