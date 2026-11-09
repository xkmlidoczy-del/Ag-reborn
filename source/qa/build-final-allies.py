#!/usr/bin/env python3
"""Assemble10actualplayer-allycases +3real lifecycle casts +2expectedMPmotion refusals."""
import argparse,json,hashlib
from pathlib import Path
S=Path(__file__).resolve().parents[1];E=S.parent/'qa/evidence/2026-10-30-canon-finalization'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=E/'scripts');a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
recipes=json.loads((E/'dual-skill-fixtures.json').read_text());assert recipes['count']==10
H=(S/'qa/canon-formal-host.script').read_text().strip().replace('CanonGuest','@OBSERVER@')
G=(S/'qa/canon-formal-observer.script').read_text().strip()
ht='canonformal:await:observer-done|wait:30|canonformal:done|quit';gt='canonformal:signal:observer-done|wait:30|canonformal:done|quit'
assert H.endswith(ht) and G.endswith(gt)
h=[H[:-len(ht)].rstrip('|'),'cmd:team add complete_allies'];g=[G[:-len(gt)].rstrip('|')];cases=[]
for case in recipes['cases']:
 skill=case['skill'];tag=skill.replace('_','-')
 h+=['cmd:tp @s -8.5 -60 -3.5 0 0','cmd:tp @OBSERVER@ -8.5 -60 -5.5 0 0','cmd:kill @e[type=!minecraft:player]','cmd:fill -6 -64 -2 6 -60 12 minecraft:air','cmd:fill -6 -62 -2 6 -62 12 minecraft:dirt','cmd:fill -6 -61 -2 6 -61 12 minecraft:grass_block']
 host=[s for s in case['hostSteps'] if s!='cmd:team add complete_allies'];guest=list(case['observerSteps']);overrides=[]
 if skill=='mental_crowd_detach':
  host=[x.replace('husk -1.5 -60 4.5','husk -3.5 -60 4.5').replace('husk 2.5 -60 4.5','husk 4.5 -60 4.5').replace('husk -1.5 -60 6.0','husk -3.5 -60 7.5').replace('husk 2.5 -60 6.0','husk 4.5 -60 7.5') for x in host]
  overrides.append('Actual AI hostiles retain zero native navigation speed and acquire real retaliation aggro from the same vanilla player_attack damage command, but stand4 horizontal blocks or farther from the consenting player. Outside normal melee bounding-box reach, within original12-block skill scan. No invulnerability/knockback/position repair, no artificial target setter.')
 if skill.startswith('float_'):
  # Stand on genuine support first. Start recording and aim while stable, then
  # let an ordinary block removal create accepted gravity motion. Keeping aim
  # before removal avoids consuming the short falling ray window with GUI lead.
  i=host.index('cmd:setblock 0 -55 4 minecraft:air');j=host.index('keydown:82',i)
  host[i:j]=['rec:host_'+skill,'aim:0.5,-55.95,4.5','cmd:setblock 0 -55 4 minecraft:air','canonformal:soak:ally-before-'+tag]
  overrides.append('Actual observer stands on original stone support at y=-55. Both consent and record while stable; host pre-aims then removes support by vanilla command. Native gravity/accepted movement is sampled immediately before ordinary R. No motion/flight/gravity setter or jump effect.')
 else:
  if skill=='hydro_douse_ally':
   i=host.index('cmd:setblock 0 -60 4 minecraft:fire');assert host[i+1]=='wait:8';host[i+1]='wait:32'
   overrides.append('32 explicit ticks of actual vanilla fire collision exceed Player20-tick negative-Fire ignition gate; then remove source to prevent re-ignition. Actual readonly ally-before checkpoint must show burning.')
  i=next(i for i,s in enumerate(host) if s.startswith('aim:'));host.insert(i,'canonformal:soak:ally-before-'+tag)
  i=host.index('rec:host_'+skill);aim=host[i-1] if host[i-1].startswith('aim:') else None
  if aim:host[i-1:i+1]=['rec:host_'+skill,aim]
 if skill=='hydro_tow_ally':
  i=host.index('cmd:tp @OBSERVER@ 0.5 -62 4.5 180 0');j=host.index('keydown:82',i)
  host[i:j]=['rec:host_'+skill,'aim:0.5,-61.2,4.5','cmd:setblock 0 -62 4 minecraft:stone','cmd:tp @OBSERVER@ 0.5 -61 4.5 180 0','cmd:setblock 0 -62 4 minecraft:water','canonformal:soak:ally-before-'+tag]
  overrides.append('Actual three-layer water target stands on real stone support at (0,-62,4), feet-61; host records/aims before normal teleport. Normal vanilla block replacement restores this support to water, then only normal6tick command settling, readonly checkpoint and R. Same support-fall mechanism as independently passed hydro repel/undertow native fixture; target must remain wet and clear of floor, no velocity/gravity/flight mutation.')
 i=host.index('canonformal:await-vfx:'+skill);host.insert(i+1,'canonformal:soak:ally-after-'+tag)
 host=[('wait:35' if s=='wait:90' else s) for s in host]
 # Consent is deliberately withdrawn during finite guard/hover windows; keep
 # recording15ticks afterward instead of claiming mere admission is persistence.
 i=guest.index('wait:90');guest[i:i+1]=['wait:20','keyup:340','wait:15']
 last=max(i for i,s in enumerate(guest) if s=='keyup:340');guest.pop(last)
 h+=host+['wait:8','canonformal:soak:ally-release-'+tag];g+=guest
 cases.append({'catalogKey':case['catalogKey'],'skill':skill,'category':case['category'],'expectedPhase':case['expectedPhase'],'fixtureOverrides':overrides,'requiresActualConsentAndMutualTeam':True,'continuousConsentReleaseInput':True,'preparedOnlyUntilNativeRun':True})
guards=[{'skill':'wind_pressure_lift','label':'wind','diagnostic':'live:wind-diagnostic','ordinaryJumpBeforeR':False,'maximumHorizontalDisplacement':1e-6,'maximumVerticalDisplacement':1e-6},
        {'skill':'beam_step','label':'beam','diagnostic':'live:motion-preflight','ordinaryJumpBeforeR':True,'maximumHorizontalDisplacement':1e-6,'maximumVerticalDisplacement':2.5}]
for spec in guards:
 skill=spec['skill'];label=spec['label'];diag=spec['diagnostic']
 h+=['cmd:tp @s 0.5 -60 0.5 0 0','cmd:tp @OBSERVER@ 4.5 -60 4.5','cmd:gamemode creative @OBSERVER@','cmd:effect clear @s',f'live:setup:{skill}','wait:35','live:clearui',diag,f'canonformal:signal:{label}-guard-ready',f'canonformal:await:{label}-guard-watching',f'mark:{skill.upper()}_MULTIPLAYER_EXPECTED_REFUSAL',f'rec:host_{label}_mp_guard']
 if spec['ordinaryJumpBeforeR']:h+=['keydown:32','wait:3','keyup:32']
 h+=[f'canonformal:soak:guard-begin-{skill}','keydown:82','wait:8','keyup:82','wait:5',diag,'live:visual-report',f'canonformal:soak:guard-end-{skill}','recend',f'canonformal:signal:{label}-guard-done',f'canonformal:await:{label}-guard-seen']
 g+=[f'canonformal:await:{label}-guard-ready',f'canonformal:soak:guard-observer-before-{skill}',f'rec:observer_{label}_mp_guard',f'canonformal:signal:{label}-guard-watching',f'canonformal:await:{label}-guard-done',f'canonformal:soak:guard-observer-after-{skill}','recend',f'canonformal:signal:{label}-guard-seen']
h+=['canonformal:done','quit'];g+=['canonformal:done','quit']
for role,steps in [('host',h),('observer',g)]:
 (a.out/f'multiplayer-allies-{role}.script').write_text('|'.join(steps)+'\n')
manifest={'schema':'canon-final-realallies-plan-v1','cases':cases,'count':len(cases),'initialRealLifecycleSkills':['dark_wings','nitrogen_armor','dark_blade'],'expectedActualRPresses':17,'expectedWindMultiplayerRefusal':True,'guardedMultiplayerRefusals':guards,'expectedWindZeroChargeWitness':'per-skill server-tick minimumCP/nativeCP/maxclock/cooldown, ownercontext0, persistentgrantstateunchanged and actualposition/raw/knownmotion traces; no syntheticcastorpreview','originalPreparedRecipes':str(E/'dual-skill-fixtures.json'),'scripts':{role:hashlib.sha256((a.out/f'multiplayer-allies-{role}.script').read_bytes()).hexdigest() for role in ('host','observer')},'limits':'RealLANplayers, ordinaryinput and productionallyguards. Setup supplies team/loadout/location/water/fire. Guard/float target consent is released after20ticks and15moreticksrecorded; impact protection is separately covered by native runtime tests, not inferred from sharedpacket alone.'}
#3initial +10ally +2prerequisitearmor/wings +2expectedrefusal
assert manifest['expectedActualRPresses']==3+10+2+2
(a.out/'multiplayer-allies-plan.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print('allies scripts prepared',len(cases),'positive +2 expectedrefusals; no client launched')
