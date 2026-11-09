#!/usr/bin/env python3
"""Run an explicit, serial plan against one prepared build; retain every result."""
import argparse
import json
import pathlib
import subprocess
import sys

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('plan', type=pathlib.Path)
p.add_argument('--directory', default='run-showcase-sable')
p.add_argument('--stop-on-failure', action='store_true', help='Stop after preserving the first failed preflight; remaining cases are not claimed run')
a = p.parse_args()
source = pathlib.Path(__file__).resolve().parents[1]
plan = a.plan.resolve()
cases = json.loads(plan.read_text())
out = source.parent / 'qa/evidence/2026-10-22-full-gameplay'
results_path = plan.with_name(plan.stem.removesuffix('-plan') + '-results.json')
if results_path.exists():
    raise SystemExit('Use a new plan name; existing evidence will not be overwritten')
names = [c['name'] for c in cases]
if len(names) != len(set(names)):
    raise SystemExit('Duplicate case names')
for c in cases:
    if (out / (c['name'] + '.log')).exists():
        raise SystemExit('Existing case log: ' + c['name'])
    if not (source / 'qa' / c['script']).is_file():
        raise SystemExit('Missing script: ' + c['script'])
results = []
for c in cases:
    script = (source / 'qa' / c['script']).read_text().strip()
    command = [sys.executable, 'qa/run-prepared-client.py', c['name'],
               '--directory', c.get('directory', a.directory),
               '--props', c.get('props', ''), '--script', script]
    result = subprocess.run(command, cwd=source)
    results.append({'name': c['name'], 'script': c['script'], 'exit': result.returncode})
    results_path.write_text(json.dumps(results, ensure_ascii=False, indent=2) + '\n')
    print(c['name'], result.returncode, flush=True)
    if a.stop_on_failure and result.returncode != 0:
        break
raise SystemExit(0 if all(r['exit'] == 0 for r in results) else 1)
