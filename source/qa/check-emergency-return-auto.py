#!/usr/bin/env python3
"""Supplementary native automatic escape proof; never substitutes CP for base S."""
import argparse
import hashlib
import importlib.util
import json
import re
from decimal import Decimal
from pathlib import Path

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--run',type=Path,required=True)
p.add_argument('--snapshot',type=Path,required=True)
p.add_argument('--out',type=Path,required=True)
a=p.parse_args();run=a.run.resolve()
load=lambda path:json.loads(path.read_text())
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
spec=importlib.util.spec_from_file_location('matrix',Path(__file__).with_name('check-canon-complete-matrix.py'))
m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
request=load(run/'request.json');result=load(run/'result.json')
log=(run/'client.log').read_text(errors='replace');script=(run/'script.txt').read_text()
receipts=[json.loads(line) for line in (run/'fx4-receipts.jsonl').read_text().splitlines() if line.strip()]
checks=[];errors=[]
def require(ok,message):
 checks.append({'pass':bool(ok),'detail':message})
 if not ok:errors.append(message)
require(result.get('pass') and result.get('exit')==0 and not result.get('compiledOutputChanged'),'complete nonfatal native run with stable compiled outputs')
require(request.get('hidden') and 'HIDDEN_AGENT:' in log and 'QA_DONE' in log,'actual mandatory hidden native client completed')
require(request.get('emergencyReturnDiagnostic') and '-Dacademy.highpower.emergencyReturnDiagnostic=true' in request.get('args',[]),'existing native read-only diagnostic explicitly enabled')
require(m.production_snapshot(run/'compiled-snapshot.json')==m.production_snapshot(a.snapshot),'exact final production snapshot')
require('gametp:native-melee' in script and 'gametp:check:auto-return' in script
        and not any(text in script for text in ['aca_canon_vfx','cmd:damage','|fx:','|motif:']),'original real-PvE fixture and unchanged automatic-return assertion, no synthetic effect/damage command')
ends=[r['data'] for r in receipts if r.get('event')=='end' and r['data'].get('skill')=='emergency_return']
require(len(ends)==1 and ends[0].get('physicalRDown')==1 and ends[0].get('physicalRUp',0)>=1,'one ordinary R arms the actual automatic return')
require('QA_GAMEPLAY_REAL_MELEE skill=emergency_return accepted=true actualSource=' in log
        and 'QA_GAMEPLAY_PASS skill=emergency_return mechanism=auto-return' in log
        and re.search(r'QA_GAMEPLAY_AUTO_RETURN receipt=Receipt\[.*?paid=true, moved=true,.*?automatic=true\].*?sufferedNativePve=true',log),
        'real hostile melee and original paid/moved automatic native receipt satisfy full fixture assertion')
pattern=re.compile(r'NEW342_DIAGNOSTIC phase=(\d+) reason=(.*?) automatic=(true|false) paid=(true|false) moved=(true|false) input=(\d+) token=(\d+) source=(\(.*?\)|null) destination=(\(.*?\)|null) current=(\(.*?\)) hp=(\S+) maxHp=(\S+) baseS=(\S+) maxBaseS=(\S+) cp=(\S+) velocity=(\(.*?\)) fall=(\S+) grounded=(true|false) autoFence=(\d+)')
keys=['phase','reason','automatic','paid','moved','input','token','source','destination','current','hp','maxHp','baseS','maxBaseS','cp','velocity','fall','grounded','autoFence']
states=[dict(zip(keys,match.groups())) for match in pattern.finditer(log)]
warnings=[x for x in states if x['phase']=='1' and x['automatic']=='true']
success=[x for x in states if x['phase']=='2' and x['automatic']==x['paid']==x['moved']=='true']
require(len(warnings)==len(success)==1,'unique native post-hurt WARNING and paid/moved SUCCESS for automatic escape')
mechanism={}
if len(warnings)==len(success)==1:
 before,after=warnings[0],success[0]
 try:
  cost=Decimal(before['maxBaseS'])/Decimal(10);actual=Decimal(before['baseS'])-Decimal(after['baseS'])
  require(before['input']==after['input'] and before['token']==after['token'],'automatic native before/after bind same authenticated input and context')
  require(cost>0 and actual==cost and Decimal(after['baseS'])>=0,'actual exact base-S debit equals one tenth of certified base capacity')
  require(before['maxBaseS']==after['maxBaseS'] and before['hp']==after['hp'] and before['maxHp']==after['maxHp'] and before['cp']==after['cp'],'payment changes only base S after settled native hit; no CP or health repair')
  require(after['current']==after['destination'] and 0<int(after['autoFence'])<=1200,'actual returned destination and persistent automatic fence are committed')
  mechanism={'nativeBeforeAfter':[before,after],'exactAutomaticBaseSCost':str(cost),'actualBaseSDecreased':str(actual),'nativeCpChargeExpected':False}
 except (ValueError,ArithmeticError):require(False,'native exact decimal diagnostic malformed')
clips=[c for c in result.get('clips',[]) if c['name']=='complete_emergency_return'];frames=[]
if len(clips)==1:
 clip=clips[0];folder=run/'game/screenshots/rec'/clip['name'];files=sorted(folder.glob('f*.png'))
 require(len(files)==clip['frames'] and len(files)>=20 and [x.name for x in files]==[f'f{i:05d}.png' for i in range(len(files))],'continuous original native automatic-return recording')
 for file in files:
  digest=sha(file);require(clip['frameHashes'].get(file.name)==digest,'native frame SHA matches recorder: '+file.name)
  frames.append({'path':str(file),'sha256':digest})
else:require(False,'one original automatic-return native clip required')
sources={name:sha(run/name) for name in ['request.json','script.txt','result.json','client.log','compiled-snapshot.json','fx4-receipts.jsonl']}
report={'schema':'canon-native-emergency-automatic-base-s-v1','pass':not errors,'errors':errors,'run':str(run),'checks':checks,'mechanism':mechanism,'nativeDiagnosticStates':states,'nativeFrames':frames,'sourceHashes':sources,
        'catalogAcceptance':False,'scope':'Supplementary automatic PvE/base-S route. The manual-R exact-CP native fixture remains the full-catalog positive proof; generic CP receipt policy is unchanged.',
        'referenceSnapshot':{'path':str(a.snapshot.resolve()),'sha256':sha(a.snapshot)},'visualPixelsReviewedByThisProgram':False}
a.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'pass':report['pass'],'errors':errors,'nativeFrames':len(frames),'mechanism':mechanism}))
raise SystemExit(0 if report['pass'] else 1)
