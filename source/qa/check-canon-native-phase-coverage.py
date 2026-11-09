#!/usr/bin/env python3
"""Read-only coverage inventory of current-build native phases and advertised lifetimes.

This audit is deliberately independent of successful cast acceptance and pixel
judgment. An accepted R action can still need an extended recording. Advertised
network VFX duration does not assert the complete underlying mechanism lease.
"""
import argparse,collections,datetime,importlib.util,json
from pathlib import Path
S=Path(__file__).resolve().parents[1];R=S.parent
spec=importlib.util.spec_from_file_location('matrix',S/'qa/check-canon-complete-matrix.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--reference-snapshot',type=Path,required=True);p.add_argument('--provenance-policy',type=Path);p.add_argument('--render-provenance-policy',type=Path);p.add_argument('--runs-root',type=Path,default=R/'qa/evidence/2026-10-30-canon-finalization/runs');p.add_argument('--out',type=Path,required=True);a=p.parse_args();ref=m.production_snapshot(a.reference_snapshot);policy=m.attach_render_policy(m.provenance_policy(a.provenance_policy,ref),a.render_provenance_policy,ref);records=collections.defaultdict(list);excluded=[]
 for run in sorted(a.runs_root.iterdir()):
  if not (run/'result.json').is_file() or not (run/'fx4-receipts.jsonl').is_file():continue
  raw=m.load(run/'result.json')
  if not(raw.get('pass') is True and raw.get('hidden') is True and raw.get('exit')==0 and not raw.get('compiledOutputChanged')):excluded.append({'run':str(run),'reason':'incomplete/failed/drifted native recorder'});continue
  receipts=[json.loads(x) for x in (run/'fx4-receipts.jsonl').read_text().splitlines() if x.strip()];native={x['name']:x for x in raw.get('clips',[])}
  # Labels are source-script provenance, not guessed from file or motif names.
  clipmap={};active=None
  for step in (run/'script.txt').read_text().strip().split('|'):
   if step.startswith('fx4qa:begin:'):active={'skill':step.split(':')[2],'mode':step.split(':')[3]}
   elif step.startswith('rec:') and active is not None:active['label']=step[4:]
   elif step=='fx4qa:end' and active is not None:clipmap[len(clipmap)+1]=active;active=None
  for e in receipts:
   d=e.get('data',{});clip=d.get('clip');source=clipmap.get(clip,{})
   if e.get('event')!='end' or d.get('mode')!='real' or d.get('confirmedRealCast') is not True:continue
   provenance=m.provenance(run,ref,d['category'],policy)
   if not provenance['currentOwningProductionCompatible']:
    excluded.append({'run':str(run),'skill':d['skill'],'reason':'different relevant production bytes or required four-category retest',**provenance});continue
   label=source.get('label');nr=native.get(label,{});events=[x for x in receipts if x.get('event')=='server-visual' and x.get('data',{}).get('clip')==clip];phases=collections.Counter(x['data'].get('phase') for x in events)
   life=[]
   for v in events:
    if v['data'].get('phase')=='END':continue
    duration=v['data'].get('duration',0);remaining=e.get('clientTick',0)-v.get('clientTick',0)
    life.append({'phase':v['data'].get('phase'),'durationTicks':duration,'clientTicksUntilSessionEnd':remaining,'advertisedDurationFitsSessionAndFrameCount':isinstance(duration,int) and duration>=0 and remaining>=duration and nr.get('frames',0)>=duration+5})
   records[d['category']+':'+d['skill']].append({'run':str(run.resolve()),'clip':label,'clipFrames':nr.get('frames'),'realAcceptedAction':True,**provenance,'nativePhases':dict(phases),'nativeEndObserved':phases['END']>0,'allAdvertisedVisualDurationsFit':all(x['advertisedDurationFitsSessionAndFrameCount'] for x in life) if life else None,'durationWindows':life,'nativeHeldSignatureFrames':d.get('nativeHeldSignatureFrames',0),'nativeReleasedSignatureFrames':d.get('nativeReleasedSignatureFrames',0),'casterPositionBefore':d.get('serverBefore',{}).get('position'),'casterPositionAfter':d.get('serverAfter',{}).get('position')})
 result={'createdUtc':datetime.datetime.now(datetime.timezone.utc).isoformat(),'inventoryOnly':True,'provenancePolicy':{'path':policy['_path'],'sha256':policy['_sha256']} if policy else None,'renderProvenancePolicy':{'path':policy['_renderPolicy']['_path'],'sha256':policy['_renderPolicy']['_sha256']} if policy and policy.get('_renderPolicy') else None,'referenceSnapshot':str(a.reference_snapshot.resolve()),'acceptedCurrentCatalogKeys':len(records),'records':[{'catalogKey':k,'hasAnyFullAdvertisedDurationWindow':any(x['allAdvertisedVisualDurationsFit'] is True for x in rs),'hasNativeEndInAnyRecording':any(x['nativeEndObserved'] for x in rs),'attempts':rs} for k,rs in sorted(records.items())],'excludedRuns':excluded,'limits':['Receipt session boundaries are slightly wider than the rec window; requiring enough original PNG frames also bounds duration, but this audit does not visually inspect each frame.','Network duration is a rendering duration, not necessarily the backend lease expiry.','Native classical effects use their own held/released class witnesses rather than CanonVfx phases.','A no-END skill is not failed by this inventory; source semantics decide whether END is applicable.','An empty lifetime/phase inventory does not count as render lifecycle evidence.']};a.out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'acceptedCurrentCatalogKeys':len(records),'out':str(a.out)}))
if __name__=='__main__':main()
