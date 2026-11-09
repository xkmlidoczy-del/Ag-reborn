#!/usr/bin/env python3
"""Compile actual Railgun VFX2 and ClassicEffects against recording client adapters.

This is an integration oracle, not a GPU or native NeoForge test. Production Java
is compiled unchanged; adapters supply a controlled client clock/thread queue,
frustum, resource failures, deferred shader callbacks and recorded vertices/state.
Classic vertices are compared with the immutable pre-slice Git source. MEDIUM's
approved nearest-four inner materials are normalized only for material and the
explicit U phase, preserving positions/colors/count/order. The actual pinned
getBuffer body drives shared-buffer fences. Mutation controls reject regressions.
An existing offline runtime classpath enables actual NeoForge enum correction and
Night Config TOML checks. Native game file recovery still requires a client test.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import tempfile
from zipfile import ZipFile

BASELINE = 'ec0c40bab302b878f3cf14df0540f83f61c184c7'


def run(command, cwd=None):
    result = subprocess.run([str(x) for x in command], cwd=cwd, text=True, capture_output=True)
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    return result.stdout


def jdk(root):
    homes = [Path(os.environ['JAVA_HOME'])] if os.environ.get('JAVA_HOME') else []
    if shutil.which('javac'):
        homes.append(Path(shutil.which('javac')).resolve().parent.parent)
    for parent in [root, *root.parents]:
        homes.extend(sorted((parent / 'aca-tools').glob('jdk*')))
        homes.extend(sorted((parent / '.tools').glob('jdk*')))
    for home in homes:
        if (home / 'bin/javac').is_file() and (home / 'bin/java').is_file():
            return home
    raise AssertionError('A JDK 21 installation is required; set JAVA_HOME')


def block(source, marker):
    start = source.index(marker)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


STUBS = {
    'org/joml/Matrix4f.java': 'package org.joml; public class Matrix4f {}',
    'net/neoforged/api/distmarker/Dist.java': 'package net.neoforged.api.distmarker; public enum Dist {CLIENT, DEDICATED_SERVER}',
    'net/neoforged/bus/api/SubscribeEvent.java': '''package net.neoforged.bus.api;
        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
        public @interface SubscribeEvent {}''',
    'net/neoforged/fml/common/EventBusSubscriber.java': '''package net.neoforged.fml.common;
        @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
        public @interface EventBusSubscriber { String modid(); net.neoforged.api.distmarker.Dist[] value();
        Bus bus() default Bus.GAME; enum Bus {MOD, GAME} }''',
    'net/minecraft/Util.java': 'package net.minecraft; public class Util {public static long now; public static long getMillis(){return now;}}',
    'net/minecraft/util/Mth.java': '''package net.minecraft.util; public class Mth {
        public static float sin(float x){return (float)Math.sin(x);} public static float cos(float x){return (float)Math.cos(x);}}''',
    'net/minecraft/world/phys/Vec3.java': '''package net.minecraft.world.phys; public class Vec3 {
        public static final Vec3 ZERO=new Vec3(0,0,0); public final double x,y,z;
        public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
        public Vec3 add(Vec3 p){return new Vec3(x+p.x,y+p.y,z+p.z);} public Vec3 add(double a,double b,double c){return add(new Vec3(a,b,c));}
        public Vec3 subtract(Vec3 p){return new Vec3(x-p.x,y-p.y,z-p.z);} public Vec3 scale(double s){return new Vec3(x*s,y*s,z*s);}
        public double dot(Vec3 p){return x*p.x+y*p.y+z*p.z;} public double lengthSqr(){return dot(this);}
        public Vec3 cross(Vec3 p){return new Vec3(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}
        public Vec3 normalize(){double n=Math.sqrt(lengthSqr());return n<1e-4?ZERO:scale(1/n);}
        public double distanceToSqr(Vec3 p){return subtract(p).lengthSqr();}
        public Vec3 xRot(float a){double c=Math.cos(a),s=Math.sin(a);return new Vec3(x,y*c+z*s,z*c-y*s);}
        public Vec3 yRot(float a){double c=Math.cos(a),s=Math.sin(a);return new Vec3(x*c+z*s,y,z*c-x*s);}
        public Vec3 zRot(float a){double c=Math.cos(a),s=Math.sin(a);return new Vec3(x*c+y*s,y*c-x*s,z);}
    }''',
    'net/minecraft/world/phys/AABB.java': '''package net.minecraft.world.phys; public class AABB {
        public final double minY,maxY; public AABB(Vec3 a,Vec3 b){minY=Math.min(a.y,b.y);maxY=Math.max(a.y,b.y);}
        private AABB(double a,double b){minY=a;maxY=b;} public AABB inflate(double x){return new AABB(minY-x,maxY+x);}}''',
    'net/minecraft/resources/ResourceLocation.java': '''package net.minecraft.resources;
        public record ResourceLocation(String namespace,String path){public static ResourceLocation fromNamespaceAndPath(String n,String p){return new ResourceLocation(n,p);}
        public String getPath(){return path;} public String toString(){return namespace+":"+path;}}''',
    'net/minecraft/world/InteractionHand.java': 'package net.minecraft.world; public enum InteractionHand {MAIN_HAND,OFF_HAND}',
    'net/minecraft/world/entity/HumanoidArm.java': 'package net.minecraft.world.entity; public enum HumanoidArm {LEFT,RIGHT}',
    'net/minecraft/world/entity/Entity.java': '''package net.minecraft.world.entity; public class Entity {
        public boolean alive=true,removed,spectator;public boolean isAlive(){return alive;}public boolean isRemoved(){return removed;}public boolean isSpectator(){return spectator;}
        public net.minecraft.world.phys.Vec3 getEyePosition(float t){return new net.minecraft.world.phys.Vec3(0,2,0);}
        public net.minecraft.world.phys.Vec3 getViewVector(float t){return new net.minecraft.world.phys.Vec3(1,0,0);}
        public int getId(){return 1;} public HumanoidArm getMainArm(){return HumanoidArm.RIGHT;}
        public java.util.List<net.minecraft.network.chat.Component> messages=new java.util.ArrayList<>();
        public void displayClientMessage(net.minecraft.network.chat.Component c,boolean actionbar){messages.add(c);}}''',
    'net/minecraft/client/multiplayer/ClientLevel.java': '''package net.minecraft.client.multiplayer;
        public class ClientLevel { public net.minecraft.world.entity.Entity getEntity(int id){return null;}}''',
    'net/minecraft/client/Minecraft.java': '''package net.minecraft.client; public class Minecraft {
        private static final Minecraft INSTANCE=new Minecraft(); public static Minecraft getInstance(){return INSTANCE;}
        public net.minecraft.client.multiplayer.ClientLevel level; public net.minecraft.world.entity.Entity player=new net.minecraft.world.entity.Entity();
        public boolean paused; public boolean isPaused(){return paused;}
        public boolean singleplayer=true,windowActive=true;public Object screen,connection=new Object();public net.minecraft.world.entity.Entity cameraEntity;
        public boolean hasSingleplayerServer(){return singleplayer;}public Object getConnection(){return connection;}public boolean isWindowActive(){return windowActive;}
        public net.minecraft.world.entity.Entity getCameraEntity(){return cameraEntity==null?player:cameraEntity;}
        public final Font font=new Font();public static class Font {public int width(String text){return text.length()*6;}}
        public final java.util.List<Runnable> queue=new java.util.ArrayList<>(); public void execute(Runnable r){queue.add(r);}
        public void drain(){var copy=java.util.List.copyOf(queue);queue.clear();copy.forEach(Runnable::run);}
        public final Options options=new Options(); public static class Options {public boolean hideGui;public CameraType getCameraType(){return new CameraType();}}
        public static class CameraType {public boolean isFirstPerson(){return true;}}}''',
    'net/minecraft/network/chat/Component.java': '''package net.minecraft.network.chat;
        public record Component(String key,Object[] args){public static Component translatable(String key,Object...args){return new Component(key,args);}}''',
    'net/minecraft/commands/Commands.java': '''package net.minecraft.commands; public class Commands {
        public static Node literal(String name){return new Node(name);} public static class Node {
        public final String name; public java.util.function.ToIntFunction<Object> callback; public final java.util.List<Node> children=new java.util.ArrayList<>();
        public Node(String name){this.name=name;} public Node executes(java.util.function.ToIntFunction<Object> f){callback=f;return this;}
        public Node then(Node n){children.add(n);return this;}}
        public static class Dispatcher {public Node root;public void register(Node n){root=n;}
        public int execute(String command){var words=command.split(" ");if(root==null||!root.name.equals(words[0]))throw new IllegalArgumentException(command);
        Node n=root;if(words.length>1)n=root.children.stream().filter(c->c.name.equals(words[1])).findFirst().orElseThrow(()->new IllegalArgumentException(command));
        return n.callback.applyAsInt(null);}}}''',
    'net/neoforged/neoforge/client/event/RegisterClientCommandsEvent.java': '''package net.neoforged.neoforge.client.event;
        public class RegisterClientCommandsEvent {public final net.minecraft.commands.Commands.Dispatcher dispatcher=new net.minecraft.commands.Commands.Dispatcher();
        public net.minecraft.commands.Commands.Dispatcher getDispatcher(){return dispatcher;}}''',
    'net/neoforged/neoforge/client/event/ClientTickEvent.java': 'package net.neoforged.neoforge.client.event; public class ClientTickEvent {public static class Post {}}',
    'cn/academy/port/client/AcademyClient.java': '''package cn.academy.port.client;public class AcademyClient {
        public static final State state=new State();public static long epoch=1;public static long inputSessionEpoch(){return epoch;}public static boolean sameInputSessionEpoch(long value){return epoch==value;}
        public static class State {public String category="electromaster";public final Presets presets=new Presets();}
        public static class Presets {public int current;public long revision;public int current(){return current;}public long revision(){return revision;}}}''',
    'org/lwjgl/glfw/GLFW.java': '''package org.lwjgl.glfw;public class GLFW {public static final int GLFW_PRESS=1,GLFW_KEY_F8=297,GLFW_KEY_F9=298,GLFW_KEY_F10=299;}''',
    'net/neoforged/neoforge/client/event/InputEvent.java': '''package net.neoforged.neoforge.client.event;public class InputEvent {public record Key(int key,int action){public int getKey(){return key;}public int getAction(){return action;}}}''',
    'net/neoforged/neoforge/client/event/RenderGuiEvent.java': '''package net.neoforged.neoforge.client.event;public class RenderGuiEvent {
        public static class Post {public final Graphics graphics=new Graphics();public Graphics getGuiGraphics(){return graphics;}}
        public static class Graphics {public final java.util.List<String> labels=new java.util.ArrayList<>();public void fill(int a,int b,int c,int d,int color){}
        public void drawString(net.minecraft.client.Minecraft.Font font,String text,int x,int y,int color,boolean shadow){labels.add(text);}}}''',
    'com/mojang/blaze3d/vertex/PoseStack.java': '''package com.mojang.blaze3d.vertex; public class PoseStack {
        public int depth; final org.joml.Matrix4f matrix=new org.joml.Matrix4f();public void pushPose(){depth++;}public void popPose(){depth--;}
        public Pose last(){return new Pose(matrix);}public record Pose(org.joml.Matrix4f pose){}
        public void translate(double x,double y,double z){} public void mulPose(Object rotation){} public void scale(float x,float y,float z){}}''',
    'net/neoforged/neoforge/client/event/RenderLevelStageEvent.java': '''package net.neoforged.neoforge.client.event;
        public class RenderLevelStageEvent {public enum Stage {AFTER_PARTICLES,AFTER_ENTITIES}
        public Stage stage=Stage.AFTER_PARTICLES;public final com.mojang.blaze3d.vertex.PoseStack poses=new com.mojang.blaze3d.vertex.PoseStack();
        public net.minecraft.world.phys.Vec3 camera=new net.minecraft.world.phys.Vec3(0,2,0);public final Frustum frustum=new Frustum();
        public Stage getStage(){return stage;}public com.mojang.blaze3d.vertex.PoseStack getPoseStack(){return poses;}
        public Camera getCamera(){return new Camera(camera);}public record Camera(net.minecraft.world.phys.Vec3 getPosition){public Object rotation(){return null;}}
        public Frustum getFrustum(){return frustum;}public static class Frustum {public double maxY=Double.POSITIVE_INFINITY;public boolean isVisible(net.minecraft.world.phys.AABB b){return b.minY<maxY;}}
        public PartialTick getPartialTick(){return new PartialTick();}public static class PartialTick {public float getGameTimeDeltaPartialTick(boolean b){return 0;}}}''',
    'net/neoforged/neoforge/client/event/RenderHandEvent.java': '''package net.neoforged.neoforge.client.event;public class RenderHandEvent {
        public net.minecraft.world.InteractionHand getHand(){return net.minecraft.world.InteractionHand.MAIN_HAND;}
        public com.mojang.blaze3d.vertex.PoseStack getPoseStack(){return new com.mojang.blaze3d.vertex.PoseStack();}
        public net.minecraft.client.renderer.MultiBufferSource getMultiBufferSource(){return new net.minecraft.client.renderer.MultiBufferSource.BufferSource();}}''',
    'com/mojang/blaze3d/vertex/ByteBufferBuilder.java': 'package com.mojang.blaze3d.vertex;public class ByteBufferBuilder {public ByteBufferBuilder(int size){}}',
    'oracle/Recording.java': '''package oracle; public class Recording {
        public static final java.util.List<Vertex> vertices=new java.util.ArrayList<>();public static final java.util.List<String> flushes=new java.util.ArrayList<>();
        public static final java.util.List<String> batchEnds=new java.util.ArrayList<>();
        public static org.joml.Matrix4f currentMatrix;
        public static void clear(){vertices.clear();flushes.clear();batchEnds.clear();}
        public static class Vertex {public final String type;public final org.joml.Matrix4f matrix;public final float x,y,z;public float u,v,nx,ny,nz;public int r,g,b,a,u1,v1,u2,v2;
        public Vertex(String type,org.joml.Matrix4f m,float x,float y,float z){this.type=type;matrix=m;this.x=x;this.y=y;this.z=z;}
        public String signature(){return type+"|"+x+"|"+y+"|"+z+"|"+u+"|"+v+"|"+r+"|"+g+"|"+b+"|"+a;}}}''',
    'com/mojang/blaze3d/vertex/VertexConsumer.java': '''package com.mojang.blaze3d.vertex;public interface VertexConsumer {
        VertexConsumer addVertex(float x,float y,float z);VertexConsumer setUv(float u,float v);VertexConsumer setColor(int r,int g,int b,int a);
        VertexConsumer setUv1(int u,int v);VertexConsumer setUv2(int u,int v);VertexConsumer setNormal(float x,float y,float z);
        default VertexConsumer addVertex(org.joml.Matrix4f matrix,float x,float y,float z){oracle.Recording.currentMatrix=matrix;return addVertex(x,y,z);}}
    ''',
    'com/mojang/blaze3d/vertex/RecordingConsumer.java': '''package com.mojang.blaze3d.vertex;public class RecordingConsumer implements VertexConsumer {
        final String type;oracle.Recording.Vertex last;public boolean ended;public int emitted;public RecordingConsumer(String type){this.type=type;}
        public void check(){if(ended)throw new IllegalStateException("writing ended shared RenderType buffer");}public void end(){ended=true;}
        public VertexConsumer addVertex(float x,float y,float z){check();emitted++;last=new oracle.Recording.Vertex(type,oracle.Recording.currentMatrix,x,y,z);oracle.Recording.vertices.add(last);return this;}
        public VertexConsumer setUv(float u,float v){check();last.u=u;last.v=v;return this;}public VertexConsumer setColor(int r,int g,int b,int a){check();last.r=r;last.g=g;last.b=b;last.a=a;return this;}
        public VertexConsumer setUv1(int u,int v){check();last.u1=u;last.v1=v;return this;}public VertexConsumer setUv2(int u,int v){check();last.u2=u;last.v2=v;return this;}
        public VertexConsumer setNormal(float x,float y,float z){check();last.nx=x;last.ny=y;last.nz=z;return this;}}''',
    'com/mojang/blaze3d/vertex/BufferBuilder.java': '''package com.mojang.blaze3d.vertex;public class BufferBuilder extends RecordingConsumer {
        public BufferBuilder(ByteBufferBuilder bytes,VertexFormat.Mode mode,Object format){super(net.minecraft.client.renderer.RenderType.constructing.name);}}''',
    'net/minecraft/client/renderer/MultiBufferSource.java': '''package net.minecraft.client.renderer; import com.mojang.blaze3d.vertex.*;public interface MultiBufferSource {
        com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType type);
        static BufferSource immediate(com.mojang.blaze3d.vertex.ByteBufferBuilder b){return new BufferSource();}
        class BufferSource implements MultiBufferSource {
        final java.util.Map<RenderType,BufferBuilder> startedBuilders=new java.util.HashMap<>();final java.util.Map<RenderType,ByteBufferBuilder> fixedBuffers=java.util.Map.of();
        final ByteBufferBuilder sharedBuffer=new ByteBufferBuilder(65536);RenderType lastSharedType;boolean acquiring;
        public VertexConsumer getBuffer(RenderType type){acquiring=true;try{return getPinnedBuffer(type);}finally{acquiring=false;}}
        /*PINNED_GET_BUFFER*/
        public void endBatch(RenderType type){if(!acquiring)oracle.Recording.flushes.add(type.name);var builder=startedBuilders.remove(type);if(builder!=null)endBatch(type,builder);}
        private void endBatch(RenderType type,BufferBuilder builder){builder.end();if(builder.emitted>0)oracle.Recording.batchEnds.add(type.name);if(type.equals(lastSharedType))lastSharedType=null;}
        }}''',
    'cn/academy/port/client/ClassicRenderTypes.java': '''package cn.academy.port.client; public class ClassicRenderTypes {
        static final java.util.Map<String,net.minecraft.client.renderer.RenderType> types=new java.util.HashMap<>();
        public static net.minecraft.client.renderer.RenderType world(net.minecraft.resources.ResourceLocation t){return types.computeIfAbsent(t.toString(),net.minecraft.client.renderer.RenderType::new);}
        public static net.minecraft.client.renderer.RenderType hand(net.minecraft.resources.ResourceLocation t){return world(t);}}''',
    'com/mojang/blaze3d/vertex/DefaultVertexFormat.java': 'package com.mojang.blaze3d.vertex; public class DefaultVertexFormat {public static final Object POSITION_TEX_COLOR=new Object();}',
    'com/mojang/blaze3d/vertex/VertexFormat.java': 'package com.mojang.blaze3d.vertex;public class VertexFormat {public enum Mode {QUADS}}',
    'com/mojang/blaze3d/platform/GlStateManager.java': '''package com.mojang.blaze3d.platform;public class GlStateManager {
        public enum SourceFactor {SRC_ALPHA,ZERO}public enum DestFactor {ONE}}''',
    'com/mojang/blaze3d/systems/RenderSystem.java': '''package com.mojang.blaze3d.systems;public class RenderSystem {
        public static boolean blend;public static Object[] factors;public static int defaults;
        public static void enableBlend(){blend=true;}public static void disableBlend(){blend=false;}public static void defaultBlendFunc(){defaults++;}
        public static void blendFuncSeparate(Object a,Object b,Object c,Object d){factors=new Object[]{a,b,c,d};}}''',
    'net/minecraft/client/renderer/RenderType.java': '''package net.minecraft.client.renderer;public class RenderType {
        public final String name;public CompositeState state;public RenderType(String name){this.name=name;}
        public static RenderType constructing;public boolean canConsolidateConsecutiveGeometry(){return true;}
        public com.mojang.blaze3d.vertex.VertexFormat.Mode mode(){constructing=this;return com.mojang.blaze3d.vertex.VertexFormat.Mode.QUADS;}
        public Object format(){return com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR;}
        public RenderType(String name,Object f,com.mojang.blaze3d.vertex.VertexFormat.Mode m,int n,boolean a,boolean b,Runnable c,Runnable d){this(name);}
        protected static final Object LEQUAL_DEPTH_TEST="LEQUAL",COLOR_WRITE="COLOR",NO_CULL="NO_CULL",NO_LIGHTMAP="NO_LIGHTMAP",NO_OVERLAY="NO_OVERLAY",PARTICLES_TARGET="PARTICLES";
        public static final TransparencyStateShard TRANSLUCENT_TRANSPARENCY=new TransparencyStateShard("translucent",()->{},()->{});
        public static RenderType create(String name,Object format,com.mojang.blaze3d.vertex.VertexFormat.Mode mode,int n,boolean a,boolean b,CompositeState state){var t=new RenderType(name);t.state=state;return t;}
        public static class TransparencyStateShard {public final Runnable setup,clear;public TransparencyStateShard(String name,Runnable setup,Runnable clear){this.setup=setup;this.clear=clear;}}
        public record ShaderStateShard(java.util.function.Supplier<ShaderInstance> shader){}
        public record TextureStateShard(net.minecraft.resources.ResourceLocation texture,boolean blur,boolean mipmap){}
        public static class CompositeState {public Object depth,mask,cull,lightmap,overlay,target;public ShaderStateShard shader;public TextureStateShard texture;public TransparencyStateShard transparency;
        public static Builder builder(){return new Builder();}public static class Builder {final CompositeState s=new CompositeState();
        public Builder setShaderState(ShaderStateShard a){s.shader=a;return this;}public Builder setTextureState(TextureStateShard a){s.texture=a;return this;}
        public Builder setTransparencyState(TransparencyStateShard a){s.transparency=a;return this;}public Builder setDepthTestState(Object a){s.depth=a;return this;}
        public Builder setWriteMaskState(Object a){s.mask=a;return this;}public Builder setCullState(Object a){s.cull=a;return this;}
        public Builder setLightmapState(Object a){s.lightmap=a;return this;}public Builder setOverlayState(Object a){s.overlay=a;return this;}
        public Builder setOutputState(Object a){s.target=a;return this;}public CompositeState createCompositeState(boolean b){return s;}}}}''',
    'net/minecraft/client/renderer/ShaderInstance.java': '''package net.minecraft.client.renderer;public class ShaderInstance implements AutoCloseable {
        public static boolean fail,failCore;public boolean closed;public final String name;public ShaderInstance(net.minecraft.server.packs.resources.ResourceProvider p,net.minecraft.resources.ResourceLocation r,Object f)throws java.io.IOException {
        name=r.getPath();if(fail||failCore&&name.equals("railgun_vfx2_core"))throw new java.io.IOException("injected shader failure");}
        public ShaderInstance(){name="fallback";}public int getId(){return name.equals("railgun_vfx2_core")?18:17;}public void close(){closed=true;}}''',
    'net/minecraft/client/renderer/GameRenderer.java': '''package net.minecraft.client.renderer;public class GameRenderer {
        public static final ShaderInstance FALLBACK=new ShaderInstance();public static ShaderInstance getPositionTexColorShader(){return FALLBACK;}}''',
    'org/lwjgl/opengl/GL20.java': '''package org.lwjgl.opengl;public class GL20 {public static final int GL_LINK_STATUS=35714;public static int linked=1,failedId=-1;
        public static int glGetProgrami(int id,int what){return id==failedId?0:linked;}public static String glGetProgramInfoLog(int id){return "injected link failure";}}''',
    'com/mojang/logging/LogUtils.java': '''package com.mojang.logging;public class LogUtils {public static Logger getLogger(){return new Logger();}
        public static class Logger {public void warn(String s,Object...args){}public void info(String s,Object...args){}}}''',
    'net/minecraft/server/packs/resources/ResourceProvider.java': 'package net.minecraft.server.packs.resources;public interface ResourceProvider {}',
    'net/minecraft/server/packs/resources/ResourceManager.java': '''package net.minecraft.server.packs.resources;public interface ResourceManager extends ResourceProvider {
        Resource getResourceOrThrow(net.minecraft.resources.ResourceLocation location)throws java.io.IOException;
        record Resource(byte[] data){public java.io.InputStream open(){return new java.io.ByteArrayInputStream(data);}}}''',
    'net/minecraft/server/packs/resources/ResourceManagerReloadListener.java': '''package net.minecraft.server.packs.resources;
        public interface ResourceManagerReloadListener {void onResourceManagerReload(ResourceManager resources);}''',
    'net/neoforged/neoforge/client/event/RegisterClientReloadListenersEvent.java': '''package net.neoforged.neoforge.client.event;
        public class RegisterClientReloadListenersEvent {public net.minecraft.server.packs.resources.ResourceManagerReloadListener listener;
        public void registerReloadListener(net.minecraft.server.packs.resources.ResourceManagerReloadListener l){listener=l;}}''',
    'net/neoforged/neoforge/client/event/RegisterShadersEvent.java': '''package net.neoforged.neoforge.client.event;
        public class RegisterShadersEvent {public net.minecraft.client.renderer.ShaderInstance candidate;public java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance> callback;
        public record Registration(net.minecraft.client.renderer.ShaderInstance candidate,java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance> callback){}
        public final java.util.List<Registration> registrations=new java.util.ArrayList<>();
        public net.minecraft.server.packs.resources.ResourceProvider getResourceProvider(){return null;}
        public void registerShader(net.minecraft.client.renderer.ShaderInstance candidate,java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance> callback){this.candidate=candidate;this.callback=callback;registrations.add(new Registration(candidate,callback));}
        public void complete(){for(var registration:registrations)registration.callback.accept(registration.candidate);}
        public void complete(String name){var r=registrations.stream().filter(x->x.candidate.name.equals(name)).findFirst().orElseThrow();r.callback.accept(r.candidate);}}''',
    'com/mojang/blaze3d/platform/NativeImage.java': '''package com.mojang.blaze3d.platform;public class NativeImage implements AutoCloseable {
        final java.awt.image.BufferedImage image;private NativeImage(java.awt.image.BufferedImage image){this.image=image;}
        public static NativeImage read(java.io.InputStream in)throws java.io.IOException {var image=javax.imageio.ImageIO.read(in);if(image==null)throw new java.io.IOException("not a PNG");return new NativeImage(image);}
        public int getWidth(){return image.getWidth();}public int getHeight(){return image.getHeight();}public void close(){}}''',
    'net/neoforged/neoforge/common/ModConfigSpec.java': '''package net.neoforged.neoforge.common;public class ModConfigSpec {
        public boolean loaded=true;public int saves;public boolean isLoaded(){return loaded;}public void save(){saves++;}
        public static class ConfigValue<T>{T value;ConfigValue(T value){this.value=value;}public T get(){return value;}public void set(T value){this.value=value;}}
        public static class DoubleValue extends ConfigValue<Double>{DoubleValue(double d){super(d);}}
        public static class BooleanValue extends ConfigValue<Boolean>{BooleanValue(boolean b){super(b);}}
        public static class EnumValue<E extends Enum<E>> extends ConfigValue<E>{EnumValue(E e){super(e);}}
        public static class Builder {public Builder push(String name){return this;}public Builder pop(){return this;}public Builder comment(String...text){return this;}
        public DoubleValue defineInRange(String n,double d,double a,double b){return new DoubleValue(d);}public BooleanValue define(String n,boolean b){return new BooleanValue(b);}
        public ConfigValue<String> define(String n,String d){return new ConfigValue<>(d);}public <E extends Enum<E>>EnumValue<E> defineEnum(String n,E e){return new EnumValue<>(e);}
        public ModConfigSpec build(){return new ModConfigSpec();}}}''',
}

HARNESS = r'''
package oracle;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.ClassicHudConfig.Vfx2Quality;
import cn.academy.port.client.ClassicEffects;
import cn.academy.port.client.ClassicEffectsBaseline;
import cn.academy.port.client.AcademyClient;
import cn.academy.port.client.vfx2.*;
import cn.academy.port.client.vfx2.RailgunCoreCameraSafety.Point;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.lang.reflect.*;
import java.util.*;

public class RailgunVfx2IntegrationOracle {
    static int assertions, groups;
    static final Minecraft MC=Minecraft.getInstance();
    static final Vec3 CAMERA=new Vec3(0,2,0), AXIS=new Vec3(1,0,0);
    static void yes(boolean result,String why){assertions++;if(!result)throw new AssertionError(why);}
    static Field field(Class<?> c,String name)throws Exception {var f=c.getDeclaredField(name);f.setAccessible(true);return f;}
    static Object value(Class<?> c,String name)throws Exception{return field(c,name).get(null);}
    static int count(String name)throws Exception{return (int)value(RailgunVfx2.class,name);}
    static List<?> beams(Class<?> c)throws Exception{return (List<?>)value(c,"BEAMS");}
    static void invoke(Class<?> c,String method,Class<?>[] types,Object...args)throws Exception {c.getMethod(method,types).invoke(null,args);}
    static void tick(Class<?> c)throws Exception{invoke(c,"onClientTick",new Class<?>[]{ClientTickEvent.Post.class},new ClientTickEvent.Post());}
    static void add(Class<?> c,Vec3 origin,Vec3 direction,double length)throws Exception {
        invoke(c,"addRailgun",new Class<?>[]{Vec3.class,Vec3.class,double.class},origin,direction,length);
    }
    static void reset(Class<?> c)throws Exception {
        Util.now=10000;MC.paused=false;MC.queue.clear();MC.level=new ClientLevel();tick(c);Recording.clear();
    }
    static void prepare(Class<?> c,int number,boolean reverse)throws Exception {
        reset(c);
        for(int i=0;i<number;i++)add(c,new Vec3(0,3+(reverse?number-1-i:i),0),AXIS,128);
        MC.drain();
        for(Object beam:beams(c)) {
            ((Random)field(beam.getClass(),"random").get(beam)).setSeed(73);
            // Random classic sparks are a separate historical oracle. Keep this comparison
            // deterministic; baseline guards verify their complete production code unchanged.
            ((List<?>)field(beam.getClass(),"sparks").get(beam)).clear();
        }
        Util.now+=500;
    }
    static List<String> render(Class<?> c,Vfx2Quality quality,RenderLevelStageEvent event)throws Exception {
        ClassicHudConfig.VFX2_QUALITY.set(quality);Recording.clear();
        invoke(c,"onRenderLevel",new Class<?>[]{RenderLevelStageEvent.class},event);
        yes(event.poses.depth==0,"pose stack balanced");
        long age=RailgunVfx2Preview.activeSample()==null?(long)value(c,"clockMillis"):RailgunVfx2Preview.phaseAge();
        return classicValues(age);
    }
    static boolean optional(String name){return name.startsWith("academy_railgun_vfx2_");}
    static boolean inner(String name){return name.equals("academy_railgun_vfx2_inner_core");}
    static List<String> classicValues(long age) {
        return Recording.vertices.stream().filter(v->!optional(v.type)||inner(v.type)).map(v->{
            if(!inner(v.type))return v.signature();
            float original=v.u-age/1800f, source=Math.round(original);
            yes(Math.abs(original-source)<2e-6&&(source==0||source==1)&&(v.v==0||v.v==1),"selected inner core changes only U phase from original zero/one coordinates");
            return "academy:textures/port/white.png|"+v.x+"|"+v.y+"|"+v.z+"|"+source+"|"+v.v+"|"+v.r+"|"+v.g+"|"+v.b+"|"+v.a;
        }).toList();
    }
    static int optionalCount(){return (int)Recording.vertices.stream().filter(v->optional(v.type)).count();}
    static void textures(int mode)throws Exception {
        var event=new RegisterClientReloadListenersEvent();RailgunVfx2Material.resources(event);
        var data=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(mode==2?511:512,64,java.awt.image.BufferedImage.TYPE_INT_ARGB),"PNG",data);
        var noise=new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(mode==6?255:256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB),"PNG",noise);
        event.listener.onResourceManagerReload(location->{
            if(mode==1)throw new java.io.IOException("injected missing resource");
            if(mode==4 && location.equals(RailgunVfx2Material.CORE))throw new java.io.IOException("second resource missing");
            boolean inner=location.equals(RailgunVfx2Material.NOISE);
            if(mode==5&&inner)throw new java.io.IOException("noise missing");
            return new net.minecraft.server.packs.resources.ResourceManager.Resource(mode==3||mode==7&&inner?new byte[]{1,2,3}:inner?noise.toByteArray():data.toByteArray());
        });
    }
    static RegisterShadersEvent shaders(int mode) {
        ShaderInstance.fail=mode==1;org.lwjgl.opengl.GL20.linked=mode==2?0:1;
        if(mode==3)System.setProperty("academy.vfx2.forceFallback","true");else System.clearProperty("academy.vfx2.forceFallback");
        var event=new RegisterShadersEvent();RailgunVfx2Material.shaders(event);
        ShaderInstance.fail=false;org.lwjgl.opengl.GL20.linked=1;System.clearProperty("academy.vfx2.forceFallback");return event;
    }
    static void ready()throws Exception {textures(0);shaders(0).complete();yes(RailgunVfx2Material.available()&&RailgunVfx2Material.coreAvailable(),"successful overlay and inner materials available");}
    static void classicAndSelection()throws Exception {
        groups++;ready();prepare(ClassicEffectsBaseline.class,20,true);
        var baseline=render(ClassicEffectsBaseline.class,Vfx2Quality.OFF,new RenderLevelStageEvent());
        var baselineFlushes=List.copyOf(Recording.flushes);
        var baselineDraws=List.copyOf(Recording.batchEnds);
        yes(baseline.size()==20*(12+2*12*9*4),"baseline keeps boards and both complete 12-sided cores");
        for(var q:Vfx2Quality.values()) {
            prepare(ClassicEffects.class,20,true);var candidate=render(ClassicEffects.class,q,new RenderLevelStageEvent());
            yes(baseline.equals(candidate),"all classic vertex attributes/order match immutable baseline for "+q);
            yes(baselineFlushes.equals(Recording.flushes.stream().filter(t->!optional(t)).toList()),"classic batch order preserved for "+q);
            yes(baselineDraws.equals(Recording.batchEnds.stream().filter(t->!optional(t)).toList()),"relative classic implicit/shared batch draw order preserved for "+q);
            int expected=q==Vfx2Quality.OFF?0:q==Vfx2Quality.LOW?8:16;
            yes(count("drawn")==expected,"only optional closest "+expected+" beams admitted; full cap remains visible");
            yes(count("coreDrawn")== (q==Vfx2Quality.MEDIUM?4:0),"only MEDIUM admits nearest four inner materials");
            yes(count("vertices")==optionalCount(),"all recorded auxiliary vertices counted");
            yes(optionalCount()<=(q==Vfx2Quality.LOW?512:4096),"per-frame auxiliary ceiling "+q);
            var seen=new TreeSet<Integer>();
            boolean beganOptionalFlush=false;
            for(String type:Recording.flushes){if(optional(type))beganOptionalFlush=true;else yes(!beganOptionalFlush,"classic batches flush before auxiliary batches");}
            for(var v:Recording.vertices)if(optional(v.type)&&!inner(v.type)) {
                seen.add(Math.round(v.y+2));
                yes(v.r>=v.g && v.g>=v.b,"auxiliary vertices retain warm tint");
                yes(v.a>=0 && v.a<=41,"auxiliary alpha is a low-energy single vertex fade");
                yes(v.matrix!=null && Float.isFinite(v.x)&&Float.isFinite(v.y)&&Float.isFinite(v.z),"actual emitter finite camera-local vertices");
            }
            yes(seen.size()==expected,"selection consists of distinct existing beams");
            if(expected>0)yes(seen.first()==3 && seen.last()==2+expected,"nearest beams selected while original beam iteration remains reversed");
        }
        prepare(ClassicEffects.class,2,false);var far=new RenderLevelStageEvent();far.camera=new Vec3(0,1000,0);
        render(ClassicEffects.class,Vfx2Quality.MEDIUM,far);yes(optionalCount()==0 && !Recording.vertices.isEmpty(),"distance culls only auxiliary geometry");
        prepare(ClassicEffects.class,2,false);var clipped=new RenderLevelStageEvent();clipped.frustum.maxY=-100;
        render(ClassicEffects.class,Vfx2Quality.MEDIUM,clipped);yes(optionalCount()==0 && !Recording.vertices.isEmpty(),"frustum culls only auxiliary geometry");
        prepare(ClassicEffects.class,1,false);var wrongStage=new RenderLevelStageEvent();wrongStage.stage=RenderLevelStageEvent.Stage.AFTER_ENTITIES;
        render(ClassicEffects.class,Vfx2Quality.MEDIUM,wrongStage);yes(Recording.vertices.isEmpty(),"only AFTER_PARTICLES callback renders");
        prepare(ClassicEffects.class,20,false);for(Object beam:beams(ClassicEffects.class))field(beam.getClass(),"origin").set(beam,new Vec3(0,3,0));
        render(ClassicEffects.class,Vfx2Quality.LOW,new RenderLevelStageEvent());yes(count("drawn")==8,"equal-distance full cap does not disappear");
        ClassicHudConfig.SPEC.loaded=false;render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        yes(optionalCount()==0&&!Recording.vertices.isEmpty(),"unloaded config falls back to complete classic rendering");ClassicHudConfig.SPEC.loaded=true;
    }
    static void admissionAndClock()throws Exception {
        groups++;ready();reset(ClassicEffects.class);
        add(ClassicEffects.class,null,AXIS,1);add(ClassicEffects.class,new Vec3(Double.NaN,0,0),AXIS,1);
        add(ClassicEffects.class,Vec3.ZERO,new Vec3(0,Double.POSITIVE_INFINITY,0),1);
        add(ClassicEffects.class,Vec3.ZERO,Vec3.ZERO,1);add(ClassicEffects.class,Vec3.ZERO,AXIS,Double.NaN);
        add(ClassicEffects.class,Vec3.ZERO,AXIS,0);add(ClassicEffects.class,Vec3.ZERO,AXIS,-1);
        yes(MC.queue.isEmpty()&&beams(ClassicEffects.class).isEmpty(),"invalid callbacks fail before client admission");
        add(ClassicEffects.class,Vec3.ZERO,AXIS,8);yes(beams(ClassicEffects.class).isEmpty(),"Railgun callbacks marshal to client thread");
        MC.level=new ClientLevel();MC.drain();yes(beams(ClassicEffects.class).isEmpty(),"stale-world queued callback rejected");
        MC.level=null;add(ClassicEffects.class,Vec3.ZERO,AXIS,8);MC.drain();yes(beams(ClassicEffects.class).isEmpty(),"disconnected callback rejected");
        reset(ClassicEffects.class);add(ClassicEffects.class,new Vec3(0,3,0),new Vec3(4,0,0),999);MC.drain();
        var beam=beams(ClassicEffects.class).getFirst();
        yes((double)field(beam.getClass(),"length").get(beam)==256,"existing classic accepted range cap retained");
        yes(((Vec3)field(beam.getClass(),"direction").get(beam)).x==1,"existing classic normalized direction retained");
        ((Random)field(beam.getClass(),"random").get(beam)).setSeed(73);((List<?>)field(beam.getClass(),"sparks").get(beam)).clear();
        Util.now+=1000;var first=render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        var firstAux=Recording.vertices.stream().filter(v->optional(v.type)).map(Recording.Vertex::signature).toList();
        long age=(long)value(ClassicEffects.class,"clockMillis");int ticks=(int)field(beam.getClass(),"ticks").get(beam);
        MC.paused=true;Util.now+=100000;tick(ClassicEffects.class);
        var paused=render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        yes(first.equals(paused),"classic vertices freeze with original pause-aware clock");
        yes(firstAux.equals(Recording.vertices.stream().filter(v->optional(v.type)).map(Recording.Vertex::signature).toList()),"auxiliary geometry freezes with classic age");
        yes((long)value(ClassicEffects.class,"clockMillis")==age&&(int)field(beam.getClass(),"ticks").get(beam)==ticks,"pause creates no new ticks/lifetime advancement");
        MC.paused=false;Util.now+=1499;tick(ClassicEffects.class);render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        yes(beams(ClassicEffects.class).size()==1 && optionalCount()>0,"classic and auxiliary remain visible at 2499ms");
        Util.now++;tick(ClassicEffects.class);render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        yes(beams(ClassicEffects.class).isEmpty() && Recording.vertices.isEmpty(),"both layers expire at original 2500ms");
        reset(ClassicEffects.class);for(int i=0;i<70;i++)add(ClassicEffects.class,new Vec3(i,3,0),AXIS,1);MC.drain();
        yes(beams(ClassicEffects.class).size()==64,"original queue remains 64 independent of overlay budget");
        yes(((Vec3)field(beams(ClassicEffects.class).getFirst().getClass(),"origin").get(beams(ClassicEffects.class).getFirst())).x==6,"original oldest-beam eviction retained");
        MC.level=new ClientLevel();tick(ClassicEffects.class);render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        yes(beams(ClassicEffects.class).isEmpty()&&optionalCount()==0,"world replacement clears classic owner and auxiliary admission");
        reset(ClassicEffects.class);add(ClassicEffects.class,new Vec3(0,3,0),AXIS,2);MC.drain();MC.level=null;tick(ClassicEffects.class);
        render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());yes(beams(ClassicEffects.class).isEmpty()&&optionalCount()==0,"disconnect clears both layers");
    }
    static void failureAndGeneration()throws Exception {
        groups++;ready();prepare(ClassicEffectsBaseline.class,1,false);
        var baseline=render(ClassicEffectsBaseline.class,Vfx2Quality.OFF,new RenderLevelStageEvent());
        for(int mode=1;mode<=4;mode++) {
            ready();textures(mode);yes(!RailgunVfx2Material.available(),"texture missing/dimensions/decode/second failure resets readiness "+mode);
            prepare(ClassicEffects.class,1,false);yes(baseline.equals(render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent())),"resource failure preserves complete baseline "+mode);
            yes(optionalCount()==0,"resource failure emits no optional missing-texture geometry "+mode);
        }
        for(int mode=1;mode<=3;mode++) {
            ready();var failed=shaders(mode);yes(!RailgunVfx2Material.available(),"compile/link/forced shader failure disables auxiliary "+mode);
            if(failed.candidate!=null)yes(failed.candidate.closed,"rejected link candidate closed");
            prepare(ClassicEffects.class,1,false);yes(baseline.equals(render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent())),"shader failure preserves complete baseline "+mode);
            yes(optionalCount()==0,"shader failure emits no optional geometry "+mode);
        }
        ready();var old=shaders(0);shaders(1);old.complete();
        yes(!RailgunVfx2Material.available(),"stale shader generation cannot revive failed newer registration");
        var older=shaders(0);var newest=shaders(0);newest.complete();var current=value(RailgunVfx2Material.class,"shader");older.complete();
        yes(value(RailgunVfx2Material.class,"shader")==current,"older callback cannot replace current shader generation");
        ready();textures(1);yes(!RailgunVfx2Material.available(),"failed reload retains no stale texture readiness");
        textures(0);yes(RailgunVfx2Material.available(),"successful resource recovery restores optional layer without a second effects queue");
    }
    static List<String> direct(Vfx2Quality q,Vec3 origin,Vec3 camera,int attempts)throws Exception {
        ClassicHudConfig.VFX2_QUALITY.set(q);RailgunVfx2.beginFrame();Recording.clear();
        var matrix=new org.joml.Matrix4f();var buffers=new MultiBufferSource.BufferSource();var used=new LinkedHashSet<RenderType>();
        for(int i=0;i<attempts;i++)RailgunVfx2.render(origin,AXIS,128,camera,500,1,1,matrix,buffers,used);
        for(var v:Recording.vertices)yes(v.matrix==matrix,"camera subtraction happens once before unchanged pose matrix");
        return Recording.vertices.stream().map(v->v.x+"|"+v.y+"|"+v.z+"|"+v.u+"|"+v.v+"|"+v.r+"|"+v.g+"|"+v.b+"|"+v.a).toList();
    }
    static void frameCapsAndPrecision()throws Exception {
        groups++;ready();
        for(var q:Vfx2Quality.values()) {
            direct(q,new Vec3(.25,4.5,.125),CAMERA,1000);
            int expected=q==Vfx2Quality.OFF?0:q==Vfx2Quality.LOW?8:16;
            yes(count("drawn")==expected,"repeated same-frame submissions cannot exceed instance cap "+q);
            yes(count("vertices")==Recording.vertices.size()&&count("vertices")<=(q==Vfx2Quality.LOW?512:4096),"actual emitted same-frame vertex cap "+q);
            RailgunVfx2.finishFrame();int peak=count("peakVertices");RailgunVfx2.beginFrame();
            yes(count("drawn")==0&&count("coreDrawn")==0&&count("vertices")==0&&count("peakVertices")==peak,"beginFrame resets local counters and preserves peak diagnostics");
        }
        var near=direct(Vfx2Quality.MEDIUM,new Vec3(.25,4.5,.125),new Vec3(-2.25,6.25,3.75),1);
        yes(!near.isEmpty(),"near-coordinate actual renderer emits optional geometry");
        for(double shift:new double[]{8192,-1000000,29999900,-29999900}) {
            var far=direct(Vfx2Quality.MEDIUM,new Vec3(shift+.25,4.5,-shift+.125),new Vec3(shift-2.25,6.25,-shift+3.75),1);
            yes(near.equals(far),"actual emitter translation precision survives world border "+shift);
        }
        double d=RailgunVfx2.distanceSquared(new Vec3(29999900,4,0),AXIS,128,new Vec3(29999964,7,0));
        yes(d==9,"nearest-beam distance measures segment rather than remote origin");
        yes(Double.isInfinite(RailgunVfx2.distanceSquared(null,AXIS,1,CAMERA)),"invalid selection input rejected");
    }
    static void renderStateAndCommands()throws Exception {
        groups++;ready();
        var cls=Class.forName("cn.academy.port.client.vfx2.RailgunVfx2RenderTypes");
        for(String name:List.of("HALO","CORE")) {
            var type=(RenderType)value(cls,name);var state=type.state;
            yes("LEQUAL".equals(state.depth)&&"COLOR".equals(state.mask),"auxiliary material depth tests and writes color only");
            yes("NO_CULL".equals(state.cull)&&"NO_LIGHTMAP".equals(state.lightmap)&&"NO_OVERLAY".equals(state.overlay),"scoped billboard/light states");
            yes("PARTICLES".equals(state.target)&&state.texture.blur()&&!state.texture.mipmap(),"filtered particle target texture states");
            state.transparency.setup.run();
            yes(com.mojang.blaze3d.systems.RenderSystem.blend,"blend enabled during own material");
            yes(Arrays.equals(com.mojang.blaze3d.systems.RenderSystem.factors,new Object[]{com.mojang.blaze3d.platform.GlStateManager.SourceFactor.SRC_ALPHA,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE,com.mojang.blaze3d.platform.GlStateManager.SourceFactor.ZERO,
                com.mojang.blaze3d.platform.GlStateManager.DestFactor.ONE}),"additive source alpha only once and destination alpha untouched");
            int defaults=com.mojang.blaze3d.systems.RenderSystem.defaults;state.transparency.clear.run();
            yes(!com.mojang.blaze3d.systems.RenderSystem.blend&&com.mojang.blaze3d.systems.RenderSystem.defaults==defaults+1,"blend state restored after auxiliary material");
            yes(state.shader.shader().get()==value(RailgunVfx2Material.class,"shader"),"scoped shader supplier follows current generation");
        }
        for(Class<?> c:List.of(RailgunVfx2.class,RailgunVfx2Material.class,ClassicEffects.class)) {
            var annotation=c.getAnnotation(net.neoforged.fml.common.EventBusSubscriber.class);
            yes(annotation!=null&&Arrays.equals(annotation.value(),new net.neoforged.api.distmarker.Dist[]{net.neoforged.api.distmarker.Dist.CLIENT}),"event subscription is client only: "+c.getSimpleName());
        }
        var event=new RegisterClientCommandsEvent();RailgunVfx2.commands(event);
        yes(event.dispatcher.root.children.stream().map(n->n.name).collect(java.util.stream.Collectors.toSet()).equals(Set.of("off","low","medium","status")),"only supported client quality commands exposed");
        int saves=ClassicHudConfig.SPEC.saves;
        for(var q:Vfx2Quality.values()) {
            yes(event.dispatcher.execute("aca_vfx2 "+q.name().toLowerCase(Locale.ROOT))==1,"client command executes "+q);
            yes(ClassicHudConfig.VFX2_QUALITY.get()==q,"client command sets exact enum "+q);
        }
        yes(ClassicHudConfig.SPEC.saves==saves+3,"client quality changes save only client config");
        try{event.dispatcher.execute("aca_vfx2 high");throw new AssertionError("unsupported HIGH command accepted");}catch(IllegalArgumentException expected){}
        ClassicHudConfig.SPEC.loaded=false;yes(event.dispatcher.execute("aca_vfx2 low")==0,"unloaded config command fails safely");
        yes(ClassicHudConfig.SPEC.saves==saves+3,"unloaded command performs no config write");ClassicHudConfig.SPEC.loaded=true;
        int messages=MC.player.messages.size();event.dispatcher.execute("aca_vfx2 status");
        yes(MC.player.messages.size()==messages+1&&MC.player.messages.getLast().key().equals("academy.vfx2.status"),"status reports local diagnostics to client player");
    }
    static void previewDisabled()throws Exception {
        groups++;yes(!(boolean)value(RailgunVfx2Preview.class,"ENABLED"),"ordinary client starts with staged preview disabled");
        yes(value(RailgunVfx2Preview.class,"AGES")==null,"disabled preview creates no phase array");
        ready();prepare(ClassicEffects.class,1,false);render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        for(String name:List.of("pendingBeam","pendingGeometry","captured"))yes(value(RailgunVfx2Preview.class,name)==null,"disabled natural beam creates no preview capture: "+name);
        var reload=new RegisterClientReloadListenersEvent();RailgunVfx2Preview.Reloads.register(reload);
        yes(reload.listener==null,"disabled preview creates no reload-listener registration");
        var hud=new RenderGuiEvent.Post();RailgunVfx2Preview.hud(hud);yes(hud.graphics.labels.isEmpty(),"disabled preview creates no HUD disclosure");
    }
    static void coreSelectionAndFallback()throws Exception {
        groups++;ready();prepare(ClassicEffectsBaseline.class,20,true);
        var baseline=render(ClassicEffectsBaseline.class,Vfx2Quality.OFF,new RenderLevelStageEvent());
        prepare(ClassicEffects.class,20,true);yes(baseline.equals(render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent())),"combined original/new inner buffers preserve baseline positions/colors/count/order");
        var innerVertices=Recording.vertices.stream().filter(v->inner(v.type)).toList();
        yes(innerVertices.size()==4*432&&count("coreDrawn")==4,"nearest four complete original inner cylinders replaced");
        yes(innerVertices.stream().map(v->Math.round(v.y+2)).collect(java.util.stream.Collectors.toSet()).equals(Set.of(3,4,5,6)),"core material selects nearest four while preserving source iteration");
        yes(count("vertices")==optionalCount()&&count("vertices")<=4032,"four432 core reservations plus sixteen144 halos remain below4096");
        prepare(ClassicEffects.class,20,false);for(Object beam:beams(ClassicEffects.class))field(beam.getClass(),"origin").set(beam,new Vec3(0,3,0));
        render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        var allCore=Recording.vertices.stream().filter(v->!optional(v.type)||inner(v.type)).toList();
        for(int i=0;i<20;i++)yes(allCore.subList(i*876,(i+1)*876).stream().filter(v->inner(v.type)).count()==(i<4?432:0),"equal-distance core priority is stable source order "+i);
        for(int mode=5;mode<=7;mode++) {
            ready();textures(mode);yes(RailgunVfx2Material.available()&&!RailgunVfx2Material.coreAvailable(),"noise-only failure preserves overlay but refuses inner replacement "+mode);
            prepare(ClassicEffects.class,20,true);yes(baseline.equals(render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent())),"partial resource failure cannot skip any original core "+mode);
            yes(count("coreDrawn")==0&&Recording.vertices.stream().noneMatch(v->inner(v.type))&&count("drawn")==16,"partial resource failure keeps classic inner and all selected overlays "+mode);
        }
        for(int mode=0;mode<2;mode++) {
            ready();ShaderInstance.failCore=mode==0;org.lwjgl.opengl.GL20.failedId=mode==1?18:-1;
            var event=shaders(0);event.complete();ShaderInstance.failCore=false;org.lwjgl.opengl.GL20.failedId=-1;
            yes(RailgunVfx2Material.available()&&!RailgunVfx2Material.coreAvailable(),"core-only compile/link failure preserves overlay "+mode);
            prepare(ClassicEffects.class,20,true);yes(baseline.equals(render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent())),"partial shader failure cannot omit original inner or outer core "+mode);
            yes(count("coreDrawn")==0&&count("drawn")==16,"partial shader failure consumes no replacement budget "+mode);
        }
        ready();var old=shaders(0);var current=shaders(0);current.complete("railgun_vfx2");old.complete("railgun_vfx2_core");
        yes(RailgunVfx2Material.available()&&!RailgunVfx2Material.coreAvailable(),"old partial core callback cannot join newer overlay generation");
        prepare(ClassicEffects.class,20,true);yes(baseline.equals(render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent())),"partial callback generation retains complete original core");
        current.complete("railgun_vfx2_core");var latestCore=value(RailgunVfx2Material.class,"coreShader");old.complete();
        yes(RailgunVfx2Material.coreAvailable()&&value(RailgunVfx2Material.class,"coreShader")==latestCore,"older completion cannot replace fully ready current core shader");
        prepare(ClassicEffectsBaseline.class,1,false);var baselineOne=render(ClassicEffectsBaseline.class,Vfx2Quality.OFF,new RenderLevelStageEvent());
        var originalBuffers=value(ClassicEffects.class,"worldBuffers");
        for(int mode=0;mode<3;mode++) {
            ready();prepare(ClassicEffects.class,1,false);final int failureMode=mode;
            field(ClassicEffects.class,"worldBuffers").set(null,new MultiBufferSource.BufferSource(){
                @Override public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType type){
                    if(inner(type.name)) {
                        if(failureMode==0)throw new IllegalStateException("injected acquisition failure");
                        if(failureMode==1)shaders(0).complete();
                        if(failureMode==2)try{textures(5);}catch(Exception failure){throw new RuntimeException(failure);}
                    }
                    return super.getBuffer(type);
                }
            });
            yes(baselineOne.equals(render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent())),"acquisition revalidation failure retains entire original core "+mode);
            yes(count("coreDrawn")==0&&Recording.vertices.stream().noneMatch(v->inner(v.type)),"acquisition revalidation consumes no core reservation "+mode);
            field(ClassicEffects.class,"worldBuffers").set(null,originalBuffers);
        }
        ready();direct(Vfx2Quality.MEDIUM,new Vec3(.25,4.5,.125),CAMERA,1000);
        var buffers=new MultiBufferSource.BufferSource();var used=new LinkedHashSet<RenderType>();var matrix=new org.joml.Matrix4f();
        for(int instance=0;instance<4;instance++) {
            var out=RailgunVfx2.prepareInnerCore(900,buffers,used);yes(out!=null,"core reservation fits after full overlay budget "+instance);
            for(int i=0;i<432;i++) {
                out.addVertex(matrix,1.25f,-2.5f,3.75f).setUv(.25f,.75f).setColor(37,73,109,147).setUv1(11,13).setUv2(17,19).setNormal(.125f,-.25f,.5f);
                var v=Recording.vertices.getLast();
                yes(v.x==1.25f&&v.y==-2.5f&&v.z==3.75f&&v.u==.75f&&v.v==.75f&&v.r==37&&v.g==73&&v.b==109&&v.a==147
                    &&v.u1==11&&v.v1==13&&v.u2==17&&v.v2==19&&v.nx==.125f&&v.ny==-.25f&&v.nz==.5f&&v.matrix==matrix,
                    "core wrapper forwards every position/color/normal/light attribute and changes only U phase");
            }
        }
        int before=count("vertices");for(int i=0;i<1000;i++)yes(RailgunVfx2.prepareInnerCore(900,buffers,used)==null,"same-frame core quota rejects excess acquisition");
        yes(count("coreDrawn")==4&&count("drawn")==16&&count("vertices")==before&&before==optionalCount()&&before<=4032,"same-frame combined actual emission and reservations never exceed finite caps");
        RailgunVfx2.beginFrame();yes(count("coreDrawn")==0&&count("vertices")==0,"core reservations reset each frame");
        for(long age:new long[]{-1,2500,Long.MAX_VALUE})yes(RailgunVfx2.prepareInnerCore(age,buffers,used)==null&&count("vertices")==0,"expired/invalid core phase cannot reserve vertices "+age);
        for(var quality:List.of(Vfx2Quality.OFF,Vfx2Quality.LOW)) {
            ClassicHudConfig.VFX2_QUALITY.set(quality);RailgunVfx2.beginFrame();
            yes(RailgunVfx2.maxCoreInstances()==0&&RailgunVfx2.prepareInnerCore(900,buffers,used)==null&&count("vertices")==0,"OFF/LOW always keep original inner material "+quality);
        }
        ready();var type=(RenderType)value(Class.forName("cn.academy.port.client.vfx2.RailgunVfx2RenderTypes"),"INNER_CORE");
        yes(type.state.transparency==RenderType.TRANSLUCENT_TRANSPARENCY,"inner core selects native alpha-blend state instead of additive halo state");
        yes("LEQUAL".equals(type.state.depth)&&"COLOR".equals(type.state.mask)&&"PARTICLES".equals(type.state.target),"inner material depth-tests and writes color only in scoped target");
        yes(type.state.texture.texture().equals(RailgunVfx2Material.NOISE)&&type.state.texture.blur()&&!type.state.texture.mipmap(),"inner material uses only filtered reference noise");
        yes(type.state.shader.shader().get()==value(RailgunVfx2Material.class,"coreShader"),"inner material supplier follows current core shader generation");
    }
    static Point p(double x,double y,double z){return new Point(x,y,z);}
    static void close(double actual,double expected,String why){yes(Double.isFinite(actual)&&Math.abs(actual-expected)<1e-10,why+": "+actual+" versus "+expected);}
    static RailgunCoreCameraSafety safety()throws Exception{return (RailgunCoreCameraSafety)value(ClassicEffects.class,"CORE_CAMERA_SAFETY");}
    static void cameraSafetyAndIntegration()throws Exception {
        groups++;var fence=new RailgunCoreCameraSafety(65);Point origin=p(0,0,0),axis=p(1,0,0);Object key=new Object();
        for(Point camera:List.of(p(0,0,0),p(5,0,0),p(5,.19,0),p(-.20,0,0),p(10+.0882,0,0),p(10.2,0,0)))
            yes(!fence.allow(key,origin,axis,10,.09,camera),"camera inside/on caps or within near-plane clearance uses classic fallback");
        yes(fence.allow(key,origin,axis,10,.09,p(5,.70,0)),"side camera beyond clearance and reentry gap admits material");
        yes(fence.allow(key,origin,axis,10,.09,p(5,.55,0)),"previously safe camera remains admitted inside hysteresis band");
        yes(!fence.allow(key,origin,axis,10,.09,p(5,.50,0)),"minimum safety-floor boundary enters fallback");
        yes(!fence.allow(key,origin,axis,10,.09,p(5,.55,0)),"unsafe camera stays in fallback inside hysteresis band");
        yes(!fence.allow(key,origin,axis,10,.09,p(5,.60,0)),"reentry boundary is strict");
        yes(fence.allow(key,origin,axis,10,.09,p(5,Math.nextUp(.60),0)),"camera beyond reentry boundary re-admits material");
        fence.clear();yes(fence.candidate(key,origin,axis,10,.09,p(5,.70,0))&&fence.trackedIdentities()==0,"passing conservative ranking does not mutate hysteresis");
        yes(!fence.allow(key,origin,axis,10,.09,p(5,.49,0)),"exact post-ranking check dominates a passing hint");
        yes(!fence.candidate(key,origin,axis,10,.09,p(5,.55,0)),"a conservative rejection does not toggle back to safe in same frame");
        fence.clear();yes(fence.allow(key,origin,axis,10,.01125,p(5,2,0)),"small phase starts with safe side camera");
        yes(fence.allow(key,origin,axis,10,.01125,p(10.59,0,0)),"narrow late-phase end cap leaves required clearance");
        yes(!fence.allow(key,origin,axis,10,.117,p(10.59,0,0)),"larger actual phase radius extends cap into unsafe clearance");
        yes(!fence.allow(key,origin,axis,10,.117,p(10.62,0,0)),"grown phase stays unsafe within reentry gap");
        yes(fence.allow(key,origin,axis,10,.01125,p(10.62,0,0)),"shrinking phase re-admits only when actual cap clearance exceeds reentry gap");
        close(RailgunCoreCameraSafety.axisDistance(origin,axis,10,.09,p(-1,0,0)),1+.0018,"actual start cap begins at .02 radius");
        close(RailgunCoreCameraSafety.axisDistance(origin,axis,10,.09,p(12,0,0)),2-.0882,"actual final cap extends by .98 radius");
        close(RailgunCoreCameraSafety.axisDistance(origin,axis,.001,.117,p(.08,0,0)),0,"early body/caps include radius even when visible length is shorter");
        close(RailgunCoreCameraSafety.axisDistance(origin,p(1+5e-7,0,0),10,.09,p(12,0,0)),12-(10+.0882)*(1+5e-7),"accepted unit roundoff retains raw-direction cap scaling");
        for(Point bad:List.of(p(0,0,0),p(2,0,0),p(.5,0,0),p(1+2e-6,0,0),p(Double.NaN,0,0),p(Double.POSITIVE_INFINITY,0,0)))
            yes(!Double.isFinite(RailgunCoreCameraSafety.axisDistance(origin,bad,10,.09,p(5,1,0))),"non-unit/nonfinite direction fails closed");
        for(double bad:new double[]{0,-1,Double.NaN,Double.POSITIVE_INFINITY})
            yes(!fence.allow(key,origin,axis,bad,.09,p(5,1,0))&&!fence.allow(key,origin,axis,10,bad,p(5,1,0)),"invalid actual length/radius fails closed "+bad);
        yes(!fence.allow(null,origin,axis,10,.09,p(5,1,0))&&!fence.allow(key,null,axis,10,.09,p(5,1,0))
            &&!fence.allow(key,origin,axis,10,.09,p(Double.NaN,1,0)),"missing/nonfinite identity/point/camera fails closed");
        for(double shift:new double[]{0,8192,-1000000,29999900,-29999900}) {
            Point moved=p(shift+.25,65.5,-shift-.125),camera=p(shift+5.25,66.125,-shift-.125);
            close(RailgunCoreCameraSafety.axisDistance(moved,axis,128,.117,camera),.625,"camera-relative safety distance survives world-border translation "+shift);
            var translated=new RailgunCoreCameraSafety(1);Object identity=new Object();
            yes(translated.allow(identity,moved,axis,128,.117,camera),"far-coordinate safe admission unchanged "+shift);
            yes(!translated.allow(identity,moved,axis,128,.117,p(shift+5.25,65.625,-shift-.125)),"far-coordinate camera-inside fallback unchanged "+shift);
        }
        var bounded=new RailgunCoreCameraSafety(2);Object a=new String("equal"),b=new String("equal"),c=new Object();
        yes(bounded.allow(a,origin,axis,10,.09,p(5,1,0))&&bounded.allow(b,origin,axis,10,.09,p(5,1,0))&&bounded.trackedIdentities()==2,"equal objects retain distinct bounded identity state");
        yes(!bounded.allow(c,origin,axis,10,.09,p(5,1,0))&&bounded.trackedIdentities()==2,"identity capacity fails closed without eviction or growth");
        bounded.retain(List.of(b),null);yes(bounded.trackedIdentities()==1&&bounded.allow(c,origin,axis,10,.09,p(5,1,0)),"retired identities prune and capacity is reusable");
        bounded.retain(List.of(),c);yes(bounded.trackedIdentities()==1,"one historical sample identity survives live-owner pruning");bounded.clear();yes(bounded.trackedIdentities()==0,"clear discards bounded hysteresis state");
        ready();prepare(ClassicEffectsBaseline.class,1,false);render(ClassicEffectsBaseline.class,Vfx2Quality.OFF,new RenderLevelStageEvent());
        Object original=beams(ClassicEffectsBaseline.class).getFirst();double radius=.09*(1+(double)field(original.getClass(),"widthWiggle").get(original));
        var originalInner=Recording.vertices.stream().filter(v->v.r==241&&v.g==240&&v.b==222).toList();
        yes(originalInner.size()==432,"cap oracle uses all actual immutable-baseline inner vertices");
        for(var vertex:originalInner)yes(RailgunCoreCameraSafety.axisDistance(p(0,3,0),axis,128,radius,p(vertex.x,vertex.y+2,vertex.z))<=radius+1e-5,"conservative capsule contains actual original cylinder/cap vertex");
        yes(Math.abs(originalInner.stream().mapToDouble(v->v.x).min().orElseThrow()-.02*radius)<1e-5
            &&Math.abs(originalInner.stream().mapToDouble(v->v.x).max().orElseThrow()-(128+.98*radius))<1e-5,"actual cap extrema match source-derived safety interval");
        for(Vec3 camera:List.of(new Vec3(0,3,0),new Vec3(64,3.1,0),new Vec3(128.02,3,0),new Vec3(-.2,3,0))) {
            prepare(ClassicEffectsBaseline.class,1,false);var event=new RenderLevelStageEvent();event.camera=camera;var expected=render(ClassicEffectsBaseline.class,Vfx2Quality.OFF,event);
            prepare(ClassicEffects.class,1,false);event=new RenderLevelStageEvent();event.camera=camera;
            yes(expected.equals(render(ClassicEffects.class,Vfx2Quality.MEDIUM,event)),"unsafe first-person/body/end/cap camera retains complete original geometry");
            yes(count("coreDrawn")==0&&Recording.vertices.stream().noneMatch(v->inner(v.type))&&count("drawn")==1,"unsafe camera disables only inner override and preserves auxiliary layer");
        }
        prepare(ClassicEffects.class,20,false);for(int i=0;i<20;i++)field(beams(ClassicEffects.class).get(i).getClass(),"origin").set(beams(ClassicEffects.class).get(i),new Vec3(0,i<16?3:4+i-16,0));
        var event=new RenderLevelStageEvent();event.camera=new Vec3(0,3,0);render(ClassicEffects.class,Vfx2Quality.MEDIUM,event);
        yes(count("coreDrawn")==4&&count("drawn")==16,"unsafe nearest beams are rejected before nearest-four selection and safe beams refill quota");
        yes(Recording.vertices.stream().filter(v->inner(v.type)).map(v->Math.round(v.y+3)).collect(java.util.stream.Collectors.toSet()).equals(Set.of(4,5,6,7)),"safe core ranking spans full visible list beyond overlay sixteen");
        yes(safety().trackedIdentities()<=65&&count("vertices")<=4096,"safety selection preserves finite identity and shared-vertex bounds");
        Util.now+=2500;tick(ClassicEffects.class);yes(safety().trackedIdentities()==0&&beams(ClassicEffects.class).isEmpty(),"retired actual beams prune camera hysteresis state");
        prepare(ClassicEffects.class,1,false);double[] offsets={.70,.55,.49,.55,.61};int[] admitted={1,1,0,0,1};
        for(int i=0;i<offsets.length;i++) {double offset=offsets[i];
            event=new RenderLevelStageEvent();event.camera=new Vec3(64,3+offset,0);render(ClassicEffects.class,Vfx2Quality.MEDIUM,event);
            yes(count("coreDrawn")==admitted[i],"actual camera traversal obeys enter/exit hysteresis "+i);
        }
        yes(safety().trackedIdentities()==1,"actual safe beam retains one camera identity");
        var reload=new RegisterClientReloadListenersEvent();ClassicEffects.CoreCameraSafetyReloads.register(reload);reload.listener.onResourceManagerReload(location->{throw new java.io.IOException();});
        yes(safety().trackedIdentities()==0,"resource reload clears actual camera identity state");
        render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());AcademyClient.epoch++;tick(ClassicEffects.class);
        yes(safety().trackedIdentities()==0,"input-session change clears actual camera identity state");
        render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());MC.connection=new Object();tick(ClassicEffects.class);
        yes(safety().trackedIdentities()==0,"connection change clears actual camera identity state");
        render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());var player=MC.player;MC.player=new net.minecraft.world.entity.Entity();tick(ClassicEffects.class);
        yes(safety().trackedIdentities()==0,"player/session identity change clears camera state");MC.player=player;tick(ClassicEffects.class);
        render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());MC.level=new ClientLevel();tick(ClassicEffects.class);
        yes(safety().trackedIdentities()==0&&beams(ClassicEffects.class).isEmpty(),"world change clears camera identity and original beam owner");
        for(var quality:List.of(Vfx2Quality.OFF,Vfx2Quality.LOW)) {
            prepare(ClassicEffects.class,1,false);render(ClassicEffects.class,quality,new RenderLevelStageEvent());
            yes(safety().trackedIdentities()==0&&count("coreDrawn")==0,"OFF/LOW skip camera-state admission and keep original core "+quality);
        }
    }
    public static void main(String[] args)throws Exception {
        classicAndSelection();admissionAndClock();failureAndGeneration();frameCapsAndPrecision();renderStateAndCommands();previewDisabled();coreSelectionAndFallback();cameraSafetyAndIntegration();
        System.out.println("PASS|"+groups+"|"+assertions);
    }
}
'''

PREVIEW_HARNESS = r'''
package oracle;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.ClassicHudConfig.Vfx2Quality;
import cn.academy.port.client.AcademyClient;
import cn.academy.port.client.ClassicEffects;
import cn.academy.port.client.ClassicEffects.RailgunMaterialSample;
import cn.academy.port.client.ClassicEffectsBaseline;
import cn.academy.port.client.vfx2.*;
import net.minecraft.Util;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.*;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.*;
import java.util.*;
import static oracle.RailgunVfx2IntegrationOracle.*;

public class RailgunVfx2PreviewOracle {
    static Object live,baselineBeam;
    static byte[] randomBytes(Object beam)throws Exception {
        var out=new java.io.ByteArrayOutputStream();try(var stream=new java.io.ObjectOutputStream(out)){stream.writeObject(field(beam.getClass(),"random").get(beam));}return out.toByteArray();
    }
    static String liveState()throws Exception {
        var result=new StringBuilder(value(ClassicEffects.class,"clockMillis")+"|"+value(ClassicEffects.class,"lastWallMillis")+"|"+MC.queue.size());
        for(Object beam:beams(ClassicEffects.class)) {
            result.append('|').append(System.identityHashCode(beam));
            for(String name:List.of("created","length","lastFrame","widthWiggle","glowWiggle","ticks"))result.append('|').append(field(beam.getClass(),name).get(beam));
            result.append('|').append(Base64.getEncoder().encodeToString(randomBytes(beam)));
            for(Object spark:(List<?>)field(beam.getClass(),"sparks").get(beam))result.append('|').append(field(spark.getClass(),"draw").get(spark)).append(':').append(field(spark.getClass(),"pattern").get(spark));
        }
        return result.toString();
    }
    static void clean()throws Exception {
        MC.singleplayer=true;MC.windowActive=true;MC.screen=null;MC.cameraEntity=null;MC.player.alive=true;MC.player.removed=MC.player.spectator=false;
        AcademyClient.state.category="electromaster";reset(ClassicEffects.class);
    }
    static RailgunMaterialSample capture()throws Exception {
        clean();add(ClassicEffects.class,new Vec3(0,3,0),AXIS,128);MC.drain();live=beams(ClassicEffects.class).getFirst();
        ((Random)field(live.getClass(),"random").get(live)).setSeed(73);
        yes(value(RailgunVfx2Preview.class,"pendingBeam")==live,"only accepted actual beam identity becomes pending");
        Util.now+=149;render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        yes(value(RailgunVfx2Preview.class,"captured")==null,"preview does not manufacture a sample before natural 150ms phase");
        Util.now++;render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        var sample=(RailgunMaterialSample)value(RailgunVfx2Preview.class,"captured");
        yes(sample!=null&&sample.finite()&&sample.capturedAge()==150,"sample freezes only after natural accepted production emission");
        yes(value(RailgunVfx2Preview.class,"pendingBeam")==null&&value(RailgunVfx2Preview.class,"pendingGeometry")==null,"frozen sample retains no mutable Beam identity");
        return sample;
    }
    static void key(int which){RailgunVfx2Preview.key(new InputEvent.Key(which,org.lwjgl.glfw.GLFW.GLFW_PRESS));}
    static List<String> fixed(RailgunMaterialSample sample,long phase,boolean enhanced)throws Exception {
        ClassicHudConfig.VFX2_QUALITY.set(Vfx2Quality.MEDIUM);RailgunVfx2.beginFrame();Recording.clear();
        ClassicEffects.renderRailgunMaterialPreview(sample,phase,CAMERA,new org.joml.Matrix4f(),new MultiBufferSource.BufferSource(),new HashSet<>(),new LinkedHashSet<>(),enhanced);
        return classicValues(phase);
    }
    static List<String> baseline(RailgunMaterialSample sample,long phase)throws Exception {
        field(ClassicEffectsBaseline.class,"clockMillis").setLong(null,phase);field(baselineBeam.getClass(),"lastFrame").setLong(baselineBeam,phase);
        for(String name:List.of("widthWiggle","glowWiggle"))field(baselineBeam.getClass(),name).set(baselineBeam,field(sample.getClass(),name).get(sample));
        Recording.clear();var method=ClassicEffectsBaseline.class.getDeclaredMethod("renderBeam",baselineBeam.getClass(),Vec3.class,org.joml.Matrix4f.class,MultiBufferSource.class,Set.class);method.setAccessible(true);
        method.invoke(null,baselineBeam,CAMERA,new org.joml.Matrix4f(),new MultiBufferSource.BufferSource(),new HashSet<RenderType>());
        return Recording.vertices.stream().map(Recording.Vertex::signature).toList();
    }
    public static void main(String[] args)throws Exception {
        yes((boolean)value(RailgunVfx2Preview.class,"ENABLED"),"separate opt-in JVM enables staged preview");ready();
        prepare(ClassicEffectsBaseline.class,1,false);baselineBeam=beams(ClassicEffectsBaseline.class).getFirst();
        clean();add(ClassicEffects.class,Vec3.ZERO,AXIS,4);MC.level=new ClientLevel();MC.drain();
        yes(value(RailgunVfx2Preview.class,"pendingBeam")==null,"stale-world callback cannot become preview capture");
        clean();MC.singleplayer=false;add(ClassicEffects.class,Vec3.ZERO,AXIS,4);MC.drain();
        yes(beams(ClassicEffects.class).size()==1&&value(RailgunVfx2Preview.class,"pendingBeam")==null,"multiplayer retains ordinary beam but disallows staged sample");
        var sample=capture();var sparks=(List<?>)field(sample.getClass(),"sparks").get(sample);
        yes(!sparks.isEmpty(),"sample copies actual original spark fields");
        try{sparks.clear();throw new AssertionError("sample spark list mutable");}catch(UnsupportedOperationException expected){}
        Object liveSpark=((List<?>)field(live.getClass(),"sparks").get(live)).getFirst();
        field(liveSpark.getClass(),"draw").setBoolean(liveSpark,true);
        yes(!(boolean)field(sparks.getFirst().getClass(),"draw").get(sparks.getFirst()),"sample spark flags do not alias mutable original BeamSpark");
        field(liveSpark.getClass(),"draw").setBoolean(liveSpark,false);
        String before=liveState();
        ClassicHudConfig.VFX2_QUALITY.set(Vfx2Quality.MEDIUM);RailgunVfx2.beginFrame();Recording.clear();
        ClassicEffects.renderRailgunMaterialPreview(sample,150,sample.origin(),new org.joml.Matrix4f(),new MultiBufferSource.BufferSource(),new HashSet<>(),new LinkedHashSet<>(),true);
        yes(count("coreDrawn")==0&&Recording.vertices.stream().noneMatch(v->inner(v.type))
            &&Recording.vertices.stream().filter(v->!optional(v.type)).count()==876,"historical preview exact post-wiggle camera gate preserves complete classic fallback");
        yes(before.equals(liveState()),"unsafe historical camera check changes no live owner/clock/random state");
        for(long phase:new long[]{150,400,1200,2000,2400}) {
            var expected=baseline(sample,phase);
            yes(expected.equals(fixed(sample,phase,false)),"fixed-phase classic emitter matches immutable baseline at "+phase);
            yes(optionalCount()==0,"classic selected staged material contains no auxiliary vertices");
            yes(expected.equals(fixed(sample,phase,true)),"enhanced staged material preserves complete classic vertices at "+phase);
            yes(optionalCount()>0,"enhanced fixed phase emits optional geometry "+phase);
            yes(before.equals(liveState()),"fixed-phase render does not mutate live beam/queue/clock/ticks/random "+phase);
        }
        for(long invalid:new long[]{-1,0,149,151,2500,Long.MAX_VALUE})yes(fixed(sample,invalid,true).isEmpty()&&Recording.vertices.isEmpty(),"nonselected staged age rejected "+invalid);
        yes(before.equals(liveState()),"invalid staged calls also preserve live state");
        key(org.lwjgl.glfw.GLFW.GLFW_KEY_F8);yes(RailgunVfx2Preview.activeSample()==null,"F8 cannot double-render captured beam before original expiry");
        Util.now+=2349;tick(ClassicEffects.class);yes(beams(ClassicEffects.class).size()==1&&RailgunVfx2Preview.activeSample()==null,"overlap remains blocked through 2499ms");
        Util.now++;tick(ClassicEffects.class);yes(beams(ClassicEffects.class).isEmpty()&&RailgunVfx2Preview.activeSample()==sample,"historical preview activates after actual original 2500ms expiry");
        before=liveState();render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        yes(!Recording.vertices.isEmpty()&&optionalCount()>0&&beams(ClassicEffects.class).isEmpty(),"staged callback draws frozen sample without resurrecting live BEAMS");
        yes(before.equals(liveState()),"same-clock staged callback changes no live lifetime state");
        key(org.lwjgl.glfw.GLFW.GLFW_KEY_F10);render(ClassicEffects.class,Vfx2Quality.MEDIUM,new RenderLevelStageEvent());
        yes(optionalCount()==0&&!Recording.vertices.isEmpty(),"F10 classic comparison switches only optional material");
        key(org.lwjgl.glfw.GLFW.GLFW_KEY_F9);yes(RailgunVfx2Preview.phaseAge()==400,"F9 advances fixed phase only");
        MC.options.hideGui=true;var hud=new RenderGuiEvent.Post();RailgunVfx2Preview.hud(hud);
        yes(hud.graphics.labels.stream().anyMatch(s->s.contains("STAGED IN-ENGINE MATERIAL PREVIEW")),"disclosure rendering contains explicit staged label despite hideGui input");MC.options.hideGui=false;
        MC.screen=new Object();yes(RailgunVfx2Preview.activeSample()==null,"menu interruption stops staged rendering");MC.screen=null;
        yes(RailgunVfx2Preview.activeSample()==null,"closing menu requires deliberate F8 resume");key(org.lwjgl.glfw.GLFW.GLFW_KEY_F8);
        yes(RailgunVfx2Preview.activeSample()==sample,"F8 resumes intact historical sample after menu");
        AcademyClient.state.presets.revision++;RailgunVfx2Preview.tick(new ClientTickEvent.Post());
        yes(value(RailgunVfx2Preview.class,"captured")==null,"preset revision invalidates historical sample");
        sample=capture();AcademyClient.epoch++;RailgunVfx2Preview.tick(new ClientTickEvent.Post());
        yes(value(RailgunVfx2Preview.class,"captured")==null,"input session change invalidates historical sample");
        sample=capture();var reload=new RegisterClientReloadListenersEvent();RailgunVfx2Preview.Reloads.register(reload);reload.listener.onResourceManagerReload(location->{throw new java.io.IOException();});
        yes(value(RailgunVfx2Preview.class,"captured")==null,"resource reload clears historical sample");
        clean();add(ClassicEffects.class,Vec3.ZERO,AXIS,4);MC.drain();Util.now+=2500;tick(ClassicEffects.class);
        yes(value(RailgunVfx2Preview.class,"pendingBeam")==null&&value(RailgunVfx2Preview.class,"captured")==null,"pending sample expires with original beam if no natural frame captured");
        sample=capture();MC.level=new ClientLevel();tick(ClassicEffects.class);
        yes(value(RailgunVfx2Preview.class,"captured")==null&&beams(ClassicEffects.class).isEmpty(),"world replacement clears sample and live owner");
        System.out.println("PASS|"+assertions);
    }
}
'''

NATIVE_CONFIG = r'''
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.ClassicHudConfig.Vfx2Quality;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.util.*;
public class RailgunNativeConfigOracle {
    static int assertions;
    static void yes(boolean value,String why){assertions++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args) {
        yes(!ClassicHudConfig.SPEC.isLoaded(),"actual unloaded spec");
        for(Object bad:List.of("HIGH","", "ultra", "MEDIUM ", -1, 999, Double.NaN, List.of("LOW"))) {
            var config=CommentedConfig.inMemory();ClassicHudConfig.SPEC.correct(config);
            config.set("vfx2.quality",bad);yes(!ClassicHudConfig.SPEC.isCorrect(config),"invalid enum rejected "+bad);
            ClassicHudConfig.SPEC.correct(config);yes(config.get("vfx2.quality").toString().equals("LOW"),"invalid value corrected to actual default "+bad);
            yes(ClassicHudConfig.SPEC.isCorrect(config),"corrected enum spec valid "+bad);
        }
        var missing=CommentedConfig.inMemory();ClassicHudConfig.SPEC.correct(missing);
        missing.remove("vfx2.quality");ClassicHudConfig.SPEC.correct(missing);
        yes(missing.get("vfx2.quality").toString().equals("LOW"),"missing quality receives actual default");
        for(var quality:Vfx2Quality.values()) {
            var config=new TomlParser().parse("[vfx2]\nquality = \""+quality.name()+"\"\n");ClassicHudConfig.SPEC.correct(config);
            yes(config.get("vfx2.quality").toString().equals(quality.name()),"valid TOML enum retained "+quality);
            yes(ClassicHudConfig.SPEC.isCorrect(config),"valid actual config spec "+quality);
        }
        boolean malformed=false;try {new TomlParser().parse("[vfx2]\nquality = [\n");}catch(RuntimeException expected){malformed=true;}
        yes(malformed,"malformed TOML rejected by actual pinned parser; game-level recovery is not tested");
        System.out.println("PASS|"+assertions);
    }
}
'''


def source_guards(root, sources, baseline):
    classic = sources['cn/academy/port/client/ClassicEffects.java']
    def ordinary(source):
        # Permit only the accepted-beam preview hook and lifecycle clear/observation.
        source = source.replace('            Beam beam = new Beam(origin, direction.normalize(), Math.min(length, 256), clockMillis);\n'
                                '            BEAMS.add(beam);\n'
                                '            RailgunVfx2Preview.accepted(beam, beam.origin, beam.direction, beam.length, beam.created);',
                                '            BEAMS.add(new Beam(origin, direction.normalize(), Math.min(length, 256), clockMillis));')
        source = re.sub(r'^\s*RailgunVfx2Preview\.(?:advanceClock\(clockMillis\)|clear\(\));\n', '', source, flags=re.M)
        source = re.sub(r'^\s*CORE_CAMERA_SAFETY\.(?:clear\(\)|retain\(BEAMS, RailgunVfx2Preview\.activeSample\(\)\));\n', '', source, flags=re.M)
        source = re.sub(r'^\s*synchronizeCoreSafety\(minecraft\);\n', '', source, flags=re.M)
        return source
    for marker in [
        'public static void addRailgun(', 'public static void onClientTick(',
        'private static void synchronizeWorld(', 'private static void updateClock(',
        'private static void board(',
        'private static void vertex(', 'private static final class Beam {',
        'private static final class BeamSpark {', 'private static final class WeakArc {',
    ]:
        assert block(ordinary(classic), marker) == block(baseline, marker), 'historical classic implementation changed: ' + marker
    for marker in ['private static void cylinder(', 'private static void ringQuad(', 'private static Vec3 ring(']:
        original = block(baseline, marker).replace('Beam beam', 'Vec3 direction').replace('beam.direction', 'direction')
        original = re.sub(r'\bbeam\b', 'direction', original)
        assert block(classic, marker) == original, 'historical ring/cylinder changed beyond immutable direction plumbing: ' + marker
    original_beam = block(baseline, 'private static void renderBeam(')
    candidate_beam = block(classic, 'private static boolean renderBeamAtAge(')
    def normalize(source):
        for name, replacement in [('beamOrigin', 'beam.origin'), ('beamLength', 'beam.length'),
                                  ('widthWiggle', 'beam.widthWiggle'), ('glowWiggle', 'beam.glowWiggle')]:
            source = re.sub(r'\b' + name + r'\b', replacement, source)
        return source.replace('return false;', 'return;')
    original_timeline = original_beam[original_beam.index('double alpha'):original_beam.index('if (!beam.sparks')]
    candidate_timeline = candidate_beam[candidate_beam.index('double alpha'):candidate_beam.index('if (live != null')]
    exact_safety_gate = ('        if (enhancedCore) enhancedCore = CORE_CAMERA_SAFETY.allow(live != null ? live : sample,\n'
                         '                safetyPoint(beamOrigin), safetyPoint(direction), length, .09 * widthScale, safetyPoint(camera));\n')
    assert exact_safety_gate in candidate_timeline, 'inner material must recheck actual post-wiggle phase radius and visible length'
    candidate_timeline = candidate_timeline.replace(exact_safety_gate, '')
    assert normalize(candidate_timeline) == original_timeline, 'classic age/width/length/alpha equations changed'
    live_sparks = block(candidate_beam, 'if (live != null && !live.sparks.isEmpty())').replace('if (live != null && !live.sparks.isEmpty())', 'if (!beam.sparks.isEmpty())').replace('live.sparks', 'beam.sparks')
    assert live_sparks == block(original_beam, 'if (!beam.sparks.isEmpty())'), 'classic live spark emission changed'
    candidate_geometry = candidate_beam[candidate_beam.index('Vec3 origin'):]
    inner_admission = ('        VertexConsumer inner = enhancedCore ? RailgunVfx2.prepareInnerCore(age,buffers,enhancedTypes) : null;\n'
                       '        // Acquiring another shared RenderType ends the preceding BufferBuilder. Never retain a\n'
                       '        // classic consumer across optional material acquisition; fetch its fallback only when used.\n'
                       '        VertexConsumer core = null;\n'
                       '        if (inner == null) { core = worldBuffer(buffers, used, WHITE); inner = core; }\n')
    assert inner_admission in candidate_geometry, 'inner override must be admitted before choosing original-or-new consumer'
    candidate_geometry = candidate_geometry.replace(inner_admission, '        VertexConsumer core = worldBuffer(buffers, used, WHITE);\n')
    assert 'cylinder(inner, matrix, direction, origin, length, 0.09 * widthScale, 0.98, 241, 240, 222' in candidate_geometry, 'null/unready override must preserve complete original inner cylinder'
    assert '        if (core == null) core = worldBuffer(buffers, used, WHITE);\n' in candidate_geometry, 'outer core must acquire an active classic consumer after successful optional emission'
    candidate_geometry = candidate_geometry.replace('        if (core == null) core = worldBuffer(buffers, used, WHITE);\n', '').replace('cylinder(inner,', 'cylinder(core,')
    candidate_geometry = re.sub(r'\s*if \(enhanced\) RailgunVfx2\.render\([^;]+;', '', candidate_geometry)
    candidate_geometry = re.sub(r'\s*return true;', '', candidate_geometry)
    candidate_geometry = normalize(candidate_geometry)
    candidate_geometry = re.sub(r'(?<![.\w])direction\b', 'beam.direction', candidate_geometry)
    candidate_geometry = candidate_geometry.replace('matrix, beam.direction, origin', 'matrix, beam, origin')
    assert candidate_geometry == original_beam[original_beam.index('Vec3 origin'):], 'classic core/board geometry changed beyond factored immutable inputs'
    wrapper = block(classic, 'private static void renderBeam(')
    assert wrapper.index('beam.wiggle(clockMillis)') < wrapper.index('renderBeamAtAge(') < wrapper.index('RailgunVfx2Preview.wantsSample('), 'sample capture must follow natural production emission'
    preview = sources['cn/academy/port/client/vfx2/RailgunVfx2Preview.java']
    accepted = block(preview, 'public static void accepted(')
    assert accepted.index('if (!ENABLED) return;') < accepted.index('new Geometry('), 'disabled preview must not allocate pending geometry'
    assert re.search(r'if \(rendered && RailgunVfx2Preview.wantsSample\(beam, age\)\)\s*RailgunVfx2Preview.freeze\(beam, new RailgunMaterialSample', wrapper), 'sample construction must be guarded by actual accepted opt-in capture'
    assert classic.count('RailgunVfx2.render(') == 1, 'only existing renderBeam may enter auxiliary renderer'
    for name, source in sources.items():
        if '/vfx2/' in name:
            code = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
            assert not re.search(r'cn\.academy\.port\.(?:highpower|skill|ability|network|AcademyNetwork)', code), 'VFX2 reaches gameplay/network: ' + name
            assert not re.search(r'\b(?:hurt|setHealth|consumeCp|consumeCP|sendToServer|sendToPlayer|playSound|addParticle|broadcast)\s*\(', code), 'VFX2 performs gameplay/audio/particle action: ' + name
    academy = (root / 'src/main/java/cn/academy/port/AcademyCraft.java').read_text()
    assert 'container.registerConfig(ModConfig.Type.CLIENT,ClassicHudConfig.SPEC)' in academy, 'VFX2 config must remain client-only'
    config = sources['cn/academy/port/ClassicHudConfig.java']
    assert re.search(r'enum Vfx2Quality\s*\{\s*OFF\s*,\s*LOW\s*,\s*MEDIUM\s*}', config), 'only bounded qualities allowed'
    assert '.defineEnum("quality",Vfx2Quality.LOW)' in config, 'actual enum validation and conservative default required'
    assert not re.search(r'net\.minecraft\.client|vfx2\.Railgun', config), 'common-safe config must not load a client renderer'
    fsh = (root / 'src/main/resources/assets/academy/shaders/core/railgun_vfx2.fsh').read_text()
    fsh = re.sub(r'//[^\n]*', '', fsh)
    assert len(re.findall(r'\bsource\.a\b', fsh)) == 1, 'texture alpha must enter shader exactly once'
    assert 'source.a * vertexColor.a' in fsh, 'single texture alpha times vertex fade required'
    assert 'vec3(intensity) * vertexColor.rgb' in fsh, 'concept cyan texture must retain warm vertex color'
    assert not re.search(r'\b(?:pow|exp|smoothstep)\s*\(', fsh), 'no second softness mask may multiply supplied texture'
    shader = json.loads((root / 'src/main/resources/assets/academy/shaders/core/railgun_vfx2.json').read_text())
    assert shader['vertex'] == shader['fragment'] == 'academy:railgun_vfx2', 'shader must be scoped to auxiliary material'
    historical = {}
    for name in ['verify-displaced-needle-ledger.py', 'verify-classic-terminal-ingress.py', 'verify-classic-terminal-oracles.py']:
        path = 'scripts/' + name
        original = subprocess.run(['git', 'show', BASELINE + ':' + path], cwd=root, capture_output=True)
        assert original.returncode == 0, 'immutable guard missing from recorded baseline: ' + path
        assert original.stdout == (root / path).read_bytes(), 'historical guard weakened or changed: ' + path
        historical[path] = hashlib.sha256(original.stdout).hexdigest()
    return historical


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, help='write JSON results with exact production source hashes')
    parser.add_argument('--native-config-classpath', type=Path, help='file containing existing pinned runtime classpath; no Gradle is launched')
    parser.add_argument('--pinned-minecraft-sources', type=Path, help='existing NeoForge21.1.252/Minecraft1.21.1 source archive containing MultiBufferSource.java')
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    java_root = root / 'src/main/java'
    names = ['cn/academy/port/ClassicHudConfig.java', 'cn/academy/port/client/ClassicEffects.java',
             'cn/academy/port/client/ClassicEffectTimeline.java', 'cn/academy/port/client/ClassicArcGeometry.java',
             'cn/academy/port/client/vfx2/RailgunVfx2.java', 'cn/academy/port/client/vfx2/RailgunVfx2Material.java',
             'cn/academy/port/client/vfx2/RailgunVfx2RenderTypes.java', 'cn/academy/port/client/vfx2/RailgunOverlayGeometry.java',
             'cn/academy/port/client/vfx2/RailgunVfx2Preview.java', 'cn/academy/port/client/vfx2/RailgunCoreCameraSafety.java']
    sources = {name: (java_root / name).read_text() for name in names}
    baseline = run(['git', 'show', BASELINE + ':src/main/java/cn/academy/port/client/ClassicEffects.java'], cwd=root)
    historical = source_guards(root, sources, baseline)
    archive = args.pinned_minecraft_sources or root / 'build/moddev/artifacts/neoforge-21.1.252-sources.jar'
    with ZipFile(archive) as jar:
        pinned_source = jar.read('net/minecraft/client/renderer/MultiBufferSource.java').decode()
    getter = block(pinned_source, 'public VertexConsumer getBuffer(')
    assert 'this.endBatch(this.lastSharedType)' in getter and 'new BufferBuilder(this.sharedBuffer' in getter, 'pinned nonfixed shared-buffer seam must retain type-switch invalidation'
    stubs = dict(STUBS)
    stubs['net/minecraft/client/renderer/MultiBufferSource.java'] = stubs['net/minecraft/client/renderer/MultiBufferSource.java'].replace('/*PINNED_GET_BUFFER*/', getter.replace('public VertexConsumer getBuffer(', 'private VertexConsumer getPinnedBuffer('))
    toolchain = jdk(root)
    mutations = []
    native_config = {'result': 'NOT_RUN', 'reason': 'No existing pinned runtime classpath available'}
    with tempfile.TemporaryDirectory(prefix='railgun-vfx2-integration-') as directory:
        work = Path(directory)
        generated = work / 'sources'
        classes = work / 'classes'
        all_sources = {**stubs, **sources, 'oracle/RailgunVfx2IntegrationOracle.java': HARNESS,
                       'oracle/RailgunVfx2PreviewOracle.java': PREVIEW_HARNESS,
                       'cn/academy/port/client/ClassicEffectsBaseline.java': baseline.replace('ClassicEffects', 'ClassicEffectsBaseline')}
        for name, source in all_sources.items():
            path = generated / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
        paths = sorted(generated.rglob('*.java'))
        run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-d', classes, *paths])
        result = run([toolchain / 'bin/java', '-Djava.awt.headless=true', '-cp', classes, 'oracle.RailgunVfx2IntegrationOracle']).strip().split('|')
        assert result[0] == 'PASS', result
        preview_result = run([toolchain / 'bin/java', '-Djava.awt.headless=true', '-Dacademy.vfx2.materialPreview=true',
                              '-cp', classes, 'oracle.RailgunVfx2PreviewOracle']).strip().split('|')
        assert preview_result[0] == 'PASS', preview_result
        # Negative controls mutate only temporary production copies, never the checkout.
        controls = [
            ('instanceCap', 'cn/academy/port/client/vfx2/RailgunVfx2.java',
             lambda s: s.replace('case LOW -> 8', 'case LOW -> 9').replace('case MEDIUM -> 16', 'case MEDIUM -> 17'),
             'only optional closest'),
            ('staleShaderGeneration', 'cn/academy/port/client/vfx2/RailgunVfx2Material.java',
             lambda s: re.sub(r'\s*if \(generation != shaderGeneration\) return;', '', s),
             'stale shader generation'),
            ('earlyFloatWorldCoordinates', 'cn/academy/port/client/vfx2/RailgunOverlayGeometry.java',
             lambda s: s.replace('startWorld.subtract(cameraWorld)',
                 'new Vec((float)startWorld.x()-(float)cameraWorld.x(), (float)startWorld.y()-(float)cameraWorld.y(), (float)startWorld.z()-(float)cameraWorld.z())'),
             'actual emitter translation precision'),
            ('earlyPreviewOverlap', 'cn/academy/port/client/vfx2/RailgunVfx2Preview.java',
             lambda s: re.sub(r'\s*&& observedClock - captured\.created\(\) >= ClassicEffectTimeline\.RAILGUN_LIFETIME_MS', '', s),
             'F8 cannot double-render'),
            ('innerUvPhase', 'cn/academy/port/client/vfx2/RailgunVfx2.java',
             lambda s: s.replace('target.setUv(u+phase,v)', 'target.setUv(u,v)'),
             'selected inner core changes only U phase'),
            ('coreAcquisitionGeneration', 'cn/academy/port/client/vfx2/RailgunVfx2.java',
             lambda s: s.replace('||generation!=RailgunVfx2Material.coreGeneration()', ''),
             'acquisition revalidation consumes no core reservation'),
            ('originalCoreFallback', 'cn/academy/port/client/ClassicEffects.java',
             lambda s: s.replace('if (inner == null) { core = worldBuffer(buffers, used, WHITE); inner = core; }', 'if (inner == null) return false;'),
             'all classic vertex attributes/order match immutable baseline'),
            ('coreQuotaPriority', 'cn/academy/port/client/vfx2/RailgunVfx2.java',
             lambda s: s.replace('RailgunVfx2Material.coreAvailable()?4:0', 'RailgunVfx2Material.coreAvailable()?5:0'),
             'core material selects nearest four'),
            ('stalePrefetchedWhiteConsumer', 'cn/academy/port/client/ClassicEffects.java',
             lambda s: s.replace('        VertexConsumer inner = enhancedCore ?',
                                 '        VertexConsumer core = worldBuffer(buffers, used, WHITE);\n        VertexConsumer inner = enhancedCore ?')
                        .replace('        VertexConsumer core = null;\n', '')
                        .replace('if (inner == null) { core = worldBuffer(buffers, used, WHITE); inner = core; }', 'if (inner == null) inner = core;'),
             'writing ended shared RenderType buffer'),
            ('cameraClearance', 'cn/academy/port/client/vfx2/RailgunCoreCameraSafety.java',
             lambda s: s.replace('return distance > clearance;', 'return true;'),
             'camera inside/on caps'),
            ('cameraCaps', 'cn/academy/port/client/vfx2/RailgunCoreCameraSafety.java',
             lambda s: s.replace('Math.max(radius, length + radius - offset) * norm', 'Math.max(radius, length) * norm'),
             'larger actual phase radius extends cap'),
            ('cameraHysteresis', 'cn/academy/port/client/vfx2/RailgunCoreCameraSafety.java',
             lambda s: s.replace('REENTRY_GAP = .10', 'REENTRY_GAP = 0'),
             'unsafe camera stays in fallback'),
            ('cameraUnitDirection', 'cn/academy/port/client/vfx2/RailgunCoreCameraSafety.java',
             lambda s: s.replace(' || Math.abs(norm - 1) > UNIT_NORM_TOLERANCE', ''),
             'non-unit/nonfinite direction fails closed'),
            ('cameraExactPostWiggle', 'cn/academy/port/client/ClassicEffects.java',
             lambda s: s.replace('        if (enhancedCore) enhancedCore = CORE_CAMERA_SAFETY.allow(live != null ? live : sample,\n'
                                 '                safetyPoint(beamOrigin), safetyPoint(direction), length, .09 * widthScale, safetyPoint(camera));\n', ''),
             'historical preview exact post-wiggle camera gate'),
        ]
        for label, name, mutate, expected in controls:
            original = sources[name]
            changed = mutate(original)
            assert changed != original, 'mutation must change production source: ' + label
            path = generated / name
            path.write_text(changed)
            run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', classes, '-d', classes, path])
            launch = [str(toolchain / 'bin/java'), '-Djava.awt.headless=true']
            if label in {'earlyPreviewOverlap', 'cameraExactPostWiggle'}:
                launch.append('-Dacademy.vfx2.materialPreview=true')
            launch.extend(['-cp', str(classes), 'oracle.RailgunVfx2PreviewOracle' if label in {'earlyPreviewOverlap', 'cameraExactPostWiggle'} else 'oracle.RailgunVfx2IntegrationOracle'])
            broken = subprocess.run(launch, text=True, capture_output=True)
            assert broken.returncode != 0 and expected in broken.stderr, 'negative control did not fail its expected assertion: ' + label + '\n' + broken.stderr
            mutations.append(label)
            path.write_text(original)
            run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', classes, '-d', classes, path])
        cp_file = args.native_config_classpath or root / 'run-highpower-clean-loader/instance/runtime-classpath-colon.txt'
        if cp_file.is_file():
            cp = cp_file.read_text().strip()
            pinned = work / 'native-config'
            config_test = work / 'RailgunNativeConfigOracle.java'
            config_test.write_text(NATIVE_CONFIG)
            run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', cp, '-d', pinned,
                 java_root / 'cn/academy/port/ClassicHudConfig.java', config_test])
            output = run([toolchain / 'bin/java', '-cp', str(pinned) + os.pathsep + cp, config_test.stem], cwd=work)
            native = next((line for line in output.splitlines() if line.startswith('PASS|')), '').split('|')
            assert native[0] == 'PASS', output
            native_config = {'result': 'PASS', 'assertions': int(native[1]), 'api': 'NeoForge 21.1.252 / Night Config 3.8.3',
                             'classpathSha256': hashlib.sha256(cp_file.read_bytes()).hexdigest(), 'actualClassicHudConfig': True,
                             'invalidAndMissingEnumCorrection': True, 'validTomlEnumPreservation': True, 'malformedTomlParserRejection': True}
    report = {
        'result': 'PASS', 'groups': int(result[1]), 'recordingAssertions': int(result[2]),
        'stagedPreviewAssertions': int(preview_result[1]),
        'actualProductionClasses': len(sources), 'baselineCommit': BASELINE,
        'classicVertexAttributesAndEmissionOrder': 'OFF/LOW and fallback exact baseline; MEDIUM positions/colors/count/order exact with only approved nearest-four material and U-phase normalization',
        'classicRelativeSharedBatchDrawOrderingPreserved': True, 'finalAuxiliaryCleanupRequestsAfterClassic': True,
        'callbackAdmissionAndPauseAware2500msLifecycle': True, 'sameFrameInstanceAndVertexLimits': {'LOW': [8, 512], 'MEDIUM': [16, 4096]},
        'closestSelectionDistanceAndFrustumOnlyOptional': True,
        'adversarialDeferredShaderCallbackGeneration': 'PASS; not evidence of actual overlapping native reloads',
        'innerCoreMaterial': {'onlyMedium': True, 'nearestStableSelection': 4, 'verticesPerCompleteOriginalInner': 432,
                             'maximumCombinedReservedVertices': 4032, 'sharedMaximum': 4096,
                             'sourceUvZeroOrOnePlusPauseAwareAgeDividedBy1800': True,
                             'wrapperOtherAttributesUnchanged': True, 'partialNoiseShaderAndGenerationFailuresPreserveClassicCore': True,
                             'getBufferFailureAndGenerationRevalidationBeforeReplacement': True,
                             'nativeAlphaBlendDepthTestColorOnlyStateSelection': True},
        'coreCameraSafety': {'nearPlaneMarginMeters': .20, 'minimumAxisClearanceMeters': .50, 'unsafeReentryGapMeters': .10,
                             'exactPostWiggleRadius': '.09 * widthScale', 'actualCapInterval': 'min(.02r,L) through max(r,L+.98r), retaining accepted unit-norm roundoff',
                             'all432ActualBaselineCapAndCylinderVerticesContained': True,
                             'nonUnitOrNonFiniteGeometryFailsClosed': True, 'unitNormTolerance': 1e-6,
                             'conservativeRankingCannotAdmitOrToggleBackToSafe': True, 'exactPostWiggleGateDominatesIncludingHistoricalPreview': True,
                             'unsafeNearestBeamsExcludedBeforeCoreQuota': True, 'safeCoreSelectionCanRefillBeyondNearestSixteenOverlays': True,
                             'identityBound': 65, 'retirementWorldResourcePlayerConnectionSessionCleanup': True,
                             'classicFallbackGeometryAndOffLowRenderingPreserved': True, 'worldBorderTranslationInvariant': True},
        'sharedBufferAdapter': {'pinnedApi': 'Minecraft1.21.1 / NeoForge21.1.252',
                                'entry': 'net/minecraft/client/renderer/MultiBufferSource.java',
                                'sourceEntrySha256': hashlib.sha256(pinned_source.encode()).hexdigest(),
                                'getBufferMethodSha256': hashlib.sha256(getter.encode()).hexdigest(),
                                'getBufferBodyExtractedUnchanged': True, 'finiteBuilderEndedConsumerFence': True,
                                'fixedBuffers': 0, 'reachedRenderModes': ['QUADS'],
                                'stalePrefetchedWhiteMutationRejected': True},
        'stagedPreview': {'defaultOffNoCaptureOrPhaseArray': True, 'onlyAcceptedNaturallyRenderedBeamCaptured': True,
                         'immutableSampleAndSparkFields': True, 'liveClockQueueTicksRandomAndLifetimeUnchanged': True,
                         'fixedPhaseClassicVerticesMatchImmutableBaseline': [150, 400, 1200, 2000, 2400],
                         'noOverlapBeforeOriginal2500msExpiry': True, 'historicalRenderAfterOriginalExpiry': True,
                         'presetSessionResourceAndWorldCleanup': True},
        'mutationControlsRejected': mutations, 'nativeConfig': native_config,
        'historicalGuardsUnchanged': historical,
        'productionSourceHashes': {name: hashlib.sha256(source.encode()).hexdigest() for name, source in sources.items()},
        'nativeMinecraftOrGpuRuntime': False,
        'limitations': ['Recording adapters implement only reached client/resource/render API seams; full pinned compilation remains separate',
                        'Render-state calls are recorded, not proof of driver blending, terrain occlusion, shader compilation or visual quality',
                        'Malformed TOML is rejected by the pinned parser; native file-recovery behavior is not exercised',
                        'Same-frame caps cover repeated submissions between beginFrame calls; engine multipass callback timing is not simulated',
                        'Shared getBuffer switches implicitly draw batches between beams; ordered final cleanup requests do not defer all auxiliary draws until all classic draws',
                        'Deterministic baseline vertex comparison suppresses random spark lists; their unchanged production implementation is separately guarded',
                        'Frozen staged spark cutoff is nominal presentation behavior; it does not prove actual 30-tick wall timing under lag',
                        'Camera safety is a conservative admission rule tested against actual emitted geometry; native screenshots must establish whether it removes the observed fan',
                        'HUD disclosure is exercised by directly invoking its callback; native F1 event dispatch and screen visibility require native verification'],
    }
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
