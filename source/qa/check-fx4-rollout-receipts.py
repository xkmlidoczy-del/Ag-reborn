#!/usr/bin/env python3
"""Read-only, manifest-led cast/frame validation; recorder completion alone never passes.

Use --manifest more than once for an intentionally concatenated script. No client,
Gradle, generation, repair or rewriting of raw receipts is performed by this tool.
"""
import argparse
from collections import Counter,defaultdict
from datetime import datetime,timezone
import hashlib
import json
from pathlib import Path
import re
import struct

NEW={'mental_out','dark_matter','nitrogen_armor','aero_hand','hydro_hand','float_dial','liquid_shadow','paper_psychokinesis'}
CLASSIC={'electromaster','meltdowner','teleporter','vecmanip'}
FIRE={'meltdowner','plasma_cannon','railgun','thunder_clap'}
OWN={'railgun','thunder_clap','rail_thunder_array','scatter_bomb','thermal_shell','disintegration_sweep','multicore_canopy',
     'shear_windblade','storm_wing','trajectory_return','vector_cage','multi_axis_reflection','wind_pressure_lift',
     'zenith_vector_dominion','plasma_cannon','vector_construct','multidimensional_landing_array'}

def sha(path):return hashlib.sha256(path.read_bytes()).hexdigest()
def load(path):return json.loads(path.read_text())

def catalog(source):
    result={r['id']:(r['category'],r['level']) for r in load(source/'src/main/resources/classic-skills.json')['skills']}
    result.update({r['id']:(r['category'],r['level']) for r in load(source/'docs/canon/skills.json')})
    text=(source/'src/main/java/cn/academy/port/highpower/HighPowerSkills.java').read_text()
    constants=dict(re.findall(r'\b([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"',text))
    categories={'HighPowerCategories.TELEPORTER':'teleporter','HighPowerCategories.MELTDOWNER':'meltdowner',
                'HighPowerCategories.VECMANIP':'vecmanip','CATEGORY':'electromaster'}
    for category,id_token,level in re.findall(r'new\s+SkillCatalog\.Skill\(\s*([^,]+),\s*([^,]+),\s*(\d+)',text):
        category=categories.get(category.strip(),constants.get(category.strip(),category.strip().strip('"')))
        skill=constants.get(id_token.strip(),id_token.strip().strip('"'));result[skill]=(category,int(level))
    return result

def script_clips(text,errors):
    result=[];active=None
    for step in text.strip().split('|'):
        if step.startswith('fx4qa:begin:'):
            parts=step.split(':');
            if active is not None or len(parts)!=4:errors.append('nested/malformed QA begin: '+step)
            active={'skill':parts[2],'mode':parts[3],'name':None,'synthetic':[],'recended':False}
        elif step.startswith('rec:'):
            if active is None:errors.append('recording has no QA begin: '+step);continue
            if active['name'] is not None:errors.append('multiple rec labels in one QA session')
            active['name']=step[4:]
        elif active is not None and (step.startswith(('fx:','motif:')) or step.startswith('cmd:aca_canon_vfx ')):
            active['synthetic'].append(step)
        elif step=='recend':
            if active is None:errors.append('recend without QA begin')
            else:active['recended']=True
        elif step=='fx4qa:end':
            if active is None:errors.append('QA end without begin');continue
            if active['name'] is None or not active['recended']:errors.append('QA clip missing rec/recend')
            result.append(active);active=None
    if active is not None:errors.append('unterminated QA clip')
    return result

def verify(run,manifests,expect_new=False,expect_classic=False,allow_lifecycle=False,require_paid_new=False):
    errors=[];rows=[];coverage=defaultdict(lambda:defaultdict(set));source=Path(__file__).resolve().parents[1]
    expected=[clip for manifest in manifests for clip in load(manifest)['clips']]
    expected_by_name={c['name']:c for c in expected}
    if len(expected_by_name)!=len(expected):errors.append('duplicate expected labels across manifests')
    raw=load(run/'result.json');request=load(run/'request.json');log=(run/'client.log').read_text(errors='replace')
    for ok,detail in [(raw.get('pass') is True,'recorder pass'),(raw.get('exit')==0,'client exit0'),
        (raw.get('hidden') is True and request.get('hidden') is True,'hidden flags'),
        (any(str(a).startswith('-javaagent:') and str(a).endswith('hidden-window-agent.jar') for a in request.get('args',[])),'explicit hidden agent argument'),
        ('HIDDEN_AGENT:' in log,'hidden agent runtime banner'),('QA_DONE' in log,'QA_DONE'),
        ('QA_UNKNOWN_STEP' not in log,'no unknown driver step'),(not raw.get('compiledOutputChanged'),'compiled output freeze')]:
        if not ok:errors.append(detail+' failed')
    script=(run/'script.txt').read_text();clips=script_clips(script,errors);actual_names=[c['name'] for c in clips]
    if len(actual_names)!=len(set(actual_names)):errors.append('duplicate rec label in recorded script')
    if set(actual_names)!=set(expected_by_name):errors.append('actual rec labels differ from manifest: '+str({'missing':sorted(set(expected_by_name)-set(actual_names)),'extra':sorted(set(actual_names)-set(expected_by_name))}))
    receipts=[json.loads(line) for line in (run/'fx4-receipts.jsonl').read_text().splitlines() if line.strip()]
    tokens={r.get('run') for r in receipts}
    if len(tokens)!=1 or not all(isinstance(t,str) and re.fullmatch(r'[A-Za-z0-9_-]{8,64}',t) for t in tokens):errors.append('foreign/missing receipt run identity')
    args_token=[a.split('=',1)[1] for a in request.get('args',[]) if str(a).startswith('-Dacademy.qa.fx4.run=')]
    if len(args_token)!=1 or tokens!={args_token[0]}:errors.append('receipt run does not match launch argument')
    expected_seq=[(c['skill'],c['mode']) for c in clips]
    begins=[r for r in receipts if r.get('event')=='begin'];ends=[r for r in receipts if r.get('event')=='end']
    for kind,series in [('begin',begins),('end',ends)]:
        if [(r['data'].get('skill'),r['data'].get('mode')) for r in series]!=expected_seq:errors.append(kind+' receipts do not match script sequence')
        if [r['data'].get('clip') for r in series]!=list(range(1,len(clips)+1)):errors.append(kind+' serials not contiguous1..N')
    native_clips={r['name']:r for r in raw.get('clips',[])};catalogue=catalog(source)
    for index,c in enumerate(clips,1):
        problems=[];name=c['name'];intended=expected_by_name.get(name,{})
        if intended.get('skill')!=c['skill'] or intended.get('mode')!=c['mode']:problems.append('manifest skill/mode does not match actual script')
        end=next((r for r in ends if r['data'].get('clip')==index),None);begin=next((r for r in begins if r['data'].get('clip')==index),None)
        data=end['data'] if end else {};begin_data=begin['data'] if begin else {}
        if begin is None or end is None:problems.append('missing begin/end receipt')
        if end and begin and end.get('clientTick',-1)<begin.get('clientTick',0):problems.append('end predates begin')
        category=data.get('category','unknown');level=data.get('skillLevel')
        if catalogue.get(c['skill'])!=(category,level):problems.append('native category/tier differs from current catalogue')
        accepted=False
        visual_rows=[r for r in receipts if r.get('event')=='server-visual' and r.get('data',{}).get('clip')==index and r['data'].get('phase')!='END']
        if len(visual_rows)!=data.get('serverVisuals',-1):problems.append('serverVisuals count does not match actual event rows')
        if c['mode']=='preview':
            if data.get('status')!='visual_preview_only' or data.get('confirmedRealCast') is not False:problems.append('preview promoted to real cast')
            if not c['synthetic']:problems.append('preview mode has no explicitly synthetic visual step')
        elif c['mode']=='real':
            if c['synthetic']:problems.append('synthetic fixture used inside real clip')
            physical=data.get('physicalRDown',0)>0 and data.get('physicalRUp',0)>0
            if category in NEW:
                accepted=physical and len(visual_rows)>0
                end_rows=[r for r in receipts if r.get('event')=='server-visual' and r.get('data',{}).get('clip')==index and r['data'].get('phase')=='END']
                if allow_lifecycle and not visual_rows and end_rows:
                    before_owned=data.get('serverBefore',{}).get('ownedMechanisms',0)
                    after_owned=data.get('serverAfter',{}).get('ownedMechanisms',0)
                    accepted=physical and data.get('paymentEvidence') is True and before_owned>after_owned and data.get('lifecycleEndOnly') is True
                if require_paid_new:accepted=accepted and data.get('paymentEvidence') is True
            elif category in CLASSIC:
                paid=data.get('paymentEvidence') is True;released=data.get('nativeReleasedSignatureFrames',0)>0
                cooldown=data.get('committedCooldownEvidence') is True
                accepted=physical and paid and released and (c['skill'] not in FIRE or cooldown)
            if not accepted or data.get('confirmedRealCast') is not True or data.get('status')!='confirmed_real_cast':problems.append('real input was rejected or lacks required native acceptance evidence')
        else:problems.append('unknown mode')
        frames_dir=run/'game/screenshots/rec'/str(name);files=sorted(frames_dir.glob('f*.png'))
        if not files or [f.name for f in files]!=[f'f{i:05d}.png' for i in range(len(files))]:problems.append('missing/noncontiguous frames')
        native=native_clips.get(name,{})
        if native.get('frames')!=len(files):problems.append('frame count does not match recorder result')
        ends_in_log=re.findall(r'QA_REC_END '+re.escape(str(name))+r' frames=(\d+)',log)
        if len(ends_in_log)!=1 or int(ends_in_log[0])!=len(files):problems.append('frame count does not match unique QA_REC_END')
        hashes={f.name:sha(f) for f in files}
        if hashes!=native.get('frameHashes'):problems.append('native frame bytes changed or snapshot hashes absent')
        for f in files:
            with f.open('rb') as stream:header=stream.read(24)
            if len(header)!=24 or header[:8]!=b'\x89PNG\r\n\x1a\n' or header[12:16]!=b'IHDR' or struct.unpack('>II',header[16:24])!=(1280,720):
                problems.append('not an original1280x720 PNG: '+f.name);break
        if isinstance(level,int):
            coverage['intended'][category].add(level)
            if c['mode']=='preview':coverage['preview'][category].add(level)
            elif accepted:
                coverage['confirmedReal'][category].add(level)
                coverage['confirmedOwn' if c['skill'] in OWN else 'confirmedNonOwn'][category].add(level)
        row={'name':name,'skill':c['skill'],'mode':c['mode'],'category':category,'skillLevel':level,'pass':not problems,'errors':problems,
             'confirmedRealCast':accepted,'recordedStatus':data.get('status'),'frames':len(files),'frameHashes':hashes,
             'begin':begin_data,'end':data,'ownRenderer':c['skill'] in OWN}
        rows.append(row)
    actual_folders={p.name for p in (run/'game/screenshots/rec').iterdir() if p.is_dir() and not p.name.startswith('._')}
    if actual_folders!=set(actual_names):errors.append('frame directories differ from requested clip set')
    covered_new=set(coverage['confirmedReal'])&NEW
    if expect_new and covered_new!=NEW:errors.append('not all8 new categories have confirmed real casts: '+str(sorted(NEW-covered_new)))
    if expect_classic:
        for category in sorted(CLASSIC):
            missing=set(range(1,6))-coverage['confirmedReal'][category]
            if missing:errors.append(category+' missing confirmed skill tiers '+str(sorted(missing)))
    return {'schema':1,'createdAtUtc':datetime.now(timezone.utc).isoformat(),'run':str(run.resolve()),
        'manifests':[{'path':str(p.resolve()),'sha256':sha(p)} for p in manifests],
        'pass':not errors and all(r['pass'] for r in rows),'errors':errors,'clipsExpected':len(expected),'clipsVerified':len(rows),
        'realIntended':sum(c['mode']=='real' for c in clips),'realConfirmed':sum(r['confirmedRealCast'] for r in rows),
        'previews':sum(c['mode']=='preview' for c in clips),'rejectedOrUnconfirmed':[r['name'] for r in rows if r['mode']=='real' and not r['confirmedRealCast']],
        'coverage':{kind:{cat:sorted(tiers) for cat,tiers in sorted(cats.items())} for kind,cats in coverage.items()},
        'rawRecorderPass':raw.get('pass'),'rawVisualPixelsReviewed':raw.get('visualPixelsReviewed'),'visualPixelsReviewedByThisChecker':False,
        'sourceEvidence':{'scriptSha256':sha(run/'script.txt'),'receiptSha256':sha(run/'fx4-receipts.jsonl'),'resultSha256':sha(run/'result.json')},
        'clips':rows,'limits':['This checker reads hashes and actual bounded receiver/ledger/release witnesses;it does not render or judge pixels.',
            'Category/tier counts do not prove every newly added rendering branch;OWN and non-OWN coverage are separate.',
            'No multiplayer, audio, performance,all-skill or alternate graphics-mode acceptance is implied.']}

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True)
    p.add_argument('--allow-lifecycle',action='store_true',help='Accept paid real END-only actions only with an actual owned-mechanism decrease')
    p.add_argument('--require-paid-new',action='store_true',help='Require real CP/nativeCP/clock/cooldown evidence for each new-system action')
    p.add_argument('--manifest',type=Path,action='append',required=True);p.add_argument('--out',type=Path,required=True)
    p.add_argument('--expect-new-categories',action='store_true');p.add_argument('--expect-classic-tiers',action='store_true')
    a=p.parse_args();report=verify(a.run,a.manifest,a.expect_new_categories,a.expect_classic_tiers,a.allow_lifecycle,a.require_paid_new)
    a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({k:report[k] for k in ('pass','clipsVerified','realIntended','realConfirmed','previews','rejectedOrUnconfirmed','errors')},ensure_ascii=False))
    raise SystemExit(0 if report['pass'] else 1)

if __name__=='__main__':main()
