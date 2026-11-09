#!/usr/bin/env python3
"""Single source of truth for the Academy City building materials.

Writes: textures (64x64), blockstates, block/item models, loot tables, the block tag, the lang
entries (zh_cn/en_us/ja_jp/zh_tw), the Java spec table, and the tall story-city dimension JSON.
Idempotent; re-run after editing CATALOG.

    .venv-qa/bin/python source/scripts/city/city_materials.py
"""
import colorsys
import json
import re
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parents[2]
RES = ROOT / "src/main/resources"
JAVA = ROOT / "src/main/java/cn/academy/port/city/CityBlockTable.java"
TEX = RES / "assets/academy/textures/blocks/city"

# id, zh, en, kind, texture recipe, light, hardness
CATALOG = [
    ("aluminum_panel", "铝板", "Aluminum Panel", "cube", ("aluminum",), 0, 1.5),
    ("white_panel_wall", "白色幕墙板", "White Wall Panel", "cube", ("panel", (236, 238, 240)), 0, 1.5),
    ("dark_panel_wall", "深色幕墙板", "Dark Wall Panel", "cube", ("panel", (52, 56, 62)), 0, 1.5),
    ("blue_panel_wall", "蓝色幕墙板", "Blue Wall Panel", "cube", ("panel", (52, 112, 170)), 0, 1.5),
    ("brushed_steel", "拉丝钢板", "Brushed Steel", "cube", ("steel",), 0, 2.0),
    ("reinforced_concrete", "清水混凝土", "Exposed Concrete", "cube", ("concrete",), 0, 2.0),
    ("plaza_tile", "广场地砖", "Plaza Tile", "cube", ("tile", (168, 168, 172)), 0, 1.5),
    ("composite_floor", "复合地板", "Composite Floor", "cube", ("checker",), 0, 1.5),
    ("asphalt", "沥青路面", "Asphalt", "cube", ("asphalt",), 0, 1.5),
    ("road_marking", "路面标线", "Road Marking", "cube", ("marking",), 0, 1.5),
    ("curtain_glass_blue", "蓝色幕墙玻璃", "Blue Curtain Glass", "glass", ("glass", (90, 160, 220), 120), 0, 0.5),
    ("curtain_glass_teal", "青色幕墙玻璃", "Teal Curtain Glass", "glass", ("glass", (60, 190, 190), 120), 0, 0.5),
    ("curtain_glass_smoke", "烟灰幕墙玻璃", "Smoke Curtain Glass", "glass", ("glass", (70, 76, 86), 175), 0, 0.5),
    ("curtain_glass_clear", "透明幕墙玻璃", "Clear Curtain Glass", "glass", ("glass", (200, 225, 240), 70), 0, 0.5),
    ("neon_cyan", "青色霓虹", "Cyan Neon", "cube", ("neon", (60, 235, 255)), 15, 0.8),
    ("neon_magenta", "洋红霓虹", "Magenta Neon", "cube", ("neon", (255, 70, 210)), 15, 0.8),
    ("neon_amber", "琥珀霓虹", "Amber Neon", "cube", ("neon", (255, 175, 50)), 15, 0.8),
    ("lit_window", "亮灯窗户", "Lit Window", "cube", ("window",), 12, 0.8),
    ("led_screen", "LED显示屏", "LED Screen", "cube", ("led",), 13, 0.8),
    ("pillar_white", "白色立柱", "White Pillar", "pillar", ("pillar", (232, 234, 236)), 0, 2.0),
    ("pillar_dark", "深色立柱", "Dark Pillar", "pillar", ("pillar", (60, 64, 70)), 0, 2.0),
    ("thin_glass_panel", "薄玻璃板", "Thin Glass Panel", "thin", ("glass", (200, 225, 240), 70), 0, 0.4),
    ("facade_fin", "立面装饰翼", "Facade Fin", "fin", ("steel",), 0, 1.5),
    ("white_panel_slab", "白色幕墙板半砖", "White Wall Panel Slab", "slab", ("panel", (236, 238, 240)), 0, 1.5),
    ("steel_slab", "钢板半砖", "Steel Slab", "slab", ("steel",), 0, 2.0),
    ("ceiling_light", "暖白吸顶灯", "Warm Ceiling Light", "ceiling_light", ("diffuser",), 14, 0.8),
]
BASE = {"white_panel_slab": "white_panel_wall", "steel_slab": "brushed_steel"}


# ---------------------------------------------------------------- textures (64x64)
S = 64


def solid(c, a=255):
    arr = np.zeros((S, S, 4), np.float32)
    arr[..., :3] = c
    arr[..., 3] = a
    return arr


def lowfreq(seed, cells):
    g = np.random.default_rng(seed).uniform(-1, 1, (cells, cells)).astype(np.float32)
    return np.asarray(Image.fromarray(g, "F").resize((S, S), Image.BICUBIC))


def fbm(seed):
    return (lowfreq(seed, 4) * 0.5 + lowfreq(seed + 1, 8) * 0.3 + lowfreq(seed + 2, 16) * 0.2)[..., None]


def grain(a, seed, amp):
    a[..., :3] += np.random.default_rng(seed).normal(0, amp, (S, S, 1))


def add(a, ys, xs, d):
    a[ys, xs, :3] += d


def bevel(a, x0, y0, x1, y1, w, hi, lo):
    """Raised edge: light top/left, dark bottom/right (swap hi/lo to recess)."""
    for i in range(w):
        f = 1 - i / w
        a[y0 + i, x0 + i:x1 - i, :3] += hi * f
        a[y0 + i:y1 - i, x0 + i, :3] += hi * f
        a[y1 - 1 - i, x0 + i:x1 - i, :3] += lo * f
        a[y0 + i:y1 - i, x1 - 1 - i, :3] += lo * f


def disc(a, cx, cy, r, d):
    ii, jj = np.mgrid[0:S, 0:S]
    m = (ii - cy) ** 2 + (jj - cx) ** 2 <= r * r
    a[m, :3] += d


def screw(a, cx, cy):
    disc(a, cx, cy, 1.8, -30)
    disc(a, cx - 1, cy - 1, 0.8, 55)


def frame(a, c, w=3):
    for sl in ((slice(0, w), slice(None)), (slice(S - w, S), slice(None)), (slice(None), slice(0, w)), (slice(None), slice(S - w, S))):
        a[sl[0], sl[1], :3] = c
        a[sl[0], sl[1], 3] = 255
    bevel(a, 0, 0, S, S, w, 26, -34)
    bevel(a, w, w, S - w, S - w, 2, -22, 18)


def tex_panel(color, seed):
    a = solid(color)
    r = np.random.default_rng(seed)
    a[..., :3] += fbm(seed) * 5
    grain(a, seed + 9, 1.4)
    for oy in (0, 32):
        for ox in (0, 32):
            a[oy:oy + 32, ox:ox + 32, :3] += r.normal(0, 3)
            bevel(a, ox, oy, ox + 32, oy + 32, 2, 18, -24)
            bevel(a, ox + 5, oy + 5, ox + 27, oy + 27, 1, -16, 12)          # recessed groove
            for dx, dy in ((3, 3), (28, 3), (3, 28), (28, 28)): screw(a, ox + dx, oy + dy)
    return a


def tex_aluminum(seed):
    a = solid((205, 209, 214))
    x = np.arange(S)
    a[..., :3] *= (0.96 + 0.06 * np.cos(2 * np.pi * x / 8))[None, :, None]
    for k in range(0, S, 16): add(a, slice(None), k, -26); add(a, slice(None), k + 1, 12)
    for k in (0, 32): add(a, k, slice(None), -26); add(a, k + 1, slice(None), 14)
    a[..., :3] += fbm(seed) * 3
    grain(a, seed + 3, 1.5)
    return a


def tex_steel(seed):
    a = solid((172, 178, 186))
    r = np.random.default_rng(seed)
    a[..., :3] += r.normal(0, 4.5, (S, 1, 1)) + r.normal(0, 1.6, (S, S, 1))
    a[..., :3] += np.asarray(Image.fromarray(r.uniform(-1, 1, (S, 3)).astype(np.float32), "F").resize((S, S), Image.BICUBIC))[..., None] * 5
    for row, off in ((0, 0), (16, 16), (32, 0), (48, 16)):                  # staggered 32x16 plates
        for px in range(off, S + 32, 32):
            x0 = px % S
            add(a, row, slice(None), -24)
        add(a, row + 1, slice(None), 12)
    for k in (0, 32): add(a, slice(None), k, -24); add(a, slice(None), k + 1, 12)
    for row, off in ((0, 0), (16, 16), (32, 0), (48, 16)):
        for px in (4, 28):
            screw(a, (off + px) % S, row + 4)
            screw(a, (off + px) % S, row + 12)
    return a


def tex_concrete(seed):
    a = solid((150, 150, 148))
    r = np.random.default_rng(seed)
    a[..., :3] += fbm(seed) * 11
    grain(a, seed + 4, 2.6)
    for _ in range(90):                                                      # pores
        y, x = r.integers(1, S - 2, 2)
        a[y:y + r.integers(1, 3), x:x + r.integers(1, 3), :3] -= r.uniform(18, 40)
    for k in (0, 32): add(a, slice(None), k, -14); add(a, slice(None), k + 1, 8)
    for k in (21, 42): add(a, k, slice(None), -6)
    for cx, cy in ((16, 16), (48, 16), (16, 48), (48, 48)):                  # form tie holes
        disc(a, cx, cy, 4.2, 14); disc(a, cx, cy, 3, -62); disc(a, cx + 1, cy + 1, 1.2, -20)
    for x in r.integers(0, S, 5):                                            # rain stains
        a[:, x:x + 2, :3] -= np.linspace(10, 0, S).reshape(S, 1, 1) * r.uniform(.6, 1.2)
    return a


def tex_tile(color, seed):
    a = solid(color)
    r = np.random.default_rng(seed)
    a[..., :3] += fbm(seed) * 4
    for ty in range(4):
        for tx in range(4):
            sl = (slice(ty * 16, ty * 16 + 16), slice(tx * 16, tx * 16 + 16))
            a[sl][..., :3] += r.normal(0, 5)
            for _ in range(10):
                y, x = r.integers(1, 15, 2)
                a[ty * 16 + y, tx * 16 + x, :3] += r.choice([-26, 24])
            bevel(a, tx * 16, ty * 16, tx * 16 + 16, ty * 16 + 16, 1, 14, -18)
    for k in range(0, S, 16): add(a, k, slice(None), -46); add(a, slice(None), k, -46)
    grain(a, seed + 2, 1.2)
    return a


def tex_checker(seed):
    a = solid((208, 211, 215))
    r = np.random.default_rng(seed)
    for ty in range(2):
        for tx in range(2):
            sl = (slice(ty * 32, ty * 32 + 32), slice(tx * 32, tx * 32 + 32))
            dark = (tx + ty) % 2
            a[sl][..., :3] = (182, 186, 192) if dark else (208, 211, 215)
            y = np.arange(32)[:, None] if dark else np.arange(32)[None, :]
            a[sl][..., :3] += (np.sin(y * 1.3 + r.uniform(0, 6)) * 2.6 + r.normal(0, 1.2, (32, 32)))[..., None]
            bevel(a, tx * 32, ty * 32, tx * 32 + 32, ty * 32 + 32, 2, 12, -16)
    for k in (0, 32): add(a, k, slice(None), -34); add(a, slice(None), k, -34)
    return a


def tex_asphalt(seed):
    a = solid((46, 48, 52))
    r = np.random.default_rng(seed)
    a[..., :3] += fbm(seed) * 5
    grain(a, seed + 5, 3.2)
    for _ in range(420):
        y, x = r.integers(0, S, 2)
        a[y:y + r.integers(1, 3), x:x + r.integers(1, 3), :3] += r.uniform(14, 48)
    for _ in range(22):
        y, x = r.integers(0, S, 2)
        a[y:y + 2, x:x + 3, :3] -= 16
    return a


def tex_marking(seed):
    a = tex_asphalt(seed)
    r = np.random.default_rng(seed + 11)
    for y in range(S):
        if y % 32 < 20:
            for x in (28, 29, 34, 35):                                       # worn double yellow dashes
                if r.random() > 0.1: a[y, x, :3] = (232 + r.normal(0, 5), 192 + r.normal(0, 5), 44)
    return a


def tex_glass(color, alpha, seed):
    a = solid(color, alpha)
    r = np.random.default_rng(seed)
    g = np.linspace(0, 1, S).reshape(S, 1, 1)
    a[..., :3] += 46 * (1 - g) - 20 * g
    ii, jj = np.mgrid[0:S, 0:S]
    for c, w, amp in ((10, 3.5, 44), (-26, 2.2, 30), (30, 1.6, 22)):         # soft diagonal sky reflections
        s = np.exp(-(((ii - jj) - c) / w) ** 2)
        a[..., :3] += (s * amp)[..., None]
        a[..., 3] = np.minimum(255, a[..., 3] + s * 60)
    x = 4                                                                    # reflected skyline in the lower third
    while x < S - 4:
        w = int(r.integers(3, 8)); h = int(r.integers(6, 22))
        a[S - 4 - h:S - 4, x:x + w, :3] *= 0.86
        a[S - 4 - h:S - 4, x:x + w, 3] = np.minimum(255, a[S - 4 - h:S - 4, x:x + w, 3] + 26)
        x += w + int(r.integers(0, 3))
    frame(a, (178, 186, 194), 3)
    for k in (31,):
        a[4:S - 4, k:k + 2, :3] = (178, 186, 194); a[4:S - 4, k:k + 2, 3] = 255
        a[k:k + 2, 4:S - 4, :3] = (178, 186, 194); a[k:k + 2, 4:S - 4, 3] = 255
        add(a, slice(4, S - 4), k, 22); add(a, slice(4, S - 4), k + 1, -26)
        add(a, k, slice(4, S - 4), 22); add(a, k + 1, slice(4, S - 4), -26)
    for cx, cy in ((7, 7), (56, 7), (7, 56), (56, 56)): screw(a, cx, cy)
    return a


def tex_neon(color, seed):
    a = solid((28, 28, 34))
    c = np.array(color, np.float32)
    for k in range(4):                                                       # four glowing tubes
        y0 = 6 + k * 14
        for y in range(y0, y0 + 10):
            p = np.cos((y - (y0 + 4.5)) / 5.2 * np.pi / 2)
            p = max(p, 0) ** 1.2
            a[y, 6:S - 6, :3] = c * (0.30 + 0.70 * p) + 255 * 0.55 * p ** 3
    for y in range(S):
        a[y, 4:6, :3] = (60, 60, 70); a[y, S - 6:S - 4, :3] = (60, 60, 70)
    frame(a, (36, 36, 42), 3)
    for cx, cy in ((6, 6), (57, 6), (6, 57), (57, 57)): screw(a, cx, cy)
    grain(a, seed, 1.2)
    return a


def tex_window(seed):
    a = solid((255, 226, 160))
    r = np.random.default_rng(seed)
    g = np.linspace(0, 1, S).reshape(S, 1, 1)
    a[..., :3] += np.array([0, -56, -70]) * g
    ii, jj = np.mgrid[0:S, 0:S]
    a[..., :3] += (np.exp(-(((ii - 18) ** 2 + (jj - 32) ** 2) / 380.0)) * 40)[..., None]   # ceiling lamp glow
    for side in (0, 1):                                                      # curtains
        x0, x1 = (3, 13) if side == 0 else (S - 13, S - 3)
        folds = 0.82 + 0.18 * np.cos(np.arange(x1 - x0) * 1.6)
        a[3:S - 3, x0:x1, :3] *= folds[None, :, None] * np.array([1, 0.78, 0.62])
    for _ in range(4):                                                       # furniture silhouettes
        x = int(r.integers(14, S - 24)); w = int(r.integers(6, 14)); h = int(r.integers(5, 12))
        a[S - 3 - h:S - 3, x:x + w, :3] *= 0.42
    a[3:S - 3, 31:33, :3] = (70, 74, 80)
    a[31:33, 3:S - 3, :3] = (70, 74, 80)
    frame(a, (78, 82, 88), 3)
    grain(a, seed + 1, 2)
    return a


def tex_diffuser(seed):
    a = solid((253, 227, 186))
    y, x = np.mgrid[0:S, 0:S]
    radius = ((x - 31.5) / 31.5) ** 2 + ((y - 31.5) / 31.5) ** 2
    falloff = np.clip(radius, 0, 1)[..., None]
    a[..., :3] -= falloff * np.array((10, 24, 34))
    ribs = (3 * np.cos(x * np.pi / 4) + 1.1 * np.cos(y * np.pi / 2))[..., None]
    a[..., :3] += ribs
    grain(a, seed, 0.8)
    bevel(a, 0, 0, S, S, 3, 7, -19)
    return a


def tex_ceiling_case(seed):
    a = solid((163, 170, 176))
    grain(a, seed, 1.6)
    bevel(a, 0, 0, S, S, 4, 22, -31)
    frame(a, (136, 145, 152), 3)
    for x, y in ((5, 5), (58, 5), (5, 58), (58, 58)):
        screw(a, x, y)
    return a


def tex_led(seed):
    a = solid((8, 10, 14))
    inner = np.zeros((S - 6, S - 6, 3), np.float32)
    ii, jj = np.mgrid[0:S - 6, 0:S - 6]
    hue = (jj / (S - 6) * 0.55 + 0.52 + 0.04 * np.sin(ii / 5.0)) % 1
    for y in range(S - 6):
        for x in range(S - 6):
            inner[y, x] = colorsys.hsv_to_rgb(hue[y, x], 0.78, 0.9)
    inner *= 255
    for k in range(3):                                                       # data traces
        y = (np.sin(np.arange(S - 6) / (5 + 3 * k) + k * 2) * (6 + 3 * k) + 14 + 16 * k).astype(int).clip(1, S - 8)
        for x, yy in enumerate(y): inner[yy:yy + 2, x] = (255, 255, 255)
    inner *= (1 - 0.18 * (ii % 2))[..., None]
    inner[(ii % 2 == 1) & (jj % 2 == 1)] *= 0.45
    a[3:S - 3, 3:S - 3, :3] = inner
    frame(a, (22, 24, 30), 3)
    return a


def tex_pillar_side(color, seed):
    a = solid(color)
    x = np.arange(S)
    a[..., :3] *= (0.84 + 0.16 * np.cos(2 * np.pi * x / 8))[None, :, None]
    a[..., :3] += fbm(seed) * 3
    add(a, 0, slice(None), -20); add(a, S - 1, slice(None), 12)
    grain(a, seed + 6, 1.4)
    return a


def tex_pillar_top(color, seed):
    a = solid(color)
    base = np.array(color, np.float32)
    bevel(a, 0, 0, S, S, 6, 22, -30)
    bevel(a, 8, 8, S - 8, S - 8, 3, -26, 20)
    disc(a, 31.5, 31.5, 9, 14); disc(a, 31.5, 31.5, 6.5, -18)
    grain(a, seed + 5, 1.6)
    return a


def make_texture(cid, recipe, seed):
    kind = recipe[0]
    if kind == "panel": return tex_panel(recipe[1], seed)
    if kind == "aluminum": return tex_aluminum(seed)
    if kind == "steel": return tex_steel(seed)
    if kind == "concrete": return tex_concrete(seed)
    if kind == "tile": return tex_tile(recipe[1], seed)
    if kind == "checker": return tex_checker(seed)
    if kind == "asphalt": return tex_asphalt(seed)
    if kind == "marking": return tex_marking(seed)
    if kind == "glass": return tex_glass(recipe[1], recipe[2], seed)
    if kind == "neon": return tex_neon(recipe[1], seed)
    if kind == "window": return tex_window(seed)
    if kind == "diffuser": return tex_diffuser(seed)
    if kind == "led": return tex_led(seed)
    raise KeyError(kind)


def save_png(arr, path):
    path.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.clip(arr, 0, 255).astype(np.uint8), "RGBA").save(path)


# ---------------------------------------------------------------- json helpers
def wjson(path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")


def tx(name): return f"academy:blocks/city/{name}"


def thin_model(texture):
    face = {"texture": "#t", "cullface": None}
    def f(uv, cull=None):
        d = {"uv": uv, "texture": "#t"}
        if cull: d["cullface"] = cull
        return d
    return {"render_type": "minecraft:translucent", "textures": {"t": texture, "particle": texture},
            "elements": [{"from": [0, 0, 0], "to": [16, 16, 2],
                          "faces": {"north": f([0, 0, 16, 16], "north"), "south": f([0, 0, 16, 16]),
                                    "east": f([14, 0, 16, 16]), "west": f([0, 0, 2, 16]),
                                    "up": f([0, 14, 16, 16], "up"), "down": f([0, 0, 16, 2], "down")}}]}


def fin_model(texture):
    def f(uv): return {"uv": uv, "texture": "#t"}
    return {"textures": {"t": texture, "particle": texture},
            "elements": [{"from": [6, 0, 0], "to": [10, 16, 5],
                          "faces": {"north": f([6, 0, 10, 16]), "south": f([6, 0, 10, 16]),
                                    "east": f([0, 0, 5, 16]), "west": f([0, 0, 5, 16]),
                                    "up": f([6, 0, 10, 5]), "down": f([6, 0, 10, 5])}}]}


def ceiling_light_model(texture, housing):
    sides = ("north", "south", "east", "west")
    all_faces = (*sides, "up", "down")
    metal = {"texture": "#housing"}
    underside = {"uv": [0, 0, 16, 16], "texture": "#housing"}
    diffuser = {"uv": [0, 0, 16, 16], "texture": "#lens",
                "neoforge_data": {"block_light": 14, "ambient_occlusion": False}}
    return {
        "parent": "minecraft:block/block",
        "textures": {"particle": housing, "housing": housing, "lens": texture},
        "elements": [
            {"from": [1, 15.25, 2], "to": [15, 16, 14],
             "faces": {**dict.fromkeys(all_faces, metal), "up": {**metal, "cullface": "up"}}},
            {"from": [0.5, 13, 1.5], "to": [15.5, 15.25, 14.5],
             "faces": {**dict.fromkeys(all_faces, metal), "down": underside}},
            {"from": [1.4, 12.35, 2.4], "to": [14.6, 13, 13.6], "shade": False,
             "faces": {**dict.fromkeys(sides, {"texture": "#lens"}), "down": diffuser}},
        ],
    }


def write_assets():
    mdl, bs, itm, loot = (RES / "assets/academy/models/block", RES / "assets/academy/blockstates",
                          RES / "assets/academy/models/item", RES / "data/academy/loot_table/blocks")
    seed = 100
    for cid, zh, en, kind, recipe, light, hard in CATALOG:
        seed += 1
        if cid not in BASE and kind != "pillar":          # slabs reuse the base block's texture
            save_png(make_texture(cid, recipe, seed), TEX / f"{cid}.png")
        if kind == "pillar":
            save_png(tex_pillar_side(recipe[1], seed), TEX / f"{cid}.png")
            save_png(tex_pillar_top(recipe[1], seed), TEX / f"{cid}_top.png")
        t = tx(BASE.get(cid, cid))
        if kind == "cube":
            wjson(mdl / f"{cid}.json", {"parent": "minecraft:block/cube_all", "textures": {"all": t}})
            wjson(bs / f"{cid}.json", {"variants": {"": {"model": f"academy:block/{cid}"}}})
        elif kind == "ceiling_light":
            housing = cid + "_housing"
            save_png(tex_ceiling_case(seed + 1), TEX / f"{housing}.png")
            wjson(mdl / f"{cid}.json", ceiling_light_model(t, tx(housing)))
            wjson(bs / f"{cid}.json", {"variants": {"": {"model": f"academy:block/{cid}"}}})
        elif kind == "glass":
            wjson(mdl / f"{cid}.json", {"parent": "minecraft:block/cube_all", "render_type": "minecraft:translucent",
                                        "textures": {"all": t}})
            wjson(bs / f"{cid}.json", {"variants": {"": {"model": f"academy:block/{cid}"}}})
        elif kind == "pillar":
            wjson(mdl / f"{cid}.json", {"parent": "minecraft:block/cube_column",
                                        "textures": {"end": tx(cid + "_top"), "side": t}})
            wjson(bs / f"{cid}.json", {"variants": {"axis=y": {"model": f"academy:block/{cid}"},
                                                    "axis=z": {"model": f"academy:block/{cid}", "x": 90},
                                                    "axis=x": {"model": f"academy:block/{cid}", "x": 90, "y": 90}}})
        elif kind in ("thin", "fin"):
            wjson(mdl / f"{cid}.json", (thin_model if kind == "thin" else fin_model)(t))
            wjson(bs / f"{cid}.json", {"variants": {f"facing={d}": {"model": f"academy:block/{cid}", "y": y}
                                                    for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}})
        elif kind == "slab":
            tt = {"bottom": t, "top": t, "side": t}
            wjson(mdl / f"{cid}.json", {"parent": "minecraft:block/slab", "textures": tt})
            wjson(mdl / f"{cid}_top.json", {"parent": "minecraft:block/slab_top", "textures": tt})
            wjson(mdl / f"{cid}_double.json", {"parent": "minecraft:block/cube_all", "textures": {"all": t}})
            wjson(bs / f"{cid}.json", {"variants": {"type=bottom": {"model": f"academy:block/{cid}"},
                                                    "type=top": {"model": f"academy:block/{cid}_top"},
                                                    "type=double": {"model": f"academy:block/{cid}_double"}}})
        item = {"parent": f"academy:block/{cid}"}
        if kind == "ceiling_light":
            item["display"] = {"gui": {"rotation": [-25, 225, 0], "translation": [0, -3.5, 0],
                                      "scale": [0.75, 0.75, 0.75]}}
        wjson(itm / f"{cid}.json", item)
        entry = {"type": "minecraft:item", "name": f"academy:{cid}"}
        if kind == "slab":
            entry["functions"] = [{"function": "minecraft:set_count", "count": 2, "add": False, "conditions": [
                {"condition": "minecraft:block_state_property", "block": f"academy:{cid}", "properties": {"type": "double"}}]},
                {"function": "minecraft:explosion_decay"}]
        else:
            entry["functions"] = [{"function": "minecraft:explosion_decay"}]
        wjson(loot / f"{cid}.json", {"type": "minecraft:block", "random_sequence": f"academy:blocks/{cid}",
                                     "pools": [{"rolls": 1, "entries": [entry]}]})
    wjson(RES / "data/academy/tags/block/city_materials.json",
          {"replace": False, "values": [f"academy:{c[0]}" for c in CATALOG]})


def add_to_pickaxe():
    p = RES / "data/minecraft/tags/block/mineable/pickaxe.json"
    s = p.read_text(encoding="utf-8")
    if "#academy:city_materials" not in s:
        s = s.replace('"values": [', '"values": [\n    "#academy:city_materials",', 1)
        p.write_text(s, encoding="utf-8")


def add_lang():
    ceiling_names = {"ja_jp": "温白色シーリングライト", "zh_tw": "暖白吸頂燈"}
    for code, idx in (("zh_cn", 1), ("en_us", 2), ("ja_jp", 1), ("zh_tw", 1)):
        p = RES / f"assets/academy/lang/{code}.json"
        s = p.read_text(encoding="utf-8")
        add = []
        for c in CATALOG:
            name = ceiling_names.get(code, c[idx]) if c[0] == "ceiling_light" else c[idx]
            for key in (f"block.academy.{c[0]}", f"item.academy.{c[0]}"):
                if f'"{key}"' not in s: add.append(f'  "{key}": {json.dumps(name, ensure_ascii=False)}')
        if add:
            body = s.rstrip()
            assert body.endswith("}")
            p.write_text(body[:-1].rstrip() + ",\n" + ",\n".join(add) + "\n}\n", encoding="utf-8")


def write_java():
    rows = ",\n".join(f'            new Spec("{c[0]}", Kind.{c[3].upper()}, {c[5]}, {c[6]}f)' for c in CATALOG)
    JAVA.parent.mkdir(parents=True, exist_ok=True)
    JAVA.write_text(f"""package cn.academy.port.city;

/** Generated by scripts/city/city_materials.py; edit CATALOG there, not here. */
final class CityBlockTable {{
    enum Kind {{ CUBE, GLASS, PILLAR, THIN, FIN, SLAB, CEILING_LIGHT }}

    record Spec(String id, Kind kind, int light, float hardness) {{}}

    static final Spec[] SPECS = {{
{rows}
    }};

    private CityBlockTable() {{}}
}}
""", encoding="utf-8")


def write_dimension():
    wjson(RES / "data/academy/dimension_type/story_city.json", {
        "ultrawarm": False, "natural": True, "coordinate_scale": 1.0, "has_skylight": True, "has_ceiling": False,
        "ambient_light": 0.0, "monster_spawn_light_level": 0, "monster_spawn_block_light_limit": 0,
        "piglin_safe": False, "bed_works": True, "respawn_anchor_works": False, "has_raids": False,
        "logical_height": 1024, "height": 1024, "min_y": -64,
        "infiniburn": "#minecraft:infiniburn_overworld", "effects": "minecraft:overworld"})
    wjson(RES / "data/academy/dimension/story_city.json", {
        "type": "academy:story_city",
        "generator": {"type": "minecraft:flat", "settings": {
            "biome": "minecraft:plains", "features": False, "lakes": False, "structure_overrides": [],
            "layers": [{"block": "minecraft:bedrock", "height": 1}, {"block": "minecraft:stone", "height": 59},
                       {"block": "academy:reinforced_concrete", "height": 3}, {"block": "academy:plaza_tile", "height": 1}]}}})


if __name__ == "__main__":
    write_assets()
    add_to_pickaxe()
    add_lang()
    write_java()
    write_dimension()
    print(f"{len(CATALOG)} blocks written")
