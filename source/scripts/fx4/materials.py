"""FX4 water, buoyancy, liquid shadow and paper: original procedural materials.

No downloaded or third-party artwork. Fixed-seed analytic fields, original paper
facets and smooth time curves. All colors are straight alpha (never premultiplied).
Data normals use OpenGL +Y; water/ink/paper require ordinary alpha compositing.
"""
from pathlib import Path
import math
import numpy as np
from PIL import Image, ImageDraw
from fx4lib import OUT, ROOT, grid, rgba, smooth, noise, canvas, sheet, save

PI = np.pi


def gaussian(d, width):
    return np.exp(-((d / width) ** 2))


def paint(alpha, base, light=None, light_rgb=(1, 1, 1)):
    z = rgba(alpha, base)
    if light is not None:
        q = np.clip(light, 0, 1)[..., None]
        z[..., :3] = z[..., :3] * (1-q) + np.array(light_rgb) * q
    return z


def water_ribbon(w=512, h=128):
    y, x = np.mgrid[:h, :w]
    u, v = x / (w-1), y / (h-1) * 2-1
    n = noise(w, h, 714, 6)
    center = .07*np.sin(2*PI*u*2) + .04*np.sin(2*PI*u*5)
    d = np.abs(v-center)
    edge = .69 + .05*np.sin(2*PI*u*3) + .025*np.sin(2*PI*u*11)
    body = (1-smooth(edge-.035, edge+.035, d)) * (.65 + .18*n)
    # Crossed caustic ridges, not blurred stripes.
    c1 = gaussian(np.sin(2*PI*(u*3+.33*v+.10*np.sin(v*7))), .09)
    c2 = gaussian(np.sin(2*PI*(u*5-.40*v+.08*np.cos(v*9))), .09)
    caustic = np.clip(c1+c2, 0, 1) * .6
    foam = gaussian(d-edge+.025, .027) * (.45+.55*gaussian(np.sin(PI*18*u),.65))
    shine = np.clip(caustic+foam,0,1)
    a = body + foam*.6
    return paint(a, (.055,.37,.72), shine, (.84,.97,1))


def crown(i,n,w,h):
    """Side-view hollow water crown: outward sheet, scalloped rim, ballistic drops."""
    t=i/(n-1); x,y=grid(w,h)
    env = smooth(0,.10,t) * (1-smooth(.72,1,t))
    radius=.16+.62*(1-(1-t)**2)
    height=.68*np.sin(PI*t)**.8
    q=x/max(radius,.01)
    inside=np.maximum(0,1-q*q)
    base=.57+.10*np.sqrt(inside)
    # View front wall; top stays open, with a serrated lip and a rear rim only.
    spikes=(.5+.5*np.cos(q*PI*5+.5))**8
    top=base-height*(.40+.60*spikes)*inside**.28
    side=(1-smooth(.98,1.02,np.abs(q)))
    wall=smooth(top-.02,top+.018,y)*(1-smooth(base-.015,base+.025,y))*side
    lip=gaussian(y-top,.016)*side
    foot=gaussian(np.sqrt((x/max(radius,.02))**2+((y-.57)/.13)**2)-1,.055)
    back=gaussian(y-(.57-.10*np.sqrt(inside)),.012)*side*.35
    striation=gaussian(np.sin(14*PI*q+.4*y),.20)
    a=(wall*(.52+.16*striation)+lip*.9+foot*.65+back)*env
    shine=np.clip(lip+striation*.3+foot*.5,0,1)
    # Detached droplets follow deterministic outward parabolic paths.
    for k in range(11):
        birth=.13+.018*(k%3); age=max(0,t-birth)
        sign=-1 if k%2 else 1
        vx=sign*(.4+.045*k)
        px=sign*.13 + vx*age
        py=.38-(1.6+.07*(k%4))*age+2.8*age*age
        rr=.014+.004*(k%3)
        blob=gaussian(np.sqrt(((x-px)/rr)**2+((y-py)/(rr*1.5))**2),1)
        d_env=smooth(birth,birth+.05,t)*(1-smooth(.75,.98,t))
        a=np.maximum(a,blob*.85*d_env)
        shine=np.maximum(shine,blob*.6)
    return paint(a,(.055,.41,.78),shine,(.80,.96,1))


def drop(i,n,w,h):
    x,y=grid(w,h); angle=.14*np.sin(i*2.1)
    xx=x*np.cos(angle)-y*np.sin(angle); yy=x*np.sin(angle)+y*np.cos(angle)
    rw=.24+.045*(i%4); rh=.30+.10*(i//4)
    # Pear-shaped liquid droplet, with rounded lower lobe.
    taper=.58+.42*smooth(-.6,.5,yy)
    d=np.sqrt((xx/(rw*taper))**2+((yy-.02)/rh)**2)
    a=1-smooth(.94,1.04,d)
    rim=gaussian(d-.85,.08)
    glint=gaussian(np.sqrt(((xx+rw*.30)/(.11*rw+.025))**2+((yy+rh*.25)/(.22*rh+.025))**2),1)
    shine=np.clip(rim*.45+glint*.9,0,1)
    return paint(a*(.67+.26*rim),(.07,.44,.78),shine,(.90,.98,1))


def ripple(i,n,w,h):
    t=i/(n-1); x,y=grid(w,h); r=np.hypot(x,y)
    a=np.zeros_like(x)
    for j in range(3):
        age=t-j*.12
        if age>0:
            radius=.04+.90*age
            env=smooth(0,.10,age)*(1-smooth(.45,.87,age))
            a+=gaussian(r-radius,.009+.004*age)*env*(1-j*.18)
    return rgba(a*(1-smooth(.86,1,t)))


def curtain():
    w,h=256,512; y,x=np.mgrid[:h,:w]; u=x/(w-1); v=y/(h-1)
    sides=smooth(0,.08,u)*(1-smooth(.92,1,u))
    wave=np.sin(2*PI*(9*u+.06*np.sin(2*PI*v*3)))
    threads=gaussian(wave,.15)
    flow=gaussian(np.sin(2*PI*(v*5+.25*np.sin(u*29))),.11)
    body=(.60+.13*np.sin(u*17)**2)*sides
    a=body+threads*.22*sides
    light=np.clip(threads*.65+flow*.20,0,1)
    return paint(a,(.035,.36,.67),light,(.82,.96,1))


def wet_decal():
    w=h=512; x,y=grid(w,h); n=noise(w,h,514,7)
    r=np.sqrt(x*x+(y*1.2)**2); theta=np.arctan2(y,x)
    boundary=.67+.07*np.sin(theta*5)+.045*np.sin(theta*11)+.03*np.cos(theta*19)
    body=1-smooth(boundary-.025,boundary+.025,r)
    rim=gaussian(r-boundary+.025,.012)
    patch=gaussian(np.sin(x*14+y*9+.4*np.sin(y*21)),.10)
    glint=patch*.20*(1-smooth(.4,.7,r))+rim*.35
    return paint(body*(.47+.12*n),(.025,.16,.25),glint,(.56,.78,.85))


def bubble(i,n,w,h):
    x,y=grid(w,h); r0=.42+.035*(i%4)
    sx=1+.04*np.sin(i); sy=1+.05*np.cos(i*.8)
    r=np.sqrt((x/sx)**2+(y/sy)**2); theta=np.arctan2(y,x)
    inside=1-smooth(r0-.012,r0+.012,r)
    rim=gaussian(r-r0+.02,.025)
    inner=gaussian(r-r0+.07,.016)*.18
    phase=theta*1.5+i*.48+r*20
    hue=np.stack([.70+.22*np.sin(phase),.80+.17*np.sin(phase+2.1),.88+.12*np.sin(phase+4.2)],-1)
    a=(.035+rim*.88+inner)*inside
    shine=gaussian(theta+2.15,.24)*gaussian(r-r0+.045,.035)
    z=rgba(a)
    z[...,:3]=hue*(1-shine[...,None]*.6)+shine[...,None]*.6
    return z


def slice_ring():
    w=h=512; x,y=grid(w,h); r=np.hypot(x,y); theta=np.arctan2(y,x)
    a=gaussian(r-.69,.004)+gaussian(r-.63,.002)*.32
    for k in range(72):
        ang=k*2*PI/72; ad=np.arctan2(np.sin(theta-ang),np.cos(theta-ang))
        length=.040 if k%6==0 else .015
        a+=gaussian(ad,.002)*(smooth(.71,.714,r)-smooth(.714+length,.718+length,r))*.9
    # Discrete density scale arrows, with no magic-rune decoration.
    for k in range(4):
        ang=k*PI/2
        xx=x*np.cos(ang)+y*np.sin(ang); yy=-x*np.sin(ang)+y*np.cos(ang)
        a+=gaussian(yy,.003)*smooth(.48,.49,xx)*(1-smooth(.56,.57,xx))*.5
    return rgba(a)


def bubble_column():
    w,h=128,512; y,x=np.mgrid[:h,:w]; u=x/(w-1)*2-1; v=y/(h-1)
    a=np.zeros((h,w)); rng=np.random.default_rng(348)
    for k in range(21):
        px=rng.uniform(-.6,.6); py=rng.uniform(0,1); rad=rng.uniform(.035,.10)
        dy=(v-py+.5)%1-.5
        r=np.sqrt((u-px)**2+(dy*8)**2)
        ring=gaussian(r-rad, .009+.008*rad)
        glint=gaussian(np.sqrt((u-(px-rad*.35))**2+(dy*8+rad*.45)**2),rad*.14)
        a=np.maximum(a,ring*.65+glint*.5)
    return rgba(a)


def pop(i,n,w,h):
    t=i/(n-1); x,y=grid(w,h); r=np.hypot(x,y); theta=np.arctan2(y,x)
    radius=.22+.55*t
    env=smooth(0,.06,t)*(1-smooth(.55,1,t))
    gap=np.sin(theta*8+t*3)>.25+t*.55
    a=gaussian(r-radius,.018*(1-t)+.008)*gap*env
    for k in range(8):
        ang=k*2*PI/8+.2
        rr=.25+.55*t
        a+=gaussian(np.hypot(x-rr*np.cos(ang),y-rr*np.sin(ang)),.023*(1-t)+.005)*env
    return rgba(a)


def ink_surface(field, highlight=None):
    body=smooth(.88,1.10,field)
    # Black body, pale narrow wet specular, and only a restrained violet rim.
    edge=gaussian(field-1.07,.13)*body
    gy,gx=np.gradient(np.log1p(field))
    grad=np.sqrt(gx*gx+gy*gy)+1e-5
    facing=np.clip((-gx*.55-gy*.84)/grad,0,1)
    # Only a narrow part of the rounded surface catches light. Gradients at an
    # inverse-square center are not a real surface normal and must stay black.
    wet=facing*gaussian(field-1.65,.36)*body
    if highlight is not None: wet=np.maximum(wet,highlight*body)
    z=paint(body*.96,(.028,.020,.045),edge*.45,(.22,.14,.35))
    s=np.clip(wet*.55,0,.62)[...,None]
    z[...,:3]=z[...,:3]*(1-s)+np.array([.52,.50,.62])*s
    return z


def ink(i,n,w,h):
    t=i/(n-1); x,y=grid(w,h)
    growth=smooth(0,.22,t); fade=1-smooth(.78,1,t)
    r=.26*growth*fade
    extension=np.sin(PI*t)**2
    field=(r*r)/(x*x+(y-.14)**2+.007)
    for k in range(6):
        ang=k*2*PI/6 + .25*np.sin(t*PI)
        reach=.12+.45*extension*(.7+.3*np.cos(ang))
        px=reach*np.cos(ang); py=.12+reach*np.sin(ang)*.7
        rr=(.11+.02*np.sin(k*1.3))*growth*fade
        field+=rr*rr/((x-px)**2+(y-py)**2+.004)
        # Connecting beads retain a smooth, visible liquid filament.
        for m in [0.35,.65]:
            field+=(rr*.52)**2/((x-px*m)**2+(y-(.12+(py-.12)*m))**2+.003)
    z=ink_surface(field)
    z[...,3]*=smooth(0,.04,t)*(1-smooth(.95,1,t))
    return z


def ink_ribbon():
    w,h=512,128; y,x=np.mgrid[:h,:w]; u=x/(w-1); v=y/(h-1)*2-1
    center=.11*np.sin(2*PI*u*2)+.06*np.sin(2*PI*u*5)
    width=.45+.06*np.cos(2*PI*u*3)
    d=np.abs(v-center)
    field=(width/(d+.001))**2
    shine=gaussian(v-center+width*.35,.025)*(.20+.80*gaussian(np.sin(2*PI*u*4),.70))
    return ink_surface(field,shine*.7)


def drip(i,n,w,h):
    t=i/(n-1); x,y=grid(w,h)
    # Starts attached, elongates, neck breaks, drop falls and fades.
    neck=(1-smooth(.35,.60,t))
    drop_y=-.30+1.40*t*t
    field=.18**2/(x*x+((y+.62)*1.1)**2+.004)
    field+=(.20-.06*t)**2/(x*x+((y-drop_y)*.7)**2+.004)
    for m in np.linspace(0,1,7):
        cy=-.62+(drop_y+.62)*m
        field+=(.055*neck)**2/(x*x+(y-cy)**2+.002)
    z=ink_surface(field); z[...,3]*=smooth(0,.08,t)*(1-smooth(.76,1,t))
    return z


def pool():
    w=h=512; x,y=grid(w,h); r=np.sqrt(x*x+(y*1.17)**2); theta=np.arctan2(y,x)
    radius=.66+.05*np.sin(theta*5)+.04*np.sin(theta*9)+.025*np.cos(theta*15)
    field=(radius/(r+.001))**3
    streak=gaussian(y+.25+.06*np.sin(x*9),.007)*smooth(-.44,-.34,x)*(1-smooth(.28,.40,x))*.7
    return ink_surface(field,streak)


def tendril():
    w,h=512,64; x,y=grid(w,h); u=(x+1)/2
    center=.11*np.sin(u*PI*2)*np.sin(u*PI)
    width=.58*(1-u)**.8+.008
    end=smooth(0,.02,u)*(1-smooth(.96,.985,u))
    field=(width/(np.abs(y-center)+.002))**2
    shine=gaussian(y-center+width*.42,.025)*.60
    z=ink_surface(field,shine); z[...,3]*=end
    return z


PAPER_COLORS=[(238,225,192,255),(247,237,210,255),(216,198,160,255),(190,168,125,255)]


def paper_cell(i,n,w,h):
    """Original folded panels: square, book fold, triangle, kite, crane/plane wings."""
    facets = [
      [([(.18,.18),(.82,.18),(.82,.82),(.18,.82)],0)],
      [([(.19,.20),(.52,.26),(.52,.82),(.19,.75)],0),([(.52,.26),(.81,.18),(.81,.75),(.52,.82)],2)],
      [([(.15,.80),(.79,.18),(.85,.80)],1),([(.15,.80),(.79,.18),(.57,.70)],2)],
      [([(.50,.10),(.83,.50),(.50,.91)],0),([(.50,.10),(.50,.91),(.17,.50)],2),([(.50,.10),(.56,.52),(.50,.91)],1)],
      [([(.12,.73),(.42,.30),(.88,.19),(.63,.62)],1),([(.12,.73),(.63,.62),(.45,.84)],2),([(.42,.30),(.63,.62),(.56,.48)],0)],
      [([(.09,.73),(.91,.21),(.58,.79)],1),([(.09,.73),(.60,.59),(.91,.21)],2),([(.60,.59),(.70,.70),(.91,.21)],0)],
      [([(.16,.74),(.24,.25),(.40,.65)],0),([(.24,.25),(.48,.18),(.40,.65)],2),([(.48,.18),(.67,.25),(.40,.65)],1),([(.67,.25),(.85,.42),(.40,.65)],2),([(.85,.42),(.83,.76),(.40,.65)],0)],
      [([(.24,.23),(.58,.17),(.80,.36),(.69,.78),(.30,.83),(.16,.51)],0),([(.24,.23),(.55,.49),(.58,.17)],1),([(.58,.17),(.55,.49),(.80,.36)],2),([(.80,.36),(.55,.49),(.69,.78)],0),([(.69,.78),(.55,.49),(.30,.83)],2),([(.30,.83),(.55,.49),(.16,.51)],1)]
    ]
    def draw(d,s):
        for poly,c in facets[i]:
            pts=[(round(x*w*s),round(y*h*s)) for x,y in poly]
            d.polygon(pts,fill=PAPER_COLORS[c])
            d.line(pts+[pts[0]],fill=(160,137,95,255),width=max(1,int(s*.65)))
    z=canvas(w,h,draw,scale=3)
    # Fibers modulate actual paper color, retaining opaque body and crisp folds.
    xx,yy=grid(w,h); grain=noise(w,h,919,19)-.5
    fiber=np.sin(xx*280+np.sin(yy*12)*.7)*.006+grain*.035
    z[...,:3]=np.clip(z[...,:3]+fiber[...,None],0,1)
    return z


def paper_normal():
    w=h=256; x,y=grid(w,h)
    # Piecewise flat sheet with opposing valley and mountain creases.
    height=.055*np.abs(x+.05)+.025*np.abs(x*.72+y*.48-.15)-.020*np.abs(y*.70-x*.40+.18)
    gy,gx=np.gradient(height,2/(h-1),2/(w-1))
    nn=np.stack([-gx,gy,np.ones_like(x)],-1)
    nn/=np.linalg.norm(nn,axis=-1,keepdims=True)
    z=rgba(np.ones((h,w))); z[...,:3]=nn*.5+.5
    return z


def paper_scrap(i,n,w,h):
    t=i/(n-1); roll=t*PI*4; yaw=t*PI*6+.2
    # Side changes brightness at edge-on turns; same paper pieces persist over time.
    def draw(d,s):
        for k in range(4):
            phase=k*1.6; cx=.50+.20*np.sin(t*PI*2+phase)
            cy=.29+.42*t+.11*np.sin(phase+t*PI)
            rot=roll+phase; flatten=.08+.92*abs(np.cos(yaw+phase))
            rw=(.12+.015*(k%2))*flatten; rh=.085+.015*(k%3)
            pts=[]
            for px,py in [(-rw,-rh),(rw,-rh),(rw,rh),(-rw,rh)]:
                xx=cx+px*np.cos(rot)-py*np.sin(rot); yy=cy+px*np.sin(rot)+py*np.cos(rot)
                pts.append((int(xx*w*s),int(yy*h*s)))
            front=np.cos(yaw+phase)>0
            color=(241,228,195,255) if front else (185,164,124,255)
            d.polygon(pts,fill=color)
            d.line(pts+[pts[0]],fill=(151,128,87,255),width=max(1,s//2))
            d.line([pts[0],pts[2]],fill=(215,197,157,255),width=max(1,s//2))
    z=canvas(w,h,draw,3)
    z[...,3]*=smooth(0,.06,t)*(1-smooth(.87,1,t))
    return z


def paper_fiber():
    w=h=256; yy,xx=np.mgrid[:h,:w]; u=xx/(w-1); v=yy/(h-1)
    n=noise(w,h,3129,42)-.5
    grain=n*.035
    rng=np.random.default_rng(3129)
    # Random oriented, tapered cellulose fibers; never a regular woven grid.
    for k in range(110):
        cx,cy=rng.random(2); angle=rng.uniform(0,2*PI)
        length=rng.uniform(.018,.065); width=rng.uniform(.001,.0027)
        dx=(u-cx+.5)%1-.5; dy=(v-cy+.5)%1-.5
        along=dx*np.cos(angle)+dy*np.sin(angle)
        across=-dx*np.sin(angle)+dy*np.cos(angle)
        fiber=gaussian(across,width)*gaussian(along,length)
        grain+=fiber*rng.uniform(-.022,.024)
    z=rgba(np.ones((h,w)),(.935,.882,.75)); z[...,:3]+=grain[...,None]
    return z


def edge_shadow():
    w,h=256,16; yy,xx=np.mgrid[:h,:w]; u=xx/(w-1)
    shade=.32+.68*smooth(0,.85,u)
    return rgba(np.ones((h,w)),np.stack([shade]*3,-1))


def build(out=OUT):
    save('hh_water_ribbon',water_ribbon(),out)
    save('hh_splash_sheet',sheet(8,8,128,128,crown),out,8,8)
    save('hh_drop_atlas',sheet(4,4,64,64,drop),out,4,4)
    save('hh_ripple_sheet',sheet(4,4,128,128,ripple),out,4,4)
    save('hh_curtain',curtain(),out)
    save('hh_wet_decal',wet_decal(),out)
    save('fd_bubble_atlas',sheet(4,4,128,128,bubble),out,4,4)
    save('fd_slice_ring',slice_ring(),out)
    save('fd_column',bubble_column(),out)
    save('fd_pop_sheet',sheet(4,4,64,64,pop),out,4,4)
    save('ls_ink_sheet',sheet(8,8,128,128,ink),out,8,8)
    save('ls_wet_ribbon',ink_ribbon(),out)
    save('ls_drip_sheet',sheet(2,4,128,128,drip),out,2,4)
    save('ls_pool_decal',pool(),out)
    save('ls_tendril',tendril(),out)
    save('pp_sheet_atlas',sheet(4,2,256,256,paper_cell),out,4,2)
    save('pp_fold_normal',paper_normal(),out)
    save('pp_scrap_sheet',sheet(8,8,128,128,paper_scrap),out,8,8)
    save('pp_fiber',paper_fiber(),out)
    save('pp_edge_shadow',edge_shadow(),out)


if __name__=='__main__':
    build()
    names=['hh_water_ribbon','hh_splash_sheet','hh_drop_atlas','hh_ripple_sheet','hh_curtain','hh_wet_decal','fd_bubble_atlas','fd_slice_ring','fd_column','fd_pop_sheet','ls_ink_sheet','ls_wet_ribbon','ls_drip_sheet','ls_pool_decal','ls_tendril','pp_sheet_atlas','pp_fold_normal','pp_scrap_sheet','pp_fiber','pp_edge_shadow']
    layouts={'hh_splash_sheet':(8,8),'hh_drop_atlas':(4,4),'hh_ripple_sheet':(4,4),'fd_bubble_atlas':(4,4),'fd_pop_sheet':(4,4),'ls_ink_sheet':(8,8),'ls_drip_sheet':(2,4),'pp_sheet_atlas':(4,2),'pp_scrap_sheet':(8,8)}
    contact=Image.new('RGB',(1200,math.ceil(len(names)/5)*270),(24,30,39)); d=ImageDraw.Draw(contact)
    for j,name in enumerate(names):
        im=Image.open(OUT/(name+'.png')).convert('RGBA')
        if name in layouts:
            c,r=layouts[name]; cw,ch=im.width//c,im.height//r
            if 'atlas' not in name:
                k=int(c*r*.43); im=im.crop((k%c*cw,k//c*ch,(k%c+1)*cw,(k//c+1)*ch))
        im.thumbnail((222,222))
        px=(j%5)*240; py=(j//5)*270
        contact.paste(im,(px+(240-im.width)//2,py+12+(222-im.height)//2),im)
        d.text((px+8,py+242),name,fill=(225,230,236))
    contact.save(ROOT/'qa/evidence/2026-10-29-fx4-textures/materials-review.png')
    print('Wrote 20 textures and materials-review.png')
