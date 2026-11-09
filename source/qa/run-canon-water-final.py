#!/usr/bin/env python3
"""Two actual R presses move one real water source crosschunk; cold-JVM same-save persistence."""
import argparse,json,uuid
from pathlib import Path
from final_native_launch import E,HiddenLaunch,receipt_rows,save
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--output-base',type=Path,default=E/'water');a=p.parse_args()
a.output_base.mkdir(parents=True,exist_ok=True);run=uuid.uuid4().hex;out=a.output_base/('water-formal-final-'+run);out.mkdir()
launch=HiddenLaunch(out,'FinalWater',{'academy.qa.showcase':'true','academy.qa.world':'WaterFormal'},fps=30)
print(out.resolve(),flush=True)
first=launch.run('transfer','wait:40|canonwater:setup|wait:35|live:setup:hydro_transfer|wait:60|live:clearui|live:isolation|live:motion-preflight|canonwater:source|wait:5|canonwater:target|wait:5|canonwater:check|shot:water-transfer-final|quit',timeout=360)
rows=receipt_rows(launch.game/'water-formal-receipts.jsonl');errors=[]
if not first['pass']:errors.append('transfer native stage failed')
if sum(r.get('event')=='press-released' for r in rows)!=2:errors.append('need exactly2 actual R press/release witnesses')
setup=[r for r in rows if r.get('event')=='setup'];paid=[r for r in rows if r.get('event')=='paid-placement']
if len(setup)!=1 or len(paid)!=1 or not paid[0]['cp']<setup[0]['cp']:errors.append('real post-debit placement resource witness missing')
if not any(r.get('event')=='complete' for r in rows):errors.append('real water-block transfer invariant not verified')
restart=None
if not errors:
    world=launch.worlds();save(out/'save-before-restart.json',{'directory':str(world),'levelDatPresent':True})
    restart=launch.run('restart','wait:40|canonwater:restart-check|quit',timeout=300,extra={'academy.qa.world':world.name})
    fresh=[r for r in receipt_rows(launch.game/'water-formal-receipts.jsonl') if r.get('time',0)>=restart['beganEpochMs']]
    if not restart['pass'] or not any(r.get('event')=='restart-verified' for r in fresh):errors.append('cold-JVM persistence witness missing')
result={'pass':not errors,'run':run,'hidden':True,'actualRPressReleases':sum(r.get('event')=='press-released' for r in rows),'nativeStages':launch.stage_results,'coldJvmRestart':bool(restart and restart['pass'] and not errors),'source':[2079,161,2051],'target':[2081,161,2051],'sourceChunkX':2079>>4,'targetChunkX':2081>>4,'errors':errors,'screenshots':launch.images(),'limits':'Sealed real sourcebasins are QAfixtures; actual normal keypath, production sourceconservation and exactdebit assertions execute in Java without replacing the backend.'}
save(out/'result.json',result);print(json.dumps({k:result[k] for k in ('pass','actualRPressReleases','coldJvmRestart','errors')}),flush=True)
raise SystemExit(0 if result['pass'] else 1)
