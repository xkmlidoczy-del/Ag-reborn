#!/usr/bin/env python3
"""One owned game JVM at a time, frozen outputs; keep every failed native attempt."""
import argparse,hashlib,json,subprocess,sys,uuid
from pathlib import Path
S=Path(__file__).resolve().parents[1];E=S.parent/'qa/evidence/2026-10-30-canon-finalization'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--name',required=True);p.add_argument('--plan',type=Path,required=True);a=p.parse_args()
plan=json.loads(a.plan.read_text());out=E/'queues'/(a.name+'-'+uuid.uuid4().hex[:10]);out.mkdir(parents=True)
(out/'plan.json').write_text(json.dumps(plan,indent=2)+'\n');print(out,flush=True)
rows=[]
for i,row in enumerate(plan['batches']):
 if (out/'PAUSE_AFTER_CURRENT').exists():break
 script=Path(row['script']);manifest=Path(row['manifest']);code=None;receipt=None
 state={'current':row['name'],'index':i,'total':len(plan['batches']),'scriptSha256':hashlib.sha256(script.read_bytes()).hexdigest(),'results':rows}
 (out/'state.json').write_text(json.dumps(state,indent=2)+'\n')
 args=[sys.executable,str(S/'qa/run-canon-complete.py'),a.name+'-'+row['name'],str(script),'--timeout','2400']
 if row.get('profile')=='sable':args+=['--mods-dir',str(S/'run-showcase-sable/mods')]
 if row.get('profile')=='iris':args+=['--mods-dir',str(E/'compat/mods-stable'),'--shaderpack',str(E/'compat/downloads/ComplementaryReimagined_r5.9.3.zip'),'--iris-reload-key','f6']
 for option in ['camera','shaders']:
  if option in row:args+=['--'+option,row[option]]
 if row.get('beamStepDiagnostic'):args+=['--beam-step-diagnostic']
 if row.get('arrayDiagnostic'):args+=['--array-diagnostic']
 if row.get('emergencyReturnDiagnostic'):args+=['--emergency-return-diagnostic']
 log=out/(row['name']+'.log')
 with log.open('w') as f:r=subprocess.run(args,cwd=S,stdout=f,stderr=subprocess.STDOUT)
 text=log.read_text(errors='replace');paths=[Path(l.strip()) for l in text.splitlines() if l.strip().startswith(str(E/'runs'))]
 run=next((x for x in paths if x.is_dir()),None)
 report=out/(row['name']+'-receipt-check.json')
 if run and (run/'result.json').exists():
  cmd=[sys.executable,str(S/'qa/check-fx4-rollout-receipts.py'),'--run',str(run),'--manifest',str(manifest),'--out',str(report),'--allow-lifecycle','--require-paid-new']
  with (out/(row['name']+'-checker.log')).open('w') as f:checked=subprocess.run(cmd,cwd=S,stdout=f,stderr=subprocess.STDOUT)
  receipt=json.loads(report.read_text()) if report.exists() else None
 result={'name':row['name'],'script':str(script),'manifest':str(manifest),'profile':row.get('profile','standard'),'nativeExit':r.returncode,'run':str(run) if run else None,'strictReport':str(report) if report.exists() else None,'strictPass':receipt.get('pass') if receipt else False,'realConfirmed':receipt.get('realConfirmed',0) if receipt else 0,'rejections':receipt.get('rejectedOrUnconfirmed',[]) if receipt else []}
 rows.append(result);(out/'results.json').write_text(json.dumps(rows,indent=2)+'\n');print(json.dumps(result,ensure_ascii=False),flush=True)
(out/'state.json').write_text(json.dumps({'finished':len(rows)==len(plan['batches']),'paused':(out/'PAUSE_AFTER_CURRENT').exists(),'results':rows},indent=2)+'\n')
print('queue ended',len(rows),'of',len(plan['batches']),flush=True)
