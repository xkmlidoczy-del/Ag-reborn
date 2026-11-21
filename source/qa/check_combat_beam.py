"""Check seven real frames from the fixed camera positions in CombatNetworkProbe."""
import argparse
import json
from pathlib import Path

from PIL import Image


def beam_coverage(path, bounds):
    with Image.open(path) as source:
        frame = source.convert("RGB")
    frame.thumbnail((480, 270))
    left, top, right, bottom = bounds
    width, height = frame.size
    area = frame.crop((int(left * width), int(top * height), int(right * width), int(bottom * height)))
    pixels = area.load()
    gold = 0
    for y in range(area.height):
        for x in range(area.width):
            red, green, blue = pixels[x, y]
            if red >= 145 and green >= 110 and green * 1.30 > blue and red * 1.23 > blue:
                gold += 1
    return round(100 * gold / (area.width * area.height), 2)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    args = parser.parse_args()
    regions = {"caster": (.21, .26, .67, .72), "observer": (.55, .46, .86, .72)}
    results = {}
    for role, bounds in regions.items():
        directory = args.run / role / "screenshots"
        names = [f"combat-{i}.png" if i != 3 else "combat.png" for i in range(7)]
        results[role] = [beam_coverage(directory / name, bounds) for name in names]
    print(json.dumps(results))
    # The empty ray corridor in this scene covers less than 3% with HUD and entity marks.
    return 0 if all(min(frames) >= 8 for frames in results.values()) else 1


if __name__ == "__main__":
    raise SystemExit(main())
