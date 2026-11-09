#!/usr/bin/env python3
"""Append-only .73 proof for one isolated exhaustion-test batch annotation.

The frozen v1 verifier still checks the unmodified distributable class manifest.
GameTest classes are excluded from that JAR: this companion separately proves
the exact source edit and reproduces both test-class families with native javac.
"""
from pathlib import Path
import argparse
import hashlib
import json
import os
import re
import shutil
import subprocess
import sys
import tempfile


ROOT = Path(__file__).resolve().parents[1]
FIX = ROOT / 'src/test/resources/wind-pressure-flow/v72'
V1 = ROOT / 'scripts/verify-wind-lift-flow-v72.py'
V1_SHA = '97705b5417df799cda13f56db3a607838f931f8e03e6467aeccc0d8e55536572'
BASE_COMMIT = 'f7399a6cdec425aa1ca2b685a8d5d961ae33112e'
SOURCE = Path('src/main/java/cn/academy/port/gametest/HighPowerWindPressureLiftRuntimeTests.java')
BASE_SOURCE = FIX / 'HighPowerWindPressureLiftRuntimeTests.java.txt'
BASE_SHA = 'b3fcc01c914932889d0ccf5bb440cea56e9b362512cc8994a585b465d528384b'
CURRENT_SHA = '8b973a3849d8211270d66b772f53e40584d38f27e0d132bbe654e78e3fff74cd'
METHOD = b'    public static void wind_lift_resource_exhaustion_stops_at_next_window_without_refund_or_free_grant(GameTestHelper h) {'
OLD = b'    @GameTest(template=TEMPLATE,batch=BATCH,timeoutTicks=35)\n' + METHOD
NEW = b'    @GameTest(template=TEMPLATE,batch=BATCH+"_exhaustion_order",timeoutTicks=35)\n' + METHOD
CLASS_PREFIX = 'cn/academy/port/gametest/HighPowerWindPressureLiftRuntimeTests'
TOP_CLASS = CLASS_PREFIX + '.class'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def command(args):
    result = subprocess.run(list(map(str, args)), cwd=ROOT, text=True,
                            capture_output=True)
    require(result.returncode == 0,
            'Command failed: ' + ' '.join(map(str, args)) + '\n' + result.stdout + result.stderr)
    return result.stdout + result.stderr


def validate_source(current, baseline):
    require(sha(baseline) == BASE_SHA, 'Frozen .72 test source changed')
    require(baseline.count(OLD) == 1 and NEW not in baseline,
            'Frozen exhaustion annotation is not unique')
    require(current.count(NEW) == 1 and OLD not in current,
            'Exactly one reviewed isolated exhaustion annotation is required')
    restored = current.replace(NEW, OLD, 1)
    require(restored == baseline,
            'Restoring only the isolated batch must reproduce the entire frozen test source')
    require(sha(current) == CURRENT_SHA, 'Reviewed isolated test source hash changed')
    return restored


def expect_rejected(name, action):
    try:
        action()
    except AssertionError:
        return {'mutation': name, 'result': 'EXPECTED_REJECTION'}
    raise AssertionError('Negative control survived: ' + name)


def source_negatives(current, baseline):
    controls = []
    variants = {
        'missing batch isolation': baseline,
        'unreviewed batch name': current.replace(NEW, NEW.replace(b'_exhaustion_order', b'_other'), 1),
        'duplicate isolated annotation': current + b'\n' + NEW + b'\n',
        'changed timeout': current.replace(NEW, NEW.replace(b'timeoutTicks=35', b'timeoutTicks=36'), 1),
    }
    start = current.index(NEW)
    end = current.index(b'\n    @GameTest(', start + len(NEW))
    body = current[start:end]
    for name, before, after in (
        ('delayed 20-tick assertion', b'f.afterPosts(20, () -> {', b'f.afterPosts(21, () -> {'),
        ('relaxed closure assertion',
         b'h.assertFalse(WindPressureLift.active(f.p) || WindPressureLift.consumeIntegrated(old, f.p.server)',
         b'h.assertFalse(WindPressureLift.consumeIntegrated(old, f.p.server)'),
        ('removed CP exhaustion', b'putDouble("cp", 0)', b'putDouble("cp", 125)'),
    ):
        require(body.count(before) == 1, 'Negative-control mutation site changed: ' + name)
        variants[name] = current[:start] + body.replace(before, after, 1) + current[end:]
    for name, mutant in variants.items():
        require(mutant != current, 'Negative control made no change: ' + name)
        controls.append(expect_rejected(name, lambda data=mutant: validate_source(data, baseline)))
    return controls


def find_javac(requested):
    candidates = []
    if requested:
        candidates.append(requested / 'bin/javac')
    else:
        if os.environ.get('JAVA_HOME'):
            candidates.append(Path(os.environ['JAVA_HOME']) / 'bin/javac')
        candidates.extend(sorted((ROOT / '.tools').glob('jdk*/bin/javac')))
        found = shutil.which('javac')
        if found:
            candidates.append(Path(found))
    for candidate in candidates:
        if candidate.is_file():
            version = command([candidate, '-version']).strip()
            if re.match(r'^javac 21(?:[.\s+\-]|$)', version):
                return candidate.resolve(), version
    raise AssertionError('Existing JDK 21 required; supply --jdk or JAVA_HOME')


def family_files(directory):
    parent = directory / Path(CLASS_PREFIX).parent
    stem = Path(CLASS_PREFIX).name
    return {path.relative_to(directory).as_posix(): path.read_bytes()
            for path in parent.glob(stem + '*.class')
            if path.name == stem + '.class' or path.name.startswith(stem + '$')}


def validate_family(actual, expected, description):
    require(actual.keys() == expected.keys(), description + ': class closure changed')
    differences = sorted(name for name in expected if actual[name] != expected[name])
    require(not differences, description + ': class bytes changed: ' + str(differences))


def compile_family(work, name, source, javac, classpath):
    destination = work / name
    source_path = destination / 'src' / (CLASS_PREFIX + '.java')
    source_path.parent.mkdir(parents=True)
    source_path.write_bytes(source)
    classes = destination / 'classes'
    classes.mkdir()
    command([javac, '--release', '21', '-g', '-encoding', 'UTF-8',
             '-proc:none', '-implicit:none', '-sourcepath', '',
             '-classpath', classpath, '-d', classes, source_path])
    result = family_files(classes)
    emitted = {path.relative_to(classes).as_posix() for path in classes.rglob('*.class')}
    require(TOP_CLASS in result and emitted == result.keys(),
            name + ': compilation must emit exactly the target test family')
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--jdk', type=Path)
    parser.add_argument('--classpath-file', type=Path, default=ROOT / 'build/vfx2/native-classpath.txt')
    parser.add_argument('--classes', type=Path, default=ROOT / 'build/classes/java/main')
    parser.add_argument('--report', type=Path, default=ROOT / 'build/reports/wind-pressure-flow-v72-v2.json')
    args = parser.parse_args()
    # Clear only this companion's previous summary so a failed invocation cannot
    # leave a stale PASS. Historical guards and fixtures are untouched.
    if args.report.exists():
        args.report.unlink()
    require(sha(V1.read_bytes()) == V1_SHA, 'Frozen v1 verifier changed')
    baseline = BASE_SOURCE.read_bytes()
    current = (ROOT / SOURCE).read_bytes()
    restored = validate_source(current, baseline)
    controls = source_negatives(current, baseline)

    # Run every v1 source, texture, server/motion-class and mutation check verbatim.
    # No manifest exception or normalized JAR is introduced for the test change.
    command([sys.executable, V1])
    v1_report = json.loads((ROOT / 'build/reports/wind-pressure-flow-v72.json').read_text())
    require(v1_report['result'] == 'PASS', 'Frozen v1 proof did not pass')
    manifest = json.loads((FIX / 'class-hashes.json').read_text())
    require(not any('/gametest/' in name for name in manifest),
            'Historical JAR proof scope changed; review test-class provenance')

    require(args.classpath_file.is_file(), 'Native classpath file missing; generate it with the existing build task')
    native_classpath = args.classpath_file.read_text().strip()
    require(native_classpath, 'Native classpath file is empty')
    classes = args.classes.resolve()
    require(classes.is_dir(), 'compileJava output is required')
    javac, compiler_version = find_javac(args.jdk)
    classpath = os.pathsep.join([str(classes), native_classpath])
    with tempfile.TemporaryDirectory(prefix='wind-lift-flow-v72-v2-') as temporary:
        work = Path(temporary)
        old_family = compile_family(work, 'restored-v72', restored, javac, classpath)
        new_family = compile_family(work, 'isolated-v73', current, javac, classpath)
        require(old_family.keys() == new_family.keys(), 'Annotation edit changed the emitted test family')
        changed = sorted(name for name in old_family if old_family[name] != new_family[name])
        require(changed == [TOP_CLASS], 'Only the test top class may differ after pairwise reproduction: ' + str(changed))
        built_family = family_files(classes)
        validate_family(built_family, new_family, 'compileJava does not reproduce reviewed current source')
        mutant = dict(new_family)
        mutant[TOP_CLASS] = mutant[TOP_CLASS] + b'\x00'
        controls.append(expect_rejected('unreviewed compiled test bytes',
            lambda: validate_family(mutant, new_family, 'Mutated compileJava fixture')))
        mutant_missing = dict(new_family)
        del mutant_missing[TOP_CLASS]
        controls.append(expect_rejected('missing compiled test class',
            lambda: validate_family(mutant_missing, new_family, 'Incomplete compileJava fixture')))
        old_hashes = {name: sha(data) for name, data in sorted(old_family.items())}
        new_hashes = {name: sha(data) for name, data in sorted(new_family.items())}

    report = {
        'result': 'PASS',
        'contract': 'wind-pressure-flow-v72-v2-isolated-exhaustion-annotation',
        'frozen_v1_script_sha256': V1_SHA,
        'frozen_v1_result': v1_report,
        'test_source_provenance': {'commit': BASE_COMMIT, 'path': SOURCE.as_posix(),
            'frozen_source_sha256': BASE_SHA, 'current_source_sha256': CURRENT_SHA},
        'only_source_edit': 'Exhaustion test batch BATCH becomes BATCH+"_exhaustion_order"',
        'unchanged_20_tick_assertion_and_entire_test_body': True,
        'compiler': compiler_version,
        'restored_test_class_sha256': old_hashes,
        'current_test_class_sha256': new_hashes,
        'pairwise_changed_test_classes': changed,
        'all_current_test_classes_equal_compileJava': True,
        'negative_controls': controls,
        'limitations': [
            'GameTest classes were excluded from the historical distributable JAR manifest; reconstructed baseline test hashes are not historical compiled-byte evidence',
            'This companion proves source/class identity and preserves the v1 proof; native GameTest execution is a separate required result',
        ],
    }
    args.report.parent.mkdir(parents=True, exist_ok=True)
    args.report.write_text(json.dumps(report, indent=2) + '\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
