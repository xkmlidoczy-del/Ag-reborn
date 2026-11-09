#!/usr/bin/env python3
"""Compile frozen .62/current NEW251 Effects separately and compare real CPU submissions.
No Gradle, source-only fallback oracle, GPU, or gameplay compatibility claim. Fixtures never auto-refresh.
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

CONTRACT = 'multicore-canopy-v62-fallback-v1'
COMMIT = 'c1c02209960c329ab7e304562d2f102718791ec5'
SUPPORT = 'src/test/resources/multicore-canopy-v62-fallback'
PREFIX = 'src/main/java/cn/academy/port/'
FROZEN = {
    "src/main/java/cn/academy/port/client/MulticoreCanopyEffects.java": "8b0df24456e09bf3f78b1a0360c9d4b841013b7881625347718e5d140505041d",
    "src/main/java/cn/academy/port/client/MulticoreCanopyMesh.java": "2b038db4c8c846ec37f39ee4c29914e202cfa3cb2e9465a97ff4c3fee08c1142",
    "src/main/java/cn/academy/port/client/MulticoreCanopyState.java": "e31a67a519c61fd4c5584ecf52979224c8b287c0b7a04980e5e5dd6fc5e070ea",
    "src/main/java/cn/academy/port/client/FoldedMirrorMesh.java": "87c90c1de8f40e76e7d34d73bd01b13487a1a9ec1646f29c3b7f57c203d8c695",
    "src/main/java/cn/academy/port/client/FoldedMirrorState.java": "f3180af8ac1f4712afda2a9146a2f4197e3d1c192f2d4c7ecab40a4fbc54e0cb",
    "src/main/java/cn/academy/port/client/FoldedMirrorMaterial.java": "0f29ff32f7b3095d7d0ce079d33644060b6fa4b474e5a912a5c0b5f16349d30c",
    "src/main/java/cn/academy/port/client/FoldedMirrorRenderTypes.java": "2d4575c08745e498e5d90f9dc321d2fea2d43e7b09269e8cf41245a78a1e3198",
    "src/main/java/cn/academy/port/client/BeamEtchingMesh.java": "5d8a91e786fa5afae95e21e0c478eb3854884c7639ef94ae931b5beaff415755",
    "src/main/java/cn/academy/port/highpower/core/FoldedMirrorGeometry.java": "a5370ebeaff8845eaaecdb79ea3b909c9cd1d0b2ec9f2eeb14377ca8a966590c"
}
ASSETS = {
    "src/main/resources/assets/academy/textures/port/white.png": "7ac2abfce2be8b46dd9826d597d94edeefc1570b4a992622721b90a1850e3ee9",
    "src/main/resources/assets/academy/textures/effects/mdball/glow.png": "2dd6e193adfbf100cf00671aa40ada6c40acd01156c9c44ee231edf50e9208b5",
    "src/main/resources/assets/academy/textures/effects/mdball/0.png": "01130bef3ab1d82cfaf41892cae654f8d77d40ed0c00d3642d4fffd31d09546b",
    "src/main/resources/assets/academy/textures/effects/mdball/1.png": "e98893f6f46ba618a715cf214452c3d351669d3c72e6efa87c02ec370d9c4518",
    "src/main/resources/assets/academy/textures/effects/mdball/2.png": "537f631eed7be706abf0000c4cb75358be117faf9738d88c922d2085de14c8c7",
    "src/main/resources/assets/academy/textures/effects/mdball/3.png": "27e55f9292f346ff24cc25e9bebcf1a7d6e0046e21ac019755c5c6faf017b3db",
    "src/main/resources/assets/academy/textures/effects/mdball/4.png": "d48f594e02b49f7dd5e59faddb538d249b7a9f0fb9ff697e5493226db50cf82a",
    "src/main/resources/assets/academy/textures/effects/mdray_small/blend_in.png": "ac97e7a452ecdc5a41f6b2379c98225e443693337f3dd90e2ba54707f1b38104",
    "src/main/resources/assets/academy/textures/effects/mdray_small/tile.png": "591ab5e780dc406a1d32e4dbf938fc584a56fe8898dcc9c596cf5e07b912e88f",
    "src/main/resources/assets/academy/textures/effects/mdray_small/blend_out.png": "0c71ed58ed0c122517e09a3db39addf931934ed22b58f880c781f0c31d916620"
}
PINNED = {
    "host-inventory.json": "3b5726301fd10ada7c8ce828495c5a43a35ed3df518c8ca0cf217ab0fdce5e0a",
    "MulticoreCanopyFallbackHarness.java": "d40f04638f188c28145fab3d2a8a7719d755f930b7bb079756faf60e52ab9850",
    "expected/cases.tsv": "a5efa2ad4f644178ff74eb03987531d4087e44c7645b633360bfb5b7b2ae94c1",
    "native-api-review.txt": "ad64cb0a8fa1dddd037a16292f270264bf6ca93466593dec623260c853cc04f5",
    "native-api-provenance.json": "e3e689f0419ca0dcba787269de32353f827b61b9a9aa8db054706787bac3c7b7"
}
FROZEN_CAPTURE_SHA256 = '03cfdc35b02f61340cbb63933e27b9436ccf5aabacc2d17bea342a799570af16'
CURRENT_ONLY = [PREFIX + 'client/' + name + '.java' for name in ('MulticoreCanopyCrownMaterial', 'MulticoreCanopyCrownGeometry', 'MulticoreCanopyCrownBudget', 'MulticoreCanopyEmissionGeometry',
                                             'MulticoreCanopyIrisRenderTypes', 'ShaderPackState')] + [PREFIX + 'HighPowerVisualConfig.java']
LANES = ('off', 'forced', 'missing-glow', 'corrupt-glow', 'missing-white', 'corrupt-white', 'config-unloaded', 'reload-pending', 'reload-stale', 'midframe-generation', 'midframe-unavailable')
MUTANTS = {
    'coordinate': ('client/MulticoreCanopyEffects.java', '(float)p.x(), (float)p.y(), (float)p.z()', '(float)p.x() + .125f, (float)p.y(), (float)p.z()'),
    'uv': ('client/MulticoreCanopyEffects.java', '.setUv((float)uv.u(), (float)uv.v())', '.setUv((float)uv.u() + .125f, (float)uv.v())'),
    'color': ('client/MulticoreCanopyEffects.java', 'rgb & 255, alpha)', '(rgb & 255) ^ 1, alpha)'),
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
        require(proc.returncode != 0 and expected_failure in proc.stdout + proc.stderr,
                'NEGATIVE_CONTROL_DID_NOT_FAIL_AS_EXPECTED ' + str(log))
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


def vertex_cases(path):
    """Bounded one-case streaming comparison; native font calls are captured separately."""
    name, vertices, labels = None, [], []
    with gzip.open(path, 'rt') as stream:
        for line in stream:
            if line.startswith('CASE '):
                name, vertices, labels = line.strip()[5:], [], []
            elif line.startswith('v:'):
                vertices.append(line)
            elif line.startswith('font:'):
                labels.append(line)
            elif line == 'END\n':
                yield name, vertices, labels


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
        read(root / 'scripts/verify-multicore-canopy-v62-fallback.py')
        require(json.loads(read(support / 'frozen-v62.json')) == dict(commit=COMMIT, sources=FROZEN), 'FROZEN_MANIFEST_CHANGED')
        provenance = json.loads(read(support / 'provenance.json'))
        require(provenance['commit'] == COMMIT and provenance['source_sha256'] == FROZEN and provenance['asset_sha256'] == ASSETS, 'FROZEN_PROVENANCE_CHANGED')
        frozen = {}
        for path, expected in FROZEN.items():
            frozen[path] = read(support / 'frozen' / path)
            require(sha(frozen[path]) == expected, 'FROZEN_SOURCE_CHANGED ' + path)
        require(json.loads(read(support / 'frozen-assets.json')) == ASSETS, 'FROZEN_ASSET_MANIFEST_CHANGED')
        for path, expected in ASSETS.items():
            require(sha(read(root / path)) == expected, 'ORIGINAL_RESOURCE_CHANGED ' + path)
        for path, expected in PINNED.items():
            require(sha(read(support / path)) == expected, 'PINNED_SUPPORT_CHANGED ' + path)
        fixtures = {p.relative_to(support).as_posix(): read(p) for p in sorted((support / 'hosts').rglob('*.java'))}
        require({p: sha(data) for p, data in fixtures.items()} == json.loads(read(support / 'host-inventory.json')), 'UNKNOWN_MISSING_OR_MODIFIED_HOST')
        fixtures['MulticoreCanopyFallbackHarness.java'] = read(support / 'MulticoreCanopyFallbackHarness.java')
        read(support / 'expected/capture.txt.gz')
        require(capture_hash(support / 'expected/capture.txt.gz') == FROZEN_CAPTURE_SHA256, 'FROZEN_EXPECTED_CAPTURE_CHANGED')
        java = args.java_home / 'bin/java'
        result['toolchain'] = {'inputs': {rel: sha(read(args.java_home / rel)) for rel in ('bin/java', 'lib/modules', 'release')}}
        run([java, '-XshowSettings:properties', '-version'], report / 'java-version.log')
        require(re.search(r'java\.specification\.version\s*=\s*21\s*$', (report / 'java-version.log').read_text(), re.M), 'JAVA21_REQUIRED')
        version = run([java, '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-version'], report / 'compiler-version.log').strip()
        require(re.match(r'^javac 21(?:[.\s+\-]|$)', version), 'JAVA21_COMPILER_REQUIRED')
        result['toolchain'].update(compiler_version=version, source_target=21)
        compiler = [java, '-Xmx256m', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-source', '21', '-target', '21']
        with tempfile.TemporaryDirectory(prefix='multicore-canopy-v62-proof-') as td:
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

            def execute(name, classes, lane):
                raw = run([java, '-ea', '-Xmx256m', '-cp', classes,
                           'cn.academy.port.client.MulticoreCanopyFallbackHarness', lane, report / name,
                           root / 'src/main/resources'], report / (name + '-execution.log'))
                summary = json.loads(raw)
                summary['cases_sha256'] = sha((report / name / 'cases.tsv').read_bytes())
                summary['full_capture_sha256'] = capture_hash(report / name / 'capture.txt.gz')
                require(summary['exception_cases'] == 5, 'EXCEPTION_COVERAGE_INCOMPLETE ' + name)
                summary['exceptions_sha256'] = sha((report / name / 'exceptions.txt').read_bytes())
                return summary

            frozen_classes = compile_lane('frozen', frozen)
            baseline = execute('frozen', frozen_classes, 'frozen')
            require(baseline['cases'] == 312 and baseline['nonempty_cases'] == 233 and baseline['vertices'] == 227784
                    and baseline['labels'] == 573 and baseline['peak_vertices'] == 6144, 'FROZEN_MATRIX_INCOMPLETE')
            require(baseline['cases_sha256'] == PINNED['expected/cases.tsv'] and baseline['full_capture_sha256'] == FROZEN_CAPTURE_SHA256, 'FROZEN_CAPTURE_CHANGED')
            result['frozen'] = baseline
            current = frozen if args.frozen_only else {p: read(root / p) for p in [*FROZEN, *CURRENT_ONLY]}
            if not args.frozen_only:
                for path in FROZEN:
                    if not path.endswith('/MulticoreCanopyEffects.java'):
                        require(current[path] == frozen[path], 'ORIGINAL_CLOSURE_CHANGED ' + path)
                current_classes = compile_lane('current', current)
                for lane in LANES:
                    summary = execute(lane, current_classes, lane)
                    mismatch = differences(report / 'frozen/cases.tsv', report / lane / 'cases.tsv')
                    require(not mismatch, 'FROZEN_DIFFERENTIAL_MISMATCH ' + lane + ': ' + json.dumps(mismatch[:3]))
                    require(summary['full_capture_sha256'] == baseline['full_capture_sha256'], 'FULL_CAPTURE_MISMATCH ' + lane)
                    require(summary['exceptions_sha256'] == baseline['exceptions_sha256'], 'EXCEPTION_CAPTURE_MISMATCH ' + lane)
                    result['lanes'][lane] = dict(result='EXACT_MATCH', **summary)
                enabled = execute('enabled', current_classes, 'enabled')
                mismatch = differences(report / 'frozen/cases.tsv', report / 'enabled/cases.tsv')
                require(mismatch, 'ENABLED_LANE_NEVER_CHANGED_RENDERING')
                for left, right in zip((report / 'frozen/cases.tsv').read_text().splitlines(), (report / 'enabled/cases.tsv').read_text().splitlines()):
                    a, b = left.split('\t'), right.split('\t')
                    require(a[-1] == b[-1], 'ENHANCED_MUTATED_RECEIPT_STATE ' + a[0])
                    if a[1] == '0':
                        require(b[1] == '0', 'ENHANCED_REVIVED_EMPTY_RENDER ' + a[0])
                for (name, old, old_labels), (other, new, new_labels) in zip(vertex_cases(report / 'frozen/capture.txt.gz'), vertex_cases(report / 'enabled/capture.txt.gz')):
                    require(name == other and old_labels == new_labels, 'ENHANCED_CHANGED_CLASSIC_LABEL ' + name)
                    cursor = iter(new)
                    require(all(any(candidate == original for candidate in cursor) for original in old), 'ENHANCED_CHANGED_OR_DROPPED_CLASSIC_VERTEX ' + name)
                result['enabled_integration'] = dict(result='CPU_INTEGRATION_SMOKE', differing_cases=len(mismatch), original_vertex_subsequence_cases=baseline['cases'], **enabled)
            for name, (path, before, after) in MUTANTS.items():
                relative = PREFIX + path
                source = current[relative].decode()
                require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED ' + name)
                mutated = dict(current)
                mutated[relative] = source.replace(before, after, 1).encode()
                classes = compile_lane('mutant-' + name, mutated)
                summary = execute('mutant-' + name, classes, 'frozen' if args.frozen_only else 'off')
                mismatch = differences(report / 'frozen/cases.tsv', report / ('mutant-' + name) / 'cases.tsv')
                require(mismatch and summary['full_capture_sha256'] != baseline['full_capture_sha256'], 'MUTANT_SURVIVED ' + name)
                (report / ('mutant-' + name) / 'differences.json').write_text(json.dumps(mismatch, indent=2) + '\n')
                result['negative_controls'].append(dict(name=name, result='DETECTED', differing_cases=len(mismatch), first_case=mismatch[0]['case']))
            if not args.frozen_only:
                relative = PREFIX + 'client/MulticoreCanopyCrownMaterial.java'
                for name, before, after, lane, witness in (
                        ('resource-hash-guard', 'hashes[i].equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data)))', 'true', 'corrupt-white', 'RESOURCE_READY_WRONG:corrupt-white'),
                        ('stale-reload-guard', 'if (revision == generation) ready = accepted;', 'ready = accepted;', 'reload-stale', 'RESOURCE_READY_WRONG:reload-stale')):
                    source = current[relative].decode()
                    require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED ' + name)
                    mutated = dict(current)
                    mutated[relative] = source.replace(before, after, 1).encode()
                    classes = compile_lane('mutant-' + name, mutated)
                    run([java, '-ea', '-Xmx256m', '-cp', classes, 'cn.academy.port.client.MulticoreCanopyFallbackHarness',
                         lane, report / ('mutant-' + name), root / 'src/main/resources'],
                        report / ('mutant-' + name + '-execution.log'), expected_failure=witness)
                    result['negative_controls'].append(dict(name=name, result='DETECTED', witness=witness))
            relative = PREFIX + 'client/MulticoreCanopyEffects.java'
            source = current[relative].decode()
            before = 'try { buffers.endBatch(); } finally {'
            require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED exception-finally')
            mutated = dict(current)
            mutated[relative] = source.replace(before, 'buffers.endBatch(); {', 1).encode()
            classes = compile_lane('mutant-exception-finally', mutated)
            witness = 'MODEL_VIEW_NOT_RESTORED:flush-finally'
            run([java, '-ea', '-Xmx256m', '-cp', classes, 'cn.academy.port.client.MulticoreCanopyFallbackHarness',
                 'frozen' if args.frozen_only else 'enabled', report / 'mutant-exception-finally', root / 'src/main/resources', 'exceptions-only'],
                report / 'mutant-exception-finally-execution.log', expected_failure=witness)
            result['negative_controls'].append(dict(name='exception-finally', result='DETECTED', witness=witness))
        for path, expected in receipts.items():
            require(sha(path.read_bytes()) == expected, 'INPUT_CHANGED_BEFORE_COMPLETION ' + str(path))
        result.update(result='PASS_CPU_FROZEN_CAPTURE_SELF_TEST' if args.frozen_only else 'PASS_CPU_FROZEN_DIFFERENTIAL',
                      comparisons=['Actual emitted vertex order, raw/transformed coordinate float bits, UV float bits, RGBA, primitive type, shader/texture/render state, uniform writes, native-font call arguments, buffer requests and flushes',
                                   'Accepted frames, fences, markers, leases, visibility, flash ages and input notifications; rendering cannot mutate receipts',
                                   'Model-view, pose-stack and shader-color restoration during normal rendering and five injected submission/flush failures'],
                      limits=['Nine complete frozen production files and fourteen complete current files are independently compiled against the same explicit hash-pinned CPU host inventory.',
                              '312 cases include core/target/ray phases, status/interpolation, ENDED tails and 79 empty renders; no native GPU, rasterization, font-glyph tessellation, shader compilation or gameplay compatibility claim.',
                              'Enabled output must retain the complete original vertex subsequence and identical label calls; correctness of the new native geometry is a separate regression/native acceptance responsibility.',
                              'API declarations were reviewed against pinned cached NeoForge21.1.252/Minecraft1.21.1 and JOML1.10.5. Runtime hosts remain documented substitutes, not those native binaries.',
                              'Java21 source/target are explicit; no --release21 public-API compatibility claim.'])
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
    parser.add_argument('--frozen-only', action='store_true', help='Self-test frozen captures and mutation sensitivity; does not claim current equivalence')
    try:
        print(json.dumps(prove(parser.parse_args()), indent=2, sort_keys=True))
        return 0
    except Exception as failure:
        print(json.dumps(dict(contract=CONTRACT, result='FAIL', error=str(failure)), indent=2))
        return 1


if __name__ == '__main__':
    sys.exit(main())
