"""Strict hidden native-launch helpers for final story/water acceptance (no builds)."""
import hashlib,json,os,signal,subprocess,time
from pathlib import Path
S=Path(__file__).resolve().parents[1]
R=S.parent
E=R/'qa/evidence/2026-10-30-canon-finalization'

def save(path,value):
    path.write_text(json.dumps(value,ensure_ascii=False,indent=2)+'\n')

def snapshot():
    return {str(f.relative_to(S/'build')):hashlib.sha256(f.read_bytes()).hexdigest()
            for root in (S/'build/classes/java/main',S/'build/resources/main',S/'build/classes/java/showcaseQa')
            for f in sorted(root.rglob('*')) if f.is_file()
            and not any(t.startswith('._') or t=='.DS_Store' for t in f.parts)}

class HiddenLaunch:
    def __init__(self,out,username,props,*,fps=40):
        self.out=out.resolve();self.game=self.out/'game';self.game.mkdir(parents=True)
        self.metadata=json.loads((S/'build/moddev/showcaseClientLaunch.json').read_text())
        self.agent=S/'qa/hidden-window-agent.jar'
        if not self.agent.is_file():raise RuntimeError('Hidden-window agent required; no visible fallback')
        self.before=snapshot();save(self.out/'compiled-snapshot.json',self.before)
        self.java=Path(os.environ.get('ACA_QA_JAVA_HOME',Path.home()/'.local/jdks/jdk-21.0.12.1+1/Contents/Home'))/'bin/java'
        if 'JAVA_VERSION="21.' not in (self.java.parents[1]/'release').read_text():raise RuntimeError('Java21 required')
        self.vm=self.out/'vm.args';vm=(S/'build/moddev/showcaseClientRunVmArgs.txt').read_text()
        self.vm.write_text('\n'.join(x for x in vm.splitlines() if not ('-javaagent:' in x and 'hidden-window-agent.jar' in x))+'\n')
        program=(S/'build/moddev/showcaseClientRunProgramArgs.txt').read_text().replace('--gameDir\n.','--gameDir\n'+str(self.game))
        self.program=self.out/'program.args';self.program.write_text(program+'\n--username\n'+username+'\n')
        (self.game/'options.txt').write_text(f'pauseOnLostFocus:false\nmaxFps:{fps}\nenableVsync:false\nrenderDistance:6\nsimulationDistance:5\nautoJump:false\n')
        self.props=dict(props);self.env=os.environ.copy();self.env.update(self.metadata['environment'])
        self.stage_results=[]
    def run(self,name,script,*,timeout=1200,extra=None):
        if (self.out/(name+'.log')).exists():raise RuntimeError('Refusing to overwrite native log')
        props={**self.props,**(extra or {}),'academy.qa.script':script}
        args=[str(self.java),'-javaagent:'+str(self.agent),'@'+str(self.vm),'-Xmx2560m','-cp',self.metadata['classpath'],*self.metadata['modArguments'],*[f'-D{k}={v}' for k,v in props.items()],'@'+str(self.program)]
        save(self.out/(name+'-request.json'),{'hidden':True,'args':args,'script':script,'properties':props})
        (self.out/(name+'-script.txt')).write_text(script+'\n')
        began=time.time();failure=None;log=self.out/(name+'.log')
        with log.open('w') as stream:
            proc=subprocess.Popen(['/usr/bin/caffeinate','-d','-u',*args],cwd=self.game,env=self.env,stdout=stream,stderr=subprocess.STDOUT,start_new_session=True)
            (self.out/(name+'-pid.txt')).write_text(str(proc.pid)+'\n');deadline=time.monotonic()+timeout
            try:
                while proc.poll() is None:
                    text=log.read_text(errors='replace')
                    if time.monotonic()>deadline:failure='owned native stage timed out'
                    elif any(t in text for t in ('Unreported exception thrown!','Exception caught during firing event','QA_UNKNOWN_STEP','QA_FAIL')):failure='native failure marker; inspect raw log'
                    if failure:os.killpg(proc.pid,signal.SIGTERM);break
                    time.sleep(.5)
                code=proc.wait(timeout=20)
            except BaseException:
                if proc.poll() is None:os.killpg(proc.pid,signal.SIGTERM)
                raise
        text=log.read_text(errors='replace');after=snapshot()
        changed=sorted(n for n in self.before.keys()|after.keys() if self.before.get(n)!=after.get(n))
        result={'stage':name,'exit':code,'hidden':True,'beganEpochMs':int(began*1000),'endedEpochMs':int(time.time()*1000),'failure':failure,'compiledOutputChanged':changed,'hiddenAgentBanner':'HIDDEN_AGENT: GLFW creation visibility/focus disabled' in text,'scriptCompleted':'QA_DONE' in text}
        result['pass']=code==0 and not failure and not changed and result['hiddenAgentBanner'] and result['scriptCompleted']
        save(self.out/(name+'-result.json'),result);self.stage_results.append(result)
        return result
    def worlds(self):
        worlds=[p for p in (self.game/'saves').iterdir() if (p/'level.dat').is_file() and not p.name.startswith('._')]
        if len(worlds)!=1:raise RuntimeError('Require exactly one actual save for cold restart')
        return worlds[0]
    def images(self):
        return [{'file':str(p.relative_to(self.out)),'sha256':hashlib.sha256(p.read_bytes()).hexdigest()}
                for p in sorted((self.game/'screenshots').rglob('*.png')) if not p.name.startswith('._')]

def receipt_rows(path):
    return [json.loads(x) for x in path.read_text().splitlines()] if path.is_file() else []
