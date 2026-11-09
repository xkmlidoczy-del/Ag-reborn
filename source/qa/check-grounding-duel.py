#!/usr/bin/env python3
"""Read realtwo-player Grounding absorption; never synthesize or modify gameplay."""
import argparse,hashlib,json
from decimal import Decimal
from pathlib import Path
from PIL import Image
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--run',type=Path,required=True);p.add_argument('--out',type=Path,required=True);a=p.parse_args()
load=lambda name:json.loads((a.run/name).read_text())
request=load('request.json');token=request['run'];formal=load('result.json') if (a.run/'result.json').exists() else {};errors=[];checks=[]
def req(value,detail):
 checks.append({'pass':bool(value),'detail':detail})
 if not value:errors.append(detail)
def rows(role,tail):
 path=a.run/(role+'-'+tail+'.jsonl');return [json.loads(x) for x in path.read_text().splitlines()] if path.exists() else []
H,G=rows('host','grounding-receipts'),rows('observer','grounding-receipts');FH,FG=rows('host','receipts'),rows('observer','receipts')
req(formal.get('status')=='PASS','formal native process resultPASS and immutable compiled snapshot')
for role,rs in [('host',H),('observer',G)]:
 req(bool(rs) and all(r.get('run')==token and r.get('role')==role for r in rs),role+' currentrun nativeGrounding receipts')
 text=(a.run/(role+'.log')).read_text(errors='replace');req('HIDDEN_AGENT: GLFW creation visibility/focus disabled' in text and 'QA_DONE' in text,role+' hidden native scriptcompleted')
 script=(a.run/(role+'-script.txt')).read_text();req(not any(v in script for v in ['aca_canon_vfx','|fx:','|motif:','cmd:damage','lightning_bolt']),role+' no syntheticvisual or forgeddamage command')
ids=next((r['data'] for r in FH if r.get('event')=='server-two-players'),{})
req(len(ids)==2 and len(set(ids.values()))==2,'two actual distinct server player UUIDs')
host=ids.get(request['host']);attacker=ids.get(request['observer'])
checks_data=[r['data'] for r in H if r['event']=='check'];req(len(checks_data)==1,'one native mechanismcheck')
mechanism={}
if len(checks_data)==1:
 c=checks_data[0];before=c['before'];after=c['after'];req(c.get('passed'),'server observed actualspent/precision/uniquehit outcome')
 req(c['host']==host and c['attacker']==attacker,'mechanismexacthost/attackerUUID binding')
 req(before['pvp'] and before['attackPlayers'] and before['serverPlayers']==2,'actual legal isolatedPvPprofile')
 req(c['incomingCount']==1 and c['postCount']==1 and len(c['incoming'])==1 and len(c['posts'])==1,'exactlyone actual incomingdamage and post, no duplicatehit')
 for event in c['incoming']+c['posts']:
  req(event['owner']==attacker and event['target']==host and event['sourceSkill']=='electromaster.forked_arc' and event['sourceClass'].endswith('HighPowerCombat$ExactSkillDamageSource'),'actual reviewed electrical sourceidentity/skill/owner/target')
 req(not c['incoming'][0]['canceledAtObservedPriority'],'electrical skill incomingwasnot the immuneenvironmentalLightning oracle')
 ctx=after.get('context',{});spent=Decimal(c['maximumSpent']);req(spent>0 and c['precisionUsed'] and ctx.get('precisionUsed'),'actual context spentshield and usedprecision decision')
 refund=Decimal(after['host']['shield'])-Decimal(before['host']['shield'])+spent
 expected=min(Decimal(ctx.get('capacity','0'))*Decimal('.20'),spent)
 req(refund>0 and refund==expected,'one bounded precision refund agrees with exactshield accounting')
 req(before['host']['health']==after['host']['health'],'fullyabsorbed hit doesnot debit exactHP')
 guest_samples=[before['attacker'],after['attacker']]+[s['attacker'] for s in c['serverTickSamples']]
 guest_min_hp=min(Decimal(v['health']) for v in guest_samples);guest_min_shield=min(Decimal(v['shield']) for v in guest_samples)
 req(guest_min_hp==Decimal(before['attacker']['health']) and guest_min_shield==Decimal(before['attacker']['shield']),'ranged guest remains alive with no HP/shield loss in every native server-tick sample; genuine shield capacity growth and natural recovery may increase shield')
 req(Decimal(str(after['host']['experience']))>Decimal(str(before['host']['experience'])),'absorption awards actualGrounding experience')
 paid=Decimal(str(before['attacker']['cp']))-Decimal(str(c['incoming'][0]['attackerCp']))
 req(paid==Decimal('10.0'),'one genuine LV1forked_arc debit10CP, measured inside actualincoming callback before regeneration')
 hit_tick=c['incoming'][0]['worldTick'];started=ctx.get('started');age=hit_tick-started if started is not None else None
 req(age is not None and 0<=age<6,'actual reviewedhit within original6tick precise window')
 mechanism={'host':host,'attacker':attacker,'spent':str(spent),'precisionUsed':c['precisionUsed'],'computedRefund':str(refund),'expectedRefund':str(expected),'actualAttackerCpDebit':str(paid),'preciseHitAgeTicks':age,'incomingCount':c['incomingCount'],'postCount':c['postCount'],'hostPayment':'activationCPcost0 bydesign; shieldabsorption spent>0 is successful-action payment','attackSkill':'electromaster.forked_arc','attackerMinimumObservedHealth':str(guest_min_hp),'attackerMinimumObservedShield':str(guest_min_shield),'attackerShieldRecoveryNote':'Original paid forked_arc XP increases capacity; native tickShield recovery may fill the small earned increase. No observed HP/shield loss is required, not equal final shield.','nativeCheckReceipt':c}
for role,rs in [('host',H),('observer',G)]:
 summary=next((r for r in reversed(rs) if r['event']=='summary'),{})
 req(summary.get('physicalRDown')==1 and summary.get('physicalRUp',0)>=1,role+' one actual normalRpress/release')
 if role=='host':req(summary.get('physicalShiftDown')==1,'host actualShift stanceinput')
 else:req(summary.get('data',{}).get('fired') and sum(r['event']=='actual-attacker-r-dispatched' for r in rs)==1,'secondclient R dispatchedonce only after original nativeGrounding STARTreceipt')
def visual_keys(rs):return {(d['owner'],d['entity'],d['input'],d['epoch'],d['sequence'],d['phase'],d['point']) for r in rs if r['event']=='native-visual' for d in [r['data']] if d['owner']==host and d['phase']==1}
common=visual_keys(H)&visual_keys(G);req(bool(common),'sameoriginal nativeGrounding absorption packet on both clients')
shots=load('screenshots.json');shotmap={r['file']:r['sha256'] for r in shots};frame_rows=[]
for role in ('host','observer'):
 folder=a.run/role/'screenshots/rec'/('host_grounding_return' if role=='host' else 'observer_grounding_return');frames=sorted(folder.glob('f*.png'))
 req(len(frames)>=20,role+' actual nativeframe sequence sufficient')
 nums=[int(f.stem[1:]) for f in frames];req(nums==list(range(len(nums))),role+' framescontiguous from0')
 for f in frames:
  relative=str(f.relative_to(a.run));digest=hashlib.sha256(f.read_bytes()).hexdigest();req(shotmap.get(relative)==digest,'nativePNG receiptSHA '+relative)
  with Image.open(f) as im:im.load();req(im.size==(1280,720),'nativePNG1280x720 '+relative)
  frame_rows.append({'file':relative,'sha256':digest,'role':role})
anchors=['request.json','result.json','compiled-snapshot.json','screenshots.json','host.log','observer.log','host-script.txt','observer-script.txt','host-grounding-receipts.jsonl','observer-grounding-receipts.jsonl','host-receipts.jsonl','observer-receipts.jsonl']
source_hashes={name:hashlib.sha256((a.run/name).read_bytes()).hexdigest() if (a.run/name).is_file() else None for name in anchors}
req(all(source_hashes.values()),'all actual native process/packet/ledger/frame anchors present and hashed')
report={'schema':'canon-real-grounding-duel-v1','pass':not errors,'run':str(a.run.resolve()),'runToken':token,'catalogKey':'electromaster:grounding_return','sourceHashes':source_hashes,'supportedScenario':'actual2playerPvP, realphysicalShift/R stance and realguestR forked_arc electricalattack','mechanism':mechanism,'sharedNativeAbsorptionPacket':list(sorted(common))[0] if common else None,'nativeFrames':frame_rows,'actualFrameCount':len(frame_rows),'checks':len(checks),'errors':errors,'checkDetails':checks,'visualPixelsReviewed':False,'limits':'Setup grants lawfulPvP/loadout only; notnaturalprogression. EnvironmentalLightningimmune failures retained separately and notaccepted as Groundingpositive. Nativeframe hashes/dimensions are not aestheticapproval.'}
a.out.write_text(json.dumps(report,ensure_ascii=False,indent=2)+'\n');print(json.dumps({k:report[k] for k in ['pass','actualFrameCount','errors']},ensure_ascii=False));raise SystemExit(0 if report['pass'] else 1)
