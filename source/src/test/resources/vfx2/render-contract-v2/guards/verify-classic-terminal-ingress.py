#!/usr/bin/env python3
"""Execute actual modern terminal network/session/lifecycle and wireless graph with finite boundary shims."""
from pathlib import Path
import argparse,hashlib,json,os,shutil,subprocess,sys,tempfile

def run(args):
    process=subprocess.run([str(v) for v in args],text=True,capture_output=True)
    if process.returncode:raise AssertionError(process.stdout+process.stderr)
    return process.stdout

def jdk(root):
    paths=[Path(os.environ['JAVA_HOME'])] if os.environ.get('JAVA_HOME') else []
    if shutil.which('javac'):paths.append(Path(shutil.which('javac')).resolve().parent.parent)
    for location in [root,Path.cwd(),*root.parents]:paths.extend(sorted((location/'.tools').glob('jdk*')))
    for path in paths:
        if (path/'bin/javac').is_file() and (path/'bin/java').is_file():return path
    raise AssertionError('JDK 21 required: set JAVA_HOME or put javac/java on PATH')

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--common-root',type=Path,help='repository root containing actual modern terminal sources')
    parser.add_argument('--dependency-root',type=Path,help='repository root containing actual wireless graph/ImagFlux interfaces; defaults to common root')
    parser.add_argument('--report',type=Path,help='write JSON test summary and exact modern input hashes')
    args=parser.parse_args();root=Path(__file__).resolve().parents[1];common=(args.common_root or root).resolve();dependencies=(args.dependency_root or common).resolve();fixtures=root/'src/test/resources/classic-terminal-ingress'
    manifest=json.loads((fixtures/'harness-manifest.json').read_text())
    for resource,digest in manifest.items():assert hashlib.sha256((fixtures/resource).read_bytes()).hexdigest()==digest,'shim/harness changed: '+resource
    modern=[common/'src/main/java/cn/academy/port/terminal'/(name+'.java') for name in ['TerminalState','TerminalStorage','TerminalNetwork','TerminalFrequencySessions','TerminalEvents']]
    graph=[dependencies/'src/main/java'/name for name in ['cn/academy/port/wireless/ClassicWirelessGraph.java','cn/academy/port/wireless/ImagFluxNode.java','cn/academy/port/wireless/ImagFluxMatrix.java','cn/academy/port/solar/ImagFluxGenerator.java','cn/academy/port/machine/ImagFluxReceiver.java']]
    inputs=modern+graph
    assert all(path.is_file() for path in inputs),'missing production common sources; select --common-root and --dependency-root'
    # Compile production code, never copied/frozen expected implementations.
    sources=[*sorted((fixtures/'stubs').rglob('*.java')),fixtures/'TerminalIngressOracle.java',*inputs];toolchain=jdk(root)
    with tempfile.TemporaryDirectory(prefix='classic-terminal-ingress-') as temp:
        classes=Path(temp)/'classes';classes.mkdir()
        run([toolchain/'bin/javac','-encoding','UTF-8','-d',classes,*sources])
        result=run([toolchain/'bin/java','-cp',classes,'oracle.TerminalIngressOracle']).strip().split('|')
        assert result[:2]==['SUMMARY','modern-ingress'],result
        report={'modernCommonIngress':True,'testGroups':int(result[2]),'assertions':int(result[3]),'finiteShimJavaFiles':len(list((fixtures/'stubs').rglob('*.java'))),'harnessManifestResources':len(manifest),'actualProductionSources':{str(path.relative_to(common if path in modern else dependencies)):hashlib.sha256(path.read_bytes()).hexdigest() for path in inputs},'nativeMinecraftOrNeoForgeRuntime':False}
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2));print('PASS actual common terminal ingress/session/graph oracle')
if __name__=='__main__':
    try:main()
    except (AssertionError,FileNotFoundError) as e:print('FAIL:',e,file=sys.stderr);sys.exit(1)
