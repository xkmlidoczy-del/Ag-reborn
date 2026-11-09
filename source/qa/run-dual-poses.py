#!/usr/bin/env python3
"""Two native Minecraft clients on loopback LAN, with separate worlds/options and strict observer assertions."""
import hashlib, json, os, pathlib, shutil, subprocess, time, uuid
ROOT=pathlib.Path(__file__).resolve().parents[2]
SOURCE=ROOT/'source'
OUT=ROOT/'qa/output'/('dual-tech-'+str(uuid.uuid4())[:8]); OUT.mkdir(parents=True)
JAVA=pathlib.Path(os.environ.get('ACA_QA_JAVA_HOME',pathlib.Path.home()/'.local/jdks/jdk-21.0.12.1+1/Contents/Home'))/'bin/java'
if 'JAVA_VERSION="21.' not in (JAVA.parents[1]/'release').read_text(): raise SystemExit('Java 21 required')
# The ModDev launch was prepared from the same source set; both clients execute its actual classpath and launch arguments.
metadata=json.loads((SOURCE/'build/moddev/showcaseClientLaunch.json').read_text())
env=os.environ.copy(); env.update(metadata['environment'])
if not metadata['classpath']: raise SystemExit('Run writeShowcaseLaunchMetadata first')
snapshot={'files':{},'mods':{}}
for folder in (SOURCE/'build/classes/java/main',SOURCE/'build/resources/main',SOURCE/'build/classes/java/showcaseQa'):
    for file in sorted(folder.rglob('*')):
        if file.is_file() and not any(part.startswith('._') for part in file.parts):snapshot['files'][str(file.relative_to(SOURCE/'build'))]=hashlib.sha256(file.read_bytes()).hexdigest()
snapshot['mods']={file.name:hashlib.sha256(file.read_bytes()).hexdigest() for file in sorted((SOURCE/'run-showcase-sable/mods').glob('*.jar')) if not file.name.startswith('._')}
(OUT/'compiled-snapshot.json').write_text(json.dumps(snapshot,indent=2)+'\n')
report=OUT/'observer-states.txt'
expected='DISCHARGE,ACCELERATOR,LANCE,RAIL_DEPLOY,FIELD_TUNE,CANOPY,PHASE_ROUTE,PHASE_GATE,VECTOR_CAGE,VECTOR_DOMAIN,FLIGHT,BRAKE,LISTEN,construct-0,construct-2,construct-3,construct-4,construct-left,construct-steer'
expected+=','+','.join('signature-'+category for category in ('electromaster','meltdowner','teleporter','vecmanip'))
expected+=','+','.join('signature-skill-'+skill for skill in ('iron_sand_lance','rail_thunder_array','magnetic_domain','multicore_canopy','multidimensional_landing_array','phase_corridor','vector_cage','zenith_vector_dominion','storm_wing','reverse_jet_brake','circulation_listening','thunder_clap','plasma_cannon'))
expected+=',construct-channel-vector_construct'
processes=[]; streams=[]
REVERSE=os.environ.get('DUAL_REVERSE')=='1'
def extra_props(role):
    if not REVERSE: return []
    if role=='host': return ['-Dacademy.qa.dualRecord=true','-Dacademy.qa.dualReportHost='+str(OUT/'host-seen.txt'),'-Dacademy.qa.dualExpectedHost=LANCE,signature-electromaster,signature-skill-iron_sand_lance']
    return ['-Dacademy.qa.dualScript=true']
def launch(role):
    directory=OUT/role; directory.mkdir()
    opts=dict(line.split(':',1) for line in (SOURCE/'run-showcase/options.txt').read_text().splitlines() if ':' in line)
    opts.update(pauseOnLostFocus='false',maxFps='45',renderDistance='6',simulationDistance='5')
    (directory/'options.txt').write_text(''.join(k+':'+v+'\n' for k,v in opts.items()))
    mods=directory/'mods';mods.mkdir()
    for jar in (SOURCE/'run-showcase-sable/mods').glob('*.jar'):
        if jar.name.startswith('._'): continue
        # Copy bytes only: copy2 also copies macOS extended attributes and can create ExFAT ._ companions.
        with jar.open('rb') as src,(mods/jar.name).open('wb') as dst: shutil.copyfileobj(src,dst)
    for companion in mods.glob('._*'): companion.unlink()
    if REVERSE:
        script=(SOURCE/'qa/live-dual-reverse-host.script' if role=='host' else SOURCE/'qa/live-dual-reverse-observer.script').read_text().strip()
    else:
        script=(SOURCE/'qa/live-dual-tech.script').read_text().strip() if role=='host' else 'quit'
    (OUT/(role+'-script.txt')).write_text(script+'\n')
    args=[str(JAVA),'@'+str(SOURCE/'build/moddev/showcaseClientRunVmArgs.txt'),'-Xmx2048m','-cp',metadata['classpath'],
          *metadata['modArguments'],'-Dacademy.qa.script='+script,'-Dacademy.qa.dual='+role,'-Dacademy.qa.dualReport='+str(report),
          '-Dacademy.qa.dualExpected='+expected,*extra_props(role),'@'+str(SOURCE/'build/moddev/showcaseClientRunProgramArgs.txt'),
          '--username','ACAHost' if role=='host' else 'ACAObserver','--gameDir',str(directory)]
    # gameDir appears once in generated args, so rewrite just that token pair instead of supplying a duplicate option.
    program=(SOURCE/'build/moddev/showcaseClientRunProgramArgs.txt').read_text().replace('--gameDir\n.','--gameDir\n'+str(directory))
    program+='\n--username\n'+('ACAHost' if role=='host' else 'ACAObserver')+'\n'
    pf=OUT/(role+'-program.args'); pf.write_text(program)
    args=args[:args.index('@'+str(SOURCE/'build/moddev/showcaseClientRunProgramArgs.txt'))]+['@'+str(pf)]
    log=OUT/(role+'.log'); stream=log.open('w'); streams.append(stream)
    p=subprocess.Popen(args,cwd=directory,env=env,stdout=stream,stderr=subprocess.STDOUT); processes.append(p)
    return p,log
try:
    wake=subprocess.Popen(['/usr/bin/caffeinate','-d','-u']); processes.append(wake)
    host,hostlog=launch('host'); print('logs:',OUT,flush=True)
    deadline=time.monotonic()+360
    while 'QA_DUAL_PUBLISHED' not in hostlog.read_text(errors='replace'):
        startup=hostlog.read_text(errors='replace')
        if host.poll() is not None or time.monotonic()>deadline or 'Failure details:' in startup or 'Failed to initialize the mod loading system' in startup: raise RuntimeError('host did not publish LAN')
        time.sleep(.5)
    observer,observerlog=launch('observer')
    while host.poll() is None or observer.poll() is None:
        if host.poll() not in (None,0) or observer.poll() not in (None,0):
            raise RuntimeError(f'client failed during actual pose branch host={host.poll()} observer={observer.poll()}')
        if time.monotonic()>deadline: raise RuntimeError('dual run timed out')
        time.sleep(.5)
    if host.returncode!=0 or observer.returncode!=0: raise RuntimeError(f'exit host={host.returncode} observer={observer.returncode}')
    if (not REVERSE and 'QA_DUAL_HOST_PASS' not in hostlog.read_text(errors='replace')) or 'QA_DUAL_DONE' not in observerlog.read_text(errors='replace'): raise RuntimeError('missing live completion markers')
    if REVERSE and 'QA_DUAL_HOST_REVERSE_PASS' not in hostlog.read_text(errors='replace'): raise RuntimeError('host did not see the observer casts')
    states=report.read_text().splitlines()
    for state in ([] if REVERSE else expected.split(','))+['disconnect-clean']:
        if state not in states: raise RuntimeError('missing rendered remote state: '+state)
    (OUT/'result.json').write_text(json.dumps({'kind':'poses_reverse' if REVERSE else 'poses_forward','hostExit':host.returncode,'observerExit':observer.returncode,'actualRemoteStatesVerified':True,'disconnectClean':True},indent=2)+'\n')
    print('PASS native dual-client poses and disconnect cleanup',flush=True)
finally:
    for p in processes:
        if p.poll() is None: p.terminate()
    for p in processes:
        try: p.wait(timeout=10)
        except subprocess.TimeoutExpired: p.kill(); p.wait()
    for f in streams:f.close()
    screenshots=[]
    for role in ('host','observer'):
        for file in sorted((OUT/role/'screenshots').rglob('*.png')):
            if not file.name.startswith('._'):screenshots.append({'file':str(file.relative_to(OUT)),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()})
    (OUT/'screenshots.json').write_text(json.dumps(screenshots,indent=2)+'\n')
