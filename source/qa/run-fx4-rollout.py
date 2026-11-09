#!/usr/bin/env python3
"""Record an isolated native FX4 run from frozen compiled outputs, always hidden."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import signal
import subprocess
import time
import uuid

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('name');p.add_argument('script',type=Path)
p.add_argument('--root',type=Path)
p.add_argument('--timeout',type=int,default=1200)
p.add_argument('--shaders',choices=('on','off'),default='on')
p.add_argument('--camera',choices=('on','off'),default='on')
p.add_argument('--max-fps',type=int,default=60)
a=p.parse_args()
s=Path(__file__).resolve().parents[1]
base=a.root or s.parent/'qa/evidence/2026-10-30-fx4-rollout/runs'
out=base/(a.name+'-'+uuid.uuid4().hex[:12]);out.mkdir(parents=True)
game=out/'game';game.mkdir();(game/'config').mkdir()
hidden=s/'qa/hidden-window-agent.jar'
assert hidden.is_file(),'No foreground fallback is allowed'
metadata=json.loads((s/'build/moddev/showcaseClientLaunch.json').read_text())
script=a.script.read_text().strip()
(out/'script.txt').write_text(script+'\n')
(game/'options.txt').write_text('pauseOnLostFocus:false\nrenderDistance:5\nsimulationDistance:5\n'
    f'maxFps:{a.max_fps}\nenableVsync:false\nguiScale:2\nlang:zh_cn\nfov:0.0\ntutorialStep:none\n')
on=a.camera=='on'
(game/'config/academy-canon-fx-client.toml').write_text(
    f'shaders = {str(a.shaders=="on").lower()}\ncameraShake = {1.0 if on else 0.0}\n'
    f'hitFreeze = {str(on).lower()}\nfovKick = {str(on).lower()}\nscreenEffects = {str(on).lower()}\n')
vm=(s/'build/moddev/showcaseClientRunVmArgs.txt').read_text()
vm='\n'.join(line for line in vm.splitlines() if not ('-javaagent:' in line and 'hidden-window-agent.jar' in line))
(out/'vm.args').write_text(vm+'\n')
program=(s/'build/moddev/showcaseClientRunProgramArgs.txt').read_text().replace('--gameDir\n.','--gameDir\n'+str(game.resolve()))
program+='\n--username\nFx4Rollout\n';(out/'program.args').write_text(program)
folders=(s/'build/classes/java/main',s/'build/resources/main')
def snapshot():
    return {str(f.relative_to(s/'build')):hashlib.sha256(f.read_bytes()).hexdigest()
        for folder in folders for f in sorted(folder.rglob('*'))
        if f.is_file() and not any(x.startswith('._') or x=='.DS_Store' for x in f.parts)}
for folder in folders:
    for f in folder.rglob('._*'):
        if f.is_file():f.unlink(missing_ok=True)
before=snapshot();(out/'compiled-snapshot.json').write_text(json.dumps(before,indent=2)+'\n')
java=Path.home()/'.local/jdks/jdk-21.0.12.1+1/Contents/Home/bin/java'
args=[str(java),'-javaagent:'+str(hidden),'@'+str(out/'vm.args'),'-Xmx2048m',
    '-cp',metadata['classpath'],*metadata['modArguments'],
    '-Dacademy.qa.showcase=true','-Dacademy.visual.qa=true',
    '-Dacademy.qa.fx4.root='+str(out.resolve()),'-Dacademy.qa.world=Fx4Rollout',
    '-Dacademy.qa.fx4rollout=true','-Dacademy.qa.fx4.run='+uuid.uuid4().hex,
    '-Dacademy.qa.fx4.metrics=true',
    '-Dacademy.qa.script='+script,'@'+str(out/'program.args')]
env=os.environ.copy();env.update(metadata['environment'])
(out/'request.json').write_text(json.dumps({'hidden':True,'script':str(a.script.resolve()),
    'shaders':a.shaders,'camera':a.camera,'args':args},indent=2)+'\n')
print(out.resolve(),flush=True)
failure=None;log=out/'client.log'
with log.open('w') as stream:
    # Keep the display service available for hidden OpenGL, as the existing prepared-client runner does.
    process=subprocess.Popen(['/usr/bin/caffeinate','-d','-u',*args],cwd=game,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
    (out/'client.pid').write_text(str(process.pid));deadline=time.monotonic()+a.timeout
    try:
        while process.poll() is None:
            value=log.read_text(errors='replace')
            if 'Unreported exception thrown!' in value or 'Failed to initialize the mod loading system' in value:
                failure='Native client crashed; inspect client.log'
            elif time.monotonic()>deadline:failure='Owned client exceeded timeout'
            if failure:
                os.killpg(process.pid,signal.SIGTERM);break
            time.sleep(.5)
        code=process.wait(timeout=15)
    except BaseException:
        if process.poll() is None:os.killpg(process.pid,signal.SIGTERM)
        raise
after=snapshot();changed=sorted(n for n in before.keys()|after.keys() if before.get(n)!=after.get(n))
clips=[]
for directory in sorted((game/'screenshots/rec').glob('*')):
    if not directory.is_dir() or directory.name.startswith('._'):continue
    frames=sorted(directory.glob('f*.png'))
    clips.append({'name':directory.name,'frames':len(frames),'first':str(frames[0]) if frames else None,
        'last':str(frames[-1]) if frames else None,
        'frameHashes':{f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in frames}})
text=log.read_text(errors='replace')
passed=code==0 and failure is None and not changed and 'QA_DONE' in text and 'QA_UNKNOWN_STEP' not in text
result={'pass':passed,'exit':code,'hidden':True,'failure':failure,'compiledOutputChanged':changed,
    'clips':clips,'screenshots':sum(row['frames'] for row in clips),'visualPixelsReviewed':False,
    'limits':'Recorder completion does not prove a successful cast or visual acceptance. Check per-clip receipts and review native frames separately.'}
(out/'result.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps({k:v for k,v in result.items() if k!='clips'}),flush=True)
raise SystemExit(0 if passed else 1)
