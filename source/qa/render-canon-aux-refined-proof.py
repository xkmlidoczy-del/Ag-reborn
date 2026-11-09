#!/usr/bin/env python3
"""Arrange crops of native screenshots; never paint over or replace model pixels."""
import argparse
import hashlib
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

p=argparse.ArgumentParser(description=__doc__)
p.add_argument('gallery',type=Path)
p.add_argument('output',type=Path)
a=p.parse_args()
result=json.loads((a.gallery/'result.json').read_text())
assert result['pass'] and result['screenshots']==42
screens=a.gallery/'game/screenshots'
a.output.mkdir(parents=True,exist_ok=True)
font='/System/Library/Fonts/STHeiti Medium.ttc'
title=ImageFont.truetype(font,40)
label=ImageFont.truetype(font,28)
small=ImageFont.truetype(font,20)
bg='#121c29';white='#e9eef2';muted='#a9becf'
rows=[
 ('心理遥控器','倒角外壳 · 星徽与独立按钮','aux_first_mental_remote.png'),
 ('未元物质结晶','八棱切面 · 独立折射碎片','aux_first_dark_matter_crystal.png'),
 ('氮气罐','圆形罐体 · 阀门与压力表','aux_first_nitrogen_canister.png'),
 ('气压片','立体风道 · 电路与金色触点','aux_first_aero_pressure_chip.png'),
 ('水之瓶 / 满瓶','透明玻璃 · 独立水体与液面','aux_flask_damage_0.png'),
 ('浮力浮标','圆弧浮体 · 折面旗帜','aux_first_buoyancy_float.png'),
 ('折纸','立体折翼 · 细颈与尖尾','aux_first_origami_paper.png'),
 ('水之瓶 / 空瓶','五档水量共用玻璃与金属框','aux_flask_damage_4.png'),
]
crop=(820,260,1280,680)
canvas=Image.new('RGB',(1940,1170),bg);draw=ImageDraw.Draw(canvas)
draw.text((20,20),'ACA 辅助道具 · 精细模型实机',font=title,fill=white)
draw.text((22,78),'Minecraft 1.21.1 / NeoForge 21.1.252  ·  原生第一人称截图局部',font=small,fill=muted)
proof=[]
for i,(name,detail,file) in enumerate(rows):
    x=20+(i%4)*480;y=126+(i//4)*508
    source=screens/file
    im=Image.open(source).convert('RGB')
    assert im.size==(1280,720)
    # Copy exactly 460x420 pixels, without rescaling, retouching, or removing holes.
    canvas.paste(im.crop(crop),(x,y))
    draw.text((x,y+430),name,font=label,fill=white)
    draw.text((x,y+467),detail,font=small,fill=muted)
    proof.append({'source':str(source.resolve()),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'crop':crop,'rescaled':False})
canvas.save(a.output/'refined-items-native.png')

water=Image.new('RGB',(2420,610),bg);d=ImageDraw.Draw(water)
d.text((20,18),'水之瓶 · 五档原生水量',font=title,fill=white)
for damage in range(5):
    source=screens/f'aux_flask_damage_{damage}.png'
    im=Image.open(source).convert('RGB');x=20+damage*480
    water.paste(im.crop(crop),(x,100))
    d.text((x,535),f'剩余水量 {4-damage} / 4',font=label,fill=white)
    proof.append({'source':str(source.resolve()),'sha256':hashlib.sha256(source.read_bytes()).hexdigest(),'crop':crop,'rescaled':False})
water.save(a.output/'flask-five-native-states.png')
(a.output/'native-image-provenance.json').write_text(json.dumps({'gallery':str(a.gallery.resolve()),'method':'Native screenshot crops copied at 1:1; labels and layout only; no model pixels painted or synthesized.','images':proof},ensure_ascii=False,indent=2)+'\n')
print(a.output.resolve())
