#!/usr/bin/env python3
"""Focused continuous-core math and actual Java readiness/resource oracle.

CPU sampling uses the byte-pinned supplied PNG and constants extracted from the
production GLSL, with contracts for the expressions being modeled. The resource
oracle compiles the actual material unchanged against narrow client adapters.
Neither is a GPU compilation, blending, occlusion or visual-quality test.
No Gradle, game, remote service or graphical window is launched.
"""
from pathlib import Path
import argparse
import hashlib
import json
import math
import os
import re
import shutil
import subprocess
import tempfile
from PIL import Image

NOISE_SHA256 = 'e61402fa1d9b14830d136068f6c685ac06da35fc9561f530cdeab3c34e12a2f1'


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def run(command):
    result = subprocess.run(list(map(str, command)), text=True, capture_output=True)
    require(result.returncode == 0, result.stdout + result.stderr)
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
    raise AssertionError('JDK 21 required; set JAVA_HOME')


def shader_contract(fsh):
    code = re.sub(r'//[^\n]*', '', fsh)
    require(not re.search(r'\b(?:discard|Time|GameTime|frameTime|gl_FragDepth)\b', code),
            'continuous core may not discard, use a frame clock, or write depth')
    require('ivec2 size = max(textureSize(Sampler0, 0), ivec2(1));' in code,
            'texture size needs a finite positive divisor')
    require(code.count('texelFetch(') == 2 and 'texture(' not in code,
            'two coarse wrapped fetches must supply data without sampler clamp dependence')
    require('(point.x % size.x + size.x) % size.x' in code and
            '(point.y % size.y + size.y) % size.y' in code, 'both coordinates must wrap')
    require('float position = fract(u) * 4.0 - 0.5;' in code and 'float fraction = fract(position);' in code,
            'virtual four-cell periodic grid changed')
    require('fraction * fraction * (3.0 - 2.0 * fraction)' in code and
            'mix(n0, n1, interpolation)' in code, 'smooth coarse interpolation changed')
    require('(base % 4 + 4) % 4' in code and '((base + 1) % 4 + 4) % 4' in code,
            'both coarse grid neighbors must wrap')
    for cell in ('cell0', 'cell1'):
        require(f'int((float({cell}) + 0.5) * float(size.x) / 4.0), size.y / 2' in code,
                'reference sampling must use four coarse centers with constant transverse coordinate')
    require('periodicNoise(texCoord0.x)' in code and 'texCoord0.y' not in code and 'detail' not in code,
            'sparse longitudinal material may not restore transverse/fine strands')
    heat = re.search(r'float heat = smoothstep\(([\d.]+), ([\d.]+), coarse\);', code)
    warmth = re.search(r'vec3 warmth = mix\(vec3\(([\d.]+), ([\d.]+), ([\d.]+)\), vec3\(([\d.]+), ([\d.]+), ([\d.]+)\), heat\);', code)
    lum = re.search(r'float luminosity = mix\(([\d.]+), ([\d.]+), heat\);', code)
    alpha = re.search(r'float continuity = mix\(([\d.]+), ([\d.]+), heat\);', code)
    require(all([heat, warmth, lum, alpha]), 'material needs auditable bounded expressions')
    require('fragColor = vec4(vertexColor.rgb * warmth * luminosity, vertexColor.a * continuity) * ColorModulator;' in code,
            'vertex warmth/fade and global modulator must enter once')
    constants = {name: tuple(map(float, match.groups())) for name, match in
                 [('heat', heat), ('warmth', warmth), ('luminosity', lum), ('continuity', alpha)]}
    require(constants['heat'] == (.20, .80), 'heat envelope changed')
    require(constants['warmth'] == (1., .99, .96, 1., 1., 1.), 'continuous ivory floor changed')
    require(constants['luminosity'] == (.94, 1.), 'low-contrast luminosity variation changed')
    require(constants['continuity'] == (.90, .96), 'continuous alpha bounds changed')
    return constants


def numerical_contract(noise, constants):
    with Image.open(noise) as source:
        image = source.convert('RGBA')
    require(image.size == (256, 256), 'reference noise dimensions changed')
    require(image.getextrema() == ((0, 254), (0, 254), (0, 254), (255, 255)), 'noise is opaque grayscale DATA')
    pixels, size = image.load(), 256

    def sample(u):
        position = (u % 1.) * 4. - .5
        base, fraction = math.floor(position), position-math.floor(position)
        interpolation = fraction*fraction*(3.-2.*fraction)
        cell0, cell1 = base % 4, (base+1) % 4
        n0 = pixels[int((cell0+.5)*size/4.) % size, size//2][0]/255.
        n1 = pixels[int((cell1+.5)*size/4.) % size, size//2][0]/255.
        return max(0., min(1., n0*(1.-interpolation)+n1*interpolation))

    def shade(u, v, original_alpha=1.):
        lo, hi = constants['heat']
        n = sample(u)
        t = max(0., min(1., (n-lo)/(hi-lo)))
        heat = t*t*(3.-2.*t)
        rgb = tuple(constants['warmth'][i]*(1.-heat)+constants['warmth'][i+3]*heat for i in range(3))
        lum = constants['luminosity'][0]*(1.-heat)+constants['luminosity'][1]*heat
        alpha = original_alpha*(constants['continuity'][0]*(1.-heat)+constants['continuity'][1]*heat)
        return (*[c*lum for c in rgb], alpha)

    observed, count = [[1e9, -1e9] for _ in range(4)], 0
    luminance_bounds = [1e9, -1e9]
    for age in (0, 150, 400, 900, 1200, 1799, 1800, 2000, 2400, 2499):
        for xi in range(161):
            for yi in range(49):
                result = shade(xi/160.+age/1800., yi/48.)
                require(all(map(math.isfinite, result)), 'nonfinite output')
                luminance = .2126*result[0]+.7152*result[1]+.0722*result[2]
                require(.93 <= luminance <= 1., 'ivory core luminance has no strong luminous floor')
                luminance_bounds = [min(luminance_bounds[0], luminance), max(luminance_bounds[1], luminance)]
                floors, ceilings = (.94, .9306, .9024, .90), (1., 1., 1., .96)
                for i, value in enumerate(result):
                    require(floors[i]-1e-12 <= value <= ceilings[i]+1e-12, 'modulation escaped bounds')
                    observed[i][0], observed[i][1] = min(observed[i][0], value), max(observed[i][1], value)
                count += 1
    for original_alpha in (0., 1/255., 16/255., 80/255., 200/255.):
        for xi in range(257):
            result = shade(xi/256., .43, original_alpha)
            require(original_alpha*.90-1e-12 <= result[3] <= original_alpha*.96+1e-12, 'original fade changed')
            require((result[3] > 0) == (original_alpha > 0), 'holes or expired core revival')
    max_seam = 0.
    for phase in (0., .15, .4, 1.2, 2.4):
        for i in range(129):
            u, v = phase+i/128., i/128.
            for a, b in ((shade(u, 0.), shade(u, 1.)), (shade(phase, v), shade(phase+1., v))):
                max_seam = max(max_seam, max(abs(x-y) for x, y in zip(a, b)))
            for a, b in ((shade(phase-1e-10, v), shade(phase+1e-10, v)),
                         (shade(u, -1e-10), shade(u, 1e-10))):
                require(max(abs(x-y) for x, y in zip(a, b)) < 1e-6, 'bilinear boundary discontinuity')
    require(max_seam < 1e-12, 'facet/end periodic seam mismatch')
    flow_delta = max(abs(a-b) for i in range(129) for a, b in
                     zip(shade(i/128.+400/1800., .45), shade(i/128.+700/1800., .45)))
    require(.02 < flow_delta < .10, 'age-driven ivory flow must stay gently visible')
    max_transverse_delta = max(abs(a-b) for i in range(129) for a, b in
                               zip(shade(i/128., .17), shade(i/128., .83)))
    require(max_transverse_delta == 0., 'transverse fibres reintroduced')
    axial = [shade(i/4096., .5)[0] for i in range(4097)]
    slopes = [1 if b > a else -1 for a, b in zip(axial, axial[1:]) if abs(b-a) > 1e-12]
    slope_changes = sum(a != b for a, b in zip(slopes, slopes[1:]))
    require(slope_changes <= 4, 'fine axial grain exceeds four-cell sampling bandwidth')
    maximum_step = max(abs(a-b) for a, b in zip(axial, axial[1:]))
    require(maximum_step < .001, 'coarse interpolation introduced sharp axial stripes')
    phase_repeat = max(abs(a-b) for i in range(129) for a, b in
                       zip(shade(i/128.+400/1800., .45), shade(i/128.+2200/1800., .45)))
    require(phase_repeat < 1e-12, '1800 ms integer UV phase must repeat exactly')
    return {'samples': count, 'relativeRgbBounds': observed[:3], 'relativeAlphaBounds': observed[3],
            'maximumPeriodicSeamError': max_seam, 'maximum300msFlowDelta': flow_delta,
            'relativeLuminanceBounds': luminance_bounds, 'maximum1800msPhaseRepeatError': phase_repeat,
            'virtualLongitudinalCells': 4, 'maximumTransverseColorDelta': max_transverse_delta,
            'axialSlopeDirectionChanges': slope_changes, 'maximum4096thAxialStep': maximum_step,
            'noTimeUniformOnlyUvPhase': True, 'originalFadeAndExpiryPreserved': True}


STUBS = {
 'net/neoforged/api/distmarker/Dist.java': 'package net.neoforged.api.distmarker; public enum Dist {CLIENT}',
 'net/neoforged/bus/api/SubscribeEvent.java': 'package net.neoforged.bus.api; public @interface SubscribeEvent {}',
 'net/neoforged/fml/common/EventBusSubscriber.java': 'package net.neoforged.fml.common; public @interface EventBusSubscriber {String modid(); net.neoforged.api.distmarker.Dist[] value(); Bus bus(); enum Bus {MOD}}',
 'net/minecraft/resources/ResourceLocation.java': 'package net.minecraft.resources; public record ResourceLocation(String namespace,String path){public static ResourceLocation fromNamespaceAndPath(String n,String p){return new ResourceLocation(n,p);}}',
 'com/mojang/blaze3d/vertex/DefaultVertexFormat.java': 'package com.mojang.blaze3d.vertex; public class DefaultVertexFormat {public static final Object POSITION_TEX_COLOR=new Object();}',
 'com/mojang/logging/LogUtils.java': 'package com.mojang.logging; public class LogUtils {public static Logger getLogger(){return new Logger();} public static class Logger {public void warn(String s,Object...args){}public void info(String s,Object...args){}}}',
 'net/minecraft/server/packs/resources/ResourceProvider.java': 'package net.minecraft.server.packs.resources; public interface ResourceProvider {}',
 'net/minecraft/server/packs/resources/ResourceManager.java': '''package net.minecraft.server.packs.resources; public interface ResourceManager extends ResourceProvider {
 Resource getResourceOrThrow(net.minecraft.resources.ResourceLocation location)throws java.io.IOException;
 record Resource(byte[] data){public java.io.InputStream open(){return new java.io.ByteArrayInputStream(data);}}}''',
 'net/minecraft/server/packs/resources/ResourceManagerReloadListener.java': 'package net.minecraft.server.packs.resources; public interface ResourceManagerReloadListener {void onResourceManagerReload(ResourceManager resources);}',
 'net/neoforged/neoforge/client/event/RegisterClientReloadListenersEvent.java': '''package net.neoforged.neoforge.client.event; public class RegisterClientReloadListenersEvent {
 public net.minecraft.server.packs.resources.ResourceManagerReloadListener listener; public void registerReloadListener(net.minecraft.server.packs.resources.ResourceManagerReloadListener l){listener=l;}}''',
 'com/mojang/blaze3d/platform/NativeImage.java': '''package com.mojang.blaze3d.platform; public class NativeImage implements AutoCloseable {
 final java.awt.image.BufferedImage image; private NativeImage(java.awt.image.BufferedImage i){image=i;}
 public static NativeImage read(java.io.InputStream in)throws java.io.IOException {var i=javax.imageio.ImageIO.read(in);if(i==null)throw new java.io.IOException("not PNG");return new NativeImage(i);}
 public int getWidth(){return image.getWidth();}public int getHeight(){return image.getHeight();}public void close(){}}''',
 'net/minecraft/client/renderer/ShaderInstance.java': '''package net.minecraft.client.renderer; public class ShaderInstance implements AutoCloseable {
 public static final java.util.Set<String> failures=new java.util.HashSet<>(),linkFailures=new java.util.HashSet<>();
 public static final java.util.Map<Integer,ShaderInstance> instances=new java.util.HashMap<>(); public final String name;public final int id;public boolean closed;
 public ShaderInstance(net.minecraft.server.packs.resources.ResourceProvider p,net.minecraft.resources.ResourceLocation r,Object f)throws java.io.IOException {
 name=r.path();if(failures.contains(name))throw new java.io.IOException("injected "+name);id=instances.size()+1;instances.put(id,this);}
 public int getId(){return id;}public void close(){closed=true;}}''',
 'net/minecraft/client/renderer/GameRenderer.java': 'package net.minecraft.client.renderer;public class GameRenderer {public static ShaderInstance getPositionTexColorShader(){return null;}}',
 'org/lwjgl/opengl/GL20.java': '''package org.lwjgl.opengl; public class GL20 {public static final int GL_LINK_STATUS=35714;
 public static int glGetProgrami(int id,int what){return net.minecraft.client.renderer.ShaderInstance.linkFailures.contains(net.minecraft.client.renderer.ShaderInstance.instances.get(id).name)?0:1;}
 public static String glGetProgramInfoLog(int id){return "injected link failure";}}''',
 'net/neoforged/neoforge/client/event/RegisterShadersEvent.java': '''package net.neoforged.neoforge.client.event; public class RegisterShadersEvent {
 public final java.util.Map<String,net.minecraft.client.renderer.ShaderInstance> candidates=new java.util.HashMap<>();
 public final java.util.Map<String,java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance>> callbacks=new java.util.HashMap<>();
 public net.minecraft.server.packs.resources.ResourceProvider getResourceProvider(){return null;}
 public void registerShader(net.minecraft.client.renderer.ShaderInstance s,java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance> c){candidates.put(s.name,s);callbacks.put(s.name,c);}
 public void complete(String n){callbacks.get(n).accept(candidates.get(n));}public void completeAll(){for(var n:java.util.List.copyOf(callbacks.keySet()))complete(n);}}''',
}

HARNESS = r'''
package cn.academy.port.client.vfx2;
import java.nio.file.*;
import java.util.*;
import net.minecraft.server.packs.resources.*;
import net.minecraft.client.renderer.*;
import net.neoforged.neoforge.client.event.*;
public final class RailgunCoreMaterialOracle {
 static int checks;
 static void require(boolean c,String m){checks++;if(!c)throw new AssertionError(m);}
 static final String OVERLAY="railgun_vfx2", CORE="railgun_vfx2_core";
 static RegisterClientReloadListenersEvent resources;
 static byte[] noise,strip;
 static void reload(String missing,byte[] candidateNoise){
  resources.listener.onResourceManagerReload(location->{
   if(!missing.isEmpty()&&location.path().contains(missing))throw new java.io.IOException("missing "+location);
   return new ResourceManager.Resource(location.path().contains("loopable_noise")?candidateNoise:strip);
  });
 }
 static RegisterShadersEvent shaders(){var e=new RegisterShadersEvent();RailgunVfx2Material.shaders(e);return e;}
 static byte[] png(int width,int height)throws Exception{
  var out=new java.io.ByteArrayOutputStream();javax.imageio.ImageIO.write(new java.awt.image.BufferedImage(width,height,java.awt.image.BufferedImage.TYPE_INT_ARGB),"PNG",out);return out.toByteArray();
 }
 static boolean both(){return RailgunVfx2Material.available()&&RailgunVfx2Material.coreAvailable();}
 public static void main(String[] args)throws Exception{
  noise=Files.readAllBytes(Path.of(args[0]));strip=Files.readAllBytes(Path.of(args[1]));
  require(!RailgunVfx2Material.available()&&!RailgunVfx2Material.coreAvailable(),"uninitialized must be classic");
  resources=new RegisterClientReloadListenersEvent();RailgunVfx2Material.resources(resources);reload("",noise);
  require(!RailgunVfx2Material.coreAvailable(),"resources alone cannot replace core");
  var initial=shaders();require(initial.candidates.size()==2,"independent shader registrations");
  initial.complete(CORE);require(!RailgunVfx2Material.coreAvailable(),"core requires optional overlay readiness");
  initial.complete(OVERLAY);require(both(),"valid shaders/resources enable core");
  for(String missing:List.of("loopable_noise","beam_halo_strip","beam_core_strip")){
   reload(missing,noise);require(!RailgunVfx2Material.coreAvailable(),"missing resource retains classic core "+missing);
   require(RailgunVfx2Material.available()==missing.equals("loopable_noise"),"noise failure preserves overlay");
   reload("",noise);require(both(),"resources recover");
  }
  for(byte[] bad:List.of(new byte[]{1,2,3},png(1,1),png(256,64))){
   reload("",bad);require(RailgunVfx2Material.available()&&!RailgunVfx2Material.coreAvailable(),"invalid noise retains classic core");
  }
  reload("",noise);
  for(String failed:List.of(OVERLAY,CORE)){
   ShaderInstance.failures.add(failed);var e=shaders();e.completeAll();
   require(!RailgunVfx2Material.coreAvailable(),"partial shader construction failure retains classic core");
   require(RailgunVfx2Material.available()==failed.equals(CORE),"core construction failure preserves overlay");
   ShaderInstance.failures.clear();shaders().completeAll();require(both(),"shader recovery");
   ShaderInstance.linkFailures.add(failed);e=shaders();e.completeAll();
   require(!RailgunVfx2Material.coreAvailable(),"partial link failure retains classic core");
   require(ShaderInstance.instances.values().stream().filter(s->s.name.equals(failed)).reduce((a,b)->a.id>b.id?a:b).orElseThrow().closed,"unlinked candidate closed");
   ShaderInstance.linkFailures.clear();shaders().completeAll();require(both(),"link recovery");
  }
  var old=shaders();old.completeAll();require(both(),"old generation ready");
  long generation=RailgunVfx2Material.coreGeneration();var current=shaders();
  require(RailgunVfx2Material.coreGeneration()>generation,"new generation invalidates prior admission");
  require(!RailgunVfx2Material.available()&&!RailgunVfx2Material.coreAvailable(),"reload clears both shaders");
  old.completeAll();require(!RailgunVfx2Material.available()&&!RailgunVfx2Material.coreAvailable(),"stale callbacks cannot revive either material");
  current.complete(OVERLAY);require(RailgunVfx2Material.available()&&!RailgunVfx2Material.coreAvailable(),"pending core remains classic");
  current.complete(CORE);require(both(),"new core completes independently");
  var kept=RailgunVfx2Material.coreShader();old.completeAll();require(RailgunVfx2Material.coreShader()==kept,"stale callback cannot replace new core");
  System.setProperty("academy.vfx2.forceFallback","true");var forced=shaders();
  require(forced.candidates.isEmpty()&&!RailgunVfx2Material.available()&&!RailgunVfx2Material.coreAvailable(),"forced fallback invalidates both");
  current.completeAll();old.completeAll();require(!RailgunVfx2Material.coreAvailable()&&!RailgunVfx2Material.available(),"forced fallback cannot be revived");
  System.clearProperty("academy.vfx2.forceFallback");shaders().completeAll();require(both(),"fresh generation recovers");
  System.out.println("PASS|"+checks);
 }
}
'''


def resource_oracle(root, material):
    home = jdk(root)
    with tempfile.TemporaryDirectory(prefix='railgun-core-material-') as directory:
        temp = Path(directory)
        generated, classes = temp/'src', temp/'classes'
        sources = {**STUBS, 'cn/academy/port/client/vfx2/RailgunVfx2Material.java': material,
                   'cn/academy/port/client/vfx2/RailgunCoreMaterialOracle.java': HARNESS}
        for name, content in sources.items():
            p = generated/name
            p.parent.mkdir(parents=True, exist_ok=True)
            p.write_text(content)
        run([home/'bin/javac', '--release', '21', '-d', classes, *sorted(generated.rglob('*.java'))])
        textures = root/'src/main/resources/assets/academy/textures/vfx2'
        command = [home/'bin/java', '-Djava.awt.headless=true', '-cp', classes,
                   'cn.academy.port.client.vfx2.RailgunCoreMaterialOracle', textures/'loopable_noise.png', textures/'beam_core_strip.png']
        result = run(command).strip().split('|')
        require(result[0] == 'PASS', 'actual material oracle failed')
        altered = re.sub(r'\s*if \(generation != shaderGeneration\) return;', '', material)
        require(altered != material, 'generation mutation did not alter source')
        p = generated/'cn/academy/port/client/vfx2/RailgunVfx2Material.java'
        p.write_text(altered)
        run([home/'bin/javac', '--release', '21', '-cp', classes, '-d', classes, p])
        broken = subprocess.run(list(map(str, command)), text=True, capture_output=True)
        require(broken.returncode != 0 and 'stale callbacks cannot revive' in broken.stderr,
                'oracle must reject stale shader generation mutation')
        return {'actualJavaMaterial': True, 'adapterAssertions': int(result[1]),
                'resourceDecodeDimensionsMissingResourcesPartialShadersLinkFailuresDeferredCallbacksForcedFallback': 'PASS',
                'staleGenerationMutationRejected': True, 'nativeClientResourceReload': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    shader_root = root/'src/main/resources/assets/academy/shaders/core'
    java_root = root/'src/main/java/cn/academy/port/client/vfx2'
    noise = root/'src/main/resources/assets/academy/textures/vfx2/loopable_noise.png'
    material = (java_root/'RailgunVfx2Material.java').read_text()
    types = (java_root/'RailgunVfx2RenderTypes.java').read_text()
    fsh = (shader_root/'railgun_vfx2_core.fsh').read_text()
    vsh = (shader_root/'railgun_vfx2_core.vsh').read_text()
    shader = json.loads((shader_root/'railgun_vfx2_core.json').read_text())
    require(hashlib.sha256(noise.read_bytes()).hexdigest() == NOISE_SHA256, 'supplied PNG bytes changed')
    require(shader['vertex'] == shader['fragment'] == 'academy:railgun_vfx2_core', 'dedicated core identity')
    require(shader['samplers'] == [{'name':'Sampler0'}], 'core only consumes pinned noise')
    require({u['name'] for u in shader['uniforms']} == {'ModelViewMat','ProjMat','ColorModulator'}, 'no time uniform')
    require('texCoord0 = UV0;' in vsh and 'vertexColor = Color;' in vsh, 'original UV/color must pass unchanged')
    inner = types[types.index('static final RenderType INNER_CORE'):types.index('private RailgunVfx2RenderTypes')]
    for required in ('RailgunVfx2Material::coreShader','RailgunVfx2Material.NOISE, true, false',
                     'TRANSLUCENT_TRANSPARENCY','LEQUAL_DEPTH_TEST','COLOR_WRITE','NO_LIGHTMAP','NO_OVERLAY','PARTICLES_TARGET'):
        require(required in inner, 'missing scoped inner state: '+required)
    require('RenderSystem.' not in inner and 'COLOR_DEPTH_WRITE' not in inner, 'no depth writes/global state mutation')
    constants = shader_contract(fsh)
    rejected = []
    for label, changed in [('alphaHoles', fsh.replace('mix(0.90, 0.96, heat)', 'mix(0.0, 0.96, heat)')),
                           ('fineGrain', fsh.replace('fract(u) * 4.0', 'fract(u) * 256.0')),
                           ('transverseFibres', fsh.replace('periodicNoise(texCoord0.x)', 'periodicNoise(texCoord0.x + texCoord0.y)')),
                           ('discardHoles', fsh.replace('void main() {', 'void main() { discard;'))]:
        try:
            shader_contract(changed)
        except AssertionError:
            rejected.append(label)
        else:
            raise AssertionError('negative control escaped: '+label)
    report = {'result':'PASS','noiseSha256':NOISE_SHA256,'noiseBytes':noise.stat().st_size,
              'cpuMaterialReference':numerical_contract(noise, constants),
              'actualMaterialResourceOracle':resource_oracle(root, material),
              'shaderSourceMutationControlsRejected':rejected,
              'productionSourceHashes':{str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in
                                       [java_root/'RailgunVfx2Material.java', java_root/'RailgunVfx2RenderTypes.java',
                                        *[shader_root/('railgun_vfx2_core.'+ext) for ext in ('json','vsh','fsh')], noise]},
              'nativeMinecraftOrGpuRuntime':False,
              'limitations':['CPU shader-formula reference; native driver/framebuffer/terrain/blending acceptance remains open',
                             'Narrow resource adapters; actual pause-aware UV admission is covered separately by integration oracle']}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
