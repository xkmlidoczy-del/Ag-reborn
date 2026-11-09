#!/usr/bin/env python3
"""Compiled Meltdowner near-eye .53 differential using unchanged production sources.

Real renderer ingress, emitters, timelines, material and policy run against recording
Minecraft/resource/GL hosts with pinned native JOML. No Minecraft/GPU/GLSL execution
or visual acceptance is claimed. Existing historical geometry guards are untouched.
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
BASELINE_COMMIT = '8cbf2fc49cf5c28231647856eb3033f2614cdaf9'
BASELINE_SHA256 = 'b02f2dd687881d80d4c50689821f1a16619483623147db3cd9dd089b394fd1c4'
JAVA = 'src/main/java/cn/academy/port/'
EFFECTS = 'client/ClassicMeltdownerBeamEffects.java'
FIXTURE = 'src/test/resources/vfx2/md-near-eye/baseline-53/ClassicMeltdownerBeamEffects.java.txt'
PINNED = {
    'src/main/resources/assets/academy/shaders/core/classic_meltdowner_cutoff05.json': '3ee32b7f6b4a95b509839044104fe474248befcb92c71177e6a4e9bd0f0d55c2',
    'src/main/resources/assets/academy/shaders/core/classic_meltdowner_cutoff05.fsh': '5f16606f23ec0f5781fdd86a237f35d24bf6adf245247b7f9b4fda8d067f9c23',
    'scripts/verify-light-shield-vfx2-integration.py': 'b1cb6e23575709917d6a11baf3529fa5879cf2f2ca807ade8107c4467b0fe62a',
    'scripts/verify-plasma-vfx2-integration.py': '18faee15fc46c5e4138be6ae189f98110114c32fdf115150560d3bc444b51ab3',
    JAVA + 'client/ClassicMeltdownerBeamTimeline.java': 'c3287de14f074febbccab8e99ffba46e7bfabfacefb47202831564eeea859ba4',
    JAVA + 'client/ClassicMeltdownerStarterTimeline.java': 'b53f94133f8eecb4f580b48d1d16047b2f3181dbb4b83194eb38454df5cdff77',
    JAVA + 'client/ClassicAdvancedMineRayTimeline.java': '256b156e76bdd5159fb3d32911b298b6bb780782a8dc4e4e330baa7e36601a1f',
    'src/test/java/cn/academy/port/client/ClassicMeltdownerGeometryClassResourcesTest.java': '219634a1929ae701f4e2780dcdd68a5abc2d774e995df23b63ead95aa15dc270',
    'src/test/resources/classic-oracles/meltdowner-camera/files.sha256': '49cec99d629e9eb46f6cab953f4ddd1e5271b36dc4da37182ed7bbab8e00c98a',
    'src/test/resources/classic-oracles/meltdowner-camera/guards.properties': 'adb8d97593366be32ddf0c007ff7b5cb79a12d428021e9d57752fe294febe830',
}
PRODUCTION = [EFFECTS, 'ClassicHudConfig.java', 'client/ClassicMeltdownerBeamTimeline.java',
              'client/ClassicMeltdownerStarterTimeline.java', 'client/ClassicAdvancedMineRayTimeline.java',
              'client/vfx2/MdNearEyeMaterial.java', 'client/vfx2/MdNearEyeClient.java',
              'client/vfx2/MdNearEyeVisibility.java']


def digest(data):
    return hashlib.sha256(data if isinstance(data, bytes) else data.encode()).hexdigest()


def run(command):
    result = subprocess.run([str(x) for x in command], text=True, capture_output=True)
    assert result.returncode == 0, result.stdout + result.stderr
    return result.stdout


def module(root, file, name):
    spec = importlib.util.spec_from_file_location(name, root / file)
    result = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(result)
    return result


def guards(root, baseline):
    assert digest(baseline) == BASELINE_SHA256, 'immutable .53 fixture changed'
    for name, expected in PINNED.items():
        assert digest((root / name).read_bytes()) == expected, 'immutable classic/oracle input changed: ' + name
    original = subprocess.run(['git', '-C', str(root), 'show', BASELINE_COMMIT + ':' + JAVA + EFFECTS], capture_output=True)
    if original.returncode == 0:
        assert original.stdout == baseline.encode(), 'fixture differs from exact .53 Git source'
    return 'pinned Git object and fixture' if original.returncode == 0 else 'SHA256-pinned .53 fixture'


def hosts(old):
    result = dict(old.STUBS)
    key = 'net/minecraft/world/entity/Entity.java'
    result[key] = result[key].replace('public int id;', 'public java.util.UUID uuid;public java.util.UUID getUUID(){return uuid;}public int id;').replace(
        'this.id=id;world=l;', 'this.id=id;world=l;uuid=new java.util.UUID(1234,id);')
    key = 'net/minecraft/world/entity/player/Player.java'
    result[key] = result[key].replace('public Player(', 'public final Abilities abilities=new Abilities();public Abilities getAbilities(){return abilities;}public static class Abilities{public float speed=.1F;public void setWalkingSpeed(float s){speed=s;}}public Player(')
    key = 'net/minecraft/nbt/CompoundTag.java'
    result[key] = result[key].replace('public void putString', 'public boolean hasUUID(String k){return map.get(k) instanceof java.util.UUID;}public java.util.UUID getUUID(String k){return (java.util.UUID)map.get(k);}public void putUUID(String k,java.util.UUID v){map.put(k,v);}public void putString')
    key = 'net/minecraft/client/Minecraft.java'
    result[key] = result[key].replace('public boolean hideGui;', 'public boolean hideGui;public boolean firstPerson=true;public CameraType getCameraType(){return new CameraType(firstPerson);}public record CameraType(boolean value){public boolean isFirstPerson(){return value;}}')
    result[key] = result[key].replace('public boolean isPaused()', 'public final net.minecraft.client.renderer.GameRenderer gameRenderer=new net.minecraft.client.renderer.GameRenderer();public boolean isPaused()')
    key = 'net/minecraft/client/renderer/GameRenderer.java'
    result[key] = result[key].replace('public static final ShaderInstance', 'public final net.neoforged.neoforge.client.event.RenderLevelStageEvent.Camera camera=new net.neoforged.neoforge.client.event.RenderLevelStageEvent.Camera();public net.neoforged.neoforge.client.event.RenderLevelStageEvent.Camera getMainCamera(){return camera;}public static final ShaderInstance')
    key = 'net/neoforged/neoforge/client/event/RenderLevelStageEvent.java'
    result[key] = result[key].replace('return new Camera();', 'return camera==null?net.minecraft.client.Minecraft.getInstance().gameRenderer.getMainCamera():camera;').replace('public enum Stage', 'public Camera camera;public enum Stage')
    result[key] = result[key].replace('public static class Camera {', 'public static class Camera {public net.minecraft.world.phys.Vec3 position=new net.minecraft.world.phys.Vec3(-7.5,2,11);public net.minecraft.world.entity.Entity entity;public boolean detached;public net.minecraft.world.entity.Entity getEntity(){return entity==null?net.minecraft.client.Minecraft.getInstance().player:entity;}public boolean isDetached(){return detached;}')
    result[key] = result[key].replace('return new net.minecraft.world.phys.Vec3(-7.5,2,11);', 'return position;')
    key = 'net/minecraft/client/renderer/ShaderInstance.java'
    result[key] = result[key].replace('public static boolean fail;', 'public static boolean fail;public static String failName="";')
    result[key] = result[key].replace('if(fail&&name.equals("light_shield_vfx2"))', 'if(name.equals(failName)||fail)')
    result[key] = result[key].replace('"VisualPhase"', '"VisualPhase","FadeEnd"')
    result[key] = result[key].replace('public static boolean fail;', 'public static int nextId=100;public static final java.util.Map<Integer,ShaderInstance> programs=new java.util.HashMap<>();public final int id=++nextId;public static boolean fail;')
    result[key] = result[key].replace('name=r.getPath();', 'name=r.getPath();programs.put(id,this);')
    result[key] = result[key].replace('return 17;', 'return id;').replace('public boolean closed;', 'public boolean closed,pendingDelete;')
    result['org/lwjgl/opengl/GL20.java'] = result['org/lwjgl/opengl/GL20.java'].replace('GL_LINK_STATUS=35714;', 'GL_LINK_STATUS=35714,GL_DELETE_STATUS=35712;public static boolean glIsProgram(int id){var p=net.minecraft.client.renderer.ShaderInstance.programs.get(id);return p!=null&&!p.closed;}').replace('return linked;', 'if(what==GL_DELETE_STATUS){var p=net.minecraft.client.renderer.ShaderInstance.programs.get(id);return p!=null&&(p.closed||p.pendingDelete)?1:0;}return linked;')
    result[key] = result[key].replace('public ShaderInstance(){name="fallback";}', 'public ShaderInstance(){name="fallback";}public ShaderInstance(String n){name=n;}')
    # The real .53 cutoff getter is a nested adapter dependency. Preserve its
    # shader identity explicitly; shader source cutoff is pinned separately.
    result['cn/academy/port/client/ClassicMeltdownerStarterEffects.java'] = '''package cn.academy.port.client;public class ClassicMeltdownerStarterEffects {
public static final class CutoffShader {public static final net.minecraft.client.renderer.ShaderInstance SHADER=new net.minecraft.client.renderer.ShaderInstance("classic_meltdowner_cutoff05");public static net.minecraft.client.renderer.ShaderInstance get(){return SHADER;}}}'''
    key = 'net/minecraft/client/renderer/RenderType.java'
    result[key] = result[key].replace('public final String name;', 'public static final java.util.Map<String,RenderType> registry=new java.util.HashMap<>();public final String name;')
    result[key] = result[key].replace('name=n;format=f;', 'name=n;registry.put(n,this);format=f;')
    result[key] = result[key].replace('public CompositeState state;', 'public static boolean setupFailure;public static RenderType active;public final Runnable setup,clear;public CompositeState state;')
    result[key] = result[key].replace('name=n;registry.put(n,this);format=f;', 'name=n;registry.put(n,this);this.setup=setup;this.clear=clear;format=f;')
    result[key] = result[key].replace('public String signature(){', 'public void setupRenderState(){if(state==null)setup.run();else{if(setupFailure&&name.startsWith("academy_md_near_eye_"))throw new IllegalStateException("injected material setup failure");if(state.shader.shader().get().closed)throw new IllegalStateException("closed shader");active=this;}}public void clearRenderState(){if(state==null)clear.run();else active=null;}public String signature(){')

    key = 'net/neoforged/neoforge/client/event/RegisterShadersEvent.java'
    result[key] = result[key].replace('public final oracle.Resources provider=', 'public static int failAt;public final oracle.Resources provider=')
    result[key] = result[key].replace('registrations.add(new Registration(s,c));', 'if(failAt>0&&registrations.size()+1==failAt)throw new IllegalStateException("injected shader registration failure");registrations.add(new Registration(s,c));')
    # Every classic submission ends its exact type immediately. This deliberately
    # narrow shared-buffer host rejects simultaneous builders and stale writes;
    # it does not claim to execute Minecraft's native BufferSource implementation.
    key = 'net/minecraft/client/renderer/MultiBufferSource.java'
    result[key] = result[key].replace('java.util.Map<RenderType,Consumer>', 'public static Runnable beforeFlush;java.util.Map<RenderType,Consumer>')
    result[key] = result[key].replace('oracle.Recording.states.add(t.signature());', 'if(!builders.isEmpty()&&!builders.containsKey(t))throw new AssertionError("unflushed shared-buffer consumer");')
    result[key] = result[key].replace('public void endBatch(RenderType t){', 'public void endBatch(RenderType t){if(!builders.containsKey(t))throw new AssertionError("flush without acquired type");if(beforeFlush!=null){var action=beforeFlush;beforeFlush=null;action.run();}t.setupRenderState();if(RenderType.active==null)throw new AssertionError("no effective draw state");oracle.Recording.states.add(RenderType.active.signature());t.clearRenderState();if(RenderType.active!=null)throw new AssertionError("uncleared draw state");')
    return result


HARNESS = r'''
package oracle;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.client.*;
import cn.academy.port.client.vfx2.*;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
import java.lang.reflect.*;
public final class MdNearEyeOracle {
 static final Minecraft MC=Minecraft.getInstance();static String mode;static int frame,selected;
 static Object get(Object owner,String name)throws Exception {Class<?> c=owner instanceof Class<?> k?k:owner.getClass();var f=c.getDeclaredField(name);f.setAccessible(true);return f.get(owner instanceof Class?null:owner);}
 static void check(boolean b,String why){if(!b)throw new AssertionError(why);}
 static List<?> rays()throws Exception{return (List<?>)get(ClassicMeltdownerBeamEffects.class,"RAYS");}
 static Map<?,?> contexts(String name)throws Exception{return (Map<?,?>)get(ClassicMeltdownerBeamEffects.class,name);}
 static void reset(long seed){
  ClassicMeltdownerBeamEffects.clear();MC.queue.clear();MC.sound.active.clear();MC.level=new ClientLevel();MC.player=new Player(1,MC.level);MC.level.entities.put(1,MC.player);MC.connection=new Object();MC.paused=false;MC.cameraEntity=null;
  MC.options.firstPerson=true;MC.gameRenderer.getMainCamera().position=new Vec3(0,2,0);MC.gameRenderer.getMainCamera().entity=null;MC.gameRenderer.getMainCamera().detached=false;Util.now=10000;
  ClassicMeltdownerBeamEffects.tick(new ClientTickEvent.Post());
  try{((Random)get(ClassicMeltdownerBeamEffects.class,"RANDOM")).setSeed(seed);}catch(Exception e){throw new RuntimeException(e);}
  Recording.clear();
 }
 static void configure(){
  ClassicHudConfig.SPEC.loaded=!mode.equals("config-unloaded");ClassicHudConfig.MD_NEAR_EYE_VISIBILITY.set(!mode.equals("off"));
  ShaderInstance.fail=false;ShaderInstance.failName="";ShaderInstance.missingUniform="";org.lwjgl.opengl.GL20.linked=1;org.lwjgl.opengl.GL20.inactive="";
  System.clearProperty("academy.vfx2.forceFallback");System.clearProperty("academy.vfx2.mdNearEyeForceFallback");
  if(mode.equals("forced"))System.setProperty("academy.vfx2.mdNearEyeForceFallback","true");
  if(mode.equals("forced-global"))System.setProperty("academy.vfx2.forceFallback","true");
  if(mode.equals("glow-compile-failure"))ShaderInstance.failName="md_near_eye_glow";
  if(mode.equals("core-compile-failure"))ShaderInstance.failName="md_near_eye_core";
  if(mode.equals("missing-uniform"))ShaderInstance.missingUniform="FadeEnd";
  if(mode.equals("inactive-uniform"))org.lwjgl.opengl.GL20.inactive="FadeEnd";
  if(mode.equals("unlinked"))org.lwjgl.opengl.GL20.linked=0;
  RenderType.setupFailure=mode.equals("setup-failure");RegisterShadersEvent.failAt=mode.equals("second-registration-failure")?2:0;
  var event=new RegisterShadersEvent();MdNearEyeMaterial.shaders(event);
  if(!mode.equals("reload-pending"))event.complete();
  if(mode.equals("closed-shader"))event.registrations.forEach(r->r.candidate().close());
  if(mode.equals("closed-glow"))event.registrations.get(0).candidate().close();
  if(mode.equals("closed-core"))event.registrations.get(1).candidate().close();
  if(mode.equals("delete-pending-glow"))event.registrations.get(0).candidate().pendingDelete=true;
  if(mode.equals("delete-pending-core"))event.registrations.get(1).candidate().pendingDelete=true;
  if(mode.equals("reload-before-flush"))MultiBufferSource.BufferSource.beforeFlush=()->MdNearEyeMaterial.shaders(new RegisterShadersEvent());
  if(mode.equals("forced-before-flush"))MultiBufferSource.BufferSource.beforeFlush=()->System.setProperty("academy.vfx2.mdNearEyeForceFallback","true");
 }
 static CompoundTag packet(String kind,int id,long token){
  var p=(Player)MC.level.getEntity(id);var t=new CompoundTag();t.putString("kind",kind);t.putInt("entity",id);t.putUUID("entity_uuid",p.getUUID());t.putLong("token",token);t.putLong("input",0);return t;
 }
 static void receive(CompoundTag t){ClassicMeltdownerBeamEffects.receive(t);MC.drain();}
 static void start(String skill,int id,long token){receive(packet(skill+"_start",id,token));}
 static void shot(int id,long token,boolean reflection,Vec3 origin,Vec3 direction,double length){
  var t=packet(reflection?"meltdowner_reflection":"meltdowner_ray",id,token);t.putInt("index",reflection?1:0);t.putInt("charge",40);
  t.putDouble("x",origin.x);t.putDouble("y",origin.y);t.putDouble("z",origin.z);t.putDouble("dx",direction.x);t.putDouble("dy",direction.y);t.putDouble("dz",direction.z);t.putDouble("length",reflection?10:length);receive(t);
 }
 static String bytes(double x){return Long.toHexString(Double.doubleToLongBits(x));}
 static String rayState(Object ray)throws Exception{
  var w=get(ray,"wiggle");var p=(Vec3)get(ray,"origin");var d=(Vec3)get(ray,"direction");
  return get(ray,"ticks")+"/"+get(ray,"created")+"/"+bytes((double)get(ray,"length"))+"/"+bytes(p.x)+","+bytes(p.y)+","+bytes(p.z)+"/"+bytes(d.x)+","+bytes(d.y)+","+bytes(d.z)+"/"+bytes((double)get(w,"width"))+"/"+bytes((double)get(w,"glow"))+"/"+get(w,"last");
 }
 static String state()throws Exception{
  var b=new StringBuilder().append(((ClassicMeltdownerStarterTimeline.PauseClock)get(ClassicMeltdownerBeamEffects.class,"CLOCK")).elapsed()).append('|').append(MC.sound.active.size()).append('|').append(Recording.bits(MC.player.getAbilities().speed));
  for(Object r:rays())b.append("|R:").append(rayState(r));
  for(String name:List.of("CHARGES","MINERS","EXPERT_MINERS","LUCK_MINERS")){
   b.append('|').append(name);for(Object c:contexts(name).values()){b.append('/').append(get(c,"token")).append('/').append(get(c,"nonce")).append('/').append(get(c,"ticks")).append('/').append(get(c,"shown"));var r=get(c,"ray");if(r!=null)b.append('/').append(rayState(r));}
  }
  for(Object p:(List<?>)get(ClassicMeltdownerBeamEffects.class,"PARTICLES")){var v=(Vec3)get(p,"position");b.append("|P:").append(get(p,"age")).append('/').append(get(p,"life")).append('/').append(bytes(v.x)).append(',').append(bytes(v.y)).append(',').append(bytes(v.z));}
  // Serialize a copy of Random's state without consuming a production sample.
  var out=new java.io.ByteArrayOutputStream();try(var stream=new java.io.ObjectOutputStream(out)){stream.writeObject(get(ClassicMeltdownerBeamEffects.class,"RANDOM"));}b.append("|RNG:").append(Base64.getEncoder().encodeToString(out.toByteArray()));return b.toString();
 }
 static boolean near(String name){return name.startsWith("academy_md_near_eye_");}
 static String material(String name){
  if(!near(name))return name;String texture=name.substring("academy_md_near_eye_".length());return "academy_classic_beam_"+texture+"_"+texture.equals("textures/port/white.png");
 }
 static String shaderState(String s){return s.replace("|md_near_eye_glow","|classic_meltdowner_cutoff05").replace("|md_near_eye_core","|fallback");}
 static String sha(String s)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(s.getBytes(java.nio.charset.StandardCharsets.UTF_8)));}
 static void draw(String label,long delta,boolean eligible)throws Exception{
  Util.now+=delta;var event=new RenderLevelStageEvent();drawEvent(label,event,eligible);
 }
 static void drawEvent(String label,RenderLevelStageEvent e,boolean eligible)throws Exception{
  e.poses.last().pose().rotateY(.37F).rotateX(-.22F);Recording.clear();float[] color={.2F,.4F,.6F,.8F};com.mojang.blaze3d.systems.RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);
  ClassicMeltdownerBeamEffects.render(e);
  check(e.poses.depth()==1,"render leaked pose stack");check(Arrays.equals(color,com.mojang.blaze3d.systems.RenderSystem.getShaderColor()),"render leaked shader color");
  long count=Recording.vertices.stream().filter(v->near(v.type)).count();
  if(mode.equals("selected")&&eligible&&!rays().isEmpty()){check(count==876,"exactly three glow quads and two 432-vertex cylinders selected");selected++;}
  else check(Recording.states.stream().noneMatch(s->s.contains("|md_near_eye_")),"ineligible/fallback draw used near-eye shader: "+label+" mode="+mode);
  var b=new StringBuilder();for(var v:Recording.vertices)b.append(material(v.type)).append('|').append(v.data()).append('\n');
  for(String s:Recording.states)b.append("S:").append(shaderState(s)).append('\n');for(String s:Recording.flushes)b.append("F:").append(material(s)).append('\n');
  b.append(state());System.out.println(label+"|"+Recording.vertices.size()+"|"+Recording.flushes.size()+"|"+sha(b.toString()));frame++;
 }
 static void geometry()throws Exception{
  int n=0;for(double world:new double[]{0,30000000})for(double distance:new double[]{.03,12})for(Vec3 axis:List.of(new Vec3(0,0,-1),new Vec3(1,0,0),new Vec3(0,1,0),new Vec3(.3,-.2,.8).normalize()))for(double length:new double[]{0,.01,.3,1,30}){
   reset(8103+n);Vec3 origin=new Vec3(world+4.25,72.5,-world+3.125);MC.gameRenderer.getMainCamera().position=origin.add(distance,-.02,.01);start("meltdowner",1,1);shot(1,1,false,origin,axis,length);
   check(rays().size()==1,"accepted direct ingress missing");
   for(long delta:new long[]{0,1,74,125,200,1400,499,1,201})draw("geometry-"+n+"-"+delta,delta,true);n++;
  }
 }
 static void exclusions()throws Exception{
  for(String reason:List.of("remote","reflection","third-person","detached","other-event-camera","other-event-entity","other-minecraft-camera","spectator","removed","dead","uuid-reused","entity-replaced","player-replaced","connection-replaced","level-replaced","wrong-stage")){
   reset(610);int id=reason.equals("remote")?2:1;if(id==2)MC.level.entities.put(2,new Player(2,MC.level));start("meltdowner",id,1);shot(id,1,reason.equals("reflection"),new Vec3(0,2,0),new Vec3(0,0,-1),30);var e=new RenderLevelStageEvent();
   switch(reason){
    case "third-person"->MC.options.firstPerson=false;case "detached"->MC.gameRenderer.getMainCamera().detached=true;
    case "other-event-camera"->e.camera=new RenderLevelStageEvent.Camera();case "other-event-entity"->MC.gameRenderer.getMainCamera().entity=new Player(2,MC.level);
    case "other-minecraft-camera"->MC.cameraEntity=new Player(2,MC.level);case "spectator"->MC.player.spectator=true;case "removed"->MC.player.removed=true;case "dead"->MC.player.alive=false;
    case "uuid-reused"->MC.player.uuid=new UUID(9,9);case "entity-replaced"->MC.level.entities.put(1,new Player(1,MC.level));
    case "player-replaced"->{MC.player=new Player(1,MC.level);MC.level.entities.put(1,MC.player);}case "connection-replaced"->MC.connection=new Object();case "level-replaced"->MC.level=new ClientLevel();case "wrong-stage"->e.stage=RenderLevelStageEvent.Stage.AFTER_ENTITIES;
   }
   Util.now+=400;drawEvent("exclude-"+reason,e,false);
  }
  for(String skill:List.of("mine_ray_basic","mine_ray_expert","mine_ray_luck"))for(boolean remote:new boolean[]{false,true}){
   reset(872);int id=remote?2:1;if(remote)MC.level.entities.put(2,new Player(2,MC.level));start(skill,id,1);
   for(int tick=0;tick<5;tick++){Util.now+=50;ClassicMeltdownerBeamEffects.tick(new ClientTickEvent.Post());draw("miner-"+skill+"-"+remote+"-"+tick,0,false);}
   var particles=packet(skill+"_particles",id,1);particles.putInt("index",0);particles.putInt("x",-1);particles.putInt("y",-1);particles.putInt("z",-1);receive(particles);draw("miner-particles-"+skill+"-"+remote,10,false);
   receive(packet(skill+"_end",id,1));draw("miner-ended-"+skill+"-"+remote,50,false);
  }
 }
 static void lifecycle()throws Exception{
  reset(800);start("meltdowner",1,1);shot(1,1,false,new Vec3(0,2,0),new Vec3(0,0,-1),30);shot(1,1,true,new Vec3(0,2,-5),new Vec3(1,0,0),10);
  for(int tick=0;tick<=51;tick++){
   if(tick==3){MC.paused=true;draw("pause",7000,true);MC.paused=false;}
   if(tick==5){shot(1,1,false,new Vec3(9,9,9),new Vec3(1,0,0),10);check(rays().size()==2,"duplicate ray admitted");receive(packet("meltdowner_end",1,1));}
   Util.now+=50;ClassicMeltdownerBeamEffects.tick(new ClientTickEvent.Post());draw("life-"+tick,0,tick<=49);
  }
  check(rays().isEmpty(),"ray lifetime did not clear");
  for(String change:List.of("level","player","connection","clear")){
   reset(24);var t=packet("meltdowner_start",1,9);ClassicMeltdownerBeamEffects.receive(t);
   switch(change){case "level"->MC.level=new ClientLevel();case "player"->MC.player=new Player(1,MC.level);case "connection"->MC.connection=new Object();case "clear"->ClassicMeltdownerBeamEffects.clear();}
   MC.drain();check(contexts("CHARGES").isEmpty(),"stale queued callback admitted after "+change);draw("queued-"+change,0,false);
  }
 }
 static void reload()throws Exception{
  mode="selected";configure();check(MdNearEyeMaterial.available(),"two shaders should be available");
  var stale=new RegisterShadersEvent();MdNearEyeMaterial.shaders(stale);check(!MdNearEyeMaterial.available(),"reload did not invalidate previous pair");
  var fresh=new RegisterShadersEvent();MdNearEyeMaterial.shaders(fresh);stale.complete();check(!MdNearEyeMaterial.available(),"stale callback revived previous generation");
  check(fresh.registrations.size()==2,"missing pair registration");var first=fresh.registrations.getFirst();first.callback().accept(first.candidate());check(!MdNearEyeMaterial.available(),"half generation allowed partial material selection");
  fresh.complete();check(MdNearEyeMaterial.available(),"fresh pair not ready");
  var texture=net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","textures/effects/mdray/tile.png");
  var material=Arrays.stream(ClassicMeltdownerBeamEffects.class.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Material")).findFirst().orElseThrow();
  var factory=material.getDeclaredMethod("type",net.minecraft.resources.ResourceLocation.class,boolean.class);factory.setAccessible(true);
  var original=(RenderType)factory.invoke(null,texture,false);var valid=MdNearEyeMaterial.choose(texture,false,original);check(valid!=original,"valid MD material not selected");
  int cache=((Map<?,?>)get(MdNearEyeMaterial.class,"TYPES")).size();
  check(MdNearEyeMaterial.choose(null,false,original)==original,"null texture changed original");
  check(MdNearEyeMaterial.choose(texture,false,null)==null,"null original not retained");
  check(MdNearEyeMaterial.choose(texture,true,original)==original,"MD board accepted as core");
  check(MdNearEyeMaterial.choose(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","textures/effects/mdray_small/tile.png"),false,original)==original,"miner texture accepted");
  for(int i=0;i<1000;i++)check(MdNearEyeMaterial.choose(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","unknown/"+i),false,original)==original,"unknown texture accepted");
  check(((Map<?,?>)get(MdNearEyeMaterial.class,"TYPES")).size()==cache,"rejected textures enlarged cache");
  RegisterShadersEvent.failAt=2;var failed=new RegisterShadersEvent();MdNearEyeMaterial.shaders(failed);failed.complete();check(!MdNearEyeMaterial.available(),"partial registration failure resurrected first candidate");RegisterShadersEvent.failAt=0;
  System.out.println("PASS|reload-invalidates-pair|stale-callback-rejected|half-generation-classic|fresh-generation-ready|second-registration-failure-classic|null-and-wrong-role-textures-rejected|unknown-texture-cache-bounded");
 }
 static void commands()throws Exception{
  check(!ClassicHudConfig.MD_NEAR_EYE_VISIBILITY.get(),"new option must default off");reset(1);ClassicHudConfig.SPEC.loaded=true;
  var e=new RegisterClientCommandsEvent();MdNearEyeClient.commands(e);check(!MdNearEyeClient.enabled(),"default client mode changed");
  e.dispatcher.execute("aca_vfx2 md_near_eye on");check(MdNearEyeClient.enabled()&&ClassicHudConfig.SPEC.saves==1,"ON command failed to save enabled config");
  e.dispatcher.execute("aca_vfx2 md_near_eye off");check(!MdNearEyeClient.enabled()&&ClassicHudConfig.SPEC.saves==2,"OFF command failed to save disabled config");
  e.dispatcher.execute("aca_vfx2 md_near_eye status");check(ClassicHudConfig.SPEC.saves==2,"status mutated config");
  ClassicHudConfig.SPEC.loaded=false;check(e.dispatcher.execute("aca_vfx2 md_near_eye on")==0&&!MdNearEyeClient.enabled()&&ClassicHudConfig.SPEC.saves==2,"unloaded config command changed state");
  System.out.println("PASS|default-off|on-off-save-config|status-read-only|unloaded-config-classic");
 }
 public static void main(String[] args)throws Exception{mode=args[0];if(mode.equals("commands")){commands();return;}if(mode.equals("reload")){reload();return;}configure();geometry();exclusions();lifecycle();if(mode.equals("selected"))check(selected>0,"vacuous selected test");}
}
'''


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--joml', type=Path)
    parser.add_argument('--pinned-minecraft-sources', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    baseline = (root / FIXTURE).read_text()
    current = (root / (JAVA + EFFECTS)).read_text()
    origin = guards(root, baseline)
    old = module(root, 'scripts/verify-light-shield-vfx2-integration.py', 'md_recording_hosts')
    plasma = module(root, 'scripts/verify-plasma-vfx2-integration.py', 'md_dependency_locator')
    toolchain = old.jdk(root)
    joml = plasma.find_joml(root, old, args.joml)
    archive = args.pinned_minecraft_sources
    if archive is None:
        archive = next((p for p in [root / 'build/moddev/artifacts/neoforge-21.1.252-sources.jar', root.parent / 'clean-loader/build/moddev/artifacts/neoforge-21.1.252-sources.jar'] if p.is_file()), None)
    assert archive is not None, 'pinned Minecraft render-state source archive required'
    with ZipFile(archive) as jar:
        native_type = jar.read('net/minecraft/client/renderer/RenderType.java')
        native_state = jar.read('net/minecraft/client/renderer/RenderStateShard.java')
    assert digest(native_type) == old.NATIVE_RENDER_TYPE_SHA256 and digest(native_state) == old.NATIVE_RENDER_STATE_SHA256, 'pinned native state declarations changed'
    modes = ['off', 'forced', 'forced-global', 'glow-compile-failure', 'core-compile-failure',
             'missing-uniform', 'inactive-uniform', 'unlinked', 'closed-shader', 'closed-glow', 'closed-core', 'delete-pending-glow', 'delete-pending-core', 'reload-pending', 'config-unloaded',
             'second-registration-failure', 'reload-before-flush', 'forced-before-flush', 'setup-failure', 'selected']
    sources = {name: (root / (JAVA + name)).read_text() for name in PRODUCTION}
    mutations = []
    with tempfile.TemporaryDirectory(prefix='md-near-eye-integration-') as directory:
        work = Path(directory)
        common = hosts(old)
        common['oracle/MdNearEyeOracle.java'] = HARNESS
        traces = {}
        for label, effects in [('baseline', baseline), ('current', current)]:
            generated, classes = work / label / 'sources', work / label / 'classes'
            all_sources = dict(common)
            all_sources.update({'cn/academy/port/' + name: value for name, value in sources.items()})
            all_sources['cn/academy/port/' + EFFECTS] = effects
            for name, value in all_sources.items():
                path = generated / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text(value)
            run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', joml,
                 '-d', classes, *sorted(generated.rglob('*.java'))])
            launch = [toolchain / 'bin/java', '-Xmx512m', '-Djava.awt.headless=true',
                      '-Doracle.root=' + str(root), '-cp', str(classes) + os.pathsep + str(joml), 'oracle.MdNearEyeOracle']
            for mode in ['baseline'] if label == 'baseline' else modes:
                traces[mode] = run([*launch, mode]).strip().splitlines()
                if label == 'current':
                    assert traces[mode] == traces['baseline'], 'compiled .53 differential failed: ' + mode + '\n' + '\n'.join(a+' != '+b for a,b in zip(traces['baseline'],traces[mode]) if a!=b)[:3000]
            if label == 'current':
                reload_result = run([*launch, 'reload']).strip()
                assert reload_result.startswith('PASS|'), reload_result
                command_result = run([*launch, 'commands']).strip()
                assert command_result.startswith('PASS|'), command_result
                target = generated / ('cn/academy/port/' + EFFECTS)
                cases = [('classic-alpha-drift', '230 / 255.0 * alpha', '229 / 255.0 * alpha'),
                         ('classic-uv-drift', 'vec(frame.apply(vertex.point())), vertex.u(), vertex.v(), r, g, b, alpha', 'vec(frame.apply(vertex.point())), vertex.u() + .01F, vertex.v(), r, g, b, alpha'),
                         ('extra-rng-consumption', 'long age = CLOCK.elapsed() - ray.created;', 'RANDOM.nextDouble(); long age = CLOCK.elapsed() - ray.created;')]
                for name, before, after in cases:
                    assert current.count(before) == 1, 'mutation target changed: ' + name
                    target.write_text(current.replace(before, after))
                    run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', str(classes) + os.pathsep + str(joml), '-d', classes, target])
                    mutant = run([*launch, 'off']).strip().splitlines()
                    assert mutant != traces['baseline'], 'oracle did not kill mutation: ' + name
                    mutations.append(name)
                for name, before, after, mode in [
                    ('reflected-ray-scope-drift', 'ray.directMain && ray.spec == MD', 'ray.spec == MD', 'selected'),
                    ('inverted-config-selection', 'currentCamera && MdNearEyeClient.enabled()', 'currentCamera && !MdNearEyeClient.enabled()', 'off')]:
                    assert current.count(before) == 1, 'mutation target changed: ' + name
                    target.write_text(current.replace(before, after))
                    run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', str(classes) + os.pathsep + str(joml), '-d', classes, target])
                    mutant = subprocess.run([str(x) for x in [*launch, mode]], text=True, capture_output=True)
                    assert mutant.returncode != 0 or mutant.stdout.strip().splitlines() != traces['baseline'], 'oracle did not kill mutation: ' + name
                    mutations.append(name)
    assert len(traces['baseline']) > 400 and any(int(s.split('|')[1]) >= 876 for s in traces['baseline']), 'vacuous differential'
    report = {'result': 'PASS', 'baselineCommit': BASELINE_COMMIT, 'baselineSha256': BASELINE_SHA256,
              'baselineOrigin': origin, 'unmodifiedProductionCompiled': PRODUCTION,
              'nativeJoml': {'version': '1.10.5', 'sha256': digest(joml.read_bytes())},
              'framesPerMode': len(traces['baseline']), 'classicDifferentialModes': modes,
              'traceSha256': digest('\n'.join(traces['baseline'])), 'reloadChecks': reload_result.split('|')[1:], 'commandChecks': command_result.split('|')[1:],
              'compiledMutationsKilled': mutations, 'immutableOriginalHashes': PINNED,
              'productionSourceHashes': {JAVA + name: digest(value) for name, value in sources.items()},
              'comparison': ['raw/transformed vertex coordinate float bits, full pose matrices, UV/RGBA and order',
                             'classic render-state declarations, texture, cull, flush order and complete batch submissions',
                             'serialized production Random state, wiggle state, pause clock, ray/context/particle lifecycle and sound counts'],
              'limitations': ['Recording Minecraft/resource/GL hosts, not a native Minecraft or GPU run',
                              'Pinned native JOML arithmetic; render-state declarations/defaults checked against pinned native sources',
                              'Shared-buffer completion fencing is modeled; native BufferSource and driver state not executed',
                              'Classic cutoff getter is an explicitly named host; cutoff GLSL pinned, not executed',
                              'Selected mode normalizes only material names and corresponding shader identity; geometry and all other recorded state remain exact',
                              'No pixel comparison, GLSL execution, performance or visual-quality acceptance claim; gameplay/damage not simulated']}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print('PASS: Meltdowner .53 compiled differential: '+str(len(traces['baseline']))+' frames x '+str(len(modes))+' modes; exact vertices/state/RNG and reload boundary')


if __name__ == '__main__':
    main()
