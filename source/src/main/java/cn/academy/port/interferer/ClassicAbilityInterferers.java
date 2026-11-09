/* AcademyCraft1.0.7 ModuleAbility.abilityInterferer registration. GPLv3; see NOTICE. */
package cn.academy.port.interferer;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;
/** Original plain TileEntity: no inventory, power capability, frequency endpoint, recipe or survival acquisition. */
public final class ClassicAbilityInterferers {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"academy");
    public static final DeferredBlock<ClassicAbilityInterfererBlock> BLOCK=BLOCKS.register("ability_interferer",()->new ClassicAbilityInterfererBlock(BlockBehaviour.Properties.of().strength(0).sound(SoundType.STONE).noOcclusion()));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ITEM=ITEMS.registerSimpleBlockItem(BLOCK,new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicAbilityInterfererBlockEntity>> TILE=TILES.register("ability_interferer",()->BlockEntityType.Builder.of(ClassicAbilityInterfererBlockEntity::new,BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ClassicAbilityInterfererMenu>> MENU=MENUS.register("ability_interferer",()->IMenuTypeExtension.create(ClassicAbilityInterfererMenu::new));
    private ClassicAbilityInterferers(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);MENUS.register(bus);bus.addListener(ClassicInterfererNetwork::register);bus.addListener(ClassicAbilityInterferers::creative);}
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES)event.accept(ITEM);}
}
