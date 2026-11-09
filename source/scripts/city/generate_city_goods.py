#!/usr/bin/env python3
"""Build the city catalogue's 32px item art, models, and translations.

The catalogue JSON is the single source for the product names, shop, tier,
price, silhouette, and palette. Art uses the project's existing integer-grid
pixel-art approach; no external textures or fonts are required.
"""
from pathlib import Path
import hashlib
import json
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / 'src/main/resources/assets/academy'
CATALOG = ASSETS / 'city/catalog.json'
GOODS = json.loads(CATALOG.read_text(encoding='utf-8'))['goods']
TEXTURES = ASSETS / 'textures/items/city'
MODELS = ASSETS / 'models/item'
INK = '#2c3440'
CREAM = '#f8edcf'
WHITE = '#f7f5e9'
GREEN = '#6d9d63'


def shade(hexcolor, factor):
    rgb = tuple(int(hexcolor[i:i+2], 16) for i in (0, 2, 4))
    return tuple(min(255, int(c * factor)) for c in rgb) + (255,)


def icon(g):
    im = Image.new('RGBA', (32, 32))
    d = ImageDraw.Draw(im)
    shape = g['shape']
    c = '#' + g['color']
    dark = shade(g['color'], .66)
    light = shade(g['color'], 1.22)
    detail = hashlib.sha256(g['id'].encode()).digest()
    if shape in ('bento', 'fish_bento', 'noodle_box', 'salad'):
        d.polygon([(4,8),(7,5),(26,5),(29,8),(28,25),(25,28),(6,28),(3,25)], fill=INK)
        d.rectangle((5,8,27,25), fill='#eddbc0')
        d.rectangle((6,9,15,24), fill=WHITE)
        d.rectangle((17,9,26,24), fill=c)
        d.line((16,8,16,25), fill='#8a786b', width=1)
        if shape == 'fish_bento':
            d.polygon([(17,14),(21,11),(25,12),(27,15),(25,18),(21,19),(17,17)], fill='#758d98')
            d.line((19,13,24,17), fill=WHITE, width=1)
            d.ellipse((22,11,25,14), fill='#f0dc77')
        elif shape == 'noodle_box':
            for y in (11,14,17,20): d.line((18,y,25,y-1), fill=CREAM, width=1)
            d.rectangle((19,21,24,23), fill='#739f65')
        elif shape == 'salad':
            for x,y in ((19,11),(23,14),(19,18),(24,21)): d.ellipse((x-1,y-1,x+2,y+2), fill='#ebd58b')
            d.rectangle((7,10,14,13), fill='#8ebd78')
        else:
            for i in range(3):
                x = 19 + (detail[i] % 5); y = 10 + i * 4
                d.rectangle((x,y,min(25,x+3),y+2), fill=dark)
            d.rectangle((7,10,9,12), fill=GREEN); d.rectangle((11,20,14,23), fill='#e3b85a')
        d.line((6,26,25,26), fill='#a67d62', width=1)
    elif shape in ('rice_bowl','noodles','soup','dumplings','balls'):
        d.ellipse((4,8,28,19), fill=INK)
        d.ellipse((6,9,26,18), fill=CREAM)
        d.ellipse((8,10,24,16), fill=c)
        d.polygon([(5,15),(27,15),(24,25),(21,28),(11,28),(8,25)], fill=INK)
        d.polygon([(7,17),(25,17),(22,24),(19,26),(12,26),(9,24)], fill='#e5e9e3')
        d.line((10,23,22,23), fill='#77969e', width=2)
        if shape == 'noodles':
            for y in (10,12,14): d.line([(9,y),(13,y+1),(17,y),(22,y+1)], fill='#f6dfa3', width=1)
            d.line((22,5,28,1), fill='#845639', width=2); d.line((23,7,29,3), fill='#b78055', width=1)
        elif shape in ('dumplings','balls'):
            for x in (10,15,20): d.ellipse((x-2,10,x+2,14), fill=light, outline=dark)
        else:
            for x,y in ((10,12),(14,10),(19,13),(22,11)):
                d.rectangle((x,y,x+1,y+1), fill=GREEN if shape=='rice_bowl' else '#fff1b2')
    elif shape in ('bottle','carton','iced_cup','tea_cup','pudding'):
        if shape == 'carton':
            d.polygon([(8,7),(12,3),(22,3),(25,7),(25,27),(8,27)],fill=INK)
            d.polygon([(10,8),(13,5),(21,5),(23,8),(23,25),(10,25)],fill=WHITE)
            d.rectangle((10,14,23,20),fill=c); d.line((12,6,20,6),fill=light)
            d.rectangle((14,15,19,18),fill=WHITE)
        elif shape == 'bottle':
            d.rectangle((12,2,20,6),fill=INK); d.rectangle((13,3,19,5),fill=dark)
            d.polygon([(11,6),(21,6),(24,10),(24,26),(21,29),(11,29),(8,26),(8,10)],fill=INK)
            d.polygon([(11,9),(21,9),(22,11),(22,25),(20,27),(11,27),(10,25),(10,11)],fill=c)
            d.rectangle((10,15,22,21),fill='#e8eddb'); d.line((12,17,20,17),fill=dark,width=2)
            d.line((11,10,11,14),fill=WHITE,width=1)
        else:
            d.polygon([(7,9),(25,9),(23,26),(20,29),(12,29),(9,26)],fill=INK)
            d.polygon([(9,11),(23,11),(21,25),(19,27),(13,27),(11,25)],fill=c)
            d.rectangle((7,7,25,10),fill='#dce4de'); d.line((9,7,23,7),fill=WHITE)
            if shape == 'pudding': d.rectangle((10,10,22,13),fill='#9a5b33')
            else:
                d.line((20,8,24,2),fill='#c65f55',width=2)
                d.rectangle((12,14,15,16),fill=light); d.rectangle((17,19,20,21),fill=light)
    elif shape == 'onigiri':
        d.polygon([(15,3),(18,3),(29,24),(27,28),(5,28),(3,24)],fill=INK)
        d.polygon([(16,6),(26,24),(25,26),(7,26),(6,24)],fill=WHITE)
        d.rectangle((12,19,20,27),fill='#354e48'); d.rectangle((15,12,18,15),fill=c)
    elif shape in ('sandwich','toast','croissant','wrap'):
        if shape == 'sandwich':
            d.polygon([(4,23),(15,5),(28,23),(26,27),(5,27)],fill=INK)
            d.polygon([(7,22),(15,9),(25,22)],fill=WHITE)
            d.line((9,21,23,21),fill=c,width=4); d.line((7,25,25,25),fill='#d3a275',width=2)
        elif shape == 'wrap':
            d.polygon([(7,10),(20,4),(26,9),(17,27),(11,29),(4,24)],fill=INK)
            d.polygon([(8,11),(19,6),(23,10),(16,25),(11,26),(6,23)],fill=CREAM)
            d.line((8,12,20,8),fill=GREEN,width=3); d.line((9,15,20,11),fill=c,width=3)
        elif shape == 'croissant':
            d.arc((4,4,28,28),10,170,fill=INK,width=9); d.arc((6,6,26,26),10,170,fill=c,width=6)
            d.line((10,10,12,17),fill=light,width=2); d.line((20,10,18,17),fill=light,width=2)
            d.line((10,21,21,21),fill='#e88c86',width=2)
        else:
            d.polygon([(6,5),(25,5),(28,9),(28,25),(25,28),(6,28),(3,25),(3,9)],fill=INK)
            d.rectangle((6,8,25,25),fill='#d6a56c'); d.rectangle((8,10,23,23),fill=c)
            for x,y in ((11,13),(18,14),(13,20),(21,20)): d.ellipse((x,y,x+2,y+2),fill=light)
    elif shape in ('skewer','corn','dango'):
        d.line((5,28,27,4),fill=INK,width=4); d.line((5,28,27,4),fill='#d5ad78',width=2)
        for x,y in ((10,22),(15,17),(20,12)):
            if shape == 'corn': d.ellipse((x-4,y-4,x+4,y+4),fill=c,outline=dark,width=2)
            elif shape == 'dango': d.ellipse((x-4,y-4,x+4,y+4),fill=c,outline=INK,width=1)
            else: d.polygon([(x-4,y),(x-1,y-4),(x+4,y-2),(x+3,y+2),(x,y+4)],fill=c,outline=INK)
            d.rectangle((x-2,y-2,x,y-1),fill=light)
    elif shape in ('cake','roll','mont_blanc','waffle','wind_cookie','fish_cake','cracker','biscuit','berries'):
        if shape == 'cake':
            d.polygon([(5,25),(9,12),(22,8),(27,25)],fill=INK); d.polygon([(8,23),(11,14),(21,11),(24,23)],fill=CREAM)
            d.line((9,19,23,19),fill=c,width=3); d.ellipse((17,7,22,12),fill=c)
        elif shape == 'roll':
            d.rounded_rectangle((5,8,27,24),radius=5,fill=INK); d.rounded_rectangle((7,10,25,22),radius=4,fill=c)
            d.ellipse((17,11,24,21),fill=CREAM); d.arc((19,13,23,19),0,300,fill=dark,width=2)
        elif shape == 'mont_blanc':
            d.rectangle((7,24,25,28),fill=INK); d.polygon([(7,24),(10,18),(12,14),(15,9),(18,7),(22,14),(25,24)],fill=INK)
            for y,w in ((13,3),(16,6),(19,8),(22,9)): d.line((16-w,y,16+w,y),fill=c,width=3)
            d.ellipse((13,5,19,10),fill=light)
        elif shape == 'fish_cake':
            d.polygon([(4,16),(9,10),(20,9),(25,12),(29,9),(27,16),(29,23),(24,20),(19,23),(9,22)],fill=INK)
            d.polygon([(6,16),(10,12),(20,11),(24,14),(27,12),(25,16),(27,20),(23,18),(19,21),(10,20)],fill=c)
            d.point((10,14),fill=INK); d.arc((13,13,20,20),270,90,fill=dark,width=1)
        elif shape == 'berries':
            for x,y in ((10,12),(19,10),(14,20),(22,21)): d.rectangle((x-4,y-4,x+4,y+4),fill=INK); d.rectangle((x-3,y-3,x+3,y+3),fill=c); d.point((x-1,y-2),fill=WHITE)
        else:
            d.rounded_rectangle((5,5,27,27),radius=4,fill=INK); d.rounded_rectangle((7,7,25,25),radius=3,fill=c)
            if shape == 'waffle':
                for n in (11,16,21): d.line((9,n,23,n),fill=dark,width=2); d.line((n,9,n,23),fill=dark,width=2)
            elif shape == 'wind_cookie':
                for a,b in ((13,8),(18,12),(14,18),(9,14)): d.rectangle((a,b,a+3,b+4),fill=light)
                d.rectangle((14,14,18,18),fill=dark)
            elif shape == 'biscuit': d.rectangle((7,14,25,18),fill=CREAM); d.line((9,10,23,10),fill=dark,width=2)
            else: d.rectangle((10,12,22,21),fill='#42614e'); d.line((9,9,22,9),fill=light)
    elif shape == 'packet':
        d.polygon([(7,4),(25,4),(27,8),(25,28),(7,28),(5,8)],fill=INK)
        d.polygon([(8,6),(24,6),(25,9),(23,26),(9,26),(7,9)],fill=c)
        d.rectangle((10,12,22,21),fill=CREAM); d.ellipse((13,14,19,19),fill=dark)
        d.line((9,8,23,8),fill=light)
    elif shape in ('book','leaflet','card','postcard','luggage_tag'):
        d.polygon([(6,5),(23,3),(27,7),(27,25),(9,28),(5,25)],fill=INK)
        d.polygon([(8,7),(23,5),(25,8),(25,23),(9,26),(7,24)],fill=c)
        d.line((10,9,10,23),fill=light,width=2); d.rectangle((13,10,22,18),fill=CREAM)
        d.line((14,13,21,13),fill=dark); d.line((14,16,19,16),fill=dark)
        if shape == 'luggage_tag': d.ellipse((18,5,22,9),fill=INK); d.line((20,6,27,1),fill='#c8c8c1',width=2)
        if shape == 'postcard': d.polygon([(14,19),(18,13),(22,19)],fill='#76a7b5')
    elif shape == 'badge':
        d.ellipse((5,4,27,26),fill=INK); d.ellipse((7,6,25,24),fill=c); d.ellipse((10,9,22,21),fill=CREAM)
        d.polygon([(16,10),(18,14),(22,15),(19,18),(20,22),(16,20),(12,22),(13,18),(10,15),(14,14)],fill='#e1bd67')
        d.polygon([(10,24),(12,30),(16,26),(20,30),(22,24)],fill=dark)
    elif shape == 'wind_charm':
        d.line((16,9,16,28),fill=INK,width=4); d.line((16,10,16,27),fill=c,width=2)
        d.ellipse((13,12,19,18),fill=INK); d.ellipse((15,14,17,16),fill=WHITE)
        for p in ([(15,13),(12,4),(15,3),(17,13)],[(18,14),(27,11),(28,14),(18,17)],[(16,18),(11,26),(9,23),(14,16)]): d.polygon(p,fill=CREAM,outline=INK)
    elif shape == 'train':
        d.polygon([(4,22),(8,12),(24,10),(28,14),(28,24),(24,27),(8,27)],fill=INK)
        d.polygon([(6,21),(10,14),(23,12),(26,15),(26,23),(23,25),(8,25)],fill=WHITE)
        d.rectangle((10,15,23,19),fill=c); d.line((7,22,25,22),fill='#568db0',width=2)
        d.rectangle((9,25,12,28),fill=INK); d.rectangle((22,25,25,28),fill=INK)
    else:
        raise ValueError('Unknown icon shape: ' + shape)
    return im


def write_image(id, image):
    TEXTURES.mkdir(parents=True, exist_ok=True)
    MODELS.mkdir(parents=True, exist_ok=True)
    image.save(TEXTURES / (id + '.png'), optimize=True)
    (MODELS / (id + '.json')).write_text(json.dumps({'parent':'minecraft:item/generated',
        'textures':{'layer0':'academy:items/city/' + id}}, indent=2) + '\n', encoding='utf-8')


def write_locales():
    common = {
        'academy.city.npc.mentor': ('能力导师','Ability Mentor'),
        'academy.city.npc.librarian': ('图书管理员','Librarian'),
        'academy.city.npc.exchange': ('中央站服务员','Central Station Attendant'),
        'academy.city.npc.canteen': ('校园食堂店员','Campus Canteen Clerk'),
        'academy.city.npc.food_street': ('餐饮街店员','Food Street Clerk'),
        'academy.city.npc.west': ('西亭店员','West Kiosk Clerk'),
        'academy.city.npc.east': ('咖啡亭店员','Cafe Kiosk Clerk'),
        'academy.city.npc.airport': ('航站柜台店员','Airport Counter Clerk'),
        'academy.city.message.mentor_start': ('课程归档：请到校园图书馆找管理员，完成后可得 5 学园币。','Course Archive: meet the librarian for 5 City Coins.'),
        'academy.city.message.mentor_guide': ('能力开发：右键高级开发机，未觉醒时在界面输入 learn 并回车；学习技能需满足前置。机台消耗 IF，由屋顶风机补能。','Use an advanced developer. To awaken, enter learn in its console. Skill prerequisites still apply. Rooftop turbines supply IF.'),
        'academy.city.message.library_wait': ('这里可以查阅资料。课程归档请先找第二校舍实训室导师。','Find the training-room mentor before handing in Course Archive.'),
        'academy.city.message.course_done': ('课程归档已完成，获得 5 学园币。','Course Archive completed: 5 City Coins.'),
        'academy.city.message.course_already': ('课程档案已归档；欢迎查阅资料。','The course records are filed. Welcome to the library.'),
        'academy.city.message.stock_start': ('商亭补货单：请交给 D15 西亭店员，完成后可得 7 学园币。','Kiosk Restock: visit the D15 west kiosk clerk for 7 City Coins.'),
        'academy.city.message.stock_done': ('商亭补货单已交付，获得 7 学园币。','Kiosk Restock completed: 7 City Coins.'),
        'academy.city.message.closed': ('现在是休息时间，请白天再来。','The counter is closed. Please return during opening hours.'),
        'academy.city.message.busy': ('店员正在接待其他顾客，请稍候。','The attendant is serving someone else; please wait.'),
        'academy.city.guide.transfer_leaflet.1': ('城市换乘\n\n东西向动车：西端—西城—中央—东城—东端。中央站可换乘南北线与地下线路。','City transfers\n\nEast–west express: West End, West City, Central, East City and East End. Transfer at Central.'),
        'academy.city.guide.transfer_leaflet.2': ('机场线连接机场区域；环线和三条地下线连接其他站点。输入 /aca_city map 查看具体路线。','The airport line, city ring and three underground lines serve additional stops. Use /aca_city map for the route.'),
        'academy.city.guide.ability_development_guide.1': ('能力开发\n\n右键开发机，未觉醒时在界面输入 learn 并回车。诱导因子可选择能力系；学习技能需满足等级、前置和经验。','Ability development\n\nUse a developer. Enter learn to awaken. An induction factor selects a category; skills require their level and prerequisites.'),
        'academy.city.guide.ability_development_guide.2': ('D7 第二校舍一层的高级开发机可免费使用，开发会消耗机内 IF。屋顶风机经中层无线节点为机台补能。','The four D7 advanced developers are free to use. Development consumes stored IF; rooftop wind generators recharge them through the wireless node.'),
        'academy.city.guide.if_wiring_card.1': ('虚能接线\n\n发电机储存 IF，无线节点连接发电机与接收设备。节点有连接数量、距离与传输带宽限制。','Imaginary Flux\n\nGenerators store IF. A wireless node connects generators to receivers within its range, capacity and bandwidth.'),
        'academy.city.guide.if_wiring_card.2': ('在设备的无线页面选择附近节点并连接。D7 实训室节点位于中层；机台与屋顶风机已预先接入。','Open a device’s wireless page and connect a nearby node. The D7 lab node is on the middle floor; its machines and turbines are preconnected.')
    }
    for lang, index in [('zh_cn',0),('en_us',1)]:
        path = ASSETS / 'lang' / (lang + '.json')
        values = json.loads(path.read_text(encoding='utf-8'))
        values['item.academy.city_coin'] = ('学园币','City Coin')[index]
        for good in GOODS:
            if good.get('kind') != 'supply': values['item.academy.' + good['id']] = good[['zh','en'][index]]
        for key, text in common.items(): values[key] = text[index]
        path.write_text(json.dumps(values, ensure_ascii=False, indent=2) + '\n', encoding='utf-8')


def main():
    food = [g for g in GOODS if 'tier' in g]
    assert len(food) == 50 and all(sum(g['shop'] == shop for g in food) == 10 for shop in ('canteen','food_street','west','east','airport'))
    assert len({g['id'] for g in GOODS}) == len(GOODS)
    for good in GOODS:
        if good.get('kind') != 'supply': write_image(good['id'], icon(good))
    coin = Image.new('RGBA', (32,32)); d = ImageDraw.Draw(coin)
    d.ellipse((4,4,27,27),fill=INK); d.ellipse((6,6,25,25),fill='#e2b65a'); d.ellipse((9,9,22,22),fill='#f7d985')
    d.polygon([(16,9),(19,14),(23,15),(19,19),(20,23),(16,21),(12,23),(13,19),(9,15),(13,14)],fill='#4388aa')
    write_image('city_coin', coin)
    write_locales()
    # Keep the authored GUI sprite and the physical voxel model in sync with the catalogue.
    from refine_city_food import main as refine_food
    refine_food()
    from food_realism import main as finish_food
    finish_food()
    print('Generated', len(food), 'foods,', len(GOODS)-len(food)-3, 'city goods and one currency icon.')


if __name__ == '__main__':
    main()
