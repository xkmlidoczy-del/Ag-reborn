package cn.academy.port.client;

import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.energy.ClassicEnergyUnitItem;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;

/** Client-only property hookup; the common registration/storage code imports no client classes. */
public final class ClassicEnergyUnitProperties {
    private ClassicEnergyUnitProperties() {}
    public static void register() {
        ItemProperties.register(ClassicEnergyItems.ENERGY_UNIT.get(), ResourceLocation.fromNamespaceAndPath("academy", "energy_unit_level"),
                (stack, level, entity, seed) -> ClassicEnergyUnitItem.iconLevel(stack));
    }
}
