#!/usr/bin/env python3
"""Portable unchanged classic Java / modern common terminal differential oracle.
Requires Python 3 and JDK 21. No Gradle, game launch, reference checkout, or staging dependency.
"""
from pathlib import Path
import argparse,hashlib,itertools,json,os,re,shutil,subprocess,sys,tempfile

def run(args,cwd=None):
    process=subprocess.run([str(v) for v in args],cwd=cwd,text=True,capture_output=True)
    if process.returncode:raise AssertionError(process.stdout+process.stderr)
    return process.stdout

def jdk(root):
    candidates=[Path(os.environ['JAVA_HOME'])] if os.environ.get('JAVA_HOME') else []
    if shutil.which('javac'):candidates.append(Path(shutil.which('javac')).resolve().parent.parent)
    for location in [root,Path.cwd(),*root.parents]:
        candidates.extend(sorted((location/'.tools').glob('jdk*')))
    for path in candidates:
        if (path/'bin/javac').is_file() and (path/'bin/java').is_file():return path
    raise AssertionError('JDK 21 required: set JAVA_HOME or put javac/java on PATH')

def source_recipes(source):
    # Independent text parser, then differential check against unchanged LambdaLib RecipeParser.
    cleaned=re.sub(r';[^\r\n]*','',source)
    blocks=re.findall(r'(\w+)\s*\(([^)]*)\)\s*(?:\[[^]]*\])?\s*\{([^}]*)\}',cleaned)
    assert len(blocks)==49,len(blocks)
    result={}
    for index,(kind,out,body) in enumerate(blocks,1):
        if index not in [22,38,39,40]:continue
        parts=out.replace(' ','').split('*');name=parts[0];count=int(parts[1]) if len(parts)>1 else 1
        rows=[[v.strip() for v in row.split(',')] for row in re.findall(r'\[([^]]*)\]',body)]
        assert len({len(row) for row in rows})==1
        result[index]=(kind,name,count,rows)
    return result

def normalize_grid(recipe):
    return [[None if symbol==' ' else recipe['key'][symbol] for symbol in row] for row in recipe['pattern']]

def placements(rows):
    h,w=len(rows),len(rows[0]);result=set()
    for mirrored in [False,True]:
        view=[list(reversed(row)) if mirrored else row for row in rows]
        for y in range(4-h):
            for x in range(4-w):
                grid=[[None]*3 for _ in range(3)]
                for yy in range(h):
                    for xx in range(w):grid[y+yy][x+xx]=view[yy][xx]
                result.add(json.dumps(grid,sort_keys=True))
    return result

def recipes(fixtures,resources,original_lines):
    expected=source_recipes((fixtures/'academycraft/src/main/resources/assets/academy/recipes/default.recipe').read_text())
    lines=[]
    for index,(kind,out,count,rows) in expected.items():lines.append(f'{index}|{kind}|{out}|{count}|{len(rows[0])}|{len(rows)}|'+','.join(itertools.chain.from_iterable(rows)))
    assert original_lines.strip().splitlines()==lines,'independent parser/source RecipeParser differential mismatch'
    mapping=json.loads((fixtures/'recipe-mapping.json').read_text());witnesses=[]
    # Original OreDictionary identifiers are populated generic keys, never their first vanilla member.
    for key,tag in [('plateIron','c:plates/iron'),('blockRedstone','c:storage_blocks/redstone')]:
        assert mapping['ingredients'][key]=={'tag':tag},'original generic OreDictionary token narrowed: '+key
    for index,(kind,out,count,rows) in expected.items():
        item=mapping[out];path=resources/'data/academy/recipe/classic'/f'{item.split(":")[1]}_{index:02d}.json';recipe=json.loads(path.read_text())
        assert recipe['type']=='minecraft:crafting_shaped' and recipe['result']=={'id':item,'count':count},path
        grid=[[mapping['ingredients'][v] for v in row] for row in rows];assert normalize_grid(recipe)==grid,path
        positive=placements(grid);assert placements(normalize_grid(recipe))==positive,path
        # Missing each nonempty ingredient and adding an extra ingredient must reject.
        negative=set()
        for value in positive:
            expanded=json.loads(value)
            for y in range(3):
                for x in range(3):
                    clone=json.loads(value)
                    clone[y][x]=None if expanded[y][x] is not None else {'item':'minecraft:bedrock'}
                    bad=json.dumps(clone,sort_keys=True)
                    assert bad not in positive
                    negative.add(bad)
        witnesses.append({'sourceBlock':index,'result':item,'count':count,'sourceRows':rows,'positive3x3':list(map(json.loads,sorted(positive))),'negative3x3':list(map(json.loads,sorted(negative)))})
        model=json.loads((resources/'assets/academy/models/item'/f'{item.split(":")[1]}.json').read_text());assert model=={'parent':'minecraft:item/generated','textures':{'layer0':'academy:items/'+item.split(':')[1]}}
    frozen=json.loads((fixtures/'recipe-witnesses.json').read_text());assert frozen==witnesses,'recipe witness fixture differs'
    return sum(len(w['positive3x3']) for w in witnesses),sum(len(w['negative3x3']) for w in witnesses)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--common-root',type=Path,help='root containing modern src/main/java and src/main/resources; defaults to this repository')
    parser.add_argument('--reference-only',action='store_true',help='run source originals and recipe/assets checks without modern common source')
    parser.add_argument('--report',type=Path,help='write machine-readable test summary')
    args=parser.parse_args();root=Path(__file__).resolve().parents[1];fixtures=root/'src/test/resources/classic-terminal-source';modern=(args.common_root or root).resolve();resources=modern/'src/main/resources'
    if args.reference_only and args.common_root is None:resources=root/'src/main/resources'
    for resource,digest in json.loads((fixtures/'harness-manifest.json').read_text()).items():
        assert hashlib.sha256((fixtures/resource).read_bytes()).hexdigest()==digest,'finite shim/harness changed: '+resource
    manifest=json.loads((fixtures/'source-manifest.json').read_text())
    for entry in manifest:
        assert hashlib.sha256((fixtures/entry['resource']).read_bytes()).hexdigest()==entry['sha256'],entry['resource']
        if entry['upstream']=='academycraft':assert entry['commit']=='00d19ec0cf538f61c1095c9292f5ee6863db4521'
    for path,digest in json.loads((fixtures/'license-integrity.json').read_text()).items():
        assert hashlib.sha256((fixtures/path).read_bytes()).hexdigest()==digest,'license/notice changed: '+path
    assert (fixtures/'LICENSE-AcademyCraft-GPL3.txt').read_text().count('GNU GENERAL PUBLIC LICENSE')>=1
    assert len((fixtures/'LICENSE-AcademyCraft-GPL3.txt').read_bytes())>30000
    assert 'Permission is hereby granted' in (fixtures/'lambdalib/LICENSE').read_text()
    inventory=json.loads((fixtures/'asset-inventory.json').read_text());excluded=[i for i in inventory if i['classification'].startswith('excluded')];assert len(excluded)==9
    for item in inventory:
        path=modern/item['expectedDestination']
        if item['included'] is True or path.is_file():assert path.is_file() and hashlib.sha256(path.read_bytes()).hexdigest()==item['sha256'],str(path)
    forbidden={i['sha256'] for i in excluded}
    for path in resources.rglob('*'):
        if path.is_file() and path.suffix in ['.png','.ogg']:assert hashlib.sha256(path.read_bytes()).hexdigest() not in forbidden,'excluded third-party asset leaked: '+str(path)
    toolchain=jdk(root)
    with tempfile.TemporaryDirectory(prefix='classic-terminal-oracle-') as temp:
        classes=Path(temp)/'classes';classes.mkdir()
        original=[*sorted((fixtures/'academycraft/src/main/java').rglob('*.java')),*sorted((fixtures/'lambdalib/src/main/java').rglob('*.java')),*sorted((fixtures/'stubs').rglob('*.java')),*sorted((fixtures/'harness').rglob('*.java'))]
        run([toolchain/'bin/javac','-encoding','UTF-8','-d',classes,*original])
        observed=run([toolchain/'bin/java','-cp',classes,'oracle.TerminalSourceOracle'])
        frequency=run([toolchain/'bin/java','-cp',classes,'cn.academy.energy.client.app.FrequencySourceOracle'])
        frequency_assertions=int(frequency.strip().split('|')[-1])
        parsed=run([toolchain/'bin/java','-cp',classes,'cn.lambdalib.crafting.RecipeSourceOracle',fixtures/'academycraft/src/main/resources/assets/academy/recipes/default.recipe'])
        positives,negatives=recipes(fixtures,resources,parsed)
        source_summary=observed.splitlines()[-1].split('|');assert source_summary[2:4]==['120','4200']
        report={'canonicalCommit':'00d19ec0cf538f61c1095c9292f5ee6863db4521','sourceFixtures':len(manifest),'originalRegistrationPermutations':int(source_summary[2]),'originalItemUseObservations':int(source_summary[3]),'originalAssertions':int(source_summary[4]),'recipeBlocks':[22,38,39,40],'recipePositiveWitnesses':positives,'recipeNegativeWitnesses':negatives,'excludedThirdPartyMediaAssets':len(excluded),'modernDifferential':False,'originalFrequencyAssertions':frequency_assertions}
        if not args.reference_only:
            names=['TerminalState','TerminalStorage','TerminalInstallerItem','TerminalAppItem','TerminalInstalledEvent','AppInstalledEvent'];common=modern/'src/main/java/cn/academy/port/terminal'
            sources=[*sorted((fixtures/'modern-stubs').rglob('*.java')),*sorted((fixtures/'modern-harness').rglob('*.java')),*[common/(name+'.java') for name in names]]
            assert all(p.is_file() for p in sources),'missing modern common source root '+str(modern)
            run([toolchain/'bin/javac','-encoding','UTF-8','-d',classes,*sources]);actual=run([toolchain/'bin/java','-cp',classes,'oracle.TerminalModernOracle'])
            a=observed.splitlines();b=actual.splitlines();assert a[:-1]==b[:-1],next((f'item-use differential mismatch: {x}\n{y}' for x,y in zip(a,b) if x!=y),'observation length mismatch')
            modern_summary=b[-1].split('|');report.update(modernDifferential=True,modernAssertions=int(modern_summary[4]),matchedItemUseObservations=len(a)-1,actualModernCommonSources={str(path.relative_to(modern)):hashlib.sha256(path.read_bytes()).hexdigest() for path in sources if path.is_relative_to(modern/'src/main/java')})
    if args.report:args.report.parent.mkdir(parents=True,exist_ok=True);args.report.write_text(json.dumps(report,indent=2)+'\n')
    print(json.dumps(report,indent=2));print('PASS unchanged-source terminal/app common oracle')
if __name__=='__main__':
    try:main()
    except (AssertionError,FileNotFoundError) as e:print('FAIL:',e,file=sys.stderr);sys.exit(1)
