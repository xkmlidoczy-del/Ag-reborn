package cn.academy.port.client;
import cn.academy.port.fusion.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ItemBlockRenderTypes;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.*;
import net.minecraft.util.RandomSource;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicFusionClient {
    private ClassicFusionClient(){}
    @SubscribeEvent public static void menus(RegisterMenuScreensEvent event){event.register(ClassicFusion.FUSOR_MENU.get(),ClassicFusorScreen::new);}
    @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers event){event.registerBlockEntityRenderer(ClassicFusion.PHASE_TILE.get(),ClassicPhaseRenderer::new);}
    @SubscribeEvent public static void fluids(RegisterClientExtensionsEvent event){event.registerFluidType(new IClientFluidTypeExtensions(){private final ResourceLocation texture=ResourceLocation.fromNamespaceAndPath("academy","blocks/phase_liquid");@Override public ResourceLocation getStillTexture(){return texture;}@Override public ResourceLocation getFlowingTexture(){return texture;}@Override public ResourceLocation getStillTexture(net.minecraft.world.level.material.FluidState state,net.minecraft.world.level.BlockAndTintGetter getter,BlockPos pos){return ResourceLocation.fromNamespaceAndPath("academy","blocks/black");}@Override public ResourceLocation getFlowingTexture(net.minecraft.world.level.material.FluidState state,net.minecraft.world.level.BlockAndTintGetter getter,BlockPos pos){return ResourceLocation.fromNamespaceAndPath("academy","blocks/black");}},ClassicFusion.PHASE_TYPE.get());}
    @SubscribeEvent public static void setup(FMLClientSetupEvent event){event.enqueueWork(()->{ClassicFusorBlockEntity.setClientObserver(Sounds::observe);ItemBlockRenderTypes.setRenderLayer(ClassicFusion.PHASE_SOURCE.get(),RenderType.translucent());ItemBlockRenderTypes.setRenderLayer(ClassicFusion.PHASE_FLOWING.get(),RenderType.translucent());});}
    @EventBusSubscriber(modid="academy",value=Dist.CLIENT)
    public static final class Sounds {
        private static final Map<BlockPos,FusorLoop> LOOPS=new HashMap<>();private static net.minecraft.client.multiplayer.ClientLevel world;
        static void observe(ClassicFusorBlockEntity tile){var mc=Minecraft.getInstance();if(mc.level!=null&&tile.getLevel()==mc.level&&tile.clientActive()&&!LOOPS.containsKey(tile.getBlockPos())){var loop=new FusorLoop(tile,mc.level);LOOPS.put(tile.getBlockPos().immutable(),loop);mc.getSoundManager().play(loop);}}
        @SubscribeEvent public static void tick(ClientTickEvent.Post event){var mc=Minecraft.getInstance();if(mc.level!=world){for(var loop:LOOPS.values())loop.finish();LOOPS.clear();world=mc.level;}if(world==null||mc.player==null)return;
            LOOPS.entrySet().removeIf(entry->entry.getValue().isStopped());

        }
    }
    private static final class FusorLoop extends AbstractTickableSoundInstance {
        private final ClassicFusorBlockEntity tile;private final net.minecraft.client.multiplayer.ClientLevel world;
        FusorLoop(ClassicFusorBlockEntity tile,net.minecraft.client.multiplayer.ClientLevel world){super(SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("academy","machine.imag_fusor_work")),SoundSource.MASTER,RandomSource.create());this.tile=tile;this.world=world;volume=.6f;looping=true;delay=0;x=tile.getBlockPos().getX()+.5;y=tile.getBlockPos().getY()+.5;z=tile.getBlockPos().getZ()+.5;}
        @Override public void tick(){if(Minecraft.getInstance().level!=world||tile.isRemoved()||world.getBlockEntity(tile.getBlockPos())!=tile||!tile.clientActive())stop();}
        void finish(){stop();}
    }
}
