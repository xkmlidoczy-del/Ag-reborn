package cn.academy.port.qa;

import cn.academy.port.city.*;
import cn.academy.port.train.TrainManager;
import com.google.gson.GsonBuilder;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.*;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Bounded CPU wall-time samples in one actual city scene. Does not claim GPU time or long-run performance. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class CityInteriorPerfQa {
    private static final List<CityElevatorController> LIFTS=new ArrayList<>();private static final List<CityNpcEntity> NPCS=new ArrayList<>();
    private static final Map<String,Object> REPORT=new LinkedHashMap<>();private static volatile boolean stress;private static volatile Window current;
    private static Window frameWindow,tickWindow;private static long frameStart,tickStart,previousFrame;
    private static final Map<String,Integer> ARRIVALS=new HashMap<>();private static final Map<String,CityElevatorController.Phase> PREVIOUS=new HashMap<>();
    private static final class Window {final String name;final List<Long> frame=new ArrayList<>(),intervals=new ArrayList<>(),server=new ArrayList<>();long peakHeap;int peakMoving;long movingTicks;Window(String n){name=n;}}
    private static <T>T server(Minecraft mc,java.util.function.Supplier<T> f){if(mc.getSingleplayerServer()==null)throw new IllegalStateException("Owned integrated city required");return mc.getSingleplayerServer().submit(f).join();}
    public static boolean step(Minecraft mc,String arg){var a=arg.split(",");
        switch(a[0]){
            case "setup" -> server(mc,()->{
                ServerLevel w=mc.getSingleplayerServer().getLevel(TrainManager.CITY);var g=((CityGreyboxGenerator)w.getChunkSource().getGenerator()).interiorAt(0,760);LIFTS.clear();
                for(var plan:g.parts())if(plan.lift!=null&&LIFTS.size()<5){w.getChunk(plan.lift.controller());var c=(CityElevatorController)w.getBlockEntity(plan.lift.controller());if(c==null)throw new IllegalStateException("Missing actual generated lift");LIFTS.add(c);c.request(0);}
                if(LIFTS.size()!=5)throw new IllegalStateException("Five actual elevators required");
                var p=mc.getSingleplayerServer().getPlayerList().getPlayer(mc.player.getUUID());
                var view=new Vec3(21.5,1,766.5);if(!CityElevators.validExit(w,view,p))throw new IllegalStateException("Actual common concourse viewpoint");p.teleportTo(w,view.x,view.y,view.z,Set.of(),0,0);
                // Place ten original, normally ticking NPCs around this same public floor; keep AI/rules intact.
                for(int n=0;n<10;n++){var at=CityElevators.safeExit(w,BlockPos.containing(p.position().add((n%5-2)*2,0,5+(n/5)*3)),null).orElseThrow();var npc=CityNpcContent.NPC.get().create(w);if(npc==null)throw new IllegalStateException("NPC factory");npc.setPos(at);npc.setPersistenceRequired();if(!w.addFreshEntity(npc))throw new IllegalStateException("Native NPC admission");NPCS.add(npc);}
                REPORT.put("fixture",Map.of("addedActualNpcs",NPCS.size(),"actualLiftIds",LIFTS.stream().map(CityElevatorController::identity).toList(),"heapMaxMb",ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getMax()/1048576L,"serverType","integrated native server","syntheticConnections",false,"frameCap",mc.options.framerateLimit().get()));return true;
            });
            case "start" -> {server(mc,()->{ARRIVALS.clear();PREVIOUS.clear();stress=true;for(var c:LIFTS)c.request(Math.min(12,c.floors().length-1));return true;});}
            case "five-moving" -> {if(!server(mc,()->LIFTS.size()==5&&LIFTS.stream().allMatch(c->c.phase()==CityElevatorController.Phase.MOVING)))return false;}
            case "begin" -> {previousFrame=0;current=new Window(a[1]);}
            case "end" -> {Window window=current;current=null;server(mc,()->true);if(window==null)throw new IllegalStateException("No sample window");REPORT.put(window.name,Map.of("frameCpuWall",stats(window.frame),"frameStartIntervals",stats(window.intervals),"serverTickCpuWall",stats(window.server),"peakHeapMb",window.peakHeap/1048576.0,"peakSimultaneousMoving",window.peakMoving,"serverTicksWithFiveMoving",window.movingTicks));}
            case "finish" -> {server(mc,()->{stress=false;REPORT.put("completedStops",Map.copyOf(ARRIVALS));if(ARRIVALS.size()<5||ARRIVALS.values().stream().anyMatch(v->v<1))throw new IllegalStateException("Every selected elevator must complete a real stop");return true;});
                REPORT.put("scope","RenderFrameEvent.Pre LOWEST to Post HIGHEST CPU wall time, server tick event interval and heap samples. Frame cap30. Native10 added NPCs and five actual elevators, same scene; not GPU timer, synthetic multiplayer, long soak or hardware-general guarantee.");
                try{Files.writeString(Path.of(System.getProperty("academy.qa.macro.root")).resolve("city-performance.json"),new GsonBuilder().setPrettyPrinting().create().toJson(REPORT)+"\n");}catch(Exception e){throw new IllegalStateException(e);}
            }
            default -> throw new IllegalArgumentException(arg);
        }return true;
    }
    private static Map<String,Object> stats(List<Long> values){if(values.isEmpty())return Map.of("samples",0);long[] sorted=values.stream().mapToLong(Long::longValue).sorted().toArray();return Map.of("samples",sorted.length,"meanMs",Arrays.stream(sorted).average().orElse(0)/1e6,"p50Ms",sorted[(sorted.length-1)/2]/1e6,"p95Ms",sorted[Math.min(sorted.length-1,(int)Math.ceil(sorted.length*.95)-1)]/1e6,"maxMs",sorted[sorted.length-1]/1e6);}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void framePre(RenderFrameEvent.Pre e){frameWindow=current;if(frameWindow!=null){frameStart=System.nanoTime();if(previousFrame!=0)frameWindow.intervals.add(frameStart-previousFrame);previousFrame=frameStart;}}
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void framePost(RenderFrameEvent.Post e){if(frameWindow!=null&&frameWindow==current&&frameWindow.frame.size()<10000)frameWindow.frame.add(System.nanoTime()-frameStart);frameWindow=null;}
    @SubscribeEvent(priority=EventPriority.LOWEST)public static void serverPre(ServerTickEvent.Pre e){tickWindow=current;if(tickWindow!=null)tickStart=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.HIGHEST)public static void serverPost(ServerTickEvent.Post e){
        if(stress)for(var c:LIFTS)if(!c.isRemoved()&&c.boarding())c.request(c.current()==0?Math.min(12,c.floors().length-1):0);
        for(var c:LIFTS){var before=PREVIOUS.put(c.identity(),c.phase());if(c.phase()==CityElevatorController.Phase.DWELL&&before!=null&&before!=c.phase())ARRIVALS.merge(c.identity(),1,Integer::sum);}
        if(tickWindow!=null&&tickWindow==current){tickWindow.server.add(System.nanoTime()-tickStart);int moving=(int)LIFTS.stream().filter(c->c.phase()==CityElevatorController.Phase.MOVING).count();tickWindow.peakMoving=Math.max(tickWindow.peakMoving,moving);if(moving==5)tickWindow.movingTicks++;tickWindow.peakHeap=Math.max(tickWindow.peakHeap,ManagementFactory.getMemoryMXBean().getHeapMemoryUsage().getUsed());}tickWindow=null;
    }
}
