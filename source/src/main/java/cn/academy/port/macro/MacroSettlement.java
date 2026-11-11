package cn.academy.port.macro;

import java.util.*;
import java.util.function.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Server-owned work only. Callers retain targeting, sealed damage permits and payment authority. */
@EventBusSubscriber(modid="academy")
public final class MacroSettlement {
    private static final Map<MinecraftServer,FairWorkQueue> QUEUES=new IdentityHashMap<>();
    private MacroSettlement(){}
    public static <T> void submit(ServerLevel world,List<T> snapshot,BooleanSupplier valid,Consumer<T> action,Runnable done){
        var server=world.getServer();if(!server.isSameThread())throw new IllegalStateException("Settlement must run on server thread");
        var tasks=new ArrayList<Runnable>(snapshot.size());for(T target:List.copyOf(snapshot))tasks.add(()->action.accept(target));
        var q=QUEUES.computeIfAbsent(server,s->new FairWorkQueue());q.add(tasks,valid,done);q.drain(server.getTickCount(),MacroSkillRules.TARGETS_PER_TICK);
    }
    public static int pending(MinecraftServer server){var q=QUEUES.get(server);return q==null?0:q.pending();}
    /** Tick-owned native controllers can resume their own sealed permits without moving them into a callback. */
    public static boolean reserve(MinecraftServer server){
        if(!server.isSameThread())return false;
        return QUEUES.computeIfAbsent(server,s->new FairWorkQueue()).reserve(server.getTickCount(),MacroSkillRules.TARGETS_PER_TICK);
    }
    @SubscribeEvent public static void tick(ServerTickEvent.Post e){var q=QUEUES.get(e.getServer());if(q!=null)q.drain(e.getServer().getTickCount(),MacroSkillRules.TARGETS_PER_TICK);}
    @SubscribeEvent public static void stop(ServerStoppedEvent e){QUEUES.remove(e.getServer());}
}
