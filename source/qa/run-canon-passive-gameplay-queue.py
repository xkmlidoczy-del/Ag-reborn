#!/usr/bin/env python3
"""Run the7 actual passive gameplay scenarios serially in one authorized JVM slot.

Do not launch while another owner uses the same slot. No Gradle/source/resource
mutation. Each real hidden JVM gets a unique gameDir and preserves raw failures.
"""
import argparse,datetime,hashlib,json,subprocess,sys,uuid
from pathlib import Path
S=Path(__file__).resolve().parents[1];R=S.parent;E=R/'qa/evidence/2026-10-30-canon-finalization';PYTHON=R/'.venv-qa/bin/python3'
def dump(p,x):p.write_text(json.dumps(x,ensure_ascii=False,indent=2)+'\n')
def now():return datetime.datetime.now(datetime.timezone.utc).isoformat()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--plan',type=Path,default=E/'passive-gameplay-scripts/plan.json');p.add_argument('--only',default='');p.add_argument('--timeout',type=int,default=1500);p.add_argument('--out',type=Path);a=p.parse_args();plan=json.loads(a.plan.read_text());cases=[x for x in plan['scenarios'] if not a.only or x['scenario'] in a.only.split(',')];out=a.out or E/'queues'/('passive-gameplay-'+uuid.uuid4().hex[:10]);out.mkdir(parents=True,exist_ok=False)
 dump(out/'request.json',{'createdUtc':now(),'plan':str(a.plan.resolve()),'planSha256':hashlib.sha256(a.plan.read_bytes()).hexdigest(),'cases':cases,'serialOnly':True,'maxOwnedGameJvms':1,'changesProduction':False})
 print(out,flush=True);results=[]
 for index,c in enumerate(cases,1):
  name=c['scenario'];script=Path(c['script']);assert hashlib.sha256(script.read_bytes()).hexdigest()==c['scriptSha256']
  dump(out/'progress.json',{'updatedUtc':now(),'stage':'native_hidden_run','index':index,'total':len(cases),'scenario':name,'completed':results})
  log=out/(name+'-runner.log');command=[str(PYTHON),str(S/'qa/run-canon-complete.py'),'passive-gameplay-'+name,str(script),'--timeout',str(a.timeout)]
  with log.open('w') as f:r=subprocess.run(command,cwd=S,stdout=f,stderr=subprocess.STDOUT)
  (out/(name+'-runner.exit')).write_text(str(r.returncode)+'\n')
  paths=[Path(x.strip()) for x in log.read_text(errors='replace').splitlines() if x.startswith(str(E/'runs')+'/')];run=paths[0] if len(paths)==1 else None;check=out/(name+'-check.json');checker_exit=None
  if run is not None and (run/'result.json').is_file():
   with (out/(name+'-checker.log')).open('w') as f:q=subprocess.run([str(PYTHON),str(S/'qa/check-canon-passive-gameplay.py'),'--run',str(run),'--scenario',name,'--out',str(check)],cwd=S,stdout=f,stderr=subprocess.STDOUT)
   checker_exit=q.returncode
  result={'scenario':name,'catalogKeys':c['catalogKeys'],'nativeExit':r.returncode,'run':str(run) if run else None,'check':str(check) if check.exists() else None,'checkerExit':checker_exit,'pass':r.returncode==0 and checker_exit==0};results.append(result)
  dump(out/'progress.json',{'updatedUtc':now(),'stage':'between_native_runs','index':index,'total':len(cases),'completed':results});print(json.dumps(result,ensure_ascii=False),flush=True)
 summary={'createdUtc':now(),'pass':all(x['pass'] for x in results) and len(results)==len(cases),'scenarioCount':len(cases),'completed':results,'rawFailuresPreserved':True,'uniqueCatalogKeysRequested':sorted({k for x in cases for k in x['catalogKeys']}),'scope':'Real native learning and player-tick/attack mechanisms; source/code unchanged, serial hidden clients; no preview substitution.'};dump(out/'summary.json',summary);print('queue_complete',summary['pass'],flush=True);raise SystemExit(0 if summary['pass'] else 1)
if __name__=='__main__':main()
