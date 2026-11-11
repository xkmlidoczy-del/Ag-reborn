"""Package original sprite artwork into padded GPU textures; the source RGBA artwork is preserved."""
from pathlib import Path
from PIL import Image
import numpy as np
import json,hashlib
r=Path(__file__).resolve().parents[2];art=r/'design/Ag_十二系宏大特效_20261109';dst=r/'source/src/main/resources/assets/academy/textures/canon_fx/macro';dst.mkdir(parents=True,exist_ok=True)
names=['electromaster','vecmanip','meltdowner','teleporter','mental_out','aero_hand','float_dial','paper_psychokinesis','liquid_shadow','hydro_hand','dark_matter','nitrogen_armor']
im=Image.open(art/'十二系素材原稿.png').convert('RGBA');w,h=im.size
for i,name in enumerate(names):
 box=(round((i%4)*w/4),round((i//4)*h/3),round((i%4+1)*w/4),round((i//4+1)*h/3));cell=im.crop(box)
 # GPU-safe edge fade and transparent border prevent adjacent atlas art from bleeding at distance.
 a=np.asarray(cell).copy();yy,xx=np.mgrid[:a.shape[0],:a.shape[1]];edge=np.minimum.reduce([xx,yy,a.shape[1]-1-xx,a.shape[0]-1-yy]);a[:,:,3]=(a[:,:,3]*np.clip(edge/12,0,1)).astype('uint8')
 canvas=Image.new('RGBA',(512,512));tile=Image.fromarray(a).resize((464,464),Image.Resampling.LANCZOS);canvas.alpha_composite(tile,(24,24));canvas.save(dst/(name+'.png'))
for name in ['plasma_body','thunder_main','beam_tile']:
 Image.open(art/(name+'_原稿.png')).save(dst/(name+'.png'))
# Native three-part beam UV layout: one shared tile and smooth endpoint masks.
beam=Image.open(art/'beam_tile_原稿.png').convert('RGBA').resize((1024,512),Image.Resampling.LANCZOS)
for name,kind in [('md_blend_in','in'),('md_tile','tile'),('md_blend_out','out')]:
 a=np.asarray(beam).copy();x=np.linspace(0,1,a.shape[1]);fade=x*x*(3-2*x);fade=fade if kind=='in' else 1-fade if kind=='out' else np.ones_like(x);a[:,:,3]=(a[:,:,3]*fade[None,:]).astype('uint8');Image.fromarray(a).save(dst/(name+'.png'))
# Fine continuous arc strip and pressure ripple are deterministic emission masks; classic files remain intact.
y,x=np.mgrid[-1:1:512j,0:1:1024j];profile=np.exp(-((y-(.003*np.sin(x*91)))/.027)**2);corona=.24*np.exp(-(y/.16)**2);v=np.clip(profile+corona,0,1)
a=np.zeros((512,1024,4),dtype=np.uint8);a[:,:,:3]=[166,225,255];a[:,:,3]=(v*245).astype('uint8');Image.fromarray(a).save(dst/'thunder_arc.png')
y,x=np.mgrid[-1:1:1024j,-1:1:1024j];rr=np.sqrt(x*x+y*y);v=np.exp(-((rr-.78)/.008)**2)+.2*np.exp(-((rr-.78)/.03)**2)
a=np.zeros((1024,1024,4),dtype=np.uint8);a[:,:,:3]=[210,241,255];a[:,:,3]=(np.clip(v,0,1)*245).astype('uint8');Image.fromarray(a).save(dst/'thunder_ripple.png')
manifest={p.name:{'sha256':hashlib.sha256(p.read_bytes()).hexdigest(),'size':Image.open(p).size} for p in dst.glob('*.png')};(art/'素材清单.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2));print('Packaged',len(manifest),'RGBA textures')
