#!/usr/bin/env python3
"""Run two freshly isolated prepared clients; accept only current-run real-network receipts."""
import argparse, hashlib, json, os, pathlib, re, shutil, signal, socket, subprocess, time, uuid

SOURCE=pathlib.Path(__file__).resolve().parents[1]
p=argparse.ArgumentParser(description=__doc__)
p.add_argument('--host-script',type=pathlib.Path,required=True)
p.add_argument('--observer-script',type=pathlib.Path,required=True)
p.add_argument('--scenario',choices=['visual','liquid-module','grounding'],default='visual')
p.add_argument('--skill',required=True,help='actual skill whose matching server-confirmed VFX must reach both clients')
p.add_argument('--phase',default='HIT',choices=['START','ACTIVE','HIT','END'])
p.add_argument('--require-visual',action='append',default=[],metavar='SKILL:PHASE',help='also require this actual event on both clients')
p.add_argument('--host-name',default='CanonHost');p.add_argument('--observer-name',default='CanonGuest')
p.add_argument('--port',type=int,default=0);p.add_argument('--timeout',type=int,default=600)
p.add_argument('--fps',type=int,default=20)
p.add_argument('--capture-all-server-vfx',action='store_true',help='QA only: retain every actual server VFX callback with render-registration flag')
p.add_argument('--float-binding-diagnostic',action='store_true',help='QA only: read original Float effect binding and native interpolation destinations without changing the renderer')
p.add_argument('--mods-dir',type=pathlib.Path,default=SOURCE/'run-showcase-sable/mods')
p.add_argument('--output-base',type=pathlib.Path,default=SOURCE.parent/'qa/output')
a=p.parse_args()
for name in (a.host_name,a.observer_name):
    if not re.fullmatch(r'[A-Za-z0-9_]{3,16}',name):raise SystemExit('Minecraft usernames require 3-16 ASCII letters, digits or underscores')
if a.host_name.lower()==a.observer_name.lower():raise SystemExit('Host and observer usernames must differ')
if not re.fullmatch(r'[a-z0-9_]{1,64}',a.skill):raise SystemExit('invalid skill id')
required_visuals=[]
for item in a.require_visual:
    parts=item.split(':')
    if len(parts)!=2 or not re.fullmatch(r'[a-z0-9_]{1,64}',parts[0]) or parts[1] not in ('START','ACTIVE','HIT','END'):
        raise SystemExit('required visual must be SKILL:START|ACTIVE|HIT|END')
    required_visuals.append(tuple(parts))
if a.port and not 1024<=a.port<=65535:raise SystemExit('invalid port')
if a.timeout<30 or a.timeout>3600:raise SystemExit('timeout must be 30-3600 seconds')
if a.fps<10 or a.fps>120:raise SystemExit('fps must be 10-120')
scripts={role:path.read_text(encoding='utf8').strip() for role,path in [('host',a.host_script),('observer',a.observer_script)]}
for role,script in scripts.items():
    if not script or 'canonformal:done' not in script or 'quit' not in script:raise SystemExit(role+' script must end with canonformal:done and quit')
if 'canonformal:publish' not in scripts['host'] or (a.scenario=='visual' and 'canonformal:rejoin' not in scripts['observer']):
    raise SystemExit('host must publish; visual scenario observer must rejoin')
scripts={role:script.replace('@OBSERVER@',a.observer_name).replace('@HOST@',a.host_name) for role,script in scripts.items()}
if not a.port:
    with socket.socket() as probe:probe.bind(('127.0.0.1',0));a.port=probe.getsockname()[1]
run=uuid.uuid4().hex;a.output_base.mkdir(parents=True,exist_ok=True);out=(a.output_base/('canon-formal-'+run)).resolve();out.mkdir()
java=pathlib.Path(os.environ.get('ACA_QA_JAVA_HOME',pathlib.Path.home()/'.local/jdks/jdk-21.0.12.1+1/Contents/Home'))/'bin/java'
if 'JAVA_VERSION="21.' not in (java.parents[1]/'release').read_text():raise SystemExit('Java 21 required')
hidden=SOURCE/'qa/hidden-window-agent.jar'
if not hidden.is_file():raise SystemExit('hidden-window QA agent is required for every client')
metadata=json.loads((SOURCE/'build/moddev/showcaseClientLaunch.json').read_text())
if not metadata.get('classpath'):raise SystemExit('prepare current showcase launch metadata first')
for cls in ('cn/academy/port/qa/ShowcaseDriver.class','cn/academy/port/qa/CanonFormalClientQa.class','cn/academy/port/client/canon/CanonVfxClient.class'):
    if not (SOURCE/'build/classes/java/main'/cls).is_file() and not (SOURCE/'build/classes/java/showcaseQa'/cls).is_file():raise SystemExit('missing prepared class '+cls)
env=os.environ.copy();env.update(metadata['environment'])
snapshot={}
for folder in (SOURCE/'build/classes/java/main',SOURCE/'build/resources/main',SOURCE/'build/classes/java/showcaseQa'):
    if folder.exists():
        for f in sorted(folder.rglob('*')):
            if f.is_file() and not any(x.startswith('._') for x in f.parts):snapshot[str(f.relative_to(SOURCE/'build'))]=hashlib.sha256(f.read_bytes()).hexdigest()
(out/'compiled-snapshot.json').write_text(json.dumps({'run':run,'files':snapshot},indent=2)+'\n')
(out/'request.json').write_text(json.dumps({'run':run,'port':a.port,'skill':a.skill,'phase':a.phase,'scenario':a.scenario,'experimentalPropertySet':False,'host':a.host_name,'observer':a.observer_name},indent=2)+'\n')
processes={};streams=[]
def launch(role,name):
    directory=out/role;directory.mkdir();mods=directory/'mods';mods.mkdir()
    for f in sorted(a.mods_dir.glob('*.jar')):
        if not f.name.startswith('._'):
            # macOS clone/copyfile can create AppleDouble ._*.jar on ExFAT;
            # NeoForge then attempts to load that metadata as a second ZIP.
            (mods/f.name).write_bytes(f.read_bytes())
    for metadata_file in mods.glob('._*'):
        if metadata_file.is_file():metadata_file.unlink()
    template=SOURCE/'run-showcase/options.txt'
    options=dict(line.split(':',1) for line in template.read_text().splitlines() if ':' in line) if template.exists() else {}
    options.update(pauseOnLostFocus='false',maxFps=str(a.fps),enableVsync='false',renderDistance='6',simulationDistance='5')
    if a.scenario=='grounding':
        (directory/'defaultconfigs').mkdir()
        (directory/'defaultconfigs/academy-server.toml').write_text('attackPlayers = true\n')
    (directory/'options.txt').write_text(''.join(k+':'+v+'\n' for k,v in options.items()))
    (out/(role+'-script.txt')).write_text(scripts[role]+'\n')
    vm=(SOURCE/'build/moddev/showcaseClientRunVmArgs.txt').read_text()
    vm='\n'.join(line for line in vm.splitlines() if not ('-javaagent:' in line and 'hidden-window-agent.jar' in line))
    vf=out/(role+'-vm.args');vf.write_text(vm+'\n')
    program=(SOURCE/'build/moddev/showcaseClientRunProgramArgs.txt').read_text().replace('--gameDir\n.','--gameDir\n'+str(directory))
    program+='\n--username\n'+name+'\n';pf=out/(role+'-program.args');pf.write_text(program)
    args=[str(java),'-javaagent:'+str(hidden),'@'+str(vf),'-Xmx2048m','-cp',metadata['classpath'],*metadata['modArguments'],
          '-Dacademy.qa.script='+scripts[role],'-Dacademy.qa.canon.role='+role,'-Dacademy.qa.canon.run='+run,
          '-Dacademy.qa.canon.root='+str(out),'-Dacademy.qa.canon.port='+str(a.port),'-Dacademy.qa.canon.hostName='+a.host_name,
          *(['-Dacademy.qa.canon.captureAllServerVfx=true'] if a.capture_all_server_vfx else []),
          *(['-Dacademy.qa.canon.floatBinding=true'] if a.float_binding_diagnostic else []),
          *(['-Dacademy.qa.canon.groundingDuel=true'] if a.scenario=='grounding' else []),
          '-Dfml.earlyWindowControl=false',
          '@'+str(pf)]
    stream=(out/(role+'.log')).open('w');streams.append(stream)
    processes[role]=subprocess.Popen(['/usr/bin/caffeinate','-d','-u',*args],cwd=directory,env=env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
def records(role):
    path=out/(role+'-receipts.jsonl')
    if not path.exists():return []
    result=[]
    for line in path.read_text().splitlines():
        try:value=json.loads(line)
        except json.JSONDecodeError:continue # writer may be appending the current line
        if value.get('run')!=run or value.get('role')!=role:raise RuntimeError('foreign or stale receipt')
        result.append(value)
    return result

def validate(host,guest):
    def has(rows,event):return any(x.get('event')==event for x in rows)
    for event in ('published','server-two-players','canon-default','ready','done'):
        if not has(host,event):raise RuntimeError('missing host receipt '+event)
    required=(['joined','ready','disconnect-request','disconnect-clean','rejoined','dimension-change','dimension-clean','done'] if a.scenario=='visual' else ['joined','ready','done'] if a.scenario=='grounding' else ['joined','ready','liquid-observer','done'])
    if not has(guest,'canon-default'):raise RuntimeError('observer did not confirm default-on setting without property')
    for event in required:
        if not has(guest,event):raise RuntimeError('missing observer receipt '+event)
    events=[x['event'] for x in guest]
    indices=[events.index(event) for event in required]
    if indices!=sorted(indices) or len(set(indices))!=len(indices):raise RuntimeError('observer lifecycle receipts out of order')
    ids=next(x['data'] for x in host if x['event']=='server-two-players')
    if set(ids)!={a.host_name,a.observer_name} or ids[a.host_name]==ids[a.observer_name]:raise RuntimeError('server did not observe distinct expected players')
    if a.scenario=='grounding':
        duel={}
        for role in ('host','observer'):
            values=[json.loads(line) for line in (out/(role+'-grounding-receipts.jsonl')).read_text().splitlines()]
            if any(x.get('run')!=run or x.get('role')!=role for x in values):raise RuntimeError('stale native Grounding receipts')
            duel[role]=values
        checked=[x['data'] for x in duel['host'] if x['event']=='check']
        if len(checked)!=1 or not checked[0].get('passed'):raise RuntimeError('actual Grounding spent/precision/unique-damage check did not pass')
        def native(role):return {(d['owner'],d['input'],d['epoch'],d['sequence'],d['phase'],d['point']) for x in duel[role] if x['event']=='native-visual' for d in [x['data']] if d['phase']==1 and d['owner']==ids[a.host_name]}
        common=native('host')&native('observer')
        if not common:raise RuntimeError('no common actual nativeGrounding absorption packet')
        return {'serverPlayers':ids,'nativeGroundingMatched':list(sorted(common))[0],'actualGroundingMechanism':True,'pixelsRequireReview':True}
    def keys(rows,local,skill,phase):
        return {(d.get('owner'),d.get('skill'),d.get('phase'),d.get('dimension'),d.get('origin'),d.get('endpoint'))
                for x in rows if x['event']=='vfx-received' for d in [x['data']]
                if d.get('local') is local and d.get('owner')==ids[a.host_name] and d.get('skill')==skill and d.get('phase')==phase}
    common=keys(host,True,a.skill,a.phase)&keys(guest,False,a.skill,a.phase)
    if not common:raise RuntimeError('no matching host/observer real server VFX receipt for requested skill/phase')
    others=[]
    for skill,phase in required_visuals:
        matches=keys(host,True,skill,phase)&keys(guest,False,skill,phase)
        if not matches:raise RuntimeError('no matching host/observer VFX: '+skill+':'+phase)
        others.append(list(sorted(matches))[0])
    if a.scenario=='liquid-module':
        servers=[x['data'] for x in host if x['event']=='liquid-server']
        clients=[x['data'] for x in host if x['event']=='liquid-client']
        observed=[x['data'] for x in guest if x['event']=='liquid-observer']
        if [x['stage'] for x in servers]!=['none','spyglass','module'] or [x['stage'] for x in clients]!=['none','spyglass','view','exit'] or [x['keyPresses'] for x in clients]!=[1,2,3,4]:
            raise RuntimeError('liquid admission stages missing or out of order')
        if any(x['owner']!=ids[a.host_name] or not x['mediumConsumed'] for x in servers):raise RuntimeError('server owner or medium evidence mismatch')
        if [x['module'] for x in servers]!=[False,False,True] or clients[0]['body']!=servers[0]['body'] or clients[1]['body']!=servers[1]['body'] or any(x['body']!=servers[2]['body'] for x in clients[2:]):
            raise RuntimeError('server/client body or module identity mismatch')
        if len(observed)!=1 or observed[0]['owner']!=ids[a.host_name] or observed[0]['body']!=servers[2]['body'] or not observed[0]['renderer'].endswith('CanonLiquidClient$ShadowRenderer'):
            raise RuntimeError('observer did not receive the real owned module and renderer')
        if 'QA_LIQUID_MOVE advance=' not in (out/'host.log').read_text(errors='replace'):
            raise RuntimeError('real body turn/motion assertion missing')
        return {'serverPlayers':ids,'matchedVisual':list(sorted(common))[0], 'moduleStages':servers,'cameraStages':clients,'observer':observed[0], 'pixelsRequireReview':True}
    def poses(role):
        path=out/(role+'-pose-receipts.jsonl')
        rows=[json.loads(line) for line in path.read_text().splitlines()] if path.exists() else []
        if any(row.get('run')!=run or row.get('role')!=role for row in rows):raise RuntimeError('stale pose receipt')
        return rows
    hostposes=poses('host');guestposes=poses('observer')
    if not any(row.get('event')=='first-person-hand' and row.get('skill')==a.skill and row.get('owner')==ids[a.host_name] and row.get('local') is True for row in hostposes):
        raise RuntimeError('host first-person hand transform not witnessed during real skill')
    if not any(row.get('event')=='third-person-model' and row.get('skill')==a.skill and row.get('owner')==ids[a.host_name] and row.get('local') is False for row in guestposes):
        raise RuntimeError('observer third-person model pose not witnessed during real skill')
    return {'serverPlayers':ids,'matchedVisual':list(sorted(common))[0],'additionalVisuals':others,'firstPersonPose':True,'observerModelPose':True}

try:
    launch('host',a.host_name);print('isolated run:',out,flush=True);deadline=time.monotonic()+a.timeout
    while not any(x['event']=='published' for x in records('host')):
        if processes['host'].poll() is not None or time.monotonic()>deadline:raise RuntimeError('host did not publish')
        if 'ERROR DISPLAY' in (out/'host.log').read_text(errors='replace'):raise RuntimeError('host bootstrap failed; inspect host.log')
        time.sleep(.2)
    launch('observer',a.observer_name)
    while any(p.poll() is None for p in processes.values()):
        if any(p.poll() not in (None,0) for p in processes.values()):raise RuntimeError('one client failed')
        if time.monotonic()>deadline:raise RuntimeError('run timed out')
        # Each positive ally branch emits immediately on acceptance. Bound a
        # missing packet independently of long-soak connected idle intervals.
        if 'canonformal:soak:ally-before-' in scripts['host']:
            checkpoints=[r for r in records('host') if r.get('event')=='soak-checkpoint' and r.get('data',{}).get('stage','').startswith(('ally-before-','ally-after-'))]
            if checkpoints and checkpoints[-1]['data']['stage'].startswith('ally-before-') and time.time()*1000-checkpoints[-1]['time']>10000:
                (out/'ally-missing-packet-watchdog.json').write_text(json.dumps({'lastActualCheckpoint':checkpoints[-1],'waitedMs':time.time()*1000-checkpoints[-1]['time'],'failure':'Positive actual ally action emitted no completion receipt within10seconds; abort only owned native processes and preserve raw.'},indent=2)+'\n')
                raise RuntimeError('required actual ally packet missing after10seconds: '+checkpoints[-1]['data']['stage'])
        time.sleep(.2)
    if any(p.returncode!=0 for p in processes.values()):raise RuntimeError('nonzero client exit')
    for role in ('host','observer'):
        log=(out/(role+'.log')).read_text(errors='replace')
        if 'QA_DONE' not in log or 'QA_UNKNOWN_STEP' in log:raise RuntimeError(role+' script did not finish cleanly')
    after={}
    for folder in (SOURCE/'build/classes/java/main',SOURCE/'build/resources/main',SOURCE/'build/classes/java/showcaseQa'):
        for f in sorted(folder.rglob('*')):
            if f.is_file() and not any(x.startswith('._') for x in f.parts):after[str(f.relative_to(SOURCE/'build'))]=hashlib.sha256(f.read_bytes()).hexdigest()
    changed=sorted(n for n in snapshot.keys()|after.keys() if snapshot.get(n)!=after.get(n))
    if changed:raise RuntimeError('compiled outputs changed during dual run: '+str(changed))
    evidence=validate(records('host'),records('observer'))
    evidence['compiledOutputChanged']=changed
    (out/'result.json').write_text(json.dumps({'status':'PASS','run':run,'evidence':evidence,'visualPixelsReviewed':False},indent=2)+'\n')
    print('PASS two-client receipt and cleanup checks:',out,flush=True)
finally:
    for proc in processes.values():
        if proc.poll() is None:os.killpg(proc.pid,signal.SIGTERM)
    for proc in processes.values():
        try:proc.wait(timeout=10)
        except subprocess.TimeoutExpired:os.killpg(proc.pid,signal.SIGKILL);proc.wait()
    for stream in streams:stream.close()
    shots=[]
    for role in ('host','observer'):
        for file in sorted((out/role/'screenshots').rglob('*.png')):
            if not file.name.startswith('._'):shots.append({'file':str(file.relative_to(out)),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()})
    (out/'screenshots.json').write_text(json.dumps(shots,indent=2)+'\n')
