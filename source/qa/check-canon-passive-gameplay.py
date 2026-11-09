#!/usr/bin/env python3
"""Verify existing actual-player-tick and real-attack passive gameplay fixtures."""
import argparse,json,re,hashlib
from pathlib import Path
from datetime import datetime,timezone

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--scenario',required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args();r=a.run;raw=json.loads((r/'result.json').read_text());req=json.loads((r/'request.json').read_text());script=(r/'script.txt').read_text();log=(r/'client.log').read_text(errors='replace');errors=[]
 if not(raw.get('pass') is True and raw.get('hidden') is True and raw.get('exit')==0 and not raw.get('compiledOutputChanged')):errors.append('native hidden frozen runner did not complete')
 if 'HIDDEN_AGENT:' not in log or 'QA_DONE' not in log or 'QA_UNKNOWN_STEP' in log or 'QA_PASSIVE_FAILED' in log:errors.append('runtime hidden/completion/mechanism assertion failed')
 if any(x.startswith(('fx:','motif:','cmd:aca_canon_vfx ','castforce:','pose:','posefix:')) for x in script.strip().split('|')):errors.append('synthetic visual/pose substitution forbidden')
 if a.scenario.startswith('training-'):
  category=a.scenario[len('training-'):];ids=['brain_course','brain_course_advanced','mind_course']
 else:category='meltdowner' if a.scenario=='rad_intensify' else 'teleporter';ids=[a.scenario]
 records=[]
 for skill in ids:
  issue=[];learning=[line for line in log.splitlines() if 'QA_PASSIVE_LEARNING_PASS' in line and 'category='+category+' ' in line and 'skill='+skill+' ' in line]
  effects=[line for line in log.splitlines() if 'QA_PASSIVE_EFFECT_PASS' in line and 'category='+category+' ' in line and 'skill='+skill+' ' in line]
  if len(learning)!=1 or 'learnedViaNormalUi=true' not in learning[0]:issue.append('missing actual native developer GUI/IF learning')
  if len(effects)!=1:issue.append('missing/nonunique actual passive gameplay effect')
  if 'QA_TREE_DETAIL_CONFIRM id='+skill+' requested=true viaRealClick=true' not in log:issue.append('missing actual GUI mouse confirmation')
  if learning:
   debit=re.search(r'realMachineIFSpent=([0-9.eE+-]+)',learning[0])
   if debit is None or float(debit[1])<=0:issue.append('missing finite positive native IF debit')
  if skill=='mind_course' and effects and not('witness=real_player_tick_recovery' in effects[0] and 'noRecoveryTickCalled=true' in effects[0]):issue.append('recovery lacks actual server-tick witness')
  if skill=='rad_intensify' and effects and 'witness=actual_mark_and_followup_damage' not in effects[0]:issue.append('radiation lacks real mark and attributed followup damage')
  if skill in {'dim_folding_theorem','space_fluct'} and effects and not('witness=actual_random_critical_native_damage' in effects[0] and 'noSeedOrCriticalRollOverride=true' in effects[0]):issue.append('critical lacks actual natural random native damage proof')
  records.append({'catalogKey':category+':'+skill,'pass':not issue,'errors':issue,'nativeLearningLines':learning,'actualMechanismLines':effects})
 wanted=script.strip().split('|').count('keydown:82');down=log.count('QA_KEY 82 DOWN');up=log.count('QA_KEY 82 UP')
 if wanted and (down<wanted or up<wanted):errors.append('actual ordinary attack R presses/releases missing')
 result={'createdUtc':datetime.now(timezone.utc).isoformat(),'pass':not errors and all(c['pass'] for c in records),'errors':errors,'run':str(r.resolve()),'scenario':a.scenario,'passiveRecordsExpected':len(ids),'passiveRecordsConfirmed':sum(c['pass'] for c in records),'records':records,'actualPhysicalRDown':down,'actualPhysicalRUp':up,'requestedRPresses':wanted,'sourceHashes':{n:sha(r/n) for n in ['script.txt','client.log','request.json','result.json','compiled-snapshot.json']},'scope':'Real machine UI IF debit and actual server-tick recovery / native damage and natural critical procs, as appropriate. Pure API probability checks alone cannot satisfy these effect lines.'}
 a.out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:result[k] for k in ['pass','passiveRecordsConfirmed','errors']}));raise SystemExit(0 if result['pass'] else 1)
if __name__=='__main__':main()
