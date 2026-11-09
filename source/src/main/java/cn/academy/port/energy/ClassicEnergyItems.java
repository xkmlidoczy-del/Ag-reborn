package cn.academy.port.energy;

import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Owns exactly one genuine item registration and its finite capability. */
public final class ClassicEnergyItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("academy");
    public static final DeferredItem<ClassicEnergyUnitItem> ENERGY_UNIT = ITEMS.register("energy_unit", ClassicEnergyUnitItem::new);
    private ClassicEnergyItems() {}
    public static void register(IEventBus bus) {
        ClassicEnergyRecipes.register(bus);
        ITEMS.register(bus);
        bus.addListener(ClassicEnergyItems::capabilities);
        bus.addListener(ClassicEnergyItems::creative);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(Capabilities.EnergyStorage.ITEM, (stack, context) -> new ClassicItemEnergy(stack).forgeEnergy(), ENERGY_UNIT.get());
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() != CreativeModeTabs.TOOLS_AND_UTILITIES) return;
        event.accept(ClassicEnergyItemHelper.createEmptyItem(ENERGY_UNIT.get()));
        event.accept(ClassicEnergyItemHelper.createFullItem(ENERGY_UNIT.get()));
    }
}
