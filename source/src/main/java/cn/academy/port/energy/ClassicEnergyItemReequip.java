package cn.academy.port.energy;

import java.util.HashSet;
import java.util.Objects;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;

/** The held item stays equipped when its own IF payload is the only changed state. */
public final class ClassicEnergyItemReequip {
    private ClassicEnergyItemReequip() {}

    /** True only for an actual energy update; all other cases retain the item's default hook. */
    public static boolean isEnergyOnlyUpdate(Item expectedItem, ItemStack oldStack, ItemStack newStack,
            boolean slotChanged, String energyKey, double capacity, boolean damageGauge) {
        if (slotChanged || expectedItem == null || oldStack == null || newStack == null
                || oldStack.isEmpty() || newStack.isEmpty()
                || oldStack.getItem() != expectedItem || newStack.getItem() != expectedItem
                || oldStack.getCount() != 1 || newStack.getCount() != 1
                || !Double.isFinite(capacity) || capacity <= 0) return false;

        CompoundTag oldData = oldStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        CompoundTag newData = newStack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        if (!validEnergy(oldData, energyKey, capacity) || !validEnergy(newData, energyKey, capacity)) return false;
        boolean energyChanged = !Objects.equals(oldData.get(energyKey), newData.get(energyKey));
        double oldEnergy = oldData.getDouble(energyKey), newEnergy = newData.getDouble(energyKey);
        oldData.remove(energyKey);
        newData.remove(energyKey);
        if (!oldData.equals(newData)) return false;

        DataComponentMap oldComponents = oldStack.getComponents(), newComponents = newStack.getComponents();
        boolean gaugeChanged = damageGauge
                && !Objects.equals(oldComponents.get(DataComponents.DAMAGE), newComponents.get(DataComponents.DAMAGE));
        // DAMAGE belongs to the energy unit's source gauge only when both values are derived from IF.
        if (gaugeChanged && (!Objects.equals(oldComponents.get(DataComponents.DAMAGE), ClassicEnergy.damage(oldEnergy, capacity))
                || !Objects.equals(newComponents.get(DataComponents.DAMAGE), ClassicEnergy.damage(newEnergy, capacity)))) return false;

        var keys = new HashSet<DataComponentType<?>>(oldComponents.keySet());
        keys.addAll(newComponents.keySet());
        for (DataComponentType<?> key : keys) {
            if (key == DataComponents.CUSTOM_DATA || gaugeChanged && key == DataComponents.DAMAGE) continue;
            if (!Objects.equals(oldComponents.get(key), newComponents.get(key))) return false;
        }
        return energyChanged || gaugeChanged;
    }

    private static boolean validEnergy(CompoundTag data, String key, double capacity) {
        if (!data.contains(key)) return true; // The existing storage contract defines absent IF as zero.
        if (!data.contains(key, Tag.TAG_DOUBLE)) return false; // Both actual storage writers use putDouble.
        double energy = data.getDouble(key);
        return Double.isFinite(energy) && energy >= 0 && energy <= capacity;
    }
}
