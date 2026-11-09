"""Original ACA auxiliary-item pixel art and vanilla element models, authored 2026-10-29.

All silhouettes, palettes, material tiles and cuboids are drawn here from integer-grid
instructions. No imported artwork, AI raster assets, blur or antialiasing is used.
32px output follows the existing coin/needle asset scale. PNG previews may be enlarged
with nearest-neighbour sampling only. Model face UVs use Minecraft's logical 0..16.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[1] / 'src/main/resources/assets/academy'
TEX = ASSETS / 'textures/items'
MODELS = ASSETS / 'models/item'
INK = '#273441'
METAL = '#8399a8'
LIGHT = '#d6e4e8'
WHITE = '#f2f4e5'
GOLD = '#e3b95c'
TEAL = '#70c9bd'
BLUE = '#4199d1'


def canvas():
    image = Image.new('RGBA', (32, 32))
    return image, ImageDraw.Draw(image)


def finish(image, name):
    TEX.mkdir(parents=True, exist_ok=True)
    image.save(TEX / (name + '.png'), optimize=True)


def mental_remote():
    im, d = canvas()
    d.polygon([(10, 2), (21, 2), (23, 4), (23, 28), (21, 30), (10, 30), (8, 28), (8, 4)], fill=INK)
    d.rectangle((10, 3, 21, 28), fill='#475261')
    d.line((10, 4, 10, 26), fill='#73818d')
    d.rectangle((12, 4, 20, 7), fill='#1a242e')
    d.line((13, 5, 18, 5), fill='#9ca9ab')
    d.rectangle((19, 5, 20, 6), fill=GOLD)
    d.rectangle((11, 10, 21, 20), fill='#25323e')
    d.polygon([(16, 10), (18, 13), (21, 13), (19, 16), (20, 19), (16, 17), (12, 19), (13, 16), (11, 13), (15, 13)], fill='#8b6534')
    d.polygon([(16, 10), (17, 14), (21, 14), (18, 16), (19, 19), (16, 17), (13, 19), (14, 16), (11, 14), (15, 14)], fill=GOLD)
    d.line((16, 12, 16, 15), fill='#fff0ac')
    for x, c in [(11, '#a7d8cf'), (15, '#eacb85'), (19, '#d9acc0')]:
        d.rectangle((x, 22, x + 2, 24), fill='#202b35')
        d.line((x, 22, x + 1, 22), fill=c)
    d.line((13, 27, 19, 27), fill='#202a33')
    finish(im, 'mental_remote')


def dark_matter_crystal():
    im, d = canvas()
    d.polygon([(16, 1), (25, 12), (22, 22), (16, 30), (9, 22), (6, 12)], fill='#485475')
    d.polygon([(16, 3), (23, 12), (16, 28), (8, 12)], fill='#c5c8e4')
    d.polygon([(16, 3), (16, 14), (8, 12)], fill='#f5f4eb')
    d.polygon([(16, 3), (23, 12), (16, 14)], fill='#e0e7ee')
    d.polygon([(8, 12), (16, 14), (16, 28), (11, 21)], fill='#a5afd3')
    d.polygon([(16, 14), (23, 12), (20, 22), (16, 28)], fill='#d7c7e4')
    d.line([(16, 4), (16, 14), (16, 26)], fill=WHITE)
    d.line([(9, 13), (12, 21), (15, 27)], fill='#82c6cf')
    d.line([(23, 13), (21, 20), (18, 25)], fill='#d99dbb')
    for x, y in [(5, 8), (26, 20)]:
        d.line((x - 1, y, x + 1, y), fill='#cbdcdb')
        d.point((x, y - 1), fill=WHITE)
    finish(im, 'dark_matter_crystal')


FONT = {'N': ['101', '111', '111', '111', '101'], '2': ['110', '001', '010', '100', '111']}


def nitrogen_canister():
    im, d = canvas()
    d.polygon([(11, 8), (21, 8), (24, 12), (24, 27), (21, 30), (10, 30), (7, 27), (7, 12)], fill=INK)
    d.rectangle((9, 12, 22, 27), fill=METAL)
    d.rectangle((11, 9, 20, 12), fill='#aabcc6')
    d.line((10, 13, 10, 26), fill='#e0e8e6')
    d.rectangle((21, 13, 22, 26), fill='#617986')
    d.rectangle((9, 16, 22, 23), fill='#2c607d')
    d.line((9, 16, 22, 16), fill='#82c9dc')
    for letter, x in [('N', 12), ('2', 16)]:
        for y, row in enumerate(FONT[letter]):
            for dx, bit in enumerate(row):
                if bit == '1':d.point((x + dx, 18 + y), fill=WHITE)
    d.rectangle((13, 5, 18, 8), fill='#6d8190')
    d.rectangle((11, 2, 21, 4), fill=INK)
    d.line((12, 2, 20, 2), fill=GOLD)
    d.rectangle((15, 3, 17, 6), fill='#b99e64')
    d.rectangle((20, 6, 26, 9), fill=INK)
    d.line((21, 6, 25, 6), fill=LIGHT)
    d.rectangle((24, 7, 26, 8), fill='#c9d9da')
    d.line((11, 28, 20, 28), fill='#5c7481')
    finish(im, 'nitrogen_canister')


def aero_pressure_chip():
    im, d = canvas()
    for y in (8, 13, 18, 23):
        d.rectangle((2, y, 5, y + 1), fill=GOLD)
        d.rectangle((26, y, 29, y + 1), fill=GOLD)
    d.polygon([(8, 3), (23, 3), (27, 7), (27, 25), (24, 28), (7, 28), (4, 25), (4, 7)], fill=INK)
    d.polygon([(8, 5), (22, 5), (25, 8), (25, 24), (23, 26), (8, 26), (6, 24), (6, 8)], fill='#447e78')
    d.line((9, 5, 21, 5), fill='#91c7b5')
    for y, x in [(10, 10), (15, 8), (20, 11)]:
        d.rectangle((x, y, x + 9, y + 1), fill='#253f43')
        d.line((x, y, x + 6, y), fill='#d5e8d8')
        d.line([(x + 6, y - 2), (x + 8, y), (x + 6, y + 2)], fill=WHITE)
    for x, y in [(8, 8), (23, 8), (8, 24), (23, 24)]:d.point((x, y), fill=GOLD)
    finish(im, 'aero_pressure_chip')


def water_flask():
    for charges in range(5):
        im, d = canvas()
        d.polygon([(12, 8), (20, 8), (20, 11), (24, 13), (26, 17), (26, 26), (23, 30), (9, 30), (6, 26), (6, 17), (8, 13), (12, 11)], fill=INK)
        d.polygon([(13, 9), (19, 9), (19, 13), (23, 15), (24, 18), (24, 26), (22, 28), (10, 28), (8, 26), (8, 18), (10, 15), (13, 13)], fill='#738e9c')
        d.rectangle((10, 15, 22, 27), fill='#364f61')
        if charges:
            top = 28 - charges * 3
            d.rectangle((10, top, 22, 27), fill='#2e7fae')
            d.line((10, top, 22, top), fill='#88d4df')
            d.line((11, top + 1, 20, top + 1), fill='#51abca')
        for i in range(4):
            y = 17 + i * 3
            d.line((20, y, 22, y), fill=LIGHT if charges >= 4 - i else '#8d9f9f')
        d.line((8, 18, 8, 25), fill='#c5e0de')
        d.line((10, 13, 12, 12), fill='#d8e9df')
        d.rectangle((11, 4, 21, 7), fill='#5e6662')
        d.rectangle((12, 3, 20, 5), fill='#b7a67a')
        d.line((13, 3, 18, 3), fill='#e4d7a9')
        d.rectangle((22, 8, 24, 11), fill='#73848a')
        d.point((23, 8), fill=LIGHT)
        finish(im, 'water_flask' if charges == 4 else 'water_flask_' + str(charges))


def buoyancy_float():
    im, d = canvas()
    d.rectangle((14, 2, 16, 17), fill=INK)
    d.line((15, 3, 15, 16), fill=LIGHT)
    d.polygon([(17, 3), (25, 5), (17, 8)], fill='#dc985b')
    d.line((17, 3, 22, 4), fill='#f4d294')
    d.polygon([(9, 14), (21, 14), (25, 19), (25, 24), (22, 27), (8, 27), (5, 24), (5, 19)], fill=INK)
    d.polygon([(10, 15), (20, 15), (23, 19), (7, 19)], fill=WHITE)
    d.rectangle((7, 20, 23, 23), fill='#73c8c6')
    d.line((7, 20, 22, 20), fill='#cde9de')
    d.rectangle((9, 24, 21, 25), fill='#488590')
    d.line((11, 28, 11, 29), fill='#8b9fa2')
    d.line((18, 28, 18, 29), fill='#8b9fa2')
    d.line((11, 30, 18, 30), fill=INK)
    finish(im, 'buoyancy_float')


def origami_paper():
    im, d = canvas()
    # Two differently oriented wings, a long folded neck, and a pointed tail.
    d.polygon([(12, 19), (8, 4), (17, 12), (19, 21)], fill='#78858a')
    d.polygon([(12, 18), (10, 7), (16, 13), (17, 20)], fill=WHITE)
    d.line([(10, 8), (14, 15), (16, 19)], fill='#c7d6d0')
    d.polygon([(16, 20), (29, 10), (25, 22), (19, 25)], fill='#74828a')
    d.polygon([(17, 20), (27, 13), (24, 21), (19, 24)], fill='#d6dfd5')
    d.line((18, 21, 25, 15), fill=WHITE)
    d.polygon([(10, 20), (6, 13), (3, 11), (2, 8), (6, 8), (10, 15), (15, 19)], fill=INK)
    d.polygon([(10, 19), (7, 13), (4, 10), (6, 10), (11, 16), (14, 19)], fill='#e6e6d3')
    d.rectangle((3, 9, 5, 10), fill='#cf8f78')
    d.polygon([(12, 18), (19, 21), (20, 25), (11, 25), (9, 22)], fill='#a6b5b3')
    d.polygon([(12, 19), (17, 21), (12, 24), (10, 22)], fill=WHITE)
    d.polygon([(19, 22), (30, 25), (21, 28), (17, 25)], fill='#657781')
    d.polygon([(20, 23), (27, 25), (21, 26), (19, 25)], fill='#d8dfd1')
    finish(im, 'origami_paper')


MATERIALS = {
    'edge': ('#263641', [0, 0, 4, 4]), 'metal': ('#91a7b2', [4, 0, 8, 4]),
    'blue': ('#376f92', [8, 0, 12, 4]), 'gold': ('#c5a366', [12, 0, 16, 4]),
    'white': ('#e4e8dc', [0, 4, 4, 8]), 'crystal': ('#d6daed', [4, 4, 8, 8]),
    'lilac': ('#a6a6ce', [8, 4, 12, 8]), 'teal': ('#68b6b2', [12, 4, 16, 8]),
}


def materials():
    im, d = canvas()
    for name, (color, uv) in MATERIALS.items():
        x, y = int(uv[0] * 2), int(uv[1] * 2)
        d.rectangle((x, y, x + 7, y + 7), fill=color)
        rgb = tuple(int(color[i:i + 2], 16) for i in (1, 3, 5))
        d.line((x, y, x + 7, y), fill=tuple(min(255, a + 18) for a in rgb))
        d.line((x + 7, y + 1, x + 7, y + 7), fill=tuple(max(0, a - 18) for a in rgb))
    # Unused lower rows stay opaque: models only sample the named integer tiles.
    d.rectangle((0, 16, 31, 31), fill=INK)
    finish(im, 'canon_aux_materials')


def cube(lo, hi, material='metal', front=None, rotation=None):
    uv = MATERIALS[material][1]
    faces = {side: {'texture': '#material', 'uv': uv} for side in ('north', 'south', 'east', 'west', 'up', 'down')}
    if front is not None:faces['north'] = {'texture': '#front', 'uv': [v / 2 for v in front]}
    result = {'from': lo, 'to': hi, 'faces': faces}
    if rotation is not None:result['rotation'] = rotation
    return result


def display(kind='tool'):
    return {
        'gui': {'rotation': [12, 195, -12] if kind == 'remote' else [20, 210, -8], 'translation': [0, 0, 0], 'scale': [1.08, 1.08, 1.08]},
        'firstperson_righthand': {'rotation': [0, 155, 12], 'translation': [0, 6, 0], 'scale': [.5, .5, .5]},
        'firstperson_lefthand': {'rotation': [0, -155, -12], 'translation': [0, 6, 0], 'scale': [.5, .5, .5]},
        'thirdperson_righthand': {'rotation': [75, 0, 0], 'translation': [0, 2.5, 1], 'scale': [.55, .55, .55]},
        'thirdperson_lefthand': {'rotation': [75, 0, 0], 'translation': [0, 2.5, 1], 'scale': [.55, .55, .55]},
        'ground': {'rotation': [0, 0, 0], 'translation': [0, 2, 0], 'scale': [.5, .5, .5]},
        'fixed': {'rotation': [0, 180, 0], 'translation': [0, 0, 0], 'scale': [.75, .75, .75]},
    }


def model(name, elements, kind='tool'):
    return {'gui_light': 'front', 'ambientocclusion': False,
            'textures': {'front': 'academy:items/' + name, 'material': 'academy:items/canon_aux_materials', 'particle': 'academy:items/' + name},
            'display': display(kind), 'elements': elements}


def build_models():
    materials()
    MODELS.mkdir(parents=True, exist_ok=True)
    models = {}
    models['mental_remote'] = model('mental_remote', [
        cube([5.1, 1, 7], [10.9, 14.8, 8.7], 'edge', [8, 2, 24, 31]),
        cube([5.4, 1.4, 6.7], [10.6, 14.4, 7], 'metal', [9, 3, 23, 30]),
        cube([6, 13, 8.7], [10, 14, 9], 'edge')], 'remote')
    crystals = []
    for y, height, radius in [(1, 2, .7), (3, 2, 1.4), (5, 2, 2.2), (7, 3, 3), (10, 2, 2.3), (12, 2, 1.5), (14, 1, .7)]:
        c = cube([8 - radius, y, 8 - radius], [8 + radius, y + height, 8 + radius], 'crystal', rotation={'origin': [8, 8, 8], 'axis': 'y', 'angle': 45})
        c['faces']['east']['uv'] = MATERIALS['lilac'][1]
        c['faces']['west']['uv'] = MATERIALS['white'][1]
        crystals.append(c)
    models['dark_matter_crystal'] = model('dark_matter_crystal', crystals)
    models['nitrogen_canister'] = model('nitrogen_canister', [
        cube([4.5, 1, 5.5], [11.5, 11.5, 10.5], 'metal', [7, 10, 25, 30]),
        cube([5.5, 11.5, 6], [10.5, 13, 10], 'metal'),
        cube([7, 13, 7], [9, 14, 9], 'gold'),
        cube([5.8, 14, 7.5], [10.2, 14.8, 8.5], 'edge'),
        cube([10.5, 12, 7], [13.5, 13.2, 8.5], 'metal'),
        cube([5, 5, 5.3], [11, 8.7, 5.5], 'blue', [9, 16, 23, 24])])
    flask = model('water_flask', [
        cube([3.8, 1, 5.5], [12.2, 10.7, 10.5], 'metal', [6, 12, 27, 31]),
        cube([5, 10.7, 6], [11, 12, 10], 'metal', [9, 10, 24, 14]),
        cube([6.5, 12, 6.7], [9.5, 14, 9.3], 'metal'),
        cube([6, 14, 6.2], [10, 15.2, 9.8], 'gold'),
        cube([11, 11.3, 7.3], [12.5, 13, 8.7], 'edge')])
    models['water_flask_body'] = flask
    models['water_flask'] = {'parent': 'academy:item/water_flask_body', 'overrides': [
        {'predicate': {'minecraft:damage': damage / 4}, 'model': 'academy:item/water_flask_' + str(4 - damage)} for damage in range(1, 5)]}
    for charges in range(4):
        models['water_flask_' + str(charges)] = {'parent': 'academy:item/water_flask_body',
            'textures': {'front': 'academy:items/water_flask_' + str(charges), 'particle': 'academy:items/water_flask_' + str(charges)}}
    models['buoyancy_float'] = model('buoyancy_float', [
        cube([4, 2.5, 4.5], [12, 5.5, 11.5], 'teal', [5, 20, 26, 27]),
        cube([5, 5.5, 5.5], [11, 8, 10.5], 'white', [8, 14, 23, 20]),
        cube([7.6, 8, 7.6], [8.4, 15.5, 8.4], 'metal'),
        cube([8.4, 13.2, 7.8], [12, 14.8, 8.2], 'gold'),
        cube([7, 1, 7], [9, 2.5, 9], 'edge')])
    for name in ('aero_pressure_chip', 'origami_paper'):
        pose = display('tool')
        if name == 'aero_pressure_chip':
            # The board is wider than the other held silhouettes. Native first-person
            # review exposed clipped contact pins, so keep the whole board in-frame.
            for hand in ('firstperson_righthand', 'firstperson_lefthand'):
                pose[hand]['scale'] = [.42, .42, .42]
                pose[hand]['translation'] = [-.5, 6, 0]
        models[name] = {'parent': 'minecraft:item/generated', 'textures': {'layer0': 'academy:items/' + name},
            'display': pose}
    for name, data in models.items():
        (MODELS / (name + '.json')).write_text(json.dumps(data, indent=2) + '\n')
    return sorted(models)
