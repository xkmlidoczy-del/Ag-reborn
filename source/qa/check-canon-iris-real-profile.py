#!/usr/bin/env python3
"""Read-only verification of nine real casts on the final isolated Iris profile; no lifecycle-preview claim."""
import argparse,hashlib,json,importlib.util
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--strict-report',type=Path,required=True);p.add_argument('--snapshot',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
load=lambda p:json.loads(p.read_text());sha=lambda p:hashlib.sha256(p.read_bytes()).hexdigest()
s=importlib.util.spec_from_file_location('m',Path(__file__).with_name('check-canon-complete-matrix.py'));m=importlib.util.module_from_spec(s);s.loader.exec_module(m)
r=a.run.resolve();q=load(r/'request.json');v=load(r/'result.json');c=load(a.strict_report);log=(r/'client.log').read_text(errors='replace');metrics=[json.loads(x) for x in (r/'fx4-render-metrics.jsonl').read_text().splitlines()];checks=[]
def check(ok,label):checks.append({'pass':bool(ok),'detail':label})
check(v.get('pass') and v.get('exit')==0 and not v.get('compiledOutputChanged'),'native exit0, complete recording, no output drift')
check(c.get('pass') and c.get('realConfirmed')==9 and c.get('previews')==0 and Path(c['run']).resolve()==r,'nine actual-key paid native casts, zero previews')
check(m.production_snapshot(r/'compiled-snapshot.json')==m.production_snapshot(a.snapshot),'exact final production snapshot')
check(q.get('hidden') and 'HIDDEN_AGENT:' in log,'mandatory hidden native client')
check(q.get('irisReloadKey')=='f6' and 'key_iris.keybind.reload:key.keyboard.f6' in (r/'game/options.txt').read_text(),'real isolated F6 Iris reload config, R remains skill key')
check('Profile: HIGH (+0 options changed by user)' in log,'actual native HIGH shader pack profile')
check(len(q.get('mods',{}))==2,'isolated Iris and Sodium pair only')
for name,h in q.get('mods',{}).items():check(sha(r/'game/mods'/name)==h,'actual optional artifact '+name)
pack=Path(q['shaderpack']);check(sha(pack)==sha(r/'game/shaderpacks'/pack.name),'copied actual shader pack SHA')
fallback={'ADD':'ADD','ALPHA':'ALPHA','FLOW':'ALPHA','DISSOLVE':'ALPHA','HOLO':'ADD','REFRACT':None,'DISPERSE':None}
check(len(metrics)==9,'nine native state captures')
states=[]
for x in metrics:
 d=x['data'];b=d['nativeShaderState'];i=b['iris'];ok=(d['mode']=='real' and b['configuredShaders'] is True and b['shaders'] is False and b['shaderPackDetectedActive'] is True and i['present'] is True and i['currentPackPresent'] is True and i['fallback'] is False and i['incompatiblePack'] is False and i['currentPackName']==pack.name and b['loadedShaderCount']==5 and b['effectiveModes']==fallback);check(ok,'native active Iris ownership/fallback mode '+d['skill']);states.append(d)
for x in c['clips']:
 b=log.find('QA_REC_START '+x['name']);e=log.find('QA_REC_END '+x['name'],b);t=log[b:e] if b>=0 and e>b else '';check(t and 'QA_KEY 82 DOWN' in t and 'QA_KEY 82 UP' in t and not any(s in t for s in ['Destroying pipeline','Creating pipeline','Using shaderpack:']),'real skill R does not reload Iris '+x['name'])
evidence=[r/n for n in ['request.json','result.json','compiled-snapshot.json','client.log','fx4-render-metrics.jsonl','fx4-receipts.jsonl']]+[a.strict_report]
out={'schema':'canon-iris-final-real9-profile-v1','pass':all(x['pass'] for x in checks),'checks':checks,'nativeStates':states,'sourceEvidence':[{'path':str(p.resolve()),'sha256':sha(p)} for p in evidence],'scope':'Final exact-production nine real casts only. Earlier fifth-freeze off/on/F6 material-preview lifecycle remains separately dated evidence; no new toggle lifecycle or general shader compatibility claim.'};a.out.write_text(json.dumps(out,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'pass':out['pass'],'checks':len(checks),'errors':[x for x in checks if not x['pass']]}));raise SystemExit(0 if out['pass'] else 1)
