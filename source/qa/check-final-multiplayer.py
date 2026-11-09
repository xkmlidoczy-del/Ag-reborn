#!/usr/bin/env python3
"""Strictly bind real two-client network/lifecycle and optionally long soak checkpoints."""
import argparse,ast,hashlib,json,math,re
from pathlib import Path
E=Path(__file__).resolve().parents[2]/'qa/evidence/2026-10-30-canon-finalization'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--suite',choices=['eight','liquid','soak','allies'],required=True);p.add_argument('--plan',type=Path,default=E/'scripts/multiplayer-plan.json');p.add_argument('--out',type=Path,required=True);a=p.parse_args()
rows=lambda role:[json.loads(x) for x in (a.run/f'{role}-receipts.jsonl').read_text().splitlines()]
H,G=rows('host'),rows('observer');result=json.loads((a.run/'result.json').read_text());request=json.loads((a.run/'request.json').read_text());errors=[];checks=[]
def require(ok,why):
 checks.append({'pass':bool(ok),'detail':why})
 if not ok:errors.append(why)
require(result.get('status')=='PASS','base formal native/lifecycle verdict PASS')
require(not result.get('evidence',{}).get('compiledOutputChanged',['unknown']),'compiled class/resources unchanged')
require(all(r.get('run')==request['run'] and r.get('role')==role for role,rs in [('host',H),('observer',G)] for r in rs),'all receipts currentrun and correctrole')
ids=result['evidence']['serverPlayers'];owner=ids[request['host']];require(len(set(ids.values()))==2,'two real distinct server identities')
for role,rs in [('host',H),('observer',G)]:
 log=(a.run/(role+'.log')).read_text(errors='replace')
 require('HIDDEN_AGENT: GLFW creation visibility/focus disabled' in log and 'QA_DONE' in log and 'QA_UNKNOWN_STEP' not in log,role+' hidden native script completed')
 script=(a.run/(role+'-script.txt')).read_text()
 require('aca_canon_vfx' not in script,role+' contains no visualpreview injection')

def packet(r):
 d=r.get('data',{});return (d.get('owner'),d.get('entity'),d.get('skill'),d.get('phase'),d.get('dimension'),d.get('origin'),d.get('endpoint'))
def visuals(rs,local,skill=None):
 return [r for r in rs if r.get('event')=='vfx-received' and r.get('data',{}).get('owner')==owner and r['data'].get('local') is local and (skill is None or r['data'].get('skill')==skill)]
match_summary=[]
expected=['dark_wings','nitrogen_armor','dark_blade','mental_probe','aero_jet','hydro_pressure_jet','float_lift','paper_plane','liquid_scout'] if a.suite!='liquid' else ['liquid_scout']
if a.suite=='allies':expected=['dark_wings','nitrogen_armor','dark_blade']+[c['skill'] for c in json.loads((a.plan.parent/'multiplayer-allies-plan.json').read_text())['cases']]
for skill in expected:
 hs=visuals(H,True,skill);gs=visuals(G,False,skill)
 matches=[(h,g) for h in hs for g in gs if packet(h)==packet(g) and abs(h['time']-g['time'])<=1500]
 require(bool(matches),'actual matching hostlocal/observerremote server event: '+skill)
 match_summary.append({'skill':skill,'hostEvents':len(hs),'observerEvents':len(gs),'matchingEvents':len(matches)})
soak=None
if a.suite=='soak':
 plan=json.loads(a.plan.read_text());samples={role:[r for r in rs if r.get('event')=='soak-checkpoint'] for role,rs in [('host',H),('observer',G)]}
 for role,rs in samples.items():
  by={r['data']['stage']:r for r in rs};require('begin' in by and 'clean-end' in by,role+' soak begin and final naturalcleanup witnessed')
  require(len(by)==len(rs),role+' checkpoint stages unique')
  for r in rs:
   d=r['data'];require(d['pendingPreviews']==0,role+' no delayed QA preview at '+d['stage'])
   pending_bindings=d.get('pendingBindings');require(isinstance(pending_bindings,int) and 0<=pending_bindings<=24,role+' actual pending Float bindings bounded at '+d['stage'])
   require(d['particles']<=700 and d['decals']<=48 and d['warps']<=40 and d['effects']<=24,role+' native pool caps at '+d['stage'])
   if role=='observer' or d['stage'].startswith('clean-'):
    require(all(math.isfinite(v) and v==0 for v in d['cameraTimers'].values()),role+' remote or cleaned camera timers zero at '+d['stage'])
   if d['stage'].startswith('clean-'):
    require(all(d.get(k)==0 for k in ['effects','pendingPreviews','pendingBindings','particles','decals','warps']),role+' native FX pools and pending bindings naturally empty at '+d['stage'])
   if role=='host':
    require(d['server']['serverPlayerCount']==2,role+' realserver has2players at '+d['stage'])
    for player in d['server']['players']:
     require(player['alive'] and all(math.isfinite(player[k]) and player[k]>=0 for k in ['cp','nativeCp','health']),role+' realplayer alive with finiteledger at '+d['stage'])
  if 'begin' in by and 'clean-end' in by:
   duration=by['clean-end']['time']-by['begin']['time'];require(duration>=plan['minimumSoakWallSecondsRequired']*1000,role+' measured soak duration>=900seconds')
 hb={r['data']['stage']:r for r in samples['host']}
 if 'begin' in hb and 'clean-end' in hb:
  ticks=hb['clean-end']['data']['server']['serverTick']-hb['begin']['data']['server']['serverTick'];require(ticks>=plan['minimumServerTicksRequired'],'actualserver advanced>=18000ticks throughout soak')
 else:ticks=None
 previous=hb.get('begin',{}).get('time',0);percase=[]
 for case in plan['loopCasts']:
  end=hb.get('cast-'+case['tag']);require(end is not None,'actualcase checkpoint '+case['tag'])
  if end is None:continue
  hs=[r for r in visuals(H,True,case['skill']) if previous<r['time']<=end['time'] and r['data']['phase']!='END']
  gs=visuals(G,False,case['skill']);matches=[(h,g) for h in hs for g in gs if packet(h)==packet(g) and abs(h['time']-g['time'])<=1500]
  require(bool(matches),'loopcase actual currentinterval sharedserver event '+case['tag']+' '+case['skill'])
  percase.append({**case,'pass':bool(matches),'hostCheckpointTime':end['time'],'matchingEventTimes':[[h['time'],g['time']] for h,g in matches]});previous=end['time']
 hostlog=(a.run/'host.log').read_text(errors='replace');rdowns=hostlog.count('QA_KEY 82 DOWN');rups=hostlog.count('QA_KEY 82 UP')
 require(rdowns==len(plan['loopCasts'])+9 and rups>=rdowns,'all9initial+64loop actualRpresses and releases observed')
 events=[r['event'] for r in G];require(events.count('rejoined')>=5 and events.count('dimension-clean')>=5,'initial+4 additional real rejoin/dimension-clean cycles')
 soak={'plannedLoopCasts':len(plan['loopCasts']),'caseResults':percase,'actualRDown':rdowns,'actualRUp':rups,'serverTicks':ticks,'durationMsByRole':{role:(rs[-1]['time']-rs[0]['time']) if rs else None for role,rs in samples.items()},'nativeCheckpointsByRole':{role:len(rs) for role,rs in samples.items()},'heapUsedBytesByRole':{role:[r['data']['heapUsedBytes'] for r in rs] for role,rs in samples.items()},'heapInterpretation':'Observed only; no GC is invoked and no broad memoryleak conclusion is derived.'}
allies=None
if a.suite=='allies':
 plan=json.loads((a.plan.parent/'multiplayer-allies-plan.json').read_text())
 checkpoints={r['data']['stage']:r for r in H if r.get('event')=='soak-checkpoint'};case_results=[]
 for c in plan['cases']:
  tag=c['skill'].replace('_','-');before=checkpoints.get('ally-before-'+tag);after=checkpoints.get('ally-after-'+tag);release=checkpoints.get('ally-release-'+tag)
  require(before is not None and after is not None and release is not None,'actualally nativecheckpoints '+c['skill'])
  if not before or not after or not release:continue
  players=before['data']['server']['players'];caster=next((p for p in players if p['uuid']==owner),None);target=next((p for p in players if p['uuid']==ids[request['observer']]),None)
  require(caster is not None and target is not None and caster['team']==target['team']!='' and target['sneaking'],'actual mutualteam and continuously consenting realtarget '+c['skill'])
  require(caster['slot2']==c['skill'] and caster['active'],'actual enabled mappedhost skill '+c['skill'])
  if c['skill'].startswith('float_'):
   require(not target['onGround'] and not target['inWater'],'actual airborne dry ally '+c['skill'])
   if c['skill']=='float_rescue':require(ast.literal_eval(target.get('knownMovement',target['velocity']))[1]<0,'floatrescue actual known negativeverticalvelocity beforeR')
  if c['skill'] in ('hydro_rescue','hydro_tow_ally'):require(target['inWater'],'actual target submerged beforeR '+c['skill'])
  if c['skill']=='hydro_douse_ally':require(target['onFire'],'actual realtarget burning beforedouseR')
  events=[r for r in visuals(H,True,c['skill']) if before['time']<r['time']<=after['time'] and r['data']['phase']==c['expectedPhase']]
  require(bool(events),'actual currentinterval acceptedally serverevent '+c['skill'])
  require(not next(p for p in release['data']['server']['players'] if p['uuid']==target['uuid'])['sneaking'],'actual targetconsentrelease reachedserver '+c['skill'])
  # Admission and sharedpacket are guaranteed; relative pose/impact/lease details
  # remain explicit fields, not assumed from a green recorder result.
  case_results.append({**c,'acceptedActualServerEvents':len(events),'beforePlayers':players,'afterPlayers':after['data']['server']['players'],'releasePlayers':release['data']['server']['players']})
 guards=[]
 for spec in plan['guardedMultiplayerRefusals']:
  skill=spec['skill'];gb=checkpoints.get('guard-begin-'+skill);ge=checkpoints.get('guard-end-'+skill)
  require(gb is not None and ge is not None,'expectedMPguard before/end checkpoints '+skill)
  guard=None
  if gb and ge:
   require(gb['data']['server'].get('published') is True and ge['data']['server'].get('published') is True and ge['data']['server']['serverPlayerCount']==2,'actual published2player LAN profile '+skill)
   guard=ge['data']['server'].get('multiplayerGuard');require(guard is not None and guard.get('skill')==skill and guard.get('owner')==owner,'per-skill owner-scoped server-tick witness '+skill)
   if guard:
    require(guard['samples']>=8 and guard['minimumCp']==guard['beforeCp'] and guard['minimumNativeCp']==guard['beforeNativeCp'] and guard['maximumClock']==guard['beforeClock'] and guard['maximumCooldown']==0,'expectedMP refusal spentzeroCP/nativeCP/clock/cooldown throughout sampledinterval '+skill)
    require(guard['maximumOwnedContexts']==0 and guard['maximumQuotaEntries']==0 and not guard['persistentGrantStateChanged'],'no ownercontext/lease/grantquota/payment-persistence created '+skill)
    require(guard['maximumHorizontalDisplacement']<=spec['maximumHorizontalDisplacement'] and guard['maximumVerticalDisplacement']<=spec['maximumVerticalDisplacement'] and guard['maximumRawHorizontalVelocity']<=1e-6 and guard['maximumKnownHorizontalVelocity']<=1e-6,'actual position/raw/known-motion within declared normalinput boundary '+skill)
    require(len(guard['serverTickTrace'])==guard['samples'] and all(row['ownedContexts']==0 for row in guard['serverTickTrace']),'complete actualserver-tick context/motion trace '+skill)
    if spec['ordinaryJumpBeforeR']:
     require(any(not row['onGround'] for row in guard['serverTickTrace']) and guard['maximumVerticalDisplacement']>.05,'actual ordinary airborne motion sampled for expectedMPrefusal '+skill)
     require(math.isfinite(guard['minimumKnownVerticalVelocity']) and math.isfinite(guard['maximumKnownVerticalVelocity']),'beamMP actual knownvertical motion is finite and retained; no frozen-gravity assumption')
   log=(a.run/'host.log').read_text(errors='replace')
   if skill=='wind_pressure_lift':require('QA_LIVE_WIND_PREFLIGHT blocker=academy.highpower.wind_pressure_lift.unsupported' in log and 'published=true' in log,'actual client reports explicitpublished-LAN WindPressureLiftunsupported gate')
   else:require(any('QA_LIVE_MOTION_PREFLIGHT skill=beam_step ' in line and 'beamBlocker=academy.highpower.beam_step.unsupported' in line for line in log.splitlines()),'actual client reports explicitpublished-LAN BeamStepunsupported gate')
   signatures=re.findall(r'QA_LIVE_VISUAL_SAMPLE skill='+re.escape(skill)+r' .*?signatureStageFrames=(\d+) projectedSignatureQuads=(\d+)',log)
   require(bool(signatures) and all(int(frames)==0 and int(quads)==0 for frames,quads in signatures),'no accepted native projectedworld-signature for expectedMPrefusal '+skill)
   no_packets=not any(gb['time']<r['time']<=ge['time'] for r in visuals(H,True,skill));require(no_packets,'no canonical serverVFX during expectedrefusal '+skill)
  guards.append({'skill':skill,'witness':guard,'inputProfile':spec,'note':'Beam ordinaryjump/gravity is allowed and recorded; zero means no payment/context/grant, not frozen vanilla motion.'})
 rdowns=(a.run/'host.log').read_text(errors='replace').count('QA_KEY 82 DOWN');require(rdowns==plan['expectedActualRPresses'],'3initial+10allies+2prerequisites+2refusal actualRdowncount')
 allies={'positiveCases':case_results,'positiveIntended':plan['count'],'positiveAccepted':sum(r['acceptedActualServerEvents']>0 for r in case_results),'expectedMultiplayerRefusals':guards,'actualRDown':rdowns,'limits':'Actualally admission/sharedtransport/consent and supplied physicalpreconditions verified. Expectedmotion refusals are separate boundary evidence, never MPpositive support. No claim that every impact/cancellation/latency combination was exercised; runtimephysics guards are separately tested.'}
report={'schema':'canon-final-multiplayer-native-check-v1','pass':not errors,'suite':a.suite,'run':str(a.run.resolve()),'runToken':request['run'],'matchingRealServerEvents':match_summary,'soak':soak,'allies':allies,'checks':len(checks),'errors':errors,'checkDetails':checks,'rawResultSha256':hashlib.sha256((a.run/'result.json').read_bytes()).hexdigest(),'limits':'Actual localLAN2player sessions in QA-unlocked world; doesnot establish internetlatency, adversarialsecurity, allskill visualquality or human narrative acceptance.'}
a.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:report[k] for k in ['pass','suite','checks','errors']},ensure_ascii=False));raise SystemExit(0 if report['pass'] else 1)
