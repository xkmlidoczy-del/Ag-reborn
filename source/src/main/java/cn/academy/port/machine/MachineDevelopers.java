package cn.academy.port.machine;

import cn.academy.port.develop.DeveloperType;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.capabilities.BlockCapability;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class MachineDevelopers {
    public static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    public static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES=DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE,"academy");
    public static final DeferredBlock<MachineDeveloperBlock> NORMAL=block("developer_normal",DeveloperType.NORMAL);
    public static final DeferredBlock<MachineDeveloperBlock> ADVANCED=block("developer_advanced",DeveloperType.ADVANCED);
    public static final DeferredItem<MachineDeveloperItem> NORMAL_ITEM=ITEMS.register("developer_normal",()->new MachineDeveloperItem(NORMAL.get(),new Item.Properties()));
    public static final DeferredItem<MachineDeveloperItem> ADVANCED_ITEM=ITEMS.register("developer_advanced",()->new MachineDeveloperItem(ADVANCED.get(),new Item.Properties()));
    public static final DeferredHolder<BlockEntityType<?>,BlockEntityType<MachineDeveloperBlockEntity>> BLOCK_ENTITY=BLOCK_ENTITIES.register("developer",()->BlockEntityType.Builder.of(MachineDeveloperBlockEntity::new,NORMAL.get(),ADVANCED.get()).build(null));
    public static final BlockCapability<ImagFluxReceiver,Direction> IMAG_FLUX=BlockCapability.createSided(ResourceLocation.fromNamespaceAndPath("academy","imag_flux_receiver"),ImagFluxReceiver.class);
    private MachineDevelopers() {}
    private static DeferredBlock<MachineDeveloperBlock> block(String name,DeveloperType type){return BLOCKS.register(name,()->new MachineDeveloperBlock(type,BlockBehaviour.Properties.of().strength(4f).sound(SoundType.STONE).requiresCorrectToolForDrops().noOcclusion().pushReaction(PushReaction.BLOCK)));}
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);BLOCK_ENTITIES.register(bus);bus.addListener(MachineDevelopers::capabilities);bus.addListener(MachineDevelopers::creative);}
    private static void capabilities(RegisterCapabilitiesEvent event){event.registerBlockEntity(Capabilities.EnergyStorage.BLOCK,BLOCK_ENTITY.get(),(entity,side)->entity.forgeEnergy());event.registerBlockEntity(IMAG_FLUX,BLOCK_ENTITY.get(),(entity,side)->entity.imagFluxReceiver());}
    private static void creative(BuildCreativeModeTabContentsEvent event){if(event.getTabKey()==CreativeModeTabs.TOOLS_AND_UTILITIES){event.accept(NORMAL_ITEM);event.accept(ADVANCED_ITEM);}}
}
