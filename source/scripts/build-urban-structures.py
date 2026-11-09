#!/usr/bin/env python3
"""Create the two small 1.21.1 structure templates from deterministic block layouts."""
from pathlib import Path
import gzip
import json
import struct

ROOT = Path(__file__).resolve().parents[1] / "src/main/resources/data/academy/structure/urban"

def text(value):
    raw = value.encode("utf-8")
    return struct.pack(">H", len(raw)) + raw

def payload(kind, value):
    if kind == 1:
        return struct.pack(">b", value)
    if kind == 3:
        return struct.pack(">i", value)
    if kind == 5:
        return struct.pack(">f", value)
    if kind == 6:
        return struct.pack(">d", value)
    if kind == 8:
        return text(value)
    if kind == 9:
        element, entries = value
        return struct.pack(">bi", element, len(entries)) + b"".join(payload(element, entry) for entry in entries)
    if kind == 10:
        return b"".join(struct.pack(">b", t) + text(name) + payload(t, data)
                        for name, (t, data) in value.items()) + b"\0"
    raise ValueError(kind)

def entity(x, z, marker, name, health):
    name_json = json.dumps({"translate": "academy.urban.npc." + name}, separators=(",", ":"))
    return {
        "pos": (9, (6, [x + .5, 1.0, z + .5])),
        "blockPos": (9, (3, [x, 1, z])),
        "nbt": (10, {"id": (8, "minecraft:villager"), "CustomName": (8, name_json),
                     "CustomNameVisible": (1, 1), "PersistenceRequired": (1, 1),
                     "NoAI": (1, 1), "Invulnerable": (1, 1), "Health": (5, health),
                     "NeoForgeData": (10, {"academy:urban_witness": (8, marker)})}),
    }

def build(name, lab, facility=False):
    width, height, depth = 15, 10 if facility else 7, 15 if facility else 13
    palette = ["minecraft:air", "minecraft:smooth_stone", "minecraft:quartz_bricks",
               "minecraft:light_blue_stained_glass", "minecraft:sea_lantern", "minecraft:lectern",
               "minecraft:cyan_terracotta", "minecraft:bookshelf", "minecraft:polished_deepslate",
               "minecraft:lime_terracotta", "minecraft:yellow_terracotta", "minecraft:red_terracotta",
               "minecraft:magenta_terracotta", "minecraft:lever", "minecraft:amethyst_block",
               "minecraft:cartography_table", "minecraft:gold_block", "minecraft:iron_block"]
    if facility:
        palette.append("minecraft:smooth_stone_slab")
    blocks = {}
    def put(x, y, z, block):
        blocks[(x, y, z)] = palette.index("minecraft:" + block)
    for x in range(width):
        for z in range(depth):
            put(x, 0, z, "smooth_stone")
            for y in range(1, 6):
                edge = x in (0, width - 1) or z in (0, depth - 1)
                door = z == 0 and x in (6, 7, 8) and y <= 3
                glass = edge and y in (2, 3) and ((z in (0, depth-1) and x in (3, 4, 10, 11)) or
                                                     (x in (0, width-1) and z in (3, 4, 8, 9)))
                put(x, y, z, "air" if not edge or door else "light_blue_stained_glass" if glass else "quartz_bricks")
            put(x, 6, z, "quartz_bricks" if x in (0, width-1) or z in (0, depth-1) else "smooth_stone")
    for x, z in ((3, 3), (11, 3), (3, 9), (11, 9)):
        put(x, 6, z, "sea_lantern")
    put(7, 1, 8, "lectern")
    for x in (2, 3, 11, 12):
        put(x, 1, 11, "bookshelf" if not lab or x in (2, 3) else "polished_deepslate")
    put(2, 1, 2, "cyan_terracotta")
    put(12, 1, 2, "yellow_terracotta" if lab else "lime_terracotta")
    # The raised control desk can only be reached on a three-piece paper bridge.
    # Metal sockets make the real collision puzzle legible without item names/NBT tricks.
    for x in (10, 11, 12):
        put(x, 0, 4, "gold_block" if x == 11 else "iron_block")
        put(x, 1, 4, "air")
        put(x, 2, 4, "air")
    put(11, 1, 6, "quartz_bricks")
    put(11, 2, 6, "quartz_bricks")
    put(11, 3, 6, "cartography_table")
    if facility:
        # Twelve half-block rises from the floor to the roof. The three-wide
        # ramp has open headroom and no one-block jump bottlenecks.
        for x in range(1, 4):
            for z in range(2, 14):
                whole, half = divmod(z - 1, 2)
                for y in range(1, whole + 1):
                    put(x, y, z, "smooth_stone")
                if half:
                    put(x, whole + 1, z, "smooth_stone_slab")
                for y in range(whole + half + 1, height):
                    put(x, y, z, "air")
            # Clear the roof-edge landing as part of the structure, including
            # terrain that might otherwise cover the last step.
            for y in range(7, height):
                put(x, y, 14, "air")
        # Closed side at the low/middle ramp prevents pathfinding from trying
        # to jump across several steps from the chamber; the entrance stays open.
        for z in range(3, 10):
            for y in range(1, 7):
                put(4, y, z, "quartz_bricks")
        # Keep a broad, clearly marked approach on the chamber floor. It connects
        # the student's room to the low end of the stairs without furniture in
        # the way; the two-block clearance is written into the template itself.
        for x in range(4, 7):
            for z in range(1, 3):
                for y in range(1, 4):
                    put(x, y, z, "air")
        put(4, 0, 2, "cyan_terracotta")
        for x, color in ((4, "magenta_terracotta"), (6, "red_terracotta"),
                         (8, "yellow_terracotta"), (10, "lime_terracotta")):
            put(x, 0, 10, color)
            put(x, 1, 10, "lever")
        put(7, 1, 4, "amethyst_block")
    residents = ([entity(8, 7, "protocol_student", "protocol_student", 6.0)] if facility else
                 [entity(4, 7, "injured_student", "injured", 6.0),
                  entity(10, 7, "student_witness", "witness", 20.0)] if lab
                 else [entity(7, 5, "broadcast_guide", "guide", 20.0)])
    compound = {"DataVersion": (3, 3955), "size": (9, (3, [width, height, depth])),
                "palette": (9, (10, [{"Name": (8, item), **({"Properties": (10, {
                    "face": (8, "floor"), "facing": (8, "north"), "powered": (8, "true")})}
                    if item == "minecraft:lever" else {"Properties": (10, {"type": (8, "bottom"), "waterlogged": (8, "false")})}
                    if item == "minecraft:smooth_stone_slab" else {})} for item in palette])),
                "blocks": (9, (10, [{"pos": (9, (3, list(pos))), "state": (3, state)}
                                     for pos, state in sorted(blocks.items(), key=lambda item:(item[0][1],item[0][2],item[0][0]))])),
                "entities": (9, (10, residents))}
    result = b"\x0a\x00\x00" + payload(10, compound)
    ROOT.mkdir(parents=True, exist_ok=True)
    (ROOT / (name + ".nbt")).write_bytes(gzip.compress(result, compresslevel=9, mtime=0))

build("campus", False)
build("station", True)
build("facility", True, True)
