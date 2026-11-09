package cn.academy.port.survival;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Only source-complete inert crafting materials and ordinary ore/frame blocks. */
public final class ClassicMaterials {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems("academy");
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks("academy");
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_TYPES =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, "academy");
    public static final DeferredHolder<PlacementModifierType<?>, PlacementModifierType<ClassicOverworldPlacement>>
            OVERWORLD_PLACEMENT = PLACEMENT_TYPES.register("classic_overworld",
                    () -> () -> ClassicOverworldPlacement.CODEC);
    private static final Map<String, DeferredItem<Item>> MATERIAL_ITEMS = new LinkedHashMap<>();
    private static final Map<String, DeferredBlock<Block>> ORE_BLOCKS = new LinkedHashMap<>();
    public static final DeferredItem<Item> CRYSTAL_LOW = item("crystal_low");
    public static final DeferredItem<Item> CRYSTAL_NORMAL = item("crystal_normal");
    public static final DeferredItem<Item> CRYSTAL_PURE = item("crystal_pure");
    public static final DeferredItem<Item> CALC_CHIP = item("calc_chip");
    public static final DeferredItem<Item> DATA_CHIP = item("data_chip");
    public static final DeferredItem<Item> WAFER = item("wafer");
    public static final DeferredItem<Item> CONSTRAINT_INGOT = item("constraint_ingot");
    public static final DeferredItem<Item> IMAG_SILICON_INGOT = item("imag_silicon_ingot");
    public static final DeferredItem<Item> REINFORCED_IRON_PLATE = item("reinforced_iron_plate");
    public static final DeferredItem<Item> IMAG_SILICON_PIECE = item("imag_silicon_piece");
    public static final DeferredItem<Item> RESO_CRYSTAL = item("reso_crystal");
    public static final DeferredItem<Item> CONSTRAINT_PLATE = item("constraint_plate");
    public static final DeferredItem<Item> BRAIN_COMPONENT = item("brain_component");
    public static final DeferredItem<Item> INFO_COMPONENT = item("info_component");
    public static final DeferredItem<Item> RESONANCE_COMPONENT = item("resonance_component");
    public static final DeferredItem<Item> ENERGY_CONVERT_COMPONENT = item("energy_convert_component");
    public static final DeferredBlock<Block> MACHINE_FRAME = block("machine_frame", 4f);
    static {
        for (var ore : ClassicOreRules.ORES) ORE_BLOCKS.put(ore.id(), block(ore.id(), ore.hardness()));
    }
    private ClassicMaterials() {}
    private static DeferredItem<Item> item(String name) {
        var item = ITEMS.registerSimpleItem(name, new Item.Properties());
        MATERIAL_ITEMS.put(name, item);
        return item;
    }
    private static DeferredBlock<Block> block(String name, float hardness) {
        var block = BLOCKS.registerSimpleBlock(name, BlockBehaviour.Properties.of()
                .strength(hardness).sound(SoundType.STONE).requiresCorrectToolForDrops());
        ITEMS.registerSimpleBlockItem(block);
        return block;
    }
    public static Map<String, DeferredItem<Item>> materials() { return Map.copyOf(MATERIAL_ITEMS); }
    public static Map<String, DeferredBlock<Block>> ores() { return Map.copyOf(ORE_BLOCKS); }
    public static void register(IEventBus bus, ModContainer container) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        PLACEMENT_TYPES.register(bus);
        container.registerConfig(ModConfig.Type.COMMON, ClassicWorldgenConfig.SPEC, "academy-worldgen.toml");
        bus.addListener(ClassicMaterials::creative);
        NeoForge.EVENT_BUS.addListener(ClassicFactorLoot::load);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.INGREDIENTS)
            MATERIAL_ITEMS.values().forEach(event::accept);
        if (event.getTabKey() == CreativeModeTabs.BUILDING_BLOCKS) {
            event.accept(MACHINE_FRAME);
            ORE_BLOCKS.values().forEach(event::accept);
        }
    }
}
