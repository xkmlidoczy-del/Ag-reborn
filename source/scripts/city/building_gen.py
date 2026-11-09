#!/usr/bin/env python3
"""Parametric Academy City building generator (prototype).

Each building is podium (storefront, sign band, entrance awning) + tower (recessed windows,
piers, fins, belt courses, setbacks, balconies) + crown (penthouse, vents, antenna beacon),
standing on a small plaza with trees and street lamps. Output: a 1.21 structure-template
.nbt per building plus isometric day/night previews and a contact sheet.

    .venv-qa/bin/python source/scripts/city/building_gen.py [outdir]
"""
import gzip
import random
import re
import struct
import sys
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parents[3] / "tmp/city-prototype"

STYLES = {
    "academic": dict(wall="academy:white_panel_wall", pier="academy:pillar_white", accent="academy:blue_panel_wall",
                     glass="academy:curtain_glass_blue", sill="academy:white_panel_slab[type=bottom]",
                     belt="academy:white_panel_slab[type=top]", light="academy:lit_window", base="academy:plaza_tile",
                     roof="academy:reinforced_concrete", dark="academy:dark_panel_wall", sign="academy:blue_panel_wall",
                     fin="academy:facade_fin", plaza2="academy:composite_floor", neon="academy:neon_cyan"),
    "lab": dict(wall="academy:dark_panel_wall", pier="academy:pillar_dark", accent="academy:neon_cyan",
                glass="academy:curtain_glass_teal", sill="academy:steel_slab[type=bottom]",
                belt="academy:steel_slab[type=top]", light="academy:lit_window", base="academy:brushed_steel",
                roof="academy:reinforced_concrete", dark="academy:dark_panel_wall", sign="academy:dark_panel_wall",
                fin="academy:facade_fin", plaza2="academy:plaza_tile", neon="academy:neon_cyan"),
    "residential": dict(wall="academy:aluminum_panel", pier="academy:pillar_white", accent="academy:blue_panel_wall",
                        glass="academy:curtain_glass_clear", sill="academy:white_panel_slab[type=bottom]",
                        belt="academy:white_panel_slab[type=top]", light="academy:lit_window", base="academy:plaza_tile",
                        roof="academy:reinforced_concrete", dark="academy:dark_panel_wall", sign="academy:aluminum_panel",
                        fin="academy:facade_fin", plaza2="academy:composite_floor", neon="academy:neon_amber"),
    "commercial": dict(wall="academy:dark_panel_wall", pier="academy:pillar_dark", accent="academy:neon_magenta",
                       glass="academy:curtain_glass_smoke", sill="academy:steel_slab[type=bottom]",
                       belt="academy:steel_slab[type=top]", light="academy:lit_window", base="academy:brushed_steel",
                       roof="academy:reinforced_concrete", dark="academy:dark_panel_wall", sign="academy:led_screen",
                       fin="academy:facade_fin", plaza2="academy:plaza_tile", neon="academy:neon_magenta"),
}


class Building:
    def __init__(self, style, seed):
        self.st = STYLES[style]
        self.r = random.Random(seed)
        self.B = {}

    def put(self, x, y, z, block):
        if block in ("air", "minecraft:air"):
            self.B.pop((x, y, z), None)
        else:
            self.B[(x, y, z)] = block if ":" in block else "minecraft:" + block

    def fill(self, x0, y0, z0, x1, y1, z1, block):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.put(x, y, z, block)

    @staticmethod
    def perimeter(rect, grow=0):
        x0, z0, x1, z1 = rect[0] - grow, rect[1] - grow, rect[2] + grow, rect[3] + grow
        for x in range(x0, x1 + 1):
            for z in range(z0, z1 + 1):
                if x in (x0, x1) or z in (z0, z1):
                    yield x, z

    @staticmethod
    def wall_info(rect, x, z):
        """(outward vector, index along wall, wall length, is_corner)."""
        x0, z0, x1, z1 = rect
        corner = x in (x0, x1) and z in (z0, z1)
        if z == z0: return (0, -1), x - x0, x1 - x0, corner
        if z == z1: return (0, 1), x - x0, x1 - x0, corner
        if x == x0: return (-1, 0), z - z0, z1 - z0, corner
        return (1, 0), z - z0, z1 - z0, corner

    def fin(self, o):
        face = {(0, -1): "south", (0, 1): "north", (1, 0): "west", (-1, 0): "east"}[o]   # fin hugs the wall behind it
        return self.st["fin"] + f"[facing={face}]"

    def facade(self, rect, f0, f1, every=4, fins=False, balcony=False, neon=False, lit=0.5, tall=False):
        st = self.st
        x0, z0, x1, z1 = rect
        for f in range(f0, f1 + 1):
            for k in range(4):
                y = 4 * f + k
                for x in range(x0, x1 + 1):
                    for z in range(z0, z1 + 1):
                        if not (x in (x0, x1) or z in (z0, z1)):
                            if k == 0: self.put(x, y, z, st["wall"])      # floor slab
                            continue
                        o, idx, ln, corner = self.wall_info(rect, x, z)
                        if corner or idx % every == 0:
                            self.put(x, y, z, st["pier"])
                            if fins and not corner and idx % (every * 2) == 0:
                                self.put(x + o[0], y, z + o[1], st["neon"] if neon and k % 2 == 0 and f % 2 == 0 else self.fin(o))
                        elif k in ((1, 2, 3) if tall else (1, 2)):
                            if k == 1: self.put(x, y, z, st["sill"])
                            self.put(x - o[0], y, z - o[1], st["glass"])  # recessed pane
                            if k == 2 and idx % every in (1, 3) and self.r.random() < lit:
                                self.put(x - 2 * o[0], y, z - 2 * o[1], st["light"])
                        else:
                            self.put(x, y, z, st["wall"])
                        if balcony and k == 0 and not corner and idx % (every * 2) in (1, 2, 3):
                            for d in (1, 2): self.put(x + o[0] * d, y, z + o[1] * d, st["belt"])
                            self.put(x + o[0] * 2, y + 1, z + o[1] * 2, "iron_bars")
                            if idx % (every * 2) in (1, 3): self.put(x + o[0], y + 1, z + o[1], "iron_bars")
            if (f - f0) % 4 == 3 or f == f1:                              # belt course
                for x, z in self.perimeter(rect, 1): self.put(x, 4 * f + 3, z, st["belt"])

    def roof(self, rect, f_end, next_rect=None, garden=0.12):
        st = self.st
        y = 4 * (f_end + 1)
        x0, z0, x1, z1 = rect
        self.fill(x0, y, z0, x1, y, z1, st["roof"])
        for x, z in self.perimeter(rect):
            if next_rect and next_rect[0] <= x <= next_rect[2] and next_rect[1] <= z <= next_rect[3]: continue
            self.put(x, y + 1, z, "academy:dark_panel_wall")
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                if next_rect and next_rect[0] - 1 <= x <= next_rect[2] + 1 and next_rect[1] - 1 <= z <= next_rect[3] + 1: continue
                if self.r.random() < garden:
                    self.put(x, y, z, "moss_block")
                    self.put(x, y + 1, z, "oak_leaves[persistent=true]")

    def podium(self, P, every=4, neon=False):
        st = self.st
        x0, z0, x1, z1 = P
        mid = (x1 - x0) // 2
        for x in range(x0, x1 + 1):                                      # ground floor storefront
            for z in range(z0, z1 + 1):
                if not (x in (x0, x1) or z in (z0, z1)):
                    self.put(x, 0, z, st["base"])
                    continue
                o, idx, ln, corner = self.wall_info(P, x, z)
                door = o == (0, -1) and mid - 2 <= idx < mid + 2
                self.put(x, 0, z, st["base"])
                for k in range(1, 4):
                    if door: continue
                    self.put(x, k, z, st["pier"] if corner or idx % every == 0 else st["glass"])
                if door:
                    for d in range(1, 4):
                        self.put(x, 3, z + o[1] * d, st["belt"])            # entrance awning
                    if idx in (mid - 2, mid + 1):
                        for d in (1, 2, 3): self.put(x, 4, z + o[1] * d, "lantern")
        for d in range(1, 4):
            for ix in (mid - 3, mid + 2): self.put(x0 + ix, 3, z0 - d, st["belt"])
        self.facade(P, 1, 1, every=every, neon=neon, lit=0.7)
        for x, z in self.perimeter(P):                                   # sign band on floor 1's spandrel row
            o, idx, ln, corner = self.wall_info(P, x, z)
            self.put(x, 4, z, st["light"] if idx % 4 == 2 else st["sign"])


def tree(b, x, z):
    for y in range(0, 3): b.put(x, y, z, "oak_log")
    for dx in range(-2, 3):
        for dz in range(-2, 3):
            for dy in range(0, 4):
                if dx * dx + dz * dz + (dy - 1) ** 2 <= 5 and (dx or dz or dy > 2):
                    b.put(x + dx, 3 + dy, z + dz, "oak_leaves[persistent=true]")
    b.put(x, 3, z, "oak_log")


def lamp(b, x, z):
    for y in range(0, 4): b.put(x, y, z, "oak_fence")
    b.put(x, 4, z, "lantern")


def make(style, lnx, lnz, floors, seed, steps=(), fins=False, balcony=False, neon=False):
    b = Building(style, seed)
    st = b.st
    T = (0, 0, lnx, lnz)
    P = (-2, -2, lnx + 2, lnz + 2)
    R = (-7, -7, lnx + 7, lnz + 7)
    b.fill(R[0], -1, R[1], R[2], -1, R[3], st["base"])                   # plaza
    for x in range(R[0], R[2] + 1):
        for z in range(R[1], R[3] + 1):
            if (x + z) % 2 == 0 and not (P[0] <= x <= P[2] and P[1] <= z <= P[3]) and (x % 4 == 0 or z % 4 == 0):
                b.put(x, -1, z, st["plaza2"])
    b.podium(P, neon=neon)
    b.roof(P, 1, T)
    segs, rect, f = [], T, 2
    for at, (a, c, d, e) in steps:
        segs.append((f, at - 1, rect))
        rect = (rect[0] + a, rect[1] + c, rect[2] + d, rect[3] + e)
        f = at
    segs.append((f, floors - 1, rect))
    for i, (fa, fb, rc) in enumerate(segs):
        b.facade(rc, fa, fb, fins=fins, balcony=balcony, neon=neon)
        nxt = segs[i + 1][2] if i + 1 < len(segs) else None
        b.roof(rc, fb, nxt, garden=0.1 if nxt else 0.03)
    fa, fb, rc = segs[-1]                                               # crown
    top = 4 * floors + 1
    px0, pz0, px1, pz1 = rc[0] + 3, rc[1] + 3, rc[2] - 3, rc[3] - 3
    b.fill(px0, top, pz0, px1, top + 2, pz1, st["dark"])
    b.fill(px0 + 1, top, pz0 + 1, px1 - 1, top + 2, pz1 - 1, "air") if px1 - px0 > 2 and pz1 - pz0 > 2 else None
    for i in range(px0 + 1, px1, 2): b.put(i, top + 1, pz0, "iron_bars")
    b.fill(px0, top + 3, pz0, px1, top + 3, pz1, st["roof"])
    cx, cz = (px0 + px1) // 2, (pz0 + pz1) // 2
    for y in range(top + 4, top + 15): b.put(cx, y, cz, "iron_bars")
    b.put(cx, top + 8, cz, "red_concrete")
    b.put(cx, top + 15, cz, "sea_lantern")
    for lx, lz in ((P[0] - 2, P[1] - 2), (P[2] + 2, P[1] - 2), (P[0] - 2, P[3] + 2), (P[2] + 2, P[3] + 2)):
        lamp(b, lx, lz)
    for tx, tz in ((R[0] + 2, R[1] + 2), (R[2] - 2, R[1] + 2), (R[0] + 2, R[3] - 2), (R[2] - 2, R[3] - 2),
                   ((R[0] + R[2]) // 2 - 6, R[1] + 2), ((R[0] + R[2]) // 2 + 6, R[1] + 2)):
        tree(b, tx, tz)
    for lx in range(R[0] + 4, R[2] - 3, 8):
        lamp(b, lx, R[1] + 4)
        lamp(b, lx, R[3] - 4)
    return b.B


# ---------------------------------------------------------------- NBT
def _text(v):
    raw = v.encode()
    return struct.pack(">H", len(raw)) + raw


def _payload(kind, v):
    if kind == 3: return struct.pack(">i", v)
    if kind == 8: return _text(v)
    if kind == 9:
        el, items = v
        return struct.pack(">bi", el, len(items)) + b"".join(_payload(el, i) for i in items)
    if kind == 10:
        return b"".join(struct.pack(">b", t) + _text(n) + _payload(t, d) for n, (t, d) in v.items()) + b"\0"
    raise ValueError(kind)


def split_state(block):
    m = re.fullmatch(r"([a-z_:0-9]+)(?:\[(.*)\])?", block)
    props = dict(p.split("=") for p in m.group(2).split(",")) if m.group(2) else {}
    return m.group(1), props


def write_nbt(path, B):
    mn = [min(k[i] for k in B) for i in range(3)]
    mx = [max(k[i] for k in B) for i in range(3)]
    pal = sorted(set(B.values()))
    entries = []
    for p in pal:
        name, props = split_state(p)
        e = {"Name": (8, name)}
        if props: e["Properties"] = (10, {k: (8, v) for k, v in props.items()})
        entries.append(e)
    blocks = [{"pos": (9, (3, [k[0] - mn[0], k[1] - mn[1], k[2] - mn[2]])), "state": (3, pal.index(v))}
              for k, v in sorted(B.items(), key=lambda kv: (kv[0][1], kv[0][2], kv[0][0]))]
    root = {"DataVersion": (3, 3955), "size": (9, (3, [mx[i] - mn[i] + 1 for i in range(3)])),
            "palette": (9, (10, entries)), "blocks": (9, (10, blocks)), "entities": (9, (10, []))}
    path.write_bytes(gzip.compress(b"\x0a\x00\x00" + _payload(10, root), 9, mtime=0))
    return [mx[i] - mn[i] + 1 for i in range(3)], len(B), len(pal)


# ---------------------------------------------------------------- preview
COL = {
    "white_panel_wall": (236, 238, 240), "dark_panel_wall": (52, 56, 62), "blue_panel_wall": (52, 112, 170),
    "aluminum_panel": (200, 204, 208), "brushed_steel": (170, 176, 184), "reinforced_concrete": (152, 152, 150),
    "plaza_tile": (168, 168, 172), "composite_floor": (200, 202, 205), "asphalt": (48, 50, 54), "road_marking": (120, 110, 60),
    "curtain_glass_blue": (90, 160, 220), "curtain_glass_teal": (60, 190, 190), "curtain_glass_smoke": (70, 76, 86),
    "curtain_glass_clear": (200, 225, 240), "neon_cyan": (60, 235, 255), "neon_magenta": (255, 70, 210),
    "neon_amber": (255, 175, 50), "lit_window": (255, 222, 150), "led_screen": (120, 200, 240),
    "pillar_white": (232, 234, 236), "pillar_dark": (60, 64, 70), "thin_glass_panel": (200, 225, 240),
    "facade_fin": (180, 186, 194), "white_panel_slab": (236, 238, 240), "steel_slab": (170, 176, 184),
    "white_concrete": (232, 232, 232), "smooth_quartz": (232, 226, 214), "light_blue_concrete": (45, 150, 205),
    "light_blue_stained_glass": (135, 195, 232), "quartz_slab": (232, 226, 214), "smooth_quartz_slab": (232, 226, 214),
    "sea_lantern": (200, 235, 225), "polished_andesite": (140, 142, 140), "andesite": (130, 132, 130),
    "smooth_stone": (155, 155, 155), "smooth_stone_slab": (155, 155, 155), "light_gray_concrete": (150, 150, 146),
    "gray_concrete": (62, 66, 70), "cyan_concrete": (22, 118, 134), "cyan_stained_glass": (70, 155, 175),
    "stone_brick_wall": (112, 112, 112), "stone_brick_slab": (118, 118, 118), "stone_bricks": (120, 120, 118),
    "iron_bars": (170, 170, 175), "oak_log": (100, 80, 50), "oak_leaves": (52, 128, 52), "moss_block": (90, 112, 42),
    "lantern": (255, 205, 110), "black_concrete": (24, 24, 28), "polished_blackstone": (48, 44, 52),
    "polished_blackstone_slab": (48, 44, 52), "iron_block": (205, 205, 205), "white_terracotta": (210, 178, 160),
    "terracotta": (152, 94, 67), "orange_terracotta": (162, 84, 38), "white_stained_glass": (225, 235, 240),
    "glowstone": (245, 205, 125), "magenta_concrete": (170, 48, 160), "magenta_stained_glass": (190, 100, 190),
    "polished_deepslate": (72, 72, 74), "red_concrete": (180, 40, 40), "oak_fence": (110, 88, 55), "grass_block": (96, 150, 60), "water": (50, 90, 200), "poppy": (200, 40, 40),
    "azure_bluet": (230, 230, 230), "cornflower": (80, 100, 220), "oak_log": (100, 80, 50),
}
EMISSIVE = {"sea_lantern", "glowstone", "lantern", "neon_cyan", "neon_magenta", "neon_amber", "led_screen"}
SEE_THROUGH = ("glass", "iron_bars", "lantern", "leaves", "fence", "facade_fin", "water", "poppy", "bluet", "cornflower")
unknown = set()


def info(block):
    name = block.split(":")[1].split("[")[0]
    top = bottom = None
    props = block.split("[")[1] if "[" in block else ""
    y0, y1 = 0.0, 1.0
    if name.endswith("_slab"):
        y0, y1 = (0.5, 1.0) if "type=top" in props else (0.0, 0.5)
    if name not in COL:
        base = name[:-5] if name.endswith("_slab") else name
        if base not in COL: unknown.add(name)
        return COL.get(base, (200, 0, 200)), name, y0, y1
    return COL[name], name, y0, y1


def render(B, night=False, hw=8, hh=4, vz=8):
    items = []
    for (x, y, z), blk in B.items():
        col, name, y0, y1 = info(blk)
        glass = name.endswith("glass") or name == "water" or "curtain_glass" in name or name == "thin_glass_panel"
        # faces that can be seen: top, +x, +z (camera sits toward +x,+z,+y)
        def open_(dx, dy, dz):
            n = B.get((x + dx, y + dy, z + dz))
            return n is None or any(s in n for s in SEE_THROUGH)
        faces = (open_(0, 1, 0) or y1 < 1, open_(1, 0, 0), open_(0, 0, 1))
        if any(faces): items.append((x + y + z, x, y, z, name, col, y0, y1, glass, faces))
    items.sort()

    def P(x, y, z): return ((x - z) * hw, (x + z) * hh - y * vz)
    pts = [P(x + a, y + b, z + c) for _, x, y, z, *_ in items for a in (0, 1) for b in (0, 1) for c in (0, 1)]
    minx, maxx = min(p[0] for p in pts), max(p[0] for p in pts)
    miny, maxy = min(p[1] for p in pts), max(p[1] for p in pts)
    m = 30
    W, H = int(maxx - minx + 2 * m), int(maxy - miny + 2 * m)
    img = Image.new("RGB", (W, H))
    d = ImageDraw.Draw(img)
    for yy in range(H):
        t = yy / H
        d.line([(0, yy), (W, yy)], fill=(int(14 + 14 * t), int(18 + 14 * t), int(40 + 22 * t)) if night
               else (int(150 + 75 * t), int(195 + 43 * t), int(235 + 13 * t)))
    d = ImageDraw.Draw(img, "RGBA")
    ox, oy = m - minx, m - miny
    light = 0.30 if night else 1.0
    glow = []
    for _, x, y, z, name, col, y0, y1, glass, faces in items:
        emissive = name in EMISSIVE
        if glass and night and (hash((x * 73856093) ^ (y * 19349663) ^ (z * 83492791)) % 100) < 42:
            col, emissive = (255, 214, 140), True
        def shade(f):
            if emissive: return col
            return tuple(int(c * f * light) for c in col)
        alpha = 150 if glass else (170 if any(s in name for s in ("bars", "fence")) else 255)
        def poly(corners, color):
            d.polygon([(P(*c)[0] + ox, P(*c)[1] + oy) for c in corners], fill=color + (alpha,))
        top_y, bot_y = y + y1, y + y0
        if faces[0]: poly([(x, top_y, z), (x + 1, top_y, z), (x + 1, top_y, z + 1), (x, top_y, z + 1)], shade(1.0))
        if faces[1]: poly([(x + 1, bot_y, z), (x + 1, top_y, z), (x + 1, top_y, z + 1), (x + 1, bot_y, z + 1)], shade(0.74))
        if faces[2]: poly([(x, bot_y, z + 1), (x, top_y, z + 1), (x + 1, top_y, z + 1), (x + 1, bot_y, z + 1)], shade(0.56))
        if night and name in EMISSIVE:
            glow.append(((P(x + .5, y + .5, z + .5)[0] + ox), (P(x + .5, y + .5, z + .5)[1] + oy), col))
    if night:
        for gx, gy, col in glow:
            d.ellipse([gx - 9, gy - 9, gx + 9, gy + 9], fill=col + (26,))
    return img


CONFIGS = [
    ("academic_tower", "academic", dict(lnx=20, lnz=16, floors=14, seed=1,
                                         steps=[(9, (4, 0, -4, 0)), (12, (0, 4, 0, -4))], fins=True)),
    ("lab_tower", "lab", dict(lnx=16, lnz=16, floors=24, seed=2, steps=[(14, (4, 4, -4, -4))], fins=True)),
    ("residential_block", "residential", dict(lnx=24, lnz=12, floors=12, seed=3,
                                               steps=[(10, (0, 0, -8, 0))], balcony=True)),
    ("commercial_tower", "commercial", dict(lnx=16, lnz=16, floors=16, seed=4,
                                             steps=[(10, (0, 0, -4, -4))], fins=True, neon=True)),
]


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else OUT
    out.mkdir(parents=True, exist_ok=True)
    tiles = []
    for name, style, kw in CONFIGS:
        B = make(style, **kw)
        size, n, pal = write_nbt(out / (name + ".nbt"), B)
        print(f"{name}: size={size} blocks={n} palette={pal}")
        row = []
        for night in (False, True):
            im = render(B, night)
            im.save(out / f"{name}_{'night' if night else 'day'}.png")
            row.append(im)
        tiles.append(row)
    if unknown: print("unknown colours:", sorted(unknown))
    th = 760
    cols = []
    for row in tiles:
        r = [im.resize((int(im.width * th / im.height), th), Image.LANCZOS) for im in row]
        cols.append(r)
    w = max(max(r[0].width, r[1].width) for r in cols)
    sheet = Image.new("RGB", (w * len(cols), th * 2), (20, 20, 24))
    for i, r in enumerate(cols):
        for j, im in enumerate(r): sheet.paste(im, (i * w + (w - im.width) // 2, j * th))
    sheet.save(out / "contact_sheet.png")
    print("sheet", sheet.size)


if __name__ == "__main__":
    main()
