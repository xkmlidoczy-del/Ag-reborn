#!/usr/bin/env python3
"""Read actual vanilla state predicates alongside unchanged native Hydro receipts."""
import argparse,hashlib,json
from pathlib import Path
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
root=a.run.resolve();log=(root/'client.log').read_text(errors='replace');rs=[json.loads(x) for x in (root/'fx4-receipts.jsonl').read_text().splitlines()];errors=[];checks=[]
def require(ok,message):
 checks.append({'pass':bool(ok),'message':message})
 if not ok:errors.append(message)
result=json.loads((root/'result.json').read_text());require(result.get('pass') and result.get('exit')==0 and not result.get('compiledOutputChanged'),'unchanged compiled snapshot and actual hidden native process pass')
require('HIDDEN_AGENT: GLFW creation visibility/focus disabled' in log and 'QA_DONE' in log,'mandatory hidden native completion')
markers=['QA_HYDRO_IRRIGATE_DRY_BEFORE_R','QA_HYDRO_IRRIGATE_SOURCE_BEFORE_R','QA_HYDRO_IRRIGATE_MOIST7_AFTER_R','QA_HYDRO_DOUSE_ON_FIRE_BEFORE_R','QA_HYDRO_DOUSE_CLEAR_AFTER_R'];positions={}
for marker in markers:
 found=[(i,line) for i,line in enumerate(log.splitlines()) if '[Server thread/INFO]' in line and marker in line]
 require(len(found)==1,'actual successful vanilla server predicate exactly once: '+marker)
 positions[marker]=found[0][0] if found else -1
require(0<=positions[markers[0]]<positions[markers[1]]<positions[markers[2]]<positions[markers[3]]<positions[markers[4]],'actual server state predicates ordered before/after each real action')
for skill in ['hydro_irrigate','hydro_douse_self']:
 ends=[r['data'] for r in rs if r['event']=='end' and r['data'].get('skill')==skill]
 require(len(ends)==1 and ends[0].get('confirmedRealCast') and ends[0].get('physicalRDown')==1 and ends[0].get('physicalRUp')==1 and ends[0].get('serverVisuals')==1 and ends[0].get('committedCooldownEvidence'),'one real R and one accepted original server HIT with committed clock: '+skill)
 sources=[r for r in rs if r['event']=='server-visual' and r['data'].get('skill')==skill]
 require(len(sources)==1 and sources[0]['data'].get('phase')=='HIT','actual original server HIT, not preview: '+skill)
anchors=['request.json','result.json','compiled-snapshot.json','script.txt','client.log','fx4-receipts.jsonl'];hashes={n:hashlib.sha256((root/n).read_bytes()).hexdigest() for n in anchors}
report={'schema':'canon-hydro-native-preconditions-v1','pass':not errors,'run':str(root),'checks':checks,'errors':errors,'sourceHashes':hashes,'scope':'Successful vanilla conditional commands prove actual before/after soil/Fire state; unchanged native recorder independently proves paid skill input/HIT. No added production state setters.'}
a.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'pass':report['pass'],'errors':errors},ensure_ascii=False));raise SystemExit(0 if report['pass'] else 1)
