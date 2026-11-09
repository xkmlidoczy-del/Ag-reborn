#!/usr/bin/env python3
"""Contact sheet of the nine motif probe shots of one skill: contact-sheet.py <prefix> <skill> <out.png> (run with an interpreter that has Pillow)."""
import sys
from pathlib import Path
from PIL import Image
prefix, skill, out = sys.argv[1:4]
base = str(Path(__file__).resolve().parents[1] / 'run-showcase' / 'screenshots') + '/'
tiles = []
for k in "abcdefghi":
    try:
        tiles.append(Image.open(f"{base}{prefix}_{skill}_{k}.png").convert("RGB").crop((0, 100, 1280, 500)).resize((640, 200)))
    except FileNotFoundError:
        tiles.append(Image.new("RGB", (640, 200), (40, 0, 0)))
sheet = Image.new("RGB", (1920, 600))
for i, t in enumerate(tiles):
    sheet.paste(t, ((i % 3) * 640, (i // 3) * 200))
sheet.save(out)
