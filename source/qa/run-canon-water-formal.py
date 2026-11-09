#!/usr/bin/env python3
"""Hidden native two-press water transfer and same-save cold restart verification."""
import pathlib,json,subprocess,os,time,uuid,hashlib,signal
s=pathlib.Path(__file__).resolve().parents[1];out=s.parent/'qa/output'/('water-formal-'+uuid.uuid4().hex);game=out/'game';game.mkdir(parents=True)
(game/'options.txt').write_text('pauseOnLostFocus:false\nmaxFps:20\nrenderDistance:5\nsimulationDistance:5\nautoJump:false\n')
m=json.loads((s/'build/moddev/showcaseClientLaunch.json').read_text());java=pathlib.Path.home()/'.local/jdks/jdk-21.0.12.1+1/Contents/Home/bin/java';agent=s/'qa/hidden-window-agent.jar'
if not agent.is_file():raise SystemExit('Hidden agent required; no visible fallback')
pf=out/'program.args';pf.write_text((s/'build/moddev/showcaseClientRunProgramArgs.txt').read_text().replace('--gameDir\n.','--gameDir\n'+str(game))+'\n--username\nWaterFormal\n')
base=[str(java),'-javaagent:'+str(agent),'@'+str(s/'build/moddev/showcaseClientRunVmArgs.txt'),'-Xmx2048m','-cp',m['classpath'],*m['modArguments'],'-Dacademy.qa.showcase=true','-Dacademy.qa.world=WaterFormal']
env=os.environ.copy();env.update(m['environment']);print(out,flush=True)
(out/'compiled-snapshot.json').write_text(json.dumps({str(f.relative_to(s/'build')):hashlib.sha256(f.read_bytes()).hexdigest() for root in [s/'build/classes/java/main',s/'build/resources/main'] for f in sorted(root.rglob('*')) if f.is_file() and not any(t.startswith('._') for t in f.parts)},indent=2)+'\n')
result={}
for name,script in [('transfer','wait:40|canonwater:setup|wait:35|canonwater:source|wait:5|canonwater:target|wait:5|canonwater:check|shot:water-transfer|quit'),('restart','wait:40|canonwater:restart-check|quit')]:
 (out/(name+'-script.txt')).write_text(script+'\n');log=out/(name+'.log')
 with log.open('w') as stream:
  process=subprocess.Popen(base+['-Dacademy.qa.script='+script,'@'+str(pf)],cwd=game,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
  (out/'client.pid').write_text(str(process.pid));deadline=time.monotonic()+300
  while process.poll() is None:
   t=log.read_text(errors='replace')
   if time.monotonic()>deadline or 'Unreported exception thrown!' in t or 'Exception caught during firing event' in t:os.killpg(process.pid,signal.SIGTERM);break
   time.sleep(.5)
  code=process.wait(timeout=20)
 rows=[json.loads(x) for x in (game/'water-formal-receipts.jsonl').read_text().splitlines()] if (game/'water-formal-receipts.jsonl').exists() else []
 event='complete' if name=='transfer' else 'restart-verified';passed=code==0 and any(x['event']==event for x in rows) and 'QA_DONE' in log.read_text(errors='replace');result[name]={'pass':passed,'exit':code}
 if not passed:break
result['pass']=len(result)==2 and all(x['pass'] for x in result.values());result['hidden']=True;(out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print(result,flush=True)
raise SystemExit(0 if result['pass'] else 1)
