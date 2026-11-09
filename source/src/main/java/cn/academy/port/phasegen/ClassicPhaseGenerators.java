/* AcademyCraft1.0.7 ModuleEnergy.phaseGen registration adapter. GPLv3. See NOTICE. */
package cn.academy.port.phasegen;

import cn.academy.port.solar.ClassicSolarGenerators;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.registries.*;

public final class ClassicPhaseGenerators {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"academy");
    public static final DeferredBlock<ClassicPhaseGeneratorBlock> BLOCK=BLOCKS.register("phase_gen",()->new ClassicPhaseGeneratorBlock(BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ITEM=ITEMS.registerSimpleBlockItem(BLOCK,new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicPhaseGeneratorBlockEntity>> TILE=TILES.register("phase_gen",()->BlockEntityType.Builder.of(ClassicPhaseGeneratorBlockEntity::new,BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ClassicPhaseGeneratorMenu>> MENU=MENUS.register("phase_gen",()->IMenuTypeExtension.create(ClassicPhaseGeneratorMenu::new));
    private ClassicPhaseGenerators(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);MENUS.register(bus);bus.addListener(ClassicPhaseGenerators::capabilities);bus.addListener(ClassicPhaseGenerators::creative);}
    private static void capabilities(RegisterCapabilitiesEvent event){
        event.registerBlockEntity(ClassicSolarGenerators.IMAG_FLUX,TILE.get(),(tile,side)->tile.available()?tile:null);
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,TILE.get(),(tile,side)->tile.available()?tile.fluidHandler():null);
        // Source TileInventory exposes all three unrestricted slots on every face; menu filters remain separate.
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,TILE.get(),(tile,side)->tile.available()?new InvWrapper(tile):null);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES)event.accept(ITEM);}
}
