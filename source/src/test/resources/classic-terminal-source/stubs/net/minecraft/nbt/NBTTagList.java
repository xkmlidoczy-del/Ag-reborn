package net.minecraft.nbt;
import java.util.*; public class NBTTagList extends NBTBase {private final List<NBTBase> values=new ArrayList<>();public void appendTag(NBTBase v){values.add(v);}public int tagCount(){return values.size();}public NBTTagCompound getCompoundTagAt(int i){return (NBTTagCompound)values.get(i);}}
