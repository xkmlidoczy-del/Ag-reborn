/* AcademyCraft 1.0.7 EnergyItemHelper native-manager semantics. GPLv3. See NOTICE. */
package cn.academy.port.energy;

import cn.academy.port.DeveloperItem;
import cn.academy.port.develop.DeveloperItemEnergy;
import cn.academy.port.develop.DeveloperType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** Native IF manager priority; third-party FE capability lookup remains in ChargingEnergy. */
public final class ClassicEnergyItemHelper {
    private ClassicEnergyItemHelper() {}
    public static ClassicEnergy.Access nativeStorage(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        if (stack.getItem() instanceof ImagEnergyItem) return new ClassicItemEnergy(stack);
        if (stack.getItem() instanceof DeveloperItem) {
            var energy = new DeveloperItemEnergy(stack, DeveloperType.PORTABLE);
            return new ClassicEnergy.Access() {
                public double energy() { return energy.energy(); }
                public void energy(double amount) { energy.energy(amount); }
                public double capacity() { return energy.type().energy; }
                public double bandwidth() { return energy.type().bandwidth; }
                public boolean available() { return energy.available(); }
            };
        }
        return null;
    }
    public static boolean isSupported(ItemStack stack) { return nativeStorage(stack) != null; }
    public static double getEnergy(ItemStack stack) {
        var storage = nativeStorage(stack); return storage == null ? 0 : storage.energy();
    }
    public static void setEnergy(ItemStack stack, double amount) {
        var storage = nativeStorage(stack); if (storage != null) storage.energy(amount);
    }
    /** Returns IF not transferred, including the full request for unsupported items. */
    public static double charge(ItemStack stack, double amount, boolean ignoreBandwidth) {
        var storage = nativeStorage(stack); return storage == null ? amount : ClassicEnergy.charge(storage, amount, ignoreBandwidth);
    }
    /** Returns actual IF removed; unsupported items yield zero. */
    public static double pull(ItemStack stack, double amount, boolean ignoreBandwidth) {
        var storage = nativeStorage(stack); return storage == null ? 0 : ClassicEnergy.pull(storage, amount, ignoreBandwidth);
    }
    public static ItemStack createEmptyItem(Item item) {
        var stack = new ItemStack(item); charge(stack, 0, true); return stack;
    }
    public static ItemStack createFullItem(Item item) {
        var stack = new ItemStack(item); charge(stack, Double.MAX_VALUE, true); return stack;
    }
}
