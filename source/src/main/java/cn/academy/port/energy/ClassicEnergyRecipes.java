package cn.academy.port.energy;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Only the three source energy-unit shaped recipes need the bounded overstack serializer. */
public final class ClassicEnergyRecipes {
    private static final DeferredRegister<RecipeSerializer<?>> SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, "academy");
    public static final DeferredHolder<RecipeSerializer<?>, ClassicEnergyUnitRecipe.Serializer> ENERGY_UNIT_SHAPED =
            SERIALIZERS.register("energy_unit_shaped", ClassicEnergyUnitRecipe.Serializer::new);

    private ClassicEnergyRecipes() {}
    public static void register(IEventBus bus) { SERIALIZERS.register(bus); }
}
