"""Original deterministic Dark Matter / Nitrogen / Aero artwork, 2026-10-29.

All contours, feather barbs, crystal planes and fluid paths are generated here.
No downloaded or traced artwork. Sheet coordinates are row-major. Normals use
OpenGL +Y, encoded as (normal + 1) / 2; sample them as linear data.
"""
import math
from pathlib import Path
import numpy as np
from PIL import Image, ImageDraw
from fx4lib import OUT, ROOT, grid, rgba, smooth, noise, sheet, save, canvas

TAU=math.tau

def feather(x,y,variant=0,flip=1.0):
    """Analytic vane with root left, tip right and fine oblique barbs."""
    length=[.76,.80,.65,.84,.70,.82,.59,.78][variant%8]
    u=(x+.84)/(length*2)
    center=.12*np.sin(u*math.pi)*(variant%3-1)+.03*np.sin(u*TAU)
    yy=(y-center)/max(.08,abs(flip))
    width=(.19+.035*(variant%4))*np.sin(np.clip(u,0,1)*math.pi)**.74
    width*=1+.09*np.sin(u*9+variant)
    side=np.where(yy<0,1.0,.74+.06*(variant%3))
    # Uneven vane tips break the leaf-like silhouette while retaining fine barbs.
    vane_tip=.008*(.5+.5*np.sin(u*31*TAU+variant))**5
    d=width*side-vane_tip-np.abs(yy)
    mask=smooth(-.008,.008,d)*smooth(.035,.085,u)*(1-smooth(.94,.99,u))
    # Barbs angle back toward the quill; gaps widen towards the vane edge.
    phase=(u*31-np.abs(yy)*13+variant*.21)%1
    gaps=(1-smooth(.04,.15,np.minimum(phase,1-phase)))
    edge=np.clip(np.abs(yy)/(width*side+1e-4),0,1)
    alpha=mask*(.88-.38*gaps*edge**1.4)
    shaft=np.exp(-(yy/.008)**2)*smooth(.005,.055,u)*(1-smooth(.95,1.015,u))
    alpha=np.maximum(alpha,shaft*.95)
    iri=np.stack([.91+.065*np.cos(u*TAU+1.4),.92+.055*np.cos(u*TAU+3.2),.97+.025*np.cos(u*TAU)],axis=-1)
    shade=.89+.11*np.exp(-np.abs(yy)*7)-.065*gaps*edge
    color=np.clip(iri*shade[...,None],0,1)
    color+=shaft[...,None]*.05
    return rgba(alpha,np.clip(color,0,1))

def dm_feathers():
    def frame(i,n,w,h):
        x,y=grid(w,h)
        return feather(x,y,i)
    return sheet(4,2,256,256,frame)

def dm_fall(i,n,w,h):
    t=i/(n-1); x,y=grid(w,h)
    # One physical feather drifts and tumbles continuously rather than random frames.
    dx=.11*np.sin(TAU*t); dy=-.26+.52*t
    angle=-.33+1.3*np.sin(t*TAU*.7)
    xx=(x-dx)*np.cos(angle)+(y-dy)*np.sin(angle)
    yy=-(x-dx)*np.sin(angle)+(y-dy)*np.cos(angle)
    a=feather(xx/.76,yy/.76,2,.22+.78*abs(np.cos(TAU*t)))
    a[...,3]*=smooth(0,.11,t)*(1-smooth(.86,1,t))
    return a

def dm_normal():
    x,y=grid(256)
    rng=np.random.default_rng(41029)
    sites=rng.uniform(-1.2,1.2,(28,2))
    dist=(x[...,None]-sites[:,0])**2+(y[...,None]-sites[:,1])**2
    index=np.argmin(dist,axis=-1)
    slopes=rng.uniform(-.28,.28,(28,2))
    nx=slopes[index,0]; ny=slopes[index,1]
    # +Y is upward in texture tangent space; no image-space derivative inversion.
    norm=np.sqrt(nx*nx+ny*ny+1)
    normal=np.stack([nx/norm,ny/norm,1/norm],axis=-1)
    # Flat neutral perimeter simplifies clamp sampling and blending out refraction.
    blend=smooth(.78,.98,np.maximum(abs(x),abs(y)))
    normal=normal*(1-blend[...,None])+np.array([0,0,1])*blend[...,None]
    normal/=np.linalg.norm(normal,axis=-1)[...,None]
    return rgba(np.ones(x.shape),(normal+1)/2)

def dm_shards():
    def cell(i,n,w,h):
        rng=np.random.default_rng(520+i)
        sides=3+i%4
        angles=np.arange(sides)*TAU/sides+rng.uniform(-.25,.25,sides)
        radii=rng.uniform(.53,.8,sides)
        points=np.stack([np.cos(angles)*radii,np.sin(angles)*radii],axis=-1)
        def draw(d,s):
            p=[((x+1)*w/2*s,(y+1)*h/2*s) for x,y in points]
            center=(w/2*s,h/2*s)
            for j in range(sides):
                c=[(232,240,250,235),(255,244,253,250),(220,233,248,240),(250,252,255,245)][j%4]
                d.polygon([center,p[j],p[(j+1)%sides]],fill=c)
            d.line(p+[p[0]],fill=(255,255,255,250),width=max(1,s))
        return canvas(w,h,draw,3)
    return sheet(4,4,64,64,cell)

def nitrogen_rim():
    x,y=grid(256); r=np.sqrt(x*x+y*y); theta=np.arctan2(y,x)
    a=np.zeros(r.shape)
    for radius,power,width in [(.78,.75,.011),(.705,.29,.008),(.85,.26,.012),(.65,.09,.01)]:
        wobble=.003*np.sin(theta*9)+.002*np.cos(theta*17)
        a+=np.exp(-((r-radius-wobble)/width)**2)*power
    a+=np.exp(-((r-.79)/.07)**2)*.11
    a*=.8+.2*np.cos(theta-1.2)**2
    return rgba(np.clip(a,0,.95))

def frost_decal():
    rng=np.random.default_rng(61029)
    def draw(d,s):
        center=np.array([256.,256.])
        for arm in range(6):
            angle=arm*TAU/6+.13
            direction=np.array([np.cos(angle),np.sin(angle)])
            end=center+direction*(184+rng.uniform(-12,12))
            def line(p,q,width,color):
                d.line([tuple(p*s),tuple(q*s)],fill=color,width=max(1,int(width*s)))
            line(center,end,2.2,(196,234,255,210))
            for frac in [.22,.36,.50,.65,.79]:
                origin=center+(end-center)*frac
                size=(1-frac)*58+7
                for sign in [-1,1]:
                    bend=angle+sign*.78
                    branch=origin+np.array([np.cos(bend),np.sin(bend)])*size
                    line(origin,branch,1.4,(185,224,247,180))
                    for subfrac in [.48,.73]:
                        start=origin+(branch-origin)*subfrac
                        bend2=bend+sign*.72
                        tip=start+np.array([np.cos(bend2),np.sin(bend2)])*size*.24
                        line(start,tip,.8,(210,242,255,145))
        # fine crystal veins and irregular frozen water contact area
        for j in range(44):
            an=rng.random()*TAU; radius=rng.uniform(30,177)
            p=center+radius*np.array([np.cos(an),np.sin(an)])
            line(p,p+np.array([np.cos(an+.5),np.sin(an+.5)])*rng.uniform(3,13),.8,(198,234,250,150))
    a=canvas(512,512,draw,2)
    x,y=grid(512); r=np.sqrt(x*x+y*y)
    ground=(1-smooth(.35,.83,r))*(.035+.05*noise(512,512,617))
    return np.dstack([np.maximum(a[...,:3],np.array([.65,.83,.93])),np.maximum(a[...,3],ground)])

def nitrogen_compress(i,n,w,h):
    t=i/(n-1); x,y=grid(w,h); r=np.sqrt(x*x+y*y); th=np.arctan2(y,x)
    if t<.30:
        p=t/.30; radius=.64-.48*p; opacity=smooth(0,.2,p)
        a=np.exp(-((r-radius)/.021)**2)*opacity*.8
        a+=np.exp(-((r-radius-.07)/.013)**2)*opacity*.22
    else:
        p=(t-.30)/.70; radius=.16+.74*(1-(1-p)**2)
        opacity=(1-smooth(.25,1,p))
        a=np.exp(-((r-radius)/(.012+.018*p))**2)*opacity
        a+=np.exp(-((r-radius+.09)/.015)**2)*opacity*.3
        a+=np.exp(-(r/(.06+.14*p))**2)*(1-p)**4*.7
        a*=.75+.25*np.sin(8*th+4*p)**2
    a*=1-smooth(.9,.97,r)
    return rgba(a)

def frost_atlas():
    def cell(i,n,w,h):
        rng=np.random.default_rng(700+i)
        def draw(d,s):
            cx=cy=32; radius=16+i%5*2; angle=i*.41
            points=[((cx+radius*np.cos(angle+j*TAU/6))*s,(cy+radius*np.sin(angle+j*TAU/6))*s) for j in range(6)]
            if i%3==0:
                for j in range(6):
                    p=points[j]; d.line([(cx*s,cy*s),p],fill=(205,241,255,240),width=s)
                    for frac in [.45,.7]:
                        ox=cx+(p[0]/s-cx)*frac; oy=cy+(p[1]/s-cy)*frac
                        for sign in [-1,1]:
                            a=angle+j*TAU/6+sign*.75
                            d.line([(ox*s,oy*s),((ox+6*np.cos(a))*s,(oy+6*np.sin(a))*s)],fill=(172,223,250,200),width=s)
            else:
                d.polygon(points,fill=(135,200,235,150))
                d.line(points+[points[0]],fill=(223,249,255,240),width=s)
                for j in range(0,6,2): d.line([(cx*s,cy*s),points[j]],fill=(191,233,255,210),width=s)
        return canvas(w,h,draw)
    return sheet(4,4,64,64,cell)

def vapor(i,n,w,h):
    t=i/(n-1); x,y=grid(w,h); a=np.zeros(x.shape)
    for j in range(9):
        an=j*2.39996
        cx=np.sin(an)*(.14+.25*t)+.08*np.sin(4*t+j)
        cy=.25-.65*t+np.cos(an)*.1
        rr=.10+.10*t+.03*np.sin(j)
        a+=np.exp(-((x-cx)**2+(y-cy)**2)/(rr*rr))*(.08+.035*(j%3))
    a*=smooth(0,.15,t)*(1-smooth(.48,1,t))
    a*=.7+.3*noise(w,h,803)
    return rgba(a)

def aero_stream():
    x,y=grid(512,128); u=(x+1)/2; a=np.zeros(x.shape)
    for j in range(7):
        center=(j-3)*.19+.065*np.sin(TAU*u*2+j*.7)+.035*np.cos(TAU*u*3+j)
        width=.009+.004*(j%2)
        a+=np.exp(-((y-center)/width)**2)*(.3+.4*(.5+.5*np.sin(TAU*u+j*1.3))**4)
        a+=np.exp(-((y-center)/.04)**2)*.035
    return rgba(a*(1-smooth(.78,.95,abs(y))))

def vortex(i,n,w,h):
    t=i/(n-1); x,y=grid(w,h); r=np.sqrt(x*x+y*y); th=np.arctan2(y,x)
    phase=th+5.2*r-TAU*t*1.3
    spiral=np.exp(-((np.sin(phase*1.5)/(.055+.08*r))**2))
    a=spiral*smooth(.07,.22,r)*(1-smooth(.65,.91,r))*.63
    a+=np.exp(-((r-.71)/.014)**2)*(.16+.12*np.sin(th*3-8*t)**2)
    a*=smooth(0,.15,t)*(1-smooth(.76,1,t))
    return rgba(a)

def dust(i,n,w,h):
    t=i/(n-1); x,y=grid(w,h); a=np.zeros(x.shape)
    for j in range(11):
        cx=np.sin(j*2.39996)*(.1+.42*t)
        cy=.38-.49*t+np.cos(j*2.39996)*(.06+.15*t)
        radius=.065+.095*t+.02*(j%3)
        a+=np.exp(-((x-cx)**2/(radius*radius)+(y-cy)**2/(radius*.8)**2))*.27
    a*=smooth(0,.1,t)*(1-smooth(.35,1,t))*(.7+.3*noise(w,h,910))
    rgb=np.empty(x.shape+(3,)); rgb[...]=[.53,.48,.37]
    rgb+=np.exp(-((x+.2)**2+(y+.3)**2)/.8)[...,None]*np.array([.12,.12,.10])
    return rgba(a,rgb)

def aero_cone():
    x,y=grid(256); r=np.sqrt(x*x+y*y); th=np.arctan2(y,x)
    # Looking down the axis: nested shock diamonds project to interrupted rings.
    a=np.zeros(x.shape)
    for rad,power in [(.16,.4),(.33,.75),(.54,.45),(.78,.3)]:
        a+=np.exp(-((r-rad)/.012)**2)*power*(.3+.7*np.cos(th*2+.3)**8)
    a+=np.exp(-(x/.018)**2)*(1-smooth(.12,.77,abs(y)))*.2
    a+=np.exp(-(y/.018)**2)*(1-smooth(.12,.77,abs(x)))*.2
    return rgba(a)

def build(out=OUT):
    save('dm_feather_atlas',dm_feathers(),out,4,2)
    x=np.linspace(0,1,256); stops=np.array([[1,1,1],[1,.89,.96],[.81,.97,1],[.91,.87,1.]])
    ramp=np.stack([np.interp(x,np.linspace(0,1,4),stops[:,c]) for c in range(3)],axis=-1)
    save('dm_iri_ramp',rgba(np.ones((16,256)),np.broadcast_to(ramp,(16,256,3))),out)
    save('dm_feather_fall_sheet',sheet(8,8,128,128,dm_fall),out,8,8)
    save('dm_refraction_normal',dm_normal(),out)
    save('dm_shard_atlas',dm_shards(),out,4,4)
    save('na_shell_rim',nitrogen_rim(),out)
    save('na_frost_decal',frost_decal(),out)
    save('na_compress_sheet',sheet(8,8,128,128,nitrogen_compress),out,8,8)
    save('na_frost_atlas',frost_atlas(),out,4,4)
    save('na_vapor_sheet',sheet(4,4,128,128,vapor),out,4,4)
    save('ah_stream',aero_stream(),out)
    save('ah_vortex_sheet',sheet(4,4,128,128,vortex),out,4,4)
    save('ah_dust_sheet',sheet(4,4,128,128,dust),out,4,4)
    save('ah_cone',aero_cone(),out)

def review():
    names=['dm_feather_atlas','dm_iri_ramp','dm_feather_fall_sheet','dm_refraction_normal','dm_shard_atlas','na_shell_rim','na_frost_decal','na_compress_sheet','na_frost_atlas','na_vapor_sheet','ah_stream','ah_vortex_sheet','ah_dust_sheet','ah_cone']
    im=Image.new('RGB',(1000,math.ceil(len(names)/4)*230),(23,29,39)); d=ImageDraw.Draw(im)
    layouts={'dm_feather_atlas':(4,2),'dm_feather_fall_sheet':(8,8),'dm_shard_atlas':(4,4),'na_compress_sheet':(8,8),'na_frost_atlas':(4,4),'na_vapor_sheet':(4,4),'ah_vortex_sheet':(4,4),'ah_dust_sheet':(4,4)}
    for i,name in enumerate(names):
        a=Image.open(OUT/(name+'.png')).convert('RGBA')
        if name.endswith('sheet'):
            cols,rows=layouts[name]; cw,ch=a.width//cols,a.height//rows
            at=(cols*rows)//2 if 'feather' in name else (cols*rows)//3
            a=a.crop((at%cols*cw,at//cols*ch,(at%cols+1)*cw,(at//cols+1)*ch))
        a.thumbnail((230,194))
        xx=i%4*250+10; yy=i//4*230+25
        im.paste(a,(xx+(230-a.width)//2,yy+(194-a.height)//2),a)
        d.text((xx,yy-19),name,fill=(225,234,246))
    p=ROOT/'qa/evidence/2026-10-29-fx4-textures/air-review.png'; im.save(p)

if __name__=='__main__':
    build(); review()
