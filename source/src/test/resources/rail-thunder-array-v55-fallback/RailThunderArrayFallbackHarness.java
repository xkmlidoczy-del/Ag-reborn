package cn.academy.port.client;

import cn.academy.port.highpower.RailThunderArray;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.*;
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

/** Runs the complete renderer, packet receipt and lease methods against explicit recording hosts.
 * The GPU, world queries and AcademyClient eligibility are controlled hosts, not game-runtime claims. */
public final class RailThunderArrayFallbackHarness {
    static final UUID OWNER=new UUID(1,2), TARGET=new UUID(3,4), LOCAL=new UUID(5,6);
    static final Vec3 FORWARD=new Vec3(0,0,1);
    static final List<Vec3> AXES=List.of(FORWARD,new Vec3(1,0,0),new Vec3(0,1,0));
    static final Minecraft MC=Minecraft.getInstance();
    private static BufferedWriter cases,capture;
    private static int count,nonempty,totalVertices,totalBatches;
    private static String lane;
    static Entity owner,target;
    static Vec3 camera,origin,to;
    static RenderLevelStageEvent event;

    static Object field(Object subject,String name)throws Exception {
        Class<?> type=subject instanceof Class<?> c?c:subject.getClass();Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(subject instanceof Class<?>?null:subject);
    }
    static Map<?,?> markers()throws Exception{return (Map<?,?>)field(RailThunderArrayEffects.class,"MARKERS");}
    static String sha(String s)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8)));}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reset(int scene,int view) {
        RailThunderArrayEffects.clear();MC.level=new ClientLevel();MC.player=new Entity(10,LOCAL);MC.connection=new Object();MC.screen=null;MC.active=true;MC.paused=false;
        AcademyClient.state.activated=true;AcademyClient.state.overloadFine=true;AcademyClient.state.interfering=false;AcademyClient.state.level=5;
        MC.options.firstPerson=view!=2;MC.player.arm=view==1?HumanoidArm.LEFT:HumanoidArm.RIGHT;
        AcademyClient.epoch=1;AcademyClient.input=1;
        owner=view==3?new Entity(11,OWNER):MC.player;target=new Entity(12,TARGET);
        MC.level.entities.put(owner.id,owner);MC.level.entities.put(MC.player.id,MC.player);MC.level.entities.put(target.id,target);
        camera=scene==3?new Vec3(29_999_968,128,29_999_960):Vec3.ZERO;
        origin=switch(scene){case 1->camera.add(.02,-.01,.12);case 2->camera.add(-2,.4,-.8);case 4->camera.add(.4,.2,-2);case 5->camera.add(.08,0,.19);default->camera;};
        to=switch(scene){case 1->camera.add(.1,.05,.35);case 2->camera.add(3,1,18);case 3->camera.add(8,2,28);case 4->camera.add(1,.2,-.3);case 5->camera.add(.08,0,12);default->camera.add(.3,.15,24);};
        PoseStack pose=new PoseStack();if(scene==2)pose.matrix.translation(.125f,-.25f,.5f);
        event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_PARTICLES,new Camera(camera,new Vector3f(0,0,1),new Vector3f(0,1,0)),new Matrix4f().translation(2,3,4),pose);
        RenderSystem.STACK.value.identity().translation(-4,2,7);RenderSystem.STACK.frames.clear();
        RailThunderArrayEffects.localVisual();
    }
    static RailThunderArray.Visual packet(int phase,int status,long seq,int age,int reason) {
        List<RailThunderArray.Segment> segs=phase==RailThunderArrayState.ENDED?List.of():List.of(new RailThunderArray.Segment(0,status==RailThunderArrayState.HIT?target.id:-1,status==RailThunderArrayState.HIT?TARGET:RailThunderArrayState.NO_TARGET,origin,to,status));
        return new RailThunderArray.Visual(owner.id,owner.uuid,1,71,1,seq,phase,age,origin,AXES,segs,reason);
    }
    static void receive(int phase,int status){RailThunderArrayEffects.receive(packet(phase,status,1,0,0));}
    static void tick(){RailThunderArrayEffects.tick(new ClientTickEvent.Post());}
    static String state()throws Exception {
        RailThunderArrayState state=(RailThunderArrayState)field(RailThunderArrayEffects.class,"STATE");StringBuilder b=new StringBuilder("state:").append(state.size()).append(':').append(state.watermarkCount()).append(':').append(markers().size());
        for(var e:markers().entrySet()){
            Object m=e.getValue();RailThunderArray.Visual p=(RailThunderArray.Visual)field(m,"state");RailThunderArrayState.Lease lease=(RailThunderArrayState.Lease)field(m,"lease");
            b.append("\nmarker:").append(e.getKey()).append(':').append(p).append(':').append(lease.age()).append(',').append(lease.quiet()).append(',').append(lease.terminalAge());
            for(Object flash:(List<?>)field(m,"flashes"))b.append("\nflash:").append(field(flash,"segment")).append(':').append(field(flash,"phase")).append(':').append(field(flash,"age"));
        }
        return b.toString();
    }
    private static void snap(String name,boolean expectEmpty)throws Exception {
        Trace.reset();String before=RenderSystem.STACK.value.toString();int depth=RenderSystem.STACK.frames.size();
        RailThunderArrayEffects.render(event);
        require(RenderSystem.STACK.frames.size()==depth&&RenderSystem.STACK.value.toString().equals(before),"MODEL_VIEW_NOT_RESTORED:"+name);
        require(Trace.vertices%4==0,"INCOMPLETE_QUAD:"+name);
        if(expectEmpty)require(Trace.vertices==0,"EXPECTED_EMPTY:"+name);
        String rendered=String.join("\n",Trace.rows)+"\nrestored:"+RenderSystem.STACK.value+":"+RenderSystem.STACK.frames.size();String state=state();
        cases.write(name+"\t"+Trace.vertices+"\t"+Trace.batches+"\t"+sha(rendered)+"\t"+sha(state)+"\n");
        capture.write("CASE "+name+"\n"+rendered+"\n"+state+"\nEND\n");
        count++;if(Trace.vertices>0)nonempty++;totalVertices+=Trace.vertices;totalBatches+=Trace.batches;
    }
    private static void matrix()throws Exception {
        int[][] kinds={{0,0},{1,0},{2,1},{2,2},{2,3},{2,4},{3,1},{3,2},{3,3},{3,4}};
        for(int scene=0;scene<6;scene++)for(int view=0;view<4;view++)for(int[] kind:kinds){
            if(lane.equals("refused")&&kind[1]!=RailThunderArrayState.REFUSED)continue;
            reset(scene,view);receive(kind[0],kind[1]);require(markers().size()==1,"VALID_PACKET_REJECTED");
            for(int age=0;age<=5;age++){snap("matrix/s"+scene+"/v"+view+"/p"+kind[0]+"/status"+kind[1]+"/age"+age,scene==4||age==5&&kind[0]>=2);tick();}
        }
    }
    private static void lifecycle()throws Exception {
        reset(0,0);receive(2,2);snap("life/initial",false);MC.paused=true;for(int i=0;i<16;i++)tick();snap("life/paused-retains",false);MC.player.alive=false;tick();snap("life/paused-invalid-owner",true);
        reset(0,0);receive(3,2);MC.level.chunks=false;snap("life/unloaded",true);MC.level.chunks=true;MC.level.blocked=true;snap("life/occluded",true);MC.level.blockFraction=1;snap("life/clipped-at-end",false);
        reset(0,0);receive(2,2);target.alive=false;snap("life/confirmed-hit-target-removed",false);
        reset(0,3);var noHit=new RailThunderArray.Segment(0,target.id,TARGET,origin,to,RailThunderArrayState.MISS);RailThunderArrayEffects.receive(new RailThunderArray.Visual(owner.id,owner.uuid,1,71,1,1,2,0,origin,AXES,List.of(noHit),0));target.alive=false;snap("life/unconfirmed-target-removed",true);
        for(String change:List.of("world","player","connection","clear","abort","screen","deactivate","owner-id-reuse")){
            reset(0,0);receive(2,2);
            switch(change){case "world"->MC.level=new ClientLevel();case "player"->MC.player=new Entity(19,LOCAL);case "connection"->MC.connection=new Object();case "clear"->RailThunderArrayEffects.clear();case "abort"->RailThunderArrayEffects.abortLocal(1,1);case "screen"->MC.screen=new Object();case "deactivate"->AcademyClient.state.activated=false;case "owner-id-reuse"->MC.level.entities.put(owner.id,new Entity(owner.id,owner.uuid));}
            tick();snap("life/"+change,true);
        }
        reset(0,0);receive(2,2);RailThunderArrayEffects.receive(packet(4,0,2,1,3));snap("life/cancelled-end",true);
        reset(0,0);receive(2,2);RailThunderArrayEffects.receive(packet(4,0,2,1,0));for(int i=0;i<6;i++){snap("life/normal-end/"+i,i==5);tick();}
        reset(0,0);receive(0,0);for(int i=0;i<13;i++){snap("life/quiet/"+i,i>=12);tick();}
        reset(0,0);receive(3,2);for(int i=0;i<3;i++)tick();RailThunderArrayEffects.receive(new RailThunderArray.Visual(owner.id,owner.uuid,1,71,1,2,3,3,origin,AXES,List.of(),0));snap("life/heartbeat-retains-age",false);for(int i=0;i<2;i++)tick();snap("life/heartbeat-no-new-flash",true);
        reset(0,0);receive(2,2);tick();RailThunderArrayEffects.receive(packet(2,2,1,0,0));snap("life/stale-sequence-rejected",false);
        reset(0,0);receive(0,0);event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_ENTITIES,event.getCamera(),event.getModelViewMatrix(),event.getPoseStack());snap("life/wrong-stage",true);
        reset(0,3);receive(2,2);event=new RenderLevelStageEvent(event.getStage(),new Camera(new Vec3(0,0,-160),new Vector3f(0,0,1),new Vector3f(0,1,0)),event.getModelViewMatrix(),event.getPoseStack());snap("life/remote-distance-cull",true);
        reset(3,0);origin=new Vec3(30_000_001,0,0);to=origin.add(0,0,1);receive(2,2);snap("life/world-border-invalid",true);
    }
    private static void bounds()throws Exception {
        reset(2,0);
        for(int i=0;i<66;i++) {
            Entity remote=new Entity(100+i,new UUID(17,i+1));MC.level.entities.put(remote.id,remote);
            var segment=new RailThunderArray.Segment(0,-1,RailThunderArrayState.NO_TARGET,origin,to,RailThunderArrayState.MISS);
            RailThunderArrayEffects.receive(new RailThunderArray.Visual(remote.id,remote.uuid,1,200+i,1,1,2,0,origin,AXES,List.of(segment),0));
        }
        require(markers().size()==64,"CONTEXT_BOUND");snap("bounds/64-contexts-8-visible",false);
        reset(2,0);
        for(int seq=1;seq<=24;seq++)RailThunderArrayEffects.receive(packet(2,1,seq,0,0));
        require(((List<?>)field(markers().values().iterator().next(),"flashes")).size()==16,"FLASH_BOUND");snap("bounds/16-flashes",false);
        reset(2,0);
        var segments=new ArrayList<RailThunderArray.Segment>();for(int i=0;i<16;i++)segments.add(new RailThunderArray.Segment(i%3,-1,RailThunderArrayState.NO_TARGET,origin,to.add(i*.001,0,0),RailThunderArrayState.MISS));
        RailThunderArrayEffects.receive(new RailThunderArray.Visual(owner.id,owner.uuid,1,71,1,1,2,0,origin,AXES,segments,0));snap("bounds/16-segments",false);
    }
    static void prepareMaterial(String lane,Path resourcesRoot)throws Exception {
        if(!lane.equals("frozen")) {
            Class<?> config=Class.forName("cn.academy.port.HighPowerVisualConfig");
            Object setting=config.getField("RAIL_ARRAY_GRAND").get(null);setting.getClass().getMethod("set",boolean.class).invoke(setting,!lane.equals("off"));
            var reload=new RegisterClientReloadListenersEvent();
            Class.forName("cn.academy.port.client.RailThunderArrayGrandMaterial$Reload").getMethod("register",RegisterClientReloadListenersEvent.class).invoke(null,reload);
            var resources=new net.minecraft.server.packs.resources.ResourceManager(resourcesRoot,lane.equals("missing")?"missing":lane.equals("corrupt")?"corrupt":"valid");
            reload.fire(resources);
            Class<?> material=Class.forName("cn.academy.port.client.RailThunderArrayGrandMaterial");
            boolean ready=(boolean)field(material,"ready");
            require(ready==!List.of("missing","corrupt").contains(lane),"MATERIAL_READINESS_WRONG:"+lane);
            require(resources.opened==((lane.equals("missing")||lane.equals("corrupt"))?1:4),"MATERIAL_RELOAD_NOT_EXECUTED");
            var shaderResources=new net.minecraft.server.packs.resources.ResourceManager(resourcesRoot,"valid");
            var shaders=new RegisterShadersEvent(shaderResources);
            shaders.defer=lane.equals("shader-pending")||lane.equals("shader-stale");
            net.minecraft.client.renderer.ShaderInstance.failCompile=lane.equals("shader-compile");
            org.lwjgl.opengl.GL20.linked=!lane.equals("shader-link");
            Class<?> soft=Class.forName("cn.academy.port.client.RailThunderArraySoftMaterial");
            var register=soft.getDeclaredMethod("register",RegisterShadersEvent.class);register.setAccessible(true);register.invoke(null,shaders);
            if(lane.equals("shader-stale")){
                net.minecraft.client.renderer.ShaderInstance.failCompile=true;
                register.invoke(null,new RegisterShadersEvent(shaderResources));
                net.minecraft.client.renderer.ShaderInstance.failCompile=false;shaders.finish();
            }
            var available=soft.getDeclaredMethod("available");available.setAccessible(true);
            boolean shaderExpected=!lane.startsWith("shader-")&&!lane.equals("forced");
            require((boolean)available.invoke(null)==shaderExpected,"SHADER_LIFECYCLE_NOT_EXECUTED:"+lane);
            require(lane.equals("forced")?net.minecraft.client.renderer.ShaderInstance.attempts==0:net.minecraft.client.renderer.ShaderInstance.attempts>0,"SHADER_CONSTRUCTOR_NOT_EXERCISED:"+lane);
            if(lane.equals("shader-link"))require(net.minecraft.client.renderer.ShaderInstance.closed==1,"UNLINKED_SHADER_NOT_CLOSED");
            var enabled=material.getDeclaredMethod("enabled");enabled.setAccessible(true);
            require((boolean)enabled.invoke(null)==(lane.equals("refused")||lane.equals("enabled")),"FALLBACK_LANE_MATERIAL_ENABLED:"+lane);
        }
    }
    public static void main(String[] args)throws Exception {
        lane=args[0];
        prepareMaterial(lane,Path.of(args[2]));
        Path out=Path.of(args[1]);Files.createDirectories(out);
        try(var c=Files.newBufferedWriter(out.resolve("cases.tsv"));var gz=new GZIPOutputStream(Files.newOutputStream(out.resolve("capture.txt.gz")));var full=new BufferedWriter(new OutputStreamWriter(gz,StandardCharsets.UTF_8))){cases=c;capture=full;matrix();if(!lane.equals("refused")){lifecycle();bounds();}}
        if(!Boolean.getBoolean("proof.mutant"))require(lane.equals("refused")?nonempty>50&&totalVertices>500:nonempty>700&&totalVertices>30_000,"VACUOUS_MATRIX:"+nonempty+":"+totalVertices);
        System.out.println("{\"lane\":\""+lane+"\",\"cases\":"+count+",\"nonempty_cases\":"+nonempty+",\"vertices\":"+totalVertices+",\"batches\":"+totalBatches+"}");
    }
}
