/* AcademyCraft 1.0.7 IFItemManager/StackUtils payload adaptation. GPLv3. See NOTICE. */
package cn.academy.port.energy;

import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** Stack-owned native IF; missing data is empty. No external map, test refill, or implicit power source. */
public final class ClassicItemEnergy implements ClassicEnergy.Access {
    public static final String KEY = "academy:imaginary_energy";
    private final ItemStack stack;
    private final ImagEnergyItem item;

    public ClassicItemEnergy(ItemStack stack) {
        this.stack = Objects.requireNonNull(stack);
        if (!(stack.getItem() instanceof ImagEnergyItem nativeItem))
            throw new IllegalArgumentException("Item does not provide native imaginary energy");
        item = nativeItem;
    }

    public static double read(CompoundTag data, double capacity) {
        return ClassicEnergy.sanitize(data.getDouble(KEY), capacity);
    }
    public static void write(CompoundTag data, double capacity, double energy) {
        data.putDouble(KEY, ClassicEnergy.sanitize(energy, capacity));
    }
    @Override public double capacity() { return item.getMaxEnergy(); }
    @Override public double bandwidth() { return item.getBandwidth(); }
    // Recipes legitimately return 2/4 empty nonstackable items. No shared charged
    // payload may be filled/extracted before these results split into single units.
    @Override public boolean available() { return !stack.isEmpty() && stack.getCount() == 1 && stack.getItem() == item; }
    @Override public double energy() {
        return available() ? read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(), capacity()) : 0;
    }
    @Override public void energy(double energy) {
        if (!available()) return;
        double bounded = ClassicEnergy.sanitize(energy, capacity());
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> write(data, capacity(), bounded));
        // Source maxDamage13 is a gauge, not energy lost to physical wear.
        stack.setDamageValue(ClassicEnergy.damage(bounded, capacity()));
    }
    public double charge(double amount, boolean ignoreBandwidth) { return ClassicEnergy.charge(this, amount, ignoreBandwidth); }
    public double pull(double amount, boolean ignoreBandwidth) { return ClassicEnergy.pull(this, amount, ignoreBandwidth); }
    public ClassicForgeEnergyStorage forgeEnergy() { return new ClassicForgeEnergyStorage(this); }
}
