package cn.academy.port.fleet.client;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import cn.academy.port.fleet.InteractionLab;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.minecraft.world.entity.HumanoidArm;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Only registered inside the explicitly enabled isolated QA client. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class QaView {
 public static int forceSlim=-1;
 @SubscribeEvent public static void register(RegisterClientCommandsEvent e){
  if(!Boolean.getBoolean("academy.qa.showcase"))return;
  G03MotionQa.register(e.getDispatcher());
  e.getDispatcher().register(Commands.literal("aclab_qa_slim").then(Commands.argument("slim",IntegerArgumentType.integer(0,1)).executes(c->{forceSlim=IntegerArgumentType.getInteger(c,"slim");InteractionLab.LOGGER.info("C03_QA_SLIM {}",forceSlim);return 1;})));
  e.getDispatcher().register(Commands.literal("aclab_qa_window").then(Commands.argument("width",IntegerArgumentType.integer(640,2560)).then(Commands.argument("height",IntegerArgumentType.integer(480,1440)).executes(c->{var w=Minecraft.getInstance().getWindow();org.lwjgl.glfw.GLFW.glfwSetWindowSize(w.getWindow(),IntegerArgumentType.getInteger(c,"width"),IntegerArgumentType.getInteger(c,"height"));return 1;}))));
  e.getDispatcher().register(Commands.literal("aclab_qa_view")
   .then(Commands.argument("fov",IntegerArgumentType.integer(30,110))
    .then(Commands.argument("left",IntegerArgumentType.integer(0,1)).executes(c->{
     Minecraft mc=Minecraft.getInstance();int fov=IntegerArgumentType.getInteger(c,"fov");
     HumanoidArm arm=IntegerArgumentType.getInteger(c,"left")==1?HumanoidArm.LEFT:HumanoidArm.RIGHT;
     mc.options.fov().set(fov);mc.options.mainHand().set(arm);mc.options.broadcastOptions();
     InteractionLab.LOGGER.info("ACLAB_QA_VIEW fov={} option={} actual={}",mc.options.fov().get(),mc.options.mainHand().get(),mc.player.getMainArm());
     return 1;
    }))));
 }
}
