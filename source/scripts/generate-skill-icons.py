#!/usr/bin/env python3
"""Original 32px skill pictograms, drawn from semantic primitives. Never reads/copies third-party imagery.
Run with Pillow available; outputs a separate skill_icons_v2 resource namespace and provenance manifest.
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont
import re,json,hashlib,math
ROOT=Path(__file__).resolve().parents[1];JAVA=ROOT/'src/main/java/cn/academy/port'
OUT=ROOT/'src/main/resources/assets/academy/textures/skill_icons_v2'
REPORT=ROOT.parent/'docs/skill-icons-v2';REPORT.mkdir(parents=True,exist_ok=True)
COLORS={'electromaster':(100,221,255),'meltdowner':(135,251,161),'teleporter':(201,159,255),'vecmanip':(231,240,255)}
W=(245,251,255);GOLD=(255,205,104);RED=(255,153,142)
class Icon:
 def __init__(self,cat,level):
  self.im=Image.new('RGBA',(32,32));self.d=ImageDraw.Draw(self.im);self.c=COLORS[cat];self.level=level
  # Open technical registration marks, no opaque panel and no letters/numbers.
  for x,y,sx,sy in [(1,1,1,1),(30,1,-1,1),(1,30,1,-1),(30,30,-1,-1)]:
   self.line([(x,y+sy*3),(x,y),(x+sx*3,y)],self.c)
  if level>=4:
   self.line([(9,1),(13,1),(15,3),(17,1),(22,1)],GOLD)
   if level>=5:self.line([(10,30),(14,30),(16,28),(18,30),(22,30)],GOLD)
  # Category changes shape, not just tint; visually separate scientific disciplines.
  if cat=='electromaster':self.line([(4,4),(6,4),(4,7),(6,7)],self.c)
  if cat=='meltdowner':self.line([(4,4),(7,7)],self.c);self.line([(6,4),(8,6)],self.c)
  if cat=='teleporter':self.poly([(5,3),(8,6),(5,9),(2,6)],self.c)
  if cat=='vecmanip':self.arrow(3,7,7,3,self.c)
 def line(self,p,c=None,w=1):self.d.line(p,fill=c or self.c,width=w)
 def poly(self,p,c=None,fill=None,w=1):
  if fill:self.d.polygon(p,fill=fill)
  self.d.line(p+[p[0]],fill=c or self.c,width=w)
 def box(self,x,y,w,h,c=None,fill=None):self.d.rectangle((x,y,x+w,y+h),outline=c or self.c,fill=fill)
 def circle(self,x,y,r,c=None,fill=None):self.d.ellipse((x-r,y-r,x+r,y+r),outline=c or self.c,fill=fill)
 def arc(self,x,y,r,a,b,c=None):self.d.arc((x-r,y-r,x+r,y+r),a,b,fill=c or self.c,width=1)
 def arrow(self,x,y,X,Y,c=None,w=1):
  c=c or self.c;self.line([(x,y),(X,Y)],c,w);ang=math.atan2(Y-y,X-x);s=3
  self.line([(round(X-s*math.cos(ang-.65)),round(Y-s*math.sin(ang-.65))),(X,Y),(round(X-s*math.cos(ang+.65)),round(Y-s*math.sin(ang+.65)))],c,w)
 def bolt(self,x=11,y=6,s=1,c=None):
  pts=[(7,0),(1,9),(5,9),(0,18),(12,6),(7,6)]
  self.poly([(round(x+a*s),round(y+b*s)) for a,b in pts],c or W)
 def shield(self,x=9,y=7,w=14,h=19,c=None):self.poly([(x,y),(x+w,y),(x+w-1,y+h*.55),(x+w*.5,y+h),(x+1,y+h*.55)],c or self.c)
 def person(self,x=16,y=9,c=None):
  self.circle(x,y,2,c);self.line([(x,y+3),(x,y+10)],c);self.line([(x-5,y+6),(x,y+4),(x+5,y+6)],c);self.line([(x-4,y+15),(x,y+10),(x+4,y+15)],c)
 def palm(self,x=12,y=13,c=None):
  self.line([(x-3,y+7),(x-3,y+1),(x-1,y+3),(x-1,y-5),(x+1,y-5),(x+1,y),(x+2,y-7),(x+4,y-7),(x+4,y),(x+5,y-5),(x+7,y-5),(x+7,y+4),(x+4,y+9),(x,y+9),(x-3,y+7)],c or W)
 def cube(self,x=16,y=16,r=7,c=None):
  self.poly([(x,y-r),(x+r,y-r/2),(x+r,y+r/2),(x,y+r),(x-r,y+r/2),(x-r,y-r/2)],c)
  self.line([(x-r,y-r/2),(x,y),(x+r,y-r/2)],c);self.line([(x,y),(x,y+r)],c)
 def target(self,x=16,y=16,r=7,c=None):
  self.circle(x,y,r,c);self.circle(x,y,2,c)
  for dx,dy in [(1,0),(-1,0),(0,1),(0,-1)]:self.line([(x+dx*(r-1),y+dy*(r-1)),(x+dx*(r+3),y+dy*(r+3))],c)
 def wing(self,x=16,y=17,c=None):
  self.line([(x-1,y),(x-5,y-7),(x-12,y-9),(x-8,y+1),(x-3,y+4)],c or W)
  self.line([(x+1,y),(x+5,y-7),(x+12,y-9),(x+8,y+1),(x+3,y+4)],c or W)
  for s in [-1,1]:self.line([(x+s*4,y-3),(x+s*9,y-5)],self.c);self.line([(x+s*3,y+1),(x+s*7,y-1)],self.c)
 def portal(self,x,y,w=6,h=15,c=None):self.d.ellipse((x-w/2,y-h/2,x+w/2,y+h/2),outline=c or self.c)
 def beam(self,x,y,X,Y,c=None,w=2):self.line([(x,y),(X,Y)],c or W,w)
 def mirror(self,x=16,y=16,c=None):self.poly([(x-3,y-8),(x+3,y-5),(x+3,y+8),(x-3,y+5)],c)
 def grid(self,x=6,y=8,w=20,h=16,n=4,c=None):
  self.box(x,y,w,h,c)
  for k in range(1,n):self.line([(x+w*k/n,y),(x+w*k/n,y+h)],c);self.line([(x,y+h*k/n),(x+w,y+h*k/n)],c)
 def waves(self,x=16,y=15,c=None):
  for r in [4,7,10]:self.arc(x,y,r,210,330,c);self.arc(x,y,r,30,150,c)
 def book(self,x=7,y=10,c=None):
  self.poly([(x,y),(x+8,y+2),(x+16,y),(x+16,y+14),(x+8,y+16),(x,y+14)],c)
  self.line([(x+8,y+2),(x+8,y+16)],c)
 def brain(self,x=16,y=13,c=None):
  self.line([(x,y+7),(x-6,y+6),(x-9,y+2),(x-8,y-4),(x-4,y-7),(x,y-6),(x+4,y-7),(x+8,y-4),(x+9,y+2),(x+6,y+6),(x,y+7),(x,y-5)],c or W)
  self.line([(x-7,y-2),(x-3,y-2),(x-3,y+3),(x-6,y+3)],self.c);self.line([(x+7,y-2),(x+3,y-2),(x+3,y+3),(x+6,y+3)],self.c)
 def chevrons(self,x=16,y=15,c=None):
  for y0 in [y-5,y,y+5]:self.line([(x-6,y0+3),(x,y0),(x+6,y0+3)],c or self.c)
 def drop(self,x=16,y=16,c=None):self.poly([(x,y-9),(x-6,y+1),(x-5,y+6),(x,y+8),(x+5,y+6),(x+6,y+1)],c or RED)
 def gear(self,x=16,y=16,r=8,c=None):
  self.circle(x,y,r,c);self.circle(x,y,r-4,c)
  for j in range(8):
   a=j*math.pi/4;self.line([(x+round(math.cos(a)*(r-1)),y+round(math.sin(a)*(r-1))),(x+round(math.cos(a)*(r+2)),y+round(math.sin(a)*(r+2)))],c)

# Each recipe uses shapes that depict the skill's actual action, rather than a numbered badge or recolor.
RECIPES={}
def recipe(ids,meaning):
 def reg(fn):
  for id in ids.split():RECIPES[id]=(meaning,fn)
  return fn
 return reg
@recipe('arc_gen','single short electric discharge')
def _(i):i.bolt(10,6);i.line([(8,15),(5,12),(4,16)],i.c);i.line([(24,16),(27,13),(26,19)],i.c)
@recipe('charging','charge collected between paired electrodes')
def _(i):i.box(7,11,5,11);i.box(20,11,5,11);i.bolt(12,5,.65);i.arrow(5,7,11,10);i.arrow(27,7,21,10)
@recipe('mag_movement','magnetic pull towards a supported block')
def _(i):i.cube(23,11,4);i.arrow(7,25,19,13,W,2);i.arc(22,12,8,70,180)
@recipe('mag_manip','suspended metal block controlled from the palm')
def _(i):i.cube(16,12,6);i.line([(7,26),(12,23),(22,23),(25,19)],W);i.line([(9,19),(8,15)],i.c);i.line([(24,19),(25,15)],i.c)
@recipe('mine_detect','ore vein identified by a radial sensor')
def _(i):i.cube(16,18,6);i.arc(16,15,11,195,345);i.arc(16,15,8,195,345);i.circle(16,18,2,W)
@recipe('body_intensify','enhanced body and jump with regeneration')
def _(i):i.person(16,10,W);i.arrow(6,23,6,13);i.line([(23,18),(27,18)],RED,2);i.line([(25,16),(25,20)],RED,2)
@recipe('thunder_bolt','descending branching lightning strike')
def _(i):i.bolt(10,5,1.05);i.line([(9,26),(16,23),(24,26)],i.c,2);i.line([(10,12),(6,12),(8,18)],i.c)
@recipe('railgun','white-hot projectile accelerated between parallel rails')
def _(i):i.line([(7,25),(23,5)],i.c,2);i.line([(11,28),(27,8)],i.c,2);i.beam(6,27,25,6,GOLD,3);i.circle(21,11,4,W)
@recipe('thunder_clap','massive discharge expands from an overhead core')
def _(i):i.circle(16,10,4,W);i.bolt(4,13,.65);i.bolt(21,13,.65);i.arc(16,18,10,0,180);i.arrow(16,14,16,27,W)
@recipe('forked_arc','electric arc splits into two target branches')
def _(i):i.bolt(11,13,.7);i.line([(15,17),(9,9),(6,11),(5,5)],W,2);i.line([(15,17),(23,8),(27,10),(27,5)],W,2)
@recipe('magnetic_bracer','an armored forearm wrapped in electrical guard')
def _(i):i.poly([(10,26),(7,15),(12,11),(19,12),(24,22),(19,27)],W);i.line([(10,20),(19,17),(22,20)],i.c,2);i.bolt(18,4,.5)
@recipe('charge_decoy','electric double behind the true caster')
def _(i):i.person(11,10,i.c);i.person(21,12,W);i.line([(5,25),(13,25)],i.c);i.bolt(22,3,.4)
@recipe('polarity_tether','two opposed poles joined by a tension line')
def _(i):i.circle(7,17,4);i.circle(25,12,4);i.line([(10,17),(14,13),(18,17),(22,12)],W,2);i.line([(5,17),(9,17)],RED);i.line([(25,10),(25,14)],W);i.line([(23,12),(27,12)],W)
@recipe('conductive_circuit','current follows a connected zigzag circuit')
def _(i):
 for x,y in [(7,24),(14,17),(22,22),(25,8)]:i.circle(x,y,2,W)
 i.line([(7,24),(7,17),(14,17),(22,17),(22,22),(25,22),(25,8)],i.c,2)
@recipe('grounding_return','electric charge safely discharged to earth')
def _(i):i.bolt(10,5,.8);i.line([(16,21),(16,24)],W,2);i.line([(8,24),(24,24)],W);i.line([(11,27),(21,27)],W);i.line([(14,29),(18,29)],W)
@recipe('magnetic_ground_step','magnetically propelled grounded footstep')
def _(i):i.poly([(8,14),(13,15),(15,22),(24,23),(25,27),(8,27)],W);i.arrow(11,11,26,11,i.c,2);i.line([(5,29),(26,29)],i.c)
@recipe('magnetic_item_pull','scattered item cubes drawn into a hand')
def _(i):i.cube(7,9,3);i.cube(25,9,3);i.cube(16,5,2);i.line([(8,16),(13,23),(19,23),(24,16)],W,2);i.arrow(8,14,13,20);i.arrow(24,14,19,20)
@recipe('iron_sand_lance','granular iron compressed into a piercing lance')
def _(i):i.poly([(7,25),(12,15),(25,5),(18,20)],W);i.line([(10,24),(24,7)],i.c)
@recipe('thunder_net','multiple electric fence nodes form a target net')
def _(i):i.grid(7,9,19,16,3);i.bolt(12,10,.65,W);i.circle(7,25,2);i.circle(26,9,2)
@recipe('potential_buffer','charge stored inside a protective capacitor')
def _(i):i.shield();i.line([(12,12),(20,12)],W,2);i.line([(12,17),(20,17)],W,2);i.arrow(16,5,16,10,GOLD)
@recipe('rail_thunder_array','three accelerating rails converge on a strike')
def _(i):
 for x in [7,16,25]:i.line([(x,26),(x,11)],i.c,2);i.arrow(x,12,16,6,W)
 i.circle(16,6,3,GOLD)
@recipe('magnetic_domain','a central magnet controls an extended field')
def _(i):i.arc(16,16,10,30,330);i.arc(16,16,6,30,330);i.poly([(12,10),(12,19),(20,19),(20,10),(17,10),(17,16),(15,16),(15,10)],W);i.arrow(24,23,28,23)
@recipe('pulse_shield','a shield emits discrete outward electrical pulses')
def _(i):i.shield(11,8,11,17,W);i.arc(16,17,11,205,335);i.arc(16,17,8,205,335);i.bolt(14,11,.45)
@recipe('rail_relay','two rail anchors relay a projectile between them')
def _(i):i.box(4,10,6,12);i.box(22,10,6,12);i.beam(7,15,25,15,W,2);i.arrow(12,21,20,21,GOLD);i.circle(7,15,2,W);i.circle(25,15,2,W)
@recipe('electron_bomb','single particle bomb with a directional tail')
def _(i):i.circle(21,15,6,W);i.circle(21,15,2,fill=i.c);i.beam(5,15,14,15,i.c,2);i.line([(7,10),(12,12)],i.c);i.line([(7,21),(12,19)],i.c)
@recipe('rad_intensify','passive radiation is focused into a larger emission')
def _(i):i.circle(16,16,4,W);i.circle(16,16,8);i.arrow(16,9,16,4);i.arrow(10,20,5,25);i.arrow(22,20,27,25)
@recipe('scatter_bomb','a particle orb fragments into three directed rays')
def _(i):i.circle(11,17,5,W);i.arrow(17,16,27,7);i.arrow(17,17,28,17);i.arrow(17,18,26,26);i.circle(11,17,2,fill=i.c)
@recipe('light_shield','curved luminous front barrier blocks incoming force')
def _(i):i.arc(19,16,11,100,260,W);i.arc(19,16,8,100,260);i.arrow(27,16,19,16,RED);i.line([(18,11),(18,21)],W,2)
@recipe('meltdowner','a concentrated beam burns through its focused core')
def _(i):i.circle(10,16,5);i.beam(5,16,28,16,W,3);i.line([(13,11),(23,11)],i.c);i.line([(13,21),(23,21)],i.c)
@recipe('mine_ray_basic','focused beam breaks a solid ore block')
def _(i):i.cube(21,16,7);i.beam(5,16,21,16,W,2);i.line([(19,10),(22,15),(19,21)],RED)
@recipe('mine_ray_expert','expert mining fans rays across multiple blocks')
def _(i):
 for x,y in [(22,8),(24,17),(20,25)]:i.cube(x,y,3);i.beam(5,16,x,y,W)
@recipe('mine_ray_luck','precise mining beam extracts a valuable ore crystal')
def _(i):i.cube(21,16,7);i.beam(4,17,17,17,W,2);i.poly([(21,11),(24,15),(21,22),(18,15)],GOLD);i.line([(8,24),(11,24)],GOLD);i.line([(10,22),(10,26)],GOLD)
@recipe('ray_barrage','several particle cores fire a broad ray barrage')
def _(i):
 for y in [8,16,24]:i.circle(9,y,3,W);i.beam(13,y,28,y,i.c,2)
@recipe('jet_engine','forward jet thrust drives directional acceleration')
def _(i):i.poly([(20,7),(25,16),(20,25),(14,21),(14,11)],W);i.arrow(5,16,19,16,i.c,2);i.line([(6,8),(12,12)],i.c);i.line([(6,24),(12,20)],i.c)
@recipe('electron_missile','orbiting particle cores generate multiple guided rays')
def _(i):
 i.arc(16,16,10,0,320)
 for x,y in [(9,8),(26,14),(12,26)]:i.circle(x,y,3,W);i.arrow(x,y,17,16,i.c)
@recipe('dual_point','two charged focal points establish a crossfire')
def _(i):i.circle(7,10,4,W);i.circle(25,23,4,W);i.beam(10,12,24,21);i.beam(9,26,22,8,i.c);i.circle(16,16,2,GOLD)
@recipe('scatter_plates','one ray splits across a pair of scattering plates')
def _(i):i.mirror(12,16);i.mirror(23,16);i.beam(4,16,11,16,W,2);i.line([(13,15),(24,8),(26,5)],i.c);i.line([(13,17),(24,24),(26,27)],i.c)
@recipe('beam_etching','a particle pencil engraves a lasting angular incision')
def _(i):i.beam(5,6,15,18,W,2);i.line([(6,25),(13,20),(18,24),(26,17)],i.c,2);i.circle(14,18,3,GOLD)
@recipe('point_meltdown','all particle pressure converges on one melting point')
def _(i):i.target(17,17,6);i.beam(5,6,14,14);i.beam(29,6,20,14);i.beam(5,28,14,20);i.circle(17,17,3,W,fill=i.c)
@recipe('scatter_locator_plate','a stationary plate locks a visible coordinate')
def _(i):i.mirror(10,18);i.target(23,13,5);i.line([(12,17),(19,14)],W);i.line([(5,27),(15,27)],i.c)
@recipe('remote_scatter_relay','remote plate changes the beam route at a distance')
def _(i):i.mirror(10,16);i.mirror(24,16);i.beam(3,24,10,17);i.beam(11,16,23,16);i.beam(25,15,29,7);i.arc(17,8,4,180,360)
@recipe('thermal_shell','a warm particle shell dissipates incoming projectiles')
def _(i):i.shield(9,7,15,19);i.line([(13,22),(11,17),(14,13),(13,9)],RED);i.line([(19,22),(17,17),(20,13),(19,9)],W)
@recipe('folded_mirror_array','three folding mirrors form a reflected two-leg beam')
def _(i):i.mirror(8,18);i.mirror(24,18);i.beam(3,24,8,18);i.beam(9,17,23,10);i.beam(25,12,29,20);i.circle(16,14,2,GOLD)
@recipe('disintegration_sweep','a beam sweeps into a broad disintegration fan')
def _(i):
 i.arc(9,17,18,300,60,W)
 for y in [5,11,17,23,28]:i.line([(7,17),(25,y)],i.c)
@recipe('particle_laminate','multiple particle layers stagger into a defensive wall')
def _(i):
 for x,y in [(6,11),(12,8),(18,5)]:i.poly([(x,y),(x+7,y+3),(x+7,y+20),(x,y+17)],W if x==12 else i.c)
@recipe('directional_dissipation_curtain','a directional curtain breaks a hostile ray into fragments')
def _(i):i.line([(16,5),(16,27)],W,2);i.line([(20,5),(20,27)],i.c);i.arrow(4,16,14,16,RED,2);i.line([(22,15),(27,9)],i.c);i.line([(22,17),(27,23)],i.c)
@recipe('multicore_canopy','several elevated emitters form an enclosing ray canopy')
def _(i):i.arc(16,19,11,180,360,W);i.line([(5,19),(5,26)],i.c);i.line([(27,19),(27,26)],i.c)
@recipe('zero_gap_armor','tightly interlocked overlapping armor plates')
def _(i):i.shield(7,6,18,22,W);i.grid(11,10,10,11,2);i.poly([(13,16),(16,13),(19,16),(16,19)],GOLD)
@recipe('beam_step','short directed jet pulse propels one rapid step')
def _(i):i.poly([(10,10),(15,11),(17,21),(25,23),(25,27),(9,27)],W);i.arrow(3,17,11,17,i.c,2);i.beam(3,22,9,22,i.c)
@recipe('reverse_jet_brake','opposed jet force brakes the forward movement')
def _(i):i.arrow(5,10,24,10,W,2);i.line([(25,6),(25,26)],RED,2);i.arrow(22,21,8,21,i.c,2);i.line([(6,18),(6,24)],i.c)
@recipe('threatening_teleport','object penetrates a target through a displaced short path')
def _(i):i.circle(22,16,5);i.arrow(5,24,25,10,W,2);i.portal(11,18,5,14)
@recipe('dim_folding_theorem','passive spatial folding shortens a coordinate segment')
def _(i):i.grid(6,8,20,16,2);i.line([(6,24),(16,12),(26,24)],W,2);i.line([(9,6),(16,10),(23,6)],i.c)
@recipe('penetrate_teleport','movement passes through a solid wall')
def _(i):i.box(13,6,6,20);i.arrow(5,23,27,9,W,2);i.line([(15,8),(15,14)],i.c);i.line([(17,20),(17,24)],i.c)
@recipe('mark_teleport','a persistent destination marker receives a warp')
def _(i):i.target(23,10,5);i.arrow(5,26,21,13,W,2);i.box(4,21,6,6)
@recipe('flesh_ripping','a displaced cut tears a living target')
def _(i):i.person(16,9);i.line([(7,25),(25,7)],W,2);i.line([(12,15),(16,18),(20,14)],RED,2)
@recipe('location_teleport','a coordinate crosshair selects a distant landing')
def _(i):i.grid(6,8,20,17,3);i.target(18,16,5,W);i.arrow(5,27,12,22,GOLD)
@recipe('shift_tp','two objects exchange opposite positions')
def _(i):i.cube(8,18,4);i.cube(24,13,4);i.arrow(8,9,23,6,W);i.arrow(24,24,9,27,W)
@recipe('space_fluct','passive coordinate fluctuation around a spatial origin')
def _(i):i.grid(7,7,18,18,2);i.waves(16,16,W);i.poly([(16,12),(20,16),(16,20),(12,16)],i.c)
@recipe('flashing','rapid chained spatial steps follow one continuous route')
def _(i):
 for x,y in [(6,23),(15,17),(25,10)]:i.portal(x,y,5,10,W)
 i.arrow(6,23,25,10,i.c,2)
@recipe('safe_landing_probe','a downward probe verifies a safe landing surface')
def _(i):i.target(16,17,7);i.arrow(16,4,16,13,W);i.line([(8,24),(12,28),(25,23)],GOLD,2)
@recipe('displaced_projection','a projected double occupies a nearby coordinate')
def _(i):i.person(10,10,i.c);i.person(22,10,W);i.line([(5,28),(15,28)],i.c);i.box(17,7,10,21)
@recipe('phase_sidestep','a quick lateral warp avoids the incoming line')
def _(i):i.portal(9,17,5,17);i.portal(23,17,5,17);i.arrow(8,15,25,15,W,2);i.line([(16,4),(16,9)],RED)
@recipe('return_mark','a return beacon stores the departure position')
def _(i):i.box(7,9,7,15);i.arc(19,16,8,270,120,W);i.arrow(20,9,13,9,W);i.circle(10,16,2,GOLD)
@recipe('cross_substitution','crossed routes swap caster and target')
def _(i):i.box(5,8,7,7);i.circle(24,23,4);i.arrow(8,17,24,7,W);i.arrow(22,17,8,27,W)
@recipe('recovery_mark','a marked coordinate supports safe recovery')
def _(i):i.target(16,16,9);i.line([(12,16),(20,16)],W,2);i.line([(16,12),(16,20)],W,2);i.arrow(7,27,12,22)
@recipe('corner_exchange','a bent two-leg route swaps around an obstacle corner')
def _(i):i.box(15,14,11,12);i.arrow(5,25,5,9,W,2);i.arrow(5,9,24,9,W,2);i.circle(5,25,2);i.circle(24,9,2)
@recipe('short_interception','a short spatial barrier intercepts an approaching shot')
def _(i):i.portal(18,16,5,20,W);i.arrow(4,16,15,16,RED,2);i.line([(23,12),(27,8)],i.c);i.line([(23,20),(27,24)],i.c)
@recipe('spatial_pincer_corridor','opposed spatial gates pinch a target corridor')
def _(i):i.portal(7,16,6,20);i.portal(25,16,6,20);i.arrow(9,16,14,16,W);i.arrow(23,16,18,16,W);i.target(16,16,4,GOLD)
@recipe('multidimensional_landing_array','three linked landing coordinates form a spatial array')
def _(i):
 for x,y in [(7,23),(16,7),(25,23)]:i.poly([(x,y-3),(x+3,y),(x,y+3),(x-3,y)],W)
 i.line([(7,23),(16,7),(25,23),(7,23)],i.c);i.circle(16,18,3,GOLD)
@recipe('spatial_relay','two anchors pass a spatial route through a middle relay')
def _(i):i.portal(6,17,5,14);i.portal(26,17,5,14);i.poly([(16,10),(22,16),(16,22),(10,16)],W);i.arrow(5,17,26,17)
@recipe('tactical_coordinate_formation','a tactical group of coordinate beacons')
def _(i):i.grid(7,7,18,18,2);i.target(7,7,3,W);i.target(25,25,3,W);i.target(16,16,3,GOLD)
@recipe('emergency_return','an emergency fallback route returns to its protected beacon')
def _(i):i.shield(6,11,10,15);i.arrow(26,24,26,8,W);i.arrow(26,8,10,8,W);i.line([(9,18),(13,18)],RED);i.line([(11,16),(11,20)],RED)
@recipe('incident_bypass','a detour route bypasses a visible incident zone')
def _(i):i.box(12,12,8,9,RED);i.line([(4,20),(7,20),(7,7),(25,7),(25,20)],W,2);i.arrow(25,20,29,20,W)
@recipe('phase_corridor','parallel gates open a deep forward spatial corridor')
def _(i):
 for x,y,w,h in [(16,16,22,22),(18,16,14,14),(20,16,6,6)]:i.box(x-w/2,y-h/2,w,h)
 i.arrow(6,23,22,14,W,2)
@recipe('dir_shock','a palm delivers one directed kinetic strike')
def _(i):i.palm(12,13);i.arrow(21,17,29,17,RED,2)
@recipe('ground_shock','a downward impact fractures the ground')
def _(i):i.arrow(16,5,16,20,W,2);i.line([(4,25),(10,23),(13,28),(17,22),(22,26),(28,24)],RED,2)
@recipe('vec_accel','a charged forward vector propels the caster')
def _(i):i.person(9,10,W);i.arrow(15,11,27,11,RED,2);i.arrow(15,20,27,20,i.c,2)
@recipe('vec_deviation','an incoming vector is deflected to the side')
def _(i):i.shield(11,8,10,18);i.arrow(4,18,13,18,RED,2);i.arrow(14,18,24,7,W,2)
@recipe('dir_blast','a charged palm blasts a wide kinetic cone')
def _(i):i.palm(9,13);i.line([(18,16),(28,6)],RED);i.arrow(18,16,29,16,W,2);i.line([(18,16),(28,26)],RED)
@recipe('storm_wing','paired controllable wind wings generate flight')
def _(i):i.wing(16,18);i.line([(14,24),(16,27),(18,24)],RED);i.arrow(16,18,16,6,i.c)
@recipe('blood_retro','a reversed internal flow returns through a blood vessel')
def _(i):i.drop();i.arrow(9,23,9,10,W);i.arrow(22,10,22,23,W);i.line([(13,15),(19,15)],W)
@recipe('vec_reflection','a protective field returns a hostile vector to its source')
def _(i):i.shield(14,7,11,19,W);i.arrow(4,13,15,13,RED,2);i.arrow(15,20,4,20,i.c,2)
@recipe('plasma_cannon','confined wind plasma launches from an accelerating core')
def _(i):i.circle(12,16,8);i.circle(12,16,4,W);i.beam(12,16,29,16,W,3);i.line([(18,10),(27,6)],RED);i.line([(18,22),(27,26)],RED)
@recipe('deflection_strike','a strike redirects force at a sharp angle')
def _(i):i.palm(8,15);i.arrow(4,23,15,19,RED,2);i.arrow(16,18,28,7,W,2)
@recipe('vector_parry','a timed crossed guard parries an incoming vector')
def _(i):i.line([(8,8),(24,25)],W,3);i.line([(24,8),(8,25)],i.c,3);i.arrow(4,16,13,16,RED)
@recipe('braking_touch','contact arrests a moving target vector')
def _(i):i.palm(17,14);i.arrow(4,16,14,16,RED,2);i.line([(12,10),(12,22)],W,2)
@recipe('shear_windblade','two shearing air layers form a curved cutting blade')
def _(i):i.line([(5,23),(11,12),(22,7),(28,7),(21,14),(11,20),(5,23)],W,2);i.line([(9,26),(20,20),(26,14)],RED)
@recipe('inertial_anchor','a fixed anchor suppresses nearby motion')
def _(i):i.circle(16,7,3,W);i.line([(16,10),(16,25)],W,2);i.line([(7,16),(7,22),(16,27),(25,22),(25,16)],i.c,2);i.line([(10,15),(22,15)],RED)
@recipe('yaw_palm','a lateral palm force rotates the target trajectory')
def _(i):i.palm(11,15);i.arc(17,17,11,210,30,RED);i.arrow(27,19,24,25,RED)
@recipe('trajectory_return','a projectile follows a curved returning path')
def _(i):i.arc(17,16,10,245,115,W);i.arrow(15,6,6,8,RED,2);i.circle(24,23,3,i.c);i.line([(6,19),(13,19)],i.c)
@recipe('circulation_relief','a closed circulation loop vents excess pressure')
def _(i):i.arc(16,15,9,20,330,W);i.arrow(8,12,7,8,W);i.arrow(16,19,16,28,RED,2);i.line([(10,27),(22,27)],i.c)
@recipe('circulation_listening','an ear reads directional movement pressure waves')
def _(i):i.line([(18,25),(14,23),(14,18),(11,15),(12,9),(16,6),(21,7),(24,11),(24,16),(20,20),(20,25)],W);i.arc(15,13,5,200,340);i.arc(9,16,6,110,250,RED)
@recipe('vector_cage','a force cage confines a target at its center')
def _(i):i.cube(16,16,10);i.arrow(4,16,12,16,RED);i.arrow(28,16,20,16,RED);i.person(16,11,W)
@recipe('multi_axis_reflection','one center reflects vectors along multiple axes')
def _(i):i.circle(16,16,5,W)
@recipe('wind_pressure_lift','an upward pressure column lifts the caster')
def _(i):i.person(16,8,W);i.arrow(8,27,8,13,i.c,2);i.arrow(24,27,24,13,i.c,2);i.arc(16,26,8,180,360,RED)
@recipe('autonomous_vector_circle','an independent ring redirects nearby incoming force')
def _(i):i.circle(16,16,9);i.person(16,10,W);i.arrow(4,19,10,24,RED);i.arrow(27,11,21,7,RED)
@recipe('zenith_vector_dominion','a central sovereign field controls a broad volume')
def _(i):i.cube(16,17,10);i.poly([(8,8),(8,3),(13,7),(16,3),(19,7),(24,3),(24,8)],GOLD);i.arrow(16,26,16,13,W,2)
@recipe('impulse_split','one impact vector splits into three redistributed paths')
def _(i):i.arrow(3,17,13,17,RED,2);i.circle(16,17,3,W);i.arrow(19,17,28,7,i.c,2);i.arrow(19,17,29,17,i.c,2);i.arrow(19,17,28,27,i.c,2)
@recipe('vector_construct','a controlled rigid body is pulled, held and thrown')
def _(i):i.cube(18,13,7,W);i.line([(5,26),(10,22),(19,24),(24,20)],i.c,2);i.arrow(7,17,10,10,RED)
@recipe('brain_course','training connects a neural diagram to a workbook')
def _(i):i.book(7,13);i.brain(16,10,W)
@recipe('brain_course_advanced','advanced training develops branching neural circuits')
def _(i):
 i.brain(16,13,W)
 for x,y in [(7,24),(16,27),(25,24)]:i.line([(16,19),(x,y)],i.c);i.circle(x,y,2,GOLD)
@recipe('mind_course','mental training concentrates a complete cognitive lattice')
def _(i):i.brain(16,13,W);i.poly([(16,5),(27,16),(16,27),(5,16)],GOLD);i.circle(16,15,3,i.c)

# Functionally distinct finishing marks are part of the depiction, never an ID-derived noise pattern.
def finishing(i,sid):
 if sid=='iron_sand_lance':
  for x,y in [(5,22),(6,18),(7,28),(11,28),(3,25)]:i.box(x,y,1,1,fill=i.c)
 if sid=='multicore_canopy':
  for x,y in [(6,16),(11,10),(21,10),(26,16)]:i.circle(x,y,2,W);i.line([(x,y+2),(16,26)],i.c)
 if sid=='multi_axis_reflection':
  for x,y in [(5,5),(27,5),(5,27),(27,27)]:i.arrow(16+(x-16)*.4,16+(y-16)*.4,x,y,RED if x==y else i.c,2)

def catalog():
 rows=json.loads((ROOT/'src/main/resources/classic-skills.json').read_text())['skills']
 hp=(JAVA/'highpower/HighPowerSkills.java').read_text();const=dict(re.findall(r'\b([A-Z][A-Z_0-9]*)\s*=\s*"([a-z_0-9]+)"',hp));const['CATEGORY']='electromaster'
 for k,v in re.findall(r'\b([A-Z][A-Z_0-9]*)\s*=\s*"([a-z_0-9]+)"',(JAVA/'highpower/HighPowerCategories.java').read_text()):const['HighPowerCategories.'+k]=v
 def val(x):return x.strip('"') if x.startswith('"') else const[x]
 for m in re.finditer(r'new SkillCatalog\.Skill\(([^,]+),\s*([^,]+),\s*(\d+),\s*(true|false)',hp):rows.append(dict(category=val(m[1].strip()),id=val(m[2].strip()),level=int(m[3]),controllable=m[4]=='true'))
 return rows

def main():
 rows=catalog();manifest=[];hashes=set();lang=json.loads((ROOT/'src/main/resources/assets/academy/lang/zh_cn.json').read_text())
 font=ImageFont.truetype('/System/Library/Fonts/STHeiti Medium.ttc',14)
 small=ImageFont.truetype('/System/Library/Fonts/Supplemental/Arial.ttf',10)
 for s in rows:
  sid=s['id'];meaning,draw=RECIPES[sid];i=Icon(s['category'],s['level']);draw(i);finishing(i,sid)
  # Preserve an entirely transparent outer pixel, making atlas filtering and glow masks safe.
  i.d.rectangle((0,0,31,31),outline=(0,0,0,0))
  out=OUT/s['category']/(sid+'.png');out.parent.mkdir(parents=True,exist_ok=True);i.im.save(out,optimize=True)
  digest=hashlib.sha256(out.read_bytes()).hexdigest();assert digest not in hashes,(sid,'duplicate PNG');hashes.add(digest)
  key='ac.ability.'+s['category']+'.skill.'+sid+'.name';name=lang.get(key,lang.get('ac.ability.generic.skill.'+sid+'.name',sid))
  if name==sid:
   opts=[v for k,v in lang.items() if k.endswith('.'+sid+'.name')];name=opts[0] if opts else sid
  manifest.append(dict(category=s['category'],id=sid,level=s['level'],controllable=s['controllable'],name=name,meaning=meaning,texture='academy:textures/skill_icons_v2/'+s['category']+'/'+sid+'.png',sha256=digest,original=True,shape=sid))
 for cat in COLORS:
  subset=[x for x in manifest if x['category']==cat];w=1000;cellw=200;cellh=188;h=70+math.ceil(len(subset)/5)*cellh
  sheet=Image.new('RGB',(w,h),(12,19,29));d=ImageDraw.Draw(sheet);d.text((24,20),cat+' / Original 32px → 96px + actual 16px',font=font,fill=COLORS[cat])
  for n,m in enumerate(subset):
   x=n%5*cellw;y=70+n//5*cellh;im=Image.open(OUT/cat/(m['id']+'.png')).convert('RGBA')
   sheet.paste(im.resize((96,96),Image.Resampling.NEAREST),(x+48,y),im.resize((96,96),Image.Resampling.NEAREST))
   tiny=im.resize((16,16),Image.Resampling.LANCZOS);sheet.paste(tiny,(x+166,y+43),tiny)
   d.text((x+10,y+103),m['name'][:12],font=font,fill=(242,247,250));d.text((x+10,y+126),m['id'][:30],font=small,fill=(162,180,196));d.text((x+10,y+144),'LV'+str(m['level'])+' / '+('active' if m['controllable'] else 'training' if 'course' in m['id'] else 'passive'),font=small,fill=GOLD if m['level']>=4 else COLORS[cat])
  sheet.save(REPORT/(cat+'-contact.png'))
 (ROOT/'src/main/resources/skill-icons-v2.json').write_text(json.dumps({'schema':1,'generator':'scripts/generate-skill-icons.py','external_artwork':False,'icons':manifest},ensure_ascii=False,indent=2)+'\n')
 (REPORT/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
 print('Generated',len(manifest),'independent semantic pictograms; unique SHA256',len(hashes))
if __name__=='__main__':main()
