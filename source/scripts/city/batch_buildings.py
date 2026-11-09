#!/usr/bin/env python3
"""Batch library: 40 buildings (10 per style) built with building_gen.make.

    .venv-qa/bin/python source/scripts/city/batch_buildings.py

Writes into tmp/city-prototype/batch/: <style>_<NN>.nbt, <style>_<NN>_day.png, sheet_<style>.png
and index.json. Parameters are fixed in LIBRARY, so reruns are identical. building_gen.py is not modified.
"""
import gzip
import json
import struct
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
from building_gen import make, render, write_nbt  # noqa: E402
from PIL import Image, ImageDraw, ImageFont  # noqa: E402

ROOT = HERE.parents[2]
OUT = ROOT / "tmp/city-prototype/batch"

# Setback shrink presets (dx0, dz0, dx1, dz1); every value is a multiple of 4.
S_A = (4, 4, -4, -4)    # needs lnx >= 16 and lnz >= 16
S_B = (4, 0, -4, 0)     # narrows x only
S_C = (0, 0, -8, 0)     # narrows x only, from the +x side
S_D = (0, 4, 0, -4)     # narrows z only

STYLES_ORDER = ["academic", "lab", "residential", "commercial"]

# (lnx, lnz, floors, seed, steps, fins, balcony, neon); steps = [(floor_index, shrink)]
LIBRARY = {
    "academic": [
        (16, 12, 8, 11001, [], False, False, False),
        (20, 16, 12, 11002, [(6, S_A)], True, False, False),
        (24, 12, 16, 11003, [(8, S_B)], False, False, False),
        (28, 20, 22, 11004, [(12, S_A)], True, False, False),
        (16, 16, 26, 11005, [(10, S_A)], True, False, False),
        (20, 12, 30, 11006, [], False, False, False),
        (24, 20, 10, 11007, [(5, S_B)], False, False, False),
        (28, 12, 28, 11008, [], True, False, False),
        (16, 20, 18, 11009, [(9, S_D)], True, False, False),
        (20, 20, 20, 11010, [], False, False, False),
    ],
    "lab": [
        (16, 16, 8, 12001, [(4, S_A)], False, False, False),
        (20, 12, 14, 12002, [(7, S_B)], True, False, False),
        (24, 16, 20, 12003, [(10, S_A)], True, False, False),
        (28, 12, 26, 12004, [], False, False, False),
        (16, 20, 30, 12005, [(14, S_D)], True, False, False),
        (20, 20, 12, 12006, [(6, S_A)], False, False, False),
        (24, 12, 16, 12007, [], True, False, False),
        (28, 16, 22, 12008, [(11, S_A)], False, False, False),
        (16, 12, 10, 12009, [(5, S_B)], False, False, False),
        (20, 16, 24, 12010, [], True, False, False),
    ],
    "residential": [
        (16, 12, 8, 13001, [(4, S_C)], False, True, False),
        (20, 12, 10, 13002, [], False, True, False),
        (24, 16, 14, 13003, [(6, S_A)], True, True, False),
        (28, 16, 18, 13004, [], True, False, False),
        (16, 16, 22, 13005, [(9, S_A)], False, True, False),
        (20, 20, 26, 13006, [(12, S_D)], True, False, False),
        (24, 12, 30, 13007, [(16, S_B)], False, True, False),
        (28, 12, 12, 13008, [], False, True, False),
        (16, 20, 16, 13009, [], False, False, False),
        (20, 16, 28, 13010, [(14, S_A)], True, True, False),
    ],
    "commercial": [
        (16, 12, 9, 14001, [], False, False, True),
        (20, 16, 12, 14002, [(5, S_A)], True, False, True),
        (24, 12, 15, 14003, [(7, S_B)], True, False, False),
        (28, 20, 20, 14004, [(10, S_A)], False, False, True),
        (16, 16, 24, 14005, [], True, False, True),
        (20, 12, 28, 14006, [(12, S_B)], False, False, False),
        (24, 20, 30, 14007, [(16, S_A)], True, False, True),
        (28, 16, 8, 14008, [], False, False, True),
        (16, 20, 18, 14009, [(8, S_D)], False, False, True),
        (20, 20, 22, 14010, [(10, S_A)], False, False, False),
    ],
}

WHITELIST = {
    "minecraft:oak_log", "minecraft:oak_leaves[persistent=true]", "minecraft:grass_block", "minecraft:moss_block",
    "minecraft:iron_bars", "minecraft:oak_fence", "minecraft:lantern", "minecraft:water[level=0]",
    "minecraft:poppy", "minecraft:azure_bluet", "minecraft:cornflower",
    "minecraft:red_concrete", "minecraft:sea_lantern",  # roof beacon and marker from building_gen.py
}


# ---------------------------------------------------------------- parameter checks
def check_params(lnx, lnz, floors, steps):
    for at, (a, c, d, e) in steps:
        if not (4 <= at <= floors - 3):
            raise ValueError(f"step floor {at} outside 4..{floors - 3}")
        if any(v % 4 for v in (a, c, d, e)):
            raise ValueError(f"shrink {(a, c, d, e)} not multiple of 4")
        if lnx + d - a + 1 < 8 or lnz + e - c + 1 < 8:
            raise ValueError(f"setback {(a, c, d, e)} narrower than 8 blocks")


# ---------------------------------------------------------------- NBT reader (validation only)
class _Reader:
    def __init__(self, data):
        self.d, self.p = data, 0

    def take(self, fmt):
        v = struct.unpack_from(fmt, self.d, self.p)[0]
        self.p += struct.calcsize(fmt)
        return v

    def string(self):
        n = self.take(">H")
        s = self.d[self.p:self.p + n].decode("utf-8")
        self.p += n
        return s

    def payload(self, t):
        scalar = {1: ">b", 2: ">h", 3: ">i", 4: ">q", 5: ">f", 6: ">d"}
        if t in scalar:
            return self.take(scalar[t])
        if t == 8:
            return self.string()
        if t == 9:
            el = self.take(">b")
            n = self.take(">i")
            return [self.payload(el) for _ in range(n)]
        if t == 10:
            out = {}
            while True:
                tt = self.take(">b")
                if tt == 0:
                    return out
                name = self.string()
                out[name] = self.payload(tt)
        if t in (7, 11, 12):
            n = self.take(">i")
            self.p += n * {7: 1, 11: 4, 12: 8}[t]
            return None
        raise ValueError(f"bad NBT tag {t}")


def parse_nbt(raw):
    r = _Reader(raw)
    if r.take(">b") != 10:
        raise ValueError("root is not a compound")
    r.string()
    root = r.payload(10)
    if r.p != len(raw):
        raise ValueError("trailing bytes after root compound")
    return root


def state_str(entry):
    props = entry.get("Properties")
    if not props:
        return entry["Name"]
    return entry["Name"] + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"


def validate(path, B, size):
    res = {"gzip": False, "nbt_parse": False, "block_count_match": False, "unexpected_block_ids": [], "passed": False}
    try:
        raw = gzip.decompress(path.read_bytes())
        res["gzip"] = True
        root = parse_nbt(raw)
        res["nbt_parse"] = True
    except Exception as exc:  # noqa: BLE001 - report any decode failure
        res["error"] = repr(exc)
        return res
    palette = [state_str(e) for e in root["palette"]]
    res["block_count_match"] = len(root["blocks"]) == len(B) and list(root["size"]) == list(size)
    ids = set(palette) | set(B.values())
    res["unexpected_block_ids"] = sorted(s for s in ids if not (s.startswith("academy:") or s in WHITELIST))
    res["passed"] = res["gzip"] and res["nbt_parse"] and res["block_count_match"] and not res["unexpected_block_ids"]
    return res


# ---------------------------------------------------------------- contact sheet
def contact_sheet(path, tiles, th=420, cols=5, pad=8, label_h=28):
    font = ImageFont.load_default(size=18)
    scaled = []
    for name, im in tiles:
        w = round(im.width * th / im.height)
        scaled.append((name, im.convert("RGB").resize((w, th), Image.LANCZOS)))
    cw = max(s.width for _, s in scaled)
    rows = (len(scaled) + cols - 1) // cols
    canvas = Image.new("RGB", (cols * cw + (cols + 1) * pad, pad + rows * (label_h + th + pad)), (20, 20, 24))
    draw = ImageDraw.Draw(canvas)
    for i, (name, s) in enumerate(scaled):
        r, c = divmod(i, cols)
        x = pad + c * (cw + pad) + (cw - s.width) // 2
        y = pad + r * (label_h + th + pad)
        draw.rectangle([x, y, x + s.width, y + label_h - 1], fill=(0, 0, 0))
        draw.text((x + 6, y + 4), name, fill=(255, 255, 255), font=font)
        canvas.paste(s, (x, y + label_h))
    canvas.save(path)
    return canvas.size


# ---------------------------------------------------------------- main
def main():
    OUT.mkdir(parents=True, exist_ok=True)
    records, tiles = [], {s: [] for s in STYLES_ORDER}
    for style in STYLES_ORDER:
        for idx, entry in enumerate(LIBRARY[style], start=1):
            lnx, lnz, floors, seed, steps, fins, balcony, neon = entry
            name = f"{style}_{idx:02d}"
            notes = []
            rec = {"file": f"{name}.nbt", "preview": f"{name}_day.png", "style": style, "index": idx,
                   "params": {"lnx": lnx, "lnz": lnz, "floors": floors, "seed": seed,
                              "steps": [[at, list(sh)] for at, sh in steps],
                              "fins": fins, "balcony": balcony, "neon": neon}}
            try:
                check_params(lnx, lnz, floors, steps)
                B = make(style, lnx, lnz, floors, seed, steps=steps, fins=fins, balcony=balcony, neon=neon)
                nbt_path = OUT / rec["file"]
                size, n_blocks, n_pal = write_nbt(nbt_path, B)
                img = render(B, night=False, hw=6, hh=3, vz=6)
                img.save(OUT / rec["preview"])
                tiles[style].append((rec["file"], img))
                val = validate(nbt_path, B, size)
                rec.update(size=list(size), block_count=n_blocks, palette_count=n_pal, validation=val)
            except Exception as exc:  # noqa: BLE001 - record and continue so the rest of the library still builds
                notes.append(f"generation error: {exc!r}")
                rec.update(size=None, block_count=None, palette_count=None,
                           validation={"passed": False, "error": repr(exc)})
            reasons = []
            if rec["block_count"] is not None:
                if rec["block_count"] < 3000:
                    reasons.append(f"block count {rec['block_count']} < 3000")
                if rec["block_count"] > 120000:
                    reasons.append(f"block count {rec['block_count']} > 120000")
                if rec["size"][1] < 24:
                    reasons.append(f"y-size {rec['size'][1]} < 24")
            rec["suspect"] = bool(reasons)
            rec["suspect_reasons"] = reasons
            rec["notes"] = notes
            records.append(rec)
            print(f"{name}: size={rec['size']} blocks={rec['block_count']} palette={rec['palette_count']} "
                  f"passed={rec['validation'].get('passed')} suspect={rec['suspect']}", flush=True)

    for style in STYLES_ORDER:
        if tiles[style]:
            contact_sheet(OUT / f"sheet_{style}.png", tiles[style])
    (OUT / "index.json").write_text(json.dumps(records, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")

    print()
    print(f"{'style':<12}{'count':>6}{'min_blk':>10}{'max_blk':>10}  {'suspects':<40} unexpected block ids")
    for style in STYLES_ORDER:
        recs = [r for r in records if r["style"] == style]
        counts = [r["block_count"] for r in recs if r["block_count"] is not None]
        sus = [r["file"] for r in recs if r["suspect"]]
        unexp = sorted({b for r in recs for b in r["validation"].get("unexpected_block_ids", [])})
        print(f"{style:<12}{len(recs):>6}{min(counts, default=0):>10}{max(counts, default=0):>10}  "
              f"{', '.join(sus) or '-':<40} {', '.join(unexp) or '-'}")
    failed = [r["file"] for r in records if not r["validation"].get("passed")]
    print("failed validation:", failed or "none")


if __name__ == "__main__":
    main()
