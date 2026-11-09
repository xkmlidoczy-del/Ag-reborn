#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Summarize actual client evidence per registered skill, never upgrading attempts/eligible geometry to gameplay acceptance."""
import argparse,collections,csv,json,re
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1];J=ROOT/'src/main/java/cn/academy/port'
def catalog():
 rows=json.loads((ROOT/'src/main/resources/classic-skills.json').read_text(encoding="utf-8"))['skills'];hp=(J/'highpower/HighPowerSkills.java').read_text(encoding="utf-8")
 c=dict(re.findall(r'\b([A-Z][A-Z_0-9]*)\s*=\s*"([a-z_0-9]+)"',hp));c['CATEGORY']='electromaster'
 c.update({'HighPowerCategories.'+k:v for k,v in re.findall(r'\b([A-Z][A-Z_0-9]*)\s*=\s*"([a-z_0-9]+)"',(J/'highpower/HighPowerCategories.java').read_text(encoding="utf-8"))})
 def val(x):return x.strip('"') if x.startswith('"') else c[x]
 for m in re.finditer(r'new SkillCatalog\.Skill\(([^,]+),\s*([^,]+),\s*(\d+),\s*(true|false)',hp):rows.append(dict(category=val(m[1].strip()),id=val(m[2].strip()),level=int(m[3]),controllable=m[4]=='true'))
 return rows
MULTISTEP={'location_teleport':'保存目标后重开GUI并确认','tactical_coordinate_formation':'保存模板后真正确认/邀请确认','magnetic_item_pull':'自己丢物品并真实框选/箭分离','vector_construct':'有效刚体预览、抓取、转向、投掷/放下','folded_mirror_array':'放镜后真实反射命中','cross_substitution':'预置标记与真实交叉换位','return_mark':'标记后离开并返回','recovery_mark':'标记携物并核回收','spatial_relay':'多个有效节点后真搬运','scatter_locator_plate':'放板、定位及反射','remote_scatter_relay':'放远端板后束路接力','scatter_plates':'放置与分裂射线','vector_cage':'标记后第二次确认囚笼','rail_thunder_array':'多个导轨标记后执行','potential_buffer':'建立电势后受击消耗','thermal_shell':'开启后受击/消弹','particle_laminate':'层甲开启后实际受击','zero_gap_armor':'开启后受击及耗尽','mine_ray_basic':'持续真实采掘及物品掉落','mine_ray_expert':'持续多块采掘','mine_ray_luck':'真实采掘及额外掉落','storm_wing':'启动后40秒有限资源存活/降落','mag_manip':'真实金属块抓取与投掷','mag_movement':'实体支撑点与真实移动','flashing':'模式开启后方向键释放执行闪现'}
SUFFIX={'not_ready','no_target','unsupported','no_backend','forbidden','terrain','too_large','protected','changed','server_busy','unavailable','native_compat','ground','blocked','autojump','no_candidates','expired'}
def refusal_patterns():
 out=[]
 for path in (ROOT/'src/main/resources/assets/academy/lang').glob('*.json'):
  if path.name.startswith('._'):continue
  try:lang=json.loads(path.read_text(encoding="utf-8"))
  except Exception:continue
  for key,text in lang.items():
   if key.rsplit('.',1)[-1] not in SUFFIX or len(text)<6:continue
   pattern=re.escape(text)
   pattern=re.sub(r'%\d*\$?[sdif]',r'.+?',pattern)
   out.append((key,re.compile(pattern)))
 return out

def main():
 ap=argparse.ArgumentParser();ap.add_argument('logs',nargs='+',type=Path);ap.add_argument('--out',type=Path,required=True);args=ap.parse_args();args.out.mkdir(parents=True,exist_ok=True)
 records=catalog();assert len(records)==111 and sum(r["controllable"] for r in records)==96 and len({(r["category"],r["id"]) for r in records})==111,"catalog drift: refresh expected classification"
 byid={r['id']:r for r in records if r['controllable']};runs=collections.defaultdict(list);patterns=refusal_patterns();completed=[]
 for file in args.logs:
  current=None
  log_lines=file.read_text(encoding='utf-8',errors='replace').splitlines()
  log_complete=any('QA_DONE' in line for line in log_lines)
  for no,line in enumerate(log_lines,1):
   match=re.search(r'QA_LIVE_SETUP skill=(\S+) category=(\S+)',line)
   if match:
    sid,category=match.groups();current=dict(skill=sid,category=category,log=str(file.resolve()),log_completion='complete' if log_complete else 'partial_log',start_line=no,end_line=no,hand_frames=0,arm_frames=0,signature_stage_frames=0,signature_quads=0,camera_fov_frames=0,pose_renderer_witness=False,confirmed_success=False,feature_opt_in=False,feature_restored=False,mechanical_witnesses=[],feedback=[],refusals=[],preflights=[],failures=[],screenshots=[])
    runs[(category,sid)].append(current)
   if 'QA_DONE' in line:completed.append(str(file.resolve()))
   if current is None:continue
   current['end_line']=no
   for names,field in [(['handFrames'],'hand_frames'),(['armFrames'],'arm_frames'),(['signatureStageFrames','signatureFrames'],'signature_stage_frames'),(['projectedSignatureQuads','maxProjectedQuads'],'signature_quads'),(['cameraFovFrames','actualFovEventFrames','actualFovFrames'],'camera_fov_frames')]:
    for key in names:
     m=re.search(r'\b'+key+r'=(\d+)',line)
     if m:current[field]=max(current[field],int(m[1]))
   if 'QA_LIVE_FEATURE_OPTIN property=true optedIn=true enabled=true' in line:current['feature_opt_in']=True
   if 'QA_LIVE_FEATURE_OPTIN property=true optedIn=false enabled=false' in line:current['feature_restored']=True
   if 'QA_LIVE_POSE_PASS' in line:current['pose_renderer_witness']=True
   if 'QA_LIVE_CONFIRMED_PASS' in line:current['confirmed_success']=True
   if any(x in line for x in ['QA_LIVE_DAMAGE_PASS','QA_LIVE_MOVEMENT_PASS','QA_LIVE_MIRROR_FIRED','QA_LIVE_CONSTRUCT_PASS','QA_LIVE_STORM_SUSTAIN_PASS','QA_LIVE_ITEM_PULL_PASS']):current['mechanical_witnesses'].append({'line':no,'message':line.strip()})
   if current['skill']=='magnetic_ground_step' and re.search(r'NEW132 token=[1-9][0-9]* stage=client_applied reason=first_addition',line):current['mechanical_witnesses'].append({'line':no,'message':line.strip(),'scope':'one_real_native_velocity_addition'})
   if current['skill']=='beam_step' and re.search(r'NEW232 stage=applied input=[1-9][0-9]* .*token=[1-9][0-9]* oldXYZ=.* newXYZ=',line):current['mechanical_witnesses'].append({'line':no,'message':line.strip(),'scope':'one_real_native_velocity_addition'})
   if 'QA_LIVE_WIND_PREFLIGHT' in line:
    current['preflights'].append({'line':no,'message':line.strip()});m=re.search(r'blocker=(.*?) nativeReady=',line)
    if m and m[1]:current['refusals'].append({'line':no,'key':m[1],'origin':'explicit_local_preflight'})
   if '[CHAT]' in line:
    message=line.split('[CHAT]',1)[1].strip();current['feedback'].append({'line':no,'message':message})
    for key,p in patterns:
     if current['skill'] in key and p.search(message):current['refusals'].append({'line':no,'key':key,'origin':'chat_feedback_source_not_proven'});break
   if 'Exception caught during firing event:' in line or 'QA_LIVE_FAILED' in line:current['failures'].append({'line':no,'message':line.strip()})
   m=re.search(r'QA_SHOT_SAVED\s+(.*)',line)
   if m:current['screenshots'].append({'line':no,'message':m[1]})
 result=[]
 for row in records:
  key=(row['category'],row['id']);samples=runs.get(key,[]);active=row['controllable'];training='course' in row['id']
  merged={x:max([s[x] for s in samples] or [0]) for x in ['hand_frames','arm_frames','signature_stage_frames','signature_quads','camera_fov_frames']}
  mechanical=any(s['mechanical_witnesses'] for s in samples);confirmed=any(s['confirmed_success'] for s in samples);rendered=any(s['pose_renderer_witness'] for s in samples)
  feedback=[r for s in samples for r in s['refusals']];failure=[r for s in samples for r in s['failures']]
  if not active:status='training_not_cast' if training else 'passive_not_cast'
  elif not samples:status='not_run'
  elif mechanical:status='specific_mechanical_witness'
  elif confirmed:status='confirmed_action_and_render_sample'
  elif merged['signature_stage_frames'] or rendered:status='render_sample_only'
  elif feedback:status='observed_refusal_no_render'
  elif row['id'] in MULTISTEP:status='needs_multistep_no_render'
  elif row['id']=='magnetic_ground_step' and not any(s['feature_opt_in'] for s in samples):status='default_feature_disabled_motion_not_tested'
  else:status='zero_render_unresolved'
  if failure and status in ['zero_render_unresolved','needs_multistep_no_render']:status='script_assertion_failed'
  result.append(dict(category=row['category'],skill=row['id'],level=row['level'],active=active,status=status,attempts=len(samples),**merged,pose_renderer_witness=rendered,confirmed_success=confirmed,mechanical_witness=mechanical,feature_opt_in=any(s["feature_opt_in"] for s in samples),feature_restored=any(s["feature_restored"] for s in samples),multistep_requirement=MULTISTEP.get(row['id'],''),refusals=feedback,failures=failure,runs=samples))
 image_dir=args.out.parent/'screenshots'
 latest={'wind_pressure_lift':'tech_wind_pressure_lift_fp.png','storm_wing':'codex_storm_40s.png','railgun':'codex_railgun_actual_peak.png','plasma_cannon':'codex_plasma_actual_peak.png','magnetic_item_pull':'codex_item_pull_confirmed.png','magnetic_ground_step':'codex_ground_optin.png'}
 latest.update({s:'codex_camera_release_'+s+'.png' for s in ['shift_tp','spatial_relay','emergency_return','spatial_pincer_corridor']})
 for row in result:
  screenshot=image_dir/latest.get(row['skill'],'codex_all_'+row['skill']+'.png')
  row['screenshot']=str(screenshot.resolve()) if screenshot.is_file() else ''
  row['screenshot_scope']='earlier_refused_probe_not_success_evidence' if row['skill']=='beam_step' else 'captured_point_only_not_whole_skill_acceptance'
 summary=dict(records=len(result),active_records=sum(r['active'] for r in result),attempted_active=sum(r['active'] and r['attempts']>0 for r in result),statuses=dict(collections.Counter(r['status'] for r in result)),logs=[str(p.resolve()) for p in args.logs],completed_logs=completed,partial_logs=[str(p.resolve()) for p in args.logs if str(p.resolve()) not in completed],limits=['render_sample_only is not gameplay or pixel acceptance','signature counters prove eligible projected geometry, not GPU draw submission','chat refusal may originate locally or on the server; not automatically a server rejection','one key is insufficient for UI/marked/multi-stage actions','later successful trials do not erase failures from earlier trials','partial_log keeps individual recorded samples but never claims the run completed'])
 (args.out/'coverage.json').write_text(json.dumps(dict(summary=summary,skills=result),ensure_ascii=False,indent=2)+'\n',encoding='utf-8')
 fields=['category','skill','level','active','status','attempts','hand_frames','arm_frames','signature_stage_frames','signature_quads','camera_fov_frames','pose_renderer_witness','confirmed_success','mechanical_witness','multistep_requirement','screenshot']
 with (args.out/'coverage.csv').open('w',newline='',encoding='utf-8') as stream:
  writer=csv.DictWriter(stream,fieldnames=fields,extrasaction='ignore');writer.writeheader();writer.writerows(result)
 text=['# 逐技能客户端证据汇总','',f"已尝试主动技能：{summary['attempted_active']} / {summary['active_records']}。**不表示全部技能作用或视觉验收通过。**",'', '状态计数：'+json.dumps(summary['statuses'],ensure_ascii=False),'','| 系别 | 技能 | 状态 | 手/臂帧 | 签名阶段帧/quad | 镜头FOV帧 | 需补流程 | 永久截图 |','|---|---|---|---|---|---:|---|---|']
 for r in result:text.append(f"| {r['category']} | {r['skill']} | {r['status']} | {r['hand_frames']}/{r['arm_frames']} | {r['signature_stage_frames']}/{r['signature_quads']} | {r['camera_fov_frames']} | {r['multistep_requirement']} | "+ (('['+('早期探针PNG' if r['skill']=='beam_step' else 'PNG')+']('+r['screenshot']+')') if r['screenshot'] else '')+' |')
 text+=['','拒绝来源与每段日志的精确行号见coverage.json。只有specific_mechanical_witness含脚本明确核对的那一项机械作用；confirmed_action_and_render_sample不证明整套作用；render_sample_only仅渲染采样。被动3项与训练12项不应伪造按键施法，应检查实际增益、消耗/成长、学习条件和UI说明。']
 (args.out/'coverage.md').write_text('\n'.join(text)+'\n',encoding='utf-8');print(json.dumps(summary,ensure_ascii=False))
if __name__=='__main__':main()
