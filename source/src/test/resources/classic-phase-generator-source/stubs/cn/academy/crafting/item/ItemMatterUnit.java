package cn.academy.crafting.item;
import net.minecraft.item.Item; import net.minecraft.item.ItemStack;
public class ItemMatterUnit extends Item {
 public static class MatterMaterial { public final int id; public MatterMaterial(int id) { this.id = id; } }
 public static final MatterMaterial NONE = new MatterMaterial(0), PHASE = new MatterMaterial(1);
 public ItemMatterUnit() { stackLimit = 16; }
 public MatterMaterial getMaterial(ItemStack stack) { if (stack.getItem() != this) return null; return stack.getItemDamage() == 0 ? NONE : stack.getItemDamage() == 1 ? PHASE : null; }
 public ItemStack create(MatterMaterial material) { ItemStack result = new ItemStack(this); result.setItemDamage(material.id); return result; }
}
