#!/usr/bin/env python3
"""Archive-portable .36 near-eye material differential against exact restored .35.

This is a recording-adapter test, not a Minecraft/GPU acceptance test. All thirteen
.35 reference Java files are copied byte-for-byte from the restored source archive
and pinned below. References compile in an independently renamed namespace; they
are never reconstructed from the candidate. The unavailable ec0c40 Git oracle is
reported separately and its historical runner and guards remain unchanged.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import re
import subprocess
import sys
import tempfile
from zipfile import ZipFile

ROOT = Path(__file__).resolve().parents[1]
FIXTURE_ROOT = ROOT / 'src/test/resources/vfx2/railgun-near-eye/baseline-35'
BASELINE = {
    'cn/academy/port/ClassicHudConfig.java': 'eee629f8db8f5ba85dcb2660aa4aee91223865418cc979870436f7f2659c5cd9',
    'cn/academy/port/client/ClassicEffects.java': '3870996a872c8f072508eb96850bd1cd90cc3693c7991084ccc73912465dfd9a',
    'cn/academy/port/client/ClassicEffectTimeline.java': '95157ea6de87f53b16381cbb7b92d0a60b08fd1a60e3198c87ece4f789262856',
    'cn/academy/port/client/ClassicArcGeometry.java': '98c6168fb9e42ce2c2eb139ca5846ce9e77d98b5e12fba2df7b32727c51ec7f5',
    'cn/academy/port/client/vfx2/RailgunVfx2.java': '4a8174c27d710c6765b99b050fb5538f867053dfabd85f9d8ed5b74a0dc7b1c9',
    'cn/academy/port/client/vfx2/RailgunVfx2Material.java': 'eff1f5f47d4bccde86184ac14d2ecfcf41ed1b34d4cc03c7b3ed6df498d532fe',
    'cn/academy/port/client/vfx2/RailgunVfx2RenderTypes.java': '878c9bd211e45e0ffb35ea9f81eb1b7806f2b5becc155b3eada6fea334d4bf1a',
    'cn/academy/port/client/vfx2/RailgunOverlayGeometry.java': '0a165241120979e6ba86bdb0481333d1684a72de81edb13211634752e293ec53',
    'cn/academy/port/client/vfx2/RailgunVfx2Preview.java': 'c29cdea46f6c08f222e5ad22857725cdd8dad7c13c33bb5c789d5791a961bad8',
    'cn/academy/port/client/vfx2/RailgunCoreCameraSafety.java': 'cf05bb7a73bc42626c7db1fc3156969b64a6c84b8837f87fab6e50a10161752c',
    'cn/academy/port/client/vfx2/RailgunGrandPass.java': '628e8f1714f486e058d3e3356e2da751ef9e130bbb5a8d985550cc37b534d160',
    'cn/academy/port/client/vfx2/RailgunGrandGeometry.java': '69ee196de486f6e4ae261cc78cf9b6200a17b83b3b7e1fac0a8c8c48bd3bb42a',
    'cn/academy/port/client/vfx2/RailgunLaunchGeometry.java': '44bc2ffb7633922409194c16102f89c2106e252e10be475724bd368747ec5580',
}
FROZEN_RUNNERS = {
    'scripts/verify-railgun-grand-integration.py': 'e5214b71c861cc4ce48cb8eb86cf2212e604c6549e7752adbe4d99b9a2f60d03',
    'scripts/verify-railgun-vfx2-integration.py': 'b76d19409243e3fe5efe9fbbdb766d5af9a65ec06fbf23604fd26a393b02b3c6',
    'scripts/verify-displaced-needle-ledger.py': '2aa986c4b7e67319e8a0068ac21a296e435a93d307d6ac17d1084fe07a095aa4',
    'scripts/verify-classic-terminal-ingress.py': 'd0b349b1a418003f986a4d9f7f17edfb2dfe43406a87462652ee811cba0f109a',
    'scripts/verify-classic-terminal-oracles.py': '13ba676ce718c9f590455207867fc0ced634435a95bc9cd77f948af5e8586ae8',
}
GETTER_SHA256 = '8e0c6c734979d2cf2808d3800b993463816801bb8e113c20aca3c5ac6b9343b8'
VANILLA_SHADERS = {
    'assets/minecraft/shaders/core/position_tex_color.vsh': 'f49ef060bb74ae5339219382ffa33baa56aad6603ad6b9e18618181665ed992a',
    'assets/minecraft/shaders/core/position_tex_color.fsh': 'b13d5eb9a77ccc8edd4e42300edec4da941c234fb10b8f4979e141420ac44f47',
}


def digest(data):
    return hashlib.sha256(data).hexdigest()


def check_bytes(path, expected):
    data = path.read_bytes()
    assert digest(data) == expected, 'Pinned input changed: ' + str(path)
    return data.decode()


def run(command, cwd=None):
    result = subprocess.run([str(x) for x in command], cwd=cwd, text=True, capture_output=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout


def scaffold():
    for name, expected in FROZEN_RUNNERS.items():
        check_bytes(ROOT / name, expected)
    spec = importlib.util.spec_from_file_location('frozen_grand_recording', ROOT / 'scripts/verify-railgun-grand-integration.py')
    module = importlib.util.module_from_spec(spec)
    prior = sys.dont_write_bytecode
    try:
        sys.dont_write_bytecode = True
        spec.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = prior
    return module


def historical_status():
    result = subprocess.run(['git', 'cat-file', '-e', 'ec0c40bab302b878f3cf14df0540f83f61c184c7^{commit}'], cwd=ROOT, capture_output=True)
    return {'result': 'NOT_RUN', 'reason': 'missingGit' if result.returncode else 'separateHistoricalRunner',
            'baselineCommit': 'ec0c40bab302b878f3cf14df0540f83f61c184c7',
            'note': 'This runner does not claim to rerun or replace the historical ec0c40 differential.'}


# Explicit fallback model, used only while the native source artifact is absent.
# The report distinguishes it from the hash-verified Minecraft getBuffer body.
MODELED_GETTER = '''private VertexConsumer getPinnedBuffer(RenderType type) {
    BufferBuilder builder = startedBuilders.get(type);
    if (builder != null && !type.canConsolidateConsecutiveGeometry()) {
        endBatch(type, builder); builder = null;
    }
    if (builder != null) return builder;
    ByteBufferBuilder bytes = fixedBuffers.get(type);
    if (bytes != null) builder = new BufferBuilder(bytes, type.mode(), type.format());
    else {
        if (lastSharedType != null) endBatch(lastSharedType);
        builder = new BufferBuilder(sharedBuffer, type.mode(), type.format());
        lastSharedType = type;
    }
    startedBuilders.put(type, builder);
    return builder;
}'''


def recording_stubs(old, archive):
    stubs = dict(old.STUBS)
    getter = MODELED_GETTER
    seam = {'source': 'explicitSharedBufferModel', 'nativeBodyVerified': False,
            'limitation': 'Native Minecraft source archive unavailable; ended-consumer fencing is modeled.'}
    if archive.is_file():
        with ZipFile(archive) as jar:
            source = jar.read('net/minecraft/client/renderer/MultiBufferSource.java').decode()
        original = old.block(source, 'public VertexConsumer getBuffer(')
        assert digest(original.encode()) == GETTER_SHA256, 'Unexpected native getBuffer body'
        getter = original.replace('public VertexConsumer getBuffer(', 'private VertexConsumer getPinnedBuffer(')
        seam = {'source': 'pinnedMinecraft1211Body', 'nativeBodyVerified': True, 'getBufferSha256': GETTER_SHA256}
    key = 'net/minecraft/client/renderer/MultiBufferSource.java'
    stubs[key] = stubs[key].replace('/*PINNED_GET_BUFFER*/', getter)
    stubs[key] = stubs[key].replace('class BufferSource implements MultiBufferSource {', '''class BufferSource implements MultiBufferSource {
        public static Runnable nextAcquire;
        public static String throwPrefix;
    ''').replace('acquiring=true;try{return getPinnedBuffer(type);}', '''acquiring=true;try{
        if(nextAcquire!=null){var action=nextAcquire;nextAcquire=null;action.run();}
        if(throwPrefix!=null && type.name.startsWith(throwPrefix))throw new IllegalStateException("injected buffer failure");
        return getPinnedBuffer(type);}''')
    key = 'net/minecraft/world/entity/Entity.java'
    stubs[key] = stubs[key].replace('public int getId(){return 1;}', 'public int id=1;public int getId(){return id;}')
    stubs['net/minecraft/client/multiplayer/ClientLevel.java'] = '''package net.minecraft.client.multiplayer;
        public class ClientLevel {public final java.util.Map<Integer,net.minecraft.world.entity.Entity> entities=new java.util.HashMap<>();
        public net.minecraft.world.entity.Entity getEntity(int id){return entities.get(id);}}'''
    key = 'net/minecraft/client/Minecraft.java'
    stubs[key] = stubs[key].replace('public boolean hideGui;', 'public boolean hideGui;public boolean firstPerson=true;')
    stubs[key] = stubs[key].replace('return new CameraType();', 'return new CameraType(firstPerson);').replace(
        'public static class CameraType {public boolean isFirstPerson(){return true;}}',
        'public record CameraType(boolean firstPerson) {public boolean isFirstPerson(){return firstPerson;}}')
    key = 'net/neoforged/neoforge/client/event/RenderLevelStageEvent.java'
    stubs[key] = stubs[key].replace('public Stage stage=', 'public net.minecraft.world.entity.Entity cameraEntity;public boolean detached;public Stage stage=')
    stubs[key] = stubs[key].replace('return new Camera(camera,forward);', 'return new Camera(camera,forward,cameraEntity==null?net.minecraft.client.Minecraft.getInstance().player:cameraEntity,detached);')
    stubs[key] = stubs[key].replace('net.minecraft.world.phys.Vec3 forward){', 'net.minecraft.world.phys.Vec3 forward,net.minecraft.world.entity.Entity getEntity,boolean isDetached){')
    key = 'net/minecraft/client/renderer/RenderType.java'
    stubs[key] = stubs[key].replace('public final String name;', 'public static final java.util.Map<String,RenderType> registry=new java.util.HashMap<>();public final String name;')
    stubs[key] = stubs[key].replace('this.name=name;', 'this.name=name;registry.put(name,this);')
    # CompositeStateBuilder's standard depth default is LEQUAL; the production
    # classic and near-eye materials both deliberately retain that default.
    stubs[key] = stubs[key].replace('public Object depth,mask,', 'public Object depth="LEQUAL",mask,')
    key = 'net/minecraft/client/renderer/ShaderInstance.java'
    stubs[key] = stubs[key].replace('public static boolean fail,failCore,failGrand;', 'public static final java.util.Set<String> failNames=new java.util.HashSet<>();public static boolean fail,failCore,failGrand;')
    stubs[key] = stubs[key].replace('if(fail||', 'if(fail||failNames.contains(name)||')
    stubs[key] = stubs[key].replace('return name.equals("railgun_grand")?19:', 'return name.equals("railgun_near_eye_core")?32:name.equals("railgun_near_eye_glow")?31:name.equals("railgun_grand")?19:')
    stubs[key] = stubs[key].replace('public ShaderInstance(){', 'public static boolean uniformMissing;public Object getUniform(String uniform){return uniformMissing?null:new Object();}public ShaderInstance(){')
    key = 'org/lwjgl/opengl/GL20.java'
    stubs[key] = stubs[key].replace('public static int glGetProgrami(', 'public static boolean uniformUnbound;public static int glGetUniformLocation(int id,String name){return uniformUnbound?-1:1;}public static int glGetProgrami(')
    return stubs, seam


HARNESS = r'''
package oracle;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.ClassicHudConfigBaseline35;
import cn.academy.port.client.ClassicEffects;
import cn.academy.port.client.ClassicEffectsBaseline35;
import cn.academy.port.client.RailgunNearEyeMaterial;
import cn.academy.port.client.AcademyClient;
import cn.academy.port.client.vfx2.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.lang.reflect.*;
import java.util.*;

public final class RailgunNearEyeOracle {
    static int checks, scenes, selectedScenes;
    static final Minecraft MC=Minecraft.getInstance();
    static final Class<?> NOW=ClassicEffects.class, OLD=ClassicEffectsBaseline35.class;
    static final Vec3 ORIGIN=new Vec3(0,2,0), AXIS=new Vec3(0,0,-1);
    static void yes(boolean condition,String message){checks++;if(!condition)throw new AssertionError(message);}
    static Field field(Class<?> type,String name)throws Exception{var f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    static Object value(Class<?> type,String name)throws Exception{return field(type,name).get(null);}
    static List<?> beams(Class<?> type)throws Exception{return (List<?>)value(type,"BEAMS");}
    static void call(Class<?> type,String name,Class<?>[] args,Object...values)throws Exception{type.getMethod(name,args).invoke(null,values);}
    static void tick(Class<?> type)throws Exception{call(type,"onClientTick",new Class<?>[]{ClientTickEvent.Post.class},new ClientTickEvent.Post());}
    static void config(Class<?> type,String quality,boolean enabled,boolean grand){
        if(type==OLD){ClassicHudConfigBaseline35.SPEC.loaded=true;ClassicHudConfigBaseline35.VFX2_QUALITY.set(ClassicHudConfigBaseline35.Vfx2Quality.valueOf(quality));ClassicHudConfigBaseline35.GRAND_RAILGUN.set(grand);}
        else{ClassicHudConfig.SPEC.loaded=true;ClassicHudConfig.VFX2_QUALITY.set(ClassicHudConfig.Vfx2Quality.valueOf(quality));ClassicHudConfig.GRAND_RAILGUN.set(grand);ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.set(enabled);}
    }
    static void reset(Class<?> type){
        try{
            Util.now=10000;MC.paused=false;MC.queue.clear();MC.level=new ClientLevel();MC.player=new Entity();MC.connection=new Object();MC.cameraEntity=null;
            MC.options.firstPerson=true;MC.level.entities.put(1,MC.player);AcademyClient.epoch=1;
            config(type,"LOW",true,false);tick(type);Recording.clear();
            MultiBufferSource.BufferSource.throwPrefix=null;MultiBufferSource.BufferSource.nextAcquire=null;
        }catch(Exception e){throw new RuntimeException(e);}
    }
    static void add(Class<?> type,Vec3 origin,Vec3 axis,double length,int owner)throws Exception{
        if(type==NOW && owner>=0)call(type,"addRailgun",new Class<?>[]{Vec3.class,Vec3.class,double.class,int.class},origin,axis,length,owner);
        else call(type,"addRailgun",new Class<?>[]{Vec3.class,Vec3.class,double.class},origin,axis,length);
    }
    static void seed(Class<?> type)throws Exception{
        for(Object beam:beams(type)){
            ((Random)field(beam.getClass(),"random").get(beam)).setSeed(73);
            ((List<?>)field(beam.getClass(),"sparks").get(beam)).clear();
        }
    }
    static void prepare(Class<?> type,Vec3 origin,Vec3 axis,double length,long age,int owner,int number)throws Exception{
        reset(type);for(int i=0;i<number;i++)add(type,origin,axis,length,owner);MC.drain();seed(type);Util.now+=age;
    }
    static boolean near(String name){return name.contains("near_eye");}
    static boolean optional(String name){return name.startsWith("academy_railgun_vfx2_")||name.equals("academy_grand_railgun");}
    static List<String> attributes(boolean normalizeMaterial){
        return Recording.vertices.stream().filter(v->!optional(v.type)).map(v->{
            String material=v.type;
            if(normalizeMaterial&&near(material)){
                var renderType=RenderType.registry.get(material);
                yes(renderType!=null&&renderType.state!=null&&renderType.state.texture!=null,"near-eye type records original texture");
                material=renderType.state.texture.texture().toString();
            }
            return material+"|"+v.x+"|"+v.y+"|"+v.z+"|"+v.u+"|"+v.v+"|"+v.r+"|"+v.g+"|"+v.b+"|"+v.a+"|"+v.u1+"|"+v.v1+"|"+v.u2+"|"+v.v2+"|"+v.nx+"|"+v.ny+"|"+v.nz;
        }).toList();
    }
    static List<String> draw(Class<?> type,RenderLevelStageEvent event,boolean normalize)throws Exception{
        Recording.clear();call(type,"onRenderLevel",new Class<?>[]{RenderLevelStageEvent.class},event);
        yes(event.poses.depth==0,"actual production emitter restores pose stack");
        if(type==NOW){yes(value(NOW,"nearEyeFrameCamera")==null,"render clears frame camera identity");yes(!(boolean)value(NOW,"nearEyeFrameFirstPerson"),"render clears first-person frame flag");}
        for(var v:Recording.vertices)yes(v.matrix!=null&&Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z),"all actual recorded positions finite with supplied matrix");
        return attributes(normalize);
    }
    static long nearCount(){return Recording.vertices.stream().filter(v->near(v.type)).count();}
    static RegisterShadersEvent registerNear(){var event=new RegisterShadersEvent();RailgunNearEyeMaterial.shaders(event);return event;}
    static void ready(){ShaderInstance.failNames.clear();ShaderInstance.uniformMissing=false;org.lwjgl.opengl.GL20.uniformUnbound=false;org.lwjgl.opengl.GL20.linked=1;org.lwjgl.opengl.GL20.failedId=-1;System.clearProperty("academy.railgun.nearEye.forceFallback");registerNear().complete();yes(RailgunNearEyeMaterial.available(),"both near-eye shader generations ready");}
    static RenderLevelStageEvent event(Vec3 camera,Vec3 forward){var event=new RenderLevelStageEvent();event.camera=camera;event.forward=forward;return event;}
    static String state(Class<?> type)throws Exception{
        StringBuilder result=new StringBuilder().append(value(type,"clockMillis"));
        for(Object b:beams(type))for(String n:List.of("created","lastFrame","widthWiggle","glowWiggle"))result.append('|').append(field(b.getClass(),n).get(b));
        return result.toString();
    }
    static void selectedGeometry()throws Exception{
        ready();Vec3[] cameras={ORIGIN,new Vec3(14502577.5,72.25,-4277858.5),new Vec3(29999999.75,319.875,-29999999.125)};
        Vec3[] axes={AXIS,new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(0,-1,0),new Vec3(.3,-.2,.8).normalize()};
        for(Vec3 camera:cameras)for(Vec3 axis:axes)for(long age:new long[]{1,75,150,400,1700,2000,2499})for(double length:new double[]{.01,1,30,256}){
            Vec3 origin=camera.add(.03,-.02,.01);
            prepare(OLD,origin,axis,length,age,1,1);config(OLD,"OFF",false,false);var expected=draw(OLD,event(camera,axis),false);var oldState=state(OLD);
            prepare(NOW,origin,axis,length,age,1,1);config(NOW,"LOW",true,false);var actual=draw(NOW,event(camera,axis),true);
            yes(expected.equals(actual),"selected material preserves .35 positions UV RGBA other attributes count and order");
            yes(oldState.equals(state(NOW)),"selected material preserves .35 clock and post-wiggle state");
            yes(nearCount()==expected.size(),"all and only five base beam components use near-eye material");
            scenes++;if(!expected.isEmpty())selectedScenes++;
        }
    }
    static void exclusions()throws Exception{
        for(String reason:List.of("off","disabled","thirdPerson","detached","eventOtherCamera","minecraftOtherCamera","dead","removed","spectator","otherCaster","legacyIngress","epochChanged","connectionChanged","playerChanged","entityReused","unloaded")){
            prepare(OLD,ORIGIN,AXIS,30,400,1,1);config(OLD,"OFF",false,false);var expected=draw(OLD,event(ORIGIN,AXIS),false);
            prepare(NOW,ORIGIN,AXIS,30,400,reason.equals("otherCaster")?2:reason.equals("legacyIngress")?-1:1,1);
            var event=event(ORIGIN,AXIS);config(NOW,reason.equals("off")?"OFF":"LOW",!reason.equals("disabled"),false);
            switch(reason){
                case "thirdPerson" -> MC.options.firstPerson=false;
                case "detached" -> event.detached=true;
                case "eventOtherCamera" -> event.cameraEntity=new Entity();
                case "minecraftOtherCamera" -> MC.cameraEntity=new Entity();
                case "dead" -> MC.player.alive=false;
                case "removed" -> MC.player.removed=true;
                case "spectator" -> MC.player.spectator=true;
                case "epochChanged" -> AcademyClient.epoch++;
                case "connectionChanged" -> MC.connection=new Object();
                case "playerChanged","entityReused" -> {MC.player=new Entity();MC.level.entities.put(1,MC.player);}
                case "unloaded" -> ClassicHudConfig.SPEC.loaded=false;
            }
            var actual=draw(NOW,event,false);yes(nearCount()==0,"ineligible view never selects material: "+reason);yes(expected.equals(actual),"ineligible view retains exact .35 base output: "+reason);scenes++;
        }
    }
    static void queuedIdentity()throws Exception{
        for(String change:List.of("player","connection","epoch","entityMap","level")){
            reset(NOW);add(NOW,ORIGIN,AXIS,30,1);
            switch(change){case "player" -> MC.player=new Entity();case "connection" -> MC.connection=new Object();case "epoch" -> AcademyClient.epoch++;case "entityMap" -> MC.level.entities.put(1,new Entity());case "level" -> MC.level=new ClientLevel();}
            MC.drain();seed(NOW);Util.now+=400;draw(NOW,event(ORIGIN,AXIS),false);
            yes(nearCount()==0,"queued callback cannot bind stale "+change+" ownership");
            yes(beams(NOW).size()==(change.equals("level")?0:1),"ownership rejection preserves .35 same-world visual admission");
        }
        reset(NOW);for(int i=0;i<70;i++)add(NOW,ORIGIN,AXIS,30,1);MC.drain();yes(beams(NOW).size()==64,"new owned ingress retains original 64-beam cap");
        var retained=beams(NOW).getFirst();seed(NOW);Util.now+=400;draw(NOW,event(ORIGIN,AXIS),true);yes(nearCount()==64L*876,"full retained beam cap uses existing geometry without extra vertices");
        yes(beams(NOW).getFirst()==retained,"render does not replace beam ownership or list identity");
    }
    static void failures()throws Exception{
        for(String mode:List.of("glowCompile","coreCompile","link","forced","pending","buffer","missingUniform","unboundUniform")){
            prepare(OLD,ORIGIN,AXIS,30,400,1,1);config(OLD,"OFF",false,false);var expected=draw(OLD,event(ORIGIN,AXIS),false);
            ready();
            if(mode.equals("glowCompile"))ShaderInstance.failNames.add("railgun_near_eye_glow");
            if(mode.equals("coreCompile"))ShaderInstance.failNames.add("railgun_near_eye_core");
            if(mode.equals("link"))org.lwjgl.opengl.GL20.linked=0;
            if(mode.equals("forced"))System.setProperty("academy.railgun.nearEye.forceFallback","true");
            if(mode.equals("missingUniform"))ShaderInstance.uniformMissing=true;
            if(mode.equals("unboundUniform"))org.lwjgl.opengl.GL20.uniformUnbound=true;
            var registration=registerNear();if(!mode.equals("pending"))registration.complete();
            ShaderInstance.failNames.clear();ShaderInstance.uniformMissing=false;org.lwjgl.opengl.GL20.uniformUnbound=false;org.lwjgl.opengl.GL20.linked=1;System.clearProperty("academy.railgun.nearEye.forceFallback");
            prepare(NOW,ORIGIN,AXIS,30,400,1,1);
            if(mode.equals("buffer"))MultiBufferSource.BufferSource.throwPrefix="academy_railgun_near_eye";
            yes(expected.equals(draw(NOW,event(ORIGIN,AXIS),false)),"material failure preserves exact restored .35 output: "+mode);
            yes(nearCount()==0,"failed material never emits partial replacement: "+mode);scenes++;
            MultiBufferSource.BufferSource.throwPrefix=null;
        }
        ready();var stale=registerNear();ShaderInstance.failNames.add("railgun_near_eye_core");var newer=registerNear();newer.complete();ShaderInstance.failNames.clear();stale.complete();
        yes(!RailgunNearEyeMaterial.available(),"stale callbacks cannot revive a failed newer shader generation");
        ready();var pending=registerNear();pending.complete("railgun_near_eye_glow");yes(!RailgunNearEyeMaterial.available(),"partial shader readiness cannot replace any component");pending.complete("railgun_near_eye_core");yes(RailgunNearEyeMaterial.available(),"both current shader callbacks restore admission");
        prepare(OLD,ORIGIN,AXIS,30,400,1,1);config(OLD,"OFF",false,false);var expected=draw(OLD,event(ORIGIN,AXIS),false);
        prepare(NOW,ORIGIN,AXIS,30,400,1,1);MultiBufferSource.BufferSource.nextAcquire=()->registerNear();
        yes(expected.equals(draw(NOW,event(ORIGIN,AXIS),false))&&nearCount()==0,"generation invalidated during acquisition falls back before emitting any base vertex");
    }
    static void lifecycle()throws Exception{
        ready();prepare(NOW,ORIGIN,AXIS,30,400,1,1);var first=draw(NOW,event(ORIGIN,AXIS),true);var before=state(NOW);
        MC.paused=true;Util.now+=900;tick(NOW);var paused=draw(NOW,event(ORIGIN,AXIS),true);yes(first.equals(paused)&&before.equals(state(NOW)),"near-eye shares original pause-aware vertices clock and wiggle");
        MC.paused=false;Util.now+=2099;tick(NOW);draw(NOW,event(ORIGIN,AXIS),true);yes(beams(NOW).size()==1,"beam remains owned at original age2499");
        Util.now++;tick(NOW);yes(beams(NOW).isEmpty(),"owned near-eye shot expires at original2500ms");draw(NOW,event(ORIGIN,AXIS),true);yes(Recording.vertices.isEmpty(),"no near-eye queue survives original expiry");
        prepare(NOW,ORIGIN,AXIS,30,400,1,1);MC.level=new ClientLevel();tick(NOW);draw(NOW,event(ORIGIN,AXIS),true);yes(beams(NOW).isEmpty()&&Recording.vertices.isEmpty(),"world change clears owned shot");
        prepare(NOW,ORIGIN,AXIS,30,400,1,1);var wrong=event(ORIGIN,AXIS);wrong.stage=RenderLevelStageEvent.Stage.AFTER_ENTITIES;draw(NOW,wrong,true);yes(Recording.vertices.isEmpty(),"wrong render stage does not leak material");
        prepare(NOW,ORIGIN,AXIS,30,400,1,1);draw(NOW,event(ORIGIN,AXIS),true);long selected=nearCount();config(NOW,"OFF",true,false);draw(NOW,event(ORIGIN,AXIS),false);yes(selected==876&&nearCount()==0,"OFF disables visibility replacement immediately for retained shot");
        config(NOW,"LOW",true,false);draw(NOW,event(ORIGIN,AXIS),true);yes(nearCount()==876,"switching back reuses original active shot without duplication");
    }
    static void stagedSample()throws Exception{
        ready();prepare(NOW,ORIGIN,AXIS,30,400,1,1);var beam=beams(NOW).getFirst();
        var sampleType=Arrays.stream(NOW.getDeclaredClasses()).filter(c->c.getSimpleName().equals("RailgunMaterialSample")).findFirst().orElseThrow();
        var ctor=sampleType.getDeclaredConstructors()[0];ctor.setAccessible(true);var sample=ctor.newInstance(beam,400L);
        draw(NOW,event(ORIGIN,AXIS),true);Recording.clear();
        NOW.getMethod("renderRailgunMaterialPreview",sampleType,long.class,Vec3.class,org.joml.Matrix4f.class,MultiBufferSource.class,Set.class,Set.class,boolean.class)
            .invoke(null,sample,400L,ORIGIN,new org.joml.Matrix4f(),new MultiBufferSource.BufferSource(),new HashSet<RenderType>(),new HashSet<RenderType>(),false);
        yes(nearCount()==0&&Recording.vertices.size()==876,"historical staged sample never inherits live first-person material ownership");
    }
    static void materialState()throws Exception{
        ready();prepare(NOW,ORIGIN,AXIS,30,400,1,1);draw(NOW,event(ORIGIN,AXIS),true);
        var types=Recording.vertices.stream().filter(v->near(v.type)).map(v->v.type).distinct().toList();yes(types.size()==4,"three original glow textures and one white core material");
        for(String name:types){var s=RenderType.registry.get(name).state;yes("LEQUAL".equals(s.depth)&&"COLOR".equals(s.mask),"near-eye retains world occlusion and never writes faded depth");yes("NO_CULL".equals(s.cull)&&"PARTICLES".equals(s.target),"near-eye preserves classic culling and output target");yes(s.transparency==RenderType.TRANSLUCENT_TRANSPARENCY,"near-eye retains normal alpha compositing");yes(s.shader.shader().get()!=GameRenderer.FALLBACK,"selected type binds registered shader");}
        yes(Recording.batchEnds.stream().filter(RailgunNearEyeOracle::near).count()==4,"replacement batches respect shared-buffer type changes");
    }
    static void legacyReady(Class<?> type)throws Exception{
        String suffix=type==OLD?"Baseline35":"";
        var material=Class.forName("cn.academy.port.client.vfx2.RailgunVfx2Material"+suffix);
        var resources=new RegisterClientReloadListenersEvent();material.getMethod("resources",RegisterClientReloadListenersEvent.class).invoke(null,resources);
        var strip=new java.io.ByteArrayOutputStream();var noise=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(512,64,java.awt.image.BufferedImage.TYPE_INT_ARGB),"PNG",strip);
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB),"PNG",noise);
        resources.listener.onResourceManagerReload(location->new net.minecraft.server.packs.resources.ResourceManager.Resource(location.getPath().contains("loopable_noise")?noise.toByteArray():strip.toByteArray()));
        var shaders=new RegisterShadersEvent();material.getMethod("shaders",RegisterShadersEvent.class).invoke(null,shaders);shaders.complete();
        yes((boolean)material.getMethod("coreAvailable").invoke(null),"independent legacy material graph fully ready");
        var grand=Class.forName("cn.academy.port.client.vfx2.RailgunGrandPass"+suffix);shaders=new RegisterShadersEvent();grand.getMethod("register",RegisterShadersEvent.class).invoke(null,shaders);shaders.complete();
    }
    static List<String> raw(){return Recording.vertices.stream().map(Recording.Vertex::signature).toList();}
    static List<String> legacyOverlays(){return Recording.vertices.stream().filter(v->optional(v.type)&&!v.type.equals("academy_railgun_vfx2_inner_core")).map(Recording.Vertex::signature).toList();}
    static void legacyCoexistence()throws Exception{
        legacyReady(OLD);legacyReady(NOW);Vec3 origin=ORIGIN.add(2,0,0);
        for(String quality:List.of("OFF","LOW","MEDIUM"))for(boolean grand:List.of(false,true)){
            for(String mode:List.of("disabled","thirdPerson","unrelated","failure")){
                prepare(OLD,origin,AXIS,30,400,1,1);config(OLD,quality,false,grand);draw(OLD,event(ORIGIN,AXIS),false);var expected=raw();
                ready();if(mode.equals("failure")){ShaderInstance.failNames.add("railgun_near_eye_core");registerNear().complete();ShaderInstance.failNames.clear();}
                prepare(NOW,origin,AXIS,30,400,mode.equals("unrelated")?2:1,1);config(NOW,quality,!mode.equals("disabled"),grand);if(mode.equals("thirdPerson"))MC.options.firstPerson=false;
                draw(NOW,event(ORIGIN,AXIS),false);yes(expected.equals(raw()),"complete legacy material/overlay output remains exact "+quality+"/grand="+grand+"/"+mode);yes(nearCount()==0,"legacy coexistence exclusion emits no near-eye vertices");scenes++;
            }
            if(quality.equals("OFF"))continue;
            prepare(OLD,origin,AXIS,30,400,1,1);config(OLD,quality,false,grand);draw(OLD,event(ORIGIN,AXIS),false);var expectedOverlay=legacyOverlays();
            prepare(OLD,origin,AXIS,30,400,1,1);config(OLD,"OFF",false,grand);var expectedBase=draw(OLD,event(ORIGIN,AXIS),false);
            ready();prepare(NOW,origin,AXIS,30,400,1,1);config(NOW,quality,true,grand);var actual=draw(NOW,event(ORIGIN,AXIS),true);
            yes(expectedBase.equals(actual)&&nearCount()==876,"selected near-eye preserves all base vertices with legacy materials ready "+quality+"/grand="+grand);
            yes(expectedOverlay.equals(legacyOverlays()),"selected near-eye preserves old optional launch/halo geometry "+quality+"/grand="+grand);
            yes(Recording.vertices.stream().noneMatch(v->v.type.equals("academy_railgun_vfx2_inner_core")),"near-eye selected core takes material precedence without duplicate core");scenes++;
        }
    }
    static void commands()throws Exception{
        var event=new RegisterClientCommandsEvent();RailgunVfx2.commands(event);
        yes(event.dispatcher.root.children.stream().map(n->n.name).collect(java.util.stream.Collectors.toSet()).equals(Set.of("off","low","medium","status","grand","near_eye")),"only intended client command branch added");
        var route=event.dispatcher.root.children.stream().filter(n->n.name.equals("near_eye")).findFirst().orElseThrow();
        yes(route.children.stream().map(n->n.name).collect(java.util.stream.Collectors.toSet()).equals(Set.of("on","off","status")),"near-eye branch contains bounded Boolean controls and status");
        ClassicHudConfig.SPEC.loaded=true;var quality=ClassicHudConfig.VFX2_QUALITY.get();boolean grand=ClassicHudConfig.GRAND_RAILGUN.get();int saves=ClassicHudConfig.SPEC.saves;
        event.dispatcher.execute("aca_vfx2 near_eye off");yes(!ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.get(),"off command disables only new profile");
        event.dispatcher.execute("aca_vfx2 near_eye on");yes(ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.get(),"on command enables new profile");
        yes(ClassicHudConfig.VFX2_QUALITY.get()==quality&&ClassicHudConfig.GRAND_RAILGUN.get()==grand&&ClassicHudConfig.SPEC.saves==saves+2,"commands preserve other profile selections and persist exactly twice");
        ClassicHudConfig.SPEC.loaded=false;yes(event.dispatcher.execute("aca_vfx2 near_eye off")==0&&ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.get(),"unloaded spec command changes nothing");ClassicHudConfig.SPEC.loaded=true;
    }
    public static void main(String[] args)throws Exception{
        if(args.length>0){
            switch(args[0]){
                case "selected" -> selectedGeometry();
                case "exclusions" -> {ready();exclusions();}
                case "queued" -> {ready();queuedIdentity();}
                case "failures" -> failures();
                case "lifecycle" -> lifecycle();
                default -> throw new IllegalArgumentException(args[0]);
            }
            System.out.println("PASS|"+checks+"|"+scenes+"|"+selectedScenes);return;
        }
        yes(!ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.get(),"new profile default remains opt-in");
        selectedGeometry();exclusions();queuedIdentity();failures();lifecycle();stagedSample();materialState();legacyCoexistence();commands();
        System.out.println("PASS|"+checks+"|"+scenes+"|"+selectedScenes);
    }
}
'''


def shader_contract(vanilla_archive):
    # Source checks bind policy to actual GLSL without claiming GPU execution.
    folder = ROOT / 'src/main/resources/assets/academy/shaders/core'
    vertex = (folder / 'railgun_near_eye.vsh').read_text()
    fragment = (folder / 'railgun_near_eye.fsh').read_text()
    compact = lambda value: re.sub(r'\s+', '', re.sub(r'/\*.*?\*/|//[^\n]*', '', value, flags=re.S))
    def body(source):
        return re.search(r'voidmain\(\)\{(.*)\}', compact(source)).group(1).replace('{discard;}', 'discard;')
    expected_vertex = 'vec4view=ModelViewMat*vec4(Position,1.0);gl_Position=ProjMat*ModelViewMat*vec4(Position,1.0);viewDepth=-view.z;texCoord0=UV0;vertexColor=Color;'
    expected_fragment = 'vec4color=texture(Sampler0,texCoord0)*vertexColor;if(color.a<0.1)discard;fragColor=color*ColorModulator;fragColor.a*=smoothstep(0.12,FadeEnd,viewDepth);'
    def validate(v_source, f_source):
        v, f = compact(v_source), compact(f_source)
        assert body(v_source) == expected_vertex, 'Exact camera-space/projection/attribute contract changed'
        assert body(f_source) == expected_fragment, 'Exact alpha-only, original-cutoff, zero-floor contract changed'
        assert 'noperspective' not in v and 'flat' not in v + f, 'Depth interpolation must remain perspective-correct'
        assert not re.search(r'CameraPosition|length\(', v), 'No second camera subtraction or radial depth substitute'
    validate(vertex, fragment)
    rejected = []
    controls = [
        ('worldZDepth', vertex.replace('viewDepth = -view.z;', 'viewDepth = Position.z;'), fragment),
        ('doublePoseTransform', vertex.replace('vec4 view = ModelViewMat * vec4(Position, 1.0);', 'vec4 view = ModelViewMat * ModelViewMat * vec4(Position, 1.0);'), fragment),
        ('nonzeroNearFloor', vertex, fragment.replace('fragColor.a *= smoothstep', 'fragColor.a *= 0.04 + 0.96 * smoothstep')),
        ('rgbDrift', vertex, fragment.replace('fragColor = color * ColorModulator;', 'fragColor = color * ColorModulator; fragColor.rgb *= 0.5;')),
        ('postFadeCutoff', vertex, fragment.replace('fragColor.a *= smoothstep(0.12, FadeEnd, viewDepth);', 'fragColor.a *= smoothstep(0.12, FadeEnd, viewDepth); if (fragColor.a < 0.05) discard;')),
    ]
    for name, changed_vertex, changed_fragment in controls:
        assert (changed_vertex, changed_fragment) != (vertex, fragment), 'Shader control failed to mutate: ' + name
        try:
            validate(changed_vertex, changed_fragment)
        except AssertionError:
            rejected.append(name)
        else:
            raise AssertionError('Shader control survived: ' + name)
    vanilla = {'result': 'NOT_RUN', 'reason': 'nativeClientJarUnavailable'}
    if vanilla_archive.is_file():
        with ZipFile(vanilla_archive) as jar:
            native = {name: jar.read(name) for name in VANILLA_SHADERS}
        for name, data in native.items():
            assert digest(data) == VANILLA_SHADERS[name], 'Native shader witness changed: ' + name
        original_vertex = expected_vertex.replace('vec4view=ModelViewMat*vec4(Position,1.0);', '').replace('viewDepth=-view.z;', '')
        original_fragment = expected_fragment.replace('fragColor.a*=smoothstep(0.12,FadeEnd,viewDepth);', '')
        assert body(native['assets/minecraft/shaders/core/position_tex_color.vsh'].decode()) == original_vertex, 'Original native projection and attributes differ'
        assert body(native['assets/minecraft/shaders/core/position_tex_color.fsh'].decode()) == original_fragment, 'Original native texture/color/cutoff differs'
        vanilla = {'result': 'PASS', 'nativeEntries': VANILLA_SHADERS, 'comparison': 'Exact main-body preservation after removal of declared depth/alpha-only additions'}
    configs = {}
    for name, end in [('core', .8), ('glow', 1.6)]:
        config = json.loads((folder / ('railgun_near_eye_' + name + '.json')).read_text())
        assert config['vertex'] == config['fragment'] == 'academy:railgun_near_eye', 'Both scoped materials use the reviewed common shaders'
        values = {u['name']: u['values'] for u in config['uniforms']}
        assert values['FadeEnd'] == [end], 'Per-material depth range must be immutable and explicit'
        assert [s['name'] for s in config['samplers']] == ['Sampler0'], 'Only the original beam texture is sampled'
        configs[name] = {'fadeStart': .12, 'fadeEnd': end, 'sha256': digest((folder / ('railgun_near_eye_' + name + '.json')).read_bytes())}
    return {'vertexShaderSha256': digest(vertex.encode()), 'fragmentShaderSha256': digest(fragment.encode()),
            'coordinateContract': 'camera-relative Position -> ModelViewMat -> positive -view.z; one projection',
            'zeroUnsafeAlpha': True, 'onlyAlphaChanges': True, 'perMaterialUniforms': configs,
            'nativeVanillaSourceComparison': vanilla, 'shaderMutationControlsRejected': rejected,
            'gpuCompilation': 'NOT_RUN', 'projectedCoverageAndReadability': 'NOT_ACCEPTED_REQUIRES_NATIVE_PIXELS'}


def source_contract(old, sources, fixture):
    classic = sources['cn/academy/port/client/ClassicEffects.java']
    previous = fixture['cn/academy/port/client/ClassicEffects.java']
    unchanged = ['private static void cylinder(', 'private static void ringQuad(', 'private static Vec3 ring(',
                 'private static void board(', 'private static void vertex(', 'private static void renderArc(',
                 'private static void renderArcPaths(', 'private static void updateClock(',
                 'private static final class BeamSpark {', 'private static final class WeakArc {']
    for marker in unchanged:
        assert old.block(classic, marker) == old.block(previous, marker), 'Historical geometry/RNG method changed: ' + marker
    for name in ['cn/academy/port/client/ClassicEffectTimeline.java', 'cn/academy/port/client/ClassicArcGeometry.java']:
        assert sources[name] == fixture[name], 'Historical timeline/arc source changed: ' + name
    for name in ['cn/academy/port/client/vfx2/RailgunNearEyeVisibility.java', 'cn/academy/port/client/RailgunNearEyeMaterial.java']:
        source = sources[name]
        code = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
        assert not re.search(r'\b(?:hurt|setHealth|consumeCp|consumeCP|sendToServer|sendToPlayer|playSound|addParticle|broadcast)\s*\(', code), 'Near-eye material reaches gameplay/audio/network'
    config = sources['cn/academy/port/ClassicHudConfig.java']
    assert '.define("nearEyeRailgunVisibility",false)' in config, 'Near-eye profile must remain independently opt-in'
    assert re.search(r'enum Vfx2Quality\s*\{\s*OFF\s*,\s*LOW\s*,\s*MEDIUM\s*}', config), 'Existing quality enum remains unchanged'
    client = (ROOT / 'src/main/java/cn/academy/port/client/AcademyClient.java').read_text()
    assert 't.contains("entity",net.minecraft.nbt.Tag.TAG_INT)?t.getInt("entity"):-1' in client, 'Owner routing must use only the existing correctly typed caster ID'
    return {'unchangedGeometryAndRngMethods': unchanged, 'unchangedTimelineAndArcSources': True,
            'clientOnlyMaterialNoGameplayCalls': True, 'ownerPacketFieldRoute': 'source contract; no native packet ingress execution'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--pinned-minecraft-sources', type=Path)
    parser.add_argument('--java-home', type=Path)
    parser.add_argument('--vanilla-client-jar', type=Path)
    args = parser.parse_args()
    old = scaffold()
    fixture = {name: check_bytes(FIXTURE_ROOT / (name + '.txt'), expected) for name, expected in BASELINE.items()}
    names = list(BASELINE) + ['cn/academy/port/client/vfx2/RailgunNearEyeVisibility.java', 'cn/academy/port/client/RailgunNearEyeMaterial.java']
    sources = {name: (ROOT / 'src/main/java' / name).read_text() for name in names}
    vanilla_archive = args.vanilla_client_jar or ROOT.parent.parent / 'aca-gradle-cache/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar'
    shader = shader_contract(vanilla_archive)
    contracts = source_contract(old, sources, fixture)
    # Complete historical class graph gets independent names, including its config,
    # clocks, material state, admission counters and preview state.
    renames = {Path(name).stem: Path(name).stem + 'Baseline35' for name in BASELINE}
    pattern = re.compile(r'\b(' + '|'.join(sorted(renames, key=len, reverse=True)) + r')\b')
    historical_sources = {str(Path(name).with_name(renames[Path(name).stem] + '.java')):
                          pattern.sub(lambda match: renames[match.group()], source) for name, source in fixture.items()}
    archive = args.pinned_minecraft_sources or ROOT / 'build/moddev/artifacts/neoforge-21.1.252-sources.jar'
    stubs, seam = recording_stubs(old, archive)
    java_home = args.java_home or old.jdk(ROOT)
    with tempfile.TemporaryDirectory(prefix='railgun-near-eye-35-') as temporary:
        work = Path(temporary)
        generated, classes = work / 'sources', work / 'classes'
        compiled = {**stubs, **sources, **historical_sources, 'oracle/RailgunNearEyeOracle.java': HARNESS}
        for name, source in compiled.items():
            target = generated / name
            target.parent.mkdir(parents=True, exist_ok=True)
            target.write_text(source)
        paths = sorted(generated.rglob('*.java'))
        run([java_home / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-d', classes, *paths])
        output = run([java_home / 'bin/java', '-Djava.awt.headless=true', '-cp', classes, 'oracle.RailgunNearEyeOracle']).strip().split('|')
        assert output[0] == 'PASS', output
        controls = [
            ('ownerSessionFence', 'cn/academy/port/client/vfx2/RailgunNearEyeVisibility.java',
             'owner.session == session && ', '', 'exclusions', 'ineligible view never selects material: epochChanged'),
            ('actualCameraIdentity', 'cn/academy/port/client/vfx2/RailgunNearEyeVisibility.java',
             'cameraEntity == player && ', '', 'exclusions', 'ineligible view never selects material: eventOtherCamera'),
            ('offAlwaysWins', 'cn/academy/port/client/vfx2/RailgunVfx2.java',
             'frameNearEye=ClassicHudConfig.SPEC.isLoaded()&&ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.get()&&frameQuality!=ClassicHudConfig.Vfx2Quality.OFF;',
             'frameNearEye=ClassicHudConfig.SPEC.isLoaded()&&ClassicHudConfig.RAILGUN_NEAR_EYE_VISIBILITY.get();',
             'exclusions', 'ineligible view never selects material: off'),
            ('outerGeometryDrift', 'cn/academy/port/client/ClassicEffects.java',
             'length, 0.13 * widthScale, 1.0', 'length, 0.14 * widthScale, 1.0',
             'selected', 'selected material preserves .35 positions'),
            ('staleShaderGeneration', 'cn/academy/port/client/RailgunNearEyeMaterial.java',
             'if (current != generation) return;', '', 'failures', 'stale callbacks cannot revive'),
            ('acquisitionGeneration', 'cn/academy/port/client/RailgunNearEyeMaterial.java',
             'if (current != generation || !available()) return null;', '', 'failures', 'generation invalidated during acquisition'),
            ('partialShaderAdmission', 'cn/academy/port/client/RailgunNearEyeMaterial.java',
             'glowShader != null && coreShader != null', 'glowShader != null || coreShader != null',
             'failures', 'material failure preserves exact restored .35 output'),
            ('missingDepthUniform', 'cn/academy/port/client/RailgunNearEyeMaterial.java',
             'candidate.getUniform("FadeEnd") == null || ', '',
             'failures', 'material failure preserves exact restored .35 output: missingUniform'),
            ('unboundDepthUniform', 'cn/academy/port/client/RailgunNearEyeMaterial.java',
             ' || GL20.glGetUniformLocation(candidate.getId(), "FadeEnd") < 0', '',
             'failures', 'material failure preserves exact restored .35 output: unboundUniform'),
        ]
        rejected = []
        for label, name, before, after, group, expected_failure in controls:
            source = sources[name]
            assert source.count(before) == 1, 'Mutation target must be exact and unique: ' + label
            target = generated / name
            try:
                target.write_text(source.replace(before, after))
                run([java_home / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', classes, '-d', classes, target])
                failed = subprocess.run([str(java_home / 'bin/java'), '-Djava.awt.headless=true', '-cp', str(classes),
                                         'oracle.RailgunNearEyeOracle', group], text=True, capture_output=True)
                assert failed.returncode != 0 and expected_failure in failed.stdout + failed.stderr, 'Mutation failed to hit its intended assertion: ' + label + '\n' + failed.stdout + failed.stderr
                rejected.append(label)
            finally:
                target.write_text(source)
                run([java_home / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', classes, '-d', classes, target])
    report = {'result': 'PASS', 'version': 'restored-35-near-eye-differential-v1', 'assertions': int(output[1]),
              'recordedScenes': int(output[2]), 'selectedMaterialScenes': int(output[3]),
              'baselineProvenance': 'exact ../baseline-source files from restored .35 archive, independently renamed only for compilation',
              'baselineFixtureHashes': BASELINE, 'frozenHistoricalRunnersAndGuards': FROZEN_RUNNERS,
              'historicalEc0c40Oracle': historical_status(), 'sharedBufferAdapter': seam,
              'shaderContract': shader, 'sourceContracts': contracts, 'mutationControlsRejected': rejected,
              'productionSourceHashes': {name: digest(source.encode()) for name, source in sources.items()},
              'scope': 'recording adapters, exact .35 base vertex attributes/lifecycle and declared shader contracts',
              'limitations': ['No native Minecraft execution or GPU compilation', 'No first-person readability or visual-fidelity acceptance',
                              'Classic random spark geometry is source-preserved but excluded from deterministic beam vertex comparisons',
                              'No fresh ec0c40 historical Git differential']}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
