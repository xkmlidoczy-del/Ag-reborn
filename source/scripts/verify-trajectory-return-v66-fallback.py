#!/usr/bin/env python3
"""Compile exact .66/current NEW431 receive/render paths and compare CPU submissions.
No Gradle invocation, GPU/rasterization claim, gameplay change, or fixture refresh mode.
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

CONTRACT = 'trajectory-return-v66-fallback-v1'
FROZEN_CAPTURE_SHA256 = '694e68e8a34f5a3e293331f709c022069f39444c9780ee2354be5687f206f4a4'
FROZEN_CASES_SHA256 = 'ed1f94226ec20f415874c0400d9c9dcd05c6ca98c01d0c0d981721f46bf4fe6f'
COMMIT = 'dbe8100aa9c222cfea16fdcf6329c2da2dca4dd3'
SUPPORT = 'src/test/resources/trajectory-return-v66-fallback'
PREFIX = 'src/main/java/cn/academy/port/'
# Independently retrieved .66 sources and explicit CPU fixture inventory; never auto-refreshed.
FROZEN_MANIFEST_SHA256 = '647a9c549cd561b995b36f41e6098631ec72656e25c5d4a2de79b147a94cf56d'
HOST_MANIFEST_SHA256 = '0742f1958fab5f6cd8edda4e520b3ce080939cd0b0a4e83975a006e3733f7975'
HARNESS_SHA256 = '5f924a0ca1fed72615350428efc718d083788a9041f75f7fa2c1e3ab3e7868d0'
CURRENT_ONLY = [PREFIX + 'HighPowerVisualConfig.java', *[PREFIX + 'client/' + name + '.java' for name in ('TrajectoryReturnPresentation', 'TrajectoryReturnEnhancedMesh')]]
LANES = ('off', 'config-unloaded', 'forced', 'missing', 'corrupt', 'oversized', 'reload-pending', 'reload-stale')


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, message):
    if not condition:
        raise RuntimeError(message)


def run(command, log, failure=None):
    result = subprocess.run([str(p) for p in command], capture_output=True, text=True)
    log.write_text(result.stdout + result.stderr)
    if failure:
        require(result.returncode != 0 and failure in result.stdout + result.stderr, 'NEGATIVE_CONTROL_NOT_DETECTED ' + str(log))
    else:
        require(result.returncode == 0, 'COMMAND_FAILED ' + str(log) + '\n' + (result.stderr or result.stdout)[-6000:])
    return result.stdout


def differences(first, second):
    left, right = first.read_text().splitlines(), second.read_text().splitlines()
    require(len(left) == len(right), 'CASE_COUNT_CHANGED')
    return [dict(case=a.split('\t')[0], expected=a, actual=b) for a, b in zip(left, right) if a != b]


def prove(args):
    root = args.project.resolve()
    support = root / SUPPORT
    report = args.report.resolve()
    require(not report.exists(), 'REPORT_MUST_BE_NEW ' + str(report))
    report.mkdir(parents=True)
    result = dict(contract=CONTRACT, baseline_commit=COMMIT, lanes={}, negative_controls=[])
    receipts = {}

    def read(path):
        data = path.read_bytes()
        receipts[path] = sha(data)
        return data

    try:
        manifest_bytes = read(support / 'frozen-v66.json')
        require(sha(manifest_bytes) == FROZEN_MANIFEST_SHA256, 'FROZEN_MANIFEST_CHANGED')
        manifest = json.loads(manifest_bytes)
        require(manifest['commit'] == COMMIT, 'BASELINE_COMMIT_CHANGED')
        frozen = {}
        for relative, expected in manifest['sha256'].items():
            data = read(support / 'frozen' / relative)
            require(sha(data) == expected, 'FROZEN_SOURCE_CHANGED ' + relative)
            frozen[relative] = data
        # Verify Git provenance when the original commit is available, without inventing Git metadata.
        exists = subprocess.run(['git', 'cat-file', '-e', COMMIT + '^{commit}'], cwd=root, capture_output=True)
        result['original_git_commit_available'] = exists.returncode == 0
        if exists.returncode == 0:
            for relative, data in frozen.items():
                original = subprocess.check_output(['git', 'show', COMMIT + ':' + relative], cwd=root)
                require(original == data, 'GIT_PROVENANCE_MISMATCH ' + relative)
        host_bytes = read(support / 'host-inventory.json')
        require(sha(host_bytes) == HOST_MANIFEST_SHA256, 'HOST_INVENTORY_CHANGED')
        inventory = json.loads(host_bytes)
        fixtures = {}
        actual = {p.relative_to(support).as_posix() for p in (support / 'hosts').rglob('*.java')}
        require(actual == set(inventory['sha256']), 'HOST_SET_CHANGED')
        for relative, expected in inventory['sha256'].items():
            data = read(support / relative)
            require(sha(data) == expected, 'HOST_CHANGED ' + relative)
            fixtures[relative] = data
        harness = read(support / 'TrajectoryReturnFallbackHarness.java')
        require(sha(harness) == HARNESS_SHA256, 'HARNESS_CHANGED')
        fixtures['TrajectoryReturnFallbackHarness.java'] = harness
        asset = 'src/main/resources/assets/academy/textures/effects/glow_line.png'
        require(sha(read(root / asset)) == inventory['original_glow_line_sha256'], 'ORIGINAL_TEXTURE_CHANGED')
        current = frozen if args.frozen_only else {p: read(root / p) for p in [*frozen, *CURRENT_ONLY]}
        if not args.frozen_only:
            for relative in frozen:
                if not relative.endswith('/TrajectoryReturnEffects.java'):
                    require(current[relative] == frozen[relative], 'ORIGINAL_RECEIPT_OR_MESH_CHANGED ' + relative)
        java = args.java_home.resolve() / 'bin/java'
        compiler = [java, '-Xmx256m', '-m', 'jdk.compiler/com.sun.tools.javac.Main', '-source', '21', '-target', '21']
        with tempfile.TemporaryDirectory(prefix='trajectory-return-v66-proof-') as scratch:
            temp = Path(scratch)
            hosts = []
            for relative, data in fixtures.items():
                path = temp / 'fixtures' / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_bytes(data)
                hosts.append(path)

            def compile_lane(name, sources):
                dest = temp / name / 'classes'
                dest.mkdir(parents=True)
                files = []
                for relative, data in sources.items():
                    path = temp / name / 'sources' / relative
                    path.parent.mkdir(parents=True, exist_ok=True)
                    path.write_bytes(data)
                    files.append(path)
                run([*compiler, '-proc:none', '-implicit:none', '-sourcepath', '', '-classpath', dest,
                     '-encoding', 'UTF-8', '-d', dest, *hosts, *files], report / (name + '-compile.log'))
                (report / (name + '-classes.json')).write_text(json.dumps({p.relative_to(dest).as_posix(): sha(p.read_bytes()) for p in sorted(dest.rglob('*.class'))}, indent=2) + '\n')
                return dest

            def execute(name, classes, lane, failure=None, extra=()):
                raw = run([java, '-ea', '-Xmx256m', '-cp', classes, 'cn.academy.port.client.TrajectoryReturnFallbackHarness',
                           lane, report / name, root / 'src/main/resources', *extra], report / (name + '-execution.log'), failure)
                if failure:
                    return None
                summary = json.loads(raw)
                summary['cases_sha256'] = sha((report / name / 'cases.tsv').read_bytes())
                summary['capture_sha256'] = sha(gzip.decompress((report / name / 'capture.txt.gz').read_bytes()))
                summary['exceptions_sha256'] = sha((report / name / 'exceptions.txt').read_bytes())
                return summary

            frozen_classes = compile_lane('frozen', frozen)
            baseline = execute('frozen', frozen_classes, 'frozen')
            require(baseline['cases'] == 886 and baseline['nonempty_cases'] == 363 and baseline['vertices'] == 35048
                    and baseline['peak_vertices'] == 672 and baseline['exception_cases'] == 3, 'FROZEN_COVERAGE_CHANGED')
            require(baseline['capture_sha256'] == FROZEN_CAPTURE_SHA256 and baseline['cases_sha256'] == FROZEN_CASES_SHA256, 'FROZEN_CAPTURE_CHANGED')
            result['frozen'] = baseline
            if not args.frozen_only:
                current_classes = compile_lane('current', current)
                for lane in LANES:
                    summary = execute(lane, current_classes, lane)
                    mismatch = differences(report / 'frozen/cases.tsv', report / lane / 'cases.tsv')
                    require(not mismatch, 'FROZEN_DIFFERENTIAL_MISMATCH ' + lane + ': ' + json.dumps(mismatch[:2]))
                    require(summary['capture_sha256'] == baseline['capture_sha256'], 'FULL_CAPTURE_CHANGED ' + lane)
                    require(summary['exceptions_sha256'] == baseline['exceptions_sha256'], 'EXCEPTION_STATE_CHANGED ' + lane)
                    result['lanes'][lane] = dict(result='EXACT_MATCH', **summary)
                enhanced = execute('enabled', current_classes, 'enabled')
                mismatch = differences(report / 'frozen/cases.tsv', report / 'enabled/cases.tsv')
                require(mismatch, 'ENABLED_PRODUCTION_BRANCH_NEVER_CHANGED_OUTPUT')
                for a, b in zip((report / 'frozen/cases.tsv').read_text().splitlines(), (report / 'enabled/cases.tsv').read_text().splitlines()):
                    left, right = a.split('\t'), b.split('\t')
                    require(left[0] == right[0] and left[-1] == right[-1], 'ENHANCED_CHANGED_AUTHENTICATED_STATE ' + left[0])
                result['enabled_integration'] = dict(result='CPU_BRANCH_SMOKE_ONLY', differing_cases=len(mismatch), **enhanced)
            mutants = [
                ('emitted-alpha', 'client/TrajectoryReturnEffects.java', 'setColor(gray,gray,gray,alpha)', 'setColor(gray,gray,gray,alpha/2)', 'off'),
                ('classic-shoulder-width', 'client/TrajectoryReturnMesh.java', 's.width*2.45', 's.width*2.46', 'off'),
                ('receipt-trail-expiry', 'client/TrajectoryReturnState.java', 'TRAIL_LIFE=5', 'TRAIL_LIFE=6', 'off'),
            ]
            if not args.frozen_only:
                mutants += [
                    ('off-latch-bypass', 'client/TrajectoryReturnEffects.java', '.findFirst().orElse(null), pressureRequested());', '.findFirst().orElse(null), true);', 'off'),
                    ('resource-selector-bypass', 'client/TrajectoryReturnEffects.java', 'return pressureReady &&', 'return true &&', 'missing'),
                ]
            for name, relative, before, after, lane in mutants:
                relative = PREFIX + relative
                source = current[relative].decode()
                require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED ' + name)
                mutated = dict(current)
                mutated[relative] = source.replace(before, after, 1).encode()
                classes = compile_lane('mutant-' + name, mutated)
                summary = execute('mutant-' + name, classes, 'frozen' if args.frozen_only else lane)
                mismatch = differences(report / 'frozen/cases.tsv', report / ('mutant-' + name) / 'cases.tsv')
                require(mismatch and summary['capture_sha256'] != baseline['capture_sha256'], 'MUTANT_SURVIVED ' + name)
                (report / ('mutant-' + name) / 'differences.json').write_text(json.dumps(mismatch, indent=2) + '\n')
                result['negative_controls'].append(dict(name=name, result='DETECTED', differing_cases=len(mismatch), first_case=mismatch[0]['case']))
            if not args.frozen_only:
                for name, before, after, lane, witness in (
                    ('resource-hash-guard', 'ORIGINAL_GLOW_SHA.equals(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))', 'true', 'corrupt', 'RESOURCE_READY_WRONG:corrupt'),
                    ('stale-reload-guard', 'if (revision == materialGeneration) pressureReady = accepted;', 'pressureReady = accepted;', 'reload-stale', 'RESOURCE_READY_WRONG:reload-stale'),
                ):
                    relative = PREFIX + 'client/TrajectoryReturnEffects.java'
                    source = current[relative].decode()
                    require(source.count(before) == 1, 'MUTATION_ANCHOR_CHANGED ' + name)
                    mutated = dict(current)
                    mutated[relative] = source.replace(before, after, 1).encode()
                    classes = compile_lane('mutant-' + name, mutated)
                    execute('mutant-' + name, classes, lane, failure=witness)
                    result['negative_controls'].append(dict(name=name, result='DETECTED', witness=witness))
        for path, expected in receipts.items():
            require(sha(path.read_bytes()) == expected, 'INPUT_CHANGED_DURING_PROOF ' + str(path))
        result.update(result='PASS_FROZEN_SELF_TEST' if args.frozen_only else 'PASS_CPU_FROZEN_DIFFERENTIAL',
                      comparisons=['Actual production Effects receive, tick, visibility, selector, profile, ribbon and vertex submissions',
                                   'Exact raw/transformed coordinate float bits, UV bits, RGBA, shader/texture/render states, buffer/flush order and terrain-query count',
                                   'Authenticated controllers, receipt fences/highwaters, cancellation, input notifications and render-state restoration'],
                      limits=['Isolated CPU host APIs substitute for Minecraft/NeoForge/JOML. This is not a GPU, native shader, rasterization, gameplay or screenshot acceptance claim.',
                              'Camera cases include oblique, near-eye, behind-camera, opaque-plane responses and coordinates near the world border.',
                              'Enabled lane establishes the real production branch differs without changing receipt state; enhanced geometry correctness remains a separate responsibility.',
                              'Java 21 source/target are explicit. No --release public API compatibility claim.'])
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
    parser.add_argument('--java-home', type=Path, default=Path(os.environ.get('JAVA_HOME', Path(__file__).resolve().parents[1] / '.tools/jdk-21.0.12.1+1')))
    parser.add_argument('--report', type=Path, required=True)
    parser.add_argument('--frozen-only', action='store_true', help='Exercise immutable .66 snapshot and three negative controls only; no current-equivalence claim')
    try:
        print(json.dumps(prove(parser.parse_args()), indent=2, sort_keys=True))
        return 0
    except Exception as failure:
        print(json.dumps(dict(contract=CONTRACT, result='FAIL', error=str(failure)), indent=2))
        return 1


if __name__ == '__main__':
    sys.exit(main())
