#!/usr/bin/env python3
"""Extract the four unchanged real Float ally cases for read-only binding diagnosis."""
import hashlib,json
from pathlib import Path
S=Path(__file__).resolve().parents[1]
E=S.parent/'qa/evidence/2026-10-30-canon-finalization'
source=E/'mp-scripts-v5';out=E/'mp-float-binding-focus-v1';out.mkdir(exist_ok=True)
plan=json.loads((source/'multiplayer-allies-plan.json').read_text())
cases=plan['cases'];wanted=[c for c in cases if c['category']=='float_dial'];assert len(wanted)==4
host=(source/'multiplayer-allies-host.script').read_text().strip().split('|')
guest=(source/'multiplayer-allies-observer.script').read_text().strip().split('|')
starts=[i for i,s in enumerate(host) if s=='cmd:tp @s -8.5 -60 -3.5 0 0'];assert len(starts)==10
h=host[:starts[0]];g=guest[:guest.index('canonformal:await:ready-'+cases[0]['skill'].replace('_','-'))]
unchanged={}
for case in wanted:
    skill=case['skill'];tag=skill.replace('_','-');idx=next(i for i,c in enumerate(cases) if c['skill']==skill)
    end=host.index('canonformal:soak:ally-release-'+tag,starts[idx])+1
    hs=host[starts[idx]:end]
    start=guest.index('canonformal:await:ready-'+tag);end=guest.index('canonformal:signal:seen-'+tag,start)+1
    gs=guest[start:end]
    assert sum(x=='keydown:82' for x in hs)==1 and 'cmd:setblock 0 -55 4 minecraft:air' in hs
    h+=hs;g+=gs
    unchanged[skill]={'hostStepsSha256':hashlib.sha256('|'.join(hs).encode()).hexdigest(),'observerStepsSha256':hashlib.sha256('|'.join(gs).encode()).hexdigest(),'identicalV5CaseSteps':True}
h+=['canonformal:done','quit'];g+=['canonformal:done','quit']
for role,steps in [('host',h),('observer',g)]:
    p=out/('float-binding-'+role+'.script');text='|'.join(steps)+'\n'
    if p.exists() and p.read_text()!=text:raise SystemExit('immutable focused script differs: '+str(p))
    p.write_text(text)
signals=[x.removeprefix('canonformal:signal:') for x in h+g if x.startswith('canonformal:signal:')]
waits=[x.removeprefix('canonformal:await:') for x in h+g if x.startswith('canonformal:await:')]
assert sorted(signals)==sorted(waits) and len(signals)==len(set(signals))
manifest={'schema':'canon-readonly-float-binding-focus-v1','sourceScripts':str(source),'cases':wanted,'unchangedCaseHashes':unchanged,
    'steps':{'host':len(h),'observer':len(g)},'pairedUniqueSignals':len(signals),'normalHostRPresses':sum(x=='keydown:82' for x in h),
    'diagnosticOnly':True,'limits':'Original v5 case operations and input timing unchanged. Initial three actual lifecycle casts retained for existing strict dual runner. No visual success inferred from a packet or read-only candidate snapshot.',
    'requiredVisuals':['nitrogen_armor:START','dark_blade:HIT']+[c['skill']+':'+c['expectedPhase'] for c in wanted]}
(out/'plan.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'out':str(out),'cases':[c['skill'] for c in wanted],'signals':len(signals),'normalR':manifest['normalHostRPresses']},ensure_ascii=False))
