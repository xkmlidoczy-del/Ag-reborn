#!/usr/bin/env python3
"""Read native GUI/finite-IF/modifier receipts for all15 passive catalog records."""
import argparse,json,re,hashlib
from pathlib import Path
from datetime import datetime,timezone
S=Path(__file__).resolve().parents[1]
def load(p):return json.loads(p.read_text())
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def close(a,b):return abs(a-b)<=1e-5*max(1,abs(b))
def verify(run):
 errors=[];raw=load(run/'result.json');req=load(run/'request.json');log=(run/'client.log').read_text(errors='replace');path=run/'passive-receipts.jsonl'
 rows=[json.loads(x) for x in path.read_text().splitlines() if x.strip()] if path.exists() else []
 catalog=[x for x in load(S/'src/main/resources/classic-skills.json')['skills'] if not x['controllable']]
 assert len(catalog)==15
 token=[a.split('=',1)[1] for a in req.get('args',[]) if a.startswith('-Dacademy.qa.fx4.run=')]
 if len(token)!=1 or {x.get('run') for x in rows}!={token[0]}:errors.append('missing/stale actual-run passive receipts')
 if not(raw.get('pass') and raw.get('hidden') and raw.get('exit')==0 and not raw.get('compiledOutputChanged')):errors.append('hidden frozen native run did not complete')
 if 'HIDDEN_AGENT:' not in log or 'QA_DONE' not in log or 'QA_UNKNOWN_STEP' in log:errors.append('hidden runtime/driver completion failure')
 checked=[]
 for entry in catalog:
  c=entry['category'];s=entry['id'];key=c+':'+s;items=[x['data'] for x in rows if x.get('data',{}).get('category')==c and x['data'].get('skill')==s];issue=[]
  stages={stage:[x for x in items if x.get('stage')==stage] for stage in ['reset-precondition','before-learning','after-learning']}
  if any(len(x)!=1 for x in stages.values()):issue.append('missing/nonunique native reset-before-after sequence')
  else:
   reset,before,after=[stages[k][0] for k in stages]
   if not(reset['tick']<=before['tick']<after['tick']):issue.append('passive learning stages out of order')
   if reset.get('testedRecordLearned') or before.get('testedRecordLearned') or after.get('testedRecordLearned') is not True:issue.append('actual tested learned bit not false→true')
   if after.get('pass') is not True:issue.append('native behavior assertion did not pass')
   f=after.get('finiteDeveloperIf',{});debit=f.get('before',0)-f.get('after',0)
   if not(f.get('expectedNativeDebit',0)>0 and abs(debit-f.get('expectedNativeDebit',0))<1e-6):issue.append('actual finite IF debit is missing/mismatched')
   b=before['nativeModifiers'];a=after['afterNativeModifiers']
   if after.get('beforeNativeModifiers')!=b:issue.append('before modifier snapshot changed')
   if s=='brain_course' and not close(a['cachedMaxCp']-b['cachedMaxCp'],1000):issue.append('actual native maxCP +1000 absent')
   if s=='brain_course_advanced' and not(close(a['cachedMaxCp']-b['cachedMaxCp'],1500) and close(a['cachedMaxOverload']-b['cachedMaxOverload'],100)):issue.append('actual native maxCP/overload boost absent')
   if s=='mind_course' and not close(a['nativeRecoveryMultiplier']/b['nativeRecoveryMultiplier'],1.2):issue.append('actual native CP recovery multiplier absent')
   if s=='dim_folding_theorem' and not close(a['criticalTier0']-b['criticalTier0'],.1):issue.append('real learned theorem native critical probability absent')
   if s=='space_fluct' and not all(close(a['criticalTier'+str(i)]-b['criticalTier'+str(i)],v) for i,v in enumerate([.18,.1,.01])):issue.append('real learned fluct native critical probabilities absent')
   if not re.search(r'QA_LEARNING_PASS id='+re.escape(s)+r'.*category='+re.escape(c)+r'\b',log):issue.append('native GUI finite-IF learning witness missing')
  if s=='rad_intensify':
   hits=[x for x in items if x.get('stage')=='radiation-hit'];ends=[]
   rp=run/'fx4-receipts.jsonl'
   if rp.exists():ends=[json.loads(x)['data'] for x in rp.read_text().splitlines() if '"event":"end"' in x and '"skill":"electron_bomb"' in x]
   if len(hits)!=1 or hits[0].get('pass') is not True or not(0<hits[0].get('nativeMarkTicks',0)<=60) or hits[0].get('noQaMarkWrite') is not True:issue.append('actual native hit radiation mark/rate missing')
   if len(ends)!=1 or ends[0].get('confirmedRealCast') is not True or not(ends[0].get('physicalRDown',0)>0 and ends[0].get('physicalRUp',0)>0):issue.append('actual ordinary ElectronBomb R cast/release missing')
  checked.append({'catalogKey':key,'pass':not issue,'errors':issue,'nativeStages':items})
 return {'createdUtc':datetime.now(timezone.utc).isoformat(),'pass':not errors and all(x['pass'] for x in checked),'errors':errors,'run':str(run.resolve()),'passiveRecordsExpected':15,'passiveRecordsConfirmed':sum(x['pass'] for x in checked),'records':checked,'sourceHashes':{p.name:sha(p) for p in [run/'result.json',run/'request.json',run/'script.txt',run/'client.log',path] if p.is_file()},'scope':'Actual native GUI learning/finite developer IF and player modifier APIs; radiation additionally requires real native attack target-mark. Random critical-hit pixel animation and natural survival progression are separate.'}
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();r=verify(a.run);a.out.write_text(json.dumps(r,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:r[k] for k in ['pass','passiveRecordsConfirmed','errors']}));raise SystemExit(0 if r['pass'] else 1)
if __name__=='__main__':main()
