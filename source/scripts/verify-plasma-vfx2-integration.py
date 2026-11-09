#!/usr/bin/env python3
"""Compiled Plasma VFX2 integration and immutable .52 classic differential.

Compiles the unmodified current and archived .52 ClassicVectorFinalEffects in
separate class directories, with the actual VFX2 helper/material/timeline and
classic shader/geometry classes. Recording Minecraft/GL hosts intercept draws,
uniform uploads and resource failures. Matrix arithmetic uses pinned native JOML.
This is not a GPU shader compile, Minecraft launch, or visual acceptance test.
"""
from pathlib import Path
import argparse
import hashlib
import importlib.util
import json
import os
import subprocess
import sys
import tempfile
from zipfile import ZipFile

sys.dont_write_bytecode = True
BASELINE_COMMIT = 'fab28e15b6ba5f720c2ffc150302f162851576fb'
BASELINE_SHA256 = '38b5986dae24b6452b71c35629908150fccd7d98401c9928bd2212f3b0874544'
JAVA = 'src/main/java/cn/academy/port/'
EFFECTS = 'client/ClassicVectorFinalEffects.java'
FIXTURE = 'src/test/resources/vfx2/plasma/baseline-52/ClassicVectorFinalEffects.java.txt'
PINNED = {
    'scripts/verify-light-shield-vfx2-integration.py': 'b1cb6e23575709917d6a11baf3529fa5879cf2f2ca807ade8107c4467b0fe62a',
    JAVA + 'client/ClassicVectorFinalTimeline.java': '6a8c4a6cd47f28d1d9c8b57f700f166e79688d4baa4737f2e14c814fe8e8c790',
    JAVA + 'client/ClassicVectorFinalNoise.java': 'd55d6d9953ab492b5870cefa57e9864ee412e8dbb877c4a2de7bab4b0785745f',
    JAVA + 'client/ClassicVectorFinalShader.java': '5e4bc9fc98304871aa42f8a1bb26df5607d8756661e4407d69ec6a621cd93a4c',
    JAVA + 'client/ClassicVectorFinalRenderTypes.java': '476fb2d2f6eca169a43741adca4e916770194e265c5fb4a28285df02f27fb455',
    JAVA + 'client/ClassicPlasmaCameraSpace.java': '1335efa7de96d8be7ef52e69fbca7d67e361a7c00fdceef638e3ad81d9c27233',
    JAVA + 'client/ClassicVectorStarterTimeline.java': '15f16efd346db9dd113133efb913b5697f62d2f910c9bbca9bd6872ef4b0b243',
    JAVA + 'client/ClassicCubicCurve.java': 'b322f014fdf5a3a33d5b00bb812beead5dc1f1c23cc0623d93022ce48ac57ee8',
    JAVA + 'skill/PlasmaCannonSession.java': '03b86a779de3bec92ca04516f128e27af9e1e91d9e5c0e444584a2c5025fda7f',
    'src/main/resources/assets/academy/shaders/core/classic_plasma_body.fsh': '4616f77e84f94456c17ebd8ee61315cb25693fde70f0cb44b3e70726a89a116e',
    'src/main/resources/assets/academy/shaders/core/classic_plasma_body.json': '9de2f7df13f4420bde66276ce2e2ae1a9bf0d0147d05904fce1b8b483479d5e2',
    'src/main/resources/assets/academy/shaders/core/classic_plasma_body.vsh': 'cc1bac530c5906447ed7ba0a7c942db8249e2971858f9e178bb042fc4dc9b48b',
}
PRODUCTION = [EFFECTS, 'ClassicHudConfig.java', 'client/ClassicVectorFinalTimeline.java',
              'client/ClassicVectorFinalNoise.java', 'client/ClassicVectorFinalShader.java',
              'client/ClassicVectorFinalRenderTypes.java', 'client/ClassicPlasmaCameraSpace.java',
              'client/ClassicVectorStarterTimeline.java', 'client/ClassicCubicCurve.java',
              'client/vfx2/PlasmaVfx2.java', 'client/vfx2/PlasmaVfx2Material.java',
              'client/vfx2/PlasmaVfx2Timeline.java']


def digest(data):
    return hashlib.sha256(data if isinstance(data, bytes) else data.encode()).hexdigest()


def run(command):
    p = subprocess.run([str(x) for x in command], text=True, capture_output=True)
    assert p.returncode == 0, p.stdout + p.stderr
    return p.stdout


def scaffold(root):
    path = root / 'scripts/verify-light-shield-vfx2-integration.py'
    spec = importlib.util.spec_from_file_location('plasma_recording_hosts', path)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def source_guards(root, current, baseline):
    assert digest(baseline) == BASELINE_SHA256, 'immutable .52 fixture changed'
    for path, expected in PINNED.items():
        assert digest((root / path).read_bytes()) == expected, 'immutable classic/scaffold changed: ' + path
    git = subprocess.run(['git', '-C', str(root), 'show', BASELINE_COMMIT + ':' + JAVA + EFFECTS],
                         capture_output=True)
    if git.returncode == 0:
        assert git.stdout == baseline.encode(), 'archived .52 source differs from pinned Git object'
    # Reverse only the explicitly reviewed additive integration seams. Everything
    # else, including clock/alpha, event gates and the complete classic body, must
    # reconstruct the archived .52 bytes exactly. Never refresh the oracle from HEAD.
    replacements = {
        'import cn.academy.port.client.vfx2.PlasmaVfx2;\n': '',
        'Matrix4f enhancedRoot=new Matrix4f(poses.last().pose());PlasmaVfx2.beginFrame();': '',
        'if(PlasmaVfx2.render(p.enhanced,p.balls,p.at,camera,p.alpha,enhancedRoot,buffers,clock))continue;': '',
        'final PlasmaVfx2.Instance enhanced;': '',
        'enhanced=new PlasmaVfx2.Instance(charge,clock);': '',
        'enhanced.end(clock);': '',
        'enhanced.clear();': '',
        'p.enhanced.go(clock,at);': '',
        'else if(kind.equals("plasma_cannon_position")){p.at=at;p.enhanced.sample(clock,at);}':
            'else if(kind.equals("plasma_cannon_position"))p.at=at;',
        'else if(kind.equals("plasma_cannon_ready")){p.enhanced.ready(clock);if(mine)play(caster.position(),ClassicVectorFinalTimeline.READY_SOUND,.5F);}':
            'else if(kind.equals("plasma_cannon_ready")&&mine)play(caster.position(),ClassicVectorFinalTimeline.READY_SOUND,.5F);',
    }
    restored = current
    for before, after in replacements.items():
        assert restored.count(before) == 1, 'reviewed optional integration seam changed: ' + before
        restored = restored.replace(before, after)
    assert restored == baseline, 'current minus reviewed optional hooks must exactly reconstruct .52 source'
    receive = current[current.index('public static void receive'):current.index('private static Vec3 point')]
    gates = ['target!=mc.level', '!TOKENS.live(id,casterId,token)', 'seq<=EVENTS.getOrDefault(key,0L)',
             'if(!finite(at))return', 'if(p==null||p.token!=token||p.dead)return']
    for gate in gates:
        assert receive.index(gate) < receive.index('p.enhanced.go(clock,at)'), 'GO before accepted gate: ' + gate
        assert receive.index(gate) < receive.index('p.enhanced.ready(clock)'), 'READY before accepted gate: ' + gate
        assert receive.index(gate) < receive.index('p.enhanced.sample(clock,at)'), 'position before accepted gate: ' + gate
    assert receive.index('if(!finite(dest))return') < receive.index('p.enhanced.go(clock,at)')
    assert 'if(!TOKENS.end(id,casterId,token))return' in receive
    assert 'if(p!=null&&p.token==token)p.end()' in receive
    helper = (root / (JAVA + 'client/vfx2/PlasmaVfx2.java')).read_text()
    material = (root / (JAVA + 'client/vfx2/PlasmaVfx2Material.java')).read_text()
    assert 'if(phase.ended()||alpha<=0)return;' in helper, 'END must not emit an impact cue'
    for name, source in [('helper', helper), ('material', material)]:
        for state in ['LEQUAL_DEPTH_TEST', 'COLOR_WRITE', 'NO_CULL', 'NO_LIGHTMAP', 'NO_OVERLAY', 'PARTICLES_TARGET']:
            assert state in source, name + ' render-state boundary missing: ' + state
    for forbidden in ['sendToServer(', 'setDeltaMovement(', 'hurt(', 'explode(', 'destroyBlock(', 'playSound(']:
        assert forbidden not in helper, 'optional visuals may not own gameplay/audio: ' + forbidden
    assert helper.index('if(!PlasmaVfx2Material.set(') < helper.index('buffers.getBuffer(PlasmaVfx2Material.renderType())')
    observer = helper[helper.index('public static void screenshot('):helper.index('public static void commands(')]
    assert 'Boolean.getBoolean("academy.vfx2.plasmaDiagnostics")' in observer
    for forbidden in ['getImage(', 'setCanceled(', 'setScreenshotFile(', 'render(', 'capture(', 'setPixel(']:
        assert forbidden not in observer, 'screenshot observer must stay passive: ' + forbidden
    return 'pinned Git object and fixture' if git.returncode == 0 else 'SHA256-pinned archived fixture'


def recording_hosts(old, root):
    hosts = dict(old.STUBS)
    hosts['net/minecraft/util/Mth.java'] = '''package net.minecraft.util;public class Mth {
public static int clamp(int v,int a,int b){return Math.max(a,Math.min(v,b));}
public static float clamp(float v,float a,float b){return Math.max(a,Math.min(v,b));}}'''
    hosts['net/minecraft/resources/ResourceLocation.java'] = hosts['net/minecraft/resources/ResourceLocation.java'].replace(
        'public static ResourceLocation fromNamespaceAndPath',
        'public static ResourceLocation withDefaultNamespace(String p){return new ResourceLocation("minecraft",p);}public static ResourceLocation fromNamespaceAndPath')
    hosts['net/minecraft/world/phys/Vec3.java'] = hosts['net/minecraft/world/phys/Vec3.java'].replace(
        'public double lengthSqr()', 'public double distanceToSqr(Vec3 p){return subtract(p).lengthSqr();}public double distanceTo(Vec3 p){return Math.sqrt(distanceToSqr(p));}public double lengthSqr()')
    hosts['net/minecraft/world/entity/Entity.java'] = hosts['net/minecraft/world/entity/Entity.java'].replace(
        'public int id;', 'public boolean hasImpulse;public void setDeltaMovement(net.minecraft.world.phys.Vec3 p){}public int id;')
    hosts['net/minecraft/nbt/CompoundTag.java'] = hosts['net/minecraft/nbt/CompoundTag.java'].replace(
        'public void putString', 'public void putFloat(String k,float v){map.put(k,v);}public float getFloat(String k){return map.get(k) instanceof Number n?n.floatValue():0;}public void putString')
    hosts['cn/academy/port/client/AcademyClient.java'] = '''package cn.academy.port.client;public class AcademyClient {
public static State state=new State();public static class State {public String category="vecmanip";public boolean overloadFine=true,activated=true,interfering;}
public static boolean contextStartCancelled(String s,long a,long b){return false;}public static void acceptedContextStart(String s,long a,long b,boolean c){}
public static boolean acceptedContextEnd(String s,long a,long b){return false;}public static void acceptedSingleStart(String s,long a,long b){}public static void acceptedSingleEnd(String s,long a,long b){}}'''
    hosts['net/minecraft/core/BlockPos.java'] = '''package net.minecraft.core;public record BlockPos(int x,int y,int z){public static BlockPos containing(net.minecraft.world.phys.Vec3 p){return new BlockPos((int)Math.floor(p.x),(int)Math.floor(p.y),(int)Math.floor(p.z));}}'''
    hosts['net/minecraft/world/level/ClipContext.java'] = '''package net.minecraft.world.level;public record ClipContext(net.minecraft.world.phys.Vec3 from,net.minecraft.world.phys.Vec3 to,Block block,Fluid fluid,net.minecraft.world.entity.Entity caster){public enum Block{COLLIDER}public enum Fluid{NONE}}'''
    hosts['net/minecraft/world/phys/HitResult.java'] = '''package net.minecraft.world.phys;public record HitResult(Vec3 location,Type type){public enum Type{MISS,BLOCK}public Vec3 getLocation(){return location;}public Type getType(){return type;}}'''
    hosts['net/minecraft/client/multiplayer/ClientLevel.java'] = hosts['net/minecraft/client/multiplayer/ClientLevel.java'].replace(
        'public java.util.Map', 'public boolean blocked,loaded=true;public boolean hasChunkAt(net.minecraft.core.BlockPos p){return loaded;}public net.minecraft.world.phys.HitResult clip(net.minecraft.world.level.ClipContext c){return new net.minecraft.world.phys.HitResult(c.to(),blocked?net.minecraft.world.phys.HitResult.Type.BLOCK:net.minecraft.world.phys.HitResult.Type.MISS);}public java.util.Map')
    hosts['com/mojang/blaze3d/vertex/PoseStack.java'] = hosts['com/mojang/blaze3d/vertex/PoseStack.java'].replace(
        'public void mulPose(org.joml.Quaternionf', 'public void mulPose(org.joml.Matrix4f m){poses.peek().mul(m);}public void mulPose(org.joml.Quaternionf')
    hosts['com/mojang/blaze3d/vertex/VertexConsumer.java'] = hosts['com/mojang/blaze3d/vertex/VertexConsumer.java'].replace(
        'VertexConsumer setColor(int', 'default VertexConsumer setColor(float r,float g,float b,float a){return setColor((int)(r*255),(int)(g*255),(int)(b*255),(int)(a*255));}VertexConsumer setColor(int')
    hosts['net/minecraft/client/renderer/RenderType.java'] = hosts['net/minecraft/client/renderer/RenderType.java'].replace(
        'public static class TransparencyStateShard', 'public static class DepthTestStateShard {public final String name;public DepthTestStateShard(String n,int function){name=n;}public void setupRenderState(){}public void clearRenderState(){}public String toString(){return name;}}public static class TransparencyStateShard')
    hosts['com/mojang/blaze3d/systems/RenderSystem.java'] = '''package com.mojang.blaze3d.systems;public class RenderSystem {
public static final org.joml.Matrix4f MODELVIEW=new org.joml.Matrix4f();public static org.joml.Matrix4f getModelViewMatrix(){return MODELVIEW;}
public static boolean depth=true;public static void disableDepthTest(){depth=false;}public static void enableDepthTest(){depth=true;}
public static float[] getShaderColor(){return new float[]{1,1,1,1};}}'''
    hosts['com/mojang/blaze3d/shaders/Uniform.java'] = '''package com.mojang.blaze3d.shaders;public class Uniform {
public static String failure="";public final String owner,name;public Uniform(String o,String n){owner=o;name=n;}
private void write(String value){if(owner.equals("vfx2_plasma_body")&&name.equals(failure))throw new IllegalStateException("injected upload failure");if(owner.equals("classic_plasma_body"))oracle.Recording.uniforms.add(name+"="+value);}
public void set(int i){write("i:"+i);}public void set(float f){write(oracle.Recording.bits(f));}
public void set(float x,float y,float z,float w){write(oracle.Recording.bits(x)+","+oracle.Recording.bits(y)+","+oracle.Recording.bits(z)+","+oracle.Recording.bits(w));}}'''
    hosts['net/minecraft/client/renderer/ShaderInstance.java'] = '''package net.minecraft.client.renderer;public class ShaderInstance implements AutoCloseable {
public static boolean fail,missingResource;public static String missingUniform="";public final String name;public boolean closed;
public java.util.Map<String,com.mojang.blaze3d.shaders.Uniform> uniforms=new java.util.HashMap<>();
public ShaderInstance(net.minecraft.server.packs.resources.ResourceProvider p,net.minecraft.resources.ResourceLocation r,com.mojang.blaze3d.vertex.VertexFormat f)throws java.io.IOException {
name=r.getPath();if(name.equals("vfx2_plasma_body")&&(fail||missingResource))throw new java.io.IOException("injected resource/compile failure");
var names=new java.util.ArrayList<String>(java.util.List.of("ModelViewMat","ProjMat","ballCount","alpha","VisualPhase","Charge","Ready","Launch"));for(int i=0;i<16;i++)names.add("ball"+i);
for(String n:names)if(!name.equals("vfx2_plasma_body")||!n.equals(missingUniform))uniforms.put(n,new com.mojang.blaze3d.shaders.Uniform(name,n));}
public ShaderInstance(){name="fallback";}public int getId(){return closed?0:17;}public com.mojang.blaze3d.shaders.Uniform getUniform(String n){return uniforms.get(n);}public void close(){closed=true;}}'''
    hosts['oracle/Recording.java'] = hosts['oracle/Recording.java'].replace(
        'public static void clear(){', 'public static java.util.List<String> uniforms=new java.util.ArrayList<>();public static void clear(){uniforms.clear();').replace(
        'public final String type,geometry;', 'public final String type,geometry;public final boolean finite;').replace(
        'var values=m.get(new float[16]);', 'finite=Float.isFinite(p.x)&&Float.isFinite(p.y)&&Float.isFinite(p.z);var values=m.get(new float[16]);')
    hosts['net/neoforged/neoforge/client/event/RenderLevelStageEvent.java'] = hosts['net/neoforged/neoforge/client/event/RenderLevelStageEvent.java'].replace(
        'return new net.minecraft.world.phys.Vec3(-7.5,2,11);', 'return position;').replace(
        'public static class Camera {', 'public static class Camera {public static net.minecraft.world.phys.Vec3 position=new net.minecraft.world.phys.Vec3(-7.5,2,11);')
    hosts['net/neoforged/neoforge/client/event/RenderGuiLayerEvent.java'] = '''package net.neoforged.neoforge.client.event;public class RenderGuiLayerEvent {
public static class Pre {public net.minecraft.resources.ResourceLocation getName(){return net.minecraft.resources.ResourceLocation.withDefaultNamespace("crosshair");}public Graphics getGuiGraphics(){return new Graphics();}}
public static class Graphics{public int guiWidth(){return 800;}public int guiHeight(){return 600;}}}'''
    hosts['net/neoforged/neoforge/client/event/ScreenshotEvent.java'] = '''package net.neoforged.neoforge.client.event;public class ScreenshotEvent {
public int fileReads;public java.io.File getScreenshotFile(){fileReads++;return new java.io.File("recording-host-unused.png");}
public Object getImage(){throw new AssertionError("observer accessed screenshot pixels");}public void setCanceled(boolean b){throw new AssertionError("observer changed screenshot cancellation");}public void setScreenshotFile(java.io.File f){throw new AssertionError("observer changed screenshot destination");}}'''
    hosts['cn/academy/port/client/ClassicHudTimeline.java'] = '''package cn.academy.port.client;public class ClassicHudTimeline {public record Rgba(double r,double g,double b,double a){}}'''
    hosts['cn/academy/port/client/ClassicHudCanvas.java'] = '''package cn.academy.port.client;public class ClassicHudCanvas {public ClassicHudCanvas(Object o){}public void begin(){}public void end(){}public void rect(net.minecraft.resources.ResourceLocation t,double x,double y,double w,double h,double a,double b,double c,double d,ClassicHudTimeline.Rgba color,net.minecraft.client.renderer.ShaderInstance s,Object z){}}'''
    # Only movement is reached by this visual integration harness. Use the actual
    # immutable method body, without loading server gameplay or simulating damage.
    move = old.block((root / (JAVA + 'skill/PlasmaCannonSession.java')).read_text(), 'public static double[] move(')
    hosts['cn/academy/port/skill/PlasmaCannonSession.java'] = 'package cn.academy.port.skill;public class PlasmaCannonSession {' + move + '}'
    hosts['cn/academy/port/skill/VecAccelSession.java'] = 'package cn.academy.port.skill;public class VecAccelSession {public static double speed(int ticks){throw new AssertionError("unused parabola host");}}'
    return hosts


HARNESS = r'''
package oracle;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.client.*;
import cn.academy.port.client.vfx2.*;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.opengl.GL20;
import java.util.*;
public final class PlasmaIntegrationOracle {
 static final Minecraft MC=Minecraft.getInstance();static String mode;static int frame;
 static Object get(Object owner,String name)throws Exception{Class<?> type=owner instanceof Class<?> c?c:owner.getClass();var f=type.getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class?null:owner);}
 static void check(boolean ok,String reason){if(!ok)throw new AssertionError(reason);}
 static Map<?,?> plasmas()throws Exception{return (Map<?,?>)get(ClassicVectorFinalEffects.class,"PLASMAS");}
 static Object plasma()throws Exception{return plasmas().get(7);}
 static PlasmaVfx2.Instance instance()throws Exception{return (PlasmaVfx2.Instance)get(plasma(),"enhanced");}
 static CompoundTag packet(String kind,int entity,long token,long sequence,Vec3 at){var t=new CompoundTag();t.putString("kind",kind);t.putInt("entity",entity);t.putLong("token",token);t.putLong("sequence",sequence);t.putDouble("x",at.x);t.putDouble("y",at.y);t.putDouble("z",at.z);t.putFloat("charge",37.5F);t.putDouble("dx",at.x+15);t.putDouble("dy",at.y+1);t.putDouble("dz",at.z+3);return t;}
 static void send(String kind,long token,long sequence,Vec3 at){ClassicVectorFinalEffects.receive(packet(kind,7,token,sequence,at));MC.drain();}
 static void material(){
  System.clearProperty("academy.vfx2.plasmaForceFallback");System.clearProperty("academy.vfx2.forceFallback");
  ShaderInstance.fail=ShaderInstance.missingResource=false;ShaderInstance.missingUniform="";Uniform.failure="";GL20.linked=1;GL20.inactive="";
  var classic=new RegisterShadersEvent();ClassicVectorFinalShader.register(classic);classic.complete();
  switch(mode){case "forced"->System.setProperty("academy.vfx2.plasmaForceFallback","true");case "forced-global"->System.setProperty("academy.vfx2.forceFallback","true");case "compile-failure"->ShaderInstance.fail=true;case "missing-resource"->ShaderInstance.missingResource=true;case "missing-uniform"->ShaderInstance.missingUniform="ball15";case "inactive-uniform"->GL20.inactive="Launch";case "unlinked"->GL20.linked=0;}
  var optional=new RegisterShadersEvent();PlasmaVfx2Material.register(optional);optional.complete();
  if(mode.equals("upload-failure"))Uniform.failure="ball7";
  if(mode.equals("closed-shader"))optional.registrations.forEach(r->r.candidate().close());
  if(mode.equals("reload-pending")){var reload=new RegisterShadersEvent();PlasmaVfx2Material.register(reload);}
  ClassicHudConfig.PLASMA_VFX2.set(!mode.equals("off")&&!mode.equals("baseline"));
  if(mode.equals("config-unloaded"))ClassicHudConfig.SPEC.loaded=false;else ClassicHudConfig.SPEC.loaded=true;
 }
 static void setup(long seed,Vec3 at)throws Exception{
  ClassicVectorFinalEffects.clear();Util.now=0;MC.level=new ClientLevel();MC.player=new Player(7,MC.level);MC.level.entities.put(7,MC.player);MC.connection=new Object();MC.paused=false;MC.sound.active.clear();
  ClassicVectorFinalEffects.tick(new ClientTickEvent.Post());((Random)get(ClassicVectorFinalEffects.class,"RANDOM")).setSeed(seed);
  material();send("plasma_cannon_start",10,0,at);check(plasmas().size()==1,"accepted start missing");
 }
 static String hex(String s)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 static String vec(Vec3 p){return p==null?"null":Long.toHexString(Double.doubleToLongBits(p.x))+","+Long.toHexString(Double.doubleToLongBits(p.y))+","+Long.toHexString(Double.doubleToLongBits(p.z));}
 static String timing()throws Exception{
  StringBuilder s=new StringBuilder();for(var p:plasmas().values())for(String f:List.of("token","charge","at","destination","go","dead","tornadoDead","released","age","deadTicks","alpha","lastFrame")){Object v=get(p,f);s.append(f).append('=').append(v instanceof Vec3 xyz?vec(xyz):v instanceof Float f32?Recording.bits(f32):v).append(';');}
  return s+"clock="+get(ClassicVectorFinalEffects.class,"clock")+";active="+ClassicVectorFinalEffects.active("plasma_cannon")+";charging="+ClassicVectorFinalEffects.charging()+";sounds="+MC.sound.active.size();
 }
 static String draw(String label,long now,int camera)throws Exception{
  Util.now=now;Recording.clear();var e=new RenderLevelStageEvent();
  if(camera==1){e.poses.last().pose().rotateXYZ(.21F,-.71F,.03F);RenderSystem.MODELVIEW.identity().rotateXYZ(-.1F,.41F,.07F).translate(.25F,-.1F,.4F);}
  else RenderSystem.MODELVIEW.identity();
  var before=new org.joml.Matrix4f(e.poses.last().pose());ClassicVectorFinalEffects.render(e);
  check(e.poses.depth()==1&&before.equals(e.poses.last().pose()),"caller matrix/pose leaked");check(RenderSystem.depth,"depth state leaked");
  StringBuilder vertices=new StringBuilder();for(var v:Recording.vertices)vertices.append(v.type).append('|').append(v.data()).append('\n');
  String result=label+"|"+Recording.vertices.size()+"|"+hex(vertices.toString())+"|"+hex(Recording.uniforms.toString())+"|"+hex(Recording.flushes.toString())+"|"+hex(Recording.states.toString())+"|"+hex(timing());
  frame++;return result;
 }
 static void capture(String label,long now,int camera)throws Exception{System.out.println(draw(label,now,camera));}
 static void differential()throws Exception{
  for(int scene=0;scene<3;scene++){
   double world=scene==2?30000000:0;Vec3 at=new Vec3(world+4,8,-3);RenderLevelStageEvent.Camera.position=new Vec3(world-7.5,2,11);setup(9167+scene,at);
   capture(scene+":start",0,scene%2);capture(scene+":charge",47,scene%2);capture(scene+":charge-long",713,scene%2);
   MC.paused=true;capture(scene+":pause",10713,scene%2);MC.paused=false;capture(scene+":resume",10763,scene%2);
   send("plasma_cannon_ready",10,1,at);capture(scene+":ready",10813,scene%2);
   ClassicVectorFinalEffects.releaseLocal("plasma_cannon");capture(scene+":released",10840,scene%2);
   send("plasma_cannon_go",10,2,at);ClassicVectorFinalEffects.tick(new ClientTickEvent.Post());capture(scene+":go",10890,scene%2);
   send("plasma_cannon_position",10,3,at.add(1.3,.2,.1));capture(scene+":position",10950,scene%2);
   send("plasma_cannon_position",10,2,at.add(70,0,0));capture(scene+":stale-position",11000,scene%2);
   for(int i=0;i<5;i++){Util.now+=50;ClassicVectorFinalEffects.tick(new ClientTickEvent.Post());}capture(scene+":travel",11250,scene%2);
   send("plasma_cannon_end",10,4,at);capture(scene+":end",11300,scene%2);
   send("plasma_cannon_go",10,5,at);capture(scene+":post-end",11400,scene%2);
   for(int i=0;i<35;i++){Util.now+=50;ClassicVectorFinalEffects.tick(new ClientTickEvent.Post());ClassicVectorFinalEffects.render(new RenderLevelStageEvent());Recording.clear();}
   capture(scene+":expired",Util.now,scene%2);check(plasmas().isEmpty(),"expired classic context retained");
  }
 }
 static void hooks()throws Exception{
  Vec3 at=new Vec3(4,8,-3);RenderLevelStageEvent.Camera.position=new Vec3(-7.5,2,11);setup(9167,at);
  var p=instance();check(p.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.CHARGE,"accepted start state");
  ClassicVectorFinalEffects.releaseLocal("plasma_cannon");check(p.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.CHARGE,"local release invented GO");
  send("plasma_cannon_ready",11,1,at);check(p.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.CHARGE,"wrong token READY accepted");
  send("plasma_cannon_ready",10,1,at);check(p.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.READY_CUE,"accepted READY not forwarded");
  send("plasma_cannon_go",10,1,at);check(p.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.READY_CUE,"stale sequence GO accepted");
  var bad=packet("plasma_cannon_go",7,10,2,at);bad.putDouble("dx",Double.NaN);ClassicVectorFinalEffects.receive(bad);MC.drain();check(p.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.READY_CUE,"nonfinite destination accepted");
  Util.now=50;send("plasma_cannon_go",10,3,at);check(p.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.LAUNCH,"accepted GO not forwarded");
  draw("enabled",100,1);check(Recording.vertices.stream().filter(v->v.type.equals("academy_vfx2_plasma_body")).count()==4,"replacement body absent");check(Recording.uniforms.isEmpty(),"enhanced body uploaded classic uniforms");
  check(Recording.states.stream().filter(s->s.endsWith("|vfx2_plasma_body")).allMatch(s->s.contains("|LEQUAL|COLOR_WRITE|NO_CULL|NO_LIGHTMAP|NO_OVERLAY|PARTICLES_TARGET|")),"enhanced GPU state contract");
  Util.now=150;send("plasma_cannon_position",10,4,at.add(.7,0,0));check(p.history.size()==1,"accepted actual position absent");
  Util.now=200;send("plasma_cannon_position",10,5,at.add(1.4,0,0));check(p.history.size()==2,"accepted actual history missing");
  send("plasma_cannon_position",10,4,at.add(4,0,0));check(p.history.size()==2,"stale position entered history");
  send("plasma_cannon_end",9,6,at);check(!p.state.snapshot().ended(),"stale END terminated current state");
  send("plasma_cannon_end",10,6,at);check(p.state.snapshot().ended()&&p.history.size()==0,"accepted END retained trail");
  draw("ended",225,0);check(Recording.vertices.stream().noneMatch(v->v.type.equals("academy_plasma_vfx2_wisp")),"END emitted auxiliary/impact cue");
  send("plasma_cannon_position",10,7,at.add(2,0,0));check(p.history.size()==0,"ended token revived history");
  ClassicVectorFinalEffects.clear();check(p.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.INACTIVE,"clear retained enhanced state");
  setup(9167,at);var replaced=instance();send("plasma_cannon_start",11,0,at);check(replaced.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.INACTIVE,"replacement did not close old token");
  var active=instance();ClassicVectorFinalEffects.receive(packet("plasma_cannon_go",7,11,1,at));MC.connection=new Object();MC.drain();check(active.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.CHARGE,"queued old connection GO accepted");
  ClassicVectorFinalEffects.tick(new ClientTickEvent.Post());check(plasmas().isEmpty()&&active.state.snapshot().stage()==PlasmaVfx2Timeline.Stage.INACTIVE,"connection change retained context");
  setup(9167,at);p=instance();draw("pause-before",100,0);var snapshot=p.state.snapshot();MC.paused=true;draw("pause-after",10100,0);check(snapshot.equals(p.state.snapshot()),"pause advanced optional visual clock");MC.paused=false;
  System.out.println("PASS|accepted-hooks|pose-and-state-boundaries|end-without-impact|pause|connection-and-token-cleanup");
 }
 static long enhancedVertices(){return Recording.vertices.stream().filter(v->v.type.equals("academy_vfx2_plasma_body")||v.type.equals("academy_plasma_vfx2_wisp")).count();}
 static void budgetsAndCameras()throws Exception{
  Vec3 at=new Vec3(4,8,-3);Vec3 camera=new Vec3(-7.5,2,11);RenderLevelStageEvent.Camera.position=camera;setup(9167,at);
  var budget=(PlasmaVfx2Timeline.Budget)get(PlasmaVfx2.class,"BUDGET");var buffers=new net.minecraft.client.renderer.MultiBufferSource.BufferSource();
  var balls=ClassicVectorFinalTimeline.balls(new Random(999));var root=new org.joml.Matrix4f();PlasmaVfx2.beginFrame();Recording.clear();
  for(float alpha:new float[]{0,-1,Float.NaN,Float.POSITIVE_INFINITY})check(!PlasmaVfx2.render(new PlasmaVfx2.Instance(37.5F,0),balls,at,camera,alpha,root,buffers,0),"invalid alpha admitted");
  check(budget.bodiesUsed()==0&&budget.verticesUsed()==0&&Recording.vertices.isEmpty(),"zero/nonfinite alpha used budget or vertices");
  // Fill each real instance with accepted history; the shader/material and all
  // auxiliary geometry below are production code, not an estimated vertex count.
  var instances=new ArrayList<PlasmaVfx2.Instance>();
  for(int i=0;i<5;i++){
   var p=new PlasmaVfx2.Instance(37.5F,0);instances.add(p);
   PlasmaVfx2.beginFrame();PlasmaVfx2.render(p,balls,at,camera,.4F,root,buffers,0); // establish reload fence
   p.go(0,at);for(int j=0;j<24;j++)p.sample(25L*(j+1),at.add(j*.2,i*.2,0));
  }
  PlasmaVfx2.beginFrame();Recording.clear();
  for(int i=0;i<5;i++){long before=enhancedVertices();boolean admitted=PlasmaVfx2.render(instances.get(i),balls,at.add(0,i*.2,0),camera,.4F,root,buffers,600);check(admitted==(i<4),"four body admission cap");if(i==4)check(before==enhancedVertices(),"overflow partially submitted");}
  check(budget.bodiesUsed()==4&&budget.verticesUsed()==512,"shared body/vertex reservation cap");
  check(enhancedVertices()>16&&enhancedVertices()<=512,"actual enhanced submissions must exercise auxiliaries and stay within 512");
  check(((Number)get(PlasmaVfx2.class,"submittedVertices")).longValue()==enhancedVertices(),"status counter differs from actual recorded vertices");
  check(Recording.vertices.stream().allMatch(v->v.finite),"dense-history nonfinite geometry");
  // The fifth body must reach the compiled classic fallback, including uniforms.
  setup(9167,at);for(int i=8;i<=11;i++){MC.level.entities.put(i,new Player(i,MC.level));ClassicVectorFinalEffects.receive(packet("plasma_cannon_start",i,10,0,at.add(0,(i-7)*.2,0)));MC.drain();}
  draw("five-contexts",500,0);
  check(Recording.vertices.stream().filter(v->v.type.equals("academy_vfx2_plasma_body")).count()==16,"four enhanced body quads");
  check(Recording.vertices.stream().filter(v->v.type.equals("academy_vector_final_plasma_body")).count()==4,"fifth body did not fall back to classic");
  check(Recording.uniforms.size()==18,"fifth classic body uniform upload absent");check(enhancedVertices()<=512,"five-context enhanced cap");
  for(double world:new double[]{0,30000000})for(double distance:new double[]{0,.001,.4,1.6,12}){
   Vec3 center=new Vec3(world+4.25,8.5,-3.75),eye=center.add(distance,0,0);RenderLevelStageEvent.Camera.position=eye;setup(9167,center);
   draw("near-large",500,1);check(Recording.vertices.stream().filter(v->v.type.equals("academy_vfx2_plasma_body")).count()==4,"near/large body missing");
   check(Recording.vertices.stream().allMatch(v->v.finite),"near/large nonfinite geometry");check(enhancedVertices()<=128,"single near/large body exceeded reservation");
  }
  System.out.println("PASS|zero-and-nonfinite-alpha-no-budget|four-bodies-fifth-classic|actual-submissions-at-most-512|status-counter-matches-submissions|near-eye-and-30M-world-finite");
 }
 static void diagnostics()throws Exception{
  Vec3 at=new Vec3(4,8,-3);RenderLevelStageEvent.Camera.position=new Vec3(-7.5,2,11);setup(9167,at);
  System.clearProperty("academy.vfx2.plasmaDiagnostics");String without=draw("diagnostic-frame",500,1);var e=new ScreenshotEvent();
  String state=timing()+instance().state.snapshot()+instance().history.points();PlasmaVfx2.screenshot(e);
  check(e.fileReads==0,"default-off observer read screenshot file");check(state.equals(timing()+instance().state.snapshot()+instance().history.points()),"disabled observer changed state");
  System.setProperty("academy.vfx2.plasmaDiagnostics","true");String with=draw("diagnostic-frame",500,1);check(without.equals(with),"diagnostics changed recorded frame");
  var frames=(List<?>)get(PlasmaVfx2.class,"DIAGNOSTIC_FRAMES");check(frames.size()==1,"diagnostics failed to capture current body label");
  state=timing()+instance().state.snapshot()+instance().history.points();long vertices=enhancedVertices();int uniforms=Recording.uniforms.size();PlasmaVfx2.screenshot(e);
  check(e.fileReads==1,"enabled observer did not read only file name");check(state.equals(timing()+instance().state.snapshot()+instance().history.points()),"enabled observer changed state");
  check(vertices==enhancedVertices()&&uniforms==Recording.uniforms.size(),"observer submitted geometry or uniforms");
  System.clearProperty("academy.vfx2.plasmaDiagnostics");System.out.println("PASS|diagnostics-default-off|diagnostics-frame-identical|observer-no-image-cancel-or-state-mutation");
 }
 public static void main(String[] args)throws Exception{mode=args[0];if(mode.equals("hooks"))hooks();else if(mode.equals("budgets"))budgetsAndCameras();else if(mode.equals("diagnostics"))diagnostics();else differential();}
}
'''


def find_joml(root, old, supplied):
    if supplied:
        candidates = [supplied]
    else:
        candidates = []
        for parent in [root, *root.parents]:
            for cache in [parent / 'aca-gradle-cache', parent / '.gradle']:
                candidates.extend(cache.glob('caches/modules-2/files-2.1/org.joml/joml/1.10.5/*/joml-1.10.5.jar'))
        candidates.extend((Path.home() / '.gradle').glob('caches/modules-2/files-2.1/org.joml/joml/1.10.5/*/joml-1.10.5.jar'))
    for path in candidates:
        if path.is_file() and digest(path.read_bytes()) == old.JOML_SHA256:
            return path
    raise AssertionError('pinned native JOML 1.10.5 required; pass --joml; no Gradle/download is started')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--joml', type=Path)
    parser.add_argument('--pinned-minecraft-sources', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    baseline = (root / FIXTURE).read_text()
    current = (root / (JAVA + EFFECTS)).read_text()
    origin = source_guards(root, current, baseline)
    old = scaffold(root)
    toolchain = old.jdk(root)
    joml = find_joml(root, old, args.joml)
    archive = args.pinned_minecraft_sources
    if archive is None:
        archive = next((p for p in [root / 'build/moddev/artifacts/neoforge-21.1.252-sources.jar', root.parent / 'clean-loader/build/moddev/artifacts/neoforge-21.1.252-sources.jar'] if p.is_file()), None)
    assert archive is not None, 'pinned Minecraft render-state source archive required; pass --pinned-minecraft-sources'
    with ZipFile(archive) as jar:
        native_type = jar.read('net/minecraft/client/renderer/RenderType.java')
        native_state = jar.read('net/minecraft/client/renderer/RenderStateShard.java')
    assert digest(native_type) == old.NATIVE_RENDER_TYPE_SHA256 and digest(native_state) == old.NATIVE_RENDER_STATE_SHA256, 'pinned render-state host boundary changed'
    modes = ['off', 'forced', 'forced-global', 'compile-failure', 'missing-resource', 'missing-uniform',
             'inactive-uniform', 'unlinked', 'upload-failure', 'closed-shader', 'reload-pending', 'config-unloaded']
    sources = {name: (root / (JAVA + name)).read_text() for name in PRODUCTION}
    mutations = []
    with tempfile.TemporaryDirectory(prefix='plasma-vfx2-integration-') as directory:
        work = Path(directory)
        hosts = recording_hosts(old, root)
        hosts['oracle/PlasmaIntegrationOracle.java'] = HARNESS
        traces = {}
        for label, effects in [('baseline', baseline), ('current', current)]:
            generated, classes = work / label / 'sources', work / label / 'classes'
            all_sources = dict(hosts)
            all_sources.update({'cn/academy/port/' + name: value for name, value in sources.items()})
            all_sources['cn/academy/port/' + EFFECTS] = effects
            for name, value in all_sources.items():
                path = generated / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(value)
            run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', joml,
                 '-d', classes, *sorted(generated.rglob('*.java'))])
            launch = [toolchain / 'bin/java', '-Xmx512m', '-Djava.awt.headless=true',
                      '-Doracle.root=' + str(root), '-cp', str(classes) + os.pathsep + str(joml),
                      'oracle.PlasmaIntegrationOracle']
            for mode in ['baseline'] if label == 'baseline' else modes:
                traces[mode] = run([*launch, mode]).strip().splitlines()
                if label == 'current':
                    assert traces[mode] == traces['baseline'], 'compiled classic differential failed for ' + mode
            if label == 'current':
                hook_result = run([*launch, 'hooks']).strip()
                assert hook_result.startswith('PASS|'), hook_result
                budget_result = run([*launch, 'budgets']).strip()
                assert budget_result.startswith('PASS|'), budget_result
                diagnostic_result = run([*launch, 'diagnostics']).strip()
                assert diagnostic_result.startswith('PASS|'), diagnostic_result
                # Prove the recording differential is sensitive to both old-body
                # uniform drift and an inverted fallback branch. Mutants exist
                # only in the temporary compilation tree, never production.
                target = generated / ('cn/academy/port/' + EFFECTS)
                mutation_cases = [
                    ('classic-alpha-upload', 'ClassicVectorFinalShader.set(p.alpha*p.alpha,ballData);',
                     'ClassicVectorFinalShader.set(p.alpha*.5F,ballData);'),
                    ('inverted-fallback-branch', 'if(PlasmaVfx2.render(p.enhanced,p.balls,p.at,camera,p.alpha,enhancedRoot,buffers,clock))continue;',
                     'if(!PlasmaVfx2.render(p.enhanced,p.balls,p.at,camera,p.alpha,enhancedRoot,buffers,clock))continue;'),
                ]
                for name, before, after in mutation_cases:
                    assert current.count(before) == 1, 'mutation target changed: ' + name
                    target.write_text(current.replace(before, after))
                    run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8',
                         '-cp', str(classes) + os.pathsep + str(joml), '-d', classes, target])
                    mutant = run([*launch, 'off']).strip().splitlines()
                    assert mutant != traces['baseline'], 'recording oracle failed to kill mutation: ' + name
                    mutations.append(name)
        assert len(traces['baseline']) == 42 and any(int(x.split('|')[1]) > 100 for x in traces['baseline']), 'vacuous classic trace'
    report = {
        'result': 'PASS', 'baselineCommit': BASELINE_COMMIT, 'baselineSha256': BASELINE_SHA256,
        'baselineOrigin': origin, 'sourceReconstruction': 'exact .52 bytes after reviewed optional hook removal',
        'unmodifiedProductionCompiled': PRODUCTION,
        'nativeJoml': {'version': '1.10.5', 'sha256': digest(joml.read_bytes())},
        'framesPerMode': 42, 'classicDifferentialModes': modes,
        'traceSha256': digest('\n'.join(traces['baseline'])), 'acceptedHookChecks': hook_result.split('|')[1:],
        'budgetAndCameraChecks': budget_result.split('|')[1:],
        'diagnosticObserverChecks': diagnostic_result.split('|')[1:],
        'compiledMutationsKilled': mutations,
        'comparison': ['vertex raw/transformed coordinates', 'full pose matrices', 'UV and RGBA',
                       'classic shader uniform upload order and exact float bits', 'material state and flush order',
                       'alpha, clock, positions, lifecycle timing and sound object count'],
        'immutableOriginalHashes': PINNED,
        'productionSourceHashes': {JAVA + name: digest(value) for name, value in sources.items()},
        'limitations': ['Recording client/resource/GL hosts, not a native GPU/client run',
                        'Render-state declarations/defaults checked against pinned Minecraft sources; GL driver state not observed',
                        'No GLSL execution, image/pixel comparison, performance or visual-quality claim',
                        'Gameplay/damage not simulated; movement seam copies only the pinned original move method',
                        'HUD overlay hosts are not exercised by this world-render differential'],
    }
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print('PASS: Plasma .52 compiled classic OFF/failure differential: 42 frames x ' + str(len(modes)) + ' modes; accepted hooks and recorder state boundaries')


if __name__ == '__main__':
    main()
