"""Import the ImageGen masters as a HUD texture and a tintable additive sprite atlas."""
from pathlib import Path

from PIL import Image, ImageChops, ImageDraw, ImageOps

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art/imagegen/v0.4.5"
TEXTURES = ROOT.parent.parent / "src/mech/resources/assets/acamech/textures"
RESAMPLE = Image.Resampling.LANCZOS
CELL = 256


def cockpit():
    with Image.open(ART / "cockpit.png") as original:
        width, height = original.size
        surface = original.crop((4, 5, width - 4, height - 6)).convert("RGBA")
    w, h = 512, 288
    glass = surface.resize((w, h), RESAMPLE)
    alpha = Image.new("L", (w * 2, h * 2))
    corners = [(7, 0), (w - 8, 0), (w - 1, 7), (w - 1, h - 8),
               (w - 8, h - 1), (7, h - 1), (0, h - 8), (0, 7)]
    ImageDraw.Draw(alpha).polygon([(x * 2, y * 2) for x, y in corners], fill=249)
    glass.putalpha(alpha.resize((w, h), RESAMPLE))
    glass.save(TEXTURES / "gui/cockpit_v045.png", optimize=True)


def mask(name, floor, gamma):
    with Image.open(ART / name) as original:
        light = ImageOps.grayscale(original).resize((CELL, CELL), RESAMPLE)
    light = light.point([round(255 * (max(0, level - floor) / (255 - floor)) ** gamma) for level in range(256)])
    # Fade the outer pixels into the texture gutter to keep each quad's edges invisible.
    edge = Image.new("L", (CELL, CELL))
    edge.putdata([min(255, round(min(x, y, CELL - 1 - x, CELL - 1 - y) * 255 / 12))
                  for y in range(CELL) for x in range(CELL)])
    tile = Image.new("RGBA", (CELL, CELL), "white")
    tile.putalpha(ImageChops.multiply(light, edge))
    return tile


def plasma():
    atlas = Image.new("RGBA", (CELL * 2, CELL))
    atlas.paste(mask("charge-ring.png", 5, .82), (0, 0))
    atlas.paste(mask("impact-burst.png", 9, 1.05), (CELL, 0))
    atlas.save(TEXTURES / "fx/plasma_v045.png", optimize=True)


if __name__ == "__main__":
    cockpit()
    plasma()
