#!/usr/bin/env python3
"""Actual NEW252 edge shader EGL checks and a bounded software-GL cost sample.

Uses installed numpy, Java 21, libEGL and libGL; downloads nothing. The context
setup follows verify-md-near-eye-gl.py without importing or changing that runner.
This is not Minecraft execution, native batching, hardware timing or beauty QA.
"""
import argparse
import ctypes as C
import hashlib
import json
import math
import os
from pathlib import Path
import struct
import subprocess
import tempfile
import time
import zlib

import numpy as np


ROOT = Path(__file__).resolve().parents[1]
SHADERS = 'src/main/resources/assets/academy/shaders/core/'
JAVA = 'src/main/java/cn/academy/port/'
WHITE = 'src/main/resources/assets/academy/textures/port/white.png'
WHITE_SHA = '7ac2abfce2be8b46dd9826d597d94edeefc1570b4a992622721b90a1850e3ee9'
WIDTH = HEIGHT = 256
PHASES = [0., 15., 30., 45., 60., 75., 90., 105., 119.999, 120., 2399.999, 2400.]
# A photometric regression bound for the corrected continuous white crest.
# This is an opacity/color floor, not a claim about visibility in native scenes.
CORE_ALPHA_FLOOR = .67
CORE_CHANNEL_FLOOR = .92
checks = 0
inputs = {}


def check(condition, message):
    global checks
    checks += 1
    if not condition:
        raise AssertionError(message)


def read(relative):
    data = (ROOT / relative).read_bytes()
    digest = hashlib.sha256(data).hexdigest()
    check(relative not in inputs or inputs[relative] == digest, 'Input changed: ' + relative)
    inputs[relative] = digest
    return data


def white_pixels(data):
    check(hashlib.sha256(data).hexdigest() == WHITE_SHA, 'Original white texture changed')
    check(data[:8] == b'\x89PNG\r\n\x1a\n', 'White asset is not PNG')
    offset, compressed = 8, bytearray()
    while offset < len(data):
        length, = struct.unpack('>I', data[offset:offset + 4])
        kind, chunk = data[offset + 4:offset + 8], data[offset + 8:offset + 8 + length]
        if kind == b'IHDR':
            check(struct.unpack('>IIBBBBB', chunk) == (1, 1, 8, 6, 0, 0, 0), 'Unexpected pinned texture format')
        if kind == b'IDAT':
            compressed.extend(chunk)
        offset += 12 + length
    decoded = zlib.decompress(compressed)
    check(decoded == b'\0\xff\xff\xff\xff', 'Pinned white texture must decode to opaque white')
    return np.frombuffer(decoded[1:], dtype=np.uint8).copy()


def emitted_rims(report, java_home):
    """Compile actual pure sources and export their live rim vertices for cost draws."""
    closure = ['highpower/core/FoldedMirrorGeometry.java', 'highpower/core/ZeroGapArmorGeometry.java',
               'highpower/core/ZeroGapArmorTiming.java', 'client/FoldedMirrorState.java',
               'client/FoldedMirrorMesh.java', 'client/ZeroGapArmorState.java',
               'client/ZeroGapArmorMesh.java', 'client/ZeroGapArmorEnhancedMesh.java']
    sources = {JAVA + path: read(JAVA + path) for path in closure}
    fixture = '''
import cn.academy.port.client.*;
import cn.academy.port.highpower.core.*;
import cn.academy.port.highpower.core.FoldedMirrorGeometry.Vector;
import java.util.UUID;
public class ArmorRimExport {
 public static void main(String[] args) {
  var feet=new Vector(0,0,0);int mask=ZeroGapArmorGeometry.capture(feet,0,0).activeMask();
  var id=new ZeroGapArmorState.Identity(8,new UUID(252,58),2,3,10);
  var frame=new ZeroGapArmorState.Frame(id,4,ZeroGapArmorState.ACTIVE,feet,0,0,mask,mask,0,-1,feet);
  int quads=0;
  for(int plate=0;plate<6;plate++) for(var face:ZeroGapArmorEnhancedMesh.plate(frame,plate,-1,0)) {
   if(!ZeroGapArmorEnhancedMesh.isEnhancedRim(face))continue;quads++;
   for(int index:new int[]{0,1,2,0,2,3}) {
    var p=face.vertices().get(index);var uv=face.uvs().get(index);
    System.out.println(p.x()+" "+p.y()+" "+p.z()+" "+uv.u()+" "+uv.v()+" "+
     ((face.color()>>>16&255)/255.0)+" "+((face.color()>>>8&255)/255.0)+" "+
     ((face.color()&255)/255.0)+" "+(face.alpha()/255.0));
   }
  }
  if(quads!=96)throw new AssertionError("Expected96 live rim quads, got "+quads);
 }
}
'''
    (report / 'ArmorRimExport.java').write_text(fixture)
    java = java_home / 'bin/java'
    version = subprocess.run([str(java), '-version'], capture_output=True, text=True, check=True)
    (report / 'java-version.log').write_text(version.stdout + version.stderr)
    check('version "21.' in version.stderr, 'Java21 required for actual mesh export')
    with tempfile.TemporaryDirectory(prefix='armor-rim-gl-') as temporary:
        temp = Path(temporary)
        files = []
        for relative, data in sources.items():
            path = temp / 'source' / relative
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_bytes(data)
            files.append(path)
        destination = temp / 'classes'
        command = [java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-source', '21', '-target', '21',
                   '-proc:none', '-implicit:none', '-sourcepath', '', '-d', destination, *files, report / 'ArmorRimExport.java']
        env = dict(os.environ)
        for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH'):
            env.pop(key, None)
        compiled = subprocess.run(list(map(str, command)), capture_output=True, text=True, env=env)
        (report / 'mesh-compile.log').write_text(compiled.stdout + compiled.stderr)
        check(compiled.returncode == 0, 'Actual mesh export did not compile; see mesh-compile.log')
        exported = subprocess.run([str(java), '-cp', str(destination), 'ArmorRimExport'], capture_output=True, text=True, env=env)
        (report / 'mesh-export.log').write_text(exported.stderr)
        check(exported.returncode == 0, 'Actual mesh export failed')
        (report / 'rim-vertices.txt').write_text(exported.stdout)
        vertices = np.array([[float(value) for value in row.split()] for row in exported.stdout.splitlines()], dtype=np.float32)
    check(vertices.shape == (576, 9) and np.all(np.isfinite(vertices)), 'Unexpected actual live-rim submission')
    return vertices


class Context:
    def __init__(self):
        os.environ.setdefault('MESA_SHADER_CACHE_DISABLE', 'true')
        os.environ.setdefault('LIBGL_ALWAYS_SOFTWARE', 'true')
        self.E, self.G = C.CDLL('libEGL.so.1'), C.CDLL('libGL.so.1')
        P, I, U, F = C.c_void_p, C.c_int, C.c_uint, C.c_float
        self.P, self.I, self.U, self.F = P, I, U, F
        def bind(library, name, result, *args):
            function = getattr(library, name)
            function.restype, function.argtypes = result, args
            return function
        egl = lambda name, result, *args: bind(self.E, name, result, *args)
        egl('eglGetProcAddress', P, C.c_char_p)
        address = self.E.eglGetProcAddress(b'eglGetPlatformDisplayEXT')
        check(bool(address), 'Surfaceless EGL platform entrypoint unavailable')
        platform = C.CFUNCTYPE(P, U, P, C.POINTER(I))(address)
        self.display = platform(0x31DD, None, None)
        egl('eglInitialize', U, P, C.POINTER(I), C.POINTER(I))
        major, minor = I(), I()
        check(self.E.eglInitialize(self.display, C.byref(major), C.byref(minor)), 'EGL initialization failed')
        egl('eglBindAPI', U, U)
        check(self.E.eglBindAPI(0x30A2), 'OpenGL API unavailable')
        egl('eglChooseConfig', U, P, C.POINTER(I), C.POINTER(P), I, C.POINTER(I))
        attributes = (I * 15)(0x3033, 1, 0x3040, 8, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3038, 0, 0)
        config, count = P(), I()
        check(self.E.eglChooseConfig(self.display, attributes, C.byref(config), 1, C.byref(count)) and count.value, 'EGL config unavailable')
        egl('eglCreatePbufferSurface', P, P, P, C.POINTER(I))
        self.surface = self.E.eglCreatePbufferSurface(self.display, config, (I * 5)(0x3057, WIDTH, 0x3056, HEIGHT, 0x3038))
        egl('eglCreateContext', P, P, P, P, C.POINTER(I))
        self.context = self.E.eglCreateContext(self.display, config, None, (I * 7)(0x3098, 3, 0x30FB, 2, 0x30FD, 1, 0x3038))
        egl('eglMakeCurrent', U, P, P, P, P)
        check(self.surface and self.context and self.E.eglMakeCurrent(self.display, self.surface, self.surface, self.context), 'EGL current context failed')
        for name, result, signature in [
            ('glGetString', C.c_char_p, [U]), ('glGetError', U, []), ('glFinish', None, []),
            ('glCreateShader', U, [U]), ('glShaderSource', None, [U, I, C.POINTER(C.c_char_p), C.POINTER(I)]),
            ('glCompileShader', None, [U]), ('glGetShaderiv', None, [U, U, C.POINTER(I)]),
            ('glGetShaderInfoLog', None, [U, I, C.POINTER(I), C.c_char_p]), ('glDeleteShader', None, [U]),
            ('glCreateProgram', U, []), ('glAttachShader', None, [U, U]), ('glLinkProgram', None, [U]),
            ('glGetProgramiv', None, [U, U, C.POINTER(I)]), ('glGetProgramInfoLog', None, [U, I, C.POINTER(I), C.c_char_p]),
            ('glUseProgram', None, [U]), ('glGetUniformLocation', I, [U, C.c_char_p]), ('glUniform1i', None, [I, I]),
            ('glUniform1f', None, [I, F]), ('glUniform4f', None, [I, F, F, F, F]),
            ('glUniformMatrix4fv', None, [I, I, U, C.POINTER(F)]),
            ('glGenVertexArrays', None, [I, C.POINTER(U)]), ('glBindVertexArray', None, [U]),
            ('glGenBuffers', None, [I, C.POINTER(U)]), ('glBindBuffer', None, [U, U]), ('glBufferData', None, [U, C.c_ssize_t, P, U]),
            ('glGetAttribLocation', I, [U, C.c_char_p]), ('glVertexAttribPointer', None, [U, I, U, U, I, P]),
            ('glEnableVertexAttribArray', None, [U]), ('glViewport', None, [I, I, I, I]),
            ('glClearColor', None, [F, F, F, F]), ('glClearDepth', None, [C.c_double]), ('glClear', None, [U]),
            ('glDrawArrays', None, [U, I, I]), ('glReadPixels', None, [I, I, I, I, U, U, P]),
            ('glEnable', None, [U]), ('glDisable', None, [U]), ('glDepthFunc', None, [U]), ('glDepthMask', None, [U]),
            ('glColorMask', None, [U, U, U, U]), ('glBlendFuncSeparate', None, [U, U, U, U]),
            ('glGenTextures', None, [I, C.POINTER(U)]), ('glBindTexture', None, [U, U]),
            ('glTexImage2D', None, [U, I, I, I, I, I, U, U, P]), ('glTexParameteri', None, [U, U, I]),
            ('glGenFramebuffers', None, [I, C.POINTER(U)]), ('glBindFramebuffer', None, [U, U]),
            ('glFramebufferTexture2D', None, [U, U, U, U, I]), ('glCheckFramebufferStatus', U, [U])]:
            bind(self.G, name, result, *signature)
        self.renderer = self.G.glGetString(0x1F01).decode()
        self.version = self.G.glGetString(0x1F02).decode()

    def compile(self, vertex, fragment, uniforms, report):
        G, I, U = self.G, self.I, self.U
        self.program = G.glCreateProgram()
        for label, source, kind in [('vertex', vertex, 0x8B31), ('fragment', fragment, 0x8B30)]:
            shader = G.glCreateShader(kind)
            text = C.c_char_p(source)
            G.glShaderSource(shader, 1, C.byref(text), None)
            G.glCompileShader(shader)
            ok, log = I(), C.create_string_buffer(65536)
            G.glGetShaderiv(shader, 0x8B81, C.byref(ok))
            G.glGetShaderInfoLog(shader, len(log), None, log)
            (report / (label + '-compile.log')).write_bytes(log.value)
            check(ok.value, 'Actual ' + label + ' shader did not compile')
            G.glAttachShader(self.program, shader)
            G.glDeleteShader(shader)
        G.glLinkProgram(self.program)
        ok, log = I(), C.create_string_buffer(65536)
        G.glGetProgramiv(self.program, 0x8B82, C.byref(ok))
        G.glGetProgramInfoLog(self.program, len(log), None, log)
        (report / 'program-link.log').write_bytes(log.value)
        check(ok.value, 'Actual edge program did not link')
        self.locations = {name: G.glGetUniformLocation(self.program, name.encode()) for name in uniforms}
        for name, location in self.locations.items():
            check(location >= 0, 'Required production uniform inactive: ' + name)
        G.glUseProgram(self.program)

    def target(self, pixels):
        G, U = self.G, self.U
        fbo = U()
        G.glGenFramebuffers(1, C.byref(fbo))
        G.glBindFramebuffer(0x8D40, fbo)
        for attachment, internal, channels in [(0x8CE0, 0x8814, 0x1908), (0x8D00, 0x8CAC, 0x1902)]:
            target = U()
            G.glGenTextures(1, C.byref(target))
            G.glBindTexture(0x0DE1, target)
            G.glTexImage2D(0x0DE1, 0, internal, WIDTH, HEIGHT, 0, channels, 0x1406, None)
            G.glTexParameteri(0x0DE1, 0x2801, 0x2600)
            G.glTexParameteri(0x0DE1, 0x2800, 0x2600)
            G.glFramebufferTexture2D(0x8D40, attachment, 0x0DE1, target, 0)
        check(G.glCheckFramebufferStatus(0x8D40) == 0x8CD5, 'Float color/depth target incomplete')
        texture, vao, vbo = U(), U(), U()
        G.glGenTextures(1, C.byref(texture))
        G.glBindTexture(0x0DE1, texture)
        G.glTexImage2D(0x0DE1, 0, 0x8058, 1, 1, 0, 0x1908, 0x1401, pixels.ctypes.data)
        for property_ in (0x2801, 0x2800):
            G.glTexParameteri(0x0DE1, property_, 0x2601)
        G.glGenVertexArrays(1, C.byref(vao))
        G.glBindVertexArray(vao)
        G.glGenBuffers(1, C.byref(vbo))
        G.glBindBuffer(0x8892, vbo)
        for name, components, offset in [('Position', 3, 0), ('UV0', 2, 12), ('Color', 4, 20)]:
            attribute = G.glGetAttribLocation(self.program, name.encode())
            check(attribute >= 0, 'Required vertex attribute missing: ' + name)
            G.glEnableVertexAttribArray(attribute)
            G.glVertexAttribPointer(attribute, components, 0x1406, 0, 36, self.P(offset))
        G.glViewport(0, 0, WIDTH, HEIGHT)
        G.glEnable(0x0B71)
        G.glDepthFunc(0x0203)  # LEQUAL
        G.glDisable(0x0B44)  # NO_CULL
        G.glColorMask(1, 1, 1, 1)
        G.glUniform1i(self.locations['Sampler0'], 0)
        self.matrices(np.eye(4), np.eye(4))

    def matrices(self, model, projection):
        for name, matrix in [('ModelViewMat', model), ('ProjMat', projection)]:
            data = np.asarray(matrix, dtype=np.float32)
            self.G.glUniformMatrix4fv(self.locations[name], 1, 1, data.ctypes.data_as(C.POINTER(self.F)))

    def upload(self, vertices):
        self.G.glBufferData(0x8892, vertices.nbytes, vertices.ctypes.data, 0x88E0)
        self.vertex_count = len(vertices)

    def draw(self, phase, *, blend=False, background=(0., 0., 0., 0.), depth=1., depth_write=False, mod=(1., 1., 1., 1.), readback=True):
        G = self.G
        G.glUniform1f(self.locations['VisualPhase'], phase)
        G.glUniform4f(self.locations['ColorModulator'], *mod)
        (G.glEnable if blend else G.glDisable)(0x0BE2)
        G.glBlendFuncSeparate(0x0302, 0x0303, 1, 0x0303)  # Standard translucent RGB and alpha.
        G.glDepthMask(1)
        G.glClearColor(*background)
        G.glClearDepth(depth)
        G.glClear(0x4000 | 0x0100)
        G.glDepthMask(int(depth_write))  # COLOR_WRITE leaves the real depth target untouched.
        G.glDrawArrays(4, 0, self.vertex_count)
        if not readback:
            return None
        color = np.empty((HEIGHT, WIDTH, 4), dtype=np.float32)
        z = np.empty((HEIGHT, WIDTH), dtype=np.float32)
        G.glReadPixels(0, 0, WIDTH, HEIGHT, 0x1908, 0x1406, color.ctypes.data)
        G.glReadPixels(0, 0, WIDTH, HEIGHT, 0x1902, 0x1406, z.ctypes.data)
        check(G.glGetError() == 0, 'GL error in actual draw/readback')
        return color, z


def quad(rgb, alpha, constant_u=None, constant_v=None):
    positions = [(-1, -1), (1, -1), (1, 1), (-1, -1), (1, 1), (-1, 1)]
    return np.array([[x, y, 0, (x + 1) / 2 if constant_u is None else constant_u,
                      (y + 1) / 2 if constant_v is None else constant_v, *rgb, alpha]
                     for x, y in positions], dtype=np.float32)


def readable_core(color):
    return bool(np.all(np.isfinite(color)) and np.min(color[:, :, 3]) >= CORE_ALPHA_FLOOR
                and np.min(color[:, :, :3]) >= CORE_CHANNEL_FLOOR)


def prove(args, evidence):
    read('scripts/verify-zero-gap-armor-material-gl.py')
    descriptor = json.loads(read(SHADERS + 'zero_gap_armor_edge.json'))
    check(descriptor['vertex'] == descriptor['fragment'] == 'academy:zero_gap_armor_edge', 'Unexpected shader selection')
    check(descriptor['samplers'] == [{'name': 'Sampler0'}], 'Only pinned white sampler is allowed')
    vertex = read(SHADERS + 'zero_gap_armor_edge.vsh')
    fragment = read(SHADERS + 'zero_gap_armor_edge.fsh')
    material = read(JAVA + 'client/ZeroGapArmorMaterial.java').decode()
    for required in ['.setTransparencyState(TRANSLUCENT_TRANSPARENCY)', '.setDepthTestState(LEQUAL_DEPTH_TEST)',
                     '.setWriteMaskState(COLOR_WRITE)', '.setCullState(NO_CULL)', '.setOutputState(PARTICLES_TARGET)']:
        check(required in material, 'GL state fixture no longer matches material declaration: ' + required)
    pixels = white_pixels(read(WHITE))
    rims = emitted_rims(args.report, args.java_home)
    context = Context()
    evidence.update(renderer=context.renderer, gl_version=context.version, resolution=[WIDTH, HEIGHT])
    context.compile(vertex, fragment, [entry['name'] for entry in descriptor['uniforms']] + ['Sampler0'], args.report)
    context.target(pixels)
    # Derive BOTH bands' actual color and alpha from emitted production vertices.
    # An old fixture opacity must never conceal a production material correction.
    attributes, counts = np.unique(rims[:, 5:9], axis=0, return_counts=True)
    check(attributes.shape == (2, 4) and np.all(counts == 288), 'Expected two uniform, equal-count production rim bands')
    bands = {}
    for rgba in attributes:
        name = 'inner' if rgba[0] > .7 else 'outer'  # Actual shader's material discriminator.
        check(name not in bands, 'Production bands no longer select distinct shader branches')
        bands[name] = (rgba[:3].astype(float).tolist(), float(rgba[3]))
    check(set(bands) == {'inner', 'outer'}, 'Missing production band')
    evidence['emitted_band_attributes'] = {name: dict(rgb=rgb, alpha=alpha) for name, (rgb, alpha) in bands.items()}
    samples, max_period_delta, max_uv_seam_delta = {}, 0., 0.
    for name, (rgb, alpha) in bands.items():
        context.upload(quad(rgb, alpha))
        frames = {}
        for phase in PHASES:
            color, depth = context.draw(phase)
            check(np.all(np.isfinite(color)) and np.min(color) >= 0 and np.max(color) <= 1, 'Invalid or unbounded raster values')
            check(float(np.max(color[:, :, 3])) <= alpha + 1e-6, 'Band exceeds its authored opacity')
            check(np.min(color[8:-8, :, 3]) > .005, 'Positive-coverage band develops a temporal hole')
            check(np.all(depth == 1.), 'Color-only material wrote depth')
            if name == 'inner':
                check(readable_core(color), 'Corrected crest falls below continuous white/opacity floor')
            frames[phase] = color
        for a, b in [(0., 120.), (0., 2400.), (119.999, 120.), (2399.999, 2400.)]:
            delta = float(np.max(np.abs(frames[a] - frames[b])))
            max_period_delta = max(max_period_delta, delta)
            check(delta < 5e-5, 'Material jumps at phase or bounded-clock wrap')
        repeated, _ = context.draw(0.)
        check(np.array_equal(repeated, frames[0.]), 'Identical accepted phase does not remain frozen')
        check(float(np.max(np.abs(frames[0.] - frames[30.]))) > .02, 'Travel flow is absent or frozen')
        for phase in PHASES:
            endpoints = []
            for u in (0., 1.):
                context.upload(quad(rgb, alpha, constant_u=u))
                endpoints.append(context.draw(phase)[0])
            delta = float(np.max(np.abs(endpoints[0] - endpoints[1])))
            max_uv_seam_delta = max(max_uv_seam_delta, delta)
            check(delta < 5e-5, 'Perimeter U=0/1 join visibly resets the material')
        cross_band = []
        for v in (0., .025, .25, .5, .75, .975, 1.):
            context.upload(quad(rgb, alpha, constant_u=.25, constant_v=v))
            color, _ = context.draw(0.)
            cross_band.append(float(color[128, 128, 3]))
        check(all(math.isfinite(value) and 0 <= value <= alpha + 1e-6 for value in cross_band), 'Invalid cross-band response')
        check(cross_band[3] > cross_band[2] > cross_band[1], 'Transverse shoulder is not graded')
        check(max(abs(a - b) for a, b in zip(cross_band, reversed(cross_band))) < 1e-6, 'Cross-band falloff is asymmetric')
        if name == 'outer':
            check(cross_band[0] == cross_band[-1] == 0., 'Shoulder boundary should feather to transparent')
        else:
            check(min(cross_band) >= CORE_ALPHA_FLOOR, 'Corrected crest loses its opacity floor at the shoulder boundary')
        samples[name] = dict(alpha_min=float(frames[0.][8:-8, :, 3].min()), alpha_max=float(frames[0.][:, :, 3].max()),
                             cross_band_alpha=cross_band)
        context.upload(quad(rgb, 0.))
        color, depth = context.draw(0.)
        check(np.all(color == 0) and np.all(depth == 1), 'Zero vertex alpha writes color/depth')
        context.upload(quad(rgb, alpha))
        color, depth = context.draw(0., mod=(1., 1., 1., 0.))
        check(np.all(color == 0) and np.all(depth == 1), 'Zero global alpha writes color/depth')
    evidence.update(samples=samples, max_phase_boundary_delta=max_period_delta, max_perimeter_seam_delta=max_uv_seam_delta)

    rgb, alpha = bands['inner']
    # Reproduce the previous dim vertex-alpha input against the actual current
    # linked program. The production floor must reject it, rather than merely
    # proving that two equally dim test fixtures match one another.
    context.upload(quad(rgb, 112 / 255))
    dim_control, _ = context.draw(0.)
    check(not readable_core(dim_control), 'Former dim-alpha negative control escaped corrected core floor')
    evidence['core_readability_bound'] = dict(minimum_alpha=CORE_ALPHA_FLOOR, minimum_rgb_channel=CORE_CHANNEL_FLOOR,
                                             dim_alpha_control=112 / 255,
                                             dim_control_alpha_min=float(dim_control[:, :, 3].min()),
                                             dim_control_alpha_max=float(dim_control[:, :, 3].max()),
                                             negative_control_rejected=True,
                                             native_scene_readability_not_claimed=True)
    context.upload(quad(rgb, alpha, constant_u=.25, constant_v=.5))
    raw, _ = context.draw(0.)
    background = np.array([.11, .17, .23, .4], dtype=np.float32)
    for depth in (.5, .75, 1.):
        blended, z = context.draw(0., blend=True, background=background, depth=depth)
        expected = np.empty_like(raw)
        expected[:, :, :3] = raw[:, :, :3] * raw[:, :, 3:4] + background[:3] * (1 - raw[:, :, 3:4])
        expected[:, :, 3] = raw[:, :, 3] + background[3] * (1 - raw[:, :, 3])
        check(np.max(np.abs(blended - expected)) < 2e-6, 'Translucent blend does not preserve bounded scene composition')
        check(np.all(z == depth), 'Visible/equal-depth band changed scene depth')
    occluded, z = context.draw(0., blend=True, background=background, depth=.25)
    check(np.max(np.abs(occluded - background)) < 1e-7 and np.all(z == .25), 'Occluded band draws through nearer terrain')
    _, z = context.draw(0., depth_write=True)
    check(np.all(z == .5), 'Depth-writing negative control was not detected')
    evidence['depth_state'] = {'LEQUAL_equal_depth_visible': True, 'nearer_occluder_hides_band': True,
                               'COLOR_WRITE_preserves_depth': True, 'depth_write_negative_control_detected': True,
                               'translucent_composition_matches': True}

    model = np.eye(4, dtype=np.float32)
    model[1, 3], model[2, 3] = -1.1, -4.
    context.matrices(model, np.diag([.45, .9, .1, 1.]))
    timings = {}
    for owners in (1, 4):
        context.upload(np.tile(rims, (owners, 1)))
        image, _ = context.draw(30., blend=True)
        check(np.count_nonzero(image[:, :, 3]) > 0, 'Cost fixture did not actually rasterize live rims')
        for i in range(12):
            context.draw(float(i), blend=True, readback=False)
        context.G.glFinish()
        elapsed = []
        for batch in range(9):
            start = time.perf_counter()
            for frame in range(8):
                context.draw(float(batch * 8 + frame), blend=True, readback=False)
            context.G.glFinish()
            elapsed.append((time.perf_counter() - start) * 1000 / 8)
        timings[str(owners)] = dict(live_rim_quads=96 * owners, submitted_triangle_vertices=576 * owners,
                                   minimum_ms=min(elapsed), median_ms=float(np.median(elapsed)),
                                   maximum_ms=max(elapsed), batch_mean_ms=elapsed)
    check(context.G.glGetError() == 0, 'GL error during bounded cost sample')
    evidence['cost_sample'] = dict(controllers=timings, warmup_frames=12, batches=9, frames_per_batch=8,
                                  same_pose_overdraw=True, includes_cpu_submission_and_glFinish=True,
                                  excludes_original_fill_sides_ghosts_contacts_and_engine=True)
    for path in list(inputs):
        read(path)
    evidence.update(result='PASS_OFFSCREEN_GL', checks=checks, input_sha256=inputs,
                    limits=['Actual shader bytes compiled and rasterized using the recorded EGL/GL driver.',
                            'Render-state declarations are source-checked; equivalent GL states are explicitly configured, not executed by Minecraft.',
                            'Cost draws use actual pure-mesh live rim vertices, triangulated and repeated at one pose for bounded overdraw.',
                            'Timing is software-capable driver wall time at 256x256, not native engine or hardware performance acceptance.',
                            'Nonfinite phases are rejected by the separate production mesh tests; GLSL behavior for NaN/infinity is not claimed.',
                            'No native gameplay, resource-reload lifecycle, engine framebuffer integration or visual beauty acceptance.'])


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--java-home', type=Path, default=Path('/usr/lib/jvm/java-21-openjdk-amd64'))
    args = parser.parse_args()
    args.report = args.report.resolve()
    if args.report.exists():
        raise SystemExit('Report directory exists; choose a fresh path')
    args.report.mkdir(parents=True)
    evidence = {'result': 'FAIL', 'contract': 'zero-gap-armor-material-gl-v1'}
    try:
        prove(args, evidence)
    except Exception as error:
        evidence.update(error=str(error), checks=checks, input_sha256=inputs)
        raise
    finally:
        (args.report / 'evidence.json').write_text(json.dumps(evidence, indent=2) + '\n')
    print(json.dumps({'result': evidence['result'], 'checks': checks, 'renderer': evidence['renderer'],
                      'phase_boundary_delta': evidence['max_phase_boundary_delta'],
                      'perimeter_seam_delta': evidence['max_perimeter_seam_delta'],
                      'cost_median_ms': {k: v['median_ms'] for k, v in evidence['cost_sample']['controllers'].items()},
                      'report': str(args.report)}, indent=2))


if __name__ == '__main__':
    main()
