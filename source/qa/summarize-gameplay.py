#!/usr/bin/env python3
"""Report only explicit real-mechanism witnesses; never promote render/active to full gameplay acceptance."""
import json,pathlib,re,datetime
root=pathlib.Path(__file__).resolve().parents[2];out=root/'qa/evidence/2026-10-22-full-gameplay'
old=json.loads((root/'qa/evidence/2026-10-22-codex-completion/cast-coverage-final/coverage.json').read_text())
rows={s['skill']:{'category':s['category'],'skill':s['skill'],'level':s['level'],'status':'pending_complete_gameplay','witnesses':[]} for s in old['skills'] if s['active']}
failures=[]
logs=list(out.glob('*.log'))
for log in out.glob('*grounding*/host.log'):
 result=log.parent/'result.json'
 if result.is_file():
  proof=json.loads(result.read_text())
  if proof.get('kind')=='grounding' and proof.get('hostExit')==0 and proof.get('observerExit')==0 and proof.get('actualServerProof'):logs.append(log)
for log in sorted(logs):
 lines=log.read_text(errors='replace').splitlines();completed=any('QA_DONE' in x for x in lines);current_skill=None
 dual_grounding=any('QA_DUAL_GROUNDING_DONE' in t for t in lines) and all(any('QA_DUAL_GROUNDING_PASS round='+round+' ' in t for t in lines) for round in ('late','precise'))
 dedicated_multi_axis=any('QA_GAMEPLAY_MULTI_AXIS_DEDICATED_PASS ' in t for t in lines)
 for line_no,line in enumerate(lines,1):
  setup=re.search(r'QA_LIVE_SETUP skill=([a-z_]+)',line)
  if setup:current_skill=setup[1]
  m=re.search(r'QA_GAMEPLAY(?:_[A-Z]+)*_PASS skill=([a-z_]+)',line)
  if m is None and dual_grounding:m=re.search(r'QA_DUAL_GROUNDING_PASS round=(?:late|precise) skill=([a-z_]+)',line)
  if m is None and 'QA_LIVE_MOVEMENT_PASS' in line and current_skill=='wind_pressure_lift':m=re.match(r'(.*)',current_skill)
  if m is None and 'QA_GROUND_TRAVEL_PASS' in line and any('QA_GROUND_ADMISSION_PASS' in t for t in lines):m=re.match(r'(.*)','magnetic_ground_step')
  if m and m[1] in rows and (m[1]!='multi_axis_reflection' or dedicated_multi_axis):
   rows[m[1]]['status']='mechanical_witness_recorded';rows[m[1]]['witnesses'].append({'log':str(log.relative_to(root)),'line':line_no,'batch_completed':completed,'assertion':line.split(']: ',1)[-1]})
  if 'Exception caught' in line or 'QA_GAMEPLAY_FAILED' in line and 'Caused by:' in line:failures.append({'log':str(log.relative_to(root)),'line':line_no,'message':line})
report={'generated_at':datetime.datetime.now().astimezone().isoformat(),'scope':'An explicit mechanism witness is not automatically complete acceptance of every described mode. Full per-skill approval still requires reviewed scenarios, resources and cleanup. No render sample counts here.','active_total':len(rows),'skills_with_mechanical_witness':sum(bool(r['witnesses']) for r in rows.values()),'skills':list(rows.values()),'preserved_failures':failures}
(out/'gameplay-progress.json').write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
md=['# 96主动完整玩法续验','',report['scope'],'',f"主动总数 {len(rows)}；已有明确作用见证 {report['skills_with_mechanical_witness']}（不是全部模式完整通过）。",'', '| 系别 | 技能 | 状态 | 作用断言记录 |','|---|---|---|---|']
for r in rows.values():
 w=r['witnesses'];md.append(f"| {r['category']} | {r['skill']} | {r['status']} | "+'<br>'.join(f"[{pathlib.Path(t['log']).name}:{t['line']}]({pathlib.Path(t['log']).name}) "+t['assertion'].replace('|','/') for t in w)+' |')
(out/'gameplay-progress.md').write_text('\n'.join(md)+'\n');print(f"{report['skills_with_mechanical_witness']}/{len(rows)} explicit mechanism witnesses; no full96 claim")
