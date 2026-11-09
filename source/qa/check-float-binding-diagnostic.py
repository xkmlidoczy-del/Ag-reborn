#!/usr/bin/env python3
"""Summarize actual read-only Float binding observations; never infer pixel acceptance."""
import argparse,collections,hashlib,json
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);a=p.parse_args();r=a.run.resolve()
request=json.loads((r/'request.json').read_text());run=request['run']
proof={'schema':'canon-float-binding-diagnostic-summary-v1','status':'OBSERVATION_ONLY','run':str(r),'runToken':run,'roles':{},'errors':[],
    'limits':'Records actual original Effect.trackedTarget and unmodified native coordinates. These observations do not establish rendered pixels, duration correctness or functional success.'}
raw={}
for role in ['host','observer']:
    f=r/(role+'-float-binding-receipts.jsonl');raw[str(f)]=hashlib.sha256(f.read_bytes()).hexdigest()
    rows=[json.loads(l) for l in f.read_text().splitlines() if l.strip()]
    if any(x['schema']!='canon-readonly-float-binding-v1' or x['run']!=run or x['role']!=role or x['observationOnly'] is not True for x in rows):proof['errors'].append('foreign/untrusted observation '+role)
    formal=[json.loads(l) for l in (r/(role+'-receipts.jsonl')).read_text().splitlines() if l.strip()]
    ids=next((x['data'] for x in formal if x['event']=='server-two-players'),None)
    if ids is None:
        host=[json.loads(l) for l in (r/'host-receipts.jsonl').read_text().splitlines() if l.strip()]
        ids=next(x['data'] for x in host if x['event']=='server-two-players')
    expected=ids[request['observer']];groups=collections.defaultdict(list)
    for x in rows:groups[(x['skill'],x['phase'],x['packetObjectIdentity'])].append(x)
    summary=[]
    for (skill,phase,identity),g in groups.items():
        first=next((x for x in g if x['stage']=='after-original-receiver'),None)
        if first is None:proof['errors'].append('missing original-receiver row '+role+' '+skill);continue
        def expected_candidate(row):return next((e for e in row['renderingEntities'] if e['uuid']==expected),None)
        candidate=expected_candidate(first)
        earliest=next((x for x in g if (e:=expected_candidate(x)) is not None and min(e['currentEndpointDistanceSqr'],e['lerpEndpointDistanceSqr'])<=.75*.75),None)
        matched=next((x for x in g if x.get('selected',{}).get('uuid')==expected),None)
        selected=first.get('selected')
        summary.append({'skill':skill,'phase':phase,'packetObjectIdentity':identity,'rows':len(g),'receiveEffectExists':first['effectExists'],
            'receiveSelected':selected,'expectedTargetUuid':expected,'receiveExpectedCandidate':candidate,
            'firstExpectedCandidateInsideOriginalRadius':None if earliest is None else {'stage':earliest['stage'],'ageClientTicks':earliest['ageClientTicks'],'candidate':expected_candidate(earliest)},
            'firstSelectedExpectedTarget':None if matched is None else {'stage':matched['stage'],'ageClientTicks':matched['ageClientTicks']},
            'selectedUuidsObserved':sorted({x['selected']['uuid'] for x in g if 'selected'in x}),
            'maximumAgeClientTicks':max(x['ageClientTicks'] for x in g)})
    proof['roles'][role]={'rows':len(rows),'packets':summary}
proof['rawSourceHashes']=raw
out=r/'float-binding-diagnostic-summary.json';out.write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'status':proof['status'],'errors':proof['errors'],'summary':str(out),'packets':{role:[{k:x[k] for k in ['skill','phase','receiveEffectExists','selectedUuidsObserved','firstExpectedCandidateInsideOriginalRadius','firstSelectedExpectedTarget']} for x in v['packets']] for role,v in proof['roles'].items()}},ensure_ascii=False,indent=2))
raise SystemExit(1 if proof['errors'] else 0)
