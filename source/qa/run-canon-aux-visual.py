#!/usr/bin/env python3
"""Native hidden item gallery: 28 render contexts and 42 frames including natural dropped-item turns.

This fixture supplies inventory and a neutral physical stage; it never invokes the auxiliary
items' functions. Screenshots are actual Minecraft frames and need a separate visual review.
Prepare writeShowcaseLaunchMetadata first, then freeze compiled outputs during this run.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import uuid

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--timeout', type=int, default=600)
p.add_argument('--output-base', type=Path)
a = p.parse_args()
if not 60 <= a.timeout <= 1800:
    raise SystemExit('timeout must be 60..1800 seconds')
source = Path(__file__).resolve().parents[1]
run = uuid.uuid4().hex
out = (a.output_base or source.parent/'qa/output')/('aux-visual-'+run)
out.mkdir(parents=True)
game = out/'game'
game.mkdir()
ids = ['mental_remote', 'dark_matter_crystal', 'nitrogen_canister',
       'aero_pressure_chip', 'water_flask', 'buoyancy_float', 'origami_paper']
hidden = source/'qa/hidden-window-agent.jar'
if not hidden.is_file():
    raise SystemExit('hidden-window agent required; no visible fallback')
if not (source/'build/classes/java/main/cn/academy/port/qa/CanonAuxVisualQa.class').is_file():
    raise SystemExit('prepare current -PshowcaseQA=true launch metadata/classes first')
metadata = json.loads((source/'build/moddev/showcaseClientLaunch.json').read_text())
java_home = Path(os.environ.get('ACA_QA_JAVA_HOME', Path.home()/'.local/jdks/jdk-21.0.12.1+1/Contents/Home'))
java = java_home/'bin/java'
if 'JAVA_VERSION="21.' not in (java_home/'release').read_text():
    raise SystemExit('Java 21 required')
(game/'options.txt').write_text('pauseOnLostFocus:false\nmaxFps:30\nrenderDistance:5\n'
    'simulationDistance:5\nautoJump:false\nguiScale:3\nlang:zh_cn\nfov:0.0\ntutorialStep:none\n')
steps = ['wait:40', 'cmd:time set day', 'cmd:difficulty peaceful',
         'cmd:gamerule doMobSpawning false', 'auxvisual:setup', 'wait:30',
         'auxvisual:models', 'auxvisual:inventory', 'wait:20',
         'shot:aux_inventory_all_items_and_flask_states', 'auxvisual:check:inventory',
         'auxvisual:close', 'cam:first', 'vcam:off', 'gui:on',
         'auxvisual:context:hotbar', 'wait:12', 'shot:aux_hotbar_with_coin_and_needle',
         'auxvisual:check:hotbar']
shots = ['aux_inventory_all_items_and_flask_states.png', 'aux_hotbar_with_coin_and_needle.png']
for item in ids:
    steps += ['auxvisual:hold:'+item, 'wait:14', 'cam:first', 'vcam:off',
              'gui:on', 'look:0', 'auxvisual:context:first', 'wait:12',
              'shot:aux_first_'+item, 'auxvisual:check:first',
              'cam:front', 'vcam:off', 'gui:off',
              'auxvisual:context:third', 'wait:12', 'shot:aux_third_'+item,
              'auxvisual:check:third']
    shots += ['aux_first_'+item+'.png', 'aux_third_'+item+'.png']
for damage in range(5):
    steps += ['cam:first', 'vcam:off', 'gui:on', 'auxvisual:flask:'+str(damage),
              'wait:14', 'auxvisual:context:first', 'wait:12',
              'shot:aux_flask_damage_'+str(damage), 'auxvisual:check:first']
    shots.append('aux_flask_damage_'+str(damage)+'.png')
for item in ids:
    steps += ['cam:first', 'gui:off', 'vcam:0.5,-58.7,0.5,0.5,-58.7,2.5',
              'auxvisual:drop:'+item, 'wait:24', 'auxvisual:context:drop', 'wait:12',
              'auxvisual:drop-angle:base', 'shot:aux_drop_'+item, 'auxvisual:check:drop',
              # Screenshot waits plus these18 ticks let the actual ItemEntity rotate
              # roughly90 degrees. No entity/renderer rotation is replaced for photos.
              'wait:18', 'auxvisual:drop-angle:quarter', 'shot:aux_drop_'+item+'_quarter',
              'wait:18', 'auxvisual:drop-angle:half', 'shot:aux_drop_'+item+'_half']
    shots += ['aux_drop_'+item+'.png', 'aux_drop_'+item+'_quarter.png', 'aux_drop_'+item+'_half.png']
steps += ['vcam:off', 'gui:on', 'auxvisual:done', 'quit']
script = '|'.join(steps)
(out/'script.txt').write_text(script+'\n')
program = (source/'build/moddev/showcaseClientRunProgramArgs.txt').read_text().replace(
    '--gameDir\n.', '--gameDir\n'+str(game.resolve()))
program += '\n--username\nAuxVisual\n'
program_file = out/'program.args'
program_file.write_text(program)
snapshot = {}
for folder in (source/'build/classes/java/main', source/'build/resources/main'):
    # ExFAT copy/compile transactions can create metadata siblings that NeoForge
    # otherwise attempts to parse as real class, model and registry files.
    for metadata_file in folder.rglob('._*'):
        if metadata_file.is_file():
            metadata_file.unlink()
    for file in sorted(folder.rglob('*')):
        if file.is_file() and not any(x.startswith('._') or x == '.DS_Store' for x in file.parts):
            snapshot[str(file.relative_to(source/'build'))] = hashlib.sha256(file.read_bytes()).hexdigest()
(out/'compiled-snapshot.json').write_text(json.dumps(snapshot, indent=2)+'\n')
args = [str(java), '-javaagent:'+str(hidden),
        '@'+str(source/'build/moddev/showcaseClientRunVmArgs.txt'), '-Xmx2048m',
        '-cp', metadata['classpath'], *metadata['modArguments'],
        '-Dacademy.qa.showcase=true', '-Dacademy.qa.world=AuxVisual',
        '-Dacademy.qa.aux.run='+run, '-Dacademy.qa.aux.root='+str(out.resolve()),
        '-Dacademy.qa.script='+script, '@'+str(program_file.resolve())]
env = os.environ.copy()
env.update(metadata['environment'])
(out/'request.json').write_text(json.dumps({'run': run, 'hidden': True,
    'items': ids, 'flaskDamageStates': list(range(5)), 'screenshots': shots,
    'droppedItemAngleStages': ['base', 'quarter', 'half'],
    'args': args}, indent=2)+'\n')
print(out.resolve(), flush=True)
log = out/'client.log'
failure = None
code = None
with log.open('w') as stream:
    process = subprocess.Popen(args, cwd=game, env=env, stdout=stream,
        stderr=subprocess.STDOUT, start_new_session=True)
    (out/'client.pid').write_text(str(process.pid))
    deadline = time.monotonic()+a.timeout
    try:
        while process.poll() is None:
            text = log.read_text(errors='replace')
            if 'Unreported exception thrown!' in text or 'Exception caught during firing event' in text or 'ERROR DISPLAY' in text:
                failure = 'native client exception; inspect client.log'
            elif time.monotonic() > deadline:
                failure = 'native client timed out'
            if failure:
                os.killpg(process.pid, signal.SIGTERM)
                break
            time.sleep(.5)
        code = process.wait(timeout=20)
    except BaseException:
        if process.poll() is None:
            os.killpg(process.pid, signal.SIGTERM)
        raise
rows = [json.loads(line) for line in (out/'aux-visual-receipts.jsonl').read_text().splitlines()] if (out/'aux-visual-receipts.jsonl').exists() else []
if any(row.get('run') != run for row in rows):
    failure = 'foreign receipt run'
contexts = [row['data'] for row in rows if row['event'] == 'context']
complete = [row['data'] for row in rows if row['event'] == 'complete']
models = [row['data'] for row in rows if row['event'] == 'models']
angles = [row['data'] for row in rows if row['event'] == 'drop-angle']
angle_keys = {(row['item'], row['angle']) for row in angles}
angle_ok = (len(angles) == 21 and angle_keys == {(item, angle) for item in ids
            for angle in ['base', 'quarter', 'half']})
if angle_ok:
    for item in ids:
        series = [row for row in angles if row['item'] == item]
        angle_ok = angle_ok and ([row['angle'] for row in series] == ['base', 'quarter', 'half']
            and len({row['entity'] for row in series}) == 1
            and all(series[i]['age'] < series[i+1]['age']
                    and series[i]['spinRadians'] < series[i+1]['spinRadians'] for i in range(2)))
missing_shots = [name for name in shots if not (game/'screenshots'/name).is_file()]
changed = []
for name, old in snapshot.items():
    file = source/'build'/name
    if not file.is_file() or hashlib.sha256(file.read_bytes()).hexdigest() != old:
        changed.append(name)
passed = (code == 0 and failure is None and len(complete) == len(models) == 1
          and complete[0]['verifiedContexts'] == 28
          and len(contexts) == 28 and angle_ok and not missing_shots and not changed
          and 'QA_DONE' in log.read_text(errors='replace')
          and 'QA_UNKNOWN_STEP' not in log.read_text(errors='replace'))
result = {'pass': passed, 'run': run, 'exit': code, 'hidden': True,
          'failure': failure, 'renderContexts': len(contexts), 'screenshots': len(shots),
          'missingScreenshots': missing_shots, 'compiledOutputChanged': changed,
          'naturalDropAnglesVerified': angle_ok, 'nativeDropAngles': angles,
          'nativeModels': models[0] if len(models) == 1 else None,
          'visualPixelsReviewed': False,
          'limits': 'Screenshots and native render receipts; separate human/agent pixel review required. No auxiliary behavior test in this fixture.'}
(out/'result.json').write_text(json.dumps(result, indent=2)+'\n')
print('PASS' if passed else 'FAIL', out.resolve(), flush=True)
raise SystemExit(0 if passed else 1)
