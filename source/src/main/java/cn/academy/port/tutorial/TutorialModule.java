package cn.academy.port.tutorial;

import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class TutorialModule {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    public static final DeferredItem<TutorialItem> ITEM=ITEMS.register("tutorial",()->new TutorialItem(new Item.Properties()));
    public static void register(IEventBus bus,ModContainer container) {
        ITEMS.register(bus);container.registerConfig(ModConfig.Type.SERVER,AcademyTutorialConfig.SPEC,"academy-tutorial-server.toml");
        bus.addListener(TutorialModule::creative);NeoForge.EVENT_BUS.register(TutorialEvents.class);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) { if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES)event.accept(ITEM); }
    private TutorialModule() {}
}
