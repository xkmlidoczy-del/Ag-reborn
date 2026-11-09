#!/usr/bin/env python3
"""Validate actual MD shader cutoff, fade, camera-space and depth-write behavior.

Requires Python numpy and system libEGL/libGL. Uses a software-capable EGL context.
Pass the Minecraft resource JAR to compare against the actual vanilla core shader.
This does not claim native gameplay or visual acceptance.
"""
import ctypes as C
import json
import os
from pathlib import Path
import numpy as np
import argparse
from zipfile import ZipFile

parser = argparse.ArgumentParser(description='Actual offscreen GL checks; not native game visual acceptance')
parser.add_argument('--minecraft-resources', type=Path, required=True)
arguments = parser.parse_args()
ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/academy/shaders/core'
os.environ.setdefault('MESA_SHADER_CACHE_DISABLE', 'true')
E = C.CDLL('libEGL.so.1')
G = C.CDLL('libGL.so.1')
def egl(name, restype, *args):
    f = getattr(E, name); f.restype = restype; f.argtypes = args; return f
def gl(name, restype, *args):
    f = getattr(G, name); f.restype = restype; f.argtypes = args; return f
P = C.c_void_p; I = C.c_int; U = C.c_uint; F = C.c_float
egl('eglGetProcAddress', P, C.c_char_p)
platform = C.CFUNCTYPE(P, U, P, C.POINTER(I))(E.eglGetProcAddress(b'eglGetPlatformDisplayEXT'))
d = platform(0x31DD, None, None)
egl('eglInitialize', U, P, C.POINTER(I), C.POINTER(I))
major = I(); minor = I(); assert E.eglInitialize(d, C.byref(major), C.byref(minor))
egl('eglBindAPI', U, U); assert E.eglBindAPI(0x30A2)
egl('eglChooseConfig', U, P, C.POINTER(I), C.POINTER(P), I, C.POINTER(I))
attrs = (I * 15)(0x3033, 1, 0x3040, 8, 0x3024, 8, 0x3023, 8, 0x3022, 8, 0x3021, 8, 0x3038, 0, 0)
config=P(); n=I(); assert E.eglChooseConfig(d, attrs, C.byref(config), 1, C.byref(n)) and n.value
egl('eglCreatePbufferSurface', P, P, P, C.POINTER(I))
surface = E.eglCreatePbufferSurface(d, config, (I*5)(0x3057, 256, 0x3056, 256, 0x3038)); assert surface
egl('eglCreateContext', P, P, P, P, C.POINTER(I))
context = E.eglCreateContext(d, config, None, (I*7)(0x3098,3,0x30FB,2,0x30FD,1,0x3038)); assert context
egl('eglMakeCurrent', U, P, P, P, P); assert E.eglMakeCurrent(d,surface,surface,context)
gl('glGetString', C.c_char_p, U)
print('Renderer:',G.glGetString(0x1F01).decode(), 'Version:',G.glGetString(0x1F02).decode())
for name,r,args in [
 ('glCreateShader',U,[U]),('glShaderSource',None,[U,I,C.POINTER(C.c_char_p),C.POINTER(I)]),
 ('glCompileShader',None,[U]),('glGetShaderiv',None,[U,U,C.POINTER(I)]),
 ('glGetShaderInfoLog',None,[U,I,C.POINTER(I),C.c_char_p]),('glCreateProgram',U,[]),
 ('glAttachShader',None,[U,U]),('glLinkProgram',None,[U]),('glGetProgramiv',None,[U,U,C.POINTER(I)]),
 ('glGetProgramInfoLog',None,[U,I,C.POINTER(I),C.c_char_p]),('glUseProgram',None,[U]),
 ('glGetUniformLocation',I,[U,C.c_char_p]),('glUniform1i',None,[I,I]),('glUniform1f',None,[I,F]),
 ('glUniform4f',None,[I,F,F,F,F]),('glUniformMatrix4fv',None,[I,I,U,C.POINTER(F)]),
 ('glGenVertexArrays',None,[I,C.POINTER(U)]),('glBindVertexArray',None,[U]),
 ('glGenBuffers',None,[I,C.POINTER(U)]),('glBindBuffer',None,[U,U]),('glBufferData',None,[U,C.c_ssize_t,P,U]),
 ('glGetAttribLocation',I,[U,C.c_char_p]),('glVertexAttribPointer',None,[U,I,U,U,I,P]),
 ('glEnableVertexAttribArray',None,[U]),('glViewport',None,[I,I,I,I]),('glClearColor',None,[F,F,F,F]),
 ('glClear',None,[U]),('glDrawArrays',None,[U,I,I]),('glReadPixels',None,[I,I,I,I,U,U,P]),
 ('glGenTextures',None,[I,C.POINTER(U)]),('glBindTexture',None,[U,U]),
 ('glTexImage2D',None,[U,I,I,I,I,I,U,U,P]),('glTexParameteri',None,[U,U,I]),
 ('glGenFramebuffers',None,[I,C.POINTER(U)]),('glBindFramebuffer',None,[U,U]),
 ('glFramebufferTexture2D',None,[U,U,U,U,I]),('glCheckFramebufferStatus',U,[U]),('glGetError',U,[])]: gl(name,r,*args)

for name, result, signature in [
    ('glEnable', None, [U]), ('glDisable', None, [U]),
    ('glDepthFunc', None, [U]), ('glDepthMask', None, [U]),
    ('glClearDepth', None, [C.c_double]),
    ('glIsProgram', U, [U]), ('glDeleteProgram', None, [U])]:
    gl(name, result, *signature)

checks = 0


def check(condition, message):
    global checks
    checks += 1
    assert condition, message


def program(vertex_source, fragment_source, uniforms):
    result = G.glCreateProgram()
    for source, kind in [(vertex_source, 0x8B31), (fragment_source, 0x8B30)]:
        shader = G.glCreateShader(kind)
        text = C.c_char_p(source)
        G.glShaderSource(shader, 1, C.byref(text), None)
        G.glCompileShader(shader)
        ok = I()
        G.glGetShaderiv(shader, 0x8B81, C.byref(ok))
        log = C.create_string_buffer(65536)
        G.glGetShaderInfoLog(shader, len(log), None, log)
        check(ok.value != 0, log.value.decode())
        G.glAttachShader(result, shader)
    G.glLinkProgram(result)
    ok = I()
    G.glGetProgramiv(result, 0x8B82, C.byref(ok))
    log = C.create_string_buffer(65536)
    G.glGetProgramInfoLog(result, len(log), None, log)
    check(ok.value != 0, log.value.decode())
    for uniform in uniforms:
        check(G.glGetUniformLocation(result, uniform.encode()) >= 0, 'Inactive uniform: ' + uniform)
    return result


with ZipFile(arguments.minecraft_resources) as minecraft:
    vanilla_vertex = minecraft.read('assets/minecraft/shaders/core/position_tex_color.vsh')
    vanilla_fragment = minecraft.read('assets/minecraft/shaders/core/position_tex_color.fsh')

classic = {
    'core': program(vanilla_vertex, vanilla_fragment, ['ModelViewMat', 'ProjMat', 'ColorModulator', 'Sampler0']),
    'glow': program(vanilla_vertex, (ROOT / 'classic_meltdowner_cutoff05.fsh').read_bytes(),
                    ['ModelViewMat', 'ProjMat', 'ColorModulator', 'Sampler0'])
}
candidate = {}
fade_ends = {}
for layer in ['core', 'glow']:
    config = json.loads((ROOT / ('md_near_eye_' + layer + '.json')).read_text())
    vertex = config['vertex'].split(':', 1)[1]
    fragment = config['fragment'].split(':', 1)[1]
    candidate[layer] = program((ROOT / (vertex + '.vsh')).read_bytes(),
                               (ROOT / (fragment + '.fsh')).read_bytes(),
                               [u['name'] for u in config['uniforms']] + ['Sampler0'])
    fade_ends[layer] = next(u['values'][0] for u in config['uniforms'] if u['name'] == 'FadeEnd')
check(fade_ends == {'core': .8, 'glow': 1.6}, 'Production fade ranges changed')

# A floating-point color/depth target can distinguish a discarded fragment from
# one that blended to zero alpha but still wrote the classic depth buffer.
fbo = U()
G.glGenFramebuffers(1, C.byref(fbo))
G.glBindFramebuffer(0x8D40, fbo)
targets = []
for attachment, internal, channels in [(0x8CE0, 0x8814, 0x1908), (0x8D00, 0x8CAC, 0x1902)]:
    target = U()
    G.glGenTextures(1, C.byref(target))
    G.glBindTexture(0x0DE1, target)
    G.glTexImage2D(0x0DE1, 0, internal, 256, 256, 0, channels, 0x1406, None)
    G.glTexParameteri(0x0DE1, 0x2801, 0x2600)
    G.glTexParameteri(0x0DE1, 0x2800, 0x2600)
    G.glFramebufferTexture2D(0x8D40, attachment, 0x0DE1, target, 0)
    targets.append(target)
check(G.glCheckFramebufferStatus(0x8D40) == 0x8CD5, 'Float color/depth FBO incomplete')
sample_texture = U()
G.glGenTextures(1, C.byref(sample_texture))
G.glBindTexture(0x0DE1, sample_texture)
G.glTexParameteri(0x0DE1, 0x2801, 0x2600)
G.glTexParameteri(0x0DE1, 0x2800, 0x2600)
vao, vbo = U(), U()
G.glGenVertexArrays(1, C.byref(vao))
G.glBindVertexArray(vao)
G.glGenBuffers(1, C.byref(vbo))
G.glBindBuffer(0x8892, vbo)
G.glViewport(0, 0, 256, 256)
G.glEnable(0x0B71)  # GL_DEPTH_TEST
G.glDepthFunc(0x0203)  # GL_LEQUAL, as the classic material's default
G.glDepthMask(1)


def render(layer, depth, *, original=False, texture_alpha=1., vertex_alpha=1., mod_alpha=1., model=None):
    shader = classic[layer] if original else candidate[layer]
    G.glUseProgram(shader)
    location = lambda name: G.glGetUniformLocation(shader, name.encode())
    transform = np.eye(4, dtype=np.float32) if model is None else model
    projection = np.diag([1., 1., .1, 1.]).astype(np.float32)
    G.glUniformMatrix4fv(location('ModelViewMat'), 1, 1, transform.ctypes.data_as(C.POINTER(F)))
    G.glUniformMatrix4fv(location('ProjMat'), 1, 1, projection.ctypes.data_as(C.POINTER(F)))
    G.glUniform4f(location('ColorModulator'), .6, .8, .9, mod_alpha)
    G.glUniform1f(location('FadeEnd'), fade_ends[layer])
    G.glUniform1i(location('Sampler0'), 0)
    sample = np.array([.4, .7, .3, texture_alpha], dtype=np.float32)
    G.glBindTexture(0x0DE1, sample_texture)
    G.glTexImage2D(0x0DE1, 0, 0x8814, 1, 1, 0, 0x1908, 0x1406, sample.ctypes.data)
    points = np.array([[-.8, -.8, -depth, 1], [.8, -.8, -depth, 1], [.8, .8, -depth, 1],
                       [-.8, -.8, -depth, 1], [.8, .8, -depth, 1], [-.8, .8, -depth, 1]], dtype=np.float32)
    points = points @ np.linalg.inv(transform).T
    vertices = np.concatenate([points[:, :3], np.full((6, 2), .5),
                               np.tile([.5, .9, .7, vertex_alpha], (6, 1))], axis=1).astype(np.float32)
    G.glBufferData(0x8892, vertices.nbytes, vertices.ctypes.data, 0x88E0)
    for name, components, offset in [('Position', 3, 0), ('UV0', 2, 12), ('Color', 4, 20)]:
        attribute = G.glGetAttribLocation(shader, name.encode())
        G.glEnableVertexAttribArray(attribute)
        G.glVertexAttribPointer(attribute, components, 0x1406, 0, 36, P(offset))
    (G.glEnable if layer == 'core' else G.glDisable)(0x0B44)  # Original culling
    G.glClearColor(0, 0, 0, 0)
    G.glClearDepth(1.)
    G.glClear(0x4000 | 0x0100)
    G.glDrawArrays(4, 0, 6)
    color, z = np.zeros(4, dtype=np.float32), np.zeros(1, dtype=np.float32)
    G.glReadPixels(128, 128, 1, 1, 0x1908, 0x1406, color.ctypes.data)
    G.glReadPixels(128, 128, 1, 1, 0x1902, 0x1406, z.ctypes.data)
    check(G.glGetError() == 0, 'OpenGL error during draw/readback')
    return color, float(z[0])


maximum_far_delta = 0.
for layer in ['core', 'glow']:
    for texture_alpha in [0., .049, .05, .05001, .099, .1, .10001, .6, 1.]:
        for vertex_alpha in [.08, .5, 1.]:
            for mod_alpha in [.04, .5, 1.]:
                options = dict(texture_alpha=texture_alpha, vertex_alpha=vertex_alpha, mod_alpha=mod_alpha)
                base_color, base_depth = render(layer, 3., original=True, **options)
                test_color, test_depth = render(layer, 3., **options)
                delta = float(np.max(np.abs(base_color - test_color)))
                maximum_far_delta = max(maximum_far_delta, delta)
                check(delta == 0. and base_depth == test_depth, 'Far cutoff/color/depth differs from actual classic: ' + str((layer, options)))
    for depth in [-2., -.1, 0., .05, .119, .12]:
        color, z = render(layer, depth)
        check(np.max(np.abs(color)) == 0. and z == 1., 'Invisible near/behind fragment wrote color/depth')
    previous = 0.
    for depth in np.linspace(.12, fade_ends[layer], 101):
        color, z = render(layer, float(depth))
        amount = min(1., max(0., (depth - .12) / (fade_ends[layer] - .12)))
        expected = amount * amount * (3. - 2. * amount)
        check(abs(float(color[3]) - expected) < 4e-6, 'Shader visibility differs from smoothstep oracle')
        check(float(color[3]) >= previous, 'Shader fade is not monotonic')
        if depth > .12:
            check(z < 1., 'Positive visibility lost original depth write')
        previous = float(color[3])
    color, z = render(layer, .120001, texture_alpha=.11 if layer == 'core' else .051)
    check(0. < color[3] < .000001 and z < 1., 'Positive fade was cut off a second time')

# Explicit alpha-boundary and modulation-order checks.
check(render('glow', 3., texture_alpha=.05)[1] == 1., 'Glow equality must discard')
check(render('glow', 3., texture_alpha=.06)[1] < 1., 'Glow .05-.1 band must survive')
check(render('glow', 3., texture_alpha=.08, mod_alpha=.5)[1] == 1., 'Glow cutoff must include ColorModulator')
check(render('core', 3., texture_alpha=.1)[1] < 1., 'Core equality must survive')
check(render('core', 3., texture_alpha=.1, mod_alpha=.01)[1] < 1., 'Core cutoff must precede ColorModulator')

# Different caller-space coordinates that map to the same camera-space points
# must have the same fade. Both rotation and translation are included.
angle = .91
model = np.array([[np.cos(angle), 0, np.sin(angle), 2.], [0, 1, 0, -.5],
                  [-np.sin(angle), 0, np.cos(angle), 1.3], [0, 0, 0, 1]], dtype=np.float32)
for layer in ['core', 'glow']:
    for depth in [.05, .2, .4, .7, 1.2, 3.]:
        base_color, base_depth = render(layer, depth)
        transformed_color, transformed_depth = render(layer, depth, model=model)
        check(np.max(np.abs(base_color - transformed_color)) < 2e-6
              and abs(base_depth - transformed_depth) < 2e-6, 'Fade uses inconsistent projection camera space')


def live_and_linked(shader):
    # Same three GL facts used immediately before production material setup.
    if not G.glIsProgram(shader):
        return False
    deleted, linked = I(), I()
    G.glGetProgramiv(shader, 0x8B80, C.byref(deleted))  # GL_DELETE_STATUS
    G.glGetProgramiv(shader, 0x8B82, C.byref(linked))  # GL_LINK_STATUS
    return deleted.value == 0 and linked.value != 0


check(all(live_and_linked(shader) for shader in candidate.values()), 'Fresh pair not live/linked')
unlinked = G.glCreateProgram()
check(not live_and_linked(unlinked), 'Unlinked program admitted')
G.glDeleteProgram(unlinked)
G.glUseProgram(candidate['glow'])
G.glDeleteProgram(candidate['core'])
check(not G.glIsProgram(candidate['core']) and not live_and_linked(candidate['core']),
      'Deleted unbound program admitted')
G.glDeleteProgram(candidate['glow'])
check(G.glIsProgram(candidate['glow']) != 0, 'Driver should retain a bound program pending deletion')
check(not live_and_linked(candidate['glow']), 'Pending-delete bound program admitted')
G.glUseProgram(0)
check(not G.glIsProgram(candidate['glow']), 'Unbinding should finish program deletion')
check(G.glGetError() == 0, 'Liveness guard polluted the GL error state')

print(json.dumps({'result': 'PASS', 'checks': checks, 'linked_programs': 4,
                  'far_color_and_alpha_max_delta': maximum_far_delta,
                  'zero_visibility_depth_unchanged': True, 'tiny_positive_alpha_writes_depth': True,
                  'deleted_and_pending_delete_programs_rejected': True,
                  'scope': 'Actual offscreen GL shader/depth tests; native gameplay acceptance remains separate'}, indent=2))

