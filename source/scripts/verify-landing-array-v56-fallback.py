#!/usr/bin/env python3
"""Independently compile complete frozen .56/current landing renderers with explicit CPU hosts.

No Gradle, Minecraft runtime, native shader/GPU, gameplay or historical oracle admission.
Frozen sources/capture are never auto-refreshed. Added dependencies or hosts fail closed.
"""
import argparse
import gzip
import hashlib
import json
import os
import re
from pathlib import Path
import subprocess
import sys
import tempfile

CONTRACT = 'landing-array-v56-fallback-v1'
COMMIT = 'a1a78a3ee316b87659d5bf7c943650c7af6ba96b'
SUPPORT = 'src/test/resources/landing-array-v56-fallback'
PREFIX = 'src/main/java/cn/academy/port/'
FROZEN = {
    PREFIX + 'client/MultidimensionalLandingArrayEffects.java': '260c4bd10cf734d780ee434c1195a6dd4ae45187346bd9d7c92f9baee0678c09',
    PREFIX + 'client/MultidimensionalLandingArrayMesh.java': '90e27da23d5d34d3fa0d5e9596fa378cda10cb68d4eda4d13415514eaa5c2f4e',
    PREFIX + 'client/SafeLandingProbeMesh.java': 'fd540e46e3a17f6d2047344e088cd29e4dc057bff9b15daefa54cd021ad75af7',
    PREFIX + 'client/ClassicTeleporterTimeline.java': '24ba4e9a8a264a8d999a3145fddf75fbef2aa962ba3a0795d59b7c3d35300a6a',
    PREFIX + 'client/MultidimensionalLandingArrayHudLayout.java': 'c4a9383e1dd4555fa4ac8e601d11f87cfd042a1924e1e6280b84318f3f596632',
    PREFIX + 'client/ClassicHudTimeline.java': 'cdba066f0f58c1977d1f4eb3252945b0bf947746bf2d9b2bf42187e43c6666ae',
    PREFIX + 'client/EmergencyReturnHudLayout.java': '2cd64f56aa0cb2740381e0a27577e2cffe8d29c6fba78a8962e89a91d0f10b31',
    PREFIX + 'highpower/core/SafeLandingProbeGeometry.java': 'e585d53bc35e0a6326fc5ccac90c0c9e3ece490043523085adf5d6d48d7e5b98',
    PREFIX + 'client/ClassicCubicCurve.java': 'b322f014fdf5a3a33d5b00bb812beead5dc1f1c23cc0623d93022ce48ac57ee8',
}
CURRENT = list(FROZEN) + [PREFIX + 'client/MultidimensionalLandingCueGeometry.java',
                         PREFIX + 'client/LandingArrayMaterialPreview.java',
                         PREFIX + 'client/LandingArrayCueMaterial.java',
                         PREFIX + 'client/RailThunderArraySoftMaterial.java', PREFIX + 'HighPowerVisualConfig.java']
# Fixed after the first independently compiled frozen .56 capture, never updated by this runner.
FROZEN_CASES_SHA256 = '67d848455a73b759207c8f7609f863061ae637387c1032bf6d862d87d519c560'
FROZEN_CAPTURE_SHA256 = '3951ea9593d380e45e64c69c4c7bf2ff5092cbc82f76b98d77fb21426ab7d16a'
JDK_INPUTS = ('bin/java', 'lib/modules', 'release')
MUTANTS = {
    'drop-classic-cue': ('for (var line : MultidimensionalLandingArrayMesh.contact(at)) {',
                         'for (var line : java.util.List.<MultidimensionalLandingArrayMesh.Line>of()) {', 'off'),
    'classic-coordinate': ('out.addVertex(matrix,(float)at.x,(float)at.y,(float)at.z).setUv(u,v).setColor(230,235,236,alpha);',
                           'out.addVertex(matrix,(float)(at.x+.125),(float)at.y,(float)at.z).setUv(u,v).setColor(230,235,236,alpha);', 'off'),
    'classic-uv': ('.setUv(u,v).setColor(230,235,236,alpha);', '.setUv(u+.125f,v).setColor(230,235,236,alpha);', 'off'),
    'classic-color': ('.setUv(u,v).setColor(230,235,236,alpha);', '.setUv(u,v).setColor(230,235,237,alpha);', 'off'),
    'failed-submit-drops-fallback': ('if (emitted > 0) optionalVertices += emitted;',
                                    'if (emitted >= 0) optionalVertices += emitted;', 'ready-near'),
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, message):
    if not ok:
        raise ValueError(message)


def run(command, log):
    env = dict(os.environ)
    for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH'):
        env.pop(key, None)
    proc = subprocess.run(list(map(str, command)), capture_output=True, text=True, env=env)
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_text(proc.stdout + proc.stderr)
    require(proc.returncode == 0, 'COMMAND_FAILED ' + str(log) + '\n' + (proc.stdout + proc.stderr)[-6000:])
    return proc.stdout


def capture_hash(path):
    digest = hashlib.sha256()
    with gzip.open(path, 'rb') as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b''):
            digest.update(chunk)
    return digest.hexdigest()


def differences(expected, actual, near=False):
    a, b = expected.read_text().splitlines(), actual.read_text().splitlines()
    if near:
        a = [r for r in a if '/s1/' in r and '/kind3/' in r]
    require(len(a) == len(b), 'CASE_COUNT_MISMATCH')
    result = []
    for left, right in zip(a, b):
        lc, rc = left.split('\t'), right.split('\t')
        require(lc[0] == rc[0], 'CASE_ORDER_MISMATCH')
        if lc != rc:
            result.append(dict(case=lc[0], frozen=lc[1:], current=rc[1:]))
    return result


def prove(args):
    root, report = args.project.resolve(strict=True), args.report.resolve()
    require(not report.exists(), 'REPORT_ALREADY_EXISTS: choose a fresh directory')
    report.mkdir(parents=True)
    support = root / SUPPORT
    receipts = {}
    result = dict(contract=CONTRACT, result='FAIL', frozen_commit=COMMIT, lanes={}, negative_controls=[])

    def read(path):
        path = path.resolve(strict=True)
        data = path.read_bytes()
        digest = sha(data)
        require(path not in receipts or receipts[path] == digest, 'INPUT_CHANGED ' + str(path))
        receipts[path] = digest
        return data

    try:
        read(root / 'scripts/verify-landing-array-v56-fallback.py')
        manifest = json.loads(read(support / 'frozen-v56.json'))
        require(manifest == dict(commit=COMMIT, sources=FROZEN), 'FROZEN_MANIFEST_CHANGED')
        frozen = {}
        for path, expected in FROZEN.items():
            frozen[path] = read(support / 'frozen' / path)
            require(sha(frozen[path]) == expected, 'FROZEN_ORACLE_CHANGED ' + path)
        current = {p: read(root / p) for p in CURRENT}
        fixtures = {p.relative_to(support).as_posix(): read(p) for p in sorted((support / 'hosts').rglob('*.java'))}
        expected_hosts = json.loads(read(support / 'host-inventory.json'))
        require(sorted(fixtures) == expected_hosts and len(fixtures) == 58, 'UNKNOWN_OR_MISSING_HOST_SOURCE')
        fixtures['LandingArrayFallbackHarness.java'] = read(support / 'LandingArrayFallbackHarness.java')
        enabled_fixture = read(support / 'LandingArrayEnabledHarness.java')
        preview_fixture = read(support / 'LandingArrayPreviewHarness.java')
        preview_hosts = {p.relative_to(support).as_posix(): read(p) for p in sorted((support / 'preview-hosts').rglob('*.java'))}
        require(sorted(preview_hosts) == ['preview-hosts/net/minecraft/network/chat/Component.java',
                                        'preview-hosts/net/neoforged/neoforge/client/event/InputEvent.java',
                                        'preview-hosts/org/lwjgl/glfw/GLFW.java'], 'UNKNOWN_PREVIEW_HOST')
        java = args.java_home / 'bin/java'
        result['toolchain'] = {'inputs': {relative: sha(read(args.java_home / relative)) for relative in JDK_INPUTS}}
        version_log = report / 'java-version.log'
        run([java, '-XshowSettings:properties', '-version'], version_log)
        require(re.search(r'java\.specification\.version\s*=\s*21\s*$', version_log.read_text(), re.M), 'JAVA21_REQUIRED')
        compiler_version = run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-version'], report / 'compiler-version.log').strip()
        require(re.match(r'^javac 21(?:[.\s+\-]|$)', compiler_version), 'JAVA21_COMPILER_REQUIRED')
        result['toolchain']['compiler_version'] = compiler_version
        result['toolchain']['source_target'] = 21
        # The installed Java21 compiler module is present, but ct.sym / --release support is absent.
        # Explicit source/target21 is a CPU fixture check, not a Java21 public API compatibility proof.
        compiler = [java, '-Xmx256m', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-source', '21', '-target', '21']
        for ext in ('json', 'vsh', 'fsh'):
            read(root / ('src/main/resources/assets/academy/shaders/core/rail_array_soft.' + ext))
        for name in ['port/white', 'effects/glow_line', *['effects/tp_mark/' + str(i) for i in range(7)]]:
            read(root / 'src/main/resources/assets/academy/textures' / (name + '.png'))
        with tempfile.TemporaryDirectory(prefix='landing-array-v56-proof-') as td:
            temp = Path(td)
            for name, data in fixtures.items():
                path = temp / 'hosts' / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            hosts = sorted((temp / 'hosts').rglob('*.java'))

            def compile_lane(name, sources):
                base, dest = temp / name / 'source', temp / name / 'classes'
                files = []
                for rel, data in sources.items():
                    path = base / rel
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(data)
                    files.append(path)
                if name != 'frozen':
                    enabled_path = base / 'LandingArrayEnabledHarness.java'
                    enabled_path.write_bytes(enabled_fixture)
                    files.append(enabled_path)
                    preview_path = base / 'LandingArrayPreviewHarness.java'
                    preview_path.write_bytes(preview_fixture)
                    files.append(preview_path)
                    for relative, data in preview_hosts.items():
                        path = base / relative
                        path.parent.mkdir(parents=True, exist_ok=True)
                        path.write_bytes(data)
                        files.append(path)
                lane_hosts = hosts if name == 'frozen' else [p for p in hosts if not p.as_posix().endswith('/net/minecraft/network/chat/Component.java')]
                dest.mkdir(parents=True)
                run([*compiler, '-proc:none', '-implicit:none', '-sourcepath', '', '-classpath', dest,
                     '-encoding', 'UTF-8', '-d', dest, *lane_hosts, *files], report / (name + '-compile.log'))
                inventory = {p.relative_to(dest).as_posix(): sha(p.read_bytes()) for p in sorted(dest.rglob('*.class'))}
                (report / (name + '-classes.json')).write_text(json.dumps(inventory, indent=2) + '\n')
                return dest

            def execute(name, classes, lane, mutant=False):
                flags = ['-Dacademy.landingArrayCues.forceUnavailable=true'] if lane == 'forced' else []
                if mutant:
                    flags.append('-Dproof.mutant=true')
                raw = run([java, '-ea', '-Xmx256m', *flags, '-cp', classes,
                           'cn.academy.port.client.LandingArrayFallbackHarness', lane, report / name,
                           root / 'src/main/resources'], report / (name + '-execution.log'))
                summary = json.loads(raw)
                summary['cases_sha256'] = sha((report / name / 'cases.tsv').read_bytes())
                summary['full_capture_sha256'] = capture_hash(report / name / 'capture.txt.gz')
                return summary

            frozen_classes = compile_lane('frozen', frozen)
            baseline = execute('frozen', frozen_classes, 'frozen')
            require(baseline['cases'] == 1051 and baseline['nonempty_cases'] >= 700, 'FROZEN_MATRIX_INCOMPLETE')
            require(baseline['cases_sha256'] == FROZEN_CASES_SHA256 and baseline['full_capture_sha256'] == FROZEN_CAPTURE_SHA256, 'FROZEN_CAPTURE_CHANGED')
            result['frozen'] = baseline
            current_classes = compile_lane('current', current)
            for lane in ('off', 'missing', 'corrupt', 'forced', 'config-unloaded', 'shader-compile', 'shader-link', 'shader-pending', 'shader-stale', 'ready-invalid-partial', 'ready-near'):
                summary = execute(lane, current_classes, lane)
                near = lane == 'ready-near'
                mismatch = differences(report / 'frozen/cases.tsv', report / lane / 'cases.tsv', near)
                require(not mismatch, 'FROZEN_DIFFERENTIAL_MISMATCH ' + lane + ': ' + json.dumps(mismatch[:3]))
                if not near:
                    require(summary['full_capture_sha256'] == baseline['full_capture_sha256'], 'FULL_CAPTURE_MISMATCH ' + lane)
                else:
                    require(summary['cases'] == 16 and summary['nonempty_cases'] >= 10, 'READY_NEAR_SUBSET_INCOMPLETE')
                result['lanes'][lane] = dict(result='EXACT_MATCH_FROZEN_NEAR_SUBSET' if near else 'EXACT_MATCH', **summary)
            enabled_log = run([java, '-ea', '-Xmx256m', '-cp', current_classes,
                               'cn.academy.port.client.LandingArrayEnabledHarness', report / 'enabled',
                               root / 'src/main/resources'], report / 'enabled-execution.log')
            result['enabled_integration'] = json.loads(enabled_log)
            result['enabled_integration']['cases_sha256'] = sha((report / 'enabled/cases.tsv').read_bytes())
            result['enabled_integration']['full_capture_sha256'] = capture_hash(report / 'enabled/capture.txt.gz')
            result['enabled_integration']['budget_scope'] = 'Actual private planner with injected remaining143 and144. Six cues at144 each cannot naturally exhaust2048; the same zero-return complete fallback branch is independently proven in the ready-near lane.'
            result['preview_integration'] = {}
            for preview_lane, flags in {
                    'default-off': [], 'qa-only': ['-Dacademy.visual.qa=true'],
                    'preview-only': ['-Dacademy.landingArrayCues.preview=true'],
                    'enabled': ['-Dacademy.visual.qa=true', '-Dacademy.landingArrayCues.preview=true'],
                    'invalid-phase': ['-Dacademy.visual.qa=true', '-Dacademy.landingArrayCues.preview=true', '-Dacademy.landingArrayCues.preview.phase=NaN'],
                    }.items():
                raw = run([java, '-ea', '-Xmx256m', *flags, '-cp', current_classes,
                           'cn.academy.port.client.LandingArrayPreviewHarness', preview_lane, root / 'src/main/resources'],
                          report / ('preview-' + preview_lane + '-execution.log'))
                result['preview_integration'][preview_lane] = json.loads(raw)
            for name, (before, after, lane) in MUTANTS.items():
                relative = PREFIX + 'client/MultidimensionalLandingArrayEffects.java'
                source = current[relative].decode()
                require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED ' + name)
                mutated = dict(current)
                mutated[relative] = source.replace(before, after, 1).encode()
                classes = compile_lane('mutant-' + name, mutated)
                summary = execute('mutant-' + name, classes, lane, True)
                mismatch = differences(report / 'frozen/cases.tsv', report / ('mutant-' + name) / 'cases.tsv', lane == 'ready-near')
                require(mismatch, 'MUTANT_SURVIVED ' + name)
                require(summary['full_capture_sha256'] != result['lanes'][lane]['full_capture_sha256'], 'MUTANT_CAPTURE_SURVIVED ' + name)
                (report / ('mutant-' + name) / 'differences.json').write_text(json.dumps(mismatch, indent=2) + '\n')
                result['negative_controls'].append(dict(name=name, result='DETECTED', differing_cases=len(mismatch), first_case=mismatch[0]['case']))
        for path, expected in receipts.items():
            require(sha(path.read_bytes()) == expected, 'INPUT_CHANGED_BEFORE_COMPLETION ' + str(path))
        result.update(result='PASS_CPU_FROZEN_DIFFERENTIAL',
                      comparisons=['Raw emitted float coordinate/UV/normal bits, transformed coordinate bits, integer RGBA and vertex order',
                                   'Texture selection, buffer requests/flushes and actual RenderType declarations',
                                   'Model-view/pose stack and shader color restoration; label placement and native preview-item calls',
                                   'Actual receipt validation, active/latest state, shot records, lease ages, cue point/contact/age and controlled input-host notifications'],
                      coverage=['MARKED, PREVIEW, ROUND, PROJECTED, accepted/rejected IMPACT, nonterminal REFUSED, clean and cancelled ENDED',
                                'Ages0..7; normal, near/inside/behind eye, upward view, transformed pose, near-cull and world-border coordinates',
                                'Hidden GUI/screen, pause/death, world/player/connection/epoch/control changes, abort/clear, unloaded/occluded/frustum/stage gates',
                                'Stale sequence/duplicate shot, heartbeat expiry, queued clear/session/world/connection invalidation and six source/contact cues',
                                'Actual default-OFF config, missing/corrupt resource reload, forced unavailable, unloaded config and soft shader construction/link/pending/stale lifecycle',
                                'Enabled material with invalid render fraction; enabled near-eye publication shape refusal and complete frozen classic fallback',
                                'Preview double launch gate/default OFF, invalid phase, fixed points/phase, shared enhanced/classic submissions, labelled narrow HUD, hide/reframe and live-cue exclusion'],
                      limits=['Nine complete frozen/current production renderer/geometry/layout sources, five current optional/config/preview sources, 58 unchanged frozen hosts and three explicit current-preview host additions/replacements; undeclared dependencies fail compilation.',
                              'World queries, transport packet shape, AcademyClient eligibility, Minecraft/JOML/GL/vertex-buffer/font/native-item behavior are explicit CPU hosts, not gameplay or native GPU proof.',
                              'GUI-only recovery/config hosts deliberately throw if invoked; the preview HUD uses an explicit recording subclass, not a native font/layout or production guidance GUI proof.',
                              'Input ownership240 and unused safe-probe range12 are declared constant hosts; input protocol and server implementation are outside this proof.',
                              'Shader files/resources are real; shader compile/link results are controlled. No pixels, driver state, real engine batching or client-runtime acceptance is claimed.',
                              'Ready-invalid-partial injects NaN render fraction; ready-near is a valid near-eye view that refuses the entire optional publication cue.',
                              'Both lanes use the same verified Java21 compiler/runtime, with exact toolchain hashes receipted. This source/target21 fixture compile does not establish --release21 API compatibility; frozen output hashes stay mandatory on every runtime.',
                              'Successful optional source/contact submission is a bounded current-source smoke lane, not a frozen beauty oracle; injected remaining143 refuses without writes and remaining144 admits a complete cue.',
                              'Historical classic tests and guards are untouched; this independent evidence does not bless any old failures.'])
        (report / 'input-receipts.sha256').write_text(''.join(f'{v}  {p}\n' for p, v in sorted(receipts.items())))
        (report / 'evidence.json').write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
        (report / 'PASS_CPU_FROZEN_DIFFERENTIAL').write_text(CONTRACT + '\n')
        return result
    except Exception as failure:
        result['error'] = str(failure)
        (report / 'failure.json').write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64')))
    parser.add_argument('--report', type=Path, required=True)
    args = parser.parse_args()
    try:
        print(json.dumps(prove(args), indent=2, sort_keys=True))
        return 0
    except Exception as failure:
        print(json.dumps(dict(contract=CONTRACT, result='FAIL', error=str(failure)), indent=2))
        return 1


if __name__ == '__main__':
    sys.exit(main())
