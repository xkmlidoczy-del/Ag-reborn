package cn.academy.biohum.client;
import cn.academy.biohum.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.*;
import net.minecraft.client.model.*;
import net.minecraft.client.model.geom.builders.*;
import net.minecraft.resources.ResourceLocation;
import com.mojang.blaze3d.vertex.PoseStack;
@EventBusSubscriber(modid=BioHum.ID,bus=EventBusSubscriber.Bus.MOD,value=Dist.CLIENT)
public final class BioClient {
 public static final net.minecraft.client.KeyMapping CAST_KEY=new net.minecraft.client.KeyMapping("key.acabiohum.cast",org.lwjgl.glfw.GLFW.GLFW_KEY_K,"key.categories.acabiohum");
 private static int selected=-1;private static String selectedAction;private static java.util.UUID selectedUuid;
 @SubscribeEvent public static void keys(net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent e){e.register(CAST_KEY);}
 public static void prepare(HumNpc npc,String action){var id=Skills.actionId(npc.profile(),action);if(!npc.skills().contains(id))return;selected=npc.getId();selectedUuid=npc.getUUID();selectedAction="cast/"+id;var mc=Minecraft.getInstance();if(mc.player!=null)mc.player.displayClientMessage(net.minecraft.network.chat.Component.literal("已选择 "+Abilities.label(id)+" · 按 "+CAST_KEY.getTranslatedKeyMessage().getString()+" 施放"),true);}
 private static void castSelected(Minecraft mc){
  if(mc.screen==null&&selectedAction!=null&&mc.level!=null&&mc.level.getEntity(selected) instanceof HumNpc a&&a.getUUID().equals(selectedUuid)){
   net.neoforged.neoforge.network.PacketDistributor.sendToServer(new Payloads.Action(selected,selectedAction));
   selected=-1;selectedAction=null;
  }
 }
 public static void tick(net.neoforged.neoforge.client.event.ClientTickEvent.Post e){
  var mc=Minecraft.getInstance();CombatAudio.tick(mc);
  if(mc.level==null||mc.player==null){selected=-1;selectedAction=null;return;}
  while(CAST_KEY.consumeClick())castSelected(mc);
 }
 // The physical key also reaches an armed NPC when ACA assigns K to its vehicle siren.
 public static void castKey(net.neoforged.neoforge.client.event.InputEvent.Key event){
  if(selectedAction==null||event.getAction()!=org.lwjgl.glfw.GLFW.GLFW_PRESS
          ||!CAST_KEY.isActiveAndMatches(com.mojang.blaze3d.platform.InputConstants.getKey(event.getKey(),event.getScanCode())))return;
  castSelected(Minecraft.getInstance());
 }
 @SubscribeEvent public static void setup(FMLClientSetupEvent e){e.enqueueWork(()->{Payloads.openClient=id->{var mc=Minecraft.getInstance();if(mc.level!=null&&mc.level.getEntity(id) instanceof HumNpc a)mc.setScreen(new NpcScreen(a));};Payloads.cueClient=((java.util.function.Consumer<Payloads.SkillCue>)SkillFx::receive).andThen(CombatAudio::receive);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BioClient::tick);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(BioClient::castKey);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SkillFx::render);net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(SkillFx::illusion);if(Boolean.getBoolean("biohum.qa.client"))try{Class.forName("cn.academy.biohum.qa.ClientQa").getMethod("register").invoke(null);}catch(Exception ex){throw new IllegalStateException(ex);}});}
 @SubscribeEvent public static void renderers(EntityRenderersEvent.RegisterRenderers e){
  for(var p:Profiles.ALL.values())e.registerEntityRenderer(BioHum.NPCS.get(p.id()).get(),ctx->new NpcRenderer(ctx,p));
  e.registerEntityRenderer(BioHum.BOLT.get(),ctx->new SpriteRenderer<>(ctx,"bolt",.18f));
  e.registerEntityRenderer(BioHum.RUNE.get(),ctx->new SpriteRenderer<>(ctx,"rune",.5f));
  e.registerEntityRenderer(BioHum.TRAP.get(),ctx->new SpriteRenderer<>(ctx,"rose",1.3f));
  e.registerEntityRenderer(BioHum.TOY_CHARGE.get(),ctx->new SpriteRenderer<>(ctx,"toy_charge",.64f));
  e.registerEntityRenderer(BioHum.CONSTRUCT.get(),ctx->new MobRenderer<Construct,HumanoidModel<Construct>>(ctx,
    new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,64).bakeRoot()),.6f){
   @Override public ResourceLocation getTextureLocation(Construct a){
    return ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/entity/"+(a.iron()?"construct_iron":"construct_ellis")+".png");
   }
   @Override protected void scale(Construct a,PoseStack p,float partial){
    if(a.iron())p.scale(2.04f,2.38f,1.98f);else p.scale(1.45f,1.52f,1.35f);
   }
  });
  e.registerEntityRenderer(BioHum.FLAME.get(),ctx->new MobRenderer<FlameFamiliar,HumanoidModel<FlameFamiliar>>(ctx,new HumanoidModel<>(LayerDefinition.create(HumanoidModel.createMesh(CubeDeformation.NONE,0),64,64).bakeRoot()),.5f){
   {
    addLayer(new net.minecraft.client.renderer.entity.layers.RenderLayer<FlameFamiliar,HumanoidModel<FlameFamiliar>>(this){
     @Override public void render(PoseStack pose,MultiBufferSource buffers,int light,FlameFamiliar flame,
             float walk,float amount,float partial,float age,float yaw,float pitch){
      if(!flame.isInvisible())SkillFx.familiar(pose,buffers,age,yaw*net.minecraft.util.Mth.DEG_TO_RAD);
     }
    });
   }
   @Override public ResourceLocation getTextureLocation(FlameFamiliar a){return ResourceLocation.fromNamespaceAndPath(BioHum.ID,"textures/entity/flame_body.png");}
   @Override protected int getBlockLightLevel(FlameFamiliar a,net.minecraft.core.BlockPos pos){return 15;}
   @Override protected void scale(FlameFamiliar a,PoseStack p,float partial){p.scale(1.5f,1.5f,1.5f);}
  });
 }
 @SubscribeEvent public static void phaseClient(FMLClientSetupEvent event){
  event.enqueueWork(()->{
   Payloads.phaseClient=OthinusSky::receive;
   var bus=net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
   bus.addListener(OthinusSky::color);
   bus.addListener(OthinusSky::fog);
   bus.addListener(OthinusSky::render);
  });
 }
}
