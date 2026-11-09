#!/usr/bin/env python3
"""Fresh normal-world14-objective route and same-save cold-JVM restart, hidden only."""
import argparse,json,uuid
from pathlib import Path
from final_native_launch import E,HiddenLaunch,receipt_rows,save
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('route',choices=['ABILITY','EQUIPMENT','COMBAT']);p.add_argument('--output-base',type=Path,default=E/'story');p.add_argument('--timeout',type=int,default=1200);a=p.parse_args()
a.output_base.mkdir(parents=True,exist_ok=True);run=uuid.uuid4().hex
out=a.output_base/('urban-formal-final-'+a.route.lower()+'-'+run);out.mkdir()
launch=HiddenLaunch(out,'Final'+a.route.title(),{'academy.qa.showcase':'true','academy.qa.normalWorld':'true','academy.qa.seed':'251027','academy.qa.urban.directory':str(out)})
print(out.resolve(),flush=True)
script=f'wait:40|urbanformal:setup:{a.route}|urbanformal:run|urbanformal:check|shot:story-{a.route.lower()}-complete|mark:URBAN_FORMAL_COMPLETE|quit'
first=launch.run('route',script,timeout=a.timeout)
rows=receipt_rows(launch.game/'urban-formal-receipts.jsonl')
errors=[]
if not first['pass']:errors.append('route native stage failed')
if any(r.get('route')!=a.route for r in rows):errors.append('wrong route in objective receipts')
objectives=[r for r in rows if r.get('event')=='objective-complete']
if len(objectives)!=14 or len({r.get('objective') for r in objectives})!=14:errors.append('need14 distinct actual objective-complete receipts')
if [r.get('completed') for r in objectives]!=list(range(1,15)):errors.append('actual story objective ledger did not advance1..14')
if not any(r.get('event')=='complete' and r.get('completed')==14 for r in rows):errors.append('no actual complete14 receipt')
restart=None
if not errors:
    world=launch.worlds();save(out/'save-before-restart.json',{'directory':str(world),'levelDatPresent':True})
    restart=launch.run('restart',f'wait:40|urbanformal:restart-check:{a.route}|mark:URBAN_RESTART_COMPLETE|quit',timeout=300,extra={'academy.qa.world':world.name})
    restart_rows=receipt_rows(launch.game/'urban-formal-receipts.jsonl')
    fresh=[r for r in restart_rows if r.get('time',0)>=restart['beganEpochMs']]
    if not restart['pass'] or not any(r.get('event')=='restart-verified' and r.get('completed')==14 and r.get('route')==a.route for r in fresh):errors.append('actual cold-JVM same-save restart did not verify14 persisted objectives')
    if restart['beganEpochMs']<first['endedEpochMs']:errors.append('restart overlapped first JVM')
shots=launch.images();save(out/'screenshots.json',shots)
result={'pass':not errors,'run':run,'route':a.route,'objectiveReceipts':len(objectives),'distinctObjectives':len({r.get('objective') for r in objectives}),'completedObjectiveOrder':[r.get('objective') for r in objectives],'hidden':True,'nativeStages':launch.stage_results,'coldJvmRestart':bool(restart and restart['pass'] and not errors),'errors':errors,'screenshots':shots,'limits':'QA travel/loadout/learning supplied; objective progress itself uses production interactions, actual paper input/combat/NPCpathfinding and normal persistence. This is not a natural-learning, unassisted playthrough or human plot-quality assessment.'}
save(out/'result.json',result);print(json.dumps({k:result[k] for k in ('pass','route','objectiveReceipts','coldJvmRestart','errors')}),flush=True)
raise SystemExit(0 if result['pass'] else 1)
