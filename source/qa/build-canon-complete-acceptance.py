#!/usr/bin/env python3
"""Generate exhaustive source-catalog actual-input fixtures. Never launches a client.

305 catalog records are 290 active actions and 15 passive/theory records. Fixtures
are intentions, not success. All actual actions require independent server/network
receipts and native pixel review. Real teammate cases are emitted separately.
"""
import argparse,csv,hashlib,json,re
from pathlib import Path
from collections import Counter,defaultdict

S=Path(__file__).resolve().parents[1];R=S.parent
NEW={'mental_out','dark_matter','nitrogen_armor','aero_hand','hydro_hand','float_dial','liquid_shadow','paper_psychokinesis'}
DUAL={'mental_crowd_detach','dark_wing_shelter','nitrogen_ally_guard','hydro_rescue','hydro_douse_ally','hydro_tow_ally','float_rescue','float_ally_hover','float_ally_ascent','float_ally_descent'}
OWN={'railgun','thunder_clap','rail_thunder_array','scatter_bomb','thermal_shell','disintegration_sweep','multicore_canopy','shear_windblade','storm_wing','trajectory_return','vector_cage','multi_axis_reflection','wind_pressure_lift','zenith_vector_dominion','plasma_cannon','vector_construct','multidimensional_landing_array'}
FRONT=[(.5,3.5)];NEAR=[(.5,2.1)];CROWD=[(.5,3.5),(-1.2,4.5),(2.2,4.5),(-1.2,6),(2.2,6)]
HEAD=['wait:80','cmd:time set noon','cmd:gamerule doDaylightCycle false','cmd:gamerule doMobSpawning false','cmd:weather clear','cmd:difficulty normal','cam:third','vcam:7.5,-57.2,-1.5,0.5,-59.0,3.5','wait:20']
PAPER='cmd:item replace entity @s weapon.mainhand with minecraft:paper 64'

def sha(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def load(p):return json.loads(p.read_text())
def catalog():
    classic=load(S/'src/main/resources/classic-skills.json')['skills'];canon=load(S/'docs/canon/skills.json')
    hp=(S/'src/main/java/cn/academy/port/highpower/HighPowerSkills.java').read_text();constants=dict(re.findall(r'\b([A-Z][A-Z0-9_]*)\s*=\s*"([^"]+)"',hp))
    categories={'HighPowerCategories.TELEPORTER':'teleporter','HighPowerCategories.MELTDOWNER':'meltdowner','HighPowerCategories.VECMANIP':'vecmanip','CATEGORY':'electromaster'}
    high=[]
    for category,token,level,control in re.findall(r'new\s+SkillCatalog\.Skill\(\s*([^,]+),\s*([^,]+),\s*(\d+),\s*(true|false)',hp):
        high.append({'category':categories.get(category.strip(),constants.get(category.strip(),category.strip().strip('"'))),'id':constants.get(token.strip(),token.strip().strip('"')),'level':int(level),'controllable':control=='true'})
    assert len(classic)==50 and len(high)==61 and len(canon)==194,(len(classic),len(high),len(canon))
    rows=[{**x,'origin':'classic'} for x in classic]+[{**x,'origin':'highpower'} for x in high]+[{**x,'controllable':True,'origin':'canon'} for x in canon]
    csvpath=R/'docs/vfx-upgrade-skills-20261029.csv';csvrows=list(csv.DictReader(csvpath.open()))
    keyed={(x['category'],x['id']):x for x in rows};assert len(keyed)==305
    assert {(x['category'],x['id']) for x in csvrows}==set(keyed)
    for x in csvrows:
        k=(x['category'],x['id']);keyed[k].update({'zh':x['zh'],'renderer':x['current_renderer'],'presentationForms':x['presentation_forms'],'csvRow':csvrows.index(x)+2})
    for x in rows:
        x['key']=x['category']+':'+x['id'];x['ownRenderer']=x['id'] in OWN
        x['acceptanceKind']='passive_behavior' if not x['controllable'] else 'dual_actual_player_action' if x['id'] in DUAL else 'solo_actual_key_action'
    assert sum(x['controllable'] for x in rows)==290
    return rows

def reset():
    return ['cam:third','vcam:7.5,-57.2,-1.5,0.5,-59.0,3.5','escape','keyup:82','keyup:87','keyup:32','keyup:340','keyup:341','cmd:effect clear @s','cmd:kill @e[type=!minecraft:player]',
            'cmd:fill -14 -64 -10 14 -49 22 minecraft:air','cmd:fill -14 -64 -10 14 -62 22 minecraft:dirt','cmd:fill -14 -61 -10 14 -61 22 minecraft:grass_block',
            'cmd:tp @s 0.5 -60 0.5 0 0','wait:10']
def enemies(points,ai=False,y=-60):
    out=[f'cmd:summon minecraft:husk {x} {y} {z} {{Tags:["complete_target"],Silent:1b,PersistenceRequired:1b,NoAI:{0 if ai else 1}b}}' for x,z in points]
    if ai and points:out+=['cmd:execute as @e[type=minecraft:husk,tag=complete_target] run attribute @s minecraft:generic.movement_speed base set 0']
    return out

def press(hold=3):return ['keydown:82',f'wait:{hold}','keyup:82']
def slot(skill):return [f'live:slot:{skill}','wait:4']
def prerequisite(skill,back,material=()):return [f'live:setup:{skill}','wait:30',*material,*press(), 'wait:5',*slot(back)]
def recipe(row):
    skill=row['id'];cat=row['category'];q={'setup':[],'points':FRONT,'ai':False,'aim':(.5,-59.2,3.5),'action':press(),'post':70,'notes':[],'instantFixture':None,'sourceReviewed':cat in NEW,'settle':8,'lead':4}
    s=q['setup'];notes=q['notes']
    if cat=='mental_out':
        q.update(points=CROWD,ai=True,post=100)
        q['instantFixture']='mental-other' if skill=='mental_attention_shift' else 'mental-route' if skill in {'mental_focus_break','mental_route_interrupt','mental_panic_field','mental_relay_interference'} else 'mental-alert'
        notes+=['Actual AI hostiles, movement_speed=0 retains native navigation. Fixture targets/navigation are preconditions only; normal R must produce server result.']
        if skill=='mental_puppet':
            q.update(points=[],instantFixture=None,aim=(.5,-59.2,3.5),post=220)
            s += ['cmd:summon minecraft:cow 0.5 -60 3.5 {NoAI:0b,Silent:1b,PersistenceRequired:1b}','cmd:attribute @e[type=minecraft:cow,limit=1] minecraft:generic.movement_speed base set 0']
    elif cat in {'dark_matter','nitrogen_armor','aero_hand'}:
        q.update(points=CROWD,post=110)
        if skill in {'dark_blade','dark_wing_charge','nitrogen_punch','nitrogen_burst','nitrogen_ram','nitrogen_impact','nitrogen_uppercut','aero_push','aero_crosswind','nitrogen_shove'}:q.update(points=NEAR,aim=(.5,-59.2,2.1))
        if skill=='dark_blade':
            s+=['cmd:attribute @e[type=minecraft:husk,tag=complete_target,limit=1] minecraft:generic.max_health base set 200','cmd:data merge entity @e[type=minecraft:husk,tag=complete_target,limit=1] {Health:200f}','aim:0.5,-59.2,2.1','cmd:academyhp target 5','cmd:academyhp inspect','vcam:5,-58.4,1.2,0.5,-59.0,1.3']
            q['action']=press()+['cmd:academyhp inspect']
            notes+=['Durable native vanilla hostile with a real finite LV5 target ledger (existing admin fixture command); exact ledger inspected before/after real R. Ordinary 200HP alone was correctly killed by LV5 damage. A true side camera keeps the brief blade clear of the surviving target.']
        if skill in {'nitrogen_shove','aero_push','aero_crosswind'}:
            q['ai']=True;notes+=['Real AI-enabled hostile (movement speed zero); generic aimed() correctly excludes NoAI targets.']
        if cat=='nitrogen_armor' and skill!='nitrogen_armor':
            s += prerequisite('nitrogen_armor',skill);notes+=['Actual armor cast before tested action; slot rebind preserves server shell.']
        if skill in {'dark_wing_brake','dark_wing_rescue','dark_wing_shelter','dark_wing_charge'}:s += prerequisite('dark_wings',skill)
        if skill=='dark_synthesis':q['points']=[];s+=['cmd:item replace entity @s weapon.mainhand with minecraft:diamond_sword 1','cmd:give @s academy:dark_matter_crystal 2'];notes+=['Sword equipped before crystals so no material overwrite.']
        if skill=='nitrogen_shell_reinforce':s+=['cmd:damage @s 1 minecraft:generic','wait:5'];q['points']=[]
        if skill in {'dark_shard_return','dark_shard_orbit','nitrogen_item_push','aero_item_pull','aero_vortex','aero_launch'}:
            q['points']=[];s+=['cmd:item replace entity @s weapon.mainhand with minecraft:cobblestone 16','aim:0.5,-58.8,6.5','keydown:81','wait:1','keyup:81','wait:3','fx4qa:fixture:aim-owned-item'];notes+=['Real native Q toss; provenance required, item position/velocity not fabricated.']
            if skill in {'nitrogen_item_push','aero_launch'}:
                s[-7:]=['cmd:item replace entity @s weapon.mainhand with minecraft:cobblestone 16']
                q['action']=['aim:0.5,-57.7,6.5','keydown:81','wait:1','keyup:81','fx4qa:fixture:aim-owned-item']+press()
                notes+=['Toss occurs in recording just before R while the native item is airborne; pushing an old grounded item along a downward aim correctly fails collision safety.']
        if skill in {'dark_feather_guard','nitrogen_guard','aero_arrow_deflect'}:q['instantFixture']='incoming-arrow';q['points']=[];q['aim']=(.5,-58.38,6)
        if skill in {'aero_brake','nitrogen_brake','dark_wing_brake'}:
            q['points']=[];s+=['keydown:341','keydown:87','wait:12']
            jump=['keydown:32','wait:1','keyup:32'] if skill=='dark_wing_brake' else []
            q['action']=['cmd:data get entity @s Motion','cmd:data get entity @s abilities','live:motion-preflight']+jump+press()+['keyup:87','keyup:341']
            notes+=['Actual sprint/walk velocity, no movement setter. Read-only native server Motion/abilities and client motion preflight diagnose admission honestly.']
        if skill in {'aero_fall_brake','nitrogen_buoy','dark_wing_rescue','aero_double_jet'}:
            s+=['vcam:7.5,-49,-2,0.5,-53,4'];q.update(settle=0,lead=0,points=[])
            q['action']=['cmd:tp @s 0.5 -49 0.5 0 0']+press()+['cmd:data get entity @s Motion','live:motion-preflight']
            notes+=['Real gravity fall: fixture TP occurs inside the recording immediately before normal R; no setup/aim/GUI lead can use up the airborne window. Native server Motion and client motion are read-only diagnostics.']
        if skill in {'dark_lift','aero_vertical_jet'}:
            q['action']=['keydown:32','wait:2','keyup:32']+press()+['cmd:data get entity @s Motion','live:motion-preflight'];q['points']=[]
            notes+=['Normal jump near the apex, with no previous wait8 that lets the caster land before R.']
        if skill=='aero_airborne_turn':
            q.update(points=[],settle=0,lead=0,aim=(.5,-58.38,10.5))
            q['action']=['keydown:341','keydown:87','wait:10','keydown:32','wait:1','keyup:32','aim:10.5,-58.0,3.5']+press()+['keyup:87','keyup:341']
            notes+=['Actual sprint jump and approximately right-angle client aim, with movement input retained until after R; no turn or speed setter.']
        if skill=='dark_wing_charge':
            q.update(points=[(.5,1.8)],aim=(.5,-59.0,1.8))
            q['action']=['keydown:32','wait:1','keyup:32','aim:0.5,-59.0,1.8']+press()
            notes+=['Re-aim at the real target after jumping; pre-jump aim otherwise rises above this nearby target.']
        if skill=='nitrogen_shock_ring':q['action']=['keydown:32','wait:2']+press()+['keyup:32']
        if skill=='nitrogen_impact':
            q['aim']=(.5,-60.2,2.1);q['action']=['cmd:tp @s 0.5 -57 0.5']+press()+['cmd:data get entity @s Motion','live:motion-preflight']
            notes+=['Real three-block gravity fall immediately before R, not a narrow late-jump window. Pre-aim lower before fixture TP so the falling owner ray crosses the real nearby hostile at R; no horizontal impulse is needed by the native impact rule.']
        if skill in {'nitrogen_dome','nitrogen_armor','dark_wings','dark_guard','dark_panel_dome','nitrogen_plate','nitrogen_shield_line','nitrogen_bulwark','aero_pressure_wall','aero_air_curtain','dark_shard_screen','nitrogen_fall_guard'}:q['post']=160
        if skill in {'dark_wings','dark_construct'}:
            q['post']=225;notes+=['Record the real complete 200-tick lease and native server END, with a visible tail after expiry.']
    elif cat=='float_dial':
        q.update(ai=True,post=110)
        if skill.startswith('float_item_') or skill in {'float_release_field'}:
            q['points']=[];s += ['cmd:item replace entity @s weapon.mainhand with minecraft:cobblestone 16','aim:0.5,-58.8,6.5','keydown:81','wait:1','keyup:81','wait:3','fx4qa:fixture:aim-owned-item']
            if skill in {'float_item_lower','float_item_pull','float_item_release'}:
                q['preRecord']=slot('float_item_lift')+['fx4qa:fixture:aim-owned-item']+press()+slot(skill)
            if skill=='float_release_field':q['preRecord']=slot('float_item_field')+press()+slot(skill)
            notes+=['Owned item must originate from actual Q; lower/pull/release start from actual lift lease.']
            if q.get('preRecord'):notes+=['The real prerequisite lease lasts only40ticks. UI/aim/setup are finished first, then actual prerequisite R and a same-category slot rebind occur immediately before the recorded tested R. No lease expiry or backend gate is changed.']
        elif skill in {'float_hover','float_ascent','float_descent','float_air_brake','float_rescue_field'}:
            q['points']=[];s+=['vcam:7.5,-49,-2,0.5,-53,4'];q.update(settle=0,lead=0)
            q['action']=['cmd:tp @s 0.5 -49 0.5 0 0']+press()
            notes+=['Actual airborne/falling owner, no levitation/noGravity flag; fixture TP is in the clip immediately before R so setup lead cannot consume the airborne window.']
        elif skill in {'float_field','float_hover_field'}:q['points']=CROWD
    elif cat=='hydro_hand':
        s+=['cmd:setblock -2 -61 1 minecraft:water'];q['post']=105
        if skill in {'hydro_current','hydro_lift','hydro_brake','hydro_dive','hydro_lateral_current','hydro_brake_field'}:
            q.update(points=[],aim=(.5,-61,10.5),settle=0,lead=1);s+=['cmd:fill -3 -64 -3 4 -64 12 minecraft:stone','cmd:fill -3 -63 -3 4 -61 12 minecraft:water','wait:70','keydown:32','wait:6'];q['action']=press()+['keyup:32']
            notes+=['Three-layer real water, actual Space swim off floor before normal R.']
        if skill in {'hydro_domain','hydro_eddy','hydro_repel','hydro_undertow'}:
            q.update(points=CROWD,ai=True);s+=['cmd:fill -3 -63 2 4 -62 8 minecraft:water','cmd:fill -3 -61 2 4 -61 8 minecraft:water']
            if skill in {'hydro_repel','hydro_undertow'}:
                q.update(points=[],aim=(.5,-59.6,3.5))
                s+=['cmd:setblock 0 -62 3 minecraft:stone','cmd:summon minecraft:husk 0.5 -61 3.5 {Tags:["complete_target"],Silent:1b,PersistenceRequired:1b,NoAI:0b}','cmd:attribute @e[type=minecraft:husk,tag=complete_target,limit=1] minecraft:generic.movement_speed base set 0']
                q['preRecord']=['cmd:data get entity @e[type=minecraft:husk,tag=complete_target,limit=1] Pos','cmd:data get entity @e[type=minecraft:husk,tag=complete_target,limit=1] Motion']
                q['action']=['cmd:setblock 0 -62 3 minecraft:water']+press()+['cmd:data get entity @e[type=minecraft:husk,tag=complete_target,limit=1] Pos']
                notes+=['Single real AI-enabled speed0 hostile stands waist-deep on a real temporary support. Restore support to water in the recording, then R during its real underwater gravity fall: target remains within aim/range and away from the floor, so the native downward-component collision fence stays satisfied. Native Pos/Motion are read-only queries.']
        if skill=='hydro_douse_self':q['points']=[];s+=['cmd:setblock 0 -60 0 minecraft:fire','wait:8','cmd:setblock 0 -60 0 minecraft:air'];notes+=['Actual vanilla fire contact then source block removed; source water is an isolated below-ground well, not flowing across the fire.']
        if skill=='hydro_douse_hostile':s+=['cmd:execute as @e[type=minecraft:husk,tag=complete_target] run data merge entity @s {Fire:160s}']
        if skill in {'hydro_irrigate','hydro_campfire_out','hydro_candle_out','hydro_extinguish'}:
            block={'hydro_irrigate':'minecraft:farmland[moisture=0]','hydro_campfire_out':'minecraft:campfire[lit=true]','hydro_candle_out':'minecraft:candle[lit=true]','hydro_extinguish':'minecraft:fire'}[skill]
            q.update(points=[],aim=(.5,-59.8,3.5));s+=['cmd:setblock 0 -61 3 minecraft:stone',f'cmd:setblock 0 -60 3 {block}']
            if skill=='hydro_irrigate':
                s+=['cmd:setblock -2 -61 1 minecraft:grass_block','cmd:setblock -1 -61 -2 minecraft:water','cmd:setblock 0 -61 3 minecraft:farmland[moisture=0]','cmd:setblock 0 -60 3 minecraft:air'];q['aim']=(.5,-60.2,3.5)
                notes+=['Visible contained source is within real skill reach but five blocks from farmland along Z, outside vanilla natural hydration range; dry soil cannot become hydrated by an unrelated random tick before R.']
            if skill=='hydro_extinguish':s+=['cmd:setblock 0 -60 4 minecraft:stone'];q['aim']=(.5,-59.5,4.5)
        if skill=='hydro_transfer':
            q.update(points=[],aim=(.5,-58.4,3.5));s+=['cmd:setblock 0 -59 3 minecraft:water','cmd:setblock 3 -59 0 minecraft:stone']
            q['action']=press()+['wait:7','aim:3.5,-58.5,0.5']+press()+['wait:10'];notes+=['Two ordinary R casts select actual source then intended support face; selection is free/noVFX; final original START must confirm the second paid movement.']
    elif cat=='liquid_shadow':
        q.update(points=[],post=100);s+=['cmd:item replace entity @s weapon.mainhand with academy:dense_shadow_medium 1']
        if skill=='liquid_scout':
            q['action']=press()+['wait:8','keydown:340'];q['post']=425
            notes+=['Record the real full 400-tick body lease and native expiry END; after a short ordinary body advance, real Sneak keeps the body within camera until expiry.']
        if skill!='liquid_scout':
            s+=['keydown:340']+slot('liquid_scout')+press()+['wait:5']
            if skill not in {'liquid_pause','liquid_recall','liquid_dismiss','liquid_follow','liquid_retreat'}:s+=slot('liquid_pause')+press()+['wait:3']
            s+=['keyup:340']
            s+=slot(skill)
        if skill in {'liquid_strike','liquid_push','liquid_pulse','liquid_shadow_field','liquid_grapple','liquid_contact_cut','liquid_contact_sweep','liquid_contact_crush','liquid_survey','liquid_dual_sensor'}:
            s+=['cmd:execute at @e[type=academy:liquid_shadow_body,limit=1] run summon minecraft:husk ~ ~ ~0.45 {NoAI:1b,Silent:1b,Tags:["body_touch"]}'];q['aim']=(.5,-59.0,1.6)
        if skill=='liquid_route':q['aim']=(.5,-61,4.5)
        if skill=='liquid_switch':
            s+=['cmd:execute at @e[type=academy:liquid_shadow_body,limit=1] run setblock ~ ~-1 ~2 minecraft:stone','cmd:execute at @e[type=academy:liquid_shadow_body,limit=1] run setblock ~ ~ ~2 minecraft:lever[face=floor,facing=north]'];q['aim']=(.5,-60.4,3.5)
        if skill in {'liquid_intercept','liquid_screen'}:q['instantFixture']='incoming-arrow';notes+=['Incoming arrow is prepared in owner-forward corridor; verify actual body proximity, not just owner proximity.']
        notes+=['Body and pause are created by actual prerequisite R inputs, not fabricated lease/entity. Real Sneak during prerequisite binding keeps the initial body near owner/native lever reach; it is released before the tested normal R.']
    elif cat=='paper_psychokinesis':
        s+=[PAPER];q.update(points=[],post=110)
        if skill in {'paper_blade','paper_array'}:q['points']=CROWD
        elif skill=='paper_wing':
            s+=['vcam:8,-54,-1,0.5,-57,5'];q['aim']=(.5,-52,10.5);q['action']=['keydown:32','wait:3']+press()+['keyup:32'];q['post']=80
        elif skill=='paper_plane':pass
        elif skill=='paper_armor':q['post']=135
        else:
            if skill!='paper_retract':
                q['post']=425;notes+=['Real physical paper blocks have 400-tick leases; keep the complete model through natural server removal and a visible tail. No fabricated Canon END is expected for native block updates.']
            s+=['cmd:setblock 0 -60 4 minecraft:stone'];q['aim']=(.5,-59.5,4.5)
            if skill in {'paper_retract','paper_plate','paper_switch','paper_ladder','paper_gate'}:
                s+=slot('paper_patch')+['aim:0.5,-59.5,4.5']+press()+['wait:5']+slot(skill)
                q['aim']=(.5,-59.5,3.5)
                if skill=='paper_plate':s+=['cmd:setblock 0 -59 0 minecraft:stone','cmd:tp @s 0.5 -58 0.5 0 0'];q['aim']=(.5,-59.0,3.5)
            if skill in {'paper_step','paper_pad','paper_ramp','paper_lift_bridge','paper_platform','paper_puzzle_link','paper_switch_bridge'}:
                s+=['cmd:setblock 0 -59 0 minecraft:stone','cmd:tp @s 0.5 -58 0.5 0 0'];q['aim']=(.5,-59.0,4.5)
            if skill=='paper_emergency_stairs':s+=['cmd:fill -1 -64 0 1 -59 3 minecraft:air','cmd:setblock 0 -58 0 minecraft:stone','cmd:tp @s 0.5 -57 0.5 0 0','vcam:5,-51,-4,0.5,-60,2'];q['aim']=(.5,-59.8,4.5)
            if skill=='paper_step':q['aim']=(.5,-59.7,4.5)
            if skill in {'paper_pad','paper_ramp','paper_lift_bridge','paper_platform','paper_puzzle_link','paper_switch_bridge','paper_step','paper_plate'}:s+=['vcam:9,-53,-3,0.5,-56,4']
            notes+=['Real vanilla support and consumed sheets; owned-support techniques cast paper_patch first.']
    else:
        q['sourceReviewed']=False;notes+=['Classic/highpower fixture is an executable first pass; use existing dedicated gameplay acceptance and OWN17 overrides for complex mechanisms. A rejection remains a rejection.']
        if skill in {'railgun'}:s+=['cmd:item replace entity @s weapon.mainhand with minecraft:iron_ingot 16'];q['action']=press(30)
        if skill in {'meltdowner','scatter_bomb','electron_missile','plasma_cannon','thunder_clap'}:q['action']=press({'meltdowner':26,'scatter_bomb':42,'electron_missile':45,'plasma_cannon':66,'thunder_clap':66}[skill])
        if skill in {'dir_shock','flesh_ripping','blood_retro','dir_blast'}:q.update(points=NEAR,aim=(.5,-59.2,2.1));q['action']=press(12)
        if skill in {'point_meltdown','beam_etching','mine_ray_basic','mine_ray_expert','mine_ray_luck'}:q.update(points=[],aim=(.5,-58.5,4.5));s+=['cmd:setblock 0 -59 4 minecraft:stone','cmd:item replace entity @s weapon.mainhand with minecraft:diamond_pickaxe 1'];q['action']=press(35)
        if skill=='mag_manip':s+=['cmd:item replace entity @s weapon.mainhand with minecraft:iron_block 1'];q['action']=press(24)
        if skill in {'threatening_teleport','shift_tp'}:s+=['cmd:item replace entity @s weapon.mainhand with minecraft:cobblestone 16']
        if skill in {'iron_sand_lance'}:s+=['cmd:item replace entity @s weapon.offhand with academy:iron_sand_medium 32']
        if skill in {'magnetic_item_pull'}:s+=['cmd:item replace entity @s weapon.mainhand with minecraft:iron_ingot 16','keydown:81','wait:1','keyup:81','wait:3','fx4qa:fixture:aim-owned-item']
    if 'fx4qa:fixture:aim-owned-item' in s:
        q['settle']=0;q['lead']=0;q['action']=['fx4qa:fixture:aim-owned-item']+q['action']
    return q

class Batch:
    def __init__(self,name):self.name=name;self.steps=HEAD.copy();self.clips=[]
    def add(self,row,q):
        name='complete_'+row['id'];self.steps+=reset()+[f"live:setup:{row['id']}",'wait:30']
        self.steps+=enemies(q['points'],q['ai'])+q['setup']+[f"wait:{q['settle']}",f"aim:{','.join(map(str,q['aim']))}",'gui:off','live:clearui']
        self.steps+=q.get('preRecord',[])
        if q['instantFixture'] and q['instantFixture']!='incoming-arrow':self.steps += ['fx4qa:fixture:'+q['instantFixture']]
        immediate=['fx4qa:fixture:incoming-arrow'] if q['instantFixture']=='incoming-arrow' else []
        self.steps += [f"fx4qa:begin:{row['id']}:real",f'mark:complete:{row["key"]}',f'rec:{name}',f"wait:{q['lead']}"]+immediate+q['action']+[f"wait:{q['post']}",'recend','fx4qa:end','gui:on']
        self.clips.append({'name':name,'skill':row['id'],'category':row['category'],'skillLevel':row['level'],'mode':'real','catalogKey':row['key'],'origin':row['origin'],'ownRenderer':row['ownRenderer'],'preconditions':q['notes'],'recipeSourceReviewed':q['sourceReviewed'],'sourceAcceptancePending':True,'requestedPostTicks':q['post'],'requestedAction':q['action'],'requestedBeforeRecord':q.get('preRecord',[]),'aim':q['aim'],'acceptance':'actual physical R, current-run server original network event or committed native class release; lifecycle END needs real before-owned pool decrease; pixels and gameplay mechanism separate'})
    def write(self,out):
        text='|'.join(self.steps+['quit'])+'\n';p=out/(self.name+'.script');p.write_text(text)
        (out/(self.name+'-clips.json')).write_text(json.dumps({'suite':'canon-complete-acceptance','batch':self.name,'clips':self.clips,'scriptSha256':sha(p),'frames':'one original native screenshot per client tick','noSyntheticVisuals':True,'successNotClaimedByGeneration':True},ensure_ascii=False,indent=2)+'\n')

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--out',type=Path,required=True);p.add_argument('--only',default='');p.add_argument('--batch-size',type=int,default=25);p.add_argument('--categories',default='');a=p.parse_args();a.out.mkdir(parents=True,exist_ok=True)
    rows=catalog();only=set(a.only.split(',')) if a.only else set();cats=set(a.categories.split(',')) if a.categories else set();selected=[x for x in rows if (not only or x['id'] in only or x['key'] in only) and (not cats or x['category'] in cats)]
    alias=defaultdict(list)
    for r in rows:alias[r['id']].append(r['key'])
    designs=(S/'src/main/java/cn/academy/port/canon/CanonVfxDesign.java').read_text();motifs=dict(re.findall(r'Map.entry\("([^"]+)",d\("[^"]+",Motif.([A-Z_]+)\)\)',designs))
    for r in rows:r['visualMotif']=motifs.get(r['id'],r['renderer']);r['sameIdCatalogKeys']=alias[r['id']];r['currentAcceptance']='PENDING_ACTUAL_EVIDENCE';r['recipe']=recipe(r) if r['controllable'] else None
    (a.out/'catalog-matrix.json').write_text(json.dumps({'records':305,'active':290,'passive':15,'canonNew':194,'classicOriginal':50,'highPower':61,'own17':sorted(OWN),'dualRequiredActive':sorted(DUAL),'uniqueCatalogKeys':305,'rows':rows,'sourceSha256':{str(x.relative_to(R)):sha(x) for x in [S/'src/main/resources/classic-skills.json',S/'docs/canon/skills.json',S/'src/main/java/cn/academy/port/highpower/HighPowerSkills.java',R/'docs/vfx-upgrade-skills-20261029.csv']},'scope':'Executable requested fixtures are not completed acceptance. All290 active IDs plus15 real passive behaviors need proof; no aliases silently substitute.'},ensure_ascii=False,indent=2)+'\n')
    groups=defaultdict(list)
    for r in selected:
        if r['controllable'] and r['id'] not in DUAL:groups[r['category']].append(r)
    batches=[]
    for cat,items in groups.items():
        for index,start in enumerate(range(0,len(items),a.batch_size),1):
            b=Batch(f'{cat}-{index:02d}')
            for row in items[start:start+a.batch_size]:b.add(row,recipe(row))
            b.write(a.out);batches.append({'name':b.name,'skills':[c['catalogKey'] for c in b.clips],'script':str((a.out/(b.name+'.script')).resolve()),'manifest':str((a.out/(b.name+'-clips.json')).resolve())})
    (a.out/'batch-index.json').write_text(json.dumps({'batches':batches,'soloRequested':sum(len(b['skills']) for b in batches),'dualCasesPendingSeparateActualLAN':sorted(DUAL),'passivesPendingActualBehavior':[r['key'] for r in selected if not r['controllable']],'noSuccessClaim':True},ensure_ascii=False,indent=2)+'\n')
    print(json.dumps({'catalog':len(rows),'active':290,'passive':15,'soloBatches':len(batches),'soloClips':sum(len(b['skills']) for b in batches),'dual':len(DUAL),'out':str(a.out)}))
if __name__=='__main__':main()
