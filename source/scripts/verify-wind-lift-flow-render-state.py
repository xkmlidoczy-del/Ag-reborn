#!/usr/bin/env python3
"""Audit .73 source seams, assets, and CPU-only shader/readiness/budget models.

This does not launch Minecraft, execute OpenGL, or establish rendered-image,
driver-failure, gameplay, or complete fallback equivalence acceptance.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct


PREFIX = 'src/main/java/cn/academy/port/client/'
BASELINE_EFFECTS_SHA256 = '69b87574f8fc61d06abf86aff23245ddf4057c8bcde61b4229da07fe9da83ddd'
HOOK = b'        if (WindPressureLiftFlowRenderer.render(visible, event, partial)) return;\n'
TEXTURES = {
    'tornado_ring.png': '9f5cc41dd8e4464e441d0a6e0636f0042b8d3bee82a0ab7b2ca094b5414a6429',
    'glow_line.png': '0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361',
}


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def compact(source):
    return re.sub(r'\s+', '', re.sub(r'//[^\n]*|/\*.*?\*/', '', source, flags=re.S))


def method_body(source, signature):
    text = compact(source)
    start = text.index('{', text.index(signature))
    depth = 0
    for index in range(start, len(text)):
        depth += (text[index] == '{') - (text[index] == '}')
        if depth == 0:
            return text[start + 1:index]
    raise RuntimeError('UNBALANCED_METHOD ' + signature)


def guarded_flush(source):
    return method_body(source, 'privatestaticvoidflush(') == (
        'try{buffers.endBatch(type);}finally{type.clearRenderState();}'
    )


def discard_failed_frame(source):
    text = compact(source)
    return ('buffers.endBatch();' not in text and
            'if(!completed){buffers=null;if(storage!=null)storage.close();storage=null;}' in text)


def safe_failure_policy(renderer, material):
    text = compact(renderer)
    return ('submitted=true;flush(type);' in text and
            'catch(RuntimeExceptionunavailable){WindPressureLiftFlowMaterial.failed();returnsubmitted;}' in text and
            method_body(material, 'staticvoidfailed(') == 'ready=false;')


def safe_shader_policy(material, renderer):
    source = compact(material)
    enabled = method_body(material, 'publicstaticbooleanenabled(')
    registration = method_body(material, 'publicstaticvoidshaders(')
    return ('softShader!=null' in enabled and
            'intrevision=++shaderGeneration;softShader=null;' in registration and
            'if(GL20.glGetProgrami(next.getId(),GL20.GL_LINK_STATUS)==0){next.close();return;}' in registration and
            'event.registerShader(next,loaded->{if(revision==shaderGeneration)softShader=loaded;});' in registration and
            'catch(Exceptionunavailable){softShader=null;}' in registration and
            'ResourceLocation.fromNamespaceAndPath("academy","wind_lift_flow"),DefaultVertexFormat.POSITION_TEX_COLOR)' in source and
            'newShaderStateShard(WindPressureLiftFlowMaterial::shader)' in compact(renderer))


def shader_readiness_model():
    """Independent model of nullable shader admission and generation publication."""
    class State:
        def __init__(self):
            self.resource_generation = self.shader_generation = self.closed_unlinked = 0
            self.ready = False
            self.shader = None

        def begin_resources(self):
            self.resource_generation += 1
            self.ready = False
            return self.resource_generation

        def resources(self, revision, accepted):
            if revision == self.resource_generation:
                self.ready = accepted

        def begin_shader(self, result):
            self.shader_generation += 1
            self.shader = None
            if result == 'unlinked':
                self.closed_unlinked += 1
            return self.shader_generation if result == 'linked' else None

        def publish_shader(self, revision):
            if revision is not None and revision == self.shader_generation:
                self.shader = revision

        def enabled(self, configured=True, forced=False):
            return configured and not forced and self.ready and self.shader is not None

    cases = 0
    for configured in (False, True):
        for forced in (False, True):
            for resource in ('missing', 'corrupt', 'oversized', 'pending', 'valid'):
                for shader in ('missing', 'unlinked', 'pending', 'linked'):
                    state = State()
                    revision = state.begin_resources()
                    if resource != 'pending':
                        state.resources(revision, resource == 'valid')
                    pending = state.begin_shader(shader)
                    if shader == 'linked':
                        state.publish_shader(pending)
                    expected = configured and not forced and resource == 'valid' and shader == 'linked'
                    require(state.enabled(configured, forced) == expected, 'SHADER_ADMISSION_MODEL_FAILED')
                    require(state.closed_unlinked == (shader == 'unlinked'), 'UNLINKED_CANDIDATE_NOT_CLOSED')
                    cases += 1
    stale_cases = 0
    for first in ('resources', 'shader'):
        state = State()
        old_resources = state.begin_resources()
        state.resources(old_resources, True)
        old_shader = state.begin_shader('linked')
        state.publish_shader(old_shader)
        require(state.enabled(), 'INITIAL_READY_MODEL_FAILED')
        new_resources = state.begin_resources()
        new_shader = state.begin_shader('linked')
        state.resources(old_resources, True)
        state.publish_shader(old_shader)
        require(not state.enabled() and state.shader is None and not state.ready, 'STALE_CALLBACK_REACTIVATED_MODEL')
        if first == 'resources':
            state.resources(new_resources, True)
        else:
            state.publish_shader(new_shader)
        require(not state.enabled(), 'HALF_READY_GENERATION_ADMITTED')
        state.resources(new_resources, True)
        state.publish_shader(new_shader)
        require(state.enabled(), 'CURRENT_READY_GENERATION_NOT_ADMITTED')
        # A new missing/unlinked shader must not reuse the last good pointer,
        # and an old callback must not reactivate it after failure.
        for failure in ('missing', 'unlinked'):
            state.begin_shader(failure)
            state.publish_shader(new_shader)
            require(not state.enabled() and state.shader is None, 'FAILED_GENERATION_REUSED_OLD_SHADER')
            stale_cases += 1
    return {'admission_cases': cases, 'stale_generation_failure_cases': stale_cases,
            'callback_orders': ['resources-first', 'shader-first']}


def low_alpha_model(fragment, vertex, descriptor):
    """Check shader arithmetic on CPU float32 inputs; never claim GPU output."""
    expected_vertex = '''#version 150
        in vec3 Position; in vec2 UV0; in vec4 Color;
        uniform mat4 ModelViewMat; uniform mat4 ProjMat;
        out vec2 texCoord0; out vec4 vertexColor;
        void main() { gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
                      texCoord0 = UV0; vertexColor = Color; }'''
    require(compact(vertex) == compact(expected_vertex), 'SHADER_VERTEX_NATIVE_POSITION_UV_COLOR_CHANGED')
    identity = [1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]
    expected_descriptor = {
        'vertex': 'academy:wind_lift_flow', 'fragment': 'academy:wind_lift_flow',
        'samplers': [{'name': 'Sampler0'}],
        'uniforms': [
            {'name': name, 'type': 'matrix4x4', 'count': 16, 'values': identity}
            for name in ('ModelViewMat', 'ProjMat')
        ] + [{'name': 'ColorModulator', 'type': 'float', 'count': 4, 'values': [1, 1, 1, 1]}],
    }
    require(json.loads(descriptor) == expected_descriptor, 'SHADER_DESCRIPTOR_CHANGED')
    require(method_body(fragment, 'voidmain(') ==
            'fragColor=texture(Sampler0,texCoord0)*vertexColor*ColorModulator;if(fragColor.a<0.001)discard;',
            'SOFT_ALPHA_SHADER_ARITHMETIC_OR_CUTOFF_CHANGED')

    def f32(value):
        return struct.unpack('f', struct.pack('f', value))[0]

    cutoff, native_cutoff = f32(.001), f32(.1)
    preserved, bounded = 0, 0
    for texture_max, vertex_limit in ((166, 125), (255, 210)):
        for texture_alpha in range(texture_max + 1):
            for source_alpha in range(256):
                vertex_alpha = min(vertex_limit, source_alpha)
                value = f32(f32(texture_alpha / 255) * f32(vertex_alpha / 255))
                output = None if value < cutoff else value
                require(output is None or 0 <= output <= f32(f32(texture_max / 255) * f32(vertex_limit / 255)),
                        'ALPHA_MODEL_EXCEEDS_TEXTURE_VERTEX_PRODUCT')
                if cutoff <= value < native_cutoff:
                    require(output == value, 'LOW_ALPHA_SHOULDER_ERASED')
                    preserved += 1
                if texture_alpha == 0 or vertex_alpha == 0:
                    require(output is None, 'EXACT_ZERO_TIP_MUST_DISCARD')
                bounded += 1
    # Real authored tornado V-edge alpha=4 and body alpha=125: the former
    # native cutoff removes this product, whereas the new shader preserves it.
    witness = f32(f32(4 / 255) * f32(125 / 255))
    require(cutoff < witness < native_cutoff, 'LOW_ALPHA_NEGATIVE_CONTROL_NOT_DETECTED')
    require(f32(.0005) < cutoff and not cutoff < cutoff, 'SOFT_CUTOFF_BOUNDARY_CHANGED')
    return {'cpu_float32_products': bounded, 'products_preserved_below_native_cutoff': preserved,
            'authored_edge_alpha_witness': witness, 'soft_cutoff': .001, 'native_cutoff': .1,
            'body_vertex_alpha_cap': 125, 'spine_vertex_alpha_cap': 210}


def constant(source, name):
    match = re.search(r'\b' + re.escape(name) + r'\s*=\s*(\d+)\b', source)
    require(match is not None, 'MISSING_INTEGER_CONSTANT ' + name)
    return int(match.group(1))


def audit(root):
    hashes = {}

    def read(relative):
        data = (root / relative).read_bytes()
        hashes[relative] = hashlib.sha256(data).hexdigest()
        return data

    renderer = read(PREFIX + 'WindPressureLiftFlowRenderer.java').decode()
    material = read(PREFIX + 'WindPressureLiftFlowMaterial.java').decode()
    mesh = read(PREFIX + 'WindPressureLiftFlowMesh.java').decode()
    original_mesh = read(PREFIX + 'WindPressureLiftMesh.java').decode()
    effects = read(PREFIX + 'WindPressureLiftEffects.java')
    require(guarded_flush(renderer), 'FAILED_FLUSH_MUST_CLEAR_RENDER_TYPE_IN_FINALLY')
    require(discard_failed_frame(renderer), 'FAILED_FRAME_MUST_DISCARD_WITHOUT_REPLAY')
    require(safe_failure_policy(renderer, material), 'FAILED_SUBMISSION_MUST_SUPPRESS_SAME_FRAME_FALLBACK_AND_RETIRE')
    require(safe_shader_policy(material, renderer), 'SHADER_READINESS_LINK_OR_GENERATION_SEAM_CHANGED')

    mutations = [
        ('missing-flush-finally-clear',
         not guarded_flush(renderer.replace('finally {type.clearRenderState();}', 'finally {}'))),
        ('replayed-failed-buffer',
         not discard_failed_frame(renderer.replace('buffers=null;if(storage!=null)',
                                                   'buffers.endBatch();buffers=null;if(storage!=null)'))),
        ('submission-flag-after-flush',
         not safe_failure_policy(renderer.replace('submitted=true;flush(type);',
                                                   'flush(type);submitted=true;'), material)),
        ('classic-after-partial-draw',
         not safe_failure_policy(renderer.replace('return submitted;', 'return false;'), material)),
        ('failed-generation-remains-ready',
         not safe_failure_policy(renderer, material.replace('static void failed() { ready=false; }',
                                                            'static void failed() {}'))),
        ('missing-custom-shader-admitted',
         not safe_shader_policy(material.replace('&& softShader != null', ''), renderer)),
        ('unlinked-custom-shader-admitted',
         not safe_shader_policy(material.replace('if(GL20.glGetProgrami(next.getId(),GL20.GL_LINK_STATUS)==0)',
                                                  'if(false)'), renderer)),
        ('stale-shader-generation-published',
         not safe_shader_policy(material.replace('if(revision==shaderGeneration)softShader=loaded;',
                                                  'softShader=loaded;'), renderer)),
        ('previous-shader-retained-during-reload',
         not safe_shader_policy(material.replace('int revision=++shaderGeneration;softShader=null;',
                                                  'int revision=++shaderGeneration;'), renderer)),
        ('native-cutoff-shader-rebound',
         not safe_shader_policy(material, renderer.replace('WindPressureLiftFlowMaterial::shader',
                                                            'GameRenderer::getPositionTexColorShader'))),
    ]
    for name, rejected in mutations:
        require(rejected, 'NEGATIVE_CONTROL_NOT_DETECTED ' + name)

    require(effects.count(HOOK) == 1, 'OPTIONAL_HOOK_COUNT_CHANGED')
    require(hashlib.sha256(effects.replace(HOOK, b'')).hexdigest() == BASELINE_EFFECTS_SHA256,
            'V72_EFFECTS_CHANGED_BEYOND_EARLY_OPTIONAL_HOOK')
    for name, expected in TEXTURES.items():
        data = read('src/main/resources/assets/academy/textures/effects/' + name)
        require(hashlib.sha256(data).hexdigest() == expected, 'ORIGINAL_TEXTURE_CHANGED ' + name)

    m, r = compact(material), compact(renderer)
    for seam in ('ready=false;intrevision=++generation;', 'if(revision==generation)ready=accepted;',
                 'returnready&&', 'forceFallback', 'HighPowerVisualConfig.WIND_PRESSURE_LIFT_FLOW.get()'):
        require(seam in m, 'RESOURCE_OR_ENABLE_SEAM_CHANGED ' + seam)
    for seam in ('.setTransparencyState(TRANSLUCENT_TRANSPARENCY)', '.setDepthTestState(LEQUAL_DEPTH_TEST)',
                 '.setWriteMaskState(COLOR_WRITE)', '.setOutputState(PARTICLES_TARGET)',
                 'RenderSystem.setShaderColor(1,1,1,1);',
                 'intgray=spine?250:225;', 'intlimit=spine?210:125;',
                 'intaa=(int)Math.round(Math.min(limit,s.alphaFrom())*WindPressureLiftMesh.nearFade(s.a()));',
                 'intab=(int)Math.round(Math.min(limit,s.alphaTo())*WindPressureLiftMesh.nearFade(s.b()));',
                 'finally{poses.popPose();mv.popMatrix();RenderSystem.applyModelViewMatrix();'
                 'RenderSystem.setShaderColor(color[0],color[1],color[2],color[3]);}'):
        require(seam in r, 'ALPHA_OR_RENDER_STATE_SEAM_CHANGED ' + seam)

    require('WindPressureLiftFlowRibbon.build(shape,eye,fallback)' in r and
            'joined.size()!=shape.segments().size()' in r, 'SHARED_SHOULDERS_NOT_ADMITTED_AS_COMPLETE_CONTROLLER')
    require('Pointorigin=newPoint(0,0,0);' in r and
            'd.segment().a().distanceSquared(origin)+d.segment().b().distanceSquared(origin)' in r,
            'RELATIVE_DRAWS_MUST_SORT_FROM_ORIGIN')
    emit = method_body(renderer, 'privatestaticvoidemit(')
    require('WindPressureLiftFlowRibbon.clip(draw,forward,spine?WindPressureLiftFlowMesh.CORE_WIDTH_SCALE:1)' in emit,
            'EMIT_MUST_USE_SHARED_CORNER_CLIP')
    require('relative(' not in emit and 'subtract(' not in emit, 'EMIT_MUST_NOT_SUBTRACT_CAMERA_AGAIN')
    for corner, u, v, alpha in (('aMinus', 'uFrom', 1, 'aa'), ('aPlus', 'uFrom', 0, 'aa'),
                                ('bPlus', 'uTo', 0, 'ab'), ('bMinus', 'uTo', 1, 'ab')):
        require(f'vertex(out,matrix,vec(quad.{corner}()),(float)s.{u}(),{v},gray,{alpha});' in emit,
                'CLIPPED_CORNER_UV_OR_ALPHA_MAPPING_CHANGED ' + corner)

    contexts = constant(original_mesh, 'MAX_CONTEXTS')
    cap = constant(original_mesh, 'MAX_VERTICES')
    context_cap = constant(original_mesh, 'MAX_CONTEXT_VERTICES')
    particles = constant(mesh, 'MAX_PARTICLES')
    count = constant(mesh, 'RIBBONS') * constant(mesh, 'RIBBON_SEGMENTS') + constant(mesh, 'GROUND_SEGMENTS')
    require((contexts, cap, context_cap, particles) == (4, 2048, 512, 0), 'HARD_BUDGET_CHANGED')
    require('returnsegments.size()*8;' in compact(mesh) and
            'for(booleanspine:newboolean[]{false,true})' in r and
            'budget.take(shape.vertexCount())' in r, 'TWO_PASS_BUDGET_ACCOUNTING_CHANGED')
    require(count == 44, 'STATE_MODEL_SEGMENT_COUNT_CHANGED')

    # Independent state model: a flush may throw before, during, or after its
    # submission. Once attempted, no classic geometry is added in that frame.
    # failed() removes readiness, so the following frame uses only the baseline.
    scenarios, maximum = 0, 0
    for controllers in range(1, contexts + 1):
        one_pass, baseline = count * 4 * controllers, count * 8 * controllers
        require(count * 8 <= context_cap and baseline <= cap, 'BASELINE_OR_CONTEXT_OVER_BUDGET')
        maximum = max(maximum, baseline)
        for failed_pass in range(2):
            for partial_vertices in range(0, one_pass + 1, 4):
                frame_vertices = one_pass * failed_pass + partial_vertices
                require(frame_vertices <= cap and baseline <= cap, 'PARTIAL_DRAW_OR_NEXT_FRAME_OVER_BUDGET')
                scenarios += 1
                maximum = max(maximum, frame_vertices)
    old_policy = count * 4 * contexts + count * 8 * contexts
    require(old_policy == 2112 and old_policy > cap, 'MODEL_NEGATIVE_CONTROL_NOT_DETECTED')
    shader_path = 'src/main/resources/assets/academy/shaders/core/wind_lift_flow'
    alpha_model = low_alpha_model(read(shader_path + '.fsh').decode(), read(shader_path + '.vsh').decode(),
                                  read(shader_path + '.json').decode())
    readiness_model = shader_readiness_model()
    return {
        'status': 'PASS',
        'scope': 'Static source/shader seams and asset hashes; CPU-only readiness, alpha, and partial-submission models',
        'not_executed': ['Minecraft client', 'OpenGL or GPU failure injection', 'rasterization or visual acceptance',
                         'gameplay verification', 'complete runtime fallback equivalence'],
        'negative_controls': [name for name, _ in mutations] + ['old-same-frame-fallback-budget',
                                                              'native-cutoff-erases-authored-edge'],
        'shader_readiness_model': readiness_model,
        'shader_alpha_model': alpha_model,
        'model': {'partial_draw_failure_states': scenarios, 'pre_submission_fallback_states': contexts,
                  'maximum_drawn_vertices_per_frame': maximum, 'global_vertex_cap': cap,
                  'old_failure_policy_witness_vertices': old_policy, 'particles': particles},
        'baseline_effects_sha256_without_optional_hook': BASELINE_EFFECTS_SHA256,
        'source_and_asset_sha256': hashes,
    }


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parents[1],
                        help='Repository root; defaults to the parent of this scripts directory')
    args = parser.parse_args()
    print(json.dumps(audit(args.project.resolve()), indent=2, sort_keys=True))


if __name__ == '__main__':
    main()
