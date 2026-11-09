"""Original, reproducible hard-surface item meshes and painted materials (2026-10-29).

Geometry is authored here in 0..16 design units and exported as normalized OBJ.
All material pixels are generated locally, without external artwork or blur. The
NeoForge built-in OBJ loader supplies ordinary baked models; no gameplay code changes.
"""
import hashlib
import json
import math
import random
from pathlib import Path
from PIL import Image, ImageDraw
from canon_aux_pixel_art import display
from canon_aux_refined_specials import crystal, crane

ROOT = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/academy'
TEX = ROOT / 'textures/items/refined'
OBJ = ROOT / 'models/item/refined'
MODELS = ROOT / 'models/item'
PALETTE = {
    'ink': (26, 36, 47), 'rubber': (43, 51, 59), 'metal': (135, 158, 173),
    'steel': (77, 101, 118), 'silver': (198, 216, 222), 'gold': (208, 170, 91),
    'white': (226, 235, 228), 'pearl': (224, 228, 245), 'lilac': (165, 169, 214),
    'cyan': (116, 204, 218), 'teal': (50, 130, 134), 'pcb': (40, 91, 91),
    'paper': (242, 239, 216), 'paper_shadow': (187, 200, 197), 'red': (194, 101, 75),
    'water': (43, 137, 190), 'water_top': (94, 197, 219), 'glass': (178, 219, 230),
}
FONT = {
 'A':['010','101','111','101','101'],'B':['110','101','110','101','110'],
 'C':['011','100','100','100','011'],'D':['110','101','101','101','110'],
 'E':['111','100','110','100','111'],'F':['111','100','110','100','100'],
 'G':['011','100','101','101','011'],'H':['101','101','111','101','101'],
 'I':['111','010','010','010','111'],'J':['001','001','001','101','010'],
 'K':['101','101','110','101','101'],'L':['100','100','100','100','111'],
 'M':['101','111','111','101','101'],'N':['101','111','111','111','101'],
 'O':['010','101','101','101','010'],'P':['110','101','110','100','100'],
 'Q':['010','101','101','111','011'],'R':['110','101','110','101','101'],
 'S':['011','100','010','001','110'],'T':['111','010','010','010','010'],
 'U':['101','101','101','101','111'],'V':['101','101','101','101','010'],
 'W':['101','101','111','111','101'],'X':['101','101','010','101','101'],
 'Y':['101','101','010','010','010'],'Z':['111','001','010','100','111'],
 '0':['111','101','101','101','111'],'1':['010','110','010','010','111'],
 '2':['110','001','010','100','111'],'3':['110','001','010','001','110'],
 '4':['101','101','111','001','001'],'5':['111','100','110','001','110'],
 '6':['011','100','111','101','111'],'7':['111','001','010','010','010'],
 '8':['111','101','111','101','111'],'9':['111','101','111','001','110'],
 '-':['000','000','111','000','000'],'.':['000','000','000','000','010'],
 '/':['001','001','010','100','100'],':':['000','010','000','010','000'],
}


def text(draw, xy, value, color, scale=1):
    x, y = xy
    for char in value:
        for row, bits in enumerate(FONT.get(char, ['000'] * 5)):
            for col, bit in enumerate(bits):
                if bit == '1':draw.rectangle((x+col*scale,y+row*scale,x+(col+1)*scale-1,y+(row+1)*scale-1),fill=color)
        x += 4 * scale


def save(im, name):
    TEX.mkdir(parents=True, exist_ok=True)
    im.save(TEX/(name+'.png'), optimize=True)


def textures():
    for name, color in PALETTE.items():
        rng = random.Random(name)
        im = Image.new('RGBA', (64, 64));d = ImageDraw.Draw(im)
        for y in range(64):
            brushed = ((y*13)%7-3) if name in ('metal','steel','silver','gold') else 0
            for x in range(64):
                grain = rng.choice((-2,-1,0,0,0,1,2)) + brushed
                alpha = 47 if name == 'glass' else 255
                im.putpixel((x,y), tuple(max(0,min(255,c+grain)) for c in color)+(alpha,))
        if name == 'glass':
            d.line((7, 2, 7, 58), fill=(230,250,255,90), width=1)
            d.line((54, 10, 54, 40), fill=(235,250,255,65), width=1)
        save(im,name)
    im=Image.new('RGBA',(128,256),(43,53,64));d=ImageDraw.Draw(im)
    for y in range(256):d.line((0,y,127,y),fill=(42+y%3,52+y%3,63+y%3))
    d.rectangle((9,8,118,247),outline=(111,128,137),width=1)
    for y in (20,231):d.line((17,y,110,y),fill=(25,34,43))
    text(d,(23,23),'MENTAL OUT',(170,190,191),2)
    text(d,(33,234),'CTRL-07',(121,143,154),1)
    for y in range(202,223,4):
        d.line((38,y,91,y),fill=(26,36,46));d.line((39,y,90,y),fill=(78,94,107))
    save(im,'remote_panel')
    im=Image.new('RGBA',(128,64),(13,29,35));d=ImageDraw.Draw(im)
    text(d,(9,10),'AIM LINK',(110,210,206),2)
    text(d,(10,42),'24 M',(167,185,178),2)
    for i in range(5):d.rectangle((84+i*6,51-i*5,87+i*6,54),fill=(209,176,101))
    d.line((5,4,122,4),fill=(50,89,95));save(im,'remote_screen')
    im=Image.new('RGBA',(256,128),(133,155,171));d=ImageDraw.Draw(im)
    for y in range(128):d.line((0,y,255,y),fill=(132+y%4,153+y%4,170+y%4))
    d.rectangle((0,38,255,91),fill=(35,77,109));d.line((0,40,255,40),fill=(133,193,215),width=2)
    text(d,(44,47),'N2',(224,236,235),6)
    text(d,(32,82),'NITROGEN',(148,198,213),1)
    for x in range(9,53,3):d.rectangle((x,103,x+(x%2),119),fill=(71,92,107))
    text(d,(64,108),'PRESSURE / 02',(62,86,104),2);save(im,'canister_wrap')
    im=Image.new('RGBA',(128,128));d=ImageDraw.Draw(im)
    d.ellipse((4,4,123,123),fill=(35,51,63));d.ellipse((10,10,117,117),fill=(227,232,219))
    for i in range(13):
        a=math.radians(145+i*20);r=46
        d.line((64+math.cos(a)*r,64+math.sin(a)*r,64+math.cos(a)*(r-7),64+math.sin(a)*(r-7)),fill=(48,66,78),width=2)
    d.line((64,68,91,35),fill=(177,91,66),width=3);d.ellipse((59,63,69,73),fill=(57,74,86))
    text(d,(39,89),'BAR',(65,85,96),2);save(im,'gauge')
    im=Image.new('RGBA',(128,128),(33,73,74));d=ImageDraw.Draw(im)
    for i in range(6):
        x=9+i*21;d.line([(x,5),(x,20),(x+8,28),(x+8,105),(x-4,117)],fill=(73,128,122),width=1)
        d.rectangle((x+5,106,x+9,110),fill=(174,156,93))
    text(d,(18,9),'AERO-03',(157,190,176),2)
    text(d,(18,112),'PRESSURE',(138,170,156),1);save(im,'chip_panel')
    im=Image.new('RGBA',(128,128),(126,160,165));d=ImageDraw.Draw(im)
    for y in range(14,119,24):
        d.line((80,y,117,y),fill=(235,238,216),width=2)
        text(d,(98,y+5),str(4-(y-14)//24),(224,231,211),1)
    d.line((75,10,75,122),fill=(226,234,220),width=1)
    text(d,(9,8),'HYDRO',(204,225,220),1);save(im,'flask_ruler')
    im=Image.new('RGBA',(256,64),(226,231,218));d=ImageDraw.Draw(im)
    d.rectangle((0,25,255,54),fill=(45,139,147));d.line((0,26,255,26),fill=(160,221,216),width=2)
    for x in range(12,256,64):
        text(d,(x,34),'BUOY',(221,235,217),2)
        d.rectangle((x+39,7,x+48,19),fill=(204,151,81))
    save(im,'buoy_wrap')


class Mesh:
    def __init__(self):self.faces=[]
    def poly(self, points, material, uv=None, both=False):
        points=[tuple(map(float,p)) for p in points]
        if len(points)>4:
            for i in range(1,len(points)-1):self.poly([points[0],points[i],points[i+1]],material,both=both)
            return
        assert len(points) in (3,4)
        uv=uv or ([(0,1),(1,1),(1,0)] if len(points)==3 else [(0,1),(1,1),(1,0),(0,0)])
        a,b,c=points[:3];u=[b[i]-a[i] for i in range(3)];v=[c[i]-a[i] for i in range(3)]
        n=(u[1]*v[2]-u[2]*v[1],u[2]*v[0]-u[0]*v[2],u[0]*v[1]-u[1]*v[0]);length=math.sqrt(sum(t*t for t in n))
        assert length>1e-8,(points,material)
        if len(points)==4 and abs(sum(n[i]*(points[3][i]-a[i]) for i in range(3)))/length>1e-5:
            # An actual folded surface needs two planar triangles, each with its
            # own normal and the matching part of the authored UV map.
            for ids in ((0,1,2),(0,2,3)):
                self.poly([points[i] for i in ids],material,[uv[i] for i in ids],both)
            return
        self.faces.append((points,material,list(uv),tuple(t/length for t in n)))
        if both:self.poly(list(reversed(points)),material,list(reversed(uv)))
    def box(self,lo,hi,material,top_material=None):
        x,y,z=lo;X,Y,Z=hi
        for face,p in enumerate([[(X,y,z),(x,y,z),(x,Y,z),(X,Y,z)],[(x,y,Z),(X,y,Z),(X,Y,Z),(x,Y,Z)],
                  [(x,y,z),(x,y,Z),(x,Y,Z),(x,Y,z)],[(X,y,Z),(X,y,z),(X,Y,z),(X,Y,Z)],
                  [(x,Y,z),(x,Y,Z),(X,Y,Z),(X,Y,z)],[(x,y,Z),(x,y,z),(X,y,z),(X,y,Z)]]):self.poly(p,top_material if face==4 and top_material else material)
    def export(self,path):
        lines=['# Original ACA procedural item mesh; normalized OBJ coordinates.','mtllib canon_aux_refined.mtl','o '+path.stem,'s off'];idx=1
        for points,mat,uv,normal in self.faces:
            lines+=['usemtl '+mat]
            for x,y,z in points:lines.append('v %.7f %.7f %.7f'%(x/16,y/16,z/16))
            for u,v in uv:lines.append('vt %.7f %.7f'%(u,1-v))
            for _ in points:lines.append('vn %.7f %.7f %.7f'%normal)
            lines.append('f '+' '.join(f'{i}/{i}/{i}' for i in range(idx,idx+len(points))))
            idx+=len(points)
        path.write_text('\n'.join(lines)+'\n')


def bevel(m,lo,hi,b,material,edge='steel',front=None):
    x,y,z=lo;X,Y,Z=hi
    def ring(zv,inset):
        a=x+inset;A=X-inset;c=y+inset;C=Y-inset;r=max(.01,b-inset/2)
        return [(a+r,c,zv),(A-r,c,zv),(A,c+r,zv),(A,C-r,zv),(A-r,C,zv),(a+r,C,zv),(a,C-r,zv),(a,c+r,zv)]
    rings=[ring(z,b*.45),ring(z+b,0),ring(Z-b,0),ring(Z,b*.45)]
    for k in range(3):
        for i in range(8):m.poly([rings[k][i],rings[k][(i+1)%8],rings[k+1][(i+1)%8],rings[k+1][i]],edge if k!=1 else material)
    for k,rev in [(0,True),(3,False)]:
        center=((x+X)/2,(y+Y)/2,rings[k][0][2])
        for i in range(8):
            pts=[center,rings[k][(i+1)%8],rings[k][i]] if rev else [center,rings[k][i],rings[k][(i+1)%8]]
            uv=[((1-(p[0]-x)/(X-x)) if rev else (p[0]-x)/(X-x),1-(p[1]-y)/(Y-y)) for p in pts]
            m.poly(pts,front if rev and front else material,uv)


def tube(m,center,rings,material,n=12,axis='y',caps=True,wrap=False,cap_material=None):
    # rings are (axis-position,radius), with angular flat normals for a machined silhouette.
    def point(t,r,a):
        c=math.cos(a)*r;s=math.sin(a)*r
        return (center[0]+c,t,center[2]+s) if axis=='y' else (center[0]+c,center[1]+s,t)
    grid=[[point(t,r,i*2*math.pi/n) for i in range(n)] for t,r in rings]
    for k in range(len(grid)-1):
        for i in range(n):
            p=[grid[k][i],grid[k+1][i],grid[k+1][(i+1)%n],grid[k][(i+1)%n]]
            if axis=='z':p=list(reversed(p))
            if wrap:
                v0=1-(rings[k][0]-rings[0][0])/(rings[-1][0]-rings[0][0])
                v1=1-(rings[k+1][0]-rings[0][0])/(rings[-1][0]-rings[0][0])
                uv=[(1-i/n,v0),(1-i/n,v1),(1-(i+1)/n,v1),(1-(i+1)/n,v0)]
                if axis=='z':uv=list(reversed(uv))
            else:uv=None
            m.poly(p,material,uv)
    if caps:
        for k,reverse in [(0,False),(-1,True)]:
            t,r=rings[k];mid=(center[0],t,center[2]) if axis=='y' else (center[0],center[1],t)
            for i in range(n):
                p=[mid,grid[k][i],grid[k][(i+1)%n]]
                if reverse != (axis=='z'):p=list(reversed(p))
                m.poly(p,cap_material or material)


def badge(m,center,radius,z,material,points=5):
    x,y=center;ring=[]
    for i in range(points*2):
        a=math.pi/2+i*math.pi/points;r=radius if i%2==0 else radius*.45
        ring.append((x+math.cos(a)*r,y+math.sin(a)*r,z))
    for i in range(len(ring)):m.poly([(x,y,z),ring[(i+1)%len(ring)],ring[i]],material)


def remote(m):
    bevel(m,(4.8,1,6.6),(11.2,15,9.1),.38,'rubber','metal','remote_panel')
    bevel(m,(5.2,1.5,6.25),(10.8,14.5,6.66),.12,'ink','steel','remote_panel')
    bevel(m,(5.55,11.35,6.13),(10.45,13.65,6.28),.06,'ink','metal','remote_screen')
    tube(m,(8,8.8,0),[(5.98,1.65),(6.13,1.8)],'steel',12,'z')
    badge(m,(8,8.8),1.46,5.94,'gold')
    for y in (4.2,5.6):
        for x,mat in [(6.15,'silver'),(8,'gold'),(9.85,'cyan')]:
            tube(m,(x,y,0),[(6.03,.32),(6.19,.38)],mat,8,'z')
    for x in (5.4,10.6):
        for y in (2.1,14.05):
            tube(m,(x,y,0),[(6.17,.15),(6.27,.18)],'silver',8,'z')
            m.box((x-.08,y-.02,6.13),(x+.08,y+.02,6.17),'ink')
    bevel(m,(11.16,10.1,7.2),(11.5,12.5,8.4),.09,'steel','silver')
    for y in (3.2,3.7,4.2):m.box((6,y,9.09),(10,y+.11,9.13),'ink')


def canister(m):
    tube(m,(8,0,8),[(1.3,2.7),(1.65,3.15),(10.5,3.15),(11.4,2.65),(12.0,1.3)],'canister_wrap',16,wrap=True,cap_material='metal')
    for y in (1.4,10.3):tube(m,(8,0,8),[(y,3.15),(y+.2,3.33),(y+.48,3.15)],'steel',16)
    tube(m,(8,0,8),[(12,.75),(13.4,.75),(13.6,.55)],'gold',12)
    bevel(m,(5.8,13.6,7.5),(10.2,14.2,8.5),.12,'steel','silver')
    for x in (5.7,10.3):tube(m,(x,0,8),[(13.4,.35),(14.5,.35)],'rubber',8)
    tube(m,(10,11.5,0),[(4.55,.85),(4.85,.96),(5.2,.85)],'metal',16,'z')
    # Dial uses a planar, correctly oriented material face in front of its bezel.
    m.poly([(10.82,10.68,4.53),(9.18,10.68,4.53),(9.18,12.32,4.53),(10.82,12.32,4.53)],'gauge')
    bevel(m,(10.0,12,7.3),(13.0,12.8,8.7),.14,'steel','silver')
    tube(m,(8,0,8),[(.95,2.8),(1.3,2.8)],'rubber',16)


def chip(m):
    bevel(m,(3.2,3.0,7.4),(12.8,13,8.6),.35,'pcb','steel','chip_panel')
    # Recessed wind channels, stepped vent lips, and raised flow fins.
    for y in (5.0,7.4,9.8):
        bevel(m,(4.3,y,7.10),(11.7,y+1.4,7.42),.10,'ink','steel')
        m.poly([(4.65,y+.2,7.06),(11.1,y+.2,7.06),(11.6,y+.58,6.75),(5.15,y+.58,6.75)],'silver',both=True)
        m.poly([(5.15,y+.58,6.75),(11.6,y+.58,6.75),(11.3,y+.85,7.06),(4.85,y+.85,7.06)],'teal')
    for y in (4.4,6.5,8.6,10.7):
        for x in (2.3,12.8):bevel(m,(x,y,7.6),(x+.9,y+.7,8.4),.10,'gold','silver')
    for x in (3.95,12.05):
        for y in (3.8,12.2):tube(m,(x,y,0),[(7.12,.17),(7.42,.2)],'silver',8,'z')


def flask(m,charges):
    # Metal exoskeleton surrounds a translucent glass vessel with a real water volume.
    bevel(m,(4,1.2,5.55),(12,2.3,10.45),.35,'steel','silver')
    bevel(m,(4.4,10.4,5.8),(11.6,11.5,10.2),.30,'metal','silver')
    for x in (4.15,11.2):
        for z in (5.65,9.85):bevel(m,(x,2.1,z),(x+.65,10.8,z+.5),.1,'steel','silver')
    tube(m,(8,0,8),[(11.4,1.15),(13.05,1.15)],'glass',12)
    tube(m,(8,0,8),[(12.9,1.5),(13.2,1.7),(14.3,1.7),(14.5,1.4)],'gold',12)
    for i in range(12):
        a=i*math.pi/6;x=8+math.cos(a)*1.67;z=8+math.sin(a)*1.67
        m.box((x-.06,13.3,z-.06),(x+.06,14.2,z+.06),'steel')
    if charges:
        h=2.5+charges*1.84
        # Keep each liquid side as one plane. Triangle-fan water caps interleave
        # with a glass fan in vanilla's translucent quad sorting and leave X seams.
        m.box((4.98,2.45,6.30),(11.02,h,9.70),'water','water_top')
    bevel(m,(4.7,2.3,6.05),(11.3,10.65,9.95),.50,'glass','glass')
    # Raised ruler sits on a front rail; water remains visible between the rails.
    bevel(m,(10.9,2.3,5.62),(11.48,10.5,5.94),.06,'metal','silver','flask_ruler')
    for y in (3.1,4.9,6.7,8.5):m.box((10.35,y,5.57),(11.25,y+.10,5.67),'white')
    bevel(m,(11.65,10.0,7.1),(12.6,12.35,8.9),.2,'rubber','metal')


def buoy(m):
    tube(m,(8,0,8),[(2,2.8),(2.5,3.8),(4.6,4.0),(5.3,3.6),(7.3,1.9)],'buoy_wrap',16,wrap=True,cap_material='white')
    tube(m,(8,0,8),[(1.7,2.7),(2.1,3.0)],'steel',16)
    tube(m,(8,0,8),[(7.2,.46),(7.7,.46),(7.9,.25),(15.0,.25)],'silver',10)
    tube(m,(8,0,8),[(14.9,.36),(15.4,.28)],'gold',10)
    m.poly([(8.25,14.5,7.85),(12.9,13.5,8.15),(11.0,12.8,7.6),(8.25,12.6,7.85)],'red',both=True)
    m.poly([(8.26,13.7,7.8),(11.8,13.35,7.85),(11.0,13.05,7.65),(8.26,13.2,7.8)],'white',both=True)
    tube(m,(8,0,8),[(.8,.65),(1.8,.42)],'rubber',10)
    for i in range(4):
        a=i*math.pi/2;x=8+math.cos(a)*2.3;z=8+math.sin(a)*2.3
        tube(m,(x,0,z),[(6.2,.2),(6.55,.2)],'gold',8)


def model(name,obj,translucent=False):
    pose=display('remote' if name=='mental_remote' else 'tool')
    if name in ('aero_pressure_chip','origami_paper'):
        for hand in ('firstperson_righthand','firstperson_lefthand'):
            pose[hand]['scale']=[.42]*3;pose[hand]['translation']=[-.5,6,0]
    return {'loader':'neoforge:obj','model':'academy:models/item/refined/'+obj+'.obj',
        'automatic_culling':False,'shade_quads':True,'flip_v':True,'emissive_ambient':False,
        'gui_light':'front','ambientocclusion':False,
        'render_type':'minecraft:translucent' if translucent else 'minecraft:cutout',
        'textures':{'particle':'academy:items/'+name},'display':pose}


def flask_model(obj):
    # Render the liquid and metal in an opaque pass first. Keeping them in the
    # glass pass allows transparent faces to write depth ahead of the liquid.
    solid=model('water_flask',obj)
    glass=model('water_flask','water_flask_glass',True)
    pose=solid.pop('display');glass.pop('display')
    return {'loader':'neoforge:composite','gui_light':'front','ambientocclusion':False,
        'textures':{'particle':'academy:items/water_flask'},'display':pose,
        'children':{'opaque':solid,'glass':glass},'item_render_order':['opaque','glass']}


def build_refined_assets():
    textures();OBJ.mkdir(parents=True,exist_ok=True);MODELS.mkdir(parents=True,exist_ok=True)
    builders={'mental_remote':remote,'dark_matter_crystal':crystal,'nitrogen_canister':canister,
              'aero_pressure_chip':chip,'buoyancy_float':buoy,'origami_paper':crane}
    meshes={}
    for name,build in builders.items():
        m=Mesh();build(m);meshes[name]=m
    for charges in range(5):
        m=Mesh();flask(m,charges)
        opaque=Mesh();opaque.faces=[face for face in m.faces if face[1]!='glass']
        meshes['water_flask' if charges==4 else 'water_flask_'+str(charges)]=opaque
        if charges==0:
            glass=Mesh();glass.faces=[face for face in m.faces if face[1]=='glass']
            meshes['water_flask_glass']=glass
    mats=sorted({mat for mesh in meshes.values() for _,mat,_,_ in mesh.faces})
    mtl=['# Original ACA procedural surface materials.']
    for mat in mats:mtl+=['newmtl '+mat,'Ka 0 0 0','Kd 1 1 1','map_Kd academy:items/refined/'+mat,'']
    (OBJ/'canon_aux_refined.mtl').write_text('\n'.join(mtl))
    for name,m in meshes.items():m.export(OBJ/(name+'.obj'))
    models={name:model(name,name) for name in builders}
    models['water_flask_body']=flask_model('water_flask')
    models['water_flask']={'parent':'academy:item/water_flask_body','overrides':[
        {'predicate':{'minecraft:damage':i/4},'model':'academy:item/water_flask_'+str(4-i)} for i in range(1,5)]}
    for charges in range(4):models['water_flask_'+str(charges)]=flask_model('water_flask_'+str(charges))
    for name,data in models.items():(MODELS/(name+'.json')).write_text(json.dumps(data,indent=2)+'\n')
    return {'models':len(models),'meshes':{name:len(m.faces) for name,m in meshes.items()},'materials':len(mats)}
