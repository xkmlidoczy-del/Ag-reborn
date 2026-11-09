/* AcademyCraft1.0.7 logo/tab and ModuleAchievements.DUMMY_ITEM registry. GPLv3. */
package cn.academy.port.display;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.*;
public final class ClassicDisplayItems {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    public static final DeferredItem<Item> LOGO=ITEMS.registerSimpleItem("logo",new Item.Properties());
    public static final DeferredItem<ClassicAchievementIconItem> ACHIEVEMENT_ICON=ITEMS.register("achievement_icon",()->new ClassicAchievementIconItem(new Item.Properties()));
    private ClassicDisplayItems(){}
    public static void register(IEventBus bus){ITEMS.register(bus);ClassicCreativeTab.register(bus);}
    /** Adapter accepts the unchanged achievement catalog's icon descriptor without any award/page dependency. */
    public static ItemStack forIcon(String descriptor){
        if(descriptor!=null&&descriptor.startsWith("texture:"))return ClassicAchievementIconItem.getStack(descriptor.substring(8));
        if(descriptor!=null&&descriptor.startsWith("item:")){var id=ResourceLocation.tryParse("academy:"+descriptor.substring(5));if(id!=null&&BuiltInRegistries.ITEM.containsKey(id))return new ItemStack(BuiltInRegistries.ITEM.get(id));}
        return new ItemStack(ACHIEVEMENT_ICON.get());
    }
}
