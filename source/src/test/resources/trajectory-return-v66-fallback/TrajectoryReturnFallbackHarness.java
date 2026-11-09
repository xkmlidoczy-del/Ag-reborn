package cn.academy.port.client;

import cn.academy.port.highpower.TrajectoryReturn;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.*;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.packs.resources.ResourceManager;
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
import static cn.academy.port.client.TrajectoryReturnState.*;

/** Executes the entire compiled production receive/tick/render path in isolated .66/current JVMs. */
public final class TrajectoryReturnFallbackHarness {
    static final Minecraft MC=Minecraft.getInstance();
    static final UUID OWNER=new UUID(431,1);
    static final int[] AGES={0,1,2,3,4,5,6,7,8,12,16,31,32};
    static BufferedWriter cases,capture;
    static int count,nonempty,totalVertices,peakVertices,resourceChecks,exceptionCases;
    static String lane;static Path resources;static Object setting;
    static Vec3 origin;static RenderLevelStageEvent event;static long sequence;
    static Object field(Object subject,String name)throws Exception {
        Class<?> type=subject instanceof Class<?> t?t:subject.getClass();
        Field f=type.getDeclaredField(name);f.setAccessible(true);return f.get(subject instanceof Class<?>?null:subject);
    }
    static TrajectoryReturnState state()throws Exception{return (TrajectoryReturnState)field(TrajectoryReturnEffects.class,"STATE");}
    static String sha(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static String stable(Object value)throws Exception {
        if(value==null)return "null";
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,String>();for(var e:map.entrySet())sorted.put(stable(e.getKey()),stable(e.getValue()));return sorted.toString();}
        if(value instanceof Collection<?> list){var output=new ArrayList<String>();for(var v:list)output.add(stable(v));return output.toString();}
        if(value.getClass().isRecord()){var out=new StringBuilder(value.getClass().getSimpleName()+"[");for(var part:value.getClass().getRecordComponents()){var method=part.getAccessor();method.setAccessible(true);out.append(part.getName()).append('=').append(stable(method.invoke(value))).append(',');}return out.append(']').toString();}
        return value.toString();
    }
    static String stateDump()throws Exception {
        var s=state();var out=new StringBuilder();
        for(String f:List.of("active","fences","highwater","cancelledEpoch","cancelledThrough"))out.append(f).append(':').append(stable(field(s,f))).append('\n');
        return out.append("notifications:").append(AcademyClient.notifications).toString();
    }
    static void setEnabled(boolean enabled)throws Exception{if(setting!=null)setting.getClass().getMethod("set",boolean.class).invoke(setting,enabled);}
    static void prepare()throws Exception {
        if(!lane.equals("frozen")){
            var config=Class.forName("cn.academy.port.HighPowerVisualConfig");setting=config.getField("TRAJECTORY_RETURN_PRESSURE").get(null);
            require(!(boolean)setting.getClass().getMethod("get").invoke(setting),"DEFAULT_MUST_BE_OFF");
            setEnabled(!lane.equals("off"));
            net.neoforged.neoforge.common.ModConfigSpec.loaded=!lane.equals("config-unloaded");
        }
        if(lane.equals("forced"))System.setProperty("academy.trajectoryReturnPressure.forceFallback","true");
        var reload=new RegisterClientReloadListenersEvent();TrajectoryReturnEffects.Setup.reload(reload);
        require(reload.listener!=null,"PRODUCTION_RELOAD_LISTENER_MISSING");
        var manager=new ResourceManager(resources,List.of("missing","corrupt","oversized").contains(lane)?lane:"valid");
        MC.defer=lane.equals("reload-pending")||lane.equals("reload-stale");reload.fire(manager);resourceChecks+=manager.opened;
        if(!lane.equals("frozen")) {
            require(manager.opened==1,"PRODUCTION_RESOURCE_GATE_NOT_EXERCISED");
            if(lane.equals("reload-pending"))require(MC.queued.size()==1,"PENDING_RELOAD_NOT_EXERCISED");
            if(lane.equals("reload-stale")){
                MC.defer=false;var missing=new ResourceManager(resources,"missing");reload.fire(missing);resourceChecks+=missing.opened;MC.drain();
            }
            boolean expected=!(List.of("missing","corrupt","oversized","reload-pending","reload-stale").contains(lane));
            require((boolean)field(TrajectoryReturnEffects.class,"pressureReady")==expected,"RESOURCE_READY_WRONG:"+lane);
        }
        MC.defer=false;
    }
    static void reset(int scene)throws Exception {
        TrajectoryReturnEffects.clear();AcademyClient.reset();MC.level=new ClientLevel();MC.player=new Player(10,OWNER);
        MC.level.entities.put(10,MC.player);MC.connection=new Object();MC.screen=null;MC.camera=null;MC.paused=false;MC.active=true;MC.defer=false;MC.queued.clear();
        origin=scene==3?new Vec3(29_999_985,4080,29_999_970):new Vec3(0,64,0);MC.player.position=origin;
        Vec3 camera=switch(scene){case 1->origin.add(0,0,-.03);case 2->origin.add(-3,.2,-2);case 4->origin.add(0,0,8);default->origin.add(0,.2,-4);};
        Vector3f look=scene==2?new Vector3f(.70710677f,0,.70710677f):new Vector3f(0,0,1);
        Vector3f left=scene==2?new Vector3f(.70710677f,0,-.70710677f):new Vector3f(1,0,0);
        if(scene==5)MC.level.wallZ=origin.z+.18;
        PoseStack poses=new PoseStack();if(scene==2)poses.matrix.translation(.125f,-.25f,.5f).scale(.875f,1.125f,1);
        event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_PARTICLES,new Camera(camera,look,left),new Matrix4f().translation(.25f,-.5f,.75f),poses);
        RenderLevelStageEvent.partial=0;RenderLevelStageEvent.frustum=true;
        RenderSystem.STACK.value.identity().translation(-4,2,7);RenderSystem.STACK.frames.clear();RenderSystem.color=new float[]{.25f,.5f,.75f,.875f};
        TrajectoryReturnEffects.localVisual();AcademyClient.notifications.clear();sequence=0;
    }
    static TrajectoryReturn.Visual packet(Player owner,long seq,int kind,int slot,Vec3 from,Vec3 to,Vec3 velocity,int status){
        return new TrajectoryReturn.Visual(owner.id,owner.uuid,71,3,10,seq,kind,slot,from,to,velocity,status);
    }
    static void receipt(int kind,int slot,Vec3 from,Vec3 to,Vec3 velocity,int status){TrajectoryReturnEffects.receive(packet(MC.player,++sequence,kind,slot,from,to,velocity,status));}
    static void reverse(int slots)throws Exception {
        for(int slot=0;slot<slots;slot++){Vec3 p=origin.add(slot*.45,slot*.1,0);receipt(REVERSE,slot,p,p,new Vec3(.12*slot,0,1),COMMITTED);}
        require(state().controllers().size()==1&&state().controllers().getFirst().slots().size()==slots,"VALID_REVERSE_REJECTED");
    }
    static void tick(){TrajectoryReturnEffects.tick(new ClientTickEvent.Post());}
    static void restored(String name,String before,String matrix,String pose,int depth,int poseDepth,float[] color)throws Exception {
        require(before.equals(stateDump()),"RENDER_MUTATED_RECEIPT_STATE:"+name);
        require(matrix.equals(RenderSystem.STACK.value.toString())&&depth==RenderSystem.STACK.frames.size(),"MODEL_VIEW_NOT_RESTORED:"+name);
        require(pose.equals(event.getPoseStack().matrix.toString())&&poseDepth==event.getPoseStack().frames.size(),"POSE_NOT_RESTORED:"+name);
        require(Arrays.equals(color,RenderSystem.color),"SHADER_COLOR_NOT_RESTORED:"+name);
    }
    static void snap(String name,boolean empty)throws Exception {
        String before=stateDump(),matrix=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();
        int depth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();float[] color=RenderSystem.color.clone();
        MC.level.clipQueries=0;Trace.reset();TrajectoryReturnEffects.render(event);
        restored(name,before,matrix,pose,depth,poseDepth,color);
        require(Trace.vertices<=TrajectoryReturnMesh.MAX_VERTICES,"FRAME_BUDGET_EXCEEDED:"+name);
        if(empty)require(Trace.vertices==0,"EXPECTED_EMPTY:"+name);
        String rendered=String.join("\n",Trace.rows)+"\nterrain-queries:"+MC.level.clipQueries+"\nrestored:"+matrix+":"+pose+":"+Arrays.toString(RenderSystem.color);
        cases.write(name+"\t"+Trace.vertices+"\t"+sha(rendered)+"\t"+sha(before)+"\n");
        capture.write("CASE "+name+"\n"+rendered+"\n"+before+"\nEND\n");count++;
        if(Trace.vertices>0)nonempty++;totalVertices+=Trace.vertices;peakVertices=Math.max(peakVertices,Trace.vertices);
    }
    static void matrix()throws Exception {
        // Actual sequential receipts are re-played, never manufactured Controller/Slot instances.
        for(int scene=0;scene<6;scene++)for(String scenario:List.of("reverse","four-slots","flight","gap","discontinuous","refused","damaged","absorbed","hammer-refused","hammer-damaged","hammer-absorbed")) {
            reset(scene);boolean hammer=scenario.startsWith("hammer-");int status=scenario.endsWith("damaged")?DAMAGED:scenario.endsWith("absorbed")?ABSORBED:REFUSED;
            if(hammer)receipt(AIR_HAMMER,0,origin,origin.add(0,0,5.5),Vec3.ZERO,status);else reverse(scenario.equals("four-slots")?4:1);
            for(int age=0;age<=32;age++) {
                if(!hammer&&!scenario.equals("reverse")&&!scenario.equals("four-slots")){
                    if(age==1)receipt(FLIGHT,0,origin,origin.add(0,0,.65),new Vec3(0,0,1),COMMITTED);
                    if(age==2&&!scenario.equals("gap")){
                        Vec3 from=scenario.equals("discontinuous")?origin.add(1.2,0,.9):origin.add(0,0,.65);
                        receipt(FLIGHT,0,from,from.add(0,0,.65),new Vec3(0,0,1),COMMITTED);
                    }
                    if(age==3&&List.of("refused","damaged","absorbed").contains(scenario))receipt(CONTACT,0,origin.add(0,0,1.3),origin.add(0,0,1.5),new Vec3(0,0,1),status);
                    if(age==4&&scenario.equals("gap"))receipt(FLIGHT,0,origin.add(0,0,.65),origin.add(0,0,1.1),new Vec3(0,0,1),COMMITTED);
                }
                if(Arrays.binarySearch(AGES,age)>=0){RenderLevelStageEvent.partial=age==0?0:age%3==0?.999f:.375f;snap("matrix/scene"+scene+"/"+scenario+"/age"+age,age==32);}
                tick();
            }
        }
    }
    static void lifecycle()throws Exception {
        reset(0);snap("empty/initial",true);
        reset(0);reverse(1);receipt(ENDED,0,Vec3.ZERO,Vec3.ZERO,Vec3.ZERO,REFUSED);snap("empty/end-immediate",true);
        TrajectoryReturnEffects.receive(packet(MC.player,1,REVERSE,0,origin,origin,new Vec3(0,0,1),COMMITTED));snap("empty/late-reverse",true);
        reset(0);reverse(1);String accepted=stateDump();TrajectoryReturnEffects.receive(packet(MC.player,1,REVERSE,0,origin,origin,new Vec3(0,0,1),COMMITTED));require(accepted.equals(stateDump()),"DUPLICATE_CHANGED_STATE");snap("sequence/duplicate",false);
        TrajectoryReturnEffects.receive(packet(MC.player,3,FLIGHT,0,origin,origin.add(0,0,.5),new Vec3(0,0,1),COMMITTED));snap("empty/sequence-gap",true);
        for(String mode:List.of("clear","cancel","death","removed","teleport","world","player","connection","screen","epoch","focus","spectator","camera","chunks","frustum","wrong-stage")) {
            reset(0);reverse(4);
            switch(mode){case "clear"->TrajectoryReturnEffects.clear();case "cancel"->TrajectoryReturnEffects.abortLocal(3,71);case "death"->MC.player.alive=false;case "removed"->MC.player.removed=true;case "teleport"->MC.player.position=origin.add(9,0,0);case "world"->MC.level=new ClientLevel();case "player"->MC.player=new Player(11,OWNER);case "connection"->MC.connection=new Object();case "screen"->MC.screen=new Object();case "epoch"->AcademyClient.epoch++;case "focus"->MC.active=false;case "spectator"->MC.player.spectator=true;case "camera"->MC.camera=new Player(77,new UUID(431,77));case "chunks"->MC.level.chunks=false;case "frustum"->RenderLevelStageEvent.frustum=false;case "wrong-stage"->event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_ENTITIES,event.getCamera(),event.getModelViewMatrix(),event.getPoseStack());}
            tick();snap("empty/"+mode,true);
        }
        for(String mode:List.of("clear","session","world","connection")){
            reset(0);MC.defer=true;receipt(REVERSE,0,origin,origin,new Vec3(0,0,1),COMMITTED);
            switch(mode){case "clear"->TrajectoryReturnEffects.clear();case "session"->AcademyClient.session++;case "world"->MC.level=new ClientLevel();case "connection"->MC.connection=new Object();}MC.drain();MC.defer=false;snap("empty/queued-"+mode,true);
        }
        reset(0);reverse(1);tick();MC.paused=true;for(int i=0;i<40;i++)tick();snap("lifecycle/paused",false);MC.player.alive=false;tick();snap("empty/paused-death",true);
        reset(0);reverse(4);for(int i=1;i<=4;i++){Player p=new Player(10+i,new UUID(431,1+i));p.position=origin;MC.level.entities.put(p.id,p);TrajectoryReturnEffects.receive(packet(p,1,REVERSE,0,origin.add(i*.1,0,0),origin.add(i*.1,0,0),new Vec3(0,0,1),COMMITTED));}snap("budget/five-owners",false);require(state().controllers().size()==4,"CONTROLLER_LIMIT_WRONG");
    }
    static void exceptions(Path out)throws Exception {
        var result=new StringBuilder();
        for(String mode:List.of("vertex","flush-type","flush-finally")){
            reset(0);reverse(4);String before=stateDump(),matrix=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();
            int depth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();float[] color=RenderSystem.color.clone();
            Trace.reset();TrajectoryReturnEffects.render(event);require(Trace.vertices>0,"EXCEPTION_WITNESS_EMPTY");
            Trace.reset();Trace.arm(mode.equals("vertex")?5:-1,mode.equals("flush-finally")?0:-1,mode.equals("flush-type")?0:-1);
            boolean thrown=false;try{TrajectoryReturnEffects.render(event);}catch(Trace.InjectedFailure expected){thrown=true;}finally{Trace.disarm();}
            require(thrown,"EXCEPTION_NOT_EXERCISED:"+mode);restored(mode,before,matrix,pose,depth,poseDepth,color);
            result.append(mode).append(':').append(Trace.injectedFaults).append(':').append(sha(before)).append('\n');exceptionCases++;
        }
        Files.writeString(out.resolve("exceptions.txt"),result.toString());
    }
    public static void main(String[] args)throws Exception {
        lane=args[0];resources=Path.of(args[2]);prepare();Path out=Path.of(args[1]);Files.createDirectories(out);
        if(args.length==4&&args[3].equals("exceptions-only")){exceptions(out);System.out.println("{\"exception_cases\":"+exceptionCases+"}");return;}
        try(var c=Files.newBufferedWriter(out.resolve("cases.tsv"));var full=new BufferedWriter(new OutputStreamWriter(new GZIPOutputStream(Files.newOutputStream(out.resolve("capture.txt.gz"))),StandardCharsets.UTF_8))){cases=c;capture=full;matrix();lifecycle();}
        require(count>880&&nonempty>180&&totalVertices>30000&&peakVertices>=384,"VACUOUS_RENDER_PROOF");exceptions(out);
        System.out.println("{\"lane\":\""+lane+"\",\"cases\":"+count+",\"nonempty_cases\":"+nonempty+",\"vertices\":"+totalVertices+",\"peak_vertices\":"+peakVertices+",\"resource_checks\":"+resourceChecks+",\"exception_cases\":"+exceptionCases+"}");
    }
}
