/* AcademyCraft1.0.7 Silicon Barn client registration and confirmed fragments. GPLv3; see NOTICE. */
package cn.academy.port.client;
import cn.academy.port.skill.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
import net.neoforged.neoforge.client.extensions.common.*;
import java.util.*;
@EventBusSubscriber(modid="academy",value=Dist.CLIENT,bus=EventBusSubscriber.Bus.MOD)
public final class ClassicSilbarnRendering {
 private ClassicSilbarnRendering(){}
 @SubscribeEvent public static void entities(EntityRenderersEvent.RegisterRenderers e){e.registerEntityRenderer(MeltdownerLateEntities.SILBARN_ENTITY.get(),ClassicSilbarnRenderer::new);}
 @SubscribeEvent public static void models(ModelEvent.ModifyBakingResult e){var id=ModelResourceLocation.inventory(ResourceLocation.fromNamespaceAndPath("academy","silbarn"));var original=e.getModels().get(id);if(original!=null)e.getModels().put(id,new ClassicPortableBakedModel(original));}
 @SubscribeEvent public static void reload(RegisterClientReloadListenersEvent e){e.registerReloadListener(ClassicSilbarnModels.INSTANCE);}
 @SubscribeEvent public static void extensions(RegisterClientExtensionsEvent e){e.registerItem(new IClientItemExtensions(){private ClassicSilbarnItemRenderer renderer;@Override public BlockEntityWithoutLevelRenderer getCustomRenderer(){if(renderer==null)renderer=new ClassicSilbarnItemRenderer();return renderer;}@Override public boolean shouldBobAsEntity(net.minecraft.world.item.ItemStack s){return true;}},MeltdownerLateEntities.SILBARN.get());}
 @EventBusSubscriber(modid="academy",value=Dist.CLIENT)
 public static final class Fragments {
  private static Object world,connection;private static final Set<UUID> hit=new HashSet<>();
  @SubscribeEvent public static void tick(ClientTickEvent.Post e){Minecraft mc=Minecraft.getInstance();if(world!=mc.level||connection!=mc.getConnection()){hit.clear();world=mc.level;connection=mc.getConnection();}if(mc.level==null||mc.isPaused())return;Set<UUID> live=new HashSet<>();for(var entity:mc.level.entitiesForRendering())if(entity instanceof SilbarnEntity s){live.add(s.getUUID());if(s.isHit()&&hit.size()<4096&&hit.add(s.getUUID()))ClassicMeltdownerLateEffects.fragments(s.position());}hit.retainAll(live);}
 }
}
