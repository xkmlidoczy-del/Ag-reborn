#!/usr/bin/env python3
"""Build actual8-family and>=15min multiplayer soak scripts; never launch anything."""
import argparse,hashlib,json
from pathlib import Path
S=Path(__file__).resolve().parents[1];E=S.parent/'qa/evidence/2026-10-30-canon-finalization'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,default=E/'scripts');p.add_argument('--cycles',type=int,default=8);p.add_argument('--idle-ticks',type=int,default=1500);a=p.parse_args()
if a.cycles<1 or a.cycles>20 or a.idle_ticks<0:raise SystemExit('invalid soak duration plan')
a.out.mkdir(parents=True,exist_ok=True)
h=(S/'qa/canon-eight-host.script').read_text().strip().replace('CanonGuest','@OBSERVER@').replace('cmd:gamemode creative @s|','')
g=(S/'qa/canon-eight-observer.script').read_text().strip()
# Clean hostile fixtures before the new float target; preserve all native effect cleanup.
h=h.replace('live:setup:float_lift|','cmd:kill @e[type=minecraft:husk]|live:setup:float_lift|')
# Actual eligibleAI mental mob; no fake targeting orNoAI bypass.
h=h.replace('{NoGravity:1b,Silent:1b}|cmd:attribute','{NoAI:0b,NoGravity:1b,Silent:1b}|cmd:attribute')
(a.out/'multiplayer-eight-host.script').write_text(h+'\n');(a.out/'multiplayer-eight-observer.script').write_text(g+'\n')
for role in ('host','observer'):
 t=(S/f'qa/canon-liquid-module-{role}.script').read_text().strip();(a.out/f'multiplayer-liquid-{role}.script').write_text(t+'\n')
ht='canonformal:await:observer-done|wait:30|canonformal:done|quit';gt='canonformal:signal:observer-done|wait:30|canonformal:done|quit'
assert h.endswith(ht) and g.endswith(gt)
h=h[:-len(ht)].rstrip('|');g=g[:-len(gt)].rstrip('|')
H=[h,'canonformal:soak:begin','canonformal:signal:soak-start','canonformal:await:soak-guest-begin']
G=[g,'canonformal:await:soak-start','canonformal:soak:begin','canonformal:signal:soak-guest-begin']
CASES=[('dark_wings','dark_matter',[],None),('nitrogen_armor','nitrogen_armor',[],None),
       ('mental_probe','mental_out',['cmd:summon minecraft:husk 0.5 -60 4.0 {NoAI:0b,NoGravity:1b,Silent:1b,PersistenceRequired:1b}','cmd:attribute @e[type=minecraft:husk,limit=1,sort=nearest] minecraft:generic.movement_speed base set 0'],(.5,-59.025,4)),
       ('aero_jet','aero_hand',[],(.5,-59.2,8)),
       ('hydro_pressure_jet','hydro_hand',['cmd:setblock 2 -61 0 minecraft:water','cmd:summon minecraft:husk 0.5 -60 4.0 {NoAI:1b,NoGravity:1b,Silent:1b}'],(.5,-59,4)),
       ('float_lift','float_dial',['cmd:setblock 2 -61 0 minecraft:grass_block','cmd:summon minecraft:husk 0.5 -60 4.0 {NoAI:1b,Silent:1b}'],(.5,-59,4)),
       ('paper_plane','paper_psychokinesis',['cmd:item replace entity @s weapon.mainhand with minecraft:paper 4'],(.5,-59,8)),
       ('liquid_scout','liquid_shadow',['cmd:item replace entity @s weapon.mainhand with academy:dense_shadow_medium 1'],(.5,-59,8))]
rows=[]
for cycle in range(a.cycles):
 for i,(skill,category,extra,aim) in enumerate(CASES):
  tag=f'c{cycle:02d}s{i:02d}';rejoin=i==0 and cycle%2==1;dimension=i==1 and cycle%2==1
  H += ['cmd:kill @e[type=minecraft:husk]','cmd:tp @s 0.5 -60 0.5 0 0','cmd:tp @OBSERVER@ 4.5 -60 4.5',f'live:setup:{skill}','wait:35',*extra]
  if aim:H += ['aim:'+','.join(str(x) for x in aim)]
  H += ['live:clearui',f'canonformal:signal:ready-{tag}',f'canonformal:await:watch-{tag}','keydown:82','wait:3','keyup:82',f'canonformal:await-vfx:{skill}']
  G += [f'canonformal:await:ready-{tag}',f'canonformal:signal:watch-{tag}',f'canonformal:await-vfx:{skill}']
  if cycle==0:
   H += [f'shot:soak_host_{skill}'];G += [f'shot:soak_observer_{skill}']
  if rejoin:
   G += ['canonformal:rejoin','canonformal:ready',f'canonformal:signal:rejoined-{tag}'];H += [f'canonformal:await:rejoined-{tag}']
  if dimension:
   H += [f'canonformal:await:dimension-ready-{tag}','cmd:execute in minecraft:the_nether run tp @OBSERVER@ 0 120 0',f'canonformal:signal:dimension-go-{tag}',f'canonformal:await:dimension-clean-{tag}','cmd:execute in minecraft:overworld run tp @OBSERVER@ 4.5 -60 4.5',f'canonformal:signal:dimension-back-{tag}',f'canonformal:await:dimension-returned-{tag}']
   G += [f'canonformal:signal:dimension-ready-{tag}',f'canonformal:await:dimension-go-{tag}','canonformal:wait-clean',f'canonformal:soak:clean-dimension-{tag}',f'canonformal:signal:dimension-clean-{tag}',f'canonformal:await:dimension-back-{tag}','wait:20',f'canonformal:signal:dimension-returned-{tag}']
  H += [f'canonformal:signal:cast-{tag}',f'canonformal:await:seen-{tag}',f'canonformal:soak:cast-{tag}','wait:45']
  G += [f'canonformal:await:cast-{tag}',f'canonformal:soak:cast-{tag}',f'canonformal:signal:seen-{tag}']
  rows.append({'cycle':cycle,'index':i,'skill':skill,'category':category,'tag':tag,'actualRPressRelease':True,'observerRejoinDuringActiveEffect':rejoin,'observerDimensionChangeDuringActiveEffect':dimension})
 H += [f'wait:{a.idle_ticks}',f'canonformal:soak:clean-cycle-{cycle:02d}',f'canonformal:signal:cycle-clean-{cycle:02d}',f'canonformal:await:guest-clean-{cycle:02d}']
 G += [f'canonformal:await:cycle-clean-{cycle:02d}',f'canonformal:soak:clean-cycle-{cycle:02d}',f'canonformal:signal:guest-clean-{cycle:02d}']
H += ['wait:450','canonformal:soak:clean-end','canonformal:signal:soak-end','canonformal:await:soak-guest-end','canonformal:done','quit']
G += ['canonformal:await:soak-end','canonformal:soak:clean-end','canonformal:signal:soak-guest-end','canonformal:done','quit']
for role,steps in [('host',H),('observer',G)]:
 (a.out/f'multiplayer-soak-{role}.script').write_text('|'.join(steps)+'\n')
manifest={'schema':'canon-final-multiplayer-plan-v1','soakCycles':a.cycles,'idleTicksEachCycle':a.idle_ticks,'minimumSoakWallSecondsRequired':900,'minimumServerTicksRequired':18000,'loopCasts':rows,'initialEightFamilyRealCastGroup':True,'initialLifecycleGroupUsesActualDisconnectAndDimensionChange':True,'expectedCategories':sorted({x[1] for x in CASES}),'scripts':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted(a.out.glob('multiplayer-*.script'))},'limits':'All actual inputs in a QA-unlocked world, not natural learning; periodic real casts and lifecycle transitions plus intervening connected idle time. Runtime checks must confirm minimum measured duration/ticks; script intent cannot prove soak completion.'}
(a.out/'multiplayer-plan.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'out':str(a.out),'loopCasts':len(rows),'cycles':a.cycles,'minimumMeasuredSeconds':900}))
