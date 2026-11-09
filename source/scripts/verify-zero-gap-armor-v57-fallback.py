#!/usr/bin/env python3
"""Compile independent frozen .57/current NEW252 renderers and compare CPU submission traces.
No Gradle/GPU/gameplay oracle. Frozen source/capture hashes never auto-refresh.
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

CONTRACT = 'zero-gap-armor-v57-fallback-v1'
COMMIT = 'f07214e561359f83ccdc8b66411b0884e6d10cbf'
SUPPORT = 'src/test/resources/zero-gap-armor-v57-fallback'
PREFIX = 'src/main/java/cn/academy/port/'
FROZEN = {'src/main/java/cn/academy/port/client/ZeroGapArmorEffects.java': '524af6ee0e559a6fa2d63c293fbfdd5c133e0f20f109aa8b96f0b40248f9699d', 'src/main/java/cn/academy/port/client/ZeroGapArmorMesh.java': '05f4058acf2ff358c63af03098249c1ce8873f9aabbeedc71b3ea6a98acca9bd', 'src/main/java/cn/academy/port/client/ZeroGapArmorState.java': '10392c1915449e643090612be4bba160a79f05a0ab327dd5378c2bc194690e1e', 'src/main/java/cn/academy/port/client/FoldedMirrorMesh.java': '87c90c1de8f40e76e7d34d73bd01b13487a1a9ec1646f29c3b7f57c203d8c695', 'src/main/java/cn/academy/port/client/FoldedMirrorRenderTypes.java': '2d4575c08745e498e5d90f9dc321d2fea2d43e7b09269e8cf41245a78a1e3198', 'src/main/java/cn/academy/port/client/FoldedMirrorMaterial.java': '0f29ff32f7b3095d7d0ce079d33644060b6fa4b474e5a912a5c0b5f16349d30c', 'src/main/java/cn/academy/port/highpower/core/ZeroGapArmorGeometry.java': 'e7c2e3e742e355979107b90c06fb0f0b730b999a7182e805dbdce031b2cf9bc8', 'src/main/java/cn/academy/port/highpower/core/ZeroGapArmorTiming.java': '1c73e13b0338b901423acf0a68c9db289b33ccc13cc999a814bb55720caf3419', 'src/main/java/cn/academy/port/highpower/core/FoldedMirrorGeometry.java': 'a5370ebeaff8845eaaecdb79ea3b909c9cd1d0b2ec9f2eeb14377ca8a966590c', 'src/main/java/cn/academy/port/client/FoldedMirrorState.java': 'f3180af8ac1f4712afda2a9146a2f4197e3d1c192f2d4c7ecab40a4fbc54e0cb'}
ASSETS = {'src/main/resources/assets/academy/textures/port/white.png': '7ac2abfce2be8b46dd9826d597d94edeefc1570b4a992622721b90a1850e3ee9', 'src/main/resources/assets/academy/textures/effects/mdshield.png': '5a0cf5f61bea467687820983efb76cfd14f68abbc38667e091b6b0f27965c5dc', 'src/main/resources/assets/academy/shaders/core/folded_mirror_energy.json': '06eeaa51e4f8fdb2471dba7eff89ff76549942c4126df6f376d786bf00f1793a', 'src/main/resources/assets/academy/shaders/core/folded_mirror_energy.vsh': '7bf24710f868582a38a23a0f0c9935c47b4271d04f5a7bd1774a08c8bf65f94c', 'src/main/resources/assets/academy/shaders/core/folded_mirror_energy.fsh': '75b9fe2510f00815c2febf1aa0cb225113d6aa72f2e2f078195227e17aa26b40'}
CURRENT_ONLY = [PREFIX + 'client/ZeroGapArmorEnhancedMesh.java', PREFIX + 'client/ZeroGapArmorMaterial.java', PREFIX + 'HighPowerVisualConfig.java']
FROZEN_CASES_SHA256 = '502c2434b585f818c3a9423a9bc4b49849aa0b3c7ae73dd404658c1e70a8989e'
FROZEN_CAPTURE_SHA256 = '118677d18a237d15d08f412ee2404406d8ba8dfe5279fa1903eaf46051ac1f50'
HOST_INVENTORY_SHA256 = '65ead97d826e9ec407059abe86a011576a19f04cb716843dabf9bafbc5110610'
JDK_INPUTS = ('bin/java', 'lib/modules', 'release')
MUTANTS = {
    'coordinate': ('(float)p.x(),(float)p.y(),(float)p.z()', '(float)p.x()+.125f,(float)p.y(),(float)p.z()'),
    'uv': ('setUv((float)uv.u(),(float)uv.v())', 'setUv((float)uv.u()+.125f,(float)uv.v())'),
    'color': ('rgb>>>16&255,rgb>>>8&255,rgb&255,alpha', 'rgb>>>16&255,rgb>>>8&255,(rgb&255)^1,alpha'),
    'live-mask': ('p.selectedMask(),p.liveMask(),p.breakTicks()', 'p.selectedMask(),0,p.breakTicks()'),
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
        read(root / 'scripts/verify-zero-gap-armor-v57-fallback.py')
        require(json.loads(read(support / 'frozen-v57.json')) == dict(commit=COMMIT, sources=FROZEN), 'FROZEN_MANIFEST_CHANGED')
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
        require(sorted(fixtures) == json.loads(inventory_data), 'UNKNOWN_OR_MISSING_HOST')
        fixtures['ZeroGapArmorFallbackHarness.java'] = read(support / 'ZeroGapArmorFallbackHarness.java')
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
        with tempfile.TemporaryDirectory(prefix='zero-gap-armor-v57-proof-') as td:
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
                           'cn.academy.port.client.ZeroGapArmorFallbackHarness', lane, report / name,
                           root / 'src/main/resources'], report / (name + '-execution.log'))
                summary = json.loads(raw)
                summary['cases_sha256'] = sha((report / name / 'cases.tsv').read_bytes())
                summary['full_capture_sha256'] = capture_hash(report / name / 'capture.txt.gz')
                return summary

            frozen_classes = compile_lane('frozen', frozen)
            baseline = execute('frozen', frozen_classes, 'frozen')
            require(baseline['cases'] == 810 and baseline['nonempty_cases'] == 618 and baseline['vertices'] == 190108, 'FROZEN_MATRIX_INCOMPLETE')
            require(baseline['cases_sha256'] == FROZEN_CASES_SHA256 and baseline['full_capture_sha256'] == FROZEN_CAPTURE_SHA256, 'FROZEN_CAPTURE_CHANGED')
            result['frozen'] = baseline
            current = frozen if args.frozen_only else {p: read(root / p) for p in [*FROZEN, *CURRENT_ONLY]}
            if not args.frozen_only:
                for ext in ('json', 'vsh', 'fsh'):
                    read(root / ('src/main/resources/assets/academy/shaders/core/zero_gap_armor_edge.' + ext))
            current_classes = frozen_classes if args.frozen_only else compile_lane('current', current)
            if not args.frozen_only:
                for lane in ('off', 'missing', 'corrupt', 'forced', 'config-unloaded', 'shader-compile', 'shader-link', 'shader-pending', 'shader-stale', 'missing-uniform-ModelViewMat', 'missing-uniform-ProjMat', 'missing-uniform-ColorModulator', 'missing-uniform-VisualPhase', 'midframe-generation', 'midframe-unavailable', *['typed-' + name + '-' + kind for name in ('ModelViewMat', 'ProjMat', 'ColorModulator', 'VisualPhase') for kind in ('type', 'count')], 'off-typed-VisualPhase-type', 'off-typed-ModelViewMat-count'):
                    summary = execute(lane, current_classes, lane)
                    mismatch = differences(report / 'frozen/cases.tsv', report / lane / 'cases.tsv')
                    require(not mismatch, 'FROZEN_DIFFERENTIAL_MISMATCH ' + lane + ': ' + json.dumps(mismatch[:3]))
                    require(summary['full_capture_sha256'] == baseline['full_capture_sha256'], 'FULL_CAPTURE_MISMATCH ' + lane)
                    result['lanes'][lane] = dict(result='EXACT_MATCH', **summary)
            if not args.frozen_only:
                enabled = execute('enabled', current_classes, 'enabled')
                mismatches = differences(report / 'frozen/cases.tsv', report / 'enabled/cases.tsv')
                require(mismatches, 'ENABLED_LANE_NEVER_CHANGED_RENDERING')
                a = (report / 'frozen/cases.tsv').read_text().splitlines()
                b = (report / 'enabled/cases.tsv').read_text().splitlines()
                unchanged_states = 0
                for left, right in zip(a, b):
                    old, new = left.split('\t'), right.split('\t')
                    require(old[-1] == new[-1], 'ENHANCED_MUTATED_RECEIPT_OR_LEASE_STATE ' + old[0])
                    unchanged_states += 1
                    if old[1] == '0':
                        require(new[1] == '0', 'ENHANCED_REVIVED_EMPTY_RENDER ' + old[0])
                    unchanged_phase = re.search(r'^matrix/s\d+/kind(?:0|2|3|4|5|6|8)/', old[0])
                    if unchanged_phase or old[0].startswith('life/silence') or old[0] == 'bounds/injected-overflow-real-order-submit':
                        require(old == new, 'ENHANCED_CHANGED_ORIGINAL_PHASE ' + old[0])
                with gzip.open(report / 'enabled/capture.txt.gz', 'rt') as stream:
                    optional_batches = sum('draw:academy_zero_gap_armor_edge:' in line for line in stream)
                require(optional_batches > 0, 'ENHANCED_SHADER_TYPE_NEVER_SUBMITTED')
                result['enabled_integration'] = dict(result='CPU_INTEGRATION_SMOKE', differing_cases=len(mismatches),
                                                     unchanged_state_cases=unchanged_states, optional_batches=optional_batches, **enabled)
            for name, (before, after) in MUTANTS.items():
                relative = PREFIX + 'client/ZeroGapArmorEffects.java'
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
                relative = PREFIX + 'client/ZeroGapArmorMaterial.java'
                source = current[relative].decode()
                require(source.count('readNBytes(131073)') == 1 and source.count('bytes.length>131072') == 1, 'RESOURCE_BOUND_MUTATION_ANCHOR_CHANGED')
                mutated = dict(current)
                mutated[relative] = source.replace('readNBytes(131073)', 'readNBytes(65537)').replace('bytes.length>131072', 'bytes.length>65536').encode()
                classes = compile_lane('mutant-resource-bound', mutated)
                witness = 'OPTIONAL_RESOURCE_GATE_NOT_EXERCISED:enabled:2'
                run([java, '-ea', '-Xmx256m', '-cp', classes, 'cn.academy.port.client.ZeroGapArmorFallbackHarness',
                     'enabled', report / 'mutant-resource-bound', root / 'src/main/resources', 'material-only'],
                    report / 'mutant-resource-bound-execution.log', expected_failure=witness)
                result['negative_controls'].append(dict(name='resource-bound-65536', result='DETECTED', witness=witness,
                                                       authentic_mdshield_bytes=(root / 'src/main/resources/assets/academy/textures/effects/mdshield.png').stat().st_size))
                guard = 'uniform.getType()!=type||uniform.getCount()!=count'
                require(source.count(guard) == 1, 'UNIFORM_DESCRIPTOR_GUARD_ANCHOR_CHANGED')
                for name, replacement, lane, descriptor in (
                        ('uniform-type-guard', 'uniform.getCount()!=count', 'typed-VisualPhase-type', 'VisualPhase:type0:count1'),
                        ('uniform-count-guard', 'uniform.getType()!=type', 'typed-ModelViewMat-count', 'ModelViewMat:type10:count1')):
                    mutated = dict(current)
                    mutated[relative] = source.replace(guard, replacement).encode()
                    classes = compile_lane('mutant-' + name, mutated)
                    witness = 'INVALID_SHADER_NOT_CLOSED'
                    run([java, '-ea', '-Xmx256m', '-cp', classes, 'cn.academy.port.client.ZeroGapArmorFallbackHarness',
                         lane, report / ('mutant-' + name), root / 'src/main/resources', 'material-only'],
                        report / ('mutant-' + name + '-execution.log'), expected_failure=witness)
                    result['negative_controls'].append(dict(name=name, result='DETECTED', witness=witness,
                                                           lane=lane, native_descriptor=descriptor))
        for path, expected in receipts.items():
            require(sha(path.read_bytes()) == expected, 'INPUT_CHANGED_BEFORE_COMPLETION ' + str(path))
        result.update(result='PASS_CPU_FROZEN_CAPTURE_SELF_TEST' if args.frozen_only else 'PASS_CPU_FROZEN_DIFFERENTIAL',
                      comparisons=['Raw emitted and transformed coordinate/UV float bits, RGBA, vertex order, shader uniform calls, texture/render-state selection, buffer requests and flushes',
                                   'Actual accepted frames, masks, identity/sequence, per-plate contacts/ages, lease clocks and controlled input notifications',
                                   'Model-view stack, pose and shader-color restoration'],
                      coverage=['All eight funded subsets for six facing yaw choices and six physical phases; selected-but-empty and unselected panes',
                                'Startup, active, break debt1..4, ended/fade, absorbed HIT, breaking HIT, corner HIT and remote historical HIT; ages0..7',
                                'Near-plane, inside/upward, ordinary and world-border views; transformed pose and distance cull',
                                'Duplicate HIT, movement/rotation without relocated historical contacts, pause/death, world/player/connection/epoch/screen/camera/spectator changes, unloaded chunks, wrong render stage',
                                'Local abort, ended replay, 12quiet+4fade retirement, queued clear/session/world/connection invalidation',
                                'Five accepted owners with four rendered; explicit overflowing real-mesh input reaches the real768quad post-clip budget and actual drawOrdered submission',
                                'Actual optional shader registration: OFF, original resource/hash failures, forced/unloaded config, controlled compile/link failures, pending/stale callbacks and each missing required uniform',
                                'Generation change or option unavailability during the first loaded-plate query exercises whole-frame fallback before optional submission',
                                'Eight resource JSON type/count mismatches and two OFF+typed mismatches construct native-shaped descriptors, close rejected candidates, and retain exact classic output; surgical type/count guard-removal controls must fail',
                                'Enabled material integration changes rendering while all810 receipt/lease-state captures, startup/break/end output and empty culls remain unchanged'],
                      limits=['Ten frozen/current production source files are compiled complete. Minecraft/player/world/transport/eligibility/JOML/GL/vertex buffers are explicit CPU hosts; unknown source dependencies fail compilation.',
                              'Shader resource bytes are actual and original resource hashes are frozen; uniform names/types/counts come from actual JSON declarations using pinned native type numbering and vector-count adjustment while compilation/linking and injected missing-uniform outcomes are controlled hosts, not a GPU test. Midframe faults are explicit one-shot chunk-query callbacks, not native resource reload scheduling.',
                              'The768quad case injects12sets of native mesh faces into the actual ordering/submission methods. The public renderer remains capped at4owners; this does not claim4owners naturally exhaust the budget.',
                              'No native pixels, full gameplay compatibility, driver state, or historical test admission is claimed.',
                              'Both lanes use one receipted Java21 runtime/compiler. Explicit source/target21 is used because this installation lacks ct.sym; no --release21 public-API compatibility claim.'])
        (report / 'input-receipts.sha256').write_text(''.join(f'{value}  {path}\n' for path, value in sorted(receipts.items())))
        (report / 'evidence.json').write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
        (report / result['result']).write_text(CONTRACT + '\n')
        return result
    except Exception as failure:
        result['error'] = str(failure)
        (report / 'failure.json').write_text(json.dumps(result, indent=2, sort_keys=True) + '\n')
        raise


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument('--project', type=Path, default=Path(__file__).resolve().parents[1])
    p.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', '/usr/lib/jvm/java-21-openjdk-amd64')))
    p.add_argument('--report', type=Path, required=True)
    p.add_argument('--frozen-only', action='store_true', help='Self-test frozen capture/mutation sensitivity; does not claim current fallback equivalence')
    args = p.parse_args()
    try:
        print(json.dumps(prove(args), indent=2, sort_keys=True))
        return 0
    except Exception as failure:
        print(json.dumps(dict(contract=CONTRACT, result='FAIL', error=str(failure)), indent=2))
        return 1


if __name__ == '__main__':
    sys.exit(main())
