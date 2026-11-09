#!/usr/bin/env python3
"""Select finished nonfatal evidence; preserve failures without salvaging fatal batches."""
import argparse
import importlib.util
import json
import subprocess
import sys
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--out',type=Path,required=True)
p.add_argument('--rehash-frames',action='store_true')
p.add_argument('--complete',action='store_true')
p.add_argument('--reference-snapshot',type=Path)
p.add_argument('--final-patch-policy',type=Path)
a=p.parse_args();s=Path(__file__).resolve().parents[1]
e=s.parent/'qa/evidence/2026-10-30-canon-finalization'
load=lambda path:json.loads(path.read_text())
spec=importlib.util.spec_from_file_location('matrix',s/'qa/check-canon-complete-matrix.py')
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
reference=a.reference_snapshot or e/'fifth-freeze-compiled-snapshot.json'
motion=e/'provenance-policy-motion-v1.approved.json'
render=e/'provenance-policy-dark-render-v1.approved.json'
ref=m.production_snapshot(reference)
policy=m.load_policies(motion,render,ref,a.final_patch_policy)
selected={};eligible=set();quarantine=[]
for file in sorted((e/'queues').glob('*/results.json'),key=lambda x:x.stat().st_mtime):
 rows=load(file)
 if not isinstance(rows,list):continue
 for row in rows:
  if not row.get('strictReport') or row.get('profile')=='iris' or row.get('name','').startswith('representative'):continue
  report=Path(row['strictReport']);r=load(report)
  if row.get('nativeExit')!=0 or r.get('rawRecorderPass') is not True or r.get('errors'):
   quarantine.append({'queueRow':row,'reason':'Fatal, incomplete, or run-global integrity failed; zero partial salvage.'});continue
  run=Path(r['run'])
  changed=[name for name,field in [('script.txt','scriptSha256'),('fx4-receipts.jsonl','receiptSha256'),('result.json','resultSha256')]
           if not(run/name).is_file() or m.sha(run/name)!=r.get('sourceEvidence',{}).get(field)]
  changed += [x['path'] for x in r.get('manifests',[]) if not Path(x['path']).is_file() or m.sha(Path(x['path']))!=x['sha256']]
  if changed:
   quarantine.append({'queueRow':row,'reason':'Original strict evidence changed after its check; excluded without replacing/blessing hashes.','changed':changed});continue
  for clip in r.get('clips',[]):
   if clip.get('mode')!='real' or not clip.get('pass') or clip.get('confirmedRealCast') is not True:continue
   if m.provenance(run,ref,clip['category'],policy)['currentOwningProductionCompatible']:
    eligible.add(report)
    selected[clip['category']+':'+clip['skill']]=report
reports=sorted(eligible,key=lambda x:x.stat().st_mtime)
command=[sys.executable,str(s/'qa/check-canon-complete-matrix.py'),'--reference-snapshot',str(reference),
         '--provenance-policy',str(motion),'--render-provenance-policy',str(render),
         '--require-visual-review','--require-passive-gameplay','--out',str(a.out.resolve())]
if not a.complete:command+=['--allow-incomplete']
if a.final_patch_policy:command+=['--final-patch-provenance-policy',str(a.final_patch_policy)]
if a.rehash_frames:command+=['--rehash-frames']
for report in reports:command+=['--native-report',str(report)]
for file in sorted((e/'queues/passive-gameplay-fourth-freeze').glob('*-check.json')):
 if file.name.startswith('._'):continue
 r=load(file)
 if r.get('pass') and r.get('records'):command+=['--passive-report',str(file)]
grounding=e/'multiplayer/grounding/canon-formal-4fe8fce40b8e4af1bd099f62f8145787/grounding-result.json'
if grounding.is_file() and load(grounding).get('pass'):command+=['--active-mechanism-report',str(grounding)]
for file in sorted((e/'multiplayer/allies').glob('*/final-native-check.json')):
 r=load(file);run=file.parent
 if r.get('pass') and (run/'result.json').is_file() and load(run/'result.json').get('status')=='PASS':command+=['--formal-run',str(run)]
for file in [e/'visual-review-matrix-float.json',e/'visual-review-matrix-liquid.json',e/'visual-review-root-meltdowner.json',e/'visual-review-root-vecmanip.json',e/'visual-review-story-teleporter.json',e/'visual-review-matrix-electromaster.json',e/'visual-review-current.json']:
 if file.is_file():command+=['--visual-report',str(file)]
index={'selectedNativeReports':[str(x) for x in reports],'selectedNativeCatalogKeys':sorted(selected),
       'quarantinedFatalOrInvalidRows':quarantine,'command':command,
       'scope':'Representative shader compatibility is a separate suite. Default baseline catalog uses supported standard/Sable/actual ally profiles. No fatal batch contributes even earlier true clips.'}
a.out.with_suffix('.inputs.json').write_text(json.dumps(index,ensure_ascii=False,indent=2)+'\n')
raise SystemExit(subprocess.run(command,cwd=s).returncode)
