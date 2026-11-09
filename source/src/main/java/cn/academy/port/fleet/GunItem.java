package cn.academy.port.fleet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

public final class GunItem extends Item {
    public final int variant;
    public GunItem(int variant) { super(new Item.Properties().stacksTo(1)); this.variant=variant; }
    public int capacity() { return InteractionLab.CAPS[variant]; }
    public int reloadTicks() { return InteractionLab.RELOAD[variant]; }
    public boolean heavy() { return variant>=4; }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack,ItemStack newStack,boolean slotChanged) {
        // Ammo/stage/shot timestamps replace the synced stack. They must not lower G03 again.
        if(oldStack.is(this)&&newStack.is(this))return slotChanged;
        return super.shouldCauseReequipAnimation(oldStack,newStack,slotChanged);
    }
    public static CompoundTag data(ItemStack stack) { return stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(); }
    public static int ammo(ItemStack stack) { return data(stack).getInt("aclab_ammo"); }
    public static void ammo(ItemStack stack, int value) { CustomData.update(DataComponents.CUSTOM_DATA,stack,t->t.putInt("aclab_ammo", Math.max(0,Math.min(stack.getItem() instanceof GunItem g?g.capacity():0,value)))); }
    public static void reload(ItemStack stack,long start,long end) { CustomData.update(DataComponents.CUSTOM_DATA,stack,t->{t.putLong("aclab_reload_start",start);t.putLong("aclab_reload_end",end);}); }
    public static long reloadStart(ItemStack stack) { return data(stack).getLong("aclab_reload_start"); }
    public static boolean hasMagazine(ItemStack s) { var t=data(s);return !t.contains("c03_has_mag")||t.getBoolean("c03_has_mag"); }
    public static void hasMagazine(ItemStack s,boolean value) { CustomData.update(DataComponents.CUSTOM_DATA,s,t->t.putBoolean("c03_has_mag",value)); }
    public static boolean reloadWasEmpty(ItemStack s) {return data(s).getBoolean("c03_reload_empty");}
    public static long lastFire(ItemStack s) {return data(s).getLong("c03_last_fire");}
    public static void lastFire(ItemStack s,long tick) {CustomData.update(DataComponents.CUSTOM_DATA,s,t->t.putLong("c03_last_fire",tick));}
    public static long reloadEnd(ItemStack stack) { return data(stack).getLong("aclab_reload_end"); }
}
