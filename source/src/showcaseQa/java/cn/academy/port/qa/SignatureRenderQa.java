package cn.academy.port.qa;

import com.google.gson.Gson;
import com.mojang.logging.LogUtils;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderFrameEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;

/** Hidden visual fixtures and read-only counters. Preview performance is not a network or GPU benchmark. */
@EventBusSubscriber(modid="academy",value=Dist.CLIENT)
public final class SignatureRenderQa {
    private static final boolean ENABLED=Boolean.getBoolean("academy.qa.signatureBudget");
    private static final String PREFIX="cn.academy.port.client.canon.";
    private static String label="";
    private static long frameStart,stageStart;
    private static final List<Long> FRAMES=new ArrayList<>(),STAGES=new ArrayList<>();
    private static int peakQuads,peakParticles,peakGroups,droppedFrames;
    private static Map<String,Integer> lastGroups=Map.of();
    private SignatureRenderQa(){}

    private static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static Object stat(Object value,String name)throws Exception{var m=value.getClass().getDeclaredMethod(name);m.setAccessible(true);return m.invoke(value);}
    private static Class<?> type(String name)throws Exception{return Class.forName(PREFIX+name);}
    private static void require(boolean ok,String why){if(!ok)throw new IllegalStateException("QA_SIGNATURE_FAILED "+why);}
    private static void clear(String name)throws Exception{var m=type(name).getDeclaredMethod("clear");m.setAccessible(true);m.invoke(null);}

    public static void run(Minecraft mc,String command){
        require(ENABLED,"read-only signature counters must be explicitly enabled");
        try{
            if(command.equals("clear")){
                for(String n:new String[]{"CanonParticles","CanonDecals","CanonUltimates","ClassicSignatures","CanonWarps","CanonFxForms"})clear(n);
                ((Collection<?>)field(type("CanonVfxClient"),"EFFECTS").get(null)).clear();
                field(type("CanonFx"),"lastStats").set(null,null);
            }else if(command.startsWith("begin:")){
                label=command.substring(6);FRAMES.clear();STAGES.clear();peakQuads=peakParticles=peakGroups=droppedFrames=0;lastGroups=Map.of();
            }else if(command.startsWith("check:")){
                int expected=Integer.parseInt(command.substring(6));sample();
                long positive=lastGroups.entrySet().stream().filter(e->e.getKey().startsWith("view:")&&e.getValue()>0).count();
                require(positive>=expected,"every simultaneous preview must receive quads: "+lastGroups);
                require(peakQuads<=2600,"shared quad cap");
                LogUtils.getLogger().info("QA_SIGNATURE_BUDGET_PASS expected={} positive={} quads={} groups={}",expected,positive,peakQuads,lastGroups);
            }else if(command.equals("end")){
                var out=new LinkedHashMap<String,Object>();out.put("window",label);out.put("frameCpuWall",summary(FRAMES));out.put("afterParticlesCpuWall",summary(STAGES));
                out.put("peakQuads",peakQuads);out.put("peakParticles",peakParticles);out.put("peakDrawGroups",peakGroups);out.put("samplesWithLodClipping",droppedFrames);out.put("lastGroups",lastGroups);
                out.put("scope","Synthetic client previews; CPU wall intervals include GL driver submission. No GPU timer or real multiplayer load. Rendering capped at 2600 quads; per-cast LOD clipping is explicit.");
                write(out);label="";
            }else if(command.equals("invariants")){
                var own=(java.util.Set<?>)field(type("CanonFxClassic"),"OWN").get(null);
                var classic=(Map<?,?>)field(type("ClassicSignatures"),"SIG").get(null);
                var canon=(Map<?,?>)field(type("CanonSignatures"),"SIG").get(null);
                require(own.size()==17&&own.stream().noneMatch(classic::containsKey),"17 bespoke renderers excluded from signatures");
                for(var skill:cn.academy.port.SkillCatalog.REGISTERED)if(java.util.Set.of("electromaster","meltdowner","teleporter","vecmanip").contains(skill.category())&&skill.controllable()&&!own.contains(skill.id()))
                    require(classic.containsKey(skill.id()),"classic active signature coverage: "+skill.id());
                for(var skill:cn.academy.port.canon.CanonSkills.ALL){
                    boolean hydro=skill.category().equals("hydro_hand");require(canon.containsKey(skill.id())!=hydro,"signature coverage and hydro exclusion: "+skill.id());
                }
                write(Map.of("invariantsPassed",true,"ownExcluded",own.size(),"classicSignatures",classic.size(),"newSignatures",canon.size()));
                LogUtils.getLogger().info("QA_SIGNATURE_INVARIANTS_PASS own={} classic={} new={}",own.size(),classic.size(),canon.size());
            }else if(command.equals("arc-long")){
                var arcs=(List<?>)field(Class.forName("cn.academy.port.client.ClassicEffects"),"ARCS").get(null);
                require(arcs.size()==2,"one distant arc uses two native strokes");double total=0;Vec3 previous=null;
                for(var arc:arcs){
                    double length=(double)field(arc.getClass(),"length").get(arc);Vec3 from=(Vec3)field(arc.getClass(),"origin").get(arc),direction=(Vec3)field(arc.getClass(),"direction").get(arc);
                    require(length>0&&length<=20,"native stroke material bound");if(previous!=null)require(previous.distanceTo(from)<1e-6,"adjacent native strokes join exactly");
                    total+=length;previous=from.add(direction.scale(length));
                }
                require(total>20&&total<=24,"actual long arc reaches beyond the old client cap");
                write(Map.of("nativeArcChain",true,"strokes",arcs.size(),"totalLength",total,"endpoint",previous.toString()));
                LogUtils.getLogger().info("QA_NATIVE_ARC_CHAIN_PASS strokes={} totalLength={} endpoint={}",arcs.size(),total,previous);
            }else if(command.startsWith("env:")){
                clear("CanonParticles");clear("CanonDecals");
                String name=command.substring(4);Class<?> kind=type("CanonEnvironment$Kind");Object k=Enum.valueOf((Class)kind,name);
                Method impact=type("CanonEnvironment").getDeclaredMethod("impact",Vec3.class,double.class,kind,int.class);impact.setAccessible(true);
                impact.invoke(null,mc.player.position().add(0,.2,0),1d,k,0);
                var particles=(List<?>)field(type("CanonParticles"),"LIVE").get(null);int count=particles.size();
                if(name.equals("MIND"))require(count==0,"mental influence must not create physical debris");
                if(name.equals("FLOAT")){
                    require(count==5,"float emits five light dust particles");
                    for(var p:particles)require(field(p.getClass(),"tex").get(p).equals("ah_dust_sheet"),"float dust without rock chunks");
                }
                Object rgb=count==0?List.of():field(particles.get(0).getClass(),"rgb").get(particles.get(0));
                write(Map.of("environment",name,"particles",count,"firstRgb",rgb,"dayTime",mc.level.getDayTime(),"skyLightFactor",mc.level.getSkyDarken(1f),"clientOnly",true));
                LogUtils.getLogger().info("QA_SIGNATURE_ENV_PASS kind={} particles={}",name,count);
            }else throw new IllegalArgumentException(command);
        }catch(Exception ex){throw new IllegalStateException("QA_SIGNATURE_FAILED "+command,ex);}
    }

    private static Map<String,Object> summary(List<Long> values){
        if(values.isEmpty())return Map.of("samples",0);
        var sorted=values.stream().mapToLong(Long::longValue).sorted().toArray();
        return Map.of("samples",sorted.length,"meanMs",values.stream().mapToLong(Long::longValue).average().orElse(0)/1e6,
                "p95Ms",sorted[Math.max(0,(int)Math.ceil(sorted.length*.95)-1)]/1e6,"maxMs",sorted[sorted.length-1]/1e6);
    }
    @SuppressWarnings("unchecked") private static void sample()throws Exception{
        Object stats=field(type("CanonFx"),"lastStats").get(null);if(stats==null)return;
        peakQuads=Math.max(peakQuads,(int)stat(stats,"quads"));
        if((int)stat(stats,"dropped")>0)droppedFrames++;
        lastGroups=(Map<String,Integer>)stat(stats,"groups");peakGroups=Math.max(peakGroups,lastGroups.size());
        peakParticles=Math.max(peakParticles,((Collection<?>)field(type("CanonParticles"),"LIVE").get(null)).size());
    }
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void frameStart(RenderFrameEvent.Pre e){if(ENABLED&&!label.isEmpty())frameStart=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void frameEnd(RenderFrameEvent.Post e){if(ENABLED&&!label.isEmpty()&&frameStart!=0){FRAMES.add(System.nanoTime()-frameStart);frameStart=0;}}
    @SubscribeEvent(priority=EventPriority.HIGHEST) public static void stageStart(RenderLevelStageEvent e){if(ENABLED&&!label.isEmpty()&&e.getStage()==RenderLevelStageEvent.Stage.AFTER_PARTICLES)stageStart=System.nanoTime();}
    @SubscribeEvent(priority=EventPriority.LOWEST) public static void stageEnd(RenderLevelStageEvent e){
        if(!ENABLED||label.isEmpty()||e.getStage()!=RenderLevelStageEvent.Stage.AFTER_PARTICLES)return;
        if(stageStart!=0){STAGES.add(System.nanoTime()-stageStart);stageStart=0;}
        try{sample();}catch(Exception ex){throw new IllegalStateException("QA_SIGNATURE_METRICS_FAILED",ex);}
    }
    private static void write(Map<String,?> value)throws Exception{
        var p=Path.of(System.getProperty("academy.qa.signatureRoot","."),"signature-metrics.jsonl");Files.createDirectories(p.getParent());
        Files.writeString(p,new Gson().toJson(value)+"\n",StandardOpenOption.CREATE,StandardOpenOption.APPEND);
    }
}
