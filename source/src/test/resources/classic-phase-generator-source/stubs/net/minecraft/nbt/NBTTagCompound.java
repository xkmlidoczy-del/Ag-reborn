package net.minecraft.nbt;
import java.util.HashMap; import java.util.Map;
public class NBTTagCompound {
 private final Map<String,Object> data = new HashMap<>();
 public void setInteger(String key, int value) { data.put(key, value); }
 public int getInteger(String key) { return data.get(key) instanceof Number n ? n.intValue() : 0; }
 public void setDouble(String key, double value) { data.put(key, value); }
 public double getDouble(String key) { return data.get(key) instanceof Number n ? n.doubleValue() : 0; }
 public void setTag(String key, NBTTagCompound value) { data.put(key, value); }
 public NBTTagCompound getCompoundTag(String key) { return data.get(key) instanceof NBTTagCompound c ? c : new NBTTagCompound(); }
 public boolean hasKey(String key) { return data.containsKey(key); }
}
