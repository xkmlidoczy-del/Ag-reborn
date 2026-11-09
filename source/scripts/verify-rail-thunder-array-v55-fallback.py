#!/usr/bin/env python3
"""Compile frozen .55 and current NEW151 renderers into separate explicit-host CPU probes.

No game client, Gradle compilation, native GL, gameplay or historical oracle admission is claimed.
The frozen source oracle is never refreshed by this script; current sources have no hash blessing.
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile

CONTRACT = 'rail-thunder-array-v55-fallback-v1'
COMMIT = '87dbcc2aa8cad739db1662a52746445ec8726681'
SUPPORT = 'src/test/resources/rail-thunder-array-v55-fallback'
FROZEN_CASES_SHA256 = 'd5680e92ea6a598b0766a29a0a7523c389d989021288732513ce591494aebe46'
FROZEN_CAPTURE_SHA256 = 'b7f9ee6bb313d0726bc44f29682d99aa6fec00af7b07715961155ba482c5e237'
PREFIX = 'src/main/java/cn/academy/port/client/'
FROZEN = {
    PREFIX + 'RailThunderArrayEffects.java': 'b3369dd32e0c7d3f9ff451e9d3bad0cb789d6b1f2fcd5aac2a8d73994ce7eeb6',
    PREFIX + 'RailThunderArrayGeometry.java': 'd1c07cf83bd10f1fcdecc545437382156667b86dae21aa6a5703993a4c6635b3',
    PREFIX + 'RailThunderArrayState.java': 'b4c2fd9522d343027eded3c1bb7909ffcbfc086afc05192b658ef39e58e26d82',
    PREFIX + 'ClassicRenderTypes.java': '559349216e6ec0452aa2c63767a150bb358b8acce6722d8ab6a3c6b3d08e31d5',
}
CURRENT = list(FROZEN) + [PREFIX + 'RailThunderArrayGrandGeometry.java', PREFIX + 'RailThunderArrayGrandMaterial.java', PREFIX + 'RailThunderArrayMaterialPreview.java', PREFIX + 'RailThunderArraySoftMaterial.java',
                         'src/main/java/cn/academy/port/HighPowerVisualConfig.java']
JDK = {'bin/java': '2a207f5e7d075afa01d97f8048389a64432a44c4a5af0f5e77d6e286ec5f401d',
       'bin/javac': '55859b80e7a9c4c4736be19ad3addeb35112ca6d17a30c4e0e116afc0a499bdb'}
MUTANTS = {
    'failed-submit-branch': ('RailThunderArrayEffects.java', '&& grandMarker(marker, matrix, mc, camera, look, up, partial, grandBudget))', '&& (grandMarker(marker, matrix, mc, camera, look, up, partial, grandBudget) || true))'),
    'endpoint': ('RailThunderArrayEffects.java', 'point(to.subtract(camera)), point(look), width)', 'point(to.add(.125, 0, 0).subtract(camera)), point(look), width)'),
    'uv': ('RailThunderArrayEffects.java', '.setUv(u, v)', '.setUv(u + .125f, v)'),
    'color': ('RailThunderArrayEffects.java', 'rgb & 255, alpha)', '(rgb & 255) ^ 1, alpha)'),
    'fallback-branch': ('RailThunderArrayEffects.java', '                originalMarker(marker, matrix, mc, camera, look, up);',
                        '                if (RailThunderArrayGrandMaterial.enabled()) originalMarker(marker, matrix, mc, camera, look, up);'),
    'render-state': ('ClassicRenderTypes.java', '.setWriteMaskState(COLOR_WRITE)', '.setWriteMaskState(NO_CULL)'),
}


ENABLED_MUTANTS = {
    'enabled-hit-authority': ('RailThunderArrayEffects.java', 'private static boolean grandMarker',
                              'boolean hit = segment.status() == RailThunderArrayState.HIT;', 'boolean hit = true;',
                              'hit-only-two-contact-quads/p2'),
    'enabled-shot-clock': ('RailThunderArrayEffects.java', 'private static boolean grandMarker',
                           'double age = flash.age + interpolation;', 'double age = marker.state.age() + interpolation;',
                           'shot-envelope-independent-of-cast-age/p2'),
    'enabled-controller-cap': ('RailThunderArrayGrandGeometry.java', '',
                               'MAX_CONTROLLERS = 4', 'MAX_CONTROLLERS = 5',
                               'five-controllers-equal-four-optional-plus-one-complete-classic'),
    'enabled-budget-guard': ('RailThunderArrayEffects.java', 'private static boolean grandMarker',
                             'quads.size() > budget[0]', 'false',
                             'insufficient-budget-rejects-controller'),
    'enabled-clear-generation': ('RailThunderArrayEffects.java', '',
                                 'clearGeneration != generation ||', 'false ||',
                                 'stale-queued-callback-dropped/clear'),
    'enabled-session-generation': ('RailThunderArrayEffects.java', '',
                                   '!AcademyClient.sameInputSessionEpoch(capturedSession)', 'false',
                                   'stale-queued-callback-dropped/session'),
    'enabled-heartbeat-refresh': ('RailThunderArrayEffects.java', '',
                                  'state = packet; lease.received(packet.age(), packet.phase() == RailThunderArrayState.ENDED);',
                                  'state = packet; lease.received(packet.age(), packet.phase() == RailThunderArrayState.ENDED); for (Flash old : flashes) old.age = 0;',
                                  'empty-heartbeat-preserves-envelope/p2'),
    'enabled-preparation-clock': ('RailThunderArrayEffects.java', 'private static boolean grandMarker',
                                  'marker.lease.age() + interpolation', 'marker.state.age() + interpolation',
                                  'preparation-advances-between-packets'),
    'enabled-soft-scope': ('RailThunderArrayEffects.java', 'private static boolean grandMarker',
                           'buffers.getBuffer(quad.texture() == RailThunderArrayGrandGeometry.GLOW ?', 'buffers.getBuffer(true ?',
                           'soft-material-limited-to-glow/p2/s1'),
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, message):
    if not ok:
        raise ValueError(message)


def run(command, log, expected_failure=None):
    env = dict(os.environ)
    for name in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH'):
        env.pop(name, None)
    proc = subprocess.run(list(map(str, command)), capture_output=True, text=True, env=env)
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_text(proc.stdout + proc.stderr)
    if expected_failure:
        require(proc.returncode != 0 and expected_failure in proc.stdout + proc.stderr, 'ENABLED_MUTANT_SURVIVED_OR_WRONG_FAILURE ' + str(log) + '\n' + (proc.stdout + proc.stderr)[-5000:])
        return proc.stdout + proc.stderr
    require(proc.returncode == 0, 'COMMAND_FAILED ' + str(log) + '\n' + (proc.stdout + proc.stderr)[-5000:])
    return proc.stdout


def differences(expected, actual, subset=False):
    a, b = expected.read_text().splitlines(), actual.read_text().splitlines()
    if subset:
        a = [row for row in a if "/status3/" in row]
    require(len(a) == len(b), 'CASE_COUNT_MISMATCH')
    results = []
    for left, right in zip(a, b):
        lc, rc = left.split('\t'), right.split('\t')
        require(lc[0] == rc[0], 'CASE_ORDER_MISMATCH')
        if lc != rc:
            results.append(dict(case=lc[0], frozen=lc[1:], current=rc[1:]))
    return results


def capture_hash(path):
    digest = hashlib.sha256()
    with gzip.open(path, 'rb') as stream:
        for chunk in iter(lambda: stream.read(1 << 20), b''):
            digest.update(chunk)
    return digest.hexdigest()


def prove(args):
    root, report = args.project.resolve(strict=True), args.report.resolve()
    require(not report.exists(), 'REPORT_ALREADY_EXISTS: choose a fresh report directory')
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
        read(root / 'scripts/verify-rail-thunder-array-v55-fallback.py')
        manifest = json.loads(read(support / 'frozen-v55.json'))
        require(manifest == dict(commit=COMMIT, sources=FROZEN), 'FROZEN_MANIFEST_CHANGED')
        frozen = {}
        for path, expected in FROZEN.items():
            frozen[path] = read(support / 'frozen' / path)
            require(sha(frozen[path]) == expected, 'FROZEN_ORACLE_CHANGED ' + path)
        current = {p: read(root / p) for p in CURRENT}
        fixtures = {p.relative_to(support).as_posix(): read(p) for p in sorted((support / 'hosts').rglob('*.java'))}
        fixtures['RailThunderArrayFallbackHarness.java'] = read(support / 'RailThunderArrayFallbackHarness.java')
        require(len(fixtures) == 50, 'UNKNOWN_OR_MISSING_HOST_SOURCE: expected 49 hosts and harness')
        enabled_fixture = read(support / 'RailThunderArrayEnabledHarness.java')
        for relative, expected in JDK.items():
            require(sha(read(args.java_home / relative)) == expected, 'WRONG_PINNED_JDK ' + relative)
        java, javac = args.java_home / 'bin/java', args.java_home / 'bin/javac'
        # Actual material.reload reads all four original texture bytes and checks its own SHA values.
        for extension in ('json', 'vsh', 'fsh'):
            read(root / ('src/main/resources/assets/academy/shaders/core/rail_array_soft.' + extension))
        for name in ('effects/glow_line', 'effects/railgun/blend_in', 'effects/railgun/tile', 'effects/railgun/blend_out'):
            read(root / 'src/main/resources/assets/academy/textures' / (name + '.png'))
        with tempfile.TemporaryDirectory(prefix='rail-array-v55-proof-') as td:
            temp = Path(td)
            for name, data in fixtures.items():
                path = temp / 'hosts' / name
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
            common = sorted((temp / 'hosts').rglob('*.java'))

            def compile_lane(name, subjects):
                base, dest = temp / name / 'source', temp / name / 'classes'
                files = []
                for relative, data in subjects.items():
                    target = base / relative
                    target.parent.mkdir(parents=True, exist_ok=True)
                    target.write_bytes(data)
                    files.append(target)
                if name != 'frozen':
                    enabled_source = base / 'RailThunderArrayEnabledHarness.java'
                    enabled_source.write_bytes(enabled_fixture)
                    files.append(enabled_source)
                dest.mkdir(parents=True)
                run([javac, '-J-Xmx256m', '-proc:none', '-implicit:none', '-sourcepath', '', '-classpath', dest, '-encoding', 'UTF-8', '-d', dest, *common, *files], report / (name + '-compile.log'))
                inventory = {p.relative_to(dest).as_posix(): sha(p.read_bytes()) for p in sorted(dest.rglob('*.class'))}
                (report / (name + '-classes.json')).write_text(json.dumps(inventory, indent=2) + '\n')
                return dest

            def execute(name, classes, lane, mutant=False):
                output = report / name
                flags = ['-Dacademy.railArrayGrand.forceUnavailable=true'] if lane == 'forced' else []
                if mutant:
                    flags.append('-Dproof.mutant=true')
                raw = run([java, '-ea', '-Xmx256m', *flags, '-cp', classes,
                           'cn.academy.port.client.RailThunderArrayFallbackHarness', lane, output,
                           root / 'src/main/resources'], report / (name + '-execution.log'))
                summary = json.loads(raw)
                summary['cases_sha256'] = sha((output / 'cases.tsv').read_bytes())
                summary['full_capture_sha256'] = capture_hash(output / 'capture.txt.gz')
                return summary

            frozen_classes = compile_lane('frozen', frozen)
            baseline = execute('frozen', frozen_classes, 'frozen')
            require(baseline['cases'] == 1485 and baseline['nonempty_cases'] >= 930, 'FROZEN_MATRIX_INCOMPLETE')
            require(baseline['cases_sha256'] == FROZEN_CASES_SHA256 and baseline['full_capture_sha256'] == FROZEN_CAPTURE_SHA256, 'FROZEN_CAPTURE_CHANGED')
            result['frozen'] = baseline
            current_classes = compile_lane('current', current)
            for lane in ('off', 'missing', 'corrupt', 'forced', 'shader-compile', 'shader-link', 'shader-pending', 'shader-stale'):
                summary = execute(lane, current_classes, lane)
                mismatch = differences(report / 'frozen/cases.tsv', report / lane / 'cases.tsv')
                require(not mismatch, 'FROZEN_DIFFERENTIAL_MISMATCH ' + lane + ': ' + json.dumps(mismatch[:3]))
                require(summary['full_capture_sha256'] == baseline['full_capture_sha256'], 'FULL_CAPTURE_MISMATCH ' + lane)
                result['lanes'][lane] = dict(result='EXACT_MATCH', **summary)
            summary = execute('refused', current_classes, 'refused')
            mismatch = differences(report / 'frozen/cases.tsv', report / 'refused/cases.tsv', True)
            require(not mismatch, 'ENABLED_REFUSED_FALLBACK_MISMATCH: ' + json.dumps(mismatch[:3]))
            require(summary['cases'] == 288, 'REFUSED_SUBSET_INCOMPLETE')
            result['lanes']['refused'] = dict(result='EXACT_MATCH_FROZEN_REFUSED_SUBSET', **summary)
            enabled_log = run([java, '-ea', '-Xmx256m', '-cp', current_classes,
                               'cn.academy.port.client.RailThunderArrayEnabledHarness', report / 'enabled',
                               root / 'src/main/resources'], report / 'enabled-execution.log')
            result['enabled_integration'] = json.loads(enabled_log)
            result['enabled_integration']['cases_sha256'] = sha((report / 'enabled/cases.tsv').read_bytes())
            result['enabled_integration']['full_capture_sha256'] = capture_hash(report / 'enabled/capture.txt.gz')
            result['enabled_integration']['budget_shortage_scope'] = 'Real private planning method with injected remaining budget; ordinary event budget shortage is precluded by four times 256 <= 1024.'
            for mutation, (filename, before, after) in MUTANTS.items():
                relative = PREFIX + filename
                text = current[relative].decode()
                require(text.count(before) == 1, 'MUTATION_ANCHOR_CHANGED ' + mutation)
                mutated = dict(current)
                mutated[relative] = text.replace(before, after, 1).encode()
                classes = compile_lane('mutant-' + mutation, mutated)
                subset = mutation == 'failed-submit-branch'
                summary = execute('mutant-' + mutation, classes, 'refused' if subset else 'off', True)
                mismatch = differences(report / 'frozen/cases.tsv', report / ('mutant-' + mutation) / 'cases.tsv', subset)
                require(mismatch, 'MUTANT_SURVIVED ' + mutation)
                require(summary['full_capture_sha256'] != baseline['full_capture_sha256'], 'MUTANT_CAPTURE_SURVIVED ' + mutation)
                (report / ('mutant-' + mutation) / 'differences.json').write_text(json.dumps(mismatch, indent=2) + '\n')
                result['negative_controls'].append(dict(name=mutation, result='DETECTED', differing_cases=len(mismatch), first_case=mismatch[0]['case']))
            result['enabled_negative_controls'] = []
            for mutation, (filename, scope, before, after, expected) in ENABLED_MUTANTS.items():
                relative = PREFIX + filename
                source = current[relative].decode()
                offset = source.index(scope) if scope else 0
                prefix, selected = source[:offset], source[offset:]
                require(selected.count(before) == 1, 'ENABLED_MUTATION_ANCHOR_CHANGED ' + mutation)
                mutated = dict(current)
                mutated[relative] = (prefix + selected.replace(before, after, 1)).encode()
                classes = compile_lane('mutant-' + mutation, mutated)
                run([java, '-ea', '-Xmx256m', '-cp', classes, 'cn.academy.port.client.RailThunderArrayEnabledHarness',
                     report / ('mutant-' + mutation), root / 'src/main/resources'],
                    report / ('mutant-' + mutation + '-execution.log'), expected)
                result['enabled_negative_controls'].append(dict(name=mutation, result='DETECTED', expected_assertion=expected))

        for path, expected in receipts.items():
            require(sha(path.read_bytes()) == expected, 'INPUT_CHANGED_BEFORE_COMPLETION ' + str(path))
        result.update(result='PASS_CPU_FROZEN_DIFFERENTIAL',
                      comparisons=['Raw emitted float coordinate bits, UV bits and integer RGBA in vertex order',
                                   'Texture selection, buffer requests, explicit flushes and RenderType composite-state declarations',
                                   'Model-view push/set/apply/pop and restoration',
                                   'Actual State receipt/fence counters, marker payloads, flash ages and lease/terminal ages'],
                      coverage=['PREPARING/ARMED PREVIEW; TAP/HOLD MISS/HIT/REFUSED/BLOCKED; flash ages 0..5',
                                'First-person right/left, third-person, remote owner; near eye, center clear, pose transform, behind eye, world border',
                                'Pause/invalid owner, unloaded/occluded/end clipped, missing target, world/player/connection changes, clear/abort/cancel/normal end',
                                'Quiet expiry, stale sequence, empty heartbeat, render stage, remote distance, 64 contexts/8 visible, 16 flashes/segments',
                                'Successful optional HIT/MISS/BLOCKED, age-200 shot, pause/preparation clocks, queued clear/session/end, five controllers and all-or-nothing planning budgets',
                                'Actual soft-material registration with controlled shader construction/link failure, pending callback and stale callback'],
                      limits=['This independently compiles complete frozen/current renderer source and exact named dependency closure with 49 explicit hosts.',
                              'Minecraft world/session/config/event/vertex-buffer/RenderSystem/JOML behavior is modelled; no native GPU, pixels, game client or live gameplay is claimed.',
                              'Actual ClassicRenderTypes declarations execute; host buffer flushing and state callbacks are recorded, not real engine batching or GPU state restoration.',
                              'Actual current material reload verifies real resources. Shader resources are read, while shader compilation/link results are controlled hosts. OFF, missing, corrupt and forced-unavailable all select original rendering. A ready/enabled refused receipt exercises grandMarker returning false before submission.',
                              'Successful optional rendering, queue/session boundaries and combined controller budgets execute in an additional current-source integration lane; no server or protocol behavior is substituted or claimed.',
                              'Historical classic tests/guards are untouched and this independent test does not bless their failures.'])
        (report / 'input-receipts.sha256').write_text(''.join(f'{v}  {p}\n' for p, v in sorted(receipts.items())))
        (report / 'evidence.json').write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
        (report / 'PASS_CPU_FROZEN_DIFFERENTIAL').write_text(CONTRACT + '\n')
        return result
    except Exception as failure:
        result['error'] = str(failure)
        (report / 'failure.json').write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
        raise


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=root)
    parser.add_argument('--java-home', type=Path, default=root.parents[1] / 'aca-tools/jdk-21.0.12.1+1')
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
