#!/usr/bin/env python3
"""Generate detailed, Minecraft-compatible food models and matching isometric icons.

All food geometry and textures are derived from the city catalogue; the art
reference is not used as an input image. The old item IDs and food values remain.
"""
from pathlib import Path
import hashlib, json, math, random
from PIL import Image, ImageDraw
import refine_city_food as old

ASSETS=old.ASSETS
FOODS=old.FOODS
TEXTURES=old.TEX
MODELS=old.MODEL
PALETTES=old.PALETTE


def clamp(v): return max(0,min(255,int(v)))
def col(c,delta=0): return tuple(clamp(v+delta) for v in c)


def material_atlas(g):
    """Sixteen detailed 32px materials, referenced by the original model UVs."""
    rng=random.Random(int.from_bytes(hashlib.sha256(('material/'+g['id']).encode()).digest()[:8],'big'))
    colors=old.make_palette(g);out=Image.new('RGBA',(128,128))
    for n,color in enumerate(colors):
        tile=Image.new('RGBA',(32,32));px=tile.load()
        # Coherent surface variation rather than one solid colored square.
        for y in range(32):
            for x in range(32):
                mottled=math.sin((x+y)*.43)*2+math.sin((x-y)*.26)*2
                noise=rng.randint(-5,5)+mottled
                px[x,y]=(*col(color,noise),255)
        d=ImageDraw.Draw(tile)
        if n==9 and g['shape'] in ('bento','fish_bento','rice_bowl','onigiri','salad'):
            for _ in range(110):
                x,y=rng.randrange(32),rng.randrange(32);h=rng.choice((-9,9,15))
                d.line((x,y,x+rng.choice((1,2)),y-1),fill=(*col(color,h),255),width=1)
        elif n in (0,1,5,6) and g['shape'] in ('bento','rice_bowl','noodles','skewer','corn','dango','toast'):
            for _ in range(75):
                x,y=rng.randrange(32),rng.randrange(32);h=rng.choice((-22,-12,11,18))
                d.rectangle((x,y,x+rng.randrange(1,3),y+rng.randrange(1,3)),fill=(*col(color,h),255))
            if n in (5,6):
                for y in (5,13,22,29): d.line((0,y,31,y-3),fill=(*col(color,-22),255),width=1)
        elif n==4:
            for _ in range(55):
                x,y=rng.randrange(32),rng.randrange(32);d.line((x,y,x+2,y-1),fill=(*col(color,rng.choice((-25,18))),255),width=1)
        elif n==11:
            for _ in range(80):
                x,y=rng.randrange(32),rng.randrange(32);d.point((x,y),fill=(*col(color,rng.choice((-15,15))),255))
        elif n==13:
            for y in range(2,32,5):d.line((0,y,31,y-3),fill=(*col(color,15),255),width=1)
        elif n in (3,10,14):
            d.line((0,2,31,2),fill=(*col(color,8),255),width=1)
            d.line((0,30,31,30),fill=(*col(color,-8),255),width=1)
        elif n==7:
            for _ in range(36):
                x,y=rng.randrange(32),rng.randrange(32);d.point((x,y),fill=(*col(color,rng.choice((25,-20))),255))
        out.paste(tile,((n%4)*32,(n//4)*32))
    return out


def detailed_model(g):
    result=old.model(g);shape=g['shape'];ident=g['id'];rng=random.Random(int.from_bytes(hashlib.sha256(('model/'+ident).encode()).digest()[:8],'big'))
    elements=result['elements']
    def box(x1,y1,z1,x2,y2,z2,material,rotation=None):
        u=(material%4)*4;v=(material//4)*4
        el={'from':[round(x1,3),round(y1,3),round(z1,3)],'to':[round(x2,3),round(y2,3),round(z2,3)],
            'faces':{side:{'texture':'#atlas','uv':[u,v,u+4,v+4]} for side in ('down','up','north','south','west','east')}}
        if rotation:el['rotation']=rotation
        elements.append(el)
    def grain_field(x0,x1,z0,z1,y,count):
        for _ in range(count):
            x=rng.uniform(x0,x1);z=rng.uniform(z0,z1);w=rng.choice((.26,.34,.42))
            box(x,y,z,min(x+w,x1+.25),y+rng.choice((.12,.20,.28)),z+.26,9)
    def garnish(x,z,y,material=4):
        box(x,y,z,x+.7,y+.3,z+.7,material)
    if shape in ('bento','fish_bento','noodle_box','salad'):
        elements.clear()
        # Raised compartment edges, rice, main dish and individual ingredients.
        box(1,1.6,1,15,2.4,15,15);box(1,2.4,1,15,3.5,1.8,15);box(1,2.4,14.2,15,3.5,15,15)
        box(1,2.4,1.8,1.8,3.5,14.2,15);box(14.2,2.4,1.8,15,3.5,14.2,15)
        box(1.8,2.4,1.8,14.2,2.9,14.2,3);box(2,2.9,2.2,7.2,4,13.8,9)
        box(7.7,2.9,2.2,14,3.8,13.8,0);box(7.2,2.9,2.2,7.7,4.1,13.8,15)
        grain_field(2.2,6.7,2.5,13.2,4,34)
        if shape=='fish_bento':
            box(8.2,3.8,4,13.5,4.6,11.7,13);box(8.8,4.6,4.8,13,4.95,11,13)
            for z in (5.2,6.6,8,9.4,10.8): box(9.1,4.96,z,12.8,5.08,z+.22,10)
            box(11.4,4.0,2.7,13.4,4.3,4,12)
        elif shape=='noodle_box':
            for i in range(9):
                z=3+i*1.1;box(8,3.82,z,13.6,4.12,z+.32,3)
        elif shape=='salad':
            for _ in range(20):garnish(rng.uniform(8,13),rng.uniform(2.7,13),3.8,4 if rng.random()<.5 else 5)
        elif ident=='curry_chicken_rice':
            for i in range(4):
                z=4+i*1.9;box(8.5,3.82,z,13.2,4.8,z+1.25,5);box(9.0,4.8,z+.18,12.7,4.98,z+1.06,6)
            box(8.2,3.8,2.8,9.2,4.3,3.7,12);box(12.4,3.8,12,13.4,4.3,13,7)
        else:
            for i in range(4):
                z=3.5+i*2.2;box(8.2,3.8,z,13.2,4.8,z+1.4,6);box(8.5,4.8,z+.1,12.9,4.92,z+.35,2)
        for x,z in ((2.6,2.6),(4.2,2.7),(3.3,4.2)):garnish(x,z,4.3,4)
        garnish(5.6,12,4.1,7)
    elif shape in ('rice_bowl','noodles','soup','dumplings','balls'):
        elements.clear()
        # Layered octagonal bowl, dark inner rim, liquid, and separately modeled toppings.
        box(5,1.2,5,11,2,11,8);box(4,2,3.5,12,3.2,12.5,10);box(3,3.2,3,13,4.6,13,10)
        box(2.3,4.6,3,13.7,7,13,10);box(3,4.6,2.3,13,7,13.7,10)
        box(2.2,7,3,13.8,7.6,13,8);box(3,7,2.2,13,7.6,13.8,8);box(3.2,7.6,3.2,12.8,7.9,12.8,0)
        if shape=='noodles':
            # Short offset pieces suggest irregular strands, not a flat striped square.
            for row in range(7):
                z=3.8+row*1.15;start=3.7+rng.uniform(0,.8)
                for x in (start,start+2.5,start+5):box(x,7.92,z,min(12.5,x+2),8.16,z+.31,3)
            # Egg white/yolk, pork slices, seaweed and vegetables vary by dish.
            if ident=='soy_sauce_ramen':
                box(3.8,8.2,4,6.3,8.5,6.5,9);box(4.5,8.5,4.7,5.7,8.67,5.9,12)
                box(8.2,8.2,8.5,12,8.65,10,6);box(8.3,8.65,8.8,11.8,8.78,9.05,3)
                box(3.4,8.0,10.2,5.4,10.2,10.5,11)
            elif ident=='beef_yakisoba':
                for x,z in ((4,4),(8,4),(5.5,8),(9.5,9)):box(x,8.2,z,x+2,8.65,z+1.1,6)
            elif ident=='tomato_egg_udon':
                for x,z in ((4,4),(9,5),(6,9)):box(x,8.2,z,x+1.3,8.65,z+1.3,7)
                box(8,8.2,8,10,8.6,10,12)
            else:
                for x,z in ((4,4),(9,5),(6,9)):box(x,8.2,z,x+1.5,8.65,z+1,6)
            for x,z in ((6.3,6.5),(7,7.1),(7.6,6.3),(8,7.3)):garnish(x,z,8.2,4)
            box(10,8.7,2.8,10.35,14,3.15,6,{'origin':[10,9,3],'axis':'z','angle':22.5})
        elif shape in ('dumplings','balls'):
            for x,z in ((4,4),(6.7,3.8),(9.4,4),(4.8,7.6),(8.5,7.6)):
                box(x,8,z,x+2,9,z+1.8,5);box(x+.3,9,z+.3,x+1.7,9.3,z+1.5,2)
            for x,z in ((5,10.2),(7,10),(9,10.3)):garnish(x,z,8.1,4)
        else:
            grain_field(3.7,12,3.7,12,8,28)
            for x,z in ((4,5),(7,4),(10,5),(5,9),(9,9)):
                box(x,8,z,x+1.4,8.5,z+1.3,12 if ident=='oyakodon' else 4 if shape=='soup' else 6)
                garnish(x+1.1,z+.3,8.5,4)
    elif shape=='onigiri':
        # Stepped silhouette with a real nori wrap and textured rice.
        elements.clear()
        for i in range(10):
            y=2+i; half=5.5-i*.43
            box(8-half,y,5.4,8+half,y+1.05,10.6,9)
        box(5.1,2,5.1,10.9,7.1,10.9,11)
        box(7,8.5,10.62,9,9.2,10.83,7 if ident=='ume_onigiri' else 13)
        grain_field(3.3,12.3,5.5,10.1,4,16)
    elif shape in ('cake','mont_blanc'):
        elements.clear();box(2.5,1.5,2.5,13.5,2,13.5,10)
        # A triangular slice, built from short wedges, with visible sponge and cream layers.
        for segment in range(6):
            z1=2.7+segment*1.55;z2=z1+1.6;half=1.1+segment*.72;x1=8-half;x2=8+half
            box(x1,2,z1,x2,3.4,z2,5);box(x1,3.4,z1,x2,4,z2,9)
            box(x1,4,z1,x2,5.4,z2,5);box(x1,5.4,z1,x2,6.2,z2,9)
        if shape=='cake':
            box(6.2,4.0,10.65,9.8,4.5,11.1,7)
            for x,z in ((7,7),(8,7),(7,8),(8,8)):box(x,6.2,z,x+.9,7.4,z+.9,7)
            for x,z in ((7,7),(8,8)):garnish(x,z,7.4,4)
        else:
            box(5,6.2,5,11,7.2,11,0);box(6,7.2,6,10,8.2,10,0);box(7,8.2,7,9,9.2,9,0)
    elif shape=='carton':
        elements.clear();box(4,1.2,4,12,11.5,12,14);box(4,11.5,4,12,13,12,10)
        for y,x1,x2 in ((13,4.5,11.5),(13.6,5.2,10.8),(14.2,6,10),(14.8,6.8,9.2)):
            box(x1,y,4.5,x2,y+.6,11.5,14)
        box(3.95,5,5,4.05,9.5,11,0);box(11.95,5,5,12.05,9.5,11,0)
        box(5,5,3.95,11,9.5,4.05,0);box(6,6,3.85,10,8.5,3.95,9)
    elif shape=='sandwich':
        elements.clear()
        for i in range(9):
            y=2+i; half=5.4-i*.48
            box(8-half,y,5,8+half,y+1.05,11,3)
        # Yellow egg filling is visible on both cut faces.
        box(4.7,3,4.8,11.3,4,5.05,12);box(5.4,5,4.8,10.6,5.8,5.05,12)
        box(4.7,3,10.95,11.3,4,11.2,12);box(5.4,5,10.95,10.6,5.8,11.2,12)
    elif shape in ('skewer','corn','dango'):
        elements.clear();box(7.65,.7,7.65,8.35,15,8.35,6)
        for i,y in enumerate((3.0,6.3,9.6)):
            material=0 if i!=1 else 5
            box(5.8,y,5.8,10.2,y+2.5,10.2,material)
            box(6.25,y+2.5,6.25,9.75,y+2.8,9.75,2)
            if shape=='corn':
                for x in (6.1,7.2,8.3,9.4):
                    for yy in (y+.5,y+1.3,y+2.1):box(x,yy,5.7,x+.65,yy+.55,5.85,12)
            elif shape=='skewer':
                for yy in (y+.5,y+1.4):box(6.2,yy,5.7,9.8,yy+.22,5.85,1)
    elif shape=='fish_cake':
        elements.clear()
        box(4,4,5.2,11.5,8,10.8,0);box(5,3.6,6,10.5,8.4,10,0)
        box(11.5,5,6.4,14,7,9.6,0);box(2.1,4.5,5.8,4.2,7.5,10.2,1)
        box(1,5,4.7,2.2,7,11.3,0);box(5.4,7.3,5.05,6.1,8,5.25,15)
        box(8,4.1,5.0,8.3,7.6,5.2,1)
    elif shape=='roll':
        elements.clear()
        box(3,3.8,5.2,13,9.2,10.8,0);box(3,4.7,4.4,13,8.3,11.6,0)
        box(3,3,6,13,10,10,0)
        # Exposed cream and green spiral on both cut ends.
        for x in (2.8,13):
            box(x,4.4,5.8,x+.25,8.6,10.2,9);box(x-.03,5.1,6.5,x+.28,7.9,9.5,0)
            box(x-.06,5.8,7.1,x+.31,7.2,8.9,9);box(x-.09,6.1,7.5,x+.34,6.8,8.3,0)
    elif shape=='wrap':
        elements.clear()
        box(3,3.5,5,13,9.5,11,3);box(4,3,5.4,12,10,10.6,3)
        # The open end shows lettuce, chicken and tomato inside the folded tortilla.
        box(12.9,4.1,5.5,13.15,9.1,10.5,4)
        box(13.15,5,6.1,13.4,8,9.9,6);box(13.4,6,6.7,13.6,7,8.3,7)
        box(4,9.6,5.1,12,9.85,10.9,9)
    elif shape=='croissant':
        elements.clear()
        # Staggered segments and toasted ridges form a crescent around the filling.
        for x1,x2,y1,y2,z1,z2 in ((2,4,3,5,6,10),(3,5.5,4,7,5,11),(5,8,5,8,4.5,11.5),
                                    (8,11,5,8,4.5,11.5),(10.5,13,4,7,5,11),(12,14,3,5,6,10)):
            box(x1,y1,z1,x2,y2,z2,5);box(x1+.15,y2,z1+.3,x2-.15,y2+.25,z2-.3,2)
        box(4,4.6,4.75,12,5.3,5.05,7);box(4.5,4.2,4.7,11.5,4.6,5.1,12)
    elif shape=='packet':
        elements.clear();box(4,2,5,12,12.5,11,0)
        box(4,12.5,5,12,13.5,11,1);box(4,1.5,5,12,2,11,1)
        box(11.95,5,5.8,12.1,10.7,10.2,3);box(5,5,4.9,11,10.7,5.1,3)
        box(12.1,6.1,7,12.2,9.1,9.5,6 if ident=='dried_fruit_nuts' else 12)
    else:
        # Refine smaller items without changing their recognizable silhouette.
        if shape in ('bottle','iced_cup','tea_cup','pudding'):
            box(5.2,10.8,5.2,10.8,11,10.8,14)
            if shape=='bottle':box(6.1,13.5,6.1,9.9,14,9.9,14)
            elif shape=='pudding':
                for x,z in ((6,6),(8,7),(7,9)):box(x,11.45,z,x+1,11.65,z+1,6)
            else:
                for x,z in ((5.8,5.8),(8,6.2),(7,8.5)):box(x,11.5,z,x+1.2,11.9,z+1.2,10)
                box(5.5,4,4.4,5.9,9.6,4.6,14)
        elif shape in ('waffle','toast','wind_cookie','cracker','biscuit'):
            for x,z in ((5,5),(7,7),(10,5),(9,10)):garnish(x,z,6.05,2)
        elif shape=='berries':
            for x,y,z in ((3.5,5.1,3.5),(8.5,5.1,4.5),(5.5,9.1,7.5),(10.5,8.1,9.5)):
                box(x,y,z,x+.45,y+.15,z+.45,14)
    return result


def render_icon(g,model,atlas):
    """Project the same cuboids to a clean, transparent isometric inventory sprite."""
    size=128; im=Image.new('RGBA',(size,size));draw=ImageDraw.Draw(im)
    # Camera looks down the (+x,+y,+z) diagonal. Sort individual faces by depth.
    def proj(p):
        x,y,z=p;return (64+(x-z)*3.0,91-y*3.0+(x+z-16)*1.5)
    def turn(p,rot):
        if not rot:return p
        x,y,z=p;ox,oy,oz=rot['origin'];a=math.radians(rot['angle']);c=math.cos(a);s=math.sin(a)
        if rot['axis']=='z':return (ox+(x-ox)*c-(y-oy)*s,oy+(x-ox)*s+(y-oy)*c,z)
        if rot['axis']=='y':return (ox+(x-ox)*c+(z-oz)*s,y,oz-(x-ox)*s+(z-oz)*c)
        return (x,oy+(y-oy)*c-(z-oz)*s,oz+(y-oy)*s+(z-oz)*c)
    faces=[]
    for element in model['elements']:
        x1,y1,z1=element['from'];x2,y2,z2=element['to'];rot=element.get('rotation')
        raw=[('up',[(x1,y2,z1),(x2,y2,z1),(x2,y2,z2),(x1,y2,z2)],1.08),
             ('south',[(x1,y1,z2),(x2,y1,z2),(x2,y2,z2),(x1,y2,z2)],.82),
             ('east',[(x2,y1,z1),(x2,y1,z2),(x2,y2,z2),(x2,y2,z1)],.70)]
        for direction,points,light in raw:
            points=[turn(p,rot) for p in points]
            depth=sum(sum(p) for p in points)/4
            uv=element['faces'][direction]['uv'];u=int(uv[0]/4);v=int(uv[1]/4)
            color=atlas.getpixel((u*32+16,v*32+16))[:3]
            faces.append((depth,points,light,color,direction,u,v))
    for _,points,light,color,direction,u,v in sorted(faces,key=lambda f:f[0]):
        polygon=[proj(p) for p in points];draw.polygon(polygon,fill=(*tuple(clamp(c*light) for c in color),255))
        # Subdivide upper planes so rice, meat and sauce retain texture at icon size.
        if direction=='up':
            p0,p1,p2,p3=points
            dx=sum((p1[i]-p0[i])**2 for i in range(3))**.5;dz=sum((p3[i]-p0[i])**2 for i in range(3))**.5
            nx=min(8,max(1,int(dx)));nz=min(8,max(1,int(dz)))
            if nx*nz>=4:
                def interp(a,b):return tuple(p0[i]+(p1[i]-p0[i])*a+(p3[i]-p0[i])*b for i in range(3))
                for ix in range(nx):
                    for iz in range(nz):
                        sample=atlas.getpixel((u*32+min(31,int((ix+.5)*32/nx)),v*32+min(31,int((iz+.5)*32/nz))))[:3]
                        corners=[interp(ix/nx,iz/nz),interp((ix+1)/nx,iz/nz),interp((ix+1)/nx,(iz+1)/nz),interp(ix/nx,(iz+1)/nz)]
                        draw.polygon([proj(p) for p in corners],fill=(*tuple(clamp(c*light) for c in sample),255))
    # Inventory slots are only 16 pixels on screen: remove empty projection
    # margins so both wide bentos and tall bottles remain recognizable.
    bounds=im.getbbox()
    if bounds is None:return im
    content=im.crop(bounds)
    factor=min(112/content.width,112/content.height)
    scaled=content.resize((max(1,round(content.width*factor)),max(1,round(content.height*factor))),Image.Resampling.NEAREST)
    fitted=Image.new('RGBA',(128,128))
    fitted.alpha_composite(scaled,((128-scaled.width)//2,(128-scaled.height)//2))
    return fitted


def main():
    for g in FOODS:
        texture=material_atlas(g);model=detailed_model(g)
        texture.save(PALETTES/(g['id']+'.png'),optimize=True)
        (MODELS/(g['id']+'.json')).write_text(json.dumps(model,ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
        render_icon(g,model,texture).save(TEXTURES/(g['id']+'.png'),optimize=True)
    print('Generated detailed textures, 3D models and isometric icons for',len(FOODS),'foods.')

if __name__=='__main__':main()
