#!/usr/bin/env python3
"""Original, deterministic high-resolution masks and ACA-style pictograms. No downloaded imagery.
Use the project .venv with source/qa/requirements.txt. Only the new offense assets are written.
"""
from pathlib import Path
import hashlib,json,math,re
import numpy as np
from PIL import Image,ImageDraw

ROOT=Path(__file__).resolve().parents[1]
OUT=ROOT/'src/main/resources/assets/academy/textures/fx4'
ICON=ROOT/'src/main/resources/assets/academy/textures/skill_icons_v2'
WORK=ROOT.parent/'tmp/five-system-offense-20261108/art'
OUT.mkdir(parents=True,exist_ok=True);WORK.mkdir(parents=True,exist_ok=True)
manifest=[]

def grid(w,h=None):
    y,x=np.mgrid[:h or w,:w];return (x+.5)/w*2-1,(y+.5)/(h or w)*2-1
def smooth(a,b,x):
    t=np.clip((x-a)/(b-a),0,1);return t*t*(3-2*t)
def ring(r,at,width):return np.exp(-((r-at)/width)**2)
def image(alpha,rgb=None):
    z=np.ones(alpha.shape+(4,),dtype=np.float32)
    if rgb is not None:z[...,:3]=rgb
    z[...,3]=np.clip(alpha,0,1);return Image.fromarray(np.uint8(np.clip(z,0,1)*255),'RGBA')
def save(name,im):
    p=OUT/(name+'.png');im.save(p,optimize=True)
    manifest.append(dict(path=str(p.relative_to(ROOT)),width=im.width,height=im.height,sha256=hashlib.sha256(p.read_bytes()).hexdigest(),author='Original procedural ACA offense artwork'))

# Signal circuitry: a star pupil with five split filaments, a finely etched rim, and discrete contact pads.
x,y=grid(1024);r=np.hypot(x,y);a=np.arctan2(y,x)
star=.28+.10*np.cos(5*a);iris=ring(r,star,.009)+.62*ring(r,star+.035,.003)
iris+=ring(r,.60,.006)*(.35+.65*(np.cos(a*20)>.45))
iris+=ring(r,.72,.004)*(np.sin(a*5+.4)>.3)
iris+=np.exp(-(r/.065)**4)*.8
for k in range(5):
    angle=k*math.tau/5;side=np.abs(np.sin(a-angle))*r
    iris+=np.exp(-(side/.004)**2)*smooth(.40,.45,r)*(1-smooth(.67,.72,r))*(np.cos(a-angle)>0)
save('of_mind_neural',image(iris*(1-smooth(.83,.9,r))))
x,y=grid(1024,128);core=np.exp(-(y/.12)**2);fil=np.exp(-((y-.30*np.sin((x+1)*math.pi*5))/.035)**2)
save('of_mind_thread',image((core*.65+fil*.7)*(1-smooth(.91,.995,np.abs(x)))))

# Warm transparent density shells: paired ridges and segmented fluid interfaces, without a magic sigil.
x,y=grid(1024);r=np.hypot(x,y);a=np.arctan2(y,x)
density=ring(r,.67,.012)*(.45+.55*(np.cos(a*12)>.15))+ring(r,.73,.003)*.6
density+=ring(r,.45,.006)*(np.cos(a*6+.5)>.45)+ring(r,.23,.015)*.25
save('of_float_shell',image(density))
x,y=grid(256,1024)
column=np.exp(-((np.abs(x)-(.25+.05*np.sin(y*12)))/.018)**2)
column+=.38*np.exp(-((x-.32*np.sin(y*8))/.012)**2)
column*=1-smooth(.84,.98,np.abs(y))
save('of_float_column',image(column))

# Folded paper albedo: solid fibres, triangular fold planes and a dark printed/bevel edge.
x,y=grid(1024,512)
edge=.58*(1-np.abs(x));inside=smooth(0,.012,edge-np.abs(y))*(1-smooth(.94,.985,np.abs(x)))
fiber=np.sin(x*871+y*31)*np.sin(y*1137+x*29)*.012
shade=np.where(y>=0,.97,.72)+np.exp(-(y/.018)**2)*.2+fiber
rgb=np.stack([shade,shade*.975,shade*.90],axis=-1)
save('of_paper_blade',image(inside,rgb))
x,y=grid(512);fiber=np.sin(x*721+y*49)*np.sin(y*873-x*17)*.025
rgb=np.stack([.95+fiber,.935+fiber,.86+fiber],axis=-1)
save('of_paper_fiber',image(np.ones(x.shape),rgb))
x,y=grid(1024);r=np.hypot(x,y);a=np.arctan2(y,x);fold=(a%(math.tau/8))/(math.tau/8)
boundary=.66+.18*(1-fold);mask=smooth(0,.006,boundary-r)*smooth(.13,.15,r)
crease=np.where(fold<.5,.96,.68)+.05*np.cos(r*180)
save('of_paper_wheel',image(mask,np.stack([crease,crease*.975,crease*.9],axis=-1)))

# Ink has an opaque liquid core, a soft meniscus and narrow anisotropic highlights.
x,y=grid(1024,512);width=.42*(1-np.abs(x))**.7+.035
mask=smooth(0,.02,width-np.abs(y))*(1-smooth(.94,.99,np.abs(x)))
spec=np.exp(-((y+.13+np.sin(x*8)*.018)/.012)**2)*.65
shade=.16+.23*(1-np.abs(y)/np.maximum(width,.01))+spec
save('of_ink_blade',image(mask,np.stack([shade*.86,shade*.94,shade],axis=-1)))
x,y=grid(1024,128);alpha=np.exp(-((y-.18*np.sin(x*13))/.23)**2)*(1-smooth(.90,.995,np.abs(x)))
shine=np.exp(-((y-.18*np.sin(x*13)+.1)/.035)**2)
save('of_ink_filament',image(alpha,np.stack([.22+.6*shine,.25+.6*shine,.30+.6*shine],axis=-1)))

# Shear-air crescents: fine separated pressure fronts, readable against both sky and stone.
x,y=grid(1024);r=np.hypot(x,y);a=np.arctan2(y,x)
crescent=ring(np.hypot(x+.20,y),.63,.027)*(1-smooth(.15,.73,x))
crescent+=ring(np.hypot(x+.12,y),.78,.006)*(1-smooth(.02,.65,x))*.55
crescent*=1-smooth(.88,.98,r)
save('of_air_crescent',image(crescent))
x,y=grid(1024,128);streak=np.exp(-(y/.055)**2)+.4*np.exp(-((y-.22*np.sin(x*5))/.023)**2)
save('of_air_streak',image(streak*(1-smooth(.75,.98,np.abs(x)))))

# Independent 16-frame impact sheets: 256px per frame, animated by a single atlas lookup.
for name,style in [('of_mind_impact','mind'),('of_float_crush','float'),('of_ink_ripple','ink'),('of_air_impact','air')]:
    atlas=Image.new('RGBA',(1024,1024));x,y=grid(256);r=np.hypot(x,y);a=np.arctan2(y,x)
    for f in range(16):
        t=f/15;front=.10+.74*t;fade=(1-t)**.65
        alpha=ring(r,front,.018+.012*t)*fade
        if style=='mind':alpha+=ring(r,front*.6,.007)*fade*(np.cos(a*5+f*.1)>.3)+np.exp(-(r/.13)**2)*max(0,1-t*3)
        elif style=='float':alpha+=ring(r,front*.82,.005)*fade*(np.cos(a*10)>.15)+ring(r,front*.45,.012)*fade*.35
        elif style=='ink':alpha+=ring(r,front*(1+.035*np.sin(a*9)),.04)*fade*.65
        else:alpha+=ring(r,front*.77,.006)*fade*(np.sin(a*3)>.1)
        atlas.alpha_composite(image(alpha*(1-smooth(.92,.99,r))),(f%4*256,f//4*256))
    save(name,atlas)

source=(ROOT/'src/main/java/cn/academy/port/canon/CanonOffenseSkills.java').read_text()
skills=re.findall(r's\("([a-z_]+)","([a-z_]+)",(\d+),',source)
colors={'mental_out':(244,197,77),'float_dial':(139,218,228),'paper_psychokinesis':(238,231,208),'liquid_shadow':(145,161,194),'aero_hand':(151,223,213)}
previews=[]
provenance=json.loads((ROOT/'src/main/resources/skill-icons-v2.json').read_text())
new_ids={id for _,id,_ in skills}
provenance['icons']=[m for m in provenance['icons'] if m['id'] not in new_ids]
names=json.loads((ROOT/'src/main/resources/assets/academy/lang/zh_cn.json').read_text())
meanings={
 'mental_neural_sting':'star-tipped neural needle and focused signal',
 'mental_synaptic_burst':'mental star enclosed by opposing sensory waves',
 'mental_feedback_spike':'paired mental stars connected by a feedback loop',
 'mental_cortical_wave':'head silhouette and directed cortical shock wave',
 'mental_resonance_cascade':'five linked mental stars resonating around one centre',
 'float_buoyant_strike':'buoyant shell and upward impact impulse',
 'float_reversal_drop':'suspended density slice and reversed downward impulse',
 'float_counterpulse':'opposed lift impulses passing through a density slice',
 'float_pressure_drop':'upper pressure slice pressing three columns downward',
 'float_collapse_field':'bounded density cylinder collapsing toward its lower slice',
 'paper_fold_fan':'three tangible folded paper blades in a spread fan',
 'paper_piercing_lance':'long folded paper lance with a sharp ridge',
 'paper_returning_wheel':'six folded paper segments forming a rotating wheel',
 'liquid_touch_spike':'wet shadow body extruding one contact spike',
 'liquid_crosscut':'two wet blades crossing above an owned shadow pool',
 'aero_pressure_bullet':'thin compressed crescent with parallel air streaks'}
for index,(cat,id,level) in enumerate(skills):
    scale=16;im=Image.new('RGBA',(32*scale,32*scale));d=ImageDraw.Draw(im);color=colors[cat]
    def line(points,fill=color,width=1.5):d.line([(int(x*scale),int(y*scale)) for x,y in points],fill=fill,width=int(width*scale),joint='curve')
    def oval(box,fill=None,outline=color,width=1.3):d.ellipse(tuple(int(v*scale) for v in box),fill=fill,outline=outline,width=int(width*scale))
    for x0,y0,sx,sy in [(2,2,1,1),(30,2,-1,1),(2,30,1,-1),(30,30,-1,-1)]:line([(x0,y0+sy*3),(x0,y0),(x0+sx*3,y0)],width=.6)
    if cat=='mental_out':
        def star(cx,cy,rad):
            pts=[]
            for k in range(10):
                a=k*math.pi/5-math.pi/2;r=rad if k%2==0 else rad*.42;pts.append((cx+math.cos(a)*r,cy+math.sin(a)*r))
            line(pts+[pts[0]],width=1.1)
        if level=='1':
            star(17,14,8);line([(5,26),(14,17),(12,23),(5,26)],fill=(255,246,212),width=1.1)
        elif level=='2':
            star(16,16,6)
            for box in [(4,4,28,28),(8,8,24,24)]:d.arc(tuple(int(v*scale) for v in box),-65,65,fill=color,width=int(scale*.9));d.arc(tuple(int(v*scale) for v in box),115,245,fill=color,width=int(scale*.9))
        elif level=='3':
            star(10,12,5);star(22,20,5);line([(9,20),(9,24),(18,24),(15,21)],width=1.2);line([(23,12),(23,8),(14,8),(17,11)],width=1.2)
        elif level=='4':
            oval((10,5,23,23),width=1.3);line([(12,22),(12,27),(21,27)],width=1.2)
            star(16,13,4);line([(4,9),(7,12),(4,15),(7,18),(4,21)],width=1.1)
            d.arc((18*scale,6*scale,29*scale,26*scale),-60,60,fill=color,width=scale)
        else:
            pts=[]
            for k in range(5):
                a=k*math.tau/5-math.pi/2;cx=16+math.cos(a)*9;cy=16+math.sin(a)*9;pts.append((cx,cy));star(cx,cy,3.2)
            line(pts+[pts[0]],width=.75);star(16,16,4)
    elif cat=='float_dial':
        if level=='1':
            oval((9,14,23,28),width=1.3);oval((7,22,25,27),width=.8)
            line([(16,19),(16,5),(12,9)],fill=(247,253,250));line([(16,5),(20,9)],fill=(247,253,250))
        elif level=='2':
            oval((7,5,25,10),width=.9);line([(16,6),(16,24),(11,19)],fill=(247,253,250));line([(16,24),(21,19)],fill=(247,253,250));line([(5,27),(27,27)],width=1.2)
        elif level=='3':
            oval((7,12,25,19),width=.9);line([(10,25),(10,6),(6,10)],fill=(247,253,250));line([(10,6),(14,10)],fill=(247,253,250))
            line([(22,6),(22,25),(18,21)],fill=(247,253,250));line([(22,25),(26,21)],fill=(247,253,250))
        elif level=='4':
            oval((5,5,27,11),width=1.3)
            for xx in [9,16,23]:line([(xx,12),(xx,23),(xx-2,20)],fill=(247,253,250),width=1);line([(xx,23),(xx+2,20)],fill=(247,253,250),width=1)
            oval((6,25,26,28),width=.8)
        else:
            oval((4,7,28,14),width=1.2);oval((4,22,28,28),width=1.2)
            for xx in [7,16,25]:line([(xx,12),(xx,23)],width=.8)
            line([(16,10),(16,25),(12,21)],fill=(247,253,250),width=1.8);line([(16,25),(20,21)],fill=(247,253,250),width=1.8)
    elif cat=='paper_psychokinesis':
        if 'wheel' in id:
            oval((5,5,27,27));
            for k in range(6):
                a=k*math.tau/6;line([(16,16),(16+math.cos(a)*10,16+math.sin(a)*10),(16+math.cos(a+.5)*7,16+math.sin(a+.5)*7)],width=1.4)
        elif 'lance' in id:
            line([(7,25),(24,7),(20,17),(7,25),(14,20)],fill=(249,246,230),width=1.6)
        else:
            for tipx,tipy,footx in [(5,10,12),(16,5,16),(27,10,20)]:
                line([(16,27),(tipx,tipy),(footx,24),(16,27)],width=1.25)
    elif cat=='liquid_shadow':
        oval((5,22,27,28),fill=(*color,80),width=1)
        if 'crosscut' in id:
            line([(6,10),(21,18),(25,24)],fill=(211,227,242),width=2);line([(26,8),(12,20),(9,25)],width=2)
            oval((4,8,8,12),fill=(*color,220),width=.5)
        else:line([(10,25),(17,6),(17,18),(23,25)],fill=(211,227,242),width=1.7)
    else:
        d.arc((12*scale,4*scale,28*scale,23*scale),-60,80,fill=(244,255,254),width=2*scale)
        d.arc((8*scale,7*scale,24*scale,26*scale),-60,80,fill=color,width=scale)
        line([(4,24),(15,13)],width=1.3);line([(3,18),(10,11)],width=1);line([(10,29),(19,20)],width=1)
    master=WORK/(id+'-512.png');im.save(master)
    icon=im.resize((32,32),Image.Resampling.LANCZOS)
    marker=ImageDraw.Draw(icon)
    if int(level)>=4:marker.line((10,2,21,2),fill=(255,205,104,255),width=1)
    if int(level)>=5:marker.line((10,30,21,30),fill=(255,205,104,255),width=1)
    marker.rectangle((0,0,31,31),outline=(0,0,0,0))
    folder=ICON/cat;folder.mkdir(parents=True,exist_ok=True);p=folder/(id+'.png');icon.save(p)
    provenance['icons'].append(dict(category=cat,id=id,level=int(level),controllable=True,name=names['ac.ability.'+cat+'.'+id+'.name'],meaning=meanings[id],texture='academy:textures/skill_icons_v2/'+cat+'/'+id+'.png',sha256=hashlib.sha256(p.read_bytes()).hexdigest(),original=True,shape=id,generator='scripts/build-canon-offense-art.py'))
    manifest.append(dict(path=str(p.relative_to(ROOT)),width=32,height=32,source=str(master.relative_to(ROOT.parent)),sha256=hashlib.sha256(p.read_bytes()).hexdigest()))
    tile=Image.new('RGB',(192,192),(18,24,31));tile.paste(im.resize((160,160)),(16,16),im.resize((160,160)));previews.append(tile)
board=Image.new('RGB',(4*192,4*192),(18,24,31))
for i,im in enumerate(previews):board.paste(im,(i%4*192,i//4*192))
board.save(WORK/'icons-preview.png')
(WORK/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
(ROOT/'src/main/resources/skill-icons-v2.json').write_text(json.dumps(provenance,ensure_ascii=False,indent=2)+'\n')
print('Created',len(manifest),'original assets; previews and source masters:',WORK)
