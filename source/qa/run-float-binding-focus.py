#!/usr/bin/env python3
"""Prepared-only hidden two-real-client Float binding diagnosis; never builds."""
import argparse,json,subprocess,sys,uuid
from pathlib import Path
S=Path(__file__).resolve().parents[1];E=S.parent/'qa/evidence/2026-10-30-canon-finalization'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--scripts',type=Path,default=E/'mp-float-binding-focus-v1');a=p.parse_args()
scripts=a.scripts;plan=json.loads((scripts/'plan.json').read_text())
args=[sys.executable,str(S/'qa/run-canon-formal-dual.py'),'--host-script',str(scripts/'float-binding-host.script'),
    '--observer-script',str(scripts/'float-binding-observer.script'),'--scenario','visual','--skill','dark_wings','--phase','START',
    '--output-base',str(E/'multiplayer/float-binding-focus'),'--fps','30','--timeout','600','--capture-all-server-vfx','--float-binding-diagnostic']
for v in plan['requiredVisuals']:args+=['--require-visual',v]
log=E/'multiplayer'/('float-binding-focus-'+uuid.uuid4().hex[:8]+'-launcher.log')
print('read-only Float binding focus launcher',log,flush=True)
with log.open('w') as stream:r=subprocess.run(args,cwd=S,stdout=stream,stderr=subprocess.STDOUT)
print('exit',r.returncode,'log',log,flush=True);raise SystemExit(r.returncode)
