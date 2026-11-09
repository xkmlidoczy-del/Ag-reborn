#!/usr/bin/env python3
"""Build catalog-bound, code-native skill glyphs and merge authored skill text."""
from pathlib import Path
import json,math,re,hashlib
from PIL import Image,ImageDraw
ROOT=Path(__file__).resolve().parents[1]
ASSETS=ROOT/'src/main/resources/assets/academy'
COLORS={'mental_out':(244,145,204),'aero_hand':(126,238,205),'nitrogen_armor':(131,182,255),'dark_matter':(225,217,255),'hydro_hand':(94,208,255),'paper_psychokinesis':(255,224,164),'liquid_shadow':(166,173,198),'float_dial':(173,231,182)}
def authored():
    return json.loads((ROOT/'docs/canon/skills.json').read_text())

def glyph(skill):
    cat,id=skill['category'],skill['id'];c=COLORS[cat];img=Image.new('RGBA',(128,128));d=ImageDraw.Draw(img)
    d.ellipse((6,6,121,121),outline=c,width=4)
    d.arc((11,11,116,116),12,275,fill=(*c,150),width=2)
    line=lambda pts,w=5:d.line(pts,fill=c,width=w,joint='curve')
    if cat=='paper_psychokinesis':
        d.polygon([(40,30),(78,24),(94,83),(49,96)],outline=c,width=4);line([(40,30),(51,87),(94,83)],3)
        if any(x in id for x in ('wing','array','walkway')):
            for x in (26,44,62):d.polygon([(x,57),(x+24,37),(x+28,78)],outline=c,width=3)
    elif cat=='mental_out':
        d.ellipse((31,26,94,85),outline=c,width=4);line([(45,88),(46,102),(76,102)],4)
        for x in (44,60,76):d.arc((x-9,40,x+13,66),130,410,fill=c,width=3)
        d.arc((17,15,109,100),235,310,fill=c,width=3)
    elif cat=='hydro_hand':
        d.polygon([(63,23),(36,60),(35,82),(49,98),(76,98),(94,81),(91,62)],outline=c,width=5)
        d.arc((44,59,85,91),12,154,fill=c,width=4)
    elif cat=='liquid_shadow':
        d.ellipse((42,26,86,64),outline=c,width=4);d.polygon([(42,66),(87,66),(99,96),(29,96)],outline=c,width=4)
        for y in (75,85):line([(42,y),(85,y)],3)
    elif cat=='nitrogen_armor':
        d.polygon([(64,22),(96,39),(90,82),(64,104),(38,82),(32,39)],outline=c,width=5)
        line([(50,80),(50,46),(77,80),(77,46)],4)
    elif cat=='dark_matter':
        d.polygon([(63,24),(78,53),(104,78),(73,71),(64,102),(54,71),(24,78),(49,51)],outline=c,width=4)
        line([(63,24),(64,102)],3)
    elif cat=='aero_hand':
        for y,w in ((40,57),(61,73),(82,51)):
            line([(24,y),(24+w,y)],4);d.arc((24+w-22,y-20,24+w+6,y+6),265,460,fill=c,width=4)
    else:
        d.ellipse((29,27,97,48),outline=c,width=4);d.ellipse((29,80,97,101),outline=c,width=4)
        line([(64,83),(64,40)],5);line([(49,56),(64,40),(79,56)],4)
    # Functional marks are drawn in a separate lower badge: aim, area, motion, guard, medium.
    group=next((g for g,keys in [('guard',('armor','shield','dome','screen','ward','shell','deflect','guard')),('motion',('lift','jet','step','wing','rescue','dash','current','launch','turn','brake')),('area',('array','field','domain','crowd','ring','cone','barrage','squall')),('medium',('item','transfer','patch','walkway','retract','irrigate','extinguish','pause','dismiss'))] if any(k in id for k in keys)),'aim')
    d.rounded_rectangle((79,81,117,116),radius=7,fill=(13,23,35,255),outline=c,width=3)
    if group=='motion':line([(87,106),(108,89)],3);line([(96,89),(108,89),(108,101)],3)
    elif group=='guard':d.polygon([(89,88),(108,88),(106,104),(98,111),(91,104)],outline=c,width=3)
    elif group=='area':d.ellipse((87,88,110,110),outline=c,width=3);d.ellipse((95,96,102,103),fill=c)
    elif group=='medium':d.rectangle((88,89,109,109),outline=c,width=3);line([(88,99),(109,99)],2)
    else:line([(88,99),(109,99)],3);line([(99,88),(99,110)],3)
    out=img.resize((32,32),Image.Resampling.LANCZOS)
    marks=ImageDraw.Draw(out)
    # Five visible branch ticks identify each of the category's 24 authored techniques.
    for bit in range(5):
        color=(*c,255) if skill['_branch'] & (1<<bit) else (*c,60)
        marks.line((4,8+bit*3,6,8+bit*3),fill=color,width=1)
    if skill['level']>=4:marks.line((10,2,21,2),fill=(255,205,104,255),width=1)
    if skill['level']>=5:marks.line((10,30,21,30),fill=(255,205,104,255),width=1)
    # Native icon atlas requires a fully transparent one-pixel outer frame.
    for i in range(32):
        out.putpixel((0,i),(0,0,0,0));out.putpixel((31,i),(0,0,0,0));out.putpixel((i,0),(0,0,0,0));out.putpixel((i,31),(0,0,0,0))
    return out

skills=authored();locales={loc:json.loads((ASSETS/'lang'/f'{loc}.json').read_text()) for loc in ('zh_cn','zh_tw','en_us','ja_jp')}
by_id={x['id']:x for x in skills}
def localized(skill,loc):
    return skill[{'zh_cn':'zh','zh_tw':'zh_tw','ja_jp':'ja','en_us':'en'}[loc]]
for cat in COLORS:
    for branch,skill in enumerate(sorted((s for s in skills if s['category']==cat),key=lambda s:s['id']),1):skill['_branch']=branch
for s in skills:
    for loc,data in locales.items():
        data[f"ac.ability.{s['category']}.{s['id']}.name"]=localized(s,loc)['name']
for s in skills:
    parents=s.get('prerequisites',s.get('prerequisite',''))
    parents=parents if isinstance(parents,list) else [parents] if parents else []
    for loc,data in locales.items():
        text=localized(s,loc);names=[localized(by_id[p],loc)['name'] for p in parents]
        prerequisite={
            'en_us':('Requires: '+', '.join(names)+'. ') if names else 'Starting skill. ',
            'zh_cn':('前置：'+'、'.join(names)+'。') if names else '起始技能。',
            'zh_tw':('前置：'+'、'.join(names)+'。') if names else '起始技能。',
            'ja_jp':('前提：'+'・'.join(names)+'。') if names else '初期スキル。'}[loc]
        controls={'en_us':'Activate your ability and use its mapped key. ', 'zh_cn':'激活能力后使用当前预设按键。','zh_tw':'啟用能力後使用目前預設按鍵。','ja_jp':'能力を有効にし、割り当てたキーで使用する。'}[loc]
        data[f"ac.ability.{s['category']}.{s['id']}.desc"]='LV'+str(s['level'])+('. ' if loc=='en_us' else '。')+prerequisite+controls+text['description']
    for directory in ('skill_icons_v2','abilities'):
        folder=ASSETS/'textures'/directory/s['category'];folder.mkdir(parents=True,exist_ok=True)
        glyph(s).save(folder/(s['id']+'.png'))
for loc,data in locales.items():(ASSETS/'lang'/f'{loc}.json').write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n')
manifest_path=ROOT/'src/main/resources/skill-icons-v2.json'
manifest=json.loads(manifest_path.read_text());manifest['icons']=[x for x in manifest['icons'] if x['category'] not in COLORS]
for s in skills:
    path=ASSETS/'textures/skill_icons_v2'/s['category']/(s['id']+'.png')
    manifest['icons'].append({'category':s['category'],'id':s['id'],'level':s['level'],'controllable':True,
        'name':s['zh']['name'],'meaning':s['en']['description']+' Category silhouette, function badge, visible branch ticks and exact level marks.',
        'texture':'academy:textures/skill_icons_v2/'+s['category']+'/'+s['id']+'.png','sha256':hashlib.sha256(path.read_bytes()).hexdigest(),
        'original':True,'shape':s['id'],'generator':'scripts/build-canon-expansion-assets.py'})
manifest_path.write_text(json.dumps(manifest,ensure_ascii=False,indent=2)+'\n')
print('Authored entries merged:',len(skills),'; full icon manifest:',len(manifest['icons']))
