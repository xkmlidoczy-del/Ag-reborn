#!/usr/bin/env python3
"""Aggregate exact305 source catalog records without promoting previews or stale renders.

Native report rows may be selected from a fully finished run whose other clips
were rejected; each selected row must independently pass. Actual LAN teammate
proofs and native GUI/passive behavior proofs are separate. An optional final
production snapshot rejects stale client render classes from current coverage.
"""
import argparse,hashlib,importlib.util,json,re,struct,zipfile
from collections import Counter
from datetime import datetime,timezone
from decimal import Decimal
from pathlib import Path
S=Path(__file__).resolve().parents[1]
spec=importlib.util.spec_from_file_location('full_catalog',S/'qa/build-canon-complete-acceptance.py');m=importlib.util.module_from_spec(spec);spec.loader.exec_module(m)
EXCLUDE=('cn/academy/port/qa/','cn/academy/port/gametest/','cn/academy/port/client/MagneticDomainQaFixture','cn/academy/port/highpower/ZeroGapArmorQaFixture','cn/academy/port/highpower/VectorParryQaObserver')
def load(p):return json.loads(p.read_text())
def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def production_snapshot(path):
 x=load(path);x=x.get('files',x);out={}
 for k,v in x.items():
  prefix=next((p for p in ['classes/java/main/','resources/main/'] if k.startswith(p)),None)
  if prefix is None:continue
  name=k[len(prefix):]
  if name.startswith(EXCLUDE) or any(p.startswith('._') or p=='.DS_Store' for p in name.split('/')):continue
  out[k]=v
 return out

def runtime_match(run,reference):
 path=run/'compiled-snapshot.json'
 if not path.is_file():return False,['missing compiled snapshot']
 if reference is None:return False,["reference_snapshot_not_supplied"]
 actual=production_snapshot(path);changed=sorted(k for k in actual.keys()|reference.keys() if actual.get(k)!=reference.get(k))
 return not changed,changed

# Opt-in only: this reviewed motion repair changes one owner, never a shared
# render/network/progression/payment class. Unknown future changes stay strict.
MOTION_OWNER_CLASSES={
 'classes/java/main/cn/academy/port/canon/CanonExpansionCombatA.class',
 'classes/java/main/cn/academy/port/canon/CanonExpansionCombatA$Context.class',
 'classes/java/main/cn/academy/port/canon/CanonExpansionCombatA$ItemImpulse.class',
 'classes/java/main/cn/academy/port/canon/CanonExpansionCombatA$PushPlan.class',
 'classes/java/main/cn/academy/port/canon/CanonExpansionCombatA$StrikeShape.class'}
MOTION_RETEST={'mental_out','dark_matter','nitrogen_armor','aero_hand'}
MOTION_REUSE={'electromaster','meltdowner','teleporter','vecmanip','hydro_hand','paper_psychokinesis','liquid_shadow','float_dial'}
DARK_RENDER_CLASS='classes/java/main/cn/academy/port/client/canon/CanonVfxClient.class'
DARK_RENDER_SWITCH_CLASS='classes/java/main/cn/academy/port/client/canon/CanonVfxClient$1.class'
MENTAL_OWNER_CLASS='classes/java/main/cn/academy/port/canon/CanonExpansionCombatA.class'
FINAL_PENDING_CLASS='classes/java/main/cn/academy/port/client/canon/CanonVfxClient$Pending.class'
FINAL_PATCH_CLASSES={MENTAL_OWNER_CLASS,DARK_RENDER_CLASS,DARK_RENDER_SWITCH_CLASS,FINAL_PENDING_CLASS}

def load_policies(motion_path,render_path,reference,final_path=None):
 # A separately audited final patch can extend the two earlier exact
 # transitions; it never changes their historical hashes or owner limits.
 final=None;base=reference
 if final_path is not None:
  final=load(final_path)
  if final.get('schemaVersion')!=1 or final.get('policyId')!='canon-cleared-target-float-final-20261030-v1' or final.get('approved') is not True:
   raise ValueError('unapproved or unknown final pursuit/float policy')
  changes=final.get('classHashTransitions',{})
  if set(changes)!=FINAL_PATCH_CLASSES:raise ValueError('final patch must name exactly the four independently audited classes')
  if set(final.get('forceRetestCategories',[]))!={'mental_out','float_dial'}:raise ValueError('both affected final-patch categories require fresh evidence')
  base=dict(reference or {})
  for name,pair in changes.items():
   if set(pair)!={'before','after'} or pair['before']==pair['after'] or any(not isinstance(h,str) or not re.fullmatch('[0-9a-f]{64}',h) for h in pair.values()):raise ValueError('invalid exact final patch hashes')
   if reference is None or reference.get(name)!=pair['after']:raise ValueError('final patch after hash differs from reference')
   base[name]=pair['before']
  audit=Path(final['independentAudit']['path'])
  if sha(audit)!=final['independentAudit']['sha256']:raise ValueError('final patch independent audit changed')
  review=load(audit)
  required=['pass','existingOwnerMethodsUnchanged','onlyClearedMentalTargetEventAdded','mentalRendererBranchUnchanged','onlyFloatTargetBindingChanged','syntheticSwitchMapBytecodeUnchanged','pendingRecordBytecodeUnchanged','allOtherProductionBytesUnchanged','native262Passed','freshMental24Passed','freshFloat20Passed','freshFourFloatAlliesPassed']
  if not all(review.get(k) is True for k in required) or review.get('classHashTransitions')!=changes:raise ValueError('final patch lacks independent scope and actual affected-skill retests')
  intermediate=final.get('intermediateClassHashes',{})
  if intermediate:
   if not set(intermediate)<={DARK_RENDER_CLASS,DARK_RENDER_SWITCH_CLASS} or any(not isinstance(hashes,list) or not hashes or any(not re.fullmatch('[0-9a-f]{64}',h) or h in changes[k].values() for h in hashes) for k,hashes in intermediate.items()):raise ValueError('unknown intermediate render hashes')
   if review.get('intermediateFloatOnlyRendererBranchesReviewed') is not True or review.get('intermediateClassHashes')!=intermediate:raise ValueError('intermediate class hashes need their own exact unchanged-Mental branch audit')
  evidence=review.get('evidence',[])
  if not evidence or any(sha(Path(x['path']))!=x['sha256'] for x in evidence):raise ValueError('final patch supporting evidence changed')
  final.update(_path=str(final_path.resolve()),_sha256=sha(final_path),_auditSha256=sha(audit))
 policy=attach_render_policy(provenance_policy(motion_path,base),render_path,base)
 if final is not None:
  if policy is None:raise ValueError('final patch requires the two historical exact policies')
  policy['_finalPatchPolicy']=final
 return policy
def attach_render_policy(policy,path,reference):
 if path is None:return policy
 if policy is None:raise ValueError('the scoped render policy requires the independently approved motion policy')
 p=load(path)
 if p.get('schemaVersion')!=1 or p.get('policyId')!='canon-dark-target-render-20261030-v1' or p.get('approved') is not True:
  raise ValueError('dark target render policy is unapproved or unknown')
 changes=p.get('classHashTransitions',{})
 if DARK_RENDER_CLASS not in changes or not set(changes)<={DARK_RENDER_CLASS,DARK_RENDER_SWITCH_CLASS}:raise ValueError('dark target render policy allows only the reviewed outer client class and independently equivalent synthetic switch map')
 for name,pair in changes.items():
  if set(pair)!={'before','after'} or pair['before']==pair['after'] or any(not isinstance(h,str) or not re.fullmatch('[0-9a-f]{64}',h) for h in pair.values()):raise ValueError('invalid exact render class hashes')
  if reference is None or reference.get(name)!=pair['after']:raise ValueError('render after hash differs from final reference')
 if set(p.get('forceRetestCategories',[]))!={'dark_matter'} or set(p.get('reuseCategories',[]))!=(MOTION_RETEST|MOTION_REUSE)-{'dark_matter'}:
  raise ValueError('render policy must retest dark matter and preserve all other category branches')
 audit=Path(p['independentAudit']['path'])
 if sha(audit)!=p['independentAudit']['sha256']:raise ValueError('independent render audit bytes changed')
 review=load(audit)
 required=['pass','onlyDarkTargetSegmentChanged','nonDarkCategoryBranchesUnchanged','otherMethodsBytecodeUnchanged','serverNetworkCoreResourcesUnchanged','fullDark24Retested','eightFamilyRepresentativeRetested']
 if not all(review.get(k) is True for k in required) or review.get('classHashTransitions')!=changes:
  raise ValueError('independent render audit lacks exact transition, unchanged branches, or completed native retests')
 if DARK_RENDER_SWITCH_CLASS in changes and review.get('syntheticSwitchMapBytecodeUnchanged') is not True:raise ValueError('changed synthetic switch map lacks normalized whole-bytecode equivalence audit')
 evidence=review.get('evidence',[])
 if not evidence or any(sha(Path(x['path']))!=x['sha256'] for x in evidence):raise ValueError('independent render audit supporting evidence missing or changed')
 p['_path']=str(path.resolve());p['_sha256']=sha(path);p['_auditSha256']=sha(audit)
 policy['_renderPolicy']=p;return policy
def provenance_policy(path,reference):
 if path is None:return None
 p=load(path)
 if p.get('schemaVersion')!=1 or p.get('policyId')!='canon-motion-owner-20261030-v1' or p.get('approved') is not True:
  raise ValueError('provenance policy is unapproved or has an unknown version')
 if set(p.get('forceRetestCategories',[]))!=MOTION_RETEST or set(p.get('reuseCategories',[]))!=MOTION_REUSE:
  raise ValueError('motion owner policy must retest all four affected categories and reuse only eight unrelated branches')
 changes=p.get('classHashTransitions',{})
 if not changes or not set(changes)<=MOTION_OWNER_CLASSES:raise ValueError('policy includes an unreviewed shared/additional production class')
 for name,pair in changes.items():
  if set(pair)!={'before','after'} or any(not isinstance(h,str) or not re.fullmatch('[0-9a-f]{64}',h) for h in pair.values()) or pair['before']==pair['after']:
   raise ValueError('invalid exact before/after class hashes in provenance policy')
  if reference is None or reference.get(name)!=pair['after']:raise ValueError('policy after-class hash differs from final reference snapshot')
 audit=Path(p['independentAudit']['path'])
 if sha(audit)!=p['independentAudit']['sha256']:raise ValueError('independent provenance audit bytes changed')
 review=load(audit)
 if not all(review.get(k) is True for k in ['pass','staticInitializerUnchanged','dispatchUnchanged','unrelatedCategoryBranchesUnchanged','sharedRenderNetworkCoreResourcesUnchanged']):
  raise ValueError('independent owner/diff audit has not approved all required invariants')
 if set(review.get('approvedChangedClasses',[]))!=set(changes):raise ValueError('class transitions differ from independent source/diff audit')
 if review.get('classHashTransitions')!=changes:raise ValueError('exact before/after hashes were not approved by the independent source/diff audit')
 line_only=set(changes)-{k for k in changes if k.endswith(('CanonExpansionCombatA.class','CanonExpansionCombatA$Context.class'))}
 if line_only:
  if review.get('normalizedInnerCodeUnchanged') is not True or not line_only<=set(review.get('normalizedLineOnlyClasses',[])):
   raise ValueError('additional owner record classes lack normalized bytecode equivalence review')
  evidence=review.get('normalizedBytecodeEvidence',[])
  if not evidence or any(sha(Path(x['path']))!=x['sha256'] for x in evidence):raise ValueError('normalized bytecode evidence missing or changed')
 p['_path']=str(path.resolve());p['_sha256']=sha(path);p['_auditSha256']=sha(audit);return p

def provenance(run,reference,category,policy=None):
 exact,delta=runtime_match(run,reference)
 detail={'exactProductionSnapshotMatches':exact,'currentProductionSnapshotMatches':exact,'currentOwningProductionCompatible':exact,'productionDelta':delta,'compatibilityReuse':False}
 if exact or policy is None:return detail
 final=policy.get('_finalPatchPolicy')
 if final:
  actual=production_snapshot(run/'compiled-snapshot.json');base=dict(reference)
  changes=final['classHashTransitions'];used={k:v for k,v in changes.items() if actual.get(k)!=v['after']}
  if category=='mental_out' and MENTAL_OWNER_CLASS in used:return detail
  if category=='float_dial' and DARK_RENDER_CLASS in used:return detail
  intermediate=final.get('intermediateClassHashes',{})
  for name,pair in used.items():base[name]=actual[name] if actual.get(name) in intermediate.get(name,[]) else pair['before']
  historical=dict(policy);historical.pop('_finalPatchPolicy')
  checked=provenance(run,base,category,historical)
  if not checked['currentOwningProductionCompatible']:return detail
  scopes=checked.get('provenancePolicy',{}).get('scopes',[])
  if used:scopes.append({'path':final['_path'],'sha256':final['_sha256'],'independentAuditSha256':final['_auditSha256'],'policyId':final['policyId'],'explicitClassHashTransitions':used,'acceptedIntermediateClassHashes':{k:actual[k] for k in used if actual.get(k) in intermediate.get(k,[])},'basis':'Only the independently reviewed cleared-target Mental event handler and Float target association differ. Mental requires the final owner class; Float requires the final target renderer. Other owner methods/category branches and the two synthetic classes are independently equivalent.'})
  detail.update(currentOwningProductionCompatible=True,compatibilityReuse=True,provenancePolicy={'category':category,'scopes':scopes,'exactBinaryIdentityClaimed':False})
  return detail
 actual=production_snapshot(run/'compiled-snapshot.json');transitions=policy['classHashTransitions'];render=policy.get('_renderPolicy');renderchanges=render['classHashTransitions'] if render else {}
 motiondelta=set(delta)&set(transitions);renderdelta=set(delta)&set(renderchanges)
 if not delta or set(delta)!=motiondelta|renderdelta:return detail
 if motiondelta and category not in MOTION_REUSE:return detail
 if renderdelta and category=='dark_matter':return detail
 pairs={**transitions,**renderchanges}
 if any(actual.get(k)!=pairs[k]['before'] or reference.get(k)!=pairs[k]['after'] for k in delta):return detail
 scopes=[]
 if motiondelta:scopes.append({'path':policy['_path'],'sha256':policy['_sha256'],'independentAuditSha256':policy['_auditSha256'],'policyId':policy['policyId'],'explicitClassHashTransitions':{k:transitions[k] for k in sorted(motiondelta)},'basis':'Only exact reviewed other-owner motion classes differ; all four affected categories require new records.'})
 if renderdelta:scopes.append({'path':render['_path'],'sha256':render['_sha256'],'independentAuditSha256':render['_auditSha256'],'policyId':render['policyId'],'explicitClassHashTransitions':{k:renderchanges[k] for k in sorted(renderdelta)},'basis':'Exact reviewed Dark BLADE/VOLLEY client branch differs; all other category branches and methods are independently unchanged, with eight-family native retest. Dark Matter requires new records.'})
 detail.update(currentOwningProductionCompatible=True,compatibilityReuse=True,provenancePolicy={'category':category,'scopes':scopes,'exactBinaryIdentityClaimed':False})
 return detail

def runtime_profile(run):
 request=load(run/'request.json');profile={k:v for k,v in request.items() if k in ['shaders','camera','mods','shaderpack','irisEnabled','irisReloadKey']};mods=[]
 for name,expected in request.get('mods',{}).items():
  path=run/'game/mods'/name;ok=path.is_file() and sha(path)==expected;ids=[]
  if ok:
   with zipfile.ZipFile(path) as jar:
    for entry in ['META-INF/neoforge.mods.toml','META-INF/mods.toml']:
     if entry in jar.namelist():ids+=re.findall(r'^\s*modId\s*=\s*[\"\x27]([^\"\x27]+)',jar.read(entry).decode(errors='replace'),re.M)
  mods.append({'file':str(path),'sha256':expected,'copiedArtifactHashMatches':ok,'declaredModIds':sorted(set(ids))})
 profile['externalModArtifacts']=mods;return profile

def construct_runtime_proof(run,profile):
 log=(run/'client.log').read_text(errors='replace');hold=[x for x in log.splitlines() if 'QA_GAMEPLAY_CONSTRUCT_HOLD ' in x and 'body=null' not in x];land=[x for x in log.splitlines() if 'QA_GAMEPLAY_CONSTRUCT_LAND ' in x]
 sable=[x for x in profile.get('externalModArtifacts',[]) if x['copiedArtifactHashMatches'] and any('sable' in i or 'constructlink' in i or 'construct_link' in i for i in x['declaredModIds'])]
 return {'pass':bool(sable and hold and land),'actualRuntimeArtifacts':sable,'actualNativeBodyHoldWitnesses':hold,'actualNativeLandWitnesses':land,'scope':'Exact copied external mod SHA/declared identity plus real non-null native body, removed blocks and returned blocks. Main ACA class identity alone is insufficient.'}

def active_mechanism(path,reference,policy,rehash):
 """A single explicit native-class suite, never a generic report-pass escape."""
 r=load(path);errors=[]
 if r.get('schema')!='canon-real-grounding-duel-v1' or r.get('catalogKey')!='electromaster:grounding_return' or r.get('pass') is not True or r.get('errors'):
  return [],['unknown/failed active native mechanism suite: '+str(path)]
 run=Path(r['run']);required={'request.json','result.json','compiled-snapshot.json','screenshots.json'}|{role+tail for role in ['host','observer'] for tail in ['.log','-script.txt','-grounding-receipts.jsonl','-receipts.jsonl']}
 anchors=r.get('sourceHashes',{})
 if not required<=set(anchors) or any(not(run/n).is_file() or sha(run/n)!=anchors[n] for n in required):return [],['native Grounding source anchors missing/changed: '+str(path)]
 request=load(run/'request.json');result=load(run/'result.json');token=request.get('run')
 if result.get('status')!='PASS' or result.get('evidence',{}).get('compiledOutputChanged') or token!=r.get('runToken'):errors.append('Grounding actual formal completion/nonce/freeze invalid')
 roles={}
 for role in ['host','observer']:
  log=(run/(role+'.log')).read_text(errors='replace');script=(run/(role+'-script.txt')).read_text();rows=[json.loads(x) for x in (run/(role+'-grounding-receipts.jsonl')).read_text().splitlines() if x.strip()]
  if 'HIDDEN_AGENT:' not in log or 'QA_DONE' not in log or any(x in script for x in ['aca_canon_vfx','cmd:damage','lightning_bolt','|fx:','|motif:']):errors.append('Grounding hidden normal-input/source contract invalid: '+role)
  if not rows or any(x.get('run')!=token or x.get('role')!=role for x in rows):errors.append('Grounding stale/foreign native receipts: '+role)
  summary=next((x for x in reversed(rows) if x.get('event')=='summary'),{})
  if summary.get('physicalRDown')!=1 or summary.get('physicalRUp',0)<1 or role=='host' and summary.get('physicalShiftDown')!=1:errors.append('Grounding actual normal R/Shift missing: '+role)
  if role=='observer' and (not summary.get('data',{}).get('fired') or sum(x.get('event')=='actual-attacker-r-dispatched' for x in rows)!=1):errors.append('Grounding guest physical R was not dispatched exactly once after the original native START')
  roles[role]=rows
 checks=[x['data'] for x in roles['host'] if x.get('event')=='check'];c=checks[0] if len(checks)==1 else {};w=r.get('mechanism',{})
 if len(checks)!=1 or c!=w.get('nativeCheckReceipt') or not c.get('passed'):errors.append('Grounding native check receipt absent/changed')
 if c:
  try:
   before=c['before'];after=c['after'];host=c['host'];attacker=c['attacker'];ctx=after['context'];spent=Decimal(c['maximumSpent'])
   host_receipts=[json.loads(x) for x in (run/'host-receipts.jsonl').read_text().splitlines() if x.strip()];identities=next((x['data'] for x in host_receipts if x.get('event')=='server-two-players'),{})
   if len(identities)!=2 or len(set(identities.values()))!=2 or identities.get(request['host'])!=host or identities.get(request['observer'])!=attacker:errors.append('Grounding role/actual server-player UUID binding invalid')
   refund=Decimal(after['host']['shield'])-Decimal(before['host']['shield'])+spent;expected=min(Decimal(ctx['capacity'])*Decimal('.20'),spent)
   if host==attacker or not before['pvp'] or not before['attackPlayers'] or before['serverPlayers']!=2:errors.append('Grounding actual two-player legal PvP identities invalid')
   if not(c['incomingCount']==c['postCount']==len(c['incoming'])==len(c['posts'])==1):errors.append('Grounding native hit cardinality invalid')
   for hit in c['incoming']+c['posts']:
    if hit['owner']!=attacker or hit['target']!=host or hit['sourceSkill']!='electromaster.forked_arc' or not hit['sourceClass'].endswith('HighPowerCombat$ExactSkillDamageSource'):errors.append('Grounding actual reviewed electrical owner/target/source invalid')
   incoming=c['incoming'][0];age=incoming['worldTick']-ctx['started'];debit=Decimal(str(before['attacker']['cp']))-Decimal(str(incoming['attackerCp']))
   if incoming['canceledAtObservedPriority'] or not(0<=age<6) or debit!=Decimal('10'):errors.append('Grounding genuine paid electrical R/precise-window invalid')
   if not(spent>0 and c['precisionUsed'] and ctx['precisionUsed'] and refund==expected and refund>0 and before['host']['health']==after['host']['health'] and Decimal(str(after['host']['experience']))>Decimal(str(before['host']['experience']))):errors.append('Grounding actual native absorption/refund/progression invalid')
   def packets(rows):return {(x['data']['owner'],x['data']['entity'],x['data']['input'],x['data']['epoch'],x['data']['sequence'],x['data']['phase'],x['data']['point']) for x in rows if x.get('event')=='native-visual' and x['data'].get('owner')==host and x['data'].get('phase')==1}
   if not packets(roles['host'])&packets(roles['observer']):errors.append('Grounding original native phase1 packet not matched on both clients')
  except (KeyError,ValueError,IndexError,TypeError):errors.append('Grounding native mechanism fields malformed')
 shots={x['file']:x['sha256'] for x in load(run/'screenshots.json')};allowed=[]
 for role in ['host','observer']:
  label=role+'_grounding_return';folder=run/role/'screenshots/rec'/label;allowed.append(str(folder.resolve()));frames=pngs(run,role,label,shots,rehash)
  errors+=frames['errors']
  if frames['frames']<20:errors.append('Grounding actual role sequence too short: '+role)
 listed={x['file']:x['sha256'] for x in r.get('nativeFrames',[])}
 expected={str(p.relative_to(run)):shots.get(str(p.relative_to(run))) for root in allowed for p in Path(root).glob('f*.png')}
 if listed!=expected:errors.append('Grounding report native frame identities differ from actual roles')
 profile={k:v for k,v in request.items() if k in ['shaders','camera','mods','shaderpack','irisEnabled']};profile['actualRoleModArtifactHashes']={role:{p.name:sha(p) for p in (run/role/'mods').glob('*.jar') if not p.name.startswith('._')} for role in ['host','observer']}
 proof={'catalogKey':r['catalogKey'],'kind':'actual_two_player_native_grounding_absorption','pass':not errors,**provenance(run,reference,'electromaster',policy),'run':str(run.resolve()),'report':str(path.resolve()),'reportSha256':sha(path),'productionSnapshotSha256':sha(run/'compiled-snapshot.json'),'runtimeProfile':profile,'allowedFrameRoots':allowed,'nativeMechanism':w,'errors':errors}
 return [proof],errors

def pngs(run,role,label,hash_index,rehash):
 folder=run/role/'screenshots/rec'/label;files=sorted(folder.glob('f*.png'));errors=[]
 if not files or [p.name for p in files]!=[f'f{i:05d}.png' for i in range(len(files))]:errors.append('missing/noncontiguous original '+role+' frames')
 digest=hashlib.sha256()
 for p in files:
  rel=str(p.relative_to(run));h=hash_index.get(rel)
  if not h:errors.append('frame absent from formal screenshot hash index: '+rel);continue
  if rehash and sha(p)!=h:errors.append('formal frame hash changed: '+rel)
  with p.open('rb') as f:head=f.read(24)
  if len(head)!=24 or head[:8]!=b'\x89PNG\r\n\x1a\n' or struct.unpack('>II',head[16:24])!=(1280,720):errors.append('not native1280x720 PNG: '+rel)
  digest.update((rel+':'+h+'\n').encode())
 return {'role':role,'label':label,'frames':len(files),'hashDigest':digest.hexdigest(),'errors':errors}

def formal(run,catalog,reference,rehash,policy=None):
 errors=[];proofs=[];result=load(run/'result.json');request=load(run/'request.json')
 if result.get('status')!='PASS' or result.get('evidence',{}).get('compiledOutputChanged'):return [],['formal actual-network/lifecycle runner did not pass: '+str(run)]
 run_token=result.get('run');roles={}
 for role in ['host','observer']:
  log=(run/(role+'.log')).read_text(errors='replace');script=(run/(role+'-script.txt')).read_text();rows=[loadrow for line in (run/(role+'-receipts.jsonl')).read_text().splitlines() if (loadrow:=json.loads(line))]
  if 'HIDDEN_AGENT:' not in log:errors.append('formal role hidden agent missing: '+role)
  if {x.get('run') for x in rows}!={run_token}:errors.append('formal foreign/stale role receipt: '+role)
  if any(x.startswith(('fx:','motif:','cmd:aca_canon_vfx ')) for x in script.strip().split('|')):errors.append('formal contains synthetic client VFX: '+role)
  roles[role]=(log,script,rows)
 hostids=result.get('evidence',{}).get('serverPlayers',{});hostuuid=hostids.get(request.get('host','CanonHost'))
 if len(set(hostids.values()))!=2 or not hostuuid:errors.append('formal does not prove distinct actual expected players')
 index={x['file']:x['sha256'] for x in load(run/'screenshots.json')};matches,delta=runtime_match(run,reference)
 if errors:return [],errors
 for key,row in catalog.items():
  if row['id'] not in m.DUAL:continue
  skill=row['id'];hostlabel='host_'+skill;observerlabel='observer_'+skill;hlog,hscript,hrows=roles['host'];olog,oscript,orows=roles['observer']
  if 'rec:'+hostlabel not in hscript or 'rec:'+observerlabel not in oscript:continue
  start=hlog.find('QA_REC_START '+hostlabel);end=hlog.find('QA_REC_END '+hostlabel,start);segment=hlog[start:end] if start>=0 and end>start else ''
  issue=[]
  if not segment or 'QA_KEY 82 DOWN' not in segment or 'QA_KEY 82 UP' not in segment:issue.append('host ordinary R down/up not witnessed inside this skill recording')
  def tuples(rows,local):return {(x['data'].get('owner'),x['data'].get('skill'),x['data'].get('phase'),x['data'].get('dimension'),x['data'].get('origin'),x['data'].get('endpoint')) for x in rows if x.get('event')=='vfx-received' and x['data'].get('skill')==skill and x['data'].get('owner')==hostuuid and x['data'].get('local') is local and x['data'].get('phase')!='END'}
  common=tuples(hrows,True)&tuples(orows,False)
  if not common:issue.append('no actual matching original server event for this skill on both clients')
  if not re.search(r'QA_CANON_FORMAL host .*"skill":"'+re.escape(skill)+'"',segment):issue.append('server event not inside same host rec interval')
  frames=[pngs(run,role,label,index,rehash) for role,label in [('host',hostlabel),('observer',observerlabel)]]
  for f in frames:issue+=f['errors']
  profile={k:v for k,v in request.items() if k in ['shaders','camera','mods','shaderpack','irisEnabled']}
  profile['actualRoleModArtifactHashes']={role:{p.name:sha(p) for p in (run/role/'mods').glob('*.jar')} for role in ['host','observer']}
  proofs.append({'catalogKey':key,'kind':'actual_two_player_network_action','pass':not issue,**provenance(run,reference,row['category'],policy),'errors':issue,'run':str(run.resolve()),'formalResultSha256':sha(run/'result.json'),'productionSnapshotSha256':sha(run/'compiled-snapshot.json'),'runtimeProfile':profile,'matchingServerEvents':[list(x) for x in sorted(common)],'frames':frames,'allowedFrameRoots':[str((run/role/'screenshots/rec'/label).resolve()) for role,label in [('host',hostlabel),('observer',observerlabel)]]})
 return proofs,errors

def main():
 p=argparse.ArgumentParser(description=__doc__);p.add_argument('--native-report',type=Path,action='append',default=[]);p.add_argument('--active-mechanism-report',type=Path,action='append',default=[]);p.add_argument('--passive-report',type=Path,action='append',default=[]);p.add_argument('--formal-run',type=Path,action='append',default=[]);p.add_argument('--reference-snapshot',type=Path);p.add_argument('--provenance-policy',type=Path);p.add_argument('--render-provenance-policy',type=Path);p.add_argument('--final-patch-provenance-policy',type=Path);p.add_argument('--visual-report',type=Path,action='append',default=[]);p.add_argument('--require-visual-review',action='store_true');p.add_argument('--require-passive-gameplay',action='store_true');p.add_argument('--rehash-frames',action='store_true');p.add_argument('--allow-incomplete',action='store_true');p.add_argument('--out',type=Path,required=True);a=p.parse_args()
 rows=m.catalog();catalog={x['key']:x for x in rows};errors=[];allproofs=[];rejected=[];reference=production_snapshot(a.reference_snapshot) if a.reference_snapshot else None;policy=load_policies(a.provenance_policy,a.render_provenance_policy,reference,a.final_patch_provenance_policy)
 for path in a.native_report:
  r=load(path);run=Path(r['run']);issue=[]
  if r.get('errors') or r.get('rawRecorderPass') is not True:issue.append('run-global integrity/recorder completion failed')
  for name,prop in [('script.txt','scriptSha256'),('fx4-receipts.jsonl','receiptSha256'),('result.json','resultSha256')]:
   if sha(run/name)!=r.get('sourceEvidence',{}).get(prop):issue.append('native report evidence changed: '+name)
  for manifest in r.get('manifests',[]):
   if sha(Path(manifest['path']))!=manifest['sha256']:issue.append('native fixture manifest changed')
  if issue:errors.append({'report':str(path),'errors':issue});continue
  matches,delta=runtime_match(run,reference)
  profile=runtime_profile(run)
  if any(not x['copiedArtifactHashMatches'] for x in profile['externalModArtifacts']):errors.append('copied external mod hash changed: '+str(run));continue
  for c in r.get('clips',[]):
   key=c['category']+':'+c['skill']
   if key not in catalog:errors.append('unknown catalog entry '+key);continue
   if c.get('mode')!='real' or not c.get('pass') or c.get('confirmedRealCast') is not True:
    rejected.append({'key':key,'report':str(path),'clip':c.get('name'),'mode':c.get('mode'),'errors':c.get('errors')});continue
   if not catalog[key]['controllable']:errors.append('passive incorrectly submitted as R cast: '+key);continue
   if a.rehash_frames:
    for n,h in c.get('frameHashes',{}).items():
     if sha(run/'game/screenshots/rec'/c['name']/n)!=h:errors.append('native PNG changed: '+str(run)+'/'+c['name']+'/'+n)
   support=construct_runtime_proof(run,profile) if c['skill']=='vector_construct' else None
   if support is not None and not support['pass']:
    rejected.append({'key':key,'report':str(path),'clip':c.get('name'),'mode':c.get('mode'),'errors':['VectorConstruct lacks actual Sable identity/native body+landing evidence'],'runtimeSupport':support});continue
   allproofs.append({'catalogKey':key,'kind':'actual_solo_key_and_server_result','pass':True,**provenance(run,reference,catalog[key]['category'],policy),'report':str(path.resolve()),'reportSha256':sha(path),'run':str(run.resolve()),'clip':c['name'],'frames':c.get('frames'),'ownRenderer':catalog[key]['ownRenderer'],'runtimeProfile':profile,'runtimeSupport':support,'productionSnapshotSha256':sha(run/'compiled-snapshot.json')})
 for path in a.passive_report:
  r=load(path);run=Path(r['run']);matches,delta=runtime_match(run,reference)
  if not r.get('pass') or r.get('errors'):errors.append('passive functional report did not pass: '+str(path));continue
  stale=[n for n,h in r.get('sourceHashes',{}).items() if not(run/n).is_file() or sha(run/n)!=h]
  if stale:errors.append('passive evidence files changed: '+str(stale));continue
  for c in r.get('records',[]):
   if c.get('pass') and c['catalogKey'] in catalog and not catalog[c['catalogKey']]['controllable']:
    allproofs.append({'catalogKey':c['catalogKey'],'kind':'actual_native_gui_if_and_passive_behavior','pass':True,'actualPassiveGameplay':bool(c.get('actualMechanismLines')),'passiveGameplayMechanismLines':c.get('actualMechanismLines',[]),**provenance(run,reference,catalog[c['catalogKey']]['category'],policy),'report':str(path.resolve()),'reportSha256':sha(path),'run':str(run.resolve()),'runtimeProfile':runtime_profile(run),'productionSnapshotSha256':sha(run/'compiled-snapshot.json')})
 for path in a.active_mechanism_report:
  proofs,issue=active_mechanism(path,reference,policy,a.rehash_frames);allproofs+=proofs;errors+=issue
 for run in a.formal_run:
  proofs,issue=formal(run,catalog,reference,a.rehash_frames,policy);allproofs+=proofs;errors+=issue
 visuals={};visualsources=[]
 for path in a.visual_report:
  r=load(path);visualsources.append({'path':str(path.resolve()),'sha256':sha(path)})
  for c in r.get('records',[]):
   if c.get('catalogKey') in catalog and c.get('pass') is True and c.get('sourceFrames'):
    visuals[c['catalogKey']]={'report':str(path.resolve()),'entry':c}
 selected={};history=[]
 for proof in allproofs:
  if proof.get('pass') and proof.get('currentOwningProductionCompatible',proof.get('currentProductionSnapshotMatches')) and not(a.require_passive_gameplay and not catalog[proof['catalogKey']]['controllable'] and not proof.get('actualPassiveGameplay')):
   if proof['catalogKey'] in selected:history.append(selected[proof['catalogKey']])
   selected[proof['catalogKey']]=proof
  else:history.append(proof)
 result=[]
 for key,row in catalog.items():
  proof=selected.get(key);pixel=visuals.get(key)
  if pixel:
   candidates=[x for x in allproofs if x['catalogKey']==key and x.get('pass') and x.get('currentOwningProductionCompatible',x.get('currentProductionSnapshotMatches'))]
   valid=bool(candidates);frame_checks=[]
   for ref in pixel['entry'].get('sourceFrames',[]):
    if not isinstance(ref,dict):valid=False;continue
    raw=ref.get('path') or ref.get('file');frame=Path(raw) if raw else None;matched=None
    if frame is not None:
     for candidate in candidates:
      resolved=frame if frame.is_absolute() else Path(candidate['run'])/frame
      if resolved.is_file() and resolved.resolve().is_relative_to(Path(candidate['run']).resolve()) and ref.get('sha256')==sha(resolved):
       # Same actual catalog key is mandatory; native solo frames additionally
       # belong to this exact strict clip, not another skill's folder in the run.
       if candidate.get('clip') and not resolved.resolve().is_relative_to((Path(candidate['run'])/'game/screenshots/rec'/candidate['clip']).resolve()):continue
       if candidate.get('allowedFrameRoots') and not any(resolved.resolve().is_relative_to(Path(root)) for root in candidate['allowedFrameRoots']):continue
       frame=resolved;matched=candidate;break
    ok=matched is not None;valid=valid and ok
    frame_checks.append({'path':str(frame),'matchesCurrentCompatibleSkillProofAndHash':ok,'matchingProofRun':matched['run'] if matched else None,'matchingProofReportSha256':matched.get('reportSha256',matched.get('formalResultSha256')) if matched else None,'matchingProductionSnapshotSha256':matched.get('productionSnapshotSha256',sha(Path(matched['run'])/'compiled-snapshot.json')) if matched else None,'matchingRuntimeProfile':matched.get('runtimeProfile') if matched else None,'matchingRuntimeSupport':matched.get('runtimeSupport') if matched else None,'exactProductionSnapshotMatches':matched.get('exactProductionSnapshotMatches') if matched else None,'compatibilityReuse':matched.get('compatibilityReuse') if matched else None})
   pixel={**pixel,'sourceFrameChecks':frame_checks,'matchesCurrentCompatibleSkillProofs':valid}
   if not valid:pixel=None
  result.append({'catalogKey':key,'id':row['id'],'category':row['category'],'level':row['level'],'controllable':row['controllable'],'ownRenderer':row['ownRenderer'],'functionalAcceptanceCurrentBuild':proof is not None,'selectedProof':proof,'selectedFunctionalProof':proof,'nativePixelReview':pixel,'visualReviewPassed':bool(pixel) if row['controllable'] else None,'status':'CONFIRMED_CURRENT_BUILD' if proof else 'PENDING_ACTUAL_CURRENT_BUILD_EVIDENCE'})
 pending=[r['catalogKey'] for r in result if not r['functionalAcceptanceCurrentBuild']];pixelpending=[r['catalogKey'] for r in result if r['controllable'] and not r['visualReviewPassed']]
 report={'createdUtc':datetime.now(timezone.utc).isoformat(),'pass':not errors and not pending and (not a.require_visual_review or not pixelpending),'completeFunctional':not pending,'completeActivePixelReview':not pixelpending,'catalogRecords':305,'activeExpected':290,'passiveExpected':15,'activeConfirmed':sum(r['controllable'] and r['functionalAcceptanceCurrentBuild'] for r in result),'passiveConfirmed':sum(not r['controllable'] and r['functionalAcceptanceCurrentBuild'] for r in result),'requiredPassiveGameplay':a.require_passive_gameplay,'passiveGameplayConfirmed':len({p['catalogKey'] for p in allproofs if p.get('pass') and p.get('actualPassiveGameplay') and p.get('currentOwningProductionCompatible',p.get('currentProductionSnapshotMatches'))}),'own17Confirmed':sum(r['ownRenderer'] and r['functionalAcceptanceCurrentBuild'] for r in result),'own17Expected':17,'pendingFunctional':pending,'pendingActivePixelReview':pixelpending,'errors':errors,'records':result,'allProofs':allproofs,'supersededOrStaleProofs':history,'rejectedOrPreviewAttemptsPreserved':rejected,'referenceSnapshot':str(a.reference_snapshot.resolve()) if a.reference_snapshot else None,'referenceSnapshotSha256':sha(a.reference_snapshot) if a.reference_snapshot else None,'provenancePolicy':{'path':policy['_path'],'sha256':policy['_sha256'],'policyId':policy['policyId']} if policy else None,'compatibilityReusedCatalogKeys':sorted(k for k,p in selected.items() if p.get('compatibilityReuse')),'renderProvenancePolicy':{'path':policy['_renderPolicy']['_path'],'sha256':policy['_renderPolicy']['_sha256'],'policyId':policy['_renderPolicy']['policyId']} if policy and policy.get('_renderPolicy') else None,'frameBytesRehashedByThisInvocation':a.rehash_frames,'visualReviewReports':visualsources,'visualPixelsReviewedByThisProgram':False,'limits':['No preview substitutes for a real server-admitted action.','Original305category/id records are290active+15passive; repeated course ids remain separate category records.','A recorded action is not itself an aesthetic acceptance; active per-record pixel review is tracked independently.','Different production bytes stay historical unless an explicit versioned independent owner/diff policy permits the unrelated category; all changed class hashes and each reuse remain visible.','Natural survival progression, story, compatibility and multiplayer soak have their own explicit suites.']}
 a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:report[k] for k in ['pass','completeFunctional','catalogRecords','activeConfirmed','passiveConfirmed','own17Confirmed','errors']}));raise SystemExit(0 if report['pass'] or a.allow_incomplete and not errors else 1)
if __name__=='__main__':main()
