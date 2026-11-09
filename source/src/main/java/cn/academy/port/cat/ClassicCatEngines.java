/* AcademyCraft1.0.7 ModuleEnergy.infiniteGen registry equivalent. GPLv3. */
package cn.academy.port.cat;
import cn.academy.port.solar.ClassicSolarGenerators;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.registries.*;
public final class ClassicCatEngines {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    private static final DeferredRegister<BlockEntityType<?>> TILES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    public static final DeferredBlock<ClassicCatEngineBlock> BLOCK=BLOCKS.register("cat_engine",()->new ClassicCatEngineBlock(BlockBehaviour.Properties.of().strength(0).sound(SoundType.STONE).noOcclusion().pushReaction(PushReaction.BLOCK)));
    public static final DeferredItem<net.minecraft.world.item.BlockItem> ITEM=ITEMS.registerSimpleBlockItem(BLOCK,new Item.Properties());
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<ClassicCatEngineBlockEntity>> TILE=TILES.register("cat_engine",()->BlockEntityType.Builder.of(ClassicCatEngineBlockEntity::new,BLOCK.get()).build(null));
    private ClassicCatEngines(){}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);TILES.register(bus);bus.addListener(ClassicCatEngines::capabilities);}
    private static void capabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(ClassicSolarGenerators.IMAG_FLUX,TILE.get(),(tile,side)->tile.available()?tile:null);}
}
