#!/usr/bin/env python3
"""Require archival Railgun, current Railgun, and current Light Shield evidence.

This is a recording/compiler contract, not native Minecraft or GPU acceptance.
The original Git oracle remains separate from the explicitly reconstructed bytes.
"""
from pathlib import Path
import argparse
import copy
import hashlib
import importlib.util
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
FIXTURES = 'src/test/resources/vfx2/render-contract-v2'
PRE31 = FIXTURES + '/ClassicEffects.pre31.java.txt'
PATCH = FIXTURES + '/0011-vfx2-railgun-reference.patch'
INPUT31 = 'src/test/resources/vfx2/railgun-grand/historical-31/ClassicEffects.java.txt'
BASE35 = 'src/test/resources/vfx2/railgun-near-eye/baseline-35'
GRAND = 'scripts/verify-railgun-grand-integration.py'
SHIELD = 'scripts/verify-light-shield-grand-integration.py'
NEAR = 'scripts/verify-railgun-near-eye-integration.py'
GIT_BASELINE = 'ec0c40bab302b878f3cf14df0540f83f61c184c7'
PRE31_SHA = '106e7670ef6d7ba1b21f4acdc4d95fa9fd08eae1ea6da2062b1044c3a9a0438f'
INPUT31_SHA = '96ac659d49b4ccddea08ada95670e198c3a83f088f7d5f5764bb4e0ebb359ab4'
PATCH_SHA = '2b47ceffbf61bd2b7a5e9b0cb3e6c38df5b163ef287b9640a027a1793637e1a6'
GUARDS = {
    'scripts/verify-displaced-needle-ledger.py': '2aa986c4b7e67319e8a0068ac21a296e435a93d307d6ac17d1084fe07a095aa4',
    'scripts/verify-classic-terminal-ingress.py': 'd0b349b1a418003f986a4d9f7f17edfb2dfe43406a87462652ee811cba0f109a',
    'scripts/verify-classic-terminal-oracles.py': '13ba676ce718c9f590455207867fc0ced634435a95bc9cd77f948af5e8586ae8',
}
FROZEN = {
    GRAND: 'e5214b71c861cc4ce48cb8eb86cf2212e604c6549e7752adbe4d99b9a2f60d03',
    SHIELD: 'fe38d04875c6d578a580c17b090c14cc4c423ffe842e9da41e323cbe81f1091b',
    NEAR: '15428f1c82f667bd79c3846b8ea0f471d7d16cb0c30a08ead9fd2f434a776b75',
    'scripts/verify-railgun-vfx2-integration.py': 'b76d19409243e3fe5efe9fbbdb766d5af9a65ec06fbf23604fd26a393b02b3c6',
    'scripts/verify-light-shield-vfx2-integration.py': 'b1cb6e23575709917d6a11baf3529fa5879cf2f2ca807ade8107c4467b0fe62a',
    **GUARDS,
}
REVIEWED36 = {
    'src/main/java/cn/academy/port/client/ClassicEffects.java': 'e9cfa0d45a688f886c52f4c8190cd06a28d3866373e2b6aa72f40442e998e5b7',
    'src/main/java/cn/academy/port/client/vfx2/RailgunVfx2.java': '9961844c08f827dff49cf1e7bb1047e9cc2fe92888ec0ec1f032ccf61ba8cb6c',
}
PROVENANCE = {
    'version': 'render-contract-v2',
    'source': 'Restored .35 archive and its supplied 0011 patch; no recovered Git commit',
    'missingHistoricalCommit': GIT_BASELINE,
    'patchSha256': PATCH_SHA,
    'input31': {'path': INPUT31, 'sha256': INPUT31_SHA, 'bytes': 34385,
                'gitBlob': 'f907f44716a97986df3f246bd3abbb4125dc4efd'},
    'pre31': {'path': PRE31, 'sha256': PRE31_SHA, 'bytes': 23227,
              'gitBlob': '0beeb102eef36326d76dd74c4afdec66b1c62aff'},
    'reconstruction': {'reverseHunks': 11, 'offset': 0, 'fuzz': 0, 'forwardRoundTripRequired': True},
    'guardProvenance': 'Byte-compared with the independently restored baseline-source archive at creation',
    'guardSha256': GUARDS,
    'claim': 'Exact patch preimage only; neither pinned-git-show nor ec0c40 commit restoration',
}
ROUTE = """// Versioned render contract: one complete execution, with retained public task aliases.
tasks.register('renderContractV2Test', Exec) {
    dependsOn 'writeVfx2NativeClasspath'
    commandLine 'python3', 'scripts/verify-render-contract-v2.py',
            '--native-config-classpath', vfx2NativeClasspath.get().asFile.absolutePath,
            '--report', layout.buildDirectory.file('vfx2/render-contract-v2.json').get().asFile.absolutePath
}
tasks.register('railgunVfx2IntegrationTest') { dependsOn 'renderContractV2Test' }
tasks.register('lightShieldVfx2IntegrationTest') { dependsOn 'railgunVfx2IntegrationTest' }
tasks.named('check') { dependsOn 'renderContractV2Test', 'railgunVfx2IntegrationTest', 'lightShieldVfx2IntegrationTest' }
"""


def digest(data):
    return hashlib.sha256(data if isinstance(data, bytes) else data.encode()).hexdigest()


def pinned(root, name, expected, label='Pinned input changed'):
    data = (root / name).read_bytes()
    assert digest(data) == expected, label + ': ' + name
    return data


def load_frozen(name, root=ROOT):
    pinned(root, name, FROZEN[name], 'Frozen runner changed')
    spec = importlib.util.spec_from_file_location(Path(name).stem.replace('-', '_'), root / name)
    module = importlib.util.module_from_spec(spec)
    prior = sys.dont_write_bytecode
    try:
        sys.dont_write_bytecode = True
        spec.loader.exec_module(module)
    finally:
        sys.dont_write_bytecode = prior
    return module


def replace_once(source, before, after):
    assert source.count(before) == 1, 'Audited replacement must match exactly once: ' + before[:90]
    return source.replace(before, after, 1)


def patch_hunks(patch):
    target = 'src/main/java/cn/academy/port/client/ClassicEffects.java'
    marker = 'diff --git a/' + target + ' b/' + target + '\n'
    assert patch.count(marker) == 1, 'Patch must contain one ClassicEffects section'
    section = patch.split(marker, 1)[1].split('diff --git ', 1)[0]
    assert section.startswith('index 0beeb10..f907f44 100644\n'), 'Patch blob witness changed'
    lines, hunks = section.splitlines(keepends=True), []
    index = 0
    while index < len(lines):
        line = lines[index]
        match = re.match(r'@@ -(\d+)(?:,(\d+))? \+(\d+)(?:,(\d+))? @@', line)
        if not match:
            index += 1
            continue
        old_start, old_count, new_start, new_count = match.groups()
        old, new = [], []
        index += 1
        while index < len(lines) and not lines[index].startswith('@@ '):
            item = lines[index]
            assert item[:1] in ' +-\n', 'Unsupported patch line'
            if item[:1] in (' ', '-'):
                old.append(item[1:])
            if item[:1] in (' ', '+'):
                new.append(item[1:])
            index += 1
        assert len(old) == int(old_count or 1) and len(new) == int(new_count or 1), 'Patch hunk length changed'
        hunks.append((int(old_start) - 1, old, int(new_start) - 1, new))
    assert len(hunks) == 11, 'Exactly eleven reverse hunks required'
    return hunks


def apply_exact(source, hunks, reverse):
    lines, output, position = source.splitlines(keepends=True), [], 0
    for old_start, old, new_start, new in hunks:
        start, expected, replacement = (new_start, new, old) if reverse else (old_start, old, new)
        assert start >= position and lines[start:start + len(expected)] == expected, 'Patch hunk exact context mismatch; no offset or fuzz allowed'
        output.extend(lines[position:start])
        output.extend(replacement)
        position = start + len(expected)
    output.extend(lines[position:])
    return ''.join(output).encode()


def verify_inputs(root=ROOT, routing=True):
    for name, expected in FROZEN.items():
        pinned(root, name, expected, 'Frozen runner changed')
    near = load_frozen(NEAR, root)
    assert len(near.BASELINE) == 13, 'Complete thirteen-file .35 graph required'
    for name, expected in near.BASELINE.items():
        pinned(root, BASE35 + '/' + name + '.txt', expected, 'Archived .35 graph changed')
    patch = pinned(root, PATCH, PATCH_SHA, 'Patch changed')
    source31 = pinned(root, INPUT31, INPUT31_SHA, 'Historical .31 input changed')
    pre31 = pinned(root, PRE31, PRE31_SHA, 'Reconstructed pre31 fixture changed')
    assert len(source31) == 34385 and len(pre31) == 23227, 'Reconstruction byte count changed'
    for data, expected in [(source31, PROVENANCE['input31']['gitBlob']), (pre31, PROVENANCE['pre31']['gitBlob'])]:
        assert hashlib.sha1(b'blob ' + str(len(data)).encode() + b'\0' + data).hexdigest() == expected, 'Patch blob identity changed'
    hunks = patch_hunks(patch.decode())
    assert apply_exact(source31.decode(), hunks, True) == pre31, 'Reverse patch preimage differs'
    assert apply_exact(pre31.decode(), hunks, False) == source31, 'Forward patch roundtrip differs'
    for name, expected in GUARDS.items():
        archived = pinned(root, FIXTURES + '/guards/' + Path(name).name, expected, 'Archived guard changed')
        assert archived == (root / name).read_bytes(), 'Historical guard differs from archive: ' + name
    assert json.loads((root / FIXTURES / 'provenance.json').read_text()) == PROVENANCE, 'Reconstruction provenance changed'
    for name, expected in REVIEWED36.items():
        pinned(root, name, expected, 'Reviewed .36 integration changed')
    if routing:
        verify_routing(root)
    return {'reconstruction': PROVENANCE, 'archival35SourceHashes': near.BASELINE,
            'frozenRunnersAndGuards': FROZEN, 'reviewed36IntegrationHashes': REVIEWED36}


def verify_routing(root):
    build = (root / 'build.gradle').read_text()
    assert build.count(ROUTE) == 1, 'Aggregate V2 task route changed'
    assert not re.search(r"['\"](?:railgunVfx2IntegrationTest|lightShieldVfx2IntegrationTest)['\"]\s*:", build), 'Legacy integration names must be complete V2 aliases'
    retained_dependency = "tasks.named('lightShieldVfx2IntegrationTest') { dependsOn 'railgunVfx2IntegrationTest' }"
    remainder = build.replace(ROUTE, '', 1)
    assert remainder.count(retained_dependency) == 1, 'Original Shield to Railgun dependency changed'
    remainder = remainder.replace(retained_dependency, '', 1)
    assert not re.search(r"['\"](?:renderContractV2Test|railgunVfx2IntegrationTest|lightShieldVfx2IntegrationTest)['\"]", remainder), 'Additional render task override is not reviewed'
    return {'executor': 'renderContractV2Test', 'railgunAlias': 'railgunVfx2IntegrationTest',
            'lightShieldAlias': 'lightShieldVfx2IntegrationTest', 'allThreeStagesRequired': True}


def validate_results(results):
    assert set(results) == {'archivalRailgun', 'currentRailgun', 'currentLightShield'}, 'All three fresh render stages are mandatory'
    for label, report in results.items():
        assert report.get('result') == 'PASS', 'Required render stage did not pass: ' + label
    archive, current, shield = (results[name] for name in ['archivalRailgun', 'currentRailgun', 'currentLightShield'])
    assert archive.get('contractVersion') == 'railgun-grand-archive-v1', 'Archival replay version missing'
    assert archive['nativeConfig']['result'] == 'PASS', 'Archival native config is mandatory'
    assert len(archive['mutationControlsRejected']) == 29, 'All archival Java controls required'
    assert len(archive['grandRailgunEnvelope']['analyticAlphaProfile']['mutationControlsRejected']) == 8, 'All archival profile controls required'
    assert current.get('version') == 'restored-35-near-eye-differential-v1', 'Current near-eye dependency changed'
    assert current['sharedBufferAdapter'].get('nativeBodyVerified') is True and current['sharedBufferAdapter'].get('source') == 'pinnedMinecraft1211Body', 'Current native shared-buffer witness is mandatory'
    assert current['shaderContract']['nativeVanillaSourceComparison']['result'] == 'PASS', 'Current native vanilla shader witnesses are mandatory'
    assert len(current['mutationControlsRejected']) == 9 and len(current['shaderContract']['shaderMutationControlsRejected']) == 5, 'All current near-eye controls required'
    assert shield.get('contractVersion') == 'light-shield-render-v2', 'Complete current Light Shield version missing'
    assert shield['nativeConfig']['result'] == 'PASS' and shield['nativeJoml']['actualMatrixAndQuaternionArithmetic'] is True, 'Current Shield native config and JOML are mandatory'
    expected_shield_controls={'historicalGrandFixtureCorruption','reviewedClassicEffectsCorruption','reviewedRailgunVfx2Corruption','rangePredecessorCorruption','rangeTableCorruption','rangeDamageOutsideApprovedDelta'}
    assert len(shield['originalLightShieldMutationControlsRejected']) == 6 and len(shield['versionedContractMutationControlsRejected']) == 6 and set(shield['versionedContractMutationControlsRejected'])==expected_shield_controls, 'All original Light Shield and reviewed gameplay-range corruption controls required'


def input_negative_controls(root=ROOT):
    near = load_frozen(NEAR, root)
    cases = [(PATCH, 'Patch changed'), (INPUT31, 'Historical .31 input changed'), (PRE31, 'Reconstructed pre31 fixture changed')]
    cases += [(name, 'Frozen runner changed') for name in FROZEN]
    cases += [(BASE35 + '/' + name + '.txt', 'Archived .35 graph changed') for name in near.BASELINE]
    cases += [(FIXTURES + '/guards/' + Path(name).name, 'Archived guard changed') for name in GUARDS]
    cases += [(name, 'Reviewed .36 integration changed') for name in REVIEWED36]
    cases += [(FIXTURES + '/provenance.json', 'Reconstruction provenance changed'), ('build.gradle', 'Aggregate V2 task route changed')]
    paths = {name for name, _ in cases}
    rejected = []
    with tempfile.TemporaryDirectory(prefix='render-contract-v2-controls-') as directory:
        isolated = Path(directory)
        for name in paths:
            destination = isolated / name
            destination.parent.mkdir(parents=True, exist_ok=True)
            destination.write_bytes((root / name).read_bytes())
        verify_inputs(isolated)
        for name, expected in cases:
            path = isolated / name
            original = path.read_bytes()
            if name.endswith('provenance.json'):
                changed = json.loads(original)
                changed['claim'] = 'Incorrectly claims recovered Git provenance'
                path.write_text(json.dumps(changed))
            elif name == 'build.gradle':
                path.write_bytes(original.replace(b"dependsOn 'renderContractV2Test'", b"dependsOn 'writeVfx2NativeClasspath'", 1))
            else:
                path.write_bytes(original + b'\n// Isolated negative control.\n')
            try:
                verify_inputs(isolated)
            except AssertionError as error:
                assert expected in str(error), 'Input control hit unintended assertion: ' + name + ': ' + str(error)
                rejected.append(name)
            else:
                raise AssertionError('Input corruption was accepted: ' + name)
            finally:
                path.write_bytes(original)
        verify_inputs(isolated)
        build = isolated / 'build.gradle'
        original = build.read_bytes()
        build.write_bytes(original + b"\ntasks.named('renderContractV2Test') { enabled = false }\n")
        try:
            verify_inputs(isolated)
        except AssertionError as error:
            assert 'Additional render task override' in str(error), 'Appended override hit unintended assertion'
            rejected.append('build.gradle: appended disabled-task override')
        else:
            raise AssertionError('Appended disabled-task override was accepted')
        finally:
            build.write_bytes(original)
        verify_inputs(isolated)
    return rejected


def result_negative_controls(results):
    controls = [
        ('missingNearEyeDependency', lambda r: r.pop('currentRailgun'), 'All three'),
        ('changedNearEyeDependency', lambda r: r['currentRailgun'].update(version='unrelated-runner'), 'dependency changed'),
        ('failedChild', lambda r: r['currentRailgun'].update(result='FAIL'), 'did not pass'),
        ('notRunChild', lambda r: r['currentRailgun'].update(result='NOT_RUN'), 'did not pass'),
        ('modeledSharedBuffer', lambda r: r['currentRailgun']['sharedBufferAdapter'].update(nativeBodyVerified=False, source='explicitSharedBufferModel'), 'native shared-buffer'),
        ('missingNativeShader', lambda r: r['currentRailgun']['shaderContract']['nativeVanillaSourceComparison'].update(result='NOT_RUN'), 'native vanilla shader'),
        ('missingNativeConfig', lambda r: r['currentLightShield']['nativeConfig'].update(result='NOT_RUN'), 'native config and JOML'),
        ('missingArchivalMutation', lambda r: r['archivalRailgun']['mutationControlsRejected'].pop(), 'archival Java controls'),
    ]
    rejected = []
    for label, mutate, expected in controls:
        changed = copy.deepcopy(results)
        mutate(changed)
        try:
            validate_results(changed)
        except AssertionError as error:
            assert expected in str(error), 'Result control hit unintended assertion: ' + label
            rejected.append(label)
        else:
            raise AssertionError('Invalid child result accepted: ' + label)
    return rejected


def run_child(command, report_path):
    result = subprocess.run([str(value) for value in command], cwd=ROOT, text=True, capture_output=True)
    assert result.returncode == 0, 'Required render child exited nonzero:\n' + result.stdout + result.stderr
    assert report_path.is_file(), 'Required render child produced no report'
    return json.loads(report_path.read_text())


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--native-config-classpath', type=Path, required=True)
    parser.add_argument('--pinned-minecraft-sources', type=Path)
    parser.add_argument('--vanilla-client-jar', type=Path)
    parser.add_argument('--report', type=Path)
    args = parser.parse_args()
    inputs = verify_inputs()
    input_controls = input_negative_controls()
    archive = (args.pinned_minecraft_sources or ROOT / 'build/moddev/artifacts/neoforge-21.1.252-sources.jar').resolve()
    vanilla = (args.vanilla_client_jar or Path(os.environ.get('GRADLE_USER_HOME', Path.home() / '.gradle')) / 'caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar').resolve()
    cp = args.native_config_classpath.resolve()
    assert archive.is_file() and vanilla.is_file() and cp.is_file(), 'Native sources, vanilla shader archive, and native classpath are mandatory'
    assert all(Path(name).is_file() for name in cp.read_text().strip().split(os.pathsep)), 'Native classpath contains missing artifacts'
    stages = [
        ('archivalRailgun', 'scripts/verify-railgun-grand-archive-v1.py', ['--native-config-classpath', cp]),
        ('currentRailgun', NEAR, ['--vanilla-client-jar', vanilla]),
        ('currentLightShield', 'scripts/verify-light-shield-render-v2.py', ['--native-config-classpath', cp]),
    ]
    results = {}
    with tempfile.TemporaryDirectory(prefix='render-contract-v2-results-') as directory:
        for name, script, extra in stages:
            report = Path(directory) / (name + '.json')
            results[name] = run_child([sys.executable, ROOT / script, '--pinned-minecraft-sources', archive, '--report', report, *extra], report)
        validate_results(results)
        # A nonzero process must fail even if it leaves a superficially passing report.
        fake = Path(directory) / 'stale.json'
        fake.write_text('{"result":"PASS"}')
        try:
            run_child([sys.executable, '-c', 'raise SystemExit(7)'], fake)
        except AssertionError as error:
            assert 'exited nonzero' in str(error)
        else:
            raise AssertionError('Nonzero child with stale report accepted')
    result_controls = result_negative_controls(results) + ['nonzeroChildWithPassingReport']
    git = subprocess.run(['git', 'cat-file', '-e', GIT_BASELINE + '^{commit}'], cwd=ROOT, capture_output=True)
    report = {'result': 'PASS', 'contractVersion': 'render-contract-v2', 'inputs': inputs,
              'taskRouting': verify_routing(ROOT), 'stages': results,
              'inputMutationControlsRejected': input_controls, 'resultMutationControlsRejected': result_controls,
              'historicalGitOracle': {'result': 'NOT_RUN', 'commit': GIT_BASELINE,
                                      'reason': 'missingGit' if git.returncode else 'separateHistoricalRunner',
                                      'reconstructedPatchBytesAreNotGitProvenance': True},
              'nativeMinecraftOrGpuRuntime': False,
              'limitations': ['Archival .35 replay is separate from current .36 render behavior',
                              'Native Java config/JOML and source witnesses do not establish native Minecraft or GPU rendering',
                              'The three unchanged historical geometry tasks and full aggregate check must pass separately']}
    if args.report:
        args.report.parent.mkdir(parents=True, exist_ok=True)
        args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
