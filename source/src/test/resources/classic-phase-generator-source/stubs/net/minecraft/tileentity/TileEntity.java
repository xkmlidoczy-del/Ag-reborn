package net.minecraft.tileentity;
import net.minecraft.world.World; import net.minecraft.nbt.NBTTagCompound;
public class TileEntity { private World world = new World(); public World getWorldObj() { return world; } public void updateEntity() {} public void markDirty() {} public void readFromNBT(NBTTagCompound tag) {} public void writeToNBT(NBTTagCompound tag) {} }

