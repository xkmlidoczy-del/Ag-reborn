/* AcademyCraft 1.0.7 ModuleTerminal and dynamically registered ItemApp adapters. GPLv3. See NOTICE. */
package cn.academy.port.terminal;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
public final class TerminalModule {
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    public static final DeferredItem<TerminalInstallerItem> INSTALLER=ITEMS.register("terminal_installer",()->new TerminalInstallerItem(new Item.Properties()));
    public static final DeferredItem<TerminalAppItem> SKILL_TREE=app("skill_tree"),FREQUENCY_TRANSMITTER=app("freq_transmitter"),MEDIA_PLAYER=app("media_player"),URBAN_COMMUNICATION=app("urban_communication");
    private static DeferredItem<TerminalAppItem> app(String name){return ITEMS.register("app_"+name,()->new TerminalAppItem(new Item.Properties(),name));}
    public static void register(IEventBus bus){ITEMS.register(bus);bus.addListener(TerminalModule::creative);bus.addListener(TerminalNetwork::register);NeoForge.EVENT_BUS.register(TerminalEvents.class);}
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){event.accept(INSTALLER);event.accept(SKILL_TREE);event.accept(FREQUENCY_TRANSMITTER);event.accept(MEDIA_PLAYER);event.accept(URBAN_COMMUNICATION);}}
    private TerminalModule(){}
}
