#!/usr/bin/env python3
"""Bake the Blender OBJ consist (tmp/city-prototype/train/shinkansen_consist.obj) into the compact mesh the in-game
renderer reads: src/main/resources/assets/academy/train/shinkansen.bin

Four parts, each a triangle soup with per-vertex position, normal and RGBA (a=254 marks emissive):
  0 head shell, 1 mid shell, 2 head interior, 3 mid interior.
Car-local frame: y up with 0 at rail top, +z forward (nose), car centred on the origin.

    python3 source/scripts/city/export_train_mesh.py [obj] [out.bin]
"""
import struct
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
OBJ = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "tmp/city-prototype/train/shinkansen_consist.obj"
OUT = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT / "source/src/main/resources/assets/academy/train/shinkansen.bin"
HEAD_CENTER, MID_CENTER = 12.5, (-25.4 + -0.4) / 2     # Blender y of each car's middle


def srgb(c): return int(round(255 * max(0.0, min(1.0, c)) ** (1 / 2.2)))


def read_mtl(path):
    mats, cur = {}, None
    for line in path.read_text().splitlines():
        p = line.split()
        if not p: continue
        if p[0] == "newmtl": cur = p[1]; mats[cur] = {"kd": (0.8, 0.8, 0.8), "ke": (0, 0, 0)}
        elif p[0] == "Kd" and cur: mats[cur]["kd"] = tuple(map(float, p[1:4]))
        elif p[0] == "Ke" and cur: mats[cur]["ke"] = tuple(map(float, p[1:4]))
    return mats


def load():
    mats = read_mtl(OBJ.with_suffix(".mtl"))
    verts, norms = [], []
    parts = {"HeadCar": [], "MidCar": []}
    obj, mat = None, None
    for line in OBJ.read_text().splitlines():
        p = line.split()
        if not p: continue
        if p[0] == "v": verts.append(tuple(map(float, p[1:4])))
        elif p[0] == "vn": norms.append(tuple(map(float, p[1:4])))
        elif p[0] == "o": obj = p[1]
        elif p[0] == "usemtl": mat = p[1]
        elif p[0] == "f" and obj:
            key = "HeadCar" if obj.startswith("HeadCar_") else "MidCar" if obj.startswith("MidCar_") else None
            if key is None: continue
            idx = []
            for tok in p[1:]:
                f = tok.split("/")
                idx.append((int(f[0]) - 1, int(f[2]) - 1 if len(f) > 2 and f[2] else -1))
            for i in range(1, len(idx) - 1):
                parts[key].append((idx[0], idx[i], idx[i + 1], mat))
    return verts, norms, parts, mats


def to_car(v, n, center):
    # OBJ was exported forward=-Z, up=Y: Blender-forward = -z_obj. Car-local: x kept, y up, z = forward minus centre.
    return (v[0], v[1], -v[2] - center), (n[0], n[1], -n[2])


def shell(verts, norms, tris, mats, center):
    out = []
    for a, b, c, m in tris:
        kd = mats.get(m, {"kd": (0.8, 0.8, 0.8)})["kd"]
        ke = mats.get(m, {"ke": (0, 0, 0)})["ke"]
        glow = max(ke) > 0.05
        col = tuple(srgb(x) for x in kd) + (254 if glow else 255,)
        pts = []
        for vi, ni in (a, b, c):
            p, nrm = to_car(verts[vi], norms[ni] if ni >= 0 else (0, 1, 0), center)
            pts.append((p, nrm, col))
        out.append(pts[::-1])                                # the z flip mirrors the winding
    return cut_doorways(out)


# One doorway on each side of each car, centred between two seat rows.  Clip
# triangles instead of merely hiding a decal: the old shell was one continuous
# surface behind the decorative doors.  Passenger transfer remains governed
# by the service phase; this opening is visual rather than a collision volume.
DOOR_Z0, DOOR_Z1 = 0.8, 2.2
DOOR_Y0, DOOR_Y1 = 1.04, 2.95


def clip_polygon(poly, axis, limit, keep_greater):
    def inside(p):
        return p[0][axis] >= limit if keep_greater else p[0][axis] <= limit
    def intersect(a, b):
        t = (limit - a[0][axis]) / (b[0][axis] - a[0][axis])
        pos = tuple(a[0][i] + t * (b[0][i] - a[0][i]) for i in range(3))
        normal = tuple(a[1][i] + t * (b[1][i] - a[1][i]) for i in range(3))
        return pos, normal, a[2]
    if not poly: return []
    out, prev = [], poly[-1]
    for cur in poly:
        if inside(cur) != inside(prev): out.append(intersect(prev, cur))
        if inside(cur): out.append(cur)
        prev = cur
    return out


def triangles(poly):
    out = []
    for i in range(1, len(poly) - 1):
        tri = [poly[0], poly[i], poly[i + 1]]
        a, b, c = (p[0] for p in tri)
        u = tuple(b[j] - a[j] for j in range(3))
        v = tuple(c[j] - a[j] for j in range(3))
        cross = (u[1] * v[2] - u[2] * v[1], u[2] * v[0] - u[0] * v[2], u[0] * v[1] - u[1] * v[0])
        if sum(x * x for x in cross) > 1e-16: out.append(tri)
    return out


def cut_doorways(source):
    out = []
    bounds = ((2, DOOR_Z0, True), (2, DOOR_Z1, False), (1, DOOR_Y0, True), (1, DOOR_Y1, False))
    for tri in source:
        xs = [v[0][0] for v in tri]
        if min(abs(x) for x in xs) < 1.30 or not (min(xs) > 0 or max(xs) < 0):
            out.append(tri); continue
        ys, zs = [v[0][1] for v in tri], [v[0][2] for v in tri]
        if max(ys) <= DOOR_Y0 or min(ys) >= DOOR_Y1 or max(zs) <= DOOR_Z0 or min(zs) >= DOOR_Z1:
            out.append(tri); continue
        remaining = tri
        for axis, value, greater in bounds:
            out += triangles(clip_polygon(remaining, axis, value, not greater))
            remaining = clip_polygon(remaining, axis, value, greater)
            if not remaining: break
    return out


def box(x0, y0, z0, x1, y1, z1, col):
    tris = []
    def quad(p, n):
        pts = [(q, n, col) for q in p]
        tris.append([pts[0], pts[1], pts[2]]); tris.append([pts[0], pts[2], pts[3]])
    quad([(x0, y0, z0), (x1, y0, z0), (x1, y0, z1), (x0, y0, z1)], (0, -1, 0))
    quad([(x0, y1, z1), (x1, y1, z1), (x1, y1, z0), (x0, y1, z0)], (0, 1, 0))
    quad([(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], (0, 0, 1))
    quad([(x1, y0, z0), (x0, y0, z0), (x0, y1, z0), (x1, y1, z0)], (0, 0, -1))
    quad([(x1, y0, z1), (x1, y0, z0), (x1, y1, z0), (x1, y1, z1)], (1, 0, 0))
    quad([(x0, y0, z0), (x0, y0, z1), (x0, y1, z1), (x0, y1, z0)], (-1, 0, 0))
    return tris


def interior(z_from, z_to):
    out = []
    floor, seat, rack, light = (176, 180, 186, 255), (28, 52, 120, 255), (150, 154, 160, 255), (255, 245, 220, 254)
    out += box(-1.55, 0.95, z_from, 1.55, 1.04, z_to, floor)
    # The outer shell culls inward faces. A separate liner keeps the interior enclosed
    # without closing off the sight line through the side windows.
    ceiling = (219, 226, 229, 255)
    wall = (193, 207, 215, 255)
    out += box(-1.20, 3.39, z_from, 1.20, 3.45, z_to, ceiling)
    for lo, hi in ((-1.53, -1.46), (1.46, 1.53)):
        out += box(lo, 1.04, z_from, hi, 1.70, DOOR_Z0, wall)
        out += box(lo, 1.04, DOOR_Z1, hi, 1.70, z_to, wall)
    for lo, hi in ((-1.28, -1.20), (1.20, 1.28)):
        out += box(lo, 3.05, z_from, hi, 3.43, z_to, ceiling)
    # These row centres are shared with TrainCarEntity's passenger attachment points.
    # The shorter nose car omits z=6, which lies beyond its interior bulkhead.
    for z in (-9, -6, -3, 0, 3, 6):
        if z - 0.4 < z_from or z + 0.4 > z_to: continue
        for x0, x1 in ((-1.20, -0.50), (0.25, 0.95)):
            out += box(x0, 1.04, z - 0.38, x1, 1.50, z + 0.38, seat)
            out += box(x0, 1.50, z + 0.30, x1, 2.05, z + 0.40, seat)
    for sx in (-1, 1):
        out += box(sx * 1.35 - 0.2, 3.00, z_from, sx * 1.35 + 0.2, 3.07, z_to, rack)   # luggage rack above eye level
    out += box(-0.12, 3.35, z_from, 0.12, 3.39, z_to, light)                # ceiling light strip
    return out


def main():
    verts, norms, parts, mats = load()
    head = shell(verts, norms, parts["HeadCar"], mats, HEAD_CENTER)
    mid = shell(verts, norms, parts["MidCar"], mats, MID_CENTER)
    meshes = [head, mid, interior(-11.5, 5.0), interior(-11.8, 11.8)]
    OUT.parent.mkdir(parents=True, exist_ok=True)
    with OUT.open("wb") as f:
        f.write(struct.pack(">i", len(meshes)))
        for m in meshes:
            f.write(struct.pack(">i", len(m)))
            for tri in m:
                for (p, n, c) in tri:
                    f.write(struct.pack(">6f4B", *p, *n, *c))
    print("parts", [len(m) for m in meshes], "bytes", OUT.stat().st_size)


if __name__ == "__main__":
    main()
