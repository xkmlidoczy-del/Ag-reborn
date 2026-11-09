#!/usr/bin/env python3
"""Freshhidden2clientPvP Groundingnative absorption duel; no direct damage/cast."""
import argparse,json,re,subprocess,sys,uuid
from pathlib import Path
S=Path(__file__).resolve().parents[1];E=S.parent/'qa/evidence/2026-10-30-canon-finalization'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output-base',type=Path,default=E/'multiplayer/grounding');p.add_argument('--scripts',type=Path,default=E/'scripts');a=p.parse_args();a.output_base.mkdir(parents=True,exist_ok=True)
key=uuid.uuid4().hex[:12];log=a.output_base/('launcher-'+key+'.log')
args=[sys.executable,'qa/run-canon-formal-dual.py','--scenario','grounding','--skill','grounding_return','--host-script',str(a.scripts/'multiplayer-grounding-host.script'),'--observer-script',str(a.scripts/'multiplayer-grounding-observer.script'),'--host-name','GroundHost','--observer-name','ArcGuest','--fps','30','--timeout','240','--output-base',str(a.output_base)]
print('GroundingrealPvPduel',log,flush=True)
with log.open('w') as f:r=subprocess.run(args,cwd=S,stdout=f,stderr=subprocess.STDOUT)
m=re.search('isolated run: (.+)',log.read_text(errors='replace'));out=Path(m.group(1).strip()) if m else None
result={'pass':False,'nativeExit':r.returncode,'run':str(out) if out else None,'launcherLog':str(log)}
if out and (out/'host-grounding-receipts.jsonl').exists() and (out/'observer-grounding-receipts.jsonl').exists():
 report=out/'grounding-result.json';check=subprocess.run([sys.executable,'qa/check-grounding-duel.py','--run',str(out),'--out',str(report)],cwd=S)
 result['mechanismCheckExit']=check.returncode;result['report']=str(report);result['pass']=r.returncode==0 and check.returncode==0
(a.output_base/('result-'+key+'.json')).write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True);raise SystemExit(0 if result['pass'] else 1)
