#!/usr/bin/env python3
"""Run one already compiled native client without Gradle; never compiles or changes production classes."""
import argparse,json,os,pathlib,subprocess,hashlib,time,signal,datetime,shutil
p=argparse.ArgumentParser();p.add_argument('name');p.add_argument('--directory',default='run-showcase-sable');p.add_argument('--props',default='');p.add_argument('--out',type=pathlib.Path);p.add_argument('--script',default='wait:20|quit');a=p.parse_args()
s=pathlib.Path(__file__).resolve().parents[1];r=s.parent;out=a.out.resolve() if a.out else r/'qa/evidence/2026-10-22-full-gameplay';out.mkdir(parents=True,exist_ok=True)
driver=s/'build/classes/java/main/cn/academy/port/qa/ShowcaseDriver.class'
if not driver.is_file():
 raise SystemExit('Showcase QA classes are absent. Run ./gradlew -PshowcaseQA=true -PshowHidden=true writeShowcaseLaunchMetadata before the client.')
m=json.loads((s/'build/moddev/showcaseClientLaunch.json').read_text());d=s/a.directory;d.mkdir(exist_ok=True)
(out/(a.name+'-script.txt')).write_text(a.script+'\n')
(out/(a.name+'-properties.txt')).write_text(a.props+'\n')
java=pathlib.Path.home()/'.local/jdks/jdk-21.0.12.1+1/Contents/Home/bin/java'
program=(s/'build/moddev/showcaseClientRunProgramArgs.txt').read_text().replace('--gameDir\n.','--gameDir\n'+str(d));pf=out/(a.name+'-program.args');pf.write_text(program)
args=[str(java),'@'+str(s/'build/moddev/showcaseClientRunVmArgs.txt'),'-Xmx2048m','-cp',m['classpath'],*m['modArguments'],'-Dacademy.qa.script='+a.script]
args += ['-D'+q for q in a.props.split(';') if q];args+=['@'+str(pf)]
env=os.environ.copy();env.update(m['environment']);log=out/(a.name+'.log')
fingerprints={}
for folder in [s/'build/classes/java/main',s/'build/resources/main',s/'build/classes/java/showcaseQa']:
 for f in sorted(folder.rglob('*')):
  if f.is_file() and not any(t.startswith('._') for t in f.parts):fingerprints[str(f.relative_to(s/'build'))]=hashlib.sha256(f.read_bytes()).hexdigest()
mods={f.name:hashlib.sha256(f.read_bytes()).hexdigest() for f in sorted((d/'mods').glob('*.jar')) if not f.name.startswith('._')}
(out/(a.name+'-compiled-snapshot.json')).write_text(json.dumps({'generated_at':datetime.datetime.now().astimezone().isoformat(),'note':'Actual compiled production/QA classes, resources and mods used by this client; no concurrent compile permitted. This does not claim source files were unchanged after compilation.','files':fingerprints,'mods':mods},indent=2)+'\n')
started_at=time.time()
with log.open('w') as stream:
 process=subprocess.Popen(['/usr/bin/caffeinate','-d','-u',*args],cwd=d,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
 deadline=time.monotonic()+900
 fatal_since=None
 try:
  while process.poll() is None:
   text=log.read_text(errors='replace')
   if ('Unreported exception thrown!' in text or 'Exception caught during firing event: QA_' in text) and fatal_since is None:fatal_since=time.monotonic()
   if time.monotonic()>deadline or 'Failed to initialize the mod loading system and display.' in text or (fatal_since is not None and time.monotonic()-fatal_since>15):
    print('Owned client startup/crash/timeout failure; terminating only its process group',flush=True)
    os.killpg(process.pid,signal.SIGTERM)
    try:process.wait(timeout=8)
    except subprocess.TimeoutExpired:os.killpg(process.pid,signal.SIGKILL)
    break
   time.sleep(.5)
  code=process.wait(timeout=10)
 except BaseException:
  if process.poll() is None:os.killpg(process.pid,signal.SIGTERM)
  raise
if 'mixin.debug.export=true' in a.props and (d/'.mixin.out/class').is_dir():
 archive=out/(a.name+'-mixin-export')
 shutil.copytree(d/'.mixin.out/class',archive,dirs_exist_ok=True,ignore=shutil.ignore_patterns('._*'))
screens=[]
if (d/'screenshots').is_dir():
 for f in sorted((d/'screenshots').rglob('*.png')):
  if not f.name.startswith('._') and f.stat().st_mtime>=started_at-1:
   target=out/(a.name+'-screenshots')/f.relative_to(d/'screenshots');target.parent.mkdir(parents=True,exist_ok=True);shutil.copy2(f,target)
   screens.append({'file':str(target.relative_to(r)),'sha256':hashlib.sha256(target.read_bytes()).hexdigest()})
(out/(a.name+'-screenshots.json')).write_text(json.dumps(screens,indent=2)+'\n')
(out/(a.name+'.exit')).write_text(str(code)+'\n');print('exit',code,'log',log)
final_text=log.read_text(errors='replace')
if code!=0 or 'QA_DONE' not in final_text or 'QA_UNKNOWN_STEP' in final_text:raise SystemExit(1)
