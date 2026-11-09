#!/usr/bin/env python3
"""Grand Academy City complex v2: central spire, four themed satellite towers, four mid-rise corner
blocks, glass skybridges, a colonnaded podium with a landscaped roof terrace, plaza fountains and a ring road.
Needs the tall story-city dimension (min_y -64, height 1024).

    .venv-qa/bin/python source/scripts/city/complex_gen.py [--no-render]

Writes tmp/city-prototype/grand_complex.nbt (+ previews) and the structure template into
src/main/resources/data/academy/structure/city/. Template y=0 is the plaza layer (world y=-1).
"""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
from building_gen import OUT, STYLES, Building, lamp, render, tree, write_nbt  # noqa: E402

RES_STRUCT = Path(__file__).resolve().parents[2] / "src/main/resources/data/academy/structure/city"
LEAVES = "minecraft:oak_leaves[persistent=true]"


def grow(rect, n): return (rect[0] - n, rect[1] - n, rect[2] + n, rect[3] + n)


def inside(rect, x, z): return rect[0] <= x <= rect[2] and rect[1] <= z <= rect[3]


# ---------------------------------------------------------------- crowns
def crown(b, st, rc, floors, kind, mast):
    top = 4 * floors + 1
    x0, z0, x1, z1 = rc
    if kind == "spire":                                          # three stepped tiers with neon cornices, then a mast
        px = (x0, z0, x1, z1)
        y = top
        for tier in range(3):
            px = (x0 + 3 + 2 * tier, z0 + 3 + 2 * tier, x1 - 3 - 2 * tier, z1 - 3 - 2 * tier)
            if px[2] - px[0] < 2 or px[3] - px[1] < 2: break
            b.fill(px[0], y, px[1], px[2], y + 3, px[3], st["dark"])
            for x in range(px[0], px[2] + 1):
                for z in range(px[1], px[3] + 1):
                    if x in (px[0], px[2]) or z in (px[1], px[3]): b.put(x, y + 3, z, st["neon"])
            y += 4
        cx, cz = (px[0] + px[2]) // 2, (px[1] + px[3]) // 2
        for k in range(mast):
            b.put(cx, y + k, cz, st["pier"])
            if k % 8 == 7: b.put(cx, y + k, cz, st["neon"])
        b.put(cx, y + mast, cz, st["neon"])
    elif kind == "fins":                                         # four corner fins around a glass lantern
        for cx, cz in ((x0 + 1, z0 + 1), (x1 - 1, z0 + 1), (x0 + 1, z1 - 1), (x1 - 1, z1 - 1)):
            for k in range(12): b.put(cx, top + k, cz, st["pier"])
            b.put(cx, top + 12, cz, st["neon"])
        gx0, gz0, gx1, gz1 = x0 + 4, z0 + 4, x1 - 4, z1 - 4
        for x in range(gx0, gx1 + 1):
            for z in range(gz0, gz1 + 1):
                edge = x in (gx0, gx1) or z in (gz0, gz1)
                for k in range(5):
                    if edge: b.put(x, top + k, z, st["pier"] if (x in (gx0, gx1) and z in (gz0, gz1)) else "academy:curtain_glass_blue")
                b.put(x, top + 5, z, st["belt"])
        for k in range(mast): b.put((x0 + x1) // 2, top + 6 + k, (z0 + z1) // 2, st["pier"])
        b.put((x0 + x1) // 2, top + 6 + mast, (z0 + z1) // 2, st["neon"])
    else:                                                        # pavilion: glass room in a roof garden
        for x in range(x0 + 1, x1):
            for z in range(z0 + 1, z1):
                b.put(x, top - 1, z, "minecraft:grass_block")
                if (x * 5 + z * 3) % 7 == 0: b.put(x, top, z, LEAVES)
        gx0, gz0, gx1, gz1 = x0 + 4, z0 + 4, x1 - 4, z1 - 4
        for x in range(gx0, gx1 + 1):
            for z in range(gz0, gz1 + 1):
                b.put(x, top - 1, z, st["base"])
                edge = x in (gx0, gx1) or z in (gz0, gz1)
                for k in range(4):
                    b.put(x, top + k, z, ("academy:curtain_glass_clear" if edge else "air"))
                b.put(x, top + 4, z, st["belt"])
        for x, z in ((gx0, gz0), (gx1, gz0), (gx0, gz1), (gx1, gz1)):
            for k in range(5): b.put(x, top + k, z, st["pier"])


def tower(b, style, rect, floors, steps=(), f0=2, mast=14, kind="spire", **opts):
    b.st = STYLES[style]
    st = b.st
    segs, cur, f = [], rect, f0
    for at, (a, c, d, e) in steps:
        segs.append((f, at - 1, cur))
        cur = (cur[0] + a, cur[1] + c, cur[2] + d, cur[3] + e)
        f = at
    segs.append((f, floors - 1, cur))
    for i, (fa, fb, rc) in enumerate(segs):
        o = dict(opts)
        if i % 2 == 1 and "tall" not in o: o["tall"] = True          # alternate masonry and glassy bands
        b.facade(rc, fa, fb, **o)
        nxt = segs[i + 1][2] if i + 1 < len(segs) else None
        b.roof(rc, fb, nxt, garden=0.0 if nxt is None else 0.0)
        if nxt:                                                      # planted setback terrace
            for x in range(rc[0] + 1, rc[2]):
                for z in range(rc[1] + 1, rc[3]):
                    if inside(grow(nxt, 1), x, z): continue
                    b.put(x, 4 * (fb + 1), z, "minecraft:grass_block")
                    if (x * 7 + z * 11) % 5 == 0: b.put(x, 4 * (fb + 1) + 1, z, LEAVES)
    crown(b, st, segs[-1][2], floors, kind, mast)
    return 4 * floors + 1 + mast + 14


# ---------------------------------------------------------------- bridges, colonnade, terrace, plaza
def bridge(b, axis, a0, a1, c, y0, style="lab"):
    """Enclosed glass skybridge with ribs and a neon underline; carves a doorway into both towers."""
    st = STYLES[style]
    def at(t, w): return (t, c + w) if axis == "x" else (c + w, t)
    for t in range(a0, a1 + 1):
        for w in range(-2, 3):
            x, z = at(t, w)
            b.put(x, y0, z, "academy:composite_floor")
            b.put(x, y0 - 1, z, st["dark"])
            if abs(w) == 2: b.put(x, y0 - 1, z, st["neon"] if t % 2 == 0 else st["dark"])
            for dy in range(1, 7):
                if abs(w) == 2: b.put(x, y0 + dy, z, st["pier"] if t % 4 == 0 else "academy:curtain_glass_smoke")
                else: b.put(x, y0 + dy, z, "air")
            b.put(x, y0 + 7, z, st["pier"] if t % 4 == 0 else "academy:curtain_glass_smoke")
            if w == 0 and t % 6 == 0: b.put(x, y0 + 6, z, "academy:lit_window")
    for end in (a0, a1):
        for t in range(end - 1, end + 2):
            for w in range(-1, 2):
                for dy in range(1, 5):
                    x, z = at(t, w)
                    b.put(x, y0 + dy, z, "air")


def colonnade(b, P):
    x0, z0, x1, z1 = P
    st = STYLES["academic"]
    for x in range(x0 + 2, x1 - 1, 6):
        if abs(x) <= 8: continue
        for y in range(0, 7): b.put(x, y, z0 - 3, st["pier"])
    for x in range(x0 + 2, x1 - 1):
        for z in range(z0 - 3, z0): b.put(x, 7, z, st["belt"])
        if x % 6 != (x0 + 2) % 6 and abs(x) > 8: b.put(x, 6, z0 - 3, st["neon"])
    for sx in (-6, 6):                                                    # grand entrance arch
        for y in range(0, 17): b.put(sx, y, z0 - 3, st["pier"])
    for x in range(-6, 7):
        for z in range(z0 - 3, z0 - 1):
            b.put(x, 17, z, "academy:white_panel_wall")
            b.put(x, 18, z, "academy:white_panel_wall")
        if abs(x) < 6: b.put(x, 16, z0 - 3, st["neon"])
    for x in range(-5, 6):                                                # crosswalk to the entrance
        for z in range(z0 - 14, z0 - 6):
            if x % 2 == 0: b.put(x, -1, z, "academy:road_marking")


def terrace(b, P, avoid):
    """Landscaped podium roof: plots of lawn with hedges, a tree, flower bed or gazebo, joined by tiled paths."""
    y = 8
    x0, z0, x1, z1 = P
    keep = [grow(r, 3) for r in avoid]
    for x in range(x0 + 2, x1 - 1):
        for z in range(z0 + 2, z1 - 1):
            if any(inside(k, x, z) for k in keep): continue
            cx, cz = x - x0, z - z0
            u, v = cx % 16, cz % 16
            if u < 3 or v < 3:
                b.put(x, y, z, "academy:plaza_tile" if (u in (1,) or v in (1,)) else "academy:composite_floor")
                if (u == 0 and v == 0): lamp(b, x, z) if False else None
                continue
            b.put(x, y, z, "minecraft:grass_block")
            hedge = u in (3, 15) or v in (3, 15)
            if hedge:
                b.put(x, y + 1, z, LEAVES)
                continue
            plot = ((cx // 16) * 7 + (cz // 16) * 13) % 4
            mid = u == 9 and v == 9
            if plot == 0 and mid:                                         # tree
                for k in range(1, 4): b.put(x, y + k, z, "minecraft:oak_log")
                for dx in range(-2, 3):
                    for dz in range(-2, 3):
                        for dy in range(3, 6):
                            if dx * dx + dz * dz + (dy - 4) ** 2 <= 5: b.put(x + dx, y + dy, z + dz, LEAVES)
            elif plot == 1 and 6 <= u <= 12 and 6 <= v <= 12:             # water pond with a rim
                rim = u in (6, 12) or v in (6, 12)
                b.put(x, y, z, "academy:white_panel_wall" if rim else "minecraft:water[level=0]")
                if rim: b.put(x, y + 1, z, "academy:white_panel_slab[type=bottom]")
            elif plot == 2 and (u + v) % 3 == 0:                         # flower bed
                b.put(x, y + 1, z, ("minecraft:poppy", "minecraft:azure_bluet", "minecraft:cornflower")[(u + v) // 3 % 3])
            elif plot == 3 and 6 <= u <= 12 and 6 <= v <= 12:             # gazebo
                b.put(x, y, z, "academy:plaza_tile")
                if (u, v) in ((6, 6), (12, 6), (6, 12), (12, 12)):
                    for k in range(1, 5): b.put(x, y + k, z, "academy:pillar_white")
                    b.put(x, y + 5, z, "academy:neon_cyan")
                b.put(x, y + 5, z, "academy:white_panel_slab[type=bottom]") if (u, v) not in ((6, 6), (12, 6), (6, 12), (12, 12)) else None


def fountain(b, cx, cz, hw, hd):
    """Rectangular plaza pool at plaza level (water contained by the surrounding paving) with sculpture columns."""
    for x in range(cx - hw - 1, cx + hw + 2):
        for z in range(cz - hd - 1, cz + hd + 2):
            for y in range(0, 14): b.put(x, y, z, "air")
            rim = x in (cx - hw - 1, cx + hw + 1) or z in (cz - hd - 1, cz + hd + 1)
            b.put(x, -1, z, "academy:white_panel_wall" if rim else "minecraft:water[level=0]")
            if rim: b.put(x, 0, z, "academy:white_panel_slab[type=bottom]")
    for k, x in enumerate(range(cx - hw + 3, cx + hw - 2, 6)):
        h = 3 + (k % 2) * 3
        for y in range(0, h): b.put(x, y, cz, "academy:pillar_white")
        b.put(x, h, cz, "academy:neon_cyan")


def bench(b, x, z, along_x=True):
    for d in range(3):
        b.put(x + d if along_x else x, 0, z if along_x else z + d, "academy:white_panel_slab[type=bottom]")
    for end in (0, 2):
        b.put(x + end if along_x else x, 0, z if along_x else z + end, "academy:dark_panel_wall")


def make_complex():
    b = Building("academic", 7)
    st = STYLES["academic"]
    P = (-56, -56, 56, 56)
    R = grow(P, 14)
    ROAD = grow(R, 8)
    for x in range(ROAD[0], ROAD[2] + 1):                                  # ground: plaza + ring road with kerb
        for z in range(ROAD[1], ROAD[3] + 1):
            if inside(R, x, z):
                band = (x // 4 + z // 4) % 2 == 0
                b.put(x, -1, z, st["plaza2"] if band else st["base"])
            else:
                mid = abs(x) == ROAD[2] - 4 or abs(z) == ROAD[3] - 4
                b.put(x, -1, z, "academy:road_marking" if mid and (x + z) % 6 < 3 else "academy:asphalt")
    b.podium(P)
    b.roof(P, 1, None, garden=0.0)
    colonnade(b, P)
    towers = []
    def add(rect, *a, **k): towers.append(rect); return tower(b, a[0], rect, *a[1:], **k)
    add((-12, -12, 12, 12), "academic", 70, steps=[(30, (4, 4, -4, -4)), (52, (4, 4, -4, -4))], fins=True, mast=50, kind="spire")
    add((-8, -50, 8, -34), "lab", 44, steps=[(30, (4, 4, -4, -4))], fins=True, mast=18, kind="spire")
    add((34, -8, 50, 8), "commercial", 52, steps=[(36, (0, 4, 0, -4)), (46, (4, 0, -4, 0))], fins=True, neon=True, mast=22, kind="fins")
    add((-12, 34, 12, 50), "residential", 36, steps=[(28, (0, 0, -8, 0))], balcony=True, kind="pavilion")
    add((-50, -8, -34, 8), "academic", 40, steps=[(30, (4, 4, -4, -4))], fins=True, mast=14, kind="spire")
    for rect, style, fl, kind, kw in (((-52, -52, -36, -36), "residential", 16, "pavilion", dict(balcony=True)),
                                      ((36, -52, 52, -36), "commercial", 20, "fins", dict(neon=True, fins=True, mast=8)),
                                      ((-52, 36, -36, 52), "lab", 18, "fins", dict(fins=True, mast=8)),
                                      ((36, 36, 52, 52), "academic", 14, "pavilion", dict(tall=True))):
        add(rect, style, fl, kind=kind, **kw)
    terrace(b, P, towers)
    for axis, a0, a1, c in (("z", -34, -12, 0), ("x", 12, 34, 0), ("z", 12, 34, 0), ("x", -34, -12, 0)):
        bridge(b, axis, a0, a1, c, 4 * 20)
    bridge(b, "x", 12, 34, 0, 4 * 38, "commercial")
    for x in range(R[0] + 3, R[2] - 2, 8):                                 # tree lines, lamps and benches
        for z in (R[1] + 3, R[3] - 3): tree(b, x, z)
        for z in (R[1] + 7, R[3] - 7): lamp(b, x + 4, z)
    for z in range(R[1] + 11, R[3] - 10, 8):
        for x in (R[0] + 3, R[2] - 3): tree(b, x, z)
    for x in range(P[0] + 8, P[2] - 8, 16):
        bench(b, x, P[3] + 8)
        bench(b, x, P[1] - 9 - 5 if abs(x) > 12 else P[1] - 30)
    for x in range(P[0], P[2] + 1, 8): lamp(b, x, P[3] + 4)
    fountain(b, 0, P[3] + 8 + 4, 14, 4)
    fountain(b, 0, P[1] - 26, 18, 5)
    return b.B


def main():
    B = make_complex()
    OUT.mkdir(parents=True, exist_ok=True)
    RES_STRUCT.mkdir(parents=True, exist_ok=True)
    size, n, pal = write_nbt(OUT / "grand_complex.nbt", B)
    (RES_STRUCT / "grand_complex.nbt").write_bytes((OUT / "grand_complex.nbt").read_bytes())
    print(f"grand_complex: size={size} blocks={n} palette={pal} min=({min(k[0] for k in B)},{min(k[1] for k in B)},{min(k[2] for k in B)})")
    if "--no-render" in sys.argv: return
    for night in (False, True):
        im = render(B, night, hw=5, hh=2.5, vz=5)
        im.save(OUT / f"grand_complex_{'night' if night else 'day'}.png")
        print("rendered", im.size)


if __name__ == "__main__":
    main()
