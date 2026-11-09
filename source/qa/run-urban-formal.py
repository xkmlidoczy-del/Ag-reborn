#!/usr/bin/env python3
"""Run a fresh, hidden native client against an unmodified generated story world."""
import argparse,json,os,pathlib,subprocess,time,uuid,hashlib,signal
p=argparse.ArgumentParser(description=__doc__);p.add_argument('route',choices=['ABILITY','EQUIPMENT','COMBAT']);p.add_argument('--resume',type=pathlib.Path);p.add_argument('--timeout',type=int,default=1200);a=p.parse_args()
s=pathlib.Path(__file__).resolve().parents[1];out=a.resume.resolve() if a.resume else s.parent/'qa/output'/('urban-formal-'+a.route.lower()+'-'+uuid.uuid4().hex);out.mkdir(parents=True,exist_ok=bool(a.resume));game=out/'game';game.mkdir(exist_ok=bool(a.resume))
(game/'options.txt').write_text('pauseOnLostFocus:false\nmaxFps:40\nrenderDistance:6\nsimulationDistance:5\nautoJump:false\n')
m=json.loads((s/'build/moddev/showcaseClientLaunch.json').read_text());java=pathlib.Path.home()/'.local/jdks/jdk-21.0.12.1+1/Contents/Home/bin/java';agent=s/'qa/hidden-window-agent.jar'
if not agent.is_file():raise SystemExit('Hidden-window renderer required; no visible fallback is allowed')
script='wait:40|urbanformal:setup:'+a.route+'|urbanformal:run|urbanformal:check|shot:story-'+a.route.lower()+'-complete|mark:URBAN_FORMAL_COMPLETE|quit'
program=(s/'build/moddev/showcaseClientRunProgramArgs.txt').read_text().replace('--gameDir\n.','--gameDir\n'+str(game));program+='\n--username\nStory'+a.route.title()+'\n';pf=out/'program.args';pf.write_text(program)
args=[str(java),'-javaagent:'+str(agent),'@'+str(s/'build/moddev/showcaseClientRunVmArgs.txt'),'-Xmx2560m','-cp',m['classpath'],*m['modArguments'],'-Dacademy.qa.script='+script,'-Dacademy.qa.showcase=true','-Dacademy.qa.normalWorld=true','-Dacademy.qa.seed=251027','@'+str(pf)]
if a.resume:
 worlds=[d for d in (game/'saves').iterdir() if (d/'level.dat').is_file()]
 if len(worlds)!=1:raise SystemExit('resume requires one existing save')
 args.insert(-1,'-Dacademy.qa.world='+worlds[0].name);args.insert(-1,'-Dacademy.qa.urban.resume=true')
 for f in ['client.log','compiled-snapshot.json','request.json']:
  old=out/f
  if old.exists():old.rename(out/(f+'.previous-'+str(int(time.time()))))
args.insert(-1,'-Dacademy.qa.urban.directory='+str(out))
env=os.environ.copy();env.update(m['environment']);(out/'request.json').write_text(json.dumps({'route':a.route,'hidden':True,'script':script,'args':args},indent=2)+'\n')
hashes={}
for root in (s/'build/classes/java/main',s/'build/resources/main'):
 for f in sorted(root.rglob('*')):
  if f.is_file() and not any(x.startswith('._') for x in f.parts):hashes[str(f.relative_to(s/'build'))]=hashlib.sha256(f.read_bytes()).hexdigest()
(out/'compiled-snapshot.json').write_text(json.dumps(hashes,indent=2)+'\n')
log=out/'client.log';print(out,flush=True)
with log.open('w') as stream:
 proc=subprocess.Popen(args,cwd=game,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True);(out/'client.pid').write_text(str(proc.pid));deadline=time.monotonic()+a.timeout
 try:
  while proc.poll() is None:
   text=log.read_text(errors='replace')
   if time.monotonic()>deadline or 'Unreported exception thrown!' in text or 'Exception caught during firing event' in text:
    os.killpg(proc.pid,signal.SIGTERM);break
   time.sleep(.5)
  code=proc.wait(timeout=15)
 except BaseException:
  if proc.poll() is None:os.killpg(proc.pid,signal.SIGTERM)
  raise
rows=[json.loads(x) for x in (game/'urban-formal-receipts.jsonl').read_text().splitlines()] if (game/'urban-formal-receipts.jsonl').exists() else []
passed=code==0 and any(x['event']=='complete' and x['completed']==14 for x in rows) and 'QA_DONE' in log.read_text(errors='replace')
restart_pass=False
if passed:
 worlds=[d for d in (game/'saves').iterdir() if (d/'level.dat').is_file()]
 if len(worlds)!=1:raise SystemExit('restart requires exactly one generated save')
 restart_args=[q for q in args if not q.startswith('-Dacademy.qa.script=') and not q.startswith('-Dacademy.qa.world=')]
 restart_args.insert(-1,'-Dacademy.qa.script=wait:40|urbanformal:restart-check:'+a.route+'|mark:URBAN_RESTART_COMPLETE|quit')
 restart_args.insert(-1,'-Dacademy.qa.world='+worlds[0].name)
 with (out/'restart.log').open('w') as stream:
  proc=subprocess.Popen(restart_args,cwd=game,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
  try:restart_code=proc.wait(timeout=240)
  except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGTERM);restart_code=proc.wait(timeout=15)
 restart_rows=[json.loads(x) for x in (game/'urban-formal-receipts.jsonl').read_text().splitlines()]
 restart_pass=restart_code==0 and any(x['event']=='restart-verified' and x['completed']==14 for x in restart_rows)
 passed=passed and restart_pass
(out/'result.json').write_text(json.dumps({'pass':passed,'exit':code,'route':a.route,'objectiveReceipts':sum(x['event']=='objective-complete' for x in rows),'restart':restart_pass,'hidden':True},indent=2)+'\n')
print('PASS' if passed else 'FAIL',out,flush=True)
raise SystemExit(0 if passed else 1)
