#!/usr/bin/env python3
"""Independently compile frozen .61/current NEW421 renderers and compare actual CPU submissions.
No Gradle, source-only fallback oracle, GPU, or gameplay compatibility claim. Frozen fixtures never auto-refresh.
"""
import argparse
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess
import sys
import tempfile

CONTRACT = 'shear-windblade-v61-fallback-v1'
COMMIT = '917d234a19c5def7e4225904802f6638d1ad6661'
SUPPORT = 'src/test/resources/shear-windblade-v61-fallback'
PREFIX = 'src/main/java/cn/academy/port/'
FROZEN = {
    PREFIX + 'client/ShearWindbladeEffects.java': 'b9ff9d6233397bdf899cc4852597c6826a1d2ffc82b935380fcac8434c7fdc7a',
    PREFIX + 'client/ShearWindbladeRenderGeometry.java': '3c2bda906b2dcb04b33338969554d921464084070882892552b134beb9ef8864',
    PREFIX + 'client/ShearWindbladeState.java': '2e3bd0a1bc789e3e5ad723476b43ef8faebc1691d5691c755ed37b259521dab9',
    PREFIX + 'highpower/core/ShearWindbladeGeometry.java': 'f36750cc15088e0b35500ad5b71096410066218944f48ccd982b37eaa2abe20c',
}
ASSETS = {'src/main/resources/assets/academy/textures/effects/glow_line.png': '0785f30721bd1281cb356452c1c68ae721d3b09304df5b23de05578593dbf361'}
CURRENT_ONLY = [PREFIX + 'client/ShearWindbladeVolumePass.java', PREFIX + 'client/ShearWindbladeVolumeMaterial.java',
                PREFIX + 'client/ShearWindbladeVolumeGeometry.java', PREFIX + 'HighPowerVisualConfig.java']
FROZEN_CASES_SHA256 = '105c8300394a035396f5ac0991747ecbf38a2760b78a7cdfa47aca85aeea78d4'
FROZEN_CAPTURE_SHA256 = '0f814650741400a75b5010478c905cac5fe8a4add8ab2ec6d1e3dcb80816aefe'
HOST_INVENTORY_SHA256 = 'c46a322a8e3301acb78000e7a8954dca19820753459cb25c9c6d896a537683c2'
HARNESS_SHA256 = '56184344e6898a9f9b8f733cfe12a70420ca48488b12b6fe347c80c90d8a6309'
JDK_INPUTS = ('bin/java', 'lib/modules', 'release')
LANES = ('off', 'forced', 'missing', 'corrupt', 'config-unloaded', 'reload-pending', 'reload-stale', 'midframe-generation', 'midframe-unavailable')
MUTANTS = {
    'coordinate': ('client/ShearWindbladeEffects.java', '(float)relative.x, (float)relative.y, (float)relative.z', '(float)relative.x + .125f, (float)relative.y, (float)relative.z'),
    'uv': ('client/ShearWindbladeEffects.java', '.setUv(u, v)', '.setUv(u + .125f, v)'),
    'color': ('client/ShearWindbladeEffects.java', '.setColor(gray, gray, gray, alpha)', '.setColor(gray, gray, gray ^ 1, alpha)'),
    'geometry-width': ('client/ShearWindbladeRenderGeometry.java', 'CORE_WIDTH = .070', 'CORE_WIDTH = .071'),
}


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, message):
    if not ok:
        raise ValueError(message)


def run(command, log, expected_failure=None):
    env = dict(os.environ)
    for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH'):
        env.pop(key, None)
    proc = subprocess.run(list(map(str, command)), capture_output=True, text=True, env=env)
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_text(proc.stdout + proc.stderr)
    if expected_failure is not None:
        require(proc.returncode != 0 and expected_failure in proc.stdout + proc.stderr, 'NEGATIVE_CONTROL_DID_NOT_FAIL_AS_EXPECTED ' + str(log))
        return proc.stdout + proc.stderr
    require(proc.returncode == 0, 'COMMAND_FAILED ' + str(log) + '\n' + (proc.stdout + proc.stderr)[-5000:])
    return proc.stdout


def capture_hash(path):
    h = hashlib.sha256()
    with gzip.open(path, 'rb') as stream:
        for block in iter(lambda: stream.read(1 << 20), b''):
            h.update(block)
    return h.hexdigest()


def differences(expected, actual):
    a, b = expected.read_text().splitlines(), actual.read_text().splitlines()
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
    support, receipts = root / SUPPORT, {}
    result = dict(contract=CONTRACT, result='FAIL', frozen_commit=COMMIT, lanes={}, negative_controls=[])

    def read(path):
        path = path.resolve(strict=True)
        data = path.read_bytes()
        digest = sha(data)
        require(path not in receipts or receipts[path] == digest, 'INPUT_CHANGED ' + str(path))
        receipts[path] = digest
        return data

    try:
        read(root / 'scripts/verify-shear-windblade-v61-fallback.py')
        require(json.loads(read(support / 'frozen-v61.json')) == dict(commit=COMMIT, sources=FROZEN), 'FROZEN_MANIFEST_CHANGED')
        provenance = json.loads(read(support / 'provenance.json'))
        require(provenance['commit'] == COMMIT and provenance['source_sha256'] == FROZEN and provenance['asset_sha256'] == ASSETS, 'FROZEN_PROVENANCE_CHANGED')
        frozen = {}
        for path, expected in FROZEN.items():
            frozen[path] = read(support / 'frozen' / path)
            require(sha(frozen[path]) == expected, 'FROZEN_SOURCE_CHANGED ' + path)
        require(json.loads(read(support / 'frozen-assets.json')) == ASSETS, 'FROZEN_ASSET_MANIFEST_CHANGED')
        for path, expected in ASSETS.items():
            require(sha(read(root / path)) == expected, 'ORIGINAL_RESOURCE_CHANGED ' + path)
        inventory_data = read(support / 'host-inventory.json')
        require(sha(inventory_data) == HOST_INVENTORY_SHA256, 'HOST_INVENTORY_CHANGED')
        fixtures = {p.relative_to(support).as_posix(): read(p) for p in sorted((support / 'hosts').rglob('*.java'))}
        require({p: sha(data) for p, data in fixtures.items()} == json.loads(inventory_data), 'UNKNOWN_MISSING_OR_MODIFIED_HOST')
        fixtures['ShearWindbladeFallbackHarness.java'] = read(support / 'ShearWindbladeFallbackHarness.java')
        require(sha(fixtures['ShearWindbladeFallbackHarness.java']) == HARNESS_SHA256, 'HARNESS_CHANGED')
        require(sha(read(support / 'expected/cases.tsv')) == FROZEN_CASES_SHA256, 'FROZEN_EXPECTED_CASES_CHANGED')
        read(support / 'expected/capture.txt.gz')
        require(capture_hash(support / 'expected/capture.txt.gz') == FROZEN_CAPTURE_SHA256, 'FROZEN_EXPECTED_CAPTURE_CHANGED')
        java = args.java_home / 'bin/java'
        result['toolchain'] = {'inputs': {rel: sha(read(args.java_home / rel)) for rel in JDK_INPUTS}}
        run([java, '-XshowSettings:properties', '-version'], report / 'java-version.log')
        require(re.search(r'java\.specification\.version\s*=\s*21\s*$', (report / 'java-version.log').read_text(), re.M), 'JAVA21_REQUIRED')
        version = run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-version'], report / 'compiler-version.log').strip()
        require(re.match(r'^javac 21(?:[.\s+\-]|$)', version), 'JAVA21_COMPILER_REQUIRED')
        result['toolchain'].update(compiler_version=version, source_target=21)
        compiler = [java, '-Xmx256m', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-source', '21', '-target', '21']
        with tempfile.TemporaryDirectory(prefix='shear-windblade-v61-proof-') as td:
            temp = Path(td)
            hosts = []
            for rel, data in fixtures.items():
                path = temp / 'hosts' / rel
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
                hosts.append(path)

            def compile_lane(name, sources):
                base, dest = temp / name / 'source', temp / name / 'classes'
                files = []
                for rel, data in sources.items():
                    path = base / rel
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(data)
                    files.append(path)
                dest.mkdir(parents=True)
                run([*compiler, '-proc:none', '-implicit:none', '-sourcepath', '', '-classpath', dest,
                     '-encoding', 'UTF-8', '-d', dest, *hosts, *files], report / (name + '-compile.log'))
                inventory = {p.relative_to(dest).as_posix(): sha(p.read_bytes()) for p in sorted(dest.rglob('*.class'))}
                (report / (name + '-classes.json')).write_text(json.dumps(inventory, indent=2) + '\n')
                return dest

            def execute(name, classes, lane, mutant=False):
                flags = ['-Dproof.mutant=true'] if mutant else []
                raw = run([java, '-ea', '-Xmx256m', *flags, '-cp', classes,
                           'cn.academy.port.client.ShearWindbladeFallbackHarness', lane, report / name,
                           root / 'src/main/resources'], report / (name + '-execution.log'))
                summary = json.loads(raw)
                summary['cases_sha256'] = sha((report / name / 'cases.tsv').read_bytes())
                summary['full_capture_sha256'] = capture_hash(report / name / 'capture.txt.gz')
                require(summary['exception_cases'] == 5, 'EXCEPTION_COVERAGE_INCOMPLETE ' + name)
                summary['exceptions_sha256'] = sha((report / name / 'exceptions.txt').read_bytes())
                require(summary['boundary_cases'] == 1, 'MIXED_CHUNK_BOUNDARY_COVERAGE_INCOMPLETE ' + name)
                summary['boundary_sha256'] = sha((report / name / 'mixed-chunk-boundary.txt').read_bytes())
                return summary

            frozen_classes = compile_lane('frozen', frozen)
            baseline = execute('frozen', frozen_classes, 'frozen')
            require(baseline['cases'] == 1351 and baseline['nonempty_cases'] == 1008 and baseline['vertices'] == 86208
                    and baseline['peak_vertices'] == 512, 'FROZEN_MATRIX_INCOMPLETE')
            require(baseline['cases_sha256'] == FROZEN_CASES_SHA256 and baseline['full_capture_sha256'] == FROZEN_CAPTURE_SHA256, 'FROZEN_CAPTURE_CHANGED')
            result['frozen'] = baseline
            current = frozen if args.frozen_only else {p: read(root / p) for p in [*FROZEN, *CURRENT_ONLY]}
            if not args.frozen_only:
                for path in ('client/ShearWindbladeState.java', 'highpower/core/ShearWindbladeGeometry.java'):
                    require(current[PREFIX + path] == frozen[PREFIX + path], 'GAMEPLAY_OR_RECEIPT_STATE_CHANGED ' + path)
                current_classes = compile_lane('current', current)
                for lane in LANES:
                    summary = execute(lane, current_classes, lane)
                    mismatch = differences(report / 'frozen/cases.tsv', report / lane / 'cases.tsv')
                    require(not mismatch, 'FROZEN_DIFFERENTIAL_MISMATCH ' + lane + ': ' + json.dumps(mismatch[:3]))
                    require(summary['full_capture_sha256'] == baseline['full_capture_sha256'], 'FULL_CAPTURE_MISMATCH ' + lane)
                    require(summary['exceptions_sha256'] == baseline['exceptions_sha256'], 'EXCEPTION_CAPTURE_MISMATCH ' + lane)
                    require(summary['boundary_sha256'] == baseline['boundary_sha256'], 'MIXED_CHUNK_BOUNDARY_CAPTURE_MISMATCH ' + lane)
                    result['lanes'][lane] = dict(result='EXACT_MATCH', **summary)
                enabled = execute('enabled', current_classes, 'enabled')
                require(enabled['boundary_sha256'] == baseline['boundary_sha256'], 'ENABLED_MIXED_CHUNK_BOUNDARY_CAPTURE_MISMATCH')
                mismatch = differences(report / 'frozen/cases.tsv', report / 'enabled/cases.tsv')
                require(mismatch, 'ENABLED_LANE_NEVER_CHANGED_RENDERING')
                old = (report / 'frozen/cases.tsv').read_text().splitlines()
                new = (report / 'enabled/cases.tsv').read_text().splitlines()
                for left, right in zip(old, new):
                    a, b = left.split('\t'), right.split('\t')
                    require(a[-1] == b[-1], 'ENHANCED_MUTATED_RECEIPT_STATE ' + a[0])
                    if a[1] == '0':
                        require(b[1] == '0', 'ENHANCED_REVIVED_EMPTY_RENDER ' + a[0])
                result['enabled_integration'] = dict(result='CPU_INTEGRATION_SMOKE', differing_cases=len(mismatch), unchanged_state_cases=len(old), **enabled)
            for name, (path, before, after) in MUTANTS.items():
                relative = PREFIX + path
                source = current[relative].decode()
                require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED ' + name)
                mutated = dict(current)
                mutated[relative] = source.replace(before, after, 1).encode()
                classes = compile_lane('mutant-' + name, mutated)
                summary = execute('mutant-' + name, classes, 'frozen' if args.frozen_only else 'off', True)
                mismatch = differences(report / 'frozen/cases.tsv', report / ('mutant-' + name) / 'cases.tsv')
                require(mismatch, 'MUTANT_SURVIVED ' + name)
                require(summary['full_capture_sha256'] != baseline['full_capture_sha256'], 'MUTANT_CAPTURE_SURVIVED ' + name)
                (report / ('mutant-' + name) / 'differences.json').write_text(json.dumps(mismatch, indent=2) + '\n')
                result['negative_controls'].append(dict(name=name, result='DETECTED', differing_cases=len(mismatch), first_case=mismatch[0]['case']))
            if not args.frozen_only:
                relative = PREFIX + 'client/ShearWindbladeVolumeMaterial.java'
                for name, before, after, lane, witness in (
                        ('resource-hash-guard', 'ORIGINAL_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)))', 'true', 'corrupt', 'RESOURCE_READY_WRONG:corrupt'),
                        ('stale-reload-guard', 'if (revision == generation) ready = accepted;', 'ready = accepted;', 'reload-stale', 'RESOURCE_READY_WRONG:reload-stale')):
                    source = current[relative].decode()
                    require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED ' + name)
                    mutated = dict(current)
                    mutated[relative] = source.replace(before, after, 1).encode()
                    classes = compile_lane('mutant-' + name, mutated)
                    run([java, '-ea', '-Xmx256m', '-cp', classes, 'cn.academy.port.client.ShearWindbladeFallbackHarness',
                         lane, report / ('mutant-' + name), root / 'src/main/resources'],
                        report / ('mutant-' + name + '-execution.log'), expected_failure=witness)
                    result['negative_controls'].append(dict(name=name, result='DETECTED', witness=witness))
            if not args.frozen_only:
                relative = PREFIX + 'client/ShearWindbladeVolumePass.java'
                source = current[relative].decode()
                before = 'if (!mc.level.hasChunkAt(new BlockPos(x * 16, (int)Math.floor(minY), z * 16))) return false;'
                require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED mixed-chunk-refusal')
                mutated = dict(current)
                mutated[relative] = source.replace(before, before.replace('return false;', '{}'), 1).encode()
                classes = compile_lane('mutant-mixed-chunk-refusal', mutated)
                witness = 'MIXED_CHUNK_CLASSIC_CONTROLLER_LOST:'
                run([java, '-ea', '-Xmx256m', '-cp', classes, 'cn.academy.port.client.ShearWindbladeFallbackHarness',
                     'enabled', report / 'mutant-mixed-chunk-refusal', root / 'src/main/resources', 'boundary-only'],
                    report / 'mutant-mixed-chunk-refusal-execution.log', expected_failure=witness)
                result['negative_controls'].append(dict(name='mixed-chunk-refusal', result='DETECTED', witness=witness))
            # All successful captures still pass this mutation. Only an exceptional flush
            # reveals that sequential cleanup cannot substitute for the nested finally.
            relative = PREFIX + ('client/ShearWindbladeEffects.java' if args.frozen_only else 'client/ShearWindbladeVolumePass.java')
            source = current[relative].decode()
            before = 'try { buffers.endBatch(); } finally {'
            require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED exception-finally')
            mutated = dict(current)
            mutated[relative] = source.replace(before, 'buffers.endBatch(); {', 1).encode()
            classes = compile_lane('mutant-exception-finally', mutated)
            witness = 'EXCEPTION_MODEL_VIEW_NOT_RESTORED:flush-all'
            run([java, '-ea', '-Xmx256m', '-cp', classes, 'cn.academy.port.client.ShearWindbladeFallbackHarness',
                 'frozen' if args.frozen_only else 'enabled', report / 'mutant-exception-finally',
                 root / 'src/main/resources', 'exceptions-only'], report / 'mutant-exception-finally-execution.log', expected_failure=witness)
            result['negative_controls'].append(dict(name='exception-finally', result='DETECTED', witness=witness))
        for path, expected in receipts.items():
            require(sha(path.read_bytes()) == expected, 'INPUT_CHANGED_BEFORE_COMPLETION ' + str(path))
        result.update(result='PASS_CPU_FROZEN_CAPTURE_SELF_TEST' if args.frozen_only else 'PASS_CPU_FROZEN_DIFFERENTIAL',
                      comparisons=['Actual emitted vertex order, raw/transformed coordinate float bits, UV float bits, RGBA, primitive type, shader/texture/render state, buffer requests and flushes',
                                   'Complete accepted controller/receipt/fence state and input notifications; render cannot mutate receipts',
                                   'Model-view, pose-stack and shader-color restoration during normal rendering and five controlled submission/flush failures'],
                      coverage=['First/partial vertex, typed flush, final flush and combined vertex/final-flush exceptions propagate while restoring model-view, pose, color and receipt state; next draw reproduces successful trace',
                                'Two accepted controllers whose classic bounds remain loaded, with one enhanced shoulder entering missing chunk (1,0): enabled must fall back for both and exactly match frozen classic output',
                                'Eight ordinary/near-eye/inside/world-border/distance/transformed/side/upward scenes, three flight distances, travelling/blocked/expired status, horizontal/vertical/dual fronts and six receipt ages',
                                'Interpolation boundaries; no controller/no front/ended/stale/behind-near-plane empties; pause and owner invalidation',
                                'Duplicate, consecutive, blocked and revival-rejected receipts; queued clear/session/world/connection changes',
                                'Four real accepted controllers emit 512 classic vertices; fifth owner retires an old remote while preserving the cap',
                                'Real optional material initialization with OFF, forced failure, missing/corrupt resource, unloaded config, pending/stale reload and mid-frame generation/unavailability; enabled CPU integration' if not args.frozen_only else 'Frozen-only self-test: current fallback and optional integration lanes were not executed'],
                      limits=[('Four complete frozen production source files and eight complete current files are independently compiled.' if not args.frozen_only else 'Four complete frozen production source files are compiled independently from current sources.') + ' Explicit CPU hosts implement Minecraft, receipt transport/control, JOML, render-state and buffer boundaries; unknown dependencies fail compilation.',
                              'Original glow_line bytes are hash-pinned. Builtin shader selection is traced; no shader/GPU/native-pixel/driver or full gameplay compatibility claim.',
                              'Both lanes use one receipted Java21 runtime/compiler with explicit source/target21; no --release21 API compatibility claim.'])
        (report / 'input-receipts.sha256').write_text(''.join(f'{value}  {path}\n' for path, value in sorted(receipts.items())))
        (report / 'evidence.json').write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
        (report / result['result']).write_text(CONTRACT + '\n')
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
    parser.add_argument('--frozen-only', action='store_true', help='Self-test frozen captures and mutation sensitivity; does not claim current fallback equivalence')
    try:
        print(json.dumps(prove(parser.parse_args()), indent=2, sort_keys=True))
        return 0
    except Exception as failure:
        print(json.dumps(dict(contract=CONTRACT, result='FAIL', error=str(failure)), indent=2))
        return 1


if __name__ == '__main__':
    sys.exit(main())
