#!/usr/bin/env python3
"""Generate revised city food sprites and small voxel models from the catalogue.

GUI keeps the authored transparent icon; physical display contexts use the
corresponding cuboid model. Both are generated from the same food record.
"""
from pathlib import Path
import hashlib, json, random
from PIL import Image, ImageDraw
import generate_city_goods as base

ASSETS = base.ASSETS
TEX = ASSETS / 'textures/items/city'
MODEL = ASSETS / 'models/item/city_food_3d'
PALETTE = TEX / 'physical'
FOODS = [g for g in base.GOODS if g.get('tier')]


def rgb(s): return tuple(int(s[i:i+2],16) for i in (0,2,4))
def tint(c, factor): return tuple(max(0,min(255,round(x*factor))) for x in c)


def refined_icon(g):
    # The 32-pixel original is built in memory, then re-authored at 64 pixels;
    # no pre-existing files are used as input.
    im=base.icon(g).resize((64,64), Image.Resampling.NEAREST)
    pixels=im.load(); rng=random.Random(int.from_bytes(hashlib.sha256(g['id'].encode()).digest()[:8],'big'))
    primary=rgb(g['color']); shapes=g['shape']; candidates=[]
    for y in range(6,59):
        for x in range(6,58):
            r,green,b,a=pixels[x,y]
            if a and (r,green,b)==primary: candidates.append((x,y))
    # Restrained light and ingredient flecks on the food body, not the outline.
    for _ in range(min(34,len(candidates)//4)):
        if not candidates: break
        x,y=rng.choice(candidates)
        color=tint(primary, 1.13 if rng.random()<.58 else .85)
        pixels[x,y]=(*color,255)
        if x+1<64 and pixels[x+1,y][:3]==primary and rng.random()<.7: pixels[x+1,y]=(*color,255)
    d=ImageDraw.Draw(im)
    if shapes in ('bento','fish_bento','noodle_box','salad'):
        for x,y in ((14,21),(20,25),(12,32),(24,36),(16,43),(25,47)):
            d.line((x,y,x+2,y),fill='#d9d4be')
        d.line((13,18,27,18),fill='#ffffff',width=1)
        if g['id'] in ('curry_chicken_rice','teriyaki_chicken_bento','black_pepper_beef_box'):
            for x,y in ((40,22),(45,28),(40,35),(45,41)):
                d.line((x,y,x+5,y-1),fill='#efca8a',width=1)
        if shapes=='fish_bento':
            d.line((39,27,48,33),fill='#dce8e4',width=1); d.line((42,25,50,31),fill='#a1b7b4',width=1)
    elif shapes in ('rice_bowl','noodles','soup','dumplings','balls'):
        for x,y in ((20,24),(27,21),(34,24),(41,21)): d.point((x,y),fill='#fbebbb')
        d.line((22,43,43,43),fill='#b8c9c4',width=1)
        if shapes=='noodles':
            d.line((21,25,27,26,33,24,39,25,44,24),fill='#fff0be',width=1)
        elif shapes=='balls':
            d.line((20,24,41,26),fill='#6d4430',width=1)
    elif shapes in ('bottle','carton','iced_cup','tea_cup','pudding'):
        if shapes in ('bottle','carton'):
            d.line((24,34,38,34),fill=tint(primary,.65),width=1)
            d.line((26,37,35,37),fill=tint(primary,.78),width=1)
            d.line((23,21,23,27),fill='#ffffff',width=1)
        else:
            d.line((24,25,24,36),fill=tint(primary,1.35),width=1)
            d.line((43,26,42,39),fill=tint(primary,.78),width=1)
    elif shapes in ('cake','roll','mont_blanc','waffle','wind_cookie','fish_cake','cracker','biscuit','berries'):
        d.point((24,30),fill='#fff5da');d.point((31,34),fill=tint(primary,1.2))
    elif shapes in ('onigiri','sandwich','toast','croissant','wrap'):
        for x,y in ((25,35),(30,38),(34,41),(22,44)): d.point((x,y),fill='#fff4d4')
    elif shapes in ('skewer','corn','dango'):
        for x,y in ((18,42),(28,32),(38,22)): d.line((x,y,x+3,y-2),fill='#f8dca6',width=1)
    # Distinctive ingredients remain recognizable at the small inventory scale.
    ident=g['id']
    if ident=='curry_chicken_rice':
        d.polygon([(35,24),(39,20),(51,21),(52,39),(48,43),(36,41)],fill='#b77735')
        for x,y in ((38,25),(42,23),(46,25),(39,31),(45,30),(48,35),(39,37)):
            d.line((x,y,x+3,y-1),fill='#efc476',width=1)
        d.rectangle((14,20,18,23),fill='#6b9c5c');d.rectangle((22,42,27,46),fill='#e3ab52')
    elif ident=='teriyaki_chicken_bento':
        d.polygon([(36,23),(42,20),(51,23),(51,40),(46,43),(36,39)],fill='#713923')
        for y in (25,30,35,39): d.line((38,y,49,y-1),fill='#d38b4e',width=2)
        d.rectangle((14,20,19,23),fill='#6d9c60')
    elif ident=='black_pepper_beef_box':
        for x,y in ((37,22),(44,23),(38,30),(46,31),(39,38),(46,39)):
            d.rectangle((x,y,x+5,y+4),fill='#74432d');d.point((x+2,y+1),fill='#342b27')
        d.rectangle((13,21,19,24),fill='#719554')
    elif ident in ('salted_mackerel_bento','lemon_grilled_fish_rice'):
        d.line((37,29,48,35),fill='#e4f1eb',width=2)
        d.line((39,26,50,32),fill='#91abb3',width=1)
        if ident=='lemon_grilled_fish_rice': d.ellipse((44,20,51,27),fill='#f6d963',outline='#d6ad43')
    elif ident=='oyakodon':
        d.ellipse((19,21,25,25),fill='#f6db71');d.ellipse((28,22,34,26),fill='#d69d68')
        d.ellipse((38,20,44,25),fill='#f6db71');d.point((32,20),fill='#5e9c55')
    elif ident=='mushroom_beef_rice':
        d.ellipse((19,20,27,25),fill='#d5b188');d.ellipse((34,21,42,26),fill='#d5b188')
        d.rectangle((22,23,24,27),fill='#f1d6ac');d.rectangle((37,24,39,27),fill='#f1d6ac')
    elif ident=='vegetable_tofu_rice':
        for x,y in ((20,21),(30,23),(39,20)): d.rectangle((x,y,x+5,y+4),fill='#f5e8cb')
        d.line((24,25,28,22),fill='#4d8c52',width=2);d.point((37,26),fill='#d88256')
    elif ident in ('soy_sauce_ramen','beef_yakisoba','tomato_egg_udon','herb_chicken_pasta'):
        if ident=='soy_sauce_ramen':
            d.rectangle((19,18,23,25),fill='#36554a');d.ellipse((35,20,42,27),fill='#f4ead0')
            d.ellipse((37,22,40,25),fill='#e9b952');d.line((26,26,32,26),fill='#d56d6e',width=1)
        elif ident=='beef_yakisoba':
            for x,y in ((20,20),(27,23),(36,20),(42,24)):d.rectangle((x,y,x+5,y+2),fill='#704432')
            for x,y in ((25,20),(34,25),(42,21)):d.point((x,y),fill='#80a963')
        elif ident=='tomato_egg_udon':
            for x,y in ((20,20),(35,22)):d.rectangle((x,y,x+5,y+4),fill='#d34f42')
            for x,y in ((28,20),(39,25)):d.rectangle((x,y,x+4,y+3),fill='#f2d167')
        else:
            for x,y in ((20,21),(31,20),(40,23)):d.rectangle((x,y,x+4,y+3),fill='#d9b283')
            for x,y in ((26,20),(36,25),(43,20)):d.point((x,y),fill='#599355')
    elif ident=='cream_corn_soup':
        for x,y in ((20,21),(25,24),(32,21),(38,24),(43,21)):d.rectangle((x,y,x+2,y+2),fill='#f2d65c')
    elif ident=='takoyaki':
        for x,y in ((19,23),(29,22),(39,23)):d.line((x,y,x+5,y+2),fill='#5f3c2c',width=1)
        d.point((26,21),fill='#f7e5b5');d.point((38,20),fill='#6c9e58')
    return im


def make_palette(g):
    c=rgb(g['color'])
    return [c,tint(c,.68),tint(c,1.18),(242,234,209),(105,148,94),(221,180,94),
            (121,79,51),(190,80,70),(71,87,94),(250,244,224),(220,225,215),(63,79,75),
            (238,202,109),(151,182,183),(252,248,232),(54,62,71)]


def atlas(g):
    im=Image.new('RGBA',(16,16));d=ImageDraw.Draw(im)
    for i,c in enumerate(make_palette(g)):
        x=(i%4)*4;y=(i//4)*4
        d.rectangle((x,y,x+3,y+3),fill=(*c,255))
        d.point((x,y),fill=(*tint(c,1.08),255)); d.point((x+3,y+3),fill=(*tint(c,.91),255))
    return im


def model(g):
    shape=g['shape']; ident=g['id']; seed=int.from_bytes(hashlib.sha256(ident.encode()).digest()[:4],'big')
    rng=random.Random(seed); elements=[]
    def box(x1,y1,z1,x2,y2,z2,swatch,rotation=None):
        u=(swatch%4)*4;v=(swatch//4)*4
        el={'from':[x1,y1,z1],'to':[x2,y2,z2],
            'faces':{f:{'texture':'#atlas','uv':[u,v,u+4,v+4]} for f in ('down','up','north','south','west','east')}}
        if rotation: el['rotation']=rotation
        elements.append(el)
    def topping(x,y,z,col=4,w=1.3): box(x,y,z,x+w,y+.45,z+w,col)
    def bowl():
        box(4,2,4,12,3,12,8);box(3,3,3,13,5,13,10);box(2,5,2,14,7,14,10)
        box(2,7,2,14,7.5,14,8);box(3,7.5,3,13,8,13,0)
        for x,z in ((4,4),(7,4),(10,5),(5,8),(9,9),(11,11)):
            topping(x,8,z,4 if rng.random()<.43 else 9,1.2)
    def tray():
        box(1.5,2,2,14.5,3,14,15);box(2,3,2.5,14,4,13.5,3)
        box(2.5,4,3,7,4.7,13,9);box(7.5,4,3,13.5,4.7,13,0)
        box(7,4,3,7.5,5,13,8)
        for x,z in ((3,4),(5,6),(4,9),(6,11)): topping(x,4.7,z,9,.9)
        if shape=='fish_bento':
            box(8,4.7,5,13,5.7,10,13);box(9,5.7,6,12,6,9,10);topping(11,6,4,12,1.5)
        elif shape=='salad':
            for x,z in ((8,4),(10,5),(12,8),(9,10),(11,11)):topping(x,4.7,z,4 if rng.random()<.5 else 5,1.4)
        elif shape=='noodle_box':
            for z in (4,6,8,10):box(8,4.7,z,13,5,z+.7,3)
        else:
            for z in (4,6.5,9):box(8.5,4.7,z,12.5,5.4,z+1.3,6)
            topping(3,4.7,3,4,1.5);topping(5,4.7,11,12,1)
    def bottle():
        box(5,2,5,11,11,11,0);box(5.4,11,5.4,10.6,12,10.6,0)
        box(6,12,6,10,13.5,10,0);box(6,13.5,6,10,15,10,1)
        box(4.9,5,5.9,5.1,9,10.1,9);box(10.9,5,5.9,11.1,9,10.1,9)
        box(5.9,5,4.9,10.1,9,5.1,9);box(6.5,7,4.7,9.5,8,4.9,1)
    def cup():
        box(5,2,5,11,3,11,8);box(4.5,3,4.5,11.5,10,11.5,0)
        box(4,10,4,12,11,12,10);box(5,11,5,11,11.5,11,9)
        if shape!='pudding':box(9.5,11,6,10,15,6.5,7)
        else:box(5,11.5,5,11,12,11,6)
    if shape in ('bento','fish_bento','noodle_box','salad'): tray()
    elif shape in ('rice_bowl','noodles','soup','dumplings','balls'):
        bowl()
        if shape=='noodles':
            for z in (4,6,8,10):box(4,8.1,z,12,8.4,z+.55,3)
            box(10,8.7,3,10.5,14,3.5,6,{'origin':[10,9,3],'axis':'z','angle':22.5})
        elif shape in ('dumplings','balls'):
            for x,z in ((4,4),(7,4),(10,4),(5,8),(9,8)):box(x,8,z,x+2,9.5,z+2,5)
    elif shape=='bottle':bottle()
    elif shape=='carton':
        box(4,2,4,12,12,12,9);box(4,12,4,12,14,12,10);box(5,14,5,11,15,11,10)
        box(3.9,5,5,4.1,9,11,0);box(11.9,5,5,12.1,9,11,0);box(5,5,3.9,11,9,4.1,0)
    elif shape in ('iced_cup','tea_cup','pudding'):cup()
    elif shape=='onigiri':
        box(3,2,6,13,5,10,9);box(4,5,6,12,8,10,9);box(5,8,6,11,11,10,9);box(7,11,6,9,13,10,9)
        box(5,2,5.7,11,5.5,10.3,11);box(7,8,5.7,9,9,5.95,0)
    elif shape in ('sandwich','wrap','croissant'):
        if shape=='sandwich':
            for start,end,width in ((2,4,11),(4,6,9),(6,8,7),(8,10,5),(10,12,3)):
                box(8-width/2,start,5,8+width/2,end,11,3)
            box(4,3,4.8,12,4,5.1,0);box(5,5,4.8,11,6,5.1,4)
        elif shape=='wrap':
            box(3,4,5,13,10,11,3);box(4,10,5.2,12,11,10.8,4);box(5,11,6,11,11.5,10,0)
        else:
            box(3,3,5,13,6,11,5);box(4,6,5,12,8,11,0);box(5,8,5,11,9,11,5)
            box(5,5,4.8,11,6,5.1,7)
    elif shape in ('skewer','corn','dango'):
        box(7.5,1,7.5,8.5,15,8.5,6)
        for y in (4,7,10):box(5,y,5,11,y+2.2,11,0 if y!=7 else 5)
        for y in (4.7,7.7,10.7):box(5.5,y,4.8,10.5,y+.3,5,1)
    elif shape in ('toast','waffle','wind_cookie','cracker','biscuit'):
        box(3,3,4,13,5,12,6);box(3.5,5,4.5,12.5,6,11.5,0)
        if shape=='waffle':
            for x in (5,8,11):box(x,6,5,x+.45,6.4,11,1)
            for z in (6,9):box(4,6,z,12,6.4,z+.45,1)
        elif shape=='biscuit':box(3,4.2,3.9,13,5,4.1,9)
        else:
            for x,z in ((5,6),(9,6),(6,9),(10,9)):topping(x,6,z,5,.8)
    elif shape in ('cake','mont_blanc'):
        box(3,2,3,13,3,13,10);box(4,3,4,12,5,12,3);box(4,5,4,12,6,12,0);box(4,6,4,12,8,12,9)
        if shape=='cake':box(7,8,7,9,10,9,7)
        else:
            box(5,8,5,11,9,11,0);box(6,9,6,10,10,10,0);box(7,10,7,9,11,9,0)
    elif shape=='roll':
        box(3,3,4,13,9,12,0);box(3,4,5,3.4,8,11,9);box(2.8,5,6,3,7,10,4)
    elif shape=='fish_cake':
        box(3,4,5,11,8,11,0);box(11,5,6,14,7,10,0);box(1,5,4,4,7,12,1)
        box(4,7,4.9,5,7.7,5.1,15)
    elif shape=='berries':
        for x,y,z in ((3,2,3),(8,2,4),(5,6,7),(10,5,9)):box(x,y,z,x+3,y+3,z+3,0)
    elif shape=='packet':
        box(4,2,5,12,13,11,0);box(4,12,5,12,14,11,1);box(3.9,6,6,4.1,10,10,3);box(11.9,6,6,12.1,10,10,3)
    else: raise ValueError('Unimplemented 3D food shape: '+shape)
    return {'textures':{'atlas':'academy:items/city/physical/'+ident,'particle':'academy:items/city/physical/'+ident},
            'display':{'gui':{'rotation':[30,225,0],'scale':[.75,.75,.75]},
             'ground':{'translation':[0,2,0],'scale':[.5,.5,.5]},
             'fixed':{'rotation':[0,180,0],'scale':[.7,.7,.7]},
             'thirdperson_righthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.45,.45,.45]},
             'thirdperson_lefthand':{'rotation':[75,45,0],'translation':[0,2.5,0],'scale':[.45,.45,.45]},
             'firstperson_righthand':{'rotation':[0,45,0],'translation':[0,3,1],'scale':[.52,.52,.52]},
             'firstperson_lefthand':{'rotation':[0,225,0],'translation':[0,3,1],'scale':[.52,.52,.52]}},'elements':elements}


def main():
    MODEL.mkdir(parents=True,exist_ok=True);PALETTE.mkdir(parents=True,exist_ok=True)
    for g in FOODS:
        refined_icon(g).save(TEX/(g['id']+'.png'),optimize=True)
        atlas(g).save(PALETTE/(g['id']+'.png'),optimize=True)
        (MODEL/(g['id']+'.json')).write_text(json.dumps(model(g),ensure_ascii=False,separators=(',',':'))+'\n',encoding='utf-8')
    print('Created',len(FOODS),'revised 64px food icons, palette textures, and 3D hand/ground models')

if __name__=='__main__': main()
