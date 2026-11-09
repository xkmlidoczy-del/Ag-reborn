/* AcademyCraft1.0.7 ModuleCrafting/ModuleEnergy fusion subset. GPLv3. See NOTICE. */
package cn.academy.port.fusion;
import cn.academy.port.energy.ClassicEnergyItems;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.fusion.phase.PhaseLiquidGenerator;
import cn.academy.port.fusion.phase.PhaseLiquidWorldgenConfig;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.items.wrapper.InvWrapper;
import net.neoforged.neoforge.items.wrapper.SidedInvWrapper;
import net.neoforged.neoforge.registries.*;

/** Only source-functional fusion, phase material and inert source matrix-core variants. */
public final class ClassicFusion {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    private static final DeferredRegister<Fluid> FLUIDS=DeferredRegister.create(Registries.FLUID,"academy");
    private static final DeferredRegister<FluidType> FLUID_TYPES=DeferredRegister.create(net.neoforged.neoforge.registries.NeoForgeRegistries.Keys.FLUID_TYPES,"academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    private static final DeferredRegister<MenuType<?>> MENUS=DeferredRegister.create(Registries.MENU,"academy");
    private static final DeferredRegister<Feature<?>> FEATURES=DeferredRegister.create(Registries.FEATURE,"academy");
    // Source chained setDensity(7000)...setDensity(1): final density really is1, not7000.
    public static final DeferredHolder<FluidType,FluidType> PHASE_TYPE=FLUID_TYPES.register("imag_proj",()->new FluidType(FluidType.Properties.create().descriptionId("fluid.academy.imag_proj").lightLevel(8).density(1).viscosity(6000).temperature(0).canConvertToSource(false).canHydrate(true).canExtinguish(true).supportsBoating(true)){});
    public static final DeferredHolder<Fluid,ClassicPhaseFluid.Source> PHASE_SOURCE=FLUIDS.register("imag_proj",ClassicPhaseFluid.Source::new);
    public static final DeferredHolder<Fluid,ClassicPhaseFluid.Flowing> PHASE_FLOWING=FLUIDS.register("flowing_imag_proj",ClassicPhaseFluid.Flowing::new);
    public static final DeferredBlock<ClassicPhaseBlock> PHASE_BLOCK=BLOCKS.register("phase_liquid",()->new ClassicPhaseBlock(BlockBehaviour.Properties.of().replaceable().randomTicks().noCollission().noOcclusion().strength(100).noLootTable().liquid().lightLevel(state->(int)((3-Math.min(2,state.getValue(net.minecraft.world.level.block.LiquidBlock.LEVEL))-1)/3f*8)).sound(SoundType.EMPTY)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> PHASE_ITEM=ITEMS.registerSimpleBlockItem(PHASE_BLOCK);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicPhaseBlockEntity>> PHASE_TILE=TILES.register("phase_liquid",()->BlockEntityType.Builder.of(ClassicPhaseBlockEntity::new,PHASE_BLOCK.get()).build(null));
    public static final DeferredItem<ClassicMatterUnitItem> MATTER_UNIT=ITEMS.register("matter_unit",()->new ClassicMatterUnitItem(false));
    public static final DeferredItem<ClassicMatterUnitItem> PHASE_MATTER_UNIT=ITEMS.register("matter_unit_phase_liquid",()->new ClassicMatterUnitItem(true));
    public static final DeferredItem<Item> MATRIX_CORE_BASIC=ITEMS.registerSimpleItem("matrix_core_0");
    public static final DeferredItem<Item> MATRIX_CORE_IMPROVED=ITEMS.registerSimpleItem("matrix_core_1");
    public static final DeferredItem<Item> MATRIX_CORE_ADVANCED=ITEMS.registerSimpleItem("matrix_core_2");
    public static final DeferredBlock<ClassicFusorBlock> FUSOR_BLOCK=BLOCKS.register("imag_fusor",()->new ClassicFusorBlock(BlockBehaviour.Properties.of().strength(3).noOcclusion().sound(SoundType.STONE).requiresCorrectToolForDrops().lightLevel(state->state.getValue(ClassicFusorBlock.WORKING)?6:0)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> FUSOR_ITEM=ITEMS.registerSimpleBlockItem(FUSOR_BLOCK);
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicFusorBlockEntity>> FUSOR_TILE=TILES.register("imag_fusor",()->BlockEntityType.Builder.of(ClassicFusorBlockEntity::new,FUSOR_BLOCK.get()).build(null));
    public static final DeferredHolder<MenuType<?>,MenuType<ClassicFusorMenu>> FUSOR_MENU=MENUS.register("imag_fusor",()->IMenuTypeExtension.create(ClassicFusorMenu::new));
    public static final DeferredHolder<Feature<?>,Feature<NoneFeatureConfiguration>> PHASE_FEATURE=FEATURES.register("phase_liquid",()->new PhaseLiquidGenerator(PHASE_BLOCK));
    private ClassicFusion(){}
    public static void register(IEventBus bus,net.neoforged.fml.ModContainer container){container.registerConfig(net.neoforged.fml.config.ModConfig.Type.COMMON,PhaseLiquidWorldgenConfig.SPEC,"academy-phase-liquid-worldgen.toml");FLUID_TYPES.register(bus);FLUIDS.register(bus);BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);MENUS.register(bus);FEATURES.register(bus);bus.addListener(ClassicFusion::capabilities);bus.addListener(ClassicFusion::creative);}
    private static void capabilities(RegisterCapabilitiesEvent event){
        event.registerBlockEntity(Capabilities.FluidHandler.BLOCK,FUSOR_TILE.get(),(tile,side)->tile.fluidHandler());
        event.registerBlockEntity(Capabilities.ItemHandler.BLOCK,FUSOR_TILE.get(),(tile,side)->side==null?new InvWrapper(tile):new SidedInvWrapper(tile,side));
        event.registerBlockEntity(MachineDevelopers.IMAG_FLUX,FUSOR_TILE.get(),(tile,side)->tile);
        event.registerItem(Capabilities.FluidHandler.ITEM,(stack,context)->new ClassicUnitFluidHandler(stack,false),MATTER_UNIT.get(),PHASE_MATTER_UNIT.get());
        event.registerItem(Capabilities.FluidHandler.ITEM,(stack,context)->new ClassicUnitFluidHandler(stack,true),ClassicEnergyItems.ENERGY_UNIT.get());
    }
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){event.accept(FUSOR_ITEM);event.accept(PHASE_ITEM);event.accept(MATTER_UNIT);event.accept(PHASE_MATTER_UNIT);}if(event.getTabKey()==CreativeModeTabs.INGREDIENTS){event.accept(MATRIX_CORE_BASIC);event.accept(MATRIX_CORE_IMPROVED);event.accept(MATRIX_CORE_ADVANCED);}}
}
