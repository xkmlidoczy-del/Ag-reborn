/* AcademyCraft1.0.7 ModuleEnergy node/matrix registration, GPLv3. See NOTICE. */
package cn.academy.port.wireless;

import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

public final class ClassicWirelessDevices {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"academy");
    public static final DeferredBlock<ClassicWirelessNodeBlock> BASIC=node("wireless_node_basic",ClassicWirelessRules.NodeType.BASIC),STANDARD=node("wireless_node_standard",ClassicWirelessRules.NodeType.STANDARD),ADVANCED=node("wireless_node_advanced",ClassicWirelessRules.NodeType.ADVANCED);
    public static final DeferredItem<net.minecraft.world.item.BlockItem> BASIC_ITEM=ITEMS.registerSimpleBlockItem(BASIC),STANDARD_ITEM=ITEMS.registerSimpleBlockItem(STANDARD),ADVANCED_ITEM=ITEMS.registerSimpleBlockItem(ADVANCED);
    public static final DeferredBlock<ClassicWirelessMatrixBlock> MATRIX=BLOCKS.register("wireless_matrix",()->new ClassicWirelessMatrixBlock(BlockBehaviour.Properties.of().strength(3f).requiresCorrectToolForDrops().noOcclusion().sound(SoundType.STONE).lightLevel(state->15).pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<ClassicWirelessMatrixItem> MATRIX_ITEM=ITEMS.register("wireless_matrix",()->new ClassicWirelessMatrixItem(MATRIX.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicWirelessNodeBlockEntity>> NODE_TILE=TILES.register("wireless_node",()->BlockEntityType.Builder.of(ClassicWirelessNodeBlockEntity::new,BASIC.get(),STANDARD.get(),ADVANCED.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicWirelessMatrixBlockEntity>> MATRIX_TILE=TILES.register("wireless_matrix",()->BlockEntityType.Builder.of(ClassicWirelessMatrixBlockEntity::new,MATRIX.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ClassicWirelessMenu>> MENU=MENUS.register("wireless",()->IMenuTypeExtension.create(ClassicWirelessMenu::new));
    private ClassicWirelessDevices(){}
    private static DeferredBlock<ClassicWirelessNodeBlock> node(String name,ClassicWirelessRules.NodeType type){return BLOCKS.register(name,()->new ClassicWirelessNodeBlock(type,BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);MENUS.register(bus);bus.addListener(ClassicWirelessProtocol::register);bus.addListener(ClassicWirelessDevices::creative);ClassicWirelessSavedData.register();}
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){event.accept(BASIC_ITEM);event.accept(STANDARD_ITEM);event.accept(ADVANCED_ITEM);event.accept(MATRIX_ITEM);}}
}
