package net.minecraft.item;
import java.util.HashMap; import java.util.Map;
public class Item {
 private static int nextId; private static final Map<Integer,Item> ITEMS = new HashMap<>();
 public final int id = ++nextId; protected int stackLimit = 64;
 public Item() { ITEMS.put(id, this); }
 public int getItemStackLimit() { return stackLimit; }
 public static Item getById(int id) { return ITEMS.get(id); }
}
