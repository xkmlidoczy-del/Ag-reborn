#!/usr/bin/env python3
"""Prepare isolated true-input Hydro preconditions. Never launches or builds."""
import argparse,hashlib,importlib.util,json,zipfile
from pathlib import Path
S=Path(__file__).resolve().parents[1];E=S.parent/'qa/evidence/2026-10-30-canon-finalization'
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
if a.out.exists():raise SystemExit('Refusing to overwrite immutable fixture directory')
a.out.mkdir(parents=True)
spec=importlib.util.spec_from_file_location('canon_complete_generator',S/'qa/build-canon-complete-acceptance.py');g=importlib.util.module_from_spec(spec);spec.loader.exec_module(g)
rows={r['id']:r for r in g.catalog()};b=g.Batch('hydro-preconditions-01')
for skill in ['hydro_irrigate','hydro_douse_self']:
 q=g.recipe(rows[skill]);q['points']=[];q['ai']=False
 if skill=='hydro_irrigate':
  q['setup']=['cmd:gamerule randomTickSpeed 0','cmd:setblock 0 -60 3 minecraft:air','cmd:setblock 0 -61 3 minecraft:farmland[moisture=0]','cmd:setblock -1 -61 -2 minecraft:water']
  q['aim']=(.5,-60.2,3.5)
  q['action']=['cmd:execute if block 0 -61 3 minecraft:farmland[moisture=0] run say QA_HYDRO_IRRIGATE_DRY_BEFORE_R','cmd:execute if block -1 -61 -2 minecraft:water[level=0] run say QA_HYDRO_IRRIGATE_SOURCE_BEFORE_R']+g.press()+['wait:3','cmd:execute if block 0 -61 3 minecraft:farmland[moisture=7] run say QA_HYDRO_IRRIGATE_MOIST7_AFTER_R']
  q['notes']=['Fresh dry farmland placed directly at floor after clearing above; never build lower farmland beneath a solid upper farmland. Original FarmBlock schedules a one-tick survival test when above is solid.','Actual isolated visible water source is five Z blocks away from the soil, outside ordinary hydration; randomTickSpeed0 prevents natural dry-soil reversion or hydration from impersonating a paid skill.','Original vanilla conditional block commands emit distinct server say receipts for actual dry farmland and source immediately before R, then actual moisture7 afterward. Original paid server CanonVfx HIT still required.']
 else:
  q['setup']=['cmd:gamerule randomTickSpeed 0','cmd:setblock -2 -61 1 minecraft:water','cmd:scoreboard objectives add qahydro_fire dummy','cmd:setblock 0 -60 0 minecraft:fire','wait:32','cmd:setblock 0 -60 0 minecraft:air']
  q['action']=['cmd:execute store result score @s qahydro_fire run data get entity @s Fire','cmd:execute if score @s qahydro_fire matches 1.. run say QA_HYDRO_DOUSE_ON_FIRE_BEFORE_R']+g.press()+['wait:3','cmd:execute store result score @s qahydro_fire run data get entity @s Fire','cmd:execute unless score @s qahydro_fire matches 1.. run say QA_HYDRO_DOUSE_CLEAR_AFTER_R']
  q['notes']=['Real vanilla fire collision for32 explicit ticks plus normal command settling, exceeding original Player20-tick ignition delay; fire block then removed so it cannot re-ignite after the action. No Fire NBT mutation or direct clearFire/cast API.','Visible isolated source is below ground, water cannot flow into player; normal dry standing caster.','Actual vanilla scoreboard stores the read-only player Fire NBT; conditional server say receipts require positive Fire immediately before R and nonpositive after actual original received HIT. No source/debug command counts as the paid skill.']
 b.add(rows[skill],q)
b.write(a.out)
index={'batches':[{'name':b.name,'skills':[c['catalogKey'] for c in b.clips],'script':str((a.out/(b.name+'.script')).resolve()),'manifest':str((a.out/(b.name+'-clips.json')).resolve())}],'soloRequested':2,'noSuccessClaim':True,'immutable':True}
(a.out/'batch-index.json').write_text(json.dumps(index,ensure_ascii=False,indent=2)+'\n')
sha=lambda path:hashlib.sha256(path.read_bytes()).hexdigest()
source_jar=S/'build/moddev/artifacts/neoforge-21.1.252-sources.jar'
with zipfile.ZipFile(source_jar) as z:
 extracted={n:z.read(n).decode() for n in ['net/minecraft/world/level/block/FarmBlock.java','net/minecraft/world/level/block/BaseFireBlock.java','net/minecraft/world/entity/player/Player.java','net/minecraft/commands/arguments/blocks/BlockInput.java']}
proof={
 'priorRun':str(E/'runs/fourth-canon-media-hydro_hand-01-26ea67ceaec3'),
 'oldActualResult':{'hydro_irrigate':'one actualR, zeroCP/clock/VFX; actual farmland block was not read','hydro_douse_self':'one actualR, zeroCP/clock/VFX; actual Fire NBT was not read'},
 'diagnosisScope':'Source-backed fixture-precondition diagnosis, not a rerun acceptance claim. Preserve original raw failures.',
 'irrigate':'Old lower farmland was placed under solid upper farmland; BlockInput updates neighbour shapes, FarmBlock schedules one-tick canSurvive test and becomes dirt before next command removes upper block six client ticks later. New setup never creates upper farmland and disables random crop ticks in isolated QA world.',
 'douseSelf':'Original Player.getFireImmuneTicks20 and BaseFireBlock increments negative remainingFireTicks until0 before igniteForSeconds8. Old6command+8explicit ticks under20; new32explicit contact ticks exceed this gate.',
 'rawSourceSha256':{n:hashlib.sha256(v.encode()).hexdigest() for n,v in extracted.items()},
 'generatorSha256':sha(Path(__file__)),
 'scriptSha256':sha(a.out/(b.name+'.script')),
 'noProductionOrJavaChanges':True,'executed':False}
(a.out/'precondition-diagnosis.json').write_text(json.dumps(proof,ensure_ascii=False,indent=2)+'\n')
print(json.dumps({'out':str(a.out),'batchIndex':str(a.out/'batch-index.json'),'clips':2,'actualRunPending':True},ensure_ascii=False))
