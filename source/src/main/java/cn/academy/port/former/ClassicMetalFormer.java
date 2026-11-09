/* AcademyCraft 1.0.7 Metal Former registrations, native 1.21.1 adapters. GPLv3. */
package cn.academy.port.former;
import cn.academy.port.machine.MachineDevelopers;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.items.wrapper.*;
import net.neoforged.neoforge.registries.*;

public final class ClassicMetalFormer {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"academy");
    public static final DeferredBlock<ClassicMetalFormerBlock> BLOCK=BLOCKS.register("metal_former",()->new ClassicMetalFormerBlock(BlockBehaviour.Properties.of().strength(3).sound(SoundType.STONE).requiresCorrectToolForDrops()));
    public static final DeferredItem<BlockItem> ITEM=ITEMS.registerSimpleBlockItem(BLOCK);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicMetalFormerBlockEntity>> TILE=TILES.register("metal_former",()->BlockEntityType.Builder.of(ClassicMetalFormerBlockEntity::new,BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ClassicMetalFormerMenu>> MENU=MENUS.register("metal_former",()->IMenuTypeExtension.create(ClassicMetalFormerMenu::new));
    private ClassicMetalFormer(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);MENUS.register(bus);bus.addListener(ClassicMetalFormer::capabilities);bus.addListener(ClassicMetalFormer::creative);}
    private static void capabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,TILE.get(),(tile,side)->side==null?new InvWrapper(tile):new SidedInvWrapper(tile,side));event.registerBlockEntity(MachineDevelopers.IMAG_FLUX,TILE.get(),(tile,side)->tile);}
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES)event.accept(ITEM);}
}
