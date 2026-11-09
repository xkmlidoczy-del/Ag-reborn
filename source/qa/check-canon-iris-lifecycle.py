#!/usr/bin/env python3
"""Verify the actual isolated Iris profile and native off/on/reload transitions."""
import argparse
import hashlib
import importlib.util
import json
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--run',type=Path,required=True)
p.add_argument('--strict-report',type=Path,required=True)
p.add_argument('--snapshot',type=Path,required=True)
p.add_argument('--out',type=Path,required=True)
a=p.parse_args();run=a.run.resolve()
load=lambda path:json.loads(path.read_text())
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
spec=importlib.util.spec_from_file_location('matrix',Path(__file__).with_name('check-canon-complete-matrix.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
request=load(run/'request.json');result=load(run/'result.json');strict=load(a.strict_report)
log=(run/'client.log').read_text(errors='replace');script=(run/'script.txt').read_text()
metrics=[json.loads(line) for line in (run/'fx4-render-metrics.jsonl').read_text().splitlines() if line.strip()]
errors=[];checks=[]
def require(ok,message):
 checks.append({'pass':bool(ok),'detail':message})
 if not ok:errors.append(message)
require(result.get('pass') and result.get('exit')==0 and not result.get('compiledOutputChanged'),'complete native run without compiled output drift')
require(strict.get('pass') and strict.get('realConfirmed')==9 and strict.get('previews')==3 and Path(strict['run']).resolve()==run,'strict nine real casts and three explicitly separate material previews')
require(m.production_snapshot(run/'compiled-snapshot.json')==m.production_snapshot(a.snapshot),'exact final production snapshot')
require(request.get('hidden') and 'HIDDEN_AGENT:' in log and request.get('irisReloadKey')=='f6','mandatory hidden client and isolated Iris reload binding F6')
require('key_iris.keybind.reload:key.keyboard.f6' in (run/'game/options.txt').read_text(),'saved actual QA profile reload key F6; ACA R stays unchanged')
require('Profile: HIGH (+0 options changed by user)' in log,'actual shader pack native default HIGH profile')
artifacts=[]
for name,expected in request.get('mods',{}).items():
 file=run/'game/mods'/name;ok=file.is_file() and sha(file)==expected
 require(ok,'actual copied optional mod hash: '+name);artifacts.append({'file':str(file),'sha256':expected,'hashMatches':ok})
require(len(artifacts)==2 and any('iris-' in x['file'] for x in artifacts) and any('sodium-' in x['file'] for x in artifacts),'exact isolated Iris and Sodium mod pair')
pack=Path(request['shaderpack']);copied=run/'game/shaderpacks'/pack.name
require(pack.is_file() and copied.is_file() and sha(pack)==sha(copied),'actual copied shader pack artifact matches requested source')
require(len(metrics)==12 and len({x['run'] for x in metrics})==1,'twelve native summaries from one owned recorder run')
fallback={'ADD':'ADD','ALPHA':'ALPHA','FLOW':'ALPHA','DISSOLVE':'ALPHA','HOLO':'ADD','REFRACT':None,'DISPERSE':None}
normal={k:k for k in fallback};states=[]
for index,row in enumerate(metrics,1):
 d=row['data'];state=d['nativeShaderState'];iris=state['iris'];active=index!=10
 ok=(d['clip']==index and d['mode']==('real' if index<=9 else 'preview')
     and state['configuredShaders'] is True and state['shaders'] is (not active)
     and state['shaderPackDetectedActive'] is active and iris['present'] is True
     and iris['currentPackPresent'] is active and iris['fallback'] is False
     and iris['incompatiblePack'] is False and state['loadedShaderCount']==5
     and state['effectiveModes']==(fallback if active else normal))
 if active:ok=ok and iris['currentPackName']==pack.name
 require(ok,'native shader ownership/state/material modes for window '+str(index))
 states.append({'clip':index,'skill':d['skill'],'mode':d['mode'],'irisPack':iris,
                'configured':state['configuredShaders'],'detectedPack':state['shaderPackDetectedActive'],
                'acaShaders':state['shaders'],'effectiveModes':state['effectiveModes'],'pass':bool(ok)})
for clip in strict['clips']:
 if clip['mode']!='real':continue
 start=log.find('QA_REC_START '+clip['name']);end=log.find('QA_REC_END '+clip['name'],start)
 segment=log[start:end] if start>=0 and end>start else ''
 require(segment and 'QA_KEY 82 DOWN' in segment and 'QA_KEY 82 UP' in segment
         and not any(text in segment for text in ['Destroying pipeline','Creating pipeline','Using shaderpack:']),
         'ordinary real skill R does not reload Iris: '+clip['name'])
start=log.find('QA_REC_END iris_on_material_probe');end=log.find('QA_REC_START iris_f6_reload_material_probe',start)
reload=log[start:end] if start>=0 and end>start else ''
require(script.count('|key:75|')==2 and script.count('|key:295|')==1
        and all(text in reload for text in ['Destroying pipeline','Creating pipeline','Using shaderpack:']),
        'ordinary K off/on and explicit F6 native pipeline reload observed before final material window')
warnings=sorted(set(line.split('[Iris/]: ',1)[-1] for line in log.splitlines() if '[Iris/]: Failed to resolve uniform' in line))
sources={name:sha(run/name) for name in ['request.json','result.json','script.txt','compiled-snapshot.json','client.log','fx4-receipts.jsonl','fx4-render-metrics.jsonl']}
report={'pass':not errors,'errors':errors,'run':str(run),'profile':'Iris1.8.12 + Sodium0.6.13 + ComplementaryReimaginedr5.9.3 native HIGH; isolated reload F6 / ACA R',
        'checks':checks,'nativeStates':states,'sourceHashes':sources,
        'strictReport':{'path':str(a.strict_report.resolve()),'sha256':sha(a.strict_report)},
        'referenceSnapshot':{'path':str(a.snapshot.resolve()),'sha256':sha(a.snapshot)},
        'actualOptionalMods':artifacts,'actualShaderPack':{'path':str(copied),'sha256':sha(copied)},
        'thirdPartyUniformWarnings':warnings,'pixelReviewPassedByThisProgram':False,
        'limits':['Conditional profile proof: resolve Iris default R conflict using isolated F6 binding. Personal configs are unchanged.',
                  'Three material lifecycle windows are explicit previews, never real skill acceptance.',
                  'Third-party uniform warnings are retained; no zero-warning or general shader-pack compatibility claim.',
                  'CPU telemetry was captured with other clients running; no isolated performance benchmark claim.']}
a.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'pass':report['pass'],'checks':len(checks),'errors':errors,'thirdPartyUniformWarnings':len(warnings)}))
raise SystemExit(0 if report['pass'] else 1)
