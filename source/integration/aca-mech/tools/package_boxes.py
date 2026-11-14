"""Pack the box UVs, write the editable Blockbench projects, then sync them into the mod."""
from __future__ import annotations
from collections import Counter
from hashlib import sha256
from pathlib import Path
from uuid import uuid5, NAMESPACE_URL
import argparse
import base64
import json
import math
import re
import random

from PIL import Image, ImageChops, ImageColor, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art"
ASSETS = ROOT.parent.parent / "src" / "mech" / "resources" / "assets" / "acamech"
NAMES = {"atlas": "ATLAS / 09", "aether": "AETHER / 02", "seraph": "SERAPH / 03"}
WIDTH = 512


def jsonwrite(path, object, compact=False):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(object, ensure_ascii=False, indent=None if compact else 2,
                               separators=(",", ":") if compact else None) + "\n", encoding="utf-8")


def rotate_x_y(point):
    return [-point[0], -point[1], point[2]]


def dims(box):
    return [b - a for a, b in zip(box["lo"], box["hi"])]


def tile_bounds(uv, dim):
    u, v = uv
    x, y, z = dim
    return {
        "west": (u, v + z, u + z, v + z + y),
        "north": (u + z, v + z, u + z + x, v + z + y),
        "east": (u + z + x, v + z, u + 2 * z + x, v + z + y),
        "south": (u + 2 * z + x, v + z, u + 2 * z + 2 * x, v + z + y),
        "up": (u + z, v, u + z + x, v + z),
        "down": (u + z + x, v, u + z + 2 * x, v + z),
    }


def paint_tile(image, bounds, base, rng, face, style):
    a, b, c, d = bounds
    w, h = c - a, d - b
    tile = Image.new("RGBA", (w, h))
    dr = ImageDraw.Draw(tile)
    px = tile.load()
    shift = {"up": 15, "down": -19, "north": 4, "east": -5, "west": -9, "south": -8}[face]
    def shade(add, source=base):
        return tuple(max(0, min(255, k + add)) for k in source) + (255,)
    for y in range(h):
        for x in range(w):
            px[x, y] = shade(rng.choice((-4, -2, -1, 0, 0, 0, 2, 4)) + shift)
    if style == "stripes":
        for y in range(h):
            for x in range(w):
                if face in ("north", "south") and ((x - y) // 2) % 3 == 1:
                    px[x, y] = shade(-25, (39, 44, 45))
    elif style in ("light", "glass"):
        if w >= 3 and h >= 2:
            dr.line((0, 0, w - 1, 0), shade(17 if style == "light" else 9))
            dr.line((0, h - 1, w - 1, h - 1), shade(-11))
    elif w >= 4 and h >= 4:
        dr.line((1, 0, w - 2, 0), shade(shift + 19))
        dr.line((0, 1, 0, h - 2), shade(shift + 13))
        dr.line((1, h - 1, w - 2, h - 1), shade(shift - 22))
        dr.line((w - 1, 1, w - 1, h - 2), shade(shift - 15))
        if style == "plate" and w >= 11 and h >= 6:
            dr.line((2, h - 3, min(w - 3, int(w * .78)), h - 3), shade(shift - 13))
            for x in (2, w - 3):
                px[x, 1] = shade(shift + 29)
                px[x, 2] = shade(shift - 26)
        elif style == "vents" and w >= 4:
            for y in range(2, h - 1, 2):
                dr.line((1, y, w - 2, y), shade(-12))
    image.paste(tile, (a, b))


def stable_id(frame, part):
    return str(uuid5(NAMESPACE_URL, "https://aca-mech.local/" + frame + "/" + part))


def make_project(frame, model):
    parts = [(bone, i, b) for bone, group in model["bones"].items() for i, b in enumerate(group["boxes"])]
    parts.sort(key=lambda x: (-sum(dims(x[2])[1:]), x[0], x[1]))
    x = y = 2
    row = 0
    for _, _, box in parts:
        dx, dy, dz = dims(box)
        w, h = 2 * (dx + dz), dy + dz
        if x + w + 2 > WIDTH:
            x, y, row = 2, y + row, 0
        box["uv"] = [x, y]
        x += w + 2
        row = max(row, h + 2)
    height = max(128, 2 ** math.ceil(math.log2(y + row + 2)))
    atlas = Image.new("RGBA", (WIDTH, height))
    rng = random.Random("acamech/" + frame)
    texture_path = ART / (frame + ".png")
    bounds = [(box["lo"], box["hi"]) for _, _, box in parts]
    groups = {}
    elements = []
    for bone, group in model["bones"].items():
        groups[bone] = {"name": bone, "origin": rotate_x_y(group["pivot"]), "rotation": [0, 0, 0],
                        "uuid": stable_id(frame, bone), "color": 0, "isOpen": bone == "root", "children": []}
    for bone, group in model["bones"].items():
        if group["parent"]:
            groups[group["parent"]]["children"].append(groups[bone])
    for bone, i, box in parts:
        uv = tile_bounds(box["uv"], dims(box))
        tint = ImageColor.getrgb(model["palette"][box["material"]])
        base = tuple(int(v * .50) for v in tint) if box["glow"] else tint
        for face, uvrect in uv.items():
            paint_tile(atlas, uvrect, base, rng, face, box["style"])
        lo, hi = box["lo"], box["hi"]
        pos = box.get("pivot", group["pivot"])
        cube_uuid = stable_id(frame, f"{bone}/{i}/{box['label']}")
        cube = {"name": ("emissive__" if box["glow"] else "") + box["label"], "type": "cube", "uuid": cube_uuid,
                "box_uv": True, "autouv": 0, "from": [-hi[0], -hi[1], lo[2]], "to": [-lo[0], -lo[1], hi[2]],
                "origin": rotate_x_y(pos), "rotation": [0, 0, box.get("z", 0)],
                "uv_offset": box["uv"],
                "faces": {face: {"uv": list(rect), "texture": 0} for face, rect in uv.items()}}
        groups[bone]["children"].append(cube_uuid)
        elements.append(cube)
    atlas.save(texture_path)
    texture = {"name": frame + ".png", "relative_path": texture_path.name, "id": "0",
               "uuid": stable_id(frame, "atlas.png"), "width": WIDTH, "height": height,
               "uv_width": WIDTH, "uv_height": height, "visible": True, "mode": "bitmap",
               "source": "data:image/png;base64," + base64.b64encode(texture_path.read_bytes()).decode()}
    bb = {"meta": {"format_version": "4.10", "model_format": "modded_entity", "box_uv": True},
          "name": NAMES[frame], "model_identifier": frame, "modded_entity_version": "1.17",
          "resolution": {"width": WIDTH, "height": height},
          "elements": elements, "outliner": [groups["root"]], "textures": [texture]}
    jsonwrite(ART / (frame + ".bbmodel"), bb)
    return {"bones": len(groups), "cubes": len(elements), "texture": [WIDTH, height],
            "coordinate_bounds_unrotated": [[min(a[0][i] for a in bounds) for i in range(3)],
                                             [max(a[1][i] for a in bounds) for i in range(3)]],
            "original": model["source"]}


def export_runtime(frame, project):
    """Read the saved Blockbench project. Model edits stay independent of the GLB fitter."""
    bb = json.loads(project.read_text())
    if bb["meta"].get("model_format") != "modded_entity" or not bb["meta"].get("box_uv", True):
        raise ValueError(f"{project.name} must stay a Java Modded Entity with Box UV")
    tex = bb["textures"]
    if len(tex) != 1:
        raise ValueError(f"{project.name} must use one Box UV atlas")
    png = project.parent / (tex[0].get("relative_path") or tex[0]["name"])
    if png.is_file():
        img = Image.open(png).convert("RGBA")
    else:
        from io import BytesIO
        img = Image.open(BytesIO(base64.b64decode(tex[0]["source"].split(",", 1)[1]))).convert("RGBA")
    w, h = bb["resolution"]["width"], bb["resolution"]["height"]
    scale = img.width // w
    if scale < 1 or img.size != (w * scale, h * scale):
        raise ValueError(f"Atlas {img.size} must be an integer multiple of the logical Box UV resolution {(w, h)}")
    out_dir = ASSETS / "textures" / "entity"
    out_dir.mkdir(parents=True, exist_ok=True)
    img.save(out_dir / (frame + ".png"))
    glow = Image.new("RGBA", img.size)
    finish = Image.new("RGB", img.size)
    finishes = {"polished": (206, 223, 39), "brushed": (143, 141, 102), "glass": (111, 220, 28)}
    machined = any(cube["name"].startswith("metal_satin__") for cube in bb["elements"])
    if machined:
        # R is reflectivity, G is the highlight sharpness, B is grain direction/strength.
        finishes = {"polished": (231, 226, 17), "brushed": (168, 142, 153),
                    "glass": (106, 228, 8), "satin": (102, 104, 94)}
    registered = {c["uuid"]: c for c in bb["elements"]}
    groups_by_id = {b["uuid"]: b for b in bb.get("groups", [])}
    result = []
    used, names = Counter(), set()
    def tree(node, parent):
        if isinstance(node, str):
            node = groups_by_id.get(node)
        if not isinstance(node, dict):
            raise ValueError(f"Expected a group below {parent}")
        if node["uuid"] in groups_by_id:
            node = {**groups_by_id[node["uuid"]], **node}
        bone = node["name"]
        if not re.fullmatch(r"[a-z][a-z0-9_.LR]*", bone) or bone in names:
            raise ValueError(f"Invalid or duplicate bone: {bone}")
        names.add(bone)
        info = {"id": bone, "parent": parent, "pivot": rotate_x_y(node["origin"]),
                "rotation": rotate_x_y(node.get("rotation", [0, 0, 0])), "cubes": []}
        result.append(info)
        for child in node["children"]:
            if not isinstance(child, str) or child in groups_by_id:
                tree(child, bone)
                continue
            cube = registered[child]
            used[child] += 1
            if cube.get("type") != "cube" or not cube.get("box_uv", True) or cube.get("mirror_uv") or cube.get("inflate", 0):
                raise ValueError(f"Unsupported box: {bone}/{cube['name']}; use uninflated, unmirrored box UV cubes")
            lo, hi = cube["from"], cube["to"]
            dim = [int(round(b-a)) for a, b in zip(lo, hi)]
            if min(dim) < 1 or any(not math.isclose(x, y - z, abs_tol=.001) for x,y,z in zip(dim,hi,lo)):
                raise ValueError(f"Use positive integer cube dimensions: {cube['name']}")
            u, v = cube["uv_offset"]
            if min(u, v) < 0 or u + 2 * (dim[0] + dim[2]) > w or v + dim[1] + dim[2] > h:
                raise ValueError(f"Box UV out of atlas: {cube['name']}")
            emissive = cube["name"].startswith("emissive__")
            item = {"from": [-hi[0], -hi[1], lo[2]], "size": dim, "uv": [int(u), int(v)]}
            if any(cube.get("rotation", [])):
                item["pivot"] = rotate_x_y(cube["origin"])
                item["rotation"] = rotate_x_y(cube["rotation"])
            surface = re.match(r"metal_(polished|brushed|glass|satin)__", cube["name"])
            material = finishes[surface[1]] if surface else ((26, 68, 0) if machined else (52, 80, 65))
            if emissive:
                material = (0, 0, 0)
            faces = tile_bounds((int(u), int(v)), dim)
            for direction, (a, b, c, d) in faces.items():
                face = (a * scale, b * scale, c * scale, d * scale)
                if machined and surface and not emissive:
                    gray = img.crop(face).convert("L")
                    micro = ImageChops.subtract(gray, gray.filter(ImageFilter.GaussianBlur(1.3)), scale=1.9, offset=material[1])
                    grain = material[2]
                    if grain > 0 and direction in ("up", "down"):
                        grain = 255 - grain
                    finish.paste(Image.merge("RGB", (Image.new("L", gray.size, material[0]), micro,
                                                         Image.new("L", gray.size, grain))), face[:2])
                else:
                    finish.paste(material, face)
            if emissive:
                item["glow"] = True
                halo = re.match(r"emissive__halo_(core|small_core|soft|wing|wing_back)__", cube["name"])
                if halo:
                    item["halo"] = halo[1]
                for a, b, c, d in faces.values():
                    area = (a * scale, b * scale, c * scale, d * scale)
                    block = img.crop(area)
                    if scale == 1:
                        block = block.point(lambda v: min(255, round(2.16 * v)))
                    else:
                        # Bright swatch pixels carry energy; keep surrounding metal out of the light pass.
                        pixels = []
                        for r, g, b, alpha in block.getdata():
                            maximum, minimum = max(r, g, b), min(r, g, b)
                            strength = max(0, min(1, (maximum - 86) / 130))
                            strength *= max(0, min(1, (maximum - minimum + (61 if minimum > 174 else 18)) / 74))
                            pixels.append((round(r * strength), round(g * strength), round(b * strength),
                                           round(alpha * strength)))
                        block.putdata(pixels)
                    glow.paste(block, area[:2])
            info["cubes"].append(item)
    for node in bb["outliner"]:
        tree(node, None)
    if not result or result[0]["id"] != "root" or sum(b["parent"] is None for b in result) != 1:
        raise ValueError("The model must have a single root named root")
    if set(registered) != set(used) or max(used.values(), default=0) != 1:
        raise ValueError("Each cube must appear exactly once in the hierarchy")
    glow.save(out_dir / (frame + "_glow.png"))
    finish.save(out_dir / (frame + "_finish.png"), optimize=True)
    jsonwrite(ASSETS / "box" / (frame + ".json"), {"width": w, "height": h, "bones": result}, compact=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model-dir", type=Path, default=ART / "v0.4.3", help="Folder with the editable projects")
    parser.add_argument("--refit", action="store_true", help="Rebuild the 0.1 geometry from Blender (writes the requested model folder)")
    args = parser.parse_args()
    records = {}
    for frame in NAMES:
        if args.refit:
            model = json.loads((ROOT / "tmp" / "fitted" / (frame + ".json")).read_text())
            if args.model_dir.resolve() != ART:
                parser.error("--refit exports the legacy source. Use --model-dir art for 0.1 or refine_boxes.py and bake_imagegen_uv.py for the current model.")
            records[frame] = make_project(frame, model)
        project = args.model_dir / (frame + ".bbmodel")
        export_runtime(frame, project)
        if args.refit:
            records[frame]["blockbench_sha256"] = sha256(project.read_bytes()).hexdigest()
    if args.refit:
        jsonwrite(ART / "conversion.json", records)


if __name__ == "__main__":
    main()
