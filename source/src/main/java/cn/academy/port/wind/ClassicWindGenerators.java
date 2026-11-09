/* AcademyCraft1.0.7 ModuleEnergy wind registrations, GPLv3; see NOTICE. */
package cn.academy.port.wind;
import cn.academy.port.solar.ClassicSolarGenerators;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;
public final class ClassicWindGenerators {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"academy");
    public static final DeferredBlock<ClassicWindBlock> BASE=block("windgen_base",ClassicWindRules.Kind.BASE),PILLAR=block("windgen_pillar",ClassicWindRules.Kind.PILLAR),MAIN=block("windgen_main",ClassicWindRules.Kind.MAIN);
    public static final DeferredItem<ClassicWindBlockItem> BASE_ITEM=item("windgen_base",BASE),PILLAR_ITEM=item("windgen_pillar",PILLAR),MAIN_ITEM=item("windgen_main",MAIN);
    public static final DeferredItem<Item> FAN=ITEMS.registerSimpleItem("windgen_fan",new Item.Properties().durability(100));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicWindBaseBlockEntity>> BASE_TILE=TILES.register("windgen_base",()->BlockEntityType.Builder.of(ClassicWindBaseBlockEntity::new,BASE.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicWindMainBlockEntity>> MAIN_TILE=TILES.register("windgen_main",()->BlockEntityType.Builder.of(ClassicWindMainBlockEntity::new,MAIN.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicWindPillarBlockEntity>> PILLAR_TILE=TILES.register("windgen_pillar",()->BlockEntityType.Builder.of(ClassicWindPillarBlockEntity::new,PILLAR.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ClassicWindMenu>> MENU=MENUS.register("wind_generator",()->IMenuTypeExtension.create(ClassicWindMenu::new));
    private ClassicWindGenerators(){}
    private static DeferredBlock<ClassicWindBlock> block(String id,ClassicWindRules.Kind kind){return BLOCKS.register(id,()->new ClassicWindBlock(kind,BlockBehaviour.Properties.of().strength(4f).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));}
    private static DeferredItem<ClassicWindBlockItem> item(String id,DeferredBlock<ClassicWindBlock> block){return ITEMS.register(id,()->new ClassicWindBlockItem(block.get(),new Item.Properties()));}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);MENUS.register(bus);bus.addListener(ClassicWindGenerators::capabilities);bus.addListener(ClassicWindGenerators::creative);}
    private static void capabilities(RegisterCapabilitiesEvent event){
        // Generator links are root-only: multiple auxiliary routes cannot multiply300IF graph bandwidth.
        event.registerBlockEntity(ClassicSolarGenerators.IMAG_FLUX,BASE_TILE.get(),(tile,side)->tile.available()?tile:null);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,BASE_TILE.get(),(tile,side)->tile.available()?tile.itemHandler():null);
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,MAIN_TILE.get(),(tile,side)->tile.available()?tile.itemHandler():null);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){event.accept(BASE_ITEM);event.accept(PILLAR_ITEM);event.accept(MAIN_ITEM);event.accept(FAN);}}
}
