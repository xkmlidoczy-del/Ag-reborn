/* AcademyCraft 1.0.7 ItemEnergyBase energy_unit adaptation. GPLv3. See NOTICE. */
package cn.academy.port.energy;

import java.util.List;
import java.util.Locale;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.CustomData;

/** Original item, not a right-click power injector: source transfers belong to machine/node slots. */
public final class ClassicEnergyUnitItem extends Item implements ImagEnergyItem {
    public ClassicEnergyUnitItem() {
        super(new Item.Properties().durability(ClassicEnergy.GAUGE_DAMAGE)
                .component(DataComponents.DAMAGE, ClassicEnergy.GAUGE_DAMAGE));
    }
    @Override public double getMaxEnergy() { return ClassicEnergy.ENERGY_UNIT_CAPACITY; }
    @Override public double getBandwidth() { return ClassicEnergy.ENERGY_UNIT_BANDWIDTH; }
    @Override public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        if (ClassicEnergyItemReequip.isEnergyOnlyUpdate(this, oldStack, newStack, slotChanged,
                ClassicItemEnergy.KEY, getMaxEnergy(), true)) return false;
        return slotChanged || super.shouldCauseReequipAnimation(oldStack, newStack, slotChanged);
    }
    public static int iconLevel(ItemStack stack) {
        return ClassicEnergy.iconLevel(ClassicEnergyItemHelper.getEnergy(stack), ClassicEnergy.ENERGY_UNIT_CAPACITY);
    }
    private static int gauge(ItemStack stack) {
        return ClassicEnergy.damage(ClassicEnergyItemHelper.getEnergy(stack), ClassicEnergy.ENERGY_UNIT_CAPACITY);
    }
    @Override public boolean isBarVisible(ItemStack stack) { return gauge(stack) > 0; }
    @Override public int getBarWidth(ItemStack stack) { return ClassicEnergy.GAUGE_DAMAGE - gauge(stack); }
    @Override public int getBarColor(ItemStack stack) {
        return Mth.hsvToRgb((1f - gauge(stack) / (float) ClassicEnergy.GAUGE_DAMAGE) / 3f, 1, 1);
    }
    @Override public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal(String.format(Locale.ROOT, "%.0f/%.0f IF", ClassicEnergyItemHelper.getEnergy(stack), getMaxEnergy())));
    }
    @Override public void verifyComponentsAfterLoad(ItemStack stack) {
        // A corrupt multi-count charged payload must not become multiple charged units when split.
        double energy = stack.getCount() == 1
                ? ClassicItemEnergy.read(stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag(), getMaxEnergy()) : 0;
        CustomData.update(DataComponents.CUSTOM_DATA, stack, data -> ClassicItemEnergy.write(data, getMaxEnergy(), energy));
        stack.setDamageValue(ClassicEnergy.damage(energy, getMaxEnergy()));
    }
}
