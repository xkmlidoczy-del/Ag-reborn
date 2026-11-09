package cn.lambdalib.util.mc;
import net.minecraft.item.ItemStack; import net.minecraft.nbt.NBTTagCompound;
public final class StackUtils { public static NBTTagCompound loadTag(ItemStack stack) { if (stack.getTagCompound() == null) stack.setTagCompound(new NBTTagCompound()); return stack.getTagCompound(); } }

