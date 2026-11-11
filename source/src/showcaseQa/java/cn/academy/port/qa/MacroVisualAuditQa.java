package cn.academy.port.qa;

import cn.academy.port.api.SkillAttackEvent;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Passive audit of actual server actions, usable with the a3.61 baseline as well as the upgraded JAR. */
final class MacroVisualAuditQa {
    private static final List<String> visuals=Collections.synchronizedList(new ArrayList<>());
    private static final List<String> received=Collections.synchronizedList(new ArrayList<>());
    private static final List<String> impacts=Collections.synchronizedList(new ArrayList<>());
    private static final List<Map<String,Object>> attacks=Collections.synchronizedList(new ArrayList<>());
    private static final List<Map<String,Object>> trials=new ArrayList<>();
    private static final List<Map<String,Object>> timings=new ArrayList<>();
    private static final List<Map<String,Object>> presentations=new ArrayList<>();
    private static final List<Double> frameCpu=Collections.synchronizedList(new ArrayList<>()),intervals=Collections.synchronizedList(new ArrayList<>()),ticks=Collections.synchronizedList(new ArrayList<>());
    private static volatile String timing;private static String trial;private static int firstVisual,firstAttack;private static long frameStart,lastFrame,tickStart,startTick;
    private static boolean installed,macroAvailable;private static int sampledFrames;private static Consumer<Object> previous;private static java.lang.reflect.Field field;
    private MacroVisualAuditQa(){}
    public static final class Observer {
        @SubscribeEvent public void attack(SkillAttackEvent e){
            attacks.add(Map.of("skill",e.skill,"tick",e.player.serverLevel().getGameTime(),"target",e.target.getUUID().toString(),"requestedAmount",e.amount));
        }
        @SubscribeEvent public void frameStart(net.neoforged.neoforge.client.event.RenderFrameEvent.Pre e){if(timing!=null){long now=System.nanoTime();frameStart=now;if(lastFrame!=0)intervals.add((now-lastFrame)/1e6);lastFrame=now;}}
        @SubscribeEvent public void frameEnd(net.neoforged.neoforge.client.event.RenderFrameEvent.Post e){
            if(timing!=null&&frameStart!=0)frameCpu.add((System.nanoTime()-frameStart)/1e6);
            if(Boolean.getBoolean("academy.qa.coverageRenderSamples")&&trial!=null&&++sampledFrames%12==0)step(Minecraft.getInstance(),"peek");
        }
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.HIGHEST) public void tickStart(net.neoforged.neoforge.event.tick.ServerTickEvent.Pre e){if(timing!=null)tickStart=System.nanoTime();}
        @SubscribeEvent(priority=net.neoforged.bus.api.EventPriority.LOWEST) public void tickEnd(net.neoforged.neoforge.event.tick.ServerTickEvent.Post e){
            if(timing!=null&&tickStart!=0)ticks.add((System.nanoTime()-tickStart)/1e6);
            if(Boolean.getBoolean("academy.qa.coverageRenderSamples")&&Set.of("dark_wing_charge","nitrogen_impact").contains(trial==null?"":trial))
                for(var p:e.getServer().getPlayerList().getPlayers())
                    if(p.serverLevel().getGameTime()-startTick<50) {
                        var hit=cn.academy.port.skill.ClassicRaytrace.living(p,trial.equals("dark_wing_charge")?3:32,net.minecraft.world.level.ClipContext.Fluid.NONE);
                        LogUtils.getLogger().info("QA_R4_AIR_ATTACK skill={} tick={} feet={} eye={} velocity={} known={} ground={} wings={} shell={} ray={}",
                                trial,p.serverLevel().getGameTime(),p.position(),p.getEyePosition(),p.getDeltaMovement(),p.getKnownMovement(),p.onGround(),
                                cn.academy.port.canon.CanonSkillsCombatA.ownsDarkWings(p),cn.academy.port.canon.CanonSkillsCombatA.ownsNitrogenShell(p),hit);
                    }
        }
    }
    @SuppressWarnings("unchecked") static void step(Minecraft mc,String op){
        if(op.equals("init")){
            if(installed)return;installed=true;NeoForge.EVENT_BUS.register(new Observer());
            try{field=Class.forName("cn.academy.port.macro.MacroVfx").getField("observer");previous=(Consumer<Object>)field.get(null);
                field.set(null,(Consumer<Object>)v->{previous.accept(v);visuals.add(v.toString());});macroAvailable=true;
                var receiving=Class.forName("cn.academy.port.macro.MacroVfx").getField("receiver");var previousReceiving=(Consumer<Object>)receiving.get(null);
                receiving.set(null,(Consumer<Object>)v->{previousReceiving.accept(v);received.add(v.toString());});
                try{
                    var impactField=Class.forName("cn.academy.port.macro.BeamImpactVfx").getField("receiver");var previousImpact=(Consumer<Object>)impactField.get(null);
                    impactField.set(null,(Consumer<Object>)v->{previousImpact.accept(v);impacts.add(v.toString());});
                }catch(ClassNotFoundException oldPreview){/* a3.61 and the rejected preview have no result-cell channel. */}
            }catch(ClassNotFoundException baseline){macroAvailable=false;}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
            LogUtils.getLogger().info("QA_MACRO_AUDIT installed=true macroAvailable={}",macroAvailable);
        }else if(op.equals("peek")||op.equals("check-clean")){
            try{
                var c=Class.forName("cn.academy.port.client.canon.MacroVfxClient");var shows=c.getDeclaredField("SHOWS");shows.setAccessible(true);var active=List.copyOf((List<?>)shows.get(null));
                var volume=Class.forName("cn.academy.port.client.canon.AgVolumeRenderer").getDeclaredField("diagnostics");volume.setAccessible(true);
                var data=new LinkedHashMap<String,Object>();data.put("operation",op);data.put("trial",trial==null?"":trial);data.put("activeShows",active.stream().map(Object::toString).toList());data.put("volumeDiagnostics",volume.get(null));data.put("clientTick",mc.level==null?-1:mc.level.getGameTime());
                data.put("player",mc.player==null?"none":mc.player.position().toString());data.put("camera",mc.gameRenderer.getMainCamera().getPosition().toString());
                try{var beam=Class.forName("cn.academy.port.client.canon.AgEnergyBeam").getDeclaredField("diagnostics");beam.setAccessible(true);data.put("beamDiagnostics",beam.get(null));}catch(ClassNotFoundException oldPreview){}
                try{var cNative=Class.forName("cn.academy.port.client.canon.AgNativeBeams");var drawn=cNative.getDeclaredField("drawn");var remaining=cNative.getDeclaredField("remaining");drawn.setAccessible(true);remaining.setAccessible(true);data.put("nativeBeamMeshes",Map.of("drawn",drawn.get(null),"remainingQuads",remaining.get(null)));}catch(ClassNotFoundException oldPreview){}
                presentations.add(data);
                if(op.equals("check-clean")&&!active.isEmpty())throw new IllegalStateException("macro displays remain after lifecycle cleanup");
                LogUtils.getLogger().info("QA_MACRO_PRESENTATION {} active={} volume={}",op,active.size(),volume.get(null));
            }catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
        }else if(op.startsWith("fixture:")){
            MacroCoverageFixturesQa.step(mc,op.substring(8));
        }else if(op.startsWith("bench:")){
            MacroStressQa.step(mc,op.substring(6));
        }else if(op.startsWith("environment:")){
            MacroEnvironmentReloadQa.step(mc,op.substring(12));
        }else if(op.startsWith("npcbeam:")){
            MacroNpcBeamQa.step(mc,op.substring(8));
        }else if(op.startsWith("hand:")){
            mc.options.mainHand().set(op.endsWith("left")?net.minecraft.world.entity.HumanoidArm.LEFT:net.minecraft.world.entity.HumanoidArm.RIGHT);mc.options.broadcastOptions();
        }else if(op.startsWith("mastery:")){
            var args=op.substring(8).split(":");var server=mc.getSingleplayerServer();server.submit(()->{
                var p=server.getPlayerList().getPlayer(mc.player.getUUID());var state=cn.academy.port.AbilityStorage.get(p);state.experience.put(args[0],Double.parseDouble(args[1]));cn.academy.port.AbilityStorage.save(p);cn.academy.port.AcademyNetwork.sync(p);
            }).join();
        }else if(op.startsWith("begin:")){
            trial=op.substring(6);firstVisual=visuals.size();firstAttack=attacks.size();startTick=mc.level.getGameTime();
        }else if(op.equals("end")){
            var events=List.copyOf(visuals.subList(firstVisual,visuals.size()));
            trials.add(Map.of("skill",trial,"startTick",startTick,"endTick",mc.level.getGameTime(),"matchingAcceptedVisuals",events.stream().filter(v->v.contains("skill="+trial+",")).toList(),"attackEvents",List.copyOf(attacks.subList(firstAttack,attacks.size())),"scope","Actual key attempt; accepted visual and attack-request witnesses are separate from gameplay and aesthetic acceptance."));trial=null;
        }else if(op.startsWith("timing-begin:")){
            frameCpu.clear();intervals.clear();ticks.clear();lastFrame=frameStart=tickStart=0;timing=op.substring(13);
        }else if(op.equals("timing-end")){
            String name=timing;timing=null;
            timings.add(Map.of("name",name,"renderCpu",quantiles(frameCpu),"frameIntervals",quantiles(intervals),"serverTickCpu",quantiles(ticks),"scope","CPU wall intervals from native render/tick events, not GPU timings; no frame/audio capture during this window."));
        }else if(op.equals("report")){
            try{var root=Path.of(System.getProperty("academy.qa.macro.root",mc.gameDirectory.toString()));Files.createDirectories(root);
                Files.writeString(root.resolve("actual-cast-audit.json"),new GsonBuilder().setPrettyPrinting().create().toJson(Map.of("macroChannelPresent",macroAvailable,"acceptedServerVisuals",visuals,"receivedServerVisuals",received,"receivedResultCells",impacts,"actualAttackEvents",attacks,"trials",trials,"timings",timings,"presentations",presentations,"scope","Passive events, not injected effects; requested attack events alone do not prove accepted damage.")));
                LogUtils.getLogger().info("QA_MACRO_AUDIT_REPORT visuals={} attackEvents={}",visuals.size(),attacks.size());
            }catch(java.io.IOException e){throw new IllegalStateException(e);}
        }else throw new IllegalArgumentException(op);
    }
    private static Map<String,Object> quantiles(List<Double> data){
        double[] values; synchronized(data){values=data.stream().mapToDouble(Double::doubleValue).sorted().toArray();}
        if(values.length==0)return Map.of("samples",0);
        return Map.of("samples",values.length,"meanMs",Arrays.stream(values).average().orElse(0),"p50Ms",values[(int)Math.ceil(values.length*.5)-1],"p95Ms",values[(int)Math.ceil(values.length*.95)-1],"maxMs",values[values.length-1]);
    }
}
