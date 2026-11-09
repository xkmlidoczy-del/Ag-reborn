#!/usr/bin/env python3
"""Check original-alpha support, bounded RGB flow, and actual Java material readiness.

This launches no game, graphical window, or Gradle. CPU formula tests are guarded
against the production shader text and pinned artwork. Java recording adapters
exercise the unchanged production material, including adversarial reloads and
missing uniforms. Native shader compilation and visual acceptance are separate.
"""
from collections import Counter
from pathlib import Path
import argparse
import hashlib
import json
import math
import re
import runpy
import subprocess
import tempfile
from PIL import Image

TEXTURE_SHA256 = '5a0cf5f61bea467687820983efb76cfd14f68abbc38667e091b6b0f27965c5dc'
UNIFORMS = {'ModelViewMat', 'ProjMat', 'ColorModulator', 'SwirlGain', 'FlowAmount', 'VisualPhase'}


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def shader_contract(fsh, vsh, descriptor, material, types):
    code = re.sub(r'//[^\n]*', '', fsh)
    require(descriptor['vertex'] == descriptor['fragment'] == 'academy:light_shield_vfx2', 'dedicated material identity')
    require(descriptor['samplers'] == [{'name': 'Sampler0'}], 'only the original mdshield sampler is allowed')
    require({u['name'] for u in descriptor['uniforms']} == UNIFORMS, 'bounded scoped uniforms only')
    require('texCoord0 = UV0;' in vsh and 'vertexColor = Color;' in vsh, 'original UV and vertex color must pass unchanged')
    require('gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);' in vsh, 'original position transform')
    require(code.count('texture(') == 2 and 'vec4 source = texture(Sampler0, texCoord0);' in code,
            'one original unshifted RGBA fetch plus the localized RGB-only reference is required')
    require('vec3 artworkRgb = source.rgb;' in code, 'outside-band RGB must start as the exact original')
    feather = re.search(r'if \(texCoord0.y > ([\d.]+) / 512.0 && texCoord0.y < ([\d.]+) / 512.0\) \{\s*'
                        r'vec3 referenceRgb = texture\(Sampler0, vec2\(texCoord0.x, ([\d.]+) / 512.0\)\).rgb;\s*'
                        r'float feather = smoothstep\(([\d.]+) / 512.0, ([\d.]+) / 512.0, texCoord0.y\);\s*'
                        r'artworkRgb = mix\(referenceRgb, source.rgb, feather\);\s*\}', code)
    require(feather and tuple(map(float, feather.groups())) == (124.5, 140.5, 124.5, 124.5, 140.5),
            'RGB feather must stay inside the approved 16-texel band using same-u row124 RGB only')
    require(code.count('artworkRgb =') == 2 and code.count('referenceRgb') == 2,
            'no global RGB smoothing or reference-alpha propagation')
    require(code.count('source.a') == 1 and code.count('* vertexColor') == code.count('* ColorModulator') == 1,
            'original alpha and both modulators must enter once')
    require('vec4 color = vec4(swirlRgb, source.a) * vertexColor * ColorModulator;' in code,
            'exact original alpha product')
    require('if (color.a <= 0.05) discard;' in code and code.count('discard') == 1, 'strict original .05 alpha support')
    require('fragColor = color;' in code, 'no second opacity transform')
    require(not re.search(r'\b(?:gl_FragDepth|Time|GameTime|frameTime|pow|exp|atan|texelFetch)\b', code),
            'no depth change, extra clock, angle seam, or new alpha weighting')
    mask = re.search(r'float paleSwirl = smoothstep\(([\d.]+), ([\d.]+), min\(artworkRgb.r, artworkRgb.b\)\);', code)
    gain = re.search(r'float uplift = clamp\(SwirlGain, ([\d.]+), ([\d.]+)\) \* paleSwirl \* \(1.0 \+ clamp\(FlowAmount, ([\d.]+), ([\d.]+)\) \* flow\);', code)
    require(mask and gain, 'auditable pale RGB mask and flow bounds')
    constants = {'mask': tuple(map(float, mask.groups())), 'gainFlow': tuple(map(float, gain.groups()))}
    require(constants == {'mask': (.32, .82), 'gainFlow': (0., .28, 0., .18)}, 'RGB envelope changed')
    require('sin(6.28318530718 * (2.0 * texCoord0.x + texCoord0.y - VisualPhase))' in code and
            'cos(6.28318530718 * (texCoord0.y + VisualPhase))' in code, 'periodic bounded phase expression changed')
    guarded_flow = re.search(r'float flow = 0\.0;\s*if \(FlowAmount > 0\.0\) \{\s*flow = sin\([^;]+\)\s*\* cos\([^;]+\);\s*\}', code)
    require(guarded_flow and len(re.findall(r'\b(?:sin|cos)\s*\(', code)) == 2 and
            len(re.findall(r'\b(?:sin|cos)\s*\(', guarded_flow.group())) == 2,
            'LOW zero-flow uniform must guard all flow trigonometry')
    require('vec3 swirlRgb = mix(artworkRgb, vec3(1.0), uplift);' in code, 'existing bounded uplift must follow the localized RGB feather')
    require('FLOW_PERIOD_MILLIS = 6000L' in material and 'SWIRL_GAIN = .28F, MEDIUM_FLOW = .18F' in material,
            'Java and shader RGB flow bounds must match')
    require('(phaseMillis % FLOW_PERIOD_MILLIS) / (float)FLOW_PERIOD_MILLIS' in material, 'normalize the accepted clock before float conversion')
    for state in ('LightShieldVfx2Material::shader', 'LightShieldVfx2Material.TEXTURE, false, false',
                  'TRANSLUCENT_TRANSPARENCY', 'LEQUAL_DEPTH_TEST', 'COLOR_DEPTH_WRITE', 'NO_CULL',
                  'NO_LIGHTMAP', 'NO_OVERLAY', 'PARTICLES_TARGET'):
        require(state in types, 'original render state is missing: ' + state)
    require('RenderSystem' not in types + material, 'scoped material cannot mutate global RenderSystem state')
    require('ClassicMeltdownerStarterEffects.CutoffShader.get()' in material and 'GameRenderer' not in material,
            'shader supplier fallback must preserve the original cutoff supplier')
    constants['featherBand'] = (124.5, 140.5)
    return constants


def numerical_contract(texture, constants):
    with Image.open(texture) as opened:
        image = opened.convert('RGBA')
    require(image.size == (512, 512), 'original artwork dimensions')
    pixels = list(image.get_flattened_data())
    alpha_histogram = Counter(p[3] for p in pixels)
    low, high = constants['mask']
    gain, flow_amount = constants['gainFlow'][1], constants['gainFlow'][3]
    band_start, band_end = (row/512. for row in constants['featherBand'])
    reference_row = [tuple(c/255. for c in pixels[124*512+x][:3]) for x in range(512)]

    def feather_rgb(rgb, u, v):
        if not band_start < v < band_end:
            return rgb
        t = (v-band_start)/(band_end-band_start)
        weight = t*t*(3.-2.*t)
        reference = reference_row[max(0, min(511, math.floor(u*512.)))]
        return tuple(a+(b-a)*weight for a, b in zip(reference, rgb))

    def mask(rgb):
        t = max(0., min(1., (min(rgb[0], rgb[2])-low)/(high-low)))
        return t*t*(3.-2.*t)

    def flow(u, v, phase):
        return math.sin(6.28318530718*(2.*u+v-phase))*math.cos(6.28318530718*(v+phase))

    def shade(rgb, alpha, u, v, phase, medium, feather=True):
        if feather:
            rgb = feather_rgb(rgb, u, v)
        uplift = gain*mask(rgb)*(1.+(flow_amount*flow(u, v, phase) if medium else 0.))
        return (*[c+(1.-c)*uplift for c in rgb], alpha)

    alpha_samples, support_counts = 0, {}
    # Histogram weighting covers every original pixel for every modulation pair.
    for vertex_alpha in (0., 1/255., .05, .13, .5, 1.):
        for modulator_alpha in (0., .05, .25, .75, 1.):
            classic_support = enhanced_support = 0
            for original_alpha, count in alpha_histogram.items():
                alpha = original_alpha/255.*vertex_alpha*modulator_alpha
                for u, v in ((.23, .61), (.5, 130.5/512.)):
                    result = shade((.48, .996, .40), alpha, u, v, .375, True)
                    require(result[3] == alpha, 'opacity changed inside or outside the RGB feather band')
                    alpha_samples += count
                classic_support += count*(alpha > .05)
                enhanced_support += count*(result[3] > .05)
            require(classic_support == enhanced_support, 'original covered pixel support changed')
            support_counts[f'{vertex_alpha:.8f}*{modulator_alpha:.8f}'] = classic_support
    for original_alpha in (0., .049999, .05, .050001, .15, .451, 1.):
        for vertex_alpha, modulator_alpha in ((1., 1.), (.5, 1.), (1., .5), (.3, .7)):
            alpha = original_alpha*vertex_alpha*modulator_alpha
            require(shade((.48, .996, .40), alpha, .2, .8, .5, True)[3] == alpha,
                    'synthetic cutoff equality or varied original alpha changed')

    luminance_delta = [math.inf, -math.inf]
    rgb_delta = [0., 0., 0.]
    feather_delta = [[math.inf, -math.inf] for _ in range(3)]
    outside_samples, outside_low_samples, band_pixels = 0, 0, 0
    temporal_delta, changed_visible, untouched_base = 0., 0, 0
    visible_count = sum(count for a, count in alpha_histogram.items() if a/255. > .05)
    for i, pixel in enumerate(pixels):
        rgb = tuple(c/255. for c in pixel[:3])
        alpha = pixel[3]/255.
        u, v = (i % 512 + .5)/512., (i // 512 + .5)/512.
        artwork_rgb = feather_rgb(rgb, u, v)
        in_band = band_start < v < band_end
        if in_band:
            band_pixels += 1
            if alpha > .05:
                for k, delta in enumerate(a-b for a, b in zip(artwork_rgb, rgb)):
                    feather_delta[k][0] = min(feather_delta[k][0], delta)
                    feather_delta[k][1] = max(feather_delta[k][1], delta)
        else:
            require(artwork_rgb == rgb, 'original RGB changed outside the localized band')
        low_result = shade(rgb, alpha, u, v, 0., False)
        low_next = shade(rgb, alpha, u, v, .25, False)
        require(low_result == low_next, 'LOW must ignore phase entirely')
        if not in_band:
            require(low_result == shade(rgb, alpha, u, v, 0., False, feather=False), 'LOW output outside band changed')
            outside_low_samples += 1
        if alpha > .05 and any(abs(a-b) > 1e-9 for a, b in zip(low_result[:3], rgb)):
            changed_visible += 1
        if not in_band and min(rgb[0], rgb[2]) <= low:
            require(low_result[:3] == rgb, 'base green disk was changed')
            if alpha > .05:
                untouched_base += 1
        medium_first = None
        for phase in (0., .125, .25, .5, .875):
            result = shade(rgb, alpha, u, v, phase, True)
            require(result[3] == alpha and all(math.isfinite(c) and 0. <= c <= 1. for c in result), 'finite bounded RGBA')
            if not in_band:
                require(result == shade(rgb, alpha, u, v, phase, True, feather=False), 'enhanced output outside band changed')
                outside_samples += 1
            delta = [a-b for a, b in zip(result[:3], artwork_rgb)]
            require(min(delta) >= -1e-12, 'existing bounded uplift darkens feathered artwork')
            ld = sum(weight*d for weight, d in zip((.2126, .7152, .0722), delta))
            if alpha > .05:
                luminance_delta[0], luminance_delta[1] = min(luminance_delta[0], ld), max(luminance_delta[1], ld)
                rgb_delta = [max(previous, value) for previous, value in zip(rgb_delta, delta)]
            if phase == 0.:
                medium_first = result
            else:
                temporal_delta = max(temporal_delta, max(abs(a-b) for a, b in zip(result[:3], medium_first[:3])))
    require(0. < luminance_delta[1] < .025, 'pale detail luminance lift escaped the restrained envelope')
    require(max(rgb_delta) < .10, 'RGB lift escaped bounds')
    require(.003 < temporal_delta < .025, 'MEDIUM flow must remain bounded and nonzero on original detail')
    require(0 < changed_visible < visible_count and untouched_base > 0, 'RGB changes must remain selective')
    require(band_pixels == 15*512, 'only texel centers strictly inside the 16-texel band may be feathered')
    for u in (0., .1, .5, .9, 1.):
        for v in (band_start, band_end, band_start-1e-8, band_end+1e-8):
            rgb = (.48, .996, .40)
            require(feather_rgb(rgb, u, v) == rgb, 'boundary or outside-band RGB changed')
    def original_at(x, y):
        return tuple(c/255. for c in pixels[y*512+x][:3])
    def feather_at(x, y):
        return feather_rgb(original_at(x, y), (x+.5)/512., (y+.5)/512.)
    original_edge = max(abs(original_at(x, 126)[k]-original_at(x, 125)[k])*255.
                        for x in range(180, 331) for k in range(3))
    feathered_edge = max(abs(feather_at(x, 126)[k]-feather_at(x, 125)[k])*255.
                         for x in range(180, 331) for k in range(3))
    original_band_gradient = max(abs(original_at(x, y+1)[k]-original_at(x, y)[k])*255.
                                 for x in range(180, 331) for y in range(124, 141) for k in range(3))
    feathered_band_gradient = max(abs(feather_at(x, y+1)[k]-feather_at(x, y)[k])*255.
                                  for x in range(180, 331) for y in range(124, 141) for k in range(3))
    require(original_edge > 0 and feathered_edge < original_edge*.1, 'identified row125->126 gradient was not reduced')
    require(feathered_band_gradient <= original_band_gradient, 'feather introduced a larger row gradient in the inspected band')
    # A reference below alpha cutoff can contain hidden RGB. The pinned rim happens
    # to carry exactly the same green RGB, so RGB-only mixing cannot brighten it.
    reference_below_cutoff_visible_sources = []
    for y in range(125, 141):
        for x in range(512):
            source, reference = pixels[y*512+x], pixels[124*512+x]
            if source[3]/255. > .05 and reference[3]/255. <= .05:
                require(source[:3] == reference[:3] == (74, 254, 50), 'hidden reference RGB would change the visible outer rim')
                reference_below_cutoff_visible_sources.append((x, y))
    require(len(reference_below_cutoff_visible_sources) == 217, 'pinned near-rim reference sample set changed')
    def low_at(x, y, feather):
        return shade(original_at(x, y), pixels[y*512+x][3]/255., (x+.5)/512., (y+.5)/512., 0., False, feather=feather)[:3]
    old_low_edge = max(abs(low_at(x, 126, False)[k]-low_at(x, 125, False)[k])*255.
                       for x in range(180, 331) for k in range(3))
    new_low_edge = max(abs(low_at(x, 126, True)[k]-low_at(x, 125, True)[k])*255.
                       for x in range(180, 331) for k in range(3))
    require(new_low_edge < old_low_edge*.1, 'final LOW shader did not reduce the identified RGB edge')
    seam = 0.
    for phase in (0., .125, .375, .875):
        for i in range(129):
            uv = i/128.
            seam = max(seam, abs(flow(0., uv, phase)-flow(1., uv, phase)),
                       abs(flow(uv, 0., phase)-flow(uv, 1., phase)),
                       abs(flow(uv, .4, phase)-flow(uv, .4, phase+1.)))
    require(seam < 1e-10, 'RGB field has a UV or phase seam')
    boundary_step = max(abs(flow(-1e-8, i/128., .25)-flow(1e-8, i/128., .25)) for i in range(129))
    require(boundary_step < 3e-7, 'periodic field is discontinuous across its boundary')
    return {'originalPixels': len(pixels), 'alphaPixelModulationSamples': alpha_samples,
            'originalAlphaValues': len(alpha_histogram), 'supportCounts': support_counts,
            'alphaAndStrictCutoffExactlyPreserved': True, 'visiblePixels': visible_count,
            'visiblePixelsChangedByFeatherOrUplift': changed_visible, 'visibleOutsideBandBasePixelsUnchanged': untouched_base,
            'visibleLuminanceUpliftBoundsAfterFeather': luminance_delta, 'maximumRgbUpliftAfterFeather': rgb_delta,
            'localizedRgbFeather': {'sourceCoordinates': constants['featherBand'], 'sourceRows': 'same-u row124 RGB reference; original alpha always retained',
                                   'texelCentersInsideBand': band_pixels, 'outsideBandMediumSamplesExactlyEqualPriorShader': outside_samples,
                                   'outsideBandLowSamplesExactlyEqualPriorShader': outside_low_samples,
                                   'outsideBandOriginalRgbExactlyPreserved': True, 'visibleFeatherRgbDeltaBounds': feather_delta,
                                   'visibleSourcesWithBelowCutoffReferenceRgbExactlyMatchingGreen': len(reference_below_cutoff_visible_sources),
                                   'row125To126MaximumRgbGradientBytes': {'original': original_edge, 'feathered': feathered_edge},
                                   'finalLowRow125To126MaximumRgbGradientBytes': {'prior': old_low_edge, 'feathered': new_low_edge},
                                   'rows124To141MaximumRgbGradientBytes': {'original': original_band_gradient, 'feathered': feathered_band_gradient}},
            'maximumSampledMediumTemporalRgbDelta': temporal_delta, 'maximumPeriodicSeamError': seam,
            'maximumBoundaryStepAt2eMinus8': boundary_step, 'lowIgnoresPhase': True,
            'interpretation': 'CPU formula and support bounds only; no visual-quality claim'}


EXTRA_STUBS = {
    'net/minecraft/server/packs/resources/ResourceProvider.java': '''package net.minecraft.server.packs.resources;
public interface ResourceProvider {ResourceManager.Resource getResourceOrThrow(net.minecraft.resources.ResourceLocation r)throws java.io.IOException;}''',
    'com/mojang/blaze3d/shaders/Uniform.java': '''package com.mojang.blaze3d.shaders;
public final class Uniform {public final String name;public float value;public static String fail="";public static Runnable hook;
public Uniform(String n){name=n;}public void set(float v){if(fail.equals(name))throw new IllegalStateException("injected uniform "+name);
value=v;var action=hook;hook=null;if(action!=null)action.run();}}''',
    'net/minecraft/client/renderer/ShaderInstance.java': '''package net.minecraft.client.renderer;
public class ShaderInstance implements AutoCloseable {
public static final java.util.Set<String> failures=new java.util.HashSet<>(),linkFailures=new java.util.HashSet<>(),missing=new java.util.HashSet<>();
public static final java.util.Map<Integer,ShaderInstance> instances=new java.util.HashMap<>();public final String name;public final int id;public boolean closed;
private final java.util.Map<String,com.mojang.blaze3d.shaders.Uniform> uniforms=new java.util.HashMap<>();
public ShaderInstance(net.minecraft.server.packs.resources.ResourceProvider p,net.minecraft.resources.ResourceLocation r,Object f)throws java.io.IOException {
name=r.path();if(failures.contains(name))throw new java.io.IOException("injected constructor "+name);id=instances.size()+1;instances.put(id,this);
for(String n:java.util.List.of("ModelViewMat","ProjMat","ColorModulator","SwirlGain","FlowAmount","VisualPhase"))uniforms.put(n,new com.mojang.blaze3d.shaders.Uniform(n));}
public com.mojang.blaze3d.shaders.Uniform getUniform(String n){return missing.contains(n)?null:uniforms.get(n);}
public int getId(){return id;}public void close(){closed=true;}}''',
    'org/lwjgl/opengl/GL20.java': '''package org.lwjgl.opengl;
public class GL20 {public static final int GL_LINK_STATUS=35714;public static final java.util.Set<String> inactive=new java.util.HashSet<>();
public static int glGetProgrami(int id,int what){var s=net.minecraft.client.renderer.ShaderInstance.instances.get(id);return s.closed||net.minecraft.client.renderer.ShaderInstance.linkFailures.contains(s.name)?0:1;}
public static int glGetUniformLocation(int id,String n){return inactive.contains(n)?-1:1;}}''',
    'com/mojang/blaze3d/platform/NativeImage.java': '''package com.mojang.blaze3d.platform;
public class NativeImage implements AutoCloseable {public static boolean decodeFailure;public static int widthOverride;
final java.awt.image.BufferedImage image;private NativeImage(java.awt.image.BufferedImage i){image=i;}
public static NativeImage read(java.io.InputStream in)throws java.io.IOException {if(decodeFailure)throw new java.io.IOException("injected decode");
var i=javax.imageio.ImageIO.read(in);if(i==null)throw new java.io.IOException("not PNG");return new NativeImage(i);}
public int getWidth(){return widthOverride==0?image.getWidth():widthOverride;}public int getHeight(){return image.getHeight();}public void close(){}}''',
    'net/neoforged/neoforge/client/event/RegisterShadersEvent.java': '''package net.neoforged.neoforge.client.event;
public class RegisterShadersEvent {public final java.util.Map<String,net.minecraft.client.renderer.ShaderInstance> candidates=new java.util.HashMap<>();
public final java.util.Map<String,java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance>> callbacks=new java.util.HashMap<>();
private final net.minecraft.server.packs.resources.ResourceProvider provider;public RegisterShadersEvent(net.minecraft.server.packs.resources.ResourceProvider p){provider=p;}
public net.minecraft.server.packs.resources.ResourceProvider getResourceProvider(){return provider;}
public void registerShader(net.minecraft.client.renderer.ShaderInstance s,java.util.function.Consumer<net.minecraft.client.renderer.ShaderInstance> c){candidates.put(s.name,s);callbacks.put(s.name,c);}
public void complete(){for(var n:java.util.List.copyOf(callbacks.keySet()))callbacks.get(n).accept(candidates.get(n));}}''',
    'cn/academy/port/ClassicHudConfig.java': 'package cn.academy.port;public final class ClassicHudConfig {public enum Vfx2Quality {OFF,LOW,MEDIUM}}',
    'cn/academy/port/client/ClassicMeltdownerStarterEffects.java': '''package cn.academy.port.client;
public final class ClassicMeltdownerStarterEffects {public static final class CutoffShader {static net.minecraft.client.renderer.ShaderInstance fallback;
static net.minecraft.client.renderer.ShaderInstance get(){return fallback;}}}''',
}

HARNESS = r'''
package cn.academy.port.client;
import cn.academy.port.ClassicHudConfig.Vfx2Quality;
import com.mojang.blaze3d.shaders.Uniform;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.*;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import org.lwjgl.opengl.GL20;
import java.nio.file.*;
import java.util.*;
public final class LightShieldMaterialOracle {
 static int checks;static byte[] bytes;static boolean missing;static byte[] replacement;
 static void require(boolean c,String message){checks++;if(!c)throw new AssertionError(message);}
 static ResourceProvider provider(){return r->{require(r.equals(LightShieldVfx2Material.TEXTURE),"only original mdshield resource is requested");
  if(missing)throw new java.io.IOException("missing artwork");return new ResourceManager.Resource(replacement==null?bytes:replacement);};}
 static RegisterShadersEvent register(){var event=new RegisterShadersEvent(provider());LightShieldVfx2Material.register(event);return event;}
 static RegisterShadersEvent ready(){var event=register();event.complete();return event;}
 static ShaderInstance last(){return ShaderInstance.instances.values().stream().filter(s->s.name.equals("light_shield_vfx2")).max(java.util.Comparator.comparingInt(s->s.id)).orElseThrow();}
 static void fallback(String message){require(!LightShieldVfx2Material.available(),message);require(!LightShieldVfx2Material.bind(Vfx2Quality.LOW,100),message+" bind");
  require(LightShieldVfx2Material.shader()==ClassicMeltdownerStarterEffects.CutoffShader.get(),message+" exact cutoff supplier");}
 public static void main(String[] args)throws Exception {
  bytes=Files.readAllBytes(Path.of(args[0]));
  ClassicMeltdownerStarterEffects.CutoffShader.fallback=new ShaderInstance(null,ResourceLocation.fromNamespaceAndPath("academy","original_cutoff05"),new Object());
  fallback("uninitialized retains classic");
  var initial=register();require(initial.candidates.size()==1,"one scoped material");fallback("pending shader retains classic");
  initial.complete();require(LightShieldVfx2Material.available(),"complete valid resource generation");
  require(!LightShieldVfx2Material.bind(null,100)&&!LightShieldVfx2Material.bind(Vfx2Quality.OFF,100),"invalid or off quality is rejected");
  require(!LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,-1)&&!LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,Long.MIN_VALUE),"negative phase rejected");
  require(LightShieldVfx2Material.bind(Vfx2Quality.LOW,Long.MAX_VALUE),"LOW safely binds at all nonnegative long clocks");
  require(last().getUniform("SwirlGain").value==.28F&&last().getUniform("FlowAmount").value==0&&last().getUniform("VisualPhase").value==0,"LOW static original RGB mask only");
  require(LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,1500),"MEDIUM binds accepted phase");
  require(last().getUniform("FlowAmount").value==.18F&&last().getUniform("VisualPhase").value==.25F,"MEDIUM bounded flow phase");
  require(LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,Long.MAX_VALUE),"large nonnegative phase normalized before conversion");
  float phase=last().getUniform("VisualPhase").value;require(Float.isFinite(phase)&&phase>=0&&phase<1,"large phase finite and bounded");
  LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,1500);float paused=last().getUniform("VisualPhase").value;
  LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,1500);require(last().getUniform("VisualPhase").value==paused,"identical accepted clock stays frozen");
  LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,7500);require(last().getUniform("VisualPhase").value==paused,"6000ms loop exactly repeats");
  missing=true;var failed=register();require(failed.candidates.isEmpty(),"missing resource does not register shader");fallback("missing original artwork");
  initial.complete();fallback("stale callback cannot revive failed resource generation");missing=false;
  for(byte[] bad:List.of(new byte[]{1,2,3},Arrays.copyOf(bytes,bytes.length-1))){replacement=bad;register().complete();fallback("changed or corrupt artwork");}
  replacement=null;NativeImage.decodeFailure=true;register().complete();fallback("decoder failure");NativeImage.decodeFailure=false;
  NativeImage.widthOverride=1;register().complete();fallback("wrong artwork dimensions");NativeImage.widthOverride=0;
  for(String name:List.of("ModelViewMat","ProjMat","ColorModulator","SwirlGain","FlowAmount","VisualPhase")){
   ShaderInstance.missing.add(name);failed=register();require(failed.candidates.isEmpty(),"missing uniform pre-registration "+name);fallback("missing uniform "+name);
   require(last().closed,"rejected missing-uniform candidate closed");ShaderInstance.missing.clear();ready();require(LightShieldVfx2Material.available(),"uniform recovery");
   ShaderInstance.missing.add(name);require(!LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,100),"missing uniform at bind "+name);fallback("late missing uniform "+name);ShaderInstance.missing.clear();
  }
  for(String name:List.of("Sampler0","SwirlGain","FlowAmount","VisualPhase")){
   GL20.inactive.add(name);failed=register();require(failed.candidates.isEmpty(),"inactive native uniform or sampler "+name);fallback("inactive "+name);GL20.inactive.clear();
  }
  ShaderInstance.failures.add("light_shield_vfx2");register().complete();fallback("shader constructor failure");ShaderInstance.failures.clear();
  ShaderInstance.linkFailures.add("light_shield_vfx2");failed=register();require(failed.candidates.isEmpty()&&last().closed,"unlinked candidate closed");fallback("native link failure");ShaderInstance.linkFailures.clear();
  var old=ready();long generation=LightShieldVfx2Material.generation();var current=register();
  require(LightShieldVfx2Material.generation()>generation,"reload generation advances before admission");fallback("pending current reload");
  old.complete();fallback("stale shader generation cannot revive readiness");current.complete();require(LightShieldVfx2Material.available(),"current generation loads");
  var kept=LightShieldVfx2Material.shader();old.complete();require(LightShieldVfx2Material.shader()==kept,"stale callback cannot replace current shader");
  System.setProperty("academy.vfx2.lightShieldForceFallback","true");fallback("live forced fallback");var forced=register();require(forced.candidates.isEmpty(),"forced fallback registers nothing");
  current.complete();fallback("forced fallback cannot be revived");System.clearProperty("academy.vfx2.lightShieldForceFallback");
  for(String name:List.of("SwirlGain","FlowAmount","VisualPhase")){
   ready();Uniform.fail=name;require(!LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,100),"uniform set failure before replacement "+name);
   fallback("set failure "+name);Uniform.fail="";
  }
  ready();Uniform.hook=()->register();require(!LightShieldVfx2Material.bind(Vfx2Quality.MEDIUM,100),"reload during binding revalidated before replacement");
  fallback("resource generation changed during uniform bind");ready();require(LightShieldVfx2Material.available(),"fresh generation recovers without old data");
  System.out.println("PASS|"+checks);
 }
}
'''


def java_oracle(root, material):
    helpers = runpy.run_path(str(root/'scripts/verify-railgun-vfx2-core-material.py'))
    stubs = {**helpers['STUBS'], **EXTRA_STUBS}
    home = helpers['jdk'](root)
    with tempfile.TemporaryDirectory(prefix='light-shield-material-') as directory:
        temp = Path(directory)
        sources, classes = temp/'src', temp/'classes'
        all_sources = {**stubs, 'cn/academy/port/client/LightShieldVfx2Material.java': material,
                       'cn/academy/port/client/LightShieldMaterialOracle.java': HARNESS}
        for name, content in all_sources.items():
            path = sources/name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
        helpers['run']([home/'bin/javac', '--release', '21', '-d', classes, *sorted(sources.rglob('*.java'))])
        command = [str(home/'bin/java'), '-Djava.awt.headless=true', '-cp', str(classes),
                   'cn.academy.port.client.LightShieldMaterialOracle', str(root/'src/main/resources/assets/academy/textures/effects/mdshield.png')]
        result = helpers['run'](command).strip().split('|')
        require(result[0] == 'PASS', 'actual Java resource oracle failed')
        source_path = sources/'cn/academy/port/client/LightShieldVfx2Material.java'
        mutations = [
            ('staleShaderCallback', material.replace('if (current != generation || current != resourceGeneration || forcedFallback()) return;', '')
             .replace('if (current == generation && current == resourceGeneration) ready = loaded;', 'ready = loaded;')
             .replace('current.generation() == generation && current.generation() == resourceGeneration && !forcedFallback()', '!forcedFallback()'),
             'stale'),
            ('removedUniformValidation', re.sub(r'        for \(String name : new String\[\]\{[^\n]+\}\)\n            if \(shader.getUniform\(name\) == null \|\| GL20.glGetUniformLocation\(shader.getId\(\), name\) < 0\)\n                throw new IllegalStateException\("Light Shield VFX2 missing active uniform " \+ name\);\n', '', material),
             'missing uniform pre-registration'),
            ('removedBindingGenerationCheck', material.replace('return current == ready && current.generation() == generation && current.generation() == resourceGeneration && !forcedFallback();', 'return true;'),
             'reload during binding'),
        ]
        rejected = []
        for label, changed, expected in mutations:
            require(changed != material, 'mutation did not change material: '+label)
            source_path.write_text(changed)
            helpers['run']([home/'bin/javac', '--release', '21', '-cp', classes, '-d', classes, source_path])
            broken = subprocess.run(command, text=True, capture_output=True)
            require(broken.returncode != 0 and expected in broken.stderr, 'negative control escaped expected assertion: '+label+'\n'+broken.stderr)
            rejected.append(label)
        return {'productionJavaMaterialCompiled': True, 'adapterAssertions': int(result[1]),
                'missingCorruptChangedResourcesAndDecodeDimensions': 'PASS', 'missingInactiveUniformsAndSampler': 'PASS',
                'constructorLinkAndUniformSetFailures': 'PASS', 'staleCallbacksAndReloadDuringBinding': 'PASS',
                'exactOriginalCutoffSupplierFallback': True, 'negativeControlsRejected': rejected,
                'nativeResourceReloadOrDriver': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    root = Path(__file__).resolve().parents[1]
    shader_root = root/'src/main/resources/assets/academy/shaders/core'
    java_root = root/'src/main/java/cn/academy/port/client'
    texture = root/'src/main/resources/assets/academy/textures/effects/mdshield.png'
    require(hashlib.sha256(texture.read_bytes()).hexdigest() == TEXTURE_SHA256, 'original artwork bytes changed')
    material = (java_root/'LightShieldVfx2Material.java').read_text()
    types = (java_root/'LightShieldVfx2RenderTypes.java').read_text()
    fsh, vsh = [(shader_root/('light_shield_vfx2.'+extension)).read_text() for extension in ('fsh', 'vsh')]
    descriptor = json.loads((shader_root/'light_shield_vfx2.json').read_text())
    constants = shader_contract(fsh, vsh, descriptor, material, types)
    rejected = []
    for label, altered in [
        ('alphaWeighting', fsh.replace('vec4(swirlRgb, source.a)', 'vec4(swirlRgb, source.a * source.a)')),
        ('shiftedArtwork', fsh.replace('texture(Sampler0, texCoord0)', 'texture(Sampler0, texCoord0 + vec2(VisualPhase))')),
        ('cutoffEquality', fsh.replace('color.a <= 0.05', 'color.a < 0.05')),
        ('broadGreenLift', fsh.replace('min(artworkRgb.r, artworkRgb.b)', 'artworkRgb.g')),
        ('flowEnvelope', fsh.replace('0.0, 0.18', '0.0, 0.80')),
        ('unconditionalLowFlow', fsh.replace('if (FlowAmount > 0.0)', 'if (FlowAmount >= 0.0)')),
        ('globalFeather', fsh.replace('if (texCoord0.y > 124.5 / 512.0 && texCoord0.y < 140.5 / 512.0)', 'if (true)')),
        ('widenedFeather', fsh.replace('140.5 / 512.0', '180.5 / 512.0')),
        ('shiftedFeatherReference', fsh.replace('vec2(texCoord0.x, 124.5 / 512.0)', 'vec2(texCoord0.x + VisualPhase, 124.5 / 512.0)')),
        ('referenceOpacity', fsh.replace('vec4(swirlRgb, source.a)', 'vec4(swirlRgb, feather)')),
    ]:
        try:
            shader_contract(altered, vsh, descriptor, material, types)
        except AssertionError:
            rejected.append(label)
        else:
            raise AssertionError('shader negative control escaped: '+label)
    files = [java_root/name for name in ('LightShieldVfx2.java', 'LightShieldVfx2Material.java', 'LightShieldVfx2RenderTypes.java')]
    files += [shader_root/('light_shield_vfx2.'+extension) for extension in ('json', 'vsh', 'fsh')]
    files += [texture]
    report = {'result': 'PASS', 'textureSha256': TEXTURE_SHA256, 'cpuMaterialReference': numerical_contract(texture, constants),
              'actualJavaMaterialReadiness': java_oracle(root, material), 'shaderNegativeControlsRejected': rejected,
              'lowZeroFlowUniformGuardsAllTrigonometry': True,
              'sourceHashes': {str(path.relative_to(root)): hashlib.sha256(path.read_bytes()).hexdigest() for path in files},
              'nativeMinecraftOrGpuRuntime': False,
              'limitations': ['CPU shader expressions and alpha-support bounds do not prove native shader compilation or visual quality',
                              'Resource and GL recording adapters cover reached readiness seams; native reload, terrain depth and blending remain separate',
                              'Accepted renderer pause clock and original emitted geometry are checked by the integration oracle']}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2)+'\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
