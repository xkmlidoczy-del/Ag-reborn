#!/usr/bin/env python3
"""Rebuild/check QA-only grouped scripts. Preserves every original step except intermediate quit."""
import argparse
import hashlib
import json
import pathlib

args=argparse.ArgumentParser()
args.add_argument('--check',action='store_true')
check=args.parse_args().check
base=pathlib.Path(__file__).resolve().parent
plan=json.loads((base/'gameplay-final-modes-merged-plan.json').read_text())
original=json.loads((base/'gameplay-final-modes-plan.json').read_text())
sources=[name for case in plan for name in case['sourceScripts']]
assert len(sources)==51 and len(set(sources))==51
assert set(sources)=={case['script'] for case in original}
manifest=[]
for case in plan:
    output=[]
    for name in case['sourceScripts']:
        raw=(base/name).read_text()
        steps=raw.strip().split('|')
        assert steps[-1]=='quit' and steps.count('quit')==1,name
        assert all(step and step==step.strip() for step in steps),name
        marker=pathlib.Path(name).stem
        output+=['mark:begin:'+marker]+steps[:-1]+['mark:end:'+marker]
        manifest.append({'group':case['name'].removeprefix('final96-modegroup-'),'source':name,
                         'sha256':hashlib.sha256(raw.encode()).hexdigest(),
                         'retainedOriginalSteps':len(steps)-1,'removedSteps':['quit']})
    expected='|'.join(output+['quit'])+'\n'
    target=base/case['script']
    if check:
        assert target.read_text()==expected,'STALE grouped script: '+target.name
    else:
        target.write_text(expected)
target=base/'gameplay-final-modes-merge-manifest.json'
expected=json.dumps(manifest,ensure_ascii=False,indent=2)+'\n'
if check:
    assert target.read_text()==expected,'STALE source fingerprint manifest'
else:
    target.write_text(expected)
print(('CHECK' if check else 'GENERATED')+' 7 groups, 51 scenarios, '+str(sum(row['retainedOriginalSteps'] for row in manifest))+' exact original non-quit steps; no gameplay launch')
