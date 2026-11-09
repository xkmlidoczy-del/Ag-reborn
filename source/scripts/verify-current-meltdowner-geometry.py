#!/usr/bin/env python3
"""Current compiled Meltdowner geometry companion; frozen historical admission is separate.

No Gradle, production compilation, fixture refresh, native GL, or client launch.
Four complete matrices and real current event/state paths run against explicit hosts.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import tempfile
import zipfile

CONTRACT = 'current-meltdowner-cpu-geometry-v1'
CONTRACT_SHA256 = 'f85bdcd01d35748d5223622d97b1f88c0430c03ce2644265af025d576d764fd5'
SUPPORT = 'src/test/resources/current-geometry-proof/meltdowner'
SUBJECT = 'cn.academy.port.client.ClassicMeltdownerBeamEffects'
MATERIAL = 'cn.academy.port.client.vfx2.MdNearEyeMaterial'
GOLDEN = '335d8736629a8bbb585927086393a787fb9019c87befd1b654c57df8b123482a'


def sha(data):
    return hashlib.sha256(data).hexdigest()


def require(ok, why):
    if not ok:
        raise ValueError(why)


def member(z, name):
    require(z.namelist().count(name) == 1, 'Missing/duplicate archive member: ' + name)
    return z.read(name)


def props(data):
    result = {}
    for line in data.decode().splitlines():
        if line and not line.startswith(('#', '!')):
            k, v = line.split('=', 1)
            require(k not in result, 'Duplicate property: ' + k)
            result[k] = v
    return result


def production_references(data):
    """Read Class constants and descriptor UTF8s without resolving additional classes."""
    require(data[:4] == b"\xca\xfe\xba\xbe", "Bad class header")
    count = struct.unpack_from(">H", data, 8)[0]
    pos, index, strings = 10, 1, []
    lengths = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4,
               11: 4, 12: 4, 15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}
    while index < count:
        tag = data[pos]
        pos += 1
        if tag == 1:
            length = struct.unpack_from(">H", data, pos)[0]
            pos += 2
            strings.append(data[pos:pos+length].decode("utf-8", errors="replace"))
            pos += length
        else:
            require(tag in lengths, "Unknown constant-pool tag")
            pos += lengths[tag]
            if tag in (5, 6):
                index += 1
        index += 1
    return sorted({x.replace("/", ".") for text in strings
                   for x in re.findall(r"cn/academy/port/[A-Za-z0-9_$/]+", text)})


def run(command, log, failure=None):
    env = dict(os.environ)
    for key in ('JAVA_TOOL_OPTIONS', 'JDK_JAVA_OPTIONS', '_JAVA_OPTIONS', 'CLASSPATH'):
        env.pop(key, None)
    result = subprocess.run(list(map(str, command)), capture_output=True, text=True, env=env)
    text = result.stdout + result.stderr
    log.parent.mkdir(parents=True, exist_ok=True)
    log.write_text(text)
    if failure:
        require(result.returncode != 0 and failure in text and 'MUTANT_SURVIVED' not in text,
                'Negative control failed for wrong reason: ' + str(log) + '\n' + text[-5000:])
    else:
        require(result.returncode == 0, 'Command failed: ' + str(log) + '\n' + text[-5000:])
    return text


def prove(args):
    root = args.project.resolve(strict=True)
    support = root / SUPPORT
    report = args.report.resolve()
    require(not report.exists(), 'Use a new report directory; old success cannot survive failure')
    report.mkdir(parents=True)
    receipts = {}
    evidence = dict(contract=CONTRACT, result='FAIL', negative_controls=[])

    def read(path):
        path = path.resolve(strict=True)
        data = path.read_bytes()
        digest = sha(data)
        require(path not in receipts or receipts[path] == digest, 'Input changed: ' + str(path))
        receipts[path] = digest
        return data

    try:
        raw = read(support / 'contract-v1.json')
        require(sha(raw) == CONTRACT_SHA256, 'Versioned current contract changed')
        contract = json.loads(raw)
        require(contract['contract'] == CONTRACT, 'Wrong contract name')
        main = (root / 'build/classes/java/main').resolve(strict=True)
        paths = {'main': main}
        for origin, detail in contract['origins'].items():
            relative = detail['path']
            paths[origin] = ((args.cache_dir / relative[6:]) if relative.startswith('cache/') else root / relative).resolve(strict=True)
        java_home = args.java_home.resolve(strict=True)
        for name, expected in contract['jdk_files'].items():
            require(sha(read(java_home / name)) == expected, 'Wrong pinned JDK: ' + name)
        java, javac = java_home / 'bin/java', java_home / 'bin/javac'
        for name, expected in contract['test_sources'].items():
            require(sha(read(support / name)) == expected, 'Reviewed companion/host source changed: ' + name)
        for name, expected in contract['production_sources'].items():
            require(sha(read(root / name)) == expected, 'Current production source changed: ' + name)
        require(sha(read(root / 'scripts/highpower-geometry-audit-api-identity.py')) == contract['audit_sha256'], 'Changed original-ZIP trust-root audit')
        historical = json.loads(run([sys.executable, root / 'scripts/highpower-geometry-audit-api-identity.py',
                                    '--project', root, '--original-source', args.original_source,
                                    '--joml-jar', paths['joml-1.10.5.jar']], report / 'historical-api-identity.json'))
        require(historical['class_identity_result'] == 'PASS' and historical['class_count'] == 11, 'Frozen API identity failed')
        require(historical['existing_whole_archive_guard'] == 'FAIL', 'Unexpected historical archive status')
        read(root / 'scripts/highpower-geometry-audit-api-identity.py'); read(args.original_source)
        for camera, name in [('charging', 'Charging'), ('meltdowner', 'Meltdowner'), ('thunderbolt', 'ThunderBolt')]:
            read(root / f'src/test/java/cn/academy/port/client/Classic{name}GeometryClassResourcesTest.java')
            read(root / f'gradle/classic-{camera}-geometry-tests.gradle')
            for p in (root / f'src/test/resources/classic-oracles/{camera}-camera').rglob('*'):
                if p.is_file():
                    read(p)
        guards = props(read(root / 'src/test/resources/classic-oracles/meltdowner-camera/guards.properties'))
        require(guards['near.sha256'] == GOLDEN, 'Frozen near digest changed')
        for i in range(int(guards['protected.logic.count'])):
            require(sha(read(root / guards[f'protected.logic.{i}.path'])) == guards[f'protected.logic.{i}.sha256'], 'Protected ability logic changed')
        for name, expected in contract['assets'].items():
            for directory in ('src/main/resources', 'build/resources/main'):
                require(sha(read(root / directory / name)) == expected, 'Changed current/original material asset: ' + name)
        subjects = {n for n, d in contract['classes'].items() if d['origin'] == 'main'}
        require(len(subjects) == 69, 'Expected explicit 69-class production graph')
        families = {n.split('$')[0] for n in subjects}
        inventory = {p.relative_to(main).as_posix()[:-6].replace('/', '.') for p in main.rglob('*.class')
                     if p.relative_to(main).as_posix()[:-6].replace('/', '.').split('$')[0] in families}
        require(inventory == subjects, 'Unknown/missing current production class')
        archives = {o: zipfile.ZipFile(p) for o, p in paths.items() if o != 'main'}
        for o in archives:
            read(paths[o])
        frozen_api = {x['class']: x['sha256'] for x in historical['classes']}
        bindings = []
        for name, d in contract['classes'].items():
            origin, expected, item = d['origin'], d['sha256'], d['member']
            data = read(main / item) if origin == 'main' else member(archives[origin], item)
            require(sha(data) == expected, 'Changed admitted class: ' + name)
            if origin == 'main':
                require(production_references(data) == contract['production_graph'][name], 'Changed production dependency edges: ' + name)
            if name in frozen_api:
                require(expected == frozen_api[name] and d['anchor'] == 'original-m41-api', 'Changed original API anchor: ' + name)
            matching = [o for o, z in archives.items() if name.replace('.', '/') + '.class' in z.namelist()]
            if (main / (name.replace('.', '/') + '.class')).is_file():
                matching.append('main')
            require(matching == [origin], 'Duplicate/incorrect dependency origin: ' + name + str(matching))
            bindings.append('\t'.join(('class', name, str(paths[origin]), expected, item)))
        require(frozen_api.keys() <= contract['classes'].keys(), 'Missing original API definition')
        for d in contract['resources']:
            require(sha(member(archives[d['origin']], d['name'])) == d['sha256'], 'Changed declared resource: ' + d['name'])
            bindings.append('\t'.join(('resource', d['name'], str(paths[d['origin']]), d['sha256'], d['name'])))
        for name in contract['optional_absent_classes']:
            require(not any(name.replace('.', '/') + '.class' in z.namelist() for z in archives.values()), 'Unexpected optional dependency')
            bindings.append('absent\t' + name)
        for z in archives.values():
            z.close()
        with tempfile.TemporaryDirectory(prefix='current-meltdowner-proof-') as temp_name:
            temp = Path(temp_name)
            fixtures, drivers = temp / 'fixtures', temp / 'drivers'
            fixtures.mkdir(); drivers.mkdir()
            cp = os.pathsep.join(map(str, [main] + [p for o, p in paths.items() if o != 'main']))
            sources = [support / n for n in contract['test_sources'] if n.endswith('.java') and n not in ('CurrentMeltdownerGeometryProof.java', 'MutateMeltdowner.java')]
            run([javac, '-proc:none', '-implicit:none', '-sourcepath', '', '-encoding', 'UTF-8', '-cp', cp, '-d', fixtures, *sources], report / 'compile-fixtures.log')
            output = {p.relative_to(fixtures).as_posix() for p in fixtures.rglob('*.class')}
            require(output == set(contract['fixture_classes']), 'Unknown/missing compiled fixture class')
            for item in sorted(output):
                name = item[:-6].replace('/', '.')
                require(name not in contract['classes'], 'Host overlaps admitted actual class: ' + name)
                bindings.append('\t'.join(('class', name, str(fixtures), sha((fixtures / item).read_bytes()), item)))
            run([javac, '-proc:none', '-encoding', 'UTF-8', '-d', drivers, support / 'CurrentMeltdownerGeometryProof.java'], report / 'compile-runner.log')
            logging=read(support / 'log4j2.xml');(fixtures / 'log4j2.xml').write_bytes(logging)
            bindings.append('\t'.join(('resource','log4j2.xml',str(fixtures),sha(logging),'log4j2.xml')))
            pins = temp / 'pins.tsv'; pins.write_text('\n'.join(bindings) + '\n')
            command = [java, '-ea', '-Xmx512m', '-Djava.awt.headless=true', '-Dacademy.vfx2.lightShieldMaterialPreview=false', '-cp', drivers, 'CurrentMeltdownerGeometryProof']

            def execute(label, rows, lane='historical', mode='ACTUAL_MAIN', failure=None, probe='-', pinfile=pins):
                admission = temp / (label + '.tsv'); admission.write_text('\n'.join(rows) + '\n')
                log = run([*command, admission, pinfile, main, fixtures, report / label, lane, mode, probe], report / label / 'execution.log', failure)
                if failure:
                    evidence['negative_controls'].append(dict(name=label, result='DETECTED', expected_failure=failure))
                return log

            evidence['lanes'] = {}
            for lane in ('historical', 'off', 'forced', 'on'):
                log = execute('actual-' + lane, bindings, lane)
                evidence['lanes'][lane] = dict(result='PASS', summary=[s for s in log.splitlines() if s.startswith(('MeltdownerCurrentMainGeometryHarness:', 'CURRENT_PATH_PASS', 'CURRENT_PATH_MATRIX'))],
                                               loaded_classes=len((report / ('actual-' + lane) / 'loaded-classes.tsv').read_text().splitlines()))
            tool = contract['mutation_tool']; asm = (args.cache_dir / tool['path']).resolve(strict=True)
            require(sha(read(asm)) == tool['sha256'], 'Wrong mutation-tool archive')
            run([javac, '-proc:none', '-cp', asm, '-d', drivers, support / 'MutateMeltdowner.java'], report / 'compile-mutator.log')
            for mutation, lane, expected in [('coordinate', 'historical', 'camera motion boundary was not detected/restored: family=2'), ('uv', 'historical', 'NEAR_GOLDEN_MISMATCH'),
                                              ('rng', 'historical', 'NEAR_GOLDEN_MISMATCH'), ('camera', 'historical', 'camera motion boundary was not detected/restored: family=2'),
                                              ('scope', 'on', 'MATERIAL_SCOPE'), ('state', 'on', 'MATERIAL_STATE_EXACT')]:
                subject = MATERIAL if mutation == 'state' else SUBJECT
                relative = subject.replace('.', '/') + '.class'; mutant_root = temp / ('mutation-' + mutation); target = mutant_root / relative
                run([java, '-Xmx256m', '-cp', os.pathsep.join(map(str, (drivers, asm))), 'MutateMeltdowner', main / relative, target, mutation], report / ('mutate-' + mutation + '.log'))
                changed = ['\t'.join(('class', subject, str(mutant_root), sha(target.read_bytes()), relative)) if row.startswith('class\t' + subject + '\t') else row for row in bindings]
                execute('mutation-' + mutation, changed, lane, 'NEGATIVE_CONTROL:' + mutation, expected)
                if mutation in ('coordinate','uv','rng','camera'):
                    observed=props((report/('mutation-'+mutation)/'matrix.properties').read_bytes())
                    require(observed['cases']=='2640' and observed['nearVertices']=='3280176' and observed['coordinates']=='39362112','Behavioral mutant did not execute the complete original matrix')
                    require((observed['nearSnapshot']==GOLDEN)==(mutation=='camera'),'Wrong actual mutant near-geometry effect')
                    if mutation=='camera':require(int(observed['mismatches'])>0,'Camera mutation did not break far geometry')
                    evidence['negative_controls'][-1].update(complete_matrix=True,near_sha256=observed['nearSnapshot'],far_coordinate_mismatches=int(observed['mismatches']))

            host='net.minecraft.client.Minecraft';relative=host.replace('.', '/')+'.class';mutant_root=temp/'mutation-host-signature';target=mutant_root/relative
            run([java,'-Xmx256m','-cp',os.pathsep.join(map(str,(drivers,asm))),'MutateMeltdowner',fixtures/relative,target,'host-signature'],report/'mutate-host-signature.log')
            changed=['\t'.join(('class',host,str(mutant_root),sha(target.read_bytes()),relative)) if row.startswith('class\t'+host+'\t') else row for row in bindings]
            execute('mutation-host-signature',changed,'historical','NEGATIVE_CONTROL:host-signature',"NoSuchMethodError: 'net.minecraft.client.multiplayer.ClientPacketListener net.minecraft.client.Minecraft.getConnection()'")
            api='net.minecraft.world.phys.Vec3';relative=api.replace('.', '/')+'.class';false_root=temp/'wrong-api-origin';target=false_root/relative;target.parent.mkdir(parents=True)
            with zipfile.ZipFile(paths['neoforge-21.1.252-merged.jar']) as z:target.write_bytes(member(z,relative))
            changed=['\t'.join(('class',api,str(false_root),sha(target.read_bytes()),relative)) if row.startswith('class\t'+api+'\t') else row for row in bindings]
            execute('wrong-api-origin',changed,failure='WRONG_DEPENDENCY_ORIGIN '+api)
            first = next(row for row in bindings if row.startswith('class\t' + SUBJECT + '\t'))
            execute('duplicate-subject', bindings + [first], failure='DUPLICATE_CLASS_ADMISSION')
            poison = ['\t'.join(row.split('\t')[:3] + ['0' * 64] + row.split('\t')[4:]) if row == first else row for row in bindings]
            execute('stale-pin', poison, failure='WRONG_DEPENDENCY_PIN')
            missing = [row for row in bindings if not row.startswith('class\tcn.academy.port.client.vfx2.MdNearEyeVisibility$Owner\t')]
            execute('missing-owner', missing, failure='UNKNOWN_OR_MISSING_DEPENDENCY')
            execute('unadmitted-production', bindings, probe='cn.academy.port.client.AcademyClient', failure='UNKNOWN_DEPENDENCY cn.academy.port.client.AcademyClient')
        for p, expected in receipts.items():
            require(sha(p.read_bytes()) == expected, 'Input changed before completion: ' + str(p))
        evidence.update(result='PASS_CURRENT_CONTRACT_ONLY', production_classes=69, original_api_classes=11,
                        historical_archive_admission='FAIL_UNCHANGED', historical_near_sha256=GOLDEN,
                        current_contract_sha256=sha(raw), input_stability='ALL_READ_INPUTS_UNCHANGED',
                        limits=['Historical tasks and whole-archive guards are unchanged and still fail; this is a separate current contract.',
                                'Native CPU math and RenderType declarations execute; game/session/resource/shader/GL host behavior is explicitly modelled.',
                                'The event lane starts from seeded owned renderer state; no AcademyClient or packet acceptance graph is substituted or claimed.',
                                'Production DrawState setup/clear executes with recording RenderType delegates as a separately labelled component contract.',
                                'No native GPU, pixels, native client, audio output, full gameplay, or performance acceptance is established.'])
        (report / 'input-receipts.sha256').write_text(''.join(f'{v}  {k}\n' for k, v in sorted(receipts.items())))
        (report / 'evidence.json').write_text(json.dumps(evidence, indent=2, sort_keys=True) + '\n')
        (report / 'PASS_CURRENT_CONTRACT_ONLY').write_text(CONTRACT + '\n')
        return evidence
    except Exception as e:
        evidence['error'] = str(e)
        (report / 'failure.json').write_text(json.dumps(evidence, indent=2, sort_keys=True) + '\n')
        raise


def main():
    root = Path(__file__).resolve().parents[1]; workspace = root.parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--project', type=Path, default=root)
    parser.add_argument('--original-source', type=Path, default=workspace / 'aca-original-m41/academycraft-m41-meltdowner-camera-source.zip')
    parser.add_argument('--java-home', type=Path, default=workspace / 'aca-tools/jdk-21.0.12.1+1')
    parser.add_argument('--cache-dir', type=Path, default=workspace / 'aca-gradle-cache/caches')
    parser.add_argument('--report', type=Path, required=True)
    try:
        print(json.dumps(prove(parser.parse_args()), indent=2, sort_keys=True)); return 0
    except Exception as e:
        print(json.dumps(dict(contract=CONTRACT, result='FAIL', error=str(e)), indent=2)); return 1


if __name__ == '__main__':
    sys.exit(main())
