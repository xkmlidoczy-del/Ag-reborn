#!/usr/bin/env python3
"""Real two-client opt-in branches. Reads prepared ModDev metadata; never builds or starts Gradle."""
import argparse,hashlib,json,os,pathlib,shutil,subprocess,time,uuid

def main():
    cli=argparse.ArgumentParser();cli.add_argument('kind',choices=('pincer','tactical','domain','grounding'));cli.add_argument('--out',type=pathlib.Path);cli.add_argument('--timeout',type=int,default=420);cli.add_argument('--final-visual',action='store_true');args=cli.parse_args()
    root=pathlib.Path(__file__).resolve().parents[2];source=root/'source'
    out=(args.out or root/'qa/output'/('dual-consent-'+args.kind+'-'+str(uuid.uuid4())[:8])).resolve();out.mkdir(parents=True,exist_ok=False)
    metadata=json.loads((source/'build/moddev/showcaseClientLaunch.json').read_text())
    if not metadata['classpath']:raise RuntimeError('prepared showcase metadata required')
    snapshot={'metadataSha256':hashlib.sha256((source/'build/moddev/showcaseClientLaunch.json').read_bytes()).hexdigest()}
    for target in ('main','showcaseQa'):
        base=source/'build/classes/java'/target;digest=hashlib.sha256();count=0
        for file in sorted(base.rglob('*.class')):
            if file.name.startswith('._'):continue
            digest.update(str(file.relative_to(base)).encode());digest.update(file.read_bytes());count+=1
        snapshot[target]={'classes':count,'aggregateSha256':digest.hexdigest()}
    snapshot['files']={}
    for folder in (source/'build/classes/java/main',source/'build/resources/main',source/'build/classes/java/showcaseQa'):
        for file in sorted(folder.rglob('*')):
            if file.is_file() and not any(part.startswith('._') for part in file.parts):snapshot['files'][str(file.relative_to(source/'build'))]=hashlib.sha256(file.read_bytes()).hexdigest()
    snapshot['mods']={file.name:hashlib.sha256(file.read_bytes()).hexdigest() for file in sorted((source/'run-showcase-sable/mods').glob('*.jar')) if not file.name.startswith('._')}
    (out/'compiled-snapshot.json').write_text(json.dumps(snapshot,indent=2)+'\n')
    java_home=pathlib.Path(os.environ.get('ACA_QA_JAVA_HOME',pathlib.Path.home()/'.local/jdks/jdk-21.0.12.1+1/Contents/Home'))
    if 'JAVA_VERSION="21.' not in (java_home/'release').read_text():raise RuntimeError('Java 21 required')
    processes=[];streams=[];deadline=time.monotonic()+args.timeout;signal_dir=out/'signals'
    def launch(role):
        directory=out/role;directory.mkdir()
        options=dict(line.split(':',1)for line in(source/'run-showcase/options.txt').read_text().splitlines()if ':' in line)
        options.update(pauseOnLostFocus='false',maxFps='45',renderDistance='6',simulationDistance='5')
        (directory/'options.txt').write_text(''.join(k+':'+v+'\n'for k,v in options.items()))
        mods=directory/'mods';mods.mkdir()
        for jar in(source/'run-showcase-sable/mods').glob('*.jar'):
            if jar.name.startswith('._'):continue
            with jar.open('rb')as src,(mods/jar.name).open('wb')as dst:shutil.copyfileobj(src,dst)
        # ExFAT may materialize an AppleDouble companion even for a streamed copy.
        # NeoForge scans ._*.jar as archives, so remove generated companions before launch.
        for junk in directory.rglob('._*'):
            if junk.is_file():junk.unlink()
        script=(source/f'qa/consent-{args.kind}-{role}.script').read_text().strip()
        if ('|ground:' if args.kind=='grounding' else '|consent:') not in '|'+script:raise RuntimeError('actual participant script required')
        (out/(role+'-script.txt')).write_text(script+'\n')
        program=(source/'build/moddev/showcaseClientRunProgramArgs.txt').read_text().replace('--gameDir\n.','--gameDir\n'+str(directory))
        program+='\n--username\n'+('ACAHost'if role=='host'else'ACAObserver')+'\n'
        pf=out/(role+'-program.args');pf.write_text(program)
        props=['-Dacademy.qa.script='+script,'-Dacademy.qa.dual='+role,'-Dacademy.qa.consentKind='+args.kind,'-Dacademy.qa.consentDir='+str(signal_dir),'-Dacademy.qa.dualReport='+str(out/'observer-states.txt')]
        if args.final_visual and role=='host':props+=['-Dacademy.qa.finalVisual=true']
        if role=='observer':props+=['-Dacademy.qa.dualScript=true']
        env=os.environ.copy();env.update(metadata['environment'])
        command=[str(java_home/'bin/java'),'@'+str(source/'build/moddev/showcaseClientRunVmArgs.txt'),'-Xmx2048m','-cp',metadata['classpath'],*metadata['modArguments'],*props,'@'+str(pf)]
        log=out/(role+'.log');stream=log.open('w');streams.append(stream)
        process=subprocess.Popen(command,cwd=directory,env=env,stdout=stream,stderr=subprocess.STDOUT);processes.append(process);return process,log
    try:
        wake=subprocess.Popen(['/usr/bin/caffeinate','-d','-u']);processes.append(wake)
        host,hostlog=launch('host');print('dual consent logs:',out,flush=True)
        while 'QA_DUAL_PUBLISHED'not in hostlog.read_text(errors='replace'):
            startup=hostlog.read_text(errors='replace')
            if host.poll()is not None or time.monotonic()>deadline or 'Failure details:' in startup or 'Failed to initialize the mod loading system' in startup:raise RuntimeError('host failed to publish actual LAN')
            time.sleep(.5)
        observer,observerlog=launch('observer')
        while host.poll()is None or observer.poll()is None:
            if host.poll() not in (None,0) or observer.poll() not in (None,0):
                raise RuntimeError(f'client failed during actual consent branch host={host.poll()} observer={observer.poll()}')
            if time.monotonic()>deadline:raise RuntimeError('actual consent branch timed out')
            time.sleep(.5)
        host_text=hostlog.read_text(errors='replace');observer_text=observerlog.read_text(errors='replace')
        if host.returncode or observer.returncode:raise RuntimeError(f'client exits host={host.returncode} observer={observer.returncode}')
        required=['QA_DUAL_GROUNDING_PASS round=late','QA_DUAL_GROUNDING_PASS round=precise','QA_DUAL_GROUNDING_DONE','QA_DONE'] if args.kind=='grounding' else ['QA_DUAL_CONSENT stage=PASS','QA_DUAL_CONSENT stage=DONE','QA_DONE']
        if args.kind=='domain':required+=['QA_DUAL_CONSENT stage=OFF_PASS']
        for marker in required:
            if marker not in host_text:raise RuntimeError('missing actual host server proof '+marker)
        if args.final_visual and args.kind=='grounding' and 'QA_FINAL_VISUAL_PASS skill=grounding_return' not in host_text:raise RuntimeError('grounding final actual visual witness absent')
        if 'QA_DUAL_DONE'not in observer_text or 'disconnect-clean'not in(out/'observer-states.txt').read_text():raise RuntimeError('actual observer disconnect cleanup absent')
        result={'kind':args.kind,'hostExit':host.returncode,'observerExit':observer.returncode,'actualServerProof':True,'actualRecipientUi':args.kind not in ('domain','grounding'),'actualRecipientCommand':args.kind!='grounding','output':str(out)}
        (out/'result.json').write_text(json.dumps(result,indent=2)+'\n');print('PASS actual native dual-client consent branch',args.kind,flush=True)
    finally:
        for process in processes:
            if process.poll()is None:process.terminate()
        for process in processes:
            try:process.wait(timeout=10)
            except subprocess.TimeoutExpired:process.kill();process.wait()
        for stream in streams:stream.close()
        screenshots=[]
        for role in ('host','observer'):
            for file in sorted((out/role/'screenshots').glob('*.png')):
                if not file.name.startswith('._'):screenshots.append({'file':str(file.relative_to(out)),'sha256':hashlib.sha256(file.read_bytes()).hexdigest()})
        (out/'screenshots.json').write_text(json.dumps(screenshots,indent=2)+'\n')

if __name__=='__main__':main()
