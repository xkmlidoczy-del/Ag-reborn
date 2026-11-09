package net.minecraft.item;
import net.minecraft.nbt.NBTTagCompound;
public class ItemStack {
 private final Item item; private int damage; private NBTTagCompound tag; public int stackSize;
 public ItemStack(Item item) { this(item, 1); }
 public ItemStack(Item item, int count) { this.item = item; stackSize = count; }
 public Item getItem() { return item; }
 public int getItemDamage() { return damage; }
 public void setItemDamage(int value) { damage = value; }
 public int getMaxStackSize() { return item.getItemStackLimit(); }
 public int getMaxDamage() { return 100; }
 public NBTTagCompound getTagCompound() { return tag; }
 public void setTagCompound(NBTTagCompound value) { tag = value; }
 public ItemStack splitStack(int count) { stackSize -= count; ItemStack result = new ItemStack(item, count); result.damage = damage; result.tag = tag; return result; }
 public void writeToNBT(NBTTagCompound nbt) { nbt.setInteger("item", item.id); nbt.setInteger("count", stackSize); nbt.setInteger("damage", damage); if (tag != null) nbt.setTag("tag", tag); }
 public static ItemStack loadItemStackFromNBT(NBTTagCompound nbt) { ItemStack result = new ItemStack(Item.getById(nbt.getInteger("item")), nbt.getInteger("count")); result.damage = nbt.getInteger("damage"); if (nbt.hasKey("tag")) result.tag = nbt.getCompoundTag("tag"); return result; }
}
