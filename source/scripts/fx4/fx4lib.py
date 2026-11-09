"""Original procedural FX4 helpers. No external artwork. Float arrays use straight RGBA.

All generators use a fixed seed. PNG color textures carry an sRGB chunk; data maps
also carry it for the requested container format but MUST be sampled as linear data.
"""
from pathlib import Path
import hashlib
import numpy as np
from PIL import Image, ImageDraw, PngImagePlugin

ROOT = Path(__file__).resolve().parents[3]
SPEC = ROOT / 'docs/vfx-upgrade-textures-20261029.json'
OUT = ROOT / 'source/src/main/resources/assets/academy/textures/fx4'
TILE_AXES = {
 'common_noise_tile':'xy', 'common_flow_map':'xy', 'common_light_pillar':'y',
 'common_speedline':'x', 'em_arc_sheet':'x', 'em_fieldline':'x',
 'md_beam_core':'x', 'md_beam_halo':'x', 'tp_lattice':'xy',
 'vm_arrow_field':'x', 'vm_shear_streak':'x', 'mo_thread':'x',
 'ah_stream':'x','hh_water_ribbon':'x','hh_curtain':'y','fd_column':'y',
 'ls_wet_ribbon':'x','pp_fiber':'xy',
}
# These are lookup/data textures; transparent padding would corrupt their endpoints.
DATA_MAPS = {'common_noise_tile','common_flow_map','common_dissolve_ramp',
 'dm_iri_ramp','dm_refraction_normal','pp_fold_normal','pp_fiber','pp_edge_shadow'}
# Sampling semantics are separate from padding exemptions: colored ramps and
# paper fiber are colors even though they need their full UV domain.
LINEAR_DATA_MAPS = {'common_noise_tile','common_flow_map','dm_refraction_normal',
 'pp_fold_normal','pp_edge_shadow','tp_afterimage_mask','common_ground_mask'}

def grid(w,h=None):
 h = h or w
 y,x=np.mgrid[:h,:w]
 return (x+.5)/w*2-1,(y+.5)/h*2-1

def smooth(a,b,x):
 t=np.clip((x-a)/(b-a),0,1)
 return t*t*(3-2*t)

def rgba(alpha,rgb=(1.,1.,1.)):
 a=np.asarray(alpha); z=np.ones(a.shape+(4,),dtype=float)
 z[...,:3]=rgb; z[...,3]=np.clip(a,0,1)
 return z

def noise(w,h,seed=29,cells=8):
 rng=np.random.default_rng(seed)
 total=np.zeros((h,w)); norm=0
 y,x=np.mgrid[:h,:w]
 for octave,amp in enumerate([1,.5,.25,.125]):
  n=cells*2**octave; l=rng.random((n,n)); u=x/max(1,w-1)*n; v=y/max(1,h-1)*n
  ix=np.floor(u).astype(int)%n; iy=np.floor(v).astype(int)%n
  fx=smooth(0,1,u-np.floor(u)); fy=smooth(0,1,v-np.floor(v))
  total+=amp*((1-fy)*((1-fx)*l[iy,ix]+fx*l[iy,(ix+1)%n])+fy*((1-fx)*l[(iy+1)%n,ix]+fx*l[(iy+1)%n,(ix+1)%n]))
  norm+=amp
 return total/norm

def canvas(w,h,draw_fn,scale=3):
 """draw_fn(ImageDraw, scale) draws target-pixel coordinates multiplied by scale."""
 im=Image.new('RGBA',(w*scale,h*scale),(0,0,0,0))
 draw_fn(ImageDraw.Draw(im),scale)
 return np.asarray(im.resize((w,h),Image.Resampling.LANCZOS),dtype=float)/255

def sheet(cols,rows,cw,ch,fn):
 out=np.zeros((rows*ch,cols*cw,4))
 for i in range(cols*rows):
  a=fn(i,cols*rows,cw,ch)
  out[(i//cols)*ch:(i//cols+1)*ch,(i%cols)*cw:(i%cols+1)*cw]=a
 return out

def save(name,array,out=OUT,cols=1,rows=1):
 """Enforce per-cell gutter and exact periodic endpoints. Preserve straight RGB."""
 out=Path(out); out.mkdir(parents=True,exist_ok=True)
 a=np.clip(np.asarray(array)*255+.5,0,255).astype('uint8')
 if a.ndim!=3 or a.shape[2]!=4: raise ValueError(name+' requires float RGBA')
 h,w=a.shape[:2]; cw,ch=w//cols,h//rows; axes=TILE_AXES.get(name,'')
 for row in range(rows):
  for col in range(cols):
   c=a[row*ch:(row+1)*ch,col*cw:(col+1)*cw]
   if name not in DATA_MAPS:
    if 'x' not in axes: c[:,:2,3]=0; c[:,-2:,3]=0
    if 'y' not in axes: c[:2,:,3]=0; c[-2:,:,3]=0
   if 'x' in axes: c[:,-1]=c[:,0]
   if 'y' in axes: c[-1]=c[0]
 info=PngImagePlugin.PngInfo(); info.add(b'sRGB',b'\x00')
 prefix=name.split('_')[0]
 module='core' if prefix in ['common','em','md'] else ('tech' if prefix in ['tp','vm','mo'] else ('air' if prefix in ['dm','na','ah'] else 'materials'))
 info.add_text('Source','Original deterministic procedural art; source/scripts/fx4/'+module+'.py; 2026-10-29')
 Image.fromarray(a).save(out/(name+'.png'),pnginfo=info,optimize=True)
 return hashlib.sha256((out/(name+'.png')).read_bytes()).hexdigest()
