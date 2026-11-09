package cn.academy.port.city.portal;

import com.mojang.serialization.MapCodec;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.*;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.*;

public final class AmethystPortalContent {
    private static final DeferredRegister.Blocks BLOCKS=DeferredRegister.createBlocks("academy");
    private static final DeferredRegister.Items ITEMS=DeferredRegister.createItems("academy");
    public static final DeferredBlock<Fiber> FIBER=BLOCKS.register("amethyst_fiber",()->new Fiber(BlockBehaviour.Properties.of()
        .noCollission().instabreak().sound(SoundType.AMETHYST_CLUSTER).lightLevel(s->3).pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
    public static final DeferredBlock<AmethystPortalBlock> PORTAL=BLOCKS.register("amethyst_city_portal",()->new AmethystPortalBlock(BlockBehaviour.Properties.of()
        .noCollission().noOcclusion().strength(-1,3600000).sound(SoundType.AMETHYST).lightLevel(s->11).noLootTable()));
    public static final DeferredItem<BlockItem> FIBER_ITEM=ITEMS.register("amethyst_fiber",()->new BlockItem(FIBER.get(),new Item.Properties()){
        @Override public void appendHoverText(ItemStack stack,Item.TooltipContext context,List<Component> lines,TooltipFlag flag){
            super.appendHoverText(stack,context,lines,flag);lines.add(Component.translatable("item.academy.amethyst_fiber.desc").withStyle(ChatFormatting.GRAY));
        }
    });
    public static void register(IEventBus bus){BLOCKS.register(bus);ITEMS.register(bus);bus.addListener(AmethystPortalContent::creative);}
    private static void creative(BuildCreativeModeTabContentsEvent e){if(e.getTabKey()==CreativeModeTabs.NATURAL_BLOCKS)e.accept(FIBER_ITEM);}
    public static final class Fiber extends BushBlock {
        private static final MapCodec<Fiber> CODEC=simpleCodec(Fiber::new);
        Fiber(Properties properties){super(properties);}
        @Override protected MapCodec<? extends BushBlock> codec(){return CODEC;}
        @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(net.minecraft.world.level.block.state.BlockState s,net.minecraft.world.level.BlockGetter l,net.minecraft.core.BlockPos p,net.minecraft.world.phys.shapes.CollisionContext c){return box(5,0,5,11,15,11);}
        @Override public void animateTick(net.minecraft.world.level.block.state.BlockState s,net.minecraft.world.level.Level l,net.minecraft.core.BlockPos p,net.minecraft.util.RandomSource random){
            if(random.nextInt(10)==0)l.addParticle(AmethystPortalRitual.SPARK,p.getX()+.5+(random.nextDouble()-.5)*.25,p.getY()+random.nextDouble()*.9,p.getZ()+.5,0,.01,0);
        }
    }
}
