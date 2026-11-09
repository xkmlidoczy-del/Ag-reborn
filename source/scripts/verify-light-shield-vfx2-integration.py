#!/usr/bin/env python3
"""Current-fork Light Shield VFX2 recording integration and immutable .31 differential.

Compiles the actual production classes without modification against recording
client/resource hosts and native pinned JOML. The classic oracle prefers a pinned Git object and verifies equivalent archived
fixtures when Git objects are unavailable. Both raw .31 sources carry fixed SHA256
checks and commit provenance, and are never copied from current production code.
Native NeoForge/Night Config is used separately for config validation/persistence.
This does not launch Minecraft, compile GLSL on a GPU, or claim visual acceptance.
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

BASELINE = '7af9e2977641935e048e6bf3fc913ddb917be82d'
BASELINE_EFFECTS_SHA256 = '6b06f62abfc0171f0e8983ba2aa290d0e651c14e9302ea137750689267c6bcee'
BASELINE_TIMELINE_SHA256 = 'b53f94133f8eecb4f580b48d1d16047b2f3181dbb4b83194eb38454df5cdff77'
TEXTURE_SHA256 = '5a0cf5f61bea467687820983efb76cfd14f68abbc38667e091b6b0f27965c5dc'
JOML_SHA256 = 'cac9f22f83a7aa33eebda73c16ff5261e3cb4911b6bafcf4c79ea486099d0c9a'
NATIVE_RENDER_TYPE_SHA256 = 'b551315da826aa2ce986c9a8915b1330230e10c8b58d6630113cde2f459b73da'
NATIVE_RENDER_STATE_SHA256 = '613f0f6b89f2db2f1ee79bdb09109b1e3422422f4e8693062e89c1aabc2ce6c7'
JAVA = 'src/main/java/cn/academy/port/'
BASELINE_FIXTURES = 'src/test/resources/vfx2/light-shield/baseline-' + BASELINE
# Original .31 guard hashes, captured from that exact Git object. Archive restores
# must preserve these checks even when the source deliverable has no .git metadata.
HISTORICAL_SHA256 = {
    "scripts/verify-railgun-vfx2-core-material.py": "76b3871a96c632b330d9c4c8964c11d4692517cd3eb00b80646bc006b64b247b",
    "scripts/verify-railgun-vfx2-geometry.py": "fdbf8e6f5bb7dcbac2b740d4f887f44db33fa3b3c0cf56b72a2596c8044c67f2",
    "scripts/verify-railgun-vfx2-integration.py": "b76d19409243e3fe5efe9fbbdb766d5af9a65ec06fbf23604fd26a393b02b3c6",
    "src/main/java/cn/academy/port/client/ClassicArcGeometry.java": "98c6168fb9e42ce2c2eb139ca5846ce9e77d98b5e12fba2df7b32727c51ec7f5",
    "src/main/java/cn/academy/port/client/ClassicEffectTimeline.java": "95157ea6de87f53b16381cbb7b92d0a60b08fd1a60e3198c87ece4f789262856",
    "src/main/java/cn/academy/port/client/ClassicEffects.java": "96ac659d49b4ccddea08ada95670e198c3a83f088f7d5f5764bb4e0ebb359ab4",
    "src/main/java/cn/academy/port/client/vfx2/RailgunCoreCameraSafety.java": "cf05bb7a73bc42626c7db1fc3156969b64a6c84b8837f87fab6e50a10161752c",
    "src/main/java/cn/academy/port/client/vfx2/RailgunOverlayGeometry.java": "0a165241120979e6ba86bdb0481333d1684a72de81edb13211634752e293ec53",
    "src/main/java/cn/academy/port/client/vfx2/RailgunVfx2.java": "63b4f791e2da20a49490a15c05ee2030f1ff15e98fcae17dd697cf171e21b0ea",
    "src/main/java/cn/academy/port/client/vfx2/RailgunVfx2Material.java": "eff1f5f47d4bccde86184ac14d2ecfcf41ed1b34d4cc03c7b3ed6df498d532fe",
    "src/main/java/cn/academy/port/client/vfx2/RailgunVfx2Preview.java": "c29cdea46f6c08f222e5ad22857725cdd8dad7c13c33bb5c789d5791a961bad8",
    "src/main/java/cn/academy/port/client/vfx2/RailgunVfx2RenderTypes.java": "878c9bd211e45e0ffb35ea9f81eb1b7806f2b5becc155b3eada6fea334d4bf1a",
    "src/main/java/cn/academy/port/skill/RailgunDamage.java": "d57ef49a7941b2325d399726867267df2dc2b5f1c80aecf262640111c81d6a32",
    "src/main/resources/assets/academy/shaders/core/railgun_vfx2.fsh": "d36ff40a9262892a9c6ca7480cfce7a574b3b6c34b4745ad81d1bef04b7f50b2",
    "src/main/resources/assets/academy/shaders/core/railgun_vfx2.json": "2029390fefa1d81a4958827e70c99d8127652ff6f82cd04ea739a5d0b3cfd675",
    "src/main/resources/assets/academy/shaders/core/railgun_vfx2.vsh": "9d7773854cbf0a730967cd87754da4b641ac0b9954cca86accfd16f9286fdea6",
    "src/main/resources/assets/academy/shaders/core/railgun_vfx2_core.fsh": "7c1487796fca4cef560e313314f2f00d593527d29e0178a85463daf4effb1dd1",
    "src/main/resources/assets/academy/shaders/core/railgun_vfx2_core.json": "f451c0e91cabeb064739d2bd9026d3798c872a52e35576f18f04dbf2278276e5",
    "src/main/resources/assets/academy/shaders/core/railgun_vfx2_core.vsh": "925a882a559ebf37b6bdb0598d36be7c1c4c12b055802cd1fb1c164af9be334b",
    "src/main/resources/assets/academy/sounds/em/railgun.ogg": "96a1d678f9ab701a02e77ffad6df8da9deb4d2d5a0e829c087818d088d19b28a",
    "src/main/resources/assets/academy/textures/abilities/electromaster/skills/railgun.png": "de8b035cd6e628c2ec7f3c58bcd46905517c18cbb55736ad0c25807094ae2bce",
    "src/main/resources/assets/academy/textures/effects/railgun.png": "de339a8cd470aab701e91e618ced58ec1e3309d0d9b1c4e9c5bfab0373a4f3bd",
    "src/main/resources/assets/academy/textures/effects/railgun/blend_in.png": "51a30948bbea0a4f0dac7c332ab2cd14a65624aba8ea21c77bdc71e40ad080af",
    "src/main/resources/assets/academy/textures/effects/railgun/blend_out.png": "aae45dd462353dbefbb9f4ee4c3e5ea9a1c2ebceff3004847e2c2fb78eb8325b",
    "src/main/resources/assets/academy/textures/effects/railgun/tile.png": "3fbc725dc998318d271a8bfa3c188aca38209314703d4bf5bd1e4aa5a69cd5a7",
    "src/main/resources/assets/academy/textures/effects/railgun_fade.png": "88cb6585bf17411867c0b8869ebe84ff3e06f8b6033a7f9c1307b5e249d3a54d",
    "src/main/resources/data/academy/advancement/electromaster/railgun.json": "128bfaf7c9578fe4fdf1d0759844290dfd3885f3e090432d6b7b35a7b4374d5d",
    "src/test/java/cn/academy/port/client/ClassicMeltdownerGeometryClassResourcesTest.java": "219634a1929ae701f4e2780dcdd68a5abc2d774e995df23b63ead95aa15dc270",
    "src/test/java/cn/academy/port/client/MeltdownerStarterVisualRegressionTest.java": "cd7ff62aee53b44baf454c69dfb6adda049c8bfd0333bf1770275f96194fbce9",
    "src/test/resources/classic-achievements/original/resources/assets/academy/textures/abilities/electromaster/skills/railgun.png": "de8b035cd6e628c2ec7f3c58bcd46905517c18cbb55736ad0c25807094ae2bce",
    "src/test/resources/classic-achievements/original/scala/cn/academy/vanilla/electromaster/skill/Railgun.scala": "1804381ae435209a4484a8bf91f11d65c990e712ef131f2edeb297be2ffa032b",
    "src/test/resources/classic-oracles/meltdowner-camera/files.sha256": "49cec99d629e9eb46f6cab953f4ddd1e5271b36dc4da37182ed7bbab8e00c98a",
    "src/test/resources/classic-oracles/meltdowner-camera/guards.properties": "adb8d97593366be32ddf0c007ff7b5cb79a12d428021e9d57752fe294febe830",
    "src/test/resources/classic-oracles/meltdowner-camera/harness/cn/academy/port/client/MeltdownerCurrentMainGeometryHarness.java.txt": "e9f84aef9870213e13ea52eca424c500b2dccb76c4c9ca4eb5c5904be6fced3f",
    "src/test/resources/classic-oracles/meltdowner-camera/hosts/BOUNDARIES.md": "f7d7e2e81e641ea67c785b339869a1e359a3cd5609e3f8f058a137f84472709a",
    "src/test/resources/classic-oracles/meltdowner-camera/hosts/net/minecraft/client/Minecraft.java.txt": "caf02d4ff8e313c1eacf0fcf905f675f24b5826261203a3ef7c578d35a909692",
    "src/test/resources/classic-oracles/meltdowner-camera/hosts/net/minecraft/client/Options.java.txt": "61234a01603ba9ed14eb50cc221f799cac6e7e39f9ac6af710cdf6faf754979c",
    "src/test/resources/classic-oracles/meltdowner-camera/hosts/net/minecraft/client/player/LocalPlayer.java.txt": "571d02b0d3d3d3c151619830826281b55f5f98de010a1e298b3ded40cc8d7dac",
    "src/test/resources/classic-oracles/meltdowner-camera/hosts/net/minecraft/client/renderer/entity/ItemRenderer.java.txt": "8b2d8d951e1034da0ef0c34e33a46aec393111e0042c690d779bd895fc7334ef",
    "src/test/resources/classic-oracles/meltdowner-camera/hosts/net/minecraft/world/entity/Entity.java.txt": "520184a6e913fbc974f761226123f6110db0209525c5ac353e2467d3d0797be1",
    "src/test/resources/classic-oracles/meltdowner-camera/hosts/net/minecraft/world/entity/LivingEntity.java.txt": "6ad80cd5351816ea324e726fa6f60460d49741ccea7f9ccab097e71305fd7b62",
    "src/test/resources/classic-oracles/meltdowner-camera/hosts/net/minecraft/world/entity/player/Player.java.txt": "77e1a4445de570c2716354678c1b189f8ad049692483b23f4697b59e6b226e20",
    "src/test/resources/classic-oracles/meltdowner-camera/witnesses/EntityMdBall.java.txt": "173e3ee7af867e3c20ada46dc719b7ff2d7020828a01fc42d403b28830cc8e65",
    "src/test/resources/classic-oracles/meltdowner-camera/witnesses/RenderMdShield.java.txt": "851065d5d3645dad2b53cf771009841a12f8656e07d30bda178ec1dd36583bbe",
    "src/test/resources/classic-oracles/meltdowner-camera/witnesses/RenderParticle.java.txt": "65c3c626eda89f241e9eccaa6e305ae06e9a5dcd25b2b74be15b33a69271c687",
    "src/test/resources/classic-oracles/meltdowner-camera/witnesses/RendererRayBaseGlow.java.txt": "f1cc35cb06dba90a5a67d2c7d3464cbc4283742b4ffc8dc5ad22dfbf2572cb1a",
    "src/test/resources/classic-oracles/meltdowner-camera/witnesses/RendererRayBaseSimple.java.txt": "7e9165978ea2608f166b2726ffe03c0eea95a162b3ec68bc99d41a872331a32f",
    "src/test/resources/classic-oracles/shared-skill-consumption/academycraft/src/main/scala/cn/academy/vanilla/electromaster/skill/Railgun.scala.txt": "1804381ae435209a4484a8bf91f11d65c990e712ef131f2edeb297be2ffa032b"
}



def run(command, cwd=None):
    result = subprocess.run([str(x) for x in command], cwd=cwd, text=True, capture_output=True)
    if result.returncode:
        raise AssertionError(result.stdout + result.stderr)
    return result.stdout


def digest(data):
    return hashlib.sha256(data if isinstance(data, bytes) else data.encode()).hexdigest()


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
    raise AssertionError('JDK 21 required; set JAVA_HOME')


def block(source, marker):
    start = source.index(marker)
    opening = source.index('{', start)
    depth, end = 1, opening + 1
    while depth:
        depth += (source[end] == '{') - (source[end] == '}')
        end += 1
    return source[start:end]


STUBS = {
'net/neoforged/api/distmarker/Dist.java': 'package net.neoforged.api.distmarker; public enum Dist {CLIENT, DEDICATED_SERVER}',
'net/neoforged/bus/api/EventPriority.java': 'package net.neoforged.bus.api; public enum EventPriority {HIGHEST,HIGH,NORMAL,LOW,LOWEST}',
'net/neoforged/bus/api/SubscribeEvent.java': '''package net.neoforged.bus.api; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface SubscribeEvent {EventPriority priority() default EventPriority.NORMAL;}''',
'net/neoforged/fml/common/EventBusSubscriber.java': '''package net.neoforged.fml.common; @java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.RUNTIME)
public @interface EventBusSubscriber {String modid(); net.neoforged.api.distmarker.Dist[] value(); Bus bus() default Bus.GAME; enum Bus {MOD,GAME}}''',
'net/minecraft/Util.java': 'package net.minecraft;public class Util {public static long now;public static long getMillis(){return now;}}',
'net/minecraft/util/Mth.java': 'package net.minecraft.util;public class Mth {public static int clamp(int v,int a,int b){return Math.max(a,Math.min(v,b));}}',
'net/minecraft/util/RandomSource.java': 'package net.minecraft.util;public class RandomSource {public static RandomSource create(){return new RandomSource();}}',
'net/minecraft/resources/ResourceLocation.java': '''package net.minecraft.resources;public record ResourceLocation(String namespace,String path) {
public static ResourceLocation fromNamespaceAndPath(String n,String p){return new ResourceLocation(n,p);}public String getPath(){return path;}public String toString(){return namespace+":"+path;}}''',
'net/minecraft/world/phys/Vec3.java': '''package net.minecraft.world.phys;public class Vec3 {
public static final Vec3 ZERO=new Vec3(0,0,0);public final double x,y,z;public Vec3(double x,double y,double z){this.x=x;this.y=y;this.z=z;}
public Vec3 add(Vec3 p){return new Vec3(x+p.x,y+p.y,z+p.z);}public Vec3 add(double a,double b,double c){return add(new Vec3(a,b,c));}
public Vec3 subtract(Vec3 p){return new Vec3(x-p.x,y-p.y,z-p.z);}public Vec3 scale(double s){return new Vec3(x*s,y*s,z*s);}
public double lengthSqr(){return x*x+y*y+z*z;}public Vec3 normalize(){double n=Math.sqrt(lengthSqr());return n<1E-4?ZERO:scale(1/n);}
public Vec3 cross(Vec3 p){return new Vec3(y*p.z-z*p.y,z*p.x-x*p.z,x*p.y-y*p.x);}public Vec3 lerp(Vec3 p,double t){return new Vec3(x+(p.x-x)*t,y+(p.y-y)*t,z+(p.z-z)*t);}}''',
'net/minecraft/world/entity/Entity.java': '''package net.minecraft.world.entity;public class Entity {
public int id;public net.minecraft.client.multiplayer.ClientLevel world;public boolean alive=true,removed,spectator;public net.minecraft.world.phys.Vec3 position=new net.minecraft.world.phys.Vec3(13,4,-9);public float yaw=27,pitch=-13;
public Entity(int id,net.minecraft.client.multiplayer.ClientLevel l){this.id=id;world=l;}public int getId(){return id;}public boolean isAlive(){return alive;}public boolean isRemoved(){return removed;}public boolean isSpectator(){return spectator;}
public net.minecraft.client.multiplayer.ClientLevel level(){return world;}public net.minecraft.world.phys.Vec3 position(){return position;}
public float getEyeHeight(){return 1.62F;}public float getYHeadRot(){return yaw;}public float getXRot(){return pitch;}
public double getX(){return position.x;}public double getY(){return position.y;}public double getZ(){return position.z;}}''',
'net/minecraft/world/entity/player/Player.java': '''package net.minecraft.world.entity.player;public class Player extends net.minecraft.world.entity.Entity {
public Player(int id,net.minecraft.client.multiplayer.ClientLevel l){super(id,l);}}''',
'net/minecraft/client/multiplayer/ClientLevel.java': '''package net.minecraft.client.multiplayer;public class ClientLevel {
public java.util.Map<Integer,net.minecraft.world.entity.Entity> entities=new java.util.HashMap<>();public net.minecraft.world.entity.Entity getEntity(int id){return entities.get(id);}
public void playLocalSound(double x,double y,double z,net.minecraft.sounds.SoundEvent e,net.minecraft.sounds.SoundSource s,float v,float p,boolean b){}}''',
'net/minecraft/client/Minecraft.java': '''package net.minecraft.client;public class Minecraft {
private static final Minecraft INSTANCE=new Minecraft();public static Minecraft getInstance(){return INSTANCE;}
public net.minecraft.client.multiplayer.ClientLevel level;public net.minecraft.world.entity.player.Player player;public boolean paused;public Object connection=new Object();
public boolean isPaused(){return paused;}public Object getConnection(){return connection;}public final java.util.List<Runnable> queue=new java.util.ArrayList<>();
public void execute(Runnable r){queue.add(r);}public void drain(){var copy=java.util.List.copyOf(queue);queue.clear();copy.forEach(Runnable::run);}
public final SoundManager sound=new SoundManager();public SoundManager getSoundManager(){return sound;}
public static class SoundManager {public java.util.Set<Object> active=new java.util.HashSet<>();public void play(Object s){active.add(s);}public void stop(Object s){active.remove(s);}public boolean isActive(Object s){return active.contains(s);}}}''',
'net/minecraft/nbt/Tag.java': 'package net.minecraft.nbt;public class Tag {public static final int TAG_INT=3,TAG_LONG=4,TAG_DOUBLE=6,TAG_STRING=8;}',
'net/minecraft/nbt/CompoundTag.java': '''package net.minecraft.nbt;public class CompoundTag {
final java.util.Map<String,Object> map=new java.util.HashMap<>();public CompoundTag copy(){var t=new CompoundTag();t.map.putAll(map);return t;}
public void putString(String k,String v){map.put(k,v);}public void putInt(String k,int v){map.put(k,v);}public void putLong(String k,long v){map.put(k,v);}public void putDouble(String k,double v){map.put(k,v);}
public boolean contains(String k,int type){var v=map.get(k);return switch(type){case 3->v instanceof Integer;case 4->v instanceof Long;case 6->v instanceof Double;case 8->v instanceof String;default->false;};}
public String getString(String k){return map.get(k) instanceof String v?v:"";}public int getInt(String k){return map.get(k) instanceof Number n?n.intValue():0;}
public long getLong(String k){return map.get(k) instanceof Number n?n.longValue():0;}public double getDouble(String k){return map.get(k) instanceof Number n?n.doubleValue():0;}}''',
'net/minecraft/sounds/SoundEvent.java': '''package net.minecraft.sounds;public record SoundEvent(net.minecraft.resources.ResourceLocation location) {public static SoundEvent createVariableRangeEvent(net.minecraft.resources.ResourceLocation l){return new SoundEvent(l);}}''',
'net/minecraft/sounds/SoundSource.java': 'package net.minecraft.sounds;public enum SoundSource {MASTER}',
'net/minecraft/client/resources/sounds/AbstractTickableSoundInstance.java': '''package net.minecraft.client.resources.sounds;public abstract class AbstractTickableSoundInstance {
protected float volume,pitch;protected boolean looping;protected int delay;protected double x,y,z;public AbstractTickableSoundInstance(net.minecraft.sounds.SoundEvent e,net.minecraft.sounds.SoundSource s,net.minecraft.util.RandomSource r){}
protected void stop(){}public abstract void tick();}''',
'cn/academy/port/client/AcademyClient.java': '''package cn.academy.port.client;public class AcademyClient {public static void acceptedSingleStart(String s,long n,long t){}public static void acceptedSingleEnd(String s,long n,long t){}}''',
'cn/academy/port/client/ClassicSkillAlphaShader.java': '''package cn.academy.port.client;public class ClassicSkillAlphaShader {public static net.minecraft.client.renderer.ShaderInstance get(){return net.minecraft.client.renderer.GameRenderer.getPositionTexColorShader();}}''',
'com/mojang/blaze3d/vertex/PoseStack.java': '''package com.mojang.blaze3d.vertex;public class PoseStack {
private final java.util.ArrayDeque<org.joml.Matrix4f> poses=new java.util.ArrayDeque<>();public PoseStack(){poses.push(new org.joml.Matrix4f());}
public void pushPose(){poses.push(new org.joml.Matrix4f(poses.peek()));}public void popPose(){poses.pop();}public int depth(){return poses.size();}
public Pose last(){return new Pose(poses.peek());}public record Pose(org.joml.Matrix4f pose){}
public void translate(double x,double y,double z){poses.peek().translate((float)x,(float)y,(float)z);}public void mulPose(org.joml.Quaternionf q){poses.peek().rotate(q);}}''',
'com/mojang/math/Axis.java': '''package com.mojang.math;public record Axis(float x,float y,float z) {
public static final Axis XP=new Axis(1,0,0),YP=new Axis(0,1,0),ZP=new Axis(0,0,1);public org.joml.Quaternionf rotationDegrees(float d){return new org.joml.Quaternionf().rotationAxis(d*(float)Math.PI/180F,x,y,z);}}''',
'com/mojang/blaze3d/vertex/ByteBufferBuilder.java': 'package com.mojang.blaze3d.vertex;public class ByteBufferBuilder {public ByteBufferBuilder(int n){}}',
'com/mojang/blaze3d/vertex/VertexFormat.java': 'package com.mojang.blaze3d.vertex;public class VertexFormat {public enum Mode {QUADS}}',
'com/mojang/blaze3d/vertex/DefaultVertexFormat.java': 'package com.mojang.blaze3d.vertex;public class DefaultVertexFormat {public static final VertexFormat POSITION_TEX_COLOR=new VertexFormat();}',
'com/mojang/blaze3d/vertex/VertexConsumer.java': '''package com.mojang.blaze3d.vertex;public interface VertexConsumer {
VertexConsumer addVertex(org.joml.Matrix4f m,float x,float y,float z);VertexConsumer setUv(float u,float v);VertexConsumer setColor(int r,int g,int b,int a);}''',
'oracle/Recording.java': '''package oracle;public class Recording {
public static java.util.List<Vertex> vertices=new java.util.ArrayList<>();public static java.util.List<String> flushes=new java.util.ArrayList<>(),states=new java.util.ArrayList<>();
public static void clear(){vertices.clear();flushes.clear();states.clear();}public static String bits(float f){return Integer.toHexString(Float.floatToIntBits(f));}
public static class Vertex {public final String type,geometry;public float u,v;public int r,g,b,a;
public Vertex(String t,org.joml.Matrix4f m,float x,float y,float z){type=t;var p=m.transformPosition(x,y,z,new org.joml.Vector3f());var values=m.get(new float[16]);var s=new StringBuilder();for(float f:values)s.append(bits(f)).append(',');geometry=bits(x)+","+bits(y)+","+bits(z)+"|"+bits(p.x)+","+bits(p.y)+","+bits(p.z)+"|"+s;}
public String data(){return geometry+"|"+bits(u)+","+bits(v)+"|"+r+","+g+","+b+","+a;}}
}''',
'net/minecraft/client/renderer/MultiBufferSource.java': '''package net.minecraft.client.renderer;public interface MultiBufferSource {
static BufferSource immediate(com.mojang.blaze3d.vertex.ByteBufferBuilder b){return new BufferSource();}
class BufferSource implements MultiBufferSource {java.util.Map<RenderType,Consumer> builders=new java.util.HashMap<>();
public com.mojang.blaze3d.vertex.VertexConsumer getBuffer(RenderType t){oracle.Recording.states.add(t.signature());return builders.computeIfAbsent(t,Consumer::new);}
public void endBatch(RenderType t){oracle.Recording.flushes.add(t.name);var b=builders.remove(t);if(b!=null)b.ended=true;}
static class Consumer implements com.mojang.blaze3d.vertex.VertexConsumer {final RenderType type;oracle.Recording.Vertex last;boolean ended;Consumer(RenderType t){type=t;}
public com.mojang.blaze3d.vertex.VertexConsumer addVertex(org.joml.Matrix4f m,float x,float y,float z){if(ended)throw new AssertionError("ended consumer");last=new oracle.Recording.Vertex(type.name,m,x,y,z);oracle.Recording.vertices.add(last);return this;}
public com.mojang.blaze3d.vertex.VertexConsumer setUv(float u,float v){last.u=u;last.v=v;return this;}public com.mojang.blaze3d.vertex.VertexConsumer setColor(int r,int g,int b,int a){last.r=r;last.g=g;last.b=b;last.a=a;return this;}}}}''',
'net/minecraft/client/renderer/RenderType.java': '''package net.minecraft.client.renderer;public class RenderType {
public final String name;public CompositeState state;public com.mojang.blaze3d.vertex.VertexFormat format;public com.mojang.blaze3d.vertex.VertexFormat.Mode mode;public boolean sort,cr;
public RenderType(String n,com.mojang.blaze3d.vertex.VertexFormat f,com.mojang.blaze3d.vertex.VertexFormat.Mode m,int s,boolean c,boolean b,Runnable setup,Runnable clear){name=n;format=f;mode=m;sort=b;cr=c;}
protected static final Object LEQUAL_DEPTH_TEST="LEQUAL",COLOR_DEPTH_WRITE="COLOR_DEPTH_WRITE",COLOR_WRITE="COLOR_WRITE",CULL="CULL",NO_CULL="NO_CULL",NO_LIGHTMAP="NO_LIGHTMAP",NO_OVERLAY="NO_OVERLAY",PARTICLES_TARGET="PARTICLES_TARGET";
public static final TransparencyStateShard TRANSLUCENT_TRANSPARENCY=new TransparencyStateShard("translucent_transparency",()->{},()->{});
public static RenderType create(String n,com.mojang.blaze3d.vertex.VertexFormat f,com.mojang.blaze3d.vertex.VertexFormat.Mode m,int s,boolean c,boolean b,CompositeState state){var t=new RenderType(n,f,m,s,c,b,()->{},()->{});t.state=state;return t;}
public String signature(){var shader=state.shader.shader().get();return (format==com.mojang.blaze3d.vertex.DefaultVertexFormat.POSITION_TEX_COLOR)+"|"+mode+"|"+cr+"|"+sort+"|"+state.texture+"|"+state.transparency.name+"|"+state.depth+"|"+state.mask+"|"+state.cull+"|"+state.lightmap+"|"+state.overlay+"|"+state.target+"|"+shader.name;}
public static class TransparencyStateShard {public final String name;public final Runnable setup,clear;public TransparencyStateShard(String n,Runnable s,Runnable c){name=n;setup=s;clear=c;}}
public record ShaderStateShard(java.util.function.Supplier<ShaderInstance> shader){}
public record TextureStateShard(net.minecraft.resources.ResourceLocation texture,boolean blur,boolean mipmap){}
public static class CompositeState {public Object depth="LEQUAL",mask="COLOR_DEPTH_WRITE",cull="CULL",lightmap="NO_LIGHTMAP",overlay="NO_OVERLAY",target="MAIN_TARGET";public ShaderStateShard shader;public TextureStateShard texture;public TransparencyStateShard transparency;
public static Builder builder(){return new Builder();}public static class Builder {final CompositeState s=new CompositeState();
public Builder setShaderState(ShaderStateShard v){s.shader=v;return this;}public Builder setTextureState(TextureStateShard v){s.texture=v;return this;}public Builder setTransparencyState(TransparencyStateShard v){s.transparency=v;return this;}
public Builder setDepthTestState(Object v){s.depth=v;return this;}public Builder setWriteMaskState(Object v){s.mask=v;return this;}public Builder setCullState(Object v){s.cull=v;return this;}
public Builder setLightmapState(Object v){s.lightmap=v;return this;}public Builder setOverlayState(Object v){s.overlay=v;return this;}public Builder setOutputState(Object v){s.target=v;return this;}public CompositeState createCompositeState(boolean b){return s;}}}}
''',
'com/mojang/blaze3d/systems/RenderSystem.java': '''package com.mojang.blaze3d.systems;public class RenderSystem {static float[] color={1,1,1,1};public static float[] getShaderColor(){return color;}public static void setShaderColor(float r,float g,float b,float a){color=new float[]{r,g,b,a};}}''',
'com/mojang/blaze3d/shaders/Uniform.java': '''package com.mojang.blaze3d.shaders;public class Uniform {public float value;public void set(float f){value=f;}public void set(float x,float y,float z,float w){value=x;}}''',
'net/minecraft/client/renderer/ShaderInstance.java': '''package net.minecraft.client.renderer;public class ShaderInstance implements AutoCloseable {
public static boolean fail;public static String missingUniform="";public final String name;public boolean closed;public java.util.Map<String,com.mojang.blaze3d.shaders.Uniform> uniforms=new java.util.HashMap<>();
public ShaderInstance(net.minecraft.server.packs.resources.ResourceProvider p,net.minecraft.resources.ResourceLocation r,com.mojang.blaze3d.vertex.VertexFormat f)throws java.io.IOException {
name=r.getPath();if(fail&&name.equals("light_shield_vfx2"))throw new java.io.IOException("injected shader failure");
for(String n:java.util.List.of("ModelViewMat","ProjMat","ColorModulator","SwirlGain","FlowAmount","VisualPhase"))if(!n.equals(missingUniform))uniforms.put(n,new com.mojang.blaze3d.shaders.Uniform());}
public ShaderInstance(){name="fallback";}public int getId(){return 17;}public com.mojang.blaze3d.shaders.Uniform getUniform(String n){return uniforms.get(n);}public void close(){closed=true;}}
'''.replace('blaze3d/shaders','blaze3d.shaders'),
'net/minecraft/client/renderer/GameRenderer.java': '''package net.minecraft.client.renderer;public class GameRenderer {public static final ShaderInstance FALLBACK=new ShaderInstance();public static ShaderInstance getPositionTexColorShader(){return FALLBACK;}}''',
'org/lwjgl/opengl/GL20.java': '''package org.lwjgl.opengl;public class GL20 {public static final int GL_LINK_STATUS=35714;public static int linked=1;public static int glGetProgrami(int id,int what){return linked;}public static String glGetProgramInfoLog(int id){return "injected link failure";}}''',
'com/mojang/logging/LogUtils.java': '''package com.mojang.logging;public class LogUtils {public static Logger getLogger(){return new Logger();}public static class Logger {public void warn(String s,Object...args){}public void error(String s,Object...args){}public void info(String s,Object...args){}}}''',
'net/minecraft/server/packs/resources/Resource.java': '''package net.minecraft.server.packs.resources;public record Resource(byte[] bytes){public java.io.InputStream open(){return new java.io.ByteArrayInputStream(bytes);}}''',
'net/minecraft/server/packs/resources/ResourceProvider.java': '''package net.minecraft.server.packs.resources;public interface ResourceProvider {
Resource getResourceOrThrow(net.minecraft.resources.ResourceLocation l)throws java.io.IOException;default java.util.Optional<Resource> getResource(net.minecraft.resources.ResourceLocation l){try{return java.util.Optional.of(getResourceOrThrow(l));}catch(java.io.IOException e){return java.util.Optional.empty();}}}''',
'oracle/Resources.java': '''package oracle;public class Resources implements net.minecraft.server.packs.resources.ResourceProvider {
public boolean missing,corrupt;public net.minecraft.server.packs.resources.Resource getResourceOrThrow(net.minecraft.resources.ResourceLocation l)throws java.io.IOException {
if(missing)throw new java.io.IOException("injected resource absence");var p=java.nio.file.Path.of(System.getProperty("oracle.root"),"src/main/resources/assets",l.namespace(),l.path());
return new net.minecraft.server.packs.resources.Resource(corrupt?new byte[]{0,1,2}:java.nio.file.Files.readAllBytes(p));}}''',
'com/mojang/blaze3d/platform/NativeImage.java': '''package com.mojang.blaze3d.platform;public class NativeImage implements AutoCloseable {
final java.awt.image.BufferedImage image;public NativeImage(java.awt.image.BufferedImage i){image=i;}public static NativeImage read(java.io.InputStream in)throws java.io.IOException {var i=javax.imageio.ImageIO.read(in);if(i==null)throw new java.io.IOException("not PNG");return new NativeImage(i);}public static NativeImage read(byte[] b)throws java.io.IOException{return read(new java.io.ByteArrayInputStream(b));}
public int getWidth(){return image.getWidth();}public int getHeight(){return image.getHeight();}public void close(){}}''',
'net/neoforged/neoforge/client/event/RegisterShadersEvent.java': '''package net.neoforged.neoforge.client.event;public class RegisterShadersEvent {
public final oracle.Resources provider=new oracle.Resources();public record Registration(net.minecraft.client.renderer.ShaderInstance candidate,java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance> callback){}
public final java.util.List<Registration> registrations=new java.util.ArrayList<>();public net.minecraft.server.packs.resources.ResourceProvider getResourceProvider(){return provider;}
public void registerShader(net.minecraft.client.renderer.ShaderInstance s,java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance> c){registrations.add(new Registration(s,c));}public void complete(){registrations.forEach(r->r.callback.accept(r.candidate));}}''',
'net/neoforged/neoforge/client/event/ClientTickEvent.java': 'package net.neoforged.neoforge.client.event;public class ClientTickEvent {public static class Post {}}',
'net/neoforged/neoforge/client/event/RenderLevelStageEvent.java': '''package net.neoforged.neoforge.client.event;public class RenderLevelStageEvent {
public enum Stage {AFTER_PARTICLES,AFTER_ENTITIES}public Stage stage=Stage.AFTER_PARTICLES;public float partial=.37F;public final com.mojang.blaze3d.vertex.PoseStack poses=new com.mojang.blaze3d.vertex.PoseStack();
public Stage getStage(){return stage;}public com.mojang.blaze3d.vertex.PoseStack getPoseStack(){return poses;}public Camera getCamera(){return new Camera();}
public static class Camera {public net.minecraft.world.phys.Vec3 getPosition(){return new net.minecraft.world.phys.Vec3(-7.5,2,11);}public org.joml.Quaternionf rotation(){return new org.joml.Quaternionf();}}
public PartialTick getPartialTick(){return new PartialTick();}public class PartialTick {public float getGameTimeDeltaPartialTick(boolean b){return partial;}}}''',
'net/neoforged/neoforge/common/ModConfigSpec.java': '''package net.neoforged.neoforge.common;public class ModConfigSpec {
public static class ConfigValue<T> {T value;ConfigValue(T v){value=v;}public T get(){return value;}public void set(T v){value=v;}}
public static class DoubleValue extends ConfigValue<Double>{DoubleValue(double d){super(d);}}public static class BooleanValue extends ConfigValue<Boolean>{BooleanValue(boolean b){super(b);}}public static class EnumValue<E extends Enum<E>> extends ConfigValue<E>{EnumValue(E e){super(e);}}
public static class Builder {public Builder push(String n){return this;}public Builder pop(){return this;}public Builder comment(String...s){return this;}public DoubleValue defineInRange(String n,double d,double a,double b){return new DoubleValue(d);}public BooleanValue define(String n,boolean b){return new BooleanValue(b);}public ConfigValue<String> define(String n,String s){return new ConfigValue<>(s);}public <E extends Enum<E>>EnumValue<E> defineEnum(String n,E e){return new EnumValue<>(e);}public ModConfigSpec build(){return new ModConfigSpec();}}}''',
}

# The recording adapters cover only APIs reached by these current production classes.
STUBS['net/minecraft/world/entity/Entity.java'] = STUBS['net/minecraft/world/entity/Entity.java'].replace(
    'public double getX()', 'public final java.util.List<net.minecraft.network.chat.Component> messages=new java.util.ArrayList<>();public void displayClientMessage(net.minecraft.network.chat.Component c,boolean actionbar){messages.add(c);}public double getX()')
STUBS['net/neoforged/neoforge/common/ModConfigSpec.java'] = STUBS['net/neoforged/neoforge/common/ModConfigSpec.java'].replace(
    'public static class ConfigValue', 'public boolean loaded=true;public int saves;public boolean isLoaded(){return loaded;}public void save(){saves++;}public static class ConfigValue')
STUBS['org/lwjgl/opengl/GL20.java'] = STUBS['org/lwjgl/opengl/GL20.java'].replace(
    'public static String glGetProgramInfoLog', 'public static String inactive="";public static int glGetUniformLocation(int id,String n){return n.equals(inactive)?-1:1;}public static String glGetProgramInfoLog')
STUBS['com/mojang/blaze3d/shaders/Uniform.java'] = STUBS['com/mojang/blaze3d/shaders/Uniform.java'].replace(
    'public float value;', 'public static boolean fail;public float value;').replace('public void set(float f){value=f;}', 'public void set(float f){if(fail)throw new IllegalStateException("injected uniform update failure");value=f;}')
STUBS['net/minecraft/network/chat/Component.java'] = '''package net.minecraft.network.chat;public record Component(String key,Object[] args){public static Component translatable(String k,Object...a){return new Component(k,a);}}'''
STUBS['net/minecraft/commands/Commands.java'] = '''package net.minecraft.commands;public class Commands {
public static Node literal(String n){return new Node(n);}public static class Node {public String name;public java.util.function.ToIntFunction<Object> callback;public java.util.Map<String,Node> children=new java.util.LinkedHashMap<>();Node(String n){name=n;}public Node then(Node n){children.put(n.name,n);return this;}public Node executes(java.util.function.ToIntFunction<Object> c){callback=c;return this;}}
public static class Dispatcher {public java.util.Map<String,Node> roots=new java.util.HashMap<>();public void register(Node n){roots.put(n.name,n);}public int execute(String command){String[] w=command.split(" ");Node n=roots.get(w[0]);for(int i=1;i<w.length;i++)n=n.children.get(w[i]);if(n==null||n.callback==null)throw new IllegalArgumentException(command);return n.callback.applyAsInt(null);}}}'''
STUBS['net/neoforged/neoforge/client/event/RegisterClientCommandsEvent.java'] = '''package net.neoforged.neoforge.client.event;public class RegisterClientCommandsEvent {public final net.minecraft.commands.Commands.Dispatcher dispatcher=new net.minecraft.commands.Commands.Dispatcher();public net.minecraft.commands.Commands.Dispatcher getDispatcher(){return dispatcher;}}'''

STUBS['cn/academy/port/client/AcademyClient.java'] = 'package cn.academy.port.client;public class AcademyClient {\npublic static long epoch=1;public static long inputSessionEpoch(){return epoch;}public static boolean sameInputSessionEpoch(long e){return e==epoch;}\npublic static State state=new State();public static class State {public String category="meltdowner";public final Presets presets=new Presets();}public static class Presets {public int selected;public long version;public int current(){return selected;}public long revision(){return version;}}\npublic static void acceptedSingleStart(String s,long n,long t){}public static void acceptedSingleEnd(String s,long n,long t){}}'
STUBS['net/minecraft/client/Minecraft.java'] = STUBS['net/minecraft/client/Minecraft.java'].replace('public boolean isPaused()', 'public boolean singleplayer=true,windowActive=true;public Object screen;public net.minecraft.world.entity.Entity cameraEntity;public boolean hasSingleplayerServer(){return singleplayer;}public boolean isWindowActive(){return windowActive;}public net.minecraft.world.entity.Entity getCameraEntity(){return cameraEntity==null?player:cameraEntity;}public final Options options=new Options();public static class Options {public boolean hideGui;}public final Font font=new Font();public static class Font {public int width(String s){return s.length()*6;}}public boolean isPaused()')
STUBS['com/mojang/blaze3d/systems/RenderSystem.java'] = STUBS['com/mojang/blaze3d/systems/RenderSystem.java'].replace('static float[] color=', 'public static org.joml.Matrix4f getProjectionMatrix(){return new org.joml.Matrix4f();}public static org.joml.Matrix4f getModelViewMatrix(){return new org.joml.Matrix4f();}static float[] color=')
STUBS['net/neoforged/neoforge/client/event/RenderLevelStageEvent.java'] = STUBS['net/neoforged/neoforge/client/event/RenderLevelStageEvent.java'].replace('public org.joml.Quaternionf rotation(){return new org.joml.Quaternionf();}', 'public static org.joml.Quaternionf quaternion=new org.joml.Quaternionf();public org.joml.Quaternionf rotation(){return new org.joml.Quaternionf(quaternion);}')
STUBS['org/lwjgl/glfw/GLFW.java'] = 'package org.lwjgl.glfw;public class GLFW {public static final int GLFW_PRESS=1,GLFW_KEY_F8=297,GLFW_KEY_F9=298,GLFW_KEY_F10=299;}'
STUBS['net/neoforged/neoforge/client/event/InputEvent.java'] = 'package net.neoforged.neoforge.client.event;public class InputEvent {public record Key(int key,int action){public int getKey(){return key;}public int getAction(){return action;}}}'
STUBS['net/neoforged/neoforge/client/event/RenderGuiEvent.java'] = 'package net.neoforged.neoforge.client.event;public class RenderGuiEvent {public static class Post {public final Graphics graphics=new Graphics();public Graphics getGuiGraphics(){return graphics;}}public static class Graphics {public final java.util.List<String> labels=new java.util.ArrayList<>();public void fill(int a,int b,int c,int d,int color){}public void drawString(net.minecraft.client.Minecraft.Font font,String s,int x,int y,int color,boolean shadow){labels.add(s);}}}'

HARNESS = r'''
package oracle;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.ClassicHudConfig.Vfx2Quality;
import cn.academy.port.client.*;
import com.mojang.blaze3d.vertex.*;
import net.minecraft.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.phys.*;
import net.minecraft.nbt.*;
import net.neoforged.neoforge.client.event.*;
import java.lang.reflect.*;
import java.util.*;

public class LightShieldVfx2IntegrationOracle {
    static int assertions,groups;
    static final Class<?> CURRENT=ClassicMeltdownerStarterEffects.class;
    static final Class<?> BASELINE=ClassicMeltdownerStarterEffectsBaseline.class;
    static final Minecraft MC=Minecraft.getInstance();
    static void yes(boolean v,String why){assertions++;if(!v)throw new AssertionError(why);}
    static Field field(Class<?> c,String n)throws Exception{var f=c.getDeclaredField(n);f.setAccessible(true);return f;}
    static Object value(Class<?> c,String n)throws Exception{return field(c,n).get(null);}
    static void set(Object target,String n,Object v)throws Exception{field(target.getClass(),n).set(target,v);}
    static Object invoke(Class<?> c,String n,Class<?>[] types,Object...args)throws Exception{var m=c.getDeclaredMethod(n,types);m.setAccessible(true);return m.invoke(null,args);}
    static void clear(Class<?> c)throws Exception{invoke(c,"clear",new Class<?>[0]);}
    static void setup(Class<?> c)throws Exception{
        clear(c);MC.queue.clear();MC.sound.active.clear();MC.level=new ClientLevel();MC.player=new Player(1,MC.level);MC.level.entities.put(1,MC.player);MC.connection=new Object();MC.paused=false;net.minecraft.Util.now=1000;
        // Synchronizes the real renderer's session identity and initializes its pause clock.
        invoke(c,"render",new Class<?>[]{RenderLevelStageEvent.class},new RenderLevelStageEvent());Recording.clear();
    }
    static void frame(Vfx2Quality q){ClassicHudConfig.SPEC.loaded=true;ClassicHudConfig.LIGHT_SHIELD_VFX2_QUALITY.set(q);LightShieldVfx2.beginFrame(new RenderLevelStageEvent());}
    static RegisterShadersEvent register(boolean missing,boolean corrupt,boolean complete){var e=new RegisterShadersEvent();e.provider.missing=missing;e.provider.corrupt=corrupt;LightShieldVfx2Material.register(e);if(complete)e.complete();return e;}
    static void ready(){ShaderInstance.fail=false;ShaderInstance.missingUniform="";org.lwjgl.opengl.GL20.linked=1;org.lwjgl.opengl.GL20.inactive="";com.mojang.blaze3d.shaders.Uniform.fail=false;System.clearProperty("academy.vfx2.lightShieldForceFallback");register(false,false,true);yes(LightShieldVfx2Material.available(),"recording-host ready shader");}
    record Snapshot(List<String> data,List<String> types,List<String> states,List<String> flushes,float rotation,long lastRender,int poseDepth){
        boolean enhanced(){return types.stream().anyMatch(t->t.equals("academy_light_shield_vfx2"));}
    }
    static Snapshot snapshot(float rotation,long lastRender,int depth){return new Snapshot(Recording.vertices.stream().map(Recording.Vertex::data).toList(),Recording.vertices.stream().map(v->v.type).toList(),List.copyOf(Recording.states),List.copyOf(Recording.flushes),rotation,lastRender,depth);}
    static Snapshot direct(Class<?> owner,int ticks,long elapsed,boolean observer)throws Exception{
        var caster=observer?new Player(2,MC.level):MC.player;if(observer)MC.level.entities.put(2,caster);
        var context=Arrays.stream(owner.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Context")).findFirst().orElseThrow();
        var ctor=context.getDeclaredConstructor(Player.class,ClientLevel.class,Object.class,String.class,long.class,boolean.class);ctor.setAccessible(true);
        var c=ctor.newInstance(caster,MC.level,MC.connection,"light_shield",1L,true);set(c,"ticks",ticks);set(c,"rotation",17.25F);set(c,"lastRender",elapsed==0?0:elapsed-42);
        var p=(Vec3)invoke(owner,"shieldPosition",new Class<?>[]{Player.class,Player.class},caster,MC.player);set(c,"shieldPosition",p.add(.125,-.25,.375));set(c,"previousShieldPosition",p);
        set(value(owner,"CLOCK"),"elapsed",elapsed);field(owner,"buffers").set(null,MultiBufferSource.immediate(new ByteBufferBuilder(65536)));
        Recording.clear();var poses=new PoseStack();invoke(owner,"renderShield",new Class<?>[]{context,PoseStack.class,float.class,Vec3.class},c,poses,.37F,new Vec3(-7.5,2,11));
        return snapshot((float)field(context,"rotation").get(c),(long)field(context,"lastRender").get(c),poses.depth());
    }
    static String normalizeState(String s){return s.replace("|light_shield_vfx2","|fallback");}
    static void same(Snapshot a,Snapshot b,boolean materialAllowed,String why){
        yes(a.data.equals(b.data),why+": all four position/matrix/UV/RGBA attributes and order exact immutable baseline");
        yes(a.rotation==b.rotation&&a.lastRender==b.lastRender,why+": classic render-tick rotation state exact");
        yes(a.poseDepth==1&&b.poseDepth==1,why+": balanced pose stack");
        yes(a.data.size()==4&&b.data.size()==4,why+": one complete quad, no duplicate or partial fallback");
        if(materialAllowed&&b.enhanced()){
            yes(b.types.size()==4&&b.types.stream().allMatch(t->t.equals("academy_light_shield_vfx2")),why+": complete material substitution");
            yes(b.states.stream().map(LightShieldVfx2IntegrationOracle::normalizeState).toList().equals(a.states),why+": only shader state substituted");
            yes(b.flushes.equals(List.of("academy_light_shield_vfx2")),why+": original single per-quad flush retained");
        }else{
            yes(a.types.equals(b.types)&&a.states.equals(b.states)&&a.flushes.equals(b.flushes),why+": exact original material, states and flush order");
        }
    }
    static ShaderInstance shader(RegisterShadersEvent e){return e.registrations.get(0).candidate();}
    static float uniform(RegisterShadersEvent e,String n){return shader(e).getUniform(n).value;}
    static void geometry()throws Exception{
        groups++;setup(CURRENT);ready();
        for(Vfx2Quality q:Vfx2Quality.values())for(int ticks:new int[]{0,1,5,6,14,15,29,30,120})for(boolean observer:new boolean[]{false,true}){
            frame(q);var a=direct(BASELINE,ticks,1250,observer);var b=direct(CURRENT,ticks,1250,observer);same(a,b,q!=Vfx2Quality.OFF,"quality "+q+" ticks "+ticks+" observer "+observer);
            yes(b.enhanced()==(q!=Vfx2Quality.OFF),"quality controls only material replacement");
            yes(Recording.vertices.stream().allMatch(v->v.a==255&&v.r==255&&v.g==255&&v.b==255),"unused classic frame alpha stays unbound full-white");
        }
        var states=direct(BASELINE,15,0,false).states;yes(states.get(0).contains("blur=false, mipmap=false")&&states.get(0).contains("|translucent_transparency|LEQUAL|COLOR_DEPTH_WRITE|NO_CULL|NO_LIGHTMAP|NO_OVERLAY|PARTICLES_TARGET|"),"actual baseline nearest/LEQUAL/depth-write/alpha/no-cull/particle-target state");
        yes(ClassicMeltdownerStarterTimeline.shieldFrame(14).size()<ClassicMeltdownerStarterTimeline.shieldFrame(15).size()&&ClassicMeltdownerStarterTimeline.shieldFrame(15).size()==ClassicMeltdownerStarterTimeline.shieldFrame(120).size(),"15-tick growth completion");
        yes(ClassicMeltdownerStarterTimeline.shieldFrame(29).rotationSpeed()<ClassicMeltdownerStarterTimeline.shieldFrame(30).rotationSpeed()&&ClassicMeltdownerStarterTimeline.shieldFrame(30).rotationSpeed()==2F,"30-tick rotation ramp completion");
        var local=ClassicMeltdownerStarterTimeline.shieldPosition(new ClassicMeltdownerStarterTimeline.Point(0,0,0),1.62,0,0,true);var remote=ClassicMeltdownerStarterTimeline.shieldPosition(new ClassicMeltdownerStarterTimeline.Point(0,0,0),1.62,0,0,false);yes(remote.x()-local.x()==1.6&&remote.y()==local.y()&&remote.z()==local.z(),"observer retains source +1.6 X placement quirk");
    }
    static void fallback(String name)throws Exception{frame(Vfx2Quality.MEDIUM);same(direct(BASELINE,6,1250,false),direct(CURRENT,6,1250,false),false,name);yes(!Recording.vertices.get(0).type.equals("academy_light_shield_vfx2"),name+": failed enhancement retains classic quad");}
    static void failures()throws Exception{
        groups++;
        register(true,false,true);fallback("missing original texture");
        register(false,true,true);fallback("corrupt/non-original artwork");
        ShaderInstance.fail=true;register(false,false,true);fallback("shader constructor failure");ShaderInstance.fail=false;
        for(String n:List.of("ModelViewMat","ProjMat","ColorModulator","SwirlGain","FlowAmount","VisualPhase")){
            ShaderInstance.missingUniform=n;register(false,false,true);fallback("missing uniform "+n);
        }ShaderInstance.missingUniform="";
        for(String n:List.of("ModelViewMat","ProjMat","ColorModulator","SwirlGain","FlowAmount","VisualPhase","Sampler0")){
            org.lwjgl.opengl.GL20.inactive=n;register(false,false,true);fallback("inactive native uniform/sampler "+n);
        }org.lwjgl.opengl.GL20.inactive="";
        org.lwjgl.opengl.GL20.linked=0;register(false,false,true);fallback("unlinked program");org.lwjgl.opengl.GL20.linked=1;
        ready();com.mojang.blaze3d.shaders.Uniform.fail=true;fallback("uniform update runtime failure");yes(!LightShieldVfx2Material.available(),"runtime uniform failure retires generation");com.mojang.blaze3d.shaders.Uniform.fail=false;
        ready();System.setProperty("academy.vfx2.lightShieldForceFallback","true");fallback("explicit forced fallback");System.clearProperty("academy.vfx2.lightShieldForceFallback");
        var old=register(false,false,false);fallback("pending reload keeps classic");long generation=LightShieldVfx2Material.generation();var missing=register(true,false,false);yes(LightShieldVfx2Material.generation()>generation,"new reload generation advances");old.complete();fallback("stale callback cannot reactivate old shader after failed reload");
        var first=register(false,false,false);var latest=register(false,false,false);first.complete();fallback("stale callback while latest pending");latest.complete();yes(LightShieldVfx2Material.available(),"latest validated generation alone becomes ready");
        var changed=register(false,false,false);changed.registrations.get(0).callback().accept(new ShaderInstance());fallback("callback candidate substitution rejected");
        ready();ClassicHudConfig.SPEC.loaded=false;LightShieldVfx2.beginFrame(new RenderLevelStageEvent());same(direct(BASELINE,6,1250,false),direct(CURRENT,6,1250,false),false,"unloaded config fails to exact classic");ClassicHudConfig.SPEC.loaded=true;
    }
    static void budgetsAndPhase()throws Exception{
        groups++;ready();
        for(var q:List.of(Vfx2Quality.LOW,Vfx2Quality.MEDIUM)){
            int cap=q==Vfx2Quality.LOW?8:16;frame(q);int enhanced=0;
            for(int i=0;i<cap+5;i++){var a=direct(BASELINE,i,2500,false);var b=direct(CURRENT,i,2500,false);same(a,b,i<cap,"same-frame "+q+" admission "+i);if(b.enhanced())enhanced++;yes(b.enhanced()==(i<cap),"same-frame cap overflow retains baseline");}
            yes(enhanced==cap,"exact bounded complete-quad admission");
            var wrong=new RenderLevelStageEvent();wrong.stage=RenderLevelStageEvent.Stage.AFTER_ENTITIES;LightShieldVfx2.beginFrame(wrong);yes(!direct(CURRENT,15,2500,false).enhanced(),"irrelevant stage cannot reset exhausted quota");frame(q);yes(direct(CURRENT,15,2500,false).enhanced(),"next AFTER_PARTICLES frame resets quota");
        }
        frame(Vfx2Quality.LOW);ClassicHudConfig.LIGHT_SHIELD_VFX2_QUALITY.set(Vfx2Quality.MEDIUM);same(direct(BASELINE,15,2500,false),direct(CURRENT,15,2500,false),false,"quality change after frame reset fails closed");
        var e=register(false,false,true);frame(Vfx2Quality.MEDIUM);direct(CURRENT,15,1250,false);yes(uniform(e,"VisualPhase")==1250/6000F&&uniform(e,"FlowAmount")==.18F,"MEDIUM phase uses pause-aware caller clock and bounded flow");
        frame(Vfx2Quality.LOW);direct(CURRENT,15,1250,false);yes(uniform(e,"VisualPhase")==0&&uniform(e,"FlowAmount")==0&&uniform(e,"SwirlGain")==.28F,"LOW static swirl with no flow");
        frame(Vfx2Quality.MEDIUM);var original=privateMaterial(BASELINE);for(long bad:new long[]{-1,Long.MIN_VALUE})yes(LightShieldVfx2.materialOrClassic(original,bad)==original,"negative phase returns exact input before reservation");
        yes(LightShieldVfx2.materialOrClassic(original,Long.MAX_VALUE)!=original,"safe long maximum phase admitted");yes(Float.isFinite(uniform(e,"VisualPhase"))&&uniform(e,"VisualPhase")==((Long.MAX_VALUE%6000L)/6000F),"modulo before float remains bounded without long overflow");
        yes(LightShieldVfx2.materialOrClassic(null,0)==null,"null input does not invent geometry");
        for(int i=0;i<15;i++)yes(LightShieldVfx2.materialOrClassic(original,0)!=original,"invalid phase/null input consumed no admission slot");
        yes(LightShieldVfx2.materialOrClassic(original,0)==original,"full sixteen valid admissions still enforce cap after invalid attempts");
        frame(Vfx2Quality.LOW);register(true,false,true);yes(LightShieldVfx2.materialOrClassic(original,0)==original,"failed readiness does not substitute");register(false,false,true);
        for(int i=0;i<8;i++)yes(LightShieldVfx2.materialOrClassic(original,0)!=original,"failed readiness consumed no same-frame admission slot");
        yes(LightShieldVfx2.materialOrClassic(original,0)==original,"same-frame quota still bounds recovered material");
    }
    static RenderType privateMaterial(Class<?> owner)throws Exception{var material=Arrays.stream(owner.getDeclaredClasses()).filter(c->c.getSimpleName().equals("Material")).findFirst().orElseThrow();return (RenderType)invoke(material,"type",new Class<?>[]{net.minecraft.resources.ResourceLocation.class,boolean.class,boolean.class},net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("academy","textures/effects/mdshield.png"),true,false);}
    static CompoundTag packet(String kind,int id,long token,long nonce){var t=new CompoundTag();t.putString("kind",kind);t.putInt("entity",id);t.putLong("token",token);t.putLong("input",nonce);return t;}
    static void receive(Class<?> owner,CompoundTag tag)throws Exception{invoke(owner,"receive",new Class<?>[]{CompoundTag.class},tag);MC.drain();}
    static long press(Class<?> owner)throws Exception{return (long)invoke(owner,"startLocal",new Class<?>[]{String.class},"light_shield");}
    static void release(Class<?> owner)throws Exception{invoke(owner,"endLocal",new Class<?>[]{String.class},"light_shield");}
    static Snapshot render(Class<?> owner,Vfx2Quality q)throws Exception{frame(q);Recording.clear();var event=new RenderLevelStageEvent();invoke(owner,"render",new Class<?>[]{RenderLevelStageEvent.class},event);return snapshot(0,0,event.poses.depth());}
    static void count(Class<?> owner,int n,String why)throws Exception{yes(render(owner,Vfx2Quality.MEDIUM).data.size()==4*n,why);}
    static void eligibility()throws Exception{
        groups++;ready();setup(CURRENT);count(CURRENT,0,"no packet creates no shield");long nonce=press(CURRENT);count(CURRENT,0,"physical press alone produces no speculative VFX");
        receive(CURRENT,packet("light_shield_start",1,1,nonce+1));count(CURRENT,0,"mismatched local acknowledgement rejected");
        receive(CURRENT,packet("light_shield_start",1,1,nonce));count(CURRENT,1,"accepted shown valid shield owns only original quad");
        receive(CURRENT,packet("light_shield_start",1,1,nonce));count(CURRENT,1,"duplicate accepted token cannot duplicate shield");
        release(CURRENT);count(CURRENT,0,"ordinary release immediately hides held shield");receive(CURRENT,packet("light_shield_end",1,1,nonce));count(CURRENT,0,"matching terminal packet retains hidden shield");
        setup(CURRENT);nonce=press(CURRENT);release(CURRENT);receive(CURRENT,packet("light_shield_start",1,3,nonce));count(CURRENT,0,"matching late start after release establishes only hidden context");
        setup(CURRENT);var remote=new Player(2,MC.level);MC.level.entities.put(2,remote);receive(CURRENT,packet("light_shield_start",2,9,0));count(CURRENT,1,"observer accepted shown valid shield enhanced once");
        remote.removed=true;count(CURRENT,0,"removed observer excluded");remote.removed=false;remote.alive=false;count(CURRENT,0,"dead observer excluded");remote.alive=true;remote.spectator=true;count(CURRENT,0,"spectator observer excluded");remote.spectator=false;
        MC.level.entities.put(2,new Player(2,MC.level));count(CURRENT,0,"same ID replacement invalidates original context");
        setup(CURRENT);nonce=press(CURRENT);invoke(CURRENT,"receive",new Class<?>[]{CompoundTag.class},packet("light_shield_start",1,20,nonce));MC.connection=new Object();MC.drain();count(CURRENT,0,"queued callback cannot cross connection identity");
        setup(CURRENT);nonce=press(CURRENT);invoke(CURRENT,"receive",new Class<?>[]{CompoundTag.class},packet("light_shield_start",1,20,nonce));clear(CURRENT);MC.drain();count(CURRENT,0,"queued callback cannot cross explicit clear epoch");
        setup(CURRENT);nonce=press(CURRENT);receive(CURRENT,packet("light_shield_start",1,30,nonce));MC.level=new ClientLevel();MC.player=new Player(1,MC.level);MC.level.entities.put(1,MC.player);count(CURRENT,0,"world and player cleanup drops accepted contexts");
    }
    static Snapshot observerFlood(Class<?> owner,Vfx2Quality q)throws Exception{
        setup(owner);for(int i=2;i<22;i++){var player=new Player(i,MC.level);player.position=new Vec3(i*4.25,i%4,-i*1.75);MC.level.entities.put(i,player);receive(owner,packet("light_shield_start",i,100+i,0));}
        return render(owner,q);
    }
    static void fullRenderDifferential()throws Exception{
        groups++;ready();for(var q:Vfx2Quality.values()){
            var a=observerFlood(BASELINE,q);var b=observerFlood(CURRENT,q);int cap=q==Vfx2Quality.OFF?0:q==Vfx2Quality.LOW?8:16;
            yes(a.data.size()==80&&b.data.size()==80,"actual accepted twenty observers render twenty complete original quads");
            yes(a.data.equals(b.data),"full render callback all accepted observers match immutable baseline geometry, transform, UV, RGBA and order");
            yes(b.states.stream().map(LightShieldVfx2IntegrationOracle::normalizeState).toList().equals(a.states),"full callback retains original state for every admitted and overflow quad");
            yes(b.types.stream().filter(t->t.equals("academy_light_shield_vfx2")).count()==cap*4,"actual shown/valid observer loop admits only quality cap");
            String original=a.types.get(0);yes(b.flushes.stream().map(t->t.equals("academy_light_shield_vfx2")?original:t).toList().equals(a.flushes),"full callback exact per-quad flush order across enhanced and overflow observers");
            yes(b.poseDepth==1,"full callback balances original outer pose stack");
        }
        System.setProperty("academy.vfx2.lightShieldForceFallback","true");var a=observerFlood(BASELINE,Vfx2Quality.MEDIUM);var b=observerFlood(CURRENT,Vfx2Quality.MEDIUM);
        yes(a.data.equals(b.data)&&a.types.equals(b.types)&&a.states.equals(b.states)&&a.flushes.equals(b.flushes),"full callback forced failure exact immutable baseline across all twenty accepted observers");System.clearProperty("academy.vfx2.lightShieldForceFallback");
    }
    static void pauseAndCommands()throws Exception{
        groups++;ready();var e=register(false,false,true);setup(CURRENT);long nonce=press(CURRENT);receive(CURRENT,packet("light_shield_start",1,40,nonce));net.minecraft.Util.now=2250;render(CURRENT,Vfx2Quality.MEDIUM);float phase=uniform(e,"VisualPhase");yes(phase==1250/6000F,"actual full callback advances caller pause clock");
        MC.paused=true;net.minecraft.Util.now=9000;render(CURRENT,Vfx2Quality.MEDIUM);yes(uniform(e,"VisualPhase")==phase,"pause freezes visual phase despite wall elapsed");net.minecraft.Util.now=12000;render(CURRENT,Vfx2Quality.MEDIUM);yes(uniform(e,"VisualPhase")==phase,"repeated paused callback freezes phase");
        MC.paused=false;net.minecraft.Util.now=12100;render(CURRENT,Vfx2Quality.MEDIUM);yes(uniform(e,"VisualPhase")==1350/6000F,"resume excludes paused wall duration");
        setup(CURRENT);nonce=press(CURRENT);receive(CURRENT,packet("light_shield_start",1,1,nonce));render(CURRENT,Vfx2Quality.MEDIUM);yes(uniform(e,"VisualPhase")==0,"world/session cleanup resets classic caller phase");
        var commands=new RegisterClientCommandsEvent();LightShieldVfx2.commands(commands);int saves=ClassicHudConfig.SPEC.saves;for(var q:Vfx2Quality.values()){yes(commands.dispatcher.execute("aca_vfx2 light_shield "+q.name().toLowerCase(java.util.Locale.ROOT))==1,"quality client command succeeds");yes(ClassicHudConfig.LIGHT_SHIELD_VFX2_QUALITY.get()==q,"quality command sets shield enum only");}yes(ClassicHudConfig.SPEC.saves==saves+3,"every quality command requests client config save");
        yes(commands.dispatcher.execute("aca_vfx2 light_shield status")==1,"status command succeeds");yes(MC.player.messages.size()==4,"client-only quality and status feedback");
    }
    public static void main(String[] args)throws Exception{geometry();failures();budgetsAndPhase();eligibility();fullRenderDifferential();pauseAndCommands();
        yes(value(LightShieldMaterialPreview.class,"captured")==null,"disabled diagnostic stores no snapshot");Recording.clear();LightShieldMaterialPreview.render(new RenderLevelStageEvent());yes(Recording.vertices.isEmpty(),"disabled diagnostic emits no historical quad");
        System.out.println("PASS|"+groups+"|"+assertions);}
}
'''


PREVIEW_HARNESS = r'''
package oracle;
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.ClassicHudConfig.Vfx2Quality;
import cn.academy.port.client.*;
import net.minecraft.client.renderer.*;
import net.minecraft.client.multiplayer.*;
import net.minecraft.world.entity.player.*;
import net.minecraft.world.phys.*;
import net.neoforged.neoforge.client.event.*;
import org.lwjgl.glfw.GLFW;
import static oracle.LightShieldVfx2IntegrationOracle.*;

public class LightShieldMaterialPreviewOracle {
    static int checks;
    static void check(boolean v,String why){checks++;if(!v)throw new AssertionError(why);}
    static Object captured()throws Exception{return value(LightShieldMaterialPreview.class,"captured");}
    static Object context()throws Exception{return ((java.util.Map<?,?>)value(CURRENT,"SHIELDS")).get(1);}
    static void mature(int ticks)throws Exception{set(context(),"ticks",ticks);}
    static void key(int which){LightShieldMaterialPreview.key(new InputEvent.Key(which,GLFW.GLFW_PRESS));}
    static Snapshot historical()throws Exception{Recording.clear();var event=new RenderLevelStageEvent();LightShieldMaterialPreview.render(event);return snapshot(0,0,event.poses.depth());}
    static Snapshot capture()throws Exception{
        ready();setup(CURRENT);MC.screen=null;MC.windowActive=true;MC.cameraEntity=null;MC.singleplayer=true;AcademyClient.state.category="meltdowner";
        long nonce=press(CURRENT);receive(CURRENT,packet("light_shield_start",1,1,nonce));mature(60);net.minecraft.Util.now=1750;var actual=render(CURRENT,Vfx2Quality.OFF);
        check(captured()!=null,"one actual mature shown+valid local quad captured after production rendering");return actual;
    }
    static void stop()throws Exception{release(CURRENT);receive(CURRENT,packet("light_shield_end",1,1,1));}
    static void captureAndDiff()throws Exception{
        setup(CURRENT);long nonce=press(CURRENT);check(captured()==null,"physical press cannot create diagnostic sample");
        receive(CURRENT,packet("light_shield_start",1,1,nonce));mature(59);render(CURRENT,Vfx2Quality.OFF);check(captured()==null,"immature shield cannot create mature diagnostic sample");
        mature(60);MC.screen=new Object();render(CURRENT,Vfx2Quality.OFF);check(captured()==null,"menu suppresses capture");MC.screen=null;
        MC.windowActive=false;render(CURRENT,Vfx2Quality.OFF);check(captured()==null,"focus suppresses capture");MC.windowActive=true;
        MC.singleplayer=false;render(CURRENT,Vfx2Quality.OFF);check(captured()==null,"multiplayer suppresses isolated diagnostic capture");MC.singleplayer=true;
        var natural=capture();Object sample=captured();key(GLFW.GLFW_KEY_F8);check(historical().data().isEmpty(),"F8 cannot double-render a currently held shield");
        var sampleClass=sample.getClass();var position=field(sampleClass,"position");position.setAccessible(true);Object frozenPosition=position.get(sample);
        MC.player.position=new Vec3(91,7,-31);MC.player.yaw=-112;MC.player.pitch=37;net.minecraft.Util.now=4000;render(CURRENT,Vfx2Quality.OFF);
        check(captured()==sample&&position.get(captured())==frozenPosition,"historical snapshot stays immutable and is not recaptured on later live frames");
        Object live=context();long liveLast=(long)field(live.getClass(),"lastRender").get(live);float liveRotation=(float)field(live.getClass(),"rotation").get(live);
        stop();check(render(CURRENT,Vfx2Quality.OFF).data().isEmpty(),"normal held shield ends without diagnostic activation");key(GLFW.GLFW_KEY_F8);
        var classic=historical();same(natural,classic,false,"staged original versus naturally emitted frozen quad");
        check(ClassicHudConfig.LIGHT_SHIELD_VFX2_QUALITY.get()==Vfx2Quality.OFF,"staging original keeps live enum unchanged");key(GLFW.GLFW_KEY_F10);var candidate=historical();same(natural,candidate,true,"staged LOW versus exact same frozen quad");check(candidate.enhanced(),"F10 selects actual production LOW material even when live setting is OFF");
        check(ClassicHudConfig.LIGHT_SHIELD_VFX2_QUALITY.get()==Vfx2Quality.OFF,"F10 material selection never changes live client enum");
        var readyField=field(LightShieldVfx2Material.class,"ready");Object loaded=readyField.get(null);var loadedClass=loaded.getClass();var shaderField=field(loadedClass,"shader");ShaderInstance shader=(ShaderInstance)shaderField.get(loaded);
        check(shader.getUniform("VisualPhase").value==0&&shader.getUniform("FlowAmount").value==0,"staged LOW holds fixed phase zero and no flow");
        net.minecraft.Util.now=999999;var again=historical();check(again.data().equals(candidate.data())&&shader.getUniform("VisualPhase").value==0,"historical transform and material phase stay fixed as wall clock advances");
        check((long)field(live.getClass(),"lastRender").get(live)==liveLast&&(float)field(live.getClass(),"rotation").get(live)==liveRotation,"historical rendering never changes original context timing or rotation");
        check(((java.util.List<?>)value(CURRENT,"RAYS")).isEmpty()&&((java.util.List<?>)value(CURRENT,"PARTICLES")).isEmpty()&&MC.queue.isEmpty(),"diagnostic creates no ray, particle or packet queue work");
        MC.options.hideGui=true;var hud=new RenderGuiEvent.Post();LightShieldMaterialPreview.hud(hud);
        check(hud.graphics.labels.contains("STAGED IN-ENGINE MATERIAL PREVIEW"),"F1 retains required staged disclosure");
        check(hud.graphics.labels.stream().anyMatch(x->x.contains("frozen LOW (no flow)"))&&hud.graphics.labels.stream().anyMatch(x->x.contains("NO DEFENSE")),"HUD labels fixed phase and no defense");
        System.setProperty("academy.vfx2.lightShieldForceFallback","true");same(natural,historical(),false,"staged LOW failure retains exact original quad");System.clearProperty("academy.vfx2.lightShieldForceFallback");
        key(GLFW.GLFW_KEY_F10);same(natural,historical(),false,"F10 returns to same frozen original material");key(GLFW.GLFW_KEY_F8);check(historical().data().isEmpty(),"F8 exits historical preview");
    }
    static void visibilityAndFences()throws Exception{
        capture();stop();key(GLFW.GLFW_KEY_F8);check(historical().data().size()==4,"historical preview starts deliberately after natural hold ends");Object original=captured();
        MC.screen=new Object();check(historical().data().isEmpty(),"menu hides staged preview");MC.screen=null;check(historical().data().isEmpty()&&captured()==original,"menu return retains sample but requires fresh F8");key(GLFW.GLFW_KEY_F8);check(historical().data().size()==4,"F8 resumes after menu");
        MC.windowActive=false;check(historical().data().isEmpty(),"focus loss hides staged preview");MC.windowActive=true;check(historical().data().isEmpty(),"focus return requires fresh F8");key(GLFW.GLFW_KEY_F8);
        MC.cameraEntity=new Player(8,MC.level);check(historical().data().isEmpty(),"camera change hides historical preview");MC.cameraEntity=null;check(historical().data().isEmpty(),"camera return requires fresh F8");
        key(GLFW.GLFW_KEY_F8);press(CURRENT);check(historical().data().isEmpty(),"new physical hold hides historical preview without modifying live input");release(CURRENT);
        for(int mode=0;mode<8;mode++){
            capture();stop();Object sample=captured();
            switch(mode){case 0->AcademyClient.state.presets.selected++;case 1->AcademyClient.state.presets.version++;case 2->AcademyClient.epoch++;case 3->MC.connection=new Object();case 4->MC.level=new ClientLevel();case 5->MC.player=new Player(1,MC.level);case 6->AcademyClient.state.category="electromaster";case 7->register(false,false,true);}
            LightShieldMaterialPreview.tick(new ClientTickEvent.Post());check(captured()==null,"session/world/player/preset/category/resource fence clears snapshot mode "+mode);
        }
        capture();clear(CURRENT);check(captured()==null,"explicit production clear removes diagnostic snapshot");
        ready();setup(CURRENT);var remote=new Player(2,MC.level);MC.level.entities.put(2,remote);receive(CURRENT,packet("light_shield_start",2,9,0));Object remoteContext=((java.util.Map<?,?>)value(CURRENT,"SHIELDS")).get(2);set(remoteContext,"ticks",30);render(CURRENT,Vfx2Quality.OFF);check(captured()==null,"observer production shield cannot become local diagnostic snapshot");
        setup(CURRENT);long nonce=press(CURRENT);release(CURRENT);receive(CURRENT,packet("light_shield_start",1,1,nonce));mature(30);render(CURRENT,Vfx2Quality.OFF);check(captured()==null,"late hidden context cannot become diagnostic snapshot");
    }
    static void existingParticles(Class<?> owner,int count)throws Exception{
        var kind=java.util.Arrays.stream(owner.getDeclaredClasses()).filter(c->c.getSimpleName().equals("MdParticle")).findFirst().orElseThrow();var ctor=kind.getDeclaredConstructor(Vec3.class,Vec3.class);ctor.setAccessible(true);
        @SuppressWarnings("unchecked") var particles=(java.util.List<Object>)value(owner,"PARTICLES");
        for(int i=0;i<count;i++){var particle=ctor.newInstance(new Vec3(13+i*.03,5.1+i*.01,-8.3),new Vec3(.01,.02,.03));set(particle,"previous",new Vec3(12.9+i*.03,5+i*.01,-8.4));set(particle,"size",.05F+i*.0001F);set(particle,"alpha",.3+i*.002);set(particle,"life",30);set(particle,"age",i%3==0?1:i%3==1?20:35);particles.add(particle);}
    }
    static Snapshot composition(Class<?> owner,int count)throws Exception{
        ready();setup(owner);MC.screen=null;MC.windowActive=true;MC.cameraEntity=null;MC.singleplayer=true;AcademyClient.state.category="meltdowner";
        long nonce=press(owner);receive(owner,packet("light_shield_start",1,1,nonce));var context=((java.util.Map<?,?>)value(owner,"SHIELDS")).get(1);set(context,"ticks",60);existingParticles(owner,count);
        RenderLevelStageEvent.Camera.quaternion=new org.joml.Quaternionf().rotationY(.7F);net.minecraft.Util.now=1750;return render(owner,Vfx2Quality.OFF);
    }
    static void compositionDiff()throws Exception{
        var baseline=composition(BASELINE,3);var natural=composition(CURRENT,3);
        check(baseline.data().equals(natural.data())&&baseline.types().equals(natural.types())&&baseline.states().equals(natural.states())&&baseline.flushes().equals(natural.flushes()),"composition capture preserves immutable OFF baseline including all actual existing particle quads");
        Object sample=captured();check(sample!=null,"one tick60 native shield-plus-particle composition committed after full original emission");Object frozen=field(sample.getClass(),"particles").get(sample);check(((java.util.List<?>)frozen).size()==3,"exact three existing emitted particles captured");
        boolean immutable=false;try{((java.util.List<?>)frozen).clear();}catch(UnsupportedOperationException expected){immutable=true;}check(immutable,"historical particle list immutable");
        stop();((java.util.List<?>)value(CURRENT,"PARTICLES")).clear();RenderLevelStageEvent.Camera.quaternion=new org.joml.Quaternionf().rotationY(-.9F);key(GLFW.GLFW_KEY_F8);var historical=historical();
        check(historical.data().equals(natural.data())&&historical.types().equals(natural.types())&&historical.states().equals(natural.states())&&historical.flushes().equals(natural.flushes()),"staged composition exactly replays original shield then particles, frozen interpolation, size, resolved alpha and camera rotation");
        key(GLFW.GLFW_KEY_F10);var low=historical();check(low.data().equals(natural.data())&&low.states().stream().map(LightShieldVfx2IntegrationOracle::normalizeState).toList().equals(natural.states()),"LOW composition changes only shield shader and preserves every original particle state");
        check(low.types().subList(4,low.types().size()).equals(natural.types().subList(4,natural.types().size()))&&low.flushes().subList(1,low.flushes().size()).equals(natural.flushes().subList(1,natural.flushes().size())),"particle material/depth/write/flush order remains exact classic");
        key(GLFW.GLFW_KEY_F9);var none=historical();check(none.data().equals(natural.data().subList(0,4))&&none.data().size()==4,"F9 OFF removes only staged historical particles");
        var hud=new RenderGuiEvent.Post();LightShieldMaterialPreview.hud(hud);check(hud.graphics.labels.stream().anyMatch(x->x.contains("Historical particles OFF (3)")),"HUD discloses particle OFF and immutable count");
        key(GLFW.GLFW_KEY_F9);check(historical().data().equals(natural.data()),"F9 ON restores same frozen composition");hud=new RenderGuiEvent.Post();LightShieldMaterialPreview.hud(hud);check(hud.graphics.labels.stream().anyMatch(x->x.contains("Historical particles ON (3)")),"HUD discloses particle ON and count");
        check(((java.util.List<?>)value(CURRENT,"PARTICLES")).isEmpty(),"historical replay never inserts particles into live ownership");
        composition(CURRENT,64);sample=captured();check(sample!=null&&((java.util.List<?>)field(sample.getClass(),"particles").get(sample)).size()==64,"maximum64 existing particles allowed as one immutable snapshot");
        composition(CURRENT,65);check(captured()==null,"more than64 existing particles refuse entire snapshot without trimming or generating particles");stop();key(GLFW.GLFW_KEY_F8);check(historical().data().isEmpty(),"refused composition cannot stage a shield-only substitute");
        RenderLevelStageEvent.Camera.quaternion=new org.joml.Quaternionf();
    }
    public static void main(String[] args)throws Exception{captureAndDiff();visibilityAndFences();compositionDiff();System.out.println("PASS|"+checks+"|"+assertions);}
}
'''

NATIVE_CONFIG = r'''
import cn.academy.port.ClassicHudConfig;
import cn.academy.port.ClassicHudConfig.Vfx2Quality;
import com.electronwill.nightconfig.core.CommentedConfig;
import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.toml.TomlParser;
import java.nio.file.*;
import java.util.*;
public class LightShieldNativeConfigOracle {
    static int assertions;
    static void yes(boolean value,String why){assertions++;if(!value)throw new AssertionError(why);}
    public static void main(String[] args)throws Exception {
        String key="vfx2.lightShieldQuality";
        yes(!ClassicHudConfig.SPEC.isLoaded(),"actual unloaded spec");
        for(Object bad:List.of("HIGH","", "ultra", "MEDIUM ", -1, 999, Double.NaN, List.of("LOW"))) {
            var config=CommentedConfig.inMemory();ClassicHudConfig.SPEC.correct(config);config.set(key,bad);
            yes(!ClassicHudConfig.SPEC.isCorrect(config),"invalid enum rejected "+bad);
            ClassicHudConfig.SPEC.correct(config);yes(config.get(key).toString().equals("OFF"),"invalid shield enum corrected to actual OFF default "+bad);
            yes(config.get("vfx2.quality").toString().equals("LOW"),"Railgun conservative default retained");
            yes(ClassicHudConfig.SPEC.isCorrect(config),"corrected actual spec valid");
        }
        var missing=CommentedConfig.inMemory();ClassicHudConfig.SPEC.correct(missing);missing.remove(key);ClassicHudConfig.SPEC.correct(missing);
        yes(missing.get(key).toString().equals("OFF"),"missing shield quality receives actual OFF default");
        Path file=Path.of("light-shield-client-persistence.toml");
        for(var quality:Vfx2Quality.values()) {
            var parsed=new TomlParser().parse("[vfx2]\nlightShieldQuality = \""+quality.name()+"\"\nquality = \"MEDIUM\"\n");
            ClassicHudConfig.SPEC.correct(parsed);yes(parsed.get(key).toString().equals(quality.name()),"valid TOML shield enum retained "+quality);
            try(var output=CommentedFileConfig.builder(file).sync().build()){output.load();ClassicHudConfig.SPEC.correct(output);output.set(key,quality.name());output.set("vfx2.quality","MEDIUM");output.save();}
            try(var input=CommentedFileConfig.builder(file).sync().build()){input.load();yes(ClassicHudConfig.SPEC.isCorrect(input),"serialized file satisfies actual NeoForge spec");yes(input.get(key).toString().equals(quality.name()),"persisted client shield quality survives file reopen "+quality);yes(input.get("vfx2.quality").toString().equals("MEDIUM"),"shield persistence does not change Railgun quality");}
        }
        boolean malformed=false;try{new TomlParser().parse("[vfx2]\nlightShieldQuality = [\n");}catch(RuntimeException expected){malformed=true;}
        yes(malformed,"malformed TOML rejected by actual pinned parser; game-level file recovery is outside this host");
        System.out.println("PASS|"+assertions);
    }
}
'''


def baseline_source(root, name, expected_sha256):
    """Verify raw archived .31 source and prefer the equivalent pinned Git object."""
    fixture = root / BASELINE_FIXTURES / (name + '.java.txt')
    archived = fixture.read_bytes()
    assert digest(archived) == expected_sha256, 'immutable .31 baseline fixture SHA256 mismatch: ' + name
    git_path = JAVA + 'client/' + name + '.java'
    try:
        result = subprocess.run(['git', 'show', BASELINE + ':' + git_path], cwd=root, capture_output=True)
    except FileNotFoundError:
        result = None
    if result is not None and result.returncode == 0:
        assert digest(result.stdout) == expected_sha256, 'immutable .31 Git source SHA256 mismatch: ' + name
        assert result.stdout == archived, 'archived .31 fixture differs from pinned Git source: ' + name
        return result.stdout.decode('utf-8'), 'pinned-git-show'
    return archived.decode('utf-8'), 'sha256-pinned-archive-fixture'


def source_guards(root, sources, baseline_effects, baseline_timeline):
    classic = sources['client/ClassicMeltdownerStarterEffects.java']
    old = 'drawQuad(Material.type(MD_SHIELD, true, false), poses.last().pose(), ClassicMeltdownerStarterTimeline.billboard(frame.size(), true), 255, 255, 255, 1);'
    new = 'drawQuad(LightShieldVfx2.materialOrClassic(Material.type(MD_SHIELD, true, false), CLOCK.elapsed()), poses.last().pose(), ClassicMeltdownerStarterTimeline.billboard(frame.size(), true), 255, 255, 255, 1);'
    assert baseline_effects.count(old) == 1 and classic.count(new) == 1, 'single original Light Shield quad material hook required'
    capture_hook = '\n        if (context.shown && context.valid(Minecraft.getInstance()) && LightShieldMaterialPreview.wantsSample(context.caster, context.ticks))\n            LightShieldMaterialPreview.freeze(context.caster, context.ticks, position, context.caster.getYHeadRot(), context.caster.getXRot(), context.rotation, frame, CLOCK.elapsed(), poses.last().pose());'
    clear_hook = '\n        LightShieldMaterialPreview.clear();'
    begin_hook = '\n        LightShieldMaterialPreview.beginCaptureFrame(PARTICLES.size());'
    particle_hook = '\n                if (LightShieldMaterialPreview.wantsParticle()) LightShieldMaterialPreview.freezeParticle(position, particle.size, particle.alpha * ClassicMeltdownerStarterTimeline.particleAlpha(particle.age, particle.life), event.getCamera().rotation());'
    finish_hook = '\n            LightShieldMaterialPreview.finishCaptureFrame();'
    assert classic.count(begin_hook) == classic.count(particle_hook) == classic.count(finish_hook) == 1, 'exact diagnostic frame/after-particle-emission/commit hooks required'
    assert classic.count(capture_hook) == classic.count(clear_hook) == 1, 'exact gated capture/clear hooks required'
    helper_start = classic.index('    /** Diagnostic only: never constructs')
    helper_end = classic.index('    private static void renderRay(', helper_start)
    helper = classic[helper_start:helper_end]
    assert digest(helper) == 'd891c940bfe375e6b8a180485bd4a8dbfc5eba29e9029ce9994ddce9e4399701', 'diagnostic renderer helpers changed beyond reviewed frozen drawQuad/hold-read path'
    normalized = classic.replace(capture_hook, '').replace(clear_hook, '').replace(begin_hook, '').replace(particle_hook, '').replace(finish_hook, '').replace(helper, '').replace(new, old)
    assert normalized == baseline_effects, 'actual accepted/shown/valid lifecycle, packet path, sounds, scatter/rays/particles or classic rendering changed beyond one material decision and exact disabled diagnostic hooks'
    wrapper = block(classic, 'private static void renderShield(')
    assert wrapper.index(new) < wrapper.index('LightShieldMaterialPreview.freeze('), 'diagnostic sample must follow actual production emission'
    preview = sources['client/LightShieldMaterialPreview.java']
    assert 'Boolean.getBoolean("academy.vfx2.lightShieldMaterialPreview")' in preview, 'diagnostic must be explicitly opt-in'
    wants = block(preview, 'static boolean wantsSample(')
    assert wants.index('if (!ENABLED) return false;') < wants.index('Minecraft mc'), 'disabled diagnostic must not inspect or allocate capture state'
    assert 'captured == null' in wants and 'caster == mc.player' in wants and 'ticks == 60' in wants, 'at most one tick60 local naturally rendered composition sample'
    assert 'hideGui' not in block(preview, 'public static void hud('), 'F1 must retain staged disclosure'
    assert 'ClassicHudConfig.LIGHT_SHIELD_VFX2_QUALITY.set(' not in preview, 'historical LOW choice must not change live client config'

    assert sources['client/ClassicMeltdownerStarterTimeline.java'] == baseline_timeline, 'original classic timeline including 15/30-tick ramps, unused alpha, +1.6 observer X and pause clock must remain exact'
    module = sources['client/LightShieldVfx2.java']
    assert '@SubscribeEvent(priority = EventPriority.HIGH)' in module, 'per-frame quota must reset before normal classic callback'
    assert 'Stage.AFTER_PARTICLES' in block(module, 'public static void beginFrame('), 'quota reset must be limited to original render stage'
    config = sources['ClassicHudConfig.java']
    assert re.search(r'enum Vfx2Quality\s*\{\s*OFF\s*,\s*LOW\s*,\s*MEDIUM\s*}', config), 'shield quality must use bounded actual enum'
    assert re.search(r'LIGHT_SHIELD_VFX2_QUALITY\s*=.*?defineEnum\("lightShieldQuality",\s*Vfx2Quality.OFF\)', config, re.S), 'separate client shield enum must default OFF'
    assert '.defineEnum("quality",Vfx2Quality.LOW)' in config, 'Railgun .31 default must remain unchanged'
    assert not re.search(r'net\.minecraft\.client|LightShieldVfx2', config), 'common config cannot initialize client renderer'
    academy = (root / (JAVA + 'AcademyCraft.java')).read_text()
    assert 'container.registerConfig(ModConfig.Type.CLIENT,ClassicHudConfig.SPEC)' in academy, 'client-only config registration required'
    for name, source in sources.items():
        if '/LightShieldVfx2' not in name and '/LightShieldMaterialPreview' not in name:
            continue
        code = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
        assert not re.search(r'cn\.academy\.port\.(?:highpower|skill|ability|network|AcademyNetwork)', code), 'VFX2 reaches gameplay/server path: ' + name
        assert not re.search(r'\b(?:hurt|setHealth|consumeCp|consumeCP|sendToServer|sendToPlayer|playSound|addParticle|broadcast)\s*\(', code), 'VFX2 changes gameplay/sound/particles: ' + name
        assert not re.search(r'System\.(?:currentTimeMillis|nanoTime)|Util\.getMillis|new (?:Thread|Timer)|mc\.execute', code), 'VFX2 must consume original caller phase without new clock/queue'
    # Exact same immutable guards apply with or without Git metadata.
    historical = {}
    for path, expected_sha256 in HISTORICAL_SHA256.items():
        actual = (root / path).read_bytes()
        assert digest(actual) == expected_sha256, 'historical Railgun/closed Meltdowner guard changed: ' + path
        historical[path] = expected_sha256
    return historical


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path)
    parser.add_argument('--native-config-classpath', type=Path, help='existing pinned runtime classpath file; no Gradle')
    parser.add_argument('--pinned-minecraft-sources', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    names = ['ClassicHudConfig.java', 'client/ClassicMeltdownerStarterEffects.java', 'client/ClassicMeltdownerStarterTimeline.java',
             'client/LightShieldVfx2.java', 'client/LightShieldVfx2Material.java', 'client/LightShieldVfx2RenderTypes.java', 'client/LightShieldMaterialPreview.java']
    sources = {name: (root / (JAVA + name)).read_text() for name in names}
    baseline_effects, effects_origin = baseline_source(root, 'ClassicMeltdownerStarterEffects', BASELINE_EFFECTS_SHA256)
    baseline_timeline, timeline_origin = baseline_source(root, 'ClassicMeltdownerStarterTimeline', BASELINE_TIMELINE_SHA256)
    assert digest(baseline_effects) == BASELINE_EFFECTS_SHA256, 'immutable .31 effects source SHA256 mismatch'
    assert digest(baseline_timeline) == BASELINE_TIMELINE_SHA256, 'immutable .31 timeline source SHA256 mismatch'
    assert digest((root / 'src/main/resources/assets/academy/textures/effects/mdshield.png').read_bytes()) == TEXTURE_SHA256, 'original source artwork changed'
    historical = source_guards(root, sources, baseline_effects, baseline_timeline)
    cp_file = args.native_config_classpath or root / 'run-highpower-clean-loader/instance/runtime-classpath-colon.txt'
    assert cp_file.is_file(), 'existing pinned native classpath is required for native JOML and NeoForge config; no build is launched'
    cp = cp_file.read_text().strip()
    jars = [Path(p) for p in cp.split(os.pathsep)]
    joml = next((p for p in jars if p.name == 'joml-1.10.5.jar'), None)
    assert joml is not None and joml.is_file(), 'pinned native JOML1.10.5 jar missing'
    assert digest(joml.read_bytes()) == JOML_SHA256, 'native JOML1.10.5 immutable jar SHA256 mismatch'
    archive = args.pinned_minecraft_sources or root / 'build/moddev/artifacts/neoforge-21.1.252-sources.jar'
    with ZipFile(archive) as jar:
        native_type = jar.read('net/minecraft/client/renderer/RenderType.java').decode()
        native_state = jar.read('net/minecraft/client/renderer/RenderStateShard.java').decode()
    assert digest(native_type) == NATIVE_RENDER_TYPE_SHA256 and digest(native_state) == NATIVE_RENDER_STATE_SHA256, 'pinned Minecraft1.21.1 render-state source SHA256 mismatch'
    assert 'depthTestState = RenderStateShard.LEQUAL_DEPTH_TEST;' in native_type and 'writeMaskState = RenderStateShard.COLOR_DEPTH_WRITE;' in native_type, 'recording defaults must match pinned actual CompositeState builder'
    transparency = block(native_state, 'public static final RenderStateShard.TransparencyStateShard TRANSLUCENT_TRANSPARENCY')
    assert transparency.count('GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA') == 2 and 'GlStateManager.SourceFactor.SRC_ALPHA' in transparency and 'GlStateManager.SourceFactor.ONE' in transparency, 'pinned original translucent factors changed'
    toolchain = jdk(root)
    mutations = []
    with tempfile.TemporaryDirectory(prefix='light-shield-vfx2-integration-') as directory:
        work = Path(directory)
        generated, classes = work / 'sources', work / 'classes'
        all_sources = dict(STUBS)
        all_sources.update({'cn/academy/port/' + name: source for name, source in sources.items()})
        all_sources['oracle/LightShieldVfx2IntegrationOracle.java'] = HARNESS
        all_sources['oracle/LightShieldMaterialPreviewOracle.java'] = PREVIEW_HARNESS
        all_sources['cn/academy/port/client/ClassicMeltdownerStarterEffectsBaseline.java'] = baseline_effects.replace('ClassicMeltdownerStarterEffects', 'ClassicMeltdownerStarterEffectsBaseline').replace('ClassicMeltdownerStarterTimeline', 'ClassicMeltdownerStarterTimelineBaseline')
        all_sources['cn/academy/port/client/ClassicMeltdownerStarterTimelineBaseline.java'] = baseline_timeline.replace('ClassicMeltdownerStarterTimeline', 'ClassicMeltdownerStarterTimelineBaseline')
        for name, source in all_sources.items():
            path = generated / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(source)
        paths = sorted(generated.rglob('*.java'))
        run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', joml, '-d', classes, *paths])
        launch = [toolchain / 'bin/java', '-Djava.awt.headless=true', '-Doracle.root=' + str(root), '-cp', str(classes) + os.pathsep + str(joml), 'oracle.LightShieldVfx2IntegrationOracle']
        result = run(launch).strip().split('|')
        assert result[0] == 'PASS', result
        preview_launch = [toolchain / 'bin/java', '-Djava.awt.headless=true', '-Dacademy.vfx2.lightShieldMaterialPreview=true', '-Doracle.root=' + str(root), '-cp', str(classes) + os.pathsep + str(joml), 'oracle.LightShieldMaterialPreviewOracle']
        preview_result = run(preview_launch).strip().split('|')
        assert preview_result[0] == 'PASS', preview_result
        controls = [
            ('extraShieldQuad', 'client/ClassicMeltdownerStarterEffects.java',
             lambda s: s.replace('        drawQuad(LightShieldVfx2.materialOrClassic', '        drawQuad(Material.type(MD_SHIELD, true, false), poses.last().pose(), ClassicMeltdownerStarterTimeline.billboard(frame.size(), true), 255, 255, 255, 1);\n        drawQuad(LightShieldVfx2.materialOrClassic'), 'all four position/matrix/UV/RGBA'),
            ('shieldAlphaBound', 'client/ClassicMeltdownerStarterEffects.java',
             lambda s: s.replace('ClassicMeltdownerStarterTimeline.billboard(frame.size(), true), 255, 255, 255, 1);', 'ClassicMeltdownerStarterTimeline.billboard(frame.size(), true), 255, 255, 255, frame.unusedAlpha());'), 'all four position/matrix/UV/RGBA'),
            ('shaderDepthWritesRemoved', 'client/LightShieldVfx2RenderTypes.java',
             lambda s: s.replace('.setWriteMaskState(COLOR_DEPTH_WRITE)', '.setWriteMaskState(COLOR_WRITE)'), 'only shader state substituted'),
            ('sameFrameCapRaised', 'client/LightShieldVfx2.java',
             lambda s: s.replace('case LOW -> 8', 'case LOW -> 9').replace('case MEDIUM -> 16', 'case MEDIUM -> 17'), 'same-frame LOW admission 8: exact original material'),
            ('observerPlacementChanged', 'client/ClassicMeltdownerStarterTimeline.java',
             lambda s: s.replace('localPlayer ? 0 : 1.6', 'localPlayer ? 0 : 0'), 'all four position/matrix/UV/RGBA'),
            ('growthRampChanged', 'client/ClassicMeltdownerStarterTimeline.java',
             lambda s: s.replace('ticks / 15F', 'ticks / 16F'), 'all four position/matrix/UV/RGBA'),
        ]
        for label, name, mutate, expected in controls:
            original = sources[name]
            changed = mutate(original)
            assert changed != original, 'mutation must alter a temporary production copy: ' + label
            path = generated / ('cn/academy/port/' + name)
            path.write_text(changed)
            run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', str(classes) + os.pathsep + str(joml), '-d', classes, path])
            broken = subprocess.run([str(x) for x in launch], text=True, capture_output=True)
            assert broken.returncode != 0 and expected in broken.stderr, 'mutation must fail its named independent assertion: ' + label + '\n' + broken.stderr
            mutations.append(label)
            path.write_text(original)
            run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', str(classes) + os.pathsep + str(joml), '-d', classes, path])
        native = work / 'LightShieldNativeConfigOracle.java'
        native.write_text(NATIVE_CONFIG)
        native_classes = work / 'native-config'
        run([toolchain / 'bin/javac', '--release', '21', '-encoding', 'UTF-8', '-cp', cp, '-d', native_classes, root / (JAVA + 'ClassicHudConfig.java'), native])
        output = run([toolchain / 'bin/java', '-cp', str(native_classes) + os.pathsep + cp, native.stem], cwd=work)
        native_result = next((line for line in output.splitlines() if line.startswith('PASS|')), '').split('|')
        assert native_result[0] == 'PASS', output
    report = {
        'result': 'PASS', 'recordingGroups': int(result[1]), 'recordingAssertions': int(result[2]),
        'stagedPreviewAssertions': int(preview_result[1]) + int(preview_result[2]),
        'stagedPreview': {'propertyDefaultOff': True, 'oneNaturallyRenderedLocalTick60CompositionSnapshot': True, 'maximumExistingParticles': 64, 'immutableOriginalParticleQuadsAndCameraRotation': True, 'exactShieldThenParticleOrder': True, 'f9HistoricalParticlesOnly': True, 'noGeneratedParticles': True, 'onlyAfterLocalHoldEnds': True, 'frozenOriginalAndActualLowGeometryDifferential': True, 'frozenLowPhaseNoFlow': True, 'noLiveConfigOrContextMutation': True, 'f8ToggleF10Material': True, 'f1Disclosure': True, 'sessionWorldPlayerPresetCategoryResourceFences': True, 'menuFocusCameraRequireDeliberateResume': True},
        'baselineCommit': BASELINE, 'baselineEffectsSha256': BASELINE_EFFECTS_SHA256,
        'baselineTimelineSha256': BASELINE_TIMELINE_SHA256,
        'baselineObtainedByPinnedGitShow': effects_origin == timeline_origin == 'pinned-git-show',
        'baselineSourceOrigins': {'effects': effects_origin, 'timeline': timeline_origin},
        'baselineArchiveFixtures': {'directory': BASELINE_FIXTURES, 'commitProvenance': BASELINE, 'rawSourcesVerifiedAgainstPinnedSha256': True},
        'actualCurrentProductionClassesCompiledUnchanged': names,
        'actualBaselineEffectsAndTimelineCompiledIndependently': True,
        'offAndFailureDifferential': 'Exact four emitted vertices, local/world transformed positions, matrices, UV, RGBA, ordering, render state and per-quad flush versus immutable .31',
        'enhancementDifferential': 'Only material/shader identity differs; exact original quad, timing, positions, UV, RGBA, states and per-quad flush',
        'originalQuirksPreserved': {'growthTicks': 15, 'rotationRampTicks': 30, 'unusedAlphaNotBound': True, 'observerOffsetX': 1.6},
        'classicAdmissionRuntime': ['no speculative press', 'matching accepted/shown/valid local and observer', 'duplicate token', 'stale nonce', 'ordinary release', 'late hidden start', 'removed/dead/spectator/replaced caster', 'connection identity', 'callback epoch', 'world/player cleanup'],
        'materialFailures': ['missing original texture', 'corrupt/non-original artwork', 'shader constructor', 'all six required uniforms', 'inactive required uniforms/Sampler0', 'unlinked program', 'uniform update runtime exception', 'forced fallback', 'pending generation', 'stale callback after failed/latest pending reload', 'changed callback candidate', 'unloaded config'],
        'frameAdmission': {'LOW': 8, 'MEDIUM': 16, 'overflowUsesExactBaseline': True, 'oneCompleteQuadPerAdmission': True, 'wrongStageCannotReset': True, 'qualityChangeUntilNextFrameFailsClosed': True, 'actualFullCallbackTwentyAcceptedObserversDifferential': True, 'failuresAndInvalidPhaseConsumeNoQuota': True},
        'phase': {'periodMillis': 6000, 'lowStatic': True, 'actualPauseClockFrozen': True, 'resumeExcludesPause': True, 'worldSessionReset': True, 'negativeRejectedBeforeAdmission': True, 'maxLongModuloFinite': True},
        'renderState': {'nearestFiltering': True, 'lequal': True, 'depthWrite': True, 'originalAlphaTransparency': True, 'noCull': True, 'noLightmap': True, 'noOverlay': True, 'target': 'PARTICLES_TARGET'},
        'nativeJoml': {'version': '1.10.5', 'sha256': digest(joml.read_bytes()), 'actualMatrixAndQuaternionArithmetic': True},
        'nativePinnedApiStateDefaults': {'version': 'Minecraft1.21.1/NeoForge21.1.252', 'renderTypeSourceSha256': digest(native_type), 'renderStateSourceSha256': digest(native_state), 'transparencyBlockSha256': digest(transparency)},
        'nativeConfig': {'result': 'PASS', 'assertions': int(native_result[1]), 'api': 'NeoForge21.1.252/NightConfig3.8.3', 'invalidAndMissingEnumDefaultOff': True, 'validFileSaveReopenAllThreeQualities': True, 'railgunQualityPreserved': True, 'malformedParserRejected': True, 'classpathSha256': digest(cp_file.read_bytes())},
        'recordingClientQualityCommandSaveAndStatus': True,
        'historicalRailgunAndClosedMeltdownerGuardsUnchanged': historical,
        'mutationControlsRejected': mutations,
        'productionSourceHashes': {**{JAVA + name: digest(source) for name, source in sources.items()}, **{str(p.relative_to(root)): digest(p.read_bytes()) for p in (root / 'src/main/resources/assets/academy/shaders/core').glob('light_shield_vfx2.*')}},
        'nativeMinecraftOrGpuRuntime': False,
        'limitations': ['Recording hosts cover only reached client/resource/render APIs and invoke HIGH/normal callbacks explicitly; actual NeoForge event dispatch and full engine callback timing require native verification',
                        'Pinned JOML arithmetic is native Java, while PoseStack/client/buffer/shader/GL resource hosts are recording adapters',
                        'Recorded state choices and GL uniform/link failure injection do not prove GPU shader compilation, blending, occlusion, visual quality or resource reload timing',
                        'Native NeoForge enum correction and Night Config file persistence are exercised; game-level malformed-file recovery and command-to-native-save wiring require native runtime',
                        'Existing scatter, ray, particle, packet/input and audio production code is guarded byte-for-byte apart from the one shield material call; these hosts do not establish pixel/audio acceptance'],
    }
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
