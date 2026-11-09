"""Original deterministic FX4 teleporter, vector and mental-out textures.

Authored geometry, analytic distance fields and fixed-seed granular materials.
No external art, screenshots, fonts or reference pixels. UV: u along strips;
flipbooks are row-major. Data masks contain no baked glow colors.
"""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw, ImageFilter
from fx4lib import OUT, grid, rgba, smooth, noise, sheet, save, canvas


def line(x,y,ax,ay,bx,by,width):
    dx,dy=bx-ax,by-ay
    t=np.clip(((x-ax)*dx+(y-ay)*dy)/(dx*dx+dy*dy+1e-12),0,1)
    d=np.hypot(x-ax-t*dx,y-ay-t*dy)
    return 1-smooth(width*.55,width*1.5,d)


def path(x,y,pts,width):
    a=np.zeros_like(x)
    for p,q in zip(pts[:-1],pts[1:]):
        a=np.maximum(a,line(x,y,*p,*q,width))
    return a


def ring(x,y,r,width):
    return np.exp(-((np.hypot(x,y)-r)/width)**2)


def white(a):
    return rgba(a)


def maskdraw(w,h,fn):
    return canvas(w,h,fn)[...,3]


def cracks(w,h,seed,branches=9):
    x,y=grid(w,h); rng=np.random.default_rng(seed); a=np.zeros_like(x)
    for j in range(branches):
        ang=2*np.pi*j/branches+rng.uniform(-.18,.18)
        length=rng.uniform(.5,.88)
        pts=[(0,0)]
        for k in range(1,7):
            r=k/6*length; theta=ang+rng.uniform(-.10,.10)
            pts.append((math.cos(theta)*r,math.sin(theta)*r))
        a=np.maximum(a,path(x,y,pts,.0065))
        for k in (3,5):
            px,py=pts[k]; t=ang+rng.choice([-1,1])*.75
            end=(px+.16*np.cos(t),py+.16*np.sin(t))
            a=np.maximum(a,path(x,y,[(px,py),end],.004))
    return a


def teleporter(out):
    def rift(i,n,w,h):
        t=i/(n-1); x,y=grid(w,h)
        env=np.sin(np.pi*t)**.65
        ridge=.12*np.sin(y*17)+.09*np.sin(y*39+1.5)+.04*np.sin(y*73)
        half=env*.63*np.clip(1-(y/.91)**2,0,1)**.52*(1+ridge)
        center=.055*np.sin(y*8)+.022*np.sin(y*25)
        d=np.abs(x-center)-half
        interior=(1-smooth(-.018,.018,d))*(1-smooth(.85,.94,np.abs(y)))
        rim=np.exp(-(d/.023)**2)*env
        outer=np.exp(-(np.maximum(d,0)/.085)**2)*(1-interior)*env*.22
        scan=(.5+.5*np.cos((y+1)*h*np.pi))*.015
        a=np.maximum(interior*.94,np.clip(rim+outer,0,1))*env
        z=rgba(a,(.075,.035,.14))
        v=np.clip(rim*.95+outer*.8+scan*interior,0,1)
        rgb0=np.array([.07,.025,.13]); rgb1=np.array([.77,.56,1.])
        z[...,:3]=rgb0[None,None,:]*(1-v[...,None])+rgb1[None,None,:]*v[...,None]
        # Technical coordinate tags flanking the tear; never a magical emblem.
        tags=np.zeros_like(x)
        for yy in [-.62,-.32,.33,.61]:
            xx=(.42+abs(yy)*.25)*env
            tags=np.maximum(tags,line(x,y,xx,yy,xx+.12,yy,.005))
            tags=np.maximum(tags,line(x,y,-xx,yy,-xx-.10,yy,.005))
        tags*=env*.75
        z[...,:3]=np.where((tags>a)[...,None],np.array([.64,.39,.96]),z[...,:3])
        z[...,3]=np.maximum(a,tags)
        return z
    save('tp_rift_sheet',sheet(4,4,128,256,rift),out,cols=4,rows=4)
    w=h=512; yy,xx=np.mgrid[:h,:w]; u=xx/(w-1);v=yy/(h-1)
    du=np.minimum((u*8)%1,1-(u*8)%1);dv=np.minimum((v*8)%1,1-(v*8)%1)
    thin=np.maximum(1-smooth(.009,.025,du),1-smooth(.009,.025,dv))*.38
    nodes=np.exp(-(du*du+dv*dv)/.0025)*.85
    diag=np.minimum(((u+v)*4)%1,1-((u+v)*4)%1)
    a=np.maximum(thin,nodes)+.14*(1-smooth(.008,.022,diag))
    save('tp_lattice',white(a),out)
    def ret(d,s):
        def pts(ps): return [(a*s,b*s) for a,b in ps]
        for radius,angle in [(84,16),(100,7)]:
            for start in (0,90,180,270):
                d.arc((128*s-radius*s,128*s-radius*s,128*s+radius*s,128*s+radius*s),start+angle,start+90-angle,fill='white',width=max(1,round(1.2*s)))
        for k in range(64):
            a=k*np.pi/32;rr=92;ln=8 if k%8==0 else 3
            d.line(pts([(128+rr*np.cos(a),128+rr*np.sin(a)),(128+(rr+ln)*np.cos(a),128+(rr+ln)*np.sin(a))]),fill='white',width=s)
        for dx,dy in ((1,0),(-1,0),(0,1),(0,-1)):
            d.line(pts([(128+dx*9,128+dy*9),(128+dx*29,128+dy*29)]),fill='white',width=s)
        for px,py in ((23,23),(233,23),(23,233),(233,233)):
            sx=1 if px<128 else -1;sy=1 if py<128 else -1
            d.line(pts([(px,py+sy*12),(px,py),(px+sx*12,py)]),fill=(255,255,255,175),width=s)
        for px in (37,196):
            for j,ll in enumerate([23,12,18]):
                d.line(pts([(px,123+j*4),(px+ll,123+j*4)]),fill=(255,255,255,155),width=s)
    save('tp_reticle',white(maskdraw(256,256,ret)).repeat(2,0).repeat(2,1),out)
    x,y=grid(256); nn=noise(256,256,121,12)
    scan=(.35+.65*(np.sin(y*256*np.pi/3)>.2))*smooth(.22,.65,nn)
    a=scan*(1-smooth(.7,.97,np.maximum(abs(x),abs(y))))
    z=rgba(a); z[...,:3]=a[...,None]
    save('tp_afterimage_mask',z,out)
    a=cracks(512,512,951,7); x,y=grid(512)
    z=rgba(a*.9,(.38,.19,.61)); gl=ring(x,y,.11,.018)*.35
    z[...,:3]+=np.stack([gl*.6,gl*.45,gl*.6],-1)
    z[...,3]=np.maximum(a*.9,gl)
    save('tp_fold_decal',z,out)


def vector(out):
    x,y=grid(512,128); u=(x+1)/2
    a=np.zeros_like(x)
    for yy in [-.6,-.2,.2,.6]:
        for j in range(-4,5):
            cx=j*.4+(.2 if yy>0 else 0)
            a=np.maximum(a,line(x,y,cx-.21,yy,cx+.07,yy,.011)*.65)
            a=np.maximum(a,path(x,y,[(cx-.04,yy-.11),(cx+.09,yy),(cx-.04,yy+.11)],.016))
    a*=1-smooth(.78,.96,np.abs(y))
    save('vm_arrow_field',white(a),out)
    def pressure(i,n,w,h):
        t=i/(n-1);x,y=grid(w,h);r=np.hypot(x,y);theta=np.arctan2(y,x)
        if t<.16:
            rr=.22-.1*t/.16; amp=np.sin(np.pi*t/.32)
        else:
            u=(t-.16)/.84;rr=.12+.75*(1-(1-u)**2);amp=(1-u)**.65
        a=np.exp(-((r-rr)/(.012+.012*(1-t)))**2)*amp
        a+=np.exp(-((r-rr*.94)/.014)**2)*amp*.22
        # Short pressure fingers break the outline in the late phase.
        strands=np.clip(np.cos(theta*18+np.sin(theta*5)*.7),0,1)**30
        a+=strands*np.exp(-((r-rr*.96)/.09)**2)*amp*t*.65
        if i in (0,n-1): a*=0
        return white(a)
    save('vm_pressure_sheet',sheet(8,8,128,128,pressure),out,cols=8,rows=8)
    def debris(i,n,w,h):
        rng=np.random.default_rng(711+i);x,y=grid(w,h)
        sides=5+i%4; ang=np.arange(sides)*2*np.pi/sides+.3
        pts=[(w/2+np.cos(a)*w*r,h/2+np.sin(a)*h*r) for a,r in zip(ang,rng.uniform(.2,.4,sides))]
        def draw(d,s):
            p=[(a*s,b*s) for a,b in pts]
            shade=[(93,105,122,255),(136,143,150,255),(72,82,99,255),(163,158,145,255)][i%4]
            d.polygon(p,fill=shade)
            center=(w*.47*s,h*.48*s)
            for j in range(sides):
                color=tuple(max(0,min(255,c+(-24 if j%3 else 32))) for c in shade[:3])+(255,)
                d.polygon([p[j],p[(j+1)%sides],center],fill=color)
            d.line(p[:max(2,sides//2)],fill=(199,208,214,245),width=max(1,s))
        z=canvas(w,h,draw)
        # Mineral grain is confined to the cutout, never rectangular dust tiles.
        grain=noise(w,h,817+i,5)-.5
        z[...,:3]=np.clip(z[...,:3]+grain[...,None]*.09,0,1)
        return z
    save('vm_debris_atlas',sheet(4,4,64,64,debris),out,cols=4,rows=4)
    a=cracks(512,512,213,11);z=rgba(a,(.18,.22,.29))
    # Narrow pale interior gives the fissure a beveled pressure-cut surface.
    a2=cracks(512,512,213,11)>.82
    z[...,:3]=np.where(a2[...,None],np.array([.36,.41,.46]),z[...,:3])
    save('vm_crack_decal',z,out)
    x,y=grid(512,64);a=np.zeros_like(x)
    for j in range(-1,10):
        mid=j*.25-1.1
        a=np.maximum(a,line(x,y,mid-.14,.58,mid+.1,-.58,.011))
        a=np.maximum(a,line(x,y,mid-.09,.58,mid+.15,-.58,.006)*.3)
    a*=1-smooth(.65,.94,np.abs(y))
    save('vm_shear_streak',white(a),out)


def star_mask(w,h,size=1.,stretch=1.):
    def draw(d,s):
        pts=[]
        for k in range(10):
            a=-np.pi/2+k*np.pi/5;r=.37 if k%2==0 else .17
            pts.append(((w*.5+np.cos(a)*w*r*size)*s,(h*.5+np.sin(a)*h*r*size*stretch)*s))
        d.polygon(pts,fill='white')
    return maskdraw(w,h,draw)


def mental(out):
    def blink(i,n,w,h):
        t=i/(n-1);x,y=grid(w,h)
        if t<.25: scale=.22+4.6*t
        elif t<.4: scale=1.13-(t-.25)*.87
        else: scale=1.
        squash=1-.88*np.exp(-((t-.64)/.065)**2)
        a=star_mask(w,h,scale,squash)
        inner=star_mask(w,h,scale*.82,squash*.83)
        bevel=np.clip(a-inner,0,1)
        z=rgba(a*.97,(1,.77,.26))
        light=np.clip(.6-.45*x-.4*y,0,1)
        z[...,:3]=np.stack([np.ones_like(x),.63+.24*light,.18+.45*light],-1)
        z[...,:3]=np.minimum(1,z[...,:3]+bevel[...,None]*.22)
        # Sparse white highlight, gold star pupil stays a clear silhouette.
        shine=np.exp(-(((x+.16)/.10)**2+((y+.2*squash)/.065)**2))*a
        z[...,:3]=z[...,:3]*(1-shine[...,None])+shine[...,None]*np.array([1,.98,.86])
        glow=np.exp(-(x*x+y*y)/.22)*.065*squash
        z[...,3]=np.maximum(a*.97,glow)*smooth(0,.09,t)
        return z
    save('mo_star_blink_sheet',sheet(4,4,128,128,blink),out,cols=4,rows=4)
    x,y=grid(512,32);u=(x+1)/2
    a=np.exp(-(y/.048)**2)*.8
    a=np.maximum(a,np.exp(-((y-.19)/.024)**2)*((u*24)%1<.42)*.55)
    a=np.maximum(a,np.exp(-((y+.19)/.024)**2)*((u*24+.5)%1<.26)*.4)
    for cx in np.linspace(-1.0,1.0,9):
        a=np.maximum(a,path(x,y,[(cx-.025,-.095),(cx+.025,0),(cx-.025,.095)],.018))
    save('mo_thread',white(a),out)
    def panel(d,s):
        def pts(ps):return [(a*s,b*s) for a,b in ps]
        outline=[(28,35),(197,35),(226,64),(226,210),(28,210),(28,35)]
        d.line(pts(outline),fill=(255,255,255,220),width=s)
        d.line(pts([(28,52),(199,52),(211,64)]),fill=(255,255,255,155),width=s)
        for px,py,dx,dy in [(20,27,1,1),(234,27,-1,1),(20,218,1,-1),(234,218,-1,-1)]:
            d.line(pts([(px,py+dy*21),(px,py),(px+dx*22,py)]),fill='white',width=2*s)
        for j in range(6):
            yy=84+j*17
            d.rectangle((43*s,yy*s,47*s,(yy+4)*s),fill=(255,255,255,210))
            d.line(pts([(58,yy+2),(58+[105,71,120,91,58,110][j],yy+2)]),fill=(255,255,255,135),width=s)
            d.line(pts([(58,yy+6),(58+[54,93,69,44,72,51][j],yy+6)]),fill=(255,255,255,70),width=s)
        for j in range(11):
            d.line(pts([(44+j*14,193),(44+j*14,193-(j%4+1)*3)]),fill=(255,255,255,160),width=s)
    save('mo_holo_panel',white(maskdraw(256,256,panel)),out)
    def signal(i,n,w,h):
        t=i/(n-1);x,y=grid(w,h);a=np.zeros_like(x)
        for delay in [0,.18,.36]:
            q=(t-delay)/(1-delay)
            if 0<q<1:
                rr=.11+.78*q;env=np.sin(np.pi*q)**.7*(1-q)**.4
                a=np.maximum(a,ring(x,y,rr,.015)*env)
        return white(a)
    save('mo_signal_ring_sheet',sheet(4,4,128,128,signal),out,cols=4,rows=4)
    x,y=grid(256);r=np.hypot(x,y);theta=np.arctan2(y,x)
    gate=(np.mod(theta+.17,2*np.pi)>.30)
    a=ring(x,y,.76,.010)*gate*.9+ring(x,y,.65,.006)*.24
    for k in range(60):
        ang=k*np.pi/30;rr=.81;ll=.055 if k%5==0 else .025
        a=np.maximum(a,line(x,y,rr*np.cos(ang),rr*np.sin(ang),(rr+ll)*np.cos(ang),(rr+ll)*np.sin(ang),.005)*(.85 if k%5==0 else .4))
    save('mo_status_arc',white(a),out)


def build(out=OUT):
    teleporter(out);vector(out);mental(out)


if __name__=='__main__':
    build()
