#!/usr/bin/env python3
"""Build authored city NPC skins, expressions and the attachment palette.

These atlases use the standard Minecraft player UV layout. Regenerate with
python3 scripts/city/npc_samples.py from source/ (standard library only).
"""
from pathlib import Path
import hashlib
import struct
import zlib

OUT = Path(__file__).resolve().parents[2] / "src/main/resources/assets/academy/textures/entity/city_npc"

# Skin tone, hair, eyes, jacket, shirt, trousers, shoes, accent, sleeve width.
LOOKS = {
    "student_bag":   ("edb58f", "49312b", "693c35", "2d3b69", "f1eae1", "313755", "2b232d", "a94361", 3),
    "student_sport": ("ce946e", "734939", "514035", "2d3b69", "f1eae1", "2f3753", "e2d7d2", "a94361", 4),
    "student_books": ("f2c5a9", "2c303c", "3a506b", "cbc1a8", "eee8e2", "343959", "3d3040", "a94361", 3),
    "commuter":      ("d0a18e", "333341", "433a49", "505769", "c3dae1", "434858", "342e38", "536e96", 4),
    "visitor":       ("a46b50", "372e36", "433534", "c97543", "f3d1a7", "dfc6a1", "45474b", "75c5a9", 4),
    "shop_clerk":    ("e4ad92", "553348", "573744", "e5d5be", "f7efe1", "555467", "3e363e", "428d7a", 3),
    "resident":      ("bd8b71", "564454", "433d46", "8c986f", "d4cfa7", "5e6264", "3b3439", "b7b78c", 3),
    "casual_visitor":("e4bc9b", "493344", "573e4f", "60979d", "e8dabb", "566982", "40384f", "ebba65", 3),
    "office_staff":  ("a97b66", "3c3744", "4c3944", "9d7a66", "e8dcd4", "504859", "40343a", "436974", 3),
    "vendor":        ("dfab81", "655143", "533d3a", "dec0a1", "f2dcca", "564b49", "39363e", "a55548", 4),
    "researcher":   ("bd977c", "423d4b", "54465c", "e0e2dd", "82a2a7", "5e6878", "3e4452", "4c8b97", 3),
    "guard":         ("c7a48b", "47363b", "4c414b", "364b67", "7d8c9b", "374355", "30343f", "d1b568", 4),
    "technician":    ("ad7863", "523c36", "433837", "a37037", "f0c26a", "4e4f54", "373740", "f4c65f", 4),
    "athlete":       ("deaa83", "5c3d31", "4b3432", "b55e57", "e6cec2", "54506d", "dac3bd", "edbd9c", 4),
    "performer":    ("f0baa0", "80594f", "5a4541", "744579", "e0bd9b", "564456", "3c333d", "f1bd67", 3),
    "airport_staff": ("ce9a76", "59413e", "4d3b42", "3d7080", "c1dce3", "45586c", "343840", "eeb873", 3),
    "student_blazer": ("edbea2", "40323e", "644c5d", "324868", "f2e8d9", "35445b", "352f3b", "c76c78", 3),
    "student_casual": ("ca9272", "584139", "5d483e", "729b9a", "f3e5ca", "465c75", "e3d9c8", "e6b56d", 4),
    "teacher":        ("ddb49d", "594750", "514350", "dedbd2", "9eb8bb", "474d5c", "403a42", "6c94a1", 3),
    "barista":        ("d49d7d", "704d3c", "5c4038", "efe4d0", "f8efe0", "514b4b", "46372e", "7a5642", 3),
    "rail_staff":     ("c99579", "343644", "414552", "2f506e", "d4e2e5", "34475b", "2b303e", "e2bd71", 4),
    "medic":          ("e1b9a1", "514450", "5c4b60", "e3ece9", "c4e0de", "829ba1", "e4e5df", "c97074", 3),
    "engineer":       ("b47d60", "4e3d34", "473e3c", "9b764d", "e5c385", "535c62", "393b40", "e5b352", 4),
    "traveler":       ("dcaa8b", "765546", "5f4a48", "a37e7d", "f4e2d3", "647588", "4e4545", "87b9a9", 3),
    "esper_uniform":  ("f1bfa6", "64404e", "a04b6a", "334362", "f1e5d8", "384258", "352c38", "77cfd7", 3),
    "esper_track":    ("bd8967", "3c3e4b", "62818d", "44767c", "f0e3d4", "344655", "ece2ce", "edbe72", 4),
    "esper_lab":      ("e6bd9e", "8b6a50", "628d89", "e4e9e4", "58899c", "485c72", "414452", "cbaa63", 3),
    "esper_work":     ("d2a28b", "564254", "8d657c", "4f527a", "ecdbcb", "49435d", "3e343f", "88c9c6", 4),
    "esper_art":      ("dba88f", "4d617c", "626d9b", "785679", "eddbc2", "514960", "423644", "e7bc73", 3),
    "esper_field":    ("ad7f64", "6c493d", "647e65", "728c78", "eee0c8", "545b6c", "4c3d3b", "85cbd2", 4),
}


def rgb(value):
    return bytes.fromhex(value)


def shade(color, delta):
    return bytes(max(0, min(255, channel + delta)) for channel in color)


class Atlas:
    def __init__(self, width, height):
        self.width = width
        self.height = height
        self.pixels = bytearray(width * height * 4)

    def rect(self, x, y, width, height, color):
        assert 0 <= x <= x + width <= self.width and 0 <= y <= y + height <= self.height
        row = (color + b"\xff") * width
        for yy in range(y, y + height):
            offset = (yy * self.width + x) * 4
            self.pixels[offset:offset + len(row)] = row

    def save(self, filename):
        def chunk(kind, data):
            return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data))

        stride = self.width * 4
        scanlines = b"".join(b"\0" + self.pixels[i:i + stride] for i in range(0, len(self.pixels), stride))
        header = struct.pack(">IIBBBBB", self.width, self.height, 8, 6, 0, 0, 0)
        filename.write_bytes(b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", header)
                             + chunk(b"IDAT", zlib.compress(scanlines)) + chunk(b"IEND", b""))


class CubeUv:
    def __init__(self, atlas, u, v, width, height, depth):
        self.atlas = atlas
        self.faces = {
            "top": (u + depth, v, width, depth),
            "bottom": (u + depth + width, v, width, depth),
            "left": (u, v + depth, depth, height),
            "front": (u + depth, v + depth, width, height),
            "right": (u + depth + width, v + depth, depth, height),
            "back": (u + 2 * depth + width, v + depth, width, height),
        }

    def mark(self, face, x, y, width, height, color):
        u, v, w, h = self.faces[face]
        assert 0 <= x <= x + width <= w and 0 <= y <= y + height <= h
        self.atlas.rect(u + x, v + y, width, height, color)

    def fill(self, face, color):
        _, _, w, h = self.faces[face]
        self.mark(face, 0, 0, w, h, color)

    def paint(self, color):
        for face, delta in zip(self.faces, (13, -19, -8, 0, -13, -6)):
            self.fill(face, shade(color, delta))


def head(atlas, name, skin, hair, iris):
    cube = CubeUv(atlas, 0, 0, 8, 8, 8)
    cube.paint(skin)
    cube.fill("top", shade(hair, 15))
    cube.fill("back", shade(hair, -9))
    for side in ("left", "right"):
        cube.mark(side, 0, 0, 8, 3, hair)
        cube.mark(side, 4 if side == "left" else 0, 3, 4, 3, shade(hair, -7))
        cube.mark(side, 3, 4, 1, 2, shade(skin, -17))
    cube.mark("front", 0, 0, 8, 2, hair)
    cube.mark("front", 0, 2, 2, 2, hair)
    cube.mark("front", 7, 2, 1, 2, shade(hair, -8))
    cube.mark("front", 2, 0, 3, 1, shade(hair, 15))
    if name in ("student_sport", "student_books", "shop_clerk", "student_blazer", "barista", "traveler", "esper_uniform", "esper_art"):
        cube.mark("front", 1, 2, 4, 1, hair)
    if name == "student_books":
        cube.mark("front", 1, 2, 2, 1, rgb("77b5cd"))
    for x in (1, 5):
        cube.mark("front", x, 3, 2, 1, shade(skin, -21))
        cube.mark("front", x, 4, 2, 1, rgb("fff0df"))
        cube.mark("front", x + 1, 4, 1, 1, iris)
    cube.mark("front", 3, 6, 2, 1, shade(skin, -32))
    cube.mark("front", 0, 5, 1, 1, shade(skin, 10))
    cube.mark("front", 7, 5, 1, 1, shade(skin, -9))
    cube.mark("top", 1, 1, 2, 1, shade(hair, 24))
    cube.mark("back", 2, 1, 1, 5, shade(hair, 8))
    if name in ("commuter", "researcher", "teacher"):
        cube.mark("front", 0, 4, 1, 1, rgb("403d4c"))
        cube.mark("front", 3, 4, 2, 1, rgb("403d4c"))
        cube.mark("front", 7, 4, 1, 1, rgb("403d4c"))
    fringe = CubeUv(atlas, 32, 0, 8, 8, 8)
    fringe.fill("top", shade(hair, 11))
    for face, delta in (("front", 4), ("left", 1), ("right", -5), ("back", -7)):
        fringe.mark(face, 0, 0, 8, 1, shade(hair, delta))
    fringe.mark("front", 0, 1, 2, 2, hair)


def clothing(atlas, name, skin, jacket, shirt, trousers, shoes, accent, arm_width):
    torso = CubeUv(atlas, 16, 16, 8, 12, 4)
    torso.paint(jacket)
    torso.mark("front", 1, 1, 1, 9, shade(jacket, 12))
    torso.mark("front", 6, 1, 1, 9, shade(jacket, -8))
    torso.mark("front", 2, 0, 4, 3, shirt)
    torso.mark("front", 3, 2, 2, 4, accent)
    torso.mark("front", 4, 6, 1, 3, shade(jacket, -20))
    torso.mark("front", 0, 11, 8, 1, shade(jacket, -16))
    if name.startswith("student"):
        torso.mark("front", 6, 3, 1, 2, rgb("d6c77f"))
    if name in ("student_bag", "student_sport"):
        for x in (1, 6):
            torso.mark("front", x, 0, 1, 7, rgb("424155"))
    elif name == "visitor":
        torso.mark("front", 3, 0, 2, 2, rgb("3b3840"))
        torso.mark("front", 1, 8, 2, 2, shade(jacket, -25))
        torso.mark("front", 5, 8, 2, 2, shade(jacket, -25))
    elif name in ("shop_clerk", "vendor", "barista"):
        torso.mark("front", 1, 2, 6, 10, accent)
        torso.mark("front", 1, 10, 6, 1, shade(accent, -16))
        torso.mark("front", 5, 3, 2, 1, rgb("fff0c8"))
        torso.mark("front", 2, 7, 4, 2, shade(accent, 13))
    elif name in ("guard", "researcher", "airport_staff", "teacher", "rail_staff", "medic"):
        torso.mark("front", 6, 4, 1, 2, accent)
        torso.mark("front", 1, 5, 2, 1, shade(jacket, -18))
    elif name in ("technician", "engineer"):
        torso.mark("front", 0, 7, 8, 1, rgb("ffe4a4"))
        torso.mark("back", 0, 7, 8, 1, rgb("ffe4a4"))
    if name.startswith("esper_"):
        # Distinct role garments and a common two-color training badge.
        torso.mark("front", 5, 3, 2, 2, accent)
        torso.mark("front", 6, 3, 1, 1, rgb("f7ebcd"))
        if name == "esper_track":
            for x in (0, 7): torso.mark("front", x, 0, 1, 10, shirt)
            torso.mark("back", 2, 2, 4, 3, accent)
        elif name == "esper_lab":
            torso.mark("front", 3, 3, 1, 8, shirt)
            torso.mark("front", 1, 7, 2, 2, shade(jacket, -20))
        elif name == "esper_field":
            torso.mark("front", 0, 6, 8, 2, accent)
            torso.mark("back", 0, 6, 8, 2, accent)
        elif name == "esper_art":
            torso.mark("front", 1, 7, 2, 3, accent)
            torso.mark("back", 2, 3, 4, 1, shade(accent, 15))
        elif name == "esper_uniform":
            torso.mark("front", 0, 10, 8, 1, accent)
        else:
            torso.mark("front", 2, 3, 1, 4, rgb("c5d8d6"))
    # A thin outer jacket layer gives collars, closures and pockets actual depth.
    coat = CubeUv(atlas, 16, 32, 8, 12, 4)
    coat.mark("front", 0, 0, 2, 2, shade(jacket, 16))
    coat.mark("front", 6, 0, 2, 2, shade(jacket, -9))
    coat.mark("front", 0, 8, 2, 2, shade(jacket, -14))
    coat.mark("front", 6, 8, 2, 2, shade(jacket, -14))
    for y in (4, 6, 8): coat.mark("front", 4, y, 1, 1, shade(accent, 8))
    coat.mark("back", 1, 1, 6, 1, shade(jacket, 8))
    if name in ("medic", "teacher", "researcher"):
        coat.mark("front", 5, 3, 2, 2, accent)
    if name in ("rail_staff", "guard", "airport_staff"):
        coat.mark("front", 1, 1, 2, 1, rgb("e8d493"))
    short_sleeves = {"student_sport", "student_casual", "visitor", "casual_visitor", "resident", "vendor", "athlete", "barista"}
    for uv in ((40, 16), (32, 48)):
        arm = CubeUv(atlas, *uv, arm_width, 12, 4)
        arm.paint(jacket)
        for face in ("front", "back", "left", "right"):
            width = arm.faces[face][2]
            if name in short_sleeves:
                arm.mark(face, 0, 8, width, 1, shirt)
                arm.mark(face, 0, 9, width, 3, shade(skin, -3))
            else:
                arm.mark(face, 0, 10, width, 1, shirt)
                arm.mark(face, 0, 11, width, 1, shade(skin, -3))
            if name in ("athlete", "technician", "engineer", "airport_staff", "rail_staff", "medic"):
                arm.mark(face, 0, 2, width, 1, accent)
        arm.fill("bottom", shade(skin, -14))
    for uv in ((0, 16), (16, 48)):
        leg = CubeUv(atlas, *uv, 4, 12, 4)
        leg.paint(trousers)
        for face in ("front", "back", "left", "right"):
            leg.mark(face, 0, 10, 4, 2, shoes)
        leg.mark("front", 1, 1, 1, 8, shade(trousers, 9))
        leg.mark("front", 1, 10, 2, 1, shade(shoes, 15))
        leg.fill("bottom", shade(shoes, -13))


def expression_faces(source, atlas, look, skin, iris):
    for expression in range(4):
        index = look * 4 + expression
        u, v = (index % 8) * 8, (index // 8) * 8
        for y in range(8):
            for x in range(8):
                offset = ((8 + y) * 64 + 8 + x) * 4
                atlas.rect(u + x, v + y, 1, 1, source.pixels[offset:offset + 3])
        if expression == 0:  # smile
            atlas.rect(u + 2, v + 6, 4, 1, shade(skin, -52))
            atlas.rect(u + 2, v + 5, 1, 1, shade(skin, -26))
            atlas.rect(u + 5, v + 5, 1, 1, shade(skin, -26))
            atlas.rect(u + 3, v + 7, 2, 1, shade(skin, -34))
        elif expression == 1:  # focus
            atlas.rect(u + 2, v + 3, 1, 1, iris)
            atlas.rect(u + 5, v + 3, 1, 1, iris)
            atlas.rect(u + 3, v + 6, 2, 1, shade(skin, -44))
        elif expression == 2:  # surprised
            atlas.rect(u + 1, v + 3, 2, 1, shade(skin, 12))
            atlas.rect(u + 5, v + 3, 2, 1, shade(skin, 12))
            atlas.rect(u + 3, v + 5, 2, 2, shade(skin, -70))
        else:  # blink
            atlas.rect(u + 1, v + 4, 2, 1, shade(skin, -45))
            atlas.rect(u + 5, v + 4, 2, 1, shade(skin, -45))


def detailed_skin(source, name, skin, hair, jacket, shirt, accent):
    """Author a 128px player atlas while retaining the standard 64px UV layout."""
    out = Atlas(128, 128)
    for y in range(64):
        for x in range(64):
            offset = (y * 64 + x) * 4
            if source.pixels[offset + 3]:
                out.rect(x * 2, y * 2, 2, 2, source.pixels[offset:offset + 3])
    digest=hashlib.sha256(name.encode()).digest()
    # Fine highlights and stitching are confined to opaque base-clothing pixels.
    for n in range(32):
        x=40 + (digest[n % len(digest)] + n * 7) % 16
        y=41 + (digest[(n + 9) % len(digest)] + n * 11) % 22
        offset=(y * 128 + x) * 4
        if out.pixels[offset + 3]:
            old=out.pixels[offset:offset + 3]
            out.rect(x,y,1,1,shade(old, 8 if n % 3 else -7))
    # Eye glints and a small lower lip preserve individuality at close range.
    out.rect(21,24,1,1,rgb("fff8ee")); out.rect(29,24,1,1,rgb("fff8ee"))
    out.rect(23,29,2,1,shade(skin,-20))
    for x in (19,23,27): out.rect(x,3,2,1,shade(hair,20))
    # The visible jacket layer adds cuffs, buttons, a badge and a breast pocket.
    out.rect(43,76,1,4,shade(shirt,10));out.rect(52,76,1,4,shade(shirt,-5))
    for y in (74,78,82): out.rect(48,y,1,1,shade(accent,18))
    if name.startswith("student"):
        out.rect(50,72,3,2,rgb("d9c98b"));out.rect(51,72,1,1,rgb("f7e5aa"))
    elif name in ("medic", "teacher", "researcher"):
        out.rect(50,72,3,3,shade(accent,10));out.rect(51,73,1,1,rgb("f5eee2"))
    elif name in ("rail_staff", "guard", "airport_staff"):
        out.rect(42,73,4,1,rgb("e9d190"));out.rect(43,75,2,1,rgb("e9d190"))
    elif name in ("barista", "shop_clerk", "vendor"):
        out.rect(44,82,7,1,shade(accent,-12));out.rect(46,83,3,2,shade(accent,15))
    return out


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    faces = Atlas(64, 128)
    for look, (name, config) in enumerate(LOOKS.items()):
        skin, hair, iris, jacket, shirt, trousers, shoes, accent = map(rgb, config[:-1])
        atlas = Atlas(64, 64)
        head(atlas, name, skin, hair, iris)
        clothing(atlas, name, skin, jacket, shirt, trousers, shoes, accent, config[-1])
        expression_faces(atlas, faces, look, skin, iris)
        detailed_skin(atlas, name, skin, hair, jacket, shirt, accent).save(OUT / (name + ".png"))
    faces.save(OUT / "expressions.png")
    palette = Atlas(128, 64)
    for i, color in enumerate(("2a2c39", "944a62", "35695f", "6b493e", "49312b", "73b7a1",
                               "d2ab69", "95c9d6", "efe1bf", "835446", "c97543", "303844",
                               "ebbb4c", "365a82", "e8e2d1", "a76b98")):
        palette.rect(i % 4 * 32, i // 4 * 16, 32, 16, rgb(color))
    palette.save(OUT / "accessories.png")


if __name__ == "__main__":
    main()
