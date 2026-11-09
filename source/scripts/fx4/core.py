"""Original analytic masks, electrical arcs and particle-beam materials for FX4.

No photos, game captures or third-party art. Fixed trajectories and seeds ensure
adjacent animation frames show the same objects moving rather than new noise.
"""
import math
import numpy as np
from fx4lib import OUT, grid, rgba, smooth, noise, canvas, sheet, save

TAU=math.tau

def ring(x,y,r,width):
 return np.exp(-((np.hypot(x,y)-r)/width)**2)

def flash(i,n,w,h):
 x,y=grid(w,h); t=i/(n-1); r=np.hypot(x,y); theta=np.arctan2(y,x)
 e=2.5*np.sin(np.pi*t)**.6*np.exp(-3*t)
 rays=np.maximum(0,np.cos(theta*6+.2))**36
 a=(np.exp(-(r/(.035+.14*t))**2)+rays*np.exp(-r/(.2+.5*t)))*e
 return rgba(a*(1-smooth(.75,.95,r)))

def shock(i,n,w,h):
 x,y=grid(w,h); t=i/(n-1); r=np.hypot(x,y); theta=np.arctan2(y,x)
 radius=.07+.77*(1-(1-t)**2); gate=np.sin(np.pi*t)**.7
 breakup=smooth(.55,.9,t)
 chips=(1-breakup)+breakup*smooth(-.5,.3,np.sin(theta*19+2*np.sin(theta*7))-t*1.4)
 a=(ring(x,y,radius,.012+.018*t)+.2*ring(x,y,radius-.045,.028))*gate*chips
 return rgba(a)

def speed(w,h,mode):
 u=np.linspace(0,1,w)[None,:]; v=np.linspace(-1,1,h)[:,None]
 if mode=='pillar':
  x,y=grid(w,h); u=(x+1)/2; v=1-(y+1)/2
  # Repeat-safe bright base grows from zero at the lower seam, then falls off.
  env=np.sin(np.pi*v)**.7*np.exp(-2.7*v)
  a=np.exp(-(x/.43)**4)*env*(.75+.25*np.cos(TAU*(3*u+.13*np.sin(TAU*v))))
 elif mode=='beam':
  a=np.exp(-(v/.15)**2)*(.76+.18*np.cos(TAU*u*5)+.06*np.cos(TAU*u*13))
 elif mode=='halo':
  flow=.76+.12*np.cos(TAU*u*3+v*4)+.12*np.cos(TAU*u*7-v*8)
  a=np.exp(-(v/.48)**4)*flow*.48
 else:
  pulse=smooth(0,.86,u)*(1-smooth(.86,1,u))
  width=.06+.38*pulse
  a=np.exp(-(v/width)**2)*(.35+.65*pulse)
  a+=.25*np.exp(-((np.abs(v)-.5)/.025)**2)*(.5+.5*np.cos(TAU*u*3))**5
 return rgba(a)

def arc_variant(i,n,w,h):
 u=np.linspace(0,1,w); v=np.linspace(-1,1,h)[:,None]
 rng=np.random.default_rng(330+i)
 xp=np.linspace(0,1,25); yp=rng.uniform(-.23,.23,len(xp)); yp[-1]=yp[0]
 path=np.interp(u,xp,yp)
 a=np.exp(-((v-path)/.025)**2)+.14*np.exp(-((v-path)/.11)**2)
 for branch in range(4):
  start=.13+branch*.2; end=start+.13
  local=np.clip((u-start)/(end-start),0,1)
  ybranch=path+(-1)**(branch+i)*.38*local
  a+=np.exp(-((v-ybranch)/.016)**2)*((u>start)&(u<end))*(1-local)
 return rgba(np.clip(a,0,1))

def sparks(i,n,w,h):
 x,y=grid(w,h); t=i/(n-1); rng=np.random.default_rng(719); a=np.zeros_like(x)
 for j in range(22):
  angle=rng.uniform(0,TAU); vel=rng.uniform(.45,1.2); width=rng.uniform(.008,.02)
  px=np.cos(angle)*vel*t; py=np.sin(angle)*vel*t+.26*t*t
  along=(x-px)*np.cos(angle)+(y-py)*np.sin(angle)
  across=-(x-px)*np.sin(angle)+(y-py)*np.cos(angle)
  a+=np.exp(-(along/(.015+.06*t))**2-(across/width)**2)
 a*=np.sin(np.pi*t)**.5*(1-t)
 return rgba(a*(1-smooth(.82,.96,np.hypot(x,y))))

def shard(i,n,w,h,ember=False):
 rng=np.random.default_rng(2100+i); x,y=grid(w,h)
 angles=np.linspace(0,TAU,6,endpoint=False)+rng.uniform(-.18,.18,6)
 radii=rng.uniform(.36,.68,6)
 points=[((.5+np.cos(a)*r*.5)*w,(.5+np.sin(a)*r*.5)*h) for a,r in zip(angles,radii)]
 def draw(d,s):
  d.polygon([(px*s,py*s) for px,py in points],fill=(255,255,255,255))
 a=canvas(w,h,draw)[...,3]
 fac=np.clip(.65-.35*x-.24*y,0,1)
 if ember:
  heat=np.exp(-((x+.18)**2+(y+.21)**2)/.18)
  rgb=np.stack([.04+.88*heat,.16+.84*heat,.05+.54*heat],-1)
 else:
  rgb=np.stack([.09+.25*fac,.11+.28*fac,.14+.32*fac],-1)
  shine=np.exp(-((x+.23)/.025)**2)*np.exp(-((y+.2)/.23)**2)
  rgb+=shine[...,None]*.65
 return rgba(a,np.clip(rgb,0,1))

def cracks(w,h,seed=42,electric=False):
 rng=np.random.default_rng(seed); paths=[]
 for j in range(11):
  angle=TAU*j/11+rng.uniform(-.16,.16); length=rng.uniform(.42,.86)
  pts=[(w*.5,h*.5)]
  for k in range(1,8):
   r=length*k/7; ag=angle+rng.uniform(-.1,.1)
   pts.append(((.5+.5*r*np.cos(ag))*w,(.5+.5*r*np.sin(ag))*h))
  paths.append(pts)
 def draw(d,s):
  for j,pts in enumerate(paths):
   d.line([(px*s,py*s) for px,py in pts],fill=(255,255,255,235),width=max(1,int(s*(1.4 if electric else 1))))
   for k in [3,5]:
    px,py=pts[k]; ex,ey=pts[k+1]
    d.line([(px*s,py*s),((px+(ey-py)*2)*s,(py-(ex-px)*2)*s)],fill=(255,255,255,180),width=s)
 return canvas(w,h,draw)[...,3]

def charge(i,n,w,h):
 x,y=grid(w,h); t=i/(n-1); a=np.zeros_like(x); rng=np.random.default_rng(923)
 for j in range(42):
  theta=rng.uniform(0,TAU); phase=rng.uniform(0,1)
  radius=.9*(1-t)*( .45+.55*phase)
  theta+=t*(.4+phase)
  px=radius*np.cos(theta); py=radius*np.sin(theta)
  a+=np.exp(-((x-px)**2+(y-py)**2)/(.008+.009*t)**2)
 a+=np.exp(-(x*x+y*y)/(.18-.12*t)**2)*(.1+.8*t)
 a+=.25*ring(x,y,.55*(1-t)+.06,.012)
 return rgba(a*np.sin(np.pi*t)**.5)

def build(out=OUT):
 w=h=512; x,y=grid(w,h)
 nv=noise(w,h,511,5); save('common_noise_tile',rgba(np.ones_like(nv),np.repeat(nv[...,None],3,axis=-1)),out)
 # Analytic curl-style vector field, neutral center 128/255.
 u=np.linspace(0,1,w)[None,:]; v=np.linspace(0,1,h)[:,None]
 r=.5+.23*np.sin(TAU*(v*2+.22*np.sin(TAU*u)))+.11*np.cos(TAU*(u*3+v))
 g=.5-.23*np.cos(TAU*(u*2+.22*np.cos(TAU*v)))+.11*np.sin(TAU*(v*3-u))
 save('common_flow_map',rgba(np.ones((h,w)),np.stack([r+np.zeros_like(g),g+np.zeros_like(r),np.full((h,w),.5)],-1)),out)
 u=np.linspace(0,1,256)[None,:]; a=np.repeat(smooth(.03,.26,u),16,axis=0)
 edge=np.exp(-((u-.34)/.12)**2)
 ramp=np.stack([np.ones_like(u),.55+.45*edge,.16+.84*edge],-1).repeat(16,axis=0)
 save('common_dissolve_ramp',rgba(a,ramp),out)
 save('common_shock_ring_sheet',sheet(8,8,128,128,shock),out,cols=8,rows=8)
 save('common_hit_flash_sheet',sheet(4,4,128,128,flash),out,cols=4,rows=4)
 r=np.hypot(x,y); theta=np.arctan2(y,x); edge=.75+.04*np.sin(theta*7)+.025*np.cos(theta*13)
 a=1-smooth(edge-.18,edge,r)
 save('common_ground_mask',rgba(a,np.repeat(a[...,None],3,axis=-1)),out)
 save('common_light_pillar',speed(128,512,'pillar'),out)
 save('common_speedline',speed(512,64,'speed'),out)
 save('em_arc_sheet',sheet(1,8,512,64,arc_variant),out,cols=1,rows=8)
 save('em_spark_sheet',sheet(4,4,128,128,sparks),out,cols=4,rows=4)
 u=np.linspace(0,1,512)[None,:]; v=np.linspace(-1,1,64)[:,None]
 field=np.exp(-(v/.024)**2)*.5+np.exp(-(v/.08)**2)*(.5+.5*np.cos(TAU*u*5))**30
 field+=.22*np.exp(-((np.abs(v)-.32)/.02)**2)
 save('em_fieldline',rgba(field),out)
 save('em_ironsand',sheet(4,4,64,64,lambda i,n,w,h:shard(i,n,w,h)),out,cols=4,rows=4)
 sc=cracks(512,512,44,True); burn=(1-smooth(.18,.83,r))*(.35+.4*noise(512,512,81,12))
 rgb=np.stack([.05+.07*sc,.045+.16*sc,.04+.22*sc],-1)
 save('em_scorch_decal',rgba(np.maximum(sc*.9,burn),rgb),out)
 save('md_beam_core',speed(512,64,'beam'),out)
 save('md_beam_halo',speed(512,128,'halo'),out)
 save('md_charge_sheet',sheet(8,8,128,128,charge),out,cols=8,rows=8)
 xh,yh=grid(256); rh=np.hypot(xh,yh); th=np.arctan2(yh,xh)
 # Signed distance to a regular hexagon with flat top/bottom.
 d=np.maximum.reduce([np.abs(yh),np.abs(.866*xh+.5*yh),np.abs(.866*xh-.5*yh)])
 rim=np.exp(-((d-.74)/.013)**2); face=(1-smooth(.735,.76,d))*.16
 a=np.maximum(rim,face); rgb=np.empty((256,256,3)); rgb[:]=(.28,.85,.5); rgb+=rim[...,None]*.25
 save('md_hex_plate',rgba(a,np.clip(rgb,0,1)),out)
 molten=(1-smooth(.58,.83+.05*np.sin(theta*9),r))
 fiss=cracks(512,512,82); heat=np.exp(-(r/.41)**3)*(.5+.5*nv)
 rgb=np.stack([.035+.86*heat+.1*fiss,.08+.9*heat+.2*fiss,.025+.67*heat+.1*fiss],-1)
 save('md_molten_decal',rgba(molten*.95,np.clip(rgb,0,1)),out)
 save('md_ember_sheet',sheet(4,4,64,64,lambda i,n,w,h:shard(i,n,w,h,True)),out,cols=4,rows=4)

if __name__=='__main__': build()
