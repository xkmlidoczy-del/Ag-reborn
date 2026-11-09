package cn.academy.port.fleet.client;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import cn.academy.port.fleet.InteractionLab;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;

/** Test-only route to the same native mouse handler, registered solely in the isolated showcase client. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class QaMouse {
    @SubscribeEvent public static void register(RegisterClientCommandsEvent e){
        if(!Boolean.getBoolean("academy.qa.showcase"))return;
        e.getDispatcher().register(Commands.literal("aclab_qa_mouse")
            .then(Commands.argument("button",IntegerArgumentType.integer(0,1))
            .then(Commands.argument("down",IntegerArgumentType.integer(0,1)).executes(c->{
                int button=IntegerArgumentType.getInteger(c,"button"),down=IntegerArgumentType.getInteger(c,"down");
                Minecraft mc=Minecraft.getInstance();
                try{
                    var method=mc.mouseHandler.getClass().getDeclaredMethod("onPress",long.class,int.class,int.class,int.class);
                    method.setAccessible(true);method.invoke(mc.mouseHandler,mc.getWindow().getWindow(),button,down,0);
                    InteractionLab.LOGGER.info("ACLAB_QA_MOUSE button={} down={}",button,down);
                }catch(ReflectiveOperationException ex){throw new IllegalStateException(ex);}
                return 1;
            }))));
    }
}
