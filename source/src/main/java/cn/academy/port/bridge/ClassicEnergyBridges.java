/* AcademyCraft1.0.7 unconditional RFSupport registry. GPLv3; see NOTICE. */
package cn.academy.port.bridge;
import cn.academy.port.machine.MachineDevelopers;
import cn.academy.port.solar.ClassicSolarGenerators;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.*;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.common.crafting.IngredientType;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.registries.*;
public final class ClassicEnergyBridges {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    private static final DeferredRegister<IngredientType<?>> INGREDIENTS=DeferredRegister.create(NeoForgeRegistries.Keys.INGREDIENT_TYPES,"academy");
    public static final DeferredHolder<IngredientType<?>,IngredientType<ClassicBridgeEnergyUnitIngredient>> ENERGY_UNIT_INGREDIENT=INGREDIENTS.register("bridge_energy_unit",()->new IngredientType<>(ClassicBridgeEnergyUnitIngredient.CODEC,StreamCodec.unit(new ClassicBridgeEnergyUnitIngredient())));
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    public static final DeferredBlock<ClassicEnergyBridgeBlock> INPUT=block("rf_input",true),OUTPUT=block("rf_output",false);
    public static final DeferredItem<ClassicEnergyBridgeItem> INPUT_ITEM=ITEMS.register("rf_input",()->new ClassicEnergyBridgeItem(INPUT.get(),new Item.Properties())),OUTPUT_ITEM=ITEMS.register("rf_output",()->new ClassicEnergyBridgeItem(OUTPUT.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicRFInputBlockEntity>> INPUT_TILE=TILES.register("rf_input",()->BlockEntityType.Builder.of(ClassicRFInputBlockEntity::new,INPUT.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicRFOutputBlockEntity>> OUTPUT_TILE=TILES.register("rf_output",()->BlockEntityType.Builder.of(ClassicRFOutputBlockEntity::new,OUTPUT.get()).build(null));
    private ClassicEnergyBridges(){}
    private static DeferredBlock<ClassicEnergyBridgeBlock> block(String name,boolean input){return BLOCKS.register(name,()->new ClassicEnergyBridgeBlock(input,BlockBehaviour.Properties.of().strength(2.5f).sound(SoundType.STONE).requiresCorrectToolForDrops().pushReaction(PushReaction.BLOCK)));}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);INGREDIENTS.register(bus);bus.addListener(ClassicEnergyBridges::capabilities);bus.addListener(ClassicEnergyBridges::creative);}
    private static void capabilities(RegisterCapabilitiesEvent event){
        event.registerBlockEntity(ClassicSolarGenerators.IMAG_FLUX,INPUT_TILE.get(),(tile,side)->tile.available()?tile:null);
        event.registerBlockEntity(MachineDevelopers.IMAG_FLUX,OUTPUT_TILE.get(),(tile,side)->tile.available()?tile:null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,INPUT_TILE.get(),(tile,side)->tile.available()?tile.forgeEnergy():null);
        event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,OUTPUT_TILE.get(),(tile,side)->tile.available()?tile.forgeEnergy():null);
    }
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){event.accept(INPUT_ITEM);event.accept(OUTPUT_ITEM);}}
}
