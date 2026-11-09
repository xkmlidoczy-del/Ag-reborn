#!/usr/bin/env python3
"""Bounded M41/current Charging core differential; no Gradle, game or native launch.

The reference is compiled from byte-exact members of an immutable M41 ZIP.
The positive candidate is the existing compileJava output, never a substitute
recompile. Closed isolated loaders execute the same driver and compare every
ledger/event trace byte. contract-v1.json has no update/refresh mode. This explicit
v7 companion admits only NEW431 appended to the pinned .65 registry. The v1/v2/v3/v4/v5/v6
runners and all predecessor fixtures remain immutable, separately runnable inputs.
"""
import argparse
import hashlib
import io
import json
import os
import re
from pathlib import Path
import shutil
import subprocess
import tempfile
import zipfile

ORIGINAL_SHA = '8dd1dc1345cb687347d6a89abae5f30587a3dbd97a3dd31c7377da4e361c602b'
SUBJECT = 'cn.academy.port.core.CurrentChargingSession'
FIXTURES = Path('src/test/resources/current-geometry-proof/charging-core')
REGISTRY_V7 = 'registry-431-v7'
REGISTRY_V7_SHA = 'e03244a09ed7454f4c86aa34a108a377f5b363187b206d1ac690989b4d48890f'


def require(condition, message):
    if not condition:
        raise AssertionError(message)


def sha(data):
    return hashlib.sha256(data).hexdigest()


def reconstruct_registry(baseline, amendment):
    """Apply only the pinned three hunks, then reverse every hunk byte-exactly."""
    require(sha(baseline) == amendment['baseline_source_sha256'], 'REGISTRY_BASELINE_BYTES_CHANGED')
    reconstructed = baseline
    require(len(amendment['approved_edits']) == 3, 'REGISTRY_PATCH_COUNT_CHANGED')
    for edit in amendment['approved_edits']:
        before, after = edit['before'].encode(), edit['after'].encode()
        require(reconstructed.count(before) == 1, 'REGISTRY_PATCH_SITE_CHANGED ' + edit['name'])
        reconstructed = reconstructed.replace(before, after)
    require(sha(reconstructed) == amendment['reconstructed_source_sha256'], 'REGISTRY_RECONSTRUCTION_CHANGED')
    reversed_source = reconstructed
    for edit in reversed(amendment['approved_edits']):
        before, after = edit['before'].encode(), edit['after'].encode()
        require(reversed_source.count(after) == 1, 'REGISTRY_REVERSE_SITE_CHANGED ' + edit['name'])
        reversed_source = reversed_source.replace(after, before)
    require(reversed_source == baseline, 'REGISTRY_REVERSE_ROUNDTRIP_CHANGED')
    return reconstructed


def check_registry_append(candidate, baseline, amendment):
    require(candidate == reconstruct_registry(baseline, amendment), 'REGISTRY_431_NONAPPEND_CHANGE')
    require(baseline.count(b'new SkillCatalog.Skill(') == amendment['registered_highpower_before'] == 43,
            'REGISTRY_V6_COUNT_CHANGED')
    require(candidate.count(b'new SkillCatalog.Skill(') == amendment['registered_highpower_after'] == 44,
            'REGISTRY_V7_COUNT_CHANGED')


def registry_append_controls(candidate, baseline, amendment):
    controls = []
    # Exercise protection of every old registration, including prior NEW412, NEW413, NEW421, NEW422 and NEW423.
    registrations = re.findall(rb'new SkillCatalog.Skill\([^,\n]+, ([^,\n]+), ([0-9]+), (?:true|false),', baseline)
    require(len(registrations) == 43, 'REGISTRY_OLD_REGISTRATION_CONTROL_COUNT_CHANGED')
    mutations = {}
    for identifier, level in registrations:
        before = b', ' + identifier + b', ' + level + b', '
        after = b', ' + identifier + b', ' + str(int(level) + 1).encode() + b', '
        mutations['old-registration-level-' + identifier.decode()] = (before, after)
    old_412 = (b'new SkillCatalog.Skill(HighPowerCategories.VECMANIP, VECTOR_PARRY, 1, true,\n'
               b'                    List.of(new SkillCatalog.Requirement(DEFLECTION_STRIKE, 0)), 0)')
    old_413 = (b'new SkillCatalog.Skill(HighPowerCategories.VECMANIP, BRAKING_TOUCH, 1, true,\n'
               b'                    List.of(new SkillCatalog.Requirement(DEFLECTION_STRIKE, 0)), 0)')
    new_421 = (b'new SkillCatalog.Skill(HighPowerCategories.VECMANIP, SHEAR_WINDBLADE, 2, true,\n'
               b'                    List.of(new SkillCatalog.Requirement(DEFLECTION_STRIKE, 0)), 0)')
    new_422 = (b'new SkillCatalog.Skill(HighPowerCategories.VECMANIP, INERTIAL_ANCHOR, 2, true,\n'
               b'                    List.of(new SkillCatalog.Requirement(VECTOR_PARRY, 0)), 0)')
    mutations.update({
        'old-registration-id-order': (b'IDS = Set.of(FORKED_ARC, MAGNETIC_BRACER,',
                                      b'IDS = Set.of(MAGNETIC_BRACER, FORKED_ARC,'),
        'old-412-proposal-number': (b'VECTOR_PARRY_PROPOSAL = 412;', b'VECTOR_PARRY_PROPOSAL = 414;'),
        'old-412-learned-dependency': (old_412, old_412.replace(b'(DEFLECTION_STRIKE, 0)', b'(DEFLECTION_STRIKE, .5)')),
        'old-412-id-order': (b'DEFLECTION_STRIKE, VECTOR_PARRY, BRAKING_TOUCH, SHEAR_WINDBLADE, INERTIAL_ANCHOR, YAW_PALM, TRAJECTORY_RETURN);',
                             b'VECTOR_PARRY, DEFLECTION_STRIKE, BRAKING_TOUCH, SHEAR_WINDBLADE, INERTIAL_ANCHOR, YAW_PALM, TRAJECTORY_RETURN);'),
        'old-413-proposal-number': (b'BRAKING_TOUCH_PROPOSAL = 413;', b'BRAKING_TOUCH_PROPOSAL = 414;'),
        'old-413-learned-dependency': (old_413, old_413.replace(b'(DEFLECTION_STRIKE, 0)', b'(VECTOR_PARRY, 0)')),
        'old-413-prerequisite-experience': (old_413, old_413.replace(b'(DEFLECTION_STRIKE, 0)', b'(DEFLECTION_STRIKE, .5)')),
        'old-413-category': (old_413, old_413.replace(b'HighPowerCategories.VECMANIP', b'HighPowerCategories.TELEPORTER')),
        'old-413-level': (old_413, old_413.replace(b'BRAKING_TOUCH, 1,', b'BRAKING_TOUCH, 2,')),
        'old-413-controllable': (old_413, old_413.replace(b'1, true,', b'1, false,')),
        'old-413-any-learned-level': (old_413, old_413.replace(b')), 0)', b')), 1)')),
        'old-413-id-order': (b'DEFLECTION_STRIKE, VECTOR_PARRY, BRAKING_TOUCH, SHEAR_WINDBLADE, INERTIAL_ANCHOR, YAW_PALM, TRAJECTORY_RETURN);',
                             b'DEFLECTION_STRIKE, BRAKING_TOUCH, VECTOR_PARRY, SHEAR_WINDBLADE, INERTIAL_ANCHOR, YAW_PALM, TRAJECTORY_RETURN);'),
        'old-413-record-order': (old_412 + b',\n            ' + old_413,
                                 old_413 + b',\n            ' + old_412),
        'old-421-proposal-number': (b'SHEAR_WINDBLADE_PROPOSAL = 421;', b'SHEAR_WINDBLADE_PROPOSAL = 422;'),
        'old-421-learned-dependency': (new_421, new_421.replace(b'(DEFLECTION_STRIKE, 0)', b'(BRAKING_TOUCH, 0)')),
        'old-421-prerequisite-experience': (new_421, new_421.replace(b'(DEFLECTION_STRIKE, 0)', b'(DEFLECTION_STRIKE, .5)')),
        'old-421-category': (new_421, new_421.replace(b'HighPowerCategories.VECMANIP', b'HighPowerCategories.TELEPORTER')),
        'old-421-level': (new_421, new_421.replace(b'SHEAR_WINDBLADE, 2,', b'SHEAR_WINDBLADE, 1,')),
        'old-421-controllable': (new_421, new_421.replace(b'2, true,', b'2, false,')),
        'old-421-any-learned-level': (new_421, new_421.replace(b')), 0)', b')), 1)')),
        'old-421-id-order': (b'VECTOR_PARRY, BRAKING_TOUCH, SHEAR_WINDBLADE, INERTIAL_ANCHOR, YAW_PALM, TRAJECTORY_RETURN);',
                             b'VECTOR_PARRY, SHEAR_WINDBLADE, BRAKING_TOUCH, INERTIAL_ANCHOR, YAW_PALM, TRAJECTORY_RETURN);'),
        'old-421-record-order': (old_413 + b',\n            ' + new_421,
                                 new_421 + b',\n            ' + old_413),
        'old-422-id-value': (b'INERTIAL_ANCHOR = "inertial_anchor";', b'INERTIAL_ANCHOR = "inertial_anchor_other";'),
        'old-422-proposal-number': (b'INERTIAL_ANCHOR_PROPOSAL = 422;', b'INERTIAL_ANCHOR_PROPOSAL = 423;'),
        'old-422-learned-dependency': (new_422, new_422.replace(b'(VECTOR_PARRY, 0)', b'(DEFLECTION_STRIKE, 0)')),
        'old-422-prerequisite-experience': (new_422, new_422.replace(b'(VECTOR_PARRY, 0)', b'(VECTOR_PARRY, .5)')),
        'old-422-category': (new_422, new_422.replace(b'HighPowerCategories.VECMANIP', b'HighPowerCategories.TELEPORTER')),
        'old-422-level': (new_422, new_422.replace(b'INERTIAL_ANCHOR, 2,', b'INERTIAL_ANCHOR, 1,')),
        'old-422-controllable': (new_422, new_422.replace(b'2, true,', b'2, false,')),
        'old-422-any-learned-level': (new_422, new_422.replace(b')), 0)', b')), 1)')),
        'old-422-id-order': (b'BRAKING_TOUCH, SHEAR_WINDBLADE, INERTIAL_ANCHOR, YAW_PALM, TRAJECTORY_RETURN);',
                             b'BRAKING_TOUCH, INERTIAL_ANCHOR, SHEAR_WINDBLADE, YAW_PALM, TRAJECTORY_RETURN);'),
        'old-422-record-order': (new_421 + b',\n            ' + new_422,
                                 new_422 + b',\n            ' + new_421)})
    new_423 = (b'new SkillCatalog.Skill(HighPowerCategories.VECMANIP, YAW_PALM, 2, true,\n'
               b'                    List.of(new SkillCatalog.Requirement(BRAKING_TOUCH, 0)), 0)')
    mutations.update({
        'old-423-id-value': (b'YAW_PALM = "yaw_palm";', b'YAW_PALM = "yaw_palm_other";'),
        'old-423-proposal-number': (b'YAW_PALM_PROPOSAL = 423;', b'YAW_PALM_PROPOSAL = 424;'),
        'old-423-learned-dependency': (new_423, new_423.replace(b'(BRAKING_TOUCH, 0)', b'(VECTOR_PARRY, 0)')),
        'old-423-prerequisite-experience': (new_423, new_423.replace(b'(BRAKING_TOUCH, 0)', b'(BRAKING_TOUCH, .5)')),
        'old-423-category': (new_423, new_423.replace(b'HighPowerCategories.VECMANIP', b'HighPowerCategories.TELEPORTER')),
        'old-423-level': (new_423, new_423.replace(b'YAW_PALM, 2,', b'YAW_PALM, 1,')),
        'old-423-controllable': (new_423, new_423.replace(b'2, true,', b'2, false,')),
        'old-423-any-learned-level': (new_423, new_423.replace(b')), 0)', b')), 1)')),
        'old-423-id-order': (b'SHEAR_WINDBLADE, INERTIAL_ANCHOR, YAW_PALM, TRAJECTORY_RETURN);',
                             b'SHEAR_WINDBLADE, YAW_PALM, INERTIAL_ANCHOR, TRAJECTORY_RETURN);'),
        'old-423-record-order': (new_422 + b',\n            ' + new_423,
                                 new_423 + b',\n            ' + new_422)})
    new_431 = (b'new SkillCatalog.Skill(HighPowerCategories.VECMANIP, TRAJECTORY_RETURN, 3, true,\n'
               b'                    List.of(new SkillCatalog.Requirement(YAW_PALM, 0)), 0)')
    mutations.update({
        'new-431-id-value': (b'TRAJECTORY_RETURN = "trajectory_return";', b'TRAJECTORY_RETURN = "trajectory_return_other";'),
        'new-431-proposal-number': (b'TRAJECTORY_RETURN_PROPOSAL = 431;', b'TRAJECTORY_RETURN_PROPOSAL = 432;'),
        'new-431-learned-dependency': (new_431, new_431.replace(b'(YAW_PALM, 0)', b'(BRAKING_TOUCH, 0)')),
        'new-431-prerequisite-experience': (new_431, new_431.replace(b'(YAW_PALM, 0)', b'(YAW_PALM, .5)')),
        'new-431-category': (new_431, new_431.replace(b'HighPowerCategories.VECMANIP', b'HighPowerCategories.TELEPORTER')),
        'new-431-level': (new_431, new_431.replace(b'TRAJECTORY_RETURN, 3,', b'TRAJECTORY_RETURN, 2,')),
        'new-431-controllable': (new_431, new_431.replace(b'3, true,', b'3, false,')),
        'new-431-any-learned-level': (new_431, new_431.replace(b')), 0)', b')), 1)')),
        'new-431-id-order': (b'INERTIAL_ANCHOR, YAW_PALM, TRAJECTORY_RETURN);',
                             b'INERTIAL_ANCHOR, TRAJECTORY_RETURN, YAW_PALM);'),
        'new-431-record-order': (new_423 + b',\n            ' + new_431,
                                 new_431 + b',\n            ' + new_423)})
    require(len(mutations) == 95, 'REGISTRY_NEGATIVE_CONTROL_COUNT_CHANGED')
    for name, (before, after) in mutations.items():
        require(candidate.count(before) == 1 and before != after, 'REGISTRY_NEGATIVE_SITE_CHANGED ' + name)
        mutant = candidate.replace(before, after)
        try:
            check_registry_append(mutant, baseline, amendment)
        except AssertionError as error:
            require(str(error) == 'REGISTRY_431_NONAPPEND_CHANGE', 'WRONG_REGISTRY_REJECTION ' + name)
        else:
            raise AssertionError('REGISTRY_MUTANT_SURVIVED ' + name)
        controls.append({'mutation': name, 'result': 'EXPECTED_REJECTION',
                         'marker': 'REGISTRY_431_NONAPPEND_CHANGE', 'mutant_source_sha256': sha(mutant)})
    return controls


def load_registry_chain(project, fixture, contract_bytes, contract, read):
    amendment_root = fixture / REGISTRY_V7
    amendment_bytes = read(amendment_root / 'contract-v7.json')
    require(sha(amendment_bytes) == REGISTRY_V7_SHA, 'REGISTRY_V7_CONTRACT_CHANGED')
    amendment = json.loads(amendment_bytes)
    require(amendment['version'] == 7, 'REGISTRY_CONTRACT_VERSION_CHANGED')
    require(sha(contract_bytes) == amendment['base_contract_sha256'], 'REGISTRY_BASE_CONTRACT_CHANGED')
    predecessor_audit = []
    for relative, expected in amendment['immutable_predecessor_inputs'].items():
        data = read(project / relative)
        require(sha(data) == expected, 'REGISTRY_PREDECESSOR_INPUT_CHANGED ' + relative)
        predecessor_audit.append({'path': relative, 'sha256': expected, 'status': 'UNCHANGED_PREDECESSOR_BYTES'})
    v2_root = fixture / 'registry-412-v2'
    v3_root = fixture / 'registry-413-v3'
    v4_root = fixture / 'registry-421-v4'
    v5_root = fixture / 'registry-422-v5'
    v6_root = fixture / 'registry-423-v6'
    v2 = json.loads(read(v2_root / 'contract-v2.json'))
    v3 = json.loads(read(v3_root / 'contract-v3.json'))
    v4 = json.loads(read(v4_root / 'contract-v4.json'))
    v5 = json.loads(read(v5_root / 'contract-v5.json'))
    v6 = json.loads(read(v6_root / 'contract-v6.json'))
    for predecessor, version in ((v2, 2), (v3, 3), (v4, 4), (v5, 5), (v6, 6)):
        require(predecessor['version'] == version and predecessor['base_contract_sha256'] == sha(contract_bytes),
                'REGISTRY_PREDECESSOR_BASE_CHANGED')
        require(predecessor['source_path'] == amendment['source_path']
                and predecessor['class_name'] == amendment['class_name'], 'REGISTRY_PREDECESSOR_SUBJECT_CHANGED')
    v58 = read(v2_root / v2['baseline_source'])
    require(sha(v58) == contract['current_sources'][amendment['source_path']], 'REGISTRY_BASELINE_NOT_V1_SOURCE')
    require(v2['old_class_sha256'] == contract['current_classes'][amendment['class_name']],
            'REGISTRY_BASELINE_NOT_V1_CLASS')
    v59 = read(v3_root / v3['baseline_source'])
    require(v59 == reconstruct_registry(v58, v2), 'REGISTRY_V59_NOT_RECONSTRUCTED_V2')
    require(v3['old_class_sha256'] == v2['reconstructed_class_sha256'],
            'REGISTRY_V59_CLASS_NOT_PINNED_V2_OUTPUT')
    v60 = read(v4_root / v4['baseline_source'])
    require(v60 == reconstruct_registry(v59, v3), 'REGISTRY_V60_NOT_RECONSTRUCTED_V3')
    require(v4['old_class_sha256'] == v3['reconstructed_class_sha256'],
            'REGISTRY_V60_CLASS_NOT_PINNED_V3_OUTPUT')
    v61 = read(v5_root / v5['baseline_source'])
    require(v61 == reconstruct_registry(v60, v4), 'REGISTRY_V61_NOT_RECONSTRUCTED_V4')
    require(v5['old_class_sha256'] == v4['reconstructed_class_sha256'],
            'REGISTRY_V61_CLASS_NOT_PINNED_V4_OUTPUT')
    v64 = read(v6_root / v6['baseline_source'])
    require(v64 == reconstruct_registry(v61, v5), 'REGISTRY_V64_NOT_RECONSTRUCTED_V5')
    require(v6['old_class_sha256'] == v5['reconstructed_class_sha256'],
            'REGISTRY_V64_CLASS_NOT_PINNED_V5_OUTPUT')
    baseline = read(amendment_root / amendment['baseline_source'])
    require(baseline == reconstruct_registry(v64, v6), 'REGISTRY_V65_NOT_RECONSTRUCTED_V6')
    require(amendment['old_class_sha256'] == v6['reconstructed_class_sha256'],
            'REGISTRY_V65_CLASS_NOT_PINNED_V6_OUTPUT')
    reconstructed = reconstruct_registry(baseline, amendment)
    return amendment, baseline, reconstructed, predecessor_audit


def reproduce_registry_classes(work, javac, actual, baseline, reconstructed, amendment, read):
    """Compile evidence only; the executed positive still comes from compileJava."""
    outputs = {}
    for label, source, expected in (
            ('v6-v65-baseline', baseline, amendment['old_class_sha256']),
            ('v7-v66-reconstructed', reconstructed, amendment['reconstructed_class_sha256'])):
        destination = work / ('registry-' + label)
        src = destination / 'src/cn/academy/port/highpower/HighPowerSkills.java'
        src.parent.mkdir(parents=True)
        src.write_bytes(source)
        classes = destination / 'classes'
        classes.mkdir()
        command([javac, '--release', '21', '-g', '-encoding', 'UTF-8', '-proc:none', '-implicit:none',
                 '-sourcepath', '', '-classpath', actual, '-d', classes, src])
        member = amendment['class_name'].replace('.', '/') + '.class'
        require({p.relative_to(classes).as_posix() for p in classes.rglob('*.class')} == {member},
                'REGISTRY_REPRODUCED_CLASS_CLOSURE_CHANGED ' + label)
        compiled = (classes / member).read_bytes()
        require(sha(compiled) == expected, 'REGISTRY_REPRODUCED_CLASS_CHANGED ' + label)
        if label == 'v7-v66-reconstructed':
            require(compiled == read(actual / member), 'REGISTRY_COMPILEJAVA_NOT_RECONSTRUCTED_BYTES')
        outputs[label] = {'source_sha256': sha(source), 'class_sha256': sha(compiled),
                          'class_file': str(classes / member)}
    return outputs


def reserve_report(path):
    """A rejected/failed invocation must never leave an older result looking current."""
    path = path.resolve()
    try:
        path.mkdir(parents=True, exist_ok=False)
    except FileExistsError as error:
        raise AssertionError('REPORT_DIRECTORY_ALREADY_EXISTS ' + str(path) + '; choose a fresh report path') from error
    return path


def check_report_lifecycle():
    """Bounded infrastructure selfcheck; does not compile or execute either core."""
    with tempfile.TemporaryDirectory(prefix='charging-report-lifecycle-') as temporary:
        target = Path(temporary) / 'report'
        reserve_report(target)
        sentinel = b'{"result":"PRIOR_SUCCESS_SENTINEL"}\n'
        (target / 'result.json').write_bytes(sentinel)
        try:
            reserve_report(target)
        except AssertionError as error:
            require('REPORT_DIRECTORY_ALREADY_EXISTS' in str(error), 'UNEXPECTED_REPORT_REJECTION')
        else:
            raise AssertionError('EXISTING_REPORT_WAS_REUSED')
        require((target / 'result.json').read_bytes() == sentinel, 'PRIOR_EVIDENCE_CHANGED')
        require({p.name for p in target.iterdir()} == {'result.json'}, 'REJECTED_RERUN_WROTE_REPORT')
    return 'EXISTING_REPORT_REJECTED_PRIOR_EVIDENCE_UNCHANGED'


def command(args):
    result = subprocess.run(list(map(str, args)), text=True, capture_output=True)
    require(result.returncode == 0, 'COMMAND_FAILED ' + ' '.join(map(str, args)) + '\n' + result.stdout + result.stderr)
    return result.stdout


def jdk(project, requested):
    candidates = [requested] if requested else []
    if os.environ.get('JAVA_HOME'):
        candidates.append(Path(os.environ['JAVA_HOME']))
    if shutil.which('javac'):
        candidates.append(Path(shutil.which('javac')).resolve().parent.parent)
    for parent in [project, *project.parents]:
        candidates.extend(sorted((parent / 'aca-tools').glob('jdk*')))
    for home in candidates:
        if (home / 'bin/javac').is_file() and '21.' in command([home / 'bin/javac', '-version']):
            return home.resolve()
    raise AssertionError('Existing JDK 21 required; use --jdk or JAVA_HOME')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=Path(__file__).resolve().parents[1])
    parser.add_argument('--original-source', type=Path)
    parser.add_argument('--jdk', type=Path)
    parser.add_argument('--gson-jar', type=Path)
    parser.add_argument('--nbt-jar', type=Path)
    parser.add_argument('--report-dir', '--report', type=Path)
    parser.add_argument('--classpath-file', type=Path)
    parser.add_argument('--contract-version', choices=('7',), default='7',
                        help='7 admits only NEW431; use the unchanged old runners for v1/v2/v3/v4/v5/v6')
    args = parser.parse_args()
    project = args.project.resolve()
    report = reserve_report(args.report_dir or project / 'build/reports/current-geometry-proof/charging-core-v7')
    report_lifecycle = check_report_lifecycle()
    fixture = project / FIXTURES
    snapshots = {}

    def read(path):
        path = path.resolve()
        data = path.read_bytes()
        require(path not in snapshots or snapshots[path] == sha(data), 'INPUT_CHANGED ' + str(path))
        snapshots[path] = sha(data)
        return data

    contract_bytes = read(fixture / 'contract-v1.json')
    contract = json.loads(contract_bytes)
    amendment, baseline_registry, reconstructed_registry, predecessor_audit = load_registry_chain(
        project, fixture, contract_bytes, contract, read)
    registry_controls = []
    original = (args.original_source or project.parents[1] / 'aca-original-m41' /
                'academycraft-m41-meltdowner-camera-source.zip').resolve()
    archive_bytes = read(original)
    require(sha(archive_bytes) == ORIGINAL_SHA, 'IMMUTABLE_M41_ZIP_MISMATCH')
    archive = zipfile.ZipFile(io.BytesIO(archive_bytes))
    require(contract['original_zip_sha256'] == ORIGINAL_SHA, 'ORIGINAL_ANCHOR_CHANGED')
    for path, expected in contract['original_sources'].items():
        require(archive.namelist().count(path) == 1, 'AMBIGUOUS_ORIGINAL_MEMBER ' + path)
        require(sha(archive.read(path)) == expected, 'ORIGINAL_MEMBER_CHANGED ' + path)
    source_audit = []
    for path, expected in contract['current_sources'].items():
        current = read(project / path)
        if amendment and path == amendment['source_path']:
            check_registry_append(current, baseline_registry, amendment)
            registry_controls = registry_append_controls(current, baseline_registry, amendment)
            status = 'ONLY_REVIEWED_431_APPEND_TO_IMMUTABLE_V6'
        else:
            require(sha(current) == expected, 'CURRENT_SOURCE_SNAPSHOT_CHANGED ' + path)
            status = 'UNCHANGED_V1_BYTES'
        source_audit.append({'path': path, 'v1_sha256': expected, 'current_sha256': sha(current), 'status': status})
    for path in ('ChargingCoreTrace.java', 'ChargingCoreLoader.java'):
        require(sha(read(fixture / path)) == contract['proof_sources'][path], 'PROOF_DRIVER_CHANGED ' + path)
    cpfile = args.classpath_file or project / 'build/vfx2/native-classpath.txt'
    selected = [Path(value) for line in read(cpfile).decode().splitlines() for value in line.split(os.pathsep) if value]
    gson = args.gson_jar
    if gson is None:
        matches = [p for p in selected if p.name == 'gson-2.10.1.jar']
        require(len(matches) == 1, 'Need unique official Gson 2.10.1 jar, or --gson-jar')
        gson = matches[0]
    nbt = args.nbt_jar or project / 'build/moddev/artifacts/neoforge-21.1.252-merged.jar'
    gson, nbt = gson.resolve(), nbt.resolve()
    gson_bytes, nbt_bytes = read(gson), read(nbt)
    require(sha(gson_bytes) == contract['gson_archive_sha256'], 'GSON_ARCHIVE_MISMATCH')
    jars = {'gson': (gson, zipfile.ZipFile(io.BytesIO(gson_bytes))),
            'nbt-signature': (nbt, zipfile.ZipFile(io.BytesIO(nbt_bytes)))}
    dependencies = {}
    for name, pin in contract['dependencies'].items():
        origin, jar = jars[pin['origin']]
        member = name.replace('.', '/') + '.class'
        require(jar.namelist().count(member) == 1 and sha(jar.read(member)) == pin['sha256'], 'OFFICIAL_API_CLASS_MISMATCH ' + name)
        dependencies[name] = (origin, pin['sha256'])
    actual = (project / 'build/classes/java/main').resolve()
    current_classes = {}
    for name, expected in contract['current_classes'].items():
        data = read(actual / (name.replace('.', '/') + '.class'))
        if amendment and name == amendment['class_name']:
            expected = amendment['reconstructed_class_sha256']
        require(sha(data) == expected, 'CURRENT_COMPILEJAVA_SNAPSHOT_CHANGED ' + name)
        current_classes[name] = (actual, expected)
    # Reject unexpected companions instead of silently broadening the production closure.
    roots = {name.split('$')[0] for name in current_classes}
    actual_names = {p.relative_to(actual).as_posix()[:-6].replace('/', '.')
                    for name in roots for p in (actual / name.replace('.', '/')).parent.glob(name.rsplit('.', 1)[1] + '*.class')
                    if p.stem == name.rsplit('.', 1)[1] or p.stem.startswith(name.rsplit('.', 1)[1] + '$')}
    require(actual_names == set(current_classes), 'CURRENT_CLASS_CLOSURE_CHANGED')
    catalog_member = 'src/main/resources/classic-skills.json'
    catalog_bytes = archive.read(catalog_member)
    require(sha(catalog_bytes) == contract['classic_catalog_sha256'], 'ORIGINAL_CATALOG_CHANGED')
    current_resource_root = (project / 'build/resources/main').resolve()
    require(read(current_resource_root / 'classic-skills.json') == catalog_bytes, 'PROCESSED_CLASSIC_CATALOG_DIFFERS_FROM_ORIGINAL')
    require(read(project / catalog_member) == catalog_bytes, 'SOURCE_CLASSIC_CATALOG_DIFFERS_FROM_ORIGINAL')
    home = jdk(project, args.jdk)
    work = Path(tempfile.mkdtemp(prefix='run-', dir=report))
    original_src, original_classes, original_resources = work / 'original-src', work / 'original-classes', work / 'original-resources'
    for relative in contract['original_sources']:
        out = original_src / relative
        out.parent.mkdir(parents=True, exist_ok=True)
        out.write_bytes(archive.read(relative))
        require(out.read_bytes() == archive.read(relative), 'EXTRACTION_CHANGED_ORIGINAL')
    original_resources.mkdir()
    (original_resources / 'classic-skills.json').write_bytes(catalog_bytes)
    original_classes.mkdir()
    javac, java = home / 'bin/javac', home / 'bin/java'
    registry_reproduction = (reproduce_registry_classes(work, javac, actual, baseline_registry,
                             reconstructed_registry, amendment, read) if amendment else None)
    source_args = [original_src / p for p in contract['original_sources']]
    command([javac, '--release', '21', '-proc:none', '-implicit:none', '-sourcepath', '',
                           '-classpath', os.pathsep.join(map(str, [gson, nbt])), '-d', original_classes, *source_args])
    original_entries = {p.relative_to(original_classes).as_posix()[:-6].replace('/', '.'):
                        (original_classes, sha(p.read_bytes())) for p in original_classes.rglob('*.class')}
    require(set(original_entries) == set(contract['original_classes']), 'ORIGINAL_COMPILED_CLOSURE_CHANGED')
    harness = work / 'harness'
    harness.mkdir()
    command([javac, '--release', '21', '-proc:none', '-implicit:none', '-sourcepath', '',
             '-classpath', os.pathsep.join(map(str, [original_classes, gson, nbt])), '-d', harness,
             fixture / 'ChargingCoreTrace.java'])
    harness_entries = {p.relative_to(harness).as_posix()[:-6].replace('/', '.'):
                       (harness, sha(p.read_bytes())) for p in harness.rglob('*.class')}
    loader = work / 'loader'
    loader.mkdir()
    command([javac, '--release', '21', '-proc:none', '-d', loader, fixture / 'ChargingCoreLoader.java'])

    def execute(mode, production, entries, resource_root):
        output = work / mode
        output.mkdir()
        manifest = output / 'admission.tsv'
        merged = {}
        for group in (entries, harness_entries, dependencies):
            require(not merged.keys() & group.keys(), 'DUPLICATE_CLASS_ADMISSION')
            merged.update(group)
        rows = ['class\t' + name + '\t' + str(origin.resolve()) + '\t' + digest
                for name, (origin, digest) in sorted(merged.items())]
        rows.append('resource\tclassic-skills.json\t' + str(resource_root.resolve()) + '\t' + sha(catalog_bytes))
        manifest.write_text('\n'.join(rows) + '\n')
        log = command([java, '-Xmx256m', '-cp', loader, 'ChargingCoreLoader', manifest, production, harness, output, mode])
        (output / 'execution.log').write_text(log)
        require(not (output / 'undeclared-loads.txt').read_text().strip(), 'UNDECLARED_RUNTIME_DEPENDENCY')
        return (output / 'trace.tsv').read_bytes(), output

    reference, reference_out = execute('original-m41', original_classes, original_entries, original_resources)
    require(sha(reference) == contract['original_trace_sha256'], 'FROZEN_ORIGINAL_TRACE_CHANGED')
    candidate, current_out = execute('actual-compileJava', actual, current_classes, current_resource_root)
    if candidate != reference:
        first = next((i for i, (a, b) in enumerate(zip(reference.splitlines(), candidate.splitlines())) if a != b), min(len(reference.splitlines()), len(candidate.splitlines())))
        raise AssertionError('M41_CURRENT_TRACE_DIFFERENCE line=' + str(first + 1) + ' reports=' + str(work))

    session_source = read(project / 'src/main/java/cn/academy/port/core/CurrentChargingSession.java').decode()
    mutations = {
        'cp': ('return lerp(3, 7, mastery);', 'return lerp(4, 7, mastery);'),
        'xp': ('SUPPORTED_EXPERIENCE = .0001F;', 'SUPPORTED_EXPERIENCE = .0002F;'),
        'order': ('''            if (!state.consumeSkill(ID,consumption(mastery), 0, creative))
                return finish(TickResult.RESOURCE_EXHAUSTED);
            boolean supported = target.supported();
            if (supported) target.charge(speed(mastery), false);
            award(supported);''', '''            boolean supported = target.supported();
            if (supported) target.charge(speed(mastery), false);
            award(supported);
            if (!state.consumeSkill(ID,consumption(mastery), 0, creative))
                return finish(TickResult.RESOURCE_EXHAUSTED);''')
    }
    controls = []
    for mutation, (before, after) in mutations.items():
        require(session_source.count(before) == 1, 'MUTATION_SITE_CHANGED ' + mutation)
        destination = work / ('mutant-' + mutation)
        mutant_source = destination / 'src/cn/academy/port/core/CurrentChargingSession.java'
        mutant_source.parent.mkdir(parents=True)
        mutant_source.write_text(session_source.replace(before, after))
        mutant_classes = destination / 'classes'
        mutant_classes.mkdir()
        command([javac, '--release', '21', '-proc:none', '-implicit:none', '-sourcepath', '', '-classpath', actual,
                 '-d', mutant_classes, mutant_source])
        replacement = mutant_classes / (SUBJECT.replace('.', '/') + '.class')
        entries = dict(current_classes)
        entries[SUBJECT] = (mutant_classes, sha(replacement.read_bytes()))
        trace, output = execute('negative-' + mutation, actual, entries, current_resource_root)
        require(trace != reference, 'EXECUTABLE_MUTANT_SURVIVED ' + mutation)
        a, b = reference.splitlines(), trace.splitlines()
        differing = [i for i in range(min(len(a), len(b))) if a[i] != b[i]]
        require(differing, 'NO_BEHAVIORAL_DIFFERENCE ' + mutation)
        index = differing[0]
        controls.append({'mutation': mutation, 'result': 'DETECTED_BY_EXECUTED_TRACE',
                         'first_different_line': index + 1, 'different_line_count': len(differing),
                         'original_line': a[index].decode(), 'mutant_line': b[index].decode(),
                         'trace_sha256': sha(trace), 'report': str(output)})
    # Independent loader failures: a digest cannot make an alternate directory a production origin;
    # resource uniqueness and denial of unknown application classes are executable checks too.
    base_rows = (current_out / 'admission.tsv').read_text().splitlines()
    subject_row = next(row for row in base_rows if row.startswith('class\t' + SUBJECT + '\t'))
    copied_root = work / 'wrong-origin'
    copied_class = copied_root / (SUBJECT.replace('.', '/') + '.class')
    copied_class.parent.mkdir(parents=True)
    copied_class.write_bytes(read(actual / (SUBJECT.replace('.', '/') + '.class')))
    bad_origin = subject_row.split('\t'); bad_origin[2] = str(copied_root.resolve())
    bad_digest = subject_row.split('\t'); bad_digest[3] = '0' * 64
    guards = {
        'unknown': (base_rows, 'UNDECLARED_DEPENDENCY cn.academy.port.UndeclaredChargingProofDependency'),
        'wrong-origin': ([('\t'.join(bad_origin) if row == subject_row else row) for row in base_rows], 'WRONG_PRODUCTION_ORIGIN'),
        'duplicate-resource': (base_rows + [base_rows[-1]], 'DUPLICATE_RESOURCE'),
        'wrong-digest': ([('\t'.join(bad_digest) if row == subject_row else row) for row in base_rows], 'INPUT_CLASS_CHANGED')}
    guard_results = []
    for name, (rows, marker) in guards.items():
        output = work / ('guard-' + name); output.mkdir()
        manifest = output / 'admission.tsv'; manifest.write_text('\n'.join(rows) + '\n')
        result = subprocess.run(list(map(str, [java, '-Xmx256m', '-cp', loader, 'ChargingCoreLoader', manifest,
            actual, harness, output, 'guard-' + name])), text=True, capture_output=True)
        log = result.stdout + result.stderr
        (output / 'execution.log').write_text(log)
        require(result.returncode != 0 and marker in log, 'LOADER_GUARD_NOT_ENFORCED ' + name + '\n' + log)
        guard_results.append({'control': name, 'result': 'EXPECTED_REJECTION', 'marker': marker})
    for relative, expected in contract['original_sources'].items():
        require(sha((original_src / relative).read_bytes()) == expected, 'ORIGINAL_SOURCE_MODIFIED_DURING_RUN')
    for path, digest in snapshots.items():
        require(sha(path.read_bytes()) == digest, 'INPUT_CHANGED_DURING_EXECUTION ' + str(path))
    original_loaded = [row.split('\t')[0] for row in (reference_out / 'loaded-classes.tsv').read_text().splitlines()]
    current_loaded = [row.split('\t')[0] for row in (current_out / 'loaded-classes.tsv').read_text().splitlines()]
    result = {
        'result': 'BOUNDED_ORIGINAL_M41_CORE_DIFFERENTIAL_PASS', 'original_zip_sha256': ORIGINAL_SHA,
        'jdk': command([java, '--version']).strip(), 'execution_heap_limit': '-Xmx256m', 'report_directory': str(work),
        'original_sources': contract['original_sources'], 'original_source_edits': 0,
        'positive_subject': 'actual build/classes/java/main; no positive production recompile',
        'trace_sha256': sha(reference), 'trace_lines': len(reference.splitlines()),
        'cases': int(reference.splitlines()[-1].decode().split('\t')[1]),
        'all_trace_bytes_identical': True, 'original_loaded_classes': original_loaded,
        'current_loaded_classes': current_loaded, 'unknown_dependency_attempts': 0,
        'real_code_sources_and_unique_resources_verified': True, 'fixed_inputs_rehashed_unchanged': len(snapshots),
        'negative_controls': controls, 'loader_guard_controls': guard_results,
        'report_lifecycle_control': report_lifecycle,
        'companion_contract_version': int(args.contract_version),
        'current_source_audit': source_audit,
        'registry_append_proof': ({
            'result': 'ONLY_REVIEWED_NEW431_APPEND_PROVED',
            'base_contract_sha256': amendment['base_contract_sha256'],
            'amendment_contract_sha256': REGISTRY_V7_SHA,
            'immutable_predecessor_audit': predecessor_audit,
            'predecessor_source_roundtrip': 'V58_PLUS_PINNED_412_EQUALS_V59_PLUS_PINNED_413_EQUALS_V60_PLUS_PINNED_421_EQUALS_V61_PLUS_PINNED_422_EQUALS_V64_PLUS_PINNED_423_EQUALS_FROZEN_V65',
            'current_source_roundtrip': 'FROZEN_V65_PLUS_ONLY_431_AND_REVERSE_BYTE_EXACT',
            'baseline_revision': amendment['baseline_revision'],
            'approved_edit_names': [edit['name'] for edit in amendment['approved_edits']],
            'registered_highpower_before': amendment['registered_highpower_before'],
            'registered_highpower_after': amendment['registered_highpower_after'],
            'reproduced_classes': registry_reproduction,
            'negative_controls': registry_controls,
            'classic_catalog_and_ordinals': 'EXACT_ORIGINAL_RESOURCE_BYTES_UNCHANGED',
            'classic_level_denominators': 'ALL_FOUR_CATEGORIES_LEVELS_1_TO_5_IN_UNCHANGED_M41_TRACE',
            'charging_path': 'ALL_V1_CORE_SOURCE_AND_CLASS_PINS_UNCHANGED_EXCEPT_REGISTRY',
            'historical_guards': amendment['historic_guard_status']
        } if amendment else None),
        'rng': 'Not applicable: admitted Charging core execution uses no RNG',
        'limitations': [
            'Finite classic non-enrolled core scenarios only; this is not full artifact or protocol equivalence.',
            'Native CurrentCharging ingress, capabilities, world adapters, enhanced transactions and lifecycle are separate tests.',
            'No native server, Gradle task, game, UI, graphics or rendering is started.',
            'Current class/source/dependency hashes bind repeatability; they are not evidence of historical equivalence.',
            'Official NBT class signatures compile the catalog but NBT snapshot/serialization methods are outside executed scope.',
            'Current HighPowerSkills catalog initialization is admitted explicitly; no high-power skill or enrollment is exercised.',
            'Existing historical archive guards and frozen fixture tests are unchanged and retain their separate status.'
        ]}
    (report / 'result.json').write_text(json.dumps(result, indent=2) + '\n')
    print(json.dumps({k: v for k, v in result.items() if k not in ('original_sources', 'original_loaded_classes', 'current_loaded_classes', 'negative_controls')}, indent=2))
    print('negative_controls=' + ','.join(c['mutation'] + ':DETECTED_BY_EXECUTED_TRACE' for c in controls))


if __name__ == '__main__':
    main()
