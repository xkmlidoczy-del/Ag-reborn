package cn.academy.port.fleet.client;
import com.mojang.brigadier.arguments.StringArgumentType;
import cn.academy.port.fleet.InteractionLab;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.Commands;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.*;
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class FleetRenderQa {
 private static String label;private static long previous;private static final List<Double> frames=new ArrayList<>();
 @SubscribeEvent public static void command(RegisterClientCommandsEvent e){if(!Boolean.getBoolean("academy.qa.showcase")||!Boolean.getBoolean("academy.qa.confirmOwnWorld"))return;e.getDispatcher().register(Commands.literal("aclab_qa_perf")
 .then(Commands.literal("start").then(Commands.argument("label",StringArgumentType.word()).executes(c->{label=StringArgumentType.getString(c,"label");previous=0;frames.clear();Minecraft.getInstance().options.enableVsync().set(false);Minecraft.getInstance().options.framerateLimit().set(260);return 1;})))
 .then(Commands.literal("stop").executes(c->{var values=frames.stream().mapToDouble(Double::doubleValue).sorted().toArray();if(values.length<20)throw new IllegalStateException("Insufficient frame samples");double sum=Arrays.stream(values).sum();var r=Runtime.getRuntime();InteractionLab.LOGGER.info("FLEET_PERF label={} n={} meanMs={} p50Ms={} p95Ms={} p99Ms={} heapMiB={} cap={} vsync={} resolution={}x{}",label,values.length,sum/values.length,values[values.length/2],values[(int)(values.length*.95)],values[(int)(values.length*.99)],(r.totalMemory()-r.freeMemory())/1048576,Minecraft.getInstance().options.framerateLimit().get(),Minecraft.getInstance().options.enableVsync().get(),Minecraft.getInstance().getWindow().getWidth(),Minecraft.getInstance().getWindow().getHeight());label=null;return 1;})));}
 @SubscribeEvent public static void frame(RenderFrameEvent.Post e){if(label==null)return;long now=System.nanoTime();if(previous!=0)frames.add((now-previous)/1e6);previous=now;}
}
