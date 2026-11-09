package cn.academy.port.client;

import cn.academy.port.highpower.ShearWindblade;
import cn.academy.port.highpower.core.ShearWindbladeGeometry;
import cn.academy.port.highpower.core.ShearWindbladeGeometry.Point;
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

/** Runs complete, independently compiled production renderer/state/geometry; hosts replace platform boundaries only. */
public final class ShearWindbladeFallbackHarness {
    static final Minecraft MC=Minecraft.getInstance();
    static final UUID OWNER=new UUID(421,1);
    static BufferedWriter cases,capture;
    static int count,nonempty,totalVertices,totalBatches,peakVertices;
    static String lane;
    static int resourceChecks,midframeFaults,exceptionCases,boundaryCases,boundaryUnavailableQueries;
    static Object setting;
    static RegisterClientReloadListenersEvent reload;
    static Path resourceRoot;
    static Point origin,look;
    static Vec3 camera;
    static RenderLevelStageEvent event;
    static Object field(Object subject,String name)throws Exception {
        Class<?> c=subject instanceof Class<?> t?t:subject.getClass();Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(subject instanceof Class<?>?null:subject);
    }
    static ShearWindbladeState state()throws Exception{return (ShearWindbladeState)field(ShearWindbladeEffects.class,"STATE");}
    static String sha(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static Vec3 vec(Point p){return new Vec3(p.x(),p.y(),p.z());}
    static void reset(int scene)throws Exception {
        ShearWindbladeEffects.clear();AcademyClient.reset();MC.level=new ClientLevel();MC.player=new Player(10,OWNER);MC.level.entities.put(10,MC.player);MC.connection=new Object();MC.screen=null;MC.camera=null;MC.paused=false;MC.active=true;MC.defer=false;MC.queued.clear();
        origin=scene==3?new Point(29_999_990,4093,29_999_970):new Point(0,64,0);
        look=scene==7?new Point(0,1,0):new Point(0,0,1);
        camera=switch(scene){case 1->vec(origin).add(0,0,.27);case 2->vec(origin).add(0,0,1.5);case 4->vec(origin).add(0,0,-70);case 6->vec(origin).add(7,.1,1);case 7->vec(origin).add(0,-5,0);default->vec(origin).add(0,.2,-5);};
        Vector3f forward=scene==6?new Vector3f(-1,0,0):scene==7?new Vector3f(0,1,0):new Vector3f(0,0,1);
        PoseStack poses=new PoseStack();if(scene==5)poses.matrix.translation(.125f,-.25f,.5f).scale(.875f,1.125f,1);
        event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_PARTICLES,new Camera(camera,forward,new Vector3f(1,0,0)),new Matrix4f().translation(.25f,-.5f,.75f),poses);
        RenderLevelStageEvent.partial=.375f;RenderLevelStageEvent.frustum=true;
        RenderSystem.STACK.value.identity().translation(-4,2,7);RenderSystem.STACK.frames.clear();RenderSystem.color=new float[]{.25f,.5f,.75f,.875f};
        ShearWindbladeEffects.localVisual();AcademyClient.ended=AcademyClient.cancelled=0;
        if(lane.startsWith("midframe-")) {
            setting.getClass().getMethod("set",boolean.class).invoke(setting,true);
            reload.fire(new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,"valid"));
            MC.level.onChunkQuery=()->{try {
                midframeFaults++;
                if(lane.equals("midframe-generation"))reload.fire(new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,"missing"));
                else setting.getClass().getMethod("set",boolean.class).invoke(setting,false);
            }catch(Exception failure){throw new AssertionError("MIDFRAME_FAULT_FAILED",failure);}};
        }
    }
    static ShearWindblade.Visual packet(Player owner,Point at,int mask,int status,double from,double to,long sequence,boolean crouch,List<ShearWindblade.Impact> impacts) {
        var basis=ShearWindbladeGeometry.frame(at,look,.375);var fronts=new ArrayList<ShearWindblade.FrontPose>();
        for(int blade=0;blade<2;blade++)if((mask&1<<blade)!=0)fronts.add(new ShearWindblade.FrontPose(blade,
                ShearWindbladeGeometry.front(basis,blade,from).stream().map(ShearWindbladeFallbackHarness::vec).toList(),
                ShearWindbladeGeometry.front(basis,blade,to).stream().map(ShearWindbladeFallbackHarness::vec).toList(),status));
        return new ShearWindblade.Visual(owner.id,owner.uuid,71,3,10,sequence,0,crouch,vec(at),fronts,impacts);
    }
    static void active(Player owner,Point at,int mask,int status,double from,double to,long sequence,boolean crouch)throws Exception {
        ShearWindbladeEffects.receive(packet(owner,at,mask,status,from,to,sequence,crouch,List.of()));
        require(state().active().stream().anyMatch(c->c.id().entity()==owner.id),"VALID_RECEIPT_NOT_ACCEPTED");
    }
    static void ended(Player owner,long sequence){ShearWindbladeEffects.receive(new ShearWindblade.Visual(owner.id,owner.uuid,71,3,10,sequence,1,false,Vec3.ZERO,List.of(),List.of()));}
    static void tick(){ShearWindbladeEffects.tick(new ClientTickEvent.Post());}
    static String stateDump()throws Exception {
        var s=state();StringBuilder b=new StringBuilder("local:").append(ShearWindbladeEffects.localVisual()).append("\ncounts:").append(s.active().size()).append(':').append(s.watermarkCount());
        for(var cast:s.active())b.append("\ncontroller:").append(cast);
        Map<?,?> fences=(Map<?,?>)field(s,"fences");var keys=new ArrayList<>(fences.keySet());keys.sort(Comparator.comparing(Object::toString));for(var key:keys)b.append("\nfence:").append(key).append(':').append(fences.get(key));
        b.append("\ncancelled:").append(field(s,"cancelledEpoch")).append(':').append(field(s,"cancelledThrough"));
        Map<?,?> owners=(Map<?,?>)field(ShearWindbladeEffects.class,"visualOwners");keys=new ArrayList<>(owners.keySet());keys.sort(Comparator.comparing(Object::toString));for(var key:keys){var owner=(net.minecraft.world.entity.Entity)owners.get(key);b.append("\nvisual-owner:").append(key).append(':').append(owner.id).append(':').append(owner.uuid);}
        return b.append("\ninput:").append(AcademyClient.ended).append(':').append(AcademyClient.cancelled).toString();
    }
    static void snap(String name,boolean empty)throws Exception {
        String stateBefore=stateDump();Trace.reset();String before=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();int depth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();float[] color=RenderSystem.color.clone();
        ShearWindbladeEffects.render(event);
        require(RenderSystem.STACK.frames.size()==depth&&RenderSystem.STACK.value.toString().equals(before),"MODEL_VIEW_NOT_RESTORED:"+name);
        require(event.getPoseStack().frames.size()==poseDepth&&event.getPoseStack().matrix.toString().equals(pose),"POSE_NOT_RESTORED:"+name);
        require(Arrays.equals(color,RenderSystem.color),"SHADER_COLOR_NOT_RESTORED:"+name);require(Trace.vertices%4==0,"INCOMPLETE_QUAD:"+name);
        if(empty)require(Trace.vertices==0,"EXPECTED_EMPTY:"+name);require(Trace.vertices<=2048,"SCENE_BUDGET_EXCEEDED:"+name);
        String stateAfter=stateDump();require(stateBefore.equals(stateAfter),"RENDER_MUTATED_RECEIPTS:"+name);
        String rendered=String.join("\n",Trace.rows)+"\nrestored:"+before+":"+depth+":"+pose+":"+poseDepth+":"+Arrays.toString(RenderSystem.color);
        cases.write(name+"\t"+Trace.vertices+"\t"+Trace.batches+"\t"+sha(rendered)+"\t"+sha(stateAfter)+"\n");capture.write("CASE "+name+"\n"+rendered+"\n"+stateAfter+"\nEND\n");
        count++;if(Trace.vertices>0)nonempty++;totalVertices+=Trace.vertices;totalBatches+=Trace.batches;peakVertices=Math.max(peakVertices,Trace.vertices);
    }
    static void matrix()throws Exception {
        double[][] distances={{0,2},{10,12},{22,23.5}};
        for(int scene=0;scene<8;scene++)for(int phase=0;phase<3;phase++)for(int status=0;status<3;status++)for(int mask=1;mask<=3;mask++){
            reset(scene);active(MC.player,origin,mask,status,distances[phase][0],distances[phase][1],1,mask==3);int now=0;
            for(int age:new int[]{0,1,2,3,5,15}){while(now<age){tick();now++;}RenderLevelStageEvent.partial=age%3==0?0:age%3==1?.375f:1;snap("matrix/scene"+scene+"/phase"+phase+"/status"+status+"/mask"+mask+"/age"+age,scene==4||status==0&&age>=5);}
        }
        for(float partial:new float[]{0,.125f,.5f,.875f,1}){reset(1);active(MC.player,origin,3,1,0,2,1,false);RenderLevelStageEvent.partial=partial;snap("interpolation/partial"+partial,false);}
    }
    static void lifecycle()throws Exception {
        reset(0);snap("empty/no-controller",true);active(MC.player,origin,0,0,0,2,1,false);snap("empty/no-front",true);
        reset(0);active(MC.player,origin,3,0,0,2,1,false);ended(MC.player,2);snap("empty/ended",true);ShearWindbladeEffects.receive(packet(MC.player,origin,3,0,2,4,3,false,List.of()));snap("life/ended-replay",true);
        reset(0);active(MC.player,origin,3,0,0,2,1,false);for(int i=0;i<18;i++){snap("life/silence"+i,i>=5);tick();}
        reset(0);var receipt=packet(MC.player,origin,3,0,0,2,1,false,List.of(new ShearWindblade.Impact(0,0,vec(origin).add(0,0,2),1,99)));ShearWindbladeEffects.receive(receipt);tick();ShearWindbladeEffects.receive(receipt);snap("life/duplicate",false);
        ShearWindbladeEffects.receive(packet(MC.player,origin,3,0,2,4,2,false,List.of()));snap("life/consecutive-receipt",false);
        ShearWindbladeEffects.receive(packet(MC.player,origin,3,1,4,5,3,false,List.of()));snap("life/blocked-receipt",false);
        ShearWindbladeEffects.receive(packet(MC.player,origin,3,0,5,7,4,false,List.of()));snap("life/stopped-revival-rejected",false);
        reset(0);active(MC.player,origin,3,0,0,2,1,false);MC.paused=true;for(int i=0;i<20;i++)tick();snap("life/pause",false);MC.player.alive=false;tick();snap("life/paused-death",true);
        for(String change:List.of("world","player","connection","screen","epoch","clear","abort","unloaded","stage","spectator","camera","frustum","removed","focus")){
            reset(0);active(MC.player,origin,3,0,0,2,1,false);
            switch(change){case "world"->MC.level=new ClientLevel();case "player"->MC.player=new Player(11,OWNER);case "connection"->MC.connection=new Object();case "screen"->MC.screen=new Object();case "epoch"->AcademyClient.epoch++;case "clear"->ShearWindbladeEffects.clear();case "abort"->ShearWindbladeEffects.abortLocal(3,71);case "unloaded"->MC.level.chunks=false;case "stage"->event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_ENTITIES,event.getCamera(),event.getModelViewMatrix(),event.getPoseStack());case "spectator"->MC.player.spectator=true;case "camera"->MC.camera=new Player(77,new UUID(0,77));case "frustum"->RenderLevelStageEvent.frustum=false;case "removed"->MC.player.removed=true;case "focus"->MC.active=false;}
            if(!change.equals("epoch"))tick();snap("life/"+change,true);
        }
        for(String change:List.of("clear","session","world","connection")){
            reset(0);MC.defer=true;ShearWindbladeEffects.receive(packet(MC.player,origin,3,0,0,2,1,false,List.of()));
            switch(change){case "clear"->ShearWindbladeEffects.clear();case "session"->AcademyClient.session++;case "world"->MC.level=new ClientLevel();case "connection"->MC.connection=new Object();}MC.drain();snap("queued/"+change,true);
        }
    }
    static void bounds()throws Exception {
        reset(0);for(int i=0;i<4;i++){Player p=i==0?MC.player:new Player(10+i,new UUID(421,i+1));MC.level.entities.put(p.id,p);active(p,new Point(origin.x()+i*.12,origin.y(),origin.z()),3,0,0,2,1,false);}
        require(state().active().size()==4,"FOUR_CONTROLLERS_NOT_ACCEPTED");snap("bounds/four-controllers",false);if(!Boolean.getBoolean("proof.mutant")&&!lane.equals("enabled"))require(Trace.vertices==512,"FOUR_CONTROLLER_SUBMISSION_NOT_FULL");
        Player fifth=new Player(14,new UUID(421,5));MC.level.entities.put(14,fifth);active(fifth,origin,3,0,0,2,1,false);require(state().active().size()==4&&state().watermarkCount()==5,"CONTROLLER_CAP_NOT_EXERCISED");snap("bounds/fifth-owner-retires-oldest-remote",false);
        reset(1);active(MC.player,origin,3,0,0,2,1,false);snap("near-plane/crossing-front",false);require(Trace.vertices>0,"NEAR_PLANE_CASE_VACUOUS");
        reset(0);active(MC.player,origin,3,0,0,2,1,false);camera=vec(origin).add(0,0,5);event=new RenderLevelStageEvent(event.getStage(),new Camera(camera,new Vector3f(0,0,1),new Vector3f(1,0,0)),event.getModelViewMatrix(),event.getPoseStack());snap("near-plane/whole-front-behind",true);
    }
    static void prepareMaterial()throws Exception {
        if(lane.equals("frozen"))return;
        Class<?> config=Class.forName("cn.academy.port.HighPowerVisualConfig"),material=Class.forName("cn.academy.port.client.ShearWindbladeVolumeMaterial");
        setting=config.getField("SHEAR_WINDBLADE_VOLUME").get(null);
        require(!(boolean)setting.getClass().getMethod("get").invoke(setting),"DEFAULT_MUST_BE_OFF");
        setting.getClass().getMethod("set",boolean.class).invoke(setting,!lane.equals("off"));
        net.neoforged.neoforge.common.ModConfigSpec.loaded=!lane.equals("config-unloaded");
        if(lane.equals("forced"))System.setProperty("academy.shearWindbladeVolume.forceFallback","true");
        reload=new RegisterClientReloadListenersEvent();
        Class.forName("cn.academy.port.client.ShearWindbladeVolumeMaterial$Reload").getMethod("register",RegisterClientReloadListenersEvent.class).invoke(null,reload);
        require(reload.listener!=null,"REAL_RELOAD_LISTENER_NOT_REGISTERED");
        String mode=List.of("missing","corrupt").contains(lane)?lane:"valid";
        var manager=new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,mode);
        MC.defer=lane.equals("reload-pending")||lane.equals("reload-stale");reload.fire(manager);
        require(manager.opened==1,"OPTIONAL_RESOURCE_GATE_NOT_EXERCISED:"+lane);resourceChecks++;
        if(lane.equals("reload-pending"))require(MC.queued.size()==1,"PENDING_CALLBACK_NOT_EXERCISED");
        if(lane.equals("reload-stale")) {
            // Finish the newer failed reload first, then run the old successful callback last.
            MC.defer=false;var missing=new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,"missing");reload.fire(missing);
            require(missing.opened==1&&MC.queued.size()==1,"STALE_CALLBACK_NOT_EXERCISED");resourceChecks++;MC.drain();
        }
        require((boolean)field(material,"ready")==!List.of("missing","corrupt","reload-pending","reload-stale").contains(lane),"RESOURCE_READY_WRONG:"+lane);
        require((boolean)material.getMethod("enabled").invoke(null)==(lane.equals("enabled")||lane.startsWith("midframe-")),"OPTIONAL_MATERIAL_GATE_WRONG:"+lane);
        MC.defer=false;
    }
    /** Supplemental receipts are separate from the immutable .61 success captures. */
    static void exceptionSafety(Path out)throws Exception {
        var result=new StringBuilder();
        for(String mode:List.of("vertex-first","vertex-partial","flush-all","flush-type","vertex-and-flush")) {
            reset(5);active(MC.player,origin,3,0,0,2,1,false);
            String stateBefore=stateDump(),model=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();
            int modelDepth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();float[] color=RenderSystem.color.clone();
            Trace.reset();ShearWindbladeEffects.render(event);String successful=String.join("\n",Trace.rows);
            require(Trace.vertices>0,"EXCEPTION_CASE_EMPTY:"+mode);
            boolean volume=Trace.rows.stream().anyMatch(row->row.startsWith("buffer:academy_shear_windblade_volume:"));
            require(volume==lane.equals("enabled"),"EXCEPTION_RENDER_PATH_WRONG:"+lane+":"+mode);
            assertExceptionRestored(mode+"-warmup",stateBefore,model,pose,modelDepth,poseDepth,color);
            Trace.reset();Trace.arm(mode.equals("vertex-first")?0:mode.startsWith("vertex-")?5:-1,
                    mode.equals("flush-all")||mode.equals("vertex-and-flush")?0:-1,mode.equals("flush-type")?0:-1);
            Trace.InjectedFailure thrown=null;
            try {ShearWindbladeEffects.render(event);}catch(Trace.InjectedFailure expected){thrown=expected;}finally{Trace.disarm();}
            require(thrown!=null,"EXCEPTION_NOT_PROPAGATED:"+mode);
            String expected=mode.equals("flush-type")?"flush-type":mode.equals("flush-all")||mode.equals("vertex-and-flush")?"flush-all":"vertex";
            require(thrown.getMessage().equals(expected),"WRONG_EXCEPTION:"+mode);
            require(Trace.injectedFaults==(mode.equals("vertex-and-flush")?2:1),"EXCEPTION_INJECTION_NOT_EXERCISED:"+mode);
            assertExceptionRestored(mode,stateBefore,model,pose,modelDepth,poseDepth,color);
            result.append("CASE ").append(mode).append("\npropagated:").append(thrown.getMessage()).append("\nfaults:").append(Trace.injectedFaults)
                    .append("\n").append(String.join("\n",Trace.rows)).append("\nstate:").append(sha(stateBefore))
                    .append("\nrestored:").append(model).append(':').append(modelDepth).append(':').append(pose).append(':').append(poseDepth).append(':').append(Arrays.toString(color));
            Trace.reset();ShearWindbladeEffects.render(event);require(String.join("\n",Trace.rows).equals(successful),"EXCEPTION_RECOVERY_DRAW_CHANGED:"+mode);
            assertExceptionRestored(mode+"-recovery",stateBefore,model,pose,modelDepth,poseDepth,color);
            result.append("\nrecovery:").append(sha(successful)).append("\nEND\n");exceptionCases++;
        }
        Files.writeString(out.resolve("exceptions.txt"),result.toString(),StandardCharsets.UTF_8);
    }
    static void assertExceptionRestored(String mode,String stateBefore,String model,String pose,int modelDepth,int poseDepth,float[] color)throws Exception {
        require(RenderSystem.STACK.frames.size()==modelDepth&&RenderSystem.STACK.value.toString().equals(model),"EXCEPTION_MODEL_VIEW_NOT_RESTORED:"+mode);
        require(event.getPoseStack().frames.size()==poseDepth&&event.getPoseStack().matrix.toString().equals(pose),"EXCEPTION_POSE_NOT_RESTORED:"+mode);
        require(Arrays.equals(color,RenderSystem.color),"EXCEPTION_SHADER_COLOR_NOT_RESTORED:"+mode);
        require(stateDump().equals(stateBefore),"EXCEPTION_MUTATED_RECEIPTS:"+mode);
    }
    /** Both original contours are loaded; only the optional shoulder reaches unavailable chunk (1,0). */
    static void mixedChunkBoundary(Path out)throws Exception {
        reset(5);origin=new Point(15.83,64,4);camera=new Vec3(15.83,64,-5);
        event=new RenderLevelStageEvent(event.getStage(),new Camera(camera,new Vector3f(0,0,1),new Vector3f(1,0,0)),event.getModelViewMatrix(),event.getPoseStack());
        MC.level.unavailableChunks.add(1L<<32);
        active(MC.player,origin,2,0,0,2,1,false);
        Player second=new Player(11,new UUID(421,2));MC.level.entities.put(second.id,second);active(second,new Point(8,64,4),2,0,0,2,1,false);
        String stateBefore=stateDump(),model=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();
        int modelDepth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();float[] color=RenderSystem.color.clone();
        Trace.reset();ShearWindbladeEffects.render(event);
        require(Trace.vertices==128,"MIXED_CHUNK_CLASSIC_CONTROLLER_LOST:"+Trace.vertices);
        require(Trace.rows.stream().noneMatch(row->row.startsWith("buffer:academy_shear_windblade_volume:")),"MIXED_CHUNK_PARTIAL_ENHANCED_SUBMISSION");
        if(lane.equals("enabled"))require(MC.level.unavailableQueries>0,"ENHANCED_SHOULDER_DID_NOT_REACH_MISSING_CHUNK");
        assertExceptionRestored("mixed-chunk-boundary",stateBefore,model,pose,modelDepth,poseDepth,color);
        Files.writeString(out.resolve("mixed-chunk-boundary.txt"),"CASE mixed-chunk-boundary\n"+String.join("\n",Trace.rows)+"\nstate:"+stateBefore
                +"\nrestored:"+model+":"+modelDepth+":"+pose+":"+poseDepth+":"+Arrays.toString(color)+"\nEND\n",StandardCharsets.UTF_8);
        boundaryCases++;boundaryUnavailableQueries+=MC.level.unavailableQueries;
    }
    public static void main(String[] args)throws Exception {
        lane=args[0];resourceRoot=Path.of(args[2]);prepareMaterial();Path out=Path.of(args[1]);Files.createDirectories(out);
        if(args.length==4&&args[3].equals("boundary-only")){mixedChunkBoundary(out);System.out.println("{\"boundary_cases\":"+boundaryCases+"}");return;}
        if(args.length==4&&args[3].equals("exceptions-only")){exceptionSafety(out);System.out.println("{\"exception_cases\":"+exceptionCases+",\"boundary_cases\":"+boundaryCases+",\"boundary_unavailable_queries\":"+boundaryUnavailableQueries+"}");return;}
        try(var c=Files.newBufferedWriter(out.resolve("cases.tsv"));var gz=new GZIPOutputStream(Files.newOutputStream(out.resolve("capture.txt.gz")));var full=new BufferedWriter(new OutputStreamWriter(gz,StandardCharsets.UTF_8))){cases=c;capture=full;matrix();lifecycle();bounds();}
        if(lane.startsWith("midframe-"))require(midframeFaults>200,"MIDFRAME_GUARD_NOT_EXERCISED:"+midframeFaults);
        require(count>1300&&nonempty>700&&totalVertices>50000&&peakVertices>=512,"VACUOUS_RENDER_CAPTURE");
        exceptionSafety(out);mixedChunkBoundary(out);
        System.out.println("{\"lane\":\""+lane+"\",\"cases\":"+count+",\"nonempty_cases\":"+nonempty+",\"vertices\":"+totalVertices+",\"batches\":"+totalBatches+",\"peak_vertices\":"+peakVertices+",\"resource_checks\":"+resourceChecks+",\"midframe_faults\":"+midframeFaults+",\"exception_cases\":"+exceptionCases+",\"boundary_cases\":"+boundaryCases+",\"boundary_unavailable_queries\":"+boundaryUnavailableQueries+"}");
    }
}
