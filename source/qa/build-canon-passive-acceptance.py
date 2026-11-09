#!/usr/bin/env python3
"""Prepare15 actual native GUI+IF+modifier passive checks; never run a client."""
import argparse,hashlib,json
from pathlib import Path
S=Path(__file__).resolve().parents[1]
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--only',default='');a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
 rows=json.loads((S/'src/main/resources/classic-skills.json').read_text())['skills'];passives=[x for x in rows if not x['controllable']];assert len(passives)==15
 only=set(a.only.split(',')) if a.only else set();passives=[x for x in passives if not only or x['id'] in only or x['category']+':'+x['id'] in only]
 steps=['wait:80','lang:zh_cn','cmd:time set noon','cmd:gamerule doMobSpawning false','cmd:difficulty normal','cmd:gamemode survival','keyup:82','keyup:340','tree:close','cam:first','cmd:tp @s 0.5 -60 0.5 0 0'];cases=[]
 for r in passives:
  cat=r['category'];skill=r['id'];selector=cat+'/'+skill;level=r['level'];kind='prepare-learning' if level<=2 else 'prepare-machine-learning' if level==3 else 'prepare-advanced-machine-learning';stim=int(3+level*level*.5);tps=26 if level<=2 else 21 if level==3 else 16;wait=stim*tps+32
  steps+=['tree:close',f'mark:passive:{cat}:{skill}',f'fx4qa:fixture:passive-reset:{selector}',f'live:{kind}:{selector}','wait:25',f'fx4qa:fixture:passive-before:{selector}']
  if kind=='prepare-learning':steps+=['tree:develop','wait:5']
  steps += [f'tree:select:{skill}','tree:detail-confirm',f'wait:{wait}',f'live:learned:{selector}',f'fx4qa:fixture:passive-after:{selector}',f'shot:passive_{cat}_{skill}','tree:close']
  cases.append({'catalogKey':cat+':'+skill,'skill':skill,'category':cat,'level':level,'kind':'actual_native_gui_learning_and_modifier_behavior','developerType':kind,'portableDeveloperOpenedByRealInput':kind=='prepare-learning','requestedWait':wait,'separateRealActionRequired':skill=='rad_intensify','notRun':True})
  if skill=='rad_intensify':
   steps+=['cmd:kill @e[type=!minecraft:player]','cmd:summon minecraft:husk 0.5 -60 3.5 {NoAI:1b,Silent:1b,Tags:["passive_radiation_target"]}','live:slot:electron_bomb','wait:8','aim:0.5,-59.2,3.5','fx4qa:begin:electron_bomb:real','rec:passive_radiation_actual_hit','keydown:82','wait:10','keyup:82','wait:20','fx4qa:fixture:passive-radiation-hit','wait:10','recend','fx4qa:end']
 steps+=['quit'];path=a.out/'all15-passives.script';path.write_text('|'.join(steps)+'\n');manifest={'suite':'canon-passive-native-acceptance','passiveRecords':len(cases),'cases':cases,'scriptSha256':sha(path),'notRun':True,'expectedNativeLearningPasses':len(cases),'requiredRadiationMarkedActualTarget':any(x['separateRealActionRequired'] for x in cases),'proofScope':'Real native GUI learned bits, finite IF debit and cached/native-bus modifiers. Actual critical random-hit visual is separate from native passive probability API verification.'};(a.out/'all15-passives-manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n');print(json.dumps({'passiveCases':len(cases),'script':str(path.resolve())}))
if __name__=='__main__':main()
