"""Author the wearable Uru gauntlet. Run with Python + Pillow; outputs are shipped in src/infinity.
Rig space is a left hand seen from behind: +Y fingertips, +Z back of hand, +X thumb.
"""

import itertools
import json
import math
import random
from pathlib import Path

from PIL import Image, ImageDraw

ASSETS = Path(__file__).resolve().parents[2] / "src/infinity/resources/assets/aca_infinity"
SIZE, TILE = 256, 32
# Shadow, body and polished edge of each alloy or lining.
METALS = {
    "brass": ("604225", "987044", "cda567"),
    "gold": ("78502d", "c29855", "ebcf91"),
    "bright": ("9a713f", "d9b574", "f6dfaa"),
    "edge": ("b0915c", "e5c788", "faeac2"),
    "bronze": ("493125", "765138", "ab7a50"),
    "groove": ("332820", "584434", "866846"),
    "leather": ("2c2322", "4d3931", "695041"),
    "lining": ("242023", "3d3031", "53403b"),
    "engrave": ("785534", "a0804e", "c8ae74"),
    "socket": ("23232a", "40363b", "685446"),
    "strap": ("3f2c28", "60443b", "8b6953"),
    "pin": ("795944", "b89467", "e4d0a4"),
}
STONES = {
    "space": ("163c8c", "2779d6", "50b6fa", "b3e9ff"),
    "reality": ("6f142c", "ad203d", "ff485a", "ffa2a7"),
    "power": ("4f257b", "8543c6", "bd71fc", "efd2ff"),
    "mind": ("896404", "d29b0c", "ffe34c", "fff7ba"),
    "time": ("105440", "1d9c61", "54e39d", "c7fce3"),
    "soul": ("8d341e", "d06628", "ff9e46", "ffe0a3"),
}
MATERIALS = list(METALS) + [f"{name}{i}" for name in STONES for i in range(4)]


def rgb(code):
    return bytes.fromhex(code)


def interpolate(first, second, weight):
    return tuple(round(a + (b - a) * weight) for a, b in zip(first, second))


def atlas():
    image = Image.new("RGB", (SIZE, SIZE), "#463729")
    for index, name in enumerate(MATERIALS):
        x0, y0 = index % 8 * TILE, index // 8 * TILE
        rng = random.Random(index + 1900)
        if name in METALS:
            shadow, body, light = map(rgb, METALS[name])
            for y in range(TILE):
                for x in range(TILE):
                    if name in ("leather", "lining", "strap"):
                        tone = math.cos((x - y * 2) * 1.7) * 0.13 + math.cos((x * 2 + y) * 1.7) * 0.07
                    else:
                        tone = (
                            0.32 * math.cos((x * 0.64 + y) * 0.24)
                            + 0.14 * math.cos((x * 0.52 + y) * 0.48)
                            + 0.10 * (1 - y / TILE)
                        )
                    tone += rng.uniform(-0.065, 0.065)
                    image.putpixel(
                        (x0 + x, y0 + y), interpolate(body, light if tone > 0 else shadow, abs(tone))
                    )
            if name in ("brass", "bronze", "gold"):
                draw = ImageDraw.Draw(image)
                for _ in range(6):
                    x, y = rng.randrange(4, 26), rng.randrange(3, 27)
                    draw.line(
                        (x0 + x, y0 + y, x0 + x + 2, y0 + y - 1), fill=interpolate(body, shadow, 0.22)
                    )
        else:
            color = rgb(STONES[name[:-1]][int(name[-1])])
            for y in range(TILE):
                for x in range(TILE):
                    image.putpixel(
                        (x0 + x, y0 + y), interpolate(color, (255, 247, 226), 0.06 * (1 - y / TILE))
                    )
    image.save(ASSETS / "textures/rig/infinity_gauntlet.png", optimize=True)


def rounded(values):
    return [round(v, 4) for v in values]


class Bone:
    def __init__(self, name, parent="root", pivot=(0, 0, 0), rotation=(0, 0, 0), glow=False):
        self.name, self.parent, self.pivot, self.rotation = name, parent, pivot, rotation
        self.glow, self.verts, self.indices, self.lookup = glow, [], [], {}

    def face(self, points, material, uv=None):
        if len(points) == 3:
            points = [*points, points[-1]]
            if uv:
                uv = [*uv, uv[-1]]
        tile = MATERIALS.index(material)
        uv = uv or [(0, 1), (0, 0), (1, 0), (1, 1)]
        for xyz, (u, v) in zip(points, uv, strict=True):
            vertex = tuple(
                rounded(
                    [*xyz, (tile % 8) * TILE + 1 + (TILE - 2) * u, (tile // 8) * TILE + 1 + (TILE - 2) * v]
                )
            )
            if vertex not in self.lookup:
                self.lookup[vertex] = len(self.verts)
                self.verts.append(vertex)
            self.indices.append(self.lookup[vertex])

    def export(self):
        return {
            "id": self.name,
            "parent": self.parent,
            "p": rounded(self.pivot),
            "r": rounded(self.rotation),
            "boxes": [],
            "mesh": {"vertices": self.verts, "quads": self.indices, "glow": self.glow},
        }


def octagon(x, y, z, rx, rz, flat=0.64):
    return [
        (x + a * rx, y, z + b * rz)
        for a, b in [
            (1, flat),
            (flat, 1),
            (-flat, 1),
            (-1, flat),
            (-1, -flat),
            (-flat, -1),
            (flat, -1),
            (1, -flat),
        ]
    ]


def join(body, lower, upper, materials, inside=False):
    for i in range(len(lower)):
        j = (i + 1) % len(lower)
        face = [lower[i], upper[i], upper[j], lower[j]]
        body.face(face[::-1] if inside else face, materials if isinstance(materials, str) else materials[i])


def cap(body, ring, material, upward):
    for i in range(1, len(ring) - 1, 2):
        pts = [ring[0], *ring[i : i + 3]]
        body.face(pts[::-1] if upward else pts, material)


def loft(body, x, z, sections, bands, ends=True, flat=0.64):
    rings = [octagon(x, y, z, rx, rz, flat) for y, rx, rz in sections]
    for lower, upper, mat in zip(rings, rings[1:], bands):
        join(body, lower, upper, mat)
    if ends:
        cap(body, rings[0], bands[0] if isinstance(bands[0], str) else bands[0][0], False)
        cap(body, rings[-1], bands[-1] if isinstance(bands[-1], str) else bands[-1][0], True)
    return rings


def panel(body, outline, surface, depth, material="gold", side="bronze", bevel="bright", inset=0.10):
    # Convex relief on a front or rear plate; winding follows its visible surface.
    area = sum(x * y1 - x1 * y for (x, y), (x1, y1) in zip(outline, outline[1:] + outline[:1]))
    if area * depth < 0:
        outline = list(reversed(outline))
    cx = sum(x for x, y in outline) / len(outline)
    cy = sum(y for x, y in outline) / len(outline)
    height = surface if callable(surface) else lambda x, y: surface
    inner = [(cx + (x - cx) * (1 - inset), cy + (y - cy) * (1 - inset)) for x, y in outline]
    low = [(x, y, height(x, y)) for x, y in outline]
    belt = [(x, y, height(x, y) + depth * 0.49) for x, y in outline]
    top = [(x, y, height(x, y) + depth) for x, y in inner]
    for i in range(len(top)):
        j = (i + 1) % len(top)
        if abs(depth) > 0.15:
            body.face([low[i], low[j], belt[j], belt[i]], side)
            body.face([belt[i], belt[j], top[j], top[i]], bevel)
        else:
            body.face([low[i], low[j], top[j], top[i]], bevel)
    xmin, xmax = min(x for x, y in outline), max(x for x, y in outline)
    ymin, ymax = min(y for x, y in outline), max(y for x, y in outline)
    center = (cx, cy, height(cx, cy) + depth)
    if len(top) == 4:
        facets = [top]
    else:
        facets = [[center, top[i], top[(i + 1) % len(top)]] for i in range(0, len(top), 2)]
        for i, face in enumerate(facets):
            if 2 * i + 1 < len(top):
                face.append(top[(2 * i + 2) % len(top)])
    for points in facets:
        body.face(
            points,
            material,
            [((x - xmin) / (xmax - xmin), (ymax - y) / (ymax - ymin)) for x, y, z in points],
        )


def oval(x, y, rx, ry, n=8):
    return [
        (x + rx * math.cos((i + 0.5) * 2 * math.pi / n), y + ry * math.sin((i + 0.5) * 2 * math.pi / n))
        for i in range(n)
    ]


def line_panel(body, a, b, width, surface, depth, material):
    dx, dy = b[0] - a[0], b[1] - a[1]
    k = width / (2 * math.hypot(dx, dy))
    ux, uy = -dy * k, dx * k
    xy = [(a[0] + ux, a[1] + uy), (a[0] - ux, a[1] - uy), (b[0] - ux, b[1] - uy), (b[0] + ux, b[1] + uy)]
    height = surface if callable(surface) else lambda x, y: surface
    body.face([(x, y, height(x, y) + depth) for x, y in xy], material)


class Side:
    """Local U/V relief mapped onto the left or right wall, with outward winding."""

    def __init__(self, body, sign, origin=(0, 0, 0)):
        self.body, self.sign, self.origin = body, sign, origin

    def face(self, points, material, uv=None):
        x, y, z = self.origin
        self.body.face([(x + self.sign * w, y + v, z - self.sign * u) for u, v, w in points], material, uv)


def socket(bones, parent, name, x, y, z, rx, ry):
    # A dark recessed well and the narrow bezel remain visible after a stone is removed.
    panel(parent, oval(x, y, rx * 1.21, ry * 1.20), z, 0.14, "bright", "groove", "edge", 0.10)
    panel(parent, oval(x, y, rx * 1.05, ry * 1.05), z + 0.141, 0.05, "socket", "bronze", "groove", 0.04)
    if name == "soul":
        for sx in (-1, 1):
            for sy in (-1, 1):
                # Four gold talons extend over the girdle, leaving the table visible.
                claw = [(0.63, 0.83), (0.85, 0.62), (0.77, 0.48), (0.62, 0.56)]
                points = [(x + sx * a * rx, y + sy * b * ry) for a, b in claw]
                panel(parent, points, z + 0.17, 0.30 * rx, "edge", "bronze", "bright", 0.23)
    gem = Bone("gem_" + name, parent.name, (x, y, z), glow=True)
    bones.append(gem)
    n = 8
    base = [(*v, z + 0.17) for v in oval(x, y, rx * 0.94, ry * 0.94, n)]
    girdle = [(*v, z + 0.17 + rx * 0.20) for v in oval(x, y, rx * 0.96, ry * 0.96, n)]
    crown = [
        (*v, z + 0.17 + rx * 0.54) for v in oval(x - rx * 0.08, y + ry * 0.08, rx * 0.57, ry * 0.56, n)
    ]
    table = (x - rx * 0.13, y + ry * 0.19, z + 0.17 + rx * 0.62)
    for i in range(n):
        j = (i + 1) % n
        gem.face([base[i], base[j], girdle[j], girdle[i]], name + "0")
        gem.face([girdle[i], girdle[j], crown[j]], name + str((1, 2, 3, 2, 1, 0, 1, 1)[i]))
        gem.face([girdle[i], crown[j], crown[i]], name + str((2, 3, 2, 1, 2, 1, 0, 2)[i]))
        gem.face([table, crown[i], crown[j]], name + str((2, 3, 3, 2, 1, 1, 2, 2)[i]))


def build():
    root = Bone("root", None)
    bones = [root]
    # Flared, hollow cuff, rolled lip and dark lining visible from the sleeve end.
    sec = [
        (-7.15, 3.38, 2.80),
        (-7.05, 3.61, 2.98),
        (-6.78, 3.61, 2.98),
        (-6.59, 3.44, 2.86),
        (-5.91, 3.38, 2.79),
        (-5.73, 3.39, 2.81),
        (-5.54, 3.29, 2.72),
        (-2.25, 2.54, 2.15),
        (-2.12, 2.59, 2.19),
        (-1.94, 2.51, 2.12),
        (-1.20, 2.37, 2.0),
    ]
    rings = loft(
        root,
        0,
        0,
        sec,
        [
            "bright",
            "edge",
            "brass",
            "bronze",
            "bright",
            "brass",
            ["brass", "gold", "brass", "bronze", "brass", "bronze", "brass", "gold"],
            "bright",
            "gold",
            "bronze",
        ],
        False,
    )
    inner = octagon(0, -7.15, 0, 2.96, 2.41)
    inner_end = octagon(0, -1.20, 0, 2.05, 1.72)
    join(root, inner, inner_end, "lining", True)
    join(root, inner, rings[0], "brass")
    join(root, rings[-1], inner_end, "leather")
    wrist = lambda x, y: 2.73 - 0.185 * (y + 5.54)
    for sign in (-1, 1):
        blade = [(0.28, -6.50), (1.85, -6.34), (2.30, -5.91), (1.71, -2.48), (0.76, -1.46), (0.56, -3.14)]
        panel(root, [(x * sign, y) for x, y in blade], wrist, 0.17, "gold", "groove", "bright", 0.10)
        for a, b, width, mat in [
            ((0.58, -6.09), (1.65, -5.82), 0.09, "edge"),
            ((1.63, -5.68), (1.13, -3.10), 0.09, "bronze"),
            ((0.75, -5.68), (0.91, -4.40), 0.12, "engrave"),
        ]:
            line_panel(
                root,
                (a[0] * sign, a[1]),
                (b[0] * sign, b[1]),
                width,
                lambda x, y: wrist(x, y) + 0.171,
                0.026,
                mat,
            )
        panel(
            root,
            oval(sign * 1.72, -5.91, 0.14, 0.14, 6),
            lambda x, y: wrist(x, y) + 0.171,
            0.07,
            "pin",
            "groove",
            "edge",
        )

    def cheek(u, y):
        return 3.29 - 0.226 * (y + 5.54)

    for sign in (-1, 1):
        side = Side(root, sign)
        panel(
            side,
            [
                (-1.22, -5.82),
                (-0.90, -6.32),
                (0.89, -6.32),
                (1.20, -5.72),
                (0.78, -2.86),
                (0, -2.35),
                (-0.84, -2.87),
            ],
            cheek,
            0.11,
            "brass",
            "groove",
            "bright",
            0.13,
        )
        for a, b in [
            ((-0.71, -5.60), (0, -5.08)),
            ((0, -5.08), (0.71, -5.60)),
            ((-0.53, -4.78), (0, -4.40)),
            ((0, -4.40), (0.53, -4.78)),
        ]:
            line_panel(side, a, b, 0.10, lambda u, y: cheek(u, y) + 0.114, 0.045, "engrave")
        for y in (-5.92, -2.90):
            panel(
                side,
                oval(0, y, 0.15, 0.15, 6),
                lambda u, y: cheek(u, y) + 0.115,
                0.07,
                "pin",
                "groove",
                "edge",
            )
    crest = [(0, -6.64), (0.40, -5.80), (0.28, -2.82), (0, -1.98), (-0.28, -2.82), (-0.40, -5.80)]
    panel(root, crest, wrist, 0.24, "bright", "groove", "edge", 0.24)
    loft(
        root,
        0,
        0,
        [(-1.98, 2.29, 1.90), (-1.29, 2.35, 1.89), (-0.49, 2.35, 1.79)],
        ["lining", "leather"],
        False,
    )
    for y, rx, rz in [(-1.62, 2.46, 2.09), (-1.16, 2.42, 1.97), (-0.73, 2.48, 1.92)]:
        loft(
            root,
            0,
            0,
            [(y - 0.17, rx - 0.12, rz - 0.12), (y - 0.05, rx, rz), (y + 0.15, rx, rz)],
            ["brass", "strap"],
            False,
        )
    # Broad knuckles taper at the wrist. The palm has separate layered gripping pads.
    loft(
        root,
        0,
        -0.02,
        [
            (-0.81, 2.28, 1.72),
            (-0.30, 2.55, 1.78),
            (1.25, 2.94, 1.77),
            (3.45, 2.95, 1.62),
            (4.05, 2.68, 1.23),
        ],
        ["lining", "leather", "leather", "bronze"],
    )
    shell = [
        (-2.63, 3.32),
        (-2.59, 1.68),
        (-1.45, -0.18),
        (0, -0.62),
        (1.52, -0.16),
        (2.59, 1.59),
        (2.64, 3.45),
        (1.45, 3.97),
        (-1.55, 3.88),
    ]
    back = lambda x, y: 1.77 - 0.03 * max(0, y - 1)
    panel(root, shell, back, 0.32, "brass", "groove", "bright")
    for sign in (-1, 1):
        wing = [(0.64, 2.82), (1.66, 3.70), (2.40, 3.42), (2.31, 1.87), (1.08, 0.16), (0.62, 0.46)]
        panel(
            root,
            [(sign * x, y) for x, y in wing],
            lambda x, y: back(x, y) + 0.33,
            0.19,
            "gold",
            "bronze",
            "bright",
        )
        line_panel(
            root,
            (sign * 2.12, 2.83),
            (sign * 1.28, 1.12),
            0.095,
            lambda x, y: back(x, y) + 0.522,
            0.035,
            "edge",
        )
        pad = [(0.15, 2.7), (0.48, 0.06), (1.36, -0.05), (2.45, 1.34), (2.12, 2.76)]
        panel(root, [(sign * x, y) for x, y in pad], -1.77, -0.13, "strap", "leather", "bronze")
    socket(bones, root, "soul", 0, 1.55, 2.12, 0.91, 1.24)
    # Engraved channels on the hand guard follow the individual stones' installed states.
    for name, x, y in [
        ("space", 1.99, 3.69),
        ("reality", 0.68, 3.90),
        ("power", -0.65, 3.82),
        ("mind", -1.95, 3.53),
    ]:
        vein = Bone("vein_" + name, "root", (x, y, 2.35), glow=True)
        bones.append(vein)
        if abs(x) > 1:
            path = [(x * 0.97, y - 0.38), (x * 0.79, y - 0.71), (x * 0.62, y - 0.88)]
            surface = lambda xx, yy: back(xx, yy) + 0.525
        else:
            path = [(x * 1.03, y - 0.36), (x * 0.80, y - 0.61), (x * 0.72, y - 0.76)]
            surface = lambda xx, yy: back(xx, yy) + 0.327
        for a, b in itertools.pairwise(path):
            line_panel(root, a, b, 0.15, surface, 0.025, "groove")
            line_panel(
                vein, a, b, 0.062, lambda xx, yy, base=surface: base(xx, yy) + 0.026, 0.012, name + "1"
            )
    sizes = [
        ("index", 2.00, 3.50, 1.96, 1.47, 1.12, 0.64, -3, "space"),
        ("middle", 0.67, 3.75, 2.15, 1.57, 1.18, 0.66, -0.7, "reality"),
        ("ring", -0.68, 3.63, 2.01, 1.47, 1.08, 0.63, 1.7, "power"),
        ("pinky", -1.98, 3.35, 1.64, 1.18, 0.93, 0.56, 5, "mind"),
    ]
    for name, x, y, a, b, c, hw, splay, stone in sizes:
        prev = "root"
        for idx, (suffix, span) in enumerate(zip(["", "_mid", "_tip"], [a, b, c])):
            bone = Bone(
                "finger_" + name + suffix,
                prev,
                (x, y, 0.22),
                ([-9, -15, -12][idx], 0, splay if idx == 0 else 0),
            )
            bones.append(bone)
            finger_segment(bone, x, y, 0.22, span, hw * (1 - 0.10 * idx), idx)
            if idx == 0:
                socket(bones, bone, stone, x, y + 0.46, 1.43, 0.43 if name != "pinky" else 0.37, 0.55)
            prev = bone.name
            y += span
    x, y, z = 2.47, 0.46, -0.12
    thumb = Bone("thumb", "root", (x, y, z), (-15, 10, -39))
    bones.append(thumb)
    finger_segment(thumb, x, y, z, 2.34, 0.75, 0)
    socket(bones, thumb, "time", x, y + 1.16, 1.10, 0.50, 0.62)
    tip = Bone("thumb_tip", "thumb", (x, y + 2.34, z), (-18, 0, 10))
    bones.append(tip)
    finger_segment(tip, x, y + 2.34, z, 1.62, 0.63, 2)
    return bones


def finger_segment(bone, x, y, z, size, hw, idx):
    thick = [0.89, 0.68, 0.56][idx]
    loft(
        bone,
        x,
        z,
        [
            (y - 0.15, hw * 0.74, thick * 0.74),
            (y + 0.09, hw, thick),
            (y + size * 0.72, hw * 0.94, thick * 0.90),
            (y + size, hw * (0.64 if idx == 2 else 0.77), thick * 0.68),
        ],
        ["groove", ["brass", "gold", "brass", "bronze", "strap", "leather", "strap", "bronze"], "strap"],
    )
    plate = [
        (x - hw * 0.90, y + 0.12),
        (x - hw * 0.54, y - 0.04),
        (x + hw * 0.54, y - 0.04),
        (x + hw * 0.90, y + 0.12),
        (x + hw * 0.78, y + size * 0.74),
        (x + hw * 0.42, y + size * 0.94),
        (x - hw * 0.44, y + size * 0.94),
        (x - hw * 0.78, y + size * 0.74),
    ]
    surface = lambda xx, yy: z + thick * (1 - 0.14 * (yy - y) / size) - 0.06
    panel(
        bone,
        plate,
        surface,
        0.28 if idx == 0 else 0.21,
        ["gold", "brass", "gold"][idx],
        "bronze",
        "bright",
        0.16,
    )
    if idx < 2:
        for sign in (-1, 1):
            side = Side(bone, sign, (x, y, z))
            panel(
                side, oval(0, size * 0.16, 0.22, 0.22, 8), hw * 0.91, 0.10, "pin", "groove", "bright", 0.22
            )
    if idx != 0:
        line_panel(
            bone,
            (x - hw * 0.62, y + size * 0.29),
            (x + hw * 0.61, y + size * 0.25),
            0.095,
            lambda xx, yy: surface(xx, yy) + 0.211,
            0.034,
            "edge",
        )
    # One dark rim at the base of each joint reads at first-person distance.
    if idx < 2:
        loft(
            bone,
            x,
            z,
            [(y + size - 0.18, hw * 0.90, thick * 0.81), (y + size - 0.08, hw * 0.92, thick * 0.84)],
            ["bronze"],
            False,
        )


if __name__ == "__main__":
    atlas()
    bones = build()
    model = {"texture_size": SIZE, "height": 15.8, "bones": [bone.export() for bone in bones]}
    path = ASSETS / "rigs/infinity_gauntlet.json"
    path.write_text(json.dumps(model, separators=(",", ":")) + "\n", encoding="utf-8")
    print(f"{len(bones)} bones, {sum(len(b.indices) // 4 for b in bones)} quads")
