#!/usr/bin/env python3
"""Isolated FX4 recording fixtures. Only the normal R key/GUI triggers real casts.

The recorder supplies the game directory and hidden agent. This generator never
launches a client. Labels, modes and intended preconditions accompany every clip;
Fx4RolloutQa receipts decide whether the server actually accepted a real cast.
"""
import argparse
import json
from pathlib import Path

FRONT=[(.5,5.0)]
NEAR=[(.5,2.1)]
CROWD=[(-2.0,5.5),(.5,6.0),(3.0,5.5),(-1.0,8.0),(2.0,8.5)]
HEAD=['wait:80','cmd:time set noon','cmd:gamerule doDaylightCycle false',
      'cmd:gamerule doMobSpawning false','cmd:weather clear','cmd:difficulty normal',
      'cam:third','vcam:7.5,-57.2,-1.5,0.5,-59.0,3.5','wait:20']
CLASSIC=[('arc_gen',10,50),('thunder_bolt',10,50),('railgun',30,60),('magnetic_domain',10,70),
         ('electron_bomb',10,55),('meltdowner',26,65),('scatter_bomb',42,65),('point_meltdown',10,60),
         ('threatening_teleport',10,50),('flesh_ripping',10,50),('shift_tp',10,55),
         ('tactical_coordinate_formation',3,65),('dir_shock',10,50),('ground_shock',10,60),
         ('dir_blast',10,55),('plasma_cannon',66,80)]
EXTRA_CLASSIC=[('mag_manip',24,55),('mine_detect',10,55),('thunder_clap',66,65),
               ('light_shield',24,50),('ray_barrage',12,65),('electron_missile',45,60),
               ('penetrate_teleport',10,55),('vec_accel',22,60),('blood_retro',12,55)]
PAPER='cmd:item replace entity @s weapon.mainhand with minecraft:paper 64'
SHADOW='cmd:item replace entity @s weapon.mainhand with academy:dense_shadow_medium 1'
POOL=['cmd:fill -2 -61 1 3 -61 3 minecraft:water']
SWIM=['cmd:fill -1 -61 -1 2 -61 2 minecraft:water','wait:14']
WADE=['cmd:fill -3 -61 2 4 -61 7 minecraft:air','cmd:fill -3 -62 2 4 -62 7 minecraft:water','cmd:fill -3 -61 2 4 -61 7 minecraft:water']
CLOSE=[(-1.5,3.0),(.5,3.5),(2.5,3.0),(-.5,4.5),(1.5,4.5)]

def mobs(points,ai=False,y=-60):
    steps=[]
    for x,z in points:
        tag='{Silent:1b,PersistenceRequired:1b'+('' if ai else ',NoAI:1b')+'}'
        steps.append(f'cmd:summon minecraft:husk {x} {y} {z} {tag}')
    if ai and points:steps.append('cmd:execute as @e[type=minecraft:husk] run attribute @s minecraft:generic.movement_speed base set 0')
    return steps

def reset():
    return ['escape','cmd:effect clear @s','cmd:kill @e[type=!minecraft:player]',
            'cmd:fill -12 -62 -8 12 -57 18 minecraft:air',
            'cmd:fill -12 -62 -8 12 -62 18 minecraft:dirt',
            'cmd:fill -12 -61 -8 12 -61 18 minecraft:grass_block',
            'cmd:tp @s 0.5 -60 0.5 0 0','wait:8']

class Builder:
    def __init__(self,prefix,only=None):self.steps=list(HEAD);self.clips=[];self.prefix=prefix;self.only=only
    def add(self,skill,hold=3,ticks=55,points=FRONT,setup=(),action=None,label=None,mode='real',aim=(.5,-59.2,5.0),notes=(),ai=False):
        label=label or skill
        if self.only and label not in self.only and skill not in self.only:return
        name=f'{self.prefix}_{label}'
        self.steps+=reset()+[f'live:setup:{skill}','wait:30']+list(setup)+mobs(points,ai)+['wait:10']
        self.steps += [f'aim:{aim[0]},{aim[1]},{aim[2]}','gui:off','live:clearui',
                       f'fx4qa:begin:{skill}:{mode}',f'mark:fx4clip:{name}',f'rec:{name}','wait:4']
        self.steps += list(action) if action is not None else ['keydown:82',f'wait:{hold}','keyup:82']
        self.steps += [f'wait:{ticks}','recend','fx4qa:end','gui:on']
        self.clips.append({'name':name,'skill':skill,'mode':mode,'holdTicksRequested':hold,'postActionTicksRequested':ticks,
                           'aim':list(aim),'preconditions':list(notes),'acceptance':'Fx4RolloutQa received server Visual for new-system real casts; committed payment plus native release for classic casts; pixels reviewed separately'})
    def classic(self,expanded=False):
        for skill,hold,ticks in CLASSIC+(EXTRA_CLASSIC if expanded else []):
            points=CROWD if skill in ('magnetic_domain','scatter_bomb','ground_shock') else NEAR if skill in ('flesh_ripping','dir_shock','dir_blast') else FRONT
            setup=[];action=None;notes=[]
            aim=(.5,-59.2,2.1) if points==NEAR else (.5,-59.2,5.0)
            if skill=='threatening_teleport':
                setup=['cmd:item replace entity @s weapon.mainhand with minecraft:iron_ingot 16'];notes=['nonempty expendable main-hand item']
            elif skill=='point_meltdown':
                setup=['cmd:setblock 0 -59 5 minecraft:stone','cmd:item replace entity @s weapon.mainhand with minecraft:diamond_pickaxe 1']
                points=[];aim=(.5,-58.5,5.0)
                notes=['one explicit visible dry stone block','canonical unenchanted diamond pickaxe','hold freezes the surface;ten actual server focus ticks follow release']
            elif skill=='tactical_coordinate_formation':
                setup=['cmd:tp @s 0.5 -60 5.5 0 0','wait:12','keydown:82','wait:3','keyup:82','wait:14',
                       'live:gui:tactical-save','wait:14','escape','wait:12','cmd:tp @s 0.5 -60 0.5 0 0','wait:14']
                action=['keydown:82','wait:3','keyup:82','wait:14','live:gui:tactical-confirm','wait:8']
                points=[]
                notes=['destination saved through native TacticalCoordinateScreen mouse handler','real self-transport confirmed through native GUI; not an invitation/two-player test']
            elif skill=='shift_tp':
                setup=['cmd:item replace entity @s weapon.mainhand with minecraft:cobblestone 16','cmd:setblock 0 -61 8 minecraft:stone']
                points=[(.5,3.0)];aim=(.5,-60.0,8.5)
                notes=['real BlockItem in main hand','clear placement space over explicit solid support atz8','living target between caster and placement;destination itself unoccupied']
            elif skill=='mag_manip':
                setup=['cmd:item replace entity @s weapon.mainhand with minecraft:iron_block 1']
                notes=['real accepted metal BlockItem creates native rotating-block context;release throws it']
            elif skill=='mine_detect':
                setup=['cmd:setblock 2 -61 4 minecraft:iron_ore','cmd:setblock -2 -61 6 minecraft:coal_ore'];points=[]
                notes=['utility scan over real ore blocks;native blindness is retained in clip then cleared at next fixture reset']
            elif skill=='penetrate_teleport':
                setup=['cmd:fill 0 -60 4 0 -57 4 minecraft:stone'];points=[];aim=(.5,-58.38,8.5)
                notes=['actual solid barrier and clear air behind;eye-height aim, normal native release teleport']
            elif skill=='blood_retro':points=NEAR;aim=(.5,-59.2,2.1);notes=['living body within native2-block ray']
            elif skill=='light_shield':points=[];notes=['real held guard;no attack or damage assertion']
            elif skill=='thunder_clap':notes=['hold exceeds source auto-fire60 ticks;release is cancellation after the actual strike']
            elif skill=='railgun':notes=['live:setup supplies real iron ingots','hold exceeds 20-tick native charge']
            elif skill=='meltdowner':notes=['hold exceeds native minimum20 ticks']
            elif skill=='scatter_bomb':notes=['hold exceeds FIRST_BALL20 and creates three balls before release']
            elif skill=='plasma_cannon':notes=['hold exceeds fresh mastery0 charge60 ticks','post-release records actual moving plasma and detonation']
            self.add(skill,hold,ticks,points,setup,action,aim=aim,notes=notes)
    def focus(self):
        self.add('mental_probe',ticks=45,points=[(.5,5.0)],notes=['AI hostile with movement speed0 at4.5m; actual HIT lasts10 ticks'],ai=True)
        self.add('mental_probe',ticks=45,points=NEAR,label='mental_probe_near',aim=(.5,-59.2,2.1),notes=['AI hostile with movement speed0 at1.6m; compare with farther same skill'],ai=True)
        self.add('float_lift',ticks=65,points=FRONT,notes=['real AI hostile in aim; no empty-target float fixture'],ai=True)
        self.add('aero_jet',ticks=55,points=FRONT)

    def preview(self,skill,phase='start',ticks=60,setup=(),points=FRONT):
        # Direct client visual fixture is always labelled and receipted as preview.
        self.add(skill,0,ticks+12,points,setup,[f'cmd:aca_canon_vfx {skill} {phase} {ticks}'],
                 label='preview_'+skill,mode='preview',notes=['direct QA visual fixture;not a skill cast or successful gameplay'])

    def hydro(self):
        for skill,setup,points,ticks,ai in [
            ('hydro_whip',POOL,FRONT,50,False),('hydro_pressure_jet',POOL,FRONT,50,False),
            ('hydro_fan',POOL,CLOSE,55,False),('hydro_shield',POOL,[],85,False),
            ('hydro_current',POOL+SWIM,[],65,False),('hydro_lift',POOL+SWIM,[],65,False),
            ('hydro_domain',POOL+WADE,CLOSE,85,True),('hydro_eddy',POOL+WADE,CLOSE,85,True)]:
            self.add(skill,ticks=ticks,setup=setup,points=points,ai=ai,
                notes=['real source water present','caster/mobs enter fluid through ordinary gravity where required;receipts include wet counts'])
        for skill,phase,ticks in [('hydro_current','start',40),('hydro_transfer','start',40),('hydro_rescue','start',60),('hydro_douse_self','start',40)]:
            self.preview(skill,phase,ticks,setup=POOL)

    def new(self):
        for skill,points,ticks in [('mental_probe',FRONT,45),('mental_disrupt',FRONT,50),('mental_out_crowd',CROWD,65)]:
            self.add(skill,points=points,ticks=ticks,ai=True,notes=['AI retained, movement speed0;mental eligibility rejects NoAI mobs'])
        self.add('mental_probe',points=NEAR,ticks=45,label='mental_probe_near',aim=(.5,-59.2,2.1),ai=True)
        self.add('mental_puppet',points=[],ticks=95,setup=['cmd:summon minecraft:cow 0.5 -60 4.0 {Silent:1b,PersistenceRequired:1b}',
                 'cmd:attribute @e[type=minecraft:cow,limit=1,sort=nearest] minecraft:generic.movement_speed base set 0',
                 'cmd:summon minecraft:husk 4.5 -60 9.0 {NoAI:1b,Silent:1b}'],aim=(.5,-59.2,4.0),notes=['real AI-enabled cow with movement speed0;puppet admission rejects NoAI','arbitrary non-player puppet;not a player/Boss claim'])
        for skill,points,ticks in [('dark_blade',FRONT,40),('dark_wings',[],85),('dark_rain',CROWD,65),('dark_construct',CROWD,85)]:
            self.add(skill,points=points,ticks=ticks)
        self.add('dark_synthesis',points=[],ticks=45,setup=['cmd:give @s academy:dark_matter_crystal 2',
                 'cmd:item replace entity @s weapon.mainhand with minecraft:diamond_sword 1'],notes=['real crystal and main-hand sword;existing unlimited-copy behavior untouched'])
        self.add('nitrogen_armor',points=[],ticks=55)
        for skill in ('nitrogen_punch','nitrogen_ground_wave'):
            setup=['live:setup:nitrogen_armor','wait:30','keydown:82','wait:3','keyup:82','wait:8',f'live:slot:{skill}','wait:4']
            self.add(skill,points=CLOSE,ticks=55,setup=setup,notes=['native armor cast first;live:slot preserves real armor and budget'])
        for skill,points,ticks in [('aero_jet',FRONT,45),('aero_ground_blast',CROWD,65),('aero_barrage',CROWD,65)]:
            self.add(skill,points=points,ticks=ticks)
        for skill,points,ticks in [('float_lift',FRONT,65),('float_suspend',FRONT,65),('float_field',CROWD,75)]:
            self.add(skill,points=points,ticks=ticks,ai=True,notes=['real hostile targets with AI and movement speed0'])
        self.add('liquid_scout',points=[],ticks=55,setup=[SHADOW],notes=['real dense shadow medium on supported flat ground'])
        body=['live:setup:liquid_scout','wait:30',SHADOW,'wait:4','keydown:82','wait:3','keyup:82','wait:4',
              'live:slot:liquid_pause','wait:3','keydown:82','wait:3','keyup:82','wait:3',
              'live:slot:liquid_shadow_field','wait:4']
        body += [f'cmd:execute at @e[type=academy:liquid_shadow_body,limit=1] run summon minecraft:husk ~{x} ~ ~{z} {{NoAI:1b,Silent:1b}}'
                 for x,z in ((1.2,.5),(-1,1),(.4,-1.3),(1.6,1.6))]
        self.add('liquid_shadow_field',points=[],ticks=55,setup=body,notes=['body spawned via native scout then paused via native liquid_pause to remain in frame','targets spawned around actual body;main field still normal R'])
        for skill,points,ticks in [('paper_blade',FRONT,40),('paper_wing',[],75),('paper_array',CROWD,75)]:
            self.add(skill,points=points,ticks=ticks,setup=[PAPER],notes=['64 real paper material in main hand'])
        for skill,phase,ticks in [('mental_hostile_scan','start',40),('dark_panel_dome','start',60),('nitrogen_dash','start',30),('nitrogen_dome','start',60),
            ('aero_vortex','start',60),('aero_pressure_wall','start',60),('float_descent','start',50),('float_item_orbit','start',50),
            ('liquid_strike','hit',30),('liquid_listen','start',50),('liquid_screen','start',60),('paper_walkway','start',50),('paper_armor','start',60)]:
            self.preview(skill,phase,ticks)
        self.hydro()

def main():
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('--suite',choices=('baseline','focus','classic','new','hydro'),default='baseline')
    p.add_argument('--prefix',default='baseline');p.add_argument('--only',default='');p.add_argument('--out',type=Path);p.add_argument('--manifest',type=Path)
    p.add_argument('--expanded',action='store_true',help='Add9 classic grade representatives to original16 (all four systems cover skill levels1..5).')
    a=p.parse_args();assert a.prefix.replace('_','').replace('-','').isalnum()
    b=Builder(a.prefix,set(a.only.split(',')) if a.only else None)
    if a.suite in ('baseline','classic'):b.classic(a.expanded)
    if a.suite in ('baseline','focus'):b.focus()
    if a.suite=='new':b.new()
    if a.suite=='hydro':b.hydro()
    script='|'.join(b.steps+['quit'])+'\n'
    if a.out:a.out.parent.mkdir(parents=True,exist_ok=True);a.out.write_text(script)
    else:print(script,end='')
    if a.manifest:
        a.manifest.parent.mkdir(parents=True,exist_ok=True);a.manifest.write_text(json.dumps({'suite':a.suite,'prefix':a.prefix,'clips':b.clips,'frames':'one native screenshot per client tick;20fps playback','note':'Fixtures request casts, receipts decide acceptance. No claim of successful gameplay from script creation.'},ensure_ascii=False,indent=2)+'\n')

if __name__=='__main__':main()
