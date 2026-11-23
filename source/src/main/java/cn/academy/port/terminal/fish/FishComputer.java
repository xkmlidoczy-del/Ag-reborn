package cn.academy.port.terminal.fish;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class FishComputer {
    static final String ID = "academy";
    private static final String NAME = "fish_computer";
    private static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ID);
    private static final DeferredRegister<BlockEntityType<?>> TILES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, ID);
    private static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, ID);
    public static final DeferredBlock<FishBlock> TERMINAL = BLOCKS.register(NAME,
            () -> new FishBlock(BlockBehaviour.Properties.of().strength(1.8F).sound(SoundType.METAL)
                    .noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<BlockItem> ITEM = ITEMS.registerSimpleBlockItem(NAME, TERMINAL);
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<FishTile>> TILE = TILES.register(NAME,
            () -> BlockEntityType.Builder.of(FishTile::new, TERMINAL.get()).build(null));
    public static final DeferredHolder<MenuType<?>, MenuType<FishMenu>> MENU = MENUS.register(NAME,
            () -> new MenuType<>(FishMenu::new, FeatureFlags.DEFAULT_FLAGS));

    private FishComputer() {}

    public static void register(IEventBus bus) {
        // Keep the contents of worlds and inventories used for the standalone preview.
        var demoId = ResourceLocation.fromNamespaceAndPath("fish_computer", "terminal");
        var mainId = ResourceLocation.fromNamespaceAndPath(ID, NAME);
        BLOCKS.addAlias(demoId, mainId);
        ITEMS.addAlias(demoId, mainId);
        TILES.addAlias(demoId, mainId);
        BLOCKS.register(bus);
        ITEMS.register(bus);
        TILES.register(bus);
        MENUS.register(bus);
        bus.addListener(FishComputer::creativeTab);
    }

    private static void creativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey().equals(CreativeModeTabs.FUNCTIONAL_BLOCKS)) event.accept(ITEM);
    }
}
