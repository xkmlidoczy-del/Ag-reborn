package cn.academy.port.client;

import cn.academy.port.highpower.MultidimensionalLandingArray;
import cn.academy.port.highpower.core.SafeLandingProbeGeometry.Point;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.systems.RenderSystem;
import org.joml.*;
import proof.Trace;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Complete frozen/current receipt and world renderer, explicit recording hosts, no GPU/gameplay claim. */
public final class LandingArrayFallbackHarness {
    static final UUID OWNER=new UUID(1,2), TARGET=new UUID(3,4), ZERO=new UUID(0,0);
    static final Minecraft MC=Minecraft.getInstance();
    static BufferedWriter cases,capture;
    static int count,nonempty,totalVertices,totalBatches;
    static long sequence;
    static int spentTotal;
    static String lane;
    static Vec3 camera,forward,impact;
    static List<Vec3> points;
    static List<MultidimensionalLandingArray.TargetVisual> targets;
    static RenderLevelStageEvent event;
    static Object field(Object subject,String name)throws Exception {
        Class<?> type=subject instanceof Class<?> c?c:subject.getClass();Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(subject instanceof Class<?>?null:subject);
    }
    static List<?> cues()throws Exception{return (List<?>)field(MultidimensionalLandingArrayEffects.class,"CUES");}
    static MultidimensionalLandingArrayMesh.VisualState state()throws Exception{return (MultidimensionalLandingArrayMesh.VisualState)field(MultidimensionalLandingArrayEffects.class,"STATE");}
    static String sha(String s)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reset(int scene,boolean hidden)throws Exception {
        MultidimensionalLandingArrayEffects.clear();MC.level=new ClientLevel();MC.player=new Entity(10,OWNER);MC.connection=new Object();MC.screen=null;MC.paused=false;MC.defer=false;MC.queued.clear();MC.options.hideGui=hidden;MC.options.firstPerson=scene%2==0;
        AcademyClient.reset();sequence=0;spentTotal=0;
        camera=scene==3?new Vec3(29_999_968,128,29_999_960):Vec3.ZERO;
        forward=scene==6?new Vec3(0,1,0):new Vec3(0,0,1);
        double depth=switch(scene){case 1->.13;case 2->-3;case 5->.02;case 7->94;default->9;};
        var p=new ArrayList<Vec3>();for(int i=0;i<6;i++)p.add(scene==6?camera.add((i-2)*.8,9,-1.2):camera.add((i-2)*.8,-1.2,depth+(i/3)*1.1));points=List.copyOf(p);
        impact=scene==6?camera.add(.75,12,.4):camera.add(.75,.4,depth+2);
        targets=List.of(new MultidimensionalLandingArray.TargetVisual(TARGET,21,impact,true));
        PoseStack poses=new PoseStack();if(scene==4)poses.matrix.translation(.125f,-.25f,.5f).scale(.875f,1.125f,1);
        event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_PARTICLES,new Camera(camera,new Vector3f((float)forward.x,(float)forward.y,(float)forward.z),new Vector3f(0,1,0)),new Matrix4f(),poses);
        RenderLevelStageEvent.frustum=true;RenderLevelStageEvent.partial=lane.equals("ready-invalid-partial")?Float.NaN:.375f;
        RenderSystem.STACK.value.identity().translation(-4,2,7);RenderSystem.STACK.frames.clear();RenderSystem.color=new float[]{.25f,.5f,.75f,.875f};
        MultidimensionalLandingArrayEffects.localVisual();AcademyClient.accepted=AcademyClient.ended=AcademyClient.rejected=AcademyClient.cancelled=0;
    }
    static MultidimensionalLandingArray.Visual packet(int phase,int n,int shot,boolean accepted,boolean terminal,String reason) {
        boolean prep=phase==1,preview=phase==2,round=phase==7||phase==3||phase==4,medium=phase==3||phase==4;
        int spent=medium?spentTotal=Math.max(spentTotal,shot+1):round?spentTotal:phase>=5?spentTotal:0;
        return new MultidimensionalLandingArray.Visual(OWNER,1,prep?n:preview?7:8,71,++sequence,phase,
            points.subList(0,prep?n:6),prep||preview?400:round?160:0,prep?List.of():targets,prep?0:6,
            spent,spent,prep?0:6,0,false,points.get(medium?shot%6:0).add(0,1.2,0),impact,medium?new UUID(7,shot+1):ZERO,
            medium?shot%6:-1,medium?shot:-1,accepted,terminal,reason);
    }
    static void receive(MultidimensionalLandingArray.Visual p){MultidimensionalLandingArrayEffects.receive(p);}
    static void prepare(int kind)throws Exception {
        int n=kind==0?1:6;for(int i=1;i<=n;i++)receive(packet(1,i,-1,true,false,""));
        require(state().active()!=null&&state().active().points().size()==n,"MARK_SEQUENCE_REJECTED");
        if(kind==0)return;
        receive(packet(2,6,-1,true,false,""));require(state().targetsFrozen(),"PREVIEW_REJECTED");if(kind==1)return;
        receive(packet(7,6,-1,true,false,""));require(state().firing(),"ROUND_REJECTED");if(kind==2)return;
        receive(packet(3,6,0,true,false,""));require(cues().size()==1&&!(boolean)field(cues().getFirst(),"contact"),"PUBLICATION_CUE_MISSING");if(kind==3)return;
        receive(packet(4,6,0,kind!=5,false,""));
        require(cues().size()==1&&(boolean)field(cues().getFirst(),"contact")== (kind!=5),"PHANTOM_OR_MISSING_CONTACT");
        if(kind==6)receive(packet(6,6,-1,false,false,"blocked"));
        if(kind==7)receive(packet(5,6,-1,true,true,""));
    }
    static void tick(){MultidimensionalLandingArrayEffects.tick(new ClientTickEvent.Post());}
    static String stateDump()throws Exception {
        var s=state();StringBuilder b=new StringBuilder("state:").append(s.active()).append(':').append(s.remainingTicks()).append(':').append(s.visibleAge()).append(':').append(s.markersVisible()).append(':').append(s.targetsFrozen()).append(':').append(s.firing()).append(':').append(s.inFlight());
        for(String f:List.of("latest","remaining","quiet","roundOwnership","previewInput","roundInput","sequence","cancelledEpoch","cancelledThrough","retired","firing"))b.append('\n').append(f).append(':').append(field(s,f));
        for(Object shot:(Object[])field(s,"shots"))b.append("\nshot:").append(shot);
        for(Object c:cues())b.append("\ncue:").append(field(c,"receipt")).append(':').append(field(c,"at")).append(':').append(field(c,"contact")).append(':').append(field(c,"age"));
        return b.append("\ninput-host:").append(AcademyClient.accepted).append(':').append(AcademyClient.rejected).append(':').append(AcademyClient.ended).append(':').append(AcademyClient.cancelled).toString();
    }
    static void snap(String name,boolean empty)throws Exception {
        Trace.reset();String before=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();int depth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();float[] color=RenderSystem.color.clone();
        MultidimensionalLandingArrayEffects.render(event);
        require(RenderSystem.STACK.frames.size()==depth&&RenderSystem.STACK.value.toString().equals(before),"MODEL_VIEW_NOT_RESTORED:"+name);
        require(event.getPoseStack().frames.size()==poseDepth&&event.getPoseStack().matrix.toString().equals(pose),"POSE_NOT_RESTORED:"+name);
        require(Arrays.equals(color,RenderSystem.color),"SHADER_COLOR_NOT_RESTORED:"+name);
        require(Trace.vertices%2==0,"INCOMPLETE_PRIMITIVE:"+name);if(empty)require(Trace.vertices==0,"EXPECTED_EMPTY:"+name);
        String rendered=String.join("\n",Trace.rows)+"\nrestored:"+before+":"+depth+":"+pose+":"+poseDepth+":"+Arrays.toString(RenderSystem.color),state=stateDump();
        cases.write(name+"\t"+Trace.vertices+"\t"+Trace.batches+"\t"+sha(rendered)+"\t"+sha(state)+"\n");capture.write("CASE "+name+"\n"+rendered+"\n"+state+"\nEND\n");count++;if(Trace.vertices>0)nonempty++;totalVertices+=Trace.vertices;totalBatches+=Trace.batches;
    }
    static void matrix()throws Exception {
        for(int scene=0;scene<8;scene++)for(boolean hidden:List.of(false,true))for(int kind=0;kind<8;kind++){
            if(lane.equals("ready-near")&&(scene!=1||kind!=3))continue;
            reset(scene,hidden);prepare(kind);
            for(int age=0;age<8;age++){snap("matrix/s"+scene+"/hidden"+hidden+"/kind"+kind+"/age"+age,hidden&&(kind<3||age==7));tick();}
        }
    }
    static void lifecycle()throws Exception {
        reset(0,true);prepare(4);MC.paused=true;for(int i=0;i<20;i++)tick();snap("life/paused-cue-retains-age",false);MC.player.alive=false;tick();snap("life/paused-dead-owner",true);
        for(String change:List.of("world","player","connection","clear","abort","unloaded","occluded","frustum","epoch","control","screen","hidden","wrong-stage")){
            reset(0,true);prepare(4);switch(change){case "world"->MC.level=new ClientLevel();case "player"->MC.player=new Entity(11,OWNER);case "connection"->MC.connection=new Object();case "clear"->MultidimensionalLandingArrayEffects.clear();case "abort"->MultidimensionalLandingArrayEffects.abortLocal(8,1);case "unloaded"->MC.level.chunks=false;case "occluded"->MC.level.blocked=true;case "frustum"->RenderLevelStageEvent.frustum=false;case "epoch"->AcademyClient.epoch=2;case "control"->AcademyClient.control=false;case "screen"->MC.screen=new Object();case "hidden"->MC.options.hideGui=true;case "wrong-stage"->event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_ENTITIES,event.getCamera(),event.getModelViewMatrix(),event.getPoseStack());}
            tick();snap("life/"+change,!List.of("control","screen","hidden").contains(change));
        }
        reset(0,true);prepare(4);receive(packet(5,6,-1,true,true,"cancelled"));snap("life/cancelled-end-clears-cue",true);
        reset(0,true);prepare(3);var duplicate=packet(3,6,0,true,false,"");receive(duplicate);snap("life/duplicate-shot-rejected",false);receive(duplicate);snap("life/stale-sequence-rejected",false);
        reset(0,false);prepare(1);for(int i=0;i<31;i++)tick();snap("life/heartbeat-silence-hides-markers",true);
        for(String invalidate:List.of("clear","session","world","connection")){
            reset(0,true);prepare(2);MC.defer=true;receive(packet(3,6,0,true,false,""));switch(invalidate){case "clear"->MultidimensionalLandingArrayEffects.clear();case "session"->AcademyClient.session++;case "world"->MC.level=new ClientLevel();case "connection"->MC.connection=new Object();}MC.drain();snap("life/queued-"+invalidate,true);require(cues().isEmpty(),"STALE_QUEUED_CUE");
        }
        reset(0,true);prepare(2);for(int shot=0;shot<6;shot++)receive(packet(3,6,shot,true,false,""));require(cues().size()==6,"SIX_CUE_BOUND");snap("bounds/six-source-cues",false);
        for(int shot=0;shot<6;shot++)receive(packet(4,6,shot,true,false,""));require(cues().size()==6,"IMPACT_REPLACES_SOURCE");for(Object c:cues())require((boolean)field(c,"contact"),"ALL_SIX_IMPACTS_ACCEPTED");snap("bounds/six-contact-cues",false);
        reset(3,false);prepare(1);MC.player.hand.clean=false;snap("preview/patched-item-not-advised",false);MC.player.hand.clean=true;MC.player.hand.count=5;snap("preview/five-items-not-advised",false);
    }
    static void prepareMaterial(String lane,Path resourcesRoot)throws Exception {
        if(lane.equals("frozen"))return;
        Class<?> config=Class.forName("cn.academy.port.HighPowerVisualConfig");Object setting=config.getField("LANDING_ARRAY_CUES").get(null);
        require(!(boolean)setting.getClass().getMethod("get").invoke(setting),"DEFAULT_MUST_BE_OFF");
        setting.getClass().getMethod("set",boolean.class).invoke(setting,!lane.equals("off"));
        net.neoforged.neoforge.common.ModConfigSpec.loaded=!lane.equals("config-unloaded");
        var reload=new RegisterClientReloadListenersEvent();Class<?> material=Class.forName("cn.academy.port.client.LandingArrayCueMaterial");
        Class.forName("cn.academy.port.client.LandingArrayCueMaterial$Reload").getMethod("register",RegisterClientReloadListenersEvent.class).invoke(null,reload);
        var resources=new net.minecraft.server.packs.resources.ResourceManager(resourcesRoot,lane.equals("missing")?"missing":lane.equals("corrupt")?"corrupt":"valid");reload.fire(resources);
        require((boolean)field(material,"ready")==!List.of("missing","corrupt").contains(lane),"MATERIAL_READINESS_WRONG:"+lane);
        require(resources.opened==(List.of("missing","corrupt").contains(lane)?1:9),"MATERIAL_RELOAD_NOT_EXECUTED");
        var shaders=new RegisterShadersEvent(new net.minecraft.server.packs.resources.ResourceManager(resourcesRoot,"valid"));shaders.defer=lane.equals("shader-pending")||lane.equals("shader-stale");
        net.minecraft.client.renderer.ShaderInstance.failCompile=lane.equals("shader-compile");org.lwjgl.opengl.GL20.linked=!lane.equals("shader-link");
        Class<?> soft=Class.forName("cn.academy.port.client.RailThunderArraySoftMaterial");var register=soft.getDeclaredMethod("register",RegisterShadersEvent.class);register.setAccessible(true);register.invoke(null,shaders);
        if(lane.equals("shader-stale")){net.minecraft.client.renderer.ShaderInstance.failCompile=true;register.invoke(null,new RegisterShadersEvent(new net.minecraft.server.packs.resources.ResourceManager(resourcesRoot,"valid")));net.minecraft.client.renderer.ShaderInstance.failCompile=false;shaders.finish();}
        var available=soft.getDeclaredMethod("available");available.setAccessible(true);require((boolean)available.invoke(null)==!lane.startsWith("shader-"),"SHADER_LIFECYCLE_NOT_EXECUTED:"+lane);
        require(net.minecraft.client.renderer.ShaderInstance.attempts>0,"SHADER_CONSTRUCTOR_NOT_EXERCISED");if(lane.equals("shader-link"))require(net.minecraft.client.renderer.ShaderInstance.closed==1,"UNLINKED_SHADER_NOT_CLOSED");
        var enabled=material.getDeclaredMethod("enabled");enabled.setAccessible(true);require((boolean)enabled.invoke(null)==lane.startsWith("ready-"),"FALLBACK_LANE_MATERIAL_ENABLED:"+lane);
    }
    public static void main(String[] args)throws Exception {
        lane=args[0];prepareMaterial(lane,Path.of(args[2]));Path out=Path.of(args[1]);Files.createDirectories(out);
        try(var c=Files.newBufferedWriter(out.resolve("cases.tsv"));var gz=new GZIPOutputStream(Files.newOutputStream(out.resolve("capture.txt.gz")));var full=new BufferedWriter(new OutputStreamWriter(gz,StandardCharsets.UTF_8))){cases=c;capture=full;matrix();if(!lane.equals("ready-near"))lifecycle();}
        if(!Boolean.getBoolean("proof.mutant"))require(lane.equals("ready-near")?nonempty>=10&&totalVertices>100:nonempty>500&&totalVertices>100_000,"VACUOUS_MATRIX:"+nonempty+":"+totalVertices);
        System.out.println("{\"lane\":\""+lane+"\",\"cases\":"+count+",\"nonempty_cases\":"+nonempty+",\"vertices\":"+totalVertices+",\"batches\":"+totalBatches+"}");
    }
}
