package cn.academy.port.client;

import cn.academy.port.highpower.MulticoreCanopy;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import org.joml.*;
import proof.Trace;
import java.io.*;
import java.lang.reflect.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.GZIPOutputStream;

/** Complete independently compiled Effects/receipt/mesh/material closure; hosts replace platform boundaries. */
public final class MulticoreCanopyFallbackHarness {
    static final Minecraft MC=Minecraft.getInstance();
    static final UUID OWNER=new UUID(251,1);
    static final double[][] OFFSETS={{-.9,0},{-.45,.65},{.45,.65},{.9,0},{.45,-.65},{-.45,-.65}};
    static BufferedWriter cases,capture;
    static int count,nonempty,totalVertices,totalLabels,peakVertices,exceptionCases,midframeFaults,resourceChecks;
    static String lane;
    static Object setting;
    static RegisterClientReloadListenersEvent reload;
    static Path resourceRoot;
    static Vec3 origin,camera;
    static RenderLevelStageEvent event;
    static Object field(Object subject,String name)throws Exception {
        Class<?> c=subject instanceof Class<?> t?t:subject.getClass();Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(subject instanceof Class<?>?null:subject);
    }
    static MulticoreCanopyState state()throws Exception{return (MulticoreCanopyState)field(MulticoreCanopyEffects.class,"STATE");}
    static String sha(String v)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(v.getBytes(StandardCharsets.UTF_8)));}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static String stable(Object value)throws Exception {
        if(value==null)return "null";
        if(value instanceof Map<?,?> m){var sorted=new TreeMap<String,String>();for(var e:m.entrySet())sorted.put(stable(e.getKey()),stable(e.getValue()));return sorted.toString();}
        if(value instanceof Set<?> s){var sorted=new TreeSet<String>();for(var v:s)sorted.add(stable(v));return sorted.toString();}
        if(value instanceof Collection<?> c){var list=new ArrayList<String>();for(var v:c)list.add(stable(v));return list.toString();}
        if(value.getClass().isRecord()){StringBuilder b=new StringBuilder(value.getClass().getSimpleName()+"[");for(var part:value.getClass().getRecordComponents()){var method=part.getAccessor();method.setAccessible(true);b.append(part.getName()).append('=').append(stable(method.invoke(value))).append(',');}return b.append(']').toString();}
        return value.toString();
    }
    static void reset(int scene)throws Exception {
        MulticoreCanopyEffects.clear();AcademyClient.reset();MC.level=new ClientLevel();MC.player=new Player(10,OWNER);MC.level.entities.put(10,MC.player);MC.connection=new Object();MC.screen=null;MC.camera=null;MC.paused=false;MC.active=true;MC.defer=false;MC.queued.clear();
        origin=scene==3?new Vec3(29_999_990,4090,29_999_970):new Vec3(0,64,0);
        camera=scene==1?origin.add(0,0,-.3):scene==4?origin.add(0,0,25):origin.add(0,.2,-5);
        MC.player.position=origin;
        PoseStack poses=new PoseStack();if(scene==2)poses.matrix.translation(.125f,-.25f,.5f).scale(.875f,1.125f,1);
        event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_PARTICLES,new Camera(camera,new Vector3f(0,0,1),new Vector3f(1,0,0)),new Matrix4f().translation(.25f,-.5f,.75f),poses);
        RenderLevelStageEvent.partial=.375f;RenderSystem.STACK.value.identity().translation(-4,2,7);RenderSystem.STACK.frames.clear();RenderSystem.color=new float[]{.25f,.5f,.75f,.875f};
        MulticoreCanopyEffects.localVisual();AcademyClient.ended=AcademyClient.accepted=0;
    }
    static void armMidframe()throws Exception {
        if(lane.startsWith("midframe-")) {
            setting.getClass().getMethod("set",boolean.class).invoke(setting,true);
            reload.fire(new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,"valid"));
            MC.level.onChunkQuery=()->{try {
                midframeFaults++;
                if(lane.equals("midframe-generation"))reload.fire(new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,"valid"));
                else setting.getClass().getMethod("set",boolean.class).invoke(setting,false);
            }catch(Exception failure){throw new AssertionError("MIDFRAME_FAULT_FAILED",failure);}};
        }
    }
    static MulticoreCanopy.Visual packet(Player owner,Vec3 at,int phase,int n,int outcome,long sequence,boolean spent,boolean targets) {
        var cores=new ArrayList<MulticoreCanopy.CoreVisual>();var rays=new ArrayList<MulticoreCanopy.SegmentVisual>();var marks=new ArrayList<MulticoreCanopy.TargetVisual>();
        for(int i=0;i<n;i++){Vec3 p=at.add(OFFSETS[i][0],OFFSETS[i][1],0);cores.add(new MulticoreCanopy.CoreVisual(i,p,!spent));if(phase==4)rays.add(new MulticoreCanopy.SegmentVisual(i,i,p,at.add((i-2)*.7,.3,16+i),outcome));}
        if(targets)for(int i=0;i<7;i++)marks.add(new MulticoreCanopy.TargetVisual(100+i,at.add((i-3)*.8,1,18+i),i));
        return new MulticoreCanopy.Visual(owner.id,owner.uuid,71,3,10,sequence,phase,phase==4?outcome:0,phase==5?0:240,at,cores,marks,rays,phase==3||phase==4,n,phase==2?6:0);
    }
    static void active(int phase,int n,int outcome,boolean spent,boolean targets)throws Exception {
        MulticoreCanopyEffects.receive(packet(MC.player,origin,phase,n,outcome,1,spent,targets));
        require(state().current(OWNER)!=null,"VALID_RECEIPT_NOT_ACCEPTED");
    }
    static void tick(){MulticoreCanopyEffects.tick(new ClientTickEvent.Post());}
    static void ended(int n){MulticoreCanopyEffects.receive(packet(MC.player,origin,5,n,1,2,true,false));}
    static String stateDump()throws Exception {
        var s=state();StringBuilder b=new StringBuilder("local:").append(MulticoreCanopyEffects.localVisual()).append(':').append(MulticoreCanopyEffects.canControlLocal());
        for(String key:List.of("active","fences","cancelledEpoch","cancelledThrough"))b.append('\n').append(key).append(':').append(stable(field(s,key)));
        Map<?,?> markers=(Map<?,?>)field(MulticoreCanopyEffects.class,"MARKERS");var keys=new ArrayList<>(markers.keySet());keys.sort(Comparator.comparing(Object::toString));
        for(Object key:keys){Object m=markers.get(key);var owner=(net.minecraft.world.entity.Entity)field(m,"owner");b.append("\nmarker:").append(key).append(':').append(owner.id).append(':').append(stable(field(m,"frame"))).append(':').append(field(m,"terminal")).append(':').append(field(m,"age"));
            Object lease=field(m,"lease");for(String v:List.of("remaining","quiet","received","ended"))b.append(':').append(field(lease,v));b.append(":lost=").append(stable(field(field(m,"visibility"),"lost")));
            for(Object flash:(List<?>)field(m,"flashes"))b.append("\nflash:").append(field(flash,"age")).append(':').append(stable(field(flash,"frame")));
        }
        return b.append("\ninput:").append(AcademyClient.accepted).append(':').append(AcademyClient.ended).append(':').append(AcademyClient.notifications).toString();
    }
    static void restored(String name,String stateBefore,String model,String pose,int depth,int poseDepth,float[] color)throws Exception {
        require(RenderSystem.STACK.frames.size()==depth&&RenderSystem.STACK.value.toString().equals(model),"MODEL_VIEW_NOT_RESTORED:"+name);
        require(event.getPoseStack().frames.size()==poseDepth&&event.getPoseStack().matrix.toString().equals(pose),"POSE_NOT_RESTORED:"+name);
        require(Arrays.equals(color,RenderSystem.color),"SHADER_COLOR_NOT_RESTORED:"+name);
        require(stateDump().equals(stateBefore),"RENDER_MUTATED_RECEIPTS:"+name);
    }
    static void snap(String name,boolean empty)throws Exception {
        String before=stateDump(),model=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();int depth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();float[] color=RenderSystem.color.clone();armMidframe();Trace.reset();
        MulticoreCanopyEffects.render(event);restored(name,before,model,pose,depth,poseDepth,color);
        require(Trace.vertices%4==0,"INCOMPLETE_QUAD:"+name);if(empty)require(Trace.vertices==0&&Trace.labels==0,"EXPECTED_EMPTY:"+name);
        require(Trace.vertices/4+Trace.labels*4<=1536,"SCENE_BUDGET_EXCEEDED:"+name);
        require(Trace.rows.stream().filter(r->r.startsWith("font:")).allMatch(r->r.contains(":NORMAL:")),"SEE_THROUGH_LABEL:"+name);
        String rendered=String.join("\n",Trace.rows)+"\nrestored:"+model+":"+depth+":"+pose+":"+poseDepth+":"+Arrays.toString(RenderSystem.color);
        cases.write(name+"\t"+Trace.vertices+"\t"+Trace.labels+"\t"+sha(rendered)+"\t"+sha(before)+"\n");capture.write("CASE "+name+"\n"+rendered+"\n"+before+"\nEND\n");
        count++;if(Trace.vertices>0||Trace.labels>0)nonempty++;totalVertices+=Trace.vertices;totalLabels+=Trace.labels;peakVertices=Math.max(peakVertices,Trace.vertices);
    }
    static void matrix()throws Exception {
        for(int scene=0;scene<5;scene++)for(int phase=0;phase<5;phase++)for(int n:new int[]{1,3,6}) {
            reset(scene);active(phase,n,1+n%5,false,true);int now=0;
            for(int age:new int[]{0,3,4}){while(now<age){tick();now++;}RenderLevelStageEvent.partial=age==0?0:age==3?.375f:1;snap("matrix/scene"+scene+"/phase"+phase+"/cores"+n+"/age"+age,scene==4);}
        }
        for(int status=1;status<=5;status++)for(float partial:new float[]{0,.125f,.5f,.875f,1}){
            reset(2);active(4,6,status,true,true);RenderLevelStageEvent.partial=partial;snap("rays/status"+status+"/partial"+partial,false);
        }
    }
    static void lifecycle()throws Exception {
        reset(0);snap("empty/no-controller",true);active(0,0,1,false,false);snap("empty/no-cores",true);
        reset(0);active(1,6,1,true,false);snap("empty/unavailable-cores",true);
        reset(0);active(1,6,1,false,false);ended(6);snap("empty/ended-without-fire",true);
        for(int n:new int[]{1,3,6}){reset(0);active(4,n,4,true,true);ended(n);for(int age=0;age<=5;age++){RenderLevelStageEvent.partial=age==3?.99f:.375f;snap("ended/cores"+n+"/age"+age,age>=4);tick();}}
        reset(0);active(1,6,1,false,true);for(int age=0;age<=13;age++){snap("life/silence"+age,age>=12);tick();}
        reset(0);active(4,6,4,false,true);tick();MulticoreCanopyEffects.receive(packet(MC.player,origin,4,6,4,1,false,true));snap("life/duplicate",false);
        MulticoreCanopyEffects.receive(packet(MC.player,origin,4,6,5,2,true,true));snap("life/second-flash",false);ended(6);snap("life/stale-ended-rejected",false);
        MulticoreCanopyEffects.receive(packet(MC.player,origin,5,6,1,3,true,false));snap("life/two-flash-ended",false);
        reset(0);active(4,6,4,false,true);MC.paused=true;for(int i=0;i<20;i++)tick();snap("life/pause",false);MC.player.alive=false;tick();snap("life/paused-death",true);
        for(String change:List.of("world","player","connection","screen","epoch","clear","abort","unloaded","stage","spectator","camera","removed","focus","distance")){
            reset(0);active(1,6,1,false,true);
            switch(change){case "world"->MC.level=new ClientLevel();case "player"->MC.player=new Player(11,OWNER);case "connection"->MC.connection=new Object();case "screen"->MC.screen=new Object();case "epoch"->AcademyClient.epoch++;case "clear"->MulticoreCanopyEffects.clear();case "abort"->MulticoreCanopyEffects.abortLocal(3,71);case "unloaded"->MC.level.chunks=false;case "stage"->event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_ENTITIES,event.getCamera(),event.getModelViewMatrix(),event.getPoseStack());case "spectator"->MC.player.spectator=true;case "camera"->MC.camera=new Player(77,new UUID(0,77));case "removed"->MC.player.removed=true;case "focus"->MC.active=false;case "distance"->MC.player.position=origin.add(100,0,0);}tick();snap("empty/"+change,true);
        }
        for(String change:List.of("clear","session","world","connection")){
            reset(0);MC.defer=true;MulticoreCanopyEffects.receive(packet(MC.player,origin,1,6,1,1,false,true));
            switch(change){case "clear"->MulticoreCanopyEffects.clear();case "session"->AcademyClient.session++;case "world"->MC.level=new ClientLevel();case "connection"->MC.connection=new Object();}MC.drain();snap("queued/"+change,true);
        }
        reset(0);active(4,6,4,false,true);for(int i=1;i<5;i++){Player p=new Player(10+i,new UUID(251,i+1));p.position=origin;MC.level.entities.put(p.id,p);MulticoreCanopyEffects.receive(packet(p,origin.add(i*.1,0,0),4,6,4,1,false,true));}snap("bounds/five-owners-four-visible",false);
        reset(0);active(1,6,1,false,true);MC.level.unavailableChunks.add(0L);snap("chunks/partially-unloaded",false);
    }
    static void prepareMaterial()throws Exception {
        FoldedMirrorMaterial.register(new RegisterShadersEvent());require(FoldedMirrorMaterial.available(),"BASE_MATERIAL_NOT_INITIALIZED");
        if(lane.equals("frozen"))return;
        Class<?> config=Class.forName("cn.academy.port.HighPowerVisualConfig"),material=Class.forName("cn.academy.port.client.MulticoreCanopyCrownMaterial");
        setting=config.getField("MULTICORE_CANOPY_CROWN").get(null);require(!(boolean)setting.getClass().getMethod("get").invoke(setting),"DEFAULT_MUST_BE_OFF");
        setting.getClass().getMethod("set",boolean.class).invoke(setting,!lane.equals("off"));net.neoforged.neoforge.common.ModConfigSpec.loaded=!lane.equals("config-unloaded");
        if(lane.equals("forced"))System.setProperty("academy.multicoreCanopyCrown.forceFallback","true");
        reload=new RegisterClientReloadListenersEvent();Class.forName("cn.academy.port.client.MulticoreCanopyCrownMaterial$Reload").getMethod("register",RegisterClientReloadListenersEvent.class).invoke(null,reload);require(reload.listener!=null,"REAL_RELOAD_LISTENER_NOT_REGISTERED");
        String mode=lane.startsWith("missing-")||lane.startsWith("corrupt-")?lane:"valid";
        var manager=new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,mode);MC.defer=lane.equals("reload-pending")||lane.equals("reload-stale");reload.fire(manager);
        require(manager.opened==(mode.equals("missing-glow")?1:2),"OPTIONAL_RESOURCE_GATE_NOT_EXERCISED:"+lane);resourceChecks+=manager.opened;
        if(lane.equals("reload-pending"))require(MC.queued.size()==1,"PENDING_CALLBACK_NOT_EXERCISED");
        if(lane.equals("reload-stale")){MC.defer=false;var missing=new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,"missing-white");reload.fire(missing);require(missing.opened==2&&MC.queued.size()==1,"STALE_CALLBACK_NOT_EXERCISED");resourceChecks+=missing.opened;MC.drain();}
        boolean unavailable=lane.startsWith("missing-")||lane.startsWith("corrupt-")||lane.equals("reload-pending")||lane.equals("reload-stale");
        require((boolean)field(material,"ready")==!unavailable,"RESOURCE_READY_WRONG:"+lane);
        require((boolean)material.getMethod("enabled").invoke(null)==(lane.equals("enabled")||lane.startsWith("midframe-")),"OPTIONAL_MATERIAL_GATE_WRONG:"+lane);MC.defer=false;
    }
    static void exceptionSafety(Path out)throws Exception {
        StringBuilder result=new StringBuilder();
        for(String mode:List.of("vertex-first","vertex-partial","flush-finally","flush-type","vertex-and-flush")) {
            reset(2);active(4,6,4,false,true);String before=stateDump(),model=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();int depth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();float[] color=RenderSystem.color.clone();
            armMidframe();Trace.reset();MulticoreCanopyEffects.render(event);String success=String.join("\n",Trace.rows);require(Trace.vertices>0,"EXCEPTION_CASE_EMPTY:"+mode);restored(mode+"-warmup",before,model,pose,depth,poseDepth,color);
            // Re-arm per-frame changes as reset() does; this does not alter accepted receipts.
            if(lane.startsWith("midframe-")){setting.getClass().getMethod("set",boolean.class).invoke(setting,false);}
            Trace.reset();Trace.arm(mode.equals("vertex-first")?0:mode.startsWith("vertex-")?5:-1,mode.equals("flush-finally")?1:mode.equals("vertex-and-flush")?0:-1,mode.equals("flush-type")?0:-1);
            Trace.InjectedFailure thrown=null;try{MulticoreCanopyEffects.render(event);}catch(Trace.InjectedFailure e){thrown=e;}finally{Trace.disarm();}
            require(thrown!=null,"EXCEPTION_NOT_PROPAGATED:"+mode);String expected=mode.equals("flush-type")?"flush-type":mode.equals("flush-finally")||mode.equals("vertex-and-flush")?"flush-all":"vertex";
            require(thrown.getMessage().equals(expected),"WRONG_EXCEPTION:"+mode);require(Trace.injectedFaults==(mode.equals("vertex-and-flush")?2:1),"EXCEPTION_NOT_EXERCISED:"+mode);restored(mode,before,model,pose,depth,poseDepth,color);
            result.append("CASE ").append(mode).append("\npropagated:").append(thrown.getMessage()).append("\nfaults:").append(Trace.injectedFaults).append('\n').append(String.join("\n",Trace.rows)).append("\nstate:").append(sha(before)).append("\nrestored:").append(model).append(':').append(depth).append(':').append(pose).append(':').append(poseDepth).append(':').append(Arrays.toString(color));
            Trace.reset();MulticoreCanopyEffects.render(event);require(String.join("\n",Trace.rows).equals(success),"EXCEPTION_RECOVERY_DRAW_CHANGED:"+mode);restored(mode+"-recovery",before,model,pose,depth,poseDepth,color);result.append("\nrecovery:").append(sha(success)).append("\nEND\n");exceptionCases++;
        }
        Files.writeString(out.resolve("exceptions.txt"),result.toString(),StandardCharsets.UTF_8);
    }
    public static void main(String[] args)throws Exception {
        lane=args[0];resourceRoot=Path.of(args[2]);prepareMaterial();Path out=Path.of(args[1]);Files.createDirectories(out);
        if(args.length==4&&args[3].equals("exceptions-only")){exceptionSafety(out);System.out.println("{\"exception_cases\":"+exceptionCases+"}");return;}
        try(var c=Files.newBufferedWriter(out.resolve("cases.tsv"));var gz=new GZIPOutputStream(Files.newOutputStream(out.resolve("capture.txt.gz")));var full=new BufferedWriter(new OutputStreamWriter(gz,StandardCharsets.UTF_8))){cases=c;capture=full;matrix();lifecycle();}
        if(lane.startsWith("midframe-"))require(midframeFaults>75,"MIDFRAME_GUARD_NOT_EXERCISED:"+midframeFaults);
        require(count>300&&nonempty>200&&totalVertices>100000&&totalLabels>500&&peakVertices>3000,"VACUOUS_RENDER_CAPTURE");exceptionSafety(out);
        System.out.println("{\"lane\":\""+lane+"\",\"cases\":"+count+",\"nonempty_cases\":"+nonempty+",\"vertices\":"+totalVertices+",\"labels\":"+totalLabels+",\"peak_vertices\":"+peakVertices+",\"resource_checks\":"+resourceChecks+",\"midframe_faults\":"+midframeFaults+",\"exception_cases\":"+exceptionCases+"}");
    }
}
