#!/usr/bin/env python3
"""Wrap existing real-mechanism gameplay fixtures for all96 active original-four skills.

No original script/assertion is modified. Source segments retain every per-skill
mechanism assertion; original per-fixture verify steps are retained as well. Normal physical keys own tested casts.
"""
import argparse,hashlib,importlib.util,json,re
from pathlib import Path
S=Path(__file__).resolve().parents[1];R=S.parent
spec=importlib.util.spec_from_file_location('complete',S/'qa/build-canon-complete-acceptance.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def setupskill(step):
 match=re.search(r'(?:setup|begin|prepare):([a-z][a-z0-9_]+)$',step)
 return match.group(1) if match else None

def candidates(skill):
 paths=[]
 for p in (S/'qa').glob('*.script'):
  if not p.name.startswith(('gameplay-','codex-gameplay-')) or any(x in p.name for x in ('diagnostic','preflight','cross-dimension')):continue
  text=p.read_text();steps=text.strip().split('|')
  if any(x.startswith(('fx:','motif:','cmd:aca_canon_vfx ')) for x in steps):continue
  if skill=='incident_bypass' and p.name=='gameplay-incident-bypass.script':paths.append((p,steps,0,len(steps)));continue
  indices=[i for i,x in enumerate(steps) if setupskill(x)==skill]
  for start in indices:
   # Only a real fixture setup is an isolation boundary. A stage named begin
   # does not replace the server fixture or its normal input protocol.
   if ':setup:' not in steps[start] and not steps[start].startswith('setup:'):continue
   end=next((i for i in range(start+1,len(steps)) if ':setup:' in steps[i] and setupskill(steps[i])!=skill),len(steps))
   paths.append((p,steps,start,end))
 def score(x):
  p,steps,start,end=x;name=p.name
  return (30 if 'final22' in name else 25 if 'final21' in name else 20 if 'final20' in name else 0)+(15 if 'primary' in name else 0)+(10 if 'retry' in name else 0)+(8 if skill in name else 0),-(end-start)
 return sorted(paths,key=score,reverse=True)

def extract(row,ownroot):
 skill=row['id'];own=ownroot/(skill+'.script')
 if row['ownRenderer'] and own.is_file():
  steps=own.read_text().strip().split('|');steps=[x for x in steps if x!='quit' ]
  manifest=json.loads((ownroot/(skill+'-clips.json')).read_text());clip=manifest['clips'][0]
  steps=[x.replace('cmd:kill @e[type=!minecraft:player]','cmd:kill @e[type=!minecraft:player,type=!academy:displaced_needle]') for x in steps]
  return steps,{**clip,'catalogKey':row['key'],'category':row['category'],'skillLevel':row['level'],'sourceScript':str(own.resolve()),'sourceScriptSha256':sha(own),'actualMechanismAssertionsRetained':True,'ownRenderer':True}
 choices=candidates(skill)
 if not choices:raise ValueError('No real-key gameplay source fixture: '+skill)
 p,steps,start,end=choices[0];same=[x for x in steps if ':setup:' in x and setupskill(x)]
 whole=all(setupskill(x)==skill for x in same) or skill=='incident_bypass'
 selected=steps if whole else steps[start:end]
 selected=[x for x in selected if x!='quit' and not x.startswith(('rec:','mark:begin:','mark:end:')) and x!='recend']
 # Exclude leading language/session setup already supplied by batch, never a
 # gameplay assertion. Every assert/check/verify step in the selected source
 # segment is retained, including redundant final same-fixture passed checks.
 excluded=[];filtered=[]
 for x in selected:
  if x.startswith('lang:'):excluded.append(x);continue
  filtered.append(x)
 selected=filtered
 first=next((i for i,x in enumerate(selected) if x=='keydown:82'),None)
 if first is None:raise ValueError('No ordinary physical R path: '+skill+' '+p.name)
 label='complete_'+skill
 opening=[f'fx4qa:begin:{skill}:real',f'mark:complete:{row["key"]}',f'rec:{label}']
 selected=selected[:first]+opening+selected[first:]+['wait:24','recend','fx4qa:end','gui:on']
 # Fixture setup owns real entities/blocks. Base reset clears only before it.
 reset=[x.replace('cmd:kill @e[type=!minecraft:player]','cmd:kill @e[type=!minecraft:player,type=!academy:displaced_needle]') for x in m.reset()]
 selected=reset+['vcam:off','cam:first']+selected
 clip={'name':label,'skill':skill,'category':row['category'],'skillLevel':row['level'],'catalogKey':row['key'],'mode':'real','origin':row['origin'],'ownRenderer':row['ownRenderer'],'sourceScript':str(p.resolve()),'sourceScriptSha256':sha(p),'sourceSegmentStart':start,'sourceSegmentEnd':end,'wholeOriginalFile':whole,'excludedContextSteps':excluded,'mechanismAssertions':[x for x in filtered if re.search(r':(?:assert|check|verify)(?::|$)',x) or x.startswith(('live:ground:travel','live:ground:stopped','live:ground:sustain','live:storm:','vc:assert'))],'sourceAcceptancePending':True,'sourceReviewed':True,'registeredDisplacedNeedlePreservedAcrossGenericReset':True,'preconditions':['Dedicated pre-existing real server gameplay fixture copied without any direct skill invocation or synthetic VFX.','All per-skill original mechanism assertions preserved; recorder/native release still independently required.']}
 return selected,clip

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--own-root',type=Path,required=True);p.add_argument('--batch-size',type=int,default=8);p.add_argument('--only',default='');a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
 rows=[r for r in m.catalog() if r['controllable'] and r['origin']!='canon'];assert len(rows)==96
 if a.only:rows=[x for x in rows if x['id'] in a.only.split(',')]
 groups={}
 for row in rows:
  profile='sable' if row['id']=='vector_construct' else 'standard';groups.setdefault((row['category'],profile),[]).append(row)
 batches=[];allclips=[]
 for (cat,profile),entries in groups.items():
  for batch,start in enumerate(range(0,len(entries),a.batch_size),1):
   name=f'classic-native-{cat}-{profile}-{batch:02d}';steps=m.HEAD.copy();clips=[]
   for row in entries[start:start+a.batch_size]:
    scene,clip=extract(row,a.own_root);steps+=scene;clips.append(clip);allclips.append(clip)
   out=a.out/(name+'.script');out.write_text('|'.join(steps+['quit'])+'\n');manifest=a.out/(name+'-clips.json');manifest.write_text(json.dumps({'suite':'all-original-four-active-native-mechanisms','batch':name,'profile':profile,'clips':clips,'scriptSha256':sha(out),'notRun':True,'syntheticVisualsAllowed':False,'allSourceMechanismAssertionsRetained':True},ensure_ascii=False,indent=2)+'\n')
   batches.append({'name':name,'profile':profile,'script':str(out.resolve()),'manifest':str(manifest.resolve()),'skills':[x['catalogKey'] for x in clips]})
 (a.out/'batch-index.json').write_text(json.dumps({'actualSkillRecords':len(allclips),'sourceCatalogActiveTotal':96,'ownRendererCount':sum(x['ownRenderer'] for x in allclips),'batches':batches,'status':'PREPARED_NOT_RUN'},ensure_ascii=False,indent=2)+'\n')
 print(json.dumps({'requestedActive':len(allclips),'ownRenderer':sum(x['ownRenderer'] for x in allclips),'batches':len(batches),'out':str(a.out)}))
if __name__=='__main__':main()
