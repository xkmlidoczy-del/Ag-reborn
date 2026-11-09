package cn.lambdalib.util.datapart;
import net.minecraft.nbt.NBTTagCompound; import cpw.mods.fml.relauncher.Side;
public class DataPart<T> { public T entity; public int syncs; public Side side=Side.SERVER; protected void setClientNeedSync(){} protected void setNBTStorage(){} protected void checkSide(Side expected){if(side!=expected)throw new IllegalStateException("wrong side");} public T getEntity(){return entity;} public void sync(){syncs++;} public void fromNBT(NBTTagCompound tag){} public void toNBT(NBTTagCompound tag){} }
