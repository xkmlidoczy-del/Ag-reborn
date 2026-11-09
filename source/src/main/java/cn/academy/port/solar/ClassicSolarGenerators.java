/* AcademyCraft 1.0.7 ModuleEnergy.solarGen registration adaptation, GPLv3. See NOTICE. */
package cn.academy.port.solar;

import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ClassicSolarGenerators {
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "academy");
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, "academy");
    public static final DeferredBlock<ClassicSolarBlock> BLOCK = BLOCKS.register("solar_gen", () -> new ClassicSolarBlock(
            BlockBehaviour.Properties.of().strength(1.5f).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ITEM = ITEMS.registerSimpleBlockItem(BLOCK, new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ClassicSolarBlockEntity>> TILE = TILES.register("solar_gen",
            () -> BlockEntityType.Builder.of(ClassicSolarBlockEntity::new, BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<ClassicSolarMenu>> MENU = MENUS.register("solar_gen",
            () -> IMenuTypeExtension.create(ClassicSolarMenu::new));
    public static final BlockCapability<ImagFluxGenerator, Direction> IMAG_FLUX = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("academy", "imag_flux_generator"), ImagFluxGenerator.class);
    private ClassicSolarGenerators() {}
    public static void register(IEventBus bus) {
        BLOCKS.register(bus); ITEMS.register(bus); TILES.register(bus); MENUS.register(bus);
        bus.addListener(ClassicSolarGenerators::capabilities); bus.addListener(ClassicSolarGenerators::creative);
    }
    private static void capabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(IMAG_FLUX, TILE.get(), (entity, side) -> entity.available() ? entity : null);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) event.accept(ITEM);
    }
}
