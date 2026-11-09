/* AcademyCraft1.0.7 AcademyCraft.cct/getSubItems native creative presentation. GPLv3. */
package cn.academy.port.display;
import cn.academy.port.develop.DevelopmentActions;
import cn.academy.port.develop.InductionFactors;
import cn.academy.port.energy.ClassicEnergyItemHelper;
import net.minecraft.core.registries.*;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.*;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
public final class ClassicCreativeTab {
    private static final DeferredRegister<CreativeModeTab> TABS=DeferredRegister.create(Registries.CREATIVE_MODE_TAB,"academy");
    public static final DeferredHolder<CreativeModeTab,CreativeModeTab> TAB=TABS.register("academycraft",()->CreativeModeTab.builder().title(Component.translatable("itemGroup.AcademyCraft")).icon(()->new ItemStack(ClassicDisplayItems.LOGO.get())).displayItems((parameters,output)->populate(output)).build());
    private ClassicCreativeTab(){}
    public static void register(IEventBus bus){TABS.register(bus);}
    public static void populate(CreativeModeTab.Output output){
        for(var path:ClassicCreativeCatalog.ITEMS){
            var id=ResourceLocation.fromNamespaceAndPath("academy",path);if(!BuiltInRegistries.ITEM.containsKey(id))continue;var item=BuiltInRegistries.ITEM.get(id);
            if(path.equals("induction_factor")){for(var category:DevelopmentActions.CATEGORIES)output.accept(InductionFactors.stack(category));}
            else if(path.equals("portable_developer")||path.equals("energy_unit")){output.accept(ClassicEnergyItemHelper.createEmptyItem(item));output.accept(ClassicEnergyItemHelper.createFullItem(item));}
            else output.accept(new ItemStack(item));
        }
    }
}
