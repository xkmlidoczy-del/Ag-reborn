package cn.academy.port.qa;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.TreeSet;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/**
 * Opt-in, read-only native timing witnesses. No timer query, GL sync, frame cap,
 * game state, shader mode, particle or effect is changed. Samples stay in memory
 * until the Fx4RolloutQa session changes or the client leaves its world.
 *
 * NeoForge 21.1.252 supplies RenderFrameEvent.Pre/Post and the priority-ordered
 * RenderLevelStageEvent. These are CPU-side wall intervals, never GPU timings.
 */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class Fx4RenderMetricsQa {
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.fx4.metrics");
    private static final String RUN=System.getProperty("academy.qa.fx4.run","");
    private static final Path ROOT=Path.of(System.getProperty("academy.qa.fx4.root",".")).toAbsolutePath().normalize();
    private static final int MAX_QUANTILE_SAMPLES=131072;
    private static Bridge bridge;
    private static boolean failed;
    private static Window current,frameWindow,stageWindow;
    private static long frameStart,stageStart;
    private Fx4RenderMetricsQa() {}

    /** One primitive buffer per statistic; no boxing or sorting in a frame. */
    private static final class Samples {
        private long[] values=new long[256];
        private int stored;
        private long count,max;
        private double total;
        void add(long ns){
            if(ns<0)return;
            count++;total+=ns;max=Math.max(max,ns);
            if(stored>=MAX_QUANTILE_SAMPLES)return;
            if(stored==values.length)values=Arrays.copyOf(values,Math.min(MAX_QUANTILE_SAMPLES,values.length*2));
            values[stored++]=ns;
        }
        JsonObject json(){
            var j=new JsonObject();j.addProperty("samples",count);j.addProperty("quantileSamples",stored);
            j.addProperty("quantileSamplesOmitted",count-stored);
            j.addProperty("quantileMethod",count==stored?"nearest-rank over every sample":"nearest-rank over bounded chronological prefix; mean/max still use all samples");
            if(count==0){for(String key:new String[]{"meanMs","p50Ms","p95Ms","maxMs"})j.add(key,JsonNull.INSTANCE);return j;}
            long[] ordered=Arrays.copyOf(values,stored);Arrays.sort(ordered);
            j.addProperty("meanMs",total/count/1_000_000d);
            j.addProperty("p50Ms",ordered[Math.max(0,(int)Math.ceil(stored*.50)-1)]/1_000_000d);
            j.addProperty("p95Ms",ordered[Math.max(0,(int)Math.ceil(stored*.95)-1)]/1_000_000d);
            j.addProperty("maxMs",max/1_000_000d);return j;
        }
    }

    private static final class Window {
        final Object session,world;
        final int serial;
        final long openedNs,startTick;
        final JsonObject identity,shaderState;
        final Samples afterParticles=new Samples(),renderFrame=new Samples(),frameIntervals=new Samples();
        long previousFrameStart;
        int peakParticles,peakEffects,minEffects=Integer.MAX_VALUE;
        long effectCountSamples;
        boolean closed;
        Window(Minecraft mc,Object session,int serial) throws ReflectiveOperationException {
            this.session=session;this.serial=serial;world=mc.level;openedNs=System.nanoTime();startTick=mc.level.getGameTime();
            identity=new JsonObject();identity.addProperty("clip",serial);
            identity.addProperty("globalSerialAtOpen",bridge.serial.getInt(null));
            identity.addProperty("skill",String.valueOf(bridge.skill.get(session)));
            identity.addProperty("mode",String.valueOf(bridge.mode.get(session)));
            identity.addProperty("category",String.valueOf(bridge.category.get(session)));
            identity.addProperty("skillLevel",bridge.level.getInt(session));
            identity.addProperty("dimension",mc.level.dimension().location().toString());
            identity.addProperty("configuredFpsCap",mc.options.framerateLimit().get());
            identity.addProperty("configuredVsync",mc.options.enableVsync().get());
            identity.addProperty("framebufferWidth",mc.getMainRenderTarget().width);
            identity.addProperty("framebufferHeight",mc.getMainRenderTarget().height);
            shaderState=bridge.shaderState();
        }
    }

    /** Cache reflected readers once; never write a target field. */
    private static final class Bridge {
        final Field active,serial,sessionSerial,sessionWorld,ready,skill,mode,category,level;
        final Field loaded,particles,effects;
        final Method shaders,effective,configuredShaders,shaderPackActive;
        final Object[] modes;
        Bridge() throws ReflectiveOperationException {
            Class<?> qa=Class.forName("cn.academy.port.qa.Fx4RolloutQa");
            active=field(qa,"active");serial=field(qa,"serial");
            Class<?> session=active.getType();
            sessionSerial=field(session,"serial");sessionWorld=field(session,"world");ready=field(session,"ready");
            skill=field(session,"skill");mode=field(session,"mode");category=field(session,"category");level=field(session,"level");
            Class<?> fx=Class.forName("cn.academy.port.client.canon.CanonFx4");
            Class<?> fxMode=Class.forName("cn.academy.port.client.canon.CanonFx4$Mode");
            loaded=field(fx,"LOADED");shaders=fx.getDeclaredMethod("shaders");shaders.setAccessible(true);
            configuredShaders=Class.forName("cn.academy.port.client.canon.CanonFxConfig").getDeclaredMethod("shaders");configuredShaders.setAccessible(true);
            shaderPackActive=fx.getDeclaredMethod("shaderPackActive");shaderPackActive.setAccessible(true);
            effective=fx.getDeclaredMethod("effective",fxMode);effective.setAccessible(true);modes=fxMode.getEnumConstants();
            particles=field(Class.forName("cn.academy.port.client.canon.CanonParticles"),"LIVE");
            effects=field(Class.forName("cn.academy.port.client.canon.CanonVfxClient"),"EFFECTS");
        }
        JsonObject shaderState() throws ReflectiveOperationException {
            var j=new JsonObject();var names=new JsonArray();var sorted=new TreeSet<String>();
            Map<?,?> nativeLoaded=(Map<?,?>)loaded.get(null);for(Object name:nativeLoaded.keySet())sorted.add(String.valueOf(name));
            sorted.forEach(names::add);j.add("loadedShaderNames",names);j.addProperty("loadedShaderCount",nativeLoaded.size());
            j.addProperty("shaders",(Boolean)shaders.invoke(null));
            j.addProperty("configuredShaders",(Boolean)configuredShaders.invoke(null));
            j.addProperty("shaderPackDetectedActive",(Boolean)shaderPackActive.invoke(null));
            var irisState=new JsonObject();
            try{
                Class<?> iris=Class.forName("net.irisshaders.iris.Iris");
                irisState.addProperty("present",true);
                irisState.addProperty("version",String.valueOf(iris.getMethod("getVersion").invoke(null)));
                irisState.addProperty("currentPackName",String.valueOf(iris.getMethod("getCurrentPackName").invoke(null)));
                irisState.addProperty("currentPackPresent",((java.util.Optional<?>)iris.getMethod("getCurrentPack").invoke(null)).isPresent());
                irisState.addProperty("fallback",(Boolean)iris.getMethod("isFallback").invoke(null));
                irisState.addProperty("incompatiblePack",(Boolean)iris.getMethod("loadedIncompatiblePack").invoke(null));
            }catch(ClassNotFoundException absent){irisState.addProperty("present",false);}
            catch(ReflectiveOperationException|LinkageError unavailable){irisState.addProperty("queryError",unavailable.toString());}
            j.add("iris",irisState);
            var resolved=new JsonObject();
            for(Object requested:modes){Object actual=effective.invoke(null,requested);
                if(actual==null)resolved.add(String.valueOf(requested),JsonNull.INSTANCE);
                else resolved.addProperty(String.valueOf(requested),String.valueOf(actual));}
            j.add("effectiveModes",resolved);j.addProperty("sampled","once at ready-session open, queried from native fields/methods");return j;
        }
        private static Field field(Class<?> owner,String name) throws NoSuchFieldException {
            var f=owner.getDeclaredField(name);f.setAccessible(true);return f;
        }
    }

    /** May flush on a transition; never writes for an ordinary frame or tick. */
    private static Window synchronize(){
        if(!ENABLED||failed)return null;
        try{
            if(bridge==null)bridge=new Bridge();
            var mc=Minecraft.getInstance();Object session=bridge.active.get(null);
            if(mc.level==null||mc.player==null){flush("world_exit");return null;}
            if(current!=null&&current.world!=mc.level)flush("world_change");
            if(session==null){flush("session_end");return null;}
            int serial=bridge.sessionSerial.getInt(session);
            if(current!=null&&(current.session!=session||current.serial!=serial))flush("session_switch");
            if(bridge.sessionWorld.get(session)!=mc.level||!bridge.ready.getBoolean(session))return null;
            if(current==null)current=new Window(mc,session,serial);
            return current;
        }catch(ReflectiveOperationException|RuntimeException|LinkageError error){fail(error);return null;}
    }

    private static boolean sameSession(Window w){
        if(w==null||w.closed||w!=current||failed)return false;
        try{return Minecraft.getInstance().level==w.world&&bridge.active.get(null)==w.session;}
        catch(ReflectiveOperationException|RuntimeException error){fail(error);return false;}
    }
    private static void peaks(Window w){
        if(w==null||w.closed||failed)return;
        try{
            w.peakParticles=Math.max(w.peakParticles,((Collection<?>)bridge.particles.get(null)).size());
            int effects=((Collection<?>)bridge.effects.get(null)).size();
            w.peakEffects=Math.max(w.peakEffects,effects);w.minEffects=Math.min(w.minEffects,effects);w.effectCountSamples++;
        }catch(ReflectiveOperationException|RuntimeException error){fail(error);}
    }

    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void stageStart(RenderLevelStageEvent event){
        if(!ENABLED||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        stageWindow=synchronize();peaks(stageWindow);stageStart=stageWindow==null?0:System.nanoTime();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void stageEnd(RenderLevelStageEvent event){
        if(!ENABLED||event.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        long end=System.nanoTime();Window w=stageWindow;long start=stageStart;stageWindow=null;stageStart=0;
        if(start!=0&&sameSession(w)){w.afterParticles.add(end-start);peaks(w);}
    }

    /** Start late in Pre so the matching early Post excludes other frame-event witnesses. */
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void frameStart(RenderFrameEvent.Pre event){
        if(!ENABLED)return;
        frameWindow=synchronize();peaks(frameWindow);
        frameStart=frameWindow==null?0:System.nanoTime();
        if(frameWindow!=null){
            if(frameWindow.previousFrameStart!=0)frameWindow.frameIntervals.add(frameStart-frameWindow.previousFrameStart);
            frameWindow.previousFrameStart=frameStart;
        }
    }
    @SubscribeEvent(priority=EventPriority.HIGHEST)
    public static void frameEnd(RenderFrameEvent.Post event){
        if(!ENABLED)return;
        long end=System.nanoTime();Window w=frameWindow;long start=frameStart;frameWindow=null;frameStart=0;
        if(start!=0&&sameSession(w)){w.renderFrame.add(end-start);peaks(w);}
        synchronize();
    }
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void tick(ClientTickEvent.Post event){if(ENABLED)synchronize();}
    @SubscribeEvent(priority=EventPriority.LOWEST)
    public static void logout(ClientPlayerNetworkEvent.LoggingOut event){if(ENABLED)flush("world_exit_event");}

    private static void flush(String reason){
        Window w=current;if(w==null||w.closed)return;current=null;w.closed=true;
        if(frameWindow==w){frameWindow=null;frameStart=0;}
        if(stageWindow==w){stageWindow=null;stageStart=0;}
        var data=w.identity.deepCopy();data.addProperty("closeReason",reason);data.addProperty("startClientTick",w.startTick);
        var mc=Minecraft.getInstance();if(mc.level==w.world)data.addProperty("endClientTick",mc.level.getGameTime());
        data.addProperty("observedSessionWallMs",(System.nanoTime()-w.openedNs)/1_000_000d);
        data.add("afterParticlesDispatchCpuWall",w.afterParticles.json());
        data.add("renderFrameCpuWall",w.renderFrame.json());data.add("frameStartIntervals",w.frameIntervals.json());
        data.add("nativeShaderState",w.shaderState);data.addProperty("peakCanonParticlesLive",w.peakParticles);
        data.addProperty("peakCanonVfxEffects",w.peakEffects);
        if(w.effectCountSamples==0)data.add("minCanonVfxEffects",JsonNull.INSTANCE);
        else data.addProperty("minCanonVfxEffects",w.minEffects);
        data.addProperty("nativeEffectPoolSamples",w.effectCountSamples);
        data.addProperty("particleCountDefinition","native CanonParticles.LIVE size, including delayed entries; sampled at frame/stage boundaries");
        data.addProperty("effectCountDefinition","native CanonVfxClient.EFFECTS size, not a count of visible pixels or confirmed hits");
        var scope=new JsonObject();
        scope.addProperty("afterParticles","System.nanoTime HIGHEST-to-LOWEST timestamps during AFTER_PARTICLES dispatch; includes intervening listeners and GL driver submission/waits; excludes this observer's boundary bookkeeping");
        scope.addProperty("renderFrame","RenderFrameEvent.Pre LOWEST-to-Post HIGHEST wall interval around GameRenderer; other peer-priority handler order is not controlled; includes lightweight stage telemetry but not this observer's JSONL flush");
        scope.addProperty("frameIntervals","consecutive Pre timestamps within one session; includes runner FPS cap, CPU scheduling, screenshot work and QA pacing outside GameRenderer");
        scope.addProperty("window","entire ready Fx4RolloutQa session, including lead-in/tail; not an isolated incremental skill cost");
        scope.addProperty("gpuTiming",false);scope.addProperty("gpuSynchronizeOrTimerQueries",false);
        scope.addProperty("observerWritesPerOrdinaryFrame",0);scope.addProperty("observerRecordPolicy","one buffered summary per session, on transition/world exit");
        scope.addProperty("rendererOrGameplayStateChanged",false);data.add("measurementScope",scope);
        write("session-summary",data);
    }
    private static void fail(Throwable error){
        if(failed)return;failed=true;
        var data=new JsonObject();data.addProperty("status","telemetry_unavailable");data.addProperty("error",error.toString());
        data.addProperty("scope","Metrics failure only; no native renderer or gameplay state was changed. Do not infer performance results from missing summaries.");
        write("telemetry-error",data);
    }
    private static void write(String event,JsonObject data){
        var row=new JsonObject();row.addProperty("run",RUN);row.addProperty("event",event);row.addProperty("time",System.currentTimeMillis());row.add("data",data);
        try{Files.createDirectories(ROOT);Files.writeString(ROOT.resolve("fx4-render-metrics.jsonl"),row+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);}
        catch(java.io.IOException error){failed=true;LogUtils.getLogger().error("QA_FX4_RENDER_METRICS_WRITE_FAILED {}",error.toString());}
    }
}
