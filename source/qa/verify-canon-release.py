#!/usr/bin/env python3
"""Verify a local candidate against compiled outputs and a real client snapshot."""
import argparse
import hashlib
import json
from pathlib import Path
from zipfile import ZipFile

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('jar', type=Path)
p.add_argument('snapshot', type=Path)
p.add_argument('report', type=Path)
p.add_argument('--source-inputs', type=Path)
a = p.parse_args()
source = Path(__file__).resolve().parents[1]
snapshot = json.loads(a.snapshot.read_text())
snapshot = snapshot.get('files', snapshot)
excluded = ('cn/academy/port/qa/', 'cn/academy/port/gametest/')
excluded_classes = ('cn/academy/port/client/MagneticDomainQaFixture',
                    'cn/academy/port/highpower/ZeroGapArmorQaFixture',
                    'cn/academy/port/highpower/VectorParryQaObserver')
checks = []

def require(ok, detail):
    checks.append({'pass': bool(ok), 'detail': detail})

with ZipFile(a.jar) as jar:
    names = set(jar.namelist())
    prefixes = ('classes/java/main/', 'resources/main/')
    expected = {name[len(prefix):] for name in snapshot for prefix in prefixes
                if name.startswith(prefix) and not name[len(prefix):].startswith(excluded + excluded_classes)}
    actual_files = {name for name in names if not name.endswith('/')}
    require(actual_files == expected | {'META-INF/MANIFEST.MF'},
            'exact production artifact inventory; missing='+str(sorted(expected-actual_files))+
            '; unexpected='+str(sorted(actual_files-expected-{'META-INF/MANIFEST.MF'})))
    require(jar.testzip() is None, 'all ZIP entries pass CRC')
    require(not any(n.startswith(excluded) for n in names), 'no QA or GameTest classes shipped')
    require(not any(n.startswith(excluded_classes) for n in names), 'no opt-in evidence fixtures shipped')
    require(not any(part.startswith('._') or part == '.DS_Store' for n in names for part in n.split('/')), 'no AppleDouble metadata shipped')
    for folder, prefix in [(source/'build/classes/java/main', 'classes/java/main/'),
                           (source/'build/resources/main', 'resources/main/')]:
        for file in sorted(folder.rglob('*')):
            if not file.is_file() or any(part.startswith('._') or part == '.DS_Store' for part in file.parts):
                continue
            name = str(file.relative_to(folder))
            if name.startswith(excluded) or name.startswith(excluded_classes):continue
            data = file.read_bytes()
            require(name in names and jar.read(name) == data, 'compiled output matches JAR: '+name)
            require(snapshot.get(prefix+name) == hashlib.sha256(data).hexdigest(), 'client snapshot matches: '+name)
    import re
    version = re.search(r"^version = '([^']+)'", (source/'build.gradle').read_text(), re.M).group(1)
    require(('version="'+version+'"') in jar.read('META-INF/neoforge.mods.toml').decode(), 'candidate version matches build.gradle: '+version)
    require('academy.canon-pose.mixins.json' in names, 'independent pose mixin is packaged')
    for category in ('mental_out', 'aero_hand', 'nitrogen_armor', 'dark_matter', 'hydro_hand', 'float_dial', 'paper_psychokinesis', 'liquid_shadow'):
        icons = [n for n in names if n.startswith('assets/academy/textures/skill_icons_v2/'+category+'/') and n.endswith('.png')]
        require(len(icons) >= 24, 'at least 24 packaged skill icons: '+category+' has '+str(len(icons)))

if a.source_inputs:
    frozen_inputs = json.loads(a.source_inputs.read_text())
    current_inputs = {str(file.relative_to(source)): hashlib.sha256(file.read_bytes()).hexdigest()
                      for file in sorted((source/'src/main').rglob('*')) if file.is_file()
                      and not any(part.startswith('._') or part == '.DS_Store' for part in file.parts)}
    require(current_inputs == frozen_inputs, 'all final production source/resource inputs still match frozen manifest')

report = {'pass': all(row['pass'] for row in checks), 'checks': len(checks),
          'failures': [row['detail'] for row in checks if not row['pass']],
          'jar': str(a.jar.resolve()), 'snapshot': str(a.snapshot.resolve()),
          'sourceInputs': str(a.source_inputs.resolve()) if a.source_inputs else None,
          'sha256': hashlib.sha256(a.jar.read_bytes()).hexdigest(), 'size': a.jar.stat().st_size}
a.report.write_text(json.dumps(report, ensure_ascii=False, indent=2)+'\n')
print(json.dumps(report, ensure_ascii=False))
raise SystemExit(0 if report['pass'] else 1)
