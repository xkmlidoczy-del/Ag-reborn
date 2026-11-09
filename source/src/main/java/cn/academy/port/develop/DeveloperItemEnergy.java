package cn.academy.port.develop;

import java.util.Objects;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.energy.IEnergyStorage;

/** Real stack-owned storage. Missing data means zero energy; no creative or testing refill is implicit. */
public final class DeveloperItemEnergy implements DeveloperEnergy.Access {
    public static final String KEY = "academy:developer_energy";
    private final ItemStack stack;
    private final DeveloperType type;

    public DeveloperItemEnergy(ItemStack stack, DeveloperType type) {
        this.stack = Objects.requireNonNull(stack);
        this.type = Objects.requireNonNull(type);
    }

    public static double read(CompoundTag customData, DeveloperType type) {
        return DeveloperEnergy.sanitize(customData.getDouble(KEY), type);
    }

    public static void write(CompoundTag customData, DeveloperType type, double energy) {
        customData.putDouble(KEY, DeveloperEnergy.sanitize(energy, type));
    }

    @Override public double energy() {
        return stack.isEmpty() ? 0 : read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(), type);
    }

    @Override public void energy(double energy) {
        if (!stack.isEmpty()) CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> write(tag, type, energy));
    }

    @Override public DeveloperType type() { return type; }
    @Override public boolean available() { return !stack.isEmpty(); }
    public boolean tryPull(double amount) { return !stack.isEmpty() && DeveloperEnergy.tryPull(this, amount); }
    public double charge(double amount, boolean ignoreBandwidth) {
        return stack.isEmpty() ? amount : DeveloperEnergy.charge(this, amount, ignoreBandwidth);
    }
    /** Register as Capabilities.EnergyStorage.ITEM using a provider in the mod's registration owner. */
    public IEnergyStorage forgeEnergy() { return new DeveloperForgeEnergyStorage(this); }
}
