package cn.academy.port.client;

import cn.academy.port.highpower.ZeroGapArmor;
import cn.academy.port.highpower.core.ZeroGapArmorGeometry;
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

/** Complete production receipt/tick/renderer with bounded, explicit CPU hosts. No native GPU claim. */
public final class ZeroGapArmorFallbackHarness {
    static final Minecraft MC=Minecraft.getInstance();
    static final UUID OWNER=new UUID(252,1);
    static BufferedWriter cases,capture;
    static int count,nonempty,totalVertices,totalBatches,budgetQuads,midframeFaults;
    static Path resourceRoot;
    static long sequence;
    static String lane,typedDescriptor="none";
    static Vec3 feet,camera;
    static RenderLevelStageEvent event;
    static Object field(Object subject,String name)throws Exception {Class<?> c=subject instanceof Class<?> t?t:subject.getClass();Field f=c.getDeclaredField(name);f.setAccessible(true);return f.get(subject instanceof Class<?>?null:subject);}
    static Map<?,?> markers()throws Exception{return (Map<?,?>)field(ZeroGapArmorEffects.class,"MARKERS");}
    static String sha(String value)throws Exception{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));}
    static void require(boolean ok,String why){if(!ok)throw new AssertionError(why);}
    static void reset(int scene)throws Exception {
        ZeroGapArmorEffects.clear();AcademyClient.reset();MC.level=new ClientLevel();MC.player=new Player(10,OWNER);MC.level.entities.put(10,MC.player);MC.connection=new Object();MC.screen=null;MC.camera=null;MC.paused=false;MC.active=true;MC.defer=false;MC.queued.clear();sequence=0;
        feet=scene==3?new Vec3(29_999_997,4093,29_999_997):new Vec3(0,64,0);
        camera=switch(scene){case 1->feet.add(0,1.1,1.24);case 2->feet.add(0,1.1,-.02);case 4->feet.add(0,1.1,65);default->feet.add(0,1.1,7);};
        Vector3f look=scene==1?new Vector3f(.6f,0,.8f):scene==2?new Vector3f(0,1,0):new Vector3f(0,0,-1);
        PoseStack poses=new PoseStack();if(scene==5)poses.matrix.translation(.125f,-.25f,.5f).scale(.875f,1.125f,1);
        event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_PARTICLES,new Camera(camera,look,new Vector3f(0,1,0)),new Matrix4f(),poses);
        RenderLevelStageEvent.partial=.375f;RenderSystem.STACK.value.identity().translation(-4,2,7);RenderSystem.STACK.frames.clear();RenderSystem.color=new float[]{.25f,.5f,.75f,.875f};
        ZeroGapArmorEffects.localPhase();AcademyClient.accepted=AcademyClient.ended=AcademyClient.lost=0;
        if(lane.startsWith("midframe-")) {
            Object setting=Class.forName("cn.academy.port.HighPowerVisualConfig").getField("ZERO_GAP_ARMOR_STATE").get(null);
            setting.getClass().getMethod("set",boolean.class).invoke(setting,true);
            Method register=Class.forName("cn.academy.port.client.ZeroGapArmorMaterial$Registration").getMethod("register",RegisterShadersEvent.class);
            register.invoke(null,new RegisterShadersEvent(new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,"valid")));
            MC.level.onChunkQuery=()->{try {
                midframeFaults++;
                if(lane.equals("midframe-generation"))register.invoke(null,new RegisterShadersEvent(new net.minecraft.server.packs.resources.ResourceManager(resourceRoot,"missing")));
                else setting.getClass().getMethod("set",boolean.class).invoke(setting,false);
            }catch(Exception failure){throw new AssertionError("MIDFRAME_FAULT_FAILED",failure);}};
        }
    }
    static int selected(Vec3 at,double yaw,int phase){return ZeroGapArmorGeometry.capture(new Vector(at.x,at.y,at.z),yaw,phase).activeMask();}
    static int subset(int mask,int bits){int result=0,n=0;for(int i=0;i<6;i++)if((mask&1<<i)!=0){if((bits&1<<n)!=0)result|=1<<i;n++;}return result;}
    static ZeroGapArmor.Visual packet(Player owner,Vec3 at,int kind,double yaw,int physical,int bits,int gap,int plate,Vec3 point){int mask=selected(at,yaw,physical);int live=kind==2||kind==3||gap>0?0:subset(mask,bits);return new ZeroGapArmor.Visual(owner.id,owner.uuid,3,10,71,++sequence,kind,at,yaw,physical,mask,live,gap,plate,point);}
    static void active(Player owner,Vec3 at,double yaw,int physical,int bits,int gap){ZeroGapArmorEffects.receive(packet(owner,at,0,yaw,physical,bits,gap,-1,at));}
    static Vec3 crossing(Vec3 at,int physical,int id,double x,double y){Vector p=ZeroGapArmorGeometry.point(ZeroGapArmorGeometry.capture(new Vector(at.x,at.y,at.z),0,physical).plates().get(id),x,y);return new Vec3(p.x(),p.y(),p.z());}
    static void hit(Player owner,Vec3 at,double yaw,int physical,int bits,int gap,int id,Vec3 p){ZeroGapArmorEffects.receive(packet(owner,at,1,yaw,physical,bits,gap,id,p));}
    static void tick(){ZeroGapArmorEffects.tick(new ClientTickEvent.Post());}
    static String stateDump()throws Exception {
        StringBuilder b=new StringBuilder("local:").append(ZeroGapArmorEffects.localPhase()).append(':').append(ZeroGapArmorEffects.localVisual());
        var state=(ZeroGapArmorState)field(ZeroGapArmorEffects.class,"STATE");b.append("\ncounts:").append(state.size()).append(':').append(state.watermarkCount());
        for(var item:markers().entrySet()) {Object marker=item.getValue();var lease=(ZeroGapArmorState.Lease)field(marker,"lease");var contacts=(ZeroGapArmorState.Contacts)field(marker,"contacts");b.append("\nowner:").append(item.getKey()).append(':').append(field(marker,"frame")).append(':').append(lease.live()).append(':').append(lease.ending()).append(':').append(lease.quiet()).append(':').append(lease.fadeAge());for(int i=0;i<6;i++)b.append("\ncontact:").append(i).append(':').append(contacts.age(i)).append(':').append(contacts.frame(i));}
        return b.append("\ninput:").append(AcademyClient.accepted).append(':').append(AcademyClient.ended).append(':').append(AcademyClient.lost).toString();
    }
    static void snap(String name,boolean empty)throws Exception {snap(name,empty,()->ZeroGapArmorEffects.render(event));}
    interface Render {void run()throws Exception;}
    static void snap(String name,boolean empty,Render draw)throws Exception {
        Trace.reset();String before=RenderSystem.STACK.value.toString(),pose=event.getPoseStack().matrix.toString();int depth=RenderSystem.STACK.frames.size(),poseDepth=event.getPoseStack().frames.size();float[] color=RenderSystem.color.clone();draw.run();
        require(RenderSystem.STACK.frames.size()==depth&&RenderSystem.STACK.value.toString().equals(before),"MODEL_VIEW_NOT_RESTORED:"+name);require(event.getPoseStack().frames.size()==poseDepth&&event.getPoseStack().matrix.toString().equals(pose),"POSE_NOT_RESTORED:"+name);require(Arrays.equals(color,RenderSystem.color),"SHADER_COLOR_NOT_RESTORED:"+name);require(Trace.vertices%4==0,"INCOMPLETE_QUAD:"+name);if(empty)require(Trace.vertices==0,"EXPECTED_EMPTY:"+name);
        String rendered=String.join("\n",Trace.rows)+"\nrestored:"+before+":"+depth+":"+pose+":"+poseDepth+":"+Arrays.toString(RenderSystem.color),state=stateDump();cases.write(name+"\t"+Trace.vertices+"\t"+Trace.batches+"\t"+sha(rendered)+"\t"+sha(state)+"\n");capture.write("CASE "+name+"\n"+rendered+"\n"+state+"\nEND\n");count++;if(Trace.vertices>0)nonempty++;totalVertices+=Trace.vertices;totalBatches+=Trace.batches;
    }
    static void masks()throws Exception {
        for(int phase:new int[]{0,19,20,39,40,119})for(int yaw:new int[]{0,30,90,180,270,359})for(int bits=0;bits<8;bits++){reset(0);active(MC.player,feet,yaw,phase,bits,0);require(markers().size()==1,"MASK_FRAME_NOT_ACCEPTED");snap("masks/phase"+phase+"/yaw"+yaw+"/bits"+bits,false);}
    }
    static void matrix()throws Exception {
        for(int scene=0;scene<6;scene++)for(int kind=0;kind<10;kind++) {
            reset(scene);int phase=19,plate=Integer.numberOfTrailingZeros(selected(feet,0,phase));
            if(kind==0)ZeroGapArmorEffects.receive(packet(MC.player,feet,3,0,phase,0,0,-1,feet));
            else if(kind<6)active(MC.player,feet,0,phase,7,kind==1?0:kind-1);
            else if(kind==6){active(MC.player,feet,0,phase,7,0);ZeroGapArmorEffects.receive(packet(MC.player,feet,2,0,phase,0,0,-1,feet));}
            else if(kind==7)hit(MC.player,feet,0,phase,7,0,plate,crossing(feet,phase,plate,.2,.25));
            else if(kind==8)hit(MC.player,feet,0,phase,0,4,plate,crossing(feet,phase,plate,.45,.65));
            else hit(MC.player,feet,0,phase,7,0,plate,feet.add(20,0,20));
            for(int age=0;age<8;age++){snap("matrix/s"+scene+"/kind"+kind+"/age"+age,scene==4||kind==6&&age>=4);tick();}
        }
    }
    static void lifecycle()throws Exception {
        reset(0);active(MC.player,feet,0,0,7,0);var duplicate=packet(MC.player,feet,1,0,0,7,0,0,crossing(feet,0,0,0,0));ZeroGapArmorEffects.receive(duplicate);tick();ZeroGapArmorEffects.receive(duplicate);snap("life/duplicate-hit",false);active(MC.player,feet.add(0,0,2),0,0,7,0);snap("life/moved-historical-contact",false);active(MC.player,feet,0,20,7,0);snap("life/rotated-historical-contact",false);
        reset(0);hit(MC.player,feet,0,0,7,0,0,crossing(feet,0,0,0,0));MC.paused=true;for(int i=0;i<20;i++)tick();snap("life/pause",false);MC.player.alive=false;tick();snap("life/paused-death",true);
        for(String change:List.of("world","player","connection","screen","epoch","clear","abort","unloaded","stage","spectator","camera")) {
            reset(0);active(MC.player,feet,0,0,7,0);switch(change){case "world"->MC.level=new ClientLevel();case "player"->MC.player=new Player(11,OWNER);case "connection"->MC.connection=new Object();case "screen"->MC.screen=new Object();case "epoch"->AcademyClient.epoch++;case "clear"->ZeroGapArmorEffects.clear();case "abort"->ZeroGapArmorEffects.abortLocal(3,71);case "unloaded"->MC.level.chunks=false;case "stage"->event=new RenderLevelStageEvent(RenderLevelStageEvent.Stage.AFTER_ENTITIES,event.getCamera(),event.getModelViewMatrix(),event.getPoseStack());case "spectator"->MC.player.spectator=true;case "camera"->MC.camera=new Player(77,new UUID(0,77));}if(!change.equals("epoch"))tick();snap("life/"+change,!change.equals("abort"));
        }
        reset(0);active(MC.player,feet,0,47,7,4);for(int i=0;i<17;i++){snap("life/silence"+i,i>=16);tick();}
        for(String change:List.of("clear","session","world","connection")){reset(0);MC.defer=true;active(MC.player,feet,0,0,7,0);switch(change){case "clear"->ZeroGapArmorEffects.clear();case "session"->AcademyClient.session++;case "world"->MC.level=new ClientLevel();case "connection"->MC.connection=new Object();}MC.drain();snap("queued/"+change,true);}
        reset(0);active(MC.player,feet,0,0,7,0);ZeroGapArmorEffects.receive(packet(MC.player,feet,2,0,0,0,0,-1,feet));active(MC.player,feet,0,0,7,0);snap("life/terminal-replay-fence",false);
    }
    static void bounds()throws Exception {
        reset(0);for(int owner=0;owner<5;owner++){Player p=owner==0?MC.player:new Player(10+owner,new UUID(252,owner+1));MC.level.entities.put(p.id,p);Vec3 at=feet.add(owner*.12,0,0);active(p,at,0,0,7,0);for(int plate:new int[]{0,1,5})hit(p,at,0,0,7,0,plate,crossing(at,0,plate,.4,.6));}
        require(markers().size()==5,"FIVE_OWNERS_RECEIVED");snap("bounds/five-owners-four-rendered",false);int five=Trace.vertices;MC.level.entities.remove(14);snap("bounds/four-owners",false);require(five==Trace.vertices,"FIFTH_VISIBLE_OWNER_NOT_CAPPED");
        reset(0);active(MC.player,feet,0,0,7,0);snap("bounds/budget-initializes-real-buffers",false);
        var frame=((ZeroGapArmorState)field(ZeroGapArmorEffects.class,"STATE")).current(OWNER);var faces=new ArrayList<FoldedMirrorMesh.Face>();for(int owner=0;owner<12;owner++)for(int plate=0;plate<6;plate++)faces.addAll(ZeroGapArmorMesh.plate(frame,plate,-1,47));var budget=new ZeroGapArmorMesh.Budget();var ordered=ZeroGapArmorMesh.ordered(faces,new Vector(camera.x,camera.y,camera.z),new Vector(0,0,-1),budget);budgetQuads=ordered.size();if(!Boolean.getBoolean("proof.mutant"))require(budgetQuads==768&&budget.used()==768,"POST_CLIP_BUDGET_NOT_EXERCISED");
        Method draw=ZeroGapArmorEffects.class.getDeclaredMethod("drawOrdered",Matrix4f.class,List.class,Vector.class);draw.setAccessible(true);snap("bounds/injected-overflow-real-order-submit",false,()->{draw.invoke(null,event.getPoseStack().matrix,ordered,new Vector(camera.x,camera.y,camera.z));((net.minecraft.client.renderer.MultiBufferSource.BufferSource)field(ZeroGapArmorEffects.class,"buffers")).endBatch();});if(!Boolean.getBoolean("proof.mutant"))require(Trace.vertices==768*4,"BUDGET_SUBMISSION_VERTEX_COUNT");
    }
    static void prepareMaterial(Path resources)throws Exception {
        FoldedMirrorMaterial.register(new RegisterShadersEvent(new net.minecraft.server.packs.resources.ResourceManager(resources,"valid")));require(FoldedMirrorMaterial.available(),"REAL_FROZEN_MATERIAL_REGISTRATION_NOT_EXERCISED");
        if(lane.equals("frozen"))return;
        Class<?> config=Class.forName("cn.academy.port.HighPowerVisualConfig"),material=Class.forName("cn.academy.port.client.ZeroGapArmorMaterial");
        Object setting=config.getField("ZERO_GAP_ARMOR_STATE").get(null);require(!(boolean)setting.getClass().getMethod("get").invoke(setting),"DEFAULT_MUST_BE_OFF");setting.getClass().getMethod("set",boolean.class).invoke(setting,!lane.equals("off")&&!lane.startsWith("off-typed-"));
        net.neoforged.neoforge.common.ModConfigSpec.loaded=!lane.equals("config-unloaded");
        if(lane.equals("forced"))System.setProperty("academy.zeroGapArmorMaterial.forceFallback","true");
        String typed=lane.startsWith("off-typed-")?lane.substring(4):lane;
        String mode=typed.startsWith("typed-")?typed:lane.equals("missing")?"missing":lane.equals("corrupt")?"corrupt":"valid";
        var resourcesManager=new net.minecraft.server.packs.resources.ResourceManager(resources,mode);var shaders=new RegisterShadersEvent(resourcesManager);
        shaders.defer=lane.equals("shader-pending")||lane.equals("shader-stale");
        net.minecraft.client.renderer.ShaderInstance.failCompile=lane.equals("shader-compile");org.lwjgl.opengl.GL20.linked=!lane.equals("shader-link");
        if(lane.startsWith("missing-uniform-"))net.minecraft.client.renderer.ShaderInstance.missingUniform=lane.substring("missing-uniform-".length());
        Method register=Class.forName("cn.academy.port.client.ZeroGapArmorMaterial$Registration").getMethod("register",RegisterShadersEvent.class);
        int beforeAttempts=net.minecraft.client.renderer.ShaderInstance.attempts,beforeClosed=net.minecraft.client.renderer.ShaderInstance.closed;
        register.invoke(null,shaders);
        if(lane.equals("shader-stale")){net.minecraft.client.renderer.ShaderInstance.failCompile=true;register.invoke(null,new RegisterShadersEvent(new net.minecraft.server.packs.resources.ResourceManager(resources,"valid")));net.minecraft.client.renderer.ShaderInstance.failCompile=false;shaders.finish();}
        require(resourcesManager.opened==(lane.equals("forced")?0:List.of("missing","corrupt").contains(lane)?1:lane.equals("shader-compile")?2:5),"OPTIONAL_RESOURCE_GATE_NOT_EXERCISED:"+lane+":"+resourcesManager.opened);
        boolean attempted=!List.of("forced","missing","corrupt").contains(lane);
        require((net.minecraft.client.renderer.ShaderInstance.attempts>beforeAttempts)==attempted,"OPTIONAL_SHADER_CONSTRUCTION_NOT_EXERCISED:"+lane);
        if(typed.startsWith("typed-")) {
            String[] parts=typed.split("-");require(resourcesManager.descriptorMutations==1,"TYPED_JSON_MUTATION_NOT_READ");
            var candidate=net.minecraft.client.renderer.ShaderInstance.lastConstructed;require(candidate.source.equals("shader=academy:zero_gap_armor_edge"),"TYPED_CANDIDATE_NOT_CONSTRUCTED");
            var descriptor=candidate.getUniform(parts[1]);require(descriptor!=null,"TYPED_DESCRIPTOR_MISTAKEN_FOR_MISSING_UNIFORM");
            int expectedType=parts[2].equals("type")?(parts[1].equals("VisualPhase")?0:parts[1].equals("ColorModulator")?3:9):(parts[1].equals("VisualPhase")?5:parts[1].equals("ColorModulator")?6:10);
            int expectedCount=parts[2].equals("type")?(parts[1].equals("VisualPhase")?1:parts[1].equals("ColorModulator")?4:16):(parts[1].equals("VisualPhase")?2:parts[1].equals("ColorModulator")?3:1);
            require(descriptor.getType()==expectedType&&descriptor.getCount()==expectedCount,"TYPED_NATIVE_DESCRIPTOR_NOT_REPRODUCED");
            typedDescriptor=parts[1]+":type"+descriptor.getType()+":count"+descriptor.getCount();
        }
        if(lane.equals("shader-link")||lane.startsWith("missing-uniform-")||typed.startsWith("typed-"))require(net.minecraft.client.renderer.ShaderInstance.closed==beforeClosed+1,"INVALID_SHADER_NOT_CLOSED");
        Method enabled=material.getDeclaredMethod("enabled");enabled.setAccessible(true);require((boolean)enabled.invoke(null)==(lane.equals("enabled")||lane.startsWith("midframe-")),"OPTIONAL_MATERIAL_GATE_WRONG:"+lane);
        if(lane.equals("off")||lane.equals("enabled")||lane.startsWith("midframe-")||lane.equals("config-unloaded"))require(field(material,"shader")!=null,"READY_SHADER_NOT_INSTALLED");
        else require(field(material,"shader")==null,"FAILED_OR_STALE_SHADER_INSTALLED:"+lane);
    }
    public static void main(String[] args)throws Exception {
        lane=args[0];resourceRoot=Path.of(args[2]);prepareMaterial(resourceRoot);if(args.length==4&&args[3].equals("material-only")){System.out.println("{\"result\":\"READY_WITH_REQUIRED_UNIFORMS\"}");return;}Path out=Path.of(args[1]);Files.createDirectories(out);
        try(var c=Files.newBufferedWriter(out.resolve("cases.tsv"));var gz=new GZIPOutputStream(Files.newOutputStream(out.resolve("capture.txt.gz")));var full=new BufferedWriter(new OutputStreamWriter(gz,StandardCharsets.UTF_8))){cases=c;capture=full;masks();matrix();lifecycle();bounds();}
        if(lane.startsWith("midframe-"))require(midframeFaults>=300,"MIDFRAME_GUARD_NOT_EXERCISED:"+midframeFaults);
        if(!Boolean.getBoolean("proof.mutant"))require(count>700&&nonempty>500&&totalVertices>100000,"VACUOUS_RENDER_CAPTURE");
        System.out.println("{\"lane\":\""+lane+"\",\"cases\":"+count+",\"nonempty_cases\":"+nonempty+",\"vertices\":"+totalVertices+",\"batches\":"+totalBatches+",\"budget_quads\":"+budgetQuads+",\"typed_descriptor\":\""+typedDescriptor+"\"}");
    }
}
