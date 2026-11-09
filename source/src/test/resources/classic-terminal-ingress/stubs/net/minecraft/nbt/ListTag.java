package net.minecraft.nbt;
import java.util.*;public class ListTag extends Tag implements Iterable<Tag> {private final List<Tag> values=new ArrayList<>();public boolean add(Tag t){return values.add(t);}public Iterator<Tag> iterator(){return values.iterator();}public int size(){return values.size();}public Tag get(int i){return values.get(i);}}
