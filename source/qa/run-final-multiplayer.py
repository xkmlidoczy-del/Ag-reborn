#!/usr/bin/env python3
"""Run a prepared final8-family, liquidmodule, or15minsoak suite; alwayshidden."""
import argparse,json,re,subprocess,sys,uuid
from pathlib import Path
S=Path(__file__).resolve().parents[1];E=S.parent/'qa/evidence/2026-10-30-canon-finalization'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('suite',choices=['eight','liquid','soak','allies']);p.add_argument('--output-base',type=Path,default=E/'multiplayer');p.add_argument('--scripts',type=Path,default=E/'scripts');p.add_argument('--float-binding-diagnostic',action='store_true');a=p.parse_args()
a.output_base.mkdir(parents=True,exist_ok=True);key=a.suite+'-'+uuid.uuid4().hex[:8];log=a.output_base/(key+'-launcher.log')
args=[sys.executable,str(S/'qa/run-canon-formal-dual.py'),'--host-script',str(a.scripts/f'multiplayer-{a.suite}-host.script'),'--observer-script',str(a.scripts/f'multiplayer-{a.suite}-observer.script'),'--output-base',str(a.output_base/a.suite),'--fps','30','--timeout','3000','--capture-all-server-vfx']
if a.float_binding_diagnostic:args+=['--float-binding-diagnostic']
if a.suite=='liquid':args+=['--scenario','liquid-module','--skill','liquid_scout','--phase','START']
else:
 args+=['--scenario','visual','--skill','dark_wings','--phase','START']
 if a.suite=='allies':
  requirements=[('nitrogen_armor','START'),('dark_blade','HIT')]+[(c['skill'],c['expectedPhase']) for c in json.loads((a.scripts/'multiplayer-allies-plan.json').read_text())['cases']]
 else:requirements= [('nitrogen_armor','START'),('dark_blade','HIT'),('mental_probe','HIT'),('aero_jet','HIT'),('hydro_pressure_jet','HIT'),('float_lift','START'),('paper_plane','START'),('liquid_scout','START')]
 for skill,phase in requirements:args+=['--require-visual',skill+':'+phase]
print('native suite',a.suite,'log',log,flush=True)
with log.open('w') as stream:process=subprocess.run(args,cwd=S,stdout=stream,stderr=subprocess.STDOUT)
text=log.read_text(errors='replace');m=re.search(r'isolated run: (.+)',text);out=Path(m.group(1).strip()) if m else None
status={'suite':a.suite,'nativeExit':process.returncode,'run':str(out) if out else None,'launcherLog':str(log),'pass':False}
if process.returncode==0 and out:
 report=out/'final-native-check.json'
 check=subprocess.run([sys.executable,str(S/'qa/check-final-multiplayer.py'),'--run',str(out),'--suite',a.suite,'--plan',str(a.scripts/'multiplayer-plan.json'),'--out',str(report)],cwd=S)
 status['checkExit']=check.returncode;status['report']=str(report);status['pass']=check.returncode==0
(a.output_base/(key+'-result.json')).write_text(json.dumps(status,ensure_ascii=False,indent=2)+'\n');print(json.dumps(status,ensure_ascii=False),flush=True);raise SystemExit(0 if status['pass'] else 1)
